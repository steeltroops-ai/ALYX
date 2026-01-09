package com.alyx.gateway.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for user registration responses
 * 
 * Contains the response data returned after successful user registration.
 */
public class UserRegistrationResponse {
    
    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private String organization;
    private String roleName;
    private String message;
    private LocalDateTime timestamp;
    
    // Constructors
    public UserRegistrationResponse() {
        this.timestamp = LocalDateTime.now();
    }
    
    public UserRegistrationResponse(UUID userId, String email, String firstName, 
                                  String lastName, String organization, String roleName, String message) {
        this.userId = userId;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.organization = organization;
        this.roleName = roleName;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
    
    // Getters and Setters
    public UUID getUserId() {
        return userId;
    }
    
    public void setUserId(UUID userId) {
        this.userId = userId;
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
    
    public String getRoleName() {
        return roleName;
    }
    
    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    @Override
    public String toString() {
        return "UserRegistrationResponse{" +
                "userId=" + userId +
                ", email='" + email + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", organization='" + organization + '\'' +
                ", roleName='" + roleName + '\'' +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}