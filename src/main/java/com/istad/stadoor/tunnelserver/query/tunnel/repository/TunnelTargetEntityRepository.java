package com.istad.stadoor.tunnelserver.query.tunnel.repository;

import com.istad.stadoor.tunnelserver.query.tunnel.model.TunnelTargetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TunnelTargetEntityRepository extends JpaRepository<TunnelTargetEntity, UUID> {
    List<TunnelTargetEntity> findByTunnelId(UUID tunnelId);
    Optional<TunnelTargetEntity> findByKey(String key);
}
