package org.example.app.core.scaling;

import java.util.concurrent.ConcurrentLinkedQueue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * ScalingMetrics provides comprehensive monitoring for the TINC scaling infrastructure.
 * 
 * Tracks:
 * - TPS performance across 20 shards
 * - Sovereignty compliance (community ownership, geographic distribution)
 * - Data availability (35-node DA committee)
 * - Cross-shard transaction success rates
 * - Rollup batch processing
 * - Regional committee health
 */
public class ScalingMetrics {
    private static final Logger logger = Logger.getLogger(ScalingMetrics.class.getName());
    
    // Component references
    private final ShardCoordinator shardCoordinator;
    private final TPSScalingEngine tpsEngine;
    private final SovereigntyEnforcer sovereigntyEnforcer;
    private final DataAvailabilityCommittee daCommittee;
    private final CommunityNodeManager communityNodeManager;
    private final GeographicDistributor geoDistributor;
    private final CrossShardBridge crossShardBridge;
    private final RollupBatchProcessor rollupProcessor;
    
    // Aggregate metrics
    private final AtomicLong totalTransactionsProcessed;
    private final AtomicLong totalCrossShardTransactions;
    private final AtomicLong totalRollupBatches;
    
    // Performance history
    private final ConcurrentLinkedQueue<PerformanceSnapshot> performanceHistory;
    
    // Alert thresholds
    private static final double MIN_TPS_THRESHOLD = 50000;  // Alert if below 50K TPS
    private static final double MIN_COMMUNITY_OWNERSHIP = 0.50;
    private static final double MAX_CLOUD_DEPENDENCY = 0.20;
    
    public ScalingMetrics(ShardCoordinator shardCoordinator,
                         TPSScalingEngine tpsEngine,
                         SovereigntyEnforcer sovereigntyEnforcer,
                         DataAvailabilityCommittee daCommittee,
                         CommunityNodeManager communityNodeManager,
                         GeographicDistributor geoDistributor,
                         CrossShardBridge crossShardBridge,
                         RollupBatchProcessor rollupProcessor) {
        this.shardCoordinator = shardCoordinator;
        this.tpsEngine = tpsEngine;
        this.sovereigntyEnforcer = sovereigntyEnforcer;
        this.daCommittee = daCommittee;
        this.communityNodeManager = communityNodeManager;
        this.geoDistributor = geoDistributor;
        this.crossShardBridge = crossShardBridge;
        this.rollupProcessor = rollupProcessor;
        
        this.totalTransactionsProcessed = new AtomicLong(0);
        this.totalCrossShardTransactions = new AtomicLong(0);
        this.totalRollupBatches = new AtomicLong(0);
        
        this.performanceHistory = new ConcurrentLinkedQueue<>();
    }
    
    /**
     * Capture current performance snapshot
     */
    public PerformanceSnapshot captureSnapshot() {
        // TPS metrics
        double currentTPS = tpsEngine.getCurrentTPS();
        TPSScalingEngine.TPSMetrics tpsMetrics = tpsEngine.getMetrics();
        
        // Sovereignty metrics
        SovereigntyEnforcer.SovereigntyReport sovereigntyReport = sovereigntyEnforcer.getComplianceReport();
        CommunityNodeManager.CommunityStats communityStats = communityNodeManager.getStatistics();
        GeographicDistributor.GeographicReport geoReport = geoDistributor.getDistributionReport();
        
        // Cross-shard metrics
        int pendingCrossShardTx = crossShardBridge.getPendingTransactions().size();
        
        // Rollup metrics
        RollupBatchProcessor.BatchMetrics rollupMetrics = rollupProcessor.getMetrics();
        
        // Create snapshot
        PerformanceSnapshot snapshot = new PerformanceSnapshot(
            System.currentTimeMillis(),
            currentTPS,
            tpsMetrics.getTotalProcessed(),
            sovereigntyReport.getCommunityOwnershipPercentage(),
            sovereigntyReport.getCloudDependencyPercentage(),
            geoReport.getMaxCountryPercentage(),
            pendingCrossShardTx,
            rollupMetrics.getBatchesSettled(),
            sovereigntyReport.isCompliant()
        );
        
        // Store in history
        performanceHistory.offer(snapshot);
        while (performanceHistory.size() > 1000) {
            performanceHistory.poll();
        }
        
        // Check for alerts
        checkAlerts(snapshot);
        
        return snapshot;
    }
    
