package com.phantomguard.controlplane.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.CollectionOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.timeseries.Granularity;

import java.time.Duration;

/**
 * Ensures the {@code dns_query_logs} time-series collection exists before the
 * batch worker performs its first bulk insert. Time-series collections cannot
 * be created implicitly by an insert, so this runs eagerly at startup.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);

    public static final String QUERY_LOG_COLLECTION = "dns_query_logs";

    @Bean
    ApplicationRunner ensureTimeSeriesCollection(MongoTemplate mongoTemplate) {
        return args -> {
            try {
                if (!mongoTemplate.collectionExists(QUERY_LOG_COLLECTION)) {
                    mongoTemplate.createCollection(QUERY_LOG_COLLECTION, CollectionOptions.empty()
                            .timeSeries(CollectionOptions.TimeSeriesOptions.timeSeries("timestamp")
                                    .metaField("clientId")
                                    .granularity(Granularity.SECONDS)));
                    // Retention: bound long-term storage at 90 days.
                    mongoTemplate.getDb().runCommand(new org.bson.Document("collMod", QUERY_LOG_COLLECTION)
                            .append("expireAfterSeconds", Duration.ofDays(90).toSeconds()));
                    log.info("Created time-series collection '{}'", QUERY_LOG_COLLECTION);
                }
            } catch (RuntimeException e) {
                // Fault isolation: a missing analytics collection must not stop
                // profile provisioning or policy administration.
                log.error("Could not ensure time-series collection '{}': {}",
                        QUERY_LOG_COLLECTION, e.toString());
            }
        };
    }
}
