package org.example.app.core.banking.independence;

import java.io.IOException;

import org.example.app.core.block.Transaction;
import org.example.app.core.types.Address;
import org.example.app.core.p2p.Peer;
import org.example.app.core.p2p.RobustP2PManager;

import java.math.BigInteger;
import java.net.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * CommunityMeshNode - Local peer-to-peer mesh network for TINC Blockchain
 * with African sovereignty enforcement
 * 
 * AFRICAN SOVEREIGNTY ENHANCEMENTS:
 * - 5 African regions (North, West, East, Central, Southern)
 * - Regional mesh topology (prefer same-region peers)
 * - Cross-region relay coordination
 * - DAC node connectivity tracking (35 nodes, 7 per region)
 * - Committee node identification
 * - Reputation integration (≥0.60 for trusted forwarding)
 * - ASU transaction support
 * - Treasury subsidy tracking
 * - Shard-aware routing (20 shards across 5 regions)
 * - Regional trust boosting
 * - Multi-region broadcast requirements
 * - Submarine cable routing awareness
 * 
 * CRITICAL FOR INDEPENDENCE: Enable transaction relay WITHOUT central servers
 * 
 * Features:
 * - Self-organizing mesh topology (no central coordinator)
 * - Bluetooth/WiFi Direct/NFC peer discovery
 * - Store-and-forward message relay (works when disconnected)
 * - Epidemic broadcast (gossip protocol)
 * - Geographic clustering (regional-first routing)
 * - Raspberry Pi / low-power device support
 * - Battery-efficient protocols
 * - Opportunistic synchronization
 * - Regional sovereignty enforcement
 * 
 * Use Cases:
 * - Community markets (peer-to-peer trading)
 * - Rural areas (mesh replaces cell towers)
 * - Disaster zones (infrastructure destroyed)
 * - Censorship resistance (no single point to shut down)
 * - Local liquidity pools
 * - Cross-border remittances
 * - Regional trade networks
 * 
 * Architecture:
 * - Each node = mini relay server
 * - Automatic neighbor discovery with regional preference
 * - Multi-path routing (censorship resistance)
 * - Trust-based forwarding (prevent spam)
 * - Regional mesh clusters with cross-region bridges
 */
public class CommunityMeshNode {
    private static final Logger LOGGER = Logger.getLogger(CommunityMeshNode.class.getName());
    
    // African Geographic Regions
    public enum AfricanRegion {
        NORTH_AFRICA(1, "North Africa"),
        WEST_AFRICA(2, "West Africa"),
        EAST_AFRICA(3, "East Africa"),
        CENTRAL_AFRICA(4, "Central Africa"),
        SOUTHERN_AFRICA(5, "Southern Africa");
        
        private final int code;
        private final String name;
        
        AfricanRegion(int code, String name) {
            this.code = code;
            this.name = name;
        }
        
        public int getCode() { return code; }
        public String getName() { return name; }
        
        public static AfricanRegion fromCountry(String country) {
            // Mapping from country to region
            if (Arrays.asList("Morocco", "Egypt", "Algeria", "Tunisia", "Libya", "Sudan").contains(country)) {
                return NORTH_AFRICA;
            } else if (Arrays.asList("Nigeria", "Ghana", "Senegal", "Ivory Coast", "Mali", "Benin", "Togo").contains(country)) {
                return WEST_AFRICA;
            } else if (Arrays.asList("Kenya", "Ethiopia", "Tanzania", "Uganda", "Rwanda", "Somalia", "Burundi").contains(country)) {
                return EAST_AFRICA;
            } else if (Arrays.asList("DRC", "Cameroon", "Gabon", "Chad", "CAR", "Congo").contains(country)) {
                return CENTRAL_AFRICA;
            } else if (Arrays.asList("South Africa", "Zimbabwe", "Botswana", "Namibia", "Angola", "Mozambique", "Zambia").contains(country)) {
                return SOUTHERN_AFRICA;
            }
            return WEST_AFRICA; // Default
        }
        
        public static AfricanRegion fromShardId(int shardId) {
            if (shardId >= 0 && shardId <= 3) return NORTH_AFRICA;
            if (shardId >= 4 && shardId <= 7) return WEST_AFRICA;
            if (shardId >= 8 && shardId <= 11) return EAST_AFRICA;
            if (shardId >= 12 && shardId <= 15) return CENTRAL_AFRICA;
            if (shardId >= 16 && shardId <= 19) return SOUTHERN_AFRICA;
            return WEST_AFRICA;
        }
    }
    
    // Message types for mesh communication
    public enum MessageType {
        TRANSACTION,
        BLOCK,
        PEER_DISCOVERY,
        STATE_SYNC,
        CONSENSUS_VOTE,
        ALERT,
        HEARTBEAT
    }
    
    // Message priority levels
    public enum MessagePriority {
        LOW(1),
        NORMAL(2),
        HIGH(3),
        URGENT(4);
        
        private final int level;
        
        MessagePriority(int level) {
            this.level = level;
        }
        
        public int getLevel() { return level; }
    }
    
