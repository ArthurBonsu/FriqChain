package org.example.app.core.banking.compliance;

import org.example.app.core.types.Address;
import org.example.app.core.block.Transaction;
import java.util.*;
import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * DisputeResolutionContract - Core smart contract for on-chain dispute management
 * 
 * Implements Req #9: Dispute Resolution with transparent, rule-based enforcement
 * Provides due process with challenge windows, appeals, and automatic remediation
 * 
 * Dispute Lifecycle:
 * FILED -> EVIDENCE_PHASE -> ARBITRATION_PHASE -> VERDICT -> ENFORCEMENT -> REMEDIATION
 */
public class DisputeResolutionContract {
    private static final Logger LOGGER = Logger.getLogger(DisputeResolutionContract.class.getName());
    
    private final Map<String, Dispute> disputes;
    private final Map<String, DisputePhase> phaseTracker;
    private final ArbitrationCouncil arbitrationCouncil;
    private final long CHALLENGE_WINDOW_DURATION = 7 * 24 * 60 * 60 * 1000; // 7 days in milliseconds
    private final long ARBITRATION_DURATION = 14 * 24 * 60 * 60 * 1000; // 14 days
    private final long REMEDIATION_GRACE_PERIOD = 168 * 60 * 60 * 1000; // 168 hours / 7 days
    
    public enum DisputeStatus {
        FILED,
        EVIDENCE_SUBMITTED,
        CHALLENGE_WINDOW_OPEN,
        CHALLENGE_WINDOW_CLOSED,
        ARBITRATION_ONGOING,
        VERDICT_ISSUED,
        CHALLENGE_FILED,
        APPEAL_HEARING,
        ENFORCEMENT_PENDING,
        ENFORCED,
        REMEDIATION_PATH_ACTIVE,
        REMEDIATION_COMPLETE,
        DISMISSED,
        INVALID
    }
    
    public enum DisputeType {
        SANCTION_VIOLATION,
        FALSE_RESERVE_CLAIM,
        EXPORT_FRAUD,
        LIQUIDITY_DRAIN,
        GOVERNANCE_MANIPULATION,
        ORACLE_MANIPULATION,
        DATA_FALSIFICATION,
        UNAUTHORIZED_ACCESS,
        COLLATERAL_THEFT,
        OTHER
    }
    
    public static class Dispute {
        private final String disputeId;
        private final Address claimant;
        private final Address respondent;
        private final DisputeType type;
        private final String description;
        private final BigInteger collateralAmount;
        private final long filingTime;
        private final List<String> evidenceHashes;
        private final List<Address> arbitrators;
        private final Map<String, Boolean> arbitratorVotes;
        private DisputeStatus status;
        private long lastStatusUpdate;
        private String verdictReason;
        private boolean verdictFinal;
        
        public Dispute(String disputeId, Address claimant, Address respondent,
                      DisputeType type, String description, BigInteger collateral) {
            this.disputeId = disputeId;
            this.claimant = claimant;
            this.respondent = respondent;
            this.type = type;
            this.description = description;
            this.collateralAmount = collateral;
            this.filingTime = System.currentTimeMillis();
            this.evidenceHashes = new ArrayList<>();
            this.arbitrators = new ArrayList<>();
            this.arbitratorVotes = new ConcurrentHashMap<>();
            this.status = DisputeStatus.FILED;
            this.lastStatusUpdate = filingTime;
            this.verdictFinal = false;
        }
        
        // Getters
        public String getDisputeId() { return disputeId; }
        public Address getClaimant() { return claimant; }
        public Address getRespondent() { return respondent; }
        public DisputeType getType() { return type; }
        public String getDescription() { return description; }
        public BigInteger getCollateral() { return collateralAmount; }
        public long getFilingTime() { return filingTime; }
        public List<String> getEvidenceHashes() { return new ArrayList<>(evidenceHashes); }
        public List<Address> getArbitrators() { return new ArrayList<>(arbitrators); }
        public DisputeStatus getStatus() { return status; }
        public long getLastStatusUpdate() { return lastStatusUpdate; }
        public String getVerdictReason() { return verdictReason; }
        public boolean isVerdictFinal() { return verdictFinal; }
        
        public void addEvidence(String evidenceHash) {
            evidenceHashes.add(evidenceHash);
        }
        
        public void addArbitrator(Address arbitrator) {
            if (!arbitrators.contains(arbitrator)) {
                arbitrators.add(arbitrator);
                arbitratorVotes.put(arbitrator.toString(), null);
            }
        }
        
        public void recordVote(Address arbitrator, boolean guilty) {
            arbitratorVotes.put(arbitrator.toString(), guilty);
        }
        
        public void setStatus(DisputeStatus newStatus) {
            this.status = newStatus;
            this.lastStatusUpdate = System.currentTimeMillis();
        }
        
        public void setVerdict(String reason, boolean isFinal) {
            this.verdictReason = reason;
            this.verdictFinal = isFinal;
        }
        
