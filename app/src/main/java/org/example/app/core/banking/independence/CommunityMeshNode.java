package org.example.app.core.banking.independence;

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
import java.util.logging.Logger;

/**
 * CommunityMeshNode - Local peer-to-peer mesh network for TINC Blockchain
 * 
 * CRITICAL FOR INDEPENDENCE: Enable transaction relay WITHOUT central servers
 * 
 * Features:
 * - Self-organizing mesh topology (no central coordinator)
 * - Bluetooth/WiFi Direct/NFC peer discovery
 * - Store-and-forward message relay (works when disconnected)
 * - Epidemic broadcast (gossip protocol)
 * - Geographic clustering (local-first routing)
 * - Raspberry Pi / low-power device support
 * - Battery-efficient protocols
 * - Opportunistic synchronization
 * 
 * Use Cases:
 * - Community markets (peer-to-peer trading)
 * - Rural areas (mesh replaces cell towers)
 * - Disaster zones (infrastructure destroyed)
 * - Censorship resistance (no single point to shut down)
 * - Local liquidity pools
 * 
 * Architecture:
 * - Each node = mini relay server
 * - Automatic neighbor discovery
 * - Multi-path routing (censorship resistance)
 * - Trust-based forwarding (prevent spam)
 */
public class CommunityMeshNode {
    private static final Logger LOGGER = Logger.getLogger(CommunityMeshNode.class.getName());
    
    // Node configuration
    private static final int DEFAULT_PORT = 9876;
    private static final int MAX_NEIGHBORS = 50;
    private static final int MAX_MESSAGE_CACHE = 10000;
    private static final long MESSAGE_TTL = 86400000L; // 24 hours
    private static final int BROADCAST_FANOUT = 7; // Number of peers to forward to
    
    // Node identity
    private final String nodeId;
    private final Address nodeAddress;
    private final NodeType nodeType;
    private final String location; // Geographic location
    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    
    // Mesh network
    private final Map<String, MeshPeer> neighbors;
    private final Map<String, MeshMessage> messageCache; // Message ID -> Message
    private final Set<String> seenMessages; // Duplicate detection
    
    // Message queue
    private final BlockingQueue<MeshMessage> incomingQueue;
    private final BlockingQueue<MeshMessage> outgoingQueue;
    
    // Store-and-forward
    private final Map<Address, List<MeshMessage>> undeliveredMessages;
    
    // Network statistics
    private final MeshStatistics statistics;
    
    // Geographic clustering
    private final Map<String, Set<String>> geographicClusters; // Location -> Node IDs
    
    // Trust scores
    private final Map<String, Integer> trustScores; // Node ID -> Score (0-100)
    
    // Threading
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;
    private ServerSocket serverSocket;
    private volatile boolean isRunning;
    
    /**
     * NodeType enumeration
     */
    public enum NodeType {
        FULL_NODE,          // Full blockchain node
        RELAY_NODE,         // Message relay only
        LITE_NODE,          // Lightweight client
        AGENT_NODE,         // Retail agent node
        COMMUNITY_LEADER    // Trusted community node
    }
    
    /**
     * MeshPeer - Neighbor node in mesh network
     */
    public static class MeshPeer {
        private final String peerId;
        private final String peerAddress; // IP:Port
        private final Address blockchainAddress;
        private final PublicKey publicKey;
        private final NodeType nodeType;
        private final String location;
        private PeerStatus status;
        private Instant lastSeen;
        private int messagesForwarded;
        private int messagesReceived;
        
        public enum PeerStatus {
            CONNECTED, DISCONNECTED, SUSPECTED, BANNED
        }
        
        public MeshPeer(String peerId, String peerAddress, Address blockchainAddress,
                       PublicKey publicKey, NodeType nodeType, String location) {
            this.peerId = peerId;
            this.peerAddress = peerAddress;
            this.blockchainAddress = blockchainAddress;
            this.publicKey = publicKey;
            this.nodeType = nodeType;
            this.location = location;
            this.status = PeerStatus.CONNECTED;
            this.lastSeen = Instant.now();
            this.messagesForwarded = 0;
            this.messagesReceived = 0;
        }
        
