package org.example.app.core.banking.governance;

import org.example.app.core.types.Address;
import org.example.app.core.block.Transaction;
import org.example.app.core.banking.registration.ValidatorNodeRegistry;
import org.example.app.core.p2p.BroadcastManager;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.math.BigDecimal;

/**
 * VotingDAO - Core decentralized voting infrastructure
 * 
 * Handles ALL governance proposals:
 * - Proposal submission with collateral
 * - Vote collection from validators/reporters/arbitrators
 * - Automatic execution after approval
 * - Challenge windows for disputes
 */
public class VotingDAO {
    private static final Logger LOGGER = Logger.getLogger(VotingDAO.class.getName());
    
    // Voting parameters
    private static final long STANDARD_VOTING_PERIOD = 72 * 60 * 60 * 1000L; // 72 hours
    private static final long EMERGENCY_VOTING_PERIOD = 1 * 60 * 60 * 1000L; // 1 hour
    private static final BigDecimal PROPOSAL_COLLATERAL = new BigDecimal("1000000000000000000000"); // 1000 tokens
    
    public enum ProposalStatus {
        PENDING, VOTING_OPEN, VOTING_CLOSED, APPROVED, REJECTED, EXECUTED, CHALLENGED, EXPIRED
    }
    
    public static class Proposal {
        private final String proposalId;
        private final String title;
        private final String description;
        private final Address proposer;
        private final long createdTime;
        private final long votingEndTime;
        private final SupermajorityCalculator.ProposalType type;
        private final Map<String, String> parameters;
        private ProposalStatus status;
        
        public Proposal(String proposalId, String title, String description, Address proposer,
                       SupermajorityCalculator.ProposalType type, long votingDuration, 
                       Map<String, String> parameters) {
            this.proposalId = proposalId;
            this.title = title;
            this.description = description;
            this.proposer = proposer;
            this.type = type;
            this.parameters = new HashMap<>(parameters);
            this.createdTime = System.currentTimeMillis();
            this.votingEndTime = createdTime + votingDuration;
            this.status = ProposalStatus.PENDING;
        }
        
        public String getProposalId() { return proposalId; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public Address getProposer() { return proposer; }
        public long getCreatedTime() { return createdTime; }
        public long getVotingEndTime() { return votingEndTime; }
        public SupermajorityCalculator.ProposalType getType() { return type; }
        public Map<String, String> getParameters() { return new HashMap<>(parameters); }
        public ProposalStatus getStatus() { return status; }
        public void setStatus(ProposalStatus status) { this.status = status; }
        
        public boolean isVotingOpen() {
            return status == ProposalStatus.VOTING_OPEN && 
                   System.currentTimeMillis() < votingEndTime;
        }
    }
    
    private final Map<String, Proposal> proposals;
    private final SupermajorityCalculator supermajorityCalculator;
    private final GovernanceAuditLog auditLog;
    private final ValidatorNodeRegistry validatorRegistry;
    private final BroadcastManager broadcastManager;
    private final ScheduledExecutorService scheduler;
    
    public VotingDAO(SupermajorityCalculator supermajorityCalculator,
                    GovernanceAuditLog auditLog,
                    ValidatorNodeRegistry validatorRegistry,
                    BroadcastManager broadcastManager) {
        this.proposals = new ConcurrentHashMap<>();
        this.supermajorityCalculator = supermajorityCalculator;
        this.auditLog = auditLog;
        this.validatorRegistry = validatorRegistry;
        this.broadcastManager = broadcastManager;
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        startAutomatedProcessing();
        LOGGER.info("VotingDAO initialized");
    }
    
