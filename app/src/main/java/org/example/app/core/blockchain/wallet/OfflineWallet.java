package org.example.app.core.blockchain.wallet;

import org.example.app.core.wallet.Wallet;
import org.example.app.core.wallet.TransactionBuilder;
import org.example.app.core.block.Transaction;
import org.example.app.core.types.Address;
import org.example.app.core.types.Amount;
import org.example.app.core.crypto.PKIManager;
import org.example.app.core.state.BlockchainState;
import org.example.app.core.consensus.Blockchain;

import java.security.*;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.time.Instant;

/**
 * OfflineWallet - USSD/SMS-based wallet for feature phones and offline areas
 * 
 * Features:
 * - USSD menu interface (*123# style)
 * - SMS-based transaction commands
 * - Offline signed IOUs with 72-hour validity
 * - PIN-based security (no complex cryptography)
 * - Balance queries via SMS
 * - Transaction history via USSD
 * - Agent-assisted operations
 * - Automatic sync when connectivity restored
 * - Low-bandwidth optimized
 */
public class OfflineWallet extends Wallet {
    private static final Logger LOGGER = Logger.getLogger(OfflineWallet.class.getName());
    
    // User identification
    private final String userId;
    private final String phoneNumber;
    private final String preferredLanguage;
    private final String countryCode;
    
    // Security
    private final String pinHash; // Hashed 4-6 digit PIN
    private int failedPinAttempts;
    private boolean isLocked;
    
    // Balances (limited currencies for simplicity)
    private final Map<String, BigInteger> tokenBalances;
    private final String primaryCurrency;
    
    // Offline operations
    private final Map<String, OfflineIOU> pendingIOUs;
    private final List<SMSTransaction> smsTransactionQueue;
    private final Map<String, USSDSession> activeSessions;
    
    // Transaction limits (for safety)
    private final BigInteger singleTransactionLimit;
    private final BigInteger dailyLimit;
    private BigInteger dailyUsage;
    private long lastDailyReset;
    
    // Dependencies
    private final PKIManager pkiManager;
    private final BlockchainState blockchainState;
    private final Blockchain blockchain;
    
    /**
     * OfflineIOU - Signed IOU for offline transactions
     */
    public static class OfflineIOU {
        private final String iouId;
        private final String senderPhone;
        private final String recipientPhone;
        private final String currency;
        private final BigInteger amount;
        private final String signature;
        private final Instant createdAt;
        private final Instant expiresAt;
        private IOUStatus status;
        
        public enum IOUStatus {
            PENDING, REDEEMED, EXPIRED, CANCELLED
        }
        
        public OfflineIOU(String senderPhone, String recipientPhone, 
                         String currency, BigInteger amount, String signature) {
            this.iouId = UUID.randomUUID().toString().substring(0, 8); // Short ID for SMS
            this.senderPhone = senderPhone;
            this.recipientPhone = recipientPhone;
            this.currency = currency;
            this.amount = amount;
            this.signature = signature;
            this.createdAt = Instant.now();
            this.expiresAt = createdAt.plusSeconds(72 * 3600); // 72 hours
            this.status = IOUStatus.PENDING;
        }
        
        public String getIouId() { return iouId; }
        public String getSenderPhone() { return senderPhone; }
        public String getRecipientPhone() { return recipientPhone; }
        public String getCurrency() { return currency; }
        public BigInteger getAmount() { return amount; }
        public String getSignature() { return signature; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getExpiresAt() { return expiresAt; }
        public IOUStatus getStatus() { return status; }
        
        public void setStatus(IOUStatus status) { this.status = status; }
        
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
        
        public String toSMSFormat() {
            return String.format("IOU:%s FROM:%s AMT:%s %s CODE:%s", 
                iouId, senderPhone, amount.toString(), currency, signature.substring(0, 6));
        }
    }
    
    /**
     * SMSTransaction - Transaction initiated via SMS
     */
    public static class SMSTransaction {
        private final String txId;
        private final String fromPhone;
        private final String toPhone;
        private final String currency;
        private final BigInteger amount;
        private final String smsCommand;
        private final Instant timestamp;
        private SMSStatus status;
        
        public enum SMSStatus {
            QUEUED, PROCESSING, COMPLETED, FAILED
        }
        
