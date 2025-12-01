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
 * with African sovereignty enforcement
 * 
 * AFRICAN SOVEREIGNTY ENHANCEMENTS:
 * - 5 African regions (North, West, East, Central, Southern)
 * - DAC node designation and tracking (35 nodes, 7 per region)
 * - Committee member selection with reputation requirements
 * - Integration with ReputationScoreValidator (reputation scoring)
 * - Geographic distribution enforcement (max 33% from any region)
 * - Multi-region consensus requirements (3+ regions for quorum)
 * - ASU settlement participation tracking
 * - Cross-region collaboration rewards
 * - Treasury subsidy eligibility
 * - Submarine cable routing compliance
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
    
    // African Geographic Regions
    public enum AfricanRegion {
        NORTH_AFRICA(1),    // Morocco, Egypt, Algeria, Tunisia, Libya
        WEST_AFRICA(2),     // Nigeria, Ghana, Senegal, Ivory Coast, Mali
        EAST_AFRICA(3),     // Kenya, Ethiopia, Tanzania, Uganda, Rwanda
        CENTRAL_AFRICA(4),  // DRC, Cameroon, Gabon, Chad, CAR
        SOUTHERN_AFRICA(5); // South Africa, Zimbabwe, Botswana, Namibia, Angola
        
        private final int code;
        
        AfricanRegion(int code) {
            this.code = code;
        }
        
        public int getCode() { return code; }
        
        public static AfricanRegion fromCode(int code) {
            for (AfricanRegion region : values()) {
                if (region.code == code) return region;
            }
            return WEST_AFRICA; // Default
        }
    }
    
    // Node types in the African sovereignty system
    public enum NodeType {
        STANDARD_VALIDATOR,   // Regular validator
        DAC_NODE,            // Data Availability Committee node (35 total, 7 per region)
        COMMITTEE_MEMBER,    // Rotating committee member (5 per committee)
        RELAY_NODE          // Relay chain node for cross-shard communication
    }
    
    // Staking requirements
    private static final BigInteger MIN_STAKE_AMOUNT = new BigInteger("100000000000000000000"); // 100 tokens
    private static final BigInteger MIN_DAC_NODE_STAKE = new BigInteger("500000000000000000000"); // 500 tokens for DAC
    private static final BigInteger MIN_COMMITTEE_STAKE = new BigInteger("1000000000000000000000"); // 1000 tokens for committee
    private static final BigInteger MAX_STAKE_AMOUNT = new BigInteger("10000000000000000000000"); // 10000 tokens
    
    // Geographic distribution limits
    private static final double MAX_GEOGRAPHIC_SHARE = 0.33; // Max 33% from any region
    private static final int MIN_VALIDATORS_REQUIRED = 21; // Minimum validators
    private static final int MAX_VALIDATORS = 100; // Maximum active validators
    private static final int DAC_NODES_PER_REGION = 7; // 7 DAC nodes per region
    private static final int TOTAL_DAC_NODES = 35; // 5 regions × 7 nodes
    private static final int COMMITTEE_SIZE = 5; // 5-node rotating committees
    
    // Reputation requirements (aligned with ReputationScoreValidator)
    private static final double MIN_VALIDATOR_REPUTATION = 0.60;
    private static final double MIN_DAC_NODE_REPUTATION = 0.50;
    private static final double MIN_COMMITTEE_REPUTATION = 0.65;
    private static final double MIN_TREASURY_SUBSIDY_REPUTATION = 0.60;
    
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
        GOVERNANCE_ABUSE(MAJOR_SLASH_PERCENTAGE, true),     // Abusing governance power
        REGION_CONCENTRATION(MAJOR_SLASH_PERCENTAGE, true), // NEW: Violating geographic distribution
        DAC_ATTESTATION_FAILURE(MINOR_SLASH_PERCENTAGE, false); // NEW: Failed DAC attestation
        
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
     * Represents a registered validator node with African sovereignty tracking
     */
    public static class ValidatorNode {
        private final String validatorId;
        private final String nodeId;
        private final Address operatorAddress;
        private final PublicKey consensusKey;
        private final PublicKey networkKey;
        private final Certificate certificate;
        private final String peerAddress;
        private final AfricanRegion region; // Changed from int to enum
        private final String country;
        private final Date registrationDate;
        
        // Node type and roles
        private NodeType nodeType;
        private boolean isDACNode;
        private boolean isCommitteeMember;
        private int dacNodeId; // 0-34 if DAC node, -1 otherwise
        
        private BigInteger stakedAmount;
        private BigInteger slashedAmount;
        private ValidatorStatus status;
        private double reputationScore; // Changed from int to double (0.0-1.0)
        private long blocksProposed;
        private long blocksValidated;
        private long missedBlocks;
        private Date lastActiveTime;
        private long jailedUntilBlock;
        private Date unbondingCompleteTime;
        private List<SlashingRecord> slashingHistory;
        
        // NEW: African sovereignty tracking
        private final Set<AfricanRegion> collaboratedRegions;
        private long crossRegionValidations;
        private long asuSettlementCount;
        private long dacAttestations;
        private long committeeServiceEpochs;
        private long submarineCableRoutings;
        private long treasurySubsidizedTx;
        
        public ValidatorNode(String validatorId, String nodeId, Address operatorAddress,
                           PublicKey consensusKey, PublicKey networkKey, Certificate certificate,
                           String peerAddress, AfricanRegion region, String country,
                           BigInteger initialStake) {
            this.validatorId = validatorId;
            this.nodeId = nodeId;
            this.operatorAddress = operatorAddress;
            this.consensusKey = consensusKey;
            this.networkKey = networkKey;
            this.certificate = certificate;
            this.peerAddress = peerAddress;
            this.region = region;
            this.country = country;
            this.registrationDate = new Date();
            
            // Node type
            this.nodeType = NodeType.STANDARD_VALIDATOR;
            this.isDACNode = false;
            this.isCommitteeMember = false;
            this.dacNodeId = -1;
            
            this.stakedAmount = initialStake;
            this.slashedAmount = BigInteger.ZERO;
            this.status = ValidatorStatus.PENDING;
            this.reputationScore = 0.50; // Start at neutral (0.50 on 0.0-1.0 scale)
            this.blocksProposed = 0;
            this.blocksValidated = 0;
            this.missedBlocks = 0;
            this.lastActiveTime = new Date();
            this.jailedUntilBlock = 0;
            this.slashingHistory = new ArrayList<>();
            
            // Initialize African sovereignty tracking
            this.collaboratedRegions = Collections.synchronizedSet(new HashSet<>());
            this.collaboratedRegions.add(region); // Always collaborated with own region
            this.crossRegionValidations = 0;
            this.asuSettlementCount = 0;
            this.dacAttestations = 0;
            this.committeeServiceEpochs = 0;
            this.submarineCableRoutings = 0;
            this.treasurySubsidizedTx = 0;
        }
        
        // Getters
        public String getValidatorId() { return validatorId; }
        public String getNodeId() { return nodeId; }
        public Address getOperatorAddress() { return operatorAddress; }
        public PublicKey getConsensusKey() { return consensusKey; }
        public PublicKey getNetworkKey() { return networkKey; }
        public Certificate getCertificate() { return certificate; }
        public String getPeerAddress() { return peerAddress; }
        public AfricanRegion getRegion() { return region; }
        public String getCountry() { return country; }
        public Date getRegistrationDate() { return registrationDate; }
        public NodeType getNodeType() { return nodeType; }
        public boolean isDACNode() { return isDACNode; }
        public boolean isCommitteeMember() { return isCommitteeMember; }
        public int getDACNodeId() { return dacNodeId; }
        public BigInteger getStakedAmount() { return stakedAmount; }
        public BigInteger getSlashedAmount() { return slashedAmount; }
        public ValidatorStatus getStatus() { return status; }
        public double getReputationScore() { return reputationScore; }
        public long getBlocksProposed() { return blocksProposed; }
        public long getBlocksValidated() { return blocksValidated; }
        public long getMissedBlocks() { return missedBlocks; }
        public Date getLastActiveTime() { return lastActiveTime; }
        public long getJailedUntilBlock() { return jailedUntilBlock; }
        public Date getUnbondingCompleteTime() { return unbondingCompleteTime; }
        public List<SlashingRecord> getSlashingHistory() { return new ArrayList<>(slashingHistory); }
        
        // NEW: African sovereignty getters
        public Set<AfricanRegion> getCollaboratedRegions() { return new HashSet<>(collaboratedRegions); }
        public long getCrossRegionValidations() { return crossRegionValidations; }
        public long getASUSettlementCount() { return asuSettlementCount; }
        public long getDACAttestations() { return dacAttestations; }
        public long getCommitteeServiceEpochs() { return committeeServiceEpochs; }
        public long getSubmarineCableRoutings() { return submarineCableRoutings; }
        public long getTreasurySubsidizedTx() { return treasurySubsidizedTx; }
        
        // Setters and modifiers
        public void setStatus(ValidatorStatus status) { this.status = status; }
        public void setReputationScore(double score) { 
            this.reputationScore = Math.max(0.0, Math.min(1.0, score)); 
        }
        public void setLastActiveTime(Date time) { this.lastActiveTime = time; }
        public void setJailedUntilBlock(long block) { this.jailedUntilBlock = block; }
        public void setUnbondingCompleteTime(Date time) { this.unbondingCompleteTime = time; }
        public void setNodeType(NodeType type) { this.nodeType = type; }
        public void setDACNode(boolean isDACNode, int dacNodeId) {
            this.isDACNode = isDACNode;
            this.dacNodeId = dacNodeId;
            if (isDACNode) {
                this.nodeType = NodeType.DAC_NODE;
            }
        }
        public void setCommitteeMember(boolean isCommitteeMember) {
            this.isCommitteeMember = isCommitteeMember;
            if (isCommitteeMember) {
                this.nodeType = NodeType.COMMITTEE_MEMBER;
            }
        }
        
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
        
        // NEW: African sovereignty increment methods
        public void recordCrossRegionValidation(AfricanRegion targetRegion) {
            this.crossRegionValidations++;
            this.collaboratedRegions.add(targetRegion);
        }
        public void incrementASUSettlements() { this.asuSettlementCount++; }
        public void incrementDACAttestations() { this.dacAttestations++; }
        public void incrementCommitteeService() { this.committeeServiceEpochs++; }
        public void incrementSubmarineCableRouting() { this.submarineCableRoutings++; }
        public void incrementTreasurySubsidized() { this.treasurySubsidizedTx++; }
        
        public double getUptime() {
            long total = blocksProposed + blocksValidated + missedBlocks;
            if (total == 0) return 1.0;
            return (double)(blocksProposed + blocksValidated) / total;
        }
        
        /**
         * NEW: Calculates geographic diversity score (0.0 to 1.0)
         */
        public double getGeographicDiversityScore() {
            return (double) collaboratedRegions.size() / 5.0; // 5 regions max
        }
        
        public BigInteger getVotingPower() {
            // Voting power = stake × reputation factor
            BigInteger power = stakedAmount.multiply(
                BigInteger.valueOf((long)(reputationScore * 100))
            ).divide(BigInteger.valueOf(100));
            return power;
        }
        
        /**
         * NEW: Checks if node is eligible for DAC service
         */
        public boolean isEligibleForDAC() {
            return reputationScore >= MIN_DAC_NODE_REPUTATION &&
                   stakedAmount.compareTo(MIN_DAC_NODE_STAKE) >= 0 &&
                   slashingHistory.isEmpty() &&
                   getUptime() >= 0.95;
        }
        
        /**
         * NEW: Checks if node is eligible for committee service
         */
        public boolean isEligibleForCommittee() {
            return reputationScore >= MIN_COMMITTEE_REPUTATION &&
                   stakedAmount.compareTo(MIN_COMMITTEE_STAKE) >= 0 &&
                   slashingHistory.isEmpty() &&
                   crossRegionValidations >= 10 &&
                   collaboratedRegions.size() >= 3 &&
                   getUptime() >= 0.98;
        }
        
        /**
         * NEW: Checks if node qualifies for Treasury subsidies
         */
        public boolean qualifiesForTreasurySubsidy() {
            return reputationScore >= MIN_TREASURY_SUBSIDY_REPUTATION &&
                   slashingHistory.isEmpty();
        }
    }
    
    /**
     * Slashing record
     */
    public static class SlashingRecord {
        private final String validatorId;
        private final ViolationType violationType;
        private final BigInteger slashedAmount;
        private final long blockNumber;
        private final Date timestamp;
        private final String evidence;
        private final String enforcedBy;
        
        public SlashingRecord(String validatorId, ViolationType violationType,
                            BigInteger slashedAmount, long blockNumber,
                            String evidence, String enforcedBy) {
            this.validatorId = validatorId;
            this.violationType = violationType;
            this.slashedAmount = slashedAmount;
            this.blockNumber = blockNumber;
            this.timestamp = new Date();
            this.evidence = evidence;
            this.enforcedBy = enforcedBy;
        }
        
        public String getValidatorId() { return validatorId; }
        public ViolationType getViolationType() { return violationType; }
        public BigInteger getSlashedAmount() { return slashedAmount; }
        public long getBlockNumber() { return blockNumber; }
        public Date getTimestamp() { return timestamp; }
        public String getEvidence() { return evidence; }
        public String getEnforcedBy() { return enforcedBy; }
    }
    
    // Registry state
    private final Map<String, ValidatorNode> validators;
    private final Map<Address, String> addressToValidator;
    private final Map<AfricanRegion, Set<String>> regionValidators; // Changed from int to AfricanRegion
    private final Map<Integer, String> dacNodeAssignments; // DAC node ID -> validator ID
    private final Set<String> committeeMembers;
    
    private final AtomicLong totalStaked;
    private BigInteger slashingPool;
    private final AtomicLong currentBlockNumber;
    private final Map<String, BigInteger> pendingUnbonds;
    
    private final StateManager stateManager;
    private final EventLog eventLog;
    private final PeerNetwork peerNetwork;
    private final PKIManager pkiManager;
    private final CertificateAuthority ca;
    
    private final ScheduledExecutorService scheduler;
    
    public ValidatorNodeRegistry(StateManager stateManager, EventLog eventLog,
                                PeerNetwork peerNetwork, PKIManager pkiManager,
                                CertificateAuthority ca) {
        this.validators = new ConcurrentHashMap<>();
        this.addressToValidator = new ConcurrentHashMap<>();
        this.regionValidators = new ConcurrentHashMap<>();
        this.dacNodeAssignments = new ConcurrentHashMap<>();
        this.committeeMembers = ConcurrentHashMap.newKeySet();
        
        // Initialize region validators for all 5 regions
        for (AfricanRegion region : AfricanRegion.values()) {
            regionValidators.put(region, ConcurrentHashMap.newKeySet());
        }
        
        this.totalStaked = new AtomicLong(0);
        this.slashingPool = BigInteger.ZERO;
        this.currentBlockNumber = new AtomicLong(0);
        this.pendingUnbonds = new ConcurrentHashMap<>();
        
        this.stateManager = stateManager;
        this.eventLog = eventLog;
        this.peerNetwork = peerNetwork;
        this.pkiManager = pkiManager;
        this.ca = ca;
        
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        // Schedule periodic tasks
        scheduler.scheduleAtFixedRate(this::processUnbonding, 1, 1, TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(this::checkValidatorUptime, 5, 5, TimeUnit.MINUTES);
        
        LOGGER.info("ValidatorNodeRegistry initialized with 5 African regions and 35 DAC node slots");
    }
    
    /**
     * Registers a new validator node with African sovereignty tracking
     */
    public String registerValidator(String nodeId, Address operatorAddress,
                                   PublicKey consensusKey, PublicKey networkKey,
                                   String peerAddress, AfricanRegion region, String country,
                                   BigInteger stakeAmount) {
        
        // Validate stake amount
        if (stakeAmount.compareTo(MIN_STAKE_AMOUNT) < 0) {
            LOGGER.warning("Stake amount below minimum: " + stakeAmount);
            return null;
        }
        
        if (stakeAmount.compareTo(MAX_STAKE_AMOUNT) > 0) {
            LOGGER.warning("Stake amount above maximum: " + stakeAmount);
            return null;
        }
        
        // Check geographic distribution (max 33%)
        if (!checkGeographicDistribution(region)) {
            LOGGER.warning("Geographic distribution violated - cannot add validator from " + region);
            return null;
        }
        
        // Check max validators
        if (validators.size() >= MAX_VALIDATORS) {
            LOGGER.warning("Maximum validators reached: " + MAX_VALIDATORS);
            return null;
        }
        
        // Verify certificate
        Certificate cert;
        try {
            cert = ca.issueCertificate(operatorAddress.toString(), consensusKey);
        } catch (Exception e) {
            LOGGER.severe("Failed to issue certificate: " + e.getMessage());
            return null;
        }
        
        // Generate validator ID
        String validatorId = generateValidatorId(nodeId, operatorAddress);
        
        // Create validator node
        ValidatorNode validator = new ValidatorNode(
            validatorId, nodeId, operatorAddress, consensusKey, networkKey,
            cert, peerAddress, region, country, stakeAmount
        );
        
        // Deduct stake from operator balance
        if (!stateManager.deductBalance(operatorAddress.toString(), stakeAmount)) {
            LOGGER.warning("Failed to deduct stake from operator balance");
            return null;
        }
        
        validators.put(validatorId, validator);
        addressToValidator.put(operatorAddress, validatorId);
        regionValidators.get(region).add(validatorId);
        totalStaked.addAndGet(stakeAmount.longValue());
        
        eventLog.logEvent("VALIDATOR_REGISTERED: " + validatorId + " from " + region);
        LOGGER.info(String.format("Registered validator %s from %s with stake %s",
            validatorId, region, stakeAmount));
        
        return validatorId;
    }
    
    /**
     * NEW: Designates a validator as a DAC node
     */
    public boolean designateAsDACNode(String validatorId, int dacNodeId) {
        ValidatorNode validator = validators.get(validatorId);
        if (validator == null) {
            LOGGER.warning("Validator not found: " + validatorId);
            return false;
        }
        
        // Validate DAC node ID is valid for this region
        if (!isValidDACNodeForRegion(dacNodeId, validator.getRegion())) {
            LOGGER.warning(String.format("DAC node %d is not valid for region %s",
                dacNodeId, validator.getRegion()));
            return false;
        }
        
        // Check if DAC node slot is already taken
        if (dacNodeAssignments.containsKey(dacNodeId)) {
            LOGGER.warning("DAC node slot already taken: " + dacNodeId);
            return false;
        }
        
        // Check eligibility
        if (!validator.isEligibleForDAC()) {
            LOGGER.warning(String.format("Validator %s not eligible for DAC (reputation: %.2f, stake: %s)",
                validatorId, validator.getReputationScore(), validator.getStakedAmount()));
            return false;
        }
        
        validator.setDACNode(true, dacNodeId);
        dacNodeAssignments.put(dacNodeId, validatorId);
        
        eventLog.logEvent(String.format("DAC_NODE_DESIGNATED: %s as node %d in %s",
            validatorId, dacNodeId, validator.getRegion()));
        
        return true;
    }
    
    /**
     * NEW: Validates if a DAC node ID belongs to a region
     */
    private boolean isValidDACNodeForRegion(int nodeId, AfricanRegion region) {
        switch (region) {
            case NORTH_AFRICA: return nodeId >= 0 && nodeId <= 6;
            case WEST_AFRICA: return nodeId >= 7 && nodeId <= 13;
            case EAST_AFRICA: return nodeId >= 14 && nodeId <= 20;
            case CENTRAL_AFRICA: return nodeId >= 21 && nodeId <= 27;
            case SOUTHERN_AFRICA: return nodeId >= 28 && nodeId <= 34;
            default: return false;
        }
    }
    
    /**
     * NEW: Designates a validator as a committee member
     */
    public boolean designateAsCommitteeMember(String validatorId) {
        ValidatorNode validator = validators.get(validatorId);
        if (validator == null) {
            LOGGER.warning("Validator not found: " + validatorId);
            return false;
        }
        
        // Check if already at max committee members
        if (committeeMembers.size() >= COMMITTEE_SIZE) {
            LOGGER.warning("Committee already at maximum size: " + COMMITTEE_SIZE);
            return false;
        }
        
        // Check eligibility
        if (!validator.isEligibleForCommittee()) {
            LOGGER.warning(String.format("Validator %s not eligible for committee " +
                "(reputation: %.2f, cross-region: %d, regions: %d)",
                validatorId, validator.getReputationScore(),
                validator.getCrossRegionValidations(),
                validator.getCollaboratedRegions().size()));
            return false;
        }
        
        validator.setCommitteeMember(true);
        committeeMembers.add(validatorId);
        
        eventLog.logEvent(String.format("COMMITTEE_MEMBER_DESIGNATED: %s from %s",
            validatorId, validator.getRegion()));
        
        return true;
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
            LOGGER.warning("Validator not in PENDING status: " + validator.getStatus());
            return false;
        }
        
        // Check reputation meets minimum
        if (validator.getReputationScore() < MIN_VALIDATOR_REPUTATION) {
            LOGGER.warning(String.format("Validator reputation %.2f below minimum %.2f",
                validator.getReputationScore(), MIN_VALIDATOR_REPUTATION));
            return false;
        }
        
        // Check minimum validators requirement
        long activeCount = getActiveValidatorCount();
        if (activeCount < MIN_VALIDATORS_REQUIRED - 1) {
            // Allow activation if we need more validators
            validator.setStatus(ValidatorStatus.ACTIVE);
            eventLog.logEvent("VALIDATOR_ACTIVATED: " + validatorId);
            LOGGER.info("Activated validator: " + validatorId);
            return true;
        }
        
        // Check geographic distribution again
        if (!checkGeographicDistribution(validator.getRegion())) {
            LOGGER.warning("Geographic distribution would be violated");
            return false;
        }
        
        validator.setStatus(ValidatorStatus.ACTIVE);
        
        // Add to peer network
        peerNetwork.addPeer(new Peer(validator.getNodeId(), validator.getPeerAddress()));
        
        eventLog.logEvent("VALIDATOR_ACTIVATED: " + validatorId);
        LOGGER.info("Activated validator: " + validatorId);
        
        return true;
    }
    
    /**
     * Slashes a validator for violations
     */
    public boolean slashValidator(String validatorId, ViolationType violationType,
                                 String evidence, String enforcedBy) {
        ValidatorNode validator = validators.get(validatorId);
        if (validator == null) {
            LOGGER.warning("Validator not found: " + validatorId);
            return false;
        }
        
        BigInteger slashAmount = validator.getStakedAmount()
            .multiply(BigInteger.valueOf((long)(violationType.getSlashPercentage() * 100)))
            .divide(BigInteger.valueOf(100));
        
        SlashingRecord record = new SlashingRecord(
            validatorId, violationType, slashAmount,
            currentBlockNumber.get(), evidence, enforcedBy
        );
        
        validator.recordSlash(slashAmount, record);
        slashingPool = slashingPool.add(slashAmount);
        totalStaked.addAndGet(-slashAmount.longValue());
        
        // Apply reputation penalty
        double reputationPenalty = violationType.getSlashPercentage() * 0.5; // 50% of slash as reputation penalty
        validator.setReputationScore(validator.getReputationScore() - reputationPenalty);
        
        if (violationType.isJailRequired()) {
            validator.setStatus(ValidatorStatus.JAILED);
            validator.setJailedUntilBlock(currentBlockNumber.get() + JAIL_DURATION_BLOCKS);
            
            // Remove from DAC and committee if assigned
            if (validator.isDACNode()) {
                dacNodeAssignments.remove(validator.getDACNodeId());
                validator.setDACNode(false, -1);
            }
            if (validator.isCommitteeMember()) {
                committeeMembers.remove(validatorId);
                validator.setCommitteeMember(false);
            }
        }
        
        eventLog.logEvent(String.format("VALIDATOR_SLASHED: %s for %s, amount: %s",
            validatorId, violationType, slashAmount));
        LOGGER.warning(String.format("Slashed validator %s: %s, amount %s",
            validatorId, violationType, slashAmount));
        
        return true;
    }
    
    /**
     * Initiates unbonding for a validator
     */
    public boolean unbondValidator(String validatorId) {
        ValidatorNode validator = validators.get(validatorId);
        if (validator == null) {
            LOGGER.warning("Validator not found: " + validatorId);
            return false;
        }
        
        if (validator.getStatus() == ValidatorStatus.UNBONDING) {
            LOGGER.warning("Validator already unbonding");
            return false;
        }
        
        validator.setStatus(ValidatorStatus.UNBONDING);
        
        // Set unbonding completion time (21 days)
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 21);
        validator.setUnbondingCompleteTime(cal.getTime());
        
        pendingUnbonds.put(validatorId, validator.getStakedAmount());
        
        // Remove from active sets
        regionValidators.get(validator.getRegion()).remove(validatorId);
        if (validator.isDACNode()) {
            dacNodeAssignments.remove(validator.getDACNodeId());
            validator.setDACNode(false, -1);
        }
        if (validator.isCommitteeMember()) {
            committeeMembers.remove(validatorId);
            validator.setCommitteeMember(false);
        }
        
        eventLog.logEvent("UNBOND_INITIATED: " + validatorId);
        return true;
    }
    
    /**
     * Unjails a validator after jail period
     */
    public boolean unjailValidator(String validatorId) {
        ValidatorNode validator = validators.get(validatorId);
        if (validator == null) {
            LOGGER.warning("Validator not found: " + validatorId);
            return false;
        }
        
        if (validator.getStatus() != ValidatorStatus.JAILED) {
            LOGGER.warning("Validator not jailed");
            return false;
        }
        
        if (currentBlockNumber.get() < validator.getJailedUntilBlock()) {
            LOGGER.warning("Jail period not complete");
            return false;
        }
        
        if (validator.getStakedAmount().compareTo(MIN_STAKE_AMOUNT) < 0) {
            LOGGER.warning("Validator stake below minimum, cannot unjail");
            return false;
        }
        
        validator.setStatus(ValidatorStatus.ACTIVE);
        validator.setJailedUntilBlock(0);
        
        // Restore to region validators
        regionValidators.get(validator.getRegion()).add(validatorId);
        
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
            validator.setReputationScore(validator.getReputationScore() + 0.001);
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
            validator.setReputationScore(validator.getReputationScore() - 0.005);
        }
    }
    
    /**
     * NEW: Records cross-region validation
     */
    public void recordCrossRegionValidation(String validatorId, AfricanRegion targetRegion) {
        ValidatorNode validator = validators.get(validatorId);
        if (validator != null) {
            validator.recordCrossRegionValidation(targetRegion);
            // Bonus for cross-region collaboration
            validator.setReputationScore(validator.getReputationScore() + 0.002);
        }
    }
    
    /**
     * NEW: Records ASU settlement participation
     */
    public void recordASUSettlement(String validatorId) {
        ValidatorNode validator = validators.get(validatorId);
        if (validator != null) {
            validator.incrementASUSettlements();
            validator.setReputationScore(validator.getReputationScore() + 0.001);
        }
    }
    
    /**
     * NEW: Records DAC attestation
     */
    public void recordDACAttestation(String validatorId, boolean successful) {
        ValidatorNode validator = validators.get(validatorId);
        if (validator != null && validator.isDACNode()) {
            if (successful) {
                validator.incrementDACAttestations();
                validator.setReputationScore(validator.getReputationScore() + 0.002);
            } else {
                validator.setReputationScore(validator.getReputationScore() - 0.003);
            }
        }
    }
    
    /**
     * NEW: Records committee service
     */
    public void recordCommitteeService(String validatorId) {
        ValidatorNode validator = validators.get(validatorId);
        if (validator != null && validator.isCommitteeMember()) {
            validator.incrementCommitteeService();
            validator.setReputationScore(validator.getReputationScore() + 0.005);
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
     * NEW: Gets active validators by region
     */
    public List<ValidatorNode> getActiveValidatorsByRegion(AfricanRegion region) {
        return validators.values().stream()
            .filter(v -> v.getStatus() == ValidatorStatus.ACTIVE)
            .filter(v -> v.getRegion() == region)
            .sorted((a, b) -> b.getVotingPower().compareTo(a.getVotingPower()))
            .collect(Collectors.toList());
    }
    
    /**
     * NEW: Gets eligible DAC nodes for a region
     */
    public List<ValidatorNode> getEligibleDACNodesForRegion(AfricanRegion region) {
        return validators.values().stream()
            .filter(v -> v.getRegion() == region)
            .filter(ValidatorNode::isEligibleForDAC)
            .sorted((a, b) -> Double.compare(b.getReputationScore(), a.getReputationScore()))
            .collect(Collectors.toList());
    }
    
    /**
     * NEW: Gets eligible committee members (with multi-region balance)
     */
    public List<ValidatorNode> getEligibleCommitteeMembers() {
        Map<AfricanRegion, List<ValidatorNode>> byRegion = new EnumMap<>(AfricanRegion.class);
        
        // Group eligible validators by region
        for (ValidatorNode validator : validators.values()) {
            if (validator.isEligibleForCommittee()) {
                byRegion.computeIfAbsent(validator.getRegion(), k -> new ArrayList<>())
                       .add(validator);
            }
        }
        
        // Select top validator from each region
        List<ValidatorNode> committee = new ArrayList<>();
        for (AfricanRegion region : AfricanRegion.values()) {
            List<ValidatorNode> regionValidators = byRegion.get(region);
            if (regionValidators != null && !regionValidators.isEmpty()) {
                // Sort by reputation and pick top
                regionValidators.sort((a, b) -> 
                    Double.compare(b.getReputationScore(), a.getReputationScore()));
                committee.add(regionValidators.get(0));
            }
        }
        
        return committee;
    }
    
    /**
     * Selects validators for block proposal
     */
    public ValidatorNode selectBlockProposer(long blockNumber) {
        List<ValidatorNode> active = getActiveValidatorsByVotingPower();
        if (active.isEmpty()) {
            return null;
        }
        
        // Round-robin weighted by voting power
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
    private boolean checkGeographicDistribution(AfricanRegion region) {
        long totalActive = validators.values().stream()
            .filter(v -> v.getStatus() == ValidatorStatus.ACTIVE || 
                        v.getStatus() == ValidatorStatus.PENDING)
            .count();
        
        if (totalActive == 0) return true;
        
        long regionCount = regionValidators.get(region).size();
        double futureShare = (double)(regionCount + 1) / (totalActive + 1);
        
        return futureShare <= MAX_GEOGRAPHIC_SHARE;
    }
    
    /**
     * NEW: Checks if validator distribution is balanced across regions
     */
    public boolean hasBalancedDistribution() {
        long totalActive = validators.values().stream()
            .filter(v -> v.getStatus() == ValidatorStatus.ACTIVE)
            .count();
        
        if (totalActive == 0) return true;
        
        for (AfricanRegion region : AfricanRegion.values()) {
            long regionCount = regionValidators.get(region).size();
            double share = (double) regionCount / totalActive;
            
            if (share > MAX_GEOGRAPHIC_SHARE) {
                return false;
            }
        }
        
        return true;
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
                    totalStaked.addAndGet(-unbondAmount.longValue());
                    eventLog.logEvent("UNBOND_COMPLETE: " + v.getValidatorId());
                }
            });
    }
    
    private void checkValidatorUptime() {
        // Check for validators with extended downtime
        Date cutoff = new Date(System.currentTimeMillis() - (24 * 60 * 60 * 1000)); // 24 hours
        
        for (ValidatorNode validator : validators.values()) {
            if (validator.getStatus() == ValidatorStatus.ACTIVE &&
                validator.getLastActiveTime().before(cutoff)) {
                // Slash for downtime
                slashValidator(validator.getValidatorId(), ViolationType.DOWNTIME,
                    "Extended downtime detected", "SYSTEM");
            }
        }
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
    
    public ValidatorNode getValidatorByDACNodeId(int dacNodeId) {
        String validatorId = dacNodeAssignments.get(dacNodeId);
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
    
    /**
     * NEW: Gets DAC node count
     */
    public int getDACNodeCount() {
        return dacNodeAssignments.size();
    }
    
    /**
     * NEW: Gets committee member count
     */
    public int getCommitteeMemberCount() {
        return committeeMembers.size();
    }
    
    /**
     * Gets registry statistics with African sovereignty metrics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalValidators", validators.size());
        stats.put("activeValidators", getActiveValidatorCount());
        stats.put("totalStaked", getTotalStaked());
        stats.put("slashingPool", slashingPool);
        stats.put("currentBlock", currentBlockNumber.get());
        
        // Regional distribution
        Map<String, Long> regionDistribution = new HashMap<>();
        for (AfricanRegion region : AfricanRegion.values()) {
            regionDistribution.put(region.toString(), (long) regionValidators.get(region).size());
        }
        stats.put("regionDistribution", regionDistribution);
        stats.put("distributionBalanced", hasBalancedDistribution());
        
        // African sovereignty metrics
        stats.put("dacNodes", getDACNodeCount());
        stats.put("committeeMembers", getCommitteeMemberCount());
        
        long avgCrossRegionValidations = validators.values().stream()
            .mapToLong(ValidatorNode::getCrossRegionValidations)
            .sum() / Math.max(1, validators.size());
        stats.put("avgCrossRegionValidations", avgCrossRegionValidations);
        
        long totalASUSettlements = validators.values().stream()
            .mapToLong(ValidatorNode::getASUSettlementCount)
            .sum();
        stats.put("totalASUSettlements", totalASUSettlements);
        
        long totalDACAttestations = validators.values().stream()
            .mapToLong(ValidatorNode::getDACAttestations)
            .sum();
        stats.put("totalDACAttestations", totalDACAttestations);
        
        return stats;
    }
    
    public void shutdown() {
        scheduler.shutdown();
        LOGGER.info("ValidatorNodeRegistry shutdown");
    }
}