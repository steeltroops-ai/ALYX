package com.alyx.gateway.service;

import com.alyx.gateway.model.UserRole;
import com.alyx.gateway.model.Permission;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

/**
 * JWT Service for token validation and user information extraction
 * 
 * Handles JWT token validation, parsing, and user information extraction
 * for authentication and authorization purposes.
 */
@Service
public class JwtService {

    @Value("${jwt.secret:alyxSecretKeyForJWTTokenValidationThatShouldBeAtLeast256BitsLong}")
    private String secretKey;

    @Value("${jwt.expiration:86400000}") // 24 hours in milliseconds
    private Long jwtExpiration;

    /**
     * Extract username from JWT token
     */
    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract user role from JWT token
     */
    public String extractUserRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    /**
     * Extract user role as enum from JWT token
     */
    public UserRole extractUserRoleEnum(String token) {
        String roleString = extractUserRole(token);
        return UserRole.fromString(roleString);
    }

    /**
     * Extract user permissions from JWT token
     */
    @SuppressWarnings("unchecked")
    public List<String> extractPermissions(String token) {
        return extractClaim(token, claims -> claims.get("permissions", List.class));
    }

    /**
     * Check if user has specific permission
     */
    public boolean hasPermission(String token, Permission permission) {
        UserRole userRole = extractUserRoleEnum(token);
        return userRole.hasPermission(permission);
    }

    /**
     * Extract organization from JWT token
     */
    public String extractOrganization(String token) {
        return extractClaim(token, claims -> claims.get("organization", String.class));
    }

    /**
     * Extract expiration date from JWT token
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extract a specific claim from JWT token
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extract all claims from JWT token
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Check if JWT token is expired
     */
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Validate JWT token
     */
    public Boolean isTokenValid(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get signing key for JWT validation
     */
    private SecretKey getSignInKey() {
        byte[] keyBytes = secretKey.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate JWT token with enhanced claims
     */
    public String generateToken(String userId, String role, String organization, List<String> permissions) {
        return Jwts.builder()
                .setSubject(userId)
                .claim("role", role)
                .claim("organization", organization)
                .claim("permissions", permissions)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generate JWT token for testing purposes (backward compatibility)
     */
    public String generateToken(String userId, String role) {
        return generateToken(userId, role, "ALYX_PHYSICS", List.of());
    }

    /**
     * Validate token and check if user has required role
     */
    public boolean validateTokenAndRole(String token, UserRole requiredRole) {
        if (!isTokenValid(token)) {
            return false;
        }
        
        UserRole userRole = extractUserRoleEnum(token);
        return userRole.getHierarchyLevel() >= requiredRole.getHierarchyLevel();
    }
}