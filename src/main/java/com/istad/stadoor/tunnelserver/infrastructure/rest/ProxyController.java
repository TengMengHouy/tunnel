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

    // ✅ Internal tunnel server paths - never proxy these
    private static final List<String> EXCLUDED = List.of(
            "/ws",
            "/actuator",
            "/error",
            "/favicon.ico",
            "/api/tunnel",
            "/api/register",
            "/api/health"
    );

    // ✅ Next.js internal paths - proxy with stored clientId from session/cookie
    private static final List<String> NEXTJS_INTERNAL = List.of(
            "/_next/",
            "/__nextjs",
            "/favicon.ico"
    );

    // ✅ Route 1: Handle /{basePath}/{key}/** - main tunnel entry
    @RequestMapping("/{basePath}/{key}/**")
    public CompletableFuture<ResponseEntity<String>> proxy(
            @PathVariable String basePath,
            @PathVariable String key,
            HttpServletRequest request
    ) {
        String uri    = request.getRequestURI();
        String method = request.getMethod();
        String upgrade = request.getHeader("Upgrade");

        // ✅ Check excluded paths
        boolean isExcluded = EXCLUDED.stream().anyMatch(uri::startsWith);
        if (isExcluded) {
            log.warn("⚠️ Excluded: {}", uri);
            return CompletableFuture.completedFuture(ResponseEntity.notFound().build());
        }

        // ✅ Check WebSocket upgrade
        if ("websocket".equalsIgnoreCase(upgrade)) {
            return CompletableFuture.completedFuture(ResponseEntity.notFound().build());
        }

        // ✅ Handle OPTIONS (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.ok()
                            .header("Access-Control-Allow-Origin", "*")
                            .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS")
                            .header("Access-Control-Allow-Headers", "*")
                            .body("")
            );
        }

        log.info(">>> [PROXY] {} | ClientID={} | Path={}", method, key, uri);

        // ✅ Store clientId in request attribute for Next.js internal requests
        request.getSession().setAttribute("tunnel_client_key", key);

        return proxyService.forward(key, request);
    }

    // ✅ Route 2: Handle /_next/** - Next.js internal assets
    @RequestMapping("/_next/**")
    public CompletableFuture<ResponseEntity<String>> proxyNextJs(
            HttpServletRequest request
    ) {
        String uri    = request.getRequestURI();
        String method = request.getMethod();

        // ✅ Get clientId from session (set during main page request)
        String key = (String) request.getSession().getAttribute("tunnel_client_key");

        if (key == null) {
            log.warn("⚠️ No tunnel key in session for /_next/ request: {}", uri);
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(404)
                            .body("No active tunnel session")
            );
        }

        log.info(">>> [NEXT.JS] {} | ClientID={} | Path={}", method, key, uri);

        return proxyService.forwardRaw(key, uri, request);
    }

    // ✅ Route 3: Handle /favicon.ico
    @GetMapping("/favicon.ico")
    public CompletableFuture<ResponseEntity<String>> favicon(HttpServletRequest request) {
        String key = (String) request.getSession().getAttribute("tunnel_client_key");
        if (key == null) {
            return CompletableFuture.completedFuture(ResponseEntity.notFound().build());
        }
        return proxyService.forwardRaw(key, "/favicon.ico", request);
    }
}