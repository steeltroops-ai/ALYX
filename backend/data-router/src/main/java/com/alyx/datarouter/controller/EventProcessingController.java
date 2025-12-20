package com.alyx.datarouter.controller;

import com.alyx.datarouter.model.CollisionEventStream;
import com.alyx.datarouter.model.EventProcessingResult;
import com.alyx.datarouter.service.EventProcessingService;
import com.alyx.datarouter.service.EventProducerService;
import com.alyx.datarouter.service.BackpressureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for event processing pipeline operations.
 * Provides endpoints for event ingestion, processing status, and throughput monitoring.
 */
@RestController
@RequestMapping("/api/events")
public class EventProcessingController {
    
    @Autowired
    private EventProcessingService eventProcessingService;
    
    @Autowired
    private EventProducerService eventProducerService;
    
    @Autowired
    private BackpressureService backpressureService;
    
    /**
     * Publishes collision events to Kafka for processing
     */
    @PostMapping("/publish")
    public ResponseEntity<Map<String, Object>> publishEvents(@RequestBody List<CollisionEventStream> events) {
        try {
            eventProducerService.publishEventBatch(events);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("eventsPublished", events.size());
            response.put("totalPublished", eventProducerService.getPublishedEventCount());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Processes events directly (for testing/debugging)
     */
    @PostMapping("/process")
    public ResponseEntity<List<EventProcessingResult>> processEvents(@RequestBody List<CollisionEventStream> events) {
        try {
            List<EventProcessingResult> results = eventProcessingService.processEventStream(events);
            return ResponseEntity.ok(results);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Gets current throughput metrics
     */
    @GetMapping("/metrics/throughput")
    public ResponseEntity<Map<String, Object>> getThroughputMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("currentThroughput", eventProcessingService.getCurrentThroughput());
        metrics.put("averageThroughput5s", eventProcessingService.getAverageThroughput(5));
        metrics.put("averageThroughput30s", eventProcessingService.getAverageThroughput(30));
        metrics.put("processedEventCount", eventProcessingService.getProcessedEventCount());
        metrics.put("averageProcessingTimeMs", eventProcessingService.getAverageProcessingTimeMs());
        
        return ResponseEntity.ok(metrics);
    }
    
    /**
     * Gets current backpressure status
     */
    @GetMapping("/metrics/backpressure")
    public ResponseEntity<Map<String, Object>> getBackpressureMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("shouldApplyBackpressure", backpressureService.shouldApplyBackpressure());
        metrics.put("backpressureDelayMs", backpressureService.getBackpressureDelayMs());
        metrics.put("estimatedProcessingDelayMs", backpressureService.estimateProcessingDelay());
        metrics.put("overloadCount", backpressureService.getOverloadCount());
        metrics.put("currentCpuUtilization", backpressureService.getCurrentCpuUtilization());
        metrics.put("currentMemoryUtilization", backpressureService.getCurrentMemoryUtilization());
        
        return ResponseEntity.ok(metrics);
    }
    
    /**
     * Resets all metrics (for testing)
     */
    @PostMapping("/metrics/reset")
    public ResponseEntity<Map<String, String>> resetMetrics() {
        eventProcessingService.resetMetrics();
        eventProducerService.resetMetrics();
        backpressureService.resetMetrics();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "All metrics reset");
        
        return ResponseEntity.ok(response);
    }
}