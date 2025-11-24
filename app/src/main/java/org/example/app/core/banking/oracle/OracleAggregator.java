package org.example.app.core.banking.oracle;

import org.example.app.core.oracle.OracleReporter.OracleData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * OracleAggregator - Multi-source aggregation with weighted algorithms
 * 
 * Implements sophisticated data aggregation from multiple oracle sources with:
 * - Fair-weighting algorithms (volume, liquidity, stability, reputation)
 * - Outlier detection (Z-scores, Median Absolute Deviation)
 * - African exchange prioritization (50-60% weight bonus)
 * - Anti-manipulation protections
 * - Multi-source verification requirements
 * - Weighted median calculations
 * 
 * Based on Implementation Guidebook Sections II & VI
 */
public class OracleAggregator {
    private static final Logger LOGGER = Logger.getLogger(OracleAggregator.class.getName());
    
    // Aggregation parameters
    private static final int MIN_SOURCES_REQUIRED = 5; // Minimum 5 sources for valid aggregation
    private static final int RECOMMENDED_SOURCES = 7; // Recommended 7 sources
    private static final BigDecimal OUTLIER_Z_SCORE_THRESHOLD = new BigDecimal("2.5"); // 2.5 standard deviations
    private static final BigDecimal MAD_MULTIPLIER = new BigDecimal("1.4826"); // Median Absolute Deviation multiplier
    private static final BigDecimal AFRICAN_WEIGHT_BONUS = new BigDecimal("0.55"); // 55% bonus for African sources
    
    // Fair-weighting factors (sum to 1.0)
    private static final Map<WeightFactor, BigDecimal> WEIGHT_FACTORS = new HashMap<>();
    static {
        WEIGHT_FACTORS.put(WeightFactor.VOLUME, new BigDecimal("0.40"));         // 40% - Trading volume
        WEIGHT_FACTORS.put(WeightFactor.LIQUIDITY, new BigDecimal("0.25"));      // 25% - Liquidity depth
        WEIGHT_FACTORS.put(WeightFactor.STABILITY, new BigDecimal("0.20"));      // 20% - Price stability
        WEIGHT_FACTORS.put(WeightFactor.ADOPTION, new BigDecimal("0.10"));       // 10% - User adoption
        WEIGHT_FACTORS.put(WeightFactor.REPUTATION, new BigDecimal("0.05"));     // 5% - Exchange reputation
    }
    
    /**
     * Weight factors for fair calculation
     */
    public enum WeightFactor {
        VOLUME,
        LIQUIDITY,
        STABILITY,
        ADOPTION,
        REPUTATION
    }
    
    /**
     * Geographic source type
     */
    public enum SourceGeography {
        AFRICAN,
        NON_AFRICAN
    }
    
    /**
     * Aggregated result with metadata
     */
    public static class AggregatedResult {
        private final BigDecimal weightedAverage;
        private final BigDecimal median;
        private final BigDecimal weightedMedian;
        private final BigDecimal standardDeviation;
        private final BigDecimal confidenceScore;
        private final int sourcesUsed;
        private final int sourcesFiltered;
        private final List<String> sourceIds;
        private final Map<String, BigDecimal> sourceWeights;
        private final Map<String, Object> metadata;
        private final long timestamp;
        
