package org.example.app.core.state;

import org.example.app.core.types.Amount;
import org.example.app.core.types.Address;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MultiCurrencyBalance tracks balances across multiple token types for an address.
 * Essential for the African Tokenized Currency System which handles:
 * - National currency tokens (NGN-TOKEN, GHS-TOKEN, etc.)
 * - ASU (African Stable Unit)
 * - Export tokens (EXPORT-GOLD, EXPORT-OIL, etc.)
 * - Cross-shard balances
 */
public class MultiCurrencyBalance {
    
    private final Address ownerAddress;
    private final Map<String, BigInteger> balances; // Currency → Amount
    private final Map<String, Long> lastUpdate; // Currency → Timestamp
    private final Map<String, List<String>> transactionHistory; // Currency → TxIds
    private long nonce; // Transaction nonce for this address
    
    /**
     * Creates a new MultiCurrencyBalance
     * 
     * @param ownerAddress Address of the balance owner
     */
    public MultiCurrencyBalance(Address ownerAddress) {
        this.ownerAddress = ownerAddress;
        this.balances = new ConcurrentHashMap<>();
        this.lastUpdate = new ConcurrentHashMap<>();
        this.transactionHistory = new ConcurrentHashMap<>();
        this.nonce = 0;
    }
    
    /**
     * Gets the owner address
     */
    public Address getOwnerAddress() {
        return ownerAddress;
    }
    
    /**
     * Gets balance for a specific currency
     */
    public BigInteger getBalance(String currency) {
        return balances.getOrDefault(currency.toUpperCase(), BigInteger.ZERO);
    }
    
    /**
     * Gets balance as Amount object
     */
    public Amount getBalanceAsAmount(String currency) {
        BigInteger balance = getBalance(currency);
        
        // Determine decimals based on currency type
        int decimals;
        if (currency.equals("ASU")) {
            decimals = Amount.TOKEN_DECIMALS;
        } else if (currency.startsWith("EXPORT-")) {
            decimals = Amount.COMMODITY_DECIMALS;
        } else if (currency.endsWith("-TOKEN")) {
            decimals = Amount.FIAT_DECIMALS;
        } else {
            decimals = Amount.TOKEN_DECIMALS;
        }
        
        return new Amount(balance, currency, decimals);
    }
    
    /**
     * Gets all currencies with non-zero balances
     */
    public Set<String> getCurrencies() {
        Set<String> nonZero = new HashSet<>();
        for (Map.Entry<String, BigInteger> entry : balances.entrySet()) {
            if (entry.getValue().compareTo(BigInteger.ZERO) > 0) {
                nonZero.add(entry.getKey());
            }
        }
        return nonZero;
    }
    
    /**
     * Gets all balances
     */
    public Map<String, BigInteger> getAllBalances() {
        return new HashMap<>(balances);
    }
    
