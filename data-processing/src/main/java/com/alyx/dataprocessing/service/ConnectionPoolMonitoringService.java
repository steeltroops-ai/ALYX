package com.alyx.dataprocessing.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for monitoring HikariCP connection pool health and performance.
 * Provides detailed metrics, alerts, and optimization recommendations for connection pool management.
 */
@Service
public class ConnectionPoolMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolMonitoringService.class);

    private final DataSource dataSource;
    private final MeterRegistry meterRegistry;
    private final PerformanceMonitoringService performanceMonitoringService;

    @Value("${spring.datasource.hikari.maximum-pool-size:50}")
    private int maxPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:10}")
    private int minIdle;

    @Value("${alyx.data-processing.connection-pool-warning-threshold:80}")
    private int warningThreshold;

    @Value("${alyx.data-processing.connection-pool-critical-threshold:95}")
    private int criticalThreshold;

    // Connection pool metrics
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong idleConnections = new AtomicLong(0);
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong threadsAwaitingConnection = new AtomicLong(0);

    // Performance tracking
    private final Map<String, ConnectionPoolMetrics> metricsHistory = new ConcurrentHashMap<>();
    private final Map<String, Instant> alertHistory = new ConcurrentHashMap<>();
    private Instant lastHealthCheck = Instant.now();

    @Autowired
    public ConnectionPoolMonitoringService(DataSource dataSource,
                                         MeterRegistry meterRegistry,
                                         PerformanceMonitoringService performanceMonitoringService) {
        this.dataSource = dataSource;
        this.meterRegistry = meterRegistry;
        this.performanceMonitoringService = performanceMonitoringService;
        
        initializeMetrics();
    }

    /**
     * Initialize Micrometer metrics for connection pool monitoring
     */
    private void initializeMetrics() {
        // Register gauges for connection pool metrics
        Gauge.builder("alyx.dataprocessing.hikari.active.connections", this, ConnectionPoolMonitoringService::getActiveConnections)
            .description("Number of active connections in the pool")
            .register(meterRegistry);

        Gauge.builder("alyx.dataprocessing.hikari.idle.connections", this, ConnectionPoolMonitoringService::getIdleConnections)
            .description("Number of idle connections in the pool")
            .register(meterRegistry);

        Gauge.builder("alyx.dataprocessing.hikari.total.connections", this, ConnectionPoolMonitoringService::getTotalConnections)
            .description("Total number of connections in the pool")
            .register(meterRegistry);

        Gauge.builder("alyx.dataprocessing.hikari.pending.threads", this, ConnectionPoolMonitoringService::getThreadsAwaitingConnection)
            .description("Number of threads waiting for connections")
            .register(meterRegistry);

        Gauge.builder("alyx.dataprocessing.hikari.pool.usage.percent", this, ConnectionPoolMonitoringService::getPoolUsagePercent)
            .description("Connection pool usage percentage")
            .register(meterRegistry);
    }

    /**
     * Scheduled monitoring of connection pool health
     */
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void monitorConnectionPool() {
        try {
            collectConnectionPoolMetrics();
            checkConnectionPoolHealth();
            recordMetricsHistory();
            
        } catch (Exception e) {
            logger.error("Failed to monitor connection pool: {}", e.getMessage());
        }
    }

    /**
     * Collect connection pool metrics from HikariCP
     */
    private void collectConnectionPoolMetrics() {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();
            
            if (poolMXBean != null) {
                activeConnections.set(poolMXBean.getActiveConnections());
                idleConnections.set(poolMXBean.getIdleConnections());
                totalConnections.set(poolMXBean.getTotalConnections());
                threadsAwaitingConnection.set(poolMXBean.getThreadsAwaitingConnection());
                
                logger.debug("Connection pool metrics - Active: {}, Idle: {}, Total: {}, Waiting: {}", 
                    activeConnections.get(), idleConnections.get(), 
                    totalConnections.get(), threadsAwaitingConnection.get());
            }
        } else {
            logger.warn("DataSource is not HikariDataSource, limited monitoring available");
        }
    }

    /**
     * Check connection pool health and send alerts if necessary
     */
    private void checkConnectionPoolHealth() {
        double usagePercent = getPoolUsagePercent();
        long waitingThreads = threadsAwaitingConnection.get();
        
        // Check for critical usage
        if (usagePercent >= criticalThreshold) {
            sendAlert("CRITICAL_POOL_USAGE", 
                String.format("Critical connection pool usage: %.1f%% (%d/%d connections)", 
                    usagePercent, activeConnections.get(), maxPoolSize));
        }
        // Check for warning usage
        else if (usagePercent >= warningThreshold) {
            sendAlert("HIGH_POOL_USAGE", 
                String.format("High connection pool usage: %.1f%% (%d/%d connections)", 
                    usagePercent, activeConnections.get(), maxPoolSize));
        }
        
        // Check for threads waiting for connections
        if (waitingThreads > 0) {
            sendAlert("THREADS_WAITING", 
                String.format("%d threads waiting for database connections", waitingThreads));
        }
        
        // Check for pool exhaustion
        if (totalConnections.get() >= maxPoolSize && waitingThreads > 0) {
            sendAlert("POOL_EXHAUSTED", 
                "Connection pool exhausted - all connections in use with threads waiting");
        }
        
        // Check for idle connection imbalance
        double idleRatio = (double) idleConnections.get() / totalConnections.get();
        if (idleRatio > 0.8 && totalConnections.get() > minIdle * 2) {
            sendAlert("EXCESSIVE_IDLE_CONNECTIONS", 
                String.format("Excessive idle connections: %.1f%% (%d idle out of %d total)", 
                    idleRatio * 100, idleConnections.get(), totalConnections.get()));
        }
        
        lastHealthCheck = Instant.now();
    }

    /**
     * Record metrics history for trend analysis
     */
    private void recordMetricsHistory() {
        String timestamp = Instant.now().toString();
        ConnectionPoolMetrics metrics = new ConnectionPoolMetrics(
            activeConnections.get(),
            idleConnections.get(),
            totalConnections.get(),
            threadsAwaitingConnection.get(),
            getPoolUsagePercent(),
            Instant.now()
        );
        
        metricsHistory.put(timestamp, metrics);
        
        // Keep only last 24 hours of metrics (assuming 30-second intervals)
        if (metricsHistory.size() > 2880) {
            String oldestKey = metricsHistory.keySet().iterator().next();
            metricsHistory.remove(oldestKey);
        }
    }

    /**
     * Send rate-limited alerts
     */
    private void sendAlert(String alertType, String message) {
        Instant lastAlert = alertHistory.get(alertType);
        
        // Rate limit alerts to once per 5 minutes
        if (lastAlert == null || Duration.between(lastAlert, Instant.now()).toMinutes() >= 5) {
            logger.warn("CONNECTION POOL ALERT [{}]: {}", alertType, message);
            alertHistory.put(alertType, Instant.now());
            
            // Record alert in performance monitoring
            performanceMonitoringService.recordConnectionLeak("connection_pool_" + alertType.toLowerCase());
        }
    }

    /**
     * Get connection pool health status
     */
    public Map<String, Object> getConnectionPoolHealth() {
        double usagePercent = getPoolUsagePercent();
        String healthStatus;
        
        if (usagePercent >= criticalThreshold || threadsAwaitingConnection.get() > 0) {
            healthStatus = "CRITICAL";
        } else if (usagePercent >= warningThreshold) {
            healthStatus = "WARNING";
        } else {
            healthStatus = "HEALTHY";
        }
        
        return Map.of(
            "status", healthStatus,
            "usage_percent", usagePercent,
            "active_connections", activeConnections.get(),
            "idle_connections", idleConnections.get(),
            "total_connections", totalConnections.get(),
            "threads_waiting", threadsAwaitingConnection.get(),
            "max_pool_size", maxPoolSize,
            "min_idle", minIdle,
            "last_health_check", lastHealthCheck,
            "recommendations", getConnectionPoolRecommendations()
        );
    }

    /**
     * Get connection pool optimization recommendations
     */
    public Map<String, Object> getConnectionPoolRecommendations() {
        Map<String, Object> recommendations = new HashMap<>();
        double usagePercent = getPoolUsagePercent();
        
        // Analyze usage patterns
        if (usagePercent > 90) {
            recommendations.put("increase_pool_size", 
                "Consider increasing maximum pool size from " + maxPoolSize + " to " + (maxPoolSize + 10));
        }
        
        if (threadsAwaitingConnection.get() > 0) {
            recommendations.put("connection_timeout", 
                "Threads are waiting for connections - check for connection leaks or increase pool size");
        }
        
        double idleRatio = (double) idleConnections.get() / Math.max(totalConnections.get(), 1);
        if (idleRatio > 0.8 && totalConnections.get() > minIdle * 2) {
            recommendations.put("reduce_idle_connections", 
                "Consider reducing minimum idle connections or implementing connection validation");
        }
        
        // Analyze historical trends
        if (metricsHistory.size() > 100) {
            analyzeHistoricalTrends(recommendations);
        }
        
        return recommendations;
    }

    /**
     * Analyze historical trends for recommendations
     */
    private void analyzeHistoricalTrends(Map<String, Object> recommendations) {
        // Calculate average usage over last hour
        Instant oneHourAgo = Instant.now().minus(Duration.ofHours(1));
        double avgUsage = metricsHistory.values().stream()
            .filter(m -> m.timestamp.isAfter(oneHourAgo))
            .mapToDouble(m -> m.usagePercent)
            .average()
            .orElse(0.0);
        
        if (avgUsage > 75) {
            recommendations.put("sustained_high_usage", 
                String.format("Sustained high usage over last hour: %.1f%% - consider scaling", avgUsage));
        }
        
        // Check for usage spikes
        long spikes = metricsHistory.values().stream()
            .filter(m -> m.timestamp.isAfter(oneHourAgo))
            .mapToLong(m -> m.usagePercent > 95 ? 1 : 0)
            .sum();
        
        if (spikes > 5) {
            recommendations.put("usage_spikes", 
                String.format("Detected %d usage spikes over 95%% in last hour - investigate load patterns", spikes));
        }
    }

    /**
     * Get detailed connection pool metrics
     */
    public Map<String, Object> getDetailedMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // Current metrics
        metrics.put("current", Map.of(
            "active_connections", activeConnections.get(),
            "idle_connections", idleConnections.get(),
            "total_connections", totalConnections.get(),
            "threads_waiting", threadsAwaitingConnection.get(),
            "usage_percent", getPoolUsagePercent(),
            "timestamp", Instant.now()
        ));
        
        // Configuration
        metrics.put("configuration", Map.of(
            "max_pool_size", maxPoolSize,
            "min_idle", minIdle,
            "warning_threshold", warningThreshold,
            "critical_threshold", criticalThreshold
        ));
        
        // Historical data (last 24 hours)
        metrics.put("historical_data", metricsHistory.values());
        
        // Health status
        metrics.put("health", getConnectionPoolHealth());
        
        return metrics;
    }

    /**
     * Force connection pool analysis
     */
    public void analyzeConnectionPool() {
        logger.info("Starting connection pool analysis");
        
        try {
            collectConnectionPoolMetrics();
            checkConnectionPoolHealth();
            
            Map<String, Object> health = getConnectionPoolHealth();
            logger.info("Connection pool analysis completed - Status: {}, Usage: {}%", 
                health.get("status"), health.get("usage_percent"));
                
        } catch (Exception e) {
            logger.error("Connection pool analysis failed: {}", e.getMessage());
        }
    }

    /**
     * Get connection pool statistics for the last period
     */
    public Map<String, Object> getConnectionPoolStatistics(Duration period) {
        Instant cutoff = Instant.now().minus(period);
        
        var recentMetrics = metricsHistory.values().stream()
            .filter(m -> m.timestamp.isAfter(cutoff))
            .toList();
        
        if (recentMetrics.isEmpty()) {
            return Map.of("error", "No metrics available for the specified period");
        }
        
        double avgUsage = recentMetrics.stream().mapToDouble(m -> m.usagePercent).average().orElse(0.0);
        double maxUsage = recentMetrics.stream().mapToDouble(m -> m.usagePercent).max().orElse(0.0);
        double minUsage = recentMetrics.stream().mapToDouble(m -> m.usagePercent).min().orElse(0.0);
        
        long totalWaitingThreads = recentMetrics.stream().mapToLong(m -> m.threadsWaiting).sum();
        
        return Map.of(
            "period_hours", period.toHours(),
            "sample_count", recentMetrics.size(),
            "average_usage_percent", avgUsage,
            "max_usage_percent", maxUsage,
            "min_usage_percent", minUsage,
            "total_waiting_threads", totalWaitingThreads,
            "usage_variance", calculateVariance(recentMetrics)
        );
    }

    /**
     * Calculate usage variance for stability analysis
     */
    private double calculateVariance(java.util.List<ConnectionPoolMetrics> metrics) {
        double mean = metrics.stream().mapToDouble(m -> m.usagePercent).average().orElse(0.0);
        double variance = metrics.stream()
            .mapToDouble(m -> Math.pow(m.usagePercent - mean, 2))
            .average()
            .orElse(0.0);
        return Math.sqrt(variance); // Return standard deviation
    }

    // Getter methods for metrics
    public double getActiveConnections() { return activeConnections.get(); }
    public double getIdleConnections() { return idleConnections.get(); }
    public double getTotalConnections() { return totalConnections.get(); }
    public double getThreadsAwaitingConnection() { return threadsAwaitingConnection.get(); }
    
    public double getPoolUsagePercent() {
        return totalConnections.get() > 0 ? 
            (double) activeConnections.get() / maxPoolSize * 100.0 : 0.0;
    }

    /**
     * Clear metrics history (for maintenance)
     */
    public void clearMetricsHistory() {
        metricsHistory.clear();
        alertHistory.clear();
        logger.info("Connection pool metrics history cleared");
    }

    /**
     * Data class for connection pool metrics
     */
    public static class ConnectionPoolMetrics {
        public final long activeConnections;
        public final long idleConnections;
        public final long totalConnections;
        public final long threadsWaiting;
        public final double usagePercent;
        public final Instant timestamp;

        public ConnectionPoolMetrics(long activeConnections, long idleConnections, 
                                   long totalConnections, long threadsWaiting, 
                                   double usagePercent, Instant timestamp) {
            this.activeConnections = activeConnections;
            this.idleConnections = idleConnections;
            this.totalConnections = totalConnections;
            this.threadsWaiting = threadsWaiting;
            this.usagePercent = usagePercent;
            this.timestamp = timestamp;
        }
    }
}