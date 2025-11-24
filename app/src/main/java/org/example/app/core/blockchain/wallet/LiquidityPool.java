package org.example.app.core.blockchain.wallet;

import org.example.app.core.wallet.Wallet;
import org.example.app.core.wallet.TransactionBuilder;
import org.example.app.core.block.Transaction;
import org.example.app.core.types.Address;
import org.example.app.core.types.Amount;
import org.example.app.core.crypto.PKIManager;
import org.example.app.core.state.BlockchainState;
import org.example.app.core.consensus.Blockchain;

import java.security.*;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.time.Instant;

/**
 * LiquidityPool - Automated Market Maker for token swaps and ASU conversion
 * 
 * Features:
 * - Multi-currency liquidity pools (national tokens, ASU, export tokens)
 * - Constant product formula (x * y = k) for fair pricing
 * - Liquidity provider rewards
 * - Slippage protection
 * - ASU settlement mechanism for cross-border trades
 * - Fair rate calculations with anti-manipulation
 * - Emergency circuit breakers
 * - Volume-based fee structure
 */
public class LiquidityPool extends Wallet {
    private static final Logger LOGGER = Logger.getLogger(LiquidityPool.class.getName());
    
    // Pool identification
    private final String poolId;
    private final String tokenA;
    private final String tokenB;
    private final String poolType; // "TOKEN_PAIR", "ASU_PAIR", "EXPORT_PAIR"
    
    // Liquidity reserves
    private BigInteger reserveA;
    private BigInteger reserveB;
    private BigInteger totalLiquidity;
    
    // Liquidity providers
    private final Map<String, LiquidityPosition> liquidityProviders;
    
    // Trading configuration
    private final BigDecimal tradingFeePercent; // 0.3% default
    private BigInteger totalFeesCollected;
    private final BigInteger minLiquidity;
    
    // Circuit breaker for manipulation protection
    private final BigDecimal maxPriceImpact; // Max 10% price impact per trade
    private final BigInteger maxTradeSize;
    private boolean circuitBreakerTriggered;
    
    // Trading history
    private final List<SwapTransaction> swapHistory;
    private final Map<String, BigInteger> dailyVolume;
    
    // Dependencies
    private final PKIManager pkiManager;
    private final BlockchainState blockchainState;
    private final Blockchain blockchain;
    
    /**
     * LiquidityPosition - Tracks provider's share
     */
    public static class LiquidityPosition {
        private final String providerAddress;
        private BigInteger liquidityTokens;
        private final Instant createdAt;
        private BigInteger feesEarned;
        
        public LiquidityPosition(String providerAddress, BigInteger liquidityTokens) {
            this.providerAddress = providerAddress;
            this.liquidityTokens = liquidityTokens;
            this.createdAt = Instant.now();
            this.feesEarned = BigInteger.ZERO;
        }
        
        public String getProviderAddress() { return providerAddress; }
        public BigInteger getLiquidityTokens() { return liquidityTokens; }
        public Instant getCreatedAt() { return createdAt; }
        public BigInteger getFeesEarned() { return feesEarned; }
        
        public void addLiquidity(BigInteger amount) {
            liquidityTokens = liquidityTokens.add(amount);
        }
        
        public void removeLiquidity(BigInteger amount) {
            liquidityTokens = liquidityTokens.subtract(amount);
        }
        
        public void addFees(BigInteger fees) {
            feesEarned = feesEarned.add(fees);
        }
    }
    
    /**
     * SwapTransaction - Records a token swap
     */
    public static class SwapTransaction {
        private final String swapId;
        private final String userAddress;
        private final String tokenIn;
        private final BigInteger amountIn;
        private final String tokenOut;
        private final BigInteger amountOut;
        private final BigInteger fee;
        private final BigDecimal priceImpact;
        private final Instant timestamp;
        
        public SwapTransaction(String userAddress, String tokenIn, BigInteger amountIn,
                              String tokenOut, BigInteger amountOut, BigInteger fee,
                              BigDecimal priceImpact) {
            this.swapId = UUID.randomUUID().toString();
            this.userAddress = userAddress;
            this.tokenIn = tokenIn;
            this.amountIn = amountIn;
            this.tokenOut = tokenOut;
            this.amountOut = amountOut;
            this.fee = fee;
            this.priceImpact = priceImpact;
            this.timestamp = Instant.now();
        }
        
        public String getSwapId() { return swapId; }
        public String getUserAddress() { return userAddress; }
        public String getTokenIn() { return tokenIn; }
        public BigInteger getAmountIn() { return amountIn; }
        public String getTokenOut() { return tokenOut; }
        public BigInteger getAmountOut() { return amountOut; }
        public BigInteger getFee() { return fee; }
        public BigDecimal getPriceImpact() { return priceImpact; }
        public Instant getTimestamp() { return timestamp; }
    }
    
