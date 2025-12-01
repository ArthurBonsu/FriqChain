package org.example.app.core.banking.governance;

import org.example.app.core.banking.tokenization.TokenMintingController;
import org.example.app.core.banking.reserves.ProofOfReservesContract;
import org.example.app.core.banking.reserves.TreasuryReserveTracker;
import org.example.app.core.block.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * EconomicPolicyRules - Manages continental economic policies
 * 
 * Purpose: Enforce economic rules for ASU basket, token supply, and reserves
 * Key Responsibilities:
 * - ASU basket rebalancing (no country >33%)
 * - Token supply control (120% minimum backing)
 * - Foreign currency exposure monitoring (≤10%)
 * - Emergency reserve deployment
 * - Automated policy enforcement
 */
public class EconomicPolicyRules {
    private static final Logger LOGGER = Logger.getLogger(EconomicPolicyRules.class.getName());
    
    // Policy thresholds
    private static final BigDecimal ASU_REBALANCE_THRESHOLD = new BigDecimal("0.10"); // 10% deviation
    private static final BigDecimal MAX_COUNTRY_ASU_WEIGHT = new BigDecimal("0.33"); // 33% max
    private static final BigDecimal MIN_RESERVE_BACKING = new BigDecimal("1.20"); // 120%
    private static final BigDecimal TARGET_RESERVE_BACKING = new BigDecimal("1.30"); // 130% (10% buffer)
    private static final BigDecimal MAX_FOREIGN_EXPOSURE = new BigDecimal("0.10"); // 10%
    
    // Monitoring intervals
    private static final long POLICY_CHECK_INTERVAL = 60 * 60 * 1000L; // 1 hour
    private static final long RESERVE_CHECK_INTERVAL = 15 * 60 * 1000L; // 15 minutes
    
    // Dependencies
    private final TokenMintingController mintingController;
    private final ProofOfReservesContract reservesContract;
    private final TreasuryReserveTracker reserveTracker;
    private final GovernanceAuditLog auditLog;
    private final VotingDAO votingDAO;
    
    // Scheduled executor
    private final ScheduledExecutorService scheduler;
    
    // State tracking
    private final Map<String, BigDecimal> currentASUWeights;
    private final Map<String, BigDecimal> targetASUWeights;
    private final Map<String, TokenHealthStatus> tokenHealthStatus;
    
    public enum PolicyViolationType {
        ASU_IMBALANCE,
        RESERVE_BELOW_MINIMUM,
        FOREIGN_EXPOSURE_EXCEEDED,
        MINTING_UNAUTHORIZED,
        COLLATERAL_INSUFFICIENT
    }
    
    public static class TokenHealthStatus {
        private final String tokenSymbol;
        private BigDecimal currentBacking;
        private BigDecimal currentSupply;
        private boolean mintingPaused;
        private long lastCheckTime;
        private String healthStatus; // "HEALTHY", "WARNING", "CRITICAL"
        
        public TokenHealthStatus(String tokenSymbol) {
            this.tokenSymbol = tokenSymbol;
            this.currentBacking = BigDecimal.ZERO;
            this.currentSupply = BigDecimal.ZERO;
            this.mintingPaused = false;
            this.lastCheckTime = System.currentTimeMillis();
            this.healthStatus = "HEALTHY";
        }
        
        // Getters and setters
        public String getTokenSymbol() { return tokenSymbol; }
        public BigDecimal getCurrentBacking() { return currentBacking; }
        public BigDecimal getCurrentSupply() { return currentSupply; }
        public boolean isMintingPaused() { return mintingPaused; }
        public long getLastCheckTime() { return lastCheckTime; }
        public String getHealthStatus() { return healthStatus; }
        
        public void setCurrentBacking(BigDecimal backing) { this.currentBacking = backing; }
        public void setCurrentSupply(BigDecimal supply) { this.currentSupply = supply; }
        public void setMintingPaused(boolean paused) { this.mintingPaused = paused; }
        public void setLastCheckTime(long time) { this.lastCheckTime = time; }
        public void setHealthStatus(String status) { this.healthStatus = status; }
    }
    
