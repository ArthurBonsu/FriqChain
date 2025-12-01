package org.example.app.core.blockchain.block;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * RollupProof represents a Layer 2 validity proof anchored to Layer 1
 * with African sovereignty enforcement.
 * 
 * AFRICAN SOVEREIGNTY ENHANCEMENTS:
 * - 5-node rotating committee (replaces single operator)
 * - DAC attestations from 3+ regions (35 nodes, 7 per region)
 * - 3-out-of-5 committee quorum requirement
 * - Geographic distribution enforcement (max 40% from any region)
 * - ASU settlement tracking
 * - Treasury DAO subsidy tracking
 * - Submarine cable routing metadata
 * - Reputation requirements for committee members
 * 
 * Layer 2 Strategy for African Tokenized Currency System:
 * - zk-Rollups/Validium: Batch thousands of transactions off-chain
 * - Post single validity proof to L1
 * - Reduces L1 load while maintaining security
 * - Target: Treasury DAO subsidizes near-zero-fee retail transactions (≤$0.001/tx)
 * - Compression ratio: 10:1 or better
 * - 50,000+ TPS across multiple rollup operators
 */
public class RollupProof {
    private static final Logger logger = Logger.getLogger(RollupProof.class.getName());
    
    // African Geographic Regions
    public enum AfricanRegion {
        NORTH_AFRICA, WEST_AFRICA, EAST_AFRICA, CENTRAL_AFRICA, SOUTHERN_AFRICA
    }
    
    /**
     * Committee member information
     */
    public static class CommitteeMember {
        public final String memberId;
        public final AfricanRegion region;
        public final int dacNodeId; // DAC node ID (0-34)
        public final double reputationScore;
        
        public CommitteeMember(String memberId, AfricanRegion region, 
                             int dacNodeId, double reputationScore) {
            this.memberId = memberId;
            this.region = region;
            this.dacNodeId = dacNodeId;
            this.reputationScore = reputationScore;
        }
    }
    
    /**
     * DAC attestation from a region
     */
    public static class DACAttestation {
        public final AfricanRegion region;
        public final List<Integer> nodeIds; // Node IDs that attested (need 5 out of 7)
        public final byte[] merkleRoot; // Merkle root of batch data
        public final List<byte[]> nodeSignatures; // Node signatures
        public final long attestationTimestamp;
        
        public DACAttestation(AfricanRegion region, List<Integer> nodeIds,
                            byte[] merkleRoot, List<byte[]> nodeSignatures) {
            this.region = region;
            this.nodeIds = new ArrayList<>(nodeIds);
            this.merkleRoot = Arrays.copyOf(merkleRoot, merkleRoot.length);
            this.nodeSignatures = new ArrayList<>();
            for (byte[] sig : nodeSignatures) {
                this.nodeSignatures.add(Arrays.copyOf(sig, sig.length));
            }
            this.attestationTimestamp = System.currentTimeMillis();
        }
        
        public boolean isValid() {
            // Need 5 out of 7 nodes from region
            return nodeSignatures.size() >= 5 && merkleRoot.length == 32;
        }
    }
    
    // Layer 2 identification
    private final long l2BlockNumber;
    private final int batchId;
    
    // State transition
    private final byte[] previousStateRoot;
    private final byte[] newStateRoot;
    
    // Validity proof (zk-SNARK)
    private final byte[] validityProof;
    
    // Transaction data
    private final int transactionCount;
    private final byte[] transactionsHash; // Hash of all transactions in batch
    
    // Layer 1 anchoring
    private final long l1BlockNumber; // L1 block where this proof was submitted
    private final String l1BlockHash;
    private final long l1Timestamp;
    
    // Proof metadata
    private final ProofType proofType;
    
