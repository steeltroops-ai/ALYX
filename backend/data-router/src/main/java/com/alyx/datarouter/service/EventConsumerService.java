package com.alyx.datarouter.service;

import com.alyx.datarouter.model.CollisionEventStream;
import com.alyx.datarouter.model.EventProcessingResult;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Event consumer service for collision event processing.
 * Implements backpressure mechanisms and distributed processing.
 */
public class EventConsumerService {
    
    private EventProcessingService eventProcessingService;
    private BackpressureService backpressureService;
    
    private final AtomicLong consumedEventCount = new AtomicLong(0);
    private final BlockingQueue<CollisionEventStream> eventQueue = new LinkedBlockingQueue<>(10000);
    
    public EventConsumerService(EventProcessingService eventProcessingService, BackpressureService backpressureService) {
        this.eventProcessingService = eventProcessingService;
        this.backpressureService = backpressureService;
    }
    
    public EventConsumerService() {
        this.eventProcessingService = new EventProcessingService();
        this.backpressureService = new BackpressureService();
    }
    
    /**
     * Simulates consuming collision events with backpressure handling
     */
    public void consumeEvent(CollisionEventStream event) {
        try {
            // Check backpressure before processing
            if (backpressureService.shouldApplyBackpressure()) {
                // Apply backpressure - delay processing
                Thread.sleep(backpressureService.getBackpressureDelayMs());
            }
            
            // Add to processing queue
            boolean added = eventQueue.offer(event);
            if (!added) {
                // Queue is full, apply backpressure
                backpressureService.incrementOverloadCounter();
                // Process immediately if queue is full
                processEventImmediately(event);
            }
            
            consumedEventCount.incrementAndGet();
            
        } catch (Exception e) {
            System.err.println("Error processing event: " + e.getMessage());
        }
    }
    
    private void processEventImmediately(CollisionEventStream event) {
        List<EventProcessingResult> results = eventProcessingService.processEventStream(List.of(event));
        // Handle results as needed
    }
    
    /**
     * Batch processes events from the queue
     */
    public void processBatch() {
        List<CollisionEventStream> batch = new java.util.ArrayList<>();
        eventQueue.drainTo(batch, 100); // Process up to 100 events at once
        
        if (!batch.isEmpty()) {
            eventProcessingService.processEventStream(batch);
        }
    }
    
    public long getConsumedEventCount() {
        return consumedEventCount.get();
    }
    
    public int getQueueSize() {
        return eventQueue.size();
    }
    
    public void resetMetrics() {
        consumedEventCount.set(0);
        eventQueue.clear();
    }
}