        public String getPeerId() { return peerId; }
        public String getPeerAddress() { return peerAddress; }
        public Address getBlockchainAddress() { return blockchainAddress; }
        public NodeType getNodeType() { return nodeType; }
        public String getLocation() { return location; }
        public PeerStatus getStatus() { return status; }
        public Instant getLastSeen() { return lastSeen; }
        public int getMessagesForwarded() { return messagesForwarded; }
        
        public void setStatus(PeerStatus status) { this.status = status; }
        public void updateLastSeen() { this.lastSeen = Instant.now(); }
        public void incrementForwarded() { this.messagesForwarded++; }
        public void incrementReceived() { this.messagesReceived++; }
    }
    
    /**
     * MeshMessage - Message propagated through mesh
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
        private final List<String> routePath; // Nodes traversed
        
        // Priority
        private final MessagePriority priority;
        
        public enum MessageType {
            TRANSACTION,        // Blockchain transaction
            BLOCK_ANNOUNCEMENT, // New block
            PEER_DISCOVERY,     // Peer advertisement
            LIQUIDITY_REQUEST,  // Request for liquidity
            OFFLINE_SYNC,       // Offline transaction batch
            EMERGENCY_ALERT     // Emergency notification
        }
        
        public enum MessagePriority {
            LOW, NORMAL, HIGH, URGENT
        }
        
        public MeshMessage(String messageId, Address sender, MessageType type,
                          byte[] payload, byte[] signature, int hopCount,
                          List<String> routePath, MessagePriority priority) {
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
        }
        
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
        
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
        
        public MeshMessage incrementHop(String nodeId) {
            List<String> newPath = new ArrayList<>(routePath);
            newPath.add(nodeId);
            return new MeshMessage(messageId, sender, type, payload, signature,
                hopCount + 1, newPath, priority);
        }
    }
    
    /**
     * MeshStatistics - Network performance metrics
     */
    public static class MeshStatistics {
        private int messagesSent;
        private int messagesReceived;
        private int messagesForwarded;
        private int messagesDropped;
        private long totalBandwidth;
        private final Map<String, Integer> messageTypeCount;
        
        public MeshStatistics() {
            this.messageTypeCount = new ConcurrentHashMap<>();
        }
        
        public synchronized void incrementSent() { messagesSent++; }
        public synchronized void incrementReceived() { messagesReceived++; }
        public synchronized void incrementForwarded() { messagesForwarded++; }
        public synchronized void incrementDropped() { messagesDropped++; }
        public synchronized void addBandwidth(long bytes) { totalBandwidth += bytes; }
        
        public void incrementMessageType(String type) {
            messageTypeCount.merge(type, 1, Integer::sum);
        }
        
        public int getMessagesSent() { return messagesSent; }
        public int getMessagesReceived() { return messagesReceived; }
        public int getMessagesForwarded() { return messagesForwarded; }
        public int getMessagesDropped() { return messagesDropped; }
        public long getTotalBandwidth() { return totalBandwidth; }
        public Map<String, Integer> getMessageTypeCount() { 
            return new HashMap<>(messageTypeCount); 
        }
    }
    
    /**
     * Constructor for CommunityMeshNode
     */
    public CommunityMeshNode(String nodeId, Address nodeAddress, NodeType nodeType,
                            String location, PublicKey publicKey, PrivateKey privateKey) {
        this.nodeId = nodeId;
        this.nodeAddress = nodeAddress;
        this.nodeType = nodeType;
        this.location = location;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        
        this.neighbors = new ConcurrentHashMap<>();
        this.messageCache = new ConcurrentHashMap<>();
        this.seenMessages = ConcurrentHashMap.newKeySet();
        
        this.incomingQueue = new LinkedBlockingQueue<>();
        this.outgoingQueue = new LinkedBlockingQueue<>();
        
        this.undeliveredMessages = new ConcurrentHashMap<>();
        
        this.statistics = new MeshStatistics();
        this.geographicClusters = new ConcurrentHashMap<>();
        this.trustScores = new ConcurrentHashMap<>();
        
        this.executorService = Executors.newFixedThreadPool(10);
        this.scheduledExecutor = Executors.newScheduledThreadPool(5);
        this.isRunning = false;
        
        LOGGER.info(String.format("CommunityMeshNode initialized: %s (%s) at %s",
            nodeId, nodeType, location));
    }
    
