package com.alyx.gateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * User login request DTO
 * 
 * Represents the data required for user authentication including
 * email, password, and optional remember me functionality.
 */
public class UserLoginRequest {
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
    
    @NotBlank(message = "Password is required")
    private String password;
    
    private boolean rememberMe = false;
    
    // Constructors
    public UserLoginRequest() {}
    
    public UserLoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }
    
    public UserLoginRequest(String email, String password, boolean rememberMe) {
        this.email = email;
        this.password = password;
        this.rememberMe = rememberMe;
    }
    
    // Getters and Setters
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public boolean isRememberMe() {
        return rememberMe;
    }
    
    public void setRememberMe(boolean rememberMe) {
        this.rememberMe = rememberMe;
    }
    
    @Override
    public String toString() {
        return "UserLoginRequest{" +
                "email='" + email + '\'' +
                ", rememberMe=" + rememberMe +
                '}';
    }
}