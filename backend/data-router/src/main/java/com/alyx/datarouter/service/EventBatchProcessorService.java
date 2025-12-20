package com.alyx.datarouter.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled service for batch processing of events from the queue.
 * Implements parallel event reconstruction algorithms.
 */
@Service
public class EventBatchProcessorService {
    
    @Autowired
    private EventConsumerService eventConsumerService;
    
    @Autowired
    private BackpressureService backpressureService;
    
    /**
     * Processes batches of events every 100ms for high throughput
     */
    @Scheduled(fixedDelay = 100)
    public void processBatch() {
        try {
            // Update queue size for backpressure monitoring
            backpressureService.updateQueueSize(eventConsumerService.getQueueSize());
            
            // Process batch if not under severe backpressure
            if (!backpressureService.shouldApplyBackpressure() || 
                backpressureService.getBackpressureDelayMs() < 50) {
                eventConsumerService.processBatch();
            }
            
            // Update system metrics (simplified - in real implementation would get from system)
            updateSystemMetrics();
            
        } catch (Exception e) {
            System.err.println("Error in batch processing: " + e.getMessage());
        }
    }
    
    private void updateSystemMetrics() {
        // Simplified system metrics - in real implementation would use JMX or system monitoring
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        
        double memoryUtilization = (double) (totalMemory - freeMemory) / maxMemory;
        double cpuUtilization = Math.random() * 0.3 + 0.2; // Simulated CPU usage 20-50%
        
        backpressureService.updateSystemMetrics(cpuUtilization, memoryUtilization);
    }
}