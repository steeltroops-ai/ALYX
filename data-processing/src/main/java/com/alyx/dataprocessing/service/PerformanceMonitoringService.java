package com.alyx.dataprocessing.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enhanced performance monitoring service for ALYX data processing.
 * Monitors database performance, cache efficiency, and system health.
 * Provides alerting for performance degradation and optimization recommendations.
 */
@Service
public class PerformanceMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitoringService.class);

    private final MeterRegistry meterRegistry;
    private final DataSource dataSource;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DataCacheService dataCacheService;

    @Value("${alyx.data-processing.slow-query-threshold-ms:1000}")
    private long slowQueryThresholdMs;

    @Value("${alyx.data-processing.connection-pool-warning-threshold:80}")
    private int connectionPoolWarningThreshold;

    @Value("${alyx.data-processing.cache-hit-ratio-warning-threshold:0.8}")
    private double cacheHitRatioWarningThreshold;

    // Metrics counters
    private final Counter queryExecutionCounter;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Counter slowQueryCounter;
    private final Counter connectionLeakCounter;
    private final Timer queryExecutionTimer;
    private final Timer cacheOperationTimer;
    private final Timer spatialQueryTimer;

    // Gauge values
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong idleConnections = new AtomicLong(0);
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong cacheMemoryUsage = new AtomicLong(0);
    private final AtomicLong databaseSize = new AtomicLong(0);

    // Performance tracking
    private final Map<String, Long> slowQueries = new ConcurrentHashMap<>();
    private final Map<String, Double> queryPerformanceHistory = new ConcurrentHashMap<>();
    private final Map<String, Instant> alertHistory = new ConcurrentHashMap<>();

    @Autowired
    public PerformanceMonitoringService(MeterRegistry meterRegistry,
                                      DataSource dataSource,
                                      RedisTemplate<String, Object> redisTemplate,
                                      DataCacheService dataCacheService) {
        this.meterRegistry = meterRegistry;
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
        this.dataCacheService = dataCacheService;

        // Initialize counters
        this.queryExecutionCounter = Counter.builder("alyx.dataprocessing.queries.executed")
            .description("Total number of database queries executed")
            .register(meterRegistry);

        this.cacheHitCounter = Counter.builder("alyx.dataprocessing.cache.hits")
            .description("Total number of cache hits")
            .register(meterRegistry);

        this.cacheMissCounter = Counter.builder("alyx.dataprocessing.cache.misses")
            .description("Total number of cache misses")
            .register(meterRegistry);

        this.slowQueryCounter = Counter.builder("alyx.dataprocessing.queries.slow")
            .description("Total number of slow queries detected")
            .register(meterRegistry);

        this.connectionLeakCounter = Counter.builder("alyx.dataprocessing.connections.leaked")
            .description("Total number of connection leaks detected")
            .register(meterRegistry);

        // Initialize timers
        this.queryExecutionTimer = Timer.builder("alyx.dataprocessing.query.execution.time")
            .description("Database query execution time")
            .register(meterRegistry);

        this.cacheOperationTimer = Timer.builder("alyx.dataprocessing.cache.operation.time")
            .description("Cache operation execution time")
            .register(meterRegistry);

        this.spatialQueryTimer = Timer.builder("alyx.dataprocessing.spatial.query.time")
            .description("Spatial query execution time")
            .register(meterRegistry);

        // Initialize gauges
        Gauge.builder("alyx.dataprocessing.connections.active", this, PerformanceMonitoringService::getActiveConnections)
            .description("Number of active database connections")
            .register(meterRegistry);

        Gauge.builder("alyx.dataprocessing.connections.idle", this, PerformanceMonitoringService::getIdleConnections)
            .description("Number of idle database connections")
            .register(meterRegistry);

        Gauge.builder("alyx.dataprocessing.connections.total", this, PerformanceMonitoringService::getTotalConnections)
            .description("Total number of database connections")
            .register(meterRegistry);

        Gauge.builder("alyx.dataprocessing.cache.memory.usage", this, PerformanceMonitoringService::getCacheMemoryUsage)
            .description("Cache memory usage in bytes")
            .register(meterRegistry);

        Gauge.builder("alyx.dataprocessing.database.size", this, PerformanceMonitoringService::getDatabaseSize)
            .description("Database size in bytes")
            .register(meterRegistry);

        Gauge.builder("alyx.dataprocessing.cache.hit.ratio", this, PerformanceMonitoringService::getCacheHitRatio)
            .description("Cache hit ratio percentage")
            .register(meterRegistry);
    }

    /**
     * Record database query execution
     */
    public void recordQueryExecution(String queryType, Duration executionTime, String query) {
        queryExecutionCounter.increment();
        queryExecutionTimer.record(executionTime);

        // Track query performance history
        queryPerformanceHistory.put(queryType, executionTime.toMillis() / 1000.0);

        // Check for slow queries
        if (executionTime.toMillis() > slowQueryThresholdMs) {
            slowQueryCounter.increment();
            slowQueries.put(query, executionTime.toMillis());
            logger.warn("Slow query detected: {} ms - Query type: {} - Query: {}", 
                executionTime.toMillis(), queryType, query.substring(0, Math.min(query.length(), 100)));
            
            // Send alert for extremely slow queries (>5 seconds)
            if (executionTime.toMillis() > 5000) {
                sendPerformanceAlert("SLOW_QUERY", 
                    String.format("Extremely slow query detected: %d ms", executionTime.toMillis()));
            }
        }
    }

    /**
     * Record spatial query execution
     */
    public void recordSpatialQuery(Duration executionTime, String queryType) {
        spatialQueryTimer.record(executionTime);
        logger.debug("Spatial query executed: {} ms - Type: {}", executionTime.toMillis(), queryType);
    }

    /**
     * Record cache operation
     */
    public void recordCacheOperation(String operation, Duration operationTime, boolean hit) {
        cacheOperationTimer.record(operationTime);
        
        if (hit) {
            cacheHitCounter.increment();
        } else {
            cacheMissCounter.increment();
        }

        logger.debug("Cache operation: {} - Time: {} ms - Hit: {}", 
            operation, operationTime.toMillis(), hit);
    }

    /**
     * Record connection leak
     */
    public void recordConnectionLeak(String source) {
        connectionLeakCounter.increment();
        logger.error("Connection leak detected from: {}", source);
        sendPerformanceAlert("CONNECTION_LEAK", "Connection leak detected from: " + source);
    }

    /**
     * Scheduled task to collect database performance metrics
     */
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void collectDatabaseMetrics() {
        try (Connection connection = dataSource.getConnection()) {
            // Get connection statistics
            collectConnectionStatistics(connection);
            
            // Get database size
            collectDatabaseSizeMetrics(connection);
            
            // Get query performance statistics
            collectQueryPerformanceMetrics(connection);
            
            // Check for performance issues
            checkPerformanceThresholds();
            
        } catch (SQLException e) {
            logger.error("Failed to collect database metrics: {}", e.getMessage());
        }
    }

    /**
     * Collect connection pool statistics
     */
    private void collectConnectionStatistics(Connection connection) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
            "SELECT state, count(*) FROM pg_stat_activity GROUP BY state")) {
            try (ResultSet rs = stmt.executeQuery()) {
                long active = 0, idle = 0, total = 0;
                while (rs.next()) {
                    String state = rs.getString(1);
                    long count = rs.getLong(2);
                    total += count;
                    
                    if ("active".equals(state)) {
                        active = count;
                    } else if ("idle".equals(state)) {
                        idle = count;
                    }
                }
                
                activeConnections.set(active);
                idleConnections.set(idle);
                totalConnections.set(total);
            }
        }
    }

    /**
     * Collect database size metrics
     */
    private void collectDatabaseSizeMetrics(Connection connection) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
            "SELECT pg_database_size(current_database())")) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    databaseSize.set(rs.getLong(1));
                }
            }
        }
    }

    /**
     * Collect query performance metrics
     */
    private void collectQueryPerformanceMetrics(Connection connection) throws SQLException {
        // Check if pg_stat_statements extension is available
        try (PreparedStatement stmt = connection.prepareStatement(
            "SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'pg_stat_statements')")) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getBoolean(1)) {
                    collectPgStatStatements(connection);
                }
            }
        }
    }

    /**
     * Collect statistics from pg_stat_statements
     */
    private void collectPgStatStatements(Connection connection) throws SQLException {
        String sql = """
            SELECT query, calls, mean_exec_time, total_exec_time
            FROM pg_stat_statements 
            WHERE mean_exec_time > ?
            ORDER BY mean_exec_time DESC 
            LIMIT 10
            """;
            
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setDouble(1, slowQueryThresholdMs);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String query = rs.getString("query");
                    long calls = rs.getLong("calls");
                    double meanTime = rs.getDouble("mean_exec_time");
                    
                    // Track frequently executed slow queries
                    if (calls > 100 && meanTime > slowQueryThresholdMs) {
                        String queryKey = "frequent_slow_" + query.hashCode();
                        slowQueries.put(queryKey, (long) meanTime);
                    }
                }
            }
        }
    }

    /**
     * Scheduled task to collect cache metrics
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void collectCacheMetrics() {
        try {
            // Get Redis memory usage
            String memoryInfo = redisTemplate.getConnectionFactory()
                .getConnection()
                .info("memory")
                .getProperty("used_memory");
                
            if (memoryInfo != null) {
                cacheMemoryUsage.set(Long.parseLong(memoryInfo));
            }

            // Get cache statistics from DataCacheService
            Map<String, Object> cacheStats = dataCacheService.getCacheStatistics();
            logger.debug("Cache statistics: {}", cacheStats);
            
        } catch (Exception e) {
            logger.error("Failed to collect cache metrics: {}", e.getMessage());
        }
    }

    /**
     * Check performance thresholds and send alerts
     */
    private void checkPerformanceThresholds() {
        // Check connection pool usage
        double connectionUsage = (double) activeConnections.get() / 100.0; // Assuming max 100 connections
        if (connectionUsage > connectionPoolWarningThreshold / 100.0) {
            sendPerformanceAlert("HIGH_CONNECTION_USAGE", 
                String.format("Connection pool usage: %.1f%%", connectionUsage * 100));
        }

        // Check cache hit ratio
        double hitRatio = getCacheHitRatio();
        if (hitRatio < cacheHitRatioWarningThreshold) {
            sendPerformanceAlert("LOW_CACHE_HIT_RATIO", 
                String.format("Cache hit ratio: %.2f", hitRatio));
        }

        // Check database size growth
        long dbSizeMB = databaseSize.get() / (1024 * 1024);
        if (dbSizeMB > 10000) { // 10GB threshold
            sendPerformanceAlert("LARGE_DATABASE_SIZE", 
                String.format("Database size: %d MB", dbSizeMB));
        }
    }

    /**
     * Send performance alert (rate-limited)
     */
    private void sendPerformanceAlert(String alertType, String message) {
        String alertKey = alertType + "_" + message.hashCode();
        Instant lastAlert = alertHistory.get(alertKey);
        
        // Rate limit alerts to once per hour
        if (lastAlert == null || Duration.between(lastAlert, Instant.now()).toHours() >= 1) {
            logger.warn("PERFORMANCE ALERT [{}]: {}", alertType, message);
            alertHistory.put(alertKey, Instant.now());
            
            // In a production system, this would integrate with alerting systems like:
            // - Slack notifications
            // - Email alerts
            // - PagerDuty
            // - Custom webhook endpoints
        }
    }

    /**
     * Get performance optimization recommendations
     */
    public List<Map<String, Object>> getOptimizationRecommendations() {
        List<Map<String, Object>> recommendations = new java.util.ArrayList<>();

        // Check for missing indexes
        if (!slowQueries.isEmpty()) {
            recommendations.add(Map.of(
                "type", "INDEX_OPTIMIZATION",
                "priority", "HIGH",
                "description", "Slow queries detected - consider adding indexes",
                "details", "Found " + slowQueries.size() + " slow queries"
            ));
        }

        // Check cache hit ratio
        double hitRatio = getCacheHitRatio();
        if (hitRatio < 0.8) {
            recommendations.add(Map.of(
                "type", "CACHE_OPTIMIZATION",
                "priority", "MEDIUM",
                "description", "Low cache hit ratio - consider cache warming or TTL adjustment",
                "details", String.format("Current hit ratio: %.2f", hitRatio)
            ));
        }

        // Check connection pool usage
        double connectionUsage = (double) activeConnections.get() / 100.0;
        if (connectionUsage > 0.8) {
            recommendations.add(Map.of(
                "type", "CONNECTION_POOL_OPTIMIZATION",
                "priority", "HIGH",
                "description", "High connection pool usage - consider increasing pool size",
                "details", String.format("Current usage: %.1f%%", connectionUsage * 100)
            ));
        }

        return recommendations;
    }

    /**
     * Get comprehensive performance report
     */
    public Map<String, Object> getPerformanceReport() {
        return Map.of(
            "database_metrics", Map.of(
                "active_connections", getActiveConnections(),
                "idle_connections", getIdleConnections(),
                "total_connections", getTotalConnections(),
                "database_size_mb", getDatabaseSize() / (1024 * 1024),
                "slow_queries_count", slowQueries.size()
            ),
            "cache_metrics", Map.of(
                "hit_ratio", getCacheHitRatio(),
                "memory_usage_mb", getCacheMemoryUsage() / (1024 * 1024),
                "total_hits", cacheHitCounter.count(),
                "total_misses", cacheMissCounter.count()
            ),
            "query_metrics", Map.of(
                "total_queries", queryExecutionCounter.count(),
                "avg_execution_time_ms", queryExecutionTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS),
                "slow_queries", slowQueryCounter.count(),
                "spatial_queries_avg_ms", spatialQueryTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS)
            ),
            "alerts", Map.of(
                "connection_leaks", connectionLeakCounter.count(),
                "recent_alerts", alertHistory.size()
            ),
            "recommendations", getOptimizationRecommendations(),
            "timestamp", Instant.now()
        );
    }

    // Getter methods for gauges
    public double getActiveConnections() { return activeConnections.get(); }
    public double getIdleConnections() { return idleConnections.get(); }
    public double getTotalConnections() { return totalConnections.get(); }
    public double getCacheMemoryUsage() { return cacheMemoryUsage.get(); }
    public double getDatabaseSize() { return databaseSize.get(); }

    public double getCacheHitRatio() {
        double totalOps = cacheHitCounter.count() + cacheMissCounter.count();
        return totalOps > 0 ? cacheHitCounter.count() / totalOps : 1.0;
    }

    /**
     * Clear performance history (for maintenance)
     */
    public void clearPerformanceHistory() {
        slowQueries.clear();
        queryPerformanceHistory.clear();
        alertHistory.clear();
        logger.info("Performance history cleared");
    }

    /**
     * Force performance analysis
     */
    public void analyzePerformance() {
        logger.info("Starting performance analysis");
        collectDatabaseMetrics();
        collectCacheMetrics();
        checkPerformanceThresholds();
        logger.info("Performance analysis completed");
    }
}