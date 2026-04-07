package com.istad.stadoor.tunnelserver.application.dto.response;

import java.util.UUID;

public record IamVerifyTokenResponse(
        boolean valid,
        UUID userId,
        String username,
        String message
) {}