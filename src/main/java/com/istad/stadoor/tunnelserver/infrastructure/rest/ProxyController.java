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

    // Exact paths owned by tunnel SERVER
    private static final Set<String> EXCLUDED_EXACT = Set.of(
            "/agent-ws", "/error", "/favicon.ico"
    );

    // Prefix paths owned by tunnel SERVER
    private static final Set<String> EXCLUDED_PREFIX = Set.of(
            "/actuator", "/api/tunnels", "/api/auth", "/api/health"
    );

    // ── Route 1: /{basePath}/{key}/** ─────────────────────────────
    // e.g. /api/d721759b/about
    @RequestMapping("/{basePath}/{key}/**")
    public CompletableFuture<ResponseEntity<byte[]>> proxy(
            @PathVariable String basePath,
            @PathVariable String key,
            HttpServletRequest request) {

        String uri = request.getRequestURI();

        if (isExcluded(uri)) {
            log.warn("⚠️ Excluded: {}", uri);
            return CompletableFuture.completedFuture(
                    ResponseEntity.notFound().build());
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return CompletableFuture.completedFuture(corsResponse());
        }

        log.info(">>> [PROXY] {} | key={} | uri={}", request.getMethod(), key, uri);

        // Store key in session for Next.js asset requests
        request.getSession().setAttribute("tunnel_key", key);

        return proxyService.forward(key, request);
    }

    // ── Route 2: /_next/** ────────────────────────────────────────
    @RequestMapping("/_next/**")
    public CompletableFuture<ResponseEntity<byte[]>> nextAssets(
            HttpServletRequest request) {

        String key = getKeyFromSession(request);
        if (key == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.notFound().build());
        }

        log.info(">>> [NEXT] {} | key={}", request.getRequestURI(), key);
        return proxyService.forwardRaw(key, getFullPath(request), request);
    }

    // ── Route 3: Root static files ────────────────────────────────
    // e.g. /logo.png /favicon.ico /robots.txt
    @RequestMapping("/{file:.+\\.[a-zA-Z0-9]+}")
    public CompletableFuture<ResponseEntity<byte[]>> rootStaticFile(
            @PathVariable String file,
            HttpServletRequest request) {

        String key = getKeyFromSession(request);
        if (key == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.notFound().build());
        }

        log.info(">>> [STATIC] {} | key={}", file, key);
        return proxyService.forwardRaw(key, "/" + file, request);
    }

    // ── Route 4: Sub pages /{page}/** ─────────────────────────────
    // e.g. /about /contact-us /login
    @RequestMapping("/{page}/**")
    public CompletableFuture<ResponseEntity<byte[]>> subPage(
            @PathVariable String page,
            HttpServletRequest request) {

        String uri = request.getRequestURI();

        if (isExcluded(uri)) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.notFound().build());
        }

        String key = getKeyFromSession(request);
        if (key == null) {
            log.warn("⚠️ No tunnel session for: {}", uri);
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
        if (EXCLUDED_EXACT.contains(uri)) return true;
        return EXCLUDED_PREFIX.stream().anyMatch(uri::startsWith);
    }

    private String getKeyFromSession(HttpServletRequest request) {
        return (String) request.getSession().getAttribute("tunnel_key");
    }

    private String getFullPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        return query != null ? uri + "?" + query : uri;
    }

    private ResponseEntity<byte[]> corsResponse() {
        return ResponseEntity.ok()
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods",
                        "GET, POST, PUT, DELETE, PATCH, OPTIONS")
                .header("Access-Control-Allow-Headers", "*")
                .build();
    }
}