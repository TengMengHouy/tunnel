package com.istad.stadoor.tunnelserver.infrastructure.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class WebSocketInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            HttpServletRequest  request,
            HttpServletResponse response,
            Object              handler
    ) throws Exception {
        String uri = request.getRequestURI();
        String upgrade = request.getHeader("Upgrade");

        // ✅ If WebSocket upgrade request -> skip proxy
        if ("websocket".equalsIgnoreCase(upgrade)) {
            return true;
        }

        return true;
    }
}