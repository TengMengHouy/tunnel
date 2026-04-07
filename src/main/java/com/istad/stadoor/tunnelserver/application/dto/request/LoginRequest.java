package com.istad.stadoor.tunnelserver.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String token
) {}
