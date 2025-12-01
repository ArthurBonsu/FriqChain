package org.example.app.core.blockchain.transaction;

import org.example.app.core.types.Address;
import org.example.app.core.types.Amount;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CrossShardTransaction enables transactions across blockchain shards with African sovereignty enforcement,
 * critical for scaling the African Tokenized Currency System to 1.2 billion users.
 * 
 * African Sovereignty Architecture:
 * - 20 Geographic Shards across 5 regions (North, West, East, Central, Southern Africa)
 * - 5 Regional Data Availability Committees (7 nodes each = 35 total)
 * - Local consensus: ~5,000 TPS per shard
 * - Cross-Shard Bridge: Relay chain with multi-region validation
 * - Total capacity: 20 shards × 5,000 TPS = 100,000 TPS continent-wide
 * - Geographic distribution limit: Max 33% control from any single region
 * - ASU (African Stable Unit) settlement layer for inter-country transactions
 * 
 * CRITICAL: No single region can control more than 33% of validators
 */
public class CrossShardTransaction extends BaseTransaction {
    
    // African Geographic Regions
    public enum AfricanRegion {
        NORTH_AFRICA,    // Morocco, Egypt, Algeria, Tunisia, Libya
        WEST_AFRICA,     // Nigeria, Ghana, Senegal, Ivory Coast, Mali
        EAST_AFRICA,     // Kenya, Ethiopia, Tanzania, Uganda, Rwanda
        CENTRAL_AFRICA,  // DRC, Cameroon, Gabon, Chad, CAR
        SOUTHERN_AFRICA  // South Africa, Zimbabwe, Botswana, Namibia, Angola
    }
    
    // Regional Data Availability Committee structure
    public static class RegionalDAC {
        private final AfricanRegion region;
        private final List<Integer> nodeIds; // 7 nodes per region
        private final Map<Integer, Double> nodeReputations;
        
        public RegionalDAC(AfricanRegion region, List<Integer> nodeIds) {
            this.region = region;
            this.nodeIds = new ArrayList<>(nodeIds);
            this.nodeReputations = new ConcurrentHashMap<>();
            // Initialize reputation scores
            for (Integer nodeId : nodeIds) {
                nodeReputations.put(nodeId, 1.0); // Perfect score initially
            }
        }
        
        public AfricanRegion getRegion() { return region; }
        public List<Integer> getNodeIds() { return new ArrayList<>(nodeIds); }
        public double getNodeReputation(int nodeId) { 
            return nodeReputations.getOrDefault(nodeId, 0.0); 
        }
        
        public void updateReputation(int nodeId, double newReputation) {
            nodeReputations.put(nodeId, Math.max(0.0, Math.min(1.0, newReputation)));
        }
    }
    
    private final Address senderAddress;
    private final Address recipientAddress;
    private final int sourceShard;
    private final int destinationShard;
    private final Amount amount;
    private final byte[] crossShardProof; // Merkle proof for cross-shard verification
    private final List<byte[]> relaySignatures; // Relay chain validator signatures
    private final Map<AfricanRegion, Integer> regionValidatorCount; // Geographic distribution tracking
    private final String messageHash; // Hash of cross-shard message
    private final long sourceBlockHeight; // Source shard block height
    private final long nonce; // Prevents replay attacks
    private final boolean requiresAtomicExecution; // For atomic cross-shard swaps
    private final boolean settleViaASU; // Use ASU settlement layer
    private final List<Integer> dacNodeConfirmations; // DAC node confirmations (need 5+ from different regions)
    private final byte[] submarineCableRoute; // Preferred African submarine cable routing
    
    // 5 Regional Data Availability Committees (7 nodes each)
    private static final Map<AfricanRegion, RegionalDAC> REGIONAL_DACS = initializeRegionalDACs();
    
    // Geographic distribution limit: Max 33% from any region
    private static final double MAX_REGION_CONTROL = 0.33;
    
    // Shard to region mapping (20 shards across 5 regions)
    private static final Map<Integer, AfricanRegion> SHARD_REGIONS = initializeShardRegions();
    
