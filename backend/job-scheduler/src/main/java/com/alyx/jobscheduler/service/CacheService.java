package com.alyx.jobscheduler.service;

import com.alyx.jobscheduler.model.AnalysisJob;
import com.alyx.jobscheduler.model.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing Redis cache operations in the job scheduler.
 * Provides caching for frequently accessed data with intelligent invalidation strategies.
 */
@Service
public class CacheService {

    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    // Cache key prefixes
    private static final String JOB_STATUS_PREFIX = "job:status:";
    private static final String USER_JOBS_PREFIX = "user:jobs:";
    private static final String QUEUE_STATE_PREFIX = "queue:state:";
    private static final String ML_PREDICTION_PREFIX = "ml:prediction:";
    private static final String RESOURCE_ALLOCATION_PREFIX = "resource:allocation:";
    private static final String JOB_STATISTICS_PREFIX = "job:stats:";

    @Autowired
    public CacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Cache job status with automatic expiration
     */
    @Cacheable(value = "jobStatus", key = "#jobId.toString()")
    public JobStatus getJobStatus(UUID jobId) {
        // This method is used for cache annotation only
        // Actual implementation should be in JobSchedulerService
        return null;
    }

    /**
     * Cache user's active jobs
     */
    @Cacheable(value = "userJobs", key = "#userId")
    public List<AnalysisJob> getUserActiveJobs(String userId) {
        // This method is used for cache annotation only
        return null;
    }

    /**
     * Cache ML execution time predictions
     */
    @Cacheable(value = "mlPredictions", key = "#jobType + ':' + #parameters.hashCode()")
    public Long getPredictedExecutionTime(String jobType, Map<String, Object> parameters) {
        // This method is used for cache annotation only
        return null;
    }

    /**
     * Cache resource allocation information
     */
    @Cacheable(value = "resourceAllocation", key = "#resourceNode")
    public Map<String, Object> getResourceAllocation(String resourceNode) {
        // This method is used for cache annotation only
        return null;
    }

    /**
     * Cache job queue statistics
     */
    @Cacheable(value = "jobStatistics", key = "'queue:' + #queueType")
    public Map<String, Long> getQueueStatistics(String queueType) {
        // This method is used for cache annotation only
        return null;
    }

    /**
     * Invalidate job-related caches when job status changes
     */
    @Caching(evict = {
        @CacheEvict(value = "jobStatus", key = "#jobId.toString()"),
        @CacheEvict(value = "userJobs", key = "#userId"),
        @CacheEvict(value = "jobStatistics", allEntries = true)
    })
    public void invalidateJobCaches(UUID jobId, String userId) {
        logger.debug("Invalidated caches for job {} and user {}", jobId, userId);
    }

    /**
     * Invalidate resource allocation cache when resources change
     */
    @CacheEvict(value = "resourceAllocation", key = "#resourceNode")
    public void invalidateResourceCache(String resourceNode) {
        logger.debug("Invalidated resource cache for node {}", resourceNode);
    }

    /**
     * Invalidate all job statistics caches
     */
    @CacheEvict(value = "jobStatistics", allEntries = true)
    public void invalidateJobStatistics() {
        logger.debug("Invalidated all job statistics caches");
    }

    /**
     * Store frequently accessed data with custom TTL
     */
    public void cacheWithTtl(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl.getSeconds(), TimeUnit.SECONDS);
            logger.debug("Cached key {} with TTL {} seconds", key, ttl.getSeconds());
        } catch (Exception e) {
            logger.error("Failed to cache key {}: {}", key, e.getMessage());
        }
    }

    /**
     * Get cached value by key
     */
    public Object getCachedValue(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            logger.error("Failed to retrieve cached key {}: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * Check if key exists in cache
     */
    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            logger.error("Failed to check existence of key {}: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * Delete specific cache key
     */
    public void evict(String key) {
        try {
            redisTemplate.delete(key);
            logger.debug("Evicted cache key {}", key);
        } catch (Exception e) {
            logger.error("Failed to evict key {}: {}", key, e.getMessage());
        }
    }

    /**
     * Delete multiple cache keys by pattern
     */
    public void evictByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                logger.debug("Evicted {} keys matching pattern {}", keys.size(), pattern);
            }
        } catch (Exception e) {
            logger.error("Failed to evict keys by pattern {}: {}", pattern, e.getMessage());
        }
    }

    /**
     * Cache job execution metrics for performance monitoring
     */
    public void cacheJobMetrics(UUID jobId, Map<String, Object> metrics) {
        String key = "job:metrics:" + jobId.toString();
        cacheWithTtl(key, metrics, Duration.ofHours(24));
    }

    /**
     * Cache user session data for quick access
     */
    public void cacheUserSession(String userId, Map<String, Object> sessionData) {
        String key = "user:session:" + userId;
        cacheWithTtl(key, sessionData, Duration.ofMinutes(30));
    }

    /**
     * Get cache statistics for monitoring
     */
    public Map<String, Object> getCacheStatistics() {
        try {
            // Get Redis info
            Properties info = redisTemplate.getConnectionFactory().getConnection().info("memory");
            
            // Parse relevant metrics
            Map<String, Object> stats = new HashMap<>();
            stats.put("timestamp", Instant.now());
            stats.put("redis_info", info.toString());
            stats.put("connection_active", !redisTemplate.getConnectionFactory().getConnection().isClosed());
            
            return stats;
        } catch (Exception e) {
            logger.error("Failed to get cache statistics: {}", e.getMessage());
            Map<String, Object> errorStats = new HashMap<>();
            errorStats.put("error", e.getMessage());
            return errorStats;
        }
    }

    /**
     * Warm up cache with frequently accessed data
     */
    public void warmUpCache() {
        logger.info("Starting cache warm-up process");
        
        try {
            // Pre-load common job statistics
            cacheWithTtl("warmup:timestamp", Instant.now(), Duration.ofMinutes(5));
            
            logger.info("Cache warm-up completed successfully");
        } catch (Exception e) {
            logger.error("Cache warm-up failed: {}", e.getMessage());
        }
    }
}