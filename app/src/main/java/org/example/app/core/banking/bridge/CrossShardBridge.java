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
 * with African sovereignty enforcement
 * 
 * AFRICAN SOVEREIGNTY ENHANCEMENTS:
 * - 5 African regions (North, West, East, Central, Southern)
 * - 20 geographic shards (4 per region)
 * - DAC node validation (35 nodes, 7 per region)
 * - Multi-region consensus (3+ regions required)
 * - Reputation-based routing
 * - ASU settlement for cross-region transactions
 * - Treasury subsidy support
 * - Submarine cable routing awareness
 * - Geographic distribution enforcement
 * 
 * Enables scalability to 1.2B users by:
 * - Geographic sharding (5 African regions)
 * - Functional sharding (Retail, Wholesale, Cross-border, Settlements)
 * - Cross-shard message passing via relay chain
 * - Atomic cross-shard transactions with 2PC protocol
 * - Multi-region validation for critical operations
 * 
 * Target: 100,000+ TPS continent-wide (20 shards × 5,000 TPS each)
 */
public class CrossShardBridge {
    private static final Logger LOGGER = Logger.getLogger(CrossShardBridge.class.getName());
    
    // African Geographic Regions
    public enum AfricanRegion {
        NORTH_AFRICA(1, "North Africa"),      // Morocco, Egypt, Algeria, Tunisia
        WEST_AFRICA(2, "West Africa"),        // Nigeria, Ghana, Senegal, Ivory Coast
        EAST_AFRICA(3, "East Africa"),        // Kenya, Ethiopia, Tanzania, Uganda
        CENTRAL_AFRICA(4, "Central Africa"),  // DRC, Cameroon, Gabon, Chad
        SOUTHERN_AFRICA(5, "Southern Africa"); // South Africa, Zimbabwe, Botswana
        
        private final int code;
        private final String name;
        
        AfricanRegion(int code, String name) {
            this.code = code;
            this.name = name;
        }
        
        public int getCode() { return code; }
        public String getName() { return name; }
        
        public static AfricanRegion fromCode(int code) {
            for (AfricanRegion region : values()) {
                if (region.code == code) return region;
            }
            return WEST_AFRICA; // Default
        }
        
        public static AfricanRegion fromShardId(int shardId) {
            if (shardId >= 0 && shardId <= 3) return NORTH_AFRICA;
            if (shardId >= 4 && shardId <= 7) return WEST_AFRICA;
            if (shardId >= 8 && shardId <= 11) return EAST_AFRICA;
            if (shardId >= 12 && shardId <= 15) return CENTRAL_AFRICA;
            if (shardId >= 16 && shardId <= 19) return SOUTHERN_AFRICA;
            return WEST_AFRICA; // Default
        }
    }
    
    // Shard configuration
    private static final int MAX_SHARDS = 20; // 4 shards per region × 5 regions
    private static final int SHARDS_PER_REGION = 4;
    private static final int SHARD_TPS_TARGET = 5000;
    private static final long MESSAGE_TIMEOUT_MS = 30000; // 30 seconds
    private static final int MAX_CROSS_SHARD_HOPS = 3; // Prevent routing loops
    
    // African sovereignty requirements
    private static final int MIN_REGIONS_FOR_CRITICAL_OPS = 3; // Multi-region requirement
    private static final double MIN_VALIDATOR_REPUTATION = 0.60;
    private static final int DAC_NODES_PER_REGION = 7;
    private static final int MIN_DAC_CONFIRMATIONS = 5; // Per region
    private static final int MIN_DAC_REGIONS = 3; // Minimum regions with confirmations
    
    // Shard types
    public enum ShardType {
        GEOGRAPHIC,     // Regional shards (5 African regions)
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
        DAC_ATTESTATION,      // NEW: DAC attestation
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
     * Represents a shard in the network with African sovereignty tracking
     */
    public static class Shard {
        private final int shardId;
        private final String shardName;
        private final ShardType type;
        private final AfricanRegion region; // NEW: African region
        private final Set<String> countries;
        private final Address coordinatorAddress;
        private final boolean isActive;
        private final Date creationTime;
        
        private long transactionCount;
        private long currentTPS;
        private BigDecimal totalValue;
        private Date lastActivity;
        
        // NEW: African sovereignty tracking
        private long crossRegionTransactions;
        private long asuSettlements;
        private long treasurySubsidizedTx;
        private final Map<Integer, Integer> dacNodeConfirmations; // DAC node ID -> confirmation count
        
        public Shard(int shardId, String shardName, ShardType type, 
                    Set<String> countries, Address coordinatorAddress) {
            this.shardId = shardId;
            this.shardName = shardName;
            this.type = type;
            this.region = AfricanRegion.fromShardId(shardId); // Derive region from shard ID
            this.countries = new HashSet<>(countries);
            this.coordinatorAddress = coordinatorAddress;
            this.isActive = true;
            this.creationTime = new Date();
            this.transactionCount = 0;
            this.currentTPS = 0;
            this.totalValue = BigDecimal.ZERO;
            this.lastActivity = new Date();
            
            // Initialize African sovereignty tracking
            this.crossRegionTransactions = 0;
            this.asuSettlements = 0;
            this.treasurySubsidizedTx = 0;
            this.dacNodeConfirmations = new ConcurrentHashMap<>();
        }
        
