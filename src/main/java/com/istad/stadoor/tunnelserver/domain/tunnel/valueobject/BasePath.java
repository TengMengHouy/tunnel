package com.istad.stadoor.tunnelserver.domain.tunnel.valueobject;
public record BasePath(String value) {
    public BasePath {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("BasePath cannot be blank");
    }
}
