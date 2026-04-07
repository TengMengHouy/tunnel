package com.istad.stadoor.tunnelserver.domain.tunnel.event;
import java.time.LocalDateTime;
import java.util.UUID;

public record TunnelSessionOpenedEvent(
        UUID sessionId,
        UUID tunnelId,
        UUID connectionId,
        LocalDateTime connectedAt) {}
