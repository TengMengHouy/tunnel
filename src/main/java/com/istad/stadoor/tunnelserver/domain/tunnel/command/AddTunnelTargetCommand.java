package com.istad.stadoor.tunnelserver.domain.tunnel.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.util.UUID;

public record AddTunnelTargetCommand(
        @TargetAggregateIdentifier UUID tunnelId,
        UUID targetId,
        String publicUrl,
        String key,
        int localPort
) {}