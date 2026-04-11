package com.istad.stadoor.tunnelserver.infrastructure.config;

import org.springframework.context.annotation.Configuration;

/**
 * Kafka is configured only for EVENT PUBLISHING (producer) to allow
 * external services to consume tunnel events.
 *
 * This service no longer consumes events from Kafka.
 * The tunnel-projection reads directly from Axon's local event bus (PostgreSQL-backed).
 *
 * Producer settings are handled via application.yml (axon.kafka.producer.*).
 */
@Configuration
public class KafkaConfig {
    // No consumer beans needed — Kafka is outbound-only for this service.
}