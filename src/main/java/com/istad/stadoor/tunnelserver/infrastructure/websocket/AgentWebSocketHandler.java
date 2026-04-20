package com.istad.stadoor.tunnelserver.infrastructure.websocket;

import com.istad.stadoor.tunnelserver.application.dto.request.CreateTunnelRequest;
import com.istad.stadoor.tunnelserver.application.service.AuthApplicationService;
import com.istad.stadoor.tunnelserver.application.service.TunnelApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AgentWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentWebSocketHandler.class);

    private final AgentSessionRegistry registry;
    private final AuthApplicationService authService;
    private final TunnelApplicationService tunnelService;
    private final ObjectMapper mapper;



    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println(">>> Server: WebSocket connected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        System.out.println(">>> Server: raw payload: " + message.getPayload());

        WsMessage ws = mapper.readValue(message.getPayload(), WsMessage.class);
        System.out.println(">>> Server: parsed message type = " + ws.getType());

        try {
            switch (ws.getType()) {
                case "register" -> handleRegister(session, ws);
                case "heartbeat" -> handleHeartbeat(session, ws);
                case "tunnel_create" -> handleTunnelCreate(session, ws);
                default -> sendError(session, ws.getRequestId(), "Unknown type: " + ws.getType());
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(session, ws.getRequestId(),
                    e.getMessage() != null ? e.getMessage() : e.getClass().getName());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        System.out.println(">>> Server: WebSocket closed: " + session.getId() + ", status=" + status);
        registry.unregister(session);
    }

    // =====================================================
    // HERE: handleRegister
    // =====================================================
    private void handleRegister(WebSocketSession session, WsMessage ws) {
        try {
            var p = ws.getPayload();
            System.out.println(">>> Server: handleRegister payload = " + p);

            String token = str(p, "token");
            System.out.println(">>> Server: token = " + token);

            UUID userId = authService.validateToken(token);
            System.out.println(">>> Server: validated userId = " + userId);

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
            System.out.println(">>> Server: client registered = " + clientId);

            WsMessage response = new WsMessage(
                    "registered",
                    ws.getRequestId(),
                    Map.of(
                            "clientId", clientId.toString(),
                            "userId", userId.toString()
                    )
            );

            System.out.println(">>> Server: sending response = registered");
            send(session, response);

        } catch (Exception e) {
            System.out.println(">>> Server: handleRegister ERROR");
            e.printStackTrace();
            sendError(session, ws.getRequestId(),
                    e.getMessage() != null ? e.getMessage() : e.getClass().getName());
        }
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
                    ex.printStackTrace();
                    sendError(session, ws.getRequestId(),
                            ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName());
                    return null;
                });
    }

    private void send(WebSocketSession session, WsMessage msg) {
        try {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(msg)));
        } catch (Exception e) {
            log.error("Failed to send WS message", e);
        }
    }

    private void sendError(WebSocketSession session, String requestId, String error) {
        send(session, new WsMessage(
                "error",
                requestId,
                Map.of("error", error != null ? error : "Unknown error")
        ));
    }

    private String str(Map<String, Object> map, String key) {
        Object val = map != null ? map.get(key) : null;
        return val != null ? val.toString() : "";
    }
}