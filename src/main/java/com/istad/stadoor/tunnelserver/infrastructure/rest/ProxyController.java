package com.istad.stadoor.tunnelserver.infrastructure.rest;

import com.istad.stadoor.tunnelserver.application.service.ProxyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ProxyController {

    private final ProxyService proxyService;

    @RequestMapping("/{basePath}/{key}/**")
    public CompletableFuture<ResponseEntity<String>> proxy(
            @PathVariable String basePath,
            @PathVariable String key,
            HttpServletRequest request
    ) {
        String uri = request.getRequestURI();

        // ✅ Exclude system paths
        if (uri.startsWith("/ws") ||
                uri.startsWith("/api") ||
                uri.startsWith("/actuator")) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.notFound().build()
            );
        }

        log.info(">>> [PROXY] {} /{}/{}",
                request.getMethod(), basePath, key);

        return proxyService.forward(key, request);
    }
}