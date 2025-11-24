package org.example.app.core.blockchain.wallet;

import org.example.app.core.wallet.Wallet;
import org.example.app.core.wallet.TransactionBuilder;
import org.example.app.core.block.Transaction;
import org.example.app.core.types.Address;
import org.example.app.core.types.Amount;
import org.example.app.core.crypto.PKIManager;
import org.example.app.core.state.BlockchainState;
import org.example.app.core.consensus.Blockchain;

import java.security.*;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.time.Instant;

/**
 * ValidatorStakeWallet - Manages validator staking, slashing, and rewards
 * 
 * Features:
 * - Stake tokens to become validator
 * - Slashing for bad behavior (downtime, double-signing, rate manipulation)
 * - Reward distribution for block validation
 * - Validator rotation for geographic distribution
 * - Reputation scoring system
 * - Emergency validator removal
 * - Delegation support (future)
 */
public class ValidatorStakeWallet extends Wallet {
    private static final Logger LOGGER = Logger.getLogger(ValidatorStakeWallet.class.getName());
    
    // Validator identification
    private final String validatorId;
    private final String validatorName;
    private final String countryCode;
    private final String regionCode;
    
    // Staking
    private BigInteger stakedAmount;
    private final BigInteger minimumStake;
    private final Instant stakeStartTime;
    private ValidatorStatus status;
    
    // Reputation and performance
    private int reputationScore; // 0-100
    private int successfulValidations;
    private int failedValidations;
    private int missedBlocks;
    private long totalUptime;
    private long totalDowntime;
    
    // Slashing
    private final List<SlashEvent> slashHistory;
    private BigInteger totalSlashed;
    private final Map<String, SlashReason> slashReasons;
    
    // Rewards
    private BigInteger totalRewardsEarned;
    private BigInteger unclaimedRewards;
    private final List<RewardDistribution> rewardHistory;
    
    // Geographic distribution tracking
    private final Map<String, Integer> validatorsByRegion;
    private final BigDecimal maxRegionConcentration; // 33% max
    
    // Dependencies
    private final PKIManager pkiManager;
    private final BlockchainState blockchainState;
    private final Blockchain blockchain;
    
    /**
     * ValidatorStatus enumeration
     */
    public enum ValidatorStatus {
        PENDING, ACTIVE, SUSPENDED, SLASHED, RETIRED
    }
    
    /**
     * SlashEvent - Record of slashing penalty
     */
    public static class SlashEvent {
        private final String eventId;
        private final SlashReason reason;
        private final BigInteger slashAmount;
        private final String evidence;
        private final Instant timestamp;
        private final String reportedBy;
        
        public enum SlashReason {
            DOUBLE_SIGNING,
            DOWNTIME,
            RATE_MANIPULATION,
            INVALID_BLOCK,
            COLLUSION,
            DATA_MANIPULATION
        }
        
        public SlashEvent(SlashReason reason, BigInteger slashAmount, 
                         String evidence, String reportedBy) {
            this.eventId = UUID.randomUUID().toString();
            this.reason = reason;
            this.slashAmount = slashAmount;
            this.evidence = evidence;
            this.timestamp = Instant.now();
            this.reportedBy = reportedBy;
        }
        
        public String getEventId() { return eventId; }
        public SlashReason getReason() { return reason; }
        public BigInteger getSlashAmount() { return slashAmount; }
        public String getEvidence() { return evidence; }
        public Instant getTimestamp() { return timestamp; }
        public String getReportedBy() { return reportedBy; }
    }
    
    /**
     * RewardDistribution - Record of earned rewards
     */
    public static class RewardDistribution {
        private final String rewardId;
        private final BigInteger amount;
        private final String source; // "BLOCK_REWARD", "TRANSACTION_FEE", "ORACLE_FEE"
        private final long blockNumber;
        private final Instant timestamp;
        
