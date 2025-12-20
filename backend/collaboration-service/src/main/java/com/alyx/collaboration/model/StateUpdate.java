package com.alyx.collaboration.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

public class StateUpdate {
    @JsonProperty("type")
    private UpdateType type;
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    @JsonProperty("data")
    private Map<String, Object> data;
    
    @JsonProperty("version")
    private long version;
    
    @JsonProperty("sessionId")
    private String sessionId;

    public enum UpdateType {
        PARAMETER_CHANGE,
        QUERY_UPDATE,
        VISUALIZATION_UPDATE,
        CURSOR_MOVE,
        SELECTION_CHANGE
    }

    public StateUpdate() {
        this.timestamp = Instant.now();
    }

    public StateUpdate(UpdateType type, String userId, String sessionId, Map<String, Object> data, long version) {
        this();
        this.type = type;
        this.userId = userId;
        this.sessionId = sessionId;
        this.data = data;
        this.version = version;
    }

    // Getters and setters
    public UpdateType getType() {
        return type;
    }

    public void setType(UpdateType type) {
        this.type = type;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}