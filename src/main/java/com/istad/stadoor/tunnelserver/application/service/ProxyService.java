package com.istad.stadoor.tunnelserver.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.istad.stadoor.tunnelserver.infrastructure.websocket.AgentSessionRegistry;
import com.istad.stadoor.tunnelserver.infrastructure.websocket.WsMessage;
import jakarta.servlet.http.HttpServletRequest;
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
public class ProxyService {

    private final AgentSessionRegistry     registry;
    private final TunnelApplicationService tunnelService;
    private final ObjectMapper             mapper;

    // ✅ Store pending responses from agent
    private final Map<String, CompletableFuture<String>> pending
            = new ConcurrentHashMap<>();

    public ProxyService(
            AgentSessionRegistry     registry,
            TunnelApplicationService tunnelService,
            ObjectMapper             mapper
    ) {
        this.registry      = registry;
        this.tunnelService = tunnelService;
        this.mapper        = mapper;
    }

    // ── Forward Request to Agent ──────────────────────────────────
    public CompletableFuture<ResponseEntity<String>> forward(
            String             key,
            HttpServletRequest request
    ) {
        return tunnelService.findTargetByKey(key)
                .thenCompose(target -> {

                    log.info(">>> [PROXY] key={} | ip={} | port={}",
                            key, target.ipAddress(), target.localPort());

                    // ✅ Find agent by IP with fallback
                    WebSocketSession session = registry
                            .getSessionByIp(target.ipAddress())
                            .orElseGet(() -> {
                                log.warn("⚠️ No session for ip={}, trying first available",
                                        target.ipAddress());
                                return registry.getFirstAvailableSession()
                                        .orElseThrow(() -> new RuntimeException(
                                                "No agent connected"
                                        ));
                            });

                    String requestId = UUID.randomUUID().toString();
                    String method    = request.getMethod();
                    String fullUri   = request.getRequestURI();

                    // ✅ Extract path after key
                    // /houy/002c0b04/client/jph/users  -> /client/jph/users
                    // /nextjs/abc123/about              -> /about
                    String path = extractPath(fullUri, key);
                    String body = readBody(request);

                    log.info(">>> [PROXY] {} {} -> port:{}",
                            method, path, target.localPort());

                    // Register pending future
                    CompletableFuture<String> future = new CompletableFuture<>();
                    pending.put(requestId, future);

                    // Build payload to send agent
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("method",    method);
                    payload.put("path",      path);
                    payload.put("localPort", target.localPort());
                    payload.put("body",      body);

                    // Send to agent via WebSocket
                    try {
                        session.sendMessage(new TextMessage(
                                mapper.writeValueAsString(
                                        new WsMessage("http_request", requestId, payload)
                                )
                        ));
                        log.info("✓ [PROXY] Sent: requestId={}", requestId);
                    } catch (Exception e) {
                        pending.remove(requestId);
                        return CompletableFuture.failedFuture(e);
                    }

                    // ✅ Wait for agent response with CORS headers
                    return future
                            .orTimeout(30, TimeUnit.SECONDS)
                            .thenApply(responseBody -> {
                                // ✅ Detect content type
                                String contentType = detectContentType(responseBody);

                                log.info("✓ [PROXY] Response contentType={}", contentType);

                                return ResponseEntity.ok()
                                        .header("Access-Control-Allow-Origin", "*")
                                        .header("Access-Control-Allow-Methods",
                                                "GET, POST, PUT, DELETE, PATCH, OPTIONS")
                                        .header("Access-Control-Allow-Headers", "*")
                                        .header("Content-Type", contentType)
                                        .body(responseBody);
                            })
                            .whenComplete((r, e) -> {
                                pending.remove(requestId);
                                if (e != null) {
                                    log.error("❌ [PROXY] Failed: {}", e.getMessage());
                                }
                            });
                });
    }

    // ── Complete Response from Agent ──────────────────────────────
    public void completeResponse(String requestId, String body) {
        CompletableFuture<String> future = pending.get(requestId);
        if (future != null) {
            future.complete(body);
            log.info("✓ [PROXY] Completed: requestId={}", requestId);
        } else {
            log.warn("⚠️ [PROXY] No pending: requestId={}", requestId);
        }
    }

    // ── Extract Path After Key ────────────────────────────────────
    // /houy/002c0b04/client/jph/users  -> /client/jph/users
    // /nextjs/abc123/about             -> /about
    // /proxy/abc123/api/users          -> /api/users
    private String extractPath(String uri, String key) {
        int keyIndex = uri.indexOf(key);
        if (keyIndex == -1) return "/";
        String afterKey = uri.substring(keyIndex + key.length());
        return afterKey.isEmpty() ? "/" : afterKey;
    }

    // ── Detect Content Type ───────────────────────────────────────
    private String detectContentType(String body) {
        if (body == null) return "text/plain";
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return "application/json";
        } else if (trimmed.startsWith("<!DOCTYPE") ||
                trimmed.startsWith("<html") ||
                trimmed.startsWith("<HTML")) {
            return "text/html; charset=utf-8";
        } else if (trimmed.startsWith("<")) {
            return "text/xml";
        } else {
            return "text/plain";
        }
    }

    // ── Read Request Body ─────────────────────────────────────────
    private String readBody(HttpServletRequest request) {
        try (BufferedReader reader = request.getReader()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to read body");
            return "";
        }
    }
}