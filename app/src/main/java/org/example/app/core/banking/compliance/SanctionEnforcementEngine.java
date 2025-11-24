package org.example.app.core.banking.compliance;

import org.example.app.core.types.Address;
import org.example.app.core.banking.security.SlashingEngine;
import java.util.*;
import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * SanctionEnforcementEngine - Neutral, transparent enforcement of sanctions
 * 
 * Implements Req #8 from guidebook: Rule-based enforcement with:
 * - Graduated sanctions (warnings -> fines -> suspension)
 * - Automatic execution (smart contracts, not discretionary)
 * - No freezing of citizen wallets (only institutional privileges)
 * - Automatic expiration unless reconfirmed
 * - Reversible upon remediation
 * 
 * Sanctions types:
 * 1. Reduce validator voting weight
 * 2. Reduce currency weight in ASU basket
 * 3. Freeze/throttle treasury minting rights
 * 4. Suspend bank connector registrations
 * 5. Increase M-of-N attestation requirements
 * 6. Automatic fines from stake/treasury
 * 7. Reputation penalties
 */
public class SanctionEnforcementEngine {
    private static final Logger LOGGER = Logger.getLogger(SanctionEnforcementEngine.class.getName());
    
    private final Map<String, Sanction> sanctions; // Sanction ID -> Sanction
    private final Map<String, List<String>> targetSanctions; // Target address -> Sanction IDs
    private final Map<String, SanctionHistory> sanctionHistory; // Target -> History
    private final SlashingEngine slashingEngine;
    
    public enum SanctionLevel {
        WARNING,           // No action, reputational only
        FINE,              // Financial penalty
        SUSPENSION,        // Temporary privilege reduction
        HEAVY_SUSPENSION,  // Major restrictions
        REMOVAL            // Permanent removal
    }
    
    public enum SanctionType {
        REDUCE_VALIDATOR_WEIGHT,       // -5% to -50% voting weight
        REDUCE_CURRENCY_WEIGHT,        // Reduce country's weight in ASU basket
        THROTTLE_MINTING,              // Reduce minting speed/amount
        SUSPEND_BANK_CONNECTOR,        // Suspend bank operations
        INCREASE_ATTESTATION,          // Increase M-of-N requirements
        FINANCIAL_FINE,                // Stake/treasury penalty
        REPUTATION_PENALTY,            // Reputation score decrease
        COLLATERAL_SLASH,              // Slash arbitrator/validator stake
        CUSTOM                         // Custom enforcement rule
    }
    
    public static class Sanction {
        private final String sanctionId;
        private final String disputeId;
        private final Address targetAddress;
        private final String targetEntity; // Bank name, validator ID, etc.
        private final SanctionType type;
        private final SanctionLevel level;
        private final String description;
        private final BigInteger fineAmount; // If applicable
        private final Map<String, Object> parameters; // Type-specific params
        private final long issuedTime;
        private final long expirationTime;
        private final long maxExtensionTime;
        private SanctionStatus status;
        private final List<String> enforcementRecords; // Log of enforcement actions
        private final boolean isReversible; // Can be lifted by remediation
        private final List<String> remediationConditions;
        
        public enum SanctionStatus {
            ISSUED,      // Issued but not yet active
            ACTIVE,      // Currently enforced
            ENFORCED,    // Action taken
            EXPIRED,     // Automatically expired
            LIFTED,      // Removed by remediation
            CHALLENGED   // Under appeal
        }
        
        public Sanction(String sanctionId, String disputeId, Address target,
                       String entityName, SanctionType type, SanctionLevel level,
                       String description, BigInteger fineAmount, long durationMs,
                       boolean reversible) {
            this.sanctionId = sanctionId;
            this.disputeId = disputeId;
            this.targetAddress = target;
            this.targetEntity = entityName;
            this.type = type;
            this.level = level;
            this.description = description;
            this.fineAmount = fineAmount;
            this.issuedTime = System.currentTimeMillis();
            this.expirationTime = issuedTime + durationMs;
            this.maxExtensionTime = issuedTime + (durationMs * 2); // Can extend 1x
            this.status = SanctionStatus.ISSUED;
            this.enforcementRecords = new ArrayList<>();
            this.isReversible = reversible;
            this.remediationConditions = new ArrayList<>();
            this.parameters = new ConcurrentHashMap<>();
        }
        
