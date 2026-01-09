package com.alyx.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for database configuration.
 * Verifies Neon PostgreSQL connection and configuration.
 */
@SpringBootTest
@ActiveProfiles("test")
class DatabaseConfigIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void shouldConnectToDatabase() throws SQLException {
        // Given the configured DataSource
        assertThat(dataSource).isNotNull();
        
        // When establishing a connection
        try (Connection connection = dataSource.getConnection()) {
            // Then connection should be valid
            assertThat(connection.isValid(5)).isTrue();
            assertThat(connection.getMetaData().getDatabaseProductName())
                .containsIgnoringCase("postgresql");
        }
    }

    @Test
    void shouldHaveCorrectDriverConfiguration() throws SQLException {
        // Given the configured DataSource
        try (Connection connection = dataSource.getConnection()) {
            // When checking driver metadata
            var metaData = connection.getMetaData();
            
            // Then should use PostgreSQL driver
            assertThat(metaData.getDriverName()).containsIgnoringCase("postgresql");
            assertThat(metaData.getDatabaseProductName()).containsIgnoringCase("postgresql");
        }
    }
}