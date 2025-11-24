package org.example.app.core.banking.reserves;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * EmergencyStabilityFund - Emergency liquidity and stability reserve
 * 
 * Implements automatic stabilization mechanisms:
 * 1. Emergency reserve pool (segregated from normal reserves)
 * 2. Liquidity assistance to distressed tokens
 * 3. Backing ratio recovery protocols
 * 4. Cross-country collateral support
 * 5. Circuit breakers and emergency limits
 * 
 * Triggered when:
 * - Token backing falls below 1.20
 * - Multiple consecutive failed audits
 * - Systemic bank failures
 * - Cross-border contagion detected
 * - DAO governance vote
 * 
 * Based on Implementation Guidebook: Stability Fund for emergency reserves
 */
public class EmergencyStabilityFund {
    private static final Logger LOGGER = Logger.getLogger(EmergencyStabilityFund.class.getName());
    
    // Emergency levels
    public enum EmergencyLevel {
        NORMAL,      // All systems operating normally
        WARNING,     // Backing ratio between 1.15-1.20
        ELEVATED,    // Backing ratio between 1.05-1.15
        CRITICAL,    // Backing ratio between 1.00-1.05
        CRISIS       // Backing ratio below 1.00
    }
    
    /**
     * Emergency stabilization action
     */
    public static class StabilizationAction {
        private final String actionId;
        private final String tokenSymbol;
        private final EmergencyLevel triggerLevel;
        private final String actionType; // LIQUIDITY_INJECTION, RATE_ADJUSTMENT, etc.
        private final BigDecimal fundAmount;
        private final String actionDescription;
        private final Date initiatedTime;
        private volatile Date completedTime;
        private volatile boolean isCompleted;
        private final List<String> authorizedBy;
        private final String statusReason;
        
        public StabilizationAction(String actionId, String tokenSymbol, EmergencyLevel level,
                                 String actionType, BigDecimal amount, String description) {
            this.actionId = actionId;
            this.tokenSymbol = tokenSymbol;
            this.triggerLevel = level;
            this.actionType = actionType;
            this.fundAmount = amount;
            this.actionDescription = description;
            this.initiatedTime = new Date();
            this.isCompleted = false;
            this.authorizedBy = Collections.synchronizedList(new ArrayList<>());
            this.statusReason = "INITIATED";
        }
        
        public String getActionId() { return actionId; }
        public String getTokenSymbol() { return tokenSymbol; }
        public EmergencyLevel getTriggerLevel() { return triggerLevel; }
        public String getActionType() { return actionType; }
        public BigDecimal getFundAmount() { return fundAmount; }
        public String getActionDescription() { return actionDescription; }
        public Date getInitiatedTime() { return initiatedTime; }
        public Date getCompletedTime() { return completedTime; }
        public boolean isCompleted() { return isCompleted; }
        public List<String> getAuthorizedBy() { return new ArrayList<>(authorizedBy); }
        public void addAuthorization(String authorizer) { authorizedBy.add(authorizer); }
        public void markCompleted() { 
            this.isCompleted = true;
            this.completedTime = new Date();
        }
    }
    
    /**
     * Emergency reserve injection record
     */
    public static class ReserveInjection {
        private final String injectionId;
        private final String tokenSymbol;
        private final BigDecimal injectionAmount;
        private final String sourceType; // STABILITY_FUND, DAO_TREASURY, etc.
        private final String destinationType; // Where reserves go
        private final Date injectionDate;
        private final String justification;
        private final EmergencyLevel emergencyLevel;
        private final List<String> approverSignatures;
        private boolean isApproved;
        private boolean isExecuted;
        
        public ReserveInjection(String injectionId, String tokenSymbol, BigDecimal amount,
                              String sourceType, String destType, String justification,
                              EmergencyLevel level) {
            this.injectionId = injectionId;
            this.tokenSymbol = tokenSymbol;
            this.injectionAmount = amount;
            this.sourceType = sourceType;
            this.destinationType = destType;
            this.injectionDate = new Date();
            this.justification = justification;
            this.emergencyLevel = level;
            this.approverSignatures = Collections.synchronizedList(new ArrayList<>());
            this.isApproved = false;
            this.isExecuted = false;
        }
        
