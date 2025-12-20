package com.alyx.datarouter.model;

import java.util.List;
import java.util.Objects;

public class DataDistributionRequest {
    private String jobId;
    private List<String> dataFiles;
    private String preferredLocation;
    private int requiredCores;
    private long requiredMemoryMB;
    private Priority priority;
    
    public enum Priority {
        LOW, NORMAL, HIGH, CRITICAL
    }
    
    public DataDistributionRequest() {}
    
    public DataDistributionRequest(String jobId, List<String> dataFiles, 
                                 String preferredLocation, int requiredCores, 
                                 long requiredMemoryMB, Priority priority) {
        this.jobId = jobId;
        this.dataFiles = dataFiles;
        this.preferredLocation = preferredLocation;
        this.requiredCores = requiredCores;
        this.requiredMemoryMB = requiredMemoryMB;
        this.priority = priority;
    }
    
    // Getters and setters
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    
    public List<String> getDataFiles() { return dataFiles; }
    public void setDataFiles(List<String> dataFiles) { this.dataFiles = dataFiles; }
    
    public String getPreferredLocation() { return preferredLocation; }
    public void setPreferredLocation(String preferredLocation) { this.preferredLocation = preferredLocation; }
    
    public int getRequiredCores() { return requiredCores; }
    public void setRequiredCores(int requiredCores) { this.requiredCores = requiredCores; }
    
    public long getRequiredMemoryMB() { return requiredMemoryMB; }
    public void setRequiredMemoryMB(long requiredMemoryMB) { this.requiredMemoryMB = requiredMemoryMB; }
    
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataDistributionRequest that = (DataDistributionRequest) o;
        return Objects.equals(jobId, that.jobId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(jobId);
    }
}