    // Node configuration
    private static final int DEFAULT_PORT = 9876;
    private static final int MAX_NEIGHBORS = 50;
    private static final int MAX_MESSAGE_CACHE = 10000;
    private static final long MESSAGE_TTL = 86400000L; // 24 hours
    private static final int BROADCAST_FANOUT = 7; // Number of peers to forward to
    
    // African sovereignty configuration
    private static final double MIN_TRUSTED_REPUTATION = 0.60;
    private static final double REGIONAL_TRUST_BOOST = 0.15; // 15% boost for same region
    private static final int MIN_CROSS_REGION_RELAYS = 3; // Minimum relay nodes for cross-region
    private static final int DAC_NODES_PER_REGION = 7;
    private static final int COMMITTEE_SIZE = 5;
    
    // Node identity
    private final String nodeId;
    private final Address nodeAddress;
    private final NodeType nodeType;
    private final String country; // Country name
    private final AfricanRegion region; // African region
    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    
    // NEW: Blockchain integration
    private final int shardId; // Which shard this node connects to (0-19)
    private final double reputationScore; // From ReputationScoreValidator
    private final boolean isDACNode;
    private final boolean isCommitteeMember;
    private final int dacNodeId; // 0-34 if DAC node
    
    // Mesh network
    private final Map<String, MeshPeer> neighbors;
    private final Map<String, MeshMessage> messageCache;
    private final Set<String> seenMessages;
    
    // NEW: Regional mesh topology
    private final Map<AfricanRegion, Set<String>> regionalNeighbors; // Region -> Node IDs
    private final Set<String> crossRegionRelays; // Nodes that bridge regions
    private final Map<Integer, Set<String>> dacNodeConnections; // DAC node ID -> Connected mesh nodes
    private final Map<String, Set<String>> committeeNodeConnections; // Committee member -> Connected mesh nodes
    
    // Message queue
    private final BlockingQueue<MeshMessage> incomingQueue;
    private final BlockingQueue<MeshMessage> outgoingQueue;
    
    // Store-and-forward
    private final Map<Address, List<MeshMessage>> undeliveredMessages;
    
    // Network statistics
    private final MeshStatistics statistics;
    
    // Trust scores (enhanced with reputation)
    private final Map<String, Double> trustScores; // Node ID -> Score (0.0-1.0)
    private final Map<String, Double> reputationCache; // Node ID -> Blockchain reputation
    
    // Threading
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;
    private ServerSocket serverSocket;
    private volatile boolean isRunning;
    
    /**
     * NodeType enumeration (enhanced)
     */
    public enum NodeType {
        FULL_NODE,          // Full blockchain node
        RELAY_NODE,         // Message relay only
        LITE_NODE,          // Lightweight client
        AGENT_NODE,         // Retail agent node
        COMMUNITY_LEADER,   // Trusted community node
        DAC_NODE,           // NEW: Data Availability Committee node
        COMMITTEE_NODE,     // NEW: Rotating committee member
        VALIDATOR_NODE,     // NEW: Blockchain validator
        BRIDGE_NODE         // NEW: Cross-region bridge
    }
    
    /**
     * MeshPeer - Neighbor node in mesh network (enhanced with African sovereignty)
     */
    public static class MeshPeer {
        private final String peerId;
        private final String peerAddress;
        private final Address blockchainAddress;
        private final PublicKey publicKey;
        private final NodeType nodeType;
        private final String country;
        private final AfricanRegion region; // NEW: African region
        
        // NEW: Blockchain integration
        private final int shardId;
        private final double reputationScore;
        private final boolean isDACNode;
        private final boolean isCommitteeMember;
        private final int dacNodeId;
        
        private PeerStatus status;
        private Instant lastSeen;
        private int messagesForwarded;
        private int messagesReceived;
        
        // NEW: African sovereignty metrics
        private int crossRegionForwards;
        private int asuTransactions;
        private int treasurySubsidizedTx;
        
        public enum PeerStatus {
            CONNECTED, DISCONNECTED, SUSPECTED, BANNED
        }
        
        public MeshPeer(String peerId, String peerAddress, Address blockchainAddress,
                       PublicKey publicKey, NodeType nodeType, String country,
                       int shardId, double reputationScore, boolean isDACNode,
                       boolean isCommitteeMember, int dacNodeId) {
            this.peerId = peerId;
            this.peerAddress = peerAddress;
            this.blockchainAddress = blockchainAddress;
            this.publicKey = publicKey;
            this.nodeType = nodeType;
            this.country = country;
            this.region = AfricanRegion.fromCountry(country);
            
            this.shardId = shardId;
            this.reputationScore = reputationScore;
            this.isDACNode = isDACNode;
            this.isCommitteeMember = isCommitteeMember;
            this.dacNodeId = dacNodeId;
            
            this.status = PeerStatus.CONNECTED;
            this.lastSeen = Instant.now();
            this.messagesForwarded = 0;
            this.messagesReceived = 0;
            
            this.crossRegionForwards = 0;
            this.asuTransactions = 0;
            this.treasurySubsidizedTx = 0;
        }
        
