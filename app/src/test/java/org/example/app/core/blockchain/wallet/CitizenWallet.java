package org.example.app.core.blockchain.wallet;

import org.example.app.core.wallet.Wallet;
import org.example.app.core.wallet.TransactionBuilder;
import org.example.app.core.block.Transaction;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;
import org.example.app.core.account.Account;
import org.example.app.core.consensus.HashUtils;
import org.example.app.core.mempool.TransactionValidator;

import java.security.*;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * CitizenWallet - Simple, user-friendly wallet for African citizens
 * 
 * Features:
 * - Simplified interface for non-technical users
 * - Government resistance through pseudonymous design
 * - Offline transaction capabilities for areas with poor connectivity
 * - Multi-currency support with fair exchange rates
 * - Emergency backup and recovery features
 * - Social payment features for family and community
 * - Financial privacy and protection from surveillance
 * - Integration with local merchants and services
 */
public class CitizenWallet extends Wallet {
    private static final Logger LOGGER = Logger.getLogger(CitizenWallet.class.getName());
    
    // Wallet identification (pseudonymous)
    private final String citizenId; // Pseudonymous identifier
    private final String countryCode;
    private final String localCurrency;
    private final String displayName; // User-chosen display name
    
    // Currency and balance management
    private final Map<String, CurrencyBalance> currencyBalances;
    private final Map<String, ExchangeRate> exchangeRates;
    private String preferredDisplayCurrency;
    private final CurrencyPreferences currencyPrefs;
    
    // Social and family features
    private final Map<String, Contact> contacts;
    private final List<RecurringPayment> recurringPayments;
    private final Map<String, FamilyGroup> familyGroups;
    private final SocialPaymentManager socialPayments;
    
    // Offline and connectivity features
    private final OfflineTransactionManager offlineManager;
    private final List<QRCodeTransaction> qrTransactions;
    private final SMSTransactionManager smsTransactions;
    private boolean isOfflineMode;
    
    // Government resistance features
    private final PrivacyEngine privacyEngine;
    private final List<byte[]> recoverySeeds;
    private final Map<String, EmergencyContact> emergencyContacts;
    private final AnonymityManager anonymityManager;
    
    // Financial management
    private final BudgetManager budgetManager;
    private final SavingsGoals savingsGoals;
    private final TransactionHistory transactionHistory;
    private final SpendingAnalytics analytics;
    
    // Security and backup
    private final ReentrantLock walletLock;
    private final TransactionValidator validator;
    private final List<SecurityAlert> securityAlerts;
    private final BackupManager backupManager;
    private Instant lastBackup;
    
    /**
     * CurrencyBalance represents balance in a specific currency
     */
    public static class CurrencyBalance {
        private final String currencyCode;
        private final String currencyName;
        private final String symbol;
        private volatile long availableBalance;
        private volatile long pendingBalance;
        private final boolean isLocalCurrency;
        private Instant lastUpdated;
        
        public CurrencyBalance(String currencyCode, String currencyName, String symbol, 
                             long availableBalance, boolean isLocalCurrency) {
            this.currencyCode = currencyCode;
            this.currencyName = currencyName;
            this.symbol = symbol;
            this.availableBalance = availableBalance;
            this.pendingBalance = 0;
            this.isLocalCurrency = isLocalCurrency;
            this.lastUpdated = Instant.now();
        }
        
        public String getCurrencyCode() { return currencyCode; }
        public String getCurrencyName() { return currencyName; }
        public String getSymbol() { return symbol; }
        public long getAvailableBalance() { return availableBalance; }
        public long getPendingBalance() { return pendingBalance; }
        public long getTotalBalance() { return availableBalance + pendingBalance; }
        public boolean isLocalCurrency() { return isLocalCurrency; }
        public Instant getLastUpdated() { return lastUpdated; }
        
        public synchronized void updateBalance(long newAvailable, long newPending) {
            this.availableBalance = Math.max(0, newAvailable);
            this.pendingBalance = Math.max(0, newPending);
            this.lastUpdated = Instant.now();
        }
        
        public String getFormattedBalance() {
            double balance = availableBalance / 100.0; // Assuming 2 decimal places
            return String.format("%s %.2f", symbol, balance);
        }
    }
    
    /**
     * Contact represents a trusted contact for easy payments
     */
    public static class Contact {
        private final String contactId;
        private final String displayName;
        private final String walletAddress;
        private final String phoneNumber;
        private final String countryCode;
        private final ContactType type;
        private boolean isTrusted;
        private long totalTransactionAmount;
        private int transactionCount;
        private Instant lastTransactionTime;
        
        public enum ContactType {
            FAMILY, FRIEND, MERCHANT, SERVICE_PROVIDER, EMERGENCY_CONTACT
        }
        
        public Contact(String contactId, String displayName, String walletAddress, 
                      String phoneNumber, String countryCode, ContactType type) {
            this.contactId = contactId;
            this.displayName = displayName;
            this.walletAddress = walletAddress;
            this.phoneNumber = phoneNumber;
            this.countryCode = countryCode;
            this.type = type;
            this.isTrusted = false;
            this.totalTransactionAmount = 0;
            this.transactionCount = 0;
        }
        