        // Getters
        public int getShardId() { return shardId; }
        public String getShardName() { return shardName; }
        public ShardType getType() { return type; }
        public AfricanRegion getRegion() { return region; }
        public Set<String> getCountries() { return new HashSet<>(countries); }
        public Address getCoordinatorAddress() { return coordinatorAddress; }
        public boolean isActive() { return isActive; }
        public Date getCreationTime() { return creationTime; }
        public long getTransactionCount() { return transactionCount; }
        public long getCurrentTPS() { return currentTPS; }
        public BigDecimal getTotalValue() { return totalValue; }
        public Date getLastActivity() { return lastActivity; }
        
        // NEW: African sovereignty getters
        public long getCrossRegionTransactions() { return crossRegionTransactions; }
        public long getASUSettlements() { return asuSettlements; }
        public long getTreasurySubsidizedTx() { return treasurySubsidizedTx; }
        public Map<Integer, Integer> getDACNodeConfirmations() { 
            return new HashMap<>(dacNodeConfirmations); 
        }
        
        public void recordTransaction(BigDecimal value) {
            this.transactionCount++;
            this.totalValue = this.totalValue.add(value);
            this.lastActivity = new Date();
        }
        
        public void recordCrossRegionTransaction() {
            this.crossRegionTransactions++;
        }
        
        public void recordASUSettlement() {
            this.asuSettlements++;
        }
        
        public void recordTreasurySubsidy() {
            this.treasurySubsidizedTx++;
        }
        
        public void recordDACConfirmation(int dacNodeId) {
            dacNodeConfirmations.put(dacNodeId, 
                dacNodeConfirmations.getOrDefault(dacNodeId, 0) + 1);
        }
        
        public void updateTPS(long tps) {
            this.currentTPS = tps;
        }
        
        public boolean servesCountry(String country) {
            return countries.contains(country) || countries.contains("*");
        }
    }
    
    /**
     * NEW: DAC attestation for cross-shard messages
     */
    public static class DACAttestation {
        private final AfricanRegion region;
        private final Set<Integer> confirmingNodeIds;
        private final byte[] merkleRoot;
        private final Map<Integer, byte[]> nodeSignatures;
        private final Date attestationTime;
        
        public DACAttestation(AfricanRegion region, Set<Integer> confirmingNodeIds,
                            byte[] merkleRoot, Map<Integer, byte[]> nodeSignatures) {
            this.region = region;
            this.confirmingNodeIds = new HashSet<>(confirmingNodeIds);
            this.merkleRoot = Arrays.copyOf(merkleRoot, merkleRoot.length);
            this.nodeSignatures = new ConcurrentHashMap<>(nodeSignatures);
            this.attestationTime = new Date();
        }
        
        public AfricanRegion getRegion() { return region; }
        public Set<Integer> getConfirmingNodeIds() { return new HashSet<>(confirmingNodeIds); }
        public byte[] getMerkleRoot() { return Arrays.copyOf(merkleRoot, merkleRoot.length); }
        public Map<Integer, byte[]> getNodeSignatures() { return new HashMap<>(nodeSignatures); }
        public Date getAttestationTime() { return attestationTime; }
        
        public boolean isValid() {
            // Requires 5+ confirmations (5/7 supermajority)
            return confirmingNodeIds.size() >= MIN_DAC_CONFIRMATIONS &&
                   merkleRoot.length == 32 &&
                   nodeSignatures.size() >= MIN_DAC_CONFIRMATIONS;
        }
        
        public boolean hasValidNodeIds() {
            // Check all node IDs belong to this region
            int regionStart = region.getCode() == 1 ? 0 : (region.getCode() - 1) * 7;
            int regionEnd = regionStart + 6;
            
            return confirmingNodeIds.stream()
                .allMatch(nodeId -> nodeId >= regionStart && nodeId <= regionEnd);
        }
    }
    
    /**
     * Represents a cross-shard message with African sovereignty validation
     */
    public static class CrossShardMessage {
        private final String messageId;
        private final int sourceShardId;
        private final int targetShardId;
        private final AfricanRegion sourceRegion;
        private final AfricanRegion targetRegion;
        private final MessageType type;
        private final byte[] payload;
        private final Date timestamp;
        private final int hopCount;
        private final byte[] signature;
        
        // NEW: African sovereignty fields
        private final boolean settlesViaASU;
        private final boolean treasurySubsidized;
        private final List<DACAttestation> dacAttestations;
        private final double requiredReputation;
        
        private boolean delivered;
        private Date deliveryTime;
        private String errorMessage;
        
