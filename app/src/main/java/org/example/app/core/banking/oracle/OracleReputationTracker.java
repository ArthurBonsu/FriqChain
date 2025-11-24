package org.example.app.core.banking.oracle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * OracleReputationTracker - Tracks and manages reputation scores for oracle reporters
 * 
 * Implements reputation system with:
 * - Historical accuracy tracking
 * - Weighted reputation scoring
 * - Time-decay for old submissions
 * - Reputation-based data weighting
 * - Automatic slashing triggers
 * - Reputation recovery mechanisms
 * 
 * Integrates with: ReputationScoreValidator, OracleStakingManager
 */
public class OracleReputationTracker {
    private static final Logger LOGGER = Logger.getLogger(OracleReputationTracker.class.getName());
    
    // Reputation parameters
    private static final BigDecimal MIN_REPUTATION = BigDecimal.ZERO;
    private static final BigDecimal MAX_REPUTATION = new BigDecimal("100");
    private static final BigDecimal INITIAL_REPUTATION = new BigDecimal("50");
    
    // Reputation adjustments
    private static final BigDecimal ACCURATE_SUBMISSION_BONUS = new BigDecimal("0.5");
    private static final BigDecimal INACCURATE_SUBMISSION_PENALTY = new BigDecimal("-2.0");
    private static final BigDecimal OUTLIER_PENALTY = new BigDecimal("-1.5");
    private static final BigDecimal SLASH_PENALTY = new BigDecimal("-15.0");
    
    // Time decay
    private static final BigDecimal DECAY_RATE_PER_DAY = new BigDecimal("0.05");
    private static final long DECAY_CHECK_INTERVAL_MS = 86400000; // 24 hours
    
    // Reputation tracking
    private final Map<String, ReputationRecord> reputationRecords;
    private final Map<String, List<ReputationEvent>> eventHistory;
    
    /**
     * Reputation record
     */
    public static class ReputationRecord {
        private final String reporterId;
        private BigDecimal currentScore;
        private long totalSubmissions;
        private long accurateSubmissions;
        private long inaccurateSubmissions;
        private long outlierSubmissions;
        private final long firstSeenTime;
        private long lastActivityTime;
        private long lastDecayTime;
        
        public ReputationRecord(String reporterId) {
            this.reporterId = reporterId;
            this.currentScore = INITIAL_REPUTATION;
            this.totalSubmissions = 0;
            this.accurateSubmissions = 0;
            this.inaccurateSubmissions = 0;
            this.outlierSubmissions = 0;
            this.firstSeenTime = System.currentTimeMillis();
            this.lastActivityTime = System.currentTimeMillis();
            this.lastDecayTime = System.currentTimeMillis();
        }
        
        public String getReporterId() { return reporterId; }
        public BigDecimal getCurrentScore() { return currentScore; }
        public long getTotalSubmissions() { return totalSubmissions; }
        public long getAccurateSubmissions() { return accurateSubmissions; }
        public long getInaccurateSubmissions() { return inaccurateSubmissions; }
        public long getOutlierSubmissions() { return outlierSubmissions; }
        public long getFirstSeenTime() { return firstSeenTime; }
        public long getLastActivityTime() { return lastActivityTime; }
        
        public void setScore(BigDecimal score) {
            this.currentScore = score.max(MIN_REPUTATION).min(MAX_REPUTATION);
            this.lastActivityTime = System.currentTimeMillis();
        }
        
        public void incrementTotal() { totalSubmissions++; }
        public void incrementAccurate() { accurateSubmissions++; }
        public void incrementInaccurate() { inaccurateSubmissions++; }
        public void incrementOutlier() { outlierSubmissions++; }
        
        public BigDecimal getAccuracyRate() {
            if (totalSubmissions == 0) return BigDecimal.ZERO;
            return new BigDecimal(accurateSubmissions)
                .divide(new BigDecimal(totalSubmissions), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        }
        
        public void updateDecayTime() {
            lastDecayTime = System.currentTimeMillis();
        }
        
        public boolean shouldDecay() {
            return System.currentTimeMillis() - lastDecayTime >= DECAY_CHECK_INTERVAL_MS;
        }
    }
    
    /**
     * Reputation event
     */
    public static class ReputationEvent {
        private final String reporterId;
        private final String eventType;
        private final BigDecimal scoreChange;
        private final String reason;
        private final long timestamp;
        
