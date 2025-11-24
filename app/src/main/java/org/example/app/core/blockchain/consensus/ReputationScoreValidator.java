package org.example.app.core.blockchain.consensus;

import org.example.app.core.types.Address;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * ReputationScoreValidator - Tracks node reputation for government-resistant network
 * 
 * Based on Implementation Guidebook Section V: Defense Against Internal Threats
 * 
 * Reputation factors:
 * 1. Oracle accuracy (for data reporters)
 * 2. Validation reliability (for validators)
 * 3. Uptime and availability
 * 4. Stake/collateral maintained
 * 5. Community trust votes
 * 6. Historical behavior
 * 7. Geographic diversity contribution
 * 8. Dispute resolution participation
 * 
 * Reputation affects:
 * - Voting weight in DAO
 * - Validator selection probability
 * - Oracle data weight
 * - Transaction validation priority
 * - Slashing penalties
 */
public class ReputationScoreValidator {
    private static final Logger LOGGER = Logger.getLogger(ReputationScoreValidator.class.getName());
    
    // Reputation score ranges
    private static final BigDecimal MIN_REPUTATION_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAX_REPUTATION_SCORE = new BigDecimal("100");
    private static final BigDecimal INITIAL_REPUTATION_SCORE = new BigDecimal("50"); // Neutral start
    
    // Minimum reputation thresholds for activities
    private static final BigDecimal MIN_VALIDATOR_REPUTATION = new BigDecimal("60");
    private static final BigDecimal MIN_ORACLE_REPUTATION = new BigDecimal("55");
    private static final BigDecimal MIN_BANK_REPUTATION = new BigDecimal("70");
    
    // Reputation change factors
    private static final BigDecimal ACCURACY_BONUS = new BigDecimal("0.5");
    private static final BigDecimal INACCURACY_PENALTY = new BigDecimal("-2.0");
    private static final BigDecimal VALIDATION_SUCCESS_BONUS = new BigDecimal("0.2");
    private static final BigDecimal VALIDATION_FAILURE_PENALTY = new BigDecimal("-1.0");
    private static final BigDecimal UPTIME_BONUS_PER_DAY = new BigDecimal("0.1");
    private static final BigDecimal DOWNTIME_PENALTY_PER_HOUR = new BigDecimal("-0.5");
    private static final BigDecimal SLASH_PENALTY = new BigDecimal("-10.0");
    private static final BigDecimal DISPUTE_WIN_BONUS = new BigDecimal("1.0");
    private static final BigDecimal DISPUTE_LOSS_PENALTY = new BigDecimal("-3.0");
    
    // Decay factor (reputation decays if inactive)
    private static final BigDecimal DECAY_RATE_PER_DAY = new BigDecimal("0.05"); // 0.05 points per day
    private static final long DECAY_CHECK_INTERVAL_MS = 86400000; // 24 hours
    
    // Tracking
    private final Map<String, ReputationRecord> reputationRecords;
    private final Map<String, List<ReputationEvent>> eventHistory;
    
    /**
     * Reputation record for a node
     */
    public static class ReputationRecord {
        private final String nodeId;
        private final NodeType nodeType;
        private BigDecimal currentScore;
        private long totalValidations;
        private long successfulValidations;
        private long totalOracleSubmissions;
        private long accurateOracleSubmissions;
        private long uptimeSeconds;
        private long downtimeSeconds;
        private int slashCount;
        private int disputeWins;
        private int disputeLosses;
        private final long firstSeenTime;
        private long lastActivityTime;
        
        public ReputationRecord(String nodeId, NodeType nodeType) {
            this.nodeId = nodeId;
            this.nodeType = nodeType;
            this.currentScore = INITIAL_REPUTATION_SCORE;
            this.totalValidations = 0;
            this.successfulValidations = 0;
            this.totalOracleSubmissions = 0;
            this.accurateOracleSubmissions = 0;
            this.uptimeSeconds = 0;
            this.downtimeSeconds = 0;
            this.slashCount = 0;
            this.disputeWins = 0;
            this.disputeLosses = 0;
            this.firstSeenTime = System.currentTimeMillis();
            this.lastActivityTime = System.currentTimeMillis();
        }
        
