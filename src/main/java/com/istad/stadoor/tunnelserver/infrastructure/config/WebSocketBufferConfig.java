package com.istad.stadoor.tunnelserver.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
public class WebSocketBufferConfig {

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();

        // ✅ Set large buffer sizes to handle big HTML pages (Next.js)
        container.setMaxTextMessageBufferSize(10 * 1024 * 1024);   // 10 MB
        container.setMaxBinaryMessageBufferSize(10 * 1024 * 1024); // 10 MB
        container.setMaxSessionIdleTimeout(300_000L);               // 5 min
        container.setAsyncSendTimeout(30_000L);                     // 30 sec

        return container;
    }
}