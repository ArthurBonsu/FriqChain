package org.example.app.core.banking.compliance;

import org.example.app.core.types.Address;
import java.util.*;
import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * AMLRiskEngine - Anti-Money Laundering risk assessment and monitoring
 * 
 * Implements multilayered AML compliance:
 * - Risk scoring (0-100 scale)
 * - Transaction pattern analysis
 * - Suspicious activity detection (SUSPICIOUS ACTIVITY REPORTS - SARs)
 * - Sanctions list checking
 * - Transaction monitoring thresholds
 * - Risk categorization (LOW, MEDIUM, HIGH, CRITICAL)
 */
public class AMLRiskEngine {
    private static final Logger LOGGER = Logger.getLogger(AMLRiskEngine.class.getName());
    
    private final Map<String, RiskProfile> riskProfiles; // Address -> RiskProfile
    private final Map<String, TransactionAlert> alerts; // Alert ID -> Alert
    private final Map<String, SuspiciousActivityReport> sars; // SAR ID -> SAR
    private final Set<String> sanctionsList; // Known sanctioned entities
    private final Map<String, RiskIndicator> riskIndicators;
    
    public enum RiskLevel {
        LOW,        // 0-25: Normal activity
        MEDIUM,     // 25-50: Some concerns
        HIGH,       // 50-75: Significant risk
        CRITICAL    // 75-100: Immediate action required
    }
    
    public enum AlertType {
        LARGE_TRANSACTION,           // >threshold amount
        RAPID_MOVEMENT,              // Multiple transactions in short time
        UNUSUAL_COUNTRY,             // Transaction to high-risk jurisdiction
        SANCTIONED_ENTITY,           // Blocked entity match
        STRUCTURING,                 // Suspicious pattern (splitting amounts)
        CASH_INTENSIVE,              // Business with high cash volume
        PEP_INVOLVEMENT,             // Politically Exposed Person
        SOURCE_OF_FUNDS_UNCLEAR,     // Can't verify source
        DESTINATION_RISK,            // High-risk destination
        ACTIVITY_SPIKE               // Sudden increase in activity
    }
    
    public static class RiskProfile {
        private final Address address;
        private final String entityName;
        private double riskScore; // 0-100
        private RiskLevel riskLevel;
        private final long createdTime;
        private long lastUpdateTime;
        private final List<String> alertIds;
        private final Map<String, Integer> indicatorCounts; // Type -> count
        private BigDecimal totalMonthlyVolume;
        private BigDecimal averageTransactionSize;
        private boolean isOnSanctionsList;
        private final List<String> flaggedCountries;
        private String lastReviewedBy;
        private long lastReviewTime;
        
        public RiskProfile(Address address, String entityName) {
            this.address = address;
            this.entityName = entityName;
            this.riskScore = 25.0; // Default medium-low
            this.riskLevel = RiskLevel.LOW;
            this.createdTime = System.currentTimeMillis();
            this.lastUpdateTime = createdTime;
            this.alertIds = new ArrayList<>();
            this.indicatorCounts = new ConcurrentHashMap<>();
            this.totalMonthlyVolume = BigDecimal.ZERO;
            this.averageTransactionSize = BigDecimal.ZERO;
            this.isOnSanctionsList = false;
            this.flaggedCountries = new ArrayList<>();
        }
        
        // Getters
        public Address getAddress() { return address; }
        public String getEntityName() { return entityName; }
        public double getRiskScore() { return riskScore; }
        public RiskLevel getRiskLevel() { return riskLevel; }
        public long getCreatedTime() { return createdTime; }
        public List<String> getAlertIds() { return new ArrayList<>(alertIds); }
        public boolean isOnSanctionsList() { return isOnSanctionsList; }
        public BigDecimal getTotalMonthlyVolume() { return totalMonthlyVolume; }
        
