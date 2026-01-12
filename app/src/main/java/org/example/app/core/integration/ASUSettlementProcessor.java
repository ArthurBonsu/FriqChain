package org.example.app.core.integration;

import org.example.app.core.blockchain.transaction.ASUSettlementTransaction;
import org.example.app.core.state.StateManager;
import org.example.app.core.types.Amount;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ASUSettlementProcessor handles African Stable Unit (ASU) settlement transactions.
 * 
 * Key features:
 * - Cross-border settlement without USD/SWIFT dependency
 * - Basket-backed ASU validation
 * - Lower fees for intra-African trade (0.1% vs 0.5%)
 * - Multi-currency reserve tracking
 * - Settlement finality guarantees
 */
public class ASUSettlementProcessor {
    private static final Logger logger = Logger.getLogger(ASUSettlementProcessor.class.getName());
    
    private static final BigInteger INTRA_AFRICAN_FEE_BPS = BigInteger.valueOf(10); // 0.1%
    private static final BigInteger EXTERNAL_FEE_BPS = BigInteger.valueOf(50); // 0.5%
    private static final BigInteger BASIS_POINTS = BigInteger.valueOf(10000);
    
    // State management
    private final StateManager stateManager;
    
    // Settlement tracking
    private final ConcurrentHashMap<String, SettlementRecord> settlements;
    
    // Reserve tracking per currency
    private final ConcurrentHashMap<String, BigInteger> currencyReserves;
    
    // ASU supply tracking
    private BigInteger totalASUSupply;
    private final Object supplyLock = new Object();
    
    // Metrics
    private final AtomicLong settlementsProcessed;
    private final AtomicLong settlementsFailed;
    private final AtomicLong intraAfricanSettlements;
    private final AtomicLong externalSettlements;
    
    public ASUSettlementProcessor(StateManager stateManager) {
        this.stateManager = stateManager;
        this.settlements = new ConcurrentHashMap<>();
        this.currencyReserves = new ConcurrentHashMap<>();
        this.totalASUSupply = BigInteger.ZERO;
        this.settlementsProcessed = new AtomicLong(0);
        this.settlementsFailed = new AtomicLong(0);
        this.intraAfricanSettlements = new AtomicLong(0);
        this.externalSettlements = new AtomicLong(0);
        
        logger.info("ASUSettlementProcessor initialized");
    }
    
