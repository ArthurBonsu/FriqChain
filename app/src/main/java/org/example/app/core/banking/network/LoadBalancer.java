package org.example.app.core.banking.network;

import java.util.stream.Collectors;

import org.example.app.core.blockchain.block.ShardState;
import org.example.app.core.blockchain.transaction.CrossShardTransaction;
import org.example.app.core.types.Address;

import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LoadBalancer distributes transactions across shards to ensure balanced
 * utilization and optimal performance across the African Tokenized Currency System.
 * 
 * Key Features:
 * - Dynamic shard selection based on load
 * - Account affinity (keep related accounts on same shard)
 * - Geographic optimization for latency
 * - Automatic load redistribution
 * Target: 100,000 TPS across 20 shards (5,000 TPS each)
 */
public class LoadBalancer {
    private static final Logger logger = Logger.getLogger(LoadBalancer.class.getName());
    
    // Configuration
    private static final double TARGET_UTILIZATION = 0.75; // 75% target utilization
    private static final double MAX_UTILIZATION = 0.90; // 90% max before redistribution
    private static final long REBALANCE_INTERVAL_MS = 30000; // 30 seconds
    
    // Load tracking
    private final Map<Integer, ShardLoad> shardLoads;
    private final Map<String, Integer> accountShardMapping; // account → shard ID
    private final ShardCoordinator shardCoordinator;
    
    // Transaction routing
    private final AtomicLong transactionsRouted;
    private final Map<Integer, AtomicLong> shardTransactionCounts;
    
    // Load balancing strategy
    private LoadBalancingStrategy strategy;
    private final ScheduledExecutorService rebalancer;
    
    /**
     * Shard load information
     */
    public static class ShardLoad {
        private final int shardId;
        private double currentLoad; // 0.0 to 1.0
        private long pendingTransactions;
        private double currentTPS;
        private long lastUpdate;
        
        public ShardLoad(int shardId) {
            this.shardId = shardId;
            this.currentLoad = 0.0;
            this.pendingTransactions = 0;
            this.currentTPS = 0.0;
            this.lastUpdate = System.currentTimeMillis();
        }
        
        public int getShardId() { return shardId; }
        public double getCurrentLoad() { return currentLoad; }
        public long getPendingTransactions() { return pendingTransactions; }
        public double getCurrentTPS() { return currentTPS; }
        public long getLastUpdate() { return lastUpdate; }
        
        public void updateLoad(double load) {
            this.currentLoad = Math.min(1.0, Math.max(0.0, load));
            this.lastUpdate = System.currentTimeMillis();
        }
        
        public void updatePendingTransactions(long count) {
            this.pendingTransactions = count;
            this.lastUpdate = System.currentTimeMillis();
        }
        
        public void updateTPS(double tps) {
            this.currentTPS = tps;
            this.lastUpdate = System.currentTimeMillis();
        }
    }
    
    /**
     * Load balancing strategy
     */
    public enum LoadBalancingStrategy {
        ROUND_ROBIN,        // Simple round-robin distribution
        LEAST_LOADED,       // Route to least loaded shard
        GEOGRAPHIC,         // Route based on geographic proximity
        ACCOUNT_AFFINITY,   // Keep related accounts together
        HYBRID              // Combination of strategies
    }
    
    /**
     * Constructor
     */
    public LoadBalancer(ShardCoordinator shardCoordinator) {
        if (shardCoordinator == null) {
            throw new IllegalArgumentException("ShardCoordinator cannot be null");
        }
        
        this.shardCoordinator = shardCoordinator;
        this.shardLoads = new ConcurrentHashMap<>();
        this.accountShardMapping = new ConcurrentHashMap<>();
        this.transactionsRouted = new AtomicLong(0);
        this.shardTransactionCounts = new ConcurrentHashMap<>();
        this.strategy = LoadBalancingStrategy.HYBRID;
        this.rebalancer = Executors.newScheduledThreadPool(1);
        
        logger.info("LoadBalancer initialized");
    }
    
    /**
     * Initializes load tracking for all shards
     */
    public void initializeShardLoads() {
        int shardCount = shardCoordinator.getShardCount();
        
        for (int shardId = 0; shardId < shardCount; shardId++) {
            shardLoads.put(shardId, new ShardLoad(shardId));
            shardTransactionCounts.put(shardId, new AtomicLong(0));
        }
        
        logger.info("Initialized load tracking for " + shardCount + " shards");
    }
    
