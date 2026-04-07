package com.istad.stadoor.tunnelserver.application.dto.response;

import com.istad.stadoor.tunnelserver.query.tunnel.model.TunnelSessionView;
import java.time.LocalDateTime;
import java.util.UUID;

public record SessionResponse(
    UUID sessionId,
    UUID tunnelId,
    UUID connectionId,
    boolean active,
    LocalDateTime connectedAt,
    LocalDateTime disconnectedAt
) {
    public static SessionResponse from(TunnelSessionView v) {
        return new SessionResponse(
            v.getId(),
            v.getTunnelId(),
            v.getConnectionId(),
            v.isStatus(),
            v.getConnectedAt(),
            v.getDisconnectedAt()
        );
    }
}
