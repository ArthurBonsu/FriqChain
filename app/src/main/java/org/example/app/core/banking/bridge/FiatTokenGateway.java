package org.example.app.core.banking.bridge;

import org.example.app.core.blockchain.transaction.BankRegistrationTransaction;
import org.example.app.core.crypto.Certificate;
import org.example.app.core.crypto.PKIManager;
import org.example.app.core.state.StateManager;
import org.example.app.core.state.BlockchainState;
import org.example.app.core.mempool.TransactionValidator;
import org.example.app.core.p2p.PeerNetwork;
import org.example.app.core.internal.EventLog;
import org.example.app.core.config.BlockchainConfig;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * FiatTokenGateway - Core gateway for African currency tokenization
 * 
 * This gateway implements the fair-weighting algorithm for African currencies,
 * handles fiat-to-token conversions, and maintains government-resistant pricing
 * through multiple exchange sources and anti-manipulation controls.
 */
public class FiatTokenGateway {
    private static final Logger LOGGER = Logger.getLogger(FiatTokenGateway.class.getName());
    
    // Fair weighting algorithm constants (African-focused)
    private static final double VOLUME_WEIGHT = 0.40;     // 40% - Trading volume
    private static final double LIQUIDITY_WEIGHT = 0.25;  // 25% - Market liquidity
    private static final double STABILITY_WEIGHT = 0.20;  // 20% - Price stability
    private static final double ADOPTION_WEIGHT = 0.10;   // 10% - Local adoption
    private static final double EXCHANGE_WEIGHT = 0.05;   // 5% - Exchange diversity
    
    // Anti-manipulation settings
    private static final int MIN_EXCHANGE_SOURCES = 7;
    private static final double MAX_PRICE_DEVIATION = 0.15; // 15% max deviation from median
    private static final int PRICE_HISTORY_HOURS = 24;
    private static final double OUTLIER_THRESHOLD = 2.0; // MAD threshold for outlier detection
    
    // Gateway operation modes
    public enum GatewayMode {
        CONVERSION_ONLY,    // Only token conversions
        FULL_BANKING,       // Full banking integration
        EMERGENCY_MODE,     // Crisis response mode
        MAINTENANCE        // Maintenance mode
    }
    
    // Conversion types
    public enum ConversionType {
        FIAT_TO_TOKEN,     // Fiat currency to African token
        TOKEN_TO_FIAT,     // African token to fiat currency
        TOKEN_TO_TOKEN,    // African token to African token
        CROSS_BORDER      // Cross-border currency exchange
    }
    
    /**
     * Represents an African currency and its tokenized version
     */
    public static class AfricanCurrency {
        private final String currencyCode;        // ISO code (NGN, ZAR, KES, etc.)
        private final String countryCode;         // Country ISO code
        private final String tokenSymbol;         // Token symbol (TNGN, TZAR, TKES, etc.)
        private final Address tokenContract;      // Token contract address
        private final int decimals;              // Token decimal places
        private final BigDecimal reserveRatio;   // Reserve backing ratio
        private final boolean isActive;          // Active status
        private final Date activationDate;      // When tokenization started
        
        public AfricanCurrency(String currencyCode, String countryCode, String tokenSymbol,
                             Address tokenContract, int decimals, BigDecimal reserveRatio) {
            this.currencyCode = currencyCode;
            this.countryCode = countryCode;
            this.tokenSymbol = tokenSymbol;
            this.tokenContract = tokenContract;
            this.decimals = decimals;
            this.reserveRatio = reserveRatio;
            this.isActive = true;
            this.activationDate = new Date();
        }
        
        // Getters
        public String getCurrencyCode() { return currencyCode; }
        public String getCountryCode() { return countryCode; }
        public String getTokenSymbol() { return tokenSymbol; }
        public Address getTokenContract() { return tokenContract; }
        public int getDecimals() { return decimals; }
        public BigDecimal getReserveRatio() { return reserveRatio; }
        public boolean isActive() { return isActive; }
        public Date getActivationDate() { return activationDate; }
    }
    
    /**
     * Represents exchange rate data from multiple sources
     */
    public static class ExchangeRateData {
        private final String source;              // Exchange source name
        private final String fromCurrency;       // Source currency
        private final String toCurrency;         // Target currency
        private final BigDecimal rate;           // Exchange rate
        private final BigDecimal volume24h;      // 24h trading volume
        private final BigDecimal liquidity;      // Market liquidity
        private final Date timestamp;            // Rate timestamp
        private final double confidenceScore;    // Reliability score (0-1)
        
