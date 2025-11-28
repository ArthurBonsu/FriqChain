package org.example.app.core.blockchain.foreign;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * FXExposureMonitor tracks and enforces limits on foreign currency exposure
 * to maintain African financial sovereignty and prevent over-dependence on
 * external currencies.
 * 
 * Core Principles:
 * - Treasury DAO sets maximum % exposure to foreign currencies (≤10%)
 * - Real-time monitoring of exposure across all foreign currencies
 * - Automatic rebalancing when limits approached
 * - Alert system for exposure threshold breaches
 * - Historical tracking for trend analysis
 * - Emergency intervention capabilities
 */
public class FXExposureMonitor {
    
    private static final Logger LOGGER = Logger.getLogger(FXExposureMonitor.class.getName());
    
    // Maximum total exposure to ALL foreign currencies: 10%
    private static final BigDecimal MAX_TOTAL_FOREIGN_EXPOSURE = new BigDecimal("0.10");
    
    // Maximum exposure to any SINGLE foreign currency: 5%
    private static final BigDecimal MAX_SINGLE_CURRENCY_EXPOSURE = new BigDecimal("0.05");
    
    // Warning threshold (triggers alerts): 80% of max
    private static final BigDecimal WARNING_THRESHOLD = new BigDecimal("0.80");
    
    // Critical threshold (requires intervention): 95% of max
    private static final BigDecimal CRITICAL_THRESHOLD = new BigDecimal("0.95");
    
    // Rebalancing trigger threshold: 90% of max
    private static final BigDecimal REBALANCE_THRESHOLD = new BigDecimal("0.90");
    
    // Exposure tracking: Currency -> ExposureData
    private final Map<String, ExposureData> exposureMap;
    
    // Exposure history for trend analysis
    private final List<ExposureSnapshot> exposureHistory;
    
    // Alert history
    private final List<ExposureAlert> alertHistory;
    
    private final ReentrantReadWriteLock lock;
    private long lastRebalanceTimestamp;
    private BigInteger totalSystemValue; // Total value in ASU equivalent
    
    /**
     * Represents exposure data for a specific currency
     */
    public static class ExposureData {
        private final String currency;
        private BigInteger currentExposure;
        private BigInteger maxExposure;
        private BigDecimal exposureRatio;
        private ExposureLevel level;
        private long lastUpdateTimestamp;
        
        public enum ExposureLevel {
            SAFE,        // < 80% of limit
            WARNING,     // 80-95% of limit
            CRITICAL,    // 95-100% of limit
            EXCEEDED     // > 100% of limit
        }
        
        public ExposureData(String currency) {
            this.currency = currency;
            this.currentExposure = BigInteger.ZERO;
            this.maxExposure = BigInteger.ZERO;
            this.exposureRatio = BigDecimal.ZERO;
            this.level = ExposureLevel.SAFE;
            this.lastUpdateTimestamp = System.currentTimeMillis();
        }
        
        public String getCurrency() { return currency; }
        public BigInteger getCurrentExposure() { return currentExposure; }
        public BigInteger getMaxExposure() { return maxExposure; }
        public BigDecimal getExposureRatio() { return exposureRatio; }
        public ExposureLevel getLevel() { return level; }
        public long getLastUpdateTimestamp() { return lastUpdateTimestamp; }
        
        public void updateExposure(BigInteger newExposure, BigInteger totalSystemValue,
                                  BigDecimal maxRatio) {
            this.currentExposure = newExposure;
            this.maxExposure = new BigDecimal(totalSystemValue)
                .multiply(maxRatio)
                .toBigInteger();
            
            if (maxExposure.compareTo(BigInteger.ZERO) > 0) {
                this.exposureRatio = new BigDecimal(currentExposure)
                    .divide(new BigDecimal(maxExposure), 8, RoundingMode.HALF_UP);
            } else {
                this.exposureRatio = BigDecimal.ZERO;
            }
            
            this.level = calculateLevel(exposureRatio);
            this.lastUpdateTimestamp = System.currentTimeMillis();
        }
        
        private ExposureLevel calculateLevel(BigDecimal ratio) {
            if (ratio.compareTo(BigDecimal.ONE) >= 0) {
                return ExposureLevel.EXCEEDED;
            } else if (ratio.compareTo(CRITICAL_THRESHOLD) >= 0) {
                return ExposureLevel.CRITICAL;
            } else if (ratio.compareTo(WARNING_THRESHOLD) >= 0) {
                return ExposureLevel.WARNING;
            } else {
                return ExposureLevel.SAFE;
            }
        }
    }
    
