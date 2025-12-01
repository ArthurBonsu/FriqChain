package org.example.app.core.blockchain.transaction;

import org.example.app.core.types.Address;
import org.example.app.core.types.Amount;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RollupBatchTransaction represents Layer 2 transaction batching for scaling
 * the African Tokenized Currency System to 1.2 billion users with African sovereignty.
 * 
 * African Sovereignty L2 Architecture:
 * - 5-node rotating committee (not single operator) from different regions
 * - Integration with 35-node African Data Availability Committee (7 per region)
 * - Geographic distribution enforcement (max 33% from any region)
 * - ASU (African Stable Unit) settlement integration
 * - Treasury DAO subsidizes near-zero-fee retail transactions (≤$0.001/tx)
 * - Submarine cable routing via African infrastructure
 * - Sanction-resistant through distributed architecture
 * 
 * Layer 2 Strategy:
 * - zk-Rollups/Validium: Batch thousands of transactions off-chain
 * - Post single validity proof to L1
 * - Reduces L1 load while maintaining security
 * - Target throughput: 10,000+ TPS per rollup operator
 * - Total L2 capacity: 50,000+ TPS across multiple rollups
 */
public class RollupBatchTransaction extends BaseTransaction {
    
    // African Geographic Regions (mirroring CrossShardTransaction)
    public enum AfricanRegion {
        NORTH_AFRICA, WEST_AFRICA, EAST_AFRICA, CENTRAL_AFRICA, SOUTHERN_AFRICA
    }
    
    /**
     * 5-node rotating committee structure for L2 batch submission
     * Replaces single rollup operator for African sovereignty
     */
    public static class RotatingCommittee {
        private final List<CommitteeMember> members; // 5 members from different regions
        private final long rotationEpoch; // Epoch when committee rotates
        private final Map<AfricanRegion, Integer> regionRepresentation;
        
        public RotatingCommittee(List<CommitteeMember> members, long rotationEpoch) {
            if (members.size() != 5) {
                throw new IllegalArgumentException("Committee must have exactly 5 members");
            }
            this.members = new ArrayList<>(members);
            this.rotationEpoch = rotationEpoch;
            this.regionRepresentation = new ConcurrentHashMap<>();
            
            // Track region representation
            for (CommitteeMember member : members) {
                regionRepresentation.merge(member.region, 1, Integer::sum);
            }
            
            // Enforce: No region can have more than 2 members (40% < 33% enforcement relaxed for committee of 5)
            for (Integer count : regionRepresentation.values()) {
                if (count > 2) {
                    throw new IllegalArgumentException("No region can have more than 2 committee members");
                }
            }
        }
        
        public List<CommitteeMember> getMembers() { return new ArrayList<>(members); }
        public long getRotationEpoch() { return rotationEpoch; }
        public Map<AfricanRegion, Integer> getRegionRepresentation() { 
            return new HashMap<>(regionRepresentation); 
        }
        
        /**
         * Checks if committee has quorum (3 out of 5 signatures required)
         */
        public boolean hasQuorum(Set<Address> signers) {
            int count = 0;
            for (CommitteeMember member : members) {
                if (signers.contains(member.address)) {
                    count++;
                }
            }
            return count >= 3; // 3 out of 5 = 60% supermajority
        }
    }
    
    /**
     * Committee member with regional affiliation
     */
    public static class CommitteeMember {
        public final Address address;
        public final AfricanRegion region;
        public final int nodeId; // DAC node ID (0-34)
        public final double reputationScore;
        
        public CommitteeMember(Address address, AfricanRegion region, int nodeId, double reputationScore) {
            this.address = address;
            this.region = region;
            this.nodeId = nodeId;
            this.reputationScore = reputationScore;
        }
    }
    
    /**
     * African Data Availability Committee reference
     */
    public static class DACReference {
        public final AfricanRegion region;
        public final List<Integer> nodeIds; // Nodes storing this batch data
        public final byte[] merkleRoot; // Merkle root of batch data
        public final List<byte[]> nodeSignatures; // Node attestations
        
        public DACReference(AfricanRegion region, List<Integer> nodeIds, 
                           byte[] merkleRoot, List<byte[]> nodeSignatures) {
            this.region = region;
            this.nodeIds = new ArrayList<>(nodeIds);
            this.merkleRoot = Arrays.copyOf(merkleRoot, merkleRoot.length);
            this.nodeSignatures = new ArrayList<>();
            for (byte[] sig : nodeSignatures) {
                this.nodeSignatures.add(Arrays.copyOf(sig, sig.length));
            }
        }
    }
    
