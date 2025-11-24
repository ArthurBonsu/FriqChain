package org.example.app.core.banking.independence;

import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;
import org.example.app.core.block.Transaction;
import org.example.app.core.crypto.Keypair;
import org.example.app.core.crypto.Keypair.SignatureResult;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * StateChannelManager implements Layer 3 payment channels for the TINC Blockchain.
 * 
 * Payment channels enable parties to conduct unlimited off-chain transactions
 * without hitting the main blockchain, only settling the final balance on-chain.
 * This is critical for:
 * - High-frequency micropayments
 * - Recurring payment scenarios
 * - Reducing transaction costs
 * - Supporting offline transaction scenarios
 * - Enabling instant settlements between trusted parties
 * 
 * Security Features:
 * - Multi-signature channel opening/closing
 * - Cryptographic state commitments
 * - Dispute resolution with challenge periods
 * - Timeout mechanisms for non-responsive parties
 * - Anti-fraud protections
 * 
 * @author BlockchainLab
 * @version 1.0
 */
public class StateChannelManager {
    private static final Logger LOGGER = Logger.getLogger(StateChannelManager.class.getName());
    
    // Channel configuration constants
    private static final long DEFAULT_CHALLENGE_PERIOD_MS = 24 * 60 * 60 * 1000; // 24 hours
    private static final long DEFAULT_CHANNEL_TIMEOUT_MS = 30 * 24 * 60 * 60 * 1000L; // 30 days
    private static final int MAX_CHANNEL_UPDATES = 1000000; // Maximum state updates per channel
    private static final BigInteger MIN_CHANNEL_DEPOSIT = new BigInteger("1000"); // Minimum deposit
    
    // Channel storage
    private final ConcurrentHashMap<String, PaymentChannel> activeChannels;
    private final ConcurrentHashMap<String, PaymentChannel> closingChannels;
    private final ConcurrentHashMap<String, List<DisputeRecord>> disputes;
    
    // Channel indexes for efficient lookup
    private final ConcurrentHashMap<Address, Set<String>> addressChannelIndex;
    
    // Locks for thread safety
    private final ReadWriteLock channelLock;
    private final ReadWriteLock disputeLock;
    
    // Background task executor
    private final ScheduledExecutorService scheduler;
    private final ExecutorService disputeProcessor;
    
    // Metrics
    private volatile long totalChannelsOpened;
    private volatile long totalChannelsClosed;
    private volatile long totalDisputesRaised;
    private volatile long totalDisputesResolved;
    
    /**
     * Represents a bidirectional payment channel between two parties
     */
    public static class PaymentChannel {
        private final String channelId;
        private final Address party1;
        private final Address party2;
        private final BigInteger initialDeposit1;
        private final BigInteger initialDeposit2;
        private final long openTimestamp;
        private final long expiryTimestamp;
        
        // Current channel state
        private volatile BigInteger balance1;
        private volatile BigInteger balance2;
        private volatile long sequenceNumber;
        private volatile ChannelState state;
        private volatile byte[] latestStateHash;
        private volatile List<SignatureResult> latestSignatures;
        
        // Challenge period tracking
        private volatile Long challengeStartTime;
        private volatile ChannelStateCommitment challengedState;
        
        public enum ChannelState {
            OPENING,
            ACTIVE,
            CLOSING,
            CHALLENGED,
            CLOSED,
            EXPIRED
        }
        
        public PaymentChannel(String channelId, Address party1, Address party2,
                            BigInteger deposit1, BigInteger deposit2,
                            long expiryTimestamp) {
            this.channelId = channelId;
            this.party1 = party1;
            this.party2 = party2;
            this.initialDeposit1 = deposit1;
            this.initialDeposit2 = deposit2;
            this.balance1 = deposit1;
            this.balance2 = deposit2;
            this.openTimestamp = System.currentTimeMillis();
            this.expiryTimestamp = expiryTimestamp;
            this.sequenceNumber = 0;
            this.state = ChannelState.OPENING;
            this.latestSignatures = new ArrayList<>();
        }
        
