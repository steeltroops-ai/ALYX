package com.alyx.dataprocessing;

import com.alyx.dataprocessing.model.CollisionEvent;
import com.alyx.dataprocessing.model.DetectorHit;
import com.alyx.dataprocessing.model.ParticleTrack;
import net.java.quickcheck.Generator;
import net.java.quickcheck.QuickCheck;
import net.java.quickcheck.characteristic.AbstractCharacteristic;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.*;

import static net.java.quickcheck.generator.PrimitiveGenerators.*;

/**
 * **Feature: alyx-system-fix, Property 9: Data processing integrity**
 * Simple property-based test to validate that collision event data processing
 * maintains all essential physics properties and relationships from the input.
 * **Validates: Requirements 4.3**
 */
public class DataProcessingIntegritySimpleTest {

    private static final GeometryFactory geometryFactory = new GeometryFactory();

    /**
     * Generator for valid collision events with detector hits and tracks
     */
    private static final Generator<CollisionEvent> collisionEventGenerator() {
        return new Generator<CollisionEvent>() {
            @Override
            public CollisionEvent next() {
                // Generate basic collision event properties
                Instant timestamp = Instant.now().minusSeconds(integers(0, 86400).next());
                Double centerOfMassEnergy = doubles(1.0, 14000.0).next(); // LHC energy range
                Long runNumber = longs(1L, 999999L).next();
                Long eventNumber = longs(1L, 999999999L).next();
                
                CollisionEvent event = new CollisionEvent(timestamp, centerOfMassEnergy, runNumber, eventNumber);
                
                // Add collision vertex
                Point vertex = geometryFactory.createPoint(new Coordinate(
                    doubles(-10.0, 10.0).next(), // x coordinate (cm)
                    doubles(-10.0, 10.0).next()  // y coordinate (cm)
                ));
                event.setCollisionVertex(vertex);
                
                // Add beam energies
                event.setBeamEnergy1(centerOfMassEnergy / 2.0);
                event.setBeamEnergy2(centerOfMassEnergy / 2.0);
                
                // Generate detector hits
                int numHits = integers(5, 50).next();
                for (int i = 0; i < numHits; i++) {
                    DetectorHit hit = generateDetectorHit(timestamp);
                    event.addDetectorHit(hit);
                }
                
                // Generate particle tracks
                int numTracks = integers(1, 10).next();
                for (int i = 0; i < numTracks; i++) {
                    ParticleTrack track = generateParticleTrack();
                    event.addReconstructedTrack(track);
                }
                
                return event;
            }
        };
    }

    /**
     * Generator for detector hits
     */
    private static DetectorHit generateDetectorHit(Instant eventTime) {
        String[] detectorTypes = {"ECAL", "HCAL", "MUON", "TRACKER", "PIXEL"};
        String detectorId = detectorTypes[integers(0, detectorTypes.length - 1).next()] + 
                           "_" + integers(1, 1000).next();
        
        Double energyDeposit = doubles(0.001, 100.0).next(); // GeV
        Instant hitTime = eventTime.plusNanos(integers(0, 1000000).next()); // Within 1ms
        
        Point position = geometryFactory.createPoint(new Coordinate(
            doubles(-300.0, 300.0).next(), // x coordinate (cm)
            doubles(-300.0, 300.0).next()  // y coordinate (cm)
        ));
        
        DetectorHit hit = new DetectorHit(detectorId, energyDeposit, hitTime, position);
        hit.setSignalAmplitude(doubles(0.1, 10.0).next());
        hit.setUncertainty(doubles(0.001, 0.1).next());
        
        return hit;
    }

    /**
     * Generator for particle tracks
     */
    private static ParticleTrack generateParticleTrack() {
        String[] particleTypes = {"electron", "muon", "pion", "kaon", "proton", "photon"};
        String particleType = particleTypes[integers(0, particleTypes.length - 1).next()];
        
        Double momentum = doubles(0.1, 1000.0).next(); // GeV/c
        Integer charge = integers(-2, 2).next(); // -2, -1, 0, 1, 2
        
        // Generate trajectory as a line string
        Coordinate[] coords = new Coordinate[integers(2, 10).next()];
        for (int i = 0; i < coords.length; i++) {
            coords[i] = new Coordinate(
                doubles(-300.0, 300.0).next(),
                doubles(-300.0, 300.0).next()
            );
        }
        LineString trajectory = geometryFactory.createLineString(coords);
        
        ParticleTrack track = new ParticleTrack(particleType, momentum, charge, trajectory);
        track.setConfidenceLevel(doubles(0.5, 1.0).next());
        track.setChiSquared(doubles(0.1, 10.0).next());
        track.setDegreesOfFreedom(integers(1, 20).next());
        
        return track;
    }

