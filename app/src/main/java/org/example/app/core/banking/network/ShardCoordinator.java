package org.example.app.core.banking.network;

import org.example.app.core.blockchain.block.ShardState;
import org.example.app.core.blockchain.block.ShardState.ShardHealth;
import org.example.app.core.p2p.PeerNetwork;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ShardCoordinator manages the lifecycle, assignment, and health monitoring
 * of all shards in the African Tokenized Currency System.
 * 
 * Key Features:
 * - Manages 20 geographic shards across Africa
 * - Ensures no single region has >33% control (government resistance)
 * - Coordinates shard creation, scaling, and failure recovery
 * - Target: 100,000 TPS (20 shards × 5,000 TPS each)
 * - Dynamic load balancing and rebalancing
 */
public class ShardCoordinator {
    private static final Logger logger = Logger.getLogger(ShardCoordinator.class.getName());
    
    // Configuration constants
    private static final int MAX_SHARDS = 50; // Maximum possible shards
    private static final int TARGET_GEOGRAPHIC_SHARDS = 20; // Initial geographic shards
    private static final double MAX_REGION_CONTROL = 0.33; // 33% max control per region
    private static final long HEALTH_CHECK_INTERVAL_MS = 5000; // 5 seconds
    private static final long REBALANCE_INTERVAL_MS = 60000; // 1 minute
    
    // Shard management
    private final Map<Integer, ShardState> shards;
    private final Map<String, Set<Integer>> regionToShards; // region → shard IDs
    private final Map<Integer, String> shardToRegion; // shard ID → region
    private final AtomicInteger nextShardId;
    
    // Shard validators
    private final Map<Integer, Set<String>> shardValidators; // shard ID → validator IDs
    private final Map<String, Set<Integer>> validatorShards; // validator ID → shard IDs
    
    // Shard health monitoring
    private final ScheduledExecutorService healthMonitor;
    private final ScheduledExecutorService rebalancer;
    private final Map<Integer, ShardMetrics> shardMetrics;
    
    // Geographic regions
    private static final String[] AFRICAN_REGIONS = {
        "WEST_AFRICA", "EAST_AFRICA", "CENTRAL_AFRICA", 
        "SOUTHERN_AFRICA", "NORTH_AFRICA"
    };
    
    /**
     * Shard metrics for monitoring
     */
    public static class ShardMetrics {
        private final int shardId;
        private double averageTPS;
        private double utilization;
        private long lastHealthCheck;
        private int failureCount;
        private ShardHealth healthStatus;
        
        public ShardMetrics(int shardId) {
            this.shardId = shardId;
            this.averageTPS = 0.0;
            this.utilization = 0.0;
            this.lastHealthCheck = System.currentTimeMillis();
            this.failureCount = 0;
            this.healthStatus = ShardHealth.HEALTHY;
        }
        
        public int getShardId() { return shardId; }
        public double getAverageTPS() { return averageTPS; }
        public double getUtilization() { return utilization; }
        public long getLastHealthCheck() { return lastHealthCheck; }
        public int getFailureCount() { return failureCount; }
        public ShardHealth getHealthStatus() { return healthStatus; }
        
        public void setAverageTPS(double tps) { this.averageTPS = tps; }
        public void setUtilization(double util) { this.utilization = util; }
        public void setLastHealthCheck(long time) { this.lastHealthCheck = time; }
        public void incrementFailureCount() { this.failureCount++; }
        public void resetFailureCount() { this.failureCount = 0; }
        public void setHealthStatus(ShardHealth status) { this.healthStatus = status; }
    }
    
    /**
     * Constructor
     */
    public ShardCoordinator() {
        this.shards = new ConcurrentHashMap<>();
        this.regionToShards = new ConcurrentHashMap<>();
        this.shardToRegion = new ConcurrentHashMap<>();
        this.nextShardId = new AtomicInteger(0);
        
        this.shardValidators = new ConcurrentHashMap<>();
        this.validatorShards = new ConcurrentHashMap<>();
        
        this.shardMetrics = new ConcurrentHashMap<>();
        
        // Initialize health monitoring
        this.healthMonitor = Executors.newScheduledThreadPool(1);
        this.rebalancer = Executors.newScheduledThreadPool(1);
        
        // Initialize African regions
        for (String region : AFRICAN_REGIONS) {
            regionToShards.put(region, ConcurrentHashMap.newKeySet());
        }
        
        logger.info("ShardCoordinator initialized");
    }
    
    /**
     * Initializes the default 20 geographic shards
     */
    public void initializeGeographicShards() {
        logger.info("Initializing " + TARGET_GEOGRAPHIC_SHARDS + " geographic shards");
        
        // Create 4 shards per region
        int shardsPerRegion = TARGET_GEOGRAPHIC_SHARDS / AFRICAN_REGIONS.length;
        
        for (String region : AFRICAN_REGIONS) {
            for (int i = 0; i < shardsPerRegion; i++) {
                createShard(region);
            }
        }
        
        logger.info("Geographic shards initialized successfully");
    }
    
