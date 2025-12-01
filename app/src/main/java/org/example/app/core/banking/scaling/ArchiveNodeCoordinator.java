package org.example.app.core.scaling;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * ARCHIVE NODE COORDINATOR
 * 
 * Coordinates archive nodes that maintain full blockchain history.
 * Essential for African sovereignty - ensures historical data is
 * preserved across the continent without foreign dependencies.
 * 
 * Features:
 * - Distribute archive responsibilities across regions
 * - Redundant storage (3+ copies per region)
 * - Historical data retrieval
 * - Archive node health monitoring
 */
public class ArchiveNodeCoordinator {
    private static final Logger LOGGER = Logger.getLogger(ArchiveNodeCoordinator.class.getName());
    
    private static final int MIN_ARCHIVE_NODES_PER_REGION = 3;
    
    private final Map<ValidiumEngine.AfricanRegion, Set<ArchiveNode>> archiveNodes;
    private final ReadWriteLock nodesLock;
    private final AtomicLong totalDataArchived;
    
    public ArchiveNodeCoordinator() {
        this.archiveNodes = new ConcurrentHashMap<>();
        for (ValidiumEngine.AfricanRegion region : ValidiumEngine.AfricanRegion.values()) {
            archiveNodes.put(region, ConcurrentHashMap.newKeySet());
        }
        this.nodesLock = new ReentrantReadWriteLock();
        this.totalDataArchived = new AtomicLong(0);
        
        LOGGER.info("Archive Node Coordinator initialized");
    }
    
    /**
     * Register archive node
     */
    public void registerArchiveNode(ValidiumEngine.AfricanRegion region, ArchiveNode node) {
        nodesLock.writeLock().lock();
        try {
            Set<ArchiveNode> regional = archiveNodes.get(region);
            regional.add(node);
            
            LOGGER.info(String.format(
                "Archive node registered: region=%s, nodeId=%s, total=%d",
                region, node.getNodeId(), regional.size()
            ));
        } finally {
            nodesLock.writeLock().unlock();
        }
    }
    
    /**
     * Archive historical data
     */
    public void archiveData(ValidiumEngine.AfricanRegion region, HistoricalData data) {
        nodesLock.readLock().lock();
        try {
            Set<ArchiveNode> regional = archiveNodes.get(region);
            
            if (regional.size() < MIN_ARCHIVE_NODES_PER_REGION) {
                LOGGER.warning(String.format(
                    "Insufficient archive nodes in region %s: %d/%d",
                    region, regional.size(), MIN_ARCHIVE_NODES_PER_REGION
                ));
            }
            
            // Distribute to all regional archive nodes
            for (ArchiveNode node : regional) {
                try {
                    node.storeData(data);
                } catch (Exception e) {
                    LOGGER.warning("Failed to archive data to node: " + node.getNodeId());
                }
            }
            
            totalDataArchived.addAndGet(data.getSize());
            
        } finally {
            nodesLock.readLock().unlock();
        }
    }
    
    /**
     * Retrieve historical data
     */
    public HistoricalData retrieveData(ValidiumEngine.AfricanRegion region, long blockNumber) {
        nodesLock.readLock().lock();
        try {
            Set<ArchiveNode> regional = archiveNodes.get(region);
            
            // Try each archive node until data is found
            for (ArchiveNode node : regional) {
                try {
                    HistoricalData data = node.retrieveData(blockNumber);
                    if (data != null) {
                        return data;
                    }
                } catch (Exception e) {
                    LOGGER.warning("Failed to retrieve from node: " + node.getNodeId());
                }
            }
            
            return null;
        } finally {
            nodesLock.readLock().unlock();
        }
    }
    
    /**
     * Get archive statistics
     */
    public ArchiveStatistics getStatistics() {
        Map<ValidiumEngine.AfricanRegion, Integer> nodeCounts = new HashMap<>();
        
        nodesLock.readLock().lock();
        try {
            for (Map.Entry<ValidiumEngine.AfricanRegion, Set<ArchiveNode>> entry : 
                    archiveNodes.entrySet()) {
                nodeCounts.put(entry.getKey(), entry.getValue().size());
            }
        } finally {
            nodesLock.readLock().unlock();
        }
        
        return new ArchiveStatistics(
            nodeCounts,
            totalDataArchived.get()
        );
    }
    
    public static class ArchiveNode {
        private final String nodeId;
        private final ValidiumEngine.AfricanRegion region;
        private final String endpoint;
        private final Map<Long, HistoricalData> storage;
        
        public ArchiveNode(String nodeId, ValidiumEngine.AfricanRegion region, String endpoint) {
            this.nodeId = nodeId;
            this.region = region;
            this.endpoint = endpoint;
            this.storage = new ConcurrentHashMap<>();
        }
        
        public void storeData(HistoricalData data) {
            storage.put(data.getBlockNumber(), data);
        }
        
        public HistoricalData retrieveData(long blockNumber) {
            return storage.get(blockNumber);
        }
        
        public String getNodeId() { return nodeId; }
        public ValidiumEngine.AfricanRegion getRegion() { return region; }
        public String getEndpoint() { return endpoint; }
    }
    
    public static class HistoricalData {
        private final long blockNumber;
        private final byte[] data;
        private final long timestamp;
        
        public HistoricalData(long blockNumber, byte[] data, long timestamp) {
            this.blockNumber = blockNumber;
            this.data = data;
            this.timestamp = timestamp;
        }
        
        public long getBlockNumber() { return blockNumber; }
        public byte[] getData() { return data; }
        public long getTimestamp() { return timestamp; }
        public long getSize() { return data != null ? data.length : 0; }
    }
    
    public static class ArchiveStatistics {
        private final Map<ValidiumEngine.AfricanRegion, Integer> nodeCountsByRegion;
        private final long totalDataArchived;
        
        public ArchiveStatistics(Map<ValidiumEngine.AfricanRegion, Integer> nodeCountsByRegion,
                                long totalDataArchived) {
            this.nodeCountsByRegion = nodeCountsByRegion;
            this.totalDataArchived = totalDataArchived;
        }
        
        public Map<ValidiumEngine.AfricanRegion, Integer> getNodeCountsByRegion() {
            return nodeCountsByRegion;
        }
        public long getTotalDataArchived() { return totalDataArchived; }
        
        @Override
        public String toString() {
            return String.format(
                "ArchiveStats[totalNodes=%d, dataArchived=%dMB]",
                nodeCountsByRegion.values().stream().mapToInt(Integer::intValue).sum(),
                totalDataArchived / (1024 * 1024)
            );
        }
    }
}