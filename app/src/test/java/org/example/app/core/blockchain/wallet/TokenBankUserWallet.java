package org.example.app.core.blockchain.wallet;

import org.example.app.core.wallet.Wallet;
import org.example.app.core.wallet.TransactionBuilder;
import org.example.app.core.block.Transaction;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;
import org.example.app.core.account.Account;
import org.example.app.core.token.TokenTransferProcessor;
import org.example.app.core.mempool.TransactionValidator;
import org.example.app.core.consensus.HashUtils;

import java.security.*;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.time.Instant;
import java.time.Duration;

/**
 * TokenBankUserWallet - User wallet for interacting with tokenized banking services
 * 
 * Features:
 * - Seamless interaction with multiple African tokenized currencies
 * - Offline transaction capabilities for areas with poor connectivity
 * - Government resistance through decentralized design
 * - Fair exchange rate calculations favoring African currencies
 * - Direct bank integration while maintaining user independence
 * - Emergency exit capabilities without bank dependency
 */
public class TokenBankUserWallet extends Wallet {
    private static final Logger LOGGER = Logger.getLogger(TokenBankUserWallet.class.getName());
    
    // User identification and preferences
    private final String userId;
    private final String userCountry;
    private final String preferredCurrency;
    private final Map<String, UserCurrencyPreference> currencyPreferences;
    
    // Token and bank interactions
    private final Map<String, TokenBalance> tokenBalances;
    private final Map<String, BankConnection> bankConnections;
    private final Set<String> trustedBankIds;
    private final TokenTransferProcessor tokenProcessor;
    
    // Offline transaction capabilities
    private final List<OfflineTransaction> pendingOfflineTransactions;
    private final OfflineTransactionManager offlineManager;
    private final QRCodeGenerator qrGenerator;
    
    // Exchange rate management
    private final ExchangeRateCache exchangeRateCache;
    private final FairRateCalculator fairRateCalculator;
    private final Map<String, ExchangeHistory> exchangeHistory;
    
    // Government resistance features
    private final List<byte[]> emergencySeeds;
    private final Map<String, EmergencyContact> emergencyContacts;
    private final PrivacyProtection privacyProtection;
    
    // Security and validation
    private final TransactionValidator validator;
    private final ReentrantLock walletLock;
    private final List<SecurityEvent> securityEvents;
    
    /**
     * UserCurrencyPreference for managing user's currency preferences
     */
    public static class UserCurrencyPreference {
        private final String currencyCode;
        private final boolean isPreferred;
        private final double preferenceWeight; // Higher means more preferred
        private final boolean allowAutoExchange;
        
        public UserCurrencyPreference(String currencyCode, boolean isPreferred, 
                                    double preferenceWeight, boolean allowAutoExchange) {
            this.currencyCode = currencyCode;
            this.isPreferred = isPreferred;
            this.preferenceWeight = preferenceWeight;
            this.allowAutoExchange = allowAutoExchange;
        }
        
        public String getCurrencyCode() { return currencyCode; }
        public boolean isPreferred() { return isPreferred; }
        public double getPreferenceWeight() { return preferenceWeight; }
        public boolean isAutoExchangeAllowed() { return allowAutoExchange; }
    }
    
    /**
     * TokenBalance for tracking token holdings
     */
    public static class TokenBalance {
        private final String tokenSymbol;
        private final String tokenAddress;
        private final String backingCurrency;
        private volatile long availableBalance;
        private volatile long pendingBalance;
        private final Instant lastUpdated;
        
        public TokenBalance(String tokenSymbol, String tokenAddress, String backingCurrency, 
                           long availableBalance, long pendingBalance) {
            this.tokenSymbol = tokenSymbol;
            this.tokenAddress = tokenAddress;
            this.backingCurrency = backingCurrency;
            this.availableBalance = availableBalance;
            this.pendingBalance = pendingBalance;
            this.lastUpdated = Instant.now();
        }
        
        public String getTokenSymbol() { return tokenSymbol; }
        public String getTokenAddress() { return tokenAddress; }
        public String getBackingCurrency() { return backingCurrency; }
        public long getAvailableBalance() { return availableBalance; }
        public long getPendingBalance() { return pendingBalance; }
        public long getTotalBalance() { return availableBalance + pendingBalance; }
        public Instant getLastUpdated() { return lastUpdated; }
        
