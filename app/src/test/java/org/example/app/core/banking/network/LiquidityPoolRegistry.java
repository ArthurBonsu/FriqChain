package org.example.app.core.banking.network;

import org.example.app.core.blockchain.wallet.LiquidityPool;
import org.example.app.core.banking.tokenization.ExchangeRateOracle;
import org.example.app.core.banking.tokenization.AntiManipulationController;
import org.example.app.core.crypto.PKIManager;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;
import org.example.app.core.config.NetworkConfig;
import org.example.app.core.internal.EventLog;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * LiquidityPoolRegistry - Government-resistant registry for African-focused liquidity pools
 * 
 * Manages multiple liquidity pools with fair distribution, anti-manipulation protection,
 * and prioritization of African currency pairs to support financial independence.
 */
public class LiquidityPoolRegistry {
    private static final Logger LOGGER = Logger.getLogger(LiquidityPoolRegistry.class.getName());
    
    // Fair distribution constants
    private static final double AFRICAN_POOL_WEIGHT = 0.60; // 60% weight for African pools
    private static final double GLOBAL_POOL_WEIGHT = 0.40;  // 40% weight for global pools
    private static final int MIN_POOLS_PER_CURRENCY = 3;     // Minimum pools per currency for resistance
    private static final int MAX_COUNTRY_POOLS = 33;        // Max 33% pools from single country
    
    // Pool performance thresholds
    private static final BigDecimal MIN_LIQUIDITY_THRESHOLD = new BigDecimal("100000"); // $100k minimum
    private static final BigDecimal MAX_SPREAD_THRESHOLD = new BigDecimal("0.05");       // 5% max spread
    private static final double MIN_UPTIME_THRESHOLD = 0.95; // 95% minimum uptime
    private static final long POOL_EVALUATION_INTERVAL = 3600000; // 1 hour evaluation cycle
    
    // Pool registry and management
    private final Map<String, RegisteredPool> pools = new ConcurrentHashMap<>();
    private final Map<String, List<String>> currencyPoolMapping = new ConcurrentHashMap<>();
    private final Map<String, PoolPerformanceMetrics> performanceMetrics = new ConcurrentHashMap<>();
    private final Map<String, GeographicDistribution> geographicTracker = new ConcurrentHashMap<>();
    
    // Fair rate calculation and anti-manipulation
    private final ExchangeRateOracle exchangeRateOracle;
    private final AntiManipulationController antiManipulationController;
    private final PKIManager pkiManager;
    private final NetworkConfig networkConfig;
    
    // Pool quality assessment
    private final Map<String, PoolQualityScore> qualityScores = new ConcurrentHashMap<>();
    private final Set<String> blacklistedPools = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> suspendedPools = new ConcurrentHashMap<>();
    
    // Concurrency control
    private final ReentrantReadWriteLock registryLock = new ReentrantReadWriteLock();
    private final ScheduledExecutorService evaluationScheduler = Executors.newScheduledThreadPool(3);
    private final ExecutorService asyncOperations = Executors.newFixedThreadPool(10);
    
    // System metrics
    private final AtomicLong totalRegisteredPools = new AtomicLong(0);
    private final AtomicLong africanPoolCount = new AtomicLong(0);
    private final AtomicLong activePoolCount = new AtomicLong(0);
    private final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Pool registration data structure
     */
    public static class RegisteredPool {
        private final String poolId;
        private final LiquidityPool poolInstance;
        private final String baseCurrency;
        private final String quoteCurrency;
        private final String countryCode;
        private final boolean isAfricanFocused;
        private final long registrationTimestamp;
        private volatile PoolStatus status;
        private volatile BigDecimal totalLiquidity;
        private volatile BigDecimal currentSpread;
        private volatile double uptimePercentage;
        
