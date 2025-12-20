package com.alyx.jobscheduler;

/**
 * Simple test runner to validate project setup without requiring Maven
 */
public class SimpleTestRunner {
    
    public static void main(String[] args) {
        System.out.println("Running project setup validation...");
        
        try {
            // Test 1: Validate that we can create job parameters
            ProjectSetupValidationTest.JobParameters params = 
                new ProjectSetupValidationTest.JobParameters(
                    "test-job", "Test description", 100, 5.0, false);
            
            System.out.println("✓ Job parameters creation successful");
            
            // Test 2: Validate basic job submission logic
            ProjectSetupValidationTest test = new ProjectSetupValidationTest();
            ProjectSetupValidationTest.JobSubmissionResult result = 
                test.validateJobSubmission(params);
            
            if (result.isValid() && result.getJobId() != null && result.getEstimatedCompletion() != null) {
                System.out.println("✓ Job submission validation successful");
                System.out.println("  Job ID: " + result.getJobId());
                System.out.println("  Estimated completion: " + result.getEstimatedCompletion());
            } else {
                System.out.println("✗ Job submission validation failed: " + result.getErrorMessage());
                System.exit(1);
            }
            
            // Test 3: Validate invalid parameters are rejected
            ProjectSetupValidationTest.JobParameters invalidParams = 
                new ProjectSetupValidationTest.JobParameters(
                    "", "Test description", -1, -5.0, false);
            
            ProjectSetupValidationTest.JobSubmissionResult invalidResult = 
                test.validateJobSubmission(invalidParams);
            
            if (!invalidResult.isValid()) {
                System.out.println("✓ Invalid job parameters correctly rejected");
            } else {
                System.out.println("✗ Invalid job parameters should have been rejected");
                System.exit(1);
            }
            
            System.out.println("\n🎉 All project setup validation tests passed!");
            System.out.println("Property 1 (Job submission and validation) is working correctly.");
            
        } catch (Exception e) {
            System.out.println("✗ Test failed with exception: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}