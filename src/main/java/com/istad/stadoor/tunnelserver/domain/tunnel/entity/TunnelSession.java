package com.istad.stadoor.tunnelserver.domain.tunnel.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TunnelSession {
    private UUID sessionId;
    private UUID connectionId;
    private boolean active;
    private LocalDateTime connectedAt;
    private LocalDateTime disconnectedAt;


    public void close(LocalDateTime disconnectedAt) {
        this.active = false;
        this.disconnectedAt = disconnectedAt;
    }


}
