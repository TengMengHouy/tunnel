package com.istad.stadoor.tunnelserver.infrastructure.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AgentSessionRegistry {

    private final Map<UUID, WebSocketSession> sessions            = new ConcurrentHashMap<>();
    private final Map<UUID, ConnectedClient>  clients             = new ConcurrentHashMap<>();
    private final Map<String, UUID>           sessionIdToClientId = new ConcurrentHashMap<>();
    private final Map<String, UUID>           ipToClientId        = new ConcurrentHashMap<>();

    // ── Register ──────────────────────────────────────────────────
    public void register(ConnectedClient client, WebSocketSession session) {
        sessions.put(client.getClientId(), session);
        clients.put(client.getClientId(), client);
        sessionIdToClientId.put(session.getId(), client.getClientId());

        // ✅ Store IP -> ClientId mapping
        ipToClientId.put(client.getIpAddress(), client.getClientId());

        log.info("✅ Registered: clientId={} | ip={}",
                client.getClientId(), client.getIpAddress());
        log.info("✅ IP Map: {}", ipToClientId);
    }

    // ── Unregister ────────────────────────────────────────────────
    public void unregister(WebSocketSession session) {
        UUID clientId = sessionIdToClientId.remove(session.getId());
        if (clientId != null) {
            ConnectedClient client = clients.remove(clientId);
            sessions.remove(clientId);

            // ✅ Remove IP mapping
            if (client != null) {
                ipToClientId.remove(client.getIpAddress());
                log.info("❌ Unregistered: clientId={} | ip={}",
                        clientId, client.getIpAddress());
            }
        }
    }

    // ── Get Session By IP ─────────────────────────────────────────
    public Optional<WebSocketSession> getSessionByIp(String ipAddress) {
        log.info("🔍 Looking for ip={} | available={}",
                ipAddress, ipToClientId);

        UUID clientId = ipToClientId.get(ipAddress);
        if (clientId == null) {
            log.warn("⚠️ No session for ip={}", ipAddress);
            return Optional.empty();
        }

        return Optional.ofNullable(sessions.get(clientId))
                .filter(WebSocketSession::isOpen);
    }

    // ── Get First Available Session ───────────────────────────────
    public Optional<WebSocketSession> getFirstAvailableSession() {
        return sessions.values().stream()
                .filter(WebSocketSession::isOpen)
                .findFirst();
    }

    // ── Get Session ───────────────────────────────────────────────
    public Optional<WebSocketSession> getSession(UUID clientId) {
        return Optional.ofNullable(sessions.get(clientId))
                .filter(WebSocketSession::isOpen);
    }

    // ── Get Client By Session ─────────────────────────────────────
    public Optional<ConnectedClient> getClientBySession(WebSocketSession session) {
        UUID clientId = sessionIdToClientId.get(session.getId());
        return clientId == null
                ? Optional.empty()
                : Optional.ofNullable(clients.get(clientId));
    }

    // ── Get ClientId By Session ───────────────────────────────────
    public Optional<UUID> getClientIdBySession(WebSocketSession session) {
        return Optional.ofNullable(sessionIdToClientId.get(session.getId()));
    }

    // ── Is Connected ──────────────────────────────────────────────
    public boolean isConnected(UUID clientId) {
        return getSession(clientId).isPresent();
    }
}