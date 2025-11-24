package org.example.app.core.blockchain.consensus;

import org.example.app.core.banking.tokenization.AfricanStableUnitToken;
import org.example.app.core.state.BlockchainState;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * ASUBasketValidator - Validates African Stable Unit (ASU) composition
 * 
 * ASU Formula (from Guidebook):
 * ASU = Σ(α × Currency) + Σ(β × Commodity) + (γ × Crypto_reserve)
 * 
 * Where:
 * - α: African currency weights (must total ≥60%)
 * - β: Commodity weights (gold, oil, agricultural) 
 * - γ: Crypto reserve buffer (≤10% for global liquidity)
 * 
 * Core Requirements:
 * - African currencies must be ≥60% of basket
 * - Crypto reserve must be ≤10%
 * - No single currency > 35%
 * - Auto-rebalancing when weights deviate >5%
 * - Validates backing collateral
 * - Prevents manipulation of basket composition
 */
public class ASUBasketValidator {
    private static final Logger LOGGER = Logger.getLogger(ASUBasketValidator.class.getName());
    
    // Composition limits
    private static final BigDecimal MIN_AFRICAN_CURRENCY_WEIGHT = new BigDecimal("0.60"); // 60%
    private static final BigDecimal MAX_CRYPTO_RESERVE_WEIGHT = new BigDecimal("0.10"); // 10%
    private static final BigDecimal MAX_SINGLE_CURRENCY_WEIGHT = new BigDecimal("0.35"); // 35%
    private static final BigDecimal MAX_SINGLE_COMMODITY_WEIGHT = new BigDecimal("0.20"); // 20%
    
    // Rebalancing triggers
    private static final BigDecimal REBALANCE_THRESHOLD = new BigDecimal("0.05"); // 5% deviation
    private static final long REBALANCE_COOLDOWN_MS = 3600000; // 1 hour minimum between rebalances
    
    // Minimum collateral ratios
    private static final BigDecimal MIN_COLLATERAL_RATIO = new BigDecimal("1.2"); // 120% backing
    
    // Dependencies
    private final BlockchainState blockchainState;
    
    // Basket composition tracking
    private final Map<String, CurrencyComponent> currencyComponents;
    private final Map<String, CommodityComponent> commodityComponents;
    private CryptoReserveComponent cryptoReserve;
    private long lastRebalanceTime;
    
    /**
     * Represents a currency component in the ASU basket
     */
    public static class CurrencyComponent {
        private final String currencyCode;
        private final boolean isAfrican;
        private BigDecimal weight;
        private BigDecimal value;
        private BigDecimal targetWeight;
        private long lastUpdate;
        
        public CurrencyComponent(String currencyCode, boolean isAfrican, BigDecimal weight) {
            this.currencyCode = currencyCode;
            this.isAfrican = isAfrican;
            this.weight = weight;
            this.value = BigDecimal.ZERO;
            this.targetWeight = weight;
            this.lastUpdate = System.currentTimeMillis();
        }
        
        public String getCurrencyCode() { return currencyCode; }
        public boolean isAfrican() { return isAfrican; }
        public BigDecimal getWeight() { return weight; }
        public BigDecimal getValue() { return value; }
        public BigDecimal getTargetWeight() { return targetWeight; }
        
        public void setWeight(BigDecimal weight) {
            this.weight = weight;
            this.lastUpdate = System.currentTimeMillis();
        }
        
        public void setValue(BigDecimal value) { this.value = value; }
        public void setTargetWeight(BigDecimal targetWeight) { this.targetWeight = targetWeight; }
        
        public BigDecimal getWeightDeviation() {
            return weight.subtract(targetWeight).abs();
        }
    }
    
    /**
     * Represents a commodity component in the ASU basket
     */
    public static class CommodityComponent {
        private final String commodityType; // GOLD, OIL, AGRICULTURAL
        private BigDecimal weight;
        private BigDecimal value;
        private BigDecimal reserves; // Physical reserves backing
        private long lastUpdate;
        
