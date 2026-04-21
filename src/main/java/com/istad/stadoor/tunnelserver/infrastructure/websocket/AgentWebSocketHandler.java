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
    private final ProxyService             proxyService;
    private final ObjectMapper             mapper;

    // ── Connection Established ───────────────────────────────────
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("✅ Agent connected: sessionId={}", session.getId());
    }

    // ── Handle Text Messages ─────────────────────────────────────
    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message
    ) throws Exception {

        log.info(">>> Raw: {}", message.getPayload());

        WsMessage ws;
        try {
            ws = mapper.readValue(message.getPayload(), WsMessage.class);
        } catch (Exception e) {
            log.error("❌ Failed to parse message: {}", e.getMessage());
            sendError(session, null, "Invalid message format: " + e.getMessage());
            return;
        }

        log.info(">>> Type: {}", ws.getType());

        try {
            switch (ws.getType()) {
                case "register"      -> handleRegister(session, ws);
                case "heartbeat"     -> handleHeartbeat(session, ws);
                case "tunnel_create" -> handleTunnelCreate(session, ws);
                case "http_response" -> handleHttpResponse(ws);
                default              -> {
                    log.warn("⚠️ Unknown message type: {}", ws.getType());
                    sendError(session, ws.getRequestId(),
                            "Unknown type: " + ws.getType());
                }
            }
        } catch (Exception e) {
            log.error("❌ Handler error [{}]: {}", ws.getType(), e.getMessage(), e);
            sendError(session, ws.getRequestId(),
                    e.getMessage() != null ? e.getMessage() : e.getClass().getName());
        }
    }

    // ── Connection Closed ────────────────────────────────────────
    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status
    ) {
        log.info("❌ Disconnected: sessionId={} | status={}", session.getId(), status);
        registry.unregister(session);
    }

    // ── Transport Error ──────────────────────────────────────────
    @Override
    public void handleTransportError(
            WebSocketSession session,
            Throwable exception
    ) {
        log.error("❌ Transport error: sessionId={} | error={}",
                session.getId(), exception.getMessage());
        registry.unregister(session);
    }

    // ── Handle Register ──────────────────────────────────────────
    private void handleRegister(WebSocketSession session, WsMessage ws) {
        try {
            Map<String, Object> p = ws.getPayload();
            log.info(">>> Register payload: {}", p);

            String token  = str(p, "token");
            String ip     = str(p, "ipAddress");
            String host   = str(p, "hostName");
            String port   = str(p, "hostPort");
            String os     = str(p, "osType");

            // Validate required fields
            if (token.isBlank()) {
                sendError(session, ws.getRequestId(), "Token is required");
                return;
            }

            if (ip.isBlank()) {
                sendError(session, ws.getRequestId(), "ipAddress is required");
                return;
            }

            // Validate token
            UUID userId = authService.validateToken(token);
            log.info(">>> Validated userId: {}", userId);

            UUID clientId = UUID.randomUUID();

            ConnectedClient client = new ConnectedClient(
                    clientId,
                    userId,
                    token,
                    host,
                    port,
                    ip,
                    os
            );

            registry.register(client, session);
            log.info("✅ Registered: clientId={}", clientId);

            // Send back registered confirmation
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
        boolean registered = registry.getClientBySession(session).isPresent();

        if (!registered) {
            log.warn("⚠️ Heartbeat from unregistered session: {}", session.getId());
            sendError(session, ws.getRequestId(), "Client not registered");
            return;
        }

        log.debug("💓 Heartbeat from sessionId={}", session.getId());

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

        Map<String, Object> p = ws.getPayload();
        String basePath = str(p, "basePath");

        if (basePath.isBlank()) {
            sendError(session, ws.getRequestId(), "basePath is required");
            return;
        }

        log.info(">>> Tunnel create: basePath={} | clientId={}", basePath, client.getClientId());

        tunnelService.createTunnel(new CreateTunnelRequest(
                        client.getUserId(), basePath
                ))
                .thenAccept(tunnelId -> {
                    log.info("✅ Tunnel created: tunnelId={}", tunnelId);
                    send(session, new WsMessage(
                            "tunnel_created",
                            ws.getRequestId(),
                            Map.of(
                                    "tunnelId", tunnelId.toString(),
                                    "basePath", basePath
                            )
                    ));
                })
                .exceptionally(ex -> {
                    log.error("❌ Tunnel create failed: {}", ex.getMessage());
                    sendError(session, ws.getRequestId(),
                            ex.getMessage() != null
                                    ? ex.getMessage()
                                    : ex.getClass().getName());
                    return null;
                });
    }

    // ── Handle HTTP Response from Agent ──────────────────────────
    private void handleHttpResponse(WsMessage ws) {
        String requestId = ws.getRequestId();

        if (requestId == null || requestId.isBlank()) {
            log.warn("⚠️ http_response missing requestId");
            return;
        }

        log.info("✅ http_response received | requestId={}", requestId);

        try {
            Map<String, Object> p = ws.getPayload();

            // ── Try full AgentResponse format ─────────────────
            AgentResponse agentResponse = mapper.convertValue(p, AgentResponse.class);

            if (agentResponse != null &&
                    (agentResponse.body() != null || agentResponse.bodyBase64() != null)) {
                log.info("✓ Full AgentResponse | status={} | binary={}",
                        agentResponse.status(), agentResponse.isBinary());
                proxyService.completeResponse(requestId, agentResponse);
                return;
            }

        } catch (Exception e) {
            log.warn("⚠️ Failed to parse AgentResponse, fallback to plain body: {}", e.getMessage());
        }

        // ── Fallback: plain body string ───────────────────────
        String body = str(ws.getPayload(), "body");
        log.info("✓ Plain body fallback | requestId={} | length={}",
                requestId, body.length());
        proxyService.completeResponse(requestId, body);
    }

    // ── Helpers ──────────────────────────────────────────────────

    private void send(WebSocketSession session, WsMessage msg) {
        try {
            if (!session.isOpen()) {
                log.warn("⚠️ Cannot send, session closed: {}", session.getId());
                return;
            }
            session.sendMessage(new TextMessage(mapper.writeValueAsString(msg)));
        } catch (Exception e) {
            log.error("❌ Send failed: sessionId={} | error={}",
                    session.getId(), e.getMessage());
        }
    }

    private void sendError(
            WebSocketSession session,
            String requestId,
            String error
    ) {
        log.warn("⚠️ Sending error: requestId={} | error={}", requestId, error);
        send(session, new WsMessage(
                "error",
                requestId,
                Map.of("error", error != null ? error : "Unknown error")
        ));
    }

    private String str(Map<String, Object> map, String key) {
        if (map == null) return "";
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }
}