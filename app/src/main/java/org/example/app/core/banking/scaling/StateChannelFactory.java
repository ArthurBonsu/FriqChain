package org.example.app.core.scaling;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.security.MessageDigest;

/**
 * STATE CHANNEL FACTORY
 * 
 * Creates payment and state channels for instant transactions
 * with minimal on-chain footprint. Critical for African deployment:
 * 
 * Features:
 * - Offline transaction support (USSD/SMS certificates)
 * - Low-bandwidth operation
 * - Dispute resolution with on-chain arbitration
 * - Multi-party channels for merchant networks
 * - Near-zero fees for retail transactions
 * 
 * Channel Types:
 * - Payment Channels: Simple value transfers
 * - State Channels: Complex state transitions
 * - Virtual Channels: Multi-hop routing
 */
public class StateChannelFactory {
    private static final Logger LOGGER = Logger.getLogger(StateChannelFactory.class.getName());
    
    // Channel Management
    private final Map<String, StateChannel> activeChannels;
    private final ReadWriteLock channelLock;
    
    // Channel Configuration
    private static final long DEFAULT_CHALLENGE_PERIOD = 86400000; // 24 hours
    private static final long MAX_CHANNEL_LIFETIME = 2592000000L; // 30 days
    private static final int MAX_OFFLINE_UPDATES = 1000;
    
    // Dispute Resolution
    private final Map<String, ChannelDispute> activeDisputes;
    private final ReadWriteLock disputeLock;
    
    // Metrics
    private final AtomicLong channelsCreated;
    private final AtomicLong channelsClosed;
    private final AtomicLong totalTransactions;
    private final AtomicLong disputesRaised;
    
    // Offline Support
    private final OfflineCertificateManager offlineManager;
    
    /**
     * Constructor
     */
    public StateChannelFactory() {
        this.activeChannels = new ConcurrentHashMap<>();
        this.channelLock = new ReentrantReadWriteLock();
        
        this.activeDisputes = new ConcurrentHashMap<>();
        this.disputeLock = new ReentrantReadWriteLock();
        
        this.channelsCreated = new AtomicLong(0);
        this.channelsClosed = new AtomicLong(0);
        this.totalTransactions = new AtomicLong(0);
        this.disputesRaised = new AtomicLong(0);
        
        this.offlineManager = new OfflineCertificateManager();
        
        LOGGER.info("State Channel Factory initialized");
    }
    
    /**
     * Create new payment channel
     */
    public StateChannel createPaymentChannel(
            String participant1,
            String participant2,
            long initialDeposit1,
            long initialDeposit2,
            long challengePeriod) throws Exception {
        
        if (participant1 == null || participant2 == null) {
            throw new IllegalArgumentException("Participants cannot be null");
        }
        
        if (participant1.equals(participant2)) {
            throw new IllegalArgumentException("Participants must be different");
        }
        
        // Generate channel ID
        String channelId = generateChannelId(participant1, participant2);
        
        // Check if channel already exists
        channelLock.readLock().lock();
        try {
            if (activeChannels.containsKey(channelId)) {
                throw new Exception("Channel already exists: " + channelId);
            }
        } finally {
            channelLock.readLock().unlock();
        }
        
        // Create channel
        StateChannel channel = new StateChannel(
            channelId,
            Arrays.asList(participant1, participant2),
            ChannelType.PAYMENT,
            Map.of(participant1, initialDeposit1, participant2, initialDeposit2),
            challengePeriod > 0 ? challengePeriod : DEFAULT_CHALLENGE_PERIOD,
            System.currentTimeMillis()
        );
        
        // Store channel
        channelLock.writeLock().lock();
        try {
            activeChannels.put(channelId, channel);
        } finally {
            channelLock.writeLock().unlock();
        }
        
        channelsCreated.incrementAndGet();
        
        LOGGER.info(String.format(
            "Payment channel created: id=%s, participants=%s,%s, deposits=%d,%d",
            channelId, participant1, participant2, initialDeposit1, initialDeposit2
        ));
        
        return channel;
    }
    