        public RegisteredPool(String poolId, LiquidityPool poolInstance, String baseCurrency, 
                            String quoteCurrency, String countryCode, boolean isAfricanFocused) {
            this.poolId = poolId;
            this.poolInstance = poolInstance;
            this.baseCurrency = baseCurrency;
            this.quoteCurrency = quoteCurrency;
            this.countryCode = countryCode;
            this.isAfricanFocused = isAfricanFocused;
            this.registrationTimestamp = System.currentTimeMillis();
            this.status = PoolStatus.PENDING_VERIFICATION;
            this.totalLiquidity = BigDecimal.ZERO;
            this.currentSpread = BigDecimal.ZERO;
            this.uptimePercentage = 0.0;
        }
        
        // Getters
        public String getPoolId() { return poolId; }
        public LiquidityPool getPoolInstance() { return poolInstance; }
        public String getBaseCurrency() { return baseCurrency; }
        public String getQuoteCurrency() { return quoteCurrency; }
        public String getCountryCode() { return countryCode; }
        public boolean isAfricanFocused() { return isAfricanFocused; }
        public long getRegistrationTimestamp() { return registrationTimestamp; }
        public PoolStatus getStatus() { return status; }
        public BigDecimal getTotalLiquidity() { return totalLiquidity; }
        public BigDecimal getCurrentSpread() { return currentSpread; }
        public double getUptimePercentage() { return uptimePercentage; }
        
        public void setStatus(PoolStatus status) { this.status = status; }
        public void setTotalLiquidity(BigDecimal totalLiquidity) { this.totalLiquidity = totalLiquidity; }
        public void setCurrentSpread(BigDecimal currentSpread) { this.currentSpread = currentSpread; }
        public void setUptimePercentage(double uptimePercentage) { this.uptimePercentage = uptimePercentage; }
    }
    
    /**
     * Pool status enumeration
     */
    public enum PoolStatus {
        PENDING_VERIFICATION,
        ACTIVE,
        SUSPENDED,
        BLACKLISTED,
        MAINTENANCE,
        FAILED_VERIFICATION
    }
    
    /**
     * Pool performance metrics
     */
    public static class PoolPerformanceMetrics {
        private final String poolId;
        private BigDecimal averageSpread = BigDecimal.ZERO;
        private BigDecimal totalVolume = BigDecimal.ZERO;
        private long totalTransactions = 0;
        private double uptimeScore = 0.0;
        private int failureCount = 0;
        private long lastUpdateTimestamp;
        
        public PoolPerformanceMetrics(String poolId) {
            this.poolId = poolId;
            this.lastUpdateTimestamp = System.currentTimeMillis();
        }
        
        // Performance tracking methods
        public void updateMetrics(BigDecimal spread, BigDecimal volume, boolean isUptime) {
            this.averageSpread = calculateRunningAverage(averageSpread, spread, totalTransactions + 1);
            this.totalVolume = totalVolume.add(volume);
            this.totalTransactions++;
            
            if (isUptime) {
                uptimeScore = (uptimeScore * (totalTransactions - 1) + 1.0) / totalTransactions;
            } else {
                uptimeScore = (uptimeScore * (totalTransactions - 1)) / totalTransactions;
                failureCount++;
            }
            
            this.lastUpdateTimestamp = System.currentTimeMillis();
        }
        
        private BigDecimal calculateRunningAverage(BigDecimal currentAvg, BigDecimal newValue, long count) {
            if (count <= 1) return newValue;
            BigDecimal factor = new BigDecimal(count - 1).divide(new BigDecimal(count), MathContext.DECIMAL128);
            BigDecimal newFactor = BigDecimal.ONE.divide(new BigDecimal(count), MathContext.DECIMAL128);
            return currentAvg.multiply(factor).add(newValue.multiply(newFactor));
        }
        
        // Getters
        public String getPoolId() { return poolId; }
        public BigDecimal getAverageSpread() { return averageSpread; }
        public BigDecimal getTotalVolume() { return totalVolume; }
        public long getTotalTransactions() { return totalTransactions; }
        public double getUptimeScore() { return uptimeScore; }
        public int getFailureCount() { return failureCount; }
        public long getLastUpdateTimestamp() { return lastUpdateTimestamp; }
    }
    
