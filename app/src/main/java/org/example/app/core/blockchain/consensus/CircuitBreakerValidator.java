package org.example.app.core.blockchain.consensus;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * CircuitBreakerValidator - Emergency stops and protection mechanisms
 * 
 * Based on Implementation Guidebook Section VI: Oracle Data Integrity System
 * 
 * Circuit breakers activate when:
 * - Liquidity drain attacks detected
 * - Coordinated price manipulation
 * - Abnormal transaction volumes
 * - System instability indicators
 * - DAO emergency proposals
 * 
 * Protection Levels:
 * 1. WARNING: Increased monitoring, no action
 * 2. THROTTLED: Rate limiting enforced
 * 3. PAUSED: Trading paused temporarily
 * 4. EMERGENCY_STOP: All operations halted
 */
public class CircuitBreakerValidator {
    private static final Logger LOGGER = Logger.getLogger(CircuitBreakerValidator.class.getName());
    
    // Circuit breaker states
    public enum CircuitState {
        NORMAL,      // System operating normally
        WARNING,     // Monitoring increased
        THROTTLED,   // Rate limiting active
        PAUSED,      // Trading temporarily paused
        EMERGENCY_STOP // Full system halt
    }
    
    // Thresholds for triggering circuit breakers
    private static final BigDecimal LIQUIDITY_DRAIN_THRESHOLD = new BigDecimal("0.20"); // 20% drain
    private static final BigDecimal PRICE_VOLATILITY_THRESHOLD = new BigDecimal("0.15"); // 15% price swing
    private static final long ABNORMAL_VOLUME_MULTIPLIER = 5; // 5x normal volume
    private static final int MAX_FAILED_TRANSACTIONS_PER_MINUTE = 100;
    
    // Circuit breaker cooldowns
    private static final long WARNING_COOLDOWN_MS = 300000; // 5 minutes
    private static final long THROTTLE_COOLDOWN_MS = 1800000; // 30 minutes
    private static final long PAUSE_COOLDOWN_MS = 3600000; // 1 hour
    
    // Rate limiting when throttled
    private static final long THROTTLE_MIN_DELAY_MS = 1000; // 1 second between transactions
    
    // Current state
    private CircuitState currentState;
    private final AtomicLong stateChangeTime;
    private final AtomicBoolean emergencyStopActive;
    
    // Tracking for trigger detection
    private final Map<String, CircuitBreakerMetrics> assetMetrics;
    private final Map<String, RateLimiter> addressRateLimiters;
    private final List<CircuitBreakerEvent> eventHistory;
    private final AtomicLong failedTransactionCount;
    
    /**
     * Metrics for circuit breaker monitoring
     */
    private static class CircuitBreakerMetrics {
        final String assetId;
        BigDecimal currentLiquidity;
        BigDecimal previousLiquidity;
        BigDecimal currentPrice;
        BigDecimal previousPrice;
        long currentVolume;
        long averageVolume;
        long lastUpdate;
        
        CircuitBreakerMetrics(String assetId) {
            this.assetId = assetId;
            this.currentLiquidity = BigDecimal.ZERO;
            this.previousLiquidity = BigDecimal.ZERO;
            this.currentPrice = BigDecimal.ZERO;
            this.previousPrice = BigDecimal.ZERO;
            this.currentVolume = 0;
            this.averageVolume = 0;
            this.lastUpdate = System.currentTimeMillis();
        }
    }
    
    /**
     * Rate limiter for addresses
     */
    private static class RateLimiter {
        final String address;
        long lastTransactionTime;
        int transactionsInWindow;
        long windowStartTime;
        
        RateLimiter(String address) {
            this.address = address;
            this.lastTransactionTime = 0;
            this.transactionsInWindow = 0;
            this.windowStartTime = System.currentTimeMillis();
        }
        
        boolean canTransact(long minDelay) {
            long now = System.currentTimeMillis();
            
            // Reset window every minute
            if (now - windowStartTime > 60000) {
                windowStartTime = now;
                transactionsInWindow = 0;
            }
            
            // Check rate limit
            return (now - lastTransactionTime) >= minDelay;
        }
        
        void recordTransaction() {
            lastTransactionTime = System.currentTimeMillis();
            transactionsInWindow++;
        }
    }
    
    /**
     * Circuit breaker event record
     */
    public static class CircuitBreakerEvent {
        private final CircuitState previousState;
        private final CircuitState newState;
        private final String trigger;
        private final String description;
        private final long timestamp;
        private final Map<String, Object> metadata;
        
