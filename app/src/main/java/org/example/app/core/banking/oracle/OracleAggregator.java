package org.example.app.core.banking.oracle;

import org.example.app.core.banking.oracle.AfricanSovereigntyTypes.*;
import org.example.app.core.banking.oracle.OracleReporter.OracleData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * OracleAggregator - Multi-source aggregation with African Sovereignty enforcement
 * 
 * ⚠️ CRITICAL COMPONENT - UPGRADED FOR AFRICAN NODE SOVEREIGNTY
 * 
 * Implements sophisticated data aggregation with:
 * - 35-node African Data Availability Committee (7 per region)
 * - Geographic distribution enforcement (max 33% per region/country)
 * - Community ownership targets (50%+ community, max 20% cloud)
 * - Enhanced African prioritization (50-60% bonus + 3x multiplier)
 * - Byzantine Fault Tolerance (5/7 consensus per region)
 * - Multi-region verification (minimum 3 regions required)
 * - Fair-weighting algorithms (volume, liquidity, stability, reputation)
 * - Outlier detection and anti-manipulation protections
 * 
 * SOVEREIGNTY FEATURES:
 * - Cannot be controlled by single region/country (33% limit)
 * - Cannot be shut down by single government
 * - Prioritizes African sources (3x weight) over global sources
 * - Community-owned nodes incentivized over cloud providers
 * 
 * Based on Implementation Guidebook Sections II & VI + African Sovereignty Requirements
 */
public class OracleAggregator {
    private static final Logger LOGGER = Logger.getLogger(OracleAggregator.class.getName());
    
    // ============ AFRICAN SOVEREIGNTY PARAMETERS ============
    
    // Committee structure (35 nodes = 5 regions × 7 nodes)
    private final Map<AfricanRegion, RegionalCommittee> regionalCommittees;
    private final Map<String, NodeInfo> nodeRegistry;
    
    // African prioritization (UPGRADED from 55% to 50-60% + 3x multiplier)
    private static final BigDecimal AFRICAN_MIN_BONUS = new BigDecimal("0.50");  // 50% minimum bonus
    private static final BigDecimal AFRICAN_MAX_BONUS = new BigDecimal("0.60");  // 60% maximum bonus
    private static final BigDecimal AFRICAN_PRIORITY_MULTIPLIER = new BigDecimal("3.0"); // 3x weight
    
    // Geographic distribution limits
    private static final BigDecimal MAX_REGION_PERCENTAGE = new BigDecimal("0.33");   // 33%
    private static final BigDecimal MAX_COUNTRY_PERCENTAGE = new BigDecimal("0.33");  // 33%
    private static final int MIN_REGIONS_REQUIRED = 3;  // Minimum 3 of 5 regions
    
    // Node ownership targets
    private static final BigDecimal COMMUNITY_TARGET_PERCENTAGE = new BigDecimal("0.50"); // 50%
    private static final BigDecimal CLOUD_MAX_PERCENTAGE = new BigDecimal("0.20");        // 20%
    private static final BigDecimal COMMUNITY_REWARD_BONUS = new BigDecimal("0.25");      // 25% extra rewards
    
    // ============ AGGREGATION PARAMETERS ============
    
    private static final int MIN_SOURCES_REQUIRED = 5; // Minimum 5 sources for valid aggregation
    private static final int RECOMMENDED_SOURCES = 7;  // Recommended 7 sources
    private static final BigDecimal OUTLIER_Z_SCORE_THRESHOLD = new BigDecimal("2.5");
    private static final BigDecimal MAD_MULTIPLIER = new BigDecimal("1.4826");
    
    // Fair-weighting factors (sum to 1.0)
    private static final Map<WeightFactor, BigDecimal> WEIGHT_FACTORS = new HashMap<>();
    static {
        WEIGHT_FACTORS.put(WeightFactor.VOLUME, new BigDecimal("0.40"));         // 40%
        WEIGHT_FACTORS.put(WeightFactor.LIQUIDITY, new BigDecimal("0.25"));      // 25%
        WEIGHT_FACTORS.put(WeightFactor.STABILITY, new BigDecimal("0.20"));      // 20%
        WEIGHT_FACTORS.put(WeightFactor.ADOPTION, new BigDecimal("0.10"));       // 10%
        WEIGHT_FACTORS.put(WeightFactor.REPUTATION, new BigDecimal("0.05"));     // 5%
    }
    
