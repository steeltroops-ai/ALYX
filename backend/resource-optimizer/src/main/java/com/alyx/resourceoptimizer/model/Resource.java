package com.alyx.resourceoptimizer.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Resource {
    private String resourceId;
    private int totalCores;
    private long totalMemoryMB;
    private int availableCores;
    private long availableMemoryMB;
    private List<String> runningJobIds;
    private boolean isOnline;
    
    public Resource() {
        this.runningJobIds = new ArrayList<>();
    }
    
    public Resource(String resourceId, int totalCores, long totalMemoryMB) {
        this.resourceId = resourceId;
        this.totalCores = totalCores;
        this.totalMemoryMB = totalMemoryMB;
        this.availableCores = totalCores;
        this.availableMemoryMB = totalMemoryMB;
        this.runningJobIds = new ArrayList<>();
        this.isOnline = true;
    }
    
    public boolean canAccommodate(Job job) {
        return isOnline && 
               availableCores >= job.getRequiredCores() && 
               availableMemoryMB >= job.getRequiredMemoryMB();
    }
    
    public void allocateJob(Job job) {
        if (canAccommodate(job)) {
            availableCores -= job.getRequiredCores();
            availableMemoryMB -= job.getRequiredMemoryMB();
            runningJobIds.add(job.getJobId());
        }
    }
    
    public void deallocateJob(Job job) {
        if (runningJobIds.remove(job.getJobId())) {
            availableCores += job.getRequiredCores();
            availableMemoryMB += job.getRequiredMemoryMB();
        }
    }
    
    // Getters and setters
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    
    public int getTotalCores() { return totalCores; }
    public void setTotalCores(int totalCores) { this.totalCores = totalCores; }
    
    public long getTotalMemoryMB() { return totalMemoryMB; }
    public void setTotalMemoryMB(long totalMemoryMB) { this.totalMemoryMB = totalMemoryMB; }
    
    public int getAvailableCores() { return availableCores; }
    public void setAvailableCores(int availableCores) { this.availableCores = availableCores; }
    
    public long getAvailableMemoryMB() { return availableMemoryMB; }
    public void setAvailableMemoryMB(long availableMemoryMB) { this.availableMemoryMB = availableMemoryMB; }
    
    public List<String> getRunningJobIds() { return runningJobIds; }
    public void setRunningJobIds(List<String> runningJobIds) { this.runningJobIds = runningJobIds; }
    
    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Resource resource = (Resource) o;
        return Objects.equals(resourceId, resource.resourceId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(resourceId);
    }
}