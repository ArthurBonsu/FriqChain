package org.example.app.core.banking.network;

import java.util.stream.Collectors;

import org.example.app.core.blockchain.block.ShardState;
import org.example.app.core.blockchain.transaction.CrossShardTransaction;
import org.example.app.core.types.Address;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PasschainManager handles passchain-based sharding for scalability.
 * Passchains are lightweight sidechains that enable fast local consensus
 * while maintaining connection to the main relay chain.
 * 
 * Key Features:
 * - Geographic/functional passchains (West Africa, retail banking, etc.)
 * - Local consensus: ~5,000 TPS per passchain
 * - Periodic checkpointing to main chain
 * - Fast intra-passchain settlements
 */
public class PasschainManager {
    private static final Logger logger = Logger.getLogger(PasschainManager.class.getName());
    
    // Configuration
    private static final int MAX_PASSCHAINS = 100;
    private static final long CHECKPOINT_INTERVAL_MS = 10000; // 10 seconds
    private static final int MAX_VALIDATORS_PER_PASSCHAIN = 21;
    private static final int MIN_VALIDATORS_PER_PASSCHAIN = 4;
    
    // Passchain management
    private final Map<String, Passchain> passchains;
    private final Map<String, Set<String>> accountToPasschains; // account → passchain IDs
    private final ScheduledExecutorService checkpointExecutor;
    
    /**
     * Passchain data structure
     */
    public static class Passchain {
        private final String passchainId;
        private final String name;
        private final PasschainType type;
        private final Set<String> validators;
        private final AtomicLong blockHeight;
        private final Map<String, byte[]> stateData;
        private byte[] lastCheckpointHash;
        private long lastCheckpointTime;
        private final AtomicLong transactionCount;
        private double currentTPS;
        private boolean isActive;
        
        public Passchain(String passchainId, String name, PasschainType type) {
            this.passchainId = passchainId;
            this.name = name;
            this.type = type;
            this.validators = ConcurrentHashMap.newKeySet();
            this.blockHeight = new AtomicLong(0);
            this.stateData = new ConcurrentHashMap<>();
            this.lastCheckpointHash = new byte[32];
            this.lastCheckpointTime = System.currentTimeMillis();
            this.transactionCount = new AtomicLong(0);
            this.currentTPS = 0.0;
            this.isActive = true;
        }
        
        public String getPasschainId() { return passchainId; }
        public String getName() { return name; }
        public PasschainType getType() { return type; }
        public Set<String> getValidators() { return new HashSet<>(validators); }
        public long getBlockHeight() { return blockHeight.get(); }
        public byte[] getLastCheckpointHash() { return Arrays.copyOf(lastCheckpointHash, lastCheckpointHash.length); }
        public long getLastCheckpointTime() { return lastCheckpointTime; }
        public long getTransactionCount() { return transactionCount.get(); }
        public double getCurrentTPS() { return currentTPS; }
        public boolean isActive() { return isActive; }
        
        public void addValidator(String validatorId) {
            validators.add(validatorId);
        }
        
        public void removeValidator(String validatorId) {
            validators.remove(validatorId);
        }
        
        public void incrementBlockHeight() {
            blockHeight.incrementAndGet();
        }
        
        public void incrementTransactionCount(long count) {
            transactionCount.addAndGet(count);
        }
        
        public void updateTPS(double tps) {
            this.currentTPS = tps;
        }
        
        public void setLastCheckpoint(byte[] hash, long timestamp) {
            this.lastCheckpointHash = Arrays.copyOf(hash, hash.length);
            this.lastCheckpointTime = timestamp;
        }
        
        public void setActive(boolean active) {
            this.isActive = active;
        }
        
        public void updateStateData(String key, byte[] data) {
            stateData.put(key, Arrays.copyOf(data, data.length));
        }
        
        public byte[] getStateData(String key) {
            byte[] data = stateData.get(key);
            return data != null ? Arrays.copyOf(data, data.length) : null;
        }
    }
    
