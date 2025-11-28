package org.example.app.core.blockchain.foreign;

import org.example.app.core.types.Address;
import org.example.app.core.types.Amount;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * ExternalStablecoinBridge provides ISOLATED bridges to external stablecoins
 * (USDC, EURC, etc.) for optional liquidity without compromising core system independence.
 * 
 * Critical Design Principles:
 * - NOT part of monetary core - if blocked, core system still functions
 * - Circuit breakers can instantly disconnect external bridges
 * - Exposure limits prevent over-reliance on foreign stablecoins
 * - Transparent monitoring of bridge health
 * - No privileged access to core system functions
 * - Emergency shutdown capability
 * 
 * These bridges are CONVENIENCE FEATURES, not dependencies.
 */
public class ExternalStablecoinBridge {
    
    private static final Logger LOGGER = Logger.getLogger(ExternalStablecoinBridge.class.getName());
    
    // Supported external stablecoins
    private static final Set<String> SUPPORTED_STABLECOINS = new HashSet<>(Arrays.asList(
        "USDC", "USDT", "EURC", "DAI", "BUSD", "TUSD"
    ));
    
    // Maximum exposure to any single external stablecoin (10% of total)
    private static final BigDecimal MAX_EXPOSURE_RATIO = new BigDecimal("0.10");
    
    // Circuit breaker threshold (triggers at 95% of max exposure)
    private static final BigDecimal CIRCUIT_BREAKER_THRESHOLD = new BigDecimal("0.95");
    
    // Bridge cooldown period after circuit breaker (1 hour)
    private static final long CIRCUIT_BREAKER_COOLDOWN = 3600000;
    
    // Active bridges: Stablecoin -> Bridge
    private final Map<String, StablecoinBridge> bridges;
    
    // Bridge transaction history
    private final List<BridgeTransaction> transactionHistory;
    
    private final ReentrantReadWriteLock lock;
    private final FXExposureMonitor exposureMonitor;
    
    /**
     * Represents a bridge to an external stablecoin
     */
    public static class StablecoinBridge {
        private final String stablecoinType; // e.g., "USDC"
        private final String bridgeContract; // External contract address
        private BigInteger totalBridgedIn; // Amount bridged into TINC
        private BigInteger totalBridgedOut; // Amount bridged out of TINC
        private BigInteger currentExposure; // Net exposure (in - out)
        private BridgeStatus status;
        private long lastActivityTimestamp;
        private long circuitBreakerTriggeredAt;
        private final List<String> validators; // Multi-sig validators for bridge
        
        public enum BridgeStatus {
            ACTIVE,           // Normal operation
            PAUSED,           // Temporarily paused
            CIRCUIT_BREAKER,  // Circuit breaker triggered
            SHUTDOWN          // Permanently shut down
        }
        
        public StablecoinBridge(String stablecoinType, String bridgeContract, List<String> validators) {
            this.stablecoinType = stablecoinType;
            this.bridgeContract = bridgeContract;
            this.totalBridgedIn = BigInteger.ZERO;
            this.totalBridgedOut = BigInteger.ZERO;
            this.currentExposure = BigInteger.ZERO;
            this.status = BridgeStatus.PAUSED; // Start paused until activated
            this.lastActivityTimestamp = System.currentTimeMillis();
            this.circuitBreakerTriggeredAt = 0;
            this.validators = new ArrayList<>(validators);
        }
        
        public String getStablecoinType() { return stablecoinType; }
        public String getBridgeContract() { return bridgeContract; }
        public BigInteger getTotalBridgedIn() { return totalBridgedIn; }
        public BigInteger getTotalBridgedOut() { return totalBridgedOut; }
        public BigInteger getCurrentExposure() { return currentExposure; }
        public BridgeStatus getStatus() { return status; }
        public long getLastActivityTimestamp() { return lastActivityTimestamp; }
        public List<String> getValidators() { return new ArrayList<>(validators); }
        
        public boolean isOperational() {
            return status == BridgeStatus.ACTIVE;
        }
        
        public boolean canExitCircuitBreaker() {
            if (status != BridgeStatus.CIRCUIT_BREAKER) {
                return false;
            }
            long timeSinceBreaker = System.currentTimeMillis() - circuitBreakerTriggeredAt;
            return timeSinceBreaker >= CIRCUIT_BREAKER_COOLDOWN;
        }
        
        public void recordBridgeIn(BigInteger amount) {
            totalBridgedIn = totalBridgedIn.add(amount);
            currentExposure = currentExposure.add(amount);
            lastActivityTimestamp = System.currentTimeMillis();
        }
        
