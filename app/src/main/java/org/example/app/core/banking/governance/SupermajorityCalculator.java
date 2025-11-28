package org.example.app.core.banking.governance;

import org.example.app.core.types.Address;
import org.example.app.core.banking.registration.ValidatorNodeRegistry;
import org.example.app.core.banking.registration.ReporterRegistry;
import org.example.app.core.banking.compliance.DisputeResolutionContract;
import org.example.app.core.crypto.PKIManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * SupermajorityCalculator - Calculates and verifies multi-party approval requirements
 * 
 * Purpose: Ensures no single entity can control governance decisions
 * Implements three-party approval system:
 * - Validators (weighted by stake + reputation)
 * - Oracle Reporters (weighted by accuracy/trust score)
 * - Arbitrators (weighted by reputation)
 * 
 * Thresholds:
 * - Standard proposals: ≥66% (2/3 supermajority)
 * - Emergency proposals: ≥51% (simple majority, fast-track)
 * - Critical actions: ≥75% (3/4 supermajority + explicit M-of-N signatures)
 * 
 * Formula: (V_yes + R_yes + A_yes) / (V_total + R_total + A_total) ≥ threshold
 */
public class SupermajorityCalculator {
    private static final Logger LOGGER = Logger.getLogger(SupermajorityCalculator.class.getName());
    
    // Thresholds
    public static final BigDecimal SUPERMAJORITY_THRESHOLD = new BigDecimal("0.66"); // 66%
    public static final BigDecimal EMERGENCY_THRESHOLD = new BigDecimal("0.51"); // 51%
    public static final BigDecimal CRITICAL_THRESHOLD = new BigDecimal("0.75"); // 75%
    
    // Minimum participants required
    private static final int MIN_VALIDATORS_REQUIRED = 7;
    private static final int MIN_REPORTERS_REQUIRED = 5;
    private static final int MIN_ARBITRATORS_REQUIRED = 3;
    
    // Weight factors for each participant type
    private static final BigDecimal VALIDATOR_BASE_WEIGHT = new BigDecimal("1.0");
    private static final BigDecimal REPORTER_BASE_WEIGHT = new BigDecimal("0.8");
    private static final BigDecimal ARBITRATOR_BASE_WEIGHT = new BigDecimal("1.2");
    
    // Dependencies
    private final ValidatorNodeRegistry validatorRegistry;
    private final ReporterRegistry reporterRegistry;
    private final PKIManager pkiManager;
    
    // Vote tracking
    private final Map<String, VoteSet> proposalVotes;
    
    public enum ProposalType {
        STANDARD,     // Regular governance proposals
        EMERGENCY,    // Fast-track for urgent matters
        CRITICAL      // Highest stakes (sanctions, treasury large amounts)
    }
    
    public enum ParticipantType {
        VALIDATOR,
        REPORTER,
        ARBITRATOR
    }
    
    /**
     * Represents a vote from a participant
     */
    public static class Vote {
        private final String voterId;
        private final ParticipantType participantType;
        private final boolean votedYes;
        private final BigDecimal weight;
        private final long timestamp;
        private final byte[] signature;
        
        public Vote(String voterId, ParticipantType participantType, boolean votedYes,
                   BigDecimal weight, byte[] signature) {
            this.voterId = voterId;
            this.participantType = participantType;
            this.votedYes = votedYes;
            this.weight = weight;
            this.timestamp = System.currentTimeMillis();
            this.signature = signature;
        }
        
        // Getters
        public String getVoterId() { return voterId; }
        public ParticipantType getParticipantType() { return participantType; }
        public boolean isVotedYes() { return votedYes; }
        public BigDecimal getWeight() { return weight; }
        public long getTimestamp() { return timestamp; }
        public byte[] getSignature() { return signature; }
    }
    
    /**
     * Aggregates votes for a proposal
     */
    public static class VoteSet {
        private final String proposalId;
        private final ProposalType proposalType;
        private final Map<String, Vote> votes;
        private final long votingStartTime;
        private final long votingEndTime;
        
        public VoteSet(String proposalId, ProposalType proposalType, long votingDuration) {
            this.proposalId = proposalId;
            this.proposalType = proposalType;
            this.votes = new ConcurrentHashMap<>();
            this.votingStartTime = System.currentTimeMillis();
            this.votingEndTime = votingStartTime + votingDuration;
        }
        
        public void addVote(Vote vote) {
            votes.put(vote.getVoterId(), vote);
        }
        
        public boolean hasVoted(String voterId) {
            return votes.containsKey(voterId);
        }
        
        public boolean isVotingOpen() {
            return System.currentTimeMillis() < votingEndTime;
        }
        
