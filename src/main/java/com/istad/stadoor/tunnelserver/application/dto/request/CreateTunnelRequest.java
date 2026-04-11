package com.istad.stadoor.tunnelserver.application.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateTunnelRequest(
        @NotNull(message = "userId is required")
        UUID userId,

        @NotBlank(message = "basePath is required")
        String basePath) {}
