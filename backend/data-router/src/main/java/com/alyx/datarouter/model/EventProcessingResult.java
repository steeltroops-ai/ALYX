package com.alyx.datarouter.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Result of processing a collision event through the pipeline.
 */
public class EventProcessingResult {
    private UUID eventId;
    private Instant processingStartTime;
    private Instant processingEndTime;
    private boolean successful;
    private String errorMessage;
    private long processingTimeMs;
    private int reconstructedTracks;
    
    public EventProcessingResult() {}
    
    public EventProcessingResult(UUID eventId, Instant processingStartTime, 
                               Instant processingEndTime, boolean successful) {
        this.eventId = eventId;
        this.processingStartTime = processingStartTime;
        this.processingEndTime = processingEndTime;
        this.successful = successful;
        this.processingTimeMs = processingEndTime.toEpochMilli() - processingStartTime.toEpochMilli();
    }
    
    // Getters and setters
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    
    public Instant getProcessingStartTime() { return processingStartTime; }
    public void setProcessingStartTime(Instant processingStartTime) { this.processingStartTime = processingStartTime; }
    
    public Instant getProcessingEndTime() { return processingEndTime; }
    public void setProcessingEndTime(Instant processingEndTime) { this.processingEndTime = processingEndTime; }
    
    public boolean isSuccessful() { return successful; }
    public void setSuccessful(boolean successful) { this.successful = successful; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    
    public int getReconstructedTracks() { return reconstructedTracks; }
    public void setReconstructedTracks(int reconstructedTracks) { this.reconstructedTracks = reconstructedTracks; }
}