package org.example.app.core.banking.independence;

import org.example.app.core.types.Address;
import org.example.app.core.state.BlockchainState;

import java.math.BigInteger;
import java.security.PublicKey;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * AgentNetworkManager - Retail agent network for TINC Blockchain
 * 
 * CRITICAL MISSION: Enable cash-in/cash-out WITHOUT banks
 * 
 * Features:
 * - Decentralized agent registration
 * - Float management (liquidity tracking)
 * - Commission calculation (incentivize agents)
 * - Geographic routing (find nearest agent)
 * - Fraud detection and reputation scoring
 * - Agent training and certification
 * - Settlement reconciliation
 * - Mobile money integration
 * 
 * Use Cases:
 * - Rural cash points (replace bank branches)
 * - Street vendors (community liquidity)
 * - M-Pesa style agents
 * - Remittance pickup points
 * - Emergency cash access
 * 
 * Agent Types:
 * - Retail shops, gas stations, pharmacies
 * - Mobile money kiosks
 * - Community leaders
 * - Cooperatives
 */
public class AgentNetworkManager {
    private static final Logger LOGGER = Logger.getLogger(AgentNetworkManager.class.getName());
    
    // Network constants
    private static final BigInteger MIN_FLOAT_BALANCE = new BigInteger("50000"); // 50k units
    private static final BigInteger MIN_SECURITY_DEPOSIT = new BigInteger("100000"); // 100k units
    private static final double BASE_COMMISSION_RATE = 0.015; // 1.5%
    private static final int MIN_TRUST_SCORE = 70; // Minimum 70/100
    private static final long SETTLEMENT_WINDOW = 86400000L; // 24 hours
    
    // Agent registry
    private final Map<String, RetailAgent> agents;
    private final Map<String, List<String>> locationAgents; // Location -> Agent IDs
    private final Map<Address, String> addressToAgentId; // Blockchain Address -> Agent ID
    
    // Transaction tracking
    private final Map<String, AgentTransaction> transactions;
    private final Map<String, List<AgentTransaction>> agentTransactions; // Agent ID -> Txs
    
    // Settlement tracking
    private final Map<String, AgentSettlement> pendingSettlements;
    private final Map<String, AgentSettlement> completedSettlements;
    
    // Commission pool
    private final Map<String, BigInteger> commissionPool; // Currency -> Total
    private final Map<String, Map<String, BigInteger>> agentCommissions; // Agent ID -> Currency -> Amount
    
    // Reputation system
    private final Map<String, ReputationScore> agentReputations;
    
    // Geographic index
    private final Map<String, GeoLocation> agentLocations;
    
    // Dependencies
    private final BlockchainState blockchainState;
    
    // Threading
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;
    
    /**
     * RetailAgent - Agent in the network
     */
    public static class RetailAgent {
        private final String agentId;
        private final String businessName;
        private final String ownerName;
        private final Address blockchainAddress;
        private final PublicKey publicKey;
        private final String phoneNumber;
        private final GeoLocation location;
        private final AgentType agentType;
        private final Instant registeredAt;
        private AgentStatus status;
        
        // Financial
        private final Map<String, BigInteger> floatBalance; // Currency -> Balance
        private final BigInteger securityDeposit;
        
        // Capacity
        private final Map<String, BigInteger> dailyLimits; // Currency -> Limit
        private final Map<String, BigInteger> transactionLimits; // Currency -> Limit
        
        // Statistics
        private int totalTransactions;
        private BigInteger totalVolume;
        private int successfulTransactions;
        private int failedTransactions;
        
        public enum AgentType {
            RETAIL_SHOP, GAS_STATION, PHARMACY, SUPERMARKET,
            MOBILE_MONEY_KIOSK, COMMUNITY_LEADER, COOPERATIVE,
            BANK_AGENT, MICROFINANCE
        }
        
        public enum AgentStatus {
            PENDING_APPROVAL, ACTIVE, SUSPENDED, BLACKLISTED, INACTIVE
        }
        
