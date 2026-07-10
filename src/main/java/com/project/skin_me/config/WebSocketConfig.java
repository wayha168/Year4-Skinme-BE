package com.project.skin_me.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple message broker for real-time communication
        config.enableSimpleBroker(
                "/topic/notifications", // For broadcast notifications
                "/topic/chat", // For chat messages
                "/topic/orders", // For order updates
                "/topic/products", // For product updates
                "/topic/inventory", // For inventory updates
                "/topic/feedback", // New product feedback (admin toast)
                "/user" // For user-specific messages
        );

        // Set application destination prefix for client-to-server messages
        config.setApplicationDestinationPrefixes("/app");

        // Set user registry prefix for user-specific messaging
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SockJS endpoint; handshake uses HTTP session so principal is available for /user destinations
        registry.addEndpoint("/ws-endpoint")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

}