        public AggregatedResult(BigDecimal weightedAverage, BigDecimal median, 
                               BigDecimal weightedMedian, BigDecimal standardDeviation,
                               BigDecimal confidenceScore, int sourcesUsed, int sourcesFiltered,
                               List<String> sourceIds, Map<String, BigDecimal> sourceWeights) {
            this.weightedAverage = weightedAverage;
            this.median = median;
            this.weightedMedian = weightedMedian;
            this.standardDeviation = standardDeviation;
            this.confidenceScore = confidenceScore;
            this.sourcesUsed = sourcesUsed;
            this.sourcesFiltered = sourcesFiltered;
            this.sourceIds = new ArrayList<>(sourceIds);
            this.sourceWeights = new HashMap<>(sourceWeights);
            this.metadata = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public BigDecimal getWeightedAverage() { return weightedAverage; }
        public BigDecimal getMedian() { return median; }
        public BigDecimal getWeightedMedian() { return weightedMedian; }
        public BigDecimal getStandardDeviation() { return standardDeviation; }
        public BigDecimal getConfidenceScore() { return confidenceScore; }
        public int getSourcesUsed() { return sourcesUsed; }
        public int getSourcesFiltered() { return sourcesFiltered; }
        public List<String> getSourceIds() { return new ArrayList<>(sourceIds); }
        public Map<String, BigDecimal> getSourceWeights() { return new HashMap<>(sourceWeights); }
        public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
        public long getTimestamp() { return timestamp; }
        
        public void addMetadata(String key, Object value) {
            metadata.put(key, value);
        }
        
        @Override
        public String toString() {
            return String.format("AggregatedResult{weighted=%.6f, median=%.6f, sources=%d/%d, confidence=%.2f%%}",
                weightedAverage, median, sourcesUsed, sourcesUsed + sourcesFiltered, 
                confidenceScore.multiply(new BigDecimal("100")));
        }
    }
    
    /**
     * Source data with weight factors
     */
    public static class SourceData {
        private final String sourceId;
        private final BigDecimal value;
        private final SourceGeography geography;
        private final BigDecimal volume;
        private final BigDecimal liquidity;
        private final BigDecimal stability;
        private final BigDecimal adoption;
        private final BigDecimal reputation;
        private BigDecimal calculatedWeight;
        
        public SourceData(String sourceId, BigDecimal value, SourceGeography geography,
                         BigDecimal volume, BigDecimal liquidity, BigDecimal stability,
                         BigDecimal adoption, BigDecimal reputation) {
            this.sourceId = sourceId;
            this.value = value;
            this.geography = geography;
            this.volume = volume;
            this.liquidity = liquidity;
            this.stability = stability;
            this.adoption = adoption;
            this.reputation = reputation;
            this.calculatedWeight = BigDecimal.ZERO;
        }
        
        public String getSourceId() { return sourceId; }
        public BigDecimal getValue() { return value; }
        public SourceGeography getGeography() { return geography; }
        public BigDecimal getVolume() { return volume; }
        public BigDecimal getLiquidity() { return liquidity; }
        public BigDecimal getStability() { return stability; }
        public BigDecimal getAdoption() { return adoption; }
        public BigDecimal getReputation() { return reputation; }
        public BigDecimal getCalculatedWeight() { return calculatedWeight; }
        
        public void setCalculatedWeight(BigDecimal weight) {
            this.calculatedWeight = weight;
        }
    }
    
    // Reporter registry
    private final Map<String, OracleReporter> reporters;
    
    // Cache for recent aggregations
    private final Map<String, AggregatedResult> aggregationCache;
    private static final long CACHE_EXPIRY_MS = 300000; // 5 minutes
    
    /**
     * Constructor
     */
    public OracleAggregator() {
        this.reporters = new ConcurrentHashMap<>();
        this.aggregationCache = new ConcurrentHashMap<>();
        
        LOGGER.info("OracleAggregator initialized");
    }
    
    /**
     * Registers an oracle reporter
     */
    public void registerReporter(OracleReporter reporter) {
        reporters.put(reporter.getReporterId(), reporter);
        LOGGER.info("Reporter registered: " + reporter.getReporterId());
    }
    
    /**
     * Unregisters an oracle reporter
     */
    public void unregisterReporter(String reporterId) {
        reporters.remove(reporterId);
        LOGGER.info("Reporter unregistered: " + reporterId);
    }
    
