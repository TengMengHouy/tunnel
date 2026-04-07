package com.istad.stadoor.tunnelserver.domain.tunnel.command;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.util.UUID;

public record CreateTunnelCommand(
        @TargetAggregateIdentifier
        UUID tunnelId,
        UUID userId,
        String basePath) {}