        // Getters
        public String getContactId() { return contactId; }
        public String getDisplayName() { return displayName; }
        public String getWalletAddress() { return walletAddress; }
        public String getPhoneNumber() { return phoneNumber; }
        public String getCountryCode() { return countryCode; }
        public ContactType getType() { return type; }
        public boolean isTrusted() { return isTrusted; }
        public long getTotalTransactionAmount() { return totalTransactionAmount; }
        public int getTransactionCount() { return transactionCount; }
        
        public void recordTransaction(long amount) {
            this.totalTransactionAmount += amount;
            this.transactionCount++;
            this.lastTransactionTime = Instant.now();
            
            // Auto-trust after multiple successful transactions
            if (transactionCount >= 5 && totalTransactionAmount >= 10000) {
                this.isTrusted = true;
            }
        }
        
        public void setTrusted(boolean trusted) {
            this.isTrusted = trusted;
        }
    }
    
    /**
     * RecurringPayment for regular payments like rent, utilities, etc.
     */
    public static class RecurringPayment {
        private final String paymentId;
        private final String recipientContactId;
        private final String description;
        private final long amount;
        private final String currency;
        private final RecurringSchedule schedule;
        private final Instant startDate;
        private Instant nextPaymentDate;
        private boolean isActive;
        private int executedCount;
        
        public enum RecurringSchedule {
            DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY
        }
        
        public RecurringPayment(String recipientContactId, String description, long amount, 
                              String currency, RecurringSchedule schedule) {
            this.paymentId = generatePaymentId();
            this.recipientContactId = recipientContactId;
            this.description = description;
            this.amount = amount;
            this.currency = currency;
            this.schedule = schedule;
            this.startDate = Instant.now();
            this.nextPaymentDate = calculateNextPaymentDate();
            this.isActive = true;
            this.executedCount = 0;
        }
        
        private String generatePaymentId() {
            return "RP_" + System.currentTimeMillis() + "_" + 
                   Integer.toHexString(new Random().nextInt());
        }
        
        private Instant calculateNextPaymentDate() {
            long currentTime = System.currentTimeMillis();
            long interval = switch (schedule) {
                case DAILY -> 24 * 60 * 60 * 1000L;
                case WEEKLY -> 7 * 24 * 60 * 60 * 1000L;
                case MONTHLY -> 30L * 24 * 60 * 60 * 1000L;
                case QUARTERLY -> 90L * 24 * 60 * 60 * 1000L;
                case YEARLY -> 365L * 24 * 60 * 60 * 1000L;
            };
            return Instant.ofEpochMilli(currentTime + interval);
        }
        
        public boolean isDue() {
            return isActive && Instant.now().isAfter(nextPaymentDate);
        }
        
        public void markExecuted() {
            this.executedCount++;
            this.nextPaymentDate = calculateNextPaymentDate();
        }
        
        // Getters
        public String getPaymentId() { return paymentId; }
        public String getRecipientContactId() { return recipientContactId; }
        public String getDescription() { return description; }
        public long getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public RecurringSchedule getSchedule() { return schedule; }
        public Instant getNextPaymentDate() { return nextPaymentDate; }
        public boolean isActive() { return isActive; }
        public int getExecutedCount() { return executedCount; }
        
        public void setActive(boolean active) { this.isActive = active; }
    }
    
    /**
     * QRCodeTransaction for offline payments
     */
    public static class QRCodeTransaction {
        private final String qrId;
        private final String qrCode;
        private final String recipientAddress;
        private final long amount;
        private final String currency;
        private final String description;
        private final Instant createdAt;
        private final long validUntil;
        private boolean isUsed;
        private boolean isSynced;
        
        public QRCodeTransaction(String recipientAddress, long amount, String currency, 
                               String description, long validityHours) {
            this.qrId = generateQRId();
            this.qrCode = generateQRCode(recipientAddress, amount, currency, description);
            this.recipientAddress = recipientAddress;
            this.amount = amount;
            this.currency = currency;
            this.description = description;
            this.createdAt = Instant.now();
            this.validUntil = System.currentTimeMillis() + (validityHours * 3600000);
            this.isUsed = false;
            this.isSynced = false;
        }
        
        private String generateQRId() {
            return "QR_" + System.currentTimeMillis() + "_" + 
                   Integer.toHexString(new Random().nextInt());
        }
        
        private String generateQRCode(String recipient, long amount, String currency, String description) {
            return "TINC_PAY:" + recipient + ":" + amount + ":" + currency + ":" + 
                   description + ":" + System.currentTimeMillis();
        }
        
        public boolean isValid() {
            return !isUsed && !isSynced && System.currentTimeMillis() <= validUntil;
        }
        
        public void markUsed() { this.isUsed = true; }
        public void markSynced() { this.isSynced = true; }
        
        // Getters
        public String getQrId() { return qrId; }
        public String getQrCode() { return qrCode; }
        public String getRecipientAddress() { return recipientAddress; }
        public long getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public String getDescription() { return description; }
        public Instant getCreatedAt() { return createdAt; }
        public boolean isUsed() { return isUsed; }
        public boolean isSynced() { return isSynced; }
    }
    
