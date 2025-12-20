package com.alyx.dataprocessing.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis cache configuration for ALYX data processing service.
 * Optimized for collision event data and spatial query caching.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${alyx.data-processing.cache-ttl-seconds:3600}")
    private long defaultTtlSeconds;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisHost, redisPort);
        factory.setValidateConnection(true);
        return factory;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values with type information
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
            objectMapper.getPolymorphicTypeValidator(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Configure JSON serializer for cache values
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
            objectMapper.getPolymorphicTypeValidator(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        
        // Default cache configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(defaultTtlSeconds))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
            .disableCachingNullValues();

        // Cache-specific configurations with different TTLs optimized for data processing
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Collision events cache - medium TTL for event data
        cacheConfigurations.put("collisionEvents", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        
        // Detector hits cache - shorter TTL for detailed data
        cacheConfigurations.put("detectorHits", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        
        // Particle tracks cache - shorter TTL for reconstruction data
        cacheConfigurations.put("particleTracks", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        
        // Spatial queries cache - longer TTL for expensive computations
        cacheConfigurations.put("spatialQueries", defaultConfig.entryTtl(Duration.ofHours(2)));
        
        // Event statistics cache - long TTL for aggregated data
        cacheConfigurations.put("eventStatistics", defaultConfig.entryTtl(Duration.ofHours(4)));
        
        // Materialized views cache - very long TTL, manually refreshed
        cacheConfigurations.put("materializedViews", defaultConfig.entryTtl(Duration.ofHours(12)));
        
        // Detector performance cache - medium TTL for monitoring data
        cacheConfigurations.put("detectorPerformance", defaultConfig.entryTtl(Duration.ofHours(1)));
        
        // Run statistics cache - long TTL for run-level aggregations
        cacheConfigurations.put("runStatistics", defaultConfig.entryTtl(Duration.ofHours(6)));
        
        // Energy distribution cache - long TTL for physics analysis
        cacheConfigurations.put("energyDistribution", defaultConfig.entryTtl(Duration.ofHours(8)));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }
}