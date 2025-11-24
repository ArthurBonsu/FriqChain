package org.example.app.core.blockchain.block;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import java.util.logging.Logger;

/**
 * CheckpointState represents a snapshot of the blockchain state at a specific height.
 * Used for pruning and fast synchronization in the African Tokenized Currency System.
 * 
 * Pruning Strategy:
 * - Create checkpoint every N blocks (e.g., every 100 blocks)
 * - Keep last M checkpoints (e.g., 10 checkpoints)
 * - Prune old blocks between checkpoints
 * - Target: <100 GB/year per node
 * - Enables stateless clients and fast sync
 */
public class CheckpointState {
    private static final Logger logger = Logger.getLogger(CheckpointState.class.getName());
    
    // Checkpoint identification
    private final long blockNumber;
    private final byte[] stateRoot;
    private final long timestamp;
    private final String checkpointHash;
    
    // State snapshots
    private final Map<String, Map<String, BigInteger>> tokenBalances; // address -> (token -> balance)
    private final Map<String, BigInteger> proofOfReserves; // token -> reserve amount
    private final Map<String, Double> asuBasketWeights; // currency -> weight
    
    // Account state summary
    private final int totalAccounts;
    private final int totalTokens;
    private final BigInteger totalValueLocked;
    
    // Shard state summaries
    private final Map<Integer, ShardCheckpoint> shardCheckpoints;
    
    // Settlement summary
    private final long totalSettlements;
    private final BigInteger totalSettlementVolume;
    
    // Validators at checkpoint
    private final Set<String> activeValidators;
    
    // Checkpoint metadata
    private final CheckpointType type;
    private final boolean isPrunable; // Can blocks before this be pruned?
    
    /**
     * Checkpoint type enum
     */
    public enum CheckpointType {
        REGULAR,        // Regular periodic checkpoint
        EPOCH,          // End of epoch checkpoint
        EMERGENCY,      // Emergency checkpoint (system recovery)
        UPGRADE         // System upgrade checkpoint
    }
    
    /**
     * Shard checkpoint summary
     */
    public static class ShardCheckpoint {
        private final int shardId;
        private final long blockHeight;
        private final byte[] shardStateRoot;
        private final long transactionCount;
        private final double averageTPS;
        
        public ShardCheckpoint(int shardId, long blockHeight, byte[] shardStateRoot,
                             long transactionCount, double averageTPS) {
            this.shardId = shardId;
            this.blockHeight = blockHeight;
            this.shardStateRoot = Arrays.copyOf(shardStateRoot, shardStateRoot.length);
            this.transactionCount = transactionCount;
            this.averageTPS = averageTPS;
        }
        
        public int getShardId() { return shardId; }
        public long getBlockHeight() { return blockHeight; }
        public byte[] getShardStateRoot() { return Arrays.copyOf(shardStateRoot, shardStateRoot.length); }
        public long getTransactionCount() { return transactionCount; }
        public double getAverageTPS() { return averageTPS; }
        
        @Override
        public String toString() {
            return String.format("ShardCheckpoint{shard=%d, height=%d, txCount=%d, TPS=%.2f}",
                shardId, blockHeight, transactionCount, averageTPS);
        }
    }
    
    /**
     * Constructor for CheckpointState
     */
    public CheckpointState(long blockNumber, byte[] stateRoot,
                          Map<String, Map<String, BigInteger>> tokenBalances,
                          Map<String, BigInteger> proofOfReserves,
                          Map<String, Double> asuBasketWeights) {
        this(blockNumber, stateRoot, System.currentTimeMillis(), tokenBalances,
             proofOfReserves, asuBasketWeights, new HashMap<>(), new HashSet<>(),
             0, BigInteger.ZERO, CheckpointType.REGULAR, true);
    }
    
