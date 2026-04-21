package com.istad.stadoor.tunnelserver.infrastructure.config;

import com.istad.stadoor.tunnelserver.infrastructure.websocket.AgentWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final AgentWebSocketHandler agentWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentWebSocketHandler, "/agent-ws")
                .setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();

        // ✅ Large buffer to handle Next.js large JS chunks and images
        container.setMaxTextMessageBufferSize(50 * 1024 * 1024);   // 50 MB
        container.setMaxBinaryMessageBufferSize(50 * 1024 * 1024); // 50 MB
        container.setMaxSessionIdleTimeout(300_000L);              // 5 minutes
        container.setAsyncSendTimeout(60_000L);                    // 60 seconds

        return container;
    }
}