        public ExchangeRateData(String source, String fromCurrency, String toCurrency,
                              BigDecimal rate, BigDecimal volume24h, BigDecimal liquidity,
                              double confidenceScore) {
            this.source = source;
            this.fromCurrency = fromCurrency;
            this.toCurrency = toCurrency;
            this.rate = rate;
            this.volume24h = volume24h;
            this.liquidity = liquidity;
            this.timestamp = new Date();
            this.confidenceScore = confidenceScore;
        }
        
        // Getters
        public String getSource() { return source; }
        public String getFromCurrency() { return fromCurrency; }
        public String getToCurrency() { return toCurrency; }
        public BigDecimal getRate() { return rate; }
        public BigDecimal getVolume24h() { return volume24h; }
        public BigDecimal getLiquidity() { return liquidity; }
        public Date getTimestamp() { return timestamp; }
        public double getConfidenceScore() { return confidenceScore; }
        
        public boolean isExpired() {
            long ageMinutes = (System.currentTimeMillis() - timestamp.getTime()) / (1000 * 60);
            return ageMinutes > 15; // 15 minutes expiry
        }
    }
    
    /**
     * Represents a conversion transaction
     */
    public static class ConversionTransaction {
        private final String transactionId;
        private final ConversionType type;
        private final String fromCurrency;
        private final String toCurrency;
        private final BigDecimal fromAmount;
        private final BigDecimal toAmount;
        private final BigDecimal exchangeRate;
        private final BigDecimal fees;
        private final Address userAddress;
        private final Address bankAddress;
        private final Date timestamp;
        private final String proofOfFunds;
        private final Map<String, Object> metadata;
        
        public ConversionTransaction(ConversionType type, String fromCurrency, String toCurrency,
                                   BigDecimal fromAmount, BigDecimal toAmount, BigDecimal exchangeRate,
                                   BigDecimal fees, Address userAddress, Address bankAddress,
                                   String proofOfFunds) {
            this.transactionId = UUID.randomUUID().toString();
            this.type = type;
            this.fromCurrency = fromCurrency;
            this.toCurrency = toCurrency;
            this.fromAmount = fromAmount;
            this.toAmount = toAmount;
            this.exchangeRate = exchangeRate;
            this.fees = fees;
            this.userAddress = userAddress;
            this.bankAddress = bankAddress;
            this.timestamp = new Date();
            this.proofOfFunds = proofOfFunds;
            this.metadata = new HashMap<>();
        }
        
        // Getters
        public String getTransactionId() { return transactionId; }
        public ConversionType getType() { return type; }
        public String getFromCurrency() { return fromCurrency; }
        public String getToCurrency() { return toCurrency; }
        public BigDecimal getFromAmount() { return fromAmount; }
        public BigDecimal getToAmount() { return toAmount; }
        public BigDecimal getExchangeRate() { return exchangeRate; }
        public BigDecimal getFees() { return fees; }
        public Address getUserAddress() { return userAddress; }
        public Address getBankAddress() { return bankAddress; }
        public Date getTimestamp() { return timestamp; }
        public String getProofOfFunds() { return proofOfFunds; }
        public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
        
        public void addMetadata(String key, Object value) {
            metadata.put(key, value);
        }
    }
    
    // Core infrastructure
    private final StateManager stateManager;
    private final BlockchainState blockchainState;
    private final TransactionValidator transactionValidator;
    private final PKIManager pkiManager;
    private final PeerNetwork peerNetwork;
    private final EventLog eventLog;
    private final BlockchainConfig config;
    
    // Gateway state
    private final Map<String, AfricanCurrency> supportedCurrencies;
    private final Map<String, List<ExchangeRateData>> exchangeRates; // currency pair -> rates
    private final Map<String, ConversionTransaction> recentTransactions;
    private final Map<String, BigDecimal> reserveBalances; // currency -> reserve amount
    private final ScheduledExecutorService rateUpdateExecutor;
    private final AtomicLong totalConversions;
    
    // Gateway configuration
    private GatewayMode currentMode;
    private final BigDecimal baseFeeRate;
    private final SecureRandom secureRandom;
    