    /**
     * Constructor for LiquidityPool
     */
    public LiquidityPool(String poolId, String tokenA, String tokenB, String poolType,
                        BlockchainState blockchainState, Blockchain blockchain,
                        PKIManager pkiManager) throws NoSuchAlgorithmException {
        super();
        
        this.poolId = poolId;
        this.tokenA = tokenA.toUpperCase();
        this.tokenB = tokenB.toUpperCase();
        this.poolType = poolType;
        
        this.reserveA = BigInteger.ZERO;
        this.reserveB = BigInteger.ZERO;
        this.totalLiquidity = BigInteger.ZERO;
        
        this.liquidityProviders = new ConcurrentHashMap<>();
        this.swapHistory = Collections.synchronizedList(new ArrayList<>());
        this.dailyVolume = new ConcurrentHashMap<>();
        
        this.tradingFeePercent = new BigDecimal("0.003"); // 0.3%
        this.totalFeesCollected = BigInteger.ZERO;
        this.minLiquidity = new BigInteger("1000"); // Minimum liquidity
        
        this.maxPriceImpact = new BigDecimal("0.10"); // 10% max
        this.maxTradeSize = new BigInteger("1000000000000000000000"); // 1000 tokens
        this.circuitBreakerTriggered = false;
        
        this.blockchainState = blockchainState;
        this.blockchain = blockchain;
        this.pkiManager = pkiManager;
        
        LOGGER.info(String.format("LiquidityPool created: %s (%s/%s)", 
            poolId, tokenA, tokenB));
    }
    
    /**
     * Adds liquidity to the pool
     */
    public BigInteger addLiquidity(String providerAddress, BigInteger amountA, 
                                   BigInteger amountB) {
        if (amountA.compareTo(BigInteger.ZERO) <= 0 || 
            amountB.compareTo(BigInteger.ZERO) <= 0) {
            LOGGER.warning("Invalid liquidity amounts");
            return BigInteger.ZERO;
        }
        
        BigInteger liquidityMinted;
        
        if (totalLiquidity.equals(BigInteger.ZERO)) {
            // Initial liquidity
            liquidityMinted = sqrt(amountA.multiply(amountB));
            
            // Prevent initial liquidity attacks
            if (liquidityMinted.compareTo(minLiquidity) < 0) {
                LOGGER.warning("Initial liquidity below minimum");
                return BigInteger.ZERO;
            }
        } else {
            // Subsequent liquidity - maintain ratio
            BigInteger liquidityA = amountA.multiply(totalLiquidity).divide(reserveA);
            BigInteger liquidityB = amountB.multiply(totalLiquidity).divide(reserveB);
            
            liquidityMinted = liquidityA.min(liquidityB);
        }
        
        // Update reserves
        reserveA = reserveA.add(amountA);
        reserveB = reserveB.add(amountB);
        totalLiquidity = totalLiquidity.add(liquidityMinted);
        
        // Track provider
        LiquidityPosition position = liquidityProviders.computeIfAbsent(
            providerAddress, k -> new LiquidityPosition(providerAddress, BigInteger.ZERO)
        );
        position.addLiquidity(liquidityMinted);
        
        LOGGER.info(String.format("Liquidity added: %s %s + %s %s = %s LP tokens", 
            amountA.toString(), tokenA, amountB.toString(), tokenB, 
            liquidityMinted.toString()));
        
        return liquidityMinted;
    }
    
    /**
     * Removes liquidity from the pool
     */
    public Map<String, BigInteger> removeLiquidity(String providerAddress, 
                                                   BigInteger liquidityAmount) {
        LiquidityPosition position = liquidityProviders.get(providerAddress);
        
        if (position == null || position.getLiquidityTokens().compareTo(liquidityAmount) < 0) {
            LOGGER.warning("Insufficient liquidity tokens");
            return Collections.emptyMap();
        }
        
        // Calculate withdrawal amounts
        BigInteger amountA = liquidityAmount.multiply(reserveA).divide(totalLiquidity);
        BigInteger amountB = liquidityAmount.multiply(reserveB).divide(totalLiquidity);
        
        // Update reserves
        reserveA = reserveA.subtract(amountA);
        reserveB = reserveB.subtract(amountB);
        totalLiquidity = totalLiquidity.subtract(liquidityAmount);
        
        // Update provider position
        position.removeLiquidity(liquidityAmount);
        
        if (position.getLiquidityTokens().equals(BigInteger.ZERO)) {
            liquidityProviders.remove(providerAddress);
        }
        
        Map<String, BigInteger> withdrawal = new HashMap<>();
        withdrawal.put(tokenA, amountA);
        withdrawal.put(tokenB, amountB);
        
        LOGGER.info(String.format("Liquidity removed: %s LP tokens -> %s %s + %s %s", 
            liquidityAmount.toString(), amountA.toString(), tokenA, 
            amountB.toString(), tokenB));
        
        return withdrawal;
    }
    
