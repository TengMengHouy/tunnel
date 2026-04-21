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
            "/ws/",
            "/api/",
            "/actuator/",
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

        // ✅ Handle OPTIONS preflight for CORS
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

        // ✅ Skip WebSocket upgrade requests
        if ("websocket".equalsIgnoreCase(upgrade)) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.notFound().build()
            );
        }

        // ✅ Skip system paths
        boolean excluded = EXCLUDED.stream()
                .anyMatch(uri::startsWith);

        if (excluded) {
            log.warn("⚠️ Skipping excluded path: {}", uri);
            return CompletableFuture.completedFuture(
                    ResponseEntity.notFound().build()
            );
        }

        log.info(">>> [PROXY] {} | basePath={} | key={}",
                method, basePath, key);

        return proxyService.forward(key, request);
    }
}