        public CircuitBreakerEvent(CircuitState prev, CircuitState newState, 
                                  String trigger, String description) {
            this.previousState = prev;
            this.newState = newState;
            this.trigger = trigger;
            this.description = description;
            this.timestamp = System.currentTimeMillis();
            this.metadata = new HashMap<>();
        }
        
        public CircuitState getPreviousState() { return previousState; }
        public CircuitState getNewState() { return newState; }
        public String getTrigger() { return trigger; }
        public String getDescription() { return description; }
        public long getTimestamp() { return timestamp; }
        public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
        
        public void addMetadata(String key, Object value) { metadata.put(key, value); }
    }
    
    /**
     * Validation result
     */
    public static class ValidationResult {
        private final boolean allowed;
        private final String reason;
        private final CircuitState currentState;
        private final long retryAfterMs;
        
        private ValidationResult(boolean allowed, String reason, CircuitState state, long retryAfter) {
            this.allowed = allowed;
            this.reason = reason;
            this.currentState = state;
            this.retryAfterMs = retryAfter;
        }
        
        public static ValidationResult allow() {
            return new ValidationResult(true, null, CircuitState.NORMAL, 0);
        }
        
        public static ValidationResult deny(String reason, CircuitState state, long retryAfter) {
            return new ValidationResult(false, reason, state, retryAfter);
        }
        
        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
        public CircuitState getCurrentState() { return currentState; }
        public long getRetryAfterMs() { return retryAfterMs; }
    }
    
    /**
     * Constructor
     */
    public CircuitBreakerValidator() {
        this.currentState = CircuitState.NORMAL;
        this.stateChangeTime = new AtomicLong(System.currentTimeMillis());
        this.emergencyStopActive = new AtomicBoolean(false);
        this.assetMetrics = new ConcurrentHashMap<>();
        this.addressRateLimiters = new ConcurrentHashMap<>();
        this.eventHistory = Collections.synchronizedList(new ArrayList<>());
        this.failedTransactionCount = new AtomicLong(0);
        
        LOGGER.info("CircuitBreakerValidator initialized in NORMAL state");
    }
    
    /**
     * Checks if a transaction can proceed
     */
    public ValidationResult checkTransaction(String fromAddress, String assetId, BigDecimal amount) {
        // Emergency stop overrides everything
        if (emergencyStopActive.get()) {
            return ValidationResult.deny(
                "Emergency stop is active. All transactions halted.",
                CircuitState.EMERGENCY_STOP,
                0
            );
        }
        
        // Check current state
        switch (currentState) {
            case EMERGENCY_STOP:
                return ValidationResult.deny(
                    "System in emergency stop state",
                    currentState,
                    0
                );
                
            case PAUSED:
                long pauseRemaining = getRemainingCooldown(PAUSE_COOLDOWN_MS);
                return ValidationResult.deny(
                    "Trading is paused",
                    currentState,
                    pauseRemaining
                );
                
            case THROTTLED:
                // Check rate limit
                RateLimiter limiter = addressRateLimiters.computeIfAbsent(
                    fromAddress, k -> new RateLimiter(fromAddress)
                );
                
                if (!limiter.canTransact(THROTTLE_MIN_DELAY_MS)) {
                    long waitTime = THROTTLE_MIN_DELAY_MS - 
                                   (System.currentTimeMillis() - limiter.lastTransactionTime);
                    return ValidationResult.deny(
                        "Rate limit exceeded",
                        currentState,
                        waitTime
                    );
                }
                
                limiter.recordTransaction();
                return ValidationResult.allow();
                
            case WARNING:
            case NORMAL:
            default:
                return ValidationResult.allow();
        }
    }
    
    /**
     * Updates metrics and checks for circuit breaker triggers
     */
    public void updateMetrics(String assetId, BigDecimal liquidity, BigDecimal price, long volume) {
        CircuitBreakerMetrics metrics = assetMetrics.computeIfAbsent(
            assetId, k -> new CircuitBreakerMetrics(assetId)
        );
        
        // Store previous values
        metrics.previousLiquidity = metrics.currentLiquidity;
        metrics.previousPrice = metrics.currentPrice;
        
        // Update current values
        metrics.currentLiquidity = liquidity;
        metrics.currentPrice = price;
        metrics.currentVolume = volume;
        metrics.lastUpdate = System.currentTimeMillis();
        
        // Update rolling average volume
        if (metrics.averageVolume == 0) {
            metrics.averageVolume = volume;
        } else {
            metrics.averageVolume = (metrics.averageVolume * 9 + volume) / 10; // Rolling avg
        }
        
        // Check for triggers
        checkLiquidityDrain(metrics);
        checkPriceVolatility(metrics);
        checkAbnormalVolume(metrics);
    }
    
