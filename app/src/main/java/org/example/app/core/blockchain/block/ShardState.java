package org.example.app.core.blockchain.block;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * ShardState manages the state of a single shard in the multi-shard
 * African Tokenized Currency System.
 * 
 * Sharding Strategy:
 * - 20 geographic shards (West, East, Central, Southern, North Africa)
 * - ~5,000 TPS per shard
 * - Total capacity: 100,000 TPS continent-wide
 * - Cross-shard communication via relay chain
 */
public class ShardState {
    private static final Logger logger = Logger.getLogger(ShardState.class.getName());
    
    // Shard identification
    private final int shardId;
    private final String region; // e.g., "WEST_AFRICA_1"
    
    // Shard blockchain state
    private byte[] stateRoot;
    private long blockHeight;
    private String latestBlockHash;
    
    // Transaction tracking
    private final AtomicLong totalTransactions;
    private final AtomicLong crossShardTransactions;
    private final AtomicLong incomingCrossShardTx;
    private final AtomicLong outgoingCrossShardTx;
    
    // Performance metrics
    private double currentTPS; // Current transactions per second
    private double averageTPS; // Average TPS
    private long lastUpdateTime;
    private final List<Double> recentTPS; // Rolling window of TPS measurements
    private static final int TPS_WINDOW_SIZE = 60; // 60 seconds
    
    // Account state in this shard
    private final Map<String, BigInteger> accountBalances;
    private final Map<String, Long> accountNonces;
    
    // Token state in this shard
    private final Map<String, BigInteger> tokenSupply; // token -> total supply in shard
    
    // Validator information for this shard
    private final Set<String> activeValidators;
    private String currentLeader; // Current PBFT primary
    
    // Cross-shard pending transactions
    private final Map<String, CrossShardPendingTx> pendingCrossShardTx;
    
    // Shard health status
    private ShardHealth healthStatus;
    
    /**
     * Shard health enum
     */
    public enum ShardHealth {
        HEALTHY,        // Operating normally
        DEGRADED,       // Some performance issues
        SYNCING,        // Syncing with other shards
        OFFLINE,        // Shard is offline
        RECOVERING      // Recovering from failure
    }
    
    /**
     * Cross-shard pending transaction
     */
    public static class CrossShardPendingTx {
        private final String txHash;
        private final int sourceShard;
        private final int destinationShard;
        private final long timestamp;
        private final byte[] proof;
        
        public CrossShardPendingTx(String txHash, int sourceShard, int destinationShard,
                                  long timestamp, byte[] proof) {
            this.txHash = txHash;
            this.sourceShard = sourceShard;
            this.destinationShard = destinationShard;
            this.timestamp = timestamp;
            this.proof = Arrays.copyOf(proof, proof.length);
        }
        
        public String getTxHash() { return txHash; }
        public int getSourceShard() { return sourceShard; }
        public int getDestinationShard() { return destinationShard; }
        public long getTimestamp() { return timestamp; }
        public byte[] getProof() { return Arrays.copyOf(proof, proof.length); }
    }
    
    /**
     * Constructor for ShardState
     */
    public ShardState(int shardId, String region) {
        if (shardId < 0) {
            throw new IllegalArgumentException("Shard ID cannot be negative");
        }
        if (region == null || region.isEmpty()) {
            throw new IllegalArgumentException("Region cannot be null or empty");
        }
        
        this.shardId = shardId;
        this.region = region;
        this.stateRoot = new byte[32]; // Initial empty state root
        this.blockHeight = 0;
        this.latestBlockHash = "";
        
        this.totalTransactions = new AtomicLong(0);
        this.crossShardTransactions = new AtomicLong(0);
        this.incomingCrossShardTx = new AtomicLong(0);
        this.outgoingCrossShardTx = new AtomicLong(0);
        
        this.currentTPS = 0.0;
        this.averageTPS = 0.0;
        this.lastUpdateTime = System.currentTimeMillis();
        this.recentTPS = Collections.synchronizedList(new ArrayList<>());
        
        this.accountBalances = new ConcurrentHashMap<>();
        this.accountNonces = new ConcurrentHashMap<>();
        this.tokenSupply = new ConcurrentHashMap<>();
        
        this.activeValidators = ConcurrentHashMap.newKeySet();
        this.currentLeader = "";
        
        this.pendingCrossShardTx = new ConcurrentHashMap<>();
        this.healthStatus = ShardHealth.HEALTHY;
        
        logger.info("Created ShardState for shard " + shardId + " in region " + region);
    }
    
    // Getters
    
    public int getShardId() {
        return shardId;
    }
    
    public String getRegion() {
        return region;
    }
    
    public byte[] getStateRoot() {
        return Arrays.copyOf(stateRoot, stateRoot.length);
    }
    
    public long getBlockHeight() {
        return blockHeight;
    }
    
    public String getLatestBlockHash() {
        return latestBlockHash;
    }
    
    public long getTotalTransactions() {
        return totalTransactions.get();
    }
    
