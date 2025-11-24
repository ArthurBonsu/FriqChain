package org.example.app.core.banking.compliance;

import org.example.app.core.types.Address;
import java.util.*;
import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * ArbitrationCouncil - Independent arbitrator pool for dispute resolution
 * 
 * Multi-tier DAO structure ensures no single entity controls arbitration:
 * - Local arbitrators (community level)
 * - National arbitrators (country level)
 * - Continental arbitrators (Africa-wide, NGOs + international experts)
 * 
 * Requirements:
 * - Geographic diversity (no single region/country dominates)
 * - Reputation scoring system
 * - Staking mechanism (skin in the game)
 * - Conflict-of-interest detection
 */
public class ArbitrationCouncil {
    private static final Logger LOGGER = Logger.getLogger(ArbitrationCouncil.class.getName());
    
    private final Map<String, Arbitrator> arbitrators;
    private final Map<String, Integer> arbitratorAssignments; // Dispute ID -> count of assignments
    private final Set<String> activeArbitrators;
    private final Map<String, Set<String>> conflictOfInterest; // Arbitrator ID -> set of conflicting parties
    private final Map<String, Double> reputationScores; // Arbitrator ID -> reputation
    private final BigInteger minimumStake;
    private final int maxAssignmentsPerArbitrator;
    
    public enum ArbitratorTier {
        LOCAL,          // Community level (district/city)
        NATIONAL,       // Country level
        CONTINENTAL    // Africa-wide (NGOs, international experts)
    }
    
    public enum ArbitratorStatus {
        ACTIVE,
        SUSPENDED,
        REMOVED,
        INACTIVE
    }
    
    public static class Arbitrator {
        private final String arbitratorId;
        private final Address address;
        private final String name;
        private final String affiliation;
        private final ArbitratorTier tier;
        private final String country;
        private final String region; // Geographic region
        private BigInteger stakedAmount;
        private ArbitratorStatus status;
        private final long registrationDate;
        private final List<String> reviewedDisputes;
        private final Map<String, Integer> verdictAccuracy; // Dispute ID -> accuracy score
        private double reputationScore;
        private final Set<String> conflictingParties;
        private int totalAssignments;
        private int verdicts;
        
        public Arbitrator(String arbitratorId, Address address, String name,
                         String affiliation, ArbitratorTier tier, String country, String region) {
            this.arbitratorId = arbitratorId;
            this.address = address;
            this.name = name;
            this.affiliation = affiliation;
            this.tier = tier;
            this.country = country;
            this.region = region;
            this.stakedAmount = BigInteger.ZERO;
            this.status = ArbitratorStatus.INACTIVE;
            this.registrationDate = System.currentTimeMillis();
            this.reviewedDisputes = new ArrayList<>();
            this.verdictAccuracy = new ConcurrentHashMap<>();
            this.reputationScore = 0.5; // Neutral starting reputation
            this.conflictingParties = ConcurrentHashMap.newKeySet();
            this.totalAssignments = 0;
            this.verdicts = 0;
        }
        
        // Getters and setters
        public String getArbitratorId() { return arbitratorId; }
        public Address getAddress() { return address; }
        public String getName() { return name; }
        public String getAffiliation() { return affiliation; }
        public ArbitratorTier getTier() { return tier; }
        public String getCountry() { return country; }
        public String getRegion() { return region; }
        public BigInteger getStakedAmount() { return stakedAmount; }
        public ArbitratorStatus getStatus() { return status; }
        public long getRegistrationDate() { return registrationDate; }
        public List<String> getReviewedDisputes() { return new ArrayList<>(reviewedDisputes); }
        public double getReputationScore() { return reputationScore; }
        public int getTotalAssignments() { return totalAssignments; }
        public int getVerdicts() { return verdicts; }
        
        public void setStakedAmount(BigInteger amount) { this.stakedAmount = amount; }
        public void setStatus(ArbitratorStatus s) { this.status = s; }
        public void setReputationScore(double score) { this.reputationScore = Math.max(0, Math.min(1, score)); }
        
        public void addDisputeReview(String disputeId) { reviewedDisputes.add(disputeId); }
        public void recordVerdict() { verdicts++; }
        public void recordAssignment() { totalAssignments++; }
        
