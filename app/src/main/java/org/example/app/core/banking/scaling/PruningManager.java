package org.example.app.core.scaling;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * PRUNING MANAGER
 * 
 * Manages blockchain state pruning to keep storage requirements manageable
 * for 1.2 billion users across Africa. Key features:
 * 
 * - Prunes old state data while preserving Merkle roots
 * - Maintains checkpoints for state recovery
 * - Archive nodes keep full history
 * - Target: <100 GB/year per node (pruned)
 * 
 * Pruning Strategy:
 * 1. Keep recent state (last 30 days) - fully accessible
 * 2. Summarize old state (30-365 days) - Merkle roots only
 * 3. Archive ancient state (>365 days) - archive nodes only
 */
public class PruningManager {
    private static final Logger LOGGER = Logger.getLogger(PruningManager.class.getName());
    
    // Pruning Configuration
    private static final long RECENT_PERIOD_MS = 30L * 24 * 60 * 60 * 1000; // 30 days
    private static final long SUMMARY_PERIOD_MS = 365L * 24 * 60 * 60 * 1000; // 1 year
    private static final int CHECKPOINT_INTERVAL_BLOCKS = 10000; // Every 10k blocks
    private static final int PRUNING_BATCH_SIZE = 1000;
    
    // State Storage
    private final Map<Long, BlockState> recentStates;        // Recent 30 days
    private final Map<Long, StateSummary> summarizedStates;  // 30 days - 1 year
    private final Map<Long, Checkpoint> checkpoints;         // State snapshots
    private final ReadWriteLock storageLock;
    
    // Pruning Queue
    private final BlockingQueue<Long> pruningQueue;
    private final ScheduledExecutorService pruningScheduler;
    
    // Metrics
    private final AtomicLong totalStatesPruned;
    private final AtomicLong totalSpaceSaved;
    private final AtomicLong checkpointsCreated;
    private volatile long lastPruningTime;
    
    // Configuration
    private final boolean archiveMode;
    private volatile boolean pruningEnabled;
    
    /**
     * Constructor
     */
    public PruningManager(boolean archiveMode) {
        this.archiveMode = archiveMode;
        this.pruningEnabled = !archiveMode; // Archive nodes don't prune
        
        // Initialize storage
        this.recentStates = new ConcurrentHashMap<>();
        this.summarizedStates = new ConcurrentHashMap<>();
        this.checkpoints = new ConcurrentHashMap<>();
        this.storageLock = new ReentrantReadWriteLock();
        
        // Initialize pruning queue
        this.pruningQueue = new LinkedBlockingQueue<>();
        this.pruningScheduler = Executors.newScheduledThreadPool(1,
            r -> new Thread(r, "Pruning-Scheduler"));
        
        // Initialize metrics
        this.totalStatesPruned = new AtomicLong(0);
        this.totalSpaceSaved = new AtomicLong(0);
        this.checkpointsCreated = new AtomicLong(0);
        this.lastPruningTime = System.currentTimeMillis();
        
        // Start pruning scheduler if not in archive mode
        if (pruningEnabled) {
            startPruningScheduler();
        }
        
        LOGGER.info(String.format(
            "Pruning Manager initialized: archiveMode=%s, pruningEnabled=%s",
            archiveMode, pruningEnabled
        ));
    }
    
    /**
     * Store new block state
     */
    public void storeBlockState(long blockNumber, BlockState state) {
        if (state == null) {
            throw new IllegalArgumentException("State cannot be null");
        }
        
        storageLock.writeLock().lock();
        try {
            recentStates.put(blockNumber, state);
            
            // Create checkpoint if needed
            if (blockNumber % CHECKPOINT_INTERVAL_BLOCKS == 0) {
                createCheckpoint(blockNumber, state);
            }
            
            // Queue old states for pruning
            if (pruningEnabled) {
                long cutoffTime = System.currentTimeMillis() - RECENT_PERIOD_MS;
                queueStatesForPruning(cutoffTime);
            }
        } finally {
            storageLock.writeLock().unlock();
        }
        
        LOGGER.fine(String.format(
            "State stored: block=%d, size=%d bytes",
            blockNumber, state.getSize()
        ));
    }
    
