package com.istad.stadoor.tunnelserver.domain.tunnel.valueobject;
import java.util.Objects; import java.util.UUID;
public record SessionId(UUID value) {
    public SessionId { Objects.requireNonNull(value); }
    public static SessionId create() { return new SessionId(UUID.randomUUID()); }
}