    public long getCrossShardTransactions() {
        return crossShardTransactions.get();
    }
    
    public double getCurrentTPS() {
        return currentTPS;
    }
    
    public double getAverageTPS() {
        return averageTPS;
    }
    
    public ShardHealth getHealthStatus() {
        return healthStatus;
    }
    
    public Set<String> getActiveValidators() {
        return new HashSet<>(activeValidators);
    }
    
    public String getCurrentLeader() {
        return currentLeader;
    }
    
    // State update methods
    
    /**
     * Updates the shard state root
     */
    public void updateStateRoot(byte[] newStateRoot) {
        if (newStateRoot == null || newStateRoot.length != 32) {
            throw new IllegalArgumentException("Invalid state root");
        }
        this.stateRoot = Arrays.copyOf(newStateRoot, newStateRoot.length);
        logger.fine("Updated state root for shard " + shardId);
    }
    
    /**
     * Updates block information
     */
    public void updateBlock(long newHeight, String newBlockHash, byte[] newStateRoot) {
        if (newHeight <= blockHeight) {
            throw new IllegalArgumentException("New block height must be greater than current");
        }
        
        this.blockHeight = newHeight;
        this.latestBlockHash = newBlockHash;
        this.stateRoot = Arrays.copyOf(newStateRoot, newStateRoot.length);
        
        logger.fine("Updated shard " + shardId + " to block " + newHeight);
    }
    
    /**
     * Increments transaction count and updates TPS
     */
    public void incrementTransactionCount(int txCount) {
        totalTransactions.addAndGet(txCount);
        updateTPS(txCount);
    }
    
    /**
     * Increments cross-shard transaction count
     */
    public void incrementCrossShardTxCount() {
        crossShardTransactions.incrementAndGet();
    }
    
    /**
     * Records an incoming cross-shard transaction
     */
    public void recordIncomingCrossShardTx() {
        incomingCrossShardTx.incrementAndGet();
        crossShardTransactions.incrementAndGet();
    }
    
    /**
     * Records an outgoing cross-shard transaction
     */
    public void recordOutgoingCrossShardTx() {
        outgoingCrossShardTx.incrementAndGet();
        crossShardTransactions.incrementAndGet();
    }
    
    /**
     * Updates TPS metrics
     */
    private void updateTPS(int txCount) {
        long currentTime = System.currentTimeMillis();
        long timeDelta = currentTime - lastUpdateTime;
        
        if (timeDelta > 0) {
            // Calculate current TPS
            currentTPS = (txCount * 1000.0) / timeDelta;
            
            // Add to rolling window
            synchronized (recentTPS) {
                recentTPS.add(currentTPS);
                
                // Keep only recent measurements
                if (recentTPS.size() > TPS_WINDOW_SIZE) {
                    recentTPS.remove(0);
                }
                
                // Calculate average TPS
                averageTPS = recentTPS.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
            }
            
            lastUpdateTime = currentTime;
        }
    }
    
    /**
     * Updates account balance
     */
    public void updateAccountBalance(String address, BigInteger newBalance) {
        if (address == null || newBalance == null) {
            throw new IllegalArgumentException("Address and balance cannot be null");
        }
        if (newBalance.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("Balance cannot be negative");
        }
        
        accountBalances.put(address, newBalance);
    }
    
    /**
     * Gets account balance
     */
    public BigInteger getAccountBalance(String address) {
        return accountBalances.getOrDefault(address, BigInteger.ZERO);
    }
    
    /**
     * Updates account nonce
     */
    public void updateAccountNonce(String address, long nonce) {
        if (address == null) {
            throw new IllegalArgumentException("Address cannot be null");
        }
        accountNonces.put(address, nonce);
    }
    
    /**
     * Gets account nonce
     */
    public long getAccountNonce(String address) {
        return accountNonces.getOrDefault(address, 0L);
    }
    
    /**
     * Updates token supply in this shard
     */
    public void updateTokenSupply(String token, BigInteger supply) {
        if (token == null || supply == null) {
            throw new IllegalArgumentException("Token and supply cannot be null");
        }
        tokenSupply.put(token, supply);
    }
    
    /**
     * Gets token supply in this shard
     */
    public BigInteger getTokenSupply(String token) {
        return tokenSupply.getOrDefault(token, BigInteger.ZERO);
    }
    
    /**
     * Adds a validator to this shard
     */
    public void addValidator(String validatorId) {
        if (validatorId == null || validatorId.isEmpty()) {
            throw new IllegalArgumentException("Validator ID cannot be null or empty");
        }
        activeValidators.add(validatorId);
        logger.fine("Added validator " + validatorId + " to shard " + shardId);
    }
    
    /**
     * Removes a validator from this shard
     */
    public void removeValidator(String validatorId) {
        activeValidators.remove(validatorId);
        logger.fine("Removed validator " + validatorId + " from shard " + shardId);
    }
    
