package com.alyx.datarouter;

import com.alyx.datarouter.service.BackpressureService;
import net.java.quickcheck.Generator;
import net.java.quickcheck.QuickCheck;
import net.java.quickcheck.characteristic.AbstractCharacteristic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static net.java.quickcheck.generator.PrimitiveGenerators.*;

/**
 * **Feature: alyx-distributed-orchestrator, Property 13: Backpressure and overload handling**
 * **Validates: Requirements 4.3, 4.5**
 */
public class BackpressureOverloadHandlingPropertyTest {
    
    private BackpressureService backpressureService;
    
    @BeforeEach
    public void setUp() {
        backpressureService = new BackpressureService();
        backpressureService.resetMetrics();
    }
    
    @Test
    public void testBackpressureAndOverloadHandling() {
        // **Feature: alyx-distributed-orchestrator, Property 13: Backpressure and overload handling**
        QuickCheck.forAll(systemLoadGenerator(),
            new AbstractCharacteristic<SystemLoad>() {
                @Override
                protected void doSpecify(SystemLoad systemLoad) throws Throwable {
                    // Property: For any processing bottleneck or system overload condition, 
                    // the system should implement backpressure mechanisms to prevent data loss 
                    // and queue excess work with estimated processing delays
                    
                    // Setup system conditions
                    backpressureService.updateQueueSize(systemLoad.queueSize);
                    backpressureService.updateSystemMetrics(systemLoad.cpuUtilization, systemLoad.memoryUtilization);
                    
                    // Test backpressure decision
                    boolean shouldApplyBackpressure = backpressureService.shouldApplyBackpressure();
                    long backpressureDelay = backpressureService.getBackpressureDelayMs();
                    long estimatedDelay = backpressureService.estimateProcessingDelay();
                    
                    // Verify backpressure is applied when system is overloaded
                    boolean isOverloaded = systemLoad.queueSize > 8000 || // 80% of max queue size
                                         systemLoad.cpuUtilization > 0.8 ||
                                         systemLoad.memoryUtilization > 0.85;
                    
                    if (isOverloaded) {
                        assert shouldApplyBackpressure : 
                            "Backpressure should be applied when system is overloaded. " +
                            "Queue: " + systemLoad.queueSize + ", CPU: " + systemLoad.cpuUtilization + 
                            ", Memory: " + systemLoad.memoryUtilization;
                        
                        assert backpressureDelay > 0 : 
                            "Backpressure delay should be positive when overloaded: " + backpressureDelay;
                        
                        assert estimatedDelay >= 0 : 
                            "Estimated processing delay should be non-negative: " + estimatedDelay;
                        
                        // Verify delay is reasonable (not excessive)
                        assert backpressureDelay <= 100 : 
                            "Backpressure delay should not be excessive: " + backpressureDelay + "ms";
                    } else {
                        // When not overloaded, backpressure should not be applied or should be minimal
                        if (shouldApplyBackpressure) {
                            assert backpressureDelay <= 10 : 
                                "Backpressure delay should be minimal when not severely overloaded: " + backpressureDelay;
                        } else {
                            assert backpressureDelay == 0 : 
                                "No backpressure delay when system is not overloaded: " + backpressureDelay;
                        }
                    }
                    
                    // Verify backpressure delay increases with load severity
                    if (shouldApplyBackpressure) {
                        // Test with higher load
                        SystemLoad higherLoad = new SystemLoad(
                            Math.min(15000, systemLoad.queueSize + 2000),
                            Math.min(1.0, systemLoad.cpuUtilization + 0.1),
                            Math.min(1.0, systemLoad.memoryUtilization + 0.1)
                        );
                        
                        backpressureService.updateQueueSize(higherLoad.queueSize);
                        backpressureService.updateSystemMetrics(higherLoad.cpuUtilization, higherLoad.memoryUtilization);
                        
                        long higherLoadDelay = backpressureService.getBackpressureDelayMs();
                        
                        // Higher load should result in higher or equal delay
                        assert higherLoadDelay >= backpressureDelay : 
                            "Higher system load should result in higher backpressure delay. " +
                            "Original: " + backpressureDelay + "ms, Higher load: " + higherLoadDelay + "ms";
                    }
                    
                    // Verify overload counter functionality
                    long initialOverloadCount = backpressureService.getOverloadCount();
                    backpressureService.incrementOverloadCounter();
                    long afterIncrementCount = backpressureService.getOverloadCount();
                    
                    assert afterIncrementCount == initialOverloadCount + 1 : 
                        "Overload counter should increment correctly";
                    
                    // Verify system metrics are properly stored
                    assert Math.abs(backpressureService.getCurrentCpuUtilization() - systemLoad.cpuUtilization) < 0.001 : 
                        "CPU utilization should be stored correctly";
                    assert Math.abs(backpressureService.getCurrentMemoryUtilization() - systemLoad.memoryUtilization) < 0.001 : 
                        "Memory utilization should be stored correctly";
                }
            });
    }
    
    private static Generator<SystemLoad> systemLoadGenerator() {
        return new Generator<SystemLoad>() {
            @Override
            public SystemLoad next() {
                return new SystemLoad(
                    longs(0L, 15000L).next(), // Queue size: 0 to 15000 (max is 10000)
                    doubles(0.0, 1.0).next(), // CPU utilization: 0% to 100%
                    doubles(0.0, 1.0).next()  // Memory utilization: 0% to 100%
                );
            }
        };
    }
    
    static class SystemLoad {
        final long queueSize;
        final double cpuUtilization;
        final double memoryUtilization;
        
        SystemLoad(long queueSize, double cpuUtilization, double memoryUtilization) {
            this.queueSize = queueSize;
            this.cpuUtilization = cpuUtilization;
            this.memoryUtilization = memoryUtilization;
        }
        
        @Override
        public String toString() {
            return String.format("SystemLoad{queue=%d, cpu=%.2f, memory=%.2f}", 
                               queueSize, cpuUtilization, memoryUtilization);
        }
    }
}