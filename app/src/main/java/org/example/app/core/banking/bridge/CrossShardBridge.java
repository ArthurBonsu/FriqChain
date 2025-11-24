package org.example.app.core.banking.bridge;

import org.example.app.core.crypto.PKIManager;
import org.example.app.core.state.StateManager;
import org.example.app.core.state.BlockchainState;
import org.example.app.core.internal.EventLog;
import org.example.app.core.config.BlockchainConfig;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;
import org.example.app.core.block.Transaction;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * CrossShardBridge - Inter-shard communication and transaction routing
 * 
 * Enables scalability to 1.2B users by:
 * - Geographic sharding (West Africa, East Africa, Southern Africa, North Africa)
 * - Functional sharding (Retail, Wholesale, Cross-border, Settlements)
 * - Cross-shard message passing via relay chain
 * - Atomic cross-shard transactions with 2PC protocol
 * 
 * Target: 100,000+ TPS continent-wide (20 shards × 5,000 TPS each)
 */
public class CrossShardBridge {
    private static final Logger LOGGER = Logger.getLogger(CrossShardBridge.class.getName());
    
    // Shard configuration
    private static final int MAX_SHARDS = 20;
    private static final int SHARD_TPS_TARGET = 5000;
    private static final long MESSAGE_TIMEOUT_MS = 30000; // 30 seconds
    private static final int MAX_CROSS_SHARD_HOPS = 3; // Prevent routing loops
    
    // Shard types
    public enum ShardType {
        GEOGRAPHIC,     // Regional shards (West, East, South, North Africa)
        FUNCTIONAL,     // Purpose-based (Retail, Wholesale, Cross-border)
        RELAY          // Central relay chain for coordination
    }
    
    // Message types
    public enum MessageType {
        TRANSACTION,           // Cross-shard transaction
        BALANCE_QUERY,        // Account balance query
        STATE_SYNC,           // State synchronization
        FINALITY_PROOF,       // Finality confirmation
        RELAY_MESSAGE,        // Relay chain message
        EMERGENCY_HALT        // Emergency circuit breaker
    }
    
    // Transaction states for 2PC (Two-Phase Commit)
    public enum TransactionState {
        PREPARING,     // Phase 1: Preparing
        PREPARED,      // Phase 1: Ready to commit
        COMMITTING,    // Phase 2: Committing
        COMMITTED,     // Phase 2: Committed
        ABORTING,      // Abort initiated
        ABORTED        // Transaction aborted
    }
    
    /**
     * Represents a shard in the network
     */
    public static class Shard {
        private final int shardId;
        private final String shardName;
        private final ShardType type;
        private final Set<String> countries; // Countries/regions in this shard
        private final Address coordinatorAddress;
        private final boolean isActive;
        private final Date creationTime;
        
        private long transactionCount;
        private long currentTPS;
        private BigDecimal totalValue;
        private Date lastActivity;
        
        public Shard(int shardId, String shardName, ShardType type, 
                    Set<String> countries, Address coordinatorAddress) {
            this.shardId = shardId;
            this.shardName = shardName;
            this.type = type;
            this.countries = new HashSet<>(countries);
            this.coordinatorAddress = coordinatorAddress;
            this.isActive = true;
            this.creationTime = new Date();
            this.transactionCount = 0;
            this.currentTPS = 0;
            this.totalValue = BigDecimal.ZERO;
            this.lastActivity = new Date();
        }
        
        // Getters
        public int getShardId() { return shardId; }
        public String getShardName() { return shardName; }
        public ShardType getType() { return type; }
        public Set<String> getCountries() { return new HashSet<>(countries); }
        public Address getCoordinatorAddress() { return coordinatorAddress; }
        public boolean isActive() { return isActive; }
        public Date getCreationTime() { return creationTime; }
        public long getTransactionCount() { return transactionCount; }
        public long getCurrentTPS() { return currentTPS; }
        public BigDecimal getTotalValue() { return totalValue; }
        public Date getLastActivity() { return lastActivity; }
        
        public void recordTransaction(BigDecimal value) {
            this.transactionCount++;
            this.totalValue = this.totalValue.add(value);
            this.lastActivity = new Date();
        }
        
