package org.example.app.core.scaling;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * CommunityNodeManager ensures minimum 50% community ownership of validators.
 * 
 * Community nodes are:
 * - Owned by individuals or African organizations (not corporations)
 * - NOT hosted on AWS/Azure/Google Cloud
 * - Run on local hardware or African data centers
 * - Have no dependencies on foreign entities
 * 
 * This is CRITICAL for sovereignty - prevents corporate/government takeover.
 */
public class CommunityNodeManager {
    private static final Logger logger = Logger.getLogger(CommunityNodeManager.class.getName());
    
    private static final double MIN_COMMUNITY_OWNERSHIP = 0.50; // 50%
    private static final double MAX_CLOUD_DEPENDENCY = 0.20;    // 20%
    
    // Node registries
    private final Set<String> communityNodes;
    private final Set<String> corporateNodes;
    private final Set<String> cloudHostedNodes;
    private final Set<String> selfHostedNodes;
    
    // Node details
    private final ConcurrentHashMap<String, CommunityNodeInfo> nodeRegistry;
    
    // Incentives tracking
    private final ConcurrentHashMap<String, Long> communityRewards;
    private final AtomicInteger communityBonusPool;
    
    public CommunityNodeManager() {
        this.communityNodes = ConcurrentHashMap.newKeySet();
        this.corporateNodes = ConcurrentHashMap.newKeySet();
        this.cloudHostedNodes = ConcurrentHashMap.newKeySet();
        this.selfHostedNodes = ConcurrentHashMap.newKeySet();
        this.nodeRegistry = new ConcurrentHashMap<>();
        this.communityRewards = new ConcurrentHashMap<>();
        this.communityBonusPool = new AtomicInteger(0);
    }
    
    /**
     * Register a community node
     */
    public boolean registerCommunityNode(String nodeId, String ownerName, String country, 
                                        HostingType hostingType, String hardwareDetails) {
        
        // Validate hosting type for community nodes
        if (hostingType == HostingType.AWS || hostingType == HostingType.AZURE || 
            hostingType == HostingType.GOOGLE_CLOUD) {
            logger.warning("Community nodes cannot be cloud-hosted");
            return false;
        }
        
        // Create node info
        CommunityNodeInfo nodeInfo = new CommunityNodeInfo(
            nodeId, ownerName, country, hostingType, hardwareDetails, true
        );
        
        // Register node
        nodeRegistry.put(nodeId, nodeInfo);
        communityNodes.add(nodeId);
        selfHostedNodes.add(nodeId);
        
        // Initialize rewards
        communityRewards.put(nodeId, 0L);
        
        logger.info("Registered community node: " + nodeId + " (Owner: " + ownerName + ", " + country + ")");
        
        // Check if we still meet minimum requirements
        return validateCommunityOwnershipRequirement();
    }
    
    /**
     * Register a corporate/institutional node
     */
    public boolean registerCorporateNode(String nodeId, String organization, String country,
                                        HostingType hostingType) {
        
        // Create node info
        CommunityNodeInfo nodeInfo = new CommunityNodeInfo(
            nodeId, organization, country, hostingType, "Corporate infrastructure", false
        );
        
        // Register node
        nodeRegistry.put(nodeId, nodeInfo);
        corporateNodes.add(nodeId);
        
        if (isCloudHosting(hostingType)) {
            cloudHostedNodes.add(nodeId);
        } else {
            selfHostedNodes.add(nodeId);
        }
        
        logger.info("Registered corporate node: " + nodeId + " (" + organization + ")");
        
        // Check if we still meet requirements
        if (!validateCommunityOwnershipRequirement()) {
            logger.warning("ALERT: Community ownership below 50% after adding corporate node");
        }
        
        if (!validateCloudDependencyLimit()) {
            logger.warning("ALERT: Cloud dependency above 20% limit");
        }
        
        return validateCommunityOwnershipRequirement() && validateCloudDependencyLimit();
    }
    
