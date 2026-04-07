package com.istad.stadoor.tunnelserver.domain.tunnel.event;
import java.time.LocalDateTime;
import java.util.UUID;

public record TunnelDeactivatedEvent(
        UUID tunnelId,
        LocalDateTime deactivatedAt) {}