    /**
     * Check for alert conditions
     */
    private void checkAlerts(PerformanceSnapshot snapshot) {
        List<String> alerts = new ArrayList<>();
        
        // TPS alerts
        if (snapshot.getCurrentTPS() < MIN_TPS_THRESHOLD) {
            alerts.add("TPS below threshold: " + String.format("%.0f", snapshot.getCurrentTPS()) + 
                " (minimum: " + String.format("%.0f", MIN_TPS_THRESHOLD) + ")");
        }
        
        // Sovereignty alerts
        if (snapshot.getCommunityOwnershipPercentage() < MIN_COMMUNITY_OWNERSHIP) {
            alerts.add("Community ownership below 50%: " + 
                String.format("%.2f%%", snapshot.getCommunityOwnershipPercentage() * 100));
        }
        
        if (snapshot.getCloudDependencyPercentage() > MAX_CLOUD_DEPENDENCY) {
            alerts.add("Cloud dependency above 20%: " + 
                String.format("%.2f%%", snapshot.getCloudDependencyPercentage() * 100));
        }
        
        if (snapshot.getMaxCountryControl() > 0.33) {
            alerts.add("Single country controls >33% of nodes: " + 
                String.format("%.2f%%", snapshot.getMaxCountryControl() * 100));
        }
        
        if (!snapshot.isSovereigntyCompliant()) {
            alerts.add("SOVEREIGNTY VIOLATION DETECTED");
        }
        
        // Log alerts
        if (!alerts.isEmpty()) {
            logger.warning("=== SCALING ALERTS ===");
            for (String alert : alerts) {
                logger.warning("  ⚠ " + alert);
            }
        }
    }
    
    /**
     * Get comprehensive system report
     */
    public SystemReport getSystemReport() {
        PerformanceSnapshot currentSnapshot = captureSnapshot();
        
        // Shard metrics
        ShardCoordinator.AggregatedMetrics shardMetrics = shardCoordinator.getAggregatedMetrics();
        
        // DA committee health
        Map<DataAvailabilityCommittee.Region, Boolean> daHealth = new HashMap<>();
        for (DataAvailabilityCommittee.Region region : DataAvailabilityCommittee.Region.values()) {
            DataAvailabilityCommittee.RegionalCommittee committee = daCommittee.getCommittee(region);
            daHealth.put(region, committee.getNodeCount() == 7);
        }
        
        return new SystemReport(
            currentSnapshot,
            shardMetrics,
            sovereigntyEnforcer.getComplianceReport(),
            communityNodeManager.getStatistics(),
            geoDistributor.getDistributionReport(),
            daHealth,
            tpsEngine.getMetrics(),
            rollupProcessor.getMetrics()
        );
    }
    
    /**
     * Get performance trend over time
     */
    public PerformanceTrend getTrend(int durationMinutes) {
        long cutoffTime = System.currentTimeMillis() - (durationMinutes * 60 * 1000);
        
        List<PerformanceSnapshot> recentSnapshots = new ArrayList<>();
        for (PerformanceSnapshot snapshot : performanceHistory) {
            if (snapshot.getTimestamp() >= cutoffTime) {
                recentSnapshots.add(snapshot);
            }
        }
        
        if (recentSnapshots.isEmpty()) {
            return null;
        }
        
        // Calculate trends
        double avgTPS = recentSnapshots.stream()
            .mapToDouble(PerformanceSnapshot::getCurrentTPS)
            .average()
            .orElse(0.0);
        
        double maxTPS = recentSnapshots.stream()
            .mapToDouble(PerformanceSnapshot::getCurrentTPS)
            .max()
            .orElse(0.0);
        
        double minTPS = recentSnapshots.stream()
            .mapToDouble(PerformanceSnapshot::getCurrentTPS)
            .min()
            .orElse(0.0);
        
        long totalTxProcessed = recentSnapshots.get(recentSnapshots.size() - 1).getTotalTransactionsProcessed() -
                               recentSnapshots.get(0).getTotalTransactionsProcessed();
        
        return new PerformanceTrend(durationMinutes, avgTPS, maxTPS, minTPS, totalTxProcessed);
    }
    