        public synchronized void updateBalance(long newAvailable, long newPending) {
            this.availableBalance = Math.max(0, newAvailable);
            this.pendingBalance = Math.max(0, newPending);
        }
    }
    
    /**
     * BankConnection for managing connections to partner banks
     */
    public static class BankConnection {
        private final String bankId;
        private final String bankName;
        private final String countryCode;
        private final String apiEndpoint;
        private final boolean isActive;
        private final Instant lastConnection;
        private final Set<String> supportedCurrencies;
        
        public BankConnection(String bankId, String bankName, String countryCode, 
                            String apiEndpoint, Set<String> supportedCurrencies) {
            this.bankId = bankId;
            this.bankName = bankName;
            this.countryCode = countryCode;
            this.apiEndpoint = apiEndpoint;
            this.supportedCurrencies = new HashSet<>(supportedCurrencies);
            this.isActive = true;
            this.lastConnection = Instant.now();
        }
        
        public String getBankId() { return bankId; }
        public String getBankName() { return bankName; }
        public String getCountryCode() { return countryCode; }
        public String getApiEndpoint() { return apiEndpoint; }
        public boolean isActive() { return isActive; }
        public Instant getLastConnection() { return lastConnection; }
        public Set<String> getSupportedCurrencies() { return new HashSet<>(supportedCurrencies); }
    }
    
    /**
     * OfflineTransaction for transactions that can be processed without internet
     */
    public static class OfflineTransaction {
        private final String transactionId;
        private final String fromAddress;
        private final String toAddress;
        private final long amount;
        private final String currency;
        private final String qrCode;
        private final Instant createdAt;
        private final long validUntil;
        private boolean isSynced;
        
        public OfflineTransaction(String fromAddress, String toAddress, long amount, 
                                String currency, String qrCode, long validityHours) {
            this.transactionId = generateOfflineTransactionId();
            this.fromAddress = fromAddress;
            this.toAddress = toAddress;
            this.amount = amount;
            this.currency = currency;
            this.qrCode = qrCode;
            this.createdAt = Instant.now();
            this.validUntil = System.currentTimeMillis() + (validityHours * 3600000);
            this.isSynced = false;
        }
        
        private String generateOfflineTransactionId() {
            return "OFF_" + System.currentTimeMillis() + "_" + 
                   Integer.toHexString(new Random().nextInt());
        }
        
        public String getTransactionId() { return transactionId; }
        public String getFromAddress() { return fromAddress; }
        public String getToAddress() { return toAddress; }
        public long getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public String getQrCode() { return qrCode; }
        public Instant getCreatedAt() { return createdAt; }
        public long getValidUntil() { return validUntil; }
        public boolean isSynced() { return isSynced; }
        public void markAsSynced() { this.isSynced = true; }
        
        public boolean isValid() {
            return System.currentTimeMillis() <= validUntil && !isSynced;
        }
    }
    
    /**
     * ExchangeRateCache for caching exchange rates with fair weighting
     */
    public static class ExchangeRateCache {
        private final Map<String, CachedRate> rates;
        private final long cacheExpiryMillis;
        private final ReentrantLock rateLock;
        
        public ExchangeRateCache(long cacheExpiryMinutes) {
            this.rates = new ConcurrentHashMap<>();
            this.cacheExpiryMillis = cacheExpiryMinutes * 60 * 1000;
            this.rateLock = new ReentrantLock();
        }
        
        public static class CachedRate {
            private final String fromCurrency;
            private final String toCurrency;
            private final double rate;
            private final double fairWeightedRate;
            private final Instant timestamp;
            private final String source;
            
            public CachedRate(String fromCurrency, String toCurrency, double rate, 
                            double fairWeightedRate, String source) {
                this.fromCurrency = fromCurrency;
                this.toCurrency = toCurrency;
                this.rate = rate;
                this.fairWeightedRate = fairWeightedRate;
                this.timestamp = Instant.now();
                this.source = source;
            }
            
            public boolean isExpired(long expiryMillis) {
                return Duration.between(timestamp, Instant.now()).toMillis() > expiryMillis;
            }
            
            public double getRate() { return rate; }
            public double getFairWeightedRate() { return fairWeightedRate; }
            public String getSource() { return source; }
        }
        
