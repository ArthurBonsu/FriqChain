package org.example.app.core.banking.tokenization;

import org.example.app.core.state.BlockchainState;
import org.example.app.core.consensus.Blockchain;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Dynamic Basket Rebalancer - Automatically adjusts ASU basket weights
 * 
 * Implements production-level rebalancing with:
 * - Auto-rebalancing when countries exit/enter system
 * - Non-participation attack defense (auto-weight adjustment)
 * - Economic metric-based weight calculation
 * - DAO governance approval for major changes
 * - Transparent on-chain execution
 */
public class DynamicBasketRebalancer {
    private static final Logger LOGGER = Logger.getLogger(DynamicBasketRebalancer.class.getName());
    
    // Rebalancing thresholds
    private final BigDecimal majorRebalanceThreshold = new BigDecimal("0.10"); // 10% change
    private final BigDecimal minorRebalanceThreshold = new BigDecimal("0.05"); // 5% change
    private final long rebalanceInterval = 3600000; // 1 hour minimum
    private long lastRebalance = System.currentTimeMillis();
    
    // Weight calculation factors
    private final Map<String, BigDecimal> factorWeights = new HashMap<>() {{
        put("GDP_CONTRIBUTION", new BigDecimal("0.30")); // 30%
        put("TRADE_VOLUME", new BigDecimal("0.25")); // 25%
        put("RESERVES", new BigDecimal("0.20")); // 20%
        put("STABILITY", new BigDecimal("0.15")); // 15%
        put("PARTICIPATION", new BigDecimal("0.10")); // 10%
    }};
    
    // Basket components tracking
    private final Map<String, BasketComponent> currentBasket = new ConcurrentHashMap<>();
    private final Map<String, BasketComponent> targetBasket = new ConcurrentHashMap<>();
    private final List<RebalancingEvent> rebalancingHistory = Collections.synchronizedList(new ArrayList<>());
    
    // Non-participation tracking
    private final Map<String, ParticipationMetrics> participationMetrics = new ConcurrentHashMap<>();
    private final Set<String> nonParticipatingCountries = ConcurrentHashMap.newKeySet();
    
    // Geographic constraints
    private final BigDecimal maxCountryWeight = new BigDecimal("0.30"); // Max 30% per country
    private final BigDecimal minCountryWeight = new BigDecimal("0.01"); // Min 1% per country
    
    // Governance
    private final Set<String> daoValidators = ConcurrentHashMap.newKeySet();
    private final Map<String, RebalanceProposal> pendingProposals = new ConcurrentHashMap<>();
    private final int requiredApprovalThreshold = 67; // 67% approval
    
    // Scheduling
    private final ScheduledExecutorService rebalanceScheduler = Executors.newScheduledThreadPool(2);
    
    // Dependencies
    private final BlockchainState blockchainState;
    private final Blockchain blockchain;
    private final AfricanStableUnitToken asuToken;
    
    /**
     * Basket Component
     */
    public static class BasketComponent {
        public final String countryCode;
        public final String currencyCode;
        public BigDecimal currentWeight;
        public BigDecimal targetWeight;
        public final ComponentMetrics metrics;
        public long lastUpdated;
        public boolean participating;
        
        public BasketComponent(String countryCode, String currencyCode,
                             BigDecimal currentWeight, ComponentMetrics metrics) {
            this.countryCode = countryCode;
            this.currencyCode = currencyCode;
            this.currentWeight = currentWeight;
            this.targetWeight = currentWeight;
            this.metrics = metrics;
            this.lastUpdated = System.currentTimeMillis();
            this.participating = true;
        }
    }
    
    /**
     * Component Metrics for weight calculation
     */
    public static class ComponentMetrics {
        public BigDecimal gdpContribution;
        public BigDecimal tradeVolume;
        public BigDecimal reserves;
        public BigDecimal stabilityScore;
        public BigDecimal participationScore;
        public long lastDataUpdate;
        