        public long getTimeRemaining() {
            return Math.max(0, votingEndTime - System.currentTimeMillis());
        }
        
        public Collection<Vote> getAllVotes() {
            return votes.values();
        }
        
        public int getTotalVotes() {
            return votes.size();
        }
    }
    
    /**
     * Weighted approval calculation result
     */
    public static class ApprovalResult {
        private final boolean approved;
        private final BigDecimal yesPercentage;
        private final BigDecimal threshold;
        private final BigDecimal validatorYesWeight;
        private final BigDecimal validatorTotalWeight;
        private final BigDecimal reporterYesWeight;
        private final BigDecimal reporterTotalWeight;
        private final BigDecimal arbitratorYesWeight;
        private final BigDecimal arbitratorTotalWeight;
        private final String failureReason;
        
        public ApprovalResult(boolean approved, BigDecimal yesPercentage, BigDecimal threshold,
                             BigDecimal validatorYesWeight, BigDecimal validatorTotalWeight,
                             BigDecimal reporterYesWeight, BigDecimal reporterTotalWeight,
                             BigDecimal arbitratorYesWeight, BigDecimal arbitratorTotalWeight,
                             String failureReason) {
            this.approved = approved;
            this.yesPercentage = yesPercentage;
            this.threshold = threshold;
            this.validatorYesWeight = validatorYesWeight;
            this.validatorTotalWeight = validatorTotalWeight;
            this.reporterYesWeight = reporterYesWeight;
            this.reporterTotalWeight = reporterTotalWeight;
            this.arbitratorYesWeight = arbitratorYesWeight;
            this.arbitratorTotalWeight = arbitratorTotalWeight;
            this.failureReason = failureReason;
        }
        
        // Getters
        public boolean isApproved() { return approved; }
        public BigDecimal getYesPercentage() { return yesPercentage; }
        public BigDecimal getThreshold() { return threshold; }
        public BigDecimal getValidatorYesWeight() { return validatorYesWeight; }
        public BigDecimal getValidatorTotalWeight() { return validatorTotalWeight; }
        public BigDecimal getReporterYesWeight() { return reporterYesWeight; }
        public BigDecimal getReporterTotalWeight() { return reporterTotalWeight; }
        public BigDecimal getArbitratorYesWeight() { return arbitratorYesWeight; }
        public BigDecimal getArbitratorTotalWeight() { return arbitratorTotalWeight; }
        public String getFailureReason() { return failureReason; }
        
        @Override
        public String toString() {
            return String.format("Approval: %s (%.2f%% >= %.2f%%) | V: %.2f/%.2f | R: %.2f/%.2f | A: %.2f/%.2f",
                approved ? "YES" : "NO",
                yesPercentage.multiply(new BigDecimal("100")),
                threshold.multiply(new BigDecimal("100")),
                validatorYesWeight, validatorTotalWeight,
                reporterYesWeight, reporterTotalWeight,
                arbitratorYesWeight, arbitratorTotalWeight);
        }
    }
    
    public SupermajorityCalculator(ValidatorNodeRegistry validatorRegistry,
                                   ReporterRegistry reporterRegistry,
                                   PKIManager pkiManager) {
        this.validatorRegistry = validatorRegistry;
        this.reporterRegistry = reporterRegistry;
        this.pkiManager = pkiManager;
        this.proposalVotes = new ConcurrentHashMap<>();
        
        LOGGER.info("SupermajorityCalculator initialized");
    }
    
    /**
     * Creates a new vote set for a proposal
     */
    public void createVoteSet(String proposalId, ProposalType proposalType, long votingDurationMs) {
        VoteSet voteSet = new VoteSet(proposalId, proposalType, votingDurationMs);
        proposalVotes.put(proposalId, voteSet);
        
        LOGGER.info("Vote set created for proposal: " + proposalId + " (" + proposalType + ")");
    }
    
    /**
     * Records a vote from a participant
     */
    public boolean recordVote(String proposalId, String voterId, ParticipantType participantType,
                             boolean voteYes, byte[] signature) {
        VoteSet voteSet = proposalVotes.get(proposalId);
        
        if (voteSet == null) {
            LOGGER.warning("Vote set not found for proposal: " + proposalId);
            return false;
        }
        
        if (!voteSet.isVotingOpen()) {
            LOGGER.warning("Voting period closed for proposal: " + proposalId);
            return false;
        }
        
        if (voteSet.hasVoted(voterId)) {
            LOGGER.warning("Participant already voted: " + voterId);
            return false;
        }
        
        // Verify participant eligibility and calculate weight
        BigDecimal weight = calculateVoteWeight(voterId, participantType);
        if (weight.compareTo(BigDecimal.ZERO) <= 0) {
            LOGGER.warning("Participant not eligible to vote: " + voterId);
            return false;
        }
        
        // Verify signature
        if (!verifyVoteSignature(voterId, proposalId, voteYes, signature)) {
            LOGGER.warning("Invalid vote signature from: " + voterId);
            return false;
        }
        
        // Record vote
        Vote vote = new Vote(voterId, participantType, voteYes, weight, signature);
        voteSet.addVote(vote);
        
        LOGGER.info("Vote recorded: " + voterId + " -> " + (voteYes ? "YES" : "NO") + 
                   " (weight: " + weight + ")");
        return true;
    }
    