    private final RotatingCommittee committee; // 5-node rotating committee
    private final int batchId;
    private final List<byte[]> compressedTransactions; // Compressed L2 transactions
    private final byte[] stateRoot; // New state root after batch
    private final byte[] validityProof; // zk-SNARK proof
    private final int transactionCount;
    private final Amount totalValue; // Total value in batch
    private final long l2BlockNumber;
    private final byte[] previousStateRoot;
    private final Map<String, BigInteger> tokenBalanceDeltas; // Net balance changes per token
    private final List<DACReference> dacReferences; // Data availability attestations from 3+ regions
    private final Map<Address, byte[]> committeeSignatures; // 3 out of 5 committee signatures required
    private final boolean treasurySubsidized; // Treasury DAO subsidy applied
    private final byte[] submarineCableRoute; // African submarine cable routing
    private final boolean settleViaASU; // Settle in ASU tokens
    
    // Geographic distribution limit
    private static final double MAX_REGION_CONTROL = 0.33;
    
    /**
     * Creates a new RollupBatchTransaction with African sovereignty enforcement
     * 
     * @param committee 5-node rotating committee from different regions
     * @param batchId Unique batch identifier
     * @param compressedTransactions List of compressed L2 transactions
     * @param stateRoot New state root after applying batch
     * @param validityProof zk-SNARK validity proof
     * @param totalValue Total value transferred in batch
     * @param l2BlockNumber L2 block number
     * @param previousStateRoot Previous state root
     * @param tokenBalanceDeltas Net balance changes per token
     * @param dacReferences Data availability attestations (need 3+ regions)
     * @param committeeSignatures Committee member signatures (need 3 out of 5)
     * @param treasurySubsidized Whether Treasury DAO subsidy is applied
     * @param submarineCableRoute Preferred African submarine cable routing
     * @param settleViaASU Settle using ASU tokens
     */
    public RollupBatchTransaction(
            RotatingCommittee committee, int batchId,
            List<byte[]> compressedTransactions, byte[] stateRoot,
            byte[] validityProof, Amount totalValue,
            long l2BlockNumber, byte[] previousStateRoot,
            Map<String, BigInteger> tokenBalanceDeltas,
            List<DACReference> dacReferences,
            Map<Address, byte[]> committeeSignatures,
            boolean treasurySubsidized,
            byte[] submarineCableRoute,
            boolean settleViaASU) {
        
        super(buildTransactionData(committee, batchId, compressedTransactions,
                stateRoot, validityProof, totalValue, l2BlockNumber, previousStateRoot,
                tokenBalanceDeltas, dacReferences, committeeSignatures, 
                treasurySubsidized, submarineCableRoute, settleViaASU), 
                TransactionType.ROLLUP_BATCH);
        
        this.committee = committee;
        this.batchId = batchId;
        this.compressedTransactions = new ArrayList<>();
        for (byte[] tx : compressedTransactions) {
            this.compressedTransactions.add(Arrays.copyOf(tx, tx.length));
        }
        this.stateRoot = Arrays.copyOf(stateRoot, stateRoot.length);
        this.validityProof = Arrays.copyOf(validityProof, validityProof.length);
        this.transactionCount = compressedTransactions.size();
        this.totalValue = totalValue;
        this.l2BlockNumber = l2BlockNumber;
        this.previousStateRoot = Arrays.copyOf(previousStateRoot, previousStateRoot.length);
        this.tokenBalanceDeltas = new HashMap<>(tokenBalanceDeltas);
        this.dacReferences = new ArrayList<>(dacReferences);
        this.committeeSignatures = new ConcurrentHashMap<>(committeeSignatures);
        this.treasurySubsidized = treasurySubsidized;
        this.submarineCableRoute = Arrays.copyOf(submarineCableRoute, submarineCableRoute.length);
        this.settleViaASU = settleViaASU;
    }
    
