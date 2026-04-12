package com.istad.stadoor.tunnelserver.query.tunnel.projection;

import com.istad.stadoor.tunnelserver.domain.tunnel.event.*;
import com.istad.stadoor.tunnelserver.query.tunnel.model.*;
import com.istad.stadoor.tunnelserver.query.tunnel.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TunnelProjection {

    private final TunnelEntityRepository tunnelRepo;
    private final TunnelTargetEntityRepository targetRepo;
    private final TunnelSessionEntityRepository sessionRepo;

    public TunnelProjection(TunnelEntityRepository tunnelRepo,
                            TunnelTargetEntityRepository targetRepo,
                            TunnelSessionEntityRepository sessionRepo) {
        this.tunnelRepo = tunnelRepo;
        this.targetRepo = targetRepo;
        this.sessionRepo = sessionRepo;
    }
//Entity
    @EventHandler
    public void on(TunnelCreatedEvent e) {
        tunnelRepo.save(new TunnelEntity(
            e.tunnelId(),
            e.userId(),
            e.basePath(),
            true,
            e.createdAt()
        ));
    }

    @EventHandler
    public void on(TunnelTargetAddedEvent e) {
        log.info(">>> TunnelTargetAddedEvent received: {}", e.targetId()); // add this
        targetRepo.save(new TunnelTargetEntity(
                e.targetId(), e.tunnelId(), e.publicUrl(),
                e.key(), e.ipAddress(), e.localPort(), e.createdAt()
        ));
    }

    @EventHandler
    public void on(TunnelSessionOpenedEvent e) {
        log.info(">>> TunnelSessionOpenedEvent received: {}", e.sessionId()); // add this
        TunnelSessionEntity view = TunnelSessionEntity.builder()
                .id(e.sessionId())
                .tunnelId(e.tunnelId())
                .connectedAt(e.connectedAt())
                .build();
        sessionRepo.save(view);
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
