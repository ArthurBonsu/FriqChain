package org.example.app.core.blockchain.wallet;

import org.example.app.core.wallet.Wallet;
import org.example.app.core.wallet.TransactionBuilder;
import org.example.app.core.block.Transaction;
import org.example.app.core.types.Address;
import org.example.app.core.types.Amount;
import org.example.app.core.types.Hash;
import org.example.app.core.crypto.PKIManager;
import org.example.app.core.state.BlockchainState;
import org.example.app.core.consensus.Blockchain;

import java.security.*;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.time.Instant;

/**
 * CitizenWallet - Primary wallet for ordinary African citizens (1.2 billion users)
 * 
 * Features:
 * - Multi-currency support (national tokens, ASU, export tokens)
 * - P2P transfers without bank intermediaries
 * - Offline transaction capabilities (USSD/SMS)
 * - Low-fee micro-transactions (target ≤$0.001)
 * - Easy-to-use interface for feature phones
 * - Automatic ASU conversion for cross-border payments
 * - Emergency exit protocol support
 * - Protection from government seizure
 */
public class CitizenWallet extends Wallet {
    private static final Logger LOGGER = Logger.getLogger(CitizenWallet.class.getName());
    
    // Citizen wallet properties
    private final String citizenId;
    private final String phoneNumber;  // For USSD/SMS support
    private final String countryCode;
    private final String preferredCurrency;
    
    // Multi-currency balances (currency -> amount)
    private final Map<String, BigInteger> tokenBalances;
    
    // Offline transaction support
    private final Map<String, OfflineTransaction> pendingOfflineTransactions;
    private final List<String> offlineTransactionIOUs; // Signed IOUs
    
    // P2P transaction history
    private final List<P2PTransfer> p2pHistory;
    private final int maxHistorySize = 1000;
    
    // Security features
    private final PKIManager pkiManager;
    private final BlockchainState blockchainState;
    private final Blockchain blockchain;
    
    // Transaction limits for safety
    private final BigInteger dailyTransferLimit;
    private BigInteger dailyTransferUsed;
    private long lastResetTimestamp;
    
    // Emergency exit settings
    private boolean emergencyExitEnabled;
    private String emergencyExitWalletAddress;
    
    /**
     * OfflineTransaction for areas with poor connectivity
     */
    public static class OfflineTransaction {
        private final String transactionId;
        private final String senderPhone;
        private final String recipientPhone;
        private final String currency;
        private final BigInteger amount;
        private final long timestamp;
        private final String signedIOU;
        private boolean synced;
        
        public OfflineTransaction(String senderPhone, String recipientPhone, 
                                String currency, BigInteger amount, String signedIOU) {
            this.transactionId = UUID.randomUUID().toString();
            this.senderPhone = senderPhone;
            this.recipientPhone = recipientPhone;
            this.currency = currency;
            this.amount = amount;
            this.timestamp = System.currentTimeMillis();
            this.signedIOU = signedIOU;
            this.synced = false;
        }
        
        public String getTransactionId() { return transactionId; }
        public String getSenderPhone() { return senderPhone; }
        public String getRecipientPhone() { return recipientPhone; }
        public String getCurrency() { return currency; }
        public BigInteger getAmount() { return amount; }
        public long getTimestamp() { return timestamp; }
        public String getSignedIOU() { return signedIOU; }
        public boolean isSynced() { return synced; }
        public void setSynced(boolean synced) { this.synced = synced; }
        
        public boolean isExpired() {
            // IOUs expire after 72 hours
            return (System.currentTimeMillis() - timestamp) > (72 * 60 * 60 * 1000);
        }
    }
    
    /**
     * P2PTransfer record
     */
    public static class P2PTransfer {
        private final Instant timestamp;
        private final String fromAddress;
        private final String toAddress;
        private final String currency;
        private final BigInteger amount;
        private final String txHash;
        private final TransferType type;
        
        public enum TransferType {
            SENT, RECEIVED, SELF_TRANSFER
        }
        
        public P2PTransfer(String fromAddress, String toAddress, String currency, 
                          BigInteger amount, String txHash, TransferType type) {
            this.timestamp = Instant.now();
            this.fromAddress = fromAddress;
            this.toAddress = toAddress;
            this.currency = currency;
            this.amount = amount;
            this.txHash = txHash;
            this.type = type;
        }
        
