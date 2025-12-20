package com.alyx.datarouter.model;

import java.time.Instant;
import java.util.Objects;

public class GridResource {
    private String resourceId;
    private String location;
    private int availableCores;
    private long availableMemoryMB;
    private double cpuUtilization;
    private double memoryUtilization;
    private boolean isOnline;
    private Instant lastHeartbeat;
    private String datacenter;
    
    public GridResource() {}
    
    public GridResource(String resourceId, String location, int availableCores, 
                       long availableMemoryMB, String datacenter) {
        this.resourceId = resourceId;
        this.location = location;
        this.availableCores = availableCores;
        this.availableMemoryMB = availableMemoryMB;
        this.datacenter = datacenter;
        this.isOnline = true;
        this.lastHeartbeat = Instant.now();
        this.cpuUtilization = 0.0;
        this.memoryUtilization = 0.0;
    }
    
    // Getters and setters
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    
    public int getAvailableCores() { return availableCores; }
    public void setAvailableCores(int availableCores) { this.availableCores = availableCores; }
    
    public long getAvailableMemoryMB() { return availableMemoryMB; }
    public void setAvailableMemoryMB(long availableMemoryMB) { this.availableMemoryMB = availableMemoryMB; }
    
    public double getCpuUtilization() { return cpuUtilization; }
    public void setCpuUtilization(double cpuUtilization) { this.cpuUtilization = cpuUtilization; }
    
    public double getMemoryUtilization() { return memoryUtilization; }
    public void setMemoryUtilization(double memoryUtilization) { this.memoryUtilization = memoryUtilization; }
    
    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }
    
    public Instant getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(Instant lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    
    public String getDatacenter() { return datacenter; }
    public void setDatacenter(String datacenter) { this.datacenter = datacenter; }
    
    public double getLoadScore() {
        return (cpuUtilization + memoryUtilization) / 2.0;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GridResource that = (GridResource) o;
        return Objects.equals(resourceId, that.resourceId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(resourceId);
    }
}