        public String getNodeId() { return nodeId; }
        public NodeType getNodeType() { return nodeType; }
        public BigDecimal getCurrentScore() { return currentScore; }
        public long getTotalValidations() { return totalValidations; }
        public long getSuccessfulValidations() { return successfulValidations; }
        public long getTotalOracleSubmissions() { return totalOracleSubmissions; }
        public long getAccurateOracleSubmissions() { return accurateOracleSubmissions; }
        public long getUptimeSeconds() { return uptimeSeconds; }
        public long getDowntimeSeconds() { return downtimeSeconds; }
        public int getSlashCount() { return slashCount; }
        public int getDisputeWins() { return disputeWins; }
        public int getDisputeLosses() { return disputeLosses; }
        public long getFirstSeenTime() { return firstSeenTime; }
        public long getLastActivityTime() { return lastActivityTime; }
        
        public void setCurrentScore(BigDecimal score) {
            // Clamp to valid range
            this.currentScore = score.max(MIN_REPUTATION_SCORE).min(MAX_REPUTATION_SCORE);
            this.lastActivityTime = System.currentTimeMillis();
        }
        
        public void incrementValidations() { this.totalValidations++; }
        public void incrementSuccessfulValidations() { this.successfulValidations++; }
        public void incrementOracleSubmissions() { this.totalOracleSubmissions++; }
        public void incrementAccurateSubmissions() { this.accurateOracleSubmissions++; }
        public void addUptime(long seconds) { this.uptimeSeconds += seconds; }
        public void addDowntime(long seconds) { this.downtimeSeconds += seconds; }
        public void incrementSlashCount() { this.slashCount++; }
        public void incrementDisputeWins() { this.disputeWins++; }
        public void incrementDisputeLosses() { this.disputeLosses++; }
        
        public BigDecimal getValidationSuccessRate() {
            if (totalValidations == 0) return BigDecimal.ZERO;
            return new BigDecimal(successfulValidations)
                .divide(new BigDecimal(totalValidations), 4, RoundingMode.HALF_UP);
        }
        
        public BigDecimal getOracleAccuracyRate() {
            if (totalOracleSubmissions == 0) return BigDecimal.ZERO;
            return new BigDecimal(accurateOracleSubmissions)
                .divide(new BigDecimal(totalOracleSubmissions), 4, RoundingMode.HALF_UP);
        }
        
        public BigDecimal getUptimePercentage() {
            long totalTime = uptimeSeconds + downtimeSeconds;
            if (totalTime == 0) return new BigDecimal("100");
            return new BigDecimal(uptimeSeconds)
                .divide(new BigDecimal(totalTime), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        }
    }
    
    /**
     * Types of nodes in the system
     */
    public enum NodeType {
        VALIDATOR,
        ORACLE,
        BANK,
        EXPORTER,
        RELAY_NODE,
        AGENT_NODE
    }
    
    /**
     * Reputation event record
     */
    public static class ReputationEvent {
        private final String nodeId;
        private final String eventType;
        private final BigDecimal scoreChange;
        private final String reason;
        private final long timestamp;
        private final Map<String, Object> metadata;
        
        public ReputationEvent(String nodeId, String eventType, 
                              BigDecimal scoreChange, String reason) {
            this.nodeId = nodeId;
            this.eventType = eventType;
            this.scoreChange = scoreChange;
            this.reason = reason;
            this.timestamp = System.currentTimeMillis();
            this.metadata = new HashMap<>();
        }
        
        public String getNodeId() { return nodeId; }
        public String getEventType() { return eventType; }
        public BigDecimal getScoreChange() { return scoreChange; }
        public String getReason() { return reason; }
        public long getTimestamp() { return timestamp; }
        public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
        
        public void addMetadata(String key, Object value) { metadata.put(key, value); }
    }
    
    /**
     * Validation result
     */
    public static class ValidationResult {
        private final boolean meetsThreshold;
        private final BigDecimal currentScore;
        private final BigDecimal requiredScore;
        private final String failureReason;
        
