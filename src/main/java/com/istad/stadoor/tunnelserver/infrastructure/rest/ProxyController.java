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

    // ✅ Only exclude actual tunnel server internal paths
    private static final List<String> EXCLUDED = List.of(
            "/ws",
            "/actuator",
            "/error",
            "/api/tunnel",
            "/api/register",
            "/api/health",
            "/agent-ws"
    );

    // ─────────────────────────────────────────────────────────────
    // Route 1: Main tunnel entry /{basePath}/{key}/**
    // Example: /api/d721759b, /api/d721759b/about
    // ─────────────────────────────────────────────────────────────
    @RequestMapping("/{basePath}/{key}/**")
    public CompletableFuture<ResponseEntity<String>> proxy(
            @PathVariable String basePath,
            @PathVariable String key,
            HttpServletRequest request
    ) {
        String uri    = request.getRequestURI();
        String method = request.getMethod();

        // Check excluded
        boolean isExcluded = EXCLUDED.stream().anyMatch(uri::startsWith);
        if (isExcluded) {
            log.warn("⚠️ Excluded: {}", uri);
            return CompletableFuture.completedFuture(
                    ResponseEntity.notFound().build()
            );
        }

        // WebSocket upgrade - skip
        if ("websocket".equalsIgnoreCase(request.getHeader("Upgrade"))) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.notFound().build()
            );
        }

        // OPTIONS - CORS preflight
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

        log.info(">>> [PROXY] {} | ClientID={} | URI={}", method, key, uri);

        // ✅ Store key in session for asset requests
        request.getSession().setAttribute("tunnel_client_key", key);

        return proxyService.forward(key, request);
    }

    // ─────────────────────────────────────────────────────────────
    // Route 2: Next.js internal assets /_next/**
    // Example: /_next/static/chunks/main.js
    //          /_next/image?url=%2Fimage.png
    // ─────────────────────────────────────────────────────────────
    @RequestMapping("/_next/**")
    public CompletableFuture<ResponseEntity<String>> proxyNextAssets(
            HttpServletRequest request
    ) {
        String uri    = request.getRequestURI();
        String method = request.getMethod();

        String key = (String) request.getSession().getAttribute("tunnel_client_key");

        if (key == null) {
            log.warn("⚠️ No tunnel session for /_next/ | uri={}", uri);
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(404)
                            .body("No active tunnel session for Next.js assets")
            );
        }

        log.info(">>> [NEXT] {} {} | key={}", method, uri, key);
        return proxyService.forwardRaw(key, uri, request);
    }

    // ─────────────────────────────────────────────────────────────
    // Route 3: Root-level static files
    // Example: /RTR-LOGO.png, /favicon.ico, /image.png
    // ─────────────────────────────────────────────────────────────
    @RequestMapping("/{filename:.+\\.[a-zA-Z0-9]+}")
    public CompletableFuture<ResponseEntity<String>> proxyRootStaticFile(
            @PathVariable String filename,
            HttpServletRequest request
    ) {
        String uri = request.getRequestURI();

        String key = (String) request.getSession().getAttribute("tunnel_client_key");

        if (key == null) {
            log.warn("⚠️ No tunnel session for static file: {}", filename);
            return CompletableFuture.completedFuture(
                    ResponseEntity.notFound().build()
            );
        }

        log.info(">>> [STATIC] {} | key={}", uri, key);
        return proxyService.forwardRaw(key, uri, request);
    }

    // ─────────────────────────────────────────────────────────────
    // Route 4: Sub-page routes without key in path
    // Example: /contact-us/, /about, /login
    // These come from Next.js navigation after initial page load
    // ─────────────────────────────────────────────────────────────
    @RequestMapping("/{page}/**")
    public CompletableFuture<ResponseEntity<String>> proxySubPage(
            @PathVariable String page,
            HttpServletRequest request
    ) {
        String uri = request.getRequestURI();

        // Skip excluded
        boolean isExcluded = EXCLUDED.stream().anyMatch(uri::startsWith);
        if (isExcluded) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.notFound().build()
            );
        }

        String key = (String) request.getSession().getAttribute("tunnel_client_key");

        if (key == null) {
            log.warn("⚠️ No tunnel session for sub-page: {}", uri);
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(404)
                            .body("No active tunnel session")
            );
        }

        log.info(">>> [SUB-PAGE] {} | key={}", uri, key);
        return proxyService.forwardRaw(key, uri, request);
    }
}