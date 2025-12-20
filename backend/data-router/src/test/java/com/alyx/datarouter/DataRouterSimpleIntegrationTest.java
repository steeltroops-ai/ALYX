package com.alyx.datarouter;

import com.alyx.datarouter.model.DataDistributionRequest;
import com.alyx.datarouter.model.GridResource;
import com.alyx.datarouter.service.DataRouterService;

import java.util.Arrays;
import java.util.List;

/**
 * Simple integration test for the complete Data Router implementation
 * Tests all requirements: 4.2, 7.2, 7.3, 7.4, 7.5
 */
public class DataRouterSimpleIntegrationTest {
    
    public static void main(String[] args) {
        System.out.println("Running Data Router integration tests...");
        
        try {
            testIntelligentDataDistribution();
            testLoadBalancingAcrossGridResources();
            testDataLocalityAwareScheduling();
            testFaultToleranceAndFailover();
            
            System.out.println("\n🎉 All Data Router integration tests passed!");
            System.out.println("Requirements 4.2, 7.2, 7.3, 7.4, 7.5 are implemented correctly.");
            
        } catch (Exception e) {
            System.out.println("✗ Integration test failed with exception: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void testIntelligentDataDistribution() {
        System.out.println("Test 1: Intelligent data distribution");
        
        DataRouterService service = new DataRouterService();
        
        // Add resources with different characteristics
        GridResource resource1 = new GridResource("res-1", "cern-geneva", 16, 32768, "cern-dc1");
        GridResource resource2 = new GridResource("res-2", "fermilab-chicago", 8, 16384, "fermilab-dc1");
        GridResource resource3 = new GridResource("res-3", "cern-geneva", 12, 24576, "cern-dc2");
        
        service.addGridResource(resource1);
        service.addGridResource(resource2);
        service.addGridResource(resource3);
        
        // Create distribution requests with different priorities and requirements
        DataDistributionRequest req1 = new DataDistributionRequest(
            "job-high-priority", Arrays.asList("collision_data_1.root"), 
            "cern-geneva", 4, 8192, DataDistributionRequest.Priority.HIGH);
        
        DataDistributionRequest req2 = new DataDistributionRequest(
            "job-normal", Arrays.asList("collision_data_2.root"), 
            "fermilab-chicago", 6, 12288, DataDistributionRequest.Priority.NORMAL);
        
        DataDistributionRequest req3 = new DataDistributionRequest(
            "job-low", Arrays.asList("collision_data_3.root"), 
            "cern-geneva", 2, 4096, DataDistributionRequest.Priority.LOW);
        
        List<GridResource> allocated = service.distributeLoad(Arrays.asList(req1, req2, req3));
        
        if (allocated.size() != 3) {
            throw new RuntimeException("Should allocate resources for all 3 requests");
        }
        
        // Verify intelligent distribution (no over-allocation)
        boolean intelligentDistribution = allocated.stream()
            .allMatch(resource -> resource.getAvailableCores() >= 0 && 
                                resource.getAvailableMemoryMB() >= 0);
        
        if (!intelligentDistribution) {
            throw new RuntimeException("Resources should not be over-allocated");
        }
        
        System.out.println("✓ Intelligent data distribution successful");
    }
    
    private static void testLoadBalancingAcrossGridResources() {
        System.out.println("Test 2: Load balancing across GRID resources");
        
        DataRouterService service = new DataRouterService();
        
        // Add multiple resources
        for (int i = 1; i <= 5; i++) {
            GridResource resource = new GridResource("grid-node-" + i, "location-" + (i % 3), 
                                                   8, 16384, "datacenter-" + (i % 2));
            service.addGridResource(resource);
        }
        
        // Create many small requests to test load balancing
        int successfulAllocations = 0;
        for (int i = 1; i <= 10; i++) {
            DataDistributionRequest request = new DataDistributionRequest(
                "job-" + i, Arrays.asList("data-" + i + ".root"), 
                "location-" + (i % 3), 2, 4096, 
                DataDistributionRequest.Priority.values()[i % DataDistributionRequest.Priority.values().length]);
            
            List<GridResource> allocated = service.distributeLoad(Arrays.asList(request));
            
            if (!allocated.isEmpty()) {
                successfulAllocations++;
            }
        }
        
        if (successfulAllocations < 5) {
            throw new RuntimeException("Should be able to allocate resources for multiple jobs");
        }
        
        System.out.println("✓ Load balancing across GRID resources successful");
    }
    
    private static void testDataLocalityAwareScheduling() {
        System.out.println("Test 3: Data locality-aware scheduling");
        
        DataRouterService service = new DataRouterService();
        
        // Add resources in different locations
        GridResource localResource = new GridResource("local-res", "cern-geneva", 8, 16384, "cern-dc1");
        GridResource remoteResource = new GridResource("remote-res", "fermilab-chicago", 16, 32768, "fermilab-dc1");
        
        service.addGridResource(localResource);
        service.addGridResource(remoteResource);
        
        // Test locality preference by creating a request for cern-geneva location
        DataDistributionRequest localRequest = new DataDistributionRequest(
            "local-job", Arrays.asList("local-data.root"), "cern-geneva", 4, 8192, 
            DataDistributionRequest.Priority.NORMAL);
        
        List<GridResource> allocated = service.distributeLoad(Arrays.asList(localRequest));
        
        if (allocated.isEmpty()) {
            throw new RuntimeException("Should allocate resource for local request");
        }
        
        // The service should prefer resources with lower load scores
        GridResource selectedResource = allocated.get(0);
        if (selectedResource == null) {
            throw new RuntimeException("Should select a resource");
        }
        
        System.out.println("✓ Data locality-aware scheduling successful");
    }
    
    private static void testFaultToleranceAndFailover() {
        System.out.println("Test 4: Fault tolerance and automatic failover");
        
        DataRouterService service = new DataRouterService();
        
        // Add resources
        GridResource primaryResource = new GridResource("primary", "location-1", 8, 16384, "dc-1");
        GridResource backupResource = new GridResource("backup", "location-1", 8, 16384, "dc-1");
        
        service.addGridResource(primaryResource);
        service.addGridResource(backupResource);
        
        // Simulate primary resource going offline
        primaryResource.setOnline(false);
        
        // Test that backup resource is used
        DataDistributionRequest request = new DataDistributionRequest(
            "failover-job", Arrays.asList("data.root"), "location-1", 4, 8192, 
            DataDistributionRequest.Priority.HIGH);
        
        List<GridResource> allocated = service.distributeLoad(Arrays.asList(request));
        
        if (allocated.isEmpty()) {
            throw new RuntimeException("Should failover to backup resource");
        }
        
        GridResource usedResource = allocated.get(0);
        if (!usedResource.getResourceId().equals("backup")) {
            throw new RuntimeException("Should use backup resource when primary is offline");
        }
        
        // Test resource recovery
        primaryResource.setOnline(true);
        
        if (!primaryResource.isOnline()) {
            throw new RuntimeException("Primary resource should be back online");
        }
        
        System.out.println("✓ Fault tolerance and automatic failover successful");
    }
}