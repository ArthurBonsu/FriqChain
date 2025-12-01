package org.example.app.core.blockchain.consensus;

import org.example.app.core.types.Address;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * ReputationScoreValidator - Tracks node reputation for government-resistant network
 * with African sovereignty enforcement
 * 
 * Based on Implementation Guidebook Section V: Defense Against Internal Threats
 * 
 * AFRICAN SOVEREIGNTY ENHANCEMENTS:
 * - Regional affiliation tracking (5 African regions)
 * - Geographic diversity bonuses
 * - Cross-region collaboration rewards
 * - Anti-concentration mechanisms (no region > 33% of high-reputation nodes)
 * - DAC node reputation requirements (min 0.5 for 35 nodes)
 * - Committee member selection based on reputation + regional balance
 * - ASU settlement participation tracking
 * - Treasury subsidy eligibility (min 0.6 for subsidized transactions)
 * - Submarine cable routing compliance bonuses
 * 
 * Reputation factors:
 * 1. Oracle accuracy (for data reporters)
 * 2. Validation reliability (for validators)
 * 3. Uptime and availability
 * 4. Stake/collateral maintained
 * 5. Community trust votes
 * 6. Historical behavior
 * 7. Geographic diversity contribution (NEW)
 * 8. Dispute resolution participation
 * 9. Cross-region collaboration (NEW)
 * 10. ASU settlement participation (NEW)
 * 
 * Reputation affects:
 * - Voting weight in DAO
 * - Validator selection probability
 * - Oracle data weight
 * - Transaction validation priority
 * - Slashing penalties
 * - DAC node eligibility (NEW)
 * - Committee member selection (NEW)
 * - Treasury subsidy eligibility (NEW)
 */
public class ReputationScoreValidator {
    private static final Logger LOGGER = Logger.getLogger(ReputationScoreValidator.class.getName());
    
    // African Geographic Regions
    public enum AfricanRegion {
        NORTH_AFRICA,    // Morocco, Egypt, Algeria, Tunisia, Libya
        WEST_AFRICA,     // Nigeria, Ghana, Senegal, Ivory Coast, Mali
        EAST_AFRICA,     // Kenya, Ethiopia, Tanzania, Uganda, Rwanda
        CENTRAL_AFRICA,  // DRC, Cameroon, Gabon, Chad, CAR
        SOUTHERN_AFRICA  // South Africa, Zimbabwe, Botswana, Namibia, Angola
    }
    
    // Reputation score ranges
    private static final BigDecimal MIN_REPUTATION_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAX_REPUTATION_SCORE = new BigDecimal("100");
    private static final BigDecimal INITIAL_REPUTATION_SCORE = new BigDecimal("50"); // Neutral start
    
    // Minimum reputation thresholds for activities
    private static final BigDecimal MIN_VALIDATOR_REPUTATION = new BigDecimal("60");
    private static final BigDecimal MIN_ORACLE_REPUTATION = new BigDecimal("55");
    private static final BigDecimal MIN_BANK_REPUTATION = new BigDecimal("70");
    private static final BigDecimal MIN_DAC_NODE_REPUTATION = new BigDecimal("50"); // DAC nodes: 0.5 threshold
    private static final BigDecimal MIN_COMMITTEE_REPUTATION = new BigDecimal("65"); // Committee members: higher bar
    private static final BigDecimal MIN_TREASURY_SUBSIDY_REPUTATION = new BigDecimal("60"); // For subsidized txs
    
    // Reputation change factors (existing)
    private static final BigDecimal ACCURACY_BONUS = new BigDecimal("0.5");
    private static final BigDecimal INACCURACY_PENALTY = new BigDecimal("-2.0");
    private static final BigDecimal VALIDATION_SUCCESS_BONUS = new BigDecimal("0.2");
    private static final BigDecimal VALIDATION_FAILURE_PENALTY = new BigDecimal("-1.0");
    private static final BigDecimal UPTIME_BONUS_PER_DAY = new BigDecimal("0.1");
    private static final BigDecimal DOWNTIME_PENALTY_PER_HOUR = new BigDecimal("-0.5");
    private static final BigDecimal SLASH_PENALTY = new BigDecimal("-10.0");
    private static final BigDecimal DISPUTE_WIN_BONUS = new BigDecimal("1.0");
    private static final BigDecimal DISPUTE_LOSS_PENALTY = new BigDecimal("-3.0");
    
