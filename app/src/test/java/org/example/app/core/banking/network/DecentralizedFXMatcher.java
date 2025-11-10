package org.example.app.core.banking.network;

import org.example.app.core.banking.tokenization.ExchangeRateOracle;
import org.example.app.core.banking.tokenization.AntiManipulationController;
import org.example.app.core.blockchain.transaction.CurrencyExchangeTransaction;
import org.example.app.core.blockchain.wallet.LiquidityPool;
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
 * DecentralizedFXMatcher - Government-resistant FX matching engine with African prioritization
 * 
 * Provides fair exchange matching using multiple sources, prioritizes African exchanges,
 * and implements sophisticated anti-manipulation algorithms to prevent rate manipulation.
 */
public class DecentralizedFXMatcher {
    private static final Logger LOGGER = Logger.getLogger(DecentralizedFXMatcher.class.getName());
    
    // Fair weighting algorithm constants (African-focused)
    private static final double AFRICAN_EXCHANGE_WEIGHT = 0.35; // 35% weight for African exchanges
    private static final double VOLUME_WEIGHT = 0.25;           // 25% - Trading volume
    private static final double LIQUIDITY_WEIGHT = 0.20;       // 20% - Market liquidity
    private static final double STABILITY_WEIGHT = 0.15;       // 15% - Price stability
    private static final double DIVERSITY_WEIGHT = 0.05;       // 5% - Source diversity
    
    // Anti-manipulation settings
    private static final int MIN_EXCHANGE_SOURCES = 5;          // Minimum sources for matching
    private static final double MAX_RATE_DEVIATION = 0.12;     // 12% max deviation from weighted median
    private static final long RATE_VALIDITY_WINDOW = 300000;   // 5 minutes rate validity
    private static final int MAX_SINGLE_SOURCE_WEIGHT = 25;    // Max 25% weight for single source
    
    // Order matching parameters
    private static final BigDecimal MIN_ORDER_SIZE = new BigDecimal("100");     // $100 minimum
    private static final BigDecimal MAX_ORDER_SIZE = new BigDecimal("1000000"); // $1M maximum
    private static final long ORDER_TIMEOUT = 1800000;         // 30 minutes order timeout
    private static final double SLIPPAGE_TOLERANCE = 0.005;    // 0.5% default slippage tolerance
    
    // Core components
    private final ExchangeRateOracle exchangeRateOracle;
    private final AntiManipulationController antiManipulationController;
    private final LiquidityPoolRegistry liquidityPoolRegistry;
    private final PKIManager pkiManager;
    private final NetworkConfig networkConfig;
    
    // Order management
    private final Map<String, PendingOrder> pendingOrders = new ConcurrentHashMap<>();
    private final Map<String, List<String>> currencyOrderMapping = new ConcurrentHashMap<>();
    private final PriorityQueue<PendingOrder> buyOrderQueue = new PriorityQueue<>(
        (o1, o2) -> o2.getPrice().compareTo(o1.getPrice()) // Highest price first for buy orders
    );
    private final PriorityQueue<PendingOrder> sellOrderQueue = new PriorityQueue<>(
        (o1, o2) -> o1.getPrice().compareTo(o2.getPrice()) // Lowest price first for sell orders
    );
    
    // Rate aggregation and matching
    private final Map<String, WeightedExchangeRate> currentRates = new ConcurrentHashMap<>();
    private final Map<String, List<ExchangeRateData>> rateHistory = new ConcurrentHashMap<>();
    private final Map<String, AfricanExchangeBonus> africanBonuses = new ConcurrentHashMap<>();
    
    // Execution tracking
    private final Map<String, MatchedTrade> executedTrades = new ConcurrentHashMap<>();
    private final Map<String, FailedMatch> failedMatches = new ConcurrentHashMap<>();
    private final AtomicLong totalMatches = new AtomicLong(0);
    private final AtomicLong africanMatches = new AtomicLong(0);
    
    // Concurrency and scheduling
    private final ReentrantReadWriteLock orderLock = new ReentrantReadWriteLock();
    private final ScheduledExecutorService matchingScheduler = Executors.newScheduledThreadPool(5);
    private final ExecutorService orderProcessor = Executors.newFixedThreadPool(15);
    private final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Pending order data structure
     */
    public static class PendingOrder {
        private final String orderId;
        private final OrderType orderType;
        private final String baseCurrency;
        private final String quoteCurrency;
        private final BigDecimal amount;
        private final BigDecimal price;
        private final Address userAddress;
        private final long timestamp;
        private final long expirationTime;
        private final double maxSlippage;
        private final boolean prioritizeAfrican;
        private volatile OrderStatus status;
        private volatile BigDecimal filledAmount;
        
