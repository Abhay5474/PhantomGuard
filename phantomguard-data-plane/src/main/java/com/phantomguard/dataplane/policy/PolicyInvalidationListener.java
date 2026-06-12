package com.phantomguard.dataplane.policy;

import com.phantomguard.common.redis.RedisKeys;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Subscribes to the policy invalidation Pub/Sub channel so administrative
 * toggles propagate to every clustered data-plane instance in well under
 * 100 ms: the local near-cache entry is evicted and the very next query for
 * that client re-reads the fresh Redis state.
 */
@Component
public class PolicyInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(PolicyInvalidationListener.class);

    private final ReactiveRedisConnectionFactory connectionFactory;
    private final PolicyEngine policyEngine;

    private ReactiveRedisMessageListenerContainer container;
    private Disposable subscription;

    public PolicyInvalidationListener(ReactiveRedisConnectionFactory connectionFactory,
                                      PolicyEngine policyEngine) {
        this.connectionFactory = connectionFactory;
        this.policyEngine = policyEngine;
    }

    @PostConstruct
    void subscribe() {
        container = new ReactiveRedisMessageListenerContainer(connectionFactory);
        subscription = container
                .receive(ChannelTopic.of(RedisKeys.POLICY_INVALIDATION_CHANNEL))
                .map(message -> message.getMessage())
                .flatMap(payload -> {
                    if (RedisKeys.CATALOG_INVALIDATION_PAYLOAD.equals(payload)) {
                        log.info("Category catalog invalidated — refreshing index");
                        return policyEngine.refreshCategoryIndex();
                    }
                    log.info("Policy invalidated for clientId={}", payload);
                    policyEngine.invalidateClient(payload);
                    return Mono.empty();
                })
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(s -> log.warn("Invalidation subscription lost, reconnecting: {}",
                                s.failure().toString())))
                .subscribe();
        log.info("Subscribed to {}", RedisKeys.POLICY_INVALIDATION_CHANNEL);
    }

    @PreDestroy
    void shutdown() {
        if (subscription != null) {
            subscription.dispose();
        }
        if (container != null) {
            container.destroyLater().subscribe();
        }
    }
}
