package org.example.app.core.monitoring;

import java.util.logging.Logger;

/**
 * HealthDashboard provides overall system health visualization.
 */
public class HealthDashboard {
    private static final Logger logger = Logger.getLogger(HealthDashboard.class.getName());
    
    private final MetricsAggregator aggregator;
    private final AlertingEngine alerting;
    
    public HealthDashboard(MetricsAggregator aggregator, AlertingEngine alerting) {
        this.aggregator = aggregator;
        this.alerting = alerting;
        logger.info("HealthDashboard initialized");
    }
    
    public String getHealthSummary() {
        MetricsAggregator.AggregatedSnapshot snapshot = aggregator.getLatestSnapshot();
        if (snapshot == null) return "No data available";
        
        StringBuilder summary = new StringBuilder();
        summary.append("TINC SYSTEM HEALTH\n");
        summary.append("==================\n\n");
        
        // TPS Health
        double tps = snapshot.getScalingStats().getCurrentTPS();
        summary.append(String.format("TPS: %,.0f %s\n", tps, 
            tps >= 50000 ? "✓" : "⚠"));
        
        // Network Health
        long peers = snapshot.getNetworkStats().getTotalPeers();
        summary.append(String.format("Peers: %d %s\n", peers,
            peers >= 10 ? "✓" : "⚠"));
        
        // Sovereignty Health
        boolean sovereigntyOK = snapshot.getSovereigntyStats().isCompliant();
        summary.append(String.format("Sovereignty: %s\n", sovereigntyOK ? "✓" : "✗"));
        
        // Integration Health
        double successRate = snapshot.getIntegrationStats().getSuccessRate();
        summary.append(String.format("Success Rate: %.2f%% %s\n", successRate * 100,
            successRate >= 0.95 ? "✓" : "⚠"));
        
        // Alerts
        int alertCount = alerting.getActiveAlerts().size();
        summary.append(String.format("\nActive Alerts: %d %s\n", alertCount,
            alertCount == 0 ? "✓" : "⚠"));
        
        return summary.toString();
    }
}