        public PendingOrder(String orderId, OrderType orderType, String baseCurrency, 
                          String quoteCurrency, BigDecimal amount, BigDecimal price, 
                          Address userAddress, double maxSlippage, boolean prioritizeAfrican) {
            this.orderId = orderId;
            this.orderType = orderType;
            this.baseCurrency = baseCurrency;
            this.quoteCurrency = quoteCurrency;
            this.amount = amount;
            this.price = price;
            this.userAddress = userAddress;
            this.timestamp = System.currentTimeMillis();
            this.expirationTime = timestamp + ORDER_TIMEOUT;
            this.maxSlippage = maxSlippage;
            this.prioritizeAfrican = prioritizeAfrican;
            this.status = OrderStatus.PENDING;
            this.filledAmount = BigDecimal.ZERO;
        }
        
        // Getters
        public String getOrderId() { return orderId; }
        public OrderType getOrderType() { return orderType; }
        public String getBaseCurrency() { return baseCurrency; }
        public String getQuoteCurrency() { return quoteCurrency; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getPrice() { return price; }
        public Address getUserAddress() { return userAddress; }
        public long getTimestamp() { return timestamp; }
        public long getExpirationTime() { return expirationTime; }
        public double getMaxSlippage() { return maxSlippage; }
        public boolean isPrioritizeAfrican() { return prioritizeAfrican; }
        public OrderStatus getStatus() { return status; }
        public BigDecimal getFilledAmount() { return filledAmount; }
        
        public void setStatus(OrderStatus status) { this.status = status; }
        public void setFilledAmount(BigDecimal filledAmount) { this.filledAmount = filledAmount; }
        
        public BigDecimal getRemainingAmount() { 
            return amount.subtract(filledAmount); 
        }
        
        public boolean isExpired() { 
            return System.currentTimeMillis() > expirationTime; 
        }
    }
    
    /**
     * Order types
     */
    public enum OrderType {
        BUY,
        SELL,
        MARKET,
        LIMIT
    }
    
    /**
     * Order status
     */
    public enum OrderStatus {
        PENDING,
        PARTIALLY_FILLED,
        FILLED,
        CANCELLED,
        EXPIRED,
        FAILED
    }
    
    /**
     * Weighted exchange rate with African prioritization
     */
    public static class WeightedExchangeRate {
        private final String currencyPair;
        private final BigDecimal weightedRate;
        private final BigDecimal africanAdjustedRate;
        private final Map<String, BigDecimal> sourceRates;
        private final Map<String, Double> sourceWeights;
        private final long calculationTimestamp;
        private final double confidenceScore;
        private final int africanSourceCount;
        
        public WeightedExchangeRate(String currencyPair, BigDecimal weightedRate, 
                                  BigDecimal africanAdjustedRate, Map<String, BigDecimal> sourceRates,
                                  Map<String, Double> sourceWeights, double confidenceScore,
                                  int africanSourceCount) {
            this.currencyPair = currencyPair;
            this.weightedRate = weightedRate;
            this.africanAdjustedRate = africanAdjustedRate;
            this.sourceRates = new HashMap<>(sourceRates);
            this.sourceWeights = new HashMap<>(sourceWeights);
            this.calculationTimestamp = System.currentTimeMillis();
            this.confidenceScore = confidenceScore;
            this.africanSourceCount = africanSourceCount;
        }
        
        // Getters
        public String getCurrencyPair() { return currencyPair; }
        public BigDecimal getWeightedRate() { return weightedRate; }
        public BigDecimal getAfricanAdjustedRate() { return africanAdjustedRate; }
        public Map<String, BigDecimal> getSourceRates() { return new HashMap<>(sourceRates); }
        public Map<String, Double> getSourceWeights() { return new HashMap<>(sourceWeights); }
        public long getCalculationTimestamp() { return calculationTimestamp; }
        public double getConfidenceScore() { return confidenceScore; }
        public int getAfricanSourceCount() { return africanSourceCount; }
        
        public boolean isValid() {
            return (System.currentTimeMillis() - calculationTimestamp) < RATE_VALIDITY_WINDOW;
        }
    }
    
    /**
     * African exchange bonus calculation
     */
    public static class AfricanExchangeBonus {
        private final String currencyPair;
        private final double bonusMultiplier;
        private final int africanExchangeCount;
        private final double localAdoptionScore;
        private final long lastUpdated;
        
