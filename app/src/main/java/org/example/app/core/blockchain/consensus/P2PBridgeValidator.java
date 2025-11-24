package org.example.app.core.blockchain.consensus;

import org.example.app.core.blockchain.transaction.P2PTransferTransaction;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;
import org.example.app.core.state.BlockchainState;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * P2PBridgeValidator - Validates peer-to-peer transfers (bank independence requirement)
 * 
 * Core Requirements (Req #7):
 * - Validates P2P transfers without bank involvement
 * - Ensures offline transaction capabilities
 * - Validates signed certificates for offline transactions
 * - Checks balance availability
 * - Prevents double-spending in P2P context
 * - Validates multi-hop routing for privacy
 * - Supports community mesh network transactions
 * 
 * This is critical for African financial independence - people can transact
 * without depending on banks or centralized infrastructure
 */
public class P2PBridgeValidator {
    private static final Logger LOGGER = Logger.getLogger(P2PBridgeValidator.class.getName());
    
    // Transaction limits
    private static final BigDecimal MAX_P2P_SINGLE_TRANSACTION = new BigDecimal("50000"); // 50K in base currency
    private static final BigDecimal MIN_P2P_TRANSACTION = new BigDecimal("0.01");
    
    // Offline transaction validity
    private static final long MAX_OFFLINE_TX_AGE_MS = 86400000; // 24 hours
    private static final int MAX_OFFLINE_CERTIFICATE_CHAIN_LENGTH = 5;
    
    // Multi-hop routing limits
    private static final int MAX_ROUTING_HOPS = 6;
    private static final BigDecimal MIN_HOP_LIQUIDITY = new BigDecimal("100"); // Min balance at each hop
    
    // Dependencies
    private final BlockchainState blockchainState;
    
    // Tracking for double-spend prevention
    private final Map<String, Set<Hash>> addressNonces; // Address -> Used nonces
    private final Map<Hash, OfflineTransactionRecord> pendingOfflineTransactions;
    private final Map<Address, BigDecimal> balanceCache;
    
    /**
     * Represents an offline transaction record
     */
    private static class OfflineTransactionRecord {
        final Hash txHash;
        final Address fromAddress;
        final Address toAddress;
        final BigDecimal amount;
        final long creationTime;
        final byte[] offlineCertificate;
        final List<byte[]> certificateChain;
        boolean settled;
        
        OfflineTransactionRecord(P2PTransferTransaction tx) {
            this.txHash = tx.getHash();
            this.fromAddress = tx.getFromAddress();
            this.toAddress = tx.getToAddress();
            this.amount = tx.getAmount();
            this.creationTime = tx.getTimestamp();
            this.offlineCertificate = tx.getOfflineCertificate();
            this.certificateChain = tx.getCertificateChain();
            this.settled = false;
        }
    }
    
    /**
     * Validation result
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String failureReason;
        private final Map<String, Object> metadata;
        
        private ValidationResult(boolean valid, String reason, Map<String, Object> metadata) {
            this.valid = valid;
            this.failureReason = reason;
            this.metadata = metadata != null ? metadata : new HashMap<>();
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, null, new HashMap<>());
        }
        
        public static ValidationResult success(Map<String, Object> metadata) {
            return new ValidationResult(true, null, metadata);
        }
        
        public static ValidationResult failure(String reason) {
            return new ValidationResult(false, reason, new HashMap<>());
        }
        
        public boolean isValid() { return valid; }
        public String getFailureReason() { return failureReason; }
        public Map<String, Object> getMetadata() { return metadata; }
    }
    
    /**
     * Constructor
     */
    public P2PBridgeValidator(BlockchainState blockchainState) {
        this.blockchainState = blockchainState;
        this.addressNonces = new ConcurrentHashMap<>();
        this.pendingOfflineTransactions = new ConcurrentHashMap<>();
        this.balanceCache = new ConcurrentHashMap<>();
        
        LOGGER.info("P2PBridgeValidator initialized");
    }
    
    /**
     * Main validation method for P2P transfers
     */
    public ValidationResult validate(P2PTransferTransaction transaction) {
        try {
            LOGGER.fine("Validating P2P transaction: " + transaction.getHash());
            
            // 1. Validate basic structure
            ValidationResult structureCheck = validateStructure(transaction);
            if (!structureCheck.isValid()) {
                return structureCheck;
            }
            
            // 2. Validate amounts
            ValidationResult amountCheck = validateAmounts(transaction);
            if (!amountCheck.isValid()) {
                return amountCheck;
            }
            
            // 3. Check for double-spending
            ValidationResult doubleSpendCheck = checkDoubleSpend(transaction);
            if (!doubleSpendCheck.isValid()) {
                return doubleSpendCheck;
            }
            
            // 4. Validate balance availability
            ValidationResult balanceCheck = validateBalance(transaction);
            if (!balanceCheck.isValid()) {
                return balanceCheck;
            }
            
            // 5. If offline transaction, validate certificate
            if (transaction.isOffline()) {
                ValidationResult offlineCheck = validateOfflineTransaction(transaction);
                if (!offlineCheck.isValid()) {
                    return offlineCheck;
                }
            }
            
            // 6. If multi-hop routing, validate route
            if (transaction.hasRoutingPath()) {
                ValidationResult routingCheck = validateRoutingPath(transaction);
                if (!routingCheck.isValid()) {
                    return routingCheck;
                }
            }
            
            // 7. Validate signature
            ValidationResult signatureCheck = validateSignature(transaction);
            if (!signatureCheck.isValid()) {
                return signatureCheck;
            }
            
            // Record nonce to prevent replay
            recordNonce(transaction);
            
            LOGGER.info("P2P transaction validated successfully: " + transaction.getHash());
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("fromAddress", transaction.getFromAddress().toString());
            metadata.put("toAddress", transaction.getToAddress().toString());
            metadata.put("amount", transaction.getAmount());
            metadata.put("isOffline", transaction.isOffline());
            metadata.put("hasRouting", transaction.hasRoutingPath());
            
            return ValidationResult.success(metadata);
            
        } catch (Exception e) {
            LOGGER.severe("P2P validation error: " + e.getMessage());
            return ValidationResult.failure("Validation exception: " + e.getMessage());
        }
    }
    
    /**
     * Validates basic transaction structure
     */
    private ValidationResult validateStructure(P2PTransferTransaction transaction) {
        if (transaction == null) {
            return ValidationResult.failure("Transaction is null");
        }
        
        if (transaction.getHash() == null) {
            return ValidationResult.failure("Transaction hash is missing");
        }
        
        if (transaction.getFromAddress() == null) {
            return ValidationResult.failure("From address is missing");
        }
        
        if (transaction.getToAddress() == null) {
            return ValidationResult.failure("To address is missing");
        }
        
        if (transaction.getFromAddress().equals(transaction.getToAddress())) {
            return ValidationResult.failure("Cannot transfer to same address");
        }
        
        if (transaction.getCurrencyCode() == null || transaction.getCurrencyCode().isEmpty()) {
            return ValidationResult.failure("Currency code is missing");
        }
        
        if (transaction.getTimestamp() <= 0) {
            return ValidationResult.failure("Invalid timestamp");
        }
        
        // Check timestamp is not too far in future or past
        long now = System.currentTimeMillis();
        long txTime = transaction.getTimestamp();
        
        // Allow wider window for offline transactions
        long tolerance = transaction.isOffline() ? MAX_OFFLINE_TX_AGE_MS : 300000; // 5 mins for online
        
        if (Math.abs(now - txTime) > tolerance) {
            return ValidationResult.failure("Transaction timestamp out of acceptable range");
        }
        
        if (transaction.getNonce() < 0) {
            return ValidationResult.failure("Invalid nonce");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates transaction amounts
     */
    private ValidationResult validateAmounts(P2PTransferTransaction transaction) {
        BigDecimal amount = transaction.getAmount();
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.failure("Amount must be positive");
        }
        
        if (amount.compareTo(MIN_P2P_TRANSACTION) < 0) {
            return ValidationResult.failure("Amount below minimum: " + MIN_P2P_TRANSACTION);
        }
        
        if (amount.compareTo(MAX_P2P_SINGLE_TRANSACTION) > 0) {
            return ValidationResult.failure("Amount exceeds maximum: " + MAX_P2P_SINGLE_TRANSACTION);
        }
        
        // Validate fee
        BigDecimal fee = transaction.getFee();
        if (fee != null && fee.compareTo(BigDecimal.ZERO) < 0) {
            return ValidationResult.failure("Fee cannot be negative");
        }
        
        // Check precision (max 8 decimals)
        if (amount.scale() > 8) {
            return ValidationResult.failure("Amount precision exceeds maximum (8 decimals)");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Checks for double-spending
     */
    private ValidationResult checkDoubleSpend(P2PTransferTransaction transaction) {
        String addressKey = transaction.getFromAddress().toString();
        Set<Hash> usedNonces = addressNonces.computeIfAbsent(
            addressKey, k -> ConcurrentHashMap.newKeySet()
        );
        
        // Check if this nonce has been used
        if (usedNonces.contains(transaction.getHash())) {
            return ValidationResult.failure("Transaction already processed (double-spend attempt)");
        }
        
        // For offline transactions, check against pending
        if (transaction.isOffline()) {
            if (pendingOfflineTransactions.containsKey(transaction.getHash())) {
                OfflineTransactionRecord record = pendingOfflineTransactions.get(transaction.getHash());
                if (record.settled) {
                    return ValidationResult.failure("Offline transaction already settled");
                }
            }
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates balance availability
     */
    private ValidationResult validateBalance(P2PTransferTransaction transaction) {
        Address fromAddress = transaction.getFromAddress();
        BigDecimal amount = transaction.getAmount();
        BigDecimal fee = transaction.getFee() != null ? transaction.getFee() : BigDecimal.ZERO;
        BigDecimal totalRequired = amount.add(fee);
        
        // Get balance from cache or state
        BigDecimal balance = getBalance(fromAddress, transaction.getCurrencyCode());
        
        if (balance.compareTo(totalRequired) < 0) {
            return ValidationResult.failure(
                String.format("Insufficient balance. Required: %s, Available: %s",
                    totalRequired, balance)
            );
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates offline transaction certificate
     */
    private ValidationResult validateOfflineTransaction(P2PTransferTransaction transaction) {
        byte[] certificate = transaction.getOfflineCertificate();
        
        if (certificate == null || certificate.length == 0) {
            return ValidationResult.failure("Offline certificate is missing");
        }
        
        // Validate certificate age
        long txAge = System.currentTimeMillis() - transaction.getTimestamp();
        if (txAge > MAX_OFFLINE_TX_AGE_MS) {
            return ValidationResult.failure(
                String.format("Offline transaction too old: %d hours (max: 24)",
                    txAge / 3600000)
            );
        }
        
        // Validate certificate chain
        List<byte[]> chain = transaction.getCertificateChain();
        if (chain != null) {
            if (chain.size() > MAX_OFFLINE_CERTIFICATE_CHAIN_LENGTH) {
                return ValidationResult.failure(
                    "Certificate chain too long: " + chain.size() + 
                    " (max: " + MAX_OFFLINE_CERTIFICATE_CHAIN_LENGTH + ")"
                );
            }
            
            // Verify chain integrity
            ValidationResult chainCheck = verifyCertificateChain(chain);
            if (!chainCheck.isValid()) {
                return chainCheck;
            }
        }
        
        // Record for settlement when online
        pendingOfflineTransactions.put(
            transaction.getHash(),
            new OfflineTransactionRecord(transaction)
        );
        
        return ValidationResult.success();
    }
    
    /**
     * Verifies certificate chain integrity
     */
    private ValidationResult verifyCertificateChain(List<byte[]> chain) {
        if (chain.isEmpty()) {
            return ValidationResult.success();
        }
        
        // Each certificate should be properly signed by previous one
        for (int i = 1; i < chain.size(); i++) {
            byte[] prevCert = chain.get(i - 1);
            byte[] currentCert = chain.get(i);
            
            // Verify signature (simplified - in production use actual crypto verification)
            if (prevCert.length == 0 || currentCert.length == 0) {
                return ValidationResult.failure(
                    "Invalid certificate in chain at position " + i
                );
            }
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates multi-hop routing path
     */
    private ValidationResult validateRoutingPath(P2PTransferTransaction transaction) {
        List<Address> routingPath = transaction.getRoutingPath();
        
        if (routingPath == null || routingPath.isEmpty()) {
            return ValidationResult.failure("Routing path is empty");
        }
        
        if (routingPath.size() > MAX_ROUTING_HOPS) {
            return ValidationResult.failure(
                "Routing path too long: " + routingPath.size() + 
                " hops (max: " + MAX_ROUTING_HOPS + ")"
            );
        }
        
        // Validate each hop has sufficient liquidity
        for (int i = 0; i < routingPath.size(); i++) {
            Address hopAddress = routingPath.get(i);
            
            if (hopAddress == null) {
                return ValidationResult.failure("Invalid address in routing path at position " + i);
            }
            
            // Check hop balance
            BigDecimal hopBalance = getBalance(hopAddress, transaction.getCurrencyCode());
            if (hopBalance.compareTo(MIN_HOP_LIQUIDITY) < 0) {
                return ValidationResult.failure(
                    String.format("Insufficient liquidity at hop %d: %s (min: %s)",
                        i, hopBalance, MIN_HOP_LIQUIDITY)
                );
            }
        }
        
        // Validate path doesn't contain sender or receiver in middle
        for (int i = 1; i < routingPath.size() - 1; i++) {
            if (routingPath.get(i).equals(transaction.getFromAddress()) ||
                routingPath.get(i).equals(transaction.getToAddress())) {
                return ValidationResult.failure("Sender/receiver cannot be in middle of routing path");
            }
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates transaction signature
     */
    private ValidationResult validateSignature(P2PTransferTransaction transaction) {
        byte[] signature = transaction.getSignature();
        
        if (signature == null || signature.length == 0) {
            return ValidationResult.failure("Transaction signature is missing");
        }
        
        // Validate signature length (typical ECDSA signature is 64-72 bytes)
        if (signature.length < 64 || signature.length > 128) {
            return ValidationResult.failure("Invalid signature length");
        }
        
        // In production, verify signature cryptographically
        // This is a placeholder for the actual crypto verification
        // which should use the sender's public key to verify the signature
        
        return ValidationResult.success();
    }
    
    /**
     * Gets balance from cache or state
     */
    private BigDecimal getBalance(Address address, String currencyCode) {
        String cacheKey = address.toString() + "_" + currencyCode;
        
        // Check cache first
        BigDecimal cachedBalance = balanceCache.get(address);
        if (cachedBalance != null) {
            return cachedBalance;
        }
        
        // Query from blockchain state
        try {
            byte[] balanceKey = ("BALANCE_" + address.toString() + "_" + currencyCode).getBytes();
            byte[] balanceData = blockchainState.getState().get(balanceKey);
            
            if (balanceData == null) {
                return BigDecimal.ZERO;
            }
            
            BigDecimal balance = new BigDecimal(new String(balanceData));
            balanceCache.put(address, balance); // Cache for future use
            
            return balance;
            
        } catch (Exception e) {
            LOGGER.warning("Failed to get balance for " + address + ": " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Records nonce to prevent replay attacks
     */
    private void recordNonce(P2PTransferTransaction transaction) {
        String addressKey = transaction.getFromAddress().toString();
        Set<Hash> usedNonces = addressNonces.computeIfAbsent(
            addressKey, k -> ConcurrentHashMap.newKeySet()
        );
        
        usedNonces.add(transaction.getHash());
        
        // Cleanup old nonces periodically (keep last 10000 per address)
        if (usedNonces.size() > 10000) {
            // In production, implement proper cleanup based on block height
            LOGGER.fine("Nonce set size limit reached for " + addressKey);
        }
    }
    
    /**
     * Settles an offline transaction
     */
    public ValidationResult settleOfflineTransaction(Hash txHash) {
        OfflineTransactionRecord record = pendingOfflineTransactions.get(txHash);
        
        if (record == null) {
            return ValidationResult.failure("Offline transaction not found");
        }
        
        if (record.settled) {
            return ValidationResult.failure("Transaction already settled");
        }
        
        // Mark as settled
        record.settled = true;
        
        LOGGER.info("Offline transaction settled: " + txHash);
        return ValidationResult.success();
    }
    
    /**
     * Gets pending offline transactions
     */
    public List<OfflineTransactionRecord> getPendingOfflineTransactions() {
        return pendingOfflineTransactions.values().stream()
            .filter(record -> !record.settled)
            .toList();
    }
    
    /**
     * Gets statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalAddressesTracked", addressNonces.size());
        stats.put("pendingOfflineTransactions", 
            pendingOfflineTransactions.values().stream()
                .filter(r -> !r.settled)
                .count());
        stats.put("settledOfflineTransactions",
            pendingOfflineTransactions.values().stream()
                .filter(r -> r.settled)
                .count());
        stats.put("cachedBalances", balanceCache.size());
        
        return stats;
    }
    
    /**
     * Clears balance cache
     */
    public void clearBalanceCache() {
        balanceCache.clear();
        LOGGER.info("Balance cache cleared");
    }
}