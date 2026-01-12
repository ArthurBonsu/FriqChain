package org.example.app.core.deployment;
import java.security.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * SecurityManager handles security configurations.
 */
public class SecurityManager {
    private static final Logger logger = Logger.getLogger(SecurityManager.class.getName());
    
    private final Map<String, KeyPair> nodeKeys;
    
    public SecurityManager() {
        this.nodeKeys = new HashMap<>();
        logger.info("SecurityManager initialized");
    }
    
    public KeyPair generateNodeKeys(String nodeId) {
        logger.info("Generating keys for node: " + nodeId);
        
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            
            nodeKeys.put(nodeId, keyPair);
            logger.info("Keys generated successfully for: " + nodeId);
            
            return keyPair;
        } catch (NoSuchAlgorithmException e) {
            logger.severe("Key generation failed: " + e.getMessage());
            return null;
        }
    }
    
    public SecurityConfig generateSecurityConfig(String environment) {
        boolean tlsRequired = environment.equals("production");
        boolean authRequired = true;
        int tokenExpiryMinutes = environment.equals("production") ? 60 : 1440;
        
        return new SecurityConfig(tlsRequired, authRequired, tokenExpiryMinutes);
    }
    
    public static class SecurityConfig {
        private final boolean tlsRequired;
        private final boolean authenticationRequired;
        private final int tokenExpiryMinutes;
        
        public SecurityConfig(boolean tls, boolean auth, int expiry) {
            this.tlsRequired = tls;
            this.authenticationRequired = auth;
            this.tokenExpiryMinutes = expiry;
        }
        
        public boolean isTlsRequired() { return tlsRequired; }
        public boolean isAuthenticationRequired() { return authenticationRequired; }
        public int getTokenExpiryMinutes() { return tokenExpiryMinutes; }
    }
}