    /**
     * Constructor
     */
    public FiatTokenGateway(StateManager stateManager,
                           BlockchainState blockchainState,
                           TransactionValidator transactionValidator,
                           PKIManager pkiManager,
                           PeerNetwork peerNetwork,
                           EventLog eventLog,
                           BlockchainConfig config) {
        this.stateManager = stateManager;
        this.blockchainState = blockchainState;
        this.transactionValidator = transactionValidator;
        this.pkiManager = pkiManager;
        this.peerNetwork = peerNetwork;
        this.eventLog = eventLog;
        this.config = config;
        
        this.supportedCurrencies = new ConcurrentHashMap<>();
        this.exchangeRates = new ConcurrentHashMap<>();
        this.recentTransactions = new ConcurrentHashMap<>();
        this.reserveBalances = new ConcurrentHashMap<>();
        this.rateUpdateExecutor = Executors.newScheduledThreadPool(5);
        this.totalConversions = new AtomicLong(0);
        
        this.currentMode = GatewayMode.FULL_BANKING;
        this.baseFeeRate = new BigDecimal("0.002"); // 0.2% base fee
        this.secureRandom = new SecureRandom();
        
        initializeAfricanCurrencies();
        startRateUpdateService();
        
        LOGGER.info("FiatTokenGateway initialized with " + supportedCurrencies.size() + " currencies");
    }
    
    /**
     * Initializes supported African currencies
     */
    private void initializeAfricanCurrencies() {
        // Major African currencies with their tokenized versions
        addAfricanCurrency("NGN", "NG", "TNGN", 8, new BigDecimal("1.0")); // Nigerian Naira
        addAfricanCurrency("ZAR", "ZA", "TZAR", 8, new BigDecimal("1.0")); // South African Rand
        addAfricanCurrency("KES", "KE", "TKES", 8, new BigDecimal("1.0")); // Kenyan Shilling
        addAfricanCurrency("EGP", "EG", "TEGP", 8, new BigDecimal("1.0")); // Egyptian Pound
        addAfricanCurrency("MAD", "MA", "TMAD", 8, new BigDecimal("1.0")); // Moroccan Dirham
        addAfricanCurrency("GHS", "GH", "TGHS", 8, new BigDecimal("1.0")); // Ghanaian Cedi
        addAfricanCurrency("TZS", "TZ", "TTZS", 8, new BigDecimal("1.0")); // Tanzanian Shilling
        addAfricanCurrency("UGX", "UG", "TUGX", 8, new BigDecimal("1.0")); // Ugandan Shilling
        addAfricanCurrency("ETB", "ET", "TETB", 8, new BigDecimal("1.0")); // Ethiopian Birr
        addAfricanCurrency("XOF", "SN", "TXOF", 8, new BigDecimal("1.0")); // West African CFA Franc
        
        LOGGER.info("Initialized " + supportedCurrencies.size() + " African currencies");
    }
    
    /**
     * Adds a new African currency to the gateway
     */
    private void addAfricanCurrency(String currencyCode, String countryCode, String tokenSymbol,
                                   int decimals, BigDecimal reserveRatio) {
        // Generate token contract address (in real system, this would be deployed)
        Address tokenContract = Address.fromString("0x" + currencyCode + "000000000000000000000000000000000000");
        
        AfricanCurrency currency = new AfricanCurrency(currencyCode, countryCode, tokenSymbol,
                                                      tokenContract, decimals, reserveRatio);
        
        supportedCurrencies.put(currencyCode, currency);
        exchangeRates.put(currencyCode + "_USD", new ArrayList<>());
        reserveBalances.put(currencyCode, BigDecimal.ZERO);
    }
    
    /**
     * Starts the rate update service
     */
    private void startRateUpdateService() {
        // Update rates every 5 minutes
        rateUpdateExecutor.scheduleAtFixedRate(this::updateAllExchangeRates, 0, 5, TimeUnit.MINUTES);
        
        // Cleanup old rates every hour
        rateUpdateExecutor.scheduleAtFixedRate(this::cleanupOldRates, 1, 1, TimeUnit.HOURS);
        
        LOGGER.info("Rate update service started");
    }
    
