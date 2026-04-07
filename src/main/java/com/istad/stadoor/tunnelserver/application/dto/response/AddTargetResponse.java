package com.istad.stadoor.tunnelserver.application.dto.response;

import java.util.UUID;

public record AddTargetResponse(
        UUID targetId,
        String publicUrl,
        String key,
        int localPort
) {}