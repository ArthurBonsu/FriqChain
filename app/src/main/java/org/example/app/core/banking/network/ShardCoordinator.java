package org.example.app.core.banking.network;

import org.example.app.core.blockchain.block.ShardState;
import org.example.app.core.blockchain.block.ShardState.ShardHealth;
import org.example.app.core.p2p.PeerNetwork;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * ShardCoordinator manages the lifecycle, assignment, and health monitoring
 * of all shards in the African Tokenized Currency System with sovereignty enforcement
 * 
 * AFRICAN SOVEREIGNTY ENHANCEMENTS:
 * - 5 African regions (North, West, East, Central, Southern)
 * - 20 geographic shards (4 per region)
 * - 35 DAC nodes (7 per region)
 * - 5-node rotating committees
 * - Multi-region validation tracking
 * - ASU settlement metrics
 * - Treasury subsidy metrics
 * - Cross-region transaction coordination
 * - Reputation-based shard assignment
 * - Max 33% regional control enforcement
 * 
 * Key Features:
 * - Manages 20 geographic shards across Africa
 * - Ensures no single region has >33% control (government resistance)
 * - Coordinates shard creation, scaling, and failure recovery
 * - Target: 100,000 TPS (20 shards × 5,000 TPS each)
 * - Dynamic load balancing and rebalancing
 * - Regional consensus coordination
 */
public class ShardCoordinator {
    private static final Logger logger = Logger.getLogger(ShardCoordinator.class.getName());
    
    // African Geographic Regions
    public enum AfricanRegion {
        NORTH_AFRICA(1, "North Africa"),
        WEST_AFRICA(2, "West Africa"),
        EAST_AFRICA(3, "East Africa"),
        CENTRAL_AFRICA(4, "Central Africa"),
        SOUTHERN_AFRICA(5, "Southern Africa");
        
        private final int code;
        private final String name;
        
        AfricanRegion(int code, String name) {
            this.code = code;
            this.name = name;
        }
        
        public int getCode() { return code; }
        public String getName() { return name; }
        
        public static AfricanRegion fromString(String str) {
            for (AfricanRegion region : values()) {
                if (region.name().equals(str) || region.getName().equals(str)) {
                    return region;
                }
            }
            return WEST_AFRICA;
        }
        
        public static AfricanRegion fromShardId(int shardId) {
            if (shardId >= 0 && shardId <= 3) return NORTH_AFRICA;
            if (shardId >= 4 && shardId <= 7) return WEST_AFRICA;
            if (shardId >= 8 && shardId <= 11) return EAST_AFRICA;
            if (shardId >= 12 && shardId <= 15) return CENTRAL_AFRICA;
            if (shardId >= 16 && shardId <= 19) return SOUTHERN_AFRICA;
            return WEST_AFRICA;
        }
    }
    
    // Configuration constants
    private static final int MAX_SHARDS = 50;
    private static final int TARGET_GEOGRAPHIC_SHARDS = 20;
    private static final double MAX_REGION_CONTROL = 0.33;
    private static final long HEALTH_CHECK_INTERVAL_MS = 5000;
    private static final long REBALANCE_INTERVAL_MS = 60000;
    
    // NEW: Sovereignty configuration
    private static final int SHARDS_PER_REGION = 4;
    private static final int DAC_NODES_PER_REGION = 7;
    private static final int TOTAL_DAC_NODES = 35;
    private static final double MIN_VALIDATOR_REPUTATION = 0.60;
    
    // Shard management
    private final Map<Integer, ShardState> shards;
    private final Map<AfricanRegion, Set<Integer>> regionToShards;
    private final Map<Integer, AfricanRegion> shardToRegion;
    private final AtomicInteger nextShardId;
    
    // Shard validators
    private final Map<Integer, Set<String>> shardValidators;
    private final Map<String, Set<Integer>> validatorShards;
    
    // NEW: DAC node tracking per shard
    private final Map<Integer, Set<Integer>> shardDACNodes; // shard ID → DAC node IDs
    private final Map<Integer, Integer> dacNodeShardAssignment; // DAC node ID → shard ID
    