        public void updateTPS(long tps) {
            this.currentTPS = tps;
        }
        
        public boolean servesCountry(String country) {
            return countries.contains(country) || countries.contains("*"); // * = all countries
        }
    }
    
    /**
     * Represents a cross-shard message
     */
    public static class CrossShardMessage {
        private final String messageId;
        private final int sourceShardId;
        private final int targetShardId;
        private final MessageType type;
        private final byte[] payload;
        private final Date timestamp;
        private final int hopCount;
        private final byte[] signature;
        
        private boolean delivered;
        private Date deliveryTime;
        private String errorMessage;
        
        public CrossShardMessage(int sourceShardId, int targetShardId, MessageType type,
                                byte[] payload, int hopCount, byte[] signature) {
            this.messageId = UUID.randomUUID().toString();
            this.sourceShardId = sourceShardId;
            this.targetShardId = targetShardId;
            this.type = type;
            this.payload = Arrays.copyOf(payload, payload.length);
            this.timestamp = new Date();
            this.hopCount = hopCount;
            this.signature = Arrays.copyOf(signature, signature.length);
            this.delivered = false;
        }
        
        // Getters
        public String getMessageId() { return messageId; }
        public int getSourceShardId() { return sourceShardId; }
        public int getTargetShardId() { return targetShardId; }
        public MessageType getType() { return type; }
        public byte[] getPayload() { return Arrays.copyOf(payload, payload.length); }
        public Date getTimestamp() { return timestamp; }
        public int getHopCount() { return hopCount; }
        public byte[] getSignature() { return Arrays.copyOf(signature, signature.length); }
        public boolean isDelivered() { return delivered; }
        public Date getDeliveryTime() { return deliveryTime; }
        public String getErrorMessage() { return errorMessage; }
        
        public void markDelivered() {
            this.delivered = true;
            this.deliveryTime = new Date();
        }
        
        public void setError(String error) {
            this.errorMessage = error;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp.getTime() > MESSAGE_TIMEOUT_MS;
        }
        
        public boolean isRoutingLoopRisk() {
            return hopCount >= MAX_CROSS_SHARD_HOPS;
        }
    }
    
    /**
     * Represents a cross-shard transaction using 2PC
     */
    public static class CrossShardTransaction {
        private final String transactionId;
        private final int coordinatorShardId;
        private final List<Integer> participantShardIds;
        private final Transaction transaction;
        private final Date initiationTime;
        
        private TransactionState state;
        private final Map<Integer, TransactionState> participantStates;
        private Date commitTime;
        private String abortReason;
        
        public CrossShardTransaction(String transactionId, int coordinatorShardId,
                                    List<Integer> participantShardIds, Transaction transaction) {
            this.transactionId = transactionId;
            this.coordinatorShardId = coordinatorShardId;
            this.participantShardIds = new ArrayList<>(participantShardIds);
            this.transaction = transaction;
            this.initiationTime = new Date();
            this.state = TransactionState.PREPARING;
            this.participantStates = new ConcurrentHashMap<>();
            
            // Initialize participant states
            for (Integer shardId : participantShardIds) {
                participantStates.put(shardId, TransactionState.PREPARING);
            }
        }
        
        // Getters
        public String getTransactionId() { return transactionId; }
        public int getCoordinatorShardId() { return coordinatorShardId; }
        public List<Integer> getParticipantShardIds() { return new ArrayList<>(participantShardIds); }
        public Transaction getTransaction() { return transaction; }
        public Date getInitiationTime() { return initiationTime; }
        public TransactionState getState() { return state; }
        public Map<Integer, TransactionState> getParticipantStates() { return new HashMap<>(participantStates); }
        public Date getCommitTime() { return commitTime; }
        public String getAbortReason() { return abortReason; }
        
        public void setState(TransactionState state) { this.state = state; }
        
        public void setParticipantState(int shardId, TransactionState state) {
            participantStates.put(shardId, state);
        }
        
        public void setCommitTime(Date time) { this.commitTime = time; }
        
        public void abort(String reason) {
            this.state = TransactionState.ABORTED;
            this.abortReason = reason;
        }
        
        public boolean allParticipantsPrepared() {
            return participantStates.values().stream()
                .allMatch(s -> s == TransactionState.PREPARED);
        }
        
