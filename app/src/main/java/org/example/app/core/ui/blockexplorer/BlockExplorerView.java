package org.example.app.core.ui.blockexplorer;

import org.example.app.core.block.Block;
import org.example.app.core.block.Transaction;

import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

public class BlockExplorerView {
    private static final Logger LOGGER = Logger.getLogger(BlockExplorerView.class.getName());
    
    private final BlockExplorerController controller;
    private ViewUpdateListener updateListener;
    private boolean autoRefresh;
    private int autoRefreshIntervalMs;
    
    public BlockExplorerView(BlockExplorerController controller) {
        this.controller = controller;
        this.autoRefresh = false;
        this.autoRefreshIntervalMs = 5000; // 5 seconds default
        LOGGER.info("BlockExplorerView initialized");
    }
    
    // ===== BLOCK DISPLAY METHODS =====
    
    /**
     * Displays a list of blocks
     */
    public void displayBlocks(List<Block> blocks) {
        try {
            if (blocks == null || blocks.isEmpty()) {
                displayInfo("No blocks to display");
                return;
            }
            
            StringBuilder blockList = new StringBuilder();
            blockList.append("\n╔════════════════════════════════════════════════════════════════╗\n");
            blockList.append("║                        BLOCK LIST                              ║\n");
            blockList.append("╠════════════════════════════════════════════════════════════════╣\n");
            
            for (Block block : blocks) {
                blockList.append(formatBlockRow(block));
            }
            
            blockList.append("╚════════════════════════════════════════════════════════════════╝\n");
            
            displayInfo(blockList.toString());
            notifyUpdateListener(ViewUpdateType.BLOCKS_DISPLAYED);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error displaying blocks", e);
            displayError("Failed to display blocks: " + e.getMessage());
        }
    }
    
    /**
     * Displays detailed information about a single block
     */
    public void displayBlockDetails(Block block) {
        try {
            if (block == null) {
                displayWarning("Block is null");
                return;
            }
            
            StringBuilder details = new StringBuilder();
            details.append("\n╔════════════════════════════════════════════════════════════════╗\n");
            details.append("║                       BLOCK DETAILS                            ║\n");
            details.append("╠════════════════════════════════════════════════════════════════╣\n");
            
            details.append(formatDetailRow("Block Number", BlockExplorerUIUtils.formatBlockNumber(block.getNumber())));
            details.append(formatDetailRow("Block Hash", BlockExplorerUIUtils.formatBlockHash(block.getHash())));
            details.append(formatDetailRow("Parent Hash", BlockExplorerUIUtils.formatBlockHash(block.getParentHash())));
            details.append(formatDetailRow("Miner", BlockExplorerUIUtils.formatAddress(block.getMiner())));
            details.append(formatDetailRow("Timestamp", BlockExplorerUIUtils.formatTimestamp(block.getTimestamp())));
            details.append(formatDetailRow("Time Ago", BlockExplorerUIUtils.getTimeAgo(block.getTimestamp())));
            details.append(formatDetailRow("Difficulty", BlockExplorerUIUtils.formatDifficulty(block.getDifficulty())));
            
            int txCount = block.getTransactions() != null ? block.getTransactions().size() : 0;
            details.append(formatDetailRow("Transactions", BlockExplorerUIUtils.formatTransactionCount(txCount)));
            
            if (block.getStateRoot() != null) {
                details.append(formatDetailRow("State Root", Arrays.toString(block.getStateRoot())));
            }
            
            details.append("╚════════════════════════════════════════════════════════════════╝\n");
            
            displayInfo(details.toString());
            notifyUpdateListener(ViewUpdateType.BLOCK_DETAILS_DISPLAYED);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error displaying block details", e);
            displayError("Failed to display block details: " + e.getMessage());
        }
    }
    
