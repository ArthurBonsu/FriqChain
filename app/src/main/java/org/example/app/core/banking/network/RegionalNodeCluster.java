package org.example.app.core.banking.network;

import org.example.app.core.p2p.PeerNetwork;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * RegionalNodeCluster manages geographically distributed node clusters
 * with African sovereignty enforcement
 * 
 * AFRICAN SOVEREIGNTY ENHANCEMENTS:
 * - 5 African regions (North, West, East, Central, Southern)
 * - 20 geographic shards (4 per region)
 * - 35 DAC nodes (7 per region)
 * - 5-node rotating committees
 * - Reputation integration (≥0.60 for validators)
 * - Max 33% regional control enforcement
 * - ASU settlement tracking
 * - Treasury subsidy tracking
 * - Cross-region coordination
 * - Multi-region validation support
 * 
 * Key Features:
 * - Geographic distribution across 5 African regions
 * - Enforces max 33% regional control (government resistance)
 * - Latency-optimized node selection
 * - Regional consensus coordination
 * - DAC node prioritization
 * - Committee member management
 * - Shard-aware routing
 */
public class RegionalNodeCluster {
    private static final Logger logger = Logger.getLogger(RegionalNodeCluster.class.getName());
    
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
            return WEST_AFRICA; // Default
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
    
    // Configuration
    private static final double MAX_REGIONAL_CONTROL = 0.33; // 33% max
    private static final int MIN_NODES_PER_REGION = 3;
    private static final long HEALTH_CHECK_INTERVAL_MS = 10000; // 10 seconds
    
    // NEW: Sovereignty configuration
    private static final double MIN_VALIDATOR_REPUTATION = 0.60;
    private static final double MIN_DAC_REPUTATION = 0.50;
    private static final double MIN_COMMITTEE_REPUTATION = 0.65;
    private static final int DAC_NODES_PER_REGION = 7;
    private static final int TOTAL_DAC_NODES = 35;
    private static final int COMMITTEE_SIZE = 5;
    private static final int SHARDS_PER_REGION = 4;
    
    // Regional clusters
    private final Map<AfricanRegion, NodeCluster> regionalClusters;
    private final Map<String, Set<AfricanRegion>> nodeRegions; // node ID → regions
    private final ScheduledExecutorService healthMonitor;
    
    // NEW: Shard tracking
    private final Map<Integer, Set<String>> shardNodes; // shard ID → node IDs
    private final Map<String, Set<Integer>> nodeShards; // node ID → shard IDs
    
    // NEW: DAC node tracking
    private final Map<Integer, String> dacNodeRegistry; // DAC node ID (0-34) → node ID
    private final Map<AfricanRegion, Set<Integer>> dacNodesByRegion; // region → DAC node IDs
    
    // NEW: Committee tracking
    private final Map<String, CommitteeInfo> committeeMembers; // node ID → committee info
    private final Set<String> activeCommitteeMembers;
    
    // NEW: Sovereignty metrics
    private final Map<AfricanRegion, SovereigntyMetrics> regionalMetrics;
    
    /**
     * Committee information for a node
     */
    public static class CommitteeInfo {
        private final String nodeId;
        private final AfricanRegion region;
        private final long joinedTime;
        private final AtomicLong batchesSubmitted;
        private final AtomicLong batchesApproved;
        private volatile boolean isActive;
        
        public CommitteeInfo(String nodeId, AfricanRegion region) {
            this.nodeId = nodeId;
            this.region = region;
            this.joinedTime = System.currentTimeMillis();
            this.batchesSubmitted = new AtomicLong(0);
            this.batchesApproved = new AtomicLong(0);
            this.isActive = true;
        }
        
        public String getNodeId() { return nodeId; }
        public AfricanRegion getRegion() { return region; }
        public long getJoinedTime() { return joinedTime; }
        public long getBatchesSubmitted() { return batchesSubmitted.get(); }
        public long getBatchesApproved() { return batchesApproved.get(); }
        public boolean isActive() { return isActive; }
        
        public void incrementBatchesSubmitted() { batchesSubmitted.incrementAndGet(); }
        public void incrementBatchesApproved() { batchesApproved.incrementAndGet(); }
        public void setActive(boolean active) { this.isActive = active; }
    }
    
