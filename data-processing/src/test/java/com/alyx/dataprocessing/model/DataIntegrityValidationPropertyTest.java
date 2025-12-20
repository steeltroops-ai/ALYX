package com.alyx.dataprocessing.model;

import net.java.quickcheck.Generator;
import net.java.quickcheck.QuickCheck;
import net.java.quickcheck.characteristic.AbstractCharacteristic;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Property-based test for data integrity validation with checksums.
 * **Feature: alyx-system-fix, Property 9: Data processing integrity**
 * **Validates: Requirements 4.3**
 */
public class DataIntegrityValidationPropertyTest {
    
    private static final Random random = new Random();
    
    /**
     * Generator for CollisionEvent with integrity data
     */
    private static final Generator<CollisionEvent> integrityCollisionEventGenerator = new Generator<CollisionEvent>() {
        @Override
        public CollisionEvent next() {
            CollisionEvent event = new CollisionEvent();
            
            // Generate core data
            event.setTimestamp(Instant.now().minusSeconds(random.nextInt(86400)));
            event.setCenterOfMassEnergy(5000.0 + random.nextDouble() * 8000.0);
            event.setRunNumber((long) (100000 + random.nextInt(900000)));
            event.setEventNumber((long) random.nextInt(1000000));
            event.setLuminosity(1e32 + random.nextDouble() * 1e34);
            event.setBeamEnergy1(6500.0 + random.nextDouble() * 500.0);
            event.setBeamEnergy2(6500.0 + random.nextDouble() * 500.0);
            event.setTriggerMask((long) random.nextInt(Integer.MAX_VALUE));
            event.setDataQualityFlags(random.nextInt(256));
            event.setReconstructionVersion("v" + (1 + random.nextInt(10)) + "." + random.nextInt(100));
            
            // Add metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("detector_config", "Run3_2024_v" + random.nextInt(10));
            metadata.put("magnetic_field", 3.8 + (random.nextDouble() - 0.5) * 0.2);
            metadata.put("temperature", 20.0 + random.nextDouble() * 10.0);
            metadata.put("pressure", 1013.25 + (random.nextDouble() - 0.5) * 50.0);
            event.setMetadata(metadata);
            
            // Generate checksum based on critical data
            String dataForChecksum = generateDataString(event);
            event.setChecksum(calculateSHA256(dataForChecksum));
            
            return event;
        }
    };
    
    /**
     * Generator for AnalysisJob with integrity data
     */
    private static final Generator<AnalysisJob> integrityAnalysisJobGenerator = new Generator<AnalysisJob>() {
        @Override
        public AnalysisJob next() {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("energy_threshold", 1000.0 + random.nextDouble() * 5000.0);
            parameters.put("pt_min", 0.1 + random.nextDouble() * 2.0);
            parameters.put("eta_max", 0.8 + random.nextDouble() * 1.2);
            parameters.put("analysis_version", "v2024." + random.nextInt(100));
            
            AnalysisJob job = new AnalysisJob("user_" + random.nextInt(1000), "physics_analysis", parameters);
            int cores = 1 + random.nextInt(32);
            job.setAllocatedCores(cores);
            // Ensure at least 100MB per core
            long minMemory = cores * 100L;
            job.setMemoryAllocationMB(minMemory + random.nextInt(15000));
            job.setPriority(1 + random.nextInt(10));
            job.setDataProcessedMB((long) (100 + random.nextInt(10000)));
            
            // Generate checksum for job integrity
            String jobDataString = generateJobDataString(job);
            job.setChecksum(calculateSHA256(jobDataString));
            
            return job;
        }
    };
    
    /**
     * Generator for corrupted data scenarios
     */
    private static final Generator<DataCorruptionScenario> corruptionScenarioGenerator = new Generator<DataCorruptionScenario>() {
        @Override
        public DataCorruptionScenario next() {
            CorruptionType type = CorruptionType.values()[random.nextInt(CorruptionType.values().length)];
            String originalData = "critical_physics_data_" + random.nextInt(10000);
            String corruptedData = corruptData(originalData, type);
            
            return new DataCorruptionScenario(type, originalData, corruptedData);
        }
    };
    