        public AfricanExchangeBonus(String currencyPair, double bonusMultiplier, 
                                  int africanExchangeCount, double localAdoptionScore) {
            this.currencyPair = currencyPair;
            this.bonusMultiplier = bonusMultiplier;
            this.africanExchangeCount = africanExchangeCount;
            this.localAdoptionScore = localAdoptionScore;
            this.lastUpdated = System.currentTimeMillis();
        }
        
        // Getters
        public String getCurrencyPair() { return currencyPair; }
        public double getBonusMultiplier() { return bonusMultiplier; }
        public int getAfricanExchangeCount() { return africanExchangeCount; }
        public double getLocalAdoptionScore() { return localAdoptionScore; }
        public long getLastUpdated() { return lastUpdated; }
    }
    
    /**
     * Exchange rate data point
     */
    public static class ExchangeRateData {
        private final String exchangeName;
        private final String currencyPair;
        private final BigDecimal rate;
        private final BigDecimal volume;
        private final BigDecimal spread;
        private final boolean isAfricanExchange;
        private final long timestamp;
        private final double reliability;
        
        public ExchangeRateData(String exchangeName, String currencyPair, BigDecimal rate,
                              BigDecimal volume, BigDecimal spread, boolean isAfricanExchange,
                              double reliability) {
            this.exchangeName = exchangeName;
            this.currencyPair = currencyPair;
            this.rate = rate;
            this.volume = volume;
            this.spread = spread;
            this.isAfricanExchange = isAfricanExchange;
            this.timestamp = System.currentTimeMillis();
            this.reliability = reliability;
        }
        
        // Getters
        public String getExchangeName() { return exchangeName; }
        public String getCurrencyPair() { return currencyPair; }
        public BigDecimal getRate() { return rate; }
        public BigDecimal getVolume() { return volume; }
        public BigDecimal getSpread() { return spread; }
        public boolean isAfricanExchange() { return isAfricanExchange; }
        public long getTimestamp() { return timestamp; }
        public double getReliability() { return reliability; }
    }
    
    /**
     * Matched trade result
     */
    public static class MatchedTrade {
        private final String tradeId;
        private final String buyOrderId;
        private final String sellOrderId;
        private final String currencyPair;
        private final BigDecimal amount;
        private final BigDecimal executionPrice;
        private final long executionTimestamp;
        private final boolean usedAfricanRates;
        private final BigDecimal totalFees;
        private final List<String> liquiditySourceIds;
        
        public MatchedTrade(String tradeId, String buyOrderId, String sellOrderId,
                          String currencyPair, BigDecimal amount, BigDecimal executionPrice,
                          boolean usedAfricanRates, BigDecimal totalFees,
                          List<String> liquiditySourceIds) {
            this.tradeId = tradeId;
            this.buyOrderId = buyOrderId;
            this.sellOrderId = sellOrderId;
            this.currencyPair = currencyPair;
            this.amount = amount;
            this.executionPrice = executionPrice;
            this.executionTimestamp = System.currentTimeMillis();
            this.usedAfricanRates = usedAfricanRates;
            this.totalFees = totalFees;
            this.liquiditySourceIds = new ArrayList<>(liquiditySourceIds);
        }
        
        // Getters
        public String getTradeId() { return tradeId; }
        public String getBuyOrderId() { return buyOrderId; }
        public String getSellOrderId() { return sellOrderId; }
        public String getCurrencyPair() { return currencyPair; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getExecutionPrice() { return executionPrice; }
        public long getExecutionTimestamp() { return executionTimestamp; }
        public boolean isUsedAfricanRates() { return usedAfricanRates; }
        public BigDecimal getTotalFees() { return totalFees; }
        public List<String> getLiquiditySourceIds() { return new ArrayList<>(liquiditySourceIds); }
    }
    
    /**
     * Failed match tracking
     */
    public static class FailedMatch {
        private final String orderId;
        private final String reason;
        private final long timestamp;
        private final String currencyPair;
        private final BigDecimal attemptedAmount;
        
        public FailedMatch(String orderId, String reason, String currencyPair, 
                         BigDecimal attemptedAmount) {
            this.orderId = orderId;
            this.reason = reason;
            this.timestamp = System.currentTimeMillis();
            this.currencyPair = currencyPair;
            this.attemptedAmount = attemptedAmount;
        }
        
        // Getters
        public String getOrderId() { return orderId; }
        public String getReason() { return reason; }
        public long getTimestamp() { return timestamp; }
        public String getCurrencyPair() { return currencyPair; }
        public BigDecimal getAttemptedAmount() { return attemptedAmount; }
    }
    