        public Instant getTimestamp() { return timestamp; }
        public String getFromAddress() { return fromAddress; }
        public String getToAddress() { return toAddress; }
        public String getCurrency() { return currency; }
        public BigInteger getAmount() { return amount; }
        public String getTxHash() { return txHash; }
        public TransferType getType() { return type; }
    }
    
    /**
     * Constructor for CitizenWallet
     */
    public CitizenWallet(String citizenId, String phoneNumber, String countryCode, 
                        String preferredCurrency, BlockchainState blockchainState,
                        Blockchain blockchain, PKIManager pkiManager) throws NoSuchAlgorithmException {
        super();
        
        this.citizenId = citizenId;
        this.phoneNumber = phoneNumber;
        this.countryCode = countryCode;
        this.preferredCurrency = preferredCurrency.toUpperCase();
        this.blockchainState = blockchainState;
        this.blockchain = blockchain;
        this.pkiManager = pkiManager;
        
        this.tokenBalances = new ConcurrentHashMap<>();
        this.pendingOfflineTransactions = new ConcurrentHashMap<>();
        this.offlineTransactionIOUs = Collections.synchronizedList(new ArrayList<>());
        this.p2pHistory = Collections.synchronizedList(new ArrayList<>());
        
        // Set reasonable daily limit (e.g., $1000 equivalent)
        this.dailyTransferLimit = new BigInteger("1000000000000000000000"); // 1000 tokens with 18 decimals
        this.dailyTransferUsed = BigInteger.ZERO;
        this.lastResetTimestamp = System.currentTimeMillis();
        
        this.emergencyExitEnabled = false;
        this.emergencyExitWalletAddress = null;
        
        // Initialize with preferred currency balance at zero
        this.tokenBalances.put(preferredCurrency, BigInteger.ZERO);
        
        LOGGER.info(String.format("CitizenWallet created for %s (phone: %s, country: %s)", 
            citizenId, phoneNumber, countryCode));
    }
    
