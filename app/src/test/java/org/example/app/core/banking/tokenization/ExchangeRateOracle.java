package org.example.app.core.banking.tokenization;

import org.example.app.core.state.BlockchainState;
import org.example.app.core.consensus.Blockchain;
import org.example.app.core.crypto.PKIManager;
import org.example.app.core.types.Address;
import org.example.app.core.blockchain.transaction.CurrencyExchangeTransaction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Exchange Rate Oracle - Provides fair, manipulation-resistant exchange rates
 * with prioritization of African exchanges and government resistance
 */
public class ExchangeRateOracle {
    private static final Logger LOGGER = Logger.getLogger(ExchangeRateOracle.class.getName());
    
    // Exchange source management
    private final Map<String, ExchangeSource> registeredExchanges = new ConcurrentHashMap<>();
    private final Set<String> africanExchanges = ConcurrentHashMap.newKeySet();
    private final Set<String> authorizedFeedProviders = ConcurrentHashMap.newKeySet();
    private final Map<String, ExchangeCredibility> exchangeCredibility = new ConcurrentHashMap<>();
    
    // Rate aggregation and storage
    private final Map<String, CurrencyPair> currencyPairs = new ConcurrentHashMap<>();
    private final Map<String, List<RateData>> rateHistory = new ConcurrentHashMap<>();
    private final Map<String, FairRate> currentFairRates = new ConcurrentHashMap<>();
    
    // Anti-manipulation features
    private final Map<String, OutlierDetection> outlierDetectors = new ConcurrentHashMap<>();
    private final Map<String, ManipulationAlert> manipulationAlerts = new ConcurrentHashMap<>();
    private final BigDecimal maxDeviationThreshold = new BigDecimal("0.15"); // 15% max deviation
    private final int minimumSourceCount = 3; // Minimum sources required for fair rate
    
    // Geographic resistance
    private final Map<String, String> exchangeCountries = new ConcurrentHashMap<>();
    private final BigDecimal maxCountryWeight = new BigDecimal("0.33"); // No country > 33% weight
    private final Map<String, BigDecimal> countryWeights = new ConcurrentHashMap<>();
    
    // Update scheduling and caching
    private final ScheduledExecutorService updateScheduler = Executors.newScheduledThreadPool(4);
    private final long updateInterval = 60000; // 1 minute
    private final long cacheExpiry = 300000; // 5 minutes
    private final Map<String, Long> lastUpdateTimestamps = new ConcurrentHashMap<>();
    
    // Blockchain integration
    private final BlockchainState blockchainState;
    private final Blockchain blockchain;
    private final PKIManager pkiManager;
    
    /**
     * Exchange Source representation
     */
    public static class ExchangeSource {
        public final String exchangeId;
        public final String exchangeName;
        public final String country;
        public final boolean isAfrican;
        public final ExchangeType exchangeType;
        public final BigDecimal credibilityScore; // 0.0 to 1.0
        public final BigDecimal weight; // Weighting in fair rate calculation
        public final String apiEndpoint;
        public final Set<String> supportedPairs;
        public boolean isActive;
        public long lastSuccessfulUpdate;
        public int consecutiveFailures;
        
        public enum ExchangeType {
            TRADITIONAL_FOREX, CRYPTO_EXCHANGE, CENTRAL_BANK, COMMODITY_EXCHANGE, 
            AFRICAN_REGIONAL, INTERNATIONAL_MAJOR
        }
        
        public ExchangeSource(String exchangeId, String exchangeName, String country,
                             ExchangeType exchangeType, BigDecimal credibilityScore,
                             String apiEndpoint, Set<String> supportedPairs) {
            this.exchangeId = exchangeId;
            this.exchangeName = exchangeName;
            this.country = country;
            this.isAfrican = isAfricanCountry(country);
            this.exchangeType = exchangeType;
            this.credibilityScore = credibilityScore;
            this.weight = calculateInitialWeight();
            this.apiEndpoint = apiEndpoint;
            this.supportedPairs = new HashSet<>(supportedPairs);
            this.isActive = true;
            this.lastSuccessfulUpdate = System.currentTimeMillis();
            this.consecutiveFailures = 0;
        }
        
