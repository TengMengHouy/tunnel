package com.istad.stadoor.tunnelserver.domain.tunnel.event;
import java.time.LocalDateTime;
import java.util.UUID;

public record TunnelCreatedEvent(
        UUID tunnelId,
        UUID userId,
        String basePath,
        String status,
        LocalDateTime createdAt) {}
