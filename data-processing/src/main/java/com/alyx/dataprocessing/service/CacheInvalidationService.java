package com.alyx.dataprocessing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service responsible for intelligent cache invalidation strategies.
 * Implements various invalidation patterns including time-based, event-driven, and dependency-based invalidation.
 */
@Service
public class CacheInvalidationService {

    private static final Logger logger = LoggerFactory.getLogger(CacheInvalidationService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final DataCacheService dataCacheService;
    private final PerformanceMonitoringService performanceMonitoringService;

    // Cache dependency tracking
    private final Map<String, Set<String>> cacheDependencies = new ConcurrentHashMap<>();
    private final Map<String, Instant> cacheCreationTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheAccessCounts = new ConcurrentHashMap<>();

    // Cache key patterns
    private static final String COLLISION_EVENT_PATTERN = "collision:event:*";
    private static final String DETECTOR_HITS_PATTERN = "detector:hits:*";
    private static final String PARTICLE_TRACKS_PATTERN = "particle:tracks:*";
    private static final String SPATIAL_QUERY_PATTERN = "spatial:query:*";
    private static final String STATISTICS_PATTERN = "stats:*";
    private static final String MATERIALIZED_VIEW_PATTERN = "mv:*";

    @Autowired
    public CacheInvalidationService(RedisTemplate<String, Object> redisTemplate,
                                  DataCacheService dataCacheService,
                                  PerformanceMonitoringService performanceMonitoringService) {
        this.redisTemplate = redisTemplate;
        this.dataCacheService = dataCacheService;
        this.performanceMonitoringService = performanceMonitoringService;
    }

    /**
     * Invalidate cache when collision event is updated
     */
    public void invalidateCollisionEventCache(UUID eventId) {
        Instant start = Instant.now();
        
        try {
            // Invalidate specific event cache
            String eventKey = "collision:event:" + eventId.toString();
            redisTemplate.delete(eventKey);
            
            // Invalidate related caches
            invalidateRelatedCaches(eventId);
            
            // Invalidate dependent statistics
            invalidateEventStatistics();
            
            logger.debug("Invalidated collision event cache for: {}", eventId);
            
        } catch (Exception e) {
            logger.error("Failed to invalidate collision event cache for {}: {}", eventId, e.getMessage());
        } finally {
            Duration duration = Duration.between(start, Instant.now());
            performanceMonitoringService.recordCacheOperation("invalidate_collision_event", duration, false);
        }
    }

    /**
     * Invalidate related caches for an event
     */
    private void invalidateRelatedCaches(UUID eventId) {
        String eventIdStr = eventId.toString();
        
        // Invalidate detector hits cache
        String detectorHitsKey = "detector:hits:" + eventIdStr;
        redisTemplate.delete(detectorHitsKey);
        
        // Invalidate particle tracks cache
        String particleTracksKey = "particle:tracks:" + eventIdStr;
        redisTemplate.delete(particleTracksKey);
        
        // Invalidate any spatial queries that might include this event
        invalidateSpatialQueriesContaining(eventId);
    }

    /**
     * Invalidate spatial queries that might contain the given event
     */
    private void invalidateSpatialQueriesContaining(UUID eventId) {
        try {
            Set<String> spatialQueryKeys = redisTemplate.keys(SPATIAL_QUERY_PATTERN);
            if (spatialQueryKeys != null) {
                for (String key : spatialQueryKeys) {
                    @SuppressWarnings("unchecked")
                    List<UUID> cachedResults = (List<UUID>) redisTemplate.opsForValue().get(key);
                    if (cachedResults != null && cachedResults.contains(eventId)) {
                        redisTemplate.delete(key);
                        logger.debug("Invalidated spatial query cache: {}", key);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to invalidate spatial queries: {}", e.getMessage());
        }
    }

    /**
     * Invalidate event statistics caches
     */
    @CacheEvict(value = {"eventStatistics", "materializedViews"}, allEntries = true)
    public void invalidateEventStatistics() {
        try {
            // Clear statistics caches
            clearCacheByPattern(STATISTICS_PATTERN);
            
            // Clear materialized view caches
            clearCacheByPattern(MATERIALIZED_VIEW_PATTERN);
            
            logger.debug("Invalidated event statistics caches");
            
        } catch (Exception e) {
            logger.error("Failed to invalidate event statistics: {}", e.getMessage());
        }
    }

    /**
     * Invalidate detector-related caches
     */
    public void invalidateDetectorCache(String detectorId) {
        Instant start = Instant.now();
        
        try {
            // Invalidate detector-specific statistics
            String detectorStatsKey = "stats:detector:" + detectorId;
            redisTemplate.delete(detectorStatsKey);
            
            // Invalidate detector performance cache
            clearCacheByPattern("stats:detector:" + detectorId + "*");
            
            // Invalidate materialized views that include detector data
            invalidateDetectorMaterializedViews();
            
            logger.debug("Invalidated detector cache for: {}", detectorId);
            
        } catch (Exception e) {
            logger.error("Failed to invalidate detector cache for {}: {}", detectorId, e.getMessage());
        } finally {
            Duration duration = Duration.between(start, Instant.now());
            performanceMonitoringService.recordCacheOperation("invalidate_detector", duration, false);
        }
    }

    /**
     * Invalidate materialized views related to detector performance
     */
    private void invalidateDetectorMaterializedViews() {
        List<String> detectorViews = List.of(
            "mv:detector_performance_summary",
            "mv:detector_statistics_daily"
        );
        
        for (String viewKey : detectorViews) {
            redisTemplate.delete(viewKey);
        }
    }

    /**
     * Time-based cache invalidation for stale data
     */
    public void invalidateStaleCache(Duration maxAge) {
        Instant cutoff = Instant.now().minus(maxAge);
        
        try {
            // Get all cache keys
            Set<String> allKeys = redisTemplate.keys("*");
            if (allKeys == null) return;
            
            int invalidated = 0;
            for (String key : allKeys) {
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                if (ttl != null && ttl > 0) {
                    // Calculate creation time based on TTL
                    Instant creationTime = cacheCreationTimes.get(key);
                    if (creationTime != null && creationTime.isBefore(cutoff)) {
                        redisTemplate.delete(key);
                        cacheCreationTimes.remove(key);
                        invalidated++;
                    }
                }
            }
            
            logger.info("Invalidated {} stale cache entries older than {}", invalidated, maxAge);
            
        } catch (Exception e) {
            logger.error("Failed to invalidate stale cache: {}", e.getMessage());
        }
    }

    /**
     * Invalidate cache based on data freshness requirements
     */
    public void invalidateByFreshness(String cacheType, Duration maxFreshness) {
        String pattern = getCachePatternForType(cacheType);
        
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys == null) return;
            
            int invalidated = 0;
            for (String key : keys) {
                Instant creationTime = cacheCreationTimes.get(key);
                if (creationTime != null && 
                    Duration.between(creationTime, Instant.now()).compareTo(maxFreshness) > 0) {
                    redisTemplate.delete(key);
                    cacheCreationTimes.remove(key);
                    invalidated++;
                }
            }
            
            logger.debug("Invalidated {} {} cache entries based on freshness", invalidated, cacheType);
            
        } catch (Exception e) {
            logger.error("Failed to invalidate cache by freshness for {}: {}", cacheType, e.getMessage());
        }
    }

    /**
     * Dependency-based cache invalidation
     */
    public void invalidateDependentCaches(String sourceKey) {
        Set<String> dependentKeys = cacheDependencies.get(sourceKey);
        if (dependentKeys == null || dependentKeys.isEmpty()) {
            return;
        }
        
        try {
            for (String dependentKey : dependentKeys) {
                redisTemplate.delete(dependentKey);
                cacheCreationTimes.remove(dependentKey);
                logger.debug("Invalidated dependent cache: {}", dependentKey);
                
                // Recursively invalidate dependencies
                invalidateDependentCaches(dependentKey);
            }
            
            logger.debug("Invalidated {} dependent caches for: {}", dependentKeys.size(), sourceKey);
            
        } catch (Exception e) {
            logger.error("Failed to invalidate dependent caches for {}: {}", sourceKey, e.getMessage());
        }
    }

    /**
     * Register cache dependency
     */
    public void registerCacheDependency(String sourceKey, String dependentKey) {
        cacheDependencies.computeIfAbsent(sourceKey, k -> ConcurrentHashMap.newKeySet()).add(dependentKey);
        logger.debug("Registered cache dependency: {} -> {}", sourceKey, dependentKey);
    }

    /**
     * Intelligent cache warming after invalidation
     */
    public void warmCacheAfterInvalidation(String cacheType) {
        try {
            switch (cacheType) {
                case "collision_events":
                    dataCacheService.cacheRecentEvents();
                    break;
                case "detector_statistics":
                    dataCacheService.cacheDetectorStatistics();
                    break;
                case "materialized_views":
                    dataCacheService.cacheMaterializedViews();
                    break;
                case "spatial_queries":
                    dataCacheService.cacheSpatialQueries();
                    break;
                case "event_aggregations":
                    dataCacheService.cacheEventAggregations();
                    break;
                default:
                    logger.debug("No warming strategy defined for cache type: {}", cacheType);
            }
            
            logger.debug("Cache warming completed for: {}", cacheType);
            
        } catch (Exception e) {
            logger.error("Failed to warm cache for {}: {}", cacheType, e.getMessage());
        }
    }

    /**
     * Selective cache invalidation based on access patterns
     */
    public void invalidateLeastAccessedCache(int maxCacheSize) {
        try {
            Set<String> allKeys = redisTemplate.keys("*");
            if (allKeys == null || allKeys.size() <= maxCacheSize) {
                return;
            }
            
            // Sort keys by access count (ascending)
            List<String> sortedKeys = allKeys.stream()
                .sorted((k1, k2) -> Long.compare(
                    cacheAccessCounts.getOrDefault(k1, 0L),
                    cacheAccessCounts.getOrDefault(k2, 0L)
                ))
                .toList();
            
            // Remove least accessed entries
            int toRemove = allKeys.size() - maxCacheSize;
            for (int i = 0; i < toRemove && i < sortedKeys.size(); i++) {
                String key = sortedKeys.get(i);
                redisTemplate.delete(key);
                cacheAccessCounts.remove(key);
                cacheCreationTimes.remove(key);
            }
            
            logger.info("Removed {} least accessed cache entries", toRemove);
            
        } catch (Exception e) {
            logger.error("Failed to invalidate least accessed cache: {}", e.getMessage());
        }
    }

    /**
     * Bulk invalidation for data consistency
     */
    public void bulkInvalidateForConsistency(List<UUID> eventIds) {
        Instant start = Instant.now();
        
        try {
            // Invalidate all event-related caches
            for (UUID eventId : eventIds) {
                invalidateCollisionEventCache(eventId);
            }
            
            // Invalidate all statistics and aggregations
            invalidateEventStatistics();
            
            // Warm critical caches
            warmCacheAfterInvalidation("collision_events");
            warmCacheAfterInvalidation("event_aggregations");
            
            logger.info("Bulk invalidation completed for {} events", eventIds.size());
            
        } catch (Exception e) {
            logger.error("Failed bulk invalidation: {}", e.getMessage());
        } finally {
            Duration duration = Duration.between(start, Instant.now());
            performanceMonitoringService.recordCacheOperation("bulk_invalidate", duration, false);
        }
    }

    /**
     * Get cache invalidation statistics
     */
    public Map<String, Object> getInvalidationStatistics() {
        try {
            Set<String> allKeys = redisTemplate.keys("*");
            long totalKeys = allKeys != null ? allKeys.size() : 0;
            
            return Map.of(
                "total_cache_keys", totalKeys,
                "tracked_dependencies", cacheDependencies.size(),
                "tracked_creation_times", cacheCreationTimes.size(),
                "tracked_access_counts", cacheAccessCounts.size(),
                "cache_patterns", Map.of(
                    "collision_events", countKeysByPattern(COLLISION_EVENT_PATTERN),
                    "detector_hits", countKeysByPattern(DETECTOR_HITS_PATTERN),
                    "particle_tracks", countKeysByPattern(PARTICLE_TRACKS_PATTERN),
                    "spatial_queries", countKeysByPattern(SPATIAL_QUERY_PATTERN),
                    "statistics", countKeysByPattern(STATISTICS_PATTERN),
                    "materialized_views", countKeysByPattern(MATERIALIZED_VIEW_PATTERN)
                ),
                "timestamp", Instant.now()
            );
        } catch (Exception e) {
            logger.error("Failed to get invalidation statistics: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Helper methods
     */
    private void clearCacheByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                logger.debug("Cleared {} cache entries matching pattern: {}", keys.size(), pattern);
            }
        } catch (Exception e) {
            logger.error("Failed to clear cache pattern {}: {}", pattern, e.getMessage());
        }
    }

    private String getCachePatternForType(String cacheType) {
        return switch (cacheType) {
            case "collision_events" -> COLLISION_EVENT_PATTERN;
            case "detector_hits" -> DETECTOR_HITS_PATTERN;
            case "particle_tracks" -> PARTICLE_TRACKS_PATTERN;
            case "spatial_queries" -> SPATIAL_QUERY_PATTERN;
            case "statistics" -> STATISTICS_PATTERN;
            case "materialized_views" -> MATERIALIZED_VIEW_PATTERN;
            default -> "*";
        };
    }

    private long countKeysByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            logger.error("Failed to count keys for pattern {}: {}", pattern, e.getMessage());
            return 0;
        }
    }

    /**
     * Track cache access for LRU invalidation
     */
    public void trackCacheAccess(String key) {
        cacheAccessCounts.merge(key, 1L, Long::sum);
        cacheCreationTimes.putIfAbsent(key, Instant.now());
    }

    /**
     * Emergency cache clear (for critical issues)
     */
    public void emergencyCacheClear() {
        logger.warn("Performing emergency cache clear");
        
        try {
            dataCacheService.invalidateAllCaches();
            cacheDependencies.clear();
            cacheCreationTimes.clear();
            cacheAccessCounts.clear();
            
            logger.warn("Emergency cache clear completed");
            
        } catch (Exception e) {
            logger.error("Emergency cache clear failed: {}", e.getMessage());
        }
    }
}