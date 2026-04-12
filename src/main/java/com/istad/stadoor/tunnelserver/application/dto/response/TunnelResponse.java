package com.istad.stadoor.tunnelserver.application.dto.response;

import com.istad.stadoor.tunnelserver.query.tunnel.model.TunnelEntity;
import java.time.LocalDateTime;
import java.util.UUID;

public record TunnelResponse(
    UUID tunnelId,
    UUID userId,
    String basePath,
    boolean active,
    LocalDateTime createdAt
) {
    public static TunnelResponse from(TunnelEntity v) {
        return new TunnelResponse(
            v.getId(),
            v.getUserId(),
            v.getBasePath(),
            v.isStatus(),
            v.getCreatedAt()
        );
    }
}
