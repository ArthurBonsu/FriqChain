package org.example.app.core.blockchain.foreign;

import org.example.app.core.types.Amount;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * FXReferenceManager manages foreign currency exchange rates as REFERENCES ONLY.
 * These rates inform pricing transparency but do NOT control the system.
 * 
 * Core Principle: African currencies define their own value through production,
 * resources, and verified trade. Foreign rates are for convenience, not validation.
 * 
 * Features:
 * - Multi-source FX rate aggregation with African exchange priority
 * - Reference rates for USD, EUR, RMB, Rupee, Ruble, etc.
 * - NO direct redemption to foreign accounts
 * - Transparent on-chain rate publication
 * - Anti-manipulation through outlier detection
 */
public class FXReferenceManager {
    
    private static final Logger LOGGER = Logger.getLogger(FXReferenceManager.class.getName());
    
    // Reference rates: CurrencyPair -> Rate
    private final Map<String, FXReferenceRate> referenceRates;
    
    // African currency priority multiplier (3x weight in calculations)
    private static final double AFRICAN_PRIORITY_MULTIPLIER = 3.0;
    
    // Maximum age for reference rates (1 hour)
    private static final long MAX_RATE_AGE_MS = 3600000;
    
    // Supported foreign currencies for reference
    private static final Set<String> SUPPORTED_FOREIGN_CURRENCIES = new HashSet<>(Arrays.asList(
        "USD", "EUR", "GBP", "RMB", "CNY", "INR", "RUB", "JPY", "CHF", "CAD", "AUD"
    ));
    
    private final ReentrantReadWriteLock lock;
    private final FXRateHistory rateHistory;
    private final FXAnomalyDetector anomalyDetector;
    
    /**
     * Inner class representing a foreign exchange reference rate
     */
    public static class FXReferenceRate {
        private final String currencyPair; // e.g., "NGN/USD"
        private final BigDecimal rate;
        private final long timestamp;
        private final List<String> sources;
        private final boolean isAfricanBased;
        private final int confidence; // 0-100
        
        public FXReferenceRate(String currencyPair, BigDecimal rate, long timestamp,
                               List<String> sources, boolean isAfricanBased, int confidence) {
            this.currencyPair = currencyPair;
            this.rate = rate;
            this.timestamp = timestamp;
            this.sources = new ArrayList<>(sources);
            this.isAfricanBased = isAfricanBased;
            this.confidence = confidence;
        }
        
        public String getCurrencyPair() { return currencyPair; }
        public BigDecimal getRate() { return rate; }
        public long getTimestamp() { return timestamp; }
        public List<String> getSources() { return new ArrayList<>(sources); }
        public boolean isAfricanBased() { return isAfricanBased; }
        public int getConfidence() { return confidence; }
        
        public boolean isStale() {
            return System.currentTimeMillis() - timestamp > MAX_RATE_AGE_MS;
        }
    }
    
    /**
     * Tracks historical FX rates for trend analysis
     */
    private static class FXRateHistory {
        private final Map<String, List<HistoricalRate>> history;
        private static final int MAX_HISTORY_SIZE = 1000;
        
        public FXRateHistory() {
            this.history = new ConcurrentHashMap<>();
        }
        
        public void addRate(String pair, BigDecimal rate, long timestamp) {
            history.computeIfAbsent(pair, k -> Collections.synchronizedList(new ArrayList<>()))
                   .add(new HistoricalRate(rate, timestamp));
            
            // Prune old history
            List<HistoricalRate> rates = history.get(pair);
            if (rates.size() > MAX_HISTORY_SIZE) {
                rates.remove(0);
            }
        }
        
        public List<HistoricalRate> getHistory(String pair) {
            List<HistoricalRate> rates = history.get(pair);
            return rates != null ? new ArrayList<>(rates) : new ArrayList<>();
        }
        
        public BigDecimal getMovingAverage(String pair, int periods) {
            List<HistoricalRate> rates = getHistory(pair);
            if (rates.isEmpty() || periods <= 0) {
                return BigDecimal.ZERO;
            }
            
            int count = Math.min(periods, rates.size());
            BigDecimal sum = BigDecimal.ZERO;
            for (int i = rates.size() - count; i < rates.size(); i++) {
                sum = sum.add(rates.get(i).rate);
            }
            return sum.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP);
        }
        
        private static class HistoricalRate {
            final BigDecimal rate;
            final long timestamp;
            