    /**
     * Sends P2P transfer (direct citizen-to-citizen, no bank involved)
     */
    public Transaction sendP2PTransfer(String recipientAddress, String currency, BigInteger amount, 
                                      String memo) {
        try {
            // Check daily limit
            if (!checkDailyLimit(amount)) {
                LOGGER.warning("Daily transfer limit exceeded for " + citizenId);
                return null;
            }
            
            // Check balance
            BigInteger balance = tokenBalances.getOrDefault(currency, BigInteger.ZERO);
            if (balance.compareTo(amount) < 0) {
                LOGGER.warning("Insufficient balance for P2P transfer");
                return null;
            }
            
            // Create transaction
            byte[] memoData = memo != null ? memo.getBytes() : new byte[0];
            
            TransactionBuilder builder = new TransactionBuilder()
                .from(getAddress())
                .to(recipientAddress)
                .value(amount)
                .data(memoData)
                .gasPrice(calculateLowFeeGasPrice()) // Low fee for citizens
                .nonce(getNonce());
            
            Transaction tx = builder.build();
            
            // Update local balance
            tokenBalances.put(currency, balance.subtract(amount));
            
            // Update daily limit
            dailyTransferUsed = dailyTransferUsed.add(amount);
            
            // Record in P2P history
            P2PTransfer transfer = new P2PTransfer(
                getAddress(), recipientAddress, currency, amount, 
                tx.getHash().toString(), P2PTransfer.TransferType.SENT
            );
            addToHistory(transfer);
            
            LOGGER.info(String.format("P2P transfer sent: %s %s to %s", 
                amount.toString(), currency, recipientAddress));
            
            return tx;
            
        } catch (Exception e) {
            LOGGER.severe("Error creating P2P transfer: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Receives P2P transfer
     */
    public boolean receiveP2PTransfer(String senderAddress, String currency, 
                                     BigInteger amount, String txHash) {
        try {
            // Add to balance
            BigInteger currentBalance = tokenBalances.getOrDefault(currency, BigInteger.ZERO);
            tokenBalances.put(currency, currentBalance.add(amount));
            
            // Record in P2P history
            P2PTransfer transfer = new P2PTransfer(
                senderAddress, getAddress(), currency, amount, 
                txHash, P2PTransfer.TransferType.RECEIVED
            );
            addToHistory(transfer);
            
            LOGGER.info(String.format("P2P transfer received: %s %s from %s", 
                amount.toString(), currency, senderAddress));
            
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Error receiving P2P transfer: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Creates an offline transaction (USSD/SMS)
     * For areas with poor internet connectivity
     */
    public OfflineTransaction createOfflineTransaction(String recipientPhone, String currency, 
                                                      BigInteger amount) {
        try {
            // Check balance
            BigInteger balance = tokenBalances.getOrDefault(currency, BigInteger.ZERO);
            if (balance.compareTo(amount) < 0) {
                LOGGER.warning("Insufficient balance for offline transaction");
                return null;
            }
            
            // Create signed IOU
            String iouData = phoneNumber + "|" + recipientPhone + "|" + currency + "|" + 
                           amount.toString() + "|" + System.currentTimeMillis();
            String signedIOU = signData(iouData);
            
            // Create offline transaction
            OfflineTransaction offlineTx = new OfflineTransaction(
                phoneNumber, recipientPhone, currency, amount, signedIOU
            );
            
            // Store pending
            pendingOfflineTransactions.put(offlineTx.getTransactionId(), offlineTx);
            offlineTransactionIOUs.add(signedIOU);
            
            // Reserve balance (subtract but don't finalize until sync)
            tokenBalances.put(currency, balance.subtract(amount));
            
            LOGGER.info(String.format("Offline transaction created: %s %s to phone %s", 
                amount.toString(), currency, recipientPhone));
            
            return offlineTx;
            
        } catch (Exception e) {
            LOGGER.severe("Error creating offline transaction: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Syncs offline transactions when connectivity is restored
     */
    public List<Transaction> syncOfflineTransactions() {
        List<Transaction> syncedTransactions = new ArrayList<>();
        
        for (OfflineTransaction offlineTx : pendingOfflineTransactions.values()) {
            if (offlineTx.isSynced()) continue;
            
            // Verify IOU is not expired
            if (offlineTx.isExpired()) {
                LOGGER.warning("Offline transaction expired: " + offlineTx.getTransactionId());
                // Refund amount
                BigInteger balance = tokenBalances.getOrDefault(offlineTx.getCurrency(), BigInteger.ZERO);
                tokenBalances.put(offlineTx.getCurrency(), balance.add(offlineTx.getAmount()));
                continue;
            }
            
            // Create on-chain transaction
            // In production, would look up recipient address by phone
            String recipientAddress = lookupAddressByPhone(offlineTx.getRecipientPhone());
            
            if (recipientAddress != null) {
                Transaction tx = sendP2PTransfer(
                    recipientAddress, 
                    offlineTx.getCurrency(), 
                    offlineTx.getAmount(),
                    "Offline transaction sync: " + offlineTx.getTransactionId()
                );
                
                if (tx != null) {
                    offlineTx.setSynced(true);
                    syncedTransactions.add(tx);
                    
                    LOGGER.info("Offline transaction synced: " + offlineTx.getTransactionId());
                }
            }
        }
        
        // Clean up synced transactions
        pendingOfflineTransactions.entrySet().removeIf(entry -> entry.getValue().isSynced());
        
        return syncedTransactions;
    }
    
    /**
     * Converts between currencies using ASU as intermediary
     */
    public boolean convertCurrency(String fromCurrency, String toCurrency, BigInteger amount) {
        try {
            // Check balance
            BigInteger balance = tokenBalances.getOrDefault(fromCurrency, BigInteger.ZERO);
            if (balance.compareTo(amount) < 0) {
                LOGGER.warning("Insufficient balance for currency conversion");
                return false;
            }
            
            // Convert via ASU (in production, would use actual exchange rates)
            BigDecimal exchangeRate = getExchangeRate(fromCurrency, toCurrency);
            BigInteger convertedAmount = new BigDecimal(amount)
                .multiply(exchangeRate)
                .toBigInteger();
            
            // Update balances
            tokenBalances.put(fromCurrency, balance.subtract(amount));
            
            BigInteger toBalance = tokenBalances.getOrDefault(toCurrency, BigInteger.ZERO);
            tokenBalances.put(toCurrency, toBalance.add(convertedAmount));
            
            LOGGER.info(String.format("Currency converted: %s %s -> %s %s", 
                amount.toString(), fromCurrency, convertedAmount.toString(), toCurrency));
            
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Error converting currency: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Enables emergency exit protocol
     */
    public boolean enableEmergencyExit(String emergencyWalletAddress) {
        this.emergencyExitEnabled = true;
        this.emergencyExitWalletAddress = emergencyWalletAddress;
        
        LOGGER.info("Emergency exit enabled for " + citizenId);
        return true;
    }
    
    /**
     * Triggers emergency exit (transfers all funds to emergency wallet)
     */
    public List<Transaction> triggerEmergencyExit() {
        if (!emergencyExitEnabled || emergencyExitWalletAddress == null) {
            LOGGER.warning("Emergency exit not enabled");
            return Collections.emptyList();
        }
        
        List<Transaction> exitTransactions = new ArrayList<>();
        
        // Transfer all balances to emergency wallet
        for (Map.Entry<String, BigInteger> entry : tokenBalances.entrySet()) {
            String currency = entry.getKey();
            BigInteger balance = entry.getValue();
            
            if (balance.compareTo(BigInteger.ZERO) > 0) {
                Transaction tx = sendP2PTransfer(
                    emergencyExitWalletAddress,
                    currency,
                    balance,
                    "EMERGENCY_EXIT"
                );
                
                if (tx != null) {
                    exitTransactions.add(tx);
                }
            }
        }
        
        LOGGER.warning("EMERGENCY EXIT TRIGGERED for " + citizenId + 
                      " - " + exitTransactions.size() + " transfers executed");
        
        return exitTransactions;
    }
    
    // Helper methods
    
    private boolean checkDailyLimit(BigInteger amount) {
        // Reset daily limit if 24 hours have passed
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastResetTimestamp > (24 * 60 * 60 * 1000)) {
            dailyTransferUsed = BigInteger.ZERO;
            lastResetTimestamp = currentTime;
        }
        
        BigInteger potentialUsage = dailyTransferUsed.add(amount);
        return potentialUsage.compareTo(dailyTransferLimit) <= 0;
    }
    
    private BigInteger calculateLowFeeGasPrice() {
        // Target ≤$0.001 per transaction for citizens
        return BigInteger.valueOf(1000000000L); // 1 Gwei
    }
    
    private long getNonce() {
        return System.currentTimeMillis();
    }
    
    private String signData(String data) {
        try {
            // In production, use proper signature
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes());
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return data;
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
    
    private String lookupAddressByPhone(String phone) {
        // In production, would query phone-to-address registry
        return "0x" + phone.hashCode();
    }
    
    private BigDecimal getExchangeRate(String fromCurrency, String toCurrency) {
        // In production, would query ExchangeRateOracle
        return BigDecimal.ONE;
    }
    
    private void addToHistory(P2PTransfer transfer) {
        p2pHistory.add(transfer);
        
        // Keep history size manageable
        if (p2pHistory.size() > maxHistorySize) {
            p2pHistory.remove(0);
        }
    }
    
    // Getters and utility methods
    
    public String getCitizenId() { return citizenId; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getCountryCode() { return countryCode; }
    public String getPreferredCurrency() { return preferredCurrency; }
    
    public BigInteger getBalance(String currency) {
        return tokenBalances.getOrDefault(currency, BigInteger.ZERO);
    }
    
    public Map<String, BigInteger> getAllBalances() {
        return new HashMap<>(tokenBalances);
    }
    
    public List<P2PTransfer> getRecentTransfers(int limit) {
        int size = p2pHistory.size();
        int fromIndex = Math.max(0, size - limit);
        return new ArrayList<>(p2pHistory.subList(fromIndex, size));
    }
    
    public List<OfflineTransaction> getPendingOfflineTransactions() {
        return new ArrayList<>(pendingOfflineTransactions.values());
    }
    
    public int getOfflineTransactionCount() {
        return pendingOfflineTransactions.size();
    }
    
    public boolean isEmergencyExitEnabled() {
        return emergencyExitEnabled;
    }
    
    public BigInteger getDailyLimitRemaining() {
        checkDailyLimit(BigInteger.ZERO); // Reset if needed
        return dailyTransferLimit.subtract(dailyTransferUsed);
    }
    
    /**
     * Deposits tokens (from bank or exchange)
     */
    public boolean deposit(String currency, BigInteger amount) {
        if (amount.compareTo(BigInteger.ZERO) <= 0) {
            return false;
        }
        
        BigInteger currentBalance = tokenBalances.getOrDefault(currency, BigInteger.ZERO);
        tokenBalances.put(currency, currentBalance.add(amount));
        
        LOGGER.info(String.format("Deposited %s %s to citizen wallet %s", 
            amount.toString(), currency, citizenId));
        
        return true;
    }
    
    @Override
    public String toString() {
        return String.format("CitizenWallet{id='%s', phone='%s', country='%s', currencies=%d, balance=%s %s}",
            citizenId, phoneNumber, countryCode, tokenBalances.size(),
            tokenBalances.getOrDefault(preferredCurrency, BigInteger.ZERO).toString(), 
            preferredCurrency);
    }
}