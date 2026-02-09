package org.example.app.core.blockchain.consensus;

import org.example.app.core.blockchain.transaction.TokenBankTransaction;
import org.example.app.core.block.Transaction;
import org.example.app.core.banking.registration.BankRegistrationContract;
import org.example.app.core.banking.tokenization.FairrateAlgorithm;
import org.example.app.core.state.BlockchainState;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * TokenBankTransactionValidator - Validates token-to-bank transactions
 * Ensures fair rates, proper bank registration, and government resistance
 * 
 * Core Requirements:
 * - Validates bank is registered and active
 * - Ensures fair rate calculation (not manipulated)
 * - Checks transaction limits and velocity
 * - Validates token backing and reserves
 * - Prevents single-bank domination (33% rule)
 */
public class TokenBankTransactionValidator {
    private static final Logger LOGGER = Logger.getLogger(TokenBankTransactionValidator.class.getName());
    
    // Transaction limits
    private static final BigDecimal MAX_SINGLE_TRANSACTION = new BigDecimal("10000000"); // 10M in base currency
    private static final BigDecimal MIN_TRANSACTION_AMOUNT = new BigDecimal("0.01");
    private static final int MAX_TRANSACTIONS_PER_BLOCK = 10000;
    
    // Velocity controls (anti-money laundering)
    private static final int VELOCITY_WINDOW_SECONDS = 3600; // 1 hour
    private static final BigDecimal MAX_HOURLY_VOLUME_PER_ADDRESS = new BigDecimal("100000000"); // 100M
    
    // Fair rate tolerance (5% deviation from oracle price)
    private static final BigDecimal FAIR_RATE_TOLERANCE = new BigDecimal("0.05");
    
    // Dependencies
    private final BlockchainState blockchainState;
    private final BankRegistrationContract bankRegistry;
    private final FairrateAlgorithm fairRateAlgorithm;
    private final AntiManipulationValidator antiManipulationValidator;
    
    // Transaction tracking for velocity checks
    private final Map<Address, List<VelocityRecord>> velocityTracker;
    private final Map<String, TransactionCache> recentTransactions;
    
    /**
     * Internal class to track transaction velocity
     */
    private static class VelocityRecord {
        final BigDecimal amount;
        final long timestamp;
        
        VelocityRecord(BigDecimal amount, long timestamp) {
            this.amount = amount;
            this.timestamp = timestamp;
        }
        
        boolean isWithinWindow(long currentTime, int windowSeconds) {
            return (currentTime - timestamp) < (windowSeconds * 1000L);
        }
    }
    
    /**
     * Cache for recent transaction validation
     */
    private static class TransactionCache {
        final Hash txHash;
        final long timestamp;
        final boolean isValid;
        
        TransactionCache(Hash txHash, long timestamp, boolean isValid) {
            this.txHash = txHash;
            this.timestamp = timestamp;
            this.isValid = isValid;
        }
    }
    
    /**
     * Validation result with detailed failure reasons
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String failureReason;
        private final Map<String, Object> validationMetrics;
        
        private ValidationResult(boolean valid, String failureReason, Map<String, Object> metrics) {
            this.valid = valid;
            this.failureReason = failureReason;
            this.validationMetrics = metrics != null ? metrics : new HashMap<>();
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, null, new HashMap<>());
        }
        
        public static ValidationResult success(Map<String, Object> metrics) {
            return new ValidationResult(true, null, metrics);
        }
        
        public static ValidationResult failure(String reason) {
            return new ValidationResult(false, reason, new HashMap<>());
        }
        
        public boolean isValid() { return valid; }
        public String getFailureReason() { return failureReason; }
        public Map<String, Object> getValidationMetrics() { return validationMetrics; }
    }
    
    /**
     * Constructor
     */
    public TokenBankTransactionValidator(BlockchainState blockchainState,
                                        BankRegistrationContract bankRegistry,
                                        FairrateAlgorithm fairRateAlgorithm,
                                        AntiManipulationValidator antiManipulationValidator) {
        this.blockchainState = blockchainState;
        this.bankRegistry = bankRegistry;
        this.fairRateAlgorithm = fairRateAlgorithm;
        this.antiManipulationValidator = antiManipulationValidator;
        this.velocityTracker = new ConcurrentHashMap<>();
        this.recentTransactions = new ConcurrentHashMap<>();
        
        LOGGER.info("TokenBankTransactionValidator initialized");
    }
    
