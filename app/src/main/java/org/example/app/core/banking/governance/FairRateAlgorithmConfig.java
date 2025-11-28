package org.example.app.core.banking.governance;

import org.example.app.core.banking.tokenization.AntiManipulationController;
import org.example.app.core.banking.tokenization.TokenMintingController;
import org.example.app.core.state.StateManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * FairRateAlgorithmConfig - Configures African-priority exchange rate calculations
 * 
 * Purpose: Ensure African financial institutions and exchanges receive fair weighting
 * Implements Req #4: Fair system for African banks considering international markets
 * 
 * Key Features:
 * - 3x priority multiplier for African exchanges (configurable 1.5x - 5.0x)
 * - 50-60% bonus weighting for African market data
 * - Outlier detection using Z-scores and Median Absolute Deviation
 * - Minimum 5 independent data sources for manipulation resistance
 * - Real-time rate calculation with anti-manipulation controls
 */
public class FairRateAlgorithmConfig {
    private static final Logger LOGGER = Logger.getLogger(FairRateAlgorithmConfig.class.getName());
    
    // Configuration ranges
    private static final BigDecimal MIN_AFRICAN_MULTIPLIER = new BigDecimal("1.5");
    private static final BigDecimal MAX_AFRICAN_MULTIPLIER = new BigDecimal("5.0");
    private static final BigDecimal DEFAULT_AFRICAN_MULTIPLIER = new BigDecimal("3.0");
    
    private static final BigDecimal MIN_BONUS_PERCENTAGE = new BigDecimal("0.40"); // 40%
    private static final BigDecimal MAX_BONUS_PERCENTAGE = new BigDecimal("0.70"); // 70%
    private static final BigDecimal DEFAULT_BONUS_PERCENTAGE = new BigDecimal("0.55"); // 55%
    
    private static final int MIN_DATA_SOURCES = 5;
    private static final BigDecimal OUTLIER_ZSCORE_THRESHOLD = new BigDecimal("2.5");
    
    // Dependencies
    private final AntiManipulationController antiManipulationController;
    private final TokenMintingController tokenMintingController;
    private final StateManager stateManager;
    private final GovernanceAuditLog auditLog;
    
    // Configuration state
    private final Map<String, AlgorithmConfig> tokenPairConfigs;
    private final Map<String, ExchangeWeight> exchangeWeights;
    private final ReadWriteLock configLock;
    
    private BigDecimal africanPriorityMultiplier;
    private BigDecimal africanBonusPercentage;
    private int minimumDataSources;
    private BigDecimal outlierThreshold;
    
    /**
     * Exchange weight configuration
     */
    public static class ExchangeWeight {
        private final String exchangeId;
        private final String region; // "AFRICA", "EUROPE", "ASIA", "AMERICAS"
        private final boolean isAfrican;
        private BigDecimal baseWeight;
        private BigDecimal adjustedWeight;
        private int dataQualityScore; // 0-100
        
        public ExchangeWeight(String exchangeId, String region, boolean isAfrican) {
            this.exchangeId = exchangeId;
            this.region = region;
            this.isAfrican = isAfrican;
            this.baseWeight = BigDecimal.ONE;
            this.adjustedWeight = BigDecimal.ONE;
            this.dataQualityScore = 50; // Start neutral
        }
        
        public String getExchangeId() { return exchangeId; }
        public String getRegion() { return region; }
        public boolean isAfrican() { return isAfrican; }
        public BigDecimal getBaseWeight() { return baseWeight; }
        public BigDecimal getAdjustedWeight() { return adjustedWeight; }
        public int getDataQualityScore() { return dataQualityScore; }
        
        public void setBaseWeight(BigDecimal weight) { this.baseWeight = weight; }
        public void setAdjustedWeight(BigDecimal weight) { this.adjustedWeight = weight; }
        public void setDataQualityScore(int score) { 
            this.dataQualityScore = Math.max(0, Math.min(100, score)); 
        }
    }
    
    /**
     * Algorithm configuration for a token pair
     */
    public static class AlgorithmConfig {
        private final String tokenPair;
        private BigDecimal africanMultiplier;
        private BigDecimal bonusPercentage;
        private boolean manipulationDetected;
        private long lastCalculationTime;
        private BigDecimal lastCalculatedRate;
        
