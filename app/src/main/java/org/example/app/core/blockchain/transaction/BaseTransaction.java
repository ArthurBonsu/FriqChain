package org.example.app.core.blockchain.transaction;

import org.example.app.core.block.Transaction;
import org.example.app.core.types.Address;
import org.example.app.core.types.Amount;

import java.util.Date;
import java.util.UUID;

/**
 * BaseTransaction is an abstract base class for all TINC Blockchain transactions.
 * It provides common functionality and ensures consistent structure across transaction types.
 * 
 * All transaction types in the African Tokenized Currency System extend this class:
 * - National currency transactions
 * - ASU settlement transactions
 * - Export token transactions
 * - Cross-shard transactions
 * - P2P transfers
 */
public abstract class BaseTransaction extends Transaction {
    
    protected final String transactionId;
    protected final Date timestamp;
    protected final TransactionType type;
    protected TransactionStatus status;
    
    /**
     * Creates a new BaseTransaction
     * 
     * @param data Transaction data bytes
     * @param type Transaction type
     */
    protected BaseTransaction(byte[] data, TransactionType type) {
        super(data);
        this.transactionId = UUID.randomUUID().toString();
        this.timestamp = new Date();
        this.type = type;
        this.status = TransactionStatus.PENDING;
    }
    
    /**
     * Gets the transaction ID
     */
    public String getTransactionId() {
        return transactionId;
    }
    
    /**
     * Gets the timestamp
     */
    public Date getTimestamp() {
        return timestamp;
    }
    
    /**
     * Gets the transaction type
     */
    public TransactionType getType() {
        return type;
    }
    
    /**
     * Gets the transaction status
     */
    public TransactionStatus getStatus() {
        return status;
    }
    
    /**
     * Sets the transaction status
     */
    public void setStatus(TransactionStatus status) {
        this.status = status;
    }
    
    /**
     * Gets the sender address (to be implemented by subclasses)
     */
    public abstract Address getSenderAddress();
    
    /**
     * Gets the recipient address (to be implemented by subclasses)
     */
    public abstract Address getRecipientAddress();
    
    /**
     * Gets the transaction amount (to be implemented by subclasses)
     */
    public abstract Amount getAmount();
    
    /**
     * Validates transaction-specific rules (to be implemented by subclasses)
     */
    protected abstract boolean validateTransactionRules();
    
    /**
     * Checks if transaction can be processed offline
     */
    public boolean supportsOfflineProcessing() {
        return false; // Override in subclasses that support offline
    }
    
    /**
     * Checks if transaction is cross-border
     */
    public boolean isCrossBorder() {
        return false; // Override in subclasses that support cross-border
    }
    
    /**
     * Gets the gas/fee required for this transaction
     */
    public Amount getRequiredFee() {
        // Base fee calculation - override in subclasses for custom logic
        return Amount.forASU(java.math.BigInteger.valueOf(1000));
    }
    
    @Override
    public boolean isValid() {
        // Base validation
        if (!super.isValid()) {
            return false;
        }
        
        // Check transaction ID
        if (transactionId == null || transactionId.isEmpty()) {
            return false;
        }
        
        // Check timestamp (not too old, not in future)
        long currentTime = System.currentTimeMillis();
        long txTime = timestamp.getTime();
        
        // Reject transactions older than 24 hours
        if (currentTime - txTime > 24 * 60 * 60 * 1000) {
            return false;
        }
        
        // Reject transactions from the future (allow 5 min clock skew)
        if (txTime > currentTime + 5 * 60 * 1000) {
            return false;
        }
        
        // Check addresses
        if (getSenderAddress() == null || getRecipientAddress() == null) {
            return false;
        }
        
        // Check amount
        if (getAmount() == null || !getAmount().isPositive()) {
            return false;
        }
        
        // Validate transaction-specific rules
        return validateTransactionRules();
    }
    
    /**
     * Gets a human-readable description of the transaction
     */
    public String getDescription() {
        return String.format("%s: %s from %s to %s",
            type.name(),
            getAmount().toFormattedString(),
            getSenderAddress().toString().substring(0, 8) + "...",
            getRecipientAddress().toString().substring(0, 8) + "..."
        );
    }
    
    @Override
    public String toString() {
        return String.format("BaseTransaction{id='%s', type=%s, status=%s, timestamp=%s}",
            transactionId, type, status, timestamp);
    }
}