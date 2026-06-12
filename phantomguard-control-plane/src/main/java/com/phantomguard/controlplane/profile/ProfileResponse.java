package com.phantomguard.controlplane.profile;

import java.time.Instant;
import java.util.Set;

/** Public projection of a child profile plus its onboarding endpoints. */
public record ProfileResponse(
        String clientId,
        String parentId,
        String childName,
        String dohUrl,
        String dotHostname,
        String mobileConfigPath,
        Set<String> blockedCategories,
        Set<String> blockedDomains,
        Set<String> allowedDomains,
        Instant createdAt) {
}
