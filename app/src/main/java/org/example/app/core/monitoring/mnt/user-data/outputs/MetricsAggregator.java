package org.example.app.core.monitoring;

import org.example.app.core.metrics.*;
import org.example.app.core.scaling.ScalingMetrics;
import org.example.app.core.scaling.SovereigntyEnforcer;
import org.example.app.core.integration.IntegrationMetrics;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * MetricsAggregator is the central hub for all TINC system metrics.
 * 
 * Aggregates data from:
 * - Blockchain metrics (blocks, transactions, block time)
 * - Consensus metrics (validators, consensus rounds)
 * - Network metrics (peers, messages, latency)
 * - Scaling metrics (TPS, shards, sovereignty)
 * - Integration metrics (banking-blockchain bridge)
 * - Compliance metrics (AML, KYC)
 * - Reserve metrics (treasury, audits)
 * - Governance metrics (proposals, votes)
 * 
 * Provides unified view for monitoring dashboards and analytics.
 */
public class MetricsAggregator {
    private static final Logger logger = Logger.getLogger(MetricsAggregator.class.getName());
    
    // Core metrics
    private final BlockchainMetrics blockchainMetrics;
    private final ConsensusMetrics consensusMetrics;
    private final NetworkMetrics networkMetrics;
    
    // Scaling & sovereignty metrics
    private final ScalingMetrics scalingMetrics;
    private final SovereigntyEnforcer sovereigntyEnforcer;
    
    // Integration metrics
    private final IntegrationMetrics integrationMetrics;
    
    // Banking metrics (injected)
    private ComplianceMetrics complianceMetrics;
    private ReserveMetrics reserveMetrics;
    private GovernanceMetrics governanceMetrics;
    
    // Aggregated snapshot cache
    private volatile AggregatedSnapshot lastSnapshot;
    private final Object snapshotLock = new Object();
    
    // Scheduled aggregation
    private final ScheduledExecutorService scheduler;
    private final int aggregationIntervalSeconds;
    
    // Historical snapshots
    private final ConcurrentLinkedQueue<AggregatedSnapshot> snapshotHistory;
    private static final int MAX_HISTORY_SIZE = 1000;
    
    public MetricsAggregator(
            BlockchainMetrics blockchainMetrics,
            ConsensusMetrics consensusMetrics,
            NetworkMetrics networkMetrics,
            ScalingMetrics scalingMetrics,
            SovereigntyEnforcer sovereigntyEnforcer,
            IntegrationMetrics integrationMetrics,
            int aggregationIntervalSeconds) {
        
        this.blockchainMetrics = blockchainMetrics;
        this.consensusMetrics = consensusMetrics;
        this.networkMetrics = networkMetrics;
        this.scalingMetrics = scalingMetrics;
        this.sovereigntyEnforcer = sovereigntyEnforcer;
        this.integrationMetrics = integrationMetrics;
        this.aggregationIntervalSeconds = aggregationIntervalSeconds;
        
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.snapshotHistory = new ConcurrentLinkedQueue<>();
        
        logger.info("MetricsAggregator initialized with " + aggregationIntervalSeconds + "s interval");
    }
    
    /**
     * Set banking-related metrics (optional, injected later)
     */
    public void setBankingMetrics(ComplianceMetrics compliance, ReserveMetrics reserve, GovernanceMetrics governance) {
        this.complianceMetrics = compliance;
        this.reserveMetrics = reserve;
        this.governanceMetrics = governance;
        logger.info("Banking metrics injected into aggregator");
    }
    
    /**
     * Start periodic aggregation
     */
    public void startAggregation() {
        scheduler.scheduleAtFixedRate(
            this::aggregateMetrics,
            0,
            aggregationIntervalSeconds,
            TimeUnit.SECONDS
        );
        logger.info("Started periodic metrics aggregation");
    }
    