    /**
     * Full constructor for CheckpointState
     */
    public CheckpointState(long blockNumber, byte[] stateRoot, long timestamp,
                          Map<String, Map<String, BigInteger>> tokenBalances,
                          Map<String, BigInteger> proofOfReserves,
                          Map<String, Double> asuBasketWeights,
                          Map<Integer, ShardCheckpoint> shardCheckpoints,
                          Set<String> activeValidators,
                          long totalSettlements, BigInteger totalSettlementVolume,
                          CheckpointType type, boolean isPrunable) {
        
        if (blockNumber < 0) {
            throw new IllegalArgumentException("Block number cannot be negative");
        }
        if (stateRoot == null || stateRoot.length != 32) {
            throw new IllegalArgumentException("Invalid state root");
        }
        if (tokenBalances == null || proofOfReserves == null || asuBasketWeights == null) {
            throw new IllegalArgumentException("State maps cannot be null");
        }
        
        this.blockNumber = blockNumber;
        this.stateRoot = Arrays.copyOf(stateRoot, stateRoot.length);
        this.timestamp = timestamp;
        this.tokenBalances = deepCopyTokenBalances(tokenBalances);
        this.proofOfReserves = new HashMap<>(proofOfReserves);
        this.asuBasketWeights = new HashMap<>(asuBasketWeights);
        this.shardCheckpoints = new HashMap<>(shardCheckpoints);
        this.activeValidators = new HashSet<>(activeValidators);
        this.totalSettlements = totalSettlements;
        this.totalSettlementVolume = totalSettlementVolume;
        this.type = type;
        this.isPrunable = isPrunable;
        
        // Calculate totals
        this.totalAccounts = tokenBalances.size();
        this.totalTokens = proofOfReserves.size();
        this.totalValueLocked = calculateTotalValueLocked();
        
        // Generate checkpoint hash
        this.checkpointHash = generateCheckpointHash();
        
        logger.info("Created checkpoint at block " + blockNumber + " with " +
                   totalAccounts + " accounts and " + totalTokens + " tokens");
    }
    
    /**
     * Deep copies token balances map
     */
    private Map<String, Map<String, BigInteger>> deepCopyTokenBalances(
            Map<String, Map<String, BigInteger>> original) {
        
        Map<String, Map<String, BigInteger>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, BigInteger>> entry : original.entrySet()) {
            copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return copy;
    }
    
    /**
     * Calculates total value locked across all tokens
     */
    private BigInteger calculateTotalValueLocked() {
        BigInteger total = BigInteger.ZERO;
        
        for (Map<String, BigInteger> balances : tokenBalances.values()) {
            for (BigInteger balance : balances.values()) {
                total = total.add(balance);
            }
        }
        
        return total;
    }
    
    /**
     * Generates a hash for this checkpoint
     */
    private String generateCheckpointHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Hash block number
            digest.update(ByteBuffer.allocate(8).putLong(blockNumber).array());
            
            // Hash state root
            digest.update(stateRoot);
            
            // Hash timestamp
            digest.update(ByteBuffer.allocate(8).putLong(timestamp).array());
            
            // Hash total accounts and tokens
            digest.update(ByteBuffer.allocate(8).putInt(totalAccounts).putInt(totalTokens).array());
            
