package com.istad.stadoor.tunnelserver.infrastructure.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.*;

@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    // ✅ CORS
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

    // ✅ Filter to block /ws/** BEFORE Spring MVC
    @Bean
    public FilterRegistrationBean<Filter> wsFilter() {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();

        bean.setFilter((request, response, chain) -> {
            HttpServletRequest  req  = (HttpServletRequest)  request;
            HttpServletResponse resp = (HttpServletResponse) response;

            String uri     = req.getRequestURI();
            String upgrade = req.getHeader("Upgrade");

            // ✅ Let WebSocket pass through
            if (uri.startsWith("/ws") ||
                    "websocket".equalsIgnoreCase(upgrade)) {
                log.debug("✅ WebSocket path - passing through: {}", uri);
                chain.doFilter(request, response);
                return;
            }

            chain.doFilter(request, response);
        });

        bean.addUrlPatterns("/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE); // ✅ Run first!
        bean.setName("wsFilter");

        return bean;
    }
}