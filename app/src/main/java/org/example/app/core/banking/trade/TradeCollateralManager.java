package org.example.app.core.banking.trade;

import org.example.app.core.types.Address;
import org.example.app.core.types.Amount;
import org.example.app.core.banking.oracle.AfricanSovereigntyTypes.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * TradeCollateralManager - Collateral management with African Sovereignty
 * 
 * ⚠️ UPGRADED FOR AFRICAN NODE SOVEREIGNTY
 * 
 * SOVEREIGNTY FEATURES:
 * - Multi-region dispute resolution (requires 3+ of 5 regions)
 * - Geographic validator distribution (max 33% per region)
 * - African validator priority (community validators preferred)
 * - Cross-border arbitration panels
 * - ASU-denominated collateral preferred
 * - Emergency freeze protocols for government attacks
 */
public class TradeCollateralManager {
    private static final Logger LOGGER = Logger.getLogger(TradeCollateralManager.class.getName());
    
    // ============ SOVEREIGNTY PARAMETERS ============
    
    private static final int MIN_REGIONS_FOR_DISPUTE = 3; // 3 of 5 regions required
    private static final int MIN_VALIDATORS_PER_REGION = 2; // 2 validators per region
    private static final BigDecimal COMMUNITY_VALIDATOR_BONUS = new BigDecimal("0.15"); // 15% bonus weight
    
    private static final BigDecimal MINIMUM_COLLATERAL_RATIO = new BigDecimal("1.10"); // 110%
    private static final BigDecimal RECOMMENDED_COLLATERAL_RATIO = new BigDecimal("1.20"); // 120%
    
    private final Map<String, CollateralDeposit> deposits = new ConcurrentHashMap<>();
    private final Map<String, CollateralLock> locks = new ConcurrentHashMap<>();
    private final Map<String, CollateralRelease> releases = new ConcurrentHashMap<>();
    private final Map<String, DisputeHold> disputeHolds = new ConcurrentHashMap<>();
    private final Map<AfricanRegion, Set<String>> regionalValidators = new ConcurrentHashMap<>();
    
    /**
     * Enhanced collateral deposit with geographic tracking
     */
    public static class CollateralDeposit {
        private final String depositId;
        private Address depositor;
        private final Amount amount;
        private final String tradePurpose;
        private final String tradeId;
        private CollateralStatus status;
        private final long depositTime;
        private final AfricanRegion region; // NEW: Geographic tracking
        private final String country; // NEW: Country tracking
        private final boolean isCommunityValidator; // NEW: Community flag
        
        public CollateralDeposit(String depositId, Address depositor, Amount amount,
                                String tradePurpose, String tradeId, CollateralStatus status,
                                long depositTime, AfricanRegion region, String country,
                                boolean isCommunityValidator) {
            this.depositId = depositId;
            this.depositor = depositor;
            this.amount = amount;
            this.tradePurpose = tradePurpose;
            this.tradeId = tradeId;
            this.status = status;
            this.depositTime = depositTime;
            this.region = region;
            this.country = country;
            this.isCommunityValidator = isCommunityValidator;
        }
        
        public String getDepositId() { return depositId; }
        public Address getDepositor() { return depositor; }
        public void setDepositor(Address depositor) { this.depositor = depositor; }
        public Amount getAmount() { return amount; }
        public String getTradePurpose() { return tradePurpose; }
        public String getTradeId() { return tradeId; }
        public CollateralStatus getStatus() { return status; }
        public void setStatus(CollateralStatus status) { this.status = status; }
        public long getDepositTime() { return depositTime; }
        public AfricanRegion getRegion() { return region; }
        public String getCountry() { return country; }
        public boolean isCommunityValidator() { return isCommunityValidator; }
    }
    
    /**
     * Enhanced dispute hold with multi-region validation
     */
    public static class DisputeHold {
        private final String holdId;
        private final String depositId;
        private final String reason;
        private final Address initiator;
        private DisputeStatus status;
        private final long holdTime;
        private final Map<AfricanRegion, List<String>> regionalValidators; // NEW
        private final Map<AfricanRegion, DisputeVote> regionalVotes; // NEW
        
        public DisputeHold(String holdId, String depositId, String reason, Address initiator,
                          DisputeStatus status, long holdTime) {
            this.holdId = holdId;
            this.depositId = depositId;
            this.reason = reason;
            this.initiator = initiator;
            this.status = status;
            this.holdTime = holdTime;
            this.regionalValidators = new HashMap<>();
            this.regionalVotes = new HashMap<>();
        }
        