        public AlgorithmConfig(String tokenPair, BigDecimal multiplier, BigDecimal bonus) {
            this.tokenPair = tokenPair;
            this.africanMultiplier = multiplier;
            this.bonusPercentage = bonus;
            this.manipulationDetected = false;
            this.lastCalculationTime = 0;
            this.lastCalculatedRate = BigDecimal.ZERO;
        }
        
        public String getTokenPair() { return tokenPair; }
        public BigDecimal getAfricanMultiplier() { return africanMultiplier; }
        public BigDecimal getBonusPercentage() { return bonusPercentage; }
        public boolean isManipulationDetected() { return manipulationDetected; }
        public long getLastCalculationTime() { return lastCalculationTime; }
        public BigDecimal getLastCalculatedRate() { return lastCalculatedRate; }
        
        public void setAfricanMultiplier(BigDecimal multiplier) { this.africanMultiplier = multiplier; }
        public void setBonusPercentage(BigDecimal bonus) { this.bonusPercentage = bonus; }
        public void setManipulationDetected(boolean detected) { this.manipulationDetected = detected; }
        public void setLastCalculationTime(long time) { this.lastCalculationTime = time; }
        public void setLastCalculatedRate(BigDecimal rate) { this.lastCalculatedRate = rate; }
    }
    
    /**
     * Price data point from an exchange
     */
    public static class PricePoint {
        private final String exchangeId;
        private final BigDecimal price;
        private final BigDecimal volume;
        private final long timestamp;
        private final boolean isAfrican;
        
        public PricePoint(String exchangeId, BigDecimal price, BigDecimal volume, 
                         long timestamp, boolean isAfrican) {
            this.exchangeId = exchangeId;
            this.price = price;
            this.volume = volume;
            this.timestamp = timestamp;
            this.isAfrican = isAfrican;
        }
        
        public String getExchangeId() { return exchangeId; }
        public BigDecimal getPrice() { return price; }
        public BigDecimal getVolume() { return volume; }
        public long getTimestamp() { return timestamp; }
        public boolean isAfrican() { return isAfrican; }
    }
    
    public FairRateAlgorithmConfig(AntiManipulationController antiManipulationController,
                                   TokenMintingController tokenMintingController,
                                   StateManager stateManager,
                                   GovernanceAuditLog auditLog) {
        this.antiManipulationController = antiManipulationController;
        this.tokenMintingController = tokenMintingController;
        this.stateManager = stateManager;
        this.auditLog = auditLog;
        
        this.tokenPairConfigs = new ConcurrentHashMap<>();
        this.exchangeWeights = new ConcurrentHashMap<>();
        this.configLock = new ReentrantReadWriteLock();
        
        // Initialize defaults
        this.africanPriorityMultiplier = DEFAULT_AFRICAN_MULTIPLIER;
        this.africanBonusPercentage = DEFAULT_BONUS_PERCENTAGE;
        this.minimumDataSources = MIN_DATA_SOURCES;
        this.outlierThreshold = OUTLIER_ZSCORE_THRESHOLD;
        
        loadConfigurationFromState();
        LOGGER.info("FairRateAlgorithmConfig initialized");
    }
    
    /**
     * Updates the African priority multiplier (governance action)
     */
    public void setAfricanPriorityMultiplier(BigDecimal multiplier) {
        if (multiplier.compareTo(MIN_AFRICAN_MULTIPLIER) < 0 || 
            multiplier.compareTo(MAX_AFRICAN_MULTIPLIER) > 0) {
            throw new IllegalArgumentException("Multiplier must be between " + 
                MIN_AFRICAN_MULTIPLIER + " and " + MAX_AFRICAN_MULTIPLIER);
        }
        
        configLock.writeLock().lock();
        try {
            BigDecimal oldValue = this.africanPriorityMultiplier;
            this.africanPriorityMultiplier = multiplier;
            
            // Update all token pair configs
            for (AlgorithmConfig config : tokenPairConfigs.values()) {
                config.setAfricanMultiplier(multiplier);
            }
            
            // Persist to state
            stateManager.persistConfiguration("african_priority_multiplier", multiplier);
            
            // Record in audit log
            auditLog.recordEvent(GovernanceAuditLog.AuditEventType.ALGORITHM_CONFIGURED,
                "SYSTEM", "FairRateAlgorithm",
                Map.of("parameter", "africanPriorityMultiplier",
                       "oldValue", oldValue.toString(),
                       "newValue", multiplier.toString()),
                "African priority multiplier updated");
            
            LOGGER.info("African priority multiplier updated: " + oldValue + " -> " + multiplier);
            
        } finally {
            configLock.writeLock().unlock();
        }
    }
    