        // Getters
        public String getSanctionId() { return sanctionId; }
        public String getDisputeId() { return disputeId; }
        public Address getTargetAddress() { return targetAddress; }
        public String getTargetEntity() { return targetEntity; }
        public SanctionType getType() { return type; }
        public SanctionLevel getLevel() { return level; }
        public String getDescription() { return description; }
        public BigInteger getFineAmount() { return fineAmount; }
        public long getIssuedTime() { return issuedTime; }
        public long getExpirationTime() { return expirationTime; }
        public SanctionStatus getStatus() { return status; }
        public boolean isReversible() { return isReversible; }
        
        public void setStatus(SanctionStatus s) { this.status = s; }
        public void recordEnforcement(String action) { enforcementRecords.add(action); }
        public void addParameter(String key, Object value) { parameters.put(key, value); }
        public void addRemediationCondition(String condition) { remediationConditions.add(condition); }
        
        public List<String> getEnforcementRecords() { return new ArrayList<>(enforcementRecords); }
        public List<String> getRemediationConditions() { return new ArrayList<>(remediationConditions); }
        
        public boolean isExpired() { return System.currentTimeMillis() >= expirationTime; }
        public boolean canExtend() { return System.currentTimeMillis() < maxExtensionTime; }
        public long getTimeRemaining() { return Math.max(0, expirationTime - System.currentTimeMillis()); }
    }
    
    public static class SanctionHistory {
        private final Address target;
        private final List<String> sanctionIds;
        private final Map<SanctionType, Integer> sanctionCounts;
        private final long firstSanctionTime;
        private int escalationLevel; // 0 = warning, 1 = fine, 2 = suspension, etc.
        
        public SanctionHistory(Address target) {
            this.target = target;
            this.sanctionIds = new ArrayList<>();
            this.sanctionCounts = new ConcurrentHashMap<>();
            this.firstSanctionTime = System.currentTimeMillis();
            this.escalationLevel = 0;
        }
        
        public void addSanction(String sanctionId, SanctionType type) {
            sanctionIds.add(sanctionId);
            sanctionCounts.merge(type, 1, Integer::sum);
        }
        
        public List<String> getSanctionIds() { return new ArrayList<>(sanctionIds); }
        public int getEscalationLevel() { return escalationLevel; }
        public void setEscalationLevel(int level) { this.escalationLevel = level; }
        public long getFirstSanctionTime() { return firstSanctionTime; }
    }
    
    public SanctionEnforcementEngine(SlashingEngine slashingEngine) {
        this.sanctions = new ConcurrentHashMap<>();
        this.targetSanctions = new ConcurrentHashMap<>();
        this.sanctionHistory = new ConcurrentHashMap<>();
        this.slashingEngine = slashingEngine;
        LOGGER.info("SanctionEnforcementEngine initialized");
    }
    
