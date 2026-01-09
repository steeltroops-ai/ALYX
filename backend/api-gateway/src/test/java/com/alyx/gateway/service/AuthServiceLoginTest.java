package com.alyx.gateway.service;

import com.alyx.gateway.dto.UserLoginRequest;
import com.alyx.gateway.dto.UserLoginResponse;
import com.alyx.gateway.model.Role;
import com.alyx.gateway.model.User;
import com.alyx.gateway.repository.RoleRepository;
import com.alyx.gateway.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService login authentication logic
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private RoleRepository roleRepository;
    
    @Mock
    private PasswordService passwordService;
    
    @Mock
    private SecurityAuditService auditService;
    
    @Mock
    private JwtService jwtService;
    
    private AuthService authService;
    
    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, roleRepository, passwordService, auditService, jwtService);
    }
    
    @Test
    void testSuccessfulLogin() {
        // Given
        String email = "test@example.com";
        String password = "password123";
        String hashedPassword = "$2a$12$hashedPassword";
        String token = "jwt.token.here";
        
        UserLoginRequest request = new UserLoginRequest(email, password);
        
        Role role = new Role("PHYSICIST", "Physicist role", List.of("READ_DATA", "SUBMIT_JOBS"), 2);
        role.setId(UUID.randomUUID());
        
        User user = new User(email, hashedPassword, "John", "Doe", "CERN", role);
        user.setId(UUID.randomUUID());
        
        // Mock repository calls
        when(userRepository.findByEmailWithRole(email)).thenReturn(Optional.of(user));
        when(passwordService.verifyPassword(password, hashedPassword)).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn(token);
        
        // When
        UserLoginResponse response = authService.authenticateUser(request);
        
        // Then
        assertNotNull(response);
        assertEquals(token, response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertNotNull(response.getUser());
        assertEquals(email, response.getUser().getEmail());
        assertEquals("John", response.getUser().getFirstName());
        assertEquals("Doe", response.getUser().getLastName());
        assertEquals("PHYSICIST", response.getUser().getRole());
        
        // Verify interactions
        verify(userRepository).findByEmailWithRole(email);
        verify(passwordService).verifyPassword(password, hashedPassword);
        verify(jwtService).generateToken(user);
        verify(userRepository).resetFailedLoginAttempts(user.getId());
        verify(userRepository).updateLastLogin(eq(user.getId()), any());
        verify(auditService).logSecurityEvent(eq("LOGIN_SUCCESS"), anyString(), eq(user.getId().toString()), anyString());
    }
    
    @Test
    void testLoginWithInvalidCredentials() {
        // Given
        String email = "test@example.com";
        String password = "wrongpassword";
        String hashedPassword = "$2a$12$hashedPassword";
        
        UserLoginRequest request = new UserLoginRequest(email, password);
        
        Role role = new Role("PHYSICIST", "Physicist role", List.of("READ_DATA", "SUBMIT_JOBS"), 2);
        User user = new User(email, hashedPassword, "John", "Doe", "CERN", role);
        user.setId(UUID.randomUUID());
        
        // Mock repository calls
        when(userRepository.findByEmailWithRole(email)).thenReturn(Optional.of(user));
        when(passwordService.verifyPassword(password, hashedPassword)).thenReturn(false);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        
        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            authService.authenticateUser(request);
        });
        
        assertEquals("Invalid email or password", exception.getMessage());
        
        // Verify interactions
        verify(userRepository).findByEmailWithRole(email);
        verify(passwordService).verifyPassword(password, hashedPassword);
        verify(userRepository).incrementFailedLoginAttempts(user.getId());
        verify(auditService).logSecurityEvent(eq("LOGIN_FAILED"), anyString(), eq(user.getId().toString()), anyString());
    }
    
    @Test
    void testLoginWithNonExistentUser() {
        // Given
        String email = "nonexistent@example.com";
        String password = "password123";
        
        UserLoginRequest request = new UserLoginRequest(email, password);
        
        // Mock repository calls
        when(userRepository.findByEmailWithRole(email)).thenReturn(Optional.empty());
        
        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            authService.authenticateUser(request);
        });
        
        assertEquals("Invalid email or password", exception.getMessage());
        
        // Verify interactions
        verify(userRepository).findByEmailWithRole(email);
        verify(auditService).logSecurityEvent(eq("LOGIN_FAILED"), anyString(), eq(email), anyString());
        verifyNoInteractions(passwordService);
        verifyNoInteractions(jwtService);
    }
    
    @Test
    void testLoginWithLockedAccount() {
        // Given
        String email = "locked@example.com";
        String password = "password123";
        
        UserLoginRequest request = new UserLoginRequest(email, password);
        
        Role role = new Role("PHYSICIST", "Physicist role", List.of("READ_DATA", "SUBMIT_JOBS"), 2);
        User user = new User(email, "hashedPassword", "John", "Doe", "CERN", role);
        user.setId(UUID.randomUUID());
        user.lockAccount(15); // Lock for 15 minutes
        
        // Mock repository calls
        when(userRepository.findByEmailWithRole(email)).thenReturn(Optional.of(user));
        
        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            authService.authenticateUser(request);
        });
        
        assertEquals("Account is temporarily locked due to multiple failed login attempts", exception.getMessage());
        
        // Verify interactions
        verify(userRepository).findByEmailWithRole(email);
        verify(auditService).logSecurityEvent(eq("LOGIN_FAILED"), anyString(), eq(user.getId().toString()), anyString());
        verifyNoInteractions(passwordService);
        verifyNoInteractions(jwtService);
    }
    
    @Test
    void testLoginWithInactiveAccount() {
        // Given
        String email = "inactive@example.com";
        String password = "password123";
        
        UserLoginRequest request = new UserLoginRequest(email, password);
        
        Role role = new Role("PHYSICIST", "Physicist role", List.of("READ_DATA", "SUBMIT_JOBS"), 2);
        User user = new User(email, "hashedPassword", "John", "Doe", "CERN", role);
        user.setId(UUID.randomUUID());
        user.setIsActive(false);
        
        // Mock repository calls
        when(userRepository.findByEmailWithRole(email)).thenReturn(Optional.of(user));
        
        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            authService.authenticateUser(request);
        });
        
        assertEquals("Account is inactive", exception.getMessage());
        
        // Verify interactions
        verify(userRepository).findByEmailWithRole(email);
        verify(auditService).logSecurityEvent(eq("LOGIN_FAILED"), anyString(), eq(user.getId().toString()), anyString());
        verifyNoInteractions(passwordService);
        verifyNoInteractions(jwtService);
    }
}