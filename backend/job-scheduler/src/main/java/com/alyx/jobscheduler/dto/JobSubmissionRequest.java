package com.alyx.jobscheduler.dto;

import com.alyx.jobscheduler.model.JobParameters;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for job submission
 */
public class JobSubmissionRequest {
    
    @NotBlank(message = "User ID is required")
    private final String userId;
    
    @NotNull(message = "Job parameters are required")
    @Valid
    private final JobParameters parameters;

    @JsonCreator
    public JobSubmissionRequest(
            @JsonProperty("userId") String userId,
            @JsonProperty("parameters") JobParameters parameters) {
        this.userId = userId;
        this.parameters = parameters;
    }

    public String getUserId() { return userId; }
    public JobParameters getParameters() { return parameters; }
}