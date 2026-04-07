package com.istad.stadoor.tunnelserver.application.dto.request;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateTunnelRequest(
        @NotBlank UUID userId,
        @NotBlank String basePath) {}
