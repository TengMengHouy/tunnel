package com.istad.stadoor.tunnelserver.domain.tunnel.valueobject;
import java.util.Objects; import java.util.UUID;
public record TunnelId(UUID value) {
    public TunnelId { Objects.requireNonNull(value); }
    public static TunnelId create() { return new TunnelId(UUID.randomUUID()); }
}
