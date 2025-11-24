package org.example.app.core.banking.trade;

import org.example.app.core.types.Address;
import org.example.app.core.types.Amount;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * TradeCollateralManager manages collateral deposits and releases for trade operations,
 * ensuring all export tokens are properly backed and disputes can be resolved.
 */
public class TradeCollateralManager {
    private static final Logger LOGGER = Logger.getLogger(TradeCollateralManager.class.getName());
    
    private final Map<String, CollateralDeposit> deposits = new ConcurrentHashMap<>();
    private final Map<String, CollateralLock> locks = new ConcurrentHashMap<>();
    private final Map<String, CollateralRelease> releases = new ConcurrentHashMap<>();
    private final Map<String, DisputeHold> disputeHolds = new ConcurrentHashMap<>();
    
    private static final BigDecimal MINIMUM_COLLATERAL_RATIO = new BigDecimal("1.10"); // 110%
    
    /**
     * Deposits collateral for trade operation
     */
    public CollateralDeposit depositCollateral(Address depositor, Amount amount,
                                               String tradePurpose, String tradeId) 
            throws CollateralException {
        try {
            if (amount == null || amount.isZero()) {
                throw new CollateralException("Invalid collateral amount");
            }
            
            String depositId = UUID.randomUUID().toString();
            CollateralDeposit deposit = new CollateralDeposit(
                depositId,
                depositor,
                amount,
                tradePurpose,
                tradeId,
                CollateralStatus.DEPOSITED,
                System.currentTimeMillis()
            );
            
            deposits.put(depositId, deposit);
            LOGGER.info("Collateral deposited: " + depositId + " - " + amount);
            
            return deposit;
        } catch (CollateralException e) {
            throw e;
        } catch (Exception e) {
            throw new CollateralException("Deposit failed: " + e.getMessage());
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
                lockId,
                depositId,
                exportTokenId,
                lockedAmount,
                CollateralStatus.LOCKED,
                System.currentTimeMillis()
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
     * Verifies collateral ratio meets minimum requirements
     */
    public boolean verifyCollateralRatio(Amount tradeValue, Amount collateral) 
            throws CollateralException {
        try {
            if (tradeValue == null || collateral == null) {
                throw new CollateralException("Invalid amounts");
            }
            
            BigDecimal ratio = new BigDecimal(collateral.getValue())
                .divide(new BigDecimal(tradeValue.getValue()), 4, java.math.RoundingMode.HALF_UP);
            
            boolean isValid = ratio.compareTo(MINIMUM_COLLATERAL_RATIO) >= 0;
            
            LOGGER.info("Collateral ratio verification: " + ratio + 
                       " (required: " + MINIMUM_COLLATERAL_RATIO + ") - " + 
                       (isValid ? "VALID" : "INVALID"));
            
            return isValid;
        } catch (Exception e) {
            throw new CollateralException("Verification failed: " + e.getMessage());
        }
    }
    
    /**
     * Places dispute hold on collateral
     */
    public DisputeHold placeDisputeHold(String depositId, String reason,
                                        Address initiator) throws CollateralException {
        try {
            CollateralDeposit deposit = deposits.get(depositId);
            if (deposit == null) {
                throw new CollateralException("Deposit not found: " + depositId);
            }
            
            String holdId = UUID.randomUUID().toString();
            DisputeHold hold = new DisputeHold(
                holdId,
                depositId,
                reason,
                initiator,
                DisputeStatus.ACTIVE,
                System.currentTimeMillis()
            );
            
            disputeHolds.put(holdId, hold);
            deposit.setStatus(CollateralStatus.DISPUTE_HOLD);
            
            LOGGER.warning("Dispute hold placed: " + holdId + " - Reason: " + reason);
            
            return hold;
        } catch (CollateralException e) {
            throw e;
        } catch (Exception e) {
            throw new CollateralException("Hold placement failed: " + e.getMessage());
        }
    }
    
    /**
     * Resolves dispute and releases or forfeits collateral
     */
    public void resolveDispute(String holdId, DisputeResolution resolution) 
            throws CollateralException {
        try {
            DisputeHold hold = disputeHolds.get(holdId);
            if (hold == null) {
                throw new CollateralException("Hold not found: " + holdId);
            }
            
            if (resolution == DisputeResolution.RELEASE) {
                // Release collateral
                CollateralDeposit deposit = deposits.get(hold.getDepositId());
                if (deposit != null) {
                    deposit.setStatus(CollateralStatus.RELEASED);
                    releaseCollateral(hold.getDepositId());
                }
                hold.setStatus(DisputeStatus.RESOLVED_RELEASE);
                LOGGER.info("Dispute resolved - collateral released: " + hold.getDepositId());
            } else if (resolution == DisputeResolution.FORFEIT) {
                // Forfeit collateral
                CollateralDeposit deposit = deposits.get(hold.getDepositId());
                if (deposit != null) {
                    deposit.setStatus(CollateralStatus.FORFEITED);
                }
                hold.setStatus(DisputeStatus.RESOLVED_FORFEIT);
                LOGGER.warning("Dispute resolved - collateral forfeited: " + hold.getDepositId());
            } else if (resolution == DisputeResolution.PARTIAL) {
                // Partial release/forfeit - handled case-by-case
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
                releaseId,
                depositId,
                deposit.getAmount(),
                deposit.getDepositor(),
                System.currentTimeMillis()
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
    
    public enum CollateralStatus {
        DEPOSITED, LOCKED, RELEASED, FORFEITED, DISPUTE_HOLD
    }
    
    public enum DisputeStatus {
        ACTIVE, RESOLVED_RELEASE, RESOLVED_FORFEIT, RESOLVED_PARTIAL, CANCELLED
    }
    
    public enum DisputeResolution {
        RELEASE, FORFEIT, PARTIAL
    }
    
    public static class CollateralDeposit {
        private final String depositId;
        private Address depositor;
        private final Amount amount;
        private final String tradePurpose;
        private final String tradeId;
        private CollateralStatus status;
        private final long depositTime;
        
        public CollateralDeposit(String depositId, Address depositor, Amount amount,
                                String tradePurpose, String tradeId, CollateralStatus status,
                                long depositTime) {
            this.depositId = depositId;
            this.depositor = depositor;
            this.amount = amount;
            this.tradePurpose = tradePurpose;
            this.tradeId = tradeId;
            this.status = status;
            this.depositTime = depositTime;
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
    
    public static class DisputeHold {
        private final String holdId;
        private final String depositId;
        private final String reason;
        private final Address initiator;
        private DisputeStatus status;
        private final long holdTime;
        
        public DisputeHold(String holdId, String depositId, String reason, Address initiator,
                          DisputeStatus status, long holdTime) {
            this.holdId = holdId;
            this.depositId = depositId;
            this.reason = reason;
            this.initiator = initiator;
            this.status = status;
            this.holdTime = holdTime;
        }
        
        public String getHoldId() { return holdId; }
        public String getDepositId() { return depositId; }
        public String getReason() { return reason; }
        public Address getInitiator() { return initiator; }
        public DisputeStatus getStatus() { return status; }
        public void setStatus(DisputeStatus status) { this.status = status; }
        public long getHoldTime() { return holdTime; }
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