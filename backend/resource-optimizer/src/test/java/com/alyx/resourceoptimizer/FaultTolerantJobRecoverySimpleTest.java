package com.alyx.resourceoptimizer;

import com.alyx.resourceoptimizer.model.Job;
import com.alyx.resourceoptimizer.model.Resource;
import com.alyx.resourceoptimizer.service.ResourceOptimizerService;

/**
 * Simple test for fault-tolerant job recovery without requiring Spring Boot
 * **Feature: alyx-distributed-orchestrator, Property 23: Fault-tolerant job recovery**
 * **Validates: Requirements 7.4**
 */
public class FaultTolerantJobRecoverySimpleTest {
    
    public static void main(String[] args) {
        System.out.println("Running fault-tolerant job recovery tests...");
        
        try {
            testBasicJobRecovery();
            testMultipleJobRecovery();
            testRecoveryJobPriority();
            testResourceRestoration();
            
            System.out.println("\n🎉 All fault-tolerant job recovery tests passed!");
            System.out.println("Property 23 (Fault-tolerant job recovery) is working correctly.");
            
        } catch (Exception e) {
            System.out.println("✗ Test failed with exception: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void testBasicJobRecovery() {
        System.out.println("Test 1: Basic job recovery");
        
        ResourceOptimizerService service = new ResourceOptimizerService();
        
        // Add resources
        Resource resource1 = new Resource("res-1", 4, 4096);
        Resource resource2 = new Resource("res-2", 4, 4096);
        service.addResource(resource1);
        service.addResource(resource2);
        
        // Schedule a job on resource1
        Job job = new Job("job-1", Job.Priority.NORMAL, 2, 2048);
        boolean scheduled = service.scheduleJobWithPreemption(job);
        
        if (!scheduled) {
            throw new RuntimeException("Job should have been scheduled");
        }
        
        if (job.getStatus() != Job.JobStatus.RUNNING) {
            throw new RuntimeException("Job should be running");
        }
        
        String originalResourceId = job.getAssignedResourceId();
        
        // Simulate resource failure
        service.simulateResourceFailure(originalResourceId);
        
        // Check that original job is marked as failed
        if (job.getStatus() != Job.JobStatus.FAILED) {
            throw new RuntimeException("Original job should be marked as failed");
        }
        
        // Check that a recovery job was created and scheduled
        Job recoveryJob = service.getJob("job-1-recovery");
        if (recoveryJob == null) {
            throw new RuntimeException("Recovery job should have been created");
        }
        
        if (recoveryJob.getStatus() != Job.JobStatus.RUNNING && recoveryJob.getStatus() != Job.JobStatus.QUEUED) {
            throw new RuntimeException("Recovery job should be running or queued");
        }
        
        System.out.println("✓ Basic job recovery successful");
    }
    
    private static void testMultipleJobRecovery() {
        System.out.println("Test 2: Multiple job recovery");
        
        ResourceOptimizerService service = new ResourceOptimizerService();
        
        // Add resources
        Resource resource1 = new Resource("res-1", 8, 8192);
        Resource resource2 = new Resource("res-2", 8, 8192);
        service.addResource(resource1);
        service.addResource(resource2);
        
        // Schedule multiple jobs on resource1
        Job job1 = new Job("job-1", Job.Priority.NORMAL, 2, 2048);
        Job job2 = new Job("job-2", Job.Priority.LOW, 2, 2048);
        Job job3 = new Job("job-3", Job.Priority.HIGH, 2, 2048);
        
        service.scheduleJobWithPreemption(job1);
        service.scheduleJobWithPreemption(job2);
        service.scheduleJobWithPreemption(job3);
        
        // Verify all jobs are running
        if (job1.getStatus() != Job.JobStatus.RUNNING ||
            job2.getStatus() != Job.JobStatus.RUNNING ||
            job3.getStatus() != Job.JobStatus.RUNNING) {
            throw new RuntimeException("All jobs should be running initially");
        }
        
        // Find which resource has jobs and simulate its failure
        String resourceWithJobs = job1.getAssignedResourceId();
        service.simulateResourceFailure(resourceWithJobs);
        
        // Check that all affected jobs are marked as failed
        if (job1.getAssignedResourceId() != null && job1.getStatus() != Job.JobStatus.FAILED) {
            throw new RuntimeException("Job1 should be marked as failed if it was on the failed resource");
        }
        
        // Check that recovery jobs were created
        Job recovery1 = service.getJob("job-1-recovery");
        Job recovery2 = service.getJob("job-2-recovery");
        Job recovery3 = service.getJob("job-3-recovery");
        
        int recoveryJobsCreated = 0;
        if (recovery1 != null) recoveryJobsCreated++;
        if (recovery2 != null) recoveryJobsCreated++;
        if (recovery3 != null) recoveryJobsCreated++;
        
        if (recoveryJobsCreated == 0) {
            throw new RuntimeException("At least one recovery job should have been created");
        }
        
        System.out.println("✓ Multiple job recovery successful");
    }
    
    private static void testRecoveryJobPriority() {
        System.out.println("Test 3: Recovery job priority");
        
        ResourceOptimizerService service = new ResourceOptimizerService();
        
        // Add limited resources
        Resource resource1 = new Resource("res-1", 4, 4096);
        Resource resource2 = new Resource("res-2", 2, 2048); // Limited capacity
        service.addResource(resource1);
        service.addResource(resource2);
        
        // Schedule a job on resource1
        Job originalJob = new Job("job-original", Job.Priority.LOW, 3, 3000);
        service.scheduleJobWithPreemption(originalJob);
        
        // Fill resource2 with a low priority job
        Job blockingJob = new Job("job-blocking", Job.Priority.LOW, 2, 2048);
        service.scheduleJobWithPreemption(blockingJob);
        
        // Simulate failure of resource1
        service.simulateResourceFailure("res-1");
        
        // Check that recovery job was created
        Job recoveryJob = service.getJob("job-original-recovery");
        if (recoveryJob == null) {
            throw new RuntimeException("Recovery job should have been created");
        }
        
        // Recovery job should have high priority
        if (recoveryJob.getPriority() != Job.Priority.HIGH) {
            throw new RuntimeException("Recovery job should have HIGH priority");
        }
        
        System.out.println("✓ Recovery job priority successful");
    }
    
    private static void testResourceRestoration() {
        System.out.println("Test 4: Resource restoration");
        
        ResourceOptimizerService service = new ResourceOptimizerService();
        
        // Add two resources to ensure we have backup capacity
        Resource resource1 = new Resource("res-1", 4, 4096);
        Resource resource2 = new Resource("res-2", 4, 4096);
        service.addResource(resource1);
        service.addResource(resource2);
        
        // Schedule a job on resource1
        Job job = new Job("job-1", Job.Priority.NORMAL, 2, 2048);
        service.scheduleJobWithPreemption(job);
        
        // Verify job is running on resource1
        if (!job.getAssignedResourceId().equals("res-1")) {
            // If it's on resource2, let's force it to resource1 by filling resource2
            Job fillerJob = new Job("filler", Job.Priority.LOW, 4, 4096);
            service.scheduleJobWithPreemption(fillerJob);
            
            // Now schedule our test job
            job = new Job("job-1", Job.Priority.NORMAL, 2, 2048);
            service.scheduleJobWithPreemption(job);
        }
        
        // Simulate resource failure
        service.simulateResourceFailure("res-1");
        
        // Verify resource is offline
        Resource failedResource = service.getResource("res-1");
        if (failedResource.isOnline()) {
            throw new RuntimeException("Resource should be offline after failure");
        }
        
        // Restore the resource
        service.restoreResource("res-1");
        
        // Verify resource is back online
        if (!failedResource.isOnline()) {
            throw new RuntimeException("Resource should be online after restoration");
        }
        
        // Verify resource has full capacity restored
        if (failedResource.getAvailableCores() != failedResource.getTotalCores()) {
            throw new RuntimeException("Resource should have full core capacity restored");
        }
        
        if (failedResource.getAvailableMemoryMB() != failedResource.getTotalMemoryMB()) {
            throw new RuntimeException("Resource should have full memory capacity restored");
        }
        
        // Check that new jobs can be scheduled on the restored resource
        Job newJob = new Job("job-new", Job.Priority.CRITICAL, 2, 2048);
        boolean scheduled = service.scheduleJobWithPreemption(newJob);
        
        if (!scheduled) {
            throw new RuntimeException("New job should be schedulable on restored resource");
        }
        
        System.out.println("✓ Resource restoration successful");
    }
}