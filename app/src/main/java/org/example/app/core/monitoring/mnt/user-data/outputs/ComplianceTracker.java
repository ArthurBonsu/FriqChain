package org.example.app.core.monitoring;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * ComplianceTracker monitors AML/KYC compliance and generates alerts.
 * 
 * Integrates with:
 * - AMLRiskEngine for risk scoring
 * - ComplianceReportGenerator for regulatory reports
 * - Sovereignty requirements monitoring
 */
public class ComplianceTracker {
    private static final Logger logger = Logger.getLogger(ComplianceTracker.class.getName());
    
    private final AtomicLong totalScreenings;
    private final AtomicLong highRiskDetections;
    private final AtomicLong complianceReportsGenerated;
    private final ConcurrentLinkedQueue<ComplianceEvent> recentEvents;
    
    public ComplianceTracker() {
        this.totalScreenings = new AtomicLong(0);
        this.highRiskDetections = new AtomicLong(0);
        this.complianceReportsGenerated = new AtomicLong(0);
        this.recentEvents = new ConcurrentLinkedQueue<>();
        logger.info("ComplianceTracker initialized");
    }
    
    public void recordScreening(String transactionId, int riskScore) {
        totalScreenings.incrementAndGet();
        
        if (riskScore > 70) {
            highRiskDetections.incrementAndGet();
            recentEvents.offer(new ComplianceEvent("HIGH_RISK", transactionId, riskScore));
        }
        
        while (recentEvents.size() > 1000) {
            recentEvents.poll();
        }
    }
    
    public void recordReportGeneration() {
        complianceReportsGenerated.incrementAndGet();
    }
    
    public ComplianceStats getStats() {
        return new ComplianceStats(
            totalScreenings.get(),
            highRiskDetections.get(),
            complianceReportsGenerated.get()
        );
    }
    
    public List<ComplianceEvent> getRecentHighRiskEvents(int limit) {
        List<ComplianceEvent> events = new ArrayList<>();
        for (ComplianceEvent event : recentEvents) {
            if (events.size() >= limit) break;
            events.add(event);
        }
        return events;
    }
    
    public static class ComplianceEvent {
        private final String type;
        private final String transactionId;
        private final int riskScore;
        private final long timestamp;
        
        public ComplianceEvent(String type, String txId, int riskScore) {
            this.type = type;
            this.transactionId = txId;
            this.riskScore = riskScore;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getType() { return type; }
        public String getTransactionId() { return transactionId; }
        public int getRiskScore() { return riskScore; }
        public long getTimestamp() { return timestamp; }
    }
    
    public static class ComplianceStats {
        private final long totalScreenings;
        private final long highRiskDetections;
        private final long reportsGenerated;
        
        public ComplianceStats(long screenings, long highRisk, long reports) {
            this.totalScreenings = screenings;
            this.highRiskDetections = highRisk;
            this.reportsGenerated = reports;
        }
        
        public long getTotalScreenings() { return totalScreenings; }
        public long getHighRiskDetections() { return highRiskDetections; }
        public long getReportsGenerated() { return reportsGenerated; }
        
        public double getHighRiskRate() {
            return totalScreenings > 0 ? (double) highRiskDetections / totalScreenings : 0.0;
        }
    }
}
