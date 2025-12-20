package com.alyx.collaboration.health;

import com.alyx.collaboration.service.CollaborationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator for Collaboration Service
 * Validates: Requirements 4.1, 4.5, 6.4
 */
@Component
public class CollaborationHealthIndicator implements HealthIndicator {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private CollaborationService collaborationService;

    @Override
    public Health health() {
        try {
            // Check Redis connectivity
            if (!isRedisHealthy()) {
                return Health.down()
                    .withDetail("redis", "Connection failed")
                    .build();
            }

            // Check active sessions
            int activeSessions = collaborationService.getActiveSessionsCount();
            
            // Check WebSocket connections
            int activeConnections = collaborationService.getActiveConnectionsCount();
            
            return Health.up()
                .withDetail("redis", "Connected")
                .withDetail("activeSessions", activeSessions)
                .withDetail("activeConnections", activeConnections)
                .withDetail("service", "Collaboration service running normally")
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .withException(e)
                .build();
        }
    }

    private boolean isRedisHealthy() {
        try {
            redisTemplate.opsForValue().set("health-check", "ping");
            String result = (String) redisTemplate.opsForValue().get("health-check");
            redisTemplate.delete("health-check");
            return "ping".equals(result);
        } catch (Exception e) {
            return false;
        }
    }
}