    /**
     * Geographic distribution tracker
     */
    public static class GeographicDistribution {
        private final Map<String, Integer> countryPoolCounts = new ConcurrentHashMap<>();
        private final Map<String, BigDecimal> countryLiquidityShares = new ConcurrentHashMap<>();
        private int totalPools = 0;
        private BigDecimal totalLiquidity = BigDecimal.ZERO;
        
        public synchronized void addPool(String countryCode, BigDecimal liquidity) {
            countryPoolCounts.merge(countryCode, 1, Integer::sum);
            countryLiquidityShares.merge(countryCode, liquidity, BigDecimal::add);
            totalPools++;
            totalLiquidity = totalLiquidity.add(liquidity);
        }
        
        public synchronized void removePool(String countryCode, BigDecimal liquidity) {
            Integer count = countryPoolCounts.get(countryCode);
            if (count != null && count > 0) {
                if (count == 1) {
                    countryPoolCounts.remove(countryCode);
                    countryLiquidityShares.remove(countryCode);
                } else {
                    countryPoolCounts.put(countryCode, count - 1);
                    BigDecimal currentLiquidity = countryLiquidityShares.get(countryCode);
                    countryLiquidityShares.put(countryCode, currentLiquidity.subtract(liquidity));
                }
                totalPools--;
                totalLiquidity = totalLiquidity.subtract(liquidity);
            }
        }
        
        public boolean exceedsCountryLimit(String countryCode) {
            Integer count = countryPoolCounts.get(countryCode);
            if (count == null) return false;
            return (double) count / totalPools > (MAX_COUNTRY_POOLS / 100.0);
        }
        
        public Map<String, Integer> getCountryPoolCounts() { return new HashMap<>(countryPoolCounts); }
        public Map<String, BigDecimal> getCountryLiquidityShares() { return new HashMap<>(countryLiquidityShares); }
        public int getTotalPools() { return totalPools; }
        public BigDecimal getTotalLiquidity() { return totalLiquidity; }
    }
    
    /**
     * Pool quality scoring
     */
    public static class PoolQualityScore {
        private final String poolId;
        private double liquidityScore = 0.0;
        private double spreadScore = 0.0;
        private double uptimeScore = 0.0;
        private double volumeScore = 0.0;
        private double diversityScore = 0.0;
        private double overallScore = 0.0;
        private long lastCalculated;
        
        public PoolQualityScore(String poolId) {
            this.poolId = poolId;
            this.lastCalculated = System.currentTimeMillis();
        }
        
        public void calculateOverallScore(RegisteredPool pool, PoolPerformanceMetrics metrics) {
            // Liquidity score (30%)
            liquidityScore = Math.min(1.0, pool.getTotalLiquidity().divide(MIN_LIQUIDITY_THRESHOLD, MathContext.DECIMAL128).doubleValue());
            
            // Spread score (25%) - lower spread is better
            double spreadRatio = pool.getCurrentSpread().divide(MAX_SPREAD_THRESHOLD, MathContext.DECIMAL128).doubleValue();
            spreadScore = Math.max(0.0, 1.0 - spreadRatio);
            
            // Uptime score (20%)
            uptimeScore = Math.min(1.0, pool.getUptimePercentage() / MIN_UPTIME_THRESHOLD);
            
            // Volume score (15%)
            volumeScore = Math.min(1.0, metrics.getTotalVolume().divide(new BigDecimal("1000000"), MathContext.DECIMAL128).doubleValue());
            
            // African diversity bonus (10%)
            diversityScore = pool.isAfricanFocused() ? 1.0 : 0.5;
            
            // Calculate weighted overall score
            overallScore = (liquidityScore * 0.30) + 
                          (spreadScore * 0.25) + 
                          (uptimeScore * 0.20) + 
                          (volumeScore * 0.15) + 
                          (diversityScore * 0.10);
            
            this.lastCalculated = System.currentTimeMillis();
        }
        
