package com.istad.stadoor.tunnelserver.domain.shared;
import java.util.Objects; import java.util.UUID;



public record UserId(
        UUID value) {
    public UserId { Objects.requireNonNull(value); }
    public static UserId create() { return new UserId(UUID.randomUUID()); }
    public static UserId of(UUID value) { return new UserId(value); }
}