    /**
     * Creates a new CrossShardTransaction with African sovereignty enforcement
     * 
     * @param senderAddress Sender's address
     * @param recipientAddress Recipient's address
     * @param sourceShard Source shard ID (0-19)
     * @param destinationShard Destination shard ID (0-19)
     * @param amount Amount to transfer
     * @param crossShardProof Merkle proof for verification
     * @param relaySignatures Relay chain validator signatures (multi-region required)
     * @param sourceBlockHeight Block height in source shard
     * @param nonce Transaction nonce
     * @param requiresAtomicExecution Whether this needs atomic execution
     * @param settleViaASU Use ASU settlement layer for inter-country transactions
     * @param dacNodeConfirmations DAC node confirmations from multiple regions
     * @param submarineCableRoute Preferred African submarine cable routing
     */
    public CrossShardTransaction(
            Address senderAddress, Address recipientAddress,
            int sourceShard, int destinationShard,
            Amount amount, byte[] crossShardProof,
            List<byte[]> relaySignatures, long sourceBlockHeight,
            long nonce, boolean requiresAtomicExecution,
            boolean settleViaASU, List<Integer> dacNodeConfirmations,
            byte[] submarineCableRoute) {
        
        super(buildTransactionData(senderAddress, recipientAddress, sourceShard,
                destinationShard, amount, crossShardProof, relaySignatures,
                sourceBlockHeight, nonce, requiresAtomicExecution, settleViaASU,
                dacNodeConfirmations, submarineCableRoute),
                TransactionType.CROSS_SHARD);
        
        this.senderAddress = senderAddress;
        this.recipientAddress = recipientAddress;
        this.sourceShard = sourceShard;
        this.destinationShard = destinationShard;
        this.amount = amount;
        this.crossShardProof = Arrays.copyOf(crossShardProof, crossShardProof.length);
        this.relaySignatures = new ArrayList<>();
        for (byte[] sig : relaySignatures) {
            this.relaySignatures.add(Arrays.copyOf(sig, sig.length));
        }
        this.regionValidatorCount = new ConcurrentHashMap<>();
        this.messageHash = generateMessageHash();
        this.sourceBlockHeight = sourceBlockHeight;
        this.nonce = nonce;
        this.requiresAtomicExecution = requiresAtomicExecution;
        this.settleViaASU = settleViaASU;
        this.dacNodeConfirmations = new ArrayList<>(dacNodeConfirmations);
        this.submarineCableRoute = Arrays.copyOf(submarineCableRoute, submarineCableRoute.length);
        
        // Track validator geographic distribution
        trackValidatorDistribution();
    }
    
    /**
     * Initializes the 5 Regional Data Availability Committees
     * 35 nodes total (7 per region)
     */
    private static Map<AfricanRegion, RegionalDAC> initializeRegionalDACs() {
        Map<AfricanRegion, RegionalDAC> dacs = new EnumMap<>(AfricanRegion.class);
        
        // North Africa DAC: 7 nodes
        dacs.put(AfricanRegion.NORTH_AFRICA, new RegionalDAC(
            AfricanRegion.NORTH_AFRICA,
            Arrays.asList(0, 1, 2, 3, 4, 5, 6) // Morocco, Egypt, Algeria, Tunisia, Libya, Sudan, Mauritania
        ));
        
        // West Africa DAC: 7 nodes
        dacs.put(AfricanRegion.WEST_AFRICA, new RegionalDAC(
            AfricanRegion.WEST_AFRICA,
            Arrays.asList(7, 8, 9, 10, 11, 12, 13) // Nigeria, Ghana, Senegal, Ivory Coast, Mali, Burkina Faso, Guinea
        ));
        
        // East Africa DAC: 7 nodes
        dacs.put(AfricanRegion.EAST_AFRICA, new RegionalDAC(
            AfricanRegion.EAST_AFRICA,
            Arrays.asList(14, 15, 16, 17, 18, 19, 20) // Kenya, Ethiopia, Tanzania, Uganda, Rwanda, Burundi, Djibouti
        ));
        
        // Central Africa DAC: 7 nodes
        dacs.put(AfricanRegion.CENTRAL_AFRICA, new RegionalDAC(
            AfricanRegion.CENTRAL_AFRICA,
            Arrays.asList(21, 22, 23, 24, 25, 26, 27) // DRC, Cameroon, Gabon, Chad, CAR, Congo, Equatorial Guinea
        ));
        
        // Southern Africa DAC: 7 nodes
        dacs.put(AfricanRegion.SOUTHERN_AFRICA, new RegionalDAC(
            AfricanRegion.SOUTHERN_AFRICA,
            Arrays.asList(28, 29, 30, 31, 32, 33, 34) // South Africa, Zimbabwe, Botswana, Namibia, Angola, Zambia, Mozambique
        ));
        
        return Collections.unmodifiableMap(dacs);
    }
    
