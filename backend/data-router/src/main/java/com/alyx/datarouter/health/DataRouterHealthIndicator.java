package com.alyx.datarouter.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Health indicator for Data Router service
 * Checks database, Redis, and Kafka connectivity
 */
@Component
public class DataRouterHealthIndicator implements HealthIndicator {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();
        
        try {
            // Check database connectivity
            checkDatabase(builder);
            
            // Check Redis connectivity
            checkRedis(builder);
            
            // Check Kafka connectivity
            checkKafka(builder);
            
            return builder.up()
                    .withDetail("status", "All systems operational")
                    .build();
                    
        } catch (Exception e) {
            return builder.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    private void checkDatabase(Health.Builder builder) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(5)) {
                builder.withDetail("database", "UP");
            } else {
                throw new SQLException("Database connection validation failed");
            }
        }
    }

    private void checkRedis(Health.Builder builder) {
        try {
            redisTemplate.opsForValue().set("health:check", "ok");
            String result = (String) redisTemplate.opsForValue().get("health:check");
            if ("ok".equals(result)) {
                builder.withDetail("redis", "UP");
                redisTemplate.delete("health:check");
            } else {
                throw new RuntimeException("Redis health check failed");
            }
        } catch (Exception e) {
            throw new RuntimeException("Redis connectivity issue: " + e.getMessage());
        }
    }

    private void checkKafka(Health.Builder builder) {
        try {
            // Simple check - if we can get metadata, Kafka is accessible
            kafkaTemplate.getProducerFactory().createProducer().partitionsFor("health-check");
            builder.withDetail("kafka", "UP");
        } catch (Exception e) {
            throw new RuntimeException("Kafka connectivity issue: " + e.getMessage());
        }
    }
}