    /**
     * Routes a transaction to the optimal shard
     */
    public int routeTransaction(String fromAddress, String toAddress) {
        if (fromAddress == null || toAddress == null) {
            throw new IllegalArgumentException("Addresses cannot be null");
        }
        
        int selectedShard;
        
        switch (strategy) {
            case ROUND_ROBIN:
                selectedShard = selectRoundRobin();
                break;
            case LEAST_LOADED:
                selectedShard = selectLeastLoaded();
                break;
            case GEOGRAPHIC:
                selectedShard = selectGeographic(fromAddress, toAddress);
                break;
            case ACCOUNT_AFFINITY:
                selectedShard = selectWithAffinity(fromAddress, toAddress);
                break;
            case HYBRID:
            default:
                selectedShard = selectHybrid(fromAddress, toAddress);
                break;
        }
        
        // Update routing statistics
        transactionsRouted.incrementAndGet();
        shardTransactionCounts.computeIfAbsent(selectedShard, k -> new AtomicLong(0))
            .incrementAndGet();
        
        // Update shard load
        updateShardLoad(selectedShard);
        
        logger.fine("Routed transaction to shard " + selectedShard);
        
        return selectedShard;
    }
    
    /**
     * Round-robin shard selection
     */
    private int selectRoundRobin() {
        List<Integer> availableShards = new ArrayList<>(shardLoads.keySet());
        if (availableShards.isEmpty()) {
            return 0;
        }
        
        int index = (int) (transactionsRouted.get() % availableShards.size());
        return availableShards.get(index);
    }
    
    /**
     * Selects the least loaded shard
     */
    private int selectLeastLoaded() {
        return shardLoads.entrySet().stream()
            .min(Comparator.comparingDouble(e -> e.getValue().getCurrentLoad()))
            .map(Map.Entry::getKey)
            .orElse(0);
    }
    
    /**
     * Selects shard based on geographic proximity
     */
    private int selectGeographic(String fromAddress, String toAddress) {
        // Extract geographic region from address
        String region = extractRegion(fromAddress);
        
        // Find shards in that region
        Set<Integer> regionShards = shardCoordinator.getShardsInRegion(region);
        
        if (regionShards.isEmpty()) {
            return selectLeastLoaded();
        }
        
        // Select least loaded shard in region
        return regionShards.stream()
            .min(Comparator.comparingDouble(shardId -> 
                shardLoads.getOrDefault(shardId, new ShardLoad(shardId)).getCurrentLoad()))
            .orElse(selectLeastLoaded());
    }
    
    /**
     * Selects shard with account affinity
     */
    private int selectWithAffinity(String fromAddress, String toAddress) {
        // Check if from address already has shard assignment
        Integer fromShard = accountShardMapping.get(fromAddress);
        Integer toShard = accountShardMapping.get(toAddress);
        
        // If both are on same shard, use that shard
        if (fromShard != null && fromShard.equals(toShard)) {
            return fromShard;
        }
        
        // If from address has assignment and shard not overloaded, use it
        if (fromShard != null) {
            ShardLoad load = shardLoads.get(fromShard);
            if (load != null && load.getCurrentLoad() < MAX_UTILIZATION) {
                // Assign to address to same shard
                accountShardMapping.put(toAddress, fromShard);
                return fromShard;
            }
        }
        
        // Otherwise, select least loaded and create affinity
        int selectedShard = selectLeastLoaded();
        accountShardMapping.put(fromAddress, selectedShard);
        accountShardMapping.put(toAddress, selectedShard);
        
        return selectedShard;
    }
    
    /**
     * Hybrid selection combining multiple strategies
     */
    private int selectHybrid(String fromAddress, String toAddress) {
        // First, try account affinity
        Integer existingShard = accountShardMapping.get(fromAddress);
        
        if (existingShard != null) {
            ShardLoad load = shardLoads.get(existingShard);
            
            // If existing shard is not overloaded, use it
            if (load != null && load.getCurrentLoad() < TARGET_UTILIZATION) {
                return existingShard;
            }
        }
        
        // Otherwise, use geographic + least loaded
        String region = extractRegion(fromAddress);
        Set<Integer> regionShards = shardCoordinator.getShardsInRegion(region);
        
        if (!regionShards.isEmpty()) {
            // Find least loaded shard in region
            Optional<Integer> optimalShard = regionShards.stream()
                .filter(shardId -> {
                    ShardLoad load = shardLoads.get(shardId);
                    return load != null && load.getCurrentLoad() < MAX_UTILIZATION;
                })
                .min(Comparator.comparingDouble(shardId -> 
                    shardLoads.get(shardId).getCurrentLoad()));
            
            if (optimalShard.isPresent()) {
                int shard = optimalShard.get();
                // Create affinity
                accountShardMapping.put(fromAddress, shard);
                accountShardMapping.put(toAddress, shard);
                return shard;
            }
        }
        
        // Fallback to global least loaded
        return selectLeastLoaded();
    }
    