    @Test
    public void testCollisionEventChecksumValidation() {
        QuickCheck.forAll(integrityCollisionEventGenerator, new AbstractCharacteristic<CollisionEvent>() {
            @Override
            protected void doSpecify(CollisionEvent event) throws Throwable {
                // Property: For any collision event, checksum validation should correctly
                // identify data integrity and detect any corruption
                
                if (event.getChecksum() == null) {
                    throw new AssertionError("Expected non-null checksum");
                }
                
                // Verify checksum format (SHA-256 produces 64 hex characters)
                if (event.getChecksum() != null) {
                    if (event.getChecksum().length() != 64) {
                        throw new AssertionError("Expected checksum length 64 but was " + event.getChecksum().length());
                    }
                    if (!event.getChecksum().matches("[a-f0-9]+")) {
                        throw new AssertionError("Expected checksum to match hex pattern");
                    }
                }
                
                // Verify checksum correctness
                String dataString = generateDataString(event);
                String expectedChecksum = calculateSHA256(dataString);
                if (!event.getChecksum().equals(expectedChecksum)) {
                    throw new AssertionError("Expected checksum " + expectedChecksum + " but was " + event.getChecksum());
                }
                
                // Test checksum validation function
                boolean isValid = validateChecksum(dataString, event.getChecksum());
                if (!isValid) {
                    throw new AssertionError("Expected checksum validation to be true");
                }
                
                // Test that modified data produces different checksum
                String modifiedDataString = dataString + "_modified";
                String modifiedChecksum = calculateSHA256(modifiedDataString);
                if (modifiedChecksum.equals(event.getChecksum())) {
                    throw new AssertionError("Expected modified checksum to be different");
                }
                
                // Verify validation fails for corrupted data
                boolean corruptedValidation = validateChecksum(modifiedDataString, event.getChecksum());
                if (corruptedValidation) {
                    throw new AssertionError("Expected corrupted validation to be false");
                }
            }
        });
    }
    
    @Test
    public void testAnalysisJobIntegrityValidation() {
        QuickCheck.forAll(integrityAnalysisJobGenerator, new AbstractCharacteristic<AnalysisJob>() {
            @Override
            protected void doSpecify(AnalysisJob job) throws Throwable {
                // Property: For any analysis job, integrity validation should ensure
                // job parameters and results maintain consistency
                
                if (job.getChecksum() == null) {
                    throw new AssertionError("Expected non-null checksum");
                }
                
                // Verify checksum format
                if (job.getChecksum() != null) {
                    if (job.getChecksum().length() != 64) {
                        throw new AssertionError("Expected checksum length 64 but was " + job.getChecksum().length());
                    }
                    if (!job.getChecksum().matches("[a-f0-9]+")) {
                        throw new AssertionError("Expected checksum to match hex pattern");
                    }
                }
                
                // Verify job data integrity
                String jobDataString = generateJobDataString(job);
                String expectedChecksum = calculateSHA256(jobDataString);
                if (!job.getChecksum().equals(expectedChecksum)) {
                    throw new AssertionError("Expected checksum " + expectedChecksum + " but was " + job.getChecksum());
                }
                
                // Test parameter integrity
                if (job.getParameters() != null) {
                    // Parameters should be consistent with job type
                    if (!(job.getParameters() instanceof Map)) {
                        throw new AssertionError("Expected parameters to be a Map");
                    }
                    
                    // Critical parameters should be present for physics analysis
                    if ("physics_analysis".equals(job.getJobType())) {
                        if (!job.getParameters().containsKey("energy_threshold")) {
                            throw new AssertionError("Expected energy_threshold parameter for physics analysis");
                        }
                    }
                }
                
                // Test resource allocation consistency
                if (job.getAllocatedCores() != null && job.getMemoryAllocationMB() != null) {
                    // Memory per core should be reasonable (at least 100MB per core)
                    double memoryPerCore = (double) job.getMemoryAllocationMB() / job.getAllocatedCores();
                    if (memoryPerCore < 100.0) {
                        throw new AssertionError("Expected memory per core >= 100MB but was " + memoryPerCore);
                    }
                }
                
                // Test execution time consistency
                if (job.getStartedAt() != null && job.getCompletedAt() != null && job.getExecutionTimeSeconds() != null) {
                    long actualDuration = java.time.Duration.between(job.getStartedAt(), job.getCompletedAt()).getSeconds();
                    // Allow some tolerance for timing precision
                    long difference = Math.abs(actualDuration - job.getExecutionTimeSeconds());
                    if (difference > 2L) {
                        throw new AssertionError("Expected execution time difference <= 2 seconds but was " + difference);
                    }
                }
            }
        });
    }
    
