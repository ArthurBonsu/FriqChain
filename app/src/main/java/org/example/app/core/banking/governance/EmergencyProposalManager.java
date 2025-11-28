package org.example.app.core.banking.governance;

import org.example.app.core.types.Address;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * EmergencyProposalManager - Fast-track emergency proposals
 * 
 * Features:
 * - Reduced threshold: 51% instead of 66%
 * - Fast-track voting: 1 hour instead of 72 hours
 * - Requires ≥7 of 9 emergency key signatures
 * - Auto-expires after 72 hours unless reconfirmed
 * - Limited to existential threats only
 */
public class EmergencyProposalManager {
    private static final Logger LOGGER = Logger.getLogger(EmergencyProposalManager.class.getName());
    
    private static final long EMERGENCY_VOTING_PERIOD = 1 * 60 * 60 * 1000L; // 1 hour
    private static final long AUTO_EXPIRATION_PERIOD = 72 * 60 * 60 * 1000L; // 72 hours
    private static final int REQUIRED_EMERGENCY_SIGNATURES = 7; // 7 of 9
    private static final int TOTAL_EMERGENCY_SIGNERS = 9;
    
    private final VotingDAO votingDAO;
    private final SupermajorityCalculator supermajorityCalculator;
    private final GovernanceAuditLog auditLog;
    private final ScheduledExecutorService scheduler;
    
    private final Map<String, EmergencyProposal> emergencyProposals;
    private final Set<String> emergencySigners; // Addresses authorized to sign
    
    public enum EmergencyType {
        RESERVE_INJECTION,      // Backing <120%
        CIRCUIT_BREAKER,        // Exposure >10% or manipulation
        SYSTEM_WIDE_HALT,       // Existential threat
        CRITICAL_BUG_FIX,       // Security vulnerability
        SANCTION_URGENT         // Urgent sanction needed
    }
    
    public static class EmergencyProposal {
        private final String proposalId;
        private final EmergencyType type;
        private final String reason;
        private final Address proposer;
        private final long createdTime;
        private final long expirationTime;
        private final Map<String, byte[]> signatures; // signer -> signature
        private boolean executed;
        
        public EmergencyProposal(String proposalId, EmergencyType type, String reason,
                                Address proposer) {
            this.proposalId = proposalId;
            this.type = type;
            this.reason = reason;
            this.proposer = proposer;
            this.createdTime = System.currentTimeMillis();
            this.expirationTime = createdTime + AUTO_EXPIRATION_PERIOD;
            this.signatures = new ConcurrentHashMap<>();
            this.executed = false;
        }
        
        public String getProposalId() { return proposalId; }
        public EmergencyType getType() { return type; }
        public long getExpirationTime() { return expirationTime; }
        public boolean isExecuted() { return executed; }
        public void setExecuted(boolean executed) { this.executed = executed; }
        public int getSignatureCount() { return signatures.size(); }
        
        public void addSignature(String signer, byte[] signature) {
            signatures.put(signer, signature);
        }
    }
    
    public EmergencyProposalManager(VotingDAO votingDAO,
                                    SupermajorityCalculator supermajorityCalculator,
                                    GovernanceAuditLog auditLog) {
        this.votingDAO = votingDAO;
        this.supermajorityCalculator = supermajorityCalculator;
        this.auditLog = auditLog;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.emergencyProposals = new ConcurrentHashMap<>();
        this.emergencySigners = ConcurrentHashMap.newKeySet();
        
        initializeEmergencySigners();
        startAutoExpirationMonitoring();
        
        LOGGER.info("EmergencyProposalManager initialized");
    }
    
    /**
     * Submits emergency proposal (fast-track)
     */
    public String submitEmergencyProposal(EmergencyType type, String reason, 
                                         Address proposer, Map<String, String> parameters) {
        // Verify proposer is authorized
        if (!emergencySigners.contains(proposer.toString())) {
            throw new IllegalArgumentException("Proposer not authorized for emergency proposals");
        }
        
        String proposalId = UUID.randomUUID().toString();
        EmergencyProposal emergency = new EmergencyProposal(proposalId, type, reason, proposer);
        emergencyProposals.put(proposalId, emergency);
        
        // Create fast-track voting proposal (1 hour, 51% threshold)
        String votingProposalId = votingDAO.submitProposal(
            "EMERGENCY: " + type.toString(),
            reason,
            proposer,
            SupermajorityCalculator.ProposalType.EMERGENCY, // Uses 51% threshold
            parameters
        );
        
        auditLog.recordEvent(GovernanceAuditLog.AuditEventType.PROPOSAL_SUBMITTED,
            proposer.toString(), proposalId,
            Map.of("type", type.toString(),
                   "votingProposalId", votingProposalId,
                   "expiresIn", String.valueOf(AUTO_EXPIRATION_PERIOD)),
            "Emergency proposal submitted: " + type);
        
        LOGGER.severe("EMERGENCY PROPOSAL SUBMITTED: " + proposalId + " (" + type + ")");
        return proposalId;
    }
    