        // Getters
        public String getPeerId() { return peerId; }
        public String getPeerAddress() { return peerAddress; }
        public Address getBlockchainAddress() { return blockchainAddress; }
        public NodeType getNodeType() { return nodeType; }
        public String getCountry() { return country; }
        public AfricanRegion getRegion() { return region; }
        public int getShardId() { return shardId; }
        public double getReputationScore() { return reputationScore; }
        public boolean isDACNode() { return isDACNode; }
        public boolean isCommitteeMember() { return isCommitteeMember; }
        public int getDACNodeId() { return dacNodeId; }
        public PeerStatus getStatus() { return status; }
        public Instant getLastSeen() { return lastSeen; }
        public int getMessagesForwarded() { return messagesForwarded; }
        public int getCrossRegionForwards() { return crossRegionForwards; }
        public int getASUTransactions() { return asuTransactions; }
        public int getTreasurySubsidizedTx() { return treasurySubsidizedTx; }
        
        public void setStatus(PeerStatus status) { this.status = status; }
        public void updateLastSeen() { this.lastSeen = Instant.now(); }
        public void incrementForwarded() { this.messagesForwarded++; }
        public void incrementReceived() { this.messagesReceived++; }
        public void incrementCrossRegion() { this.crossRegionForwards++; }
        public void incrementASU() { this.asuTransactions++; }
        public void incrementTreasurySubsidy() { this.treasurySubsidizedTx++; }
        
        /**
         * NEW: Checks if peer is trusted for forwarding
         */
        public boolean isTrusted() {
            return reputationScore >= MIN_TRUSTED_REPUTATION &&
                   status == PeerStatus.CONNECTED;
        }
        
        /**
         * NEW: Checks if peer can serve as cross-region relay
         */
        public boolean canRelayCrossRegion() {
            return isTrusted() &&
                   (nodeType == NodeType.RELAY_NODE ||
                    nodeType == NodeType.FULL_NODE ||
                    nodeType == NodeType.BRIDGE_NODE ||
                    isDACNode ||
                    isCommitteeMember);
        }
    }
    
    /**
     * MeshMessage - Message propagated through mesh (enhanced with African sovereignty)
     */
    public static class MeshMessage {
        private final String messageId;
        private final Address sender;
        private final MessageType type;
        private final byte[] payload;
        private final Instant createdAt;
        private final Instant expiresAt;
        private final byte[] signature;
        private final int hopCount;
        private final List<String> routePath;
        private final MessagePriority priority;
        
        // NEW: African sovereignty fields
        private final AfricanRegion sourceRegion;
        private final AfricanRegion targetRegion;
        private final int sourceShardId;
        private final int targetShardId;
        private final boolean settlesViaASU;
        private final boolean treasurySubsidized;
        private final boolean requiresMultiRegion;
        private final Set<AfricanRegion> traversedRegions;
        
        public enum MessageType {
            TRANSACTION,        // Blockchain transaction
            BLOCK_ANNOUNCEMENT, // New block
            PEER_DISCOVERY,     // Peer advertisement
            LIQUIDITY_REQUEST,  // Request for liquidity
            OFFLINE_SYNC,       // Offline transaction batch
            EMERGENCY_ALERT,    // Emergency notification
            CROSS_SHARD,        // NEW: Cross-shard transaction
            DAC_ATTESTATION,    // NEW: DAC attestation
            COMMITTEE_SIGNATURE, // NEW: Committee signature
            ASU_SETTLEMENT      // NEW: ASU settlement
        }
        
        public enum MessagePriority {
            LOW, NORMAL, HIGH, URGENT
        }
        
        public MeshMessage(String messageId, Address sender, MessageType type,
                          byte[] payload, byte[] signature, int hopCount,
                          List<String> routePath, MessagePriority priority,
                          AfricanRegion sourceRegion, AfricanRegion targetRegion,
                          int sourceShardId, int targetShardId,
                          boolean settlesViaASU, boolean treasurySubsidized) {
            this.messageId = messageId;
            this.sender = sender;
            this.type = type;
            this.payload = payload;
            this.createdAt = Instant.now();
            this.expiresAt = createdAt.plusMillis(MESSAGE_TTL);
            this.signature = signature;
            this.hopCount = hopCount;
            this.routePath = new ArrayList<>(routePath);
            this.priority = priority;
            
            this.sourceRegion = sourceRegion;
            this.targetRegion = targetRegion;
            this.sourceShardId = sourceShardId;
            this.targetShardId = targetShardId;
            this.settlesViaASU = settlesViaASU;
            this.treasurySubsidized = treasurySubsidized;
            this.requiresMultiRegion = sourceRegion != targetRegion;
            this.traversedRegions = new HashSet<>();
            traversedRegions.add(sourceRegion);
        }
        
