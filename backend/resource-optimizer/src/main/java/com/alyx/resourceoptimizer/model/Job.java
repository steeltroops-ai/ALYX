package com.alyx.resourceoptimizer.model;

import java.time.Instant;
import java.util.Objects;

public class Job {
    private String jobId;
    private Priority priority;
    private JobStatus status;
    private int requiredCores;
    private long requiredMemoryMB;
    private Instant submittedAt;
    private Instant startedAt;
    private String assignedResourceId;
    private boolean canBePreempted;
    
    public enum Priority {
        LOW(1), NORMAL(2), HIGH(3), CRITICAL(4);
        
        private final int level;
        
        Priority(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    public enum JobStatus {
        QUEUED, RUNNING, PREEMPTED, COMPLETED, FAILED
    }
    
    public Job() {}
    
    public Job(String jobId, Priority priority, int requiredCores, long requiredMemoryMB) {
        this.jobId = jobId;
        this.priority = priority;
        this.requiredCores = requiredCores;
        this.requiredMemoryMB = requiredMemoryMB;
        this.status = JobStatus.QUEUED;
        this.submittedAt = Instant.now();
        this.canBePreempted = priority != Priority.CRITICAL;
    }
    
    // Getters and setters
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    
    public int getRequiredCores() { return requiredCores; }
    public void setRequiredCores(int requiredCores) { this.requiredCores = requiredCores; }
    
    public long getRequiredMemoryMB() { return requiredMemoryMB; }
    public void setRequiredMemoryMB(long requiredMemoryMB) { this.requiredMemoryMB = requiredMemoryMB; }
    
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    
    public String getAssignedResourceId() { return assignedResourceId; }
    public void setAssignedResourceId(String assignedResourceId) { this.assignedResourceId = assignedResourceId; }
    
    public boolean canBePreempted() { return canBePreempted; }
    public void setCanBePreempted(boolean canBePreempted) { this.canBePreempted = canBePreempted; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Job job = (Job) o;
        return Objects.equals(jobId, job.jobId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(jobId);
    }
}