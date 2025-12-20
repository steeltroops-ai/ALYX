package com.alyx.dataprocessing.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Association entity linking particle tracks to detector hits.
 * Represents the many-to-many relationship between tracks and hits.
 */
@Entity
@Table(name = "track_hit_associations", indexes = {
    @Index(name = "idx_track_hit_track_id", columnList = "track_id"),
    @Index(name = "idx_track_hit_hit_id", columnList = "hit_id")
})
public class TrackHitAssociation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "association_id")
    private UUID associationId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", nullable = false)
    private ParticleTrack track;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hit_id", nullable = false)
    private DetectorHit hit;
    
    @NotNull
    @Column(name = "association_weight", nullable = false)
    private Double associationWeight;
    
    @Column(name = "residual")
    private Double residual;
    
    // Constructors
    public TrackHitAssociation() {}
    
    public TrackHitAssociation(ParticleTrack track, DetectorHit hit, Double associationWeight) {
        this.track = track;
        this.hit = hit;
        this.associationWeight = associationWeight;
    }
    
    // Getters and Setters
    public UUID getAssociationId() {
        return associationId;
    }
    
    public void setAssociationId(UUID associationId) {
        this.associationId = associationId;
    }
    
    public ParticleTrack getTrack() {
        return track;
    }
    
    public void setTrack(ParticleTrack track) {
        this.track = track;
    }
    
    public DetectorHit getHit() {
        return hit;
    }
    
    public void setHit(DetectorHit hit) {
        this.hit = hit;
    }
    
    public Double getAssociationWeight() {
        return associationWeight;
    }
    
    public void setAssociationWeight(Double associationWeight) {
        this.associationWeight = associationWeight;
    }
    
    public Double getResidual() {
        return residual;
    }
    
    public void setResidual(Double residual) {
        this.residual = residual;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrackHitAssociation)) return false;
        TrackHitAssociation that = (TrackHitAssociation) o;
        return associationId != null && associationId.equals(that.associationId);
    }
    
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
    
    @Override
    public String toString() {
        return "TrackHitAssociation{" +
                "associationId=" + associationId +
                ", associationWeight=" + associationWeight +
                ", residual=" + residual +
                '}';
    }
}