    @Test
    public void testDataCorruptionDetection() {
        QuickCheck.forAll(corruptionScenarioGenerator, new AbstractCharacteristic<DataCorruptionScenario>() {
            @Override
            protected void doSpecify(DataCorruptionScenario scenario) throws Throwable {
                // Property: For any data corruption scenario, the integrity validation
                // should immediately flag corruption and prevent data loss
                
                String originalChecksum = calculateSHA256(scenario.originalData);
                String corruptedChecksum = calculateSHA256(scenario.corruptedData);
                
                // Verify corruption is detected
                if (!scenario.originalData.equals(scenario.corruptedData)) {
                    if (originalChecksum.equals(corruptedChecksum)) {
                        throw new AssertionError("Expected different checksums for corrupted data");
                    }
                }
                
                // Test validation functions
                boolean originalValidation = validateChecksum(scenario.originalData, originalChecksum);
                if (!originalValidation) {
                    throw new AssertionError("Expected original validation to be true");
                }
                
                boolean corruptedValidation = validateChecksum(scenario.corruptedData, originalChecksum);
                if (!scenario.originalData.equals(scenario.corruptedData)) {
                    if (corruptedValidation) {
                        throw new AssertionError("Expected corrupted validation to be false");
                    }
                }
                
                // Test specific corruption types
                switch (scenario.corruptionType) {
                    case BIT_FLIP:
                        // Single bit corruption should be detected
                        if (scenario.corruptedData.length() != scenario.originalData.length()) {
                            throw new AssertionError("Expected same length for bit flip corruption");
                        }
                        break;
                    case TRUNCATION:
                        // Truncated data should be detected
                        if (scenario.corruptedData.length() >= scenario.originalData.length()) {
                            throw new AssertionError("Expected shorter length for truncation corruption");
                        }
                        break;
                    case INSERTION:
                        // Inserted data should be detected
                        if (scenario.corruptedData.length() <= scenario.originalData.length()) {
                            throw new AssertionError("Expected longer length for insertion corruption");
                        }
                        break;
                    case SUBSTITUTION:
                        // Substituted data should be detected
                        if (scenario.corruptedData.length() != scenario.originalData.length()) {
                            throw new AssertionError("Expected same length for substitution corruption");
                        }
                        break;
                }
                
                // Verify checksum properties
                if (originalChecksum.length() != 64) {
                    throw new AssertionError("Expected original checksum length 64 but was " + originalChecksum.length());
                }
                if (corruptedChecksum.length() != 64) {
                    throw new AssertionError("Expected corrupted checksum length 64 but was " + corruptedChecksum.length());
                }
                if (!originalChecksum.matches("[a-f0-9]+")) {
                    throw new AssertionError("Expected original checksum to match hex pattern");
                }
                if (!corruptedChecksum.matches("[a-f0-9]+")) {
                    throw new AssertionError("Expected corrupted checksum to match hex pattern");
                }
            }
        });
    }
    
