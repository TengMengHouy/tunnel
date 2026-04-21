package com.istad.stadoor.tunnelserver.infrastructure.config;

import com.istad.stadoor.tunnelserver.infrastructure.websocket.AgentWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;

import java.util.Map;

@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final AgentWebSocketHandler agentWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentWebSocketHandler, "/agent-ws")
                .setAllowedOrigins("*");
        log.info("✅ WebSocket registered at /agent-ws");
    }

    // ✅ Force WebSocket HandlerMapping to highest priority
    // This ensures /agent-ws is handled by WebSocket BEFORE Spring MVC
    @Bean
    public HandlerMapping webSocketHandlerMapping() {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(Ordered.HIGHEST_PRECEDENCE); // ← Highest priority

        WebSocketHttpRequestHandler handler =
                new WebSocketHttpRequestHandler(agentWebSocketHandler);

        mapping.setUrlMap(Map.of("/agent-ws", handler));

        log.info("✅ WebSocket HandlerMapping registered with HIGHEST_PRECEDENCE");
        return mapping;
    }

    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(50 * 1024 * 1024);
        container.setMaxBinaryMessageBufferSize(50 * 1024 * 1024);
        container.setMaxSessionIdleTimeout(300_000L);
        container.setAsyncSendTimeout(60_000L);
        log.info("✅ WebSocket container: 50MB buffer");
        return container;
    }
}