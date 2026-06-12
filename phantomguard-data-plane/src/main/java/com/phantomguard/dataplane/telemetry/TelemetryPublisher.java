package com.phantomguard.dataplane.telemetry;

import com.phantomguard.common.redis.RedisKeys;
import com.phantomguard.common.telemetry.TelemetryCodec;
import com.phantomguard.common.telemetry.TelemetryEvent;
import com.phantomguard.dataplane.config.DataPlaneProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Fire-and-forget telemetry emitter. Every event is written to two places:
 * a Redis LIST (durable buffer drained by the control plane's batch worker)
 * and a Pub/Sub channel (live WebSocket feed). Both writes happen entirely
 * off the resolution critical path — failures are logged and dropped, never
 * surfaced to the resolving client.
 */
@Component
public class TelemetryPublisher {

    private static final Logger log = LoggerFactory.getLogger(TelemetryPublisher.class);

    private final ReactiveStringRedisTemplate redis;
    private final long bufferMaxLength;

    public TelemetryPublisher(ReactiveStringRedisTemplate redis, DataPlaneProperties properties) {
        this.redis = redis;
        this.bufferMaxLength = properties.getTelemetryBufferMaxLength();
    }

    public void publish(TelemetryEvent event) {
        final String json;
        try {
            json = TelemetryCodec.encode(event);
        } catch (RuntimeException e) {
            log.error("Failed to encode telemetry event", e);
            return;
        }

        redis.opsForList().leftPush(RedisKeys.TELEMETRY_BUFFER, json)
                .flatMap(length -> length > bufferMaxLength
                        ? redis.opsForList().trim(RedisKeys.TELEMETRY_BUFFER, 0, bufferMaxLength - 1)
                        : Mono.empty())
                .then(redis.convertAndSend(RedisKeys.TELEMETRY_LIVE_CHANNEL, json))
                .subscribe(
                        subscribers -> { },
                        e -> log.warn("Telemetry publish failed (event dropped): {}", e.toString()));
    }
}