    // NEW: African sovereignty bonuses/penalties
    private static final BigDecimal GEOGRAPHIC_DIVERSITY_BONUS = new BigDecimal("0.3"); // Per unique region collaborated with
    private static final BigDecimal CROSS_REGION_COLLABORATION_BONUS = new BigDecimal("0.5"); // Successful cross-region validation
    private static final BigDecimal ASU_SETTLEMENT_BONUS = new BigDecimal("0.2"); // ASU settlement participation
    private static final BigDecimal SUBMARINE_CABLE_COMPLIANCE_BONUS = new BigDecimal("0.1"); // Using African infrastructure
    private static final BigDecimal REGION_CONCENTRATION_PENALTY = new BigDecimal("-0.5"); // If region > 33% control
    private static final BigDecimal DAC_ATTESTATION_BONUS = new BigDecimal("0.3"); // Successful DAC attestation
    private static final BigDecimal COMMITTEE_SERVICE_BONUS = new BigDecimal("1.0"); // Serving on rotating committee
    
    // Decay factor (reputation decays if inactive)
    private static final BigDecimal DECAY_RATE_PER_DAY = new BigDecimal("0.05"); // 0.05 points per day
    private static final long DECAY_CHECK_INTERVAL_MS = 86400000; // 24 hours
    
    // Geographic distribution enforcement
    private static final double MAX_REGION_CONTROL = 0.33; // No region > 33% of high-reputation nodes
    private static final BigDecimal HIGH_REPUTATION_THRESHOLD = new BigDecimal("70");
    
    // Tracking
    private final Map<String, ReputationRecord> reputationRecords;
    private final Map<String, List<ReputationEvent>> eventHistory;
    private final Map<AfricanRegion, Set<String>> nodesByRegion; // Track nodes by region
    
    /**
     * Reputation record for a node with African sovereignty tracking
     */
    public static class ReputationRecord {
        private final String nodeId;
        private final NodeType nodeType;
        private final AfricanRegion region; // Node's geographic region
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
        
        // NEW: African sovereignty tracking
        private final Set<AfricanRegion> collaboratedRegions; // Regions this node worked with
        private long crossRegionValidations; // Successful cross-region validations
        private long asuSettlementCount; // ASU settlement participations
        private long dacAttestations; // DAC attestation count
        private long committeeServiceEpochs; // Epochs served on committee
        private long submarineCableRoutings; // Times used African submarine cables
        
        public ReputationRecord(String nodeId, NodeType nodeType, AfricanRegion region) {
            this.nodeId = nodeId;
            this.nodeType = nodeType;
            this.region = region;
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
            
            // Initialize African sovereignty tracking
            this.collaboratedRegions = Collections.synchronizedSet(new HashSet<>());
            this.collaboratedRegions.add(region); // Always collaborated with own region
            this.crossRegionValidations = 0;
            this.asuSettlementCount = 0;
            this.dacAttestations = 0;
            this.committeeServiceEpochs = 0;
            this.submarineCableRoutings = 0;
        }
        
        // Existing getters
        public String getNodeId() { return nodeId; }
        public NodeType getNodeType() { return nodeType; }
        public AfricanRegion getRegion() { return region; }
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
        
        // NEW: African sovereignty getters
        public Set<AfricanRegion> getCollaboratedRegions() { 
            return new HashSet<>(collaboratedRegions); 
        }
        public long getCrossRegionValidations() { return crossRegionValidations; }
        public long getAsuSettlementCount() { return asuSettlementCount; }
        public long getDacAttestations() { return dacAttestations; }
        public long getCommitteeServiceEpochs() { return committeeServiceEpochs; }
        public long getSubmarineCableRoutings() { return submarineCableRoutings; }
        
