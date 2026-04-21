package com.istad.stadoor.tunnelserver.infrastructure.rest;

import com.istad.stadoor.tunnelserver.application.service.ProxyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ProxyController {

    private final ProxyService proxyService;

    // ✅ Paths that should NOT be proxied
    private static final List<String> EXCLUDED = List.of(
            "/ws",        // ✅ WebSocket - no trailing slash needed!
            "/api",
            "/actuator",
            "/error",
            "/favicon.ico"
    );

    @RequestMapping("/{basePath}/{key}/**")
    public CompletableFuture<ResponseEntity<String>> proxy(
            @PathVariable String basePath,
            @PathVariable String key,
            HttpServletRequest request
    ) {
        String uri     = request.getRequestURI();
        String upgrade = request.getHeader("Upgrade");
        String method  = request.getMethod();

        // ✅ FIRST - Check excluded paths
        boolean excluded = EXCLUDED.stream()
                .anyMatch(uri::startsWith);

        if (excluded) {
            log.warn("⚠️ Skipping excluded: {}", uri);
            return CompletableFuture.completedFuture(
                    ResponseEntity.notFound().build()
            );
        }

        // ✅ SECOND - Skip WebSocket upgrade
        if ("websocket".equalsIgnoreCase(upgrade)) {
            log.warn("⚠️ Skipping WebSocket upgrade: {}", uri);
            return CompletableFuture.completedFuture(
                    ResponseEntity.notFound().build()
            );
        }

        // ✅ THIRD - Handle OPTIONS preflight
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.ok()
                            .header("Access-Control-Allow-Origin", "*")
                            .header("Access-Control-Allow-Methods",
                                    "GET, POST, PUT, DELETE, PATCH, OPTIONS")
                            .header("Access-Control-Allow-Headers", "*")
                            .body("")
            );
        }

        log.info(">>> [PROXY] {} | basePath={} | key={}",
                method, basePath, key);

        return proxyService.forward(key, request);
    }
}