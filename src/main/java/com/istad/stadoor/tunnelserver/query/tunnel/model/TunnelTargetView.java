package com.istad.stadoor.tunnelserver.query.tunnel.model;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "tunnel_targets")
public class TunnelTargetView {

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

    protected TunnelTargetView() {}

    public TunnelTargetView(UUID id, UUID tunnelId, String publicUrl, String key, String ipAddress, int localPort, LocalDateTime createdAt) {
        this.id = id;
        this.tunnelId = tunnelId;
        this.publicUrl = publicUrl;
        this.key = key;
        this.ipAddress = ipAddress;
        this.localPort = localPort;
        this.createdAt = createdAt;
    }

}