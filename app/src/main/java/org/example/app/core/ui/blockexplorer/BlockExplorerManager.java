package org.example.app.core.ui.blockexplorer;

import org.example.app.core.consensus.Blockchain;
import org.example.app.core.block.Block;
import org.example.app.core.block.Transaction;
import org.example.app.core.state.BlockManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BlockExplorerManager {
    private static final Logger LOGGER = Logger.getLogger(BlockExplorerManager.class.getName());
    
    private final Blockchain blockchain;
    private final BlockManager blockManager;
    private final ExecutorService executorService;
    private final List<BlockExplorerEventListener> eventListeners;
    private Map<String, BlockCache> blockCacheMap;
    private BlockExplorerMetrics metrics;
    
    // Cache configuration
    private static final int MAX_CACHE_SIZE = 1000;
    private static final long CACHE_EXPIRY_TIME = 5 * 60 * 1000; // 5 minutes
    
    public BlockExplorerManager(Blockchain blockchain, BlockManager blockManager) {
        this.blockchain = blockchain;
        this.blockManager = blockManager;
        this.executorService = Executors.newFixedThreadPool(4);
        this.eventListeners = new ArrayList<>();
        this.blockCacheMap = new LinkedHashMap<String, BlockCache>(MAX_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        };
        this.metrics = new BlockExplorerMetrics();
        LOGGER.info("BlockExplorerManager initialized with Blockchain and BlockManager");
    }
    
    // ===== BLOCK RETRIEVAL METHODS =====
    
    /**
     * Gets the latest block from the blockchain
     */
    public Block getLatestBlock() {
        try {
            Block latestBlock = blockchain.getLatestBlock();
            if (latestBlock != null) {
                metrics.incrementBlocksQueried();
                cacheBlock(latestBlock);
            }
            return latestBlock;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving latest block", e);
            return null;
        }
    }
    
    /**
     * Gets a block by its hash
     */
    public Block getBlockByHash(String blockHash) {
        try {
            // Check cache first
            BlockCache cached = blockCacheMap.get(blockHash);
            if (cached != null && !cached.isExpired()) {
                LOGGER.fine("Block cache hit for hash: " + blockHash);
                metrics.incrementCacheHits();
                return cached.block;
            }
            
            Block block = blockchain.getBlockByHash(blockHash);
            if (block != null) {
                metrics.incrementBlocksQueried();
                cacheBlock(block);
            }
            return block;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving block by hash: " + blockHash, e);
            return null;
        }
    }
    
    /**
     * Gets a block by its number/height
     */
    public Block getBlockByNumber(long blockNumber) {
        try {
            if (blockNumber < 0 || blockNumber >= blockchain.getChainLength()) {
                LOGGER.warning("Invalid block number: " + blockNumber);
                return null;
            }
            
            Block block = blockchain.getBlockByIndex((int) blockNumber);
            if (block != null) {
                metrics.incrementBlocksQueried();
                cacheBlock(block);
            }
            return block;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving block by number: " + blockNumber, e);
            return null;
        }
    }
    
    /**
     * Gets the latest N blocks
     */
    public List<Block> getLatestBlocks(int count) {
        try {
            List<Block> blocks = new ArrayList<>();
            int chainLength = blockchain.getChainLength();
            int startIndex = Math.max(0, chainLength - count);
            
            for (int i = startIndex; i < chainLength; i++) {
                Block block = blockchain.getBlockByIndex(i);
                if (block != null) {
                    blocks.add(block);
                }
            }
            
            metrics.incrementBlocksQueried();
            blocks.forEach(this::cacheBlock);
            return blocks;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving latest blocks", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Gets blocks within a range
     */
    public List<Block> getBlockRange(long startBlock, long endBlock) {
        try {
            List<Block> blocks = new ArrayList<>();
            for (long i = startBlock; i <= endBlock && i < blockchain.getChainLength(); i++) {
                Block block = blockchain.getBlockByIndex((int) i);
                if (block != null) {
                    blocks.add(block);
                }
            }
            
            metrics.incrementBlocksQueried();
            blocks.forEach(this::cacheBlock);
            return blocks;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving block range", e);
            return new ArrayList<>();
        }
    }
    
    // ===== TRANSACTION METHODS =====
    
    /**
     * Gets all transactions in a specific block
     */
    public List<Transaction> getBlockTransactions(String blockHash) {
        try {
            Block block = getBlockByHash(blockHash);
            if (block != null) {
                List<Transaction> transactions = block.getTransactions();
                metrics.incrementTransactionsQueried();
                return transactions != null ? transactions : new ArrayList<>();
            }
            return new ArrayList<>();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving block transactions", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Gets a transaction by hash from all blocks
     */
    public TransactionSearchResult findTransactionByHash(String txHash) {
        try {
            List<Block> allBlocks = blockchain.getAllBlocks();
            
            for (Block block : allBlocks) {
                List<Transaction> transactions = block.getTransactions();
                if (transactions != null) {
                    for (Transaction tx : transactions) {
                        if (tx.getHash().equals(txHash)) {
                            metrics.incrementTransactionsQueried();
                            return new TransactionSearchResult(true, block, tx, "Transaction found");
                        }
                    }
                }
            }
            
            return new TransactionSearchResult(false, null, null, "Transaction not found");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error searching for transaction", e);
            return new TransactionSearchResult(false, null, null, "Error searching for transaction: " + e.getMessage());
        }
    }
    
    /**
     * Gets pending transactions
     */
    public List<Transaction> getPendingTransactions() {
        try {
            List<Transaction> pendingTxs = blockchain.getPendingTransactions();
            metrics.incrementTransactionsQueried();
            return pendingTxs != null ? pendingTxs : new ArrayList<>();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving pending transactions", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Gets transactions by sender address
     */
    public List<TransactionInfo> getTransactionsBySender(String senderAddress) {
        try {
            List<Block> allBlocks = blockchain.getAllBlocks();
            List<TransactionInfo> results = new ArrayList<>();
            
            for (Block block : allBlocks) {
                List<Transaction> transactions = block.getTransactions();
                if (transactions != null) {
                    for (Transaction tx : transactions) {
                        if (senderAddress.equals(tx.getSender())) {
                            results.add(new TransactionInfo(tx, block.getHash(), block.getNumber(), block.getTimestamp()));
                        }
                    }
                }
            }
            
            metrics.incrementTransactionsQueried();
            return results;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving transactions by sender", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Gets transactions by recipient address
     */
    public List<TransactionInfo> getTransactionsByRecipient(String recipientAddress) {
        try {
            List<Block> allBlocks = blockchain.getAllBlocks();
            List<TransactionInfo> results = new ArrayList<>();
            
            for (Block block : allBlocks) {
                List<Transaction> transactions = block.getTransactions();
                if (transactions != null) {
                    for (Transaction tx : transactions) {
                        if (recipientAddress.equals(tx.getRecipient())) {
                            results.add(new TransactionInfo(tx, block.getHash(), block.getNumber(), block.getTimestamp()));
                        }
                    }
                }
            }
            
            metrics.incrementTransactionsQueried();
            return results;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving transactions by recipient", e);
            return new ArrayList<>();
        }
    }
    
    // ===== BLOCKCHAIN STATISTICS =====
    
    /**
     * Gets blockchain statistics
     */
    public BlockchainStats getBlockchainStats() {
        try {
            BlockchainStats stats = new BlockchainStats();
            stats.totalBlocks = blockchain.getChainLength();
            stats.totalTransactions = countTotalTransactions();
            stats.pendingTransactions = blockchain.getPendingTransactions().size();
            stats.lastBlockTime = getLastBlockTime();
            stats.averageBlockTime = calculateAverageBlockTime();
            
            metrics.incrementStatsRequests();
            return stats;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error retrieving blockchain statistics", e);
            return new BlockchainStats();
        }
    }
    
    /**
     * Counts total transactions in blockchain
     */
    private int countTotalTransactions() {
        return (int) blockchain.getAllBlocks().stream()
            .flatMap(block -> block.getTransactions() != null ? block.getTransactions().stream() : Stream.empty())
            .count();
    }
    
    /**
     * Gets the time of the last block
     */
    private long getLastBlockTime() {
        Block latestBlock = blockchain.getLatestBlock();
        return latestBlock != null ? latestBlock.getTimestamp() : 0;
    }
    
    /**
     * Calculates average block time
     */
    private long calculateAverageBlockTime() {
        try {
            List<Block> allBlocks = blockchain.getAllBlocks();
            if (allBlocks.size() < 2) {
                return 0;
            }
            
            Block firstBlock = allBlocks.get(0);
            Block lastBlock = allBlocks.get(allBlocks.size() - 1);
            
            long timeDiff = lastBlock.getTimestamp() - firstBlock.getTimestamp();
            return timeDiff / (allBlocks.size() - 1);
        } catch (Exception e) {
            return 0;
        }
    }
    
    // ===== ASYNC OPERATIONS =====
    
    /**
     * Asynchronously retrieves a block by hash
     */
    public CompletableFuture<Block> getBlockByHashAsync(String blockHash) {
        return CompletableFuture.supplyAsync(() -> getBlockByHash(blockHash), executorService);
    }
    
    /**
     * Asynchronously retrieves latest blocks
     */
    public CompletableFuture<List<Block>> getLatestBlocksAsync(int count) {
        return CompletableFuture.supplyAsync(() -> getLatestBlocks(count), executorService);
    }
    
    /**
     * Asynchronously searches for transaction
     */
    public CompletableFuture<TransactionSearchResult> findTransactionAsync(String txHash) {
        return CompletableFuture.supplyAsync(() -> findTransactionByHash(txHash), executorService);
    }
    
    // ===== CACHING =====
    
    /**
     * Caches a block for quick retrieval
     */
    private void cacheBlock(Block block) {
        if (block != null && block.getHash() != null) {
            blockCacheMap.put(block.getHash(), new BlockCache(block));
        }
    }
    
    /**
     * Clears the block cache
     */
    public void clearCache() {
        blockCacheMap.clear();
        LOGGER.info("Block cache cleared");
    }
    
    /**
     * Gets cache statistics
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheSize", blockCacheMap.size());
        stats.put("maxCacheSize", MAX_CACHE_SIZE);
        stats.put("cacheHits", metrics.getCacheHits());
        return stats;
    }
    
    // ===== EVENT LISTENERS =====
    
    /**
     * Adds an event listener
     */
    public void addEventListener(BlockExplorerEventListener listener) {
        if (listener != null && !eventListeners.contains(listener)) {
            eventListeners.add(listener);
            LOGGER.info("Block explorer event listener added");
        }
    }
    
    /**
     * Removes an event listener
     */
    public void removeEventListener(BlockExplorerEventListener listener) {
        eventListeners.remove(listener);
    }
    
    /**
     * Notifies listeners of new block
     */
    public void notifyNewBlock(Block block) {
        LOGGER.info("Notifying listeners of new block: " + block.getHash());
        for (BlockExplorerEventListener listener : eventListeners) {
            try {
                listener.onNewBlock(block);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in new block listener", e);
            }
        }
    }
    
    /**
     * Notifies listeners of new transaction
     */
    public void notifyNewTransaction(Transaction tx) {
        LOGGER.info("Notifying listeners of new transaction: " + tx.getHash());
        for (BlockExplorerEventListener listener : eventListeners) {
            try {
                listener.onNewTransaction(tx);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in new transaction listener", e);
            }
        }
    }
    
    // ===== UTILITY METHODS =====
    
    /**
     * Gets total blockchain size (block count)
     */
    public int getBlockchainSize() {
        return blockchain.getChainLength();
    }
    
    /**
     * Gets block at specific index
     */
    public Block getBlockAt(int index) {
        try {
            return blockchain.getBlockByIndex(index);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting block at index " + index, e);
            return null;
        }
    }
    
    /**
     * Checks if a block exists
     */
    public boolean blockExists(String blockHash) {
        return getBlockByHash(blockHash) != null;
    }
    
    /**
     * Gets metrics
     */
    public BlockExplorerMetrics getMetrics() {
        return metrics;
    }
    
    /**
     * Shutdown manager
     */
    public void shutdown() {
        executorService.shutdown();
        LOGGER.info("BlockExplorerManager shutdown complete");
    }
    
    // ===== INNER CLASSES =====
    
    /**
     * Block cache entry with expiry
     */
    private static class BlockCache {
        final Block block;
        final long createdAt;
        
        BlockCache(Block block) {
            this.block = block;
            this.createdAt = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CACHE_EXPIRY_TIME;
        }
    }
    
    /**
     * Transaction search result
     */
    public static class TransactionSearchResult {
        public final boolean found;
        public final Block containingBlock;
        public final Transaction transaction;
        public final String message;
        
        public TransactionSearchResult(boolean found, Block containingBlock, Transaction transaction, String message) {
            this.found = found;
            this.containingBlock = containingBlock;
            this.transaction = transaction;
            this.message = message;
        }
    }
    
    /**
     * Transaction information wrapper
     */
    public static class TransactionInfo {
        public final Transaction transaction;
        public final String blockHash;
        public final long blockNumber;
        public final long blockTimestamp;
        
        public TransactionInfo(Transaction transaction, String blockHash, long blockNumber, long blockTimestamp) {
            this.transaction = transaction;
            this.blockHash = blockHash;
            this.blockNumber = blockNumber;
            this.blockTimestamp = blockTimestamp;
        }
    }
    
    /**
     * Blockchain statistics
     */
    public static class BlockchainStats {
        public int totalBlocks;
        public int totalTransactions;
        public int pendingTransactions;
        public long lastBlockTime;
        public long averageBlockTime;
        
        @Override
        public String toString() {
            return String.format("BlockchainStats{blocks=%d, txs=%d, pending=%d, avgBlockTime=%dms}",
                totalBlocks, totalTransactions, pendingTransactions, averageBlockTime);
        }
    }
    
    /**
     * Block explorer metrics
     */
    public static class BlockExplorerMetrics {
        private int blocksQueried = 0;
        private int transactionsQueried = 0;
        private int cacheHits = 0;
        private int statsRequests = 0;
        
        public void incrementBlocksQueried() { blocksQueried++; }
        public void incrementTransactionsQueried() { transactionsQueried++; }
        public void incrementCacheHits() { cacheHits++; }
        public void incrementStatsRequests() { statsRequests++; }
        
        public int getBlocksQueried() { return blocksQueried; }
        public int getTransactionsQueried() { return transactionsQueried; }
        public int getCacheHits() { return cacheHits; }
        public int getStatsRequests() { return statsRequests; }
    }
    
    /**
     * Event listener interface
     */
    public interface BlockExplorerEventListener {
        default void onNewBlock(Block block) {}
        default void onNewTransaction(Transaction tx) {}
        default void onBlockExplorerRefresh() {}
    }
}