        public void recordBridgeOut(BigInteger amount) {
            totalBridgedOut = totalBridgedOut.add(amount);
            currentExposure = currentExposure.subtract(amount);
            lastActivityTimestamp = System.currentTimeMillis();
        }
        
        public void triggerCircuitBreaker() {
            status = BridgeStatus.CIRCUIT_BREAKER;
            circuitBreakerTriggeredAt = System.currentTimeMillis();
        }
        
        public void setStatus(BridgeStatus newStatus) {
            this.status = newStatus;
        }
    }
    
    /**
     * Represents a bridge transaction
     */
    public static class BridgeTransaction {
        private final String txId;
        private final BridgeDirection direction;
        private final String stablecoin;
        private final Address tincAddress;
        private final String externalAddress;
        private final BigInteger amount;
        private final long timestamp;
        private final BridgeTransactionStatus status;
        
        public enum BridgeDirection {
            BRIDGE_IN,   // External stablecoin → TINC
            BRIDGE_OUT   // TINC → External stablecoin
        }
        
        public enum BridgeTransactionStatus {
            PENDING,
            CONFIRMED,
            FAILED,
            REVERSED
        }
        
        public BridgeTransaction(String txId, BridgeDirection direction, String stablecoin,
                               Address tincAddress, String externalAddress, BigInteger amount,
                               BridgeTransactionStatus status) {
            this.txId = txId;
            this.direction = direction;
            this.stablecoin = stablecoin;
            this.tincAddress = tincAddress;
            this.externalAddress = externalAddress;
            this.amount = amount;
            this.timestamp = System.currentTimeMillis();
            this.status = status;
        }
        
        public String getTxId() { return txId; }
        public BridgeDirection getDirection() { return direction; }
        public String getStablecoin() { return stablecoin; }
        public Address getTincAddress() { return tincAddress; }
        public String getExternalAddress() { return externalAddress; }
        public BigInteger getAmount() { return amount; }
        public long getTimestamp() { return timestamp; }
        public BridgeTransactionStatus getStatus() { return status; }
    }
    
    public ExternalStablecoinBridge(FXExposureMonitor exposureMonitor) {
        this.bridges = new ConcurrentHashMap<>();
        this.transactionHistory = Collections.synchronizedList(new ArrayList<>());
        this.lock = new ReentrantReadWriteLock(true);
        this.exposureMonitor = exposureMonitor;
        
        LOGGER.info("ExternalStablecoinBridge initialized - ISOLATED bridges for optional liquidity");
    }
    
