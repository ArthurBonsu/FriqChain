package org.example.app.core.monitoring;

import java.util.logging.Logger;

/**
 * SovereigntyDashboard provides dedicated monitoring of African sovereignty compliance.
 */
public class SovereigntyDashboard {
    private static final Logger logger = Logger.getLogger(SovereigntyDashboard.class.getName());
    
    private final MetricsAggregator aggregator;
    
    public SovereigntyDashboard(MetricsAggregator aggregator) {
        this.aggregator = aggregator;
        logger.info("SovereigntyDashboard initialized");
    }
    
    public String getSovereigntyReport() {
        MetricsAggregator.AggregatedSnapshot snapshot = aggregator.getLatestSnapshot();
        if (snapshot == null) return "No data available";
        
        MetricsAggregator.SovereigntyStats stats = snapshot.getSovereigntyStats();
        
        StringBuilder report = new StringBuilder();
        report.append("╔══════════════════════════════════════════════════╗\n");
        report.append("║       AFRICAN SOVEREIGNTY COMPLIANCE             ║\n");
        report.append("╠══════════════════════════════════════════════════╣\n");
        report.append(String.format("║ Community Ownership:    %5.2f%% %s          ║\n",
            stats.getCommunityOwnership() * 100,
            stats.getCommunityOwnership() >= 0.50 ? "✓" : "✗"));
        report.append(String.format("║ Cloud Dependency:       %5.2f%% %s          ║\n",
            stats.getCloudDependency() * 100,
            stats.getCloudDependency() <= 0.20 ? "✓" : "✗"));
        report.append(String.format("║ Max Country Control:    %5.2f%% %s          ║\n",
            stats.getMaxCountryControl() * 100,
            stats.getMaxCountryControl() <= 0.33 ? "✓" : "✗"));
        report.append(String.format("║ Max Region Control:     %5.2f%% %s          ║\n",
            stats.getMaxRegionControl() * 100,
            stats.getMaxRegionControl() <= 0.33 ? "✓" : "✗"));
        report.append("╠══════════════════════════════════════════════════╣\n");
        report.append(String.format("║ Overall Compliance:     %-25s║\n",
            stats.isCompliant() ? "PASS ✓" : "FAIL ✗"));
        report.append("╚══════════════════════════════════════════════════╝\n");
        
        return report.toString();
    }
}