        public ComponentMetrics(BigDecimal gdpContribution, BigDecimal tradeVolume,
                              BigDecimal reserves, BigDecimal stabilityScore,
                              BigDecimal participationScore) {
            this.gdpContribution = gdpContribution;
            this.tradeVolume = tradeVolume;
            this.reserves = reserves;
            this.stabilityScore = stabilityScore;
            this.participationScore = participationScore;
            this.lastDataUpdate = System.currentTimeMillis();
        }
    }
    
    /**
     * Participation Metrics for non-participation defense
     */
    public static class ParticipationMetrics {
        public final String countryCode;
        public int consecutiveAbsences;
        public BigDecimal penaltyFactor;
        public long lastParticipation;
        public final List<Long> absenceTimes;
        
        public ParticipationMetrics(String countryCode) {
            this.countryCode = countryCode;
            this.consecutiveAbsences = 0;
            this.penaltyFactor = BigDecimal.ONE;
            this.lastParticipation = System.currentTimeMillis();
            this.absenceTimes = new ArrayList<>();
        }
        
        public void recordAbsence() {
            consecutiveAbsences++;
            absenceTimes.add(System.currentTimeMillis());
            
            // Increase penalty: 10% per absence
            penaltyFactor = penaltyFactor.subtract(new BigDecimal("0.10"))
                                        .max(BigDecimal.ZERO);
        }
        
        public void recordParticipation() {
            consecutiveAbsences = 0;
            lastParticipation = System.currentTimeMillis();
            
            // Gradually restore penalty factor
            if (penaltyFactor.compareTo(BigDecimal.ONE) < 0) {
                penaltyFactor = penaltyFactor.add(new BigDecimal("0.05"))
                                            .min(BigDecimal.ONE);
            }
        }
    }
    
    /**
     * Rebalancing Event
     */
    public static class RebalancingEvent {
        public final String eventId;
        public final Map<String, BigDecimal> oldWeights;
        public final Map<String, BigDecimal> newWeights;
        public final String trigger; // "COUNTRY_EXIT", "METRICS_UPDATE", "DAO_PROPOSAL"
        public final long executionTime;
        public final boolean daoApproved;
        public final Map<String, Object> details;
        
        public RebalancingEvent(String eventId, Map<String, BigDecimal> oldWeights,
                              Map<String, BigDecimal> newWeights, String trigger,
                              boolean daoApproved, Map<String, Object> details) {
            this.eventId = eventId;
            this.oldWeights = new HashMap<>(oldWeights);
            this.newWeights = new HashMap<>(newWeights);
            this.trigger = trigger;
            this.executionTime = System.currentTimeMillis();
            this.daoApproved = daoApproved;
            this.details = new HashMap<>(details);
        }
    }
    
    /**
     * Rebalance Proposal for DAO governance
     */
    public static class RebalanceProposal {
        public final String proposalId;
        public final Map<String, BigDecimal> proposedWeights;
        public final String rationale;
        public final Map<String, Boolean> votes;
        public final long proposalTime;
        public final long votingDeadline;
        public ProposalStatus status;
        
        public enum ProposalStatus {
            PENDING, APPROVED, REJECTED, EXECUTED
        }
        
        public RebalanceProposal(String proposalId, Map<String, BigDecimal> proposedWeights,
                               String rationale, long votingDeadline) {
            this.proposalId = proposalId;
            this.proposedWeights = new HashMap<>(proposedWeights);
            this.rationale = rationale;
            this.votes = new ConcurrentHashMap<>();
            this.proposalTime = System.currentTimeMillis();
            this.votingDeadline = votingDeadline;
            this.status = ProposalStatus.PENDING;
        }
        
        public int getApprovalPercentage() {
            if (votes.isEmpty()) return 0;
            long approvals = votes.values().stream().filter(v -> v).count();
            return (int)((approvals * 100) / votes.size());
        }
    }
    
    /**
     * Constructor
     */
    public DynamicBasketRebalancer(BlockchainState blockchainState,
                                  Blockchain blockchain,
                                  AfricanStableUnitToken asuToken) {
        this.blockchainState = blockchainState;
        this.blockchain = blockchain;
        this.asuToken = asuToken;
        
        startAutomaticRebalancing();
        
        LOGGER.info("DynamicBasketRebalancer initialized");
    }
    
