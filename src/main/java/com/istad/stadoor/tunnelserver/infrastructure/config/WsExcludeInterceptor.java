package com.istad.stadoor.tunnelserver.infrastructure.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class WsExcludeInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            HttpServletRequest  request,
            HttpServletResponse response,
            Object              handler
    ) throws Exception {
        String uri     = request.getRequestURI();
        String upgrade = request.getHeader("Upgrade");

        // ✅ Block proxy from handling WebSocket
        if (uri.startsWith("/ws") ||
                "websocket".equalsIgnoreCase(upgrade)) {
            response.setStatus(404);
            return false;
        }
        return true;
    }
}