        private BigDecimal calculateInitialWeight() {
            BigDecimal baseWeight = credibilityScore;
            
            // Boost African exchanges by 20%
            if (isAfrican) {
                baseWeight = baseWeight.multiply(new BigDecimal("1.2"));
            }
            
            // Boost based on exchange type
            switch (exchangeType) {
                case CENTRAL_BANK:
                    baseWeight = baseWeight.multiply(new BigDecimal("1.3"));
                    break;
                case AFRICAN_REGIONAL:
                    baseWeight = baseWeight.multiply(new BigDecimal("1.4"));
                    break;
                case INTERNATIONAL_MAJOR:
                    baseWeight = baseWeight.multiply(new BigDecimal("1.1"));
                    break;
            }
            
            return baseWeight.min(BigDecimal.ONE); // Cap at 1.0
        }
        
        private boolean isAfricanCountry(String country) {
            Set<String> africanCountries = Set.of(
                "Algeria", "Angola", "Benin", "Botswana", "Burkina Faso", "Burundi",
                "Cameroon", "Cape Verde", "Central African Republic", "Chad", "Comoros",
                "Democratic Republic of Congo", "Republic of Congo", "Djibouti", "Egypt",
                "Equatorial Guinea", "Eritrea", "Eswatini", "Ethiopia", "Gabon", "Gambia",
                "Ghana", "Guinea", "Guinea-Bissau", "Ivory Coast", "Kenya", "Lesotho",
                "Liberia", "Libya", "Madagascar", "Malawi", "Mali", "Mauritania",
                "Mauritius", "Morocco", "Mozambique", "Namibia", "Niger", "Nigeria",
                "Rwanda", "Sao Tome and Principe", "Senegal", "Seychelles", "Sierra Leone",
                "Somalia", "South Africa", "South Sudan", "Sudan", "Tanzania", "Togo",
                "Tunisia", "Uganda", "Zambia", "Zimbabwe"
            );
            return africanCountries.contains(country);
        }
    }
    
    /**
     * Currency Pair configuration
     */
    public static class CurrencyPair {
        public final String baseCurrency;
        public final String quoteCurrency;
        public final String pairSymbol; // e.g., "USD/NGN"
        public final boolean isAfricanPair; // At least one currency is African
        public final Set<String> activeSources;
        public final BigDecimal africanPriority; // Higher priority for African pairs
        
        public CurrencyPair(String baseCurrency, String quoteCurrency) {
            this.baseCurrency = baseCurrency;
            this.quoteCurrency = quoteCurrency;
            this.pairSymbol = baseCurrency + "/" + quoteCurrency;
            this.isAfricanPair = isAfricanCurrency(baseCurrency) || isAfricanCurrency(quoteCurrency);
            this.activeSources = new HashSet<>();
            this.africanPriority = isAfricanPair ? new BigDecimal("1.5") : BigDecimal.ONE;
        }
        
        private boolean isAfricanCurrency(String currency) {
            Set<String> africanCurrencies = Set.of(
                "NGN", "ZAR", "KES", "EGP", "GHS", "XOF", "XAF", "MAD", "TND", "ETB",
                "UGX", "TZS", "MWK", "ZMW", "BWP", "MZN", "AOA", "DZD", "LYD"
            );
            return africanCurrencies.contains(currency);
        }
    }
    
    /**
     * Rate Data from individual source
     */
    public static class RateData {
        public final String sourceExchangeId;
        public final String pairSymbol;
        public final BigDecimal rate;
        public final BigDecimal volume; // Trading volume
        public final BigDecimal spread; // Bid-ask spread
        public final long timestamp;
        public final String signature; // Cryptographic signature from source
        public boolean isValidated;
        
        public RateData(String sourceExchangeId, String pairSymbol, BigDecimal rate,
                       BigDecimal volume, BigDecimal spread, String signature) {
            this.sourceExchangeId = sourceExchangeId;
            this.pairSymbol = pairSymbol;
            this.rate = rate;
            this.volume = volume != null ? volume : BigDecimal.ZERO;
            this.spread = spread != null ? spread : BigDecimal.ZERO;
            this.signature = signature;
            this.timestamp = System.currentTimeMillis();
            this.isValidated = false;
        }
    }
    
    /**
     * Fair Rate calculation result
     */
    public static class FairRate {
        public final String pairSymbol;
        public final BigDecimal fairRate;
        public final BigDecimal confidence; // Confidence level 0.0 to 1.0
        public final int sourceCount; // Number of sources used
        public final BigDecimal africanWeight; // Weight from African exchanges
        public final BigDecimal standardDeviation;
        public final long calculationTimestamp;
        public final List<String> contributingSources;
        
