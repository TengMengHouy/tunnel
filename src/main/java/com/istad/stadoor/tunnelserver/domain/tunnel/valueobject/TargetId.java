package com.istad.stadoor.tunnelserver.domain.tunnel.valueobject;
import java.util.Objects; import java.util.UUID;
public record TargetId(UUID value) {
    public TargetId { Objects.requireNonNull(value); }
    public static TargetId create() { return new TargetId(UUID.randomUUID()); }
}
