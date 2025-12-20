package com.alyx.datarouter.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight representation of a collision event for streaming processing.
 * Optimized for high-throughput Kafka messaging.
 */
public class CollisionEventStream {
    private UUID eventId;
    private Instant timestamp;
    private Double centerOfMassEnergy;
    private Long runNumber;
    private Long eventNumber;
    private List<DetectorHitStream> detectorHits;
    private Map<String, Object> metadata;
    private String checksum;
    
    public CollisionEventStream() {}
    
    public CollisionEventStream(UUID eventId, Instant timestamp, Double centerOfMassEnergy, 
                               Long runNumber, Long eventNumber) {
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.centerOfMassEnergy = centerOfMassEnergy;
        this.runNumber = runNumber;
        this.eventNumber = eventNumber;
    }
    
    // Getters and setters
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    
    public Double getCenterOfMassEnergy() { return centerOfMassEnergy; }
    public void setCenterOfMassEnergy(Double centerOfMassEnergy) { this.centerOfMassEnergy = centerOfMassEnergy; }
    
    public Long getRunNumber() { return runNumber; }
    public void setRunNumber(Long runNumber) { this.runNumber = runNumber; }
    
    public Long getEventNumber() { return eventNumber; }
    public void setEventNumber(Long eventNumber) { this.eventNumber = eventNumber; }
    
    public List<DetectorHitStream> getDetectorHits() { return detectorHits; }
    public void setDetectorHits(List<DetectorHitStream> detectorHits) { this.detectorHits = detectorHits; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CollisionEventStream)) return false;
        CollisionEventStream that = (CollisionEventStream) o;
        return eventId != null && eventId.equals(that.eventId);
    }
    
    @Override
    public int hashCode() {
        return eventId != null ? eventId.hashCode() : 0;
    }
}