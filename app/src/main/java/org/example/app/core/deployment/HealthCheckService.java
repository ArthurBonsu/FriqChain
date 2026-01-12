package org.example.app.core.deployment;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * HealthCheckService monitors node health continuously.
 */
public class HealthCheckService {
    private static final Logger logger = Logger.getLogger(HealthCheckService.class.getName());
    
    private final Map<String, NodeHealth> nodeHealthMap;
    private final ScheduledExecutorService scheduler;
    
    public HealthCheckService() {
        this.nodeHealthMap = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
        logger.info("HealthCheckService initialized");
    }
    
    public void startMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            for (String nodeId : nodeHealthMap.keySet()) {
                checkNodeHealth(nodeId);
            }
        }, 0, 30, TimeUnit.SECONDS);
        
        logger.info("Health monitoring started (30s intervals)");
    }
    
    public void registerNode(String nodeId) {
        nodeHealthMap.put(nodeId, new NodeHealth(nodeId));
        logger.info("Registered node for health monitoring: " + nodeId);
    }
    
    private void checkNodeHealth(String nodeId) {
        NodeHealth health = nodeHealthMap.get(nodeId);
        if (health == null) return;
        
        boolean apiResponsive = checkAPI(nodeId);
        boolean peerConnected = checkPeers(nodeId);
        boolean syncing = checkSync(nodeId);
        
        health.update(apiResponsive, peerConnected, syncing);
    }
    
    private boolean checkAPI(String nodeId) { return Math.random() > 0.05; }
    private boolean checkPeers(String nodeId) { return Math.random() > 0.1; }
    private boolean checkSync(String nodeId) { return Math.random() > 0.05; }
    
    public NodeHealth getNodeHealth(String nodeId) {
        return nodeHealthMap.get(nodeId);
    }
    
    public Map<String, NodeHealth> getAllNodeHealth() {
        return new HashMap<>(nodeHealthMap);
    }
    
    public void shutdown() {
        scheduler.shutdown();
    }
    
    public static class NodeHealth {
        private final String nodeId;
        private boolean apiResponsive;
        private boolean peersConnected;
        private boolean syncing;
        private long lastCheckTime;
        
        public NodeHealth(String nodeId) {
            this.nodeId = nodeId;
            this.lastCheckTime = System.currentTimeMillis();
        }
        
        public void update(boolean api, boolean peers, boolean sync) {
            this.apiResponsive = api;
            this.peersConnected = peers;
            this.syncing = sync;
            this.lastCheckTime = System.currentTimeMillis();
        }
        
        public boolean isHealthy() {
            return apiResponsive && peersConnected && syncing;
        }
        
        public String getStatus() {
            return isHealthy() ? "HEALTHY" : "UNHEALTHY";
        }
        
        @Override
        public String toString() {
            return String.format("%s: %s (API:%s, Peers:%s, Sync:%s)",
                nodeId, getStatus(), apiResponsive?"✓":"✗",
                peersConnected?"✓":"✗", syncing?"✓":"✗");
        }
    }
}
