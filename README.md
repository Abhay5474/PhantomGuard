# PhantomGuard — Encrypted DNS Parental Control System

PhantomGuard captures **all device DNS traffic with zero client-side apps** by leveraging the
encrypted DNS resolvers built into modern operating systems:

- **iOS / macOS** — a dynamically generated `.mobileconfig` profile provisions system-wide
  **DNS-over-HTTPS** pointed at a unique per-child URL: `https://dns.yourdomain.com/dns-query/{clientId}`
- **Android** — the native *Private DNS* setting speaks **DNS-over-TLS** to a unique per-child
  hostname: `{clientId}.dns.yourdomain.com` (clientId recovered from TLS SNI)

Every query is evaluated against the child's policy in **O(1)** (Redis-backed sets with a local
near-cache), answered with a synthesized `NXDOMAIN` (or safe-landing redirect) when blocked, or
forwarded asynchronously to an upstream recursive resolver when allowed. Telemetry streams fully
out-of-band into a live parent dashboard and a MongoDB time-series audit trail.

## Architecture

```
                          ┌────────────────────────────────────────────┐
  iOS/macOS (DoH :443) ──▶│            DATA PLANE (WebFlux/Netty)      │
  Android   (DoT :853) ──▶│  parse wire format → policy (O(1)) →       │──▶ Upstream 1.1.1.1
                          │  NXDOMAIN | forward      │                 │    (UDP + TCP fallback)
                          └───────────┬──────────────┼─────────────────┘
                                      │ near-cache   │ fire-and-forget
                                      ▼              ▼
                          ┌─────────────────────────────────────────────┐
                          │                   REDIS                     │
                          │  pg:policy:{clientId}:{domains|categories|  │
                          │  allowed}   pg:telemetry:buffer (LIST)      │
                          │  pg:telemetry:live / pg:policy:invalidate   │
                          └───────────┬─────────────────────────────────┘
                                      │ pub/sub + batch drain
                                      ▼
                          ┌─────────────────────────────────────────────┐
  Parent Dashboard ◀──────│        CONTROL PLANE (Spring Boot MVC)      │◀──▶ MongoDB
  (Next.js 14, STOMP WS)  │  profiles · .mobileconfig · policy toggle   │  child_profiles +
                          │  dual-write sync · STOMP /topic/live-feed   │  dns_query_logs (TS)
                          └─────────────────────────────────────────────┘
```

| Module | Location | Highlights |
|---|---|---|
| A — Profile generation & onboarding | `controlplane/profile` | `GET /api/profiles/generate`, dynamic `.mobileconfig` (`application/x-apple-aspen-config`), Android DoT onboarding payload |
| B — Reactive data plane | `dataplane/doh`, `dataplane/dot` | RFC 8484 DoH (GET base64url + POST binary), RFC 7858 DoT with 2-byte framing & SNI clientId extraction |
| C — Policy enforcement & telemetry | `dataplane/policy`, `dataplane/telemetry`, `controlplane/telemetry` | Redis sets + Caffeine near-cache, NXDOMAIN/redirect synthesis, async upstream forward, batched time-series persistence |
| D — Policy control & sync | `controlplane/policy` | `POST /api/policies/toggle`, Mongo↔Redis dual-write (MULTI/EXEC), pub/sub invalidation (<100 ms cluster propagation) |
| E — WebSocket telemetry | `controlplane/telemetry/LiveFeedBridge` | STOMP `/topic/live-feed/{parentId}`, clientId→parent mapping with caching |
| F — Parent dashboard | `dashboard/` | Next.js 14 App Router, Tailwind, Lucide, Recharts; live rolling feed with blocked markers, category toggles, custom domain overrides |

## Repository layout

```
phantomguard-common/         DNS wire-format codec, telemetry contracts, Redis namespace, category catalog
phantomguard-data-plane/     DoH controller (WebFlux), DoT Netty listener, policy engine, upstream resolver
phantomguard-control-plane/  REST APIs, MongoDB documents, dual-write sync, batch worker, STOMP server
dashboard/                   Next.js 14 parent dashboard
docker-compose.yml           Full local stack (MongoDB, Redis, both planes, dashboard)
```

## Quick start

```bash
# Full stack
docker compose up --build

# Or run pieces locally (requires local MongoDB + Redis):
mvn package
java -jar phantomguard-control-plane/target/phantomguard-control-plane-1.0.0.jar
java -jar phantomguard-data-plane/target/phantomguard-data-plane-1.0.0.jar
cd dashboard && npm install && npm run dev
```

Dashboard: http://localhost:3000 · Control plane: http://localhost:8080 · DoH: http://localhost:8443 · DoT: :853

### Try it end to end

```bash
# 1. Provision a child profile
curl 'http://localhost:8080/api/profiles/generate?parentId=demo-parent&childName=Asha'
# → returns clientId, DoH URL, DoT hostname, .mobileconfig path

# 2. Block the SOCIAL_MEDIA category
curl -X POST http://localhost:8080/api/policies/toggle \
  -H 'Content-Type: application/json' -H 'X-PG-Admin-Key: change-me-in-production' \
  -d '{"clientId":"<clientId>","targetType":"CATEGORY","value":"SOCIAL_MEDIA","action":"BLOCK"}'

# 3. Resolve through the data plane (DoH GET, base64url wire format for tiktok.com/A)
Q=$(python3 - <<'EOF'
import base64,struct
name=b''.join(bytes([len(l)])+l for l in b'tiktok.com'.split(b'.'))+b'\x00'
print(base64.urlsafe_b64encode(struct.pack('>HHHHHH',0x1234,0x0100,1,0,0,0)+name+struct.pack('>HH',1,1)).decode().rstrip('='))
EOF
)
curl -s "http://localhost:8443/dns-query/<clientId>?dns=$Q" | xxd | head -2
# → RCODE=3 (NXDOMAIN); the dashboard live feed shows the blocked attempt instantly
```

## Non-functional design notes

- **Critical-path latency** — steady-state policy evaluation is in-memory (Caffeine snapshot);
  Redis is touched only on cache miss, with a 150 ms budget. Telemetry is fire-and-forget
  (`LPUSH`+`PUBLISH`, subscribed off-path). The upstream hop dominates end-to-end latency.
- **Fault tolerance / fail-open** — if Redis or MongoDB is down, policy loads fail OPEN and
  queries are still forwarded upstream: a child device never loses internet because the control
  tier is degraded. Telemetry batches that fail to persist are re-queued; the buffer is
  length-capped to protect Redis memory.
- **Policy propagation** — toggles dual-write MongoDB (source of truth) and Redis (projection,
  rebuilt atomically in MULTI/EXEC), then broadcast on `pg:policy:invalidate`, evicting every
  instance's near-cache in <100 ms.
- **Production checklist** — wildcard TLS cert for `*.dns.yourdomain.com` (DoT) and a cert for
  the DoH host; sign `.mobileconfig` payloads; replace the static admin API key with your IdP
  (OAuth2/JWT); terminate DoH on :443 at your LB or via `server.ssl.*`.

## Security model

- `clientId` is a `SecureRandom`-sourced UUIDv4 — the bearer token embedded in resolver endpoints.
- Mutating policy APIs require the `X-PG-Admin-Key` header (constant-time comparison); swap in a
  real identity provider for multi-tenant deployments.
- The DoT listener rejects connections whose SNI doesn't match `{uuid}.dns.yourdomain.com` —
  PhantomGuard is not an open resolver.