        public RewardDistribution(BigInteger amount, String source, long blockNumber) {
            this.rewardId = UUID.randomUUID().toString();
            this.amount = amount;
            this.source = source;
            this.blockNumber = blockNumber;
            this.timestamp = Instant.now();
        }
        
        public String getRewardId() { return rewardId; }
        public BigInteger getAmount() { return amount; }
        public String getSource() { return source; }
        public long getBlockNumber() { return blockNumber; }
        public Instant getTimestamp() { return timestamp; }
    }
    
    /**
     * Constructor for ValidatorStakeWallet
     */
    public ValidatorStakeWallet(String validatorId, String validatorName, 
                               String countryCode, String regionCode,
                               BigInteger minimumStake,
                               BlockchainState blockchainState, Blockchain blockchain,
                               PKIManager pkiManager) throws NoSuchAlgorithmException {
        super();
        
        this.validatorId = validatorId;
        this.validatorName = validatorName;
        this.countryCode = countryCode.toUpperCase();
        this.regionCode = regionCode.toUpperCase();
        
        this.stakedAmount = BigInteger.ZERO;
        this.minimumStake = minimumStake;
        this.stakeStartTime = Instant.now();
        this.status = ValidatorStatus.PENDING;
        
        this.reputationScore = 80; // Start at 80/100
        this.successfulValidations = 0;
        this.failedValidations = 0;
        this.missedBlocks = 0;
        this.totalUptime = 0;
        this.totalDowntime = 0;
        
        this.slashHistory = Collections.synchronizedList(new ArrayList<>());
        this.totalSlashed = BigInteger.ZERO;
        this.slashReasons = new ConcurrentHashMap<>();
        
        this.totalRewardsEarned = BigInteger.ZERO;
        this.unclaimedRewards = BigInteger.ZERO;
        this.rewardHistory = Collections.synchronizedList(new ArrayList<>());
        
        this.validatorsByRegion = new ConcurrentHashMap<>();
        this.maxRegionConcentration = new BigDecimal("0.33");
        
        this.blockchainState = blockchainState;
        this.blockchain = blockchain;
        this.pkiManager = pkiManager;
        
        LOGGER.info(String.format("ValidatorStakeWallet created for %s (%s, %s)", 
            validatorName, countryCode, regionCode));
    }
    
    /**
     * Stakes tokens to become validator
     */
    public boolean stake(BigInteger amount) {
        if (amount.compareTo(minimumStake) < 0) {
            LOGGER.warning("Stake amount below minimum: " + amount);
            return false;
        }
        
        // Check geographic distribution
        if (!checkGeographicDistribution()) {
            LOGGER.warning("Geographic concentration limit exceeded for region: " + regionCode);
            return false;
        }
        
        stakedAmount = stakedAmount.add(amount);
        
        // Activate validator if minimum reached
        if (status == ValidatorStatus.PENDING && stakedAmount.compareTo(minimumStake) >= 0) {
            status = ValidatorStatus.ACTIVE;
            LOGGER.info("Validator activated: " + validatorName);
        }
        
        LOGGER.info(String.format("Staked %s tokens (total: %s)", 
            amount.toString(), stakedAmount.toString()));
        
        return true;
    }
    
    /**
     * Unstakes tokens (with cooldown period)
     */
    public boolean unstake(BigInteger amount) {
        if (amount.compareTo(stakedAmount) > 0) {
            LOGGER.warning("Cannot unstake more than staked amount");
            return false;
        }
        
        // Check if unstaking would drop below minimum
        BigInteger remainingStake = stakedAmount.subtract(amount);
        if (remainingStake.compareTo(BigInteger.ZERO) > 0 && 
            remainingStake.compareTo(minimumStake) < 0) {
            LOGGER.warning("Remaining stake would be below minimum");
            return false;
        }
        
        stakedAmount = stakedAmount.subtract(amount);
        
        // Deactivate if below minimum
        if (stakedAmount.compareTo(minimumStake) < 0) {
            status = ValidatorStatus.RETIRED;
            LOGGER.info("Validator retired: " + validatorName);
        }
        
        LOGGER.info(String.format("Unstaked %s tokens (remaining: %s)", 
            amount.toString(), stakedAmount.toString()));
        
        return true;
    }
    
