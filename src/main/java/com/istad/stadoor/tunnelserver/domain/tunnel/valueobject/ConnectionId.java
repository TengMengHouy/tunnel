package com.istad.stadoor.tunnelserver.domain.tunnel.valueobject;
import java.util.Objects; import java.util.UUID;
public record ConnectionId(String value) {
    public ConnectionId { Objects.requireNonNull(value); }
    public static ConnectionId create() { return new ConnectionId(UUID.randomUUID().toString()); }
}
