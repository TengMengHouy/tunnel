package com.istad.stadoor.tunnelserver.application.dto.response;

import java.util.UUID;

public record LoginResponse(
        UUID userId,
        String token
) {}