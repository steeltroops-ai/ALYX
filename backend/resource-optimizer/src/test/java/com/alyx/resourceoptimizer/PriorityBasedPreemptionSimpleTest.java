package com.alyx.resourceoptimizer;

import com.alyx.resourceoptimizer.model.Job;
import com.alyx.resourceoptimizer.model.Resource;
import com.alyx.resourceoptimizer.service.ResourceOptimizerService;

/**
 * Simple test for priority-based preemption without requiring Spring Boot
 * **Feature: alyx-distributed-orchestrator, Property 22: Priority-based preemption**
 * **Validates: Requirements 7.3**
 */
public class PriorityBasedPreemptionSimpleTest {
    
    public static void main(String[] args) {
        System.out.println("Running priority-based preemption tests...");
        
        try {
            testBasicPreemption();
            testNoPreemptionForCriticalJobs();
            testPreemptionWithMultipleJobs();
            testNoPreemptionWhenNotNecessary();
            
            System.out.println("\n🎉 All priority-based preemption tests passed!");
            System.out.println("Property 22 (Priority-based preemption) is working correctly.");
            
        } catch (Exception e) {
            System.out.println("✗ Test failed with exception: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void testBasicPreemption() {
        System.out.println("Test 1: Basic preemption");
        
        ResourceOptimizerService service = new ResourceOptimizerService();
        
        // Add a resource with limited capacity
        Resource resource = new Resource("res-1", 4, 4096);
        service.addResource(resource);
        
        // Schedule a low priority job that uses all resources
        Job lowPriorityJob = new Job("job-low", Job.Priority.LOW, 4, 4096);
        boolean scheduled1 = service.scheduleJobWithPreemption(lowPriorityJob);
        
        if (!scheduled1) {
            throw new RuntimeException("Low priority job should have been scheduled");
        }
        
        // Schedule a high priority job that should preempt the low priority job
        Job highPriorityJob = new Job("job-high", Job.Priority.HIGH, 2, 2048);
        boolean scheduled2 = service.scheduleJobWithPreemption(highPriorityJob);
        
        if (!scheduled2) {
            throw new RuntimeException("High priority job should have preempted low priority job");
        }
        
        // Verify states
        if (lowPriorityJob.getStatus() != Job.JobStatus.PREEMPTED) {
            throw new RuntimeException("Low priority job should have been preempted");
        }
        
        if (highPriorityJob.getStatus() != Job.JobStatus.RUNNING) {
            throw new RuntimeException("High priority job should be running");
        }
        
        System.out.println("✓ Basic preemption successful");
    }
    
    private static void testNoPreemptionForCriticalJobs() {
        System.out.println("Test 2: No preemption for critical jobs");
        
        ResourceOptimizerService service = new ResourceOptimizerService();
        
        // Add a resource
        Resource resource = new Resource("res-1", 4, 4096);
        service.addResource(resource);
        
        // Schedule a critical job
        Job criticalJob = new Job("job-critical", Job.Priority.CRITICAL, 4, 4096);
        criticalJob.setCanBePreempted(false);
        boolean scheduled1 = service.scheduleJobWithPreemption(criticalJob);
        
        if (!scheduled1) {
            throw new RuntimeException("Critical job should have been scheduled");
        }
        
        // Try to schedule another high priority job
        Job highPriorityJob = new Job("job-high", Job.Priority.HIGH, 2, 2048);
        boolean scheduled2 = service.scheduleJobWithPreemption(highPriorityJob);
        
        // High priority job should be queued, not preempt critical job
        if (scheduled2) {
            throw new RuntimeException("High priority job should not preempt critical job");
        }
        
        if (criticalJob.getStatus() != Job.JobStatus.RUNNING) {
            throw new RuntimeException("Critical job should still be running");
        }
        
        if (highPriorityJob.getStatus() != Job.JobStatus.QUEUED) {
            throw new RuntimeException("High priority job should be queued");
        }
        
        System.out.println("✓ No preemption for critical jobs successful");
    }
    
    private static void testPreemptionWithMultipleJobs() {
        System.out.println("Test 3: Preemption with multiple jobs");
        
        ResourceOptimizerService service = new ResourceOptimizerService();
        
        // Add a resource
        Resource resource = new Resource("res-1", 8, 8192);
        service.addResource(resource);
        
        // Schedule multiple low priority jobs
        Job lowJob1 = new Job("job-low-1", Job.Priority.LOW, 3, 3000);
        Job lowJob2 = new Job("job-low-2", Job.Priority.LOW, 3, 3000);
        Job normalJob = new Job("job-normal", Job.Priority.NORMAL, 2, 2000);
        
        service.scheduleJobWithPreemption(lowJob1);
        service.scheduleJobWithPreemption(lowJob2);
        service.scheduleJobWithPreemption(normalJob);
        
        // All should be running
        if (lowJob1.getStatus() != Job.JobStatus.RUNNING ||
            lowJob2.getStatus() != Job.JobStatus.RUNNING ||
            normalJob.getStatus() != Job.JobStatus.RUNNING) {
            throw new RuntimeException("All initial jobs should be running");
        }
        
        // Schedule a high priority job that needs more resources
        Job highPriorityJob = new Job("job-high", Job.Priority.HIGH, 4, 4000);
        boolean scheduled = service.scheduleJobWithPreemption(highPriorityJob);
        
        if (!scheduled) {
            throw new RuntimeException("High priority job should have been scheduled with preemption");
        }
        
        // Check that some lower priority jobs were preempted
        int preemptedCount = 0;
        if (lowJob1.getStatus() == Job.JobStatus.PREEMPTED) preemptedCount++;
        if (lowJob2.getStatus() == Job.JobStatus.PREEMPTED) preemptedCount++;
        if (normalJob.getStatus() == Job.JobStatus.PREEMPTED) preemptedCount++;
        
        if (preemptedCount == 0) {
            throw new RuntimeException("Some lower priority jobs should have been preempted");
        }
        
        if (highPriorityJob.getStatus() != Job.JobStatus.RUNNING) {
            throw new RuntimeException("High priority job should be running");
        }
        
        System.out.println("✓ Preemption with multiple jobs successful");
    }
    
    private static void testNoPreemptionWhenNotNecessary() {
        System.out.println("Test 4: No preemption when not necessary");
        
        ResourceOptimizerService service = new ResourceOptimizerService();
        
        // Add a resource with plenty of capacity
        Resource resource = new Resource("res-1", 8, 8192);
        service.addResource(resource);
        
        // Schedule a low priority job
        Job lowPriorityJob = new Job("job-low", Job.Priority.LOW, 2, 2048);
        service.scheduleJobWithPreemption(lowPriorityJob);
        
        // Schedule a high priority job that fits without preemption
        Job highPriorityJob = new Job("job-high", Job.Priority.HIGH, 2, 2048);
        boolean scheduled = service.scheduleJobWithPreemption(highPriorityJob);
        
        if (!scheduled) {
            throw new RuntimeException("High priority job should have been scheduled");
        }
        
        // Both jobs should be running (no preemption needed)
        if (lowPriorityJob.getStatus() != Job.JobStatus.RUNNING) {
            throw new RuntimeException("Low priority job should still be running");
        }
        
        if (highPriorityJob.getStatus() != Job.JobStatus.RUNNING) {
            throw new RuntimeException("High priority job should be running");
        }
        
        System.out.println("✓ No preemption when not necessary successful");
    }
}