        public void setCurrentScore(BigDecimal score) {
            // Clamp to valid range
            this.currentScore = score.max(MIN_REPUTATION_SCORE).min(MAX_REPUTATION_SCORE);
            this.lastActivityTime = System.currentTimeMillis();
        }
        
        // Existing increment methods
        public void incrementValidations() { this.totalValidations++; }
        public void incrementSuccessfulValidations() { this.successfulValidations++; }
        public void incrementOracleSubmissions() { this.totalOracleSubmissions++; }
        public void incrementAccurateSubmissions() { this.accurateOracleSubmissions++; }
        public void addUptime(long seconds) { this.uptimeSeconds += seconds; }
        public void addDowntime(long seconds) { this.downtimeSeconds += seconds; }
        public void incrementSlashCount() { this.slashCount++; }
        public void incrementDisputeWins() { this.disputeWins++; }
        public void incrementDisputeLosses() { this.disputeLosses++; }
        
        // NEW: African sovereignty increment methods
        public void addCollaboratedRegion(AfricanRegion region) { 
            this.collaboratedRegions.add(region); 
        }
        public void incrementCrossRegionValidations() { this.crossRegionValidations++; }
        public void incrementAsuSettlements() { this.asuSettlementCount++; }
        public void incrementDacAttestations() { this.dacAttestations++; }
        public void incrementCommitteeService() { this.committeeServiceEpochs++; }
        public void incrementSubmarineCableRouting() { this.submarineCableRoutings++; }
        
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
        
        /**
         * NEW: Calculates geographic diversity score (0.0 to 1.0)
         */
        public BigDecimal getGeographicDiversityScore() {
            // Maximum diversity: collaborated with all 5 regions
            return new BigDecimal(collaboratedRegions.size())
                .divide(new BigDecimal("5"), 4, RoundingMode.HALF_UP);
        }
        
        /**
         * NEW: Checks if node is eligible for DAC service
         */
        public boolean isEligibleForDAC() {
            return currentScore.compareTo(MIN_DAC_NODE_REPUTATION) >= 0 &&
                   slashCount == 0 &&
                   getUptimePercentage().compareTo(new BigDecimal("95")) >= 0;
        }
        
        /**
         * NEW: Checks if node is eligible for committee service
         */
        public boolean isEligibleForCommittee() {
            return currentScore.compareTo(MIN_COMMITTEE_REPUTATION) >= 0 &&
                   slashCount == 0 &&
                   crossRegionValidations >= 10 && // Must have cross-region experience
                   collaboratedRegions.size() >= 3 && // Must have worked with 3+ regions
                   getUptimePercentage().compareTo(new BigDecimal("98")) >= 0;
        }
        
        /**
         * NEW: Checks if node qualifies for Treasury subsidies
         */
        public boolean qualifiesForTreasurySubsidy() {
            return currentScore.compareTo(MIN_TREASURY_SUBSIDY_REPUTATION) >= 0 &&
                   slashCount == 0;
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
        AGENT_NODE,
        DAC_NODE,         // NEW: Data Availability Committee node (35 total)
        COMMITTEE_MEMBER  // NEW: Rotating committee member (5 per committee)
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
        private final String message;
        private final AfricanRegion nodeRegion; // NEW: Node's region
        
        public ValidationResult(boolean meetsThreshold, BigDecimal currentScore,
                              BigDecimal requiredScore, String message, AfricanRegion nodeRegion) {
            this.meetsThreshold = meetsThreshold;
            this.currentScore = currentScore;
            this.requiredScore = requiredScore;
            this.message = message;
            this.nodeRegion = nodeRegion;
        }
        
        public boolean meetsThreshold() { return meetsThreshold; }
        public BigDecimal getCurrentScore() { return currentScore; }
        public BigDecimal getRequiredScore() { return requiredScore; }
        public String getMessage() { return message; }
        public AfricanRegion getNodeRegion() { return nodeRegion; }
    }
    
