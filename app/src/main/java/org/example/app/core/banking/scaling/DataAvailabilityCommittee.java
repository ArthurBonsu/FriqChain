package org.example.app.core.sovereignty;

import org.example.app.core.scaling.ValidiumEngine;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * AFRICAN DATA AVAILABILITY COMMITTEE
 * 
 * Implements the 35-node African DA layer:
 * - 5 Regional Committees (7 nodes each)
 * - Byzantine Fault Tolerant (requires 23/35 confirmations)
 * - Geographic distribution enforced
 * - NO external dependency (NOT Celestia/EigenDA)
 * 
 * Regional Distribution:
 * - North Africa:   7 nodes (Morocco, Egypt, Algeria, Tunisia, Libya)
 * - West Africa:    7 nodes (Nigeria, Ghana, Senegal, Ivory Coast, Mali)
 * - East Africa:    7 nodes (Kenya, Ethiopia, Tanzania, Uganda, Rwanda)
 * - Southern Africa: 7 nodes (SA, Botswana, Namibia, Zimbabwe, Zambia)
 * - Central Africa:  7 nodes (Cameroon, DRC, Gabon, Chad, CAR)
 */
public class DataAvailabilityCommittee {
    private static final Logger LOGGER = Logger.getLogger(DataAvailabilityCommittee.class.getName());
    
    private static final int TOTAL_NODES = 35;
    private static final int NODES_PER_REGION = 7;
    private static final int BFT_THRESHOLD = 23; // 2/3 of 35
    private static final int REGIONAL_QUORUM = 5; // 5/7 per region
    
    private final Map<ValidiumEngine.AfricanRegion, RegionalCommittee> regionalCommittees;
    private final CommunityNodeRegistry nodeRegistry;
    
    public DataAvailabilityCommittee(CommunityNodeRegistry nodeRegistry) {
        this.nodeRegistry = nodeRegistry;
        this.regionalCommittees = new ConcurrentHashMap<>();
        
        // Initialize regional committees
        for (ValidiumEngine.AfricanRegion region : ValidiumEngine.AfricanRegion.values()) {
            regionalCommittees.put(region, new RegionalCommittee(region));
        }
        
        LOGGER.info("African Data Availability Committee initialized: 35 nodes, 5 regions");
    }
    
    /**
     * Register DA node with regional committee
     */
    public boolean registerDANode(ValidiumEngine.AfricanRegion region, 
                                  ValidiumEngine.DANode node) throws Exception {
        RegionalCommittee committee = regionalCommittees.get(region);
        if (committee == null) {
            throw new IllegalArgumentException("Invalid region: " + region);
        }
        
        if (committee.getNodeCount() >= NODES_PER_REGION) {
            throw new Exception("Regional committee full: " + region);
        }
        
        committee.registerNode(node);
        LOGGER.info(String.format(
            "DA node registered: region=%s, nodeId=%s, total=%d/%d",
            region, node.getNodeId(), committee.getNodeCount(), NODES_PER_REGION
        ));
        
        return true;
    }
    
    /**
     * Check if DA committee is fully operational
     */
    public boolean isOperational() {
        int totalNodes = 0;
        int operationalRegions = 0;
        
        for (RegionalCommittee committee : regionalCommittees.values()) {
            int nodeCount = committee.getNodeCount();
            totalNodes += nodeCount;
            
            if (nodeCount >= REGIONAL_QUORUM) {
                operationalRegions++;
            }
        }
        
        // Need at least 4/5 regions with quorum and total >= BFT threshold
        return operationalRegions >= 4 && totalNodes >= BFT_THRESHOLD;
    }
    
    /**
     * Get committee status
     */
    public CommitteeStatus getStatus() {
        Map<ValidiumEngine.AfricanRegion, Integer> nodeCounts = new HashMap<>();
        
        for (Map.Entry<ValidiumEngine.AfricanRegion, RegionalCommittee> entry : 
                regionalCommittees.entrySet()) {
            nodeCounts.put(entry.getKey(), entry.getValue().getNodeCount());
        }
        
        return new CommitteeStatus(
            nodeCounts,
            isOperational()
        );
    }
    
    private static class RegionalCommittee {
        private final ValidiumEngine.AfricanRegion region;
        private final Set<ValidiumEngine.DANode> nodes;
        
        public RegionalCommittee(ValidiumEngine.AfricanRegion region) {
            this.region = region;
            this.nodes = ConcurrentHashMap.newKeySet();
        }
        
        public void registerNode(ValidiumEngine.DANode node) {
            nodes.add(node);
        }
        
        public int getNodeCount() {
            return nodes.size();
        }
    }
    
    public static class CommitteeStatus {
        private final Map<ValidiumEngine.AfricanRegion, Integer> nodeCounts;
        private final boolean operational;
        
        public CommitteeStatus(Map<ValidiumEngine.AfricanRegion, Integer> nodeCounts,
                              boolean operational) {
            this.nodeCounts = nodeCounts;
            this.operational = operational;
        }
        
        public Map<ValidiumEngine.AfricanRegion, Integer> getNodeCounts() { return nodeCounts; }
        public boolean isOperational() { return operational; }
        
        @Override
        public String toString() {
            int total = nodeCounts.values().stream().mapToInt(Integer::intValue).sum();
            return String.format(
                "DACommittee[total=%d/%d, operational=%s]",
                total, TOTAL_NODES, operational
            );
        }
    }
}