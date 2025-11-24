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
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.time.Instant;

/**
 * AgentNodeWallet - Lightweight wallet for retail agents in rural/offline areas
 * 
 * Features:
 * - Cash-in/cash-out services for citizens without bank accounts
 * - USSD/SMS transaction signing
 * - Offline transaction batching and sync
 * - Agent commission system
 * - Float management (agent liquidity)
 * - Community trust network
 * - Low-bandwidth operation
 * - Agent verification and reputation
 */
public class AgentNodeWallet extends Wallet {
    private static final Logger LOGGER = Logger.getLogger(AgentNodeWallet.class.getName());
    
    // Agent identification
    private final String agentId;
    private final String agentName;
    private final String phoneNumber;
    private final String location;
    private final String countryCode;
    
    // Float management (agent's working capital)
    private final Map<String, BigInteger> floatBalances;
    private final BigInteger minimumFloat;
    private final BigInteger maximumFloat;
    
    // Transaction tracking
    private final List<AgentTransaction> transactionHistory;
    private final Map<String, OfflineBatch> offlineBatches;
    
    // Commission and fees
    private final BigDecimal commissionRate; // e.g., 0.02 = 2%
    private BigInteger totalCommissionsEarned;
    private BigInteger unclaimedCommissions;
    
    // Agent reputation
    private int trustScore; // 0-100
    private int successfulTransactions;
    private int failedTransactions;
    private int disputesResolved;
    private int disputesPending;
    
    // Service limits
    private final BigInteger dailyTransactionLimit;
    private BigInteger dailyTransactionUsed;
    private long lastResetTimestamp;
    
    // Dependencies
    private final PKIManager pkiManager;
    private final BlockchainState blockchainState;
    private final Blockchain blockchain;
    
    /**
     * AgentTransaction - Record of agent-facilitated transaction
     */
    public static class AgentTransaction {
        private final String transactionId;
        private final TransactionType type;
        private final String citizenPhone;
        private final String currency;
        private final BigInteger amount;
        private final BigInteger commission;
        private final Instant timestamp;
        private TransactionStatus status;
        private String blockchainTxHash;
        
        public enum TransactionType {
            CASH_IN, CASH_OUT, P2P_FACILITATED, BILL_PAYMENT, AIRTIME
        }
        
        public enum TransactionStatus {
            PENDING, COMPLETED, FAILED, DISPUTED
        }
        
        public AgentTransaction(TransactionType type, String citizenPhone, 
                               String currency, BigInteger amount, BigInteger commission) {
            this.transactionId = UUID.randomUUID().toString();
            this.type = type;
            this.citizenPhone = citizenPhone;
            this.currency = currency;
            this.amount = amount;
            this.commission = commission;
            this.timestamp = Instant.now();
            this.status = TransactionStatus.PENDING;
        }
        
        public String getTransactionId() { return transactionId; }
        public TransactionType getType() { return type; }
        public String getCitizenPhone() { return citizenPhone; }
        public String getCurrency() { return currency; }
        public BigInteger getAmount() { return amount; }
        public BigInteger getCommission() { return commission; }
        public Instant getTimestamp() { return timestamp; }
        public TransactionStatus getStatus() { return status; }
        public String getBlockchainTxHash() { return blockchainTxHash; }
        
        public void setStatus(TransactionStatus status) { this.status = status; }
        public void setBlockchainTxHash(String txHash) { this.blockchainTxHash = txHash; }
    }
    
    /**
     * OfflineBatch - Batched transactions for sync
     */
    public static class OfflineBatch {
        private final String batchId;
        private final List<AgentTransaction> transactions;
        private final Instant createdAt;
        private boolean synced;
        
        public OfflineBatch() {
            this.batchId = UUID.randomUUID().toString();
            this.transactions = Collections.synchronizedList(new ArrayList<>());
            this.createdAt = Instant.now();
            this.synced = false;
        }
        
        public String getBatchId() { return batchId; }
        public List<AgentTransaction> getTransactions() { return new ArrayList<>(transactions); }
        public Instant getCreatedAt() { return createdAt; }
        public boolean isSynced() { return synced; }
        