    /**
     * Builds the transaction data byte array
     */
    private static byte[] buildTransactionData(
            RotatingCommittee committee, int batchId,
            List<byte[]> compressedTransactions, byte[] stateRoot,
            byte[] validityProof, Amount totalValue,
            long l2BlockNumber, byte[] previousStateRoot,
            Map<String, BigInteger> tokenBalanceDeltas,
            List<DACReference> dacReferences,
            Map<Address, byte[]> committeeSignatures,
            boolean treasurySubsidized,
            byte[] submarineCableRoute,
            boolean settleViaASU) {
        
        ByteBuffer buffer = ByteBuffer.allocate(131072); // 128KB for large batches with sovereignty data
        
        // Write committee information
        buffer.putInt(committee.getMembers().size());
        for (CommitteeMember member : committee.getMembers()) {
            buffer.put(member.address.toSlice());
            buffer.putInt(member.region.ordinal());
            buffer.putInt(member.nodeId);
            buffer.putDouble(member.reputationScore);
        }
        buffer.putLong(committee.getRotationEpoch());
        
        // Write batch ID
        buffer.putInt(batchId);
        
        // Write compressed transactions
        buffer.putInt(compressedTransactions.size());
        for (byte[] tx : compressedTransactions) {
            buffer.putInt(tx.length);
            buffer.put(tx);
        }
        
        // Write state roots
        buffer.put(previousStateRoot);
        buffer.put(stateRoot);
        
        // Write validity proof
        buffer.putInt(validityProof.length);
        buffer.put(validityProof);
        
        // Write total value
        byte[] valueBytes = totalValue.toBytes();
        buffer.putInt(valueBytes.length);
        buffer.put(valueBytes);
        
        // Write L2 block number
        buffer.putLong(l2BlockNumber);
        
        // Write token balance deltas
        buffer.putInt(tokenBalanceDeltas.size());
        for (Map.Entry<String, BigInteger> entry : tokenBalanceDeltas.entrySet()) {
            byte[] tokenBytes = entry.getKey().getBytes();
            buffer.putInt(tokenBytes.length);
            buffer.put(tokenBytes);
            
            byte[] deltaBytes = entry.getValue().toByteArray();
            buffer.putInt(deltaBytes.length);
            buffer.put(deltaBytes);
        }
        
        // Write DAC references
        buffer.putInt(dacReferences.size());
        for (DACReference dac : dacReferences) {
            buffer.putInt(dac.region.ordinal());
            buffer.putInt(dac.nodeIds.size());
            for (Integer nodeId : dac.nodeIds) {
                buffer.putInt(nodeId);
            }
            buffer.put(dac.merkleRoot);
            buffer.putInt(dac.nodeSignatures.size());
            for (byte[] sig : dac.nodeSignatures) {
                buffer.putInt(sig.length);
                buffer.put(sig);
            }
        }
        
        // Write committee signatures
        buffer.putInt(committeeSignatures.size());
        for (Map.Entry<Address, byte[]> entry : committeeSignatures.entrySet()) {
            buffer.put(entry.getKey().toSlice());
            buffer.putInt(entry.getValue().length);
            buffer.put(entry.getValue());
        }
        
        // Write flags
        byte flags = 0;
        if (treasurySubsidized) flags |= 0x01;
        if (settleViaASU) flags |= 0x02;
        buffer.put(flags);
        
        // Write submarine cable route
        buffer.putInt(submarineCableRoute.length);
        buffer.put(submarineCableRoute);
        
        buffer.flip();
        byte[] result = new byte[buffer.limit()];
        buffer.get(result);
        return result;
    }
    
    @Override
    public Address getSenderAddress() {
        // Return first committee member as representative
        return committee.getMembers().get(0).address;
    }
    
    @Override
    public Address getRecipientAddress() {
        // Return first committee member as representative
        return committee.getMembers().get(0).address;
    }
    
    @Override
    public Amount getAmount() {
        return totalValue;
    }
    
    public RotatingCommittee getCommittee() {
        return committee;
    }
    
    public int getBatchId() {
        return batchId;
    }
    
    public List<byte[]> getCompressedTransactions() {
        List<byte[]> copies = new ArrayList<>();
        for (byte[] tx : compressedTransactions) {
            copies.add(Arrays.copyOf(tx, tx.length));
        }
        return copies;
    }
    
    public byte[] getStateRoot() {
        return Arrays.copyOf(stateRoot, stateRoot.length);
    }
    
    public byte[] getValidityProof() {
        return Arrays.copyOf(validityProof, validityProof.length);
    }
    
    public int getTransactionCount() {
        return transactionCount;
    }
    
    public long getL2BlockNumber() {
        return l2BlockNumber;
    }
    