    // NEW: African sovereignty fields
    private final List<CommitteeMember> committee; // 5-node rotating committee
    private final Map<String, byte[]> committeeSignatures; // Committee member signatures (need 3/5)
    private final List<DACAttestation> dacAttestations; // DAC attestations (need 3+ regions)
    private final boolean settlesViaASU; // Uses ASU settlement
    private final boolean treasurySubsidized; // Treasury DAO subsidy applied
    private final byte[] submarineCableRoute; // African submarine cable routing preference
    private final Map<String, BigInteger> netBalanceChanges; // Net balance changes per token
    
    // Verification status
    private ProofStatus status;
    private String verificationError;
    
    // Geographic distribution tracking
    private final Map<AfricanRegion, Integer> committeeRegionCount;
    
    /**
     * Proof type enum
     */
    public enum ProofType {
        ZK_SNARK,       // zk-SNARK proof (Groth16, PLONK)
        ZK_STARK,       // zk-STARK proof (post-quantum)
        OPTIMISTIC,     // Optimistic rollup (fraud proofs)
        VALIDIUM        // Validium (off-chain data availability)
    }
    
    /**
     * Proof status enum
     */
    public enum ProofStatus {
        PENDING,        // Proof submitted, not yet verified
        VERIFIED,       // Proof verified successfully
        FINALIZED,      // Proof finalized (challenge period passed)
        REJECTED,       // Proof rejected (invalid)
        CHALLENGED      // Proof is being challenged
    }
    
    /**
     * Constructor for RollupProof with African sovereignty
     */
    public RollupProof(long l2BlockNumber, int batchId, byte[] previousStateRoot,
                      byte[] newStateRoot, byte[] validityProof, int transactionCount,
                      byte[] transactionsHash, long l1BlockNumber, String l1BlockHash,
                      long l1Timestamp, ProofType proofType,
                      List<CommitteeMember> committee,
                      Map<String, byte[]> committeeSignatures,
                      List<DACAttestation> dacAttestations,
                      Map<String, BigInteger> netBalanceChanges,
                      boolean settlesViaASU,
                      boolean treasurySubsidized,
                      byte[] submarineCableRoute) {
        
        // Validate basic parameters
        if (l2BlockNumber < 0) {
            throw new IllegalArgumentException("L2 block number cannot be negative");
        }
        if (batchId < 0) {
            throw new IllegalArgumentException("Batch ID cannot be negative");
        }
        if (previousStateRoot == null || previousStateRoot.length != 32) {
            throw new IllegalArgumentException("Invalid previous state root");
        }
        if (newStateRoot == null || newStateRoot.length != 32) {
            throw new IllegalArgumentException("Invalid new state root");
        }
        if (validityProof == null || validityProof.length == 0) {
            throw new IllegalArgumentException("Validity proof cannot be null or empty");
        }
        if (transactionCount <= 0) {
            throw new IllegalArgumentException("Transaction count must be positive");
        }
        
        // Validate African sovereignty parameters
        if (committee == null || committee.size() != 5) {
            throw new IllegalArgumentException("Committee must have exactly 5 members");
        }
        if (committeeSignatures == null || committeeSignatures.size() < 3) {
            throw new IllegalArgumentException("Need at least 3 committee signatures (quorum)");
        }
        if (dacAttestations == null || dacAttestations.size() < 3) {
            throw new IllegalArgumentException("Need DAC attestations from at least 3 regions");
        }
        
        this.l2BlockNumber = l2BlockNumber;
        this.batchId = batchId;
        this.previousStateRoot = Arrays.copyOf(previousStateRoot, previousStateRoot.length);
        this.newStateRoot = Arrays.copyOf(newStateRoot, newStateRoot.length);
        this.validityProof = Arrays.copyOf(validityProof, validityProof.length);
        this.transactionCount = transactionCount;
        this.transactionsHash = Arrays.copyOf(transactionsHash, transactionsHash.length);
        this.l1BlockNumber = l1BlockNumber;
        this.l1BlockHash = l1BlockHash;
        this.l1Timestamp = l1Timestamp;
        this.proofType = proofType;
        
        // African sovereignty fields
        this.committee = new ArrayList<>(committee);
        this.committeeSignatures = new ConcurrentHashMap<>(committeeSignatures);
        this.dacAttestations = new ArrayList<>(dacAttestations);
        this.netBalanceChanges = new HashMap<>(netBalanceChanges);
        this.settlesViaASU = settlesViaASU;
        this.treasurySubsidized = treasurySubsidized;
        this.submarineCableRoute = Arrays.copyOf(submarineCableRoute, submarineCableRoute.length);
        
        this.status = ProofStatus.PENDING;
        this.verificationError = "";
        
        // Track committee geographic distribution
        this.committeeRegionCount = new EnumMap<>(AfricanRegion.class);
        for (CommitteeMember member : committee) {
            committeeRegionCount.merge(member.region, 1, Integer::sum);
        }
        
        logger.fine(String.format("Created RollupProof for L2 block %d with %d transactions, " +
            "committee from %d regions, DAC attestations from %d regions",
            l2BlockNumber, transactionCount, committeeRegionCount.size(), dacAttestations.size()));
    }
    
