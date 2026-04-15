package com.im.server.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@EnableConfigurationProperties({WebSocketProperties.class, AgoraProperties.class})
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor;
    private final WebSocketStompRateLimitChannelInterceptor webSocketStompRateLimitChannelInterceptor;
    private final WebSocketProperties webSocketProperties;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
            .setAllowedOriginPatterns(webSocketProperties.getAllowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        if (webSocketProperties.getRelay().isEnabled()) {
            var r = webSocketProperties.getRelay();
            registry.enableStompBrokerRelay("/queue", "/topic")
                .setRelayHost(r.getHost())
                .setRelayPort(r.getPort())
                .setClientLogin(r.getClientLogin())
                .setClientPasscode(r.getClientPasscode())
                .setSystemLogin(r.getSystemLogin())
                .setSystemPasscode(r.getSystemPasscode());
        } else {
            registry.enableSimpleBroker("/queue", "/topic");
        }
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(
            webSocketAuthChannelInterceptor,
            webSocketStompRateLimitChannelInterceptor
        );
    }
}