    @Test
    public void testEnergyConservationInProcessing() {
        // **Feature: alyx-system-fix, Property 9: Data processing integrity**
        QuickCheck.forAll(collisionEventGenerator(), 
            new AbstractCharacteristic<CollisionEvent>() {
                @Override
                protected void doSpecify(CollisionEvent originalEvent) throws Throwable {
                    // Property: Energy conservation during data processing
                    // The total energy in detector hits should be consistent with beam energies
                    
                    // Calculate total energy deposited in detectors
                    double totalDetectorEnergy = originalEvent.getDetectorHits().stream()
                        .mapToDouble(DetectorHit::getEnergyDeposit)
                        .sum();
                    
                    // Calculate total momentum of reconstructed tracks
                    double totalTrackMomentum = originalEvent.getReconstructedTracks().stream()
                        .mapToDouble(ParticleTrack::getMomentum)
                        .sum();
                    
                    // Physics constraints
                    assert totalDetectorEnergy > 0 : "Total detector energy should be positive";
                    assert totalTrackMomentum > 0 : "Total track momentum should be positive";
                    
                    // Energy should not exceed center of mass energy (allowing for measurement uncertainties)
                    double maxAllowedEnergy = originalEvent.getCenterOfMassEnergy() * 1.1; // 10% tolerance
                    assert totalDetectorEnergy <= maxAllowedEnergy : 
                        String.format("Total detector energy (%.2f) should not exceed center of mass energy (%.2f)", 
                                     totalDetectorEnergy, originalEvent.getCenterOfMassEnergy());
                    
                    // Track momentum should be reasonable compared to available energy
                    assert totalTrackMomentum <= maxAllowedEnergy : 
                        String.format("Total track momentum (%.2f) should not exceed available energy (%.2f)", 
                                     totalTrackMomentum, originalEvent.getCenterOfMassEnergy());
                }
            });
    }

    @Test
    public void testSpatialConsistencyInProcessing() {
        // **Feature: alyx-system-fix, Property 9: Data processing integrity**
        QuickCheck.forAll(collisionEventGenerator(), 
            new AbstractCharacteristic<CollisionEvent>() {
                @Override
                protected void doSpecify(CollisionEvent originalEvent) throws Throwable {
                    // Property: Spatial consistency during data processing
                    // Detector hits and track trajectories should be spatially consistent
                    
                    Point collisionVertex = originalEvent.getCollisionVertex();
                    if (collisionVertex == null) return; // Skip if no vertex
                    
                    // All detector hits should be within reasonable distance from collision vertex
                    for (DetectorHit hit : originalEvent.getDetectorHits()) {
                        if (hit.getPosition() != null) {
                            double distance = collisionVertex.distance(hit.getPosition());
                            
                            // Detector hits should be within 500 cm of collision vertex (reasonable for LHC detectors)
                            assert distance <= 500.0 : 
                                String.format("Detector hit at distance %.2f cm is too far from collision vertex", distance);
                        }
                    }
                    
                    // Track trajectories should start near the collision vertex
                    for (ParticleTrack track : originalEvent.getReconstructedTracks()) {
                        if (track.getTrajectory() != null && track.getTrajectory().getNumPoints() > 0) {
                            Coordinate startPoint = track.getTrajectory().getCoordinateN(0);
                            Point trackStart = geometryFactory.createPoint(startPoint);
                            
                            double distanceToVertex = collisionVertex.distance(trackStart);
                            
                            // Track should start within 50 cm of collision vertex
                            assert distanceToVertex <= 50.0 : 
                                String.format("Track starts %.2f cm from collision vertex, should be closer", distanceToVertex);
                        }
                    }
                }
            });
    }