    /**
     * Constructor for CitizenWallet
     */
    public CitizenWallet(String citizenId, String countryCode, String localCurrency, String displayName) 
            throws NoSuchAlgorithmException {
        super();
        
        this.citizenId = citizenId;
        this.countryCode = countryCode;
        this.localCurrency = localCurrency;
        this.displayName = displayName;
        this.preferredDisplayCurrency = localCurrency;
        
        this.currencyBalances = new ConcurrentHashMap<>();
        this.exchangeRates = new ConcurrentHashMap<>();
        this.contacts = new ConcurrentHashMap<>();
        this.recurringPayments = new ArrayList<>();
        this.familyGroups = new ConcurrentHashMap<>();
        this.qrTransactions = new ArrayList<>();
        this.emergencyContacts = new ConcurrentHashMap<>();
        this.recoverySeeds = new ArrayList<>();
        this.securityAlerts = new ArrayList<>();
        this.walletLock = new ReentrantLock();
        this.isOfflineMode = false;
        this.lastBackup = Instant.now();
        
        // Initialize components
        this.currencyPrefs = new CurrencyPreferences(localCurrency);
        this.socialPayments = new SocialPaymentManager();
        this.offlineManager = new OfflineTransactionManager();
        this.smsTransactions = new SMSTransactionManager();
        this.privacyEngine = new PrivacyEngine(citizenId);
        this.anonymityManager = new AnonymityManager();
        this.budgetManager = new BudgetManager(localCurrency);
        this.savingsGoals = new SavingsGoals();
        this.transactionHistory = new TransactionHistory();
        this.analytics = new SpendingAnalytics();
        this.backupManager = new BackupManager(citizenId);
        this.validator = new TransactionValidator(new CitizenWalletMempoolConfig());
        
        // Initialize local currency balance
        addCurrencyBalance(localCurrency, getCurrencyName(localCurrency), 
                         getCurrencySymbol(localCurrency), 0, true);
        
        // Generate recovery seeds for government resistance
        generateRecoverySeeds();
        
        LOGGER.info("Created CitizenWallet for " + displayName + " (" + citizenId + 
                   ") in " + countryCode + " with local currency " + localCurrency);
        
        addSecurityAlert("WALLET_CREATED", "Citizen wallet created", "INFO");
    }
    
    /**
     * Sends money to a contact with simplified interface
     */
    public Transaction sendMoney(String contactId, long amount, String currency, String message) {
        walletLock.lock();
        try {
            // Get contact
            Contact contact = contacts.get(contactId);
            if (contact == null) {
                LOGGER.warning("Contact not found: " + contactId);
                return null;
            }
            
            // Check balance
            CurrencyBalance balance = currencyBalances.get(currency);
            if (balance == null || balance.getAvailableBalance() < amount) {
                LOGGER.warning("Insufficient balance for " + currency + ": " + amount);
                addSecurityAlert("INSUFFICIENT_FUNDS", 
                    "Attempted to send " + amount + " " + currency + " but insufficient funds", "WARNING");
                return null;
            }
            
            // Check if this is a large amount (anti-fraud)
            if (amount > balance.getAvailableBalance() * 0.5 && !contact.isTrusted()) {
                LOGGER.warning("Large transaction to untrusted contact blocked: " + amount);
                addSecurityAlert("LARGE_TRANSACTION_BLOCKED", 
                    "Large transaction to untrusted contact blocked", "WARNING");
                return null;
            }
            
            // Create transaction
            byte[] paymentData = createPaymentData(contactId, amount, currency, message);
            
            TransactionBuilder builder = new TransactionBuilder()
                .from(getAddress())
                .to(contact.getWalletAddress())
                .value(BigInteger.valueOf(amount))
                .data(paymentData)
                .gasPrice(BigInteger.valueOf(calculateCitizenGasPrice(currency)))
                .nonce(getNonce());
            
            Transaction tx = builder.build();
            
            // Update balance optimistically
            balance.updateBalance(balance.getAvailableBalance() - amount, 
                                balance.getPendingBalance() + amount);
            
            // Record transaction in contact
            contact.recordTransaction(amount);
            
            // Record in transaction history
            transactionHistory.addTransaction(tx.getHash(), "SENT", contact.getDisplayName(), 
                                            amount, currency, message);
            
            // Update spending analytics
            analytics.recordSpending(amount, currency, contact.getType().toString());
            
            LOGGER.info("Sent " + amount + " " + currency + " to " + contact.getDisplayName());
            
            return tx;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error sending money", e);
            addSecurityAlert("TRANSACTION_ERROR", "Error sending money: " + e.getMessage(), "ERROR");
            return null;
        } finally {
            walletLock.unlock();
        }
    }
    
    /**
     * Requests money from a contact using QR code or SMS
     */
    public QRCodeTransaction requestMoney(String contactId, long amount, String currency, 
                                        String message, boolean useSMS) {
        walletLock.lock();
        try {
            Contact contact = contacts.get(contactId);
            if (contact == null) {
                LOGGER.warning("Contact not found for money request: " + contactId);
                return null;
            }
            
            // Create QR code transaction for the request
            QRCodeTransaction qrTx = new QRCodeTransaction(getAddress(), amount, currency, 
                                                         "Request: " + message, 24); // Valid for 24 hours
            
            qrTransactions.add(qrTx);
            
            // If SMS is requested and contact has phone number
            if (useSMS && contact.getPhoneNumber() != null) {
                String smsMessage = String.format(
                    "%s is requesting %s %s. Pay via: %s",
                    displayName, 
                    formatAmount(amount, currency),
                    currency,
                    qrTx.getQrCode()
                );
                smsTransactions.sendPaymentRequest(contact.getPhoneNumber(), smsMessage);
            }
            
            transactionHistory.addTransaction(qrTx.getQrId(), "REQUEST_SENT", 
                                            contact.getDisplayName(), amount, currency, message);
            
            LOGGER.info("Created payment request: " + amount + " " + currency + 
                       " from " + contact.getDisplayName());
            
            return qrTx;
            
        } finally {
            walletLock.unlock();
        }
    }
    
