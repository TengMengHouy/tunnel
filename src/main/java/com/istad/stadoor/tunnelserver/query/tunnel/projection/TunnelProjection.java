package com.istad.stadoor.tunnelserver.query.tunnel.projection;

import com.istad.stadoor.tunnelserver.domain.tunnel.event.*;
import com.istad.stadoor.tunnelserver.query.tunnel.model.*;
import com.istad.stadoor.tunnelserver.query.tunnel.repository.*;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

@Component
@ProcessingGroup("tunnel-projection")
public class TunnelProjection {

    private final TunnelViewRepository tunnelRepo;
    private final TunnelTargetViewRepository targetRepo;
    private final TunnelSessionViewRepository sessionRepo;

    public TunnelProjection(TunnelViewRepository tunnelRepo,
                            TunnelTargetViewRepository targetRepo,
                            TunnelSessionViewRepository sessionRepo) {
        this.tunnelRepo = tunnelRepo;
        this.targetRepo = targetRepo;
        this.sessionRepo = sessionRepo;
    }

    @EventHandler
    public void on(TunnelCreatedEvent e) {
        tunnelRepo.save(new TunnelView(
            e.tunnelId(),
            e.userId(),
            e.basePath(),
            true,
            e.createdAt()
        ));
    }

    @EventHandler
    public void on(TunnelTargetAddedEvent e) {
        targetRepo.save(new TunnelTargetView(
                e.targetId(),
                e.tunnelId(),
                e.publicUrl(),
                e.key(),
                e.ipAddress(), // saving to projection
                e.localPort(),
                e.createdAt()
        ));
    }

    @EventHandler
    public void on(TunnelSessionOpenedEvent e) {
        TunnelSessionView tunnelSessionView = TunnelSessionView.builder()
                .id(e.sessionId())
                .tunnelId(e.tunnelId())
                .connectedAt(e.connectedAt())
                .build();
        sessionRepo.save(tunnelSessionView);
    }

    @EventHandler
    public void on(TunnelSessionClosedEvent e) {
        sessionRepo.findById(e.sessionId()).ifPresent(view -> {
            view.setStatus(false);
            view.setDisconnectedAt(e.disconnectedAt());
            sessionRepo.save(view);
        });
    }

    @EventHandler
    public void on(TunnelDeactivatedEvent e) {
        tunnelRepo.findById(e.tunnelId()).ifPresent(view -> {
            view.setStatus(false);
            tunnelRepo.save(view);
        });
    }
}
