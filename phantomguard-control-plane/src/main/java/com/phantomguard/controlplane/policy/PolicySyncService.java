package com.phantomguard.controlplane.policy;

import com.phantomguard.common.policy.ContentCategory;
import com.phantomguard.common.redis.RedisKeys;
import com.phantomguard.controlplane.domain.ChildProfileDocument;
import com.phantomguard.controlplane.profile.ProfileService;
import com.phantomguard.controlplane.repository.ChildProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

/**
 * MODULE D — dual-write synchronization coordinator.
 *
 * <p>Write path for every administrative toggle:
 * <ol>
 *   <li>Mutate and persist the MongoDB profile document (source of truth;
 *       single-document writes are atomic, optimistic locking via @Version).</li>
 *   <li>Rebuild the client's three Redis policy sets in one pipelined,
 *       transactional MULTI/EXEC batch so the data plane never observes a
 *       half-written policy.</li>
 *   <li>PUBLISH the clientId on the invalidation channel so every clustered
 *       data-plane instance evicts its local near-cache (&lt;100 ms propagation).</li>
 * </ol>
 *
 * <p>If Redis is unavailable the Mongo write still succeeds; the data plane
 * fails open and this coordinator logs the divergence for the next resync.
 */
@Service
public class PolicySyncService {

    private static final Logger log = LoggerFactory.getLogger(PolicySyncService.class);

    private final ChildProfileRepository repository;
    private final ProfileService profileService;
    private final StringRedisTemplate redis;

    public PolicySyncService(ChildProfileRepository repository,
                             ProfileService profileService,
                             StringRedisTemplate redis) {
        this.repository = repository;
        this.profileService = profileService;
        this.redis = redis;
    }

    /**
     * Removes a child profile entirely: deletes the MongoDB source-of-truth
     * document, purges the client's three Redis policy sets, and broadcasts an
     * invalidation so every data-plane instance drops its near-cache.
     */
    public void deleteProfile(String clientId) {
        ChildProfileDocument profile = profileService.requireByClientId(clientId);
        repository.delete(profile);
        purgeRedis(clientId);
        publishInvalidation(clientId);
        log.info("Deleted profile clientId={}", clientId);
    }

    private void purgeRedis(String clientId) {
        try {
            redis.delete(java.util.List.of(
                    RedisKeys.blockedDomains(clientId),
                    RedisKeys.blockedCategories(clientId),
                    RedisKeys.allowedDomains(clientId)));
        } catch (RuntimeException e) {
            log.error("Redis policy purge failed for clientId={}: {}", clientId, e.toString());
        }
    }

    public ChildProfileDocument toggle(PolicyToggleRequest request) {
        ChildProfileDocument profile = profileService.requireByClientId(request.clientId());
        applyMutation(profile, request);

        ChildProfileDocument saved;
        try {
            saved = repository.save(profile);
        } catch (OptimisticLockingFailureException e) {
            // Concurrent admin update: reload and replay once.
            ChildProfileDocument fresh = profileService.requireByClientId(request.clientId());
            applyMutation(fresh, request);
            saved = repository.save(fresh);
        }

        syncRedis(saved);
        publishInvalidation(saved.getClientId());
        return saved;
    }

    private void applyMutation(ChildProfileDocument profile, PolicyToggleRequest request) {
        if (request.targetType() == PolicyToggleRequest.TargetType.CATEGORY) {
            ContentCategory category = parseCategory(request.value());
            switch (request.action()) {
                case BLOCK -> profile.getBlockedCategories().add(category.name());
                case UNBLOCK -> profile.getBlockedCategories().remove(category.name());
                default -> throw new IllegalArgumentException(
                        "Action " + request.action() + " is not valid for CATEGORY targets");
            }
            return;
        }

        String domain = normalizeDomain(request.value());
        switch (request.action()) {
            case BLOCK -> {
                profile.getBlockedDomains().add(domain);
                profile.getAllowedDomains().remove(domain);
            }
            case UNBLOCK -> profile.getBlockedDomains().remove(domain);
            case ALLOW -> {
                profile.getAllowedDomains().add(domain);
                profile.getBlockedDomains().remove(domain);
            }
            case UNALLOW -> profile.getAllowedDomains().remove(domain);
        }
    }

    /** Atomically replaces the client's Redis policy projection (DEL + SADD in MULTI/EXEC). */
    public void syncRedis(ChildProfileDocument profile) {
        String clientId = profile.getClientId();
        try {
            redis.execute(new org.springframework.data.redis.core.SessionCallback<>() {
                @Override
                @SuppressWarnings("unchecked")
                public Object execute(org.springframework.data.redis.core.RedisOperations operations) {
                    var ops = (org.springframework.data.redis.core.RedisOperations<String, String>) operations;
                    ops.multi();
                    rewriteSet(ops, RedisKeys.blockedDomains(clientId), profile.getBlockedDomains());
                    rewriteSet(ops, RedisKeys.blockedCategories(clientId), profile.getBlockedCategories());
                    rewriteSet(ops, RedisKeys.allowedDomains(clientId), profile.getAllowedDomains());
                    return ops.exec();
                }
            });
        } catch (RuntimeException e) {
            log.error("Redis policy sync failed for clientId={} — data plane will fail open "
                    + "until the next successful sync: {}", clientId, e.toString());
        }
    }

    private void rewriteSet(org.springframework.data.redis.core.RedisOperations<String, String> ops,
                            String key, Set<String> members) {
        ops.delete(key);
        if (!members.isEmpty()) {
            ops.opsForSet().add(key, members.toArray(String[]::new));
        }
    }

    private void publishInvalidation(String clientId) {
        try {
            redis.convertAndSend(RedisKeys.POLICY_INVALIDATION_CHANNEL, clientId);
        } catch (RuntimeException e) {
            log.error("Failed to broadcast policy invalidation for clientId={}: {}", clientId, e.toString());
        }
    }

    private static ContentCategory parseCategory(String value) {
        try {
            return ContentCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown category '" + value + "'");
        }
    }

    private static String normalizeDomain(String value) {
        String domain = value.trim().toLowerCase(Locale.ROOT);
        if (domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        if (domain.startsWith("*.")) {
            domain = domain.substring(2);
        }
        if (!domain.matches("^(?!-)[a-z0-9-]{1,63}(?<!-)(\\.(?!-)[a-z0-9-]{1,63}(?<!-))+$")) {
            throw new IllegalArgumentException("'" + value + "' is not a valid domain name");
        }
        return domain;
    }
}
