package com.alyx.gateway.service;

import com.alyx.gateway.model.UserRole;
import com.alyx.gateway.model.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JWT Service
 */
class JwtServiceTest {

    private JwtService jwtService;
    private final String testSecret = "testSecretKeyForJWTTokenValidationThatShouldBeAtLeast256BitsLong";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", testSecret);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86400000L);
    }

    @Test
    void testGenerateAndValidateToken() {
        // Given
        String userId = "test-user-123";
        String role = "PHYSICIST";
        String organization = "CERN";
        List<String> permissions = List.of("SUBMIT_JOBS", "READ_DATA");

        // When
        String token = jwtService.generateToken(userId, role, organization, permissions);

        // Then
        assertNotNull(token);
        assertTrue(jwtService.isTokenValid(token));
        assertEquals(userId, jwtService.extractUserId(token));
        assertEquals(role, jwtService.extractUserRole(token));
        assertEquals(organization, jwtService.extractOrganization(token));
    }

    @Test
    void testExtractUserRoleEnum() {
        // Given
        String token = jwtService.generateToken("user123", "ADMIN", "ALYX", List.of());

        // When
        UserRole role = jwtService.extractUserRoleEnum(token);

        // Then
        assertEquals(UserRole.ADMIN, role);
    }

    @Test
    void testHasPermission() {
        // Given
        String physicistToken = jwtService.generateToken("physicist", "PHYSICIST", "CERN", List.of());
        String adminToken = jwtService.generateToken("admin", "ADMIN", "ALYX", List.of());

        // When & Then
        assertTrue(jwtService.hasPermission(physicistToken, Permission.SUBMIT_JOBS));
        assertTrue(jwtService.hasPermission(physicistToken, Permission.READ_DATA));
        assertFalse(jwtService.hasPermission(physicistToken, Permission.USER_MANAGEMENT));

        assertTrue(jwtService.hasPermission(adminToken, Permission.USER_MANAGEMENT));
        assertTrue(jwtService.hasPermission(adminToken, Permission.SUBMIT_JOBS));
    }

    @Test
    void testValidateTokenAndRole() {
        // Given
        String physicistToken = jwtService.generateToken("physicist", "PHYSICIST", "CERN", List.of());
        String analystToken = jwtService.generateToken("analyst", "ANALYST", "FERMILAB", List.of());

        // When & Then
        assertTrue(jwtService.validateTokenAndRole(physicistToken, UserRole.ANALYST));
        assertTrue(jwtService.validateTokenAndRole(physicistToken, UserRole.PHYSICIST));
        assertFalse(jwtService.validateTokenAndRole(physicistToken, UserRole.ADMIN));

        assertTrue(jwtService.validateTokenAndRole(analystToken, UserRole.ANALYST));
        assertFalse(jwtService.validateTokenAndRole(analystToken, UserRole.PHYSICIST));
    }

    @Test
    void testInvalidToken() {
        // Given
        String invalidToken = "invalid.token.here";

        // When & Then
        assertFalse(jwtService.isTokenValid(invalidToken));
        assertThrows(Exception.class, () -> jwtService.extractUserId(invalidToken));
    }

    @Test
    void testBackwardCompatibilityToken() {
        // Given
        String userId = "test-user";
        String role = "PHYSICIST";

        // When
        String token = jwtService.generateToken(userId, role);

        // Then
        assertNotNull(token);
        assertTrue(jwtService.isTokenValid(token));
        assertEquals(userId, jwtService.extractUserId(token));
        assertEquals(role, jwtService.extractUserRole(token));
        assertEquals("ALYX_PHYSICS", jwtService.extractOrganization(token));
    }
}