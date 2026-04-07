package com.istad.stadoor.tunnelserver.application.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;


public record AddTargetRequest(
        @Min(1) @Max(65535) int localPort
) {}