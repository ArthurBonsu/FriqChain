package org.example.app.core.banking.oracle;

import org.example.app.core.types.Hash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * CommitRevealOracle - Prevents front-running via commit-reveal protocol
 * 
 * Two-phase submission:
 * 1. COMMIT: Submit hash(data + nonce) without revealing data
 * 2. REVEAL: Submit actual data + nonce after commitment window closes
 * 
 * Prevents:
 * - Front-running attacks
 * - Data manipulation before consensus
 * - Oracle coordination attacks
 */
public class CommitRevealOracle {
    private static final Logger LOGGER = Logger.getLogger(CommitRevealOracle.class.getName());
    
    // Commit window parameters
    private static final long COMMIT_WINDOW_MS = 300000; // 5 minutes
    private static final long REVEAL_WINDOW_MS = 600000; // 10 minutes
    
    // Commitment tracking
    private final Map<String, Commitment> commitments;
    private final Map<String, RevealedData> reveals;
    
    /**
     * Commitment record
     */
    public static class Commitment {
        private final String commitmentId;
        private final String reporterId;
        private final byte[] commitmentHash;
        private final long commitTime;
        private final long revealDeadline;
        
        public Commitment(String reporterId, byte[] commitmentHash) {
            this.commitmentId = UUID.randomUUID().toString();
            this.reporterId = reporterId;
            this.commitmentHash = commitmentHash;
            this.commitTime = System.currentTimeMillis();
            this.revealDeadline = commitTime + COMMIT_WINDOW_MS + REVEAL_WINDOW_MS;
        }
        
        public String getCommitmentId() { return commitmentId; }
        public String getReporterId() { return reporterId; }
        public byte[] getCommitmentHash() { return commitmentHash; }
        public long getCommitTime() { return commitTime; }
        public long getRevealDeadline() { return revealDeadline; }
        
        public boolean isRevealWindowOpen() {
            long now = System.currentTimeMillis();
            return now >= commitTime + COMMIT_WINDOW_MS && now <= revealDeadline;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > revealDeadline;
        }
    }
    
    /**
     * Revealed data record
     */
    public static class RevealedData {
        private final String commitmentId;
        private final String reporterId;
        private final Object data;
        private final byte[] nonce;
        private final long revealTime;
        private boolean verified;
        
        public RevealedData(String commitmentId, String reporterId, Object data, byte[] nonce) {
            this.commitmentId = commitmentId;
            this.reporterId = reporterId;
            this.data = data;
            this.nonce = nonce;
            this.revealTime = System.currentTimeMillis();
            this.verified = false;
        }
        
        public String getCommitmentId() { return commitmentId; }
        public String getReporterId() { return reporterId; }
        public Object getData() { return data; }
        public byte[] getNonce() { return nonce; }
        public long getRevealTime() { return revealTime; }
        public boolean isVerified() { return verified; }
        
        public void setVerified(boolean verified) {
            this.verified = verified;
        }
    }
    
    /**
     * Constructor
     */
    public CommitRevealOracle() {
        this.commitments = new ConcurrentHashMap<>();
        this.reveals = new ConcurrentHashMap<>();
        
        LOGGER.info("CommitRevealOracle initialized");
    }
    
    /**
     * Phase 1: Commit to data without revealing it
     */
    public String commit(String reporterId, Object data) throws NoSuchAlgorithmException {
        // Generate random nonce
        byte[] nonce = generateNonce();
        
        // Calculate commitment hash
        byte[] commitmentHash = calculateCommitmentHash(data, nonce);
        
        // Create commitment
        Commitment commitment = new Commitment(reporterId, commitmentHash);
        commitments.put(commitment.getCommitmentId(), commitment);
        
        LOGGER.info(String.format("Commitment created: %s for reporter: %s",
            commitment.getCommitmentId(), reporterId));
        
        // Store nonce separately for reporter (in real implementation, reporter keeps this private)
        return commitment.getCommitmentId() + ":" + bytesToHex(nonce);
    }
    
    /**
     * Phase 2: Reveal committed data
     */
    public boolean reveal(String commitmentId, String reporterId, Object data, byte[] nonce) 
            throws NoSuchAlgorithmException {
        
        Commitment commitment = commitments.get(commitmentId);
        
        if (commitment == null) {
            LOGGER.warning("Commitment not found: " + commitmentId);
            return false;
        }
        
        if (!commitment.getReporterId().equals(reporterId)) {
            LOGGER.warning("Reporter mismatch for commitment: " + commitmentId);
            return false;
        }
        
        if (!commitment.isRevealWindowOpen()) {
            if (commitment.isExpired()) {
                LOGGER.warning("Reveal window expired: " + commitmentId);
            } else {
                LOGGER.warning("Reveal window not yet open: " + commitmentId);
            }
            return false;
        }
        
        // Verify commitment
        byte[] calculatedHash = calculateCommitmentHash(data, nonce);
        
        if (!Arrays.equals(calculatedHash, commitment.getCommitmentHash())) {
            LOGGER.warning("Commitment verification failed: " + commitmentId);
            return false;
        }
        
        // Store revealed data
        RevealedData revealed = new RevealedData(commitmentId, reporterId, data, nonce);
        revealed.setVerified(true);
        reveals.put(commitmentId, revealed);
        
        LOGGER.info(String.format("Data revealed and verified: %s", commitmentId));
        
        return true;
    }
    
    /**
     * Calculates commitment hash: hash(data || nonce)
     */
    private byte[] calculateCommitmentHash(Object data, byte[] nonce) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        
        // Convert data to bytes
        byte[] dataBytes = data.toString().getBytes();
        
        // Combine data and nonce
        byte[] combined = new byte[dataBytes.length + nonce.length];
        System.arraycopy(dataBytes, 0, combined, 0, dataBytes.length);
        System.arraycopy(nonce, 0, combined, dataBytes.length, nonce.length);
        
        return digest.digest(combined);
    }
    
    /**
     * Generates random nonce
     */
    private byte[] generateNonce() {
        byte[] nonce = new byte[32];
        new SecureRandom().nextBytes(nonce);
        return nonce;
    }
    
    /**
     * Converts bytes to hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * Converts hex string to bytes
     */
    public byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }
    
    /**
     * Gets commitment
     */
    public Commitment getCommitment(String commitmentId) {
        return commitments.get(commitmentId);
    }
    
    /**
     * Gets revealed data
     */
    public RevealedData getRevealedData(String commitmentId) {
        return reveals.get(commitmentId);
    }
    
    /**
     * Cleans up expired commitments
     */
    public void cleanupExpired() {
        commitments.entrySet().removeIf(entry -> entry.getValue().isExpired());
        LOGGER.fine("Cleaned up expired commitments");
    }
    
    /**
     * Gets statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCommitments", commitments.size());
        stats.put("totalReveals", reveals.size());
        stats.put("verifiedReveals", (int) reveals.values().stream()
            .filter(RevealedData::isVerified).count());
        return stats;
    }
}
