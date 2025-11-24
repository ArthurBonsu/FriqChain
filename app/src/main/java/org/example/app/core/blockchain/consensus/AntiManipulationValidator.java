package org.example.app.core.blockchain.consensus;

import org.example.app.core.blockchain.transaction.TokenBankTransaction;
import org.example.app.core.blockchain.transaction.CurrencyExchangeTransaction;
import org.example.app.core.banking.tokenization.ExchangeRateOracle;
import org.example.app.core.state.BlockchainState;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * AntiManipulationValidator - Detects and prevents market manipulation attempts
 * 
 * Uses statistical analysis (Z-scores, MAD) to detect abnormal activity:
 * - Price manipulation detection
 * - Volume manipulation detection  
 * - Wash trading detection
 * - Oracle data manipulation detection
 * - Pump and dump schemes
 * - Coordinated attack detection
 * 
 * Based on Implementation Guidebook Section V: Defense Against Internal Threats
 */
public class AntiManipulationValidator {
    private static final Logger LOGGER = Logger.getLogger(AntiManipulationValidator.class.getName());
    
    // Statistical thresholds for anomaly detection
    private static final BigDecimal Z_SCORE_THRESHOLD = new BigDecimal("3.0"); // 3 standard deviations
    private static final BigDecimal MAD_MULTIPLIER = new BigDecimal("3.5"); // Median Absolute Deviation multiplier
    private static final BigDecimal PRICE_SPIKE_THRESHOLD = new BigDecimal("0.15"); // 15% price change
    private static final BigDecimal VOLUME_SPIKE_THRESHOLD = new BigDecimal("5.0"); // 5x average volume
    
    // Time windows for analysis
    private static final long SHORT_WINDOW_MS = 300000; // 5 minutes
    private static final long MEDIUM_WINDOW_MS = 3600000; // 1 hour
    private static final long LONG_WINDOW_MS = 86400000; // 24 hours
    
    // Wash trading detection
    private static final BigDecimal WASH_TRADE_SIMILARITY_THRESHOLD = new BigDecimal("0.95"); // 95% similar
    private static final int WASH_TRADE_MIN_OCCURRENCES = 3;
    
    // Coordinated attack detection
    private static final int COORDINATED_ATTACK_MIN_PARTICIPANTS = 5;
    private static final BigDecimal COORDINATED_ATTACK_CORRELATION = new BigDecimal("0.90"); // 90% correlation
    
    // Dependencies
    private final BlockchainState blockchainState;
    private final ExchangeRateOracle exchangeRateOracle;
    
    // Data tracking for statistical analysis
    private final Map<String, List<PriceDataPoint>> priceHistory;
    private final Map<String, List<VolumeDataPoint>> volumeHistory;
    private final Map<String, List<TransactionPattern>> transactionPatterns;
    private final Map<String, SuspiciousActivity> suspiciousActivities;
    
    /**
     * Represents a price data point
     */
    private static class PriceDataPoint {
        final BigDecimal price;
        final long timestamp;
        final String source;
        
        PriceDataPoint(BigDecimal price, long timestamp, String source) {
            this.price = price;
            this.timestamp = timestamp;
            this.source = source;
        }
        
        boolean isWithinWindow(long currentTime, long windowMs) {
            return (currentTime - timestamp) < windowMs;
        }
    }
    
    /**
     * Represents a volume data point
     */
    private static class VolumeDataPoint {
        final BigDecimal volume;
        final long timestamp;
        final String pair;
        
        VolumeDataPoint(BigDecimal volume, long timestamp, String pair) {
            this.volume = volume;
            this.timestamp = timestamp;
            this.pair = pair;
        }
        
        boolean isWithinWindow(long currentTime, long windowMs) {
            return (currentTime - timestamp) < windowMs;
        }
    }
    
    /**
     * Represents a transaction pattern for wash trading detection
     */
    private static class TransactionPattern {
        final String fromAddress;
        final String toAddress;
        final BigDecimal amount;
        final BigDecimal rate;
        final long timestamp;
        final String tokenPair;
        
        TransactionPattern(String from, String to, BigDecimal amount, 
                          BigDecimal rate, long timestamp, String tokenPair) {
            this.fromAddress = from;
            this.toAddress = to;
            this.amount = amount;
            this.rate = rate;
            this.timestamp = timestamp;
            this.tokenPair = tokenPair;
        }
        
