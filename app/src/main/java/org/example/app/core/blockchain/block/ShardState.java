package org.example.app.core.blockchain.block;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * ShardState manages the state of a single shard in the multi-shard
 * African Tokenized Currency System with African sovereignty enforcement.
 * 
 * AFRICAN SOVEREIGNTY ENHANCEMENTS:
 * - AfricanRegion enum (5 regions: North, West, East, Central, Southern)
 * - DAC node tracking for data availability (7 nodes per region)
 * - Validator reputation requirements (min 0.60)
 * - Geographic distribution enforcement (max 33% from any region)
 * - ASU settlement metrics and tracking
 * - Cross-region collaboration statistics
 * - Treasury subsidy eligibility tracking
 * - Submarine cable routing preferences
 * - Committee rotation tracking
 * 
 * Sharding Strategy:
 * - 20 geographic shards across 5 African regions
 * - ~5,000 TPS per shard
 * - Total capacity: 100,000 TPS continent-wide
 * - Cross-shard communication via relay chain with multi-region validation
 */
public class ShardState {
    private static final Logger logger = Logger.getLogger(ShardState.class.getName());
    
    // African Geographic Regions
    public enum AfricanRegion {
        NORTH_AFRICA,    // Shards 0-3, DAC nodes 0-6
        WEST_AFRICA,     // Shards 4-7, DAC nodes 7-13
        EAST_AFRICA,     // Shard 8-11, DAC nodes 14-20
        CENTRAL_AFRICA,  // Shards 12-15, DAC nodes 21-27
        SOUTHERN_AFRICA  // Shards 16-19, DAC nodes 28-34
    }
    
    /**
     * Validator information with reputation
     */
    public static class ValidatorInfo {
        public final String validatorId;
        public final AfricanRegion region;
        public final double reputationScore;
        public final long joinedTime;
        public long lastActiveTime;
        
        public ValidatorInfo(String validatorId, AfricanRegion region, double reputationScore) {
            this.validatorId = validatorId;
            this.region = region;
            this.reputationScore = reputationScore;
            this.joinedTime = System.currentTimeMillis();
            this.lastActiveTime = System.currentTimeMillis();
        }
        
        public boolean isEligible() {
            // Minimum reputation of 0.60 required
            return reputationScore >= 0.60;
        }
        
        public void updateActivity() {
            this.lastActiveTime = System.currentTimeMillis();
        }
    }
    
    /**
     * DAC node information for this shard's region
     */
    public static class DACNodeInfo {
        public final int nodeId; // 0-34 globally
        public final AfricanRegion region;
        public final double reputationScore;
        public long attestationCount;
        public long lastAttestationTime;
        
        public DACNodeInfo(int nodeId, AfricanRegion region, double reputationScore) {
            this.nodeId = nodeId;
            this.region = region;
            this.reputationScore = reputationScore;
            this.attestationCount = 0;
            this.lastAttestationTime = 0;
        }
        
        public boolean isEligible() {
            // Minimum reputation of 0.50 required for DAC nodes
            return reputationScore >= 0.50;
        }
        
        public void recordAttestation() {
            this.attestationCount++;
            this.lastAttestationTime = System.currentTimeMillis();
        }
    }
    
    // Shard identification
    private final int shardId;
    private final AfricanRegion region; // Changed from String to enum
    
    // Shard blockchain state
    private byte[] stateRoot;
    private long blockHeight;
    private String latestBlockHash;
    
    // Transaction tracking
    private final AtomicLong totalTransactions;
    private final AtomicLong crossShardTransactions;
    private final AtomicLong incomingCrossShardTx;
    private final AtomicLong outgoingCrossShardTx;
    
    // NEW: African sovereignty transaction tracking
    private final AtomicLong asuSettlementCount;
    private final AtomicLong treasurySubsidizedCount;
    private final AtomicLong crossRegionTransactions;
    private final Map<AfricanRegion, AtomicLong> transactionsByTargetRegion;
    
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
    private final Map<String, BigInteger> asuReserves; // ASU reserves per token
    
    // NEW: Validator information with reputation
    private final Map<String, ValidatorInfo> validators;
    private String currentLeader; // Current PBFT primary
    private final Map<AfricanRegion, Integer> validatorsByRegion;
    
