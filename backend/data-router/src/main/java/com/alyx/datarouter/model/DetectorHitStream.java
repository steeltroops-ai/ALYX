package com.alyx.datarouter.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight representation of a detector hit for streaming processing.
 */
public class DetectorHitStream {
    private UUID hitId;
    private String detectorId;
    private Double energyDeposit;
    private Instant hitTime;
    private Double x;
    private Double y;
    private Double z;
    private Double signalAmplitude;
    
    public DetectorHitStream() {}
    
    public DetectorHitStream(UUID hitId, String detectorId, Double energyDeposit, 
                           Instant hitTime, Double x, Double y, Double z) {
        this.hitId = hitId;
        this.detectorId = detectorId;
        this.energyDeposit = energyDeposit;
        this.hitTime = hitTime;
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    // Getters and setters
    public UUID getHitId() { return hitId; }
    public void setHitId(UUID hitId) { this.hitId = hitId; }
    
    public String getDetectorId() { return detectorId; }
    public void setDetectorId(String detectorId) { this.detectorId = detectorId; }
    
    public Double getEnergyDeposit() { return energyDeposit; }
    public void setEnergyDeposit(Double energyDeposit) { this.energyDeposit = energyDeposit; }
    
    public Instant getHitTime() { return hitTime; }
    public void setHitTime(Instant hitTime) { this.hitTime = hitTime; }
    
    public Double getX() { return x; }
    public void setX(Double x) { this.x = x; }
    
    public Double getY() { return y; }
    public void setY(Double y) { this.y = y; }
    
    public Double getZ() { return z; }
    public void setZ(Double z) { this.z = z; }
    
    public Double getSignalAmplitude() { return signalAmplitude; }
    public void setSignalAmplitude(Double signalAmplitude) { this.signalAmplitude = signalAmplitude; }
}