    /**
     * Initializes shard to region mapping
     * 20 geographic shards distributed across 5 regions (4 shards per region)
     */
    private static Map<Integer, AfricanRegion> initializeShardRegions() {
        Map<Integer, AfricanRegion> mapping = new HashMap<>();
        
        // North Africa: Shards 0-3
        for (int i = 0; i < 4; i++) {
            mapping.put(i, AfricanRegion.NORTH_AFRICA);
        }
        
        // West Africa: Shards 4-7
        for (int i = 4; i < 8; i++) {
            mapping.put(i, AfricanRegion.WEST_AFRICA);
        }
        
        // East Africa: Shards 8-11
        for (int i = 8; i < 12; i++) {
            mapping.put(i, AfricanRegion.EAST_AFRICA);
        }
        
        // Central Africa: Shards 12-15
        for (int i = 12; i < 16; i++) {
            mapping.put(i, AfricanRegion.CENTRAL_AFRICA);
        }
        
        // Southern Africa: Shards 16-19
        for (int i = 16; i < 20; i++) {
            mapping.put(i, AfricanRegion.SOUTHERN_AFRICA);
        }
        
        return Collections.unmodifiableMap(mapping);
    }
    
    /**
     * Builds the transaction data byte array
     */
    private static byte[] buildTransactionData(
            Address senderAddress, Address recipientAddress,
            int sourceShard, int destinationShard,
            Amount amount, byte[] crossShardProof,
            List<byte[]> relaySignatures, long sourceBlockHeight,
            long nonce, boolean requiresAtomicExecution,
            boolean settleViaASU, List<Integer> dacNodeConfirmations,
            byte[] submarineCableRoute) {
        
        ByteBuffer buffer = ByteBuffer.allocate(16384); // Increased for sovereignty data
        
        // Write addresses
        buffer.put(senderAddress.toSlice());
        buffer.put(recipientAddress.toSlice());
        
        // Write shard IDs
        buffer.putInt(sourceShard);
        buffer.putInt(destinationShard);
        
        // Write amount
        byte[] amountBytes = amount.toBytes();
        buffer.putInt(amountBytes.length);
        buffer.put(amountBytes);
        
        // Write cross-shard proof
        buffer.putInt(crossShardProof.length);
        buffer.put(crossShardProof);
        
        // Write relay signatures
        buffer.putInt(relaySignatures.size());
        for (byte[] signature : relaySignatures) {
            buffer.putInt(signature.length);
            buffer.put(signature);
        }
        
        // Write source block height
        buffer.putLong(sourceBlockHeight);
        
        // Write nonce
        buffer.putLong(nonce);
        
        // Write flags
        byte flags = 0;
        if (requiresAtomicExecution) flags |= 0x01;
        if (settleViaASU) flags |= 0x02;
        buffer.put(flags);
        
        // Write DAC node confirmations
        buffer.putInt(dacNodeConfirmations.size());
        for (Integer nodeId : dacNodeConfirmations) {
            buffer.putInt(nodeId);
        }
        
        // Write submarine cable route
        buffer.putInt(submarineCableRoute.length);
        buffer.put(submarineCableRoute);
        
        buffer.flip();
        byte[] result = new byte[buffer.limit()];
        buffer.get(result);
        return result;
    }
    
    /**
     * Tracks validator geographic distribution to enforce 33% limit
     */
    private void trackValidatorDistribution() {
        regionValidatorCount.clear();
        
        for (byte[] signature : relaySignatures) {
            try {
                ByteBuffer buffer = ByteBuffer.wrap(signature);
                int validatorId = buffer.getInt();
                
                // Determine validator's region
                AfricanRegion region = getValidatorRegion(validatorId);
                regionValidatorCount.merge(region, 1, Integer::sum);
                
            } catch (Exception e) {
                // Skip invalid signatures
            }
        }
    }
    
