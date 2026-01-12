package org.example.app.core.integration;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * IntegrationMetrics tracks performance metrics across all integration components.
 * 
 * Monitors:
 * - Pending operations count
 * - Successful operations
 * - Failed operations
 * - Oracle validation success rate
 * - Transaction routing efficiency
 * - State reconciliation status
 */
public class IntegrationMetrics {
    private static final Logger logger = Logger.getLogger(IntegrationMetrics.class.getName());
    
    // Operation counts
    private final AtomicLong pendingOperations;
    private final AtomicLong successfulOperations;
    private final AtomicLong failedOperations;
    
    // Component-specific metrics
    private final AtomicLong tokenTransactionsProcessed;
    private final AtomicLong asuSettlementsProcessed;
    private final AtomicLong oracleValidationsPerformed;
    private final AtomicLong routingAttempts;
    
    // Timing metrics
    private final AtomicLong totalProcessingTimeMs;
    private final AtomicLong operationCount;
    
    public IntegrationMetrics() {
        this.pendingOperations = new AtomicLong(0);
        this.successfulOperations = new AtomicLong(0);
        this.failedOperations = new AtomicLong(0);
        this.tokenTransactionsProcessed = new AtomicLong(0);
        this.asuSettlementsProcessed = new AtomicLong(0);
        this.oracleValidationsPerformed = new AtomicLong(0);
        this.routingAttempts = new AtomicLong(0);
        this.totalProcessingTimeMs = new AtomicLong(0);
        this.operationCount = new AtomicLong(0);
        
        logger.info("IntegrationMetrics initialized");
    }
    
    // Pending operations
    public void incrementPendingOperations() { pendingOperations.incrementAndGet(); }
    public void decrementPendingOperations() { pendingOperations.decrementAndGet(); }
    public long getPendingOperations() { return pendingOperations.get(); }
    
    // Successful operations
    public void incrementSuccessfulOperations() { successfulOperations.incrementAndGet(); }
    public long getSuccessfulOperations() { return successfulOperations.get(); }
    
    // Failed operations
    public void incrementFailedOperations() { failedOperations.incrementAndGet(); }
    public long getFailedOperations() { return failedOperations.get(); }
    
    // Component metrics
    public void incrementTokenTransactions() { tokenTransactionsProcessed.incrementAndGet(); }
    public void incrementASUSettlements() { asuSettlementsProcessed.incrementAndGet(); }
    public void incrementOracleValidations() { oracleValidationsPerformed.incrementAndGet(); }
    public void incrementRoutingAttempts() { routingAttempts.incrementAndGet(); }
    
    // Timing
    public void recordProcessingTime(long timeMs) {
        totalProcessingTimeMs.addAndGet(timeMs);
        operationCount.incrementAndGet();
    }
    
    /**
     * Get success rate
     */
    public double getSuccessRate() {
        long total = successfulOperations.get() + failedOperations.get();
        return total > 0 ? (double) successfulOperations.get() / total : 0.0;
    }
    
    /**
     * Get average processing time
     */
    public double getAverageProcessingTimeMs() {
        long count = operationCount.get();
        return count > 0 ? (double) totalProcessingTimeMs.get() / count : 0.0;
    }
    
    /**
     * Get comprehensive report
     */
    public MetricsReport getReport() {
        return new MetricsReport(
            pendingOperations.get(),
            successfulOperations.get(),
            failedOperations.get(),
            tokenTransactionsProcessed.get(),
            asuSettlementsProcessed.get(),
            oracleValidationsPerformed.get(),
            routingAttempts.get(),
            getSuccessRate(),
            getAverageProcessingTimeMs()
        );
    }
    
    /**
     * Metrics report
     */
    public static class MetricsReport {
        private final long pendingOperations;
        private final long successfulOperations;
        private final long failedOperations;
        private final long tokenTransactions;
        private final long asuSettlements;
        private final long oracleValidations;
        private final long routingAttempts;
        private final double successRate;
        private final double avgProcessingTime;
        
        MetricsReport(long pendingOperations, long successfulOperations, long failedOperations,
                     long tokenTransactions, long asuSettlements, long oracleValidations,
                     long routingAttempts, double successRate, double avgProcessingTime) {
            this.pendingOperations = pendingOperations;
            this.successfulOperations = successfulOperations;
            this.failedOperations = failedOperations;
            this.tokenTransactions = tokenTransactions;
            this.asuSettlements = asuSettlements;
            this.oracleValidations = oracleValidations;
            this.routingAttempts = routingAttempts;
            this.successRate = successRate;
            this.avgProcessingTime = avgProcessingTime;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Integration Metrics:\n" +
                "  Pending: %d\n" +
                "  Successful: %d\n" +
                "  Failed: %d\n" +
                "  Success Rate: %.2f%%\n" +
                "  Token Transactions: %d\n" +
                "  ASU Settlements: %d\n" +
                "  Oracle Validations: %d\n" +
                "  Routing Attempts: %d\n" +
                "  Avg Processing Time: %.2fms",
                pendingOperations, successfulOperations, failedOperations,
                successRate * 100, tokenTransactions, asuSettlements,
                oracleValidations, routingAttempts, avgProcessingTime
            );
        }
    }
}