        public String getInjectionId() { return injectionId; }
        public String getTokenSymbol() { return tokenSymbol; }
        public BigDecimal getInjectionAmount() { return injectionAmount; }
        public String getSourceType() { return sourceType; }
        public String getDestinationType() { return destinationType; }
        public Date getInjectionDate() { return injectionDate; }
        public String getJustification() { return justification; }
        public EmergencyLevel getEmergencyLevel() { return emergencyLevel; }
        public List<String> getApproverSignatures() { return new ArrayList<>(approverSignatures); }
        public void addApproverSignature(String sig) { approverSignatures.add(sig); }
        public boolean isApproved() { return isApproved; }
        public void markApproved() { this.isApproved = true; }
        public boolean isExecuted() { return isExecuted; }
        public void markExecuted() { this.isExecuted = true; }
    }
    
    /**
     * Liquidity assistance agreement
     */
    public static class LiquidityAssistance {
        private final String assistanceId;
        private final String tokenSymbol;
        private final BigDecimal assistanceAmount;
        private final int repaymentPeriodDays;
        private final BigDecimal interestRate;
        private final Date disbursementDate;
        private volatile Date repaymentDueDate;
        private volatile BigDecimal repaidAmount;
        private final String assistanceReason;
        private volatile String status; // PENDING, ACTIVE, REPAYING, COMPLETED, DEFAULTED
        
        public LiquidityAssistance(String assistanceId, String tokenSymbol, BigDecimal amount,
                                 int repaymentDays, BigDecimal interestRate, String reason) {
            this.assistanceId = assistanceId;
            this.tokenSymbol = tokenSymbol;
            this.assistanceAmount = amount;
            this.repaymentPeriodDays = repaymentDays;
            this.interestRate = interestRate;
            this.disbursementDate = new Date();
            this.repaymentDueDate = new Date(disbursementDate.getTime() + 
                (long) repaymentDays * 24 * 60 * 60 * 1000);
            this.repaidAmount = BigDecimal.ZERO;
            this.assistanceReason = reason;
            this.status = "PENDING";
        }
        
        public String getAssistanceId() { return assistanceId; }
        public String getTokenSymbol() { return tokenSymbol; }
        public BigDecimal getAssistanceAmount() { return assistanceAmount; }
        public int getRepaymentPeriodDays() { return repaymentPeriodDays; }
        public BigDecimal getInterestRate() { return interestRate; }
        public Date getDisbursementDate() { return disbursementDate; }
        public Date getRepaymentDueDate() { return repaymentDueDate; }
        public BigDecimal getRepaidAmount() { return repaidAmount; }
        public void recordRepayment(BigDecimal amount) { repaidAmount = repaidAmount.add(amount); }
        public String getAssistanceReason() { return assistanceReason; }
        public String getStatus() { return status; }
        public void setStatus(String newStatus) { this.status = newStatus; }
    }
    
    /**
     * Emergency event log
     */
    public static class EmergencyEvent {
        private final String eventId;
        private final EmergencyLevel level;
        private final String eventDescription;
        private final Date eventTime;
        private final String triggeredBy;
        private final Map<String, String> contextData;
        private final List<String> actionsTriggered;
        
        public EmergencyEvent(String eventId, EmergencyLevel level, String description,
                            String triggeredBy) {
            this.eventId = eventId;
            this.level = level;
            this.eventDescription = description;
            this.eventTime = new Date();
            this.triggeredBy = triggeredBy;
            this.contextData = new ConcurrentHashMap<>();
            this.actionsTriggered = Collections.synchronizedList(new ArrayList<>());
        }
        
        public String getEventId() { return eventId; }
        public EmergencyLevel getLevel() { return level; }
        public String getEventDescription() { return eventDescription; }
        public Date getEventTime() { return eventTime; }
        public String getTriggeredBy() { return triggeredBy; }
        public Map<String, String> getContextData() { return new HashMap<>(contextData); }
        public List<String> getActionsTriggered() { return new ArrayList<>(actionsTriggered); }
        public void addAction(String actionId) { actionsTriggered.add(actionId); }
    }
    
    // Storage
    private final Map<String, BigDecimal> emergencyReservePool;
    private final Map<String, StabilizationAction> activeActions;
    private final Map<String, ReserveInjection> injections;
    private final Map<String, LiquidityAssistance> liquidityAgreements;
    private final Map<String, EmergencyEvent> emergencyLog;
    private final AtomicLong totalDisbursed;
    private final AtomicLong totalRepaid;
    
    // Circuit breakers
    private static final BigDecimal MAX_INJECTION_PER_EVENT = new BigDecimal("1000000000"); // 1B ASU
    private static final BigDecimal MAX_TOTAL_OUTSTANDING = new BigDecimal("5000000000"); // 5B ASU
    private static final long INJECTION_COOLDOWN_MS = 24 * 60 * 60 * 1000; // 24 hours
    private volatile long lastInjectionTime = 0;
    
