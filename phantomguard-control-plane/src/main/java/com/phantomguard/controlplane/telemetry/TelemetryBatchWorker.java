package com.phantomguard.controlplane.telemetry;

import com.phantomguard.common.redis.RedisKeys;
import com.phantomguard.common.telemetry.TelemetryCodec;
import com.phantomguard.common.telemetry.TelemetryEvent;
import com.phantomguard.controlplane.config.ControlPlaneProperties;
import com.phantomguard.controlplane.config.MongoConfig;
import com.phantomguard.controlplane.domain.DnsQueryLogDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * MODULE C (persistence half) — drains the Redis telemetry buffer in batches
 * and bulk-inserts the events into the MongoDB time-series collection.
 *
 * <p>The buffer is a Redis LIST: the data plane LPUSHes, this worker RPOPs in
 * counted batches, preserving rough ordering. On a Mongo outage the popped
 * batch is pushed back to the tail of the list so no audit data is lost; the
 * data plane independently caps list length to bound broker memory.
 */
@Component
public class TelemetryBatchWorker {

    private static final Logger log = LoggerFactory.getLogger(TelemetryBatchWorker.class);

    private final StringRedisTemplate redis;
    private final MongoTemplate mongoTemplate;
    private final ControlPlaneProperties properties;

    public TelemetryBatchWorker(StringRedisTemplate redis,
                                MongoTemplate mongoTemplate,
                                ControlPlaneProperties properties) {
        this.redis = redis;
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${phantomguard.controlplane.telemetry-drain-interval-ms:500}")
    public void drain() {
        final List<String> batch;
        try {
            batch = redis.opsForList().rightPop(
                    RedisKeys.TELEMETRY_BUFFER, properties.getTelemetryBatchSize());
        } catch (RuntimeException e) {
            log.warn("Telemetry drain skipped — Redis unavailable: {}", e.toString());
            return;
        }
        if (batch == null || batch.isEmpty()) {
            return;
        }

        List<DnsQueryLogDocument> documents = new ArrayList<>(batch.size());
        for (String json : batch) {
            try {
                TelemetryEvent event = TelemetryCodec.decode(json);
                documents.add(new DnsQueryLogDocument(
                        event.timestamp(), event.clientId(), event.domain(),
                        event.queryType(), event.status().name(),
                        event.blockReason(), event.latencyMicros()));
            } catch (RuntimeException e) {
                log.warn("Dropping undecodable telemetry payload: {}", e.toString());
            }
        }
        if (documents.isEmpty()) {
            return;
        }

        try {
            mongoTemplate.insert(documents, MongoConfig.QUERY_LOG_COLLECTION);
            log.debug("Persisted {} telemetry events", documents.size());
        } catch (RuntimeException e) {
            log.error("Bulk insert of {} telemetry events failed — re-queueing batch: {}",
                    documents.size(), e.toString());
            try {
                redis.opsForList().rightPushAll(RedisKeys.TELEMETRY_BUFFER, batch);
            } catch (RuntimeException requeueFailure) {
                log.error("Re-queue also failed; {} events lost: {}",
                        batch.size(), requeueFailure.toString());
            }
        }
    }
}
