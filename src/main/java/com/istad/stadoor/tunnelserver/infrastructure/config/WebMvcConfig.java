package com.istad.stadoor.tunnelserver.infrastructure.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(HttpServletRequest request,
                                             HttpServletResponse response,
                                             Object handler) throws Exception {

                        String uri     = request.getRequestURI();
                        String upgrade = request.getHeader("Upgrade");

                        // ✅ If /agent-ws WebSocket upgrade reaches MVC
                        // it means WebSocket handler didn't catch it
                        // Return false to stop processing (don't return 404 body)
                        if ("/agent-ws".equals(uri) &&
                                "websocket".equalsIgnoreCase(upgrade)) {
                            log.warn("⚠️ /agent-ws reached MVC interceptor - should not happen!");
                            return false;
                        }

                        return true;
                    }
                })
                .addPathPatterns("/**")
                .excludePathPatterns("/agent-ws"); // ✅ Exclude from MVC
    }
}