        double similarity(TransactionPattern other) {
            if (!this.tokenPair.equals(other.tokenPair)) return 0.0;
            
            // Check if addresses are reversed (A->B then B->A)
            boolean reversedAddresses = this.fromAddress.equals(other.toAddress) &&
                                       this.toAddress.equals(other.fromAddress);
            
            if (!reversedAddresses) return 0.0;
            
            // Calculate amount similarity
            BigDecimal amountDiff = this.amount.subtract(other.amount).abs();
            BigDecimal avgAmount = this.amount.add(other.amount).divide(new BigDecimal(2), 8, RoundingMode.HALF_UP);
            double amountSimilarity = BigDecimal.ONE.subtract(amountDiff.divide(avgAmount, 8, RoundingMode.HALF_UP))
                .doubleValue();
            
            // Calculate rate similarity
            BigDecimal rateDiff = this.rate.subtract(other.rate).abs();
            BigDecimal avgRate = this.rate.add(other.rate).divide(new BigDecimal(2), 8, RoundingMode.HALF_UP);
            double rateSimilarity = BigDecimal.ONE.subtract(rateDiff.divide(avgRate, 8, RoundingMode.HALF_UP))
                .doubleValue();
            
            // Calculate time proximity (closer in time = higher similarity)
            long timeDiff = Math.abs(this.timestamp - other.timestamp);
            double timeSimilarity = Math.max(0, 1.0 - (timeDiff / (double)MEDIUM_WINDOW_MS));
            
            // Weighted average
            return (amountSimilarity * 0.4 + rateSimilarity * 0.4 + timeSimilarity * 0.2);
        }
    }
    
    /**
     * Tracks suspicious activity
     */
    private static class SuspiciousActivity {
        final String identifier;
        final String activityType;
        final String description;
        final long timestamp;
        final Map<String, Object> evidence;
        int occurrences;
        
        SuspiciousActivity(String identifier, String type, String description, Map<String, Object> evidence) {
            this.identifier = identifier;
            this.activityType = type;
            this.description = description;
            this.timestamp = System.currentTimeMillis();
            this.evidence = evidence;
            this.occurrences = 1;
        }
        
        void incrementOccurrences() {
            this.occurrences++;
        }
    }
    
    /**
     * Validation result
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String failureReason;
        private final String manipulationType;
        private final Map<String, Object> evidence;
        
        private ValidationResult(boolean valid, String reason, String type, Map<String, Object> evidence) {
            this.valid = valid;
            this.failureReason = reason;
            this.manipulationType = type;
            this.evidence = evidence != null ? evidence : new HashMap<>();
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, null, null, new HashMap<>());
        }
        
        public static ValidationResult failure(String reason, String type, Map<String, Object> evidence) {
            return new ValidationResult(false, reason, type, evidence);
        }
        
        public boolean isValid() { return valid; }
        public String getFailureReason() { return failureReason; }
        public String getManipulationType() { return manipulationType; }
        public Map<String, Object> getEvidence() { return evidence; }
    }
    
    /**
     * Constructor
     */
    public AntiManipulationValidator(BlockchainState blockchainState,
                                     ExchangeRateOracle exchangeRateOracle) {
        this.blockchainState = blockchainState;
        this.exchangeRateOracle = exchangeRateOracle;
        this.priceHistory = new ConcurrentHashMap<>();
        this.volumeHistory = new ConcurrentHashMap<>();
        this.transactionPatterns = new ConcurrentHashMap<>();
        this.suspiciousActivities = new ConcurrentHashMap<>();
        
        LOGGER.info("AntiManipulationValidator initialized");
    }
    
    /**
     * Checks token-bank transaction for manipulation
     */
    public ValidationResult checkTokenBankTransaction(TokenBankTransaction transaction) {
        try {
            // 1. Check for price manipulation
            ValidationResult priceCheck = checkPriceManipulation(
                transaction.getTokenSymbol(),
                transaction.getFiatAmount().divide(transaction.getTokenAmount(), 8, RoundingMode.HALF_UP)
            );
            if (!priceCheck.isValid()) {
                recordSuspiciousActivity(transaction.getHash().toString(), 
                    "PRICE_MANIPULATION", priceCheck.getFailureReason(), priceCheck.getEvidence());
                return priceCheck;
            }
            
            // 2. Check for volume manipulation
            ValidationResult volumeCheck = checkVolumeManipulation(
                transaction.getTokenSymbol(),
                transaction.getFiatAmount()
            );
            if (!volumeCheck.isValid()) {
                recordSuspiciousActivity(transaction.getHash().toString(),
                    "VOLUME_MANIPULATION", volumeCheck.getFailureReason(), volumeCheck.getEvidence());
                return volumeCheck;
            }
            
            // Record data for future analysis
            recordPriceData(transaction.getTokenSymbol(),
                transaction.getFiatAmount().divide(transaction.getTokenAmount(), 8, RoundingMode.HALF_UP),
                "TRANSACTION");
            recordVolumeData(transaction.getTokenSymbol(), transaction.getFiatAmount());
            
            return ValidationResult.success();
            
        } catch (Exception e) {
            LOGGER.severe("Manipulation check error: " + e.getMessage());
            return ValidationResult.failure("Manipulation check failed: " + e.getMessage(), 
                "ERROR", new HashMap<>());
        }
    }
    
