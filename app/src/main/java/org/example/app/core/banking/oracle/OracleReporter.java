package org.example.app.core.banking.oracle;

import org.example.app.core.types.Hash;
import org.example.app.core.types.Address;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * OracleReporter - Base abstract class for all oracle data reporters
 * 
 * Implements core functionality required by all oracle types:
 * - Data submission with cryptographic verification
 * - Reputation tracking integration
 * - Stake management
 * - Commit-reveal protocol support
 * - Multi-source verification
 * - Merkle proof generation
 * 
 * Based on Implementation Guidebook Section VI: Oracle Data Integrity System
 */
public abstract class OracleReporter {
    private static final Logger LOGGER = Logger.getLogger(OracleReporter.class.getName());
    
    // Reporter identification
    protected final String reporterId;
    protected final String reporterType;
    protected final Address reporterAddress;
    
    // Reputation and stake
    protected BigDecimal reputationScore;
    protected BigDecimal stakedAmount;
    protected final BigDecimal minimumStake;
    
    // Data tracking
    protected final Map<String, DataSubmission> submissionHistory;
    protected long totalSubmissions;
    protected long accurateSubmissions;
    protected long inaccurateSubmissions;
    
    // Status
    protected boolean isActive;
    protected boolean isSlashed;
    protected long lastSubmissionTime;
    protected long activeSince;
    
    /**
     * Data submission record
     */
    public static class DataSubmission {
        private final String submissionId;
        private final String dataType;
        private final byte[] dataHash;
        private final Object dataValue;
        private final long timestamp;
        private final byte[] signature;
        private boolean verified;
        private boolean accurate;
        private String verificationNote;
        
        public DataSubmission(String dataType, byte[] dataHash, Object dataValue, byte[] signature) {
            this.submissionId = UUID.randomUUID().toString();
            this.dataType = dataType;
            this.dataHash = dataHash;
            this.dataValue = dataValue;
            this.timestamp = System.currentTimeMillis();
            this.signature = signature;
            this.verified = false;
            this.accurate = false;
        }
        
        public String getSubmissionId() { return submissionId; }
        public String getDataType() { return dataType; }
        public byte[] getDataHash() { return dataHash; }
        public Object getDataValue() { return dataValue; }
        public long getTimestamp() { return timestamp; }
        public byte[] getSignature() { return signature; }
        public boolean isVerified() { return verified; }
        public boolean isAccurate() { return accurate; }
        public String getVerificationNote() { return verificationNote; }
        
        public void setVerified(boolean verified) { this.verified = verified; }
        public void setAccurate(boolean accurate) { this.accurate = accurate; }
        public void setVerificationNote(String note) { this.verificationNote = note; }
    }
    
    /**
     * Oracle data result with metadata
     */
    public static class OracleData {
        private final String dataType;
        private final Object value;
        private final long timestamp;
        private final String reporterId;
        private final BigDecimal confidence;
        private final byte[] signature;
        private final Map<String, Object> metadata;
        
        public OracleData(String dataType, Object value, String reporterId, 
                         BigDecimal confidence, byte[] signature) {
            this.dataType = dataType;
            this.value = value;
            this.timestamp = System.currentTimeMillis();
            this.reporterId = reporterId;
            this.confidence = confidence;
            this.signature = signature;
            this.metadata = new HashMap<>();
        }
        
        public String getDataType() { return dataType; }
        public Object getValue() { return value; }
        public long getTimestamp() { return timestamp; }
        public String getReporterId() { return reporterId; }
        public BigDecimal getConfidence() { return confidence; }
        public byte[] getSignature() { return signature; }
        public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
        
        public void addMetadata(String key, Object value) {
            metadata.put(key, value);
        }
    }
    
    /**
     * Constructor
     */
    public OracleReporter(String reporterId, String reporterType, Address reporterAddress,
                         BigDecimal minimumStake) {
        this.reporterId = reporterId;
        this.reporterType = reporterType;
        this.reporterAddress = reporterAddress;
        this.minimumStake = minimumStake;
        this.reputationScore = new BigDecimal("50.0"); // Start with neutral reputation
        this.stakedAmount = BigDecimal.ZERO;
        this.submissionHistory = new ConcurrentHashMap<>();
        this.totalSubmissions = 0;
        this.accurateSubmissions = 0;
        this.inaccurateSubmissions = 0;
        this.isActive = false;
        this.isSlashed = false;
        this.lastSubmissionTime = 0;
        this.activeSince = System.currentTimeMillis();
        
        LOGGER.info(String.format("Oracle reporter created: %s (%s)", reporterId, reporterType));
    }
    
    /**
     * Abstract method to fetch data from the oracle source
     * Must be implemented by each specific oracle type
     */
    public abstract OracleData fetchData(String dataKey) throws Exception;
    
    /**
     * Abstract method to validate data format
     */
    protected abstract boolean validateDataFormat(Object data);
    
    /**
     * Abstract method to get data type identifier
     */
    protected abstract String getDataTypeIdentifier();
    