    /**
     * Exchanges currency using fair rates that prioritize African currencies
     */
    public Transaction exchangeCurrency(String fromCurrency, String toCurrency, long amount) {
        walletLock.lock();
        try {
            // Check balance
            CurrencyBalance fromBalance = currencyBalances.get(fromCurrency);
            if (fromBalance == null || fromBalance.getAvailableBalance() < amount) {
                LOGGER.warning("Insufficient balance for exchange: " + fromCurrency);
                return null;
            }
            
            // Get exchange rate with African currency priority
            ExchangeRate rate = getExchangeRate(fromCurrency, toCurrency);
            if (rate == null) {
                LOGGER.warning("Exchange rate not available: " + fromCurrency + "/" + toCurrency);
                return null;
            }
            
            // Apply fair rate calculation (favors African currencies)
            double exchangeRate = rate.getFairRate();
            long toAmount = (long) (amount * exchangeRate);
            
            // Create exchange transaction
            byte[] exchangeData = createExchangeData(fromCurrency, toCurrency, amount, toAmount, exchangeRate);
            
            TransactionBuilder builder = new TransactionBuilder()
                .from(getAddress())
                .to(getAddress()) // Self transaction for currency exchange
                .value(BigInteger.ZERO)
                .data(exchangeData)
                .gasPrice(BigInteger.valueOf(calculateExchangeGasPrice(fromCurrency, toCurrency)))
                .nonce(getNonce());
            
            Transaction tx = builder.build();
            
            // Update balances
            fromBalance.updateBalance(fromBalance.getAvailableBalance() - amount, 
                                    fromBalance.getPendingBalance());
            
            CurrencyBalance toBalance = currencyBalances.get(toCurrency);
            if (toBalance == null) {
                addCurrencyBalance(toCurrency, getCurrencyName(toCurrency), 
                                 getCurrencySymbol(toCurrency), toAmount, false);
            } else {
                toBalance.updateBalance(toBalance.getAvailableBalance() + toAmount, 
                                      toBalance.getPendingBalance());
            }
            
            // Record exchange in history
            transactionHistory.addTransaction(tx.getHash(), "EXCHANGE", "Currency Exchange", 
                                            amount, fromCurrency, 
                                            "Exchanged to " + toAmount + " " + toCurrency);
            
            LOGGER.info("Exchanged " + amount + " " + fromCurrency + " to " + 
                       toAmount + " " + toCurrency + " at rate " + exchangeRate);
            
            return tx;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error exchanging currency", e);
            return null;
        } finally {
            walletLock.unlock();
        }
    }
    
    /**
     * Creates an offline transaction for areas with poor connectivity
     */
    public QRCodeTransaction createOfflineTransaction(String recipientAddress, long amount, 
                                                    String currency, String description) {
        walletLock.lock();
        try {
            // Check balance
            CurrencyBalance balance = currencyBalances.get(currency);
            if (balance == null || balance.getAvailableBalance() < amount) {
                LOGGER.warning("Insufficient balance for offline transaction");
                return null;
            }
            
            // Create QR code transaction
            QRCodeTransaction qrTx = new QRCodeTransaction(recipientAddress, amount, currency, 
                                                         description, 72); // Valid for 72 hours
            
            qrTransactions.add(qrTx);
            
            // Reserve the amount
            balance.updateBalance(balance.getAvailableBalance() - amount, 
                                balance.getPendingBalance() + amount);
            
            // Set offline mode
            this.isOfflineMode = true;
            
            transactionHistory.addTransaction(qrTx.getQrId(), "OFFLINE_CREATED", recipientAddress, 
                                            amount, currency, description);
            
            LOGGER.info("Created offline transaction: " + qrTx.getQrCode());
            
            return qrTx;
            
        } finally {
            walletLock.unlock();
        }
    }
    