    /**
     * Get block state
     */
    public BlockState getBlockState(long blockNumber) {
        storageLock.readLock().lock();
        try {
            // Check recent states
            BlockState state = recentStates.get(blockNumber);
            if (state != null) {
                return state;
            }
            
            // Check if summarized (can't retrieve full state)
            if (summarizedStates.containsKey(blockNumber)) {
                LOGGER.warning(String.format(
                    "Block %d state has been pruned - only summary available",
                    blockNumber
                ));
                return null;
            }
            
            // Try to restore from nearest checkpoint
            return restoreFromCheckpoint(blockNumber);
            
        } finally {
            storageLock.readLock().unlock();
        }
    }
    
    /**
     * Get state summary for pruned block
     */
    public StateSummary getStateSummary(long blockNumber) {
        storageLock.readLock().lock();
        try {
            return summarizedStates.get(blockNumber);
        } finally {
            storageLock.readLock().unlock();
        }
    }
    
    /**
     * Create state checkpoint
     */
    private void createCheckpoint(long blockNumber, BlockState state) {
        Checkpoint checkpoint = new Checkpoint(
            blockNumber,
            state.getMerkleRoot(),
            state.getAccountCount(),
            state.getTotalBalance(),
            System.currentTimeMillis()
        );
        
        checkpoints.put(blockNumber, checkpoint);
        checkpointsCreated.incrementAndGet();
        
        LOGGER.info(String.format(
            "Checkpoint created: block=%d, accounts=%d",
            blockNumber, state.getAccountCount()
        ));
    }
    
    /**
     * Queue states for pruning
     */
    private void queueStatesForPruning(long cutoffTime) {
        for (Map.Entry<Long, BlockState> entry : recentStates.entrySet()) {
            if (entry.getValue().getTimestamp() < cutoffTime) {
                pruningQueue.offer(entry.getKey());
            }
        }
    }
    
    /**
     * Prune old states
     */
    private void pruneStates() {
        if (!pruningEnabled) {
            return;
        }
        
        List<Long> toPrune = new ArrayList<>();
        pruningQueue.drainTo(toPrune, PRUNING_BATCH_SIZE);
        
        if (toPrune.isEmpty()) {
            return;
        }
        
        storageLock.writeLock().lock();
        try {
            long spaceSaved = 0;
            
            for (Long blockNumber : toPrune) {
                BlockState state = recentStates.remove(blockNumber);
                
                if (state != null) {
                    // Create summary
                    StateSummary summary = new StateSummary(
                        blockNumber,
                        state.getMerkleRoot(),
                        state.getAccountCount(),
                        state.getSize(),
                        state.getTimestamp()
                    );
                    
                    summarizedStates.put(blockNumber, summary);
                    
                    // Track space saved
                    spaceSaved += state.getSize();
                    totalStatesPruned.incrementAndGet();
                }
            }
            
            totalSpaceSaved.addAndGet(spaceSaved);
            lastPruningTime = System.currentTimeMillis();
            
            LOGGER.info(String.format(
                "Pruned %d states, saved %d MB",
                toPrune.size(), spaceSaved / (1024 * 1024)
            ));
            
        } finally {
            storageLock.writeLock().unlock();
        }
    }
    
    /**
     * Restore state from nearest checkpoint
     */
    private BlockState restoreFromCheckpoint(long blockNumber) {
        // Find nearest checkpoint before requested block
        Long nearestCheckpoint = null;
        
        for (Long checkpointBlock : checkpoints.keySet()) {
            if (checkpointBlock <= blockNumber) {
                if (nearestCheckpoint == null || checkpointBlock > nearestCheckpoint) {
                    nearestCheckpoint = checkpointBlock;
                }
            }
        }
        
        if (nearestCheckpoint == null) {
            LOGGER.warning("No checkpoint found for block: " + blockNumber);
            return null;
        }
        
        LOGGER.info(String.format(
            "Restoring state from checkpoint: requested=%d, checkpoint=%d",
            blockNumber, nearestCheckpoint
        ));
        
        // In production, would replay blocks from checkpoint to requested block
        // For now, return null to indicate state not available
        return null;
    }
    
