package com.phantomguard.common.telemetry;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Structural event emitted off the resolution critical path for every DNS
 * query. Serialized to JSON onto a Redis list (durable batch sink) and a
 * Redis Pub/Sub channel (live dashboard feed).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TelemetryEvent(
        String eventId,
        String clientId,
        String domain,
        String queryType,
        QueryStatus status,
        String blockReason,
        long latencyMicros,
        Instant timestamp) {

    public TelemetryEvent {
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(status, "status");
        if (eventId == null) {
            eventId = UUID.randomUUID().toString();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public static TelemetryEvent allowed(String clientId, String domain, String queryType, long latencyMicros) {
        return new TelemetryEvent(null, clientId, domain, queryType, QueryStatus.ALLOWED, null, latencyMicros, null);
    }

    public static TelemetryEvent blocked(String clientId, String domain, String queryType, String reason, long latencyMicros) {
        return new TelemetryEvent(null, clientId, domain, queryType, QueryStatus.BLOCKED, reason, latencyMicros, null);
    }

    public static TelemetryEvent error(String clientId, String domain, String queryType, long latencyMicros) {
        return new TelemetryEvent(null, clientId, domain, queryType, QueryStatus.ERROR, null, latencyMicros, null);
    }
}