    /**
     * Weight factors for fair calculation
     */
    public enum WeightFactor {
        VOLUME, LIQUIDITY, STABILITY, ADOPTION, REPUTATION
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
     * Source data with weight factors and geographic information
     */
    public static class SourceData {
        private final String sourceId;
        private final BigDecimal value;
        private final SourceGeography geography;
        private final AfricanRegion africanRegion; // NEW: Regional classification
        private final String country; // NEW: Country tracking
        private final BigDecimal volume;
        private final BigDecimal liquidity;
        private final BigDecimal stability;
        private final BigDecimal adoption;
        private final BigDecimal reputation;
        private BigDecimal calculatedWeight;
        
        public SourceData(String sourceId, BigDecimal value, SourceGeography geography,
                         BigDecimal volume, BigDecimal liquidity, BigDecimal stability,
                         BigDecimal adoption, BigDecimal reputation) {
            this(sourceId, value, geography, null, null, volume, liquidity, stability, adoption, reputation);
        }
        
        public SourceData(String sourceId, BigDecimal value, SourceGeography geography,
                         AfricanRegion africanRegion, String country,
                         BigDecimal volume, BigDecimal liquidity, BigDecimal stability,
                         BigDecimal adoption, BigDecimal reputation) {
            this.sourceId = sourceId;
            this.value = value;
            this.geography = geography;
            this.africanRegion = africanRegion;
            this.country = country;
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
        public AfricanRegion getAfricanRegion() { return africanRegion; }
        public String getCountry() { return country; }
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
        this.regionalCommittees = new ConcurrentHashMap<>();
        this.nodeRegistry = new ConcurrentHashMap<>();
        
        // Initialize 5 regional committees (7 nodes each)
        for (AfricanRegion region : AfricanRegion.values()) {
            regionalCommittees.put(region, new RegionalCommittee(region));
        }
        
        LOGGER.info("OracleAggregator initialized with African Sovereignty enforcement");
        LOGGER.info(String.format("Committee structure: %d regions × %d nodes = %d total nodes",
            AfricanRegion.TOTAL_REGIONS, AfricanRegion.NODES_PER_REGION, AfricanRegion.TOTAL_NODES));
    }
    
    /**
     * Registers a node to the 35-node committee
     */
    public boolean registerNode(NodeInfo node) {
        // Validate geographic distribution BEFORE adding
        if (!wouldViolateGeographicLimits(node)) {
            return false;
        }
        
        // Validate node type distribution
        if (!wouldViolateNodeTypeLimits(node.getNodeType())) {
            return false;
        }
        
        // Register to appropriate regional committee
        RegionalCommittee committee = regionalCommittees.get(node.getRegion());
        if (committee == null || !committee.registerNode(node)) {
            return false;
        }
        
        // Add to global registry
        nodeRegistry.put(node.getNodeId(), node);
        
        LOGGER.info(String.format("Node registered: %s - %s - %s - Community: %s",
            node.getNodeId(), node.getRegion(), node.getNodeType(), node.isCommunityOwned()));
        
        // Log current distribution
        logDistributionStatus();
        
        return true;
    }
    
    /**
     * Checks if adding node would violate geographic distribution limits
     */
    private boolean wouldViolateGeographicLimits(NodeInfo newNode) {
        // Calculate what distribution would be WITH new node
        Map<AfricanRegion, BigDecimal> regionalCounts = new HashMap<>();
        Map<String, BigDecimal> countryCounts = new HashMap<>();
        
        for (NodeInfo node : nodeRegistry.values()) {
            regionalCounts.merge(node.getRegion(), BigDecimal.ONE, BigDecimal::add);
            countryCounts.merge(node.getCountry(), BigDecimal.ONE, BigDecimal::add);
        }
        
        // Add the new node
        regionalCounts.merge(newNode.getRegion(), BigDecimal.ONE, BigDecimal::add);
        countryCounts.merge(newNode.getCountry(), BigDecimal.ONE, BigDecimal::add);
        
        // Validate
        if (!GeographicDistributionValidator.validateRegionalDistribution(regionalCounts)) {
            LOGGER.warning("Adding node would violate regional distribution (33% limit)");
            return false;
        }
        
        if (!GeographicDistributionValidator.validateCountryDistribution(countryCounts)) {
            LOGGER.warning("Adding node would violate country distribution (33% limit)");
            return false;
        }
        
        return true;
    }
    