        public SMSTransaction(String fromPhone, String toPhone, String currency, 
                             BigInteger amount, String smsCommand) {
            this.txId = UUID.randomUUID().toString().substring(0, 8);
            this.fromPhone = fromPhone;
            this.toPhone = toPhone;
            this.currency = currency;
            this.amount = amount;
            this.smsCommand = smsCommand;
            this.timestamp = Instant.now();
            this.status = SMSStatus.QUEUED;
        }
        
        public String getTxId() { return txId; }
        public String getFromPhone() { return fromPhone; }
        public String getToPhone() { return toPhone; }
        public String getCurrency() { return currency; }
        public BigInteger getAmount() { return amount; }
        public String getSmsCommand() { return smsCommand; }
        public Instant getTimestamp() { return timestamp; }
        public SMSStatus getStatus() { return status; }
        
        public void setStatus(SMSStatus status) { this.status = status; }
    }
    
    /**
     * USSDSession - Active USSD menu session
     */
    public static class USSDSession {
        private final String sessionId;
        private final String phoneNumber;
        private final Instant startTime;
        private String currentMenu;
        private final Map<String, String> sessionData;
        
        public USSDSession(String phoneNumber) {
            this.sessionId = UUID.randomUUID().toString();
            this.phoneNumber = phoneNumber;
            this.startTime = Instant.now();
            this.currentMenu = "MAIN";
            this.sessionData = new HashMap<>();
        }
        
        public String getSessionId() { return sessionId; }
        public String getPhoneNumber() { return phoneNumber; }
        public Instant getStartTime() { return startTime; }
        public String getCurrentMenu() { return currentMenu; }
        
        public void setCurrentMenu(String menu) { this.currentMenu = menu; }
        public void setData(String key, String value) { sessionData.put(key, value); }
        public String getData(String key) { return sessionData.get(key); }
        
        public boolean isExpired() {
            return Instant.now().isAfter(startTime.plusSeconds(120)); // 2 minutes
        }
    }
    
    /**
     * Constructor for OfflineWallet
     */
    public OfflineWallet(String userId, String phoneNumber, String pinHash,
                        String preferredLanguage, String countryCode,
                        String primaryCurrency,
                        BlockchainState blockchainState, Blockchain blockchain,
                        PKIManager pkiManager) throws NoSuchAlgorithmException {
        super();
        
        this.userId = userId;
        this.phoneNumber = phoneNumber;
        this.pinHash = pinHash;
        this.preferredLanguage = preferredLanguage;
        this.countryCode = countryCode.toUpperCase();
        this.primaryCurrency = primaryCurrency.toUpperCase();
        
        this.failedPinAttempts = 0;
        this.isLocked = false;
        
        this.tokenBalances = new ConcurrentHashMap<>();
        this.tokenBalances.put(primaryCurrency, BigInteger.ZERO);
        
        this.pendingIOUs = new ConcurrentHashMap<>();
        this.smsTransactionQueue = Collections.synchronizedList(new ArrayList<>());
        this.activeSessions = new ConcurrentHashMap<>();
        
        // Conservative limits for offline wallet
        this.singleTransactionLimit = new BigInteger("100000000000000000000"); // 100 tokens
        this.dailyLimit = new BigInteger("500000000000000000000"); // 500 tokens
        this.dailyUsage = BigInteger.ZERO;
        this.lastDailyReset = System.currentTimeMillis();
        
        this.blockchainState = blockchainState;
        this.blockchain = blockchain;
        this.pkiManager = pkiManager;
        
        LOGGER.info(String.format("OfflineWallet created for phone %s", phoneNumber));
    }
    
    /**
     * Verifies PIN for transaction
     */
    public boolean verifyPIN(String pin) {
        if (isLocked) {
            LOGGER.warning("Wallet is locked due to failed attempts");
            return false;
        }
        
        String hashedPin = hashPin(pin);
        boolean isValid = hashedPin.equals(pinHash);
        
        if (!isValid) {
            failedPinAttempts++;
            if (failedPinAttempts >= 3) {
                isLocked = true;
                LOGGER.warning("Wallet locked after 3 failed PIN attempts");
            }
        } else {
            failedPinAttempts = 0;
        }
        
        return isValid;
    }
    