        // Getters
        public String getPoolId() { return poolId; }
        public double getOverallScore() { return overallScore; }
        public double getLiquidityScore() { return liquidityScore; }
        public double getSpreadScore() { return spreadScore; }
        public double getUptimeScore() { return uptimeScore; }
        public double getVolumeScore() { return volumeScore; }
        public double getDiversityScore() { return diversityScore; }
        public long getLastCalculated() { return lastCalculated; }
    }
    
    /**
     * Constructor
     */
    public LiquidityPoolRegistry(ExchangeRateOracle exchangeRateOracle,
                               AntiManipulationController antiManipulationController,
                               PKIManager pkiManager,
                               NetworkConfig networkConfig) {
        this.exchangeRateOracle = exchangeRateOracle;
        this.antiManipulationController = antiManipulationController;
        this.pkiManager = pkiManager;
        this.networkConfig = networkConfig;
        
        // Start periodic evaluation
        startPeriodicEvaluation();
        
        LOGGER.info("LiquidityPoolRegistry initialized with African prioritization");
    }
    
    /**
     * Register a new liquidity pool
     */
    public CompletableFuture<String> registerPool(LiquidityPool pool, String baseCurrency, 
                                                String quoteCurrency, String countryCode, 
                                                boolean isAfricanFocused) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                registryLock.writeLock().lock();
                
                // Generate unique pool ID
                String poolId = generatePoolId(baseCurrency, quoteCurrency, countryCode);
                
                // Check geographic distribution limits
                String currencyPair = baseCurrency + "/" + quoteCurrency;
                GeographicDistribution distribution = geographicTracker.computeIfAbsent(
                    currencyPair, k -> new GeographicDistribution()
                );
                
                if (distribution.exceedsCountryLimit(countryCode)) {
                    throw new IllegalStateException("Country " + countryCode + " exceeds maximum pool limit for " + currencyPair);
                }
                
                // Create registered pool
                RegisteredPool registeredPool = new RegisteredPool(
                    poolId, pool, baseCurrency, quoteCurrency, countryCode, isAfricanFocused
                );
                
