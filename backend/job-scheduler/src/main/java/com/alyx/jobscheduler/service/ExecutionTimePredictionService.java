package com.alyx.jobscheduler.service;

import com.alyx.jobscheduler.model.JobParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * ML-based service for predicting job execution times
 * This is a simplified implementation that would be replaced with actual ML models in production
 */
@Service
public class ExecutionTimePredictionService {
    
    // Base processing time per event in milliseconds
    private static final long BASE_TIME_PER_EVENT_MS = 100L;
    
    // High priority jobs get more resources, so they complete faster
    private static final double HIGH_PRIORITY_SPEEDUP = 0.7;
    
    // Energy threshold affects complexity - higher thresholds require more processing
    private static final double ENERGY_COMPLEXITY_FACTOR = 0.1;

    /**
     * Predicts the execution time for a job based on its parameters
     * In a real implementation, this would use trained ML models
     * 
     * @param parameters The job parameters
     * @return Estimated completion time
     */
    public Instant predictExecutionTime(JobParameters parameters) {
        // Base calculation: events * base time per event
        long baseTimeMs = parameters.getExpectedEvents() * BASE_TIME_PER_EVENT_MS;
        
        // Adjust for energy threshold complexity
        double energyFactor = 1.0 + (parameters.getEnergyThreshold() * ENERGY_COMPLEXITY_FACTOR);
        long adjustedTimeMs = (long) (baseTimeMs * energyFactor);
        
        // High priority jobs get better resource allocation
        if (parameters.isHighPriority()) {
            adjustedTimeMs = (long) (adjustedTimeMs * HIGH_PRIORITY_SPEEDUP);
        }
        
        // Add some variability based on current system load (simplified)
        double loadFactor = getCurrentSystemLoadFactor();
        adjustedTimeMs = (long) (adjustedTimeMs * loadFactor);
        
        return Instant.now().plus(Duration.ofMillis(adjustedTimeMs));
    }
    
    /**
     * Estimates resource requirements for a job
     * 
     * @param parameters The job parameters
     * @return Estimated number of CPU cores needed
     */
    public int estimateRequiredCores(JobParameters parameters) {
        // Base cores calculation
        int baseCores = Math.max(1, parameters.getExpectedEvents() / 10000); // 1 core per 10k events
        
        // High priority jobs can use more cores
        if (parameters.isHighPriority()) {
            baseCores = Math.min(baseCores * 2, 16); // Cap at 16 cores
        }
        
        return Math.min(baseCores, 8); // Default cap at 8 cores
    }
    
    /**
     * Estimates memory requirements for a job
     * 
     * @param parameters The job parameters
     * @return Estimated memory in MB
     */
    public long estimateRequiredMemoryMB(JobParameters parameters) {
        // Base memory: 100MB + 10MB per 1000 events
        long baseMemoryMB = 100L + (parameters.getExpectedEvents() / 1000L) * 10L;
        
        // Energy threshold affects memory usage for complex calculations
        double energyFactor = 1.0 + (parameters.getEnergyThreshold() / 100.0);
        long adjustedMemoryMB = (long) (baseMemoryMB * energyFactor);
        
        return Math.max(adjustedMemoryMB, 512L); // Minimum 512MB
    }
    
    /**
     * Simulates current system load factor
     * In a real implementation, this would query actual system metrics
     * 
     * @return Load factor (1.0 = normal load, >1.0 = higher load)
     */
    private double getCurrentSystemLoadFactor() {
        // Simulate varying system load between 0.8 and 1.5
        return 0.8 + (Math.random() * 0.7);
    }
    
    /**
     * Updates the prediction model with actual execution data
     * This would be used for continuous learning in a real ML system
     * 
     * @param parameters The job parameters
     * @param actualExecutionTimeMs The actual execution time
     */
    public void updateModelWithActualData(JobParameters parameters, long actualExecutionTimeMs) {
        // In a real implementation, this would update the ML model
        // For now, we just log the data for future model training
        System.out.println("Model update: " + parameters.getJobName() + 
                          " took " + actualExecutionTimeMs + "ms");
    }
}