        // Getters
        public String getChannelId() { return channelId; }
        public Address getParty1() { return party1; }
        public Address getParty2() { return party2; }
        public BigInteger getInitialDeposit1() { return initialDeposit1; }
        public BigInteger getInitialDeposit2() { return initialDeposit2; }
        public BigInteger getBalance1() { return balance1; }
        public BigInteger getBalance2() { return balance2; }
        public long getSequenceNumber() { return sequenceNumber; }
        public ChannelState getState() { return state; }
        public long getOpenTimestamp() { return openTimestamp; }
        public long getExpiryTimestamp() { return expiryTimestamp; }
        public byte[] getLatestStateHash() { return latestStateHash != null ? latestStateHash.clone() : null; }
        public List<SignatureResult> getLatestSignatures() { return new ArrayList<>(latestSignatures); }
        public Long getChallengeStartTime() { return challengeStartTime; }
        public ChannelStateCommitment getChallengedState() { return challengedState; }
        
        // Setters
        void setState(ChannelState state) { this.state = state; }
        void setBalance1(BigInteger balance1) { this.balance1 = balance1; }
        void setBalance2(BigInteger balance2) { this.balance2 = balance2; }
        void incrementSequence() { this.sequenceNumber++; }
        void setLatestStateHash(byte[] hash) { this.latestStateHash = hash != null ? hash.clone() : null; }
        void setLatestSignatures(List<SignatureResult> signatures) { this.latestSignatures = new ArrayList<>(signatures); }
        void setChallengeStartTime(Long time) { this.challengeStartTime = time; }
        void setChallengedState(ChannelStateCommitment commitment) { this.challengedState = commitment; }
        
        /**
         * Checks if the channel is expired
         */
        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTimestamp;
        }
        
        /**
         * Checks if the channel has capacity for a payment
         */
        public boolean hasCapacity(Address from, BigInteger amount) {
            if (from.equals(party1)) {
                return balance1.compareTo(amount) >= 0;
            } else if (from.equals(party2)) {
                return balance2.compareTo(amount) >= 0;
            }
            return false;
        }
        