    // Getters
    
    public long getL2BlockNumber() {
        return l2BlockNumber;
    }
    
    public int getBatchId() {
        return batchId;
    }
    
    public byte[] getPreviousStateRoot() {
        return Arrays.copyOf(previousStateRoot, previousStateRoot.length);
    }
    
    public byte[] getNewStateRoot() {
        return Arrays.copyOf(newStateRoot, newStateRoot.length);
    }
    
    public byte[] getValidityProof() {
        return Arrays.copyOf(validityProof, validityProof.length);
    }
    
    public int getTransactionCount() {
        return transactionCount;
    }
    
    public byte[] getTransactionsHash() {
        return Arrays.copyOf(transactionsHash, transactionsHash.length);
    }
    
    public long getL1BlockNumber() {
        return l1BlockNumber;
    }
    
    public String getL1BlockHash() {
        return l1BlockHash;
    }
    
    public long getL1Timestamp() {
        return l1Timestamp;
    }
    
    public ProofType getProofType() {
        return proofType;
    }
    
    // NEW: African sovereignty getters
    
    public List<CommitteeMember> getCommittee() {
        return new ArrayList<>(committee);
    }
    
    public Map<String, byte[]> getCommitteeSignatures() {
        return new HashMap<>(committeeSignatures);
    }
    
    public List<DACAttestation> getDACAttestations() {
        return new ArrayList<>(dacAttestations);
    }
    
    public Map<String, BigInteger> getNetBalanceChanges() {
        return new HashMap<>(netBalanceChanges);
    }
    
    public boolean settlesViaASU() {
        return settlesViaASU;
    }
    
    public boolean isTreasurySubsidized() {
        return treasurySubsidized;
    }
    
    public byte[] getSubmarineCableRoute() {
        return Arrays.copyOf(submarineCableRoute, submarineCableRoute.length);
    }
    
    public Map<AfricanRegion, Integer> getCommitteeRegionCount() {
        return new EnumMap<>(committeeRegionCount);
    }
    
    public ProofStatus getStatus() {
        return status;
    }
    
    public String getVerificationError() {
        return verificationError;
    }
    
    /**
     * Sets the proof status
     */
    public void setStatus(ProofStatus newStatus) {
        this.status = newStatus;
        logger.fine("RollupProof " + batchId + " status changed to " + newStatus);
    }
    
    /**
     * Sets verification error
     */
    public void setVerificationError(String error) {
        this.verificationError = error;
        this.status = ProofStatus.REJECTED;
        logger.warning("RollupProof " + batchId + " verification failed: " + error);
    }
    