    /**
     * Snapshot of exposure at a specific time
     */
    public static class ExposureSnapshot {
        private final long timestamp;
        private final Map<String, BigInteger> exposureByCurrency;
        private final BigInteger totalForeignExposure;
        private final BigDecimal totalExposureRatio;
        private final BigInteger totalSystemValue;
        
        public ExposureSnapshot(Map<String, BigInteger> exposureByCurrency,
                              BigInteger totalForeignExposure,
                              BigDecimal totalExposureRatio,
                              BigInteger totalSystemValue) {
            this.timestamp = System.currentTimeMillis();
            this.exposureByCurrency = new HashMap<>(exposureByCurrency);
            this.totalForeignExposure = totalForeignExposure;
            this.totalExposureRatio = totalExposureRatio;
            this.totalSystemValue = totalSystemValue;
        }
        
        public long getTimestamp() { return timestamp; }
        public Map<String, BigInteger> getExposureByCurrency() { 
            return new HashMap<>(exposureByCurrency); 
        }
        public BigInteger getTotalForeignExposure() { return totalForeignExposure; }
        public BigDecimal getTotalExposureRatio() { return totalExposureRatio; }
        public BigInteger getTotalSystemValue() { return totalSystemValue; }
    }
    
    /**
     * Represents an exposure alert
     */
    public static class ExposureAlert {
        private final long timestamp;
        private final AlertType type;
        private final String currency;
        private final BigDecimal exposureRatio;
        private final String message;
        private boolean resolved;
        
        public enum AlertType {
            WARNING,
            CRITICAL,
            EXCEEDED,
            REBALANCE_NEEDED
        }
        
        public ExposureAlert(AlertType type, String currency, BigDecimal exposureRatio, String message) {
            this.timestamp = System.currentTimeMillis();
            this.type = type;
            this.currency = currency;
            this.exposureRatio = exposureRatio;
            this.message = message;
            this.resolved = false;
        }
        
        public long getTimestamp() { return timestamp; }
        public AlertType getType() { return type; }
        public String getCurrency() { return currency; }
        public BigDecimal getExposureRatio() { return exposureRatio; }
        public String getMessage() { return message; }
        public boolean isResolved() { return resolved; }
        
        public void resolve() {
            this.resolved = true;
        }
    }
    
    public FXExposureMonitor(BigInteger initialTotalSystemValue) {
        this.exposureMap = new ConcurrentHashMap<>();
        this.exposureHistory = Collections.synchronizedList(new ArrayList<>());
        this.alertHistory = Collections.synchronizedList(new ArrayList<>());
        this.lock = new ReentrantReadWriteLock(true);
        this.lastRebalanceTimestamp = 0;
        this.totalSystemValue = initialTotalSystemValue;
        
        LOGGER.info(String.format("FXExposureMonitor initialized (max foreign exposure: %.1f%%)",
            MAX_TOTAL_FOREIGN_EXPOSURE.multiply(BigDecimal.valueOf(100)).doubleValue()));
    }
    