    /**
     * Slashes stake for misbehavior
     */
    public boolean slash(SlashEvent.SlashReason reason, BigInteger slashAmount, 
                        String evidence, String reportedBy) {
        if (slashAmount.compareTo(stakedAmount) > 0) {
            slashAmount = stakedAmount; // Slash entire stake if amount exceeds
        }
        
        SlashEvent slashEvent = new SlashEvent(reason, slashAmount, evidence, reportedBy);
        slashHistory.add(slashEvent);
        
        stakedAmount = stakedAmount.subtract(slashAmount);
        totalSlashed = totalSlashed.add(slashAmount);
        
        // Update reputation
        reputationScore = Math.max(0, reputationScore - calculateReputationPenalty(reason));
        
        // Suspend or deactivate validator
        if (stakedAmount.compareTo(minimumStake) < 0) {
            status = ValidatorStatus.SLASHED;
            LOGGER.warning(String.format("Validator SLASHED and deactivated: %s", validatorName));
        } else {
            status = ValidatorStatus.SUSPENDED;
            LOGGER.warning(String.format("Validator SUSPENDED: %s", validatorName));
        }
        
        LOGGER.warning(String.format("Slash event: %s - %s tokens slashed for %s", 
            validatorName, slashAmount.toString(), reason));
        
        return true;
    }
    
    /**
     * Records successful validation
     */
    public void recordSuccessfulValidation(long blockNumber) {
        successfulValidations++;
        reputationScore = Math.min(100, reputationScore + 1);
        
        LOGGER.info(String.format("Successful validation recorded: block %d", blockNumber));
    }
    
    /**
     * Records failed validation
     */
    public void recordFailedValidation(String reason) {
        failedValidations++;
        reputationScore = Math.max(0, reputationScore - 2);
        
        LOGGER.warning(String.format("Failed validation recorded: %s", reason));
    }
    
    /**
     * Records missed block
     */
    public void recordMissedBlock(long blockNumber) {
        missedBlocks++;
        totalDowntime += 1;
        reputationScore = Math.max(0, reputationScore - 1);
        
        // Slash for excessive downtime
        if (missedBlocks % 10 == 0) {
            BigInteger downtimeSlash = stakedAmount.divide(BigInteger.valueOf(1000)); // 0.1%
            slash(SlashEvent.SlashReason.DOWNTIME, downtimeSlash, 
                  "Excessive missed blocks: " + missedBlocks, "SYSTEM");
        }
        
        LOGGER.warning(String.format("Missed block recorded: %d (total: %d)", 
            blockNumber, missedBlocks));
    }
    
    /**
     * Distributes rewards to validator
     */
    public void distributeReward(BigInteger amount, String source, long blockNumber) {
        RewardDistribution reward = new RewardDistribution(amount, source, blockNumber);
        rewardHistory.add(reward);
        
        unclaimedRewards = unclaimedRewards.add(amount);
        totalRewardsEarned = totalRewardsEarned.add(amount);
        
        LOGGER.info(String.format("Reward distributed: %s from %s (block %d)", 
            amount.toString(), source, blockNumber));
    }
    
    /**
     * Claims accumulated rewards
     */
    public BigInteger claimRewards() {
        if (unclaimedRewards.compareTo(BigInteger.ZERO) == 0) {
            LOGGER.info("No unclaimed rewards");
            return BigInteger.ZERO;
        }
        
        BigInteger claimed = unclaimedRewards;
        unclaimedRewards = BigInteger.ZERO;
        
        LOGGER.info(String.format("Rewards claimed: %s", claimed.toString()));
        
        return claimed;
    }
    
