package org.example.app.core.ui.network;

import org.example.app.core.network.LocalTransport;
import org.example.app.core.network.Messages;
import org.example.app.core.metrics.NetworkMetrics;
import org.example.app.core.p2p.PeerNetwork;
import org.example.app.core.p2p.Peer;
import org.example.app.core.p2p.Message;
import org.example.app.core.p2p.BroadcastManager;
import org.example.app.core.consensus.Blockchain;
import org.example.app.core.block.Block;
import org.example.app.core.block.Transaction;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * NetworkController manages network operations, peer connections, and message handling
 * for the TINC blockchain network interface.
 */
public class NetworkController {
    private static final Logger LOGGER = Logger.getLogger(NetworkController.class.getName());
    
    // Network Components
    private PeerNetwork peerNetwork;
    private LocalTransport localTransport;
    private NetworkMetrics networkMetrics;
    private BroadcastManager broadcastManager;
    private Blockchain blockchain;
    
    // Connection Management
    private final Map<String, Peer> connectedPeers;
    private final Map<String, LocalTransport> localTransports;
    private final Set<InetSocketAddress> knownAddresses;
    private final Queue<Messages.Message> messageQueue;
    
    // Network Configuration
    private String nodeId;
    private InetSocketAddress nodeAddress;
    private boolean networkStarted;
    private boolean discoveryEnabled;
    private int maxPeers;
    private long heartbeatInterval;
    
    // Event Listeners
    private final List<NetworkEventListener> eventListeners;
    
    // Executor Service for async operations
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutorService;
    
    /**
     * Network Event Listener Interface
     */
    public interface NetworkEventListener {
        void onPeerConnected(String peerId, InetSocketAddress address);
        void onPeerDisconnected(String peerId);
        void onMessageReceived(String fromPeer, Messages.Message message);
        void onMessageSent(String toPeer, Messages.Message message);
        void onBroadcastSent(Messages.Message message, int peerCount);
        void onNetworkError(String error);
    }
    
    /**
     * Network Status Information
     */
    public static class NetworkStatus {
        public final boolean isActive;
        public final int connectedPeers;
        public final int knownPeers;
        public final long messagesSent;
        public final long messagesReceived;
        public final long bytesSent;
        public final long bytesReceived;
        public final String nodeId;
        public final InetSocketAddress nodeAddress;
        public final boolean discoveryEnabled;
        
        public NetworkStatus(boolean isActive, int connectedPeers, int knownPeers,
                           long messagesSent, long messagesReceived, long bytesSent,
                           long bytesReceived, String nodeId, InetSocketAddress nodeAddress,
                           boolean discoveryEnabled) {
            this.isActive = isActive;
            this.connectedPeers = connectedPeers;
            this.knownPeers = knownPeers;
            this.messagesSent = messagesSent;
            this.messagesReceived = messagesReceived;
            this.bytesSent = bytesSent;
            this.bytesReceived = bytesReceived;
            this.nodeId = nodeId;
            this.nodeAddress = nodeAddress;
            this.discoveryEnabled = discoveryEnabled;
        }
        
        @Override
        public String toString() {
            return String.format(
                "NetworkStatus{active=%s, connected=%d, known=%d, sent=%d, received=%d}",
                isActive, connectedPeers, knownPeers, messagesSent, messagesReceived
            );
        }
    }
    
    /**
     * Peer Information for UI display
     */
    public static class PeerInfo {
        public final String peerId;
        public final InetSocketAddress address;
        public final boolean connected;
        public final long connectionTime;
        public final long lastSeen;
        public final long messagesSent;
        public final long messagesReceived;
        public final long latency;
        public final String status;
        
        public PeerInfo(String peerId, InetSocketAddress address, boolean connected,
                       long connectionTime, long lastSeen, long messagesSent,
                       long messagesReceived, long latency, String status) {
            this.peerId = peerId;
            this.address = address;
            this.connected = connected;
            this.connectionTime = connectionTime;
            this.lastSeen = lastSeen;
            this.messagesSent = messagesSent;
            this.messagesReceived = messagesReceived;
            this.latency = latency;
            this.status = status;
        }
    }
    