        // Setters
        public void setRiskScore(double score) {
            this.riskScore = Math.max(0, Math.min(100, score));
            updateRiskLevel();
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        private void updateRiskLevel() {
            if (riskScore < 25) {
                riskLevel = RiskLevel.LOW;
            } else if (riskScore < 50) {
                riskLevel = RiskLevel.MEDIUM;
            } else if (riskScore < 75) {
                riskLevel = RiskLevel.HIGH;
            } else {
                riskLevel = RiskLevel.CRITICAL;
            }
        }
        
        public void addAlert(String alertId) {
            alertIds.add(alertId);
            // Each alert increases risk by 5 points
            setRiskScore(riskScore + 5);
        }
        
        public void addIndicator(String type) {
            indicatorCounts.merge(type, 1, Integer::sum);
            // Multiple indicators increase risk
            setRiskScore(riskScore + 2);
        }
        
        public void setOnSanctionsList(boolean sanctioned) {
            this.isOnSanctionsList = sanctioned;
            if (sanctioned) {
                setRiskScore(100.0); // Immediate critical
            }
        }
        
        public void addFlaggedCountry(String country) {
            if (!flaggedCountries.contains(country)) {
                flaggedCountries.add(country);
                setRiskScore(riskScore + 10);
            }
        }
    }
    
    public static class TransactionAlert {
        private final String alertId;
        private final Address source;
        private final Address destination;
        private final BigDecimal amount;
        private final AlertType type;
        private final double riskIncrease;
        private final long alertTime;
        private final String description;
        private boolean reviewed;
        private String reviewedBy;
        private String reviewNotes;
        
        public TransactionAlert(String alertId, Address source, Address destination,
                               BigDecimal amount, AlertType type, double riskIncrease,
                               String description) {
            this.alertId = alertId;
            this.source = source;
            this.destination = destination;
            this.amount = amount;
            this.type = type;
            this.riskIncrease = riskIncrease;
            this.alertTime = System.currentTimeMillis();
            this.description = description;
            this.reviewed = false;
        }
        
        // Getters
        public String getAlertId() { return alertId; }
        public Address getSource() { return source; }
        public AlertType getType() { return type; }
        public BigDecimal getAmount() { return amount; }
        public double getRiskIncrease() { return riskIncrease; }
        public long getAlertTime() { return alertTime; }
        public boolean isReviewed() { return reviewed; }
        
        public void markReviewed(String reviewer, String notes) {
            this.reviewed = true;
            this.reviewedBy = reviewer;
            this.reviewNotes = notes;
        }
    }
    
    public static class SuspiciousActivityReport {
        private final String sarId;
        private final Address reportedAddress;
        private final String entityName;
        private final String reason;
        private final long reportedTime;
        private final String reportedBy;
        private final List<String> supportingAlerts;
        private String sarStatus; // "FILED", "UNDER_REVIEW", "ESCALATED"
        private String investigationNotes;
        private boolean escalatedToAuthorities;
        
        public SuspiciousActivityReport(String sarId, Address address, String entityName,
                                      String reason, String reportedBy) {
            this.sarId = sarId;
            this.reportedAddress = address;
            this.entityName = entityName;
            this.reason = reason;
            this.reportedTime = System.currentTimeMillis();
            this.reportedBy = reportedBy;
            this.supportingAlerts = new ArrayList<>();
            this.sarStatus = "FILED";
            this.escalatedToAuthorities = false;
        }
        
        // Getters
        public String getSarId() { return sarId; }
        public Address getReportedAddress() { return reportedAddress; }
        public String getEntityName() { return entityName; }
        public String getReason() { return reason; }
        public long getReportedTime() { return reportedTime; }
        public String getStatus() { return sarStatus; }
        public boolean isEscalatedToAuthorities() { return escalatedToAuthorities; }
        
        public void addSupportingAlert(String alertId) { supportingAlerts.add(alertId); }
        public void escalate(String notes) {
            sarStatus = "ESCALATED";
            investigationNotes = notes;
            escalatedToAuthorities = true;
            LOGGER.warning("SAR escalated to authorities: " + sarId);
        }
    }
    
    public static class RiskIndicator {
        private final String indicatorName;
        private final double riskWeight; // How much this indicator affects score
        private final AlertType triggerAlert;
        private long lastTriggeredTime;
        private int triggerCount;
        
        public RiskIndicator(String name, double weight, AlertType alert) {
            this.indicatorName = name;
            this.riskWeight = weight;
            this.triggerAlert = alert;
            this.lastTriggeredTime = 0;
            this.triggerCount = 0;
        }
        
        public void trigger() {
            this.lastTriggeredTime = System.currentTimeMillis();
            this.triggerCount++;
        }
        
