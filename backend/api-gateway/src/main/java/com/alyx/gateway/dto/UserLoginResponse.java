package com.alyx.gateway.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * User login response DTO
 * 
 * Represents the response data returned after successful user authentication
 * including JWT token, user profile information, and session details.
 */
public class UserLoginResponse {
    
    private String token;
    private String tokenType = "Bearer";
    private Long expiresIn;
    private String refreshToken;
    private UserProfileDto user;
    private LocalDateTime timestamp;
    
    // Constructors
    public UserLoginResponse() {
        this.timestamp = LocalDateTime.now();
    }
    
    public UserLoginResponse(String token, Long expiresIn, UserProfileDto user) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.user = user;
        this.timestamp = LocalDateTime.now();
    }
    
    public UserLoginResponse(String token, Long expiresIn, String refreshToken, UserProfileDto user) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.refreshToken = refreshToken;
        this.user = user;
        this.timestamp = LocalDateTime.now();
    }
    
    // Getters and Setters
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public String getTokenType() {
        return tokenType;
    }
    
    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }
    
    public Long getExpiresIn() {
        return expiresIn;
    }
    
    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }
    
    public String getRefreshToken() {
        return refreshToken;
    }
    
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
    
    public UserProfileDto getUser() {
        return user;
    }
    
    public void setUser(UserProfileDto user) {
        this.user = user;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    /**
     * User profile DTO for login response
     */
    public static class UserProfileDto {
        private UUID id;
        private String email;
        private String firstName;
        private String lastName;
        private String organization;
        private String role;
        private List<String> permissions;
        private LocalDateTime lastLoginAt;
        
        // Constructors
        public UserProfileDto() {}
        
        public UserProfileDto(UUID id, String email, String firstName, String lastName, 
                             String organization, String role, List<String> permissions) {
            this.id = id;
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
            this.organization = organization;
            this.role = role;
            this.permissions = permissions;
        }
        
        // Getters and Setters
        public UUID getId() {
            return id;
        }
        
        public void setId(UUID id) {
            this.id = id;
        }
        
        public String getEmail() {
            return email;
        }
        
        public void setEmail(String email) {
            this.email = email;
        }
        
        public String getFirstName() {
            return firstName;
        }
        
        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }
        
        public String getLastName() {
            return lastName;
        }
        
        public void setLastName(String lastName) {
            this.lastName = lastName;
        }
        
        public String getOrganization() {
            return organization;
        }
        
        public void setOrganization(String organization) {
            this.organization = organization;
        }
        
        public String getRole() {
            return role;
        }
        
        public void setRole(String role) {
            this.role = role;
        }
        
        public List<String> getPermissions() {
            return permissions;
        }
        
        public void setPermissions(List<String> permissions) {
            this.permissions = permissions;
        }
        
        public LocalDateTime getLastLoginAt() {
            return lastLoginAt;
        }
        
        public void setLastLoginAt(LocalDateTime lastLoginAt) {
            this.lastLoginAt = lastLoginAt;
        }
        
        public String getFullName() {
            return firstName + " " + lastName;
        }
        
        @Override
        public String toString() {
            return "UserProfileDto{" +
                    "id=" + id +
                    ", email='" + email + '\'' +
                    ", firstName='" + firstName + '\'' +
                    ", lastName='" + lastName + '\'' +
                    ", organization='" + organization + '\'' +
                    ", role='" + role + '\'' +
                    '}';
        }
    }
    
    @Override
    public String toString() {
        return "UserLoginResponse{" +
                "tokenType='" + tokenType + '\'' +
                ", expiresIn=" + expiresIn +
                ", user=" + user +
                ", timestamp=" + timestamp +
                '}';
    }
}