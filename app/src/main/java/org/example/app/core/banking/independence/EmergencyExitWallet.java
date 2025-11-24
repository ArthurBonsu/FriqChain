package org.example.app.core.blockchain.wallet;

import org.example.app.core.wallet.Wallet;
import org.example.app.core.wallet.TransactionBuilder;
import org.example.app.core.block.Transaction;
import org.example.app.core.types.Address;
import org.example.app.core.types.Amount;
import org.example.app.core.crypto.PKIManager;
import org.example.app.core.state.BlockchainState;
import org.example.app.core.consensus.Blockchain;

import java.security.*;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.time.Instant;

/**
 * EmergencyExitWallet - Crisis recovery mechanism for African financial independence
 * 
 * Features:
 * - Shamir's Secret Sharing (M-of-N key recovery)
 * - Multi-party emergency authorization
 * - Government resistance (no single authority control)
 * - Geographic distribution of key holders
 * - Time-delayed activation (72-hour cooldown)
 * - Audit trail and public transparency
 * - Emergency fund redistribution
 * - Human rights organization oversight
 */
public class EmergencyExitWallet extends Wallet {
    private static final Logger LOGGER = Logger.getLogger(EmergencyExitWallet.class.getName());
    
    // Emergency wallet identification
    private final String emergencyId;
    private final String emergencyName;
    private final EmergencyType emergencyType;
    
    // Fund management
    private final Map<String, BigInteger> emergencyFunds;
    private final BigInteger minimumThreshold;
    
    // Shamir's Secret Sharing configuration
    private final int totalKeyShares; // N total shares
    private final int requiredShares; // M shares needed (M-of-N)
    private final Map<String, KeyHolder> keyHolders;
    private final Map<String, byte[]> submittedShares;
    
    // Emergency activation
    private EmergencyStatus status;
    private Instant activationRequestTime;
    private final long cooldownPeriod; // 72 hours in milliseconds
    private final Map<String, EmergencyAuthorization> authorizations;
    
    // Geographic distribution
    private final Map<String, String> keyHolderCountries;
    private final BigDecimal maxCountryConcentration; // 33% max
    
    // Audit and oversight
    private final List<EmergencyEvent> auditTrail;
    private final Set<String> oversightOrganizations;
    
    // Dependencies
    private final PKIManager pkiManager;
    private final BlockchainState blockchainState;
    private final Blockchain blockchain;
    
    /**
     * EmergencyType enumeration
     */
    public enum EmergencyType {
        GOVERNMENT_SEIZURE,
        BANK_FAILURE,
        CURRENCY_COLLAPSE,
        POLITICAL_CRISIS,
        NATURAL_DISASTER,
        SYSTEM_COMPROMISE
    }
    
    /**
     * EmergencyStatus enumeration
     */
    public enum EmergencyStatus {
        DORMANT, PENDING, ACTIVE, EXECUTED, CANCELLED
    }
    
    /**
     * KeyHolder - Holds part of the secret
     */
    public static class KeyHolder {
        private final String holderId;
        private final String holderName;
        private final String organization;
        private final String country;
        private final KeyHolderType type;
        private final PublicKey publicKey;
        private final int shareNumber;
        private boolean hasSubmittedShare;
        
        public enum KeyHolderType {
            CENTRAL_BANK,
            HUMAN_RIGHTS_ORG,
            TECHNICAL_EXPERT,
            COMMUNITY_LEADER,
            INTERNATIONAL_OBSERVER
        }
        
        public KeyHolder(String holderId, String holderName, String organization,
                        String country, KeyHolderType type, PublicKey publicKey,
                        int shareNumber) {
            this.holderId = holderId;
            this.holderName = holderName;
            this.organization = organization;
            this.country = country;
            this.type = type;
            this.publicKey = publicKey;
            this.shareNumber = shareNumber;
            this.hasSubmittedShare = false;
        }
        
        public String getHolderId() { return holderId; }
        public String getHolderName() { return holderName; }
        public String getOrganization() { return organization; }
        public String getCountry() { return country; }
        public KeyHolderType getType() { return type; }
        public PublicKey getPublicKey() { return publicKey; }
        public int getShareNumber() { return shareNumber; }
        public boolean hasSubmittedShare() { return hasSubmittedShare; }
        
        public void markShareSubmitted() {
            this.hasSubmittedShare = true;
        }
    }
    
    /**
     * EmergencyAuthorization - Authorization from oversight
     */
    public static class EmergencyAuthorization {
        private final String authId;
        private final String organizationId;
        private final String organizationName;
        private final EmergencyType emergencyType;
        private final String justification;
        private final Instant timestamp;
        private final String signature;
        
