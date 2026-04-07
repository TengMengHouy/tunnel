package com.istad.stadoor.tunnelserver.domain.tunnel.aggregate;

import com.istad.stadoor.tunnelserver.domain.tunnel.command.*;
import com.istad.stadoor.tunnelserver.domain.tunnel.entity.TunnelSession;
import com.istad.stadoor.tunnelserver.domain.tunnel.entity.TunnelTarget;
import com.istad.stadoor.tunnelserver.domain.tunnel.event.*;
import com.istad.stadoor.tunnelserver.domain.tunnel.valueobject.TunnelStatus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Aggregate
public class TunnelAggregate {

    @AggregateIdentifier
    private UUID tunnelId;
    private UUID userId;
    private String basePath;
    private TunnelStatus status;
    private LocalDateTime createdAt;

    private List<TunnelTarget> targets = new ArrayList<>();
    private List<TunnelSession> sessions = new ArrayList<>();

    protected TunnelAggregate() {}

    @CommandHandler
    public TunnelAggregate(CreateTunnelCommand cmd) {
        if (cmd.userId() == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (cmd.basePath() == null || cmd.basePath().isBlank()) {
            throw new IllegalArgumentException("basePath is required");
        }

        AggregateLifecycle.apply(new TunnelCreatedEvent(
            cmd.tunnelId(),
            cmd.userId(),
            cmd.basePath(),
            TunnelStatus.ACTIVE.name(),
            LocalDateTime.now()
        ));
    }

    @CommandHandler
    public void handle(AddTunnelTargetCommand cmd) {
        guardActive();

        boolean keyExists = targets.stream()
                .anyMatch(t -> t.getKey().equals(cmd.key()));
        if (keyExists) {
            throw new IllegalArgumentException("Duplicate target key: " + cmd.key());
        }

        if (cmd.ipAddress() == null || cmd.ipAddress().isBlank()) {
            throw new IllegalArgumentException("ipAddress is required");
        }

        if (cmd.localPort() < 1 || cmd.localPort() > 65535) {
            throw new IllegalArgumentException("Invalid local port: " + cmd.localPort());
        }

        AggregateLifecycle.apply(new TunnelTargetAddedEvent(
                cmd.targetId(),
                cmd.tunnelId(),
                cmd.publicUrl(),
                cmd.key(),
                cmd.ipAddress(),
                cmd.localPort(),
                LocalDateTime.now()
        ));
    }

    @CommandHandler
    public void handle(OpenTunnelSessionCommand cmd) {
        guardActive();

        AggregateLifecycle.apply(new TunnelSessionOpenedEvent(
            cmd.sessionId(),
            cmd.tunnelId(),
            cmd.connectionId(),
            LocalDateTime.now()
        ));
    }

    @CommandHandler
    public void handle(CloseTunnelSessionCommand cmd) {
        TunnelSession session = sessions.stream()
            .filter(s -> s.getSessionId().equals(cmd.sessionId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + cmd.sessionId()));

        if (!session.isActive()) {
            return;
        }
        AggregateLifecycle.apply(session
        );
    }

    @CommandHandler
    public void handle(DeactivateTunnelCommand cmd) {
        guardActive();

        sessions.stream()
            .filter(TunnelSession::isActive)
            .forEach(s -> AggregateLifecycle.apply(
                new TunnelSessionClosedEvent(
                    s.getSessionId(),
                    cmd.tunnelId(),
                    LocalDateTime.now()
                )
            ));

        AggregateLifecycle.apply(new TunnelDeactivatedEvent(
            cmd.tunnelId(),
            LocalDateTime.now()
        ));
    }

    @EventSourcingHandler
    public void on(TunnelCreatedEvent event) {
        this.tunnelId = event.tunnelId();
        this.userId = event.userId();
        this.basePath = event.basePath();
        this.status = TunnelStatus.ACTIVE;
        this.createdAt = event.createdAt();
        this.targets = new ArrayList<>();
        this.sessions = new ArrayList<>();
    }

    @EventSourcingHandler
    public void on(TunnelTargetAddedEvent event) {
        this.targets.add(new TunnelTarget(
                event.targetId(),
                event.publicUrl(),
                event.key(),
                event.ipAddress(),
                event.localPort(),
                event.createdAt()
        ));
    }

    @EventSourcingHandler
    public void on(TunnelSessionOpenedEvent event) {
        TunnelSession tunnelSession = TunnelSession.builder()
                .sessionId(event.sessionId())
                .connectionId(event.connectionId())
                .connectedAt(event.connectedAt())
                .build();
        this.sessions.add(tunnelSession);
    }

    @EventSourcingHandler
    public void on(TunnelSessionClosedEvent event) {
        sessions.stream()
            .filter(s -> s.getSessionId().equals(event.sessionId()))
            .findFirst()
            .ifPresent(s -> s.close(event.disconnectedAt()));
    }

    @EventSourcingHandler
    public void on(TunnelDeactivatedEvent event) {
        this.status = TunnelStatus.INACTIVE;
    }

    private void guardActive() {
        if (this.status != TunnelStatus.ACTIVE) {
            throw new IllegalStateException("Tunnel is not active: " + this.tunnelId);
        }
    }
}