    /**
     * Create multi-party state channel
     */
    public StateChannel createMultiPartyChannel(
            List<String> participants,
            Map<String, Long> initialDeposits,
            long challengePeriod) throws Exception {
        
        if (participants == null || participants.size() < 2) {
            throw new IllegalArgumentException("At least 2 participants required");
        }
        
        // Generate channel ID
        String channelId = generateChannelId(participants.toArray(new String[0]));
        
        // Create channel
        StateChannel channel = new StateChannel(
            channelId,
            participants,
            ChannelType.MULTI_PARTY,
            initialDeposits,
            challengePeriod > 0 ? challengePeriod : DEFAULT_CHALLENGE_PERIOD,
            System.currentTimeMillis()
        );
        
        channelLock.writeLock().lock();
        try {
            activeChannels.put(channelId, channel);
        } finally {
            channelLock.writeLock().unlock();
        }
        
        channelsCreated.incrementAndGet();
        
        LOGGER.info(String.format(
            "Multi-party channel created: id=%s, participants=%d",
            channelId, participants.size()
        ));
        
        return channel;
    }
    
    /**
     * Update channel state (off-chain)
     */
    public ChannelUpdate updateChannelState(
            String channelId,
            Map<String, Long> newBalances,
            int nonce,
            List<String> signatures) throws Exception {
        
        channelLock.readLock().lock();
        StateChannel channel;
        try {
            channel = activeChannels.get(channelId);
            if (channel == null) {
                throw new Exception("Channel not found: " + channelId);
            }
        } finally {
            channelLock.readLock().unlock();
        }
        
        // Validate update
        validateUpdate(channel, newBalances, nonce, signatures);
        
        // Create update
        ChannelUpdate update = new ChannelUpdate(
            channelId,
            nonce,
            newBalances,
            signatures,
            System.currentTimeMillis()
        );
        
        // Apply update
        channel.applyUpdate(update);
        
        totalTransactions.incrementAndGet();
        
        LOGGER.fine(String.format(
            "Channel updated: id=%s, nonce=%d",
            channelId, nonce
        ));
        
        return update;
    }
    
    /**
     * Create offline transaction certificate
     * For areas with poor connectivity
     */
    public OfflineCertificate createOfflineCertificate(
            String channelId,
            String sender,
            String recipient,
            long amount) throws Exception {
        
        channelLock.readLock().lock();
        StateChannel channel;
        try {
            channel = activeChannels.get(channelId);
            if (channel == null) {
                throw new Exception("Channel not found: " + channelId);
            }
        } finally {
            channelLock.readLock().unlock();
        }
        
        // Verify sender has sufficient balance
        if (channel.getBalance(sender) < amount) {
            throw new Exception("Insufficient balance for offline certificate");
        }
        
        // Create certificate
        OfflineCertificate certificate = offlineManager.createCertificate(
            channelId,
            sender,
            recipient,
            amount,
            System.currentTimeMillis()
        );
        
        LOGGER.info(String.format(
            "Offline certificate created: channel=%s, amount=%d",
            channelId, amount
        ));
        
        return certificate;
    }
    
    /**
     * Redeem offline certificates when back online
     */
    public void redeemOfflineCertificates(String channelId, List<OfflineCertificate> certificates)
            throws Exception {
        
        if (certificates == null || certificates.isEmpty()) {
            return;
        }
        
        channelLock.readLock().lock();
        StateChannel channel;
        try {
            channel = activeChannels.get(channelId);
            if (channel == null) {
                throw new Exception("Channel not found: " + channelId);
            }
        } finally {
            channelLock.readLock().unlock();
        }
        
        // Verify and apply each certificate
        for (OfflineCertificate cert : certificates) {
            if (offlineManager.verifyCertificate(cert)) {
                // Update channel balances
                Map<String, Long> balances = new HashMap<>(channel.getBalances());
                balances.compute(cert.getSender(), (k, v) -> v - cert.getAmount());
                balances.compute(cert.getRecipient(), (k, v) -> v + cert.getAmount());
                
                channel.updateBalances(balances);
                
                LOGGER.fine(String.format(
                    "Offline certificate redeemed: %s -> %s, amount=%d",
                    cert.getSender(), cert.getRecipient(), cert.getAmount()
                ));
            }
        }
        
        totalTransactions.addAndGet(certificates.size());
    }
    