    /**
     * Process an ASU settlement transaction
     */
    public boolean processSettlement(ASUSettlementTransaction transaction) {
        String settlementId = transaction.getSettlementId();
        
        try {
            logger.info("Processing ASU settlement: " + settlementId);
            
            // Check if already processed
            if (settlements.containsKey(settlementId)) {
                logger.warning("Settlement already processed: " + settlementId);
                return false;
            }
            
            // Validate basket composition
            if (!validateBasket(transaction)) {
                logger.warning("Invalid basket composition");
                settlementsFailed.incrementAndGet();
                return false;
            }
            
            // Validate reserves backing
            if (!validateReserveBacking(transaction)) {
                logger.warning("Insufficient reserves for settlement");
                settlementsFailed.incrementAndGet();
                return false;
            }
            
            // Execute settlement
            boolean executed = executeSettlement(transaction);
            
            if (executed) {
                // Record settlement
                recordSettlement(transaction);
                
                // Update metrics
                settlementsProcessed.incrementAndGet();
                if (transaction.isIntraAfrican()) {
                    intraAfricanSettlements.incrementAndGet();
                } else {
                    externalSettlements.incrementAndGet();
                }
                
                logger.info("ASU settlement completed: " + settlementId);
                return true;
            } else {
                settlementsFailed.incrementAndGet();
                return false;
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing ASU settlement", e);
            settlementsFailed.incrementAndGet();
            return false;
        }
    }
    
    /**
     * Validate ASU basket composition
     */
    private boolean validateBasket(ASUSettlementTransaction transaction) {
        Map<String, BigInteger> basket = transaction.getBasketComposition();
        
        if (basket == null || basket.isEmpty()) {
            return false;
        }
        
        // Validate weights sum to 100%
        BigInteger totalWeight = BigInteger.ZERO;
        for (BigInteger weight : basket.values()) {
            if (weight.compareTo(BigInteger.ZERO) < 0) {
                logger.warning("Negative weight in basket");
                return false;
            }
            totalWeight = totalWeight.add(weight);
        }
        
        // Allow 0.1% tolerance for rounding
        BigInteger expectedTotal = BigInteger.valueOf(1000000); // 100% in basis points
        BigInteger tolerance = BigInteger.valueOf(1000);
        BigInteger difference = totalWeight.subtract(expectedTotal).abs();
        
        if (difference.compareTo(tolerance) > 0) {
            logger.warning("Basket weights don't sum to 100%: " + totalWeight);
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate that reserves can back the settlement
     */
    private boolean validateReserveBacking(ASUSettlementTransaction transaction) {
        Map<String, BigInteger> basket = transaction.getBasketComposition();
        Amount asuAmount = transaction.getAmount();
        
        // Calculate required reserves for each currency in basket
        for (Map.Entry<String, BigInteger> entry : basket.entrySet()) {
            String currency = entry.getKey();
            BigInteger weight = entry.getValue();
            
            // Calculate required reserve: asuAmount * (weight / 1000000)
            BigInteger requiredReserve = asuAmount.getValue()
                .multiply(weight)
                .divide(BigInteger.valueOf(1000000));
            
            // Check if we have sufficient reserves
            BigInteger currentReserve = currencyReserves.getOrDefault(currency, BigInteger.ZERO);
            
            if (currentReserve.compareTo(requiredReserve) < 0) {
                logger.warning("Insufficient " + currency + " reserves: need " + 
                    requiredReserve + ", have " + currentReserve);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Execute the settlement
     */
    private boolean executeSettlement(ASUSettlementTransaction transaction) {
        try {
            // Transfer ASU from sender to recipient
            boolean transferred = transferASU(
                transaction.getSenderAddress(),
                transaction.getRecipientAddress(),
                transaction.getAmount()
            );
            
            if (!transferred) {
                logger.warning("ASU transfer failed");
                return false;
            }
            
            // Deduct fee
            BigInteger fee = calculateFee(transaction);
            boolean feeDeducted = deductFee(transaction.getSenderAddress(), fee);
            
            if (!feeDeducted) {
                logger.warning("Fee deduction failed");
                // Rollback transfer
                transferASU(
                    transaction.getRecipientAddress(),
                    transaction.getSenderAddress(),
                    transaction.getAmount()
                );
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error executing settlement", e);
            return false;
        }
    }
    
    /**
     * Transfer ASU between addresses
     */
    private boolean transferASU(org.example.app.core.types.Address from, 
                                org.example.app.core.types.Address to,
                                Amount amount) {
        try {
            String fromAddress = from.toString();
            String toAddress = to.toString();
            
            // Update state for sender
            stateManager.updateBalance(fromAddress, amount.getValue().negate());
            
            // Update state for recipient
            stateManager.updateBalance(toAddress, amount.getValue());
            
            logger.fine("Transferred " + amount + " ASU from " + fromAddress + " to " + toAddress);
            return true;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error transferring ASU", e);
            return false;
        }
    }
    
    /**
     * Calculate settlement fee
     */
    private BigInteger calculateFee(ASUSettlementTransaction transaction) {
        Amount asuAmount = transaction.getAmount();
        BigInteger feeBps = transaction.isIntraAfrican() ? 
            INTRA_AFRICAN_FEE_BPS : EXTERNAL_FEE_BPS;
        
        BigInteger fee = asuAmount.getValue()
            .multiply(feeBps)
            .divide(BASIS_POINTS);
        
        logger.fine("Settlement fee: " + fee + " (" + 
            (transaction.isIntraAfrican() ? "0.1%" : "0.5%") + ")");
        
        return fee;
    }
    
    /**
     * Deduct fee from sender
     */
    private boolean deductFee(org.example.app.core.types.Address from, BigInteger fee) {
        try {
            String fromAddress = from.toString();
            stateManager.updateBalance(fromAddress, fee.negate());
            
            logger.fine("Deducted fee: " + fee + " from " + fromAddress);
            return true;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error deducting fee", e);
            return false;
        }
    }
    
    /**
     * Record settlement
     */
    private void recordSettlement(ASUSettlementTransaction transaction) {
        SettlementRecord record = new SettlementRecord(
            transaction.getSettlementId(),
            transaction.getSenderCountry(),
            transaction.getRecipientCountry(),
            transaction.getAmount(),
            transaction.getBasketComposition(),
            transaction.isIntraAfrican(),
            System.currentTimeMillis()
        );
        
        settlements.put(transaction.getSettlementId(), record);
        
        logger.fine("Recorded settlement: " + transaction.getSettlementId());
    }
    
    /**
     * Add reserves for a currency
     */
    public void addReserves(String currency, BigInteger amount) {
        currencyReserves.merge(currency, amount, BigInteger::add);
        logger.info("Added " + amount + " to " + currency + " reserves");
    }
    
    /**
     * Get reserve balance for currency
     */
    public BigInteger getReserves(String currency) {
        return currencyReserves.getOrDefault(currency, BigInteger.ZERO);
    }
    
    /**
     * Get total ASU supply
     */
    public BigInteger getTotalASUSupply() {
        synchronized (supplyLock) {
            return totalASUSupply;
        }
    }
    
    /**
     * Mint new ASU (when reserves are added)
     */
    public void mintASU(BigInteger amount) {
        synchronized (supplyLock) {
            totalASUSupply = totalASUSupply.add(amount);
            logger.info("Minted " + amount + " ASU, new supply: " + totalASUSupply);
        }
    }
    
    /**
     * Burn ASU (when reserves are withdrawn)
     */
    public void burnASU(BigInteger amount) {
        synchronized (supplyLock) {
            totalASUSupply = totalASUSupply.subtract(amount);
            logger.info("Burned " + amount + " ASU, new supply: " + totalASUSupply);
        }
    }
    
    /**
     * Get settlement record
     */
    public SettlementRecord getSettlement(String settlementId) {
        return settlements.get(settlementId);
    }
    
    /**
     * Get recent settlements
     */
    public List<SettlementRecord> getRecentSettlements(int limit) {
        List<SettlementRecord> recent = new ArrayList<>();
        
        for (SettlementRecord record : settlements.values()) {
            recent.add(record);
            if (recent.size() >= limit) {
                break;
            }
        }
        
        return recent;
    }
    
    /**
     * Get processing statistics
     */
    public SettlementStats getStats() {
        return new SettlementStats(
            settlementsProcessed.get(),
            settlementsFailed.get(),
            intraAfricanSettlements.get(),
            externalSettlements.get(),
            totalASUSupply,
            currencyReserves.size()
        );
    }
    
    /**
     * Settlement record
     */
    public static class SettlementRecord {
        private final String settlementId;
        private final String senderCountry;
        private final String recipientCountry;
        private final Amount asuAmount;
        private final Map<String, BigInteger> basketComposition;
        private final boolean intraAfrican;
        private final long timestamp;
        
        SettlementRecord(String settlementId, String senderCountry, String recipientCountry,
                        Amount asuAmount, Map<String, BigInteger> basketComposition,
                        boolean intraAfrican, long timestamp) {
            this.settlementId = settlementId;
            this.senderCountry = senderCountry;
            this.recipientCountry = recipientCountry;
            this.asuAmount = asuAmount;
            this.basketComposition = new HashMap<>(basketComposition);
            this.intraAfrican = intraAfrican;
            this.timestamp = timestamp;
        }
        
        public String getSettlementId() { return settlementId; }
        public String getSenderCountry() { return senderCountry; }
        public String getRecipientCountry() { return recipientCountry; }
        public Amount getAsuAmount() { return asuAmount; }
        public Map<String, BigInteger> getBasketComposition() { return new HashMap<>(basketComposition); }
        public boolean isIntraAfrican() { return intraAfrican; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Settlement statistics
     */
    public static class SettlementStats {
        private final long settlementsProcessed;
        private final long settlementsFailed;
        private final long intraAfricanSettlements;
        private final long externalSettlements;
        private final BigInteger totalASUSupply;
        private final int currenciesTracked;
        
        SettlementStats(long settlementsProcessed, long settlementsFailed,
                       long intraAfricanSettlements, long externalSettlements,
                       BigInteger totalASUSupply, int currenciesTracked) {
            this.settlementsProcessed = settlementsProcessed;
            this.settlementsFailed = settlementsFailed;
            this.intraAfricanSettlements = intraAfricanSettlements;
            this.externalSettlements = externalSettlements;
            this.totalASUSupply = totalASUSupply;
            this.currenciesTracked = currenciesTracked;
        }
        
        public long getSettlementsProcessed() { return settlementsProcessed; }
        public long getSettlementsFailed() { return settlementsFailed; }
        public long getIntraAfricanSettlements() { return intraAfricanSettlements; }
        public long getExternalSettlements() { return externalSettlements; }
        public BigInteger getTotalASUSupply() { return totalASUSupply; }
        public int getCurrenciesTracked() { return currenciesTracked; }
        
        public double getSuccessRate() {
            long total = settlementsProcessed + settlementsFailed;
            return total > 0 ? (double) settlementsProcessed / total : 0.0;
        }
        
        public double getIntraAfricanPercentage() {
            return settlementsProcessed > 0 ? 
                (double) intraAfricanSettlements / settlementsProcessed : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "SettlementStats{processed=%d, failed=%d, intraAfrican=%d (%.2f%%), " +
                "external=%d, asuSupply=%s, currencies=%d}",
                settlementsProcessed, settlementsFailed,
                intraAfricanSettlements, getIntraAfricanPercentage() * 100,
                externalSettlements, totalASUSupply, currenciesTracked
            );
        }
    }
}