    /**
     * Synchronizes offline transactions when connectivity is restored
     */
    public List<Transaction> synchronizeOfflineTransactions() {
        walletLock.lock();
        try {
            List<Transaction> syncedTransactions = new ArrayList<>();
            Iterator<QRCodeTransaction> iterator = qrTransactions.iterator();
            
            while (iterator.hasNext()) {
                QRCodeTransaction qrTx = iterator.next();
                
                if (!qrTx.isValid()) {
                    // Release reserved amounts for expired transactions
                    CurrencyBalance balance = currencyBalances.get(qrTx.getCurrency());
                    if (balance != null) {
                        balance.updateBalance(balance.getAvailableBalance() + qrTx.getAmount(), 
                                            balance.getPendingBalance() - qrTx.getAmount());
                    }
                    iterator.remove();
                    continue;
                }
                
                if (!qrTx.isSynced() && qrTx.isUsed()) {
                    // Create blockchain transaction for used QR codes
                    byte[] syncData = createOfflineSyncData(qrTx);
                    
                    TransactionBuilder builder = new TransactionBuilder()
                        .from(getAddress())
                        .to(qrTx.getRecipientAddress())
                        .value(BigInteger.valueOf(qrTx.getAmount()))
                        .data(syncData)
                        .gasPrice(BigInteger.valueOf(1500000000L))
                        .nonce(getNonce());
                    
                    Transaction syncTx = builder.build();
                    syncedTransactions.add(syncTx);
                    
                    qrTx.markSynced();
                    
                    // Update balance (remove from pending)
                    CurrencyBalance balance = currencyBalances.get(qrTx.getCurrency());
                    if (balance != null) {
                        balance.updateBalance(balance.getAvailableBalance(), 
                                            balance.getPendingBalance() - qrTx.getAmount());
                    }
                    
                    transactionHistory.addTransaction(syncTx.getHash(), "OFFLINE_SYNCED", 
                                                    qrTx.getRecipientAddress(), qrTx.getAmount(), 
                                                    qrTx.getCurrency(), "Offline transaction synced");
                    
                    LOGGER.info("Synchronized offline transaction: " + qrTx.getQrId());
                }
            }
            
            // Clear offline mode if all transactions are synced
            boolean hasUnsynced = qrTransactions.stream().anyMatch(qr -> !qr.isSynced() && qr.isUsed());
            if (!hasUnsynced) {
                this.isOfflineMode = false;
            }
            
            addSecurityAlert("OFFLINE_SYNC", 
                "Synchronized " + syncedTransactions.size() + " offline transactions", "INFO");
            
            return syncedTransactions;
            
        } finally {
            walletLock.unlock();
        }
    }
    
    /**
     * Creates an emergency transaction for government resistance
     */
    public Transaction createEmergencyTransaction(String emergencyContactId, String emergencyPhrase) {
        walletLock.lock();
        try {
            // Validate emergency contact
            EmergencyContact emergency = emergencyContacts.get(emergencyContactId);
            if (emergency == null) {
                LOGGER.severe("Invalid emergency contact: " + emergencyContactId);
                return null;
            }
            
            // Validate emergency phrase
            if (!validateEmergencyPhrase(emergencyPhrase)) {
                LOGGER.severe("Invalid emergency phrase");
                addSecurityAlert("INVALID_EMERGENCY_PHRASE", 
                    "Invalid emergency phrase attempted", "CRITICAL");
                return null;
            }
            
            // Transfer all available balances to emergency contact
            List<Transaction> emergencyTransactions = new ArrayList<>();
            
            for (CurrencyBalance balance : currencyBalances.values()) {
                if (balance.getAvailableBalance() > 0) {
                    byte[] emergencyData = createEmergencyTransactionData(emergencyContactId, 
                                                                         balance.getCurrencyCode(), 
                                                                         balance.getAvailableBalance(), 
                                                                         emergencyPhrase);
                    
                    TransactionBuilder builder = new TransactionBuilder()
                        .from(getAddress())
                        .to(emergency.getWalletAddress())
                        .value(BigInteger.valueOf(balance.getAvailableBalance()))
                        .data(emergencyData)
                        .gasPrice(BigInteger.valueOf(10000000000L)) // Maximum priority
                        .nonce(getNonce());
                    
                    Transaction tx = builder.build();
                    
                    // Clear balance
                    balance.updateBalance(0, 0);
                    
                    transactionHistory.addTransaction(tx.getHash(), "EMERGENCY_TRANSFER", 
                                                    emergency.getName(), balance.getAvailableBalance(), 
                                                    balance.getCurrencyCode(), "Emergency transfer");
                    
                    addSecurityAlert("EMERGENCY_TRANSACTION", 
                        "Emergency transfer executed: " + balance.getAvailableBalance() + 
                        " " + balance.getCurrencyCode(), "CRITICAL");
                    
                    // Return first transaction (in real implementation, would batch)
                    return tx;
                }
            }
            
            return null;
            
        } finally {
            walletLock.unlock();
        }
    }
    
    /**
     * Adds a new contact
     */
    public boolean addContact(String displayName, String walletAddress, String phoneNumber, 
                            Contact.ContactType type) {
        walletLock.lock();
        try {
            String contactId = generateContactId(displayName, walletAddress);
            
            Contact contact = new Contact(contactId, displayName, walletAddress, phoneNumber, 
                                        countryCode, type);
            contacts.put(contactId, contact);
            
            // Auto-trust family members
            if (type == Contact.ContactType.FAMILY) {
                contact.setTrusted(true);
            }
            
            LOGGER.info("Added contact: " + displayName + " (" + type + ")");
            return true;
            
        } finally {
            walletLock.unlock();
        }
    }
    
    /**
     * Sets up a recurring payment
     */
    public boolean setupRecurringPayment(String contactId, String description, long amount, 
                                        String currency, RecurringPayment.RecurringSchedule schedule) {
        walletLock.lock();
        try {
            if (!contacts.containsKey(contactId)) {
                LOGGER.warning("Contact not found for recurring payment: " + contactId);
                return false;
            }
            
            RecurringPayment payment = new RecurringPayment(contactId, description, amount, 
                                                          currency, schedule);
            recurringPayments.add(payment);
            
            LOGGER.info("Set up recurring payment: " + description + " - " + 
                       amount + " " + currency + " " + schedule);
            return true;
            
        } finally {
            walletLock.unlock();
        }
    }
    
