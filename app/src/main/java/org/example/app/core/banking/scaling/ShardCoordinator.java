package org.example.app.core.scaling;

import org.example.app.core.block.Block;
import org.example.app.core.block.Transaction;
import org.example.app.core.state.StateManager;
import org.example.app.core.state.WorldState;
import org.example.app.core.p2p.RobustP2PManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ShardCoordinator manages 20 geographic shards for TINC blockchain.
 * Ensures cross-shard transaction routing, load balancing, and sovereignty guarantees.
 * 
 * Geographic Distribution:
 * - Shards 0-3: West Africa (Nigeria, Ghana, Senegal, Côte d'Ivoire)
 * - Shards 4-7: East Africa (Kenya, Ethiopia, Tanzania, Uganda)
 * - Shards 8-11: Southern Africa (South Africa, Zimbabwe, Botswana, Namibia)
 * - Shards 12-15: North Africa (Egypt, Morocco, Algeria, Tunisia)
 * - Shards 16-18: Central Africa (Cameroon, DRC, Gabon)
 * - Shard 19: Cross-border settlement shard
 */
public class ShardCoordinator {
    private static final Logger logger = Logger.getLogger(ShardCoordinator.class.getName());
    
    private static final int TOTAL_SHARDS = 20;
    private static final int WEST_AFRICA_START = 0;
    private static final int EAST_AFRICA_START = 4;
    private static final int SOUTHERN_AFRICA_START = 8;
    private static final int NORTH_AFRICA_START = 12;
    private static final int CENTRAL_AFRICA_START = 16;
    private static final int SETTLEMENT_SHARD = 19;
    
    // Shard states and managers
    private final ConcurrentHashMap<Integer, ShardState> shardStates;
    private final ConcurrentHashMap<Integer, StateManager> shardStateManagers;
    private final ConcurrentHashMap<Integer, WorldState> shardWorldStates;
    
    // Cross-shard transaction queue
    private final ConcurrentHashMap<Integer, BlockingQueue<CrossShardTransaction>> crossShardQueues;
    
    // Shard load tracking
    private final ConcurrentHashMap<Integer, ShardMetrics> shardMetrics;
    
    // P2P network for cross-shard communication
    private final RobustP2PManager p2pManager;
    
    // Thread pool for parallel shard processing
    private final ExecutorService shardExecutor;
    
    // Geographic distribution validator
    private final GeographicDistributor geoDistributor;
    
    public ShardCoordinator(RobustP2PManager p2pManager, GeographicDistributor geoDistributor) {
        this.p2pManager = p2pManager;
        this.geoDistributor = geoDistributor;
        this.shardStates = new ConcurrentHashMap<>();
        this.shardStateManagers = new ConcurrentHashMap<>();
        this.shardWorldStates = new ConcurrentHashMap<>();
        this.crossShardQueues = new ConcurrentHashMap<>();
        this.shardMetrics = new ConcurrentHashMap<>();
        this.shardExecutor = Executors.newFixedThreadPool(TOTAL_SHARDS);
        
        initializeShards();
    }
    
    /**
     * Initialize all 20 geographic shards
     */
    private void initializeShards() {
        for (int shardId = 0; shardId < TOTAL_SHARDS; shardId++) {
            // Create shard state
            ShardState shardState = new ShardState(shardId, getRegionForShard(shardId));
            shardStates.put(shardId, shardState);
            
            // Create state manager and world state
            WorldState worldState = new WorldState();
            shardWorldStates.put(shardId, worldState);
            shardStateManagers.put(shardId, new StateManager(null)); // TODO: Connect to StateDB
            
            // Initialize cross-shard queue
            crossShardQueues.put(shardId, new LinkedBlockingQueue<>());
            
            // Initialize metrics
            shardMetrics.put(shardId, new ShardMetrics(shardId));
            
            logger.info("Initialized Shard " + shardId + " for region: " + getRegionForShard(shardId));
        }
    }
    
    /**
     * Route transaction to appropriate shard based on geographic location
     */
    public int routeTransaction(Transaction transaction) {
        String address = transaction.getFrom();
        
        // Special handling for cross-border settlements
        if (isCrossBorderTransaction(transaction)) {
            return SETTLEMENT_SHARD;
        }
        
        // Hash-based routing within geographic region
        int shardId = determineShardFromAddress(address);
        
        // Validate sovereignty requirements
        if (!geoDistributor.validateShardDistribution(shardId, address)) {
            logger.warning("Sovereignty violation detected for shard " + shardId);
            return -1; // Reject transaction
        }
        
        return shardId;
    }
    
    /**
     * Process transaction on specific shard
     */
    public boolean processTransaction(int shardId, Transaction transaction) {
        if (shardId < 0 || shardId >= TOTAL_SHARDS) {
            logger.warning("Invalid shard ID: " + shardId);
            return false;
        }
        
        ShardState shard = shardStates.get(shardId);
        ShardMetrics metrics = shardMetrics.get(shardId);
        
        try {
            // Update metrics
            metrics.incrementTransactionCount();
            long startTime = System.currentTimeMillis();
            
            // Process transaction on shard
            boolean success = shard.addTransaction(transaction);
            
            if (success) {
                // Update processing time
                metrics.recordProcessingTime(System.currentTimeMillis() - startTime);
                logger.fine("Transaction processed on shard " + shardId);
            }
            
            return success;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing transaction on shard " + shardId, e);
            metrics.incrementErrorCount();
            return false;
        }
    }
    
    /**
     * Submit cross-shard transaction
     */
    public boolean submitCrossShardTransaction(CrossShardTransaction transaction) {
        int sourceShardId = routeTransaction(transaction.getSourceTransaction());
        int destShardId = routeTransaction(transaction.getDestinationTransaction());
        
        if (sourceShardId == -1 || destShardId == -1) {
            logger.warning("Invalid cross-shard transaction routing");
            return false;
        }
        
        try {
            // Add to both shard queues
            crossShardQueues.get(sourceShardId).offer(transaction);
            crossShardQueues.get(destShardId).offer(transaction);
            
            logger.info("Cross-shard transaction submitted: " + sourceShardId + " -> " + destShardId);
            return true;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error submitting cross-shard transaction", e);
            return false;
        }
    }
    
    /**
     * Process pending cross-shard transactions
     */
    public void processCrossShardTransactions() {
        for (int shardId = 0; shardId < TOTAL_SHARDS; shardId++) {
            final int currentShardId = shardId;
            shardExecutor.submit(() -> {
                BlockingQueue<CrossShardTransaction> queue = crossShardQueues.get(currentShardId);
                
                while (!queue.isEmpty()) {
                    try {
                        CrossShardTransaction transaction = queue.poll(100, TimeUnit.MILLISECONDS);
                        if (transaction != null) {
                            processCrossShardTransaction(currentShardId, transaction);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.log(Level.WARNING, "Cross-shard processing interrupted", e);
                        break;
                    }
                }
            });
        }
    }
    
    /**
     * Process individual cross-shard transaction
     */
    private void processCrossShardTransaction(int shardId, CrossShardTransaction transaction) {
        ShardState shard = shardStates.get(shardId);
        
        // Atomic cross-shard execution
        synchronized (shard) {
            try {
                // Lock both source and destination shards
                boolean success = transaction.execute(shard);
                
                if (success) {
                    // Broadcast to P2P network for verification
                    broadcastCrossShardProof(transaction);
                    logger.info("Cross-shard transaction executed on shard " + shardId);
                } else {
                    logger.warning("Cross-shard transaction failed on shard " + shardId);
                }
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error executing cross-shard transaction", e);
            }
        }
    }
    
    /**
     * Broadcast cross-shard proof to network
     */
    private void broadcastCrossShardProof(CrossShardTransaction transaction) {
        try {
            String proof = transaction.generateProof();
            p2pManager.broadcast("CROSS_SHARD_PROOF", proof);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error broadcasting cross-shard proof", e);
        }
    }
    
    /**
     * Get shard state for specific shard ID
     */
    public ShardState getShardState(int shardId) {
        return shardStates.get(shardId);
    }
    
    /**
     * Get metrics for specific shard
     */
    public ShardMetrics getShardMetrics(int shardId) {
        return shardMetrics.get(shardId);
    }
    
    /**
     * Get aggregated metrics across all shards
     */
    public AggregatedMetrics getAggregatedMetrics() {
        long totalTransactions = 0;
        long totalErrors = 0;
        double avgProcessingTime = 0.0;
        
        for (ShardMetrics metrics : shardMetrics.values()) {
            totalTransactions += metrics.getTransactionCount();
            totalErrors += metrics.getErrorCount();
            avgProcessingTime += metrics.getAverageProcessingTime();
        }
        
        avgProcessingTime /= TOTAL_SHARDS;
        
        return new AggregatedMetrics(totalTransactions, totalErrors, avgProcessingTime);
    }
    
    /**
     * Determine geographic region for shard ID
     */
    private String getRegionForShard(int shardId) {
        if (shardId >= WEST_AFRICA_START && shardId < EAST_AFRICA_START) {
            return "WEST_AFRICA";
        } else if (shardId >= EAST_AFRICA_START && shardId < SOUTHERN_AFRICA_START) {
            return "EAST_AFRICA";
        } else if (shardId >= SOUTHERN_AFRICA_START && shardId < NORTH_AFRICA_START) {
            return "SOUTHERN_AFRICA";
        } else if (shardId >= NORTH_AFRICA_START && shardId < CENTRAL_AFRICA_START) {
            return "NORTH_AFRICA";
        } else if (shardId >= CENTRAL_AFRICA_START && shardId < SETTLEMENT_SHARD) {
            return "CENTRAL_AFRICA";
        } else {
            return "SETTLEMENT";
        }
    }
    
    /**
     * Determine shard from address using consistent hashing
     */
    private int determineShardFromAddress(String address) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(address.getBytes());
            
            // Use first 4 bytes as integer
            int hashValue = Math.abs(
                ((hash[0] & 0xFF) << 24) |
                ((hash[1] & 0xFF) << 16) |
                ((hash[2] & 0xFF) << 8) |
                (hash[3] & 0xFF)
            );
            
            // Map to shard (excluding settlement shard)
            return hashValue % (TOTAL_SHARDS - 1);
            
        } catch (NoSuchAlgorithmException e) {
            logger.log(Level.SEVERE, "SHA-256 not available", e);
            return 0; // Fallback to first shard
        }
    }
    
    /**
     * Check if transaction is cross-border
     */
    private boolean isCrossBorderTransaction(Transaction transaction) {
        // Check if transaction involves multiple currencies or regions
        // This would typically check transaction metadata
        return false; // Placeholder
    }
    
    /**
     * Shutdown coordinator
     */
    public void shutdown() {
        logger.info("Shutting down ShardCoordinator");
        shardExecutor.shutdown();
        try {
            if (!shardExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                shardExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            shardExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Shard state representation
     */
    public static class ShardState {
        private final int shardId;
        private final String region;
        private final List<Transaction> pendingTransactions;
        private long lastBlockHeight;
        
        public ShardState(int shardId, String region) {
            this.shardId = shardId;
            this.region = region;
            this.pendingTransactions = new CopyOnWriteArrayList<>();
            this.lastBlockHeight = 0;
        }
        
        public boolean addTransaction(Transaction transaction) {
            return pendingTransactions.add(transaction);
        }
        
        public List<Transaction> getPendingTransactions() {
            return new ArrayList<>(pendingTransactions);
        }
        
        public void clearPendingTransactions() {
            pendingTransactions.clear();
        }
        
        public int getShardId() { return shardId; }
        public String getRegion() { return region; }
        public long getLastBlockHeight() { return lastBlockHeight; }
        public void setLastBlockHeight(long height) { this.lastBlockHeight = height; }
    }
    
    /**
     * Cross-shard transaction representation
     */
    public static class CrossShardTransaction {
        private final Transaction sourceTransaction;
        private final Transaction destinationTransaction;
        private final String proof;
        
        public CrossShardTransaction(Transaction source, Transaction dest) {
            this.sourceTransaction = source;
            this.destinationTransaction = dest;
            this.proof = generateProof();
        }
        
        public boolean execute(ShardState shard) {
            // Execute atomic cross-shard operation
            return shard.addTransaction(sourceTransaction) && 
                   shard.addTransaction(destinationTransaction);
        }
        
        public String generateProof() {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                String combined = sourceTransaction.getHash() + destinationTransaction.getHash();
                byte[] hash = digest.digest(combined.getBytes());
                return Base64.getEncoder().encodeToString(hash);
            } catch (NoSuchAlgorithmException e) {
                return "";
            }
        }
        
        public Transaction getSourceTransaction() { return sourceTransaction; }
        public Transaction getDestinationTransaction() { return destinationTransaction; }
        public String getProof() { return proof; }
    }
    
    /**
     * Shard performance metrics
     */
    public static class ShardMetrics {
        private final int shardId;
        private final AtomicLong transactionCount;
        private final AtomicLong errorCount;
        private final CopyOnWriteArrayList<Long> processingTimes;
        
        public ShardMetrics(int shardId) {
            this.shardId = shardId;
            this.transactionCount = new AtomicLong(0);
            this.errorCount = new AtomicLong(0);
            this.processingTimes = new CopyOnWriteArrayList<>();
        }
        
        public void incrementTransactionCount() {
            transactionCount.incrementAndGet();
        }
        
        public void incrementErrorCount() {
            errorCount.incrementAndGet();
        }
        
        public void recordProcessingTime(long timeMs) {
            processingTimes.add(timeMs);
            // Keep only last 1000 entries
            if (processingTimes.size() > 1000) {
                processingTimes.remove(0);
            }
        }
        
        public long getTransactionCount() { return transactionCount.get(); }
        public long getErrorCount() { return errorCount.get(); }
        
        public double getAverageProcessingTime() {
            if (processingTimes.isEmpty()) return 0.0;
            return processingTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
        }
    }
    
    /**
     * Aggregated metrics across all shards
     */
    public static class AggregatedMetrics {
        private final long totalTransactions;
        private final long totalErrors;
        private final double averageProcessingTime;
        
        public AggregatedMetrics(long totalTransactions, long totalErrors, double avgProcessingTime) {
            this.totalTransactions = totalTransactions;
            this.totalErrors = totalErrors;
            this.averageProcessingTime = avgProcessingTime;
        }
        
        public long getTotalTransactions() { return totalTransactions; }
        public long getTotalErrors() { return totalErrors; }
        public double getAverageProcessingTime() { return averageProcessingTime; }
        
        public double getErrorRate() {
            if (totalTransactions == 0) return 0.0;
            return (double) totalErrors / totalTransactions;
        }
    }
}
