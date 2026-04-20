package com.istad.stadoor.tunnelserver.infrastructure.config;

import org.axonframework.config.EventProcessingConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProcessorConfig {

    @Autowired
    public void configure(EventProcessingConfigurer configurer) {
        // ✅ Register ALL processors as subscribing
        configurer.registerSubscribingEventProcessor("tunnel-projection");
        configurer.registerSubscribingEventProcessor(
                "com.istad.stadoor.tunnelserver.query.tunnel.projection"
        );
        configurer.usingSubscribingEventProcessors(); // 👈 Force ALL to subscribing
    }
}