    /**
     * Archive old summarized states
     * Moves states older than 1 year to archive storage
     */
    public void archiveOldStates() {
        if (archiveMode) {
            return; // Archive nodes keep everything
        }
        
        long archiveCutoff = System.currentTimeMillis() - SUMMARY_PERIOD_MS;
        List<Long> toArchive = new ArrayList<>();
        
        storageLock.readLock().lock();
        try {
            for (Map.Entry<Long, StateSummary> entry : summarizedStates.entrySet()) {
                if (entry.getValue().getTimestamp() < archiveCutoff) {
                    toArchive.add(entry.getKey());
                }
            }
        } finally {
            storageLock.readLock().unlock();
        }
        
        if (toArchive.isEmpty()) {
            return;
        }
        
        storageLock.writeLock().lock();
        try {
            for (Long blockNumber : toArchive) {
                StateSummary summary = summarizedStates.remove(blockNumber);
                
                // In production, would transfer to archive storage
                // (e.g., Arweave, IPFS, cold storage)
                
                LOGGER.fine("State archived: block=" + blockNumber);
            }
            
            LOGGER.info(String.format(
                "Archived %d old state summaries",
                toArchive.size()
            ));
        } finally {
            storageLock.writeLock().unlock();
        }
    }
    
    /**
     * Get checkpoint
     */
    public Checkpoint getCheckpoint(long blockNumber) {
        storageLock.readLock().lock();
        try {
            return checkpoints.get(blockNumber);
        } finally {
            storageLock.readLock().unlock();
        }
    }
    
    /**
     * Get all checkpoints in range
     */
    public List<Checkpoint> getCheckpointsInRange(long startBlock, long endBlock) {
        List<Checkpoint> result = new ArrayList<>();
        
        storageLock.readLock().lock();
        try {
            for (Map.Entry<Long, Checkpoint> entry : checkpoints.entrySet()) {
                long blockNumber = entry.getKey();
                if (blockNumber >= startBlock && blockNumber <= endBlock) {
                    result.add(entry.getValue());
                }
            }
        } finally {
            storageLock.readLock().unlock();
        }
        
        result.sort(Comparator.comparing(Checkpoint::getBlockNumber));
        return result;
    }
    
    /**
     * Start pruning scheduler
     */
    private void startPruningScheduler() {
        // Run pruning every hour
        pruningScheduler.scheduleAtFixedRate(() -> {
            try {
                pruneStates();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Pruning error", e);
            }
        }, 1, 1, TimeUnit.HOURS);
        
        // Archive old summaries daily
        pruningScheduler.scheduleAtFixedRate(() -> {
            try {
                archiveOldStates();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Archive error", e);
            }
        }, 24, 24, TimeUnit.HOURS);
    }
    
    /**
     * Enable/disable pruning
     */
    public void setPruningEnabled(boolean enabled) {
        if (!archiveMode) {
            this.pruningEnabled = enabled;
            LOGGER.info("Pruning " + (enabled ? "enabled" : "disabled"));
        }
    }
    
    /**
     * Get pruning statistics
     */
    public PruningStatistics getStatistics() {
        storageLock.readLock().lock();
        try {
            long recentStateSize = recentStates.values().stream()
                .mapToLong(BlockState::getSize)
                .sum();
            
            long summarySize = summarizedStates.size() * 1024L; // ~1KB per summary
            
            return new PruningStatistics(
                recentStates.size(),
                summarizedStates.size(),
                checkpoints.size(),
                totalStatesPruned.get(),
                totalSpaceSaved.get(),
                checkpointsCreated.get(),
                recentStateSize,
                summarySize,
                lastPruningTime,
                pruningEnabled,
                archiveMode
            );
        } finally {
            storageLock.readLock().unlock();
        }
    }
    