    // NEW: Committee tracking per shard
    private final Map<Integer, Set<String>> shardCommitteeMembers; // shard ID → committee node IDs
    
    // Shard health monitoring
    private final ScheduledExecutorService healthMonitor;
    private final ScheduledExecutorService rebalancer;
    private final Map<Integer, ShardMetrics> shardMetrics;
    
    // NEW: Regional metrics
    private final Map<AfricanRegion, RegionalShardMetrics> regionalMetrics;
    
    /**
     * NEW: Regional shard metrics
     */
    public static class RegionalShardMetrics {
        private final AfricanRegion region;
        private final AtomicLong totalTransactions;
        private final AtomicLong crossRegionTransactions;
        private final AtomicLong asuSettlements;
        private final AtomicLong treasurySubsidizedTx;
        private final AtomicLong multiRegionValidations;
        private final AtomicLong dacAttestations;
        private final AtomicLong committeeSignatures;
        private volatile double averageTPS;
        private volatile double averageUtilization;
        
        public RegionalShardMetrics(AfricanRegion region) {
            this.region = region;
            this.totalTransactions = new AtomicLong(0);
            this.crossRegionTransactions = new AtomicLong(0);
            this.asuSettlements = new AtomicLong(0);
            this.treasurySubsidizedTx = new AtomicLong(0);
            this.multiRegionValidations = new AtomicLong(0);
            this.dacAttestations = new AtomicLong(0);
            this.committeeSignatures = new AtomicLong(0);
            this.averageTPS = 0.0;
            this.averageUtilization = 0.0;
        }
        
        public void recordTransaction() { totalTransactions.incrementAndGet(); }
        public void recordCrossRegion() { crossRegionTransactions.incrementAndGet(); }
        public void recordASU() { asuSettlements.incrementAndGet(); }
        public void recordTreasurySubsidy() { treasurySubsidizedTx.incrementAndGet(); }
        public void recordMultiRegionValidation() { multiRegionValidations.incrementAndGet(); }
        public void recordDACAttestation() { dacAttestations.incrementAndGet(); }
        public void recordCommitteeSignature() { committeeSignatures.incrementAndGet(); }
        
        public void setAverageTPS(double tps) { this.averageTPS = tps; }
        public void setAverageUtilization(double util) { this.averageUtilization = util; }
        
        public AfricanRegion getRegion() { return region; }
        public long getTotalTransactions() { return totalTransactions.get(); }
        public long getCrossRegionTransactions() { return crossRegionTransactions.get(); }
        public long getASUSettlements() { return asuSettlements.get(); }
        public long getTreasurySubsidizedTx() { return treasurySubsidizedTx.get(); }
        public long getMultiRegionValidations() { return multiRegionValidations.get(); }
        public long getDACAttestations() { return dacAttestations.get(); }
        public long getCommitteeSignatures() { return committeeSignatures.get(); }
        public double getAverageTPS() { return averageTPS; }
        public double getAverageUtilization() { return averageUtilization; }
    }
    
    /**
     * Shard metrics for monitoring (enhanced)
     */
    public static class ShardMetrics {
        private final int shardId;
        private final AfricanRegion region;
        private double averageTPS;
        private double utilization;
        private long lastHealthCheck;
        private int failureCount;
        private ShardHealth healthStatus;
        
        // NEW: Sovereignty metrics
        private final AtomicLong crossRegionTransactions;
        private final AtomicLong asuSettlements;
        private final AtomicLong treasurySubsidizedTx;
        private final AtomicLong multiRegionValidations;
        private final AtomicLong dacAttestations;
        
        public ShardMetrics(int shardId, AfricanRegion region) {
            this.shardId = shardId;
            this.region = region;
            this.averageTPS = 0.0;
            this.utilization = 0.0;
            this.lastHealthCheck = System.currentTimeMillis();
            this.failureCount = 0;
            this.healthStatus = ShardHealth.HEALTHY;
            
            this.crossRegionTransactions = new AtomicLong(0);
            this.asuSettlements = new AtomicLong(0);
            this.treasurySubsidizedTx = new AtomicLong(0);
            this.multiRegionValidations = new AtomicLong(0);
            this.dacAttestations = new AtomicLong(0);
        }
        