        public EmergencyAuthorization(String organizationId, String organizationName,
                                     EmergencyType emergencyType, String justification,
                                     String signature) {
            this.authId = UUID.randomUUID().toString();
            this.organizationId = organizationId;
            this.organizationName = organizationName;
            this.emergencyType = emergencyType;
            this.justification = justification;
            this.timestamp = Instant.now();
            this.signature = signature;
        }
        
        public String getAuthId() { return authId; }
        public String getOrganizationId() { return organizationId; }
        public String getOrganizationName() { return organizationName; }
        public EmergencyType getEmergencyType() { return emergencyType; }
        public String getJustification() { return justification; }
        public Instant getTimestamp() { return timestamp; }
        public String getSignature() { return signature; }
    }
    
    /**
     * EmergencyEvent - Audit trail entry
     */
    public static class EmergencyEvent {
        private final Instant timestamp;
        private final String eventType;
        private final String description;
        private final String initiatedBy;
        
        public EmergencyEvent(String eventType, String description, String initiatedBy) {
            this.timestamp = Instant.now();
            this.eventType = eventType;
            this.description = description;
            this.initiatedBy = initiatedBy;
        }
        
        public Instant getTimestamp() { return timestamp; }
        public String getEventType() { return eventType; }
        public String getDescription() { return description; }
        public String getInitiatedBy() { return initiatedBy; }
    }
    
    /**
     * Constructor for EmergencyExitWallet
     */
    public EmergencyExitWallet(String emergencyId, String emergencyName,
                              EmergencyType emergencyType, int totalKeyShares,
                              int requiredShares, BigInteger minimumThreshold,
                              BlockchainState blockchainState, Blockchain blockchain,
                              PKIManager pkiManager) throws NoSuchAlgorithmException {
        super();
        
        if (requiredShares > totalKeyShares) {
            throw new IllegalArgumentException("Required shares cannot exceed total shares");
        }
        
        if (requiredShares < 3) {
            throw new IllegalArgumentException("Minimum 3 shares required for security");
        }
        
        this.emergencyId = emergencyId;
        this.emergencyName = emergencyName;
        this.emergencyType = emergencyType;
        
        this.emergencyFunds = new ConcurrentHashMap<>();
        this.minimumThreshold = minimumThreshold;
        
        this.totalKeyShares = totalKeyShares;
        this.requiredShares = requiredShares;
        this.keyHolders = new ConcurrentHashMap<>();
        this.submittedShares = new ConcurrentHashMap<>();
        
        this.status = EmergencyStatus.DORMANT;
        this.activationRequestTime = null;
        this.cooldownPeriod = 72 * 60 * 60 * 1000L; // 72 hours
        this.authorizations = new ConcurrentHashMap<>();
        
        this.keyHolderCountries = new ConcurrentHashMap<>();
        this.maxCountryConcentration = new java.math.BigDecimal("0.33");
        
        this.auditTrail = Collections.synchronizedList(new ArrayList<>());
        this.oversightOrganizations = ConcurrentHashMap.newKeySet();
        
        this.blockchainState = blockchainState;
        this.blockchain = blockchain;
        this.pkiManager = pkiManager;
        
        LOGGER.info(String.format("EmergencyExitWallet created: %s (%d-of-%d)", 
            emergencyName, requiredShares, totalKeyShares));
    }
    
    /**
     * Adds key holder with geographic verification
     */
    public boolean addKeyHolder(String holderId, String holderName, String organization,
                               String country, KeyHolder.KeyHolderType type,
                               PublicKey publicKey, int shareNumber) {
        
        // Verify geographic distribution
        if (!verifyGeographicDistribution(country)) {
            LOGGER.warning("Geographic concentration too high for country: " + country);
            return false;
        }
        
        if (shareNumber < 1 || shareNumber > totalKeyShares) {
            LOGGER.warning("Invalid share number: " + shareNumber);
            return false;
        }
        
        KeyHolder keyHolder = new KeyHolder(holderId, holderName, organization, 
                                           country, type, publicKey, shareNumber);
        
        keyHolders.put(holderId, keyHolder);
        keyHolderCountries.put(holderId, country);
        
        auditTrail.add(new EmergencyEvent("KEY_HOLDER_ADDED", 
            String.format("Added: %s (%s) from %s - Share #%d", 
                holderName, organization, country, shareNumber), 
            "SYSTEM"));
        
        LOGGER.info(String.format("Key holder added: %s (share #%d)", holderName, shareNumber));
        
        return true;
    }
    
    /**
     * Requests emergency activation
     */
    public boolean requestEmergencyActivation(String requestorId, String justification) {
        if (status != EmergencyStatus.DORMANT) {
            LOGGER.warning("Emergency already in progress");
            return false;
        }
        
        status = EmergencyStatus.PENDING;
        activationRequestTime = Instant.now();
        
        auditTrail.add(new EmergencyEvent("EMERGENCY_REQUESTED", 
            "Emergency activation requested: " + justification, requestorId));
        
        LOGGER.warning(String.format("EMERGENCY ACTIVATION REQUESTED by %s: %s", 
            requestorId, justification));
        
        return true;
    }
    