    /**
     * Processes due recurring payments
     */
    public List<Transaction> processDueRecurringPayments() {
        walletLock.lock();
        try {
            List<Transaction> processedPayments = new ArrayList<>();
            
            for (RecurringPayment payment : recurringPayments) {
                if (payment.isDue()) {
                    Contact contact = contacts.get(payment.getRecipientContactId());
                    if (contact != null) {
                        Transaction tx = sendMoney(payment.getRecipientContactId(), 
                                                 payment.getAmount(), payment.getCurrency(), 
                                                 "Recurring: " + payment.getDescription());
                        
                        if (tx != null) {
                            payment.markExecuted();
                            processedPayments.add(tx);
                            
                            LOGGER.info("Executed recurring payment: " + payment.getDescription());
                        }
                    }
                }
            }
            
            return processedPayments;
            
        } finally {
            walletLock.unlock();
        }
    }
    
    // Data creation methods
    
    private byte[] createPaymentData(String contactId, long amount, String currency, String message) {
        try {
            StringBuilder dataBuilder = new StringBuilder();
            dataBuilder.append("CITIZEN_PAYMENT:");
            dataBuilder.append("CONTACT_ID=").append(contactId).append(";");
            dataBuilder.append("AMOUNT=").append(amount).append(";");
            dataBuilder.append("CURRENCY=").append(currency).append(";");
            dataBuilder.append("MESSAGE=").append(message != null ? message : "").append(";");
            dataBuilder.append("CITIZEN_ID=").append(citizenId).append(";");
            dataBuilder.append("TIMESTAMP=").append(System.currentTimeMillis()).append(";");
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(dataBuilder.toString().getBytes());
            
        } catch (NoSuchAlgorithmException e) {
            return new byte[32];
        }
    }
    
    private byte[] createExchangeData(String fromCurrency, String toCurrency, long fromAmount, 
                                    long toAmount, double rate) {
        try {
            StringBuilder dataBuilder = new StringBuilder();
            dataBuilder.append("CITIZEN_EXCHANGE:");
            dataBuilder.append("FROM_CURRENCY=").append(fromCurrency).append(";");
            dataBuilder.append("TO_CURRENCY=").append(toCurrency).append(";");
            dataBuilder.append("FROM_AMOUNT=").append(fromAmount).append(";");
            dataBuilder.append("TO_AMOUNT=").append(toAmount).append(";");
            dataBuilder.append("RATE=").append(rate).append(";");
            dataBuilder.append("CITIZEN_ID=").append(citizenId).append(";");
            dataBuilder.append("TIMESTAMP=").append(System.currentTimeMillis()).append(";");
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(dataBuilder.toString().getBytes());
            
        } catch (NoSuchAlgorithmException e) {
            return new byte[32];
        }
    }
    
    private byte[] createOfflineSyncData(QRCodeTransaction qrTx) {
        try {
            StringBuilder dataBuilder = new StringBuilder();
            dataBuilder.append("OFFLINE_SYNC:");
            dataBuilder.append("QR_ID=").append(qrTx.getQrId()).append(";");
            dataBuilder.append("QR_CODE=").append(qrTx.getQrCode()).append(";");
            dataBuilder.append("CREATED_AT=").append(qrTx.getCreatedAt().toEpochMilli()).append(";");
            dataBuilder.append("CITIZEN_ID=").append(citizenId).append(";");
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(dataBuilder.toString().getBytes());
            
        } catch (NoSuchAlgorithmException e) {
            return new byte[32];
        }
    }
    
    private byte[] createEmergencyTransactionData(String emergencyContactId, String currency, 
                                                 long amount, String emergencyPhrase) {
        try {
            StringBuilder dataBuilder = new StringBuilder();
            dataBuilder.append("EMERGENCY_TRANSFER:");
            dataBuilder.append("EMERGENCY_CONTACT=").append(emergencyContactId).append(";");
            dataBuilder.append("CURRENCY=").append(currency).append(";");
            dataBuilder.append("AMOUNT=").append(amount).append(";");
            dataBuilder.append("PHRASE_HASH=").append(HashUtils.generateHash(emergencyPhrase)).append(";");
            dataBuilder.append("CITIZEN_ID=").append(citizenId).append(";");
            dataBuilder.append("TIMESTAMP=").append(System.currentTimeMillis()).append(";");
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(dataBuilder.toString().getBytes());
            
        } catch (NoSuchAlgorithmException e) {
            return new byte[32];
        }
    }
    
    // Utility and helper methods
    
    private void addCurrencyBalance(String currencyCode, String currencyName, String symbol, 
                                  long initialBalance, boolean isLocalCurrency) {
        CurrencyBalance balance = new CurrencyBalance(currencyCode, currencyName, symbol, 
                                                     initialBalance, isLocalCurrency);
        currencyBalances.put(currencyCode, balance);
    }
    
    private String getCurrencyName(String currencyCode) {
        // Mapping of currency codes to names (simplified)
        Map<String, String> currencyNames = Map.of(
            "NGN", "Nigerian Naira",
            "KES", "Kenyan Shilling",
            "ZAR", "South African Rand",
            "GHS", "Ghanaian Cedi",
            "USD", "US Dollar",
            "EUR", "Euro"
        );
        return currencyNames.getOrDefault(currencyCode, currencyCode);
    }
    
