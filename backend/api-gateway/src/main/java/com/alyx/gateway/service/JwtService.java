package com.alyx.gateway.service;

import com.alyx.gateway.model.User;
import com.alyx.gateway.model.UserRole;
import com.alyx.gateway.model.Permission;
import com.alyx.gateway.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * JWT Service for token validation and user information extraction
 * 
 * Handles JWT token validation, parsing, and user information extraction
 * for authentication and authorization purposes.
 */
@Service
public class JwtService {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RolePermissionService rolePermissionService;

    @Value("${jwt.secret:alyxSecretKeyForJWTTokenValidationThatShouldBeAtLeast256BitsLong}")
    private String secretKey;

    @Value("${jwt.expiration:86400000}") // 24 hours in milliseconds
    private Long jwtExpiration;

    @Value("${jwt.refresh-expiration:604800000}") // 7 days in milliseconds
    private Long refreshExpiration;

    /**
     * Extract user ID from JWT token
     */
    public UUID extractUserId(String token) {
        String userIdString = extractClaim(token, Claims::getSubject);
        return UUID.fromString(userIdString);
    }

    /**
     * Extract user email from JWT token
     */
    public String extractUserEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
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
    public Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Validate JWT token with database user verification and token invalidation check
     */
    public Boolean isTokenValid(String token) {
        try {
            if (isTokenExpired(token)) {
                return false;
            }
            
            // Verify user still exists and is active in database
            UUID userId = extractUserId(token);
            
            // Check if token has been invalidated (e.g., due to role change)
            if (rolePermissionService.isTokenInvalidated(userId)) {
                return false;
            }
            
            return userRepository.findById(userId)
                    .map(user -> user.getIsActive() && !user.isAccountLocked())
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
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

    /**
     * Get signing key for JWT validation
     */
    private SecretKey getSignInKey() {
        byte[] keyBytes = secretKey.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate JWT token with comprehensive user and role claims
     */
    public String generateToken(User user) {
        return Jwts.builder()
                .setSubject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("firstName", user.getFirstName())
                .claim("lastName", user.getLastName())
                .claim("organization", user.getOrganization())
                .claim("role", user.getRole().getName())
                .claim("permissions", user.getRole().getPermissions())
                .claim("hierarchyLevel", user.getRole().getHierarchyLevel())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generate refresh token with extended expiration
     */
    public String generateRefreshToken(User user) {
        return Jwts.builder()
                .setSubject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("tokenType", "refresh")
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generate JWT token with enhanced claims (backward compatibility)
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
     * Extract hierarchy level from JWT token
     */
    public Integer extractHierarchyLevel(String token) {
        return extractClaim(token, claims -> claims.get("hierarchyLevel", Integer.class));
    }

    /**
     * Extract first name from JWT token
     */
    public String extractFirstName(String token) {
        return extractClaim(token, claims -> claims.get("firstName", String.class));
    }

    /**
     * Extract last name from JWT token
     */
    public String extractLastName(String token) {
        return extractClaim(token, claims -> claims.get("lastName", String.class));
    }

    /**
     * Check if token is a refresh token
     */
    public boolean isRefreshToken(String token) {
        try {
            String tokenType = extractClaim(token, claims -> claims.get("tokenType", String.class));
            return "refresh".equals(tokenType);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validate refresh token with invalidation check
     */
    public Boolean isRefreshTokenValid(String refreshToken) {
        try {
            if (!isRefreshToken(refreshToken) || isTokenExpired(refreshToken)) {
                return false;
            }
            
            // Verify user still exists and is active in database
            UUID userId = extractUserId(refreshToken);
            
            // Check if token has been invalidated (e.g., due to role change)
            if (rolePermissionService.isTokenInvalidated(userId)) {
                return false;
            }
            
            return userRepository.findById(userId)
                    .map(user -> user.getIsActive() && !user.isAccountLocked())
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }
}