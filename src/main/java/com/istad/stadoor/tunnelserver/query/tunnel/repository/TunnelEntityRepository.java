package com.istad.stadoor.tunnelserver.query.tunnel.repository;

import com.istad.stadoor.tunnelserver.query.tunnel.model.TunnelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TunnelEntityRepository extends JpaRepository<TunnelEntity, UUID> {
    List<TunnelEntity> findByUserId(UUID userId);
    Optional<TunnelEntity> findByBasePath(String basePath);
}
