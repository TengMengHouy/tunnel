package com.istad.stadoor.tunnelserver.query.tunnel.repository;

import com.istad.stadoor.tunnelserver.query.tunnel.model.TunnelView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TunnelViewRepository extends JpaRepository<TunnelView, UUID> {
    List<TunnelView> findByUserId(UUID userId);
    Optional<TunnelView> findByBasePath(String basePath);
}
