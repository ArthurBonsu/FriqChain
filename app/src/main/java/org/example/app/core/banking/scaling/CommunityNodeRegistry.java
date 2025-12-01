package org.example.app.core.sovereignty;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * COMMUNITY NODE REGISTRY
 * 
 * Manages community-owned validator nodes to ensure decentralization
 * and prevent capture by governments or corporations.
 * 
 * Sovereignty Goals:
 * - Target: 50%+ of validators are community-owned
 * - Max 20% from cloud providers (AWS/Azure/Google)
 * - Max 33% from any single country
 * - Max 33% from any single entity
 * 
 * Node Types:
 * - Community: Individual/cooperative owned
 * - Cloud: AWS/Azure/Google infrastructure
 * - Bank: Financial institution nodes
 * - Government: Treasury/Central Bank nodes
 */
public class CommunityNodeRegistry {
    private static final Logger LOGGER = Logger.getLogger(CommunityNodeRegistry.class.getName());
    
    // Sovereignty Limits
    private static final double MIN_COMMUNITY_PERCENTAGE = 0.50;  // 50%
    private static final double MAX_CLOUD_PERCENTAGE = 0.20;      // 20%
    private static final double MAX_SINGLE_COUNTRY = 0.33;        // 33%
    private static final double MAX_SINGLE_ENTITY = 0.33;         // 33%
    
    // Node Registry
    private final Map<String, ValidatorNode> registeredNodes;
    private final Map<NodeType, Set<String>> nodesByType;
    private final Map<String, Set<String>> nodesByCountry;
    private final Map<String, Set<String>> nodesByEntity;
    private final ReadWriteLock registryLock;
    
    // Metrics
    private final AtomicLong totalNodes;
    private final AtomicLong communityNodes;
    private final AtomicLong cloudNodes;
    private final AtomicLong bankNodes;
    private final AtomicLong governmentNodes;
    
    // Enforcement
    private volatile boolean enforcementEnabled;
    private final List<SovereigntyViolation> violations;
    
    public enum NodeType {
        COMMUNITY,      // Community-owned (individuals, cooperatives)
        CLOUD,          // Cloud provider infrastructure
        BANK,           // Financial institution
        GOVERNMENT,     // Treasury/Central Bank
        RETAIL_AGENT    // Rural retail agent
    }
    
    /**
     * Constructor
     */
    public CommunityNodeRegistry() {
        this.registeredNodes = new ConcurrentHashMap<>();
        this.nodesByType = new ConcurrentHashMap<>();
        this.nodesByCountry = new ConcurrentHashMap<>();
        this.nodesByEntity = new ConcurrentHashMap<>();
        this.registryLock = new ReentrantReadWriteLock();
        
        // Initialize type tracking
        for (NodeType type : NodeType.values()) {
            nodesByType.put(type, ConcurrentHashMap.newKeySet());
        }
        
        // Initialize metrics
        this.totalNodes = new AtomicLong(0);
        this.communityNodes = new AtomicLong(0);
        this.cloudNodes = new AtomicLong(0);
        this.bankNodes = new AtomicLong(0);
        this.governmentNodes = new AtomicLong(0);
        
        this.enforcementEnabled = true;
        this.violations = new CopyOnWriteArrayList<>();
        
        LOGGER.info("Community Node Registry initialized");
    }
    
    /**
     * Register new validator node
     */
    public NodeRegistrationResult registerNode(ValidatorNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        
        registryLock.writeLock().lock();
        try {
            // Check if node already registered
            if (registeredNodes.containsKey(node.getNodeId())) {
                return new NodeRegistrationResult(
                    false,
                    "Node already registered",
                    null
                );
            }
            
            // Check sovereignty constraints
            if (enforcementEnabled) {
                SovereigntyCheck check = checkSovereigntyConstraints(node);
                if (!check.isPassed()) {
                    LOGGER.warning(String.format(
                        "Node registration rejected: %s - %s",
                        node.getNodeId(), check.getReason()
                    ));
                    return new NodeRegistrationResult(
                        false,
                        check.getReason(),
                        check.getViolation()
                    );
                }
            }
            
            // Register node
            registeredNodes.put(node.getNodeId(), node);
            nodesByType.get(node.getType()).add(node.getNodeId());
            
            nodesByCountry.computeIfAbsent(node.getCountry(), k -> ConcurrentHashMap.newKeySet())
                .add(node.getNodeId());
            
            nodesByEntity.computeIfAbsent(node.getEntityId(), k -> ConcurrentHashMap.newKeySet())
                .add(node.getNodeId());
            
            // Update metrics
            totalNodes.incrementAndGet();
            updateTypeMetrics(node.getType(), 1);
            
            LOGGER.info(String.format(
                "Node registered: id=%s, type=%s, country=%s, entity=%s",
                node.getNodeId(), node.getType(), node.getCountry(), node.getEntityId()
            ));
            
            return new NodeRegistrationResult(true, "Registration successful", null);
            
        } finally {
            registryLock.writeLock().unlock();
        }
    }
    