    /**
     * Calculates if proposal has reached supermajority approval
     */
    public ApprovalResult calculateApproval(String proposalId) {
        VoteSet voteSet = proposalVotes.get(proposalId);
        
        if (voteSet == null) {
            return new ApprovalResult(false, BigDecimal.ZERO, SUPERMAJORITY_THRESHOLD,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, "Vote set not found");
        }
        
        // Determine threshold based on proposal type
        BigDecimal threshold = getThresholdForProposal(voteSet.proposalType);
        
        // Separate votes by participant type
        List<Vote> validatorVotes = new ArrayList<>();
        List<Vote> reporterVotes = new ArrayList<>();
        List<Vote> arbitratorVotes = new ArrayList<>();
        
        for (Vote vote : voteSet.getAllVotes()) {
            switch (vote.getParticipantType()) {
                case VALIDATOR:
                    validatorVotes.add(vote);
                    break;
                case REPORTER:
                    reporterVotes.add(vote);
                    break;
                case ARBITRATOR:
                    arbitratorVotes.add(vote);
                    break;
            }
        }
        
        // Calculate weighted approval for each type
        BigDecimal validatorYesWeight = calculateGroupYesWeight(validatorVotes);
        BigDecimal validatorTotalWeight = calculateGroupTotalWeight(validatorVotes);
        
        BigDecimal reporterYesWeight = calculateGroupYesWeight(reporterVotes);
        BigDecimal reporterTotalWeight = calculateGroupTotalWeight(reporterVotes);
        
        BigDecimal arbitratorYesWeight = calculateGroupYesWeight(arbitratorVotes);
        BigDecimal arbitratorTotalWeight = calculateGroupTotalWeight(arbitratorVotes);
        
        // Calculate combined approval
        BigDecimal totalYesWeight = validatorYesWeight.add(reporterYesWeight).add(arbitratorYesWeight);
        BigDecimal totalWeight = validatorTotalWeight.add(reporterTotalWeight).add(arbitratorTotalWeight);
        
        // Check minimum participation
        String failureReason = checkMinimumParticipation(
            validatorVotes.size(), reporterVotes.size(), arbitratorVotes.size()
        );
        
        if (failureReason != null) {
            return new ApprovalResult(false, BigDecimal.ZERO, threshold,
                validatorYesWeight, validatorTotalWeight,
                reporterYesWeight, reporterTotalWeight,
                arbitratorYesWeight, arbitratorTotalWeight,
                failureReason);
        }
        
        // Calculate approval percentage
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return new ApprovalResult(false, BigDecimal.ZERO, threshold,
                validatorYesWeight, validatorTotalWeight,
                reporterYesWeight, reporterTotalWeight,
                arbitratorYesWeight, arbitratorTotalWeight,
                "No votes cast");
        }
        
        BigDecimal yesPercentage = totalYesWeight.divide(totalWeight, 4, RoundingMode.HALF_UP);
        boolean approved = yesPercentage.compareTo(threshold) >= 0;
        