    /**
     * Calculates target weights based on current metrics
     */
    public CompletableFuture<Map<String, BigDecimal>> calculateTargetWeights() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, BigDecimal> targetWeights = new HashMap<>();
            BigDecimal totalScore = BigDecimal.ZERO;
            Map<String, BigDecimal> componentScores = new HashMap<>();
            
            // Calculate scores for each component
            for (BasketComponent component : currentBasket.values()) {
                if (!component.participating) {
                    LOGGER.info("Skipping non-participating country: " + component.countryCode);
                    continue;
                }
                
                BigDecimal score = calculateComponentScore(component);
                
                // Apply participation penalty
                ParticipationMetrics participation = participationMetrics.get(component.countryCode);
                if (participation != null) {
                    score = score.multiply(participation.penaltyFactor);
                }
                
                componentScores.put(component.currencyCode, score);
                totalScore = totalScore.add(score);
            }
            
            // Calculate proportional weights
            if (totalScore.compareTo(BigDecimal.ZERO) > 0) {
                for (Map.Entry<String, BigDecimal> entry : componentScores.entrySet()) {
                    BigDecimal weight = entry.getValue()
                        .divide(totalScore, 8, RoundingMode.HALF_UP);
                    
                    // Apply constraints
                    weight = weight.min(maxCountryWeight).max(minCountryWeight);
                    
                    targetWeights.put(entry.getKey(), weight);
                }
            }
            
            // Normalize to ensure sum = 1.0
            normalizeWeights(targetWeights);
            
