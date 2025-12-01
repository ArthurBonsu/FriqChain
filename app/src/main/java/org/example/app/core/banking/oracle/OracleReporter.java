package org.example.app.core.banking.oracle;

import org.example.app.core.types.Address;
import org.example.app.core.banking.oracle.AfricanSovereigntyTypes.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * OracleReporter - Base class for all oracle reporters with African Sovereignty
 * 
 * UPGRADED FEATURES:
 * - Geographic information tracking (region, country)
 * - Node type classification (COMMUNITY/INSTITUTIONAL/CLOUD)
 * - Community ownership flag
 * - Stake requirements enforcement
 * - Submission history with geographic context
 * 
 * All oracles inherit from this base class and provide:
 * - Geographic diversity for government resistance
 * - Node type tracking for cloud provider limits
 * - Community ownership for priority incentives
 */
public abstract class OracleReporter {
    private static final Logger LOGGER = Logger.getLogger(OracleReporter.class.getName());
    
    // Reporter identity and geographic information
    protected final String reporterId;
    protected final String reporterType;
    protected final Address reporterAddress;
    protected final BigDecimal minimumStake;
    protected final AfricanRegion region; // NEW
    protected final String country; // NEW
    protected final NodeType nodeType; // NEW
    protected final boolean isCommunityOwned; // NEW
    
    // Submission tracking
    protected final Map<String, OracleData> submittedData;
    protected final List<SubmissionRecord> submissionHistory;
    protected BigDecimal currentStake;
    protected boolean isActive;
    protected long lastSubmissionTime;
    
    /**
     * Enhanced Oracle data with geographic context
     */
    public static class OracleData {
        private final String dataType;
        private final Object data;
        private final String reporterId;
        private final BigDecimal confidence;
        private final byte[] signature;
        private final long timestamp;
        private final AfricanRegion region; // NEW
        
        public OracleData(String dataType, Object data, String reporterId,
                        BigDecimal confidence, byte[] signature) {
            this.dataType = dataType;
            this.data = data;
            this.reporterId = reporterId;
            this.confidence = confidence;
            this.signature = signature;
            this.timestamp = System.currentTimeMillis();
            this.region = null; // Set by subclasses
        }
        
        public OracleData(String dataType, Object data, String reporterId,
                        BigDecimal confidence, byte[] signature, AfricanRegion region) {
            this.dataType = dataType;
            this.data = data;
            this.reporterId = reporterId;
            this.confidence = confidence;
            this.signature = signature;
            this.timestamp = System.currentTimeMillis();
            this.region = region;
        }
        
        public String getDataType() { return dataType; }
        public Object getData() { return data; }
        public String getReporterId() { return reporterId; }
        public BigDecimal getConfidence() { return confidence; }
        public byte[] getSignature() { return signature; }
        public long getTimestamp() { return timestamp; }
        public AfricanRegion getRegion() { return region; }
    }
    
    /**
     * Submission record
     */
    public static class SubmissionRecord {
        private final String dataType;
        private final String dataKey;
        private final long timestamp;
        private final boolean successful;
        private final AfricanRegion region;
        
        public SubmissionRecord(String dataType, String dataKey, boolean successful, AfricanRegion region) {
            this.dataType = dataType;
            this.dataKey = dataKey;
            this.timestamp = System.currentTimeMillis();
            this.successful = successful;
            this.region = region;
        }
        
        public String getDataType() { return dataType; }
        public String getDataKey() { return dataKey; }
        public long getTimestamp() { return timestamp; }
        public boolean isSuccessful() { return successful; }
        public AfricanRegion getRegion() { return region; }
    }
    
    /**
     * Constructor with geographic information
     */
    public OracleReporter(String reporterId, String reporterType, Address reporterAddress,
                         BigDecimal minimumStake, AfricanRegion region, String country,
                         NodeType nodeType, boolean isCommunityOwned) {
        this.reporterId = reporterId;
        this.reporterType = reporterType;
        this.reporterAddress = reporterAddress;
        this.minimumStake = minimumStake;
        this.region = region;
        this.country = country;
        this.nodeType = nodeType;
        this.isCommunityOwned = isCommunityOwned;
        
        this.submittedData = new ConcurrentHashMap<>();
        this.submissionHistory = new ArrayList<>();
        this.currentStake = BigDecimal.ZERO;
        this.isActive = true;
        this.lastSubmissionTime = 0;
        
        LOGGER.info(String.format("OracleReporter initialized: %s (%s) - Region: %s (%s) - Type: %s - Community: %s",
            reporterId, reporterType, region, country, nodeType, isCommunityOwned));
    }
    
