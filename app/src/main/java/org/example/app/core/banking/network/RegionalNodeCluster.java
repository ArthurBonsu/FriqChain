package org.example.app.core.banking.network;

import org.example.app.core.p2p.PeerNetwork;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * RegionalNodeCluster manages geographically distributed node clusters
 * to ensure government resistance (max 33% control from any region) and
 * optimize network latency for the African Tokenized Currency System.
 * 
 * Key Features:
 * - Geographic distribution across 5 African regions
 * - Enforces max 33% regional control
 * - Latency-optimized node selection
 * - Regional consensus coordination
 */
public class RegionalNodeCluster {
    private static final Logger logger = Logger.getLogger(RegionalNodeCluster.class.getName());
    
    // Configuration
    private static final double MAX_REGIONAL_CONTROL = 0.33; // 33% max
    private static final int MIN_NODES_PER_REGION = 3;
    private static final long HEALTH_CHECK_INTERVAL_MS = 10000; // 10 seconds
    
    // Regional clusters
    private final Map<String, NodeCluster> regionalClusters;
    private final Map<String, Set<String>> nodeRegions; // node ID → regions
    private final ScheduledExecutorService healthMonitor;
    
    // African regions
    private static final String[] AFRICAN_REGIONS = {
        "WEST_AFRICA",      // Nigeria, Ghana, Senegal, etc.
        "EAST_AFRICA",      // Kenya, Tanzania, Uganda, etc.
        "CENTRAL_AFRICA",   // DRC, Cameroon, etc.
        "SOUTHERN_AFRICA",  // South Africa, Zimbabwe, etc.
        "NORTH_AFRICA"      // Egypt, Morocco, Algeria, etc.
    };
    
    /**
     * Node cluster for a region
     */
    public static class NodeCluster {
        private final String region;
        private final Set<Node> nodes;
        private final AtomicInteger activeNodeCount;
        private double averageLatency;
        private ClusterHealth health;
        
        public NodeCluster(String region) {
            this.region = region;
            this.nodes = ConcurrentHashMap.newKeySet();
            this.activeNodeCount = new AtomicInteger(0);
            this.averageLatency = 0.0;
            this.health = ClusterHealth.HEALTHY;
        }
        
        public String getRegion() { return region; }
        public Set<Node> getNodes() { return new HashSet<>(nodes); }
        public int getActiveNodeCount() { return activeNodeCount.get(); }
        public double getAverageLatency() { return averageLatency; }
        public ClusterHealth getHealth() { return health; }
        
        public void addNode(Node node) {
            nodes.add(node);
            if (node.isActive()) {
                activeNodeCount.incrementAndGet();
            }
        }
        
        public void removeNode(Node node) {
            if (nodes.remove(node) && node.isActive()) {
                activeNodeCount.decrementAndGet();
            }
        }
        
        public void updateLatency(double latency) {
            this.averageLatency = latency;
        }
        
        public void updateHealth(ClusterHealth health) {
            this.health = health;
        }
        
        public void updateActiveCount() {
            activeNodeCount.set((int) nodes.stream().filter(Node::isActive).count());
        }
    }
    
    /**
     * Node representation
     */
    public static class Node {
        private final String nodeId;
        private final String ipAddress;
        private final String region;
        private boolean isActive;
        private long lastSeen;
        private double latency;
        private int validatorPower;
        
        public Node(String nodeId, String ipAddress, String region) {
            this.nodeId = nodeId;
            this.ipAddress = ipAddress;
            this.region = region;
            this.isActive = true;
            this.lastSeen = System.currentTimeMillis();
            this.latency = 0.0;
            this.validatorPower = 1;
        }
        
        public String getNodeId() { return nodeId; }
        public String getIpAddress() { return ipAddress; }
        public String getRegion() { return region; }
        public boolean isActive() { return isActive; }
        public long getLastSeen() { return lastSeen; }
        public double getLatency() { return latency; }
        public int getValidatorPower() { return validatorPower; }
        
        public void setActive(boolean active) {
            this.isActive = active;
        }
        
        public void updateLastSeen() {
            this.lastSeen = System.currentTimeMillis();
        }
        
        public void updateLatency(double latency) {
            this.latency = latency;
        }
        
        public void setValidatorPower(int power) {
            this.validatorPower = Math.max(1, power);
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
        this.regionalClusters = new ConcurrentHashMap<>();
        this.nodeRegions = new ConcurrentHashMap<>();
        this.healthMonitor = Executors.newScheduledThreadPool(1);
        
        // Initialize regions
        for (String region : AFRICAN_REGIONS) {
            regionalClusters.put(region, new NodeCluster(region));
        }
        
        logger.info("RegionalNodeCluster initialized with " + AFRICAN_REGIONS.length + " regions");
    }
    
