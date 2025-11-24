package org.example.app.core.banking.registration;

import org.example.app.core.crypto.Certificate;
import org.example.app.core.crypto.CertificateAuthority;
import org.example.app.core.crypto.PKIManager;
import org.example.app.core.state.StateManager;
import org.example.app.core.internal.EventLog;
import org.example.app.core.p2p.PeerNetwork;
import org.example.app.core.p2p.Peer;
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
 * ValidatorNodeRegistry - Manages validator node registration, staking, and slashing
 * 
 * Implements decentralized validator management for the TINC Blockchain with:
 * - Stake-based participation (validators must stake tokens)
 * - Slashing for malicious behavior
 * - Geographic distribution for government resistance
 * - Reputation tracking for validator performance
 * - PBFT consensus integration
 */
public class ValidatorNodeRegistry {
    private static final Logger LOGGER = Logger.getLogger(ValidatorNodeRegistry.class.getName());
    
    // Staking requirements
    private static final BigInteger MIN_STAKE_AMOUNT = new BigInteger("100000000000000000000"); // 100 tokens (18 decimals)
    private static final BigInteger MAX_STAKE_AMOUNT = new BigInteger("10000000000000000000000"); // 10000 tokens
    private static final double MAX_GEOGRAPHIC_SHARE = 0.33; // Max 33% from any region
    private static final int MIN_VALIDATORS_REQUIRED = 21; // Minimum validators for network security
    private static final int MAX_VALIDATORS = 100; // Maximum active validators
    
    // Slashing parameters
    private static final double MINOR_SLASH_PERCENTAGE = 0.01; // 1% for minor violations
    private static final double MAJOR_SLASH_PERCENTAGE = 0.10; // 10% for major violations
    private static final double CRITICAL_SLASH_PERCENTAGE = 0.50; // 50% for critical violations
    private static final int JAIL_DURATION_BLOCKS = 10000; // Blocks before unjailing allowed
    
    // Validator status
    public enum ValidatorStatus {
        PENDING,      // Registered but not yet active
        ACTIVE,       // Actively participating in consensus
        INACTIVE,     // Temporarily offline
        JAILED,       // Slashed and temporarily banned
        UNBONDING,    // Stake is being withdrawn
        REMOVED       // Permanently removed
    }
    
    // Violation types for slashing
    public enum ViolationType {
        DOUBLE_SIGNING(CRITICAL_SLASH_PERCENTAGE, true),    // Signing conflicting blocks
        DOWNTIME(MINOR_SLASH_PERCENTAGE, false),            // Extended offline period
        INVALID_BLOCK(MAJOR_SLASH_PERCENTAGE, true),        // Proposing invalid blocks
        CENSORSHIP(MAJOR_SLASH_PERCENTAGE, true),           // Deliberately excluding transactions
        ORACLE_MANIPULATION(CRITICAL_SLASH_PERCENTAGE, true), // Manipulating oracle data
        GOVERNANCE_ABUSE(MAJOR_SLASH_PERCENTAGE, true);     // Abusing governance power
        
        private final double slashPercentage;
        private final boolean jailRequired;
        
        ViolationType(double slashPercentage, boolean jailRequired) {
            this.slashPercentage = slashPercentage;
            this.jailRequired = jailRequired;
        }
        
        public double getSlashPercentage() { return slashPercentage; }
        public boolean isJailRequired() { return jailRequired; }
    }
    
    /**
     * Represents a registered validator node
     */
    public static class ValidatorNode {
        private final String validatorId;
        private final String nodeId;
        private final Address operatorAddress;
        private final PublicKey consensusKey;
        private final PublicKey networkKey;
        private final Certificate certificate;
        private final String peerAddress;
        private final int geographicRegion;
        private final String country;
        private final Date registrationDate;
        
        private BigInteger stakedAmount;
        private BigInteger slashedAmount;
        private ValidatorStatus status;
        private int reputation; // 0-1000
        private long blocksProposed;
        private long blocksValidated;
        private long missedBlocks;
        private Date lastActiveTime;
        private long jailedUntilBlock;
        private Date unbondingCompleteTime;
        private List<SlashingRecord> slashingHistory;
        