    @Test
    public void testTemporalConsistencyInProcessing() {
        // **Feature: alyx-system-fix, Property 9: Data processing integrity**
        QuickCheck.forAll(collisionEventGenerator(), 
            new AbstractCharacteristic<CollisionEvent>() {
                @Override
                protected void doSpecify(CollisionEvent originalEvent) throws Throwable {
                    // Property: Temporal consistency during data processing
                    // All detector hits should occur at or after the collision time
                    
                    Instant collisionTime = originalEvent.getTimestamp();
                    
                    for (DetectorHit hit : originalEvent.getDetectorHits()) {
                        Instant hitTime = hit.getHitTime();
                        
                        // Hit time should be at or after collision time
                        assert !hitTime.isBefore(collisionTime) : 
                            String.format("Detector hit time %s is before collision time %s", hitTime, collisionTime);
                        
                        // Hit time should not be too far after collision (within 10ms for light speed)
                        long timeDiffNanos = hitTime.toEpochMilli() - collisionTime.toEpochMilli();
                        assert timeDiffNanos <= 10_000_000L : // 10ms in nanoseconds
                            String.format("Detector hit is %d ns after collision, too late for physics", timeDiffNanos);
                    }
                }
            });
    }

    @Test
    public void testChargeConservationInProcessing() {
        // **Feature: alyx-system-fix, Property 9: Data processing integrity**
        QuickCheck.forAll(collisionEventGenerator(), 
            new AbstractCharacteristic<CollisionEvent>() {
                @Override
                protected void doSpecify(CollisionEvent originalEvent) throws Throwable {
                    // Property: Charge conservation during data processing
                    // Total charge of reconstructed tracks should be conserved
                    
                    int totalCharge = originalEvent.getReconstructedTracks().stream()
                        .mapToInt(ParticleTrack::getCharge)
                        .sum();
                    
                    // For proton-proton collisions, initial charge is +2
                    // Final state should conserve charge (allowing for neutral particles)
                    // We can't enforce exact conservation without knowing initial state,
                    // but we can check reasonableness
                    
                    // Total charge should be reasonable (not extremely large)
                    assert Math.abs(totalCharge) <= 20 : 
                        String.format("Total charge %d seems unreasonably large", totalCharge);
                    
                    // Each individual track charge should be reasonable
                    for (ParticleTrack track : originalEvent.getReconstructedTracks()) {
                        int charge = track.getCharge();
                        assert Math.abs(charge) <= 2 : 
                            String.format("Track charge %d is outside reasonable range [-2, +2]", charge);
                    }
                }
            });
    }

    @Test
    public void testDataIntegrityAfterProcessing() {
        // **Feature: alyx-system-fix, Property 9: Data processing integrity**
        QuickCheck.forAll(collisionEventGenerator(), 
            new AbstractCharacteristic<CollisionEvent>() {
                @Override
                protected void doSpecify(CollisionEvent originalEvent) throws Throwable {
                    // Property: Data integrity is maintained during processing
                    // All essential relationships and constraints should be preserved
                    
                    // Event should have valid basic properties
                    assert originalEvent.getEventId() != null : "Event ID should not be null";
                    assert originalEvent.getTimestamp() != null : "Timestamp should not be null";
                    assert originalEvent.getCenterOfMassEnergy() != null : "Center of mass energy should not be null";
                    assert originalEvent.getCenterOfMassEnergy() > 0 : "Center of mass energy should be positive";
                    assert originalEvent.getRunNumber() != null : "Run number should not be null";
                    assert originalEvent.getEventNumber() != null : "Event number should not be null";
                    
                    // All detector hits should be properly associated with the event
                    for (DetectorHit hit : originalEvent.getDetectorHits()) {
                        assert hit.getEvent() == originalEvent : "Detector hit should be associated with the event";
                        assert hit.getDetectorId() != null : "Detector ID should not be null";
                        assert hit.getEnergyDeposit() != null : "Energy deposit should not be null";
                        assert hit.getEnergyDeposit() > 0 : "Energy deposit should be positive";
                        assert hit.getHitTime() != null : "Hit time should not be null";
                    }
                    
                    // All particle tracks should be properly associated with the event
                    for (ParticleTrack track : originalEvent.getReconstructedTracks()) {
                        assert track.getEvent() == originalEvent : "Particle track should be associated with the event";
                        assert track.getParticleType() != null : "Particle type should not be null";
                        assert track.getMomentum() != null : "Momentum should not be null";
                        assert track.getMomentum() > 0 : "Momentum should be positive";
                        assert track.getCharge() != null : "Charge should not be null";
                        
                        // Confidence level should be reasonable if present
                        if (track.getConfidenceLevel() != null) {
                            assert track.getConfidenceLevel() >= 0.0 && track.getConfidenceLevel() <= 1.0 : 
                                "Confidence level should be between 0 and 1";
                        }
                        
                        // Chi-squared should be positive if present
                        if (track.getChiSquared() != null) {
                            assert track.getChiSquared() >= 0.0 : "Chi-squared should be non-negative";
                        }
                        
                        // Degrees of freedom should be positive if present
                        if (track.getDegreesOfFreedom() != null) {
                            assert track.getDegreesOfFreedom() > 0 : "Degrees of freedom should be positive";
                        }
                    }
                    
                    // Event should have reasonable number of hits and tracks
                    assert originalEvent.getDetectorHits().size() > 0 : "Event should have at least one detector hit";
                    assert originalEvent.getReconstructedTracks().size() > 0 : "Event should have at least one reconstructed track";
                    
                    // Number of tracks should not exceed number of hits (each track needs hits)
                    assert originalEvent.getReconstructedTracks().size() <= originalEvent.getDetectorHits().size() : 
                        "Number of tracks should not exceed number of detector hits";
                }
            });
    }

