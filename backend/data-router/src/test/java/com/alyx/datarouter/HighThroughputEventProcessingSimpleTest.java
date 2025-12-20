package com.alyx.datarouter;

import com.alyx.datarouter.model.CollisionEventStream;
import com.alyx.datarouter.model.DetectorHitStream;
import com.alyx.datarouter.model.EventProcessingResult;
import com.alyx.datarouter.service.EventProcessingService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Simple test for high-throughput event processing without requiring Spring Boot
 * **Feature: alyx-distributed-orchestrator, Property 11: High-throughput event processing**
 * **Validates: Requirements 4.1, 4.4**
 */
public class HighThroughputEventProcessingSimpleTest {
    
    public static void main(String[] args) {
        System.out.println("Running high-throughput event processing tests...");
        
        try {
            testSingleEventProcessing();
            testBatchEventProcessing();
            testParallelProcessingPerformance();
            testEventProcessingMetrics();
            
            System.out.println("\n🎉 All high-throughput event processing tests passed!");
            System.out.println("Property 11 (High-throughput event processing) is working correctly.");
            
        } catch (Exception e) {
            System.out.println("✗ Test failed with exception: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void testSingleEventProcessing() {
        System.out.println("Test 1: Single event processing");
        
        EventProcessingService service = new EventProcessingService();
        service.resetMetrics();
        
        CollisionEventStream event = createTestEvent(UUID.randomUUID(), Instant.now());
        List<EventProcessingResult> results = service.processEventStream(List.of(event));
        
        if (results.size() == 1 && results.get(0).isSuccessful() && 
            results.get(0).getEventId().equals(event.getEventId())) {
            System.out.println("✓ Single event processing successful");
        } else {
            throw new RuntimeException("Single event processing failed");
        }
    }
    
    private static void testBatchEventProcessing() {
        System.out.println("Test 2: Batch event processing");
        
        EventProcessingService service = new EventProcessingService();
        service.resetMetrics();
        
        // Create batch of events
        List<CollisionEventStream> events = new ArrayList<>();
        Instant baseTime = Instant.now();
        for (int i = 0; i < 10; i++) {
            events.add(createTestEvent(UUID.randomUUID(), baseTime.plusMillis(i * 10)));
        }
        
        List<EventProcessingResult> results = service.processEventStream(events);
        
        if (results.size() == events.size()) {
            boolean allSuccessful = results.stream().allMatch(EventProcessingResult::isSuccessful);
            if (allSuccessful) {
                System.out.println("✓ Batch event processing successful");
            } else {
                throw new RuntimeException("Some events failed processing");
            }
        } else {
            throw new RuntimeException("Batch processing failed - expected: " + events.size() + 
                                     ", got: " + results.size());
        }
    }
    
    private static void testParallelProcessingPerformance() {
        System.out.println("Test 3: Parallel processing performance");
        
        EventProcessingService service = new EventProcessingService();
        service.resetMetrics();
        
        // Create larger batch to test parallel processing
        List<CollisionEventStream> events = new ArrayList<>();
        Instant baseTime = Instant.now();
        for (int i = 0; i < 100; i++) {
            events.add(createTestEvent(UUID.randomUUID(), baseTime.plusMillis(i)));
        }
        
        long startTime = System.currentTimeMillis();
        List<EventProcessingResult> results = service.processEventStream(events);
        long endTime = System.currentTimeMillis();
        
        long processingTimeMs = endTime - startTime;
        double eventsPerSecond = (double) events.size() / (processingTimeMs / 1000.0);
        
        if (results.size() == events.size() && eventsPerSecond > 100) {
            System.out.println("✓ Parallel processing performance: " + String.format("%.1f", eventsPerSecond) + " events/sec");
        } else {
            throw new RuntimeException("Parallel processing performance insufficient: " + 
                                     String.format("%.1f", eventsPerSecond) + " events/sec");
        }
    }
    
    private static void testEventProcessingMetrics() {
        System.out.println("Test 4: Event processing metrics");
        
        EventProcessingService service = new EventProcessingService();
        service.resetMetrics();
        
        List<CollisionEventStream> events = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            events.add(createTestEvent(UUID.randomUUID(), Instant.now().plusMillis(i)));
        }
        
        service.processEventStream(events);
        
        long processedCount = service.getProcessedEventCount();
        double avgProcessingTime = service.getAverageProcessingTimeMs();
        
        if (processedCount == events.size() && avgProcessingTime >= 0) {
            System.out.println("✓ Event processing metrics updated correctly");
            System.out.println("  - Processed events: " + processedCount);
            System.out.println("  - Average processing time: " + String.format("%.2f", avgProcessingTime) + "ms");
        } else {
            throw new RuntimeException("Event processing metrics not updated correctly");
        }
    }
    
    private static CollisionEventStream createTestEvent(UUID eventId, Instant timestamp) {
        CollisionEventStream event = new CollisionEventStream(
            eventId, timestamp, 13000.0, 12345L, 67890L);
        
        // Add detector hits
        List<DetectorHitStream> hits = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            DetectorHitStream hit = new DetectorHitStream(
                UUID.randomUUID(),
                "detector-" + i,
                Math.random() * 10.0, // Energy deposit
                timestamp.plusNanos(i * 1000),
                Math.random() * 100 - 50, // X
                Math.random() * 100 - 50, // Y
                Math.random() * 200 - 100  // Z
            );
            hits.add(hit);
        }
        event.setDetectorHits(hits);
        
        // Add metadata
        event.setMetadata(new HashMap<>());
        event.getMetadata().put("detector_config", "standard");
        
        return event;
    }
}