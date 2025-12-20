package com.alyx.resourceoptimizer.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Resource Optimizer service
 * Checks Redis connectivity and resource allocation status
 */
@Component
public class ResourceOptimizerHealthIndicator implements HealthIndicator {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();
        
        try {
            // Check Redis connectivity
            checkRedis(builder);
            
            // Check resource allocation status
            checkResourceAllocation(builder);
            
            return builder.up()
                    .withDetail("status", "Resource optimizer operational")
                    .build();
                    
        } catch (Exception e) {
            return builder.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    private void checkRedis(Health.Builder builder) {
        try {
            redisTemplate.opsForValue().set("health:resource-optimizer", "ok");
            String result = (String) redisTemplate.opsForValue().get("health:resource-optimizer");
            if ("ok".equals(result)) {
                builder.withDetail("redis", "UP");
                redisTemplate.delete("health:resource-optimizer");
            } else {
                throw new RuntimeException("Redis health check failed");
            }
        } catch (Exception e) {
            throw new RuntimeException("Redis connectivity issue: " + e.getMessage());
        }
    }

    private void checkResourceAllocation(Health.Builder builder) {
        try {
            // Check if resource allocation service is responsive
            // This would typically check if the ML model is loaded and responsive
            long allocatedCores = getTotalAllocatedCores();
            long availableCores = getTotalAvailableCores();
            
            builder.withDetail("allocated_cores", allocatedCores)
                   .withDetail("available_cores", availableCores)
                   .withDetail("utilization_percentage", 
                       availableCores > 0 ? (allocatedCores * 100.0 / availableCores) : 0);
                       
        } catch (Exception e) {
            throw new RuntimeException("Resource allocation check failed: " + e.getMessage());
        }
    }

    private long getTotalAllocatedCores() {
        // Mock implementation - in real scenario, this would query actual resource usage
        return 150L;
    }

    private long getTotalAvailableCores() {
        // Mock implementation - in real scenario, this would query GRID resources
        return 1000L;
    }
}