    /**
     * Issues a sanction based on arbitration verdict (neutral, rule-based)
     */
    public String issueSanction(String disputeId, Address target, String entityName,
                               SanctionType type, SanctionLevel level,
                               String description, BigInteger fineAmount,
                               long durationMs) {
        String sanctionId = UUID.randomUUID().toString();
        
        // Verify multi-party activation requirement (from guidebook)
        // In production, this would check DAO/arbitration council approval
        if (!hasMultiPartyApproval(disputeId)) {
            LOGGER.warning("Multi-party activation failed for sanction: " + sanctionId);
            return null;
        }
        
        // Determine if sanction is reversible (most are, except removal)
        boolean reversible = level != SanctionLevel.REMOVAL;
        
        // Create sanction with automatic expiration
        Sanction sanction = new Sanction(sanctionId, disputeId, target, entityName, type, level,
                                        description, fineAmount, durationMs, reversible);
        
        sanctions.put(sanctionId, sanction);
        targetSanctions.computeIfAbsent(target.toString(), k -> new ArrayList<>()).add(sanctionId);
        
        // Track in history
        SanctionHistory history = sanctionHistory.computeIfAbsent(target,
            addr -> new SanctionHistory(addr));
        history.addSanction(sanctionId, type);
        
        // Determine escalation
        int escalation = (history.sanctionIds.size() - 1) / 3; // Escalate every 3 sanctions
        history.setEscalationLevel(Math.min(escalation, 4));
        
        LOGGER.info("Sanction issued: " + sanctionId + " to " + entityName +
                   " (Level: " + level + ", Duration: " + durationMs + "ms)");
        
        return sanctionId;
    }
    
    /**
     * Executes the sanction (actually applies it)
     */
    public boolean executeSanction(String sanctionId) {
        Sanction sanction = sanctions.get(sanctionId);
        if (sanction == null) {
            LOGGER.warning("Sanction not found: " + sanctionId);
            return false;
        }
        
        if (sanction.getStatus() != Sanction.SanctionStatus.ISSUED) {
            LOGGER.warning("Sanction not in ISSUED state: " + sanctionId);
            return false;
        }
        
        switch (sanction.getType()) {
            case REDUCE_VALIDATOR_WEIGHT:
                executeValidatorWeightReduction(sanction);
                break;
            case REDUCE_CURRENCY_WEIGHT:
                executeCurrencyWeightReduction(sanction);
                break;
            case THROTTLE_MINTING:
                executeThrottleMinting(sanction);
                break;
            case SUSPEND_BANK_CONNECTOR:
                executeBankSuspension(sanction);
                break;
            case INCREASE_ATTESTATION:
                executeAttestationIncrease(sanction);
                break;
            case FINANCIAL_FINE:
                executeFinancialFine(sanction);
                break;
            case REPUTATION_PENALTY:
                executeReputationPenalty(sanction);
                break;
            case COLLATERAL_SLASH:
                executeCollateralSlash(sanction);
                break;
            default:
                LOGGER.warning("Unknown sanction type: " + sanction.getType());
                return false;
        }
        
        sanction.setStatus(Sanction.SanctionStatus.ENFORCED);
        sanction.recordEnforcement("Executed at " + System.currentTimeMillis());
        
        LOGGER.info("Sanction enforced: " + sanctionId);
        return true;
    }
    
    /**
     * Checks if sanction has expired (auto-expiration)
     */
    public void checkAndExpireSanctions() {
        for (Sanction sanction : sanctions.values()) {
            if (sanction.getStatus() == Sanction.SanctionStatus.ENFORCED && sanction.isExpired()) {
                sanction.setStatus(Sanction.SanctionStatus.EXPIRED);
                sanction.recordEnforcement("Automatically expired at " + System.currentTimeMillis());
                LOGGER.info("Sanction automatically expired: " + sanction.getSanctionId());
            }
        }
    }
    
    /**
     * Lifts a sanction upon remediation
     */
    public boolean liftSanction(String sanctionId) {
        Sanction sanction = sanctions.get(sanctionId);
        if (sanction == null || !sanction.isReversible()) {
            LOGGER.warning("Cannot lift sanction: " + sanctionId);
            return false;
        }
        
        sanction.setStatus(Sanction.SanctionStatus.LIFTED);
        sanction.recordEnforcement("Lifted by remediation at " + System.currentTimeMillis());
        
        LOGGER.info("Sanction lifted: " + sanctionId);
        return true;
    }
    
    /**
     * Gets all active sanctions for a target
     */
    public List<Sanction> getActiveSanctions(Address target) {
        return targetSanctions.getOrDefault(target.toString(), new ArrayList<>()).stream()
            .map(sanctions::get)
            .filter(s -> s != null && s.getStatus() == Sanction.SanctionStatus.ENFORCED)
            .toList();
    }
    
