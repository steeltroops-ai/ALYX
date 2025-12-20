package com.alyx.dataprocessing.model;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.Type;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a single high-energy particle collision event.
 * Contains detector hit data, metadata, and reconstructed particle tracks.
 * Optimized for time-series partitioning and spatial queries.
 */
@Entity
@Table(name = "collision_events", indexes = {
    @Index(name = "idx_collision_events_timestamp", columnList = "timestamp"),
    @Index(name = "idx_collision_events_energy_range", columnList = "center_of_mass_energy"),
    @Index(name = "idx_collision_events_run_number", columnList = "run_number"),
    @Index(name = "idx_collision_events_event_number", columnList = "event_number")
})
public class CollisionEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "event_id")
    private UUID eventId;
    
    @NotNull
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;
    
    @NotNull
    @Column(name = "center_of_mass_energy", nullable = false)
    private Double centerOfMassEnergy;
    
    @NotNull
    @Column(name = "run_number", nullable = false)
    private Long runNumber;
    
    @NotNull
    @Column(name = "event_number", nullable = false)
    private Long eventNumber;
    
    @Column(name = "collision_vertex", columnDefinition = "geometry(Point,4326)")
    private Point collisionVertex;
    
    @Column(name = "luminosity")
    private Double luminosity;
    
    @Column(name = "beam_energy_1")
    private Double beamEnergy1;
    
    @Column(name = "beam_energy_2")
    private Double beamEnergy2;
    
    @Column(name = "trigger_mask")
    private Long triggerMask;
    
    @Type(JsonType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(name = "data_quality_flags")
    private Integer dataQualityFlags;
    
    @Column(name = "reconstruction_version")
    private String reconstructionVersion;
    
    @Column(name = "checksum")
    private String checksum;
    
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DetectorHit> detectorHits = new ArrayList<>();
    
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ParticleTrack> reconstructedTracks = new ArrayList<>();
    
    // Constructors
    public CollisionEvent() {}
    
    public CollisionEvent(Instant timestamp, Double centerOfMassEnergy, Long runNumber, Long eventNumber) {
        this.timestamp = timestamp;
        this.centerOfMassEnergy = centerOfMassEnergy;
        this.runNumber = runNumber;
        this.eventNumber = eventNumber;
    }
    
    // Getters and Setters
    public UUID getEventId() {
        return eventId;
    }
    
    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    public Double getCenterOfMassEnergy() {
        return centerOfMassEnergy;
    }
    
    public void setCenterOfMassEnergy(Double centerOfMassEnergy) {
        this.centerOfMassEnergy = centerOfMassEnergy;
    }
    
    public Long getRunNumber() {
        return runNumber;
    }
    
    public void setRunNumber(Long runNumber) {
        this.runNumber = runNumber;
    }
    
    public Long getEventNumber() {
        return eventNumber;
    }
    
    public void setEventNumber(Long eventNumber) {
        this.eventNumber = eventNumber;
    }
    
    public Point getCollisionVertex() {
        return collisionVertex;
    }
    
    public void setCollisionVertex(Point collisionVertex) {
        this.collisionVertex = collisionVertex;
    }
    
    public Double getLuminosity() {
        return luminosity;
    }
    
    public void setLuminosity(Double luminosity) {
        this.luminosity = luminosity;
    }
    
    public Double getBeamEnergy1() {
        return beamEnergy1;
    }
    
    public void setBeamEnergy1(Double beamEnergy1) {
        this.beamEnergy1 = beamEnergy1;
    }
    
    public Double getBeamEnergy2() {
        return beamEnergy2;
    }
    
    public void setBeamEnergy2(Double beamEnergy2) {
        this.beamEnergy2 = beamEnergy2;
    }
    
    public Long getTriggerMask() {
        return triggerMask;
    }
    
    public void setTriggerMask(Long triggerMask) {
        this.triggerMask = triggerMask;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public Integer getDataQualityFlags() {
        return dataQualityFlags;
    }
    
    public void setDataQualityFlags(Integer dataQualityFlags) {
        this.dataQualityFlags = dataQualityFlags;
    }
    
    public String getReconstructionVersion() {
        return reconstructionVersion;
    }
    
    public void setReconstructionVersion(String reconstructionVersion) {
        this.reconstructionVersion = reconstructionVersion;
    }
    
    public String getChecksum() {
        return checksum;
    }
    
    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }
    
    public List<DetectorHit> getDetectorHits() {
        return detectorHits;
    }
    
    public void setDetectorHits(List<DetectorHit> detectorHits) {
        this.detectorHits = detectorHits;
    }
    
    public void addDetectorHit(DetectorHit hit) {
        detectorHits.add(hit);
        hit.setEvent(this);
    }
    
    public void removeDetectorHit(DetectorHit hit) {
        detectorHits.remove(hit);
        hit.setEvent(null);
    }
    
    public List<ParticleTrack> getReconstructedTracks() {
        return reconstructedTracks;
    }
    
    public void setReconstructedTracks(List<ParticleTrack> reconstructedTracks) {
        this.reconstructedTracks = reconstructedTracks;
    }
    
    public void addReconstructedTrack(ParticleTrack track) {
        reconstructedTracks.add(track);
        track.setEvent(this);
    }
    
    public void removeReconstructedTrack(ParticleTrack track) {
        reconstructedTracks.remove(track);
        track.setEvent(null);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CollisionEvent)) return false;
        CollisionEvent that = (CollisionEvent) o;
        return eventId != null && eventId.equals(that.eventId);
    }
    
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
    
    @Override
    public String toString() {
        return "CollisionEvent{" +
                "eventId=" + eventId +
                ", timestamp=" + timestamp +
                ", centerOfMassEnergy=" + centerOfMassEnergy +
                ", runNumber=" + runNumber +
                ", eventNumber=" + eventNumber +
                '}';
    }
}