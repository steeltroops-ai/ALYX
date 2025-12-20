package com.alyx.dataprocessing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for refreshing materialized views and maintaining cache consistency.
 * Ensures optimal query performance by keeping aggregated data up-to-date.
 */
@Service
public class MaterializedViewRefreshService {

    private static final Logger logger = LoggerFactory.getLogger(MaterializedViewRefreshService.class);

    private final DataSource dataSource;
    private final DataCacheService dataCacheService;

    @Value("${alyx.data-processing.materialized-view-refresh-interval:900}")
    private long refreshIntervalSeconds;

    // Track refresh status
    private final Map<String, Instant> lastRefreshTimes = new ConcurrentHashMap<>();
    private final Map<String, Boolean> refreshInProgress = new ConcurrentHashMap<>();

    @Autowired
    public MaterializedViewRefreshService(DataSource dataSource, DataCacheService dataCacheService) {
        this.dataSource = dataSource;
        this.dataCacheService = dataCacheService;
    }

    /**
     * Scheduled refresh of all materialized views
     */
    @Scheduled(fixedRateString = "${alyx.data-processing.materialized-view-refresh-interval:900}000") // Convert to milliseconds
    public void refreshAllMaterializedViews() {
        logger.info("Starting scheduled refresh of materialized views");
        
        try {
            refreshMaterializedView("daily_collision_summary");
            refreshMaterializedView("job_statistics_summary");
            refreshMaterializedView("user_activity_summary");
            refreshMaterializedView("collision_event_statistics");
            refreshMaterializedView("detector_performance_summary");
            
            // Refresh related caches after materialized views are updated
            dataCacheService.cacheMaterializedViews();
            dataCacheService.cacheEventAggregations();
            
            logger.info("Completed scheduled refresh of materialized views");
            
        } catch (Exception e) {
            logger.error("Failed to refresh materialized views: {}", e.getMessage(), e);
        }
    }

    /**
     * Refresh a specific materialized view
     */
    public boolean refreshMaterializedView(String viewName) {
        if (refreshInProgress.getOrDefault(viewName, false)) {
            logger.debug("Refresh already in progress for view: {}", viewName);
            return false;
        }

        refreshInProgress.put(viewName, true);
        Instant startTime = Instant.now();
        
        try (Connection connection = dataSource.getConnection()) {
            String sql = "REFRESH MATERIALIZED VIEW CONCURRENTLY " + viewName;
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.execute();
                
                Duration refreshDuration = Duration.between(startTime, Instant.now());
                lastRefreshTimes.put(viewName, Instant.now());
                
                logger.info("Successfully refreshed materialized view '{}' in {} ms", 
                    viewName, refreshDuration.toMillis());
                
                return true;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to refresh materialized view '{}': {}", viewName, e.getMessage());
            return false;
        } finally {
            refreshInProgress.put(viewName, false);
        }
    }

    /**
     * Force refresh of collision event statistics (called after bulk data ingestion)
     */
    public void refreshCollisionEventStatistics() {
        logger.info("Force refreshing collision event statistics");
        
        try {
            refreshMaterializedView("collision_event_statistics");
            refreshMaterializedView("daily_collision_summary");
            
            // Invalidate related caches
            dataCacheService.invalidateAllCaches();
            
            // Re-cache fresh data
            dataCacheService.cacheRecentEvents();
            dataCacheService.cacheEventAggregations();
            
        } catch (Exception e) {
            logger.error("Failed to force refresh collision event statistics: {}", e.getMessage());
        }
    }

    /**
     * Force refresh of job statistics (called after job status changes)
     */
    public void refreshJobStatistics() {
        logger.info("Force refreshing job statistics");
        
        try {
            refreshMaterializedView("job_statistics_summary");
            refreshMaterializedView("user_activity_summary");
            
        } catch (Exception e) {
            logger.error("Failed to force refresh job statistics: {}", e.getMessage());
        }
    }

    /**
     * Force refresh of detector performance statistics
     */
    public void refreshDetectorStatistics() {
        logger.info("Force refreshing detector performance statistics");
        
        try {
            refreshMaterializedView("detector_performance_summary");
            
            // Refresh detector cache
            dataCacheService.cacheDetectorStatistics();
            
        } catch (Exception e) {
            logger.error("Failed to force refresh detector statistics: {}", e.getMessage());
        }
    }

