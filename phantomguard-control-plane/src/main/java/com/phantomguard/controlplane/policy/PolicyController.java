package com.phantomguard.controlplane.policy;

import com.phantomguard.common.policy.CategoryCatalog;
import com.phantomguard.common.policy.ContentCategory;
import com.phantomguard.controlplane.domain.ChildProfileDocument;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * MODULE D — administrative policy API. POST endpoints require the admin API
 * key header (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicySyncService policySyncService;

    public PolicyController(PolicySyncService policySyncService) {
        this.policySyncService = policySyncService;
    }

    @PostMapping("/toggle")
    public Map<String, Object> toggle(@Valid @RequestBody PolicyToggleRequest request) {
        ChildProfileDocument updated = policySyncService.toggle(request);
        return Map.of(
                "clientId", updated.getClientId(),
                "blockedCategories", updated.getBlockedCategories(),
                "blockedDomains", updated.getBlockedDomains(),
                "allowedDomains", updated.getAllowedDomains(),
                "syncedAt", updated.getUpdatedAt());
    }

    /** Catalog of toggleable categories for the dashboard configuration panel. */
    @GetMapping("/categories")
    public List<Map<String, Object>> categories() {
        return Arrays.stream(ContentCategory.values())
                .map(category -> Map.<String, Object>of(
                        "id", category.name(),
                        "displayName", category.displayName(),
                        "sampleDomains", CategoryCatalog.seedDomains()
                                .getOrDefault(category, java.util.Set.of()).stream()
                                .sorted().limit(5).toList()))
                .toList();
    }
}