    /**
     * Adds to balance
     */
    public void credit(String currency, BigInteger amount) {
        if (amount.compareTo(BigInteger.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        
        String currencyKey = currency.toUpperCase();
        BigInteger currentBalance = balances.getOrDefault(currencyKey, BigInteger.ZERO);
        balances.put(currencyKey, currentBalance.add(amount));
        lastUpdate.put(currencyKey, System.currentTimeMillis());
    }
    
    /**
     * Subtracts from balance
     */
    public void debit(String currency, BigInteger amount) {
        if (amount.compareTo(BigInteger.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        
        String currencyKey = currency.toUpperCase();
        BigInteger currentBalance = balances.getOrDefault(currencyKey, BigInteger.ZERO);
        
        if (currentBalance.compareTo(amount) < 0) {
            throw new IllegalStateException(
                String.format("Insufficient balance: have %s, need %s %s",
                    currentBalance, amount, currency));
        }
        
        balances.put(currencyKey, currentBalance.subtract(amount));
        lastUpdate.put(currencyKey, System.currentTimeMillis());
    }
    
    /**
     * Transfers between currencies (exchange)
     */
    public void exchange(String fromCurrency, BigInteger fromAmount,
                        String toCurrency, BigInteger toAmount) {
        debit(fromCurrency, fromAmount);
        credit(toCurrency, toAmount);
    }
    
    /**
     * Checks if balance is sufficient
     */
    public boolean hasSufficientBalance(String currency, BigInteger required) {
        BigInteger current = getBalance(currency);
        return current.compareTo(required) >= 0;
    }
    
    /**
     * Gets the nonce
     */
    public long getNonce() {
        return nonce;
    }
    
    /**
     * Increments the nonce
     */
    public void incrementNonce() {
        nonce++;
    }
    
    /**
     * Adds transaction to history
     */
    public void recordTransaction(String currency, String transactionId) {
        String currencyKey = currency.toUpperCase();
        transactionHistory.computeIfAbsent(currencyKey, k -> new ArrayList<>())
            .add(transactionId);
    }
    
    /**
     * Gets transaction history for a currency
     */
    public List<String> getTransactionHistory(String currency) {
        String currencyKey = currency.toUpperCase();
        return new ArrayList<>(transactionHistory.getOrDefault(currencyKey, new ArrayList<>()));
    }
    
    /**
     * Gets the last update timestamp for a currency
     */
    public long getLastUpdate(String currency) {
        String currencyKey = currency.toUpperCase();
        return lastUpdate.getOrDefault(currencyKey, 0L);
    }
    
    /**
     * Calculates total value in ASU
     */
    public BigInteger getTotalValueInASU(Map<String, BigInteger> exchangeRates) {
        BigInteger total = BigInteger.ZERO;
        
        for (Map.Entry<String, BigInteger> entry : balances.entrySet()) {
            String currency = entry.getKey();
            BigInteger balance = entry.getValue();
            
            if (currency.equals("ASU")) {
                total = total.add(balance);
            } else {
                BigInteger rate = exchangeRates.get(currency);
                if (rate != null) {
                    // Convert to ASU: balance × rate / precision
                    BigInteger asuValue = balance.multiply(rate)
                        .divide(BigInteger.valueOf(1000000)); // Rate precision
                    total = total.add(asuValue);
                }
            }
        }
        
        return total;
    }
    
    /**
     * Generates a balance summary
     */
    public String generateBalanceSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("=== Balance Summary ===\n");
        summary.append("Owner: ").append(ownerAddress).append("\n");
        summary.append("Nonce: ").append(nonce).append("\n");
        summary.append("\nBalances:\n");
        
        // Group by type
        List<String> national = new ArrayList<>();
        List<String> exports = new ArrayList<>();
        List<String> other = new ArrayList<>();
        
        for (String currency : getCurrencies()) {
            if (currency.equals("ASU")) {
                continue; // Show ASU first
            } else if (currency.endsWith("-TOKEN")) {
                national.add(currency);
            } else if (currency.startsWith("EXPORT-")) {
                exports.add(currency);
            } else {
                other.add(currency);
            }
        }
        
        // Show ASU first
        if (balances.containsKey("ASU")) {
            Amount asuBalance = getBalanceAsAmount("ASU");
            summary.append(String.format("  ASU: %s\n", asuBalance.toFormattedString()));
        }
        
        // Show national currencies
        if (!national.isEmpty()) {
            summary.append("\nNational Currencies:\n");
            for (String currency : national) {
                Amount balance = getBalanceAsAmount(currency);
                summary.append(String.format("  %s\n", balance.toFormattedString()));
            }
        }
        
        // Show export tokens
        if (!exports.isEmpty()) {
            summary.append("\nExport Tokens:\n");
            for (String currency : exports) {
                Amount balance = getBalanceAsAmount(currency);
                summary.append(String.format("  %s\n", balance.toFormattedString()));
            }
        }
        
        // Show other
        if (!other.isEmpty()) {
            summary.append("\nOther:\n");
            for (String currency : other) {
                Amount balance = getBalanceAsAmount(currency);
                summary.append(String.format("  %s\n", balance.toFormattedString()));
            }
        }
        
        return summary.toString();
    }
    
    /**
     * Serializes to bytes for blockchain storage
     */
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(65536);
        
        // Write owner address
        buffer.put(ownerAddress.toSlice());
        
        // Write nonce
        buffer.putLong(nonce);
        
        // Write balances
        buffer.putInt(balances.size());
        for (Map.Entry<String, BigInteger> entry : balances.entrySet()) {
            byte[] currencyBytes = entry.getKey().getBytes();
            buffer.putInt(currencyBytes.length);
            buffer.put(currencyBytes);
            
            byte[] amountBytes = entry.getValue().toByteArray();
            buffer.putInt(amountBytes.length);
            buffer.put(amountBytes);
            
            // Write last update
            long update = lastUpdate.getOrDefault(entry.getKey(), 0L);
            buffer.putLong(update);
        }
        
        buffer.flip();
        byte[] result = new byte[buffer.limit()];
        buffer.get(result);
        return result;
    }
    
    /**
     * Creates MultiCurrencyBalance from bytes
     */
    public static MultiCurrencyBalance fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        
        // Read owner address
        byte[] addressBytes = new byte[Address.ADDRESS_LENGTH];
        buffer.get(addressBytes);
        Address owner = new Address(addressBytes);
        
        MultiCurrencyBalance balance = new MultiCurrencyBalance(owner);
        
        // Read nonce
        balance.nonce = buffer.getLong();
        
        // Read balances
        int count = buffer.getInt();
        for (int i = 0; i < count; i++) {
            int currencyLength = buffer.getInt();
            byte[] currencyBytes = new byte[currencyLength];
            buffer.get(currencyBytes);
            String currency = new String(currencyBytes);
            
            int amountLength = buffer.getInt();
            byte[] amountBytes = new byte[amountLength];
            buffer.get(amountBytes);
            BigInteger amount = new BigInteger(amountBytes);
            
            long update = buffer.getLong();
            
            balance.balances.put(currency, amount);
            balance.lastUpdate.put(currency, update);
        }
        
        return balance;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        MultiCurrencyBalance other = (MultiCurrencyBalance) obj;
        return ownerAddress.equals(other.ownerAddress) &&
               balances.equals(other.balances) &&
               nonce == other.nonce;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(ownerAddress, balances, nonce);
    }
    
    @Override
    public String toString() {
        return String.format("MultiCurrencyBalance{owner=%s, currencies=%d, nonce=%d}",
            ownerAddress, getCurrencies().size(), nonce);
    }
}