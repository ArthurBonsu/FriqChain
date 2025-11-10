package org.example.app.core.banking.bridge;

import org.example.app.core.crypto.PKIManager;
import org.example.app.core.state.StateManager;
import org.example.app.core.state.BlockchainState;
import org.example.app.core.mempool.TransactionValidator;
import org.example.app.core.internal.EventLog;
import org.example.app.core.config.BlockchainConfig;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * SettlementReconciliation - Final settlement and reconciliation system
 * 
 * This system provides immutable settlement records for all fiat-token conversions,
 * implements government-resistant reconciliation mechanisms, and ensures perfect
 * balance matching between fiat reserves and tokenized assets across all banks.
 */
public class SettlementReconciliation {
    private static final Logger LOGGER = Logger.getLogger(SettlementReconciliation.class.getName());
    
    // Settlement constants
    private static final long SETTLEMENT_BATCH_SIZE = 1000;        // Max transactions per batch
    private static final int SETTLEMENT_INTERVAL_MINUTES = 30;     // Settlement frequency
    private static final double MAX_VARIANCE_THRESHOLD = 0.001;    // 0.1% max variance allowed
    private static final int RECONCILIATION_WINDOW_HOURS = 24;     // Reconciliation period
    private static final int MAX_RETRY_ATTEMPTS = 5;              // Max settlement retry attempts
    
    // Settlement status
    public enum SettlementStatus {
        PENDING,          // Awaiting settlement
        IN_PROGRESS,      // Settlement processing
        SETTLED,          // Successfully settled
        FAILED,           // Settlement failed
        DISPUTED,         // Settlement disputed
        RECONCILING,      // Under reconciliation
        FINALIZED        // Final and immutable
    }
    
    // Settlement types
    public enum SettlementType {
        FIAT_TO_TOKEN,    // Fiat currency to token conversion
        TOKEN_TO_FIAT,    // Token to fiat currency conversion
        CROSS_CURRENCY,   // Cross-currency conversion
        RESERVE_UPDATE,   // Reserve balance update
        BATCH_SETTLEMENT, // Batch of multiple transactions
        EMERGENCY_SETTLE  // Emergency settlement
    }
    
    /**
     * Represents a settlement record
     */
    public static class SettlementRecord {
        private final String settlementId;
        private final String batchId;
        private final SettlementType type;
        private final String bankId;
        private final Address bankAddress;
        private final String fromCurrency;
        private final String toCurrency;
        private final BigDecimal fromAmount;
        private final BigDecimal toAmount;
        private final BigDecimal exchangeRate;
        private final BigDecimal fees;
        private final Date settlementTime;
        private final List<String> transactionIds;
        private final Map<String, Object> settlementData;
        
        private SettlementStatus status;
        private String settlementHash;
        private byte[] cryptographicProof;
        private Date confirmationTime;
        private int retryCount;
        private String errorMessage;
        
        public SettlementRecord(SettlementType type, String bankId, Address bankAddress,
                               String fromCurrency, String toCurrency, BigDecimal fromAmount,
                               BigDecimal toAmount, BigDecimal exchangeRate, BigDecimal fees) {
            this.settlementId = UUID.randomUUID().toString();
            this.batchId = null; // Set during batch processing
            this.type = type;
            this.bankId = bankId;
            this.bankAddress = bankAddress;
            this.fromCurrency = fromCurrency;
            this.toCurrency = toCurrency;
            this.fromAmount = fromAmount;
            this.toAmount = toAmount;
            this.exchangeRate = exchangeRate;
            this.fees = fees;
            this.settlementTime = new Date();
            this.transactionIds = new ArrayList<>();
            this.settlementData = new HashMap<>();
            this.status = SettlementStatus.PENDING;
            this.retryCount = 0;
        }
        