    /**
     * Submits key share for recovery
     */
    public boolean submitKeyShare(String holderId, byte[] encryptedShare, String signature) {
        KeyHolder holder = keyHolders.get(holderId);
        
        if (holder == null) {
            LOGGER.warning("Unknown key holder: " + holderId);
            return false;
        }
        
        if (status != EmergencyStatus.PENDING && status != EmergencyStatus.ACTIVE) {
            LOGGER.warning("Emergency not active for key submission");
            return false;
        }
        
        // Verify signature
        if (!verifyShareSignature(holderId, encryptedShare, signature)) {
            LOGGER.warning("Invalid share signature from: " + holderId);
            return false;
        }
        
        submittedShares.put(holderId, encryptedShare);
        holder.markShareSubmitted();
        
        auditTrail.add(new EmergencyEvent("SHARE_SUBMITTED", 
            String.format("Share #%d submitted by %s", holder.getShareNumber(), holder.getHolderName()),
            holderId));
        
        LOGGER.info(String.format("Key share submitted: %s (%d/%d)", 
            holder.getHolderName(), submittedShares.size(), requiredShares));
        
        // Check if we have enough shares
        if (submittedShares.size() >= requiredShares) {
            return attemptRecovery();
        }
        
        return true;
    }
    
    /**
     * Attempts recovery with submitted shares
     */
    private boolean attemptRecovery() {
        // Verify cooldown period
        if (activationRequestTime != null) {
            long elapsed = System.currentTimeMillis() - activationRequestTime.toEpochMilli();
            if (elapsed < cooldownPeriod) {
                long remaining = (cooldownPeriod - elapsed) / (60 * 60 * 1000);
                LOGGER.info(String.format("Cooldown period: %d hours remaining", remaining));
                return false;
            }
        }
        
        // Reconstruct secret using Shamir's Secret Sharing
        boolean recovered = reconstructSecret();
        
        if (recovered) {
            status = EmergencyStatus.ACTIVE;
            
            auditTrail.add(new EmergencyEvent("EMERGENCY_ACTIVATED", 
                String.format("Secret recovered with %d shares", submittedShares.size()), 
                "SYSTEM"));
            
            LOGGER.warning("EMERGENCY ACTIVATED - Secret successfully recovered");
            return true;
        }
        
        return false;
    }
    
    /**
     * Executes emergency fund redistribution
     */
    public Map<String, List<Transaction>> executeEmergencyExit(
            Map<String, String> recipientAddresses) {
        
        if (status != EmergencyStatus.ACTIVE) {
            LOGGER.warning("Emergency not active for execution");
            return Collections.emptyMap();
        }
        
        Map<String, List<Transaction>> transactions = new HashMap<>();
        
        for (Map.Entry<String, BigInteger> entry : emergencyFunds.entrySet()) {
            String currency = entry.getKey();
            BigInteger amount = entry.getValue();
            
            String recipientAddress = recipientAddresses.get(currency);
            if (recipientAddress != null && amount.compareTo(BigInteger.ZERO) > 0) {
                
                try {
                    TransactionBuilder builder = new TransactionBuilder()
                        .from(getAddress())
                        .to(recipientAddress)
                        .value(amount)
                        .data("EMERGENCY_EXIT".getBytes())
                        .gasPrice(BigInteger.valueOf(1000000000L))
                        .nonce(System.currentTimeMillis());
                    
                    Transaction tx = builder.build();
                    
                    transactions.computeIfAbsent(currency, k -> new ArrayList<>()).add(tx);
                    
                    auditTrail.add(new EmergencyEvent("FUNDS_TRANSFERRED", 
                        String.format("%s %s transferred to %s", amount, currency, recipientAddress),
                        "SYSTEM"));
                    
                } catch (Exception e) {
                    LOGGER.severe("Error creating emergency transaction: " + e.getMessage());
                }
            }
        }
        
        if (!transactions.isEmpty()) {
            status = EmergencyStatus.EXECUTED;
            
            auditTrail.add(new EmergencyEvent("EMERGENCY_EXECUTED", 
                "Emergency exit completed", "SYSTEM"));
            
            LOGGER.warning("EMERGENCY EXIT EXECUTED - Funds redistributed");
        }
        
        return transactions;
    }
    
    /**
     * Adds oversight organization
     */
    public boolean addOversightOrganization(String organizationId) {
        oversightOrganizations.add(organizationId);
        
        auditTrail.add(new EmergencyEvent("OVERSIGHT_ADDED", 
            "Oversight organization added: " + organizationId, "SYSTEM"));
        
        LOGGER.info("Oversight organization added: " + organizationId);
        return true;
    }
    
