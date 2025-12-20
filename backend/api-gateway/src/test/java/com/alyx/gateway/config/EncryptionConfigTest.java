package com.alyx.gateway.config;

import com.alyx.gateway.config.EncryptionConfig.AESEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Encryption Configuration
 */
class EncryptionConfigTest {

    private EncryptionConfig encryptionConfig;
    private AESEncryptionService aesEncryptionService;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        encryptionConfig = new EncryptionConfig();
        ReflectionTestUtils.setField(encryptionConfig, "encryptionKeyBase64", "");
        ReflectionTestUtils.setField(encryptionConfig, "algorithm", "AES");
        ReflectionTestUtils.setField(encryptionConfig, "transformation", "AES/GCM/NoPadding");
        ReflectionTestUtils.setField(encryptionConfig, "keyLength", 256);
        
        aesEncryptionService = encryptionConfig.aesEncryptionService();
        passwordEncoder = encryptionConfig.passwordEncoder();
    }

    @Test
    void testPasswordEncoder() {
        // Given
        String plainPassword = "testPassword123";

        // When
        String hashedPassword = passwordEncoder.encode(plainPassword);

        // Then
        assertNotNull(hashedPassword);
        assertNotEquals(plainPassword, hashedPassword);
        assertTrue(passwordEncoder.matches(plainPassword, hashedPassword));
        assertFalse(passwordEncoder.matches("wrongPassword", hashedPassword));
    }

    @Test
    void testAESEncryption() {
        // Given
        String plaintext = "This is sensitive data that needs encryption";

        // When
        String encrypted = aesEncryptionService.encrypt(plaintext);
        String decrypted = aesEncryptionService.decrypt(encrypted);

        // Then
        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void testAESEncryptionWithDifferentInputs() {
        // Given
        String plaintext1 = "First message";
        String plaintext2 = "Second message";

        // When
        String encrypted1 = aesEncryptionService.encrypt(plaintext1);
        String encrypted2 = aesEncryptionService.encrypt(plaintext2);

        // Then
        assertNotEquals(encrypted1, encrypted2);
        assertEquals(plaintext1, aesEncryptionService.decrypt(encrypted1));
        assertEquals(plaintext2, aesEncryptionService.decrypt(encrypted2));
    }

    @Test
    void testSaltGeneration() {
        // When
        String salt1 = aesEncryptionService.generateSalt();
        String salt2 = aesEncryptionService.generateSalt();

        // Then
        assertNotNull(salt1);
        assertNotNull(salt2);
        assertNotEquals(salt1, salt2);
        assertTrue(salt1.length() > 0);
        assertTrue(salt2.length() > 0);
    }

    @Test
    void testHashWithSalt() {
        // Given
        String data = "sensitive data";
        String salt = aesEncryptionService.generateSalt();

        // When
        String hash1 = aesEncryptionService.hashWithSalt(data, salt);
        String hash2 = aesEncryptionService.hashWithSalt(data, salt);

        // Then
        assertNotNull(hash1);
        assertNotNull(hash2);
        assertEquals(hash1, hash2); // Same data + salt should produce same hash
        assertTrue(aesEncryptionService.verifyHash(data, salt, hash1));
        assertFalse(aesEncryptionService.verifyHash("different data", salt, hash1));
    }

    @Test
    void testHashWithDifferentSalts() {
        // Given
        String data = "test data";
        String salt1 = aesEncryptionService.generateSalt();
        String salt2 = aesEncryptionService.generateSalt();

        // When
        String hash1 = aesEncryptionService.hashWithSalt(data, salt1);
        String hash2 = aesEncryptionService.hashWithSalt(data, salt2);

        // Then
        assertNotEquals(hash1, hash2); // Different salts should produce different hashes
    }

    @Test
    void testEncryptionWithEmptyString() {
        // Given
        String emptyString = "";

        // When
        String encrypted = aesEncryptionService.encrypt(emptyString);
        String decrypted = aesEncryptionService.decrypt(encrypted);

        // Then
        assertEquals(emptyString, decrypted);
    }

    @Test
    void testEncryptionWithSpecialCharacters() {
        // Given
        String specialChars = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~";

        // When
        String encrypted = aesEncryptionService.encrypt(specialChars);
        String decrypted = aesEncryptionService.decrypt(encrypted);

        // Then
        assertEquals(specialChars, decrypted);
    }

    @Test
    void testEncryptionWithUnicodeCharacters() {
        // Given
        String unicode = "Hello 世界 🌍 Здравствуй мир";

        // When
        String encrypted = aesEncryptionService.encrypt(unicode);
        String decrypted = aesEncryptionService.decrypt(encrypted);

        // Then
        assertEquals(unicode, decrypted);
    }

    @Test
    void testDecryptionWithInvalidData() {
        // Given
        String invalidEncryptedData = "invalid-base64-data";

        // When & Then
        assertThrows(RuntimeException.class, () -> 
            aesEncryptionService.decrypt(invalidEncryptedData)
        );
    }
}