    /**
     * Checks for liquidity drain attack
     */
    private void checkLiquidityDrain(CircuitBreakerMetrics metrics) {
        if (metrics.previousLiquidity.compareTo(BigDecimal.ZERO) == 0) {
            return; // No baseline yet
        }
        
        BigDecimal liquidityChange = metrics.currentLiquidity.subtract(metrics.previousLiquidity);
        BigDecimal drainPercentage = liquidityChange.abs()
            .divide(metrics.previousLiquidity, 4, java.math.RoundingMode.HALF_UP);
        
        if (drainPercentage.compareTo(LIQUIDITY_DRAIN_THRESHOLD) > 0 && 
            liquidityChange.compareTo(BigDecimal.ZERO) < 0) {
            
            LOGGER.warning(String.format("Liquidity drain detected for %s: %.2f%%",
                metrics.assetId, drainPercentage.multiply(new BigDecimal(100)).doubleValue()));
            
            triggerCircuitBreaker(
                CircuitState.PAUSED,
                "LIQUIDITY_DRAIN",
                String.format("Liquidity drain of %.2f%% detected for %s",
                    drainPercentage.multiply(new BigDecimal(100)).doubleValue(),
                    metrics.assetId)
            );
        }
    }
    
    /**
     * Checks for price volatility
     */
    private void checkPriceVolatility(CircuitBreakerMetrics metrics) {
        if (metrics.previousPrice.compareTo(BigDecimal.ZERO) == 0) {
            return; // No baseline yet
        }
        
        BigDecimal priceChange = metrics.currentPrice.subtract(metrics.previousPrice);
        BigDecimal volatilityPercentage = priceChange.abs()
            .divide(metrics.previousPrice, 4, java.math.RoundingMode.HALF_UP);
        
        if (volatilityPercentage.compareTo(PRICE_VOLATILITY_THRESHOLD) > 0) {
            LOGGER.warning(String.format("Price volatility detected for %s: %.2f%%",
                metrics.assetId, volatilityPercentage.multiply(new BigDecimal(100)).doubleValue()));
            
            if (currentState == CircuitState.NORMAL) {
                triggerCircuitBreaker(
                    CircuitState.WARNING,
                    "PRICE_VOLATILITY",
                    String.format("Price volatility of %.2f%% detected for %s",
                        volatilityPercentage.multiply(new BigDecimal(100)).doubleValue(),
                        metrics.assetId)
                );
            } else if (currentState == CircuitState.WARNING) {
                triggerCircuitBreaker(
                    CircuitState.THROTTLED,
                    "PRICE_VOLATILITY",
                    "Continued volatility, escalating to throttled state"
                );
            }
        }
    }
    
    /**
     * Checks for abnormal volume
     */
    private void checkAbnormalVolume(CircuitBreakerMetrics metrics) {
        if (metrics.averageVolume == 0) {
            return; // No baseline yet
        }
        
        long volumeRatio = metrics.currentVolume / metrics.averageVolume;
        
        if (volumeRatio >= ABNORMAL_VOLUME_MULTIPLIER) {
            LOGGER.warning(String.format("Abnormal volume detected for %s: %dx average",
                metrics.assetId, volumeRatio));
            
            if (currentState == CircuitState.NORMAL || currentState == CircuitState.WARNING) {
                triggerCircuitBreaker(
                    CircuitState.THROTTLED,
                    "ABNORMAL_VOLUME",
                    String.format("Volume %dx normal for %s", volumeRatio, metrics.assetId)
                );
            }
        }
    }
    
    /**
     * Records a failed transaction
     */
    public void recordFailedTransaction() {
        long count = failedTransactionCount.incrementAndGet();
        
        // Reset counter every minute
        if (count % 1000 == 0) {
            failedTransactionCount.set(0);
        }
        
        if (count >= MAX_FAILED_TRANSACTIONS_PER_MINUTE) {
            LOGGER.warning("High rate of failed transactions detected: " + count);
            
            if (currentState == CircuitState.NORMAL) {
                triggerCircuitBreaker(
                    CircuitState.WARNING,
                    "FAILED_TRANSACTIONS",
                    "High rate of failed transactions: " + count
                );
            }
        }
    }
    
