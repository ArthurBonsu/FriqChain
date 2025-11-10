package org.example.app.core.blockchain.transaction;

import org.example.app.core.block.Transaction;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * TokenBankTransaction represents transactions specifically for token banking operations
 * in the TINC Blockchain global banking system. These transactions handle token movements
 * between bank vaults and user wallets.
 */
public class TokenBankTransaction extends Transaction {
    
    private String tokenSymbol;
    private BigInteger amount;
    private Address bankAddress;
    private String currencyCode;
    private BigInteger exchangeRate;
    private Date rateTimestamp;
    private String sourceBankId;
    
    // LRU Cache implementation for rate calculations
    private static final int CACHE_SIZE = 100;
    private static final long CACHE_EXPIRY_MS = 60000; // 1 minute
    
    // Cache of recent exchange rates to prevent manipulation
    private static Map<String, CacheEntry> rateCache = new HashMap<>();
    private static class CacheEntry {
        BigInteger rate;
        long timestamp;
        
        CacheEntry(BigInteger rate) {
            this.rate = rate;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;
        }
    }
    
    /**
     * Creates a new TokenBankTransaction
     * 
     * @param from Sender address
     * @param to Recipient address
     * @param value Transaction value
     * @param tokenSymbol Symbol of the token
     * @param amount Amount of tokens
     * @param currencyCode Currency code (e.g., NGN, KES, ZAR)
     * @param exchangeRate Exchange rate at transaction time
     */
    public TokenBankTransaction(Address from, Address to, long value, String tokenSymbol, 
                               BigInteger amount, String currencyCode, BigInteger exchangeRate) {
        super(buildTransactionData(tokenSymbol, amount, currencyCode, exchangeRate));
        this.tokenSymbol = tokenSymbol;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.exchangeRate = exchangeRate;
        this.rateTimestamp = new Date();
    }
    
    /**
     * Builds the transaction data byte array
     */
    private static byte[] buildTransactionData(String tokenSymbol, BigInteger amount, 
                                             String currencyCode, BigInteger exchangeRate) {
        // Serialize fields to byte array
        ByteBuffer buffer = ByteBuffer.allocate(512); // Sufficient size for data
        
        // Write token symbol with length prefix
        byte[] symbolBytes = tokenSymbol.getBytes();
        buffer.putInt(symbolBytes.length);
        buffer.put(symbolBytes);
        
        // Write amount
        byte[] amountBytes = amount.toByteArray();
        buffer.putInt(amountBytes.length);
        buffer.put(amountBytes);
        
        // Write currency code with length prefix
        byte[] currencyBytes = currencyCode.getBytes();
        buffer.putInt(currencyBytes.length);
        buffer.put(currencyBytes);
        
        // Write exchange rate
        byte[] rateBytes = exchangeRate.toByteArray();
        buffer.putInt(rateBytes.length);
        buffer.put(rateBytes);
        
        // Finalize and return
        buffer.flip();
        byte[] result = new byte[buffer.limit()];
        buffer.get(result);
        return result;
    }
    
    /**
     * Gets the token symbol
     */
    public String getTokenSymbol() {
        return tokenSymbol;
    }
    
    /**
     * Gets the token amount
     */
    public BigInteger getAmount() {
        return amount;
    }
    
    /**
     * Gets the currency code
     */
    public String getCurrencyCode() {
        return currencyCode;
    }
    
    /**
     * Gets the exchange rate
     */
    public BigInteger getExchangeRate() {
        return exchangeRate;
    }
    
    /**
     * Gets the rate timestamp
     */
    public Date getRateTimestamp() {
        return rateTimestamp;
    }
    
    /**
     * Gets the source bank ID
     */
    public String getSourceBankId() {
        return sourceBankId;
    }
    
    /**
     * Sets the bank address
     */
    public void setBankAddress(Address bankAddress) {
        this.bankAddress = bankAddress;
    }
    
    /**
     * Gets the bank address
     */
    public Address getBankAddress() {
        return bankAddress;
    }
    
    /**
     * Sets the source bank ID
     */
    public void setSourceBankId(String sourceBankId) {
        this.sourceBankId = sourceBankId;
    }
    
    /**
     * Verifies that the exchange rate is fair by comparing with cached rates
     * Implements anti-manipulation protection
     */
    public boolean verifyFairRate() {
        String cacheKey = tokenSymbol + ":" + currencyCode;
        CacheEntry cachedRate = rateCache.get(cacheKey);
        
        // If no cached rate, this is valid (first rate)
        if (cachedRate == null || cachedRate.isExpired()) {
            // Store this rate for future reference
            rateCache.put(cacheKey, new CacheEntry(exchangeRate));
            return true;
        }
        
        // Allow a 5% variance for normal market fluctuations
        BigInteger lowerBound = cachedRate.rate.multiply(BigInteger.valueOf(95)).divide(BigInteger.valueOf(100));
        BigInteger upperBound = cachedRate.rate.multiply(BigInteger.valueOf(105)).divide(BigInteger.valueOf(100));
        
        boolean isWithinBounds = exchangeRate.compareTo(lowerBound) >= 0 && 
                               exchangeRate.compareTo(upperBound) <= 0;
        
        // Update cache if rate is valid
        if (isWithinBounds) {
            rateCache.put(cacheKey, new CacheEntry(exchangeRate));
        }
        
        return isWithinBounds;
    }
    
    /**
     * Asynchronously fetches the current exchange rate from multiple sources
     * to ensure fair weighting
     */
    public static CompletableFuture<BigInteger> fetchFairExchangeRate(String tokenSymbol, String currencyCode) {
        return CompletableFuture.supplyAsync(() -> {
            // In a real implementation, this would query multiple exchanges
            // and apply fair weighting algorithm
            
            // For now, return a placeholder value
            return BigInteger.valueOf(10000); // Example rate: 1.0000
        });
    }
    
    @Override
    public boolean isValid() {
        if (!super.isValid()) {
            return false;
        }
        
        // Additional validation specific to token bank transactions
        if (tokenSymbol == null || tokenSymbol.isEmpty()) {
            return false;
        }
        
        if (amount == null || amount.compareTo(BigInteger.ZERO) <= 0) {
            return false;
        }
        
        if (currencyCode == null || currencyCode.isEmpty()) {
            return false;
        }
        
        if (exchangeRate == null || exchangeRate.compareTo(BigInteger.ZERO) <= 0) {
            return false;
        }
        
        // Verify the fairness of the exchange rate
        return verifyFairRate();
    }
    
    @Override
    public String toString() {
        return "TokenBankTransaction{" +
                "token='" + tokenSymbol + '\'' +
                ", amount=" + amount +
                ", currency='" + currencyCode + '\'' +
                ", rate=" + exchangeRate +
                ", timestamp=" + rateTimestamp +
                ", bank='" + (bankAddress != null ? bankAddress.toString() : "null") + '\'' +
                ", sourceBank='" + sourceBankId + '\'' +
                '}';
    }
}