        public String getIndicatorName() { return indicatorName; }
        public double getRiskWeight() { return riskWeight; }
        public AlertType getTriggerAlert() { return triggerAlert; }
        public int getTriggerCount() { return triggerCount; }
    }
    
    public AMLRiskEngine() {
        this.riskProfiles = new ConcurrentHashMap<>();
        this.alerts = new ConcurrentHashMap<>();
        this.sars = new ConcurrentHashMap<>();
        this.sanctionsList = ConcurrentHashMap.newKeySet();
        this.riskIndicators = new ConcurrentHashMap<>();
        
        // Initialize default risk indicators
        initializeRiskIndicators();
        
        LOGGER.info("AMLRiskEngine initialized");
    }
    
    /**
     * Initialize standard AML risk indicators
     */
    private void initializeRiskIndicators() {
        riskIndicators.put("LARGE_TRANSACTION",
            new RiskIndicator("Large Transaction", 10, AlertType.LARGE_TRANSACTION));
        riskIndicators.put("RAPID_MOVEMENT",
            new RiskIndicator("Rapid Fund Movement", 15, AlertType.RAPID_MOVEMENT));
        riskIndicators.put("HIGH_RISK_JURISDICTION",
            new RiskIndicator("High-Risk Jurisdiction", 20, AlertType.UNUSUAL_COUNTRY));
        riskIndicators.put("SANCTIONED_ENTITY",
            new RiskIndicator("Sanctioned Entity Match", 100, AlertType.SANCTIONED_ENTITY));
        riskIndicators.put("STRUCTURING",
            new RiskIndicator("Potential Structuring", 25, AlertType.STRUCTURING));
        riskIndicators.put("PEP",
            new RiskIndicator("Politically Exposed Person", 15, AlertType.PEP_INVOLVEMENT));
    }
    
    /**
     * Analyzes transaction and generates alerts if risk detected
     */
    public List<TransactionAlert> analyzeTransaction(Address source, Address destination,
                                                    BigDecimal amount, String sourceCountry,
                                                    String destCountry) {
        List<TransactionAlert> generatedAlerts = new ArrayList<>();
        RiskProfile sourceProfile = riskProfiles.get(source.toString());
        
        // Create profile if doesn't exist
        if (sourceProfile == null) {
            sourceProfile = new RiskProfile(source, source.toString());
            riskProfiles.put(source.toString(), sourceProfile);
        }
        
        // Check 1: Large transaction
        if (amount.compareTo(BigDecimal.valueOf(1_000_000)) > 0) {
            String alertId = UUID.randomUUID().toString();
            TransactionAlert alert = new TransactionAlert(alertId, source, destination,
                amount, AlertType.LARGE_TRANSACTION, 5.0,
                "Transaction amount exceeds $1M threshold");
            alerts.put(alertId, alert);
            generatedAlerts.add(alert);
            sourceProfile.addAlert(alertId);
        }
        
        // Check 2: High-risk destination
        if (isHighRiskJurisdiction(destCountry)) {
            String alertId = UUID.randomUUID().toString();
            TransactionAlert alert = new TransactionAlert(alertId, source, destination,
                amount, AlertType.UNUSUAL_COUNTRY, 10.0,
                "High-risk destination jurisdiction: " + destCountry);
            alerts.put(alertId, alert);
            generatedAlerts.add(alert);
            sourceProfile.addAlert(alertId);
            sourceProfile.addFlaggedCountry(destCountry);
        }
        
        // Check 3: Sanctioned entity
        if (sanctionsList.contains(destination.toString())) {
            String alertId = UUID.randomUUID().toString();
            TransactionAlert alert = new TransactionAlert(alertId, source, destination,
                amount, AlertType.SANCTIONED_ENTITY, 100.0,
                "Destination is on sanctions list");
            alerts.put(alertId, alert);
            generatedAlerts.add(alert);
            sourceProfile.addAlert(alertId);
            sourceProfile.setOnSanctionsList(true);
            
            // Immediately file SAR
            fileSuspiciousActivityReport(source, source.toString(),
                "Attempted transaction to sanctioned entity", "AMLEngine");
        }
        
        // Check 4: Structuring pattern (multiple small transactions)
        if (amount.compareTo(BigDecimal.valueOf(50_000)) > 0 &&
            amount.compareTo(BigDecimal.valueOf(100_000)) < 0) {
            // Check if multiple similar amounts in short time
            long recentTransactionCount = alerts.values().stream()
                .filter(a -> a.getSource().equals(source) &&
                           System.currentTimeMillis() - a.getAlertTime() < 3600000) // 1 hour
                .count();
            
            if (recentTransactionCount >= 3) {
                String alertId = UUID.randomUUID().toString();
                TransactionAlert alert = new TransactionAlert(alertId, source, destination,
                    amount, AlertType.STRUCTURING, 15.0,
                    "Potential structuring: multiple similar-sized transactions");
                alerts.put(alertId, alert);
                generatedAlerts.add(alert);
                sourceProfile.addAlert(alertId);
            }
        }
        
        // Update profile
        sourceProfile.setRiskScore(sourceProfile.getRiskScore());
        
        return generatedAlerts;
    }
    
