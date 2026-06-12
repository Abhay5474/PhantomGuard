package com.phantomguard.controlplane.policy;

import com.phantomguard.common.policy.CategoryCatalog;
import com.phantomguard.common.redis.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Materializes the built-in category catalog into the shared Redis
 * domain->category hash at startup, then broadcasts a catalog invalidation so
 * already-running data-plane instances refresh their in-memory index.
 */
@Component
public class CategorySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CategorySeeder.class);

    private final StringRedisTemplate redis;

    public CategorySeeder(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Map<String, String> index = new HashMap<>();
            CategoryCatalog.seedDomains().forEach((category, domains) ->
                    domains.forEach(domain -> index.put(domain, category.name())));
            redis.opsForHash().putAll(RedisKeys.DOMAIN_CATEGORY_INDEX, index);
            redis.convertAndSend(RedisKeys.POLICY_INVALIDATION_CHANNEL,
                    RedisKeys.CATALOG_INVALIDATION_PAYLOAD);
            log.info("Seeded {} domain-category mappings into Redis", index.size());
        } catch (RuntimeException e) {
            log.error("Category catalog seeding failed (will rely on existing Redis state): {}",
                    e.toString());
        }
    }
}