    /**
     * Displays transactions in a block
     */
    public void displayBlockTransactions(List<Transaction> transactions) {
        try {
            if (transactions == null || transactions.isEmpty()) {
                displayInfo("No transactions in this block");
                return;
            }
            
            StringBuilder txList = new StringBuilder();
            txList.append("\n╔════════════════════════════════════════════════════════════════╗\n");
            txList.append("║                     BLOCK TRANSACTIONS                         ║\n");
            txList.append("╠════════════════════════════════════════════════════════════════╣\n");
            
            for (int i = 0; i < transactions.size(); i++) {
                Transaction tx = transactions.get(i);
                txList.append(formatTransactionRow(tx, i));
            }
            
            txList.append("╚════════════════════════════════════════════════════════════════╝\n");
            
            displayInfo(txList.toString());
            notifyUpdateListener(ViewUpdateType.TRANSACTIONS_DISPLAYED);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error displaying block transactions", e);
            displayError("Failed to display transactions: " + e.getMessage());
        }
    }
    
    // ===== TRANSACTION DISPLAY METHODS =====
    
    /**
     * Displays details about a transaction
     */
    public void displayTransactionDetails(Transaction transaction, Block containingBlock) {
        try {
            if (transaction == null) {
                displayWarning("Transaction is null");
                return;
            }
            
            StringBuilder details = new StringBuilder();
            details.append("\n╔════════════════════════════════════════════════════════════════╗\n");
            details.append("║                    TRANSACTION DETAILS                         ║\n");
            details.append("╠════════════════════════════════════════════════════════════════╣\n");
            
            details.append(formatDetailRow("Transaction Hash", BlockExplorerUIUtils.formatTxHash(transaction.getHash())));
            details.append(formatDetailRow("From", BlockExplorerUIUtils.formatAddress(transaction.getSender())));
            details.append(formatDetailRow("To", BlockExplorerUIUtils.formatAddress(transaction.getRecipient())));
            details.append(formatDetailRow("Value", String.valueOf(transaction.getValue())));
            details.append(formatDetailRow("Gas Limit", BlockExplorerUIUtils.formatGasLimit(transaction.getGasLimit())));
            details.append(formatDetailRow("Gas Price", BlockExplorerUIUtils.formatGasPrice(transaction.getGasPrice().longValue())));
            details.append(formatDetailRow("Nonce", String.valueOf(transaction.getNonce())));
            details.append(formatDetailRow("Status", transaction.getStatus().toString()));
            
            if (containingBlock != null) {
                details.append(formatDetailRow("Block Number", BlockExplorerUIUtils.formatBlockNumber(containingBlock.getNumber())));
                details.append(formatDetailRow("Block Hash", BlockExplorerUIUtils.formatBlockHash(containingBlock.getHash())));
                details.append(formatDetailRow("Block Timestamp", BlockExplorerUIUtils.formatTimestamp(containingBlock.getTimestamp())));
            }
            
            details.append("╚════════════════════════════════════════════════════════════════╝\n");
            
            displayInfo(details.toString());
            notifyUpdateListener(ViewUpdateType.TRANSACTION_DETAILS_DISPLAYED);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error displaying transaction details", e);
            displayError("Failed to display transaction details: " + e.getMessage());
        }
    }
    
    /**
     * Displays pending transactions
     */
    public void displayPendingTransactions(List<Transaction> pendingTransactions) {
        try {
            if (pendingTransactions == null || pendingTransactions.isEmpty()) {
                displayInfo("No pending transactions");
                return;
            }
            
            StringBuilder txList = new StringBuilder();
            txList.append("\n╔════════════════════════════════════════════════════════════════╗\n");
            txList.append("║                    PENDING TRANSACTIONS                        ║\n");
            txList.append("╠════════════════════════════════════════════════════════════════╣\n");
            
            for (int i = 0; i < pendingTransactions.size(); i++) {
                Transaction tx = pendingTransactions.get(i);
                txList.append(formatTransactionRow(tx, i));
            }
            
            txList.append("╚════════════════════════════════════════════════════════════════╝\n");
            
            displayInfo(txList.toString());
            notifyUpdateListener(ViewUpdateType.PENDING_TRANSACTIONS_DISPLAYED);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error displaying pending transactions", e);
            displayError("Failed to display pending transactions: " + e.getMessage());
        }
    }
    
