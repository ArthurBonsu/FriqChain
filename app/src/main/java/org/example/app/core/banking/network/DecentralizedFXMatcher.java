package org.example.app.core.banking.network;

import java.util.stream.Collectors;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DecentralizedFXMatcher implements the Fair-Rate Algorithm for currency exchanges
 * with priority for African exchanges (50-60% bonus over global alternatives).
 * 
 * Core Philosophy:
 * - Africa should NOT depend on USD/EUR validation
 * - Value established through verified production, commodity reserves, and trade
 * - Multi-source verification prevents manipulation
 * - Statistical analysis (Z-scores, MAD) for outlier detection
 * 
 * Key Features:
 * - Fair weighting: 50-60% bonus for African financial institutions
 * - Multi-source aggregation (African exchanges, global exchanges, oracles)
 * - Anti-manipulation via outlier detection
 * - Real-time rate calculation
 */
public class DecentralizedFXMatcher {
    private static final Logger logger = Logger.getLogger(DecentralizedFXMatcher.class.getName());
    
    // Fair weighting configuration
    private static final double AFRICAN_EXCHANGE_WEIGHT = 1.55; // 55% bonus (1.0 + 0.55)
    private static final double GLOBAL_EXCHANGE_WEIGHT = 1.0;
    private static final double MINIMUM_SOURCES = 3; // Minimum data sources required
    
    // Anti-manipulation thresholds
    private static final double Z_SCORE_THRESHOLD = 3.0; // 3 standard deviations
    private static final double MAD_THRESHOLD = 3.0; // Median Absolute Deviation threshold
    
    // Exchange data
    private final Map<String, ExchangeSource> exchangeSources;
    private final Map<String, List<RateQuote>> recentQuotes; // currency pair → quotes
    private final Map<String, FairRate> fairRates; // currency pair → fair rate
    
    // Rate calculation
    private final ScheduledExecutorService rateCalculator;
    private static final long RATE_UPDATE_INTERVAL_MS = 5000; // 5 seconds
    
    /**
     * Exchange source
     */
    public static class ExchangeSource {
        private final String sourceId;
        private final String name;
        private final SourceType type;
        private final double weight;
        private double reputationScore;
        private long totalQuotes;
        private long rejectedQuotes;
        private boolean isActive;
        
        public ExchangeSource(String sourceId, String name, SourceType type) {
            this.sourceId = sourceId;
            this.name = name;
            this.type = type;
            this.weight = type == SourceType.AFRICAN_EXCHANGE ? 
                AFRICAN_EXCHANGE_WEIGHT : GLOBAL_EXCHANGE_WEIGHT;
            this.reputationScore = 1.0;
            this.totalQuotes = 0;
            this.rejectedQuotes = 0;
            this.isActive = true;
        }
        
        public String getSourceId() { return sourceId; }
        public String getName() { return name; }
        public SourceType getType() { return type; }
        public double getWeight() { return weight * reputationScore; }
        public double getReputationScore() { return reputationScore; }
        public boolean isActive() { return isActive; }
        
        public void recordQuote(boolean accepted) {
            totalQuotes++;
            if (!accepted) {
                rejectedQuotes++;
                // Reduce reputation if too many rejections
                if (rejectedQuotes > totalQuotes * 0.1) { // >10% rejection rate
                    reputationScore = Math.max(0.5, reputationScore - 0.05);
                }
            }
        }
        
        public void setActive(boolean active) { this.isActive = active; }
    }
    
    /**
     * Source type
     */
    public enum SourceType {
        AFRICAN_EXCHANGE,   // African exchange (priority)
        GLOBAL_EXCHANGE,    // Global exchange
        ORACLE,             // External oracle
        BANK_FEED          // Bank settlement feed
    }
    
    /**
     * Rate quote from an exchange
     */
    public static class RateQuote {
        private final String sourceId;
        private final String currencyPair;
        private final BigDecimal rate;
        private final long timestamp;
        private final BigDecimal volume;
        
        public RateQuote(String sourceId, String currencyPair, BigDecimal rate, BigDecimal volume) {
            this.sourceId = sourceId;
            this.currencyPair = currencyPair;
            this.rate = rate;
            this.timestamp = System.currentTimeMillis();
            this.volume = volume;
        }
        
        public String getSourceId() { return sourceId; }
        public String getCurrencyPair() { return currencyPair; }
        public BigDecimal getRate() { return rate; }
        public long getTimestamp() { return timestamp; }
        public BigDecimal getVolume() { return volume; }
    }
    
    /**
     * Fair rate calculation result
     */
    public static class FairRate {
        private final String currencyPair;
        private final BigDecimal rate;
        private final long timestamp;
        private final int sourcesUsed;
        private final BigDecimal confidence;
        private final List<String> outliersSources; // Sources rejected as outliers
        
        public FairRate(String currencyPair, BigDecimal rate, int sourcesUsed, 
                       BigDecimal confidence, List<String> outliers) {
            this.currencyPair = currencyPair;
            this.rate = rate;
            this.timestamp = System.currentTimeMillis();
            this.sourcesUsed = sourcesUsed;
            this.confidence = confidence;
            this.outliersSources = new ArrayList<>(outliers);
        }
        
