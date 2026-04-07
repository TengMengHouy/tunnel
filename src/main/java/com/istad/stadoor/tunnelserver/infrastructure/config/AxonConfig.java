package com.istad.stadoor.tunnelserver.infrastructure.config;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AxonConfig {
    // No InMemoryEventStorageEngine bean.
    // Axon uses PostgreSQL-backed JPA event storage.
}
