package com.alyx.datarouter.service;

import com.alyx.datarouter.model.DataDistributionRequest;
import com.alyx.datarouter.model.GridResource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DataRouterService {
    
    private final Map<String, GridResource> gridResources = new HashMap<>();
    
    /**
     * Distributes processing load across available GRID resources
     * Property 12: For any available GRID resource configuration, 
     * the system should automatically distribute processing load across multiple nodes
     */
    public List<GridResource> distributeLoad(List<DataDistributionRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<GridResource> availableResources = getAvailableResources();
        if (availableResources.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Sort requests by priority (highest first)
        List<DataDistributionRequest> sortedRequests = requests.stream()
            .sorted((r1, r2) -> r2.getPriority().compareTo(r1.getPriority()))
            .collect(Collectors.toList());
        
        List<GridResource> allocatedResources = new ArrayList<>();
        
        for (DataDistributionRequest request : sortedRequests) {
            GridResource bestResource = findBestResource(request, availableResources);
            if (bestResource != null) {
                allocatedResources.add(bestResource);
                // Update resource availability
                bestResource.setAvailableCores(bestResource.getAvailableCores() - request.getRequiredCores());
                bestResource.setAvailableMemoryMB(bestResource.getAvailableMemoryMB() - request.getRequiredMemoryMB());
            }
        }
        
        return allocatedResources;
    }
    
    private GridResource findBestResource(DataDistributionRequest request, List<GridResource> availableResources) {
        return availableResources.stream()
            .filter(resource -> resource.isOnline())
            .filter(resource -> resource.getAvailableCores() >= request.getRequiredCores())
            .filter(resource -> resource.getAvailableMemoryMB() >= request.getRequiredMemoryMB())
            .min(Comparator.comparingDouble(GridResource::getLoadScore))
            .orElse(null);
    }
    
    public List<GridResource> getAvailableResources() {
        return gridResources.values().stream()
            .filter(GridResource::isOnline)
            .collect(Collectors.toList());
    }
    
    public void addGridResource(GridResource resource) {
        gridResources.put(resource.getResourceId(), resource);
    }
    
    public void removeGridResource(String resourceId) {
        gridResources.remove(resourceId);
    }
    
    public void updateResourceStatus(String resourceId, double cpuUtilization, double memoryUtilization) {
        GridResource resource = gridResources.get(resourceId);
        if (resource != null) {
            resource.setCpuUtilization(cpuUtilization);
            resource.setMemoryUtilization(memoryUtilization);
        }
    }
}