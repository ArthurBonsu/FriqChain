package org.example.app.core.blockchain.transaction;

import org.example.app.core.types.Address;
import org.example.app.core.types.Amount;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * CrossShardTransaction enables transactions across blockchain shards,
 * critical for scaling the African Tokenized Currency System to 1.2 billion users.
 * 
 * Sharding strategy:
 * - Passchain Shards: Geographic/functional regions (West Africa, Central Africa, Retail)
 * - Local consensus: ~5,000 TPS per shard
 * - Cross-Shard Bridge: Relay chain handles message passing
 * - Total capacity: 20 shards × 5,000 TPS = 100,000 TPS continent-wide
 */
public class CrossShardTransaction extends BaseTransaction {
    
    private final Address senderAddress;
    private final Address recipientAddress;
    private final int sourceShard;
    private final int destinationShard;
    private final Amount amount;
    private final byte[] crossShardProof; // Merkle proof for cross-shard verification
    private final List<byte[]> relaySignatures; // Relay chain validator signatures
    private final String messageHash; // Hash of cross-shard message
    private final long sourceBlockHeight; // Source shard block height
    private final long nonce; // Prevents replay attacks
    private final boolean requiresAtomicExecution; // For atomic cross-shard swaps
    
    /**
     * Creates a new CrossShardTransaction
     * 
     * @param senderAddress Sender's address
     * @param recipientAddress Recipient's address
     * @param sourceShard Source shard ID
     * @param destinationShard Destination shard ID
     * @param amount Amount to transfer
     * @param crossShardProof Merkle proof for verification
     * @param relaySignatures Relay chain validator signatures
     * @param sourceBlockHeight Block height in source shard
     * @param nonce Transaction nonce
     * @param requiresAtomicExecution Whether this needs atomic execution
     */
    public CrossShardTransaction(
            Address senderAddress, Address recipientAddress,
            int sourceShard, int destinationShard,
            Amount amount, byte[] crossShardProof,
            List<byte[]> relaySignatures, long sourceBlockHeight,
            long nonce, boolean requiresAtomicExecution) {
        
        super(buildTransactionData(senderAddress, recipientAddress, sourceShard,
                destinationShard, amount, crossShardProof, relaySignatures,
                sourceBlockHeight, nonce, requiresAtomicExecution),
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
        this.messageHash = generateMessageHash();
        this.sourceBlockHeight = sourceBlockHeight;
        this.nonce = nonce;
        this.requiresAtomicExecution = requiresAtomicExecution;
    }
    
    /**
     * Builds the transaction data byte array
     */
    private static byte[] buildTransactionData(
            Address senderAddress, Address recipientAddress,
            int sourceShard, int destinationShard,
            Amount amount, byte[] crossShardProof,
            List<byte[]> relaySignatures, long sourceBlockHeight,
            long nonce, boolean requiresAtomicExecution) {
        
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        
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
        
        // Write atomic execution flag
        buffer.put((byte) (requiresAtomicExecution ? 1 : 0));
        
        buffer.flip();
        byte[] result = new byte[buffer.limit()];
        buffer.get(result);
        return result;
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
    
    @Override
    public boolean isCrossBorder() {
        // Cross-shard can be cross-border if shards are regional
        return isGeographicShard(sourceShard) && isGeographicShard(destinationShard);
    }
    
    /**
     * Checks if a shard is a geographic shard (vs functional shard)
     */
    private boolean isGeographicShard(int shardId) {
        // Geographic shards: 0-19 (regional sharding)
        // Functional shards: 20+ (banking, retail, etc.)
        return shardId < 20;
    }
    
    /**
     * Generates a hash of the cross-shard message
     */
    private String generateMessageHash() {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            
            ByteBuffer buffer = ByteBuffer.allocate(256);
            buffer.put(senderAddress.toSlice());
            buffer.put(recipientAddress.toSlice());
            buffer.putInt(sourceShard);
            buffer.putInt(destinationShard);
            buffer.put(amount.toBytes());
            buffer.putLong(sourceBlockHeight);
            buffer.putLong(nonce);
            
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
        // Validate shard IDs
        if (sourceShard < 0 || sourceShard >= 50 || // Max 50 shards
            destinationShard < 0 || destinationShard >= 50) {
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
        
        // Validate relay signatures (minimum 2/3 required)
        int requiredSignatures = getRequiredRelaySignatures();
        if (relaySignatures == null || relaySignatures.size() < requiredSignatures) {
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
        
        // Verify relay signatures
        return verifyRelaySignatures();
    }
    
    /**
     * Gets the required number of relay signatures based on shard configuration
     */
    private int getRequiredRelaySignatures() {
        // For critical cross-shard transactions, require more signatures
        if (requiresAtomicExecution || amount.getValue().compareTo(BigInteger.valueOf(1000000)) > 0) {
            return 5; // High-value or atomic: 5 signatures
        } else {
            return 3; // Standard: 3 signatures
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
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            
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
     * Verifies relay chain validator signatures
     */
    private boolean verifyRelaySignatures() {
        int validSignatures = 0;
        
        for (byte[] signature : relaySignatures) {
            if (verifyRelaySignature(signature)) {
                validSignatures++;
            }
        }
        
        return validSignatures >= getRequiredRelaySignatures();
    }
    
    /**
     * Verifies a single relay validator signature
     */
    private boolean verifyRelaySignature(byte[] signature) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(signature);
            
            // Read validator ID
            int validatorId = buffer.getInt();
            
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
            
            // In production: verify signature against validator's public key
            // For now, basic validation
            return sigData.length >= 64 && validatorId >= 0;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Estimates the time for cross-shard finalization
     */
    public long estimateFinalityTime() {
        // Base finality time: 3 seconds (block time)
        long baseTime = 3000;
        
        // Add relay chain confirmation time
        long relayTime = 2000; // 2 seconds
        
        // Add destination shard inclusion time
        long destTime = 3000; // 3 seconds
        
        // Add network latency based on geographic distance
        long latency = estimateNetworkLatency(sourceShard, destinationShard);
        
        return baseTime + relayTime + destTime + latency;
    }
    
    /**
     * Estimates network latency between shards
     */
    private long estimateNetworkLatency(int shard1, int shard2) {
        // If both shards are in same region, low latency
        if (getShardRegion(shard1).equals(getShardRegion(shard2))) {
            return 100; // 100ms
        } else {
            return 500; // 500ms cross-region
        }
    }
    
    /**
     * Gets the geographic region for a shard
     */
    private String getShardRegion(int shardId) {
        if (shardId < 5) return "WEST_AFRICA";
        if (shardId < 10) return "EAST_AFRICA";
        if (shardId < 15) return "CENTRAL_AFRICA";
        if (shardId < 20) return "SOUTHERN_AFRICA";
        return "FUNCTIONAL"; // Non-geographic shard
    }
    
    @Override
    public Amount getRequiredFee() {
        // Cross-shard fees are slightly higher due to relay costs
        BigInteger baseFee = amount.getValue().divide(BigInteger.valueOf(500)); // 0.2%
        
        // Add relay fee
        BigInteger relayFee = BigInteger.valueOf(100);
        
        return new Amount(baseFee.add(relayFee), amount.getCurrency(), amount.getDecimals());
    }
    
    @Override
    public String getDescription() {
        return String.format("Cross-Shard: %s from Shard-%d to Shard-%d (%s → %s)",
            amount.toFormattedString(),
            sourceShard,
            destinationShard,
            getShardRegion(sourceShard),
            getShardRegion(destinationShard)
        );
    }
    
    @Override
    public String toString() {
        return String.format("CrossShardTransaction{source=%d, dest=%d, amount=%s, atomic=%b, nonce=%d}",
            sourceShard, destinationShard, amount, requiresAtomicExecution, nonce);
    }
}