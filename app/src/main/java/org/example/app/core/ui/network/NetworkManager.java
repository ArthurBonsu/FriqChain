package org.example.app.core.ui.network;

import org.example.app.core.network.LocalTransport;
import org.example.app.core.network.Messages;
import org.example.app.core.metrics.NetworkMetrics;
import org.example.app.core.p2p.PeerNetwork;
import org.example.app.core.p2p.Peer;
import org.example.app.core.internal.NodeDiscovery;
import org.example.app.core.config.NetworkConfig;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * NetworkManager handles network state management, peer discovery, 
 * connection policies, and network optimization for the TINC blockchain.
 */
public class NetworkManager {
    private static final Logger LOGGER = Logger.getLogger(NetworkManager.class.getName());
    
    // Network Controller Integration
    private NetworkController networkController;
    
    // Network State Management
    private final NetworkState networkState;
    private final ConnectionPolicy connectionPolicy;
    private final PeerDiscoveryManager discoveryManager;
    private final NetworkHealthMonitor healthMonitor;
    
    // Configuration
    private NetworkConfig networkConfig;
    private final NetworkSettings settings;
    
    // Event Management
    private final List<NetworkManagerEventListener> eventListeners;
    
    // Services
    private final ScheduledExecutorService schedulerService;
    private final ExecutorService workerService;
    
    /**
     * Network Manager Event Listener
     */
    public interface NetworkManagerEventListener {
        void onNetworkStateChanged(NetworkState.StateType oldState, NetworkState.StateType newState);
        void onPeerDiscovered(String peerId, InetSocketAddress address);
        void onConnectionPolicyChanged(ConnectionPolicy.PolicyType policyType);
        void onNetworkHealthChanged(NetworkHealthStatus health);
        void onNetworkOptimization(String optimization);
        void onNetworkManagerError(String error);
    }
    
    /**
     * Network State Management
     */
    public static class NetworkState {
        public enum StateType {
            STOPPED("Stopped", "Network is not running"),
            STARTING("Starting", "Network is initializing"),
            ACTIVE("Active", "Network is running normally"),
            DISCOVERING("Discovering", "Searching for peers"),
            CONNECTING("Connecting", "Establishing connections"),
            DEGRADED("Degraded", "Network performance is reduced"),
            ERROR("Error", "Network has encountered errors");
            
            private final String displayName;
            private final String description;
            
            StateType(String displayName, String description) {
                this.displayName = displayName;
                this.description = description;
            }
            
            public String getDisplayName() { return displayName; }
            public String getDescription() { return description; }
        }
        
        private StateType currentState;
        private String stateMessage;
        private long stateChangeTime;
        
        public NetworkState() {
            this.currentState = StateType.STOPPED;
            this.stateMessage = "Network initialized but not started";
            this.stateChangeTime = System.currentTimeMillis();
        }
        
        public synchronized void setState(StateType newState, String message) {
            StateType oldState = this.currentState;
            this.currentState = newState;
            this.stateMessage = message;
            this.stateChangeTime = System.currentTimeMillis();
            
            LOGGER.info("Network state changed: " + oldState + " -> " + newState + " (" + message + ")");
        }
        
        public StateType getCurrentState() { return currentState; }
        public String getStateMessage() { return stateMessage; }
        public long getStateChangeTime() { return stateChangeTime; }
    }
    
    /**
     * Connection Policy Management
     */
    public static class ConnectionPolicy {
        public enum PolicyType {
            CONSERVATIVE("Conservative", "Connect to trusted peers only"),
            BALANCED("Balanced", "Balance between security and connectivity"),
            AGGRESSIVE("Aggressive", "Connect to maximum number of peers"),
            CUSTOM("Custom", "User-defined connection rules");
            
            private final String displayName;
            private final String description;
            
            PolicyType(String displayName, String description) {
                this.displayName = displayName;
                this.description = description;
            }
            
            public String getDisplayName() { return displayName; }
            public String getDescription() { return description; }
        }
        
        private PolicyType policyType;
        private int maxConnections;
        private int minConnections;
        private boolean allowIncoming;
        private boolean enableDiscovery;
        private Set<String> trustedPeers;
        private Set<String> blockedPeers;
        