    /**
     * Passchain type enumeration
     */
    public enum PasschainType {
        GEOGRAPHIC,     // Geographic region (e.g., West Africa)
        FUNCTIONAL,     // Functional area (e.g., retail banking)
        HYBRID          // Combination of geographic + functional
    }
    
    /**
     * Checkpoint data
     */
    public static class CheckpointData {
        private final String passchainId;
        private final long blockHeight;
        private final byte[] stateRoot;
        private final long timestamp;
        private final List<byte[]> validatorSignatures;
        
        public CheckpointData(String passchainId, long blockHeight, byte[] stateRoot,
                            long timestamp, List<byte[]> signatures) {
            this.passchainId = passchainId;
            this.blockHeight = blockHeight;
            this.stateRoot = Arrays.copyOf(stateRoot, stateRoot.length);
            this.timestamp = timestamp;
            this.validatorSignatures = new ArrayList<>();
            for (byte[] sig : signatures) {
                this.validatorSignatures.add(Arrays.copyOf(sig, sig.length));
            }
        }
        
        public String getPasschainId() { return passchainId; }
        public long getBlockHeight() { return blockHeight; }
        public byte[] getStateRoot() { return Arrays.copyOf(stateRoot, stateRoot.length); }
        public long getTimestamp() { return timestamp; }
        public List<byte[]> getValidatorSignatures() {
            List<byte[]> copies = new ArrayList<>();
            for (byte[] sig : validatorSignatures) {
                copies.add(Arrays.copyOf(sig, sig.length));
            }
            return copies;
        }
    }
    
    /**
     * Constructor
     */
    public PasschainManager() {
        this.passchains = new ConcurrentHashMap<>();
        this.accountToPasschains = new ConcurrentHashMap<>();
        this.checkpointExecutor = Executors.newScheduledThreadPool(2);
        
        logger.info("PasschainManager initialized");
    }
    
