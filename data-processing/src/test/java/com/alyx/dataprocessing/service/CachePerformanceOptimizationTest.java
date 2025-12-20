package com.alyx.dataprocessing.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class for caching and performance optimization functionality.
 * Tests the core caching, invalidation, and performance monitoring features.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "alyx.data-processing.slow-query-threshold-ms=1000",
    "alyx.data-processing.cache-ttl-seconds=3600",
    "alyx.data-processing.connection-pool-warning-threshold=80"
})
class CachePerformanceOptimizationTest {

    @MockBean
    private DataSource dataSource;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    private MeterRegistry meterRegistry;
    private PerformanceMonitoringService performanceMonitoringService;
    private DataCacheService dataCacheService;
    private CacheInvalidationService cacheInvalidationService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        dataCacheService = new DataCacheService(redisTemplate, dataSource);
        performanceMonitoringService = new PerformanceMonitoringService(
            meterRegistry, dataSource, redisTemplate, dataCacheService);
        cacheInvalidationService = new CacheInvalidationService(
            redisTemplate, dataCacheService, performanceMonitoringService);
    }

    @Test
    void testPerformanceMonitoringMetricsRecording() {
        // Test query execution recording
        Duration queryTime = Duration.ofMillis(1500);
        String query = "SELECT * FROM collision_events WHERE timestamp > ?";
        
        performanceMonitoringService.recordQueryExecution("collision_query", queryTime, query);
        
        // Verify metrics are recorded
        Map<String, Object> report = performanceMonitoringService.getPerformanceReport();
        assertNotNull(report);
        assertTrue(report.containsKey("query_metrics"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> queryMetrics = (Map<String, Object>) report.get("query_metrics");
        assertTrue((Double) queryMetrics.get("total_queries") >= 1.0);
    }

    @Test
    void testCacheOperationRecording() {
        // Test cache hit recording
        Duration cacheTime = Duration.ofMillis(50);
        performanceMonitoringService.recordCacheOperation("get_collision_event", cacheTime, true);
        
        // Test cache miss recording
        performanceMonitoringService.recordCacheOperation("get_collision_event", cacheTime, false);
        
        // Verify cache metrics
        Map<String, Object> report = performanceMonitoringService.getPerformanceReport();
        assertNotNull(report);
        assertTrue(report.containsKey("cache_metrics"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> cacheMetrics = (Map<String, Object>) report.get("cache_metrics");
        assertTrue((Double) cacheMetrics.get("total_hits") >= 1.0);
        assertTrue((Double) cacheMetrics.get("total_misses") >= 1.0);
    }

    @Test
    void testSpatialQueryRecording() {
        // Test spatial query recording
        Duration spatialQueryTime = Duration.ofMillis(2000);
        performanceMonitoringService.recordSpatialQuery(spatialQueryTime, "detector_geometry_query");
        
        // Verify spatial query metrics are recorded
        Map<String, Object> report = performanceMonitoringService.getPerformanceReport();
        assertNotNull(report);
        assertTrue(report.containsKey("query_metrics"));
    }

    @Test
    void testCacheInvalidationStatistics() {
        // Test cache invalidation statistics
        Map<String, Object> stats = cacheInvalidationService.getInvalidationStatistics();
        
        assertNotNull(stats);
        assertTrue(stats.containsKey("total_cache_keys"));
        assertTrue(stats.containsKey("cache_patterns"));
        assertTrue(stats.containsKey("timestamp"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> patterns = (Map<String, Object>) stats.get("cache_patterns");
        assertTrue(patterns.containsKey("collision_events"));
        assertTrue(patterns.containsKey("detector_hits"));
        assertTrue(patterns.containsKey("spatial_queries"));
    }

    @Test
    void testCollisionEventCacheInvalidation() {
        UUID eventId = UUID.randomUUID();
        
        // Mock Redis operations
        when(redisTemplate.keys(anyString())).thenReturn(java.util.Set.of());
        
        // Test collision event cache invalidation
        assertDoesNotThrow(() -> {
            cacheInvalidationService.invalidateCollisionEventCache(eventId);
        });
        
        // Verify Redis delete operations were called
        verify(redisTemplate, atLeastOnce()).delete(anyString());
    }

    @Test
    void testCacheAccessTracking() {
        String cacheKey = "test:cache:key";
        
        // Test cache access tracking
        cacheInvalidationService.trackCacheAccess(cacheKey);
        cacheInvalidationService.trackCacheAccess(cacheKey);
        
        // Verify tracking doesn't throw exceptions
        assertDoesNotThrow(() -> {
            cacheInvalidationService.trackCacheAccess(cacheKey);
        });
    }

    @Test
    void testStaleDataInvalidation() {
        Duration maxAge = Duration.ofHours(1);
        
        // Mock Redis operations for stale cache invalidation
        when(redisTemplate.keys("*")).thenReturn(java.util.Set.of("test:key:1", "test:key:2"));
        when(redisTemplate.getExpire(anyString(), any())).thenReturn(3600L);
        
        // Test stale cache invalidation
        assertDoesNotThrow(() -> {
            cacheInvalidationService.invalidateStaleCache(maxAge);
        });
    }

    @Test
    void testCacheWarmingAfterInvalidation() {
        // Test cache warming for different types
        String[] cacheTypes = {
            "collision_events", 
            "detector_statistics", 
            "materialized_views", 
            "spatial_queries", 
            "event_aggregations"
        };
        
        for (String cacheType : cacheTypes) {
            assertDoesNotThrow(() -> {
                cacheInvalidationService.warmCacheAfterInvalidation(cacheType);
            });
        }
    }

    @Test
    void testPerformanceReportGeneration() {
        // Generate performance report
        Map<String, Object> report = performanceMonitoringService.getPerformanceReport();
        
        // Verify report structure
        assertNotNull(report);
        assertTrue(report.containsKey("database_metrics"));
        assertTrue(report.containsKey("cache_metrics"));
        assertTrue(report.containsKey("query_metrics"));
        assertTrue(report.containsKey("alerts"));
        assertTrue(report.containsKey("recommendations"));
        assertTrue(report.containsKey("timestamp"));
        
        // Verify timestamp is recent
        Instant timestamp = (Instant) report.get("timestamp");
        assertTrue(Duration.between(timestamp, Instant.now()).toMinutes() < 1);
    }

    @Test
    void testOptimizationRecommendations() {
        // Test optimization recommendations generation
        var recommendations = performanceMonitoringService.getOptimizationRecommendations();
        
        assertNotNull(recommendations);
        // Recommendations list may be empty in test environment, which is fine
        assertTrue(recommendations instanceof java.util.List);
    }

    @Test
    void testCacheHitRatioCalculation() {
        // Record some cache operations
        performanceMonitoringService.recordCacheOperation("test1", Duration.ofMillis(10), true);
        performanceMonitoringService.recordCacheOperation("test2", Duration.ofMillis(15), true);
        performanceMonitoringService.recordCacheOperation("test3", Duration.ofMillis(20), false);
        
        // Calculate hit ratio
        double hitRatio = performanceMonitoringService.getCacheHitRatio();
        
        // Should be approximately 0.67 (2 hits out of 3 operations)
        assertTrue(hitRatio >= 0.6 && hitRatio <= 0.7);
    }

    @Test
    void testPerformanceHistoryClearing() {
        // Record some metrics first
        performanceMonitoringService.recordQueryExecution("test", Duration.ofMillis(100), "SELECT 1");
        performanceMonitoringService.recordCacheOperation("test", Duration.ofMillis(10), true);
        
        // Clear history
        assertDoesNotThrow(() -> {
            performanceMonitoringService.clearPerformanceHistory();
        });
        
        // Verify clearing doesn't break functionality
        Map<String, Object> report = performanceMonitoringService.getPerformanceReport();
        assertNotNull(report);
    }

    @Test
    void testEmergencyCacheClear() {
        // Test emergency cache clear
        assertDoesNotThrow(() -> {
            cacheInvalidationService.emergencyCacheClear();
        });
        
        // Verify it doesn't throw exceptions
        Map<String, Object> stats = cacheInvalidationService.getInvalidationStatistics();
        assertNotNull(stats);
    }

    @Test
    void testCacheInvalidationByFreshness() {
        Duration maxFreshness = Duration.ofMinutes(30);
        
        // Mock Redis operations
        when(redisTemplate.keys(anyString())).thenReturn(java.util.Set.of("test:key"));
        
        // Test freshness-based invalidation
        assertDoesNotThrow(() -> {
            cacheInvalidationService.invalidateByFreshness("collision_events", maxFreshness);
        });
    }

    @Test
    void testBulkCacheInvalidation() {
        java.util.List<UUID> eventIds = java.util.List.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        
        // Mock Redis operations
        when(redisTemplate.keys(anyString())).thenReturn(java.util.Set.of());
        
        // Test bulk invalidation
        assertDoesNotThrow(() -> {
            cacheInvalidationService.bulkInvalidateForConsistency(eventIds);
        });
        
        // Verify multiple delete operations
        verify(redisTemplate, atLeast(eventIds.size())).delete(anyString());
    }
}