package com.istad.stadoor.tunnelserver.query.tunnel.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tunnel_targets")
public class TunnelTargetEntity {

    @Id
    private UUID id;

    @Column(name = "tunnel_id", nullable = false)
    private UUID tunnelId;

    @Column(name = "public_url", nullable = false)
    private String publicUrl;

    @Column(name = "key", nullable = false, unique = true)
    private String key;

    @Column(name = "ip_address", nullable = false) // Database gets IP
    private String ipAddress;

    @Column(name = "local_port", nullable = false)
    private int localPort;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

}