    /**
     * Sovereignty metrics for a region
     */
    public static class SovereigntyMetrics {
        private final AfricanRegion region;
        private final AtomicLong crossRegionTransactions;
        private final AtomicLong asuSettlements;
        private final AtomicLong treasurySubsidizedTx;
        private final AtomicLong dacAttestations;
        private final AtomicLong committeeSignatures;
        private final AtomicLong multiRegionValidations;
        
        public SovereigntyMetrics(AfricanRegion region) {
            this.region = region;
            this.crossRegionTransactions = new AtomicLong(0);
            this.asuSettlements = new AtomicLong(0);
            this.treasurySubsidizedTx = new AtomicLong(0);
            this.dacAttestations = new AtomicLong(0);
            this.committeeSignatures = new AtomicLong(0);
            this.multiRegionValidations = new AtomicLong(0);
        }
        
        public void recordCrossRegion() { crossRegionTransactions.incrementAndGet(); }
        public void recordASU() { asuSettlements.incrementAndGet(); }
        public void recordTreasurySubsidy() { treasurySubsidizedTx.incrementAndGet(); }
        public void recordDACAttestation() { dacAttestations.incrementAndGet(); }
        public void recordCommitteeSignature() { committeeSignatures.incrementAndGet(); }
        public void recordMultiRegionValidation() { multiRegionValidations.incrementAndGet(); }
        
        public long getCrossRegionTransactions() { return crossRegionTransactions.get(); }
        public long getASUSettlements() { return asuSettlements.get(); }
        public long getTreasurySubsidizedTx() { return treasurySubsidizedTx.get(); }
        public long getDACAttestations() { return dacAttestations.get(); }
        public long getCommitteeSignatures() { return committeeSignatures.get(); }
        public long getMultiRegionValidations() { return multiRegionValidations.get(); }
    }
    
    /**
     * Node cluster for a region (enhanced)
     */
    public static class NodeCluster {
        private final AfricanRegion region;
        private final Set<Node> nodes;
        private final AtomicInteger activeNodeCount;
        private final AtomicInteger validatorCount;
        private final AtomicInteger dacNodeCount;
        private final AtomicInteger committeeNodeCount;
        private double averageLatency;
        private ClusterHealth health;
        
        // NEW: Regional shard tracking
        private final Set<Integer> shardIds; // Which shards this region manages
        
        public NodeCluster(AfricanRegion region) {
            this.region = region;
            this.nodes = ConcurrentHashMap.newKeySet();
            this.activeNodeCount = new AtomicInteger(0);
            this.validatorCount = new AtomicInteger(0);
            this.dacNodeCount = new AtomicInteger(0);
            this.committeeNodeCount = new AtomicInteger(0);
            this.averageLatency = 0.0;
            this.health = ClusterHealth.HEALTHY;
            this.shardIds = ConcurrentHashMap.newKeySet();
            
            // Initialize shards for this region (4 shards per region)
            int baseShardId = region.getCode() * SHARDS_PER_REGION - SHARDS_PER_REGION;
            for (int i = 0; i < SHARDS_PER_REGION; i++) {
                shardIds.add(baseShardId + i);
            }
        }
        
        public AfricanRegion getRegion() { return region; }
        public Set<Node> getNodes() { return new HashSet<>(nodes); }
        public int getActiveNodeCount() { return activeNodeCount.get(); }
        public int getValidatorCount() { return validatorCount.get(); }
        public int getDACNodeCount() { return dacNodeCount.get(); }
        public int getCommitteeNodeCount() { return committeeNodeCount.get(); }
        public double getAverageLatency() { return averageLatency; }
        public ClusterHealth getHealth() { return health; }
        public Set<Integer> getShardIds() { return new HashSet<>(shardIds); }
        
        public void addNode(Node node) {
            nodes.add(node);
            if (node.isActive()) {
                activeNodeCount.incrementAndGet();
            }
            if (node.isValidator()) {
                validatorCount.incrementAndGet();
            }
            if (node.isDACNode()) {
                dacNodeCount.incrementAndGet();
            }
            if (node.isCommitteeMember()) {
                committeeNodeCount.incrementAndGet();
            }
        }
        
