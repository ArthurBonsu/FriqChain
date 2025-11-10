package org.example.app.core.banking.registration;

import org.example.app.core.blockchain.transaction.BankRegistrationTransaction;
import org.example.app.core.crypto.Certificate;
import org.example.app.core.crypto.CertificateAuthority;
import org.example.app.core.crypto.PKIManager;
import org.example.app.core.smartcontract.ContractCallManager;
import org.example.app.core.state.StateManager;
import org.example.app.core.state.BlockchainState;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;
import org.example.app.core.internal.EventLog;
import org.example.app.core.config.BlockchainConfig;
import org.example.app.core.p2p.PeerNetwork;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * BankRegistrationContract - Smart contract for secure, government-resistant bank registration
 * Implements the core registration logic for banks in the TINC Blockchain system with
 * geographic distribution controls and encrypted communication channels.
 */
public class BankRegistrationContract {
    private static final Logger LOGGER = Logger.getLogger(BankRegistrationContract.class.getName());
    
    // Geographic distribution limits (max 33% per region for government resistance)
    private static final double MAX_GEOGRAPHIC_SHARE = 0.33;
    private static final int AFRICA_REGION = 1;
    private static final int EUROPE_REGION = 2;
    private static final int AMERICAS_REGION = 3;
    private static final int ASIA_REGION = 4;
    
    // Registration states
    public enum RegistrationStatus {
        PENDING,
        APPROVED,
        REJECTED,
        SUSPENDED,
        REVOKED
    }
    
    // Core infrastructure dependencies
    private final StateManager stateManager;
    private final BlockchainState blockchainState;
    private final CertificateAuthority certificateAuthority;
    private final PKIManager pkiManager;
    private final EventLog eventLog;
    private final BlockchainConfig config;
    private final PeerNetwork peerNetwork;
    
    // Registration storage
    private final Map<String, BankRegistration> registeredBanks;
    private final Map<Integer, AtomicLong> regionCounts;
    private final Map<String, EncryptedChannel> bankChannels;
    private final AtomicLong totalBanks;
    
    // Security components
    private final SecureRandom secureRandom;
    
    /**
     * Internal class to represent a complete bank registration
     */
    public static class BankRegistration {
        private final String bankId;
        private final String bankName;
        private final String bankCountry;
        private final String swiftCode;
        private final Address bankAddress;
        private final PublicKey bankPublicKey;
        private final byte[] encryptedCredentials;
        private final List<Address> operators;
        private final boolean isCentralBank;
        private final int geographicRegion;
        private final Date registrationDate;
        private final String registrationHash;
        private RegistrationStatus status;
        private Certificate bankCertificate;
        
        public BankRegistration(BankRegistrationTransaction tx, String registrationHash) {
            this.bankId = tx.getRegistrationId();
            this.bankName = tx.getBankName();
            this.bankCountry = tx.getBankCountry();
            this.swiftCode = tx.getSwiftCode();
            this.bankAddress = tx.getBankAddress();
            this.bankPublicKey = deserializePublicKey(tx.getPublicKey());
            this.encryptedCredentials = tx.getEncryptedCredentials();
            this.operators = new ArrayList<>(tx.getInitialOperators());
            this.isCentralBank = tx.isCentralBank();
            this.geographicRegion = tx.getGeographicRegion();
            this.registrationDate = tx.getRegistrationDate();
            this.registrationHash = registrationHash;
            this.status = RegistrationStatus.PENDING;
        }
        
        // Getters
        public String getBankId() { return bankId; }
        public String getBankName() { return bankName; }
        public String getBankCountry() { return bankCountry; }
        public String getSwiftCode() { return swiftCode; }
        public Address getBankAddress() { return bankAddress; }
        public PublicKey getBankPublicKey() { return bankPublicKey; }
        public byte[] getEncryptedCredentials() { return encryptedCredentials; }
        public List<Address> getOperators() { return new ArrayList<>(operators); }
        public boolean isCentralBank() { return isCentralBank; }
        public int getGeographicRegion() { return geographicRegion; }
        public Date getRegistrationDate() { return registrationDate; }
        public String getRegistrationHash() { return registrationHash; }
        public RegistrationStatus getStatus() { return status; }
        public Certificate getBankCertificate() { return bankCertificate; }
        
