package org.example.app.core.banking.compliance;

import org.example.app.core.types.Address;
import java.util.*;
import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * KYCPrivacyLayer - Know Your Customer verification with privacy preservation
 * 
 * Balances regulatory KYC requirements with privacy:
 * - Hashed identity data (not stored plaintext)
 * - Privacy levels (ANONYMOUS, PSEUDONYMOUS, IDENTIFIED)
 * - Verification tiers based on transaction size
 * - Privacy-preserving evidence storage
 * - Consent management for data collection
 */
public class KYCPrivacyLayer {
    private static final Logger LOGGER = Logger.getLogger(KYCPrivacyLayer.class.getName());
    
    private final Map<String, KYCRecord> kycRecords; // Address -> KYC record
    private final Map<String, PrivacyProfile> privacyProfiles; // Address -> Privacy settings
    private final Map<String, VerificationEvent> verificationHistory; // Event ID -> Verification
    private final Map<String, ConsentRecord> consents; // Address -> Consent records
    
    public enum KYCStatus {
        UNVERIFIED,        // No KYC submitted
        PENDING,           // KYC submitted, awaiting verification
        VERIFIED,          // KYC verified
        SUSPICIOUS,        // Suspicious activity detected
        FLAGGED,           // Flagged for review
        REJECTED,          // KYC rejected
        EXPIRED            // KYC expired (annual renewal)
    }
    
    public enum PrivacyLevel {
        ANONYMOUS,         // Minimal identification (for small transactions)
        PSEUDONYMOUS,      // Hashed identity (for medium transactions)
        IDENTIFIED         // Full identity (for large transactions)
    }
    
    public enum VerificationType {
        DOCUMENT_VERIFICATION,      // ID document check
        FACIAL_RECOGNITION,         // Liveness check
        BANK_STATEMENT_VERIFICATION,// Bank account proof
        THIRD_PARTY_VERIFICATION,   // External auditor
        BLOCKCHAIN_REPUTATION,      // On-chain history
        SELF_CERTIFICATION          // Entity self-attestation
    }
    
    public static class KYCRecord {
        private final String kycId;
        private final Address address;
        private final String entityName; // Hashed, not plaintext
        private final String entityType; // "INDIVIDUAL", "BUSINESS", "BANK"
        private final VerificationType verificationMethod;
        private final String jurisdictionCode;
        private KYCStatus status;
        private final long submissionTime;
        private final long verificationTime;
        private final long expirationTime; // Annual renewal requirement
        private final byte[] identityProof; // Hashed identity document hash
        private final String verifierName; // Who verified (auditor/oracle)
        private final List<String> documentHashes; // Supporting documents (hashed)
        private final Map<String, String> attributes; // Additional properties
        private BigInteger transactionLimit; // Limits based on verification tier
        private boolean isRiskApproved; // AML risk check passed
        
        public KYCRecord(String kycId, Address address, String entityName,
                        String entityType, VerificationType method,
                        String jurisdiction) {
            this.kycId = kycId;
            this.address = address;
            this.entityName = hashData(entityName);
            this.entityType = entityType;
            this.verificationMethod = method;
            this.jurisdictionCode = jurisdiction;
            this.status = KYCStatus.PENDING;
            this.submissionTime = System.currentTimeMillis();
            this.verificationTime = 0;
            this.expirationTime = submissionTime + (365L * 24 * 60 * 60 * 1000); // 1 year
            this.identityProof = new byte[0];
            this.verifierName = "";
            this.documentHashes = new ArrayList<>();
            this.attributes = new ConcurrentHashMap<>();
            this.transactionLimit = BigInteger.ZERO;
            this.isRiskApproved = false;
        }
        
        // Getters
        public String getKycId() { return kycId; }
        public Address getAddress() { return address; }
        public String getEntityName() { return entityName; }
        public String getEntityType() { return entityType; }
        public KYCStatus getStatus() { return status; }
        public long getSubmissionTime() { return submissionTime; }
        public long getExpirationTime() { return expirationTime; }
        public BigInteger getTransactionLimit() { return transactionLimit; }
        public boolean isRiskApproved() { return isRiskApproved; }
        
        public void setStatus(KYCStatus s) { this.status = s; }
        public void setTransactionLimit(BigInteger limit) { this.transactionLimit = limit; }
        public void setRiskApproved(boolean approved) { this.isRiskApproved = approved; }
        public void addDocumentHash(String hash) { documentHashes.add(hash); }
        public void setAttribute(String key, String value) { attributes.put(key, value); }
        
        public List<String> getDocumentHashes() { return new ArrayList<>(documentHashes); }
        public boolean isExpired() { return System.currentTimeMillis() > expirationTime; }
        public boolean requiresRenewal() { return isExpired(); }
    }
    