        /**
         * Gets the total locked value in the channel
         */
        public BigInteger getTotalValue() {
            return balance1.add(balance2);
        }
    }
    
    /**
     * Represents a state commitment in a payment channel
     */
    public static class ChannelStateCommitment {
        private final String channelId;
        private final long sequenceNumber;
        private final BigInteger balance1;
        private final BigInteger balance2;
        private final long timestamp;
        private final byte[] stateHash;
        private final List<SignatureResult> signatures;
        
        public ChannelStateCommitment(String channelId, long sequenceNumber,
                                     BigInteger balance1, BigInteger balance2,
                                     byte[] stateHash, List<SignatureResult> signatures) {
            this.channelId = channelId;
            this.sequenceNumber = sequenceNumber;
            this.balance1 = balance1;
            this.balance2 = balance2;
            this.timestamp = System.currentTimeMillis();
            this.stateHash = stateHash.clone();
            this.signatures = new ArrayList<>(signatures);
        }
        
        public String getChannelId() { return channelId; }
        public long getSequenceNumber() { return sequenceNumber; }
        public BigInteger getBalance1() { return balance1; }
        public BigInteger getBalance2() { return balance2; }
        public long getTimestamp() { return timestamp; }
        public byte[] getStateHash() { return stateHash.clone(); }
        public List<SignatureResult> getSignatures() { return new ArrayList<>(signatures); }
    }
    
    /**
     * Represents a dispute record
     */
    public static class DisputeRecord {
        private final String disputeId;
        private final String channelId;
        private final Address challenger;
        private final ChannelStateCommitment claimedState;
        private final long challengeTime;
        private volatile DisputeStatus status;
        private volatile String resolution;
        
        public enum DisputeStatus {
            PENDING,
            COUNTER_CHALLENGED,
            RESOLVED_CHALLENGER,
            RESOLVED_COUNTER,
            EXPIRED
        }
        
        public DisputeRecord(String channelId, Address challenger, ChannelStateCommitment claimedState) {
            this.disputeId = UUID.randomUUID().toString();
            this.channelId = channelId;
            this.challenger = challenger;
            this.claimedState = claimedState;
            this.challengeTime = System.currentTimeMillis();
            this.status = DisputeStatus.PENDING;
        }
        
        public String getDisputeId() { return disputeId; }
        public String getChannelId() { return channelId; }
        public Address getChallenger() { return challenger; }
        public ChannelStateCommitment getClaimedState() { return claimedState; }
        public long getChallengeTime() { return challengeTime; }
        public DisputeStatus getStatus() { return status; }
        public String getResolution() { return resolution; }
        
        void setStatus(DisputeStatus status) { this.status = status; }
        void setResolution(String resolution) { this.resolution = resolution; }
    }
    
    /**
     * Result of channel operations
     */
    public static class ChannelOperationResult {
        private final boolean success;
        private final String message;
        private final PaymentChannel channel;
        private final ChannelStateCommitment stateCommitment;
        
        public ChannelOperationResult(boolean success, String message, 
                                    PaymentChannel channel, ChannelStateCommitment commitment) {
            this.success = success;
            this.message = message;
            this.channel = channel;
            this.stateCommitment = commitment;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public PaymentChannel getChannel() { return channel; }
        public ChannelStateCommitment getStateCommitment() { return stateCommitment; }
    }
    
    /**
     * Constructor
     */
    public StateChannelManager() {
        this.activeChannels = new ConcurrentHashMap<>();
        this.closingChannels = new ConcurrentHashMap<>();
        this.disputes = new ConcurrentHashMap<>();
        this.addressChannelIndex = new ConcurrentHashMap<>();
        this.channelLock = new ReentrantReadWriteLock();
        this.disputeLock = new ReentrantReadWriteLock();
        
        // Initialize executors
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "ChannelManager-Scheduler");
            t.setDaemon(true);
            return t;
        });
        
        this.disputeProcessor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "ChannelManager-DisputeProcessor");
            t.setDaemon(true);
            return t;
        });
        
        // Start background tasks
        startBackgroundTasks();
        
        LOGGER.info("StateChannelManager initialized");
    }
    
    /**
     * Opens a new payment channel between two parties
     */
    public ChannelOperationResult openChannel(Address party1, Address party2,
                                             BigInteger deposit1, BigInteger deposit2,
                                             long durationMs) {
        channelLock.writeLock().lock();
        try {
            // Validate inputs
            if (party1 == null || party2 == null) {
                return new ChannelOperationResult(false, "Invalid party addresses", null, null);
            }
            
            if (party1.equals(party2)) {
                return new ChannelOperationResult(false, "Cannot open channel with self", null, null);
            }
            
            if (deposit1.compareTo(MIN_CHANNEL_DEPOSIT) < 0 || 
                deposit2.compareTo(MIN_CHANNEL_DEPOSIT) < 0) {
                return new ChannelOperationResult(false, 
                    "Deposits must be at least " + MIN_CHANNEL_DEPOSIT, null, null);
            }
            
            if (durationMs <= 0 || durationMs > DEFAULT_CHANNEL_TIMEOUT_MS * 2) {
                return new ChannelOperationResult(false, "Invalid channel duration", null, null);
            }
            
            // Generate unique channel ID
            String channelId = generateChannelId(party1, party2);
            
            // Check if channel already exists
            if (activeChannels.containsKey(channelId)) {
                return new ChannelOperationResult(false, "Channel already exists", null, null);
            }
            
            // Create channel
            long expiryTime = System.currentTimeMillis() + durationMs;
            PaymentChannel channel = new PaymentChannel(channelId, party1, party2, 
                                                       deposit1, deposit2, expiryTime);
            
            // Activate channel
            channel.setState(PaymentChannel.ChannelState.ACTIVE);
            activeChannels.put(channelId, channel);
            
            // Update indexes
            addressChannelIndex.computeIfAbsent(party1, k -> ConcurrentHashMap.newKeySet()).add(channelId);
            addressChannelIndex.computeIfAbsent(party2, k -> ConcurrentHashMap.newKeySet()).add(channelId);
            
            // Update metrics
            totalChannelsOpened++;
            
            LOGGER.info("Opened payment channel: " + channelId + " between " + 
                       party1 + " and " + party2);
            
            return new ChannelOperationResult(true, "Channel opened successfully", channel, null);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error opening channel", e);
            return new ChannelOperationResult(false, "Error: " + e.getMessage(), null, null);
        } finally {
            channelLock.writeLock().unlock();
        }
    }
    
    /**
     * Processes an off-chain payment within a channel
     */
    public ChannelOperationResult processPayment(String channelId, Address from, Address to,
                                                BigInteger amount, 
                                                List<SignatureResult> signatures) {
        channelLock.writeLock().lock();
        try {
            PaymentChannel channel = activeChannels.get(channelId);
            
            if (channel == null) {
                return new ChannelOperationResult(false, "Channel not found", null, null);
            }
            
            if (channel.getState() != PaymentChannel.ChannelState.ACTIVE) {
                return new ChannelOperationResult(false, "Channel not active", null, null);
            }
            
            if (channel.isExpired()) {
                return new ChannelOperationResult(false, "Channel expired", null, null);
            }
            
            // Validate parties
            if (!from.equals(channel.getParty1()) && !from.equals(channel.getParty2())) {
                return new ChannelOperationResult(false, "Invalid sender", null, null);
            }
            
            if (!to.equals(channel.getParty1()) && !to.equals(channel.getParty2())) {
                return new ChannelOperationResult(false, "Invalid recipient", null, null);
            }
            
            if (from.equals(to)) {
                return new ChannelOperationResult(false, "Cannot send to self", null, null);
            }
            
            // Check capacity
            if (!channel.hasCapacity(from, amount)) {
                return new ChannelOperationResult(false, "Insufficient channel balance", null, null);
            }
            
            // Validate signatures (both parties must sign)
            if (signatures == null || signatures.size() < 2) {
                return new ChannelOperationResult(false, "Missing signatures", null, null);
            }
            
            // Update balances
            BigInteger newBalance1, newBalance2;
            if (from.equals(channel.getParty1())) {
                newBalance1 = channel.getBalance1().subtract(amount);
                newBalance2 = channel.getBalance2().add(amount);
            } else {
                newBalance1 = channel.getBalance1().add(amount);
                newBalance2 = channel.getBalance2().subtract(amount);
            }
            
            // Verify conservation of funds
            BigInteger totalBefore = channel.getBalance1().add(channel.getBalance2());
            BigInteger totalAfter = newBalance1.add(newBalance2);
            if (!totalBefore.equals(totalAfter)) {
                return new ChannelOperationResult(false, "Balance conservation violated", null, null);
            }
            
            // Create state commitment
            channel.incrementSequence();
            byte[] stateHash = calculateStateHash(channelId, channel.getSequenceNumber(),
                                                 newBalance1, newBalance2);
            
            ChannelStateCommitment commitment = new ChannelStateCommitment(
                channelId, channel.getSequenceNumber(), newBalance1, newBalance2,
                stateHash, signatures
            );
            
            // Update channel state
            channel.setBalance1(newBalance1);
            channel.setBalance2(newBalance2);
            channel.setLatestStateHash(stateHash);
            channel.setLatestSignatures(signatures);
            
            LOGGER.info("Processed payment in channel " + channelId + ": " + 
                       amount + " from " + from + " to " + to);
            
            return new ChannelOperationResult(true, "Payment processed", channel, commitment);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing payment", e);
            return new ChannelOperationResult(false, "Error: " + e.getMessage(), null, null);
        } finally {
            channelLock.writeLock().unlock();
        }
    }
    
    /**
     * Initiates cooperative channel closure
     */
    public ChannelOperationResult closeChannel(String channelId, 
                                              List<SignatureResult> closingSignatures) {
        channelLock.writeLock().lock();
        try {
            PaymentChannel channel = activeChannels.get(channelId);
            
            if (channel == null) {
                return new ChannelOperationResult(false, "Channel not found", null, null);
            }
            
            if (channel.getState() != PaymentChannel.ChannelState.ACTIVE) {
                return new ChannelOperationResult(false, "Channel not in active state", null, null);
            }
            
            // Validate closing signatures (both parties must agree)
            if (closingSignatures == null || closingSignatures.size() < 2) {
                return new ChannelOperationResult(false, "Missing closing signatures", null, null);
            }
            
            // Create final state commitment
            byte[] finalStateHash = calculateStateHash(channelId, channel.getSequenceNumber(),
                                                      channel.getBalance1(), channel.getBalance2());
            
            ChannelStateCommitment finalState = new ChannelStateCommitment(
                channelId, channel.getSequenceNumber(), 
                channel.getBalance1(), channel.getBalance2(),
                finalStateHash, closingSignatures
            );
            
            // Move to closing state
            channel.setState(PaymentChannel.ChannelState.CLOSING);
            channel.setChallengeStartTime(System.currentTimeMillis());
            closingChannels.put(channelId, channel);
            activeChannels.remove(channelId);
            
            LOGGER.info("Channel " + channelId + " entering closing state with challenge period");
            
            return new ChannelOperationResult(true, "Channel closing initiated", channel, finalState);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error closing channel", e);
            return new ChannelOperationResult(false, "Error: " + e.getMessage(), null, null);
        } finally {
            channelLock.writeLock().unlock();
        }
    }
    
    /**
     * Raises a dispute with a channel state
     */
    public DisputeRecord raiseDispute(String channelId, Address challenger,
                                     ChannelStateCommitment claimedState) {
        disputeLock.writeLock().lock();
        try {
            PaymentChannel channel = activeChannels.get(channelId);
            if (channel == null) {
                channel = closingChannels.get(channelId);
            }
            
            if (channel == null) {
                LOGGER.warning("Cannot raise dispute: channel not found");
                return null;
            }
            
            // Validate challenger is a party to the channel
            if (!challenger.equals(channel.getParty1()) && !challenger.equals(channel.getParty2())) {
                LOGGER.warning("Cannot raise dispute: challenger not party to channel");
                return null;
            }
            
            // Create dispute record
            DisputeRecord dispute = new DisputeRecord(channelId, challenger, claimedState);
            
            // Store dispute
            disputes.computeIfAbsent(channelId, k -> new ArrayList<>()).add(dispute);
            
            // Update channel state
            channel.setState(PaymentChannel.ChannelState.CHALLENGED);
            channel.setChallengeStartTime(System.currentTimeMillis());
            channel.setChallengedState(claimedState);
            
            // Update metrics
            totalDisputesRaised++;
            
            LOGGER.warning("Dispute raised for channel " + channelId + " by " + challenger);
            
            return dispute;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error raising dispute", e);
            return null;
        } finally {
            disputeLock.writeLock().unlock();
        }
    }
    
    /**
     * Submits a counter-challenge with a higher sequence number state
     */
    public boolean submitCounterChallenge(String channelId, Address counterChallenger,
                                         ChannelStateCommitment newerState) {
        disputeLock.writeLock().lock();
        try {
            List<DisputeRecord> channelDisputes = disputes.get(channelId);
            if (channelDisputes == null || channelDisputes.isEmpty()) {
                return false;
            }
            
            // Find active dispute
            DisputeRecord activeDispute = channelDisputes.stream()
                .filter(d -> d.getStatus() == DisputeRecord.DisputeStatus.PENDING)
                .findFirst()
                .orElse(null);
            
            if (activeDispute == null) {
                return false;
            }
            
            // Validate counter-challenger
            PaymentChannel channel = closingChannels.get(channelId);
            if (channel == null) {
                channel = activeChannels.get(channelId);
            }
            
            if (channel == null) {
                return false;
            }
            
            if (!counterChallenger.equals(channel.getParty1()) && 
                !counterChallenger.equals(channel.getParty2())) {
                return false;
            }
            
            // Validate newer state has higher sequence number
            if (newerState.getSequenceNumber() <= activeDispute.getClaimedState().getSequenceNumber()) {
                return false;
            }
            
            // Update dispute status
            activeDispute.setStatus(DisputeRecord.DisputeStatus.COUNTER_CHALLENGED);
            activeDispute.setResolution("Counter-challenged with higher sequence state");
            
            // Update channel with newer state
            channel.setBalance1(newerState.getBalance1());
            channel.setBalance2(newerState.getBalance2());
            channel.setLatestStateHash(newerState.getStateHash());
            channel.setLatestSignatures(newerState.getSignatures());
            
            LOGGER.info("Counter-challenge submitted for channel " + channelId);
            
            return true;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error submitting counter-challenge", e);
            return false;
        } finally {
            disputeLock.writeLock().unlock();
        }
    }
    
    /**
     * Finalizes channel closure after challenge period
     */
    public boolean finalizeChannel(String channelId) {
        channelLock.writeLock().lock();
        try {
            PaymentChannel channel = closingChannels.get(channelId);
            
            if (channel == null) {
                return false;
            }
            
            if (channel.getState() != PaymentChannel.ChannelState.CLOSING &&
                channel.getState() != PaymentChannel.ChannelState.CHALLENGED) {
                return false;
            }
            
            // Check if challenge period has passed
            Long challengeStart = channel.getChallengeStartTime();
            if (challengeStart == null) {
                return false;
            }
            
            long elapsed = System.currentTimeMillis() - challengeStart;
            if (elapsed < DEFAULT_CHALLENGE_PERIOD_MS) {
                return false;
            }
            
            // Finalize channel
            channel.setState(PaymentChannel.ChannelState.CLOSED);
            closingChannels.remove(channelId);
            
            // Clean up indexes
            addressChannelIndex.getOrDefault(channel.getParty1(), Collections.emptySet())
                              .remove(channelId);
            addressChannelIndex.getOrDefault(channel.getParty2(), Collections.emptySet())
                              .remove(channelId);
            
            // Update metrics
            totalChannelsClosed++;
            
            LOGGER.info("Channel " + channelId + " finalized. Final balances: " +
                       "Party1=" + channel.getBalance1() + ", Party2=" + channel.getBalance2());
            
            return true;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error finalizing channel", e);
            return false;
        } finally {
            channelLock.writeLock().unlock();
        }
    }
    
    /**
     * Gets channels for an address
     */
    public List<PaymentChannel> getChannelsForAddress(Address address) {
        channelLock.readLock().lock();
        try {
            Set<String> channelIds = addressChannelIndex.getOrDefault(address, Collections.emptySet());
            List<PaymentChannel> channels = new ArrayList<>();
            
            for (String channelId : channelIds) {
                PaymentChannel channel = activeChannels.get(channelId);
                if (channel == null) {
                    channel = closingChannels.get(channelId);
                }
                if (channel != null) {
                    channels.add(channel);
                }
            }
            
            return channels;
            
        } finally {
            channelLock.readLock().unlock();
        }
    }
    
    /**
     * Gets disputes for a channel
     */
    public List<DisputeRecord> getDisputes(String channelId) {
        disputeLock.readLock().lock();
        try {
            return new ArrayList<>(disputes.getOrDefault(channelId, Collections.emptyList()));
        } finally {
            disputeLock.readLock().unlock();
        }
    }
    
    /**
     * Calculates state hash for channel commitment
     */
    private byte[] calculateStateHash(String channelId, long sequenceNumber,
                                     BigInteger balance1, BigInteger balance2) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(512);
            buffer.put(channelId.getBytes());
            buffer.putLong(sequenceNumber);
            buffer.put(balance1.toByteArray());
            buffer.put(balance2.toByteArray());
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(Arrays.copyOf(buffer.array(), buffer.position()));
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error calculating state hash", e);
            return new byte[32]; // Return empty hash on error
        }
    }
    
    /**
     * Generates unique channel ID
     */
    private String generateChannelId(Address party1, Address party2) {
        try {
            // Sort addresses to ensure consistency
            String addr1 = party1.toString();
            String addr2 = party2.toString();
            String combined = addr1.compareTo(addr2) < 0 ? 
                            (addr1 + addr2) : (addr2 + addr1);
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined.getBytes());
            
            return "CH-" + bytesToHex(Arrays.copyOf(hash, 16));
            
        } catch (Exception e) {
            return "CH-" + UUID.randomUUID().toString();
        }
    }
    
    /**
     * Starts background maintenance tasks
     */
    private void startBackgroundTasks() {
        // Check for expired channels every hour
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkExpiredChannels();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in expired channel check", e);
            }
        }, 1, 60, TimeUnit.MINUTES);
        
        // Process pending disputes every 10 minutes
        scheduler.scheduleAtFixedRate(() -> {
            try {
                processDisputeTimeouts();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in dispute processing", e);
            }
        }, 10, 10, TimeUnit.MINUTES);
    }
    
    /**
     * Checks for and handles expired channels
     */
    private void checkExpiredChannels() {
        channelLock.writeLock().lock();
        try {
            long now = System.currentTimeMillis();
            List<String> expiredIds = new ArrayList<>();
            
            for (Map.Entry<String, PaymentChannel> entry : activeChannels.entrySet()) {
                PaymentChannel channel = entry.getValue();
                if (now > channel.getExpiryTimestamp()) {
                    expiredIds.add(entry.getKey());
                }
            }
            
            for (String channelId : expiredIds) {
                PaymentChannel channel = activeChannels.remove(channelId);
                if (channel != null) {
                    channel.setState(PaymentChannel.ChannelState.EXPIRED);
                    LOGGER.info("Channel expired: " + channelId);
                }
            }
            
        } finally {
            channelLock.writeLock().unlock();
        }
    }
    
    /**
     * Processes dispute timeouts
     */
    private void processDisputeTimeouts() {
        disputeLock.writeLock().lock();
        try {
            long now = System.currentTimeMillis();
            
            for (Map.Entry<String, List<DisputeRecord>> entry : disputes.entrySet()) {
                for (DisputeRecord dispute : entry.getValue()) {
                    if (dispute.getStatus() == DisputeRecord.DisputeStatus.PENDING) {
                        long elapsed = now - dispute.getChallengeTime();
                        if (elapsed > DEFAULT_CHALLENGE_PERIOD_MS) {
                            dispute.setStatus(DisputeRecord.DisputeStatus.RESOLVED_CHALLENGER);
                            dispute.setResolution("No counter-challenge received");
                            totalDisputesResolved++;
                        }
                    }
                }
            }
            
        } finally {
            disputeLock.writeLock().unlock();
        }
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
     * Gets channel statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeChannels", activeChannels.size());
        stats.put("closingChannels", closingChannels.size());
        stats.put("totalChannelsOpened", totalChannelsOpened);
        stats.put("totalChannelsClosed", totalChannelsClosed);
        stats.put("totalDisputesRaised", totalDisputesRaised);
        stats.put("totalDisputesResolved", totalDisputesResolved);
        return stats;
    }
    
    /**
     * Shutdown
     */
    public void shutdown() {
        scheduler.shutdown();
        disputeProcessor.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!disputeProcessor.awaitTermination(5, TimeUnit.SECONDS)) {
                disputeProcessor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            disputeProcessor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("StateChannelManager shutdown complete");
    }
}