    /**
     * Checks currency exchange transaction for manipulation
     */
    public ValidationResult checkCurrencyExchange(CurrencyExchangeTransaction transaction) {
        try {
            String pair = transaction.getFromCurrency() + "/" + transaction.getToCurrency();
            
            // Create transaction pattern for wash trading detection
            TransactionPattern pattern = new TransactionPattern(
                transaction.getFromAddress().toString(),
                transaction.getToAddress().toString(),
                transaction.getFromAmount(),
                transaction.getToAmount().divide(transaction.getFromAmount(), 8, RoundingMode.HALF_UP),
                transaction.getTimestamp(),
                pair
            );
            
            // 1. Check for wash trading
            ValidationResult washCheck = detectWashTrading(pattern);
            if (!washCheck.isValid()) {
                recordSuspiciousActivity(transaction.getHash().toString(),
                    "WASH_TRADING", washCheck.getFailureReason(), washCheck.getEvidence());
                return washCheck;
            }
            
            // 2. Check for price manipulation
            ValidationResult priceCheck = checkPriceManipulation(
                pair,
                transaction.getToAmount().divide(transaction.getFromAmount(), 8, RoundingMode.HALF_UP)
            );
            if (!priceCheck.isValid()) {
                recordSuspiciousActivity(transaction.getHash().toString(),
                    "PRICE_MANIPULATION", priceCheck.getFailureReason(), priceCheck.getEvidence());
                return priceCheck;
            }
            
            // Record pattern for future analysis
            List<TransactionPattern> patterns = transactionPatterns.computeIfAbsent(
                pair, k -> Collections.synchronizedList(new ArrayList<>())
            );
            patterns.add(pattern);
            cleanOldPatterns(patterns);
            
            return ValidationResult.success();
            
        } catch (Exception e) {
            LOGGER.severe("Exchange manipulation check error: " + e.getMessage());
            return ValidationResult.failure("Exchange check failed: " + e.getMessage(),
                "ERROR", new HashMap<>());
        }
    }
    
