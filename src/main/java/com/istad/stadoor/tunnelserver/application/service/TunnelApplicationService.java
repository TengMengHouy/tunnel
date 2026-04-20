package com.istad.stadoor.tunnelserver.application.service;

import com.istad.stadoor.tunnelserver.application.dto.request.*;
import com.istad.stadoor.tunnelserver.application.dto.response.*;
import com.istad.stadoor.tunnelserver.domain.tunnel.command.*;
import com.istad.stadoor.tunnelserver.query.tunnel.model.*;
import com.istad.stadoor.tunnelserver.query.tunnel.query.*;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class TunnelApplicationService {

    private final CommandGateway commandGateway;
    private final QueryGateway   queryGateway;

    public TunnelApplicationService(
            CommandGateway commandGateway,
            QueryGateway   queryGateway
    ) {
        this.commandGateway = commandGateway;
        this.queryGateway   = queryGateway;
    }

    // ── Create Tunnel ────────────────────────────────────────────
    public CompletableFuture<UUID> createTunnel(CreateTunnelRequest request) {
        UUID tunnelId = UUID.randomUUID();
        return commandGateway.<Object>send(new CreateTunnelCommand(
                tunnelId,
                request.userId(),
                request.basePath()
        )).thenApply(r -> tunnelId);
    }

    // ── Deactivate Tunnel ────────────────────────────────────────
    public CompletableFuture<Void> deactivateTunnel(UUID tunnelId) {
        return commandGateway.<Object>send(
                new DeactivateTunnelCommand(tunnelId)
        ).thenApply(r -> null);
    }

    // ── Add Target ───────────────────────────────────────────────
    public CompletableFuture<TargetResponse> addTarget(
            UUID             tunnelId,
            AddTargetRequest request
    ) {
        return queryGateway.query(
                new FindTunnelByIdQuery(tunnelId),
                ResponseTypes.instanceOf(TunnelEntity.class)
        ).thenCompose(tunnel -> {

            UUID   targetId  = UUID.randomUUID();
            String key       = UUID.randomUUID().toString().substring(0, 8);
            String publicUrl = "https://stadoor.com/"
                    + tunnel.getBasePath() + "/" + key;

            return commandGateway.<Object>send(new AddTunnelTargetCommand(
                    tunnelId,
                    targetId,
                    publicUrl,
                    key,
                    request.ipAddress(),
                    request.localPort()
            )).thenApply(r -> new TargetResponse(
                    targetId,
                    tunnelId,
                    publicUrl,
                    key,
                    request.ipAddress(),
                    request.localPort(),
                    LocalDateTime.now()
            ));
        });
    }

    // ── Find Tunnel ──────────────────────────────────────────────
    public CompletableFuture<TunnelResponse> findTunnel(UUID tunnelId) {
        return queryGateway.query(
                new FindTunnelByIdQuery(tunnelId),
                ResponseTypes.instanceOf(TunnelEntity.class)
        ).thenApply(TunnelResponse::from);  // ✅ Use from()
    }

    // ── Find Tunnels By User ─────────────────────────────────────
    public CompletableFuture<List<TunnelResponse>> findTunnelsByUser(UUID userId) {
        return queryGateway.query(
                new FindTunnelsByUserQuery(userId),
                ResponseTypes.multipleInstancesOf(TunnelEntity.class)
        ).thenApply(list -> list.stream()
                .map(TunnelResponse::from)  // ✅ Use from()
                .toList());
    }

    // ── Find Targets ─────────────────────────────────────────────
    public CompletableFuture<List<TargetResponse>> findTargets(UUID tunnelId) {
        return queryGateway.query(
                new FindTargetsByTunnelQuery(tunnelId),
                ResponseTypes.multipleInstancesOf(TunnelTargetEntity.class)
        ).thenApply(list -> list.stream()
                .map(TargetResponse::from)  // ✅ Use from()
                .toList());
    }

    // ── Find Target By Key ───────────────────────────────────────
    public CompletableFuture<TargetResponse> findTargetByKey(String key) {
        return queryGateway.query(
                new FindTargetByKeyQuery(key),
                ResponseTypes.instanceOf(TunnelTargetEntity.class)
        ).thenApply(TargetResponse::from);  // ✅ Use from()
    }

    // ── Open Session ─────────────────────────────────────────────
    public CompletableFuture<UUID> openSession(
            UUID               tunnelId,
            OpenSessionRequest request
    ) {
        UUID sessionId    = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        return commandGateway.<Object>send(new OpenTunnelSessionCommand(
                tunnelId,
                sessionId,
                connectionId
        )).thenApply(r -> sessionId);
    }

    // ── Close Session ────────────────────────────────────────────
    public CompletableFuture<Void> closeSession(UUID tunnelId, UUID sessionId) {
        return commandGateway.<Object>send(new CloseTunnelSessionCommand(
                tunnelId,
                sessionId
        )).thenApply(r -> null);
    }

    // ── Find Sessions ────────────────────────────────────────────
    public CompletableFuture<List<SessionResponse>> findSessions(UUID tunnelId) {
        return queryGateway.query(
                new FindSessionsByTunnelQuery(tunnelId),
                ResponseTypes.multipleInstancesOf(TunnelSessionEntity.class)
        ).thenApply(list -> list.stream()
                .map(SessionResponse::from)  // ✅ Use from()
                .toList());
    }
}