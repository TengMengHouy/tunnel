package com.istad.stadoor.tunnelserver.query.tunnel.repository;

import com.istad.stadoor.tunnelserver.query.tunnel.model.TunnelSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TunnelSessionEntityRepository extends JpaRepository<TunnelSessionEntity, UUID> {
    List<TunnelSessionEntity> findByTunnelId(UUID tunnelId);
}
