package com.istad.stadoor.tunnelserver.application.dto.response;

import com.istad.stadoor.tunnelserver.query.tunnel.model.TunnelTargetView;

import java.time.LocalDateTime;
import java.util.UUID;

public record TargetResponse(
        UUID targetId,
        UUID tunnelId,
        String publicUrl,
        String key,
        String ipAddress,
        int localPort,
        LocalDateTime createdAt
) {
    public static TargetResponse from(TunnelTargetView v) {
        return new TargetResponse(
                v.getId(),
                v.getTunnelId(),
                v.getPublicUrl(),
                v.getKey(),
                v.getIpAddress(),
                v.getLocalPort(),
                v.getCreatedAt()
        );
    }
}