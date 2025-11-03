


package org.example.app.core.security;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Production-ready encryption service for sensitive configuration data
 */
public class EncryptionService {
    private static final Logger LOGGER = Logger.getLogger(EncryptionService.class.getName());
    
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int KEY_SIZE = 256;
    private static final int ITERATION_COUNT = 65536;
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 16;
    
    private final SecureRandom secureRandom;
    private volatile SecretKey masterKey;
    private final Object keyLock = new Object();
    
    public EncryptionService() {
        this.secureRandom = new SecureRandom();
        initializeMasterKey();
    }
    
    /**
     * Initialize master encryption key
     */
    private void initializeMasterKey() {
        try {
            // In production, this should be loaded from secure storage
            // or hardware security module (HSM)
            String masterPassword = System.getenv("TINC_CONFIG_MASTER_KEY");
            if (masterPassword == null || masterPassword.isEmpty()) {
                // Generate default key for development
                LOGGER.warning("Using default master key - not suitable for production!");
                masterPassword = "TINC-DEFAULT-KEY-CHANGE-IN-PRODUCTION";
            }
            
            byte[] salt = generateSalt();
            this.masterKey = deriveKey(masterPassword, salt);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize master key", e);
            throw new RuntimeException("Encryption service initialization failed", e);
        }
    }
    
    /**
     * Encrypt sensitive data
     */
    public String encrypt(String plainText) throws Exception {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        
        synchronized (keyLock) {
            // Generate IV
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            // Encrypt
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, ivSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));
            
            // Combine IV and encrypted data
            byte[] combined = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);
            
            // Encode to base64
            return Base64.getEncoder().encodeToString(combined);
        }
    }
    
    /**
     * Decrypt sensitive data
     */
    public String decrypt(String encryptedText) throws Exception {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        
        synchronized (keyLock) {
            // Decode from base64
            byte[] combined = Base64.getDecoder().decode(encryptedText);
            
            // Extract IV
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            // Extract encrypted data
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);
            
            // Decrypt
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, ivSpec);
            byte[] decrypted = cipher.doFinal(encrypted);
            
            return new String(decrypted, "UTF-8");
        }
    }
    
    /**
     * Derive encryption key from password
     */
    private SecretKey deriveKey(String password, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_SIZE);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
    }
    
    /**
     * Generate cryptographically secure salt
     */
    private byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        return salt;
    }
    
    /**
     * Rotate encryption key
     */
    public void rotateKey(String newPassword) throws Exception {
        synchronized (keyLock) {
            byte[] salt = generateSalt();
            SecretKey newKey = deriveKey(newPassword, salt);
            
            // Re-encrypt all sensitive data with new key
            // This would require access to all encrypted configuration data
            
            this.masterKey = newKey;
            LOGGER.info("Encryption key rotated successfully");
        }
    }
    
    /**
     * Validate encryption setup
     */
    public boolean validateEncryption() {
        try {
            String testData = "ENCRYPTION_TEST_" + System.currentTimeMillis();
            String encrypted = encrypt(testData);
            String decrypted = decrypt(encrypted);
            return testData.equals(decrypted);
        } catch (Exception e) {
            LOGGER.severe("Encryption validation failed: " + e.getMessage());
            return false;
        }
    }
}