    public static class PrivacyProfile {
        private final Address address;
        private PrivacyLevel preferredLevel;
        private final List<String> consentedPurposes; // Data usage purposes
        private final long createdTime;
        private final boolean dataMinimization; // Collect only necessary data
        private final boolean rightToBeForotten; // GDPR compliance
        private final Map<String, Long> consentExpiration; // Purpose -> expiry time
        
        public PrivacyProfile(Address address, PrivacyLevel level) {
            this.address = address;
            this.preferredLevel = level;
            this.consentedPurposes = new ArrayList<>();
            this.createdTime = System.currentTimeMillis();
            this.dataMinimization = true;
            this.rightToBeForotten = true;
            this.consentExpiration = new ConcurrentHashMap<>();
        }
        
        // Getters
        public Address getAddress() { return address; }
        public PrivacyLevel getPreferredLevel() { return preferredLevel; }
        public List<String> getConsentedPurposes() { return new ArrayList<>(consentedPurposes); }
        
        public void setPreferredLevel(PrivacyLevel level) { this.preferredLevel = level; }
        public void addConsentPurpose(String purpose, long expirationMs) {
            consentedPurposes.add(purpose);
            consentExpiration.put(purpose, System.currentTimeMillis() + expirationMs);
        }
        public boolean hasConsent(String purpose) {
            if (!consentedPurposes.contains(purpose)) {
                return false;
            }
            Long expiration = consentExpiration.get(purpose);
            if (expiration == null) {
                return true;
            }
            return System.currentTimeMillis() < expiration;
        }
    }
    
    public static class VerificationEvent {
        private final String eventId;
        private final String kycId;
        private final VerificationType type;
        private final Address verifier; // Oracle or auditor
        private final long eventTime;
        private final boolean passed;
        private final String notes;
        private final String riskScore; // From AML engine
        
        public VerificationEvent(String eventId, String kycId, VerificationType type,
                                Address verifier, boolean passed, String notes, String riskScore) {
            this.eventId = eventId;
            this.kycId = kycId;
            this.type = type;
            this.verifier = verifier;
            this.eventTime = System.currentTimeMillis();
            this.passed = passed;
            this.notes = notes;
            this.riskScore = riskScore;
        }
        
        // Getters
        public String getEventId() { return eventId; }
        public String getKycId() { return kycId; }
        public VerificationType getType() { return type; }
        public Address getVerifier() { return verifier; }
        public long getEventTime() { return eventTime; }
        public boolean isPassed() { return passed; }
        public String getNotes() { return notes; }
        public String getRiskScore() { return riskScore; }
    }
    
    public static class ConsentRecord {
        private final String consentId;
        private final Address address;
        private final String purpose; // "KYC_VERIFICATION", "TRANSACTION_MONITORING", etc.
        private final long consentTime;
        private final long expirationTime;
        private boolean withdrawn;
        
        public ConsentRecord(String consentId, Address address, String purpose,
                            long durationMs) {
            this.consentId = consentId;
            this.address = address;
            this.purpose = purpose;
            this.consentTime = System.currentTimeMillis();
            this.expirationTime = consentTime + durationMs;
            this.withdrawn = false;
        }
        
        // Getters
        public String getConsentId() { return consentId; }
        public String getPurpose() { return purpose; }
        public long getConsentTime() { return consentTime; }
        public boolean isWithdrawn() { return withdrawn; }
        public boolean isValid() { return !withdrawn && System.currentTimeMillis() < expirationTime; }
        
        public void withdraw() { this.withdrawn = true; }
    }
    
    public KYCPrivacyLayer() {
        this.kycRecords = new ConcurrentHashMap<>();
        this.privacyProfiles = new ConcurrentHashMap<>();
        this.verificationHistory = new ConcurrentHashMap<>();
        this.consents = new ConcurrentHashMap<>();
        LOGGER.info("KYCPrivacyLayer initialized");
    }
    
    /**
     * Submits KYC information for verification
     */
    public String submitKYC(Address address, String entityName, String entityType,
                           VerificationType method, String jurisdiction) {
        String kycId = UUID.randomUUID().toString();
        
        KYCRecord record = new KYCRecord(kycId, address, entityName, entityType, method, jurisdiction);
        kycRecords.put(kycId, record);
        
        // Create default privacy profile
        privacyProfiles.putIfAbsent(address.toString(),
            new PrivacyProfile(address, PrivacyLevel.PSEUDONYMOUS));
        
        LOGGER.info("KYC submitted: " + kycId + " for " + entityType);
        return kycId;
    }
    