        public int getGuiltyVotes() {
            return (int) arbitratorVotes.values().stream()
                .filter(v -> v != null && v).count();
        }
        
        public int getTotalVotes() {
            return (int) arbitratorVotes.values().stream()
                .filter(v -> v != null).count();
        }
    }
    
    public enum DisputePhase {
        EVIDENCE_COLLECTION,
        ARBITRATOR_SELECTION,
        CHALLENGE_WINDOW,
        ARBITRATION_HEARING,
        VERDICT_ISSUANCE,
        ENFORCEMENT_EXECUTION,
        REMEDIATION_TRACKING
    }
    
    public DisputeResolutionContract(ArbitrationCouncil council) {
        this.disputes = new ConcurrentHashMap<>();
        this.phaseTracker = new ConcurrentHashMap<>();
        this.arbitrationCouncil = council;
        LOGGER.info("DisputeResolutionContract initialized");
    }
    
    /**
     * Files a new dispute on-chain (Req #9)
     */
    public String fileDispute(Address claimant, Address respondent, DisputeType type,
                              String description, BigInteger collateral) {
        String disputeId = UUID.randomUUID().toString();
        Dispute dispute = new Dispute(disputeId, claimant, respondent, type, description, collateral);
        
        disputes.put(disputeId, dispute);
        phaseTracker.put(disputeId, DisputePhase.EVIDENCE_COLLECTION);
        
        LOGGER.info("Dispute filed: " + disputeId + " (" + type + ")");
        return disputeId;
    }
    
    /**
     * Submits evidence (on-chain hash + encrypted evidence off-chain via EvidenceSubmissionPortal)
     */
    public boolean submitEvidence(String disputeId, String evidenceHash) {
        Dispute dispute = disputes.get(disputeId);
        if (dispute == null) {
            LOGGER.warning("Dispute not found: " + disputeId);
            return false;
        }
        
        if (dispute.getStatus() != DisputeStatus.FILED && 
            dispute.getStatus() != DisputeStatus.EVIDENCE_SUBMITTED) {
            LOGGER.warning("Cannot submit evidence in status: " + dispute.getStatus());
            return false;
        }
        
        dispute.addEvidence(evidenceHash);
        dispute.setStatus(DisputeStatus.EVIDENCE_SUBMITTED);
        
        LOGGER.info("Evidence submitted for dispute: " + disputeId);
        return true;
    }
    
    /**
     * Initiates challenge window (7 days for both parties to challenge verdict or evidence)
     */
    public void openChallengeWindow(String disputeId) {
        Dispute dispute = disputes.get(disputeId);
        if (dispute != null) {
            dispute.setStatus(DisputeStatus.CHALLENGE_WINDOW_OPEN);
            phaseTracker.put(disputeId, DisputePhase.CHALLENGE_WINDOW);
            LOGGER.info("Challenge window opened for dispute: " + disputeId);
        }
    }
    
    /**
     * Closes challenge window after 7 days
     */
    public void closeChallengeWindow(String disputeId) {
        Dispute dispute = disputes.get(disputeId);
        if (dispute != null && dispute.getStatus() == DisputeStatus.CHALLENGE_WINDOW_OPEN) {
            long timeInWindow = System.currentTimeMillis() - dispute.getLastStatusUpdate();
            if (timeInWindow >= CHALLENGE_WINDOW_DURATION) {
                dispute.setStatus(DisputeStatus.CHALLENGE_WINDOW_CLOSED);
                LOGGER.info("Challenge window closed for dispute: " + disputeId);
            }
        }
    }
    
    /**
     * Moves dispute to arbitration phase with assigned arbitrators (>= 3 from Arbitration Council)
     */
    public void moveToArbitration(String disputeId, List<Address> arbitrators) {
        Dispute dispute = disputes.get(disputeId);
        if (dispute == null) {
            LOGGER.warning("Dispute not found: " + disputeId);
            return;
        }
        
        // Require minimum 3 arbitrators
        if (arbitrators.size() < 3) {
            LOGGER.warning("Insufficient arbitrators for dispute: " + disputeId);
            return;
        }
        
        for (Address arbitrator : arbitrators) {
            dispute.addArbitrator(arbitrator);
        }
        
        dispute.setStatus(DisputeStatus.ARBITRATION_ONGOING);
        phaseTracker.put(disputeId, DisputePhase.ARBITRATION_HEARING);
        
        LOGGER.info("Dispute moved to arbitration with " + arbitrators.size() + " arbitrators: " + disputeId);
    }
    
    /**
     * Records arbitrator vote on guilt
     */
    public void recordArbitratorVote(String disputeId, Address arbitrator, boolean guilty) {
        Dispute dispute = disputes.get(disputeId);
        if (dispute != null && dispute.getStatus() == DisputeStatus.ARBITRATION_ONGOING) {
            dispute.recordVote(arbitrator, guilty);
            LOGGER.info("Vote recorded from arbitrator: " + arbitrator + " for dispute: " + disputeId);
        }
    }
    