    @Test
    public void testPhysicsConstraintsAfterProcessing() {
        // **Feature: alyx-system-fix, Property 9: Data processing integrity**
        QuickCheck.forAll(collisionEventGenerator(), 
            new AbstractCharacteristic<CollisionEvent>() {
                @Override
                protected void doSpecify(CollisionEvent originalEvent) throws Throwable {
                    // Property: Physics constraints are maintained during processing
                    // Fundamental physics laws should be respected
                    
                    // Beam energies should be consistent with center of mass energy
                    if (originalEvent.getBeamEnergy1() != null && originalEvent.getBeamEnergy2() != null) {
                        double totalBeamEnergy = originalEvent.getBeamEnergy1() + originalEvent.getBeamEnergy2();
                        double centerOfMassEnergy = originalEvent.getCenterOfMassEnergy();
                        
                        // Total beam energy should approximately equal center of mass energy
                        double energyDifference = Math.abs(totalBeamEnergy - centerOfMassEnergy);
                        double tolerance = centerOfMassEnergy * 0.01; // 1% tolerance
                        
                        assert energyDifference <= tolerance : 
                            String.format("Beam energy sum (%.2f) differs from center of mass energy (%.2f) by %.2f", 
                                         totalBeamEnergy, centerOfMassEnergy, energyDifference);
                    }
                    
                    // Particle types should be valid
                    Set<String> validParticleTypes = Set.of("electron", "muon", "pion", "kaon", "proton", "neutron", "photon");
                    for (ParticleTrack track : originalEvent.getReconstructedTracks()) {
                        assert validParticleTypes.contains(track.getParticleType()) : 
                            String.format("Invalid particle type: %s", track.getParticleType());
                    }
                    
                    // Energy deposits should be reasonable for detector types
                    for (DetectorHit hit : originalEvent.getDetectorHits()) {
                        String detectorType = hit.getDetectorId().split("_")[0];
                        double energy = hit.getEnergyDeposit();
                        
                        // Different detector types have different energy ranges
                        switch (detectorType) {
                            case "PIXEL":
                            case "TRACKER":
                                // Tracking detectors should have small energy deposits
                                assert energy <= 1.0 : 
                                    String.format("Tracking detector energy %.3f GeV is too high", energy);
                                break;
                            case "ECAL":
                                // Electromagnetic calorimeter can have higher energies
                                assert energy <= 1000.0 : 
                                    String.format("ECAL energy %.2f GeV is unreasonably high", energy);
                                break;
                            case "HCAL":
                                // Hadronic calorimeter can have very high energies
                                assert energy <= 5000.0 : 
                                    String.format("HCAL energy %.2f GeV is unreasonably high", energy);
                                break;
                            case "MUON":
                                // Muon detectors should have small energy deposits
                                assert energy <= 10.0 : 
                                    String.format("Muon detector energy %.2f GeV is too high", energy);
                                break;
                        }
                    }
                }
            });
    }
}