        public int getShardId() { return shardId; }
        public AfricanRegion getRegion() { return region; }
        public double getAverageTPS() { return averageTPS; }
        public double getUtilization() { return utilization; }
        public long getLastHealthCheck() { return lastHealthCheck; }
        public int getFailureCount() { return failureCount; }
        public ShardHealth getHealthStatus() { return healthStatus; }
        
        public long getCrossRegionTransactions() { return crossRegionTransactions.get(); }
        public long getASUSettlements() { return asuSettlements.get(); }
        public long getTreasurySubsidizedTx() { return treasurySubsidizedTx.get(); }
        public long getMultiRegionValidations() { return multiRegionValidations.get(); }
        public long getDACAttestations() { return dacAttestations.get(); }
        
        public void setAverageTPS(double tps) { this.averageTPS = tps; }
        public void setUtilization(double util) { this.utilization = util; }
        public void setLastHealthCheck(long time) { this.lastHealthCheck = time; }
        public void incrementFailureCount() { this.failureCount++; }
        public void resetFailureCount() { this.failureCount = 0; }
        public void setHealthStatus(ShardHealth status) { this.healthStatus = status; }
        
        public void recordCrossRegion() { crossRegionTransactions.incrementAndGet(); }
        public void recordASU() { asuSettlements.incrementAndGet(); }
        public void recordTreasurySubsidy() { treasurySubsidizedTx.incrementAndGet(); }
        public void recordMultiRegionValidation() { multiRegionValidations.incrementAndGet(); }
        public void recordDACAttestation() { dacAttestations.incrementAndGet(); }
    }
    
    /**
     * Constructor
     */
    public ShardCoordinator() {
        this.shards = new ConcurrentHashMap<>();
        this.regionToShards = new EnumMap<>(AfricanRegion.class);
        this.shardToRegion = new ConcurrentHashMap<>();
        this.nextShardId = new AtomicInteger(0);
        
        this.shardValidators = new ConcurrentHashMap<>();
        this.validatorShards = new ConcurrentHashMap<>();
        
        this.shardDACNodes = new ConcurrentHashMap<>();
        this.dacNodeShardAssignment = new ConcurrentHashMap<>();
        this.shardCommitteeMembers = new ConcurrentHashMap<>();
        
        this.shardMetrics = new ConcurrentHashMap<>();
        this.regionalMetrics = new EnumMap<>(AfricanRegion.class);
        
        this.healthMonitor = Executors.newScheduledThreadPool(1);
        this.rebalancer = Executors.newScheduledThreadPool(1);
        
        // Initialize African regions
        for (AfricanRegion region : AfricanRegion.values()) {
            regionToShards.put(region, ConcurrentHashMap.newKeySet());
            regionalMetrics.put(region, new RegionalShardMetrics(region));
        }
        
        logger.info("ShardCoordinator initialized with 5 African regions");
    }
    
    /**
     * Initializes the default 20 geographic shards
     */
    public void initializeGeographicShards() {
        logger.info("Initializing " + TARGET_GEOGRAPHIC_SHARDS + " geographic shards across 5 regions");
        
        // Create 4 shards per region
        for (AfricanRegion region : AfricanRegion.values()) {
            for (int i = 0; i < SHARDS_PER_REGION; i++) {
                createShard(region);
            }
        }
        
        // Assign DAC nodes to shards (7 DAC nodes per region, distributed across 4 shards)
        assignDACNodesToShards();
        
        logger.info("Geographic shards initialized successfully with DAC node assignments");
    }
    
    /**
     * NEW: Assigns DAC nodes to shards
     */
    private void assignDACNodesToShards() {
        int dacNodeId = 0;
        
        for (AfricanRegion region : AfricanRegion.values()) {
            Set<Integer> regionShards = regionToShards.get(region);
            List<Integer> shardList = new ArrayList<>(regionShards);
            
            // Assign 7 DAC nodes per region
            for (int i = 0; i < DAC_NODES_PER_REGION; i++) {
                // Distribute DAC nodes across shards (round-robin)
                int shardId = shardList.get(i % SHARDS_PER_REGION);
                assignDACNodeToShard(dacNodeId, shardId);
                dacNodeId++;
            }
        }
        
        logger.info("Assigned " + TOTAL_DAC_NODES + " DAC nodes to 20 shards");
    }
    