    /**
     * Gets the region for a validator based on their ID
     */
    private AfricanRegion getValidatorRegion(int validatorId) {
        // Validators 0-6: North Africa
        if (validatorId >= 0 && validatorId <= 6) return AfricanRegion.NORTH_AFRICA;
        // Validators 7-13: West Africa
        if (validatorId >= 7 && validatorId <= 13) return AfricanRegion.WEST_AFRICA;
        // Validators 14-20: East Africa
        if (validatorId >= 14 && validatorId <= 20) return AfricanRegion.EAST_AFRICA;
        // Validators 21-27: Central Africa
        if (validatorId >= 21 && validatorId <= 27) return AfricanRegion.CENTRAL_AFRICA;
        // Validators 28-34: Southern Africa
        return AfricanRegion.SOUTHERN_AFRICA;
    }
    
    @Override
    public Address getSenderAddress() {
        return senderAddress;
    }
    
    @Override
    public Address getRecipientAddress() {
        return recipientAddress;
    }
    
    @Override
    public Amount getAmount() {
        return amount;
    }
    
    public int getSourceShard() {
        return sourceShard;
    }
    
    public int getDestinationShard() {
        return destinationShard;
    }
    
    public byte[] getCrossShardProof() {
        return Arrays.copyOf(crossShardProof, crossShardProof.length);
    }
    
    public List<byte[]> getRelaySignatures() {
        List<byte[]> copies = new ArrayList<>();
        for (byte[] sig : relaySignatures) {
            copies.add(Arrays.copyOf(sig, sig.length));
        }
        return copies;
    }
    
    public String getMessageHash() {
        return messageHash;
    }
    
    public long getSourceBlockHeight() {
        return sourceBlockHeight;
    }
    
    public long getNonce() {
        return nonce;
    }
    
    public boolean requiresAtomicExecution() {
        return requiresAtomicExecution;
    }
    
    public boolean settlesViaASU() {
        return settleViaASU;
    }
    
    public List<Integer> getDACNodeConfirmations() {
        return new ArrayList<>(dacNodeConfirmations);
    }
    
    public byte[] getSubmarineCableRoute() {
        return Arrays.copyOf(submarineCableRoute, submarineCableRoute.length);
    }
    
    public Map<AfricanRegion, Integer> getRegionValidatorCount() {
        return new HashMap<>(regionValidatorCount);
    }
    
    @Override
    public boolean isCrossBorder() {
        // Cross-shard is cross-border if shards are in different regions
        AfricanRegion sourceRegion = SHARD_REGIONS.get(sourceShard);
        AfricanRegion destRegion = SHARD_REGIONS.get(destinationShard);
        return sourceRegion != null && destRegion != null && !sourceRegion.equals(destRegion);
    }
    
    /**
     * Gets the geographic region for a shard
     */
    public AfricanRegion getShardRegion(int shardId) {
        return SHARD_REGIONS.getOrDefault(shardId, AfricanRegion.WEST_AFRICA);
    }
    
    /**
     * Generates a hash of the cross-shard message
     */
    private String generateMessageHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            ByteBuffer buffer = ByteBuffer.allocate(512);
            buffer.put(senderAddress.toSlice());
            buffer.put(recipientAddress.toSlice());
            buffer.putInt(sourceShard);
            buffer.putInt(destinationShard);
            buffer.put(amount.toBytes());
            buffer.putLong(sourceBlockHeight);
            buffer.putLong(nonce);
            buffer.put((byte) (settleViaASU ? 1 : 0));
            
            buffer.flip();
            byte[] data = new byte[buffer.limit()];
            buffer.get(data);
            