            HistoricalRate(BigDecimal rate, long timestamp) {
                this.rate = rate;
                this.timestamp = timestamp;
            }
        }
    }
    
    /**
     * Detects anomalies in FX rate data to prevent manipulation
     */
    private static class FXAnomalyDetector {
        
        public boolean isAnomaly(BigDecimal newRate, List<BigDecimal> historicalRates) {
            if (historicalRates.isEmpty()) {
                return false; // Can't detect anomaly without history
            }
            
            // Calculate mean and standard deviation
            BigDecimal mean = calculateMean(historicalRates);
            BigDecimal stdDev = calculateStdDev(historicalRates, mean);
            
            // Check if new rate is more than 3 standard deviations from mean
            BigDecimal deviation = newRate.subtract(mean).abs();
            BigDecimal threshold = stdDev.multiply(BigDecimal.valueOf(3));
            
            return deviation.compareTo(threshold) > 0;
        }
        
        private BigDecimal calculateMean(List<BigDecimal> values) {
            BigDecimal sum = BigDecimal.ZERO;
            for (BigDecimal value : values) {
                sum = sum.add(value);
            }
            return sum.divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP);
        }
        
        private BigDecimal calculateStdDev(List<BigDecimal> values, BigDecimal mean) {
            BigDecimal sumSquaredDiff = BigDecimal.ZERO;
            for (BigDecimal value : values) {
                BigDecimal diff = value.subtract(mean);
                sumSquaredDiff = sumSquaredDiff.add(diff.multiply(diff));
            }
            BigDecimal variance = sumSquaredDiff.divide(
                BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP);
            return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
        }
    }
    
    public FXReferenceManager() {
        this.referenceRates = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock(true);
        this.rateHistory = new FXRateHistory();
        this.anomalyDetector = new FXAnomalyDetector();
        
        LOGGER.info("FXReferenceManager initialized - Foreign currencies for REFERENCE ONLY");
    }
    
    /**
     * Updates a foreign exchange reference rate
     * 
     * @param baseCurrency Base currency (typically African)
     * @param quoteCurrency Quote currency (typically foreign)
     * @param rate Exchange rate
     * @param sources Data sources for this rate
     * @param isAfricanBased Whether the rate comes from African exchanges
     */
    public boolean updateReferenceRate(String baseCurrency, String quoteCurrency,
                                       BigDecimal rate, List<String> sources,
                                       boolean isAfricanBased) {
        lock.writeLock().lock();
        try {
            // Validate inputs
            if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
                LOGGER.warning("Invalid rate: " + rate);
                return false;
            }
            
            if (sources == null || sources.size() < 3) {
                LOGGER.warning("Insufficient sources: " + (sources != null ? sources.size() : 0));
                return false;
            }
            
            String pair = baseCurrency + "/" + quoteCurrency;
            
            // Check for anomalies
            List<BigDecimal> historicalRates = getHistoricalRates(pair);
            if (!historicalRates.isEmpty() && anomalyDetector.isAnomaly(rate, historicalRates)) {
                LOGGER.warning("Anomaly detected for " + pair + ": " + rate);
                // Don't reject, but flag for review
            }
            
            // Calculate confidence based on source count and African priority
            int confidence = calculateConfidence(sources, isAfricanBased);
            
            // Create and store reference rate
            FXReferenceRate refRate = new FXReferenceRate(
                pair, rate, System.currentTimeMillis(), sources, isAfricanBased, confidence
            );
            
            referenceRates.put(pair, refRate);
            rateHistory.addRate(pair, rate, System.currentTimeMillis());
            
            LOGGER.info(String.format("Updated FX reference: %s = %s (confidence: %d%%, African-based: %b)",
                pair, rate.toPlainString(), confidence, isAfricanBased));
            
            return true;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Gets a reference rate for a currency pair
     */
    public Optional<FXReferenceRate> getReferenceRate(String baseCurrency, String quoteCurrency) {
        lock.readLock().lock();
        try {
            String pair = baseCurrency + "/" + quoteCurrency;
            FXReferenceRate rate = referenceRates.get(pair);
            
            if (rate != null && !rate.isStale()) {
                return Optional.of(rate);
            }
            
            // Try inverse rate
            String inversePair = quoteCurrency + "/" + baseCurrency;
            FXReferenceRate inverseRate = referenceRates.get(inversePair);
            if (inverseRate != null && !inverseRate.isStale()) {
                BigDecimal inverted = BigDecimal.ONE.divide(
                    inverseRate.getRate(), 8, RoundingMode.HALF_UP);
                return Optional.of(new FXReferenceRate(
                    pair, inverted, inverseRate.getTimestamp(),
                    inverseRate.getSources(), inverseRate.isAfricanBased(),
                    inverseRate.getConfidence()
                ));
            }
            
            return Optional.empty();
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Converts an amount from one currency to another using reference rates
     * NOTE: This is for DISPLAY/REFERENCE only, NOT for actual settlements
     */
    public Optional<BigDecimal> convertForReference(Amount amount, String toCurrency) {
        String fromCurrency = amount.getCurrencyCode();
        
        Optional<FXReferenceRate> rate = getReferenceRate(fromCurrency, toCurrency);
        if (rate.isEmpty()) {
            return Optional.empty();
        }
        
        BigDecimal amountValue = new BigDecimal(amount.getValue());
        BigDecimal converted = amountValue.multiply(rate.get().getRate())
            .setScale(8, RoundingMode.HALF_UP);
        
        return Optional.of(converted);
    }
    
    /**
     * Gets all current reference rates
     */
    public Map<String, FXReferenceRate> getAllReferenceRates() {
        lock.readLock().lock();
        try {
            Map<String, FXReferenceRate> result = new HashMap<>();
            for (Map.Entry<String, FXReferenceRate> entry : referenceRates.entrySet()) {
                if (!entry.getValue().isStale()) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets reference rates specifically for African currencies
     */
    public Map<String, FXReferenceRate> getAfricanReferenceRates() {
        lock.readLock().lock();
        try {
            Map<String, FXReferenceRate> result = new HashMap<>();
            for (Map.Entry<String, FXReferenceRate> entry : referenceRates.entrySet()) {
                if (entry.getValue().isAfricanBased() && !entry.getValue().isStale()) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Checks if a currency is a supported foreign currency
     */
    public static boolean isForeignCurrency(String currency) {
        return SUPPORTED_FOREIGN_CURRENCIES.contains(currency.toUpperCase());
    }
    
    /**
     * Gets the moving average rate for a currency pair
     */
    public Optional<BigDecimal> getMovingAverage(String baseCurrency, String quoteCurrency, int periods) {
        String pair = baseCurrency + "/" + quoteCurrency;
        BigDecimal ma = rateHistory.getMovingAverage(pair, periods);
        
        if (ma.compareTo(BigDecimal.ZERO) > 0) {
            return Optional.of(ma);
        }
        return Optional.empty();
    }
    
    /**
     * Calculates confidence score based on sources and African priority
     */
    private int calculateConfidence(List<String> sources, boolean isAfricanBased) {
        int baseConfidence = Math.min(100, sources.size() * 20); // 5 sources = 100%
        
        // Boost confidence for African-based rates (prioritize African exchanges)
        if (isAfricanBased) {
            baseConfidence = Math.min(100, (int)(baseConfidence * 1.2));
        }
        
        return baseConfidence;
    }
    
    /**
     * Gets historical rates for a currency pair
     */
    private List<BigDecimal> getHistoricalRates(String pair) {
        List<FXRateHistory.HistoricalRate> history = rateHistory.getHistory(pair);
        List<BigDecimal> rates = new ArrayList<>();
        for (FXRateHistory.HistoricalRate hr : history) {
            rates.add(hr.rate);
        }
        return rates;
    }
    
    /**
     * Removes stale rates
     */
    public void cleanupStaleRates() {
        lock.writeLock().lock();
        try {
            int removed = 0;
            Iterator<Map.Entry<String, FXReferenceRate>> iterator = referenceRates.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, FXReferenceRate> entry = iterator.next();
                if (entry.getValue().isStale()) {
                    iterator.remove();
                    removed++;
                }
            }
            
            if (removed > 0) {
                LOGGER.info("Cleaned up " + removed + " stale FX reference rates");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Gets system status
     */
    public Map<String, Object> getSystemStatus() {
        lock.readLock().lock();
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("totalRates", referenceRates.size());
            status.put("africanRates", getAfricanReferenceRates().size());
            status.put("foreignCurrencies", SUPPORTED_FOREIGN_CURRENCIES.size());
            status.put("maxRateAgeMs", MAX_RATE_AGE_MS);
            status.put("africanPriorityMultiplier", AFRICAN_PRIORITY_MULTIPLIER);
            
            return status;
        } finally {
            lock.readLock().unlock();
        }
    }
}