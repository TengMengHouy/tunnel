package com.istad.stadoor.tunnelserver.domain.tunnel.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TunnelTarget {

    private UUID targetId;
    private String publicUrl;
    private String key;
    private String ipAddress;
    private int localPort;
    private LocalDateTime createdAt;
}