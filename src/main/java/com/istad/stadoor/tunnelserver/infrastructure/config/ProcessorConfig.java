package com.istad.stadoor.tunnelserver.infrastructure.config;

import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.extensions.kafka.eventhandling.consumer.streamable.StreamableKafkaMessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProcessorConfig {

    @Autowired
    public void configure(
            EventProcessingConfigurer configurer,
            StreamableKafkaMessageSource<String, byte[]> streamableKafkaMessageSource
    ) {
        configurer.registerTrackingEventProcessor(
                "tunnel-projection",
                config -> streamableKafkaMessageSource
        );
    }
}