    /**
     * Creates a new shard in a specific region
     */
    public synchronized int createShard(String region) {
        if (region == null || region.isEmpty()) {
            throw new IllegalArgumentException("Region cannot be null or empty");
        }
        
        // Check if we can add more shards
        if (shards.size() >= MAX_SHARDS) {
            throw new IllegalStateException("Maximum shard limit reached");
        }
        
        // Verify region control limits (max 33%)
        if (!canAddShardToRegion(region)) {
            throw new IllegalStateException(
                "Cannot add shard to " + region + " - would exceed 33% region control limit"
            );
        }
        
        int shardId = nextShardId.getAndIncrement();
        String shardName = region + "_" + shardId;
        
        ShardState shardState = new ShardState(shardId, shardName);
        shards.put(shardId, shardState);
        
        // Update region mappings
        regionToShards.computeIfAbsent(region, k -> ConcurrentHashMap.newKeySet()).add(shardId);
        shardToRegion.put(shardId, region);
        
        // Initialize validators set
        shardValidators.put(shardId, ConcurrentHashMap.newKeySet());
        
        // Initialize metrics
        shardMetrics.put(shardId, new ShardMetrics(shardId));
        
        logger.info("Created shard " + shardId + " in region " + region);
        
        return shardId;
    }
    
    /**
     * Checks if a shard can be added to a region without violating 33% control limit
     */
    private boolean canAddShardToRegion(String region) {
        int totalShards = shards.size();
        if (totalShards == 0) {
            return true; // No shards yet, can add
        }
        
        int regionShards = regionToShards.getOrDefault(region, Collections.emptySet()).size();
        double currentControl = (double) regionShards / totalShards;
        double futureControl = (double) (regionShards + 1) / (totalShards + 1);
        
        return futureControl <= MAX_REGION_CONTROL;
    }
    
    /**
     * Assigns a validator to a shard
     */
    public synchronized void assignValidatorToShard(String validatorId, int shardId) {
        if (validatorId == null || validatorId.isEmpty()) {
            throw new IllegalArgumentException("Validator ID cannot be null or empty");
        }
        
        if (!shards.containsKey(shardId)) {
            throw new IllegalArgumentException("Shard " + shardId + " does not exist");
        }
        
        // Add validator to shard
        shardValidators.computeIfAbsent(shardId, k -> ConcurrentHashMap.newKeySet())
            .add(validatorId);
        
        // Track shard for validator
        validatorShards.computeIfAbsent(validatorId, k -> ConcurrentHashMap.newKeySet())
            .add(shardId);
        
        // Update shard state
        ShardState shard = shards.get(shardId);
        shard.addValidator(validatorId);
        
        logger.fine("Assigned validator " + validatorId + " to shard " + shardId);
    }
    
    /**
     * Removes a validator from a shard
     */
    public synchronized void removeValidatorFromShard(String validatorId, int shardId) {
        Set<String> validators = shardValidators.get(shardId);
        if (validators != null) {
            validators.remove(validatorId);
        }
        
        Set<Integer> validatorAssignments = validatorShards.get(validatorId);
        if (validatorAssignments != null) {
            validatorAssignments.remove(shardId);
        }
        
        ShardState shard = shards.get(shardId);
        if (shard != null) {
            shard.removeValidator(validatorId);
        }
        
        logger.fine("Removed validator " + validatorId + " from shard " + shardId);
    }
    
    /**
     * Gets a shard by ID
     */
    public ShardState getShard(int shardId) {
        return shards.get(shardId);
    }
    
    /**
     * Gets all shards
     */
    public Map<Integer, ShardState> getAllShards() {
        return new HashMap<>(shards);
    }
    
    /**
     * Gets shards in a specific region
     */
    public Set<Integer> getShardsInRegion(String region) {
        Set<Integer> regionShardIds = regionToShards.get(region);
        return regionShardIds != null ? new HashSet<>(regionShardIds) : Collections.emptySet();
    }
    
    /**
     * Gets the region for a shard
     */
    public String getShardRegion(int shardId) {
        return shardToRegion.get(shardId);
    }
    
    /**
     * Gets total number of shards
     */
    public int getShardCount() {
        return shards.size();
    }
    
