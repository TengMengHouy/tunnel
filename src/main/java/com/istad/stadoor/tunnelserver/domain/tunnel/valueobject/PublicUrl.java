package com.istad.stadoor.tunnelserver.domain.tunnel.valueobject;

public record PublicUrl(String value) {
    public PublicUrl {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("PublicUrl cannot be blank");
    }
}