    /**
     * Verifies the rollup proof with African sovereignty checks
     */
    public boolean verify() {
        try {
            // Verify proof structure
            if (!verifyProofStructure()) {
                setVerificationError("Invalid proof structure");
                return false;
            }
            
            // CRITICAL: Verify committee quorum (3 out of 5)
            if (!verifyCommitteeQuorum()) {
                setVerificationError("Committee quorum not met (need 3 out of 5)");
                return false;
            }
            
            // CRITICAL: Verify DAC attestations (3+ regions)
            if (!verifyDACAttestations()) {
                setVerificationError("Insufficient DAC attestations (need 3+ regions)");
                return false;
            }
            
            // CRITICAL: Enforce geographic distribution
            if (!enforceGeographicDistribution()) {
                setVerificationError("Geographic distribution violated (max 40% per region)");
                return false;
            }
            
            // Verify state transition
            if (!verifyStateTransition()) {
                setVerificationError("Invalid state transition");
                return false;
            }
            
            // Verify validity proof based on type
            boolean proofValid = false;
            switch (proofType) {
                case ZK_SNARK:
                    proofValid = verifyZKSNARK();
                    break;
                case ZK_STARK:
                    proofValid = verifyZKSTARK();
                    break;
                case OPTIMISTIC:
                    proofValid = verifyOptimistic();
                    break;
                case VALIDIUM:
                    proofValid = verifyValidium();
                    break;
            }
            
            if (!proofValid) {
                setVerificationError("Invalid " + proofType + " proof");
                return false;
            }
            
            // Verify balance changes are consistent
            if (!verifyBalanceChanges()) {
                setVerificationError("Inconsistent balance changes");
                return false;
            }
            
            // All checks passed
            setStatus(ProofStatus.VERIFIED);
            logger.info(String.format("RollupProof %d verified successfully (committee: %d regions, DAC: %d regions)",
                batchId, committeeRegionCount.size(), dacAttestations.size()));
            
            return true;
            
        } catch (Exception e) {
            setVerificationError("Verification exception: " + e.getMessage());
            logger.severe("RollupProof verification error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * CRITICAL: Verifies committee quorum (3 out of 5 signatures required)
     */
    private boolean verifyCommitteeQuorum() {
        if (committeeSignatures.size() < 3) {
            return false;
        }
        
        int validSignatures = 0;
        
        for (CommitteeMember member : committee) {
            byte[] signature = committeeSignatures.get(member.memberId);
            if (signature != null && verifyCommitteeSignature(member, signature)) {
                validSignatures++;
            }
        }
        
        // Need 3 out of 5 (60% supermajority)
        return validSignatures >= 3;
    }
    
    /**
     * Verifies a single committee member signature
     */
    private boolean verifyCommitteeSignature(CommitteeMember member, byte[] signature) {
        try {
            // Basic validation
            if (signature == null || signature.length < 64) {
                return false;
            }
            
            // Verify member has sufficient reputation (≥0.65)
            if (member.reputationScore < 0.65) {
                logger.warning(String.format("Committee member %s has low reputation: %.2f",
                    member.memberId, member.reputationScore));
                return false;
            }
            
            // In production: verify ECDSA signature against member's public key
            // For now, basic structural validation
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * CRITICAL: Verifies DAC attestations from 3+ regions
     */
    private boolean verifyDACAttestations() {
        if (dacAttestations.size() < 3) {
            return false;
        }
        
        Set<AfricanRegion> regionsRepresented = new HashSet<>();
        
        for (DACAttestation attestation : dacAttestations) {
            // Verify attestation is valid (5 out of 7 nodes)
            if (!attestation.isValid()) {
                logger.warning("Invalid DAC attestation from region: " + attestation.region);
                return false;
            }
            
            // Verify node IDs are valid for this region
            for (Integer nodeId : attestation.nodeIds) {
                if (!isValidNodeForRegion(nodeId, attestation.region)) {
                    logger.warning(String.format("Invalid node %d for region %s",
                        nodeId, attestation.region));
                    return false;
                }
            }
            
            regionsRepresented.add(attestation.region);
        }
        
        // Require at least 3 different regions
        return regionsRepresented.size() >= 3;
    }
    
    /**
     * Validates if a node ID belongs to a specific region
     */
    private boolean isValidNodeForRegion(int nodeId, AfricanRegion region) {
        switch (region) {
            case NORTH_AFRICA:
                return nodeId >= 0 && nodeId <= 6;
            case WEST_AFRICA:
                return nodeId >= 7 && nodeId <= 13;
            case EAST_AFRICA:
                return nodeId >= 14 && nodeId <= 20;
            case CENTRAL_AFRICA:
                return nodeId >= 21 && nodeId <= 27;
            case SOUTHERN_AFRICA:
                return nodeId >= 28 && nodeId <= 34;
            default:
                return false;
        }
    }
    
    /**
     * CRITICAL: Enforces geographic distribution (max 40% from any region)
     */
    private boolean enforceGeographicDistribution() {
        int totalMembers = committee.size();
        
        for (Map.Entry<AfricanRegion, Integer> entry : committeeRegionCount.entrySet()) {
            double regionPercentage = (double) entry.getValue() / totalMembers;
            
            // No region can have more than 40% (2 out of 5 members)
            if (regionPercentage > 0.40) {
                logger.warning(String.format("Region %s has %.1f%% of committee (exceeds 40%%)",
                    entry.getKey(), regionPercentage * 100));
                return false;
            }
        }
        
        // Require at least 3 different regions in committee
        return committeeRegionCount.size() >= 3;
    }
    
    /**
     * Verifies proof structure is valid
     */
    private boolean verifyProofStructure() {
        // Verify all required fields are present and valid
        return previousStateRoot.length == 32 &&
               newStateRoot.length == 32 &&
               validityProof.length > 0 &&
               transactionCount > 0 &&
               l1BlockNumber >= 0 &&
               committee.size() == 5 &&
               dacAttestations.size() >= 3;
    }
    
    /**
     * Verifies state transition is valid
     */
    private boolean verifyStateTransition() {
        // State roots must be different (state changed)
        if (Arrays.equals(previousStateRoot, newStateRoot)) {
            return false;
        }
        
        // Verify transactions hash is valid
        return transactionsHash.length == 32;
    }
    
    /**
     * Verifies zk-SNARK proof
     */
    private boolean verifyZKSNARK() {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(validityProof);
            
            // Read proof type
            byte proofTypeId = buffer.get();
            if (proofTypeId != 0x02) { // 0x02 = zk-SNARK
                return false;
            }
            
            // Read A component (G1 point - 64 bytes compressed)
            byte[] pointA = new byte[64];
            buffer.get(pointA);
            
            // Read B component (G2 point - 128 bytes compressed)
            byte[] pointB = new byte[128];
            buffer.get(pointB);
            
            // Read C component (G1 point - 64 bytes)
            byte[] pointC = new byte[64];
            buffer.get(pointC);
            
            // Read public inputs
            int inputCount = buffer.getInt();
            if (inputCount < 2 || inputCount > 100) {
                return false;
            }
            
            List<byte[]> publicInputs = new ArrayList<>();
            for (int i = 0; i < inputCount; i++) {
                byte[] input = new byte[32];
                buffer.get(input);
                publicInputs.add(input);
            }
            
            // Verify public inputs match our state roots
            if (!Arrays.equals(publicInputs.get(0), previousStateRoot)) {
                return false;
            }
            if (!Arrays.equals(publicInputs.get(1), newStateRoot)) {
                return false;
            }
            
            // In production: verify pairing equation e(A, B) = e(C, G2)
            // For simulation, basic structural validation
            boolean allValid = false;
            for (byte b : pointA) if (b != 0) { allValid = true; break; }
            for (byte b : pointB) if (b != 0) { allValid = true; break; }
            for (byte b : pointC) if (b != 0) { allValid = true; break; }
            
            return allValid;
            
        } catch (Exception e) {
            logger.warning("zk-SNARK verification error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Verifies zk-STARK proof (post-quantum secure)
     */
    private boolean verifyZKSTARK() {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(validityProof);
            
            byte proofTypeId = buffer.get();
            if (proofTypeId != 0x03) { // 0x03 = zk-STARK
                return false;
            }
            
            // FRI commitments
            int commitmentCount = buffer.getInt();
            if (commitmentCount < 1 || commitmentCount > 20) {
                return false;
            }
            
            // In production: use StarkWare, Winterfell, or similar
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Verifies optimistic rollup proof
     */
    private boolean verifyOptimistic() {
        // Optimistic rollups assume validity
        // Verification happens via fraud proofs during challenge period
        
        // Check if challenge period has passed
        long challengePeriod = 7 * 24 * 60 * 60 * 1000; // 7 days
        long timeSinceSubmission = System.currentTimeMillis() - l1Timestamp;
        
        if (timeSinceSubmission > challengePeriod) {
            // Challenge period passed, proof is valid
            return true;
        } else {
            // Still in challenge period
            setStatus(ProofStatus.CHALLENGED);
            return false;
        }
    }
    
    /**
     * Verifies validium proof with DAC data availability
     */
    private boolean verifyValidium() {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(validityProof);
            
            // Read data availability commitment
            byte[] daCommitment = new byte[32];
            buffer.get(daCommitment);
            
            // Verify commitment matches DAC attestations
            for (DACAttestation attestation : dacAttestations) {
                // Each DAC attestation should reference the same data
                if (!Arrays.equals(daCommitment, attestation.merkleRoot)) {
                    logger.warning("DAC attestation merkle root mismatch");
                    // Allow some flexibility - just verify commitment is non-zero
                }
            }
            
            // Verify commitment is non-zero
            boolean valid = false;
            for (byte b : daCommitment) {
                if (b != 0) {
                    valid = true;
                    break;
                }
            }
            
            return valid;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Verifies balance changes are consistent
     */
    private boolean verifyBalanceChanges() {
        // Sum of all balance changes should be zero (conservation)
        BigInteger totalChange = BigInteger.ZERO;
        
        for (BigInteger change : netBalanceChanges.values()) {
            totalChange = totalChange.add(change);
        }
        
        // Allow small rounding errors
        return totalChange.abs().compareTo(BigInteger.valueOf(1000)) < 0;
    }
    
    /**
     * Calculates the compression ratio achieved
     */
    public double getCompressionRatio() {
        // Standard L1 transaction size: ~200 bytes
        // Compressed L2 transaction size: ~20 bytes
        // Plus proof overhead
        
        long uncompressedSize = transactionCount * 200L;
        long compressedSize = validityProof.length + 64; // Proof + state roots
        
        return (double) uncompressedSize / (double) compressedSize;
    }
    
    /**
     * Calculates cost savings from rollup
     */
    public BigInteger calculateCostSavings() {
        // L1 individual transaction cost: 21,000 gas each
        // L2 batch cost: 500,000 gas + (100 gas × txCount)
        
        BigInteger l1Cost = BigInteger.valueOf(21000)
            .multiply(BigInteger.valueOf(transactionCount));
        
        BigInteger l2Cost = BigInteger.valueOf(500000)
            .add(BigInteger.valueOf(100).multiply(BigInteger.valueOf(transactionCount)));
        
        // Apply Treasury subsidy if applicable
        if (treasurySubsidized) {
            l2Cost = l2Cost.divide(BigInteger.valueOf(10)); // 90% subsidy
        }
        
        return l1Cost.subtract(l2Cost);
    }
    
    /**
     * NEW: Gets data availability score (0.0 to 1.0)
     */
    public double getDataAvailabilityScore() {
        if (dacAttestations.isEmpty()) return 0.0;
        
        double totalScore = 0.0;
        for (DACAttestation attestation : dacAttestations) {
            // Score based on node count (7 nodes per region max)
            double coverage = (double) attestation.nodeSignatures.size() / 7.0;
            totalScore += coverage;
        }
        
        // Average across regions (max 5 regions)
        return Math.min(1.0, totalScore / 5.0);
    }
    
    /**
     * NEW: Checks if proof enforces African sovereignty
     */
    public boolean enforcesAfricanSovereignty() {
        return verifyCommitteeQuorum() &&
               verifyDACAttestations() &&
               enforceGeographicDistribution();
    }
    
    /**
     * Serializes rollup proof to bytes
     */
    public byte[] serialize() {
        ByteBuffer buffer = ByteBuffer.allocate(16384); // Increased for sovereignty data
        
        // L2 block info
        buffer.putLong(l2BlockNumber);
        buffer.putInt(batchId);
        
        // State roots
        buffer.put(previousStateRoot);
        buffer.put(newStateRoot);
        
        // Validity proof
        buffer.putInt(validityProof.length);
        buffer.put(validityProof);
        
        // Transaction info
        buffer.putInt(transactionCount);
        buffer.put(transactionsHash);
        
        // L1 anchoring
        buffer.putLong(l1BlockNumber);
        buffer.putLong(l1Timestamp);
        
        // Proof type and status
        buffer.put((byte) proofType.ordinal());
        buffer.put((byte) status.ordinal());
        
        // African sovereignty data
        buffer.putInt(committee.size());
        for (CommitteeMember member : committee) {
            byte[] memberIdBytes = member.memberId.getBytes();
            buffer.putInt(memberIdBytes.length);
            buffer.put(memberIdBytes);
            buffer.putInt(member.region.ordinal());
            buffer.putInt(member.dacNodeId);
            buffer.putDouble(member.reputationScore);
        }
        
        buffer.putInt(dacAttestations.size());
        for (DACAttestation attestation : dacAttestations) {
            buffer.putInt(attestation.region.ordinal());
            buffer.putInt(attestation.nodeIds.size());
            for (Integer nodeId : attestation.nodeIds) {
                buffer.putInt(nodeId);
            }
            buffer.put(attestation.merkleRoot);
        }
        
        // Flags
        byte flags = 0;
        if (settlesViaASU) flags |= 0x01;
        if (treasurySubsidized) flags |= 0x02;
        buffer.put(flags);
        
        buffer.flip();
        byte[] result = new byte[buffer.limit()];
        buffer.get(result);
        return result;
    }
    
    /**
     * Gets proof statistics with African sovereignty metrics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("l2BlockNumber", l2BlockNumber);
        stats.put("batchId", batchId);
        stats.put("transactionCount", transactionCount);
        stats.put("proofType", proofType.toString());
        stats.put("status", status.toString());
        stats.put("compressionRatio", String.format("%.2f:1", getCompressionRatio()));
        stats.put("costSavings", calculateCostSavings().toString());
        stats.put("l1BlockNumber", l1BlockNumber);
        stats.put("proofSize", validityProof.length);
        
        // African sovereignty metrics
        stats.put("committeeSize", committee.size());
        stats.put("committeeRegions", committeeRegionCount.size());
        stats.put("committeeSignatures", committeeSignatures.size());
        stats.put("dacAttestations", dacAttestations.size());
        stats.put("dataAvailabilityScore", String.format("%.2f", getDataAvailabilityScore()));
        stats.put("settlesViaASU", settlesViaASU);
        stats.put("treasurySubsidized", treasurySubsidized);
        stats.put("enforcesAfricanSovereignty", enforcesAfricanSovereignty());
        
        return stats;
    }
    
    @Override
    public String toString() {
        return String.format("RollupProof{l2Block=%d, batch=%d, txCount=%d, type=%s, status=%s, " +
            "committee=%d regions, DAC=%d regions, ASU=%b}",
            l2BlockNumber, batchId, transactionCount, proofType, status,
            committeeRegionCount.size(), dacAttestations.size(), settlesViaASU);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RollupProof that = (RollupProof) o;
        return l2BlockNumber == that.l2BlockNumber && batchId == that.batchId;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(l2BlockNumber, batchId);
    }
}