    /**
     * Checks if adding node would violate node type limits
     */
    private boolean wouldViolateNodeTypeLimits(NodeType newNodeType) {
        Map<NodeType, Long> nodeTypeCounts = nodeRegistry.values().stream()
            .collect(Collectors.groupingBy(NodeInfo::getNodeType, Collectors.counting()));
        
        // Simulate adding new node
        nodeTypeCounts.merge(newNodeType, 1L, Long::sum);
        
        return GeographicDistributionValidator.validateNodeTypeDistribution(nodeTypeCounts);
    }
    
    /**
     * Registers an oracle reporter
     */
    public void registerReporter(OracleReporter reporter) {
        reporters.put(reporter.getReporterId(), reporter);
        LOGGER.info("Reporter registered: " + reporter.getReporterId());
    }
    
    /**
     * Aggregates data from multiple sources with African Sovereignty enforcement
     * 
     * CRITICAL: This enforces all sovereignty requirements
     */
    public AggregatedResult aggregateData(List<SourceData> sources, String dataKey) {
        // Check cache first
        String cacheKey = dataKey + "_" + sources.size();
        AggregatedResult cached = aggregationCache.get(cacheKey);
        if (cached != null && !isCacheExpired(cached)) {
            LOGGER.fine("Returning cached aggregation: " + dataKey);
            return cached;
        }
        
        // SOVEREIGNTY CHECK 1: Minimum sources
        if (sources.size() < MIN_SOURCES_REQUIRED) {
            LOGGER.warning(String.format("Insufficient sources: %d (minimum: %d)", 
                sources.size(), MIN_SOURCES_REQUIRED));
            return null;
        }
        
        // SOVEREIGNTY CHECK 2: Geographic distribution validation
        if (!validateGeographicDistribution(sources)) {
            LOGGER.severe("Geographic distribution validation FAILED - rejecting aggregation");
            return null;
        }
        
        // SOVEREIGNTY CHECK 3: Multi-region consensus requirement
        if (!hasMultiRegionConsensus(sources)) {
            LOGGER.severe("Multi-region consensus FAILED - need sources from 3+ regions");
            return null;
        }
        
        // SOVEREIGNTY CHECK 4: Minimum African sources (for African-related data)
        if (!hasMinimumAfricanSources(sources, dataKey)) {
            LOGGER.warning("Insufficient African sources - may not meet sovereignty requirements");
        }
        
        // Filter outliers
        List<SourceData> filtered = filterOutliers(sources);
        int filteredCount = sources.size() - filtered.size();
        
        if (filtered.isEmpty()) {
            LOGGER.warning("All sources filtered as outliers");
            return null;
        }
        
        // Calculate weights with ENHANCED African prioritization
        calculateWeights(filtered);
        
        // Calculate aggregated values
        BigDecimal weightedAvg = calculateWeightedAverage(filtered);
        BigDecimal median = calculateMedian(filtered);
        BigDecimal weightedMedian = calculateWeightedMedian(filtered);
        BigDecimal stdDev = calculateStandardDeviation(filtered, weightedAvg);
        BigDecimal confidence = calculateConfidenceScore(filtered, stdDev);
        
        // Extract source IDs and weights
        List<String> sourceIds = filtered.stream()
            .map(SourceData::getSourceId)
            .collect(Collectors.toList());
        
        Map<String, BigDecimal> sourceWeights = filtered.stream()
            .collect(Collectors.toMap(
                SourceData::getSourceId,
                SourceData::getCalculatedWeight
            ));
        
        // Create result
        AggregatedResult result = new AggregatedResult(
            weightedAvg, median, weightedMedian, stdDev, confidence,
            filtered.size(), filteredCount, sourceIds, sourceWeights
        );
        
        // Add African sovereignty metadata
        int africanSources = countAfricanSources(filtered);
        Set<AfricanRegion> regions = getRepresentedRegions(filtered);
        
        result.addMetadata("africanSources", africanSources);
        result.addMetadata("africanPercentage", 
            new BigDecimal(africanSources).divide(new BigDecimal(filtered.size()), 4, RoundingMode.HALF_UP));
        result.addMetadata("regionsRepresented", regions.size());
        result.addMetadata("regions", regions);
        result.addMetadata("communitySourcesCount", countCommunityOwnedSources(filtered));
        
        // Cache result
        aggregationCache.put(cacheKey, result);
        
        LOGGER.info(String.format("Aggregation complete: %s - Sources: %d/%d - African: %d (%.0f%%) - Regions: %d",
            dataKey, filtered.size(), sources.size(), africanSources,
            result.getMetadata().get("africanPercentage"), regions.size()));
        
        return result;
    }
    
