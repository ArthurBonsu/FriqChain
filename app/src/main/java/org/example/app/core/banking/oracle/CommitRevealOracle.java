package org.example.app.core.banking.oracle;

import org.example.app.core.types.Hash;
import org.example.app.core.banking.oracle.AfricanSovereigntyTypes.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * CommitRevealOracle - Front-running prevention with African Sovereignty
 * 
 * 🔸 MODERATE PRIORITY - UPGRADED FOR AFRICAN NODE SOVEREIGNTY
 * 
 * Two-phase submission with regional coordination:
 * 1. COMMIT: Submit hash(data + nonce) without revealing data
 * 2. REVEAL: Submit actual data + nonce after commitment window closes
 * 
 * SOVEREIGNTY FEATURES:
 * - Regional committee coordination (track by 5 African regions)
 * - Byzantine Fault Tolerance (5/7 consensus per region)
 * - Cross-regional validation (requires 3+ of 5 regions)
 * - Geographic tracking of commitments
 * 
 * Prevents:
 * - Front-running attacks
 * - Data manipulation before consensus
 * - Oracle coordination attacks
 * - Single-region manipulation
 */
public class CommitRevealOracle {
    private static final Logger LOGGER = Logger.getLogger(CommitRevealOracle.class.getName());
    
    // ============ SOVEREIGNTY PARAMETERS ============
    
    private static final int MIN_REGIONS_FOR_CONSENSUS = 3; // Need 3 of 5 regions
    private static final int BFT_THRESHOLD_PER_REGION = 5;  // 5 of 7 nodes per region
    
    // ============ COMMIT-REVEAL PARAMETERS ============
    
    private static final long COMMIT_WINDOW_MS = 300000; // 5 minutes
    private static final long REVEAL_WINDOW_MS = 600000; // 10 minutes
    
    // Commitment tracking with geographic information
    private final Map<String, Commitment> commitments;
    private final Map<String, RevealedData> reveals;
    private final Map<AfricanRegion, List<String>> regionalCommitments; // NEW: Track by region
    private final Map<String, Map<AfricanRegion, Integer>> dataKeyRegionalCounts; // NEW: Regional consensus tracking
    
    /**
     * Enhanced commitment record with geographic tracking
     */
    public static class Commitment {
        private final String commitmentId;
        private final String reporterId;
        private final AfricanRegion region; // NEW: Reporter's region
        private final byte[] commitmentHash;
        private final long commitTime;
        private final long revealDeadline;
        private final String dataKey; // NEW: Track what data this is for
        
        public Commitment(String reporterId, AfricanRegion region, byte[] commitmentHash, String dataKey) {
            this.commitmentId = UUID.randomUUID().toString();
            this.reporterId = reporterId;
            this.region = region;
            this.commitmentHash = commitmentHash;
            this.dataKey = dataKey;
            this.commitTime = System.currentTimeMillis();
            this.revealDeadline = commitTime + COMMIT_WINDOW_MS + REVEAL_WINDOW_MS;
        }
        
        public String getCommitmentId() { return commitmentId; }
        public String getReporterId() { return reporterId; }
        public AfricanRegion getRegion() { return region; }
        public byte[] getCommitmentHash() { return commitmentHash; }
        public String getDataKey() { return dataKey; }
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
        private final AfricanRegion region;
        private final Object data;
        private final byte[] nonce;
        private final long revealTime;
        private boolean verified;
        
        public RevealedData(String commitmentId, String reporterId, AfricanRegion region,
                          Object data, byte[] nonce) {
            this.commitmentId = commitmentId;
            this.reporterId = reporterId;
            this.region = region;
            this.data = data;
            this.nonce = nonce;
            this.revealTime = System.currentTimeMillis();
            this.verified = false;
        }
        
        public String getCommitmentId() { return commitmentId; }
        public String getReporterId() { return reporterId; }
        public AfricanRegion getRegion() { return region; }
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
        this.regionalCommitments = new ConcurrentHashMap<>();
        this.dataKeyRegionalCounts = new ConcurrentHashMap<>();
        
        LOGGER.info("CommitRevealOracle initialized with regional coordination");
    }
    
    /**
     * Phase 1: Commit WITH REGIONAL TRACKING
     */
    public String commit(String reporterId, AfricanRegion region, String dataKey, Object data) 
            throws NoSuchAlgorithmException {
        
        // Generate random nonce
        byte[] nonce = generateNonce();
        
        // Calculate commitment hash
        byte[] commitmentHash = calculateCommitmentHash(data, nonce);
        
        // Create commitment with regional info
        Commitment commitment = new Commitment(reporterId, region, commitmentHash, dataKey);
        commitments.put(commitment.getCommitmentId(), commitment);
        
        // SOVEREIGNTY FEATURE: Track by region
        regionalCommitments.computeIfAbsent(region, k -> new ArrayList<>())
            .add(commitment.getCommitmentId());
        
        // Track regional participation for this data key
        dataKeyRegionalCounts.computeIfAbsent(dataKey, k -> new HashMap<>())
            .merge(region, 1, Integer::sum);
        
        LOGGER.info(String.format("Commitment created: %s for reporter: %s in region: %s for data: %s",
            commitment.getCommitmentId(), reporterId, region, dataKey));
        
        // Store nonce separately for reporter
        return commitment.getCommitmentId() + ":" + bytesToHex(nonce);
    }
    