    /**
     * Constructor
     */
    public DecentralizedFXMatcher(ExchangeRateOracle exchangeRateOracle,
                                AntiManipulationController antiManipulationController,
                                LiquidityPoolRegistry liquidityPoolRegistry,
                                PKIManager pkiManager,
                                NetworkConfig networkConfig) {
        this.exchangeRateOracle = exchangeRateOracle;
        this.antiManipulationController = antiManipulationController;
        this.liquidityPoolRegistry = liquidityPoolRegistry;
        this.pkiManager = pkiManager;
        this.networkConfig = networkConfig;
        
        // Start matching engine
        startMatchingEngine();
        
        LOGGER.info("DecentralizedFXMatcher initialized with African prioritization");
    }
    
    /**
     * Submit a new order for matching
     */
    public CompletableFuture<String> submitOrder(OrderType orderType, String baseCurrency, 
                                               String quoteCurrency, BigDecimal amount, 
                                               BigDecimal price, Address userAddress,
                                               double maxSlippage, boolean prioritizeAfrican) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate order parameters
                validateOrderParameters(orderType, baseCurrency, quoteCurrency, amount, price, maxSlippage);
                
                // Generate order ID
                String orderId = generateOrderId(userAddress, baseCurrency, quoteCurrency);
                
                // Create pending order
                PendingOrder order = new PendingOrder(
                    orderId, orderType, baseCurrency, quoteCurrency, amount, price,
                    userAddress, maxSlippage, prioritizeAfrican
                );
                
