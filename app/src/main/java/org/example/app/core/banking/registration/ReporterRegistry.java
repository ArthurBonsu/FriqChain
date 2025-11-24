package org.example.app.core.banking.registration;

import org.example.app.core.crypto.Certificate;
import org.example.app.core.crypto.CertificateAuthority;
import org.example.app.core.crypto.PKIManager;
import org.example.app.core.state.StateManager;
import org.example.app.core.internal.EventLog;
import org.example.app.core.p2p.PeerNetwork;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;

import java.math.BigInteger;
import java.security.PublicKey;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * ReporterRegistry - Manages oracle data reporters for the TINC Blockchain
 * 
 * Implements a decentralized oracle system with:
 * - Multi-source data verification
 * - Stake-based reporter incentives
 * - Reputation tracking for data accuracy
 * - Commit-reveal scheme to prevent front-running
 * - Slashing for false data reporting
 * - TEE attestation support for hardware verification
 */
public class ReporterRegistry {
    private static final Logger LOGGER = Logger.getLogger(ReporterRegistry.class.getName());
    
    // Staking requirements
    private static final BigInteger MIN_REPORTER_STAKE = new BigInteger("10000000000000000000"); // 10 tokens
    private static final BigInteger MAX_REPORTER_STAKE = new BigInteger("1000000000000000000000"); // 1000 tokens
    
    // Reputation parameters
    private static final int INITIAL_REPUTATION = 500;
    private static final int MAX_REPUTATION = 1000;
    private static final int MIN_REPUTATION_FOR_REPORTING = 100;
    private static final int ACCURACY_BONUS = 10;
    private static final int INACCURACY_PENALTY = 50;
    
    // Consensus parameters
    private static final double DATA_CONSENSUS_THRESHOLD = 0.66; // 66% agreement required
    private static final int MIN_REPORTERS_FOR_CONSENSUS = 3;
    private static final int COMMIT_REVEAL_WINDOW_BLOCKS = 10;
    
    // Reporter types
    public enum ReporterType {
        EXCHANGE_RATE,      // FX and token exchange rates
        COMMODITY_PRICE,    // Gold, oil, minerals, etc.
        CUSTOMS_DATA,       // Import/export data from customs
        SATELLITE,          // Remote sensing/satellite data
        BANK_FEED,          // Bank settlement feeds
        RESERVE_AUDIT,      // Proof of reserves verification
        MARKET_DATA,        // DEX/order book data
        GENERAL             // General purpose oracle
    }
    
    // Reporter status
    public enum ReporterStatus {
        PENDING,
        ACTIVE,
        SUSPENDED,
        SLASHED,
        REMOVED
    }
    
    /**
     * Represents a registered oracle reporter
     */
    public static class Reporter {
        private final String reporterId;
        private final String organizationName;
        private final Address operatorAddress;
        private final PublicKey signingKey;
        private final Certificate certificate;
        private final Set<ReporterType> supportedTypes;
        private final String country;
        private final Date registrationDate;
        private final boolean hasTEEAttestation;
        private final byte[] teeAttestationProof;
        
        private BigInteger stakedAmount;
        private ReporterStatus status;
        private int reputation;
        private long totalReports;
        private long accurateReports;
        private long inaccurateReports;
        private long disputedReports;
        private Date lastReportTime;
        private Map<ReporterType, Integer> typeReputation;
        
        public Reporter(String reporterId, String organizationName, Address operatorAddress,
                       PublicKey signingKey, Certificate certificate, Set<ReporterType> supportedTypes,
                       String country, BigInteger initialStake, boolean hasTEEAttestation,
                       byte[] teeAttestationProof) {
            this.reporterId = reporterId;
            this.organizationName = organizationName;
            this.operatorAddress = operatorAddress;
            this.signingKey = signingKey;
            this.certificate = certificate;
            this.supportedTypes = new HashSet<>(supportedTypes);
            this.country = country;
            this.registrationDate = new Date();
            this.hasTEEAttestation = hasTEEAttestation;
            this.teeAttestationProof = teeAttestationProof != null ? 
                Arrays.copyOf(teeAttestationProof, teeAttestationProof.length) : null;
            
            this.stakedAmount = initialStake;
            this.status = ReporterStatus.PENDING;
            this.reputation = INITIAL_REPUTATION;
            this.totalReports = 0;
            this.accurateReports = 0;
            this.inaccurateReports = 0;
            this.disputedReports = 0;
            this.typeReputation = new ConcurrentHashMap<>();
            
            for (ReporterType type : supportedTypes) {
                typeReputation.put(type, INITIAL_REPUTATION);
            }
        }
        
