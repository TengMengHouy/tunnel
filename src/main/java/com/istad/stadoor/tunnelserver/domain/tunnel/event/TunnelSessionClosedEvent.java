package com.istad.stadoor.tunnelserver.domain.tunnel.event;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;


@Builder
public record TunnelSessionClosedEvent(
        UUID sessionId,
        UUID tunnelId,
        LocalDateTime disconnectedAt) {}