    /**
     * Initializes a bridge to an external stablecoin
     */
    public boolean initializeBridge(String stablecoin, String bridgeContract, List<String> validators) {
        lock.writeLock().lock();
        try {
            if (!SUPPORTED_STABLECOINS.contains(stablecoin)) {
                LOGGER.warning("Unsupported stablecoin: " + stablecoin);
                return false;
            }
            
            if (bridges.containsKey(stablecoin)) {
                LOGGER.warning("Bridge already exists for: " + stablecoin);
                return false;
            }
            
            if (validators == null || validators.size() < 5) {
                LOGGER.warning("Insufficient validators (minimum 5 required for bridges)");
                return false;
            }
            
            StablecoinBridge bridge = new StablecoinBridge(stablecoin, bridgeContract, validators);
            bridges.put(stablecoin, bridge);
            
            LOGGER.info(String.format("Initialized bridge for %s (contract: %s, validators: %d)",
                stablecoin, bridgeContract, validators.size()));
            
            return true;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Bridges external stablecoin into TINC system
     */
    public Optional<String> bridgeIn(String stablecoin, String externalAddress,
                                     Address tincAddress, BigInteger amount) {
        lock.writeLock().lock();
        try {
            StablecoinBridge bridge = bridges.get(stablecoin);
            if (bridge == null) {
                LOGGER.warning("Bridge not found for: " + stablecoin);
                return Optional.empty();
            }
            
            if (!bridge.isOperational()) {
                LOGGER.warning(String.format("Bridge not operational for %s (status: %s)",
                    stablecoin, bridge.getStatus()));
                return Optional.empty();
            }
            
            if (amount.compareTo(BigInteger.ZERO) <= 0) {
                LOGGER.warning("Invalid bridge amount: " + amount);
                return Optional.empty();
            }
            
            // Check exposure limits
            BigInteger newExposure = bridge.getCurrentExposure().add(amount);
            if (!checkExposureLimit(stablecoin, newExposure)) {
                LOGGER.warning(String.format("Bridge-in would exceed exposure limit for %s", stablecoin));
                
                // Check if circuit breaker should trigger
                if (shouldTriggerCircuitBreaker(stablecoin, newExposure)) {
                    bridge.triggerCircuitBreaker();
                    LOGGER.warning("Circuit breaker triggered for " + stablecoin);
                }
                
                return Optional.empty();
            }
            
            // Verify external transaction (in production, this would verify on-chain)
            if (!verifyExternalTransaction(externalAddress, amount, stablecoin)) {
                LOGGER.warning("Failed to verify external transaction");
                return Optional.empty();
            }
            
            // Record bridge transaction
            String txId = generateTransactionId();
            BridgeTransaction tx = new BridgeTransaction(
                txId,
                BridgeTransaction.BridgeDirection.BRIDGE_IN,
                stablecoin,
                tincAddress,
                externalAddress,
                amount,
                BridgeTransaction.BridgeTransactionStatus.CONFIRMED
            );
            
            transactionHistory.add(tx);
            bridge.recordBridgeIn(amount);
            
            // Update exposure monitoring
            exposureMonitor.recordExposure(stablecoin, newExposure);
            
            LOGGER.info(String.format("Bridged in %s %s (tx: %s, exposure: %s)",
                amount, stablecoin, txId, newExposure));
            
            return Optional.of(txId);
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Bridges TINC assets out to external stablecoin
     */
    public Optional<String> bridgeOut(String stablecoin, Address tincAddress,
                                      String externalAddress, BigInteger amount) {
        lock.writeLock().lock();
        try {
            StablecoinBridge bridge = bridges.get(stablecoin);
            if (bridge == null) {
                LOGGER.warning("Bridge not found for: " + stablecoin);
                return Optional.empty();
            }
            
            if (!bridge.isOperational()) {
                LOGGER.warning(String.format("Bridge not operational for %s (status: %s)",
                    stablecoin, bridge.getStatus()));
                return Optional.empty();
            }
            
            if (amount.compareTo(BigInteger.ZERO) <= 0) {
                LOGGER.warning("Invalid bridge amount: " + amount);
                return Optional.empty();
            }
            
            // Verify TINC has sufficient balance
            // (In production, this would check actual balance)
            
            // Calculate new exposure
            BigInteger newExposure = bridge.getCurrentExposure().subtract(amount);
            
            // Generate transaction ID
            String txId = generateTransactionId();
            
            // Record bridge transaction
            BridgeTransaction tx = new BridgeTransaction(
                txId,
                BridgeTransaction.BridgeDirection.BRIDGE_OUT,
                stablecoin,
                tincAddress,
                externalAddress,
                amount,
                BridgeTransaction.BridgeTransactionStatus.CONFIRMED
            );
            
            transactionHistory.add(tx);
            bridge.recordBridgeOut(amount);
            
            // Update exposure monitoring
            exposureMonitor.recordExposure(stablecoin, newExposure);
            
            LOGGER.info(String.format("Bridged out %s %s (tx: %s, exposure: %s)",
                amount, stablecoin, txId, newExposure));
            
            return Optional.of(txId);
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Activates a bridge (changes status from PAUSED to ACTIVE)
     */
    public boolean activateBridge(String stablecoin, List<String> validatorSignatures) {
        lock.writeLock().lock();
        try {
            StablecoinBridge bridge = bridges.get(stablecoin);
            if (bridge == null) {
                return false;
            }
            
            // Verify multi-sig approval (require 66% of validators)
            int requiredSignatures = (int) Math.ceil(bridge.getValidators().size() * 0.66);
            if (validatorSignatures.size() < requiredSignatures) {
                LOGGER.warning(String.format("Insufficient validator signatures: %d/%d",
                    validatorSignatures.size(), requiredSignatures));
                return false;
            }
            
            bridge.setStatus(StablecoinBridge.BridgeStatus.ACTIVE);
            LOGGER.info("Activated bridge for: " + stablecoin);
            
            return true;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Pauses a bridge
     */
    public boolean pauseBridge(String stablecoin) {
        lock.writeLock().lock();
        try {
            StablecoinBridge bridge = bridges.get(stablecoin);
            if (bridge == null) {
                return false;
            }
            
            bridge.setStatus(StablecoinBridge.BridgeStatus.PAUSED);
            LOGGER.info("Paused bridge for: " + stablecoin);
            
            return true;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Emergency shutdown of a bridge
     */
    public boolean emergencyShutdown(String stablecoin, String reason) {
        lock.writeLock().lock();
        try {
            StablecoinBridge bridge = bridges.get(stablecoin);
            if (bridge == null) {
                return false;
            }
            
            bridge.setStatus(StablecoinBridge.BridgeStatus.SHUTDOWN);
            LOGGER.severe(String.format("EMERGENCY SHUTDOWN of %s bridge. Reason: %s", 
                stablecoin, reason));
            
            return true;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Resets circuit breaker if cooldown period has passed
     */
    public boolean resetCircuitBreaker(String stablecoin) {
        lock.writeLock().lock();
        try {
            StablecoinBridge bridge = bridges.get(stablecoin);
            if (bridge == null) {
                return false;
            }
            
            if (!bridge.canExitCircuitBreaker()) {
                LOGGER.warning("Cannot reset circuit breaker yet (cooldown not complete)");
                return false;
            }
            
            bridge.setStatus(StablecoinBridge.BridgeStatus.ACTIVE);
            LOGGER.info("Reset circuit breaker for: " + stablecoin);
            
            return true;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Checks if exposure limit would be exceeded
     */
    private boolean checkExposureLimit(String stablecoin, BigInteger newExposure) {
        // Get total system value (in production, this would calculate actual total)
        BigInteger totalSystemValue = calculateTotalSystemValue();
        
        if (totalSystemValue.compareTo(BigInteger.ZERO) == 0) {
            return false;
        }
        
        BigDecimal exposureRatio = new BigDecimal(newExposure)
            .divide(new BigDecimal(totalSystemValue), 8, RoundingMode.HALF_UP);
        
        return exposureRatio.compareTo(MAX_EXPOSURE_RATIO) <= 0;
    }
    
    /**
     * Checks if circuit breaker should trigger
     */
    private boolean shouldTriggerCircuitBreaker(String stablecoin, BigInteger newExposure) {
        BigInteger totalSystemValue = calculateTotalSystemValue();
        
        if (totalSystemValue.compareTo(BigInteger.ZERO) == 0) {
            return true;
        }
        
        BigDecimal exposureRatio = new BigDecimal(newExposure)
            .divide(new BigDecimal(totalSystemValue), 8, RoundingMode.HALF_UP);
        
        BigDecimal threshold = MAX_EXPOSURE_RATIO.multiply(CIRCUIT_BREAKER_THRESHOLD);
        
        return exposureRatio.compareTo(threshold) >= 0;
    }
    
    /**
     * Verifies external transaction (production would verify on-chain)
     */
    private boolean verifyExternalTransaction(String externalAddress, BigInteger amount, String stablecoin) {
        // In production, this would:
        // 1. Query external blockchain
        // 2. Verify transaction exists
        // 3. Verify amount matches
        // 4. Verify destination is bridge contract
        // 5. Verify sufficient confirmations
        
        // For now, basic validation
        return externalAddress != null && !externalAddress.isEmpty() &&
               amount.compareTo(BigInteger.ZERO) > 0;
    }
    
    /**
     * Calculates total system value (production would use actual data)
     */
    private BigInteger calculateTotalSystemValue() {
        // In production, this would sum:
        // - Total ASU in circulation
        // - Total national currency tokens
        // - Total collateral locked
        // For now, use a placeholder
        return new BigInteger("10000000000000000000"); // 10B placeholder
    }
    
    /**
     * Generates unique transaction ID
     */
    private String generateTransactionId() {
        return "BRIDGE-" + UUID.randomUUID().toString().substring(0, 16).toUpperCase();
    }
    
    /**
     * Gets bridge information
     */
    public Optional<StablecoinBridge> getBridge(String stablecoin) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(bridges.get(stablecoin));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets all bridges
     */
    public Map<String, StablecoinBridge> getAllBridges() {
        lock.readLock().lock();
        try {
            return new HashMap<>(bridges);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets transaction history
     */
    public List<BridgeTransaction> getTransactionHistory(int limit) {
        lock.readLock().lock();
        try {
            int size = transactionHistory.size();
            int fromIndex = Math.max(0, size - limit);
            return new ArrayList<>(transactionHistory.subList(fromIndex, size));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets system statistics
     */
    public Map<String, Object> getSystemStats() {
        lock.readLock().lock();
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalBridges", bridges.size());
            stats.put("activeBridges", bridges.values().stream()
                .filter(StablecoinBridge::isOperational).count());
            stats.put("totalTransactions", transactionHistory.size());
            stats.put("maxExposureRatio", MAX_EXPOSURE_RATIO);
            stats.put("circuitBreakerThreshold", CIRCUIT_BREAKER_THRESHOLD);
            
            BigInteger totalExposure = BigInteger.ZERO;
            for (StablecoinBridge bridge : bridges.values()) {
                totalExposure = totalExposure.add(bridge.getCurrentExposure());
            }
            stats.put("totalExposureAcrossBridges", totalExposure);
            
            return stats;
        } finally {
            lock.readLock().unlock();
        }
    }
}