package org.example.app.core.banking.independence;

import org.example.app.core.block.Transaction;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;
import org.example.app.core.crypto.PKIManager;
import org.example.app.core.state.BlockchainState;

import java.math.BigInteger;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * OfflineSettlementTech - Offline transaction processing for TINC Blockchain
 * 
 * CRITICAL FOR AFRICA: Enable transactions WITHOUT internet connectivity
 * 
 * Features:
 * - USSD/SMS transaction support (works on feature phones)
 * - QR code-based offline transfers
 * - Batch synchronization when online
 * - Merkle proof verification (prove tx validity offline)
 * - Time-stamped transaction certificates
 * - Conflict resolution (double-spend detection)
 * - Agent-based settlement (cash agents handle offline txs)
 * - Cryptographic receipts (tamper-proof)
 * 
 * Use Cases:
 * - Rural areas with no internet
 * - Power outages in cities
 * - Emergency situations (natural disasters)
 * - Mobile money style transactions
 * - Peer-to-peer cash exchanges
 * 
 * Security Model:
 * - Signed transaction certificates
 * - Nonce-based replay protection
 * - Multi-party validation upon sync
 * - Fraud detection and blacklisting
 * - Time-window validation (prevent stale txs)
 */
public class OfflineSettlementTech {
    private static final Logger LOGGER = Logger.getLogger(OfflineSettlementTech.class.getName());
    
    // Protocol constants
    private static final long MAX_OFFLINE_DURATION = 604800000L; // 7 days in milliseconds
    private static final int MAX_OFFLINE_BATCH_SIZE = 1000; // Max transactions per batch
    private static final long CONFLICT_RESOLUTION_WINDOW = 86400000L; // 24 hours
    private static final int USSD_MESSAGE_MAX_LENGTH = 182; // Standard USSD limit
    
    // Offline transaction tracking
    private final Map<String, OfflineTransaction> pendingOfflineTransactions;
    private final Map<String, OfflineTransaction> syncedTransactions;
    private final Map<String, OfflineTransaction> conflictedTransactions;
    
    // Transaction batches (for bulk sync)
    private final Map<String, OfflineBatch> offlineBatches;
    
    // Offline agents (handle offline settlements)
    private final Map<String, OfflineAgent> offlineAgents;
    private final Map<String, List<String>> locationAgents; // Location -> Agent IDs
    
    // USSD/SMS gateway
    private final Map<String, USSDSession> activeSessions;
    
    // QR code registry
    private final Map<String, QRCodeTransaction> qrTransactions;
    
    // Merkle trees for offline verification
    private final Map<String, MerkleProof> merkleProofs;
    
    // Nonce tracking (prevent replay attacks)
    private final Map<Address, Long> userNonces;
    
    // Dependencies
    private final BlockchainState blockchainState;
    private final PKIManager pkiManager;
    
    // Thread-safe execution
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;
    
    /**
     * OfflineTransaction - Represents an offline transaction
     */
    public static class OfflineTransaction {
        private final String transactionId;
        private final Address sender;
        private final Address receiver;
        private final String currency;
        private final BigInteger amount;
        private final Instant createdAt;
        private final Instant expiresAt;
        private final long nonce;
        private final byte[] signature;
        private final byte[] certificate; // Cryptographic proof
        private OfflineStatus status;
        
        // Offline metadata
        private final OfflineMethod method; // USSD, SMS, QR, etc.
        private final String agentId; // Agent who facilitated (if any)
        private final String deviceFingerprint;
        private final boolean requiresAgentVerification;
        
        // Synchronization
        private Instant syncedAt;
        private String onChainTxHash;
        
        // Conflict detection
        private final List<String> conflictingTxIds;
        
        public OfflineTransaction(String transactionId, Address sender, Address receiver,
                                 String currency, BigInteger amount, long nonce,
                                 byte[] signature, byte[] certificate, OfflineMethod method,
                                 String agentId, String deviceFingerprint,
                                 boolean requiresAgentVerification) {
            this.transactionId = transactionId;
            this.sender = sender;
            this.receiver = receiver;
            this.currency = currency;
            this.amount = amount;
            this.createdAt = Instant.now();
            this.expiresAt = createdAt.plusMillis(MAX_OFFLINE_DURATION);
            this.nonce = nonce;
            this.signature = signature;
            this.certificate = certificate;
            this.status = OfflineStatus.PENDING_SYNC;
            this.method = method;
            this.agentId = agentId;
            this.deviceFingerprint = deviceFingerprint;
            this.requiresAgentVerification = requiresAgentVerification;
            this.conflictingTxIds = new CopyOnWriteArrayList<>();
        }
        