    /**
     * Records exposure for a specific currency
     */
    public void recordExposure(String currency, BigInteger exposure) {
        lock.writeLock().lock();
        try {
            ExposureData data = exposureMap.computeIfAbsent(currency, ExposureData::new);
            
            data.updateExposure(exposure, totalSystemValue, MAX_SINGLE_CURRENCY_EXPOSURE);
            
            // Check for alerts
            checkExposureAlerts(data);
            
            // Record snapshot periodically
            if (shouldRecordSnapshot()) {
                recordSnapshot();
            }
            
            LOGGER.fine(String.format("Recorded exposure for %s: %s (ratio: %.4f%%, level: %s)",
                currency, exposure, data.getExposureRatio().multiply(BigDecimal.valueOf(100)),
                data.getLevel()));
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Updates total system value
     */
    public void updateTotalSystemValue(BigInteger newTotalValue) {
        lock.writeLock().lock();
        try {
            this.totalSystemValue = newTotalValue;
            
            // Recalculate all exposure ratios
            for (ExposureData data : exposureMap.values()) {
                data.updateExposure(data.getCurrentExposure(), totalSystemValue,
                    MAX_SINGLE_CURRENCY_EXPOSURE);
            }
            
            LOGGER.info("Updated total system value: " + newTotalValue);
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Gets total foreign currency exposure
     */
    public BigInteger getTotalForeignExposure() {
        lock.readLock().lock();
        try {
            BigInteger total = BigInteger.ZERO;
            for (ExposureData data : exposureMap.values()) {
                total = total.add(data.getCurrentExposure());
            }
            return total;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets total foreign exposure ratio
     */
    public BigDecimal getTotalForeignExposureRatio() {
        lock.readLock().lock();
        try {
            if (totalSystemValue.compareTo(BigInteger.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            
            BigInteger totalExposure = getTotalForeignExposure();
            return new BigDecimal(totalExposure)
                .divide(new BigDecimal(totalSystemValue), 8, RoundingMode.HALF_UP);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Checks if adding exposure would exceed limits
     */
    public boolean canAddExposure(String currency, BigInteger additionalExposure) {
        lock.readLock().lock();
        try {
            ExposureData data = exposureMap.get(currency);
            BigInteger currentExposure = data != null ? 
                data.getCurrentExposure() : BigInteger.ZERO;
            
            BigInteger newExposure = currentExposure.add(additionalExposure);
            
            // Check single currency limit
            BigDecimal singleLimit = new BigDecimal(totalSystemValue)
                .multiply(MAX_SINGLE_CURRENCY_EXPOSURE);
            if (new BigDecimal(newExposure).compareTo(singleLimit) > 0) {
                return false;
            }
            
            // Check total foreign exposure limit
            BigInteger newTotalExposure = getTotalForeignExposure().add(additionalExposure);
            BigDecimal totalLimit = new BigDecimal(totalSystemValue)
                .multiply(MAX_TOTAL_FOREIGN_EXPOSURE);
            
            return new BigDecimal(newTotalExposure).compareTo(totalLimit) <= 0;
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets exposure data for a specific currency
     */
    public Optional<ExposureData> getExposureData(String currency) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(exposureMap.get(currency));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets all exposure data
     */
    public Map<String, ExposureData> getAllExposureData() {
        lock.readLock().lock();
        try {
            return new HashMap<>(exposureMap);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets currencies that need rebalancing
     */
    public List<String> getCurrenciesNeedingRebalance() {
        lock.readLock().lock();
        try {
            List<String> needRebalance = new ArrayList<>();
            
            for (ExposureData data : exposureMap.values()) {
                if (data.getExposureRatio().compareTo(REBALANCE_THRESHOLD) >= 0) {
                    needRebalance.add(data.getCurrency());
                }
            }
            
            return needRebalance;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets unresolved alerts
     */
    public List<ExposureAlert> getUnresolvedAlerts() {
        lock.readLock().lock();
        try {
            List<ExposureAlert> unresolved = new ArrayList<>();
            for (ExposureAlert alert : alertHistory) {
                if (!alert.isResolved()) {
                    unresolved.add(alert);
                }
            }
            return unresolved;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets recent exposure history
     */
    public List<ExposureSnapshot> getExposureHistory(int limit) {
        lock.readLock().lock();
        try {
            int size = exposureHistory.size();
            int fromIndex = Math.max(0, size - limit);
            return new ArrayList<>(exposureHistory.subList(fromIndex, size));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Resolves an alert
     */
    public void resolveAlert(ExposureAlert alert) {
        lock.writeLock().lock();
        try {
            alert.resolve();
            LOGGER.info("Resolved alert for " + alert.getCurrency());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Checks for exposure alerts
     */
    private void checkExposureAlerts(ExposureData data) {
        ExposureData.ExposureLevel level = data.getLevel();
        
        switch (level) {
            case EXCEEDED:
                createAlert(ExposureAlert.AlertType.EXCEEDED, data.getCurrency(),
                    data.getExposureRatio(),
                    String.format("EXCEEDED: %s exposure at %.2f%% (max: 100%%)",
                        data.getCurrency(),
                        data.getExposureRatio().multiply(BigDecimal.valueOf(100))));
                break;
                
            case CRITICAL:
                createAlert(ExposureAlert.AlertType.CRITICAL, data.getCurrency(),
                    data.getExposureRatio(),
                    String.format("CRITICAL: %s exposure at %.2f%% (threshold: 95%%)",
                        data.getCurrency(),
                        data.getExposureRatio().multiply(BigDecimal.valueOf(100))));
                break;
                
            case WARNING:
                createAlert(ExposureAlert.AlertType.WARNING, data.getCurrency(),
                    data.getExposureRatio(),
                    String.format("WARNING: %s exposure at %.2f%% (threshold: 80%%)",
                        data.getCurrency(),
                        data.getExposureRatio().multiply(BigDecimal.valueOf(100))));
                break;
                
            case SAFE:
                // No alert needed
                break;
        }
        
        // Check if rebalancing needed
        if (data.getExposureRatio().compareTo(REBALANCE_THRESHOLD) >= 0) {
            createAlert(ExposureAlert.AlertType.REBALANCE_NEEDED, data.getCurrency(),
                data.getExposureRatio(),
                String.format("Rebalancing needed for %s (ratio: %.2f%%)",
                    data.getCurrency(),
                    data.getExposureRatio().multiply(BigDecimal.valueOf(100))));
        }
    }
    
    /**
     * Creates an exposure alert
     */
    private void createAlert(ExposureAlert.AlertType type, String currency,
                            BigDecimal ratio, String message) {
        // Check if similar unresolved alert already exists
        for (ExposureAlert alert : alertHistory) {
            if (!alert.isResolved() && 
                alert.getCurrency().equals(currency) &&
                alert.getType() == type) {
                return; // Don't create duplicate
            }
        }
        
        ExposureAlert alert = new ExposureAlert(type, currency, ratio, message);
        alertHistory.add(alert);
        
        LOGGER.warning("EXPOSURE ALERT: " + message);
    }
    
    /**
     * Records a snapshot of current exposures
     */
    private void recordSnapshot() {
        Map<String, BigInteger> exposureByCurrency = new HashMap<>();
        for (Map.Entry<String, ExposureData> entry : exposureMap.entrySet()) {
            exposureByCurrency.put(entry.getKey(), entry.getValue().getCurrentExposure());
        }
        
        BigInteger totalExposure = getTotalForeignExposure();
        BigDecimal totalRatio = getTotalForeignExposureRatio();
        
        ExposureSnapshot snapshot = new ExposureSnapshot(
            exposureByCurrency, totalExposure, totalRatio, totalSystemValue
        );
        
        exposureHistory.add(snapshot);
        
        // Prune old history (keep last 1000 snapshots)
        if (exposureHistory.size() > 1000) {
            exposureHistory.remove(0);
        }
    }
    
    /**
     * Checks if snapshot should be recorded (every hour)
     */
    private boolean shouldRecordSnapshot() {
        if (exposureHistory.isEmpty()) {
            return true;
        }
        
        ExposureSnapshot last = exposureHistory.get(exposureHistory.size() - 1);
        long timeSinceLast = System.currentTimeMillis() - last.getTimestamp();
        
        return timeSinceLast >= 3600000; // 1 hour
    }
    
    /**
     * Gets system statistics
     */
    public Map<String, Object> getSystemStats() {
        lock.readLock().lock();
        try {
            Map<String, Object> stats = new HashMap<>();
            
            stats.put("totalSystemValue", totalSystemValue);
            stats.put("totalForeignExposure", getTotalForeignExposure());
            stats.put("totalForeignExposureRatio", getTotalForeignExposureRatio());
            stats.put("maxTotalForeignExposure", MAX_TOTAL_FOREIGN_EXPOSURE);
            stats.put("maxSingleCurrencyExposure", MAX_SINGLE_CURRENCY_EXPOSURE);
            stats.put("trackedCurrencies", exposureMap.size());
            stats.put("unresolvedAlerts", getUnresolvedAlerts().size());
            stats.put("currenciesNeedingRebalance", getCurrenciesNeedingRebalance().size());
            
            // Count by exposure level
            Map<String, Integer> byLevel = new HashMap<>();
            for (ExposureData.ExposureLevel level : ExposureData.ExposureLevel.values()) {
                byLevel.put(level.toString(), 0);
            }
            for (ExposureData data : exposureMap.values()) {
                String level = data.getLevel().toString();
                byLevel.put(level, byLevel.get(level) + 1);
            }
            stats.put("currenciesByExposureLevel", byLevel);
            
            return stats;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets exposure limits
     */
    public Map<String, BigDecimal> getExposureLimits() {
        Map<String, BigDecimal> limits = new HashMap<>();
        limits.put("maxTotalForeignExposure", MAX_TOTAL_FOREIGN_EXPOSURE);
        limits.put("maxSingleCurrencyExposure", MAX_SINGLE_CURRENCY_EXPOSURE);
        limits.put("warningThreshold", WARNING_THRESHOLD);
        limits.put("criticalThreshold", CRITICAL_THRESHOLD);
        limits.put("rebalanceThreshold", REBALANCE_THRESHOLD);
        return limits;
    }
}