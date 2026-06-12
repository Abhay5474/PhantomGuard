package com.phantomguard.controlplane.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.timeseries.Granularity;
import org.springframework.data.mongodb.core.mapping.TimeSeries;

import java.time.Instant;

/**
 * Persistent audit record of a single DNS query, bulk-inserted by the
 * telemetry batch worker into a MongoDB time-series collection (declared via
 * {@link TimeSeries}; collection creation is ensured in MongoConfig).
 */
@Document("dns_query_logs")
@TimeSeries(collection = "dns_query_logs", timeField = "timestamp",
        metaField = "clientId", granularity = Granularity.SECONDS)
public class DnsQueryLogDocument {

    @Id
    private String id;

    @Field("timestamp")
    private Instant timestamp;

    @Field("clientId")
    private String clientId;

    private String domain;
    private String queryType;
    private String status;
    private String blockReason;
    private long latencyMicros;

    public DnsQueryLogDocument() {
    }

    public DnsQueryLogDocument(Instant timestamp, String clientId, String domain,
                               String queryType, String status, String blockReason, long latencyMicros) {
        this.timestamp = timestamp;
        this.clientId = clientId;
        this.domain = domain;
        this.queryType = queryType;
        this.status = status;
        this.blockReason = blockReason;
        this.latencyMicros = latencyMicros;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getQueryType() { return queryType; }
    public void setQueryType(String queryType) { this.queryType = queryType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBlockReason() { return blockReason; }
    public void setBlockReason(String blockReason) { this.blockReason = blockReason; }
    public long getLatencyMicros() { return latencyMicros; }
    public void setLatencyMicros(long latencyMicros) { this.latencyMicros = latencyMicros; }
}
