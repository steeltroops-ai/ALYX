package com.alyx.jobscheduler;

import java.util.Random;

/**
 * **Feature: alyx-distributed-orchestrator, Property 2: Invalid job rejection**
 * Simplified property-based test to validate that invalid job parameters are properly rejected.
 * This test validates Requirements 1.3 by ensuring that invalid job parameters result in 
 * rejection with specific error messages without queuing the job.
 */
public class InvalidJobRejectionSimpleTest {

    private static final Random random = new Random();

    /**
     * Test Property 2: Invalid job rejection
     */
    public static void testInvalidJobRejection() {
        System.out.println("Testing Property 2: Invalid job rejection");
        
        // Test invalid job names (100 iterations)
        for (int i = 0; i < 100; i++) {
            ProjectSetupValidationSimple.JobParameters invalidParams = generateInvalidJobNameParameters();
            ProjectSetupValidationSimple.JobSubmissionResult result = 
                ProjectSetupValidationSimple.validateJobSubmission(invalidParams);
            
            if (result.isValid()) {
                throw new AssertionError("Invalid job name should be rejected (iteration " + i + ")");
            }
            
            if (result.getJobId() != null) {
                throw new AssertionError("Job ID should be null for invalid parameters (iteration " + i + ")");
            }
            
            if (result.getEstimatedCompletion() != null) {
                throw new AssertionError("Estimated completion should be null for invalid parameters (iteration " + i + ")");
            }
            
            if (result.getErrorMessage() == null || !result.getErrorMessage().toLowerCase().contains("name")) {
                throw new AssertionError("Error message should mention job name issue (iteration " + i + ")");
            }
        }
        
        // Test invalid expected events (100 iterations)
        for (int i = 0; i < 100; i++) {
            ProjectSetupValidationSimple.JobParameters invalidParams = generateInvalidExpectedEventsParameters();
            ProjectSetupValidationSimple.JobSubmissionResult result = 
                ProjectSetupValidationSimple.validateJobSubmission(invalidParams);
            
            if (result.isValid()) {
                throw new AssertionError("Invalid expected events should be rejected (iteration " + i + ")");
            }
            
            if (result.getJobId() != null) {
                throw new AssertionError("Job ID should be null for invalid parameters (iteration " + i + ")");
            }
            
            if (result.getErrorMessage() == null || !result.getErrorMessage().toLowerCase().contains("positive")) {
                throw new AssertionError("Error message should mention positive requirement (iteration " + i + ")");
            }
        }
        
        // Test invalid energy threshold (100 iterations)
        for (int i = 0; i < 100; i++) {
            ProjectSetupValidationSimple.JobParameters invalidParams = generateInvalidEnergyThresholdParameters();
            ProjectSetupValidationSimple.JobSubmissionResult result = 
                ProjectSetupValidationSimple.validateJobSubmission(invalidParams);
            
            if (result.isValid()) {
                throw new AssertionError("Invalid energy threshold should be rejected (iteration " + i + ")");
            }
            
            if (result.getJobId() != null) {
                throw new AssertionError("Job ID should be null for invalid parameters (iteration " + i + ")");
            }
            
            if (result.getErrorMessage() == null || !result.getErrorMessage().toLowerCase().contains("positive")) {
                throw new AssertionError("Error message should mention positive requirement (iteration " + i + ")");
            }
        }
        
        System.out.println("✓ Property 2 passed all 300 iterations (100 each for job name, events, energy)");
    }

    /**
     * Generate job parameters with invalid job names
     */
    private static ProjectSetupValidationSimple.JobParameters generateInvalidJobNameParameters() {
        String invalidJobName = random.nextBoolean() ? "" : null; // Empty or null
        return new ProjectSetupValidationSimple.JobParameters(
            invalidJobName,
            "description-" + random.nextInt(1000),
            random.nextInt(1000) + 1, // Valid events
            random.nextDouble() * 100.0 + 0.1, // Valid energy
            random.nextBoolean()
        );
    }

    /**
     * Generate job parameters with invalid expected events
     */
    private static ProjectSetupValidationSimple.JobParameters generateInvalidExpectedEventsParameters() {
        int invalidEvents = random.nextInt(1001) - 1000; // -1000 to 0
        return new ProjectSetupValidationSimple.JobParameters(
            "job-" + random.nextInt(1000), // Valid name
            "description-" + random.nextInt(1000),
            invalidEvents,
            random.nextDouble() * 100.0 + 0.1, // Valid energy
            random.nextBoolean()
        );
    }

    /**
     * Generate job parameters with invalid energy threshold
     */
    private static ProjectSetupValidationSimple.JobParameters generateInvalidEnergyThresholdParameters() {
        double invalidThreshold = random.nextDouble() * -100.0; // Negative value
        return new ProjectSetupValidationSimple.JobParameters(
            "job-" + random.nextInt(1000), // Valid name
            "description-" + random.nextInt(1000),
            random.nextInt(1000) + 1, // Valid events
            invalidThreshold,
            random.nextBoolean()
        );
    }

    /**
     * Main method to run the test
     */
    public static void main(String[] args) {
        System.out.println("Running Property 2: Invalid Job Rejection Test");
        System.out.println("==============================================");
        
        try {
            testInvalidJobRejection();
            
            System.out.println("\n🎉 Property 2 test passed!");
            System.out.println("Invalid job rejection is working correctly.");
            System.out.println("Requirements 1.3 is satisfied.");
            
        } catch (Exception e) {
            System.out.println("✗ Test failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}