    /**
     * Constructor
     */
    public NetworkController() {
        this.connectedPeers = new ConcurrentHashMap<>();
        this.localTransports = new ConcurrentHashMap<>();
        this.knownAddresses = ConcurrentHashMap.newKeySet();
        this.messageQueue = new ConcurrentLinkedQueue<>();
        this.eventListeners = new ArrayList<>();
        
        this.nodeId = "tinc-node-" + System.currentTimeMillis();
        this.nodeAddress = new InetSocketAddress("127.0.0.1", 8080);
        this.networkStarted = false;
        this.discoveryEnabled = true;
        this.maxPeers = 50;
        this.heartbeatInterval = 30000; // 30 seconds
        
        this.executorService = Executors.newCachedThreadPool();
        this.scheduledExecutorService = Executors.newScheduledThreadPool(2);
        
        // Initialize components
        initializeComponents();
    }
    
    /**
     * Initialize network components
     */
    private void initializeComponents() {
        try {
            // Initialize metrics
            networkMetrics = new NetworkMetrics();
            
            // Initialize local transport
            localTransport = new LocalTransport(nodeAddress);
            
            // Initialize peer network
            peerNetwork = new PeerNetwork(nodeId, nodeAddress.getHostString(), nodeAddress.getPort());
            
            // Initialize broadcast manager
            Peer localPeer = new Peer(nodeId, nodeAddress.getHostString(), nodeAddress.getPort());
                     Set<String> knownPeers = ConcurrentHashMap.newKeySet();
                broadcastManager = new BroadcastManager(localPeer, knownPeers);
            
            LOGGER.info("Network components initialized successfully");
        } catch (Exception e) {
            LOGGER.severe("Failed to initialize network components: " + e.getMessage());
            throw new RuntimeException("Network initialization failed", e);
        }
    }
    
    // ================================
    // NETWORK LIFECYCLE MANAGEMENT
    // ================================
    
