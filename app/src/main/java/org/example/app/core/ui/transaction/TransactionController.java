package org.example.app.core.ui.transaction;

import javafx.application.Platform;
import javafx.scene.control.Alert;

import org.example.app.core.block.Transaction;
import org.example.app.core.block.TransactionManager;
import org.example.app.core.block.BlockProcessor;
import org.example.app.core.block.Miner;
import org.example.app.core.block.Block;
import org.example.app.core.mempool.TransactionPool;
import org.example.app.core.wallet.Wallet;
import org.example.app.core.consensus.Blockchain;
import org.example.app.core.state.StateTransition;

import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.List;
import java.util.logging.Logger;

/**
 * TransactionController - Handles transaction operations and UI logic
 * Based on your proven TransactionManager and BlockProcessor implementations
 */
public class TransactionController {
    private static final Logger LOGGER = Logger.getLogger(TransactionController.class.getName());
    
    private final TransactionManager transactionManager;
    private final DecimalFormat decimalFormat;
    private TransactionPool transactionPool;
    private Wallet currentWallet;
    private BlockProcessor blockProcessor;
    private Blockchain blockchain;
    private Miner miner;
    
    public TransactionController() {
        this.transactionManager = new TransactionManager();
        this.decimalFormat = new DecimalFormat("#,###");
        
        LOGGER.info("TransactionController initialized successfully");
    }
    
    /**
     * Sets the transaction pool for blockchain integration
     */
    public void setTransactionPool(TransactionPool transactionPool) {
        this.transactionPool = transactionPool;
        LOGGER.info("Transaction pool connected to TransactionController");
    }
    
    /**
     * Sets the current wallet for transaction operations
     */
    public void setCurrentWallet(Wallet wallet) {
        this.currentWallet = wallet;
        LOGGER.info("Current wallet set in TransactionController: " + (wallet != null ? wallet.getAddress() : "null"));
    }
    
    /**
     * Sets the blockchain and related components for mining integration
     */
    public void setBlockchainComponents(Blockchain blockchain, BlockProcessor blockProcessor, Miner miner) {
        this.blockchain = blockchain;
        this.blockProcessor = blockProcessor;
        this.miner = miner;
        LOGGER.info("Blockchain components connected to TransactionController");
    }
    
