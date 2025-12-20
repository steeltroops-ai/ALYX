package com.alyx.datarouter.service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for managing backpressure and overload handling in the event processing pipeline.
 * Implements mechanisms to prevent data loss during high load conditions.
 */
public class BackpressureService {
    
    private static final long MAX_QUEUE_SIZE = 10000;
    private static final double CPU_THRESHOLD = 0.8;
    private static final double MEMORY_THRESHOLD = 0.85;
    
    private final AtomicLong overloadCounter = new AtomicLong(0);
    private final AtomicLong queueSize = new AtomicLong(0);
    private volatile double currentCpuUtilization = 0.0;
    private volatile double currentMemoryUtilization = 0.0;
    
    /**
     * Determines if backpressure should be applied based on system conditions.
     * Property 13: For any processing bottleneck or system overload condition, 
     * the system should implement backpressure mechanisms to prevent data loss
     */
    public boolean shouldApplyBackpressure() {
        return queueSize.get() > MAX_QUEUE_SIZE * 0.8 || 
               currentCpuUtilization > CPU_THRESHOLD ||
               currentMemoryUtilization > MEMORY_THRESHOLD;
    }
    
    /**
     * Calculates appropriate backpressure delay based on current system load
     */
    public long getBackpressureDelayMs() {
        if (!shouldApplyBackpressure()) {
            return 0;
        }
        
        // Calculate delay based on severity of overload
        double queuePressure = Math.min(1.0, queueSize.get() / (double) MAX_QUEUE_SIZE);
        double cpuPressure = Math.max(0.0, (currentCpuUtilization - CPU_THRESHOLD) / (1.0 - CPU_THRESHOLD));
        double memoryPressure = Math.max(0.0, (currentMemoryUtilization - MEMORY_THRESHOLD) / (1.0 - MEMORY_THRESHOLD));
        
        double maxPressure = Math.max(Math.max(queuePressure, cpuPressure), memoryPressure);
        
        // Exponential backoff: 1ms to 100ms based on pressure
        return Math.round(Math.pow(100, maxPressure));
    }
    
    /**
     * Handles system overload by queuing excess work and providing estimated delays
     */
    public long estimateProcessingDelay() {
        if (!shouldApplyBackpressure()) {
            return 0;
        }
        
        // Estimate delay based on current queue size and processing rate
        long currentQueue = queueSize.get();
        double processingRate = getCurrentProcessingRate();
        
        if (processingRate > 0) {
            return Math.round(currentQueue / processingRate * 1000); // Convert to milliseconds
        }
        
        return 5000; // Default 5-second estimate if rate unknown
    }
    
    private double getCurrentProcessingRate() {
        // Simplified processing rate calculation
        // In real implementation, this would track actual processing metrics
        return Math.max(1.0, 1000.0 - (overloadCounter.get() * 10));
    }
    
    public void incrementOverloadCounter() {
        overloadCounter.incrementAndGet();
    }
    
    public void updateQueueSize(long size) {
        queueSize.set(size);
    }
    
    public void updateSystemMetrics(double cpuUtilization, double memoryUtilization) {
        this.currentCpuUtilization = cpuUtilization;
        this.currentMemoryUtilization = memoryUtilization;
    }
    
    public long getOverloadCount() {
        return overloadCounter.get();
    }
    
    public double getCurrentCpuUtilization() {
        return currentCpuUtilization;
    }
    
    public double getCurrentMemoryUtilization() {
        return currentMemoryUtilization;
    }
    
    public void resetMetrics() {
        overloadCounter.set(0);
        queueSize.set(0);
        currentCpuUtilization = 0.0;
        currentMemoryUtilization = 0.0;
    }
}