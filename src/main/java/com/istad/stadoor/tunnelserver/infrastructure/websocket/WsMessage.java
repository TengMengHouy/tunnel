package com.istad.stadoor.tunnelserver.infrastructure.websocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WsMessage {

    private String type;
    private String requestId;
    private Map<String, Object> payload;

    public WsMessage() {}

    public WsMessage(String type, String requestId, Map<String, Object> payload) {
        this.type = type;
        this.requestId = requestId;
        this.payload = payload;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
}