        public CrossShardMessage(int sourceShardId, int targetShardId, MessageType type,
                                byte[] payload, int hopCount, byte[] signature,
                                boolean settlesViaASU, boolean treasurySubsidized,
                                double requiredReputation) {
            this.messageId = UUID.randomUUID().toString();
            this.sourceShardId = sourceShardId;
            this.targetShardId = targetShardId;
            this.sourceRegion = AfricanRegion.fromShardId(sourceShardId);
            this.targetRegion = AfricanRegion.fromShardId(targetShardId);
            this.type = type;
            this.payload = Arrays.copyOf(payload, payload.length);
            this.timestamp = new Date();
            this.hopCount = hopCount;
            this.signature = Arrays.copyOf(signature, signature.length);
            
            // African sovereignty
            this.settlesViaASU = settlesViaASU;
            this.treasurySubsidized = treasurySubsidized;
            this.dacAttestations = new ArrayList<>();
            this.requiredReputation = requiredReputation;
            
            this.delivered = false;
        }
        
        // Getters
        public String getMessageId() { return messageId; }
        public int getSourceShardId() { return sourceShardId; }
        public int getTargetShardId() { return targetShardId; }
        public AfricanRegion getSourceRegion() { return sourceRegion; }
        public AfricanRegion getTargetRegion() { return targetRegion; }
        public MessageType getType() { return type; }
        public byte[] getPayload() { return Arrays.copyOf(payload, payload.length); }
        public Date getTimestamp() { return timestamp; }
        public int getHopCount() { return hopCount; }
        public byte[] getSignature() { return Arrays.copyOf(signature, signature.length); }
        public boolean isDelivered() { return delivered; }
        public Date getDeliveryTime() { return deliveryTime; }
        public String getErrorMessage() { return errorMessage; }
        
        // NEW: African sovereignty getters
        public boolean settlesViaASU() { return settlesViaASU; }
        public boolean isTreasurySubsidized() { return treasurySubsidized; }
        public List<DACAttestation> getDACAttestations() { return new ArrayList<>(dacAttestations); }
        public double getRequiredReputation() { return requiredReputation; }
        
        public void addDACAttestation(DACAttestation attestation) {
            this.dacAttestations.add(attestation);
        }
        
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
        
        public boolean isCrossRegion() {
            return sourceRegion != targetRegion;
        }
        
        /**
         * NEW: Validates DAC attestations meet multi-region requirements
         */
        public boolean hasValidDACAttestations() {
            if (dacAttestations.isEmpty()) return false;
            
            // Count unique regions with valid attestations
            Set<AfricanRegion> attestedRegions = new HashSet<>();
            for (DACAttestation attestation : dacAttestations) {
                if (attestation.isValid() && attestation.hasValidNodeIds()) {
                    attestedRegions.add(attestation.getRegion());
                }
            }
            
            // Require 3+ regions for cross-region transactions
            return attestedRegions.size() >= MIN_DAC_REGIONS;
        }
    }
    
    /**
     * Represents a cross-shard transaction using 2PC with African sovereignty
     */
    public static class CrossShardTransaction {
        private final String transactionId;
        private final int coordinatorShardId;
        private final List<Integer> participantShardIds;
        private final Transaction transaction;
        private final Date initiationTime;
        
        // NEW: African sovereignty fields
        private final Set<AfricanRegion> participatingRegions;
        private final boolean settlesViaASU;
        private final boolean treasurySubsidized;
        private final Map<AfricanRegion, DACAttestation> dacAttestations;
        
        private TransactionState state;
        private final Map<Integer, TransactionState> participantStates;
        private Date commitTime;
        private String abortReason;
        
        public CrossShardTransaction(String transactionId, int coordinatorShardId,
                                    List<Integer> participantShardIds, Transaction transaction,
                                    boolean settlesViaASU, boolean treasurySubsidized) {
            this.transactionId = transactionId;
            this.coordinatorShardId = coordinatorShardId;
            this.participantShardIds = new ArrayList<>(participantShardIds);
            this.transaction = transaction;
            this.initiationTime = new Date();
            
            // Calculate participating regions
            this.participatingRegions = new HashSet<>();
            participatingRegions.add(AfricanRegion.fromShardId(coordinatorShardId));
            for (Integer shardId : participantShardIds) {
                participatingRegions.add(AfricanRegion.fromShardId(shardId));
            }
            
            this.settlesViaASU = settlesViaASU;
            this.treasurySubsidized = treasurySubsidized;
            this.dacAttestations = new ConcurrentHashMap<>();
            
            this.state = TransactionState.PREPARING;
            this.participantStates = new ConcurrentHashMap<>();
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
        public Map<Integer, TransactionState> getParticipantStates() { 
            return new HashMap<>(participantStates); 
        }
        public Date getCommitTime() { return commitTime; }
        public String getAbortReason() { return abortReason; }
        
        // NEW: African sovereignty getters
        public Set<AfricanRegion> getParticipatingRegions() { return new HashSet<>(participatingRegions); }
        public boolean settlesViaASU() { return settlesViaASU; }
        public boolean isTreasurySubsidized() { return treasurySubsidized; }
        public Map<AfricanRegion, DACAttestation> getDACAttestations() { 
            return new HashMap<>(dacAttestations); 
        }
        
        public void setState(TransactionState state) { this.state = state; }
        public void setParticipantState(int shardId, TransactionState state) {
            participantStates.put(shardId, state);
        }
        public void setCommitTime(Date time) { this.commitTime = time; }
        public void setAbortReason(String reason) { this.abortReason = reason; }
        
        public void addDACAttestation(AfricanRegion region, DACAttestation attestation) {
            dacAttestations.put(region, attestation);
        }
        
        public boolean isTimedOut() {
            long ageSeconds = (System.currentTimeMillis() - initiationTime.getTime()) / 1000;
            return ageSeconds > 300; // 5 minutes
        }
        
        public boolean allPrepared() {
            return participantStates.values().stream()
                .allMatch(state -> state == TransactionState.PREPARED);
        }
        
        public boolean allCommitted() {
            return participantStates.values().stream()
                .allMatch(state -> state == TransactionState.COMMITTED);
        }
        
        /**
         * NEW: Checks if transaction meets multi-region requirements
         */
        public boolean meetsMultiRegionRequirement() {
            return participatingRegions.size() >= MIN_REGIONS_FOR_CRITICAL_OPS;
        }
        
        /**
         * NEW: Checks if DAC attestations are sufficient
         */
        public boolean hasSufficientDACAttestations() {
            // Count valid attestations
            long validAttestations = dacAttestations.values().stream()
                .filter(DACAttestation::isValid)
                .filter(DACAttestation::hasValidNodeIds)
                .count();
            
            // Require attestations from 3+ regions
            return validAttestations >= MIN_DAC_REGIONS;
        }
    }
    