        // Getters
        public String getSettlementId() { return settlementId; }
        public String getBatchId() { return batchId; }
        public SettlementType getType() { return type; }
        public String getBankId() { return bankId; }
        public Address getBankAddress() { return bankAddress; }
        public String getFromCurrency() { return fromCurrency; }
        public String getToCurrency() { return toCurrency; }
        public BigDecimal getFromAmount() { return fromAmount; }
        public BigDecimal getToAmount() { return toAmount; }
        public BigDecimal getExchangeRate() { return exchangeRate; }
        public BigDecimal getFees() { return fees; }
        public Date getSettlementTime() { return settlementTime; }
        public List<String> getTransactionIds() { return new ArrayList<>(transactionIds); }
        public Map<String, Object> getSettlementData() { return new HashMap<>(settlementData); }
        public SettlementStatus getStatus() { return status; }
        public String getSettlementHash() { return settlementHash; }
        public byte[] getCryptographicProof() { return cryptographicProof != null ? 
            Arrays.copyOf(cryptographicProof, cryptographicProof.length) : null; }
        public Date getConfirmationTime() { return confirmationTime; }
        public int getRetryCount() { return retryCount; }
        public String getErrorMessage() { return errorMessage; }
        
        // Setters
        public void setStatus(SettlementStatus status) { this.status = status; }
        public void setSettlementHash(String hash) { this.settlementHash = hash; }
        public void setCryptographicProof(byte[] proof) { 
            this.cryptographicProof = proof != null ? Arrays.copyOf(proof, proof.length) : null; 
        }
        public void setConfirmationTime(Date time) { this.confirmationTime = time; }
        public void incrementRetryCount() { this.retryCount++; }
        public void setErrorMessage(String message) { this.errorMessage = message; }
        
        public void addTransactionId(String txId) {
            transactionIds.add(txId);
        }
        
        public void addSettlementData(String key, Object value) {
            settlementData.put(key, value);
        }
        
        public boolean isFinalized() {
            return status == SettlementStatus.FINALIZED;
        }
        
        public boolean canRetry() {
            return retryCount < MAX_RETRY_ATTEMPTS && 
                   (status == SettlementStatus.FAILED || status == SettlementStatus.DISPUTED);
        }
        
        public BigDecimal getNetAmount() {
            return toAmount.subtract(fees);
        }
        