    /**
     * NEW: Assigns a DAC node to a shard
     */
    public void assignDACNodeToShard(int dacNodeId, int shardId) {
        if (dacNodeId < 0 || dacNodeId >= TOTAL_DAC_NODES) {
            throw new IllegalArgumentException("Invalid DAC node ID: " + dacNodeId);
        }
        
        shardDACNodes.computeIfAbsent(shardId, k -> ConcurrentHashMap.newKeySet())
                    .add(dacNodeId);
        dacNodeShardAssignment.put(dacNodeId, shardId);
        
        logger.fine("Assigned DAC node " + dacNodeId + " to shard " + shardId);
    }
    
    /**
     * Creates a new shard in a specific region (enhanced)
     */
    public synchronized int createShard(AfricanRegion region) {
        if (region == null) {
            throw new IllegalArgumentException("Region cannot be null");
        }
        
        if (shards.size() >= MAX_SHARDS) {
            throw new IllegalStateException("Maximum shard limit reached");
        }
        
        if (!canAddShardToRegion(region)) {
            throw new IllegalStateException(
                "Cannot add shard to " + region.getName() + " - would exceed 33% region control limit"
            );
        }
        
        int shardId = nextShardId.getAndIncrement();
        String shardName = region.name() + "_SHARD_" + shardId;
        
        ShardState shardState = new ShardState(shardId, shardName);
        shards.put(shardId, shardState);
        
        // Update region mappings
        regionToShards.get(region).add(shardId);
        shardToRegion.put(shardId, region);
        
        // Initialize validators set
        shardValidators.put(shardId, ConcurrentHashMap.newKeySet());
        
        // Initialize DAC nodes set
        shardDACNodes.put(shardId, ConcurrentHashMap.newKeySet());
        
        // Initialize committee members set
        shardCommitteeMembers.put(shardId, ConcurrentHashMap.newKeySet());
        
        // Initialize metrics
        shardMetrics.put(shardId, new ShardMetrics(shardId, region));
        
        logger.info("Created shard " + shardId + " in " + region.getName());
        
        return shardId;
    }
    
    /**
     * String-based shard creation for backward compatibility
     */
    public synchronized int createShard(String regionStr) {
        return createShard(AfricanRegion.fromString(regionStr));
    }
    
    /**
     * Checks if a shard can be added to a region without violating 33% control limit
     */
    private boolean canAddShardToRegion(AfricanRegion region) {
        int totalShards = shards.size();
        if (totalShards == 0) {
            return true;
        }
        
        int regionShards = regionToShards.get(region).size();
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
        
        shardValidators.computeIfAbsent(shardId, k -> ConcurrentHashMap.newKeySet())
            .add(validatorId);
        
        validatorShards.computeIfAbsent(validatorId, k -> ConcurrentHashMap.newKeySet())
            .add(shardId);
        
        ShardState shard = shards.get(shardId);
        shard.addValidator(validatorId);
        
        logger.fine("Assigned validator " + validatorId + " to shard " + shardId);
    }
    
    /**
     * NEW: Assigns a committee member to a shard
     */
    public synchronized void assignCommitteeMemberToShard(String memberId, int shardId) {
        if (!shards.containsKey(shardId)) {
            throw new IllegalArgumentException("Shard " + shardId + " does not exist");
        }
        
        shardCommitteeMembers.computeIfAbsent(shardId, k -> ConcurrentHashMap.newKeySet())
                            .add(memberId);
        
        logger.fine("Assigned committee member " + memberId + " to shard " + shardId);
    }
    
