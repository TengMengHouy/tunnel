package com.istad.stadoor.tunnelserver.infrastructure.config;

import com.istad.stadoor.tunnelserver.infrastructure.websocket.AgentWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AgentWebSocketHandler handler;

    public WebSocketConfig(AgentWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // ✅ Change from /ws/agent to /agent-ws
        // ProxyController pattern /{basePath}/{key}/** won't match this!
        registry.addHandler(handler, "/agent-ws")
                .setAllowedOrigins("*");
    }
}