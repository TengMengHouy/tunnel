package com.istad.stadoor.tunnelserver.infrastructure.config;

import com.istad.stadoor.tunnelserver.infrastructure.websocket.AgentWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
@Order(1) // ✅ Highest priority
public class WebSocketConfig implements WebSocketConfigurer {

    private final AgentWebSocketHandler handler;

    public WebSocketConfig(AgentWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/agent")
                .setAllowedOrigins("*");
    }
}