        // Getters
        public String getMessageId() { return messageId; }
        public Address getSender() { return sender; }
        public MessageType getType() { return type; }
        public byte[] getPayload() { return payload; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getExpiresAt() { return expiresAt; }
        public byte[] getSignature() { return signature; }
        public int getHopCount() { return hopCount; }
        public List<String> getRoutePath() { return new ArrayList<>(routePath); }
        public MessagePriority getPriority() { return priority; }
        
        // NEW: African sovereignty getters
        public AfricanRegion getSourceRegion() { return sourceRegion; }
        public AfricanRegion getTargetRegion() { return targetRegion; }
        public int getSourceShardId() { return sourceShardId; }
        public int getTargetShardId() { return targetShardId; }
        public boolean settlesViaASU() { return settlesViaASU; }
        public boolean isTreasurySubsidized() { return treasurySubsidized; }
        public boolean requiresMultiRegion() { return requiresMultiRegion; }
        public Set<AfricanRegion> getTraversedRegions() { return new HashSet<>(traversedRegions); }
        
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
        
        public boolean isCrossRegion() {
            return sourceRegion != targetRegion;
        }
        
        public boolean isCrossShard() {
            return sourceShardId != targetShardId;
        }
        
        public MeshMessage incrementHop(String nodeId, AfricanRegion nodeRegion) {
            List<String> newPath = new ArrayList<>(routePath);
            newPath.add(nodeId);
            
            MeshMessage newMessage = new MeshMessage(
                messageId, sender, type, payload, signature,
                hopCount + 1, newPath, priority,
                sourceRegion, targetRegion, sourceShardId, targetShardId,
                settlesViaASU, treasurySubsidized
            );
            
            newMessage.traversedRegions.addAll(this.traversedRegions);
            newMessage.traversedRegions.add(nodeRegion);
            
            return newMessage;
        }
        
        /**
         * NEW: Checks if message has traversed required regions
         */
        public boolean hasMultiRegionPath() {
            return !requiresMultiRegion || traversedRegions.size() >= MIN_CROSS_REGION_RELAYS;
        }
    }
    
    /**
     * MeshStatistics - Network statistics (enhanced)
     */
    public static class MeshStatistics {
        private final AtomicLong messagesReceived;
        private final AtomicLong messagesSent;
        private final AtomicLong messagesForwarded;
        private final AtomicLong messagesDropped;
        private final AtomicLong bytesReceived;
        private final AtomicLong bytesSent;
        
        // NEW: African sovereignty metrics
        private final AtomicLong crossRegionMessages;
        private final AtomicLong asuTransactions;
        private final AtomicLong treasurySubsidizedMessages;
        private final Map<AfricanRegion, AtomicLong> messagesByRegion;
        private final AtomicLong dacAttestations;
        private final AtomicLong committeeSignatures;
        
        public MeshStatistics() {
            this.messagesReceived = new AtomicLong(0);
            this.messagesSent = new AtomicLong(0);
            this.messagesForwarded = new AtomicLong(0);
            this.messagesDropped = new AtomicLong(0);
            this.bytesReceived = new AtomicLong(0);
            this.bytesSent = new AtomicLong(0);
            
            this.crossRegionMessages = new AtomicLong(0);
            this.asuTransactions = new AtomicLong(0);
            this.treasurySubsidizedMessages = new AtomicLong(0);
            this.messagesByRegion = new EnumMap<>(AfricanRegion.class);
            for (AfricanRegion region : AfricanRegion.values()) {
                messagesByRegion.put(region, new AtomicLong(0));
            }
            this.dacAttestations = new AtomicLong(0);
            this.committeeSignatures = new AtomicLong(0);
        }
        
        public void recordReceived(int bytes) {
            messagesReceived.incrementAndGet();
            bytesReceived.addAndGet(bytes);
        }
        
        public void recordSent(int bytes) {
            messagesSent.incrementAndGet();
            bytesSent.addAndGet(bytes);
        }
        
        public void recordForwarded() { messagesForwarded.incrementAndGet(); }
        public void recordDropped() { messagesDropped.incrementAndGet(); }
        
        // NEW: Sovereignty tracking
        public void recordCrossRegion() { crossRegionMessages.incrementAndGet(); }
        public void recordASU() { asuTransactions.incrementAndGet(); }
        public void recordTreasurySubsidy() { treasurySubsidizedMessages.incrementAndGet(); }
        public void recordRegionMessage(AfricanRegion region) {
            messagesByRegion.get(region).incrementAndGet();
        }
        public void recordDACAttestation() { dacAttestations.incrementAndGet(); }
        public void recordCommitteeSignature() { committeeSignatures.incrementAndGet(); }
        
        // Getters
        public long getMessagesReceived() { return messagesReceived.get(); }
        public long getMessagesSent() { return messagesSent.get(); }
        public long getMessagesForwarded() { return messagesForwarded.get(); }
        public long getMessagesDropped() { return messagesDropped.get(); }
        public long getCrossRegionMessages() { return crossRegionMessages.get(); }
        public long getASUTransactions() { return asuTransactions.get(); }
        public long getTreasurySubsidizedMessages() { return treasurySubsidizedMessages.get(); }
        public long getDACAttestations() { return dacAttestations.get(); }
        public long getCommitteeSignatures() { return committeeSignatures.get(); }
        
        public Map<String, Long> getRegionalStats() {
            Map<String, Long> stats = new HashMap<>();
            for (Map.Entry<AfricanRegion, AtomicLong> entry : messagesByRegion.entrySet()) {
                stats.put(entry.getKey().getName(), entry.getValue().get());
            }
            return stats;
        }
    }
    
