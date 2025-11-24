package org.example.app.core.blockchain.block;

import org.example.app.core.block.Block;
import org.example.app.core.blockchain.transaction.*;
import org.example.app.core.types.Address;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Enhanced BlockchainState for African Tokenized Currency System
 * Manages multi-shard state, settlement records, and Layer 2 integration
 * 
 * Supports:
 * - Geographic sharding (20 shards for 100,000 TPS continent-wide)
 * - Settlement tracking for inter-bank and ASU transactions
 * - Layer 2 rollup state anchoring
 * - Checkpoint state for pruning (target: <100GB/year per node)
 */
public class BlockchainState {
    private static final Logger logger = Logger.getLogger(BlockchainState.class.getName());
    
    // Core blockchain state
    private final Map<String, Block> blocks;
    private final Map<String, SettlementRecord> settlements;
    private final Map<Integer, ShardState> shardStates;
    private final Map<Long, CheckpointState> checkpoints;
    private final Map<Long, RollupProof> rollupProofs;
    
    // State metadata
    private Block latestBlock;
    private long latestBlockNumber;
    private byte[] latestStateRoot;
    
    // Token balances and reserves
    private final Map<String, Map<String, BigInteger>> tokenBalances; // address -> (token -> balance)
    private final Map<String, BigInteger> proofOfReserves; // token -> reserve amount
    
    // ASU (African Stable Unit) basket weights
    private final Map<String, Double> asuBasketWeights; // currency -> weight
    
    // Sharding configuration
    private final int totalShards;
    private final Map<Integer, String> shardRegions; // shardId -> region name
    
    // Thread safety
    private final ReadWriteLock stateLock;
    private final ReadWriteLock shardLock;
    private final ReadWriteLock settlementLock;
    
    // Pruning configuration
    private final long checkpointInterval; // Create checkpoint every N blocks
    private final long pruningThreshold; // Prune blocks older than N checkpoints
    private long lastCheckpointBlock;
    
    /**
     * Creates a new BlockchainState with default configuration
     */
    public BlockchainState() {
        this(20, 100); // 20 shards, checkpoint every 100 blocks
    }
    
    /**
     * Creates a new BlockchainState with specified configuration
     * 
     * @param totalShards Total number of shards in the system
     * @param checkpointInterval Blocks between checkpoints
     */
    public BlockchainState(int totalShards, long checkpointInterval) {
        this.blocks = new ConcurrentHashMap<>();
        this.settlements = new ConcurrentHashMap<>();
        this.shardStates = new ConcurrentHashMap<>();
        this.checkpoints = new ConcurrentHashMap<>();
        this.rollupProofs = new ConcurrentHashMap<>();
        
        this.tokenBalances = new ConcurrentHashMap<>();
        this.proofOfReserves = new ConcurrentHashMap<>();
        this.asuBasketWeights = new ConcurrentHashMap<>();
        
        this.totalShards = totalShards;
        this.shardRegions = new ConcurrentHashMap<>();
        
        this.stateLock = new ReentrantReadWriteLock();
        this.shardLock = new ReentrantReadWriteLock();
        this.settlementLock = new ReentrantReadWriteLock();
        
        this.checkpointInterval = checkpointInterval;
        this.pruningThreshold = 10; // Keep 10 checkpoints
        this.lastCheckpointBlock = 0;
        
        this.latestBlockNumber = -1;
        this.latestStateRoot = new byte[32];
        
        initializeShardRegions();
        initializeASUBasket();
        
        logger.info("BlockchainState initialized with " + totalShards + " shards");
    }
    