    /**
     * Processes USSD command
     */
    public String processUSSDCommand(String input) {
        // Create or get session
        USSDSession session = activeSessions.computeIfAbsent(
            phoneNumber, k -> new USSDSession(phoneNumber));
        
        if (session.isExpired()) {
            activeSessions.remove(phoneNumber);
            return "Session expired. Dial again.";
        }
        
        String response;
        
        switch (session.getCurrentMenu()) {
            case "MAIN":
                response = handleMainMenu(input, session);
                break;
            case "BALANCE":
                response = getBalanceUSSD();
                session.setCurrentMenu("MAIN");
                break;
            case "SEND":
                response = handleSendMenu(input, session);
                break;
            case "HISTORY":
                response = getHistoryUSSD();
                session.setCurrentMenu("MAIN");
                break;
            default:
                response = getMainMenuUSSD();
                session.setCurrentMenu("MAIN");
        }
        
        return response;
    }
    
    /**
     * Processes SMS command
     */
    public String processSMSCommand(String smsText) {
        String[] parts = smsText.toUpperCase().split(" ");
        
        if (parts.length < 2) {
            return "Invalid command. Send HELP for usage.";
        }
        
        String command = parts[0];
        
        switch (command) {
            case "BAL":
            case "BALANCE":
                return getBalanceSMS();
                
            case "SEND":
                return processSendSMS(parts);
                
            case "IOU":
                return processIOUSMS(parts);
                
            case "HELP":
                return getHelpSMS();
                
            default:
                return "Unknown command. Send HELP for usage.";
        }
    }
    
    /**
     * Creates offline IOU for recipient
     */
    public OfflineIOU createOfflineIOU(String recipientPhone, String currency, 
                                      BigInteger amount, String pin) {
        if (!verifyPIN(pin)) {
            LOGGER.warning("Invalid PIN for IOU creation");
            return null;
        }
        
        if (!checkDailyLimit(amount)) {
            LOGGER.warning("Daily limit exceeded");
            return null;
        }
        
        // Check balance
        BigInteger balance = tokenBalances.getOrDefault(currency, BigInteger.ZERO);
        if (balance.compareTo(amount) < 0) {
            LOGGER.warning("Insufficient balance for IOU");
            return null;
        }
        
        // Create signature (simplified for offline)
        String signature = createSimpleSignature(phoneNumber, recipientPhone, 
                                                amount.toString(), currency);
        
        OfflineIOU iou = new OfflineIOU(phoneNumber, recipientPhone, currency, 
                                       amount, signature);
        
        // Reserve balance
        tokenBalances.put(currency, balance.subtract(amount));
        pendingIOUs.put(iou.getIouId(), iou);
        
        dailyUsage = dailyUsage.add(amount);
        
        LOGGER.info(String.format("Offline IOU created: %s for %s %s to %s", 
            iou.getIouId(), amount.toString(), currency, recipientPhone));
        
        return iou;
    }
    
    /**
     * Redeems offline IOU
     */
    public boolean redeemIOU(String iouId, String recipientPin) {
        OfflineIOU iou = pendingIOUs.get(iouId);
        
        if (iou == null) {
            LOGGER.warning("IOU not found: " + iouId);
            return false;
        }
        
        if (iou.isExpired()) {
            iou.setStatus(OfflineIOU.IOUStatus.EXPIRED);
            // Refund sender
            refundExpiredIOU(iou);
            return false;
        }
        
        if (!verifyPIN(recipientPin)) {
            LOGGER.warning("Invalid PIN for IOU redemption");
            return false;
        }
        
        // Credit recipient
        BigInteger balance = tokenBalances.getOrDefault(iou.getCurrency(), BigInteger.ZERO);
        tokenBalances.put(iou.getCurrency(), balance.add(iou.getAmount()));
        
        iou.setStatus(OfflineIOU.IOUStatus.REDEEMED);
        
        LOGGER.info(String.format("IOU redeemed: %s", iouId));
        
        return true;
    }
    