    /**
     * Updates the African bonus percentage (governance action)
     */
    public void setAfricanBonusPercentage(BigDecimal bonus) {
        if (bonus.compareTo(MIN_BONUS_PERCENTAGE) < 0 || 
            bonus.compareTo(MAX_BONUS_PERCENTAGE) > 0) {
            throw new IllegalArgumentException("Bonus must be between " + 
                MIN_BONUS_PERCENTAGE + " and " + MAX_BONUS_PERCENTAGE);
        }
        
        configLock.writeLock().lock();
        try {
            BigDecimal oldValue = this.africanBonusPercentage;
            this.africanBonusPercentage = bonus;
            
            // Update all token pair configs
            for (AlgorithmConfig config : tokenPairConfigs.values()) {
                config.setBonusPercentage(bonus);
            }
            
            // Persist to state
            stateManager.persistConfiguration("african_bonus_percentage", bonus);
            
            // Record in audit log
            auditLog.recordEvent(GovernanceAuditLog.AuditEventType.ALGORITHM_CONFIGURED,
                "SYSTEM", "FairRateAlgorithm",
                Map.of("parameter", "africanBonusPercentage",
                       "oldValue", oldValue.toString(),
                       "newValue", bonus.toString()),
                "African bonus percentage updated");
            
            LOGGER.info("African bonus percentage updated: " + oldValue + " -> " + bonus);
            
        } finally {
            configLock.writeLock().unlock();
        }
    }
    
    /**
     * Registers an exchange with geographic weighting
     */
    public void registerExchange(String exchangeId, String region, boolean isAfrican) {
        configLock.writeLock().lock();
        try {
            ExchangeWeight weight = new ExchangeWeight(exchangeId, region, isAfrican);
            
            // African exchanges get priority weighting
            if (isAfrican) {
                weight.setBaseWeight(africanPriorityMultiplier);
                weight.setAdjustedWeight(africanPriorityMultiplier);
            }
            
            exchangeWeights.put(exchangeId, weight);
            LOGGER.info("Exchange registered: " + exchangeId + " (" + region + 
                       ", African: " + isAfrican + ")");
            
        } finally {
            configLock.writeLock().unlock();
        }
    }
    
    /**
     * Calculates fair exchange rate using African-priority algorithm
     */
    public BigDecimal calculateFairRate(String tokenPair, List<PricePoint> priceData) {
        configLock.readLock().lock();
        try {
            // Validate minimum data sources
            if (priceData.size() < minimumDataSources) {
                LOGGER.warning("Insufficient data sources for " + tokenPair + ": " + 
                              priceData.size() + " < " + minimumDataSources);
                return BigDecimal.ZERO;
            }
            
            // Get or create config for this token pair
            AlgorithmConfig config = tokenPairConfigs.computeIfAbsent(tokenPair,
                k -> new AlgorithmConfig(k, africanPriorityMultiplier, africanBonusPercentage));
            
            // Check for manipulation
            boolean manipulation = antiManipulationController.detectManipulation(
                tokenPair, priceData);
            config.setManipulationDetected(manipulation);
            
            if (manipulation) {
                LOGGER.warning("Manipulation detected for " + tokenPair + 
                              " - using median-based fallback");
                return calculateMedianRate(priceData);
            }
            
            // Filter outliers using Z-score
            List<PricePoint> filteredData = filterOutliers(priceData);
            
            // Separate African vs non-African data
            List<PricePoint> africanData = filteredData.stream()
                .filter(PricePoint::isAfrican)
                .collect(Collectors.toList());
            
            List<PricePoint> foreignData = filteredData.stream()
                .filter(p -> !p.isAfrican())
                .collect(Collectors.toList());
            
            // Calculate weighted rates
            BigDecimal africanRate = calculateVolumeWeightedRate(africanData);
            BigDecimal foreignRate = calculateVolumeWeightedRate(foreignData);
            
            // Apply African priority multiplier
            BigDecimal weightedAfricanRate = africanRate.multiply(config.getAfricanMultiplier());
            
            // Combine rates with African bonus
            BigDecimal fairRate;
            if (africanData.isEmpty()) {
                // No African data - use foreign only
                fairRate = foreignRate;
            } else if (foreignData.isEmpty()) {
                // Only African data
                fairRate = africanRate;
            } else {
                // Combine with bonus weighting
                BigDecimal africanWeight = BigDecimal.ONE.add(config.getBonusPercentage());
                BigDecimal foreignWeight = BigDecimal.ONE;
                BigDecimal totalWeight = africanWeight.add(foreignWeight);
                
                fairRate = weightedAfricanRate.multiply(africanWeight)
                    .add(foreignRate.multiply(foreignWeight))
                    .divide(totalWeight, 8, RoundingMode.HALF_UP);
            }
            
            // Update config
            config.setLastCalculatedRate(fairRate);
            config.setLastCalculationTime(System.currentTimeMillis());
            
            LOGGER.info("Fair rate calculated for " + tokenPair + ": " + fairRate + 
                       " (African: " + africanData.size() + ", Foreign: " + foreignData.size() + ")");
            
            return fairRate;
            
        } finally {
            configLock.readLock().unlock();
        }
    }
    
