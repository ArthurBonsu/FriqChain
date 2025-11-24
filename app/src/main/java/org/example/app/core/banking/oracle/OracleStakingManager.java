package org.example.app.core.banking.oracle;

import org.example.app.core.types.Address;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * OracleStakingManager - Manages staking and slashing for oracle reporters
 * 
 * Implements staking/slashing mechanisms with:
 * - Collateral requirements for oracle reporters
 * - Automated slashing for inaccurate data
 * - Stake rewards for accurate reporting
 * - Progressive slashing penalties
 * - Stake withdrawal with cooldown periods
 * - Emergency recovery mechanisms
 * 
 * Based on Implementation Guidebook Section VI: Oracle Data Integrity System
 */
public class OracleStakingManager {
    private static final Logger LOGGER = Logger.getLogger(OracleStakingManager.class.getName());
    
    // Staking parameters
    private static final BigDecimal MIN_VALIDATOR_STAKE = new BigDecimal("10000"); // 10,000 tokens
    private static final BigDecimal MIN_ORACLE_STAKE = new BigDecimal("5000");     // 5,000 tokens
    private static final BigDecimal MIN_BANK_ORACLE_STAKE = new BigDecimal("20000"); // 20,000 tokens
    
    // Slashing parameters
    private static final BigDecimal MINOR_SLASH_PERCENTAGE = new BigDecimal("0.05");  // 5%
    private static final BigDecimal MODERATE_SLASH_PERCENTAGE = new BigDecimal("0.15"); // 15%
    private static final BigDecimal MAJOR_SLASH_PERCENTAGE = new BigDecimal("0.30");  // 30%
    private static final BigDecimal SEVERE_SLASH_PERCENTAGE = new BigDecimal("0.50"); // 50%
    
    // Reward parameters
    private static final BigDecimal ACCURACY_REWARD_RATE = new BigDecimal("0.001"); // 0.1% per accurate submission
    private static final BigDecimal MAX_ANNUAL_REWARD_RATE = new BigDecimal("0.15"); // 15% APY cap
    
    // Withdrawal parameters
    private static final long WITHDRAWAL_COOLDOWN_MS = 604800000; // 7 days
    private static final long SLASH_COOLDOWN_MS = 2592000000L; // 30 days before unslashing
    
    // Stake tracking
    private final Map<String, StakePosition> stakePositions;
    private final Map<String, List<SlashEvent>> slashHistory;
    private final Map<String, List<RewardEvent>> rewardHistory;
    private final Map<String, WithdrawalRequest> pendingWithdrawals;
    
    // Total stakes
    private BigDecimal totalStaked;
    private BigDecimal totalSlashed;
    private BigDecimal totalRewarded;
    
    /**
     * Stake position for a reporter
     */
    public static class StakePosition {
        private final String reporterId;
        private final String reporterType;
        private BigDecimal stakedAmount;
        private BigDecimal lockedAmount;
        private BigDecimal rewardsAccrued;
        private final long stakeTime;
        private long lastRewardTime;
        private boolean isSlashed;
        private int slashCount;
        
        public StakePosition(String reporterId, String reporterType, BigDecimal initialStake) {
            this.reporterId = reporterId;
            this.reporterType = reporterType;
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
        private final long timestamp;
        
        public RewardEvent(String reporterId, BigDecimal amount, String reason) {
            this.reporterId = reporterId;
            this.amount = amount;
            this.reason = reason;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getReporterId() { return reporterId; }
        public BigDecimal getAmount() { return amount; }
        public String getReason() { return reason; }
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
        MINOR,      // Occasional inaccuracy
        MODERATE,   // Repeated inaccuracies
        MAJOR,      // Systematic manipulation
        SEVERE      // Malicious behavior
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
        
        LOGGER.info("OracleStakingManager initialized");
    }
    
    /**
     * Stakes tokens for a reporter
     */
    public boolean stake(String reporterId, String reporterType, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            LOGGER.warning("Invalid stake amount: " + amount);
            return false;
        }
        
        // Get minimum stake for reporter type
        BigDecimal minStake = getMinimumStake(reporterType);
        
        StakePosition position = stakePositions.get(reporterId);
        
        if (position == null) {
            // New stake position
            if (amount.compareTo(minStake) < 0) {
                LOGGER.warning(String.format("Stake below minimum: %s (required: %s)", amount, minStake));
                return false;
            }
            
            position = new StakePosition(reporterId, reporterType, amount);
            stakePositions.put(reporterId, position);
            
            LOGGER.info(String.format("New stake position: %s - %s tokens", reporterId, amount));
        } else {
            // Add to existing position
            position.addStake(amount);
            
            LOGGER.info(String.format("Stake increased: %s - %s tokens (total: %s)", 
                reporterId, amount, position.getStakedAmount()));
        }
        
        totalStaked = totalStaked.add(amount);
        
        return true;
    }
    
