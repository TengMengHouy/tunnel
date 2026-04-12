package com.istad.stadoor.tunnelserver.query.tunnel.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "tunnel_sessions")
public class TunnelSessionEntity {

    @Id
    private UUID id;

    @Column(name = "tunnel_id", nullable = false)
    private UUID tunnelId;

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    private boolean status;

    @Column(name = "connected_at")
    private LocalDateTime connectedAt;

    @Column(name = "disconnected_at")
    private LocalDateTime disconnectedAt;
}
