package org.example.app.core.ui.blockexplorer;

import org.example.app.core.consensus.Blockchain;
import org.example.app.core.block.Block;
import org.example.app.core.block.Transaction;
import org.example.app.core.state.BlockManager;

import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

public class BlockExplorerController {
    private static final Logger LOGGER = Logger.getLogger(BlockExplorerController.class.getName());
    
    private final BlockExplorerManager manager;
    private final BlockExplorerView view;
    private String currentSearchQuery;
    private SearchType currentSearchType;
    private List<Block> currentDisplayedBlocks;
    private Block currentSelectedBlock;
    private Transaction currentSelectedTransaction;
    
    public enum SearchType {
        BLOCK_HASH,
        BLOCK_NUMBER,
        TRANSACTION_HASH,
        ADDRESS,
        NONE
    }
    
    public BlockExplorerController(Blockchain blockchain, BlockManager blockManager) {
        this.manager = new BlockExplorerManager(blockchain, blockManager);
        this.view = new BlockExplorerView(this);
        this.currentDisplayedBlocks = new ArrayList<>();
        this.currentSearchType = SearchType.NONE;
        LOGGER.info("BlockExplorerController initialized");
    }
    
    // ===== INITIALIZATION & REFRESH =====
    
    /**
     * Initializes the block explorer with latest blocks
     */
    public void initialize() {
        try {
            LOGGER.info("Initializing block explorer");
            refreshLatestBlocks();
            view.displayBlockchainStats(manager.getBlockchainStats());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error initializing block explorer", e);
            view.displayError("Failed to initialize block explorer: " + e.getMessage());
        }
    }
    
    /**
     * Refreshes the display with latest blocks
     */
    public void refreshLatestBlocks() {
        try {
            List<Block> latestBlocks = manager.getLatestBlocks(20);
            currentDisplayedBlocks = latestBlocks;
            currentSearchType = SearchType.NONE;
            view.displayBlocks(latestBlocks);
            view.displayBlockchainStats(manager.getBlockchainStats());
            LOGGER.info("Refreshed latest blocks: " + latestBlocks.size() + " blocks displayed");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error refreshing latest blocks", e);
            view.displayError("Failed to refresh blocks: " + e.getMessage());
        }
    }
    
    /**
     * Full refresh of the block explorer
     */
    public void refreshAll() {
        try {
            LOGGER.info("Performing full refresh");
            refreshLatestBlocks();
            manager.clearCache();
            view.displayBlockchainStats(manager.getBlockchainStats());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during full refresh", e);
            view.displayError("Full refresh failed: " + e.getMessage());
        }
    }
    
    // ===== SEARCH OPERATIONS =====
    
