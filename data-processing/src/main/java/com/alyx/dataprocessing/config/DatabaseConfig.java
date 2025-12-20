package com.alyx.dataprocessing.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Database configuration with HikariCP connection pooling and PostGIS support.
 * Optimized for high-concurrency access and spatial data operations.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.alyx.dataprocessing.repository")
@EnableTransactionManagement
public class DatabaseConfig {
    
    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/alyx}")
    private String jdbcUrl;
    
    @Value("${spring.datasource.username:alyx}")
    private String username;
    
    @Value("${spring.datasource.password:alyx}")
    private String password;
    
    @Value("${spring.datasource.hikari.maximum-pool-size:50}")
    private int maximumPoolSize;
    
    @Value("${spring.datasource.hikari.minimum-idle:10}")
    private int minimumIdle;
    
    @Value("${spring.datasource.hikari.connection-timeout:30000}")
    private long connectionTimeout;
    
    @Value("${spring.datasource.hikari.idle-timeout:600000}")
    private long idleTimeout;
    
    @Value("${spring.datasource.hikari.max-lifetime:1800000}")
    private long maxLifetime;
    
    @Value("${spring.datasource.hikari.leak-detection-threshold:60000}")
    private long leakDetectionThreshold;
    
    /**
     * HikariCP DataSource configuration optimized for high-concurrency access.
     * Configured to handle 1000+ concurrent connections efficiently.
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        
        // Basic connection settings
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        
        // Pool sizing for high concurrency
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        
        // Connection timing
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);
        config.setLeakDetectionThreshold(leakDetectionThreshold);
        
        // Performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        // PostgreSQL specific optimizations
        config.addDataSourceProperty("tcpKeepAlive", "true");
        config.addDataSourceProperty("socketTimeout", "30");
        config.addDataSourceProperty("loginTimeout", "10");
        config.addDataSourceProperty("connectTimeout", "10");
        
        // Pool name for monitoring
        config.setPoolName("AlyxHikariPool");
        
        return new HikariDataSource(config);
    }
    
    /**
     * JPA EntityManagerFactory with Hibernate and PostGIS support.
     */
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.alyx.dataprocessing.model");
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        
        Properties properties = new Properties();
        
        // Hibernate properties
        properties.setProperty("hibernate.dialect", "org.hibernate.spatial.dialect.postgis.PostgisPG95Dialect");
        properties.setProperty("hibernate.hbm2ddl.auto", "validate");
        properties.setProperty("hibernate.show_sql", "false");
        properties.setProperty("hibernate.format_sql", "false");
        
        // Performance optimizations
        properties.setProperty("hibernate.jdbc.batch_size", "50");
        properties.setProperty("hibernate.order_inserts", "true");
        properties.setProperty("hibernate.order_updates", "true");
        properties.setProperty("hibernate.jdbc.batch_versioned_data", "true");
        
        // Connection handling
        properties.setProperty("hibernate.connection.provider_disables_autocommit", "true");
        properties.setProperty("hibernate.query.fail_on_pagination_over_collection_fetch", "true");
        properties.setProperty("hibernate.query.in_clause_parameter_padding", "true");
        
        // Statistics and monitoring
        properties.setProperty("hibernate.generate_statistics", "true");
        properties.setProperty("hibernate.session.events.log.LOG_QUERIES_SLOWER_THAN_MS", "1000");
        
        // JSON support
        properties.setProperty("hibernate.type_contributors", "io.hypersistence.utils.hibernate.type.json.JsonTypeContributor");
        
        em.setJpaProperties(properties);
        
        return em;
    }
    
    /**
     * Transaction manager for JPA operations.
     */
    @Bean
    public PlatformTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory.getObject());
        return transactionManager;
    }
}