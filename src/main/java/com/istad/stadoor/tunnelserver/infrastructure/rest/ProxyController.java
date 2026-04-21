package com.istad.stadoor.tunnelserver.infrastructure.rest;

import com.istad.stadoor.tunnelserver.application.service.ProxyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequiredArgsConstructor
@Order(Integer.MAX_VALUE)
public class ProxyController {

    private final ProxyService proxyService;

    // Only exclude truly internal tunnel server paths
    private static final List<String> EXCLUDED_PATHS = List.of(
            "/ws",
            "/actuator",
            "/error",
            "/favicon.ico",
            "/api/tunnel",      // internal tunnel management
            "/api/register",    // if you have registration endpoint
            "/api/health"
    );

    @RequestMapping("/{basePath}/{key}/**")
    public CompletableFuture<ResponseEntity<String>> proxy(
            @PathVariable String basePath,
            @PathVariable String key,
            HttpServletRequest request) {

        String uri = request.getRequestURI();

        // Check if this is an internal path that should NOT be proxied
        boolean isExcluded = EXCLUDED_PATHS.stream().anyMatch(uri::startsWith);

        if (isExcluded) {
            log.warn("⚠️ Excluded: {}", uri);
            return CompletableFuture.completedFuture(ResponseEntity.notFound().build());
        }

        // Optional: Skip WebSocket upgrade
        if ("websocket".equalsIgnoreCase(request.getHeader("Upgrade"))) {
            return CompletableFuture.completedFuture(ResponseEntity.notFound().build());
        }

        log.info(">>> [PROXY] {} | ClientID={} | Path={}",
                request.getMethod(), key, uri);

        return proxyService.forward(key, request);
    }
}