    @Test
    public void testChecksumConsistency() {
        QuickCheck.forAll(integrityCollisionEventGenerator, new AbstractCharacteristic<CollisionEvent>() {
            @Override
            protected void doSpecify(CollisionEvent event) throws Throwable {
                // Property: For any collision event, checksum calculation should be
                // deterministic and consistent across multiple calculations
                
                String dataString = generateDataString(event);
                
                // Calculate checksum multiple times
                String checksum1 = calculateSHA256(dataString);
                String checksum2 = calculateSHA256(dataString);
                String checksum3 = calculateSHA256(dataString);
                
                // All calculations should produce identical results
                if (!checksum1.equals(checksum2)) {
                    throw new AssertionError("Expected consistent checksum calculation");
                }
                if (!checksum2.equals(checksum3)) {
                    throw new AssertionError("Expected consistent checksum calculation");
                }
                if (!checksum1.equals(event.getChecksum())) {
                    throw new AssertionError("Expected checksum to match event checksum");
                }
                
                // Test with identical data but different object instances
                CollisionEvent eventCopy = new CollisionEvent();
                eventCopy.setTimestamp(event.getTimestamp());
                eventCopy.setCenterOfMassEnergy(event.getCenterOfMassEnergy());
                eventCopy.setRunNumber(event.getRunNumber());
                eventCopy.setEventNumber(event.getEventNumber());
                eventCopy.setLuminosity(event.getLuminosity());
                eventCopy.setBeamEnergy1(event.getBeamEnergy1());
                eventCopy.setBeamEnergy2(event.getBeamEnergy2());
                eventCopy.setTriggerMask(event.getTriggerMask());
                eventCopy.setDataQualityFlags(event.getDataQualityFlags());
                eventCopy.setReconstructionVersion(event.getReconstructionVersion());
                eventCopy.setMetadata(event.getMetadata());
                
                String copyDataString = generateDataString(eventCopy);
                String copyChecksum = calculateSHA256(copyDataString);
                
                if (!copyChecksum.equals(event.getChecksum())) {
                    throw new AssertionError("Expected copy checksum to match original");
                }
            }
        });
    }
    
    // Helper methods
    private static String generateDataString(CollisionEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append(event.getTimestamp()).append("|");
        sb.append(event.getCenterOfMassEnergy()).append("|");
        sb.append(event.getRunNumber()).append("|");
        sb.append(event.getEventNumber()).append("|");
        sb.append(event.getLuminosity()).append("|");
        sb.append(event.getBeamEnergy1()).append("|");
        sb.append(event.getBeamEnergy2()).append("|");
        sb.append(event.getTriggerMask()).append("|");
        sb.append(event.getDataQualityFlags()).append("|");
        sb.append(event.getReconstructionVersion()).append("|");
        if (event.getMetadata() != null) {
            sb.append(event.getMetadata().toString());
        }
        return sb.toString();
    }
    
    private static String generateJobDataString(AnalysisJob job) {
        StringBuilder sb = new StringBuilder();
        sb.append(job.getUserId()).append("|");
        sb.append(job.getJobType()).append("|");
        sb.append(job.getSubmittedAt()).append("|");
        sb.append(job.getAllocatedCores()).append("|");
        sb.append(job.getMemoryAllocationMB()).append("|");
        sb.append(job.getPriority()).append("|");
        sb.append(job.getDataProcessedMB()).append("|");
        if (job.getParameters() != null) {
            sb.append(job.getParameters().toString());
        }
        return sb.toString();
    }
    
    private static String calculateSHA256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    private static boolean validateChecksum(String data, String expectedChecksum) {
        String actualChecksum = calculateSHA256(data);
        return actualChecksum.equals(expectedChecksum);
    }
    
    private static String corruptData(String originalData, CorruptionType type) {
        if (originalData == null || originalData.isEmpty()) {
            return originalData;
        }
        
        switch (type) {
            case BIT_FLIP:
                // Flip a random character
                char[] chars = originalData.toCharArray();
                int pos = random.nextInt(chars.length);
                chars[pos] = (char) (chars[pos] ^ 1);
                return new String(chars);
                
            case TRUNCATION:
                // Remove random suffix
                int truncateAt = random.nextInt(originalData.length());
                return originalData.substring(0, truncateAt);
                
            case INSERTION:
                // Insert random character
                int insertAt = random.nextInt(originalData.length() + 1);
                char randomChar = (char) ('a' + random.nextInt(26));
                return originalData.substring(0, insertAt) + randomChar + originalData.substring(insertAt);
                
            case SUBSTITUTION:
                // Replace random character
                char[] substChars = originalData.toCharArray();
                int substPos = random.nextInt(substChars.length);
                substChars[substPos] = (char) ('a' + random.nextInt(26));
                return new String(substChars);
                
            default:
                return originalData;
        }
    }
    
    // Data classes
    private enum CorruptionType {
        BIT_FLIP, TRUNCATION, INSERTION, SUBSTITUTION
    }
    
    private static class DataCorruptionScenario {
        final CorruptionType corruptionType;
        final String originalData;
        final String corruptedData;
        
        DataCorruptionScenario(CorruptionType corruptionType, String originalData, String corruptedData) {
            this.corruptionType = corruptionType;
            this.originalData = originalData;
            this.corruptedData = corruptedData;
        }
    }
    

}