        public ValidatorNode(String validatorId, String nodeId, Address operatorAddress,
                           PublicKey consensusKey, PublicKey networkKey, Certificate certificate,
                           String peerAddress, int geographicRegion, String country,
                           BigInteger initialStake) {
            this.validatorId = validatorId;
            this.nodeId = nodeId;
            this.operatorAddress = operatorAddress;
            this.consensusKey = consensusKey;
            this.networkKey = networkKey;
            this.certificate = certificate;
            this.peerAddress = peerAddress;
            this.geographicRegion = geographicRegion;
            this.country = country;
            this.registrationDate = new Date();
            
            this.stakedAmount = initialStake;
            this.slashedAmount = BigInteger.ZERO;
            this.status = ValidatorStatus.PENDING;
            this.reputation = 500; // Start at neutral
            this.blocksProposed = 0;
            this.blocksValidated = 0;
            this.missedBlocks = 0;
            this.lastActiveTime = new Date();
            this.jailedUntilBlock = 0;
            this.slashingHistory = new ArrayList<>();
        }
        
        // Getters
        public String getValidatorId() { return validatorId; }
        public String getNodeId() { return nodeId; }
        public Address getOperatorAddress() { return operatorAddress; }
        public PublicKey getConsensusKey() { return consensusKey; }
        public PublicKey getNetworkKey() { return networkKey; }
        public Certificate getCertificate() { return certificate; }
        public String getPeerAddress() { return peerAddress; }
        public int getGeographicRegion() { return geographicRegion; }
        public String getCountry() { return country; }
        public Date getRegistrationDate() { return registrationDate; }
        public BigInteger getStakedAmount() { return stakedAmount; }
        public BigInteger getSlashedAmount() { return slashedAmount; }
        public ValidatorStatus getStatus() { return status; }
        public int getReputation() { return reputation; }
        public long getBlocksProposed() { return blocksProposed; }
        public long getBlocksValidated() { return blocksValidated; }
        public long getMissedBlocks() { return missedBlocks; }
        public Date getLastActiveTime() { return lastActiveTime; }
        public long getJailedUntilBlock() { return jailedUntilBlock; }
        public Date getUnbondingCompleteTime() { return unbondingCompleteTime; }
        public List<SlashingRecord> getSlashingHistory() { return new ArrayList<>(slashingHistory); }
        
        // Setters and modifiers
        public void setStatus(ValidatorStatus status) { this.status = status; }
        public void setReputation(int reputation) { this.reputation = Math.max(0, Math.min(1000, reputation)); }
        public void setLastActiveTime(Date time) { this.lastActiveTime = time; }
        public void setJailedUntilBlock(long block) { this.jailedUntilBlock = block; }
        public void setUnbondingCompleteTime(Date time) { this.unbondingCompleteTime = time; }
        
        public void addStake(BigInteger amount) {
            this.stakedAmount = this.stakedAmount.add(amount);
        }
        
        public void removeStake(BigInteger amount) {
            this.stakedAmount = this.stakedAmount.subtract(amount);
        }
        
        public void recordSlash(BigInteger amount, SlashingRecord record) {
            this.slashedAmount = this.slashedAmount.add(amount);
            this.stakedAmount = this.stakedAmount.subtract(amount);
            this.slashingHistory.add(record);
        }
        
        public void incrementBlocksProposed() { this.blocksProposed++; }
        public void incrementBlocksValidated() { this.blocksValidated++; }
        public void incrementMissedBlocks() { this.missedBlocks++; }
        
        public double getUptime() {
            long total = blocksProposed + blocksValidated + missedBlocks;
            if (total == 0) return 1.0;
            return (double)(blocksProposed + blocksValidated) / total;
        }
        
        public BigInteger getVotingPower() {
            // Voting power = stake * reputation factor
            double reputationFactor = reputation / 1000.0;
            BigInteger power = stakedAmount.multiply(BigInteger.valueOf((long)(reputationFactor * 100)));
            return power.divide(BigInteger.valueOf(100));
        }
    }
    
    /**
     * Records a slashing event
     */
    public static class SlashingRecord {
        private final String recordId;
        private final String validatorId;
        private final ViolationType violationType;
        private final BigInteger slashedAmount;
        private final String evidence;
        private final Date timestamp;
        private final long blockNumber;
        private final List<String> reportingValidators;
        
