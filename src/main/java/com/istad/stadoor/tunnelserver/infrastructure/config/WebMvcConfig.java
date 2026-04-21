package com.istad.stadoor.tunnelserver.infrastructure.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    // ✅ CORS for all paths
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods(
                        "GET", "POST", "PUT",
                        "DELETE", "PATCH", "OPTIONS"
                )
                .allowedHeaders("*")
                .exposedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }

    // ✅ Intercept and block /ws/** from ProxyController
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(
                    HttpServletRequest  request,
                    HttpServletResponse response,
                    Object              handler
            ) throws Exception {
                String uri     = request.getRequestURI();
                String upgrade = request.getHeader("Upgrade");

                // ✅ Block ProxyController from handling WebSocket paths
                if (uri.startsWith("/ws")) {
                    log.warn("⚠️ Blocking /ws path from MVC: {}", uri);
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return false;
                }

                // ✅ Block WebSocket upgrade from ProxyController
                if ("websocket".equalsIgnoreCase(upgrade)) {
                    log.warn("⚠️ Blocking WebSocket upgrade from MVC: {}", uri);
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return false;
                }

                return true;
            }
        }).addPathPatterns("/{basePath}/{key}/**");
    }
}