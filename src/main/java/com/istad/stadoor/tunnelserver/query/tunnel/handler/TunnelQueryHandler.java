package com.istad.stadoor.tunnelserver.query.tunnel.handler;

import com.istad.stadoor.tunnelserver.query.tunnel.model.*;
import com.istad.stadoor.tunnelserver.query.tunnel.query.*;
import com.istad.stadoor.tunnelserver.query.tunnel.repository.*;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TunnelQueryHandler {

    private final TunnelViewRepository tunnelRepo;
    private final TunnelTargetViewRepository targetRepo;
    private final TunnelSessionViewRepository sessionRepo;

    public TunnelQueryHandler(TunnelViewRepository tunnelRepo,
                              TunnelTargetViewRepository targetRepo,
                              TunnelSessionViewRepository sessionRepo) {
        this.tunnelRepo = tunnelRepo;
        this.targetRepo = targetRepo;
        this.sessionRepo = sessionRepo;
    }

    @QueryHandler
    public TunnelView handle(FindTunnelByIdQuery q) {
        return tunnelRepo.findById(q.tunnelId())
            .orElseThrow(() -> new RuntimeException("Tunnel not found"));
    }

    @QueryHandler
    public List<TunnelView> handle(FindTunnelsByUserQuery q) {
        return tunnelRepo.findByUserId(q.userId());
    }

    @QueryHandler
    public List<TunnelTargetView> handle(FindTargetsByTunnelQuery q) {
        return targetRepo.findByTunnelId(q.tunnelId());
    }

    @QueryHandler
    public List<TunnelSessionView> handle(FindSessionsByTunnelQuery q) {
        return sessionRepo.findByTunnelId(q.tunnelId());
    }
}