    /**
     * Updates exchange rates from multiple sources
     */
    private void updateAllExchangeRates() {
        try {
            for (String currencyCode : supportedCurrencies.keySet()) {
                CompletableFuture.runAsync(() -> updateCurrencyRates(currencyCode));
            }
        } catch (Exception e) {
            LOGGER.severe("Error updating exchange rates: " + e.getMessage());
        }
    }
    
    /**
     * Updates rates for a specific currency
     */
    private void updateCurrencyRates(String currencyCode) {
        try {
            // Simulate fetching from multiple exchange sources
            List<ExchangeRateData> newRates = fetchExchangeRatesForCurrency(currencyCode);
            
            String pair = currencyCode + "_USD";
            exchangeRates.put(pair, newRates);
            
            // Apply fair-weighting algorithm
            BigDecimal fairRate = calculateFairWeightedRate(newRates);
            
            eventLog.logEvent("RATE_UPDATE: " + currencyCode + " = " + fairRate + " USD");
            
        } catch (Exception e) {
            LOGGER.warning("Error updating rates for " + currencyCode + ": " + e.getMessage());
        }
    }
    
    /**
     * Simulates fetching exchange rates from multiple sources
     */
    private List<ExchangeRateData> fetchExchangeRatesForCurrency(String currencyCode) {
        List<ExchangeRateData> rates = new ArrayList<>();
        
        // Simulate data from various exchanges
        String[] exchanges = {"Binance", "Coinbase", "Kraken", "Bitstamp", "KuCoin", "OKEx", "Huobi", "LocalBitcoins"};
        
        // Base rate (simulated) - in real system, this would come from actual APIs
        BigDecimal baseRate = getSimulatedBaseRate(currencyCode);
        
        for (String exchange : exchanges) {
            // Add some variance to simulate real market conditions
            double variance = (secureRandom.nextGaussian() * 0.02); // 2% standard deviation
            BigDecimal rate = baseRate.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(variance)));
            
            // Simulate volume and liquidity data
            BigDecimal volume = BigDecimal.valueOf(secureRandom.nextDouble() * 10000000);
            BigDecimal liquidity = BigDecimal.valueOf(secureRandom.nextDouble() * 5000000);
            double confidence = 0.7 + (secureRandom.nextDouble() * 0.3); // 0.7-1.0
            