        public String getCurrencyPair() { return currencyPair; }
        public BigDecimal getRate() { return rate; }
        public long getTimestamp() { return timestamp; }
        public int getSourcesUsed() { return sourcesUsed; }
        public BigDecimal getConfidence() { return confidence; }
        public List<String> getOutliersSources() { return new ArrayList<>(outliersSources); }
    }
    
    /**
     * Constructor
     */
    public DecentralizedFXMatcher() {
        this.exchangeSources = new ConcurrentHashMap<>();
        this.recentQuotes = new ConcurrentHashMap<>();
        this.fairRates = new ConcurrentHashMap<>();
        this.rateCalculator = Executors.newScheduledThreadPool(2);
        
        logger.info("DecentralizedFXMatcher initialized with African priority weighting");
    }
    
    /**
     * Registers an exchange source
     */
    public void registerExchange(String sourceId, String name, SourceType type) {
        ExchangeSource source = new ExchangeSource(sourceId, name, type);
        exchangeSources.put(sourceId, source);
        
        logger.info("Registered exchange: " + name + " (type: " + type + 
            ", weight: " + String.format("%.2f", source.getWeight()) + ")");
    }
    
    /**
     * Submits a rate quote
     */
    public void submitQuote(String sourceId, String currencyPair, BigDecimal rate, BigDecimal volume) {
        ExchangeSource source = exchangeSources.get(sourceId);
        if (source == null || !source.isActive()) {
            logger.warning("Quote rejected: unknown or inactive source " + sourceId);
            return;
        }
        
        RateQuote quote = new RateQuote(sourceId, currencyPair, rate, volume);
        
        recentQuotes.computeIfAbsent(currencyPair, k -> new CopyOnWriteArrayList<>())
            .add(quote);
        
        // Cleanup old quotes (older than 1 minute)
        cleanupOldQuotes(currencyPair);
        
        logger.fine("Received quote from " + source.getName() + ": " + 
            currencyPair + " = " + rate);
    }
    
    /**
     * Cleans up old quotes
     */
    private void cleanupOldQuotes(String currencyPair) {
        List<RateQuote> quotes = recentQuotes.get(currencyPair);
        if (quotes != null) {
            long cutoffTime = System.currentTimeMillis() - 60000; // 1 minute
            quotes.removeIf(q -> q.getTimestamp() < cutoffTime);
        }
    }
    
