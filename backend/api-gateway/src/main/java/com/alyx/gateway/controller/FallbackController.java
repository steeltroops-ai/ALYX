package com.alyx.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Fallback controller for circuit breaker patterns
 * 
 * Provides fallback responses when downstream services are unavailable
 * or experiencing issues, ensuring graceful degradation of functionality.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/job-scheduler")
    @PostMapping("/job-scheduler")
    public ResponseEntity<Map<String, Object>> jobSchedulerFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "Job Scheduler service is temporarily unavailable",
                "message", "Please try again later. Your request has been logged.",
                "timestamp", Instant.now().toString(),
                "service", "job-scheduler",
                "fallback", true
            ));
    }

    @GetMapping("/data-router")
    @PostMapping("/data-router")
    public ResponseEntity<Map<String, Object>> dataRouterFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "Data Router service is temporarily unavailable",
                "message", "Data routing operations are currently offline. Please try again later.",
                "timestamp", Instant.now().toString(),
                "service", "data-router",
                "fallback", true
            ));
    }

    @GetMapping("/resource-optimizer")
    @PostMapping("/resource-optimizer")
    public ResponseEntity<Map<String, Object>> resourceOptimizerFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "Resource Optimizer service is temporarily unavailable",
                "message", "Resource optimization is currently offline. Jobs will use default allocation.",
                "timestamp", Instant.now().toString(),
                "service", "resource-optimizer",
                "fallback", true
            ));
    }

    @GetMapping("/collaboration")
    @PostMapping("/collaboration")
    public ResponseEntity<Map<String, Object>> collaborationFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "Collaboration service is temporarily unavailable",
                "message", "Real-time collaboration features are currently offline.",
                "timestamp", Instant.now().toString(),
                "service", "collaboration-service",
                "fallback", true
            ));
    }

    @GetMapping("/notebook")
    @PostMapping("/notebook")
    public ResponseEntity<Map<String, Object>> notebookFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "Notebook service is temporarily unavailable",
                "message", "Notebook operations are currently offline. Please try again later.",
                "timestamp", Instant.now().toString(),
                "service", "notebook-service",
                "fallback", true
            ));
    }

    @GetMapping("/result-aggregator")
    @PostMapping("/result-aggregator")
    public ResponseEntity<Map<String, Object>> resultAggregatorFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "Result Aggregator service is temporarily unavailable",
                "message", "Result aggregation is currently offline. Results may be incomplete.",
                "timestamp", Instant.now().toString(),
                "service", "result-aggregator",
                "fallback", true
            ));
    }

    @GetMapping("/quality-monitor")
    @PostMapping("/quality-monitor")
    public ResponseEntity<Map<String, Object>> qualityMonitorFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "Quality Monitor service is temporarily unavailable",
                "message", "Quality monitoring is currently offline.",
                "timestamp", Instant.now().toString(),
                "service", "quality-monitor",
                "fallback", true
            ));
    }
}