        private ValidationResult(boolean meets, BigDecimal current, BigDecimal required, String reason) {
            this.meetsThreshold = meets;
            this.currentScore = current;
            this.requiredScore = required;
            this.failureReason = reason;
        }
        
        public static ValidationResult pass(BigDecimal currentScore, BigDecimal requiredScore) {
            return new ValidationResult(true, currentScore, requiredScore, null);
        }
        
        public static ValidationResult fail(BigDecimal currentScore, BigDecimal requiredScore, String reason) {
            return new ValidationResult(false, currentScore, requiredScore, reason);
        }
        
        public boolean meetsThreshold() { return meetsThreshold; }
        public BigDecimal getCurrentScore() { return currentScore; }
        public BigDecimal getRequiredScore() { return requiredScore; }
        public String getFailureReason() { return failureReason; }
    }
    
    /**
     * Constructor
     */
    public ReputationScoreValidator() {
        this.reputationRecords = new ConcurrentHashMap<>();
        this.eventHistory = new ConcurrentHashMap<>();
        
        LOGGER.info("ReputationScoreValidator initialized");
    }
    
    /**
     * Registers a new node
     */
    public void registerNode(String nodeId, NodeType nodeType) {
        if (reputationRecords.containsKey(nodeId)) {
            LOGGER.warning("Node already registered: " + nodeId);
            return;
        }
        
        ReputationRecord record = new ReputationRecord(nodeId, nodeType);
        reputationRecords.put(nodeId, record);
        
        recordEvent(nodeId, "REGISTRATION", BigDecimal.ZERO, 
            "Node registered with initial score " + INITIAL_REPUTATION_SCORE);
        
        LOGGER.info(String.format("Node registered: %s (%s) with initial score %.2f",
            nodeId, nodeType, INITIAL_REPUTATION_SCORE.doubleValue()));
    }
    
    /**
     * Validates node reputation meets threshold for activity
     */
    public ValidationResult validateReputationForValidator(String nodeId) {
        return validateReputationThreshold(nodeId, MIN_VALIDATOR_REPUTATION, "validator operations");
    }
    
    public ValidationResult validateReputationForOracle(String nodeId) {
        return validateReputationThreshold(nodeId, MIN_ORACLE_REPUTATION, "oracle submissions");
    }
    
    public ValidationResult validateReputationForBank(String nodeId) {
        return validateReputationThreshold(nodeId, MIN_BANK_REPUTATION, "bank operations");
    }
    
    /**
     * Generic reputation threshold validation
     */
    private ValidationResult validateReputationThreshold(String nodeId, 
                                                        BigDecimal threshold, 
                                                        String activity) {
        ReputationRecord record = reputationRecords.get(nodeId);
        
        if (record == null) {
            return ValidationResult.fail(BigDecimal.ZERO, threshold, 
                "Node not found: " + nodeId);
        }
        
        BigDecimal currentScore = record.getCurrentScore();
        
        if (currentScore.compareTo(threshold) < 0) {
            return ValidationResult.fail(currentScore, threshold,
                String.format("Insufficient reputation for %s: %.2f (required: %.2f)",
                    activity, currentScore.doubleValue(), threshold.doubleValue()));
        }
        
        return ValidationResult.pass(currentScore, threshold);
    }
    
    /**
     * Records a successful validation
     */
    public void recordValidationSuccess(String nodeId) {
        ReputationRecord record = reputationRecords.get(nodeId);
        if (record == null) return;
        
        record.incrementValidations();
        record.incrementSuccessfulValidations();
        
        BigDecimal newScore = record.getCurrentScore().add(VALIDATION_SUCCESS_BONUS);
        record.setCurrentScore(newScore);
        
        recordEvent(nodeId, "VALIDATION_SUCCESS", VALIDATION_SUCCESS_BONUS,
            "Successful validation");
    }
    
    /**
     * Records a failed validation
     */
    public void recordValidationFailure(String nodeId, String reason) {
        ReputationRecord record = reputationRecords.get(nodeId);
        if (record == null) return;
        
        record.incrementValidations();
        
        BigDecimal newScore = record.getCurrentScore().add(VALIDATION_FAILURE_PENALTY);
        record.setCurrentScore(newScore);
        
        recordEvent(nodeId, "VALIDATION_FAILURE", VALIDATION_FAILURE_PENALTY,
            "Failed validation: " + reason);
        
        LOGGER.warning(String.format("Validation failure recorded for %s: %s",
            nodeId, reason));
    }
    