        public CommodityComponent(String commodityType, BigDecimal weight) {
            this.commodityType = commodityType;
            this.weight = weight;
            this.value = BigDecimal.ZERO;
            this.reserves = BigDecimal.ZERO;
            this.lastUpdate = System.currentTimeMillis();
        }
        
        public String getCommodityType() { return commodityType; }
        public BigDecimal getWeight() { return weight; }
        public BigDecimal getValue() { return value; }
        public BigDecimal getReserves() { return reserves; }
        
        public void setWeight(BigDecimal weight) {
            this.weight = weight;
            this.lastUpdate = System.currentTimeMillis();
        }
        
        public void setValue(BigDecimal value) { this.value = value; }
        public void setReserves(BigDecimal reserves) { this.reserves = reserves; }
    }
    
    /**
     * Represents crypto reserve component
     */
    public static class CryptoReserveComponent {
        private BigDecimal weight;
        private BigDecimal value;
        private final Map<String, BigDecimal> assets; // BTC, ETH, etc.
        private long lastUpdate;
        
        public CryptoReserveComponent() {
            this.weight = BigDecimal.ZERO;
            this.value = BigDecimal.ZERO;
            this.assets = new HashMap<>();
            this.lastUpdate = System.currentTimeMillis();
        }
        
        public BigDecimal getWeight() { return weight; }
        public BigDecimal getValue() { return value; }
        public Map<String, BigDecimal> getAssets() { return new HashMap<>(assets); }
        
        public void setWeight(BigDecimal weight) {
            this.weight = weight;
            this.lastUpdate = System.currentTimeMillis();
        }
        
        public void setValue(BigDecimal value) { this.value = value; }
        public void addAsset(String asset, BigDecimal amount) { assets.put(asset, amount); }
    }
    
    /**
     * Validation result
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String failureReason;
        private final Map<String, Object> metadata;
        
        private ValidationResult(boolean valid, String reason, Map<String, Object> metadata) {
            this.valid = valid;
            this.failureReason = reason;
            this.metadata = metadata != null ? metadata : new HashMap<>();
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, null, new HashMap<>());
        }
        
        public static ValidationResult success(Map<String, Object> metadata) {
            return new ValidationResult(true, null, metadata);
        }
        
        public static ValidationResult failure(String reason) {
            return new ValidationResult(false, reason, new HashMap<>());
        }
        
        public boolean isValid() { return valid; }
        public String getFailureReason() { return failureReason; }
        public Map<String, Object> getMetadata() { return metadata; }
    }
    
    /**
     * Constructor
     */
    public ASUBasketValidator(BlockchainState blockchainState) {
        this.blockchainState = blockchainState;
        this.currencyComponents = new ConcurrentHashMap<>();
        this.commodityComponents = new ConcurrentHashMap<>();
        this.cryptoReserve = new CryptoReserveComponent();
        this.lastRebalanceTime = 0;
        
        initializeDefaultBasket();
        LOGGER.info("ASUBasketValidator initialized");
    }
    
    /**
     * Initialize default ASU basket composition
     */
    private void initializeDefaultBasket() {
        // Major African currencies (65% total)
        addCurrency("NGN", true, new BigDecimal("0.15")); // Nigerian Naira
        addCurrency("ZAR", true, new BigDecimal("0.15")); // South African Rand
        addCurrency("EGP", true, new BigDecimal("0.10")); // Egyptian Pound
        addCurrency("KES", true, new BigDecimal("0.08")); // Kenyan Shilling
        addCurrency("GHS", true, new BigDecimal("0.07")); // Ghanaian Cedi
        addCurrency("TZS", true, new BigDecimal("0.05")); // Tanzanian Shilling
        addCurrency("MAD", true, new BigDecimal("0.05")); // Moroccan Dirham
        
        // Commodities (25% total)
        addCommodity("GOLD", new BigDecimal("0.10"));
        addCommodity("OIL", new BigDecimal("0.08"));
        addCommodity("AGRICULTURAL", new BigDecimal("0.07"));
        
        // Crypto reserve (10% - maximum allowed)
        cryptoReserve.setWeight(new BigDecimal("0.10"));
        
        LOGGER.info("Default ASU basket initialized");
    }
    