    /**
     * Verifies KYC and sets transaction limits based on verification tier
     */
    public boolean verifyKYC(String kycId, boolean passed, String notes,
                            BigInteger transactionLimit, String riskScore) {
        KYCRecord record = kycRecords.get(kycId);
        if (record == null) {
            return false;
        }
        
        if (passed) {
            record.setStatus(KYCStatus.VERIFIED);
            record.setTransactionLimit(transactionLimit);
            LOGGER.info("KYC verified: " + kycId + " - Limit: " + transactionLimit);
        } else {
            record.setStatus(KYCStatus.REJECTED);
            LOGGER.info("KYC rejected: " + kycId + " - Reason: " + notes);
        }
        
        // Record verification event
        String eventId = UUID.randomUUID().toString();
        VerificationEvent event = new VerificationEvent(eventId, kycId, record.verificationMethod,
                                                       null, passed, notes, riskScore);
        verificationHistory.put(eventId, event);
        
        return passed;
    }
    
    /**
     * Gets KYC record with privacy considerations
     */
    public KYCRecord getKYCRecord(String kycId, PrivacyLevel requestedLevel) {
        KYCRecord record = kycRecords.get(kycId);
        if (record == null) {
            return null;
        }
        
        // Check privacy level permissions
        PrivacyProfile profile = privacyProfiles.get(record.getAddress().toString());
        if (profile != null && requestedLevel.ordinal() > profile.getPreferredLevel().ordinal()) {
            LOGGER.warning("Privacy level exceeds preference for KYC: " + kycId);
            return null; // Return null if requesting more data than allowed
        }
        
        return record;
    }
    
    /**
     * Requests consent for data usage
     */
    public String requestConsent(Address address, String purpose, long durationMs) {
        String consentId = UUID.randomUUID().toString();
        ConsentRecord consent = new ConsentRecord(consentId, address, purpose, durationMs);
        
        consents.put(consentId, consent);
        
        // Track in privacy profile
        PrivacyProfile profile = privacyProfiles.get(address.toString());
        if (profile != null) {
            profile.addConsentPurpose(purpose, durationMs);
        }
        
        LOGGER.info("Consent requested: " + consentId + " for " + purpose);
        return consentId;
    }
    
    /**
     * Checks if entity has valid consent for data usage
     */
    public boolean hasValidConsent(Address address, String purpose) {
        PrivacyProfile profile = privacyProfiles.get(address.toString());
        if (profile == null) {
            return false;
        }
        return profile.hasConsent(purpose);
    }
    
    /**
     * Withdraws consent for data usage (Right to be Forgotten - GDPR)
     */
    public boolean withdrawConsent(String consentId) {
        ConsentRecord consent = consents.get(consentId);
        if (consent == null) {
            return false;
        }
        
        consent.withdraw();
        LOGGER.info("Consent withdrawn: " + consentId);
        return true;
    }
    
    /**
     * Sets privacy level for entity
     */
    public void setPrivacyLevel(Address address, PrivacyLevel level) {
        PrivacyProfile profile = privacyProfiles.get(address.toString());
        if (profile != null) {
            profile.setPreferredLevel(level);
            LOGGER.info("Privacy level set to " + level + " for " + address);
        }
    }
    
    /**
     * Checks KYC status and transaction limit
     */
    public boolean canTransact(Address address, BigInteger amount) {
        KYCRecord record = kycRecords.values().stream()
            .filter(r -> r.getAddress().equals(address) && r.getStatus() == KYCStatus.VERIFIED)
            .findFirst()
            .orElse(null);
        
        if (record == null) {
            LOGGER.warning("No verified KYC for address: " + address);
            return false;
        }
        
        if (record.isExpired()) {
            LOGGER.warning("KYC expired for address: " + address);
            return false;
        }
        
        if (amount.compareTo(record.getTransactionLimit()) > 0) {
            LOGGER.warning("Transaction exceeds limit for address: " + address);
            return false;
        }
        
        return record.isRiskApproved();
    }
    
    /**
     * Gets all KYC records (for compliance reporting)
     */
    public Collection<KYCRecord> getAllKYCRecords() {
        return kycRecords.values();
    }
    
    /**
     * Gets KYC records by status (for monitoring)
     */
    public List<KYCRecord> getKYCRecordsByStatus(KYCStatus status) {
        return kycRecords.values().stream()
            .filter(r -> r.getStatus() == status)
            .toList();
    }
    
    /**
     * Flags entity for suspicious activity
     */
    public void flagSuspicious(Address address, String reason) {
        KYCRecord record = kycRecords.values().stream()
            .filter(r -> r.getAddress().equals(address))
            .findFirst()
            .orElse(null);
        
        if (record != null) {
            record.setStatus(KYCStatus.SUSPICIOUS);
            LOGGER.warning("KYC flagged suspicious: " + record.getKycId() + " - " + reason);
        }
    }
    
    /**
     * Hashes sensitive data for privacy
     */
    private static String hashData(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            LOGGER.warning("Error hashing data: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * Gets verification history for audit trail
     */
    public List<VerificationEvent> getVerificationHistory(String kycId) {
        return verificationHistory.values().stream()
            .filter(e -> e.getKycId().equals(kycId))
            .toList();
    }
}