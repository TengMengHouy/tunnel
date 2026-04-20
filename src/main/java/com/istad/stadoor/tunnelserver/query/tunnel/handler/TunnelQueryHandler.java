package com.istad.stadoor.tunnelserver.query.tunnel.handler;

import com.istad.stadoor.tunnelserver.query.tunnel.model.TunnelEntity;
import com.istad.stadoor.tunnelserver.query.tunnel.model.TunnelSessionEntity;
import com.istad.stadoor.tunnelserver.query.tunnel.model.TunnelTargetEntity;
import com.istad.stadoor.tunnelserver.query.tunnel.query.*;
import com.istad.stadoor.tunnelserver.query.tunnel.repository.TunnelEntityRepository;
import com.istad.stadoor.tunnelserver.query.tunnel.repository.TunnelSessionEntityRepository;
import com.istad.stadoor.tunnelserver.query.tunnel.repository.TunnelTargetEntityRepository;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TunnelQueryHandler {

    private final TunnelEntityRepository        tunnelRepo;
    private final TunnelTargetEntityRepository  targetRepo;
    private final TunnelSessionEntityRepository sessionRepo;

    public TunnelQueryHandler(
            TunnelEntityRepository        tunnelRepo,
            TunnelTargetEntityRepository  targetRepo,
            TunnelSessionEntityRepository sessionRepo
    ) {
        this.tunnelRepo  = tunnelRepo;
        this.targetRepo  = targetRepo;
        this.sessionRepo = sessionRepo;
    }

    @QueryHandler
    public TunnelEntity handle(FindTunnelByIdQuery q) {
        return tunnelRepo.findById(q.tunnelId())
                .orElseThrow(() ->
                        new RuntimeException("Tunnel not found: " + q.tunnelId()));
    }

    @QueryHandler
    public List<TunnelEntity> handle(FindTunnelsByUserQuery q) {
        return tunnelRepo.findByUserId(q.userId());
    }

    @QueryHandler
    public List<TunnelTargetEntity> handle(FindTargetsByTunnelQuery q) {
        return targetRepo.findByTunnelId(q.tunnelId());
    }

    @QueryHandler
    public List<TunnelSessionEntity> handle(FindSessionsByTunnelQuery q) {
        return sessionRepo.findByTunnelId(q.tunnelId());
    }

    // 👇 NEW
    @QueryHandler
    public TunnelTargetEntity handle(FindTargetByKeyQuery q) {
        return targetRepo.findByKey(q.key())
                .orElseThrow(() ->
                        new RuntimeException("Target not found: " + q.key()));
    }
}