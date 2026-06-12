package com.phantomguard.dataplane.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tunables for the DNS resolution engine. All defaults are production-sane;
 * override via {@code application.yml} or environment variables
 * (e.g. {@code PHANTOMGUARD_DATAPLANE_UPSTREAM_HOST}).
 */
@Validated
@ConfigurationProperties(prefix = "phantomguard.dataplane")
public class DataPlaneProperties {

    public enum BlockMode { NXDOMAIN, REDIRECT }

    /** Upstream recursive resolver (Cloudflare by default). */
    @NotBlank
    private String upstreamHost = "1.1.1.1";

    @Min(1)
    @Max(65535)
    private int upstreamPort = 53;

    /** Hard deadline for a single upstream round trip. */
    @Min(100)
    private long upstreamTimeoutMs = 2500;

    /** DoT listener configuration. */
    private boolean dotEnabled = true;

    @Min(1)
    @Max(65535)
    private int dotPort = 853;

    /** PEM certificate chain / PKCS#8 key for the DoT listener. Empty = self-signed dev cert. */
    private String tlsCertChainPath = "";
    private String tlsPrivateKeyPath = "";

    /** DNS suffix used to extract the clientId from SNI: {clientId}.dns.yourdomain.com */
    @NotBlank
    private String dnsDomainSuffix = "dns.yourdomain.com";

    /** How blocked queries are answered. */
    private BlockMode blockMode = BlockMode.NXDOMAIN;

    /** Landing IP returned for blocked A queries when blockMode = REDIRECT. */
    @NotBlank
    private String safeLandingIp = "0.0.0.0";

    @Min(1)
    private int redirectTtlSeconds = 60;

    /** Local policy near-cache TTL; invalidation pub/sub evicts earlier. */
    @Min(1)
    private long policyCacheTtlSeconds = 300;

    @Min(10000)
    private long policyCacheMaxEntries = 100_000;

    /** Budget for a Redis policy load before failing open (fault tolerance). */
    @Min(10)
    private long policyLookupTimeoutMs = 150;

    /** Periodic refresh of the in-memory domain->category index. */
    @Min(5)
    private long categoryIndexRefreshSeconds = 300;

    /** Upper bound on the Redis telemetry buffer to protect broker memory. */
    @Min(1000)
    private long telemetryBufferMaxLength = 500_000;

    public String getUpstreamHost() { return upstreamHost; }
    public void setUpstreamHost(String upstreamHost) { this.upstreamHost = upstreamHost; }
    public int getUpstreamPort() { return upstreamPort; }
    public void setUpstreamPort(int upstreamPort) { this.upstreamPort = upstreamPort; }
    public long getUpstreamTimeoutMs() { return upstreamTimeoutMs; }
    public void setUpstreamTimeoutMs(long upstreamTimeoutMs) { this.upstreamTimeoutMs = upstreamTimeoutMs; }
    public boolean isDotEnabled() { return dotEnabled; }
    public void setDotEnabled(boolean dotEnabled) { this.dotEnabled = dotEnabled; }
    public int getDotPort() { return dotPort; }
    public void setDotPort(int dotPort) { this.dotPort = dotPort; }
    public String getTlsCertChainPath() { return tlsCertChainPath; }
    public void setTlsCertChainPath(String tlsCertChainPath) { this.tlsCertChainPath = tlsCertChainPath; }
    public String getTlsPrivateKeyPath() { return tlsPrivateKeyPath; }
    public void setTlsPrivateKeyPath(String tlsPrivateKeyPath) { this.tlsPrivateKeyPath = tlsPrivateKeyPath; }
    public String getDnsDomainSuffix() { return dnsDomainSuffix; }
    public void setDnsDomainSuffix(String dnsDomainSuffix) { this.dnsDomainSuffix = dnsDomainSuffix; }
    public BlockMode getBlockMode() { return blockMode; }
    public void setBlockMode(BlockMode blockMode) { this.blockMode = blockMode; }
    public String getSafeLandingIp() { return safeLandingIp; }
    public void setSafeLandingIp(String safeLandingIp) { this.safeLandingIp = safeLandingIp; }
    public int getRedirectTtlSeconds() { return redirectTtlSeconds; }
    public void setRedirectTtlSeconds(int redirectTtlSeconds) { this.redirectTtlSeconds = redirectTtlSeconds; }
    public long getPolicyCacheTtlSeconds() { return policyCacheTtlSeconds; }
    public void setPolicyCacheTtlSeconds(long policyCacheTtlSeconds) { this.policyCacheTtlSeconds = policyCacheTtlSeconds; }
    public long getPolicyCacheMaxEntries() { return policyCacheMaxEntries; }
    public void setPolicyCacheMaxEntries(long policyCacheMaxEntries) { this.policyCacheMaxEntries = policyCacheMaxEntries; }
    public long getPolicyLookupTimeoutMs() { return policyLookupTimeoutMs; }
    public void setPolicyLookupTimeoutMs(long policyLookupTimeoutMs) { this.policyLookupTimeoutMs = policyLookupTimeoutMs; }
    public long getCategoryIndexRefreshSeconds() { return categoryIndexRefreshSeconds; }
    public void setCategoryIndexRefreshSeconds(long categoryIndexRefreshSeconds) { this.categoryIndexRefreshSeconds = categoryIndexRefreshSeconds; }
    public long getTelemetryBufferMaxLength() { return telemetryBufferMaxLength; }
    public void setTelemetryBufferMaxLength(long telemetryBufferMaxLength) { this.telemetryBufferMaxLength = telemetryBufferMaxLength; }
}