    /**
     * Sets the current PBFT leader
     */
    public void setCurrentLeader(String leaderId) {
        this.currentLeader = leaderId;
        logger.fine("Shard " + shardId + " leader is now " + leaderId);
    }
    
    /**
     * Adds a pending cross-shard transaction
     */
    public void addPendingCrossShardTx(String txHash, int sourceShard, int destShard,
                                       byte[] proof) {
        CrossShardPendingTx pendingTx = new CrossShardPendingTx(
            txHash, sourceShard, destShard, System.currentTimeMillis(), proof
        );
        pendingCrossShardTx.put(txHash, pendingTx);
    }
    
    /**
     * Removes a pending cross-shard transaction
     */
    public void removePendingCrossShardTx(String txHash) {
        pendingCrossShardTx.remove(txHash);
    }
    
    /**
     * Gets pending cross-shard transaction
     */
    public CrossShardPendingTx getPendingCrossShardTx(String txHash) {
        return pendingCrossShardTx.get(txHash);
    }
    
    /**
     * Gets all pending cross-shard transactions
     */
    public Collection<CrossShardPendingTx> getAllPendingCrossShardTx() {
        return new ArrayList<>(pendingCrossShardTx.values());
    }
    
    /**
     * Cleans up old pending cross-shard transactions (older than 5 minutes)
     */
    public void cleanupOldPendingTx() {
        long cutoffTime = System.currentTimeMillis() - (5 * 60 * 1000);
        
        pendingCrossShardTx.entrySet().removeIf(entry -> 
            entry.getValue().getTimestamp() < cutoffTime
        );
    }
    
    /**
     * Updates shard health status
     */
    public void updateHealthStatus(ShardHealth newStatus) {
        this.healthStatus = newStatus;
        logger.info("Shard " + shardId + " health status: " + newStatus);
    }
    
    /**
     * Checks if shard is healthy
     */
    public boolean isHealthy() {
        return healthStatus == ShardHealth.HEALTHY;
    }
    
    /**
     * Checks if shard meets target TPS (5,000 TPS)
     */
    public boolean meetsTargetTPS() {
        return averageTPS >= 4000; // Allow 80% of target (4,000 TPS)
    }
    
    /**
     * Gets shard utilization percentage
     */
    public double getUtilization() {
        double targetTPS = 5000.0;
        return Math.min(100.0, (averageTPS / targetTPS) * 100.0);
    }
    
    /**
     * Gets cross-shard traffic ratio
     */
    public double getCrossShardTrafficRatio() {
        long total = totalTransactions.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) crossShardTransactions.get() / total;
    }
    
    /**
     * Serializes shard state to bytes
     */
    public byte[] serialize() {
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        
        // Shard identification
        buffer.putInt(shardId);
        byte[] regionBytes = region.getBytes();
        buffer.putInt(regionBytes.length);
        buffer.put(regionBytes);
        
        // State root and block info
        buffer.put(stateRoot);
        buffer.putLong(blockHeight);
        byte[] blockHashBytes = latestBlockHash.getBytes();
        buffer.putInt(blockHashBytes.length);
        buffer.put(blockHashBytes);
        
        // Transaction counts
        buffer.putLong(totalTransactions.get());
        buffer.putLong(crossShardTransactions.get());
        buffer.putLong(incomingCrossShardTx.get());
        buffer.putLong(outgoingCrossShardTx.get());
        
        // TPS metrics
        buffer.putDouble(currentTPS);
        buffer.putDouble(averageTPS);
        
        // Health status
        buffer.put((byte) healthStatus.ordinal());
        
        // Validator count
        buffer.putInt(activeValidators.size());
        
        buffer.flip();
        byte[] result = new byte[buffer.limit()];
        buffer.get(result);
        return result;
    }
    
    /**
     * Gets shard statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("shardId", shardId);
        stats.put("region", region);
        stats.put("blockHeight", blockHeight);
        stats.put("totalTransactions", totalTransactions.get());
        stats.put("crossShardTransactions", crossShardTransactions.get());
        stats.put("incomingCrossShardTx", incomingCrossShardTx.get());
        stats.put("outgoingCrossShardTx", outgoingCrossShardTx.get());
        stats.put("currentTPS", String.format("%.2f", currentTPS));
        stats.put("averageTPS", String.format("%.2f", averageTPS));
        stats.put("utilization", String.format("%.2f%%", getUtilization()));
        stats.put("crossShardRatio", String.format("%.2f%%", getCrossShardTrafficRatio() * 100));
        stats.put("healthStatus", healthStatus.toString());
        stats.put("activeValidators", activeValidators.size());
        stats.put("pendingCrossShardTx", pendingCrossShardTx.size());
        stats.put("accountCount", accountBalances.size());
        stats.put("tokenCount", tokenSupply.size());
        return stats;
    }
    
    @Override
    public String toString() {
        return String.format("ShardState{id=%d, region=%s, height=%d, txCount=%d, TPS=%.2f, health=%s}",
            shardId, region, blockHeight, totalTransactions.get(), averageTPS, healthStatus);
    }
}