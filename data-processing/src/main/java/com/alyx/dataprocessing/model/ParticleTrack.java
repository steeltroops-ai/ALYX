package com.alyx.dataprocessing.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.locationtech.jts.geom.LineString;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a reconstructed particle track from collision event analysis.
 * Contains trajectory information, momentum, and associated detector hits.
 */
@Entity
@Table(name = "particle_tracks", indexes = {
    @Index(name = "idx_particle_tracks_event_id", columnList = "event_id"),
    @Index(name = "idx_particle_tracks_particle_type", columnList = "particle_type"),
    @Index(name = "idx_particle_tracks_momentum", columnList = "momentum")
})
public class ParticleTrack {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "track_id")
    private UUID trackId;
    
    @NotNull
    @Column(name = "particle_type", nullable = false)
    private String particleType;
    
    @NotNull
    @Column(name = "momentum", nullable = false)
    private Double momentum;
    
    @NotNull
    @Column(name = "charge", nullable = false)
    private Integer charge;
    
    @Column(name = "trajectory", columnDefinition = "geometry(LineString,4326)")
    private LineString trajectory;
    
    @Column(name = "confidence_level")
    private Double confidenceLevel;
    
    @Column(name = "chi_squared")
    private Double chiSquared;
    
    @Column(name = "degrees_of_freedom")
    private Integer degreesOfFreedom;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private CollisionEvent event;
    
    @OneToMany(mappedBy = "track", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TrackHitAssociation> associatedHits = new ArrayList<>();
    
    // Constructors
    public ParticleTrack() {}
    
    public ParticleTrack(String particleType, Double momentum, Integer charge, LineString trajectory) {
        this.particleType = particleType;
        this.momentum = momentum;
        this.charge = charge;
        this.trajectory = trajectory;
    }
    
    // Getters and Setters
    public UUID getTrackId() {
        return trackId;
    }
    
    public void setTrackId(UUID trackId) {
        this.trackId = trackId;
    }
    
    public String getParticleType() {
        return particleType;
    }
    
    public void setParticleType(String particleType) {
        this.particleType = particleType;
    }
    
    public Double getMomentum() {
        return momentum;
    }
    
    public void setMomentum(Double momentum) {
        this.momentum = momentum;
    }
    
    public Integer getCharge() {
        return charge;
    }
    
    public void setCharge(Integer charge) {
        this.charge = charge;
    }
    
    public LineString getTrajectory() {
        return trajectory;
    }
    
    public void setTrajectory(LineString trajectory) {
        this.trajectory = trajectory;
    }
    
    public Double getConfidenceLevel() {
        return confidenceLevel;
    }
    
    public void setConfidenceLevel(Double confidenceLevel) {
        this.confidenceLevel = confidenceLevel;
    }
    
    public Double getChiSquared() {
        return chiSquared;
    }
    
    public void setChiSquared(Double chiSquared) {
        this.chiSquared = chiSquared;
    }
    
    public Integer getDegreesOfFreedom() {
        return degreesOfFreedom;
    }
    
    public void setDegreesOfFreedom(Integer degreesOfFreedom) {
        this.degreesOfFreedom = degreesOfFreedom;
    }
    
    public CollisionEvent getEvent() {
        return event;
    }
    
    public void setEvent(CollisionEvent event) {
        this.event = event;
    }
    
    public List<TrackHitAssociation> getAssociatedHits() {
        return associatedHits;
    }
    
    public void setAssociatedHits(List<TrackHitAssociation> associatedHits) {
        this.associatedHits = associatedHits;
    }
    
    public void addHitAssociation(TrackHitAssociation association) {
        associatedHits.add(association);
        association.setTrack(this);
    }
    
    public void removeHitAssociation(TrackHitAssociation association) {
        associatedHits.remove(association);
        association.setTrack(null);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParticleTrack)) return false;
        ParticleTrack that = (ParticleTrack) o;
        return trackId != null && trackId.equals(that.trackId);
    }
    
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
    
    @Override
    public String toString() {
        return "ParticleTrack{" +
                "trackId=" + trackId +
                ", particleType='" + particleType + '\'' +
                ", momentum=" + momentum +
                ", charge=" + charge +
                '}';
    }
}