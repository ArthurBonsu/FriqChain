package org.example.app.core.crypto;

import java.io.File;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PKI Manager for managing keys and certificates for nodes.
 */
public class PKIManager {
    private static final Logger LOGGER = Logger.getLogger(PKIManager.class.getName());
    private static String DATABASE_FILE;
    private final ReentrantLock certificateLock;

    final Keypair keypair;
    Certificate certificate;
    
    public PKIManager() {
        this.keypair = Keypair.generate();
        this.certificateLock = new ReentrantLock();
    }

    public PublicKey getPublicKey() {
        return keypair.getPublicKey();
    }

    public void requestCertificate(CertificateAuthority ca) throws GeneralSecurityException {
        if (ca == null) {
            throw new IllegalArgumentException("Certificate Authority cannot be null");
        }

        certificateLock.lock();
        try {
            String nodeId = Base64.getEncoder().encodeToString(getPublicKey().getEncoded());
            Certificate newCertificate = ca.issueCertificate(nodeId, getPublicKey());
            
            // Verify the certificate before assigning
            if (!ca.verifyCertificate(newCertificate)) {
                throw new GeneralSecurityException("Failed to verify newly issued certificate");
            }
            
            this.certificate = newCertificate;
            LOGGER.info("Certificate received and verified: " + certificate);
            System.out.println("PKIManager: Certificate received: " + certificate);
        } catch (GeneralSecurityException e) {
            LOGGER.severe("Failed to request certificate: " + e.getMessage());
            throw e;
        } finally {
            certificateLock.unlock();
        }
    }

    public boolean verifyCertificate(Certificate cert, CertificateAuthority ca) {
        if (cert == null || ca == null) {
            LOGGER.warning("Certificate or CA is null during verification");
            return false;
        }
        
        try {
            boolean isValid = ca.verifyCertificate(cert);
            if (!isValid) {
                LOGGER.warning("Certificate validation failed for: " + cert.getNodeId());
            }
            return isValid;
        } catch (Exception e) {
            LOGGER.warning("Certificate verification failed with error: " + e.getMessage());
            return false;
        }
    }

    public void renewCertificate(CertificateAuthority ca) throws GeneralSecurityException {
        if (ca == null) {
            throw new IllegalArgumentException("Certificate Authority cannot be null");
        }

        certificateLock.lock();
        try {
            Certificate oldCert = this.certificate;
            
            // Request new certificate
            String nodeId = Base64.getEncoder().encodeToString(getPublicKey().getEncoded());
            Certificate newCertificate = ca.issueCertificate(nodeId, getPublicKey());
            
            // Verify new certificate
            if (!ca.verifyCertificate(newCertificate)) {
                throw new GeneralSecurityException("Failed to verify renewed certificate");
            }
            
            // Check if signatures are different
            if (oldCert != null && Arrays.equals(oldCert.getSignature(), newCertificate.getSignature())) {
                throw new GeneralSecurityException("New certificate is identical to old certificate");
            }
            
            this.certificate = newCertificate;
        } finally {
            certificateLock.unlock();
        }
    }

    // Database methods remain unchanged
    public static void setDatabaseFile(String databaseFilePath) {
        if (databaseFilePath == null || databaseFilePath.isEmpty()) {
            throw new IllegalArgumentException("Database file path cannot be null or empty");
        }
        
        try {
            File dbFile = new File(databaseFilePath);
            if (!dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }
            DATABASE_FILE = databaseFilePath;
            LOGGER.info("Database file set to: " + DATABASE_FILE);
        } catch (Exception e) {
            LOGGER.severe("Failed to set database file: " + e.getMessage());
            throw new RuntimeException("Failed to set database file", e);
        }
    }

    public void storeKeypair(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) {
            throw new IllegalArgumentException("Node ID cannot be null or empty");
        }
        if (DATABASE_FILE == null) {
            throw new IllegalStateException("Database not initialized");
        }
        LOGGER.info("Storing keypair for node: " + nodeId);
    }

    public static Keypair loadKeypair(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) {
            throw new IllegalArgumentException("Node ID cannot be null or empty");
        }
        if (DATABASE_FILE == null) {
            throw new IllegalStateException("Database not initialized");
        }
        LOGGER.info("Loading keypair for node: " + nodeId);
        return Keypair.generate(); // Temporary implementation
    }
} 
