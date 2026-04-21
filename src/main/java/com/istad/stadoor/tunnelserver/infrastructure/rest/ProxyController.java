package com.istad.stadoor.tunnelserver.infrastructure.rest;

import com.istad.stadoor.tunnelserver.application.service.ProxyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequiredArgsConstructor
@Order(Integer.MAX_VALUE)
public class ProxyController {

    private final ProxyService proxyService;

    // ✅ Exact paths - never proxy these
    private static final Set<String> EXCLUDED_EXACT = Set.of(
            "/agent-ws",
            "/error",
            "/favicon.ico"
    );

    // ✅ Prefix paths - never proxy these
    private static final Set<String> EXCLUDED_PREFIX = Set.of(
            "/actuator",
            "/api/tunnels",
            "/api/auth",
            "/api/health",
            "/ws"
    );

    // ─────────────────────────────────────────────────────────────
    // Route 1: /{basePath}/{key}/**
    // e.g. /api/d721759b/about
    // ─────────────────────────────────────────────────────────────
    @RequestMapping("/{basePath}/{key}/**")
    public CompletableFuture<ResponseEntity<byte[]>> proxy(
            @PathVariable String basePath,
            @PathVariable String key,
            HttpServletRequest request) {

        String uri = request.getRequestURI();

        if (isExcluded(uri) || isWebSocketUpgrade(request)) {
            log.warn("⚠️ Excluded or WS: {}", uri);
            return CompletableFuture.completedFuture(
                    ResponseEntity.notFound().build());
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return corsPreFlight();
        }

        log.info(">>> [PROXY] {} | key={} | uri={}",
                request.getMethod(), key, uri);

        request.getSession().setAttribute("tunnel_key", key);
        return proxyService.forward(key, request);
    }

    // ─────────────────────────────────────────────────────────────
    // Route 2: /_next/**
    // ─────────────────────────────────────────────────────────────
    @RequestMapping("/_next/**")
    public CompletableFuture<ResponseEntity<byte[]>> nextAssets(
            HttpServletRequest request) {

        String key = getKeyFromSession(request);
        if (key == null) {
            log.warn("⚠️ No session for /_next/: {}", request.getRequestURI());
            return CompletableFuture.completedFuture(
                    ResponseEntity.notFound().build());
        }

        log.info(">>> [NEXT] {} | key={}", request.getRequestURI(), key);
        return proxyService.forwardRaw(key, getFullPath(request), request);
    }

    // ─────────────────────────────────────────────────────────────
    // Route 3: Root static files
    // e.g. /logo.png /robots.txt
    // ─────────────────────────────────────────────────────────────
    @RequestMapping("/{file:.+\\.[a-zA-Z0-9]+}")
    public CompletableFuture<ResponseEntity<byte[]>> rootStatic(
            @PathVariable String file,
            HttpServletRequest request) {

        // ✅ Never proxy agent-ws even if regex matches
        if (isExcluded(request.getRequestURI())) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.notFound().build());
        }

        String key = getKeyFromSession(request);
        if (key == null) {
            log.warn("⚠️ No session for static: {}", file);
            return CompletableFuture.completedFuture(
                    ResponseEntity.notFound().build());
        }

        log.info(">>> [STATIC] {} | key={}", file, key);
        return proxyService.forwardRaw(key, "/" + file, request);
    }

    // ─────────────────────────────────────────────────────────────
    // Route 4: Sub pages /{page}/**
    // e.g. /about /contact-us
    // ─────────────────────────────────────────────────────────────
    @RequestMapping("/{page}/**")
    public CompletableFuture<ResponseEntity<byte[]>> subPage(
            @PathVariable String page,
            HttpServletRequest request) {

        String uri = request.getRequestURI();

        // ✅ Critical: exclude /agent-ws and other internal paths
        if (isExcluded(uri) || isWebSocketUpgrade(request)) {
            log.debug("⚠️ Excluded sub-page: {}", uri);
            return CompletableFuture.completedFuture(
                    ResponseEntity.notFound().build());
        }

        String key = getKeyFromSession(request);
        if (key == null) {
            log.warn("⚠️ No session for sub-page: {}", uri);
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(404).build());
        }

        log.info(">>> [SUB-PAGE] {} | key={}", uri, key);
        return proxyService.forwardRaw(key, getFullPath(request), request);
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private boolean isExcluded(String uri) {
        // Check exact match
        if (EXCLUDED_EXACT.contains(uri)) return true;
        // Check if uri starts with any excluded prefix
        return EXCLUDED_PREFIX.stream().anyMatch(uri::startsWith);
    }

    private boolean isWebSocketUpgrade(HttpServletRequest request) {
        return "websocket".equalsIgnoreCase(request.getHeader("Upgrade"));
    }

    private String getKeyFromSession(HttpServletRequest request) {
        return (String) request.getSession().getAttribute("tunnel_key");
    }

    private String getFullPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        return query != null ? uri + "?" + query : uri;
    }

    private CompletableFuture<ResponseEntity<byte[]>> corsPreFlight() {
        return CompletableFuture.completedFuture(
                ResponseEntity.ok()
                        .header("Access-Control-Allow-Origin", "*")
                        .header("Access-Control-Allow-Methods",
                                "GET, POST, PUT, DELETE, PATCH, OPTIONS")
                        .header("Access-Control-Allow-Headers", "*")
                        .build()
        );
    }
}