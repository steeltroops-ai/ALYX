package com.alyx.datarouter.service;

import com.alyx.datarouter.model.CollisionEventStream;
import com.alyx.datarouter.model.EventProcessingResult;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Service for processing collision events at high throughput.
 * Implements parallel event reconstruction algorithms and throughput monitoring.
 */
public class EventProcessingService {
    
    private final AtomicLong processedEventCount = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);
    private final ConcurrentHashMap<String, Long> throughputMetrics = new ConcurrentHashMap<>();
    
    /**
     * Processes collision events in streaming mode maintaining high throughput.
     * Property 11: For any collision event stream in normal operating conditions, 
     * the system should maintain throughput of at least 50,000 events per second using parallel processing
     */
    public List<EventProcessingResult> processEventStream(List<CollisionEventStream> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        
        Instant batchStartTime = Instant.now();
        
        // Process events in parallel for high throughput
        List<CompletableFuture<EventProcessingResult>> futures = events.parallelStream()
            .map(this::processEventAsync)
            .collect(Collectors.toList());
        
        // Wait for all events to complete processing
        List<EventProcessingResult> results = futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());
        
        // Update throughput metrics
        Instant batchEndTime = Instant.now();
        long batchProcessingTimeMs = batchEndTime.toEpochMilli() - batchStartTime.toEpochMilli();
        
        updateThroughputMetrics(events.size(), batchProcessingTimeMs);
        
        return results;
    }
    
    private CompletableFuture<EventProcessingResult> processEventAsync(CollisionEventStream event) {
        return CompletableFuture.supplyAsync(() -> {
            Instant startTime = Instant.now();
            
            try {
                // Simulate event reconstruction processing
                // In real implementation, this would involve complex physics algorithms
                Thread.sleep(1); // Minimal processing time to simulate work
                
                Instant endTime = Instant.now();
                
                EventProcessingResult result = new EventProcessingResult(
                    event.getEventId(), startTime, endTime, true);
                
                // Simulate track reconstruction count
                int trackCount = event.getDetectorHits() != null ? 
                    Math.min(event.getDetectorHits().size() / 3, 10) : 0;
                result.setReconstructedTracks(trackCount);
                
                processedEventCount.incrementAndGet();
                totalProcessingTimeMs.addAndGet(result.getProcessingTimeMs());
                
                return result;
                
            } catch (Exception e) {
                Instant endTime = Instant.now();
                EventProcessingResult result = new EventProcessingResult(
                    event.getEventId(), startTime, endTime, false);
                result.setErrorMessage(e.getMessage());
                return result;
            }
        });
    }
    
    private void updateThroughputMetrics(int eventCount, long processingTimeMs) {
        String timeWindow = String.valueOf(System.currentTimeMillis() / 1000); // 1-second windows
        throughputMetrics.merge(timeWindow, (long) eventCount, Long::sum);
        
        // Clean up old metrics (keep only last 60 seconds)
        long currentTime = System.currentTimeMillis() / 1000;
        throughputMetrics.entrySet().removeIf(entry -> 
            currentTime - Long.parseLong(entry.getKey()) > 60);
    }
    
    /**
     * Gets current throughput in events per second
     */
    public double getCurrentThroughput() {
        long currentTime = System.currentTimeMillis() / 1000;
        return throughputMetrics.entrySet().stream()
            .filter(entry -> currentTime - Long.parseLong(entry.getKey()) <= 1)
            .mapToLong(entry -> entry.getValue())
            .sum();
    }
    
    /**
     * Gets average throughput over the last N seconds
     */
    public double getAverageThroughput(int seconds) {
        long currentTime = System.currentTimeMillis() / 1000;
        long totalEvents = throughputMetrics.entrySet().stream()
            .filter(entry -> currentTime - Long.parseLong(entry.getKey()) <= seconds)
            .mapToLong(entry -> entry.getValue())
            .sum();
        
        return seconds > 0 ? (double) totalEvents / seconds : 0.0;
    }
    
    public long getProcessedEventCount() {
        return processedEventCount.get();
    }
    
    public double getAverageProcessingTimeMs() {
        long count = processedEventCount.get();
        return count > 0 ? (double) totalProcessingTimeMs.get() / count : 0.0;
    }
    
    public void resetMetrics() {
        processedEventCount.set(0);
        totalProcessingTimeMs.set(0);
        throughputMetrics.clear();
    }
}