    /**
     * Shutdown pruning manager
     */
    public void shutdown() {
        pruningScheduler.shutdown();
        
        try {
            if (!pruningScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                pruningScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            pruningScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        LOGGER.info("Pruning Manager shut down");
    }
    
    // Inner classes
    
    public static class BlockState {
        private final long blockNumber;
        private final String merkleRoot;
        private final Map<String, AccountState> accounts;
        private final long timestamp;
        
        public BlockState(long blockNumber, String merkleRoot,
                         Map<String, AccountState> accounts, long timestamp) {
            this.blockNumber = blockNumber;
            this.merkleRoot = merkleRoot;
            this.accounts = new HashMap<>(accounts);
            this.timestamp = timestamp;
        }
        
        public long getBlockNumber() { return blockNumber; }
        public String getMerkleRoot() { return merkleRoot; }
        public Map<String, AccountState> getAccounts() { return accounts; }
        public int getAccountCount() { return accounts.size(); }
        public long getTimestamp() { return timestamp; }
        
        public long getTotalBalance() {
            return accounts.values().stream()
                .mapToLong(AccountState::getBalance)
                .sum();
        }
        
        public long getSize() {
            // Estimate size: account count * average account size
            return accounts.size() * 256L; // ~256 bytes per account
        }
    }
    
    public static class AccountState {
        private final String address;
        private final long balance;
        private final long nonce;
        
        public AccountState(String address, long balance, long nonce) {
            this.address = address;
            this.balance = balance;
            this.nonce = nonce;
        }
        
        public String getAddress() { return address; }
        public long getBalance() { return balance; }
        public long getNonce() { return nonce; }
    }
    
    public static class StateSummary {
        private final long blockNumber;
        private final String merkleRoot;
        private final int accountCount;
        private final long originalSize;
        private final long timestamp;
        
        public StateSummary(long blockNumber, String merkleRoot, int accountCount,
                          long originalSize, long timestamp) {
            this.blockNumber = blockNumber;
            this.merkleRoot = merkleRoot;
            this.accountCount = accountCount;
            this.originalSize = originalSize;
            this.timestamp = timestamp;
        }
        
        public long getBlockNumber() { return blockNumber; }
        public String getMerkleRoot() { return merkleRoot; }
        public int getAccountCount() { return accountCount; }
        public long getOriginalSize() { return originalSize; }
        public long getTimestamp() { return timestamp; }
    }
    
    public static class Checkpoint {
        private final long blockNumber;
        private final String merkleRoot;
        private final int accountCount;
        private final long totalBalance;
        private final long timestamp;
        
        public Checkpoint(long blockNumber, String merkleRoot, int accountCount,
                         long totalBalance, long timestamp) {
            this.blockNumber = blockNumber;
            this.merkleRoot = merkleRoot;
            this.accountCount = accountCount;
            this.totalBalance = totalBalance;
            this.timestamp = timestamp;
        }
        
        public long getBlockNumber() { return blockNumber; }
        public String getMerkleRoot() { return merkleRoot; }
        public int getAccountCount() { return accountCount; }
        public long getTotalBalance() { return totalBalance; }
        public long getTimestamp() { return timestamp; }
    }
    
    public static class PruningStatistics {
        private final int recentStatesCount;
        private final int summarizedStatesCount;
        private final int checkpointsCount;
        private final long totalStatesPruned;
        private final long totalSpaceSaved;
        private final long checkpointsCreated;
        private final long recentStateSize;
        private final long summarySize;
        private final long lastPruningTime;
        private final boolean pruningEnabled;
        private final boolean archiveMode;
        
        public PruningStatistics(int recentStatesCount, int summarizedStatesCount,
                                int checkpointsCount, long totalStatesPruned,
                                long totalSpaceSaved, long checkpointsCreated,
                                long recentStateSize, long summarySize,
                                long lastPruningTime, boolean pruningEnabled,
                                boolean archiveMode) {
            this.recentStatesCount = recentStatesCount;
            this.summarizedStatesCount = summarizedStatesCount;
            this.checkpointsCount = checkpointsCount;
            this.totalStatesPruned = totalStatesPruned;
            this.totalSpaceSaved = totalSpaceSaved;
            this.checkpointsCreated = checkpointsCreated;
            this.recentStateSize = recentStateSize;
            this.summarySize = summarySize;
            this.lastPruningTime = lastPruningTime;
            this.pruningEnabled = pruningEnabled;
            this.archiveMode = archiveMode;
        }
        
        public int getRecentStatesCount() { return recentStatesCount; }
        public int getSummarizedStatesCount() { return summarizedStatesCount; }
        public int getCheckpointsCount() { return checkpointsCount; }
        public long getTotalStatesPruned() { return totalStatesPruned; }
        public long getTotalSpaceSaved() { return totalSpaceSaved; }
        public long getCheckpointsCreated() { return checkpointsCreated; }
        public long getRecentStateSize() { return recentStateSize; }
        public long getSummarySize() { return summarySize; }
        public long getTotalSize() { return recentStateSize + summarySize; }
        public long getLastPruningTime() { return lastPruningTime; }
        public boolean isPruningEnabled() { return pruningEnabled; }
        public boolean isArchiveMode() { return archiveMode; }
        
        @Override
        public String toString() {
            return String.format(
                "PruningStats[recent=%d, summarized=%d, pruned=%d, saved=%dMB, mode=%s]",
                recentStatesCount, summarizedStatesCount, totalStatesPruned,
                totalSpaceSaved / (1024 * 1024),
                archiveMode ? "ARCHIVE" : (pruningEnabled ? "PRUNING" : "DISABLED")
            );
        }
    }
}