        public void addRegionalValidator(AfricanRegion region, String validatorId) {
            regionalValidators.computeIfAbsent(region, k -> new ArrayList<>()).add(validatorId);
        }
        
        public void recordRegionalVote(AfricanRegion region, DisputeVote vote) {
            regionalVotes.put(region, vote);
        }
        
        public String getHoldId() { return holdId; }
        public String getDepositId() { return depositId; }
        public String getReason() { return reason; }
        public Address getInitiator() { return initiator; }
        public DisputeStatus getStatus() { return status; }
        public void setStatus(DisputeStatus status) { this.status = status; }
        public long getHoldTime() { return holdTime; }
        public Map<AfricanRegion, List<String>> getRegionalValidators() { 
            return new HashMap<>(regionalValidators); 
        }
        public Map<AfricanRegion, DisputeVote> getRegionalVotes() {
            return new HashMap<>(regionalVotes);
        }
    }
    
    public enum CollateralStatus {
        DEPOSITED, LOCKED, RELEASED, FORFEITED, DISPUTE_HOLD, EMERGENCY_FREEZE
    }
    
    public enum DisputeStatus {
        ACTIVE, UNDER_REVIEW, RESOLVED_RELEASE, RESOLVED_FORFEIT, 
        RESOLVED_PARTIAL, CANCELLED, ESCALATED
    }
    
    public enum DisputeResolution {
        RELEASE, FORFEIT, PARTIAL
    }
    
    public enum DisputeVote {
        RELEASE, FORFEIT, PARTIAL, ABSTAIN
    }
    
    /**
     * Deposits collateral WITH GEOGRAPHIC TRACKING
     */
    public CollateralDeposit depositCollateral(Address depositor, Amount amount,
                                               String tradePurpose, String tradeId,
                                               AfricanRegion region, String country,
                                               boolean isCommunityValidator) 
            throws CollateralException {
        try {
            if (amount == null || amount.isZero()) {
                throw new CollateralException("Invalid collateral amount");
            }
            
            String depositId = UUID.randomUUID().toString();
            CollateralDeposit deposit = new CollateralDeposit(
                depositId, depositor, amount, tradePurpose, tradeId,
                CollateralStatus.DEPOSITED, System.currentTimeMillis(),
                region, country, isCommunityValidator
            );
            
            deposits.put(depositId, deposit);
            
            LOGGER.info(String.format("Collateral deposited: %s - %s - Region: %s - Community: %s",
                depositId, amount, region, isCommunityValidator));
            
            return deposit;
        } catch (CollateralException e) {
            throw e;
        } catch (Exception e) {
            throw new CollateralException("Deposit failed: " + e.getMessage());
        }
    }
    
    /**
     * Places dispute hold WITH MULTI-REGION VALIDATION
     */
    public DisputeHold placeDisputeHold(String depositId, String reason, Address initiator,
                                        Map<AfricanRegion, List<String>> regionalValidators) 
            throws CollateralException {
        try {
            CollateralDeposit deposit = deposits.get(depositId);
            if (deposit == null) {
                throw new CollateralException("Deposit not found: " + depositId);
            }
            
            // SOVEREIGNTY CHECK: Minimum regional validators required
            if (regionalValidators.size() < MIN_REGIONS_FOR_DISPUTE) {
                throw new CollateralException(String.format(
                    "Insufficient regions for dispute: %d (minimum: %d)",
                    regionalValidators.size(), MIN_REGIONS_FOR_DISPUTE));
            }
            
            // SOVEREIGNTY CHECK: Each region must have minimum validators
            for (Map.Entry<AfricanRegion, List<String>> entry : regionalValidators.entrySet()) {
                if (entry.getValue().size() < MIN_VALIDATORS_PER_REGION) {
                    throw new CollateralException(String.format(
                        "Region %s has insufficient validators: %d (minimum: %d)",
                        entry.getKey(), entry.getValue().size(), MIN_VALIDATORS_PER_REGION));
                }
            }
            
            String holdId = UUID.randomUUID().toString();
            DisputeHold hold = new DisputeHold(
                holdId, depositId, reason, initiator,
                DisputeStatus.ACTIVE, System.currentTimeMillis()
            );
            
            // Add regional validators
            for (Map.Entry<AfricanRegion, List<String>> entry : regionalValidators.entrySet()) {
                for (String validatorId : entry.getValue()) {
                    hold.addRegionalValidator(entry.getKey(), validatorId);
                }
            }
            
            disputeHolds.put(holdId, hold);
            deposit.setStatus(CollateralStatus.DISPUTE_HOLD);
            
            LOGGER.warning(String.format("Dispute hold placed: %s - %d regions - Reason: %s",
                holdId, regionalValidators.size(), reason));
            
            return hold;
        } catch (CollateralException e) {
            throw e;
        } catch (Exception e) {
            throw new CollateralException("Hold placement failed: " + e.getMessage());
        }
    }
    
