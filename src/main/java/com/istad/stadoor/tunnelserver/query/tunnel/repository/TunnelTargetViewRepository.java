package com.istad.stadoor.tunnelserver.query.tunnel.repository;

import com.istad.stadoor.tunnelserver.query.tunnel.model.TunnelTargetView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TunnelTargetViewRepository extends JpaRepository<TunnelTargetView, UUID> {
    List<TunnelTargetView> findByTunnelId(UUID tunnelId);
    Optional<TunnelTargetView> findByKey(String key);
}