        public void removeNode(Node node) {
            if (nodes.remove(node)) {
                if (node.isActive()) {
                    activeNodeCount.decrementAndGet();
                }
                if (node.isValidator()) {
                    validatorCount.decrementAndGet();
                }
                if (node.isDACNode()) {
                    dacNodeCount.decrementAndGet();
                }
                if (node.isCommitteeMember()) {
                    committeeNodeCount.decrementAndGet();
                }
            }
        }
        
        public void updateLatency(double latency) {
            this.averageLatency = latency;
        }
        
        public void updateHealth(ClusterHealth health) {
            this.health = health;
        }
        
        public void updateCounts() {
            activeNodeCount.set((int) nodes.stream().filter(Node::isActive).count());
            validatorCount.set((int) nodes.stream().filter(Node::isValidator).count());
            dacNodeCount.set((int) nodes.stream().filter(Node::isDACNode).count());
            committeeNodeCount.set((int) nodes.stream().filter(Node::isCommitteeMember).count());
        }
    }
    
    /**
     * Node representation (enhanced with African sovereignty)
     */
    public static class Node {
        private final String nodeId;
        private final String ipAddress;
        private final AfricanRegion region;
        
        // NEW: Blockchain integration
        private final double reputationScore;
        private final boolean isValidator;
        private final boolean isDACNode;
        private final boolean isCommitteeMember;
        private final int dacNodeId; // -1 if not DAC node
        private final Set<Integer> assignedShards;
        
        private volatile boolean isActive;
        private volatile long lastSeen;
        private volatile double latency;
        private volatile int validatorPower;
        
        // NEW: Sovereignty tracking
        private final AtomicLong crossRegionTx;
        private final AtomicLong asuTransactions;
        private final AtomicLong treasurySubsidizedTx;
        
        public Node(String nodeId, String ipAddress, AfricanRegion region,
                   double reputationScore, boolean isValidator, boolean isDACNode,
                   boolean isCommitteeMember, int dacNodeId) {
            this.nodeId = nodeId;
            this.ipAddress = ipAddress;
            this.region = region;
            this.reputationScore = reputationScore;
            this.isValidator = isValidator;
            this.isDACNode = isDACNode;
            this.isCommitteeMember = isCommitteeMember;
            this.dacNodeId = dacNodeId;
            this.assignedShards = ConcurrentHashMap.newKeySet();
            
            this.isActive = true;
            this.lastSeen = System.currentTimeMillis();
            this.latency = 0.0;
            this.validatorPower = 1;
            
            this.crossRegionTx = new AtomicLong(0);
            this.asuTransactions = new AtomicLong(0);
            this.treasurySubsidizedTx = new AtomicLong(0);
        }
        
        // Getters
        public String getNodeId() { return nodeId; }
        public String getIpAddress() { return ipAddress; }
        public AfricanRegion getRegion() { return region; }
        public double getReputationScore() { return reputationScore; }
        public boolean isValidator() { return isValidator; }
        public boolean isDACNode() { return isDACNode; }
        public boolean isCommitteeMember() { return isCommitteeMember; }
        public int getDACNodeId() { return dacNodeId; }
        public Set<Integer> getAssignedShards() { return new HashSet<>(assignedShards); }
        public boolean isActive() { return isActive; }
        public long getLastSeen() { return lastSeen; }
        public double getLatency() { return latency; }
        public int getValidatorPower() { return validatorPower; }
        
        // Sovereignty metrics
        public long getCrossRegionTx() { return crossRegionTx.get(); }
        public long getASUTransactions() { return asuTransactions.get(); }
        public long getTreasurySubsidizedTx() { return treasurySubsidizedTx.get(); }
        
        public void setActive(boolean active) { this.isActive = active; }
        public void updateLastSeen() { this.lastSeen = System.currentTimeMillis(); }
        public void updateLatency(double latency) { this.latency = latency; }
        public void setValidatorPower(int power) { this.validatorPower = Math.max(1, power); }
        
