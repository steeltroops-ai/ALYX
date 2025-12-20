package com.alyx.dataprocessing.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single detector hit from a particle collision event.
 * Contains spatial coordinates, energy measurements, and timing information.
 */
@Entity
@Table(name = "detector_hits", indexes = {
    @Index(name = "idx_detector_hits_event_id", columnList = "event_id"),
    @Index(name = "idx_detector_hits_detector_id", columnList = "detector_id"),
    @Index(name = "idx_detector_hits_energy", columnList = "energy_deposit")
})
public class DetectorHit {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "hit_id")
    private UUID hitId;
    
    @NotNull
    @Column(name = "detector_id", nullable = false)
    private String detectorId;
    
    @NotNull
    @Column(name = "energy_deposit", nullable = false)
    private Double energyDeposit;
    
    @NotNull
    @Column(name = "hit_time", nullable = false)
    private Instant hitTime;
    
    @Column(name = "position", columnDefinition = "geometry(Point,4326)")
    private Point position;
    
    @Column(name = "signal_amplitude")
    private Double signalAmplitude;
    
    @Column(name = "uncertainty")
    private Double uncertainty;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private CollisionEvent event;
    
    // Constructors
    public DetectorHit() {}
    
    public DetectorHit(String detectorId, Double energyDeposit, Instant hitTime, Point position) {
        this.detectorId = detectorId;
        this.energyDeposit = energyDeposit;
        this.hitTime = hitTime;
        this.position = position;
    }
    
    // Getters and Setters
    public UUID getHitId() {
        return hitId;
    }
    
    public void setHitId(UUID hitId) {
        this.hitId = hitId;
    }
    
    public String getDetectorId() {
        return detectorId;
    }
    
    public void setDetectorId(String detectorId) {
        this.detectorId = detectorId;
    }
    
    public Double getEnergyDeposit() {
        return energyDeposit;
    }
    
    public void setEnergyDeposit(Double energyDeposit) {
        this.energyDeposit = energyDeposit;
    }
    
    public Instant getHitTime() {
        return hitTime;
    }
    
    public void setHitTime(Instant hitTime) {
        this.hitTime = hitTime;
    }
    
    public Point getPosition() {
        return position;
    }
    
    public void setPosition(Point position) {
        this.position = position;
    }
    
    public Double getSignalAmplitude() {
        return signalAmplitude;
    }
    
    public void setSignalAmplitude(Double signalAmplitude) {
        this.signalAmplitude = signalAmplitude;
    }
    
    public Double getUncertainty() {
        return uncertainty;
    }
    
    public void setUncertainty(Double uncertainty) {
        this.uncertainty = uncertainty;
    }
    
    public CollisionEvent getEvent() {
        return event;
    }
    
    public void setEvent(CollisionEvent event) {
        this.event = event;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DetectorHit)) return false;
        DetectorHit that = (DetectorHit) o;
        return hitId != null && hitId.equals(that.hitId);
    }
    
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
    
    @Override
    public String toString() {
        return "DetectorHit{" +
                "hitId=" + hitId +
                ", detectorId='" + detectorId + '\'' +
                ", energyDeposit=" + energyDeposit +
                ", hitTime=" + hitTime +
                '}';
    }
}