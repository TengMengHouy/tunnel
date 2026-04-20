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

    public CompletableFuture<ResponseEntity<String>> forward(
            String             key,
            HttpServletRequest request
    ) {
        return tunnelService.findTargetByKey(key)
                .thenCompose(target -> {

                    log.info(">>> [PROXY] key={} | ip={} | port={}",
                            key, target.ipAddress(), target.localPort());

                    // ✅ Find agent WebSocket session by IP
                    // with fallback to first available
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

                    // ✅ Extract path - handle both formats:
                    // /proxy/{key}/path    -> /path
                    // /{basePath}/{key}/path -> /path
                    String path = extractPath(fullUri, key);
                    String body = readBody(request);

                    log.info(">>> [PROXY] {} {} -> port:{}",
                            method, path, target.localPort());

                    // Register pending future
                    CompletableFuture<String> future = new CompletableFuture<>();
                    pending.put(requestId, future);

                    // Build payload
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

                    // Wait for agent response (30s timeout)
                    return future
                            .orTimeout(30, TimeUnit.SECONDS)
                            .thenApply(ResponseEntity::ok)
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

    // ── Extract Path ──────────────────────────────────────────────
    // Handles both:
    // /proxy/002c0b04/client/jph/users  -> /client/jph/users
    // /houy/002c0b04/client/jph/users   -> /client/jph/users
    private String extractPath(String uri, String key) {
        // Find key position in URI
        int keyIndex = uri.indexOf(key);
        if (keyIndex == -1) return "/";

        // Get everything after the key
        String afterKey = uri.substring(keyIndex + key.length());

        // Make sure it starts with /
        return afterKey.isEmpty() ? "/" : afterKey;
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