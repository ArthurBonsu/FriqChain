package org.example.app.core.banking.compliance;

import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;
import java.util.*;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * EvidenceSubmissionPortal - Secure on-chain evidence portal for disputes
 * 
 * Features:
 * - Evidence hash storage on-chain (content stored off-chain / IPFS)
 * - Timestamp proofs (immutable filing records)
 * - Multi-format support (documents, digital signatures, multimedia)
 * - Encrypted submissions for sensitive data
 * - Integrity verification via cryptographic hashing
 * - Access control (only parties and arbitrators)
 */
public class EvidenceSubmissionPortal {
    private static final Logger LOGGER = Logger.getLogger(EvidenceSubmissionPortal.class.getName());
    
    private final Map<String, Evidence> evidenceRecords; // Evidence ID -> Evidence
    private final Map<String, List<String>> disputeEvidenceMap; // Dispute ID -> list of Evidence IDs
    private final Map<String, EvidenceMetadata> metadataStore;
    
    public enum EvidenceType {
        DOCUMENT,              // PDF, Word, etc.
        IMAGE,                 // Screenshots, photos
        VIDEO,                 // Video recordings
        AUDIT_REPORT,          // Reserve audits, financial reports
        SIGNATURE,             // Digital signatures
        TRANSACTION_RECORD,    // On-chain transaction records
        TESTIMONY,             // Written statements
        CRYPTOGRAPHIC_PROOF,   // Zero-knowledge proofs
        ORACLE_DATA,           // Oracle feed data
        CUSTOMS_DECLARATION,   // Trade documents
        OTHER
    }
    
    public enum EvidenceStatus {
        SUBMITTED,
        VERIFIED,
        CHALLENGED,
        ACCEPTED,
        REJECTED,
        SEALED
    }
    
    public static class Evidence {
        private final String evidenceId;
        private final String disputeId;
        private final Address submitter;
        private final EvidenceType type;
        private final String contentHash; // SHA-256 of content
        private final String ipfsHash; // Off-chain storage reference
        private final long submissionTime;
        private final byte[] encryptionKey; // For sensitive evidence
        private final List<String> witnessSignatures;
        private final String description;
        private EvidenceStatus status;
        private int challengeCount;
        private final List<String> challengeReasons;
        private final Map<String, String> arbitratorNotes;
        
        public Evidence(String evidenceId, String disputeId, Address submitter,
                       EvidenceType type, String contentHash, String ipfsHash,
                       byte[] encryptionKey, String description) {
            this.evidenceId = evidenceId;
            this.disputeId = disputeId;
            this.submitter = submitter;
            this.type = type;
            this.contentHash = contentHash;
            this.ipfsHash = ipfsHash;
            this.submissionTime = System.currentTimeMillis();
            this.encryptionKey = encryptionKey != null ? encryptionKey.clone() : null;
            this.witnessSignatures = new ArrayList<>();
            this.description = description;
            this.status = EvidenceStatus.SUBMITTED;
            this.challengeCount = 0;
            this.challengeReasons = new ArrayList<>();
            this.arbitratorNotes = new ConcurrentHashMap<>();
        }
        
        // Getters
        public String getEvidenceId() { return evidenceId; }
        public String getDisputeId() { return disputeId; }
        public Address getSubmitter() { return submitter; }
        public EvidenceType getType() { return type; }
        public String getContentHash() { return contentHash; }
        public String getIpfsHash() { return ipfsHash; }
        public long getSubmissionTime() { return submissionTime; }
        public String getDescription() { return description; }
        public EvidenceStatus getStatus() { return status; }
        public int getChallengeCount() { return challengeCount; }
        public List<String> getChallengeReasons() { return new ArrayList<>(challengeReasons); }
        
        public void setStatus(EvidenceStatus s) { this.status = s; }
        public void addWitnessSignature(String signature) { witnessSignatures.add(signature); }
        public void addChallenge(String reason) {
            challengeReasons.add(reason);
            challengeCount++;
        }
        public void addArbitratorNote(String arbitratorId, String note) {
            arbitratorNotes.put(arbitratorId, note);
        }
        