    /**
     * Initiates withdrawal request (with cooldown)
     */
    public boolean initiateWithdrawal(String reporterId, BigDecimal amount) {
        StakePosition position = stakePositions.get(reporterId);
        
        if (position == null) {
            LOGGER.warning("No stake position found: " + reporterId);
            return false;
        }
        
        if (position.isSlashed()) {
            LOGGER.warning("Cannot withdraw while slashed: " + reporterId);
            return false;
        }
        
        BigDecimal available = position.getAvailableAmount();
        
        if (amount.compareTo(available) > 0) {
            LOGGER.warning(String.format("Insufficient available stake: requested %s, available %s", 
                amount, available));
            return false;
        }
        
        // Check if withdrawal would bring stake below minimum
        BigDecimal minStake = getMinimumStake(position.getReporterType());
        BigDecimal remaining = position.getStakedAmount().subtract(amount);
        
        if (remaining.compareTo(minStake) < 0 && remaining.compareTo(BigDecimal.ZERO) > 0) {
            LOGGER.warning(String.format("Withdrawal would bring stake below minimum: %s (required: %s)",
                remaining, minStake));
            return false;
        }
        
        // Lock the amount
        position.lockAmount(amount);
        
        // Create withdrawal request
        WithdrawalRequest request = new WithdrawalRequest(reporterId, amount);
        pendingWithdrawals.put(reporterId, request);
        
        LOGGER.info(String.format("Withdrawal initiated: %s - %s tokens (unlock in %d days)",
            reporterId, amount, WITHDRAWAL_COOLDOWN_MS / 86400000));
        
        return true;
    }
    
    /**
     * Processes withdrawal after cooldown
     */
    public boolean processWithdrawal(String reporterId) {
        WithdrawalRequest request = pendingWithdrawals.get(reporterId);
        
        if (request == null) {
            LOGGER.warning("No withdrawal request found: " + reporterId);
            return false;
        }
        
        if (request.isProcessed()) {
            LOGGER.warning("Withdrawal already processed: " + reporterId);
            return false;
        }
        
        if (!request.isUnlocked()) {
            long remainingMs = request.getUnlockTime() - System.currentTimeMillis();
            LOGGER.warning(String.format("Withdrawal not yet unlocked: %d hours remaining",
                remainingMs / 3600000));
            return false;
        }
        
        StakePosition position = stakePositions.get(reporterId);
        
        if (position == null) {
            LOGGER.warning("No stake position found: " + reporterId);
            return false;
        }
        
        // Process withdrawal
        position.removeStake(request.getAmount());
        position.unlockAmount(request.getAmount());
        request.process();
        
        totalStaked = totalStaked.subtract(request.getAmount());
        
        LOGGER.info(String.format("Withdrawal processed: %s - %s tokens", 
            reporterId, request.getAmount()));
        
        // Remove from pending if fully withdrawn
        if (position.getStakedAmount().compareTo(BigDecimal.ZERO) == 0) {
            stakePositions.remove(reporterId);
            pendingWithdrawals.remove(reporterId);
        }
        
        return true;
    }
    
    /**
     * Slashes a reporter's stake
     */
    public BigDecimal slash(String reporterId, SlashSeverity severity, String reason) {
        StakePosition position = stakePositions.get(reporterId);
        
        if (position == null) {
            LOGGER.warning("No stake position found for slashing: " + reporterId);
            return BigDecimal.ZERO;
        }
        
        // Calculate slash amount based on severity
        BigDecimal slashPercentage = getSlashPercentage(severity);
        BigDecimal slashAmount = position.getStakedAmount()
            .multiply(slashPercentage)
            .setScale(2, RoundingMode.HALF_UP);
        
        // Apply progressive penalties for repeat offenders
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
        
        return slashAmount;
    }
    
    /**
     * Awards reward to reporter for accurate data
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
        
        // Calculate reward based on staked amount
        BigDecimal rewardAmount = position.getStakedAmount()
            .multiply(ACCURACY_REWARD_RATE)
            .setScale(2, RoundingMode.HALF_UP);
        
        // Apply annual cap
        long stakeDuration = System.currentTimeMillis() - position.getStakeTime();
        BigDecimal annualCap = position.getStakedAmount()
            .multiply(MAX_ANNUAL_REWARD_RATE);
        
        BigDecimal totalRewards = position.getRewardsAccrued().add(rewardAmount);
        BigDecimal annualizedRewards = totalRewards
            .multiply(new BigDecimal("31536000000")) // milliseconds in year
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
        RewardEvent event = new RewardEvent(reporterId, rewardAmount, reason);
        rewardHistory.computeIfAbsent(reporterId, k -> new ArrayList<>()).add(event);
        
        totalRewarded = totalRewarded.add(rewardAmount);
        totalStaked = totalStaked.add(rewardAmount);
        
        LOGGER.fine(String.format("Reporter rewarded: %s - %s tokens - Reason: %s",
            reporterId, rewardAmount, reason));
        
        return rewardAmount;
    }
    
    /**
     * Unslashes a reporter after cooldown period
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
        
        // Check if cooldown period has passed
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
    
    /**
     * Gets stake position
     */
    public StakePosition getStakePosition(String reporterId) {
        return stakePositions.get(reporterId);
    }
    
    /**
     * Gets slash history
     */
    public List<SlashEvent> getSlashHistory(String reporterId) {
        return new ArrayList<>(slashHistory.getOrDefault(reporterId, new ArrayList<>()));
    }
    
    /**
     * Gets reward history
     */
    public List<RewardEvent> getRewardHistory(String reporterId) {
        return new ArrayList<>(rewardHistory.getOrDefault(reporterId, new ArrayList<>()));
    }
    
    /**
     * Gets statistics
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
        return stats;
    }
}
