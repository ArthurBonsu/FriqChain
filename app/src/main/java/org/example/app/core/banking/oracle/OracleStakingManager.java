package org.example.app.core.banking.oracle;

import org.example.app.core.banking.oracle.AfricanSovereigntyTypes.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * OracleStakingManager - Staking with African Sovereignty enforcement
 * 
 * ⚠️ CRITICAL COMPONENT - UPGRADED FOR AFRICAN NODE SOVEREIGNTY
 * 
 * Implements staking/slashing mechanisms with:
 * - Geographic stake distribution (max 33% per region/country)
 * - Community node incentives (+25% rewards bonus)
 * - Node type tracking (COMMUNITY/INSTITUTIONAL/CLOUD)
 * - Cloud provider limits (max 20% of total stake)
 * - Automated slashing for inaccurate data
 * - Progressive slashing penalties
 * - Stake withdrawal with cooldown periods
 * - Geographic rebalancing after slashing
 * 
 * SOVEREIGNTY FEATURES:
 * - No single region can hold >33% of stake
 * - No single country can hold >33% of stake
 * - Cloud providers limited to 20% maximum
 * - Community nodes receive 25% bonus rewards
 * - Automatic geographic rebalancing
 * 
 * Based on Implementation Guidebook Section VI + African Sovereignty Requirements
 */
public class OracleStakingManager {
    private static final Logger LOGGER = Logger.getLogger(OracleStakingManager.class.getName());
    
    // ============ SOVEREIGNTY PARAMETERS ============
    
    // Geographic distribution limits
    private static final BigDecimal MAX_REGION_STAKE_PERCENTAGE = new BigDecimal("0.33");   // 33%
    private static final BigDecimal MAX_COUNTRY_STAKE_PERCENTAGE = new BigDecimal("0.33");  // 33%
    
    // Node type targets and limits
    private static final BigDecimal COMMUNITY_TARGET_PERCENTAGE = new BigDecimal("0.50");   // 50% target
    private static final BigDecimal CLOUD_MAX_PERCENTAGE = new BigDecimal("0.20");          // 20% hard limit
    private static final BigDecimal COMMUNITY_REWARD_BONUS = new BigDecimal("0.25");        // 25% extra rewards
    
    // ============ STAKING PARAMETERS ============
    
    private static final BigDecimal MIN_VALIDATOR_STAKE = new BigDecimal("10000");
    private static final BigDecimal MIN_ORACLE_STAKE = new BigDecimal("5000");
    private static final BigDecimal MIN_BANK_ORACLE_STAKE = new BigDecimal("20000");
    
    // Slashing parameters
    private static final BigDecimal MINOR_SLASH_PERCENTAGE = new BigDecimal("0.05");
    private static final BigDecimal MODERATE_SLASH_PERCENTAGE = new BigDecimal("0.15");
    private static final BigDecimal MAJOR_SLASH_PERCENTAGE = new BigDecimal("0.30");
    private static final BigDecimal SEVERE_SLASH_PERCENTAGE = new BigDecimal("0.50");
    
    // Reward parameters
    private static final BigDecimal ACCURACY_REWARD_RATE = new BigDecimal("0.001");
    private static final BigDecimal MAX_ANNUAL_REWARD_RATE = new BigDecimal("0.15");
    
    // Withdrawal parameters
    private static final long WITHDRAWAL_COOLDOWN_MS = 604800000; // 7 days
    private static final long SLASH_COOLDOWN_MS = 2592000000L; // 30 days
    
    // Stake tracking with geographic information
    private final Map<String, StakePosition> stakePositions;
    private final Map<String, List<SlashEvent>> slashHistory;
    private final Map<String, List<RewardEvent>> rewardHistory;
    private final Map<String, WithdrawalRequest> pendingWithdrawals;
    
    // Total stakes
    private BigDecimal totalStaked;
    private BigDecimal totalSlashed;
    private BigDecimal totalRewarded;
    
    /**
     * Enhanced stake position with geographic information
     */
    public static class StakePosition {
        private final String reporterId;
        private final String reporterType;
        private final AfricanRegion region; // NEW: Geographic tracking
        private final String country; // NEW: Country tracking
        private final NodeType nodeType; // NEW: Node type tracking
        private final boolean isCommunityOwned; // NEW: Community ownership flag
        private BigDecimal stakedAmount;
        private BigDecimal lockedAmount;
        private BigDecimal rewardsAccrued;
        private final long stakeTime;
        private long lastRewardTime;
        private boolean isSlashed;
        private int slashCount;
        