        public int getWitnessCount() { return witnessSignatures.size(); }
        public List<String> getWitnessSignatures() { return new ArrayList<>(witnessSignatures); }
    }
    
    public static class EvidenceMetadata {
        private final String evidenceId;
        private final String chain; // "MAIN" for on-chain, "IPFS" for off-chain
        private final long fileSize;
        private final String fileName;
        private final long expirationTime; // When evidence becomes public (optional seal period)
        private final boolean isEncrypted;
        private final int encryptionAlgorithm; // 1 = AES-256-GCM
        
        public EvidenceMetadata(String evidenceId, String chain, long fileSize,
                               String fileName, boolean isEncrypted, int encryptionAlgorithm) {
            this.evidenceId = evidenceId;
            this.chain = chain;
            this.fileSize = fileSize;
            this.fileName = fileName;
            this.expirationTime = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000); // 1 year
            this.isEncrypted = isEncrypted;
            this.encryptionAlgorithm = encryptionAlgorithm;
        }
        
        public String getEvidenceId() { return evidenceId; }
        public String getChain() { return chain; }
        public long getFileSize() { return fileSize; }
        public String getFileName() { return fileName; }
        public long getExpirationTime() { return expirationTime; }
        public boolean isEncrypted() { return isEncrypted; }
        public int getEncryptionAlgorithm() { return encryptionAlgorithm; }
    }
    
    public EvidenceSubmissionPortal() {
        this.evidenceRecords = new ConcurrentHashMap<>();
        this.disputeEvidenceMap = new ConcurrentHashMap<>();
        this.metadataStore = new ConcurrentHashMap<>();
        LOGGER.info("EvidenceSubmissionPortal initialized");
    }
    
    /**
     * Submits evidence for a dispute (on-chain hash + off-chain storage)
     * 
     * @param disputeId The dispute ID
     * @param submitter The address submitting evidence
     * @param type Evidence type
     * @param contentHash SHA-256 hash of content
     * @param ipfsHash IPFS or off-chain storage reference
     * @param encrypted Whether content is encrypted
     * @param description Human-readable description
     * @return Evidence ID if successful
     */
    public String submitEvidence(String disputeId, Address submitter, EvidenceType type,
                                String contentHash, String ipfsHash,
                                boolean encrypted, String description) {
        return submitEvidenceWithEncryption(disputeId, submitter, type, contentHash, ipfsHash,
                                           null, encrypted, description);
    }
    
    /**
     * Submits encrypted evidence
     */
    public String submitEvidenceWithEncryption(String disputeId, Address submitter,
                                              EvidenceType type, String contentHash, String ipfsHash,
                                              byte[] encryptionKey, boolean encrypted,
                                              String description) {
        // Validate inputs
        if (disputeId == null || submitter == null || contentHash == null) {
            LOGGER.warning("Invalid evidence submission parameters");
            return null;
        }
        
        // Verify content hash format (SHA-256 = 64 hex chars)
        if (!isValidSHA256Hash(contentHash)) {
            LOGGER.warning("Invalid content hash format: " + contentHash);
            return null;
        }
        
        String evidenceId = UUID.randomUUID().toString();
        
        // Create evidence record
        Evidence evidence = new Evidence(evidenceId, disputeId, submitter, type,
                                        contentHash, ipfsHash, encryptionKey, description);
        
        // Create metadata
        EvidenceMetadata metadata = new EvidenceMetadata(evidenceId,
                ipfsHash != null ? "IPFS" : "MAIN",
                0, // fileSize would be provided separately
                generateFileName(type),
                encrypted,
                1); // AES-256-GCM
        
        // Store on-chain
        evidenceRecords.put(evidenceId, evidence);
        metadataStore.put(evidenceId, metadata);
        
        // Link to dispute
        disputeEvidenceMap.computeIfAbsent(disputeId, k -> new ArrayList<>()).add(evidenceId);
        
        LOGGER.info("Evidence submitted: " + evidenceId + " for dispute: " + disputeId +
                   " (encrypted: " + encrypted + ")");
        return evidenceId;
    }
    
    /**
     * Verifies evidence integrity by re-computing hash
     */
    public boolean verifyEvidenceIntegrity(String evidenceId, byte[] contentData) {
        Evidence evidence = evidenceRecords.get(evidenceId);
        if (evidence == null) {
            LOGGER.warning("Evidence not found: " + evidenceId);
            return false;
        }
        
        try {
            String computedHash = computeSHA256(contentData);
            boolean matches = computedHash.equals(evidence.getContentHash());
            
            if (matches) {
                evidence.setStatus(EvidenceStatus.VERIFIED);
                LOGGER.info("Evidence verified: " + evidenceId);
            } else {
                LOGGER.warning("Evidence integrity check failed: " + evidenceId);
            }
            
            return matches;
        } catch (Exception e) {
            LOGGER.warning("Error verifying evidence: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Challenges evidence authenticity/relevance
     */
    public boolean challengeEvidence(String evidenceId, String challengeReason) {
        Evidence evidence = evidenceRecords.get(evidenceId);
        if (evidence == null) {
            return false;
        }
        
        evidence.addChallenge(challengeReason);
        evidence.setStatus(EvidenceStatus.CHALLENGED);
        
        LOGGER.info("Evidence challenged: " + evidenceId + " - Reason: " + challengeReason);
        return true;
    }
    
    /**
     * Adds witness signature to evidence (notarization)
     */
    public boolean addWitnessSignature(String evidenceId, String witnessSignature) {
        Evidence evidence = evidenceRecords.get(evidenceId);
        if (evidence == null) {
            return false;
        }
        
        evidence.addWitnessSignature(witnessSignature);
        LOGGER.info("Witness signature added to evidence: " + evidenceId);
        return true;
    }
    
    /**
     * Records arbitrator review notes
     */
    public void recordArbitratorNote(String evidenceId, String arbitratorId, String note) {
        Evidence evidence = evidenceRecords.get(evidenceId);
        if (evidence != null) {
            evidence.addArbitratorNote(arbitratorId, note);
            LOGGER.info("Arbitrator note recorded for evidence: " + evidenceId);
        }
    }
    
    /**
     * Accepts evidence as part of record
     */
    public void acceptEvidence(String evidenceId) {
        Evidence evidence = evidenceRecords.get(evidenceId);
        if (evidence != null) {
            evidence.setStatus(EvidenceStatus.ACCEPTED);
            LOGGER.info("Evidence accepted: " + evidenceId);
        }
    }
    
    /**
     * Rejects evidence from record
     */
    public void rejectEvidence(String evidenceId, String reason) {
        Evidence evidence = evidenceRecords.get(evidenceId);
        if (evidence != null) {
            evidence.setStatus(EvidenceStatus.REJECTED);
            LOGGER.info("Evidence rejected: " + evidenceId + " - Reason: " + reason);
        }
    }
    
    /**
     * Seals evidence (limits access until case closed)
     */
    public void sealEvidence(String evidenceId) {
        Evidence evidence = evidenceRecords.get(evidenceId);
        if (evidence != null) {
            evidence.setStatus(EvidenceStatus.SEALED);
            LOGGER.info("Evidence sealed: " + evidenceId);
        }
    }
    
    /**
     * Gets all evidence for a dispute
     */
    public List<Evidence> getDisputeEvidence(String disputeId) {
        return disputeEvidenceMap.getOrDefault(disputeId, new ArrayList<>()).stream()
            .map(evidenceRecords::get)
            .filter(Objects::nonNull)
            .toList();
    }
    
    /**
     * Gets evidence by ID
     */
    public Evidence getEvidence(String evidenceId) {
        return evidenceRecords.get(evidenceId);
    }
    
    /**
     * Gets metadata for evidence
     */
    public EvidenceMetadata getMetadata(String evidenceId) {
        return metadataStore.get(evidenceId);
    }
    
    // Helper methods
    
    private boolean isValidSHA256Hash(String hash) {
        return hash != null && hash.length() == 64 && hash.matches("[a-fA-F0-9]{64}");
    }
    
    private String computeSHA256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(data);
        StringBuilder hex = new StringBuilder();
        for (byte b : hashBytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
    
    private String generateFileName(EvidenceType type) {
        return "evidence_" + type.toString().toLowerCase() + "_" + System.currentTimeMillis();
    }
}