    /**
     * Removes a validator from a shard
     */
    public synchronized void removeValidatorFromShard(String validatorId, int shardId) {
        Set<String> validators = shardValidators.get(shardId);
        if (validators != null) {
            validators.remove(validatorId);
        }
        
        Set<Integer> validatorShardSet = validatorShards.get(validatorId);
        if (validatorShardSet != null) {
            validatorShardSet.remove(shardId);
        }
        
        ShardState shard = shards.get(shardId);
        if (shard != null) {
            shard.removeValidator(validatorId);
        }
        
        logger.fine("Removed validator " + validatorId + " from shard " + shardId);
    }
    
    /**
     * Gets shards in a region
     */
    public Set<Integer> getShardsInRegion(AfricanRegion region) {
        return new HashSet<>(regionToShards.getOrDefault(region, Collections.emptySet()));
    }
    
    /**
     * String-based region lookup for backward compatibility
     */
    public Set<Integer> getShardsInRegion(String regionStr) {
        return getShardsInRegion(AfricanRegion.fromString(regionStr));
    }
    
    /**
     * Gets region for a shard
     */
    public AfricanRegion getRegionForShard(int shardId) {
        return shardToRegion.get(shardId);
    }
    
    /**
     * NEW: Gets DAC nodes for a shard
     */
    public Set<Integer> getDACNodesForShard(int shardId) {
        return new HashSet<>(shardDACNodes.getOrDefault(shardId, Collections.emptySet()));
    }
    
    /**
     * NEW: Gets shard for a DAC node
     */
    public Integer getShardForDACNode(int dacNodeId) {
        return dacNodeShardAssignment.get(dacNodeId);
    }
    
    /**
     * NEW: Gets committee members for a shard
     */
    public Set<String> getCommitteeMembersForShard(int shardId) {
        return new HashSet<>(shardCommitteeMembers.getOrDefault(shardId, Collections.emptySet()));
    }
    
    /**
     * Gets a specific shard
     */
    public ShardState getShard(int shardId) {
        return shards.get(shardId);
    }
    
    /**
     * Gets total number of shards
     */
    public int getShardCount() {
        return shards.size();
    }
    
    /**
     * NEW: Records cross-region transaction for a shard
     */
    public void recordCrossRegionTransaction(int shardId) {
        ShardMetrics metrics = shardMetrics.get(shardId);
        if (metrics != null) {
            metrics.recordCrossRegion();
            regionalMetrics.get(metrics.getRegion()).recordCrossRegion();
        }
    }
    
    /**
     * NEW: Records ASU settlement for a shard
     */
    public void recordASUSettlement(int shardId) {
        ShardMetrics metrics = shardMetrics.get(shardId);
        if (metrics != null) {
            metrics.recordASU();
            regionalMetrics.get(metrics.getRegion()).recordASU();
        }
    }
    
    /**
     * NEW: Records Treasury subsidy for a shard
     */
    public void recordTreasurySubsidy(int shardId) {
        ShardMetrics metrics = shardMetrics.get(shardId);
        if (metrics != null) {
            metrics.recordTreasurySubsidy();
            regionalMetrics.get(metrics.getRegion()).recordTreasurySubsidy();
        }
    }
    
    /**
     * NEW: Records multi-region validation
     */
    public void recordMultiRegionValidation(int shardId) {
        ShardMetrics metrics = shardMetrics.get(shardId);
        if (metrics != null) {
            metrics.recordMultiRegionValidation();
            regionalMetrics.get(metrics.getRegion()).recordMultiRegionValidation();
        }
    }
    
    /**
     * NEW: Records DAC attestation
     */
    public void recordDACAttestation(int shardId) {
        ShardMetrics metrics = shardMetrics.get(shardId);
        if (metrics != null) {
            metrics.recordDACAttestation();
            regionalMetrics.get(metrics.getRegion()).recordDACAttestation();
        }
    }
    
    /**
     * NEW: Records committee signature
     */
    public void recordCommitteeSignature(AfricanRegion region) {
        regionalMetrics.get(region).recordCommitteeSignature();
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
                    AfricanRegion region = shardToRegion.get(shardId);
                    metrics = new ShardMetrics(shardId, region);
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
                    logger.warning("Shard " + shardId + " (" + metrics.getRegion().getName() + 
                        ") is unhealthy: " + shard.getHealthStatus());
                    
                    if (metrics.getFailureCount() > 3) {
                        triggerShardRecovery(shardId);
                    }
                } else {
                    metrics.resetFailureCount();
                }
                
