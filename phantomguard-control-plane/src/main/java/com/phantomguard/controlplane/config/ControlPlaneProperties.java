package com.phantomguard.controlplane.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "phantomguard.controlplane")
public class ControlPlaneProperties {

    /** Public base URL of the DoH data plane, embedded in .mobileconfig payloads. */
    @NotBlank
    private String publicDohBaseUrl = "https://dns.yourdomain.com";

    /** DNS suffix for per-client DoT hostnames: {clientId}.dns.yourdomain.com */
    @NotBlank
    private String dotDomainSuffix = "dns.yourdomain.com";

    /** Shared secret required on administrative endpoints (header X-PG-Admin-Key). */
    @NotBlank
    private String adminApiKey = "change-me-in-production";

    /** Origins allowed for REST + WebSocket access (the dashboard). */
    private List<String> allowedOrigins = List.of("http://localhost:3000");

    /** Telemetry batch worker tuning. */
    @Min(1)
    private int telemetryBatchSize = 500;

    @Min(50)
    private long telemetryDrainIntervalMs = 500;

    public String getPublicDohBaseUrl() { return publicDohBaseUrl; }
    public void setPublicDohBaseUrl(String publicDohBaseUrl) { this.publicDohBaseUrl = publicDohBaseUrl; }
    public String getDotDomainSuffix() { return dotDomainSuffix; }
    public void setDotDomainSuffix(String dotDomainSuffix) { this.dotDomainSuffix = dotDomainSuffix; }
    public String getAdminApiKey() { return adminApiKey; }
    public void setAdminApiKey(String adminApiKey) { this.adminApiKey = adminApiKey; }
    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    public int getTelemetryBatchSize() { return telemetryBatchSize; }
    public void setTelemetryBatchSize(int telemetryBatchSize) { this.telemetryBatchSize = telemetryBatchSize; }
    public long getTelemetryDrainIntervalMs() { return telemetryDrainIntervalMs; }
    public void setTelemetryDrainIntervalMs(long telemetryDrainIntervalMs) { this.telemetryDrainIntervalMs = telemetryDrainIntervalMs; }
}