    /**
     * Performance snapshot
     */
    public static class PerformanceSnapshot {
        private final long timestamp;
        private final double currentTPS;
        private final long totalTransactionsProcessed;
        private final double communityOwnershipPercentage;
        private final double cloudDependencyPercentage;
        private final double maxCountryControl;
        private final int pendingCrossShardTransactions;
        private final long totalRollupBatches;
        private final boolean sovereigntyCompliant;
        
        public PerformanceSnapshot(long timestamp, double currentTPS, long totalTransactionsProcessed,
                                 double communityOwnershipPercentage, double cloudDependencyPercentage,
                                 double maxCountryControl, int pendingCrossShardTransactions,
                                 long totalRollupBatches, boolean sovereigntyCompliant) {
            this.timestamp = timestamp;
            this.currentTPS = currentTPS;
            this.totalTransactionsProcessed = totalTransactionsProcessed;
            this.communityOwnershipPercentage = communityOwnershipPercentage;
            this.cloudDependencyPercentage = cloudDependencyPercentage;
            this.maxCountryControl = maxCountryControl;
            this.pendingCrossShardTransactions = pendingCrossShardTransactions;
            this.totalRollupBatches = totalRollupBatches;
            this.sovereigntyCompliant = sovereigntyCompliant;
        }
        
        public long getTimestamp() { return timestamp; }
        public double getCurrentTPS() { return currentTPS; }
        public long getTotalTransactionsProcessed() { return totalTransactionsProcessed; }
        public double getCommunityOwnershipPercentage() { return communityOwnershipPercentage; }
        public double getCloudDependencyPercentage() { return cloudDependencyPercentage; }
        public double getMaxCountryControl() { return maxCountryControl; }
        public int getPendingCrossShardTransactions() { return pendingCrossShardTransactions; }
        public long getTotalRollupBatches() { return totalRollupBatches; }
        public boolean isSovereigntyCompliant() { return sovereigntyCompliant; }
    }
    
    /**
     * Performance trend
     */
    public static class PerformanceTrend {
        private final int durationMinutes;
        private final double averageTPS;
        private final double maxTPS;
        private final double minTPS;
        private final long totalTransactionsProcessed;
        
        public PerformanceTrend(int durationMinutes, double averageTPS, double maxTPS, 
                              double minTPS, long totalTransactionsProcessed) {
            this.durationMinutes = durationMinutes;
            this.averageTPS = averageTPS;
            this.maxTPS = maxTPS;
            this.minTPS = minTPS;
            this.totalTransactionsProcessed = totalTransactionsProcessed;
        }
        
        public int getDurationMinutes() { return durationMinutes; }
        public double getAverageTPS() { return averageTPS; }
        public double getMaxTPS() { return maxTPS; }
        public double getMinTPS() { return minTPS; }
        public long getTotalTransactionsProcessed() { return totalTransactionsProcessed; }
        
        @Override
        public String toString() {
            return String.format(
                "Performance Trend (Last %d minutes):\n" +
                "  Average TPS: %.0f\n" +
                "  Max TPS: %.0f\n" +
                "  Min TPS: %.0f\n" +
                "  Total Transactions: %d",
                durationMinutes, averageTPS, maxTPS, minTPS, totalTransactionsProcessed
            );
        }
    }
    
    /**
     * Comprehensive system report
     */
    public static class SystemReport {
        private final PerformanceSnapshot currentSnapshot;
        private final ShardCoordinator.AggregatedMetrics shardMetrics;
        private final SovereigntyEnforcer.SovereigntyReport sovereigntyReport;
        private final CommunityNodeManager.CommunityStats communityStats;
        private final GeographicDistributor.GeographicReport geoReport;
        private final Map<DataAvailabilityCommittee.Region, Boolean> daHealth;
        private final TPSScalingEngine.TPSMetrics tpsMetrics;
        private final RollupBatchProcessor.BatchMetrics rollupMetrics;
        
        public SystemReport(PerformanceSnapshot currentSnapshot,
                          ShardCoordinator.AggregatedMetrics shardMetrics,
                          SovereigntyEnforcer.SovereigntyReport sovereigntyReport,
                          CommunityNodeManager.CommunityStats communityStats,
                          GeographicDistributor.GeographicReport geoReport,
                          Map<DataAvailabilityCommittee.Region, Boolean> daHealth,
                          TPSScalingEngine.TPSMetrics tpsMetrics,
                          RollupBatchProcessor.BatchMetrics rollupMetrics) {
            this.currentSnapshot = currentSnapshot;
            this.shardMetrics = shardMetrics;
            this.sovereigntyReport = sovereigntyReport;
            this.communityStats = communityStats;
            this.geoReport = geoReport;
            this.daHealth = daHealth;
            this.tpsMetrics = tpsMetrics;
            this.rollupMetrics = rollupMetrics;
        }
        