    // NEW: DAC nodes for this shard's region
    private final Map<Integer, DACNodeInfo> dacNodes; // 7 nodes for this region
    
    // Cross-shard pending transactions
    private final Map<String, CrossShardPendingTx> pendingCrossShardTx;
    
    // Shard health status
    private ShardHealth healthStatus;
    
    // Geographic distribution limits
    private static final double MAX_REGION_VALIDATOR_PERCENTAGE = 0.33;
    
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
     * Cross-shard pending transaction with regional tracking
     */
    public static class CrossShardPendingTx {
        private final String txHash;
        private final int sourceShard;
        private final int destinationShard;
        private final AfricanRegion sourceRegion;
        private final AfricanRegion destRegion;
        private final long timestamp;
        private final byte[] proof;
        private final boolean settlesViaASU;
        
        public CrossShardPendingTx(String txHash, int sourceShard, int destinationShard,
                                  AfricanRegion sourceRegion, AfricanRegion destRegion,
                                  long timestamp, byte[] proof, boolean settlesViaASU) {
            this.txHash = txHash;
            this.sourceShard = sourceShard;
            this.destinationShard = destinationShard;
            this.sourceRegion = sourceRegion;
            this.destRegion = destRegion;
            this.timestamp = timestamp;
            this.proof = Arrays.copyOf(proof, proof.length);
            this.settlesViaASU = settlesViaASU;
        }
        
        public String getTxHash() { return txHash; }
        public int getSourceShard() { return sourceShard; }
        public int getDestinationShard() { return destinationShard; }
        public AfricanRegion getSourceRegion() { return sourceRegion; }
        public AfricanRegion getDestRegion() { return destRegion; }
        public long getTimestamp() { return timestamp; }
        public byte[] getProof() { return Arrays.copyOf(proof, proof.length); }
        public boolean settlesViaASU() { return settlesViaASU; }
        
        public boolean isCrossRegion() {
            return !sourceRegion.equals(destRegion);
        }
    }
    
