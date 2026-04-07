package com.istad.stadoor.tunnelserver.infrastructure.websocket;

import com.istad.stadoor.tunnelserver.application.dto.request.CreateTunnelRequest;
import com.istad.stadoor.tunnelserver.application.service.AuthApplicationService;
import com.istad.stadoor.tunnelserver.application.service.TunnelApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.UUID;

@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentWebSocketHandler.class);

    private final AgentSessionRegistry registry;
    private final AuthApplicationService authService;
    private final TunnelApplicationService tunnelService;
    private final ObjectMapper mapper;

    public AgentWebSocketHandler(AgentSessionRegistry registry,
                                 AuthApplicationService authService,
                                 TunnelApplicationService tunnelService) {
        this.registry = registry;
        this.authService = authService;
        this.tunnelService = tunnelService;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregister(session);
        log.info("WebSocket disconnected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WsMessage ws = mapper.readValue(message.getPayload(), WsMessage.class);

        try {
            switch (ws.getType()) {
                case "register" -> handleRegister(session, ws);
                case "heartbeat" -> handleHeartbeat(session, ws);
                case "tunnel_create" -> handleTunnelCreate(session, ws);
                default -> sendError(session, ws.getRequestId(), "Unknown type: " + ws.getType());
            }
        } catch (Exception e) {
            sendError(session, ws.getRequestId(), e.getMessage());
        }
    }

    private void handleRegister(WebSocketSession session, WsMessage ws) {
        var p = ws.getPayload();

        String token = str(p, "token");
        UUID userId = authService.validateToken(token);
        UUID clientId = UUID.randomUUID();

        ConnectedClient client = new ConnectedClient(
            clientId,
            userId,
            token,
            str(p, "hostName"),
            str(p, "hostPort"),
            str(p, "ipAddress"),
            str(p, "osType")
        );

        registry.register(client, session);

        send(session, new WsMessage(
            "registered",
            ws.getRequestId(),
            Map.of(
                "clientId", clientId,
                "userId", userId
            )
        ));
    }

    private void handleHeartbeat(WebSocketSession session, WsMessage ws) {
        if (registry.getClientBySession(session).isEmpty()) {
            sendError(session, ws.getRequestId(), "Client not registered");
            return;
        }

        send(session, new WsMessage(
            "heartbeat_ack",
            ws.getRequestId(),
            Map.of("status", "ok")
        ));
    }

    private void handleTunnelCreate(WebSocketSession session, WsMessage ws) {
        ConnectedClient client = registry.getClientBySession(session)
            .orElseThrow(() -> new IllegalStateException("Client not registered"));

        String basePath = str(ws.getPayload(), "basePath");

        tunnelService.createTunnel(new CreateTunnelRequest(
                client.getUserId(),
                basePath
            ))
                .thenAccept(tunnelId -> send(session, new WsMessage(
                        "tunnel_created",
                        ws.getRequestId(),
                        Map.of(
                                "tunnelId", tunnelId.toString(),
                                "basePath", basePath
                        )
                )))
            .exceptionally(ex -> {
                sendError(session, ws.getRequestId(), ex.getMessage());
                return null;
            });
    }

    public void pushToClient(String clientId, WsMessage msg) {
        registry.getSession(clientId).ifPresent(s -> send(s, msg));
    }

    private void send(WebSocketSession session, WsMessage msg) {
        try {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(msg)));
        } catch (Exception e) {
            log.error("Failed to send message", e);
        }
    }

    private void sendError(WebSocketSession session, String requestId, String error) {
        send(session, new WsMessage(
            "error",
            requestId,
            Map.of("error", error)
        ));
    }

    private String str(Map<String, Object> map, String key) {
        Object val = map != null ? map.get(key) : null;
        return val != null ? val.toString() : "";
    }
}