                // Perform initial verification
                if (verifyPoolRequirements(registeredPool)) {
                    pools.put(poolId, registeredPool);
                    
                    // Update mappings
                    currencyPoolMapping.computeIfAbsent(currencyPair, k -> new ArrayList<>()).add(poolId);
                    performanceMetrics.put(poolId, new PoolPerformanceMetrics(poolId));
                    qualityScores.put(poolId, new PoolQualityScore(poolId));
                    
                    // Update geographic tracking
                    distribution.addPool(countryCode, pool.getTotalLiquidity());
                    
                    // Update counters
                    totalRegisteredPools.incrementAndGet();
                    if (isAfricanFocused) {
                        africanPoolCount.incrementAndGet();
                    }
                    
                    registeredPool.setStatus(PoolStatus.ACTIVE);
                    activePoolCount.incrementAndGet();
                    
                    LOGGER.info("Successfully registered pool: " + poolId + " for " + currencyPair);
                    EventLog.log("POOL_REGISTERED", "Pool " + poolId + " registered for " + currencyPair);
                    
                    return poolId;
                } else {
                    registeredPool.setStatus(PoolStatus.FAILED_VERIFICATION);
                    throw new IllegalArgumentException("Pool failed verification requirements");
                }
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to register pool", e);
                throw new RuntimeException("Pool registration failed: " + e.getMessage(), e);
            } finally {
                registryLock.writeLock().unlock();
            }
        }, asyncOperations);
    }
    
    /**
     * Get optimal pools for currency pair
     */
    public List<RegisteredPool> getOptimalPoolsForPair(String baseCurrency, String quoteCurrency, 
                                                     int maxPools) {
        registryLock.readLock().lock();
        try {
            String currencyPair = baseCurrency + "/" + quoteCurrency;
            List<String> poolIds = currencyPoolMapping.get(currencyPair);
            
            if (poolIds == null || poolIds.isEmpty()) {
                return Collections.emptyList();
            }
            
            // Filter active pools and sort by quality score
            return poolIds.stream()
                .map(pools::get)
                .filter(pool -> pool != null && pool.getStatus() == PoolStatus.ACTIVE)
                .filter(pool -> !blacklistedPools.contains(pool.getPoolId()))
                .sorted((p1, p2) -> {
                    PoolQualityScore s1 = qualityScores.get(p1.getPoolId());
                    PoolQualityScore s2 = qualityScores.get(p2.getPoolId());
                    
                    // African pools get priority if quality is close
                    if (Math.abs(s1.getOverallScore() - s2.getOverallScore()) < 0.1) {
                        if (p1.isAfricanFocused() && !p2.isAfricanFocused()) return -1;
                        if (!p1.isAfricanFocused() && p2.isAfricanFocused()) return 1;
                    }
                    
                    return Double.compare(s2.getOverallScore(), s1.getOverallScore());
                })
                .limit(maxPools)
                .collect(Collectors.toList());
                
        } finally {
            registryLock.readLock().unlock();
        }
    }
    
    /**
     * Update pool performance metrics
     */
    public void updatePoolPerformance(String poolId, BigDecimal spread, BigDecimal volume, 
                                    boolean isOnline) {
        asyncOperations.execute(() -> {
            try {
                PoolPerformanceMetrics metrics = performanceMetrics.get(poolId);
                RegisteredPool pool = pools.get(poolId);
                
                if (metrics != null && pool != null) {
                    metrics.updateMetrics(spread, volume, isOnline);
                    
                    // Update pool data
                    pool.setCurrentSpread(spread);
                    pool.setUptimePercentage(metrics.getUptimeScore());
                    
                    // Recalculate quality score
                    PoolQualityScore qualityScore = qualityScores.get(poolId);
                    if (qualityScore != null) {
                        qualityScore.calculateOverallScore(pool, metrics);
                    }
                    
                    // Check if pool needs suspension
                    if (metrics.getUptimeScore() < MIN_UPTIME_THRESHOLD || 
                        spread.compareTo(MAX_SPREAD_THRESHOLD) > 0) {
                        suspendPool(poolId, "Performance below threshold");
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to update pool performance for " + poolId, e);
            }
        });
    }
    
    /**
     * Suspend a pool
     */
    public void suspendPool(String poolId, String reason) {
        registryLock.writeLock().lock();
        try {
            RegisteredPool pool = pools.get(poolId);
            if (pool != null && pool.getStatus() == PoolStatus.ACTIVE) {
                pool.setStatus(PoolStatus.SUSPENDED);
                suspendedPools.put(poolId, System.currentTimeMillis());
                activePoolCount.decrementAndGet();
                
                LOGGER.warning("Pool " + poolId + " suspended: " + reason);
                EventLog.log("POOL_SUSPENDED", "Pool " + poolId + " suspended: " + reason);
            }
        } finally {
            registryLock.writeLock().unlock();
        }
    }
    
    /**
     * Get registry statistics
     */
    public RegistryStatistics getStatistics() {
        registryLock.readLock().lock();
        try {
            return new RegistryStatistics(
                totalRegisteredPools.get(),
                activePoolCount.get(),
                africanPoolCount.get(),
                suspendedPools.size(),
                blacklistedPools.size(),
                geographicTracker.size()
            );
        } finally {
            registryLock.readLock().unlock();
        }
    }
    
    /**
     * Registry statistics data structure
     */
    public static class RegistryStatistics {
        private final long totalPools;
        private final long activePools;
        private final long africanPools;
        private final long suspendedPools;
        private final long blacklistedPools;
        private final long currencyPairs;
        
        public RegistryStatistics(long totalPools, long activePools, long africanPools,
                                long suspendedPools, long blacklistedPools, long currencyPairs) {
            this.totalPools = totalPools;
            this.activePools = activePools;
            this.africanPools = africanPools;
            this.suspendedPools = suspendedPools;
            this.blacklistedPools = blacklistedPools;
            this.currencyPairs = currencyPairs;
        }
        
        // Getters
        public long getTotalPools() { return totalPools; }
        public long getActivePools() { return activePools; }
        public long getAfricanPools() { return africanPools; }
        public long getSuspendedPools() { return suspendedPools; }
        public long getBlacklistedPools() { return blacklistedPools; }
        public long getCurrencyPairs() { return currencyPairs; }
    }
    
    /**
     * Generate unique pool ID
     */
    private String generatePoolId(String baseCurrency, String quoteCurrency, String countryCode) {
        try {
            String input = baseCurrency + quoteCurrency + countryCode + System.currentTimeMillis() + secureRandom.nextLong();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            return "POOL-" + bytesToHex(hash).substring(0, 16).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * Convert bytes to hexadecimal string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /**
     * Verify pool meets requirements
     */
    private boolean verifyPoolRequirements(RegisteredPool pool) {
        try {
            // Check minimum liquidity
            if (pool.getPoolInstance().getTotalLiquidity().compareTo(MIN_LIQUIDITY_THRESHOLD) < 0) {
                LOGGER.warning("Pool " + pool.getPoolId() + " below minimum liquidity threshold");
                return false;
            }
            
            // Verify pool instance is functional
            if (pool.getPoolInstance() == null) {
                LOGGER.warning("Pool " + pool.getPoolId() + " has null pool instance");
                return false;
            }
            
            // Additional verification can be added here
            return true;
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Pool verification failed for " + pool.getPoolId(), e);
            return false;
        }
    }
    
    /**
     * Start periodic evaluation of pools
     */
    private void startPeriodicEvaluation() {
        evaluationScheduler.scheduleAtFixedRate(
            this::evaluateAllPools,
            POOL_EVALUATION_INTERVAL,
            POOL_EVALUATION_INTERVAL,
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * Evaluate all pools performance
     */
    private void evaluateAllPools() {
        try {
            LOGGER.info("Starting periodic pool evaluation");
            
            for (RegisteredPool pool : pools.values()) {
                if (pool.getStatus() == PoolStatus.ACTIVE) {
                    evaluatePoolHealth(pool);
                }
            }
            
            LOGGER.info("Completed periodic pool evaluation");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during periodic pool evaluation", e);
        }
    }
    
    /**
     * Evaluate individual pool health
     */
    private void evaluatePoolHealth(RegisteredPool pool) {
        try {
            PoolPerformanceMetrics metrics = performanceMetrics.get(pool.getPoolId());
            if (metrics == null) return;
            
            // Check for suspended pools that can be reactivated
            if (pool.getStatus() == PoolStatus.SUSPENDED) {
                Long suspensionTime = suspendedPools.get(pool.getPoolId());
                if (suspensionTime != null && 
                    (System.currentTimeMillis() - suspensionTime) > 3600000) { // 1 hour
                    
                    if (metrics.getUptimeScore() >= MIN_UPTIME_THRESHOLD &&
                        pool.getCurrentSpread().compareTo(MAX_SPREAD_THRESHOLD) <= 0) {
                        
                        pool.setStatus(PoolStatus.ACTIVE);
                        suspendedPools.remove(pool.getPoolId());
                        activePoolCount.incrementAndGet();
                        
                        LOGGER.info("Pool " + pool.getPoolId() + " reactivated after suspension");
                    }
                }
            }
            
            // Update quality scores
            PoolQualityScore qualityScore = qualityScores.get(pool.getPoolId());
            if (qualityScore != null) {
                qualityScore.calculateOverallScore(pool, metrics);
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to evaluate pool " + pool.getPoolId(), e);
        }
    }
    
    /**
     * Shutdown the registry
     */
    public void shutdown() {
        try {
            evaluationScheduler.shutdown();
            asyncOperations.shutdown();
            
            if (!evaluationScheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                evaluationScheduler.shutdownNow();
            }
            if (!asyncOperations.awaitTermination(30, TimeUnit.SECONDS)) {
                asyncOperations.shutdownNow();
            }
            
            LOGGER.info("LiquidityPoolRegistry shutdown completed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Registry shutdown interrupted");
        }
    }
}