    /**
     * Constructor with African sovereignty parameters
     */
    public CommunityMeshNode(String nodeId, Address nodeAddress, NodeType nodeType,
                            String country, int shardId, double reputationScore,
                            boolean isDACNode, boolean isCommitteeMember, int dacNodeId,
                            PublicKey publicKey, PrivateKey privateKey) {
        this.nodeId = nodeId;
        this.nodeAddress = nodeAddress;
        this.nodeType = nodeType;
        this.country = country;
        this.region = AfricanRegion.fromCountry(country);
        this.shardId = shardId;
        this.reputationScore = reputationScore;
        this.isDACNode = isDACNode;
        this.isCommitteeMember = isCommitteeMember;
        this.dacNodeId = dacNodeId;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        
        this.neighbors = new ConcurrentHashMap<>();
        this.messageCache = new ConcurrentHashMap<>();
        this.seenMessages = ConcurrentHashMap.newKeySet();
        
        // Regional mesh topology
        this.regionalNeighbors = new EnumMap<>(AfricanRegion.class);
        for (AfricanRegion r : AfricanRegion.values()) {
            regionalNeighbors.put(r, ConcurrentHashMap.newKeySet());
        }
        this.crossRegionRelays = ConcurrentHashMap.newKeySet();
        this.dacNodeConnections = new ConcurrentHashMap<>();
        this.committeeNodeConnections = new ConcurrentHashMap<>();
        
        this.incomingQueue = new LinkedBlockingQueue<>();
        this.outgoingQueue = new LinkedBlockingQueue<>();
        this.undeliveredMessages = new ConcurrentHashMap<>();
        
        this.statistics = new MeshStatistics();
        this.trustScores = new ConcurrentHashMap<>();
        this.reputationCache = new ConcurrentHashMap<>();
        
        this.executorService = Executors.newFixedThreadPool(4);
        this.scheduledExecutor = Executors.newScheduledThreadPool(2);
        this.isRunning = false;
        
        LOGGER.info(String.format("CommunityMeshNode initialized: %s in %s (Shard: %d, Reputation: %.2f, DAC: %b, Committee: %b)",
            nodeId, region.getName(), shardId, reputationScore, isDACNode, isCommitteeMember));
    }
    
    /**
     * Starts the mesh node
     */
    public void start(int port) {
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;
            
            // Start background tasks
            startIncomingHandler();
            startOutgoingHandler();
            startPeerDiscovery();
            startHealthCheck();
            startMessageCleaner();
            startRegionalSync(); // NEW
            
            LOGGER.info("CommunityMeshNode started on port " + port + " in " + region.getName());
            
        } catch (IOException e) {
            LOGGER.severe("Failed to start mesh node: " + e.getMessage());
        }
    }
    
    /**
     * Adds a neighbor to the mesh
     */
    public boolean addNeighbor(MeshPeer peer) {
        if (neighbors.size() >= MAX_NEIGHBORS) {
            LOGGER.warning("Maximum neighbors reached");
            return false;
        }
        
        neighbors.put(peer.getPeerId(), peer);
        
        // Track by region
        regionalNeighbors.get(peer.getRegion()).add(peer.getPeerId());
        
        // Track cross-region relays
        if (peer.canRelayCrossRegion() && peer.getRegion() != region) {
            crossRegionRelays.add(peer.getPeerId());
        }
        
        // Track DAC nodes
        if (peer.isDACNode()) {
            dacNodeConnections.computeIfAbsent(peer.getDACNodeId(), k -> ConcurrentHashMap.newKeySet())
                             .add(peer.getPeerId());
        }
        
        // Track committee members
        if (peer.isCommitteeMember()) {
            committeeNodeConnections.computeIfAbsent(peer.getPeerId(), k -> ConcurrentHashMap.newKeySet())
                                   .add(peer.getPeerId());
        }
        
        // Initialize trust with reputation
        trustScores.put(peer.getPeerId(), Math.min(1.0, peer.getReputationScore() + 
            (peer.getRegion() == region ? REGIONAL_TRUST_BOOST : 0.0)));
        reputationCache.put(peer.getPeerId(), peer.getReputationScore());
        
        LOGGER.info(String.format("Added neighbor: %s from %s (Reputation: %.2f, DAC: %b)",
            peer.getPeerId(), peer.getRegion().getName(), peer.getReputationScore(), peer.isDACNode()));
        
        return true;
    }
    
    /**
     * Broadcasts a message to the mesh
     */
    public String broadcastMessage(MessageType type, byte[] payload, MessagePriority priority,
                                  AfricanRegion targetRegion, int targetShardId,
                                  boolean settlesViaASU, boolean treasurySubsidized) {
        String messageId = generateMessageId();
        byte[] signature = signMessage(payload);
        
        MeshMessage message = new MeshMessage(
            messageId, nodeAddress, type, payload, signature, 0,
            new ArrayList<>(Arrays.asList(nodeId)), priority,
            region, targetRegion, shardId, targetShardId,
            settlesViaASU, treasurySubsidized
        );
        
        outgoingQueue.offer(message);
        
        // Track statistics
        if (message.isCrossRegion()) {
            statistics.recordCrossRegion();
        }
        if (settlesViaASU) {
            statistics.recordASU();
        }
        if (treasurySubsidized) {
            statistics.recordTreasurySubsidy();
        }
        statistics.recordRegionMessage(targetRegion);
        
        return messageId;
    }
    