    /**
     * Creates risk profile for entity
     */
    public RiskProfile createRiskProfile(Address address, String entityName) {
        RiskProfile profile = new RiskProfile(address, entityName);
        riskProfiles.put(address.toString(), profile);
        LOGGER.info("Risk profile created for: " + entityName);
        return profile;
    }
    
    /**
     * Gets risk profile
     */
    public RiskProfile getRiskProfile(Address address) {
        return riskProfiles.get(address.toString());
    }
    
    /**
     * Adds entity to sanctions list
     */
    public void addToSanctionsList(String addressOrName) {
        sanctionsList.add(addressOrName);
        LOGGER.warning("Entity added to sanctions list: " + addressOrName);
    }
    
    /**
     * Removes entity from sanctions list
     */
    public void removeFromSanctionsList(String addressOrName) {
        sanctionsList.remove(addressOrName);
        LOGGER.info("Entity removed from sanctions list: " + addressOrName);
    }
    
    /**
     * Checks if entity is on sanctions list
     */
    public boolean isOnSanctionsList(String addressOrName) {
        return sanctionsList.contains(addressOrName);
    }
    
    /**
     * Files Suspicious Activity Report (SAR)
     */
    public String fileSuspiciousActivityReport(Address address, String entityName,
                                              String reason, String reportedBy) {
        String sarId = UUID.randomUUID().toString();
        SuspiciousActivityReport sar = new SuspiciousActivityReport(sarId, address,
            entityName, reason, reportedBy);
        
        sars.put(sarId, sar);
        
        RiskProfile profile = riskProfiles.get(address.toString());
        if (profile != null) {
            profile.setRiskScore(100.0); // Immediate critical
        }
        
        LOGGER.warning("SAR filed: " + sarId + " for " + entityName + " - Reason: " + reason);
        return sarId;
    }
    
    /**
     * Gets all active alerts
     */
    public List<TransactionAlert> getActiveAlerts() {
        return alerts.values().stream()
            .filter(a -> !a.isReviewed())
            .toList();
    }
    
    /**
     * Gets all high-risk profiles
     */
    public List<RiskProfile> getHighRiskProfiles() {
        return riskProfiles.values().stream()
            .filter(p -> p.getRiskLevel() == RiskLevel.HIGH ||
                        p.getRiskLevel() == RiskLevel.CRITICAL)
            .toList();
    }
    
    /**
     * Gets all filed SARs
     */
    public Collection<SuspiciousActivityReport> getAllSARs() {
        return sars.values();
    }
    
    /**
     * Checks if jurisdiction is high-risk (simplified list)
     */
    private boolean isHighRiskJurisdiction(String country) {
        Set<String> highRiskCountries = Set.of(
            "IRAN", "SYRIA", "NORTH_KOREA", "CRIMEA",
            "HIGH_AML_RISK_JURISDICTIONS"
        );
        return highRiskCountries.contains(country.toUpperCase());
    }
    
    /**
     * Gets comprehensive AML statistics for reporting
     */
    public Map<String, Object> getAMLStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_risk_profiles", riskProfiles.size());
        stats.put("high_risk_count", getHighRiskProfiles().size());
        stats.put("active_alerts", getActiveAlerts().size());
        stats.put("filed_sars", sars.size());
        stats.put("sanctioned_entities", sanctionsList.size());
        return stats;
    }
}