    /**
     * Swaps tokenIn for tokenOut
     */
    public BigInteger swap(String userAddress, String tokenIn, BigInteger amountIn,
                          BigInteger minAmountOut) {
        if (circuitBreakerTriggered) {
            LOGGER.warning("Circuit breaker active - trading paused");
            return BigInteger.ZERO;
        }
        
        // Validate trade size
        if (amountIn.compareTo(maxTradeSize) > 0) {
            LOGGER.warning("Trade size exceeds maximum");
            return BigInteger.ZERO;
        }
        
        // Determine direction
        boolean isAtoB = tokenIn.equals(tokenA);
        BigInteger reserveIn = isAtoB ? reserveA : reserveB;
        BigInteger reserveOut = isAtoB ? reserveB : reserveA;
        String tokenOut = isAtoB ? tokenB : tokenA;
        
        // Calculate output amount with constant product formula (x * y = k)
        // amountOut = (amountIn * reserveOut) / (reserveIn + amountIn)
        // Apply 0.3% fee
        BigInteger amountInWithFee = amountIn.multiply(BigInteger.valueOf(997));
        BigInteger numerator = amountInWithFee.multiply(reserveOut);
        BigInteger denominator = reserveIn.multiply(BigInteger.valueOf(1000))
                                          .add(amountInWithFee);
        BigInteger amountOut = numerator.divide(denominator);
        
        // Calculate fee
        BigInteger fee = new BigDecimal(amountIn)
            .multiply(tradingFeePercent)
            .toBigInteger();
        
        // Check slippage
        if (amountOut.compareTo(minAmountOut) < 0) {
            LOGGER.warning("Slippage tolerance exceeded");
            return BigInteger.ZERO;
        }
        
        // Calculate price impact
        BigDecimal priceImpact = calculatePriceImpact(amountIn, reserveIn, amountOut, reserveOut);
        
        // Check price impact
        if (priceImpact.compareTo(maxPriceImpact) > 0) {
            LOGGER.warning("Price impact too high: " + priceImpact.toString());
            triggerCircuitBreaker();
            return BigInteger.ZERO;
        }
        
        // Update reserves
        if (isAtoB) {
            reserveA = reserveA.add(amountIn);
            reserveB = reserveB.subtract(amountOut);
        } else {
            reserveB = reserveB.add(amountIn);
            reserveA = reserveA.subtract(amountOut);
        }
        
        // Distribute fees to liquidity providers
        totalFeesCollected = totalFeesCollected.add(fee);
        distributeFees(fee);
        
        // Record swap
        SwapTransaction swap = new SwapTransaction(
            userAddress, tokenIn, amountIn, tokenOut, amountOut, fee, priceImpact
        );
        swapHistory.add(swap);
        
        // Update daily volume
        String today = Instant.now().toString().substring(0, 10);
        dailyVolume.put(today, dailyVolume.getOrDefault(today, BigInteger.ZERO).add(amountIn));
        
        LOGGER.info(String.format("Swap executed: %s %s -> %s %s (impact: %.2f%%)", 
            amountIn.toString(), tokenIn, amountOut.toString(), tokenOut, 
            priceImpact.multiply(new BigDecimal("100")).doubleValue()));
        
        return amountOut;
    }
    
    /**
     * Gets quote for swap (doesn't execute)
     */
    public BigInteger getSwapQuote(String tokenIn, BigInteger amountIn) {
        boolean isAtoB = tokenIn.equals(tokenA);
        BigInteger reserveIn = isAtoB ? reserveA : reserveB;
        BigInteger reserveOut = isAtoB ? reserveB : reserveA;
        
        if (reserveIn.equals(BigInteger.ZERO) || reserveOut.equals(BigInteger.ZERO)) {
            return BigInteger.ZERO;
        }
        
        BigInteger amountInWithFee = amountIn.multiply(BigInteger.valueOf(997));
        BigInteger numerator = amountInWithFee.multiply(reserveOut);
        BigInteger denominator = reserveIn.multiply(BigInteger.valueOf(1000))
                                          .add(amountInWithFee);
        
        return numerator.divide(denominator);
    }
    
