package com.istad.stadoor.tunnelserver.infrastructure.rest;

import com.istad.stadoor.tunnelserver.application.dto.request.*;
import com.istad.stadoor.tunnelserver.application.dto.response.*;
import com.istad.stadoor.tunnelserver.application.service.TunnelApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/tunnels")
public class TunnelController {

    private final TunnelApplicationService svc;

    public TunnelController(TunnelApplicationService svc) {
        this.svc = svc;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<Map<String, String>>> create(
            @Valid @RequestBody CreateTunnelRequest req) {
        return svc.createTunnel(req)
                .thenApply(id -> ResponseEntity
                        .created(URI.create("/api/tunnels/" + id))
                        .body(Map.of("tunnelId", id.toString())));
    }

    @GetMapping("/{tunnelId}")
    public CompletableFuture<ResponseEntity<TunnelResponse>> get(@PathVariable UUID tunnelId) {
        return svc.findTunnel(tunnelId).thenApply(ResponseEntity::ok);
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<List<TunnelResponse>>> list(@RequestParam UUID userId) {
        return svc.findTunnelsByUser(userId).thenApply(ResponseEntity::ok);
    }

    @PostMapping("/{tunnelId}/deactivate")
    public CompletableFuture<ResponseEntity<Void>> deactivate(@PathVariable UUID tunnelId) {
        return svc.deactivateTunnel(tunnelId).thenApply(v -> ResponseEntity.ok().build());
    }

    @PostMapping("/{tunnelId}/targets")
    public CompletableFuture<ResponseEntity<AddTargetResponse>> addTarget(
            @PathVariable UUID tunnelId,
            @Valid @RequestBody AddTargetRequest req) {
        return svc.addTarget(tunnelId, req)
                .thenApply(response -> ResponseEntity
                        .created(URI.create("/api/tunnels/" + tunnelId + "/targets/" + response.targetId()))
                        .body(response));
    }

    @GetMapping("/{tunnelId}/targets")
    public CompletableFuture<ResponseEntity<List<TargetResponse>>> targets(@PathVariable UUID tunnelId) {
        return svc.findTargets(tunnelId).thenApply(ResponseEntity::ok);
    }

    @PostMapping("/{tunnelId}/sessions")
    public CompletableFuture<ResponseEntity<Map<String, UUID>>> openSession(
            @PathVariable UUID tunnelId,
            @Valid @RequestBody OpenSessionRequest req) {
        return svc.openSession(tunnelId, req)
            .thenApply(id -> ResponseEntity.ok(Map.of("sessionId", id)));
    }

    @GetMapping("/{tunnelId}/sessions")
    public CompletableFuture<ResponseEntity<List<SessionResponse>>> sessions(@PathVariable UUID tunnelId) {
        return svc.findSessions(tunnelId).thenApply(ResponseEntity::ok);
    }

    @PostMapping("/{tunnelId}/sessions/{sessionId}/close")
    public CompletableFuture<ResponseEntity<Void>> closeSession(@PathVariable UUID tunnelId,
                                                                @PathVariable UUID sessionId) {
        return svc.closeSession(tunnelId, sessionId).thenApply(v -> ResponseEntity.ok().build());
    }
}