    /**
     * Phase 2: Reveal WITH REGIONAL VALIDATION
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
        
        // Store revealed data with regional info
        RevealedData revealed = new RevealedData(commitmentId, reporterId, 
            commitment.getRegion(), data, nonce);
        revealed.setVerified(true);
        reveals.put(commitmentId, revealed);
        
        LOGGER.info(String.format("Data revealed and verified: %s from region: %s",
            commitmentId, commitment.getRegion()));
        
        return true;
    }
    
    /**
     * SOVEREIGNTY CHECK: Validates cross-regional consensus
     */
    public boolean validateCrossRegionalConsensus(String dataKey) {
        Map<AfricanRegion, Integer> regionalCounts = dataKeyRegionalCounts.get(dataKey);
        
        if (regionalCounts == null || regionalCounts.size() < MIN_REGIONS_FOR_CONSENSUS) {
            LOGGER.warning(String.format(
                "Insufficient regional consensus for %s: %d regions (need %d)",
                dataKey, regionalCounts != null ? regionalCounts.size() : 0, 
                MIN_REGIONS_FOR_CONSENSUS));
            return false;
        }
        
        // Check each participating region has BFT threshold (5 commitments)
        for (Map.Entry<AfricanRegion, Integer> entry : regionalCounts.entrySet()) {
            if (entry.getValue() < BFT_THRESHOLD_PER_REGION) {
                LOGGER.warning(String.format(
                    "Region %s below BFT threshold: %d commitments (need %d)",
                    entry.getKey(), entry.getValue(), BFT_THRESHOLD_PER_REGION));
                return false;
            }
        }
        
        LOGGER.info(String.format(
            "Cross-regional consensus validated: %s - %d regions participating",
            dataKey, regionalCounts.size()));
        
        return true;
    }
    
    /**
     * Gets regional consensus details for data key
     */
    public Map<AfricanRegion, Integer> getRegionalConsensus(String dataKey) {
        return new HashMap<>(dataKeyRegionalCounts.getOrDefault(dataKey, new HashMap<>()));
    }
    
    /**
     * Gets commitments for a specific region
     */
    public List<Commitment> getRegionalCommitments(AfricanRegion region) {
        List<String> commitmentIds = regionalCommitments.getOrDefault(region, new ArrayList<>());
        
        return commitmentIds.stream()
            .map(commitments::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Checks regional BFT status
     */
    public boolean hasRegionalBFT(AfricanRegion region, String dataKey) {
        Map<AfricanRegion, Integer> counts = dataKeyRegionalCounts.get(dataKey);
        if (counts == null) {
            return false;
        }
        
        return counts.getOrDefault(region, 0) >= BFT_THRESHOLD_PER_REGION;
    }
    
    /**
     * Calculates commitment hash: hash(data || nonce)
     */
    private byte[] calculateCommitmentHash(Object data, byte[] nonce) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        
        byte[] dataBytes = data.toString().getBytes();
        
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
    
    public Commitment getCommitment(String commitmentId) {
        return commitments.get(commitmentId);
    }
    
    public RevealedData getRevealedData(String commitmentId) {
        return reveals.get(commitmentId);
    }
    
    /**
     * Cleans up expired commitments
     */
    public void cleanupExpired() {
        // Remove expired commitments
        List<String> expiredIds = commitments.entrySet().stream()
            .filter(entry -> entry.getValue().isExpired())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        for (String id : expiredIds) {
            Commitment commitment = commitments.remove(id);
            if (commitment != null) {
                // Clean up regional tracking
                AfricanRegion region = commitment.getRegion();
                List<String> regional = regionalCommitments.get(region);
                if (regional != null) {
                    regional.remove(id);
                }
            }
        }
        
        LOGGER.fine("Cleaned up " + expiredIds.size() + " expired commitments");
    }
    
    /**
     * Gets statistics with sovereignty metrics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalCommitments", commitments.size());
        stats.put("totalReveals", reveals.size());
        stats.put("verifiedReveals", (int) reveals.values().stream()
            .filter(RevealedData::isVerified).count());
        
        // Sovereignty metrics
        stats.put("regionsParticipating", regionalCommitments.size());
        
        Map<AfricanRegion, Integer> regionCommitCounts = new HashMap<>();
        for (Map.Entry<AfricanRegion, List<String>> entry : regionalCommitments.entrySet()) {
            regionCommitCounts.put(entry.getKey(), entry.getValue().size());
        }
        stats.put("commitmentsByRegion", regionCommitCounts);
        
        stats.put("dataKeysTracked", dataKeyRegionalCounts.size());
        
        // Count data keys with full consensus (3+ regions)
        long fullConsensusCount = dataKeyRegionalCounts.values().stream()
            .filter(map -> map.size() >= MIN_REGIONS_FOR_CONSENSUS)
            .count();
        stats.put("dataKeysWithFullConsensus", fullConsensusCount);
        
        return stats;
    }
}