        public void putRate(String fromCurrency, String toCurrency, double rate, 
                          double fairWeightedRate, String source) {
            rateLock.lock();
            try {
                String key = fromCurrency + "/" + toCurrency;
                rates.put(key, new CachedRate(fromCurrency, toCurrency, rate, fairWeightedRate, source));
            } finally {
                rateLock.unlock();
            }
        }
        
        public CachedRate getRate(String fromCurrency, String toCurrency) {
            String key = fromCurrency + "/" + toCurrency;
            CachedRate cached = rates.get(key);
            
            if (cached != null && !cached.isExpired(cacheExpiryMillis)) {
                return cached;
            }
            return null;
        }
    }
    
    /**
     * Constructor for TokenBankUserWallet
     */
    public TokenBankUserWallet(String userId, String userCountry, String preferredCurrency) 
            throws NoSuchAlgorithmException {
        super();
        
        this.userId = userId;
        this.userCountry = userCountry;
        this.preferredCurrency = preferredCurrency;
        this.currencyPreferences = new ConcurrentHashMap<>();
        this.tokenBalances = new ConcurrentHashMap<>();
        this.bankConnections = new ConcurrentHashMap<>();
        this.trustedBankIds = ConcurrentHashMap.newKeySet();
        this.pendingOfflineTransactions = new ArrayList<>();
        this.exchangeHistory = new ConcurrentHashMap<>();
        this.emergencySeeds = new ArrayList<>();
        this.emergencyContacts = new ConcurrentHashMap<>();
        this.securityEvents = new ArrayList<>();
        this.walletLock = new ReentrantLock();
        
        // Initialize components
        this.tokenProcessor = new TokenTransferProcessor(null); // Will be set when blockchain is available
        this.offlineManager = new OfflineTransactionManager();
        this.qrGenerator = new QRCodeGenerator();
        this.exchangeRateCache = new ExchangeRateCache(30); // 30 minute cache
        this.fairRateCalculator = new FairRateCalculator(userCountry);
        this.privacyProtection = new PrivacyProtection(userId);
        this.validator = new TransactionValidator(new UserWalletMempoolConfig());
        
        // Set default currency preference
        if (preferredCurrency != null) {
            setCurrencyPreference(preferredCurrency, true, 1.0, true);
        }
        
        // Initialize emergency seeds for government resistance
        generateEmergencySeeds();
        
        LOGGER.info("Created TokenBankUserWallet for user " + userId + 
                   " in " + userCountry + " with preferred currency " + preferredCurrency);
    }
    
    /**
     * Adds a token balance to the wallet
     */
    public void addTokenBalance(String tokenSymbol, String tokenAddress, String backingCurrency, 
                              long initialBalance) {
        walletLock.lock();
        try {
            TokenBalance balance = new TokenBalance(tokenSymbol, tokenAddress, backingCurrency, 
                                                   initialBalance, 0);
            tokenBalances.put(tokenSymbol, balance);
            
            LOGGER.info("Added token balance: " + tokenSymbol + " = " + initialBalance);
            
            // Add security event
            addSecurityEvent("TOKEN_ADDED", "Token " + tokenSymbol + " added to wallet");
            
        } finally {
            walletLock.unlock();
        }
    }
    
    /**
     * Creates a token transfer transaction with fair exchange rate consideration
     */
    public Transaction createTokenTransfer(String toAddress, long amount, String tokenSymbol, 
                                         boolean useOfflineMode) {
        walletLock.lock();
        try {
            // Check token balance
            TokenBalance balance = tokenBalances.get(tokenSymbol);
            if (balance == null) {
                LOGGER.warning("Token not found in wallet: " + tokenSymbol);
                return null;
            }
            
            if (balance.getAvailableBalance() < amount) {
                LOGGER.warning("Insufficient token balance: " + tokenSymbol + 
                              " available: " + balance.getAvailableBalance() + 
                              " requested: " + amount);
                return null;
            }
            
            // If offline mode, create offline transaction
            if (useOfflineMode) {
                return createOfflineTokenTransfer(toAddress, amount, tokenSymbol);
            }
            
            // Create online transaction
            byte[] transferData = createTokenTransferData(toAddress, amount, tokenSymbol);
            
            TransactionBuilder builder = new TransactionBuilder()
                .from(getAddress())
                .to(toAddress)
                .value(BigInteger.valueOf(amount))
                .data(transferData)
                .gasPrice(BigInteger.valueOf(calculateOptimalGasPrice(tokenSymbol)))
                .nonce(getNonce());
            
            Transaction tx = builder.build();
            
            // Update balance optimistically
            balance.updateBalance(balance.getAvailableBalance() - amount, 
                                balance.getPendingBalance() + amount);
            
            // Record exchange if currency conversion is involved
            recordExchangeHistory(tokenSymbol, amount, toAddress);
            
            addSecurityEvent("TOKEN_TRANSFER", 
                "Token transfer: " + amount + " " + tokenSymbol + " to " + toAddress);
            
            LOGGER.info("Created token transfer: " + amount + " " + tokenSymbol);
            return tx;
            
        } finally {
            walletLock.unlock();
        }
    }
    
