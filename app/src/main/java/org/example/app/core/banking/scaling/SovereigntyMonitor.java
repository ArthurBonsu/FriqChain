package org.example.app.core.sovereignty;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * SOVEREIGNTY MONITOR
 * 
 * Continuously monitors and enforces African sovereignty rules:
 * - Community node percentage (>= 50%)
 * - Cloud provider limits (<= 20%)
 * - Geographic distribution (max 33% per country)
 * - Entity concentration (max 33% per entity)
 * - DA committee operational status
 * - Emergency trigger detection
 * 
 * Actions:
 * - Alert on sovereignty violations
 * - Automatic circuit breakers
 * - Emergency protocol activation
 * - Audit logging
 */
public class SovereigntyMonitor {
    private static final Logger LOGGER = Logger.getLogger(SovereigntyMonitor.class.getName());
    
    private final CommunityNodeRegistry nodeRegistry;
    private final DataAvailabilityCommittee daCommittee;
    private final EmergencyProtocol emergencyProtocol;
    
    // Monitoring
    private final ScheduledExecutorService monitorScheduler;
    private final List<SovereigntyAlert> activeAlerts;
    private final ReadWriteLock alertLock;
    
    // Metrics
    private final AtomicLong totalChecks;
    private final AtomicLong violationsDetected;
    private final AtomicLong emergenciesTriggered;
    
    // Configuration
    private volatile boolean monitoringEnabled;
    private static final long CHECK_INTERVAL_MS = 60000; // 1 minute
    
    /**
     * Constructor
     */
    public SovereigntyMonitor(CommunityNodeRegistry nodeRegistry,
                             DataAvailabilityCommittee daCommittee,
                             EmergencyProtocol emergencyProtocol) {
        this.nodeRegistry = nodeRegistry;
        this.daCommittee = daCommittee;
        this.emergencyProtocol = emergencyProtocol;
        
        this.monitorScheduler = Executors.newScheduledThreadPool(1,
            r -> new Thread(r, "Sovereignty-Monitor"));
        
        this.activeAlerts = new CopyOnWriteArrayList<>();
        this.alertLock = new ReentrantReadWriteLock();
        
        this.totalChecks = new AtomicLong(0);
        this.violationsDetected = new AtomicLong(0);
        this.emergenciesTriggered = new AtomicLong(0);
        
        this.monitoringEnabled = true;
        
        startMonitoring();
        
        LOGGER.info("Sovereignty Monitor started");
    }
    
