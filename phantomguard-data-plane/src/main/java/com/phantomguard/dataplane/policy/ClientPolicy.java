package com.phantomguard.dataplane.policy;

import java.util.Set;

/**
 * Immutable snapshot of a client's effective policy, materialized from the
 * Redis sets written by the control plane. Held in a local near-cache so the
 * resolution hot path performs pure in-memory hash lookups.
 *
 * @param failOpen true when this snapshot was produced because Redis was
 *                 unreachable; the engine then allows everything so the child
 *                 device never loses connectivity.
 */
public record ClientPolicy(
        Set<String> blockedDomains,
        Set<String> blockedCategories,
        Set<String> allowedDomains,
        boolean failOpen) {

    public static final ClientPolicy FAIL_OPEN = new ClientPolicy(Set.of(), Set.of(), Set.of(), true);
    public static final ClientPolicy EMPTY = new ClientPolicy(Set.of(), Set.of(), Set.of(), false);
}