        public void addTransaction(AgentTransaction tx) {
            transactions.add(tx);
        }
        
        public void markSynced() {
            this.synced = true;
        }
        
        public int getTransactionCount() {
            return transactions.size();
        }
    }
    
    /**
     * Constructor for AgentNodeWallet
     */
    public AgentNodeWallet(String agentId, String agentName, String phoneNumber,
                          String location, String countryCode,
                          BigInteger minimumFloat, BigInteger maximumFloat,
                          BlockchainState blockchainState, Blockchain blockchain,
                          PKIManager pkiManager) throws NoSuchAlgorithmException {
        super();
        
        this.agentId = agentId;
        this.agentName = agentName;
        this.phoneNumber = phoneNumber;
        this.location = location;
        this.countryCode = countryCode.toUpperCase();
        
        this.floatBalances = new ConcurrentHashMap<>();
        this.minimumFloat = minimumFloat;
        this.maximumFloat = maximumFloat;
        
        this.transactionHistory = Collections.synchronizedList(new ArrayList<>());
        this.offlineBatches = new ConcurrentHashMap<>();
        
        this.commissionRate = new BigDecimal("0.02"); // 2% default
        this.totalCommissionsEarned = BigInteger.ZERO;
        this.unclaimedCommissions = BigInteger.ZERO;
        
        this.trustScore = 70; // Start at 70/100
        this.successfulTransactions = 0;
        this.failedTransactions = 0;
        this.disputesResolved = 0;
        this.disputesPending = 0;
        
        this.dailyTransactionLimit = new BigInteger("10000000000000000000000"); // 10,000 tokens
        this.dailyTransactionUsed = BigInteger.ZERO;
        this.lastResetTimestamp = System.currentTimeMillis();
        
        this.blockchainState = blockchainState;
        this.blockchain = blockchain;
        this.pkiManager = pkiManager;
        
        LOGGER.info(String.format("AgentNodeWallet created for %s in %s", 
            agentName, location));
    }
    
    /**
     * Citizen deposits cash, receives tokens (cash-in)
     */
    public AgentTransaction cashIn(String citizenPhone, String currency, BigInteger amount) {
        if (!checkDailyLimit(amount)) {
            LOGGER.warning("Daily transaction limit exceeded");
            return null;
        }
        
        // Check float capacity
        BigInteger currentFloat = floatBalances.getOrDefault(currency, BigInteger.ZERO);
        BigInteger newFloat = currentFloat.add(amount);
        
        if (newFloat.compareTo(maximumFloat) > 0) {
            LOGGER.warning("Cash-in would exceed maximum float");
            return null;
        }
        
        // Calculate commission
        BigInteger commission = new BigDecimal(amount)
            .multiply(commissionRate)
            .toBigInteger();
        
        // Create transaction
        AgentTransaction agentTx = new AgentTransaction(
            AgentTransaction.TransactionType.CASH_IN,
            citizenPhone, currency, amount, commission
        );
        
        // Update float
        floatBalances.put(currency, newFloat);
        
        // Track commission
        unclaimedCommissions = unclaimedCommissions.add(commission);
        totalCommissionsEarned = totalCommissionsEarned.add(commission);
        
        // Update daily limit
        dailyTransactionUsed = dailyTransactionUsed.add(amount);
        
        // Add to history
        transactionHistory.add(agentTx);
        
        LOGGER.info(String.format("Cash-in: %s %s from %s (commission: %s)", 
            amount.toString(), currency, citizenPhone, commission.toString()));
        
        return agentTx;
    }
    