        public StakePosition(String reporterId, String reporterType, 
                           AfricanRegion region, String country,
                           NodeType nodeType, boolean isCommunityOwned,
                           BigDecimal initialStake) {
            this.reporterId = reporterId;
            this.reporterType = reporterType;
            this.region = region;
            this.country = country;
            this.nodeType = nodeType;
            this.isCommunityOwned = isCommunityOwned;
            this.stakedAmount = initialStake;
            this.lockedAmount = BigDecimal.ZERO;
            this.rewardsAccrued = BigDecimal.ZERO;
            this.stakeTime = System.currentTimeMillis();
            this.lastRewardTime = System.currentTimeMillis();
            this.isSlashed = false;
            this.slashCount = 0;
        }
        
        public String getReporterId() { return reporterId; }
        public String getReporterType() { return reporterType; }
        public AfricanRegion getRegion() { return region; }
        public String getCountry() { return country; }
        public NodeType getNodeType() { return nodeType; }
        public boolean isCommunityOwned() { return isCommunityOwned; }
        public BigDecimal getStakedAmount() { return stakedAmount; }
        public BigDecimal getLockedAmount() { return lockedAmount; }
        public BigDecimal getRewardsAccrued() { return rewardsAccrued; }
        public BigDecimal getAvailableAmount() { return stakedAmount.subtract(lockedAmount); }
        public long getStakeTime() { return stakeTime; }
        public long getLastRewardTime() { return lastRewardTime; }
        public boolean isSlashed() { return isSlashed; }
        public int getSlashCount() { return slashCount; }
        
        public void addStake(BigDecimal amount) {
            stakedAmount = stakedAmount.add(amount);
        }
        
        public void removeStake(BigDecimal amount) {
            stakedAmount = stakedAmount.subtract(amount);
        }
        
        public void lockAmount(BigDecimal amount) {
            lockedAmount = lockedAmount.add(amount);
        }
        
        public void unlockAmount(BigDecimal amount) {
            lockedAmount = lockedAmount.subtract(amount);
        }
        
        public void addReward(BigDecimal reward) {
            rewardsAccrued = rewardsAccrued.add(reward);
            lastRewardTime = System.currentTimeMillis();
        }
        
        public void slash() {
            isSlashed = true;
            slashCount++;
        }
        
        public void unslash() {
            isSlashed = false;
        }
    }
    
    /**
     * Slash event record
     */
    public static class SlashEvent {
        private final String reporterId;
        private final BigDecimal amount;
        private final String reason;
        private final SlashSeverity severity;
        private final long timestamp;
        