            byte[] hash = digest.digest();
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            
            return hexString.toString();
            
        } catch (Exception e) {
            logger.warning("Error generating checkpoint hash: " + e.getMessage());
            return "";
        }
    }
    
    // Getters
    
    public long getBlockNumber() {
        return blockNumber;
    }
    
    public byte[] getStateRoot() {
        return Arrays.copyOf(stateRoot, stateRoot.length);
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getCheckpointHash() {
        return checkpointHash;
    }
    
    public Map<String, Map<String, BigInteger>> getTokenBalances() {
        return deepCopyTokenBalances(tokenBalances);
    }
    
    public Map<String, BigInteger> getProofOfReserves() {
        return new HashMap<>(proofOfReserves);
    }
    
    public Map<String, Double> getASUBasketWeights() {
        return new HashMap<>(asuBasketWeights);
    }
    
    public int getTotalAccounts() {
        return totalAccounts;
    }
    
    public int getTotalTokens() {
        return totalTokens;
    }
    
    public BigInteger getTotalValueLocked() {
        return totalValueLocked;
    }
    
    public Map<Integer, ShardCheckpoint> getShardCheckpoints() {
        return new HashMap<>(shardCheckpoints);
    }
    
    public Set<String> getActiveValidators() {
        return new HashSet<>(activeValidators);
    }
    
    public long getTotalSettlements() {
        return totalSettlements;
    }
    
    public BigInteger getTotalSettlementVolume() {
        return totalSettlementVolume;
    }
    
    public CheckpointType getType() {
        return type;
    }
    
    public boolean isPrunable() {
        return isPrunable;
    }
    
    /**
     * Gets account balance for a specific token
     */
    public BigInteger getAccountTokenBalance(String address, String token) {
        Map<String, BigInteger> balances = tokenBalances.get(address);
        if (balances == null) {
            return BigInteger.ZERO;
        }
        return balances.getOrDefault(token, BigInteger.ZERO);
    }
    
    /**
     * Gets all token balances for an account
     */
    public Map<String, BigInteger> getAccountBalances(String address) {
        Map<String, BigInteger> balances = tokenBalances.get(address);
        if (balances == null) {
            return new HashMap<>();
        }
        return new HashMap<>(balances);
    }
    
    /**
     * Gets reserve amount for a token
     */
    public BigInteger getTokenReserves(String token) {
        return proofOfReserves.getOrDefault(token, BigInteger.ZERO);
    }
    
    /**
     * Gets ASU weight for a currency
     */
    public double getASUWeight(String currency) {
        return asuBasketWeights.getOrDefault(currency, 0.0);
    }
    
    /**
     * Gets shard checkpoint by shard ID
     */
    public ShardCheckpoint getShardCheckpoint(int shardId) {
        return shardCheckpoints.get(shardId);
    }
    
    /**
     * Checks if this checkpoint is an epoch checkpoint
     */
    public boolean isEpochCheckpoint() {
        return type == CheckpointType.EPOCH;
    }
    
    /**
     * Checks if this checkpoint is recent (within last hour)
     */
    public boolean isRecent() {
        long oneHour = 60 * 60 * 1000;
        return (System.currentTimeMillis() - timestamp) < oneHour;
    }
    
    /**
     * Calculates the age of this checkpoint in seconds
     */
    public long getAge() {
        return (System.currentTimeMillis() - timestamp) / 1000;
    }
    
    /**
     * Validates the checkpoint
     */
    public boolean validate() {
        // Validate block number
        if (blockNumber < 0) {
            logger.warning("Invalid block number: " + blockNumber);
            return false;
        }
        
        // Validate state root
        boolean validStateRoot = false;
        for (byte b : stateRoot) {
            if (b != 0) {
                validStateRoot = true;
                break;
            }
        }
        if (!validStateRoot) {
            logger.warning("Invalid state root (all zeros)");
            return false;
        }
        
        // Validate timestamp
        if (timestamp <= 0 || timestamp > System.currentTimeMillis() + 60000) {
            logger.warning("Invalid timestamp: " + timestamp);
            return false;
        }
        
        // Validate ASU basket weights sum to 1.0
        double totalWeight = asuBasketWeights.values().stream()
            .mapToDouble(Double::doubleValue)
            .sum();
        if (Math.abs(totalWeight - 1.0) > 0.01 && !asuBasketWeights.isEmpty()) {
            logger.warning("ASU basket weights don't sum to 1.0: " + totalWeight);
            return false;
        }
        
        // Validate reserves
        for (Map.Entry<String, BigInteger> entry : proofOfReserves.entrySet()) {
            if (entry.getValue().compareTo(BigInteger.ZERO) < 0) {
                logger.warning("Negative reserve for " + entry.getKey());
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Serializes checkpoint to bytes
     */
    public byte[] serialize() {
        ByteBuffer buffer = ByteBuffer.allocate(65536); // 64KB buffer
        
        // Block info
        buffer.putLong(blockNumber);
        buffer.put(stateRoot);
        buffer.putLong(timestamp);
        
        // Checkpoint hash
        byte[] hashBytes = checkpointHash.getBytes();
        buffer.putInt(hashBytes.length);
        buffer.put(hashBytes);
        
        // Token balances count
        buffer.putInt(tokenBalances.size());
        for (Map.Entry<String, Map<String, BigInteger>> entry : tokenBalances.entrySet()) {
            // Address
            byte[] addressBytes = entry.getKey().getBytes();
            buffer.putInt(addressBytes.length);
            buffer.put(addressBytes);
            
            // Token balances for this address
            Map<String, BigInteger> balances = entry.getValue();
            buffer.putInt(balances.size());
            
            for (Map.Entry<String, BigInteger> tokenEntry : balances.entrySet()) {
                // Token symbol
                byte[] tokenBytes = tokenEntry.getKey().getBytes();
                buffer.putInt(tokenBytes.length);
                buffer.put(tokenBytes);
                
                // Balance
                byte[] balanceBytes = tokenEntry.getValue().toByteArray();
                buffer.putInt(balanceBytes.length);
                buffer.put(balanceBytes);
            }
        }
        
        // Proof of reserves
        buffer.putInt(proofOfReserves.size());
        for (Map.Entry<String, BigInteger> entry : proofOfReserves.entrySet()) {
            byte[] tokenBytes = entry.getKey().getBytes();
            buffer.putInt(tokenBytes.length);
            buffer.put(tokenBytes);
            
            byte[] reserveBytes = entry.getValue().toByteArray();
            buffer.putInt(reserveBytes.length);
            buffer.put(reserveBytes);
        }
        
        // ASU basket weights
        buffer.putInt(asuBasketWeights.size());
        for (Map.Entry<String, Double> entry : asuBasketWeights.entrySet()) {
            byte[] currencyBytes = entry.getKey().getBytes();
            buffer.putInt(currencyBytes.length);
            buffer.put(currencyBytes);
            buffer.putDouble(entry.getValue());
        }
        
        // Totals
        buffer.putInt(totalAccounts);
        buffer.putInt(totalTokens);
        byte[] tvlBytes = totalValueLocked.toByteArray();
        buffer.putInt(tvlBytes.length);
        buffer.put(tvlBytes);
        
        // Settlement info
        buffer.putLong(totalSettlements);
        byte[] settlementVolumeBytes = totalSettlementVolume.toByteArray();
        buffer.putInt(settlementVolumeBytes.length);
        buffer.put(settlementVolumeBytes);
        
        // Metadata
        buffer.put((byte) type.ordinal());
        buffer.put((byte) (isPrunable ? 1 : 0));
        
        buffer.flip();
        byte[] result = new byte[buffer.limit()];
        buffer.get(result);
        return result;
    }
    
    /**
     * Calculates the size of this checkpoint in bytes
     */
    public long getSize() {
        return serialize().length;
    }
    
    /**
     * Estimates disk space saved by pruning blocks before this checkpoint
     */
    public long estimatePruningSavings(long blocksPerCheckpoint, long avgBlockSize) {
        return blocksPerCheckpoint * avgBlockSize;
    }
    
    /**
     * Creates a compact summary of this checkpoint
     */
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("blockNumber", blockNumber);
        summary.put("checkpointHash", checkpointHash);
        summary.put("timestamp", new Date(timestamp).toString());
        summary.put("age", getAge() + " seconds");
        summary.put("totalAccounts", totalAccounts);
        summary.put("totalTokens", totalTokens);
        summary.put("totalValueLocked", totalValueLocked.toString());
        summary.put("totalSettlements", totalSettlements);
        summary.put("totalSettlementVolume", totalSettlementVolume.toString());
        summary.put("activeValidators", activeValidators.size());
        summary.put("shards", shardCheckpoints.size());
        summary.put("type", type.toString());
        summary.put("isPrunable", isPrunable);
        summary.put("size", getSize() + " bytes");
        return summary;
    }
    
    /**
     * Gets detailed statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("blockNumber", blockNumber);
        stats.put("checkpointHash", checkpointHash);
        stats.put("timestamp", timestamp);
        stats.put("totalAccounts", totalAccounts);
        stats.put("totalTokens", totalTokens);
        stats.put("totalValueLocked", totalValueLocked.toString());
        stats.put("totalSettlements", totalSettlements);
        stats.put("totalSettlementVolume", totalSettlementVolume.toString());
        stats.put("activeValidators", activeValidators.size());
        stats.put("type", type.toString());
        stats.put("isPrunable", isPrunable);
        
        // Shard statistics
        Map<Integer, Map<String, Object>> shardStats = new HashMap<>();
        for (Map.Entry<Integer, ShardCheckpoint> entry : shardCheckpoints.entrySet()) {
            ShardCheckpoint sc = entry.getValue();
            Map<String, Object> shardData = new HashMap<>();
            shardData.put("blockHeight", sc.getBlockHeight());
            shardData.put("transactionCount", sc.getTransactionCount());
            shardData.put("averageTPS", String.format("%.2f", sc.getAverageTPS()));
            shardStats.put(entry.getKey(), shardData);
        }
        stats.put("shardCheckpoints", shardStats);
        
        // ASU basket
        stats.put("asuBasketWeights", new HashMap<>(asuBasketWeights));
        
        // Top tokens by reserve
        List<Map.Entry<String, BigInteger>> topTokens = new ArrayList<>(proofOfReserves.entrySet());
        topTokens.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        Map<String, String> topReserves = new HashMap<>();
        for (int i = 0; i < Math.min(5, topTokens.size()); i++) {
            Map.Entry<String, BigInteger> entry = topTokens.get(i);
            topReserves.put(entry.getKey(), entry.getValue().toString());
        }
        stats.put("topReserves", topReserves);
        
        return stats;
    }
    
    @Override
    public String toString() {
        return String.format("CheckpointState{block=%d, accounts=%d, tokens=%d, TVL=%s, type=%s, age=%ds}",
            blockNumber, totalAccounts, totalTokens, totalValueLocked, type, getAge());
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CheckpointState that = (CheckpointState) o;
        return blockNumber == that.blockNumber && checkpointHash.equals(that.checkpointHash);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(blockNumber, checkpointHash);
    }
}