    /**
     * Aggregates data from multiple sources with fair-weighting
     */
    public AggregatedResult aggregateData(List<SourceData> sources, String dataKey) {
        // Check cache first
        String cacheKey = dataKey + "_" + sources.size();
        AggregatedResult cached = aggregationCache.get(cacheKey);
        if (cached != null && !isCacheExpired(cached)) {
            LOGGER.fine("Returning cached aggregation: " + dataKey);
            return cached;
        }
        
        // Validate minimum sources
        if (sources.size() < MIN_SOURCES_REQUIRED) {
            LOGGER.warning(String.format("Insufficient sources: %d (minimum: %d)", 
                sources.size(), MIN_SOURCES_REQUIRED));
            return null;
        }
        
        LOGGER.info(String.format("Aggregating data from %d sources for: %s", sources.size(), dataKey));
        
        // Step 1: Calculate weights for each source
        List<SourceData> weightedSources = calculateWeights(sources);
        
        // Step 2: Detect and filter outliers
        List<SourceData> filteredSources = filterOutliers(weightedSources);
        
        int sourcesFiltered = sources.size() - filteredSources.size();
        
        if (filteredSources.size() < MIN_SOURCES_REQUIRED) {
            LOGGER.warning("Too many sources filtered as outliers");
            return null;
        }
        
        // Step 3: Calculate statistics
        BigDecimal weightedAverage = calculateWeightedAverage(filteredSources);
        BigDecimal median = calculateMedian(filteredSources);
        BigDecimal weightedMedian = calculateWeightedMedian(filteredSources);
        BigDecimal stdDev = calculateStandardDeviation(filteredSources, weightedAverage);
        
        // Step 4: Calculate confidence score
        BigDecimal confidence = calculateConfidenceScore(filteredSources, stdDev);
        
        // Step 5: Extract source IDs and weights
        List<String> sourceIds = filteredSources.stream()
            .map(SourceData::getSourceId)
            .collect(Collectors.toList());
        
        Map<String, BigDecimal> sourceWeights = new HashMap<>();
        for (SourceData source : filteredSources) {
            sourceWeights.put(source.getSourceId(), source.getCalculatedWeight());
        }
        
        // Create result
        AggregatedResult result = new AggregatedResult(
            weightedAverage, median, weightedMedian, stdDev, confidence,
            filteredSources.size(), sourcesFiltered, sourceIds, sourceWeights
        );
        
        // Add metadata
        result.addMetadata("dataKey", dataKey);
        result.addMetadata("minSources", MIN_SOURCES_REQUIRED);
        result.addMetadata("africanSources", countAfricanSources(filteredSources));
        
        // Cache result
        aggregationCache.put(cacheKey, result);
        
        LOGGER.info(String.format("Aggregation complete: %s", result));
        
        return result;
    }
    
    /**
     * Calculates weights for each source using fair-weighting algorithm
     */
    private List<SourceData> calculateWeights(List<SourceData> sources) {
        List<SourceData> weighted = new ArrayList<>();
        
        // Normalize each factor across all sources
        Map<WeightFactor, BigDecimal> maxValues = calculateMaxValues(sources);
        
        for (SourceData source : sources) {
            // Calculate base weight from factors
            BigDecimal volumeWeight = normalizeAndWeight(source.getVolume(), 
                maxValues.get(WeightFactor.VOLUME), WEIGHT_FACTORS.get(WeightFactor.VOLUME));
            
            BigDecimal liquidityWeight = normalizeAndWeight(source.getLiquidity(),
                maxValues.get(WeightFactor.LIQUIDITY), WEIGHT_FACTORS.get(WeightFactor.LIQUIDITY));
            
            BigDecimal stabilityWeight = normalizeAndWeight(source.getStability(),
                maxValues.get(WeightFactor.STABILITY), WEIGHT_FACTORS.get(WeightFactor.STABILITY));
            
            BigDecimal adoptionWeight = normalizeAndWeight(source.getAdoption(),
                maxValues.get(WeightFactor.ADOPTION), WEIGHT_FACTORS.get(WeightFactor.ADOPTION));
            
            BigDecimal reputationWeight = normalizeAndWeight(source.getReputation(),
                maxValues.get(WeightFactor.REPUTATION), WEIGHT_FACTORS.get(WeightFactor.REPUTATION));
            
            // Sum base weight
            BigDecimal baseWeight = volumeWeight.add(liquidityWeight)
                .add(stabilityWeight).add(adoptionWeight).add(reputationWeight);
            
            // Apply African bonus
            BigDecimal finalWeight = baseWeight;
            if (source.getGeography() == SourceGeography.AFRICAN) {
                finalWeight = baseWeight.multiply(BigDecimal.ONE.add(AFRICAN_WEIGHT_BONUS));
            }
            
            source.setCalculatedWeight(finalWeight);
            weighted.add(source);
        }
        
        // Normalize weights to sum to 1.0
        BigDecimal totalWeight = weighted.stream()
            .map(SourceData::getCalculatedWeight)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        for (SourceData source : weighted) {
            BigDecimal normalized = source.getCalculatedWeight()
                .divide(totalWeight, 10, RoundingMode.HALF_UP);
            source.setCalculatedWeight(normalized);
        }
        
        return weighted;
    }
    