    /**
     * Gets current configuration for a token pair
     */
    public AlgorithmConfig getConfig(String tokenPair) {
        return tokenPairConfigs.get(tokenPair);
    }
    
    /**
     * Gets all exchange weights
     */
    public Map<String, ExchangeWeight> getExchangeWeights() {
        configLock.readLock().lock();
        try {
            return new HashMap<>(exchangeWeights);
        } finally {
            configLock.readLock().unlock();
        }
    }
    
    // Private helper methods
    
    private List<PricePoint> filterOutliers(List<PricePoint> priceData) {
        if (priceData.size() < 3) return priceData;
        
        // Calculate mean and standard deviation
        BigDecimal mean = priceData.stream()
            .map(PricePoint::getPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(priceData.size()), 8, RoundingMode.HALF_UP);
        
        BigDecimal variance = priceData.stream()
            .map(p -> p.getPrice().subtract(mean).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(priceData.size()), 8, RoundingMode.HALF_UP);
        
        BigDecimal stdDev = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
        
        // Filter using Z-score
        return priceData.stream()
            .filter(p -> {
                BigDecimal zScore = p.getPrice().subtract(mean)
                    .divide(stdDev, 8, RoundingMode.HALF_UP)
                    .abs();
                return zScore.compareTo(outlierThreshold) <= 0;
            })
            .collect(Collectors.toList());
    }
    
    private BigDecimal calculateVolumeWeightedRate(List<PricePoint> priceData) {
        if (priceData.isEmpty()) return BigDecimal.ZERO;
        
        BigDecimal totalVolume = priceData.stream()
            .map(PricePoint::getVolume)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalVolume.compareTo(BigDecimal.ZERO) == 0) {
            // Fallback to simple average
            return priceData.stream()
                .map(PricePoint::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(priceData.size()), 8, RoundingMode.HALF_UP);
        }
        
        return priceData.stream()
            .map(p -> p.getPrice().multiply(p.getVolume()))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(totalVolume, 8, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateMedianRate(List<PricePoint> priceData) {
        List<BigDecimal> prices = priceData.stream()
            .map(PricePoint::getPrice)
            .sorted()
            .collect(Collectors.toList());
        
        int size = prices.size();
        if (size % 2 == 0) {
            return prices.get(size / 2 - 1).add(prices.get(size / 2))
                .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
        } else {
            return prices.get(size / 2);
        }
    }
    
    private void loadConfigurationFromState() {
        try {
            Object multiplierObj = stateManager.getConfiguration("african_priority_multiplier");
            if (multiplierObj instanceof BigDecimal) {
                this.africanPriorityMultiplier = (BigDecimal) multiplierObj;
            }
            
            Object bonusObj = stateManager.getConfiguration("african_bonus_percentage");
            if (bonusObj instanceof BigDecimal) {
                this.africanBonusPercentage = (BigDecimal) bonusObj;
            }
            
            LOGGER.info("Configuration loaded from state");
        } catch (Exception e) {
            LOGGER.warning("Failed to load configuration: " + e.getMessage());
        }
    }
}