    /**
     * Validate 50% community ownership requirement
     */
    private boolean validateCommunityOwnershipRequirement() {
        int totalNodes = communityNodes.size() + corporateNodes.size();
        if (totalNodes == 0) return true;
        
        double communityPercentage = (double) communityNodes.size() / totalNodes;
        boolean meetsRequirement = communityPercentage >= MIN_COMMUNITY_OWNERSHIP;
        
        if (!meetsRequirement) {
            logger.severe("SOVEREIGNTY VIOLATION: Community ownership at " + 
                String.format("%.2f%%", communityPercentage * 100) + " (minimum 50%)");
        } else {
            logger.info("Community ownership: " + String.format("%.2f%%", communityPercentage * 100));
        }
        
        return meetsRequirement;
    }
    
    /**
     * Validate 20% cloud dependency limit
     */
    private boolean validateCloudDependencyLimit() {
        int totalNodes = communityNodes.size() + corporateNodes.size();
        if (totalNodes == 0) return true;
        
        double cloudPercentage = (double) cloudHostedNodes.size() / totalNodes;
        boolean meetsLimit = cloudPercentage <= MAX_CLOUD_DEPENDENCY;
        
        if (!meetsLimit) {
            logger.severe("SOVEREIGNTY VIOLATION: Cloud dependency at " + 
                String.format("%.2f%%", cloudPercentage * 100) + " (maximum 20%)");
        } else {
            logger.info("Cloud dependency: " + String.format("%.2f%%", cloudPercentage * 100));
        }
        
        return meetsLimit;
    }
    
    /**
     * Check if hosting type is cloud-based
     */
    private boolean isCloudHosting(HostingType type) {
        return type == HostingType.AWS || type == HostingType.AZURE || 
               type == HostingType.GOOGLE_CLOUD || type == HostingType.OTHER_CLOUD;
    }
    
    /**
     * Distribute community bonus rewards
     */
    public void distributeCommunityBonus(long totalRewards) {
        if (communityNodes.isEmpty()) return;
        
        // Community nodes get proportional share of bonus pool
        long bonusPerNode = totalRewards / communityNodes.size();
        
        for (String nodeId : communityNodes) {
            long currentRewards = communityRewards.getOrDefault(nodeId, 0L);
            communityRewards.put(nodeId, currentRewards + bonusPerNode);
        }
        
        communityBonusPool.addAndGet((int) totalRewards);
        logger.info("Distributed " + totalRewards + " bonus rewards to " + communityNodes.size() + " community nodes");
    }
    
    /**
     * Get community ownership statistics
     */
    public CommunityStats getStatistics() {
        int total = communityNodes.size() + corporateNodes.size();
        int community = communityNodes.size();
        int corporate = corporateNodes.size();
        int cloudHosted = cloudHostedNodes.size();
        int selfHosted = selfHostedNodes.size();
        
        double communityPercentage = total > 0 ? (double) community / total : 0.0;
        double cloudPercentage = total > 0 ? (double) cloudHosted / total : 0.0;
        
        return new CommunityStats(
            total, community, corporate, cloudHosted, selfHosted,
            communityPercentage, cloudPercentage,
            communityBonusPool.get()
        );
    }
    
    /**
     * Get node information
     */
    public CommunityNodeInfo getNodeInfo(String nodeId) {
        return nodeRegistry.get(nodeId);
    }
    
    /**
     * Check if node is community-owned
     */
    public boolean isCommunityNode(String nodeId) {
        return communityNodes.contains(nodeId);
    }
    
    /**
     * Get all community nodes
     */
    public Set<String> getCommunityNodes() {
        return new HashSet<>(communityNodes);
    }
    
    /**
     * Get nodes by country
     */
    public List<CommunityNodeInfo> getNodesByCountry(String country) {
        List<CommunityNodeInfo> nodes = new ArrayList<>();
        nodeRegistry.values().forEach(node -> {
            if (node.getCountry().equals(country)) {
                nodes.add(node);
            }
        });
        return nodes;
    }
    