    public ReputationScoreValidator() {
        this.reputationRecords = new ConcurrentHashMap<>();
        this.eventHistory = new ConcurrentHashMap<>();
        this.nodesByRegion = new ConcurrentHashMap<>();
        
        // Initialize region tracking
        for (AfricanRegion region : AfricanRegion.values()) {
            nodesByRegion.put(region, Collections.synchronizedSet(new HashSet<>()));
        }
    }
    
    /**
     * Registers a new node with regional affiliation
     */
    public void registerNode(String nodeId, NodeType nodeType, AfricanRegion region) {
        if (reputationRecords.containsKey(nodeId)) {
            LOGGER.warning(String.format("Node %s already registered", nodeId));
            return;
        }
        
        ReputationRecord record = new ReputationRecord(nodeId, nodeType, region);
        reputationRecords.put(nodeId, record);
        nodesByRegion.get(region).add(nodeId);
        
        recordEvent(nodeId, "NODE_REGISTERED", BigDecimal.ZERO,
            String.format("Node registered as %s in %s", nodeType, region));
        
        LOGGER.info(String.format("Registered %s node %s in region %s", 
            nodeType, nodeId, region));
    }
    
    /**
     * Validates if a node meets reputation threshold for validator role
     */
    public ValidationResult validateForValidator(String nodeId) {
        ReputationRecord record = reputationRecords.get(nodeId);
        if (record == null) {
            return new ValidationResult(false, BigDecimal.ZERO, 
                MIN_VALIDATOR_REPUTATION, "Node not registered", null);
        }
        
        boolean meetsThreshold = record.getCurrentScore()
            .compareTo(MIN_VALIDATOR_REPUTATION) >= 0;
        
        String message = meetsThreshold ? 
            "Node meets validator reputation threshold" :
            String.format("Node reputation %.2f below threshold %.2f",
                record.getCurrentScore(), MIN_VALIDATOR_REPUTATION);
        
        return new ValidationResult(meetsThreshold, record.getCurrentScore(),
            MIN_VALIDATOR_REPUTATION, message, record.getRegion());
    }
    
    /**
     * NEW: Validates if a node meets reputation threshold for DAC service
     */
    public ValidationResult validateForDAC(String nodeId) {
        ReputationRecord record = reputationRecords.get(nodeId);
        if (record == null) {
            return new ValidationResult(false, BigDecimal.ZERO,
                MIN_DAC_NODE_REPUTATION, "Node not registered", null);
        }
        
        boolean eligible = record.isEligibleForDAC();
        
        String message = eligible ?
            "Node meets DAC reputation requirements" :
            String.format("Node ineligible for DAC: score=%.2f, slashes=%d, uptime=%.2f%%",
                record.getCurrentScore(), record.getSlashCount(), 
                record.getUptimePercentage());
        
        return new ValidationResult(eligible, record.getCurrentScore(),
            MIN_DAC_NODE_REPUTATION, message, record.getRegion());
    }
    
    /**
     * NEW: Validates if a node meets reputation threshold for committee service
     */
    public ValidationResult validateForCommittee(String nodeId) {
        ReputationRecord record = reputationRecords.get(nodeId);
        if (record == null) {
            return new ValidationResult(false, BigDecimal.ZERO,
                MIN_COMMITTEE_REPUTATION, "Node not registered", null);
        }
        
        boolean eligible = record.isEligibleForCommittee();
        
        String message = eligible ?
            "Node meets committee member requirements" :
            String.format("Node ineligible for committee: score=%.2f, crossRegionVal=%d, regions=%d",
                record.getCurrentScore(), record.getCrossRegionValidations(),
                record.getCollaboratedRegions().size());
        
        return new ValidationResult(eligible, record.getCurrentScore(),
            MIN_COMMITTEE_REPUTATION, message, record.getRegion());
    }
    
