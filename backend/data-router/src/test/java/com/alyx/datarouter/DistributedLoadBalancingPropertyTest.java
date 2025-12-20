package com.alyx.datarouter;

import com.alyx.datarouter.model.DataDistributionRequest;
import com.alyx.datarouter.model.GridResource;
import com.alyx.datarouter.service.DataRouterService;
import net.java.quickcheck.Generator;
import net.java.quickcheck.QuickCheck;
import net.java.quickcheck.characteristic.AbstractCharacteristic;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.java.quickcheck.generator.PrimitiveGenerators.*;

/**
 * **Feature: alyx-distributed-orchestrator, Property 12: Distributed load balancing**
 * **Validates: Requirements 4.2**
 */
public class DistributedLoadBalancingPropertyTest {
    
    @Test
    public void testDistributedLoadBalancing() {
        // **Feature: alyx-distributed-orchestrator, Property 12: Distributed load balancing**
        QuickCheck.forAll(gridResourceListGenerator(), dataDistributionRequestListGenerator(),
            new AbstractCharacteristic<List<GridResource>, List<DataDistributionRequest>>() {
                @Override
                protected void doSpecify(List<GridResource> gridResources, List<DataDistributionRequest> requests) throws Throwable {
                    DataRouterService dataRouterService = new DataRouterService();
                    
                    // Setup: Add grid resources to the service
                    for (GridResource resource : gridResources) {
                        dataRouterService.addGridResource(resource);
                    }
                    
                    // Act: Distribute load
                    List<GridResource> allocatedResources = dataRouterService.distributeLoad(requests);
                    
                    // Property: For any available GRID resource configuration, 
                    // the system should automatically distribute processing load across multiple nodes
                    
                    // 1. All allocated resources should be from the available resources
                    List<GridResource> availableResources = dataRouterService.getAvailableResources();
                    boolean allAllocatedAreAvailable = allocatedResources.stream()
                        .allMatch(allocated -> availableResources.stream()
                            .anyMatch(available -> available.getResourceId().equals(allocated.getResourceId())));
                    
                    assert allAllocatedAreAvailable : "All allocated resources should be from available resources";
                    
                    // 2. No resource should be over-allocated
                    boolean noOverAllocation = allocatedResources.stream()
                        .allMatch(resource -> resource.getAvailableCores() >= 0 && 
                                            resource.getAvailableMemoryMB() >= 0);
                    
                    assert noOverAllocation : "No resource should be over-allocated";
                    
                    // 3. If there are multiple resources and multiple requests, 
                    // load should be distributed when possible
                    if (gridResources.size() > 1 && requests.size() > 1 && !allocatedResources.isEmpty()) {
                        // Check if we're using more than one resource when possible
                        long uniqueResourcesUsed = allocatedResources.stream()
                            .map(GridResource::getResourceId)
                            .distinct()
                            .count();
                        
                        // If we have sufficient resources, we should use multiple
                        long resourcesWithCapacity = availableResources.stream()
                            .filter(r -> requests.stream().anyMatch(req -> 
                                r.getAvailableCores() >= req.getRequiredCores() && 
                                r.getAvailableMemoryMB() >= req.getRequiredMemoryMB()))
                            .count();
                        
                        // If we have multiple capable resources and multiple requests, 
                        // we should distribute unless all requests fit on one resource
                        if (resourcesWithCapacity > 1 && requests.size() > 1) {
                            boolean canFitAllOnOneResource = availableResources.stream()
                                .anyMatch(resource -> {
                                    int totalCores = requests.stream().mapToInt(DataDistributionRequest::getRequiredCores).sum();
                                    long totalMemory = requests.stream().mapToLong(DataDistributionRequest::getRequiredMemoryMB).sum();
                                    return resource.getAvailableCores() >= totalCores && 
                                           resource.getAvailableMemoryMB() >= totalMemory;
                                });
                            
                            // If we can't fit all on one resource, we should use multiple
                            if (!canFitAllOnOneResource && allocatedResources.size() > 1) {
                                assert uniqueResourcesUsed > 1 : "Should distribute load across multiple resources when necessary";
                            }
                        }
                    }
                }
            });
    }
    
    private static Generator<List<GridResource>> gridResourceListGenerator() {
        return integers(1, 5).flatMap(size -> {
            return new Generator<List<GridResource>>() {
                @Override
                public List<GridResource> next() {
                    List<GridResource> resources = new ArrayList<>();
                    for (int i = 0; i < size; i++) {
                        GridResource resource = new GridResource(
                            "resource-" + i,
                            "location-" + (i % 3), // 3 different locations
                            integers(2, 16).next(), // 2-16 cores
                            longs(2048L, 32768L).next(), // 2-32 GB memory
                            "datacenter-" + (i % 2) // 2 datacenters
                        );
                        resource.setCpuUtilization(doubles(0.0, 0.5).next());
                        resource.setMemoryUtilization(doubles(0.0, 0.5).next());
                        resources.add(resource);
                    }
                    return resources;
                }
            };
        });
    }
    
    private static Generator<List<DataDistributionRequest>> dataDistributionRequestListGenerator() {
        return integers(1, 4).flatMap(size -> {
            return new Generator<List<DataDistributionRequest>>() {
                @Override
                public List<DataDistributionRequest> next() {
                    List<DataDistributionRequest> requests = new ArrayList<>();
                    for (int i = 0; i < size; i++) {
                        DataDistributionRequest request = new DataDistributionRequest(
                            "job-" + i,
                            Arrays.asList("file-" + i + ".dat"),
                            "location-" + (i % 3),
                            integers(1, 4).next(), // 1-4 cores required
                            longs(512L, 4096L).next(), // 0.5-4 GB memory required
                            DataDistributionRequest.Priority.values()[i % DataDistributionRequest.Priority.values().length]
                        );
                        requests.add(request);
                    }
                    return requests;
                }
            };
        });
    }
}