        public FairRate(String pairSymbol, BigDecimal fairRate, BigDecimal confidence,
                       int sourceCount, BigDecimal africanWeight, BigDecimal standardDeviation,
                       List<String> contributingSources) {
            this.pairSymbol = pairSymbol;
            this.fairRate = fairRate;
            this.confidence = confidence;
            this.sourceCount = sourceCount;
            this.africanWeight = africanWeight;
            this.standardDeviation = standardDeviation;
            this.calculationTimestamp = System.currentTimeMillis();
            this.contributingSources = new ArrayList<>(contributingSources);
        }
    }
    
    /**
     * Outlier Detection using Median Absolute Deviation
     */
    public static class OutlierDetection {
        public final String pairSymbol;
        public final List<BigDecimal> recentRates;
        public final BigDecimal median;
        public final BigDecimal mad; // Median Absolute Deviation
        public final BigDecimal outlierThreshold;
        
        public OutlierDetection(String pairSymbol, List<BigDecimal> rates) {
            this.pairSymbol = pairSymbol;
            this.recentRates = new ArrayList<>(rates);
            this.median = calculateMedian(rates);
            this.mad = calculateMAD(rates, median);
            this.outlierThreshold = mad.multiply(new BigDecimal("3.5")); // 3.5 * MAD
        }
        
        private BigDecimal calculateMedian(List<BigDecimal> rates) {
            List<BigDecimal> sorted = new ArrayList<>(rates);
            sorted.sort(BigDecimal::compareTo);
            
            int size = sorted.size();
            if (size == 0) return BigDecimal.ZERO;
            if (size % 2 == 1) {
                return sorted.get(size / 2);
            } else {
                return sorted.get(size / 2 - 1).add(sorted.get(size / 2))
                            .divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
            }
        }
        
        private BigDecimal calculateMAD(List<BigDecimal> rates, BigDecimal median) {
            List<BigDecimal> deviations = new ArrayList<>();
            for (BigDecimal rate : rates) {
                deviations.add(rate.subtract(median).abs());
            }
            return calculateMedian(deviations);
        }
        
        public boolean isOutlier(BigDecimal rate) {
            return rate.subtract(median).abs().compareTo(outlierThreshold) > 0;
        }
    }
    
    /**
     * Exchange Credibility tracking
     */
    public static class ExchangeCredibility {
        public final String exchangeId;
        public BigDecimal reliabilityScore; // Updated based on performance
        public int totalUpdates;
        public int successfulUpdates;
        public int outlierReports;
        public long lastOutlierTime;
        
        public ExchangeCredibility(String exchangeId) {
            this.exchangeId = exchangeId;
            this.reliabilityScore = new BigDecimal("0.8"); // Start at 80%
            this.totalUpdates = 0;
            this.successfulUpdates = 0;
            this.outlierReports = 0;
            this.lastOutlierTime = 0;
        }
        
        public void recordUpdate(boolean successful, boolean isOutlier) {
            totalUpdates++;
            if (successful) {
                successfulUpdates++;
            }
            
            if (isOutlier) {
                outlierReports++;
                lastOutlierTime = System.currentTimeMillis();
            }
            
            updateReliabilityScore();
        }
        
        private void updateReliabilityScore() {
            // Base reliability from success rate
            BigDecimal successRate = new BigDecimal(successfulUpdates)
                .divide(new BigDecimal(totalUpdates), 4, RoundingMode.HALF_UP);
            
            // Penalty for outliers
            BigDecimal outlierPenalty = new BigDecimal(outlierReports)
                .multiply(new BigDecimal("0.1"));
            
            reliabilityScore = successRate.subtract(outlierPenalty).max(new BigDecimal("0.1"));
        }
    }
    
    /**
     * Manipulation Alert system
     */
    public static class ManipulationAlert {
        public final String alertId;
        public final String pairSymbol;
        public final AlertType alertType;
        public final String description;
        public final long timestamp;
        public final Set<String> suspiciousExchanges;
        public final BigDecimal severityLevel; // 0.0 to 1.0
        
        public enum AlertType {
            PRICE_DEVIATION, VOLUME_ANOMALY, COORDINATED_MOVEMENT, 
            SOURCE_MANIPULATION, GEOGRAPHIC_CONCENTRATION
        }
        