    /**
     * Registers a node in a region
     */
    public synchronized void registerNode(String nodeId, String ipAddress, String region) {
        if (nodeId == null || ipAddress == null || region == null) {
            throw new IllegalArgumentException("Node parameters cannot be null");
        }
        
        // Verify region exists
        NodeCluster cluster = regionalClusters.get(region);
        if (cluster == null) {
            throw new IllegalArgumentException("Unknown region: " + region);
        }
        
        // Check regional control limit
        if (!canAddNodeToRegion(region)) {
            throw new IllegalStateException(
                "Cannot add node to " + region + " - would exceed 33% regional control limit"
            );
        }
        
        // Create and add node
        Node node = new Node(nodeId, ipAddress, region);
        cluster.addNode(node);
        
        // Track node regions
        nodeRegions.computeIfAbsent(nodeId, k -> ConcurrentHashMap.newKeySet()).add(region);
        
        logger.info("Registered node " + nodeId + " in region " + region);
    }
    
    /**
     * Checks if a node can be added to a region without violating control limit
     */
    private boolean canAddNodeToRegion(String region) {
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
        Set<String> regions = nodeRegions.remove(nodeId);
        
        if (regions != null) {
            for (String region : regions) {
                NodeCluster cluster = regionalClusters.get(region);
                if (cluster != null) {
                    cluster.getNodes().removeIf(n -> n.getNodeId().equals(nodeId));
                    cluster.updateActiveCount();
                }
            }
            
            logger.info("Unregistered node " + nodeId);
        }
    }
    
    /**
     * Updates node status
     */
    public void updateNodeStatus(String nodeId, boolean isActive) {
        Set<String> regions = nodeRegions.get(nodeId);
        
        if (regions != null) {
            for (String region : regions) {
                NodeCluster cluster = regionalClusters.get(region);
                if (cluster != null) {
                    for (Node node : cluster.getNodes()) {
                        if (node.getNodeId().equals(nodeId)) {
                            node.setActive(isActive);
                            node.updateLastSeen();
                            break;
                        }
                    }
                    cluster.updateActiveCount();
                }
            }
        }
    }
    
    /**
     * Updates node latency
     */
    public void updateNodeLatency(String nodeId, double latency) {
        Set<String> regions = nodeRegions.get(nodeId);
        
        if (regions != null) {
            for (String region : regions) {
                NodeCluster cluster = regionalClusters.get(region);
                if (cluster != null) {
                    for (Node node : cluster.getNodes()) {
                        if (node.getNodeId().equals(nodeId)) {
                            node.updateLatency(latency);
                            break;
                        }
                    }
                    // Recalculate cluster average latency
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
     * Gets nodes in a region
     */
    public Set<Node> getNodesInRegion(String region) {
        NodeCluster cluster = regionalClusters.get(region);
        return cluster != null ? cluster.getNodes() : Collections.emptySet();
    }
    
    /**
     * Gets active nodes in a region
     */
    public Set<Node> getActiveNodesInRegion(String region) {
        return getNodesInRegion(region).stream()
            .filter(Node::isActive)
            .collect(Collectors.toSet());
    }
    
    /**
     * Gets the closest region to a node
     */
    public String getClosestRegion(String nodeId) {
        Set<String> nodeRegions = this.nodeRegions.get(nodeId);
        if (nodeRegions != null && !nodeRegions.isEmpty()) {
            return nodeRegions.iterator().next();
        }
        
        // If node not found, return region with lowest latency
        return regionalClusters.entrySet().stream()
            .min(Comparator.comparingDouble(e -> e.getValue().getAverageLatency()))
            .map(Map.Entry::getKey)
            .orElse(AFRICAN_REGIONS[0]);
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
                // Update active count
                cluster.updateActiveCount();
                
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
                    logger.warning("Region " + cluster.getRegion() + " health: " + health +
                        " (active: " + activeNodes + "/" + cluster.getNodes().size() + ")");
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
                logger.severe("Region " + cluster.getRegion() + " exceeds control limit: " +
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
        
        for (Map.Entry<String, NodeCluster> entry : regionalClusters.entrySet()) {
            Map<String, Object> regionStats = new HashMap<>();
            NodeCluster cluster = entry.getValue();
            
            regionStats.put("totalNodes", cluster.getNodes().size());
            regionStats.put("activeNodes", cluster.getActiveNodeCount());
            regionStats.put("control", totalNodes > 0 ? 
                String.format("%.2f%%", (cluster.getNodes().size() * 100.0) / totalNodes) : "0.00%");
            regionStats.put("averageLatency", String.format("%.2f ms", cluster.getAverageLatency()));
            regionStats.put("health", cluster.getHealth().toString());
            
            distribution.put(entry.getKey(), regionStats);
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
        
        stats.put("totalNodes", totalNodes);
        stats.put("totalActiveNodes", totalActiveNodes);
        stats.put("totalRegions", regionalClusters.size());
        stats.put("regionalDistribution", getRegionalDistribution());
        stats.put("distributionValid", validateRegionalDistribution());
        
        // Health summary
        Map<ClusterHealth, Long> healthCounts = regionalClusters.values().stream()
            .collect(Collectors.groupingBy(NodeCluster::getHealth, Collectors.counting()));
        stats.put("healthSummary", healthCounts);
        
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