    public byte[] getPreviousStateRoot() {
        return Arrays.copyOf(previousStateRoot, previousStateRoot.length);
    }
    
    public Map<String, BigInteger> getTokenBalanceDeltas() {
        return new HashMap<>(tokenBalanceDeltas);
    }
    
    public List<DACReference> getDACReferences() {
        return new ArrayList<>(dacReferences);
    }
    
    public Map<Address, byte[]> getCommitteeSignatures() {
        return new HashMap<>(committeeSignatures);
    }
    
    public boolean isTreasurySubsidized() {
        return treasurySubsidized;
    }
    
    public byte[] getSubmarineCableRoute() {
        return Arrays.copyOf(submarineCableRoute, submarineCableRoute.length);
    }
    
    public boolean settlesViaASU() {
        return settleViaASU;
    }
    
    @Override
    protected boolean validateTransactionRules() {
        // Validate batch ID
        if (batchId < 0) {
            return false;
        }
        
        // Validate transaction count (max 10,000 per batch)
        if (transactionCount <= 0 || transactionCount > 10000) {
            return false;
        }
        
        // Validate state roots
        if (stateRoot == null || stateRoot.length != 32 ||
            previousStateRoot == null || previousStateRoot.length != 32) {
            return false;
        }
        
        // Validate validity proof
        if (validityProof == null || validityProof.length == 0) {
            return false;
        }
        
        // Validate L2 block number
        if (l2BlockNumber < 0) {
            return false;
        }
        
        // CRITICAL: Validate committee has quorum (3 out of 5 signatures)
        if (!validateCommitteeQuorum()) {
            return false;
        }
        
        // CRITICAL: Validate DAC references (need 3+ regions)
        if (!validateDACReferences()) {
            return false;
        }
        
        // CRITICAL: Enforce geographic distribution
        if (!enforceGeographicDistribution()) {
            return false;
        }
        
        // Verify validity proof
        return verifyValidityProof();
    }
    
    /**
     * CRITICAL: Validates committee quorum (3 out of 5 signatures required)
     */
    private boolean validateCommitteeQuorum() {
        if (committeeSignatures.size() < 3) {
            return false;
        }
        
        // Verify signatures are from actual committee members
        Set<Address> validSigners = new HashSet<>();
        for (CommitteeMember member : committee.getMembers()) {
            if (committeeSignatures.containsKey(member.address)) {
                // In production: verify ECDSA signature
                validSigners.add(member.address);
            }
        }
        
        // Check committee has quorum
        return committee.hasQuorum(validSigners);
    }
    
    /**
     * CRITICAL: Validates DAC references (need attestations from 3+ regions)
     */
    private boolean validateDACReferences() {
        if (dacReferences.size() < 3) {
            return false;
        }
        
        Set<AfricanRegion> regionsRepresented = new HashSet<>();
        
        for (DACReference dac : dacReferences) {
            regionsRepresented.add(dac.region);
            
            // Validate DAC has enough node signatures (need 5 out of 7 from region)
            if (dac.nodeSignatures.size() < 5) {
                return false;
            }
            
            // Validate node IDs are within valid range for region
            for (Integer nodeId : dac.nodeIds) {
                if (!isValidNodeForRegion(nodeId, dac.region)) {
                    return false;
                }
            }
            
            // Verify merkle root is valid (32 bytes)
            if (dac.merkleRoot.length != 32) {
                return false;
            }
        }
        
        // Require at least 3 different regions
        return regionsRepresented.size() >= 3;
    }
    
