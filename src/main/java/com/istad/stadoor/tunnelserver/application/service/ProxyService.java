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

    private final Map<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────
    // Forward: Main request (with key in URL)
    // ─────────────────────────────────────────────────────────────
    public CompletableFuture<ResponseEntity<String>> forward(
            String key,
            HttpServletRequest request
    ) {
        String path = extractPath(request.getRequestURI(), key);
        return doForward(key, path, request);
    }

    // ─────────────────────────────────────────────────────────────
    // ForwardRaw: Next.js internal requests (/_next/**, /favicon.ico)
    // Path is already correct, no need to extract
    // ─────────────────────────────────────────────────────────────
    public CompletableFuture<ResponseEntity<String>> forwardRaw(
            String key,
            String path,
            HttpServletRequest request
    ) {
        // ✅ Append query string if present
        String queryString = request.getQueryString();
        String fullPath = queryString != null ? path + "?" + queryString : path;

        log.info(">>> [NEXT.JS RAW] {} {} | key={}", request.getMethod(), fullPath, key);
        return doForward(key, fullPath, request);
    }

    // ─────────────────────────────────────────────────────────────
    // Core forwarding logic
    // ─────────────────────────────────────────────────────────────
    private CompletableFuture<ResponseEntity<String>> doForward(
            String key,
            String path,
            HttpServletRequest request
    ) {
        String requestId = UUID.randomUUID().toString();

        return tunnelService.findTargetByKey(key)
                .thenCompose(target -> {

                    // Guard: No target
                    if (target == null) {
                        log.error("❌ No target found for key={}", key);
                        return CompletableFuture.completedFuture(
                                ResponseEntity.status(404)
                                        .body("No tunnel target found for key: " + key)
                        );
                    }

                    log.info(">>> [PROXY] key={} | ip={} | port={}",
                            key, target.ipAddress(), target.localPort());

                    // Guard: No session
                    Optional<WebSocketSession> sessionOpt = registry
                            .getSessionByIp(target.ipAddress())
                            .or(() -> registry.getFirstAvailableSession());

                    if (sessionOpt.isEmpty()) {
                        log.error("❌ No active agent for ip={}", target.ipAddress());
                        return CompletableFuture.completedFuture(
                                ResponseEntity.status(503)
                                        .body("No active tunnel agent for: " + target.ipAddress())
                        );
                    }

                    WebSocketSession session = sessionOpt.get();

                    // Guard: Session closed
                    if (!session.isOpen()) {
                        log.error("❌ Agent session closed for ip={}", target.ipAddress());
                        return CompletableFuture.completedFuture(
                                ResponseEntity.status(503)
                                        .body("Tunnel agent session is closed")
                        );
                    }

                    String method = request.getMethod();
                    String body   = readBody(request);

                    log.info(">>> [PROXY] {} {} -> port:{}", method, path, target.localPort());

                    // Register pending future
                    CompletableFuture<String> responseFuture = new CompletableFuture<>();
                    pending.put(requestId, responseFuture);

                    // Build agent payload
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("method",    method);
                    payload.put("path",      path);
                    payload.put("localPort", target.localPort());
                    payload.put("headers",   extractHeaders(request));
                    payload.put("body",      body.isEmpty() ? null : body);

                    // Send to agent
                    try {
                        String message = mapper.writeValueAsString(
                                new WsMessage("http_request", requestId, payload)
                        );
                        log.debug(">>> [PROXY] Sending {} bytes", message.length());
                        session.sendMessage(new TextMessage(message));
                        log.info("✓ Sent | requestId={}", requestId);
                    } catch (Exception e) {
                        pending.remove(requestId);
                        log.error("❌ Send failed: {}", e.getMessage());
                        return CompletableFuture.failedFuture(e);
                    }

                    // Wait for response
                    return responseFuture
                            .orTimeout(25, TimeUnit.SECONDS)
                            .thenApply(this::buildResponseEntity)
                            .whenComplete((result, throwable) -> {
                                pending.remove(requestId);
                                if (throwable != null) {
                                    log.error("❌ requestId={} | {}", requestId, throwable.getMessage());
                                } else {
                                    log.info("✓ Done | requestId={}", requestId);
                                }
                            });
                })
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    log.error("❌ Proxy error key={}: {}", key, cause.getMessage());

                    if (cause instanceof TimeoutException) {
                        return ResponseEntity.status(504)
                                .body("Gateway Timeout: Agent did not respond in time");
                    }
                    return ResponseEntity.status(502)
                            .body("Bad Gateway: " + cause.getMessage());
                });
    }

    // ─────────────────────────────────────────────────────────────
    // Complete response from agent
    // ─────────────────────────────────────────────────────────────
    public void completeResponse(String requestId, String body) {
        CompletableFuture<String> future = pending.remove(requestId);
        if (future != null) {
            future.complete(body != null ? body : "");
            log.info("✓ Completed | requestId={}", requestId);
        } else {
            log.warn("⚠️ No pending | requestId={}", requestId);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private ResponseEntity<String> buildResponseEntity(String body) {
        String contentType = detectContentType(body);
        return ResponseEntity.ok()
                .header("Access-Control-Allow-Origin",  "*")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS")
                .header("Access-Control-Allow-Headers", "*")
                .header("Content-Type", contentType)
                .body(body);
    }

    private String extractPath(String uri, String key) {
        int index = uri.indexOf(key);
        if (index == -1) return "/";
        String afterKey = uri.substring(index + key.length());
        return afterKey.isEmpty() ? "/" : afterKey;
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Set<String> skip = Set.of("host", "connection", "transfer-encoding", "upgrade");
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (!skip.contains(name.toLowerCase())) {
                headers.put(name, request.getHeader(name));
            }
        }
        return headers;
    }

    private String detectContentType(String body) {
        if (body == null || body.isBlank()) return "text/plain";
        String t = body.trim();
        if (t.startsWith("{") || t.startsWith("["))            return "application/json";
        if (t.startsWith("<!DOCTYPE") || t.startsWith("<html") ||
                t.startsWith("<HTML")     || t.startsWith("<head")) return "text/html; charset=utf-8";
        if (t.startsWith("<"))                                  return "text/xml";
        return "text/plain";
    }

    private String readBody(HttpServletRequest request) {
        try (BufferedReader reader = request.getReader()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to read body: {}", e.getMessage());
            return "";
        }
    }
}