    /**
     * SOVEREIGNTY CHECK: Validates geographic distribution limits
     */
    private boolean validateGeographicDistribution(List<SourceData> sources) {
        // Group by region and country
        Map<AfricanRegion, Long> regionCounts = sources.stream()
            .filter(s -> s.getAfricanRegion() != null)
            .collect(Collectors.groupingBy(SourceData::getAfricanRegion, Collectors.counting()));
        
        Map<String, Long> countryCounts = sources.stream()
            .filter(s -> s.getCountry() != null)
            .collect(Collectors.groupingBy(SourceData::getCountry, Collectors.counting()));
        
        long totalSources = sources.size();
        
        // Check regional distribution (max 33%)
        for (Map.Entry<AfricanRegion, Long> entry : regionCounts.entrySet()) {
            BigDecimal percentage = new BigDecimal(entry.getValue())
                .divide(new BigDecimal(totalSources), 4, RoundingMode.HALF_UP);
            
            if (percentage.compareTo(MAX_REGION_PERCENTAGE) > 0) {
                LOGGER.severe(String.format("Region %s exceeds 33%% limit: %.2f%%",
                    entry.getKey(), percentage.multiply(new BigDecimal("100"))));
                return false;
            }
        }
        
        // Check country distribution (max 33%)
        for (Map.Entry<String, Long> entry : countryCounts.entrySet()) {
            BigDecimal percentage = new BigDecimal(entry.getValue())
                .divide(new BigDecimal(totalSources), 4, RoundingMode.HALF_UP);
            
            if (percentage.compareTo(MAX_COUNTRY_PERCENTAGE) > 0) {
                LOGGER.severe(String.format("Country %s exceeds 33%% limit: %.2f%%",
                    entry.getKey(), percentage.multiply(new BigDecimal("100"))));
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * SOVEREIGNTY CHECK: Validates multi-region consensus (minimum 3 regions)
     */
    private boolean hasMultiRegionConsensus(List<SourceData> sources) {
        Set<AfricanRegion> regions = getRepresentedRegions(sources);
        return regions.size() >= MIN_REGIONS_REQUIRED;
    }
    
    /**
     * SOVEREIGNTY CHECK: Validates minimum African sources
     * For African currency pairs: need 3+ African sources from 2+ regions
     */
    private boolean hasMinimumAfricanSources(List<SourceData> sources, String dataKey) {
        // Determine if this is African-related data
        boolean isAfricanData = dataKey.contains("NGN") || dataKey.contains("GHS") || 
                               dataKey.contains("ZAR") || dataKey.contains("KES") ||
                               dataKey.contains("XOF") || dataKey.contains("ASU");
        
        if (!isAfricanData) {
            return true; // Not applicable for non-African data
        }
        
        List<SourceData> africanSources = sources.stream()
            .filter(s -> s.getGeography() == SourceGeography.AFRICAN)
            .collect(Collectors.toList());
        
        if (africanSources.size() < 3) {
            LOGGER.warning("Insufficient African sources: " + africanSources.size() + " (need 3+)");
            return false;
        }
        
        Set<AfricanRegion> africanRegions = africanSources.stream()
            .map(SourceData::getAfricanRegion)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        
        if (africanRegions.size() < 2) {
            LOGGER.warning("Insufficient African regional diversity: " + africanRegions.size() + " (need 2+)");
            return false;
        }
        
        return true;
    }
    
    /**
     * Calculates weights with ENHANCED African prioritization (3x multiplier)
     */
    private void calculateWeights(List<SourceData> sources) {
        // Normalize factors for weight calculation
        Map<WeightFactor, Map<String, BigDecimal>> normalizedFactors = normalizeFactors(sources);
        
        // Calculate base weights
        for (SourceData source : sources) {
            BigDecimal weight = calculateBaseWeight(source, normalizedFactors);
            
            // ENHANCED: Apply African prioritization
            if (source.getGeography() == SourceGeography.AFRICAN) {
                weight = applyAfricanPrioritization(weight, source);
            }
            
            source.setCalculatedWeight(weight);
        }
        
        // Normalize weights to sum to 1.0
        normalizeWeights(sources);
    }
    
    /**
     * ENHANCED: Applies African prioritization (50-60% bonus + 3x multiplier)
     */
    private BigDecimal applyAfricanPrioritization(BigDecimal baseWeight, SourceData source) {
        // Calculate reputation-based bonus (50-60% range)
        BigDecimal reputationFactor = source.getReputation()
            .subtract(new BigDecimal("0.5"))
            .multiply(new BigDecimal("0.20")); // Scale 0.5-1.0 rep to 0-0.10 bonus
        
        BigDecimal bonus = AFRICAN_MIN_BONUS.add(reputationFactor);
        bonus = bonus.min(AFRICAN_MAX_BONUS); // Cap at 60%
        
        // Apply bonus
        BigDecimal weightWithBonus = baseWeight.multiply(BigDecimal.ONE.add(bonus));
        
        // Apply 3x priority multiplier
        BigDecimal finalWeight = weightWithBonus.multiply(AFRICAN_PRIORITY_MULTIPLIER);
        
        LOGGER.fine(String.format("African priority applied: %s - Bonus: %.2f%% - Multiplier: 3x - Weight: %.6f -> %.6f",
            source.getSourceId(), bonus.multiply(new BigDecimal("100")), baseWeight, finalWeight));
        
        return finalWeight;
    }
    
    /**
     * Calculates base weight from fair-weighting factors
     */
    private BigDecimal calculateBaseWeight(SourceData source, 
                                          Map<WeightFactor, Map<String, BigDecimal>> normalizedFactors) {
        BigDecimal weight = BigDecimal.ZERO;
        
        for (Map.Entry<WeightFactor, BigDecimal> entry : WEIGHT_FACTORS.entrySet()) {
            WeightFactor factor = entry.getKey();
            BigDecimal factorWeight = entry.getValue();
            BigDecimal normalizedValue = normalizedFactors.get(factor).get(source.getSourceId());
            
            weight = weight.add(normalizedValue.multiply(factorWeight));
        }
        
        return weight;
    }
    
    /**
     * Normalizes factors across all sources
     */
    private Map<WeightFactor, Map<String, BigDecimal>> normalizeFactors(List<SourceData> sources) {
        Map<WeightFactor, Map<String, BigDecimal>> normalized = new HashMap<>();
        
        // Volume
        BigDecimal maxVolume = sources.stream()
            .map(SourceData::getVolume)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ONE);
        
        Map<String, BigDecimal> normalizedVolume = sources.stream()
            .collect(Collectors.toMap(
                SourceData::getSourceId,
                s -> s.getVolume().divide(maxVolume, 10, RoundingMode.HALF_UP)
            ));
        normalized.put(WeightFactor.VOLUME, normalizedVolume);
        
        // Liquidity
        BigDecimal maxLiquidity = sources.stream()
            .map(SourceData::getLiquidity)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ONE);
        
        Map<String, BigDecimal> normalizedLiquidity = sources.stream()
            .collect(Collectors.toMap(
                SourceData::getSourceId,
                s -> s.getLiquidity().divide(maxLiquidity, 10, RoundingMode.HALF_UP)
            ));
        normalized.put(WeightFactor.LIQUIDITY, normalizedLiquidity);
        
        // Stability, Adoption, Reputation (already 0-1)
        normalized.put(WeightFactor.STABILITY, sources.stream()
            .collect(Collectors.toMap(SourceData::getSourceId, SourceData::getStability)));
        normalized.put(WeightFactor.ADOPTION, sources.stream()
            .collect(Collectors.toMap(SourceData::getSourceId, SourceData::getAdoption)));
        normalized.put(WeightFactor.REPUTATION, sources.stream()
            .collect(Collectors.toMap(SourceData::getSourceId, SourceData::getReputation)));
        
        return normalized;
    }
    
    /**
     * Normalizes weights to sum to 1.0
     */
    private void normalizeWeights(List<SourceData> sources) {
        BigDecimal totalWeight = sources.stream()
            .map(SourceData::getCalculatedWeight)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            // Fallback: equal weights
            BigDecimal equalWeight = BigDecimal.ONE.divide(
                new BigDecimal(sources.size()), 10, RoundingMode.HALF_UP);
            sources.forEach(s -> s.setCalculatedWeight(equalWeight));
        } else {
            sources.forEach(s -> s.setCalculatedWeight(
                s.getCalculatedWeight().divide(totalWeight, 10, RoundingMode.HALF_UP)
            ));
        }
    }
    
