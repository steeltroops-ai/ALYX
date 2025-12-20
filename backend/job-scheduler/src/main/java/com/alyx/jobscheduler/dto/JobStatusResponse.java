package com.alyx.jobscheduler.dto;

import com.alyx.jobscheduler.model.AnalysisJob;
import com.alyx.jobscheduler.model.JobStatus;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for job status queries
 */
public class JobStatusResponse {
    
    private final UUID jobId;
    private final JobStatus status;
    private final Double progressPercentage;
    private final Integer allocatedCores;
    private final Long memoryAllocationMB;
    private final Instant submittedAt;
    private final Instant estimatedCompletion;
    private final Instant actualCompletion;
    private final String errorMessage;

    @JsonCreator
    public JobStatusResponse(
            @JsonProperty("jobId") UUID jobId,
            @JsonProperty("status") JobStatus status,
            @JsonProperty("progressPercentage") Double progressPercentage,
            @JsonProperty("allocatedCores") Integer allocatedCores,
            @JsonProperty("memoryAllocationMB") Long memoryAllocationMB,
            @JsonProperty("submittedAt") Instant submittedAt,
            @JsonProperty("estimatedCompletion") Instant estimatedCompletion,
            @JsonProperty("actualCompletion") Instant actualCompletion,
            @JsonProperty("errorMessage") String errorMessage) {
        this.jobId = jobId;
        this.status = status;
        this.progressPercentage = progressPercentage;
        this.allocatedCores = allocatedCores;
        this.memoryAllocationMB = memoryAllocationMB;
        this.submittedAt = submittedAt;
        this.estimatedCompletion = estimatedCompletion;
        this.actualCompletion = actualCompletion;
        this.errorMessage = errorMessage;
    }

    public static JobStatusResponse fromAnalysisJob(AnalysisJob job) {
        return new JobStatusResponse(
            job.getJobId(),
            job.getStatus(),
            job.getProgressPercentage(),
            job.getAllocatedCores(),
            job.getMemoryAllocationMB(),
            job.getSubmittedAt(),
            job.getEstimatedCompletion(),
            job.getActualCompletion(),
            job.getErrorMessage()
        );
    }

    public UUID getJobId() { return jobId; }
    public JobStatus getStatus() { return status; }
    public Double getProgressPercentage() { return progressPercentage; }
    public Integer getAllocatedCores() { return allocatedCores; }
    public Long getMemoryAllocationMB() { return memoryAllocationMB; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getEstimatedCompletion() { return estimatedCompletion; }
    public Instant getActualCompletion() { return actualCompletion; }
    public String getErrorMessage() { return errorMessage; }
}