    /**
     * Issues verdict based on supermajority (>66%) arbitrator consensus
     */
    public boolean issueVerdict(String disputeId) {
        Dispute dispute = disputes.get(disputeId);
        if (dispute == null || dispute.getStatus() != DisputeStatus.ARBITRATION_ONGOING) {
            return false;
        }
        
        int guiltyVotes = dispute.getGuiltyVotes();
        int totalVotes = dispute.getTotalVotes();
        
        // Require 66% supermajority
        if (totalVotes < 3) {
            LOGGER.warning("Insufficient votes for dispute: " + disputeId);
            return false;
        }
        
        double supermajority = 0.66;
        if ((double) guiltyVotes / totalVotes >= supermajority) {
            dispute.setStatus(DisputeStatus.VERDICT_ISSUED);
            dispute.setVerdict("GUILTY by " + guiltyVotes + "/" + totalVotes, false);
            LOGGER.info("Verdict issued (GUILTY): " + disputeId);
        } else {
            dispute.setStatus(DisputeStatus.DISMISSED);
            dispute.setVerdict("NOT GUILTY - insufficient evidence", true);
            LOGGER.info("Verdict issued (NOT GUILTY): " + disputeId);
        }
        
        phaseTracker.put(disputeId, DisputePhase.VERDICT_ISSUANCE);
        return true;
    }
    
    /**
     * Files a challenge/appeal (extends dispute window by 14 more days)
     */
    public boolean fileChallenge(String disputeId, String challengeReason) {
        Dispute dispute = disputes.get(disputeId);
        if (dispute == null) {
            return false;
        }
        
        if (dispute.getStatus() != DisputeStatus.VERDICT_ISSUED &&
            dispute.getStatus() != DisputeStatus.CHALLENGE_WINDOW_OPEN) {
            return false;
        }
        
        dispute.setStatus(DisputeStatus.CHALLENGE_FILED);
        LOGGER.info("Challenge filed for dispute: " + disputeId + " - Reason: " + challengeReason);
        return true;
    }
    
    /**
     * Escalates to appeal hearing with additional arbitrators
     */
    public void escalateToAppeal(String disputeId, List<Address> appealArbitrators) {
        Dispute dispute = disputes.get(disputeId);
        if (dispute != null && dispute.getStatus() == DisputeStatus.CHALLENGE_FILED) {
            dispute.setStatus(DisputeStatus.APPEAL_HEARING);
            phaseTracker.put(disputeId, DisputePhase.ARBITRATION_HEARING);
            
            for (Address arbitrator : appealArbitrators) {
                dispute.addArbitrator(arbitrator);
            }
            
            LOGGER.info("Dispute escalated to appeal: " + disputeId);
        }
    }
    
    /**
     * Enforces verdict (applies sanctions via SanctionEnforcementEngine)
     */
    public boolean enforceVerdict(String disputeId) {
        Dispute dispute = disputes.get(disputeId);
        if (dispute == null || dispute.getStatus() != DisputeStatus.VERDICT_ISSUED) {
            return false;
        }
        
        // Verify verdict is final (past appeals window)
        long timeSinceVerdict = System.currentTimeMillis() - dispute.getLastStatusUpdate();
        if (timeSinceVerdict < CHALLENGE_WINDOW_DURATION) {
            LOGGER.warning("Verdict still in challenge window: " + disputeId);
            return false;
        }
        
        dispute.setStatus(DisputeStatus.ENFORCEMENT_PENDING);
        phaseTracker.put(disputeId, DisputePhase.ENFORCEMENT_EXECUTION);
        
        LOGGER.info("Verdict enforcement initiated: " + disputeId);
        return true;
    }
    
    /**
     * Confirms enforcement completed
     */
    public void confirmEnforcement(String disputeId) {
        Dispute dispute = disputes.get(disputeId);
        if (dispute != null) {
            dispute.setStatus(DisputeStatus.ENFORCED);
            LOGGER.info("Enforcement confirmed for dispute: " + disputeId);
        }
    }
    
    /**
     * Gets a dispute by ID
     */
    public Dispute getDispute(String disputeId) {
        return disputes.get(disputeId);
    }
    
    /**
     * Gets all disputes
     */
    public Collection<Dispute> getAllDisputes() {
        return disputes.values();
    }
    
    /**
     * Gets disputes by status
     */
    public List<Dispute> getDisputesByStatus(DisputeStatus status) {
        return disputes.values().stream()
            .filter(d -> d.getStatus() == status)
            .toList();
    }
    
    /**
     * Gets disputes involving an address (as claimant or respondent)
     */
    public List<Dispute> getDisputesByAddress(Address address) {
        return disputes.values().stream()
            .filter(d -> d.getClaimant().equals(address) || d.getRespondent().equals(address))
            .toList();
    }
}