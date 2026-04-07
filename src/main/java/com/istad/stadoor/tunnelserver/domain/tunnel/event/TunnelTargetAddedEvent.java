package com.istad.stadoor.tunnelserver.domain.tunnel.event;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record TunnelTargetAddedEvent(
        UUID targetId,
        UUID tunnelId,
        String publicUrl,
        String key,
        String ipAddress,
        int localPort,
        LocalDateTime createdAt
) {}