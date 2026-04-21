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
@Order(Integer.MAX_VALUE) // ✅ Lowest priority
public class ProxyController {

    private final ProxyService proxyService;

    private static final List<String> EXCLUDED = List.of(
            "/ws",
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

        // ✅ Check excluded FIRST
        boolean excluded = EXCLUDED.stream()
                .anyMatch(uri::startsWith);

        if (excluded) {
            log.warn("⚠️ Excluded: {}", uri);
            return CompletableFuture.completedFuture(
                    ResponseEntity.notFound().build()
            );
        }

        // ✅ Check WebSocket
        if ("websocket".equalsIgnoreCase(upgrade)) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.notFound().build()
            );
        }

        // ✅ Handle OPTIONS
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