                if (metrics.getUtilization() > 90.0) {
                    logger.warning("Shard " + shardId + " (" + metrics.getRegion().getName() + 
                        ") is overloaded: " + String.format("%.2f%% utilization", metrics.getUtilization()));
                }
            }
            
            // Update regional averages
            updateRegionalMetrics();
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during health check", e);
        }
    }
    
    /**
     * NEW: Updates regional aggregated metrics
     */
    private void updateRegionalMetrics() {
        for (AfricanRegion region : AfricanRegion.values()) {
            Set<Integer> regionShards = regionToShards.get(region);
            RegionalShardMetrics regional = regionalMetrics.get(region);
            
            double avgTPS = regionShards.stream()
                .map(shardMetrics::get)
                .filter(Objects::nonNull)
                .mapToDouble(ShardMetrics::getAverageTPS)
                .average()
                .orElse(0.0);
            
            double avgUtil = regionShards.stream()
                .map(shardMetrics::get)
                .filter(Objects::nonNull)
                .mapToDouble(ShardMetrics::getUtilization)
                .average()
                .orElse(0.0);
            
            regional.setAverageTPS(avgTPS);
            regional.setAverageUtilization(avgUtil);
        }
    }
    
    /**
     * Performs load rebalancing across shards
     */
    private void performRebalancing() {
        try {
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
     * NEW: Gets regional metrics
     */
    public RegionalShardMetrics getRegionalMetrics(AfricanRegion region) {
        return regionalMetrics.get(region);
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
     * Gets system health statistics (enhanced)
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
        Map<String, Object> regionalDistribution = new HashMap<>();
        for (AfricanRegion region : AfricanRegion.values()) {
            Map<String, Object> regionStats = new HashMap<>();
            RegionalShardMetrics regional = regionalMetrics.get(region);
            
            regionStats.put("shardCount", getShardsInRegion(region).size());
            regionStats.put("averageTPS", String.format("%.2f", regional.getAverageTPS()));
            regionStats.put("averageUtilization", String.format("%.2f%%", regional.getAverageUtilization()));
            regionStats.put("crossRegionTx", regional.getCrossRegionTransactions());
            regionStats.put("asuSettlements", regional.getASUSettlements());
            regionStats.put("treasurySubsidized", regional.getTreasurySubsidizedTx());
            regionStats.put("multiRegionValidations", regional.getMultiRegionValidations());
            regionStats.put("dacAttestations", regional.getDACAttestations());
            regionStats.put("committeeSignatures", regional.getCommitteeSignatures());
            
            regionalDistribution.put(region.getName(), regionStats);
        }
        stats.put("regionalDistribution", regionalDistribution);
        
        // System-wide sovereignty metrics
        long totalCrossRegion = regionalMetrics.values().stream()
            .mapToLong(RegionalShardMetrics::getCrossRegionTransactions)
            .sum();
        long totalASU = regionalMetrics.values().stream()
            .mapToLong(RegionalShardMetrics::getASUSettlements)
            .sum();
        long totalSubsidized = regionalMetrics.values().stream()
            .mapToLong(RegionalShardMetrics::getTreasurySubsidizedTx)
            .sum();
        long totalMultiRegion = regionalMetrics.values().stream()
            .mapToLong(RegionalShardMetrics::getMultiRegionValidations)
            .sum();
        long totalDACAttestations = regionalMetrics.values().stream()
            .mapToLong(RegionalShardMetrics::getDACAttestations)
            .sum();
        long totalCommitteeSigs = regionalMetrics.values().stream()
            .mapToLong(RegionalShardMetrics::getCommitteeSignatures)
            .sum();
        
        stats.put("totalCrossRegionTx", totalCrossRegion);
        stats.put("totalASUSettlements", totalASU);
        stats.put("totalTreasurySubsidized", totalSubsidized);
        stats.put("totalMultiRegionValidations", totalMultiRegion);
        stats.put("totalDACAttestations", totalDACAttestations);
        stats.put("totalCommitteeSignatures", totalCommitteeSigs);
        
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