        // Getters
        public String getReporterId() { return reporterId; }
        public String getOrganizationName() { return organizationName; }
        public Address getOperatorAddress() { return operatorAddress; }
        public PublicKey getSigningKey() { return signingKey; }
        public Certificate getCertificate() { return certificate; }
        public Set<ReporterType> getSupportedTypes() { return new HashSet<>(supportedTypes); }
        public String getCountry() { return country; }
        public Date getRegistrationDate() { return registrationDate; }
        public boolean hasTEEAttestation() { return hasTEEAttestation; }
        public byte[] getTeeAttestationProof() { 
            return teeAttestationProof != null ? Arrays.copyOf(teeAttestationProof, teeAttestationProof.length) : null; 
        }
        public BigInteger getStakedAmount() { return stakedAmount; }
        public ReporterStatus getStatus() { return status; }
        public int getReputation() { return reputation; }
        public long getTotalReports() { return totalReports; }
        public long getAccurateReports() { return accurateReports; }
        public long getInaccurateReports() { return inaccurateReports; }
        public long getDisputedReports() { return disputedReports; }
        public Date getLastReportTime() { return lastReportTime; }
        public Map<ReporterType, Integer> getTypeReputation() { return new HashMap<>(typeReputation); }
        
        // Setters and modifiers
        public void setStatus(ReporterStatus status) { this.status = status; }
        public void setLastReportTime(Date time) { this.lastReportTime = time; }
        
        public void addStake(BigInteger amount) {
            this.stakedAmount = this.stakedAmount.add(amount);
        }
        
        public void removeStake(BigInteger amount) {
            this.stakedAmount = this.stakedAmount.subtract(amount);
        }
        
        public void recordAccurateReport(ReporterType type) {
            this.totalReports++;
            this.accurateReports++;
            this.reputation = Math.min(MAX_REPUTATION, this.reputation + ACCURACY_BONUS);
            this.typeReputation.merge(type, ACCURACY_BONUS, (old, bonus) -> Math.min(MAX_REPUTATION, old + bonus));
        }
        
        public void recordInaccurateReport(ReporterType type) {
            this.totalReports++;
            this.inaccurateReports++;
            this.reputation = Math.max(0, this.reputation - INACCURACY_PENALTY);
            this.typeReputation.merge(type, -INACCURACY_PENALTY, (old, penalty) -> Math.max(0, old + penalty));
        }
        
        public void recordDisputedReport() {
            this.disputedReports++;
        }
        
        public double getAccuracyRate() {
            if (totalReports == 0) return 1.0;
            return (double) accurateReports / totalReports;
        }
        
        public int getTypeSpecificReputation(ReporterType type) {
            return typeReputation.getOrDefault(type, 0);
        }
        
        public double getWeightedReputation() {
            // TEE attestation provides reputation boost
            double teeBonus = hasTEEAttestation ? 1.2 : 1.0;
            return reputation * teeBonus * getAccuracyRate();
        }
    }
    
    /**
     * Represents a data report submission
     */
    public static class DataReport {
        private final String reportId;
        private final String reporterId;
        private final ReporterType dataType;
        private final String dataKey; // e.g., "USD/NGN", "GOLD/OZ"
        private final String dataValue;
        private final Date timestamp;
        private final long blockNumber;
        private final byte[] signature;
        private final byte[] commitHash; // For commit-reveal scheme
        private boolean revealed;
        private boolean verified;
        
        public DataReport(String reporterId, ReporterType dataType, String dataKey,
                         String dataValue, long blockNumber, byte[] signature, byte[] commitHash) {
            this.reportId = UUID.randomUUID().toString();
            this.reporterId = reporterId;
            this.dataType = dataType;
            this.dataKey = dataKey;
            this.dataValue = dataValue;
            this.timestamp = new Date();
            this.blockNumber = blockNumber;
            this.signature = signature;
            this.commitHash = commitHash;
            this.revealed = commitHash == null; // If no commit hash, it's already revealed
            this.verified = false;
        }
        
        // Getters
        public String getReportId() { return reportId; }
        public String getReporterId() { return reporterId; }
        public ReporterType getDataType() { return dataType; }
        public String getDataKey() { return dataKey; }
        public String getDataValue() { return dataValue; }
        public Date getTimestamp() { return timestamp; }
        public long getBlockNumber() { return blockNumber; }
        public byte[] getSignature() { return signature; }
        public byte[] getCommitHash() { return commitHash; }
        public boolean isRevealed() { return revealed; }
        public boolean isVerified() { return verified; }
        
