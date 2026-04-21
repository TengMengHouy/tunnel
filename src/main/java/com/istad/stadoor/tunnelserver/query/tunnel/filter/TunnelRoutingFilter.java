package com.istad.stadoor.tunnelserver.infrastructure.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.istad.stadoor.tunnelserver.application.service.ProxyService;
import com.istad.stadoor.tunnelserver.application.service.TunnelApplicationService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class TunnelRoutingFilter implements Filter {

    private final ProxyService proxyService;

    @Value("${tunnel.domain:192.168.43.219.nip.io}")
    private String tunnelDomain;

    // These paths belong to tunnel SERVER itself
    private static final Set<String> SERVER_PATHS = Set.of(
            "/agent-ws", "/actuator", "/error"
    );
    private static final Set<String> SERVER_PREFIXES = Set.of(
            "/api/tunnels", "/api/auth", "/api/health"
    );

    @Override
    public void doFilter(
            ServletRequest  req,
            ServletResponse res,
            FilterChain     chain
    ) throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        String host    = request.getHeader("Host");
        String uri     = request.getRequestURI();
        String upgrade = request.getHeader("Upgrade");

        // ✅ Always pass WebSocket through
        if ("websocket".equalsIgnoreCase(upgrade)) {
            chain.doFilter(req, res);
            return;
        }

        // ✅ Always pass server paths through
        if (isServerPath(uri)) {
            chain.doFilter(req, res);
            return;
        }

        // ✅ Check if subdomain request (ngrok style)
        // Host: 0c682e05.192.168.43.219.nip.io:8080
        String clientKey = extractKeyFromHost(host);

        if (clientKey != null) {
            log.info("🌐 [NGROK] key={} | {} {}",
                    clientKey, request.getMethod(), uri);

            // Handle OPTIONS
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                response.setHeader("Access-Control-Allow-Origin",  "*");
                response.setHeader("Access-Control-Allow-Methods",
                        "GET, POST, PUT, DELETE, PATCH, OPTIONS");
                response.setHeader("Access-Control-Allow-Headers", "*");
                response.setStatus(200);
                return;
            }

            // Forward to agent
            handleTunnelRequest(clientKey, uri, request, response);
            return;
        }

        // ✅ Not a subdomain request - pass to Spring MVC
        chain.doFilter(req, res);
    }

    // ─────────────────────────────────────────────────────────────
    // Extract key from Host header
    // 0c682e05.192.168.43.219.nip.io:8080 → 0c682e05
    // ─────────────────────────────────────────────────────────────
    private String extractKeyFromHost(String host) {
        if (host == null || host.isBlank()) return null;

        // Remove port
        String hostOnly = host.contains(":")
                ? host.substring(0, host.lastIndexOf(":"))
                : host;

        // Must end with our domain
        String suffix = "." + tunnelDomain;
        if (!hostOnly.endsWith(suffix)) return null;

        // Extract subdomain
        String subdomain = hostOnly.substring(
                0, hostOnly.length() - suffix.length()
        );

        // Must be simple key (no dots)
        if (subdomain.isBlank() || subdomain.contains(".")) return null;

        return subdomain;
    }

    // ─────────────────────────────────────────────────────────────
    // Forward to agent via ProxyService
    // ─────────────────────────────────────────────────────────────
    private void handleTunnelRequest(
            String              key,
            String              uri,
            HttpServletRequest  request,
            HttpServletResponse response
    ) throws IOException {
        try {
            String query    = request.getQueryString();
            String fullPath = query != null ? uri + "?" + query : uri;

            var result = proxyService
                    .forwardRaw(key, fullPath, request)
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);

            // Write status
            response.setStatus(result.getStatusCode().value());

            // Write headers
            if (result.getHeaders() != null) {
                result.getHeaders().forEach((name, values) -> {
                    if (values != null && !values.isEmpty()) {
                        response.setHeader(name, values.get(0));
                    }
                });
            }

            // Write body
            if (result.getBody() != null) {
                response.getOutputStream().write(result.getBody());
            }

        } catch (java.util.concurrent.TimeoutException e) {
            log.error("❌ Timeout for key={}", key);
            response.setStatus(504);
            response.getWriter().write("Gateway Timeout");

        } catch (Exception e) {
            log.error("❌ Error for key={}: {}", key, e.getMessage());
            response.setStatus(502);
            response.getWriter().write("Bad Gateway: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Check if path belongs to server
    // ─────────────────────────────────────────────────────────────
    private boolean isServerPath(String uri) {
        if (SERVER_PATHS.contains(uri)) return true;
        return SERVER_PREFIXES.stream().anyMatch(uri::startsWith);
    }
}