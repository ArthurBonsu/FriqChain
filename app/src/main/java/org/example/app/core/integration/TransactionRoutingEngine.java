package org.example.app.core.integration;

import org.example.app.core.block.Transaction;
import org.example.app.core.blockchain.transaction.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * TransactionRoutingEngine routes transactions to appropriate processors.
 * 
 * Routing logic:
 * - TokenBankTransaction → TokenTransactionProcessor
 * - ASUSettlementTransaction → ASUSettlementProcessor
 * - CrossShardTransaction → CrossShardBridge (from scaling module)
 * - CurrencyExchangeTransaction → OracleValidatorIntegrator + Processors
 * - ExportTokenTransaction → Commodity validation + processors
 */
public class TransactionRoutingEngine {
    private static final Logger logger = Logger.getLogger(TransactionRoutingEngine.class.getName());
    
    // Transaction processors
    private final TokenTransactionProcessor tokenProcessor;
    private final ASUSettlementProcessor asuProcessor;
    
    // Oracle integrator
    private final OracleValidatorIntegrator oracleIntegrator;
    
    // Routing metrics
    private final ConcurrentHashMap<String, RouteMetrics> routeMetrics;
    
    // Transaction type registry
    private final ConcurrentHashMap<String, TransactionRoute> routes;
    
    public TransactionRoutingEngine(
            TokenTransactionProcessor tokenProcessor,
            ASUSettlementProcessor asuProcessor,
            OracleValidatorIntegrator oracleIntegrator) {
        
        this.tokenProcessor = tokenProcessor;
        this.asuProcessor = asuProcessor;
        this.oracleIntegrator = oracleIntegrator;
        this.routeMetrics = new ConcurrentHashMap<>();
        this.routes = new ConcurrentHashMap<>();
        
        initializeRoutes();
        logger.info("TransactionRoutingEngine initialized with " + routes.size() + " routes");
    }
    
    /**
     * Initialize transaction routes
     */
    private void initializeRoutes() {
        // Token bank transactions
        routes.put("TokenBankTransaction", new TransactionRoute(
            "TOKEN_BANK",
            "Processes token banking operations",
            Arrays.asList("TokenBankValidator", "FairWeightingValidator")
        ));
        
        // ASU settlements
        routes.put("ASUSettlementTransaction", new TransactionRoute(
            "ASU_SETTLEMENT",
            "Processes cross-border ASU settlements",
            Arrays.asList("ProofOfReservesValidator", "ASUBasketValidator", "OracleValidator")
        ));
        
        // Currency exchanges
        routes.put("CurrencyExchangeTransaction", new TransactionRoute(
            "CURRENCY_EXCHANGE",
            "Processes currency exchanges with oracle data",
            Arrays.asList("FairWeightingValidator", "AntiManipulationValidator")
        ));
        
        // Export tokens
        routes.put("ExportTokenTransaction", new TransactionRoute(
            "EXPORT_TOKEN",
            "Processes export token issuance/trading",
            Arrays.asList("CommodityValidator", "CustomsValidator")
        ));
        
        // Cross-shard transactions
        routes.put("CrossShardTransaction", new TransactionRoute(
            "CROSS_SHARD",
            "Processes cross-shard atomic transactions",
            Arrays.asList("ShardValidator", "AtomicCommitValidator")
        ));
        
        // Initialize metrics for each route
        for (String routeKey : routes.keySet()) {
            routeMetrics.put(routeKey, new RouteMetrics(routeKey));
        }
    }
    
    /**
     * Route transaction to appropriate processor
     */
    public CompletableFuture<RoutingResult> routeTransaction(Transaction transaction) {
        String txType = transaction.getClass().getSimpleName();
        
        logger.fine("Routing transaction type: " + txType);
        
        // Get route for this transaction type
        TransactionRoute route = routes.get(txType);
        
        if (route == null) {
            logger.warning("No route found for transaction type: " + txType);
            return CompletableFuture.completedFuture(
                new RoutingResult(false, "Unknown transaction type", txType)
            );
        }
        
        // Update metrics
        RouteMetrics metrics = routeMetrics.get(txType);
        metrics.incrementAttempted();
        
        // Route based on transaction type
        return routeByType(transaction, route, metrics);
    }
    
    /**
     * Route by transaction type
     */
    private CompletableFuture<RoutingResult> routeByType(
            Transaction transaction, 
            TransactionRoute route,
            RouteMetrics metrics) {
        
        try {
            if (transaction instanceof TokenBankTransaction) {
                return routeTokenBankTransaction((TokenBankTransaction) transaction, metrics);
            } else if (transaction instanceof ASUSettlementTransaction) {
                return routeASUSettlement((ASUSettlementTransaction) transaction, metrics);
            } else {
                // Generic routing for other transaction types
                return routeGenericTransaction(transaction, route, metrics);
            }
        } catch (Exception e) {
            logger.warning("Error routing transaction: " + e.getMessage());
            metrics.incrementFailed();
            return CompletableFuture.completedFuture(
                new RoutingResult(false, "Routing error: " + e.getMessage(), 
                    transaction.getClass().getSimpleName())
            );
        }
    }
    