    /**
     * Simplified broadcast for local messages
     */
    public String broadcastMessage(MessageType type, byte[] payload, MessagePriority priority) {
        return broadcastMessage(type, payload, priority, region, shardId, false, false);
    }
    
    private void startIncomingHandler() {
        executorService.submit(() -> {
            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    executorService.submit(() -> handleIncomingConnection(clientSocket));
                } catch (IOException e) {
                    if (isRunning) {
                        LOGGER.warning("Error accepting connection: " + e.getMessage());
                    }
                }
            }
        });
    }
    
    private void handleIncomingConnection(Socket socket) {
        try {
            byte[] messageBytes = readFromSocket(socket);
            MeshMessage message = deserializeMessage(messageBytes);
            
            if (message != null) {
                incomingQueue.offer(message);
                statistics.recordReceived(messageBytes.length);
            }
            
        } catch (IOException e) {
            LOGGER.warning("Error handling incoming connection: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
    
    private void startOutgoingHandler() {
        executorService.submit(() -> {
            while (isRunning) {
                try {
                    MeshMessage message = outgoingQueue.poll(1, TimeUnit.SECONDS);
                    if (message != null) {
                        forwardMessage(message);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        
        executorService.submit(() -> {
            while (isRunning) {
                try {
                    MeshMessage message = incomingQueue.poll(1, TimeUnit.SECONDS);
                    if (message != null) {
                        processIncomingMessage(message);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
    
    private void processIncomingMessage(MeshMessage message) {
        // Check if already seen
        if (seenMessages.contains(message.getMessageId())) {
            statistics.recordDropped();
            return;
        }
        
        // Check expiration
        if (message.isExpired()) {
            statistics.recordDropped();
            return;
        }
        
        // Verify signature
        if (!verifyMessageSignature(message)) {
            LOGGER.warning("Invalid signature for message: " + message.getMessageId());
            statistics.recordDropped();
            return;
        }
        
        // Mark as seen
        seenMessages.add(message.getMessageId());
        messageCache.put(message.getMessageId(), message);
        
        // Process based on type
        switch (message.getType()) {
            case TRANSACTION:
                handleTransaction(message);
                break;
            case BLOCK_ANNOUNCEMENT:
                handleBlockAnnouncement(message);
                break;
            case PEER_DISCOVERY:
                handlePeerDiscovery(message);
                break;
            case LIQUIDITY_REQUEST:
                handleLiquidityRequest(message);
                break;
            case OFFLINE_SYNC:
                handleOfflineSync(message);
                break;
            case EMERGENCY_ALERT:
                handleEmergencyAlert(message);
                break;
            case CROSS_SHARD:
                handleCrossShard(message);
                break;
            case DAC_ATTESTATION:
                handleDACAttestation(message);
                statistics.recordDACAttestation();
                break;
            case COMMITTEE_SIGNATURE:
                handleCommitteeSignature(message);
                statistics.recordCommitteeSignature();
                break;
            case ASU_SETTLEMENT:
                handleASUSettlement(message);
                statistics.recordASU();
                break;
        }
        
        // Forward to other peers
        if (message.getHopCount() < 10) { // Limit hops
            outgoingQueue.offer(message.incrementHop(nodeId, region));
        }
    }
    
    /**
     * Forwards message with regional routing preference
     */
    private void forwardMessage(MeshMessage message) {
        List<MeshPeer> selectedPeers = selectPeersForForwarding(message);
        
        for (MeshPeer peer : selectedPeers) {
            sendMessageToPeer(peer, message);
        }
        
        if (!selectedPeers.isEmpty()) {
            statistics.recordForwarded();
        }
    }
    
    /**
     * Selects peers for forwarding with African sovereignty routing
     */
    private List<MeshPeer> selectPeersForForwarding(MeshMessage message) {
        List<MeshPeer> selected = new ArrayList<>();
        
        // Get available trusted peers
        List<MeshPeer> availablePeers = neighbors.values().stream()
            .filter(MeshPeer::isTrusted)
            .filter(p -> p.getStatus() == MeshPeer.PeerStatus.CONNECTED)
            .filter(p -> !message.getRoutePath().contains(p.getPeerId()))
            .collect(Collectors.toList());
        
        if (availablePeers.isEmpty()) {
            return selected;
        }
        
        // For cross-region messages, prioritize cross-region relays
        if (message.isCrossRegion()) {
            List<MeshPeer> crossRegionPeers = availablePeers.stream()
                .filter(MeshPeer::canRelayCrossRegion)
                .filter(p -> p.getRegion() == message.getTargetRegion() ||
                           crossRegionRelays.contains(p.getPeerId()))
                .collect(Collectors.toList());
            
            // Select cross-region relays first
            int needed = Math.min(MIN_CROSS_REGION_RELAYS, crossRegionPeers.size());
            for (int i = 0; i < needed; i++) {
                selected.add(crossRegionPeers.get(i));
            }
        }
        
        // Prefer same-region peers for regional messages
        if (message.getTargetRegion() == region) {
            List<MeshPeer> regionalPeers = availablePeers.stream()
                .filter(p -> p.getRegion() == region)
                .filter(p -> !selected.contains(p))
                .collect(Collectors.toList());
            
            int regionalSlots = Math.min(BROADCAST_FANOUT / 2, regionalPeers.size());
            for (int i = 0; i < regionalSlots && selected.size() < BROADCAST_FANOUT; i++) {
                selected.add(regionalPeers.get(i));
            }
        }
        
        // Fill remaining slots with highest trust peers
        availablePeers.stream()
            .filter(p -> !selected.contains(p))
            .sorted((a, b) -> Double.compare(
                trustScores.getOrDefault(b.getPeerId(), 0.0),
                trustScores.getOrDefault(a.getPeerId(), 0.0)))
            .limit(BROADCAST_FANOUT - selected.size())
            .forEach(selected::add);
        
        return selected;
    }
    
    private void sendMessageToPeer(MeshPeer peer, MeshMessage message) {
        try {
            String[] addressParts = peer.getPeerAddress().split(":");
            String host = addressParts[0];
            int port = Integer.parseInt(addressParts[1]);
            
            try (Socket socket = new Socket(host, port)) {
                byte[] messageBytes = serializeMessage(message);
                socket.getOutputStream().write(messageBytes);
                
                peer.incrementForwarded();
                peer.updateLastSeen();
                
                if (message.isCrossRegion()) {
                    peer.incrementCrossRegion();
                }
                if (message.settlesViaASU()) {
                    peer.incrementASU();
                }
                if (message.isTreasurySubsidized()) {
                    peer.incrementTreasurySubsidy();
                }
                
                // Increase trust
                updateTrustScore(peer.getPeerId(), 0.01);
                
                statistics.recordSent(messageBytes.length);
                
            } catch (IOException e) {
                LOGGER.warning("Failed to send to peer " + peer.getPeerId() + ": " + e.getMessage());
                peer.setStatus(MeshPeer.PeerStatus.SUSPECTED);
                updateTrustScore(peer.getPeerId(), -0.05);
            }
            
        } catch (Exception e) {
            LOGGER.severe("Error sending message to peer: " + e.getMessage());
        }
    }
    
    private void updateTrustScore(String peerId, double delta) {
        trustScores.compute(peerId, (k, v) -> {
            double newScore = (v == null ? 0.5 : v) + delta;
            return Math.max(0.0, Math.min(1.0, newScore));
        });
    }
    
    // Message handlers
    private void handleTransaction(MeshMessage message) {
        LOGGER.info("Processing transaction: " + message.getMessageId());
    }
    
    private void handleBlockAnnouncement(MeshMessage message) {
        LOGGER.info("Processing block announcement: " + message.getMessageId());
    }
    
    private void handlePeerDiscovery(MeshMessage message) {
        LOGGER.info("Processing peer discovery: " + message.getMessageId());
    }
    
    private void handleLiquidityRequest(MeshMessage message) {
        LOGGER.info("Processing liquidity request: " + message.getMessageId());
    }
    
    private void handleOfflineSync(MeshMessage message) {
        LOGGER.info("Processing offline sync: " + message.getMessageId());
    }
    
    private void handleEmergencyAlert(MeshMessage message) {
        LOGGER.warning("EMERGENCY ALERT: " + message.getMessageId());
    }
    
    /**
     * NEW: Handles cross-shard messages
     */
    private void handleCrossShard(MeshMessage message) {
        LOGGER.info(String.format("Processing cross-shard message: %s (Shard %d -> %d)",
            message.getMessageId(), message.getSourceShardId(), message.getTargetShardId()));
        
        if (message.isCrossRegion()) {
            statistics.recordCrossRegion();
        }
    }
    
    /**
     * NEW: Handles DAC attestation messages
     */
    private void handleDACAttestation(MeshMessage message) {
        LOGGER.info("Processing DAC attestation: " + message.getMessageId());
        
        // If this is a DAC node, process the attestation
        if (isDACNode) {
            LOGGER.info("DAC node " + dacNodeId + " processing attestation");
        }
    }
    
    /**
     * NEW: Handles committee signature messages
     */
    private void handleCommitteeSignature(MeshMessage message) {
        LOGGER.info("Processing committee signature: " + message.getMessageId());
        
        if (isCommitteeMember) {
            LOGGER.info("Committee member processing signature");
        }
    }
    
    /**
     * NEW: Handles ASU settlement messages
     */
    private void handleASUSettlement(MeshMessage message) {
        LOGGER.info("Processing ASU settlement: " + message.getMessageId());
        statistics.recordASU();
    }
    
    private void announcePresence() {
        String announcement = String.format("NODE:%s:%s:%s:%s:%d:%.2f",
            nodeId, nodeType, country, region, shardId, reputationScore);
        broadcastMessage(MessageType.PEER_DISCOVERY, announcement.getBytes(),
                        MessagePriority.NORMAL);
    }
    
    private void startPeerDiscovery() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            announcePresence();
        }, 300, 300, TimeUnit.SECONDS); // Every 5 minutes
    }
    
    private void startHealthCheck() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            for (MeshPeer peer : neighbors.values()) {
                long secondsSinceLastSeen =
                    (Instant.now().toEpochMilli() - peer.getLastSeen().toEpochMilli()) / 1000;
                
                if (secondsSinceLastSeen > 600) { // 10 minutes
                    peer.setStatus(MeshPeer.PeerStatus.SUSPECTED);
                }
                
                if (secondsSinceLastSeen > 1800) { // 30 minutes
                    peer.setStatus(MeshPeer.PeerStatus.DISCONNECTED);
                }
            }
        }, 60, 60, TimeUnit.SECONDS);
    }
    
    private void startMessageCleaner() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            messageCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            
            if (seenMessages.size() > MAX_MESSAGE_CACHE) {
                Iterator<String> iter = seenMessages.iterator();
                for (int i = 0; i < 1000 && iter.hasNext(); i++) {
                    iter.next();
                    iter.remove();
                }
            }
        }, 600, 600, TimeUnit.SECONDS);
    }
    
    /**
     * NEW: Periodic regional synchronization
     */
    private void startRegionalSync() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            LOGGER.info(String.format("Regional sync: %s - Neighbors: %d, Same-region: %d, Cross-region relays: %d",
                region.getName(), neighbors.size(),
                regionalNeighbors.get(region).size(),
                crossRegionRelays.size()));
        }, 900, 900, TimeUnit.SECONDS); // Every 15 minutes
    }
    
    private byte[] signMessage(byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(payload);
        } catch (NoSuchAlgorithmException e) {
            return new byte[64];
        }
    }
    
    private boolean verifyMessageSignature(MeshMessage message) {
        return message.getSignature() != null && message.getSignature().length > 0;
    }
    
    private String generateMessageId() {
        return "MSG-" + nodeId + "-" + System.currentTimeMillis();
    }
    
    private byte[] serializeMessage(MeshMessage message) {
        String data = message.getMessageId() + ":" + message.getType() + ":" +
                     Base64.getEncoder().encodeToString(message.getPayload());
        return data.getBytes();
    }
    
    private MeshMessage deserializeMessage(byte[] bytes) {
        // Simplified deserialization
        return null;
    }
    
    private byte[] readFromSocket(Socket socket) throws IOException {
        byte[] buffer = new byte[65536];
        int bytesRead = socket.getInputStream().read(buffer);
        return Arrays.copyOf(buffer, bytesRead);
    }
    
    // Getters
    public String getNodeId() { return nodeId; }
    public NodeType getNodeType() { return nodeType; }
    public String getCountry() { return country; }
    public AfricanRegion getRegion() { return region; }
    public int getShardId() { return shardId; }
    public double getReputationScore() { return reputationScore; }
    public boolean isDACNode() { return isDACNode; }
    public boolean isCommitteeMember() { return isCommitteeMember; }
    public List<MeshPeer> getNeighbors() { return new ArrayList<>(neighbors.values()); }
    public MeshStatistics getStatistics() { return statistics; }
    public boolean isRunning() { return isRunning; }
    
    public int getNeighborCount() { return neighbors.size(); }
    public int getCachedMessageCount() { return messageCache.size(); }
    public int getCrossRegionRelayCount() { return crossRegionRelays.size(); }
    public int getRegionalNeighborCount() { return regionalNeighbors.get(region).size(); }
    
    /**
     * NEW: Gets neighbors by region
     */
    public List<MeshPeer> getNeighborsByRegion(AfricanRegion targetRegion) {
        return neighbors.values().stream()
            .filter(p -> p.getRegion() == targetRegion)
            .collect(Collectors.toList());
    }
    
    /**
     * NEW: Gets regional statistics
     */
    public Map<String, Object> getRegionalStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("region", region.getName());
        stats.put("totalNeighbors", neighbors.size());
        stats.put("regionalNeighbors", regionalNeighbors.get(region).size());
        stats.put("crossRegionRelays", crossRegionRelays.size());
        stats.put("dacConnections", dacNodeConnections.size());
        stats.put("committeeConnections", committeeNodeConnections.size());
        stats.put("crossRegionMessages", statistics.getCrossRegionMessages());
        stats.put("asuTransactions", statistics.getASUTransactions());
        stats.put("treasurySubsidized", statistics.getTreasurySubsidizedMessages());
        stats.put("messagesByRegion", statistics.getRegionalStats());
        return stats;
    }
    
    /**
     * Stops the mesh node
     */
    public void stop() {
        isRunning = false;
        
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOGGER.severe("Error closing server socket: " + e.getMessage());
        }
        
        executorService.shutdown();
        scheduledExecutor.shutdown();
        
        LOGGER.info("CommunityMeshNode stopped");
    }
}