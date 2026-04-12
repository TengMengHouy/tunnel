package com.istad.stadoor.tunnelserver.application.service;

import com.istad.stadoor.tunnelserver.application.dto.request.*;
import com.istad.stadoor.tunnelserver.application.dto.response.*;
import com.istad.stadoor.tunnelserver.domain.tunnel.command.*;
import com.istad.stadoor.tunnelserver.query.tunnel.model.*;
import com.istad.stadoor.tunnelserver.query.tunnel.query.*;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class TunnelApplicationService {

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    public TunnelApplicationService(CommandGateway commandGateway, QueryGateway queryGateway) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
    }

    public CompletableFuture<UUID> createTunnel(CreateTunnelRequest req) {
        UUID tunnelId = UUID.randomUUID();
        return commandGateway.send(new CreateTunnelCommand(
                        tunnelId,
                        req.userId(),
                        req.basePath()
                ))
                .thenApply(result -> tunnelId);
    }
    public CompletableFuture<Void> deactivateTunnel(UUID tunnelId) {
        return commandGateway.send(new DeactivateTunnelCommand(tunnelId));
    }

    public CompletableFuture<TargetResponse> addTarget(UUID tunnelId, AddTargetRequest req) {
        return queryGateway.query(
                        new FindTunnelByIdQuery(tunnelId),
                        ResponseTypes.instanceOf(TunnelEntity.class)
                )
                .thenCompose(tunnelView -> {
                    UUID targetId = UUID.randomUUID();
                    String key = UUID.randomUUID().toString().substring(0, 8);
                    String publicUrl = "https://stadoor.com/" + tunnelView.getBasePath() + "/" + key;

                    return commandGateway.send(new AddTunnelTargetCommand(
                                    tunnelId,
                                    targetId,
                                    publicUrl,
                                    key,
                                    req.ipAddress(), // using the IP
                                    req.localPort()
                            ))
                            .thenApply(result -> new TargetResponse(
                                    targetId,
                                    tunnelId,
                                    publicUrl,
                                    key,
                                    req.ipAddress(),
                                    req.localPort(),
                                    java.time.LocalDateTime.now()
                            ));
                });
    }
    public CompletableFuture<UUID> openSession(UUID tunnelId, OpenSessionRequest req) {
        UUID sessionId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        return commandGateway.send(new OpenTunnelSessionCommand(
                        tunnelId,
                        sessionId,
                        connectionId
                ))
                .thenApply(result -> sessionId);
    }

    public CompletableFuture<Void> closeSession(UUID tunnelId, UUID sessionId) {
        return commandGateway.send(new CloseTunnelSessionCommand(tunnelId, sessionId));
    }

    public CompletableFuture<TunnelResponse> findTunnel(UUID tunnelId) {
        return queryGateway.query(
            new FindTunnelByIdQuery(tunnelId),
            ResponseTypes.instanceOf(TunnelEntity.class)
        ).thenApply(TunnelResponse::from);
    }

    public CompletableFuture<List<TunnelResponse>> findTunnelsByUser(UUID userId) {
        return queryGateway.query(
            new FindTunnelsByUserQuery(userId),
            ResponseTypes.multipleInstancesOf(TunnelEntity.class)
        ).thenApply(list -> list.stream().map(TunnelResponse::from).toList());
    }

    public CompletableFuture<List<TargetResponse>> findTargets(UUID tunnelId) {
        return queryGateway.query(
            new FindTargetsByTunnelQuery(tunnelId),
            ResponseTypes.multipleInstancesOf(TunnelTargetEntity.class)
        ).thenApply(list -> list.stream().map(TargetResponse::from).toList());
    }

    public CompletableFuture<List<SessionResponse>> findSessions(UUID tunnelId) {
        return queryGateway.query(
            new FindSessionsByTunnelQuery(tunnelId),
            ResponseTypes.multipleInstancesOf(TunnelSessionEntity.class)
        ).thenApply(list -> list.stream().map(SessionResponse::from).toList());
    }
}
