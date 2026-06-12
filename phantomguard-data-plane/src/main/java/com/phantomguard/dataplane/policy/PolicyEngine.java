package com.phantomguard.dataplane.policy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.phantomguard.common.redis.RedisKeys;
import com.phantomguard.dataplane.config.DataPlaneProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * O(1) policy evaluation for the resolution critical path.
 *
 * <p>Per-client policy snapshots are loaded from Redis sets and held in a
 * Caffeine near-cache (evicted by the pub/sub invalidation listener and a TTL
 * safety net), so steady-state evaluation never leaves process memory. The
 * domain-to-category index is a single volatile map refreshed periodically and
 * on catalog invalidation.
 *
 * <p>Fault tolerance: every Redis interaction has a hard timeout and fails
 * OPEN — a broken cache tier must never take a child's device offline.
 */
@Component
public class PolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(PolicyEngine.class);

    private final ReactiveStringRedisTemplate redis;
    private final DataPlaneProperties properties;
    private final Cache<String, ClientPolicy> policyCache;

    private volatile Map<String, String> domainCategoryIndex = Map.of();
    private Disposable indexRefreshSubscription;

    public PolicyEngine(ReactiveStringRedisTemplate redis, DataPlaneProperties properties) {
        this.redis = redis;
        this.properties = properties;
        this.policyCache = Caffeine.newBuilder()
                .maximumSize(properties.getPolicyCacheMaxEntries())
                .expireAfterWrite(Duration.ofSeconds(properties.getPolicyCacheTtlSeconds()))
                .build();
    }

    @PostConstruct
    void startIndexRefresh() {
        indexRefreshSubscription = Flux
                .interval(Duration.ZERO, Duration.ofSeconds(properties.getCategoryIndexRefreshSeconds()))
                .concatMap(tick -> refreshCategoryIndex())
                .onErrorResume(e -> {
                    log.warn("Category index refresh loop error: {}", e.toString());
                    return Mono.empty();
                })
                .subscribe();
    }

    @PreDestroy
    void stop() {
        if (indexRefreshSubscription != null) {
            indexRefreshSubscription.dispose();
        }
    }

    /**
     * Evaluates a domain for a client. Explicit allow overrides win, then
     * explicit domain blocks, then category blocks — each checked across the
     * domain's parent-suffix chain (www.tiktok.com -> tiktok.com -> com).
     */
    public Mono<PolicyDecision> evaluate(String clientId, String domain) {
        return policy(clientId).map(policy -> decide(policy, domain));
    }

    private PolicyDecision decide(ClientPolicy policy, String domain) {
        if (policy.failOpen()) {
            return PolicyDecision.ALLOWED;
        }
        Map<String, String> categoryIndex = this.domainCategoryIndex;
        String suffix = domain;
        while (suffix != null) {
            if (policy.allowedDomains().contains(suffix)) {
                return PolicyDecision.ALLOWED;
            }
            if (policy.blockedDomains().contains(suffix)) {
                return PolicyDecision.blockedByDomainRule();
            }
            String category = categoryIndex.get(suffix);
            if (category != null && policy.blockedCategories().contains(category)) {
                return PolicyDecision.blockedByCategory(category);
            }
            int dot = suffix.indexOf('.');
            suffix = dot > 0 ? suffix.substring(dot + 1) : null;
        }
        return PolicyDecision.ALLOWED;
    }

    private Mono<ClientPolicy> policy(String clientId) {
        ClientPolicy cached = policyCache.getIfPresent(clientId);
        if (cached != null && !cached.failOpen()) {
            return Mono.just(cached);
        }
        return loadFromRedis(clientId)
                .timeout(Duration.ofMillis(properties.getPolicyLookupTimeoutMs()))
                .doOnNext(policy -> policyCache.put(clientId, policy))
                .onErrorResume(e -> {
                    log.warn("Policy load failed for {} — failing open: {}", clientId, e.toString());
                    return Mono.just(ClientPolicy.FAIL_OPEN);
                });
    }

    private Mono<ClientPolicy> loadFromRedis(String clientId) {
        Mono<Set<String>> blockedDomains = readSet(RedisKeys.blockedDomains(clientId));
        Mono<Set<String>> blockedCategories = readSet(RedisKeys.blockedCategories(clientId));
        Mono<Set<String>> allowedDomains = readSet(RedisKeys.allowedDomains(clientId));
        return Mono.zip(blockedDomains, blockedCategories, allowedDomains)
                .map(t -> new ClientPolicy(t.getT1(), t.getT2(), t.getT3(), false));
    }

    private Mono<Set<String>> readSet(String key) {
        return redis.opsForSet().members(key)
                .collect(HashSet<String>::new, Set::add)
                .map(s -> (Set<String>) s)
                .defaultIfEmpty(Set.of());
    }

    /** Evicts a single client's snapshot; invoked by the invalidation listener. */
    public void invalidateClient(String clientId) {
        policyCache.invalidate(clientId);
    }

    /** Reloads the domain->category index from Redis (catalog invalidation). */
    public Mono<Void> refreshCategoryIndex() {
        return redis.<String, String>opsForHash().entries(RedisKeys.DOMAIN_CATEGORY_INDEX)
                .collect(HashMap<String, String>::new, (m, e) -> m.put(e.getKey(), e.getValue()))
                .timeout(Duration.ofSeconds(5))
                .doOnNext(index -> {
                    this.domainCategoryIndex = Map.copyOf(index);
                    log.debug("Domain-category index refreshed: {} entries", index.size());
                })
                .onErrorResume(e -> {
                    log.warn("Category index refresh failed, keeping previous snapshot: {}", e.toString());
                    return Mono.empty();
                })
                .then();
    }
}