    /**
     * Main validation method for token-bank transactions
     */
    public ValidationResult validate(TokenBankTransaction transaction) {
        try {
            LOGGER.fine("Validating TokenBankTransaction: " + transaction.getHash());
            
            // Check cache first
            Hash txHash = transaction.getHash();
            if (recentTransactions.containsKey(txHash.toString())) {
                TransactionCache cached = recentTransactions.get(txHash.toString());
                if (System.currentTimeMillis() - cached.timestamp < 60000) { // 1 minute cache
                    return cached.isValid ? ValidationResult.success() : 
                           ValidationResult.failure("Previously failed validation");
                }
            }
            
            // 1. Validate basic transaction structure
            ValidationResult structureCheck = validateStructure(transaction);
            if (!structureCheck.isValid()) {
                cacheResult(transaction, false);
                return structureCheck;
            }
            
            // 2. Validate bank registration and status
            ValidationResult bankCheck = validateBankRegistration(transaction);
            if (!bankCheck.isValid()) {
                cacheResult(transaction, false);
                return bankCheck;
            }
            
            // 3. Validate transaction amounts
            ValidationResult amountCheck = validateAmounts(transaction);
            if (!amountCheck.isValid()) {
                cacheResult(transaction, false);
                return amountCheck;
            }
            
            // 4. Validate fair rate calculation
            ValidationResult rateCheck = validateFairRate(transaction);
            if (!rateCheck.isValid()) {
                cacheResult(transaction, false);
                return rateCheck;
            }
            
            // 5. Check transaction velocity (anti-money laundering)
            ValidationResult velocityCheck = validateVelocity(transaction);
            if (!velocityCheck.isValid()) {
                cacheResult(transaction, false);
                return velocityCheck;
            }
            
            // 6. Validate reserves backing
            ValidationResult reserveCheck = validateReserveBacking(transaction);
            if (!reserveCheck.isValid()) {
                cacheResult(transaction, false);
                return reserveCheck;
            }
            
            // 7. Check for manipulation attempts
            ValidationResult manipulationCheck = antiManipulationValidator.checkTokenBankTransaction(transaction);
            if (!manipulationCheck.isValid()) {
                cacheResult(transaction, false);
                return manipulationCheck;
            }
            
            // Record velocity for future checks
            recordVelocity(transaction);
            cacheResult(transaction, true);
            
            LOGGER.info("TokenBankTransaction validated successfully: " + transaction.getHash());
            return ValidationResult.success(buildMetrics(transaction));
            
        } catch (Exception e) {
            LOGGER.severe("Validation error: " + e.getMessage());
            return ValidationResult.failure("Validation exception: " + e.getMessage());
        }
    }
    