    /**
     * Get refresh status for all materialized views
     */
    public Map<String, Object> getRefreshStatus() {
        List<String> viewNames = List.of(
            "daily_collision_summary",
            "job_statistics_summary", 
            "user_activity_summary",
            "collision_event_statistics",
            "detector_performance_summary"
        );

        Map<String, Object> status = new ConcurrentHashMap<>();
        
        for (String viewName : viewNames) {
            Map<String, Object> viewStatus = Map.of(
                "last_refresh", lastRefreshTimes.getOrDefault(viewName, Instant.EPOCH),
                "refresh_in_progress", refreshInProgress.getOrDefault(viewName, false),
                "minutes_since_refresh", lastRefreshTimes.containsKey(viewName) ? 
                    Duration.between(lastRefreshTimes.get(viewName), Instant.now()).toMinutes() : -1
            );
            status.put(viewName, viewStatus);
        }
        
        status.put("refresh_interval_seconds", refreshIntervalSeconds);
        status.put("last_check", Instant.now());
        
        return status;
    }

    /**
     * Check if materialized views need refresh based on data freshness
     */
    public boolean needsRefresh(String viewName) {
        Instant lastRefresh = lastRefreshTimes.get(viewName);
        if (lastRefresh == null) {
            return true; // Never refreshed
        }
        
        Duration timeSinceRefresh = Duration.between(lastRefresh, Instant.now());
        return timeSinceRefresh.getSeconds() > refreshIntervalSeconds;
    }

    /**
     * Analyze materialized view performance
     */
    public Map<String, Object> analyzeMaterializedViewPerformance() {
        logger.debug("Analyzing materialized view performance");
        
        try (Connection connection = dataSource.getConnection()) {
            String sql = """
                SELECT 
                    schemaname,
                    matviewname,
                    pg_size_pretty(pg_total_relation_size(schemaname||'.'||matviewname)) as size,
                    pg_total_relation_size(schemaname||'.'||matviewname) as size_bytes
                FROM pg_matviews 
                WHERE schemaname = 'public'
                ORDER BY pg_total_relation_size(schemaname||'.'||matviewname) DESC
                """;
                
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                try (var rs = stmt.executeQuery()) {
                    Map<String, Object> analysis = new ConcurrentHashMap<>();
                    
                    while (rs.next()) {
                        String viewName = rs.getString("matviewname");
                        Map<String, Object> viewInfo = Map.of(
                            "size_pretty", rs.getString("size"),
                            "size_bytes", rs.getLong("size_bytes"),
                            "last_refresh", lastRefreshTimes.getOrDefault(viewName, Instant.EPOCH),
                            "needs_refresh", needsRefresh(viewName)
                        );
                        analysis.put(viewName, viewInfo);
                    }
                    
                    return analysis;
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to analyze materialized view performance: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Optimize materialized view refresh schedule based on usage patterns
     */
    public void optimizeRefreshSchedule() {
        logger.info("Optimizing materialized view refresh schedule");
        
        try (Connection connection = dataSource.getConnection()) {
            // Analyze query patterns to determine optimal refresh frequency
            String sql = """
                SELECT 
                    query,
                    calls,
                    mean_exec_time,
                    total_exec_time
                FROM pg_stat_statements 
                WHERE query LIKE '%materialized%view%'
                ORDER BY calls DESC
                LIMIT 10
                """;
                
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        long calls = rs.getLong("calls");
                        double meanTime = rs.getDouble("mean_exec_time");
                        
                        // If materialized views are queried frequently (>100 calls) 
                        // and queries are slow (>100ms), increase refresh frequency
                        if (calls > 100 && meanTime > 100) {
                            logger.info("High usage detected for materialized views, " +
                                "consider increasing refresh frequency");
                        }
                    }
                }
            }
            
        } catch (SQLException e) {
            logger.debug("Could not analyze query patterns (pg_stat_statements may not be enabled): {}", 
                e.getMessage());
        }
    }

    /**
     * Emergency refresh of all views (for data consistency issues)
     */
    public void emergencyRefreshAll() {
        logger.warn("Performing emergency refresh of all materialized views");
        
        try {
            // Clear all caches first
            dataCacheService.invalidateAllCaches();
            
            // Refresh all views
            refreshAllMaterializedViews();
            
            // Warm up caches with fresh data
            dataCacheService.cacheRecentEvents();
            dataCacheService.cacheDetectorStatistics();
            dataCacheService.cacheEventAggregations();
            
            logger.info("Emergency refresh completed successfully");
            
        } catch (Exception e) {
            logger.error("Emergency refresh failed: {}", e.getMessage(), e);
        }
    }
}