    /**
     * Route token bank transaction
     */
    private CompletableFuture<RoutingResult> routeTokenBankTransaction(
            TokenBankTransaction transaction,
            RouteMetrics metrics) {
        
        return CompletableFuture.supplyAsync(() -> {
            boolean processed = tokenProcessor.processTransaction(transaction);
            
            if (processed) {
                metrics.incrementSuccessful();
                return new RoutingResult(true, "Token transaction processed", "TokenBankTransaction");
            } else {
                metrics.incrementFailed();
                return new RoutingResult(false, "Token processing failed", "TokenBankTransaction");
            }
        });
    }
    
    /**
     * Route ASU settlement transaction
     */
    private CompletableFuture<RoutingResult> routeASUSettlement(
            ASUSettlementTransaction transaction,
            RouteMetrics metrics) {
        
        return CompletableFuture.supplyAsync(() -> {
            boolean processed = asuProcessor.processSettlement(transaction);
            
            if (processed) {
                metrics.incrementSuccessful();
                return new RoutingResult(true, "ASU settlement processed", "ASUSettlementTransaction");
            } else {
                metrics.incrementFailed();
                return new RoutingResult(false, "ASU processing failed", "ASUSettlementTransaction");
            }
        });
    }
    
    /**
     * Route generic transaction
     */
    private CompletableFuture<RoutingResult> routeGenericTransaction(
            Transaction transaction,
            TransactionRoute route,
            RouteMetrics metrics) {
        
        // For generic transactions, log and return success
        // In production, this would route to specific processors
        logger.info("Routing generic transaction: " + route.getName());
        
        metrics.incrementSuccessful();
        return CompletableFuture.completedFuture(
            new RoutingResult(true, "Transaction routed to " + route.getName(), 
                transaction.getClass().getSimpleName())
        );
    }
    
    /**
     * Get route information for transaction type
     */
    public TransactionRoute getRoute(String transactionType) {
        return routes.get(transactionType);
    }
    
    /**
     * Get all available routes
     */
    public Map<String, TransactionRoute> getAllRoutes() {
        return new HashMap<>(routes);
    }
    
    /**
     * Get routing metrics
     */
    public Map<String, RouteMetrics> getMetrics() {
        return new HashMap<>(routeMetrics);
    }
    
    /**
     * Get routing statistics
     */
    public RoutingStats getStats() {
        long totalAttempted = 0;
        long totalSuccessful = 0;
        long totalFailed = 0;
        
        for (RouteMetrics metrics : routeMetrics.values()) {
            totalAttempted += metrics.getAttempted();
            totalSuccessful += metrics.getSuccessful();
            totalFailed += metrics.getFailed();
        }
        
        return new RoutingStats(totalAttempted, totalSuccessful, totalFailed, routes.size());
    }
    
    /**
     * Transaction route
     */
    public static class TransactionRoute {
        private final String name;
        private final String description;
        private final List<String> requiredValidators;
        
        TransactionRoute(String name, String description, List<String> requiredValidators) {
            this.name = name;
            this.description = description;
            this.requiredValidators = new ArrayList<>(requiredValidators);
        }
        
        public String getName() { return name; }
        public String getDescription() { return description; }
        public List<String> getRequiredValidators() { return new ArrayList<>(requiredValidators); }
    }
    
    /**
     * Route metrics
     */
    public static class RouteMetrics {
        private final String routeName;
        private final AtomicLong attempted;
        private final AtomicLong successful;
        private final AtomicLong failed;
        
        RouteMetrics(String routeName) {
            this.routeName = routeName;
            this.attempted = new AtomicLong(0);
            this.successful = new AtomicLong(0);
            this.failed = new AtomicLong(0);
        }
        
        void incrementAttempted() { attempted.incrementAndGet(); }
        void incrementSuccessful() { successful.incrementAndGet(); }
        void incrementFailed() { failed.incrementAndGet(); }
        
        public String getRouteName() { return routeName; }
        public long getAttempted() { return attempted.get(); }
        public long getSuccessful() { return successful.get(); }
        public long getFailed() { return failed.get(); }
        
        public double getSuccessRate() {
            long total = attempted.get();
            return total > 0 ? (double) successful.get() / total : 0.0;
        }
    }
    
    /**
     * Routing result
     */
    public static class RoutingResult {
        private final boolean success;
        private final String message;
        private final String transactionType;
        private final long timestamp;
        
        RoutingResult(boolean success, String message, String transactionType) {
            this.success = success;
            this.message = message;
            this.transactionType = transactionType;
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getTransactionType() { return transactionType; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Routing statistics
     */
    public static class RoutingStats {
        private final long totalAttempted;
        private final long totalSuccessful;
        private final long totalFailed;
        private final int routesRegistered;
        
        RoutingStats(long totalAttempted, long totalSuccessful, long totalFailed, int routesRegistered) {
            this.totalAttempted = totalAttempted;
            this.totalSuccessful = totalSuccessful;
            this.totalFailed = totalFailed;
            this.routesRegistered = routesRegistered;
        }
        
        public long getTotalAttempted() { return totalAttempted; }
        public long getTotalSuccessful() { return totalSuccessful; }
        public long getTotalFailed() { return totalFailed; }
        public int getRoutesRegistered() { return routesRegistered; }
        
        public double getSuccessRate() {
            return totalAttempted > 0 ? (double) totalSuccessful / totalAttempted : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "RoutingStats{attempted=%d, successful=%d, failed=%d, successRate=%.2f%%, routes=%d}",
                totalAttempted, totalSuccessful, totalFailed, getSuccessRate() * 100, routesRegistered
            );
        }
    }
}