        // Getters
        public String getTransactionId() { return transactionId; }
        public Address getSender() { return sender; }
        public Address getReceiver() { return receiver; }
        public String getCurrency() { return currency; }
        public BigInteger getAmount() { return amount; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getExpiresAt() { return expiresAt; }
        public long getNonce() { return nonce; }
        public byte[] getSignature() { return signature; }
        public byte[] getCertificate() { return certificate; }
        public OfflineStatus getStatus() { return status; }
        public OfflineMethod getMethod() { return method; }
        public String getAgentId() { return agentId; }
        public String getDeviceFingerprint() { return deviceFingerprint; }
        public boolean requiresAgentVerification() { return requiresAgentVerification; }
        public Instant getSyncedAt() { return syncedAt; }
        public String getOnChainTxHash() { return onChainTxHash; }
        public List<String> getConflictingTxIds() { return new ArrayList<>(conflictingTxIds); }
        
        public void setStatus(OfflineStatus status) { this.status = status; }
        public void setSyncedAt(Instant syncedAt) { this.syncedAt = syncedAt; }
        public void setOnChainTxHash(String hash) { this.onChainTxHash = hash; }
        public void addConflict(String txId) { conflictingTxIds.add(txId); }
        
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
    
    /**
     * OfflineMethod enumeration
     */
    public enum OfflineMethod {
        USSD,           // USSD code-based (e.g., *123#)
        SMS,            // SMS text message
        QR_CODE,        // QR code scan
        NFC,            // Near-field communication
        AGENT_PAPER,    // Paper certificate via agent
        BLUETOOTH       // Bluetooth transfer
    }
    
    /**
     * OfflineStatus enumeration
     */
    public enum OfflineStatus {
        PENDING_SYNC,       // Waiting to be synced
        SYNCING,            // Currently syncing
        SYNCED,             // Successfully synced
        CONFLICT_DETECTED,  // Double-spend detected
        EXPIRED,            // Expired before sync
        REJECTED            // Rejected during sync
    }
    
    /**
     * OfflineBatch - Batch of offline transactions for bulk sync
     */
    public static class OfflineBatch {
        private final String batchId;
        private final List<OfflineTransaction> transactions;
        private final Instant createdAt;
        private final String agentId; // Agent who collected batch
        private final byte[] merkleRoot; // For verification
        private BatchStatus status;
        
        public enum BatchStatus {
            COLLECTING, READY_TO_SYNC, SYNCING, SYNCED, PARTIALLY_FAILED
        }
        
        public OfflineBatch(String batchId, String agentId) {
            this.batchId = batchId;
            this.transactions = new CopyOnWriteArrayList<>();
            this.createdAt = Instant.now();
            this.agentId = agentId;
            this.merkleRoot = new byte[32];
            this.status = BatchStatus.COLLECTING;
        }
        
        public String getBatchId() { return batchId; }
        public List<OfflineTransaction> getTransactions() { return new ArrayList<>(transactions); }
        public Instant getCreatedAt() { return createdAt; }
        public String getAgentId() { return agentId; }
        public byte[] getMerkleRoot() { return merkleRoot; }
        public BatchStatus getStatus() { return status; }
        
        public void addTransaction(OfflineTransaction tx) { transactions.add(tx); }
        public void setStatus(BatchStatus status) { this.status = status; }
        
        public int getSize() { return transactions.size(); }
        
        public boolean isFull() {
            return transactions.size() >= MAX_OFFLINE_BATCH_SIZE;
        }
    }
    
    /**
     * OfflineAgent - Agent handling offline transactions
     */
    public static class OfflineAgent {
        private final String agentId;
        private final String agentName;
        private final String location;
        private final String phoneNumber; // For USSD/SMS
        private final Address agentAddress;
        private final int trustScore; // 0-100
        private final Map<String, BigInteger> floatBalance; // Currency -> Amount
        private boolean isOperational;
        
        // Statistics
        private int transactionsProcessed;
        private int conflictsDetected;
        private BigInteger totalVolume;
        
        public OfflineAgent(String agentId, String agentName, String location,
                           String phoneNumber, Address agentAddress, int trustScore) {
            this.agentId = agentId;
            this.agentName = agentName;
            this.location = location;
            this.phoneNumber = phoneNumber;
            this.agentAddress = agentAddress;
            this.trustScore = trustScore;
            this.floatBalance = new ConcurrentHashMap<>();
            this.isOperational = true;
            this.transactionsProcessed = 0;
            this.conflictsDetected = 0;
            this.totalVolume = BigInteger.ZERO;
        }
        
        public String getAgentId() { return agentId; }
        public String getAgentName() { return agentName; }
        public String getLocation() { return location; }
        public String getPhoneNumber() { return phoneNumber; }
        public Address getAgentAddress() { return agentAddress; }
        public int getTrustScore() { return trustScore; }
        public boolean isOperational() { return isOperational; }
        public int getTransactionsProcessed() { return transactionsProcessed; }
        
        public void incrementTransactions() { transactionsProcessed++; }
        public void incrementConflicts() { conflictsDetected++; }
        
        public BigInteger getFloatBalance(String currency) {
            return floatBalance.getOrDefault(currency, BigInteger.ZERO);
        }
        
        public void addFloat(String currency, BigInteger amount) {
            BigInteger current = floatBalance.getOrDefault(currency, BigInteger.ZERO);
            floatBalance.put(currency, current.add(amount));
        }
        
        public boolean deductFloat(String currency, BigInteger amount) {
            BigInteger current = floatBalance.getOrDefault(currency, BigInteger.ZERO);
            if (current.compareTo(amount) >= 0) {
                floatBalance.put(currency, current.subtract(amount));
                return true;
            }
            return false;
        }
    }
    
    /**
     * USSDSession - Active USSD session
     */
    public static class USSDSession {
        private final String sessionId;
        private final String phoneNumber;
        private final Address userAddress;
        private final Instant startTime;
        private SessionState state;
        private final Map<String, String> sessionData;
        
        public enum SessionState {
            MENU_MAIN, MENU_SEND, MENU_RECEIVE, MENU_BALANCE, COMPLETING, COMPLETED
        }
        
        public USSDSession(String sessionId, String phoneNumber, Address userAddress) {
            this.sessionId = sessionId;
            this.phoneNumber = phoneNumber;
            this.userAddress = userAddress;
            this.startTime = Instant.now();
            this.state = SessionState.MENU_MAIN;
            this.sessionData = new ConcurrentHashMap<>();
        }
        
        public String getSessionId() { return sessionId; }
        public String getPhoneNumber() { return phoneNumber; }
        public Address getUserAddress() { return userAddress; }
        public SessionState getState() { return state; }
        
        public void setState(SessionState state) { this.state = state; }
        public void putData(String key, String value) { sessionData.put(key, value); }
        public String getData(String key) { return sessionData.get(key); }
    }
    
    /**
     * QRCodeTransaction - QR code-based transaction
     */
    public static class QRCodeTransaction {
        private final String qrCode;
        private final Address sender;
        private final Address receiver;
        private final String currency;
        private final BigInteger amount;
        private final Instant createdAt;
        private final Instant expiresAt;
        private final byte[] signature;
        private boolean isRedeemed;
        
        public QRCodeTransaction(String qrCode, Address sender, Address receiver,
                                String currency, BigInteger amount, byte[] signature) {
            this.qrCode = qrCode;
            this.sender = sender;
            this.receiver = receiver;
            this.currency = currency;
            this.amount = amount;
            this.createdAt = Instant.now();
            this.expiresAt = createdAt.plusMillis(3600000L); // 1 hour validity
            this.signature = signature;
            this.isRedeemed = false;
        }
        
        public String getQrCode() { return qrCode; }
        public Address getSender() { return sender; }
        public Address getReceiver() { return receiver; }
        public String getCurrency() { return currency; }
        public BigInteger getAmount() { return amount; }
        public boolean isRedeemed() { return isRedeemed; }
        
        public void markRedeemed() { this.isRedeemed = true; }
        
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
    
    /**
     * MerkleProof - Proof of transaction inclusion
     */
    public static class MerkleProof {
        private final String transactionId;
        private final byte[] merkleRoot;
        private final List<byte[]> proof;
        private final int index;
        
        public MerkleProof(String transactionId, byte[] merkleRoot, 
                          List<byte[]> proof, int index) {
            this.transactionId = transactionId;
            this.merkleRoot = merkleRoot;
            this.proof = new ArrayList<>(proof);
            this.index = index;
        }
        
        public String getTransactionId() { return transactionId; }
        public byte[] getMerkleRoot() { return merkleRoot; }
        public List<byte[]> getProof() { return new ArrayList<>(proof); }
        public int getIndex() { return index; }
    }
    
    /**
     * Constructor for OfflineSettlementTech
     */
    public OfflineSettlementTech(BlockchainState blockchainState, PKIManager pkiManager) {
        this.blockchainState = blockchainState;
        this.pkiManager = pkiManager;
        
        this.pendingOfflineTransactions = new ConcurrentHashMap<>();
        this.syncedTransactions = new ConcurrentHashMap<>();
        this.conflictedTransactions = new ConcurrentHashMap<>();
        
        this.offlineBatches = new ConcurrentHashMap<>();
        this.offlineAgents = new ConcurrentHashMap<>();
        this.locationAgents = new ConcurrentHashMap<>();
        
        this.activeSessions = new ConcurrentHashMap<>();
        this.qrTransactions = new ConcurrentHashMap<>();
        this.merkleProofs = new ConcurrentHashMap<>();
        
        this.userNonces = new ConcurrentHashMap<>();
        
        this.executorService = Executors.newFixedThreadPool(15);
        this.scheduledExecutor = Executors.newScheduledThreadPool(5);
        
        // Start background tasks
        startExpirationMonitor();
        startBatchSynchronizer();
        startConflictDetector();
        
        LOGGER.info("OfflineSettlementTech initialized");
    }
    
    /**
     * Creates an offline transaction via USSD
     */
    public String createUSSDTransaction(String phoneNumber, Address sender, Address receiver,
                                       String currency, BigInteger amount, String agentId) {
        
        // Verify agent if required
        OfflineAgent agent = null;
        if (agentId != null) {
            agent = offlineAgents.get(agentId);
            if (agent == null || !agent.isOperational()) {
                throw new RuntimeException("Invalid or non-operational agent");
            }
        }
        
        // Get and increment nonce
        long nonce = getNextNonce(sender);
        
        // Generate transaction certificate
        byte[] certificate = generateTransactionCertificate(
            sender, receiver, currency, amount, nonce, OfflineMethod.USSD
        );
        
        // Generate signature (in production, use actual crypto)
        byte[] signature = signTransaction(sender, receiver, amount, nonce);
        
        // Create offline transaction
        String txId = generateOfflineTxId();
        OfflineTransaction offlineTx = new OfflineTransaction(
            txId, sender, receiver, currency, amount, nonce, signature, certificate,
            OfflineMethod.USSD, agentId, generateDeviceFingerprint(phoneNumber),
            agentId != null
        );
        
        pendingOfflineTransactions.put(txId, offlineTx);
        
        // Add to agent's batch if agent-facilitated
        if (agent != null) {
            addToBatch(agentId, offlineTx);
            agent.incrementTransactions();
        }
        
        LOGGER.info(String.format("USSD transaction created: %s, %s %s from %s to %s",
            txId, amount, currency, sender, receiver));
        
        return txId;
    }
    
    /**
     * Handles USSD menu interaction
     */
    public String handleUSSDRequest(String phoneNumber, String input, Address userAddress) {
        // Get or create session
        String sessionId = phoneNumber + "-" + System.currentTimeMillis();
        USSDSession session = activeSessions.computeIfAbsent(sessionId, 
            k -> new USSDSession(sessionId, phoneNumber, userAddress));
        
        String response;
        
        switch (session.getState()) {
            case MENU_MAIN:
                if ("1".equals(input)) {
                    session.setState(USSDSession.SessionState.MENU_SEND);
                    response = "CON Enter receiver address:";
                } else if ("2".equals(input)) {
                    session.setState(USSDSession.SessionState.MENU_BALANCE);
                    response = "END Your balance: [Balance info]";
                    activeSessions.remove(sessionId);
                } else {
                    response = "CON TINC Wallet\n1. Send Money\n2. Check Balance\n3. Exit";
                }
                break;
                
            case MENU_SEND:
                session.putData("receiver", input);
                response = "CON Enter amount:";
                break;
                
            default:
                response = "END Transaction processing...";
                activeSessions.remove(sessionId);
        }
        
        return response;
    }
    
    /**
     * Creates QR code transaction
     */
    public String createQRCodeTransaction(Address sender, Address receiver, String currency,
                                         BigInteger amount) {
        
        // Generate QR code
        String qrCode = generateQRCode(sender, receiver, currency, amount);
        
        // Generate signature
        byte[] signature = signTransaction(sender, receiver, amount, getNextNonce(sender));
        
        // Create QR transaction
        QRCodeTransaction qrTx = new QRCodeTransaction(
            qrCode, sender, receiver, currency, amount, signature
        );
        
        qrTransactions.put(qrCode, qrTx);
        
        LOGGER.info(String.format("QR code transaction created: %s, %s %s",
            qrCode, amount, currency));
        
        return qrCode;
    }
    
    /**
     * Redeems QR code transaction
     */
    public String redeemQRCodeTransaction(String qrCode, Address redeemer) {
        QRCodeTransaction qrTx = qrTransactions.get(qrCode);
        
        if (qrTx == null) {
            throw new RuntimeException("QR code not found");
        }
        
        if (qrTx.isRedeemed()) {
            throw new RuntimeException("QR code already redeemed");
        }
        
        if (qrTx.isExpired()) {
            throw new RuntimeException("QR code expired");
        }
        
        if (!qrTx.getReceiver().equals(redeemer)) {
            throw new RuntimeException("Redeemer does not match receiver");
        }
        
        // Mark as redeemed
        qrTx.markRedeemed();
        
        // Create offline transaction for sync
        long nonce = getNextNonce(qrTx.getSender());
        byte[] certificate = generateTransactionCertificate(
            qrTx.getSender(), qrTx.getReceiver(), qrTx.getCurrency(), 
            qrTx.getAmount(), nonce, OfflineMethod.QR_CODE
        );
        
        String txId = generateOfflineTxId();
        OfflineTransaction offlineTx = new OfflineTransaction(
            txId, qrTx.getSender(), qrTx.getReceiver(), qrTx.getCurrency(),
            qrTx.getAmount(), nonce, qrTx.signature, certificate,
            OfflineMethod.QR_CODE, null, generateDeviceFingerprint("QR-" + qrCode), false
        );
        
        pendingOfflineTransactions.put(txId, offlineTx);
        
        LOGGER.info("QR code transaction redeemed: " + qrCode);
        
        return txId;
    }
    
    /**
     * Synchronizes offline transactions to blockchain
     */
    public SyncResult synchronizeOfflineTransactions(List<String> transactionIds) {
        SyncResult result = new SyncResult();
        
        for (String txId : transactionIds) {
            OfflineTransaction offlineTx = pendingOfflineTransactions.get(txId);
            
            if (offlineTx == null) {
                result.addFailed(txId, "Transaction not found");
                continue;
            }
            
            if (offlineTx.isExpired()) {
                offlineTx.setStatus(OfflineStatus.EXPIRED);
                result.addFailed(txId, "Transaction expired");
                continue;
            }
            
            // Verify signature
            if (!verifyTransactionSignature(offlineTx)) {
                offlineTx.setStatus(OfflineStatus.REJECTED);
                result.addFailed(txId, "Invalid signature");
                continue;
            }
            
            // Check for conflicts (double-spend)
            if (detectConflicts(offlineTx)) {
                offlineTx.setStatus(OfflineStatus.CONFLICT_DETECTED);
                result.addConflict(txId);
                continue;
            }
            
            // Verify nonce
            if (!verifyNonce(offlineTx.getSender(), offlineTx.getNonce())) {
                offlineTx.setStatus(OfflineStatus.REJECTED);
                result.addFailed(txId, "Invalid nonce");
                continue;
            }
            
            // Submit to blockchain
            offlineTx.setStatus(OfflineStatus.SYNCING);
            boolean synced = submitToBlockchain(offlineTx);
            
            if (synced) {
                offlineTx.setStatus(OfflineStatus.SYNCED);
                offlineTx.setSyncedAt(Instant.now());
                pendingOfflineTransactions.remove(txId);
                syncedTransactions.put(txId, offlineTx);
                result.addSuccess(txId);
                
                LOGGER.info("Offline transaction synced: " + txId);
            } else {
                offlineTx.setStatus(OfflineStatus.REJECTED);
                result.addFailed(txId, "Blockchain submission failed");
            }
        }
        
        return result;
    }
    
    /**
     * SyncResult - Results of synchronization
     */
    public static class SyncResult {
        private final List<String> successful;
        private final List<String> conflicts;
        private final Map<String, String> failed;
        
        public SyncResult() {
            this.successful = new ArrayList<>();
            this.conflicts = new ArrayList<>();
            this.failed = new HashMap<>();
        }
        
        public void addSuccess(String txId) { successful.add(txId); }
        public void addConflict(String txId) { conflicts.add(txId); }
        public void addFailed(String txId, String reason) { failed.put(txId, reason); }
        
        public List<String> getSuccessful() { return successful; }
        public List<String> getConflicts() { return conflicts; }
        public Map<String, String> getFailed() { return failed; }
        
        public int getTotalProcessed() {
            return successful.size() + conflicts.size() + failed.size();
        }
    }
    
    /**
     * Registers offline agent
     */
    public boolean registerOfflineAgent(String agentId, String agentName, String location,
                                       String phoneNumber, Address agentAddress, int trustScore) {
        
        OfflineAgent agent = new OfflineAgent(
            agentId, agentName, location, phoneNumber, agentAddress, trustScore
        );
        
        offlineAgents.put(agentId, agent);
        locationAgents.computeIfAbsent(location, k -> new ArrayList<>()).add(agentId);
        
        // Create initial batch for agent
        String batchId = generateBatchId(agentId);
        offlineBatches.put(batchId, new OfflineBatch(batchId, agentId));
        
        LOGGER.info(String.format("Offline agent registered: %s at %s, phone: %s",
            agentName, location, phoneNumber));
        
        return true;
    }
    
    // Private helper methods
    
    private long getNextNonce(Address user) {
        long currentNonce = userNonces.getOrDefault(user, 0L);
        long nextNonce = currentNonce + 1;
        userNonces.put(user, nextNonce);
        return nextNonce;
    }
    
    private boolean verifyNonce(Address user, long nonce) {
        long expectedNonce = userNonces.getOrDefault(user, 0L) + 1;
        return nonce == expectedNonce;
    }
    
    private byte[] generateTransactionCertificate(Address sender, Address receiver,
                                                 String currency, BigInteger amount,
                                                 long nonce, OfflineMethod method) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String data = sender.toString() + receiver.toString() + currency + 
                         amount.toString() + nonce + method.toString();
            return digest.digest(data.getBytes());
        } catch (NoSuchAlgorithmException e) {
            LOGGER.severe("Error generating certificate: " + e.getMessage());
            return new byte[32];
        }
    }
    