        /**
         * Generates settlement hash for integrity verification
         */
        public String generateSettlementHash() {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update(settlementId.getBytes());
                digest.update(bankId.getBytes());
                digest.update(fromCurrency.getBytes());
                digest.update(toCurrency.getBytes());
                digest.update(fromAmount.toString().getBytes());
                digest.update(toAmount.toString().getBytes());
                digest.update(exchangeRate.toString().getBytes());
                digest.update(fees.toString().getBytes());
                digest.update(String.valueOf(settlementTime.getTime()).getBytes());
                
                return Base64.getEncoder().encodeToString(digest.digest());
            } catch (Exception e) {
                return null;
            }
        }
    }
    
    /**
     * Represents a settlement batch
     */
    public static class SettlementBatch {
        private final String batchId;
        private final Date batchTime;
        private final List<SettlementRecord> settlements;
        private final Map<String, BigDecimal> currencyTotals;  // currency -> net amount
        private final Map<String, BigDecimal> bankTotals;      // bankId -> net amount
        
        private SettlementStatus batchStatus;
        private String batchHash;
        private Date processingStartTime;
        private Date processingEndTime;
        private int totalTransactions;
        
        public SettlementBatch() {
            this.batchId = UUID.randomUUID().toString();
            this.batchTime = new Date();
            this.settlements = new ArrayList<>();
            this.currencyTotals = new HashMap<>();
            this.bankTotals = new HashMap<>();
            this.batchStatus = SettlementStatus.PENDING;
            this.totalTransactions = 0;
        }
        
        // Getters
        public String getBatchId() { return batchId; }
        public Date getBatchTime() { return batchTime; }
        public List<SettlementRecord> getSettlements() { return new ArrayList<>(settlements); }
        public Map<String, BigDecimal> getCurrencyTotals() { return new HashMap<>(currencyTotals); }
        public Map<String, BigDecimal> getBankTotals() { return new HashMap<>(bankTotals); }
        public SettlementStatus getBatchStatus() { return batchStatus; }
        public String getBatchHash() { return batchHash; }
        public Date getProcessingStartTime() { return processingStartTime; }
        public Date getProcessingEndTime() { return processingEndTime; }
        public int getTotalTransactions() { return totalTransactions; }
        
        // Setters
        public void setBatchStatus(SettlementStatus status) { this.batchStatus = status; }
        public void setBatchHash(String hash) { this.batchHash = hash; }
        public void setProcessingStartTime(Date time) { this.processingStartTime = time; }
        public void setProcessingEndTime(Date time) { this.processingEndTime = time; }
        
        public void addSettlement(SettlementRecord settlement) {
            settlements.add(settlement);
            updateTotals(settlement);
            totalTransactions++;
        }
        
        private void updateTotals(SettlementRecord settlement) {
            // Update currency totals
            String currency = settlement.getToCurrency();
            BigDecimal currentTotal = currencyTotals.getOrDefault(currency, BigDecimal.ZERO);
            currencyTotals.put(currency, currentTotal.add(settlement.getNetAmount()));
            
            // Update bank totals
            String bankId = settlement.getBankId();
            BigDecimal currentBankTotal = bankTotals.getOrDefault(bankId, BigDecimal.ZERO);
            bankTotals.put(bankId, currentBankTotal.add(settlement.getNetAmount()));
        }
        
        public boolean isEmpty() {
            return settlements.isEmpty();
        }
        
        public boolean isFull() {
            return settlements.size() >= SETTLEMENT_BATCH_SIZE;
        }
        
        /**
         * Generates batch hash for integrity verification
         */
        public String generateBatchHash() {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update(batchId.getBytes());
                digest.update(String.valueOf(batchTime.getTime()).getBytes());
                digest.update(String.valueOf(totalTransactions).getBytes());
                
                // Add settlement hashes
                for (SettlementRecord settlement : settlements) {
                    String settlementHash = settlement.generateSettlementHash();
                    if (settlementHash != null) {
                        digest.update(settlementHash.getBytes());
                    }
                }
                
                return Base64.getEncoder().encodeToString(digest.digest());
            } catch (Exception e) {
                return null;
            }
        }
    }
    
    /**
     * Represents reconciliation results
     */
    public static class ReconciliationResult {
        private final String reconciliationId;
        private final Date reconciliationTime;
        private final String currency;
        private final BigDecimal expectedBalance;
        private final BigDecimal actualBalance;
        private final BigDecimal variance;
        private final double variancePercentage;
        private final boolean isReconciled;
        private final List<String> discrepancies;
        
        public ReconciliationResult(String currency, BigDecimal expectedBalance, 
                                   BigDecimal actualBalance) {
            this.reconciliationId = UUID.randomUUID().toString();
            this.reconciliationTime = new Date();
            this.currency = currency;
            this.expectedBalance = expectedBalance;
            this.actualBalance = actualBalance;
            this.variance = actualBalance.subtract(expectedBalance);
            this.variancePercentage = expectedBalance.compareTo(BigDecimal.ZERO) == 0 ? 0.0 :
                variance.abs().divide(expectedBalance.abs(), 6, RoundingMode.HALF_UP).doubleValue();
            this.isReconciled = variancePercentage <= MAX_VARIANCE_THRESHOLD;
            this.discrepancies = new ArrayList<>();
        }
        
        // Getters
        public String getReconciliationId() { return reconciliationId; }
        public Date getReconciliationTime() { return reconciliationTime; }
        public String getCurrency() { return currency; }
        public BigDecimal getExpectedBalance() { return expectedBalance; }
        public BigDecimal getActualBalance() { return actualBalance; }
        public BigDecimal getVariance() { return variance; }
        public double getVariancePercentage() { return variancePercentage; }
        public boolean isReconciled() { return isReconciled; }
        public List<String> getDiscrepancies() { return new ArrayList<>(discrepancies); }
        
        public void addDiscrepancy(String discrepancy) {
            discrepancies.add(discrepancy);
        }
    }
    
    // Core infrastructure
    private final StateManager stateManager;
    private final BlockchainState blockchainState;
    private final TransactionValidator transactionValidator;
    private final PKIManager pkiManager;
    private final EventLog eventLog;
    private final BlockchainConfig config;
    
    // Settlement state
    private final Map<String, SettlementRecord> pendingSettlements;  // settlementId -> record
    private final Map<String, SettlementRecord> completedSettlements; // settlementId -> record
    private final Map<String, SettlementBatch> activeBatches;         // batchId -> batch
    private final Map<String, SettlementBatch> processedBatches;      // batchId -> batch
    private final Map<String, List<String>> bankSettlements;          // bankId -> settlement IDs
    
    // Reconciliation state
    private final Map<String, BigDecimal> expectedBalances;           // currency -> balance
    private final Map<String, BigDecimal> actualBalances;             // currency -> balance
    private final Map<String, ReconciliationResult> reconciliationResults;
    
    // Counters and services
    private final AtomicLong totalSettlements;
    private final AtomicLong successfulSettlements;
    private final ScheduledExecutorService settlementExecutor;
    private final SecureRandom secureRandom;
    
    // Current working batch
    private SettlementBatch currentBatch;
    
    /**
     * Constructor
     */
    public SettlementReconciliation(StateManager stateManager,
                                   BlockchainState blockchainState,
                                   TransactionValidator transactionValidator,
                                   PKIManager pkiManager,
                                   EventLog eventLog,
                                   BlockchainConfig config) {
        this.stateManager = stateManager;
        this.blockchainState = blockchainState;
        this.transactionValidator = transactionValidator;
        this.pkiManager = pkiManager;
        this.eventLog = eventLog;
        this.config = config;
        
        this.pendingSettlements = new ConcurrentHashMap<>();
        this.completedSettlements = new ConcurrentHashMap<>();
        this.activeBatches = new ConcurrentHashMap<>();
        this.processedBatches = new ConcurrentHashMap<>();
        this.bankSettlements = new ConcurrentHashMap<>();
        
        this.expectedBalances = new ConcurrentHashMap<>();
        this.actualBalances = new ConcurrentHashMap<>();
        this.reconciliationResults = new ConcurrentHashMap<>();
        
        this.totalSettlements = new AtomicLong(0);
        this.successfulSettlements = new AtomicLong(0);
        this.settlementExecutor = Executors.newScheduledThreadPool(3);
        this.secureRandom = new SecureRandom();
        
        this.currentBatch = new SettlementBatch();
        
        startSettlementServices();
        
        LOGGER.info("SettlementReconciliation initialized");
    }
    
    /**
     * Records a settlement for processing
     */
    public String recordSettlement(SettlementType type, String bankId, Address bankAddress,
                                  String fromCurrency, String toCurrency, BigDecimal fromAmount,
                                  BigDecimal toAmount, BigDecimal exchangeRate, BigDecimal fees,
                                  List<String> transactionIds) {
        try {
            LOGGER.info("Recording settlement: " + bankId + " (" + fromAmount + " " + 
                       fromCurrency + " -> " + toAmount + " " + toCurrency + ")");
            
            // Create settlement record
            SettlementRecord settlement = new SettlementRecord(type, bankId, bankAddress,
                                                              fromCurrency, toCurrency,
                                                              fromAmount, toAmount,
                                                              exchangeRate, fees);
            
            // Add transaction IDs
            if (transactionIds != null) {
                transactionIds.forEach(settlement::addTransactionId);
            }
            
            // Generate settlement hash
            String settlementHash = settlement.generateSettlementHash();
            settlement.setSettlementHash(settlementHash);
            
            // Add to pending settlements
            pendingSettlements.put(settlement.getSettlementId(), settlement);
            
            // Update bank mapping
            bankSettlements.computeIfAbsent(bankId, k -> new ArrayList<>())
                          .add(settlement.getSettlementId());
            
            // Add to current batch
            synchronized (currentBatch) {
                currentBatch.addSettlement(settlement);
                
                // Check if batch is full
                if (currentBatch.isFull()) {
                    processBatch(currentBatch);
                    currentBatch = new SettlementBatch();
                }
            }
            
            totalSettlements.incrementAndGet();
            
            eventLog.logEvent("SETTLEMENT_RECORDED: " + settlement.getSettlementId() + 
                            " (Bank: " + bankId + ", Amount: " + toAmount + " " + toCurrency + ")");
            
            LOGGER.info("Settlement recorded: " + settlement.getSettlementId());
            return settlement.getSettlementId();
            
        } catch (Exception e) {
            LOGGER.severe("Error recording settlement: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Processes a settlement batch
     */
    private void processBatch(SettlementBatch batch) {
        settlementExecutor.submit(() -> {
            try {
                LOGGER.info("Processing settlement batch: " + batch.getBatchId() + 
                           " (Transactions: " + batch.getTotalTransactions() + ")");
                
                batch.setBatchStatus(SettlementStatus.IN_PROGRESS);
                batch.setProcessingStartTime(new Date());
                
                // Generate batch hash
                String batchHash = batch.generateBatchHash();
                batch.setBatchHash(batchHash);
                
                // Process each settlement in the batch
                boolean batchSuccess = true;
                for (SettlementRecord settlement : batch.getSettlements()) {
                    boolean settled = processIndividualSettlement(settlement);
                    if (!settled && !settlement.canRetry()) {
                        batchSuccess = false;
                    }
                }
                
                // Update batch status
                SettlementStatus finalStatus = batchSuccess ? SettlementStatus.SETTLED : SettlementStatus.FAILED;
                batch.setBatchStatus(finalStatus);
                batch.setProcessingEndTime(new Date());
                
                // Move to processed batches
                activeBatches.remove(batch.getBatchId());
                processedBatches.put(batch.getBatchId(), batch);
                
                // Update balance tracking
                updateExpectedBalances(batch);
                
                eventLog.logEvent("SETTLEMENT_BATCH_PROCESSED: " + batch.getBatchId() + 
                                " (Status: " + finalStatus + ")");
                
                LOGGER.info("Settlement batch processed: " + batch.getBatchId() + 
                           " (Status: " + finalStatus + ")");
                
            } catch (Exception e) {
                LOGGER.severe("Error processing settlement batch: " + e.getMessage());
                batch.setBatchStatus(SettlementStatus.FAILED);
            }
        });
    }
    
    /**
     * Processes an individual settlement
     */
    private boolean processIndividualSettlement(SettlementRecord settlement) {
        try {
            settlement.setStatus(SettlementStatus.IN_PROGRESS);
            
            // Validate settlement data
            if (!validateSettlement(settlement)) {
                settlement.setStatus(SettlementStatus.FAILED);
                settlement.setErrorMessage("Settlement validation failed");
                return false;
            }
            
            // Generate cryptographic proof
            byte[] proof = generateCryptographicProof(settlement);
            settlement.setCryptographicProof(proof);
            
            // Execute settlement (in a real system, this would interact with banks)
            boolean executed = executeSettlement(settlement);
            
            if (executed) {
                settlement.setStatus(SettlementStatus.SETTLED);
                settlement.setConfirmationTime(new Date());
                
                // Move to completed settlements
                pendingSettlements.remove(settlement.getSettlementId());
                completedSettlements.put(settlement.getSettlementId(), settlement);
                
                successfulSettlements.incrementAndGet();
                
                LOGGER.info("Settlement processed successfully: " + settlement.getSettlementId());
                return true;
            } else {
                settlement.setStatus(SettlementStatus.FAILED);
                settlement.incrementRetryCount();
                settlement.setErrorMessage("Settlement execution failed");
                
                LOGGER.warning("Settlement execution failed: " + settlement.getSettlementId());
                return false;
            }
            
        } catch (Exception e) {
            LOGGER.severe("Error processing individual settlement: " + e.getMessage());
            settlement.setStatus(SettlementStatus.FAILED);
            settlement.setErrorMessage("Processing error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Validates a settlement record
     */
    private boolean validateSettlement(SettlementRecord settlement) {
        // Basic validation
        if (settlement.getFromAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        if (settlement.getToAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        if (settlement.getExchangeRate().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        // Validate calculation consistency
        BigDecimal expectedToAmount = settlement.getFromAmount()
            .multiply(settlement.getExchangeRate())
            .subtract(settlement.getFees());
        
        BigDecimal variance = settlement.getToAmount().subtract(expectedToAmount).abs();
        BigDecimal allowedVariance = settlement.getToAmount().multiply(
            BigDecimal.valueOf(MAX_VARIANCE_THRESHOLD));
        
        if (variance.compareTo(allowedVariance) > 0) {
            LOGGER.warning("Settlement calculation variance too high: " + variance);
            return false;
        }
        
        return true;
    }
    
    /**
     * Generates cryptographic proof for a settlement
     */
    private byte[] generateCryptographicProof(SettlementRecord settlement) {
        try {
            // In a real implementation, this would generate proper cryptographic proof
            // including signatures from multiple parties, merkle proofs, etc.
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(settlement.getSettlementHash().getBytes());
            digest.update(settlement.getBankId().getBytes());
            digest.update(String.valueOf(System.currentTimeMillis()).getBytes());
            
            return digest.digest();
            
        } catch (Exception e) {
            LOGGER.warning("Error generating cryptographic proof: " + e.getMessage());
            return new byte[32]; // Return empty proof
        }
    }
    
    /**
     * Executes the actual settlement
     */
    private boolean executeSettlement(SettlementRecord settlement) {
        try {
            // In a real implementation, this would:
            // 1. Interact with bank APIs
            // 2. Update token supplies
            // 3. Transfer funds
            // 4. Update blockchain state
            
            // For now, simulate successful settlement
            Thread.sleep(100); // Simulate processing time
            
            // Add settlement data
            settlement.addSettlementData("executionTime", new Date());
            settlement.addSettlementData("method", "AUTOMATED_SETTLEMENT");
            settlement.addSettlementData("confirmationNumber", generateConfirmationNumber());
            
            return true;
            
        } catch (Exception e) {
            LOGGER.warning("Error executing settlement: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Updates expected balance tracking
     */
    private void updateExpectedBalances(SettlementBatch batch) {
        for (Map.Entry<String, BigDecimal> entry : batch.getCurrencyTotals().entrySet()) {
            String currency = entry.getKey();
            BigDecimal batchTotal = entry.getValue();
            
            BigDecimal currentExpected = expectedBalances.getOrDefault(currency, BigDecimal.ZERO);
            expectedBalances.put(currency, currentExpected.add(batchTotal));
        }
    }
    
    /**
     * Performs reconciliation for a specific currency
     */
    public ReconciliationResult performReconciliation(String currency) {
        try {
            LOGGER.info("Performing reconciliation for currency: " + currency);
            
            BigDecimal expectedBalance = expectedBalances.getOrDefault(currency, BigDecimal.ZERO);
            BigDecimal actualBalance = getActualBalance(currency);
            
            actualBalances.put(currency, actualBalance);
            
            ReconciliationResult result = new ReconciliationResult(currency, expectedBalance, actualBalance);
            
            // Check for specific discrepancies
            if (!result.isReconciled()) {
                analyzeDiscrepancies(result, currency);
            }
            
            reconciliationResults.put(currency, result);
            
            eventLog.logEvent("RECONCILIATION_COMPLETED: " + currency + 
                            " (Variance: " + String.format("%.4f%%", result.getVariancePercentage() * 100) + 
                            ", Reconciled: " + result.isReconciled() + ")");
            
            LOGGER.info("Reconciliation completed for " + currency + 
                       " (Reconciled: " + result.isReconciled() + ")");
            
            return result;
            
        } catch (Exception e) {
            LOGGER.severe("Error performing reconciliation for " + currency + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets actual balance from external sources
     */
    private BigDecimal getActualBalance(String currency) {
        try {
            // In a real implementation, this would query:
            // 1. Bank account balances
            // 2. Token supply on blockchain
            // 3. Reserve account balances
            // 4. Third-party custodian accounts
            
            // For now, simulate with small variance
            BigDecimal expected = expectedBalances.getOrDefault(currency, BigDecimal.ZERO);
            double variance = (secureRandom.nextGaussian() * 0.0005); // 0.05% standard deviation
            
            return expected.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(variance)));
            
        } catch (Exception e) {
            LOGGER.warning("Error getting actual balance for " + currency + ": " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Analyzes discrepancies in reconciliation
     */
    private void analyzeDiscrepancies(ReconciliationResult result, String currency) {
        try {
            // Analyze common sources of discrepancies
            
            // Check for pending settlements
            long pendingCount = pendingSettlements.values().stream()
                .filter(s -> s.getToCurrency().equals(currency))
                .count();
            
            if (pendingCount > 0) {
                result.addDiscrepancy("Pending settlements: " + pendingCount);
            }
            
            // Check for failed settlements
            long failedCount = completedSettlements.values().stream()
                .filter(s -> s.getToCurrency().equals(currency))
                .filter(s -> s.getStatus() == SettlementStatus.FAILED)
                .count();
            
            if (failedCount > 0) {
                result.addDiscrepancy("Failed settlements: " + failedCount);
            }
            
            // Check for timing differences
            if (result.getVariancePercentage() > 0.001) { // 0.1%
                result.addDiscrepancy("Large variance suggests timing or processing delays");
            }
            
        } catch (Exception e) {
            LOGGER.warning("Error analyzing discrepancies: " + e.getMessage());
        }
    }
    
    /**
     * Finalizes settlements (makes them immutable)
     */
    public boolean finalizeSettlement(String settlementId) {
        try {
            SettlementRecord settlement = completedSettlements.get(settlementId);
            if (settlement == null || settlement.getStatus() != SettlementStatus.SETTLED) {
                return false;
            }
            
            // Perform final validation
            if (!validateSettlement(settlement)) {
                LOGGER.warning("Final validation failed for settlement: " + settlementId);
                return false;
            }
            
            // Mark as finalized (immutable)
            settlement.setStatus(SettlementStatus.FINALIZED);
            
            // In a real implementation, this would:
            // 1. Write to immutable blockchain storage
            // 2. Generate final cryptographic proofs
            // 3. Notify all parties
            // 4. Update permanent records
            
            eventLog.logEvent("SETTLEMENT_FINALIZED: " + settlementId);
            LOGGER.info("Settlement finalized: " + settlementId);
            
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Error finalizing settlement: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Starts settlement services
     */
    private void startSettlementServices() {
        // Process batches periodically
        settlementExecutor.scheduleAtFixedRate(this::processPeriodicBatch, 
                                             SETTLEMENT_INTERVAL_MINUTES, 
                                             SETTLEMENT_INTERVAL_MINUTES, 
                                             TimeUnit.MINUTES);
        
        // Perform reconciliation periodically
        settlementExecutor.scheduleAtFixedRate(this::performPeriodicReconciliation,
                                             RECONCILIATION_WINDOW_HOURS,
                                             RECONCILIATION_WINDOW_HOURS,
                                             TimeUnit.HOURS);
        
        // Retry failed settlements
        settlementExecutor.scheduleAtFixedRate(this::retryFailedSettlements,
                                             10, 10, TimeUnit.MINUTES);
    }
    
    /**
     * Processes current batch if not empty
     */
    private void processPeriodicBatch() {
        try {
            synchronized (currentBatch) {
                if (!currentBatch.isEmpty()) {
                    processBatch(currentBatch);
                    currentBatch = new SettlementBatch();
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error in periodic batch processing: " + e.getMessage());
        }
    }
    
    /**
     * Performs periodic reconciliation
     */
    private void performPeriodicReconciliation() {
        try {
            Set<String> currencies = new HashSet<>();
            currencies.addAll(expectedBalances.keySet());
            currencies.addAll(actualBalances.keySet());
            
            for (String currency : currencies) {
                performReconciliation(currency);
            }
            
            LOGGER.info("Periodic reconciliation completed for " + currencies.size() + " currencies");
            
        } catch (Exception e) {
            LOGGER.warning("Error in periodic reconciliation: " + e.getMessage());
        }
    }
    
    /**
     * Retries failed settlements
     */
    private void retryFailedSettlements() {
        try {
            List<SettlementRecord> failedSettlements = pendingSettlements.values().stream()
                .filter(SettlementRecord::canRetry)
                .collect(Collectors.toList());
            
            for (SettlementRecord settlement : failedSettlements) {
                boolean success = processIndividualSettlement(settlement);
                if (success) {
                    LOGGER.info("Settlement retry successful: " + settlement.getSettlementId());
                }
            }
            
            if (!failedSettlements.isEmpty()) {
                LOGGER.info("Retried " + failedSettlements.size() + " failed settlements");
            }
            
        } catch (Exception e) {
            LOGGER.warning("Error retrying failed settlements: " + e.getMessage());
        }
    }
    
    /**
     * Utility methods
     */
    private String generateConfirmationNumber() {
        return "CONF" + System.currentTimeMillis() + String.format("%04d", secureRandom.nextInt(10000));
    }
    
    /**
     * Public API methods
     */
    public SettlementRecord getSettlement(String settlementId) {
        SettlementRecord settlement = completedSettlements.get(settlementId);
        if (settlement == null) {
            settlement = pendingSettlements.get(settlementId);
        }
        return settlement;
    }
    
    public List<SettlementRecord> getBankSettlements(String bankId) {
        List<String> settlementIds = bankSettlements.get(bankId);
        if (settlementIds == null) {
            return new ArrayList<>();
        }
        
        return settlementIds.stream()
            .map(this::getSettlement)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    public SettlementBatch getBatch(String batchId) {
        SettlementBatch batch = processedBatches.get(batchId);
        if (batch == null) {
            batch = activeBatches.get(batchId);
        }
        return batch;
    }
    
    public ReconciliationResult getReconciliationResult(String currency) {
        return reconciliationResults.get(currency);
    }
    
    public Map<String, BigDecimal> getAllExpectedBalances() {
        return new HashMap<>(expectedBalances);
    }
    
    public Map<String, BigDecimal> getAllActualBalances() {
        return new HashMap<>(actualBalances);
    }
    
    public Map<String, Object> getSystemStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalSettlements", totalSettlements.get());
        stats.put("successfulSettlements", successfulSettlements.get());
        stats.put("pendingSettlements", pendingSettlements.size());
        stats.put("completedSettlements", completedSettlements.size());
        stats.put("activeBatches", activeBatches.size());
        stats.put("processedBatches", processedBatches.size());
        
        // Success rate
        double successRate = totalSettlements.get() > 0 ? 
            (double) successfulSettlements.get() / totalSettlements.get() : 0.0;
        stats.put("successRate", successRate);
        
        // Settlement status distribution
        Map<SettlementStatus, Long> statusDistribution = new HashMap<>();
        pendingSettlements.values().forEach(s -> 
            statusDistribution.merge(s.getStatus(), 1L, Long::sum));
        completedSettlements.values().forEach(s -> 
            statusDistribution.merge(s.getStatus(), 1L, Long::sum));
        stats.put("settlementStatusDistribution", statusDistribution);
        
        // Reconciliation status
        long reconciledCurrencies = reconciliationResults.values().stream()
            .mapToLong(r -> r.isReconciled() ? 1 : 0)
            .sum();
        stats.put("reconciledCurrencies", reconciledCurrencies);
        stats.put("totalCurrencies", reconciliationResults.size());
        
        return stats;
    }
    
    /**
     * Shutdown method
     */
    public void shutdown() {
        try {
            // Process final batch
            synchronized (currentBatch) {
                if (!currentBatch.isEmpty()) {
                    processBatch(currentBatch);
                }
            }
            
            settlementExecutor.shutdown();
            if (!settlementExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                settlementExecutor.shutdownNow();
            }
            
            LOGGER.info("SettlementReconciliation shutdown completed");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Settlement shutdown interrupted");
        }
    }
}