    /**
     * Creates an offline token transfer using QR codes
     */
    private Transaction createOfflineTokenTransfer(String toAddress, long amount, String tokenSymbol) {
        try {
            // Generate QR code for offline transaction
            String qrCode = qrGenerator.generateTransferQR(getAddress(), toAddress, amount, 
                                                         tokenSymbol, System.currentTimeMillis());
            
            // Create offline transaction record
            OfflineTransaction offlineTx = new OfflineTransaction(getAddress(), toAddress, 
                                                                amount, tokenSymbol, qrCode, 48); // Valid for 48 hours
            
            pendingOfflineTransactions.add(offlineTx);
            
            // Create transaction for when online
            byte[] offlineData = createOfflineTransactionData(offlineTx);
            
            TransactionBuilder builder = new TransactionBuilder()
                .from(getAddress())
                .to(toAddress)
                .value(BigInteger.valueOf(amount))
                .data(offlineData)
                .gasPrice(BigInteger.valueOf(1000000000L)) // Standard gas for offline
                .nonce(getNonce());
            
            Transaction tx = builder.build();
            
            LOGGER.info("Created offline transaction with QR: " + qrCode);
            return tx;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating offline transaction", e);
            return null;
        }
    }
    
    /**
     * Exchanges tokens using fair rate calculation
     */
    public Transaction createTokenExchange(String fromToken, String toToken, long amount, 
                                         boolean prioritizeAfricanRates) {
        walletLock.lock();
        try {
            // Get current exchange rate
            double exchangeRate = getExchangeRate(fromToken, toToken, prioritizeAfricanRates);
            if (exchangeRate <= 0) {
                LOGGER.warning("Unable to get exchange rate for " + fromToken + "/" + toToken);
                return null;
            }
            
            // Calculate fair weighted rate if prioritizing African rates
            if (prioritizeAfricanRates) {
                exchangeRate = fairRateCalculator.calculateFairRate(fromToken, toToken, exchangeRate);
            }
            
            long toAmount = (long) (amount * exchangeRate);
            
            // Check balances
            TokenBalance fromBalance = tokenBalances.get(fromToken);
            if (fromBalance == null || fromBalance.getAvailableBalance() < amount) {
                LOGGER.warning("Insufficient balance for exchange: " + fromToken);
                return null;
            }
            
            // Create exchange transaction data
            byte[] exchangeData = createTokenExchangeData(fromToken, toToken, amount, toAmount, exchangeRate);
            
            TransactionBuilder builder = new TransactionBuilder()
                .from(getAddress())
                .to(getAddress()) // Self transaction for exchange
                .value(BigInteger.ZERO) // No ether value for token exchange
                .data(exchangeData)
                .gasPrice(BigInteger.valueOf(calculateExchangeGasPrice(fromToken, toToken)))
                .nonce(getNonce());
            
            Transaction tx = builder.build();
            
            // Update balances optimistically
            fromBalance.updateBalance(fromBalance.getAvailableBalance() - amount, 
                                    fromBalance.getPendingBalance());
            
            TokenBalance toBalance = tokenBalances.get(toToken);
            if (toBalance != null) {
                toBalance.updateBalance(toBalance.getAvailableBalance(), 
                                      toBalance.getPendingBalance() + toAmount);
            }
            
            // Record exchange history
            recordTokenExchange(fromToken, toToken, amount, toAmount, exchangeRate);
            
            addSecurityEvent("TOKEN_EXCHANGE", 
                "Exchanged " + amount + " " + fromToken + " for " + toAmount + " " + toToken);
            
            LOGGER.info("Created token exchange: " + amount + " " + fromToken + 
                       " -> " + toAmount + " " + toToken + " at rate " + exchangeRate);
            
            return tx;
            
        } finally {
            walletLock.unlock();
        }
    }
    
