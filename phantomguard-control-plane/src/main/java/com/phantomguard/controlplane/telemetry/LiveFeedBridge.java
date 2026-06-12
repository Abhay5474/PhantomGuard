package com.phantomguard.controlplane.telemetry;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.phantomguard.common.redis.RedisKeys;
import com.phantomguard.common.telemetry.TelemetryCodec;
import com.phantomguard.common.telemetry.TelemetryEvent;
import com.phantomguard.controlplane.repository.ChildProfileRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * MODULE E — bridges the Redis live telemetry channel onto STOMP topics.
 *
 * <p>Each event carries only a {@code clientId}; this bridge resolves the
 * owning parent (and the child's display name) through a Caffeine-cached
 * MongoDB lookup, then broadcasts to {@code /topic/live-feed/{parentId}} for
 * every dashboard session subscribed on this node.
 */
@Component
public class LiveFeedBridge implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(LiveFeedBridge.class);

    private record ProfileOwner(String parentId, String childName) {
    }

    private final RedisMessageListenerContainer listenerContainer;
    private final ChildProfileRepository profileRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private final Cache<String, Optional<ProfileOwner>> ownerCache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    public LiveFeedBridge(RedisMessageListenerContainer listenerContainer,
                          ChildProfileRepository profileRepository,
                          SimpMessagingTemplate messagingTemplate) {
        this.listenerContainer = listenerContainer;
        this.profileRepository = profileRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    void subscribe() {
        listenerContainer.addMessageListener(this, ChannelTopic.of(RedisKeys.TELEMETRY_LIVE_CHANNEL));
        log.info("Live feed bridge subscribed to {}", RedisKeys.TELEMETRY_LIVE_CHANNEL);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            TelemetryEvent event = TelemetryCodec.decode(
                    new String(message.getBody(), StandardCharsets.UTF_8));
            Optional<ProfileOwner> owner = ownerCache.get(event.clientId(), this::lookupOwner);
            if (owner == null || owner.isEmpty()) {
                return; // event for an unknown/deleted profile
            }
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("clientId", event.clientId());
            payload.put("childName", owner.get().childName());
            payload.put("domain", event.domain());
            payload.put("queryType", event.queryType());
            payload.put("status", event.status().name());
            payload.put("blockReason", event.blockReason());
            payload.put("latencyMicros", event.latencyMicros());
            payload.put("timestamp", event.timestamp().toString());
            messagingTemplate.convertAndSend("/topic/live-feed/" + owner.get().parentId(), payload);
        } catch (RuntimeException e) {
            log.warn("Failed to relay live telemetry event: {}", e.toString());
        }
    }

    private Optional<ProfileOwner> lookupOwner(String clientId) {
        try {
            return profileRepository.findByClientId(clientId)
                    .map(p -> new ProfileOwner(p.getParentId(), p.getChildName()));
        } catch (RuntimeException e) {
            log.warn("Owner lookup failed for clientId={} (event skipped): {}", clientId, e.toString());
            return Optional.empty();
        }
    }
}
