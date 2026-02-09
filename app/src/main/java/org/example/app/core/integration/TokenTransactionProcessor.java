package org.example.app.core.integration;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.example.app.core.blockchain.transaction.TokenBankTransaction;
import org.example.app.core.state.StateManager;
import org.example.app.core.state.WorldState;
import org.example.app.core.state.StateObject;
import org.example.app.core.types.Address;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TokenTransactionProcessor handles the processing of token bank transactions.
 * 
 * Responsibilities:
 * - Execute token transfers between addresses
 * - Update state for sender and recipient
 * - Maintain token balances
 * - Ensure atomic operations
 * - Track transaction history
 */
public class TokenTransactionProcessor {
    private static final Logger logger = Logger.getLogger(TokenTransactionProcessor.class.getName());
    
    // State management
    private final StateManager stateManager;
    private final WorldState worldState;
    
    // Token balance tracking
    private final ConcurrentHashMap<String, TokenBalance> tokenBalances;
    
    // Transaction history
    private final ConcurrentLinkedQueue<ProcessedTransaction> transactionHistory;
    
    // Lock manager for atomic operations
    private final ConcurrentHashMap<String, ReentrantLock> addressLocks;
    
    // Metrics
    private final AtomicLong totalProcessed;
    private final AtomicLong totalFailed;
    
    public TokenTransactionProcessor(StateManager stateManager, WorldState worldState) {
        this.stateManager = stateManager;
        this.worldState = worldState;
        this.tokenBalances = new ConcurrentHashMap<>();
        this.transactionHistory = new ConcurrentLinkedQueue<>();
        this.addressLocks = new ConcurrentHashMap<>();
        this.totalProcessed = new AtomicLong(0);
        this.totalFailed = new AtomicLong(0);
        
        logger.info("TokenTransactionProcessor initialized");
    }
    
    /**
     * Process a token bank transaction
     */
    public boolean processTransaction(TokenBankTransaction transaction) {
        String fromAddress = transaction.getFrom();
        String toAddress = transaction.getTo();
        
        // Get locks for both addresses (ordered to prevent deadlock)
        List<String> addresses = Arrays.asList(fromAddress, toAddress);
        Collections.sort(addresses);
        
        List<ReentrantLock> locks = new ArrayList<>();
        for (String address : addresses) {
            locks.add(addressLocks.computeIfAbsent(address, k -> new ReentrantLock()));
        }
        
        // Acquire all locks
        for (ReentrantLock lock : locks) {
            lock.lock();
        }
        
        try {
            logger.info("Processing token transaction: " + transaction.getTokenSymbol() + 
                " from " + fromAddress + " to " + toAddress);
            
            // Validate sender has sufficient balance
            if (!hasSufficientBalance(fromAddress, transaction.getTokenSymbol(), transaction.getAmount())) {
                logger.warning("Insufficient balance for transaction");
                totalFailed.incrementAndGet();
                return false;
            }
            
            // Execute transfer
            boolean transferred = executeTransfer(
                fromAddress,
                toAddress,
                transaction.getTokenSymbol(),
                transaction.getAmount()
            );
            
            if (!transferred) {
                logger.warning("Transfer execution failed");
                totalFailed.incrementAndGet();
                return false;
            }
            
            // Update state
            updateState(transaction);
            
            // Record in history
            recordTransaction(transaction, true);
            
            totalProcessed.incrementAndGet();
            logger.info("Token transaction processed successfully");
            return true;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing token transaction", e);
            recordTransaction(transaction, false);
            totalFailed.incrementAndGet();
            return false;
        } finally {
            // Release all locks in reverse order
            for (int i = locks.size() - 1; i >= 0; i--) {
                locks.get(i).unlock();
            }
        }
    }
    
    /**
     * Check if address has sufficient balance
     */
    private boolean hasSufficientBalance(String address, String tokenSymbol, BigInteger amount) {
        String balanceKey = address + ":" + tokenSymbol;
        TokenBalance balance = tokenBalances.get(balanceKey);
        
        if (balance == null) {
            // Check state manager
            StateObject stateObject = stateManager.getState(address);
            if (stateObject == null) {
                return false;
            }
            
            BigInteger stateBalance = stateObject.getBalance();
            return stateBalance != null && stateBalance.compareTo(amount) >= 0;
        }
        
        return balance.getBalance().compareTo(amount) >= 0;
    }
    
    /**
     * Execute token transfer
     */
    private boolean executeTransfer(String from, String to, String tokenSymbol, BigInteger amount) {
        try {
            // Debit from sender
            String fromKey = from + ":" + tokenSymbol;
            TokenBalance fromBalance = tokenBalances.computeIfAbsent(
                fromKey,
                k -> loadBalanceFromState(from, tokenSymbol)
            );
            
            if (!fromBalance.debit(amount)) {
                return false;
            }
            
            // Credit to recipient
            String toKey = to + ":" + tokenSymbol;
            TokenBalance toBalance = tokenBalances.computeIfAbsent(
                toKey,
                k -> loadBalanceFromState(to, tokenSymbol)
            );
            
            toBalance.credit(amount);
            
            logger.fine("Transferred " + amount + " " + tokenSymbol + " from " + from + " to " + to);
            return true;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error executing transfer", e);
            return false;
        }
    }
    