        public PerformanceSnapshot getCurrentSnapshot() { return currentSnapshot; }
        public ShardCoordinator.AggregatedMetrics getShardMetrics() { return shardMetrics; }
        public SovereigntyEnforcer.SovereigntyReport getSovereigntyReport() { return sovereigntyReport; }
        public CommunityNodeManager.CommunityStats getCommunityStats() { return communityStats; }
        public GeographicDistributor.GeographicReport getGeoReport() { return geoReport; }
        public Map<DataAvailabilityCommittee.Region, Boolean> getDaHealth() { return daHealth; }
        public TPSScalingEngine.TPSMetrics getTpsMetrics() { return tpsMetrics; }
        public RollupBatchProcessor.BatchMetrics getRollupMetrics() { return rollupMetrics; }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("╔════════════════════════════════════════════════════════════════╗\n");
            sb.append("║          TINC BLOCKCHAIN SCALING SYSTEM REPORT                 ║\n");
            sb.append("╠════════════════════════════════════════════════════════════════╣\n");
            sb.append("║ PERFORMANCE METRICS                                            ║\n");
            sb.append("╠════════════════════════════════════════════════════════════════╣\n");
            sb.append(String.format("║ Current TPS:              %,10.0f                       ║\n", 
                currentSnapshot.getCurrentTPS()));
            sb.append(String.format("║ Total Processed:          %,15d                  ║\n",
                currentSnapshot.getTotalTransactionsProcessed()));
            sb.append(String.format("║ Avg Latency:              %,10.2f ms                    ║\n",
                tpsMetrics.getAverageLatency()));
            sb.append("╠════════════════════════════════════════════════════════════════╣\n");
            sb.append("║ SOVEREIGNTY COMPLIANCE                                         ║\n");
            sb.append("╠════════════════════════════════════════════════════════════════╣\n");
            sb.append(String.format("║ Community Ownership:      %5.2f%% %s                      ║\n",
                communityStats.getCommunityPercentage() * 100,
                communityStats.getCommunityPercentage() >= 0.50 ? "✓" : "✗"));
            sb.append(String.format("║ Cloud Dependency:         %5.2f%% %s                      ║\n",
                communityStats.getCloudPercentage() * 100,
                communityStats.getCloudPercentage() <= 0.20 ? "✓" : "✗"));
            sb.append(String.format("║ Max Country Control:      %5.2f%% %s                      ║\n",
                currentSnapshot.getMaxCountryControl() * 100,
                currentSnapshot.getMaxCountryControl() <= 0.33 ? "✓" : "✗"));
            sb.append(String.format("║ Overall Compliance:       %s                             ║\n",
                sovereigntyReport.isCompliant() ? "PASS ✓" : "FAIL ✗"));
            sb.append("╠════════════════════════════════════════════════════════════════╣\n");
            sb.append("║ DATA AVAILABILITY (35 Nodes / 5 Regions)                       ║\n");
            sb.append("╠════════════════════════════════════════════════════════════════╣\n");
            for (DataAvailabilityCommittee.Region region : DataAvailabilityCommittee.Region.values()) {
                sb.append(String.format("║ %-20s:    %s                              ║\n",
                    region.getDisplayName(),
                    daHealth.get(region) ? "7/7 ✓" : "DEGRADED ✗"));
            }
            sb.append("╠════════════════════════════════════════════════════════════════╣\n");
            sb.append("║ ROLLUP BATCHING                                                ║\n");
            sb.append("╠════════════════════════════════════════════════════════════════╣\n");
            sb.append(String.format("║ Batches Created:          %,15d                  ║\n",
                rollupMetrics.getBatchesCreated()));
            sb.append(String.format("║ Batches Settled:          %,15d                  ║\n",
                rollupMetrics.getBatchesSettled()));
            sb.append(String.format("║ Batches Failed:           %,15d                  ║\n",
                rollupMetrics.getBatchesFailed()));
            sb.append("╚════════════════════════════════════════════════════════════════╝\n");
            
            return sb.toString();
        }
    }
}