    /**
     * Citizen withdraws tokens, receives cash (cash-out)
     */
    public AgentTransaction cashOut(String citizenPhone, String currency, BigInteger amount) {
        if (!checkDailyLimit(amount)) {
            LOGGER.warning("Daily transaction limit exceeded");
            return null;
        }
        
        // Check float availability
        BigInteger currentFloat = floatBalances.getOrDefault(currency, BigInteger.ZERO);
        if (currentFloat.compareTo(amount) < 0) {
            LOGGER.warning("Insufficient float for cash-out");
            return null;
        }
        
        // Calculate commission
        BigInteger commission = new BigDecimal(amount)
            .multiply(commissionRate)
            .toBigInteger();
        
        // Create transaction
        AgentTransaction agentTx = new AgentTransaction(
            AgentTransaction.TransactionType.CASH_OUT,
            citizenPhone, currency, amount, commission
        );
        
        // Update float
        floatBalances.put(currency, currentFloat.subtract(amount));
        
        // Track commission
        unclaimedCommissions = unclaimedCommissions.add(commission);
        totalCommissionsEarned = totalCommissionsEarned.add(commission);
        
        // Update daily limit
        dailyTransactionUsed = dailyTransactionUsed.add(amount);
        
        // Add to history
        transactionHistory.add(agentTx);
        
        LOGGER.info(String.format("Cash-out: %s %s to %s (commission: %s)", 
            amount.toString(), currency, citizenPhone, commission.toString()));
        
        return agentTx;
    }
    
    /**
     * Creates offline batch for later sync
     */
    public OfflineBatch createOfflineBatch() {
        OfflineBatch batch = new OfflineBatch();
        offlineBatches.put(batch.getBatchId(), batch);
        
        LOGGER.info("Offline batch created: " + batch.getBatchId());
        return batch;
    }
    
    /**
     * Adds transaction to offline batch
     */
    public boolean addToOfflineBatch(String batchId, AgentTransaction transaction) {
        OfflineBatch batch = offlineBatches.get(batchId);
        if (batch == null || batch.isSynced()) {
            LOGGER.warning("Invalid or synced batch: " + batchId);
            return false;
        }
        
        batch.addTransaction(transaction);
        
        LOGGER.info(String.format("Transaction added to offline batch %s (count: %d)", 
            batchId, batch.getTransactionCount()));
        
        return true;
    }
    
    /**
     * Syncs offline batches when connectivity restored
     */
    public List<Transaction> syncOfflineBatches() {
        List<Transaction> syncedTransactions = new ArrayList<>();
        
        for (OfflineBatch batch : offlineBatches.values()) {
            if (batch.isSynced()) continue;
            
            for (AgentTransaction agentTx : batch.getTransactions()) {
                // Create blockchain transaction
                Transaction blockchainTx = createBlockchainTransaction(agentTx);
                
                if (blockchainTx != null) {
                    agentTx.setBlockchainTxHash(blockchainTx.getHash().toString());
                    agentTx.setStatus(AgentTransaction.TransactionStatus.COMPLETED);
                    syncedTransactions.add(blockchainTx);
                    
                    successfulTransactions++;
                    trustScore = Math.min(100, trustScore + 1);
                } else {
                    agentTx.setStatus(AgentTransaction.TransactionStatus.FAILED);
                    failedTransactions++;
                    trustScore = Math.max(0, trustScore - 2);
                }
            }
            
            batch.markSynced();
            LOGGER.info(String.format("Batch synced: %s (%d transactions)", 
                batch.getBatchId(), batch.getTransactionCount()));
        }
        
        return syncedTransactions;
    }
    
    /**
     * Tops up agent float
     */
    public boolean topUpFloat(String currency, BigInteger amount) {
        BigInteger currentFloat = floatBalances.getOrDefault(currency, BigInteger.ZERO);
        BigInteger newFloat = currentFloat.add(amount);
        
        if (newFloat.compareTo(maximumFloat) > 0) {
            LOGGER.warning("Top-up would exceed maximum float");
            return false;
        }
        
        floatBalances.put(currency, newFloat);
        
        LOGGER.info(String.format("Float topped up: %s %s (new total: %s)", 
            amount.toString(), currency, newFloat.toString()));
        
        return true;
    }
    