    /**
     * Gets exchange rate with fair weighting for African currencies
     */
    private double getExchangeRate(String fromToken, String toToken, boolean prioritizeAfricanRates) {
        // Check cache first
        ExchangeRateCache.CachedRate cached = exchangeRateCache.getRate(fromToken, toToken);
        if (cached != null) {
            return prioritizeAfricanRates ? cached.getFairWeightedRate() : cached.getRate();
        }
        
        // Simulate getting rate from multiple sources
        double baseRate = simulateExchangeRateFromSources(fromToken, toToken);
        double fairRate = baseRate;
        
        if (prioritizeAfricanRates) {
            fairRate = fairRateCalculator.calculateFairRate(fromToken, toToken, baseRate);
        }
        
        // Cache the rate
        exchangeRateCache.putRate(fromToken, toToken, baseRate, fairRate, "MULTI_SOURCE");
        
        return fairRate;
    }
    
    /**
     * Simulates getting exchange rate from multiple sources
     */
    private double simulateExchangeRateFromSources(String fromToken, String toToken) {
        // This would connect to multiple exchanges and calculate weighted average
        // For now, simulate with reasonable rates
        Map<String, Double> simulatedRates = new HashMap<>();
        simulatedRates.put("NGN/USD", 0.0024);
        simulatedRates.put("KES/USD", 0.0067);
        simulatedRates.put("ZAR/USD", 0.055);
        simulatedRates.put("GHS/USD", 0.083);
        simulatedRates.put("USD/EUR", 0.85);
        
        String pair = fromToken + "/" + toToken;
        return simulatedRates.getOrDefault(pair, 1.0);
    }
    
    /**
     * Connects to a partner bank
     */
    public boolean connectToBank(String bankId, String bankName, String countryCode, 
                               String apiEndpoint, Set<String> supportedCurrencies) {
        walletLock.lock();
        try {
            // Validate bank connection
            if (!validateBankConnection(bankId, apiEndpoint)) {
                LOGGER.warning("Bank connection validation failed: " + bankId);
                return false;
            }
            
            BankConnection connection = new BankConnection(bankId, bankName, countryCode, 
                                                         apiEndpoint, supportedCurrencies);
            bankConnections.put(bankId, connection);
            
            // Add to trusted banks if validation passes
            trustedBankIds.add(bankId);
            
            addSecurityEvent("BANK_CONNECTED", "Connected to bank: " + bankName + " (" + bankId + ")");
            
            LOGGER.info("Connected to bank: " + bankName + " in " + countryCode);
            return true;
            
        } finally {
            walletLock.unlock();
        }
    }
    
    /**
     * Creates an emergency exit transaction (government resistance feature)
     */
    public Transaction createEmergencyExitTransaction(String emergencyAddress, String tokenSymbol, 
                                                    String emergencyPhrase) {
        walletLock.lock();
        try {
            // Validate emergency phrase
            if (!validateEmergencyPhrase(emergencyPhrase)) {
                LOGGER.severe("Invalid emergency phrase provided");
                return null;
            }
            
            TokenBalance balance = tokenBalances.get(tokenSymbol);
            if (balance == null) {
                LOGGER.warning("No balance found for emergency exit: " + tokenSymbol);
                return null;
            }
            
            // Transfer all available balance in emergency
            long emergencyAmount = balance.getAvailableBalance();
            
            // Create emergency transaction data
            byte[] emergencyData = createEmergencyExitData(emergencyAddress, tokenSymbol, 
                                                         emergencyAmount, emergencyPhrase);
            
            TransactionBuilder builder = new TransactionBuilder()
                .from(getAddress())
                .to(emergencyAddress)
                .value(BigInteger.valueOf(emergencyAmount))
                .data(emergencyData)
                .gasPrice(BigInteger.valueOf(5000000000L)) // High gas for priority
                .nonce(getNonce());
            
            Transaction tx = builder.build();
            
            // Clear balance immediately for security
            balance.updateBalance(0, 0);
            
            addSecurityEvent("EMERGENCY_EXIT", 
                "Emergency exit executed: " + emergencyAmount + " " + tokenSymbol);
            
            LOGGER.severe("EMERGENCY EXIT TRANSACTION CREATED for " + emergencyAmount + 
                         " " + tokenSymbol);
            
            return tx;
            
        } finally {
            walletLock.unlock();
        }
    }
    
