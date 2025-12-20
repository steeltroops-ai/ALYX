package com.alyx.collaboration.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class Participant {
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("username")
    private String username;
    
    @JsonProperty("cursor")
    private CursorPosition cursor;
    
    @JsonProperty("selection")
    private SelectionRange selection;
    
    @JsonProperty("isActive")
    private boolean isActive;
    
    @JsonProperty("joinedAt")
    private Instant joinedAt;
    
    @JsonProperty("lastActivity")
    private Instant lastActivity;

    public Participant() {
        this.joinedAt = Instant.now();
        this.lastActivity = Instant.now();
        this.isActive = true;
    }

    public Participant(String userId, String username) {
        this();
        this.userId = userId;
        this.username = username;
    }

    // Getters and setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public CursorPosition getCursor() {
        return cursor;
    }

    public void setCursor(CursorPosition cursor) {
        this.cursor = cursor;
        this.lastActivity = Instant.now();
    }

    public SelectionRange getSelection() {
        return selection;
    }

    public void setSelection(SelectionRange selection) {
        this.selection = selection;
        this.lastActivity = Instant.now();
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
        this.lastActivity = Instant.now();
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    public Instant getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(Instant lastActivity) {
        this.lastActivity = lastActivity;
    }
}