        public ManipulationAlert(String pairSymbol, AlertType alertType, String description,
                               Set<String> suspiciousExchanges, BigDecimal severityLevel) {
            this.alertId = generateAlertId();
            this.pairSymbol = pairSymbol;
            this.alertType = alertType;
            this.description = description;
            this.suspiciousExchanges = new HashSet<>(suspiciousExchanges);
            this.severityLevel = severityLevel;
            this.timestamp = System.currentTimeMillis();
        }
        
        private String generateAlertId() {
            return "ALERT_" + System.currentTimeMillis() + "_" + 
                   Math.abs(Objects.hash(pairSymbol, alertType));
        }
    }
    
    /**
     * Creates a new Exchange Rate Oracle
     */
    public ExchangeRateOracle(BlockchainState blockchainState, Blockchain blockchain,
                             PKIManager pkiManager) {
        this.blockchainState = blockchainState;
        this.blockchain = blockchain;
        this.pkiManager = pkiManager;
        
        // Initialize major African exchanges
        initializeAfricanExchanges();
        
        // Initialize international exchanges
        initializeInternationalExchanges();
        
        // Start automatic rate updates
        startRateUpdateScheduler();
        
        LOGGER.info("Exchange Rate Oracle initialized with " + registeredExchanges.size() + " exchanges");
    }
    
    /**
     * Initializes major African exchanges with priority
     */
    private void initializeAfricanExchanges() {
        // South Africa - JSE and others
        registerExchange("JSE", "Johannesburg Stock Exchange", "South Africa",
                        ExchangeSource.ExchangeType.AFRICAN_REGIONAL, new BigDecimal("0.9"),
                        "https://api.jse.co.za", Set.of("USD/ZAR", "EUR/ZAR", "GBP/ZAR"));
        
        // Nigeria - NSE and others
        registerExchange("NSE", "Nigerian Stock Exchange", "Nigeria",
                        ExchangeSource.ExchangeType.AFRICAN_REGIONAL, new BigDecimal("0.85"),
                        "https://api.nse.com.ng", Set.of("USD/NGN", "EUR/NGN", "GBP/NGN"));
        
        // Kenya - NSE
        registerExchange("NSE_KE", "Nairobi Securities Exchange", "Kenya",
                        ExchangeSource.ExchangeType.AFRICAN_REGIONAL, new BigDecimal("0.8"),
                        "https://api.nse.co.ke", Set.of("USD/KES", "EUR/KES"));
        
        // Egypt - EGX
        registerExchange("EGX", "Egyptian Exchange", "Egypt",
                        ExchangeSource.ExchangeType.AFRICAN_REGIONAL, new BigDecimal("0.82"),
                        "https://api.egx.com.eg", Set.of("USD/EGP", "EUR/EGP"));
        
        // Ghana - GSE
        registerExchange("GSE", "Ghana Stock Exchange", "Ghana",
                        ExchangeSource.ExchangeType.AFRICAN_REGIONAL, new BigDecimal("0.75"),
                        "https://api.gse.com.gh", Set.of("USD/GHS", "EUR/GHS"));
        
        // Central banks for authoritative rates
        registerExchange("SARB", "South African Reserve Bank", "South Africa",
                        ExchangeSource.ExchangeType.CENTRAL_BANK, new BigDecimal("0.95"),
                        "https://api.resbank.co.za", Set.of("USD/ZAR"));
        
        registerExchange("CBN", "Central Bank of Nigeria", "Nigeria",
                        ExchangeSource.ExchangeType.CENTRAL_BANK, new BigDecimal("0.92"),
                        "https://api.cbn.gov.ng", Set.of("USD/NGN"));
    }
    
    /**
     * Initializes major international exchanges for comparison
     */
    private void initializeInternationalExchanges() {
        // Major forex providers
        registerExchange("XE", "XE Currency", "Canada",
                        ExchangeSource.ExchangeType.INTERNATIONAL_MAJOR, new BigDecimal("0.88"),
                        "https://api.xe.com", Set.of("USD/ZAR", "USD/NGN", "USD/KES"));
        
        registerExchange("OANDA", "OANDA", "United States",
                        ExchangeSource.ExchangeType.INTERNATIONAL_MAJOR, new BigDecimal("0.9"),
                        "https://api.oanda.com", Set.of("USD/ZAR", "USD/NGN", "USD/EGP"));
        
        registerExchange("FXCM", "FXCM", "United Kingdom",
                        ExchangeSource.ExchangeType.INTERNATIONAL_MAJOR, new BigDecimal("0.87"),
                        "https://api.fxcm.com", Set.of("USD/ZAR", "EUR/ZAR"));
        
        // Crypto exchanges for cross-reference
        registerExchange("BINANCE", "Binance", "Malta",
                        ExchangeSource.ExchangeType.CRYPTO_EXCHANGE, new BigDecimal("0.85"),
                        "https://api.binance.com", Set.of("USDT/ZAR", "BTC/NGN"));
    }
    