    /**
     * Records an accurate oracle submission
     */
    public void recordOracleAccuracy(String nodeId, boolean accurate) {
        ReputationRecord record = reputationRecords.get(nodeId);
        if (record == null) return;
        
        record.incrementOracleSubmissions();
        
        if (accurate) {
            record.incrementAccurateSubmissions();
            BigDecimal newScore = record.getCurrentScore().add(ACCURACY_BONUS);
            record.setCurrentScore(newScore);
            
            recordEvent(nodeId, "ORACLE_ACCURATE", ACCURACY_BONUS,
                "Accurate oracle submission");
        } else {
            BigDecimal newScore = record.getCurrentScore().add(INACCURACY_PENALTY);
            record.setCurrentScore(newScore);
            
            recordEvent(nodeId, "ORACLE_INACCURATE", INACCURACY_PENALTY,
                "Inaccurate oracle submission");
            
            LOGGER.warning("Inaccurate oracle submission recorded for " + nodeId);
        }
    }
    
    /**
     * Records uptime/downtime
     */
    public void recordUptime(String nodeId, long seconds) {
        ReputationRecord record = reputationRecords.get(nodeId);
        if (record == null) return;
        
        record.addUptime(seconds);
        
        // Award bonus for consistent uptime (every 24 hours)
        long days = seconds / 86400;
        if (days > 0) {
            BigDecimal bonus = UPTIME_BONUS_PER_DAY.multiply(new BigDecimal(days));
            BigDecimal newScore = record.getCurrentScore().add(bonus);
            record.setCurrentScore(newScore);
            
            recordEvent(nodeId, "UPTIME_BONUS", bonus,
                String.format("%d days uptime", days));
        }
    }
    
    public void recordDowntime(String nodeId, long seconds) {
        ReputationRecord record = reputationRecords.get(nodeId);
        if (record == null) return;
        
        record.addDowntime(seconds);
        
        // Apply penalty for downtime (per hour)
        long hours = seconds / 3600;
        if (hours > 0) {
            BigDecimal penalty = DOWNTIME_PENALTY_PER_HOUR.multiply(new BigDecimal(hours));
            BigDecimal newScore = record.getCurrentScore().add(penalty);
            record.setCurrentScore(newScore);
            
            recordEvent(nodeId, "DOWNTIME_PENALTY", penalty,
                String.format("%d hours downtime", hours));
            
            LOGGER.warning(String.format("Downtime recorded for %s: %d hours", nodeId, hours));
        }
    }
    
    /**
     * Records a slashing event
     */
    public void recordSlashing(String nodeId, String reason) {
        ReputationRecord record = reputationRecords.get(nodeId);
        if (record == null) return;
        
        record.incrementSlashCount();
        
        BigDecimal newScore = record.getCurrentScore().add(SLASH_PENALTY);
        record.setCurrentScore(newScore);
        
        recordEvent(nodeId, "SLASHED", SLASH_PENALTY, "Slashed: " + reason);
        
        LOGGER.severe(String.format("Node %s slashed: %s", nodeId, reason));
    }
    
    /**
     * Records dispute resolution outcome
     */
    public void recordDisputeOutcome(String nodeId, boolean won, String description) {
        ReputationRecord record = reputationRecords.get(nodeId);
        if (record == null) return;
        
        if (won) {
            record.incrementDisputeWins();
            BigDecimal newScore = record.getCurrentScore().add(DISPUTE_WIN_BONUS);
            record.setCurrentScore(newScore);
            
            recordEvent(nodeId, "DISPUTE_WON", DISPUTE_WIN_BONUS,
                "Dispute won: " + description);
        } else {
            record.incrementDisputeLosses();
            BigDecimal newScore = record.getCurrentScore().add(DISPUTE_LOSS_PENALTY);
            record.setCurrentScore(newScore);
            
            recordEvent(nodeId, "DISPUTE_LOST", DISPUTE_LOSS_PENALTY,
                "Dispute lost: " + description);
        }
    }
    
