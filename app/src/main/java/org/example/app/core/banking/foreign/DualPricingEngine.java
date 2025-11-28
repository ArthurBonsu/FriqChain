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
 * DualPricingEngine provides dual pricing mechanism where each commodity/good has:
 * - Local value in ASU (African Stable Unit) - PRIMARY
 * - Reference value in foreign currency (USD/EUR) - SECONDARY
 * 
 * Core Principles:
 * - Traders see world prices via oracle feeds but settle in African units
 * - ASU price is authoritative for settlements
 * - Foreign currency prices are for transparency and comparison only
 * - Automatic arbitrage detection between local and reference prices
 * - Fair pricing adjustments based on African market conditions
 */
public class DualPricingEngine {
    
    private static final Logger LOGGER = Logger.getLogger(DualPricingEngine.class.getName());
    
    // Dual prices: ItemId -> DualPrice
    private final Map<String, DualPrice> priceMap;
    
    // Price history for trend analysis
    private final Map<String, List<PriceSnapshot>> priceHistory;
    
    // Arbitrage opportunities detected
    private final List<ArbitrageOpportunity> arbitrageOpportunities;
    
    // African market priority multiplier (1.5x weight)
    private static final BigDecimal AFRICAN_MARKET_MULTIPLIER = new BigDecimal("1.5");
    
    // Maximum acceptable price deviation (10%)
    private static final BigDecimal MAX_PRICE_DEVIATION = new BigDecimal("0.10");
    
    private final ReentrantReadWriteLock lock;
    private final FXReferenceManager fxReferenceManager;
    
    /**
     * Represents dual pricing for an item
     */
    public static class DualPrice {
        private final String itemId;
        private final String itemName;
        private final BigDecimal asuPrice;        // PRIMARY price (authoritative)
        private final BigDecimal referencePrice;   // SECONDARY price (for comparison)
        private final String referenceCurrency;    // e.g., "USD", "EUR"
        private final BigDecimal fxRate;           // ASU to reference currency rate
        private final BigDecimal priceDeviation;   // Deviation between converted prices
        private final boolean isAfricanMarketBased; // If price comes from African markets
        private final long lastUpdateTimestamp;
        private final PriceSource source;
        
        public enum PriceSource {
            AFRICAN_EXCHANGE,      // Price from African commodity exchange
            INTERNATIONAL_ORACLE,  // Price from international oracle
            HYBRID,                // Weighted combination
            MANUAL                 // Manually set by authorized entity
        }
        
        public DualPrice(String itemId, String itemName, BigDecimal asuPrice,
                        BigDecimal referencePrice, String referenceCurrency,
                        BigDecimal fxRate, boolean isAfricanMarketBased,
                        PriceSource source) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.asuPrice = asuPrice;
            this.referencePrice = referencePrice;
            this.referenceCurrency = referenceCurrency;
            this.fxRate = fxRate;
            this.isAfricanMarketBased = isAfricanMarketBased;
            this.source = source;
            this.lastUpdateTimestamp = System.currentTimeMillis();
            
            // Calculate price deviation
            BigDecimal convertedRefPrice = referencePrice.multiply(fxRate);
            if (asuPrice.compareTo(BigDecimal.ZERO) > 0) {
                this.priceDeviation = convertedRefPrice.subtract(asuPrice)
                    .divide(asuPrice, 8, RoundingMode.HALF_UP);
            } else {
                this.priceDeviation = BigDecimal.ZERO;
            }
        }
        
        public String getItemId() { return itemId; }
        public String getItemName() { return itemName; }
        public BigDecimal getAsuPrice() { return asuPrice; }
        public BigDecimal getReferencePrice() { return referencePrice; }
        public String getReferenceCurrency() { return referenceCurrency; }
        public BigDecimal getFxRate() { return fxRate; }
        public BigDecimal getPriceDeviation() { return priceDeviation; }
        public boolean isAfricanMarketBased() { return isAfricanMarketBased; }
        public long getLastUpdateTimestamp() { return lastUpdateTimestamp; }
        public PriceSource getSource() { return source; }
        
        public boolean hasSignificantDeviation() {
            return priceDeviation.abs().compareTo(MAX_PRICE_DEVIATION) > 0;
        }
        