    /**
     * Start the network
     */
    public boolean startNetwork() {
        if (networkStarted) {
            LOGGER.warning("Network is already started");
            return true;
        }
        
        try {
            LOGGER.info("Starting network...");
            
            // Start peer network server
            peerNetwork.startServer();
            
            // Start heartbeat service
            startHeartbeatService();
            
            // Start message processing
            startMessageProcessing();
            
            networkStarted = true;
            
            // Notify listeners
            notifyNetworkEvent("Network started successfully");
            
            LOGGER.info("Network started successfully on " + nodeAddress);
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Failed to start network: " + e.getMessage());
            notifyNetworkError("Failed to start network: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Stop the network
     */
    public boolean stopNetwork() {
        if (!networkStarted) {
            LOGGER.info("Network is not started");
            return true;
        }
        
        try {
            LOGGER.info("Stopping network...");
            
            // Disconnect all peers
            disconnectAllPeers();
            
            // Stop services
            scheduledExecutorService.shutdown();
            executorService.shutdown();
            
            // Close local transport
            if (localTransport != null) {
                localTransport.close();
            }
            
            networkStarted = false;
            
            // Notify listeners
            notifyNetworkEvent("Network stopped");
            
            LOGGER.info("Network stopped successfully");
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Error stopping network: " + e.getMessage());
            notifyNetworkError("Error stopping network: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Restart the network
     */
    public boolean restartNetwork() {
        LOGGER.info("Restarting network...");
        if (stopNetwork()) {
            // Wait a moment for cleanup
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return startNetwork();
        }
        return false;
    }
    
    // ================================
    // PEER MANAGEMENT
    // ================================
    
    /**
     * Connect to a peer
     */
    public boolean connectToPeer(String host, int port) {
        InetSocketAddress address = new InetSocketAddress(host, port);
        return connectToPeer(address);
    }
    
    /**
     * Connect to a peer by address
     */
    public boolean connectToPeer(InetSocketAddress address) {
        if (address.equals(nodeAddress)) {
            LOGGER.warning("Cannot connect to self");
            return false;
        }
        
        if (connectedPeers.size() >= maxPeers) {
            LOGGER.warning("Maximum peer connections reached");
            return false;
        }
        
        try {
            LOGGER.info("Connecting to peer: " + address);
            
            // Create peer object
            String peerId = "peer-" + address.getHostString() + "-" + address.getPort();
            Peer peer = new Peer(peerId, address.getHostString(), address.getPort());
            
            // Create local transport for this peer
            LocalTransport peerTransport = new LocalTransport(address);
            
            // Connect transports
            localTransport.connect(peerTransport);
            
            // Store connections
            connectedPeers.put(peerId, peer);
            localTransports.put(peerId, peerTransport);
            knownAddresses.add(address);
            
            // Update metrics
            networkMetrics.recordPeerConnection(peerId);
            
            // Notify listeners
            notifyPeerConnected(peerId, address);
            
            // Send status message
            sendStatusMessage(peerId);
            
            LOGGER.info("Connected to peer: " + peerId);
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Failed to connect to peer " + address + ": " + e.getMessage());
            notifyNetworkError("Failed to connect to peer: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Disconnect from a peer
     */
    public boolean disconnectFromPeer(String peerId) {
        try {
            Peer peer = connectedPeers.remove(peerId);
            if (peer == null) {
                LOGGER.warning("Peer not found: " + peerId);
                return false;
            }
            
            // Close transport
            LocalTransport transport = localTransports.remove(peerId);
            if (transport != null) {
                transport.close();
            }
            
            // Update metrics
            networkMetrics.recordPeerDisconnection(peerId);
            
            // Notify listeners
            notifyPeerDisconnected(peerId);
            
            LOGGER.info("Disconnected from peer: " + peerId);
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Error disconnecting from peer " + peerId + ": " + e.getMessage());
            notifyNetworkError("Error disconnecting from peer: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Disconnect from all peers
     */
    public void disconnectAllPeers() {
        LOGGER.info("Disconnecting from all peers...");
        
        // Create a copy to avoid concurrent modification
        Set<String> peerIds = new HashSet<>(connectedPeers.keySet());
        
        for (String peerId : peerIds) {
            disconnectFromPeer(peerId);
        }
        
        LOGGER.info("Disconnected from all peers");
    }
    
    /**
     * Get connected peer information
     */
    public List<PeerInfo> getConnectedPeers() {
        List<PeerInfo> peerInfos = new ArrayList<>();
        
        for (Map.Entry<String, Peer> entry : connectedPeers.entrySet()) {
            String peerId = entry.getKey();
            Peer peer = entry.getValue();
            
            // Get metrics for this peer
            Map<String, Long> latencies = networkMetrics.getPeerLatencies();
            long latency = latencies.getOrDefault(peerId, 0L);
            
            String peerAddressStr = peer.getPeerAddress();
            String[] parts = peerAddressStr.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            PeerInfo info = new PeerInfo(
             peerId,
             new InetSocketAddress(host, port),
             true, // connected
            System.currentTimeMillis(), // connection time
            System.currentTimeMillis(), // last seen
             0, // messages sent
         0, // messages received
          latency,
         "Connected"
);;
            
            peerInfos.add(info);
        }
        
        return peerInfos;
    }
    
    // ================================
    // MESSAGE HANDLING
    // ================================
    
    /**
     * Send a message to a specific peer
     */
    public boolean sendMessage(String peerId, Messages.Message message) {
        try {
            LocalTransport transport = localTransports.get(peerId);
            if (transport == null) {
                LOGGER.warning("No transport found for peer: " + peerId);
                return false;
            }
            
            Peer peer = connectedPeers.get(peerId);
            if (peer == null) {
                LOGGER.warning("Peer not connected: " + peerId);
                return false;
            }
            
            // Serialize message
            byte[] messageData = message.serialize();
            
           
String peerAddressStr = peer.getPeerAddress();
String[] parts = peerAddressStr.split(":");
String host = parts[0];
int port = Integer.parseInt(parts[1]);

// Send message
transport.sendMessage(
    new InetSocketAddress(host, port),
    messageData
);
            // Update metrics
            networkMetrics.recordMessageSent(messageData.length);
            
            // Notify listeners
            notifyMessageSent(peerId, message);
            
            LOGGER.fine("Message sent to peer " + peerId + ": " + message.getClass().getSimpleName());
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Failed to send message to peer " + peerId + ": " + e.getMessage());
            notifyNetworkError("Failed to send message: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Broadcast a message to all connected peers
     */
    public int broadcastMessage(Messages.Message message) {
        if (connectedPeers.isEmpty()) {
            LOGGER.info("No peers connected for broadcast");
            return 0;
        }
        
        int successCount = 0;
        
        for (String peerId : connectedPeers.keySet()) {
            if (sendMessage(peerId, message)) {
                successCount++;
            }
        }
        
        // Notify listeners
        notifyBroadcastSent(message, successCount);
        
        LOGGER.info("Broadcast message to " + successCount + " peers: " + message.getClass().getSimpleName());
        return successCount;
    }
    
    /**
     * Send status message to peer
     */
    private void sendStatusMessage(String peerId) {
        try {
            Messages.StatusMessage statusMessage = new Messages.StatusMessage(
                nodeId,
                1, // protocol version
                blockchain != null ? blockchain.getChainLength() : 0
            );
            
            sendMessage(peerId, statusMessage);
        } catch (Exception e) {
            LOGGER.warning("Failed to send status message to " + peerId + ": " + e.getMessage());
        }
    }
    
    /**
     * Request blocks from peer
     */
    public boolean requestBlocks(String peerId, int from, int to) {
        try {
            Messages.GetBlocksMessage request = new Messages.GetBlocksMessage(from, to);
            return sendMessage(peerId, request);
        } catch (Exception e) {
            LOGGER.severe("Failed to request blocks from " + peerId + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Send blocks to peer
     */
    public boolean sendBlocks(String peerId, List<Block> blocks) {
        try {
            Messages.BlocksMessage blocksMessage = new Messages.BlocksMessage(blocks);
            return sendMessage(peerId, blocksMessage);
        } catch (Exception e) {
            LOGGER.severe("Failed to send blocks to " + peerId + ": " + e.getMessage());
            return false;
        }
    }
    
    // ================================
    // MESSAGE PROCESSING
    // ================================
    
    /**
     * Start message processing service
     */
    private void startMessageProcessing() {
        executorService.submit(() -> {
            LOGGER.info("Message processing started");
            
            while (networkStarted && !Thread.currentThread().isInterrupted()) {
                try {
                    // Process messages from local transport
                    processIncomingMessages();
                    
                    // Small delay to prevent busy waiting
                    Thread.sleep(100);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.severe("Error in message processing: " + e.getMessage());
                }
            }
            
            LOGGER.info("Message processing stopped");
        });
    }
    
    /**
     * Process incoming messages
     */
    private void processIncomingMessages() {
        try {
            // Check for messages on local transport
            LocalTransport.RPC rpc = localTransport.receiveWithTimeout(1000);
            
            if (rpc != null) {
                processMessage(rpc);
            }
            
        } catch (Exception e) {
            LOGGER.warning("Error processing incoming messages: " + e.getMessage());
        }
    }
    
    /**
     * Process a single message
     */
    private void processMessage(LocalTransport.RPC rpc) {
        try {
            // Read message data
            byte[] messageData = rpc.getPayload().readAllBytes();
            
            // Deserialize message
            Messages.Message message = Messages.Message.deserialize(messageData);
            
            // Update metrics
            networkMetrics.recordMessageReceived(messageData.length);
            
            // Find sender peer ID
            String senderPeerId = findPeerByAddress(rpc.getFrom());
            
            // Handle message based on type
            handleMessage(senderPeerId, message);
            
            // Notify listeners
            if (senderPeerId != null) {
                notifyMessageReceived(senderPeerId, message);
            }
            
        } catch (Exception e) {
            LOGGER.warning("Error processing message: " + e.getMessage());
        }
    }
    
    /**
     * Handle specific message types
     */
    private void handleMessage(String senderPeerId, Messages.Message message) {
        try {
            if (message instanceof Messages.GetStatusMessage) {
                handleGetStatusMessage(senderPeerId);
                
            } else if (message instanceof Messages.StatusMessage) {
                handleStatusMessage(senderPeerId, (Messages.StatusMessage) message);
                
            } else if (message instanceof Messages.GetBlocksMessage) {
                handleGetBlocksMessage(senderPeerId, (Messages.GetBlocksMessage) message);
                
            } else if (message instanceof Messages.BlocksMessage) {
                handleBlocksMessage(senderPeerId, (Messages.BlocksMessage) message);
                
            } else {
                LOGGER.warning("Unknown message type: " + message.getClass().getSimpleName());
            }
        } catch (Exception e) {
            LOGGER.severe("Error handling message: " + e.getMessage());
        }
    }
    
    private void handleGetStatusMessage(String senderPeerId) {
        LOGGER.info("Received GetStatus from " + senderPeerId);
        sendStatusMessage(senderPeerId);
    }
    
    private void handleStatusMessage(String senderPeerId, Messages.StatusMessage message) {
        LOGGER.info("Received Status from " + senderPeerId + 
                   ": id=" + message.getId() + 
                   ", height=" + message.getCurrentHeight());
    }
    
    private void handleGetBlocksMessage(String senderPeerId, Messages.GetBlocksMessage message) {
        LOGGER.info("Received GetBlocks from " + senderPeerId + 
                   ": from=" + message.getFrom() + 
                   ", to=" + message.getTo());
        
        // If we have a blockchain, send the requested blocks
        if (blockchain != null) {
            List<Block> requestedBlocks = new ArrayList<>();
            int start = message.getFrom();
            int end = message.getTo() == 0 ? blockchain.getChainLength() : message.getTo();
            
            for (int i = start; i < end && i < blockchain.getChainLength(); i++) {
                Block block = blockchain.getBlockByIndex(i);
                if (block != null) {
                    requestedBlocks.add(block);
                }
            }
            
            if (!requestedBlocks.isEmpty()) {
                sendBlocks(senderPeerId, requestedBlocks);
            }
        }
    }
    
    private void handleBlocksMessage(String senderPeerId, Messages.BlocksMessage message) {
        LOGGER.info("Received " + message.getBlocks().size() + " blocks from " + senderPeerId);
        
        // Process received blocks (could add to blockchain if valid)
        // This would integrate with the consensus module
    }
    
    /**
     * Find peer ID by address
     */
    private String findPeerByAddress(InetSocketAddress address) {
    for (Map.Entry<String, Peer> entry : connectedPeers.entrySet()) {
        Peer peer = entry.getValue();
        String peerAddressStr = peer.getPeerAddress();
        String[] parts = peerAddressStr.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        
        if (address.getHostString().equals(host) && 
            address.getPort() == port) {
            return entry.getKey();
        }
    }
    return null;
}
    // ================================
    // NETWORK SERVICES
    // ================================
    
    /**
     * Start heartbeat service
     */
    private void startHeartbeatService() {
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                sendHeartbeats();
            } catch (Exception e) {
                LOGGER.warning("Error in heartbeat service: " + e.getMessage());
            }
        }, heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Send heartbeat messages to all peers
     */
    private void sendHeartbeats() {
        if (!networkStarted || connectedPeers.isEmpty()) {
            return;
        }
        
        LOGGER.fine("Sending heartbeats to " + connectedPeers.size() + " peers");
        
        for (String peerId : connectedPeers.keySet()) {
            sendStatusMessage(peerId);
        }
    }
    
    // ================================
    // NETWORK STATUS AND METRICS
    // ================================
    
    /**
     * Get current network status
     */
    public NetworkStatus getNetworkStatus() {
        return new NetworkStatus(
            networkStarted,
            connectedPeers.size(),
            knownAddresses.size(),
            networkMetrics.getMessagesSent(),
            networkMetrics.getMessagesReceived(),
            networkMetrics.getBytesSent(),
            networkMetrics.getBytesReceived(),
            nodeId,
            nodeAddress,
            discoveryEnabled
        );
    }
    
    /**
     * Get network metrics
     */
    public NetworkMetrics getNetworkMetrics() {
        return networkMetrics;
    }
    
    /**
     * Check if network is active
     */
    public boolean isNetworkActive() {
        return networkStarted;
    }
    
    /**
     * Get connected peer count
     */
    public int getConnectedPeerCount() {
        return connectedPeers.size();
    }
    
    // ================================
    // CONFIGURATION
    // ================================
    
    /**
     * Set maximum number of peers
     */
    public void setMaxPeers(int maxPeers) {
        if (maxPeers > 0) {
            this.maxPeers = maxPeers;
            LOGGER.info("Max peers set to: " + maxPeers);
        }
    }
    
    /**
     * Set heartbeat interval
     */
    public void setHeartbeatInterval(long intervalMs) {
        if (intervalMs > 0) {
            this.heartbeatInterval = intervalMs;
            LOGGER.info("Heartbeat interval set to: " + intervalMs + "ms");
        }
    }
    
    /**
     * Enable/disable peer discovery
     */
    public void setDiscoveryEnabled(boolean enabled) {
        this.discoveryEnabled = enabled;
        LOGGER.info("Peer discovery " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Set maximum peer connections
     */
    public void setMaxPeerConnections(int maxPeers) {
        if (maxPeers > 0) {
            this.maxPeers = maxPeers;
            LOGGER.info("Max peer connections set to: " + maxPeers);
        }
    }

    /**
     * Set discovery interval (milliseconds)
     */
    public void setDiscoveryInterval(int interval) {
        if (interval > 0) {
            // This would be used if we had a discovery service
            LOGGER.info("Discovery interval set to: " + interval + "ms");
            // Could be implemented to schedule peer discovery at this interval
        }
    }
    
    // ================================
    // BLOCKCHAIN INTEGRATION
    // ================================
    
    /**
     * Set blockchain instance
     */
    public void setBlockchain(Blockchain blockchain) {
        this.blockchain = blockchain;
        LOGGER.info("Blockchain instance connected to network controller");
    }
    
    // ================================
    // EVENT HANDLING
    // ================================
    
    /**
     * Add event listener
     */
    public void addEventListener(NetworkEventListener listener) {
        if (listener != null && !eventListeners.contains(listener)) {
            eventListeners.add(listener);
        }
    }
    
    /**
     * Remove event listener
     */
    public void removeEventListener(NetworkEventListener listener) {
        eventListeners.remove(listener);
    }
    
    /**
     * Notify listeners of peer connection
     */
    private void notifyPeerConnected(String peerId, InetSocketAddress address) {
        for (NetworkEventListener listener : eventListeners) {
            try {
                listener.onPeerConnected(peerId, address);
            } catch (Exception e) {
                LOGGER.warning("Error notifying listener of peer connection: " + e.getMessage());
            }
        }
    }
    
    /**
     * Notify listeners of peer disconnection
     */
    private void notifyPeerDisconnected(String peerId) {
        for (NetworkEventListener listener : eventListeners) {
            try {
                listener.onPeerDisconnected(peerId);
            } catch (Exception e) {
                LOGGER.warning("Error notifying listener of peer disconnection: " + e.getMessage());
            }
        }
    }
    
    /**
     * Notify listeners of message received
     */
    private void notifyMessageReceived(String fromPeer, Messages.Message message) {
        for (NetworkEventListener listener : eventListeners) {
            try {
                listener.onMessageReceived(fromPeer, message);
            } catch (Exception e) {
                LOGGER.warning("Error notifying listener of message received: " + e.getMessage());
            }
        }
    }
    
    /**
     * Notify listeners of message sent
     */
    private void notifyMessageSent(String toPeer, Messages.Message message) {
        for (NetworkEventListener listener : eventListeners) {
            try {
                listener.onMessageSent(toPeer, message);
            } catch (Exception e) {
                LOGGER.warning("Error notifying listener of message sent: " + e.getMessage());
            }
        }
    }
    
    /**
     * Notify listeners of broadcast sent
     */
    private void notifyBroadcastSent(Messages.Message message, int peerCount) {
        for (NetworkEventListener listener : eventListeners) {
            try {
                listener.onBroadcastSent(message, peerCount);
            } catch (Exception e) {
                LOGGER.warning("Error notifying listener of broadcast: " + e.getMessage());
            }
        }
    }
    
    /**
     * Notify listeners of network error
     */
    private void notifyNetworkError(String error) {
        for (NetworkEventListener listener : eventListeners) {
            try {
                listener.onNetworkError(error);
            } catch (Exception e) {
                LOGGER.warning("Error notifying listener of network error: " + e.getMessage());
            }
        }
    }
    
    /**
     * Generic network event notification
     */
    private void notifyNetworkEvent(String event) {
        LOGGER.info("Network event: " + event);
    }
    
    // ================================
    // CLEANUP
    // ================================
    
    /**
     * Shutdown the network controller
     */
    public void shutdown() {
        LOGGER.info("Shutting down network controller...");
        
        stopNetwork();
        
        // Shutdown executor services
        if (!executorService.isShutdown()) {
            executorService.shutdown();
        }
        
        if (!scheduledExecutorService.isShutdown()) {
            scheduledExecutorService.shutdown();
        }
        
        // Clear collections
        connectedPeers.clear();
        localTransports.clear();
        knownAddresses.clear();
        eventListeners.clear();
        
        LOGGER.info("Network controller shutdown complete");
    }
}