        public void reveal() { this.revealed = true; }
        public void verify() { this.verified = true; }
    }
    
    /**
     * Represents an aggregated data consensus result
     */
    public static class DataConsensus {
        private final String consensusId;
        private final ReporterType dataType;
        private final String dataKey;
        private final String consensusValue;
        private final double confidenceScore;
        private final List<String> participatingReporters;
        private final List<String> outlierReporters;
        private final Date timestamp;
        private final long blockNumber;
        
        public DataConsensus(ReporterType dataType, String dataKey, String consensusValue,
                            double confidenceScore, List<String> participatingReporters,
                            List<String> outlierReporters, long blockNumber) {
            this.consensusId = UUID.randomUUID().toString();
            this.dataType = dataType;
            this.dataKey = dataKey;
            this.consensusValue = consensusValue;
            this.confidenceScore = confidenceScore;
            this.participatingReporters = new ArrayList<>(participatingReporters);
            this.outlierReporters = new ArrayList<>(outlierReporters);
            this.timestamp = new Date();
            this.blockNumber = blockNumber;
        }
        
        // Getters
        public String getConsensusId() { return consensusId; }
        public ReporterType getDataType() { return dataType; }
        public String getDataKey() { return dataKey; }
        public String getConsensusValue() { return consensusValue; }
        public double getConfidenceScore() { return confidenceScore; }
        public List<String> getParticipatingReporters() { return new ArrayList<>(participatingReporters); }
        public List<String> getOutlierReporters() { return new ArrayList<>(outlierReporters); }
        public Date getTimestamp() { return timestamp; }
        public long getBlockNumber() { return blockNumber; }
    }
    
    // Core infrastructure
    private final StateManager stateManager;
    private final CertificateAuthority certificateAuthority;
    private final PKIManager pkiManager;
    private final EventLog eventLog;
    private final PeerNetwork peerNetwork;
    
    // Reporter storage
    private final Map<String, Reporter> reporters;
    private final Map<Address, String> addressToReporter;
    private final Map<ReporterType, Set<String>> typeReporters;
    
    // Report storage
    private final Map<String, DataReport> pendingReports;
    private final Map<String, List<DataReport>> dataKeyReports; // dataKey -> reports
    private final Map<String, DataConsensus> latestConsensus; // dataKey -> consensus
    
    // Counters
    private final AtomicLong totalReports;
    private final AtomicLong currentBlockNumber;
    
    // Executor for async operations
    private final ScheduledExecutorService scheduler;
    
    /**
     * Constructor
     */
    public ReporterRegistry(StateManager stateManager,
                           CertificateAuthority certificateAuthority,
                           PKIManager pkiManager,
                           EventLog eventLog,
                           PeerNetwork peerNetwork) {
        this.stateManager = stateManager;
        this.certificateAuthority = certificateAuthority;
        this.pkiManager = pkiManager;
        this.eventLog = eventLog;
        this.peerNetwork = peerNetwork;
        
        this.reporters = new ConcurrentHashMap<>();
        this.addressToReporter = new ConcurrentHashMap<>();
        this.typeReporters = new ConcurrentHashMap<>();
        this.pendingReports = new ConcurrentHashMap<>();
        this.dataKeyReports = new ConcurrentHashMap<>();
        this.latestConsensus = new ConcurrentHashMap<>();
        this.totalReports = new AtomicLong(0);
        this.currentBlockNumber = new AtomicLong(0);
        
        // Initialize type sets
        for (ReporterType type : ReporterType.values()) {
            typeReporters.put(type, ConcurrentHashMap.newKeySet());
        }
        
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::processCommitReveal, 1, 1, TimeUnit.MINUTES);
        