    /**
     * Hosting type enumeration
     */
    public enum HostingType {
        SELF_HOSTED,           // Own hardware
        AFRICAN_DATA_CENTER,   // African-owned DC
        AWS,
        AZURE,
        GOOGLE_CLOUD,
        OTHER_CLOUD,
        MESH_NETWORK           // Community mesh
    }
    
    /**
     * Community node information
     */
    public static class CommunityNodeInfo {
        private final String nodeId;
        private final String owner;
        private final String country;
        private final HostingType hostingType;
        private final String hardwareDetails;
        private final boolean isCommunityOwned;
        private final long registrationTime;
        
        public CommunityNodeInfo(String nodeId, String owner, String country,
                               HostingType hostingType, String hardwareDetails,
                               boolean isCommunityOwned) {
            this.nodeId = nodeId;
            this.owner = owner;
            this.country = country;
            this.hostingType = hostingType;
            this.hardwareDetails = hardwareDetails;
            this.isCommunityOwned = isCommunityOwned;
            this.registrationTime = System.currentTimeMillis();
        }
        
        public String getNodeId() { return nodeId; }
        public String getOwner() { return owner; }
        public String getCountry() { return country; }
        public HostingType getHostingType() { return hostingType; }
        public String getHardwareDetails() { return hardwareDetails; }
        public boolean isCommunityOwned() { return isCommunityOwned; }
        public long getRegistrationTime() { return registrationTime; }
        
        @Override
        public String toString() {
            return String.format("%s (%s, %s) - %s [%s]",
                nodeId, owner, country, 
                isCommunityOwned ? "Community" : "Corporate",
                hostingType);
        }
    }
    
    /**
     * Community ownership statistics
     */
    public static class CommunityStats {
        private final int totalNodes;
        private final int communityNodes;
        private final int corporateNodes;
        private final int cloudHostedNodes;
        private final int selfHostedNodes;
        private final double communityPercentage;
        private final double cloudPercentage;
        private final long totalBonusDistributed;
        
        public CommunityStats(int totalNodes, int communityNodes, int corporateNodes,
                            int cloudHostedNodes, int selfHostedNodes,
                            double communityPercentage, double cloudPercentage,
                            long totalBonusDistributed) {
            this.totalNodes = totalNodes;
            this.communityNodes = communityNodes;
            this.corporateNodes = corporateNodes;
            this.cloudHostedNodes = cloudHostedNodes;
            this.selfHostedNodes = selfHostedNodes;
            this.communityPercentage = communityPercentage;
            this.cloudPercentage = cloudPercentage;
            this.totalBonusDistributed = totalBonusDistributed;
        }
        
        public int getTotalNodes() { return totalNodes; }
        public int getCommunityNodes() { return communityNodes; }
        public int getCorporateNodes() { return corporateNodes; }
        public int getCloudHostedNodes() { return cloudHostedNodes; }
        public int getSelfHostedNodes() { return selfHostedNodes; }
        public double getCommunityPercentage() { return communityPercentage; }
        public double getCloudPercentage() { return cloudPercentage; }
        public long getTotalBonusDistributed() { return totalBonusDistributed; }
        
        public boolean meetsSovereigntyRequirements() {
            return communityPercentage >= 0.50 && cloudPercentage <= 0.20;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Community Node Statistics:\n" +
                "  Total Nodes: %d\n" +
                "  Community Owned: %d (%.2f%%) %s\n" +
                "  Corporate: %d (%.2f%%)\n" +
                "  Self-Hosted: %d\n" +
                "  Cloud-Hosted: %d (%.2f%%) %s\n" +
                "  Total Bonus Distributed: %d\n" +
                "  Sovereignty Compliant: %s",
                totalNodes,
                communityNodes, communityPercentage * 100,
                communityPercentage >= 0.50 ? "✓" : "✗ BELOW 50%",
                corporateNodes, (1 - communityPercentage) * 100,
                selfHostedNodes,
                cloudHostedNodes, cloudPercentage * 100,
                cloudPercentage <= 0.20 ? "✓" : "✗ ABOVE 20%",
                totalBonusDistributed,
                meetsSovereigntyRequirements() ? "YES ✓" : "NO ✗"
            );
        }
    }
}
