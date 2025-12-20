package com.alyx.jobscheduler.service;

import com.alyx.jobscheduler.model.JobStatus;
import com.alyx.jobscheduler.model.JobParameters;
import com.alyx.jobscheduler.repository.AnalysisJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for warming up caches with frequently accessed data.
 * Runs on application startup and periodically to maintain cache performance.
 */
@Service
public class CacheWarmupService {

    private static final Logger logger = LoggerFactory.getLogger(CacheWarmupService.class);

    private final AnalysisJobRepository jobRepository;
    private final CacheService cacheService;
    private final ExecutionTimePredictionService predictionService;

    @Autowired
    public CacheWarmupService(AnalysisJobRepository jobRepository,
                             CacheService cacheService,
                             ExecutionTimePredictionService predictionService) {
        this.jobRepository = jobRepository;
        this.cacheService = cacheService;
        this.predictionService = predictionService;
    }

    /**
     * Warm up caches on application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void warmupOnStartup() {
        logger.info("Starting cache warmup on application startup");
        
        CompletableFuture.runAsync(() -> {
            try {
                warmupJobStatistics();
                warmupCommonQueries();
                warmupMLPredictions();
                logger.info("Cache warmup completed successfully");
            } catch (Exception e) {
                logger.error("Cache warmup failed: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Scheduled cache warmup - runs every hour
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void scheduledWarmup() {
        logger.debug("Starting scheduled cache warmup");
        
        try {
            warmupJobStatistics();
            warmupActiveUserData();
            logger.debug("Scheduled cache warmup completed");
        } catch (Exception e) {
            logger.error("Scheduled cache warmup failed: {}", e.getMessage());
        }
    }

    /**
     * Warm up job statistics cache
     */
    private void warmupJobStatistics() {
        logger.debug("Warming up job statistics cache");
        
        try {
            // Cache queue statistics for different job types
            List<String> jobTypes = List.of("COLLISION_ANALYSIS", "TRACK_RECONSTRUCTION", "EVENT_FILTERING");
            
            for (String jobType : jobTypes) {
                Map<String, Long> stats = Map.of(
                    "total", jobRepository.countByJobType(jobType),
                    "queued", jobRepository.countByJobTypeAndStatus(jobType, JobStatus.QUEUED),
                    "running", jobRepository.countByJobTypeAndStatus(jobType, JobStatus.RUNNING),
                    "completed_today", jobRepository.countCompletedToday(jobType)
                );
                
                cacheService.cacheWithTtl("job:stats:" + jobType, stats, 
                    java.time.Duration.ofHours(1));
            }
            
            // Cache overall queue statistics
            Map<String, Long> overallStats = Map.of(
                "total_active", jobRepository.countActiveJobs(),
                "total_queued", jobRepository.countByStatus(JobStatus.QUEUED),
                "total_running", jobRepository.countByStatus(JobStatus.RUNNING)
            );
            
            cacheService.cacheWithTtl("job:stats:overall", overallStats, 
                java.time.Duration.ofMinutes(15));
                
        } catch (Exception e) {
            logger.error("Failed to warm up job statistics: {}", e.getMessage());
        }
    }

    /**
     * Warm up common query results
     */
    private void warmupCommonQueries() {
        logger.debug("Warming up common query results");
        
        try {
            // Pre-load recent jobs for active users
            Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
            List<String> activeUsers = jobRepository.findActiveUsersInPeriod(oneDayAgo);
            
            for (String userId : activeUsers) {
                // This will populate the cache through the @Cacheable annotation
                // when the actual service methods are called
                cacheService.cacheWithTtl("user:active:" + userId, true, 
                    java.time.Duration.ofMinutes(30));
            }
            
        } catch (Exception e) {
            logger.error("Failed to warm up common queries: {}", e.getMessage());
        }
    }

