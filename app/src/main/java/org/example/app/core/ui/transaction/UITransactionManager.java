package org.example.app.core.ui.transaction;

import org.example.app.core.block.Transaction;
import org.example.app.core.block.TransactionManager; // Your proven core class
import org.example.app.core.block.BlockProcessor;
import org.example.app.core.block.Miner;
import org.example.app.core.block.Block;
import org.example.app.core.mempool.TransactionPool;
import org.example.app.core.wallet.Wallet;
import org.example.app.core.consensus.Blockchain;
import org.example.app.core.consensus.HashUtils;

import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.math.BigInteger;

/**
 * UI TransactionManager - Manages transaction operations for the UI layer
 * Integrates with your proven core TransactionManager and blockchain components
 */
public class UITransactionManager {  // ONLY CHANGE: Fixed class name
    private static final Logger LOGGER = Logger.getLogger(UITransactionManager.class.getName());  // ONLY CHANGE: Fixed logger class name
    
    // Use your proven core TransactionManager
    private final org.example.app.core.block.TransactionManager coreTransactionManager;
    
    // Blockchain components
    private TransactionPool transactionPool;
    private Wallet currentWallet;
    private BlockProcessor blockProcessor;
    private Blockchain blockchain;
    private Miner miner;
    
    // Mining configuration
    private boolean autoMining = false;
    private long miningDifficulty = 1000L;
    
    public UITransactionManager() {  // ONLY CHANGE: Fixed constructor name
        // Initialize your proven core TransactionManager
        this.coreTransactionManager = new org.example.app.core.block.TransactionManager();
        
        LOGGER.info("UI TransactionManager initialized with core TransactionManager");
    }
    
    /**
     * Sets the transaction pool for blockchain integration
     */
    public void setTransactionPool(TransactionPool transactionPool) {
        this.transactionPool = transactionPool;
        LOGGER.info("Transaction pool connected to UI TransactionManager");
    }
    
    /**
     * Sets the current wallet for transaction operations
     */
    public void setCurrentWallet(Wallet wallet) {
        this.currentWallet = wallet;
        LOGGER.info("Current wallet set: " + (wallet != null ? wallet.getAddress() : "null"));
    }
    
    /**
     * Sets blockchain components for mining integration
     */
    public void setBlockchainComponents(Blockchain blockchain, BlockProcessor blockProcessor, Miner miner) {
        this.blockchain = blockchain;
        this.blockProcessor = blockProcessor;
        this.miner = miner;
        LOGGER.info("Blockchain components connected for mining");
    }
    
