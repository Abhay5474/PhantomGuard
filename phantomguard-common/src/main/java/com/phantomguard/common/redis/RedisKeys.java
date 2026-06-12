package com.phantomguard.common.redis;

/**
 * Single source of truth for every Redis key and channel shared between the
 * control plane (writer) and the data plane (reader). Keeping the namespace
 * here prevents silent drift between the two deployables.
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** SET of explicitly blocked domains for a client. O(1) SISMEMBER lookups. */
    public static String blockedDomains(String clientId) {
        return "pg:policy:" + clientId + ":domains";
    }

    /** SET of blocked category identifiers for a client. */
    public static String blockedCategories(String clientId) {
        return "pg:policy:" + clientId + ":categories";
    }

    /** SET of explicitly allowed (override) domains for a client; wins over categories. */
    public static String allowedDomains(String clientId) {
        return "pg:policy:" + clientId + ":allowed";
    }

    /** HASH mapping domain -> category id, seeded from the category catalog. */
    public static final String DOMAIN_CATEGORY_INDEX = "pg:domain:categories";

    /** LIST used as the durable telemetry buffer drained by the batch worker. */
    public static final String TELEMETRY_BUFFER = "pg:telemetry:buffer";

    /** Pub/Sub channel carrying live telemetry events for WebSocket fan-out. */
    public static final String TELEMETRY_LIVE_CHANNEL = "pg:telemetry:live";

    /** Pub/Sub channel broadcasting policy invalidations (payload = clientId). */
    public static final String POLICY_INVALIDATION_CHANNEL = "pg:policy:invalidate";

    /** Payload on the invalidation channel meaning "category catalog changed". */
    public static final String CATALOG_INVALIDATION_PAYLOAD = "__catalog__";
}