    /**
     * Validates if a node meets reputation threshold for oracle role
     */
    public ValidationResult validateForOracle(String nodeId) {
        ReputationRecord record = reputationRecords.get(nodeId);
        if (record == null) {
            return new ValidationResult(false, BigDecimal.ZERO,
                MIN_ORACLE_REPUTATION, "Node not registered", null);
        }
        
        boolean meetsThreshold = record.getCurrentScore()
            .compareTo(MIN_ORACLE_REPUTATION) >= 0;
        
        String message = meetsThreshold ?
            "Node meets oracle reputation threshold" :
            String.format("Node reputation %.2f below threshold %.2f",
                record.getCurrentScore(), MIN_ORACLE_REPUTATION);
        
        return new ValidationResult(meetsThreshold, record.getCurrentScore(),
            MIN_ORACLE_REPUTATION, message, record.getRegion());
    }
    
    /**
     * Validates if a node meets reputation threshold for bank role
     */
    public ValidationResult validateForBank(String nodeId) {
        ReputationRecord record = reputationRecords.get(nodeId);
        if (record == null) {
            return new ValidationResult(false, BigDecimal.ZERO,
                MIN_BANK_REPUTATION, "Node not registered", null);
        }
        
        boolean meetsThreshold = record.getCurrentScore()
            .compareTo(MIN_BANK_REPUTATION) >= 0;
        
        String message = meetsThreshold ?
            "Node meets bank reputation threshold" :
            String.format("Node reputation %.2f below threshold %.2f",
                record.getCurrentScore(), MIN_BANK_REPUTATION);
        
        return new ValidationResult(meetsThreshold, record.getCurrentScore(),
            MIN_BANK_REPUTATION, message, record.getRegion());
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
     * NEW: Records a successful cross-region validation
     */
    public void recordCrossRegionValidation(String nodeId, AfricanRegion targetRegion) {
        ReputationRecord record = reputationRecords.get(nodeId);
        if (record == null) return;
        
        record.incrementCrossRegionValidations();
        record.addCollaboratedRegion(targetRegion);
        
        // Bonus for cross-region collaboration
        BigDecimal bonus = VALIDATION_SUCCESS_BONUS.add(CROSS_REGION_COLLABORATION_BONUS);
        BigDecimal newScore = record.getCurrentScore().add(bonus);
        record.setCurrentScore(newScore);
        
        recordEvent(nodeId, "CROSS_REGION_VALIDATION", bonus,
            String.format("Cross-region validation with %s", targetRegion));
        
        // Check if this increased geographic diversity
        if (record.getCollaboratedRegions().size() > 1) {
            applyGeographicDiversityBonus(nodeId);
        }
    }
    
    /**
     * NEW: Applies geographic diversity bonus
     */
    private void applyGeographicDiversityBonus(String nodeId) {
        ReputationRecord record = reputationRecords.get(nodeId);
        if (record == null) return;
        
        int uniqueRegions = record.getCollaboratedRegions().size();
        if (uniqueRegions >= 3) { // Bonus for working with 3+ regions
            BigDecimal bonus = GEOGRAPHIC_DIVERSITY_BONUS.multiply(
                new BigDecimal(uniqueRegions - 2)
            );
            BigDecimal newScore = record.getCurrentScore().add(bonus);
            record.setCurrentScore(newScore);
            
            recordEvent(nodeId, "GEOGRAPHIC_DIVERSITY", bonus,
                String.format("Collaborated with %d regions", uniqueRegions));
        }
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
        
        LOGGER.warning(String.format("Validation failure for %s: %s", nodeId, reason));
    }
    
    /**
     * Records an oracle submission
     */
    public void recordOracleSubmission(String nodeId, boolean accurate) {
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
            
            LOGGER.warning(String.format("Inaccurate oracle submission from %s", nodeId));
        }
    }
    