    /**
     * Displays transactions for a specific address
     */
    public void displayAddressTransactions(String address, List<BlockExplorerManager.TransactionInfo> transactions) {
        try {
            StringBuilder txList = new StringBuilder();
            txList.append("\n╔════════════════════════════════════════════════════════════════╗\n");
            txList.append("║              TRANSACTIONS FOR ADDRESS                          ║\n");
            txList.append(String.format("║ %s \n", BlockExplorerUIUtils.truncateText(address, 60)));
            txList.append("╠════════════════════════════════════════════════════════════════╣\n");
            
            for (int i = 0; i < transactions.size(); i++) {
                BlockExplorerManager.TransactionInfo txInfo = transactions.get(i);
                txList.append(String.format("│ [%d] TX: %s (Block #%d)\n", 
                    i, 
                    BlockExplorerUIUtils.formatTxHash(txInfo.transaction.getHash()),
                    txInfo.blockNumber));
            }
            
            txList.append("╚════════════════════════════════════════════════════════════════╝\n");
            
            displayInfo(txList.toString());
            notifyUpdateListener(ViewUpdateType.ADDRESS_TRANSACTIONS_DISPLAYED);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error displaying address transactions", e);
            displayError("Failed to display address transactions: " + e.getMessage());
        }
    }
    
    // ===== STATISTICS DISPLAY METHODS =====
    
    /**
     * Displays blockchain statistics
     */
    public void displayBlockchainStats(BlockExplorerManager.BlockchainStats stats) {
        try {
            if (stats == null) {
                displayWarning("Statistics are not available");
                return;
            }
            
            StringBuilder statsDisplay = new StringBuilder();
            statsDisplay.append("\n╔════════════════════════════════════════════════════════════════╗\n");
            statsDisplay.append("║                  BLOCKCHAIN STATISTICS                        ║\n");
            statsDisplay.append("╠════════════════════════════════════════════════════════════════╣\n");
            
            statsDisplay.append(formatDetailRow("Total Blocks", String.valueOf(stats.totalBlocks)));
            statsDisplay.append(formatDetailRow("Total Transactions", String.valueOf(stats.totalTransactions)));
            statsDisplay.append(formatDetailRow("Pending Transactions", String.valueOf(stats.pendingTransactions)));
            statsDisplay.append(formatDetailRow("Last Block Time", BlockExplorerUIUtils.formatTimestamp(stats.lastBlockTime)));
            statsDisplay.append(formatDetailRow("Average Block Time", stats.averageBlockTime + " ms"));
            
            statsDisplay.append("╚════════════════════════════════════════════════════════════════╝\n");
            
            displayInfo(statsDisplay.toString());
            notifyUpdateListener(ViewUpdateType.STATS_DISPLAYED);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error displaying blockchain statistics", e);
            displayError("Failed to display statistics: " + e.getMessage());
        }
    }
    
    /**
     * Displays cache statistics
     */
    public void displayCacheStats(Map<String, Object> cacheStats) {
        try {
            StringBuilder stats = new StringBuilder();
            stats.append("\n╔════════════════════════════════════════════════════════════════╗\n");
            stats.append("║                      CACHE STATISTICS                          ║\n");
            stats.append("╠════════════════════════════════════════════════════════════════╣\n");
            
            for (Map.Entry<String, Object> entry : cacheStats.entrySet()) {
                stats.append(formatDetailRow(entry.getKey(), entry.getValue().toString()));
            }
            
            stats.append("╚════════════════════════════════════════════════════════════════╝\n");
            
            displayInfo(stats.toString());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error displaying cache statistics", e);
            displayError("Failed to display cache statistics: " + e.getMessage());
        }
    }
    
    /**
     * Displays block explorer metrics
     */
    public void displayMetrics(BlockExplorerManager.BlockExplorerMetrics metrics) {
        try {
            StringBuilder metricsDisplay = new StringBuilder();
            metricsDisplay.append("\n╔════════════════════════════════════════════════════════════════╗\n");
            metricsDisplay.append("║                    EXPLORER METRICS                           ║\n");
            metricsDisplay.append("╠════════════════════════════════════════════════════════════════╣\n");
            
            metricsDisplay.append(formatDetailRow("Blocks Queried", String.valueOf(metrics.getBlocksQueried())));
            metricsDisplay.append(formatDetailRow("Transactions Queried", String.valueOf(metrics.getTransactionsQueried())));
            metricsDisplay.append(formatDetailRow("Cache Hits", String.valueOf(metrics.getCacheHits())));
            metricsDisplay.append(formatDetailRow("Stats Requests", String.valueOf(metrics.getStatsRequests())));
            
            metricsDisplay.append("╚════════════════════════════════════════════════════════════════╝\n");
            
            displayInfo(metricsDisplay.toString());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error displaying metrics", e);
            displayError("Failed to display metrics: " + e.getMessage());
        }
    }
    
