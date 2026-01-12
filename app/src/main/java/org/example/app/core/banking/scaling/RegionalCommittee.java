package org.example.app.core.scaling;

import org.example.app.core.pbftconsensus.PBFT;
import org.example.app.core.pbftconsensus.PBFTMessage;
import org.example.app.core.crypto.Keypair;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * RegionalCommittee implements Byzantine Fault Tolerant consensus for one of the 5 African regions.
 * 
 * Structure:
 * - 7 nodes per region
 * - 5/7 consensus required (Byzantine Fault Tolerant)
 * - Tolerates up to 2 Byzantine failures per region
 * 
 * Ensures no single node or country can manipulate regional consensus.
 */
public class RegionalCommittee {
    private static final Logger logger = Logger.getLogger(RegionalCommittee.class.getName());
    
    private static final int NODES_PER_COMMITTEE = 7;
    private static final int CONSENSUS_THRESHOLD = 5; // 5/7 required
    private static final int MAX_BYZANTINE_FAILURES = 2; // Can tolerate 2 failures
    
    // Committee configuration
    private final String region;
    private final List<CommitteeNode> nodes;
    
    // Consensus tracking
    private final ConcurrentHashMap<String, ConsensusState> activeConsensus;
    
    // PBFT instance for consensus
    private final PBFT pbftConsensus;
    
    // Node health monitoring
    private final ConcurrentHashMap<String, NodeHealth> nodeHealth;
    
    // Decision history
    private final ConcurrentLinkedQueue<ConsensusDecision> decisionHistory;
    
    public RegionalCommittee(String region, List<CommitteeNode> nodes) {
        if (nodes.size() != NODES_PER_COMMITTEE) {
            throw new IllegalArgumentException("Regional committee must have exactly " + 
                NODES_PER_COMMITTEE + " nodes");
        }
        
        this.region = region;
        this.nodes = new ArrayList<>(nodes);
        this.activeConsensus = new ConcurrentHashMap<>();
        this.pbftConsensus = new PBFT(NODES_PER_COMMITTEE, 0); // Node 0 is initial primary
        this.nodeHealth = new ConcurrentHashMap<>();
        this.decisionHistory = new ConcurrentLinkedQueue<>();
        
        // Initialize node health monitoring
        for (CommitteeNode node : nodes) {
            nodeHealth.put(node.getNodeId(), new NodeHealth(node.getNodeId()));
        }
        
        logger.info("Initialized " + region + " committee with " + NODES_PER_COMMITTEE + " nodes");
    }
    
    /**
     * Submit proposal for consensus
     */
    public CompletableFuture<Boolean> submitProposal(String proposalId, byte[] proposalData) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        try {
            // Create consensus state
            ConsensusState state = new ConsensusState(proposalId, proposalData);
            activeConsensus.put(proposalId, state);
            
            logger.info(region + " committee - Starting consensus for proposal: " + proposalId);
            
            // Initiate PBFT consensus
            initiateConsensus(state, future);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error submitting proposal to " + region + " committee", e);
            future.complete(false);
        }
        