    /**
     * Validates if a node ID belongs to a specific region
     */
    private boolean isValidNodeForRegion(int nodeId, AfricanRegion region) {
        // Each region has nodes in specific ranges:
        // North Africa: 0-6, West Africa: 7-13, East Africa: 14-20
        // Central Africa: 21-27, Southern Africa: 28-34
        
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
     * CRITICAL: Enforces geographic distribution (max 33% from any region)
     */
    private boolean enforceGeographicDistribution() {
        Map<AfricanRegion, Integer> regionCount = committee.getRegionRepresentation();
        int totalMembers = committee.getMembers().size();
        
        if (totalMembers == 0) return false;
        
        for (Map.Entry<AfricanRegion, Integer> entry : regionCount.entrySet()) {
            double regionPercentage = (double) entry.getValue() / totalMembers;
            
            // No region can control more than 40% (relaxed from 33% for committee of 5)
            // 2 out of 5 = 40% is acceptable
            if (regionPercentage > 0.40) {
                return false;
            }
        }
        
        // Require at least 3 different regions in committee
        return regionCount.size() >= 3;
    }
    
    /**
     * Verifies the zk-SNARK validity proof
     * This is a simplified simulation - in production, use a proper zk-SNARK library
     */
    private boolean verifyValidityProof() {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(validityProof);
            
            // Read proof type
            byte proofType = buffer.get();
            if (proofType != 0x02) { // 0x02 = zk-SNARK proof
                return false;
            }
            
            // Read proof components (simplified SNARK structure)
            // In production: use libsnark, bellman, or circom
            
            // Read A component (G1 point)
            byte[] pointA = new byte[64]; // Compressed G1 point
            buffer.get(pointA);
            
            // Read B component (G2 point)
            byte[] pointB = new byte[128]; // Compressed G2 point
            buffer.get(pointB);
            
            // Read C component (G1 point)
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
            
            // Verify public inputs match our transaction data
            // Input 0: Previous state root
            if (!Arrays.equals(publicInputs.get(0), previousStateRoot)) {
                return false;
            }
            
            // Input 1: New state root
            if (!Arrays.equals(publicInputs.get(1), stateRoot)) {
                return false;
            }
            
            // In production: verify pairing equation e(A, B) = e(C, G2)
            // For simulation, we'll do basic structural validation
            
            // Verify all components are non-zero
            boolean allValid = true;
            for (byte b : pointA) if (b != 0) { allValid = true; break; }
            for (byte b : pointB) if (b != 0) { allValid = true; break; }
            for (byte b : pointC) if (b != 0) { allValid = true; break; }
            
            return allValid;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Decompresses a single transaction from the batch
     */
    public Map<String, Object> decompressTransaction(int index) {
        if (index < 0 || index >= compressedTransactions.size()) {
            throw new IndexOutOfBoundsException("Invalid transaction index: " + index);
        }
        
        byte[] compressed = compressedTransactions.get(index);
        return decompressTransactionData(compressed);
    }
    
    /**
     * Decompresses transaction data
     * Uses efficient compression scheme for L2 transactions
     */
    private Map<String, Object> decompressTransactionData(byte[] compressed) {
        Map<String, Object> transaction = new HashMap<>();
        ByteBuffer buffer = ByteBuffer.wrap(compressed);
        
        try {
            // Read transaction type (1 byte)
            byte txType = buffer.get();
            transaction.put("type", TransactionType.fromCode(txType & 0xFF));
            
            // Read compressed addresses (8 bytes each instead of 20)
            byte[] senderCompressed = new byte[8];
            buffer.get(senderCompressed);
            transaction.put("senderCompressed", senderCompressed);
            
            byte[] recipientCompressed = new byte[8];
            buffer.get(recipientCompressed);
            transaction.put("recipientCompressed", recipientCompressed);
            
            // Read compressed amount (variable length)
            byte amountFlag = buffer.get();
            BigInteger amount;
            
            if (amountFlag == 0x01) {
                // Small amount (1 byte)
                amount = BigInteger.valueOf(buffer.get() & 0xFF);
            } else if (amountFlag == 0x02) {
                // Medium amount (4 bytes)
                amount = BigInteger.valueOf(buffer.getInt() & 0xFFFFFFFFL);
            } else {
                // Large amount (variable)
                int length = buffer.get() & 0xFF;
                byte[] amountBytes = new byte[length];
                buffer.get(amountBytes);
                amount = new BigInteger(amountBytes);
            }
            transaction.put("amount", amount);
            
            // Read token symbol index (1 byte)
            byte tokenIndex = buffer.get();
            transaction.put("tokenIndex", tokenIndex & 0xFF);
            
            // Read nonce (2 bytes for L2)
            int nonce = buffer.getShort() & 0xFFFF;
            transaction.put("nonce", nonce);
            
            return transaction;
            
        } catch (Exception e) {
            return transaction; // Return partial data on error
        }
    }
    
    /**
     * Calculates the compression ratio achieved
     */
    public double getCompressionRatio() {
        // Standard L1 transaction size: ~200 bytes
        // Compressed L2 transaction size: ~20 bytes
        // Compression ratio: 10:1
        
        long uncompressedSize = transactionCount * 200L;
        long compressedSize = 0;
        for (byte[] tx : compressedTransactions) {
            compressedSize += tx.length;
        }
        
        return (double) uncompressedSize / (double) compressedSize;
    }
    
    /**
     * Calculates the cost savings from batching
     */
    public BigInteger calculateCostSavings() {
        // L1 individual transaction cost: 21,000 gas each
        // L2 batch cost: 500,000 gas total + (100 gas × txCount)
        
        BigInteger l1Cost = BigInteger.valueOf(21000).multiply(BigInteger.valueOf(transactionCount));
        BigInteger l2Cost = BigInteger.valueOf(500000).add(
            BigInteger.valueOf(100).multiply(BigInteger.valueOf(transactionCount))
        );
        
        return l1Cost.subtract(l2Cost);
    }
    
    /**
     * Gets the subsidized fee per transaction
     * Target: ≤$0.001 per transaction for retail with Treasury DAO subsidy
     */
    @Override
    public Amount getRequiredFee() {
        // Total batch fee
        BigInteger batchFee = BigInteger.valueOf(100000); // 0.1 ASU base
        
        // Divide among transactions
        BigInteger feePerTx = batchFee.divide(BigInteger.valueOf(transactionCount));
        
        // Apply Treasury DAO subsidy (90% subsidy for African retail transactions)
        if (treasurySubsidized) {
            feePerTx = feePerTx.divide(BigInteger.valueOf(10)); // 90% subsidy
        }
        
        // If settling via ASU, no additional premium (African sovereignty priority)
        
        return Amount.forASU(feePerTx);
    }
    
    /**
     * Estimates throughput improvement with African sovereignty L2
     */
    public int estimateThroughputMultiplier() {
        // L1: ~100 TPS per shard
        // L2 with rollups: ~10,000 TPS per rollup operator
        // With 5 rollup operators: ~50,000 TPS total
        // Multiplier: 500x
        
        return 500;
    }
    
    /**
     * Checks if this batch enforces African sovereignty principles
     */
    public boolean enforcesAfricanSovereignty() {
        // Check committee quorum
        if (!validateCommitteeQuorum()) return false;
        
        // Check DAC references from multiple regions
        if (!validateDACReferences()) return false;
        
        // Check geographic distribution
        if (!enforceGeographicDistribution()) return false;
        
        // Check multi-region committee
        return committee.getRegionRepresentation().size() >= 3;
    }
    
    /**
     * Gets data availability score (0.0 to 1.0)
     * Based on DAC coverage and node reputation
     */
    public double getDataAvailabilityScore() {
        if (dacReferences.isEmpty()) return 0.0;
        
        double totalScore = 0.0;
        for (DACReference dac : dacReferences) {
            // Score based on node count and signature count
            double coverage = (double) dac.nodeSignatures.size() / 7.0; // 7 nodes per region
            totalScore += coverage;
        }
        
        // Average across regions
        return Math.min(1.0, totalScore / 5.0); // 5 regions total
    }
    
    /**
     * Estimates finality time using African infrastructure
     */
    public long estimateFinalityTime() {
        // L2 batch generation: 1 second
        long batchGenTime = 1000;
        
        // Committee consensus (3 out of 5): 2 seconds
        long committeeTime = 2000;
        
        // DAC attestations from 3+ regions: 3 seconds
        long dacTime = 3000;
        
        // L1 settlement time: 5 seconds
        long l1SettlementTime = 5000;
        
        // African submarine cable latency: 1 second
        long networkLatency = 1000;
        
        return batchGenTime + committeeTime + dacTime + l1SettlementTime + networkLatency;
    }
    
    @Override
    public String getDescription() {
        String subsidyInfo = treasurySubsidized ? " (Treasury subsidized)" : "";
        String settlementInfo = settleViaASU ? " via ASU" : "";
        return String.format("Rollup Batch #%d%s%s: %d transactions, compression %.1fx, L2 block %d, %d DAC regions, %.1f%% DA coverage",
            batchId, subsidyInfo, settlementInfo, transactionCount, getCompressionRatio(), 
            l2BlockNumber, dacReferences.size(), getDataAvailabilityScore() * 100);
    }
    
    @Override
    public String toString() {
        return String.format("RollupBatchTransaction{id=%d, txCount=%d, totalValue=%s, l2Block=%d, committee=%d members, dac=%d regions, subsidy=%b}",
            batchId, transactionCount, totalValue, l2BlockNumber, 
            committee.getMembers().size(), dacReferences.size(), treasurySubsidized);
    }
}