                // Add to order management
                orderLock.writeLock().lock();
                try {
                    pendingOrders.put(orderId, order);
                    String currencyPair = baseCurrency + "/" + quoteCurrency;
                    currencyOrderMapping.computeIfAbsent(currencyPair, k -> new ArrayList<>()).add(orderId);
                    
                    // Add to appropriate queue for matching
                    if (orderType == OrderType.BUY) {
                        buyOrderQueue.offer(order);
                    } else {
                        sellOrderQueue.offer(order);
                    }
                    
                    LOGGER.info("Order submitted: " + orderId + " for " + amount + " " + baseCurrency);
                    EventLog.log("ORDER_SUBMITTED", "Order " + orderId + " submitted for " + currencyPair);
                    
                    return orderId;
                    
                } finally {
                    orderLock.writeLock().unlock();
                }
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to submit order", e);
                throw new RuntimeException("Order submission failed: " + e.getMessage(), e);
            }
        }, orderProcessor);
    }
    
    /**
     * Get current weighted exchange rate
     */
    public CompletableFuture<WeightedExchangeRate> getWeightedRate(String baseCurrency, 
                                                                 String quoteCurrency,
                                                                 boolean prioritizeAfrican) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String currencyPair = baseCurrency + "/" + quoteCurrency;
                
                // Check cached rate first
                WeightedExchangeRate cachedRate = currentRates.get(currencyPair);
                if (cachedRate != null && cachedRate.isValid()) {
                    return prioritizeAfrican ? 
                        createAfricanAdjustedRate(cachedRate) : cachedRate;
                }
                
                // Get fresh rates from oracle and other sources
                List<ExchangeRateData> rateData = collectExchangeRates(currencyPair);
                
                if (rateData.size() < MIN_EXCHANGE_SOURCES) {
                    throw new IllegalStateException("Insufficient exchange sources for " + currencyPair);
                }
                
                // Calculate weighted rate
                WeightedExchangeRate weightedRate = calculateWeightedRate(currencyPair, rateData, prioritizeAfrican);
                
                // Validate against manipulation
                if (!antiManipulationController.validateRate(currencyPair, weightedRate.getWeightedRate())) {
                    throw new IllegalStateException("Rate failed anti-manipulation validation");
                }
                
                // Cache the rate
                currentRates.put(currencyPair, weightedRate);
                
                // Update rate history
                rateHistory.computeIfAbsent(currencyPair, k -> new ArrayList<>()).addAll(rateData);
                
                return weightedRate;
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to get weighted rate for " + baseCurrency + "/" + quoteCurrency, e);
                throw new RuntimeException("Rate calculation failed: " + e.getMessage(), e);
            }
        }, orderProcessor);
    }
    
    /**
     * Cancel an order
     */
    public CompletableFuture<Boolean> cancelOrder(String orderId, Address userAddress) {
        return CompletableFuture.supplyAsync(() -> {
            orderLock.writeLock().lock();
            try {
                PendingOrder order = pendingOrders.get(orderId);
                
                if (order == null) {
                    LOGGER.warning("Attempted to cancel non-existent order: " + orderId);
                    return false;
                }
                
                if (!order.getUserAddress().equals(userAddress)) {
                    LOGGER.warning("Unauthorized order cancellation attempt: " + orderId);
                    return false;
                }
                
                if (order.getStatus() == OrderStatus.FILLED || 
                    order.getStatus() == OrderStatus.CANCELLED) {
                    return false;
                }
                
                order.setStatus(OrderStatus.CANCELLED);
                
                // Remove from queues
                buyOrderQueue.remove(order);
                sellOrderQueue.remove(order);
                
                LOGGER.info("Order cancelled: " + orderId);
                EventLog.log("ORDER_CANCELLED", "Order " + orderId + " cancelled by user");
                
                return true;
                
            } finally {
                orderLock.writeLock().unlock();
            }
        }, orderProcessor);
    }
    
    /**
     * Get matching statistics
     */
    public MatchingStatistics getMatchingStatistics() {
        return new MatchingStatistics(
            totalMatches.get(),
            africanMatches.get(),
            pendingOrders.size(),
            executedTrades.size(),
            failedMatches.size(),
            currentRates.size()
        );
    }
    
    /**
     * Matching statistics data structure
     */
    public static class MatchingStatistics {
        private final long totalMatches;
        private final long africanMatches;
        private final long pendingOrders;
        private final long executedTrades;
        private final long failedMatches;
        private final long activeCurrencyPairs;
        
        public MatchingStatistics(long totalMatches, long africanMatches, long pendingOrders,
                                long executedTrades, long failedMatches, long activeCurrencyPairs) {
            this.totalMatches = totalMatches;
            this.africanMatches = africanMatches;
            this.pendingOrders = pendingOrders;
            this.executedTrades = executedTrades;
            this.failedMatches = failedMatches;
            this.activeCurrencyPairs = activeCurrencyPairs;
        }
        
        // Getters
        public long getTotalMatches() { return totalMatches; }
        public long getAfricanMatches() { return africanMatches; }
        public long getPendingOrders() { return pendingOrders; }
        public long getExecutedTrades() { return executedTrades; }
        public long getFailedMatches() { return failedMatches; }
        public long getActiveCurrencyPairs() { return activeCurrencyPairs; }
    }
    
    /**
     * Start the matching engine
     */
    private void startMatchingEngine() {
        // Order matching scheduler
        matchingScheduler.scheduleAtFixedRate(
            this::processOrderMatching,
            5000, // 5 second delay
            1000, // 1 second interval
            TimeUnit.MILLISECONDS
        );
        
        // Order cleanup scheduler
        matchingScheduler.scheduleAtFixedRate(
            this::cleanupExpiredOrders,
            30000, // 30 second delay
            30000, // 30 second interval
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * Process order matching
     */
    private void processOrderMatching() {
        try {
            orderLock.writeLock().lock();
            
            // Match buy and sell orders
            while (!buyOrderQueue.isEmpty() && !sellOrderQueue.isEmpty()) {
                PendingOrder buyOrder = buyOrderQueue.peek();
                PendingOrder sellOrder = sellOrderQueue.peek();
                
                if (buyOrder == null || sellOrder == null) break;
                
                // Check if orders can be matched
                if (canMatchOrders(buyOrder, sellOrder)) {
                    // Remove from queues
                    buyOrderQueue.poll();
                    sellOrderQueue.poll();
                    
                    // Execute the match
                    executeOrderMatch(buyOrder, sellOrder);
                } else {
                    // No more matches possible
                    break;
                }
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error during order matching", e);
        } finally {
            orderLock.writeLock().unlock();
        }
    }
    
    /**
     * Check if two orders can be matched
     */
    private boolean canMatchOrders(PendingOrder buyOrder, PendingOrder sellOrder) {
        // Basic compatibility checks
        if (!buyOrder.getBaseCurrency().equals(sellOrder.getBaseCurrency()) ||
            !buyOrder.getQuoteCurrency().equals(sellOrder.getQuoteCurrency())) {
            return false;
        }
        
        // Price compatibility check
        if (buyOrder.getPrice().compareTo(sellOrder.getPrice()) < 0) {
            return false;
        }
        
        // Status checks
        if (buyOrder.getStatus() != OrderStatus.PENDING || 
            sellOrder.getStatus() != OrderStatus.PENDING) {
            return false;
        }
        
        // Expiration checks
        if (buyOrder.isExpired() || sellOrder.isExpired()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Execute order match
     */
    private void executeOrderMatch(PendingOrder buyOrder, PendingOrder sellOrder) {
        try {
            String currencyPair = buyOrder.getBaseCurrency() + "/" + buyOrder.getQuoteCurrency();
            
            // Calculate execution parameters
            BigDecimal matchAmount = buyOrder.getRemainingAmount().min(sellOrder.getRemainingAmount());
            BigDecimal executionPrice = calculateExecutionPrice(buyOrder, sellOrder);
            
            // Get optimal liquidity pools
            List<LiquidityPoolRegistry.RegisteredPool> pools = liquidityPoolRegistry
                .getOptimalPoolsForPair(buyOrder.getBaseCurrency(), buyOrder.getQuoteCurrency(), 5);
            
            if (pools.isEmpty()) {
                recordFailedMatch(buyOrder.getOrderId(), "No liquidity pools available", 
                                currencyPair, matchAmount);
                return;
            }
            
            // Execute through liquidity pools
            boolean africanPrioritized = buyOrder.isPrioritizeAfrican() || sellOrder.isPrioritizeAfrican();
            MatchedTrade trade = executeTradeWithPools(buyOrder, sellOrder, matchAmount, 
                                                     executionPrice, pools, africanPrioritized);
            
            if (trade != null) {
                // Update order statuses
                updateOrderAfterMatch(buyOrder, matchAmount);
                updateOrderAfterMatch(sellOrder, matchAmount);
                
                // Record successful trade
                executedTrades.put(trade.getTradeId(), trade);
                totalMatches.incrementAndGet();
                
                if (africanPrioritized || trade.isUsedAfricanRates()) {
                    africanMatches.incrementAndGet();
                }
                
                LOGGER.info("Successfully matched orders: " + buyOrder.getOrderId() + 
                          " <-> " + sellOrder.getOrderId() + " for " + matchAmount);
                
                EventLog.log("ORDERS_MATCHED", "Orders matched for " + matchAmount + " " + 
                           buyOrder.getBaseCurrency());
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to execute order match", e);
            recordFailedMatch(buyOrder.getOrderId(), "Execution error: " + e.getMessage(),
                            buyOrder.getBaseCurrency() + "/" + buyOrder.getQuoteCurrency(),
                            buyOrder.getRemainingAmount());
        }
    }
    
    /**
     * Calculate execution price for matched orders
     */
    private BigDecimal calculateExecutionPrice(PendingOrder buyOrder, PendingOrder sellOrder) {
        // Use midpoint of buy and sell prices
        return buyOrder.getPrice().add(sellOrder.getPrice())
            .divide(new BigDecimal("2"), MathContext.DECIMAL128);
    }
    
    /**
     * Execute trade with liquidity pools
     */
    private MatchedTrade executeTradeWithPools(PendingOrder buyOrder, PendingOrder sellOrder,
                                             BigDecimal amount, BigDecimal price,
                                             List<LiquidityPoolRegistry.RegisteredPool> pools,
                                             boolean prioritizeAfrican) {
        try {
            String tradeId = generateTradeId(buyOrder.getOrderId(), sellOrder.getOrderId());
            List<String> poolIds = pools.stream()
                .map(LiquidityPoolRegistry.RegisteredPool::getPoolId)
                .collect(Collectors.toList());
            
            // Calculate fees (0.1% base fee, 0.05% discount for African prioritization)
            BigDecimal feeRate = prioritizeAfrican ? 
                new BigDecimal("0.0005") : new BigDecimal("0.001");
            BigDecimal totalFees = amount.multiply(price).multiply(feeRate);
            
            return new MatchedTrade(
                tradeId,
                buyOrder.getOrderId(),
                sellOrder.getOrderId(),
                buyOrder.getBaseCurrency() + "/" + buyOrder.getQuoteCurrency(),
                amount,
                price,
                prioritizeAfrican,
                totalFees,
                poolIds
            );
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to execute trade with pools", e);
            return null;
        }
    }
    
    /**
     * Update order after partial or full match
     */
    private void updateOrderAfterMatch(PendingOrder order, BigDecimal matchedAmount) {
        order.setFilledAmount(order.getFilledAmount().add(matchedAmount));
        
        if (order.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            order.setStatus(OrderStatus.FILLED);
        } else {
            order.setStatus(OrderStatus.PARTIALLY_FILLED);
            // Re-add to queue if partially filled
            if (order.getOrderType() == OrderType.BUY) {
                buyOrderQueue.offer(order);
            } else {
                sellOrderQueue.offer(order);
            }
        }
    }
    
    /**
     * Record failed match
     */
    private void recordFailedMatch(String orderId, String reason, String currencyPair, 
                                 BigDecimal amount) {
        FailedMatch failedMatch = new FailedMatch(orderId, reason, currencyPair, amount);
        failedMatches.put(orderId + "_" + System.currentTimeMillis(), failedMatch);
        
        LOGGER.warning("Order match failed: " + orderId + " - " + reason);
    }
    
    /**
     * Cleanup expired orders
     */
    private void cleanupExpiredOrders() {
        try {
            orderLock.writeLock().lock();
            
            List<String> expiredOrderIds = new ArrayList<>();
            
            for (PendingOrder order : pendingOrders.values()) {
                if (order.isExpired() && order.getStatus() == OrderStatus.PENDING) {
                    order.setStatus(OrderStatus.EXPIRED);
                    expiredOrderIds.add(order.getOrderId());
                    
                    // Remove from queues
                    buyOrderQueue.remove(order);
                    sellOrderQueue.remove(order);
                }
            }
            
            if (!expiredOrderIds.isEmpty()) {
                LOGGER.info("Cleaned up " + expiredOrderIds.size() + " expired orders");
            }
            
        } finally {
            orderLock.writeLock().unlock();
        }
    }
    
    /**
     * Collect exchange rates from multiple sources
     */
    private List<ExchangeRateData> collectExchangeRates(String currencyPair) {
        // This would integrate with the ExchangeRateOracle
        // For now, return a mock implementation
        List<ExchangeRateData> rates = new ArrayList<>();
        
        // This should be replaced with actual oracle integration
        // rates.addAll(exchangeRateOracle.getRatesForPair(currencyPair));
        
        return rates;
    }
    
    /**
     * Calculate weighted rate with African prioritization
     */
    private WeightedExchangeRate calculateWeightedRate(String currencyPair, 
                                                     List<ExchangeRateData> rateData,
                                                     boolean prioritizeAfrican) {
        Map<String, BigDecimal> sourceRates = new HashMap<>();
        Map<String, Double> sourceWeights = new HashMap<>();
        
        double totalWeight = 0.0;
        BigDecimal weightedSum = BigDecimal.ZERO;
        int africanSourceCount = 0;
        
        for (ExchangeRateData data : rateData) {
            double weight = calculateSourceWeight(data, prioritizeAfrican);
            
            sourceRates.put(data.getExchangeName(), data.getRate());
            sourceWeights.put(data.getExchangeName(), weight);
            
            weightedSum = weightedSum.add(data.getRate().multiply(new BigDecimal(weight)));
            totalWeight += weight;
            
            if (data.isAfricanExchange()) {
                africanSourceCount++;
            }
        }
        
        BigDecimal weightedRate = weightedSum.divide(new BigDecimal(totalWeight), MathContext.DECIMAL128);
        
        // Apply African adjustment if prioritized
        BigDecimal africanAdjustedRate = prioritizeAfrican && africanSourceCount > 0 ? 
            applyAfricanAdjustment(weightedRate, rateData) : weightedRate;
        
        // Calculate confidence score
        double confidenceScore = calculateConfidenceScore(rateData, weightedRate);
        
        return new WeightedExchangeRate(
            currencyPair,
            weightedRate,
            africanAdjustedRate,
            sourceRates,
            sourceWeights,
            confidenceScore,
            africanSourceCount
        );
    }
    
    /**
     * Calculate source weight for rate aggregation
     */
    private double calculateSourceWeight(ExchangeRateData data, boolean prioritizeAfrican) {
        double baseWeight = 1.0;
        
        // African exchange bonus
        if (data.isAfricanExchange() && prioritizeAfrican) {
            baseWeight *= 1.5; // 50% bonus for African exchanges
        }
        
        // Reliability weight
        baseWeight *= data.getReliability();
        
        // Volume weight (logarithmic scale)
        if (data.getVolume().compareTo(BigDecimal.ZERO) > 0) {
            double volumeWeight = Math.log10(data.getVolume().doubleValue() + 1) / 10.0;
            baseWeight *= Math.min(2.0, 1.0 + volumeWeight);
        }
        
        // Spread penalty (tighter spreads get higher weight)
        if (data.getSpread().compareTo(BigDecimal.ZERO) > 0) {
            double spreadPenalty = Math.max(0.5, 1.0 - data.getSpread().doubleValue());
            baseWeight *= spreadPenalty;
        }
        
        return Math.min(baseWeight, MAX_SINGLE_SOURCE_WEIGHT / 100.0);
    }
    
    /**
     * Apply African adjustment to weighted rate
     */
    private BigDecimal applyAfricanAdjustment(BigDecimal baseRate, List<ExchangeRateData> rateData) {
        // Calculate average African exchange rate
        List<ExchangeRateData> africanRates = rateData.stream()
            .filter(ExchangeRateData::isAfricanExchange)
            .collect(Collectors.toList());
        
        if (africanRates.isEmpty()) {
            return baseRate;
        }
        
        BigDecimal africanAverage = africanRates.stream()
            .map(ExchangeRateData::getRate)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(new BigDecimal(africanRates.size()), MathContext.DECIMAL128);
        
        // Blend with base rate (70% base, 30% African)
        return baseRate.multiply(new BigDecimal("0.7"))
            .add(africanAverage.multiply(new BigDecimal("0.3")));
    }
    
    /**
     * Calculate confidence score for rate
     */
    private double calculateConfidenceScore(List<ExchangeRateData> rateData, BigDecimal weightedRate) {
        if (rateData.size() < MIN_EXCHANGE_SOURCES) {
            return 0.0;
        }
        
        // Calculate standard deviation
        double mean = weightedRate.doubleValue();
        double variance = rateData.stream()
            .mapToDouble(data -> {
                double diff = data.getRate().doubleValue() - mean;
                return diff * diff;
            })
            .average()
            .orElse(0.0);
        
        double standardDeviation = Math.sqrt(variance);
        double coefficientOfVariation = standardDeviation / mean;
        
        // Higher confidence for lower variation
        return Math.max(0.0, 1.0 - (coefficientOfVariation * 10));
    }
    
    /**
     * Create African-adjusted rate from cached rate
     */
    private WeightedExchangeRate createAfricanAdjustedRate(WeightedExchangeRate baseRate) {
        if (baseRate.getAfricanSourceCount() > 0) {
            return baseRate; // Already has African adjustment
        }
        
        // Apply African bonus if available
        AfricanExchangeBonus bonus = africanBonuses.get(baseRate.getCurrencyPair());
        if (bonus != null) {
            BigDecimal adjustedRate = baseRate.getWeightedRate()
                .multiply(new BigDecimal(bonus.getBonusMultiplier()));
            
            return new WeightedExchangeRate(
                baseRate.getCurrencyPair(),
                baseRate.getWeightedRate(),
                adjustedRate,
                baseRate.getSourceRates(),
                baseRate.getSourceWeights(),
                baseRate.getConfidenceScore(),
                bonus.getAfricanExchangeCount()
            );
        }
        
        return baseRate;
    }
    
    /**
     * Validate order parameters
     */
    private void validateOrderParameters(OrderType orderType, String baseCurrency, 
                                       String quoteCurrency, BigDecimal amount, 
                                       BigDecimal price, double maxSlippage) {
        if (orderType == null) {
            throw new IllegalArgumentException("Order type cannot be null");
        }
        
        if (baseCurrency == null || baseCurrency.trim().isEmpty()) {
            throw new IllegalArgumentException("Base currency cannot be null or empty");
        }
        
        if (quoteCurrency == null || quoteCurrency.trim().isEmpty()) {
            throw new IllegalArgumentException("Quote currency cannot be null or empty");
        }
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        
        if (amount.compareTo(MIN_ORDER_SIZE) < 0 || amount.compareTo(MAX_ORDER_SIZE) > 0) {
            throw new IllegalArgumentException("Amount outside allowed range");
        }
        
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        
        if (maxSlippage < 0 || maxSlippage > 1.0) {
            throw new IllegalArgumentException("Max slippage must be between 0 and 1");
        }
    }
    
    /**
     * Generate unique order ID
     */
    private String generateOrderId(Address userAddress, String baseCurrency, String quoteCurrency) {
        try {
            String input = userAddress.toString() + baseCurrency + quoteCurrency + 
                         System.currentTimeMillis() + secureRandom.nextLong();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            return "ORD-" + bytesToHex(hash).substring(0, 16).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * Generate unique trade ID
     */
    private String generateTradeId(String buyOrderId, String sellOrderId) {
        try {
            String input = buyOrderId + sellOrderId + System.currentTimeMillis();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            return "TRD-" + bytesToHex(hash).substring(0, 16).toUpperCase();
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
     * Shutdown the matcher
     */
    public void shutdown() {
        try {
            matchingScheduler.shutdown();
            orderProcessor.shutdown();
            
            if (!matchingScheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                matchingScheduler.shutdownNow();
            }
            if (!orderProcessor.awaitTermination(30, TimeUnit.SECONDS)) {
                orderProcessor.shutdownNow();
            }
            
            LOGGER.info("DecentralizedFXMatcher shutdown completed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("FX matcher shutdown interrupted");
        }
    }
}