    private String getCurrencySymbol(String currencyCode) {
        // Mapping of currency codes to symbols (simplified)
        Map<String, String> currencySymbols = Map.of(
            "NGN", "₦",
            "KES", "KSh",
            "ZAR", "R",
            "GHS", "₵",
            "USD", "$",
            "EUR", "€"
        );
        return currencySymbols.getOrDefault(currencyCode, currencyCode);
    }
    
    private ExchangeRate getExchangeRate(String fromCurrency, String toCurrency) {
        String key = fromCurrency + "/" + toCurrency;
        ExchangeRate cached = exchangeRates.get(key);
        
        // In production, this would fetch from multiple sources
        if (cached == null) {
            // Simulate exchange rate with African currency preference
            double baseRate = getSimulatedExchangeRate(fromCurrency, toCurrency);
            double fairRate = applyAfricanCurrencyBonus(fromCurrency, toCurrency, baseRate);
            
            cached = new ExchangeRate(fromCurrency, toCurrency, baseRate, fairRate);
            exchangeRates.put(key, cached);
        }
        
        return cached;
    }
    
    private double getSimulatedExchangeRate(String fromCurrency, String toCurrency) {
        // Simplified exchange rate simulation
        Map<String, Double> rates = Map.of(
            "NGN/USD", 0.0024,
            "KES/USD", 0.0067,
            "ZAR/USD", 0.055,
            "GHS/USD", 0.083,
            "USD/EUR", 0.85
        );
        
        String pair = fromCurrency + "/" + toCurrency;
        String reversePair = toCurrency + "/" + fromCurrency;
        
        if (rates.containsKey(pair)) {
            return rates.get(pair);
        } else if (rates.containsKey(reversePair)) {
            return 1.0 / rates.get(reversePair);
        } else {
            return 1.0; // Default 1:1 rate
        }
    }
    
    private double applyAfricanCurrencyBonus(String fromCurrency, String toCurrency, double baseRate) {
        Set<String> africanCurrencies = Set.of("NGN", "KES", "ZAR", "GHS", "EGP", "MAD", "TZS", "UGX");
        
        // Apply 2% bonus for African currency exchanges
        if (africanCurrencies.contains(fromCurrency) || africanCurrencies.contains(toCurrency)) {
            return baseRate * 1.02;
        }
        
        return baseRate;
    }
    
