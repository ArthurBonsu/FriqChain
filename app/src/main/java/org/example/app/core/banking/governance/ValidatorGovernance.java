package org.example.app.core.banking.governance;

import org.example.app.core.types.Address;
import org.example.app.core.banking.registration.ValidatorNodeRegistry;
import org.example.app.core.crypto.Certificate;
import java.math.BigInteger;
import java.util.*;
import java.util.logging.Logger;

/**
 * ValidatorGovernance - Validator management, staking, slashing
 * 
 * Manages:
 * - Validator registration (with geographic distribution checks)
 * - Staking (minimum 100,000 ASU)
 * - Slashing (progressive: 5% - 50%)
 * - Performance tracking & reputation
 */
public class ValidatorGovernance {
    private static final Logger LOGGER = Logger.getLogger(ValidatorGovernance.class.getName());
    
    private static final BigInteger MIN_VALIDATOR_STAKE = new BigInteger("100000000000000000000000"); // 100k tokens
    private static final BigDecimal MAX_REGION_CONCENTRATION = new BigDecimal("0.33"); // 33%
    
    private final ValidatorNodeRegistry validatorRegistry;
    private final GovernanceAuditLog auditLog;
    
    public enum SlashingReason {
        DOWNTIME(new BigDecimal("0.05")),          // 5%
        INVALID_BLOCK(new BigDecimal("0.10")),     // 10%
        DOUBLE_SIGNING(new BigDecimal("0.25")),    // 25%
        DATA_MANIPULATION(new BigDecimal("0.50")), // 50%
        COLLUSION(new BigDecimal("1.00"));         // 100% + permanent ban
        
        private final BigDecimal slashPercentage;
        SlashingReason(BigDecimal percentage) {
            this.slashPercentage = percentage;
        }
        public BigDecimal getSlashPercentage() { return slashPercentage; }
    }
    
    public ValidatorGovernance(ValidatorNodeRegistry validatorRegistry,
                              GovernanceAuditLog auditLog) {
        this.validatorRegistry = validatorRegistry;
        this.auditLog = auditLog;
        
        LOGGER.info("ValidatorGovernance initialized");
    }
    
    /**
     * Registers a new validator (checks geographic distribution)
     */
    public String registerValidator(Address operatorAddress, BigInteger stakeAmount,
                                   int geographicRegion, String country, Certificate certificate) {
        // Verify minimum stake
        if (stakeAmount.compareTo(MIN_VALIDATOR_STAKE) < 0) {
            throw new IllegalArgumentException("Stake below minimum: " + stakeAmount);
        }
        
        // Check geographic diversity
        if (!verifyGeographicDiversity(geographicRegion)) {
            throw new IllegalStateException("Region " + geographicRegion + 
                " at capacity (>33%)");
        }
        
        // Register validator
        String validatorId = validatorRegistry.registerValidator(
            UUID.randomUUID().toString(),
            operatorAddress,
            certificate.getPublicKey(),
            certificate.getPublicKey(),
            certificate,
            operatorAddress.toString(),
            geographicRegion,
            country,
            stakeAmount
        );
        
        // Record in audit log
        auditLog.recordEvent(GovernanceAuditLog.AuditEventType.VALIDATOR_REGISTERED,
            operatorAddress.toString(), validatorId,
            Map.of("stakeAmount", stakeAmount.toString(),
                   "region", String.valueOf(geographicRegion),
                   "country", country),
            "Validator registered");
        
        LOGGER.info("Validator registered: " + validatorId + " (stake: " + stakeAmount + ")");
        return validatorId;
    }
    
    /**
     * Slashes a validator for misbehavior
     */
    public void slashValidator(String validatorId, SlashingReason reason, String evidence) {
        ValidatorNodeRegistry.ValidatorNode validator = validatorRegistry.getValidator(validatorId);
        if (validator == null) {
            throw new IllegalArgumentException("Validator not found: " + validatorId);
        }
        
        BigDecimal slashPercentage = reason.getSlashPercentage();
        BigInteger slashAmount = validator.getStakedAmount()
            .multiply(slashPercentage.toBigInteger())
            .divide(BigInteger.valueOf(100));
        
        // Execute slashing
        validatorRegistry.slashValidator(validatorId, 
            ValidatorNodeRegistry.ViolationType.DOUBLE_SIGNING, // Map to registry type
            evidence);
        
        // Record in audit log
        auditLog.recordEvent(GovernanceAuditLog.AuditEventType.VALIDATOR_SLASHED,
            "SYSTEM", validatorId,
            Map.of("reason", reason.toString(),
                   "slashPercentage", slashPercentage.toString(),
                   "slashAmount", slashAmount.toString(),
                   "evidence", evidence),
            "Validator slashed: " + reason);
        
        LOGGER.warning("Validator slashed: " + validatorId + " (" + slashPercentage + "%)");
        
        // Permanent ban for collusion
        if (reason == SlashingReason.COLLUSION) {
            validatorRegistry.removeValidator(validatorId, "Permanent ban for collusion");
            LOGGER.severe("Validator permanently banned: " + validatorId);
        }
    }
    
    /**
     * Distributes staking rewards to active validators
     */
    public void distributeStakingRewards() {
        validatorRegistry.distributeSlashingRewards();
        LOGGER.info("Staking rewards distributed");
    }
    
    private boolean verifyGeographicDiversity(int region) {
        Map<String, Object> stats = validatorRegistry.getStatistics();
        @SuppressWarnings("unchecked")
        Map<Integer, Long> regionDistribution = 
            (Map<Integer, Long>) stats.get("regionDistribution");
        
        long totalValidators = (Long) stats.get("activeValidators");
        long regionCount = regionDistribution.getOrDefault(region, 0L);
        
        if (totalValidators == 0) return true;
        
        double regionPercentage = (double)(regionCount + 1) / (totalValidators + 1);
        return regionPercentage <= MAX_REGION_CONCENTRATION.doubleValue();
    }
}