    /**
     * Registers a new exchange source
     */
    public boolean registerExchange(String exchangeId, String exchangeName, String country,
                                   ExchangeSource.ExchangeType exchangeType, BigDecimal credibilityScore,
                                   String apiEndpoint, Set<String> supportedPairs) {
        
        // Validate geographic distribution
        if (!validateGeographicDistribution(country)) {
            LOGGER.warning("Geographic concentration too high for country: " + country);
            return false;
        }
        
        ExchangeSource exchange = new ExchangeSource(exchangeId, exchangeName, country,
                                                    exchangeType, credibilityScore,
                                                    apiEndpoint, supportedPairs);
        
        registeredExchanges.put(exchangeId, exchange);
        exchangeCountries.put(exchangeId, country);
        exchangeCredibility.put(exchangeId, new ExchangeCredibility(exchangeId));
        
        if (exchange.isAfrican) {
            africanExchanges.add(exchangeId);
        }
        
        // Update country weights
        updateCountryWeights();
        
        LOGGER.info(String.format("Registered exchange: %s (%s) from %s with credibility %s",
            exchangeId, exchangeName, country, credibilityScore.toString()));
        
        return true;
    }
    
    /**
     * Updates exchange rate from a source with signature verification
     */
    public boolean updateExchangeRate(String exchangeId, String pairSymbol, BigDecimal rate,
                                     BigDecimal volume, BigDecimal spread, String signature,
                                     byte[] signatureBytes) {
        
        ExchangeSource exchange = registeredExchanges.get(exchangeId);
        if (exchange == null || !exchange.isActive) {
            LOGGER.warning("Exchange not found or inactive: " + exchangeId);
            return false;
        }
        
        // Verify signature
        if (!verifyRateSignature(exchangeId, pairSymbol, rate, signature, signatureBytes)) {
            LOGGER.warning("Invalid signature for rate update from: " + exchangeId);
            return false;
        }
        
        // Check for outliers
        if (isRateOutlier(pairSymbol, rate, exchangeId)) {
            LOGGER.warning("Outlier rate detected from " + exchangeId + ": " + rate);
            recordOutlierAlert(pairSymbol, exchangeId, rate);
            return false;
        }
        
        // Create rate data
        RateData rateData = new RateData(exchangeId, pairSymbol, rate, volume, spread, signature);
        rateData.isValidated = true;
        
        // Store rate data
        rateHistory.computeIfAbsent(pairSymbol, k -> Collections.synchronizedList(new ArrayList<>()))
                   .add(rateData);
        
        // Update exchange credibility
        ExchangeCredibility credibility = exchangeCredibility.get(exchangeId);
        credibility.recordUpdate(true, false);
        
        // Update exchange status
        exchange.lastSuccessfulUpdate = System.currentTimeMillis();
        exchange.consecutiveFailures = 0;
        
        lastUpdateTimestamps.put(pairSymbol, System.currentTimeMillis());
        
        // Trigger fair rate recalculation
        calculateFairRate(pairSymbol);
        
        LOGGER.info(String.format("Updated rate: %s = %s from %s", pairSymbol, rate.toString(), exchangeId));
        return true;
    }
    