            byte[] hash = digest.digest(data);
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            
            return hexString.toString();
            
        } catch (Exception e) {
            return "";
        }
    }
    
    @Override
    protected boolean validateTransactionRules() {
        // Validate shard IDs (0-19 for geographic shards)
        if (sourceShard < 0 || sourceShard >= 20 || 
            destinationShard < 0 || destinationShard >= 20) {
            return false;
        }
        
        // Cannot transfer to same shard
        if (sourceShard == destinationShard) {
            return false;
        }
        
        // Validate cross-shard proof
        if (crossShardProof == null || crossShardProof.length == 0) {
            return false;
        }
        
        // Validate relay signatures (supermajority required)
        int requiredSignatures = getRequiredRelaySignatures();
        if (relaySignatures == null || relaySignatures.size() < requiredSignatures) {
            return false;
        }
        
        // CRITICAL: Enforce geographic distribution limit (max 33% from any region)
        if (!enforceGeographicDistribution()) {
            return false;
        }
        
        // Validate DAC node confirmations (need 5+ from different regions)
        if (!validateDACConfirmations()) {
            return false;
        }
        
        // Validate source block height
        if (sourceBlockHeight < 0) {
            return false;
        }
        
        // Verify cross-shard proof
        if (!verifyCrossShardProof()) {
            return false;
        }
        
        // Verify relay signatures with multi-region requirement
        return verifyRelaySignatures();
    }
    
    /**
     * CRITICAL: Enforces geographic distribution limit (max 33% from any region)
     * Prevents any single region from controlling the network
     */
    private boolean enforceGeographicDistribution() {
        int totalValidators = relaySignatures.size();
        if (totalValidators == 0) return false;
        
        for (Map.Entry<AfricanRegion, Integer> entry : regionValidatorCount.entrySet()) {
            double regionPercentage = (double) entry.getValue() / totalValidators;
            
            // No region can control more than 33%
            if (regionPercentage > MAX_REGION_CONTROL) {
                return false;
            }
        }
        
        // Require at least 3 different regions represented
        return regionValidatorCount.size() >= 3;
    }
    
    /**
     * Validates DAC node confirmations (need 5+ from different regions)
     */
    private boolean validateDACConfirmations() {
        if (dacNodeConfirmations.size() < 5) {
            return false;
        }
        
        Set<AfricanRegion> regionsRepresented = new HashSet<>();
        
        for (Integer nodeId : dacNodeConfirmations) {
            AfricanRegion region = getValidatorRegion(nodeId);
            regionsRepresented.add(region);
            
            // Verify node is part of a Regional DAC
            RegionalDAC dac = REGIONAL_DACS.get(region);
            if (dac == null || !dac.getNodeIds().contains(nodeId)) {
                return false;
            }
            
            // Verify node has good reputation (>0.5)
            if (dac.getNodeReputation(nodeId) < 0.5) {
                return false;
            }
        }
        
        // Require at least 3 different regions
        return regionsRepresented.size() >= 3;
    }
    
    /**
     * Gets the required number of relay signatures based on transaction type
     * Uses supermajority for critical operations
     */
    private int getRequiredRelaySignatures() {
        // For critical cross-shard transactions, require supermajority (2/3)
        if (requiresAtomicExecution || 
            amount.getValue().compareTo(BigInteger.valueOf(1000000)) > 0 ||
            settleViaASU) {
            return 7; // Supermajority: 7 out of 10 validators
        } else {
            return 5; // Standard majority: 5 out of 10 validators
        }
    }
    
    /**
     * Verifies the cross-shard Merkle proof
     * Ensures transaction was finalized in source shard
     */
    private boolean verifyCrossShardProof() {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(crossShardProof);
            
            // Read proof type
            byte proofType = buffer.get();
            if (proofType != 0x01) { // 0x01 = Merkle proof
                return false;
            }
            
            // Read Merkle root from source shard
            byte[] sourceRoot = new byte[32];
            buffer.get(sourceRoot);
            
            // Read Merkle path length
            int pathLength = buffer.getInt();
            if (pathLength < 1 || pathLength > 20) {
                return false; // Invalid path length
            }
            
            // Read Merkle path
            List<byte[]> merklePath = new ArrayList<>();
            for (int i = 0; i < pathLength; i++) {
                byte[] pathNode = new byte[32];
                buffer.get(pathNode);
                merklePath.add(pathNode);
            }
            
            // Read transaction index in block
            int txIndex = buffer.getInt();
            
            // Compute Merkle root from path
            byte[] computedRoot = computeMerkleRoot(messageHash.getBytes(), merklePath, txIndex);
            
            // Verify computed root matches source root
            return Arrays.equals(sourceRoot, computedRoot);
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Computes Merkle root from path
     */
    private byte[] computeMerkleRoot(byte[] leaf, List<byte[]> path, int index) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            byte[] current = digest.digest(leaf);
            
            for (byte[] sibling : path) {
                // Determine order based on index
                byte[] combined;
                if (index % 2 == 0) {
                    combined = new byte[64];
                    System.arraycopy(current, 0, combined, 0, 32);
                    System.arraycopy(sibling, 0, combined, 32, 32);
                } else {
                    combined = new byte[64];
                    System.arraycopy(sibling, 0, combined, 0, 32);
                    System.arraycopy(current, 0, combined, 32, 32);
                }
                
                current = digest.digest(combined);
                index = index / 2;
            }
            
            return current;
            
        } catch (Exception e) {
            return new byte[32];
        }
    }
    
    /**
     * Verifies relay chain validator signatures with multi-region requirement
     */
    private boolean verifyRelaySignatures() {
        int validSignatures = 0;
        Set<AfricanRegion> regionsRepresented = new HashSet<>();
        
        for (byte[] signature : relaySignatures) {
            if (verifyRelaySignature(signature)) {
                validSignatures++;
                
                // Track region representation
                try {
                    ByteBuffer buffer = ByteBuffer.wrap(signature);
                    int validatorId = buffer.getInt();
                    AfricanRegion region = getValidatorRegion(validatorId);
                    regionsRepresented.add(region);
                } catch (Exception e) {
                    // Skip
                }
            }
        }
        
        // Require supermajority AND multi-region representation
        boolean hasSupermajority = validSignatures >= getRequiredRelaySignatures();
        boolean hasMultiRegion = regionsRepresented.size() >= 3; // At least 3 regions
        
        return hasSupermajority && hasMultiRegion;
    }
    
    /**
     * Verifies a single relay validator signature
     */
    private boolean verifyRelaySignature(byte[] signature) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(signature);
            
            // Read validator ID
            int validatorId = buffer.getInt();
            
            // Validate validator ID is within DAC range (0-34)
            if (validatorId < 0 || validatorId >= 35) {
                return false;
            }
            
            // Read signature data
            int sigLength = buffer.getInt();
            byte[] sigData = new byte[sigLength];
            buffer.get(sigData);
            
            // Read timestamp
            long timestamp = buffer.getLong();
            
            // Verify signature is recent (within last 10 minutes)
            long currentTime = System.currentTimeMillis();
            if (currentTime - timestamp > 10 * 60 * 1000) {
                return false;
            }
            
            // Verify validator has good reputation
            AfricanRegion region = getValidatorRegion(validatorId);
            RegionalDAC dac = REGIONAL_DACS.get(region);
            if (dac != null && dac.getNodeReputation(validatorId) < 0.5) {
                return false; // Low reputation validator
            }
            
            // In production: verify signature against validator's public key using ECDSA
            // For now, basic validation
            return sigData.length >= 64 && validatorId >= 0;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Estimates the time for cross-shard finalization using African infrastructure
     */
    public long estimateFinalityTime() {
        // Base finality time: 3 seconds (block time)
        long baseTime = 3000;
        
        // Add relay chain confirmation time (multi-region consensus)
        long relayTime = 2500; // 2.5 seconds for multi-region
        
        // Add destination shard inclusion time
        long destTime = 3000; // 3 seconds
        
        // Add network latency based on African submarine cable routing
        long latency = estimateAfricanNetworkLatency(sourceShard, destinationShard);
        
        // Add DAC verification time
        long dacTime = 1000; // 1 second for DAC confirmations
        
        return baseTime + relayTime + destTime + latency + dacTime;
    }
    
    /**
     * Estimates network latency using African submarine cable infrastructure
     * Prioritizes African submarine cables over routing through Europe
     */
    private long estimateAfricanNetworkLatency(int shard1, int shard2) {
        AfricanRegion region1 = getShardRegion(shard1);
        AfricanRegion region2 = getShardRegion(shard2);
        
        // Same region: low latency
        if (region1.equals(region2)) {
            return 100; // 100ms intra-region
        }
        
        // Use African submarine cable routing
        // EASSy cable (East Africa), MainOne/WACS (West Africa), SAFE (Southern Africa)
        
        // Adjacent regions: moderate latency
        if (areAdjacentRegions(region1, region2)) {
            return 300; // 300ms adjacent regions via submarine cables
        }
        
        // Cross-continent: higher latency but still via African routes
        return 600; // 600ms cross-continent via African submarine cables
    }
    
    /**
     * Checks if two regions are geographically adjacent
     */
    private boolean areAdjacentRegions(AfricanRegion region1, AfricanRegion region2) {
        // North adjacent to: West, East
        if (region1 == AfricanRegion.NORTH_AFRICA) {
            return region2 == AfricanRegion.WEST_AFRICA || region2 == AfricanRegion.EAST_AFRICA;
        }
        
        // West adjacent to: North, Central
        if (region1 == AfricanRegion.WEST_AFRICA) {
            return region2 == AfricanRegion.NORTH_AFRICA || region2 == AfricanRegion.CENTRAL_AFRICA;
        }
        
        // East adjacent to: North, Central, Southern
        if (region1 == AfricanRegion.EAST_AFRICA) {
            return region2 == AfricanRegion.NORTH_AFRICA || 
                   region2 == AfricanRegion.CENTRAL_AFRICA ||
                   region2 == AfricanRegion.SOUTHERN_AFRICA;
        }
        
        // Central adjacent to: West, East, Southern
        if (region1 == AfricanRegion.CENTRAL_AFRICA) {
            return region2 == AfricanRegion.WEST_AFRICA || 
                   region2 == AfricanRegion.EAST_AFRICA ||
                   region2 == AfricanRegion.SOUTHERN_AFRICA;
        }
        
        // Southern adjacent to: East, Central
        if (region1 == AfricanRegion.SOUTHERN_AFRICA) {
            return region2 == AfricanRegion.EAST_AFRICA || region2 == AfricanRegion.CENTRAL_AFRICA;
        }
        
        return false;
    }
    
    @Override
    public Amount getRequiredFee() {
        // Base fee for cross-shard transfer
        BigInteger baseFee = amount.getValue().divide(BigInteger.valueOf(1000)); // 0.1%
        
        // Add relay fee (subsidized by Treasury DAO for African transfers)
        BigInteger relayFee = BigInteger.valueOf(50); // Reduced from 100
        
        // If settling via ASU, slight premium
        if (settleViaASU) {
            relayFee = relayFee.add(BigInteger.valueOf(25));
        }
        
        // African exchange priority: reduce fees by 30%
        BigInteger totalFee = baseFee.add(relayFee);
        totalFee = totalFee.multiply(BigInteger.valueOf(70)).divide(BigInteger.valueOf(100));
        
        return new Amount(totalFee, amount.getCurrency(), amount.getDecimals());
    }
    
    /**
     * Checks if this transaction enforces African sovereignty principles
     */
    public boolean enforcesAfricanSovereignty() {
        // Check geographic distribution
        if (!enforceGeographicDistribution()) return false;
        
        // Check DAC confirmations
        if (!validateDACConfirmations()) return false;
        
        // Check multi-region representation
        Set<AfricanRegion> regions = new HashSet<>();
        for (Integer nodeId : dacNodeConfirmations) {
            regions.add(getValidatorRegion(nodeId));
        }
        
        return regions.size() >= 3;
    }
    
    @Override
    public String getDescription() {
        String settlementInfo = settleViaASU ? " via ASU" : "";
        return String.format("Cross-Shard%s: %s from Shard-%d to Shard-%d (%s → %s) [%d DAC confirmations from %d regions]",
            settlementInfo,
            amount.toFormattedString(),
            sourceShard,
            destinationShard,
            getShardRegion(sourceShard),
            getShardRegion(destinationShard),
            dacNodeConfirmations.size(),
            regionValidatorCount.size()
        );
    }
    
    @Override
    public String toString() {
        return String.format("CrossShardTransaction{source=%d, dest=%d, amount=%s, ASU=%b, atomic=%b, regions=%d, nonce=%d}",
            sourceShard, destinationShard, amount, settleViaASU, requiresAtomicExecution, 
            regionValidatorCount.size(), nonce);
    }
}