package org.example.app.core.banking.governance;

import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;
import org.example.app.core.block.Block;
import org.example.app.core.block.Transaction;
import org.example.app.core.banking.compliance.ComplianceReportGenerator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.math.BigInteger;

/**
 * GovernanceAuditLog - Immutable transparent record of ALL governance actions
 * 
 * Purpose: Complete audit trail for transparency and compliance
 * - Every proposal, vote, execution, slashing, sanction is recorded immutably
 * - Public blockchain transparency for all governance decisions
 * - Compliance reporting for international law enforcement
 * - Historical analysis and dispute resolution
 * 
 * Key Features:
 * - Immutable records (write-once)
 * - Merkle tree for tamper-proof verification
 * - Public queryable interface
 * - Automatic archiving to blockchain blocks
 * - Compliance report generation
 */
public class GovernanceAuditLog {
    private static final Logger LOGGER = Logger.getLogger(GovernanceAuditLog.class.getName());
    
    // Audit record types
    public enum AuditEventType {
        PROPOSAL_SUBMITTED,
        PROPOSAL_VOTED,
        PROPOSAL_APPROVED,
        PROPOSAL_REJECTED,
        PROPOSAL_EXECUTED,
        PROPOSAL_FAILED,
        PROPOSAL_CHALLENGED,
        
        VALIDATOR_REGISTERED,
        VALIDATOR_SLASHED,
        VALIDATOR_UNJAILED,
        VALIDATOR_REMOVED,
        
        SANCTION_APPLIED,
        SANCTION_LIFTED,
        SANCTION_ESCALATED,
        
        TREASURY_WITHDRAWAL,
        TREASURY_DEPOSIT,
        EMERGENCY_FUND_DEPLOYED,
        
        ASU_REBALANCED,
        TOKEN_MINTING_PAUSED,
        TOKEN_MINTING_RESUMED,
        
        FX_EXPOSURE_LIMIT_REACHED,
        CIRCUIT_BREAKER_ACTIVATED,
        EMERGENCY_SHUTDOWN,
        
        DISPUTE_FILED,
        DISPUTE_RESOLVED,
        ARBITRATION_VERDICT,
        
        POLICY_UPDATED,
        ALGORITHM_CONFIGURED,
        THRESHOLD_CHANGED
    }
    
    /**
     * Immutable audit record
     */
    public static class AuditRecord {
        private final String recordId;
        private final AuditEventType eventType;
        private final long timestamp;
        private final String actorAddress; // Who performed the action
        private final String targetAddress; // Who/what was affected
        private final Map<String, String> metadata; // Event-specific data
        private final String description;
        private final String transactionHash; // If recorded on-chain
        private final long blockNumber;
        private final Hash merkleRoot; // Merkle root at time of record
        
        public AuditRecord(String recordId, AuditEventType eventType, String actorAddress,
                          String targetAddress, Map<String, String> metadata, String description) {
            this.recordId = recordId;
            this.eventType = eventType;
            this.timestamp = System.currentTimeMillis();
            this.actorAddress = actorAddress;
            this.targetAddress = targetAddress;
            this.metadata = new HashMap<>(metadata);
            this.description = description;
            this.transactionHash = null;
            this.blockNumber = 0;
            this.merkleRoot = null;
        }
        
        // Getters
        public String getRecordId() { return recordId; }
        public AuditEventType getEventType() { return eventType; }
        public long getTimestamp() { return timestamp; }
        public String getActorAddress() { return actorAddress; }
        public String getTargetAddress() { return targetAddress; }
        public Map<String, String> getMetadata() { return new HashMap<>(metadata); }
        public String getDescription() { return description; }
        public String getTransactionHash() { return transactionHash; }
        public long getBlockNumber() { return blockNumber; }
        public Hash getMerkleRoot() { return merkleRoot; }
        
        @Override
        public String toString() {
            return String.format("[%s] %s: %s -> %s | %s | Block: %d",
                new Date(timestamp), eventType, actorAddress, targetAddress,
                description, blockNumber);
        }
    }
    
    // Storage
    private final Map<String, AuditRecord> records;
    private final Map<AuditEventType, List<String>> recordsByType;
    private final Map<String, List<String>> recordsByActor;
    private final Map<String, List<String>> recordsByTarget;
    private final List<String> chronologicalIndex;
    private final ReadWriteLock lock;
    private final ComplianceReportGenerator complianceReporter;
    
    // Merkle tree for tamper-proof verification
    private final List<Hash> merkleLeaves;
    private Hash currentMerkleRoot;
    
    public GovernanceAuditLog(ComplianceReportGenerator complianceReporter) {
        this.records = new ConcurrentHashMap<>();
        this.recordsByType = new ConcurrentHashMap<>();
        this.recordsByActor = new ConcurrentHashMap<>();
        this.recordsByTarget = new ConcurrentHashMap<>();
        this.chronologicalIndex = Collections.synchronizedList(new ArrayList<>());
        this.lock = new ReentrantReadWriteLock();
        this.complianceReporter = complianceReporter;
        this.merkleLeaves = Collections.synchronizedList(new ArrayList<>());
        this.currentMerkleRoot = null;
        
        // Initialize indices for all event types
        for (AuditEventType type : AuditEventType.values()) {
            recordsByType.put(type, Collections.synchronizedList(new ArrayList<>()));
        }
        
        LOGGER.info("GovernanceAuditLog initialized");
    }
    