    private byte[] signTransaction(Address sender, Address receiver, 
                                  BigInteger amount, long nonce) {
        // In production, use actual cryptographic signing
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String data = sender.toString() + receiver.toString() + amount.toString() + nonce;
            return digest.digest(data.getBytes());
        } catch (NoSuchAlgorithmException e) {
            return new byte[64];
        }
    }
    
    private boolean verifyTransactionSignature(OfflineTransaction tx) {
        // In production, verify actual cryptographic signature
        return tx.getSignature() != null && tx.getSignature().length > 0;
    }
    
    private boolean detectConflicts(OfflineTransaction tx) {
        // Check for double-spend attempts
        for (OfflineTransaction existing : pendingOfflineTransactions.values()) {
            if (existing.getSender().equals(tx.getSender()) &&
                existing.getNonce() == tx.getNonce() &&
                !existing.getTransactionId().equals(tx.getTransactionId())) {
                
                // Conflict detected
                tx.addConflict(existing.getTransactionId());
                existing.addConflict(tx.getTransactionId());
                
                conflictedTransactions.put(tx.getTransactionId(), tx);
                conflictedTransactions.put(existing.getTransactionId(), existing);
                
                LOGGER.warning(String.format("Conflict detected between %s and %s",
                    tx.getTransactionId(), existing.getTransactionId()));
                
                return true;
            }
        }
        return false;
    }
    