        public ConnectionPolicy() {
            this.policyType = PolicyType.BALANCED;
            this.maxConnections = 50;
            this.minConnections = 3;
            this.allowIncoming = true;
            this.enableDiscovery = true;
            this.trustedPeers = new HashSet<>();
            this.blockedPeers = new HashSet<>();
        }
        
        // Getters and setters
        public PolicyType getPolicyType() { return policyType; }
        public void setPolicyType(PolicyType policyType) { this.policyType = policyType; }
        public int getMaxConnections() { return maxConnections; }
        public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }
        public int getMinConnections() { return minConnections; }
        public void setMinConnections(int minConnections) { this.minConnections = minConnections; }
        public boolean isAllowIncoming() { return allowIncoming; }
        public void setAllowIncoming(boolean allowIncoming) { this.allowIncoming = allowIncoming; }
        public boolean isEnableDiscovery() { return enableDiscovery; }
        public void setEnableDiscovery(boolean enableDiscovery) { this.enableDiscovery = enableDiscovery; }
        public Set<String> getTrustedPeers() { return new HashSet<>(trustedPeers); }
        public Set<String> getBlockedPeers() { return new HashSet<>(blockedPeers); }
        
        public void addTrustedPeer(String peerId) { trustedPeers.add(peerId); }
        public void removeTrustedPeer(String peerId) { trustedPeers.remove(peerId); }
        public void addBlockedPeer(String peerId) { blockedPeers.add(peerId); }
        public void removeBlockedPeer(String peerId) { blockedPeers.remove(peerId); }
        
