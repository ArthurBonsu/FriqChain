package org.example.app.core.banking.network;

import org.example.app.core.types.Amount;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * LiquidityPoolRegistry manages liquidity pools for decentralized token swaps
 * within the African Tokenized Currency System.
 * 
 * Key Features:
 * - ASU/national currency pairs
 * - African exchange prioritization (50-60% bonus)
 * - Automated market maker (AMM) pools
 * - Fair pricing algorithms
 * - Slippage protection
 */
public class LiquidityPoolRegistry {
    private static final Logger logger = Logger.getLogger(LiquidityPoolRegistry.class.getName());
    
    // Configuration
    private static final double AFRICAN_EXCHANGE_BONUS = 0.55; // 55% bonus for African exchanges
    private static final double MIN_LIQUIDITY_THRESHOLD = 10000.0; // Minimum liquidity
    private static final double MAX_SLIPPAGE = 0.05; // 5% max slippage
    
    // Pool management
    private final Map<String, LiquidityPool> pools; // poolId → pool
    private final Map<String, Set<String>> tokenPools; // token → pool IDs
    private final Map<String, PoolStatistics> poolStats;
    
    /**
     * Liquidity pool
     */
    public static class LiquidityPool {
        private final String poolId;
        private final String tokenA;
        private final String tokenB;
        private BigInteger reserveA;
        private BigInteger reserveB;
        private final boolean isAfricanPool; // African-to-African token pair
        private final Set<String> liquidityProviders;
        private final Map<String, BigInteger> providerShares;
        private BigInteger totalShares;
        private long lastTradeTime;
        
        public LiquidityPool(String poolId, String tokenA, String tokenB, boolean isAfricanPool) {
            this.poolId = poolId;
            this.tokenA = tokenA;
            this.tokenB = tokenB;
            this.reserveA = BigInteger.ZERO;
            this.reserveB = BigInteger.ZERO;
            this.isAfricanPool = isAfricanPool;
            this.liquidityProviders = ConcurrentHashMap.newKeySet();
            this.providerShares = new ConcurrentHashMap<>();
            this.totalShares = BigInteger.ZERO;
            this.lastTradeTime = System.currentTimeMillis();
        }
        
        public String getPoolId() { return poolId; }
        public String getTokenA() { return tokenA; }
        public String getTokenB() { return tokenB; }
        public BigInteger getReserveA() { return reserveA; }
        public BigInteger getReserveB() { return reserveB; }
        public boolean isAfricanPool() { return isAfricanPool; }
        public long getLastTradeTime() { return lastTradeTime; }
        
        public void addLiquidity(String provider, BigInteger amountA, BigInteger amountB) {
            if (totalShares.equals(BigInteger.ZERO)) {
                // First liquidity provider
                totalShares = amountA.multiply(amountB).sqrt();
            } else {
                // Calculate shares proportional to existing pool
                BigInteger sharesA = amountA.multiply(totalShares).divide(reserveA);
                BigInteger sharesB = amountB.multiply(totalShares).divide(reserveB);
                totalShares = totalShares.add(sharesA.min(sharesB));
            }
            
            reserveA = reserveA.add(amountA);
            reserveB = reserveB.add(amountB);
            
            liquidityProviders.add(provider);
            providerShares.merge(provider, totalShares, BigInteger::add);
        }
        
        public void removeLiquidity(String provider, BigInteger shares) {
            BigInteger providerTotal = providerShares.getOrDefault(provider, BigInteger.ZERO);
            if (shares.compareTo(providerTotal) > 0) {
                throw new IllegalArgumentException("Insufficient shares");
            }
            
            BigInteger amountA = shares.multiply(reserveA).divide(totalShares);
            BigInteger amountB = shares.multiply(reserveB).divide(totalShares);
            
            reserveA = reserveA.subtract(amountA);
            reserveB = reserveB.subtract(amountB);
            totalShares = totalShares.subtract(shares);
            
            providerShares.compute(provider, (k, v) -> 
                v != null ? v.subtract(shares) : BigInteger.ZERO);
        }
        
        public void updateReserves(BigInteger newReserveA, BigInteger newReserveB) {
            this.reserveA = newReserveA;
            this.reserveB = newReserveB;
            this.lastTradeTime = System.currentTimeMillis();
        }
        