        public void setStatus(RegistrationStatus status) { this.status = status; }
        public void setBankCertificate(Certificate certificate) { this.bankCertificate = certificate; }
        
        private PublicKey deserializePublicKey(byte[] keyData) {
            // In production, implement proper key deserialization
            // For now, return null to avoid compilation issues
            return null;
        }
    }
    
    /**
     * Represents an encrypted communication channel for a bank
     */
    public static class EncryptedChannel {
        private final String bankId;
        private final SecretKey channelKey;
        private final String encryptionAlgorithm;
        private final Date createdAt;
        private final Date expiresAt;
        
        public EncryptedChannel(String bankId, SecretKey channelKey) {
            this.bankId = bankId;
            this.channelKey = channelKey;
            this.encryptionAlgorithm = "AES/GCM/NoPadding";
            this.createdAt = new Date();
            this.expiresAt = new Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L); // 1 year
        }
        
        public String getBankId() { return bankId; }
        public SecretKey getChannelKey() { return channelKey; }
        public String getEncryptionAlgorithm() { return encryptionAlgorithm; }
        public Date getCreatedAt() { return createdAt; }
        public Date getExpiresAt() { return expiresAt; }
        
        public boolean isExpired() {
            return new Date().after(expiresAt);
        }
    }
    
    /**
     * Constructor
     */
    public BankRegistrationContract(StateManager stateManager,
                                   BlockchainState blockchainState,
                                   CertificateAuthority certificateAuthority,
                                   PKIManager pkiManager,
                                   EventLog eventLog,
                                   BlockchainConfig config,
                                   PeerNetwork peerNetwork) {
        this.stateManager = stateManager;
        this.blockchainState = blockchainState;
        this.certificateAuthority = certificateAuthority;
        this.pkiManager = pkiManager;
        this.eventLog = eventLog;
        this.config = config;
        this.peerNetwork = peerNetwork;
        
        this.registeredBanks = new ConcurrentHashMap<>();
        this.regionCounts = new ConcurrentHashMap<>();
        this.bankChannels = new ConcurrentHashMap<>();
        this.totalBanks = new AtomicLong(0);
        this.secureRandom = new SecureRandom();
        
        // Initialize region counters
        for (int i = 1; i <= 4; i++) {
            regionCounts.put(i, new AtomicLong(0));
        }
        
        LOGGER.info("BankRegistrationContract initialized");
    }
    
    /**
     * Processes a bank registration transaction
     */
    public boolean processRegistration(BankRegistrationTransaction transaction) {
        try {
            LOGGER.info("Processing bank registration for: " + transaction.getBankName());
            
            // Validate transaction
            if (!validateRegistrationTransaction(transaction)) {
                LOGGER.warning("Registration validation failed for: " + transaction.getBankName());
                return false;
            }
            
            // Check geographic distribution
            if (!checkGeographicDistribution(transaction.getGeographicRegion())) {
                LOGGER.warning("Geographic distribution limit exceeded for region: " + 
                             transaction.getGeographicRegion());
                eventLog.logEvent("REGISTRATION_REJECTED_GEOGRAPHIC: " + transaction.getBankName());
                return false;
            }
            
            // Generate registration hash
            String registrationHash = generateRegistrationHash(transaction);
            
            // Create bank registration
            BankRegistration registration = new BankRegistration(transaction, registrationHash);
            
            // Issue certificate
            Certificate bankCert = issueBankCertificate(registration);
            registration.setBankCertificate(bankCert);
            
            // Create encrypted channel
            EncryptedChannel channel = createEncryptedChannel(registration);
            
            // Store registration
            registeredBanks.put(registration.getBankId(), registration);
            bankChannels.put(registration.getBankId(), channel);
            
            // Update counters
            regionCounts.get(registration.getGeographicRegion()).incrementAndGet();
            totalBanks.incrementAndGet();
            
            // Log event
            eventLog.logEvent("BANK_REGISTERED: " + registration.getBankName() + 
                             " (ID: " + registration.getBankId() + ")");
            
            LOGGER.info("Bank registration successful: " + registration.getBankName());
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Error processing bank registration: " + e.getMessage());
            eventLog.logEvent("REGISTRATION_ERROR: " + transaction.getBankName() + " - " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Validates a bank registration transaction
     */
    private boolean validateRegistrationTransaction(BankRegistrationTransaction transaction) {
        // Basic transaction validation
        if (!transaction.isValid()) {
            return false;
        }
        
        // Check for duplicate registration
        if (isDuplicateBank(transaction)) {
            LOGGER.warning("Duplicate bank registration detected: " + transaction.getBankName());
            return false;
        }
        
        // Validate bank credentials
        if (!validateBankCredentials(transaction)) {
            LOGGER.warning("Bank credentials validation failed: " + transaction.getBankName());
            return false;
        }
        
        // Validate geographic region
        if (transaction.getGeographicRegion() < 1 || transaction.getGeographicRegion() > 4) {
            LOGGER.warning("Invalid geographic region: " + transaction.getGeographicRegion());
            return false;
        }
        
        return true;
    }
    
    /**
     * Checks if geographic distribution limits are met
     */
    private boolean checkGeographicDistribution(int region) {
        long currentRegionCount = regionCounts.get(region).get();
        long total = totalBanks.get();
        
        if (total == 0) {
            return true; // First bank always allowed
        }
        
        double futureShare = (double)(currentRegionCount + 1) / (total + 1);
        
        if (futureShare > MAX_GEOGRAPHIC_SHARE) {
            LOGGER.warning("Geographic distribution limit would be exceeded. Region " + region + 
                          " would have " + String.format("%.2f%%", futureShare * 100) + 
                          " (max " + String.format("%.2f%%", MAX_GEOGRAPHIC_SHARE * 100) + ")");
            return false;
        }
        
        return true;
    }
    
    /**
     * Issues a certificate for a registered bank
     */
    private Certificate issueBankCertificate(BankRegistration registration) throws Exception {
        String nodeId = "BANK_" + registration.getBankId();
        PublicKey bankPublicKey = registration.getBankPublicKey();
        
        if (bankPublicKey == null) {
            throw new Exception("Bank public key is null");
        }
        
        Certificate certificate = certificateAuthority.issueCertificate(nodeId, bankPublicKey);
        
        LOGGER.info("Certificate issued for bank: " + registration.getBankName());
        return certificate;
    }
    
    /**
     * Creates an encrypted communication channel for a bank
     */
    private EncryptedChannel createEncryptedChannel(BankRegistration registration) throws Exception {
        // Generate symmetric key for the bank's encrypted channel
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        SecretKey channelKey = keyGenerator.generateKey();
        
        EncryptedChannel channel = new EncryptedChannel(registration.getBankId(), channelKey);
        
        LOGGER.info("Encrypted channel created for bank: " + registration.getBankName());
        return channel;
    }
    
    /**
     * Generates a unique registration hash
     */
    private String generateRegistrationHash(BankRegistrationTransaction transaction) {
        String data = transaction.getBankName() + 
                     transaction.getSwiftCode() + 
                     transaction.getBankCountry() + 
                     System.currentTimeMillis();
        
        return Hash.create(data.getBytes()).toString();
    }
    
    /**
     * Checks for duplicate bank registration
     */
    private boolean isDuplicateBank(BankRegistrationTransaction transaction) {
        return registeredBanks.values().stream()
            .anyMatch(bank -> 
                bank.getSwiftCode().equals(transaction.getSwiftCode()) ||
                bank.getBankAddress().equals(transaction.getBankAddress())
            );
    }
    
    /**
     * Validates bank credentials
     */
    private boolean validateBankCredentials(BankRegistrationTransaction transaction) {
        // Validate SWIFT code format
        String swiftCode = transaction.getSwiftCode();
        if (swiftCode == null || swiftCode.length() < 8 || swiftCode.length() > 11) {
            return false;
        }
        
        // Validate bank license ID
        if (transaction.getBankLicenseId() == null || transaction.getBankLicenseId().trim().isEmpty()) {
            return false;
        }
        
        // Validate encrypted credentials
        if (transaction.getEncryptedCredentials() == null || 
            transaction.getEncryptedCredentials().length == 0) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Approves a pending bank registration
     */
    public boolean approveRegistration(String bankId) {
        BankRegistration registration = registeredBanks.get(bankId);
        if (registration == null || registration.getStatus() != RegistrationStatus.PENDING) {
            return false;
        }
        
        registration.setStatus(RegistrationStatus.APPROVED);
        eventLog.logEvent("BANK_APPROVED: " + registration.getBankName());
        
        LOGGER.info("Bank registration approved: " + registration.getBankName());
        return true;
    }
    
    /**
     * Rejects a pending bank registration
     */
    public boolean rejectRegistration(String bankId, String reason) {
        BankRegistration registration = registeredBanks.get(bankId);
        if (registration == null || registration.getStatus() != RegistrationStatus.PENDING) {
            return false;
        }
        
        registration.setStatus(RegistrationStatus.REJECTED);
        
        // Adjust counters
        regionCounts.get(registration.getGeographicRegion()).decrementAndGet();
        totalBanks.decrementAndGet();
        
        // Remove from active registrations
        registeredBanks.remove(bankId);
        bankChannels.remove(bankId);
        
        eventLog.logEvent("BANK_REJECTED: " + registration.getBankName() + " - Reason: " + reason);
        
        LOGGER.info("Bank registration rejected: " + registration.getBankName() + " - " + reason);
        return true;
    }
    
    /**
     * Gets a bank registration by ID
     */
    public BankRegistration getBankRegistration(String bankId) {
        return registeredBanks.get(bankId);
    }
    
    /**
     * Gets all registered banks
     */
    public Collection<BankRegistration> getAllRegistrations() {
        return new ArrayList<>(registeredBanks.values());
    }
    
    /**
     * Gets banks by geographic region
     */
    public List<BankRegistration> getBanksByRegion(int region) {
        return registeredBanks.values().stream()
            .filter(bank -> bank.getGeographicRegion() == region)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Gets the encrypted channel for a bank
     */
    public EncryptedChannel getBankChannel(String bankId) {
        return bankChannels.get(bankId);
    }
    
    /**
     * Gets geographic distribution statistics
     */
    public Map<Integer, Double> getGeographicDistribution() {
        Map<Integer, Double> distribution = new HashMap<>();
        long total = totalBanks.get();
        
        if (total == 0) {
            for (int i = 1; i <= 4; i++) {
                distribution.put(i, 0.0);
            }
            return distribution;
        }
        
        for (int i = 1; i <= 4; i++) {
            double percentage = (double) regionCounts.get(i).get() / total;
            distribution.put(i, percentage);
        }
        
        return distribution;
    }
    
    /**
     * Checks if the system is government-resistant based on distribution
     */
    public boolean isGovernmentResistant() {
        Map<Integer, Double> distribution = getGeographicDistribution();
        return distribution.values().stream().allMatch(share -> share <= MAX_GEOGRAPHIC_SHARE);
    }
    
    /**
     * Gets system statistics
     */
    public Map<String, Object> getSystemStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalBanks", totalBanks.get());
        stats.put("pendingRegistrations", 
                 registeredBanks.values().stream()
                     .mapToLong(bank -> bank.getStatus() == RegistrationStatus.PENDING ? 1 : 0)
                     .sum());
        stats.put("approvedBanks", 
                 registeredBanks.values().stream()
                     .mapToLong(bank -> bank.getStatus() == RegistrationStatus.APPROVED ? 1 : 0)
                     .sum());
        stats.put("geographicDistribution", getGeographicDistribution());
        stats.put("isGovernmentResistant", isGovernmentResistant());
        stats.put("activeChannels", bankChannels.size());
        
        return stats;
    }
}