    /**
     * Records uptime
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
     * NEW: Records ASU settlement participation
     */
    public void recordASUSettlement(String nodeId) {
        ReputationRecord record = reputationRecords.get(nodeId);
        if (record == null) return;
        
        record.incrementAsuSettlements();
        
        BigDecimal newScore = record.getCurrentScore().add(ASU_SETTLEMENT_BONUS);
        record.setCurrentScore(newScore);
        
        recordEvent(nodeId, "ASU_SETTLEMENT", ASU_SETTLEMENT_BONUS,
            "ASU settlement participation");
    }
    
    /**
     * NEW: Records DAC attestation
     */
    public void recordDACAttestation(String nodeId, boolean successful) {
        ReputationRecord record = reputationRecords.get(nodeId);
        if (record == null) return;
        
        if (successful) {
            record.incrementDacAttestations();
            
            BigDecimal newScore = record.getCurrentScore().add(DAC_ATTESTATION_BONUS);
            record.setCurrentScore(newScore);
            
            recordEvent(nodeId, "DAC_ATTESTATION", DAC_ATTESTATION_BONUS,
                "Successful DAC attestation");
        } else {
            BigDecimal penalty = new BigDecimal("-0.5");
            BigDecimal newScore = record.getCurrentScore().add(penalty);
            record.setCurrentScore(newScore);
            
            recordEvent(nodeId, "DAC_ATTESTATION_FAILED", penalty,
                "Failed DAC attestation");
        }
    }
    
    /**
     * NEW: Records committee service
     */
    public void recordCommitteeService(String nodeId, long epochsServed) {
        ReputationRecord record = reputationRecords.get(nodeId);
        if (record == null) return;
        
        record.incrementCommitteeService();
        
        BigDecimal bonus = COMMITTEE_SERVICE_BONUS.multiply(new BigDecimal(epochsServed));
        BigDecimal newScore = record.getCurrentScore().add(bonus);
        record.setCurrentScore(newScore);
        
        recordEvent(nodeId, "COMMITTEE_SERVICE", bonus,
            String.format("Committee service for %d epochs", epochsServed));
    }
    