    /**
     * Applies reputation decay for inactive nodes
     */
    public void applyDecay() {
        long now = System.currentTimeMillis();
        
        for (ReputationRecord record : reputationRecords.values()) {
            long inactiveDays = (now - record.getLastActivityTime()) / 86400000;
            
            if (inactiveDays > 0) {
                BigDecimal decay = DECAY_RATE_PER_DAY.multiply(new BigDecimal(inactiveDays));
                BigDecimal newScore = record.getCurrentScore().subtract(decay);
                record.setCurrentScore(newScore);
                
                if (inactiveDays > 7) { // Only log if inactive for a week
                    recordEvent(record.getNodeId(), "DECAY", decay.negate(),
                        String.format("Reputation decay after %d days inactive", inactiveDays));
                }
            }
        }
    }
    
    /**
     * Gets reputation score for a node
     */
    public BigDecimal getReputationScore(String nodeId) {
        ReputationRecord record = reputationRecords.get(nodeId);
        return record != null ? record.getCurrentScore() : BigDecimal.ZERO;
    }
    
    /**
     * Gets reputation record
     */
    public ReputationRecord getReputationRecord(String nodeId) {
        return reputationRecords.get(nodeId);
    }
    
    /**
     * Gets top nodes by reputation
     */
    public List<ReputationRecord> getTopNodes(int limit) {
        return reputationRecords.values().stream()
            .sorted((a, b) -> b.getCurrentScore().compareTo(a.getCurrentScore()))
            .limit(limit)
            .toList();
    }
    
    /**
     * Gets nodes below minimum reputation threshold
     */
    public List<ReputationRecord> getLowReputationNodes(BigDecimal threshold) {
        return reputationRecords.values().stream()
            .filter(r -> r.getCurrentScore().compareTo(threshold) < 0)
            .sorted((a, b) -> a.getCurrentScore().compareTo(b.getCurrentScore()))
            .toList();
    }
    
    /**
     * Records a reputation event
     */
    private void recordEvent(String nodeId, String eventType, 
                           BigDecimal scoreChange, String reason) {
        List<ReputationEvent> events = eventHistory.computeIfAbsent(
            nodeId, k -> Collections.synchronizedList(new ArrayList<>())
        );
        
        ReputationEvent event = new ReputationEvent(nodeId, eventType, scoreChange, reason);
        events.add(event);
        
        // Keep only last 1000 events per node
        if (events.size() > 1000) {
            events.subList(0, events.size() - 1000).clear();
        }
    }
    
    /**
     * Gets event history for a node
     */
    public List<ReputationEvent> getEventHistory(String nodeId) {
        List<ReputationEvent> events = eventHistory.get(nodeId);
        return events != null ? new ArrayList<>(events) : new ArrayList<>();
    }
    
    /**
     * Gets statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalNodes", reputationRecords.size());
        
        Map<NodeType, Long> typeCount = new EnumMap<>(NodeType.class);
        for (NodeType type : NodeType.values()) {
            long count = reputationRecords.values().stream()
                .filter(r -> r.getNodeType() == type)
                .count();
            typeCount.put(type, count);
        }
        stats.put("nodesByType", typeCount);
        
        BigDecimal avgScore = reputationRecords.values().stream()
            .map(ReputationRecord::getCurrentScore)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(new BigDecimal(reputationRecords.size()), 2, RoundingMode.HALF_UP);
        stats.put("averageScore", avgScore);
        
        stats.put("nodesAbove60", reputationRecords.values().stream()
            .filter(r -> r.getCurrentScore().compareTo(new BigDecimal("60")) >= 0)
            .count());
        
        stats.put("nodesBelow40", reputationRecords.values().stream()
            .filter(r -> r.getCurrentScore().compareTo(new BigDecimal("40")) < 0)
            .count());
        
        return stats;
    }
    
    /**
     * Gets all reputation records
     */
    public Collection<ReputationRecord> getAllRecords() {
        return new ArrayList<>(reputationRecords.values());
    }
}