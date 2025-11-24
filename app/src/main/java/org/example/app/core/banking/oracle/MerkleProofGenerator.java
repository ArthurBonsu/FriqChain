package org.example.app.core.banking.oracle;

import org.example.app.core.types.Hash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Logger;

/**
 * MerkleProofGenerator - Data availability proofs for off-chain storage
 * 
 * Generates Merkle roots and proofs for oracle data stored off-chain (IPFS/Arweave).
 * Enables on-chain verification without storing full data on-chain.
 * 
 * Based on Implementation Guidebook Section VI: Merkleized Data Availability
 */
public class MerkleProofGenerator {
    private static final Logger LOGGER = Logger.getLogger(MerkleProofGenerator.class.getName());
    
    /**
     * Merkle tree node
     */
    public static class MerkleNode {
        private final byte[] hash;
        private final MerkleNode left;
        private final MerkleNode right;
        
        public MerkleNode(byte[] hash, MerkleNode left, MerkleNode right) {
            this.hash = hash;
            this.left = left;
            this.right = right;
        }
        
        public byte[] getHash() { return hash; }
        public MerkleNode getLeft() { return left; }
        public MerkleNode getRight() { return right; }
        public boolean isLeaf() { return left == null && right == null; }
    }
    
    /**
     * Merkle proof
     */
    public static class MerkleProof {
        private final byte[] leafHash;
        private final List<byte[]> proofHashes;
        private final List<Boolean> proofDirections; // true = right, false = left
        private final byte[] rootHash;
        
        public MerkleProof(byte[] leafHash, List<byte[]> proofHashes, 
                         List<Boolean> proofDirections, byte[] rootHash) {
            this.leafHash = leafHash;
            this.proofHashes = new ArrayList<>(proofHashes);
            this.proofDirections = new ArrayList<>(proofDirections);
            this.rootHash = rootHash;
        }
        
        public byte[] getLeafHash() { return leafHash; }
        public List<byte[]> getProofHashes() { return new ArrayList<>(proofHashes); }
        public List<Boolean> getProofDirections() { return new ArrayList<>(proofDirections); }
        public byte[] getRootHash() { return rootHash; }
    }
    
    /**
     * Constructor
     */
    public MerkleProofGenerator() {
        LOGGER.info("MerkleProofGenerator initialized");
    }
    
    /**
     * Builds Merkle tree from data items
     */
    public MerkleNode buildMerkleTree(List<byte[]> dataItems) throws NoSuchAlgorithmException {
        if (dataItems == null || dataItems.isEmpty()) {
            throw new IllegalArgumentException("Cannot build Merkle tree from empty data");
        }
        
        // Create leaf nodes
        List<MerkleNode> nodes = new ArrayList<>();
        for (byte[] data : dataItems) {
            byte[] hash = hashData(data);
            nodes.add(new MerkleNode(hash, null, null));
        }
        
        // Build tree bottom-up
        while (nodes.size() > 1) {
            List<MerkleNode> parentNodes = new ArrayList<>();
            
            for (int i = 0; i < nodes.size(); i += 2) {
                MerkleNode left = nodes.get(i);
                MerkleNode right = (i + 1 < nodes.size()) ? nodes.get(i + 1) : left;
                
                byte[] combinedHash = hashCombined(left.getHash(), right.getHash());
                parentNodes.add(new MerkleNode(combinedHash, left, right));
            }
            
            nodes = parentNodes;
        }
        
        LOGGER.fine("Merkle tree built with " + dataItems.size() + " leaves");
        
        return nodes.get(0); // Root node
    }
    
    /**
     * Generates Merkle proof for specific data item
     */
    public MerkleProof generateProof(List<byte[]> dataItems, int index) 
            throws NoSuchAlgorithmException {
        
        if (index < 0 || index >= dataItems.size()) {
            throw new IllegalArgumentException("Invalid index: " + index);
        }
        
        byte[] leafHash = hashData(dataItems.get(index));
        
        // Build tree and collect proof path
        List<byte[]> proofHashes = new ArrayList<>();
        List<Boolean> proofDirections = new ArrayList<>();
        
        List<byte[]> currentLevel = new ArrayList<>();
        for (byte[] data : dataItems) {
            currentLevel.add(hashData(data));
        }
        
        int currentIndex = index;
        
        while (currentLevel.size() > 1) {
            List<byte[]> nextLevel = new ArrayList<>();
            
            for (int i = 0; i < currentLevel.size(); i += 2) {
                byte[] left = currentLevel.get(i);
                byte[] right = (i + 1 < currentLevel.size()) ? currentLevel.get(i + 1) : left;
                
                // If this is our target node, add sibling to proof
                if (i == currentIndex || i + 1 == currentIndex) {
                    if (currentIndex == i) {
                        // We're the left node, sibling is right
                        proofHashes.add(right);
                        proofDirections.add(true);
                    } else {
                        // We're the right node, sibling is left
                        proofHashes.add(left);
                        proofDirections.add(false);
                    }
                    currentIndex = i / 2;
                }
                
                nextLevel.add(hashCombined(left, right));
            }
            
            currentLevel = nextLevel;
        }
        
        byte[] rootHash = currentLevel.get(0);
        
        LOGGER.fine("Merkle proof generated for index: " + index);
        
        return new MerkleProof(leafHash, proofHashes, proofDirections, rootHash);
    }
    
    /**
     * Verifies Merkle proof
     */
    public boolean verifyProof(MerkleProof proof) throws NoSuchAlgorithmException {
        byte[] currentHash = proof.getLeafHash();
        
        List<byte[]> proofHashes = proof.getProofHashes();
        List<Boolean> proofDirections = proof.getProofDirections();
        
        for (int i = 0; i < proofHashes.size(); i++) {
            byte[] siblingHash = proofHashes.get(i);
            boolean isRight = proofDirections.get(i);
            
            if (isRight) {
                // Sibling is on the right
                currentHash = hashCombined(currentHash, siblingHash);
            } else {
                // Sibling is on the left
                currentHash = hashCombined(siblingHash, currentHash);
            }
        }
        
        boolean verified = Arrays.equals(currentHash, proof.getRootHash());
        
        LOGGER.fine("Merkle proof verification: " + (verified ? "SUCCESS" : "FAILED"));
        
        return verified;
    }
    
    /**
     * Hashes single data item
     */
    private byte[] hashData(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(data);
    }
    
    /**
     * Hashes combined left and right hashes
     */
    private byte[] hashCombined(byte[] left, byte[] right) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        
        byte[] combined = new byte[left.length + right.length];
        System.arraycopy(left, 0, combined, 0, left.length);
        System.arraycopy(right, 0, combined, left.length, right.length);
        
        return digest.digest(combined);
    }
    
    /**
     * Converts Merkle root to Hash object
     */
    public Hash convertToHash(byte[] rootHash) {
        return Hash.fromBytes(rootHash);
    }
    
    /**
     * Converts bytes to hex string
     */
    public String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