        public void addConflict(String partyAddress) { conflictingParties.add(partyAddress); }
        public boolean hasConflict(String partyAddress) { return conflictingParties.contains(partyAddress); }
        public void removeConflict(String partyAddress) { conflictingParties.remove(partyAddress); }
    }
    
    public ArbitrationCouncil(BigInteger minimumStake) {
        this.arbitrators = new ConcurrentHashMap<>();
        this.arbitratorAssignments = new ConcurrentHashMap<>();
        this.activeArbitrators = ConcurrentHashMap.newKeySet();
        this.conflictOfInterest = new ConcurrentHashMap<>();
        this.reputationScores = new ConcurrentHashMap<>();
        this.minimumStake = minimumStake;
        this.maxAssignmentsPerArbitrator = 5; // Max concurrent assignments
        LOGGER.info("ArbitrationCouncil initialized with minimum stake: " + minimumStake);
    }
    
    /**
     * Registers a new arbitrator with staking requirement
     */
    public boolean registerArbitrator(String arbitratorId, Address address, String name,
                                     String affiliation, ArbitratorTier tier,
                                     String country, String region, BigInteger stakedAmount) {
        // Validate stake
        if (stakedAmount.compareTo(minimumStake) < 0) {
            LOGGER.warning("Insufficient stake for arbitrator: " + arbitratorId);
            return false;
        }
        
        // Check for duplicate
        if (arbitrators.containsKey(arbitratorId)) {
            LOGGER.warning("Arbitrator already registered: " + arbitratorId);
            return false;
        }
        
        Arbitrator arbitrator = new Arbitrator(arbitratorId, address, name, affiliation, tier, country, region);
        arbitrator.setStakedAmount(stakedAmount);
        arbitrator.setStatus(ArbitratorStatus.ACTIVE);
        
        arbitrators.put(arbitratorId, arbitrator);
        activeArbitrators.add(arbitratorId);
        reputationScores.put(arbitratorId, arbitrator.getReputationScore());
        conflictOfInterest.put(arbitratorId, new HashSet<>());
        
        LOGGER.info("Arbitrator registered: " + arbitratorId + " (" + tier + ")");
        return true;
    }
    
    /**
     * Selects arbitrators for a dispute with conflict-of-interest checks
     * Requires geographic diversity and multi-tier representation
     */
    public List<Address> selectArbitratorsForDispute(Address claimant, Address respondent,
                                                      DisputeResolutionContract.DisputeType type) {
        List<Address> selected = new ArrayList<>();
        Map<ArbitratorTier, Integer> tierDistribution = new HashMap<>();
        Map<String, Integer> regionDistribution = new HashMap<>();
        
        // Select at least 3 arbitrators: 1 local + 1 national + 1 continental (if possible)
        // Prefer more continental arbitrators for major disputes
        int targetCount = type.equals(DisputeResolutionContract.DisputeType.GOVERNANCE_MANIPULATION) ? 5 : 3;
        
        // Filter eligible arbitrators
        for (Arbitrator arb : arbitrators.values()) {
            if (!isEligibleForDispute(arb, claimant, respondent)) {
                continue;
            }
            
            // Enforce geographic diversity
            regionDistribution.putIfAbsent(arb.getRegion(), 0);
            if (regionDistribution.get(arb.getRegion()) >= 2) {
                continue; // Don't pick more than 2 from same region
            }
            
            // Enforce tier distribution
            tierDistribution.putIfAbsent(arb.getTier(), 0);
            if (arb.getTier() == ArbitratorTier.LOCAL && tierDistribution.get(ArbitratorTier.LOCAL) >= 2) {
                continue; // Max 2 local arbitrators
            }
            
            // Check workload
            int assignments = arbitratorAssignments.getOrDefault(arb.getArbitratorId(), 0);
            if (assignments >= maxAssignmentsPerArbitrator) {
                continue;
            }
            
            // Prioritize high reputation
            selected.add(arb.getAddress());
            tierDistribution.put(arb.getTier(), tierDistribution.get(arb.getTier()) + 1);
            regionDistribution.put(arb.getRegion(), regionDistribution.get(arb.getRegion()) + 1);
            
            if (selected.size() >= targetCount) {
                break;
            }
        }
        
        if (selected.size() < 3) {
            LOGGER.warning("Could not select minimum 3 arbitrators");
            return new ArrayList<>();
        }
        
        LOGGER.info("Selected " + selected.size() + " arbitrators for dispute type: " + type);
        return selected;
    }
    
