package org.example.app.core.banking.trade;

import org.example.app.core.crypto.PKIManager;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * CustomsSignatureValidator verifies digital signatures from customs authorities
 * to authenticate trade documents and prevent fraudulent exports.
 */
public class CustomsSignatureValidator {
    private static final Logger LOGGER = Logger.getLogger(CustomsSignatureValidator.class.getName());
    
    private final PKIManager pkiManager;
    private final Map<String, CustomsAuthority> registeredAuthorities = new ConcurrentHashMap<>();
    private final Map<String, SignatureRecord> signatureRecords = new ConcurrentHashMap<>();
    
    public CustomsSignatureValidator(PKIManager pkiManager) {
        this.pkiManager = pkiManager;
    }
    
    /**
     * Registers a customs authority's public key
     */
    public void registerCustomsAuthority(String countryCode, String authorityName,
                                        PublicKey publicKey) throws ValidationException {
        try {
            if (publicKey == null) {
                throw new ValidationException("Public key cannot be null");
            }
            
            String authorityId = UUID.randomUUID().toString();
            CustomsAuthority authority = new CustomsAuthority(
                authorityId,
                countryCode,
                authorityName,
                publicKey,
                System.currentTimeMillis()
            );
            
            registeredAuthorities.put(authorityId, authority);
            LOGGER.info("Customs authority registered: " + authorityName + " (" + countryCode + ")");
        } catch (Exception e) {
            throw new ValidationException("Authority registration failed: " + e.getMessage());
        }
    }
    
    /**
     * Validates a customs signature on trade documents
     */
    public boolean validateSignature(byte[] signature, byte[] document) {
        try {
            if (signature == null || signature.length == 0 || document == null) {
                LOGGER.warning("Invalid signature or document");
                return false;
            }
            
            // Try validation with each registered authority's public key
            for (CustomsAuthority authority : registeredAuthorities.values()) {
                if (verifySignatureWithKey(signature, document, authority.getPublicKey())) {
                    recordSignatureValidation(signature, document, authority.getAuthorityId(), true);
                    return true;
                }
            }
            
            LOGGER.warning("Signature validation failed for all registered authorities");
            recordSignatureValidation(signature, document, null, false);
            return false;
        } catch (Exception e) {
            LOGGER.severe("Signature validation error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Validates signature with a specific customs authority
     */
    public boolean validateSignatureForAuthority(byte[] signature, byte[] document,
                                                 String authorityId) throws ValidationException {
        try {
            CustomsAuthority authority = registeredAuthorities.get(authorityId);
            if (authority == null) {
                throw new ValidationException("Authority not found: " + authorityId);
            }
            
            boolean isValid = verifySignatureWithKey(signature, document, authority.getPublicKey());
            recordSignatureValidation(signature, document, authorityId, isValid);
            
            return isValid;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("Validation failed: " + e.getMessage());
        }
    }
    
    /**
     * Verifies signature using RSA/ECDSA public key
     */
    private boolean verifySignatureWithKey(byte[] signature, byte[] document, PublicKey publicKey) {
        try {
            // Try RSA signature verification
            try {
                Signature sig = Signature.getInstance("SHA256withRSA");
                sig.initVerify(publicKey);
                sig.update(document);
                return sig.verify(signature);
            } catch (Exception e) {
                // Try ECDSA if RSA fails
                Signature sig = Signature.getInstance("SHA256withECDSA");
                sig.initVerify(publicKey);
                sig.update(document);
                return sig.verify(signature);
            }
        } catch (Exception e) {
            LOGGER.fine("Signature verification failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Validates batch of customs signatures
     */
    public List<SignatureValidation> validateBatchSignatures(List<SignaturePair> signaturePairs) 
            throws ValidationException {
        List<SignatureValidation> results = new ArrayList<>();
        
        try {
            for (SignaturePair pair : signaturePairs) {
                boolean isValid = validateSignature(pair.getSignature(), pair.getDocument());
                results.add(new SignatureValidation(pair.getSignatureId(), isValid));
            }
            
            LOGGER.info("Batch validation completed: " + results.size() + " signatures");
            return results;
        } catch (Exception e) {
            throw new ValidationException("Batch validation failed: " + e.getMessage());
        }
    }
    
    /**
     * Records signature validation for audit trail
     */
    private void recordSignatureValidation(byte[] signature, byte[] document, 
                                          String authorityId, boolean isValid) {
        try {
            String recordId = UUID.randomUUID().toString();
            String signatureHash = hashSignature(signature);
            
            SignatureRecord record = new SignatureRecord(
                recordId,
                signatureHash,
                authorityId,
                isValid,
                System.currentTimeMillis()
            );
            
            signatureRecords.put(recordId, record);
        } catch (Exception e) {
            LOGGER.warning("Could not record signature validation: " + e.getMessage());
        }
    }
    
    /**
     * Hashes signature for record keeping
     */
    private String hashSignature(byte[] signature) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(signature);
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }
    
    public CustomsAuthority getAuthority(String authorityId) {
        return registeredAuthorities.get(authorityId);
    }
    
    public Collection<CustomsAuthority> getAllAuthorities() {
        return new ArrayList<>(registeredAuthorities.values());
    }
    
    public SignatureRecord getSignatureRecord(String recordId) {
        return signatureRecords.get(recordId);
    }
    
    public static class CustomsAuthority {
        private final String authorityId;
        private final String countryCode;
        private final String authorityName;
        private final PublicKey publicKey;
        private final long registrationTime;
        
        public CustomsAuthority(String authorityId, String countryCode, String authorityName,
                               PublicKey publicKey, long registrationTime) {
            this.authorityId = authorityId;
            this.countryCode = countryCode;
            this.authorityName = authorityName;
            this.publicKey = publicKey;
            this.registrationTime = registrationTime;
        }
        
        public String getAuthorityId() { return authorityId; }
        public String getCountryCode() { return countryCode; }
        public String getAuthorityName() { return authorityName; }
        public PublicKey getPublicKey() { return publicKey; }
        public long getRegistrationTime() { return registrationTime; }
    }
    
    public static class SignaturePair {
        private final String signatureId;
        private final byte[] signature;
        private final byte[] document;
        
        public SignaturePair(String signatureId, byte[] signature, byte[] document) {
            this.signatureId = signatureId;
            this.signature = signature.clone();
            this.document = document.clone();
        }
        
        public String getSignatureId() { return signatureId; }
        public byte[] getSignature() { return signature.clone(); }
        public byte[] getDocument() { return document.clone(); }
    }
    
    public static class SignatureRecord {
        private final String recordId;
        private final String signatureHash;
        private final String authorityId;
        private final boolean isValid;
        private final long timestamp;
        
        public SignatureRecord(String recordId, String signatureHash, String authorityId,
                              boolean isValid, long timestamp) {
            this.recordId = recordId;
            this.signatureHash = signatureHash;
            this.authorityId = authorityId;
            this.isValid = isValid;
            this.timestamp = timestamp;
        }
        
        public String getRecordId() { return recordId; }
        public String getSignatureHash() { return signatureHash; }
        public String getAuthorityId() { return authorityId; }
        public boolean isValid() { return isValid; }
        public long getTimestamp() { return timestamp; }
    }
    
    public static class SignatureValidation {
        private final String signatureId;
        private final boolean isValid;
        
        public SignatureValidation(String signatureId, boolean isValid) {
            this.signatureId = signatureId;
            this.isValid = isValid;
        }
        
        public String getSignatureId() { return signatureId; }
        public boolean isValid() { return isValid; }
    }
    
    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
        
        public ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}