    /**
     * Adds a transaction using your proven core TransactionManager
     */
    public boolean addTransaction(Transaction transaction) {
        try {
            LOGGER.info("Adding transaction to core TransactionManager");
            
            // Add to your proven core manager
            coreTransactionManager.addTransaction(transaction);
            
            // Also add to blockchain transaction pool if available
            if (transactionPool != null) {
                transactionPool.addTransaction(transaction);
                LOGGER.info("Transaction added to blockchain transaction pool");
            }
            
            // Auto-mine if enabled
            if (autoMining) {
                mineTransactionsToBlock();
            }
            
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Error adding transaction: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets pending transactions using your proven core TransactionManager
     */
    public List<Transaction> getPendingTransactions() {
        return coreTransactionManager.getTransactionsForBlock();
    }
    
    /**
     * Creates and processes a block with pending transactions
     * Uses your proven BlockProcessor and Miner
     */
    public Block mineTransactionsToBlock() {
        try {
            LOGGER.info("Mining transactions to block using proven components");
            
            if (blockchain == null || blockProcessor == null || miner == null) {
                LOGGER.warning("Mining components not available");
                return null;
            }
            
            // Get transactions from your proven core manager
            List<Transaction> transactions = coreTransactionManager.getTransactionsForBlock();
            
            if (transactions.isEmpty()) {
                LOGGER.info("No transactions to mine");
                return null;
            }
            
            LOGGER.info("Mining " + transactions.size() + " transactions into block");
            
            // Get latest block for mining
            Block latestBlock = blockchain.getLatestBlock();
            if (latestBlock == null) {
                // Create genesis block if none exists
                latestBlock = createGenesisBlock();
                blockchain.addBlock(latestBlock);
            }
            
            // Create new block with transactions using your proven pattern
            Block newBlock = createBlockWithTransactions(latestBlock, transactions);
            
            // Process block using your proven BlockProcessor
            boolean processed = blockProcessor.processBlock(newBlock);
            
            if (processed) {
                LOGGER.info("Block mined and processed successfully: " + newBlock.getHash());
                
                // Clear processed transactions (create new manager)
                // Note: Your core TransactionManager doesn't have clear method,
                // so we'd need to create a new instance or modify the core class
                
                return newBlock;
            } else {
                LOGGER.warning("Block processing failed");
                return null;
            }
            
        } catch (Exception e) {
            LOGGER.severe("Error mining transactions to block: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Creates a new block with transactions using blockchain patterns
     */
    private Block createBlockWithTransactions(Block parentBlock, List<Transaction> transactions) {
        try {
            // Generate block properties
            String parentHash = parentBlock.getHash();
            long blockNumber = parentBlock.getNumber() + 1;
            long timestamp = System.currentTimeMillis();
            String minerAddress = currentWallet != null ? currentWallet.getAddress() : "default-miner";
            
            // Calculate block hash using your proven HashUtils
            String blockData = parentHash + blockNumber + timestamp + transactions.size();
            String blockHash = HashUtils.generateHash(blockData);
            
            // Create block using your proven Block constructor
            Block newBlock = new Block(
                blockHash,
                parentHash,
                minerAddress,
                timestamp,
                miningDifficulty,
                blockNumber,
                new ArrayList<>(transactions)
            );
            
            LOGGER.info("Created new block: " + blockHash + " with " + transactions.size() + " transactions");
            return newBlock;
            
        } catch (Exception e) {
            LOGGER.severe("Error creating block with transactions: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Creates genesis block if none exists
     */
    private Block createGenesisBlock() {
        LOGGER.info("Creating genesis block");
        
        String genesisHash = HashUtils.generateHash("genesis_block");
        long timestamp = System.currentTimeMillis();
        
        return new Block(
            genesisHash,
            null,
            "genesis-miner",
            timestamp,
            miningDifficulty,
            0,
            new ArrayList<>()
        );
    }
    
    /**
     * Gets transaction statistics for UI display
     */
    public TransactionStatistics getStatistics() {
        List<Transaction> pending = getPendingTransactions();
        int pendingCount = pending.size();
        
        long totalValue = 0;
        long totalFees = BigInteger.ZERO.longValue();
        
        for (Transaction tx : pending) {
            if (tx.getValue() != null) {
                totalValue += tx.getValue();
            }
            if (tx.getGasPrice() != null) {
                totalFees += tx.getGasPrice().multiply(BigInteger.valueOf(tx.getGasLimit())).longValue();
            }
        }
        
        return new TransactionStatistics(pendingCount, totalValue, totalFees);
    }
    
    /**
     * Enables or disables auto-mining
     */
    public void setAutoMining(boolean autoMining) {
        this.autoMining = autoMining;
        LOGGER.info("Auto-mining " + (autoMining ? "enabled" : "disabled"));
    }
    
    /**
     * Sets mining difficulty
     */
    public void setMiningDifficulty(long difficulty) {
        this.miningDifficulty = difficulty;
        if (miner != null) {
            miner.setDifficulty(difficulty);
        }
        LOGGER.info("Mining difficulty set to: " + difficulty);
    }
    
    /**
     * Gets transaction count
     */
    public int getTransactionCount() {
        return coreTransactionManager.getTransactionsForBlock().size();
    }
    
    /**
     * Checks if auto-mining is enabled
     */
    public boolean isAutoMining() {
        return autoMining;
    }
    
    /**
     * Gets mining difficulty
     */
    public long getMiningDifficulty() {
        return miningDifficulty;
    }
    
    /**
     * Transaction statistics data class
     */
    public static class TransactionStatistics {
        private final int pendingTransactions;
        private final long totalValue;
        private final long totalFees;
        
        public TransactionStatistics(int pendingTransactions, long totalValue, long totalFees) {
            this.pendingTransactions = pendingTransactions;
            this.totalValue = totalValue;
            this.totalFees = totalFees;
        }
        
        public int getPendingTransactions() { return pendingTransactions; }
        public long getTotalValue() { return totalValue; }
        public long getTotalFees() { return totalFees; }
    }
}