    /**
     * Validates basic transaction structure
     */
    private ValidationResult validateStructure(TokenBankTransaction transaction) {
        if (transaction == null) {
            return ValidationResult.failure("Transaction is null");
        }
        
        if (transaction.getHash() == null || transaction.getHash().toString().isEmpty()) {
            return ValidationResult.failure("Transaction hash is missing");
        }
        
        if (transaction.getFromAddress() == null) {
            return ValidationResult.failure("From address is missing");
        }
        
        if (transaction.getBankAddress() == null) {
            return ValidationResult.failure("Bank address is missing");
        }
        
        if (transaction.getTokenSymbol() == null || transaction.getTokenSymbol().isEmpty()) {
            return ValidationResult.failure("Token symbol is missing");
        }
        
        if (transaction.getSignature() == null || transaction.getSignature().length == 0) {
            return ValidationResult.failure("Transaction signature is missing");
        }
        
        if (transaction.getTimestamp() <= 0) {
            return ValidationResult.failure("Invalid timestamp");
        }
        
        // Check timestamp is not too far in future or past
        long now = System.currentTimeMillis();
        long txTime = transaction.getTimestamp();
        if (Math.abs(now - txTime) > 300000) { // 5 minutes tolerance
            return ValidationResult.failure("Transaction timestamp out of acceptable range");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates bank registration and status
     */
    private ValidationResult validateBankRegistration(TokenBankTransaction transaction) {
        Address bankAddress = transaction.getBankAddress();
        
        // Get bank registration
        BankRegistrationContract.BankRegistration bank = 
            bankRegistry.getBankRegistration(bankAddress.toString());
        
        if (bank == null) {
            return ValidationResult.failure("Bank not registered: " + bankAddress);
        }
        
        // Check bank status
        if (bank.getStatus() != BankRegistrationContract.RegistrationStatus.APPROVED) {
            return ValidationResult.failure("Bank not approved: " + bankAddress + 
                                          " (Status: " + bank.getStatus() + ")");
        }
        
        // Verify bank certificate is valid
        if (bank.getBankCertificate() == null) {
            return ValidationResult.failure("Bank has no valid certificate");
        }
        
        // Check if bank's country matches the token currency (for domestic transactions)
        if (transaction.isDomestic()) {
            String tokenCurrency = transaction.getTokenSymbol().substring(0, 3); // e.g., GHS from GHS-Token
            if (!bank.getBankCountry().startsWith(tokenCurrency)) {
                return ValidationResult.failure("Domestic transaction requires local bank");
            }
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates transaction amounts
     */
    private ValidationResult validateAmounts(TokenBankTransaction transaction) {
        BigDecimal tokenAmount = transaction.getTokenAmount();
        BigDecimal fiatAmount = transaction.getFiatAmount();
        
        // Check amounts are positive
        if (tokenAmount == null || tokenAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.failure("Token amount must be positive");
        }
        
        if (fiatAmount == null || fiatAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.failure("Fiat amount must be positive");
        }
        
        // Check minimum transaction
        if (fiatAmount.compareTo(MIN_TRANSACTION_AMOUNT) < 0) {
            return ValidationResult.failure("Transaction below minimum: " + MIN_TRANSACTION_AMOUNT);
        }
        
        // Check maximum transaction
        if (fiatAmount.compareTo(MAX_SINGLE_TRANSACTION) > 0) {
            return ValidationResult.failure("Transaction exceeds maximum: " + MAX_SINGLE_TRANSACTION);
        }
        
        // Validate precision (no more than 8 decimal places)
        if (tokenAmount.scale() > 8 || fiatAmount.scale() > 8) {
            return ValidationResult.failure("Amount precision exceeds maximum (8 decimals)");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates fair rate calculation
     */
    private ValidationResult validateFairRate(TokenBankTransaction transaction) {
        try {
            // Get fair rate from algorithm
            BigDecimal fairRate = fairRateAlgorithm.calculateFairRate(
                transaction.getTokenSymbol(),
                transaction.isTokenToFiat()
            );
            
            // Calculate transaction rate
            BigDecimal txRate = transaction.getFiatAmount()
                .divide(transaction.getTokenAmount(), 8, RoundingMode.HALF_UP);
            
            // Calculate deviation
            BigDecimal deviation = txRate.subtract(fairRate)
                .divide(fairRate, 8, RoundingMode.HALF_UP)
                .abs();
            
            // Check if within tolerance
            if (deviation.compareTo(FAIR_RATE_TOLERANCE) > 0) {
                return ValidationResult.failure(
                    String.format("Rate deviation exceeds tolerance. Fair rate: %s, TX rate: %s, Deviation: %.2f%%",
                        fairRate, txRate, deviation.multiply(new BigDecimal(100)).doubleValue())
                );
            }
            
            return ValidationResult.success();
            
        } catch (Exception e) {
            return ValidationResult.failure("Fair rate validation failed: " + e.getMessage());
        }
    }
    
    /**
     * Validates transaction velocity (anti-money laundering)
     */
    private ValidationResult validateVelocity(TokenBankTransaction transaction) {
        Address fromAddress = transaction.getFromAddress();
        long now = System.currentTimeMillis();
        
        // Get velocity records for this address
        List<VelocityRecord> records = velocityTracker.computeIfAbsent(
            fromAddress, k -> Collections.synchronizedList(new ArrayList<>())
        );
        
        // Calculate total volume in window
        BigDecimal volumeInWindow = records.stream()
            .filter(r -> r.isWithinWindow(now, VELOCITY_WINDOW_SECONDS))
            .map(r -> r.amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Add current transaction amount
        volumeInWindow = volumeInWindow.add(transaction.getFiatAmount());
        
        // Check against limit
        if (volumeInWindow.compareTo(MAX_HOURLY_VOLUME_PER_ADDRESS) > 0) {
            return ValidationResult.failure(
                String.format("Hourly volume limit exceeded: %s (limit: %s)",
                    volumeInWindow, MAX_HOURLY_VOLUME_PER_ADDRESS)
            );
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates reserve backing for the token
     */
    private ValidationResult validateReserveBacking(TokenBankTransaction transaction) {
        // This will be implemented with ProofOfReservesValidator
        // For now, basic check that token exists in state
        try {
            byte[] tokenKey = ("TOKEN_" + transaction.getTokenSymbol()).getBytes();
            byte[] tokenData = blockchainState.getState().get(tokenKey);
            
            if (tokenData == null) {
                return ValidationResult.failure("Token not found in state: " + transaction.getTokenSymbol());
            }
            
            return ValidationResult.success();
            
        } catch (Exception e) {
            return ValidationResult.failure("Reserve validation failed: " + e.getMessage());
        }
    }
    
    /**
     * Records transaction for velocity tracking
     */
    private void recordVelocity(TokenBankTransaction transaction) {
        Address fromAddress = transaction.getFromAddress();
        List<VelocityRecord> records = velocityTracker.computeIfAbsent(
            fromAddress, k -> Collections.synchronizedList(new ArrayList<>())
        );
        
        records.add(new VelocityRecord(
            transaction.getFiatAmount(),
            System.currentTimeMillis()
        ));
        
        // Clean old records
        cleanOldVelocityRecords(records);
    }
    
    /**
     * Removes old velocity records outside the window
     */
    private void cleanOldVelocityRecords(List<VelocityRecord> records) {
        long now = System.currentTimeMillis();
        records.removeIf(r -> !r.isWithinWindow(now, VELOCITY_WINDOW_SECONDS * 2)); // Keep 2x window for safety
    }
    
    /**
     * Caches validation result
     */
    private void cacheResult(TokenBankTransaction transaction, boolean isValid) {
        recentTransactions.put(
            transaction.getHash().toString(),
            new TransactionCache(transaction.getHash(), System.currentTimeMillis(), isValid)
        );
        
        // Clean old cache entries
        cleanOldCache();
    }
    
    /**
     * Removes old cache entries
     */
    private void cleanOldCache() {
        long now = System.currentTimeMillis();
        recentTransactions.entrySet().removeIf(
            entry -> (now - entry.getValue().timestamp) > 300000 // 5 minutes
        );
    }
    
    /**
     * Builds validation metrics
     */
    private Map<String, Object> buildMetrics(TokenBankTransaction transaction) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("txHash", transaction.getHash().toString());
        metrics.put("tokenAmount", transaction.getTokenAmount());
        metrics.put("fiatAmount", transaction.getFiatAmount());
        metrics.put("bankAddress", transaction.getBankAddress().toString());
        metrics.put("timestamp", transaction.getTimestamp());
        return metrics;
    }
    
    /**
     * Validates a batch of transactions
     */
    public Map<Hash, ValidationResult> validateBatch(List<TokenBankTransaction> transactions) {
        Map<Hash, ValidationResult> results = new HashMap<>();
        
        if (transactions.size() > MAX_TRANSACTIONS_PER_BLOCK) {
            LOGGER.warning("Batch size exceeds maximum: " + transactions.size());
        }
        
        for (TokenBankTransaction tx : transactions) {
            results.put(tx.getHash(), validate(tx));
        }
        
        return results;
    }
    
    /**
     * Gets velocity statistics for an address
     */
    public Map<String, Object> getVelocityStats(Address address) {
        List<VelocityRecord> records = velocityTracker.get(address);
        Map<String, Object> stats = new HashMap<>();
        
        if (records == null || records.isEmpty()) {
            stats.put("volumeInWindow", BigDecimal.ZERO);
            stats.put("transactionCount", 0);
            return stats;
        }
        
        long now = System.currentTimeMillis();
        BigDecimal volume = records.stream()
            .filter(r -> r.isWithinWindow(now, VELOCITY_WINDOW_SECONDS))
            .map(r -> r.amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        long count = records.stream()
            .filter(r -> r.isWithinWindow(now, VELOCITY_WINDOW_SECONDS))
            .count();
        
        stats.put("volumeInWindow", volume);
        stats.put("transactionCount", count);
        stats.put("remainingCapacity", MAX_HOURLY_VOLUME_PER_ADDRESS.subtract(volume));
        
        return stats;
    }
}