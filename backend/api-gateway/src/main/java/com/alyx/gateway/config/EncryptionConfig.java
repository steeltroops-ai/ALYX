package com.alyx.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encryption configuration for data at rest and in transit
 * 
 * Provides AES-256-GCM encryption for sensitive data storage
 * and BCrypt for password hashing with proper security parameters.
 */
@Configuration
public class EncryptionConfig {

    @Value("${encryption.key:}")
    private String encryptionKeyBase64;
    
    @Value("${encryption.algorithm:AES}")
    private String algorithm;
    
    @Value("${encryption.transformation:AES/GCM/NoPadding}")
    private String transformation;
    
    @Value("${encryption.key-length:256}")
    private int keyLength;
    
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 16; // 128 bits

    /**
     * Password encoder using BCrypt with strength 12
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * AES encryption service for sensitive data
     */
    @Bean
    public AESEncryptionService aesEncryptionService() {
        return new AESEncryptionService(getOrGenerateEncryptionKey());
    }

    /**
     * Get or generate encryption key
     */
    private SecretKey getOrGenerateEncryptionKey() {
        if (encryptionKeyBase64 != null && !encryptionKeyBase64.isEmpty()) {
            byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
            return new SecretKeySpec(keyBytes, algorithm);
        } else {
            // Generate a new key (in production, this should be externally managed)
            try {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(algorithm);
                keyGenerator.init(keyLength);
                return keyGenerator.generateKey();
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Failed to generate encryption key", e);
            }
        }
    }

    /**
     * AES-GCM encryption service implementation
     */
    public class AESEncryptionService {
        
        private final SecretKey secretKey;
        private final SecureRandom secureRandom;

        public AESEncryptionService(SecretKey secretKey) {
            this.secretKey = secretKey;
            this.secureRandom = new SecureRandom();
        }

        /**
         * Encrypt plaintext using AES-GCM
         */
        public String encrypt(String plaintext) {
            try {
                // Generate random IV
                byte[] iv = new byte[GCM_IV_LENGTH];
                secureRandom.nextBytes(iv);

                // Initialize cipher
                Cipher cipher = Cipher.getInstance(transformation);
                GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

                // Encrypt the data
                byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

                // Combine IV and ciphertext
                byte[] encryptedData = new byte[GCM_IV_LENGTH + ciphertext.length];
                System.arraycopy(iv, 0, encryptedData, 0, GCM_IV_LENGTH);
                System.arraycopy(ciphertext, 0, encryptedData, GCM_IV_LENGTH, ciphertext.length);

                return Base64.getEncoder().encodeToString(encryptedData);
                
            } catch (Exception e) {
                throw new RuntimeException("Encryption failed", e);
            }
        }

        /**
         * Decrypt ciphertext using AES-GCM
         */
        public String decrypt(String encryptedData) {
            try {
                byte[] decodedData = Base64.getDecoder().decode(encryptedData);

                // Extract IV and ciphertext
                byte[] iv = new byte[GCM_IV_LENGTH];
                byte[] ciphertext = new byte[decodedData.length - GCM_IV_LENGTH];
                
                System.arraycopy(decodedData, 0, iv, 0, GCM_IV_LENGTH);
                System.arraycopy(decodedData, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

                // Initialize cipher for decryption
                Cipher cipher = Cipher.getInstance(transformation);
                GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

                // Decrypt the data
                byte[] plaintext = cipher.doFinal(ciphertext);
                return new String(plaintext);
                
            } catch (Exception e) {
                throw new RuntimeException("Decryption failed", e);
            }
        }

        /**
         * Generate a secure random salt for password hashing
         */
        public String generateSalt() {
            byte[] salt = new byte[32]; // 256 bits
            secureRandom.nextBytes(salt);
            return Base64.getEncoder().encodeToString(salt);
        }

        /**
         * Hash sensitive data with salt
         */
        public String hashWithSalt(String data, String salt) {
            try {
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                digest.update(Base64.getDecoder().decode(salt));
                byte[] hash = digest.digest(data.getBytes());
                return Base64.getEncoder().encodeToString(hash);
            } catch (Exception e) {
                throw new RuntimeException("Hashing failed", e);
            }
        }

        /**
         * Verify hashed data
         */
        public boolean verifyHash(String data, String salt, String expectedHash) {
            String actualHash = hashWithSalt(data, salt);
            return actualHash.equals(expectedHash);
        }
    }
}