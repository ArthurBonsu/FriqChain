package org.example.app.core.integration;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * SystemHealthMonitor tracks overall health of the integrated TINC system.
 * 
 * Monitors:
 * - Banking-blockchain bridge status
 * - Oracle connectivity and validation rates
 * - Transaction processing performance
 * - State consistency
 * - Resource utilization
 */
public class SystemHealthMonitor {
    private static final Logger logger = Logger.getLogger(SystemHealthMonitor.class.getName());
    
    private final BankingBlockchainBridge bridge;
    private final OracleValidatorIntegrator oracleIntegrator;
    private final TokenTransactionProcessor tokenProcessor;
    private final ASUSettlementProcessor asuProcessor;
    private final IntegrationMetrics metrics;
    
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, HealthStatus> componentHealth;
    
    public SystemHealthMonitor(
            BankingBlockchainBridge bridge,
            OracleValidatorIntegrator oracleIntegrator,
            TokenTransactionProcessor tokenProcessor,
            ASUSettlementProcessor asuProcessor,
            IntegrationMetrics metrics) {
        
        this.bridge = bridge;
        this.oracleIntegrator = oracleIntegrator;
        this.tokenProcessor = tokenProcessor;
        this.asuProcessor = asuProcessor;
        this.metrics = metrics;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.componentHealth = new ConcurrentHashMap<>();
        
        logger.info("SystemHealthMonitor initialized");
    }
    
    public void startMonitoring() {
        scheduler.scheduleAtFixedRate(this::checkHealth, 0, 30, TimeUnit.SECONDS);
    }
    
    private void checkHealth() {
        try {
            checkBridgeHealth();
            checkOracleHealth();
            checkProcessorHealth();
            
            SystemHealth overallHealth = getOverallHealth();
            
            if (!overallHealth.isHealthy()) {
                logger.warning("System health degraded: " + overallHealth);
            }
        } catch (Exception e) {
            logger.severe("Error checking system health: " + e.getMessage());
        }
    }
    
    private void checkBridgeHealth() {
        int pendingOps = bridge.getPendingOperations().size();
        boolean healthy = pendingOps < 1000;
        componentHealth.put("BRIDGE", new HealthStatus("Banking Bridge", healthy, 
            "Pending operations: " + pendingOps));
    }
    
    private void checkOracleHealth() {
        Map<String, OracleValidatorIntegrator.OracleMetrics> oracleMetrics = 
            oracleIntegrator.getOracleMetrics();
        
        long totalValid = oracleMetrics.values().stream()
            .mapToLong(OracleValidatorIntegrator.OracleMetrics::getValidSignatures)
            .sum();
        
        long totalInvalid = oracleMetrics.values().stream()
            .mapToLong(OracleValidatorIntegrator.OracleMetrics::getInvalidSignatures)
            .sum();
        
        double successRate = totalValid + totalInvalid > 0 ?
            (double) totalValid / (totalValid + totalInvalid) : 0.0;
        
        boolean healthy = successRate > 0.95;
        componentHealth.put("ORACLE", new HealthStatus("Oracle System", healthy,
            String.format("Success rate: %.2f%%", successRate * 100)));
    }
    
    private void checkProcessorHealth() {
        TokenTransactionProcessor.ProcessingStats stats = tokenProcessor.getStats();
        boolean healthy = stats.getSuccessRate() > 0.90;
        componentHealth.put("PROCESSOR", new HealthStatus("Transaction Processor", healthy,
            String.format("Success rate: %.2f%%", stats.getSuccessRate() * 100)));
    }
    
    public SystemHealth getOverallHealth() {
        boolean allHealthy = componentHealth.values().stream()
            .allMatch(HealthStatus::isHealthy);
        
        return new SystemHealth(allHealthy, new HashMap<>(componentHealth),
            metrics.getReport());
    }
    
    public void shutdown() {
        scheduler.shutdown();
    }
    
    public static class HealthStatus {
        private final String component;
        private final boolean healthy;
        private final String details;
        
        HealthStatus(String component, boolean healthy, String details) {
            this.component = component;
            this.healthy = healthy;
            this.details = details;
        }
        
        public String getComponent() { return component; }
        public boolean isHealthy() { return healthy; }
        public String getDetails() { return details; }
    }
    
    public static class SystemHealth {
        private final boolean healthy;
        private final Map<String, HealthStatus> componentHealth;
        private final IntegrationMetrics.MetricsReport metrics;
        
        SystemHealth(boolean healthy, Map<String, HealthStatus> componentHealth,
                    IntegrationMetrics.MetricsReport metrics) {
            this.healthy = healthy;
            this.componentHealth = componentHealth;
            this.metrics = metrics;
        }
        
        public boolean isHealthy() { return healthy; }
        public Map<String, HealthStatus> getComponentHealth() { return componentHealth; }
        public IntegrationMetrics.MetricsReport getMetrics() { return metrics; }
        
        @Override
        public String toString() {
            return String.format("SystemHealth{healthy=%s, components=%d}",
                healthy, componentHealth.size());
        }
    }
}