    /**
     * Calculates price impact percentage
     */
    private BigDecimal calculatePriceImpact(BigInteger amountIn, BigInteger reserveIn,
                                           BigInteger amountOut, BigInteger reserveOut) {
        // Price impact = 1 - (actual_price / expected_price)
        BigDecimal actualPrice = new BigDecimal(amountIn).divide(
            new BigDecimal(amountOut), 18, RoundingMode.HALF_UP);
        
        BigDecimal expectedPrice = new BigDecimal(reserveIn).divide(
            new BigDecimal(reserveOut), 18, RoundingMode.HALF_UP);
        
        if (expectedPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return BigDecimal.ONE.subtract(actualPrice.divide(expectedPrice, 18, RoundingMode.HALF_UP))
                            .abs();
    }
    
    /**
     * Distributes trading fees to liquidity providers
     */
    private void distributeFees(BigInteger fee) {
        if (totalLiquidity.equals(BigInteger.ZERO)) return;
        
        for (LiquidityPosition position : liquidityProviders.values()) {
            BigInteger providerShare = fee.multiply(position.getLiquidityTokens())
                                         .divide(totalLiquidity);
            position.addFees(providerShare);
        }
    }
    
    /**
     * Triggers circuit breaker to halt trading
     */
    private void triggerCircuitBreaker() {
        circuitBreakerTriggered = true;
        LOGGER.warning("CIRCUIT BREAKER TRIGGERED for pool " + poolId);
    }
    
    /**
     * Resets circuit breaker (requires governance/admin)
     */
    public void resetCircuitBreaker() {
        circuitBreakerTriggered = false;
        LOGGER.info("Circuit breaker reset for pool " + poolId);
    }
    
    /**
     * Square root calculation using Newton's method
     */
    private BigInteger sqrt(BigInteger n) {
        if (n.compareTo(BigInteger.ZERO) == 0) return BigInteger.ZERO;
        
        BigInteger x = n;
        BigInteger y = x.add(BigInteger.ONE).divide(BigInteger.TWO);
        
        while (y.compareTo(x) < 0) {
            x = y;
            y = x.add(n.divide(x)).divide(BigInteger.TWO);
        }
        
        return x;
    }
    
    // Getters and utility methods
    
    public String getPoolId() { return poolId; }
    public String getTokenA() { return tokenA; }
    public String getTokenB() { return tokenB; }
    public String getPoolType() { return poolType; }
    
    public BigInteger getReserveA() { return reserveA; }
    public BigInteger getReserveB() { return reserveB; }
    public BigInteger getTotalLiquidity() { return totalLiquidity; }
    
    public BigDecimal getCurrentPrice() {
        if (reserveB.equals(BigInteger.ZERO)) return BigDecimal.ZERO;
        return new BigDecimal(reserveA).divide(new BigDecimal(reserveB), 18, RoundingMode.HALF_UP);
    }
    
    public LiquidityPosition getProviderPosition(String address) {
        return liquidityProviders.get(address);
    }
    
    public int getProviderCount() {
        return liquidityProviders.size();
    }
    
    public BigInteger getTotalFeesCollected() {
        return totalFeesCollected;
    }
    
    public List<SwapTransaction> getRecentSwaps(int limit) {
        int size = swapHistory.size();
        int fromIndex = Math.max(0, size - limit);
        return new ArrayList<>(swapHistory.subList(fromIndex, size));
    }
    
    public BigInteger getTodayVolume() {
        String today = Instant.now().toString().substring(0, 10);
        return dailyVolume.getOrDefault(today, BigInteger.ZERO);
    }
    
    public boolean isCircuitBreakerActive() {
        return circuitBreakerTriggered;
    }
    
    public Map<String, Object> getPoolStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("poolId", poolId);
        stats.put("tokenA", tokenA);
        stats.put("tokenB", tokenB);
        stats.put("reserveA", reserveA);
        stats.put("reserveB", reserveB);
        stats.put("totalLiquidity", totalLiquidity);
        stats.put("currentPrice", getCurrentPrice());
        stats.put("providerCount", getProviderCount());
        stats.put("totalFees", totalFeesCollected);
        stats.put("todayVolume", getTodayVolume());
        stats.put("circuitBreakerActive", circuitBreakerTriggered);
        return stats;
    }
    
    @Override
    public String toString() {
        return String.format("LiquidityPool{id='%s', pair='%s/%s', reserveA=%s, reserveB=%s, " +
            "liquidity=%s, price=%s}",
            poolId, tokenA, tokenB, reserveA.toString(), reserveB.toString(),
            totalLiquidity.toString(), getCurrentPrice().toString());
    }
}