    /**
     * Starts automatic rate calculation
     */
    public void startRateCalculation() {
        rateCalculator.scheduleAtFixedRate(
            this::calculateAllFairRates,
            0,
            RATE_UPDATE_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        logger.info("Started automatic fair rate calculation");
    }
    
    /**
     * Calculates fair rates for all currency pairs
     */
    private void calculateAllFairRates() {
        try {
            for (String currencyPair : recentQuotes.keySet()) {
                FairRate fairRate = calculateFairRate(currencyPair);
                if (fairRate != null) {
                    fairRates.put(currencyPair, fairRate);
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error calculating fair rates", e);
        }
    }
    
    /**
     * Calculates fair rate for a currency pair using weighted aggregation
     */
    public FairRate calculateFairRate(String currencyPair) {
        List<RateQuote> quotes = recentQuotes.get(currencyPair);
        if (quotes == null || quotes.size() < MINIMUM_SOURCES) {
            logger.fine("Insufficient quotes for " + currencyPair + ": " + 
                (quotes != null ? quotes.size() : 0));
            return null;
        }
        
        // Step 1: Remove outliers using Z-score and MAD
        List<RateQuote> validQuotes = removeOutliers(quotes);
        List<String> outlierSources = new ArrayList<>();
        
        for (RateQuote quote : quotes) {
            if (!validQuotes.contains(quote)) {
                outlierSources.add(quote.getSourceId());
                
                ExchangeSource source = exchangeSources.get(quote.getSourceId());
                if (source != null) {
                    source.recordQuote(false); // Rejected
                }
            }
        }
        
        if (validQuotes.size() < MINIMUM_SOURCES) {
            logger.warning("Too many outliers for " + currencyPair);
            return null;
        }
        
        // Step 2: Calculate weighted average with African priority
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        
        for (RateQuote quote : validQuotes) {
            ExchangeSource source = exchangeSources.get(quote.getSourceId());
            if (source != null) {
                BigDecimal weight = BigDecimal.valueOf(source.getWeight());
                weightedSum = weightedSum.add(quote.getRate().multiply(weight));
                totalWeight = totalWeight.add(weight);
                
                source.recordQuote(true); // Accepted
            }
        }
        
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        
        BigDecimal fairRate = weightedSum.divide(totalWeight, 18, RoundingMode.HALF_UP);
        
        // Step 3: Calculate confidence based on source agreement
        BigDecimal confidence = calculateConfidence(validQuotes, fairRate);
        
        FairRate result = new FairRate(currencyPair, fairRate, validQuotes.size(), 
            confidence, outlierSources);
        
        logger.fine("Calculated fair rate for " + currencyPair + ": " + fairRate + 
            " (confidence: " + String.format("%.2f%%", confidence.multiply(BigDecimal.valueOf(100))) + 
            ", sources: " + validQuotes.size() + ")");
        
        return result;
    }
    
    /**
     * Removes outliers using Z-score and MAD analysis
     */
    private List<RateQuote> removeOutliers(List<RateQuote> quotes) {
        if (quotes.size() < 3) {
            return new ArrayList<>(quotes);
        }
        
        // Extract rates
        List<BigDecimal> rates = quotes.stream()
            .map(RateQuote::getRate)
            .collect(Collectors.toList());
        
        // Calculate mean and median
        BigDecimal mean = rates.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(rates.size()), 18, RoundingMode.HALF_UP);
        
        List<BigDecimal> sortedRates = new ArrayList<>(rates);
        Collections.sort(sortedRates);
        BigDecimal median = sortedRates.get(sortedRates.size() / 2);
        
        // Calculate standard deviation for Z-score
        BigDecimal variance = rates.stream()
            .map(r -> r.subtract(mean).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(rates.size()), 18, RoundingMode.HALF_UP);
        BigDecimal stdDev = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
        
        // Calculate MAD (Median Absolute Deviation)
        List<BigDecimal> deviations = rates.stream()
            .map(r -> r.subtract(median).abs())
            .collect(Collectors.toList());
        Collections.sort(deviations);
        BigDecimal mad = deviations.get(deviations.size() / 2);
        
        // Filter outliers
        List<RateQuote> validQuotes = new ArrayList<>();
        for (int i = 0; i < quotes.size(); i++) {
            RateQuote quote = quotes.get(i);
            BigDecimal rate = rates.get(i);
            
            // Z-score test
            double zScore = rate.subtract(mean).abs()
                .divide(stdDev.max(BigDecimal.valueOf(0.000001)), 18, RoundingMode.HALF_UP)
                .doubleValue();
            
            // MAD test
            double madScore = rate.subtract(median).abs()
                .divide(mad.max(BigDecimal.valueOf(0.000001)), 18, RoundingMode.HALF_UP)
                .doubleValue();
            
            // Accept if passes both tests
            if (zScore <= Z_SCORE_THRESHOLD && madScore <= MAD_THRESHOLD) {
                validQuotes.add(quote);
            } else {
                logger.fine("Rejected outlier from " + quote.getSourceId() + 
                    ": rate=" + rate + ", z-score=" + String.format("%.2f", zScore) +
                    ", mad-score=" + String.format("%.2f", madScore));
            }
        }
        
        return validQuotes;
    }
    
    /**
     * Calculates confidence based on source agreement
     */
    private BigDecimal calculateConfidence(List<RateQuote> quotes, BigDecimal fairRate) {
        if (quotes.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        // Calculate average deviation from fair rate
        BigDecimal totalDeviation = quotes.stream()
            .map(q -> q.getRate().subtract(fairRate).abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal avgDeviation = totalDeviation.divide(
            BigDecimal.valueOf(quotes.size()), 18, RoundingMode.HALF_UP);
        
        // Convert to confidence (lower deviation = higher confidence)
        // confidence = 1 / (1 + avgDeviation)
        BigDecimal confidence = BigDecimal.ONE.divide(
            BigDecimal.ONE.add(avgDeviation), 18, RoundingMode.HALF_UP);
        
        return confidence;
    }
    
    /**
     * Gets current fair rate for a currency pair
     */
    public FairRate getFairRate(String currencyPair) {
        return fairRates.get(currencyPair);
    }
    
    /**
     * Gets all fair rates
     */
    public Map<String, FairRate> getAllFairRates() {
        return new HashMap<>(fairRates);
    }
    
    /**
     * Gets exchange source
     */
    public ExchangeSource getExchangeSource(String sourceId) {
        return exchangeSources.get(sourceId);
    }
    
    /**
     * Gets matcher statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalExchanges", exchangeSources.size());
        stats.put("africanExchanges", exchangeSources.values().stream()
            .filter(s -> s.getType() == SourceType.AFRICAN_EXCHANGE)
            .count());
        stats.put("activePairs", fairRates.size());
        stats.put("totalQuotes", recentQuotes.values().stream()
            .mapToInt(List::size)
            .sum());
        
        // Average confidence
        double avgConfidence = fairRates.values().stream()
            .mapToDouble(fr -> fr.getConfidence().doubleValue())
            .average()
            .orElse(0.0);
        stats.put("averageConfidence", String.format("%.2f%%", avgConfidence * 100));
        
        return stats;
    }
    
    /**
     * Shuts down the matcher
     */
    public void shutdown() {
        logger.info("Shutting down DecentralizedFXMatcher");
        
        rateCalculator.shutdown();
        try {
            if (!rateCalculator.awaitTermination(10, TimeUnit.SECONDS)) {
                rateCalculator.shutdownNow();
            }
        } catch (InterruptedException e) {
            rateCalculator.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("DecentralizedFXMatcher shut down");
    }
}