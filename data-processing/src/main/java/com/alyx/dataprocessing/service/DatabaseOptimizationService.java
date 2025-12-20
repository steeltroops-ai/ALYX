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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for database query optimization and performance tuning.
 * Monitors query performance, suggests optimizations, and maintains database health.
 */
@Service
public class DatabaseOptimizationService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseOptimizationService.class);

    private final DataSource dataSource;
    private final PerformanceMonitoringService performanceMonitoringService;

    @Value("${alyx.data-processing.slow-query-threshold-ms:1000}")
    private long slowQueryThresholdMs;

    @Value("${alyx.data-processing.index-usage-threshold:0.1}")
    private double indexUsageThreshold;

    @Value("${alyx.data-processing.table-bloat-threshold:0.2}")
    private double tableBloatThreshold;

    // Query optimization tracking
    private final Map<String, QueryPerformanceMetrics> queryMetrics = new ConcurrentHashMap<>();
    private final Map<String, IndexRecommendation> indexRecommendations = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastOptimizationRun = new ConcurrentHashMap<>();

    @Autowired
    public DatabaseOptimizationService(DataSource dataSource,
                                     PerformanceMonitoringService performanceMonitoringService) {
        this.dataSource = dataSource;
        this.performanceMonitoringService = performanceMonitoringService;
    }

    /**
     * Scheduled database optimization analysis
     */
    @Scheduled(fixedRate = 1800000) // Every 30 minutes
    public void performOptimizationAnalysis() {
        logger.info("Starting database optimization analysis");
        
        try {
            analyzeQueryPerformance();
            analyzeIndexUsage();
            analyzeTableBloat();
            generateOptimizationRecommendations();
            
            logger.info("Database optimization analysis completed");
            
        } catch (Exception e) {
            logger.error("Database optimization analysis failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Analyze query performance using pg_stat_statements
     */
    public void analyzeQueryPerformance() {
        try (Connection connection = dataSource.getConnection()) {
            // Check if pg_stat_statements is available
            if (!isPgStatStatementsAvailable(connection)) {
                logger.warn("pg_stat_statements extension not available - limited query analysis");
                return;
            }

            String sql = """
                SELECT 
                    query,
                    calls,
                    total_exec_time,
                    mean_exec_time,
                    stddev_exec_time,
                    rows,
                    100.0 * shared_blks_hit / nullif(shared_blks_hit + shared_blks_read, 0) AS hit_percent
                FROM pg_stat_statements 
                WHERE calls > 10
                ORDER BY mean_exec_time DESC
                LIMIT 50
                """;

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String query = rs.getString("query");
                        long calls = rs.getLong("calls");
                        double totalTime = rs.getDouble("total_exec_time");
                        double meanTime = rs.getDouble("mean_exec_time");
                        double stddevTime = rs.getDouble("stddev_exec_time");
                        long rows = rs.getLong("rows");
                        double hitPercent = rs.getDouble("hit_percent");

                        QueryPerformanceMetrics metrics = new QueryPerformanceMetrics(
                            query, calls, totalTime, meanTime, stddevTime, rows, hitPercent
                        );
                        
                        String queryHash = String.valueOf(query.hashCode());
                        queryMetrics.put(queryHash, metrics);

                        // Identify problematic queries
                        if (meanTime > slowQueryThresholdMs) {
                            analyzeSlowQuery(query, metrics);
                        }
                        
                        if (hitPercent < 95.0) {
                            analyzeLowCacheHitQuery(query, metrics);
                        }
                    }
                }
            }
            
            logger.debug("Analyzed {} query performance metrics", queryMetrics.size());
            
        } catch (SQLException e) {
            logger.error("Failed to analyze query performance: {}", e.getMessage());
        }
    }

    /**
     * Analyze index usage and identify unused or missing indexes
     */
    public void analyzeIndexUsage() {
        try (Connection connection = dataSource.getConnection()) {
            // Analyze unused indexes
            analyzeUnusedIndexes(connection);
            
            // Analyze missing indexes based on sequential scans
            analyzeMissingIndexes(connection);
            
            // Analyze index efficiency
            analyzeIndexEfficiency(connection);
            
        } catch (SQLException e) {
            logger.error("Failed to analyze index usage: {}", e.getMessage());
        }
    }

    /**
     * Analyze unused indexes
     */
    private void analyzeUnusedIndexes(Connection connection) throws SQLException {
        String sql = """
            SELECT 
                schemaname,
                tablename,
                indexrelname,
                idx_scan,
                pg_size_pretty(pg_relation_size(indexrelid)) as size
            FROM pg_stat_user_indexes 
            WHERE idx_scan = 0 
            AND schemaname = 'public'
            AND indexrelname NOT LIKE '%_pkey'
            ORDER BY pg_relation_size(indexrelid) DESC
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("tablename");
                    String indexName = rs.getString("indexrelname");
                    String size = rs.getString("size");
                    
                    IndexRecommendation recommendation = new IndexRecommendation(
                        "DROP_UNUSED_INDEX",
                        tableName,
                        indexName,
                        "Index is never used and consumes space: " + size,
                        "HIGH"
                    );
                    
                    indexRecommendations.put(indexName, recommendation);
                    logger.debug("Found unused index: {} on table {} ({})", indexName, tableName, size);
                }
            }
        }
    }

    /**
     * Analyze missing indexes based on sequential scan patterns
     */
    private void analyzeMissingIndexes(Connection connection) throws SQLException {
        String sql = """
            SELECT 
                schemaname,
                tablename,
                seq_scan,
                seq_tup_read,
                idx_scan,
                idx_tup_fetch,
                n_tup_ins + n_tup_upd + n_tup_del as modifications
            FROM pg_stat_user_tables 
            WHERE schemaname = 'public'
            AND seq_scan > 1000
            ORDER BY seq_scan DESC
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("tablename");
                    long seqScan = rs.getLong("seq_scan");
                    long seqTupRead = rs.getLong("seq_tup_read");
                    long idxScan = rs.getLong("idx_scan");
                    long modifications = rs.getLong("modifications");
                    
                    // Calculate sequential scan ratio
                    double seqScanRatio = (double) seqScan / (seqScan + idxScan);
                    
                    if (seqScanRatio > 0.3 && seqScan > 1000) {
                        String recommendationKey = "missing_index_" + tableName;
                        IndexRecommendation recommendation = new IndexRecommendation(
                            "ADD_MISSING_INDEX",
                            tableName,
                            null,
                            String.format("High sequential scan ratio: %.2f%% (%d scans, %d tuples read)", 
                                seqScanRatio * 100, seqScan, seqTupRead),
                            seqScanRatio > 0.8 ? "HIGH" : "MEDIUM"
                        );
                        
                        indexRecommendations.put(recommendationKey, recommendation);
                        logger.debug("Potential missing index on table: {} (seq scan ratio: {:.2f}%)", 
                            tableName, seqScanRatio * 100);
                    }
                }
            }
        }
    }

    /**
     * Analyze index efficiency
     */
    private void analyzeIndexEfficiency(Connection connection) throws SQLException {
        String sql = """
            SELECT 
                schemaname,
                tablename,
                indexrelname,
                idx_scan,
                idx_tup_read,
                idx_tup_fetch,
                pg_size_pretty(pg_relation_size(indexrelid)) as size
            FROM pg_stat_user_indexes 
            WHERE schemaname = 'public'
            AND idx_scan > 0
            ORDER BY idx_scan DESC
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("tablename");
                    String indexName = rs.getString("indexrelname");
                    long idxScan = rs.getLong("idx_scan");
                    long idxTupRead = rs.getLong("idx_tup_read");
                    long idxTupFetch = rs.getLong("idx_tup_fetch");
                    
                    // Calculate index efficiency
                    double efficiency = idxTupRead > 0 ? (double) idxTupFetch / idxTupRead : 0.0;
                    
                    if (efficiency < indexUsageThreshold && idxScan > 100) {
                        String recommendationKey = "inefficient_index_" + indexName;
                        IndexRecommendation recommendation = new IndexRecommendation(
                            "OPTIMIZE_INDEX",
                            tableName,
                            indexName,
                            String.format("Low index efficiency: %.2f%% (scans: %d)", efficiency * 100, idxScan),
                            "MEDIUM"
                        );
                        
                        indexRecommendations.put(recommendationKey, recommendation);
                    }
                }
            }
        }
    }

    /**
     * Analyze table bloat and suggest maintenance
     */
    public void analyzeTableBloat() {
        try (Connection connection = dataSource.getConnection()) {
            String sql = """
                SELECT 
                    schemaname,
                    tablename,
                    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size,
                    pg_total_relation_size(schemaname||'.'||tablename) as size_bytes,
                    n_dead_tup,
                    n_live_tup,
                    CASE WHEN n_live_tup > 0 
                         THEN n_dead_tup::float / n_live_tup::float 
                         ELSE 0 END as bloat_ratio
                FROM pg_stat_user_tables 
                WHERE schemaname = 'public'
                ORDER BY n_dead_tup DESC
                """;

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String tableName = rs.getString("tablename");
                        String size = rs.getString("size");
                        long sizeBytes = rs.getLong("size_bytes");
                        long deadTuples = rs.getLong("n_dead_tup");
                        long liveTuples = rs.getLong("n_live_tup");
                        double bloatRatio = rs.getDouble("bloat_ratio");
                        
                        if (bloatRatio > tableBloatThreshold && deadTuples > 1000) {
                            String recommendationKey = "bloat_" + tableName;
                            IndexRecommendation recommendation = new IndexRecommendation(
                                "VACUUM_ANALYZE",
                                tableName,
                                null,
                                String.format("High table bloat: %.2f%% (%d dead tuples, size: %s)", 
                                    bloatRatio * 100, deadTuples, size),
                                bloatRatio > 0.5 ? "HIGH" : "MEDIUM"
                            );
                            
                            indexRecommendations.put(recommendationKey, recommendation);
                            logger.debug("Table bloat detected: {} (bloat ratio: {:.2f}%)", 
                                tableName, bloatRatio * 100);
                        }
                    }
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to analyze table bloat: {}", e.getMessage());
        }
    }

    /**
     * Generate comprehensive optimization recommendations
     */
    public void generateOptimizationRecommendations() {
        logger.info("Generating optimization recommendations");
        
        // Analyze query patterns for index suggestions
        for (QueryPerformanceMetrics metrics : queryMetrics.values()) {
            if (metrics.meanTime > slowQueryThresholdMs) {
                generateQueryOptimizationRecommendations(metrics);
            }
        }
        
        logger.info("Generated {} optimization recommendations", indexRecommendations.size());
    }

    /**
     * Generate recommendations for slow queries
     */
    private void generateQueryOptimizationRecommendations(QueryPerformanceMetrics metrics) {
        String query = metrics.query.toLowerCase();
        
        // Detect common optimization opportunities
        if (query.contains("where") && !query.contains("index")) {
            // Suggest index for WHERE clauses
            String recommendationKey = "query_optimization_" + metrics.query.hashCode();
            IndexRecommendation recommendation = new IndexRecommendation(
                "ADD_WHERE_INDEX",
                extractTableName(query),
                null,
                String.format("Slow query with WHERE clause: %.2f ms avg (calls: %d)", 
                    metrics.meanTime, metrics.calls),
                "HIGH"
            );
            indexRecommendations.put(recommendationKey, recommendation);
        }
        
        if (query.contains("order by") && !query.contains("index")) {
            // Suggest index for ORDER BY clauses
            String recommendationKey = "order_optimization_" + metrics.query.hashCode();
            IndexRecommendation recommendation = new IndexRecommendation(
                "ADD_ORDER_INDEX",
                extractTableName(query),
                null,
                String.format("Slow query with ORDER BY: %.2f ms avg (calls: %d)", 
                    metrics.meanTime, metrics.calls),
                "MEDIUM"
            );
            indexRecommendations.put(recommendationKey, recommendation);
        }
        
        if (query.contains("join") && metrics.meanTime > slowQueryThresholdMs * 2) {
            // Suggest join optimization
            String recommendationKey = "join_optimization_" + metrics.query.hashCode();
            IndexRecommendation recommendation = new IndexRecommendation(
                "OPTIMIZE_JOIN",
                extractTableName(query),
                null,
                String.format("Slow JOIN query: %.2f ms avg (calls: %d)", 
                    metrics.meanTime, metrics.calls),
                "HIGH"
            );
            indexRecommendations.put(recommendationKey, recommendation);
        }
    }

    /**
     * Analyze specific slow query for optimization opportunities
     */
    private void analyzeSlowQuery(String query, QueryPerformanceMetrics metrics) {
        logger.debug("Analyzing slow query: {} ms - {}", metrics.meanTime, 
            query.substring(0, Math.min(query.length(), 100)));
        
        // Record slow query for monitoring
        performanceMonitoringService.recordQueryExecution("slow_query", 
            Duration.ofMillis((long) metrics.meanTime), query);
    }

    /**
     * Analyze query with low cache hit ratio
     */
    private void analyzeLowCacheHitQuery(String query, QueryPerformanceMetrics metrics) {
        logger.debug("Low cache hit query: {:.2f}% - {}", metrics.hitPercent, 
            query.substring(0, Math.min(query.length(), 100)));
    }

    /**
     * Execute recommended optimizations (with caution)
     */
    public void executeOptimization(String recommendationKey, boolean dryRun) {
        IndexRecommendation recommendation = indexRecommendations.get(recommendationKey);
        if (recommendation == null) {
            logger.warn("Optimization recommendation not found: {}", recommendationKey);
            return;
        }
        
        if (dryRun) {
            logger.info("DRY RUN - Would execute: {}", recommendation);
            return;
        }
        
        try (Connection connection = dataSource.getConnection()) {
            switch (recommendation.type) {
                case "VACUUM_ANALYZE":
                    executeVacuumAnalyze(connection, recommendation.tableName);
                    break;
                case "DROP_UNUSED_INDEX":
                    if (recommendation.indexName != null) {
                        executeDropIndex(connection, recommendation.indexName);
                    }
                    break;
                default:
                    logger.info("Manual optimization required for: {}", recommendation);
            }
            
            lastOptimizationRun.put(recommendationKey, Instant.now());
            
        } catch (SQLException e) {
            logger.error("Failed to execute optimization {}: {}", recommendationKey, e.getMessage());
        }
    }

    /**
     * Execute VACUUM ANALYZE on a table
     */
    private void executeVacuumAnalyze(Connection connection, String tableName) throws SQLException {
        String sql = "VACUUM ANALYZE " + tableName;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.execute();
            logger.info("Executed VACUUM ANALYZE on table: {}", tableName);
        }
    }

    /**
     * Drop an unused index
     */
    private void executeDropIndex(Connection connection, String indexName) throws SQLException {
        String sql = "DROP INDEX CONCURRENTLY " + indexName;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.execute();
            logger.info("Dropped unused index: {}", indexName);
        }
    }

    /**
     * Get optimization report
     */
    public Map<String, Object> getOptimizationReport() {
        Map<String, Object> report = new HashMap<>();
        
        // Query performance summary
        Map<String, Object> queryStats = new HashMap<>();
        double avgQueryTime = queryMetrics.values().stream()
            .mapToDouble(m -> m.meanTime)
            .average()
            .orElse(0.0);
        
        long slowQueries = queryMetrics.values().stream()
            .mapToLong(m -> m.meanTime > slowQueryThresholdMs ? 1 : 0)
            .sum();
        
        queryStats.put("total_queries_analyzed", queryMetrics.size());
        queryStats.put("average_query_time_ms", avgQueryTime);
        queryStats.put("slow_queries_count", slowQueries);
        
        // Index recommendations summary
        Map<String, Long> recommendationsByType = new HashMap<>();
        for (IndexRecommendation rec : indexRecommendations.values()) {
            recommendationsByType.merge(rec.type, 1L, Long::sum);
        }
        
        report.put("query_performance", queryStats);
        report.put("recommendations_count", indexRecommendations.size());
        report.put("recommendations_by_type", recommendationsByType);
        report.put("recommendations", new ArrayList<>(indexRecommendations.values()));
        report.put("last_analysis", Instant.now());
        
        return report;
    }

    /**
     * Helper methods
     */
    private boolean isPgStatStatementsAvailable(Connection connection) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
            "SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'pg_stat_statements')")) {
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private String extractTableName(String query) {
        // Simple table name extraction - in production, use a proper SQL parser
        String[] parts = query.split("\\s+");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("from".equalsIgnoreCase(parts[i]) || "update".equalsIgnoreCase(parts[i]) || 
                "into".equalsIgnoreCase(parts[i])) {
                return parts[i + 1].replaceAll("[^a-zA-Z0-9_]", "");
            }
        }
        return "unknown";
    }

    /**
     * Clear optimization history
     */
    public void clearOptimizationHistory() {
        queryMetrics.clear();
        indexRecommendations.clear();
        lastOptimizationRun.clear();
        logger.info("Optimization history cleared");
    }

    /**
     * Data classes for tracking metrics and recommendations
     */
    public static class QueryPerformanceMetrics {
        public final String query;
        public final long calls;
        public final double totalTime;
        public final double meanTime;
        public final double stddevTime;
        public final long rows;
        public final double hitPercent;

        public QueryPerformanceMetrics(String query, long calls, double totalTime, 
                                     double meanTime, double stddevTime, long rows, double hitPercent) {
            this.query = query;
            this.calls = calls;
            this.totalTime = totalTime;
            this.meanTime = meanTime;
            this.stddevTime = stddevTime;
            this.rows = rows;
            this.hitPercent = hitPercent;
        }
    }

    public static class IndexRecommendation {
        public final String type;
        public final String tableName;
        public final String indexName;
        public final String description;
        public final String priority;

        public IndexRecommendation(String type, String tableName, String indexName, 
                                 String description, String priority) {
            this.type = type;
            this.tableName = tableName;
            this.indexName = indexName;
            this.description = description;
            this.priority = priority;
        }

        @Override
        public String toString() {
            return String.format("IndexRecommendation{type='%s', table='%s', index='%s', priority='%s', desc='%s'}", 
                type, tableName, indexName, priority, description);
        }
    }
}