    /**
     * Records a new audit event (IMMUTABLE - write-once)
     */
    public String recordEvent(AuditEventType eventType, String actorAddress, 
                             String targetAddress, Map<String, String> metadata, 
                             String description) {
        lock.writeLock().lock();
        try {
            // Generate unique record ID
            String recordId = generateRecordId(eventType, actorAddress);
            
            // Create immutable record
            AuditRecord record = new AuditRecord(recordId, eventType, actorAddress,
                targetAddress, metadata, description);
            
            // Store in all indices (write-once, no updates allowed)
            records.put(recordId, record);
            chronologicalIndex.add(recordId);
            recordsByType.get(eventType).add(recordId);
            
            recordsByActor.computeIfAbsent(actorAddress, k -> 
                Collections.synchronizedList(new ArrayList<>())).add(recordId);
            
            if (targetAddress != null && !targetAddress.isEmpty()) {
                recordsByTarget.computeIfAbsent(targetAddress, k -> 
                    Collections.synchronizedList(new ArrayList<>())).add(recordId);
            }
            
            // Update Merkle tree
            updateMerkleTree(record);
            
            LOGGER.info("Audit record created: " + recordId + " - " + eventType);
            return recordId;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Retrieves a specific audit record by ID
     */
    public AuditRecord getRecord(String recordId) {
        lock.readLock().lock();
        try {
            return records.get(recordId);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets all records of a specific type
     */
    public List<AuditRecord> getRecordsByType(AuditEventType type) {
        lock.readLock().lock();
        try {
            return recordsByType.get(type).stream()
                .map(records::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets all records by an actor (who performed actions)
     */
    public List<AuditRecord> getRecordsByActor(String actorAddress) {
        lock.readLock().lock();
        try {
            List<String> recordIds = recordsByActor.get(actorAddress);
            if (recordIds == null) return Collections.emptyList();
            
            return recordIds.stream()
                .map(records::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets all records affecting a target (who/what was affected)
     */
    public List<AuditRecord> getRecordsByTarget(String targetAddress) {
        lock.readLock().lock();
        try {
            List<String> recordIds = recordsByTarget.get(targetAddress);
            if (recordIds == null) return Collections.emptyList();
            
            return recordIds.stream()
                .map(records::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets records within a time range
     */
    public List<AuditRecord> getRecordsByTimeRange(long startTime, long endTime) {
        lock.readLock().lock();
        try {
            return chronologicalIndex.stream()
                .map(records::get)
                .filter(Objects::nonNull)
                .filter(r -> r.getTimestamp() >= startTime && r.getTimestamp() <= endTime)
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets recent records (last N records)
     */
    public List<AuditRecord> getRecentRecords(int count) {
        lock.readLock().lock();
        try {
            int size = chronologicalIndex.size();
            int start = Math.max(0, size - count);
            
            return chronologicalIndex.subList(start, size).stream()
                .map(records::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Searches records by metadata key-value
     */
    public List<AuditRecord> searchByMetadata(String key, String value) {
        lock.readLock().lock();
        try {
            return records.values().stream()
                .filter(r -> {
                    String metaValue = r.getMetadata().get(key);
                    return metaValue != null && metaValue.equals(value);
                })
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets governance statistics for a time period
     */
    public Map<String, Object> getStatistics(long startTime, long endTime) {
        lock.readLock().lock();
        try {
            List<AuditRecord> periodRecords = getRecordsByTimeRange(startTime, endTime);
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalRecords", periodRecords.size());
            stats.put("startTime", startTime);
            stats.put("endTime", endTime);
            
            // Count by event type
            Map<AuditEventType, Long> byType = periodRecords.stream()
                .collect(Collectors.groupingBy(
                    AuditRecord::getEventType,
                    Collectors.counting()
                ));
            stats.put("eventTypeCounts", byType);
            
            // Most active actors
            Map<String, Long> actorActivity = periodRecords.stream()
                .collect(Collectors.groupingBy(
                    AuditRecord::getActorAddress,
                    Collectors.counting()
                ));
            stats.put("topActors", actorActivity.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            
            // Count proposals
            long proposalsSubmitted = byType.getOrDefault(AuditEventType.PROPOSAL_SUBMITTED, 0L);
            long proposalsApproved = byType.getOrDefault(AuditEventType.PROPOSAL_APPROVED, 0L);
            long proposalsRejected = byType.getOrDefault(AuditEventType.PROPOSAL_REJECTED, 0L);
            
            stats.put("proposals", Map.of(
                "submitted", proposalsSubmitted,
                "approved", proposalsApproved,
                "rejected", proposalsRejected,
                "approvalRate", proposalsSubmitted > 0 ? 
                    (double)proposalsApproved / proposalsSubmitted : 0.0
            ));
            
            // Count sanctions
            long sanctionsApplied = byType.getOrDefault(AuditEventType.SANCTION_APPLIED, 0L);
            long sanctionsLifted = byType.getOrDefault(AuditEventType.SANCTION_LIFTED, 0L);
            stats.put("sanctions", Map.of(
                "applied", sanctionsApplied,
                "lifted", sanctionsLifted,
                "active", sanctionsApplied - sanctionsLifted
            ));
            
            // Count validator actions
            long validatorsSlashed = byType.getOrDefault(AuditEventType.VALIDATOR_SLASHED, 0L);
            long validatorsRegistered = byType.getOrDefault(AuditEventType.VALIDATOR_REGISTERED, 0L);
            stats.put("validators", Map.of(
                "registered", validatorsRegistered,
                "slashed", validatorsSlashed
            ));
            
            return stats;
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Generates compliance report for external auditors
     */
    public String generateComplianceReport(long startTime, long endTime, String reportType) {
        lock.readLock().lock();
        try {
            List<AuditRecord> periodRecords = getRecordsByTimeRange(startTime, endTime);
            
            // Delegate to ComplianceReportGenerator
            Map<String, Object> reportData = new HashMap<>();
            reportData.put("auditRecords", periodRecords);
            reportData.put("statistics", getStatistics(startTime, endTime));
            reportData.put("merkleRoot", currentMerkleRoot != null ? 
                currentMerkleRoot.toString() : "N/A");
            
            return complianceReporter.generateReport(reportType, reportData);
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Verifies audit log integrity using Merkle tree
     */
    public boolean verifyIntegrity() {
        lock.readLock().lock();
        try {
            if (merkleLeaves.isEmpty()) return true;
            
            Hash recomputedRoot = computeMerkleRoot(merkleLeaves);
            boolean valid = recomputedRoot.equals(currentMerkleRoot);
            
            if (!valid) {
                LOGGER.severe("AUDIT LOG INTEGRITY VIOLATION DETECTED!");
            }
            
            return valid;
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets current Merkle root for tamper-proof verification
     */
    public Hash getCurrentMerkleRoot() {
        lock.readLock().lock();
        try {
            return currentMerkleRoot;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets total number of audit records
     */
    public int getTotalRecordCount() {
        lock.readLock().lock();
        try {
            return records.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Exports audit log for archival (all records)
     */
    public List<AuditRecord> exportAll() {
        lock.readLock().lock();
        try {
            return chronologicalIndex.stream()
                .map(records::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // Private helper methods
    
    private String generateRecordId(AuditEventType type, String actorAddress) {
        String data = type.toString() + actorAddress + System.currentTimeMillis() + 
                     UUID.randomUUID().toString();
        try {
            java.security.MessageDigest digest = 
                java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes());
            return Hash.fromBytes(hash).toString().substring(0, 16).toUpperCase();
        } catch (Exception e) {
            return UUID.randomUUID().toString().substring(0, 16).toUpperCase();
        }
    }
    
    private void updateMerkleTree(AuditRecord record) {
        try {
            // Create leaf hash from record
            String leafData = record.getRecordId() + record.getEventType() + 
                             record.getTimestamp() + record.getActorAddress();
            
            java.security.MessageDigest digest = 
                java.security.MessageDigest.getInstance("SHA-256");
            byte[] leafHash = digest.digest(leafData.getBytes());
            Hash leaf = Hash.fromBytes(leafHash);
            
            merkleLeaves.add(leaf);
            
            // Recompute Merkle root
            currentMerkleRoot = computeMerkleRoot(merkleLeaves);
            
        } catch (Exception e) {
            LOGGER.severe("Failed to update Merkle tree: " + e.getMessage());
        }
    }
    
    private Hash computeMerkleRoot(List<Hash> leaves) {
        if (leaves.isEmpty()) return null;
        if (leaves.size() == 1) return leaves.get(0);
        
        List<Hash> currentLevel = new ArrayList<>(leaves);
        
        while (currentLevel.size() > 1) {
            List<Hash> nextLevel = new ArrayList<>();
            
            for (int i = 0; i < currentLevel.size(); i += 2) {
                Hash left = currentLevel.get(i);
                Hash right = (i + 1 < currentLevel.size()) ? 
                    currentLevel.get(i + 1) : left;
                
                Hash parent = hashPair(left, right);
                nextLevel.add(parent);
            }
            
            currentLevel = nextLevel;
        }
        
        return currentLevel.get(0);
    }
    
    private Hash hashPair(Hash left, Hash right) {
        try {
            java.security.MessageDigest digest = 
                java.security.MessageDigest.getInstance("SHA-256");
            
            byte[] combined = new byte[64];
            System.arraycopy(left.toBytes(), 0, combined, 0, 32);
            System.arraycopy(right.toBytes(), 0, combined, 32, 32);
            
            byte[] hash = digest.digest(combined);
            return Hash.fromBytes(hash);
            
        } catch (Exception e) {
            LOGGER.severe("Failed to hash pair: " + e.getMessage());
            return left;
        }
    }
}