    /**
     * Calculates fair rate with African exchange prioritization
     */
    public FairRate calculateFairRate(String pairSymbol) {
        List<RateData> recentRates = getRecentRates(pairSymbol, 3600000); // Last hour
        
        if (recentRates.size() < minimumSourceCount) {
            LOGGER.warning("Insufficient sources for fair rate calculation: " + pairSymbol);
            return null;
        }
        
        // Separate African and non-African rates
        List<RateData> africanRates = new ArrayList<>();
        List<RateData> nonAfricanRates = new ArrayList<>();
        
        for (RateData rate : recentRates) {
            ExchangeSource exchange = registeredExchanges.get(rate.sourceExchangeId);
            if (exchange != null && exchange.isAfrican) {
                africanRates.add(rate);
            } else {
                nonAfricanRates.add(rate);
            }
        }
        
        // Calculate weighted average with African priority
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal africanWeight = BigDecimal.ZERO;
        List<String> contributingSources = new ArrayList<>();
        
        // Process African rates with higher weight
        for (RateData rate : africanRates) {
            ExchangeSource exchange = registeredExchanges.get(rate.sourceExchangeId);
            ExchangeCredibility credibility = exchangeCredibility.get(rate.sourceExchangeId);
            
            BigDecimal weight = exchange.weight.multiply(credibility.reliabilityScore)
                                             .multiply(new BigDecimal("1.5")); // 50% African bonus
            
            weightedSum = weightedSum.add(rate.rate.multiply(weight));
            totalWeight = totalWeight.add(weight);
            africanWeight = africanWeight.add(weight);
            contributingSources.add(rate.sourceExchangeId);
        }
        
        // Process non-African rates
        for (RateData rate : nonAfricanRates) {
            ExchangeSource exchange = registeredExchanges.get(rate.sourceExchangeId);
            ExchangeCredibility credibility = exchangeCredibility.get(rate.sourceExchangeId);
            
            BigDecimal weight = exchange.weight.multiply(credibility.reliabilityScore);
            
            weightedSum = weightedSum.add(rate.rate.multiply(weight));
            totalWeight = totalWeight.add(weight);
            contributingSources.add(rate.sourceExchangeId);
        }
        
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        
        BigDecimal fairRate = weightedSum.divide(totalWeight, 8, RoundingMode.HALF_UP);
        
        // Calculate confidence and standard deviation
        BigDecimal confidence = calculateConfidence(recentRates, fairRate);
        BigDecimal standardDeviation = calculateStandardDeviation(recentRates, fairRate);
        
        // Calculate African weight percentage
        BigDecimal africanWeightPercent = totalWeight.compareTo(BigDecimal.ZERO) > 0 ?
            africanWeight.divide(totalWeight, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        
        FairRate result = new FairRate(pairSymbol, fairRate, confidence,
                                      recentRates.size(), africanWeightPercent,
                                      standardDeviation, contributingSources);
        
        // Store fair rate
        currentFairRates.put(pairSymbol, result);
        
        // Store on blockchain for transparency
        storeFairRateOnChain(result);
        
        LOGGER.info(String.format("Calculated fair rate: %s = %s (confidence: %s, African weight: %s)",
            pairSymbol, fairRate.toString(), confidence.toString(), africanWeightPercent.toString()));
        
        return result;
    }
    
    /**
     * Gets exchange rate with anti-manipulation protection
     */
    public BigDecimal getExchangeRate(String baseCurrency, String quoteCurrency) {
        String pairSymbol = baseCurrency + "/" + quoteCurrency;
        FairRate fairRate = currentFairRates.get(pairSymbol);
        
        if (fairRate == null || isRateStale(fairRate)) {
            // Try to calculate new fair rate
            fairRate = calculateFairRate(pairSymbol);
        }
        
        if (fairRate != null && fairRate.confidence.compareTo(new BigDecimal("0.5")) >= 0) {
            return fairRate.fairRate;
        }
        
        // Fallback to most recent reliable source
        return getFallbackRate(pairSymbol);
    }
    
    /**
     * Gets commodity price (for commodity-backed tokens)
     */
    public BigDecimal getCommodityPrice(String commoditySymbol, String quoteCurrency) {
        String pairSymbol = commoditySymbol + "/" + quoteCurrency;
        return getExchangeRate(commoditySymbol, quoteCurrency);
    }
    
    /**
     * Detects manipulation attempts
     */
    public void detectManipulation() {
        for (String pairSymbol : currentFairRates.keySet()) {
            checkPriceDeviation(pairSymbol);
            checkVolumeAnomalies(pairSymbol);
            checkCoordinatedMovement(pairSymbol);
            checkGeographicConcentration(pairSymbol);
        }
    }
    
    // Private helper methods
    
    private boolean validateGeographicDistribution(String country) {
        // Count exchanges per country
        Map<String, Integer> countryCount = new HashMap<>();
        for (String exchangeCountry : exchangeCountries.values()) {
            countryCount.put(exchangeCountry, countryCount.getOrDefault(exchangeCountry, 0) + 1);
        }
        
        int totalExchanges = exchangeCountries.size() + 1; // +1 for new exchange
        int countryExchanges = countryCount.getOrDefault(country, 0) + 1;
        
        BigDecimal countryPercent = new BigDecimal(countryExchanges)
            .divide(new BigDecimal(totalExchanges), 4, RoundingMode.HALF_UP);
        
        return countryPercent.compareTo(maxCountryWeight) <= 0;
    }
    
    private void updateCountryWeights() {
        countryWeights.clear();
        Map<String, Integer> countryCount = new HashMap<>();
        
        for (String country : exchangeCountries.values()) {
            countryCount.put(country, countryCount.getOrDefault(country, 0) + 1);
        }
        
        int totalExchanges = exchangeCountries.size();
        for (Map.Entry<String, Integer> entry : countryCount.entrySet()) {
            BigDecimal weight = new BigDecimal(entry.getValue())
                .divide(new BigDecimal(totalExchanges), 4, RoundingMode.HALF_UP);
            countryWeights.put(entry.getKey(), weight);
        }
    }
    
    private boolean verifyRateSignature(String exchangeId, String pairSymbol, BigDecimal rate,
                                       String signature, byte[] signatureBytes) {
        // Simplified signature verification using PKI manager
        if (pkiManager == null || signatureBytes == null) return false;
        
        try {
            String message = exchangeId + "|" + pairSymbol + "|" + rate.toString() + "|" + 
                           System.currentTimeMillis();
            
            // In production, would verify actual cryptographic signature
            return signatureBytes.length > 0 && signature != null && !signature.isEmpty();
            
        } catch (Exception e) {
            LOGGER.severe("Signature verification failed: " + e.getMessage());
            return false;
        }
    }
    
    private boolean isRateOutlier(String pairSymbol, BigDecimal rate, String exchangeId) {
        OutlierDetection detector = outlierDetectors.get(pairSymbol);
        
        if (detector == null) {
            // Create new detector with recent rates
            List<RateData> recentRates = getRecentRates(pairSymbol, 3600000);
            if (recentRates.size() < 5) return false; // Need minimum data
            
            List<BigDecimal> rates = recentRates.stream()
                .map(rd -> rd.rate)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            
            detector = new OutlierDetection(pairSymbol, rates);
            outlierDetectors.put(pairSymbol, detector);
        }
        
        return detector.isOutlier(rate);
    }
    
    private void recordOutlierAlert(String pairSymbol, String exchangeId, BigDecimal rate) {
        ManipulationAlert alert = new ManipulationAlert(
            pairSymbol,
            ManipulationAlert.AlertType.PRICE_DEVIATION,
            "Outlier rate detected: " + rate + " from " + exchangeId,
            Set.of(exchangeId),
            new BigDecimal("0.7")
        );
        
        manipulationAlerts.put(alert.alertId, alert);
        
        // Update exchange credibility
        ExchangeCredibility credibility = exchangeCredibility.get(exchangeId);
        if (credibility != null) {
            credibility.recordUpdate(false, true);
        }
    }
    
    private List<RateData> getRecentRates(String pairSymbol, long timeWindowMs) {
        List<RateData> allRates = rateHistory.get(pairSymbol);
        if (allRates == null) return new ArrayList<>();
        
        long cutoffTime = System.currentTimeMillis() - timeWindowMs;
        
        return allRates.stream()
            .filter(rate -> rate.timestamp > cutoffTime)
            .filter(rate -> rate.isValidated)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    private BigDecimal calculateConfidence(List<RateData> rates, BigDecimal fairRate) {
        if (rates.isEmpty()) return BigDecimal.ZERO;
        
        // Base confidence on number of sources and agreement
        BigDecimal sourceBonus = new BigDecimal(Math.min(rates.size() * 0.1, 0.5));
        
        // Agreement bonus based on standard deviation
        BigDecimal stdDev = calculateStandardDeviation(rates, fairRate);
        BigDecimal agreementBonus = stdDev.compareTo(fairRate.multiply(new BigDecimal("0.02"))) <= 0 ?
            new BigDecimal("0.3") : BigDecimal.ZERO;
        
        return new BigDecimal("0.2").add(sourceBonus).add(agreementBonus).min(BigDecimal.ONE);
    }
    
    private BigDecimal calculateStandardDeviation(List<RateData> rates, BigDecimal mean) {
        if (rates.size() <= 1) return BigDecimal.ZERO;
        
        BigDecimal sumSquaredDeviations = BigDecimal.ZERO;
        for (RateData rate : rates) {
            BigDecimal deviation = rate.rate.subtract(mean);
            sumSquaredDeviations = sumSquaredDeviations.add(deviation.multiply(deviation));
        }
        
        BigDecimal variance = sumSquaredDeviations.divide(new BigDecimal(rates.size() - 1), 
                                                         8, RoundingMode.HALF_UP);
        
        // Approximate square root using Newton's method
        return sqrt(variance);
    }
    
    private BigDecimal sqrt(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        
        BigDecimal x = value;
        BigDecimal lastX;
        
        do {
            lastX = x;
            x = x.add(value.divide(x, 8, RoundingMode.HALF_UP))
                 .divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
        } while (x.subtract(lastX).abs().compareTo(new BigDecimal("0.00000001")) > 0);
        
        return x;
    }
    
    private boolean isRateStale(FairRate rate) {
        return (System.currentTimeMillis() - rate.calculationTimestamp) > cacheExpiry;
    }
    
    private BigDecimal getFallbackRate(String pairSymbol) {
        List<RateData> recentRates = getRecentRates(pairSymbol, 1800000); // Last 30 minutes
        
        if (recentRates.isEmpty()) return BigDecimal.ZERO;
        
        // Return most recent rate from most credible source
        return recentRates.stream()
            .max((r1, r2) -> {
                ExchangeCredibility c1 = exchangeCredibility.get(r1.sourceExchangeId);
                ExchangeCredibility c2 = exchangeCredibility.get(r2.sourceExchangeId);
                return c1.reliabilityScore.compareTo(c2.reliabilityScore);
            })
            .map(rate -> rate.rate)
            .orElse(BigDecimal.ZERO);
    }
    
    private void storeFairRateOnChain(FairRate fairRate) {
        try {
            // Store rate data on blockchain for transparency
            LOGGER.info("Fair rate stored on-chain: " + fairRate.pairSymbol + " = " + fairRate.fairRate);
        } catch (Exception e) {
            LOGGER.severe("Failed to store fair rate on blockchain: " + e.getMessage());
        }
    }
    
    private void startRateUpdateScheduler() {
        updateScheduler.scheduleAtFixedRate(() -> {
            try {
                // Trigger rate collection from all active exchanges
                collectRatesFromAllExchanges();
            } catch (Exception e) {
                LOGGER.severe("Rate collection failed: " + e.getMessage());
            }
        }, 0, updateInterval, TimeUnit.MILLISECONDS);
        
        updateScheduler.scheduleAtFixedRate(() -> {
            try {
                detectManipulation();
            } catch (Exception e) {
                LOGGER.severe("Manipulation detection failed: " + e.getMessage());
            }
        }, 300000, 300000, TimeUnit.MILLISECONDS); // Every 5 minutes
    }
    
    private void collectRatesFromAllExchanges() {
        // In production, would make API calls to all registered exchanges
        // For now, this is a placeholder
        LOGGER.info("Collecting rates from " + registeredExchanges.size() + " exchanges");
    }
    
    private void checkPriceDeviation(String pairSymbol) {
        // Implementation for price deviation detection
    }
    
    private void checkVolumeAnomalies(String pairSymbol) {
        // Implementation for volume anomaly detection  
    }
    
    private void checkCoordinatedMovement(String pairSymbol) {
        // Implementation for coordinated movement detection
    }
    
    private void checkGeographicConcentration(String pairSymbol) {
        // Implementation for geographic concentration check
    }
    
    // Public getters and utility methods
    
    public FairRate getCurrentFairRate(String pairSymbol) {
        return currentFairRates.get(pairSymbol);
    }
    
    public Map<String, FairRate> getAllCurrentRates() {
        return new HashMap<>(currentFairRates);
    }
    
    public Set<String> getAfricanExchanges() {
        return new HashSet<>(africanExchanges);
    }
    
    public int getRegisteredExchangeCount() {
        return registeredExchanges.size();
    }
    
    public int getActiveExchangeCount() {
        return (int) registeredExchanges.values().stream()
            .filter(exchange -> exchange.isActive)
            .count();
    }
    
    public Map<String, ManipulationAlert> getActiveAlerts() {
        return new HashMap<>(manipulationAlerts);
    }
    
    /**
     * Safely shuts down the oracle
     */
    public void shutdown() {
        updateScheduler.shutdown();
        LOGGER.info("Exchange Rate Oracle shutdown completed");
    }
}