    /**
     * Unregister validator node
     */
    public boolean unregisterNode(String nodeId) {
        registryLock.writeLock().lock();
        try {
            ValidatorNode node = registeredNodes.remove(nodeId);
            if (node == null) {
                return false;
            }
            
            nodesByType.get(node.getType()).remove(nodeId);
            nodesByCountry.get(node.getCountry()).remove(nodeId);
            nodesByEntity.get(node.getEntityId()).remove(nodeId);
            
            totalNodes.decrementAndGet();
            updateTypeMetrics(node.getType(), -1);
            
            LOGGER.info("Node unregistered: " + nodeId);
            return true;
            
        } finally {
            registryLock.writeLock().unlock();
        }
    }
    
    /**
     * Check sovereignty constraints for new node
     */
    private SovereigntyCheck checkSovereigntyConstraints(ValidatorNode newNode) {
        long total = totalNodes.get() + 1; // Include new node
        
        // Check community percentage (must be >= 50%)
        long communityCount = communityNodes.get();
        if (newNode.getType() == NodeType.COMMUNITY) {
            communityCount++;
        }
        double communityPercentage = (double) communityCount / total;
        
        if (communityPercentage < MIN_COMMUNITY_PERCENTAGE) {
            // Only allow community nodes if below threshold
            if (newNode.getType() != NodeType.COMMUNITY) {
                return new SovereigntyCheck(
                    false,
                    "Community node percentage below 50% - only community nodes accepted",
                    new SovereigntyViolation(
                        ViolationType.INSUFFICIENT_COMMUNITY,
                        communityPercentage,
                        MIN_COMMUNITY_PERCENTAGE
                    )
                );
            }
        }
        
        // Check cloud percentage (must be <= 20%)
        if (newNode.getType() == NodeType.CLOUD) {
            long cloudCount = cloudNodes.get() + 1;
            double cloudPercentage = (double) cloudCount / total;
            
            if (cloudPercentage > MAX_CLOUD_PERCENTAGE) {
                return new SovereigntyCheck(
                    false,
                    String.format("Cloud nodes exceed %.0f%% limit", MAX_CLOUD_PERCENTAGE * 100),
                    new SovereigntyViolation(
                        ViolationType.EXCESSIVE_CLOUD,
                        cloudPercentage,
                        MAX_CLOUD_PERCENTAGE
                    )
                );
            }
        }
        
        // Check country concentration (must be <= 33%)
        Set<String> countryNodes = nodesByCountry.getOrDefault(newNode.getCountry(), new HashSet<>());
        double countryPercentage = (double) (countryNodes.size() + 1) / total;
        
        if (countryPercentage > MAX_SINGLE_COUNTRY) {
            return new SovereigntyCheck(
                false,
                String.format("Country %s exceeds %.0f%% limit", 
                    newNode.getCountry(), MAX_SINGLE_COUNTRY * 100),
                new SovereigntyViolation(
                    ViolationType.COUNTRY_CONCENTRATION,
                    countryPercentage,
                    MAX_SINGLE_COUNTRY
                )
            );
        }
        
        // Check entity concentration (must be <= 33%)
        Set<String> entityNodes = nodesByEntity.getOrDefault(newNode.getEntityId(), new HashSet<>());
        double entityPercentage = (double) (entityNodes.size() + 1) / total;
        
        if (entityPercentage > MAX_SINGLE_ENTITY) {
            return new SovereigntyCheck(
                false,
                String.format("Entity %s exceeds %.0f%% limit",
                    newNode.getEntityId(), MAX_SINGLE_ENTITY * 100),
                new SovereigntyViolation(
                    ViolationType.ENTITY_CONCENTRATION,
                    entityPercentage,
                    MAX_SINGLE_ENTITY
                )
            );
        }
        
        return new SovereigntyCheck(true, "All sovereignty constraints satisfied", null);
    }
    