        public boolean isTimedOut() {
            return System.currentTimeMillis() - initiationTime.getTime() > MESSAGE_TIMEOUT_MS;
        }
    }
    
    // Core infrastructure
    private final StateManager stateManager;
    private final BlockchainState blockchainState;
    private final PKIManager pkiManager;
    private final EventLog eventLog;
    private final BlockchainConfig config;
    
    // Shard management
    private final Map<Integer, Shard> shards;
    private final Map<String, Integer> countryToShard; // country -> shard ID
    private final BlockingQueue<CrossShardMessage> messageQueue;
    private final Map<String, CrossShardTransaction> activeTransactions;
    private final ExecutorService messageExecutor;
    private final ScheduledExecutorService monitorExecutor;
    
    // Relay chain (shard 0)
    private static final int RELAY_SHARD_ID = 0;
    
    // Statistics
    private final AtomicLong totalCrossShardMessages;
    private final AtomicLong totalCrossShardTransactions;
    private final AtomicLong failedTransactions;
    
    /**
     * Constructor
     */
    public CrossShardBridge(StateManager stateManager,
                           BlockchainState blockchainState,
                           PKIManager pkiManager,
                           EventLog eventLog,
                           BlockchainConfig config) {
        this.stateManager = stateManager;
        this.blockchainState = blockchainState;
        this.pkiManager = pkiManager;
        this.eventLog = eventLog;
        this.config = config;
        
        this.shards = new ConcurrentHashMap<>();
        this.countryToShard = new ConcurrentHashMap<>();
        this.messageQueue = new LinkedBlockingQueue<>(10000);
        this.activeTransactions = new ConcurrentHashMap<>();
        this.messageExecutor = Executors.newFixedThreadPool(10);
        this.monitorExecutor = Executors.newScheduledThreadPool(2);
        
        this.totalCrossShardMessages = new AtomicLong(0);
        this.totalCrossShardTransactions = new AtomicLong(0);
        this.failedTransactions = new AtomicLong(0);
        
        initializeShards();
        startMessageProcessor();
        startMonitoring();
        
        LOGGER.info("CrossShardBridge initialized with " + shards.size() + " shards");
    }
    
    /**
     * Initializes the shard topology
     */
    private void initializeShards() {
        // Relay Chain (Shard 0)
        Shard relayChain = new Shard(RELAY_SHARD_ID, "Relay Chain", ShardType.RELAY,
                                    Set.of("*"), generateAddress("RELAY"));
        shards.put(RELAY_SHARD_ID, relayChain);
        
        // Geographic Shards
        // West Africa Shard (Shard 1)
        Shard westAfrica = new Shard(1, "West Africa", ShardType.GEOGRAPHIC,
                                    Set.of("NG", "GH", "SN", "CI", "ML", "BF"),
                                    generateAddress("WEST_AFRICA"));
        shards.put(1, westAfrica);
        mapCountriesToShard(westAfrica.getCountries(), 1);
        
        // East Africa Shard (Shard 2)
        Shard eastAfrica = new Shard(2, "East Africa", ShardType.GEOGRAPHIC,
                                    Set.of("KE", "TZ", "UG", "RW", "ET"),
                                    generateAddress("EAST_AFRICA"));
        shards.put(2, eastAfrica);
        mapCountriesToShard(eastAfrica.getCountries(), 2);
        
        // Southern Africa Shard (Shard 3)
        Shard southernAfrica = new Shard(3, "Southern Africa", ShardType.GEOGRAPHIC,
                                        Set.of("ZA", "ZW", "BW", "MZ", "NA"),
                                        generateAddress("SOUTHERN_AFRICA"));
        shards.put(3, southernAfrica);
        mapCountriesToShard(southernAfrica.getCountries(), 3);
        
        // North Africa Shard (Shard 4)
        Shard northAfrica = new Shard(4, "North Africa", ShardType.GEOGRAPHIC,
                                     Set.of("EG", "MA", "TN", "DZ", "LY"),
                                     generateAddress("NORTH_AFRICA"));
        shards.put(4, northAfrica);
        mapCountriesToShard(northAfrica.getCountries(), 4);
        
        // Functional Shards
        // Retail Shard (Shard 5)
        Shard retail = new Shard(5, "Retail Payments", ShardType.FUNCTIONAL,
                                Set.of("*"), generateAddress("RETAIL"));
        shards.put(5, retail);
        
        // Wholesale Shard (Shard 6)
        Shard wholesale = new Shard(6, "Wholesale Banking", ShardType.FUNCTIONAL,
                                   Set.of("*"), generateAddress("WHOLESALE"));
        shards.put(6, wholesale);
        
        // Cross-border Shard (Shard 7)
        Shard crossBorder = new Shard(7, "Cross-Border", ShardType.FUNCTIONAL,
                                     Set.of("*"), generateAddress("CROSSBORDER"));
        shards.put(7, crossBorder);
        
        LOGGER.info("Initialized " + shards.size() + " shards");
    }
    
