package org.example.app.core.monitoring;

import org.example.app.core.metrics.*;
import org.example.app.core.scaling.ScalingMetrics;
import org.example.app.core.scaling.SovereigntyEnforcer;
import org.example.app.core.integration.IntegrationMetrics;

import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * MonitoringCoordinator is the main entry point for the monitoring module.
 * Coordinates all monitoring components.
 */
public class MonitoringCoordinator {
    private static final Logger logger = Logger.getLogger(MonitoringCoordinator.class.getName());
    
    private final MetricsAggregator aggregator;
    private final ComplianceTracker complianceTracker;
    private final ReserveMonitor reserveMonitor;
    private final GovernanceMonitor governanceMonitor;
    private final RealTimeMonitor realTimeMonitor;
    private final AlertingEngine alertingEngine;
    private final ReportGenerator reportGenerator;
    private final PerformanceAnalyzer performanceAnalyzer;
    private final SovereigntyDashboard sovereigntyDashboard;
    private final HealthDashboard healthDashboard;
    
    private final ScheduledExecutorService scheduler;
    
    public MonitoringCoordinator(
            BlockchainMetrics blockchainMetrics,
            ConsensusMetrics consensusMetrics,
            NetworkMetrics networkMetrics,
            ScalingMetrics scalingMetrics,
            SovereigntyEnforcer sovereigntyEnforcer,
            IntegrationMetrics integrationMetrics) {
        
        // Initialize core aggregator
        this.aggregator = new MetricsAggregator(
            blockchainMetrics, consensusMetrics, networkMetrics,
            scalingMetrics, sovereigntyEnforcer, integrationMetrics, 30
        );
        
        // Initialize trackers
        this.complianceTracker = new ComplianceTracker();
        this.reserveMonitor = new ReserveMonitor();
        this.governanceMonitor = new GovernanceMonitor();
        
        // Set banking metrics on aggregator
        aggregator.setBankingMetrics(
            () -> complianceTracker.getStats(),
            () -> reserveMonitor.getStats(),
            () -> governanceMonitor.getStats()
        );
        
        // Initialize monitoring components
        this.realTimeMonitor = new RealTimeMonitor(aggregator);
        this.alertingEngine = new AlertingEngine(aggregator);
        this.reportGenerator = new ReportGenerator(aggregator, complianceTracker);
        this.performanceAnalyzer = new PerformanceAnalyzer(aggregator);
        this.sovereigntyDashboard = new SovereigntyDashboard(aggregator);
        this.healthDashboard = new HealthDashboard(aggregator, alertingEngine);
        
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        logger.info("MonitoringCoordinator initialized");
    }
    
    public void start() {
        aggregator.startAggregation();
        realTimeMonitor.start();
        
        // Schedule alert checks every 60 seconds
        scheduler.scheduleAtFixedRate(() -> {
            alertingEngine.checkAlerts();
        }, 60, 60, TimeUnit.SECONDS);
        
        logger.info("Monitoring system started");
    }
    
    public void shutdown() {
        aggregator.shutdown();
        realTimeMonitor.shutdown();
        scheduler.shutdown();
        logger.info("Monitoring system shut down");
    }
    
    // Getters
    public MetricsAggregator getAggregator() { return aggregator; }
    public ComplianceTracker getComplianceTracker() { return complianceTracker; }
    public ReserveMonitor getReserveMonitor() { return reserveMonitor; }
    public GovernanceMonitor getGovernanceMonitor() { return governanceMonitor; }
    public RealTimeMonitor getRealTimeMonitor() { return realTimeMonitor; }
    public AlertingEngine getAlertingEngine() { return alertingEngine; }
    public ReportGenerator getReportGenerator() { return reportGenerator; }
    public PerformanceAnalyzer getPerformanceAnalyzer() { return performanceAnalyzer; }
    public SovereigntyDashboard getSovereigntyDashboard() { return sovereigntyDashboard; }
    public HealthDashboard getHealthDashboard() { return healthDashboard; }
}
