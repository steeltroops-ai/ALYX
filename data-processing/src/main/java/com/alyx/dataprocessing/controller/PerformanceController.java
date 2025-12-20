package com.alyx.dataprocessing.controller;

import com.alyx.dataprocessing.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

/**
 * REST controller for performance monitoring and optimization endpoints.
 * Provides access to caching metrics, database optimization, and connection pool monitoring.
 */
@RestController
@RequestMapping("/api/performance")
public class PerformanceController {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceController.class);

    private final PerformanceMonitoringService performanceMonitoringService;
    private final DataCacheService dataCacheService;
    private final CacheInvalidationService cacheInvalidationService;
    private final DatabaseOptimizationService databaseOptimizationService;
    private final ConnectionPoolMonitoringService connectionPoolMonitoringService;
    private final MaterializedViewRefreshService materializedViewRefreshService;

    @Autowired
    public PerformanceController(PerformanceMonitoringService performanceMonitoringService,
                               DataCacheService dataCacheService,
                               CacheInvalidationService cacheInvalidationService,
                               DatabaseOptimizationService databaseOptimizationService,
                               ConnectionPoolMonitoringService connectionPoolMonitoringService,
                               MaterializedViewRefreshService materializedViewRefreshService) {
        this.performanceMonitoringService = performanceMonitoringService;
        this.dataCacheService = dataCacheService;
        this.cacheInvalidationService = cacheInvalidationService;
        this.databaseOptimizationService = databaseOptimizationService;
        this.connectionPoolMonitoringService = connectionPoolMonitoringService;
        this.materializedViewRefreshService = materializedViewRefreshService;
    }

    /**
     * Get comprehensive performance report
     */
    @GetMapping("/report")
    public ResponseEntity<Map<String, Object>> getPerformanceReport() {
        try {
            Map<String, Object> report = performanceMonitoringService.getPerformanceReport();
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            logger.error("Failed to get performance report: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to generate performance report"));
        }
    }

    /**
     * Get cache statistics and metrics
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, Object>> getCacheStatistics() {
        try {
            Map<String, Object> stats = dataCacheService.getCacheStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Failed to get cache statistics: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get cache statistics"));
        }
    }

    /**
     * Get cache invalidation statistics
     */
    @GetMapping("/cache/invalidation/stats")
    public ResponseEntity<Map<String, Object>> getCacheInvalidationStats() {
        try {
            Map<String, Object> stats = cacheInvalidationService.getInvalidationStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Failed to get cache invalidation statistics: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get cache invalidation statistics"));
        }
    }

    /**
     * Invalidate specific cache type
     */
    @PostMapping("/cache/invalidate/{cacheType}")
    public ResponseEntity<Map<String, Object>> invalidateCache(@PathVariable String cacheType) {
        try {
            switch (cacheType.toLowerCase()) {
                case "collision_events":
                    cacheInvalidationService.invalidateByFreshness("collision_events", Duration.ofHours(1));
                    break;
                case "detector_statistics":
                    cacheInvalidationService.invalidateByFreshness("detector_statistics", Duration.ofMinutes(30));
                    break;
                case "materialized_views":
                    cacheInvalidationService.invalidateByFreshness("materialized_views", Duration.ofHours(2));
                    break;
                case "all":
                    cacheInvalidationService.emergencyCacheClear();
                    break;
                default:
                    return ResponseEntity.badRequest()
                        .body(Map.of("error", "Unknown cache type: " + cacheType));
            }
            
            return ResponseEntity.ok(Map.of(
                "message", "Cache invalidation completed for: " + cacheType,
                "timestamp", java.time.Instant.now()
            ));
        } catch (Exception e) {
            logger.error("Failed to invalidate cache {}: {}", cacheType, e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to invalidate cache"));
        }
    }

    /**
     * Warm cache for specific type
     */
    @PostMapping("/cache/warm/{cacheType}")
    public ResponseEntity<Map<String, Object>> warmCache(@PathVariable String cacheType) {
        try {
            cacheInvalidationService.warmCacheAfterInvalidation(cacheType);
            return ResponseEntity.ok(Map.of(
                "message", "Cache warming completed for: " + cacheType,
                "timestamp", java.time.Instant.now()
            ));
        } catch (Exception e) {
            logger.error("Failed to warm cache {}: {}", cacheType, e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to warm cache"));
        }
    }

    /**
     * Get database optimization report
     */
    @GetMapping("/database/optimization")
    public ResponseEntity<Map<String, Object>> getDatabaseOptimizationReport() {
        try {
            Map<String, Object> report = databaseOptimizationService.getOptimizationReport();
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            logger.error("Failed to get database optimization report: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to generate optimization report"));
        }
    }

    /**
     * Trigger database optimization analysis
     */
    @PostMapping("/database/analyze")
    public ResponseEntity<Map<String, Object>> analyzeDatabasePerformance() {
        try {
            databaseOptimizationService.performOptimizationAnalysis();
            return ResponseEntity.ok(Map.of(
                "message", "Database optimization analysis completed",
                "timestamp", java.time.Instant.now()
            ));
        } catch (Exception e) {
            logger.error("Failed to analyze database performance: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to analyze database performance"));
        }
    }

    /**
     * Execute specific optimization recommendation
     */
    @PostMapping("/database/optimize/{recommendationKey}")
    public ResponseEntity<Map<String, Object>> executeOptimization(
            @PathVariable String recommendationKey,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        try {
            databaseOptimizationService.executeOptimization(recommendationKey, dryRun);
            return ResponseEntity.ok(Map.of(
                "message", dryRun ? "Dry run completed" : "Optimization executed",
                "recommendation", recommendationKey,
                "dry_run", dryRun,
                "timestamp", java.time.Instant.now()
            ));
        } catch (Exception e) {
            logger.error("Failed to execute optimization {}: {}", recommendationKey, e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to execute optimization"));
        }
    }

    /**
     * Get connection pool health status
     */
    @GetMapping("/connection-pool/health")
    public ResponseEntity<Map<String, Object>> getConnectionPoolHealth() {
        try {
            Map<String, Object> health = connectionPoolMonitoringService.getConnectionPoolHealth();
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            logger.error("Failed to get connection pool health: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get connection pool health"));
        }
    }

    /**
     * Get detailed connection pool metrics
     */
    @GetMapping("/connection-pool/metrics")
    public ResponseEntity<Map<String, Object>> getConnectionPoolMetrics() {
        try {
            Map<String, Object> metrics = connectionPoolMonitoringService.getDetailedMetrics();
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            logger.error("Failed to get connection pool metrics: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get connection pool metrics"));
        }
    }

    /**
     * Get connection pool statistics for a specific period
     */
    @GetMapping("/connection-pool/statistics")
    public ResponseEntity<Map<String, Object>> getConnectionPoolStatistics(
            @RequestParam(defaultValue = "24") int hours) {
        try {
            Duration period = Duration.ofHours(hours);
            Map<String, Object> stats = connectionPoolMonitoringService.getConnectionPoolStatistics(period);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Failed to get connection pool statistics: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get connection pool statistics"));
        }
    }

    /**
     * Force connection pool analysis
     */
    @PostMapping("/connection-pool/analyze")
    public ResponseEntity<Map<String, Object>> analyzeConnectionPool() {
        try {
            connectionPoolMonitoringService.analyzeConnectionPool();
            return ResponseEntity.ok(Map.of(
                "message", "Connection pool analysis completed",
                "timestamp", java.time.Instant.now()
            ));
        } catch (Exception e) {
            logger.error("Failed to analyze connection pool: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to analyze connection pool"));
        }
    }

    /**
     * Get materialized view refresh status
     */
    @GetMapping("/materialized-views/status")
    public ResponseEntity<Map<String, Object>> getMaterializedViewStatus() {
        try {
            Map<String, Object> status = materializedViewRefreshService.getRefreshStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.error("Failed to get materialized view status: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get materialized view status"));
        }
    }

    /**
     * Refresh specific materialized view
     */
    @PostMapping("/materialized-views/refresh/{viewName}")
    public ResponseEntity<Map<String, Object>> refreshMaterializedView(@PathVariable String viewName) {
        try {
            boolean success = materializedViewRefreshService.refreshMaterializedView(viewName);
            return ResponseEntity.ok(Map.of(
                "success", success,
                "view_name", viewName,
                "message", success ? "View refreshed successfully" : "View refresh failed",
                "timestamp", java.time.Instant.now()
            ));
        } catch (Exception e) {
            logger.error("Failed to refresh materialized view {}: {}", viewName, e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to refresh materialized view"));
        }
    }

    /**
     * Refresh all materialized views
     */
    @PostMapping("/materialized-views/refresh-all")
    public ResponseEntity<Map<String, Object>> refreshAllMaterializedViews() {
        try {
            materializedViewRefreshService.refreshAllMaterializedViews();
            return ResponseEntity.ok(Map.of(
                "message", "All materialized views refresh initiated",
                "timestamp", java.time.Instant.now()
            ));
        } catch (Exception e) {
            logger.error("Failed to refresh all materialized views: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to refresh all materialized views"));
        }
    }

    /**
     * Get materialized view performance analysis
     */
    @GetMapping("/materialized-views/analysis")
    public ResponseEntity<Map<String, Object>> getMaterializedViewAnalysis() {
        try {
            Map<String, Object> analysis = materializedViewRefreshService.analyzeMaterializedViewPerformance();
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            logger.error("Failed to get materialized view analysis: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get materialized view analysis"));
        }
    }

    /**
     * Force emergency refresh of all views and caches
     */
    @PostMapping("/emergency-refresh")
    public ResponseEntity<Map<String, Object>> emergencyRefresh() {
        try {
            logger.warn("Emergency refresh initiated via API");
            
            // Emergency refresh of materialized views
            materializedViewRefreshService.emergencyRefreshAll();
            
            // Clear and warm caches
            cacheInvalidationService.emergencyCacheClear();
            cacheInvalidationService.warmCacheAfterInvalidation("collision_events");
            cacheInvalidationService.warmCacheAfterInvalidation("event_aggregations");
            
            return ResponseEntity.ok(Map.of(
                "message", "Emergency refresh completed",
                "timestamp", java.time.Instant.now(),
                "warning", "This operation may impact system performance temporarily"
            ));
        } catch (Exception e) {
            logger.error("Emergency refresh failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Emergency refresh failed"));
        }
    }

    /**
     * Get system health summary
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        try {
            Map<String, Object> health = Map.of(
                "performance", performanceMonitoringService.getPerformanceReport(),
                "connection_pool", connectionPoolMonitoringService.getConnectionPoolHealth(),
                "cache", dataCacheService.getCacheStatistics(),
                "materialized_views", materializedViewRefreshService.getRefreshStatus(),
                "timestamp", java.time.Instant.now()
            );
            
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            logger.error("Failed to get system health: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get system health"));
        }
    }

    /**
     * Clear all performance history and metrics
     */
    @PostMapping("/clear-history")
    public ResponseEntity<Map<String, Object>> clearPerformanceHistory() {
        try {
            performanceMonitoringService.clearPerformanceHistory();
            connectionPoolMonitoringService.clearMetricsHistory();
            databaseOptimizationService.clearOptimizationHistory();
            
            return ResponseEntity.ok(Map.of(
                "message", "Performance history cleared",
                "timestamp", java.time.Instant.now()
            ));
        } catch (Exception e) {
            logger.error("Failed to clear performance history: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to clear performance history"));
        }
    }
}