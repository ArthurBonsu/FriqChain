package org.example.app.core.blockchain.consensus;

import org.example.app.core.banking.tokenization.ExchangeRateOracle;
import org.example.app.core.state.BlockchainState;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * FairWeightingValidator - Ensures fair prioritization of African currencies and exchanges
 * 
 * Core Principles:
 * - African exchanges get higher weight than global exchanges
 * - African currencies prioritized in ASU basket calculations
 * - Prevents single country/currency from dominating (33% max)
 * - Implements geographic distribution requirements
 * - Validates fair rate calculations favor African economic output
 * 
 * Requirements from Guidebook:
 * - Maximum 33% control per country/region
 * - Fair-weighting algorithms prioritize African exchanges
 * - Dynamic rebalancing when weights become unfair
 */
public class FairWeightingValidator {
    private static final Logger LOGGER = Logger.getLogger(FairWeightingValidator.class.getName());
    
    // Geographic weight priorities (higher = more priority)
    private static final BigDecimal AFRICAN_EXCHANGE_WEIGHT = new BigDecimal("3.0");
    private static final BigDecimal GLOBAL_EXCHANGE_WEIGHT = new BigDecimal("1.0");
    
    // Maximum concentration limits
    private static final BigDecimal MAX_SINGLE_COUNTRY_WEIGHT = new BigDecimal("0.33"); // 33%
    private static final BigDecimal MAX_SINGLE_CURRENCY_WEIGHT = new BigDecimal("0.35"); // 35%
    private static final BigDecimal MAX_SINGLE_EXCHANGE_WEIGHT = new BigDecimal("0.25"); // 25%
    
    // Minimum African representation
    private static final BigDecimal MIN_AFRICAN_TOTAL_WEIGHT = new BigDecimal("0.60"); // 60%
    
    // Weight adjustment thresholds
    private static final BigDecimal REBALANCE_THRESHOLD = new BigDecimal("0.05"); // 5% deviation triggers rebalance
    
    // Dependencies
    private final BlockchainState blockchainState;
    private final ExchangeRateOracle exchangeRateOracle;
    
    // Weight tracking
    private final Map<String, CurrencyWeight> currencyWeights;
    private final Map<String, ExchangeWeight> exchangeWeights;
    private final Map<String, CountryWeight> countryWeights;
    
    /**
     * Represents a currency's weight in the system
     */
    public static class CurrencyWeight {
        private final String currencyCode;
        private final String countryCode;
        private final boolean isAfrican;
        private BigDecimal currentWeight;
        private BigDecimal targetWeight;
        private BigDecimal tradingVolume;
        private long lastUpdate;
        
        public CurrencyWeight(String currencyCode, String countryCode, boolean isAfrican) {
            this.currencyCode = currencyCode;
            this.countryCode = countryCode;
            this.isAfrican = isAfrican;
            this.currentWeight = BigDecimal.ZERO;
            this.targetWeight = BigDecimal.ZERO;
            this.tradingVolume = BigDecimal.ZERO;
            this.lastUpdate = System.currentTimeMillis();
        }
        
        public String getCurrencyCode() { return currencyCode; }
        public String getCountryCode() { return countryCode; }
        public boolean isAfrican() { return isAfrican; }
        public BigDecimal getCurrentWeight() { return currentWeight; }
        public BigDecimal getTargetWeight() { return targetWeight; }
        public BigDecimal getTradingVolume() { return tradingVolume; }
        public long getLastUpdate() { return lastUpdate; }
        
        public void setCurrentWeight(BigDecimal weight) { 
            this.currentWeight = weight;
            this.lastUpdate = System.currentTimeMillis();
        }
        
        public void setTargetWeight(BigDecimal weight) { this.targetWeight = weight; }
        public void addTradingVolume(BigDecimal volume) { this.tradingVolume = this.tradingVolume.add(volume); }
    }
    
    /**
     * Represents an exchange's weight in the system
     */
    public static class ExchangeWeight {
        private final String exchangeId;
        private final String country;
        private final boolean isAfrican;
        private BigDecimal currentWeight;
        private BigDecimal liquidityScore;
        private BigDecimal reliabilityScore;
        private long lastUpdate;
        