    /**
     * Abstract method: Fetch data from source
     * Must be implemented by each oracle type
     */
    public abstract OracleData fetchData(String dataKey) throws Exception;
    
    /**
     * Submits data with geographic context
     */
    protected void submitData(String dataType, Object data, byte[] signature) {
        if (!isActive) {
            LOGGER.warning("Oracle reporter is not active: " + reporterId);
            return;
        }
        
        if (currentStake.compareTo(minimumStake) < 0) {
            LOGGER.warning("Insufficient stake: " + reporterId);
            return;
        }
        
        if (!validateDataFormat(data)) {
            LOGGER.warning("Invalid data format: " + reporterId);
            return;
        }
        
        String dataKey = getDataTypeIdentifier();
        OracleData oracleData = new OracleData(dataType, data, reporterId, 
            new BigDecimal("0.95"), signature, region);
        
        submittedData.put(dataKey, oracleData);
        lastSubmissionTime = System.currentTimeMillis();
        
        // Record submission
        SubmissionRecord record = new SubmissionRecord(dataType, dataKey, true, region);
        submissionHistory.add(record);
        
        LOGGER.fine(String.format("Data submitted: %s - Reporter: %s - Region: %s",
            dataType, reporterId, region));
    }
    
    /**
     * Abstract validation methods
     */
    protected abstract boolean validateDataFormat(Object data);
    protected abstract String getDataTypeIdentifier();
    
    /**
     * Stake management
     */
    public void addStake(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            currentStake = currentStake.add(amount);
            LOGGER.info(String.format("Stake added: %s - Amount: %s - Total: %s",
                reporterId, amount, currentStake));
        }
    }
    
    public void removeStake(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(currentStake) <= 0) {
            currentStake = currentStake.subtract(amount);
            LOGGER.info(String.format("Stake removed: %s - Amount: %s - Remaining: %s",
                reporterId, amount, currentStake));
        }
    }
    
    public void activate() {
        isActive = true;
        LOGGER.info("Oracle reporter activated: " + reporterId);
    }
    
    public void deactivate() {
        isActive = false;
        LOGGER.info("Oracle reporter deactivated: " + reporterId);
    }
    
    // Getters for geographic information
    public String getReporterId() { return reporterId; }
    public String getReporterType() { return reporterType; }
    public Address getReporterAddress() { return reporterAddress; }
    public BigDecimal getMinimumStake() { return minimumStake; }
    public AfricanRegion getRegion() { return region; }
    public String getCountry() { return country; }
    public NodeType getNodeType() { return nodeType; }
    public boolean isCommunityOwned() { return isCommunityOwned; }
    public BigDecimal getCurrentStake() { return currentStake; }
    public boolean isActive() { return isActive; }
    public long getLastSubmissionTime() { return lastSubmissionTime; }
    
    public OracleData getSubmittedData(String dataKey) {
        return submittedData.get(dataKey);
    }
    
    public List<SubmissionRecord> getSubmissionHistory() {
        return new ArrayList<>(submissionHistory);
    }
    
    public int getSubmissionCount() {
        return submissionHistory.size();
    }
    
    public int getSuccessfulSubmissions() {
        return (int) submissionHistory.stream()
            .filter(SubmissionRecord::isSuccessful)
            .count();
    }
    
    /**
     * Gets statistics with sovereignty metrics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("reporterId", reporterId);
        stats.put("reporterType", reporterType);
        stats.put("region", region);
        stats.put("country", country);
        stats.put("nodeType", nodeType);
        stats.put("isCommunityOwned", isCommunityOwned);
        stats.put("currentStake", currentStake);
        stats.put("minimumStake", minimumStake);
        stats.put("isActive", isActive);
        stats.put("totalSubmissions", submissionHistory.size());
        stats.put("successfulSubmissions", getSuccessfulSubmissions());
        stats.put("lastSubmissionTime", lastSubmissionTime);
        
        return stats;
    }
}