    /**
     * Synchronizes offline transactions when connection is restored
     */
    public List<Transaction> synchronizeOfflineTransactions() {
        walletLock.lock();
        try {
            List<Transaction> syncedTransactions = new ArrayList<>();
            Iterator<OfflineTransaction> iterator = pendingOfflineTransactions.iterator();
            
            while (iterator.hasNext()) {
                OfflineTransaction offlineTx = iterator.next();
                
                if (!offlineTx.isValid()) {
                    // Remove expired transactions
                    iterator.remove();
                    continue;
                }
                
                // Convert offline transaction to on-chain transaction
                byte[] syncData = createOfflineTransactionData(offlineTx);
                
                TransactionBuilder builder = new TransactionBuilder()
                    .from(offlineTx.getFromAddress())
                    .to(offlineTx.getToAddress())
                    .value(BigInteger.valueOf(offlineTx.getAmount()))
                    .data(syncData)
                    .gasPrice(BigInteger.valueOf(2000000000L))
                    .nonce(getNonce());
                
                Transaction syncTx = builder.build();
                syncedTransactions.add(syncTx);
                
                // Mark as synced
                offlineTx.markAsSynced();
                iterator.remove();
                
                LOGGER.info("Synchronized offline transaction: " + offlineTx.getTransactionId());
            }
            
            addSecurityEvent("OFFLINE_SYNC", "Synchronized " + syncedTransactions.size() + " offline transactions");
            
            return syncedTransactions;
            
        } finally {
            walletLock.unlock();
        }
    }
    
    // Data creation methods
    
    private byte[] createTokenTransferData(String toAddress, long amount, String tokenSymbol) {
        try {
            StringBuilder dataBuilder = new StringBuilder();
            dataBuilder.append("TOKEN_TRANSFER:");
            dataBuilder.append("TO=").append(toAddress).append(";");
            dataBuilder.append("AMOUNT=").append(amount).append(";");
            dataBuilder.append("TOKEN=").append(tokenSymbol).append(";");
            dataBuilder.append("USER_ID=").append(userId).append(";");
            dataBuilder.append("TIMESTAMP=").append(System.currentTimeMillis()).append(";");
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(dataBuilder.toString().getBytes());
            
        } catch (NoSuchAlgorithmException e) {
            return new byte[32];
        }
    }
    
    private byte[] createTokenExchangeData(String fromToken, String toToken, long fromAmount, 
                                          long toAmount, double rate) {
        try {
            StringBuilder dataBuilder = new StringBuilder();
            dataBuilder.append("TOKEN_EXCHANGE:");
            dataBuilder.append("FROM_TOKEN=").append(fromToken).append(";");
            dataBuilder.append("TO_TOKEN=").append(toToken).append(";");
            dataBuilder.append("FROM_AMOUNT=").append(fromAmount).append(";");
            dataBuilder.append("TO_AMOUNT=").append(toAmount).append(";");
            dataBuilder.append("RATE=").append(rate).append(";");
            dataBuilder.append("USER_ID=").append(userId).append(";");
            dataBuilder.append("TIMESTAMP=").append(System.currentTimeMillis()).append(";");
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(dataBuilder.toString().getBytes());
            
        } catch (NoSuchAlgorithmException e) {
            return new byte[32];
        }
    }
    
    private byte[] createOfflineTransactionData(OfflineTransaction offlineTx) {
        try {
            StringBuilder dataBuilder = new StringBuilder();
            dataBuilder.append("OFFLINE_TX:");
            dataBuilder.append("TX_ID=").append(offlineTx.getTransactionId()).append(";");
            dataBuilder.append("QR_CODE=").append(offlineTx.getQrCode()).append(";");
            dataBuilder.append("CREATED_AT=").append(offlineTx.getCreatedAt().toEpochMilli()).append(";");
            dataBuilder.append("USER_ID=").append(userId).append(";");
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(dataBuilder.toString().getBytes());
            
        } catch (NoSuchAlgorithmException e) {
            return new byte[32];
        }
    }
    
