package com.alyx.dataprocessing.model;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an analysis job that processes collision event data.
 * Tracks job lifecycle, resource allocation, and execution status.
 */
@Entity
@Table(name = "analysis_jobs", indexes = {
    @Index(name = "idx_analysis_jobs_user_id", columnList = "user_id"),
    @Index(name = "idx_analysis_jobs_status", columnList = "status"),
    @Index(name = "idx_analysis_jobs_submitted_at", columnList = "submitted_at"),
    @Index(name = "idx_analysis_jobs_priority", columnList = "priority")
})
public class AnalysisJob {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "job_id")
    private UUID jobId;
    
    @NotNull
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobStatus status;
    
    @NotNull
    @Column(name = "job_type", nullable = false)
    private String jobType;
    
    @Type(JsonType.class)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private Map<String, Object> parameters;
    
    @NotNull
    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
    
    @Column(name = "started_at")
    private Instant startedAt;
    
    @Column(name = "completed_at")
    private Instant completedAt;
    
    @Column(name = "estimated_completion")
    private Instant estimatedCompletion;
    
    @Column(name = "allocated_cores")
    private Integer allocatedCores;
    
    @Column(name = "memory_allocation_mb")
    private Long memoryAllocationMB;
    
    @Column(name = "priority")
    private Integer priority;
    
    @Column(name = "progress_percentage")
    private Double progressPercentage;
    
    @Column(name = "error_message", length = 2000)
    private String errorMessage;
    
    @Type(JsonType.class)
    @Column(name = "result_metadata", columnDefinition = "jsonb")
    private Map<String, Object> resultMetadata;
    
    @Column(name = "resource_node")
    private String resourceNode;
    
    @Column(name = "execution_time_seconds")
    private Long executionTimeSeconds;
    
    @Column(name = "data_processed_mb")
    private Long dataProcessedMB;
    
    @Column(name = "checksum")
    private String checksum;
    
    // Constructors
    public AnalysisJob() {
        this.status = JobStatus.QUEUED;
        this.submittedAt = Instant.now();
        this.progressPercentage = 0.0;
        this.priority = 5; // Default priority
    }
    
    public AnalysisJob(String userId, String jobType, Map<String, Object> parameters) {
        this();
        this.userId = userId;
        this.jobType = jobType;
        this.parameters = parameters;
    }
    
    // Getters and Setters
    public UUID getJobId() {
        return jobId;
    }
    
    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public JobStatus getStatus() {
        return status;
    }
    
    public void setStatus(JobStatus status) {
        this.status = status;
    }
    
    public String getJobType() {
        return jobType;
    }
    
    public void setJobType(String jobType) {
        this.jobType = jobType;
    }
    
    public Map<String, Object> getParameters() {
        return parameters;
    }
    
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }
    
    public Instant getSubmittedAt() {
        return submittedAt;
    }
    
    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }
    
    public Instant getStartedAt() {
        return startedAt;
    }
    
    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }
    
    public Instant getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
    
    public Instant getEstimatedCompletion() {
        return estimatedCompletion;
    }
    
    public void setEstimatedCompletion(Instant estimatedCompletion) {
        this.estimatedCompletion = estimatedCompletion;
    }
    
    public Integer getAllocatedCores() {
        return allocatedCores;
    }
    
    public void setAllocatedCores(Integer allocatedCores) {
        this.allocatedCores = allocatedCores;
    }
    
    public Long getMemoryAllocationMB() {
        return memoryAllocationMB;
    }
    
    public void setMemoryAllocationMB(Long memoryAllocationMB) {
        this.memoryAllocationMB = memoryAllocationMB;
    }
    
    public Integer getPriority() {
        return priority;
    }
    
    public void setPriority(Integer priority) {
        this.priority = priority;
    }
    
    public Double getProgressPercentage() {
        return progressPercentage;
    }
    
    public void setProgressPercentage(Double progressPercentage) {
        this.progressPercentage = progressPercentage;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public Map<String, Object> getResultMetadata() {
        return resultMetadata;
    }
    
    public void setResultMetadata(Map<String, Object> resultMetadata) {
        this.resultMetadata = resultMetadata;
    }
    
    public String getResourceNode() {
        return resourceNode;
    }
    
    public void setResourceNode(String resourceNode) {
        this.resourceNode = resourceNode;
    }
    
    public Long getExecutionTimeSeconds() {
        return executionTimeSeconds;
    }
    
    public void setExecutionTimeSeconds(Long executionTimeSeconds) {
        this.executionTimeSeconds = executionTimeSeconds;
    }
    
    public Long getDataProcessedMB() {
        return dataProcessedMB;
    }
    
    public void setDataProcessedMB(Long dataProcessedMB) {
        this.dataProcessedMB = dataProcessedMB;
    }
    
    public String getChecksum() {
        return checksum;
    }
    
    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }
    
    // Utility methods
    public void markAsStarted() {
        this.status = JobStatus.RUNNING;
        this.startedAt = Instant.now();
    }
    
    public void markAsCompleted() {
        this.status = JobStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.progressPercentage = 100.0;
        if (this.startedAt != null) {
            this.executionTimeSeconds = java.time.Duration.between(this.startedAt, this.completedAt).getSeconds();
        }
    }
    
    public void markAsFailed(String errorMessage) {
        this.status = JobStatus.FAILED;
        this.completedAt = Instant.now();
        this.errorMessage = errorMessage;
        if (this.startedAt != null) {
            this.executionTimeSeconds = java.time.Duration.between(this.startedAt, this.completedAt).getSeconds();
        }
    }
    
    public boolean isActive() {
        return status == JobStatus.QUEUED || status == JobStatus.RUNNING;
    }
    
    public boolean isCompleted() {
        return status == JobStatus.COMPLETED || status == JobStatus.FAILED || status == JobStatus.CANCELLED;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnalysisJob)) return false;
        AnalysisJob that = (AnalysisJob) o;
        return jobId != null && jobId.equals(that.jobId);
    }
    
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
    
    @Override
    public String toString() {
        return "AnalysisJob{" +
                "jobId=" + jobId +
                ", userId='" + userId + '\'' +
                ", status=" + status +
                ", jobType='" + jobType + '\'' +
                ", submittedAt=" + submittedAt +
                ", priority=" + priority +
                '}';
    }
}