    /**
     * Load balance from state
     */
    private TokenBalance loadBalanceFromState(String address, String tokenSymbol) {
        StateObject stateObject = stateManager.getState(address);
        
        if (stateObject == null) {
            // Create new with zero balance
            return new TokenBalance(address, tokenSymbol, BigInteger.ZERO);
        }
        
        BigInteger balance = stateObject.getBalance();
        return new TokenBalance(address, tokenSymbol, balance != null ? balance : BigInteger.ZERO);
    }
    
    /**
     * Update state after transaction
     */
    private void updateState(TokenBankTransaction transaction) {
        String from = transaction.getFrom();
        String to = transaction.getTo();
        String tokenSymbol = transaction.getTokenSymbol();
        
        // Update sender state
        String fromKey = from + ":" + tokenSymbol;
        TokenBalance fromBalance = tokenBalances.get(fromKey);
        if (fromBalance != null) {
            stateManager.updateBalance(from, fromBalance.getBalance());
        }
        
        // Update recipient state
        String toKey = to + ":" + tokenSymbol;
        TokenBalance toBalance = tokenBalances.get(toKey);
        if (toBalance != null) {
            stateManager.updateBalance(to, toBalance.getBalance());
        }
    }
    
    /**
     * Record transaction in history
     */
    private void recordTransaction(TokenBankTransaction transaction, boolean success) {
        ProcessedTransaction record = new ProcessedTransaction(
            transaction.getHash(),
            transaction.getFrom(),
            transaction.getTo(),
            transaction.getTokenSymbol(),
            transaction.getAmount(),
            success,
            System.currentTimeMillis()
        );
        
        transactionHistory.offer(record);
        
        // Keep only last 10000 transactions
        while (transactionHistory.size() > 10000) {
            transactionHistory.poll();
        }
    }
    
    /**
     * Get token balance for address
     */
    public BigInteger getBalance(String address, String tokenSymbol) {
        String balanceKey = address + ":" + tokenSymbol;
        TokenBalance balance = tokenBalances.get(balanceKey);
        
        if (balance == null) {
            balance = loadBalanceFromState(address, tokenSymbol);
            tokenBalances.put(balanceKey, balance);
        }
        
        return balance.getBalance();
    }
    
    /**
     * Get transaction history for address
     */
    public List<ProcessedTransaction> getTransactionHistory(String address, int limit) {
        List<ProcessedTransaction> history = new ArrayList<>();
        
        for (ProcessedTransaction tx : transactionHistory) {
            if (tx.getFrom().equals(address) || tx.getTo().equals(address)) {
                history.add(tx);
                if (history.size() >= limit) {
                    break;
                }
            }
        }
        
        return history;
    }
    
    /**
     * Get processing statistics
     */
    public ProcessingStats getStats() {
        return new ProcessingStats(
            totalProcessed.get(),
            totalFailed.get(),
            tokenBalances.size(),
            transactionHistory.size()
        );
    }
    
    /**
     * Token balance tracker
     */
    private static class TokenBalance {
        private final String address;
        private final String tokenSymbol;
        private BigInteger balance;
        
        TokenBalance(String address, String tokenSymbol, BigInteger initialBalance) {
            this.address = address;
            this.tokenSymbol = tokenSymbol;
            this.balance = initialBalance;
        }
        
        synchronized boolean debit(BigInteger amount) {
            if (balance.compareTo(amount) < 0) {
                return false;
            }
            balance = balance.subtract(amount);
            return true;
        }
        
        synchronized void credit(BigInteger amount) {
            balance = balance.add(amount);
        }
        
        synchronized BigInteger getBalance() {
            return balance;
        }
    }
    
    /**
     * Processed transaction record
     */
    public static class ProcessedTransaction {
        private final String txHash;
        private final String from;
        private final String to;
        private final String tokenSymbol;
        private final BigInteger amount;
        private final boolean success;
        private final long timestamp;
        
        ProcessedTransaction(String txHash, String from, String to, String tokenSymbol,
                           BigInteger amount, boolean success, long timestamp) {
            this.txHash = txHash;
            this.from = from;
            this.to = to;
            this.tokenSymbol = tokenSymbol;
            this.amount = amount;
            this.success = success;
            this.timestamp = timestamp;
        }
        
        public String getTxHash() { return txHash; }
        public String getFrom() { return from; }
        public String getTo() { return to; }
        public String getTokenSymbol() { return tokenSymbol; }
        public BigInteger getAmount() { return amount; }
        public boolean isSuccess() { return success; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Processing statistics
     */
    public static class ProcessingStats {
        private final long totalProcessed;
        private final long totalFailed;
        private final int activeBalances;
        private final int historySize;
        
        ProcessingStats(long totalProcessed, long totalFailed, int activeBalances, int historySize) {
            this.totalProcessed = totalProcessed;
            this.totalFailed = totalFailed;
            this.activeBalances = activeBalances;
            this.historySize = historySize;
        }
        
        public long getTotalProcessed() { return totalProcessed; }
        public long getTotalFailed() { return totalFailed; }
        public int getActiveBalances() { return activeBalances; }
        public int getHistorySize() { return historySize; }
        
        public double getSuccessRate() {
            long total = totalProcessed + totalFailed;
            return total > 0 ? (double) totalProcessed / total : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "ProcessingStats{processed=%d, failed=%d, successRate=%.2f%%, balances=%d, history=%d}",
                totalProcessed, totalFailed, getSuccessRate() * 100, activeBalances, historySize
            );
        }
    }
}
