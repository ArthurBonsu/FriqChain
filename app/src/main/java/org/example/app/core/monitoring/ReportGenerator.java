package org.example.app.core.monitoring;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

/**
 * ReportGenerator creates comprehensive system reports.
 */
public class ReportGenerator {
    private static final Logger logger = Logger.getLogger(ReportGenerator.class.getName());
    
    private final MetricsAggregator aggregator;
    private final ComplianceTracker complianceTracker;
    private final SimpleDateFormat dateFormat;
    
    public ReportGenerator(MetricsAggregator aggregator, ComplianceTracker complianceTracker) {
        this.aggregator = aggregator;
        this.complianceTracker = complianceTracker;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        logger.info("ReportGenerator initialized");
    }
    
    public String generateDailyReport() {
        MetricsAggregator.AggregatedSnapshot snapshot = aggregator.getLatestSnapshot();
        if (snapshot == null) return "No data available";
        
        StringBuilder report = new StringBuilder();
        report.append("═══════════════════════════════════════════════════════\n");
        report.append("           TINC BLOCKCHAIN DAILY REPORT\n");
        report.append("═══════════════════════════════════════════════════════\n");
        report.append("Generated: ").append(dateFormat.format(new Date())).append("\n\n");
        
        // Blockchain stats
        report.append("BLOCKCHAIN METRICS:\n");
        report.append(String.format("  Total Blocks: %,d\n", snapshot.getBlockchainStats().getTotalBlocks()));
        report.append(String.format("  Total Transactions: %,d\n", snapshot.getBlockchainStats().getTotalTransactions()));
        report.append(String.format("  Avg Block Time: %d ms\n\n", snapshot.getBlockchainStats().getAverageBlockTime()));
        
        // Scaling stats
        report.append("SCALING & PERFORMANCE:\n");
        report.append(String.format("  Current TPS: %,.0f\n", snapshot.getScalingStats().getCurrentTPS()));
        report.append(String.format("  Transactions Processed: %,d\n", snapshot.getScalingStats().getTotalTransactionsProcessed()));
        report.append(String.format("  Rollup Batches: %,d\n\n", snapshot.getScalingStats().getTotalRollupBatches()));
        
        // Sovereignty stats
        report.append("AFRICAN SOVEREIGNTY:\n");
        report.append(String.format("  Community Ownership: %.2f%% %s\n", 
            snapshot.getSovereigntyStats().getCommunityOwnership() * 100,
            snapshot.getSovereigntyStats().getCommunityOwnership() >= 0.50 ? "✓" : "✗"));
        report.append(String.format("  Cloud Dependency: %.2f%% %s\n",
            snapshot.getSovereigntyStats().getCloudDependency() * 100,
            snapshot.getSovereigntyStats().getCloudDependency() <= 0.20 ? "✓" : "✗"));
        report.append(String.format("  Max Country Control: %.2f%% %s\n",
            snapshot.getSovereigntyStats().getMaxCountryControl() * 100,
            snapshot.getSovereigntyStats().getMaxCountryControl() <= 0.33 ? "✓" : "✗"));
        report.append(String.format("  Compliant: %s\n\n", snapshot.getSovereigntyStats().isCompliant() ? "YES ✓" : "NO ✗"));
        
        // Compliance stats
        ComplianceTracker.ComplianceStats compStats = complianceTracker.getStats();
        report.append("COMPLIANCE:\n");
        report.append(String.format("  Total Screenings: %,d\n", compStats.getTotalScreenings()));
        report.append(String.format("  High Risk Detections: %,d (%.2f%%)\n", 
            compStats.getHighRiskDetections(), compStats.getHighRiskRate() * 100));
        report.append(String.format("  Reports Generated: %,d\n\n", compStats.getReportsGenerated()));
        
        report.append("═══════════════════════════════════════════════════════\n");
        
        return report.toString();
    }
    
    public String generateComplianceReport() {
        ComplianceTracker.ComplianceStats stats = complianceTracker.getStats();
        List<ComplianceTracker.ComplianceEvent> highRiskEvents = complianceTracker.getRecentHighRiskEvents(10);
        
        StringBuilder report = new StringBuilder();
        report.append("COMPLIANCE REPORT\n");
        report.append("================\n\n");
        report.append(String.format("Total Screenings: %,d\n", stats.getTotalScreenings()));
        report.append(String.format("High Risk Rate: %.2f%%\n\n", stats.getHighRiskRate() * 100));
        report.append("Recent High Risk Events:\n");
        
        for (ComplianceTracker.ComplianceEvent event : highRiskEvents) {
            report.append(String.format("  - TX: %s | Risk: %d | Time: %s\n",
                event.getTransactionId(), event.getRiskScore(),
                dateFormat.format(new Date(event.getTimestamp()))));
        }
        
        return report.toString();
    }
}