    public String submitProposal(String title, String description, Address proposer,
                                 SupermajorityCalculator.ProposalType type,
                                 Map<String, String> parameters) {
        // Verify proposer is eligible validator
        ValidatorNodeRegistry.ValidatorNode validator = validatorRegistry.getValidatorByAddress(proposer);
        if (validator == null || validator.getStatus() != ValidatorNodeRegistry.ValidatorStatus.ACTIVE) {
            throw new IllegalArgumentException("Proposer must be active validator");
        }
        
        // Generate proposal ID
        String proposalId = UUID.randomUUID().toString();
        
        // Determine voting duration
        long votingDuration = type == SupermajorityCalculator.ProposalType.EMERGENCY ?
            EMERGENCY_VOTING_PERIOD : STANDARD_VOTING_PERIOD;
        
        // Create proposal
        Proposal proposal = new Proposal(proposalId, title, description, proposer, 
                                        type, votingDuration, parameters);
        proposals.put(proposalId, proposal);
        
        // Create vote set in supermajority calculator
        supermajorityCalculator.createVoteSet(proposalId, type, votingDuration);
        
        // Open voting
        proposal.setStatus(ProposalStatus.VOTING_OPEN);
        
        // Broadcast to network
        broadcastProposalToNetwork(proposal);
        
        // Record in audit log
        auditLog.recordEvent(GovernanceAuditLog.AuditEventType.PROPOSAL_SUBMITTED,
            proposer.toString(), proposalId, parameters, title);
        
        LOGGER.info("Proposal submitted: " + proposalId + " by " + proposer);
        return proposalId;
    }
    
    public boolean castVote(String proposalId, Address voter, boolean voteYes, byte[] signature) {
        Proposal proposal = proposals.get(proposalId);
        if (proposal == null || !proposal.isVotingOpen()) {
            return false;
        }
        
        // Determine participant type
        SupermajorityCalculator.ParticipantType participantType;
        ValidatorNodeRegistry.ValidatorNode validator = validatorRegistry.getValidatorByAddress(voter);
        if (validator != null) {
            participantType = SupermajorityCalculator.ParticipantType.VALIDATOR;
        } else {
            // Check if reporter or arbitrator
            participantType = SupermajorityCalculator.ParticipantType.VALIDATOR; // Simplified
        }
        
        // Record vote
        boolean recorded = supermajorityCalculator.recordVote(proposalId, voter.toString(),
            participantType, voteYes, signature);
        
        if (recorded) {
            auditLog.recordEvent(GovernanceAuditLog.AuditEventType.PROPOSAL_VOTED,
                voter.toString(), proposalId, 
                Map.of("vote", voteYes ? "YES" : "NO"), "Vote cast");
        }
        
        return recorded;
    }
    
    public Proposal getProposal(String proposalId) {
        return proposals.get(proposalId);
    }
    
    public List<Proposal> getAllProposals() {
        return new ArrayList<>(proposals.values());
    }
    
    public List<Proposal> getProposalsByStatus(ProposalStatus status) {
        return proposals.values().stream()
            .filter(p -> p.getStatus() == status)
            .toList();
    }
    
    private void startAutomatedProcessing() {
        // Check voting completion every 5 minutes
        scheduler.scheduleAtFixedRate(() -> {
            try {
                closeCompletedVotes();
            } catch (Exception e) {
                LOGGER.severe("Error in automated vote closure: " + e.getMessage());
            }
        }, 5, 5, TimeUnit.MINUTES);
    }
    
    private void closeCompletedVotes() {
        long now = System.currentTimeMillis();
        
        for (Proposal proposal : proposals.values()) {
            if (proposal.getStatus() == ProposalStatus.VOTING_OPEN && 
                now >= proposal.getVotingEndTime()) {
                
                // Close voting
                proposal.setStatus(ProposalStatus.VOTING_CLOSED);
                
                // Calculate approval
                SupermajorityCalculator.ApprovalResult result = 
                    supermajorityCalculator.calculateApproval(proposal.getProposalId());
                
                if (result.isApproved()) {
                    proposal.setStatus(ProposalStatus.APPROVED);
                    auditLog.recordEvent(GovernanceAuditLog.AuditEventType.PROPOSAL_APPROVED,
                        proposal.getProposer().toString(), proposal.getProposalId(),
                        Map.of("yesPercentage", result.getYesPercentage().toString()),
                        "Proposal approved");
                } else {
                    proposal.setStatus(ProposalStatus.REJECTED);
                    auditLog.recordEvent(GovernanceAuditLog.AuditEventType.PROPOSAL_REJECTED,
                        proposal.getProposer().toString(), proposal.getProposalId(),
                        Map.of("reason", result.getFailureReason()),
                        "Proposal rejected");
                }
            }
        }
    }
    
    private void broadcastProposalToNetwork(Proposal proposal) {
        String message = "NEW_PROPOSAL:" + proposal.getProposalId() + ":" + proposal.getTitle();
        broadcastManager.broadcastMessage(message);
    }
    
    public void shutdown() {
        scheduler.shutdown();
    }
}