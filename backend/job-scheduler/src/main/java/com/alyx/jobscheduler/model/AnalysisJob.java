package com.alyx.jobscheduler.model;

import com.fasterxml.jackson.annotation.JsonIgnore;


import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an analysis job in the system
 */
@Entity
@Table(name = "analysis_jobs", indexes = {
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_submitted_at", columnList = "submittedAt")
})
public class AnalysisJob {
    
    @Id
    private UUID jobId;
    
    @Column(nullable = false)
    private String userId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;
    
    @Column(columnDefinition = "jsonb")
    private JobParameters parameters;
    
    @Column(nullable = false)
    private Instant submittedAt;
    
    @Column
    private Instant estimatedCompletion;
    
    @Column
    private Instant actualCompletion;
    
    @Column
    private Integer allocatedCores;
    
    @Column
    private Long memoryAllocationMB;
    
    @Column
    private Double progressPercentage;
    
    @Column(length = 1000)
    private String errorMessage;
    
    @Column
    private Integer priority;

    // Default constructor for JPA
    public AnalysisJob() {}

    public AnalysisJob(String userId, JobParameters parameters) {
        this.jobId = UUID.randomUUID();
        this.userId = userId;
        this.parameters = parameters;
        this.status = JobStatus.SUBMITTED;
        this.submittedAt = Instant.now();
        this.progressPercentage = 0.0;
        this.priority = parameters.isHighPriority() ? 1 : 5; // 1 = high priority, 5 = normal
    }

    // Getters and setters
    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }

    public JobParameters getParameters() { return parameters; }
    public void setParameters(JobParameters parameters) { this.parameters = parameters; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getEstimatedCompletion() { return estimatedCompletion; }
    public void setEstimatedCompletion(Instant estimatedCompletion) { this.estimatedCompletion = estimatedCompletion; }

    public Instant getActualCompletion() { return actualCompletion; }
    public void setActualCompletion(Instant actualCompletion) { this.actualCompletion = actualCompletion; }

    public Integer getAllocatedCores() { return allocatedCores; }
    public void setAllocatedCores(Integer allocatedCores) { this.allocatedCores = allocatedCores; }

    public Long getMemoryAllocationMB() { return memoryAllocationMB; }
    public void setMemoryAllocationMB(Long memoryAllocationMB) { this.memoryAllocationMB = memoryAllocationMB; }

    public Double getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(Double progressPercentage) { this.progressPercentage = progressPercentage; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    @JsonIgnore
    public boolean isCompleted() {
        return status == JobStatus.COMPLETED || status == JobStatus.FAILED || status == JobStatus.CANCELLED;
    }

    @JsonIgnore
    public boolean canBeModified() {
        return status == JobStatus.SUBMITTED || status == JobStatus.QUEUED;
    }

    @JsonIgnore
    public boolean canBeCancelled() {
        return status != JobStatus.COMPLETED && status != JobStatus.FAILED && status != JobStatus.CANCELLED;
    }
}