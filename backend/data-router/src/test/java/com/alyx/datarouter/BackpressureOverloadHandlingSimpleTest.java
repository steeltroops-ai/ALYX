package com.alyx.datarouter;

import com.alyx.datarouter.service.BackpressureService;

/**
 * Simple test for backpressure and overload handling without requiring Spring Boot
 * **Feature: alyx-distributed-orchestrator, Property 13: Backpressure and overload handling**
 * **Validates: Requirements 4.3, 4.5**
 */
public class BackpressureOverloadHandlingSimpleTest {
    
    public static void main(String[] args) {
        System.out.println("Running backpressure and overload handling tests...");
        
        try {
            testNormalLoadConditions();
            testHighQueueSizeBackpressure();
            testHighCpuUtilizationBackpressure();
            testHighMemoryUtilizationBackpressure();
            testBackpressureDelayCalculation();
            testProcessingDelayEstimation();
            testOverloadCounterFunctionality();
            
            System.out.println("\n🎉 All backpressure and overload handling tests passed!");
            System.out.println("Property 13 (Backpressure and overload handling) is working correctly.");
            
        } catch (Exception e) {
            System.out.println("✗ Test failed with exception: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void testNormalLoadConditions() {
        System.out.println("Test 1: Normal load conditions");
        
        BackpressureService service = new BackpressureService();
        service.resetMetrics();
        
        // Set normal load conditions
        service.updateQueueSize(1000); // 10% of max queue size
        service.updateSystemMetrics(0.5, 0.6); // 50% CPU, 60% memory
        
        boolean shouldApplyBackpressure = service.shouldApplyBackpressure();
        long backpressureDelay = service.getBackpressureDelayMs();
        
        if (!shouldApplyBackpressure && backpressureDelay == 0) {
            System.out.println("✓ Normal load conditions handled correctly - no backpressure applied");
        } else {
            throw new RuntimeException("Backpressure incorrectly applied under normal conditions");
        }
    }
    
    private static void testHighQueueSizeBackpressure() {
        System.out.println("Test 2: High queue size backpressure");
        
        BackpressureService service = new BackpressureService();
        service.resetMetrics();
        
        // Set high queue size (90% of max)
        service.updateQueueSize(9000);
        service.updateSystemMetrics(0.5, 0.6); // Normal CPU and memory
        
        boolean shouldApplyBackpressure = service.shouldApplyBackpressure();
        long backpressureDelay = service.getBackpressureDelayMs();
        
        if (shouldApplyBackpressure && backpressureDelay > 0) {
            System.out.println("✓ High queue size backpressure applied correctly - delay: " + backpressureDelay + "ms");
        } else {
            throw new RuntimeException("Backpressure not applied for high queue size");
        }
    }
    
    private static void testHighCpuUtilizationBackpressure() {
        System.out.println("Test 3: High CPU utilization backpressure");
        
        BackpressureService service = new BackpressureService();
        service.resetMetrics();
        
        // Set high CPU utilization
        service.updateQueueSize(1000); // Normal queue size
        service.updateSystemMetrics(0.9, 0.6); // 90% CPU, normal memory
        
        boolean shouldApplyBackpressure = service.shouldApplyBackpressure();
        long backpressureDelay = service.getBackpressureDelayMs();
        
        if (shouldApplyBackpressure && backpressureDelay > 0) {
            System.out.println("✓ High CPU utilization backpressure applied correctly - delay: " + backpressureDelay + "ms");
        } else {
            throw new RuntimeException("Backpressure not applied for high CPU utilization");
        }
    }
    
    private static void testHighMemoryUtilizationBackpressure() {
        System.out.println("Test 4: High memory utilization backpressure");
        
        BackpressureService service = new BackpressureService();
        service.resetMetrics();
        
        // Set high memory utilization
        service.updateQueueSize(1000); // Normal queue size
        service.updateSystemMetrics(0.5, 0.9); // Normal CPU, 90% memory
        
        boolean shouldApplyBackpressure = service.shouldApplyBackpressure();
        long backpressureDelay = service.getBackpressureDelayMs();
        
        if (shouldApplyBackpressure && backpressureDelay > 0) {
            System.out.println("✓ High memory utilization backpressure applied correctly - delay: " + backpressureDelay + "ms");
        } else {
            throw new RuntimeException("Backpressure not applied for high memory utilization");
        }
    }
    
    private static void testBackpressureDelayCalculation() {
        System.out.println("Test 5: Backpressure delay calculation");
        
        BackpressureService service = new BackpressureService();
        service.resetMetrics();
        
        // Test increasing load levels
        service.updateQueueSize(8500); // Moderate overload
        service.updateSystemMetrics(0.85, 0.87);
        long moderateDelay = service.getBackpressureDelayMs();
        
        service.updateQueueSize(12000); // Severe overload
        service.updateSystemMetrics(0.95, 0.95);
        long severeDelay = service.getBackpressureDelayMs();
        
        if (severeDelay >= moderateDelay && moderateDelay > 0) {
            System.out.println("✓ Backpressure delay increases with load severity");
            System.out.println("  - Moderate load delay: " + moderateDelay + "ms");
            System.out.println("  - Severe load delay: " + severeDelay + "ms");
        } else {
            throw new RuntimeException("Backpressure delay calculation incorrect");
        }
    }
    
    private static void testProcessingDelayEstimation() {
        System.out.println("Test 6: Processing delay estimation");
        
        BackpressureService service = new BackpressureService();
        service.resetMetrics();
        
        // Test with overload conditions
        service.updateQueueSize(9000);
        service.updateSystemMetrics(0.9, 0.9);
        
        long estimatedDelay = service.estimateProcessingDelay();
        
        if (estimatedDelay > 0) {
            System.out.println("✓ Processing delay estimation working - estimated: " + estimatedDelay + "ms");
        } else {
            throw new RuntimeException("Processing delay estimation failed");
        }
    }
    
    private static void testOverloadCounterFunctionality() {
        System.out.println("Test 7: Overload counter functionality");
        
        BackpressureService service = new BackpressureService();
        service.resetMetrics();
        
        long initialCount = service.getOverloadCount();
        
        service.incrementOverloadCounter();
        service.incrementOverloadCounter();
        service.incrementOverloadCounter();
        
        long finalCount = service.getOverloadCount();
        
        if (finalCount == initialCount + 3) {
            System.out.println("✓ Overload counter functionality working correctly");
            System.out.println("  - Initial count: " + initialCount);
            System.out.println("  - Final count: " + finalCount);
        } else {
            throw new RuntimeException("Overload counter not working correctly");
        }
    }
}