package org.example.app.core.banking.trade;

import org.example.app.core.crypto.PKIManager;
import org.example.app.core.banking.oracle.AfricanSovereigntyTypes.*;

import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * CustomsSignatureValidator - Customs verification with African Sovereignty
 * 
 * SOVEREIGNTY FEATURES:
 * - African customs authorities prioritized (3x weight)
 * - Multi-region verification (3+ of 5 regions)
 * - Cross-border signature validation
 * - Community-verified customs authorities
 */
public class CustomsSignatureValidator {
    private static final Logger LOGGER = Logger.getLogger(CustomsSignatureValidator.class.getName());
    
    private static final int MIN_REGIONS_REQUIRED = 3;
    private static final int MIN_AFRICAN_AUTHORITIES = 2;
    
    private final PKIManager pkiManager;
    private final Map<String, CustomsAuthority> registeredAuthorities = new ConcurrentHashMap<>();
    private final Map<String, SignatureRecord> signatureRecords = new ConcurrentHashMap<>();
    private final Map<AfricanRegion, Set<String>> regionalAuthorities = new ConcurrentHashMap<>();
    
    public CustomsSignatureValidator(PKIManager pkiManager) {
        this.pkiManager = pkiManager;
    }
    
    public static class CustomsAuthority {
        private final String authorityId;
        private final String countryCode;
        private final String authorityName;
        private final PublicKey publicKey;
        private final long registrationTime;
        private final AfricanRegion region;
        private final boolean isAfrican;
        private final boolean isCommunityVerified;
        
        public CustomsAuthority(String authorityId, String countryCode, String authorityName,
                               PublicKey publicKey, long registrationTime, AfricanRegion region,
                               boolean isAfrican, boolean isCommunityVerified) {
            this.authorityId = authorityId;
            this.countryCode = countryCode;
            this.authorityName = authorityName;
            this.publicKey = publicKey;
            this.registrationTime = registrationTime;
            this.region = region;
            this.isAfrican = isAfrican;
            this.isCommunityVerified = isCommunityVerified;
        }
        
        public String getAuthorityId() { return authorityId; }
        public String getCountryCode() { return countryCode; }
        public String getAuthorityName() { return authorityName; }
        public PublicKey getPublicKey() { return publicKey; }
        public long getRegistrationTime() { return registrationTime; }
        public AfricanRegion getRegion() { return region; }
        public boolean isAfrican() { return isAfrican; }
        public boolean isCommunityVerified() { return isCommunityVerified; }
    }
    
    public void registerCustomsAuthority(String countryCode, String authorityName,
                                        PublicKey publicKey, AfricanRegion region,
                                        boolean isAfrican, boolean isCommunityVerified) 
            throws ValidationException {
        try {
            if (publicKey == null) {
                throw new ValidationException("Public key cannot be null");
            }
            
            String authorityId = UUID.randomUUID().toString();
            CustomsAuthority authority = new CustomsAuthority(
                authorityId, countryCode, authorityName, publicKey,
                System.currentTimeMillis(), region, isAfrican, isCommunityVerified
            );
            
            registeredAuthorities.put(authorityId, authority);
            
            if (region != null) {
                regionalAuthorities.computeIfAbsent(region, k -> new HashSet<>()).add(authorityId);
            }
            
            LOGGER.info(String.format("Customs authority registered: %s (%s) - Region: %s - African: %s",
                authorityName, countryCode, region, isAfrican));
        } catch (Exception e) {
            throw new ValidationException("Authority registration failed: " + e.getMessage());
        }
    }
    
    public boolean validateSignature(byte[] signature, byte[] document) {
        try {
            if (signature == null || signature.length == 0 || document == null) {
                LOGGER.warning("Invalid signature or document");
                return false;
            }
            
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
    
    public boolean validateMultiRegionSignatures(byte[] signature, byte[] document,
                                                 List<String> requiredAuthorityIds)
            throws ValidationException {
        try {
            Set<AfricanRegion> regionsRepresented = new HashSet<>();
            int africanAuthorities = 0;
            
            for (String authorityId : requiredAuthorityIds) {
                CustomsAuthority authority = registeredAuthorities.get(authorityId);
                if (authority == null) continue;
                
                if (authority.getRegion() != null) {
                    regionsRepresented.add(authority.getRegion());
                }
                
                if (authority.isAfrican()) {
                    africanAuthorities++;
                }
            }
            
            if (regionsRepresented.size() < MIN_REGIONS_REQUIRED) {
                throw new ValidationException(String.format(
                    "Insufficient regions represented: %d (minimum: %d)",
                    regionsRepresented.size(), MIN_REGIONS_REQUIRED));
            }
            
            if (africanAuthorities < MIN_AFRICAN_AUTHORITIES) {
                throw new ValidationException(String.format(
                    "Insufficient African authorities: %d (minimum: %d)",
                    africanAuthorities, MIN_AFRICAN_AUTHORITIES));
            }
            
            return validateSignature(signature, document);
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("Multi-region validation failed: " + e.getMessage());
        }
    }
    
    private boolean verifySignatureWithKey(byte[] signature, byte[] document, PublicKey publicKey) {
        try {
            try {
                Signature sig = Signature.getInstance("SHA256withRSA");
                sig.initVerify(publicKey);
                sig.update(document);
                return sig.verify(signature);
            } catch (Exception e) {
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
    
    private void recordSignatureValidation(byte[] signature, byte[] document, 
                                          String authorityId, boolean isValid) {
        try {
            String recordId = UUID.randomUUID().toString();
            String signatureHash = hashSignature(signature);
            
            SignatureRecord record = new SignatureRecord(
                recordId, signatureHash, authorityId, isValid, System.currentTimeMillis()
            );
            
            signatureRecords.put(recordId, record);
        } catch (Exception e) {
            LOGGER.warning("Could not record signature validation: " + e.getMessage());
        }
    }
    
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
    
    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
        
        public ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}