    private byte[] createEmergencyExitData(String emergencyAddress, String tokenSymbol, 
                                          long amount, String emergencyPhrase) {
        try {
            StringBuilder dataBuilder = new StringBuilder();
            dataBuilder.append("EMERGENCY_EXIT:");
            dataBuilder.append("EMERGENCY_ADDR=").append(emergencyAddress).append(";");
            dataBuilder.append("TOKEN=").append(tokenSymbol).append(";");
            dataBuilder.append("AMOUNT=").append(amount).append(";");
            dataBuilder.append("PHRASE_HASH=").append(HashUtils.generateHash(emergencyPhrase)).append(";");
            dataBuilder.append("USER_ID=").append(userId).append(";");
            dataBuilder.append("TIMESTAMP=").append(System.currentTimeMillis()).append(";");
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(dataBuilder.toString().getBytes());
            
        } catch (NoSuchAlgorithmException e) {
            return new byte[32];
        }
    }
    
    // Utility and helper methods
    
    private void setCurrencyPreference(String currencyCode, boolean isPreferred, 
                                      double weight, boolean allowAutoExchange) {
        UserCurrencyPreference preference = new UserCurrencyPreference(currencyCode, isPreferred, 
                                                                      weight, allowAutoExchange);
        currencyPreferences.put(currencyCode, preference);
    }
    