        public void assignShard(int shardId) { assignedShards.add(shardId); }
        public void unassignShard(int shardId) { assignedShards.remove(shardId); }
        
        public void recordCrossRegion() { crossRegionTx.incrementAndGet(); }
        public void recordASU() { asuTransactions.incrementAndGet(); }
        public void recordTreasurySubsidy() { treasurySubsidizedTx.incrementAndGet(); }
        
        /**
         * NEW: Checks if node meets reputation requirements
         */
        public boolean meetsReputationRequirement() {
            if (isValidator) return reputationScore >= MIN_VALIDATOR_REPUTATION;
            if (isDACNode) return reputationScore >= MIN_DAC_REPUTATION;
            if (isCommitteeMember) return reputationScore >= MIN_COMMITTEE_REPUTATION;
            return true;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Node)) return false;
            Node node = (Node) o;
            return Objects.equals(nodeId, node.nodeId);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(nodeId);
        }
    }
    
    /**
     * Cluster health status
     */
    public enum ClusterHealth {
        HEALTHY,        // All nodes operational
        DEGRADED,       // Some nodes offline
        CRITICAL,       // Below minimum nodes
        OFFLINE         // No active nodes
    }
    
    /**
     * Constructor
     */
    public RegionalNodeCluster() {
        this.regionalClusters = new EnumMap<>(AfricanRegion.class);
        this.nodeRegions = new ConcurrentHashMap<>();
        this.healthMonitor = Executors.newScheduledThreadPool(1);
        
        this.shardNodes = new ConcurrentHashMap<>();
        this.nodeShards = new ConcurrentHashMap<>();
        
        this.dacNodeRegistry = new ConcurrentHashMap<>();
        this.dacNodesByRegion = new EnumMap<>(AfricanRegion.class);
        
        this.committeeMembers = new ConcurrentHashMap<>();
        this.activeCommitteeMembers = ConcurrentHashMap.newKeySet();
        
        this.regionalMetrics = new EnumMap<>(AfricanRegion.class);
        
        // Initialize regions
        for (AfricanRegion region : AfricanRegion.values()) {
            regionalClusters.put(region, new NodeCluster(region));
            dacNodesByRegion.put(region, ConcurrentHashMap.newKeySet());
            regionalMetrics.put(region, new SovereigntyMetrics(region));
        }
        
        // Initialize shards (20 total, 4 per region)
        for (int i = 0; i < 20; i++) {
            shardNodes.put(i, ConcurrentHashMap.newKeySet());
        }
        
        logger.info("RegionalNodeCluster initialized with 5 African regions, 20 shards, 35 DAC nodes");
    }
    
    /**
     * Registers a node in a region (enhanced)
     */
    public synchronized void registerNode(String nodeId, String ipAddress, AfricanRegion region,
                                         double reputationScore, boolean isValidator,
                                         boolean isDACNode, boolean isCommitteeMember,
                                         int dacNodeId) {
        if (nodeId == null || ipAddress == null || region == null) {
            throw new IllegalArgumentException("Node parameters cannot be null");
        }
        
        // Verify reputation requirements
        if (isValidator && reputationScore < MIN_VALIDATOR_REPUTATION) {
            throw new IllegalArgumentException("Validator reputation too low: " + reputationScore);
        }
        if (isDACNode && reputationScore < MIN_DAC_REPUTATION) {
            throw new IllegalArgumentException("DAC node reputation too low: " + reputationScore);
        }
        if (isCommitteeMember && reputationScore < MIN_COMMITTEE_REPUTATION) {
            throw new IllegalArgumentException("Committee member reputation too low: " + reputationScore);
        }
        
        // Check regional control limit
        if (!canAddNodeToRegion(region)) {
            throw new IllegalStateException(
                "Cannot add node to " + region.getName() + " - would exceed 33% regional control limit"
            );
        }
        
        // Create and add node
        Node node = new Node(nodeId, ipAddress, region, reputationScore,
                           isValidator, isDACNode, isCommitteeMember, dacNodeId);
        
        NodeCluster cluster = regionalClusters.get(region);
        cluster.addNode(node);
        
        // Track node regions
        nodeRegions.computeIfAbsent(nodeId, k -> ConcurrentHashMap.newKeySet()).add(region);
        
        // Register DAC node
        if (isDACNode && dacNodeId >= 0 && dacNodeId < TOTAL_DAC_NODES) {
            dacNodeRegistry.put(dacNodeId, nodeId);
            dacNodesByRegion.get(region).add(dacNodeId);
        }
        
        // Register committee member
        if (isCommitteeMember) {
            CommitteeInfo info = new CommitteeInfo(nodeId, region);
            committeeMembers.put(nodeId, info);
            activeCommitteeMembers.add(nodeId);
        }
        
        logger.info(String.format("Registered node %s in %s (Validator: %b, DAC: %b, Committee: %b, Reputation: %.2f)",
            nodeId, region.getName(), isValidator, isDACNode, isCommitteeMember, reputationScore));
    }
    
    /**
     * Simplified registration for backward compatibility
     */
    public synchronized void registerNode(String nodeId, String ipAddress, String regionStr) {
        AfricanRegion region = AfricanRegion.fromString(regionStr);
        registerNode(nodeId, ipAddress, region, 0.50, false, false, false, -1);
    }
    
    /**
     * Checks if a node can be added to a region without violating control limit
     */
    private boolean canAddNodeToRegion(AfricanRegion region) {
        long totalNodes = regionalClusters.values().stream()
            .mapToLong(c -> c.getNodes().size())
            .sum();
        
        if (totalNodes == 0) {
            return true;
        }
        
        NodeCluster cluster = regionalClusters.get(region);
        long regionNodes = cluster.getNodes().size();
        
        double futureControl = (double) (regionNodes + 1) / (totalNodes + 1);
        
        return futureControl <= MAX_REGIONAL_CONTROL;
    }
    
    /**
     * Unregisters a node
     */
    public synchronized void unregisterNode(String nodeId) {
        Set<AfricanRegion> regions = nodeRegions.remove(nodeId);
        
        if (regions != null) {
            for (AfricanRegion region : regions) {
                NodeCluster cluster = regionalClusters.get(region);
                if (cluster != null) {
                    for (Node node : cluster.getNodes()) {
                        if (node.getNodeId().equals(nodeId)) {
                            cluster.removeNode(node);
                            
                            // Remove from DAC registry
                            if (node.isDACNode()) {
                                dacNodeRegistry.remove(node.getDACNodeId());
                                dacNodesByRegion.get(region).remove(node.getDACNodeId());
                            }
                            
                            // Remove from committee
                            if (node.isCommitteeMember()) {
                                committeeMembers.remove(nodeId);
                                activeCommitteeMembers.remove(nodeId);
                            }
                            
                            // Remove shard assignments
                            nodeShards.remove(nodeId);
                            for (int shardId : node.getAssignedShards()) {
                                shardNodes.get(shardId).remove(nodeId);
                            }
                            
                            break;
                        }
                    }
                }
            }
        }
        
        logger.info("Unregistered node " + nodeId);
    }
    
    /**
     * NEW: Assigns a node to a shard
     */
    public void assignNodeToShard(String nodeId, int shardId) {
        if (shardId < 0 || shardId >= 20) {
            throw new IllegalArgumentException("Invalid shard ID: " + shardId);
        }
        
        Set<AfricanRegion> regions = nodeRegions.get(nodeId);
        if (regions != null) {
            for (AfricanRegion region : regions) {
                NodeCluster cluster = regionalClusters.get(region);
                if (cluster != null) {
                    for (Node node : cluster.getNodes()) {
                        if (node.getNodeId().equals(nodeId)) {
                            node.assignShard(shardId);
                            shardNodes.get(shardId).add(nodeId);
                            nodeShards.computeIfAbsent(nodeId, k -> ConcurrentHashMap.newKeySet())
                                     .add(shardId);
                            logger.fine("Assigned node " + nodeId + " to shard " + shardId);
                            return;
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Updates node status
     */
    public void updateNodeStatus(String nodeId, boolean isActive) {
        Set<AfricanRegion> regions = nodeRegions.get(nodeId);
        
        if (regions != null) {
            for (AfricanRegion region : regions) {
                NodeCluster cluster = regionalClusters.get(region);
                if (cluster != null) {
                    for (Node node : cluster.getNodes()) {
                        if (node.getNodeId().equals(nodeId)) {
                            node.setActive(isActive);
                            node.updateLastSeen();
                            break;
                        }
                    }
                    cluster.updateCounts();
                }
            }
        }
    }
    
    /**
     * Updates node latency
     */
    public void updateNodeLatency(String nodeId, double latency) {
        Set<AfricanRegion> regions = nodeRegions.get(nodeId);
        
        if (regions != null) {
            for (AfricanRegion region : regions) {
                NodeCluster cluster = regionalClusters.get(region);
                if (cluster != null) {
                    for (Node node : cluster.getNodes()) {
                        if (node.getNodeId().equals(nodeId)) {
                            node.updateLatency(latency);
                            break;
                        }
                    }
                    updateClusterLatency(cluster);
                }
            }
        }
    }
    
    /**
     * Updates average latency for a cluster
     */
    private void updateClusterLatency(NodeCluster cluster) {
        double avgLatency = cluster.getNodes().stream()
            .filter(Node::isActive)
            .mapToDouble(Node::getLatency)
            .average()
            .orElse(0.0);
        
        cluster.updateLatency(avgLatency);
    }
    
    /**
     * NEW: Records cross-region transaction
     */
    public void recordCrossRegionTransaction(String nodeId, AfricanRegion region) {
        Set<AfricanRegion> regions = nodeRegions.get(nodeId);
        if (regions != null && regions.contains(region)) {
            NodeCluster cluster = regionalClusters.get(region);
            for (Node node : cluster.getNodes()) {
                if (node.getNodeId().equals(nodeId)) {
                    node.recordCrossRegion();
                    regionalMetrics.get(region).recordCrossRegion();
                    break;
                }
            }
        }
    }
    
    /**
     * NEW: Records ASU settlement
     */
    public void recordASUSettlement(String nodeId, AfricanRegion region) {
        Set<AfricanRegion> regions = nodeRegions.get(nodeId);
        if (regions != null && regions.contains(region)) {
            NodeCluster cluster = regionalClusters.get(region);
            for (Node node : cluster.getNodes()) {
                if (node.getNodeId().equals(nodeId)) {
                    node.recordASU();
                    regionalMetrics.get(region).recordASU();
                    break;
                }
            }
        }
    }
    
    /**
     * NEW: Records Treasury subsidy
     */
    public void recordTreasurySubsidy(String nodeId, AfricanRegion region) {
        Set<AfricanRegion> regions = nodeRegions.get(nodeId);
        if (regions != null && regions.contains(region)) {
            NodeCluster cluster = regionalClusters.get(region);
            for (Node node : cluster.getNodes()) {
                if (node.getNodeId().equals(nodeId)) {
                    node.recordTreasurySubsidy();
                    regionalMetrics.get(region).recordTreasurySubsidy();
                    break;
                }
            }
        }
    }
    
    /**
     * NEW: Records DAC attestation
     */
    public void recordDACAttestation(AfricanRegion region) {
        regionalMetrics.get(region).recordDACAttestation();
    }
    
    /**
     * NEW: Records committee signature
     */
    public void recordCommitteeSignature(String nodeId) {
        CommitteeInfo info = committeeMembers.get(nodeId);
        if (info != null) {
            info.incrementBatchesSubmitted();
            regionalMetrics.get(info.getRegion()).recordCommitteeSignature();
        }
    }
    
    /**
     * Gets nodes in a region
     */
    public Set<Node> getNodesInRegion(AfricanRegion region) {
        NodeCluster cluster = regionalClusters.get(region);
        return cluster != null ? cluster.getNodes() : Collections.emptySet();
    }
    
    /**
     * String-based region lookup for backward compatibility
     */
    public Set<Node> getNodesInRegion(String regionStr) {
        return getNodesInRegion(AfricanRegion.fromString(regionStr));
    }
    
    /**
     * Gets active nodes in a region
     */
    public Set<Node> getActiveNodesInRegion(AfricanRegion region) {
        return getNodesInRegion(region).stream()
            .filter(Node::isActive)
            .collect(Collectors.toSet());
    }
    
    /**
     * NEW: Gets DAC nodes in a region
     */
    public Set<Node> getDACNodesInRegion(AfricanRegion region) {
        return getNodesInRegion(region).stream()
            .filter(Node::isDACNode)
            .filter(Node::meetsReputationRequirement)
            .collect(Collectors.toSet());
    }
    
    /**
     * NEW: Gets committee members in a region
     */
    public Set<Node> getCommitteeMembersInRegion(AfricanRegion region) {
        return getNodesInRegion(region).stream()
            .filter(Node::isCommitteeMember)
            .filter(Node::meetsReputationRequirement)
            .collect(Collectors.toSet());
    }
    
    /**
     * NEW: Gets nodes assigned to a shard
     */
    public Set<String> getNodesForShard(int shardId) {
        return new HashSet<>(shardNodes.getOrDefault(shardId, Collections.emptySet()));
    }
    
    /**
     * Gets the closest region to a node
     */
    public AfricanRegion getClosestRegion(String nodeId) {
        Set<AfricanRegion> regions = nodeRegions.get(nodeId);
        if (regions != null && !regions.isEmpty()) {
            return regions.iterator().next();
        }
        
        // If node not found, return region with lowest latency
        return regionalClusters.entrySet().stream()
            .min(Comparator.comparingDouble(e -> e.getValue().getAverageLatency()))
            .map(Map.Entry::getKey)
            .orElse(AfricanRegion.WEST_AFRICA);
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
     * Performs health check on all clusters
     */
    private void performHealthCheck() {
        try {
            for (NodeCluster cluster : regionalClusters.values()) {
                cluster.updateCounts();
                
                int activeNodes = cluster.getActiveNodeCount();
                
                // Determine health status
                ClusterHealth health;
                if (activeNodes == 0) {
                    health = ClusterHealth.OFFLINE;
                } else if (activeNodes < MIN_NODES_PER_REGION) {
                    health = ClusterHealth.CRITICAL;
                } else if (activeNodes < cluster.getNodes().size() * 0.75) {
                    health = ClusterHealth.DEGRADED;
                } else {
                    health = ClusterHealth.HEALTHY;
                }
                
                cluster.updateHealth(health);
                
                if (health != ClusterHealth.HEALTHY) {
                    logger.warning(String.format("%s health: %s (active: %d/%d, validators: %d, DAC: %d, committee: %d)",
                        cluster.getRegion().getName(), health, activeNodes, cluster.getNodes().size(),
                        cluster.getValidatorCount(), cluster.getDACNodeCount(), cluster.getCommitteeNodeCount()));
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during health check", e);
        }
    }
    
    /**
     * Validates regional distribution meets 33% limit
     */
    public boolean validateRegionalDistribution() {
        long totalNodes = regionalClusters.values().stream()
            .mapToLong(c -> c.getNodes().size())
            .sum();
        
        if (totalNodes == 0) {
            return true;
        }
        
        for (NodeCluster cluster : regionalClusters.values()) {
            double control = (double) cluster.getNodes().size() / totalNodes;
            if (control > MAX_REGIONAL_CONTROL) {
                logger.severe(cluster.getRegion().getName() + " exceeds control limit: " +
                    String.format("%.2f%%", control * 100));
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Gets regional distribution statistics
     */
    public Map<String, Object> getRegionalDistribution() {
        Map<String, Object> distribution = new HashMap<>();
        
        long totalNodes = regionalClusters.values().stream()
            .mapToLong(c -> c.getNodes().size())
            .sum();
        
        for (Map.Entry<AfricanRegion, NodeCluster> entry : regionalClusters.entrySet()) {
            Map<String, Object> regionStats = new HashMap<>();
            NodeCluster cluster = entry.getValue();
            
            regionStats.put("totalNodes", cluster.getNodes().size());
            regionStats.put("activeNodes", cluster.getActiveNodeCount());
            regionStats.put("validators", cluster.getValidatorCount());
            regionStats.put("dacNodes", cluster.getDACNodeCount());
            regionStats.put("committeeMembers", cluster.getCommitteeNodeCount());
            regionStats.put("shards", cluster.getShardIds().size());
            regionStats.put("control", totalNodes > 0 ? 
                String.format("%.2f%%", (cluster.getNodes().size() * 100.0) / totalNodes) : "0.00%");
            regionStats.put("averageLatency", String.format("%.2f ms", cluster.getAverageLatency()));
            regionStats.put("health", cluster.getHealth().toString());
            
            // Sovereignty metrics
            SovereigntyMetrics metrics = regionalMetrics.get(entry.getKey());
            regionStats.put("crossRegionTx", metrics.getCrossRegionTransactions());
            regionStats.put("asuSettlements", metrics.getASUSettlements());
            regionStats.put("treasurySubsidized", metrics.getTreasurySubsidizedTx());
            regionStats.put("dacAttestations", metrics.getDACAttestations());
            regionStats.put("committeeSignatures", metrics.getCommitteeSignatures());
            
            distribution.put(entry.getKey().getName(), regionStats);
        }
        
        return distribution;
    }
    
    /**
     * Gets cluster statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalNodes = regionalClusters.values().stream()
            .mapToLong(c -> c.getNodes().size())
            .sum();
        
        long totalActiveNodes = regionalClusters.values().stream()
            .mapToInt(NodeCluster::getActiveNodeCount)
            .sum();
        
        long totalValidators = regionalClusters.values().stream()
            .mapToInt(NodeCluster::getValidatorCount)
            .sum();
        
        long totalDACNodes = regionalClusters.values().stream()
            .mapToInt(NodeCluster::getDACNodeCount)
            .sum();
        
        long totalCommitteeMembers = regionalClusters.values().stream()
            .mapToInt(NodeCluster::getCommitteeNodeCount)
            .sum();
        
        stats.put("totalNodes", totalNodes);
        stats.put("totalActiveNodes", totalActiveNodes);
        stats.put("totalValidators", totalValidators);
        stats.put("totalDACNodes", totalDACNodes);
        stats.put("totalCommitteeMembers", totalCommitteeMembers);
        stats.put("totalRegions", regionalClusters.size());
        stats.put("totalShards", 20);
        stats.put("regionalDistribution", getRegionalDistribution());
        stats.put("distributionValid", validateRegionalDistribution());
        
        // Health summary
        Map<ClusterHealth, Long> healthCounts = regionalClusters.values().stream()
            .collect(Collectors.groupingBy(NodeCluster::getHealth, Collectors.counting()));
        stats.put("healthSummary", healthCounts);
        
        // System-wide sovereignty metrics
        long totalCrossRegion = regionalMetrics.values().stream()
            .mapToLong(SovereigntyMetrics::getCrossRegionTransactions)
            .sum();
        long totalASU = regionalMetrics.values().stream()
            .mapToLong(SovereigntyMetrics::getASUSettlements)
            .sum();
        long totalSubsidized = regionalMetrics.values().stream()
            .mapToLong(SovereigntyMetrics::getTreasurySubsidizedTx)
            .sum();
        long totalDACAttestations = regionalMetrics.values().stream()
            .mapToLong(SovereigntyMetrics::getDACAttestations)
            .sum();
        long totalCommitteeSigs = regionalMetrics.values().stream()
            .mapToLong(SovereigntyMetrics::getCommitteeSignatures)
            .sum();
        
        stats.put("totalCrossRegionTx", totalCrossRegion);
        stats.put("totalASUSettlements", totalASU);
        stats.put("totalTreasurySubsidized", totalSubsidized);
        stats.put("totalDACAttestations", totalDACAttestations);
        stats.put("totalCommitteeSignatures", totalCommitteeSigs);
        
        return stats;
    }
    
    /**
     * Shuts down the cluster manager
     */
    public void shutdown() {
        logger.info("Shutting down RegionalNodeCluster");
        
        healthMonitor.shutdown();
        try {
            if (!healthMonitor.awaitTermination(10, TimeUnit.SECONDS)) {
                healthMonitor.shutdownNow();
            }
        } catch (InterruptedException e) {
            healthMonitor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("RegionalNodeCluster shut down");
    }
}