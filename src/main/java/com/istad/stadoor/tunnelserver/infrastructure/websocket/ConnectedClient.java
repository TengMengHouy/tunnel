package com.istad.stadoor.tunnelserver.infrastructure.websocket;

import java.util.UUID;

public class ConnectedClient {

    private final UUID clientId;
    private final UUID userId;
    private final String token;
    private final String hostName;
    private final String hostPort;
    private final String ipAddress;
    private final String osType;

    public ConnectedClient(UUID clientId, UUID userId, String token,
                           String hostName, String hostPort,
                           String ipAddress, String osType) {
        this.clientId = clientId;
        this.userId = userId;
        this.token = token;
        this.hostName = hostName;
        this.hostPort = hostPort;
        this.ipAddress = ipAddress;
        this.osType = osType;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }

    public String getHostName() {
        return hostName;
    }

    public String getHostPort() {
        return hostPort;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getOsType() {
        return osType;
    }
}