    /**
     * NEW: Records submarine cable routing compliance
     */
    public void recordSubmarineCableRouting(String nodeId) {
        ReputationRecord record = reputationRecords.get(nodeId);
        if (record == null) return;
        
        record.incrementSubmarineCableRouting();
        
        // Bonus every 100 routings
        if (record.getSubmarineCableRoutings() % 100 == 0) {
            BigDecimal bonus = SUBMARINE_CABLE_COMPLIANCE_BONUS.multiply(new BigDecimal("10"));
            BigDecimal newScore = record.getCurrentScore().add(bonus);
            record.setCurrentScore(newScore);
            
            recordEvent(nodeId, "SUBMARINE_CABLE_COMPLIANCE", bonus,
                String.format("%d submarine cable routings", record.getSubmarineCableRoutings()));
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
     * NEW: Enforces regional balance (no region > 33% of high-reputation nodes)
     */
    public void enforceRegionalBalance() {
        // Get high-reputation nodes
        List<ReputationRecord> highRepNodes = reputationRecords.values().stream()
            .filter(r -> r.getCurrentScore().compareTo(HIGH_REPUTATION_THRESHOLD) >= 0)
            .collect(Collectors.toList());
        
        if (highRepNodes.isEmpty()) return;
        
        // Count by region
        Map<AfricanRegion, Long> regionCounts = highRepNodes.stream()
            .collect(Collectors.groupingBy(
                ReputationRecord::getRegion,
                Collectors.counting()
            ));
        
        long totalHighRep = highRepNodes.size();
        
        // Apply penalty if any region exceeds 33%
        for (Map.Entry<AfricanRegion, Long> entry : regionCounts.entrySet()) {
            double regionPercentage = (double) entry.getValue() / totalHighRep;
            
            if (regionPercentage > MAX_REGION_CONTROL) {
                // Apply penalty to nodes in over-represented region
                highRepNodes.stream()
                    .filter(r -> r.getRegion() == entry.getKey())
                    .forEach(r -> {
                        BigDecimal penalty = REGION_CONCENTRATION_PENALTY;
                        BigDecimal newScore = r.getCurrentScore().add(penalty);
                        r.setCurrentScore(newScore);
                        
                        recordEvent(r.getNodeId(), "REGION_CONCENTRATION_PENALTY", penalty,
                            String.format("Region %s exceeds 33%% control (%.1f%%)",
                                entry.getKey(), regionPercentage * 100));
                    });
                
                LOGGER.warning(String.format("Region %s has %.1f%% of high-reputation nodes (exceeds 33%%)",
                    entry.getKey(), regionPercentage * 100));
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
     * NEW: Gets top nodes by reputation in a specific region
     */
    public List<ReputationRecord> getTopNodesByRegion(AfricanRegion region, int limit) {
        return reputationRecords.values().stream()
            .filter(r -> r.getRegion() == region)
            .sorted((a, b) -> b.getCurrentScore().compareTo(a.getCurrentScore()))
            .limit(limit)
            .toList();
    }
    
    /**
     * NEW: Gets eligible DAC nodes for a region
     */
    public List<ReputationRecord> getEligibleDACNodes(AfricanRegion region) {
        return reputationRecords.values().stream()
            .filter(r -> r.getRegion() == region)
            .filter(ReputationRecord::isEligibleForDAC)
            .sorted((a, b) -> b.getCurrentScore().compareTo(a.getCurrentScore()))
            .toList();
    }
    
    /**
     * NEW: Gets eligible committee members across all regions
     */
    public List<ReputationRecord> getEligibleCommitteeMembers() {
        return reputationRecords.values().stream()
            .filter(ReputationRecord::isEligibleForCommittee)
            .sorted((a, b) -> b.getCurrentScore().compareTo(a.getCurrentScore()))
            .toList();
    }
    
    /**
     * NEW: Selects balanced committee (one from each of 5 regions)
     */
    public List<ReputationRecord> selectBalancedCommittee() {
        List<ReputationRecord> committee = new ArrayList<>();
        
        for (AfricanRegion region : AfricanRegion.values()) {
            // Get top eligible member from this region
            Optional<ReputationRecord> topMember = reputationRecords.values().stream()
                .filter(r -> r.getRegion() == region)
                .filter(ReputationRecord::isEligibleForCommittee)
                .max(Comparator.comparing(ReputationRecord::getCurrentScore));
            
            topMember.ifPresent(committee::add);
        }
        
        return committee;
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
     * Gets statistics with regional breakdown
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
        
        // Regional breakdown
        Map<AfricanRegion, Long> regionCount = new EnumMap<>(AfricanRegion.class);
        for (AfricanRegion region : AfricanRegion.values()) {
            long count = reputationRecords.values().stream()
                .filter(r -> r.getRegion() == region)
                .count();
            regionCount.put(region, count);
        }
        stats.put("nodesByRegion", regionCount);
        
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
        
        stats.put("eligibleForDAC", reputationRecords.values().stream()
            .filter(ReputationRecord::isEligibleForDAC)
            .count());
        
        stats.put("eligibleForCommittee", reputationRecords.values().stream()
            .filter(ReputationRecord::isEligibleForCommittee)
            .count());
        
        // Geographic diversity metrics
        stats.put("averageGeographicDiversity", reputationRecords.values().stream()
            .map(ReputationRecord::getGeographicDiversityScore)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(new BigDecimal(reputationRecords.size()), 4, RoundingMode.HALF_UP));
        
        return stats;
    }
    
    /**
     * Gets all reputation records
     */
    public Collection<ReputationRecord> getAllRecords() {
        return new ArrayList<>(reputationRecords.values());
    }
}