    /**
     * Extracts geographic region from address
     */
    private String extractRegion(String address) {
        // In production, this would parse the address to determine region
        // For now, hash to distribute evenly
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(address.getBytes());
            
            int regionIndex = Math.abs(hash[0] % 5);
            String[] regions = {"WEST_AFRICA", "EAST_AFRICA", "CENTRAL_AFRICA", 
                               "SOUTHERN_AFRICA", "NORTH_AFRICA"};
            
            return regions[regionIndex];
            
        } catch (Exception e) {
            return "WEST_AFRICA"; // Default
        }
    }
    
    /**
     * Updates load information for a shard
     */
    public void updateShardLoad(int shardId) {
        ShardState shard = shardCoordinator.getShard(shardId);
        if (shard == null) {
            return;
        }
        
        ShardLoad load = shardLoads.computeIfAbsent(shardId, ShardLoad::new);
        
        // Update from shard state
        load.updateLoad(shard.getUtilization() / 100.0);
        load.updateTPS(shard.getCurrentTPS());
        
        // Calculate pending transactions estimate
        long txCount = shardTransactionCounts.getOrDefault(shardId, new AtomicLong(0)).get();
        load.updatePendingTransactions(txCount);
    }
    
    /**
     * Updates all shard loads
     */
    public void updateAllShardLoads() {
        for (int shardId : shardLoads.keySet()) {
            updateShardLoad(shardId);
        }
    }
    
    /**
     * Starts automatic rebalancing
     */
    public void startAutoRebalancing() {
        rebalancer.scheduleAtFixedRate(
            this::performRebalancing,
            REBALANCE_INTERVAL_MS,
            REBALANCE_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        logger.info("Auto-rebalancing started");
    }
    
    /**
     * Performs load rebalancing
     */
    private void performRebalancing() {
        try {
            updateAllShardLoads();
            
            // Find overloaded shards
            List<Integer> overloadedShards = shardLoads.entrySet().stream()
                .filter(e -> e.getValue().getCurrentLoad() > MAX_UTILIZATION)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            
            if (!overloadedShards.isEmpty()) {
                logger.warning("Detected " + overloadedShards.size() + " overloaded shards");
                
                // Find underloaded shards
                List<Integer> underloadedShards = shardLoads.entrySet().stream()
                    .filter(e -> e.getValue().getCurrentLoad() < TARGET_UTILIZATION)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
                
                if (!underloadedShards.isEmpty()) {
                    redistributeLoad(overloadedShards, underloadedShards);
                }
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during rebalancing", e);
        }
    }
    
    /**
     * Redistributes load from overloaded to underloaded shards
     */
    private void redistributeLoad(List<Integer> overloaded, List<Integer> underloaded) {
        logger.info("Redistributing load: " + overloaded.size() + 
            " overloaded → " + underloaded.size() + " underloaded shards");
        
        // In production, this would:
        // 1. Select accounts to migrate
        // 2. Initiate state transfer
        // 3. Update account mappings
        // 4. Verify migration success
        
        // For now, just update mappings for new transactions
        for (Integer underloadedShard : underloaded) {
            logger.fine("Shard " + underloadedShard + " available for load redistribution");
        }
    }
    
    /**
     * Sets load balancing strategy
     */
    public void setStrategy(LoadBalancingStrategy strategy) {
        this.strategy = strategy;
        logger.info("Load balancing strategy set to: " + strategy);
    }
    
    /**
     * Gets current strategy
     */
    public LoadBalancingStrategy getStrategy() {
        return strategy;
    }
    
    /**
     * Gets shard load
     */
    public ShardLoad getShardLoad(int shardId) {
        return shardLoads.get(shardId);
    }
    
    /**
     * Gets all shard loads
     */
    public Map<Integer, ShardLoad> getAllShardLoads() {
        return new HashMap<>(shardLoads);
    }
    
    /**
     * Gets total transactions routed
     */
    public long getTotalTransactionsRouted() {
        return transactionsRouted.get();
    }
    
    /**
     * Gets shard for an account
     */
    public Integer getAccountShard(String address) {
        return accountShardMapping.get(address);
    }
    
    /**
     * Gets load balancer statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("strategy", strategy.toString());
        stats.put("totalTransactionsRouted", transactionsRouted.get());
        stats.put("totalShards", shardLoads.size());
        stats.put("accountMappings", accountShardMapping.size());
        
        // Average load
        double avgLoad = shardLoads.values().stream()
            .mapToDouble(ShardLoad::getCurrentLoad)
            .average()
            .orElse(0.0);
        stats.put("averageLoad", String.format("%.2f%%", avgLoad * 100));
        
        // Overloaded shards
        long overloaded = shardLoads.values().stream()
            .filter(load -> load.getCurrentLoad() > MAX_UTILIZATION)
            .count();
        stats.put("overloadedShards", overloaded);
        
        // Transaction distribution
        Map<Integer, Long> distribution = new HashMap<>();
        shardTransactionCounts.forEach((k, v) -> distribution.put(k, v.get()));
        stats.put("transactionDistribution", distribution);
        
        return stats;
    }
    
    /**
     * Shuts down the load balancer
     */
    public void shutdown() {
        logger.info("Shutting down LoadBalancer");
        
        rebalancer.shutdown();
        try {
            if (!rebalancer.awaitTermination(10, TimeUnit.SECONDS)) {
                rebalancer.shutdownNow();
            }
        } catch (InterruptedException e) {
            rebalancer.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("LoadBalancer shut down");
    }
}