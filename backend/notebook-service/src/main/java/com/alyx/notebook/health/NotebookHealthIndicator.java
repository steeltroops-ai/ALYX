package com.alyx.notebook.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Health indicator for Notebook service
 * Checks database connectivity and notebook execution environment
 */
@Component
public class NotebookHealthIndicator implements HealthIndicator {

    @Autowired
    private DataSource dataSource;

    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();
        
        try {
            // Check database connectivity
            checkDatabase(builder);
            
            // Check notebook execution environment
            checkNotebookEnvironment(builder);
            
            return builder.up()
                    .withDetail("status", "Notebook service operational")
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

    private void checkNotebookEnvironment(Health.Builder builder) {
        try {
            // Check if notebook execution environment is available
            int activeNotebooks = getActiveNotebookCount();
            int maxNotebooks = getMaxNotebookCapacity();
            
            builder.withDetail("active_notebooks", activeNotebooks)
                   .withDetail("max_capacity", maxNotebooks)
                   .withDetail("utilization_percentage", 
                       maxNotebooks > 0 ? (activeNotebooks * 100.0 / maxNotebooks) : 0);
                       
        } catch (Exception e) {
            throw new RuntimeException("Notebook environment check failed: " + e.getMessage());
        }
    }

    private int getActiveNotebookCount() {
        // Mock implementation - in real scenario, this would query active notebook sessions
        return 25;
    }

    private int getMaxNotebookCapacity() {
        // Mock implementation - in real scenario, this would be configured based on resources
        return 100;
    }
}