        public BigDecimal getPrice() {
            if (reserveB.equals(BigInteger.ZERO)) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(reserveA).divide(new BigDecimal(reserveB), 18, RoundingMode.HALF_UP);
        }
    }
    
    /**
     * Pool statistics
     */
    public static class PoolStatistics {
        private long totalTrades;
        private BigInteger totalVolumeA;
        private BigInteger totalVolumeB;
        private double avgSlippage;
        private long lastUpdateTime;
        
        public PoolStatistics() {
            this.totalTrades = 0;
            this.totalVolumeA = BigInteger.ZERO;
            this.totalVolumeB = BigInteger.ZERO;
            this.avgSlippage = 0.0;
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        public void recordTrade(BigInteger volumeA, BigInteger volumeB, double slippage) {
            totalTrades++;
            totalVolumeA = totalVolumeA.add(volumeA);
            totalVolumeB = totalVolumeB.add(volumeB);
            avgSlippage = (avgSlippage * (totalTrades - 1) + slippage) / totalTrades;
            lastUpdateTime = System.currentTimeMillis();
        }
        
        public long getTotalTrades() { return totalTrades; }
        public BigInteger getTotalVolumeA() { return totalVolumeA; }
        public BigInteger getTotalVolumeB() { return totalVolumeB; }
        public double getAvgSlippage() { return avgSlippage; }
    }
    
    /**
     * Constructor
     */
    public LiquidityPoolRegistry() {
        this.pools = new ConcurrentHashMap<>();
        this.tokenPools = new ConcurrentHashMap<>();
        this.poolStats = new ConcurrentHashMap<>();
        
        logger.info("LiquidityPoolRegistry initialized");
    }
    
    /**
     * Creates a new liquidity pool
     */
    public String createPool(String tokenA, String tokenB, boolean isAfricanPool) {
        String poolId = generatePoolId(tokenA, tokenB);
        
        if (pools.containsKey(poolId)) {
            throw new IllegalStateException("Pool already exists: " + poolId);
        }
        
        LiquidityPool pool = new LiquidityPool(poolId, tokenA, tokenB, isAfricanPool);
        pools.put(poolId, pool);
        
        tokenPools.computeIfAbsent(tokenA, k -> ConcurrentHashMap.newKeySet()).add(poolId);
        tokenPools.computeIfAbsent(tokenB, k -> ConcurrentHashMap.newKeySet()).add(poolId);
        
        poolStats.put(poolId, new PoolStatistics());
        
        logger.info("Created liquidity pool: " + poolId + 
            (isAfricanPool ? " (African priority)" : ""));
        
        return poolId;
    }
    
    /**
     * Generates pool ID from token pair
     */
    private String generatePoolId(String tokenA, String tokenB) {
        // Sort tokens alphabetically for consistency
        if (tokenA.compareTo(tokenB) > 0) {
            String temp = tokenA;
            tokenA = tokenB;
            tokenB = temp;
        }
        return "POOL-" + tokenA + "-" + tokenB;
    }
    
    /**
     * Adds liquidity to a pool
     */
    public void addLiquidity(String poolId, String provider, BigInteger amountA, BigInteger amountB) {
        LiquidityPool pool = pools.get(poolId);
        if (pool == null) {
            throw new IllegalArgumentException("Pool not found: " + poolId);
        }
        
        pool.addLiquidity(provider, amountA, amountB);
        logger.fine("Added liquidity to pool " + poolId + ": " + provider);
    }
    
    /**
     * Removes liquidity from a pool
     */
    public void removeLiquidity(String poolId, String provider, BigInteger shares) {
        LiquidityPool pool = pools.get(poolId);
        if (pool == null) {
            throw new IllegalArgumentException("Pool not found: " + poolId);
        }
        
        pool.removeLiquidity(provider, shares);
        logger.fine("Removed liquidity from pool " + poolId + ": " + provider);
    }
    