        public ExchangeWeight(String exchangeId, String country, boolean isAfrican) {
            this.exchangeId = exchangeId;
            this.country = country;
            this.isAfrican = isAfrican;
            this.currentWeight = BigDecimal.ZERO;
            this.liquidityScore = BigDecimal.ONE;
            this.reliabilityScore = BigDecimal.ONE;
            this.lastUpdate = System.currentTimeMillis();
        }
        
        public String getExchangeId() { return exchangeId; }
        public String getCountry() { return country; }
        public boolean isAfrican() { return isAfrican; }
        public BigDecimal getCurrentWeight() { return currentWeight; }
        public BigDecimal getLiquidityScore() { return liquidityScore; }
        public BigDecimal getReliabilityScore() { return reliabilityScore; }
        
        public void setCurrentWeight(BigDecimal weight) { 
            this.currentWeight = weight;
            this.lastUpdate = System.currentTimeMillis();
        }
        
        public void setLiquidityScore(BigDecimal score) { this.liquidityScore = score; }
        public void setReliabilityScore(BigDecimal score) { this.reliabilityScore = score; }
        
        public BigDecimal calculateEffectiveWeight() {
            BigDecimal baseWeight = isAfrican ? AFRICAN_EXCHANGE_WEIGHT : GLOBAL_EXCHANGE_WEIGHT;
            return baseWeight.multiply(liquidityScore).multiply(reliabilityScore);
        }
    }
    
    /**
     * Represents a country's weight in the system
     */
    public static class CountryWeight {
        private final String countryCode;
        private final boolean isAfrican;
        private BigDecimal currentWeight;
        private int validatorCount;
        private int bankCount;
        
        public CountryWeight(String countryCode, boolean isAfrican) {
            this.countryCode = countryCode;
            this.isAfrican = isAfrican;
            this.currentWeight = BigDecimal.ZERO;
            this.validatorCount = 0;
            this.bankCount = 0;
        }
        
        public String getCountryCode() { return countryCode; }
        public boolean isAfrican() { return isAfrican; }
        public BigDecimal getCurrentWeight() { return currentWeight; }
        public int getValidatorCount() { return validatorCount; }
        public int getBankCount() { return bankCount; }
        
        public void setCurrentWeight(BigDecimal weight) { this.currentWeight = weight; }
        public void incrementValidatorCount() { this.validatorCount++; }
        public void incrementBankCount() { this.bankCount++; }
    }
    
    /**
     * Validation result for weight checks
     */
    public static class WeightValidationResult {
        private final boolean valid;
        private final String failureReason;
        private final Map<String, BigDecimal> violatedLimits;
        
        private WeightValidationResult(boolean valid, String reason, Map<String, BigDecimal> limits) {
            this.valid = valid;
            this.failureReason = reason;
            this.violatedLimits = limits != null ? limits : new HashMap<>();
        }
        
        public static WeightValidationResult success() {
            return new WeightValidationResult(true, null, new HashMap<>());
        }
        
        public static WeightValidationResult failure(String reason, Map<String, BigDecimal> limits) {
            return new WeightValidationResult(false, reason, limits);
        }
        
        public boolean isValid() { return valid; }
        public String getFailureReason() { return failureReason; }
        public Map<String, BigDecimal> getViolatedLimits() { return violatedLimits; }
    }
    
    /**
     * Constructor
     */
    public FairWeightingValidator(BlockchainState blockchainState,
                                  ExchangeRateOracle exchangeRateOracle) {
        this.blockchainState = blockchainState;
        this.exchangeRateOracle = exchangeRateOracle;
        this.currencyWeights = new ConcurrentHashMap<>();
        this.exchangeWeights = new ConcurrentHashMap<>();
        this.countryWeights = new ConcurrentHashMap<>();
        
        initializeAfricanCurrencies();
        LOGGER.info("FairWeightingValidator initialized");
    }
    