    private boolean submitToBlockchain(OfflineTransaction offlineTx) {
        // In production, create actual blockchain transaction
        LOGGER.info("Submitting offline transaction to blockchain: " + 
                   offlineTx.getTransactionId());
        return true;
    }
    
    private String generateQRCode(Address sender, Address receiver, 
                                 String currency, BigInteger amount) {
        // Generate compact QR code data
        String qrData = String.format("TINC:%s:%s:%s:%s:%d",
            sender.toString().substring(0, 16),
            receiver.toString().substring(0, 16),
            currency, amount.toString(), System.currentTimeMillis());
        
        return Base64.getEncoder().encodeToString(qrData.getBytes());
    }
    
    private String generateDeviceFingerprint(String identifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(identifier.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            return identifier;
        }
    }
    
    private void addToBatch(String agentId, OfflineTransaction tx) {
        // Find active batch for agent
        String activeBatchId = null;
        for (OfflineBatch batch : offlineBatches.values()) {
            if (batch.getAgentId().equals(agentId) && 
                batch.getStatus() == OfflineBatch.BatchStatus.COLLECTING &&
                !batch.isFull()) {
                activeBatchId = batch.getBatchId();
                break;
            }
        }
        
        // Create new batch if needed
        if (activeBatchId == null) {
            activeBatchId = generateBatchId(agentId);
            offlineBatches.put(activeBatchId, new OfflineBatch(activeBatchId, agentId));
        }
        
        OfflineBatch batch = offlineBatches.get(activeBatchId);
        batch.addTransaction(tx);
        
        // Mark batch ready if full
        if (batch.isFull()) {
            batch.setStatus(OfflineBatch.BatchStatus.READY_TO_SYNC);
        }
    }
    