    /**
     * Filters outliers using Median Absolute Deviation
     */
    private List<SourceData> filterOutliers(List<SourceData> sources) {
        if (sources.size() < MIN_SOURCES_REQUIRED) {
            return sources;
        }
        
        BigDecimal median = calculateMedian(sources);
        
        // Calculate MAD
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
        List<SourceData> sorted = sources.stream()
            .sorted(Comparator.comparing(SourceData::getValue))
            .collect(Collectors.toList());
        
        BigDecimal cumulative = BigDecimal.ZERO;
        BigDecimal target = new BigDecimal("0.5");
        
        for (SourceData source : sorted) {
            cumulative = cumulative.add(source.getCalculatedWeight());
            if (cumulative.compareTo(target) >= 0) {
                return source.getValue();
            }
        }
        
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
     * Calculates confidence score
     */
    private BigDecimal calculateConfidenceScore(List<SourceData> sources, BigDecimal stdDev) {
        BigDecimal sourceScore = new BigDecimal(Math.min(sources.size(), RECOMMENDED_SOURCES))
            .divide(new BigDecimal(RECOMMENDED_SOURCES), 4, RoundingMode.HALF_UP);
        
        BigDecimal agreementScore = BigDecimal.ONE;
        if (stdDev.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal normalized = stdDev.divide(new BigDecimal("0.1"), 4, RoundingMode.HALF_UP);
            agreementScore = BigDecimal.ONE.divide(
                BigDecimal.ONE.add(normalized), 4, RoundingMode.HALF_UP);
        }
        
        return sourceScore.multiply(new BigDecimal("0.7"))
            .add(agreementScore.multiply(new BigDecimal("0.3")));
    }
    
    /**
     * Helper methods for sovereignty tracking
     */
    private int countAfricanSources(List<SourceData> sources) {
        return (int) sources.stream()
            .filter(s -> s.getGeography() == SourceGeography.AFRICAN)
            .count();
    }
    
    private int countCommunityOwnedSources(List<SourceData> sources) {
        return (int) sources.stream()
            .filter(s -> {
                // Check if source's node is community-owned
                NodeInfo node = nodeRegistry.get(s.getSourceId());
                return node != null && node.isCommunityOwned();
            })
            .count();
    }
    
    private Set<AfricanRegion> getRepresentedRegions(List<SourceData> sources) {
        return sources.stream()
            .map(SourceData::getAfricanRegion)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }
    
    private boolean isCacheExpired(AggregatedResult result) {
        return System.currentTimeMillis() - result.getTimestamp() > CACHE_EXPIRY_MS;
    }
    
    public void clearExpiredCache() {
        aggregationCache.entrySet().removeIf(entry -> isCacheExpired(entry.getValue()));
    }
    
    /**
     * Logs current distribution status for monitoring
     */
    private void logDistributionStatus() {
        Map<AfricanRegion, Long> regionCounts = nodeRegistry.values().stream()
            .collect(Collectors.groupingBy(NodeInfo::getRegion, Collectors.counting()));
        
        Map<NodeType, Long> nodeTypeCounts = nodeRegistry.values().stream()
            .collect(Collectors.groupingBy(NodeInfo::getNodeType, Collectors.counting()));
        
        long totalNodes = nodeRegistry.size();
        long communityNodes = nodeTypeCounts.getOrDefault(NodeType.COMMUNITY, 0L);
        long cloudNodes = nodeTypeCounts.getOrDefault(NodeType.CLOUD, 0L);
        
        LOGGER.info("=== NODE DISTRIBUTION STATUS ===");
        LOGGER.info(String.format("Total Nodes: %d/%d", totalNodes, AfricanRegion.TOTAL_NODES));
        LOGGER.info(String.format("Community: %d (%.1f%%) - Target: 50%%+",
            communityNodes, (communityNodes * 100.0 / totalNodes)));
        LOGGER.info(String.format("Cloud: %d (%.1f%%) - Max: 20%%",
            cloudNodes, (cloudNodes * 100.0 / totalNodes)));
        
        for (AfricanRegion region : AfricanRegion.values()) {
            long count = regionCounts.getOrDefault(region, 0L);
            LOGGER.info(String.format("%s: %d/%d nodes (%.1f%%)",
                region, count, AfricanRegion.NODES_PER_REGION, (count * 100.0 / totalNodes)));
        }
        LOGGER.info("==============================");
    }
    
    // Getters
    public Map<AfricanRegion, RegionalCommittee> getRegionalCommittees() {
        return new HashMap<>(regionalCommittees);
    }
    
    public Map<String, NodeInfo> getNodeRegistry() {
        return new HashMap<>(nodeRegistry);
    }
    
    public OracleReporter getReporter(String reporterId) {
        return reporters.get(reporterId);
    }
    
    public List<OracleReporter> getActiveReporters() {
        return reporters.values().stream()
            .filter(OracleReporter::isActive)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets statistics with sovereignty metrics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // Basic stats
        stats.put("totalReporters", reporters.size());
        stats.put("activeReporters", getActiveReporters().size());
        stats.put("cachedAggregations", aggregationCache.size());
        
        // Sovereignty metrics
        stats.put("totalNodes", nodeRegistry.size());
        stats.put("targetNodes", AfricanRegion.TOTAL_NODES);
        
        Map<NodeType, Long> nodeTypeCounts = nodeRegistry.values().stream()
            .collect(Collectors.groupingBy(NodeInfo::getNodeType, Collectors.counting()));
        
        long total = nodeRegistry.size();
        if (total > 0) {
            stats.put("communityPercentage", 
                new BigDecimal(nodeTypeCounts.getOrDefault(NodeType.COMMUNITY, 0L))
                    .divide(new BigDecimal(total), 4, RoundingMode.HALF_UP));
            stats.put("cloudPercentage",
                new BigDecimal(nodeTypeCounts.getOrDefault(NodeType.CLOUD, 0L))
                    .divide(new BigDecimal(total), 4, RoundingMode.HALF_UP));
        }
        
        Map<AfricanRegion, Long> regionCounts = nodeRegistry.values().stream()
            .collect(Collectors.groupingBy(NodeInfo::getRegion, Collectors.counting()));
        stats.put("regionalDistribution", regionCounts);
        
        int committeesWithBFT = (int) regionalCommittees.values().stream()
            .filter(RegionalCommittee::hasBFTConsensus)
            .count();
        stats.put("committeesWithBFT", committeesWithBFT);
        
        return stats;
    }
}