    /**
     * Initialize African currencies with higher base weights
     */
    private void initializeAfricanCurrencies() {
        // Major African currencies
        String[][] africanCurrencies = {
            {"NGN", "NG"}, // Nigerian Naira
            {"ZAR", "ZA"}, // South African Rand
            {"EGP", "EG"}, // Egyptian Pound
            {"KES", "KE"}, // Kenyan Shilling
            {"GHS", "GH"}, // Ghanaian Cedi
            {"TZS", "TZ"}, // Tanzanian Shilling
            {"UGX", "UG"}, // Ugandan Shilling
            {"MAD", "MA"}, // Moroccan Dirham
            {"ETB", "ET"}, // Ethiopian Birr
            {"AOA", "AO"}, // Angolan Kwanza
            {"XOF", "SN"}, // West African CFA Franc
            {"XAF", "CM"}  // Central African CFA Franc
        };
        
        for (String[] currency : africanCurrencies) {
            CurrencyWeight weight = new CurrencyWeight(currency[0], currency[1], true);
            currencyWeights.put(currency[0], weight);
        }
        
        LOGGER.info("Initialized " + africanCurrencies.length + " African currencies");
    }
    
    /**
     * Validates that weights are fair and within limits
     */
    public WeightValidationResult validateWeights() {
        // 1. Check country concentration
        WeightValidationResult countryCheck = validateCountryConcentration();
        if (!countryCheck.isValid()) {
            return countryCheck;
        }
        
        // 2. Check currency concentration
        WeightValidationResult currencyCheck = validateCurrencyConcentration();
        if (!currencyCheck.isValid()) {
            return currencyCheck;
        }
        
        // 3. Check exchange concentration
        WeightValidationResult exchangeCheck = validateExchangeConcentration();
        if (!exchangeCheck.isValid()) {
            return exchangeCheck;
        }
        
        // 4. Check minimum African representation
        WeightValidationResult africanCheck = validateAfricanRepresentation();
        if (!africanCheck.isValid()) {
            return africanCheck;
        }
        
        LOGGER.fine("Weight validation passed");
        return WeightValidationResult.success();
    }
    
    /**
     * Validates country concentration doesn't exceed 33%
     */
    private WeightValidationResult validateCountryConcentration() {
        Map<String, BigDecimal> violatedLimits = new HashMap<>();
        
        for (CountryWeight country : countryWeights.values()) {
            if (country.getCurrentWeight().compareTo(MAX_SINGLE_COUNTRY_WEIGHT) > 0) {
                violatedLimits.put(country.getCountryCode(), country.getCurrentWeight());
            }
        }
        
        if (!violatedLimits.isEmpty()) {
            return WeightValidationResult.failure(
                "Country concentration exceeded: " + violatedLimits.keySet(),
                violatedLimits
            );
        }
        
        return WeightValidationResult.success();
    }
    
    /**
     * Validates currency concentration doesn't exceed 35%
     */
    private WeightValidationResult validateCurrencyConcentration() {
        Map<String, BigDecimal> violatedLimits = new HashMap<>();
        
        for (CurrencyWeight currency : currencyWeights.values()) {
            if (currency.getCurrentWeight().compareTo(MAX_SINGLE_CURRENCY_WEIGHT) > 0) {
                violatedLimits.put(currency.getCurrencyCode(), currency.getCurrentWeight());
            }
        }
        
        if (!violatedLimits.isEmpty()) {
            return WeightValidationResult.failure(
                "Currency concentration exceeded: " + violatedLimits.keySet(),
                violatedLimits
            );
        }
        
        return WeightValidationResult.success();
    }
    
    /**
     * Validates exchange concentration doesn't exceed 25%
     */
    private WeightValidationResult validateExchangeConcentration() {
        Map<String, BigDecimal> violatedLimits = new HashMap<>();
        
        for (ExchangeWeight exchange : exchangeWeights.values()) {
            if (exchange.getCurrentWeight().compareTo(MAX_SINGLE_EXCHANGE_WEIGHT) > 0) {
                violatedLimits.put(exchange.getExchangeId(), exchange.getCurrentWeight());
            }
        }
        
        if (!violatedLimits.isEmpty()) {
            return WeightValidationResult.failure(
                "Exchange concentration exceeded: " + violatedLimits.keySet(),
                violatedLimits
            );
        }
        
        return WeightValidationResult.success();
    }
    