    public EmergencyStabilityFund() {
        this.emergencyReservePool = new ConcurrentHashMap<>();
        this.activeActions = new ConcurrentHashMap<>();
        this.injections = new ConcurrentHashMap<>();
        this.liquidityAgreements = new ConcurrentHashMap<>();
        this.emergencyLog = new ConcurrentHashMap<>();
        this.totalDisbursed = new AtomicLong(0);
        this.totalRepaid = new AtomicLong(0);
        
        // Initialize reserve pools for major currencies
        initializeEmergencyPools();
        
        LOGGER.info("EmergencyStabilityFund initialized");
    }
    
    /**
     * Triggers emergency stabilization
     */
    public String triggerEmergencyStabilization(String tokenSymbol) throws Exception {
        
        // Log emergency event
        String eventId = generateEventId();
        EmergencyEvent event = new EmergencyEvent(
            eventId,
            EmergencyLevel.CRITICAL,
            "Emergency stabilization triggered for " + tokenSymbol,
            "AUTOMATED_SYSTEM"
        );
        emergencyLog.put(eventId, event);
        
        // Determine action type
        String actionId = generateActionId(tokenSymbol);
        StabilizationAction action = new StabilizationAction(
            actionId,
            tokenSymbol,
            EmergencyLevel.CRITICAL,
            "LIQUIDITY_INJECTION",
            new BigDecimal("100000000"), // 100M ASU
            "Emergency backing ratio recovery"
        );
        
        activeActions.put(actionId, action);
        event.addAction(actionId);
        
        // Request authorization
        requestEmergencyAuthorization(action);
        
        LOGGER.severe(String.format(
            "EMERGENCY STABILIZATION TRIGGERED: %s - Action: %s",
            tokenSymbol, actionId
        ));
        
        return actionId;
    }
    
    /**
     * Injects reserves for emergency recovery
     */
    public String injectReserves(String tokenSymbol, BigDecimal amount, String justification,
                               EmergencyLevel level) throws Exception {
        
        // Check circuit breakers
        if (amount.compareTo(MAX_INJECTION_PER_EVENT) > 0) {
            throw new IllegalArgumentException(
                "Injection amount exceeds maximum per event: " + amount + " > " + MAX_INJECTION_PER_EVENT
            );
        }
        
        // Check cooldown
        long timeSinceLastInjection = System.currentTimeMillis() - lastInjectionTime;
        if (timeSinceLastInjection < INJECTION_COOLDOWN_MS) {
            throw new IllegalStateException(
                "Injection cooldown active. Wait " + 
                ((INJECTION_COOLDOWN_MS - timeSinceLastInjection) / 1000) + " seconds"
            );
        }
        
        // Check total outstanding
        BigDecimal totalOutstanding = liquidityAgreements.values().stream()
            .filter(la -> !la.getStatus().equals("COMPLETED"))
            .map(LiquidityAssistance::getAssistanceAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalOutstanding.add(amount).compareTo(MAX_TOTAL_OUTSTANDING) > 0) {
            throw new IllegalStateException(
                "Total outstanding assistance would exceed maximum: " + 
                totalOutstanding.add(amount) + " > " + MAX_TOTAL_OUTSTANDING
            );
        }
        
        // Create injection record
        String injectionId = generateInjectionId(tokenSymbol);
        ReserveInjection injection = new ReserveInjection(
            injectionId,
            tokenSymbol,
            amount,
            "EMERGENCY_FUND",
            "TOKEN_RESERVES",
            justification,
            level
        );
        
        injections.put(injectionId, injection);
        
        // Deduct from emergency pool
        BigDecimal currentPool = emergencyReservePool.getOrDefault(tokenSymbol, BigDecimal.ZERO);
        emergencyReservePool.put(tokenSymbol, currentPool.subtract(amount));
        
        totalDisbursed.addAndGet(amount.longValue());
        lastInjectionTime = System.currentTimeMillis();
        
        LOGGER.info(String.format(
            "Emergency reserves injected: %s for %s. Amount: %s. Reason: %s",
            injectionId, tokenSymbol, amount, justification
        ));
        
        return injectionId;
    }
    