    /**
     * Starts health monitoring
     */
    public void startHealthMonitoring() {
        healthMonitor.scheduleAtFixedRate(
            this::performHealthCheck,
            0,
            HEALTH_CHECK_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        logger.info("Health monitoring started");
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
     * Performs health check on all shards
     */
    private void performHealthCheck() {
        try {
            for (Map.Entry<Integer, ShardState> entry : shards.entrySet()) {
                int shardId = entry.getKey();
                ShardState shard = entry.getValue();
                ShardMetrics metrics = shardMetrics.get(shardId);
                
                if (metrics == null) {
                    metrics = new ShardMetrics(shardId);
                    shardMetrics.put(shardId, metrics);
                }
                
                // Update metrics
                metrics.setAverageTPS(shard.getAverageTPS());
                metrics.setUtilization(shard.getUtilization());
                metrics.setHealthStatus(shard.getHealthStatus());
                metrics.setLastHealthCheck(System.currentTimeMillis());
                
                // Check shard health
                if (!shard.isHealthy()) {
                    metrics.incrementFailureCount();
                    logger.warning("Shard " + shardId + " is unhealthy: " + shard.getHealthStatus());
                    
                    // Trigger recovery if too many failures
                    if (metrics.getFailureCount() > 3) {
                        triggerShardRecovery(shardId);
                    }
                } else {
                    metrics.resetFailureCount();
                }
                
                // Check if shard is overloaded (>90% utilization)
                if (metrics.getUtilization() > 90.0) {
                    logger.warning("Shard " + shardId + " is overloaded: " + 
                        String.format("%.2f%% utilization", metrics.getUtilization()));
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during health check", e);
        }
    }
    
    /**
     * Performs load rebalancing across shards
     */
    private void performRebalancing() {
        try {
            // Find overloaded shards (>80% utilization)
            List<Integer> overloadedShards = new ArrayList<>();
            List<Integer> underloadedShards = new ArrayList<>();
            
            for (Map.Entry<Integer, ShardMetrics> entry : shardMetrics.entrySet()) {
                int shardId = entry.getKey();
                ShardMetrics metrics = entry.getValue();
                
                if (metrics.getUtilization() > 80.0) {
                    overloadedShards.add(shardId);
                } else if (metrics.getUtilization() < 50.0) {
                    underloadedShards.add(shardId);
                }
            }
            
            if (!overloadedShards.isEmpty()) {
                logger.info("Rebalancing needed: " + overloadedShards.size() + 
                    " overloaded shards, " + underloadedShards.size() + " underloaded shards");
                
                // In production, this would trigger:
                // 1. Migration of accounts to underloaded shards
                // 2. Creation of new shards if all shards are overloaded
                // 3. Redistribution of validators
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during rebalancing", e);
        }
    }
    
    /**
     * Triggers recovery for an unhealthy shard
     */
    private void triggerShardRecovery(int shardId) {
        logger.info("Triggering recovery for shard " + shardId);
        
        ShardState shard = shards.get(shardId);
        if (shard != null) {
            shard.updateHealthStatus(ShardHealth.RECOVERING);
            
            // In production, this would:
            // 1. Pause new transactions to this shard
            // 2. Sync state from other shards
            // 3. Restart validators
            // 4. Resume operations
        }
    }
    
    /**
     * Gets shard metrics
     */
    public ShardMetrics getShardMetrics(int shardId) {
        return shardMetrics.get(shardId);
    }
    
    /**
     * Gets all shard metrics
     */
    public Map<Integer, ShardMetrics> getAllShardMetrics() {
        return new HashMap<>(shardMetrics);
    }
    
    /**
     * Calculates total system TPS
     */
    public double getTotalSystemTPS() {
        return shardMetrics.values().stream()
            .mapToDouble(ShardMetrics::getAverageTPS)
            .sum();
    }
    
    /**
     * Calculates average system utilization
     */
    public double getAverageSystemUtilization() {
        if (shardMetrics.isEmpty()) {
            return 0.0;
        }
        
        return shardMetrics.values().stream()
            .mapToDouble(ShardMetrics::getUtilization)
            .average()
            .orElse(0.0);
    }
    
    /**
     * Gets system health statistics
     */
    public Map<String, Object> getSystemStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalShards", shards.size());
        stats.put("totalSystemTPS", String.format("%.2f", getTotalSystemTPS()));
        stats.put("averageUtilization", String.format("%.2f%%", getAverageSystemUtilization()));
        
        // Count shards by health status
        Map<ShardHealth, Long> healthCounts = shardMetrics.values().stream()
            .collect(Collectors.groupingBy(
                ShardMetrics::getHealthStatus,
                Collectors.counting()
            ));
        stats.put("healthCounts", healthCounts);
        
        // Regional distribution
        Map<String, Integer> regionalDistribution = new HashMap<>();
        for (String region : AFRICAN_REGIONS) {
            regionalDistribution.put(region, getShardsInRegion(region).size());
        }
        stats.put("regionalDistribution", regionalDistribution);
        
        return stats;
    }
    
    /**
     * Shuts down the coordinator
     */
    public void shutdown() {
        logger.info("Shutting down ShardCoordinator");
        
        healthMonitor.shutdown();
        rebalancer.shutdown();
        
        try {
            if (!healthMonitor.awaitTermination(10, TimeUnit.SECONDS)) {
                healthMonitor.shutdownNow();
            }
            if (!rebalancer.awaitTermination(10, TimeUnit.SECONDS)) {
                rebalancer.shutdownNow();
            }
        } catch (InterruptedException e) {
            healthMonitor.shutdownNow();
            rebalancer.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("ShardCoordinator shut down");
    }
}