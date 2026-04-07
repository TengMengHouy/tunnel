package com.istad.stadoor.tunnelserver.query.tunnel.repository;

import com.istad.stadoor.tunnelserver.query.tunnel.model.TunnelSessionView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TunnelSessionViewRepository extends JpaRepository<TunnelSessionView, UUID> {
    List<TunnelSessionView> findByTunnelId(UUID tunnelId);
}