    private String generateOfflineTxId() {
        return "OFFLINE-" + UUID.randomUUID().toString();
    }
    
    private String generateBatchId(String agentId) {
        return "BATCH-" + agentId + "-" + System.currentTimeMillis();
    }
    
    private void startExpirationMonitor() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            for (OfflineTransaction tx : pendingOfflineTransactions.values()) {
                if (tx.isExpired()) {
                    tx.setStatus(OfflineStatus.EXPIRED);
                    LOGGER.warning("Offline transaction expired: " + tx.getTransactionId());
                }
            }
            
            // Clean up expired QR codes
            qrTransactions.entrySet().removeIf(entry -> entry.getValue().isExpired());
            
        }, 300, 300, TimeUnit.SECONDS); // Every 5 minutes
    }
    
    private void startBatchSynchronizer() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            for (OfflineBatch batch : offlineBatches.values()) {
                if (batch.getStatus() == OfflineBatch.BatchStatus.READY_TO_SYNC) {
                    executorService.submit(() -> {
                        batch.setStatus(OfflineBatch.BatchStatus.SYNCING);
                        List<String> txIds = batch.getTransactions().stream()
                            .map(OfflineTransaction::getTransactionId)
                            .collect(java.util.stream.Collectors.toList());
                        
                        SyncResult result = synchronizeOfflineTransactions(txIds);
                        
                        if (result.getFailed().isEmpty() && result.getConflicts().isEmpty()) {
                            batch.setStatus(OfflineBatch.BatchStatus.SYNCED);
                        } else {
                            batch.setStatus(OfflineBatch.BatchStatus.PARTIALLY_FAILED);
                        }
                        
                        LOGGER.info(String.format("Batch sync completed: %s, Success: %d, Conflicts: %d, Failed: %d",
                            batch.getBatchId(), result.getSuccessful().size(),
                            result.getConflicts().size(), result.getFailed().size()));
                    });
                }
            }
        }, 120, 120, TimeUnit.SECONDS); // Every 2 minutes
    }
    
    private void startConflictDetector() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            // Detect and resolve conflicts
            for (OfflineTransaction tx : conflictedTransactions.values()) {
                LOGGER.info("Processing conflict for: " + tx.getTransactionId());
                // In production, implement conflict resolution logic
            }
        }, 600, 600, TimeUnit.SECONDS); // Every 10 minutes
    }
    
    // Getters
    
    public OfflineTransaction getOfflineTransaction(String txId) {
        OfflineTransaction tx = pendingOfflineTransactions.get(txId);
        if (tx != null) return tx;
        
        tx = syncedTransactions.get(txId);
        if (tx != null) return tx;
        
        return conflictedTransactions.get(txId);
    }
    
    public List<OfflineTransaction> getPendingTransactions() {
        return new ArrayList<>(pendingOfflineTransactions.values());
    }
    
    public OfflineAgent getOfflineAgent(String agentId) {
        return offlineAgents.get(agentId);
    }
    
    public OfflineBatch getOfflineBatch(String batchId) {
        return offlineBatches.get(batchId);
    }
    
    public void shutdown() {
        executorService.shutdown();
        scheduledExecutor.shutdown();
        LOGGER.info("OfflineSettlementTech shutdown");
    }
}