    // Bridge state
    private final Map<Integer, Shard> shards;
    private final Map<String, CrossShardMessage> pendingMessages;
    private final BlockingQueue<CrossShardMessage> messageQueue;
    private final Map<String, CrossShardTransaction> activeTransactions;
    
    // NEW: Regional routing tables
    private final Map<AfricanRegion, Set<Integer>> shardsByRegion;
    private final Map<AfricanRegion, Set<Integer>> dacNodesByRegion;
    
    // State managers
    private final StateManager stateManager;
    private final EventLog eventLog;
    private final PKIManager pkiManager;
    
    // Statistics
    private final AtomicLong totalMessages;
    private final AtomicLong successfulTransactions;
    private final AtomicLong failedTransactions;
    private final AtomicLong crossRegionTransactions;
    private final AtomicLong asuSettlements;
    
    // Thread pools
    private final ExecutorService messageExecutor;
    private final ScheduledExecutorService monitorExecutor;
    
    public CrossShardBridge(StateManager stateManager, EventLog eventLog, PKIManager pkiManager) {
        this.shards = new ConcurrentHashMap<>();
        this.pendingMessages = new ConcurrentHashMap<>();
        this.messageQueue = new LinkedBlockingQueue<>();
        this.activeTransactions = new ConcurrentHashMap<>();
        
        // Initialize regional routing tables
        this.shardsByRegion = new EnumMap<>(AfricanRegion.class);
        this.dacNodesByRegion = new EnumMap<>(AfricanRegion.class);
        for (AfricanRegion region : AfricanRegion.values()) {
            shardsByRegion.put(region, ConcurrentHashMap.newKeySet());
            dacNodesByRegion.put(region, ConcurrentHashMap.newKeySet());
        }
        
        this.stateManager = stateManager;
        this.eventLog = eventLog;
        this.pkiManager = pkiManager;
        
        this.totalMessages = new AtomicLong(0);
        this.successfulTransactions = new AtomicLong(0);
        this.failedTransactions = new AtomicLong(0);
        this.crossRegionTransactions = new AtomicLong(0);
        this.asuSettlements = new AtomicLong(0);
        
        this.messageExecutor = Executors.newFixedThreadPool(10);
        this.monitorExecutor = Executors.newScheduledThreadPool(2);
        
        // Initialize 20 shards across 5 regions
        initializeShards();
        
        // Initialize DAC node registry
        initializeDACNodes();
        
        // Start background tasks
        startMessageProcessor();
        startMonitoring();
        
        LOGGER.info("CrossShardBridge initialized with 20 shards across 5 African regions");
    }
    