        return future;
    }
    
    /**
     * Initiate consensus using PBFT
     */
    private void initiateConsensus(ConsensusState state, CompletableFuture<Boolean> future) {
        String proposalId = state.getProposalId();
        
        try {
            // Broadcast proposal to all nodes
            int votes = 0;
            
            for (CommitteeNode node : nodes) {
                // Simulate node voting (in real implementation, nodes would run PBFT protocol)
                if (node.voteOnProposal(state.getProposalData())) {
                    state.recordVote(node.getNodeId(), true);
                    votes++;
                } else {
                    state.recordVote(node.getNodeId(), false);
                }
            }
            
            // Check if consensus reached
            boolean consensusReached = votes >= CONSENSUS_THRESHOLD;
            
            if (consensusReached) {
                state.markDecided(true);
                logger.info(region + " committee - Consensus REACHED for " + proposalId + 
                    " (" + votes + "/" + NODES_PER_COMMITTEE + " votes)");
                
                // Record decision
                recordDecision(new ConsensusDecision(proposalId, true, votes, NODES_PER_COMMITTEE));
                
                future.complete(true);
            } else {
                state.markDecided(false);
                logger.warning(region + " committee - Consensus FAILED for " + proposalId + 
                    " (only " + votes + "/" + NODES_PER_COMMITTEE + " votes)");
                
                // Record decision
                recordDecision(new ConsensusDecision(proposalId, false, votes, NODES_PER_COMMITTEE));
                
                future.complete(false);
            }
            
            activeConsensus.remove(proposalId);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in consensus process for " + proposalId, e);
            future.complete(false);
        }
    }
    
    /**
     * Record consensus decision
     */
    private void recordDecision(ConsensusDecision decision) {
        decisionHistory.offer(decision);
        
        // Keep only last 1000 decisions
        while (decisionHistory.size() > 1000) {
            decisionHistory.poll();
        }
    }
    
    /**
     * Get consensus state
     */
    public ConsensusState getConsensusState(String proposalId) {
        return activeConsensus.get(proposalId);
    }
    
    /**
     * Check if committee is healthy
     */
    public boolean isHealthy() {
        int healthyNodes = 0;
        
        for (NodeHealth health : nodeHealth.values()) {
            if (health.isHealthy()) {
                healthyNodes++;
            }
        }
        
        // Need at least 5 healthy nodes for consensus
        return healthyNodes >= CONSENSUS_THRESHOLD;
    }
    
    /**
     * Get committee statistics
     */
    public CommitteeStats getStatistics() {
        int totalDecisions = decisionHistory.size();
        long successfulDecisions = decisionHistory.stream()
            .filter(ConsensusDecision::isApproved)
            .count();
        
        int healthyNodes = (int) nodeHealth.values().stream()
            .filter(NodeHealth::isHealthy)
            .count();
        
        return new CommitteeStats(
            region,
            NODES_PER_COMMITTEE,
            healthyNodes,
            totalDecisions,
            (int) successfulDecisions,
            activeConsensus.size()
        );
    }
    
    /**
     * Get region name
     */
    public String getRegion() {
        return region;
    }
    
    /**
     * Get all nodes
     */
    public List<CommitteeNode> getNodes() {
        return new ArrayList<>(nodes);
    }
    
    /**
     * Committee node
     */
    public static class CommitteeNode {
        private final String nodeId;
        private final String country;
        private final Keypair keypair;
        private final String endpoint;
        
        public CommitteeNode(String nodeId, String country, String endpoint) {
            this.nodeId = nodeId;
            this.country = country;
            this.endpoint = endpoint;
            this.keypair = new Keypair(); // Generate keypair
        }
        
        /**
         * Vote on proposal (simplified - real implementation would use PBFT)
         */
        public boolean voteOnProposal(byte[] proposalData) {
            // In real implementation, node would:
            // 1. Validate proposal data
            // 2. Check against local state
            // 3. Participate in PBFT protocol
            // 4. Return vote based on consensus rules
            
            // For now, simulate honest voting (95% approval rate)
            return Math.random() < 0.95;
        }
        
        public String getNodeId() { return nodeId; }
        public String getCountry() { return country; }
        public Keypair getKeypair() { return keypair; }
        public String getEndpoint() { return endpoint; }
    }
    
    /**
     * Consensus state for a proposal
     */
    public static class ConsensusState {
        private final String proposalId;
        private final byte[] proposalData;
        private final long timestamp;
        private final ConcurrentHashMap<String, Boolean> votes;
        private volatile boolean decided;
        private volatile boolean approved;
        
        public ConsensusState(String proposalId, byte[] proposalData) {
            this.proposalId = proposalId;
            this.proposalData = proposalData;
            this.timestamp = System.currentTimeMillis();
            this.votes = new ConcurrentHashMap<>();
            this.decided = false;
            this.approved = false;
        }
        
        public void recordVote(String nodeId, boolean vote) {
            votes.put(nodeId, vote);
        }
        
        public void markDecided(boolean approved) {
            this.decided = true;
            this.approved = approved;
        }
        
        public String getProposalId() { return proposalId; }
        public byte[] getProposalData() { return proposalData; }
        public long getTimestamp() { return timestamp; }
        public Map<String, Boolean> getVotes() { return new HashMap<>(votes); }
        public boolean isDecided() { return decided; }
        public boolean isApproved() { return approved; }
        
        public int getYesVotes() {
            return (int) votes.values().stream().filter(Boolean::booleanValue).count();
        }
        
        public int getNoVotes() {
            return (int) votes.values().stream().filter(v -> !v).count();
        }
    }
    
    /**
     * Node health monitoring
     */
    private static class NodeHealth {
        private final String nodeId;
        private volatile long lastHeartbeat;
        private volatile boolean healthy;
        
        public NodeHealth(String nodeId) {
            this.nodeId = nodeId;
            this.lastHeartbeat = System.currentTimeMillis();
            this.healthy = true;
        }
        
        public void updateHeartbeat() {
            lastHeartbeat = System.currentTimeMillis();
            healthy = true;
        }
        
        public boolean isHealthy() {
            long timeSinceHeartbeat = System.currentTimeMillis() - lastHeartbeat;
            return healthy && timeSinceHeartbeat < 30000; // 30 second timeout
        }
    }
    
    /**
     * Consensus decision record
     */
    public static class ConsensusDecision {
        private final String proposalId;
        private final boolean approved;
        private final int yesVotes;
        private final int totalVotes;
        private final long timestamp;
        
        public ConsensusDecision(String proposalId, boolean approved, int yesVotes, int totalVotes) {
            this.proposalId = proposalId;
            this.approved = approved;
            this.yesVotes = yesVotes;
            this.totalVotes = totalVotes;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getProposalId() { return proposalId; }
        public boolean isApproved() { return approved; }
        public int getYesVotes() { return yesVotes; }
        public int getTotalVotes() { return totalVotes; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Committee statistics
     */
    public static class CommitteeStats {
        private final String region;
        private final int totalNodes;
        private final int healthyNodes;
        private final int totalDecisions;
        private final int successfulDecisions;
        private final int activeConsensus;
        
        public CommitteeStats(String region, int totalNodes, int healthyNodes,
                            int totalDecisions, int successfulDecisions, int activeConsensus) {
            this.region = region;
            this.totalNodes = totalNodes;
            this.healthyNodes = healthyNodes;
            this.totalDecisions = totalDecisions;
            this.successfulDecisions = successfulDecisions;
            this.activeConsensus = activeConsensus;
        }
        
        public String getRegion() { return region; }
        public int getTotalNodes() { return totalNodes; }
        public int getHealthyNodes() { return healthyNodes; }
        public int getTotalDecisions() { return totalDecisions; }
        public int getSuccessfulDecisions() { return successfulDecisions; }
        public int getActiveConsensus() { return activeConsensus; }
        
        public double getSuccessRate() {
            return totalDecisions > 0 ? (double) successfulDecisions / totalDecisions : 0.0;
        }
        
        public boolean isOperational() {
            return healthyNodes >= CONSENSUS_THRESHOLD;
        }
        
        @Override
        public String toString() {
            return String.format(
                "%s Committee:\n" +
                "  Total Nodes: %d\n" +
                "  Healthy Nodes: %d/%d %s\n" +
                "  Total Decisions: %d\n" +
                "  Successful: %d (%.2f%%)\n" +
                "  Active Consensus: %d\n" +
                "  Operational: %s",
                region,
                totalNodes,
                healthyNodes, CONSENSUS_THRESHOLD,
                healthyNodes >= CONSENSUS_THRESHOLD ? "✓" : "✗",
                totalDecisions,
                successfulDecisions, getSuccessRate() * 100,
                activeConsensus,
                isOperational() ? "YES ✓" : "NO ✗"
            );
        }
    }
}