    /**
     * Start continuous monitoring
     */
    private void startMonitoring() {
        monitorScheduler.scheduleAtFixedRate(() -> {
            try {
                if (monitoringEnabled) {
                    performSovereigntyCheck();
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Sovereignty check failed", e);
            }
        }, CHECK_INTERVAL_MS, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Perform comprehensive sovereignty check
     */
    private void performSovereigntyCheck() {
        totalChecks.incrementAndGet();
        
        // Check node distribution
        CommunityNodeRegistry.SovereigntyMetrics metrics = nodeRegistry.calculateMetrics();
        
        // Check community percentage
        if (metrics.getCommunityPercentage() < 0.50) {
            raiseAlert(new SovereigntyAlert(
                AlertLevel.CRITICAL,
                "Community node percentage below 50%: " + 
                String.format("%.1f%%", metrics.getCommunityPercentage() * 100),
                AlertType.INSUFFICIENT_COMMUNITY
            ));
        }
        
        // Check cloud percentage
        if (metrics.getCloudPercentage() > 0.20) {
            raiseAlert(new SovereigntyAlert(
                AlertLevel.CRITICAL,
                "Cloud node percentage exceeds 20%: " + 
                String.format("%.1f%%", metrics.getCloudPercentage() * 100),
                AlertType.EXCESSIVE_CLOUD
            ));
        }
        
        // Check country concentration
        if (metrics.getMaxCountryPercentage() > 0.33) {
            raiseAlert(new SovereigntyAlert(
                AlertLevel.WARNING,
                "Country concentration exceeds 33%: " + 
                String.format("%.1f%%", metrics.getMaxCountryPercentage() * 100),
                AlertType.COUNTRY_CONCENTRATION
            ));
        }
        
        // Check entity concentration
        if (metrics.getMaxEntityPercentage() > 0.33) {
            raiseAlert(new SovereigntyAlert(
                AlertLevel.WARNING,
                "Entity concentration exceeds 33%: " + 
                String.format("%.1f%%", metrics.getMaxEntityPercentage() * 100),
                AlertType.ENTITY_CONCENTRATION
            ));
        }
        
        // Check DA committee status
        if (!daCommittee.isOperational()) {
            raiseAlert(new SovereigntyAlert(
                AlertLevel.CRITICAL,
                "Data Availability Committee not operational",
                AlertType.DA_COMMITTEE_FAILURE
            ));
            
            // Trigger emergency if DA committee fails
            emergencyProtocol.evaluateEmergency(EmergencyProtocol.EmergencyType.DA_COMMITTEE_FAILURE);
        }
        
        // Clear resolved alerts
        clearResolvedAlerts(metrics);
    }
    
    /**
     * Raise sovereignty alert
     */
    private void raiseAlert(SovereigntyAlert alert) {
        alertLock.writeLock().lock();
        try {
            // Check if similar alert already active
            boolean exists = activeAlerts.stream()
                .anyMatch(a -> a.getType() == alert.getType() && 
                              a.getLevel() == alert.getLevel());
            
            if (!exists) {
                activeAlerts.add(alert);
                violationsDetected.incrementAndGet();
                
                LOGGER.log(
                    alert.getLevel() == AlertLevel.CRITICAL ? Level.SEVERE : Level.WARNING,
                    String.format("SOVEREIGNTY ALERT: [%s] %s",
                        alert.getLevel(), alert.getMessage())
                );
            }
        } finally {
            alertLock.writeLock().unlock();
        }
    }
    
    /**
     * Clear resolved alerts
     */
    private void clearResolvedAlerts(CommunityNodeRegistry.SovereigntyMetrics metrics) {
        alertLock.writeLock().lock();
        try {
            activeAlerts.removeIf(alert -> {
                switch (alert.getType()) {
                    case INSUFFICIENT_COMMUNITY:
                        return metrics.getCommunityPercentage() >= 0.50;
                    case EXCESSIVE_CLOUD:
                        return metrics.getCloudPercentage() <= 0.20;
                    case COUNTRY_CONCENTRATION:
                        return metrics.getMaxCountryPercentage() <= 0.33;
                    case ENTITY_CONCENTRATION:
                        return metrics.getMaxEntityPercentage() <= 0.33;
                    case DA_COMMITTEE_FAILURE:
                        return daCommittee.isOperational();
                    default:
                        return false;
                }
            });
        } finally {
            alertLock.writeLock().unlock();
        }
    }
    
    /**
     * Get active alerts
     */
    public List<SovereigntyAlert> getActiveAlerts() {
        alertLock.readLock().lock();
        try {
            return new ArrayList<>(activeAlerts);
        } finally {
            alertLock.readLock().unlock();
        }
    }
    
    /**
     * Get monitoring statistics
     */
    public MonitorStatistics getStatistics() {
        return new MonitorStatistics(
            totalChecks.get(),
            violationsDetected.get(),
            emergenciesTriggered.get(),
            activeAlerts.size(),
            monitoringEnabled
        );
    }
    
    /**
     * Shutdown monitor
     */
    public void shutdown() {
        monitoringEnabled = false;
        monitorScheduler.shutdown();
        
        try {
            if (!monitorScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                monitorScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitorScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        LOGGER.info("Sovereignty Monitor shut down");
    }
    
    // Inner classes
    
    public enum AlertLevel {
        WARNING,    // Non-critical violation
        CRITICAL    // Critical sovereignty violation
    }
    
    public enum AlertType {
        INSUFFICIENT_COMMUNITY,
        EXCESSIVE_CLOUD,
        COUNTRY_CONCENTRATION,
        ENTITY_CONCENTRATION,
        DA_COMMITTEE_FAILURE
    }
    
    public static class SovereigntyAlert {
        private final AlertLevel level;
        private final String message;
        private final AlertType type;
        private final long timestamp;
        
        public SovereigntyAlert(AlertLevel level, String message, AlertType type) {
            this.level = level;
            this.message = message;
            this.type = type;
            this.timestamp = System.currentTimeMillis();
        }
        
        public AlertLevel getLevel() { return level; }
        public String getMessage() { return message; }
        public AlertType getType() { return type; }
        public long getTimestamp() { return timestamp; }
    }
    
    public static class MonitorStatistics {
        private final long totalChecks;
        private final long violationsDetected;
        private final long emergenciesTriggered;
        private final int activeAlerts;
        private final boolean monitoringEnabled;
        
        public MonitorStatistics(long totalChecks, long violationsDetected,
                                long emergenciesTriggered, int activeAlerts,
                                boolean monitoringEnabled) {
            this.totalChecks = totalChecks;
            this.violationsDetected = violationsDetected;
            this.emergenciesTriggered = emergenciesTriggered;
            this.activeAlerts = activeAlerts;
            this.monitoringEnabled = monitoringEnabled;
        }
        
        public long getTotalChecks() { return totalChecks; }
        public long getViolationsDetected() { return violationsDetected; }
        public long getEmergenciesTriggered() { return emergenciesTriggered; }
        public int getActiveAlerts() { return activeAlerts; }
        public boolean isMonitoringEnabled() { return monitoringEnabled; }
        
        @Override
        public String toString() {
            return String.format(
                "MonitorStats[checks=%d, violations=%d, alerts=%d, enabled=%s]",
                totalChecks, violationsDetected, activeAlerts, monitoringEnabled
            );
        }
    }
}