    /**
     * Maps countries to their shard
     */
    private void mapCountriesToShard(Set<String> countries, int shardId) {
        for (String country : countries) {
            countryToShard.put(country, shardId);
        }
    }
    
    /**
     * Routes a transaction to the appropriate shard
     */
    public int routeTransaction(Transaction transaction, String country) {
        // Check if this is a cross-border transaction
        if (isCrossBorderTransaction(transaction)) {
            return 7; // Cross-border shard
        }
        
        // Check transaction size for retail vs wholesale
        BigDecimal transactionValue = getTransactionValue(transaction);
        if (transactionValue.compareTo(new BigDecimal("100000")) > 0) {
            return 6; // Wholesale shard
        } else {
            return 5; // Retail shard
        }
        
        // Fallback to geographic routing
        // return countryToShard.getOrDefault(country, 1);
    }
    
    /**
     * Sends a cross-shard message
     */
    public String sendCrossShardMessage(int sourceShardId, int targetShardId,
                                       MessageType type, byte[] payload) {
        try {
            // Validate shards exist
            if (!shards.containsKey(sourceShardId) || !shards.containsKey(targetShardId)) {
                LOGGER.warning("Invalid shard IDs: " + sourceShardId + " -> " + targetShardId);
                return null;
            }
            
            // Create signature
            byte[] signature = signMessage(payload);
            
            // Create message
            CrossShardMessage message = new CrossShardMessage(sourceShardId, targetShardId,
                                                             type, payload, 0, signature);
            
            // Add to queue
            boolean queued = messageQueue.offer(message, 5, TimeUnit.SECONDS);
            if (!queued) {
                LOGGER.warning("Message queue full, message dropped");
                return null;
            }
            
            totalCrossShardMessages.incrementAndGet();
            
            eventLog.logEvent("CROSS_SHARD_MESSAGE: " + sourceShardId + " -> " + targetShardId + 
                            " (Type: " + type + ")");
            
            return message.getMessageId();
            
        } catch (Exception e) {
            LOGGER.severe("Error sending cross-shard message: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Executes a cross-shard transaction using 2PC
     */
    public String executeCrossShardTransaction(Transaction transaction, 
                                              List<Integer> participantShardIds) {
        try {
            String txId = UUID.randomUUID().toString();
            
            LOGGER.info("Starting cross-shard transaction: " + txId + 
                       " (Participants: " + participantShardIds + ")");
            
            // Determine coordinator shard (usually source shard)
            int coordinatorShardId = participantShardIds.get(0);
            
            // Create cross-shard transaction
            CrossShardTransaction csTx = new CrossShardTransaction(
                txId, coordinatorShardId, participantShardIds, transaction);
            
            activeTransactions.put(txId, csTx);
            totalCrossShardTransactions.incrementAndGet();
            
            // Execute 2PC protocol asynchronously
            CompletableFuture.runAsync(() -> execute2PC(csTx), messageExecutor);
            
            return txId;
            
        } catch (Exception e) {
            LOGGER.severe("Error executing cross-shard transaction: " + e.getMessage());
            failedTransactions.incrementAndGet();
            return null;
        }
    }
    
    /**
     * Executes Two-Phase Commit protocol
     */
    private void execute2PC(CrossShardTransaction csTx) {
        try {
            // PHASE 1: PREPARE
            LOGGER.info("2PC Phase 1 (PREPARE): " + csTx.getTransactionId());
            
            boolean allPrepared = sendPrepareRequests(csTx);
            
            if (!allPrepared) {
                // Abort if any participant fails to prepare
                csTx.abort("One or more participants failed to prepare");
                sendAbortRequests(csTx);
                
                eventLog.logEvent("CROSS_SHARD_TX_ABORTED: " + csTx.getTransactionId() + 
                                " - " + csTx.getAbortReason());
                
                LOGGER.warning("Cross-shard transaction aborted: " + csTx.getTransactionId());
                failedTransactions.incrementAndGet();
                return;
            }
            
            // PHASE 2: COMMIT
            LOGGER.info("2PC Phase 2 (COMMIT): " + csTx.getTransactionId());
            
            csTx.setState(TransactionState.COMMITTING);
            boolean allCommitted = sendCommitRequests(csTx);
            
            if (allCommitted) {
                csTx.setState(TransactionState.COMMITTED);
                csTx.setCommitTime(new Date());
                
                eventLog.logEvent("CROSS_SHARD_TX_COMMITTED: " + csTx.getTransactionId());
                LOGGER.info("Cross-shard transaction committed: " + csTx.getTransactionId());
            } else {
                csTx.abort("Commit phase failed");
                failedTransactions.incrementAndGet();
                LOGGER.warning("Cross-shard transaction commit failed: " + csTx.getTransactionId());
            }
            
        } catch (Exception e) {
            LOGGER.severe("Error in 2PC protocol: " + e.getMessage());
            csTx.abort("Protocol error: " + e.getMessage());
            failedTransactions.incrementAndGet();
        } finally {
            // Cleanup after timeout
            scheduleTransactionCleanup(csTx.getTransactionId(), 60000); // 60 seconds
        }
    }
    
    /**
     * Sends prepare requests to all participants
     */
    private boolean sendPrepareRequests(CrossShardTransaction csTx) {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        
        for (Integer shardId : csTx.getParticipantShardIds()) {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Send prepare message
                    byte[] payload = createPreparePayload(csTx);
                    String messageId = sendCrossShardMessage(
                        csTx.getCoordinatorShardId(), shardId,
                        MessageType.TRANSACTION, payload);
                    
                    if (messageId != null) {
                        // Simulate prepare response (in real system, wait for actual response)
                        Thread.sleep(100);
                        csTx.setParticipantState(shardId, TransactionState.PREPARED);
                        return true;
                    }
                    return false;
                    
                } catch (Exception e) {
                    LOGGER.warning("Prepare failed for shard " + shardId + ": " + e.getMessage());
                    return false;
                }
            }, messageExecutor);
            
            futures.add(future);
        }
        
        // Wait for all prepare responses
        try {
            return futures.stream()
                .map(f -> {
                    try {
                        return f.get(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .allMatch(prepared -> prepared);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Sends commit requests to all participants
     */
    private boolean sendCommitRequests(CrossShardTransaction csTx) {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        
        for (Integer shardId : csTx.getParticipantShardIds()) {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Send commit message
                    byte[] payload = createCommitPayload(csTx);
                    String messageId = sendCrossShardMessage(
                        csTx.getCoordinatorShardId(), shardId,
                        MessageType.TRANSACTION, payload);
                    
                    if (messageId != null) {
                        // Simulate commit response
                        Thread.sleep(100);
                        csTx.setParticipantState(shardId, TransactionState.COMMITTED);
                        return true;
                    }
                    return false;
                    
                } catch (Exception e) {
                    LOGGER.warning("Commit failed for shard " + shardId + ": " + e.getMessage());
                    return false;
                }
            }, messageExecutor);
            
            futures.add(future);
        }
        
        // Wait for all commit responses
        try {
            return futures.stream()
                .map(f -> {
                    try {
                        return f.get(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .allMatch(committed -> committed);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Sends abort requests to all participants
     */
    private void sendAbortRequests(CrossShardTransaction csTx) {
        for (Integer shardId : csTx.getParticipantShardIds()) {
            try {
                byte[] payload = createAbortPayload(csTx);
                sendCrossShardMessage(csTx.getCoordinatorShardId(), shardId,
                                    MessageType.TRANSACTION, payload);
            } catch (Exception e) {
                LOGGER.warning("Error sending abort to shard " + shardId + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Starts the message processor
     */
    private void startMessageProcessor() {
        for (int i = 0; i < 5; i++) { // 5 message processing threads
            messageExecutor.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        CrossShardMessage message = messageQueue.poll(1, TimeUnit.SECONDS);
                        if (message != null) {
                            processMessage(message);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        LOGGER.warning("Error processing message: " + e.getMessage());
                    }
                }
            });
        }
        
        LOGGER.info("Message processor started with 5 threads");
    }
    
    /**
     * Processes a cross-shard message
     */
    private void processMessage(CrossShardMessage message) {
        try {
            // Check expiration
            if (message.isExpired()) {
                message.setError("Message expired");
                LOGGER.warning("Message expired: " + message.getMessageId());
                return;
            }
            
            // Check routing loops
            if (message.isRoutingLoopRisk()) {
                message.setError("Too many hops");
                LOGGER.warning("Routing loop risk detected: " + message.getMessageId());
                return;
            }
            
            // Verify signature
            if (!verifyMessageSignature(message)) {
                message.setError("Invalid signature");
                LOGGER.warning("Invalid message signature: " + message.getMessageId());
                return;
            }
            
            // Route message
            Shard targetShard = shards.get(message.getTargetShardId());
            if (targetShard == null || !targetShard.isActive()) {
                message.setError("Target shard unavailable");
                LOGGER.warning("Target shard unavailable: " + message.getTargetShardId());
                return;
            }
            
            // Process based on type
            switch (message.getType()) {
                case TRANSACTION:
                    processTransactionMessage(message, targetShard);
                    break;
                case BALANCE_QUERY:
                    processBalanceQuery(message, targetShard);
                    break;
                case STATE_SYNC:
                    processStateSync(message, targetShard);
                    break;
                default:
                    LOGGER.info("Processing message type: " + message.getType());
            }
            
            message.markDelivered();
            
        } catch (Exception e) {
            message.setError("Processing error: " + e.getMessage());
            LOGGER.severe("Error processing message: " + e.getMessage());
        }
    }
    
    /**
     * Helper methods for message processing
     */
    private void processTransactionMessage(CrossShardMessage message, Shard targetShard) {
        // Record transaction in target shard
        targetShard.recordTransaction(BigDecimal.ONE); // Simplified
        LOGGER.info("Transaction processed in shard: " + targetShard.getShardName());
    }
    
    private void processBalanceQuery(CrossShardMessage message, Shard targetShard) {
        LOGGER.info("Balance query processed for shard: " + targetShard.getShardName());
    }
    
    private void processStateSync(CrossShardMessage message, Shard targetShard) {
        LOGGER.info("State sync processed for shard: " + targetShard.getShardName());
    }
    
    /**
     * Monitoring and maintenance
     */
    private void startMonitoring() {
        // Monitor shard health every 30 seconds
        monitorExecutor.scheduleAtFixedRate(this::monitorShardHealth, 30, 30, TimeUnit.SECONDS);
        
        // Cleanup expired transactions every minute
        monitorExecutor.scheduleAtFixedRate(this::cleanupExpiredTransactions, 60, 60, TimeUnit.SECONDS);
    }
    
    private void monitorShardHealth() {
        try {
            for (Shard shard : shards.values()) {
                // Calculate TPS
                // In real implementation, this would measure actual throughput
                
                LOGGER.fine("Shard " + shard.getShardName() + 
                          " - TPS: " + shard.getCurrentTPS() + 
                          ", Transactions: " + shard.getTransactionCount());
            }
        } catch (Exception e) {
            LOGGER.warning("Error monitoring shard health: " + e.getMessage());
        }
    }
    
    private void cleanupExpiredTransactions() {
        try {
            activeTransactions.entrySet().removeIf(entry -> {
                CrossShardTransaction tx = entry.getValue();
                return tx.isTimedOut() || 
                       tx.getState() == TransactionState.COMMITTED ||
                       tx.getState() == TransactionState.ABORTED;
            });
        } catch (Exception e) {
            LOGGER.warning("Error cleaning up transactions: " + e.getMessage());
        }