    /**
     * Constructor for ShardState with African sovereignty
     */
    public ShardState(int shardId, AfricanRegion region) {
        if (shardId < 0 || shardId >= 20) {
            throw new IllegalArgumentException("Shard ID must be 0-19");
        }
        if (region == null) {
            throw new IllegalArgumentException("Region cannot be null");
        }
        
        // Validate shard belongs to correct region
        if (!isValidShardForRegion(shardId, region)) {
            throw new IllegalArgumentException(
                String.format("Shard %d does not belong to region %s", shardId, region)
            );
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
        
        // Initialize African sovereignty counters
        this.asuSettlementCount = new AtomicLong(0);
        this.treasurySubsidizedCount = new AtomicLong(0);
        this.crossRegionTransactions = new AtomicLong(0);
        this.transactionsByTargetRegion = new ConcurrentHashMap<>();
        for (AfricanRegion r : AfricanRegion.values()) {
            transactionsByTargetRegion.put(r, new AtomicLong(0));
        }
        
        this.currentTPS = 0.0;
        this.averageTPS = 0.0;
        this.lastUpdateTime = System.currentTimeMillis();
        this.recentTPS = Collections.synchronizedList(new ArrayList<>());
        
        this.accountBalances = new ConcurrentHashMap<>();
        this.accountNonces = new ConcurrentHashMap<>();
        this.tokenSupply = new ConcurrentHashMap<>();
        this.asuReserves = new ConcurrentHashMap<>();
        
        this.validators = new ConcurrentHashMap<>();
        this.currentLeader = "";
        this.validatorsByRegion = new ConcurrentHashMap<>();
        
        this.dacNodes = new ConcurrentHashMap<>();
        initializeDACNodes();
        
        this.pendingCrossShardTx = new ConcurrentHashMap<>();
        this.healthStatus = ShardHealth.HEALTHY;
        
        logger.info(String.format("Created ShardState for shard %d in region %s with %d DAC nodes",
            shardId, region, dacNodes.size()));
    }
    
    /**
     * Validates if a shard belongs to a region
     */
    private boolean isValidShardForRegion(int shardId, AfricanRegion region) {
        switch (region) {
            case NORTH_AFRICA:
                return shardId >= 0 && shardId <= 3;
            case WEST_AFRICA:
                return shardId >= 4 && shardId <= 7;
            case EAST_AFRICA:
                return shardId >= 8 && shardId <= 11;
            case CENTRAL_AFRICA:
                return shardId >= 12 && shardId <= 15;
            case SOUTHERN_AFRICA:
                return shardId >= 16 && shardId <= 19;
            default:
                return false;
        }
    }
    
    /**
     * Initializes DAC nodes for this shard's region
     */
    private void initializeDACNodes() {
        int startNodeId = getRegionDACStartId(region);
        
        // Each region has 7 DAC nodes
        for (int i = 0; i < 7; i++) {
            int nodeId = startNodeId + i;
            // Initialize with perfect reputation (1.0)
            DACNodeInfo nodeInfo = new DACNodeInfo(nodeId, region, 1.0);
            dacNodes.put(nodeId, nodeInfo);
        }
        
        logger.fine(String.format("Initialized %d DAC nodes for region %s (nodes %d-%d)",
            dacNodes.size(), region, startNodeId, startNodeId + 6));
    }
    
    /**
     * Gets the starting DAC node ID for a region
     */
    private int getRegionDACStartId(AfricanRegion region) {
        switch (region) {
            case NORTH_AFRICA: return 0;
            case WEST_AFRICA: return 7;
            case EAST_AFRICA: return 14;
            case CENTRAL_AFRICA: return 21;
            case SOUTHERN_AFRICA: return 28;
            default: return 0;
        }
    }
    
    // Getters
    
    public int getShardId() {
        return shardId;
    }
    
    public AfricanRegion getRegion() {
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
    
    public long getASUSettlementCount() {
        return asuSettlementCount.get();
    }
    
    public long getTreasurySubsidizedCount() {
        return treasurySubsidizedCount.get();
    }
    
    public long getCrossRegionTransactions() {
        return crossRegionTransactions.get();
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
    
    public Collection<ValidatorInfo> getValidators() {
        return new ArrayList<>(validators.values());
    }
    
    public String getCurrentLeader() {
        return currentLeader;
    }
    
    public Collection<DACNodeInfo> getDACNodes() {
        return new ArrayList<>(dacNodes.values());
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
     * NEW: Records ASU settlement transaction
     */
    public void recordASUSettlement() {
        asuSettlementCount.incrementAndGet();
        totalTransactions.incrementAndGet();
        updateTPS(1);
    }
    
    /**
     * NEW: Records Treasury subsidized transaction
     */
    public void recordTreasurySubsidized() {
        treasurySubsidizedCount.incrementAndGet();
    }
    
    /**
     * NEW: Records cross-region transaction
     */
    public void recordCrossRegionTransaction(AfricanRegion targetRegion) {
        crossRegionTransactions.incrementAndGet();
        transactionsByTargetRegion.get(targetRegion).incrementAndGet();
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
    public void recordIncomingCrossShardTx(AfricanRegion sourceRegion, boolean settlesViaASU) {
        incomingCrossShardTx.incrementAndGet();
        crossShardTransactions.incrementAndGet();
        
        if (!sourceRegion.equals(region)) {
            crossRegionTransactions.incrementAndGet();
        }
        
        if (settlesViaASU) {
            asuSettlementCount.incrementAndGet();
        }
    }
    
    /**
     * Records an outgoing cross-shard transaction
     */
    public void recordOutgoingCrossShardTx(AfricanRegion destRegion, boolean settlesViaASU) {
        outgoingCrossShardTx.incrementAndGet();
        crossShardTransactions.incrementAndGet();
        
        if (!destRegion.equals(region)) {
            crossRegionTransactions.incrementAndGet();
            transactionsByTargetRegion.get(destRegion).incrementAndGet();
        }
        
        if (settlesViaASU) {
            asuSettlementCount.incrementAndGet();
        }
    }
    
    /**
     * Updates TPS metrics
     */
    private void updateTPS(int txCount) {
        long currentTime = System.currentTimeMillis();
        long timeDiff = currentTime - lastUpdateTime;
        
        if (timeDiff > 0) {
            // Calculate current TPS
            double tps = (txCount * 1000.0) / timeDiff;
            currentTPS = tps;
            
            // Add to rolling window
            synchronized (recentTPS) {
                recentTPS.add(tps);
                
                // Keep only last 60 measurements
                while (recentTPS.size() > TPS_WINDOW_SIZE) {
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
     * NEW: Updates ASU reserves for a token
     */
    public void updateASUReserves(String token, BigInteger reserves) {
        if (token == null || reserves == null) {
            throw new IllegalArgumentException("Token and reserves cannot be null");
        }
        asuReserves.put(token, reserves);
    }
    
    /**
     * NEW: Gets ASU reserves for a token
     */
    public BigInteger getASUReserves(String token) {
        return asuReserves.getOrDefault(token, BigInteger.ZERO);
    }
    
    /**
     * NEW: Adds a validator with reputation and region tracking
     */
    public void addValidator(String validatorId, AfricanRegion validatorRegion, double reputation) {
        if (validatorId == null || validatorId.isEmpty()) {
            throw new IllegalArgumentException("Validator ID cannot be null or empty");
        }
        if (reputation < 0.60) {
            throw new IllegalArgumentException(
                String.format("Validator reputation %.2f below minimum 0.60", reputation)
            );
        }
        
        ValidatorInfo validator = new ValidatorInfo(validatorId, validatorRegion, reputation);
        
        // Check geographic distribution before adding
        if (!canAddValidatorFromRegion(validatorRegion)) {
            logger.warning(String.format(
                "Cannot add validator from %s - would exceed 33%% regional limit", validatorRegion
            ));
            return;
        }
        
        validators.put(validatorId, validator);
        validatorsByRegion.merge(validatorRegion, 1, Integer::sum);
        
        logger.fine(String.format("Added validator %s from %s (reputation: %.2f) to shard %d",
            validatorId, validatorRegion, reputation, shardId));
    }
    
    /**
     * NEW: Checks if a validator from a region can be added without violating 33% limit
     */
    private boolean canAddValidatorFromRegion(AfricanRegion validatorRegion) {
        int totalValidators = validators.size();
        if (totalValidators == 0) return true;
        
        int regionCount = validatorsByRegion.getOrDefault(validatorRegion, 0);
        double newPercentage = (double) (regionCount + 1) / (totalValidators + 1);
        
        // No region can exceed 33%
        return newPercentage <= MAX_REGION_VALIDATOR_PERCENTAGE;
    }
    
    /**
     * Removes a validator from this shard
     */
    public void removeValidator(String validatorId) {
        ValidatorInfo validator = validators.remove(validatorId);
        if (validator != null) {
            validatorsByRegion.merge(validator.region, -1, Integer::sum);
            logger.fine("Removed validator " + validatorId + " from shard " + shardId);
        }
    }
    
    /**
     * Sets the current PBFT leader
     */
    public void setCurrentLeader(String leaderId) {
        ValidatorInfo leader = validators.get(leaderId);
        if (leader != null && leader.isEligible()) {
            this.currentLeader = leaderId;
            leader.updateActivity();
            logger.fine("Shard " + shardId + " leader is now " + leaderId);
        } else {
            logger.warning("Cannot set leader " + leaderId + " - not eligible");
        }
    }
    
    /**
     * NEW: Records DAC attestation
     */
    public void recordDACAttestation(int nodeId) {
        DACNodeInfo dacNode = dacNodes.get(nodeId);
        if (dacNode != null) {
            dacNode.recordAttestation();
            logger.fine(String.format("DAC node %d recorded attestation (total: %d)",
                nodeId, dacNode.attestationCount));
        }
    }
    
    /**
     * Adds a pending cross-shard transaction with regional tracking
     */
    public void addPendingCrossShardTx(String txHash, int sourceShard, int destShard,
                                       AfricanRegion sourceRegion, AfricanRegion destRegion,
                                       byte[] proof, boolean settlesViaASU) {
        CrossShardPendingTx pendingTx = new CrossShardPendingTx(
            txHash, sourceShard, destShard, sourceRegion, destRegion,
            System.currentTimeMillis(), proof, settlesViaASU
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
     * NEW: Gets ASU settlement ratio
     */
    public double getASUSettlementRatio() {
        long total = totalTransactions.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) asuSettlementCount.get() / total;
    }
    
    /**
     * NEW: Gets Treasury subsidy ratio
     */
    public double getTreasurySubsidyRatio() {
        long total = totalTransactions.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) treasurySubsidizedCount.get() / total;
    }
    
    /**
     * NEW: Gets cross-region traffic ratio
     */
    public double getCrossRegionTrafficRatio() {
        long total = totalTransactions.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) crossRegionTransactions.get() / total;
    }
    
    /**
     * NEW: Gets validator geographic distribution
     */
    public Map<AfricanRegion, Integer> getValidatorDistribution() {
        return new EnumMap<>(validatorsByRegion);
    }
    
    /**
     * NEW: Checks if validator distribution is balanced (no region > 33%)
     */
    public boolean hasBalancedValidators() {
        int total = validators.size();
        if (total == 0) return true;
        
        for (Integer count : validatorsByRegion.values()) {
            double percentage = (double) count / total;
            if (percentage > MAX_REGION_VALIDATOR_PERCENTAGE) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * NEW: Gets eligible validators (reputation >= 0.60)
     */
    public List<ValidatorInfo> getEligibleValidators() {
        return validators.values().stream()
            .filter(ValidatorInfo::isEligible)
            .toList();
    }
    
    /**
     * NEW: Gets eligible DAC nodes (reputation >= 0.50)
     */
    public List<DACNodeInfo> getEligibleDACNodes() {
        return dacNodes.values().stream()
            .filter(DACNodeInfo::isEligible)
            .toList();
    }
    
    /**
     * Serializes shard state to bytes
     */
    public byte[] serialize() {
        ByteBuffer buffer = ByteBuffer.allocate(16384);
        
        // Shard identification
        buffer.putInt(shardId);
        buffer.putInt(region.ordinal());
        
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
        buffer.putLong(asuSettlementCount.get());
        buffer.putLong(treasurySubsidizedCount.get());
        buffer.putLong(crossRegionTransactions.get());
        
        // TPS metrics
        buffer.putDouble(currentTPS);
        buffer.putDouble(averageTPS);
        
        // Health status
        buffer.put((byte) healthStatus.ordinal());
        
        // Validator count and distribution
        buffer.putInt(validators.size());
        buffer.putInt(validatorsByRegion.size());
        
        // DAC node count
        buffer.putInt(dacNodes.size());
        
        buffer.flip();
        byte[] result = new byte[buffer.limit()];
        buffer.get(result);
        return result;
    }
    
    /**
     * Gets shard statistics with African sovereignty metrics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("shardId", shardId);
        stats.put("region", region.toString());
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
        
        // African sovereignty metrics
        stats.put("asuSettlements", asuSettlementCount.get());
        stats.put("asuSettlementRatio", String.format("%.2f%%", getASUSettlementRatio() * 100));
        stats.put("treasurySubsidized", treasurySubsidizedCount.get());
        stats.put("treasurySubsidyRatio", String.format("%.2f%%", getTreasurySubsidyRatio() * 100));
        stats.put("crossRegionTransactions", crossRegionTransactions.get());
        stats.put("crossRegionRatio", String.format("%.2f%%", getCrossRegionTrafficRatio() * 100));
        stats.put("validators", validators.size());
        stats.put("eligibleValidators", getEligibleValidators().size());
        stats.put("validatorDistribution", validatorsByRegion);
        stats.put("validatorsBalanced", hasBalancedValidators());
        stats.put("dacNodes", dacNodes.size());
        stats.put("eligibleDACNodes", getEligibleDACNodes().size());
        stats.put("pendingCrossShardTx", pendingCrossShardTx.size());
        stats.put("accountCount", accountBalances.size());
        stats.put("tokenCount", tokenSupply.size());
        stats.put("asuReserveTokens", asuReserves.size());
        
        return stats;
    }
    
    @Override
    public String toString() {
        return String.format("ShardState{id=%d, region=%s, height=%d, txCount=%d, TPS=%.2f, " +
            "ASU=%.1f%%, validators=%d/%d regions, health=%s}",
            shardId, region, blockHeight, totalTransactions.get(), averageTPS,
            getASUSettlementRatio() * 100, validators.size(), validatorsByRegion.size(), healthStatus);
    }
}