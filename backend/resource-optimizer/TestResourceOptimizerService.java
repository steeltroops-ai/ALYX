package com.alyx.resourceoptimizer.service;

import com.alyx.resourceoptimizer.model.Job;
import com.alyx.resourceoptimizer.model.Resource;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class TestResourceOptimizerService {
    
    private final Map<String, Resource> resources = new HashMap<>();
    private final Map<String, Job> jobs = new HashMap<>();
    private final Queue<Job> jobQueue = new PriorityQueue<>(
        Comparator.comparing((Job j) -> j.getPriority().getLevel()).reversed()
            .thenComparing(Job::getSubmittedAt)
    );
    
    public boolean scheduleJobWithPreemption(Job newJob) {
        jobs.put(newJob.getJobId(), newJob);
        
        // Try to find available resource first
        Resource availableResource = findAvailableResource(newJob);
        if (availableResource != null) {
            allocateJobToResource(newJob, availableResource);
            return true;
        }
        
        // If no available resource, check if we can preempt lower priority jobs
        if (canPreemptForJob(newJob)) {
            Resource resourceForPreemption = findResourceForPreemption(newJob);
            if (resourceForPreemption != null) {
                preemptJobsOnResource(resourceForPreemption, newJob);
                allocateJobToResource(newJob, resourceForPreemption);
                return true;
            }
        }
        
        // Queue the job if no preemption is possible
        jobQueue.offer(newJob);
        return false;
    }
    
    private Resource findAvailableResource(Job job) {
        return resources.values().stream()
            .filter(resource -> resource.canAccommodate(job))
            .findFirst()
            .orElse(null);
    }
    
    private boolean canPreemptForJob(Job newJob) {
        return resources.values().stream()
            .anyMatch(resource -> canPreemptOnResource(resource, newJob));
    }
    
    private boolean canPreemptOnResource(Resource resource, Job newJob) {
        if (!resource.isOnline()) {
            return false;
        }
        
        // Find running jobs that can be preempted
        List<Job> preemptableJobs = resource.getRunningJobIds().stream()
            .map(jobs::get)
            .filter(Objects::nonNull)
            .filter(Job::canBePreempted)
            .filter(job -> job.getPriority().getLevel() < newJob.getPriority().getLevel())
            .collect(Collectors.toList());
        
        // Calculate if preempting these jobs would free enough resources
        int coresFreed = preemptableJobs.stream().mapToInt(Job::getRequiredCores).sum();
        long memoryFreed = preemptableJobs.stream().mapToLong(Job::getRequiredMemoryMB).sum();
        
        int totalAvailableCores = resource.getAvailableCores() + coresFreed;
        long totalAvailableMemory = resource.getAvailableMemoryMB() + memoryFreed;
        
        return totalAvailableCores >= newJob.getRequiredCores() && 
               totalAvailableMemory >= newJob.getRequiredMemoryMB();
    }
    
    private Resource findResourceForPreemption(Job newJob) {
        return resources.values().stream()
            .filter(resource -> canPreemptOnResource(resource, newJob))
            .min(Comparator.comparingInt(resource -> resource.getRunningJobIds().size()))
            .orElse(null);
    }
    
    private void preemptJobsOnResource(Resource resource, Job newJob) {
        List<Job> jobsToPreempt = resource.getRunningJobIds().stream()
            .map(jobs::get)
            .filter(Objects::nonNull)
            .filter(Job::canBePreempted)
            .filter(job -> job.getPriority().getLevel() < newJob.getPriority().getLevel())
            .sorted(Comparator.comparing((Job j) -> j.getPriority().getLevel())
                .thenComparing(Job::getStartedAt).reversed())
            .collect(Collectors.toList());
        
        int coresNeeded = newJob.getRequiredCores() - resource.getAvailableCores();
        long memoryNeeded = newJob.getRequiredMemoryMB() - resource.getAvailableMemoryMB();
        
        int coresFreed = 0;
        long memoryFreed = 0;
        
        for (Job jobToPreempt : jobsToPreempt) {
            if (coresFreed >= coresNeeded && memoryFreed >= memoryNeeded) {
                break;
            }
            
            // Preempt the job
            jobToPreempt.setStatus(Job.JobStatus.PREEMPTED);
            jobToPreempt.setAssignedResourceId(null);
            resource.deallocateJob(jobToPreempt);
            jobQueue.offer(jobToPreempt); // Re-queue preempted job
            
            coresFreed += jobToPreempt.getRequiredCores();
            memoryFreed += jobToPreempt.getRequiredMemoryMB();
        }
    }
    
    private void allocateJobToResource(Job job, Resource resource) {
        job.setStatus(Job.JobStatus.RUNNING);
        job.setStartedAt(Instant.now());
        job.setAssignedResourceId(resource.getResourceId());
        resource.allocateJob(job);
    }
    
    public void addResource(Resource resource) {
        resources.put(resource.getResourceId(), resource);
    }
    
    public double getUtilizationRatio(Resource resource) {
        double coreUtilization = 1.0 - ((double) resource.getAvailableCores() / resource.getTotalCores());
        double memoryUtilization = 1.0 - ((double) resource.getAvailableMemoryMB() / resource.getTotalMemoryMB());
        return Math.max(coreUtilization, memoryUtilization);
    }
    
    public void optimizeResourceAllocation() {
        // Find underutilized and overutilized resources
        List<Resource> underutilized = findUnderutilizedResources();
        List<Resource> overutilized = findOverutilizedResources();
        
        // Rebalance jobs between resources
        for (Resource overloaded : overutilized) {
            for (Resource underused : underutilized) {
                if (canMigrateJobs(overloaded, underused)) {
                    migrateJobs(overloaded, underused);
                }
            }
        }
    }
    
    private List<Resource> findUnderutilizedResources() {
        return resources.values().stream()
            .filter(Resource::isOnline)
            .filter(resource -> getUtilizationRatio(resource) < 0.3) // Less than 30% utilized
            .collect(Collectors.toList());
    }
    
    private List<Resource> findOverutilizedResources() {
        return resources.values().stream()
            .filter(Resource::isOnline)
            .filter(resource -> getUtilizationRatio(resource) > 0.8) // More than 80% utilized
            .collect(Collectors.toList());
    }
    
    private boolean canMigrateJobs(Resource from, Resource to) {
        // Find jobs that can be migrated
        List<Job> migrableJobs = from.getRunningJobIds().stream()
            .map(jobs::get)
            .filter(Objects::nonNull)
            .filter(job -> job.getPriority() != Job.Priority.CRITICAL) // Don't migrate critical jobs
            .filter(job -> to.getAvailableCores() >= job.getRequiredCores() && 
                          to.getAvailableMemoryMB() >= job.getRequiredMemoryMB())
            .collect(Collectors.toList());
        
        return !migrableJobs.isEmpty();
    }
    
    private void migrateJobs(Resource from, Resource to) {
        // Find the best job to migrate (smallest first to minimize disruption)
        Optional<Job> jobToMigrate = from.getRunningJobIds().stream()
            .map(jobs::get)
            .filter(Objects::nonNull)
            .filter(job -> job.getPriority() != Job.Priority.CRITICAL)
            .filter(job -> to.getAvailableCores() >= job.getRequiredCores() && 
                          to.getAvailableMemoryMB() >= job.getRequiredMemoryMB())
            .min(Comparator.comparingInt(Job::getRequiredCores));
        
        if (jobToMigrate.isPresent()) {
            Job job = jobToMigrate.get();
            
            // Remove from source resource
            from.deallocateJob(job);
            
            // Add to target resource
            to.allocateJob(job);
            job.setAssignedResourceId(to.getResourceId());
        }
    }
    
    public double getOverallSystemUtilization() {
        if (resources.isEmpty()) {
            return 0.0;
        }
        
        return resources.values().stream()
            .filter(Resource::isOnline)
            .mapToDouble(this::getUtilizationRatio)
            .average()
            .orElse(0.0);
    }
    
    public void updateResourceUtilization(String resourceId, double cpuLoad, double memoryLoad) {
        Resource resource = resources.get(resourceId);
        if (resource != null && resource.isOnline()) {
            // Simulate resource load changes
            int newAvailableCores = (int) (resource.getTotalCores() * (1.0 - cpuLoad));
            long newAvailableMemory = (long) (resource.getTotalMemoryMB() * (1.0 - memoryLoad));
            
            resource.setAvailableCores(Math.max(0, newAvailableCores));
            resource.setAvailableMemoryMB(Math.max(0, newAvailableMemory));
        }
    }
}