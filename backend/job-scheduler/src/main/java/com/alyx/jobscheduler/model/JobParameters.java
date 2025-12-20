package com.alyx.jobscheduler.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;

/**
 * Represents the parameters for an analysis job submission
 */
public class JobParameters {
    
    @NotBlank(message = "Job name cannot be empty")
    private final String jobName;
    
    private final String description;
    
    @NotNull(message = "Expected events count is required")
    @Positive(message = "Expected events must be positive")
    private final Integer expectedEvents;
    
    @NotNull(message = "Energy threshold is required")
    @Positive(message = "Energy threshold must be positive")
    private final Double energyThreshold;
    
    private final boolean highPriority;
    
    private final Map<String, Object> additionalParameters;

    @JsonCreator
    public JobParameters(
            @JsonProperty("jobName") String jobName,
            @JsonProperty("description") String description,
            @JsonProperty("expectedEvents") Integer expectedEvents,
            @JsonProperty("energyThreshold") Double energyThreshold,
            @JsonProperty("highPriority") boolean highPriority,
            @JsonProperty("additionalParameters") Map<String, Object> additionalParameters) {
        this.jobName = jobName;
        this.description = description;
        this.expectedEvents = expectedEvents;
        this.energyThreshold = energyThreshold;
        this.highPriority = highPriority;
        this.additionalParameters = additionalParameters;
    }

    // Convenience constructor for testing
    public JobParameters(String jobName, String description, Integer expectedEvents, 
                        Double energyThreshold, boolean highPriority) {
        this(jobName, description, expectedEvents, energyThreshold, highPriority, null);
    }

    public String getJobName() { return jobName; }
    public String getDescription() { return description; }
    public Integer getExpectedEvents() { return expectedEvents; }
    public Double getEnergyThreshold() { return energyThreshold; }
    public boolean isHighPriority() { return highPriority; }
    public Map<String, Object> getAdditionalParameters() { return additionalParameters; }
    
    @Override
    public String toString() {
        return "JobParameters{" +
                "jobName='" + jobName + '\'' +
                ", description='" + description + '\'' +
                ", expectedEvents=" + expectedEvents +
                ", energyThreshold=" + energyThreshold +
                ", highPriority=" + highPriority +
                '}';
    }
}