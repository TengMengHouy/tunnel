package com.istad.stadoor.tunnelserver.infrastructure.websocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentResponse(
        String requestId,
        int status,
        Map<String, String> headers,
        String body,           // text content
        String bodyBase64,     // binary content (images, fonts, etc.)
        boolean isBinary
) {}