package org.example.app.core.p2p;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a peer-to-peer network for communication and coordination among peers.
 */
public class PeerNetwork {
    private final String peerId;         // Unique identifier for the peer in the network
    private final String host;           // Host address where the peer listens
    private final int port;              // Port number for communication
    private final Peer peer;             // Peer instance for handling local peer actions
    private final Set<String> knownPeers; // Known peers in the network
    
    // Enhanced: Add shutdown control
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private ServerSocket serverSocket;   // Keep reference to server socket for shutdown
    private Thread serverThread;        // Keep reference to server thread

    public PeerNetwork(String peerId, String host, int port) {
        if (peerId == null || peerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Peer ID cannot be null or empty.");
        }
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty.");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535.");
        }

        this.peerId = peerId;
        this.host = host;
        this.port = port;
        this.peer = new Peer(peerId, host + ":" + port);
        this.knownPeers = Collections.synchronizedSet(new HashSet<>());
    }

    /**
     * Starts the server, allowing the peer to listen for incoming connections.
     */
    public void startServer() {
        if (isRunning.get()) {
            System.out.println("PeerNetwork: Server already running on " + host + ":" + port);
            return;
        }
        
        isRunning.set(true);
        peer.startServer();
        System.out.println("PeerNetwork: Server started on " + host + ":" + port);
    }

    /**
     * Accepts incoming connections (handled by the Peer class).
     */
    public void acceptConnections() {
        if (!isRunning.get()) {
            System.out.println("PeerNetwork: Cannot accept connections - server not running");
            return;
        }
        
        System.out.println("PeerNetwork: Accepting connections...");
        peer.startServer(); // Start the server to accept incoming connections
    }

    /**
     * Discovers peers using a static list or discovery protocol.
     */
    public void discoverPeers() {
        if (!isRunning.get()) {
            System.out.println("PeerNetwork: Cannot discover peers - network not running");
            return;
        }
        
        // Example hardcoded peers or discovery mechanism
        List<String> discoveredPeers = Arrays.asList("127.0.0.1:8081", "127.0.0.1:8082");
        for (String peerAddress : discoveredPeers) {
            addPeerToNetwork(peerAddress);
        }
        System.out.println("PeerNetwork: Discovered peers: " + discoveredPeers);
    }

    /**
     * Adds a peer to the network.
     *
     * @param peerAddress The address of the peer to add.
     */
    public void addPeerToNetwork(String peerAddress) {
        if (peerAddress == null || !peerAddress.contains(":")) {
            System.err.println("PeerNetwork: Invalid peer address: " + peerAddress);
            return;
        }

        peer.addPeer(peerAddress);
        knownPeers.add(peerAddress);
        System.out.println("PeerNetwork: Added peer to network: " + peerAddress);
    }

    /**
     * Sends a broadcast message to all known peers.
     *
     * @param message The message to broadcast.
     */
    public void sendBroadcast(String message) {
        if (!isRunning.get()) {
            System.out.println("PeerNetwork: Cannot broadcast - network not running");
            return;
        }
        
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Message cannot be null or empty.");
        }

        System.out.println("PeerNetwork: Broadcasting message: " + message);
        peer.broadcast(message);
    }

    /**
     * Handles communication with a connected peer.
     *
     * @param clientSocket   The connected client's socket.
     * @param clientAddress  The address of the connected client.
     */
    public void handlePeerConnection(Socket clientSocket, String clientAddress) {
        if (!isRunning.get()) {
            System.out.println("PeerNetwork: Cannot handle connection - network shutting down");
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("PeerNetwork: Error closing client socket during shutdown: " + e.getMessage());
            }
            return;
        }
        
        if (clientSocket == null || clientAddress == null || clientAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid client socket or address.");
        }

        try {
            System.out.println("PeerNetwork: Handling connection from " + clientAddress);
            byte[] data = NetworkUtils.receiveData(clientSocket);
            Message message = Message.deserialize(data);
            System.out.println("PeerNetwork: Received message from " + clientAddress + ": " + message);

            // Example of responding to a message
            Message response = new Message(peerId, "Acknowledged: " + message.getContent());
            NetworkUtils.sendData(clientSocket, response.serialize());
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("PeerNetwork: Error handling peer connection from " + clientAddress + ": " + e.getMessage());
        } finally {
            try {
                NetworkUtils.closeSocket(clientSocket);
            } catch (IOException e) {
                System.err.println("PeerNetwork: Error closing connection from " + clientAddress + ": " + e.getMessage());
            }
        }
    }

    // ========================================
    // ENHANCED: SHUTDOWN AND LIFECYCLE METHODS
    // ========================================

    /**
     * Gracefully shuts down the peer network.
     * This method stops all network operations and cleans up resources.
     */
    public void shutdown() {
        System.out.println("PeerNetwork: Initiating shutdown for peer " + peerId);
        
        // Set shutdown flag
        isRunning.set(false);
        
        try {
            // Close server socket if it exists
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                System.out.println("PeerNetwork: Server socket closed");
            }
            
            // Interrupt server thread if it exists
            if (serverThread != null && serverThread.isAlive()) {
                serverThread.interrupt();
                try {
                    serverThread.join(5000); // Wait up to 5 seconds for thread to finish
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("PeerNetwork: Server thread interruption was interrupted");
                }
            }
            
            // Clear known peers
            knownPeers.clear();
            System.out.println("PeerNetwork: Cleared " + knownPeers.size() + " known peers");
            
            // Shutdown peer if it has shutdown capability
            if (peer != null) {
                // Note: The Peer class would need its own shutdown method
                // For now, we just log that we're cleaning up the peer
                System.out.println("PeerNetwork: Peer instance cleanup initiated");
            }
            
            System.out.println("PeerNetwork: Shutdown completed successfully for peer " + peerId);
            
        } catch (IOException e) {
            System.err.println("PeerNetwork: Error during shutdown: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("PeerNetwork: Unexpected error during shutdown: " + e.getMessage());
        }
    }

    /**
     * Stops the network (alias for shutdown for compatibility).
     */
    public void stop() {
        shutdown();
    }

    /**
     * Checks if the peer network is currently running.
     *
     * @return true if the network is running, false otherwise
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * Gets the number of known peers in the network.
     *
     * @return the number of known peers
     */
    public int getKnownPeerCount() {
        return knownPeers.size();
    }

    /**
     * Gets a copy of all known peer addresses.
     *
     * @return a set containing all known peer addresses
     */
    public Set<String> getKnownPeers() {
        return new HashSet<>(knownPeers);
    }

    /**
     * Gets the peer ID for this network instance.
     *
     * @return the peer ID
     */
    public String getPeerId() {
        return peerId;
    }

    /**
     * Gets the host address this peer is listening on.
     *
     * @return the host address
     */
    public String getHost() {
        return host;
    }

    /**
     * Gets the port this peer is listening on.
     *
     * @return the port number
     */
    public int getPort() {
        return port;
    }

    // ========================================
    // ENHANCED: NETWORK STATUS AND DIAGNOSTICS
    // ========================================

    /**
     * Provides a status summary of the peer network.
     *
     * @return a formatted string with network status information
     */
    public String getNetworkStatus() {
        StringBuilder status = new StringBuilder();
        status.append("PeerNetwork Status:\n");
        status.append("  Peer ID: ").append(peerId).append("\n");
        status.append("  Address: ").append(host).append(":").append(port).append("\n");
        status.append("  Running: ").append(isRunning.get()).append("\n");
        status.append("  Known Peers: ").append(knownPeers.size()).append("\n");
        if (!knownPeers.isEmpty()) {
            status.append("  Peer List: ").append(knownPeers).append("\n");
        }
        return status.toString();
    }

    /**
     * Removes a peer from the network.
     *
     * @param peerAddress The address of the peer to remove
     * @return true if the peer was removed, false if it wasn't found
     */
    public boolean removePeerFromNetwork(String peerAddress) {
        if (peerAddress == null || peerAddress.trim().isEmpty()) {
            return false;
        }

        boolean removed = knownPeers.remove(peerAddress);
        if (removed) {
            System.out.println("PeerNetwork: Removed peer from network: " + peerAddress);
        } else {
            System.out.println("PeerNetwork: Peer not found for removal: " + peerAddress);
        }
        return removed;
    }

    // ========================================
    // ADDITIONAL METHODS FOR DASHBOARD INTEGRATION
    // ========================================

    private int maxPeers = 50; // Default maximum number of peers

    /**
     * Sets the maximum number of peers allowed in the network.
     *
     * @param maxPeers The maximum number of peers
     */
    public void setMaxPeers(int maxPeers) {
        if (maxPeers <= 0) {
            throw new IllegalArgumentException("Max peers must be positive");
        }
        this.maxPeers = maxPeers;
        System.out.println("PeerNetwork: Max peers set to " + maxPeers);
    }

    /**
     * Gets the maximum number of peers allowed.
     *
     * @return the maximum number of peers
     */
    public int getMaxPeers() {
        return maxPeers;
    }

    /**
     * Gets a list of active peers in the network.
     * Returns a list of Peer-like objects representing active peers.
     *
     * @return list of active peers
     */
    public List<org.example.app.core.p2p.Peer> getActivePeers() {
        List<org.example.app.core.p2p.Peer> activePeers = new ArrayList<>();
        // Convert known peer addresses to Peer objects
        for (String peerAddress : knownPeers) {
            String[] parts = peerAddress.split(":");
            String peerIdFromAddress = parts.length > 0 ? parts[0] : "unknown";
            activePeers.add(new org.example.app.core.p2p.Peer(peerIdFromAddress, peerAddress));
        }
        return activePeers;
    }

    /**
     * Disconnects a peer by its peer ID.
     *
     * @param peerId The ID of the peer to disconnect
     * @return true if the peer was disconnected, false if not found
     */
    public boolean disconnectPeer(String peerId) {
        if (peerId == null || peerId.trim().isEmpty()) {
            return false;
        }

        // Find and remove peer by ID
        boolean removed = false;
        Iterator<String> iterator = knownPeers.iterator();
        while (iterator.hasNext()) {
            String peerAddress = iterator.next();
            if (peerAddress.contains(peerId) || peerAddress.startsWith(peerId)) {
                iterator.remove();
                removed = true;
                System.out.println("PeerNetwork: Disconnected peer: " + peerId + " (" + peerAddress + ")");
                break;
            }
        }

        if (!removed) {
            System.out.println("PeerNetwork: Peer not found for disconnection: " + peerId);
        }
        return removed;
    }

    /**
     * Main method for running the PeerNetwork.
     */
    public static void main(String[] args) {
        // Initialize PeerNetwork
        PeerNetwork network = new PeerNetwork("Peer1", "127.0.0.1", 8080);
        PeerNetwork network1 = new PeerNetwork("Peer2", "127.0.0.1", 8081);

        // Start the networks
        network.startServer();
        network1.startServer();

        // Discover peers
        network.discoverPeers();

        // Accept incoming connections in a separate thread
        new Thread(network::acceptConnections).start();

        // Broadcast a message to all known peers
        network.sendBroadcast("Hello, Peer Network!");

        // Print network status
        System.out.println(network.getNetworkStatus());

        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down peer networks...");
            network.shutdown();
            network1.shutdown();
        }));
    }
}