    // ===== FORMATTING HELPER METHODS =====
    
    /**
     * Formats a single block row for display
     */
    private String formatBlockRow(Block block) {
        int txCount = block.getTransactions() != null ? block.getTransactions().size() : 0;
        return String.format("│ #%-6d | %s | %s | %d txs │ %s\n",
            block.getNumber(),
            BlockExplorerUIUtils.formatBlockHash(block.getHash()),
            BlockExplorerUIUtils.formatAddress(block.getMiner()),
            txCount,
            BlockExplorerUIUtils.getTimeAgo(block.getTimestamp()));
    }
    
    /**
     * Formats a detail row for block/transaction display
     */
    private String formatDetailRow(String label, String value) {
        return String.format("│ %-25s │ %s\n", label + ":", BlockExplorerUIUtils.truncateText(value, 35));
    }
    
    /**
     * Formats a transaction row
     */
    private String formatTransactionRow(Transaction tx, int index) {
        return String.format("│ [%d] %s → %s | %s\n",
            index,
            BlockExplorerUIUtils.formatAddress(tx.getSender()),
            BlockExplorerUIUtils.formatAddress(tx.getRecipient()),
            BlockExplorerUIUtils.formatTxHash(tx.getHash()));
    }
    
    // ===== MESSAGE DISPLAY METHODS =====
    
    /**
     * Displays an informational message
     */
    public void displayInfo(String message) {
        LOGGER.info("INFO: " + message);
        System.out.println("ℹ️  " + message);
    }
    
    /**
     * Displays a warning message
     */
    public void displayWarning(String message) {
        LOGGER.warning("WARNING: " + message);
        System.out.println("⚠️  " + message);
    }
    
    /**
     * Displays an error message
     */
    public void displayError(String message) {
        LOGGER.severe("ERROR: " + message);
        System.out.println("❌ " + message);
    }
    
    /**
     * Displays a success message
     */
    public void displaySuccess(String message) {
        LOGGER.info("SUCCESS: " + message);
        System.out.println("✅ " + message);
    }
    
    // ===== AUTO-REFRESH METHODS =====
    
    /**
     * Enables auto-refresh of the block explorer
     */
    public void enableAutoRefresh(int intervalMs) {
        this.autoRefresh = true;
        this.autoRefreshIntervalMs = intervalMs;
        displayInfo("Auto-refresh enabled (interval: " + intervalMs + "ms)");
        startAutoRefresh();
    }
    
    /**
     * Disables auto-refresh
     */
    public void disableAutoRefresh() {
        this.autoRefresh = false;
        displayInfo("Auto-refresh disabled");
    }
    
    /**
     * Starts the auto-refresh timer
     */
    private void startAutoRefresh() {
        if (!autoRefresh) {
            return;
        }
        
        new Thread(() -> {
            while (autoRefresh) {
                try {
                    Thread.sleep(autoRefreshIntervalMs);
                    if (autoRefresh) {
                        controller.refreshAll();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }
    
    // ===== EVENT LISTENER METHODS =====
    
    /**
     * Sets the update listener
     */
    public void setUpdateListener(ViewUpdateListener listener) {
        this.updateListener = listener;
    }
    
    /**
     * Notifies the update listener
     */
    private void notifyUpdateListener(ViewUpdateType updateType) {
        if (updateListener != null) {
            updateListener.onViewUpdated(updateType);
        }
    }
    
    // ===== INNER INTERFACES & ENUMS =====
    
    /**
     * Listener for view updates
     */
    public interface ViewUpdateListener {
        void onViewUpdated(ViewUpdateType updateType);
    }
    
    /**
     * Types of view updates
     */
    public enum ViewUpdateType {
        BLOCKS_DISPLAYED,
        BLOCK_DETAILS_DISPLAYED,
        TRANSACTIONS_DISPLAYED,
        TRANSACTION_DETAILS_DISPLAYED,
        PENDING_TRANSACTIONS_DISPLAYED,
        ADDRESS_TRANSACTIONS_DISPLAYED,
        STATS_DISPLAYED,
        REFRESH_COMPLETE
    }
}