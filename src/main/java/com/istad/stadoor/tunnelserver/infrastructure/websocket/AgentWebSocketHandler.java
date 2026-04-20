package com.istad.stadoor.tunnelserver.infrastructure.websocket;

import com.istad.stadoor.tunnelserver.application.dto.request.CreateTunnelRequest;
import com.istad.stadoor.tunnelserver.application.service.AuthApplicationService;
import com.istad.stadoor.tunnelserver.application.service.ProxyService;
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

    private final AgentSessionRegistry     registry;
    private final AuthApplicationService   authService;
    private final TunnelApplicationService tunnelService;
    private final ProxyService             proxyService; // 👈 ADD
    private final ObjectMapper             mapper;

    // ── Connection Established ───────────────────────────────────
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("✅ Agent connected: sessionId={}", session.getId());
    }

    // ── Handle Messages ──────────────────────────────────────────
    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage      message
    ) throws Exception {

        log.info(">>> Raw: {}", message.getPayload());
        WsMessage ws = mapper.readValue(message.getPayload(), WsMessage.class);
        log.info(">>> Type: {}", ws.getType());

        try {
            switch (ws.getType()) {
                case "register"      -> handleRegister(session, ws);
                case "heartbeat"     -> handleHeartbeat(session, ws);
                case "tunnel_create" -> handleTunnelCreate(session, ws);
                case "http_response" -> handleHttpResponse(ws);  // 👈 ADD
                default              -> sendError(session,
                        ws.getRequestId(),
                        "Unknown type: " + ws.getType());
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(session, ws.getRequestId(),
                    e.getMessage() != null ? e.getMessage() : e.getClass().getName());
        }
    }

    // ── Connection Closed ────────────────────────────────────────
    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus      status
    ) {
        log.info("❌ Disconnected: sessionId={} | status={}", session.getId(), status);
        registry.unregister(session);
    }

    // ── Handle Register ──────────────────────────────────────────
    private void handleRegister(WebSocketSession session, WsMessage ws) {
        try {
            var p = ws.getPayload();
            log.info(">>> Register payload: {}", p);

            String token  = str(p, "token");
            UUID   userId = authService.validateToken(token);
            log.info(">>> Validated userId: {}", userId);

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
            log.info("✅ Registered: clientId={}", clientId);

            send(session, new WsMessage(
                    "registered",
                    ws.getRequestId(),
                    Map.of(
                            "clientId", clientId.toString(),
                            "userId",   userId.toString()
                    )
            ));

        } catch (Exception e) {
            log.error("❌ Register failed: {}", e.getMessage());
            sendError(session, ws.getRequestId(),
                    e.getMessage() != null ? e.getMessage() : e.getClass().getName());
        }
    }

    // ── Handle Heartbeat ─────────────────────────────────────────
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

    // ── Handle Tunnel Create ─────────────────────────────────────
    private void handleTunnelCreate(WebSocketSession session, WsMessage ws) {
        ConnectedClient client = registry.getClientBySession(session)
                .orElseThrow(() ->
                        new IllegalStateException("Client not registered"));

        String basePath = str(ws.getPayload(), "basePath");

        tunnelService.createTunnel(new CreateTunnelRequest(
                        client.getUserId(), basePath
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
                    sendError(session, ws.getRequestId(),
                            ex.getMessage() != null
                                    ? ex.getMessage()
                                    : ex.getClass().getName());
                    return null;
                });
    }

    // ── Handle HTTP Response from Agent ──────────────────────────
    // 👇 ADD THIS
    private void handleHttpResponse(WsMessage ws) {
        String requestId = ws.getRequestId();
        String body      = str(ws.getPayload(), "body");

        log.info("✅ http_response: requestId={}", requestId);
        proxyService.completeResponse(requestId, body);
    }

    // ── Helpers ──────────────────────────────────────────────────
    private void send(WebSocketSession session, WsMessage msg) {
        try {
            session.sendMessage(
                    new TextMessage(mapper.writeValueAsString(msg)));
        } catch (Exception e) {
            log.error("Send failed: {}", e.getMessage());
        }
    }

    private void sendError(
            WebSocketSession session,
            String           requestId,
            String           error
    ) {
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