    /**
     * Get node by ID
     */
    public ValidatorNode getNode(String nodeId) {
        registryLock.readLock().lock();
        try {
            return registeredNodes.get(nodeId);
        } finally {
            registryLock.readLock().unlock();
        }
    }
    
    /**
     * Get all nodes of specific type
     */
    public Set<ValidatorNode> getNodesByType(NodeType type) {
        Set<ValidatorNode> nodes = new HashSet<>();
        
        registryLock.readLock().lock();
        try {
            for (String nodeId : nodesByType.get(type)) {
                ValidatorNode node = registeredNodes.get(nodeId);
                if (node != null) {
                    nodes.add(node);
                }
            }
        } finally {
            registryLock.readLock().unlock();
        }
        
        return nodes;
    }
    
    /**
     * Get all nodes in country
     */
    public Set<ValidatorNode> getNodesByCountry(String country) {
        Set<ValidatorNode> nodes = new HashSet<>();
        
        registryLock.readLock().lock();
        try {
            Set<String> nodeIds = nodesByCountry.get(country);
            if (nodeIds != null) {
                for (String nodeId : nodeIds) {
                    ValidatorNode node = registeredNodes.get(nodeId);
                    if (node != null) {
                        nodes.add(node);
                    }
                }
            }
        } finally {
            registryLock.readLock().unlock();
        }
        
        return nodes;
    }
    
    /**
     * Calculate sovereignty metrics
     */
    public SovereigntyMetrics calculateMetrics() {
        registryLock.readLock().lock();
        try {
            long total = totalNodes.get();
            
            if (total == 0) {
                return new SovereigntyMetrics(0, 0, 0, 0, 0, new HashMap<>(), new HashMap<>());
            }
            
            double communityPct = (double) communityNodes.get() / total;
            double cloudPct = (double) cloudNodes.get() / total;
            double bankPct = (double) bankNodes.get() / total;
            double govPct = (double) governmentNodes.get() / total;
            
            // Calculate country distribution
            Map<String, Double> countryDistribution = new HashMap<>();
            for (Map.Entry<String, Set<String>> entry : nodesByCountry.entrySet()) {
                double pct = (double) entry.getValue().size() / total;
                countryDistribution.put(entry.getKey(), pct);
            }
            
            // Calculate entity distribution
            Map<String, Double> entityDistribution = new HashMap<>();
            for (Map.Entry<String, Set<String>> entry : nodesByEntity.entrySet()) {
                double pct = (double) entry.getValue().size() / total;
                entityDistribution.put(entry.getKey(), pct);
            }
            
            return new SovereigntyMetrics(
                total,
                communityPct,
                cloudPct,
                bankPct,
                govPct,
                countryDistribution,
                entityDistribution
            );
            
        } finally {
            registryLock.readLock().unlock();
        }
    }
    
    /**
     * Check if sovereignty constraints are currently met
     */
    public boolean isSovereigntyMaintained() {
        SovereigntyMetrics metrics = calculateMetrics();
        
        return metrics.getCommunityPercentage() >= MIN_COMMUNITY_PERCENTAGE &&
               metrics.getCloudPercentage() <= MAX_CLOUD_PERCENTAGE &&
               metrics.getMaxCountryPercentage() <= MAX_SINGLE_COUNTRY &&
               metrics.getMaxEntityPercentage() <= MAX_SINGLE_ENTITY;
    }
    
    /**
     * Update type metrics
     */
    private void updateTypeMetrics(NodeType type, int delta) {
        switch (type) {
            case COMMUNITY -> communityNodes.addAndGet(delta);
            case CLOUD -> cloudNodes.addAndGet(delta);
            case BANK -> bankNodes.addAndGet(delta);
            case GOVERNMENT -> governmentNodes.addAndGet(delta);
        }
    }
    