    /**
     * Triggers a circuit breaker state change
     */
    private synchronized void triggerCircuitBreaker(CircuitState newState, 
                                                    String trigger, 
                                                    String description) {
        CircuitState previousState = currentState;
        
        // Don't downgrade state automatically
        if (newState.ordinal() < currentState.ordinal()) {
            return;
        }
        
        // Don't trigger same state twice
        if (newState == currentState) {
            return;
        }
        
        LOGGER.warning(String.format("Circuit breaker triggered: %s -> %s (%s: %s)",
            previousState, newState, trigger, description));
        
        currentState = newState;
        stateChangeTime.set(System.currentTimeMillis());
        
        // Record event
        CircuitBreakerEvent event = new CircuitBreakerEvent(
            previousState, newState, trigger, description
        );
        event.addMetadata("timestamp", System.currentTimeMillis());
        eventHistory.add(event);
        
        // Cleanup old events (keep last 1000)
        if (eventHistory.size() > 1000) {
            eventHistory.subList(0, eventHistory.size() - 1000).clear();
        }
    }
    
    /**
     * Activates emergency stop (requires DAO governance)
     */
    public void activateEmergencyStop(String reason) {
        emergencyStopActive.set(true);
        currentState = CircuitState.EMERGENCY_STOP;
        stateChangeTime.set(System.currentTimeMillis());
        
        LOGGER.severe("EMERGENCY STOP ACTIVATED: " + reason);
        
        CircuitBreakerEvent event = new CircuitBreakerEvent(
            currentState, CircuitState.EMERGENCY_STOP,
            "EMERGENCY_STOP", reason
        );
        eventHistory.add(event);
    }
    
    /**
     * Deactivates emergency stop (requires DAO governance)
     */
    public void deactivateEmergencyStop() {
        emergencyStopActive.set(false);
        currentState = CircuitState.NORMAL;
        stateChangeTime.set(System.currentTimeMillis());
        
        LOGGER.info("Emergency stop deactivated, returning to NORMAL state");
    }
    
    /**
     * Manual state reset (requires admin/DAO)
     */
    public void resetState() {
        currentState = CircuitState.NORMAL;
        stateChangeTime.set(System.currentTimeMillis());
        
        LOGGER.info("Circuit breaker state manually reset to NORMAL");
    }
    
    /**
     * Auto-recovery check (runs periodically)
     */
    public void checkAutoRecovery() {
        if (emergencyStopActive.get()) {
            return; // Emergency stop requires manual intervention
        }
        
        long timeSinceChange = System.currentTimeMillis() - stateChangeTime.get();
        
        switch (currentState) {
            case WARNING:
                if (timeSinceChange > WARNING_COOLDOWN_MS) {
                    currentState = CircuitState.NORMAL;
                    LOGGER.info("Auto-recovery: WARNING -> NORMAL");
                }
                break;
                
            case THROTTLED:
                if (timeSinceChange > THROTTLE_COOLDOWN_MS) {
                    currentState = CircuitState.WARNING;
                    LOGGER.info("Auto-recovery: THROTTLED -> WARNING");
                }
                break;
                
            case PAUSED:
                if (timeSinceChange > PAUSE_COOLDOWN_MS) {
                    currentState = CircuitState.THROTTLED;
                    LOGGER.info("Auto-recovery: PAUSED -> THROTTLED");
                }
                break;
        }
    }
    
    /**
     * Gets remaining cooldown time
     */
    private long getRemainingCooldown(long cooldownMs) {
        long elapsed = System.currentTimeMillis() - stateChangeTime.get();
        return Math.max(0, cooldownMs - elapsed);
    }
    
    /**
     * Gets current state
     */
    public CircuitState getCurrentState() {
        return currentState;
    }
    
    /**
     * Checks if emergency stop is active
     */
    public boolean isEmergencyStopActive() {
        return emergencyStopActive.get();
    }
    
    /**
     * Gets statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("currentState", currentState.toString());
        stats.put("emergencyStopActive", emergencyStopActive.get());
        stats.put("timeSinceStateChange", System.currentTimeMillis() - stateChangeTime.get());
        stats.put("assetsMonitored", assetMetrics.size());
        stats.put("addressesRateLimited", addressRateLimiters.size());
        stats.put("eventsRecorded", eventHistory.size());
        stats.put("failedTransactionCount", failedTransactionCount.get());
        
        // Recent events
        List<String> recentEvents = eventHistory.stream()
            .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
            .limit(5)
            .map(e -> String.format("%s: %s -> %s (%s)",
                new Date(e.getTimestamp()), e.getPreviousState(), 
                e.getNewState(), e.getTrigger()))
            .toList();
        stats.put("recentEvents", recentEvents);
        
        return stats;
    }
    
    /**
     * Gets event history
     */
    public List<CircuitBreakerEvent> getEventHistory() {
        return new ArrayList<>(eventHistory);
    }
}