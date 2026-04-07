package com.istad.stadoor.tunnelserver.query.tunnel.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "tunnels")
public class TunnelView {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "base_path", nullable = false, unique = true)
    private String basePath;

    private boolean status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
