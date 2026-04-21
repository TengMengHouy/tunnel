package com.istad.stadoor.tunnelserver.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.istad.stadoor.tunnelserver.infrastructure.websocket.AgentSessionRegistry;
import com.istad.stadoor.tunnelserver.infrastructure.websocket.WsMessage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.BufferedReader;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyService {

    private final AgentSessionRegistry registry;
    private final TunnelApplicationService tunnelService;
    private final ObjectMapper mapper;

    // Store pending requests: requestId -> CompletableFuture
    private final Map<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    /**
     * Forward HTTP request from tunnel server to the connected agent
     */
    public CompletableFuture<ResponseEntity<String>> forward(
            String key,
            HttpServletRequest request
    ) {
        String requestId = UUID.randomUUID().toString();

        return tunnelService.findTargetByKey(key)
                .thenCompose(target -> {

                    // ── Guard: No target found ──────────────────
                    if (target == null) {
                        log.error("❌ No target found for key={}", key);
                        return CompletableFuture.completedFuture(
                                ResponseEntity.status(404)
                                        .body("No tunnel target found for key: " + key)
                        );
                    }

                    log.info(">>> [PROXY] key={} | ip={} | port={}",
                            key, target.ipAddress(), target.localPort());

                    // ── Guard: No agent session ─────────────────
                    Optional<WebSocketSession> sessionOpt = registry
                            .getSessionByIp(target.ipAddress())
                            .or(() -> registry.getFirstAvailableSession());

                    if (sessionOpt.isEmpty()) {
                        log.error("❌ No active agent session for ip={}", target.ipAddress());
                        return CompletableFuture.completedFuture(
                                ResponseEntity.status(503)
                                        .body("No active tunnel agent for: " + target.ipAddress())
                        );
                    }

                    WebSocketSession session = sessionOpt.get();

                    // ── Guard: Session not open ─────────────────
                    if (!session.isOpen()) {
                        log.error("❌ Agent session is closed for ip={}", target.ipAddress());
                        return CompletableFuture.completedFuture(
                                ResponseEntity.status(503)
                                        .body("Tunnel agent session is closed")
                        );
                    }

                    String method = request.getMethod();
                    String path   = extractPath(request.getRequestURI(), key);
                    String body   = readBody(request);

                    log.info(">>> [PROXY] {} {} -> port:{}", method, path, target.localPort());

                    // Register pending future
                    CompletableFuture<String> responseFuture = new CompletableFuture<>();
                    pending.put(requestId, responseFuture);

                    // Build payload to send to agent
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("method",    method);
                    payload.put("path",      path);
                    payload.put("localPort", target.localPort());
                    payload.put("headers",   extractHeaders(request));
                    payload.put("body",      body.isEmpty() ? null : body);

                    // Send request to agent via WebSocket
                    try {
                        String message = mapper.writeValueAsString(
                                new WsMessage("http_request", requestId, payload)
                        );

                        // ✅ Check message size before sending
                        log.debug(">>> [PROXY] Message size: {} bytes", message.length());

                        session.sendMessage(new TextMessage(message));
                        log.info("✓ Sent http_request to agent | requestId={}", requestId);

                    } catch (Exception e) {
                        pending.remove(requestId);
                        log.error("❌ Failed to send message to agent: {}", e.getMessage());
                        return CompletableFuture.failedFuture(e);
                    }

                    // Wait for agent response
                    return responseFuture
                            .orTimeout(25, TimeUnit.SECONDS)
                            .thenApply(responseBody -> buildResponseEntity(responseBody))
                            .whenComplete((result, throwable) -> {
                                pending.remove(requestId);
                                if (throwable != null) {
                                    log.error("❌ [PROXY] requestId={} | error={}",
                                            requestId, throwable.getMessage());
                                } else {
                                    log.info("✓ [PROXY] requestId={} completed", requestId);
                                }
                            });
                })
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    log.error("❌ Proxy error for key={}: {}", key, cause.getMessage());

                    if (cause instanceof TimeoutException) {
                        return ResponseEntity.status(504)
                                .body("Gateway Timeout: Agent did not respond in time");
                    }

                    return ResponseEntity.status(502)
                            .body("Bad Gateway: " + cause.getMessage());
                });
    }

    /**
     * Called by WebSocketHandler when agent sends back response
     */
    public void completeResponse(String requestId, String body) {
        CompletableFuture<String> future = pending.remove(requestId);
        if (future != null) {
            future.complete(body != null ? body : "");
            log.info("✓ Response completed | requestId={}", requestId);
        } else {
            log.warn("⚠️ No pending request found | requestId={}", requestId);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Build ResponseEntity with CORS headers and correct Content-Type
     */
    private ResponseEntity<String> buildResponseEntity(String body) {
        String contentType = detectContentType(body);
        return ResponseEntity.ok()
                .header("Access-Control-Allow-Origin",  "*")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS")
                .header("Access-Control-Allow-Headers", "*")
                .header("Content-Type", contentType)
                .body(body);
    }

    /**
     * Extract path after key segment
     * /api/d721759b/users/profile → /users/profile
     * /api/d721759b               → /
     */
    private String extractPath(String uri, String key) {
        int index = uri.indexOf(key);
        if (index == -1) return "/";
        String afterKey = uri.substring(index + key.length());
        return afterKey.isEmpty() ? "/" : afterKey;
    }

    /**
     * Extract headers from request (skip problematic ones)
     */
    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();

        // Skip headers that should not be forwarded
        Set<String> skipHeaders = Set.of(
                "host", "connection", "transfer-encoding", "upgrade"
        );

        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (!skipHeaders.contains(name.toLowerCase())) {
                headers.put(name, request.getHeader(name));
            }
        }
        return headers;
    }

    /**
     * Auto detect content type from response body
     */
    private String detectContentType(String body) {
        if (body == null || body.isBlank()) return "text/plain";
        String trimmed = body.trim();

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return "application/json";
        } else if (trimmed.startsWith("<!DOCTYPE") ||
                trimmed.startsWith("<html")     ||
                trimmed.startsWith("<HTML")     ||
                trimmed.startsWith("<head")) {
            return "text/html; charset=utf-8";
        } else if (trimmed.startsWith("<")) {
            return "text/xml";
        }
        return "text/plain";
    }

    /**
     * Read request body safely
     */
    private String readBody(HttpServletRequest request) {
        try (BufferedReader reader = request.getReader()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to read request body: {}", e.getMessage());
            return "";
        }
    }
}