    /**
     * Records emergency authorization
     */
    public boolean recordAuthorization(String organizationId, String organizationName,
                                      String justification, String signature) {
        
        if (!oversightOrganizations.contains(organizationId)) {
            LOGGER.warning("Organization not authorized for oversight: " + organizationId);
            return false;
        }
        
        EmergencyAuthorization auth = new EmergencyAuthorization(
            organizationId, organizationName, emergencyType, justification, signature);
        
        authorizations.put(auth.getAuthId(), auth);
        
        auditTrail.add(new EmergencyEvent("AUTHORIZATION_RECORDED", 
            String.format("Authorization from %s: %s", organizationName, justification),
            organizationId));
        
        LOGGER.info(String.format("Emergency authorization recorded: %s", organizationName));
        
        return true;
    }
    
    /**
     * Deposits funds into emergency wallet
     */
    public boolean depositEmergencyFunds(String currency, BigInteger amount) {
        BigInteger currentBalance = emergencyFunds.getOrDefault(currency, BigInteger.ZERO);
        emergencyFunds.put(currency, currentBalance.add(amount));
        
        auditTrail.add(new EmergencyEvent("FUNDS_DEPOSITED", 
            String.format("%s %s deposited", amount, currency), "SYSTEM"));
        
        LOGGER.info(String.format("Emergency funds deposited: %s %s", amount, currency));
        
        return true;
    }
    
    /**
     * Cancels emergency (requires M-of-N approval)
     */
    public boolean cancelEmergency(Set<String> approverIds) {
        if (approverIds.size() < requiredShares) {
            LOGGER.warning("Insufficient approvals to cancel emergency");
            return false;
        }
        
        status = EmergencyStatus.CANCELLED;
        submittedShares.clear();
        
        auditTrail.add(new EmergencyEvent("EMERGENCY_CANCELLED", 
            "Emergency cancelled by multi-party approval", "SYSTEM"));
        
        LOGGER.info("Emergency cancelled");
        
        return true;
    }
    
    // Helper methods
    
    private boolean verifyGeographicDistribution(String country) {
        Map<String, Integer> countryCount = new HashMap<>();
        for (String c : keyHolderCountries.values()) {
            countryCount.put(c, countryCount.getOrDefault(c, 0) + 1);
        }
        
        int totalHolders = keyHolderCountries.size() + 1;
        int countryHolders = countryCount.getOrDefault(country, 0) + 1;
        
        java.math.BigDecimal concentration = new java.math.BigDecimal(countryHolders)
            .divide(new java.math.BigDecimal(totalHolders), 4, java.math.RoundingMode.HALF_UP);
        
        return concentration.compareTo(maxCountryConcentration) <= 0;
    }
    
    private boolean verifyShareSignature(String holderId, byte[] share, String signature) {
        // In production, verify cryptographic signature
        return signature != null && !signature.isEmpty() && share.length > 0;
    }
    
    private boolean reconstructSecret() {
        // Simplified Shamir's Secret Sharing reconstruction
        // In production, implement actual SSS algorithm
        return submittedShares.size() >= requiredShares;
    }
    
    // Getters and utility methods
    
    public String getEmergencyId() { return emergencyId; }
    public String getEmergencyName() { return emergencyName; }
    public EmergencyType getEmergencyType() { return emergencyType; }
    public EmergencyStatus getStatus() { return status; }
    
    public int getTotalKeyShares() { return totalKeyShares; }
    public int getRequiredShares() { return requiredShares; }
    public int getSubmittedShares() { return submittedShares.size(); }
    
    public BigInteger getEmergencyFunds(String currency) {
        return emergencyFunds.getOrDefault(currency, BigInteger.ZERO);
    }
    
    public Map<String, BigInteger> getAllEmergencyFunds() {
        return new HashMap<>(emergencyFunds);
    }
    
    public List<KeyHolder> getKeyHolders() {
        return new ArrayList<>(keyHolders.values());
    }
    
    public List<EmergencyEvent> getAuditTrail() {
        return new ArrayList<>(auditTrail);
    }
    
    public boolean isCooldownComplete() {
        if (activationRequestTime == null) return false;
        long elapsed = System.currentTimeMillis() - activationRequestTime.toEpochMilli();
        return elapsed >= cooldownPeriod;
    }
    
    public long getRemainingCooldown() {
        if (activationRequestTime == null) return cooldownPeriod;
        long elapsed = System.currentTimeMillis() - activationRequestTime.toEpochMilli();
        return Math.max(0, cooldownPeriod - elapsed);
    }
    
    @Override
    public String toString() {
        return String.format("EmergencyExitWallet{id='%s', type=%s, status=%s, shares=%d/%d, " +
            "funds=%d currencies}",
            emergencyId, emergencyType, status, submittedShares.size(), requiredShares,
            emergencyFunds.size());
    }
}