    /**
     * Calculates maximum values for each weight factor
     */
    private Map<WeightFactor, BigDecimal> calculateMaxValues(List<SourceData> sources) {
        Map<WeightFactor, BigDecimal> maxValues = new HashMap<>();
        
        BigDecimal maxVolume = sources.stream()
            .map(SourceData::getVolume)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ONE);
        
        BigDecimal maxLiquidity = sources.stream()
            .map(SourceData::getLiquidity)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ONE);
        
        BigDecimal maxStability = sources.stream()
            .map(SourceData::getStability)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ONE);
        
        BigDecimal maxAdoption = sources.stream()
            .map(SourceData::getAdoption)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ONE);
        
        BigDecimal maxReputation = sources.stream()
            .map(SourceData::getReputation)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ONE);
        
        maxValues.put(WeightFactor.VOLUME, maxVolume);
        maxValues.put(WeightFactor.LIQUIDITY, maxLiquidity);
        maxValues.put(WeightFactor.STABILITY, maxStability);
        maxValues.put(WeightFactor.ADOPTION, maxAdoption);
        maxValues.put(WeightFactor.REPUTATION, maxReputation);
        
        return maxValues;
    }
    
    /**
     * Normalizes and weights a value
     */
    private BigDecimal normalizeAndWeight(BigDecimal value, BigDecimal maxValue, BigDecimal weight) {
        if (maxValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal normalized = value.divide(maxValue, 10, RoundingMode.HALF_UP);
        return normalized.multiply(weight);
    }
    
    /**
     * Filters outliers using Z-score and MAD methods
     */
    private List<SourceData> filterOutliers(List<SourceData> sources) {
        if (sources.size() < MIN_SOURCES_REQUIRED) {
            return sources;
        }
        
        // Calculate median
        BigDecimal median = calculateMedian(sources);
        
        // Calculate MAD (Median Absolute Deviation)
        List<BigDecimal> deviations = sources.stream()
            .map(s -> s.getValue().subtract(median).abs())
            .sorted()
            .collect(Collectors.toList());
        
        BigDecimal mad = deviations.get(deviations.size() / 2);
        
        // Filter outliers
        List<SourceData> filtered = new ArrayList<>();
        for (SourceData source : sources) {
            BigDecimal deviation = source.getValue().subtract(median).abs();
            BigDecimal madScore = BigDecimal.ZERO;
            
            if (mad.compareTo(BigDecimal.ZERO) > 0) {
                madScore = deviation.divide(mad.multiply(MAD_MULTIPLIER), 10, RoundingMode.HALF_UP);
            }
            
            // Keep if within threshold
            if (madScore.compareTo(OUTLIER_Z_SCORE_THRESHOLD) <= 0) {
                filtered.add(source);
            } else {
                LOGGER.fine(String.format("Filtered outlier: %s (MAD score: %.2f)", 
                    source.getSourceId(), madScore));
            }
        }
        
        return filtered;
    }
    
    /**
     * Calculates weighted average
     */
    private BigDecimal calculateWeightedAverage(List<SourceData> sources) {
        return sources.stream()
            .map(s -> s.getValue().multiply(s.getCalculatedWeight()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Calculates median value
     */
    private BigDecimal calculateMedian(List<SourceData> sources) {
        List<BigDecimal> values = sources.stream()
            .map(SourceData::getValue)
            .sorted()
            .collect(Collectors.toList());
        
        int size = values.size();
        if (size == 0) return BigDecimal.ZERO;
        
        if (size % 2 == 0) {
            return values.get(size / 2 - 1).add(values.get(size / 2))
                .divide(new BigDecimal("2"), 10, RoundingMode.HALF_UP);
        } else {
            return values.get(size / 2);
        }
    }
    
    /**
     * Calculates weighted median
     */
    private BigDecimal calculateWeightedMedian(List<SourceData> sources) {
        // Sort by value
        List<SourceData> sorted = sources.stream()
            .sorted(Comparator.comparing(SourceData::getValue))
            .collect(Collectors.toList());
        
        // Find cumulative weight that reaches 0.5
        BigDecimal cumulative = BigDecimal.ZERO;
        BigDecimal target = new BigDecimal("0.5");
        
        for (SourceData source : sorted) {
            cumulative = cumulative.add(source.getCalculatedWeight());
            if (cumulative.compareTo(target) >= 0) {
                return source.getValue();
            }
        }
        
        // Fallback to last value
        return sorted.get(sorted.size() - 1).getValue();
    }
    
    /**
     * Calculates standard deviation
     */
    private BigDecimal calculateStandardDeviation(List<SourceData> sources, BigDecimal mean) {
        if (sources.size() < 2) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal sumSquaredDiff = sources.stream()
            .map(s -> s.getValue().subtract(mean).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal variance = sumSquaredDiff.divide(
            new BigDecimal(sources.size()), 10, RoundingMode.HALF_UP);
        
        // Square root approximation using Newton's method
        return sqrt(variance);
    }
    
    /**
     * Square root using Newton's method
     */
    private BigDecimal sqrt(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal x = value.divide(new BigDecimal("2"), 10, RoundingMode.HALF_UP);
        BigDecimal lastX;
        
        for (int i = 0; i < 10; i++) {
            lastX = x;
            x = x.add(value.divide(x, 10, RoundingMode.HALF_UP))
                .divide(new BigDecimal("2"), 10, RoundingMode.HALF_UP);
            
            if (x.subtract(lastX).abs().compareTo(new BigDecimal("0.00001")) < 0) {
                break;
            }
        }
        
        return x;
    }
    
    /**
     * Calculates confidence score based on agreement and sources
     */
    private BigDecimal calculateConfidenceScore(List<SourceData> sources, BigDecimal stdDev) {
        // More sources = higher confidence
        BigDecimal sourceScore = new BigDecimal(Math.min(sources.size(), RECOMMENDED_SOURCES))
            .divide(new BigDecimal(RECOMMENDED_SOURCES), 4, RoundingMode.HALF_UP);
        
        // Lower std dev = higher confidence
        BigDecimal agreementScore = BigDecimal.ONE;
        if (stdDev.compareTo(BigDecimal.ZERO) > 0) {
            // Normalize to reasonable range (0.1 = perfect, 1.0 = poor)
            BigDecimal normalized = stdDev.divide(new BigDecimal("0.1"), 4, RoundingMode.HALF_UP);
            agreementScore = BigDecimal.ONE.divide(
                BigDecimal.ONE.add(normalized), 4, RoundingMode.HALF_UP);
        }
        
        // Combine scores (70% sources, 30% agreement)
        return sourceScore.multiply(new BigDecimal("0.7"))
            .add(agreementScore.multiply(new BigDecimal("0.3")));
    }
    
    /**
     * Counts African sources
     */
    private int countAfricanSources(List<SourceData> sources) {
        return (int) sources.stream()
            .filter(s -> s.getGeography() == SourceGeography.AFRICAN)
            .count();
    }
    
    /**
     * Checks if cache is expired
     */
    private boolean isCacheExpired(AggregatedResult result) {
        return System.currentTimeMillis() - result.getTimestamp() > CACHE_EXPIRY_MS;
    }
    
    /**
     * Clears expired cache entries
     */
    public void clearExpiredCache() {
        aggregationCache.entrySet().removeIf(entry -> isCacheExpired(entry.getValue()));
    }
    
    /**
     * Gets reporter by ID
     */
    public OracleReporter getReporter(String reporterId) {
        return reporters.get(reporterId);
    }
    
    /**
     * Gets all active reporters
     */
    public List<OracleReporter> getActiveReporters() {
        return reporters.values().stream()
            .filter(OracleReporter::isActive)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalReporters", reporters.size());
        stats.put("activeReporters", getActiveReporters().size());
        stats.put("cachedAggregations", aggregationCache.size());
        return stats;
    }
}