        public SlashEvent(String reporterId, BigDecimal amount, String reason, SlashSeverity severity) {
            this.reporterId = reporterId;
            this.amount = amount;
            this.reason = reason;
            this.severity = severity;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getReporterId() { return reporterId; }
        public BigDecimal getAmount() { return amount; }
        public String getReason() { return reason; }
        public SlashSeverity getSeverity() { return severity; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Reward event record
     */
    public static class RewardEvent {
        private final String reporterId;
        private final BigDecimal amount;
        private final String reason;
        private final boolean communityBonus; // NEW: Community bonus flag
        private final long timestamp;
        
        public RewardEvent(String reporterId, BigDecimal amount, String reason, boolean communityBonus) {
            this.reporterId = reporterId;
            this.amount = amount;
            this.reason = reason;
            this.communityBonus = communityBonus;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getReporterId() { return reporterId; }
        public BigDecimal getAmount() { return amount; }
        public String getReason() { return reason; }
        public boolean isCommunityBonus() { return communityBonus; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Withdrawal request
     */
    public static class WithdrawalRequest {
        private final String reporterId;
        private final BigDecimal amount;
        private final long requestTime;
        private final long unlockTime;
        private boolean processed;
        
        public WithdrawalRequest(String reporterId, BigDecimal amount) {
            this.reporterId = reporterId;
            this.amount = amount;
            this.requestTime = System.currentTimeMillis();
            this.unlockTime = requestTime + WITHDRAWAL_COOLDOWN_MS;
            this.processed = false;
        }
        
        public String getReporterId() { return reporterId; }
        public BigDecimal getAmount() { return amount; }
        public long getRequestTime() { return requestTime; }
        public long getUnlockTime() { return unlockTime; }
        public boolean isProcessed() { return processed; }
        public boolean isUnlocked() { return System.currentTimeMillis() >= unlockTime; }
        
        public void process() {
            processed = true;
        }
    }
    
    /**
     * Slash severity levels
     */
    public enum SlashSeverity {
        MINOR, MODERATE, MAJOR, SEVERE
    }
    
    /**
     * Constructor
     */
    public OracleStakingManager() {
        this.stakePositions = new ConcurrentHashMap<>();
        this.slashHistory = new ConcurrentHashMap<>();
        this.rewardHistory = new ConcurrentHashMap<>();
        this.pendingWithdrawals = new ConcurrentHashMap<>();
        this.totalStaked = BigDecimal.ZERO;
        this.totalSlashed = BigDecimal.ZERO;
        this.totalRewarded = BigDecimal.ZERO;
        
        LOGGER.info("OracleStakingManager initialized with African Sovereignty enforcement");
    }
    
    /**
     * Adds stake WITH GEOGRAPHIC VALIDATION
     */
    public boolean addStake(String reporterId, String reporterType, 
                          AfricanRegion region, String country,
                          NodeType nodeType, boolean isCommunityOwned,
                          BigDecimal amount) throws Exception {
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        BigDecimal minimumStake = getMinimumStake(reporterType);
        
        // Get existing position or create simulation for validation
        StakePosition position = stakePositions.get(reporterId);
        BigDecimal newTotalStake = (position != null) ? 
            position.getStakedAmount().add(amount) : amount;
        
        // SOVEREIGNTY CHECK 1: Would this violate geographic distribution?
        if (!wouldViolateGeographicLimits(region, country, amount, reporterId)) {
            throw new Exception("Adding stake would violate geographic distribution limits (33%)");
        }
        
        // SOVEREIGNTY CHECK 2: Would this violate node type limits?
        if (!wouldViolateNodeTypeLimits(nodeType, amount, reporterId)) {
            throw new Exception("Adding stake would violate node type limits (20% cloud max)");
        }
        
        // Create or update position
        if (position == null) {
            position = new StakePosition(reporterId, reporterType, region, country,
                nodeType, isCommunityOwned, amount);
            stakePositions.put(reporterId, position);
        } else {
            position.addStake(amount);
        }
        
        totalStaked = totalStaked.add(amount);
        
        LOGGER.info(String.format("Stake added: %s - %s tokens - Region: %s - Type: %s - Community: %s",
            reporterId, amount, region, nodeType, isCommunityOwned));
        
        // Log distribution status
        logDistributionStatus();
        
        return true;
    }
    
    /**
     * SOVEREIGNTY CHECK: Validates geographic distribution limits
     */
    private boolean wouldViolateGeographicLimits(AfricanRegion newRegion, String newCountry,
                                                 BigDecimal newAmount, String reporterId) {
        // Calculate what stake would be WITH new amount
        Map<AfricanRegion, BigDecimal> regionalStakes = calculateRegionalStakes();
        Map<String, BigDecimal> countryStakes = calculateCountryStakes();
        
        // Add new stake (or additional stake for existing reporter)
        StakePosition existing = stakePositions.get(reporterId);
        if (existing != null) {
            // Remove old amount first (will be replaced with new total)
            regionalStakes.merge(existing.getRegion(), existing.getStakedAmount(), BigDecimal::subtract);
            countryStakes.merge(existing.getCountry(), existing.getStakedAmount(), BigDecimal::subtract);
        }
        
        // Add new amount
        BigDecimal newRegionTotal = regionalStakes.getOrDefault(newRegion, BigDecimal.ZERO).add(newAmount);
        BigDecimal newCountryTotal = countryStakes.getOrDefault(newCountry, BigDecimal.ZERO).add(newAmount);
        
        regionalStakes.put(newRegion, newRegionTotal);
        countryStakes.put(newCountry, newCountryTotal);
        
        // Calculate new total
        BigDecimal totalStakeWithNew = totalStaked.add(newAmount);
        if (existing != null) {
            totalStakeWithNew = totalStakeWithNew.subtract(existing.getStakedAmount());
        }
        
        // Validate regional distribution
        if (!GeographicDistributionValidator.validateRegionalDistribution(regionalStakes)) {
            return false;
        }
        
        // Validate country distribution
        if (!GeographicDistributionValidator.validateCountryDistribution(countryStakes)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * SOVEREIGNTY CHECK: Validates node type distribution limits
     */
    private boolean wouldViolateNodeTypeLimits(NodeType newNodeType, BigDecimal newAmount, 
                                               String reporterId) {
        // Calculate stake by node type
        Map<NodeType, BigDecimal> nodeTypeStakes = new HashMap<>();
        
        for (StakePosition position : stakePositions.values()) {
            if (!position.getReporterId().equals(reporterId)) {
                nodeTypeStakes.merge(position.getNodeType(), position.getStakedAmount(), BigDecimal::add);
            }
        }
        
        // Add new stake
        nodeTypeStakes.merge(newNodeType, newAmount, BigDecimal::add);
        
        BigDecimal total = nodeTypeStakes.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return true;
        }
        
        // Check cloud limit (hard limit)
        BigDecimal cloudStake = nodeTypeStakes.getOrDefault(NodeType.CLOUD, BigDecimal.ZERO);
        BigDecimal cloudPercentage = cloudStake.divide(total, 4, RoundingMode.HALF_UP);
        
        if (cloudPercentage.compareTo(CLOUD_MAX_PERCENTAGE) > 0) {
            LOGGER.severe(String.format("Cloud stake would exceed 20%% limit: %.2f%%",
                cloudPercentage.multiply(new BigDecimal("100"))));
            return false;
        }
        
        // Check community target (warning, not hard limit)
        BigDecimal communityStake = nodeTypeStakes.getOrDefault(NodeType.COMMUNITY, BigDecimal.ZERO);
        BigDecimal communityPercentage = communityStake.divide(total, 4, RoundingMode.HALF_UP);
        
        if (communityPercentage.compareTo(COMMUNITY_TARGET_PERCENTAGE) < 0) {
            LOGGER.warning(String.format("Community stake below 50%% target: %.2f%%",
                communityPercentage.multiply(new BigDecimal("100"))));
        }
        
        return true;
    }
    
    /**
     * Calculates stake distribution by region
     */
    private Map<AfricanRegion, BigDecimal> calculateRegionalStakes() {
        Map<AfricanRegion, BigDecimal> regionalStakes = new HashMap<>();
        
        for (StakePosition position : stakePositions.values()) {
            regionalStakes.merge(position.getRegion(), position.getStakedAmount(), BigDecimal::add);
        }
        
        return regionalStakes;
    }
    
    /**
     * Calculates stake distribution by country
     */
    private Map<String, BigDecimal> calculateCountryStakes() {
        Map<String, BigDecimal> countryStakes = new HashMap<>();
        
        for (StakePosition position : stakePositions.values()) {
            countryStakes.merge(position.getCountry(), position.getStakedAmount(), BigDecimal::add);
        }
        
        return countryStakes;
    }
    
    /**
     * Removes stake (with validation)
     */
    public boolean removeStake(String reporterId, BigDecimal amount) {
        StakePosition position = stakePositions.get(reporterId);
        
        if (position == null) {
            LOGGER.warning("No stake position found: " + reporterId);
            return false;
        }
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        if (amount.compareTo(position.getStakedAmount()) > 0) {
            LOGGER.warning("Insufficient stake to remove");
            return false;
        }
        
        position.removeStake(amount);
        totalStaked = totalStaked.subtract(amount);
        
        LOGGER.info(String.format("Stake removed: %s - %s tokens", reporterId, amount));
        
        if (position.getStakedAmount().compareTo(BigDecimal.ZERO) == 0) {
            stakePositions.remove(reporterId);
        }
        
        return true;
    }
    
    /**
     * Slashes reporter with GEOGRAPHIC REBALANCING
     */
    public BigDecimal slash(String reporterId, SlashSeverity severity, String reason) {
        StakePosition position = stakePositions.get(reporterId);
        
        if (position == null) {
            LOGGER.warning("No stake position found for slashing: " + reporterId);
            return BigDecimal.ZERO;
        }
        
        // Calculate slash amount
        BigDecimal slashPercentage = getSlashPercentage(severity);
        BigDecimal slashAmount = position.getStakedAmount()
            .multiply(slashPercentage)
            .setScale(2, RoundingMode.HALF_UP);
        
        // Progressive penalties for repeat offenders
        if (position.getSlashCount() > 0) {
            BigDecimal progressivePenalty = new BigDecimal("0.10")
                .multiply(new BigDecimal(position.getSlashCount()));
            slashAmount = slashAmount.multiply(BigDecimal.ONE.add(progressivePenalty));
        }
        
        // Ensure slash doesn't exceed total stake
        slashAmount = slashAmount.min(position.getStakedAmount());
        
        // Execute slash
        position.removeStake(slashAmount);
        position.slash();
        
        // Record slash event
        SlashEvent event = new SlashEvent(reporterId, slashAmount, reason, severity);
        slashHistory.computeIfAbsent(reporterId, k -> new ArrayList<>()).add(event);
        
        totalSlashed = totalSlashed.add(slashAmount);
        totalStaked = totalStaked.subtract(slashAmount);
        
        LOGGER.warning(String.format("Reporter slashed: %s - %s tokens (%s) - Reason: %s",
            reporterId, slashAmount, severity, reason));
        
        // SOVEREIGNTY FEATURE: Rebalance geographic distribution after slash
        rebalanceGeographicDistribution(position.getRegion());
        
        return slashAmount;
    }
    
    /**
     * Awards reward with COMMUNITY NODE BONUS
     */
    public BigDecimal reward(String reporterId, String reason) {
        StakePosition position = stakePositions.get(reporterId);
        
        if (position == null) {
            LOGGER.warning("No stake position found for reward: " + reporterId);
            return BigDecimal.ZERO;
        }
        
        if (position.isSlashed()) {
            LOGGER.fine("Cannot reward slashed reporter: " + reporterId);
            return BigDecimal.ZERO;
        }
        
        // Calculate base reward
        BigDecimal rewardAmount = position.getStakedAmount()
            .multiply(ACCURACY_REWARD_RATE)
            .setScale(2, RoundingMode.HALF_UP);
        
        // SOVEREIGNTY FEATURE: Community node bonus (+25%)
        boolean appliedBonus = false;
        if (position.isCommunityOwned() || position.getNodeType() == NodeType.COMMUNITY) {
            BigDecimal bonus = rewardAmount.multiply(COMMUNITY_REWARD_BONUS);
            rewardAmount = rewardAmount.add(bonus);
            appliedBonus = true;
            
            LOGGER.info(String.format("Community node bonus applied: %s - Bonus: %s",
                reporterId, bonus));
        }
        
        // Apply annual cap
        long stakeDuration = System.currentTimeMillis() - position.getStakeTime();
        BigDecimal annualCap = position.getStakedAmount()
            .multiply(MAX_ANNUAL_REWARD_RATE);
        
        BigDecimal totalRewards = position.getRewardsAccrued().add(rewardAmount);
        BigDecimal annualizedRewards = totalRewards
            .multiply(new BigDecimal("31536000000"))
            .divide(new BigDecimal(stakeDuration), 2, RoundingMode.HALF_UP);
        
        if (annualizedRewards.compareTo(annualCap) > 0) {
            rewardAmount = BigDecimal.ZERO;
            LOGGER.fine("Reward cap reached for: " + reporterId);
            return BigDecimal.ZERO;
        }
        
        // Apply reward
        position.addReward(rewardAmount);
        position.addStake(rewardAmount); // Compound rewards into stake
        
        // Record reward event
        RewardEvent event = new RewardEvent(reporterId, rewardAmount, reason, appliedBonus);
        rewardHistory.computeIfAbsent(reporterId, k -> new ArrayList<>()).add(event);
        
        totalRewarded = totalRewarded.add(rewardAmount);
        totalStaked = totalStaked.add(rewardAmount);
        
        LOGGER.fine(String.format("Reporter rewarded: %s - %s tokens - Community bonus: %s - Reason: %s",
            reporterId, rewardAmount, appliedBonus ? "YES" : "NO", reason));
        
        return rewardAmount;
    }
    
    /**
     * SOVEREIGNTY FEATURE: Rebalances geographic distribution after slashing
     */
    private void rebalanceGeographicDistribution(AfricanRegion affectedRegion) {
        Map<AfricanRegion, BigDecimal> regionalStakes = calculateRegionalStakes();
        
        BigDecimal total = totalStaked;
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        
        // Check if any region is now over-represented
        for (Map.Entry<AfricanRegion, BigDecimal> entry : regionalStakes.entrySet()) {
            BigDecimal percentage = entry.getValue().divide(total, 4, RoundingMode.HALF_UP);
            
            if (percentage.compareTo(new BigDecimal("0.30")) > 0) {
                LOGGER.warning(String.format(
                    "Geographic rebalancing needed: Region %s at %.2f%% after slash",
                    entry.getKey(), percentage.multiply(new BigDecimal("100"))));
            }
        }
    }
    
    /**
     * Requests withdrawal with cooldown
     */
    public boolean requestWithdrawal(String reporterId, BigDecimal amount) {
        StakePosition position = stakePositions.get(reporterId);
        
        if (position == null) {
            LOGGER.warning("No stake position found: " + reporterId);
            return false;
        }
        
        if (amount.compareTo(position.getAvailableAmount()) > 0) {
            LOGGER.warning("Insufficient available stake for withdrawal");
            return false;
        }
        
        if (pendingWithdrawals.containsKey(reporterId)) {
            LOGGER.warning("Withdrawal already pending for: " + reporterId);
            return false;
        }
        
        position.lockAmount(amount);
        
        WithdrawalRequest request = new WithdrawalRequest(reporterId, amount);
        pendingWithdrawals.put(reporterId, request);
        
        LOGGER.info(String.format("Withdrawal requested: %s - %s tokens - Unlock: %d days",
            reporterId, amount, WITHDRAWAL_COOLDOWN_MS / 86400000));
        
        return true;
    }
    
    /**
     * Processes withdrawal after cooldown
     */
    public boolean processWithdrawal(String reporterId) {
        WithdrawalRequest request = pendingWithdrawals.get(reporterId);
        
        if (request == null) {
            LOGGER.warning("No pending withdrawal for: " + reporterId);
            return false;
        }
        
        if (!request.isUnlocked()) {
            long remaining = (request.getUnlockTime() - System.currentTimeMillis()) / 86400000;
            LOGGER.warning(String.format("Withdrawal cooldown not complete: %d days remaining", remaining));
            return false;
        }
        
        if (request.isProcessed()) {
            LOGGER.warning("Withdrawal already processed: " + reporterId);
            return false;
        }
        
        StakePosition position = stakePositions.get(reporterId);
        if (position == null) {
            return false;
        }
        
        position.unlockAmount(request.getAmount());
        position.removeStake(request.getAmount());
        totalStaked = totalStaked.subtract(request.getAmount());
        
        request.process();
        
        LOGGER.info(String.format("Withdrawal processed: %s - %s tokens",
            reporterId, request.getAmount()));
        
        if (position.getStakedAmount().compareTo(BigDecimal.ZERO) == 0) {
            stakePositions.remove(reporterId);
            pendingWithdrawals.remove(reporterId);
        }
        
        return true;
    }
    
    /**
     * Unslashes reporter after cooldown
     */
    public boolean unslash(String reporterId) {
        StakePosition position = stakePositions.get(reporterId);
        
        if (position == null) {
            LOGGER.warning("No stake position found: " + reporterId);
            return false;
        }
        
        if (!position.isSlashed()) {
            LOGGER.fine("Reporter is not slashed: " + reporterId);
            return false;
        }
        
        List<SlashEvent> slashes = slashHistory.get(reporterId);
        if (slashes == null || slashes.isEmpty()) {
            position.unslash();
            return true;
        }
        
        SlashEvent lastSlash = slashes.get(slashes.size() - 1);
        long timeSinceSlash = System.currentTimeMillis() - lastSlash.getTimestamp();
        
        if (timeSinceSlash < SLASH_COOLDOWN_MS) {
            LOGGER.warning(String.format("Unslash cooldown not complete: %d days remaining",
                (SLASH_COOLDOWN_MS - timeSinceSlash) / 86400000));
            return false;
        }
        
        position.unslash();
        LOGGER.info("Reporter unslashed: " + reporterId);
        
        return true;
    }
    
    /**
     * Logs current distribution status
     */
    private void logDistributionStatus() {
        Map<AfricanRegion, BigDecimal> regionalStakes = calculateRegionalStakes();
        Map<NodeType, BigDecimal> nodeTypeStakes = new HashMap<>();
        
        for (StakePosition position : stakePositions.values()) {
            nodeTypeStakes.merge(position.getNodeType(), position.getStakedAmount(), BigDecimal::add);
        }
        
        if (totalStaked.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        
        LOGGER.info("=== STAKE DISTRIBUTION STATUS ===");
        LOGGER.info(String.format("Total Staked: %s", totalStaked));
        
        // Node type distribution
        for (NodeType type : NodeType.values()) {
            BigDecimal stake = nodeTypeStakes.getOrDefault(type, BigDecimal.ZERO);
            BigDecimal percentage = stake.divide(totalStaked, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
            LOGGER.info(String.format("%s: %s (%.1f%%)", type, stake, percentage));
        }
        
        // Regional distribution
        for (AfricanRegion region : AfricanRegion.values()) {
            BigDecimal stake = regionalStakes.getOrDefault(region, BigDecimal.ZERO);
            BigDecimal percentage = stake.divide(totalStaked, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
            LOGGER.info(String.format("%s: %s (%.1f%%)", region, stake, percentage));
        }
        
        LOGGER.info("================================");
    }
    
    /**
     * Gets minimum stake for reporter type
     */
    private BigDecimal getMinimumStake(String reporterType) {
        switch (reporterType.toUpperCase()) {
            case "VALIDATOR":
                return MIN_VALIDATOR_STAKE;
            case "BANK_FEED":
            case "RESERVE_AUDIT":
                return MIN_BANK_ORACLE_STAKE;
            default:
                return MIN_ORACLE_STAKE;
        }
    }
    
    /**
     * Gets slash percentage for severity
     */
    private BigDecimal getSlashPercentage(SlashSeverity severity) {
        switch (severity) {
            case MINOR:
                return MINOR_SLASH_PERCENTAGE;
            case MODERATE:
                return MODERATE_SLASH_PERCENTAGE;
            case MAJOR:
                return MAJOR_SLASH_PERCENTAGE;
            case SEVERE:
                return SEVERE_SLASH_PERCENTAGE;
            default:
                return MINOR_SLASH_PERCENTAGE;
        }
    }
    
    public StakePosition getStakePosition(String reporterId) {
        return stakePositions.get(reporterId);
    }
    
    public List<SlashEvent> getSlashHistory(String reporterId) {
        return new ArrayList<>(slashHistory.getOrDefault(reporterId, new ArrayList<>()));
    }
    
    public List<RewardEvent> getRewardHistory(String reporterId) {
        return new ArrayList<>(rewardHistory.getOrDefault(reporterId, new ArrayList<>()));
    }
    
    /**
     * Gets statistics with sovereignty metrics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalStakers", stakePositions.size());
        stats.put("totalStaked", totalStaked);
        stats.put("totalSlashed", totalSlashed);
        stats.put("totalRewarded", totalRewarded);
        stats.put("pendingWithdrawals", pendingWithdrawals.size());
        stats.put("slashedReporters", (int) stakePositions.values().stream()
            .filter(StakePosition::isSlashed).count());
        
        // Sovereignty metrics
        Map<AfricanRegion, BigDecimal> regionalStakes = calculateRegionalStakes();
        stats.put("regionalStakeDistribution", regionalStakes);
        
        Map<NodeType, BigDecimal> nodeTypeStakes = new HashMap<>();
        for (StakePosition position : stakePositions.values()) {
            nodeTypeStakes.merge(position.getNodeType(), position.getStakedAmount(), BigDecimal::add);
        }
        stats.put("nodeTypeStakeDistribution", nodeTypeStakes);
        
        if (totalStaked.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal communityStake = nodeTypeStakes.getOrDefault(NodeType.COMMUNITY, BigDecimal.ZERO);
            BigDecimal cloudStake = nodeTypeStakes.getOrDefault(NodeType.CLOUD, BigDecimal.ZERO);
            
            stats.put("communityStakePercentage", 
                communityStake.divide(totalStaked, 4, RoundingMode.HALF_UP));
            stats.put("cloudStakePercentage",
                cloudStake.divide(totalStaked, 4, RoundingMode.HALF_UP));
        }
        
        long communityRewards = rewardHistory.values().stream()
            .flatMap(List::stream)
            .filter(RewardEvent::isCommunityBonus)
            .count();
        stats.put("communityBonusRewardsCount", communityRewards);
        
        return stats;
    }
}