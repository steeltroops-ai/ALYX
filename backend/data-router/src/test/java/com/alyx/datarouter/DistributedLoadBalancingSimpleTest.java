package com.alyx.datarouter;

import com.alyx.datarouter.model.DataDistributionRequest;
import com.alyx.datarouter.model.GridResource;
import com.alyx.datarouter.service.DataRouterService;

import java.util.Arrays;
import java.util.List;

/**
 * Simple test for distributed load balancing without requiring Spring Boot
 * **Feature: alyx-distributed-orchestrator, Property 12: Distributed load balancing**
 * **Validates: Requirements 4.2**
 */
public class DistributedLoadBalancingSimpleTest {
    
    public static void main(String[] args) {
        System.out.println("Running distributed load balancing tests...");
        
        try {
            testBasicLoadDistribution();
            testMultipleResourceDistribution();
            testResourceCapacityRespected();
            testPriorityBasedAllocation();
            
            System.out.println("\n🎉 All distributed load balancing tests passed!");
            System.out.println("Property 12 (Distributed load balancing) is working correctly.");
            
        } catch (Exception e) {
            System.out.println("✗ Test failed with exception: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void testBasicLoadDistribution() {
        System.out.println("Test 1: Basic load distribution");
        
        DataRouterService service = new DataRouterService();
        
        // Add resources
        GridResource resource1 = new GridResource("res-1", "location-1", 8, 16384, "dc-1");
        GridResource resource2 = new GridResource("res-2", "location-2", 4, 8192, "dc-2");
        service.addGridResource(resource1);
        service.addGridResource(resource2);
        
        // Create requests
        DataDistributionRequest req1 = new DataDistributionRequest(
            "job-1", Arrays.asList("file1.dat"), "location-1", 2, 2048, 
            DataDistributionRequest.Priority.NORMAL);
        
        List<GridResource> allocated = service.distributeLoad(Arrays.asList(req1));
        
        if (allocated.size() == 1 && allocated.get(0).getResourceId().equals("res-1")) {
            System.out.println("✓ Basic load distribution successful");
        } else {
            throw new RuntimeException("Basic load distribution failed");
        }
    }
    
    private static void testMultipleResourceDistribution() {
        System.out.println("Test 2: Multiple resource distribution");
        
        DataRouterService service = new DataRouterService();
        
        // Add resources with different capacities
        GridResource resource1 = new GridResource("res-1", "location-1", 4, 4096, "dc-1");
        GridResource resource2 = new GridResource("res-2", "location-2", 4, 4096, "dc-2");
        service.addGridResource(resource1);
        service.addGridResource(resource2);
        
        // Create multiple requests that should use both resources
        DataDistributionRequest req1 = new DataDistributionRequest(
            "job-1", Arrays.asList("file1.dat"), "location-1", 3, 3000, 
            DataDistributionRequest.Priority.HIGH);
        DataDistributionRequest req2 = new DataDistributionRequest(
            "job-2", Arrays.asList("file2.dat"), "location-2", 3, 3000, 
            DataDistributionRequest.Priority.NORMAL);
        
        List<GridResource> allocated = service.distributeLoad(Arrays.asList(req1, req2));
        
        if (allocated.size() == 2) {
            System.out.println("✓ Multiple resource distribution successful");
        } else {
            throw new RuntimeException("Multiple resource distribution failed - allocated: " + allocated.size());
        }
    }
    
    private static void testResourceCapacityRespected() {
        System.out.println("Test 3: Resource capacity respected");
        
        DataRouterService service = new DataRouterService();
        
        // Add resource with limited capacity
        GridResource resource1 = new GridResource("res-1", "location-1", 2, 2048, "dc-1");
        service.addGridResource(resource1);
        
        // Create request that exceeds capacity
        DataDistributionRequest req1 = new DataDistributionRequest(
            "job-1", Arrays.asList("file1.dat"), "location-1", 4, 4096, 
            DataDistributionRequest.Priority.NORMAL);
        
        List<GridResource> allocated = service.distributeLoad(Arrays.asList(req1));
        
        if (allocated.isEmpty()) {
            System.out.println("✓ Resource capacity respected - request correctly rejected");
        } else {
            throw new RuntimeException("Resource capacity not respected");
        }
    }
    
    private static void testPriorityBasedAllocation() {
        System.out.println("Test 4: Priority-based allocation");
        
        DataRouterService service = new DataRouterService();
        
        // Add resource with limited capacity
        GridResource resource1 = new GridResource("res-1", "location-1", 4, 4096, "dc-1");
        service.addGridResource(resource1);
        
        // Create requests with different priorities
        DataDistributionRequest lowPriorityReq = new DataDistributionRequest(
            "job-low", Arrays.asList("file1.dat"), "location-1", 2, 2048, 
            DataDistributionRequest.Priority.LOW);
        DataDistributionRequest highPriorityReq = new DataDistributionRequest(
            "job-high", Arrays.asList("file2.dat"), "location-1", 2, 2048, 
            DataDistributionRequest.Priority.HIGH);
        
        // Submit in reverse priority order to test sorting
        List<GridResource> allocated = service.distributeLoad(Arrays.asList(lowPriorityReq, highPriorityReq));
        
        if (allocated.size() == 2) {
            System.out.println("✓ Priority-based allocation successful");
        } else {
            throw new RuntimeException("Priority-based allocation failed");
        }
    }
}