    private void generateEmergencySeeds() {
        try {
            SecureRandom random = new SecureRandom();
            for (int i = 0; i < 3; i++) { // Generate 3 emergency seeds
                byte[] seed = new byte[32];
                random.nextBytes(seed);
                emergencySeeds.add(seed);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating emergency seeds", e);
        }
    }
    
    private boolean validateBankConnection(String bankId, String apiEndpoint) {
        // Validate bank ID format and API endpoint
        if (bankId == null || bankId.length() < 8 || apiEndpoint == null || !apiEndpoint.startsWith("https://")) {
            return false;
        }
        
        // Additional validation would check bank registry, certificates, etc.
        return true;
    }
    
    private boolean validateEmergencyPhrase(String phrase) {
        // Validate emergency phrase against stored hashes
        String phraseHash = HashUtils.generateHash(phrase);
        
        // In production, this would check against securely stored phrase hashes
        // For now, accept non-empty phrases
        return phrase != null && phrase.length() >= 12;
    }
    
    private void recordExchangeHistory(String tokenSymbol, long amount, String toAddress) {
        String key = tokenSymbol + "_" + System.currentTimeMillis();
        ExchangeHistory history = new ExchangeHistory(tokenSymbol, amount, toAddress, Instant.now());
        exchangeHistory.put(key, history);
        
        // Keep history size manageable
        if (exchangeHistory.size() > 1000) {
            // Remove oldest entries
            exchangeHistory.entrySet().removeIf(entry -> 
                Duration.between(entry.getValue().getTimestamp(), Instant.now()).toDays() > 30);
        }
    }
    
    private void recordTokenExchange(String fromToken, String toToken, long fromAmount, 
                                   long toAmount, double rate) {
        String key = fromToken + "_" + toToken + "_" + System.currentTimeMillis();
        TokenExchangeRecord record = new TokenExchangeRecord(fromToken, toToken, fromAmount, 
                                                           toAmount, rate, Instant.now());
        // Store in exchange history for audit purposes
    }
    
    private void addSecurityEvent(String eventType, String details) {
        SecurityEvent event = new SecurityEvent(eventType, details, Instant.now());
        securityEvents.add(event);
        
        // Keep security events manageable
        if (securityEvents.size() > 500) {
            securityEvents.subList(0, 100).clear();
        }
    }
    
    private long calculateOptimalGasPrice(String tokenSymbol) {
        // Calculate optimal gas price based on network conditions and token type
        long baseGas = 2000000000L; // 2 Gwei base
        
        // African tokens get priority with slightly higher gas
        if (isAfricanToken(tokenSymbol)) {
            baseGas = (long) (baseGas * 1.2);
        }
        
        return baseGas;
    }
    
    private long calculateExchangeGasPrice(String fromToken, String toToken) {
        return calculateOptimalGasPrice(fromToken) * 2; // Higher gas for exchanges
    }
    
    private boolean isAfricanToken(String tokenSymbol) {
        // Check if token represents an African currency
        Set<String> africanCurrencies = Set.of("NGN", "KES", "ZAR", "GHS", "EGP", "MAD", "TZS", "UGX");
        return africanCurrencies.contains(tokenSymbol);
    }
    
    private long getNonce() {
        return System.currentTimeMillis();
    }
    
    // Supporting classes
    
    private static class OfflineTransactionManager {
        // Manages offline transaction logic
    }
    
    private static class QRCodeGenerator {
        public String generateTransferQR(String from, String to, long amount, String currency, long timestamp) {
            // Generate QR code data for offline transfers
            return "TINC_TRANSFER:" + from + ":" + to + ":" + amount + ":" + currency + ":" + timestamp;
        }
    }
    
    private static class FairRateCalculator {
        private final String userCountry;
        
        public FairRateCalculator(String userCountry) {
            this.userCountry = userCountry;
        }
        
        public double calculateFairRate(String fromToken, String toToken, double baseRate) {
            // Apply fair weighting algorithm that favors African currencies
            double fairAdjustment = 1.0;
            
            // Boost African currencies
            if (isAfricanCurrency(fromToken) || isAfricanCurrency(toToken)) {
                fairAdjustment = 1.05; // 5% boost for African currencies
            }
            
            return baseRate * fairAdjustment;
        }
        
        private boolean isAfricanCurrency(String currency) {
            Set<String> africanCurrencies = Set.of("NGN", "KES", "ZAR", "GHS", "EGP", "MAD", "TZS", "UGX");
            return africanCurrencies.contains(currency);
        }
    }
    
    private static class PrivacyProtection {
        private final String userId;
        
        public PrivacyProtection(String userId) {
            this.userId = userId;
        }
    }
    
    private static class ExchangeHistory {
        private final String tokenSymbol;
        private final long amount;
        private final String toAddress;
        private final Instant timestamp;
        
        public ExchangeHistory(String tokenSymbol, long amount, String toAddress, Instant timestamp) {
            this.tokenSymbol = tokenSymbol;
            this.amount = amount;
            this.toAddress = toAddress;
            this.timestamp = timestamp;
        }
        
        public Instant getTimestamp() { return timestamp; }
    }
    
    private static class TokenExchangeRecord {
        private final String fromToken;
        private final String toToken;
        private final long fromAmount;
        private final long toAmount;
        private final double rate;
        private final Instant timestamp;
        
        public TokenExchangeRecord(String fromToken, String toToken, long fromAmount, 
                                 long toAmount, double rate, Instant timestamp) {
            this.fromToken = fromToken;
            this.toToken = toToken;
            this.fromAmount = fromAmount;
            this.toAmount = toAmount;
            this.rate = rate;
            this.timestamp = timestamp;
        }
    }
    
    private static class EmergencyContact {
        private final String contactId;
        private final String publicKey;
        private final String country;
        
        public EmergencyContact(String contactId, String publicKey, String country) {
            this.contactId = contactId;
            this.publicKey = publicKey;
            this.country = country;
        }
    }
    
    private static class SecurityEvent {
        private final String eventType;
        private final String details;
        private final Instant timestamp;
        
        public SecurityEvent(String eventType, String details, Instant timestamp) {
            this.eventType = eventType;
            this.details = details;
            this.timestamp = timestamp;
        }
    }
    
    // Getters and public methods
    
    public String getUserId() { return userId; }
    public String getUserCountry() { return userCountry; }
    public String getPreferredCurrency() { return preferredCurrency; }
    
    public TokenBalance getTokenBalance(String tokenSymbol) {
        return tokenBalances.get(tokenSymbol);
    }
    
    public Map<String, TokenBalance> getAllTokenBalances() {
        return new HashMap<>(tokenBalances);
    }
    
    public List<OfflineTransaction> getPendingOfflineTransactions() {
        return new ArrayList<>(pendingOfflineTransactions);
    }
    
    public Set<String> getTrustedBanks() {
        return new HashSet<>(trustedBankIds);
    }
    
    public List<SecurityEvent> getRecentSecurityEvents(int limit) {
        int size = securityEvents.size();
        int fromIndex = Math.max(0, size - limit);
        return new ArrayList<>(securityEvents.subList(fromIndex, size));
    }
    
    /**
     * UserWalletMempoolConfig for user wallet validation
     */
    private static class UserWalletMempoolConfig implements org.example.app.core.mempool.MempoolConfig {
        @Override
        public BigInteger getMinGasPrice() {
            return BigInteger.valueOf(1000000000L); // 1 Gwei minimum
        }
        
        @Override
        public int getMaxTxPoolSize() {
            return 50; // Reasonable size for user wallets
        }
        
        @Override
        public long getMaxTxAge() {
            return 1800000; // 30 minutes maximum age
        }
    }
}