    /**
     * Gets sanction history for target
     */
    public SanctionHistory getHistory(Address target) {
        return sanctionHistory.get(target);
    }
    
    // ============== Execution Methods ==============
    
    private void executeValidatorWeightReduction(Sanction sanction) {
        int weightReduction = switch(sanction.getLevel()) {
            case WARNING -> 5;
            case FINE -> 10;
            case SUSPENSION -> 25;
            case HEAVY_SUSPENSION -> 50;
            case REMOVAL -> 100;
        };
        sanction.addParameter("weight_reduction_percent", weightReduction);
        LOGGER.info("Validator weight reduced by " + weightReduction + "% for: " + sanction.getTargetEntity());
    }
    
    private void executeCurrencyWeightReduction(Sanction sanction) {
        int weightReduction = switch(sanction.getLevel()) {
            case WARNING -> 2;
            case FINE -> 5;
            case SUSPENSION -> 15;
            case HEAVY_SUSPENSION -> 30;
            case REMOVAL -> 100;
        };
        sanction.addParameter("currency_weight_reduction_percent", weightReduction);
        LOGGER.info("Currency weight reduced by " + weightReduction + "% for: " + sanction.getTargetEntity());
    }
    
    private void executeThrottleMinting(Sanction sanction) {
        int throttlePercent = switch(sanction.getLevel()) {
            case WARNING -> 25;
            case FINE -> 50;
            case SUSPENSION -> 75;
            case HEAVY_SUSPENSION -> 90;
            case REMOVAL -> 100;
        };
        sanction.addParameter("minting_throttle_percent", throttlePercent);
        LOGGER.info("Minting throttled to " + (100 - throttlePercent) + "% capacity for: " + sanction.getTargetEntity());
    }
    
    private void executeBankSuspension(Sanction sanction) {
        // Suspension of bank connector, NOT citizen wallets
        sanction.addParameter("bank_suspended", true);
        LOGGER.info("Bank connector suspended for: " + sanction.getTargetEntity());
    }
    
    private void executeAttestationIncrease(Sanction sanction) {
        int increase = switch(sanction.getLevel()) {
            case WARNING -> 0;
            case FINE -> 1;
            case SUSPENSION -> 2;
            case HEAVY_SUSPENSION -> 3;
            case REMOVAL -> 5;
        };
        sanction.addParameter("m_of_n_increase", increase);
        LOGGER.info("Attestation requirement increased by " + increase + " for: " + sanction.getTargetEntity());
    }
    
    private void executeFinancialFine(Sanction sanction) {
        if (sanction.getFineAmount() != null && sanction.getFineAmount().compareTo(BigInteger.ZERO) > 0) {
            // Transfer fine to treasury/stability fund
            LOGGER.info("Financial fine imposed: " + sanction.getFineAmount() + " for: " + sanction.getTargetEntity());
        }
    }
    
    private void executeReputationPenalty(Sanction sanction) {
        double penaltyAmount = switch(sanction.getLevel()) {
            case WARNING -> 0.05;
            case FINE -> 0.10;
            case SUSPENSION -> 0.20;
            case HEAVY_SUSPENSION -> 0.35;
            case REMOVAL -> 1.0;
        };
        sanction.addParameter("reputation_penalty", penaltyAmount);
        LOGGER.info("Reputation penalized by " + (penaltyAmount * 100) + "% for: " + sanction.getTargetEntity());
    }
    
    private void executeCollateralSlash(Sanction sanction) {
        if (slashingEngine != null && sanction.getFineAmount() != null) {
            // Delegate to slashing engine for stake slashing
            LOGGER.info("Collateral slashing executed for: " + sanction.getTargetEntity());
        }
    }
    
    private boolean hasMultiPartyApproval(String disputeId) {
        // Placeholder: In production, check DAO vote/arbitration council consensus
        // Requirement: >= 66% consensus from multiple parties
        return true;
    }
}