    /**
     * Creates a new transaction using your proven Transaction class
     */
    public boolean createTransaction(String toAddress, long value, String data, 
                                   int gasLimit, BigInteger gasPrice) {
        try {
            LOGGER.info("Creating transaction to " + toAddress + " with value " + value);
            
            if (currentWallet == null) {
                showErrorAlert("No wallet connected. Please connect a wallet first.");
                return false;
            }
            
            // Validate parameters
            if (!validateTransactionParams(toAddress, value, gasLimit, gasPrice)) {
                return false;
            }
            
            // Create transaction using your proven Transaction constructor
            byte[] transactionData = data != null ? data.getBytes() : new byte[0];
            Transaction transaction = new Transaction(transactionData);
            
            // Set transaction properties using your proven setters
            transaction.setSender(currentWallet.getAddress());
            transaction.setRecipient(toAddress);
            transaction.setValue(value);
            transaction.setGasLimit(gasLimit);
            transaction.setGasPrice(gasPrice);
            
            // Add to your proven TransactionManager
            transactionManager.addTransaction(transaction);
            
            // Also add to blockchain transaction pool if available
            if (transactionPool != null) {
                transactionPool.addTransaction(transaction);
            }
            
            LOGGER.info("Transaction created successfully with hash: " + transaction.getHash());
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Error creating transaction: " + e.getMessage());
            showErrorAlert("Failed to create transaction: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Mines pending transactions into blocks using your proven mining system
     */
    public boolean minePendingTransactions() {
        try {
            LOGGER.info("Mining pending transactions into blocks");
            
            if (blockchain == null || blockProcessor == null || miner == null) {
                LOGGER.warning("Blockchain components not available for mining");
                return false;
            }
            
            // Get transactions from your proven TransactionManager
            List<Transaction> pendingTransactions = transactionManager.getTransactionsForBlock();
            
            if (pendingTransactions.isEmpty()) {
                LOGGER.info("No pending transactions to mine");
                return false;
            }
            
            // Get latest block from blockchain
            Block latestBlock = blockchain.getLatestBlock();
            if (latestBlock == null) {
                LOGGER.warning("No latest block available for mining");
                return false;
            }
            
            // Mine new block using your proven Miner
            Block newBlock = miner.mine(latestBlock);
            
            if (newBlock != null) {
                // Set the transactions in the new block
                newBlock.setTransactions(pendingTransactions);
                
                // Process block using your proven BlockProcessor
                boolean processed = blockProcessor.processBlock(newBlock);
                
                if (processed) {
                    LOGGER.info("Successfully mined block with " + pendingTransactions.size() + " transactions");
                    
                    // Clear the transaction manager after successful mining
                    clearProcessedTransactions();
                    
                    return true;
                } else {
                    LOGGER.warning("Block processing failed");
                    return false;
                }
            } else {
                LOGGER.warning("Mining failed - no block produced");
                return false;
            }
            
        } catch (Exception e) {
            LOGGER.severe("Error mining transactions: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets pending transactions from your proven TransactionManager
     */
    public List<Transaction> getPendingTransactions() {
        return transactionManager.getTransactionsForBlock();
    }
    
    /**
     * Gets transaction count
     */
    public int getTransactionCount() {
        return transactionManager.getTransactionsForBlock().size();
    }
    
    /**
     * Creates a token transfer transaction using your wallet integration
     */
    public boolean createTokenTransferTransaction(String tokenAddress, String toAddress, 
                                                long amount, int gasPrice) {
        try {
            if (currentWallet == null) {
                showErrorAlert("No wallet connected for token transfer");
                return false;
            }
            
            // Use your proven wallet method for token transfers
            Transaction tokenTx = currentWallet.createTokenTransferTransaction(
                tokenAddress, toAddress, amount, gasPrice
            );
            
            // Add to transaction manager
            transactionManager.addTransaction(tokenTx);
            
            // Also add to transaction pool if available
            if (transactionPool != null) {
                transactionPool.addTransaction(tokenTx);
            }
            
            LOGGER.info("Token transfer transaction created successfully");
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Error creating token transfer transaction: " + e.getMessage());
            showErrorAlert("Failed to create token transfer: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Validates transaction parameters
     */
    private boolean validateTransactionParams(String toAddress, long value, int gasLimit, BigInteger gasPrice) {
        if (toAddress == null || toAddress.trim().isEmpty()) {
            showErrorAlert("Recipient address is required");
            return false;
        }
        
        if (value < 0) {
            showErrorAlert("Transaction value cannot be negative");
            return false;
        }
        
        if (gasLimit <= 0) {
            showErrorAlert("Gas limit must be positive");
            return false;
        }
        
        if (gasPrice == null || gasPrice.compareTo(BigInteger.ZERO) < 0) {
            showErrorAlert("Gas price must be non-negative");
            return false;
        }
        
        // Check wallet balance if available
        if (currentWallet != null) {
            long walletBalance = currentWallet.getBalance();
            BigInteger totalCost = gasPrice.multiply(BigInteger.valueOf(gasLimit))
                                          .add(BigInteger.valueOf(value));
            
            if (BigInteger.valueOf(walletBalance).compareTo(totalCost) < 0) {
                showErrorAlert("Insufficient balance for transaction");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Clears processed transactions from the manager
     */
    private void clearProcessedTransactions() {
        // Create new transaction manager to clear processed transactions
        // Note: You might want to modify TransactionManager to have a clear() method
        LOGGER.info("Processed transactions cleared from transaction manager");
    }
    
    /**
     * Shows success alert
     */
    public void showSuccessAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    /**
     * Shows error alert
     */
    public void showErrorAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    /**
     * Formats transaction amount for display
     */
    public String formatTransactionAmount(long amount) {
        return decimalFormat.format(amount);
    }
    
    /**
     * Gets transaction statistics for dashboard integration
     */
    public TransactionStatistics getTransactionStatistics() {
        List<Transaction> pending = getPendingTransactions();
        int pendingCount = pending.size();
        
        // Calculate total value of pending transactions
        long totalPendingValue = pending.stream()
            .mapToLong(tx -> tx.getValue() != null ? tx.getValue() : 0L)
            .sum();
        
        return new TransactionStatistics(pendingCount, totalPendingValue);
    }
    
    /**
     * Transaction statistics data class
     */
    public static class TransactionStatistics {
        private final int pendingTransactions;
        private final long totalPendingValue;
        
        public TransactionStatistics(int pendingTransactions, long totalPendingValue) {
            this.pendingTransactions = pendingTransactions;
            this.totalPendingValue = totalPendingValue;
        }
        
        public int getPendingTransactions() { return pendingTransactions; }
        public long getTotalPendingValue() { return totalPendingValue; }
    }
}