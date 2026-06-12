package com.phantomguard.dataplane.engine;

import com.phantomguard.common.dns.DnsMessageUtil;
import com.phantomguard.common.dns.DnsQuestion;
import com.phantomguard.common.telemetry.TelemetryEvent;
import com.phantomguard.dataplane.config.DataPlaneProperties;
import com.phantomguard.dataplane.policy.PolicyDecision;
import com.phantomguard.dataplane.policy.PolicyEngine;
import com.phantomguard.dataplane.telemetry.TelemetryPublisher;
import com.phantomguard.dataplane.upstream.UpstreamResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Transport-agnostic resolution pipeline shared by the DoH controller and the
 * DoT listener:
 *
 * <pre>
 *   parse question -> O(1) policy check -> [synthesize block | forward upstream]
 *                                              \-> out-of-band telemetry
 * </pre>
 *
 * <p>The pipeline is fully non-blocking. Upstream failures degrade to
 * SERVFAIL (never to a hang), and policy-tier failures fail open inside
 * {@link PolicyEngine} so connectivity is preserved.
 */
@Service
public class DnsResolutionService {

    private static final Logger log = LoggerFactory.getLogger(DnsResolutionService.class);

    private final PolicyEngine policyEngine;
    private final UpstreamResolver upstreamResolver;
    private final TelemetryPublisher telemetryPublisher;
    private final DataPlaneProperties properties;
    private final InetAddress safeLandingAddress;

    public DnsResolutionService(PolicyEngine policyEngine,
                                UpstreamResolver upstreamResolver,
                                TelemetryPublisher telemetryPublisher,
                                DataPlaneProperties properties) {
        this.policyEngine = policyEngine;
        this.upstreamResolver = upstreamResolver;
        this.telemetryPublisher = telemetryPublisher;
        this.properties = properties;
        try {
            this.safeLandingAddress = InetAddress.getByName(properties.getSafeLandingIp());
        } catch (UnknownHostException e) {
            throw new IllegalStateException(
                    "Invalid phantomguard.dataplane.safe-landing-ip: " + properties.getSafeLandingIp(), e);
        }
    }

    /**
     * Resolves a raw wire-format query on behalf of {@code clientId}.
     *
     * @throws com.phantomguard.common.dns.DnsParseException via the returned
     *         Mono if the payload is not a valid DNS query
     */
    public Mono<byte[]> resolve(String clientId, byte[] rawQuery) {
        final long startNanos = System.nanoTime();
        return Mono.fromCallable(() -> DnsMessageUtil.parseQuestion(rawQuery))
                .flatMap(question -> policyEngine.evaluate(clientId, question.name())
                        .flatMap(decision -> decision.blocked()
                                ? deny(clientId, question, decision, startNanos)
                                : forward(clientId, question, rawQuery, startNanos)));
    }

    private Mono<byte[]> deny(String clientId, DnsQuestion question,
                              PolicyDecision decision, long startNanos) {
        byte[] response = properties.getBlockMode() == DataPlaneProperties.BlockMode.REDIRECT
                ? DnsMessageUtil.buildRedirectResponse(question, safeLandingAddress, properties.getRedirectTtlSeconds())
                : DnsMessageUtil.buildNxDomainResponse(question);
        telemetryPublisher.publish(TelemetryEvent.blocked(
                clientId, question.name(), question.type().name(),
                decision.reason(), elapsedMicros(startNanos)));
        return Mono.just(response);
    }

    private Mono<byte[]> forward(String clientId, DnsQuestion question,
                                 byte[] rawQuery, long startNanos) {
        return upstreamResolver.forward(rawQuery)
                .doOnNext(response -> telemetryPublisher.publish(TelemetryEvent.allowed(
                        clientId, question.name(), question.type().name(), elapsedMicros(startNanos))))
                .onErrorResume(e -> {
                    log.warn("Upstream resolution failed for {} ({}): {}",
                            question.name(), clientId, e.toString());
                    telemetryPublisher.publish(TelemetryEvent.error(
                            clientId, question.name(), question.type().name(), elapsedMicros(startNanos)));
                    return Mono.just(DnsMessageUtil.buildServFailResponse(question));
                });
    }

    private static long elapsedMicros(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000;
    }
}