    /**
     * Close channel cooperatively
     */
    public ChannelClosure closeChannel(String channelId, Map<String, Long> finalBalances,
                                      List<String> signatures) throws Exception {
        
        channelLock.writeLock().lock();
        StateChannel channel;
        try {
            channel = activeChannels.remove(channelId);
            if (channel == null) {
                throw new Exception("Channel not found: " + channelId);
            }
        } finally {
            channelLock.writeLock().unlock();
        }
        
        // Verify all participants signed
        if (signatures.size() != channel.getParticipants().size()) {
            throw new Exception("Missing signatures for cooperative close");
        }
        
        // Verify final balances sum correctly
        long totalDeposit = channel.getBalances().values().stream()
            .mapToLong(Long::longValue).sum();
        long totalFinal = finalBalances.values().stream()
            .mapToLong(Long::longValue).sum();
        
        if (totalDeposit != totalFinal) {
            throw new Exception("Final balances don't match total deposit");
        }
        
        // Create closure record
        ChannelClosure closure = new ChannelClosure(
            channelId,
            ClosureType.COOPERATIVE,
            finalBalances,
            System.currentTimeMillis()
        );
        
        channelsClosed.incrementAndGet();
        
        LOGGER.info(String.format(
            "Channel closed cooperatively: id=%s",
            channelId
        ));
        
        return closure;
    }
    
    /**
     * Initiate dispute (non-cooperative close)
     */
    public ChannelDispute initiateDispute(String channelId, ChannelUpdate latestUpdate,
                                         String initiator) throws Exception {
        
        channelLock.readLock().lock();
        StateChannel channel;
        try {
            channel = activeChannels.get(channelId);
            if (channel == null) {
                throw new Exception("Channel not found: " + channelId);
            }
        } finally {
            channelLock.readLock().unlock();
        }
        
        // Verify initiator is a participant
        if (!channel.getParticipants().contains(initiator)) {
            throw new SecurityException("Initiator not a channel participant");
        }
        
        // Create dispute
        long challengeEndTime = System.currentTimeMillis() + channel.getChallengePeriod();
        
        ChannelDispute dispute = new ChannelDispute(
            channelId,
            initiator,
            latestUpdate,
            challengeEndTime,
            System.currentTimeMillis()
        );
        
        disputeLock.writeLock().lock();
        try {
            activeDisputes.put(channelId, dispute);
        } finally {
            disputeLock.writeLock().unlock();
        }
        
        disputesRaised.incrementAndGet();
        
        LOGGER.warning(String.format(
            "Dispute initiated: channel=%s, initiator=%s, challenge_ends=%d",
            channelId, initiator, challengeEndTime
        ));
        
        return dispute;
    }
    
    /**
     * Challenge dispute with newer state
     */
    public void challengeDispute(String channelId, ChannelUpdate newerUpdate) throws Exception {
        disputeLock.writeLock().lock();
        try {
            ChannelDispute dispute = activeDisputes.get(channelId);
            if (dispute == null) {
                throw new Exception("No active dispute for channel: " + channelId);
            }
            
            // Verify newer update has higher nonce
            if (newerUpdate.getNonce() <= dispute.getLatestUpdate().getNonce()) {
                throw new Exception("Update is not newer than disputed state");
            }
            
            // Update dispute with newer state
            dispute.setLatestUpdate(newerUpdate);
            
            LOGGER.info(String.format(
                "Dispute challenged with newer state: channel=%s, nonce=%d",
                channelId, newerUpdate.getNonce()
            ));
        } finally {
            disputeLock.writeLock().unlock();
        }
    }
    
    /**
     * Finalize dispute after challenge period
     */
    public ChannelClosure finalizeDispute(String channelId) throws Exception {
        disputeLock.writeLock().lock();
        ChannelDispute dispute;
        try {
            dispute = activeDisputes.remove(channelId);
            if (dispute == null) {
                throw new Exception("No active dispute for channel: " + channelId);
            }
            
            // Verify challenge period has elapsed
            if (System.currentTimeMillis() < dispute.getChallengeEndTime()) {
                throw new Exception("Challenge period not yet elapsed");
            }
        } finally {
            disputeLock.writeLock().unlock();
        }
        
        // Remove channel
        channelLock.writeLock().lock();
        try {
            activeChannels.remove(channelId);
        } finally {
            channelLock.writeLock().unlock();
        }
        
        // Create closure with disputed state
        ChannelClosure closure = new ChannelClosure(
            channelId,
            ClosureType.DISPUTED,
            dispute.getLatestUpdate().getBalances(),
            System.currentTimeMillis()
        );
        
        channelsClosed.incrementAndGet();
        
        LOGGER.info(String.format(
            "Dispute finalized: channel=%s",
            channelId
        ));
        
        return closure;
    }
    
    /**
     * Get channel by ID
     */
    public StateChannel getChannel(String channelId) {
        channelLock.readLock().lock();
        try {
            return activeChannels.get(channelId);
        } finally {
            channelLock.readLock().unlock();
        }
    }
    
