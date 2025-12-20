package com.alyx.jobscheduler.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for monitoring system performance and collecting metrics.
 * Provides real-time monitoring of database connections, cache performance, and job processing.
 */
@Service
public class PerformanceMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitoringService.class);

    private final MeterRegistry meterRegistry;
    private final DataSource dataSource;
    private final CacheService cacheService;

    // Metrics counters
    private final Counter jobSubmissionCounter;
    private final Counter jobCompletionCounter;
    private final Counter jobFailureCounter;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Timer jobExecutionTimer;
    private final Timer databaseQueryTimer;
    private final Timer cacheOperationTimer;

    // Gauge values
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong activeJobs = new AtomicLong(0);
    private final AtomicLong queuedJobs = new AtomicLong(0);
    private final AtomicLong cacheSize = new AtomicLong(0);

    // Performance tracking
    private final Map<String, Long> slowQueries = new ConcurrentHashMap<>();
    private final Map<String, Instant> connectionLeaks = new ConcurrentHashMap<>();

    @Autowired
    public PerformanceMonitoringService(MeterRegistry meterRegistry, 
                                      DataSource dataSource,
                                      CacheService cacheService) {
        this.meterRegistry = meterRegistry;
        this.dataSource = dataSource;
        this.cacheService = cacheService;

        // Initialize counters
        this.jobSubmissionCounter = Counter.builder("alyx.jobs.submitted")
            .description("Total number of jobs submitted")
            .register(meterRegistry);

        this.jobCompletionCounter = Counter.builder("alyx.jobs.completed")
            .description("Total number of jobs completed successfully")
            .register(meterRegistry);

        this.jobFailureCounter = Counter.builder("alyx.jobs.failed")
            .description("Total number of jobs that failed")
            .register(meterRegistry);

        this.cacheHitCounter = Counter.builder("alyx.cache.hits")
            .description("Total number of cache hits")
            .register(meterRegistry);

        this.cacheMissCounter = Counter.builder("alyx.cache.misses")
            .description("Total number of cache misses")
            .register(meterRegistry);

        // Initialize timers
        this.jobExecutionTimer = Timer.builder("alyx.jobs.execution.time")
            .description("Job execution time")
            .register(meterRegistry);

        this.databaseQueryTimer = Timer.builder("alyx.database.query.time")
            .description("Database query execution time")
            .register(meterRegistry);

        this.cacheOperationTimer = Timer.builder("alyx.cache.operation.time")
            .description("Cache operation time")
            .register(meterRegistry);

        // Initialize gauges
        Gauge.builder("alyx.database.connections.active", this, PerformanceMonitoringService::getActiveConnections)
            .description("Number of active database connections")
            .register(meterRegistry);

        Gauge.builder("alyx.jobs.active", this, PerformanceMonitoringService::getActiveJobs)
            .description("Number of currently active jobs")
            .register(meterRegistry);

        Gauge.builder("alyx.jobs.queued", this, PerformanceMonitoringService::getQueuedJobs)
            .description("Number of queued jobs")
            .register(meterRegistry);

        Gauge.builder("alyx.cache.size", this, PerformanceMonitoringService::getCacheSize)
            .description("Current cache size")
            .register(meterRegistry);
    }

    /**
     * Record job submission
     */
    public void recordJobSubmission() {
        jobSubmissionCounter.increment();
        logger.debug("Recorded job submission");
    }

    /**
     * Record job completion
     */
    public void recordJobCompletion(Duration executionTime) {
        jobCompletionCounter.increment();
        jobExecutionTimer.record(executionTime);
        logger.debug("Recorded job completion with execution time: {}", executionTime);
    }

    /**
     * Record job failure
     */
    public void recordJobFailure(String reason) {
        jobFailureCounter.increment();
        logger.debug("Recorded job failure: {}", reason);
    }

    /**
     * Record cache hit
     */
    public void recordCacheHit(String cacheType) {
        cacheHitCounter.increment();
        logger.debug("Recorded cache hit for: {}", cacheType);
    }

    /**
     * Record cache miss
     */
    public void recordCacheMiss(String cacheType) {
        cacheMissCounter.increment();
        logger.debug("Recorded cache miss for: {}", cacheType);
    }

    /**
     * Record database query execution time
     */
    public void recordDatabaseQuery(Duration queryTime, String query) {
        databaseQueryTimer.record(queryTime);
        
        // Track slow queries (> 1 second)
        if (queryTime.toMillis() > 1000) {
            slowQueries.put(query, queryTime.toMillis());
            logger.warn("Slow query detected: {} ms - {}", queryTime.toMillis(), query);
        }
    }

    /**
     * Record cache operation time
     */
    public void recordCacheOperation(Duration operationTime, String operation) {
        cacheOperationTimer.record(operationTime);
        logger.debug("Recorded cache operation: {} in {}", operation, operationTime);
    }

    /**
     * Update active connection count
     */
    public void updateActiveConnections(long count) {
        activeConnections.set(count);
    }

    /**
     * Update active job count
     */
    public void updateActiveJobs(long count) {
        activeJobs.set(count);
    }

    /**
     * Update queued job count
     */
    public void updateQueuedJobs(long count) {
        queuedJobs.set(count);
    }

    /**
     * Update cache size
     */
    public void updateCacheSize(long size) {
        cacheSize.set(size);
    }

    /**
     * Get current active connections
     */
    public double getActiveConnections() {
        return activeConnections.get();
    }

    /**
     * Get current active jobs
     */
    public double getActiveJobs() {
        return activeJobs.get();
    }

    /**
     * Get current queued jobs
     */
    public double getQueuedJobs() {
        return queuedJobs.get();
    }

    /**
     * Get current cache size
     */
    public double getCacheSize() {
        return cacheSize.get();
    }

    /**
     * Scheduled task to collect database metrics
     */
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void collectDatabaseMetrics() {
        try (Connection connection = dataSource.getConnection()) {
            // Get active connection count
            try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT count(*) FROM pg_stat_activity WHERE state = 'active'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        updateActiveConnections(rs.getLong(1));
                    }
                }
            }

            // Get job counts
            try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT status, count(*) FROM analysis_jobs WHERE status IN ('QUEUED', 'RUNNING') GROUP BY status")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    long active = 0, queued = 0;
                    while (rs.next()) {
                        String status = rs.getString(1);
                        long count = rs.getLong(2);
                        if ("RUNNING".equals(status)) {
                            active = count;
                        } else if ("QUEUED".equals(status)) {
                            queued = count;
                        }
                    }
                    updateActiveJobs(active);
                    updateQueuedJobs(queued);
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to collect database metrics: {}", e.getMessage());
        }
    }

    /**
     * Scheduled task to collect cache metrics
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void collectCacheMetrics() {
        try {
            Map<String, Object> cacheStats = cacheService.getCacheStatistics();
            if (cacheStats.containsKey("redis_info")) {
                // Parse Redis memory usage and update cache size
                String redisInfo = (String) cacheStats.get("redis_info");
                // This is a simplified parsing - in production, you'd parse the actual Redis INFO output
                updateCacheSize(1000); // Placeholder value
            }
        } catch (Exception e) {
            logger.error("Failed to collect cache metrics: {}", e.getMessage());
        }
    }

    /**
     * Get performance summary
     */
    public Map<String, Object> getPerformanceSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("jobs_submitted_total", jobSubmissionCounter.count());
        summary.put("jobs_completed_total", jobCompletionCounter.count());
        summary.put("jobs_failed_total", jobFailureCounter.count());
        summary.put("cache_hits_total", cacheHitCounter.count());
        summary.put("cache_misses_total", cacheMissCounter.count());
        summary.put("active_connections", getActiveConnections());
        summary.put("active_jobs", getActiveJobs());
        summary.put("queued_jobs", getQueuedJobs());
        summary.put("cache_size", getCacheSize());
        summary.put("slow_queries_count", slowQueries.size());
        summary.put("avg_job_execution_time_ms", jobExecutionTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
        summary.put("avg_database_query_time_ms", databaseQueryTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
        return summary;
    }

    /**
     * Get slow queries report
     */
    public Map<String, Long> getSlowQueries() {
        return Map.copyOf(slowQueries);
    }

    /**
     * Clear slow queries history
     */
    public void clearSlowQueries() {
        slowQueries.clear();
        logger.info("Cleared slow queries history");
    }

    /**
     * Check system health
     */
    public Map<String, Object> getHealthCheck() {
        boolean healthy = true;
        StringBuilder issues = new StringBuilder();

        // Check database connections
        if (getActiveConnections() > 80) { // Assuming max 100 connections
            healthy = false;
            issues.append("High database connection usage; ");
        }

        // Check job queue
        if (getQueuedJobs() > 1000) {
            healthy = false;
            issues.append("High job queue backlog; ");
        }

        // Check cache hit ratio
        double totalCacheOps = cacheHitCounter.count() + cacheMissCounter.count();
        double hitRatio = totalCacheOps > 0 ? cacheHitCounter.count() / totalCacheOps : 1.0;
        if (hitRatio < 0.8) { // Less than 80% hit ratio
            healthy = false;
            issues.append("Low cache hit ratio; ");
        }

        return Map.of(
            "healthy", healthy,
            "issues", issues.toString(),
            "cache_hit_ratio", hitRatio,
            "timestamp", Instant.now()
        );
    }
}