    /**
     * Adds or updates a currency component
     */
    public void addCurrency(String currencyCode, boolean isAfrican, BigDecimal weight) {
        currencyComponents.put(currencyCode, 
            new CurrencyComponent(currencyCode, isAfrican, weight));
    }
    
    /**
     * Adds or updates a commodity component
     */
    public void addCommodity(String commodityType, BigDecimal weight) {
        commodityComponents.put(commodityType,
            new CommodityComponent(commodityType, weight));
    }
    
    /**
     * Validates current basket composition
     */
    public ValidationResult validateBasketComposition() {
        try {
            // 1. Calculate total weights
            BigDecimal totalWeight = calculateTotalWeight();
            
            // Weights should sum to ~1.0 (100%)
            if (totalWeight.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.01")) > 0) {
                return ValidationResult.failure(
                    String.format("Total weights do not sum to 100%% (actual: %.2f%%)",
                        totalWeight.multiply(new BigDecimal(100)).doubleValue())
                );
            }
            
            // 2. Validate African currency minimum
            ValidationResult africanCheck = validateAfricanCurrencyMinimum();
            if (!africanCheck.isValid()) {
                return africanCheck;
            }
            
            // 3. Validate crypto reserve maximum
            ValidationResult cryptoCheck = validateCryptoReserveMaximum();
            if (!cryptoCheck.isValid()) {
                return cryptoCheck;
            }
            
            // 4. Validate single currency maximum
            ValidationResult currencyCheck = validateSingleCurrencyMaximum();
            if (!currencyCheck.isValid()) {
                return currencyCheck;
            }
            
            // 5. Validate single commodity maximum
            ValidationResult commodityCheck = validateSingleCommodityMaximum();
            if (!commodityCheck.isValid()) {
                return commodityCheck;
            }
            
            // 6. Validate collateral backing
            ValidationResult collateralCheck = validateCollateralBacking();
            if (!collateralCheck.isValid()) {
                return collateralCheck;
            }
            
            LOGGER.fine("ASU basket composition validated successfully");
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("totalWeight", totalWeight);
            metadata.put("africanCurrencyWeight", calculateAfricanCurrencyWeight());
            metadata.put("cryptoReserveWeight", cryptoReserve.getWeight());
            metadata.put("commodityWeight", calculateCommodityWeight());
            
            return ValidationResult.success(metadata);
            
        } catch (Exception e) {
            LOGGER.severe("Basket validation error: " + e.getMessage());
            return ValidationResult.failure("Validation exception: " + e.getMessage());
        }
    }
    
