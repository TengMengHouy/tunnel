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
     * Forward HTTP request from tunnel server to the connected agent (client)
     */
    public CompletableFuture<ResponseEntity<String>> forward(String key, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();

        return tunnelService.findTargetByKey(key)
                .thenCompose(target -> {

                    if (target == null) {
                        log.error("❌ No target found for key: {}", key);
                        return CompletableFuture.completedFuture(
                                ResponseEntity.status(404)
                                        .body("No tunnel target registered for key: " + key)
                        );
                    }

                    log.info(">>> [PROXY] key={} | targetIp={} | localPort={}",
                            key, target.ipAddress(), target.localPort());

                    // Get WebSocket session for this target
                    WebSocketSession session = registry.getSessionByIp(target.ipAddress())
                            .or(() -> registry.getFirstAvailableSession())
                            .orElseThrow(() -> new RuntimeException("No active agent connected"));

                    String method = request.getMethod();
                    String path = extractPath(request.getRequestURI(), key);
                    String body = readBody(request);

                    log.info(">>> [PROXY] Forwarding {} {} -> localPort:{}", method, path, target.localPort());

                    CompletableFuture<String> responseFuture = new CompletableFuture<>();
                    pending.put(requestId, responseFuture);

                    // Prepare payload for agent
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("method", method);
                    payload.put("path", path);
                    payload.put("localPort", target.localPort());
                    payload.put("headers", extractHeaders(request));
                    payload.put("body", body.isEmpty() ? null : body);

                    // Send request to agent via WebSocket
                    try {
                        String message = mapper.writeValueAsString(
                                new WsMessage("http_request", requestId, payload)
                        );
                        session.sendMessage(new TextMessage(message));
                        log.info("✓ Request sent to agent | requestId={}", requestId);
                    } catch (Exception e) {
                        pending.remove(requestId);
                        log.error("Failed to send message to agent", e);
                        return CompletableFuture.failedFuture(e);
                    }

                    // Wait for response from agent
                    return responseFuture
                            .orTimeout(25, TimeUnit.SECONDS)
                            .thenApply(this::buildResponseEntity)
                            .whenComplete((result, throwable) -> {
                                pending.remove(requestId);
                                if (throwable != null) {
                                    log.error("❌ [PROXY] Failed for requestId={}: {}", requestId, throwable.toString());
                                } else {
                                    log.info("✓ [PROXY] Response completed for requestId={}", requestId);
                                }
                            });
                })
                .exceptionally(ex -> {
                    log.error("❌ Proxy exception for key={}: {}", key, ex.getMessage(), ex);
                    return ResponseEntity.status(502)
                            .body("Bad Gateway: " + ex.getMessage());
                });
    }

    /**
     * Called by WebSocket handler when agent sends back the response
     */
    public void completeResponse(String requestId, String body) {
        CompletableFuture<String> future = pending.remove(requestId);
        if (future != null) {
            future.complete(body != null ? body : "");
            log.info("✓ Response completed for requestId={}", requestId);
        } else {
            log.warn("⚠️ No pending request found for requestId={}", requestId);
        }
    }

    /**
     * Build Spring ResponseEntity with proper content type and CORS headers
     */
    private ResponseEntity<String> buildResponseEntity(String body) {
        String contentType = detectContentType(body);

        return ResponseEntity.ok()
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS")
                .header("Access-Control-Allow-Headers", "*")
                .header("Content-Type", contentType)
                .body(body);
    }

    /**
     * Extract path after the key (clientId)
     * Example: /api/d721759b/users/profile → /users/profile
     */
    private String extractPath(String uri, String key) {
        int index = uri.indexOf(key);
        if (index == -1) return "/";

        String afterKey = uri.substring(index + key.length());
        return afterKey.isEmpty() || afterKey.equals("/") ? "/" : afterKey;
    }

    /**
     * Extract all headers from request
     */
    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }

    /**
     * Auto detect content type for response
     */
    private String detectContentType(String body) {
        if (body == null || body.isBlank()) return "text/plain";

        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return "application/json";
        } else if (trimmed.startsWith("<!DOCTYPE") || trimmed.startsWith("<html") ||
                trimmed.startsWith("<HTML") || trimmed.startsWith("<head")) {
            return "text/html; charset=utf-8";
        } else if (trimmed.startsWith("<")) {
            return "text/xml";
        }
        return "text/plain";
    }

    /**
     * Read request body
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
            log.warn("Failed to read request body", e);
            return "";
        }
    }
}