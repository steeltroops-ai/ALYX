package com.alyx.dataprocessing.service;

import com.alyx.dataprocessing.model.CollisionEvent;
import com.alyx.dataprocessing.model.DetectorHit;
import com.alyx.dataprocessing.model.ParticleTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service for caching frequently accessed collision event data and detector information.
 * Optimizes performance for spatial queries and large dataset operations.
 */
@Service
@EnableCaching
public class DataCacheService {

    private static final Logger logger = LoggerFactory.getLogger(DataCacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final DataSource dataSource;

    @Value("${alyx.data-processing.cache-ttl-seconds:3600}")
    private long defaultCacheTtl;

    // Cache key prefixes
    private static final String COLLISION_EVENT_PREFIX = "collision:event:";
    private static final String DETECTOR_HITS_PREFIX = "detector:hits:";
    private static final String PARTICLE_TRACKS_PREFIX = "particle:tracks:";
    private static final String SPATIAL_QUERY_PREFIX = "spatial:query:";
    private static final String STATISTICS_PREFIX = "stats:";
    private static final String MATERIALIZED_VIEW_PREFIX = "mv:";

    @Autowired
    public DataCacheService(RedisTemplate<String, Object> redisTemplate, DataSource dataSource) {
        this.redisTemplate = redisTemplate;
        this.dataSource = dataSource;
    }

    /**
     * Cache collision event by ID
     */
    @Cacheable(value = "collisionEvents", key = "#eventId.toString()")
    public CollisionEvent getCachedCollisionEvent(UUID eventId) {
        // This method is used for cache annotation only
        // Actual implementation should be in the repository/service layer
        return null;
    }

    /**
     * Cache detector hits for an event
     */
    @Cacheable(value = "detectorHits", key = "#eventId.toString()")
    public List<DetectorHit> getCachedDetectorHits(UUID eventId) {
        // This method is used for cache annotation only
        return null;
    }

    /**
     * Cache particle tracks for an event
     */
    @Cacheable(value = "particleTracks", key = "#eventId.toString()")
    public List<ParticleTrack> getCachedParticleTracks(UUID eventId) {
        // This method is used for cache annotation only
        return null;
    }

    /**
     * Cache spatial query results
     */
    @Cacheable(value = "spatialQueries", key = "#queryHash")
    public List<UUID> getCachedSpatialQuery(String queryHash) {
        // This method is used for cache annotation only
        return null;
    }

    /**
     * Cache collision event statistics
     */
    @Cacheable(value = "eventStatistics", key = "#period")
    public Map<String, Object> getCachedEventStatistics(String period) {
        // This method is used for cache annotation only
        return null;
    }

    /**
     * Invalidate collision event cache
     */
    @CacheEvict(value = "collisionEvents", key = "#eventId.toString()")
    public void evictCollisionEvent(UUID eventId) {
        logger.debug("Evicted collision event cache for: {}", eventId);
    }

    /**
     * Invalidate all event-related caches for a specific event
     */
    @CacheEvict(value = {"collisionEvents", "detectorHits", "particleTracks"}, key = "#eventId.toString()")
    public void evictAllEventCaches(UUID eventId) {
        logger.debug("Evicted all event caches for: {}", eventId);
    }

    /**
     * Cache frequently accessed collision events
     */
    public void cacheRecentEvents() {
        logger.debug("Caching recent collision events");
        
        try (Connection connection = dataSource.getConnection()) {
            String sql = """
                SELECT event_id, timestamp, center_of_mass_energy, run_number, event_number
                FROM collision_events 
                WHERE timestamp >= ? 
                ORDER BY timestamp DESC 
                LIMIT 1000
                """;
                
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, Instant.now().minus(1, ChronoUnit.HOURS));
                
                try (ResultSet rs = stmt.executeQuery()) {
                    int cached = 0;
                    while (rs.next()) {
                        UUID eventId = UUID.fromString(rs.getString("event_id"));
                        Map<String, Object> eventData = Map.of(
                            "eventId", eventId,
                            "timestamp", rs.getTimestamp("timestamp").toInstant(),
                            "centerOfMassEnergy", rs.getDouble("center_of_mass_energy"),
                            "runNumber", rs.getLong("run_number"),
                            "eventNumber", rs.getLong("event_number")
                        );
                        
                        String key = COLLISION_EVENT_PREFIX + eventId.toString();
                        redisTemplate.opsForValue().set(key, eventData, defaultCacheTtl, TimeUnit.SECONDS);
                        cached++;
                    }
                    logger.debug("Cached {} recent collision events", cached);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to cache recent events: {}", e.getMessage());
        }
    }

    /**
     * Cache detector performance statistics
     */
    public void cacheDetectorStatistics() {
        logger.debug("Caching detector statistics");
        
        try (Connection connection = dataSource.getConnection()) {
            String sql = """
                SELECT detector_id, 
                       COUNT(*) as hit_count,
                       AVG(energy_deposit) as avg_energy,
                       MAX(energy_deposit) as max_energy,
                       AVG(signal_amplitude) as avg_amplitude
                FROM detector_hits 
                WHERE hit_time >= ? 
                GROUP BY detector_id
                """;
                
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setObject(1, Instant.now().minus(24, ChronoUnit.HOURS));
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String detectorId = rs.getString("detector_id");
                        Map<String, Object> stats = Map.of(
                            "detectorId", detectorId,
                            "hitCount", rs.getLong("hit_count"),
                            "avgEnergy", rs.getDouble("avg_energy"),
                            "maxEnergy", rs.getDouble("max_energy"),
                            "avgAmplitude", rs.getDouble("avg_amplitude"),
                            "lastUpdated", Instant.now()
                        );
                        
                        String key = STATISTICS_PREFIX + "detector:" + detectorId;
                        redisTemplate.opsForValue().set(key, stats, defaultCacheTtl, TimeUnit.SECONDS);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to cache detector statistics: {}", e.getMessage());
        }
    }

    /**
     * Cache materialized view results
     */
    public void cacheMaterializedViews() {
        logger.debug("Caching materialized view results");
        
        try (Connection connection = dataSource.getConnection()) {
            // Cache daily collision summary
            String dailySummarySQL = "SELECT * FROM daily_collision_summary ORDER BY day DESC LIMIT 30";
            cacheQueryResult("daily_collision_summary", dailySummarySQL, connection);
            
            // Cache job statistics summary
            String jobStatsSQL = "SELECT * FROM job_statistics_summary WHERE hour >= NOW() - INTERVAL '24 hours'";
            cacheQueryResult("job_statistics_summary", jobStatsSQL, connection);
            
            // Cache detector performance summary
            String detectorPerfSQL = "SELECT * FROM detector_performance_summary WHERE day >= NOW() - INTERVAL '7 days'";
            cacheQueryResult("detector_performance_summary", detectorPerfSQL, connection);
            
        } catch (SQLException e) {
            logger.error("Failed to cache materialized views: {}", e.getMessage());
        }
    }

    /**
     * Cache spatial query results for common patterns
     */
    public void cacheSpatialQueries() {
        logger.debug("Caching common spatial queries");
        
        try (Connection connection = dataSource.getConnection()) {
            // Common spatial queries that are expensive to compute
            List<String> spatialQueries = List.of(
                "SELECT event_id FROM collision_events WHERE ST_DWithin(collision_vertex, ST_Point(0, 0), 1000)",
                "SELECT event_id FROM collision_events WHERE ST_X(collision_vertex) BETWEEN -100 AND 100 AND ST_Y(collision_vertex) BETWEEN -100 AND 100"
            );
            
            for (String query : spatialQueries) {
                try (PreparedStatement stmt = connection.prepareStatement(query)) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        List<UUID> results = new java.util.ArrayList<>();
                        while (rs.next()) {
                            results.add(UUID.fromString(rs.getString("event_id")));
                        }
                        
                        String queryHash = String.valueOf(query.hashCode());
                        String key = SPATIAL_QUERY_PREFIX + queryHash;
                        redisTemplate.opsForValue().set(key, results, defaultCacheTtl, TimeUnit.SECONDS);
                        
                        logger.debug("Cached spatial query result with {} events", results.size());
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to cache spatial queries: {}", e.getMessage());
        }
    }

    /**
     * Helper method to cache query results
     */
    private void cacheQueryResult(String viewName, String sql, Connection connection) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                List<Map<String, Object>> results = new java.util.ArrayList<>();
                
                while (rs.next()) {
                    Map<String, Object> row = new java.util.HashMap<>();
                    int columnCount = rs.getMetaData().getColumnCount();
                    
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = rs.getMetaData().getColumnName(i);
                        Object value = rs.getObject(i);
                        row.put(columnName, value);
                    }
                    results.add(row);
                }
                
                String key = MATERIALIZED_VIEW_PREFIX + viewName;
                redisTemplate.opsForValue().set(key, results, defaultCacheTtl, TimeUnit.SECONDS);
                
                logger.debug("Cached materialized view {} with {} rows", viewName, results.size());
            }
        }
    }

    /**
     * Get cached materialized view data
     */
    public List<Map<String, Object>> getCachedMaterializedView(String viewName) {
        try {
            String key = MATERIALIZED_VIEW_PREFIX + viewName;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = (List<Map<String, Object>>) redisTemplate.opsForValue().get(key);
            return result;
        } catch (Exception e) {
            logger.error("Failed to get cached materialized view {}: {}", viewName, e.getMessage());
            return null;
        }
    }

    /**
     * Cache collision event aggregations
     */
    public void cacheEventAggregations() {
        logger.debug("Caching event aggregations");
        
        try (Connection connection = dataSource.getConnection()) {
            // Energy distribution
            String energyDistSQL = """
                SELECT 
                    FLOOR(center_of_mass_energy / 10) * 10 as energy_bin,
                    COUNT(*) as event_count
                FROM collision_events 
                WHERE timestamp >= NOW() - INTERVAL '24 hours'
                GROUP BY FLOOR(center_of_mass_energy / 10)
                ORDER BY energy_bin
                """;
            cacheQueryResult("energy_distribution_24h", energyDistSQL, connection);
            
            // Run statistics
            String runStatsSQL = """
                SELECT 
                    run_number,
                    COUNT(*) as event_count,
                    AVG(center_of_mass_energy) as avg_energy,
                    MIN(timestamp) as run_start,
                    MAX(timestamp) as run_end
                FROM collision_events 
                WHERE timestamp >= NOW() - INTERVAL '7 days'
                GROUP BY run_number
                ORDER BY run_number DESC
                """;
            cacheQueryResult("run_statistics_7d", runStatsSQL, connection);
            
        } catch (SQLException e) {
            logger.error("Failed to cache event aggregations: {}", e.getMessage());
        }
    }

    /**
     * Invalidate all caches
     */
    public void invalidateAllCaches() {
        logger.info("Invalidating all data caches");
        
        try {
            // Clear cache by patterns
            clearCacheByPattern(COLLISION_EVENT_PREFIX + "*");
            clearCacheByPattern(DETECTOR_HITS_PREFIX + "*");
            clearCacheByPattern(PARTICLE_TRACKS_PREFIX + "*");
            clearCacheByPattern(SPATIAL_QUERY_PREFIX + "*");
            clearCacheByPattern(STATISTICS_PREFIX + "*");
            clearCacheByPattern(MATERIALIZED_VIEW_PREFIX + "*");
            
            logger.info("All data caches invalidated");
        } catch (Exception e) {
            logger.error("Failed to invalidate caches: {}", e.getMessage());
        }
    }

    /**
     * Clear cache entries by pattern
     */
    private void clearCacheByPattern(String pattern) {
        try {
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                logger.debug("Cleared {} cache entries matching pattern: {}", keys.size(), pattern);
            }
        } catch (Exception e) {
            logger.error("Failed to clear cache pattern {}: {}", pattern, e.getMessage());
        }
    }

    /**
     * Get cache statistics
     */
    public Map<String, Object> getCacheStatistics() {
        try {
            long collisionEventCount = redisTemplate.keys(COLLISION_EVENT_PREFIX + "*").size();
            long detectorHitCount = redisTemplate.keys(DETECTOR_HITS_PREFIX + "*").size();
            long spatialQueryCount = redisTemplate.keys(SPATIAL_QUERY_PREFIX + "*").size();
            long statisticsCount = redisTemplate.keys(STATISTICS_PREFIX + "*").size();
            
            return Map.of(
                "collision_events_cached", collisionEventCount,
                "detector_hits_cached", detectorHitCount,
                "spatial_queries_cached", spatialQueryCount,
                "statistics_cached", statisticsCount,
                "last_updated", Instant.now()
            );
        } catch (Exception e) {
            logger.error("Failed to get cache statistics: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }
}