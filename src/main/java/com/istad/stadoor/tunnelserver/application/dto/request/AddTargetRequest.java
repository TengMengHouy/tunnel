package com.istad.stadoor.tunnelserver.application.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AddTargetRequest(
        @NotBlank
        String ipAddress, // server accepts IP
        @Min(1) @Max(65535) int localPort
) {}