    /**
     * Checks if arbitrator is eligible for a dispute
     */
    private boolean isEligibleForDispute(Arbitrator arbitrator, Address claimant, Address respondent) {
        // Must be active
        if (arbitrator.getStatus() != ArbitratorStatus.ACTIVE) {
            return false;
        }
        
        // No conflicts of interest
        if (arbitrator.hasConflict(claimant.toString()) || arbitrator.hasConflict(respondent.toString())) {
            return false;
        }
        
        // Must have minimum reputation
        if (arbitrator.getReputationScore() < 0.4) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Records a verdict and updates arbitrator reputation
     */
    public void recordVerdict(String arbitratorId, String disputeId, boolean accurateVerdict) {
        Arbitrator arbitrator = arbitrators.get(arbitratorId);
        if (arbitrator == null) {
            return;
        }
        
        arbitrator.recordVerdict();
        arbitrator.addDisputeReview(disputeId);
        
        // Update reputation based on verdict accuracy
        double currentRep = arbitrator.getReputationScore();
        if (accurateVerdict) {
            arbitrator.setReputationScore(currentRep + 0.05); // Increase by 5%
        } else {
            arbitrator.setReputationScore(currentRep - 0.1); // Decrease by 10%
        }
        
        reputationScores.put(arbitratorId, arbitrator.getReputationScore());
        LOGGER.info("Verdict recorded for arbitrator: " + arbitratorId + " - Accuracy: " + accurateVerdict);
    }
    
    /**
     * Suspends an arbitrator (e.g., due to slashing)
     */
    public void suspendArbitrator(String arbitratorId, String reason) {
        Arbitrator arbitrator = arbitrators.get(arbitratorId);
        if (arbitrator != null) {
            arbitrator.setStatus(ArbitratorStatus.SUSPENDED);
            activeArbitrators.remove(arbitratorId);
            LOGGER.info("Arbitrator suspended: " + arbitratorId + " - Reason: " + reason);
        }
    }
    
    /**
     * Removes an arbitrator permanently
     */
    public void removeArbitrator(String arbitratorId) {
        Arbitrator arbitrator = arbitrators.get(arbitratorId);
        if (arbitrator != null) {
            arbitrator.setStatus(ArbitratorStatus.REMOVED);
            activeArbitrators.remove(arbitratorId);
            LOGGER.info("Arbitrator removed: " + arbitratorId);
        }
    }
    
    /**
     * Registers conflict of interest
     */
    public void registerConflictOfInterest(String arbitratorId, String partyAddress) {
        Arbitrator arbitrator = arbitrators.get(arbitratorId);
        if (arbitrator != null) {
            arbitrator.addConflict(partyAddress);
            LOGGER.info("Conflict registered for arbitrator: " + arbitratorId + " with: " + partyAddress);
        }
    }
    
    /**
     * Gets arbitrator by ID
     */
    public Arbitrator getArbitrator(String arbitratorId) {
        return arbitrators.get(arbitratorId);
    }
    
    /**
     * Gets all active arbitrators
     */
    public Collection<Arbitrator> getActiveArbitrators() {
        return activeArbitrators.stream()
            .map(arbitrators::get)
            .filter(Objects::nonNull)
            .toList();
    }
    
    /**
     * Gets arbitrators by tier
     */
    public List<Arbitrator> getArbitratorsByTier(ArbitratorTier tier) {
        return arbitrators.values().stream()
            .filter(a -> a.getTier() == tier && a.getStatus() == ArbitratorStatus.ACTIVE)
            .toList();
    }
    
    /**
     * Gets arbitrators by region
     */
    public List<Arbitrator> getArbitratorsByRegion(String region) {
        return arbitrators.values().stream()
            .filter(a -> a.getRegion().equals(region) && a.getStatus() == ArbitratorStatus.ACTIVE)
            .toList();
    }
    
    /**
     * Gets average reputation score
     */
    public double getAverageReputationScore() {
        return reputationScores.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.5);
    }
}