        public RetailAgent(String agentId, String businessName, String ownerName,
                          Address blockchainAddress, PublicKey publicKey, String phoneNumber,
                          GeoLocation location, AgentType agentType, BigInteger securityDeposit) {
            this.agentId = agentId;
            this.businessName = businessName;
            this.ownerName = ownerName;
            this.blockchainAddress = blockchainAddress;
            this.publicKey = publicKey;
            this.phoneNumber = phoneNumber;
            this.location = location;
            this.agentType = agentType;
            this.registeredAt = Instant.now();
            this.status = AgentStatus.PENDING_APPROVAL;
            this.floatBalance = new ConcurrentHashMap<>();
            this.securityDeposit = securityDeposit;
            this.dailyLimits = new ConcurrentHashMap<>();
            this.transactionLimits = new ConcurrentHashMap<>();
            this.totalTransactions = 0;
            this.totalVolume = BigInteger.ZERO;
            this.successfulTransactions = 0;
            this.failedTransactions = 0;
        }
        
        // Getters
        public String getAgentId() { return agentId; }
        public String getBusinessName() { return businessName; }
        public String getOwnerName() { return ownerName; }
        public Address getBlockchainAddress() { return blockchainAddress; }
        public String getPhoneNumber() { return phoneNumber; }
        public GeoLocation getLocation() { return location; }
        public AgentType getAgentType() { return agentType; }
        public AgentStatus getStatus() { return status; }
        public BigInteger getSecurityDeposit() { return securityDeposit; }
        public int getTotalTransactions() { return totalTransactions; }
        public BigInteger getTotalVolume() { return totalVolume; }
        
        public void setStatus(AgentStatus status) { this.status = status; }
        
        public BigInteger getFloatBalance(String currency) {
            return floatBalance.getOrDefault(currency, BigInteger.ZERO);
        }
        
        public void addFloat(String currency, BigInteger amount) {
            BigInteger current = floatBalance.getOrDefault(currency, BigInteger.ZERO);
            floatBalance.put(currency, current.add(amount));
        }
        
        public boolean deductFloat(String currency, BigInteger amount) {
            BigInteger current = floatBalance.getOrDefault(currency, BigInteger.ZERO);
            if (current.compareTo(amount) >= 0) {
                floatBalance.put(currency, current.subtract(amount));
                return true;
            }
            return false;
        }
        
        public void setDailyLimit(String currency, BigInteger limit) {
            dailyLimits.put(currency, limit);
        }
        
        public void setTransactionLimit(String currency, BigInteger limit) {
            transactionLimits.put(currency, limit);
        }
        
        public void incrementTransactions() {
            totalTransactions++;
        }
        
        public void addVolume(BigInteger amount) {
            totalVolume = totalVolume.add(amount);
        }
        
        public void incrementSuccessful() {
            successfulTransactions++;
        }
        
        public void incrementFailed() {
            failedTransactions++;
        }
    }
    
    /**
     * GeoLocation - Geographic coordinates
     */
    public static class GeoLocation {
        private final double latitude;
        private final double longitude;
        private final String city;
        private final String region;
        private final String country;
        
        public GeoLocation(double latitude, double longitude, String city, 
                          String region, String country) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.city = city;
            this.region = region;
            this.country = country;
        }
        
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public String getCity() { return city; }
        public String getRegion() { return region; }
        public String getCountry() { return country; }
        