    /**
     * Executes a swap with African exchange prioritization
     */
    public BigInteger swap(String poolId, String tokenIn, BigInteger amountIn) {
        LiquidityPool pool = pools.get(poolId);
        if (pool == null) {
            throw new IllegalArgumentException("Pool not found: " + poolId);
        }
        
        // Calculate output amount using constant product formula (x * y = k)
        boolean isTokenA = tokenIn.equals(pool.getTokenA());
        BigInteger reserveIn = isTokenA ? pool.getReserveA() : pool.getReserveB();
        BigInteger reserveOut = isTokenA ? pool.getReserveB() : pool.getReserveA();
        
        // Apply African exchange bonus if applicable
        BigInteger effectiveAmountIn = amountIn;
        if (pool.isAfricanPool()) {
            // 55% bonus for African pools
            BigInteger bonus = amountIn.multiply(BigInteger.valueOf((long)(AFRICAN_EXCHANGE_BONUS * 100)))
                .divide(BigInteger.valueOf(100));
            effectiveAmountIn = amountIn.add(bonus);
            
            logger.fine("Applied African exchange bonus: " + 
                String.format("%.2f%%", AFRICAN_EXCHANGE_BONUS * 100));
        }
        
        // Calculate output with 0.3% fee
        BigInteger amountInWithFee = effectiveAmountIn.multiply(BigInteger.valueOf(997));
        BigInteger numerator = amountInWithFee.multiply(reserveOut);
        BigInteger denominator = reserveIn.multiply(BigInteger.valueOf(1000)).add(amountInWithFee);
        BigInteger amountOut = numerator.divide(denominator);
        
        // Check slippage
        double slippage = calculateSlippage(reserveIn, reserveOut, amountIn, amountOut);
        if (slippage > MAX_SLIPPAGE) {
            throw new IllegalStateException("Slippage too high: " + 
                String.format("%.2f%%", slippage * 100));
        }
        
        // Update reserves
        BigInteger newReserveIn = reserveIn.add(amountIn);
        BigInteger newReserveOut = reserveOut.subtract(amountOut);
        
        if (isTokenA) {
            pool.updateReserves(newReserveIn, newReserveOut);
        } else {
            pool.updateReserves(newReserveOut, newReserveIn);
        }
        
        // Update statistics
        PoolStatistics stats = poolStats.get(poolId);
        if (stats != null) {
            stats.recordTrade(
                isTokenA ? amountIn : amountOut,
                isTokenA ? amountOut : amountIn,
                slippage
            );
        }
        
        logger.fine("Swap executed in pool " + poolId + 
            ": " + amountIn + " " + tokenIn + " → " + amountOut);
        
        return amountOut;
    }
    
    /**
     * Calculates slippage for a trade
     */
    private double calculateSlippage(BigInteger reserveIn, BigInteger reserveOut,
                                    BigInteger amountIn, BigInteger amountOut) {
        BigDecimal price = new BigDecimal(reserveOut).divide(new BigDecimal(reserveIn), 18, RoundingMode.HALF_UP);
        BigDecimal expectedOut = price.multiply(new BigDecimal(amountIn));
        BigDecimal actualOut = new BigDecimal(amountOut);
        
        return expectedOut.subtract(actualOut).divide(expectedOut, 18, RoundingMode.HALF_UP)
            .abs().doubleValue();
    }
    
    /**
     * Gets pool by ID
     */
    public LiquidityPool getPool(String poolId) {
        return pools.get(poolId);
    }
    
    /**
     * Finds pool for token pair
     */
    public String findPool(String tokenA, String tokenB) {
        String poolId = generatePoolId(tokenA, tokenB);
        return pools.containsKey(poolId) ? poolId : null;
    }
    
    /**
     * Gets all pools for a token
     */
    public Set<String> getPoolsForToken(String token) {
        Set<String> tokenPoolSet = tokenPools.get(token);
        return tokenPoolSet != null ? new HashSet<>(tokenPoolSet) : Collections.emptySet();
    }
    
    /**
     * Gets pool statistics
     */
    public PoolStatistics getPoolStatistics(String poolId) {
        return poolStats.get(poolId);
    }
    
    /**
     * Gets registry statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalPools", pools.size());
        stats.put("africanPools", pools.values().stream()
            .filter(LiquidityPool::isAfricanPool)
            .count());
        
        long totalTrades = poolStats.values().stream()
            .mapToLong(PoolStatistics::getTotalTrades)
            .sum();
        stats.put("totalTrades", totalTrades);
        
        double avgSlippage = poolStats.values().stream()
            .mapToDouble(PoolStatistics::getAvgSlippage)
            .average()
            .orElse(0.0);
        stats.put("averageSlippage", String.format("%.4f%%", avgSlippage * 100));
        
        return stats;
    }
}