    /**
     * Validates African currency minimum (≥60%)
     */
    private ValidationResult validateAfricanCurrencyMinimum() {
        BigDecimal africanWeight = calculateAfricanCurrencyWeight();
        
        if (africanWeight.compareTo(MIN_AFRICAN_CURRENCY_WEIGHT) < 0) {
            return ValidationResult.failure(
                String.format("African currency weight too low: %.2f%% (minimum: %.2f%%)",
                    africanWeight.multiply(new BigDecimal(100)).doubleValue(),
                    MIN_AFRICAN_CURRENCY_WEIGHT.multiply(new BigDecimal(100)).doubleValue())
            );
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates crypto reserve maximum (≤10%)
     */
    private ValidationResult validateCryptoReserveMaximum() {
        BigDecimal cryptoWeight = cryptoReserve.getWeight();
        
        if (cryptoWeight.compareTo(MAX_CRYPTO_RESERVE_WEIGHT) > 0) {
            return ValidationResult.failure(
                String.format("Crypto reserve weight too high: %.2f%% (maximum: %.2f%%)",
                    cryptoWeight.multiply(new BigDecimal(100)).doubleValue(),
                    MAX_CRYPTO_RESERVE_WEIGHT.multiply(new BigDecimal(100)).doubleValue())
            );
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates single currency maximum (≤35%)
     */
    private ValidationResult validateSingleCurrencyMaximum() {
        for (CurrencyComponent currency : currencyComponents.values()) {
            if (currency.getWeight().compareTo(MAX_SINGLE_CURRENCY_WEIGHT) > 0) {
                return ValidationResult.failure(
                    String.format("Currency %s weight exceeds maximum: %.2f%% (max: %.2f%%)",
                        currency.getCurrencyCode(),
                        currency.getWeight().multiply(new BigDecimal(100)).doubleValue(),
                        MAX_SINGLE_CURRENCY_WEIGHT.multiply(new BigDecimal(100)).doubleValue())
                );
            }
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates single commodity maximum (≤20%)
     */
    private ValidationResult validateSingleCommodityMaximum() {
        for (CommodityComponent commodity : commodityComponents.values()) {
            if (commodity.getWeight().compareTo(MAX_SINGLE_COMMODITY_WEIGHT) > 0) {
                return ValidationResult.failure(
                    String.format("Commodity %s weight exceeds maximum: %.2f%% (max: %.2f%%)",
                        commodity.getCommodityType(),
                        commodity.getWeight().multiply(new BigDecimal(100)).doubleValue(),
                        MAX_SINGLE_COMMODITY_WEIGHT.multiply(new BigDecimal(100)).doubleValue())
                );
            }
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates collateral backing (≥120%)
     */
    private ValidationResult validateCollateralBacking() {
        BigDecimal totalValue = calculateTotalValue();
        BigDecimal totalReserves = calculateTotalReserves();
        
        if (totalValue.compareTo(BigDecimal.ZERO) == 0) {
            // No ASU issued yet
            return ValidationResult.success();
        }
        
        BigDecimal collateralRatio = totalReserves.divide(totalValue, 4, RoundingMode.HALF_UP);
        
        if (collateralRatio.compareTo(MIN_COLLATERAL_RATIO) < 0) {
            return ValidationResult.failure(
                String.format("Insufficient collateral backing: %.2f%% (minimum: %.2f%%)",
                    collateralRatio.multiply(new BigDecimal(100)).doubleValue(),
                    MIN_COLLATERAL_RATIO.multiply(new BigDecimal(100)).doubleValue())
            );
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Checks if rebalancing is needed
     */
    public boolean needsRebalancing() {
        // Check cooldown period
        if (System.currentTimeMillis() - lastRebalanceTime < REBALANCE_COOLDOWN_MS) {
            return false;
        }
        
        // Check if any component deviates > threshold from target
        for (CurrencyComponent currency : currencyComponents.values()) {
            if (currency.getWeightDeviation().compareTo(REBALANCE_THRESHOLD) > 0) {
                LOGGER.info("Rebalancing needed for currency: " + currency.getCurrencyCode());
                return true;
            }
        }
        
        // Check if composition violates limits
        ValidationResult validation = validateBasketComposition();
        if (!validation.isValid()) {
            LOGGER.info("Rebalancing needed due to composition violation");
            return true;
        }
        
        return false;
    }
    
    /**
     * Performs automatic rebalancing
     */
    public Map<String, BigDecimal> rebalanceBasket() {
        LOGGER.info("Starting ASU basket rebalancing");
        
        Map<String, BigDecimal> adjustments = new HashMap<>();
        
        // 1. Identify over-weight and under-weight components
        List<CurrencyComponent> overWeight = currencyComponents.values().stream()
            .filter(c -> c.getWeight().compareTo(c.getTargetWeight()) > 0)
            .toList();
        
        List<CurrencyComponent> underWeight = currencyComponents.values().stream()
            .filter(c -> c.getWeight().compareTo(c.getTargetWeight()) < 0)
            .toList();
        
        // 2. Calculate total excess and deficit
        BigDecimal totalExcess = overWeight.stream()
            .map(c -> c.getWeight().subtract(c.getTargetWeight()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalDeficit = underWeight.stream()
            .map(c -> c.getTargetWeight().subtract(c.getWeight()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 3. Proportionally reduce over-weight components
        for (CurrencyComponent currency : overWeight) {
            BigDecimal reduction = currency.getWeight().subtract(currency.getTargetWeight())
                .multiply(new BigDecimal("0.5")); // Reduce by 50% of excess
            
            BigDecimal newWeight = currency.getWeight().subtract(reduction);
            currency.setWeight(newWeight);
            adjustments.put(currency.getCurrencyCode(), reduction.negate());
        }
        
        // 4. Proportionally increase under-weight components
        if (!underWeight.isEmpty() && totalExcess.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal redistributionPerCurrency = totalExcess.divide(
                new BigDecimal(underWeight.size()), 8, RoundingMode.HALF_UP
            );
            
            for (CurrencyComponent currency : underWeight) {
                BigDecimal increase = redistributionPerCurrency;
                BigDecimal newWeight = currency.getWeight().add(increase);
                
                // Ensure doesn't exceed maximum
                if (newWeight.compareTo(MAX_SINGLE_CURRENCY_WEIGHT) > 0) {
                    newWeight = MAX_SINGLE_CURRENCY_WEIGHT;
                    increase = newWeight.subtract(currency.getWeight());
                }
                
                currency.setWeight(newWeight);
                adjustments.put(currency.getCurrencyCode(), increase);
            }
        }
        
        lastRebalanceTime = System.currentTimeMillis();
        
        LOGGER.info("Rebalancing complete. Adjusted " + adjustments.size() + " components");
        return adjustments;
    }
    
    /**
     * Calculation helpers
     */
    private BigDecimal calculateTotalWeight() {
        BigDecimal total = currencyComponents.values().stream()
            .map(CurrencyComponent::getWeight)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        total = total.add(commodityComponents.values().stream()
            .map(CommodityComponent::getWeight)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        
        total = total.add(cryptoReserve.getWeight());
        
        return total;
    }
    
    private BigDecimal calculateAfricanCurrencyWeight() {
        return currencyComponents.values().stream()
            .filter(CurrencyComponent::isAfrican)
            .map(CurrencyComponent::getWeight)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal calculateCommodityWeight() {
        return commodityComponents.values().stream()
            .map(CommodityComponent::getWeight)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal calculateTotalValue() {
        BigDecimal total = currencyComponents.values().stream()
            .map(CurrencyComponent::getValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        total = total.add(commodityComponents.values().stream()
            .map(CommodityComponent::getValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        
        total = total.add(cryptoReserve.getValue());
        
        return total;
    }
    
    private BigDecimal calculateTotalReserves() {
        return commodityComponents.values().stream()
            .map(CommodityComponent::getReserves)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Gets basket composition summary
     */
    public Map<String, Object> getCompositionSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        summary.put("totalWeight", calculateTotalWeight());
        summary.put("africanCurrencyWeight", calculateAfricanCurrencyWeight());
        summary.put("commodityWeight", calculateCommodityWeight());
        summary.put("cryptoReserveWeight", cryptoReserve.getWeight());
        summary.put("totalValue", calculateTotalValue());
        summary.put("totalReserves", calculateTotalReserves());
        summary.put("collateralRatio", 
            calculateTotalValue().compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
            calculateTotalReserves().divide(calculateTotalValue(), 4, RoundingMode.HALF_UP));
        summary.put("lastRebalance", lastRebalanceTime);
        summary.put("needsRebalancing", needsRebalancing());
        
        return summary;
    }
    
    /**
     * Gets all currency components
     */
    public Collection<CurrencyComponent> getCurrencyComponents() {
        return new ArrayList<>(currencyComponents.values());
    }
    
    /**
     * Gets all commodity components
     */
    public Collection<CommodityComponent> getCommodityComponents() {
        return new ArrayList<>(commodityComponents.values());
    }
    
    /**
     * Gets crypto reserve
     */
    public CryptoReserveComponent getCryptoReserve() {
        return cryptoReserve;
    }
}