    /**
     * Provides liquidity assistance with repayment terms
     */
    public String provideLiquidityAssistance(String tokenSymbol, BigDecimal amount,
                                            int repaymentDays, BigDecimal interestRate,
                                            String reason) throws Exception {
        
        String assistanceId = generateAssistanceId(tokenSymbol);
        
        LiquidityAssistance assistance = new LiquidityAssistance(
            assistanceId,
            tokenSymbol,
            amount,
            repaymentDays,
            interestRate,
            reason
        );
        
        liquidityAgreements.put(assistanceId, assistance);
        
        // Disburse funds
        BigDecimal currentPool = emergencyReservePool.getOrDefault(tokenSymbol, BigDecimal.ZERO);
        emergencyReservePool.put(tokenSymbol, currentPool.subtract(amount));
        
        totalDisbursed.addAndGet(amount.longValue());
        
        LOGGER.info(String.format(
            "Liquidity assistance provided: %s for %s. Amount: %s, Repayment: %d days @ %.2f%%",
            assistanceId, tokenSymbol, amount, repaymentDays, 
            interestRate.multiply(new BigDecimal("100"))
        ));
        
        return assistanceId;
    }
    
    /**
     * Records repayment of liquidity assistance
     */
    public void recordRepayment(String assistanceId, BigDecimal repaymentAmount) throws Exception {
        
        LiquidityAssistance assistance = liquidityAgreements.get(assistanceId);
        if (assistance == null) {
            throw new IllegalArgumentException("Assistance not found: " + assistanceId);
        }
        
        assistance.recordRepayment(repaymentAmount);
        
        // Return to pool
        BigDecimal currentPool = emergencyReservePool.getOrDefault(
            assistance.getTokenSymbol(), BigDecimal.ZERO
        );
        emergencyReservePool.put(assistance.getTokenSymbol(), 
            currentPool.add(repaymentAmount));
        
        totalRepaid.addAndGet(repaymentAmount.longValue());
        
        // Check if fully repaid
        if (assistance.getRepaidAmount().compareTo(assistance.getAssistanceAmount()) >= 0) {
            assistance.setStatus("COMPLETED");
            LOGGER.info("Liquidity assistance fully repaid: " + assistanceId);
        }
        
        LOGGER.info(String.format(
            "Repayment recorded: %s - Amount: %s. Outstanding: %s",
            assistanceId, repaymentAmount,
            assistance.getAssistanceAmount().subtract(assistance.getRepaidAmount())
        ));
    }
    
    /**
     * Gets emergency fund status
     */
    public Map<String, Object> getEmergencyFundStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("totalReservePool", emergencyReservePool.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        status.put("totalDisbursed", totalDisbursed.get());
        status.put("totalRepaid", totalRepaid.get());
        status.put("outstandingAssistance", liquidityAgreements.values().stream()
            .filter(la -> !la.getStatus().equals("COMPLETED"))
            .map(LiquidityAssistance::getAssistanceAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        status.put("activeActions", activeActions.size());
        status.put("emergencyEvents", emergencyLog.size());
        status.put("poolDetails", new HashMap<>(emergencyReservePool));
        
        return status;
    }
    
    /**
     * Helper methods
     */
    
    private void initializeEmergencyPools() {
        // Initialize emergency pools for major tokens
        emergencyReservePool.put("ASU", new BigDecimal("10000000000")); // 10B ASU
        emergencyReservePool.put("NGN-TOKEN", new BigDecimal("5000000000")); // 5B NGN
        emergencyReservePool.put("GHS-TOKEN", new BigDecimal("3000000000")); // 3B GHS
        emergencyReservePool.put("ZAR-TOKEN", new BigDecimal("3000000000")); // 3B ZAR
        
        LOGGER.info("Emergency reserve pools initialized");
    }
    
    private void requestEmergencyAuthorization(StabilizationAction action) {
        LOGGER.info("Requesting emergency authorization for: " + action.getActionId());
        // DAO governance vote initiated
    }
    
    private String generateEventId() {
        return "EMERG_" + System.currentTimeMillis() + "_" + 
               UUID.randomUUID().toString().substring(0, 8);
    }
    
    private String generateActionId(String tokenSymbol) {
        return "ACT_" + tokenSymbol + "_" + System.nanoTime();
    }
    
    private String generateInjectionId(String tokenSymbol) {
        return "INJ_" + tokenSymbol + "_" + System.nanoTime();
    }
    
    private String generateAssistanceId(String tokenSymbol) {
        return "ASSIST_" + tokenSymbol + "_" + System.nanoTime();
    }
}