    /**
     * Initializes geographic shard regions
     */
    private void initializeShardRegions() {
        // Geographic shards (0-19)
        shardRegions.put(0, "WEST_AFRICA_1");
        shardRegions.put(1, "WEST_AFRICA_2");
        shardRegions.put(2, "WEST_AFRICA_3");
        shardRegions.put(3, "WEST_AFRICA_4");
        shardRegions.put(4, "WEST_AFRICA_5");
        
        shardRegions.put(5, "EAST_AFRICA_1");
        shardRegions.put(6, "EAST_AFRICA_2");
        shardRegions.put(7, "EAST_AFRICA_3");
        shardRegions.put(8, "EAST_AFRICA_4");
        shardRegions.put(9, "EAST_AFRICA_5");
        
        shardRegions.put(10, "CENTRAL_AFRICA_1");
        shardRegions.put(11, "CENTRAL_AFRICA_2");
        shardRegions.put(12, "CENTRAL_AFRICA_3");
        shardRegions.put(13, "CENTRAL_AFRICA_4");
        
        shardRegions.put(14, "SOUTHERN_AFRICA_1");
        shardRegions.put(15, "SOUTHERN_AFRICA_2");
        shardRegions.put(16, "SOUTHERN_AFRICA_3");
        shardRegions.put(17, "SOUTHERN_AFRICA_4");
        
        shardRegions.put(18, "NORTH_AFRICA_1");
        shardRegions.put(19, "NORTH_AFRICA_2");
        
        // Initialize shard states
        for (int i = 0; i < totalShards; i++) {
            shardStates.put(i, new ShardState(i, shardRegions.getOrDefault(i, "FUNCTIONAL_" + i)));
        }
    }
    
    /**
     * Initializes ASU basket with African currency weights
     */
    private void initializeASUBasket() {
        // Initial ASU basket composition (can be updated via governance)
        asuBasketWeights.put("NGN", 0.25); // Nigerian Naira - 25%
        asuBasketWeights.put("ZAR", 0.20); // South African Rand - 20%
        asuBasketWeights.put("EGP", 0.15); // Egyptian Pound - 15%
        asuBasketWeights.put("KES", 0.10); // Kenyan Shilling - 10%
        asuBasketWeights.put("GHS", 0.08); // Ghanaian Cedi - 8%
        asuBasketWeights.put("MAD", 0.07); // Moroccan Dirham - 7%
        asuBasketWeights.put("TZS", 0.05); // Tanzanian Shilling - 5%
        asuBasketWeights.put("UGX", 0.05); // Ugandan Shilling - 5%
        asuBasketWeights.put("ETB", 0.05); // Ethiopian Birr - 5%
    }
    
    /**
     * Adds a block to the blockchain state
     */
    public void addBlock(String blockHash, Block block) {
        if (blockHash == null || block == null) {
            throw new IllegalArgumentException("Block hash and block cannot be null");
        }
        
        stateLock.writeLock().lock();
        try {
            blocks.put(blockHash, block);
            
            // Update latest block
            if (block.getNumber() > latestBlockNumber) {
                latestBlock = block;
                latestBlockNumber = block.getNumber();
                latestStateRoot = block.getStateRoot();
            }
            
            // Process block transactions for settlements
            if (block.getTransactions() != null) {
                processBlockTransactions(block);
            }
            
            // Check if checkpoint needed
            if (shouldCreateCheckpoint()) {
                createCheckpoint(block.getNumber());
            }
            
            logger.fine("Added block: " + blockHash + " at height " + block.getNumber());
            
        } finally {
            stateLock.writeLock().unlock();
        }
    }
    
    /**
     * Gets a block by its hash
     */
    public Block getBlock(String blockHash) {
        if (blockHash == null) {
            return null;
        }
        
        stateLock.readLock().lock();
        try {
            return blocks.get(blockHash);
        } finally {
            stateLock.readLock().unlock();
        }
    }
    
    /**
     * Gets the total number of blocks
     */
    public int getBlockCount() {
        stateLock.readLock().lock();
        try {
            return blocks.size();
        } finally {
            stateLock.readLock().unlock();
        }
    }
    
    /**
     * Removes a block by its hash (used during pruning)
     */
    public void removeBlock(String blockHash) {
        if (blockHash == null) {
            return;
        }
        
        stateLock.writeLock().lock();
        try {
            blocks.remove(blockHash);
            logger.fine("Removed block: " + blockHash);
        } finally {
            stateLock.writeLock().unlock();
        }
    }
    
