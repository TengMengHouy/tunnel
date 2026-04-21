package com.istad.stadoor.tunnelserver.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.istad.stadoor.tunnelserver.infrastructure.websocket.AgentSessionRegistry;
import com.istad.stadoor.tunnelserver.infrastructure.websocket.AgentResponse;
import com.istad.stadoor.tunnelserver.infrastructure.websocket.WsMessage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    private final Map<String, CompletableFuture<AgentResponse>> pending
            = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────
    // Forward: Main request (extract path after key)
    // ─────────────────────────────────────────────────────────────
    public CompletableFuture<ResponseEntity<String>> forward(
            String key,
            HttpServletRequest request
    ) {
        String uri         = request.getRequestURI();
        String queryString = request.getQueryString();
        String path        = extractPath(uri, key);
        String fullPath    = queryString != null ? path + "?" + queryString : path;

        return doForward(key, fullPath, request);
    }

    // ─────────────────────────────────────────────────────────────
    // ForwardRaw: Next.js assets (/_next/**, /favicon.ico)
    // ─────────────────────────────────────────────────────────────
    public CompletableFuture<ResponseEntity<String>> forwardRaw(
            String key,
            String path,
            HttpServletRequest request
    ) {
        String queryString = request.getQueryString();
        String fullPath    = queryString != null ? path + "?" + queryString : path;

        log.info(">>> [RAW] {} {} | key={}", request.getMethod(), fullPath, key);
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

                    if (target == null) {
                        log.error("❌ No target for key={}", key);
                        return CompletableFuture.completedFuture(
                                ResponseEntity.status(404)
                                        .body("No tunnel target for key: " + key)
                        );
                    }

                    Optional<WebSocketSession> sessionOpt = registry
                            .getSessionByIp(target.ipAddress())
                            .or(() -> registry.getFirstAvailableSession());

                    if (sessionOpt.isEmpty()) {
                        log.error("❌ No agent session for ip={}", target.ipAddress());
                        return CompletableFuture.completedFuture(
                                ResponseEntity.status(503)
                                        .body("No active tunnel agent")
                        );
                    }

                    WebSocketSession session = sessionOpt.get();

                    if (!session.isOpen()) {
                        log.error("❌ Session closed for ip={}", target.ipAddress());
                        return CompletableFuture.completedFuture(
                                ResponseEntity.status(503)
                                        .body("Agent session is closed")
                        );
                    }

                    String method = request.getMethod();
                    String body   = readBody(request);

                    log.info(">>> [PROXY] {} {} -> port:{}", method, path, target.localPort());

                    CompletableFuture<AgentResponse> responseFuture = new CompletableFuture<>();
                    pending.put(requestId, responseFuture);

                    // Build payload
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("method",    method);
                    payload.put("path",      path);
                    payload.put("localPort", target.localPort());
                    payload.put("headers",   extractHeaders(request));
                    payload.put("body",      body.isEmpty() ? null : body);

                    try {
                        String message = mapper.writeValueAsString(
                                new WsMessage("http_request", requestId, payload)
                        );
                        log.debug(">>> Sending {} bytes to agent", message.length());
                        session.sendMessage(new TextMessage(message));
                        log.info("✓ Sent | requestId={}", requestId);
                    } catch (Exception e) {
                        pending.remove(requestId);
                        log.error("❌ Send error: {}", e.getMessage());
                        return CompletableFuture.failedFuture(e);
                    }

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
                    log.error("❌ Error key={}: {}", key, cause.getMessage());

                    if (cause instanceof TimeoutException) {
                        return ResponseEntity.status(504)
                                .body("Gateway Timeout: Agent did not respond");
                    }
                    return ResponseEntity.status(502)
                            .body("Bad Gateway: " + cause.getMessage());
                });
    }

    // ─────────────────────────────────────────────────────────────
    // Complete response from agent
    // ─────────────────────────────────────────────────────────────
    public void completeResponse(String requestId, AgentResponse response) {
        CompletableFuture<AgentResponse> future = pending.remove(requestId);
        if (future != null) {
            future.complete(response);
            log.info("✓ Completed | requestId={}", requestId);
        } else {
            log.warn("⚠️ No pending | requestId={}", requestId);
        }
    }

    // ── backward compat if agent sends plain string ──
    public void completeResponse(String requestId, String body) {
        AgentResponse response = new AgentResponse(
                requestId, 200, Map.of(), body, null, false
        );
        completeResponse(requestId, response);
    }

    // ─────────────────────────────────────────────────────────────
    // Build response with correct status, headers, content-type
    // ─────────────────────────────────────────────────────────────
    private ResponseEntity<String> buildResponseEntity(AgentResponse response) {
        HttpStatus status = HttpStatus.resolve(response.status());
        if (status == null) status = HttpStatus.OK;

        // Detect content type
        String contentType = resolveContentType(response);

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status)
                .header("Access-Control-Allow-Origin",  "*")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS")
                .header("Access-Control-Allow-Headers", "*")
                .header("Content-Type", contentType);

        // Forward safe response headers from agent
        if (response.headers() != null) {
            Set<String> skipHeaders = Set.of(
                    "content-encoding", "transfer-encoding",
                    "connection", "keep-alive"
            );
            response.headers().forEach((k, v) -> {
                if (!skipHeaders.contains(k.toLowerCase())) {
                    builder.header(k, v);
                }
            });
        }

        // Use base64 body if binary
        String body = response.isBinary()
                ? response.bodyBase64()
                : response.body();

        return builder.body(body != null ? body : "");
    }

    // ─────────────────────────────────────────────────────────────
    // Resolve content type from agent response headers or body
    // ─────────────────────────────────────────────────────────────
    private String resolveContentType(AgentResponse response) {
        // Use agent-provided content type first
        if (response.headers() != null) {
            String ct = response.headers().get("content-type");
            if (ct == null) ct = response.headers().get("Content-Type");
            if (ct != null && !ct.isBlank()) return ct;
        }

        // Fallback: detect from body
        return detectContentType(response.body());
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

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