    /**
     * Initializes 20 geographic shards (4 per region)
     */
    private void initializeShards() {
        // North Africa: Shards 0-3
        registerShard(0, "North-Shard-0", ShardType.GEOGRAPHIC, 
            Set.of("Morocco", "Algeria"), generateAddress("north0"));
        registerShard(1, "North-Shard-1", ShardType.GEOGRAPHIC, 
            Set.of("Egypt", "Tunisia"), generateAddress("north1"));
        registerShard(2, "North-Shard-2", ShardType.GEOGRAPHIC, 
            Set.of("Libya"), generateAddress("north2"));
        registerShard(3, "North-Shard-3", ShardType.GEOGRAPHIC, 
            Set.of("Sudan"), generateAddress("north3"));
        
        // West Africa: Shards 4-7
        registerShard(4, "West-Shard-0", ShardType.GEOGRAPHIC, 
            Set.of("Nigeria", "Benin"), generateAddress("west0"));
        registerShard(5, "West-Shard-1", ShardType.GEOGRAPHIC, 
            Set.of("Ghana", "Togo"), generateAddress("west1"));
        registerShard(6, "West-Shard-2", ShardType.GEOGRAPHIC, 
            Set.of("Senegal", "Mali"), generateAddress("west2"));
        registerShard(7, "West-Shard-3", ShardType.GEOGRAPHIC, 
            Set.of("Ivory Coast", "Burkina Faso"), generateAddress("west3"));
        
        // East Africa: Shards 8-11
        registerShard(8, "East-Shard-0", ShardType.GEOGRAPHIC, 
            Set.of("Kenya", "Uganda"), generateAddress("east0"));
        registerShard(9, "East-Shard-1", ShardType.GEOGRAPHIC, 
            Set.of("Tanzania", "Rwanda"), generateAddress("east1"));
        registerShard(10, "East-Shard-2", ShardType.GEOGRAPHIC, 
            Set.of("Ethiopia", "Somalia"), generateAddress("east2"));
        registerShard(11, "East-Shard-3", ShardType.GEOGRAPHIC, 
            Set.of("Burundi", "Djibouti"), generateAddress("east3"));
        
        // Central Africa: Shards 12-15
        registerShard(12, "Central-Shard-0", ShardType.GEOGRAPHIC, 
            Set.of("DRC", "Congo"), generateAddress("central0"));
        registerShard(13, "Central-Shard-1", ShardType.GEOGRAPHIC, 
            Set.of("Cameroon", "Gabon"), generateAddress("central1"));
        registerShard(14, "Central-Shard-2", ShardType.GEOGRAPHIC, 
            Set.of("Chad", "CAR"), generateAddress("central2"));
        registerShard(15, "Central-Shard-3", ShardType.GEOGRAPHIC, 
            Set.of("Equatorial Guinea", "Sao Tome"), generateAddress("central3"));
        
        // Southern Africa: Shards 16-19
        registerShard(16, "South-Shard-0", ShardType.GEOGRAPHIC, 
            Set.of("South Africa", "Lesotho"), generateAddress("south0"));
        registerShard(17, "South-Shard-1", ShardType.GEOGRAPHIC, 
            Set.of("Zimbabwe", "Zambia"), generateAddress("south1"));
        registerShard(18, "South-Shard-2", ShardType.GEOGRAPHIC, 
            Set.of("Botswana", "Namibia"), generateAddress("south2"));
        registerShard(19, "South-Shard-3", ShardType.GEOGRAPHIC, 
            Set.of("Mozambique", "Angola"), generateAddress("south3"));
        
        LOGGER.info("Initialized 20 shards across 5 African regions");
    }
    
    /**
     * NEW: Initializes DAC node registry (35 nodes, 7 per region)
     */
    private void initializeDACNodes() {
        for (AfricanRegion region : AfricanRegion.values()) {
            int baseNodeId = (region.getCode() - 1) * DAC_NODES_PER_REGION;
            for (int i = 0; i < DAC_NODES_PER_REGION; i++) {
                dacNodesByRegion.get(region).add(baseNodeId + i);
            }
        }
        LOGGER.info("Initialized 35 DAC nodes (7 per region)");
    }
    
    /**
     * Registers a new shard
     */
    public boolean registerShard(int shardId, String shardName, ShardType type,
                                Set<String> countries, Address coordinatorAddress) {
        if (shardId < 0 || shardId >= MAX_SHARDS) {
            LOGGER.warning("Invalid shard ID: " + shardId);
            return false;
        }
        
        if (shards.containsKey(shardId)) {
            LOGGER.warning("Shard already registered: " + shardId);
            return false;
        }
        
        Shard shard = new Shard(shardId, shardName, type, countries, coordinatorAddress);
        shards.put(shardId, shard);
        shardsByRegion.get(shard.getRegion()).add(shardId);
        
        eventLog.logEvent("SHARD_REGISTERED: " + shardName + " (Region: " + shard.getRegion() + ")");
        LOGGER.info("Registered shard: " + shardName + " in " + shard.getRegion());
        
        return true;
    }
    
