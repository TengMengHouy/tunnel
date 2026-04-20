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

    // ── Forward to Agent ─────────────────────────────────────────
    public CompletableFuture<ResponseEntity<String>> forward(
            String             key,
            HttpServletRequest request
    ) {
        return tunnelService.findTargetByKey(key)
                .thenCompose(target -> {

                    log.info(">>> [PROXY] key={} | ip={} | port={}",
                            key, target.ipAddress(), target.localPort());

                    // Find agent WebSocket session by IP
                    WebSocketSession session = registry
                            .getSessionByIp(target.ipAddress())
                            .orElseThrow(() -> new RuntimeException(
                                    "Agent not connected: " + target.ipAddress()
                            ));

                    String requestId = UUID.randomUUID().toString();
                    String method    = request.getMethod();
                    String path      = request.getRequestURI();
                    String body      = readBody(request);

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
                        log.info("✓ [PROXY] http_request sent: requestId={}", requestId);
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

    // ── Complete Response from Agent ─────────────────────────────
    public void completeResponse(String requestId, String body) {
        CompletableFuture<String> future = pending.get(requestId);
        if (future != null) {
            future.complete(body);
            log.info("✓ [PROXY] Completed: requestId={}", requestId);
        } else {
            log.warn("⚠️ [PROXY] No pending: requestId={}", requestId);
        }
    }

    // ── Read Body ────────────────────────────────────────────────
    private String readBody(HttpServletRequest request) {
        try (BufferedReader reader = request.getReader()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}