    /**
     * Checks geographic distribution compliance
     */
    private boolean checkGeographicDistribution() {
        int totalValidators = validatorsByRegion.values().stream()
            .mapToInt(Integer::intValue).sum();
        
        if (totalValidators == 0) return true;
        
        int regionValidators = validatorsByRegion.getOrDefault(regionCode, 0) + 1;
        BigDecimal regionPercentage = new BigDecimal(regionValidators)
            .divide(new BigDecimal(totalValidators + 1), 4, java.math.RoundingMode.HALF_UP);
        
        return regionPercentage.compareTo(maxRegionConcentration) <= 0;
    }
    
    /**
     * Calculates reputation penalty for slash reason
     */
    private int calculateReputationPenalty(SlashEvent.SlashReason reason) {
        switch (reason) {
            case DOUBLE_SIGNING:
            case COLLUSION:
                return 50; // Severe penalty
            case RATE_MANIPULATION:
            case DATA_MANIPULATION:
                return 30; // Major penalty
            case INVALID_BLOCK:
                return 15; // Moderate penalty
            case DOWNTIME:
                return 5; // Minor penalty
            default:
                return 10;
        }
    }
    
    /**
     * Calculates validator effectiveness
     */
    public BigDecimal getEffectiveness() {
        int totalValidations = successfulValidations + failedValidations;
        if (totalValidations == 0) return BigDecimal.ZERO;
        
        return new BigDecimal(successfulValidations)
            .divide(new BigDecimal(totalValidations), 4, java.math.RoundingMode.HALF_UP);
    }
    
    /**
     * Calculates uptime percentage
     */
    public BigDecimal getUptimePercentage() {
        long totalTime = totalUptime + totalDowntime;
        if (totalTime == 0) return BigDecimal.ONE;
        
        return new BigDecimal(totalUptime)
            .divide(new BigDecimal(totalTime), 4, java.math.RoundingMode.HALF_UP);
    }
    
    // Getters and utility methods
    
    public String getValidatorId() { return validatorId; }
    public String getValidatorName() { return validatorName; }
    public String getCountryCode() { return countryCode; }
    public String getRegionCode() { return regionCode; }
    
    public BigInteger getStakedAmount() { return stakedAmount; }
    public BigInteger getMinimumStake() { return minimumStake; }
    public ValidatorStatus getStatus() { return status; }
    
    public int getReputationScore() { return reputationScore; }
    public int getSuccessfulValidations() { return successfulValidations; }
    public int getFailedValidations() { return failedValidations; }
    public int getMissedBlocks() { return missedBlocks; }
    
    public BigInteger getTotalSlashed() { return totalSlashed; }
    public List<SlashEvent> getSlashHistory() { return new ArrayList<>(slashHistory); }
    
    public BigInteger getTotalRewardsEarned() { return totalRewardsEarned; }
    public BigInteger getUnclaimedRewards() { return unclaimedRewards; }
    public List<RewardDistribution> getRewardHistory() { return new ArrayList<>(rewardHistory); }
    
    public boolean isActive() {
        return status == ValidatorStatus.ACTIVE && 
               stakedAmount.compareTo(minimumStake) >= 0;
    }
    
    public Map<String, Object> getValidatorStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("validatorId", validatorId);
        stats.put("validatorName", validatorName);
        stats.put("status", status);
        stats.put("stakedAmount", stakedAmount);
        stats.put("reputationScore", reputationScore);
        stats.put("effectiveness", getEffectiveness());
        stats.put("uptime", getUptimePercentage());
        stats.put("successfulValidations", successfulValidations);
        stats.put("failedValidations", failedValidations);
        stats.put("missedBlocks", missedBlocks);
        stats.put("totalRewards", totalRewardsEarned);
        stats.put("totalSlashed", totalSlashed);
        return stats;
    }
    
    @Override
    public String toString() {
        return String.format("ValidatorStakeWallet{id='%s', name='%s', status=%s, " +
            "stake=%s, reputation=%d/100, effectiveness=%.2f%%}",
            validatorId, validatorName, status, stakedAmount.toString(),
            reputationScore, getEffectiveness().multiply(new BigDecimal("100")).doubleValue());
    }
}