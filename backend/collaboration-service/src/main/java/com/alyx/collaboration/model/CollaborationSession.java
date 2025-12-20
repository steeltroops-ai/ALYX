package com.alyx.collaboration.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CollaborationSession {
    @JsonProperty("sessionId")
    private String sessionId;
    
    @JsonProperty("participants")
    private List<Participant> participants = new CopyOnWriteArrayList<>();
    
    @JsonProperty("sharedState")
    private SharedState sharedState;
    
    @JsonProperty("lastUpdate")
    private Instant lastUpdate;
    
    @JsonProperty("createdAt")
    private Instant createdAt;

    public CollaborationSession() {
        this.createdAt = Instant.now();
        this.lastUpdate = Instant.now();
    }

    public CollaborationSession(String sessionId, SharedState initialState) {
        this();
        this.sessionId = sessionId;
        this.sharedState = initialState;
    }

    // Getters and setters
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<Participant> getParticipants() {
        return participants;
    }

    public void setParticipants(List<Participant> participants) {
        this.participants = participants;
    }

    public SharedState getSharedState() {
        return sharedState;
    }

    public void setSharedState(SharedState sharedState) {
        this.sharedState = sharedState;
        this.lastUpdate = Instant.now();
    }

    public Instant getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Instant lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void addParticipant(Participant participant) {
        // Remove existing participant with same userId if present
        participants.removeIf(p -> p.getUserId().equals(participant.getUserId()));
        participants.add(participant);
        this.lastUpdate = Instant.now();
    }

    public void removeParticipant(String userId) {
        participants.removeIf(p -> p.getUserId().equals(userId));
        this.lastUpdate = Instant.now();
    }

    public Participant getParticipant(String userId) {
        return participants.stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .orElse(null);
    }
}