    /**
     * Enable/disable enforcement
     */
    public void setEnforcementEnabled(boolean enabled) {
        this.enforcementEnabled = enabled;
        LOGGER.info("Sovereignty enforcement " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Get all violations
     */
    public List<SovereigntyViolation> getViolations() {
        return new ArrayList<>(violations);
    }
    
    // Inner classes
    
    public static class ValidatorNode {
        private final String nodeId;
        private final NodeType type;
        private final String country;
        private final String entityId;
        private final String endpoint;
        private final long registeredAt;
        
        public ValidatorNode(String nodeId, NodeType type, String country,
                            String entityId, String endpoint) {
            this.nodeId = nodeId;
            this.type = type;
            this.country = country;
            this.entityId = entityId;
            this.endpoint = endpoint;
            this.registeredAt = System.currentTimeMillis();
        }
        
        public String getNodeId() { return nodeId; }
        public NodeType getType() { return type; }
        public String getCountry() { return country; }
        public String getEntityId() { return entityId; }
        public String getEndpoint() { return endpoint; }
        public long getRegisteredAt() { return registeredAt; }
    }
    
    public enum ViolationType {
        INSUFFICIENT_COMMUNITY,
        EXCESSIVE_CLOUD,
        COUNTRY_CONCENTRATION,
        ENTITY_CONCENTRATION
    }
    
    public static class SovereigntyViolation {
        private final ViolationType type;
        private final double actualValue;
        private final double threshold;
        private final long timestamp;
        
        public SovereigntyViolation(ViolationType type, double actualValue, double threshold) {
            this.type = type;
            this.actualValue = actualValue;
            this.threshold = threshold;
            this.timestamp = System.currentTimeMillis();
        }
        
        public ViolationType getType() { return type; }
        public double getActualValue() { return actualValue; }
        public double getThreshold() { return threshold; }
        public long getTimestamp() { return timestamp; }
    }
    
    private static class SovereigntyCheck {
        private final boolean passed;
        private final String reason;
        private final SovereigntyViolation violation;
        
        public SovereigntyCheck(boolean passed, String reason, SovereigntyViolation violation) {
            this.passed = passed;
            this.reason = reason;
            this.violation = violation;
        }
        
        public boolean isPassed() { return passed; }
        public String getReason() { return reason; }
        public SovereigntyViolation getViolation() { return violation; }
    }
    
    public static class NodeRegistrationResult {
        private final boolean success;
        private final String message;
        private final SovereigntyViolation violation;
        
        public NodeRegistrationResult(boolean success, String message, 
                                     SovereigntyViolation violation) {
            this.success = success;
            this.message = message;
            this.violation = violation;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public SovereigntyViolation getViolation() { return violation; }
    }
    
    public static class SovereigntyMetrics {
        private final long totalNodes;
        private final double communityPercentage;
        private final double cloudPercentage;
        private final double bankPercentage;
        private final double governmentPercentage;
        private final Map<String, Double> countryDistribution;
        private final Map<String, Double> entityDistribution;
        
        public SovereigntyMetrics(long totalNodes, double communityPercentage,
                                 double cloudPercentage, double bankPercentage,
                                 double governmentPercentage,
                                 Map<String, Double> countryDistribution,
                                 Map<String, Double> entityDistribution) {
            this.totalNodes = totalNodes;
            this.communityPercentage = communityPercentage;
            this.cloudPercentage = cloudPercentage;
            this.bankPercentage = bankPercentage;
            this.governmentPercentage = governmentPercentage;
            this.countryDistribution = countryDistribution;
            this.entityDistribution = entityDistribution;
        }
        
        public long getTotalNodes() { return totalNodes; }
        public double getCommunityPercentage() { return communityPercentage; }
        public double getCloudPercentage() { return cloudPercentage; }
        public double getBankPercentage() { return bankPercentage; }
        public double getGovernmentPercentage() { return governmentPercentage; }
        public Map<String, Double> getCountryDistribution() { return countryDistribution; }
        public Map<String, Double> getEntityDistribution() { return entityDistribution; }
        
        public double getMaxCountryPercentage() {
            return countryDistribution.values().stream()
                .max(Double::compare)
                .orElse(0.0);
        }
        
        public double getMaxEntityPercentage() {
            return entityDistribution.values().stream()
                .max(Double::compare)
                .orElse(0.0);
        }
        
        @Override
        public String toString() {
            return String.format(
                "SovereigntyMetrics[total=%d, community=%.1f%%, cloud=%.1f%%, " +
                "maxCountry=%.1f%%, maxEntity=%.1f%%]",
                totalNodes,
                communityPercentage * 100,
                cloudPercentage * 100,
                getMaxCountryPercentage() * 100,
                getMaxEntityPercentage() * 100
            );
        }
    }
}