    public EconomicPolicyRules(TokenMintingController mintingController,
                               ProofOfReservesContract reservesContract,
                               TreasuryReserveTracker reserveTracker,
                               GovernanceAuditLog auditLog,
                               VotingDAO votingDAO) {
        this.mintingController = mintingController;
        this.reservesContract = reservesContract;
        this.reserveTracker = reserveTracker;
        this.auditLog = auditLog;
        this.votingDAO = votingDAO;
        
        this.currentASUWeights = new ConcurrentHashMap<>();
        this.targetASUWeights = new ConcurrentHashMap<>();
        this.tokenHealthStatus = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        startAutomatedPolicyEnforcement();
        LOGGER.info("EconomicPolicyRules initialized");
    }
    
    /**
     * Main policy evaluation and enforcement (called every hour)
     */
    public void evaluateAndEnforcePolicies() {
        LOGGER.info("Running policy evaluation cycle...");
        
        try {
            // 1. Check ASU basket balance
            if (isASURebalanceRequired()) {
                enforceASURebalancing();
            }
            
            // 2. Check token reserve backing
            enforceReserveBacking();
            
            // 3. Check foreign currency exposure (handled by ExposureCapManager)
            
            // 4. Adjust minting limits based on reserve growth
            adjustMintingLimits();
            
            LOGGER.info("Policy evaluation completed successfully");
            
        } catch (Exception e) {
            LOGGER.severe("Error in policy evaluation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Checks if ASU basket rebalancing is required
     */
    public boolean isASURebalanceRequired() {
        // Check if any country exceeds 33% or if deviation >10%
        for (Map.Entry<String, BigDecimal> entry : currentASUWeights.entrySet()) {
            String country = entry.getKey();
            BigDecimal currentWeight = entry.getValue();
            
            // Check max weight violation
            if (currentWeight.compareTo(MAX_COUNTRY_ASU_WEIGHT) > 0) {
                LOGGER.warning("ASU rebalance required: " + country + " at " + 
                              currentWeight + " > " + MAX_COUNTRY_ASU_WEIGHT);
                return true;
            }
            
            // Check deviation from target
            BigDecimal targetWeight = targetASUWeights.getOrDefault(country, BigDecimal.ZERO);
            BigDecimal deviation = currentWeight.subtract(targetWeight).abs();
            
            if (deviation.compareTo(ASU_REBALANCE_THRESHOLD) > 0) {
                LOGGER.warning("ASU rebalance required: " + country + " deviation " + 
                              deviation + " > " + ASU_REBALANCE_THRESHOLD);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Enforces ASU basket rebalancing
     */
    public void enforceASURebalancing() {
        LOGGER.info("Enforcing ASU basket rebalancing...");
        
        // Calculate target weights (equal distribution capped at 33%)
        Map<String, BigDecimal> newWeights = calculateTargetASUWeights();
        
        // Create governance proposal for rebalancing
        Map<String, String> proposalParams = new HashMap<>();
        proposalParams.put("action", "ASU_REBALANCE");
        proposalParams.put("currentWeights", currentASUWeights.toString());
        proposalParams.put("targetWeights", newWeights.toString());
        
        String proposalId = votingDAO.submitProposal(
            "ASU Basket Rebalancing",
            "Rebalance ASU basket to ensure no country exceeds 33% weight",
            null, // System proposal
            SupermajorityCalculator.ProposalType.STANDARD,
            proposalParams
        );
        
        // Record in audit log
        auditLog.recordEvent(GovernanceAuditLog.AuditEventType.ASU_REBALANCED,
            "SYSTEM", "ASU",
            proposalParams,
            "ASU basket rebalancing initiated");
        
        LOGGER.info("ASU rebalancing proposal submitted: " + proposalId);
    }
    
    /**
     * Enforces 120% minimum reserve backing for all tokens
     */
    public void enforceReserveBacking() {
        LOGGER.info("Checking reserve backing for all tokens...");
        
        // Get all national currency tokens
        List<String> tokens = getNationalCurrencyTokens();
        
        for (String token : tokens) {
            TokenHealthStatus status = tokenHealthStatus.computeIfAbsent(token,
                k -> new TokenHealthStatus(k));
            
            // Get current backing ratio
            BigDecimal backingRatio = reservesContract.calculateBackingRatio(token);
            status.setCurrentBacking(backingRatio);
            status.setLastCheckTime(System.currentTimeMillis());
            
            if (backingRatio.compareTo(MIN_RESERVE_BACKING) < 0) {
                // CRITICAL: Backing below 120%
                status.setHealthStatus("CRITICAL");
                handleCriticalBackingViolation(token, backingRatio);
                
            } else if (backingRatio.compareTo(TARGET_RESERVE_BACKING) < 0) {
                // WARNING: Backing below 130% (target with buffer)
                status.setHealthStatus("WARNING");
                LOGGER.warning(token + " backing at " + backingRatio + " (below 130% target)");
                
            } else {
                // HEALTHY
                status.setHealthStatus("HEALTHY");
                
                // Resume minting if it was paused
                if (status.isMintingPaused()) {
                    mintingController.resumeMinting(token);
                    status.setMintingPaused(false);
                    
                    auditLog.recordEvent(GovernanceAuditLog.AuditEventType.TOKEN_MINTING_RESUMED,
                        "SYSTEM", token,
                        Map.of("backingRatio", backingRatio.toString()),
                        "Minting resumed - backing restored");
                }
            }
        }
    }
    
    /**
     * Handles critical backing violation (<120%)
     */
    private void handleCriticalBackingViolation(String token, BigDecimal currentBacking) {
        LOGGER.severe("CRITICAL: " + token + " backing at " + currentBacking + " < 120%");
        
        TokenHealthStatus status = tokenHealthStatus.get(token);
        
        // 1. Immediately pause minting
        if (!status.isMintingPaused()) {
            mintingController.pauseMinting(token);
            status.setMintingPaused(true);
            
            auditLog.recordEvent(GovernanceAuditLog.AuditEventType.TOKEN_MINTING_PAUSED,
                "SYSTEM", token,
                Map.of("backingRatio", currentBacking.toString(),
                       "reason", "BELOW_MINIMUM_BACKING"),
                "Minting paused - critical backing violation");
        }
        
        // 2. Calculate required injection to reach 130% (target with buffer)
        BigDecimal currentSupply = status.getCurrentSupply();
        BigDecimal currentReserves = reserveTracker.getTotalReserves(token);
        BigDecimal requiredReserves = currentSupply.multiply(TARGET_RESERVE_BACKING);
        BigDecimal injectionAmount = requiredReserves.subtract(currentReserves);
        
        // 3. Create emergency proposal for reserve injection
        Map<String, String> proposalParams = new HashMap<>();
        proposalParams.put("action", "EMERGENCY_RESERVE_INJECTION");
        proposalParams.put("token", token);
        proposalParams.put("currentBacking", currentBacking.toString());
        proposalParams.put("targetBacking", TARGET_RESERVE_BACKING.toString());
        proposalParams.put("injectionAmount", injectionAmount.toString());
        
        String proposalId = votingDAO.submitProposal(
            "Emergency Reserve Injection: " + token,
            "Deploy emergency stability fund to restore " + token + " backing to 130%",
            null, // System proposal
            SupermajorityCalculator.ProposalType.EMERGENCY, // Fast-track
            proposalParams
        );
        
        auditLog.recordEvent(GovernanceAuditLog.AuditEventType.EMERGENCY_FUND_DEPLOYED,
            "SYSTEM", token,
            proposalParams,
            "Emergency reserve injection proposed");
        
        LOGGER.severe("Emergency reserve injection proposal: " + proposalId);
    }
    
    /**
     * Adjusts minting limits based on reserve growth
     */
    private void adjustMintingLimits() {
        List<String> tokens = getNationalCurrencyTokens();
        
        for (String token : tokens) {
            TokenHealthStatus status = tokenHealthStatus.get(token);
            if (status == null || !status.getHealthStatus().equals("HEALTHY")) {
                continue; // Skip unhealthy tokens
            }
            
            // Calculate safe minting limit based on available reserves
            BigDecimal availableReserves = reserveTracker.getAvailableReserves(token);
            BigDecimal currentSupply = status.getCurrentSupply();
            
            // Safe limit = (Available Reserves / 1.30) - Current Supply
            BigDecimal safeSupplyLimit = availableReserves
                .divide(TARGET_RESERVE_BACKING, 8, RoundingMode.HALF_UP);
            BigDecimal safeMintingLimit = safeSupplyLimit.subtract(currentSupply);
            
            if (safeMintingLimit.compareTo(BigDecimal.ZERO) > 0) {
                mintingController.setMintingLimit(token, safeMintingLimit);
                LOGGER.info(token + " minting limit adjusted to: " + safeMintingLimit);
            }
        }
    }
    
    /**
     * Updates ASU basket weights (called after rebalancing execution)
     */
    public void updateASUWeights(Map<String, BigDecimal> newWeights) {
        currentASUWeights.clear();
        currentASUWeights.putAll(newWeights);
        
        LOGGER.info("ASU weights updated: " + newWeights);
    }
    
    /**
     * Sets target ASU weights
     */
    public void setTargetASUWeights(Map<String, BigDecimal> targetWeights) {
        this.targetASUWeights.clear();
        this.targetASUWeights.putAll(targetWeights);
    }
    
    /**
     * Gets token health status
     */
    public TokenHealthStatus getTokenHealth(String token) {
        return tokenHealthStatus.get(token);
    }
    
    /**
     * Gets all token health statuses
     */
    public Map<String, TokenHealthStatus> getAllTokenHealth() {
        return new HashMap<>(tokenHealthStatus);
    }
    
    // Private helper methods
    
    private void startAutomatedPolicyEnforcement() {
        // Policy check every 1 hour
        scheduler.scheduleAtFixedRate(() -> {
            try {
                evaluateAndEnforcePolicies();
            } catch (Exception e) {
                LOGGER.severe("Error in automated policy enforcement: " + e.getMessage());
            }
        }, 5, 60, TimeUnit.MINUTES);
        
        // Reserve check every 15 minutes (more frequent)
        scheduler.scheduleAtFixedRate(() -> {
            try {
                enforceReserveBacking();
            } catch (Exception e) {
                LOGGER.severe("Error in reserve checking: " + e.getMessage());
            }
        }, 2, 15, TimeUnit.MINUTES);
        
        LOGGER.info("Automated policy enforcement started");
    }
    
    private Map<String, BigDecimal> calculateTargetASUWeights() {
        Map<String, BigDecimal> targetWeights = new HashMap<>();
        
        // Get all countries in ASU basket
        Set<String> countries = currentASUWeights.keySet();
        int countryCount = countries.size();
        
        if (countryCount == 0) return targetWeights;
        
        // Equal distribution capped at 33%
        BigDecimal equalWeight = BigDecimal.ONE.divide(
            BigDecimal.valueOf(countryCount), 8, RoundingMode.HALF_UP);
        
        BigDecimal cappedWeight = equalWeight.min(MAX_COUNTRY_ASU_WEIGHT);
        
        for (String country : countries) {
            targetWeights.put(country, cappedWeight);
        }
        
        // Normalize to ensure sum = 1.0
        BigDecimal totalWeight = targetWeights.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        for (String country : targetWeights.keySet()) {
            BigDecimal normalized = targetWeights.get(country)
                .divide(totalWeight, 8, RoundingMode.HALF_UP);
            targetWeights.put(country, normalized);
        }
        
        return targetWeights;
    }
    
    private List<String> getNationalCurrencyTokens() {
        // In production: query from token registry
        return Arrays.asList("NGN-Token", "GHS-Token", "KES-Token", "ZAR-Token");
    }
    
    public void shutdown() {
        scheduler.shutdown();
        LOGGER.info("EconomicPolicyRules shutdown");
    }
}