    /**
     * Validates minimum African representation (60%)
     */
    private WeightValidationResult validateAfricanRepresentation() {
        BigDecimal africanWeight = currencyWeights.values().stream()
            .filter(CurrencyWeight::isAfrican)
            .map(CurrencyWeight::getCurrentWeight)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalWeight = currencyWeights.values().stream()
            .map(CurrencyWeight::getCurrentWeight)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return WeightValidationResult.success(); // No weights yet
        }
        
        BigDecimal africanPercentage = africanWeight.divide(totalWeight, 4, RoundingMode.HALF_UP);
        
        if (africanPercentage.compareTo(MIN_AFRICAN_TOTAL_WEIGHT) < 0) {
            Map<String, BigDecimal> limits = new HashMap<>();
            limits.put("africanPercentage", africanPercentage);
            limits.put("minimumRequired", MIN_AFRICAN_TOTAL_WEIGHT);
            
            return WeightValidationResult.failure(
                String.format("African representation too low: %.2f%% (minimum: %.2f%%)",
                    africanPercentage.multiply(new BigDecimal(100)).doubleValue(),
                    MIN_AFRICAN_TOTAL_WEIGHT.multiply(new BigDecimal(100)).doubleValue()),
                limits
            );
        }
        
        return WeightValidationResult.success();
    }
    
    /**
     * Calculates fair weight for a currency based on African priority
     */
    public BigDecimal calculateFairCurrencyWeight(String currencyCode, 
                                                  BigDecimal marketCap,
                                                  BigDecimal tradingVolume,
                                                  BigDecimal gdpContribution) {
        CurrencyWeight currency = currencyWeights.get(currencyCode);
        
        if (currency == null) {
            // Non-African currency
            currency = new CurrencyWeight(currencyCode, "XX", false);
            currencyWeights.put(currencyCode, currency);
        }
        
        // Base weight calculation
        BigDecimal baseWeight = marketCap.add(tradingVolume).add(gdpContribution)
            .divide(new BigDecimal(3), 8, RoundingMode.HALF_UP);
        
        // Apply African multiplier
        if (currency.isAfrican()) {
            baseWeight = baseWeight.multiply(AFRICAN_EXCHANGE_WEIGHT);
        }
        
        // Normalize to prevent exceeding max
        if (baseWeight.compareTo(MAX_SINGLE_CURRENCY_WEIGHT) > 0) {
            baseWeight = MAX_SINGLE_CURRENCY_WEIGHT;
        }
        
        currency.setTargetWeight(baseWeight);
        return baseWeight;
    }
    
    /**
     * Calculates fair weight for an exchange
     */
    public BigDecimal calculateFairExchangeWeight(String exchangeId,
                                                  String country,
                                                  boolean isAfrican,
                                                  BigDecimal liquidity,
                                                  BigDecimal reliability) {
        ExchangeWeight exchange = exchangeWeights.computeIfAbsent(
            exchangeId,
            k -> new ExchangeWeight(exchangeId, country, isAfrican)
        );
        
        exchange.setLiquidityScore(liquidity);
        exchange.setReliabilityScore(reliability);
        
        BigDecimal effectiveWeight = exchange.calculateEffectiveWeight();
        
        // Normalize
        if (effectiveWeight.compareTo(MAX_SINGLE_EXCHANGE_WEIGHT) > 0) {
            effectiveWeight = MAX_SINGLE_EXCHANGE_WEIGHT;
        }
        
        exchange.setCurrentWeight(effectiveWeight);
        return effectiveWeight;
    }
    
    /**
     * Rebalances weights when concentration becomes unfair
     */
    public Map<String, BigDecimal> rebalanceWeights() {
        LOGGER.info("Starting weight rebalancing");
        
        Map<String, BigDecimal> adjustments = new HashMap<>();
        
        // 1. Identify over-concentrated entities
        List<CurrencyWeight> overConcentrated = currencyWeights.values().stream()
            .filter(c -> c.getCurrentWeight().compareTo(MAX_SINGLE_CURRENCY_WEIGHT) > 0)
            .sorted((a, b) -> b.getCurrentWeight().compareTo(a.getCurrentWeight()))
            .toList();
        
        // 2. Calculate total excess weight
        BigDecimal totalExcess = overConcentrated.stream()
            .map(c -> c.getCurrentWeight().subtract(MAX_SINGLE_CURRENCY_WEIGHT))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 3. Reduce over-concentrated weights
        for (CurrencyWeight currency : overConcentrated) {
            BigDecimal newWeight = MAX_SINGLE_CURRENCY_WEIGHT.multiply(new BigDecimal("0.95")); // 5% below max
            BigDecimal reduction = currency.getCurrentWeight().subtract(newWeight);
            currency.setCurrentWeight(newWeight);
            adjustments.put(currency.getCurrencyCode(), reduction.negate());
        }
        
        // 4. Distribute to under-weighted African currencies
        List<CurrencyWeight> underWeighted = currencyWeights.values().stream()
            .filter(CurrencyWeight::isAfrican)
            .filter(c -> c.getCurrentWeight().compareTo(c.getTargetWeight()) < 0)
            .sorted((a, b) -> a.getCurrentWeight().compareTo(b.getCurrentWeight()))
            .toList();
        
        if (!underWeighted.isEmpty()) {
            BigDecimal redistributionPerCurrency = totalExcess.divide(
                new BigDecimal(underWeighted.size()), 8, RoundingMode.HALF_UP
            );
            
            for (CurrencyWeight currency : underWeighted) {
                BigDecimal newWeight = currency.getCurrentWeight().add(redistributionPerCurrency);
                // Ensure doesn't exceed max
                if (newWeight.compareTo(MAX_SINGLE_CURRENCY_WEIGHT) > 0) {
                    newWeight = MAX_SINGLE_CURRENCY_WEIGHT;
                }
                BigDecimal increase = newWeight.subtract(currency.getCurrentWeight());
                currency.setCurrentWeight(newWeight);
                adjustments.put(currency.getCurrencyCode(), increase);
            }
        }
        
        LOGGER.info("Rebalancing complete. Adjusted " + adjustments.size() + " currencies");
        return adjustments;
    }
    
    /**
     * Updates currency weight based on trading activity
     */
    public void updateCurrencyWeight(String currencyCode, BigDecimal tradingVolume) {
        CurrencyWeight currency = currencyWeights.get(currencyCode);
        if (currency != null) {
            currency.addTradingVolume(tradingVolume);
        }
    }
    
    /**
     * Gets weight distribution statistics
     */
    public Map<String, Object> getWeightStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // African vs non-African breakdown
        BigDecimal africanWeight = currencyWeights.values().stream()
            .filter(CurrencyWeight::isAfrican)
            .map(CurrencyWeight::getCurrentWeight)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalWeight = currencyWeights.values().stream()
            .map(CurrencyWeight::getCurrentWeight)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal nonAfricanWeight = totalWeight.subtract(africanWeight);
        
        stats.put("totalCurrencies", currencyWeights.size());
        stats.put("africanCurrencies", currencyWeights.values().stream()
            .filter(CurrencyWeight::isAfrican).count());
        stats.put("africanWeight", africanWeight);
        stats.put("nonAfricanWeight", nonAfricanWeight);
        stats.put("africanPercentage", totalWeight.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
            africanWeight.divide(totalWeight, 4, RoundingMode.HALF_UP));
        
        // Top weighted currencies
        List<String> topCurrencies = currencyWeights.values().stream()
            .sorted((a, b) -> b.getCurrentWeight().compareTo(a.getCurrentWeight()))
            .limit(5)
            .map(c -> c.getCurrencyCode() + ":" + c.getCurrentWeight())
            .toList();
        stats.put("topCurrencies", topCurrencies);
        
        return stats;
    }
    
    /**
     * Registers a new exchange
     */
    public void registerExchange(String exchangeId, String country, boolean isAfrican) {
        ExchangeWeight exchange = new ExchangeWeight(exchangeId, country, isAfrican);
        exchangeWeights.put(exchangeId, exchange);
        LOGGER.info("Registered exchange: " + exchangeId + " (African: " + isAfrican + ")");
    }
    
    /**
     * Gets currency weight information
     */
    public CurrencyWeight getCurrencyWeight(String currencyCode) {
        return currencyWeights.get(currencyCode);
    }
    
    /**
     * Gets all currency weights
     */
    public Collection<CurrencyWeight> getAllCurrencyWeights() {
        return new ArrayList<>(currencyWeights.values());
    }
}