package com.istad.stadoor.tunnelserver.infrastructure.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentSessionRegistry {

    private final Map<UUID, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, ConnectedClient> clients = new ConcurrentHashMap<>();
    private final Map<String, UUID> sessionIdToClientId = new ConcurrentHashMap<>();

    public void register(ConnectedClient client, WebSocketSession session) {
        sessions.put(client.getClientId(), session);
        clients.put(client.getClientId(), client);
        sessionIdToClientId.put(session.getId(), client.getClientId());
    }

    public void unregister(WebSocketSession session) {
        UUID clientId = sessionIdToClientId.remove(session.getId());
        if (clientId != null) {
            sessions.remove(clientId);
            clients.remove(clientId);
        }
    }

    public Optional<WebSocketSession> getSession(String clientId) {
        return Optional.ofNullable(sessions.get(clientId))
            .filter(WebSocketSession::isOpen);
    }

    public Optional<ConnectedClient> getClient(String clientId) {
        return Optional.ofNullable(clients.get(clientId));
    }

    public Optional<ConnectedClient> getClientBySession(WebSocketSession session) {
        UUID clientId = sessionIdToClientId.get(session.getId());
        return clientId == null ? Optional.empty() : Optional.ofNullable(clients.get(clientId));
    }

    public boolean isConnected(String clientId) {
        return getSession(clientId).isPresent();
    }
}