            ExchangeRateData rateData = new ExchangeRateData(exchange, currencyCode, "USD",
                                                            rate, volume, liquidity, confidence);
            rates.add(rateData);
        }
        
        return rates;
    }
    
    /**
     * Gets simulated base rate for a currency
     */
    private BigDecimal getSimulatedBaseRate(String currencyCode) {
        // Simulated rates relative to USD
        Map<String, BigDecimal> baseRates = Map.of(
            "NGN", new BigDecimal("0.0024"),  // 1 NGN = 0.0024 USD
            "ZAR", new BigDecimal("0.054"),   // 1 ZAR = 0.054 USD
            "KES", new BigDecimal("0.0076"),  // 1 KES = 0.0076 USD
            "EGP", new BigDecimal("0.032"),   // 1 EGP = 0.032 USD
            "MAD", new BigDecimal("0.099"),   // 1 MAD = 0.099 USD
            "GHS", new BigDecimal("0.087"),   // 1 GHS = 0.087 USD
            "TZS", new BigDecimal("0.00043"), // 1 TZS = 0.00043 USD
            "UGX", new BigDecimal("0.00027"), // 1 UGX = 0.00027 USD
            "ETB", new BigDecimal("0.018"),   // 1 ETB = 0.018 USD
            "XOF", new BigDecimal("0.0017")   // 1 XOF = 0.0017 USD
        );
        
        return baseRates.getOrDefault(currencyCode, BigDecimal.ONE);
    }
    
    /**
     * Calculates fair-weighted rate using the African-focused algorithm
     */
    private BigDecimal calculateFairWeightedRate(List<ExchangeRateData> rates) {
        if (rates.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        // Remove outliers using Median Absolute Deviation
        List<ExchangeRateData> filteredRates = removeOutliers(rates);
        
        if (filteredRates.size() < MIN_EXCHANGE_SOURCES) {
            LOGGER.warning("Insufficient exchange sources after outlier removal: " + 
                         filteredRates.size() + " (min required: " + MIN_EXCHANGE_SOURCES + ")");
            return calculateSimpleMedian(rates);
        }
        
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        
        for (ExchangeRateData rate : filteredRates) {
            // Calculate individual component weights
            BigDecimal volumeWeight = calculateVolumeWeight(rate, filteredRates);
            BigDecimal liquidityWeight = calculateLiquidityWeight(rate, filteredRates);
            BigDecimal stabilityWeight = calculateStabilityWeight(rate);
            BigDecimal adoptionWeight = calculateAdoptionWeight(rate);
            BigDecimal exchangeWeight = calculateExchangeWeight(rate);
            
            // Combine weights according to fair-weighting formula
            BigDecimal totalRateWeight = volumeWeight.multiply(BigDecimal.valueOf(VOLUME_WEIGHT))
                .add(liquidityWeight.multiply(BigDecimal.valueOf(LIQUIDITY_WEIGHT)))
                .add(stabilityWeight.multiply(BigDecimal.valueOf(STABILITY_WEIGHT)))
                .add(adoptionWeight.multiply(BigDecimal.valueOf(ADOPTION_WEIGHT)))
                .add(exchangeWeight.multiply(BigDecimal.valueOf(EXCHANGE_WEIGHT)));
            
            // Apply confidence score
            totalRateWeight = totalRateWeight.multiply(BigDecimal.valueOf(rate.getConfidenceScore()));
            
            weightedSum = weightedSum.add(rate.getRate().multiply(totalRateWeight));
            totalWeight = totalWeight.add(totalRateWeight);
        }
        
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return calculateSimpleMedian(rates);
        }
        
        return weightedSum.divide(totalWeight, 8, RoundingMode.HALF_UP);
    }
    
    /**
     * Removes statistical outliers using Median Absolute Deviation
     */
    private List<ExchangeRateData> removeOutliers(List<ExchangeRateData> rates) {
        if (rates.size() <= 3) {
            return rates; // Too few data points to remove outliers
        }
        
        // Calculate median
        List<BigDecimal> rateValues = rates.stream()
            .map(ExchangeRateData::getRate)
            .sorted()
            .collect(Collectors.toList());
        
        BigDecimal median = calculateMedianFromSortedList(rateValues);
        
        // Calculate MAD (Median Absolute Deviation)
        List<BigDecimal> deviations = rates.stream()
            .map(rate -> rate.getRate().subtract(median).abs())
            .sorted()
            .collect(Collectors.toList());
        
        BigDecimal mad = calculateMedianFromSortedList(deviations);
        
        // Filter outliers (rates beyond OUTLIER_THRESHOLD * MAD from median)
        BigDecimal threshold = mad.multiply(BigDecimal.valueOf(OUTLIER_THRESHOLD));
        
        return rates.stream()
            .filter(rate -> {
                BigDecimal deviation = rate.getRate().subtract(median).abs();
                return deviation.compareTo(threshold) <= 0;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Helper methods for weight calculations
     */
    private BigDecimal calculateVolumeWeight(ExchangeRateData rate, List<ExchangeRateData> allRates) {
        BigDecimal maxVolume = allRates.stream()
            .map(ExchangeRateData::getVolume24h)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ONE);
        
        if (maxVolume.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }
        
        return rate.getVolume24h().divide(maxVolume, 4, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateLiquidityWeight(ExchangeRateData rate, List<ExchangeRateData> allRates) {
        BigDecimal maxLiquidity = allRates.stream()
            .map(ExchangeRateData::getLiquidity)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ONE);
        
        if (maxLiquidity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }
        
        return rate.getLiquidity().divide(maxLiquidity, 4, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateStabilityWeight(ExchangeRateData rate) {
        // In a real system, this would calculate price stability over time
        // For now, return a default stability score
        return new BigDecimal("0.8");
    }
    
    private BigDecimal calculateAdoptionWeight(ExchangeRateData rate) {
        // In a real system, this would measure local adoption metrics
        // For now, assign higher weight to certain exchanges
        Map<String, BigDecimal> adoptionScores = Map.of(
            "Binance", new BigDecimal("0.9"),
            "LocalBitcoins", new BigDecimal("0.95"), // Higher for local adoption
            "Coinbase", new BigDecimal("0.8"),
            "Kraken", new BigDecimal("0.75")
        );
        
        return adoptionScores.getOrDefault(rate.getSource(), new BigDecimal("0.7"));
    }
    
    private BigDecimal calculateExchangeWeight(ExchangeRateData rate) {
        // Higher weight for diverse exchanges to prevent manipulation
        return BigDecimal.ONE;
    }
    
    /**
     * Calculates simple median as fallback
     */
    private BigDecimal calculateSimpleMedian(List<ExchangeRateData> rates) {
        List<BigDecimal> rateValues = rates.stream()
            .map(ExchangeRateData::getRate)
            .sorted()
            .collect(Collectors.toList());
        
        return calculateMedianFromSortedList(rateValues);
    }
    
    /**
     * Calculates median from a sorted list
     */
    private BigDecimal calculateMedianFromSortedList(List<BigDecimal> sortedValues) {
        int size = sortedValues.size();
        if (size == 0) {
            return BigDecimal.ZERO;
        }
        
        if (size % 2 == 0) {
            BigDecimal mid1 = sortedValues.get(size / 2 - 1);
            BigDecimal mid2 = sortedValues.get(size / 2);
            return mid1.add(mid2).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
        } else {
            return sortedValues.get(size / 2);
        }
    }
    
    /**
     * Performs currency conversion
     */
    public ConversionTransaction performConversion(ConversionType type, String fromCurrency,
                                                 String toCurrency, BigDecimal amount,
                                                 Address userAddress, Address bankAddress,
                                                 String proofOfFunds) {
        try {
            LOGGER.info("Processing conversion: " + amount + " " + fromCurrency + " -> " + toCurrency);
            
            // Validate conversion parameters
            if (!validateConversionParameters(type, fromCurrency, toCurrency, amount, userAddress)) {
                LOGGER.warning("Invalid conversion parameters");
                return null;
            }
            
            // Get current exchange rate
            BigDecimal exchangeRate = getCurrentExchangeRate(fromCurrency, toCurrency);
            if (exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
                LOGGER.warning("Unable to get exchange rate for " + fromCurrency + "/" + toCurrency);
                return null;
            }
            
            // Calculate conversion amounts
            BigDecimal convertedAmount = amount.multiply(exchangeRate);
            BigDecimal fees = calculateConversionFees(amount, fromCurrency, toCurrency, type);
            BigDecimal finalAmount = convertedAmount.subtract(fees);
            
            // Validate conversion limits and reserves
            if (!validateConversionLimits(fromCurrency, toCurrency, amount, finalAmount)) {
                LOGGER.warning("Conversion limits exceeded");
                return null;
            }
            
            // Create conversion transaction
            ConversionTransaction conversion = new ConversionTransaction(
                type, fromCurrency, toCurrency, amount, finalAmount,
                exchangeRate, fees, userAddress, bankAddress, proofOfFunds);
            
            // Add metadata
            conversion.addMetadata("gatewayMode", currentMode);
            conversion.addMetadata("sources", getActiveSources(fromCurrency, toCurrency));
            conversion.addMetadata("fairWeight", "true");
            
            // Process the conversion
            if (executeConversion(conversion)) {
                recentTransactions.put(conversion.getTransactionId(), conversion);
                totalConversions.incrementAndGet();
                
                eventLog.logEvent("CONVERSION_SUCCESS: " + conversion.getTransactionId() + 
                                 " (" + amount + " " + fromCurrency + " -> " + finalAmount + " " + toCurrency + ")");
                
                LOGGER.info("Conversion successful: " + conversion.getTransactionId());
                return conversion;
            } else {
                LOGGER.warning("Conversion execution failed");
                return null;
            }
            
        } catch (Exception e) {
            LOGGER.severe("Error performing conversion: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Validates conversion parameters
     */
    private boolean validateConversionParameters(ConversionType type, String fromCurrency,
                                               String toCurrency, BigDecimal amount,
                                               Address userAddress) {
        // Basic validation
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        if (userAddress == null) {
            return false;
        }
        
        if (fromCurrency.equals(toCurrency)) {
            return false;
        }
        
        // Check supported currencies
        if (type == ConversionType.FIAT_TO_TOKEN || type == ConversionType.TOKEN_TO_TOKEN) {
            if (!supportedCurrencies.containsKey(fromCurrency) && 
                !fromCurrency.equals("USD") && !fromCurrency.equals("EUR")) {
                return false;
            }
        }
        
        if (type == ConversionType.TOKEN_TO_FIAT || type == ConversionType.TOKEN_TO_TOKEN) {
            if (!supportedCurrencies.containsKey(toCurrency) && 
                !toCurrency.equals("USD") && !toCurrency.equals("EUR")) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Gets current exchange rate between two currencies
     */
    private BigDecimal getCurrentExchangeRate(String fromCurrency, String toCurrency) {
        try {
            // Direct pair
            String pair = fromCurrency + "_" + toCurrency;
            List<ExchangeRateData> rates = exchangeRates.get(pair);
            
            if (rates != null && !rates.isEmpty()) {
                return calculateFairWeightedRate(rates);
            }
            
            // Try reverse pair
            String reversePair = toCurrency + "_" + fromCurrency;
            rates = exchangeRates.get(reversePair);
            
            if (rates != null && !rates.isEmpty()) {
                BigDecimal reverseRate = calculateFairWeightedRate(rates);
                if (reverseRate.compareTo(BigDecimal.ZERO) > 0) {
                    return BigDecimal.ONE.divide(reverseRate, 8, RoundingMode.HALF_UP);
                }
            }
            
            // Cross-rate calculation via USD
            BigDecimal fromToUsd = getDirectRate(fromCurrency, "USD");
            BigDecimal toToUsd = getDirectRate(toCurrency, "USD");
            
            if (fromToUsd != null && toToUsd != null && toToUsd.compareTo(BigDecimal.ZERO) > 0) {
                return fromToUsd.divide(toToUsd, 8, RoundingMode.HALF_UP);
            }
            
            return null;
            
        } catch (Exception e) {
            LOGGER.warning("Error getting exchange rate: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets direct exchange rate for a currency pair
     */
    private BigDecimal getDirectRate(String fromCurrency, String toCurrency) {
        String pair = fromCurrency + "_" + toCurrency;
        List<ExchangeRateData> rates = exchangeRates.get(pair);
        
        if (rates != null && !rates.isEmpty()) {
            return calculateFairWeightedRate(rates);
        }
        
        return null;
    }
    
    /**
     * Calculates conversion fees
     */
    private BigDecimal calculateConversionFees(BigDecimal amount, String fromCurrency,
                                             String toCurrency, ConversionType type) {
        BigDecimal feeRate = baseFeeRate;
        
        // Adjust fee based on conversion type
        switch (type) {
            case TOKEN_TO_TOKEN:
                feeRate = feeRate.multiply(new BigDecimal("0.5")); // Lower fees for African-to-African
                break;
            case CROSS_BORDER:
                feeRate = feeRate.multiply(new BigDecimal("1.5")); // Higher fees for cross-border
                break;
            default:
                break;
        }
        
        // Emergency mode has higher fees
        if (currentMode == GatewayMode.EMERGENCY_MODE) {
            feeRate = feeRate.multiply(new BigDecimal("2.0"));
        }
        
        return amount.multiply(feeRate);
    }
    
    /**
     * Validates conversion limits
     */
    private boolean validateConversionLimits(String fromCurrency, String toCurrency,
                                           BigDecimal fromAmount, BigDecimal toAmount) {
        // Check daily limits (in a real system, this would be more sophisticated)
        BigDecimal dailyLimit = new BigDecimal("100000"); // $100k daily limit
        
        if (fromAmount.compareTo(dailyLimit) > 0) {
            return false;
        }
        
        // Check reserve adequacy for token-to-fiat conversions
        if (supportedCurrencies.containsKey(fromCurrency)) {
            BigDecimal currentReserve = reserveBalances.get(fromCurrency);
            BigDecimal requiredReserve = fromAmount.multiply(new BigDecimal("1.1")); // 110% reserve requirement
            
            if (currentReserve.compareTo(requiredReserve) < 0) {
                LOGGER.warning("Insufficient reserves for " + fromCurrency + 
                             ": " + currentReserve + " < " + requiredReserve);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Executes the conversion
     */
    private boolean executeConversion(ConversionTransaction conversion) {
        try {
            // Update reserve balances
            updateReserveBalances(conversion);
            
            // In a real system, this would:
            // 1. Lock source funds
            // 2. Mint/burn tokens as needed
            // 3. Transfer tokens/fiat to user
            // 4. Update on-chain state
            
            LOGGER.info("Conversion executed: " + conversion.getTransactionId());
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Error executing conversion: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Updates reserve balances after conversion
     */
    private void updateReserveBalances(ConversionTransaction conversion) {
        String fromCurrency = conversion.getFromCurrency();
        String toCurrency = conversion.getToCurrency();
        
        // Deduct from source currency reserve
        if (reserveBalances.containsKey(fromCurrency)) {
            BigDecimal currentReserve = reserveBalances.get(fromCurrency);
            BigDecimal newReserve = currentReserve.subtract(conversion.getFromAmount());
            reserveBalances.put(fromCurrency, newReserve);
        }
        
        // Add to target currency reserve
        if (reserveBalances.containsKey(toCurrency)) {
            BigDecimal currentReserve = reserveBalances.get(toCurrency);
            BigDecimal newReserve = currentReserve.add(conversion.getToAmount());
            reserveBalances.put(toCurrency, newReserve);
        }
    }
    
    /**
     * Gets active rate sources for a currency pair
     */
    private List<String> getActiveSources(String fromCurrency, String toCurrency) {
        String pair = fromCurrency + "_" + toCurrency;
        List<ExchangeRateData> rates = exchangeRates.get(pair);
        
        if (rates == null) {
            return new ArrayList<>();
        }
        
        return rates.stream()
            .filter(rate -> !rate.isExpired())
            .map(ExchangeRateData::getSource)
            .collect(Collectors.toList());
    }
    
    /**
     * Cleanup old rates
     */
    private void cleanupOldRates() {
        try {
            for (Map.Entry<String, List<ExchangeRateData>> entry : exchangeRates.entrySet()) {
                List<ExchangeRateData> rates = entry.getValue();
                rates.removeIf(ExchangeRateData::isExpired);
            }
            
            // Also cleanup old transactions (keep only last 1000)
            if (recentTransactions.size() > 1000) {
                List<ConversionTransaction> sortedTx = recentTransactions.values().stream()
                    .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                    .collect(Collectors.toList());
                
                recentTransactions.clear();
                sortedTx.stream().limit(1000)
                    .forEach(tx -> recentTransactions.put(tx.getTransactionId(), tx));
            }
            
        } catch (Exception e) {
            LOGGER.warning("Error during cleanup: " + e.getMessage());
        }
    }
    
    /**
     * Public API methods
     */
    public AfricanCurrency getCurrency(String currencyCode) {
        return supportedCurrencies.get(currencyCode);
    }
    
    public Collection<AfricanCurrency> getSupportedCurrencies() {
        return new ArrayList<>(supportedCurrencies.values());
    }
    
    public ConversionTransaction getTransaction(String transactionId) {
        return recentTransactions.get(transactionId);
    }
    
    public List<ConversionTransaction> getRecentTransactions(int limit) {
        return recentTransactions.values().stream()
            .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    public Map<String, Object> getSystemStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalConversions", totalConversions.get());
        stats.put("supportedCurrencies", supportedCurrencies.size());
        stats.put("currentMode", currentMode);
        stats.put("recentTransactions", recentTransactions.size());
        stats.put("activePairs", exchangeRates.size());
        
        // Rate source statistics
        Map<String, Integer> sourceStats = new HashMap<>();
        for (List<ExchangeRateData> rates : exchangeRates.values()) {
            for (ExchangeRateData rate : rates) {
                sourceStats.merge(rate.getSource(), 1, Integer::sum);
            }
        }
        stats.put("rateSources", sourceStats);
        
        // Reserve balances
        stats.put("reserveBalances", new HashMap<>(reserveBalances));
        
        return stats;
    }
    
    /**
     * Sets gateway mode
     */
    public void setGatewayMode(GatewayMode mode) {
        this.currentMode = mode;
        eventLog.logEvent("GATEWAY_MODE_CHANGED: " + mode);
        LOGGER.info("Gateway mode changed to: " + mode);
    }
    
    /**
     * Shutdown method
     */
    public void shutdown() {
        try {
            rateUpdateExecutor.shutdown();
            if (!rateUpdateExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                rateUpdateExecutor.shutdownNow();
            }
            
            LOGGER.info("FiatTokenGateway shutdown completed");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Gateway shutdown interrupted");
        }
    }
}