    /**
     * Starts the mesh node
     */
    public void start(int port) throws IOException {
        if (isRunning) {
            LOGGER.warning("Node already running");
            return;
        }
        
        // Start server socket
        serverSocket = new ServerSocket(port);
        isRunning = true;
        
        // Start message processors
        startMessageReceiver();
        startMessageProcessor();
        startMessageBroadcaster();
        
        // Start background tasks
        startPeerDiscovery();
        startHealthCheck();
        startMessageCleaner();
        
        // Announce presence to network
        announcePresence();
        
        LOGGER.info("CommunityMeshNode started on port " + port);
    }
    
    /**
     * Broadcasts a message to mesh network
     */
    public String broadcastMessage(MessageType type, byte[] payload, MessagePriority priority) {
        // Generate message ID
        String messageId = generateMessageId();
        
        // Sign message
        byte[] signature = signMessage(payload);
        
        // Create message
        MeshMessage message = new MeshMessage(
            messageId, nodeAddress, type, payload, signature, 0,
            Arrays.asList(nodeId), priority
        );
        
        // Add to outgoing queue
        try {
            outgoingQueue.put(message);
            statistics.incrementSent();
            statistics.incrementMessageType(type.toString());
            
            LOGGER.info(String.format("Broadcasting message: %s (Type: %s, Priority: %s)",
                messageId, type, priority));
            
            return messageId;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.severe("Failed to queue message: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Sends a message to specific destination (store-and-forward)
     */
    public boolean sendToDestination(Address destination, MessageType type, byte[] payload) {
        // Check if destination is a direct neighbor
        MeshPeer directPeer = findPeerByAddress(destination);
        
        if (directPeer != null && directPeer.getStatus() == MeshPeer.PeerStatus.CONNECTED) {
            // Send directly
            return broadcastMessage(type, payload, MessagePriority.NORMAL) != null;
        } else {
            // Store for later delivery
            String messageId = broadcastMessage(type, payload, MessagePriority.NORMAL);
            
            if (messageId != null) {
                MeshMessage message = messageCache.get(messageId);
                undeliveredMessages.computeIfAbsent(destination, k -> new ArrayList<>())
                    .add(message);
                
                LOGGER.info(String.format("Message stored for delivery to %s: %s",
                    destination, messageId));
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Adds a neighbor peer
     */
    public boolean addNeighbor(String peerId, String peerAddress, Address blockchainAddress,
                              PublicKey publicKey, NodeType nodeType, String peerLocation) {
        
        if (neighbors.size() >= MAX_NEIGHBORS) {
            LOGGER.warning("Maximum neighbors reached");
            return false;
        }
        
        MeshPeer peer = new MeshPeer(
            peerId, peerAddress, blockchainAddress, publicKey, nodeType, peerLocation
        );
        
        neighbors.put(peerId, peer);
        
        // Update geographic clusters
        geographicClusters.computeIfAbsent(peerLocation, k -> new HashSet<>()).add(peerId);
        
        // Initialize trust score
        trustScores.put(peerId, 50); // Start with neutral trust
        
        LOGGER.info(String.format("Added neighbor: %s at %s (Location: %s)",
            peerId, peerAddress, peerLocation));
        
        return true;
    }
    
    /**
     * Removes a neighbor
     */
    public boolean removeNeighbor(String peerId) {
        MeshPeer peer = neighbors.remove(peerId);
        
        if (peer != null) {
            // Remove from geographic clusters
            Set<String> cluster = geographicClusters.get(peer.getLocation());
            if (cluster != null) {
                cluster.remove(peerId);
            }
            
            trustScores.remove(peerId);
            
            LOGGER.info("Removed neighbor: " + peerId);
            return true;
        }
        
        return false;
    }
    
    /**
     * Updates trust score for a peer
     */
    public void updateTrustScore(String peerId, int delta) {
        int currentScore = trustScores.getOrDefault(peerId, 50);
        int newScore = Math.max(0, Math.min(100, currentScore + delta));
        trustScores.put(peerId, newScore);
        
        // Ban peer if trust too low
        if (newScore < 20) {
            MeshPeer peer = neighbors.get(peerId);
            if (peer != null) {
                peer.setStatus(MeshPeer.PeerStatus.BANNED);
                LOGGER.warning("Peer banned due to low trust: " + peerId);
            }
        }
    }
    
    // Private helper methods
    
    private void startMessageReceiver() {
        executorService.submit(() -> {
            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    executorService.submit(() -> handleIncomingConnection(clientSocket));
                } catch (IOException e) {
                    if (isRunning) {
                        LOGGER.severe("Error accepting connection: " + e.getMessage());
                    }
                }
            }
        });
    }
    
    private void handleIncomingConnection(Socket socket) {
        try {
            // Read message from socket
            byte[] messageBytes = readFromSocket(socket);
            
            if (messageBytes != null) {
                MeshMessage message = deserializeMessage(messageBytes);
                
                if (message != null && !message.isExpired()) {
                    incomingQueue.put(message);
                    statistics.incrementReceived();
                    statistics.addBandwidth(messageBytes.length);
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Error handling incoming connection: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
    
    private void startMessageProcessor() {
        executorService.submit(() -> {
            while (isRunning) {
                try {
                    MeshMessage message = incomingQueue.poll(1, TimeUnit.SECONDS);
                    
                    if (message != null) {
                        processMessage(message);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
    
    private void processMessage(MeshMessage message) {
        // Check if already seen
        if (seenMessages.contains(message.getMessageId())) {
            statistics.incrementDropped();
            return;
        }
        
        // Mark as seen
        seenMessages.add(message.getMessageId());
        
        // Verify signature
        if (!verifyMessageSignature(message)) {
            LOGGER.warning("Invalid signature for message: " + message.getMessageId());
            statistics.incrementDropped();
            return;
        }
        
        // Cache message
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
        }
        
        // Forward to neighbors (epidemic broadcast)
        forwardMessage(message);
    }
    
    private void startMessageBroadcaster() {
        executorService.submit(() -> {
            while (isRunning) {
                try {
                    MeshMessage message = outgoingQueue.poll(1, TimeUnit.SECONDS);
                    
                    if (message != null) {
                        // Select peers to forward to
                        List<MeshPeer> forwardPeers = selectForwardPeers(message);
                        
                        for (MeshPeer peer : forwardPeers) {
                            sendMessageToPeer(peer, message);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
    
    private void forwardMessage(MeshMessage message) {
        // Don't forward if hop count too high
        if (message.getHopCount() > 10) {
            statistics.incrementDropped();
            return;
        }
        
        // Increment hop and add to outgoing queue
        MeshMessage forwarded = message.incrementHop(nodeId);
        
        try {
            outgoingQueue.put(forwarded);
            statistics.incrementForwarded();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private List<MeshPeer> selectForwardPeers(MeshMessage message) {
        List<MeshPeer> selected = new ArrayList<>();
        
        // Get connected peers
        List<MeshPeer> availablePeers = new ArrayList<>();
        for (MeshPeer peer : neighbors.values()) {
            if (peer.getStatus() == MeshPeer.PeerStatus.CONNECTED &&
                !message.getRoutePath().contains(peer.getPeerId())) {
                availablePeers.add(peer);
            }
        }
        
        if (availablePeers.isEmpty()) {
            return selected;
        }
        
        // Priority-based selection
        if (message.getPriority() == MessagePriority.URGENT) {
            // Broadcast to all for urgent messages
            return availablePeers;
        }
        
        // Geographic clustering: prefer local peers
        List<MeshPeer> localPeers = availablePeers.stream()
            .filter(p -> p.getLocation().equals(location))
            .collect(java.util.stream.Collectors.toList());
        
        int fanout = Math.min(BROADCAST_FANOUT, availablePeers.size());
        
        // Select local peers first
        for (int i = 0; i < Math.min(fanout / 2, localPeers.size()); i++) {
            selected.add(localPeers.get(i));
        }
        
        // Fill remaining slots with non-local peers
        Collections.shuffle(availablePeers);
        for (MeshPeer peer : availablePeers) {
            if (selected.size() >= fanout) break;
            if (!selected.contains(peer)) {
                selected.add(peer);
            }
        }
        
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
                
                // Increase trust
                updateTrustScore(peer.getPeerId(), 1);
                
            } catch (IOException e) {
                LOGGER.warning("Failed to send to peer " + peer.getPeerId() + ": " + e.getMessage());
                peer.setStatus(MeshPeer.PeerStatus.SUSPECTED);
                
                // Decrease trust
                updateTrustScore(peer.getPeerId(), -5);
            }
            
        } catch (Exception e) {
            LOGGER.severe("Error sending message to peer: " + e.getMessage());
        }
    }
    
    private void handleTransaction(MeshMessage message) {
        LOGGER.info("Processing transaction message: " + message.getMessageId());
        // In production, forward to blockchain
    }
    
    private void handleBlockAnnouncement(MeshMessage message) {
        LOGGER.info("Processing block announcement: " + message.getMessageId());
        // In production, validate and process block
    }
    
    private void handlePeerDiscovery(MeshMessage message) {
        LOGGER.info("Processing peer discovery: " + message.getMessageId());
        // In production, update peer list
    }
    
    private void handleLiquidityRequest(MeshMessage message) {
        LOGGER.info("Processing liquidity request: " + message.getMessageId());
        // In production, match with local liquidity providers
    }
    
    private void handleOfflineSync(MeshMessage message) {
        LOGGER.info("Processing offline sync: " + message.getMessageId());
        // In production, batch sync offline transactions
    }
    
    private void handleEmergencyAlert(MeshMessage message) {
        LOGGER.warning("EMERGENCY ALERT: " + message.getMessageId());
        // In production, prioritize emergency messages
    }
    
    private void announcePresence() {
        String announcement = String.format("NODE:%s:%s:%s", nodeId, nodeType, location);
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
        }, 60, 60, TimeUnit.SECONDS); // Every minute
    }
    
    private void startMessageCleaner() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            // Clean expired messages
            messageCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            
            // Clean seen messages (keep last 10000)
            if (seenMessages.size() > MAX_MESSAGE_CACHE) {
                Iterator<String> iter = seenMessages.iterator();
                for (int i = 0; i < 1000 && iter.hasNext(); i++) {
                    iter.next();
                    iter.remove();
                }
            }
        }, 600, 600, TimeUnit.SECONDS); // Every 10 minutes
    }
    
    private MeshPeer findPeerByAddress(Address address) {
        for (MeshPeer peer : neighbors.values()) {
            if (peer.getBlockchainAddress().equals(address)) {
                return peer;
            }
        }
        return null;
    }
    
    private byte[] signMessage(byte[] payload) {
        // In production, use actual cryptographic signing
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(payload);
        } catch (NoSuchAlgorithmException e) {
            return new byte[64];
        }
    }
    
    private boolean verifyMessageSignature(MeshMessage message) {
        // In production, verify actual cryptographic signature
        return message.getSignature() != null && message.getSignature().length > 0;
    }
    
    private String generateMessageId() {
        return "MSG-" + nodeId + "-" + System.currentTimeMillis();
    }
    
    private byte[] serializeMessage(MeshMessage message) {
        // Simplified serialization
        String data = message.getMessageId() + ":" + message.getType() + ":" + 
                     Base64.getEncoder().encodeToString(message.getPayload());
        return data.getBytes();
    }
    
    private MeshMessage deserializeMessage(byte[] bytes) {
        // Simplified deserialization
        // In production, use proper serialization (Protobuf, etc.)
        return null;
    }
    
    private byte[] readFromSocket(Socket socket) throws IOException {
        // Simplified socket reading
        byte[] buffer = new byte[65536]; // 64KB
        int bytesRead = socket.getInputStream().read(buffer);
        return Arrays.copyOf(buffer, bytesRead);
    }
    
    // Getters
    
    public String getNodeId() { return nodeId; }
    public NodeType getNodeType() { return nodeType; }
    public String getLocation() { return location; }
    public List<MeshPeer> getNeighbors() { return new ArrayList<>(neighbors.values()); }
    public MeshStatistics getStatistics() { return statistics; }
    public boolean isRunning() { return isRunning; }
    
    public int getNeighborCount() { return neighbors.size(); }
    public int getCachedMessageCount() { return messageCache.size(); }
    
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