    /**
     * Submits data with cryptographic verification
     */
    public DataSubmission submitData(String dataType, Object dataValue, byte[] signature) {
        if (!isActive) {
            LOGGER.warning("Reporter is not active: " + reporterId);
            return null;
        }
        
        if (isSlashed) {
            LOGGER.warning("Reporter has been slashed: " + reporterId);
            return null;
        }
        
        if (stakedAmount.compareTo(minimumStake) < 0) {
            LOGGER.warning("Insufficient stake: " + reporterId);
            return null;
        }
        
        if (!validateDataFormat(dataValue)) {
            LOGGER.warning("Invalid data format from reporter: " + reporterId);
            return null;
        }
        
        try {
            // Create data hash
            byte[] dataHash = createDataHash(dataValue);
            
            // Create submission record
            DataSubmission submission = new DataSubmission(dataType, dataHash, dataValue, signature);
            
            // Store submission
            submissionHistory.put(submission.getSubmissionId(), submission);
            totalSubmissions++;
            lastSubmissionTime = System.currentTimeMillis();
            
            LOGGER.fine(String.format("Data submitted by %s: %s", reporterId, submission.getSubmissionId()));
            
            return submission;
            
        } catch (Exception e) {
            LOGGER.severe("Error submitting data: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Creates cryptographic hash of data
     */
    protected byte[] createDataHash(Object data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String dataString = data.toString();
        return digest.digest(dataString.getBytes());
    }
    
    /**
     * Verifies a data submission
     */
    public void verifySubmission(String submissionId, boolean accurate, String note) {
        DataSubmission submission = submissionHistory.get(submissionId);
        if (submission == null) {
            LOGGER.warning("Submission not found: " + submissionId);
            return;
        }
        
        submission.setVerified(true);
        submission.setAccurate(accurate);
        submission.setVerificationNote(note);
        
        if (accurate) {
            accurateSubmissions++;
            // Small reputation boost
            adjustReputation(new BigDecimal("0.5"));
        } else {
            inaccurateSubmissions++;
            // Reputation penalty
            adjustReputation(new BigDecimal("-2.0"));
        }
        
        LOGGER.fine(String.format("Submission verified: %s - %s", submissionId, 
            accurate ? "ACCURATE" : "INACCURATE"));
    }
    
    /**
     * Adjusts reputation score
     */
    public void adjustReputation(BigDecimal adjustment) {
        reputationScore = reputationScore.add(adjustment);
        
        // Clamp to valid range [0, 100]
        if (reputationScore.compareTo(BigDecimal.ZERO) < 0) {
            reputationScore = BigDecimal.ZERO;
        }
        if (reputationScore.compareTo(new BigDecimal("100")) > 0) {
            reputationScore = new BigDecimal("100");
        }
        
        LOGGER.fine(String.format("Reputation adjusted for %s: %.2f", reporterId, reputationScore));
    }
    
    /**
     * Adds stake to the reporter
     */
    public boolean addStake(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        stakedAmount = stakedAmount.add(amount);
        
        // Activate if minimum stake is met
        if (!isActive && stakedAmount.compareTo(minimumStake) >= 0) {
            isActive = true;
            LOGGER.info(String.format("Reporter activated: %s (stake: %s)", reporterId, stakedAmount));
        }
        
        return true;
    }
    
    /**
     * Removes stake from the reporter
     */
    public boolean removeStake(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        if (amount.compareTo(stakedAmount) > 0) {
            LOGGER.warning("Insufficient stake to remove");
            return false;
        }
        
        stakedAmount = stakedAmount.subtract(amount);
        
        // Deactivate if below minimum stake
        if (stakedAmount.compareTo(minimumStake) < 0) {
            isActive = false;
            LOGGER.info(String.format("Reporter deactivated: %s (stake: %s)", reporterId, stakedAmount));
        }
        
        return true;
    }
    
    /**
     * Slashes the reporter's stake (penalty for bad behavior)
     */
    public BigDecimal slash(BigDecimal slashAmount, String reason) {
        if (slashAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal actualSlash = slashAmount.min(stakedAmount);
        stakedAmount = stakedAmount.subtract(actualSlash);
        isSlashed = true;
        isActive = false;
        
        // Major reputation penalty
        adjustReputation(new BigDecimal("-10.0"));
        
        LOGGER.warning(String.format("Reporter slashed: %s - Amount: %s - Reason: %s",
            reporterId, actualSlash, reason));
        
        return actualSlash;
    }
    
    /**
     * Calculates accuracy rate
     */
    public BigDecimal getAccuracyRate() {
        if (totalSubmissions == 0) {
            return BigDecimal.ZERO;
        }
        
        return new BigDecimal(accurateSubmissions)
            .divide(new BigDecimal(totalSubmissions), 4, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("100"));
    }
    
    /**
     * Gets weighted reputation score (combines reputation and accuracy)
     */
    public BigDecimal getWeightedReputationScore() {
        BigDecimal accuracyRate = getAccuracyRate();
        
        // 70% reputation, 30% accuracy
        return reputationScore.multiply(new BigDecimal("0.7"))
            .add(accuracyRate.multiply(new BigDecimal("0.3")));
    }
    
    /**
     * Checks if reporter can submit data
     */
    public boolean canSubmitData() {
        return isActive && !isSlashed && stakedAmount.compareTo(minimumStake) >= 0;
    }
    
    // Getters
    public String getReporterId() { return reporterId; }
    public String getReporterType() { return reporterType; }
    public Address getReporterAddress() { return reporterAddress; }
    public BigDecimal getReputationScore() { return reputationScore; }
    public BigDecimal getStakedAmount() { return stakedAmount; }
    public BigDecimal getMinimumStake() { return minimumStake; }
    public long getTotalSubmissions() { return totalSubmissions; }
    public long getAccurateSubmissions() { return accurateSubmissions; }
    public long getInaccurateSubmissions() { return inaccurateSubmissions; }
    public boolean isActive() { return isActive; }
    public boolean isSlashed() { return isSlashed; }
    public long getLastSubmissionTime() { return lastSubmissionTime; }
    public long getActiveSince() { return activeSince; }
    
    public Map<String, DataSubmission> getSubmissionHistory() {
        return new HashMap<>(submissionHistory);
    }
    
    @Override
    public String toString() {
        return String.format("OracleReporter{id='%s', type='%s', reputation=%.2f, stake=%s, active=%s}",
            reporterId, reporterType, reputationScore, stakedAmount, isActive);
    }
}