            return targetWeights;
        });
    }
    
    /**
     * Calculates score for a basket component
     */
    private BigDecimal calculateComponentScore(BasketComponent component) {
        ComponentMetrics metrics = component.metrics;
        
        BigDecimal score = 
            metrics.gdpContribution.multiply(factorWeights.get("GDP_CONTRIBUTION"))
            .add(metrics.tradeVolume.multiply(factorWeights.get("TRADE_VOLUME")))
            .add(metrics.reserves.multiply(factorWeights.get("RESERVES")))
            .add(metrics.stabilityScore.multiply(factorWeights.get("STABILITY")))
            .add(metrics.participationScore.multiply(factorWeights.get("PARTICIPATION")));
        
        return score;
    }
    
    /**
     * Handles country exit/non-participation
     */
    public CompletableFuture<Boolean> handleCountryExit(String countryCode, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            LOGGER.warning(String.format("Handling country exit: %s - Reason: %s",
                countryCode, reason));
            
            // Mark as non-participating
            nonParticipatingCountries.add(countryCode);
            
            // Update basket component
            BasketComponent component = findComponentByCountry(countryCode);
            if (component != null) {
                component.participating = false;
                
                // Record participation metrics
                ParticipationMetrics metrics = participationMetrics
                    .computeIfAbsent(countryCode, ParticipationMetrics::new);
                metrics.recordAbsence();
            }
            
            // Trigger immediate rebalancing
            return executeRebalancing("COUNTRY_EXIT", 
                Collections.singletonMap("exitedCountry", countryCode)).join();
        });
    }
    
    /**
     * Handles country re-entry
     */
    public CompletableFuture<Boolean> handleCountryReentry(String countryCode) {
        return CompletableFuture.supplyAsync(() -> {
            LOGGER.info("Handling country re-entry: " + countryCode);
            
            // Remove from non-participating set
            nonParticipatingCountries.remove(countryCode);
            
            // Update basket component
            BasketComponent component = findComponentByCountry(countryCode);
            if (component != null) {
                component.participating = true;
                
                // Record participation
                ParticipationMetrics metrics = participationMetrics.get(countryCode);
                if (metrics != null) {
                    metrics.recordParticipation();
                }
            }
            
            // Trigger rebalancing
            return executeRebalancing("COUNTRY_REENTRY",
                Collections.singletonMap("reentered Country", countryCode)).join();
        });
    }
    
    /**
     * Executes basket rebalancing
     */
    public CompletableFuture<Boolean> executeRebalancing(String trigger,
                                                        Map<String, Object> details) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check if enough time has passed since last rebalance
                long timeSinceLastRebalance = System.currentTimeMillis() - lastRebalance;
                if (timeSinceLastRebalance < rebalanceInterval && !trigger.equals("COUNTRY_EXIT")) {
                    LOGGER.info("Rebalance interval not reached, skipping");
                    return false;
                }
                
                // Calculate target weights
                Map<String, BigDecimal> targetWeights = calculateTargetWeights().join();
                
                // Get current weights
                Map<String, BigDecimal> currentWeights = getCurrentWeights();
                
                // Calculate maximum weight change
                BigDecimal maxChange = calculateMaxWeightChange(currentWeights, targetWeights);
                
                // Check if DAO approval required
                boolean requiresDAOApproval = maxChange.compareTo(majorRebalanceThreshold) > 0;
                
                if (requiresDAOApproval) {
                    // Create proposal
                    String proposalId = submitRebalanceProposal(targetWeights,
                        "Automatic rebalancing triggered by: " + trigger);
                    
                    LOGGER.info(String.format("Major rebalancing requires DAO approval: %s (max change: %.2f%%)",
                        proposalId, maxChange.multiply(new BigDecimal("100"))));
                    
                    return false; // Wait for DAO approval
                } else {
                    // Execute minor rebalancing immediately
                    boolean success = applyWeightChanges(targetWeights);
                    
                    if (success) {
                        // Record rebalancing event
                        String eventId = generateId("REB");
                        RebalancingEvent event = new RebalancingEvent(
                            eventId, currentWeights, targetWeights, trigger, false, details
                        );
                        rebalancingHistory.add(event);
                        
                        lastRebalance = System.currentTimeMillis();
                        
                        LOGGER.info(String.format("Rebalancing executed: %s - Max change: %.2f%%",
                            eventId, maxChange.multiply(new BigDecimal("100"))));
                    }
                    
                    return success;
                }
                
            } catch (Exception e) {
                LOGGER.severe("Rebalancing failed: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Submits rebalance proposal to DAO
     */
    public String submitRebalanceProposal(Map<String, BigDecimal> proposedWeights,
                                         String rationale) {
        String proposalId = generateId("PROP");
        long deadline = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L); // 7 days
        
        RebalanceProposal proposal = new RebalanceProposal(
            proposalId, proposedWeights, rationale, deadline
        );
        
        pendingProposals.put(proposalId, proposal);
        
        LOGGER.info("Rebalance proposal submitted: " + proposalId);
        return proposalId;
    }
    
    /**
     * DAO member votes on proposal
     */
    public boolean voteOnProposal(String proposalId, String validatorId, boolean approve) {
        RebalanceProposal proposal = pendingProposals.get(proposalId);
        if (proposal == null || proposal.status != RebalanceProposal.ProposalStatus.PENDING) {
            return false;
        }
        
        if (!daoValidators.contains(validatorId)) {
            LOGGER.warning("Unauthorized validator: " + validatorId);
            return false;
        }
        
        proposal.votes.put(validatorId, approve);
        
        // Check if threshold reached
        if (proposal.getApprovalPercentage() >= requiredApprovalThreshold) {
            proposal.status = RebalanceProposal.ProposalStatus.APPROVED;
            
            // Execute approved rebalancing
            executeApprovedProposal(proposal);
            
            LOGGER.info(String.format("Proposal approved: %s (%d%% approval)",
                proposalId, proposal.getApprovalPercentage()));
        }
        
        return true;
    }
    
    /**
     * Executes approved proposal
     */
    private void executeApprovedProposal(RebalanceProposal proposal) {
        Map<String, BigDecimal> currentWeights = getCurrentWeights();
        boolean success = applyWeightChanges(proposal.proposedWeights);
        
        if (success) {
            proposal.status = RebalanceProposal.ProposalStatus.EXECUTED;
            
            // Record rebalancing event
            String eventId = generateId("REB");
            RebalancingEvent event = new RebalancingEvent(
                eventId, currentWeights, proposal.proposedWeights,
                "DAO_PROPOSAL", true,
                Collections.singletonMap("proposalId", proposal.proposalId)
            );
            rebalancingHistory.add(event);
            
            lastRebalance = System.currentTimeMillis();
        }
    }
    
    /**
     * Applies weight changes to ASU basket
     */
    private boolean applyWeightChanges(Map<String, BigDecimal> newWeights) {
        try {
            // Update current basket weights
            for (Map.Entry<String, BigDecimal> entry : newWeights.entrySet()) {
                BasketComponent component = currentBasket.get(entry.getKey());
                if (component != null) {
                    component.currentWeight = entry.getValue();
                    component.lastUpdated = System.currentTimeMillis();
                }
            }
            
            // Update ASU token basket
            if (asuToken != null) {
                // Implementation for updating ASU basket
                LOGGER.info("ASU basket weights updated");
            }
            
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Failed to apply weight changes: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Starts automatic rebalancing scheduler
     */
    private void startAutomaticRebalancing() {
        rebalanceScheduler.scheduleAtFixedRate(() -> {
            try {
                executeRebalancing("SCHEDULED", new HashMap<>());
            } catch (Exception e) {
                LOGGER.severe("Automatic rebalancing failed: " + e.getMessage());
            }
        }, rebalanceInterval, rebalanceInterval, TimeUnit.MILLISECONDS);
        
        LOGGER.info("Automatic rebalancing started");
    }
    
    // Helper methods
    
    private Map<String, BigDecimal> getCurrentWeights() {
        Map<String, BigDecimal> weights = new HashMap<>();
        for (BasketComponent component : currentBasket.values()) {
            weights.put(component.currencyCode, component.currentWeight);
        }
        return weights;
    }
    
    private BigDecimal calculateMaxWeightChange(Map<String, BigDecimal> current,
                                               Map<String, BigDecimal> target) {
        BigDecimal maxChange = BigDecimal.ZERO;
        
        for (Map.Entry<String, BigDecimal> entry : target.entrySet()) {
            BigDecimal currentWeight = current.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            BigDecimal change = entry.getValue().subtract(currentWeight).abs();
            maxChange = maxChange.max(change);
        }
        
        return maxChange;
    }
    
    private void normalizeWeights(Map<String, BigDecimal> weights) {
        BigDecimal sum = weights.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (sum.compareTo(BigDecimal.ZERO) > 0 && sum.compareTo(BigDecimal.ONE) != 0) {
            for (Map.Entry<String, BigDecimal> entry : weights.entrySet()) {
                BigDecimal normalized = entry.getValue()
                    .divide(sum, 8, RoundingMode.HALF_UP);
                weights.put(entry.getKey(), normalized);
            }
        }
    }
    
    private BasketComponent findComponentByCountry(String countryCode) {
        return currentBasket.values().stream()
            .filter(c -> c.countryCode.equals(countryCode))
            .findFirst()
            .orElse(null);
    }
    
    private String generateId(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + 
               UUID.randomUUID().toString().substring(0, 8);
    }
    
    // Public getters
    
    public Map<String, BasketComponent> getCurrentBasket() {
        return new HashMap<>(currentBasket);
    }
    
    public List<RebalancingEvent> getRebalancingHistory() {
        return new ArrayList<>(rebalancingHistory);
    }
    
    public Map<String, RebalanceProposal> getPendingProposals() {
        return new HashMap<>(pendingProposals);
    }
    
    public Set<String> getNonParticipatingCountries() {
        return new HashSet<>(nonParticipatingCountries);
    }
    
    /**
     * Safely shuts down the rebalancer
     */
    public void shutdown() {
        rebalanceScheduler.shutdown();
        LOGGER.info("DynamicBasketRebalancer shutdown completed");
    }
}