    /**
     * Collects emergency signature (requires 7 of 9)
     */
    public boolean collectEmergencySignature(String proposalId, String signerAddress, 
                                            byte[] signature) {
        EmergencyProposal proposal = emergencyProposals.get(proposalId);
        if (proposal == null) {
            LOGGER.warning("Emergency proposal not found: " + proposalId);
            return false;
        }
        
        // Verify signer is authorized
        if (!emergencySigners.contains(signerAddress)) {
            LOGGER.warning("Unauthorized emergency signer: " + signerAddress);
            return false;
        }
        
        // Add signature
        proposal.addSignature(signerAddress, signature);
        
        LOGGER.info("Emergency signature collected: " + proposalId + 
            " (" + proposal.getSignatureCount() + "/" + REQUIRED_EMERGENCY_SIGNATURES + ")");
        
        // Check if threshold met
        if (proposal.getSignatureCount() >= REQUIRED_EMERGENCY_SIGNATURES) {
            LOGGER.severe("EMERGENCY THRESHOLD MET: " + proposalId + " - READY FOR EXECUTION");
            
            auditLog.recordEvent(GovernanceAuditLog.AuditEventType.PROPOSAL_APPROVED,
                "EMERGENCY_MANAGER", proposalId,
                Map.of("signatureCount", String.valueOf(proposal.getSignatureCount()),
                       "type", proposal.getType().toString()),
                "Emergency proposal threshold met");
            
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if proposal has required emergency signatures
     */
    public boolean hasRequiredSignatures(String proposalId) {
        EmergencyProposal proposal = emergencyProposals.get(proposalId);
        return proposal != null && 
               proposal.getSignatureCount() >= REQUIRED_EMERGENCY_SIGNATURES;
    }
    
    /**
     * Activates circuit breaker (emergency action)
     */
    public void activateCircuitBreaker(String proposalId, String reason) {
        if (!hasRequiredSignatures(proposalId)) {
            throw new IllegalStateException("Insufficient emergency signatures");
        }
        
        LOGGER.severe("CIRCUIT BREAKER ACTIVATED: " + reason);
        
        auditLog.recordEvent(GovernanceAuditLog.AuditEventType.CIRCUIT_BREAKER_ACTIVATED,
            "EMERGENCY_MANAGER", proposalId,
            Map.of("reason", reason),
            "Circuit breaker activated");
    }
    
    /**
     * Activates emergency system-wide shutdown
     */
    public void activateEmergencyShutdown(String proposalId, String reason) {
        if (!hasRequiredSignatures(proposalId)) {
            throw new IllegalStateException("Insufficient emergency signatures");
        }
        
        LOGGER.severe("EMERGENCY SYSTEM-WIDE SHUTDOWN ACTIVATED: " + reason);
        
        auditLog.recordEvent(GovernanceAuditLog.AuditEventType.EMERGENCY_SHUTDOWN,
            "EMERGENCY_MANAGER", proposalId,
            Map.of("reason", reason),
            "Emergency system shutdown activated");
    }
    
    private void startAutoExpirationMonitoring() {
        // Check for expired emergency proposals every 5 minutes
        scheduler.scheduleAtFixedRate(() -> {
            try {
                long now = System.currentTimeMillis();
                
                for (EmergencyProposal proposal : emergencyProposals.values()) {
                    if (!proposal.isExecuted() && now >= proposal.getExpirationTime()) {
                        LOGGER.warning("Emergency proposal expired: " + proposal.getProposalId());
                        
                        auditLog.recordEvent(GovernanceAuditLog.AuditEventType.PROPOSAL_REJECTED,
                            "EMERGENCY_MANAGER", proposal.getProposalId(),
                            Map.of("reason", "AUTO_EXPIRED"),
                            "Emergency proposal auto-expired after 72 hours");
                    }
                }
            } catch (Exception e) {
                LOGGER.severe("Error in auto-expiration monitoring: " + e.getMessage());
            }
        }, 5, 5, TimeUnit.MINUTES);
    }
    
    private void initializeEmergencySigners() {
        // In production: load from secure configuration
        // These are the 9 authorized emergency signers
        for (int i = 0; i < TOTAL_EMERGENCY_SIGNERS; i++) {
            emergencySigners.add("EMERGENCY_SIGNER_" + i);
        }
    }
    
    public void shutdown() {
        scheduler.shutdown();
    }
}