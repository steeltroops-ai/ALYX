package com.alyx.gateway.service;

import com.alyx.gateway.dto.UserLoginRequest;
import com.alyx.gateway.dto.UserLoginResponse;
import com.alyx.gateway.dto.UserRegistrationRequest;
import com.alyx.gateway.dto.UserRegistrationResponse;
import com.alyx.gateway.model.Role;
import com.alyx.gateway.model.User;
import com.alyx.gateway.model.UserRole;
import com.alyx.gateway.repository.RoleRepository;
import com.alyx.gateway.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Authentication service for user registration and management
 * 
 * Provides secure user registration with validation, duplicate checking,
 * role assignment logic, and integration with password security service.
 */
@Service
@Transactional
public class AuthService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordService passwordService;
    private final SecurityAuditService auditService;
    private final JwtService jwtService;
    
    // Rate limiting configuration
    @Value("${security.rate-limiting.login-attempts:5}")
    private int maxLoginAttempts;
    
    @Value("${security.rate-limiting.lockout-duration:900}")
    private int lockoutDurationSeconds;
    
    @Value("${jwt.expiration:86400000}")
    private long jwtExpirationMs;
    
    // Organization-based role mapping for automatic role assignment
    private static final Map<String, String> ORGANIZATION_ROLE_MAPPING = Map.of(
        "CERN", "PHYSICIST",
        "FERMILAB", "PHYSICIST", 
        "DESY", "PHYSICIST",
        "KEK", "PHYSICIST",
        "ALYX_PHYSICS", "ADMIN",
        "UNIVERSITY", "ANALYST"
    );
    
    public AuthService(UserRepository userRepository, 
                      RoleRepository roleRepository,
                      PasswordService passwordService,
                      SecurityAuditService auditService,
                      JwtService jwtService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordService = passwordService;
        this.auditService = auditService;
        this.jwtService = jwtService;
    }
    
    /**
     * Register a new user with validation and duplicate checking
     * 
     * @param request the user registration request
     * @return the registration response with user details
     * @throws IllegalArgumentException if validation fails
     * @throws RuntimeException if user already exists or registration fails
     */
    public UserRegistrationResponse registerUser(UserRegistrationRequest request) {
        logger.info("Starting user registration for email: {}", request.getEmail());
        
        try {
            // Validate input data
            validateRegistrationRequest(request);
            
            // Check for duplicate email
            if (userRepository.existsByEmail(request.getEmail())) {
                logger.warn("Registration failed - email already exists: {}", request.getEmail());
                auditService.logSecurityEvent("REGISTRATION_FAILED", "/api/auth/register", 
                    request.getEmail(), "Email already exists");
                throw new RuntimeException("User with this email already exists");
            }
            
            // Validate password strength
            passwordService.validatePasswordStrength(request.getPassword());
            
            // Hash password securely
            String hashedPassword = passwordService.hashPassword(request.getPassword());
            
            // Determine role based on organization and request
            Role assignedRole = determineUserRole(request.getRoleName(), request.getOrganization());
            
            // Create new user entity
            User newUser = new User(
                request.getEmail(),
                hashedPassword,
                request.getFirstName(),
                request.getLastName(),
                request.getOrganization(),
                assignedRole
            );
            
            // Save user to database
            User savedUser = userRepository.save(newUser);
            
            logger.info("User registration successful for email: {} with role: {}", 
                request.getEmail(), assignedRole.getName());
            
            // Log successful registration
            auditService.logSecurityEvent("REGISTRATION_SUCCESS", "/api/auth/register", 
                savedUser.getId().toString(), 
                String.format("New user registered: %s with role: %s", 
                    request.getEmail(), assignedRole.getName()));
            
            // Create response
            return new UserRegistrationResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getFirstName(),
                savedUser.getLastName(),
                savedUser.getOrganization(),
                savedUser.getRole().getName(),
                "User registered successfully"
            );
            
        } catch (IllegalArgumentException e) {
            logger.warn("Registration validation failed for email: {} - {}", 
                request.getEmail(), e.getMessage());
            auditService.logSecurityEvent("REGISTRATION_VALIDATION_FAILED", "/api/auth/register", 
                request.getEmail(), "Validation error: " + e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            logger.error("Registration failed for email: {} - {}", 
                request.getEmail(), e.getMessage());
            auditService.logSecurityEvent("REGISTRATION_ERROR", "/api/auth/register", 
                request.getEmail(), "Registration error: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during registration for email: {}", 
                request.getEmail(), e);
            auditService.logSecurityEvent("REGISTRATION_ERROR", "/api/auth/register", 
                request.getEmail(), "Unexpected error: " + e.getMessage());
            throw new RuntimeException("Registration failed due to system error", e);
        }
    }
    
    /**
     * Authenticate user with database lookup and JWT token generation
     * 
     * @param request the user login request
     * @return the login response with JWT token and user information
     * @throws RuntimeException if authentication fails or account is locked
     */
    public UserLoginResponse authenticateUser(UserLoginRequest request) {
        logger.info("Starting user authentication for email: {}", request.getEmail());
        
        try {
            // Validate input data
            validateLoginRequest(request);
            
            // Find user by email
            User user = findUserByEmail(request.getEmail());
            if (user == null) {
                logger.warn("Authentication failed - user not found: {}", request.getEmail());
                auditService.logSecurityEvent("LOGIN_FAILED", "/api/auth/login", 
                    request.getEmail(), "User not found");
                // Use generic error message to prevent user enumeration
                throw new RuntimeException("Invalid email or password");
            }
            
            // Check if account is locked
            if (isAccountLocked(user)) {
                logger.warn("Authentication failed - account locked: {}", request.getEmail());
                auditService.logSecurityEvent("LOGIN_FAILED", "/api/auth/login", 
                    user.getId().toString(), "Account locked");
                throw new RuntimeException("Account is temporarily locked due to multiple failed login attempts");
            }
            
            // Check if account is active
            if (!user.getIsActive()) {
                logger.warn("Authentication failed - account inactive: {}", request.getEmail());
                auditService.logSecurityEvent("LOGIN_FAILED", "/api/auth/login", 
                    user.getId().toString(), "Account inactive");
                throw new RuntimeException("Account is inactive");
            }
            
            // Verify password
            boolean passwordValid = passwordService.verifyPassword(request.getPassword(), user.getPasswordHash());
            
            if (!passwordValid) {
                // Increment failed login attempts
                incrementFailedLoginAttempts(user.getId());
                
                // Check if account should be locked after this failed attempt
                User updatedUser = userRepository.findById(user.getId()).orElse(user);
                if (updatedUser.getFailedLoginAttempts() >= maxLoginAttempts) {
                    lockAccount(updatedUser.getId());
                    logger.warn("Account locked after {} failed attempts: {}", 
                        maxLoginAttempts, request.getEmail());
                    auditService.logSecurityEvent("ACCOUNT_LOCKED", "/api/auth/login", 
                        user.getId().toString(), 
                        String.format("Account locked after %d failed attempts", maxLoginAttempts));
                }
                
                logger.warn("Authentication failed - invalid password: {}", request.getEmail());
                auditService.logSecurityEvent("LOGIN_FAILED", "/api/auth/login", 
                    user.getId().toString(), "Invalid password");
                // Use generic error message to prevent user enumeration
                throw new RuntimeException("Invalid email or password");
            }
            
            // Authentication successful - reset failed attempts and update last login
            resetFailedLoginAttempts(user.getId());
            updateLastLogin(user.getId());
            
            // Generate JWT token
            String token = jwtService.generateToken(user);
            String refreshToken = null;
            if (request.isRememberMe()) {
                refreshToken = jwtService.generateRefreshToken(user);
            }
            
            // Create user profile DTO
            UserLoginResponse.UserProfileDto userProfile = new UserLoginResponse.UserProfileDto(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getOrganization(),
                user.getRole().getName(),
                user.getRole().getPermissions()
            );
            userProfile.setLastLoginAt(LocalDateTime.now());
            
            // Create login response
            UserLoginResponse response = new UserLoginResponse(
                token,
                jwtExpirationMs / 1000, // Convert to seconds
                refreshToken,
                userProfile
            );
            
            logger.info("User authentication successful for email: {} with role: {}", 
                request.getEmail(), user.getRole().getName());
            
            // Log successful authentication
            auditService.logSecurityEvent("LOGIN_SUCCESS", "/api/auth/login", 
                user.getId().toString(), 
                String.format("Successful login: %s with role: %s", 
                    request.getEmail(), user.getRole().getName()));
            
            return response;
            
        } catch (IllegalArgumentException e) {
            logger.warn("Authentication validation failed for email: {} - {}", 
                request.getEmail(), e.getMessage());
            auditService.logSecurityEvent("LOGIN_VALIDATION_FAILED", "/api/auth/login", 
                request.getEmail(), "Validation error: " + e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            logger.error("Authentication failed for email: {} - {}", 
                request.getEmail(), e.getMessage());
            // Audit logging already done in specific error cases above
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during authentication for email: {}", 
                request.getEmail(), e);
            auditService.logSecurityEvent("LOGIN_ERROR", "/api/auth/login", 
                request.getEmail(), "Unexpected error: " + e.getMessage());
            throw new RuntimeException("Authentication failed due to system error", e);
        }
    }
    
    /**
     * Find user by email address
     * 
     * @param email the user's email address
     * @return the user if found, null otherwise
     */
    @Transactional(readOnly = true)
    public User findUserByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        
        return userRepository.findByEmailWithRole(email).orElse(null);
    }
    
    /**
     * Update user's last login timestamp
     * 
     * @param userId the user's ID
     */
    public void updateLastLogin(UUID userId) {
        if (userId != null) {
            userRepository.updateLastLogin(userId, LocalDateTime.now());
            logger.debug("Updated last login for user: {}", userId);
        }
    }
    
    /**
     * Increment failed login attempts for a user
     * 
     * @param userId the user's ID
     */
    public void incrementFailedLoginAttempts(UUID userId) {
        if (userId != null) {
            userRepository.incrementFailedLoginAttempts(userId);
            logger.debug("Incremented failed login attempts for user: {}", userId);
        }
    }
    
    /**
     * Reset failed login attempts for a user
     * 
     * @param userId the user's ID
     */
    public void resetFailedLoginAttempts(UUID userId) {
        if (userId != null) {
            userRepository.resetFailedLoginAttempts(userId);
            logger.debug("Reset failed login attempts for user: {}", userId);
        }
    }
    
    /**
     * Check if a user account is currently locked
     * 
     * @param user the user to check
     * @return true if the account is locked, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isAccountLocked(User user) {
        return user != null && user.isAccountLocked();
    }
    
    /**
     * Lock a user account for the configured lockout duration
     * 
     * @param userId the user's ID
     */
    public void lockAccount(UUID userId) {
        if (userId != null) {
            LocalDateTime lockUntil = LocalDateTime.now().plusSeconds(lockoutDurationSeconds);
            userRepository.lockAccount(userId, lockUntil);
            logger.info("Account locked until {} for user: {}", lockUntil, userId);
        }
    }
    
    /**
     * Validate the registration request data
     * 
     * @param request the registration request to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateRegistrationRequest(UserRegistrationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Registration request cannot be null");
        }
        
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        
        if (request.getFirstName() == null || request.getFirstName().trim().isEmpty()) {
            throw new IllegalArgumentException("First name is required");
        }
        
        if (request.getLastName() == null || request.getLastName().trim().isEmpty()) {
            throw new IllegalArgumentException("Last name is required");
        }
        
        if (request.getRoleName() == null || request.getRoleName().trim().isEmpty()) {
            throw new IllegalArgumentException("Role name is required");
        }
        
        // Validate email format (basic check, more detailed validation in annotation)
        if (!request.getEmail().contains("@")) {
            throw new IllegalArgumentException("Invalid email format");
        }
    }
    
    /**
     * Validate the login request data
     * 
     * @param request the login request to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateLoginRequest(UserLoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Login request cannot be null");
        }
        
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        
        // Validate email format (basic check, more detailed validation in annotation)
        if (!request.getEmail().contains("@")) {
            throw new IllegalArgumentException("Invalid email format");
        }
    }
    
    /**
     * Determine the appropriate role for a user based on requested role and organization
     * 
     * @param requestedRoleName the role requested by the user
     * @param organization the user's organization
     * @return the assigned role
     * @throws RuntimeException if role cannot be determined or found
     */
    private Role determineUserRole(String requestedRoleName, String organization) {
        String finalRoleName;
        
        // First, check if organization has a specific role mapping
        if (organization != null && ORGANIZATION_ROLE_MAPPING.containsKey(organization.toUpperCase())) {
            String orgRole = ORGANIZATION_ROLE_MAPPING.get(organization.toUpperCase());
            
            // If requested role is higher privilege than org default, use org default
            UserRole requestedRole = UserRole.fromString(requestedRoleName);
            UserRole orgDefaultRole = UserRole.fromString(orgRole);
            
            if (requestedRole.getHierarchyLevel() > orgDefaultRole.getHierarchyLevel()) {
                finalRoleName = orgRole;
                logger.info("Role downgraded from {} to {} based on organization {}", 
                    requestedRoleName, orgRole, organization);
            } else {
                finalRoleName = requestedRoleName;
            }
        } else {
            // For unknown organizations, default to ANALYST unless explicitly requesting GUEST
            UserRole requestedRole = UserRole.fromString(requestedRoleName);
            if (requestedRole == UserRole.ADMIN || requestedRole == UserRole.PHYSICIST) {
                finalRoleName = "ANALYST";
                logger.info("Role downgraded from {} to ANALYST for unknown organization {}", 
                    requestedRoleName, organization);
            } else {
                finalRoleName = requestedRoleName;
            }
        }
        
        // Find role in database
        Optional<Role> roleOptional = roleRepository.findByName(finalRoleName);
        if (roleOptional.isEmpty()) {
            // If specific role not found, try to find default role
            roleOptional = roleRepository.findDefaultRole();
            if (roleOptional.isEmpty()) {
                logger.error("No role found for name: {} and no default role available", finalRoleName);
                throw new RuntimeException("Unable to assign role to user");
            }
            logger.warn("Requested role {} not found, assigned default role: {}", 
                finalRoleName, roleOptional.get().getName());
        }
        
        return roleOptional.get();
    }
}