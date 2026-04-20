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

    // ✅ Change from /{basePath}/{key}/** to /proxy/{key}/**
    @RequestMapping("/proxy/{key}/**")
    public CompletableFuture<ResponseEntity<String>> proxy(
            @PathVariable String key,
            HttpServletRequest request
    ) {
        log.info(">>> [PROXY] {} /proxy/{}", request.getMethod(), key);
        return proxyService.forward(key, request);
    }
}