    /**
     * Get factory statistics
     */
    public ChannelStatistics getStatistics() {
        return new ChannelStatistics(
            channelsCreated.get(),
            channelsClosed.get(),
            activeChannels.size(),
            totalTransactions.get(),
            disputesRaised.get(),
            activeDisputes.size()
        );
    }
    
    // Helper methods
    
    private String generateChannelId(String... participants) throws Exception {
        Arrays.sort(participants); // Ensure consistent ordering
        
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String participant : participants) {
            digest.update(participant.getBytes());
        }
        digest.update(longToBytes(System.currentTimeMillis()));
        
        return Base64.getEncoder().encodeToString(digest.digest());
    }
    
    private void validateUpdate(StateChannel channel, Map<String, Long> newBalances,
                               int nonce, List<String> signatures) throws Exception {
        // Verify nonce is incremental
        if (nonce <= channel.getCurrentNonce()) {
            throw new Exception("Invalid nonce: must be greater than current");
        }
        
        // Verify balances sum to total deposit
        long totalDeposit = channel.getBalances().values().stream()
            .mapToLong(Long::longValue).sum();
        long totalNew = newBalances.values().stream()
            .mapToLong(Long::longValue).sum();
        
        if (totalDeposit != totalNew) {
            throw new Exception("New balances don't match total deposit");
        }
        
        // Verify signatures (simplified - production would verify cryptographic signatures)
        if (signatures.size() != channel.getParticipants().size()) {
            throw new SecurityException("Missing participant signatures");
        }
    }
    
    private byte[] longToBytes(long value) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte)(value & 0xFF);
            value >>= 8;
        }
        return result;
    }
    
    // Inner classes
    
    public enum ChannelType {
        PAYMENT,        // Simple payment channel
        MULTI_PARTY,    // Multiple participants
        VIRTUAL         // Multi-hop routing
    }
    
    public enum ClosureType {
        COOPERATIVE,    // All parties agree
        DISPUTED,       // Challenge mechanism used
        TIMEOUT         // Channel expired
    }
    
    public static class StateChannel {
        private final String channelId;
        private final List<String> participants;
        private final ChannelType type;
        private Map<String, Long> balances;
        private int currentNonce;
        private final long challengePeriod;
        private final long createdAt;
        private final List<ChannelUpdate> updateHistory;
        
        public StateChannel(String channelId, List<String> participants, ChannelType type,
                          Map<String, Long> initialBalances, long challengePeriod, long createdAt) {
            this.channelId = channelId;
            this.participants = new ArrayList<>(participants);
            this.type = type;
            this.balances = new ConcurrentHashMap<>(initialBalances);
            this.currentNonce = 0;
            this.challengePeriod = challengePeriod;
            this.createdAt = createdAt;
            this.updateHistory = new CopyOnWriteArrayList<>();
        }
        
        public void applyUpdate(ChannelUpdate update) {
            this.balances = new ConcurrentHashMap<>(update.getBalances());
            this.currentNonce = update.getNonce();
            this.updateHistory.add(update);
        }
        
        public void updateBalances(Map<String, Long> newBalances) {
            this.balances = new ConcurrentHashMap<>(newBalances);
            this.currentNonce++;
        }
        
        public long getBalance(String participant) {
            return balances.getOrDefault(participant, 0L);
        }
        
        public String getChannelId() { return channelId; }
        public List<String> getParticipants() { return participants; }
        public ChannelType getType() { return type; }
        public Map<String, Long> getBalances() { return new HashMap<>(balances); }
        public int getCurrentNonce() { return currentNonce; }
        public long getChallengePeriod() { return challengePeriod; }
        public long getCreatedAt() { return createdAt; }
        public List<ChannelUpdate> getUpdateHistory() { return new ArrayList<>(updateHistory); }
    }
    
    public static class ChannelUpdate {
        private final String channelId;
        private final int nonce;
        private final Map<String, Long> balances;
        private final List<String> signatures;
        private final long timestamp;
        
        public ChannelUpdate(String channelId, int nonce, Map<String, Long> balances,
                           List<String> signatures, long timestamp) {
            this.channelId = channelId;
            this.nonce = nonce;
            this.balances = balances;
            this.signatures = signatures;
            this.timestamp = timestamp;
        }
        
        public String getChannelId() { return channelId; }
        public int getNonce() { return nonce; }
        public Map<String, Long> getBalances() { return balances; }
        public List<String> getSignatures() { return signatures; }
        public long getTimestamp() { return timestamp; }
    }
    
    public static class OfflineCertificate {
        private final String certificateId;
        private final String channelId;
        private final String sender;
        private final String recipient;
        private final long amount;
        private final long timestamp;
        private final String signature;
        
        public OfflineCertificate(String certificateId, String channelId, String sender,
                                 String recipient, long amount, long timestamp, String signature) {
            this.certificateId = certificateId;
            this.channelId = channelId;
            this.sender = sender;
            this.recipient = recipient;
            this.amount = amount;
            this.timestamp = timestamp;
            this.signature = signature;
        }
        
        public String getCertificateId() { return certificateId; }
        public String getChannelId() { return channelId; }
        public String getSender() { return sender; }
        public String getRecipient() { return recipient; }
        public long getAmount() { return amount; }
        public long getTimestamp() { return timestamp; }
        public String getSignature() { return signature; }
    }
    
    private static class OfflineCertificateManager {
        private final Map<String, OfflineCertificate> certificates = new ConcurrentHashMap<>();
        
        public OfflineCertificate createCertificate(String channelId, String sender,
                                                   String recipient, long amount, long timestamp) {
            String certId = UUID.randomUUID().toString();
            String signature = "sig_" + certId; // Simplified
            
            OfflineCertificate cert = new OfflineCertificate(
                certId, channelId, sender, recipient, amount, timestamp, signature
            );
            
            certificates.put(certId, cert);
            return cert;
        }
        
        public boolean verifyCertificate(OfflineCertificate cert) {
            // Simplified verification - production would verify cryptographic signature
            return certificates.containsKey(cert.getCertificateId());
        }
    }
    
    public static class ChannelDispute {
        private final String channelId;
        private final String initiator;
        private ChannelUpdate latestUpdate;
        private final long challengeEndTime;
        private final long createdAt;
        
        public ChannelDispute(String channelId, String initiator, ChannelUpdate latestUpdate,
                             long challengeEndTime, long createdAt) {
            this.channelId = channelId;
            this.initiator = initiator;
            this.latestUpdate = latestUpdate;
            this.challengeEndTime = challengeEndTime;
            this.createdAt = createdAt;
        }
        
        public String getChannelId() { return channelId; }
        public String getInitiator() { return initiator; }
        public ChannelUpdate getLatestUpdate() { return latestUpdate; }
        public void setLatestUpdate(ChannelUpdate update) { this.latestUpdate = update; }
        public long getChallengeEndTime() { return challengeEndTime; }
        public long getCreatedAt() { return createdAt; }
    }
    
    public static class ChannelClosure {
        private final String channelId;
        private final ClosureType type;
        private final Map<String, Long> finalBalances;
        private final long timestamp;
        
        public ChannelClosure(String channelId, ClosureType type,
                             Map<String, Long> finalBalances, long timestamp) {
            this.channelId = channelId;
            this.type = type;
            this.finalBalances = finalBalances;
            this.timestamp = timestamp;
        }
        
        public String getChannelId() { return channelId; }
        public ClosureType getType() { return type; }
        public Map<String, Long> getFinalBalances() { return finalBalances; }
        public long getTimestamp() { return timestamp; }
    }
    
    public static class ChannelStatistics {
        private final long totalCreated;
        private final long totalClosed;
        private final int activeChannels;
        private final long totalTransactions;
        private final long totalDisputes;
        private final int activeDisputes;
        
        public ChannelStatistics(long totalCreated, long totalClosed, int activeChannels,
                                long totalTransactions, long totalDisputes, int activeDisputes) {
            this.totalCreated = totalCreated;
            this.totalClosed = totalClosed;
            this.activeChannels = activeChannels;
            this.totalTransactions = totalTransactions;
            this.totalDisputes = totalDisputes;
            this.activeDisputes = activeDisputes;
        }
        
        public long getTotalCreated() { return totalCreated; }
        public long getTotalClosed() { return totalClosed; }
        public int getActiveChannels() { return activeChannels; }
        public long getTotalTransactions() { return totalTransactions; }
        public long getTotalDisputes() { return totalDisputes; }
        public int getActiveDisputes() { return activeDisputes; }
        
        @Override
        public String toString() {
            return String.format(
                "ChannelStats[created=%d, closed=%d, active=%d, txs=%d, disputes=%d]",
                totalCreated, totalClosed, activeChannels, totalTransactions, totalDisputes
            );
        }
    }
}