package com.phantomguard.dataplane.policy;

/**
 * Outcome of evaluating a single query against the client's policy.
 *
 * @param blocked whether the query must be denied
 * @param reason  machine-readable cause (e.g. {@code DOMAIN_RULE},
 *                {@code CATEGORY:SOCIAL_MEDIA}); null when allowed
 */
public record PolicyDecision(boolean blocked, String reason) {

    public static final PolicyDecision ALLOWED = new PolicyDecision(false, null);

    public static PolicyDecision blockedByDomainRule() {
        return new PolicyDecision(true, "DOMAIN_RULE");
    }

    public static PolicyDecision blockedByCategory(String category) {
        return new PolicyDecision(true, "CATEGORY:" + category);
    }
}
