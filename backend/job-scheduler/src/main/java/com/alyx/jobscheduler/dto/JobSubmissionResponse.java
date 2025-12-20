package com.alyx.jobscheduler.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for job submission
 */
public class JobSubmissionResponse {
    
    private final UUID jobId;
    private final Instant estimatedCompletion;
    private final boolean success;
    private final String message;

    @JsonCreator
    public JobSubmissionResponse(
            @JsonProperty("jobId") UUID jobId,
            @JsonProperty("estimatedCompletion") Instant estimatedCompletion,
            @JsonProperty("success") boolean success,
            @JsonProperty("message") String message) {
        this.jobId = jobId;
        this.estimatedCompletion = estimatedCompletion;
        this.success = success;
        this.message = message;
    }

    public static JobSubmissionResponse success(UUID jobId, Instant estimatedCompletion) {
        return new JobSubmissionResponse(jobId, estimatedCompletion, true, "Job submitted successfully");
    }

    public static JobSubmissionResponse failure(String message) {
        return new JobSubmissionResponse(null, null, false, message);
    }

    public UUID getJobId() { return jobId; }
    public Instant getEstimatedCompletion() { return estimatedCompletion; }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}