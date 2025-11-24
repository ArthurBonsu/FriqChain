package org.example.app.core.banking.registration;

import org.example.app.core.crypto.Certificate;
import org.example.app.core.crypto.CertificateAuthority;
import org.example.app.core.crypto.PKIManager;
import org.example.app.core.state.StateManager;
import org.example.app.core.internal.EventLog;
import org.example.app.core.p2p.PeerNetwork;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;

import java.math.BigInteger;
import java.security.PublicKey;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * AIAgentRegistry - Manages AI Agent verification and authorization (Requirement #8)
 * 
 * Implements AI agent identity and authorization for the TINC Blockchain with:
 * - AI agent registration with verified ownership
 * - Capability-based authorization (what the AI can do)
 * - Spending limits and transaction constraints
 * - Principal (owner) delegation controls
 * - Activity monitoring and anomaly detection
 * - Emergency kill switch for compromised agents
 * - Audit trail for all AI-initiated actions
 */
public class AIAgentRegistry {
    private static final Logger LOGGER = Logger.getLogger(AIAgentRegistry.class.getName());
    
    // Default limits for AI agents
    private static final BigInteger DEFAULT_TX_LIMIT = new BigInteger("1000000000000000000"); // 1 token
    private static final BigInteger DEFAULT_DAILY_LIMIT = new BigInteger("10000000000000000000"); // 10 tokens
    private static final int DEFAULT_MAX_TX_PER_HOUR = 100;
    private static final int ANOMALY_THRESHOLD_MULTIPLIER = 3;
    
    // Agent capability levels
    public enum CapabilityLevel {
        READ_ONLY(0),           // Can only read data
        LIMITED_TRADE(1),       // Can execute small trades within limits
        STANDARD_TRADE(2),      // Can execute standard trades
        ELEVATED_TRADE(3),      // Can execute larger trades
        FULL_AUTONOMY(4);       // Full trading capability (requires multi-sig approval)
        
        private final int level;
        CapabilityLevel(int level) { this.level = level; }
        public int getLevel() { return level; }
    }
    
    // Agent status
    public enum AgentStatus {
        PENDING,        // Awaiting activation
        ACTIVE,         // Fully operational
        LIMITED,        // Operating with reduced capabilities
        SUSPENDED,      // Temporarily disabled
        KILLED,         // Emergency killed
        EXPIRED,        // Authorization expired
        REVOKED         // Permanently revoked
    }
    
    // Agent types
    public enum AgentType {
        TRADING_BOT,        // Automated trading
        PORTFOLIO_MANAGER,  // Portfolio management
        ARBITRAGE_BOT,      // Cross-market arbitrage
        LIQUIDITY_PROVIDER, // Automated market making
        PAYMENT_PROCESSOR,  // Automated payments
        DATA_ANALYZER,      // Read-only analytics
        GOVERNANCE_VOTER,   // Automated governance participation
        GENERAL_PURPOSE     // General AI agent
    }
    
    /**
     * Represents a registered AI agent
     */
    public static class AIAgent {
        private final String agentId;
        private final String agentName;
        private final AgentType agentType;
        private final Address agentAddress;
        private final Address principalAddress; // Owner/controller
        private final PublicKey agentPublicKey;
        private final Certificate certificate;
        private final Date registrationDate;
        private final String modelIdentifier; // AI model identifier (e.g., "gpt-4", "claude-3")
        private final String developerOrg; // Organization that developed/deployed the agent
        
        // Authorization settings
        private CapabilityLevel capabilityLevel;
        private AgentStatus status;
        private BigInteger perTransactionLimit;
        private BigInteger dailySpendingLimit;
        private int maxTransactionsPerHour;
        private Set<String> allowedOperations;
        private Set<Address> allowedCounterparties;
        private Date authorizationExpiry;
        
        // Activity tracking
        private BigInteger totalTransactionVolume;
        private BigInteger dailyTransactionVolume;
        private Date lastDailyReset;
        private long totalTransactions;
        private long hourlyTransactions;
        private Date lastHourlyReset;
        private Date lastActivityTime;
        private int anomalyScore; // 0-100, higher = more anomalous
        
        // Emergency controls
        private boolean killSwitchActive;
        private String killReason;
        private Date killTimestamp;
        private List<Address> multiSigApprovers; // For high-capability agents
        
        public AIAgent(String agentId, String agentName, AgentType agentType,
                      Address agentAddress, Address principalAddress,
                      PublicKey agentPublicKey, Certificate certificate,
                      String modelIdentifier, String developerOrg,
                      CapabilityLevel capabilityLevel, Date authExpiry) {
            this.agentId = agentId;
            this.agentName = agentName;
            this.agentType = agentType;
            this.agentAddress = agentAddress;
            this.principalAddress = principalAddress;
            this.agentPublicKey = agentPublicKey;
            this.certificate = certificate;
            this.registrationDate = new Date();
            this.modelIdentifier = modelIdentifier;
            this.developerOrg = developerOrg;
            
            this.capabilityLevel = capabilityLevel;
            this.status = AgentStatus.PENDING;
            this.perTransactionLimit = DEFAULT_TX_LIMIT;
            this.dailySpendingLimit = DEFAULT_DAILY_LIMIT;
            this.maxTransactionsPerHour = DEFAULT_MAX_TX_PER_HOUR;
            this.allowedOperations = new HashSet<>();
            this.allowedCounterparties = new HashSet<>();
            this.authorizationExpiry = authExpiry;
            
            this.totalTransactionVolume = BigInteger.ZERO;
            this.dailyTransactionVolume = BigInteger.ZERO;
            this.lastDailyReset = new Date();
            this.totalTransactions = 0;
            this.hourlyTransactions = 0;
            this.lastHourlyReset = new Date();
            this.anomalyScore = 0;
            
            this.killSwitchActive = false;
            this.multiSigApprovers = new ArrayList<>();
        }
        
        // Getters
        public String getAgentId() { return agentId; }
        public String getAgentName() { return agentName; }
        public AgentType getAgentType() { return agentType; }
        public Address getAgentAddress() { return agentAddress; }
        public Address getPrincipalAddress() { return principalAddress; }
        public PublicKey getAgentPublicKey() { return agentPublicKey; }
        public Certificate getCertificate() { return certificate; }
        public Date getRegistrationDate() { return registrationDate; }
        public String getModelIdentifier() { return modelIdentifier; }
        public String getDeveloperOrg() { return developerOrg; }
        public CapabilityLevel getCapabilityLevel() { return capabilityLevel; }
        public AgentStatus getStatus() { return status; }
        public BigInteger getPerTransactionLimit() { return perTransactionLimit; }
        public BigInteger getDailySpendingLimit() { return dailySpendingLimit; }
        public int getMaxTransactionsPerHour() { return maxTransactionsPerHour; }
        public Set<String> getAllowedOperations() { return new HashSet<>(allowedOperations); }
        public Set<Address> getAllowedCounterparties() { return new HashSet<>(allowedCounterparties); }
        public Date getAuthorizationExpiry() { return authorizationExpiry; }
        public BigInteger getTotalTransactionVolume() { return totalTransactionVolume; }
        public BigInteger getDailyTransactionVolume() { return dailyTransactionVolume; }
        public long getTotalTransactions() { return totalTransactions; }
        public Date getLastActivityTime() { return lastActivityTime; }
        public int getAnomalyScore() { return anomalyScore; }
        public boolean isKillSwitchActive() { return killSwitchActive; }
        public String getKillReason() { return killReason; }
        public List<Address> getMultiSigApprovers() { return new ArrayList<>(multiSigApprovers); }
        
        // Setters
        public void setCapabilityLevel(CapabilityLevel level) { this.capabilityLevel = level; }
        public void setStatus(AgentStatus status) { this.status = status; }
        public void setPerTransactionLimit(BigInteger limit) { this.perTransactionLimit = limit; }
        public void setDailySpendingLimit(BigInteger limit) { this.dailySpendingLimit = limit; }
        public void setMaxTransactionsPerHour(int max) { this.maxTransactionsPerHour = max; }
        public void setAuthorizationExpiry(Date expiry) { this.authorizationExpiry = expiry; }
        public void setAnomalyScore(int score) { this.anomalyScore = Math.max(0, Math.min(100, score)); }
        
        public void addAllowedOperation(String op) { allowedOperations.add(op); }
        public void removeAllowedOperation(String op) { allowedOperations.remove(op); }
        public void addAllowedCounterparty(Address addr) { allowedCounterparties.add(addr); }
        public void addMultiSigApprover(Address addr) { multiSigApprovers.add(addr); }
        
        public void recordTransaction(BigInteger amount) {
            checkAndResetLimits();
            this.totalTransactionVolume = this.totalTransactionVolume.add(amount);
            this.dailyTransactionVolume = this.dailyTransactionVolume.add(amount);
            this.totalTransactions++;
            this.hourlyTransactions++;
            this.lastActivityTime = new Date();
        }
        
        private void checkAndResetLimits() {
            Date now = new Date();
            // Reset daily limit
            if (now.getTime() - lastDailyReset.getTime() > 86400000) {
                dailyTransactionVolume = BigInteger.ZERO;
                lastDailyReset = now;
            }
            // Reset hourly limit
            if (now.getTime() - lastHourlyReset.getTime() > 3600000) {
                hourlyTransactions = 0;
                lastHourlyReset = now;
            }
        }
        
        public void activateKillSwitch(String reason) {
            this.killSwitchActive = true;
            this.killReason = reason;
            this.killTimestamp = new Date();
            this.status = AgentStatus.KILLED;
        }
        
        public boolean canExecuteTransaction(BigInteger amount) {
            if (status != AgentStatus.ACTIVE) return false;
            if (killSwitchActive) return false;
            if (new Date().after(authorizationExpiry)) return false;
            
            checkAndResetLimits();
            if (amount.compareTo(perTransactionLimit) > 0) return false;
            if (dailyTransactionVolume.add(amount).compareTo(dailySpendingLimit) > 0) return false;
            if (hourlyTransactions >= maxTransactionsPerHour) return false;
            
            return true;
        }
        
        public boolean isExpired() {
            return new Date().after(authorizationExpiry);
        }
    }
    
    /**
     * Represents an AI agent action for audit trail
     */
    public static class AgentAction {
        private final String actionId;
        private final String agentId;
        private final String actionType;
        private final String description;
        private final BigInteger amount;
        private final Address targetAddress;
        private final Date timestamp;
        private final boolean success;
        private final String failureReason;
        private final byte[] signature;
        
        public AgentAction(String agentId, String actionType, String description,
                         BigInteger amount, Address targetAddress, boolean success,
                         String failureReason, byte[] signature) {
            this.actionId = UUID.randomUUID().toString();
            this.agentId = agentId;
            this.actionType = actionType;
            this.description = description;
            this.amount = amount;
            this.targetAddress = targetAddress;
            this.timestamp = new Date();
            this.success = success;
            this.failureReason = failureReason;
            this.signature = signature;
        }
        
        public String getActionId() { return actionId; }
        public String getAgentId() { return agentId; }
        public String getActionType() { return actionType; }
        public Date getTimestamp() { return timestamp; }
        public boolean isSuccess() { return success; }
    }
    
    /**
     * Represents an anomaly detection alert
     */
    public static class AnomalyAlert {
        private final String alertId;
        private final String agentId;
        private final String alertType;
        private final String description;
        private final int severityScore; // 0-100
        private final Date timestamp;
        private boolean resolved;
        private String resolution;
        
        public AnomalyAlert(String agentId, String alertType, String description, int severity) {
            this.alertId = UUID.randomUUID().toString();
            this.agentId = agentId;
            this.alertType = alertType;
            this.description = description;
            this.severityScore = severity;
            this.timestamp = new Date();
            this.resolved = false;
        }
        
        public String getAlertId() { return alertId; }
        public String getAgentId() { return agentId; }
        public int getSeverityScore() { return severityScore; }
        public boolean isResolved() { return resolved; }
        
        public void resolve(String resolution) {
            this.resolved = true;
            this.resolution = resolution;
        }
    }
    
    // Core infrastructure
    private final StateManager stateManager;
    private final CertificateAuthority certificateAuthority;
    private final PKIManager pkiManager;
    private final EventLog eventLog;
    private final PeerNetwork peerNetwork;
    
    // Agent storage
    private final Map<String, AIAgent> agents;
    private final Map<Address, String> addressToAgent;
    private final Map<Address, Set<String>> principalAgents; // principal -> their agents
    private final Map<AgentType, Set<String>> typeAgents;
    
    // Activity tracking
    private final Map<String, List<AgentAction>> agentActions;
    private final Map<String, List<AnomalyAlert>> agentAlerts;
    private final AtomicLong totalActions;
    
    // Executor
    private final ScheduledExecutorService scheduler;
    
    public AIAgentRegistry(StateManager stateManager, CertificateAuthority certificateAuthority,
                          PKIManager pkiManager, EventLog eventLog, PeerNetwork peerNetwork) {
        this.stateManager = stateManager;
        this.certificateAuthority = certificateAuthority;
        this.pkiManager = pkiManager;
        this.eventLog = eventLog;
        this.peerNetwork = peerNetwork;
        
        this.agents = new ConcurrentHashMap<>();
        this.addressToAgent = new ConcurrentHashMap<>();
        this.principalAgents = new ConcurrentHashMap<>();
        this.typeAgents = new ConcurrentHashMap<>();
        this.agentActions = new ConcurrentHashMap<>();
        this.agentAlerts = new ConcurrentHashMap<>();
        this.totalActions = new AtomicLong(0);
        
        for (AgentType type : AgentType.values()) {
            typeAgents.put(type, ConcurrentHashMap.newKeySet());
        }
        
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::runAnomalyDetection, 5, 5, TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(this::checkExpiredAgents, 1, 1, TimeUnit.HOURS);
        
        LOGGER.info("AIAgentRegistry initialized");
    }
    
    /**
     * Registers a new AI agent
     */
    public String registerAgent(String agentName, AgentType agentType,
                               Address agentAddress, Address principalAddress,
                               PublicKey agentPublicKey, String modelIdentifier,
                               String developerOrg, CapabilityLevel capabilityLevel,
                               int authorizationDays) {
        try {
            // Check for duplicate
            if (addressToAgent.containsKey(agentAddress)) {
                LOGGER.warning("Agent address already registered: " + agentAddress);
                return null;
            }
            
            // Validate principal exists in state
            if (stateManager.getState(principalAddress.toString()) == null) {
                LOGGER.warning("Principal address not found: " + principalAddress);
                return null;
            }
            
            // Generate agent ID
            String agentId = generateAgentId(agentName, agentAddress);
            
            // Issue certificate
            Certificate certificate = certificateAuthority.issueCertificate(
                "AI_AGENT_" + agentId, agentPublicKey);
            
            // Calculate expiry
            Date authExpiry = new Date(System.currentTimeMillis() + 
                                      (long) authorizationDays * 86400000);
            
            // Create agent
            AIAgent agent = new AIAgent(agentId, agentName, agentType, agentAddress,
                                       principalAddress, agentPublicKey, certificate,
                                       modelIdentifier, developerOrg, capabilityLevel, authExpiry);
            
            // Set default allowed operations based on type
            setDefaultOperations(agent);
            
            // For high capability agents, require multi-sig
            if (capabilityLevel.getLevel() >= CapabilityLevel.ELEVATED_TRADE.getLevel()) {
                agent.addMultiSigApprover(principalAddress);
            }
            
            // Store agent
            agents.put(agentId, agent);
            addressToAgent.put(agentAddress, agentId);
            principalAgents.computeIfAbsent(principalAddress, k -> ConcurrentHashMap.newKeySet())
                          .add(agentId);
            typeAgents.get(agentType).add(agentId);
            agentActions.put(agentId, new CopyOnWriteArrayList<>());
            agentAlerts.put(agentId, new CopyOnWriteArrayList<>());
            
            eventLog.logEvent("AI_AGENT_REGISTERED: " + agentId + " (" + agentName + 
                             ") model=" + modelIdentifier + " capability=" + capabilityLevel);
            LOGGER.info("AI Agent registered: " + agentId);
            
            return agentId;
            
        } catch (Exception e) {
            LOGGER.severe("Error registering AI agent: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Sets default allowed operations based on agent type
     */
    private void setDefaultOperations(AIAgent agent) {
        switch (agent.getAgentType()) {
            case TRADING_BOT:
                agent.addAllowedOperation("SWAP");
                agent.addAllowedOperation("LIMIT_ORDER");
                agent.addAllowedOperation("CANCEL_ORDER");
                break;
            case PORTFOLIO_MANAGER:
                agent.addAllowedOperation("SWAP");
                agent.addAllowedOperation("STAKE");
                agent.addAllowedOperation("UNSTAKE");
                agent.addAllowedOperation("REBALANCE");
                break;
            case LIQUIDITY_PROVIDER:
                agent.addAllowedOperation("ADD_LIQUIDITY");
                agent.addAllowedOperation("REMOVE_LIQUIDITY");
                agent.addAllowedOperation("COLLECT_FEES");
                break;
            case PAYMENT_PROCESSOR:
                agent.addAllowedOperation("TRANSFER");
                agent.addAllowedOperation("BATCH_TRANSFER");
                break;
            case DATA_ANALYZER:
                agent.addAllowedOperation("READ");
                break;
            case GOVERNANCE_VOTER:
                agent.addAllowedOperation("VOTE");
                agent.addAllowedOperation("DELEGATE");
                break;
            default:
                agent.addAllowedOperation("READ");
        }
    }
    
    /**
     * Activates an AI agent
     */
    public boolean activateAgent(String agentId, Address approverAddress) {
        AIAgent agent = agents.get(agentId);
        if (agent == null || agent.getStatus() != AgentStatus.PENDING) {
            return false;
        }
        
        // Verify approver is the principal
        if (!agent.getPrincipalAddress().equals(approverAddress)) {
            LOGGER.warning("Activation denied: approver is not principal");
            return false;
        }
        
        agent.setStatus(AgentStatus.ACTIVE);
        eventLog.logEvent("AI_AGENT_ACTIVATED: " + agentId);
        return true;
    }
    
    /**
     * Authorizes an AI agent action
     */
    public boolean authorizeAction(String agentId, String actionType, BigInteger amount,
                                  Address targetAddress, byte[] signature) {
        AIAgent agent = agents.get(agentId);
        if (agent == null) {
            recordAction(agentId, actionType, "Unknown agent", amount, targetAddress, 
                        false, "Agent not found", signature);
            return false;
        }
        
        // Check if action is allowed
        if (!agent.getAllowedOperations().contains(actionType)) {
            recordAction(agentId, actionType, "Operation not allowed", amount, targetAddress,
                        false, "Operation not in allowed list", signature);
            return false;
        }
        
        // Check transaction limits
        if (amount != null && !agent.canExecuteTransaction(amount)) {
            recordAction(agentId, actionType, "Limit exceeded", amount, targetAddress,
                        false, "Transaction limit exceeded", signature);
            
            // Raise anomaly if frequently hitting limits
            raiseAnomaly(agentId, "LIMIT_EXCEEDED", 
                        "Agent frequently hitting transaction limits", 30);
            return false;
        }
        
        // Check counterparty restrictions
        if (targetAddress != null && !agent.getAllowedCounterparties().isEmpty() &&
            !agent.getAllowedCounterparties().contains(targetAddress)) {
            recordAction(agentId, actionType, "Unauthorized counterparty", amount, targetAddress,
                        false, "Counterparty not in allowed list", signature);
            return false;
        }
        
        // For high-value transactions, check multi-sig
        if (amount != null && 
            amount.compareTo(agent.getPerTransactionLimit().multiply(BigInteger.TEN)) > 0) {
            if (agent.getMultiSigApprovers().size() < 2) {
                recordAction(agentId, actionType, "Multi-sig required", amount, targetAddress,
                            false, "High-value transaction requires multi-sig", signature);
                return false;
            }
        }
        
        // Record successful authorization
        if (amount != null) {
            agent.recordTransaction(amount);
        }
        
        recordAction(agentId, actionType, "Action authorized", amount, targetAddress,
                    true, null, signature);
        
        // Check for anomalies
        checkTransactionAnomaly(agent, amount);
        
        return true;
    }
    
    /**
     * Records an agent action for audit trail
     */
    private void recordAction(String agentId, String actionType, String description,
                             BigInteger amount, Address targetAddress, boolean success,
                             String failureReason, byte[] signature) {
        AgentAction action = new AgentAction(agentId, actionType, description, amount,
                                            targetAddress, success, failureReason, signature);
        
        List<AgentAction> actions = agentActions.get(agentId);
        if (actions != null) {
            actions.add(action);
            // Keep only last 1000 actions
            while (actions.size() > 1000) {
                actions.remove(0);
            }
        }
        
        totalActions.incrementAndGet();
        
        eventLog.logEvent("AI_AGENT_ACTION: " + agentId + " " + actionType + 
                         " success=" + success);
    }
    
    /**
     * Checks for transaction anomalies
     */
    private void checkTransactionAnomaly(AIAgent agent, BigInteger amount) {
        if (amount == null) return;
        
        // Check for unusual transaction size
        BigInteger avgTx = agent.getTotalTransactions() > 0 ?
            agent.getTotalTransactionVolume().divide(BigInteger.valueOf(agent.getTotalTransactions())) :
            BigInteger.ZERO;
        
        if (avgTx.compareTo(BigInteger.ZERO) > 0 &&
            amount.compareTo(avgTx.multiply(BigInteger.valueOf(ANOMALY_THRESHOLD_MULTIPLIER))) > 0) {
            raiseAnomaly(agent.getAgentId(), "UNUSUAL_TX_SIZE",
                        "Transaction size " + ANOMALY_THRESHOLD_MULTIPLIER + "x above average", 40);
        }
        
        // Check for rapid transactions
        List<AgentAction> recentActions = agentActions.get(agent.getAgentId());
        if (recentActions != null && recentActions.size() >= 10) {
            long recentTime = System.currentTimeMillis() - 60000; // Last minute
            long recentCount = recentActions.stream()
                .filter(a -> a.getTimestamp().getTime() > recentTime)
                .count();
            
            if (recentCount > 20) {
                raiseAnomaly(agent.getAgentId(), "RAPID_TRANSACTIONS",
                            "Unusually high transaction frequency: " + recentCount + "/min", 50);
            }
        }
    }
    
    /**
     * Raises an anomaly alert
     */
    private void raiseAnomaly(String agentId, String alertType, String description, int severity) {
        AnomalyAlert alert = new AnomalyAlert(agentId, alertType, description, severity);
        
        List<AnomalyAlert> alerts = agentAlerts.get(agentId);
        if (alerts != null) {
            alerts.add(alert);
        }
        
        AIAgent agent = agents.get(agentId);
        if (agent != null) {
            agent.setAnomalyScore(agent.getAnomalyScore() + severity / 5);
            
            // Auto-suspend if anomaly score too high
            if (agent.getAnomalyScore() > 80) {
                agent.setStatus(AgentStatus.SUSPENDED);
                eventLog.logEvent("AI_AGENT_AUTO_SUSPENDED: " + agentId + " anomalyScore=" + 
                                 agent.getAnomalyScore());
            }
        }
        
        eventLog.logEvent("AI_AGENT_ANOMALY: " + agentId + " " + alertType + " severity=" + severity);
    }
    
    /**
     * Emergency kill switch for an agent
     */
    public boolean killAgent(String agentId, Address requestorAddress, String reason) {
        AIAgent agent = agents.get(agentId);
        if (agent == null) {
            return false;
        }
        
        // Only principal can kill
        if (!agent.getPrincipalAddress().equals(requestorAddress)) {
            LOGGER.warning("Kill switch denied: requestor is not principal");
            return false;
        }
        
        agent.activateKillSwitch(reason);
        eventLog.logEvent("AI_AGENT_KILLED: " + agentId + " reason=" + reason);
        LOGGER.warning("AI Agent killed: " + agentId + " - " + reason);
        
        return true;
    }
    
    /**
     * Updates agent limits (principal only)
     */
    public boolean updateAgentLimits(String agentId, Address principalAddress,
                                    BigInteger perTxLimit, BigInteger dailyLimit,
                                    int maxTxPerHour) {
        AIAgent agent = agents.get(agentId);
        if (agent == null || !agent.getPrincipalAddress().equals(principalAddress)) {
            return false;
        }
        
        if (perTxLimit != null) agent.setPerTransactionLimit(perTxLimit);
        if (dailyLimit != null) agent.setDailySpendingLimit(dailyLimit);
        if (maxTxPerHour > 0) agent.setMaxTransactionsPerHour(maxTxPerHour);
        
        eventLog.logEvent("AI_AGENT_LIMITS_UPDATED: " + agentId);
        return true;
    }
    
    /**
     * Extends agent authorization
     */
    public boolean extendAuthorization(String agentId, Address principalAddress, int additionalDays) {
        AIAgent agent = agents.get(agentId);
        if (agent == null || !agent.getPrincipalAddress().equals(principalAddress)) {
            return false;
        }
        
        Date newExpiry = new Date(agent.getAuthorizationExpiry().getTime() + 
                                 (long) additionalDays * 86400000);
        agent.setAuthorizationExpiry(newExpiry);
        
        if (agent.getStatus() == AgentStatus.EXPIRED) {
            agent.setStatus(AgentStatus.ACTIVE);
        }
        
        eventLog.logEvent("AI_AGENT_AUTH_EXTENDED: " + agentId + " until " + newExpiry);
        return true;
    }
    
    /**
     * Runs periodic anomaly detection
     */
    private void runAnomalyDetection() {
        for (AIAgent agent : agents.values()) {
            if (agent.getStatus() != AgentStatus.ACTIVE) continue;
            
            // Decay anomaly score over time
            if (agent.getAnomalyScore() > 0) {
                agent.setAnomalyScore(agent.getAnomalyScore() - 1);
            }
            
            // Check for inactive agents with pending transactions
            if (agent.getLastActivityTime() != null) {
                long inactiveMinutes = (System.currentTimeMillis() - 
                                       agent.getLastActivityTime().getTime()) / 60000;
                
                // Agent was very active but suddenly stopped
                if (agent.getTotalTransactions() > 100 && inactiveMinutes > 60) {
                    raiseAnomaly(agent.getAgentId(), "SUDDEN_INACTIVITY",
                                "Previously active agent has been inactive for " + inactiveMinutes + " minutes", 20);
                }
            }
        }
    }
    
    /**
     * Checks and updates expired agents
     */
    private void checkExpiredAgents() {
        Date now = new Date();
        for (AIAgent agent : agents.values()) {
            if (agent.getStatus() == AgentStatus.ACTIVE && agent.isExpired()) {
                agent.setStatus(AgentStatus.EXPIRED);
                eventLog.logEvent("AI_AGENT_EXPIRED: " + agent.getAgentId());
            }
        }
    }
    
    /**
     * Gets agents owned by a principal
     */
    public List<AIAgent> getAgentsByPrincipal(Address principalAddress) {
        Set<String> agentIds = principalAgents.get(principalAddress);
        if (agentIds == null) return new ArrayList<>();
        return agentIds.stream().map(agents::get).filter(Objects::nonNull).collect(Collectors.toList());
    }
    
    /**
     * Gets agent action history
     */
    public List<AgentAction> getAgentActionHistory(String agentId, int limit) {
        List<AgentAction> actions = agentActions.get(agentId);
        if (actions == null) return new ArrayList<>();
        int start = Math.max(0, actions.size() - limit);
        return new ArrayList<>(actions.subList(start, actions.size()));
    }
    
    /**
     * Gets unresolved anomaly alerts for an agent
     */
    public List<AnomalyAlert> getUnresolvedAlerts(String agentId) {
        List<AnomalyAlert> alerts = agentAlerts.get(agentId);
        if (alerts == null) return new ArrayList<>();
        return alerts.stream().filter(a -> !a.isResolved()).collect(Collectors.toList());
    }
    
    /**
     * Resolves an anomaly alert
     */
    public boolean resolveAlert(String agentId, String alertId, String resolution) {
        List<AnomalyAlert> alerts = agentAlerts.get(agentId);
        if (alerts == null) return false;
        
        for (AnomalyAlert alert : alerts) {
            if (alert.getAlertId().equals(alertId)) {
                alert.resolve(resolution);
                eventLog.logEvent("AI_AGENT_ALERT_RESOLVED: " + alertId);
                return true;
            }
        }
        return false;
    }
    
    private String generateAgentId(String name, Address address) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] h = d.digest((name + address.toString() + System.currentTimeMillis()).getBytes());
            return "AI_" + Hash.fromBytes(h).toString().substring(0, 12).toUpperCase();
        } catch (Exception e) {
            return "AI_" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        }
    }
    
    // Public getters
    public AIAgent getAgent(String agentId) { return agents.get(agentId); }
    public AIAgent getAgentByAddress(Address address) {
        String id = addressToAgent.get(address);
        return id != null ? agents.get(id) : null;
    }
    public Collection<AIAgent> getAllAgents() { return new ArrayList<>(agents.values()); }
    
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAgents", agents.size());
        stats.put("activeAgents", agents.values().stream()
            .filter(a -> a.getStatus() == AgentStatus.ACTIVE).count());
        stats.put("killedAgents", agents.values().stream()
            .filter(a -> a.getStatus() == AgentStatus.KILLED).count());
        stats.put("totalActions", totalActions.get());
        
        Map<AgentType, Long> byType = new HashMap<>();
        for (AgentType t : AgentType.values()) {
            byType.put(t, (long) typeAgents.get(t).size());
        }
        stats.put("agentsByType", byType);
        
        long unresolvedAlerts = agentAlerts.values().stream()
            .flatMap(List::stream)
            .filter(a -> !a.isResolved())
            .count();
        stats.put("unresolvedAlerts", unresolvedAlerts);
        
        return stats;
    }
    
    public void shutdown() {
        scheduler.shutdown();
        LOGGER.info("AIAgentRegistry shutdown");
    }
}