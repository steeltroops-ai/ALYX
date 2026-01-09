package com.alyx.gateway.service;

import com.alyx.gateway.model.AuthAuditLog;
import com.alyx.gateway.model.Role;
import com.alyx.gateway.model.User;
import com.alyx.gateway.repository.AuthAuditLogRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for comprehensive audit logging functionality.
 * 
 * **Feature: neon-auth-system, Property 12: Comprehensive Audit Logging**
 * **Validates: Requirements 5.5, 7.5**
 * 
 * Tests that for any authentication event (login, logout, failed attempts, suspicious activity),
 * the system should generate appropriate audit log entries with sufficient detail for
 * security monitoring and compliance requirements.
 */
@Tag("Feature: neon-auth-system, Property 12: Comprehensive Audit Logging")
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ComprehensiveAuditLoggingPropertyTest {

    private SecurityAuditService auditService;
    
    @MockBean
    private AuthAuditLogRepository auditLogRepository;
    
    @MockBean
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        auditService = new SecurityAuditService(auditLogRepository, redisTemplate);
        
        // Setup default mock behaviors
        when(auditLogRepository.save(any(AuthAuditLog.class))).thenAnswer(invocation -> {
            AuthAuditLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(UUID.randomUUID());
            }
            return log;
        });
    }

    /**
     * Property 12a: Authentication success events are comprehensively logged
     * For any successful authentication event, the system should create a complete
     * audit log entry with user context, IP tracking, and appropriate details.
     */
    @Property(tries = 20)
    void authenticationSuccessEventsAreLogged(@ForAll("validUsers") User user,
                                            @ForAll("validIpAddresses") String ipAddress,
                                            @ForAll("validUserAgents") String userAgent) {
        // When: Logging authentication success
        auditService.logAuthenticationSuccess(user, ipAddress, userAgent);
        
        // Then: Audit log should be saved with correct details
        verify(auditLogRepository).save(argThat(auditLog -> 
            auditLog.getUser().equals(user) &&
            auditLog.getEventType().equals("LOGIN_SUCCESS") &&
            auditLog.getSuccess() == true &&
            auditLog.getIpAddress() != null &&
            auditLog.getUserAgent().equals(userAgent) &&
            auditLog.getCreatedAt() != null
        ));
    }

    /**
     * Property 12b: Authentication failure events are comprehensively logged
     * For any failed authentication event, the system should create a complete
     * audit log entry with failure details, IP tracking, and security context.
     */
    @Property(tries = 20)
    void authenticationFailureEventsAreLogged(@ForAll("validEmails") String email,
                                            @ForAll("validIpAddresses") String ipAddress,
                                            @ForAll("validUserAgents") String userAgent,
                                            @ForAll("failureReasons") String reason) {
        // When: Logging authentication failure
        auditService.logAuthenticationFailure(email, ipAddress, userAgent, reason);
        
        // Then: Audit log should be saved with correct details
        verify(auditLogRepository).save(argThat(auditLog -> 
            auditLog.getUser() == null && // No user for failed authentication
            auditLog.getEventType().equals("LOGIN_FAILURE") &&
            auditLog.getSuccess() == false &&
            auditLog.getEventDetails() != null &&
            auditLog.getEventDetails().containsKey("email") &&
            auditLog.getEventDetails().get("email").equals(email) &&
            auditLog.getEventDetails().containsKey("failure_reason") &&
            auditLog.getEventDetails().get("failure_reason").equals(reason) &&
            auditLog.getIpAddress() != null &&
            auditLog.getUserAgent().equals(userAgent) &&
            auditLog.getCreatedAt() != null
        ));
    }

    /**
     * Property 12c: Registration events are comprehensively logged
     * For any user registration event (success or failure), the system should
     * create appropriate audit log entries with registration context.
     */
    @Property(tries = 20)
    void registrationEventsAreLogged(@ForAll("validUsers") User user,
                                   @ForAll("validIpAddresses") String ipAddress,
                                   @ForAll("validUserAgents") String userAgent) {
        // When: Logging registration success
        auditService.logRegistrationSuccess(user, ipAddress, userAgent);
        
        // Then: Audit log should be saved with correct details
        verify(auditLogRepository).save(argThat(auditLog -> 
            auditLog.getUser().equals(user) &&
            auditLog.getEventType().equals("REGISTRATION_SUCCESS") &&
            auditLog.getSuccess() == true &&
            auditLog.getIpAddress() != null &&
            auditLog.getUserAgent().equals(userAgent) &&
            auditLog.getCreatedAt() != null
        ));
    }

    /**
     * Property 12d: Registration failure events are comprehensively logged
     * For any failed registration event, the system should create audit log
     * entries with failure details and security context.
     */
    @Property(tries = 20)
    void registrationFailureEventsAreLogged(@ForAll("validEmails") String email,
                                          @ForAll("validIpAddresses") String ipAddress,
                                          @ForAll("validUserAgents") String userAgent,
                                          @ForAll("failureReasons") String reason) {
        // When: Logging registration failure
        auditService.logRegistrationFailure(email, ipAddress, userAgent, reason);
        
        // Then: Audit log should be saved with correct details
        verify(auditLogRepository).save(argThat(auditLog -> 
            auditLog.getUser() == null && // No user for failed registration
            auditLog.getEventType().equals("REGISTRATION_FAILURE") &&
            auditLog.getSuccess() == false &&
            auditLog.getEventDetails() != null &&
            auditLog.getEventDetails().containsKey("email") &&
            auditLog.getEventDetails().get("email").equals(email) &&
            auditLog.getEventDetails().containsKey("failure_reason") &&
            auditLog.getEventDetails().get("failure_reason").equals(reason) &&
            auditLog.getIpAddress() != null &&
            auditLog.getUserAgent().equals(userAgent) &&
            auditLog.getCreatedAt() != null
        ));
    }

    /**
     * Property 12e: Logout events are comprehensively logged
     * For any user logout event, the system should create audit log entries
     * with user context and session details.
     */
    @Property(tries = 20)
    void logoutEventsAreLogged(@ForAll("validUsers") User user,
                             @ForAll("validIpAddresses") String ipAddress,
                             @ForAll("validUserAgents") String userAgent) {
        // When: Logging logout
        auditService.logLogout(user, ipAddress, userAgent);
        
        // Then: Audit log should be saved with correct details
        verify(auditLogRepository).save(argThat(auditLog -> 
            auditLog.getUser().equals(user) &&
            auditLog.getEventType().equals("LOGOUT") &&
            auditLog.getSuccess() == true &&
            auditLog.getIpAddress() != null &&
            auditLog.getUserAgent().equals(userAgent) &&
            auditLog.getCreatedAt() != null
        ));
    }

    /**
     * Property 12f: Account lockout events are comprehensively logged
     * For any account lockout event, the system should create audit log entries
     * with security details and lockout context.
     */
    @Property(tries = 20)
    void accountLockoutEventsAreLogged(@ForAll("usersWithFailedAttempts") User user,
                                     @ForAll("validIpAddresses") String ipAddress,
                                     @ForAll("validUserAgents") String userAgent) {
        // When: Logging account lockout
        auditService.logAccountLocked(user, ipAddress, userAgent);
        
        // Then: Audit log should be saved with correct details
        verify(auditLogRepository).save(argThat(auditLog -> 
            auditLog.getUser().equals(user) &&
            auditLog.getEventType().equals("ACCOUNT_LOCKED") &&
            auditLog.getSuccess() == false &&
            auditLog.getEventDetails() != null &&
            auditLog.getEventDetails().containsKey("failed_attempts") &&
            auditLog.getEventDetails().containsKey("locked_until") &&
            auditLog.getIpAddress() != null &&
            auditLog.getUserAgent().equals(userAgent) &&
            auditLog.getCreatedAt() != null
        ));
    }

    /**
     * Property 12g: Suspicious activity events are comprehensively logged
     * For any suspicious activity detection, the system should create audit log
     * entries with activity details and security context.
     */
    @Property(tries = 20)
    void suspiciousActivityEventsAreLogged(@ForAll("validUsers") User user,
                                         @ForAll("validIpAddresses") String ipAddress,
                                         @ForAll("validUserAgents") String userAgent,
                                         @ForAll("suspiciousActivities") String activity) {
        // When: Logging suspicious activity
        auditService.logSuspiciousActivity(user, ipAddress, userAgent, activity);
        
        // Then: Audit log should be saved with correct details
        verify(auditLogRepository).save(argThat(auditLog -> 
            auditLog.getUser().equals(user) &&
            auditLog.getEventType().equals("SUSPICIOUS_ACTIVITY") &&
            auditLog.getSuccess() == false &&
            auditLog.getEventDetails() != null &&
            auditLog.getEventDetails().containsKey("activity_type") &&
            auditLog.getEventDetails().get("activity_type").equals(activity) &&
            auditLog.getIpAddress() != null &&
            auditLog.getUserAgent().equals(userAgent) &&
            auditLog.getCreatedAt() != null
        ));
    }

    /**
     * Property 12h: Token validation failure events are comprehensively logged
     * For any token validation failure, the system should create audit log
     * entries with token details and security context.
     */
    @Property(tries = 20)
    void tokenValidationFailureEventsAreLogged(@ForAll("validTokens") String token,
                                             @ForAll("validIpAddresses") String ipAddress,
                                             @ForAll("validUserAgents") String userAgent,
                                             @ForAll("failureReasons") String reason) {
        // When: Logging token validation failure
        auditService.logTokenValidationFailure(token, ipAddress, userAgent, reason);
        
        // Then: Audit log should be saved with correct details
        verify(auditLogRepository).save(argThat(auditLog -> 
            auditLog.getUser() == null && // No user for token validation failure
            auditLog.getEventType().equals("TOKEN_VALIDATION_FAILURE") &&
            auditLog.getSuccess() == false &&
            auditLog.getEventDetails() != null &&
            auditLog.getEventDetails().containsKey("tokenPrefix") &&
            auditLog.getEventDetails().containsKey("reason") &&
            auditLog.getEventDetails().get("reason").equals(reason) &&
            auditLog.getIpAddress() != null &&
            auditLog.getUserAgent().equals(userAgent) &&
            auditLog.getCreatedAt() != null
        ));
    }

    // Data generators for property-based testing
    
    @Provide
    Arbitrary<User> validUsers() {
        return Combinators.combine(
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(3).ofMaxLength(20),
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(2).ofMaxLength(15),
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(2).ofMaxLength(15),
            Arbitraries.of("CERN", "FERMILAB", "DESY", "KEK"),
            validRoles()
        ).as((email, firstName, lastName, organization, role) -> {
            User user = new User();
            user.setId(UUID.randomUUID());
            user.setEmail(email + "@" + organization.toLowerCase() + ".org");
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setOrganization(organization);
            user.setRole(role);
            user.setIsActive(true);
            user.setEmailVerified(true);
            user.setFailedLoginAttempts(0);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            return user;
        });
    }

    @Provide
    Arbitrary<User> usersWithFailedAttempts() {
        return validUsers().map(user -> {
            user.setFailedLoginAttempts(Arbitraries.integers().between(1, 10).sample());
            user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
            return user;
        });
    }

    @Provide
    Arbitrary<Role> validRoles() {
        return Arbitraries.of("PHYSICIST", "ANALYST", "ADMIN", "STUDENT").map(roleName -> {
            Role role = new Role();
            role.setId(UUID.randomUUID());
            role.setName(roleName);
            role.setDescription("Role for " + roleName.toLowerCase());
            role.setHierarchyLevel(1);
            role.setCreatedAt(LocalDateTime.now());
            return role;
        });
    }

    @Provide
    Arbitrary<String> validEmails() {
        return Combinators.combine(
            Arbitraries.strings().withCharRange('a', 'z').ofMinLength(3).ofMaxLength(15),
            Arbitraries.of("cern.ch", "fermilab.gov", "desy.de", "kek.jp")
        ).as((username, domain) -> username + "@" + domain);
    }

    @Provide
    Arbitrary<String> validIpAddresses() {
        return Combinators.combine(
            Arbitraries.integers().between(1, 255),
            Arbitraries.integers().between(0, 255),
            Arbitraries.integers().between(0, 255),
            Arbitraries.integers().between(1, 254)
        ).as((a, b, c, d) -> a + "." + b + "." + c + "." + d);
    }

    @Provide
    Arbitrary<String> validUserAgents() {
        return Arbitraries.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 14_7_1 like Mac OS X)",
            "PostmanRuntime/7.28.4",
            "curl/7.68.0"
        );
    }

    @Provide
    Arbitrary<String> failureReasons() {
        return Arbitraries.of(
            "Invalid credentials",
            "Account not found",
            "Account locked",
            "Email already exists",
            "Invalid email format",
            "Weak password",
            "Token expired",
            "Invalid token signature",
            "Malformed token"
        );
    }

    @Provide
    Arbitrary<String> suspiciousActivities() {
        return Arbitraries.of(
            "Multiple failed login attempts",
            "Login from unusual location",
            "Rapid successive requests",
            "Privilege escalation attempt",
            "Unusual access pattern",
            "Brute force attack detected",
            "SQL injection attempt",
            "Cross-site scripting attempt"
        );
    }

    @Provide
    Arbitrary<String> validTokens() {
        return Arbitraries.strings()
            .withCharRange('A', 'Z')
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .withChars('.', '-', '_')
            .ofMinLength(50)
            .ofMaxLength(200);
    }
} 