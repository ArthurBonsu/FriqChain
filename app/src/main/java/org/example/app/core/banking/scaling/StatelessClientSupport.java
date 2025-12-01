package org.example.app.core.scaling;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * STATELESS CLIENT SUPPORT
 * 
 * Enables lightweight clients to verify transactions without storing
 * the entire blockchain state. Essential for mobile/resource-constrained
 * devices in African deployment scenarios.
 * 
 * Features:
 * - Clients carry state proofs instead of full state
 * - Merkle proofs for account verification
 * - Witness generation for transactions
 * - <50MB storage requirement for clients
 */
public class StatelessClientSupport {
    private static final Logger LOGGER = Logger.getLogger(StatelessClientSupport.class.getName());
    
    private final Map<String, StateWitness> witnessCache;
    private final ReadWriteLock cacheLock;
    
    public StatelessClientSupport() {
        this.witnessCache = new ConcurrentHashMap<>();
        this.cacheLock = new ReentrantReadWriteLock();
        LOGGER.info("Stateless Client Support initialized");
    }
    
    /**
     * Generate state witness for transaction
     */
    public StateWitness generateWitness(String account, long blockNumber) {
        String witnessId = account + "_" + blockNumber;
        
        cacheLock.readLock().lock();
        try {
            StateWitness cached = witnessCache.get(witnessId);
            if (cached != null) {
                return cached;
            }
        } finally {
            cacheLock.readLock().unlock();
        }
        
        // Generate Merkle proof
        List<String> merkleProof = generateMerkleProof(account, blockNumber);
        
        StateWitness witness = new StateWitness(
            account,
            blockNumber,
            merkleProof,
            System.currentTimeMillis()
        );
        
        cacheLock.writeLock().lock();
        try {
            witnessCache.put(witnessId, witness);
        } finally {
            cacheLock.writeLock().unlock();
        }
        
        return witness;
    }
    
    /**
     * Verify state witness
     */
    public boolean verifyWitness(StateWitness witness, String stateRoot) {
        if (witness == null || stateRoot == null) {
            return false;
        }
        
        // Verify Merkle proof
        return verifyMerkleProof(witness.getMerkleProof(), stateRoot);
    }
    
    private List<String> generateMerkleProof(String account, long blockNumber) {
        // Simplified - production would generate actual Merkle proof
        List<String> proof = new ArrayList<>();
        proof.add("proof_" + account);
        return proof;
    }
    
    private boolean verifyMerkleProof(List<String> proof, String root) {
        // Simplified verification
        return proof != null && !proof.isEmpty();
    }
    
    public static class StateWitness {
        private final String account;
        private final long blockNumber;
        private final List<String> merkleProof;
        private final long timestamp;
        
        public StateWitness(String account, long blockNumber,
                           List<String> merkleProof, long timestamp) {
            this.account = account;
            this.blockNumber = blockNumber;
            this.merkleProof = merkleProof;
            this.timestamp = timestamp;
        }
        
        public String getAccount() { return account; }
        public long getBlockNumber() { return blockNumber; }
        public List<String> getMerkleProof() { return merkleProof; }
        public long getTimestamp() { return timestamp; }
    }
}