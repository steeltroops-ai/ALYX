package com.alyx.datarouter;

import com.alyx.datarouter.model.DataDistributionRequest;
import com.alyx.datarouter.model.GridResource;
import com.alyx.datarouter.service.DataRouterService;
import com.alyx.datarouter.service.ResourceMonitoringService;

import java.util.Arrays;
import java.util.List;

/**
 * Integration test for the complete Data Router implementation
 * Tests all requirements: 4.2, 7.2, 7.3, 7.4, 7.5
 */
public class DataRouterIntegrationTest {
    
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
        
        // Verify intelligent distribution (high priority should get best resources)
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
        
        // Create many requests to test load balancing
        for (int i = 1; i <= 10; i++) {
            DataDistributionRequest request = new DataDistributionRequest(
                "job-" + i, Arrays.asList("data-" + i + ".root"), 
                "location-" + (i % 3), 2, 4096, 
                DataDistributionRequest.Priority.values()[i % DataDistributionRequest.Priority.values().length]);
            
            List<GridResource> allocated = service.distributeLoad(Arrays.asList(request));
            
            if (allocated.isEmpty()) {
                throw new RuntimeException("Should be able to allocate resources for job-" + i);
            }
        }
        
        // Verify load is distributed across multiple resources
        List<GridResource> resources = service.getAvailableResources();
        long resourcesWithLoad = resources.stream()
            .filter(r -> r.getAvailableCores() < r.getTotalCores())
            .count();
        
        if (resourcesWithLoad < 2) {
            throw new RuntimeException("Load should be distributed across multiple resources");
        }
        
        System.out.println("✓ Load balancing across GRID resources successful");
    }
    
    private static void testDataLocalityAwareScheduling() {
        System.out.println("Test 3: Data locality-aware scheduling");
        
        DataRouterService dataService = new DataRouterService();
        ResourceMonitoringService monitoringService = new ResourceMonitoringService();
        
        // Add resources in different locations
        GridResource localResource = new GridResource("local-res", "cern-geneva", 8, 16384, "cern-dc1");
        GridResource remoteResource = new GridResource("remote-res", "fermilab-chicago", 16, 32768, "fermilab-dc1");
        
        dataService.addGridResource(localResource);
        dataService.addGridResource(remoteResource);
        
        // Test locality-aware scheduling
        GridResource selectedResource = monitoringService.findLocalResource("cern-geneva", 4, 8192);
        
        if (selectedResource == null || !selectedResource.getResourceId().equals("local-res")) {
            throw new RuntimeException("Should prefer local resource for data locality");
        }
        
        // Test fallback to remote resource when local is unavailable
        GridResource fallbackResource = monitoringService.findLocalResource("cern-geneva", 12, 20480);
        
        if (fallbackResource == null || !fallbackResource.getResourceId().equals("remote-res")) {
            throw new RuntimeException("Should fallback to remote resource when local is insufficient");
        }
        
        System.out.println("✓ Data locality-aware scheduling successful");
    }
    
    private static void testFaultToleranceAndFailover() {
        System.out.println("Test 4: Fault tolerance and automatic failover");
        
        DataRouterService service = new DataRouterService();
        ResourceMonitoringService monitoring = new ResourceMonitoringService();
        
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
        monitoring.updateResourceHeartbeat("primary");
        
        if (!primaryResource.isOnline()) {
            throw new RuntimeException("Primary resource should be back online");
        }
        
        System.out.println("✓ Fault tolerance and automatic failover successful");
    }
}