    private void generateRecoverySeeds() {
        try {
            SecureRandom random = new SecureRandom();
            for (int i = 0; i < 3; i++) { // Generate 3 recovery seeds
                byte[] seed = new byte[32];
                random.nextBytes(seed);
                recoverySeeds.add(seed);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating recovery seeds", e);
        }
    }
    
    private boolean validateEmergencyPhrase(String phrase) {
        // In production, this would validate against securely stored phrase hashes
        return phrase != null && phrase.length() >= 12;
    }
    
    private String generateContactId(String displayName, String walletAddress) {
        return HashUtils.generateHash(displayName + walletAddress + citizenId).substring(0, 12);
    }
    
    private String formatAmount(long amount, String currency) {
        double formattedAmount = amount / 100.0; // Assuming 2 decimal places
        return String.format("%.2f", formattedAmount);
    }
    
    private long calculateCitizenGasPrice(String currency) {
        // Lower gas prices for citizens, especially for local currencies
        long baseGas = 1000000000L; // 1 Gwei
        
        if (currency.equals(localCurrency)) {
            baseGas = (long) (baseGas * 0.8); // 20% discount for local currency
        }
        
        return baseGas;
    }
    
    private long calculateExchangeGasPrice(String fromCurrency, String toCurrency) {
        return calculateCitizenGasPrice(fromCurrency) * 2; // Higher for exchanges
    }
    
    private void addSecurityAlert(String alertType, String message, String severity) {
        SecurityAlert alert = new SecurityAlert(alertType, message, severity, Instant.now());
        securityAlerts.add(alert);
        
        // Keep alerts manageable
        if (securityAlerts.size() > 100) {
            securityAlerts.subList(0, 20).clear();
        }
    }
    
    private long getNonce() {
        return System.currentTimeMillis();
    }
    
    // Supporting component classes
    
    private static class CurrencyPreferences {
        private final String localCurrency;
        
        CurrencyPreferences(String localCurrency) {
            this.localCurrency = localCurrency;
        }
    }
    
    private static class SocialPaymentManager {
        // Manages social payment features
    }
    
    private static class OfflineTransactionManager {
        // Manages offline transaction logic
    }
    
    private static class SMSTransactionManager {
        void sendPaymentRequest(String phoneNumber, String message) {
            // In production, would integrate with SMS gateway
            LOGGER.info("SMS payment request sent to " + phoneNumber + ": " + message);
        }
    }
    
    private static class PrivacyEngine {
        private final String citizenId;
        
        PrivacyEngine(String citizenId) {
            this.citizenId = citizenId;
        }
    }
    
    private static class AnonymityManager {
        // Manages anonymity features
    }
    
    private static class BudgetManager {
        private final String currency;
        
        BudgetManager(String currency) {
            this.currency = currency;
        }
    }
    
    private static class SavingsGoals {
        // Manages savings goals
    }
    
    private static class TransactionHistory {
        private final List<HistoryEntry> history = new ArrayList<>();
        
        void addTransaction(String txHash, String type, String counterparty, 
                           long amount, String currency, String description) {
            HistoryEntry entry = new HistoryEntry(txHash, type, counterparty, 
                                                amount, currency, description);
            history.add(entry);
        }
        
        private static class HistoryEntry {
            private final String txHash;
            private final String type;
            private final String counterparty;
            private final long amount;
            private final String currency;
            private final String description;
            private final Instant timestamp;
            
            HistoryEntry(String txHash, String type, String counterparty, 
                        long amount, String currency, String description) {
                this.txHash = txHash;
                this.type = type;
                this.counterparty = counterparty;
                this.amount = amount;
                this.currency = currency;
                this.description = description;
                this.timestamp = Instant.now();
            }
        }
    }
    
    private static class SpendingAnalytics {
        private final Map<String, Long> categorySpending = new HashMap<>();
        
        void recordSpending(long amount, String currency, String category) {
            String key = currency + "_" + category;
            categorySpending.put(key, categorySpending.getOrDefault(key, 0L) + amount);
        }
    }
    
    private static class BackupManager {
        private final String citizenId;
        
        BackupManager(String citizenId) {
            this.citizenId = citizenId;
        }
    }
    
    private static class ExchangeRate {
        private final String fromCurrency;
        private final String toCurrency;
        private final double rate;
        private final double fairRate;
        private final Instant timestamp;
        
        ExchangeRate(String fromCurrency, String toCurrency, double rate, double fairRate) {
            this.fromCurrency = fromCurrency;
            this.toCurrency = toCurrency;
            this.rate = rate;
            this.fairRate = fairRate;
            this.timestamp = Instant.now();
        }
        
        double getFairRate() { return fairRate; }
    }
    
    private static class SecurityAlert {
        private final String alertType;
        private final String message;
        private final String severity;
        private final Instant timestamp;
        
        SecurityAlert(String alertType, String message, String severity, Instant timestamp) {
            this.alertType = alertType;
            this.message = message;
            this.severity = severity;
            this.timestamp = timestamp;
        }
    }
    
    private static class EmergencyContact {
        private final String contactId;
        private final String name;
        private final String walletAddress;
        
        EmergencyContact(String contactId, String name, String walletAddress) {
            this.contactId = contactId;
            this.name = name;
            this.walletAddress = walletAddress;
        }
        
        String getName() { return name; }
        String getWalletAddress() { return walletAddress; }
    }
    
    private static class FamilyGroup {
        private final String groupId;
        private final List<String> memberIds;
        
        FamilyGroup(String groupId) {
            this.groupId = groupId;
            this.memberIds = new ArrayList<>();
        }
    }
    
    // Public getters and methods
    
    public String getCitizenId() { return citizenId; }
    public String getCountryCode() { return countryCode; }
    public String getLocalCurrency() { return localCurrency; }
    public String getDisplayName() { return displayName; }
    public boolean isOfflineMode() { return isOfflineMode; }
    
    public CurrencyBalance getCurrencyBalance(String currency) {
        return currencyBalances.get(currency);
    }
    
    public Map<String, CurrencyBalance> getAllCurrencyBalances() {
        return new HashMap<>(currencyBalances);
    }
    
    public List<Contact> getAllContacts() {
        return new ArrayList<>(contacts.values());
    }
    
    public List<Contact> getContactsByType(Contact.ContactType type) {
        return contacts.values().stream()
                      .filter(contact -> contact.getType() == type)
                      .toList();
    }
    
    public List<QRCodeTransaction> getActiveQRTransactions() {
        return qrTransactions.stream()
                           .filter(QRCodeTransaction::isValid)
                           .toList();
    }
    
    public List<RecurringPayment> getActiveRecurringPayments() {
        return recurringPayments.stream()
                               .filter(RecurringPayment::isActive)
                               .toList();
    }
    
    public List<SecurityAlert> getRecentSecurityAlerts(int limit) {
        int size = securityAlerts.size();
        int fromIndex = Math.max(0, size - limit);
        return new ArrayList<>(securityAlerts.subList(fromIndex, size));
    }
    
    public String getFormattedBalance(String currency) {
        CurrencyBalance balance = currencyBalances.get(currency);
        return balance != null ? balance.getFormattedBalance() : "0.00";
    }
    
    public String getTotalBalanceInPreferredCurrency() {
        // Calculate total value in preferred display currency
        double totalValue = 0.0;
        
        for (CurrencyBalance balance : currencyBalances.values()) {
            if (balance.getCurrencyCode().equals(preferredDisplayCurrency)) {
                totalValue += balance.getAvailableBalance() / 100.0;
            } else {
                // Convert to preferred currency
                ExchangeRate rate = getExchangeRate(balance.getCurrencyCode(), preferredDisplayCurrency);
                if (rate != null) {
                    totalValue += (balance.getAvailableBalance() / 100.0) * rate.getFairRate();
                }
            }
        }
        
        return getCurrencySymbol(preferredDisplayCurrency) + String.format(" %.2f", totalValue);
    }
    
    /**
     * CitizenWalletMempoolConfig for citizen-friendly validation
     */
    private static class CitizenWalletMempoolConfig implements org.example.app.core.mempool.MempoolConfig {
        @Override
        public BigInteger getMinGasPrice() {
            return BigInteger.valueOf(500000000L); // 0.5 Gwei - very low for citizens
        }
        
        @Override
        public int getMaxTxPoolSize() {
            return 25; // Small pool for individual users
        }
        
        @Override
        public long getMaxTxAge() {
            return 1800000; // 30 minutes maximum age
        }
    }
}