package com.alyx.datarouter.service;

import com.alyx.datarouter.model.GridResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Service for monitoring GRID resource availability and health
 * Implements fault tolerance and automatic failover mechanisms
 */
@Service
public class ResourceMonitoringService {
    
    @Autowired
    private DataRouterService dataRouterService;
    
    private static final long HEARTBEAT_TIMEOUT_MINUTES = 5;
    
    /**
     * Periodically checks resource health and removes unresponsive resources
     * Runs every 2 minutes
     */
    @Scheduled(fixedRate = 120000) // 2 minutes
    public void monitorResourceHealth() {
        List<GridResource> resources = dataRouterService.getAvailableResources();
        Instant cutoffTime = Instant.now().minus(HEARTBEAT_TIMEOUT_MINUTES, ChronoUnit.MINUTES);
        
        for (GridResource resource : resources) {
            if (resource.getLastHeartbeat() != null && 
                resource.getLastHeartbeat().isBefore(cutoffTime)) {
                
                // Mark resource as offline due to missed heartbeats
                resource.setOnline(false);
                System.out.println("Resource " + resource.getResourceId() + 
                                 " marked offline due to missed heartbeats");
            }
        }
    }
    
    /**
     * Updates resource heartbeat to indicate it's still alive
     */
    public void updateResourceHeartbeat(String resourceId) {
        List<GridResource> resources = dataRouterService.getAvailableResources();
        resources.stream()
            .filter(r -> r.getResourceId().equals(resourceId))
            .findFirst()
            .ifPresent(resource -> {
                resource.setLastHeartbeat(Instant.now());
                resource.setOnline(true);
            });
    }
    
    /**
     * Implements data locality-aware scheduling by finding resources
     * in the same datacenter or location as the data
     */
    public GridResource findLocalResource(String preferredLocation, int requiredCores, long requiredMemoryMB) {
        List<GridResource> resources = dataRouterService.getAvailableResources();
        
        // First try to find a resource in the same location
        return resources.stream()
            .filter(GridResource::isOnline)
            .filter(r -> r.getLocation().equals(preferredLocation))
            .filter(r -> r.getAvailableCores() >= requiredCores)
            .filter(r -> r.getAvailableMemoryMB() >= requiredMemoryMB)
            .min((r1, r2) -> Double.compare(r1.getLoadScore(), r2.getLoadScore()))
            .orElse(
                // If no local resource, find any available resource
                resources.stream()
                    .filter(GridResource::isOnline)
                    .filter(r -> r.getAvailableCores() >= requiredCores)
                    .filter(r -> r.getAvailableMemoryMB() >= requiredMemoryMB)
                    .min((r1, r2) -> Double.compare(r1.getLoadScore(), r2.getLoadScore()))
                    .orElse(null)
            );
    }
}