        public ReputationEvent(String reporterId, String eventType, 
                              BigDecimal scoreChange, String reason) {
            this.reporterId = reporterId;
            this.eventType = eventType;
            this.scoreChange = scoreChange;
            this.reason = reason;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getReporterId() { return reporterId; }
        public String getEventType() { return eventType; }
        public BigDecimal getScoreChange() { return scoreChange; }
        public String getReason() { return reason; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Constructor
     */
    public OracleReputationTracker() {
        this.reputationRecords = new ConcurrentHashMap<>();
        this.eventHistory = new ConcurrentHashMap<>();
        
        LOGGER.info("OracleReputationTracker initialized");
    }
    
    /**
     * Records accurate submission
     */
    public void recordAccurate(String reporterId, String reason) {
        ReputationRecord record = getOrCreateRecord(reporterId);
        
        record.incrementTotal();
        record.incrementAccurate();
        
        adjustReputation(reporterId, ACCURATE_SUBMISSION_BONUS, "ACCURATE", reason);
    }
    
    /**
     * Records inaccurate submission
     */
    public void recordInaccurate(String reporterId, String reason) {
        ReputationRecord record = getOrCreateRecord(reporterId);
        
        record.incrementTotal();
        record.incrementInaccurate();
        
        adjustReputation(reporterId, INACCURATE_SUBMISSION_PENALTY, "INACCURATE", reason);
    }
    
    /**
     * Records outlier submission
     */
    public void recordOutlier(String reporterId, String reason) {
        ReputationRecord record = getOrCreateRecord(reporterId);
        
        record.incrementTotal();
        record.incrementOutlier();
        
        adjustReputation(reporterId, OUTLIER_PENALTY, "OUTLIER", reason);
    }
    
    /**
     * Records slash event
     */
    public void recordSlash(String reporterId, String reason) {
        adjustReputation(reporterId, SLASH_PENALTY, "SLASH", reason);
    }
    
    /**
     * Adjusts reputation score
     */
    private void adjustReputation(String reporterId, BigDecimal adjustment, 
                                 String eventType, String reason) {
        ReputationRecord record = getOrCreateRecord(reporterId);
        
        BigDecimal newScore = record.getCurrentScore().add(adjustment);
        record.setScore(newScore);
        
        // Record event
        ReputationEvent event = new ReputationEvent(reporterId, eventType, adjustment, reason);
        eventHistory.computeIfAbsent(reporterId, k -> new ArrayList<>()).add(event);
        
        LOGGER.fine(String.format("Reputation adjusted: %s - %s (%.2f) - Score: %.2f",
            reporterId, eventType, adjustment, newScore));
    }
    
    /**
     * Applies time decay to reputation
     */
    public void applyDecay(String reporterId) {
        ReputationRecord record = reputationRecords.get(reporterId);
        
        if (record == null || !record.shouldDecay()) {
            return;
        }
        
        long daysSinceActivity = (System.currentTimeMillis() - record.getLastActivityTime()) / 86400000;
        
        if (daysSinceActivity > 0) {
            BigDecimal decay = DECAY_RATE_PER_DAY.multiply(new BigDecimal(daysSinceActivity));
            BigDecimal newScore = record.getCurrentScore().subtract(decay);
            record.setScore(newScore);
            record.updateDecayTime();
            
            LOGGER.fine(String.format("Decay applied: %s - %.2f (inactive for %d days)",
                reporterId, decay, daysSinceActivity));
        }
    }
    
    /**
     * Applies decay to all reporters
     */
    public void applyDecayAll() {
        for (String reporterId : reputationRecords.keySet()) {
            applyDecay(reporterId);
        }
    }
    
    /**
     * Gets reputation score
     */
    public BigDecimal getReputation(String reporterId) {
        ReputationRecord record = reputationRecords.get(reporterId);
        return record != null ? record.getCurrentScore() : INITIAL_REPUTATION;
    }
    
    /**
     * Gets weighted reputation (combines score and accuracy)
     */
    public BigDecimal getWeightedReputation(String reporterId) {
        ReputationRecord record = reputationRecords.get(reporterId);
        
        if (record == null) {
            return INITIAL_REPUTATION;
        }
        
        BigDecimal accuracyRate = record.getAccuracyRate();
        BigDecimal reputationScore = record.getCurrentScore();
        
        // 70% reputation, 30% accuracy
        return reputationScore.multiply(new BigDecimal("0.7"))
            .add(accuracyRate.multiply(new BigDecimal("0.3")));
    }
    
    /**
     * Gets or creates reputation record
     */
    private ReputationRecord getOrCreateRecord(String reporterId) {
        return reputationRecords.computeIfAbsent(reporterId, ReputationRecord::new);
    }
    
    /**
     * Gets reputation record
     */
    public ReputationRecord getRecord(String reporterId) {
        return reputationRecords.get(reporterId);
    }
    
    /**
     * Gets event history
     */
    public List<ReputationEvent> getEventHistory(String reporterId) {
        return new ArrayList<>(eventHistory.getOrDefault(reporterId, new ArrayList<>()));
    }
    
    /**
     * Gets all reporters sorted by reputation
     */
    public List<ReputationRecord> getTopReporters(int limit) {
        return reputationRecords.values().stream()
            .sorted((a, b) -> b.getCurrentScore().compareTo(a.getCurrentScore()))
            .limit(limit)
            .toList();
    }
    
    /**
     * Gets statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalReporters", reputationRecords.size());
        
        long highRepCount = reputationRecords.values().stream()
            .filter(r -> r.getCurrentScore().compareTo(new BigDecimal("70")) >= 0)
            .count();
        stats.put("highReputationReporters", highRepCount);
        
        long lowRepCount = reputationRecords.values().stream()
            .filter(r -> r.getCurrentScore().compareTo(new BigDecimal("30")) <= 0)
            .count();
        stats.put("lowReputationReporters", lowRepCount);
        
        return stats;
    }
}