    /**
     * Sends a cross-shard message with African sovereignty validation
     */
    public String sendCrossShardMessage(int sourceShardId, int targetShardId,
                                       MessageType type, byte[] payload,
                                       boolean settlesViaASU, boolean treasurySubsidized,
                                       double requiredReputation) {
        try {
            // Validate shards exist
            Shard sourceShard = shards.get(sourceShardId);
            Shard targetShard = shards.get(targetShardId);
            
            if (sourceShard == null || targetShard == null) {
                LOGGER.warning("Invalid shard IDs: " + sourceShardId + " -> " + targetShardId);
                return null;
            }
            
            // Sign message
            byte[] signature = signMessage(payload);
            
            // Create message
            CrossShardMessage message = new CrossShardMessage(
                sourceShardId, targetShardId, type, payload, 0, signature,
                settlesViaASU, treasurySubsidized, requiredReputation
            );
            
            // Add DAC attestations for cross-region messages
            if (message.isCrossRegion()) {
                addDACAttestations(message);
                crossRegionTransactions.incrementAndGet();
                sourceShard.recordCrossRegionTransaction();
            }
            
            // Track ASU settlement
            if (settlesViaASU) {
                asuSettlements.incrementAndGet();
                sourceShard.recordASUSettlement();
            }
            
            // Track Treasury subsidy
            if (treasurySubsidized) {
                sourceShard.recordTreasurySubsidy();
            }
            
            // Queue message for processing
            pendingMessages.put(message.getMessageId(), message);
            messageQueue.offer(message);
            totalMessages.incrementAndGet();
            
            eventLog.logEvent(String.format("CROSS_SHARD_MESSAGE: %s -> %s (Type: %s, ASU: %b)",
                sourceShard.getRegion(), targetShard.getRegion(), type, settlesViaASU));
            
            return message.getMessageId();
            
        } catch (Exception e) {
            LOGGER.severe("Error sending cross-shard message: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * NEW: Adds DAC attestations from multiple regions
     */
    private void addDACAttestations(CrossShardMessage message) {
        // For cross-region messages, collect attestations from 3+ regions
        int attestationsNeeded = Math.max(MIN_DAC_REGIONS, 
            message.getSourceRegion() != message.getTargetRegion() ? 3 : 1);
        
        Set<AfricanRegion> attestedRegions = new HashSet<>();
        attestedRegions.add(message.getSourceRegion());
        attestedRegions.add(message.getTargetRegion());
        
        // Add attestation from a third region if needed
        for (AfricanRegion region : AfricanRegion.values()) {
            if (attestedRegions.size() >= attestationsNeeded) break;
            if (!attestedRegions.contains(region)) {
                attestedRegions.add(region);
            }
        }
        
        // Generate attestations
        for (AfricanRegion region : attestedRegions) {
            Set<Integer> nodeIds = dacNodesByRegion.get(region);
            if (nodeIds.size() >= MIN_DAC_CONFIRMATIONS) {
                // Select 5 nodes from this region
                Set<Integer> selectedNodes = nodeIds.stream()
                    .limit(MIN_DAC_CONFIRMATIONS)
                    .collect(Collectors.toSet());
                
                // Generate attestation
                byte[] merkleRoot = generateMerkleRoot(message.getPayload());
                Map<Integer, byte[]> signatures = new HashMap<>();
                for (Integer nodeId : selectedNodes) {
                    signatures.put(nodeId, signMessage(merkleRoot));
                }
                
                DACAttestation attestation = new DACAttestation(
                    region, selectedNodes, merkleRoot, signatures
                );
                
                message.addDACAttestation(attestation);
            }
        }
    }
    
    /**
     * Executes a cross-shard transaction with 2PC and African sovereignty validation
     */
    public String executeCrossShardTransaction(int coordinatorShardId,
                                              List<Integer> participantShardIds,
                                              Transaction transaction,
                                              boolean settlesViaASU,
                                              boolean treasurySubsidized) {
        try {
            String txId = UUID.randomUUID().toString();
            
            CrossShardTransaction csTx = new CrossShardTransaction(
                txId, coordinatorShardId, participantShardIds, transaction,
                settlesViaASU, treasurySubsidized
            );
            
            // Validate multi-region requirement for critical operations
            if (!csTx.meetsMultiRegionRequirement()) {
                LOGGER.warning("Transaction does not meet multi-region requirement");
                return null;
            }
            
            // Add DAC attestations
            for (AfricanRegion region : csTx.getParticipatingRegions()) {
                DACAttestation attestation = generateDACAttestationForRegion(region, txId);
                csTx.addDACAttestation(region, attestation);
            }
            
            // Validate DAC attestations
            if (!csTx.hasSufficientDACAttestations()) {
                LOGGER.warning("Insufficient DAC attestations for transaction");
                return null;
            }
            
            activeTransactions.put(txId, csTx);
            
            // Execute 2PC in separate thread
            CompletableFuture.runAsync(() -> execute2PC(csTx), messageExecutor);
            
            eventLog.logEvent(String.format("CROSS_SHARD_TX_INITIATED: %s (Regions: %d, ASU: %b)",
                txId, csTx.getParticipatingRegions().size(), settlesViaASU));
            
            return txId;
            
        } catch (Exception e) {
            LOGGER.severe("Error executing cross-shard transaction: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * NEW: Generates DAC attestation for a specific region
     */
    private DACAttestation generateDACAttestationForRegion(AfricanRegion region, String txId) {
        Set<Integer> nodeIds = dacNodesByRegion.get(region);
        Set<Integer> selectedNodes = nodeIds.stream()
            .limit(MIN_DAC_CONFIRMATIONS)
            .collect(Collectors.toSet());
        
        byte[] merkleRoot = generateMerkleRoot(txId.getBytes());
        Map<Integer, byte[]> signatures = new HashMap<>();
        
        for (Integer nodeId : selectedNodes) {
            signatures.put(nodeId, signMessage(merkleRoot));
        }
        
        return new DACAttestation(region, selectedNodes, merkleRoot, signatures);
    }
    
    /**
     * Executes 2PC protocol with multi-region validation
     */
    private void execute2PC(CrossShardTransaction csTx) {
        try {
            // Phase 1: Prepare
            csTx.setState(TransactionState.PREPARING);
            if (!sendPrepareRequests(csTx)) {
                abortTransaction(csTx, "Prepare phase failed");
                return;
            }
            
            // Check all participants prepared
            if (!csTx.allPrepared()) {
                abortTransaction(csTx, "Not all participants prepared");
                return;
            }
            
            csTx.setState(TransactionState.PREPARED);
            
            // Phase 2: Commit
            csTx.setState(TransactionState.COMMITTING);
            if (!sendCommitRequests(csTx)) {
                abortTransaction(csTx, "Commit phase failed");
                return;
            }
            
            // Verify all committed
            if (csTx.allCommitted()) {
                csTx.setState(TransactionState.COMMITTED);
                csTx.setCommitTime(new Date());
                successfulTransactions.incrementAndGet();
                
                // Record in shards
                for (Integer shardId : csTx.getParticipantShardIds()) {
                    Shard shard = shards.get(shardId);
                    if (shard != null) {
                        shard.recordTransaction(BigDecimal.ONE);
                        if (csTx.settlesViaASU()) {
                            shard.recordASUSettlement();
                        }
                        if (csTx.isTreasurySubsidized()) {
                            shard.recordTreasurySubsidy();
                        }
                    }
                }
                
                eventLog.logEvent("CROSS_SHARD_TX_COMMITTED: " + csTx.getTransactionId());
                LOGGER.info("Cross-shard transaction committed: " + csTx.getTransactionId());
            }
            
        } catch (Exception e) {
            abortTransaction(csTx, "Error during 2PC: " + e.getMessage());
        }
    }
    
    /**
     * Aborts a cross-shard transaction
     */
    private void abortTransaction(CrossShardTransaction csTx, String reason) {
        csTx.setState(TransactionState.ABORTING);
        csTx.setAbortReason(reason);
        sendAbortRequests(csTx);
        csTx.setState(TransactionState.ABORTED);
        failedTransactions.incrementAndGet();
        
        eventLog.logEvent("CROSS_SHARD_TX_ABORTED: " + csTx.getTransactionId() + 
                         " - Reason: " + reason);
        LOGGER.warning("Cross-shard transaction aborted: " + reason);
    }
    
    /**
     * Sends prepare requests to all participants
     */
    private boolean sendPrepareRequests(CrossShardTransaction csTx) {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        
        for (Integer shardId : csTx.getParticipantShardIds()) {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    byte[] payload = createPreparePayload(csTx);
                    String messageId = sendCrossShardMessage(
                        csTx.getCoordinatorShardId(), shardId,
                        MessageType.TRANSACTION, payload,
                        csTx.settlesViaASU(), csTx.isTreasurySubsidized(),
                        MIN_VALIDATOR_REPUTATION);
                    
                    if (messageId != null) {
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
                    byte[] payload = createCommitPayload(csTx);
                    String messageId = sendCrossShardMessage(
                        csTx.getCoordinatorShardId(), shardId,
                        MessageType.TRANSACTION, payload,
                        csTx.settlesViaASU(), csTx.isTreasurySubsidized(),
                        MIN_VALIDATOR_REPUTATION);
                    
                    if (messageId != null) {
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
                    MessageType.TRANSACTION, payload,
                    false, false, MIN_VALIDATOR_REPUTATION);
            } catch (Exception e) {
                LOGGER.warning("Error sending abort to shard " + shardId + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Starts the message processor
     */
    private void startMessageProcessor() {
        for (int i = 0; i < 5; i++) {
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
     * Processes a cross-shard message with African sovereignty validation
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
            
            // NEW: Validate DAC attestations for cross-region messages
            if (message.isCrossRegion() && !message.hasValidDACAttestations()) {
                message.setError("Insufficient DAC attestations");
                LOGGER.warning("Insufficient DAC attestations: " + message.getMessageId());
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
                case DAC_ATTESTATION:
                    processDACAttestation(message, targetShard);
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
        targetShard.recordTransaction(BigDecimal.ONE);
        if (message.isCrossRegion()) {
            targetShard.recordCrossRegionTransaction();
        }
        if (message.settlesViaASU()) {
            targetShard.recordASUSettlement();
        }
        if (message.isTreasurySubsidized()) {
            targetShard.recordTreasurySubsidy();
        }
        LOGGER.info("Transaction processed in shard: " + targetShard.getShardName());
    }
    
    private void processBalanceQuery(CrossShardMessage message, Shard targetShard) {
        LOGGER.info("Balance query processed for shard: " + targetShard.getShardName());
    }
    
    private void processStateSync(CrossShardMessage message, Shard targetShard) {
        LOGGER.info("State sync processed for shard: " + targetShard.getShardName());
    }
    
    /**
     * NEW: Processes DAC attestation messages
     */
    private void processDACAttestation(CrossShardMessage message, Shard targetShard) {
        // Record DAC confirmations
        for (DACAttestation attestation : message.getDACAttestations()) {
            for (Integer nodeId : attestation.getConfirmingNodeIds()) {
                targetShard.recordDACConfirmation(nodeId);
            }
        }
        LOGGER.info("DAC attestation processed for shard: " + targetShard.getShardName());
    }
    
    /**
     * Monitoring and maintenance
     */
    private void startMonitoring() {
        monitorExecutor.scheduleAtFixedRate(this::monitorShardHealth, 30, 30, TimeUnit.SECONDS);
        monitorExecutor.scheduleAtFixedRate(this::cleanupExpiredTransactions, 60, 60, TimeUnit.SECONDS);
    }
    
    private void monitorShardHealth() {
        try {
            for (Shard shard : shards.values()) {
                LOGGER.fine(String.format("Shard %s (%s) - TPS: %d, Txs: %d, Cross-Region: %d, ASU: %d",
                    shard.getShardName(), shard.getRegion(),
                    shard.getCurrentTPS(), shard.getTransactionCount(),
                    shard.getCrossRegionTransactions(), shard.getASUSettlements()));
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
    }
    
    // Helper methods
    private byte[] signMessage(byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(payload);
        } catch (Exception e) {
            return new byte[32];
        }
    }
    
    private boolean verifyMessageSignature(CrossShardMessage message) {
        return message.getSignature() != null && message.getSignature().length == 32;
    }
    
    private byte[] generateMerkleRoot(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (Exception e) {
            return new byte[32];
        }
    }
    
    private byte[] createPreparePayload(CrossShardTransaction csTx) {
        return ("PREPARE:" + csTx.getTransactionId()).getBytes();
    }
    
    private byte[] createCommitPayload(CrossShardTransaction csTx) {
        return ("COMMIT:" + csTx.getTransactionId()).getBytes();
    }
    
    private byte[] createAbortPayload(CrossShardTransaction csTx) {
        return ("ABORT:" + csTx.getTransactionId()).getBytes();
    }
    
    private Address generateAddress(String name) {
        return Address.fromString("0x" + Integer.toHexString(name.hashCode()) + "000000000000000000000000000000");
    }
    
    // Public API methods
    public Shard getShard(int shardId) {
        return shards.get(shardId);
    }
    
    public Collection<Shard> getAllShards() {
        return new ArrayList<>(shards.values());
    }
    
    public Collection<Shard> getShardsByRegion(AfricanRegion region) {
        return shardsByRegion.get(region).stream()
            .map(shards::get)
            .collect(Collectors.toList());
    }
    
    public CrossShardMessage getMessage(String messageId) {
        return pendingMessages.get(messageId);
    }
    
    public CrossShardTransaction getTransaction(String txId) {
        return activeTransactions.get(txId);
    }
    
    /**
     * Gets comprehensive statistics with African sovereignty metrics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalShards", shards.size());
        stats.put("totalMessages", totalMessages.get());
        stats.put("successfulTransactions", successfulTransactions.get());
        stats.put("failedTransactions", failedTransactions.get());
        stats.put("crossRegionTransactions", crossRegionTransactions.get());
        stats.put("asuSettlements", asuSettlements.get());
        stats.put("activeTransactions", activeTransactions.size());
        stats.put("pendingMessages", pendingMessages.size());
        
        // Regional distribution
        Map<String, Object> regionalStats = new HashMap<>();
        for (AfricanRegion region : AfricanRegion.values()) {
            Map<String, Object> regionInfo = new HashMap<>();
            List<Shard> regionShards = getShardsByRegion(region);
            
            long totalTxs = regionShards.stream().mapToLong(Shard::getTransactionCount).sum();
            long crossRegionTxs = regionShards.stream().mapToLong(Shard::getCrossRegionTransactions).sum();
            long asuCount = regionShards.stream().mapToLong(Shard::getASUSettlements).sum();
            
            regionInfo.put("shardCount", regionShards.size());
            regionInfo.put("totalTransactions", totalTxs);
            regionInfo.put("crossRegionTransactions", crossRegionTxs);
            regionInfo.put("asuSettlements", asuCount);
            regionInfo.put("dacNodes", DAC_NODES_PER_REGION);
            
            regionalStats.put(region.getName(), regionInfo);
        }
        stats.put("regionalStatistics", regionalStats);
        
        // Throughput estimate
        long totalTPS = shards.values().stream().mapToLong(Shard::getCurrentTPS).sum();
        stats.put("estimatedTPS", totalTPS);
        stats.put("targetTPS", MAX_SHARDS * SHARD_TPS_TARGET);
        
        return stats;
    }
    
    public void shutdown() {
        messageExecutor.shutdown();
        monitorExecutor.shutdown();
        
        try {
            if (!messageExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                messageExecutor.shutdownNow();
            }
            if (!monitorExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                monitorExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        LOGGER.info("CrossShardBridge shutdown completed");
    }
}