    /**
     * Withdraws from agent float
     */
    public boolean withdrawFloat(String currency, BigInteger amount) {
        BigInteger currentFloat = floatBalances.getOrDefault(currency, BigInteger.ZERO);
        
        if (currentFloat.compareTo(amount) < 0) {
            LOGGER.warning("Insufficient float for withdrawal");
            return false;
        }
        
        BigInteger newFloat = currentFloat.subtract(amount);
        
        if (newFloat.compareTo(minimumFloat) < 0) {
            LOGGER.warning("Withdrawal would drop below minimum float");
            return false;
        }
        
        floatBalances.put(currency, newFloat);
        
        LOGGER.info(String.format("Float withdrawn: %s %s (remaining: %s)", 
            amount.toString(), currency, newFloat.toString()));
        
        return true;
    }
    
    /**
     * Claims accumulated commissions
     */
    public BigInteger claimCommissions() {
        BigInteger claimed = unclaimedCommissions;
        unclaimedCommissions = BigInteger.ZERO;
        
        LOGGER.info(String.format("Commissions claimed: %s", claimed.toString()));
        
        return claimed;
    }
    
    /**
     * Creates blockchain transaction from agent transaction
     */
    private Transaction createBlockchainTransaction(AgentTransaction agentTx) {
        try {
            // In production, would create proper blockchain transaction
            // For now, simulate
            return null;
        } catch (Exception e) {
            LOGGER.severe("Error creating blockchain transaction: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Checks daily transaction limit
     */
    private boolean checkDailyLimit(BigInteger amount) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastResetTimestamp > (24 * 60 * 60 * 1000)) {
            dailyTransactionUsed = BigInteger.ZERO;
            lastResetTimestamp = currentTime;
        }
        
        BigInteger potentialUsage = dailyTransactionUsed.add(amount);
        return potentialUsage.compareTo(dailyTransactionLimit) <= 0;
    }
    
    // Getters and utility methods
    
    public String getAgentId() { return agentId; }
    public String getAgentName() { return agentName; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getLocation() { return location; }
    public String getCountryCode() { return countryCode; }
    
    public BigInteger getFloatBalance(String currency) {
        return floatBalances.getOrDefault(currency, BigInteger.ZERO);
    }
    
    public Map<String, BigInteger> getAllFloatBalances() {
        return new HashMap<>(floatBalances);
    }
    
    public BigInteger getMinimumFloat() { return minimumFloat; }
    public BigInteger getMaximumFloat() { return maximumFloat; }
    
    public int getTrustScore() { return trustScore; }
    public int getSuccessfulTransactions() { return successfulTransactions; }
    public int getFailedTransactions() { return failedTransactions; }
    
    public BigInteger getTotalCommissionsEarned() { return totalCommissionsEarned; }
    public BigInteger getUnclaimedCommissions() { return unclaimedCommissions; }
    
    public List<AgentTransaction> getRecentTransactions(int limit) {
        int size = transactionHistory.size();
        int fromIndex = Math.max(0, size - limit);
        return new ArrayList<>(transactionHistory.subList(fromIndex, size));
    }
    
    public List<OfflineBatch> getUnsyncedBatches() {
        return offlineBatches.values().stream()
            .filter(batch -> !batch.isSynced())
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    public BigDecimal getSuccessRate() {
        int total = successfulTransactions + failedTransactions;
        if (total == 0) return BigDecimal.ONE;
        
        return new BigDecimal(successfulTransactions)
            .divide(new BigDecimal(total), 4, java.math.RoundingMode.HALF_UP);
    }
    
    public boolean isFloatHealthy(String currency) {
        BigInteger balance = floatBalances.getOrDefault(currency, BigInteger.ZERO);
        return balance.compareTo(minimumFloat) >= 0 && 
               balance.compareTo(maximumFloat) <= 0;
    }
    
    @Override
    public String toString() {
        return String.format("AgentNodeWallet{id='%s', name='%s', location='%s', " +
            "trust=%d/100, transactions=%d, commissions=%s}",
            agentId, agentName, location, trustScore, 
            successfulTransactions, totalCommissionsEarned.toString());
    }
}