        public SlashingRecord(String validatorId, ViolationType violationType,
                            BigInteger slashedAmount, String evidence, long blockNumber,
                            List<String> reportingValidators) {
            this.recordId = UUID.randomUUID().toString();
            this.validatorId = validatorId;
            this.violationType = violationType;
            this.slashedAmount = slashedAmount;
            this.evidence = evidence;
            this.timestamp = new Date();
            this.blockNumber = blockNumber;
            this.reportingValidators = new ArrayList<>(reportingValidators);
        }
        
        // Getters
        public String getRecordId() { return recordId; }
        public String getValidatorId() { return validatorId; }
        public ViolationType getViolationType() { return violationType; }
        public BigInteger getSlashedAmount() { return slashedAmount; }
        public String getEvidence() { return evidence; }
        public Date getTimestamp() { return timestamp; }
        public long getBlockNumber() { return blockNumber; }
        public List<String> getReportingValidators() { return new ArrayList<>(reportingValidators); }
    }
    
    // Core infrastructure
    private final StateManager stateManager;
    private final CertificateAuthority certificateAuthority;
    private final PKIManager pkiManager;
    private final EventLog eventLog;
    private final PeerNetwork peerNetwork;
    
    // Validator storage
    private final Map<String, ValidatorNode> validators;
    private final Map<Address, String> addressToValidator;
    private final Map<Integer, Set<String>> regionValidators;
    private final Map<String, BigInteger> pendingUnbonds;
    private final AtomicLong totalStaked;
    private final AtomicLong currentBlockNumber;
    
    // Slashing pool (redistributed to honest validators)
    private BigInteger slashingPool;
    
    // Executor for async operations
    private final ScheduledExecutorService scheduler;
    
    /**
     * Constructor
     */
    public ValidatorNodeRegistry(StateManager stateManager,
                                CertificateAuthority certificateAuthority,
                                PKIManager pkiManager,
                                EventLog eventLog,
                                PeerNetwork peerNetwork) {
        this.stateManager = stateManager;
        this.certificateAuthority = certificateAuthority;
        this.pkiManager = pkiManager;
        this.eventLog = eventLog;
        this.peerNetwork = peerNetwork;
        
        this.validators = new ConcurrentHashMap<>();
        this.addressToValidator = new ConcurrentHashMap<>();
        this.regionValidators = new ConcurrentHashMap<>();
        this.pendingUnbonds = new ConcurrentHashMap<>();
        this.totalStaked = new AtomicLong(0);
        this.currentBlockNumber = new AtomicLong(0);
        this.slashingPool = BigInteger.ZERO;
        
        // Initialize region sets
        for (int i = 1; i <= 4; i++) {
            regionValidators.put(i, ConcurrentHashMap.newKeySet());
        }
        
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::processUnbonding, 1, 1, TimeUnit.HOURS);
        
