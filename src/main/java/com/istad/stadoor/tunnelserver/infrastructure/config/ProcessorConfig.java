package com.istad.stadoor.tunnelserver.infrastructure.config;

import org.axonframework.config.EventProcessingConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the tunnel-projection processor to use a Subscribing Event Processor
 * that reads directly from Axon's local event bus (backed by the PostgreSQL event store).
 *
 * Kafka is used ONLY for publishing events to external consumers — it is NOT
 * involved in writing to the read-model DB of this service.
 */
@Configuration
public class ProcessorConfig {

    @Autowired
    public void configure(EventProcessingConfigurer configurer) {
        configurer.registerSubscribingEventProcessor("tunnel-projection");
    }
}