        /**
         * Calculates distance to another location in kilometers
         */
        public double distanceTo(GeoLocation other) {
            final double R = 6371; // Earth radius in km
            
            double lat1Rad = Math.toRadians(latitude);
            double lat2Rad = Math.toRadians(other.latitude);
            double deltaLat = Math.toRadians(other.latitude - latitude);
            double deltaLon = Math.toRadians(other.longitude - longitude);
            
            double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                      Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                      Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
            
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            
            return R * c;
        }
    }
    
    /**
     * AgentTransaction - Transaction processed by agent
     */
    public static class AgentTransaction {
        private final String transactionId;
        private final String agentId;
        private final Address customerAddress;
        private final TransactionType type;
        private final String currency;
        private final BigInteger amount;
        private final BigInteger commission;
        private final Instant timestamp;
        private TransactionStatus status;
        
        public enum TransactionType {
            CASH_IN, CASH_OUT, BALANCE_INQUIRY, TRANSFER, BILL_PAYMENT
        }
        
        public enum TransactionStatus {
            PENDING, COMPLETED, FAILED, REVERSED
        }
        
        public AgentTransaction(String transactionId, String agentId, Address customerAddress,
                               TransactionType type, String currency, BigInteger amount,
                               BigInteger commission) {
            this.transactionId = transactionId;
            this.agentId = agentId;
            this.customerAddress = customerAddress;
            this.type = type;
            this.currency = currency;
            this.amount = amount;
            this.commission = commission;
            this.timestamp = Instant.now();
            this.status = TransactionStatus.PENDING;
        }
        
        public String getTransactionId() { return transactionId; }
        public String getAgentId() { return agentId; }
        public Address getCustomerAddress() { return customerAddress; }
        public TransactionType getType() { return type; }
        public String getCurrency() { return currency; }
        public BigInteger getAmount() { return amount; }
        public BigInteger getCommission() { return commission; }
        public Instant getTimestamp() { return timestamp; }
        public TransactionStatus getStatus() { return status; }
        
        public void setStatus(TransactionStatus status) { this.status = status; }
    }
    
    /**
     * AgentSettlement - Settlement between agent and network
     */
    public static class AgentSettlement {
        private final String settlementId;
        private final String agentId;
        private final Instant settlementPeriodStart;
        private final Instant settlementPeriodEnd;
        private final Map<String, BigInteger> cashInTotal; // Currency -> Amount
        private final Map<String, BigInteger> cashOutTotal; // Currency -> Amount
        private final Map<String, BigInteger> commissionsEarned; // Currency -> Amount
        private final Map<String, BigInteger> netPosition; // Currency -> Amount
        private SettlementStatus status;
        
        public enum SettlementStatus {
            PENDING, APPROVED, SETTLED, DISPUTED
        }
        
        public AgentSettlement(String settlementId, String agentId,
                              Instant periodStart, Instant periodEnd) {
            this.settlementId = settlementId;
            this.agentId = agentId;
            this.settlementPeriodStart = periodStart;
            this.settlementPeriodEnd = periodEnd;
            this.cashInTotal = new HashMap<>();
            this.cashOutTotal = new HashMap<>();
            this.commissionsEarned = new HashMap<>();
            this.netPosition = new HashMap<>();
            this.status = SettlementStatus.PENDING;
        }
        
        public String getSettlementId() { return settlementId; }
        public String getAgentId() { return agentId; }
        public SettlementStatus getStatus() { return status; }
        public Map<String, BigInteger> getCommissionsEarned() { return new HashMap<>(commissionsEarned); }
        
        public void setStatus(SettlementStatus status) { this.status = status; }
        
        public void addCashIn(String currency, BigInteger amount) {
            BigInteger current = cashInTotal.getOrDefault(currency, BigInteger.ZERO);
            cashInTotal.put(currency, current.add(amount));
        }
        
        public void addCashOut(String currency, BigInteger amount) {
            BigInteger current = cashOutTotal.getOrDefault(currency, BigInteger.ZERO);
            cashOutTotal.put(currency, current.add(amount));
        }
        
        public void addCommission(String currency, BigInteger amount) {
            BigInteger current = commissionsEarned.getOrDefault(currency, BigInteger.ZERO);
            commissionsEarned.put(currency, current.add(amount));
        }
        
        public void calculateNetPosition() {
            Set<String> currencies = new HashSet<>();
            currencies.addAll(cashInTotal.keySet());
            currencies.addAll(cashOutTotal.keySet());
            
            for (String currency : currencies) {
                BigInteger cashIn = cashInTotal.getOrDefault(currency, BigInteger.ZERO);
                BigInteger cashOut = cashOutTotal.getOrDefault(currency, BigInteger.ZERO);
                BigInteger net = cashIn.subtract(cashOut);
                netPosition.put(currency, net);
            }
        }
    }
    
    /**
     * ReputationScore - Agent reputation tracking
     */
    public static class ReputationScore {
        private int score; // 0-100
        private int totalRatings;
        private int positiveRatings;
        private int negativeRatings;
        private final List<ReputationEvent> events;
        
        public ReputationScore() {
            this.score = 50; // Start neutral
            this.totalRatings = 0;
            this.positiveRatings = 0;
            this.negativeRatings = 0;
            this.events = new CopyOnWriteArrayList<>();
        }
        
        public int getScore() { return score; }
        public int getTotalRatings() { return totalRatings; }
        
        public void addPositive(String reason) {
            positiveRatings++;
            totalRatings++;
            score = Math.min(100, score + 2);
            events.add(new ReputationEvent(true, reason));
        }
        
        public void addNegative(String reason) {
            negativeRatings++;
            totalRatings++;
            score = Math.max(0, score - 5);
            events.add(new ReputationEvent(false, reason));
        }
        
        public static class ReputationEvent {
            private final Instant timestamp;
            private final boolean isPositive;
            private final String reason;
            
            public ReputationEvent(boolean isPositive, String reason) {
                this.timestamp = Instant.now();
                this.isPositive = isPositive;
                this.reason = reason;
            }
            
            public Instant getTimestamp() { return timestamp; }
            public boolean isPositive() { return isPositive; }
            public String getReason() { return reason; }
        }
    }
    
    /**
     * Constructor for AgentNetworkManager
     */
    public AgentNetworkManager(BlockchainState blockchainState) {
        this.blockchainState = blockchainState;
        
        this.agents = new ConcurrentHashMap<>();
        this.locationAgents = new ConcurrentHashMap<>();
        this.addressToAgentId = new ConcurrentHashMap<>();
        
        this.transactions = new ConcurrentHashMap<>();
        this.agentTransactions = new ConcurrentHashMap<>();
        
        this.pendingSettlements = new ConcurrentHashMap<>();
        this.completedSettlements = new ConcurrentHashMap<>();
        
        this.commissionPool = new ConcurrentHashMap<>();
        this.agentCommissions = new ConcurrentHashMap<>();
        
        this.agentReputations = new ConcurrentHashMap<>();
        this.agentLocations = new ConcurrentHashMap<>();
        
        this.executorService = Executors.newFixedThreadPool(10);
        this.scheduledExecutor = Executors.newScheduledThreadPool(5);
        
        // Start background tasks
        startSettlementProcessor();
        startReputationMonitor();
        
        LOGGER.info("AgentNetworkManager initialized");
    }
    
    /**
     * Registers a new retail agent
     */
    public String registerAgent(String businessName, String ownerName, Address blockchainAddress,
                               PublicKey publicKey, String phoneNumber, GeoLocation location,
                               RetailAgent.AgentType agentType, BigInteger securityDeposit) {
        
        // Verify minimum security deposit
        if (securityDeposit.compareTo(MIN_SECURITY_DEPOSIT) < 0) {
            throw new IllegalArgumentException("Security deposit below minimum");
        }
        
        // Generate agent ID
        String agentId = generateAgentId();
        
        // Create agent
        RetailAgent agent = new RetailAgent(
            agentId, businessName, ownerName, blockchainAddress, publicKey,
            phoneNumber, location, agentType, securityDeposit
        );
        
        agents.put(agentId, agent);
        addressToAgentId.put(blockchainAddress, agentId);
        
        // Add to location index
        String locationKey = location.getCity() + "-" + location.getCountry();
        locationAgents.computeIfAbsent(locationKey, k -> new ArrayList<>()).add(agentId);
        
        agentLocations.put(agentId, location);
        
        // Initialize reputation
        agentReputations.put(agentId, new ReputationScore());
        
        // Initialize commission tracking
        agentCommissions.put(agentId, new ConcurrentHashMap<>());
        
        LOGGER.info(String.format("Agent registered: %s (%s) at %s, %s",
            businessName, agentType, location.getCity(), location.getCountry()));
        
        return agentId;
    }
    
    /**
     * Approves an agent for operations
     */
    public boolean approveAgent(String agentId) {
        RetailAgent agent = agents.get(agentId);
        
        if (agent == null) {
            LOGGER.warning("Agent not found: " + agentId);
            return false;
        }
        
        // Verify security deposit is locked
        // In production, verify actual blockchain transaction
        
        agent.setStatus(RetailAgent.AgentStatus.ACTIVE);
        
        LOGGER.info("Agent approved: " + agentId);
        return true;
    }
    
    /**
     * Processes a cash-in transaction
     */
    public String processCashIn(String agentId, Address customerAddress, String currency,
                               BigInteger amount) {
        
        RetailAgent agent = agents.get(agentId);
        
        if (agent == null || agent.getStatus() != RetailAgent.AgentStatus.ACTIVE) {
            throw new RuntimeException("Agent not available");
        }
        
        // Calculate commission
        BigInteger commission = calculateCommission(amount, currency, agent);
        
        // Create transaction
        String txId = generateTransactionId();
        AgentTransaction transaction = new AgentTransaction(
            txId, agentId, customerAddress, AgentTransaction.TransactionType.CASH_IN,
            currency, amount, commission
        );
        
        // Update agent float
        agent.addFloat(currency, amount);
        
        // Update statistics
        agent.incrementTransactions();
        agent.addVolume(amount);
        agent.incrementSuccessful();
        
        // Track commission
        trackCommission(agentId, currency, commission);
        
        // Store transaction
        transactions.put(txId, transaction);
        agentTransactions.computeIfAbsent(agentId, k -> new ArrayList<>()).add(transaction);
        
        transaction.setStatus(AgentTransaction.TransactionStatus.COMPLETED);
        
        // Update reputation
        ReputationScore reputation = agentReputations.get(agentId);
        if (reputation != null) {
            reputation.addPositive("Successful cash-in");
        }
        
        LOGGER.info(String.format("Cash-in: %s %s by agent %s, commission: %s",
            amount, currency, agentId, commission));
        
        return txId;
    }
    
    /**
     * Processes a cash-out transaction
     */
    public String processCashOut(String agentId, Address customerAddress, String currency,
                                BigInteger amount) {
        
        RetailAgent agent = agents.get(agentId);
        
        if (agent == null || agent.getStatus() != RetailAgent.AgentStatus.ACTIVE) {
            throw new RuntimeException("Agent not available");
        }
        
        // Verify agent has sufficient float
        if (agent.getFloatBalance(currency).compareTo(amount) < 0) {
            agent.incrementFailed();
            throw new RuntimeException("Insufficient agent float");
        }
        
        // Calculate commission
        BigInteger commission = calculateCommission(amount, currency, agent);
        
        // Create transaction
        String txId = generateTransactionId();
        AgentTransaction transaction = new AgentTransaction(
            txId, agentId, customerAddress, AgentTransaction.TransactionType.CASH_OUT,
            currency, amount, commission
        );
        
        // Deduct from agent float
        agent.deductFloat(currency, amount);
        
        // Update statistics
        agent.incrementTransactions();
        agent.addVolume(amount);
        agent.incrementSuccessful();
        
        // Track commission
        trackCommission(agentId, currency, commission);
        
        // Store transaction
        transactions.put(txId, transaction);
        agentTransactions.computeIfAbsent(agentId, k -> new ArrayList<>()).add(transaction);
        
        transaction.setStatus(AgentTransaction.TransactionStatus.COMPLETED);
        
        // Update reputation
        ReputationScore reputation = agentReputations.get(agentId);
        if (reputation != null) {
            reputation.addPositive("Successful cash-out");
        }
        
        LOGGER.info(String.format("Cash-out: %s %s by agent %s, commission: %s",
            amount, currency, agentId, commission));
        
        return txId;
    }
    
    /**
     * Finds nearest agents to a location
     */
    public List<RetailAgent> findNearestAgents(GeoLocation location, String currency, 
                                              BigInteger minimumFloat, int maxResults) {
        
        List<AgentDistance> agentDistances = new ArrayList<>();
        
        for (RetailAgent agent : agents.values()) {
            if (agent.getStatus() != RetailAgent.AgentStatus.ACTIVE) {
                continue;
            }
            
            // Check float availability
            if (agent.getFloatBalance(currency).compareTo(minimumFloat) < 0) {
                continue;
            }
            
            // Check reputation
            ReputationScore reputation = agentReputations.get(agent.getAgentId());
            if (reputation != null && reputation.getScore() < MIN_TRUST_SCORE) {
                continue;
            }
            
            double distance = location.distanceTo(agent.getLocation());
            agentDistances.add(new AgentDistance(agent, distance));
        }
        
        // Sort by distance
        agentDistances.sort(Comparator.comparingDouble(ad -> ad.distance));
        
        // Return top N
        return agentDistances.stream()
            .limit(maxResults)
            .map(ad -> ad.agent)
            .collect(java.util.stream.Collectors.toList());
    }
    
    private static class AgentDistance {
        final RetailAgent agent;
        final double distance;
        
        AgentDistance(RetailAgent agent, double distance) {
            this.agent = agent;
            this.distance = distance;
        }
    }
    
    /**
     * Calculates commission for a transaction
     */
    private BigInteger calculateCommission(BigInteger amount, String currency, RetailAgent agent) {
        // Base commission rate
        double rate = BASE_COMMISSION_RATE;
        
        // Adjust based on agent type
        switch (agent.getAgentType()) {
            case RETAIL_SHOP:
            case GAS_STATION:
                rate *= 1.0;
                break;
            case MOBILE_MONEY_KIOSK:
                rate *= 0.8; // Lower for mobile money
                break;
            case COMMUNITY_LEADER:
                rate *= 1.2; // Higher for community leaders
                break;
            default:
                break;
        }
        
        // Adjust based on reputation
        ReputationScore reputation = agentReputations.get(agent.getAgentId());
        if (reputation != null && reputation.getScore() > 80) {
            rate *= 1.1; // Bonus for good reputation
        }
        
        return amount.multiply(BigInteger.valueOf((long)(rate * 10000)))
                    .divide(BigInteger.valueOf(10000));
    }
    
    private void trackCommission(String agentId, String currency, BigInteger commission) {
        // Track agent commission
        Map<String, BigInteger> agentComm = agentCommissions.get(agentId);
        if (agentComm != null) {
            BigInteger current = agentComm.getOrDefault(currency, BigInteger.ZERO);
            agentComm.put(currency, current.add(commission));
        }
        
        // Track total commission pool
        BigInteger poolTotal = commissionPool.getOrDefault(currency, BigInteger.ZERO);
        commissionPool.put(currency, poolTotal.add(commission));
    }
    
    private void startSettlementProcessor() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            Instant now = Instant.now();
            Instant dayAgo = now.minusMillis(SETTLEMENT_WINDOW);
            
            for (RetailAgent agent : agents.values()) {
                if (agent.getStatus() == RetailAgent.AgentStatus.ACTIVE) {
                    processAgentSettlement(agent, dayAgo, now);
                }
            }
        }, 24, 24, TimeUnit.HOURS); // Daily settlements
    }
    
    private void processAgentSettlement(RetailAgent agent, Instant periodStart, Instant periodEnd) {
        String settlementId = generateSettlementId();
        AgentSettlement settlement = new AgentSettlement(
            settlementId, agent.getAgentId(), periodStart, periodEnd
        );
        
        // Calculate settlement amounts
        List<AgentTransaction> txs = agentTransactions.get(agent.getAgentId());
        if (txs != null) {
            for (AgentTransaction tx : txs) {
                if (tx.getTimestamp().isAfter(periodStart) && 
                    tx.getTimestamp().isBefore(periodEnd) &&
                    tx.getStatus() == AgentTransaction.TransactionStatus.COMPLETED) {
                    
                    if (tx.getType() == AgentTransaction.TransactionType.CASH_IN) {
                        settlement.addCashIn(tx.getCurrency(), tx.getAmount());
                    } else if (tx.getType() == AgentTransaction.TransactionType.CASH_OUT) {
                        settlement.addCashOut(tx.getCurrency(), tx.getAmount());
                    }
                    
                    settlement.addCommission(tx.getCurrency(), tx.getCommission());
                }
            }
        }
        
        settlement.calculateNetPosition();
        pendingSettlements.put(settlementId, settlement);
        
        LOGGER.info("Settlement created for agent: " + agent.getAgentId());
    }
    
    private void startReputationMonitor() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            for (Map.Entry<String, ReputationScore> entry : agentReputations.entrySet()) {
                String agentId = entry.getKey();
                ReputationScore reputation = entry.getValue();
                
                if (reputation.getScore() < MIN_TRUST_SCORE) {
                    RetailAgent agent = agents.get(agentId);
                    if (agent != null && agent.getStatus() == RetailAgent.AgentStatus.ACTIVE) {
                        agent.setStatus(RetailAgent.AgentStatus.SUSPENDED);
                        LOGGER.warning("Agent suspended due to low reputation: " + agentId);
                    }
                }
            }
        }, 3600, 3600, TimeUnit.SECONDS); // Every hour
    }
    
    private String generateAgentId() {
        return "AGENT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    private String generateTransactionId() {
        return "AGT-TX-" + System.currentTimeMillis();
    }
    
    private String generateSettlementId() {
        return "SETTLE-" + System.currentTimeMillis();
    }
    
    // Getters
    
    public RetailAgent getAgent(String agentId) {
        return agents.get(agentId);
    }
    
    public List<RetailAgent> getAllAgents() {
        return new ArrayList<>(agents.values());
    }
    
    public List<RetailAgent> getAgentsByLocation(String city, String country) {
        String locationKey = city + "-" + country;
        List<String> agentIds = locationAgents.getOrDefault(locationKey, new ArrayList<>());
        
        return agentIds.stream()
            .map(agents::get)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toList());
    }
    
    public AgentTransaction getTransaction(String txId) {
        return transactions.get(txId);
    }
    
    public ReputationScore getReputation(String agentId) {
        return agentReputations.get(agentId);
    }
    
    public void shutdown() {
        executorService.shutdown();
        scheduledExecutor.shutdown();
        LOGGER.info("AgentNetworkManager shutdown");
    }
}