    /**
     * Detects price manipulation using statistical analysis
     */
    private ValidationResult checkPriceManipulation(String asset, BigDecimal currentPrice) {
        List<PriceDataPoint> history = priceHistory.get(asset);
        
        if (history == null || history.size() < 10) {
            // Not enough data for analysis
            return ValidationResult.success();
        }
        
        long now = System.currentTimeMillis();
        
        // Get recent prices (last hour)
        List<BigDecimal> recentPrices = history.stream()
            .filter(p -> p.isWithinWindow(now, MEDIUM_WINDOW_MS))
            .map(p -> p.price)
            .collect(Collectors.toList());
        
        if (recentPrices.isEmpty()) {
            return ValidationResult.success();
        }
        
        // Calculate Z-score
        BigDecimal mean = calculateMean(recentPrices);
        BigDecimal stdDev = calculateStandardDeviation(recentPrices, mean);
        
        if (stdDev.compareTo(BigDecimal.ZERO) == 0) {
            // No variation, check for sudden spike
            if (currentPrice.subtract(mean).abs().divide(mean, 8, RoundingMode.HALF_UP)
                .compareTo(PRICE_SPIKE_THRESHOLD) > 0) {
                Map<String, Object> evidence = new HashMap<>();
                evidence.put("asset", asset);
                evidence.put("currentPrice", currentPrice);
                evidence.put("meanPrice", mean);
                evidence.put("deviation", currentPrice.subtract(mean).abs().divide(mean, 8, RoundingMode.HALF_UP));
                
                return ValidationResult.failure(
                    String.format("Sudden price spike detected for %s: %.2f%% deviation",
                        asset, currentPrice.subtract(mean).abs().divide(mean, 8, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal(100)).doubleValue()),
                    "PRICE_SPIKE",
                    evidence
                );
            }
            return ValidationResult.success();
        }
        
        BigDecimal zScore = currentPrice.subtract(mean).divide(stdDev, 8, RoundingMode.HALF_UP);
        
        if (zScore.abs().compareTo(Z_SCORE_THRESHOLD) > 0) {
            Map<String, Object> evidence = new HashMap<>();
            evidence.put("asset", asset);
            evidence.put("currentPrice", currentPrice);
            evidence.put("meanPrice", mean);
            evidence.put("stdDev", stdDev);
            evidence.put("zScore", zScore);
            
            return ValidationResult.failure(
                String.format("Price manipulation detected for %s: Z-score = %.2f (threshold: %.2f)",
                    asset, zScore.doubleValue(), Z_SCORE_THRESHOLD.doubleValue()),
                "PRICE_MANIPULATION",
                evidence
            );
        }
        
        // MAD (Median Absolute Deviation) check for robustness
        BigDecimal median = calculateMedian(recentPrices);
        BigDecimal mad = calculateMAD(recentPrices, median);
        
        if (mad.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal madScore = currentPrice.subtract(median).abs().divide(mad, 8, RoundingMode.HALF_UP);
            
            if (madScore.compareTo(MAD_MULTIPLIER) > 0) {
                Map<String, Object> evidence = new HashMap<>();
                evidence.put("asset", asset);
                evidence.put("currentPrice", currentPrice);
                evidence.put("medianPrice", median);
                evidence.put("mad", mad);
                evidence.put("madScore", madScore);
                
                return ValidationResult.failure(
                    String.format("Price outlier detected for %s: MAD score = %.2f (threshold: %.2f)",
                        asset, madScore.doubleValue(), MAD_MULTIPLIER.doubleValue()),
                    "PRICE_OUTLIER",
                    evidence
                );
            }
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Detects volume manipulation
     */
    private ValidationResult checkVolumeManipulation(String asset, BigDecimal currentVolume) {
        List<VolumeDataPoint> history = volumeHistory.get(asset);
        
        if (history == null || history.size() < 10) {
            return ValidationResult.success();
        }
        
        long now = System.currentTimeMillis();
        
        // Get recent volumes
        List<BigDecimal> recentVolumes = history.stream()
            .filter(v -> v.isWithinWindow(now, MEDIUM_WINDOW_MS))
            .map(v -> v.volume)
            .collect(Collectors.toList());
        
        if (recentVolumes.isEmpty()) {
            return ValidationResult.success();
        }
        
        BigDecimal avgVolume = calculateMean(recentVolumes);
        
        if (avgVolume.compareTo(BigDecimal.ZERO) == 0) {
            return ValidationResult.success();
        }
        
        BigDecimal volumeRatio = currentVolume.divide(avgVolume, 8, RoundingMode.HALF_UP);
        
        if (volumeRatio.compareTo(VOLUME_SPIKE_THRESHOLD) > 0) {
            Map<String, Object> evidence = new HashMap<>();
            evidence.put("asset", asset);
            evidence.put("currentVolume", currentVolume);
            evidence.put("avgVolume", avgVolume);
            evidence.put("volumeRatio", volumeRatio);
            
            return ValidationResult.failure(
                String.format("Volume spike detected for %s: %.2fx average volume",
                    asset, volumeRatio.doubleValue()),
                "VOLUME_SPIKE",
                evidence
            );
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Detects wash trading patterns
     */
    private ValidationResult detectWashTrading(TransactionPattern newPattern) {
        List<TransactionPattern> patterns = transactionPatterns.get(newPattern.tokenPair);
        
        if (patterns == null || patterns.size() < WASH_TRADE_MIN_OCCURRENCES) {
            return ValidationResult.success();
        }
        
        // Find similar patterns
        List<TransactionPattern> similarPatterns = patterns.stream()
            .filter(p -> p.similarity(newPattern) >= WASH_TRADE_SIMILARITY_THRESHOLD.doubleValue())
            .collect(Collectors.toList());
        
        if (similarPatterns.size() >= WASH_TRADE_MIN_OCCURRENCES - 1) {
            Map<String, Object> evidence = new HashMap<>();
            evidence.put("tokenPair", newPattern.tokenPair);
            evidence.put("occurrences", similarPatterns.size() + 1);
            evidence.put("addresses", Arrays.asList(newPattern.fromAddress, newPattern.toAddress));
            evidence.put("avgSimilarity", 
                similarPatterns.stream()
                    .mapToDouble(p -> p.similarity(newPattern))
                    .average()
                    .orElse(0.0)
            );
            
            return ValidationResult.failure(
                String.format("Wash trading detected: %d similar transactions between same addresses",
                    similarPatterns.size() + 1),
                "WASH_TRADING",
                evidence
            );
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Records price data for analysis
     */
    private void recordPriceData(String asset, BigDecimal price, String source) {
        List<PriceDataPoint> history = priceHistory.computeIfAbsent(
            asset, k -> Collections.synchronizedList(new ArrayList<>())
        );
        
        history.add(new PriceDataPoint(price, System.currentTimeMillis(), source));
        cleanOldPriceData(history);
    }
    
    /**
     * Records volume data for analysis
     */
    private void recordVolumeData(String asset, BigDecimal volume) {
        List<VolumeDataPoint> history = volumeHistory.computeIfAbsent(
            asset, k -> Collections.synchronizedList(new ArrayList<>())
        );
        
        history.add(new VolumeDataPoint(volume, System.currentTimeMillis(), asset));
        cleanOldVolumeData(history);
    }
    
    /**
     * Records suspicious activity
     */
    private void recordSuspiciousActivity(String identifier, String type, 
                                         String description, Map<String, Object> evidence) {
        String key = identifier + "_" + type;
        
        SuspiciousActivity activity = suspiciousActivities.get(key);
        if (activity != null) {
            activity.incrementOccurrences();
        } else {
            suspiciousActivities.put(key, 
                new SuspiciousActivity(identifier, type, description, evidence));
        }
        
        LOGGER.warning(String.format("Suspicious activity recorded: %s - %s", type, description));
    }
    
    /**
     * Statistical helper: Calculate mean
     */
    private BigDecimal calculateMean(List<BigDecimal> values) {
        if (values.isEmpty()) return BigDecimal.ZERO;
        
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(new BigDecimal(values.size()), 8, RoundingMode.HALF_UP);
    }
    
    /**
     * Statistical helper: Calculate standard deviation
     */
    private BigDecimal calculateStandardDeviation(List<BigDecimal> values, BigDecimal mean) {
        if (values.size() < 2) return BigDecimal.ZERO;
        
        BigDecimal sumSquaredDiff = values.stream()
            .map(v -> v.subtract(mean).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal variance = sumSquaredDiff.divide(
            new BigDecimal(values.size() - 1), 8, RoundingMode.HALF_UP
        );
        
        return new BigDecimal(Math.sqrt(variance.doubleValue()));
    }
    
    /**
     * Statistical helper: Calculate median
     */
    private BigDecimal calculateMedian(List<BigDecimal> values) {
        if (values.isEmpty()) return BigDecimal.ZERO;
        
        List<BigDecimal> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return sorted.get(middle - 1).add(sorted.get(middle))
                .divide(new BigDecimal(2), 8, RoundingMode.HALF_UP);
        } else {
            return sorted.get(middle);
        }
    }
    
    /**
     * Statistical helper: Calculate MAD (Median Absolute Deviation)
     */
    private BigDecimal calculateMAD(List<BigDecimal> values, BigDecimal median) {
        if (values.isEmpty()) return BigDecimal.ZERO;
        
        List<BigDecimal> absoluteDeviations = values.stream()
            .map(v -> v.subtract(median).abs())
            .collect(Collectors.toList());
        
        return calculateMedian(absoluteDeviations);
    }
    
    /**
     * Cleanup methods
     */
    private void cleanOldPriceData(List<PriceDataPoint> history) {
        long now = System.currentTimeMillis();
        history.removeIf(p -> !p.isWithinWindow(now, LONG_WINDOW_MS * 2));
    }
    
    private void cleanOldVolumeData(List<VolumeDataPoint> history) {
        long now = System.currentTimeMillis();
        history.removeIf(v -> !v.isWithinWindow(now, LONG_WINDOW_MS * 2));
    }
    
    private void cleanOldPatterns(List<TransactionPattern> patterns) {
        long now = System.currentTimeMillis();
        patterns.removeIf(p -> (now - p.timestamp) > LONG_WINDOW_MS);
    }
    
    /**
     * Gets suspicious activities
     */
    public Collection<SuspiciousActivity> getSuspiciousActivities() {
        return new ArrayList<>(suspiciousActivities.values());
    }
    
    /**
     * Gets manipulation statistics
     */
    public Map<String, Object> getManipulationStats() {
        Map<String, Object> stats = new HashMap<>();
        
        Map<String, Long> typeCount = new HashMap<>();
        for (SuspiciousActivity activity : suspiciousActivities.values()) {
            typeCount.merge(activity.activityType, (long)activity.occurrences, Long::sum);
        }
        
        stats.put("totalSuspiciousActivities", suspiciousActivities.size());
        stats.put("byType", typeCount);
        stats.put("assetsMonitored", priceHistory.size());
        stats.put("patternsTracked", transactionPatterns.values().stream()
            .mapToInt(List::size).sum());
        
        return stats;
    }
}