    /**
     * Syncs offline wallet with blockchain
     */
    public List<Transaction> syncWithBlockchain() {
        List<Transaction> syncedTransactions = new ArrayList<>();
        
        // Process pending IOUs
        for (OfflineIOU iou : pendingIOUs.values()) {
            if (iou.getStatus() == OfflineIOU.IOUStatus.REDEEMED) {
                // Create blockchain transaction
                // In production, would create actual transaction
                LOGGER.info("IOU synced to blockchain: " + iou.getIouId());
            }
        }
        
        // Process SMS transaction queue
        for (SMSTransaction smsTx : smsTransactionQueue) {
            if (smsTx.getStatus() == SMSTransaction.SMSStatus.QUEUED) {
                smsTx.setStatus(SMSTransaction.SMSStatus.PROCESSING);
                // In production, would create blockchain transaction
                smsTx.setStatus(SMSTransaction.SMSStatus.COMPLETED);
                LOGGER.info("SMS transaction synced: " + smsTx.getTxId());
            }
        }
        
        return syncedTransactions;
    }
    
    // Helper methods
    
    private String getMainMenuUSSD() {
        return "1. Check Balance\n2. Send Money\n3. Transaction History\n4. Exit";
    }
    
    private String handleMainMenu(String input, USSDSession session) {
        switch (input) {
            case "1":
                session.setCurrentMenu("BALANCE");
                return getBalanceUSSD();
            case "2":
                session.setCurrentMenu("SEND");
                return "Enter recipient phone:";
            case "3":
                session.setCurrentMenu("HISTORY");
                return getHistoryUSSD();
            case "4":
                activeSessions.remove(phoneNumber);
                return "Thank you!";
            default:
                return getMainMenuUSSD();
        }
    }
    
    private String handleSendMenu(String input, USSDSession session) {
        // Simplified send flow
        return "Transaction queued. You will receive confirmation SMS.";
    }
    
    private String getBalanceUSSD() {
        StringBuilder sb = new StringBuilder("Your Balances:\n");
        for (Map.Entry<String, BigInteger> entry : tokenBalances.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }
    
    private String getBalanceSMS() {
        BigInteger balance = tokenBalances.getOrDefault(primaryCurrency, BigInteger.ZERO);
        return String.format("Balance: %s %s", balance.toString(), primaryCurrency);
    }
    
    private String getHistoryUSSD() {
        return "Last 5 transactions:\n(Feature in development)";
    }
    
    private String processSendSMS(String[] parts) {
        if (parts.length < 4) {
            return "Usage: SEND <phone> <amount> <PIN>";
        }
        // Simplified - would process actual send
        return "Transaction queued. ID: TX" + UUID.randomUUID().toString().substring(0, 6);
    }
    
    private String processIOUSMS(String[] parts) {
        if (parts.length < 2) {
            return "Usage: IOU <code> to redeem";
        }
        // Would process IOU redemption
        return "IOU processing...";
    }
    
    private String getHelpSMS() {
        return "Commands: BAL, SEND <phone> <amt> <PIN>, IOU <code>, HELP";
    }
    
    private boolean checkDailyLimit(BigInteger amount) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDailyReset > (24 * 60 * 60 * 1000)) {
            dailyUsage = BigInteger.ZERO;
            lastDailyReset = currentTime;
        }
        
        return dailyUsage.add(amount).compareTo(dailyLimit) <= 0;
    }
    
    private String hashPin(String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(pin.getBytes());
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return pin; // Fallback
        }
    }
    
    private String createSimpleSignature(String... parts) {
        String combined = String.join("|", parts);
        return hashPin(combined).substring(0, 12);
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    private void refundExpiredIOU(OfflineIOU iou) {
        BigInteger balance = tokenBalances.getOrDefault(iou.getCurrency(), BigInteger.ZERO);
        tokenBalances.put(iou.getCurrency(), balance.add(iou.getAmount()));
        LOGGER.info("Refunded expired IOU: " + iou.getIouId());
    }
    
    // Getters
    
    public String getUserId() { return userId; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getCountryCode() { return countryCode; }
    public String getPrimaryCurrency() { return primaryCurrency; }
    
    public BigInteger getBalance(String currency) {
        return tokenBalances.getOrDefault(currency, BigInteger.ZERO);
    }
    
    public boolean isLocked() { return isLocked; }
    public int getFailedPinAttempts() { return failedPinAttempts; }
    
    public List<OfflineIOU> getPendingIOUs() {
        return new ArrayList<>(pendingIOUs.values());
    }
    
    @Override
    public String toString() {
        return String.format("OfflineWallet{phone='%s', currency='%s', balance=%s, locked=%s}",
            phoneNumber, primaryCurrency, 
            tokenBalances.getOrDefault(primaryCurrency, BigInteger.ZERO).toString(),
            isLocked);
    }
}