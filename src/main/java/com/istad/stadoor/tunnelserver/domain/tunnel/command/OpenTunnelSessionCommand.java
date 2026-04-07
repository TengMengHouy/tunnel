package com.istad.stadoor.tunnelserver.domain.tunnel.command;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.util.UUID;

public record OpenTunnelSessionCommand(
        @TargetAggregateIdentifier
        UUID tunnelId,
        UUID sessionId,
        UUID connectionId) {}