        return new ApprovalResult(approved, yesPercentage, threshold,
            validatorYesWeight, validatorTotalWeight,
            reporterYesWeight, reporterTotalWeight,
            arbitratorYesWeight, arbitratorTotalWeight,
            approved ? null : "Threshold not met");
    }
    
    /**
     * Checks if voting has ended for a proposal
     */
    public boolean isVotingEnded(String proposalId) {
        VoteSet voteSet = proposalVotes.get(proposalId);
        return voteSet != null && !voteSet.isVotingOpen();
    }
    
    /**
     * Gets time remaining for voting
     */
    public long getVotingTimeRemaining(String proposalId) {
        VoteSet voteSet = proposalVotes.get(proposalId);
        return voteSet != null ? voteSet.getTimeRemaining() : 0;
    }
    
    /**
     * Gets voting statistics for a proposal
     */
    public Map<String, Object> getVotingStatistics(String proposalId) {
        VoteSet voteSet = proposalVotes.get(proposalId);
        if (voteSet == null) {
            return Collections.emptyMap();
        }
        
        ApprovalResult result = calculateApproval(proposalId);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("proposalId", proposalId);
        stats.put("proposalType", voteSet.proposalType);
        stats.put("totalVotes", voteSet.getTotalVotes());
        stats.put("votingOpen", voteSet.isVotingOpen());
        stats.put("timeRemaining", voteSet.getTimeRemaining());
        stats.put("approved", result.isApproved());
        stats.put("yesPercentage", result.getYesPercentage());
        stats.put("threshold", result.getThreshold());
        stats.put("validatorYesWeight", result.getValidatorYesWeight());
        stats.put("validatorTotalWeight", result.getValidatorTotalWeight());
        stats.put("reporterYesWeight", result.getReporterYesWeight());
        stats.put("reporterTotalWeight", result.getReporterTotalWeight());
        stats.put("arbitratorYesWeight", result.getArbitratorYesWeight());
        stats.put("arbitratorTotalWeight", result.getArbitratorTotalWeight());
        
        return stats;
    }
    
    // Private helper methods
    
    private BigDecimal getThresholdForProposal(ProposalType type) {
        switch (type) {
            case EMERGENCY:
                return EMERGENCY_THRESHOLD;
            case CRITICAL:
                return CRITICAL_THRESHOLD;
            default:
                return SUPERMAJORITY_THRESHOLD;
        }
    }
    
    private BigDecimal calculateVoteWeight(String voterId, ParticipantType type) {
        switch (type) {
            case VALIDATOR:
                return calculateValidatorWeight(voterId);
            case REPORTER:
                return calculateReporterWeight(voterId);
            case ARBITRATOR:
                return calculateArbitratorWeight(voterId);
            default:
                return BigDecimal.ZERO;
        }
    }
    
    private BigDecimal calculateValidatorWeight(String validatorId) {
        ValidatorNodeRegistry.ValidatorNode validator = validatorRegistry.getValidator(validatorId);
        
        if (validator == null || 
            validator.getStatus() != ValidatorNodeRegistry.ValidatorStatus.ACTIVE) {
            return BigDecimal.ZERO;
        }
        
        // Weight = base_weight * stake_factor * reputation_factor
        BigDecimal stakeFactor = new BigDecimal(validator.getStakedAmount().toString())
            .divide(new BigDecimal("1000000000000000000000"), 4, RoundingMode.HALF_UP); // Normalize
        
        BigDecimal reputationFactor = new BigDecimal(validator.getReputation())
            .divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP); // Normalize to 0-1
        
        return VALIDATOR_BASE_WEIGHT.multiply(stakeFactor).multiply(reputationFactor);
    }
    
    private BigDecimal calculateReporterWeight(String reporterId) {
        ReporterRegistry.Reporter reporter = reporterRegistry.getReporter(reporterId);
        
        if (reporter == null || !reporter.isActive()) {
            return BigDecimal.ZERO;
        }
        
        // Weight = base_weight * accuracy_score
        BigDecimal accuracyScore = reporter.getAccuracyScore();
        return REPORTER_BASE_WEIGHT.multiply(accuracyScore);
    }
    
    private BigDecimal calculateArbitratorWeight(String arbitratorId) {
        // For now, all arbitrators have equal weight
        // In full implementation, would check ArbitrationCouncil
        return ARBITRATOR_BASE_WEIGHT;
    }
    
    private BigDecimal calculateGroupYesWeight(List<Vote> votes) {
        return votes.stream()
            .filter(Vote::isVotedYes)
            .map(Vote::getWeight)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal calculateGroupTotalWeight(List<Vote> votes) {
        return votes.stream()
            .map(Vote::getWeight)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private String checkMinimumParticipation(int validatorCount, int reporterCount, int arbitratorCount) {
        if (validatorCount < MIN_VALIDATORS_REQUIRED) {
            return "Insufficient validator participation (" + validatorCount + " < " + 
                   MIN_VALIDATORS_REQUIRED + ")";
        }
        
        if (reporterCount < MIN_REPORTERS_REQUIRED) {
            return "Insufficient reporter participation (" + reporterCount + " < " + 
                   MIN_REPORTERS_REQUIRED + ")";
        }
        
        if (arbitratorCount < MIN_ARBITRATORS_REQUIRED) {
            return "Insufficient arbitrator participation (" + arbitratorCount + " < " + 
                   MIN_ARBITRATORS_REQUIRED + ")";
        }
        
        return null; // All participation requirements met
    }
    
    private boolean verifyVoteSignature(String voterId, String proposalId, boolean voteYes, byte[] signature) {
        // In production: verify cryptographic signature using PKIManager
        // For now: basic validation
        return signature != null && signature.length > 0;
    }
}