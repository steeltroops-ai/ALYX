package com.alyx.gateway.config;

import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for database schema constraint enforcement.
 * **Property 19: Database Constraint Enforcement**
 * **Validates: Requirements 10.3**
 * 
 * Feature: neon-auth-system, Property 19: Database Constraint Enforcement
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class DatabaseSchemaConstraintPropertyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("alyx_test")
            .withUsername("test_user")
            .withPassword("test_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        
        // Disable services not needed for this test
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.gateway.discovery.locator.enabled", () -> "false");
        registry.add("spring.redis.host", () -> "localhost");
        registry.add("spring.redis.port", () -> "6379");
    }

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        // Ensure clean state for each test
        try (Connection connection = dataSource.getConnection()) {
            // Clean up any test data
            connection.prepareStatement("DELETE FROM auth_audit_log").execute();
            connection.prepareStatement("DELETE FROM users").execute();
            // Don't delete roles as they contain seed data
        }
    }

    @Property(tries = 5)
    @Tag("Feature: neon-auth-system, Property 19: Database Constraint Enforcement")
    void emailUniquenessConstraintShouldBeEnforced(@ForAll("validUserData") UserTestData userData1,
                                                   @ForAll("validUserData") UserTestData userData2) throws SQLException {
        // Given two users with the same email
        String sharedEmail = "test@example.com";
        userData1.email = sharedEmail;
        userData2.email = sharedEmail;

        try (Connection connection = dataSource.getConnection()) {
            // When inserting the first user
            insertUser(connection, userData1);
            
            // Then inserting a second user with the same email should fail
            assertThatThrownBy(() -> insertUser(connection, userData2))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("duplicate key value violates unique constraint");
        }
    }

    @Property(tries = 5)
    @Tag("Feature: neon-auth-system, Property 19: Database Constraint Enforcement")
    void emailFormatConstraintShouldBeEnforced(@ForAll("invalidEmails") String invalidEmail,
                                               @ForAll("validUserData") UserTestData userData) throws SQLException {
        // Given a user with invalid email format
        userData.email = invalidEmail;

        try (Connection connection = dataSource.getConnection()) {
            // When attempting to insert user with invalid email
            // Then should fail with constraint violation
            assertThatThrownBy(() -> insertUser(connection, userData))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("check constraint");
        }
    }

    @Property(tries = 5)
    @Tag("Feature: neon-auth-system, Property 19: Database Constraint Enforcement")
    void notNullConstraintsShouldBeEnforced(@ForAll("validUserData") UserTestData userData) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            // Test email not null constraint
            userData.email = null;
            assertThatThrownBy(() -> insertUser(connection, userData))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("null value in column \"email\"");

            // Reset and test password_hash not null constraint
            userData.email = "test@example.com";
            userData.passwordHash = null;
            assertThatThrownBy(() -> insertUser(connection, userData))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("null value in column \"password_hash\"");

            // Reset and test first_name not null constraint
            userData.passwordHash = "hashedPassword123";
            userData.firstName = null;
            assertThatThrownBy(() -> insertUser(connection, userData))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("null value in column \"first_name\"");
        }
    }

    @Property(tries = 5)
    @Tag("Feature: neon-auth-system, Property 19: Database Constraint Enforcement")
    void foreignKeyConstraintShouldBeEnforced(@ForAll("validUserData") UserTestData userData) throws SQLException {
        // Given a user with non-existent role_id
        userData.roleId = UUID.randomUUID(); // Random UUID that doesn't exist

        try (Connection connection = dataSource.getConnection()) {
            // When attempting to insert user with invalid role_id
            // Then should fail with foreign key constraint violation
            assertThatThrownBy(() -> insertUser(connection, userData))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("foreign key constraint");
        }
    }

    @Property(tries = 5)
    @Tag("Feature: neon-auth-system, Property 19: Database Constraint Enforcement")
    void checkConstraintsShouldBeEnforced(@ForAll("validUserData") UserTestData userData) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            // Test failed_login_attempts non-negative constraint
            userData.failedLoginAttempts = -1;
            assertThatThrownBy(() -> insertUser(connection, userData))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("check constraint");

            // Reset and test empty string constraints
            userData.failedLoginAttempts = 0;
            userData.firstName = "   "; // Only whitespace
            assertThatThrownBy(() -> insertUser(connection, userData))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("check constraint");
        }
    }

    @Property(tries = 5)
    @Tag("Feature: neon-auth-system, Property 19: Database Constraint Enforcement")
    void validDataShouldBeInsertedSuccessfully(@ForAll("validUserData") UserTestData userData) throws SQLException {
        // Given valid user data with existing role
        UUID validRoleId = getValidRoleId();
        userData.roleId = validRoleId;
        userData.email = "valid" + System.nanoTime() + "@example.com"; // Ensure uniqueness

        try (Connection connection = dataSource.getConnection()) {
            // When inserting valid user data
            UUID userId = insertUser(connection, userData);
            
            // Then user should be successfully inserted
            assertThat(userId).isNotNull();
            
            // And user should be retrievable from database
            String selectSql = "SELECT email, first_name, last_name, role_id FROM users WHERE id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(selectSql)) {
                stmt.setObject(1, userId);
                ResultSet rs = stmt.executeQuery();
                
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("email")).isEqualTo(userData.email);
                assertThat(rs.getString("first_name")).isEqualTo(userData.firstName);
                assertThat(rs.getString("last_name")).isEqualTo(userData.lastName);
                assertThat(rs.getObject("role_id")).isEqualTo(userData.roleId);
            }
        }
    }

    // Helper methods and data providers

    private UUID insertUser(Connection connection, UserTestData userData) throws SQLException {
        String sql = """
            INSERT INTO users (email, password_hash, first_name, last_name, organization, role_id, 
                              is_active, email_verified, failed_login_attempts) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, userData.email);
            stmt.setString(2, userData.passwordHash);
            stmt.setString(3, userData.firstName);
            stmt.setString(4, userData.lastName);
            stmt.setString(5, userData.organization);
            stmt.setObject(6, userData.roleId);
            stmt.setBoolean(7, userData.isActive);
            stmt.setBoolean(8, userData.emailVerified);
            stmt.setInt(9, userData.failedLoginAttempts);
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return (UUID) rs.getObject("id");
            }
            throw new SQLException("Failed to insert user");
        }
    }

    private UUID getValidRoleId() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String sql = "SELECT id FROM roles LIMIT 1";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return (UUID) rs.getObject("id");
                }
                throw new SQLException("No roles found in database");
            }
        }
    }

    @Provide
    Arbitrary<UserTestData> validUserData() {
        return Arbitraries.create(() -> new UserTestData(
            "test" + System.nanoTime() + "@example.com", // Valid unique email
            "hashedPassword123", // Password hash
            "John", // First name
            "Doe", // Last name
            "CERN", // Organization
            UUID.randomUUID(), // Role ID (will be overridden with valid one)
            true, // Is active
            false, // Email verified
            0 // Failed login attempts
        ));
    }

    @Provide
    Arbitrary<String> invalidEmails() {
        return Arbitraries.oneOf(
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(10), // No @ symbol
            Arbitraries.just("invalid@"), // Missing domain
            Arbitraries.just("@invalid.com"), // Missing local part
            Arbitraries.just("invalid@.com"), // Empty domain part
            Arbitraries.just("invalid@com"), // Missing TLD
            Arbitraries.just(""), // Empty string
            Arbitraries.just("   ") // Only whitespace
        );
    }

    // Test data class
    static class UserTestData {
        String email;
        String passwordHash;
        String firstName;
        String lastName;
        String organization;
        UUID roleId;
        boolean isActive;
        boolean emailVerified;
        int failedLoginAttempts;

        UserTestData(String email, String passwordHash, String firstName, String lastName, 
                    String organization, UUID roleId, boolean isActive, boolean emailVerified, 
                    int failedLoginAttempts) {
            this.email = email;
            this.passwordHash = passwordHash;
            this.firstName = firstName;
            this.lastName = lastName;
            this.organization = organization;
            this.roleId = roleId;
            this.isActive = isActive;
            this.emailVerified = emailVerified;
            this.failedLoginAttempts = failedLoginAttempts;
        }
    }
}