package org.example.app.core.banking.bridge;

import org.example.app.core.crypto.PKIManager;
import org.example.app.core.state.StateManager;
import org.example.app.core.internal.EventLog;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * MobileMoneyGateway - Integration with African mobile money platforms
 * 
 * Supports:
 * - M-Pesa (Kenya, Tanzania, other markets)
 * - MTN Mobile Money (Multiple African countries)
 * - Airtel Money
 * - Orange Money
 * - Vodacom M-Pesa
 * 
 * Enables direct on/off-ramp from mobile money to tokenized currency
 * without requiring traditional bank accounts (Requirement #7: Independence)
 */
public class MobileMoneyGateway {
    private static final Logger LOGGER = Logger.getLogger(MobileMoneyGateway.class.getName());
    
    // Transaction limits
    private static final BigDecimal MIN_TRANSACTION = new BigDecimal("1");
    private static final BigDecimal MAX_TRANSACTION_DAILY = new BigDecimal("100000");
    private static final int MAX_TRANSACTIONS_PER_DAY = 50;
    
    // Mobile money providers
    public enum MobileMoneyProvider {
        M_PESA("M-Pesa", Set.of("KE", "TZ", "UG", "GH", "MZ", "ZA")),
        MTN_MOMO("MTN Mobile Money", Set.of("UG", "GH", "RW", "CI", "BJ", "CM", "NG")),
        AIRTEL_MONEY("Airtel Money", Set.of("KE", "TZ", "UG", "RW", "ZM", "MW", "NG")),
        ORANGE_MONEY("Orange Money", Set.of("CI", "SN", "ML", "BF", "CM", "MG")),
        VODACOM_M_PESA("Vodacom M-Pesa", Set.of("TZ", "MZ", "CD"));
        
        private final String displayName;
        private final Set<String> countries;
        
        MobileMoneyProvider(String displayName, Set<String> countries) {
            this.displayName = displayName;
            this.countries = countries;
        }
        
        public String getDisplayName() { return displayName; }
        public Set<String> getCountries() { return countries; }
        
        public boolean operatesInCountry(String countryCode) {
            return countries.contains(countryCode);
        }
    }
    
    // Transaction types
    public enum TransactionType {
        DEPOSIT,        // Mobile money to token
        WITHDRAWAL,     // Token to mobile money
        P2P_TRANSFER,   // Mobile-to-mobile via blockchain
        BILL_PAYMENT,   // Pay bills using tokens
        MERCHANT_PAY    // Pay merchants
    }
    
    // Transaction status
    public enum TransactionStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        REVERSED
    }
    
    /**
     * Represents a mobile money account linked to blockchain
     */
    public static class MobileMoneyAccount {
        private final String accountId;
        private final String phoneNumber;
        private final String countryCode;
        private final MobileMoneyProvider provider;
        private final Address blockchainAddress;
        private final Date registrationDate;
        
        private boolean isVerified;
        private boolean isActive;
        private BigDecimal dailyLimit;
        private Date kycVerificationDate;
        
        public MobileMoneyAccount(String phoneNumber, String countryCode,
                                 MobileMoneyProvider provider, Address blockchainAddress) {
            this.accountId = UUID.randomUUID().toString();
            this.phoneNumber = phoneNumber;
            this.countryCode = countryCode;
            this.provider = provider;
            this.blockchainAddress = blockchainAddress;
            this.registrationDate = new Date();
            this.isVerified = false;
            this.isActive = true;
            this.dailyLimit = new BigDecimal("10000"); // Default limit
        }
        
        // Getters
        public String getAccountId() { return accountId; }
        public String getPhoneNumber() { return phoneNumber; }
        public String getCountryCode() { return countryCode; }
        public MobileMoneyProvider getProvider() { return provider; }
        public Address getBlockchainAddress() { return blockchainAddress; }
        public Date getRegistrationDate() { return registrationDate; }
        public boolean isVerified() { return isVerified; }
        public boolean isActive() { return isActive; }
        public BigDecimal getDailyLimit() { return dailyLimit; }
        public Date getKycVerificationDate() { return kycVerificationDate; }
        
        public void setVerified(boolean verified) { 
            this.isVerified = verified;
            if (verified) {
                this.kycVerificationDate = new Date();
                this.dailyLimit = new BigDecimal("50000"); // Increased limit
            }
        }
        
        public void setActive(boolean active) { this.isActive = active; }
        
        public void setDailyLimit(BigDecimal limit) { this.dailyLimit = limit; }
        
        public String getMaskedPhoneNumber() {
            if (phoneNumber.length() > 4) {
                return phoneNumber.substring(0, 4) + "****" + 
                       phoneNumber.substring(phoneNumber.length() - 2);
            }
            return phoneNumber;
        }
    }
    
    /**
     * Represents a mobile money transaction
     */
    public static class MobileMoneyTransaction {
        private final String transactionId;
        private final String accountId;
        private final TransactionType type;
        private final BigDecimal amount;
        private final String currency;
        private final Date initiationTime;
        private final Map<String, String> metadata;
        
        private TransactionStatus status;
        private String providerTransactionId;
        private Date completionTime;
        private String errorMessage;
        private int retryCount;
        
        public MobileMoneyTransaction(String accountId, TransactionType type,
                                     BigDecimal amount, String currency) {
            this.transactionId = generateTransactionId();
            this.accountId = accountId;
            this.type = type;
            this.amount = amount;
            this.currency = currency;
            this.initiationTime = new Date();
            this.metadata = new HashMap<>();
            this.status = TransactionStatus.PENDING;
            this.retryCount = 0;
        }
        
        // Getters
        public String getTransactionId() { return transactionId; }
        public String getAccountId() { return accountId; }
        public TransactionType getType() { return type; }
        public BigDecimal getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public Date getInitiationTime() { return initiationTime; }
        public Map<String, String> getMetadata() { return new HashMap<>(metadata); }
        public TransactionStatus getStatus() { return status; }
        public String getProviderTransactionId() { return providerTransactionId; }
        public Date getCompletionTime() { return completionTime; }
        public String getErrorMessage() { return errorMessage; }
        public int getRetryCount() { return retryCount; }
        
        public void setStatus(TransactionStatus status) { this.status = status; }
        
        public void setProviderTransactionId(String id) { 
            this.providerTransactionId = id; 
        }
        
        public void complete() {
            this.status = TransactionStatus.COMPLETED;
            this.completionTime = new Date();
        }
        
        public void fail(String error) {
            this.status = TransactionStatus.FAILED;
            this.errorMessage = error;
            this.completionTime = new Date();
        }
        
        public void incrementRetry() { this.retryCount++; }
        
        public void addMetadata(String key, String value) {
            metadata.put(key, value);
        }
        
        private static String generateTransactionId() {
            return "MM" + System.currentTimeMillis() + 
                   String.format("%04d", new SecureRandom().nextInt(10000));
        }
    }
    
    /**
     * Tracks daily transaction limits
     */
    private static class DailyLimitTracker {
        private final Map<String, BigDecimal> dailyAmounts;
        private final Map<String, Integer> dailyCounts;
        private final Map<String, Date> lastResetDates;
        
        public DailyLimitTracker() {
            this.dailyAmounts = new ConcurrentHashMap<>();
            this.dailyCounts = new ConcurrentHashMap<>();
            this.lastResetDates = new ConcurrentHashMap<>();
        }
        
        public boolean checkAndUpdateLimit(String accountId, BigDecimal amount) {
            resetIfNewDay(accountId);
            
            BigDecimal currentAmount = dailyAmounts.getOrDefault(accountId, BigDecimal.ZERO);
            int currentCount = dailyCounts.getOrDefault(accountId, 0);
            
            // Check limits
            if (currentAmount.add(amount).compareTo(MAX_TRANSACTION_DAILY) > 0) {
                return false;
            }
            
            if (currentCount >= MAX_TRANSACTIONS_PER_DAY) {
                return false;
            }
            
            // Update limits
            dailyAmounts.put(accountId, currentAmount.add(amount));
            dailyCounts.put(accountId, currentCount + 1);
            
            return true;
        }
        
        private void resetIfNewDay(String accountId) {
            Date lastReset = lastResetDates.get(accountId);
            Date today = new Date();
            
            if (lastReset == null || !isSameDay(lastReset, today)) {
                dailyAmounts.put(accountId, BigDecimal.ZERO);
                dailyCounts.put(accountId, 0);
                lastResetDates.put(accountId, today);
            }
        }
        
        private boolean isSameDay(Date date1, Date date2) {
            Calendar cal1 = Calendar.getInstance();
            Calendar cal2 = Calendar.getInstance();
            cal1.setTime(date1);
            cal2.setTime(date2);
            
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                   cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
        }
    }
    
    // Core infrastructure
    private final StateManager stateManager;
    private final PKIManager pkiManager;
    private final EventLog eventLog;
    
    // Account and transaction management
    private final Map<String, MobileMoneyAccount> accounts; // accountId -> account
    private final Map<String, String> phoneToAccount; // phoneNumber -> accountId
    private final Map<String, MobileMoneyTransaction> transactions;
    private final DailyLimitTracker limitTracker;
    
    // Provider integrations
    private final Map<MobileMoneyProvider, ProviderAPI> providerAPIs;
    
    // Processing
    private final ExecutorService transactionExecutor;
    private final ScheduledExecutorService monitorExecutor;
    
    // Statistics
    private final AtomicLong totalTransactions;
    private final AtomicLong successfulTransactions;
    private final AtomicLong failedTransactions;
    private final Map<MobileMoneyProvider, AtomicLong> providerVolumes;
    
    /**
     * Mock provider API (in production, this would integrate with real APIs)
     */
    private static class ProviderAPI {
        private final MobileMoneyProvider provider;
        private final SecureRandom random;
        
        public ProviderAPI(MobileMoneyProvider provider) {
            this.provider = provider;
            this.random = new SecureRandom();
        }
        
        public String initiateDeposit(String phoneNumber, BigDecimal amount, String currency) {
            // Simulate API call
            return "PROV_" + System.currentTimeMillis() + random.nextInt(10000);
        }
        
        public String initiateWithdrawal(String phoneNumber, BigDecimal amount, String currency) {
            // Simulate API call
            return "PROV_" + System.currentTimeMillis() + random.nextInt(10000);
        }
        
        public boolean checkTransactionStatus(String providerTxId) {
            // Simulate status check - 95% success rate
            return random.nextDouble() > 0.05;
        }
    }
    
    /**
     * Constructor
     */
    public MobileMoneyGateway(StateManager stateManager,
                             PKIManager pkiManager,
                             EventLog eventLog) {
        this.stateManager = stateManager;
        this.pkiManager = pkiManager;
        this.eventLog = eventLog;
        
        this.accounts = new ConcurrentHashMap<>();
        this.phoneToAccount = new ConcurrentHashMap<>();
        this.transactions = new ConcurrentHashMap<>();
        this.limitTracker = new DailyLimitTracker();
        
        this.providerAPIs = new ConcurrentHashMap<>();
        this.transactionExecutor = Executors.newFixedThreadPool(20);
        this.monitorExecutor = Executors.newScheduledThreadPool(2);
        
        this.totalTransactions = new AtomicLong(0);
        this.successfulTransactions = new AtomicLong(0);
        this.failedTransactions = new AtomicLong(0);
        this.providerVolumes = new ConcurrentHashMap<>();
        
        initializeProviders();
        startMonitoring();
        
        LOGGER.info("MobileMoneyGateway initialized with " + 
                   providerAPIs.size() + " providers");
    }
    
    /**
     * Initializes provider APIs
     */
    private void initializeProviders() {
        for (MobileMoneyProvider provider : MobileMoneyProvider.values()) {
            providerAPIs.put(provider, new ProviderAPI(provider));
            providerVolumes.put(provider, new AtomicLong(0));
        }
    }
    
    /**
     * Registers a mobile money account
     */
    public String registerAccount(String phoneNumber, String countryCode,
                                  MobileMoneyProvider provider, Address blockchainAddress) {
        try {
            LOGGER.info("Registering mobile money account: " + phoneNumber + 
                       " (" + provider.getDisplayName() + ")");
            
            // Validate inputs
            if (!validatePhoneNumber(phoneNumber)) {
                LOGGER.warning("Invalid phone number format: " + phoneNumber);
                return null;
            }
            
            if (!provider.operatesInCountry(countryCode)) {
                LOGGER.warning(provider.getDisplayName() + 
                             " does not operate in country: " + countryCode);
                return null;
            }
            
            // Check if phone already registered
            if (phoneToAccount.containsKey(phoneNumber)) {
                LOGGER.warning("Phone number already registered: " + phoneNumber);
                return null;
            }
            
            // Create account
            MobileMoneyAccount account = new MobileMoneyAccount(
                phoneNumber, countryCode, provider, blockchainAddress);
            
            // Store account
            accounts.put(account.getAccountId(), account);
            phoneToAccount.put(phoneNumber, account.getAccountId());
            
            eventLog.logEvent("MOBILE_MONEY_REGISTERED: " + phoneNumber + 
                            " (" + provider.getDisplayName() + ")");
            
            LOGGER.info("Mobile money account registered: " + account.getAccountId());
            return account.getAccountId();
            
        } catch (Exception e) {
            LOGGER.severe("Error registering mobile money account: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Deposits from mobile money to tokens
     */
    public String deposit(String accountId, BigDecimal amount, String currency) {
        try {
            MobileMoneyAccount account = accounts.get(accountId);
            if (account == null || !account.isActive()) {
                LOGGER.warning("Account not found or inactive: " + accountId);
                return null;
            }
            
            // Validate amount
            if (amount.compareTo(MIN_TRANSACTION) < 0) {
                LOGGER.warning("Amount below minimum: " + amount);
                return null;
            }
            
            // Check daily limits
            if (!limitTracker.checkAndUpdateLimit(accountId, amount)) {
                LOGGER.warning("Daily limit exceeded for account: " + accountId);
                return null;
            }
            
            // Create transaction
            MobileMoneyTransaction transaction = new MobileMoneyTransaction(
                accountId, TransactionType.DEPOSIT, amount, currency);
            
            transaction.addMetadata("phoneNumber", account.getMaskedPhoneNumber());
            transaction.addMetadata("provider", account.getProvider().getDisplayName());
            transaction.addMetadata("country", account.getCountryCode());
            
            transactions.put(transaction.getTransactionId(), transaction);
            totalTransactions.incrementAndGet();
            
            // Process asynchronously
            CompletableFuture.runAsync(() -> processDeposit(transaction, account), 
                                      transactionExecutor);
            
            LOGGER.info("Deposit initiated: " + transaction.getTransactionId());
            return transaction.getTransactionId();
            
        } catch (Exception e) {
            LOGGER.severe("Error initiating deposit: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Withdraws from tokens to mobile money
     */
    public String withdraw(String accountId, BigDecimal amount, String currency) {
        try {
            MobileMoneyAccount account = accounts.get(accountId);
            if (account == null || !account.isActive()) {
                LOGGER.warning("Account not found or inactive: " + accountId);
                return null;
            }
            
            // Validate amount
            if (amount.compareTo(MIN_TRANSACTION) < 0) {
                LOGGER.warning("Amount below minimum: " + amount);
                return null;
            }
            
            // Check daily limits
            if (!limitTracker.checkAndUpdateLimit(accountId, amount)) {
                LOGGER.warning("Daily limit exceeded for account: " + accountId);
                return null;
            }
            
            // Create transaction
            MobileMoneyTransaction transaction = new MobileMoneyTransaction(
                accountId, TransactionType.WITHDRAWAL, amount, currency);
            
            transaction.addMetadata("phoneNumber", account.getMaskedPhoneNumber());
            transaction.addMetadata("provider", account.getProvider().getDisplayName());
            
            transactions.put(transaction.getTransactionId(), transaction);
            totalTransactions.incrementAndGet();
            
            // Process asynchronously
            CompletableFuture.runAsync(() -> processWithdrawal(transaction, account), 
                                      transactionExecutor);
            
            LOGGER.info("Withdrawal initiated: " + transaction.getTransactionId());
            return transaction.getTransactionId();
            
        } catch (Exception e) {
            LOGGER.severe("Error initiating withdrawal: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Processes a deposit transaction
     */
    private void processDeposit(MobileMoneyTransaction transaction, MobileMoneyAccount account) {
        try {
            transaction.setStatus(TransactionStatus.PROCESSING);
            
            // Get provider API
            ProviderAPI providerAPI = providerAPIs.get(account.getProvider());
            
            // Initiate deposit with provider
            String providerTxId = providerAPI.initiateDeposit(
                account.getPhoneNumber(), transaction.getAmount(), transaction.getCurrency());
            
            transaction.setProviderTransactionId(providerTxId);
            
            // Wait for confirmation (simulate)
            Thread.sleep(2000);
            
            // Check status
            boolean success = providerAPI.checkTransactionStatus(providerTxId);
            
            if (success) {
                // Credit blockchain account (simplified)
                transaction.complete();
                successfulTransactions.incrementAndGet();
                providerVolumes.get(account.getProvider()).addAndGet(
                    transaction.getAmount().longValue());
                
                eventLog.logEvent("DEPOSIT_COMPLETED: " + transaction.getTransactionId() + 
                                " (Amount: " + transaction.getAmount() + " " + 
                                transaction.getCurrency() + ")");
                
                LOGGER.info("Deposit completed: " + transaction.getTransactionId());
            } else {
                transaction.fail("Provider transaction failed");
                failedTransactions.incrementAndGet();
                LOGGER.warning("Deposit failed: " + transaction.getTransactionId());
            }
            
        } catch (Exception e) {
            transaction.fail("Processing error: " + e.getMessage());
            failedTransactions.incrementAndGet();
            LOGGER.severe("Error processing deposit: " + e.getMessage());
        }
    }
    
    /**
     * Processes a withdrawal transaction
     */
    private void processWithdrawal(MobileMoneyTransaction transaction, MobileMoneyAccount account) {
        try {
            transaction.setStatus(TransactionStatus.PROCESSING);
            
            // Verify blockchain balance (simplified)
            
            // Get provider API
            ProviderAPI providerAPI = providerAPIs.get(account.getProvider());
            
            // Initiate withdrawal with provider
            String providerTxId = providerAPI.initiateWithdrawal(
                account.getPhoneNumber(), transaction.getAmount(), transaction.getCurrency());
            
            transaction.setProviderTransactionId(providerTxId);
            
            // Wait for confirmation (simulate)
            Thread.sleep(2000);
            
            // Check status
            boolean success = providerAPI.checkTransactionStatus(providerTxId);
            
            if (success) {
                // Debit blockchain account (simplified)
                transaction.complete();
                successfulTransactions.incrementAndGet();
                providerVolumes.get(account.getProvider()).addAndGet(
                    transaction.getAmount().longValue());
                
                eventLog.logEvent("WITHDRAWAL_COMPLETED: " + transaction.getTransactionId() + 
                                " (Amount: " + transaction.getAmount() + " " + 
                                transaction.getCurrency() + ")");
                
                LOGGER.info("Withdrawal completed: " + transaction.getTransactionId());
            } else {
                transaction.fail("Provider transaction failed");
                failedTransactions.incrementAndGet();
                LOGGER.warning("Withdrawal failed: " + transaction.getTransactionId());
            }
            
        } catch (Exception e) {
            transaction.fail("Processing error: " + e.getMessage());
            failedTransactions.incrementAndGet();
            LOGGER.severe("Error processing withdrawal: " + e.getMessage());
        }
    }
    
    /**
     * Validates phone number format
     */
    private boolean validatePhoneNumber(String phoneNumber) {
        // Basic validation - phone number should be digits only and reasonable length
        return phoneNumber != null && 
               phoneNumber.matches("\\d{9,15}");
    }
    
    /**
     * Monitoring and maintenance
     */
    private void startMonitoring() {
        // Monitor transaction status every 30 seconds
        monitorExecutor.scheduleAtFixedRate(this::monitorTransactions, 30, 30, TimeUnit.SECONDS);
    }
    
    private void monitorTransactions() {
        try {
            // Cleanup old completed transactions
            transactions.entrySet().removeIf(entry -> {
                MobileMoneyTransaction tx = entry.getValue();
                if (tx.getStatus() == TransactionStatus.COMPLETED || 
                    tx.getStatus() == TransactionStatus.FAILED) {
                    long ageHours = (System.currentTimeMillis() - 
                                   tx.getInitiationTime().getTime()) / (1000 * 60 * 60);
                    return ageHours > 24; // Keep for 24 hours
                }
                return false;
            });
            
        } catch (Exception e) {
            LOGGER.warning("Error monitoring transactions: " + e.getMessage());
        }
    }
    
    /**
     * Public API methods
     */
    public MobileMoneyAccount getAccount(String accountId) {
        return accounts.get(accountId);
    }
    
    public MobileMoneyAccount getAccountByPhone(String phoneNumber) {
        String accountId = phoneToAccount.get(phoneNumber);
        return accountId != null ? accounts.get(accountId) : null;
    }
    
    public MobileMoneyTransaction getTransaction(String transactionId) {
        return transactions.get(transactionId);
    }
    
    public List<MobileMoneyProvider> getAvailableProviders(String countryCode) {
        return Arrays.stream(MobileMoneyProvider.values())
            .filter(p -> p.operatesInCountry(countryCode))
            .collect(Collectors.toList());
    }
    
    public Map<String, Object> getSystemStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalAccounts", accounts.size());
        stats.put("totalTransactions", totalTransactions.get());
        stats.put("successfulTransactions", successfulTransactions.get());
        stats.put("failedTransactions", failedTransactions.get());
        stats.put("activeTransactions", transactions.size());
        
        // Success rate
        long total = totalTransactions.get();
        double successRate = total > 0 ? 
            (double) successfulTransactions.get() / total : 0.0;
        stats.put("successRate", successRate);
        
        // Provider volumes
        Map<String, Long> volumes = new HashMap<>();
        for (Map.Entry<MobileMoneyProvider, AtomicLong> entry : providerVolumes.entrySet()) {
            volumes.put(entry.getKey().getDisplayName(), entry.getValue().get());
        }
        stats.put("providerVolumes", volumes);
        
        return stats;
    }
    
    /**
     * Shutdown method
     */
    public void shutdown() {
        try {
            transactionExecutor.shutdown();
            monitorExecutor.shutdown();
            
            if (!transactionExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                transactionExecutor.shutdownNow();
            }
            if (!monitorExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                monitorExecutor.shutdownNow();
            }
            
            LOGGER.info("MobileMoneyGateway shutdown completed");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Shutdown interrupted");
        }
    }
}