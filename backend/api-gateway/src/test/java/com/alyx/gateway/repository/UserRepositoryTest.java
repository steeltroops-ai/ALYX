package com.alyx.gateway.repository;

import com.alyx.gateway.model.Role;
import com.alyx.gateway.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserRepository
 * Tests custom query methods and repository functionality
 */
@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private Role testRole;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Create and persist test role
        testRole = new Role("PHYSICIST", "Test physicist role", 
                           List.of("READ_DATA", "SUBMIT_JOBS"), 3);
        testRole = entityManager.persistAndFlush(testRole);

        // Create and persist test user
        testUser = new User("test@example.com", "hashedPassword123", 
                           "John", "Doe", "CERN", testRole);
        testUser = entityManager.persistAndFlush(testUser);
    }

    @Test
    void testFindByEmail() {
        Optional<User> found = userRepository.findByEmail("test@example.com");
        assertTrue(found.isPresent());
        assertEquals(testUser.getId(), found.get().getId());
        assertEquals("test@example.com", found.get().getEmail());
    }

    @Test
    void testFindByEmailCaseInsensitive() {
        Optional<User> found = userRepository.findByEmail("TEST@EXAMPLE.COM");
        assertTrue(found.isPresent());
        assertEquals(testUser.getId(), found.get().getId());
        
        found = userRepository.findByEmail("Test@Example.Com");
        assertTrue(found.isPresent());
        assertEquals(testUser.getId(), found.get().getId());
    }

    @Test
    void testFindByEmailNotFound() {
        Optional<User> found = userRepository.findByEmail("nonexistent@example.com");
        assertFalse(found.isPresent());
    }

    @Test
    void testFindByEmailWithRole() {
        Optional<User> found = userRepository.findByEmailWithRole("test@example.com");
        assertTrue(found.isPresent());
        assertEquals(testUser.getId(), found.get().getId());
        assertNotNull(found.get().getRole());
        assertEquals(testRole.getId(), found.get().getRole().getId());
    }

    @Test
    void testExistsByEmail() {
        assertTrue(userRepository.existsByEmail("test@example.com"));
        assertTrue(userRepository.existsByEmail("TEST@EXAMPLE.COM"));
        assertFalse(userRepository.existsByEmail("nonexistent@example.com"));
    }

    @Test
    void testFindAllActiveUsers() {
        // Create inactive user
        User inactiveUser = new User("inactive@example.com", "hashedPassword", 
                                   "Jane", "Smith", "FERMILAB", testRole);
        inactiveUser.setIsActive(false);
        entityManager.persistAndFlush(inactiveUser);

        List<User> activeUsers = userRepository.findAllActiveUsers();
        assertEquals(1, activeUsers.size());
        assertEquals(testUser.getId(), activeUsers.get(0).getId());
    }

    @Test
    void testFindByOrganization() {
        // Create another user in same organization
        User sameOrgUser = new User("colleague@example.com", "hashedPassword", 
                                  "Jane", "Smith", "CERN", testRole);
        entityManager.persistAndFlush(sameOrgUser);

        // Create user in different organization
        User differentOrgUser = new User("other@example.com", "hashedPassword", 
                                       "Bob", "Johnson", "FERMILAB", testRole);
        entityManager.persistAndFlush(differentOrgUser);

        List<User> cernUsers = userRepository.findByOrganization("CERN");
        assertEquals(2, cernUsers.size());
        
        List<User> fermilabUsers = userRepository.findByOrganization("FERMILAB");
        assertEquals(1, fermilabUsers.size());
    }

    @Test
    void testFindByRoleName() {
        // Create another role and user
        Role analystRole = new Role("ANALYST", "Test analyst role", 
                                  List.of("READ_DATA"), 2);
        analystRole = entityManager.persistAndFlush(analystRole);
        
        User analystUser = new User("analyst@example.com", "hashedPassword", 
                                  "Alice", "Brown", "DESY", analystRole);
        entityManager.persistAndFlush(analystUser);

        List<User> physicists = userRepository.findByRoleName("PHYSICIST");
        assertEquals(1, physicists.size());
        assertEquals(testUser.getId(), physicists.get(0).getId());
        
        List<User> analysts = userRepository.findByRoleName("ANALYST");
        assertEquals(1, analysts.size());
        assertEquals(analystUser.getId(), analysts.get(0).getId());
    }

    @Test
    void testFindUsersWithFailedAttempts() {
        testUser.setFailedLoginAttempts(3);
        entityManager.persistAndFlush(testUser);

        List<User> usersWithFailures = userRepository.findUsersWithFailedAttempts(2);
        assertEquals(1, usersWithFailures.size());
        assertEquals(testUser.getId(), usersWithFailures.get(0).getId());
        
        List<User> usersWithManyFailures = userRepository.findUsersWithFailedAttempts(5);
        assertEquals(0, usersWithManyFailures.size());
    }

    @Test
    void testFindLockedAccounts() {
        LocalDateTime futureTime = LocalDateTime.now().plusMinutes(15);
        testUser.setLockedUntil(futureTime);
        entityManager.persistAndFlush(testUser);

        List<User> lockedUsers = userRepository.findLockedAccounts(LocalDateTime.now());
        assertEquals(1, lockedUsers.size());
        assertEquals(testUser.getId(), lockedUsers.get(0).getId());
        
        // Test with future time - should find no locked accounts
        List<User> noLockedUsers = userRepository.findLockedAccounts(futureTime.plusMinutes(1));
        assertEquals(0, noLockedUsers.size());
    }

    @Test
    void testFindUsersCreatedBetween() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        
        List<User> users = userRepository.findUsersCreatedBetween(start, end);
        assertEquals(1, users.size());
        assertEquals(testUser.getId(), users.get(0).getId());
        
        // Test with past date range
        LocalDateTime pastStart = LocalDateTime.now().minusDays(10);
        LocalDateTime pastEnd = LocalDateTime.now().minusDays(5);
        List<User> pastUsers = userRepository.findUsersCreatedBetween(pastStart, pastEnd);
        assertEquals(0, pastUsers.size());
    }

    @Test
    void testFindInactiveUsers() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        
        // User with no last login should be considered inactive
        List<User> inactiveUsers = userRepository.findInactiveUsers(cutoff);
        assertEquals(1, inactiveUsers.size());
        assertEquals(testUser.getId(), inactiveUsers.get(0).getId());
        
        // Set recent login
        testUser.setLastLoginAt(LocalDateTime.now().minusDays(1));
        entityManager.persistAndFlush(testUser);
        
        List<User> activeUsers = userRepository.findInactiveUsers(cutoff);
        assertEquals(0, activeUsers.size());
    }

    @Test
    void testUpdateLastLogin() {
        LocalDateTime loginTime = LocalDateTime.now();
        userRepository.updateLastLogin(testUser.getId(), loginTime);
        entityManager.flush();
        entityManager.clear();
        
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertNotNull(updatedUser.getLastLoginAt());
        // Allow for small time differences due to precision
        assertTrue(updatedUser.getLastLoginAt().isAfter(loginTime.minusSeconds(1)));
        assertTrue(updatedUser.getLastLoginAt().isBefore(loginTime.plusSeconds(1)));
    }

    @Test
    void testIncrementFailedLoginAttempts() {
        assertEquals(0, testUser.getFailedLoginAttempts());
        
        userRepository.incrementFailedLoginAttempts(testUser.getId());
        entityManager.flush();
        entityManager.clear();
        
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertEquals(1, updatedUser.getFailedLoginAttempts());
    }

    @Test
    void testResetFailedLoginAttempts() {
        testUser.setFailedLoginAttempts(5);
        testUser.setLockedUntil(LocalDateTime.now().plusMinutes(15));
        entityManager.persistAndFlush(testUser);
        
        userRepository.resetFailedLoginAttempts(testUser.getId());
        entityManager.flush();
        entityManager.clear();
        
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertEquals(0, updatedUser.getFailedLoginAttempts());
        assertNull(updatedUser.getLockedUntil());
    }

    @Test
    void testLockAccount() {
        LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(15);
        userRepository.lockAccount(testUser.getId(), lockUntil);
        entityManager.flush();
        entityManager.clear();
        
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertNotNull(updatedUser.getLockedUntil());
        assertTrue(updatedUser.getLockedUntil().isAfter(lockUntil.minusSeconds(1)));
        assertTrue(updatedUser.getLockedUntil().isBefore(lockUntil.plusSeconds(1)));
    }

    @Test
    void testDeactivateUser() {
        assertTrue(testUser.getIsActive());
        
        userRepository.deactivateUser(testUser.getId());
        entityManager.flush();
        entityManager.clear();
        
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertFalse(updatedUser.getIsActive());
    }

    @Test
    void testActivateUser() {
        testUser.setIsActive(false);
        entityManager.persistAndFlush(testUser);
        
        userRepository.activateUser(testUser.getId());
        entityManager.flush();
        entityManager.clear();
        
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertTrue(updatedUser.getIsActive());
    }

    @Test
    void testCountByRoleName() {
        long count = userRepository.countByRoleName("PHYSICIST");
        assertEquals(1, count);
        
        long noCount = userRepository.countByRoleName("NONEXISTENT");
        assertEquals(0, noCount);
    }

    @Test
    void testCountActiveUsers() {
        long count = userRepository.countActiveUsers();
        assertEquals(1, count);
        
        // Add inactive user
        User inactiveUser = new User("inactive@example.com", "hashedPassword", 
                                   "Jane", "Smith", "FERMILAB", testRole);
        inactiveUser.setIsActive(false);
        entityManager.persistAndFlush(inactiveUser);
        
        long stillCount = userRepository.countActiveUsers();
        assertEquals(1, stillCount);
    }
}