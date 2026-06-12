package com.phantomguard.controlplane.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket configuration for the live telemetry feed. Dashboards
 * connect to {@code /ws} (SockJS fallback enabled) and subscribe to
 * {@code /topic/live-feed/{parentId}}. The simple in-memory broker is
 * sufficient per instance; for multi-instance fan-out the Redis Pub/Sub
 * channel already delivers every event to every control-plane node, so each
 * node broadcasts to its locally connected sessions.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ControlPlaneProperties properties;

    public WebSocketConfig(ControlPlaneProperties properties) {
        this.properties = properties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = properties.getAllowedOrigins().toArray(String[]::new);
        registry.addEndpoint("/ws").setAllowedOriginPatterns(origins);
        registry.addEndpoint("/ws").setAllowedOriginPatterns(origins).withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