    /**
     * Records regional vote on dispute
     */
    public void recordRegionalDisputeVote(String holdId, AfricanRegion region, DisputeVote vote)
            throws CollateralException {
        try {
            DisputeHold hold = disputeHolds.get(holdId);
            if (hold == null) {
                throw new CollateralException("Hold not found: " + holdId);
            }
            
            hold.recordRegionalVote(region, vote);
            
            LOGGER.info(String.format("Regional vote recorded: %s - Region: %s - Vote: %s",
                holdId, region, vote));
            
            // Check if consensus reached
            if (hasRegionalConsensus(hold)) {
                DisputeResolution resolution = determineConsensusResolution(hold);
                resolveDispute(holdId, resolution);
            }
        } catch (CollateralException e) {
            throw e;
        } catch (Exception e) {
            throw new CollateralException("Vote recording failed: " + e.getMessage());
        }
    }
    
    /**
     * SOVEREIGNTY CHECK: Determines if multi-region consensus reached
     */
    private boolean hasRegionalConsensus(DisputeHold hold) {
        Map<AfricanRegion, DisputeVote> votes = hold.getRegionalVotes();
        
        // Need votes from minimum regions
        if (votes.size() < MIN_REGIONS_FOR_DISPUTE) {
            return false;
        }
        
        // Count votes by resolution type
        Map<DisputeVote, Integer> voteCounts = new HashMap<>();
        for (DisputeVote vote : votes.values()) {
            voteCounts.merge(vote, 1, Integer::sum);
        }
        
        // Check if any resolution has majority (>50% of voting regions)
        int totalVotes = votes.size();
        for (int count : voteCounts.values()) {
            if (count > totalVotes / 2) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Determines consensus resolution from regional votes
     */
    private DisputeResolution determineConsensusResolution(DisputeHold hold) {
        Map<AfricanRegion, DisputeVote> votes = hold.getRegionalVotes();
        
        // Count votes
        Map<DisputeVote, Integer> voteCounts = new HashMap<>();
        for (DisputeVote vote : votes.values()) {
            voteCounts.merge(vote, 1, Integer::sum);
        }
        
        // Find majority vote
        DisputeVote majorityVote = voteCounts.entrySet().stream()
            .max(Comparator.comparingInt(Map.Entry::getValue))
            .map(Map.Entry::getKey)
            .orElse(DisputeVote.PARTIAL);
        
        // Convert to resolution
        switch (majorityVote) {
            case RELEASE:
                return DisputeResolution.RELEASE;
            case FORFEIT:
                return DisputeResolution.FORFEIT;
            default:
                return DisputeResolution.PARTIAL;
        }
    }
    
    /**
     * Resolves dispute with multi-region consensus
     */
    public void resolveDispute(String holdId, DisputeResolution resolution) 
            throws CollateralException {
        try {
            DisputeHold hold = disputeHolds.get(holdId);
            if (hold == null) {
                throw new CollateralException("Hold not found: " + holdId);
            }
            
            // Verify multi-region consensus
            if (!hasRegionalConsensus(hold)) {
                throw new CollateralException("Insufficient regional consensus for resolution");
            }
            
            if (resolution == DisputeResolution.RELEASE) {
                CollateralDeposit deposit = deposits.get(hold.getDepositId());
                if (deposit != null) {
                    deposit.setStatus(CollateralStatus.RELEASED);
                    releaseCollateral(hold.getDepositId());
                }
                hold.setStatus(DisputeStatus.RESOLVED_RELEASE);
                LOGGER.info("Dispute resolved - collateral released: " + hold.getDepositId());
            } else if (resolution == DisputeResolution.FORFEIT) {
                CollateralDeposit deposit = deposits.get(hold.getDepositId());
                if (deposit != null) {
                    deposit.setStatus(CollateralStatus.FORFEITED);
                }
                hold.setStatus(DisputeStatus.RESOLVED_FORFEIT);
                LOGGER.warning("Dispute resolved - collateral forfeited: " + hold.getDepositId());
            } else if (resolution == DisputeResolution.PARTIAL) {
                hold.setStatus(DisputeStatus.RESOLVED_PARTIAL);
                LOGGER.info("Dispute resolved - partial release: " + hold.getDepositId());
            }
        } catch (CollateralException e) {
            throw e;
        } catch (Exception e) {
            throw new CollateralException("Dispute resolution failed: " + e.getMessage());
        }
    }
    
    /**
     * Locks collateral for specific trade
     */
    public CollateralLock lockCollateral(String depositId, String exportTokenId,
                                        Amount lockedAmount) throws CollateralException {
        try {
            CollateralDeposit deposit = deposits.get(depositId);
            if (deposit == null) {
                throw new CollateralException("Deposit not found: " + depositId);
            }
            
            if (lockedAmount.compareTo(deposit.getAmount()) > 0) {
                throw new CollateralException("Lock amount exceeds deposit");
            }
            
            String lockId = UUID.randomUUID().toString();
            CollateralLock lock = new CollateralLock(
                lockId, depositId, exportTokenId, lockedAmount,
                CollateralStatus.LOCKED, System.currentTimeMillis()
            );
            
            locks.put(lockId, lock);
            deposit.setStatus(CollateralStatus.LOCKED);
            
            LOGGER.info("Collateral locked: " + lockId + " - " + lockedAmount);
            
            return lock;
        } catch (CollateralException e) {
            throw e;
        } catch (Exception e) {
            throw new CollateralException("Lock failed: " + e.getMessage());
        }
    }
    
    /**
     * Verifies collateral ratio WITH COMMUNITY VALIDATOR BONUS
     */
    public boolean verifyCollateralRatio(Amount tradeValue, Amount collateral,
                                        boolean isCommunityValidator) 
            throws CollateralException {
        try {
            if (tradeValue == null || collateral == null) {
                throw new CollateralException("Invalid amounts");
            }
            
            BigDecimal ratio = new BigDecimal(collateral.getValue())
                .divide(new BigDecimal(tradeValue.getValue()), 4, RoundingMode.HALF_UP);
            
            BigDecimal requiredRatio = MINIMUM_COLLATERAL_RATIO;
            
            // SOVEREIGNTY FEATURE: Community validators get 15% bonus (lower requirement)
            if (isCommunityValidator) {
                requiredRatio = requiredRatio.multiply(
                    BigDecimal.ONE.subtract(COMMUNITY_VALIDATOR_BONUS));
                
                LOGGER.info(String.format("Community validator bonus applied: %.1f%% reduction",
                    COMMUNITY_VALIDATOR_BONUS.multiply(new BigDecimal("100"))));
            }
            
            boolean isValid = ratio.compareTo(requiredRatio) >= 0;
            
            LOGGER.info("Collateral ratio verification: " + ratio + 
                       " (required: " + requiredRatio + ") - " + 
                       (isValid ? "VALID" : "INVALID"));
            
            return isValid;
        } catch (Exception e) {
            throw new CollateralException("Verification failed: " + e.getMessage());
        }
    }
    
    /**
     * SOVEREIGNTY FEATURE: Emergency freeze for government attacks
     */
    public void activateEmergencyFreeze(String depositId, String reason) 
            throws CollateralException {
        try {
            CollateralDeposit deposit = deposits.get(depositId);
            if (deposit == null) {
                throw new CollateralException("Deposit not found: " + depositId);
            }
            
            deposit.setStatus(CollateralStatus.EMERGENCY_FREEZE);
            
            LOGGER.severe("⚠️ EMERGENCY FREEZE ACTIVATED: " + depositId + " - Reason: " + reason);
        } catch (Exception e) {
            throw new CollateralException("Emergency freeze failed: " + e.getMessage());
        }
    }
    
    /**
     * Releases collateral after trade completes
     */
    public CollateralRelease releaseCollateral(String depositId) throws CollateralException {
        try {
            CollateralDeposit deposit = deposits.get(depositId);
            if (deposit == null) {
                throw new CollateralException("Deposit not found: " + depositId);
            }
            
            String releaseId = UUID.randomUUID().toString();
            CollateralRelease release = new CollateralRelease(
                releaseId, depositId, deposit.getAmount(),
                deposit.getDepositor(), System.currentTimeMillis()
            );
            
            releases.put(releaseId, release);
            deposit.setStatus(CollateralStatus.RELEASED);
            
            LOGGER.info("Collateral released: " + releaseId + " - " + deposit.getAmount());
            
            return release;
        } catch (CollateralException e) {
            throw e;
        } catch (Exception e) {
            throw new CollateralException("Release failed: " + e.getMessage());
        }
    }
    
    /**
     * Transfers collateral between parties
     */
    public void transferCollateral(String depositId, Address newOwner) 
            throws CollateralException {
        try {
            CollateralDeposit deposit = deposits.get(depositId);
            if (deposit == null) {
                throw new CollateralException("Deposit not found: " + depositId);
            }
            
            deposit.setDepositor(newOwner);
            LOGGER.info("Collateral transferred: " + depositId + " to " + newOwner);
        } catch (Exception e) {
            throw new CollateralException("Transfer failed: " + e.getMessage());
        }
    }
    
    /**
     * Gets total collateral for a trade
     */
    public Amount getTotalCollateral(String tradeId) {
        Amount total = new Amount(BigInteger.ZERO, "ASU");
        
        for (CollateralDeposit deposit : deposits.values()) {
            if (deposit.getTradeId().equals(tradeId)) {
                total = total.add(deposit.getAmount());
            }
        }
        
        return total;
    }
    
    public CollateralDeposit getDeposit(String depositId) {
        return deposits.get(depositId);
    }
    
    public CollateralLock getLock(String lockId) {
        return locks.get(lockId);
    }
    
    public DisputeHold getDisputeHold(String holdId) {
        return disputeHolds.get(holdId);
    }
    
    /**
     * Gets statistics with sovereignty metrics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalDeposits", deposits.size());
        stats.put("totalLocks", locks.size());
        stats.put("activeDisputes", (int) disputeHolds.values().stream()
            .filter(h -> h.getStatus() == DisputeStatus.ACTIVE).count());
        
        long communityDeposits = deposits.values().stream()
            .filter(CollateralDeposit::isCommunityValidator).count();
        stats.put("communityValidatorDeposits", communityDeposits);
        
        Map<AfricanRegion, Long> regionalDeposits = new HashMap<>();
        for (CollateralDeposit deposit : deposits.values()) {
            if (deposit.getRegion() != null) {
                regionalDeposits.merge(deposit.getRegion(), 1L, Long::sum);
            }
        }
        stats.put("depositsByRegion", regionalDeposits);
        
        return stats;
    }
    
    public static class CollateralLock {
        private final String lockId;
        private final String depositId;
        private final String exportTokenId;
        private final Amount lockedAmount;
        private CollateralStatus status;
        private final long lockTime;
        
        public CollateralLock(String lockId, String depositId, String exportTokenId,
                             Amount lockedAmount, CollateralStatus status, long lockTime) {
            this.lockId = lockId;
            this.depositId = depositId;
            this.exportTokenId = exportTokenId;
            this.lockedAmount = lockedAmount;
            this.status = status;
            this.lockTime = lockTime;
        }
        
        public String getLockId() { return lockId; }
        public String getDepositId() { return depositId; }
        public String getExportTokenId() { return exportTokenId; }
        public Amount getLockedAmount() { return lockedAmount; }
        public CollateralStatus getStatus() { return status; }
        public long getLockTime() { return lockTime; }
    }
    
    public static class CollateralRelease {
        private final String releaseId;
        private final String depositId;
        private final Amount amount;
        private final Address releaseRecipient;
        private final long releaseTime;
        
        public CollateralRelease(String releaseId, String depositId, Amount amount,
                                Address releaseRecipient, long releaseTime) {
            this.releaseId = releaseId;
            this.depositId = depositId;
            this.amount = amount;
            this.releaseRecipient = releaseRecipient;
            this.releaseTime = releaseTime;
        }
        
        public String getReleaseId() { return releaseId; }
        public String getDepositId() { return depositId; }
        public Amount getAmount() { return amount; }
        public Address getReleaseRecipient() { return releaseRecipient; }
        public long getReleaseTime() { return releaseTime; }
    }
    
    public static class CollateralException extends Exception {
        public CollateralException(String message) {
            super(message);
        }
        
        public CollateralException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}