    /**
     * Checks if a block exists
     */
    public boolean hasBlock(String blockHash) {
        if (blockHash == null) {
            return false;
        }
        
        stateLock.readLock().lock();
        try {
            return blocks.containsKey(blockHash);
        } finally {
            stateLock.readLock().unlock();
        }
    }
    
    /**
     * Gets the latest block
     */
    public Block getLatestBlock() {
        stateLock.readLock().lock();
        try {
            return latestBlock;
        } finally {
            stateLock.readLock().unlock();
        }
    }
    
    /**
     * Gets the latest block number
     */
    public long getLatestBlockNumber() {
        stateLock.readLock().lock();
        try {
            return latestBlockNumber;
        } finally {
            stateLock.readLock().unlock();
        }
    }
    
    /**
     * Gets the latest state root
     */
    public byte[] getLatestStateRoot() {
        stateLock.readLock().lock();
        try {
            return Arrays.copyOf(latestStateRoot, latestStateRoot.length);
        } finally {
            stateLock.readLock().unlock();
        }
    }
    
    /**
     * Adds a settlement record
     */
    public void addSettlement(SettlementRecord settlement) {
        if (settlement == null) {
            throw new IllegalArgumentException("Settlement cannot be null");
        }
        
        settlementLock.writeLock().lock();
        try {
            settlements.put(settlement.getSettlementId(), settlement);
            logger.fine("Added settlement: " + settlement.getSettlementId());
        } finally {
            settlementLock.writeLock().unlock();
        }
    }
    
    /**
     * Gets a settlement record by ID
     */
    public SettlementRecord getSettlement(String settlementId) {
        if (settlementId == null) {
            return null;
        }
        
        settlementLock.readLock().lock();
        try {
            return settlements.get(settlementId);
        } finally {
            settlementLock.readLock().unlock();
        }
    }
    
    /**
     * Gets all settlements for a specific bank
     */
    public List<SettlementRecord> getBankSettlements(String bankId) {
        settlementLock.readLock().lock();
        try {
            List<SettlementRecord> bankSettlements = new ArrayList<>();
            for (SettlementRecord settlement : settlements.values()) {
                if (settlement.involvesBank(bankId)) {
                    bankSettlements.add(settlement);
                }
            }
            return bankSettlements;
        } finally {
            settlementLock.readLock().unlock();
        }
    }
    
    /**
     * Gets shard state by shard ID
     */
    public ShardState getShardState(int shardId) {
        if (shardId < 0 || shardId >= totalShards) {
            throw new IllegalArgumentException("Invalid shard ID: " + shardId);
        }
        
        shardLock.readLock().lock();
        try {
            return shardStates.get(shardId);
        } finally {
            shardLock.readLock().unlock();
        }
    }
    
    /**
     * Updates shard state
     */
    public void updateShardState(int shardId, ShardState newState) {
        if (shardId < 0 || shardId >= totalShards) {
            throw new IllegalArgumentException("Invalid shard ID: " + shardId);
        }
        if (newState == null) {
            throw new IllegalArgumentException("Shard state cannot be null");
        }
        
        shardLock.writeLock().lock();
        try {
            shardStates.put(shardId, newState);
            logger.fine("Updated shard state for shard: " + shardId);
        } finally {
            shardLock.writeLock().unlock();
        }
    }
    
    /**
     * Adds a rollup proof
     */
    public void addRollupProof(long l2BlockNumber, RollupProof proof) {
        if (proof == null) {
            throw new IllegalArgumentException("Rollup proof cannot be null");
        }
        
        stateLock.writeLock().lock();
        try {
            rollupProofs.put(l2BlockNumber, proof);
            logger.fine("Added rollup proof for L2 block: " + l2BlockNumber);
        } finally {
            stateLock.writeLock().unlock();
        }
    }
    