        public Amount getAsuAmount() {
            return Amount.forASU(asuPrice.toBigInteger());
        }
    }
    
    /**
     * Snapshot of price at a specific time
     */
    private static class PriceSnapshot {
        private final long timestamp;
        private final BigDecimal asuPrice;
        private final BigDecimal referencePrice;
        private final BigDecimal deviation;
        
        public PriceSnapshot(BigDecimal asuPrice, BigDecimal referencePrice, BigDecimal deviation) {
            this.timestamp = System.currentTimeMillis();
            this.asuPrice = asuPrice;
            this.referencePrice = referencePrice;
            this.deviation = deviation;
        }
        
        public long getTimestamp() { return timestamp; }
        public BigDecimal getAsuPrice() { return asuPrice; }
        public BigDecimal getReferencePrice() { return referencePrice; }
        public BigDecimal getDeviation() { return deviation; }
    }
    
    /**
     * Represents an arbitrage opportunity
     */
    public static class ArbitrageOpportunity {
        private final String itemId;
        private final BigDecimal asuPrice;
        private final BigDecimal equivalentRefPrice; // Reference price converted to ASU
        private final BigDecimal arbitragePercentage;
        private final long detectedTimestamp;
        private boolean exploited;
        
        public ArbitrageOpportunity(String itemId, BigDecimal asuPrice,
                                   BigDecimal equivalentRefPrice) {
            this.itemId = itemId;
            this.asuPrice = asuPrice;
            this.equivalentRefPrice = equivalentRefPrice;
            this.detectedTimestamp = System.currentTimeMillis();
            this.exploited = false;
            
            // Calculate arbitrage percentage
            if (asuPrice.compareTo(BigDecimal.ZERO) > 0) {
                this.arbitragePercentage = equivalentRefPrice.subtract(asuPrice)
                    .divide(asuPrice, 8, RoundingMode.HALF_UP);
            } else {
                this.arbitragePercentage = BigDecimal.ZERO;
            }
        }
        
        public String getItemId() { return itemId; }
        public BigDecimal getAsuPrice() { return asuPrice; }
        public BigDecimal getEquivalentRefPrice() { return equivalentRefPrice; }
        public BigDecimal getArbitragePercentage() { return arbitragePercentage; }
        public long getDetectedTimestamp() { return detectedTimestamp; }
        public boolean isExploited() { return exploited; }
        
        public void markExploited() {
            this.exploited = true;
        }
        
        public boolean isSignificant() {
            return arbitragePercentage.abs().compareTo(new BigDecimal("0.05")) > 0; // 5%+
        }
    }
    
    public DualPricingEngine(FXReferenceManager fxReferenceManager) {
        this.priceMap = new ConcurrentHashMap<>();
        this.priceHistory = new ConcurrentHashMap<>();
        this.arbitrageOpportunities = Collections.synchronizedList(new ArrayList<>());
        this.lock = new ReentrantReadWriteLock(true);
        this.fxReferenceManager = fxReferenceManager;
        
        LOGGER.info("DualPricingEngine initialized - ASU primary, foreign currency reference");
    }
    
    /**
     * Sets dual price for an item using direct inputs
     */
    public boolean setDualPrice(String itemId, String itemName, BigDecimal asuPrice,
                               BigDecimal referencePrice, String referenceCurrency,
                               boolean isAfricanMarketBased,
                               DualPrice.PriceSource source) {
        lock.writeLock().lock();
        try {
            // Validate inputs
            if (asuPrice.compareTo(BigDecimal.ZERO) <= 0 ||
                referencePrice.compareTo(BigDecimal.ZERO) <= 0) {
                LOGGER.warning("Invalid prices for " + itemId);
                return false;
            }
            
            // Get FX rate
            Optional<FXReferenceManager.FXReferenceRate> fxRateOpt =
                fxReferenceManager.getReferenceRate("ASU", referenceCurrency);
            
            if (fxRateOpt.isEmpty()) {
                LOGGER.warning("No FX rate available for ASU/" + referenceCurrency);
                return false;
            }
            
            BigDecimal fxRate = fxRateOpt.get().getRate();
            
            // Create dual price
            DualPrice dualPrice = new DualPrice(
                itemId, itemName, asuPrice, referencePrice, referenceCurrency,
                fxRate, isAfricanMarketBased, source
            );
            
            priceMap.put(itemId, dualPrice);
            
            // Record in history
            recordPriceHistory(itemId, dualPrice);
            
            // Check for arbitrage
            checkArbitrage(dualPrice);
            
            LOGGER.info(String.format("Set dual price for %s: %s ASU (ref: %s %s, deviation: %.2f%%)",
                itemName, asuPrice, referencePrice, referenceCurrency,
                dualPrice.getPriceDeviation().multiply(BigDecimal.valueOf(100))));
            
            return true;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Updates price using African and international oracle feeds
     * Applies fair-weighting algorithm with African priority
     */
    public boolean updatePriceFromOracles(String itemId, String itemName,
                                          List<BigDecimal> africanPrices,
                                          List<BigDecimal> internationalPrices,
                                          String referenceCurrency) {
        lock.writeLock().lock();
        try {
            if (africanPrices.isEmpty() && internationalPrices.isEmpty()) {
                LOGGER.warning("No oracle prices provided for " + itemId);
                return false;
            }
            
            // Calculate weighted average with African priority
            BigDecimal asuPrice = calculateWeightedPrice(africanPrices, internationalPrices);
            
            // Calculate reference price (simple average of international prices)
            BigDecimal referencePrice = BigDecimal.ZERO;
            if (!internationalPrices.isEmpty()) {
                for (BigDecimal price : internationalPrices) {
                    referencePrice = referencePrice.add(price);
                }
                referencePrice = referencePrice.divide(
                    BigDecimal.valueOf(internationalPrices.size()), 8, RoundingMode.HALF_UP);
            }
            
            boolean isAfricanBased = !africanPrices.isEmpty();
            DualPrice.PriceSource source = africanPrices.isEmpty() ? 
                DualPrice.PriceSource.INTERNATIONAL_ORACLE : 
                (internationalPrices.isEmpty() ? 
                    DualPrice.PriceSource.AFRICAN_EXCHANGE : 
                    DualPrice.PriceSource.HYBRID);
            
            return setDualPrice(itemId, itemName, asuPrice, referencePrice,
                referenceCurrency, isAfricanBased, source);
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Calculates weighted price with African market priority (1.5x weight)
     */
    private BigDecimal calculateWeightedPrice(List<BigDecimal> africanPrices,
                                             List<BigDecimal> internationalPrices) {
        BigDecimal totalPrice = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        
        // African prices get 1.5x weight
        for (BigDecimal price : africanPrices) {
            totalPrice = totalPrice.add(price.multiply(AFRICAN_MARKET_MULTIPLIER));
            totalWeight = totalWeight.add(AFRICAN_MARKET_MULTIPLIER);
        }
        
        // International prices get 1.0x weight
        for (BigDecimal price : internationalPrices) {
            totalPrice = totalPrice.add(price);
            totalWeight = totalWeight.add(BigDecimal.ONE);
        }
        
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return totalPrice.divide(totalWeight, 8, RoundingMode.HALF_UP);
    }
    
    /**
     * Gets dual price for an item
     */
    public Optional<DualPrice> getPrice(String itemId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(priceMap.get(itemId));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets all dual prices
     */
    public Map<String, DualPrice> getAllPrices() {
        lock.readLock().lock();
        try {
            return new HashMap<>(priceMap);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets prices with significant deviations
     */
    public List<DualPrice> getPricesWithDeviation() {
        lock.readLock().lock();
        try {
            List<DualPrice> result = new ArrayList<>();
            for (DualPrice price : priceMap.values()) {
                if (price.hasSignificantDeviation()) {
                    result.add(price);
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Converts reference price to ASU using current FX rate
     */
    public Optional<BigDecimal> convertReferenceToASU(BigDecimal referenceAmount,
                                                      String referenceCurrency) {
        Optional<FXReferenceManager.FXReferenceRate> rateOpt =
            fxReferenceManager.getReferenceRate("ASU", referenceCurrency);
        
        if (rateOpt.isEmpty()) {
            return Optional.empty();
        }
        
        BigDecimal asuAmount = referenceAmount.multiply(rateOpt.get().getRate());
        return Optional.of(asuAmount);
    }
    
    /**
     * Converts ASU to reference currency
     */
    public Optional<BigDecimal> convertASUToReference(BigDecimal asuAmount,
                                                      String referenceCurrency) {
        Optional<FXReferenceManager.FXReferenceRate> rateOpt =
            fxReferenceManager.getReferenceRate("ASU", referenceCurrency);
        
        if (rateOpt.isEmpty()) {
            return Optional.empty();
        }
        
        BigDecimal refAmount = asuAmount.divide(rateOpt.get().getRate(), 8, RoundingMode.HALF_UP);
        return Optional.of(refAmount);
    }
    
    /**
     * Gets price history for an item
     */
    public List<PriceSnapshot> getPriceHistory(String itemId, int limit) {
        lock.readLock().lock();
        try {
            List<PriceSnapshot> history = priceHistory.get(itemId);
            if (history == null || history.isEmpty()) {
                return new ArrayList<>();
            }
            
            int size = history.size();
            int fromIndex = Math.max(0, size - limit);
            return new ArrayList<>(history.subList(fromIndex, size));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets detected arbitrage opportunities
     */
    public List<ArbitrageOpportunity> getArbitrageOpportunities(boolean onlySignificant) {
        lock.readLock().lock();
        try {
            List<ArbitrageOpportunity> result = new ArrayList<>();
            for (ArbitrageOpportunity opp : arbitrageOpportunities) {
                if (!opp.isExploited() && (!onlySignificant || opp.isSignificant())) {
                    result.add(opp);
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Records price in history
     */
    private void recordPriceHistory(String itemId, DualPrice price) {
        List<PriceSnapshot> history = priceHistory.computeIfAbsent(
            itemId, k -> Collections.synchronizedList(new ArrayList<>()));
        
        PriceSnapshot snapshot = new PriceSnapshot(
            price.getAsuPrice(),
            price.getReferencePrice(),
            price.getPriceDeviation()
        );
        
        history.add(snapshot);
        
        // Prune old history (keep last 1000 snapshots per item)
        if (history.size() > 1000) {
            history.remove(0);
        }
    }
    
    /**
     * Checks for arbitrage opportunities
     */
    private void checkArbitrage(DualPrice price) {
        if (!price.hasSignificantDeviation()) {
            return;
        }
        
        // Calculate equivalent reference price in ASU terms
        BigDecimal equivalentRefPrice = price.getReferencePrice().multiply(price.getFxRate());
        
        ArbitrageOpportunity opportunity = new ArbitrageOpportunity(
            price.getItemId(),
            price.getAsuPrice(),
            equivalentRefPrice
        );
        
        if (opportunity.isSignificant()) {
            arbitrageOpportunities.add(opportunity);
            
            LOGGER.info(String.format("Arbitrage opportunity detected for %s: %.2f%% difference",
                price.getItemId(),
                opportunity.getArbitragePercentage().multiply(BigDecimal.valueOf(100))));
        }
        
        // Prune old opportunities (keep last 500)
        if (arbitrageOpportunities.size() > 500) {
            arbitrageOpportunities.remove(0);
        }
    }
    
    /**
     * Calculates price trend (positive = increasing, negative = decreasing)
     */
    public Optional<BigDecimal> calculatePriceTrend(String itemId, int periods) {
        List<PriceSnapshot> history = getPriceHistory(itemId, periods);
        
        if (history.size() < 2) {
            return Optional.empty();
        }
        
        BigDecimal firstPrice = history.get(0).getAsuPrice();
        BigDecimal lastPrice = history.get(history.size() - 1).getAsuPrice();
        
        if (firstPrice.compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty();
        }
        
        BigDecimal trend = lastPrice.subtract(firstPrice)
            .divide(firstPrice, 8, RoundingMode.HALF_UP);
        
        return Optional.of(trend);
    }
    
    /**
     * Gets system statistics
     */
    public Map<String, Object> getSystemStats() {
        lock.readLock().lock();
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalItems", priceMap.size());
            stats.put("africanMarketBased", priceMap.values().stream()
                .filter(DualPrice::isAfricanMarketBased).count());
            stats.put("itemsWithDeviation", getPricesWithDeviation().size());
            stats.put("activeArbitrageOpportunities", 
                getArbitrageOpportunities(false).size());
            stats.put("significantArbitrageOpportunities",
                getArbitrageOpportunities(true).size());
            stats.put("africanMarketMultiplier", AFRICAN_MARKET_MULTIPLIER);
            stats.put("maxPriceDeviation", MAX_PRICE_DEVIATION);
            
            // Count by source
            Map<String, Integer> bySource = new HashMap<>();
            for (DualPrice.PriceSource source : DualPrice.PriceSource.values()) {
                bySource.put(source.toString(), 0);
            }
            for (DualPrice price : priceMap.values()) {
                String source = price.getSource().toString();
                bySource.put(source, bySource.get(source) + 1);
            }
            stats.put("pricesBySource", bySource);
            
            return stats;
        } finally {
            lock.readLock().unlock();
        }
    }
}