        LOGGER.info("ReporterRegistry initialized");
    }
    
    /**
     * Registers a new oracle reporter
     */
    public String registerReporter(String organizationName, Address operatorAddress,
                                  PublicKey signingKey, Set<ReporterType> supportedTypes,
                                  String country, BigInteger stakeAmount,
                                  boolean hasTEEAttestation, byte[] teeAttestationProof) {
        try {
            // Validate stake
            if (stakeAmount.compareTo(MIN_REPORTER_STAKE) < 0) {
                LOGGER.warning("Stake below minimum: " + stakeAmount);
                return null;
            }
            if (stakeAmount.compareTo(MAX_REPORTER_STAKE) > 0) {
                LOGGER.warning("Stake above maximum: " + stakeAmount);
                return null;
            }
            
            // Check for duplicate
            if (addressToReporter.containsKey(operatorAddress)) {
                LOGGER.warning("Address already registered: " + operatorAddress);
                return null;
            }
            
            // Validate TEE attestation if claimed
            if (hasTEEAttestation && !validateTEEAttestation(teeAttestationProof)) {
                LOGGER.warning("Invalid TEE attestation proof");
                return null;
            }
            
            // Generate reporter ID
            String reporterId = generateReporterId(organizationName, operatorAddress);
            
            // Issue certificate
            Certificate certificate = certificateAuthority.issueCertificate(
                "REPORTER_" + reporterId, signingKey);
            
            // Create reporter
            Reporter reporter = new Reporter(
                reporterId, organizationName, operatorAddress, signingKey, certificate,
                supportedTypes, country, stakeAmount, hasTEEAttestation, teeAttestationProof);
            
            // Store reporter
            reporters.put(reporterId, reporter);
            addressToReporter.put(operatorAddress, reporterId);
            for (ReporterType type : supportedTypes) {
                typeReporters.get(type).add(reporterId);
            }
            
            // Update state
            stateManager.updateBalance(operatorAddress.toString(), stakeAmount.negate());
            
            eventLog.logEvent("REPORTER_REGISTERED: " + reporterId + " (" + organizationName + ")");
            LOGGER.info("Reporter registered: " + reporterId);
            
            return reporterId;
            
        } catch (Exception e) {
            LOGGER.severe("Error registering reporter: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Activates a pending reporter
     */
    public boolean activateReporter(String reporterId) {
        Reporter reporter = reporters.get(reporterId);
        if (reporter == null || reporter.getStatus() != ReporterStatus.PENDING) {
            return false;
        }
        
        reporter.setStatus(ReporterStatus.ACTIVE);
        eventLog.logEvent("REPORTER_ACTIVATED: " + reporterId);
        return true;
    }
    
    /**
     * Submits a commit hash for commit-reveal scheme
     */
    public String submitCommit(String reporterId, ReporterType dataType, String dataKey,
                              byte[] commitHash, byte[] signature) {
        Reporter reporter = reporters.get(reporterId);
        if (reporter == null || reporter.getStatus() != ReporterStatus.ACTIVE) {
            LOGGER.warning("Reporter not active: " + reporterId);
            return null;
        }
        
        if (!reporter.getSupportedTypes().contains(dataType)) {
            LOGGER.warning("Reporter does not support data type: " + dataType);
            return null;
        }
        
        if (reporter.getReputation() < MIN_REPUTATION_FOR_REPORTING) {
            LOGGER.warning("Reporter reputation too low: " + reporter.getReputation());
            return null;
        }
        
        // Create pending report with commit hash
        DataReport report = new DataReport(reporterId, dataType, dataKey, null,
                                          currentBlockNumber.get(), signature, commitHash);
        
        pendingReports.put(report.getReportId(), report);
        
        eventLog.logEvent("COMMIT_SUBMITTED: " + report.getReportId() + " for " + dataKey);
        return report.getReportId();
    }
    
    /**
     * Reveals the data for a previously committed report
     */
    public boolean revealData(String reportId, String dataValue, byte[] nonce) {
        DataReport report = pendingReports.get(reportId);
        if (report == null || report.isRevealed()) {
            return false;
        }
        
        // Verify commit hash matches revealed data
        byte[] expectedHash = computeCommitHash(dataValue, nonce);
        if (!Arrays.equals(report.getCommitHash(), expectedHash)) {
            LOGGER.warning("Commit hash mismatch for report: " + reportId);
            return false;
        }
        
        // Check reveal window
        long blocksSinceCommit = currentBlockNumber.get() - report.getBlockNumber();
        if (blocksSinceCommit > COMMIT_REVEAL_WINDOW_BLOCKS) {
            LOGGER.warning("Reveal window expired for report: " + reportId);
            pendingReports.remove(reportId);
            return false;
        }
        
        // Create revealed report
        DataReport revealedReport = new DataReport(
            report.getReporterId(), report.getDataType(), report.getDataKey(),
            dataValue, currentBlockNumber.get(), report.getSignature(), null);
        revealedReport.reveal();
        
        // Add to data key reports for consensus
        dataKeyReports.computeIfAbsent(report.getDataKey(), k -> new CopyOnWriteArrayList<>())
                     .add(revealedReport);
        
        pendingReports.remove(reportId);
        totalReports.incrementAndGet();
        
        Reporter reporter = reporters.get(report.getReporterId());
        if (reporter != null) {
            reporter.setLastReportTime(new Date());
        }
        
        eventLog.logEvent("DATA_REVEALED: " + reportId + " value=" + dataValue);
        
        // Attempt to reach consensus
        attemptConsensus(report.getDataKey(), report.getDataType());
        
        return true;
    }
    
    /**
     * Submits data directly (without commit-reveal, for less sensitive data)
     */
    public String submitDataDirect(String reporterId, ReporterType dataType, String dataKey,
                                  String dataValue, byte[] signature) {
        Reporter reporter = reporters.get(reporterId);
        if (reporter == null || reporter.getStatus() != ReporterStatus.ACTIVE) {
            return null;
        }
        
        if (!reporter.getSupportedTypes().contains(dataType)) {
            return null;
        }
        
        if (reporter.getReputation() < MIN_REPUTATION_FOR_REPORTING) {
            return null;
        }
        
        DataReport report = new DataReport(reporterId, dataType, dataKey, dataValue,
                                          currentBlockNumber.get(), signature, null);
        report.reveal();
        
        dataKeyReports.computeIfAbsent(dataKey, k -> new CopyOnWriteArrayList<>()).add(report);
        totalReports.incrementAndGet();
        
        reporter.setLastReportTime(new Date());
        
        eventLog.logEvent("DATA_SUBMITTED: " + report.getReportId() + " key=" + dataKey);
        
        attemptConsensus(dataKey, dataType);
        
        return report.getReportId();
    }
    
    /**
     * Attempts to reach consensus on data for a specific key
     */
    private void attemptConsensus(String dataKey, ReporterType dataType) {
        List<DataReport> reports = dataKeyReports.get(dataKey);
        if (reports == null || reports.size() < MIN_REPORTERS_FOR_CONSENSUS) {
            return;
        }
        
        // Get recent reports (last hour)
        long oneHourAgo = System.currentTimeMillis() - 3600000;
        List<DataReport> recentReports = reports.stream()
            .filter(r -> r.getTimestamp().getTime() > oneHourAgo)
            .filter(DataReport::isRevealed)
            .collect(Collectors.toList());
        
        if (recentReports.size() < MIN_REPORTERS_FOR_CONSENSUS) {
            return;
        }
        
        // Calculate weighted consensus
        Map<String, Double> valueWeights = new HashMap<>();
        double totalWeight = 0;
        
        for (DataReport report : recentReports) {
            Reporter reporter = reporters.get(report.getReporterId());
            if (reporter != null) {
                double weight = reporter.getWeightedReputation();
                valueWeights.merge(report.getDataValue(), weight, Double::sum);
                totalWeight += weight;
            }
        }
        
        // Find consensus value
        String consensusValue = null;
        double maxWeight = 0;
        
        for (Map.Entry<String, Double> entry : valueWeights.entrySet()) {
            if (entry.getValue() > maxWeight) {
                maxWeight = entry.getValue();
                consensusValue = entry.getKey();
            }
        }
        
        double consensusRatio = maxWeight / totalWeight;
        
        if (consensusRatio >= DATA_CONSENSUS_THRESHOLD && consensusValue != null) {
            // Consensus reached
            List<String> participating = new ArrayList<>();
            List<String> outliers = new ArrayList<>();
            
            for (DataReport report : recentReports) {
                if (report.getDataValue().equals(consensusValue)) {
                    participating.add(report.getReporterId());
                    report.verify();
                    
                    // Reward accurate reporters
                    Reporter reporter = reporters.get(report.getReporterId());
                    if (reporter != null) {
                        reporter.recordAccurateReport(dataType);
                    }
                } else {
                    outliers.add(report.getReporterId());
                    
                    // Penalize outliers
                    Reporter reporter = reporters.get(report.getReporterId());
                    if (reporter != null) {
                        reporter.recordInaccurateReport(dataType);
                    }
                }
            }
            
            DataConsensus consensus = new DataConsensus(
                dataType, dataKey, consensusValue, consensusRatio,
                participating, outliers, currentBlockNumber.get());
            
            latestConsensus.put(dataKey, consensus);
            
            // Clear old reports
            reports.clear();
            
            eventLog.logEvent("CONSENSUS_REACHED: " + dataKey + " = " + consensusValue +
                            " (confidence: " + String.format("%.2f%%", consensusRatio * 100) + ")");
            
            LOGGER.info("Consensus reached for " + dataKey + ": " + consensusValue);
        }
    }
    
    /**
     * Slashes a reporter for proven false data
     */
    public boolean slashReporter(String reporterId, String reason, double slashPercentage,
                                List<String> reportingValidators) {
        Reporter reporter = reporters.get(reporterId);
        if (reporter == null) {
            return false;
        }
        
        // Require multiple validators to report
        if (reportingValidators.size() < 3) {
            LOGGER.warning("Insufficient validators for slashing: " + reportingValidators.size());
            return false;
        }
        
        BigInteger slashAmount = reporter.getStakedAmount()
            .multiply(BigInteger.valueOf((long)(slashPercentage * 100)))
            .divide(BigInteger.valueOf(100));
        
        reporter.removeStake(slashAmount);
        reporter.setStatus(ReporterStatus.SLASHED);
        
        // Remove if stake falls below minimum
        if (reporter.getStakedAmount().compareTo(MIN_REPORTER_STAKE) < 0) {
            reporter.setStatus(ReporterStatus.REMOVED);
            for (ReporterType type : reporter.getSupportedTypes()) {
                typeReporters.get(type).remove(reporterId);
            }
        }
        
        eventLog.logEvent("REPORTER_SLASHED: " + reporterId + " amount=" + slashAmount +
                         " reason=" + reason);
        
        return true;
    }
    
    /**
     * Gets the latest consensus value for a data key
     */
    public DataConsensus getLatestConsensus(String dataKey) {
        return latestConsensus.get(dataKey);
    }
    
    /**
     * Gets active reporters for a specific data type
     */
    public List<Reporter> getActiveReportersForType(ReporterType type) {
        return typeReporters.get(type).stream()
            .map(reporters::get)
            .filter(r -> r != null && r.getStatus() == ReporterStatus.ACTIVE)
            .collect(Collectors.toList());
    }
    
    // Helper methods
    private boolean validateTEEAttestation(byte[] attestationProof) {
        // In production, this would verify Intel SGX or AMD SEV attestation
        return attestationProof != null && attestationProof.length > 0;
    }
    
    private byte[] computeCommitHash(String dataValue, byte[] nonce) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(dataValue.getBytes());
            digest.update(nonce);
            return digest.digest();
        } catch (Exception e) {
            return new byte[32];
        }
    }
    
    private String generateReporterId(String organizationName, Address operatorAddress) {
        String data = organizationName + operatorAddress.toString() + System.currentTimeMillis();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes());
            return Hash.fromBytes(hash).toString().substring(0, 16).toUpperCase();
        } catch (Exception e) {
            return UUID.randomUUID().toString().substring(0, 16).toUpperCase();
        }
    }
    
    private void processCommitReveal() {
        long expiredBlock = currentBlockNumber.get() - COMMIT_REVEAL_WINDOW_BLOCKS;
        pendingReports.entrySet().removeIf(entry -> 
            !entry.getValue().isRevealed() && entry.getValue().getBlockNumber() < expiredBlock);
    }
    
    public void updateBlockNumber(long blockNumber) {
        currentBlockNumber.set(blockNumber);
    }
    
    // Public getters
    public Reporter getReporter(String reporterId) {
        return reporters.get(reporterId);
    }
    
    public Reporter getReporterByAddress(Address address) {
        String reporterId = addressToReporter.get(address);
        return reporterId != null ? reporters.get(reporterId) : null;
    }
    
    public Collection<Reporter> getAllReporters() {
        return new ArrayList<>(reporters.values());
    }
    
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalReporters", reporters.size());
        stats.put("activeReporters", reporters.values().stream()
            .filter(r -> r.getStatus() == ReporterStatus.ACTIVE).count());
        stats.put("totalReports", totalReports.get());
        stats.put("consensusCount", latestConsensus.size());
        stats.put("pendingCommits", pendingReports.size());
        
        Map<ReporterType, Integer> typeCount = new HashMap<>();
        for (ReporterType type : ReporterType.values()) {
            typeCount.put(type, typeReporters.get(type).size());
        }
        stats.put("reportersByType", typeCount);
        
        return stats;
    }
    
    public void shutdown() {
        scheduler.shutdown();
        LOGGER.info("ReporterRegistry shutdown");
    }
}