    /**
     * Gets a rollup proof
     */
    public RollupProof getRollupProof(long l2BlockNumber) {
        stateLock.readLock().lock();
        try {
            return rollupProofs.get(l2BlockNumber);
        } finally {
            stateLock.readLock().unlock();
        }
    }
    
    /**
     * Creates a checkpoint at the specified block height
     */
    private void createCheckpoint(long blockNumber) {
        stateLock.writeLock().lock();
        try {
            CheckpointState checkpoint = new CheckpointState(
                blockNumber,
                latestStateRoot,
                new HashMap<>(tokenBalances),
                new HashMap<>(proofOfReserves),
                new HashMap<>(asuBasketWeights)
            );
            
            checkpoints.put(blockNumber, checkpoint);
            lastCheckpointBlock = blockNumber;
            
            logger.info("Created checkpoint at block: " + blockNumber);
            
            // Prune old blocks if needed
            pruneOldBlocks(blockNumber);
            
        } finally {
            stateLock.writeLock().unlock();
        }
    }
    
    /**
     * Checks if a checkpoint should be created
     */
    private boolean shouldCreateCheckpoint() {
        return (latestBlockNumber - lastCheckpointBlock) >= checkpointInterval;
    }
    
    /**
     * Prunes old blocks to keep storage under control
     */
    private void pruneOldBlocks(long currentBlock) {
        long pruneBeforeBlock = currentBlock - (checkpointInterval * pruningThreshold);
        
        if (pruneBeforeBlock <= 0) {
            return; // Nothing to prune yet
        }
        
        int prunedCount = 0;
        Iterator<Map.Entry<String, Block>> iterator = blocks.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, Block> entry = iterator.next();
            Block block = entry.getValue();
            
            if (block.getNumber() < pruneBeforeBlock) {
                // Keep checkpoint blocks
                if (!checkpoints.containsKey(block.getNumber())) {
                    iterator.remove();
                    prunedCount++;
                }
            }
        }
        