        public boolean isPeerAllowed(String peerId) {
            if (blockedPeers.contains(peerId)) {
                return false;
            }
            if (policyType == PolicyType.CONSERVATIVE) {
                return trustedPeers.contains(peerId);
            }
            return true;
        }
    }
    
    /**
     * Peer Discovery Management
     */
    public static class PeerDiscoveryManager {
        private final Set<InetSocketAddress> discoveredPeers;
        private final Map<String, Long> discoveryTimes;
        private final NodeDiscovery nodeDiscovery;
        private boolean discoveryActive;
        
        public PeerDiscoveryManager() {
            this.discoveredPeers = ConcurrentHashMap.newKeySet();
            this.discoveryTimes = new ConcurrentHashMap<>();
            this.nodeDiscovery = new NodeDiscovery();
            this.discoveryActive = false;
        }
        
        public void startDiscovery() {
            if (!discoveryActive) {
                discoveryActive = true;
                LOGGER.info("Peer discovery started");
            }
        }
        
        public void stopDiscovery() {
            if (discoveryActive) {
                discoveryActive = false;
                LOGGER.info("Peer discovery stopped");
            }
        }
        
        public void addDiscoveredPeer(InetSocketAddress address) {
            if (discoveredPeers.add(address)) {
                String peerId = "discovered-" + address.getHostString() + "-" + address.getPort();
                discoveryTimes.put(peerId, System.currentTimeMillis());
                LOGGER.info("New peer discovered: " + address);
            }
        }
        
        public Set<InetSocketAddress> getDiscoveredPeers() {
            return new HashSet<>(discoveredPeers);
        }
        
        public boolean isDiscoveryActive() {
            return discoveryActive;
        }
        
        public int getDiscoveredPeerCount() {
            return discoveredPeers.size();
        }
    }
    
    /**
     * Network Health Monitoring
     */
    public static class NetworkHealthStatus {
        public final double overallHealth;
        public final double connectivityHealth;
        public final double performanceHealth;
        public final double stabilityHealth;
        public final int connectedPeers;
        public final int targetPeers;
        public final long avgLatency;
        public final double messageSuccessRate;
        public final String healthMessage;
        
        public NetworkHealthStatus(double overallHealth, double connectivityHealth,
                                 double performanceHealth, double stabilityHealth,
                                 int connectedPeers, int targetPeers, long avgLatency,
                                 double messageSuccessRate, String healthMessage) {
            this.overallHealth = overallHealth;
            this.connectivityHealth = connectivityHealth;
            this.performanceHealth = performanceHealth;
            this.stabilityHealth = stabilityHealth;
            this.connectedPeers = connectedPeers;
            this.targetPeers = targetPeers;
            this.avgLatency = avgLatency;
            this.messageSuccessRate = messageSuccessRate;
            this.healthMessage = healthMessage;
        }
        
        public String getHealthLevel() {
            if (overallHealth >= 0.9) return "Excellent";
            if (overallHealth >= 0.7) return "Good";
            if (overallHealth >= 0.5) return "Fair";
            if (overallHealth >= 0.3) return "Poor";
            return "Critical";
        }
        
        @Override
        public String toString() {
            return String.format("NetworkHealth{overall=%.2f, connectivity=%.2f, performance=%.2f, peers=%d/%d}",
                               overallHealth, connectivityHealth, performanceHealth, connectedPeers, targetPeers);
        }
    }
    
    /**
     * Network Health Monitor
     */
    public static class NetworkHealthMonitor {
        private NetworkHealthStatus currentHealth;
        private final Queue<Double> healthHistory;
        private static final int HISTORY_SIZE = 100;
        
        public NetworkHealthMonitor() {
            this.healthHistory = new LinkedList<>();
            this.currentHealth = new NetworkHealthStatus(
                0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0.0, "Not initialized"
            );
        }
        
        public synchronized void updateHealth(NetworkController networkController, 
                                            ConnectionPolicy policy) {
            try {
                // Calculate connectivity health
                int connected = networkController.getConnectedPeerCount();
                int target = policy.getMaxConnections();
                double connectivityHealth = Math.min(1.0, (double) connected / Math.max(1, policy.getMinConnections()));
                
                // Calculate performance health (simplified)
                NetworkController.NetworkStatus status = networkController.getNetworkStatus();
                double performanceHealth = 0.8; // Placeholder - would use actual metrics
                
                // Calculate stability health (simplified)
                double stabilityHealth = 0.9; // Placeholder - would track disconnections
                
                // Calculate overall health
                double overallHealth = (connectivityHealth + performanceHealth + stabilityHealth) / 3.0;
                
                // Create health status
                currentHealth = new NetworkHealthStatus(
                    overallHealth,
                    connectivityHealth,
                    performanceHealth,
                    stabilityHealth,
                    connected,
                    target,
                    0, // avgLatency - placeholder
                    0.95, // messageSuccessRate - placeholder
                    generateHealthMessage(overallHealth)
                );
                
                // Update history
                healthHistory.offer(overallHealth);
                if (healthHistory.size() > HISTORY_SIZE) {
                    healthHistory.poll();
                }
                
            } catch (Exception e) {
                LOGGER.warning("Error updating network health: " + e.getMessage());
            }
        }
        
        private String generateHealthMessage(double health) {
            if (health >= 0.9) return "Network is operating optimally";
            if (health >= 0.7) return "Network is performing well";
            if (health >= 0.5) return "Network performance is acceptable";
            if (health >= 0.3) return "Network needs attention";
            return "Network requires immediate action";
        }
        
        public NetworkHealthStatus getCurrentHealth() {
            return currentHealth;
        }
        
        public List<Double> getHealthHistory() {
            return new ArrayList<>(healthHistory);
        }
    }
    
    /**
     * Network Settings
     */
    public static class NetworkSettings {
        public int discoveryInterval = 30000; // 30 seconds
        public int healthCheckInterval = 10000; // 10 seconds
        public int connectionTimeout = 5000; // 5 seconds
        public int maxRetries = 3;
        public boolean autoOptimize = true;
        public boolean logNetworkEvents = true;
        
        // Getters and setters
        public int getDiscoveryInterval() { return discoveryInterval; }
        public void setDiscoveryInterval(int interval) { this.discoveryInterval = interval; }
        public int getHealthCheckInterval() { return healthCheckInterval; }
        public void setHealthCheckInterval(int interval) { this.healthCheckInterval = interval; }
        public int getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(int timeout) { this.connectionTimeout = timeout; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int retries) { this.maxRetries = retries; }
        public boolean isAutoOptimize() { return autoOptimize; }
        public void setAutoOptimize(boolean autoOptimize) { this.autoOptimize = autoOptimize; }
        public boolean isLogNetworkEvents() { return logNetworkEvents; }
        public void setLogNetworkEvents(boolean logEvents) { this.logNetworkEvents = logEvents; }
    }
    
    /**
     * Constructor
     */
    public NetworkManager() {
        this.networkState = new NetworkState();
        this.connectionPolicy = new ConnectionPolicy();
        this.discoveryManager = new PeerDiscoveryManager();
        this.healthMonitor = new NetworkHealthMonitor();
        this.settings = new NetworkSettings();
        this.eventListeners = new ArrayList<>();
        
        this.schedulerService = Executors.newScheduledThreadPool(3);
        this.workerService = Executors.newCachedThreadPool();
        
        LOGGER.info("NetworkManager initialized");
    }
    
    // ================================
    // NETWORK CONTROLLER INTEGRATION
    // ================================
    
    /**
     * Set network controller
     */
    public void setNetworkController(NetworkController controller) {
        this.networkController = controller;
        
        if (controller != null) {
            // Add event listener to network controller
            controller.addEventListener(new NetworkController.NetworkEventListener() {
                @Override
                public void onPeerConnected(String peerId, InetSocketAddress address) {
                    handlePeerConnected(peerId, address);
                }
                
                @Override
                public void onPeerDisconnected(String peerId) {
                    handlePeerDisconnected(peerId);
                }
                
                @Override
                public void onMessageReceived(String fromPeer, Messages.Message message) {
                    handleMessageReceived(fromPeer, message);
                }
                
                @Override
                public void onMessageSent(String toPeer, Messages.Message message) {
                    handleMessageSent(toPeer, message);
                }
                
                @Override
                public void onBroadcastSent(Messages.Message message, int peerCount) {
                    handleBroadcastSent(message, peerCount);
                }
                
                @Override
                public void onNetworkError(String error) {
                    handleNetworkError(error);
                }
            });
            
            LOGGER.info("NetworkController integrated with NetworkManager");
        }
    }
    
    // ================================
    // NETWORK LIFECYCLE MANAGEMENT
    // ================================
    
    /**
     * Start network management
     */
    public boolean startNetworkManagement() {
        try {
            LOGGER.info("Starting network management...");
            
            // Set network state
            networkState.setState(NetworkState.StateType.STARTING, "Initializing network management");
            
            // Start network controller if available
            if (networkController != null && !networkController.isNetworkActive()) {
                if (!networkController.startNetwork()) {
                    throw new Exception("Failed to start network controller");
                }
            }
            
            // Start discovery if enabled
            if (connectionPolicy.isEnableDiscovery()) {
                startPeerDiscovery();
            }
            
            // Start health monitoring
            startHealthMonitoring();
            
            // Start connection management
            startConnectionManagement();
            
            // Set network state
            networkState.setState(NetworkState.StateType.ACTIVE, "Network management active");
            
            // Notify listeners
            notifyNetworkStateChanged(NetworkState.StateType.STARTING, NetworkState.StateType.ACTIVE);
            
            LOGGER.info("Network management started successfully");
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Failed to start network management: " + e.getMessage());
            networkState.setState(NetworkState.StateType.ERROR, "Failed to start: " + e.getMessage());
            notifyNetworkManagerError("Failed to start network management: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Stop network management
     */
    public boolean stopNetworkManagement() {
        try {
            LOGGER.info("Stopping network management...");
            
            // Stop discovery
            discoveryManager.stopDiscovery();
            
            // Stop network controller
            if (networkController != null && networkController.isNetworkActive()) {
                networkController.stopNetwork();
            }
            
            // Set network state
            networkState.setState(NetworkState.StateType.STOPPED, "Network management stopped");
            
            LOGGER.info("Network management stopped successfully");
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Error stopping network management: " + e.getMessage());
            notifyNetworkManagerError("Error stopping network management: " + e.getMessage());
            return false;
        }
    }
    
    // ================================
    // PEER DISCOVERY MANAGEMENT
    // ================================
    
    /**
     * Start peer discovery
     */
    public void startPeerDiscovery() {
        if (discoveryManager.isDiscoveryActive()) {
            return;
        }
        
        discoveryManager.startDiscovery();
        networkState.setState(NetworkState.StateType.DISCOVERING, "Searching for peers");
        
        // Schedule discovery process
        schedulerService.scheduleAtFixedRate(
            this::performPeerDiscovery,
            0,
            settings.getDiscoveryInterval(),
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * Stop peer discovery
     */
    public void stopPeerDiscovery() {
        discoveryManager.stopDiscovery();
    }
    
    /**
     * Perform peer discovery
     */
    private void performPeerDiscovery() {
        try {
            if (!discoveryManager.isDiscoveryActive()) {
                return;
            }
            
            LOGGER.fine("Performing peer discovery...");
            
            // Simulate peer discovery (in real implementation, would use NodeDiscovery)
            // For now, add some default peer addresses for testing
            List<InetSocketAddress> potentialPeers = Arrays.asList(
                new InetSocketAddress("127.0.0.1", 8081),
                new InetSocketAddress("127.0.0.1", 8082),
                new InetSocketAddress("127.0.0.1", 8083)
            );
            
            for (InetSocketAddress address : potentialPeers) {
                discoveryManager.addDiscoveredPeer(address);
                notifyPeerDiscovered("discovered-" + address.getPort(), address);
            }
            
            // Try to connect to discovered peers if we need more connections
            attemptConnectionsToDiscoveredPeers();
            
        } catch (Exception e) {
            LOGGER.warning("Error during peer discovery: " + e.getMessage());
        }
    }
    
    /**
     * Attempt connections to discovered peers
     */
    private void attemptConnectionsToDiscoveredPeers() {
        if (networkController == null) {
            return;
        }
        
        int currentConnections = networkController.getConnectedPeerCount();
        int maxConnections = connectionPolicy.getMaxConnections();
        
        if (currentConnections >= maxConnections) {
            return; // Already at max connections
        }
        
        Set<InetSocketAddress> discovered = discoveryManager.getDiscoveredPeers();
        int connectionsNeeded = maxConnections - currentConnections;
        
        for (InetSocketAddress address : discovered) {
            if (connectionsNeeded <= 0) {
                break;
            }
            
            // Check if we're already connected to this peer
            String peerId = "peer-" + address.getHostString() + "-" + address.getPort();
            if (connectionPolicy.isPeerAllowed(peerId)) {
                workerService.submit(() -> {
                    if (networkController.connectToPeer(address)) {
                        LOGGER.info("Connected to discovered peer: " + address);
                    }
                });
                connectionsNeeded--;
            }
        }
    }
    
    // ================================
    // CONNECTION MANAGEMENT
    // ================================
    
    /**
     * Start connection management
     */
    private void startConnectionManagement() {
        // Schedule connection maintenance
        schedulerService.scheduleAtFixedRate(
            this::maintainConnections,
            30000, // 30 second delay
            60000, // 1 minute interval
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * Maintain optimal connections
     */
    private void maintainConnections() {
        try {
            if (networkController == null) {
                return;
            }
            
            int connected = networkController.getConnectedPeerCount();
            int min = connectionPolicy.getMinConnections();
            int max = connectionPolicy.getMaxConnections();
            
            LOGGER.fine("Connection maintenance - Current: " + connected + 
                       ", Min: " + min + ", Max: " + max);
            
            // If below minimum, try to connect to more peers
            if (connected < min) {
                networkState.setState(NetworkState.StateType.CONNECTING, 
                                    "Below minimum connections, seeking peers");
                
                if (connectionPolicy.isEnableDiscovery()) {
                    performPeerDiscovery();
                }
                
                attemptConnectionsToDiscoveredPeers();
            }
            // If above maximum, disconnect from some peers (least priority first)
            else if (connected > max) {
                disconnectExcessPeers(connected - max);
            }
            // If within range, set to active state
            else if (networkState.getCurrentState() == NetworkState.StateType.CONNECTING) {
                networkState.setState(NetworkState.StateType.ACTIVE, "Optimal peer connections maintained");
            }
            
        } catch (Exception e) {
            LOGGER.warning("Error in connection maintenance: " + e.getMessage());
        }
    }
    
    /**
     * Disconnect excess peers
     */
    private void disconnectExcessPeers(int excessCount) {
        if (networkController == null || excessCount <= 0) {
            return;
        }
        
        List<NetworkController.PeerInfo> peers = networkController.getConnectedPeers();
        
        // Sort by some criteria (for now, just by connection time - newest first)
        peers.sort((p1, p2) -> Long.compare(p2.connectionTime, p1.connectionTime));
        
        int disconnected = 0;
        for (NetworkController.PeerInfo peer : peers) {
            if (disconnected >= excessCount) {
                break;
            }
            
            // Don't disconnect trusted peers
            if (connectionPolicy.getTrustedPeers().contains(peer.peerId)) {
                continue;
            }
            
            if (networkController.disconnectFromPeer(peer.peerId)) {
                disconnected++;
                LOGGER.info("Disconnected excess peer: " + peer.peerId);
            }
        }
    }
    
    // ================================
    // HEALTH MONITORING
    // ================================
    
    /**
     * Start health monitoring
     */
    private void startHealthMonitoring() {
        schedulerService.scheduleAtFixedRate(
            this::updateNetworkHealth,
            settings.getHealthCheckInterval(),
            settings.getHealthCheckInterval(),
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * Update network health
     */
    private void updateNetworkHealth() {
        try {
            if (networkController == null) {
                return;
            }
            
            NetworkHealthStatus oldHealth = healthMonitor.getCurrentHealth();
            healthMonitor.updateHealth(networkController, connectionPolicy);
            NetworkHealthStatus newHealth = healthMonitor.getCurrentHealth();
            
            // Notify if health level changed significantly
            if (Math.abs(oldHealth.overallHealth - newHealth.overallHealth) > 0.1) {
                notifyNetworkHealthChanged(newHealth);
            }
            
            // Auto-optimize if enabled and health is poor
            if (settings.isAutoOptimize() && newHealth.overallHealth < 0.5) {
                performNetworkOptimization(newHealth);
            }
            
        } catch (Exception e) {
            LOGGER.warning("Error updating network health: " + e.getMessage());
        }
    }
    
    /**
     * Perform network optimization
     */
    private void performNetworkOptimization(NetworkHealthStatus health) {
        try {
            LOGGER.info("Performing network optimization - Health: " + health.getHealthLevel());
            
            List<String> optimizations = new ArrayList<>();
            
            // Connectivity optimization
            if (health.connectivityHealth < 0.5) {
                if (connectionPolicy.isEnableDiscovery()) {
                    performPeerDiscovery();
                    optimizations.add("Enhanced peer discovery");
                }
                
                // Increase connection limits temporarily
                if (connectionPolicy.getMaxConnections() < 100) {
                    connectionPolicy.setMaxConnections(connectionPolicy.getMaxConnections() + 10);
                    optimizations.add("Increased connection limit");
                }
            }
            
            // Performance optimization
            if (health.performanceHealth < 0.5) {
                // Could implement message prioritization, compression, etc.
                optimizations.add("Performance tuning applied");
            }
            
            if (!optimizations.isEmpty()) {
                String optimization = "Applied optimizations: " + String.join(", ", optimizations);
                notifyNetworkOptimization(optimization);
                LOGGER.info("Network optimization completed: " + optimization);
            }
            
        } catch (Exception e) {
            LOGGER.warning("Error in network optimization: " + e.getMessage());
        }
    }
    
    // ================================
    // CONNECTION POLICY MANAGEMENT
    // ================================
    
    /**
     * Set connection policy
     */
    public void setConnectionPolicy(ConnectionPolicy.PolicyType policyType) {
        ConnectionPolicy.PolicyType oldType = connectionPolicy.getPolicyType();
        connectionPolicy.setPolicyType(policyType);
        
        // Apply policy-specific settings
        switch (policyType) {
            case CONSERVATIVE:
                connectionPolicy.setMaxConnections(10);
                connectionPolicy.setMinConnections(2);
                connectionPolicy.setEnableDiscovery(false);
                break;
            case BALANCED:
                connectionPolicy.setMaxConnections(25);
                connectionPolicy.setMinConnections(5);
                connectionPolicy.setEnableDiscovery(true);
                break;
            case AGGRESSIVE:
                connectionPolicy.setMaxConnections(100);
                connectionPolicy.setMinConnections(10);
                connectionPolicy.setEnableDiscovery(true);
                break;
            case CUSTOM:
                // Don't change settings for custom policy
                break;
        }
        
        notifyConnectionPolicyChanged(policyType);
        LOGGER.info("Connection policy changed from " + oldType + " to " + policyType);
    }
    
    /**
     * Add trusted peer
     */
    public void addTrustedPeer(String peerId) {
        connectionPolicy.addTrustedPeer(peerId);
        LOGGER.info("Added trusted peer: " + peerId);
    }
    
    /**
     * Remove trusted peer
     */
    public void removeTrustedPeer(String peerId) {
        connectionPolicy.removeTrustedPeer(peerId);
        LOGGER.info("Removed trusted peer: " + peerId);
    }
    
    /**
     * Add blocked peer
     */
    public void addBlockedPeer(String peerId) {
        connectionPolicy.addBlockedPeer(peerId);
        
        // Disconnect if currently connected
        if (networkController != null) {
            networkController.disconnectFromPeer(peerId);
        }
        
        LOGGER.info("Added blocked peer: " + peerId);
    }
    
    /**
     * Remove blocked peer
     */
    public void removeBlockedPeer(String peerId) {
        connectionPolicy.removeBlockedPeer(peerId);
        LOGGER.info("Removed blocked peer: " + peerId);
    }
    
    // ================================
    // EVENT HANDLING
    // ================================
    
    /**
     * Handle peer connected event from network controller
     */
    private void handlePeerConnected(String peerId, InetSocketAddress address) {
        LOGGER.info("NetworkManager handling peer connected: " + peerId);
        
        // Update network state if we were seeking connections
        if (networkState.getCurrentState() == NetworkState.StateType.CONNECTING) {
            int connected = networkController != null ? networkController.getConnectedPeerCount() : 0;
            if (connected >= connectionPolicy.getMinConnections()) {
                networkState.setState(NetworkState.StateType.ACTIVE, "Minimum peer connections achieved");
            }
        }
    }
    
    /**
     * Handle peer disconnected event from network controller
     */
    private void handlePeerDisconnected(String peerId) {
        LOGGER.info("NetworkManager handling peer disconnected: " + peerId);
        
        // Check if we need to find more connections
        int connected = networkController != null ? networkController.getConnectedPeerCount() : 0;
        if (connected < connectionPolicy.getMinConnections()) {
            networkState.setState(NetworkState.StateType.CONNECTING, "Below minimum connections, seeking peers");
            
            // Trigger discovery if enabled
            if (connectionPolicy.isEnableDiscovery()) {
                workerService.submit(this::performPeerDiscovery);
            }
        }
    }
    
    /**
     * Handle message received event from network controller
     */
    private void handleMessageReceived(String fromPeer, Messages.Message message) {
        LOGGER.fine("NetworkManager handling message received from: " + fromPeer);
        // Could implement message filtering, logging, etc.
    }
    
    /**
     * Handle message sent event from network controller
     */
    private void handleMessageSent(String toPeer, Messages.Message message) {
        LOGGER.fine("NetworkManager handling message sent to: " + toPeer);
        // Could implement message tracking, statistics, etc.
    }
    
    /**
     * Handle broadcast sent event from network controller
     */
    private void handleBroadcastSent(Messages.Message message, int peerCount) {
        LOGGER.fine("NetworkManager handling broadcast sent to " + peerCount + " peers");
        // Could implement broadcast tracking, optimization, etc.
    }
    
    /**
     * Handle network error event from network controller
     */
    private void handleNetworkError(String error) {
        LOGGER.warning("NetworkManager handling network error: " + error);
        networkState.setState(NetworkState.StateType.ERROR, "Network error: " + error);
        notifyNetworkManagerError("Network controller error: " + error);
    }
    
    // ================================
    // STATUS AND GETTERS
    // ================================
    
    /**
     * Get current network state
     */
    public NetworkState getNetworkState() {
        return networkState;
    }
    
    /**
     * Get connection policy
     */
    public ConnectionPolicy getConnectionPolicy() {
        return connectionPolicy;
    }
    
    /**
     * Get peer discovery manager
     */
    public PeerDiscoveryManager getDiscoveryManager() {
        return discoveryManager;
    }
    
    /**
     * Get network health monitor
     */
    public NetworkHealthMonitor getHealthMonitor() {
        return healthMonitor;
    }
    
    /**
     * Get network settings
     */
    public NetworkSettings getSettings() {
        return settings;
    }
    
    /**
     * Get comprehensive network status
     */
    public NetworkManagerStatus getNetworkManagerStatus() {
        return new NetworkManagerStatus(
            networkState,
            connectionPolicy,
            healthMonitor.getCurrentHealth(),
            discoveryManager.isDiscoveryActive(),
            discoveryManager.getDiscoveredPeerCount(),
            networkController != null ? networkController.getConnectedPeerCount() : 0
        );
    }
    
    /**
     * Network Manager Status
     */
    public static class NetworkManagerStatus {
        public final NetworkState networkState;
        public final ConnectionPolicy connectionPolicy;
        public final NetworkHealthStatus healthStatus;
        public final boolean discoveryActive;
        public final int discoveredPeers;
        public final int connectedPeers;
        
        public NetworkManagerStatus(NetworkState networkState, ConnectionPolicy connectionPolicy,
                                  NetworkHealthStatus healthStatus, boolean discoveryActive,
                                  int discoveredPeers, int connectedPeers) {
            this.networkState = networkState;
            this.connectionPolicy = connectionPolicy;
            this.healthStatus = healthStatus;
            this.discoveryActive = discoveryActive;
            this.discoveredPeers = discoveredPeers;
            this.connectedPeers = connectedPeers;
        }
    }
    
    // ================================
    // EVENT LISTENER MANAGEMENT
    // ================================
    
    /**
     * Add event listener
     */
    public void addEventListener(NetworkManagerEventListener listener) {
        if (listener != null && !eventListeners.contains(listener)) {
            eventListeners.add(listener);
        }
    }
    
    /**
     * Remove event listener
     */
    public void removeEventListener(NetworkManagerEventListener listener) {
        eventListeners.remove(listener);
    }
    
    /**
     * Notify listeners of network state change
     */
    private void notifyNetworkStateChanged(NetworkState.StateType oldState, NetworkState.StateType newState) {
        for (NetworkManagerEventListener listener : eventListeners) {
            try {
                listener.onNetworkStateChanged(oldState, newState);
            } catch (Exception e) {
                LOGGER.warning("Error notifying listener of network state change: " + e.getMessage());
            }
        }
    }
    
    /**
     * Notify listeners of peer discovery
     */
    private void notifyPeerDiscovered(String peerId, InetSocketAddress address) {
        for (NetworkManagerEventListener listener : eventListeners) {
            try {
                listener.onPeerDiscovered(peerId, address);
            } catch (Exception e) {
                LOGGER.warning("Error notifying listener of peer discovery: " + e.getMessage());
            }
        }
    }
    
    /**
     * Notify listeners of connection policy change
     */
    private void notifyConnectionPolicyChanged(ConnectionPolicy.PolicyType policyType) {
        for (NetworkManagerEventListener listener : eventListeners) {
            try {
                listener.onConnectionPolicyChanged(policyType);
            } catch (Exception e) {
                LOGGER.warning("Error notifying listener of policy change: " + e.getMessage());
            }
        }
    }
    
    /**
     * Notify listeners of network health change
     */
    private void notifyNetworkHealthChanged(NetworkHealthStatus health) {
        for (NetworkManagerEventListener listener : eventListeners) {
            try {
                listener.onNetworkHealthChanged(health);
            } catch (Exception e) {
                LOGGER.warning("Error notifying listener of health change: " + e.getMessage());
            }
        }
    }
    
    /**
     * Notify listeners of network optimization
     */
    private void notifyNetworkOptimization(String optimization) {
        for (NetworkManagerEventListener listener : eventListeners) {
            try {
                listener.onNetworkOptimization(optimization);
            } catch (Exception e) {
                LOGGER.warning("Error notifying listener of optimization: " + e.getMessage());
            }
        }
    }
    
    /**
     * Notify listeners of network manager error
     */
    private void notifyNetworkManagerError(String error) {
        for (NetworkManagerEventListener listener : eventListeners) {
            try {
                listener.onNetworkManagerError(error);
            } catch (Exception e) {
                LOGGER.warning("Error notifying listener of manager error: " + e.getMessage());
            }
        }
    }
    
    // ================================
    // CLEANUP
    // ================================
    
    /**
     * Shutdown the network manager
     */
    public void shutdown() {
        LOGGER.info("Shutting down network manager...");
        
        stopNetworkManagement();
        
        // Shutdown executor services
        if (!schedulerService.isShutdown()) {
            schedulerService.shutdown();
        }
        
        if (!workerService.isShutdown()) {
            workerService.shutdown();
        }
        
        // Clear listeners
        eventListeners.clear();
        
        LOGGER.info("Network manager shutdown complete");
    }
}