        LOGGER.info("ValidatorNodeRegistry initialized");
    }
    
    /**
     * Registers a new validator node
     */
    public String registerValidator(String nodeId, Address operatorAddress,
                                   PublicKey consensusKey, PublicKey networkKey,
                                   String peerAddress, int geographicRegion,
                                   String country, BigInteger stakeAmount) {
        try {
            // Validate stake amount
            if (stakeAmount.compareTo(MIN_STAKE_AMOUNT) < 0) {
                LOGGER.warning("Stake amount below minimum: " + stakeAmount);
                return null;
            }
            if (stakeAmount.compareTo(MAX_STAKE_AMOUNT) > 0) {
                LOGGER.warning("Stake amount above maximum: " + stakeAmount);
                return null;
            }
            
            // Check validator limit
            long activeCount = validators.values().stream()
                .filter(v -> v.getStatus() == ValidatorStatus.ACTIVE)
                .count();
            if (activeCount >= MAX_VALIDATORS) {
                LOGGER.warning("Maximum validator limit reached");
                return null;
            }
            
            // Check geographic distribution
            if (!checkGeographicDistribution(geographicRegion)) {
                LOGGER.warning("Geographic distribution limit exceeded for region: " + geographicRegion);
                return null;
            }
            
            // Check for duplicate registration
            if (addressToValidator.containsKey(operatorAddress)) {
                LOGGER.warning("Operator address already registered: " + operatorAddress);
                return null;
            }
            
            // Generate validator ID
            String validatorId = generateValidatorId(nodeId, operatorAddress);
            
            // Issue certificate
            Certificate certificate = certificateAuthority.issueCertificate(
                "VALIDATOR_" + validatorId, consensusKey);
            
            // Create validator node
            ValidatorNode validator = new ValidatorNode(
                validatorId, nodeId, operatorAddress, consensusKey, networkKey,
                certificate, peerAddress, geographicRegion, country, stakeAmount);
            
            // Store validator
            validators.put(validatorId, validator);
            addressToValidator.put(operatorAddress, validatorId);
            regionValidators.get(geographicRegion).add(validatorId);
            totalStaked.addAndGet(stakeAmount.longValue());
            
            // Update state
            stateManager.updateBalance(operatorAddress.toString(), stakeAmount.negate());
            
            eventLog.logEvent("VALIDATOR_REGISTERED: " + validatorId + " from " + country +
                            " with stake " + stakeAmount);
            LOGGER.info("Validator registered: " + validatorId);
            
            return validatorId;
            
        } catch (Exception e) {
            LOGGER.severe("Error registering validator: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Activates a pending validator
     */
    public boolean activateValidator(String validatorId) {
        ValidatorNode validator = validators.get(validatorId);
        if (validator == null) {
            LOGGER.warning("Validator not found: " + validatorId);
            return false;
        }
        
        if (validator.getStatus() != ValidatorStatus.PENDING) {
            LOGGER.warning("Validator not in pending status: " + validatorId);
            return false;
        }
        
        validator.setStatus(ValidatorStatus.ACTIVE);
        validator.setLastActiveTime(new Date());
        
        eventLog.logEvent("VALIDATOR_ACTIVATED: " + validatorId);
        LOGGER.info("Validator activated: " + validatorId);
        
        return true;
    }
    
    /**
     * Adds stake to an existing validator
     */
    public boolean addStake(String validatorId, BigInteger amount) {
        ValidatorNode validator = validators.get(validatorId);
        if (validator == null) {
            return false;
        }
        
        BigInteger newTotal = validator.getStakedAmount().add(amount);
        if (newTotal.compareTo(MAX_STAKE_AMOUNT) > 0) {
            LOGGER.warning("Stake would exceed maximum for validator: " + validatorId);
            return false;
        }
        
        validator.addStake(amount);
        totalStaked.addAndGet(amount.longValue());
        
        stateManager.updateBalance(validator.getOperatorAddress().toString(), amount.negate());
        
        eventLog.logEvent("STAKE_ADDED: " + validatorId + " amount " + amount);
        return true;
    }
    
    /**
     * Initiates stake withdrawal (unbonding)
     */
    public boolean initiateUnbond(String validatorId, BigInteger amount) {
        ValidatorNode validator = validators.get(validatorId);
        if (validator == null) {
            return false;
        }
        
        BigInteger remaining = validator.getStakedAmount().subtract(amount);
        if (remaining.compareTo(MIN_STAKE_AMOUNT) < 0 && remaining.compareTo(BigInteger.ZERO) > 0) {
            LOGGER.warning("Remaining stake would be below minimum");
            return false;
        }
        
        // If withdrawing all, set to unbonding status
        if (remaining.compareTo(BigInteger.ZERO) <= 0) {
            validator.setStatus(ValidatorStatus.UNBONDING);
            validator.setUnbondingCompleteTime(new Date(System.currentTimeMillis() + 
                                                       21 * 24 * 60 * 60 * 1000L)); // 21 days
        }
        
        validator.removeStake(amount);
        totalStaked.addAndGet(-amount.longValue());
        pendingUnbonds.put(validatorId, amount);
        
        eventLog.logEvent("UNBOND_INITIATED: " + validatorId + " amount " + amount);
        return true;
    }
    
    /**
     * Slashes a validator for violations
     */
    public SlashingRecord slashValidator(String validatorId, ViolationType violation,
                                        String evidence, List<String> reportingValidators) {
        ValidatorNode validator = validators.get(validatorId);
        if (validator == null) {
            LOGGER.warning("Validator not found for slashing: " + validatorId);
            return null;
        }
        
        // Require multiple validators to report (Byzantine tolerance)
        int requiredReports = (int) Math.ceil(getActiveValidatorCount() * 0.34);
        if (reportingValidators.size() < requiredReports) {
            LOGGER.warning("Insufficient reports for slashing: " + reportingValidators.size() +
                          " < " + requiredReports);
            return null;
        }
        
        // Calculate slash amount
        BigInteger slashAmount = validator.getStakedAmount()
            .multiply(BigInteger.valueOf((long)(violation.getSlashPercentage() * 100)))
            .divide(BigInteger.valueOf(100));
        
        // Create slashing record
        SlashingRecord record = new SlashingRecord(
            validatorId, violation, slashAmount, evidence,
            currentBlockNumber.get(), reportingValidators);
        
        // Apply slash
        validator.recordSlash(slashAmount, record);
        slashingPool = slashingPool.add(slashAmount);
        
        // Reduce reputation
        int reputationPenalty = (int)(violation.getSlashPercentage() * 500);
        validator.setReputation(validator.getReputation() - reputationPenalty);
        
        // Jail if required
        if (violation.isJailRequired()) {
            validator.setStatus(ValidatorStatus.JAILED);
            validator.setJailedUntilBlock(currentBlockNumber.get() + JAIL_DURATION_BLOCKS);
        }
        
        // Remove if stake falls below minimum
        if (validator.getStakedAmount().compareTo(MIN_STAKE_AMOUNT) < 0) {
            validator.setStatus(ValidatorStatus.REMOVED);
            regionValidators.get(validator.getGeographicRegion()).remove(validatorId);
        }
        
        eventLog.logEvent("VALIDATOR_SLASHED: " + validatorId + " for " + violation +
                         " amount " + slashAmount);
        LOGGER.info("Validator slashed: " + validatorId + " for " + violation);
        
        return record;
    }
    
    /**
     * Unjails a validator after serving jail time
     */
    public boolean unjailValidator(String validatorId) {
        ValidatorNode validator = validators.get(validatorId);
        if (validator == null || validator.getStatus() != ValidatorStatus.JAILED) {
            return false;
        }
        
        if (currentBlockNumber.get() < validator.getJailedUntilBlock()) {
            LOGGER.warning("Validator still jailed until block: " + validator.getJailedUntilBlock());
            return false;
        }
        
        if (validator.getStakedAmount().compareTo(MIN_STAKE_AMOUNT) < 0) {
            LOGGER.warning("Validator stake below minimum, cannot unjail");
            return false;
        }
        
        validator.setStatus(ValidatorStatus.ACTIVE);
        validator.setJailedUntilBlock(0);
        
        eventLog.logEvent("VALIDATOR_UNJAILED: " + validatorId);
        return true;
    }
    
    /**
     * Updates validator performance metrics
     */
    public void recordBlockProposed(String validatorId) {
        ValidatorNode validator = validators.get(validatorId);
        if (validator != null) {
            validator.incrementBlocksProposed();
            validator.setLastActiveTime(new Date());
            // Increase reputation for good behavior
            validator.setReputation(validator.getReputation() + 1);
        }
    }
    
    public void recordBlockValidated(String validatorId) {
        ValidatorNode validator = validators.get(validatorId);
        if (validator != null) {
            validator.incrementBlocksValidated();
            validator.setLastActiveTime(new Date());
        }
    }
    
    public void recordMissedBlock(String validatorId) {
        ValidatorNode validator = validators.get(validatorId);
        if (validator != null) {
            validator.incrementMissedBlocks();
            // Decrease reputation for missed blocks
            validator.setReputation(validator.getReputation() - 5);
        }
    }
    
    /**
     * Gets active validators sorted by voting power
     */
    public List<ValidatorNode> getActiveValidatorsByVotingPower() {
        return validators.values().stream()
            .filter(v -> v.getStatus() == ValidatorStatus.ACTIVE)
            .sorted((a, b) -> b.getVotingPower().compareTo(a.getVotingPower()))
            .collect(Collectors.toList());
    }
    
    /**
     * Selects validators for block proposal (round-robin with stake weighting)
     */
    public ValidatorNode selectBlockProposer(long blockNumber) {
        List<ValidatorNode> active = getActiveValidatorsByVotingPower();
        if (active.isEmpty()) {
            return null;
        }
        
        // Simple round-robin weighted by voting power
        int index = (int)(blockNumber % active.size());
        return active.get(index);
    }
    
    /**
     * Distributes slashing pool rewards to active validators
     */
    public void distributeSlashingRewards() {
        if (slashingPool.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }
        
        List<ValidatorNode> activeValidators = getActiveValidatorsByVotingPower();
        if (activeValidators.isEmpty()) {
            return;
        }
        
        BigInteger totalVotingPower = activeValidators.stream()
            .map(ValidatorNode::getVotingPower)
            .reduce(BigInteger.ZERO, BigInteger::add);
        
        for (ValidatorNode validator : activeValidators) {
            BigInteger share = slashingPool
                .multiply(validator.getVotingPower())
                .divide(totalVotingPower);
            validator.addStake(share);
        }
        
        eventLog.logEvent("SLASHING_REWARDS_DISTRIBUTED: " + slashingPool);
        slashingPool = BigInteger.ZERO;
    }
    
    // Helper methods
    private boolean checkGeographicDistribution(int region) {
        long totalActive = validators.values().stream()
            .filter(v -> v.getStatus() == ValidatorStatus.ACTIVE || 
                        v.getStatus() == ValidatorStatus.PENDING)
            .count();
        
        if (totalActive == 0) return true;
        
        long regionCount = regionValidators.get(region).size();
        double futureShare = (double)(regionCount + 1) / (totalActive + 1);
        
        return futureShare <= MAX_GEOGRAPHIC_SHARE;
    }
    
    private String generateValidatorId(String nodeId, Address operatorAddress) {
        String data = nodeId + operatorAddress.toString() + System.currentTimeMillis();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes());
            return Hash.fromBytes(hash).toString().substring(0, 16).toUpperCase();
        } catch (Exception e) {
            return UUID.randomUUID().toString().substring(0, 16).toUpperCase();
        }
    }
    
    private void processUnbonding() {
        Date now = new Date();
        validators.values().stream()
            .filter(v -> v.getStatus() == ValidatorStatus.UNBONDING)
            .filter(v -> v.getUnbondingCompleteTime() != null && 
                        v.getUnbondingCompleteTime().before(now))
            .forEach(v -> {
                BigInteger unbondAmount = pendingUnbonds.remove(v.getValidatorId());
                if (unbondAmount != null) {
                    stateManager.updateBalance(v.getOperatorAddress().toString(), unbondAmount);
                    v.setStatus(ValidatorStatus.REMOVED);
                    eventLog.logEvent("UNBOND_COMPLETE: " + v.getValidatorId());
                }
            });
    }
    
    public void updateBlockNumber(long blockNumber) {
        currentBlockNumber.set(blockNumber);
    }
    
    // Public getters
    public ValidatorNode getValidator(String validatorId) {
        return validators.get(validatorId);
    }
    
    public ValidatorNode getValidatorByAddress(Address address) {
        String validatorId = addressToValidator.get(address);
        return validatorId != null ? validators.get(validatorId) : null;
    }
    
    public Collection<ValidatorNode> getAllValidators() {
        return new ArrayList<>(validators.values());
    }
    
    public long getActiveValidatorCount() {
        return validators.values().stream()
            .filter(v -> v.getStatus() == ValidatorStatus.ACTIVE)
            .count();
    }
    
    public BigInteger getTotalStaked() {
        return BigInteger.valueOf(totalStaked.get());
    }
    
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalValidators", validators.size());
        stats.put("activeValidators", getActiveValidatorCount());
        stats.put("totalStaked", getTotalStaked());
        stats.put("slashingPool", slashingPool);
        stats.put("currentBlock", currentBlockNumber.get());
        
        Map<Integer, Long> regionDistribution = new HashMap<>();
        for (int i = 1; i <= 4; i++) {
            regionDistribution.put(i, (long) regionValidators.get(i).size());
        }
        stats.put("regionDistribution", regionDistribution);
        
        return stats;
    }
    
    public void shutdown() {
        scheduler.shutdown();
        LOGGER.info("ValidatorNodeRegistry shutdown");
    }
}