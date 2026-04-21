package com.istad.stadoor.tunnelserver.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.istad.stadoor.tunnelserver.infrastructure.websocket.AgentResponse;
import com.istad.stadoor.tunnelserver.infrastructure.websocket.AgentSessionRegistry;
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

import java.io.IOException;
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

    // ── Forward: extract path after key ──────────────────────────
    public CompletableFuture<ResponseEntity<byte[]>> forward(
            String key,
            HttpServletRequest request) {

        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String path = extractPath(uri, key);
        String fullPath = query != null ? path + "?" + query : path;

        return doForward(key, fullPath, request);
    }

    // ── ForwardRaw: path already correct ─────────────────────────
    public CompletableFuture<ResponseEntity<byte[]>> forwardRaw(
            String key,
            String path,
            HttpServletRequest request) {

        log.info(">>> [RAW] {} | key={}", path, key);
        return doForward(key, path, request);
    }

    // ── Core ──────────────────────────────────────────────────────
    private CompletableFuture<ResponseEntity<byte[]>> doForward(
            String key,
            String path,
            HttpServletRequest request) {

        String requestId = UUID.randomUUID().toString();

        return tunnelService.findTargetByKey(key)
                .thenCompose(target -> {

                    if (target == null) {
                        log.error("❌ No target for key={}", key);
                        return CompletableFuture.completedFuture(
                                ResponseEntity.status(404)
                                        .<byte[]>body(
                                                ("No tunnel target: " + key).getBytes()
                                        )
                        );
                    }

                    Optional<WebSocketSession> sessionOpt = registry
                            .getSessionByIp(target.ipAddress())
                            .or(() -> registry.getFirstAvailableSession());

                    if (sessionOpt.isEmpty()) {
                        log.error("❌ No agent for ip={}", target.ipAddress());
                        return CompletableFuture.completedFuture(
                                ResponseEntity.status(503)
                                        .<byte[]>body("No active agent".getBytes())
                        );
                    }

                    WebSocketSession session = sessionOpt.get();

                    if (!session.isOpen()) {
                        log.error("❌ Session closed for ip={}", target.ipAddress());
                        return CompletableFuture.completedFuture(
                                ResponseEntity.status(503)
                                        .<byte[]>body("Agent session closed".getBytes())
                        );
                    }

                    String method = request.getMethod();
                    String body = readBody(request);

                    log.info(">>> {} {} -> port:{}", method, path, target.localPort());

                    CompletableFuture<AgentResponse> future = new CompletableFuture<>();
                    pending.put(requestId, future);

                    // Build payload
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("method", method);
                    payload.put("path", path);
                    payload.put("localPort", target.localPort());
                    payload.put("headers", extractHeaders(request));
                    payload.put("body", body.isEmpty() ? null : body);

                    // Send to agent
                    try {
                        String msg = mapper.writeValueAsString(
                                new WsMessage("http_request", requestId, payload)
                        );
                        log.debug("Sending {} bytes to agent", msg.length());
                        session.sendMessage(new TextMessage(msg));
                        log.info("✓ Sent | requestId={}", requestId);
                    } catch (Exception e) {
                        pending.remove(requestId);
                        log.error("❌ Send failed: {}", e.getMessage());
                        return CompletableFuture.failedFuture(e);
                    }

                    return future
                            .orTimeout(30, TimeUnit.SECONDS)
                            .thenApply(this::buildResponse)
                            .whenComplete((r, t) -> {
                                pending.remove(requestId);
                                if (t != null) {
                                    log.error("❌ requestId={} | {}", requestId, t.getMessage());
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
                                .<byte[]>body("Gateway Timeout".getBytes());
                    }
                    return ResponseEntity.status(502)
                            .<byte[]>body(("Bad Gateway: " + cause.getMessage()).getBytes());
                });
    }

    // ── Complete from agent (full response) ───────────────────────
    public void completeResponse(String requestId, AgentResponse response) {
        CompletableFuture<AgentResponse> future = pending.remove(requestId);
        if (future != null) {
            future.complete(response);
            log.info("✓ Completed | requestId={}", requestId);
        } else {
            log.warn("⚠️ No pending | requestId={}", requestId);
        }
    }

    // ── Complete from agent (plain string fallback) ───────────────
    public void completeResponse(String requestId, String body) {
        completeResponse(requestId, new AgentResponse(
                requestId, 200, Map.of(), body, null, false
        ));
    }

    // ── Build response ────────────────────────────────────────────
    private ResponseEntity<byte[]> buildResponse(AgentResponse response) {
        HttpStatus status = HttpStatus.resolve(response.status());
        if (status == null) status = HttpStatus.OK;

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);

        // CORS headers
        builder.header("Access-Control-Allow-Origin", "*");
        builder.header("Access-Control-Allow-Methods",
                "GET, POST, PUT, DELETE, PATCH, OPTIONS");
        builder.header("Access-Control-Allow-Headers", "*");

        // Forward agent response headers
        Set<String> skipHeaders = Set.of(
                "content-encoding", "transfer-encoding",
                "connection", "keep-alive", "content-length"
        );

        if (response.headers() != null) {
            response.headers().forEach((k, v) -> {
                if (!skipHeaders.contains(k.toLowerCase())) {
                    builder.header(k, v);
                }
            });
        }

        // Handle binary vs text
        byte[] bodyBytes;
        if (response.isBinary() && response.bodyBase64() != null) {
            bodyBytes = Base64.getDecoder().decode(response.bodyBase64());
        } else if (response.body() != null) {
            bodyBytes = response.body().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } else {
            bodyBytes = new byte[0];
        }

        // Content type
        String contentType = resolveContentType(response, bodyBytes);
        builder.contentType(MediaType.parseMediaType(contentType));

        return builder.body(bodyBytes);
    }

    // ── Helpers ───────────────────────────────────────────────────

    private String resolveContentType(AgentResponse response, byte[] body) {
        if (response.headers() != null) {
            String ct = response.headers().get("content-type");
            if (ct == null) ct = response.headers().get("Content-Type");
            if (ct != null && !ct.isBlank()) return ct;
        }
        return detectContentType(body);
    }

    private String detectContentType(byte[] body) {
        if (body == null || body.length == 0) return "text/plain";
        String start = new String(body, 0, Math.min(50, body.length)).trim();

        if (start.startsWith("{") || start.startsWith("[")) return "application/json";
        if (start.startsWith("<!DOCTYPE") || start.startsWith("<html") ||
                start.startsWith("<HTML")) return "text/html; charset=utf-8";
        if (start.startsWith("<")) return "text/xml";

        // Check magic bytes for images
        if (body.length > 3) {
            if (body[0] == (byte)0xFF && body[1] == (byte)0xD8) return "image/jpeg";
            if (body[0] == (byte)0x89 && body[1] == 'P') return "image/png";
            if (body[0] == 'G' && body[1] == 'I' && body[2] == 'F') return "image/gif";
        }
        return "text/plain";
    }

    private String extractPath(String uri, String key) {
        int index = uri.indexOf(key);
        if (index == -1) return "/";
        String after = uri.substring(index + key.length());
        return after.isEmpty() ? "/" : after;
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

    private String readBody(HttpServletRequest request) {
        try (var reader = request.getReader()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }
}