        if (prunedCount > 0) {
            logger.info("Pruned " + prunedCount + " old blocks before height " + pruneBeforeBlock);
        }
    }
    
    /**
     * Gets a checkpoint by block number
     */
    public CheckpointState getCheckpoint(long blockNumber) {
        stateLock.readLock().lock();
        try {
            return checkpoints.get(blockNumber);
        } finally {
            stateLock.readLock().unlock();
        }
    }
    
    /**
     * Gets the latest checkpoint
     */
    public CheckpointState getLatestCheckpoint() {
        stateLock.readLock().lock();
        try {
            if (checkpoints.isEmpty()) {
                return null;
            }
            return checkpoints.get(lastCheckpointBlock);
        } finally {
            stateLock.readLock().unlock();
        }
    }
    
    /**
     * Processes transactions in a block for settlements and state updates
     */
    private void processBlockTransactions(Block block) {
        if (block.getTransactions() == null) {
            return;
        }
        
        for (Object tx : block.getTransactions()) {
            if (tx instanceof BankSettlementTransaction) {
                processSettlement((BankSettlementTransaction) tx);
            } else if (tx instanceof RollupBatchTransaction) {
                processRollupBatch((RollupBatchTransaction) tx);
            } else if (tx instanceof CrossShardTransaction) {
                processCrossShardTransaction((CrossShardTransaction) tx);
            }
        }
    }
    
    /**
     * Processes a bank settlement transaction
     */
    private void processSettlement(BankSettlementTransaction tx) {
        // Create settlement record
        SettlementRecord record = new SettlementRecord(
            UUID.randomUUID().toString(),
            tx.getSenderAddress().toString(),
            tx.getRecipientAddress().toString(),
            tx.getAmount().getValue(),
            tx.getAmount().getCurrency(),
            System.currentTimeMillis(),
            SettlementRecord.SettlementStatus.COMPLETED
        );
        
        addSettlement(record);
    }
    
    /**
     * Processes a rollup batch transaction
     */
    private void processRollupBatch(RollupBatchTransaction tx) {
        // Create rollup proof
        RollupProof proof = new RollupProof(
            tx.getL2BlockNumber(),
            tx.getBatchId(),
            tx.getStateRoot(),
            tx.getValidityProof(),
            tx.getTransactionCount(),
            latestBlockNumber
        );
        
        addRollupProof(tx.getL2BlockNumber(), proof);
    }
    
    /**
     * Processes a cross-shard transaction
     */
    private void processCrossShardTransaction(CrossShardTransaction tx) {
        // Update source and destination shard states
        ShardState sourceState = getShardState(tx.getSourceShard());
        ShardState destState = getShardState(tx.getDestinationShard());
        
        if (sourceState != null && destState != null) {
            sourceState.incrementCrossShardTxCount();
            destState.incrementCrossShardTxCount();
        }
    }
    
    /**
     * Updates ASU basket weights (governance operation)
     */
    public void updateASUBasketWeights(Map<String, Double> newWeights) {
        if (newWeights == null) {
            throw new IllegalArgumentException("Weights cannot be null");
        }
        
        // Validate total weight = 1.0
        double totalWeight = newWeights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(totalWeight - 1.0) > 0.001) {
            throw new IllegalArgumentException("Total ASU basket weight must equal 1.0, got: " + totalWeight);
        }
        
        stateLock.writeLock().lock();
        try {
            asuBasketWeights.clear();
            asuBasketWeights.putAll(newWeights);
            logger.info("Updated ASU basket weights");
        } finally {
            stateLock.writeLock().unlock();
        }
    }
    
    /**
     * Gets current ASU basket weights
     */
    public Map<String, Double> getASUBasketWeights() {
        stateLock.readLock().lock();
        try {
            return new HashMap<>(asuBasketWeights);
        } finally {
            stateLock.readLock().unlock();
        }
    }
    
    /**
     * Updates proof of reserves for a token
     */
    public void updateProofOfReserves(String token, BigInteger reserveAmount) {
        if (token == null || reserveAmount == null) {
            throw new IllegalArgumentException("Token and reserve amount cannot be null");
        }
        
        stateLock.writeLock().lock();
        try {
            proofOfReserves.put(token, reserveAmount);
            logger.fine("Updated proof of reserves for " + token + ": " + reserveAmount);
        } finally {
            stateLock.writeLock().unlock();
        }
    }
    
    /**
     * Gets proof of reserves for a token
     */
    public BigInteger getProofOfReserves(String token) {
        stateLock.readLock().lock();
        try {
            return proofOfReserves.getOrDefault(token, BigInteger.ZERO);
        } finally {
            stateLock.readLock().unlock();
        }
    }
    
    /**
     * Clears all blocks (use with caution)
     */
    public void clear() {
        stateLock.writeLock().lock();
        settlementLock.writeLock().lock();
        shardLock.writeLock().lock();
        try {
            blocks.clear();
            settlements.clear();
            rollupProofs.clear();
            latestBlock = null;
            latestBlockNumber = -1;
            logger.info("Cleared blockchain state");
        } finally {
            shardLock.writeLock().unlock();
            settlementLock.writeLock().unlock();
            stateLock.writeLock().unlock();
        }
    }
    
    /**
     * Gets blockchain statistics
     */
    public Map<String, Object> getStatistics() {
        stateLock.readLock().lock();
        settlementLock.readLock().lock();
        shardLock.readLock().lock();
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalBlocks", blocks.size());
            stats.put("latestBlockNumber", latestBlockNumber);
            stats.put("totalSettlements", settlements.size());
            stats.put("totalShards", totalShards);
            stats.put("totalCheckpoints", checkpoints.size());
            stats.put("totalRollupProofs", rollupProofs.size());
            stats.put("activeTokens", proofOfReserves.size());
            return stats;
        } finally {
            shardLock.readLock().unlock();
            settlementLock.readLock().unlock();
            stateLock.readLock().unlock();
        }
    }
}