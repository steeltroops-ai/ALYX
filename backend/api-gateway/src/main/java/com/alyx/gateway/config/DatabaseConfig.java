package com.alyx.gateway.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * Database configuration for Neon PostgreSQL integration.
 * Provides optimized connection pooling and JPA configuration.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.alyx.gateway.repository")
@EnableJpaAuditing
@EnableTransactionManagement
public class DatabaseConfig {

    /**
     * Primary DataSource configuration for Neon PostgreSQL.
     * Uses HikariCP for optimal connection pooling performance.
     */
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariConfig hikariConfig() {
        return new HikariConfig();
    }

    /**
     * Production-optimized DataSource for Neon PostgreSQL.
     * Configured for high-performance authentication operations.
     */
    @Bean
    @Primary
    @Profile("!test")
    public DataSource dataSource(HikariConfig hikariConfig) {
        // Additional Neon-specific optimizations
        hikariConfig.addDataSourceProperty("ApplicationName", "ALYX-Auth-Service");
        hikariConfig.addDataSourceProperty("reWriteBatchedInserts", "true");
        hikariConfig.addDataSourceProperty("prepareThreshold", "1");
        hikariConfig.addDataSourceProperty("preparedStatementCacheQueries", "256");
        hikariConfig.addDataSourceProperty("preparedStatementCacheSizeMiB", "5");
        hikariConfig.addDataSourceProperty("databaseMetadataCacheFields", "65536");
        hikariConfig.addDataSourceProperty("databaseMetadataCacheFieldsMiB", "5");
        
        // SSL configuration for Neon
        hikariConfig.addDataSourceProperty("sslmode", "require");
        hikariConfig.addDataSourceProperty("sslcert", "");
        hikariConfig.addDataSourceProperty("sslkey", "");
        hikariConfig.addDataSourceProperty("sslrootcert", "");
        
        return new HikariDataSource(hikariConfig);
    }
}