    /**
     * Warm up ML prediction cache with common job parameters
     */
    private void warmupMLPredictions() {
        logger.debug("Warming up ML prediction cache");
        
        try {
            // Common job parameter combinations for prediction caching
            List<JobParameters> commonParameters = List.of(
                new JobParameters("COLLISION_ANALYSIS", "Collision analysis job", 1000, 10.0, false),
                new JobParameters("COLLISION_ANALYSIS", "Collision analysis job", 5000, 10.0, false),
                new JobParameters("TRACK_RECONSTRUCTION", "Track reconstruction job", 1000, 5.0, false),
                new JobParameters("EVENT_FILTERING", "Event filtering job", 10000, 1.0, false)
            );
            
            for (JobParameters params : commonParameters) {
                try {
                    String jobType = params.getJobName();
                    Instant prediction = predictionService.predictExecutionTime(params);
                    
                    String cacheKey = "ml:prediction:" + jobType + ":" + params.hashCode();
                    cacheService.cacheWithTtl(cacheKey, prediction, 
                        java.time.Duration.ofHours(6));
                        
                } catch (Exception e) {
                    logger.warn("Failed to cache ML prediction for params {}: {}", params, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to warm up ML predictions: {}", e.getMessage());
        }
    }

    /**
     * Warm up active user data
     */
    private void warmupActiveUserData() {
        logger.debug("Warming up active user data");
        
        try {
            // Get users who have submitted jobs in the last 24 hours
            Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
            List<String> activeUsers = jobRepository.findActiveUsersInPeriod(oneDayAgo);
            
            for (String userId : activeUsers) {
                // Cache user permissions (simplified - in real system would check actual permissions)
                Map<String, Boolean> permissions = Map.of(
                    "canSubmitJobs", true,
                    "canCancelJobs", true,
                    "canViewAllJobs", userId.startsWith("admin_"),
                    "canSubmitHighPriority", userId.startsWith("admin_") || userId.startsWith("power_")
                );
                
                cacheService.cacheWithTtl("user:permissions:" + userId, permissions, 
                    java.time.Duration.ofHours(2));
                    
                // Cache user session data
                Map<String, Object> sessionData = Map.of(
                    "userId", userId,
                    "lastActivity", Instant.now(),
                    "activeJobCount", jobRepository.countActiveJobsByUser(userId)
                );
                
                cacheService.cacheUserSession(userId, sessionData);
            }
            
        } catch (Exception e) {
            logger.error("Failed to warm up active user data: {}", e.getMessage());
        }
    }

    /**
     * Warm up resource allocation cache
     */
    public void warmupResourceAllocation() {
        logger.debug("Warming up resource allocation cache");
        
        try {
            // Common resource nodes (in a real system, these would be discovered dynamically)
            List<String> resourceNodes = List.of("grid-node-01", "grid-node-02", "grid-node-03");
            
            for (String node : resourceNodes) {
                Map<String, Object> allocation = Map.of(
                    "node", node,
                    "totalCores", 32,
                    "availableCores", 16,
                    "totalMemoryMB", 64000,
                    "availableMemoryMB", 32000,
                    "activeJobs", 4,
                    "lastUpdated", Instant.now()
                );
                
                cacheService.cacheWithTtl("resource:allocation:" + node, allocation, 
                    java.time.Duration.ofMinutes(10));
            }
            
        } catch (Exception e) {
            logger.error("Failed to warm up resource allocation cache: {}", e.getMessage());
        }
    }

    /**
     * Clear and refresh all caches
     */
    public void refreshAllCaches() {
        logger.info("Refreshing all caches");
        
        try {
            // Clear existing caches
            cacheService.evictByPattern("job:stats:*");
            cacheService.evictByPattern("user:*");
            cacheService.evictByPattern("ml:prediction:*");
            cacheService.evictByPattern("resource:allocation:*");
            
            // Warm up again
            warmupJobStatistics();
            warmupCommonQueries();
            warmupMLPredictions();
            warmupActiveUserData();
            warmupResourceAllocation();
            
            logger.info("All caches refreshed successfully");
            
        } catch (Exception e) {
            logger.error("Failed to refresh caches: {}", e.getMessage());
        }
    }

    /**
     * Get cache warmup status
     */
    public Map<String, Object> getWarmupStatus() {
        return Map.of(
            "lastWarmup", Instant.now(),
            "jobStatsWarmed", cacheService.exists("job:stats:overall"),
            "mlPredictionsWarmed", cacheService.exists("ml:prediction:COLLISION_ANALYSIS:*"),
            "resourceAllocationWarmed", cacheService.exists("resource:allocation:grid-node-01"),
            "cacheStatistics", cacheService.getCacheStatistics()
        );
    }
}