    /**
     * Searches for a block by hash
     */
    public void searchBlockByHash(String blockHash) {
        try {
            if (!BlockExplorerUIUtils.isValidBlockHash(blockHash)) {
                view.displayWarning("Invalid block hash format");
                return;
            }
            
            currentSearchQuery = blockHash;
            currentSearchType = SearchType.BLOCK_HASH;
            Block block = manager.getBlockByHash(blockHash);
            
            if (block != null) {
                currentSelectedBlock = block;
                currentDisplayedBlocks = Arrays.asList(block);
                view.displayBlocks(currentDisplayedBlocks);
                view.displayBlockDetails(block);
                LOGGER.info("Found block by hash: " + blockHash);
            } else {
                view.displayWarning("Block not found with hash: " + blockHash);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error searching block by hash", e);
            view.displayError("Search failed: " + e.getMessage());
        }
    }
    
    /**
     * Searches for a block by number
     */
    public void searchBlockByNumber(long blockNumber) {
        try {
            if (!BlockExplorerUIUtils.isValidBlockNumber(blockNumber)) {
                view.displayWarning("Invalid block number");
                return;
            }
            
            currentSearchQuery = String.valueOf(blockNumber);
            currentSearchType = SearchType.BLOCK_NUMBER;
            Block block = manager.getBlockByNumber(blockNumber);
            
            if (block != null) {
                currentSelectedBlock = block;
                currentDisplayedBlocks = Arrays.asList(block);
                view.displayBlocks(currentDisplayedBlocks);
                view.displayBlockDetails(block);
                LOGGER.info("Found block by number: " + blockNumber);
            } else {
                view.displayWarning("Block not found with number: " + blockNumber);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error searching block by number", e);
            view.displayError("Search failed: " + e.getMessage());
        }
    }
    
    /**
     * Searches for a transaction by hash
     */
    public void searchTransactionByHash(String txHash) {
        try {
            if (!BlockExplorerUIUtils.isValidTxHash(txHash)) {
                view.displayWarning("Invalid transaction hash format");
                return;
            }
            
            currentSearchQuery = txHash;
            currentSearchType = SearchType.TRANSACTION_HASH;
            
            manager.findTransactionAsync(txHash).thenAccept(result -> {
                if (result.found) {
                    currentSelectedBlock = result.containingBlock;
                    currentSelectedTransaction = result.transaction;
                    currentDisplayedBlocks = Arrays.asList(result.containingBlock);
                    view.displayBlocks(currentDisplayedBlocks);
                    view.displayTransactionDetails(result.transaction, result.containingBlock);
                    LOGGER.info("Found transaction: " + txHash);
                } else {
                    view.displayWarning("Transaction not found: " + txHash);
                }
            }).exceptionally(ex -> {
                LOGGER.log(Level.SEVERE, "Error searching transaction", ex);
                view.displayError("Transaction search failed: " + ex.getMessage());
                return null;
            });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error searching transaction by hash", e);
            view.displayError("Search failed: " + e.getMessage());
        }
    }
    
    /**
     * Searches for transactions by address (sender or recipient)
     */
    public void searchTransactionsByAddress(String address) {
        try {
            currentSearchQuery = address;
            currentSearchType = SearchType.ADDRESS;
            
            List<BlockExplorerManager.TransactionInfo> senderTxs = manager.getTransactionsBySender(address);
            List<BlockExplorerManager.TransactionInfo> recipientTxs = manager.getTransactionsByRecipient(address);
            
            List<BlockExplorerManager.TransactionInfo> allTxs = new ArrayList<>();
            allTxs.addAll(senderTxs);
            allTxs.addAll(recipientTxs);
            
            if (!allTxs.isEmpty()) {
                view.displayAddressTransactions(address, allTxs);
                LOGGER.info("Found " + allTxs.size() + " transactions for address: " + address);
            } else {
                view.displayWarning("No transactions found for address: " + address);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error searching transactions by address", e);
            view.displayError("Address search failed: " + e.getMessage());
        }
    }
    
    // ===== BLOCK NAVIGATION =====
    
    /**
     * Displays block details for a selected block
     */
    public void viewBlockDetails(Block block) {
        try {
            currentSelectedBlock = block;
            view.displayBlockDetails(block);
            List<Transaction> transactions = block.getTransactions();
            if (transactions != null && !transactions.isEmpty()) {
                view.displayBlockTransactions(transactions);
            }
            LOGGER.info("Displaying block details: " + block.getHash());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error viewing block details", e);
            view.displayError("Failed to load block details: " + e.getMessage());
        }
    }
    
    /**
     * Navigates to parent block
     */
    public void goToParentBlock() {
        if (currentSelectedBlock == null) {
            view.displayWarning("No block selected");
            return;
        }
        
        try {
            Block parentBlock = manager.getBlockByHash(currentSelectedBlock.getParentHash());
            if (parentBlock != null) {
                viewBlockDetails(parentBlock);
                LOGGER.info("Navigated to parent block");
            } else {
                view.displayWarning("Parent block not found");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error navigating to parent block", e);
            view.displayError("Navigation failed: " + e.getMessage());
        }
    }
    
    /**
     * Navigates to next block
     */
    public void goToNextBlock() {
        if (currentSelectedBlock == null) {
            view.displayWarning("No block selected");
            return;
        }
        
        try {
            long nextBlockNumber = currentSelectedBlock.getNumber() + 1;
            Block nextBlock = manager.getBlockByNumber(nextBlockNumber);
            if (nextBlock != null) {
                viewBlockDetails(nextBlock);
                LOGGER.info("Navigated to next block");
            } else {
                view.displayWarning("Next block not found");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error navigating to next block", e);
            view.displayError("Navigation failed: " + e.getMessage());
        }
    }
    
    // ===== TRANSACTION OPERATIONS =====
    
    /**
     * Displays transaction details
     */
    public void viewTransactionDetails(Transaction transaction) {
        try {
            currentSelectedTransaction = transaction;
            BlockExplorerManager.TransactionSearchResult result = manager.findTransactionByHash(transaction.getHash());
            if (result.found) {
                view.displayTransactionDetails(transaction, result.containingBlock);
                LOGGER.info("Displaying transaction details: " + transaction.getHash());
            } else {
                view.displayWarning("Transaction details not found");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error viewing transaction details", e);
            view.displayError("Failed to load transaction details: " + e.getMessage());
        }
    }
    
    /**
     * Gets pending transactions
     */
    public void viewPendingTransactions() {
        try {
            List<Transaction> pendingTxs = manager.getPendingTransactions();
            view.displayPendingTransactions(pendingTxs);
            LOGGER.info("Displaying " + pendingTxs.size() + " pending transactions");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error viewing pending transactions", e);
            view.displayError("Failed to load pending transactions: " + e.getMessage());
        }
    }
    
    // ===== STATISTICS & ANALYTICS =====
    
    /**
     * Gets and displays blockchain statistics
     */
    public void viewBlockchainStatistics() {
        try {
            BlockExplorerManager.BlockchainStats stats = manager.getBlockchainStats();
            view.displayBlockchainStats(stats);
            LOGGER.info("Displaying blockchain statistics: " + stats);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error viewing blockchain statistics", e);
            view.displayError("Failed to load statistics: " + e.getMessage());
        }
    }
    
    /**
     * Gets cache statistics
     */
    public void viewCacheStatistics() {
        try {
            Map<String, Object> cacheStats = manager.getCacheStats();
            view.displayCacheStats(cacheStats);
            LOGGER.info("Displaying cache statistics");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error viewing cache statistics", e);
            view.displayError("Failed to load cache statistics: " + e.getMessage());
        }
    }
    
    /**
     * Gets manager metrics
     */
    public void viewMetrics() {
        try {
            BlockExplorerManager.BlockExplorerMetrics metrics = manager.getMetrics();
            view.displayMetrics(metrics);
            LOGGER.info("Displaying block explorer metrics");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error viewing metrics", e);
            view.displayError("Failed to load metrics: " + e.getMessage());
        }
    }
    
    // ===== UTILITY METHODS =====
    
    /**
     * Gets the block manager
     */
    public BlockExplorerManager getManager() {
        return manager;
    }
    
    /**
     * Gets the view
     */
    public BlockExplorerView getView() {
        return view;
    }
    
    /**
     * Gets currently selected block
     */
    public Block getCurrentSelectedBlock() {
        return currentSelectedBlock;
    }
    
    /**
     * Gets currently selected transaction
     */
    public Transaction getCurrentSelectedTransaction() {
        return currentSelectedTransaction;
    }
    
    /**
     * Gets current search query
     */
    public String getCurrentSearchQuery() {
        return currentSearchQuery;
    }
    
    /**
     * Gets current search type
     */
    public SearchType getCurrentSearchType() {
        return currentSearchType;
    }
    
    /**
     * Clears current selection
     */
    public void clearSelection() {
        currentSelectedBlock = null;
        currentSelectedTransaction = null;
        currentSearchQuery = null;
        currentSearchType = SearchType.NONE;
        LOGGER.info("Cleared current selection");
    }
    
    /**
     * Shuts down the controller
     */
    public void shutdown() {
        manager.shutdown();
        LOGGER.info("BlockExplorerController shutdown complete");
    }
}