    /**
     * Aggregate all metrics into unified snapshot
     */
    public AggregatedSnapshot aggregateMetrics() {
        try {
            long timestamp = System.currentTimeMillis();
            
            // Blockchain metrics
            BlockchainStats blockchainStats = new BlockchainStats(
                blockchainMetrics.getTotalBlocks(),
                blockchainMetrics.getTotalTransactions(),
                blockchainMetrics.getAverageBlockTime()
            );
            
            // Consensus metrics
            ConsensusStats consensusStats = new ConsensusStats(
                consensusMetrics.getBlockHeight(),
                consensusMetrics.getTotalTransactions(),
                consensusMetrics.getAverageBlockTime(),
                consensusMetrics.getValidatorMetrics()
            );
            
            // Network metrics
            NetworkStats networkStats = new NetworkStats(
                networkMetrics.getTotalPeers(),
                networkMetrics.getMessagesReceived(),
                networkMetrics.getMessagesSent(),
                networkMetrics.getBytesReceived(),
                networkMetrics.getBytesSent(),
                calculateAverageLatency(networkMetrics.getPeerLatencies())
            );
            
            // Scaling metrics
            ScalingMetrics.PerformanceSnapshot scalingSnapshot = scalingMetrics.captureSnapshot();
            ScalingStats scalingStats = new ScalingStats(
                scalingSnapshot.getCurrentTPS(),
                scalingSnapshot.getTotalTransactionsProcessed(),
                scalingSnapshot.getPendingCrossShardTransactions(),
                scalingSnapshot.getTotalRollupBatches()
            );
            
            // Sovereignty metrics
            SovereigntyEnforcer.SovereigntyReport sovereigntyReport = 
                sovereigntyEnforcer.getComplianceReport();
            SovereigntyStats sovereigntyStats = new SovereigntyStats(
                sovereigntyReport.getCommunityOwnershipPercentage(),
                sovereigntyReport.getCloudDependencyPercentage(),
                sovereigntyEnforcer.getMaxCountryControl(),
                sovereigntyEnforcer.getMaxRegionControl(),
                sovereigntyReport.isCompliant()
            );
            
            // Integration metrics
            IntegrationMetrics.MetricsReport integrationReport = integrationMetrics.getReport();
            IntegrationStats integrationStats = new IntegrationStats(
                integrationReport.getPendingOperations(),
                integrationReport.getSuccessfulOperations(),
                integrationReport.getFailedOperations(),
                integrationReport.getSuccessRate()
            );
            
            // Banking metrics (if available)
            ComplianceStats complianceStats = complianceMetrics != null ?
                complianceMetrics.getStats() : new ComplianceStats(0, 0, 0);
            
            ReserveStats reserveStats = reserveMetrics != null ?
                reserveMetrics.getStats() : new ReserveStats(0, 0, 0);
            
            GovernanceStats governanceStats = governanceMetrics != null ?
                governanceMetrics.getStats() : new GovernanceStats(0, 0, 0);
            
            // Create aggregated snapshot
            AggregatedSnapshot snapshot = new AggregatedSnapshot(
                timestamp,
                blockchainStats,
                consensusStats,
                networkStats,
                scalingStats,
                sovereigntyStats,
                integrationStats,
                complianceStats,
                reserveStats,
                governanceStats
            );
            
            // Cache and store
            synchronized (snapshotLock) {
                lastSnapshot = snapshot;
            }
            
            snapshotHistory.offer(snapshot);
            while (snapshotHistory.size() > MAX_HISTORY_SIZE) {
                snapshotHistory.poll();
            }
            
            logger.fine("Metrics aggregated successfully");
            return snapshot;
            
        } catch (Exception e) {
            logger.severe("Error aggregating metrics: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Calculate average latency from peer latencies
     */
    private double calculateAverageLatency(Map<String, Long> peerLatencies) {
        if (peerLatencies.isEmpty()) {
            return 0.0;
        }
        
        long sum = peerLatencies.values().stream()
            .mapToLong(Long::longValue)
            .sum();
        
        return (double) sum / peerLatencies.size();
    }
    
    /**
     * Get latest aggregated snapshot
     */
    public AggregatedSnapshot getLatestSnapshot() {
        synchronized (snapshotLock) {
            return lastSnapshot;
        }
    }
    
    /**
     * Get snapshot history
     */
    public List<AggregatedSnapshot> getSnapshotHistory(int limit) {
        List<AggregatedSnapshot> history = new ArrayList<>();
        Iterator<AggregatedSnapshot> iterator = snapshotHistory.iterator();
        
        int count = 0;
        while (iterator.hasNext() && count < limit) {
            history.add(iterator.next());
            count++;
        }
        
        return history;
    }
    
    /**
     * Get time-series data for specific metric
     */
    public List<TimeSeriesPoint> getTimeSeries(String metricName, int durationMinutes) {
        List<TimeSeriesPoint> timeSeries = new ArrayList<>();
        long cutoffTime = System.currentTimeMillis() - (durationMinutes * 60 * 1000);
        
        for (AggregatedSnapshot snapshot : snapshotHistory) {
            if (snapshot.getTimestamp() >= cutoffTime) {
                double value = extractMetricValue(snapshot, metricName);
                timeSeries.add(new TimeSeriesPoint(snapshot.getTimestamp(), value));
            }
        }
        
        return timeSeries;
    }
    
    /**
     * Extract specific metric value from snapshot
     */
    private double extractMetricValue(AggregatedSnapshot snapshot, String metricName) {
        switch (metricName) {
            case "tps": return snapshot.getScalingStats().getCurrentTPS();
            case "block_height": return snapshot.getBlockchainStats().getTotalBlocks();
            case "total_peers": return snapshot.getNetworkStats().getTotalPeers();
            case "community_ownership": return snapshot.getSovereigntyStats().getCommunityOwnership() * 100;
            case "success_rate": return snapshot.getIntegrationStats().getSuccessRate() * 100;
            default: return 0.0;
        }
    }
    
    /**
     * Shutdown aggregator
     */
    public void shutdown() {
        logger.info("Shutting down MetricsAggregator");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Aggregated snapshot of all metrics
     */
    public static class AggregatedSnapshot {
        private final long timestamp;
        private final BlockchainStats blockchainStats;
        private final ConsensusStats consensusStats;
        private final NetworkStats networkStats;
        private final ScalingStats scalingStats;
        private final SovereigntyStats sovereigntyStats;
        private final IntegrationStats integrationStats;
        private final ComplianceStats complianceStats;
        private final ReserveStats reserveStats;
        private final GovernanceStats governanceStats;
        
        public AggregatedSnapshot(long timestamp, BlockchainStats blockchain, ConsensusStats consensus,
                                 NetworkStats network, ScalingStats scaling, SovereigntyStats sovereignty,
                                 IntegrationStats integration, ComplianceStats compliance,
                                 ReserveStats reserve, GovernanceStats governance) {
            this.timestamp = timestamp;
            this.blockchainStats = blockchain;
            this.consensusStats = consensus;
            this.networkStats = network;
            this.scalingStats = scaling;
            this.sovereigntyStats = sovereignty;
            this.integrationStats = integration;
            this.complianceStats = compliance;
            this.reserveStats = reserve;
            this.governanceStats = governance;
        }
        
        public long getTimestamp() { return timestamp; }
        public BlockchainStats getBlockchainStats() { return blockchainStats; }
        public ConsensusStats getConsensusStats() { return consensusStats; }
        public NetworkStats getNetworkStats() { return networkStats; }
        public ScalingStats getScalingStats() { return scalingStats; }
        public SovereigntyStats getSovereigntyStats() { return sovereigntyStats; }
        public IntegrationStats getIntegrationStats() { return integrationStats; }
        public ComplianceStats getComplianceStats() { return complianceStats; }
        public ReserveStats getReserveStats() { return reserveStats; }
        public GovernanceStats getGovernanceStats() { return governanceStats; }
    }
    
    // Stats classes
    public static class BlockchainStats {
        private final int totalBlocks;
        private final int totalTransactions;
        private final long averageBlockTime;
        
        public BlockchainStats(int totalBlocks, int totalTransactions, long averageBlockTime) {
            this.totalBlocks = totalBlocks;
            this.totalTransactions = totalTransactions;
            this.averageBlockTime = averageBlockTime;
        }
        
        public int getTotalBlocks() { return totalBlocks; }
        public int getTotalTransactions() { return totalTransactions; }
        public long getAverageBlockTime() { return averageBlockTime; }
    }
    
    public static class ConsensusStats {
        private final long blockHeight;
        private final long totalTransactions;
        private final long averageBlockTime;
        private final Map<String, Long> validatorMetrics;
        
        public ConsensusStats(long blockHeight, long totalTransactions, long averageBlockTime,
                            Map<String, Long> validatorMetrics) {
            this.blockHeight = blockHeight;
            this.totalTransactions = totalTransactions;
            this.averageBlockTime = averageBlockTime;
            this.validatorMetrics = validatorMetrics;
        }
        
        public long getBlockHeight() { return blockHeight; }
        public long getTotalTransactions() { return totalTransactions; }
        public long getAverageBlockTime() { return averageBlockTime; }
        public Map<String, Long> getValidatorMetrics() { return validatorMetrics; }
    }
    
    public static class NetworkStats {
        private final long totalPeers;
        private final long messagesReceived;
        private final long messagesSent;
        private final long bytesReceived;
        private final long bytesSent;
        private final double averageLatency;
        
        public NetworkStats(long totalPeers, long messagesReceived, long messagesSent,
                          long bytesReceived, long bytesSent, double averageLatency) {
            this.totalPeers = totalPeers;
            this.messagesReceived = messagesReceived;
            this.messagesSent = messagesSent;
            this.bytesReceived = bytesReceived;
            this.bytesSent = bytesSent;
            this.averageLatency = averageLatency;
        }
        
        public long getTotalPeers() { return totalPeers; }
        public long getMessagesReceived() { return messagesReceived; }
        public long getMessagesSent() { return messagesSent; }
        public long getBytesReceived() { return bytesReceived; }
        public long getBytesSent() { return bytesSent; }
        public double getAverageLatency() { return averageLatency; }
    }
    
    public static class ScalingStats {
        private final double currentTPS;
        private final long totalTransactionsProcessed;
        private final int pendingCrossShardTx;
        private final long totalRollupBatches;
        
        public ScalingStats(double currentTPS, long totalTxProcessed, int pendingCrossShardTx, long rollupBatches) {
            this.currentTPS = currentTPS;
            this.totalTransactionsProcessed = totalTxProcessed;
            this.pendingCrossShardTx = pendingCrossShardTx;
            this.totalRollupBatches = rollupBatches;
        }
        
        public double getCurrentTPS() { return currentTPS; }
        public long getTotalTransactionsProcessed() { return totalTransactionsProcessed; }
        public int getPendingCrossShardTx() { return pendingCrossShardTx; }
        public long getTotalRollupBatches() { return totalRollupBatches; }
    }
    
    public static class SovereigntyStats {
        private final double communityOwnership;
        private final double cloudDependency;
        private final double maxCountryControl;
        private final double maxRegionControl;
        private final boolean compliant;
        
        public SovereigntyStats(double communityOwnership, double cloudDependency,
                              double maxCountryControl, double maxRegionControl, boolean compliant) {
            this.communityOwnership = communityOwnership;
            this.cloudDependency = cloudDependency;
            this.maxCountryControl = maxCountryControl;
            this.maxRegionControl = maxRegionControl;
            this.compliant = compliant;
        }
        
        public double getCommunityOwnership() { return communityOwnership; }
        public double getCloudDependency() { return cloudDependency; }
        public double getMaxCountryControl() { return maxCountryControl; }
        public double getMaxRegionControl() { return maxRegionControl; }
        public boolean isCompliant() { return compliant; }
    }
    
    public static class IntegrationStats {
        private final long pendingOperations;
        private final long successfulOperations;
        private final long failedOperations;
        private final double successRate;
        
        public IntegrationStats(long pending, long successful, long failed, double successRate) {
            this.pendingOperations = pending;
            this.successfulOperations = successful;
            this.failedOperations = failed;
            this.successRate = successRate;
        }
        
        public long getPendingOperations() { return pendingOperations; }
        public long getSuccessfulOperations() { return successfulOperations; }
        public long getFailedOperations() { return failedOperations; }
        public double getSuccessRate() { return successRate; }
    }
    
    public static class ComplianceStats {
        private final long totalScreenings;
        private final long flaggedTransactions;
        private final long complianceReports;
        
        public ComplianceStats(long screenings, long flagged, long reports) {
            this.totalScreenings = screenings;
            this.flaggedTransactions = flagged;
            this.complianceReports = reports;
        }
        
        public long getTotalScreenings() { return totalScreenings; }
        public long getFlaggedTransactions() { return flaggedTransactions; }
        public long getComplianceReports() { return complianceReports; }
    }
    
    public static class ReserveStats {
        private final long totalReserves;
        private final long auditsCompleted;
        private final long treasuryBalance;
        
        public ReserveStats(long reserves, long audits, long treasury) {
            this.totalReserves = reserves;
            this.auditsCompleted = audits;
            this.treasuryBalance = treasury;
        }
        
        public long getTotalReserves() { return totalReserves; }
        public long getAuditsCompleted() { return auditsCompleted; }
        public long getTreasuryBalance() { return treasuryBalance; }
    }
    
    public static class GovernanceStats {
        private final long activeProposals;
        private final long completedVotes;
        private final long totalParticipants;
        
        public GovernanceStats(long proposals, long votes, long participants) {
            this.activeProposals = proposals;
            this.completedVotes = votes;
            this.totalParticipants = participants;
        }
        
        public long getActiveProposals() { return activeProposals; }
        public long getCompletedVotes() { return completedVotes; }
        public long getTotalParticipants() { return totalParticipants; }
    }
    
    public static class TimeSeriesPoint {
        private final long timestamp;
        private final double value;
        
        public TimeSeriesPoint(long timestamp, double value) {
            this.timestamp = timestamp;
            this.value = value;
        }
        
        public long getTimestamp() { return timestamp; }
        public double getValue() { return value; }
    }
    
    // Placeholder interfaces for banking metrics
    public interface ComplianceMetrics {
        ComplianceStats getStats();
    }
    
    public interface ReserveMetrics {
        ReserveStats getStats();
    }
    
    public interface GovernanceMetrics {
        GovernanceStats getStats();
    }
}