    /**
     * Creates a new passchain
     */
    public synchronized String createPasschain(String name, PasschainType type) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Passchain name cannot be null or empty");
        }
        
        if (passchains.size() >= MAX_PASSCHAINS) {
            throw new IllegalStateException("Maximum passchain limit reached");
        }
        
        String passchainId = generatePasschainId(name, type);
        Passchain passchain = new Passchain(passchainId, name, type);
        passchains.put(passchainId, passchain);
        
        logger.info("Created passchain: " + passchainId + " (" + name + ", type: " + type + ")");
        
        return passchainId;
    }
    
    /**
     * Generates a unique passchain ID
     */
    private String generatePasschainId(String name, PasschainType type) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = name + type.toString() + System.currentTimeMillis();
            byte[] hash = digest.digest(input.getBytes());
            
            // Use first 16 bytes for ID
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            
            return "PC-" + sb.toString();
        } catch (Exception e) {
            return "PC-" + UUID.randomUUID().toString().substring(0, 16);
        }
    }
    
    /**
     * Assigns an account to a passchain
     */
    public void assignAccountToPasschain(String accountAddress, String passchainId) {
        if (!passchains.containsKey(passchainId)) {
            throw new IllegalArgumentException("Passchain does not exist: " + passchainId);
        }
        
        accountToPasschains.computeIfAbsent(accountAddress, k -> ConcurrentHashMap.newKeySet())
            .add(passchainId);
        
        logger.fine("Assigned account " + accountAddress + " to passchain " + passchainId);
    }
    
    /**
     * Removes an account from a passchain
     */
    public void removeAccountFromPasschain(String accountAddress, String passchainId) {
        Set<String> accountPasschains = accountToPasschains.get(accountAddress);
        if (accountPasschains != null) {
            accountPasschains.remove(passchainId);
        }
    }
    
    /**
     * Gets passchains for an account
     */
    public Set<String> getAccountPasschains(String accountAddress) {
        Set<String> chains = accountToPasschains.get(accountAddress);
        return chains != null ? new HashSet<>(chains) : Collections.emptySet();
    }
    
    /**
     * Adds a validator to a passchain
     */
    public synchronized void addValidatorToPasschain(String validatorId, String passchainId) {
        Passchain passchain = passchains.get(passchainId);
        if (passchain == null) {
            throw new IllegalArgumentException("Passchain does not exist: " + passchainId);
        }
        
        if (passchain.getValidators().size() >= MAX_VALIDATORS_PER_PASSCHAIN) {
            throw new IllegalStateException("Passchain validator limit reached");
        }
        
        passchain.addValidator(validatorId);
        logger.fine("Added validator " + validatorId + " to passchain " + passchainId);
    }
    
    /**
     * Removes a validator from a passchain
     */
    public synchronized void removeValidatorFromPasschain(String validatorId, String passchainId) {
        Passchain passchain = passchains.get(passchainId);
        if (passchain == null) {
            return;
        }
        
        passchain.removeValidator(validatorId);
        
        // Check if passchain still has minimum validators
        if (passchain.getValidators().size() < MIN_VALIDATORS_PER_PASSCHAIN) {
            logger.warning("Passchain " + passchainId + " has insufficient validators");
        }
    }
    
    /**
     * Records a transaction in a passchain
     */
    public void recordTransaction(String passchainId, long txCount) {
        Passchain passchain = passchains.get(passchainId);
        if (passchain == null) {
            logger.warning("Attempt to record transaction in non-existent passchain: " + passchainId);
            return;
        }
        
        passchain.incrementTransactionCount(txCount);
        
        // Update TPS calculation
        updatePasschainTPS(passchainId);
    }
    
    /**
     * Updates TPS for a passchain
     */
    private void updatePasschainTPS(String passchainId) {
        Passchain passchain = passchains.get(passchainId);
        if (passchain == null) {
            return;
        }
        
        // Calculate TPS based on transactions since last checkpoint
        long timeDelta = System.currentTimeMillis() - passchain.getLastCheckpointTime();
        if (timeDelta > 0) {
            double tps = (passchain.getTransactionCount() * 1000.0) / timeDelta;
            passchain.updateTPS(tps);
        }
    }
    
    /**
     * Starts periodic checkpointing
     */
    public void startCheckpointing() {
        checkpointExecutor.scheduleAtFixedRate(
            this::performCheckpoints,
            CHECKPOINT_INTERVAL_MS,
            CHECKPOINT_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        logger.info("Checkpoint system started (interval: " + CHECKPOINT_INTERVAL_MS + "ms)");
    }
    
    /**
     * Performs checkpoints for all active passchains
     */
    private void performCheckpoints() {
        try {
            for (Passchain passchain : passchains.values()) {
                if (passchain.isActive()) {
                    createCheckpoint(passchain.getPasschainId());
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during checkpoint creation", e);
        }
    }
    
    /**
     * Creates a checkpoint for a passchain
     */
    public CheckpointData createCheckpoint(String passchainId) {
        Passchain passchain = passchains.get(passchainId);
        if (passchain == null) {
            throw new IllegalArgumentException("Passchain does not exist: " + passchainId);
        }
        
        try {
            // Generate state root
            byte[] stateRoot = computeStateRoot(passchain);
            
            // Get validator signatures
            List<byte[]> signatures = collectValidatorSignatures(passchain, stateRoot);
            
            // Verify minimum signatures (2/3 of validators)
            int minSignatures = (passchain.getValidators().size() * 2) / 3;
            if (signatures.size() < minSignatures) {
                logger.warning("Insufficient validator signatures for checkpoint of " + passchainId);
                return null;
            }
            
            // Create checkpoint
            CheckpointData checkpoint = new CheckpointData(
                passchainId,
                passchain.getBlockHeight(),
                stateRoot,
                System.currentTimeMillis(),
                signatures
            );
            
            // Update passchain
            passchain.setLastCheckpoint(stateRoot, checkpoint.getTimestamp());
            passchain.incrementBlockHeight();
            
            logger.fine("Created checkpoint for passchain " + passchainId + 
                " at height " + checkpoint.getBlockHeight());
            
            return checkpoint;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error creating checkpoint for " + passchainId, e);
            return null;
        }
    }
    
    /**
     * Computes state root for a passchain
     */
    private byte[] computeStateRoot(Passchain passchain) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Combine all state data
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            buffer.putLong(passchain.getBlockHeight());
            buffer.putLong(passchain.getTransactionCount());
            buffer.put(passchain.getLastCheckpointHash());
            
            buffer.flip();
            byte[] data = new byte[buffer.limit()];
            buffer.get(data);
            
            return digest.digest(data);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error computing state root", e);
            return new byte[32];
        }
    }
    
    /**
     * Collects validator signatures for a checkpoint
     */
    private List<byte[]> collectValidatorSignatures(Passchain passchain, byte[] stateRoot) {
        List<byte[]> signatures = new ArrayList<>();
        
        // In production, this would:
        // 1. Request signatures from all validators
        // 2. Wait for responses with timeout
        // 3. Verify signature authenticity
        
        // For now, simulate validator signatures
        for (String validatorId : passchain.getValidators()) {
            byte[] signature = generateMockSignature(validatorId, stateRoot);
            signatures.add(signature);
        }
        
        return signatures;
    }
    
    /**
     * Generates a mock validator signature (for testing)
     */
    private byte[] generateMockSignature(String validatorId, byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] combined = new byte[validatorId.length() + data.length];
            System.arraycopy(validatorId.getBytes(), 0, combined, 0, validatorId.length());
            System.arraycopy(data, 0, combined, validatorId.length(), data.length);
            return digest.digest(combined);
        } catch (Exception e) {
            return new byte[64];
        }
    }
    
    /**
     * Gets a passchain by ID
     */
    public Passchain getPasschain(String passchainId) {
        return passchains.get(passchainId);
    }
    
    /**
     * Gets all passchains
     */
    public Map<String, Passchain> getAllPasschains() {
        return new HashMap<>(passchains);
    }
    
    /**
     * Gets passchains by type
     */
    public List<Passchain> getPasschainsByType(PasschainType type) {
        return passchains.values().stream()
            .filter(pc -> pc.getType() == type)
            .collect(Collectors.toList());
    }
    
    /**
     * Deactivates a passchain
     */
    public synchronized void deactivatePasschain(String passchainId) {
        Passchain passchain = passchains.get(passchainId);
        if (passchain != null) {
            passchain.setActive(false);
            logger.info("Deactivated passchain: " + passchainId);
        }
    }
    
    /**
     * Reactivates a passchain
     */
    public synchronized void reactivatePasschain(String passchainId) {
        Passchain passchain = passchains.get(passchainId);
        if (passchain != null) {
            passchain.setActive(true);
            logger.info("Reactivated passchain: " + passchainId);
        }
    }
    
    /**
     * Gets total TPS across all passchains
     */
    public double getTotalTPS() {
        return passchains.values().stream()
            .filter(Passchain::isActive)
            .mapToDouble(Passchain::getCurrentTPS)
            .sum();
    }
    
    /**
     * Gets passchain statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalPasschains", passchains.size());
        stats.put("activePasschains", passchains.values().stream()
            .filter(Passchain::isActive)
            .count());
        stats.put("totalTPS", String.format("%.2f", getTotalTPS()));
        
        Map<PasschainType, Long> typeDistribution = passchains.values().stream()
            .collect(Collectors.groupingBy(Passchain::getType, Collectors.counting()));
        stats.put("typeDistribution", typeDistribution);
        
        return stats;
    }
    
    /**
     * Shuts down the manager
     */
    public void shutdown() {
        logger.info("Shutting down PasschainManager");
        
        checkpointExecutor.shutdown();
        try {
            if (!checkpointExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                checkpointExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            checkpointExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("PasschainManager shut down");
    }
}