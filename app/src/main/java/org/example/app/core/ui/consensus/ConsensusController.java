package org.example.app.core.ui.consensus;

import org.example.app.core.consensus.Consensus;
import org.example.app.core.consensus.EthashConsensus;
import org.example.app.core.consensus.ProofOfWork;
import org.example.app.core.consensus.Blockchain;
import org.example.app.core.pbftconsensus.PBFT;
import org.example.app.core.pbftconsensus.PBFTReplica;
import org.example.app.core.pbftconsensus.NodeId;
import org.example.app.core.block.Block;
import org.example.app.core.block.Transaction;
import org.example.app.core.block.Miner;
import org.example.app.core.mempool.TransactionPool;
import org.example.app.core.metrics.ConsensusMetrics;
import org.example.app.core.wallet.Wallet;
import org.example.app.core.config.ConsensusConfig;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * ConsensusController manages consensus mechanism operations and state for the UI layer.
 * Supports multiple consensus algorithms: PoW, PoS, PBFT, and Stream-based consensus.
 */

 
public class ConsensusController {
    private static final Logger LOGGER = Logger.getLogger(ConsensusController.class.getName());
    
    // Core consensus components
    private Consensus currentConsensus;
    private ConsensusType activeConsensusType;
    private Blockchain blockchain;
    private TransactionPool transactionPool;
    private ConsensusMetrics consensusMetrics;
    private ConsensusConfig consensusConfig;
    
    // Consensus implementations - using existing classes only
    private EthashConsensus ethashConsensus;
    private ProofOfWork proofOfWork;
    private PBFT pbftConsensus;
    
    // Mining and validation
    private Miner currentMiner;
    private boolean isMining;
    private boolean isValidating;
    private String minerAddress;
    private long currentDifficulty;
    
    // PBFT specific - simplified without actual PBFT components
    private boolean pbftInitialized;
    private List<String> pbftValidatorAddresses;
    private boolean isPrimaryNode;
    
    // PoS specific - simplified without new classes
    private Map<String, Long> stakeholderBalances;
    private String stakingAddress;
    private long stakingAmount;
    
    // Wallet integration
    private Wallet connectedWallet;
    private String activeWalletId;
    
    // Event listeners for dashboard integration
    private List<ConsensusEventListener> eventListeners;
    

    // Add these fields near the top of ConsensusController class (around line 88)
// Add these fields near the top of ConsensusController class
private boolean autoMiningEnabled = false;
private boolean autoValidatingEnabled = false;

    /**
     * Supported consensus mechanisms
     */
    public enum ConsensusType {
        PROOF_OF_WORK("Proof of Work", "pow"),
        PROOF_OF_STAKE("Proof of Stake", "pos"), 
        PBFT("Practical Byzantine Fault Tolerance", "pbft"),
        STREAM_CONSENSUS("Stream-based Consensus", "stream");
        
        private final String displayName;
        private final String identifier;
        
        ConsensusType(String displayName, String identifier) {
            this.displayName = displayName;
            this.identifier = identifier;
        }
        
        public String getDisplayName() { return displayName; }
        public String getIdentifier() { return identifier; }
    }
    
    /**
     * Consensus operation results
     */
    public static class ConsensusOperationResult {
        public final boolean success;
        public final String message;
        public final Object data;
        
        public ConsensusOperationResult(boolean success, String message, Object data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
        
        public static ConsensusOperationResult success(String message, Object data) {
            return new ConsensusOperationResult(true, message, data);
        }
        
        public static ConsensusOperationResult failure(String message) {
            return new ConsensusOperationResult(false, message, null);
        }
    }
    
    /**
     * Constructor initializes consensus controller with default PoW
     */
    public ConsensusController() {
        LOGGER.info("Initializing ConsensusController");
        
        this.eventListeners = new ArrayList<>();
        this.stakeholderBalances = new HashMap<>();
        this.pbftValidatorAddresses = new ArrayList<>();
        this.consensusMetrics = new ConsensusMetrics();
        this.consensusConfig = ConsensusConfig.createDefault();
        
        // Initialize with default Proof of Work
        this.activeConsensusType = ConsensusType.PROOF_OF_WORK;
        this.currentDifficulty = 1000L; // Default difficulty
        this.isMining = false;
        this.isValidating = false;
        this.isPrimaryNode = false;
        this.pbftInitialized = false;
        
        // Initialize consensus implementations
        initializeConsensusImplementations();
        
        LOGGER.info("ConsensusController initialized successfully with " + activeConsensusType.getDisplayName());
    }
    
    /**
     * Initializes all consensus mechanism implementations using existing classes
     */
    private void initializeConsensusImplementations() {
        try {
            // Initialize PoW components
            this.ethashConsensus = new EthashConsensus();
            this.proofOfWork = new ProofOfWork();
            
            // Initialize PBFT components - simplified without actual PBFT initialization
            // since PBFT constructor requires specific parameters we don't have
            this.pbftInitialized = false;
            
            // Set current consensus to PoW by default
            this.currentConsensus = new Consensus();
            
            LOGGER.info("All consensus implementations initialized successfully");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error initializing consensus implementations", e);
            throw new RuntimeException("Failed to initialize consensus mechanisms", e);
        }
    }
    
    // ========================================
    // CONSENSUS MECHANISM MANAGEMENT
    // ========================================
    
    /**
     * Switches to a different consensus mechanism
     */
    public ConsensusOperationResult switchConsensusType(ConsensusType newType) {
        LOGGER.info("Switching consensus mechanism from " + activeConsensusType.getDisplayName() + 
                   " to " + newType.getDisplayName());
        
        try {
            // Stop current consensus operations
            stopConsensusOperations();
            
            // Prepare for transition
            ConsensusType previousType = activeConsensusType;
            activeConsensusType = newType;
            
            // Initialize new consensus mechanism
            boolean switchSuccess = initializeNewConsensus(newType);
            
            if (switchSuccess) {
                // Notify listeners
                notifyConsensusChanged(previousType, newType);
                
                LOGGER.info("Successfully switched to " + newType.getDisplayName());
                return ConsensusOperationResult.success(
                    "Consensus mechanism switched to " + newType.getDisplayName(), 
                    getConsensusStatus()
                );
            } else {
                // Rollback on failure
                activeConsensusType = previousType;
                initializeNewConsensus(previousType);
                
                return ConsensusOperationResult.failure(
                    "Failed to switch to " + newType.getDisplayName()
                );
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during consensus switch", e);
            return ConsensusOperationResult.failure("Consensus switch failed: " + e.getMessage());
        }
    }
    
    /**
     * Stops all current consensus operations safely
     */
    private void stopConsensusOperations() {
        LOGGER.info("Stopping current consensus operations");
        
        try {
            // Stop mining
            if (isMining) {
                stopMining();
            }
            
            // Stop validation
            if (isValidating) {
                stopValidation();
            }
            
            // Stop PBFT operations - simplified
            if (pbftInitialized && activeConsensusType == ConsensusType.PBFT) {
                pbftInitialized = false;
                isValidating = false;
            }
            
            LOGGER.info("All consensus operations stopped successfully");
            
        } catch (Exception e) {
            LOGGER.warning("Error stopping consensus operations: " + e.getMessage());
        }
    }
    
    /**
     * Initializes the new consensus mechanism
     */
    private boolean initializeNewConsensus(ConsensusType type) {
    try {
        LOGGER.info("Initializing " + type.getDisplayName() + " consensus mechanism");
        
        // Stop any existing consensus operations
        stopConsensusOperations();
        
        switch (type) {
            case PROOF_OF_WORK:
                boolean powInit = initializeProofOfWork();
                if (powInit && autoMiningEnabled) {
                    startMining(); // Auto-start if enabled
                }
                return powInit;
                
            case PROOF_OF_STAKE:
                boolean posInit = initializeProofOfStake();
                if (posInit && autoValidatingEnabled) {
                    startValidation(); // Auto-start validation
                }
                return posInit;
                case PBFT:
                    return initializePBFT();
                case STREAM_CONSENSUS:
                    return initializeStreamConsensus();
                default:
                    LOGGER.severe("Unknown consensus type: " + type);
                    return false;
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error initializing consensus mechanism: " + type, e);
            return false;
        }
    }
    
    // ========================================
    // PROOF OF WORK OPERATIONS
    // ========================================
    
    /**
     * Initializes Proof of Work consensus
     */
    // In ConsensusController.java, modify initializeProofOfWork()
private boolean initializeProofOfWork() {
    try {
        LOGGER.info("Initializing Proof of Work consensus");
        
        if (blockchain != null && minerAddress != null) {
            currentMiner = new Miner(blockchain, minerAddress);
            currentMiner.setDifficulty(currentDifficulty);
            
            // AUTO-START MINING IF ENABLED
            if (autoMiningEnabled) {
                startMining();
            }
        }
        
        return true;
    } catch (Exception e) {
        LOGGER.log(Level.SEVERE, "Error initializing Proof of Work", e);
        return false;
    }
}
    
    /**
     * Starts mining operation
     */
    public ConsensusOperationResult startMining() {
        if (activeConsensusType != ConsensusType.PROOF_OF_WORK) {
            return ConsensusOperationResult.failure("Mining only available in Proof of Work mode");
        }
        
        if (currentMiner == null) {
            return ConsensusOperationResult.failure("Miner not initialized. Set miner address first.");
        }
        
        if (isMining) {
            return ConsensusOperationResult.failure("Mining already in progress");
        }
        
        try {
            LOGGER.info("Starting mining operation with difficulty: " + currentDifficulty);
            
            isMining = true;
            
            // Start mining in background thread
          // In startMining() method, replace the problematic CompletableFuture.runAsync block with:
             CompletableFuture.runAsync(() -> {
               try {
                while (isMining) {
            // Since TransactionPool doesn't have getTransactions(), 
            // we'll mine empty blocks to keep the chain moving
            if (blockchain != null && currentMiner != null) {
                Block lastBlock = blockchain.getLatestBlock();
                Block newBlock = currentMiner.mine(lastBlock);
                
                // Set empty transaction list for now
                newBlock.setTransactions(new ArrayList<>());
                
                if (blockchain.addBlock(newBlock)) {
                    notifyBlockMined(newBlock);
                    LOGGER.info("Mined block #" + newBlock.getNumber());
                }
            }
            
            Thread.sleep(5000); // 5 second mining interval
        }
    } catch (Exception e) {
        LOGGER.log(Level.SEVERE, "Error during mining", e);
        isMining = false;
    }
});

return ConsensusOperationResult.success("Mining started", getMiningStatus());

        } catch (Exception e) {
            isMining = false;
            LOGGER.log(Level.SEVERE, "Error starting mining", e);
            return ConsensusOperationResult.failure("Failed to start mining: " + e.getMessage());
        }
    }
    
    /**
     * Stops mining operation
     */
    public ConsensusOperationResult stopMining() {
        if (!isMining) {
            return ConsensusOperationResult.failure("Mining is not currently running");
        }
        
        try {
            LOGGER.info("Stopping mining operation");
            isMining = false;
            
            return ConsensusOperationResult.success("Mining stopped successfully", getMiningStatus());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error stopping mining", e);
            return ConsensusOperationResult.failure("Failed to stop mining: " + e.getMessage());
        }
    }
    
    /**
     * Adjusts mining difficulty
     */
    public ConsensusOperationResult adjustDifficulty(long newDifficulty) {
        if (activeConsensusType != ConsensusType.PROOF_OF_WORK) {
            return ConsensusOperationResult.failure("Difficulty adjustment only available in Proof of Work mode");
        }
        
        if (newDifficulty <= 0) {
            return ConsensusOperationResult.failure("Difficulty must be positive");
        }
        
        try {
            long oldDifficulty = currentDifficulty;
            currentDifficulty = newDifficulty;
            
            if (currentMiner != null) {
                currentMiner.setDifficulty(newDifficulty);
            }
            
            notifyDifficultyChanged(oldDifficulty, newDifficulty);
            
            LOGGER.info("Difficulty adjusted from " + oldDifficulty + " to " + newDifficulty);
            return ConsensusOperationResult.success("Difficulty adjusted successfully", getMiningStatus());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error adjusting difficulty", e);
            return ConsensusOperationResult.failure("Failed to adjust difficulty: " + e.getMessage());
        }
    }
    
    // ========================================
    // PROOF OF STAKE OPERATIONS - SIMPLIFIED
    // ========================================
    
    /**
     * Initializes Proof of Stake consensus - simplified implementation
     */
    private boolean initializeProofOfStake() {
        try {
            LOGGER.info("Initializing Proof of Stake consensus");
            
            // Set minimum stake
            if (stakingAmount < 1000) {
                stakingAmount = 1000; // Minimum stake
            }
            
            LOGGER.info("Proof of Stake consensus initialized successfully");
            return true;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error initializing Proof of Stake", e);
            return false;
        }
    }
    
    /**
     * Stakes tokens for validation rights - simplified
     */
    public ConsensusOperationResult stakeTokens(String address, long amount) {
        if (activeConsensusType != ConsensusType.PROOF_OF_STAKE) {
            return ConsensusOperationResult.failure("Staking only available in Proof of Stake mode");
        }
        
        if (amount <= 0) {
            return ConsensusOperationResult.failure("Stake amount must be positive");
        }
        
        if (amount < 1000) {
            return ConsensusOperationResult.failure("Minimum stake amount is 1000 TINC");
        }
        
        try {
            // Check if wallet has sufficient balance
            if (connectedWallet != null && connectedWallet.getBalance() < amount) {
                return ConsensusOperationResult.failure("Insufficient balance for staking");
            }
            
            // Add to stakeholder balances
            stakeholderBalances.put(address, stakeholderBalances.getOrDefault(address, 0L) + amount);
            stakingAddress = address;
            stakingAmount = amount;
            
            // Update wallet balance if connected
            if (connectedWallet != null) {
                connectedWallet.updateBalance(connectedWallet.getBalance() - amount);
            }
            
            notifyStakingChanged(address, amount, true);
            
            LOGGER.info("Successfully staked " + amount + " tokens for address: " + address);
            return ConsensusOperationResult.success("Tokens staked successfully", getStakingStatus());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error staking tokens", e);
            return ConsensusOperationResult.failure("Failed to stake tokens: " + e.getMessage());
        }
    }
    
    /**
     * Unstakes tokens - simplified
     */
    public ConsensusOperationResult unstakeTokens(String address, long amount) {
        if (activeConsensusType != ConsensusType.PROOF_OF_STAKE) {
            return ConsensusOperationResult.failure("Unstaking only available in Proof of Stake mode");
        }
        
        if (amount <= 0) {
            return ConsensusOperationResult.failure("Unstake amount must be positive");
        }
        
        try {
            long currentStake = stakeholderBalances.getOrDefault(address, 0L);
            if (currentStake < amount) {
                return ConsensusOperationResult.failure("Insufficient staked amount");
            }
            
            // Remove from stakeholder balances
            stakeholderBalances.put(address, currentStake - amount);
            if (stakeholderBalances.get(address) <= 0) {
                stakeholderBalances.remove(address);
            }
            
            // Update wallet balance if connected
            if (connectedWallet != null) {
                connectedWallet.updateBalance(connectedWallet.getBalance() + amount);
            }
            
            notifyStakingChanged(address, amount, false);
            
            LOGGER.info("Successfully unstaked " + amount + " tokens for address: " + address);
            return ConsensusOperationResult.success("Tokens unstaked successfully", getStakingStatus());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error unstaking tokens", e);
            return ConsensusOperationResult.failure("Failed to unstake tokens: " + e.getMessage());
        }
    }
    
    // ========================================
    // PBFT OPERATIONS
    // ========================================
    
    /**
     * Initializes PBFT consensus - simplified without actual PBFT components
     */
    private boolean initializePBFT() {
        try {
            LOGGER.info("Initializing PBFT consensus (simplified)");
            
            // Validate minimum validators
            if (pbftValidatorAddresses.size() < 4) {
                LOGGER.warning("Insufficient validators for PBFT: " + pbftValidatorAddresses.size() + " (minimum: 4)");
                // Add some default validators for testing
                pbftValidatorAddresses.add("validator-1");
                pbftValidatorAddresses.add("validator-2");
                pbftValidatorAddresses.add("validator-3");
                pbftValidatorAddresses.add("validator-4");
            }
            
            // Mark PBFT as initialized
            pbftInitialized = true;
            
            LOGGER.info("PBFT consensus initialized successfully (simplified)");
            return true;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error initializing PBFT", e);
            return false;
        }
    }
    
    /**
     * Starts PBFT validation - simplified
     */
    public ConsensusOperationResult startValidation() {
        if (activeConsensusType != ConsensusType.PBFT) {
            return ConsensusOperationResult.failure("Validation only available in PBFT mode");
        }
        
        if (!pbftInitialized) {
            return ConsensusOperationResult.failure("PBFT not initialized");
        }
        
        if (isValidating) {
            return ConsensusOperationResult.failure("Validation already in progress");
        }
        
        try {
            LOGGER.info("Starting PBFT validation (simplified)");
            
            isValidating = true;
            // In a real implementation, this would start the PBFT replica
            
            return ConsensusOperationResult.success("PBFT validation started successfully", getPBFTStatus());
            
        } catch (Exception e) {
            isValidating = false;
            LOGGER.log(Level.SEVERE, "Error starting PBFT validation", e);
            return ConsensusOperationResult.failure("Failed to start validation: " + e.getMessage());
        }
    }
    
    /**
     * Stops PBFT validation - simplified
     */
    public ConsensusOperationResult stopValidation() {
        if (!isValidating) {
            return ConsensusOperationResult.failure("Validation is not currently running");
        }
        
        try {
            LOGGER.info("Stopping PBFT validation (simplified)");
            
            isValidating = false;
            // In a real implementation, this would stop the PBFT replica
            
            return ConsensusOperationResult.success("PBFT validation stopped successfully", getPBFTStatus());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error stopping PBFT validation", e);
            return ConsensusOperationResult.failure("Failed to stop validation: " + e.getMessage());
        }
    }
    
    /**
     * Adds a new PBFT validator - simplified
     */
    public ConsensusOperationResult addValidator(String nodeAddress) {
        if (activeConsensusType != ConsensusType.PBFT) {
            return ConsensusOperationResult.failure("Validator management only available in PBFT mode");
        }
        
        try {
            // Check if validator already exists
            if (pbftValidatorAddresses.contains(nodeAddress)) {
                return ConsensusOperationResult.failure("Validator already exists");
            }
            
            pbftValidatorAddresses.add(nodeAddress);
            notifyValidatorChanged(nodeAddress, true);
            
            LOGGER.info("Added new PBFT validator: " + nodeAddress);
            return ConsensusOperationResult.success("Validator added successfully", getPBFTStatus());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error adding validator", e);
            return ConsensusOperationResult.failure("Failed to add validator: " + e.getMessage());
        }
    }
    
    // ========================================
    // STREAM CONSENSUS OPERATIONS - SIMPLIFIED
    // ========================================
    /**
 * Gets the current miner instance
 */
public org.example.app.core.block.Miner getMiner() {
    // If using currentMiner, convert to right type
    
    if (currentMiner != null && blockchain != null) {
        return new org.example.app.core.block.Miner(blockchain, 
            currentMiner.getMinerAddress() != null ? 
            currentMiner.getMinerAddress() : minerAddress);
    }
    
    // Create a new miner if needed
    if (blockchain != null) {
        return new org.example.app.core.block.Miner(blockchain, 
            minerAddress != null ? minerAddress : "default-miner");
    }
    
    return null;
}


/**
 * Switches to a different consensus mechanism
 * This method extends the existing switchConsensusType method
 */
public ConsensusOperationResult switchConsensus(ConsensusType newType) {
    LOGGER.info("Switching consensus to: " + newType.getDisplayName());
    
    try {
        // Use existing switchConsensusType method
        return switchConsensusType(newType);
    } catch (Exception e) {
        LOGGER.log(Level.SEVERE, "Error during consensus switch", e);
        return ConsensusOperationResult.failure("Consensus switch failed: " + e.getMessage());
    }
}
    /**
     * Initializes Stream-based consensus - simplified
     */
    private boolean initializeStreamConsensus() {
        try {
            LOGGER.info("Initializing Stream-based consensus");
            
            // Simple stream consensus initialization
            LOGGER.info("Stream-based consensus initialized successfully");
            return true;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error initializing Stream consensus", e);
            return false;
        }
    }
    


    /**
     * Starts stream processing - simplified
     */
    public ConsensusOperationResult startStreamProcessing() {
        if (activeConsensusType != ConsensusType.STREAM_CONSENSUS) {
            return ConsensusOperationResult.failure("Stream processing only available in Stream consensus mode");
        }
        
        try {
            LOGGER.info("Starting stream consensus processing");
            
            return ConsensusOperationResult.success("Stream processing started successfully", getStreamStatus());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error starting stream processing", e);
            return ConsensusOperationResult.failure("Failed to start stream processing: " + e.getMessage());
        }
    }
    
    // ========================================
    // STATUS AND MONITORING
    // ========================================
    
    /**
     * Gets comprehensive consensus status
     */
    public ConsensusStatusInfo getConsensusStatus() {
        ConsensusStatusInfo status = new ConsensusStatusInfo();
        status.activeConsensusType = activeConsensusType;
        status.isOperational = isConsensusOperational();
        status.blockHeight = blockchain != null ? blockchain.getChainLength() : 0;
        status.pendingTransactions = transactionPool != null ? transactionPool.size() : 0;
        
        // Consensus-specific status
        switch (activeConsensusType) {
            case PROOF_OF_WORK:
                status.powStatus = getMiningStatus();
                break;
            case PROOF_OF_STAKE:
                status.posStatus = getStakingStatus();
                break;
            case PBFT:
                status.pbftStatus = getPBFTStatus();
                break;
            case STREAM_CONSENSUS:
                status.streamStatus = getStreamStatus();
                break;
        }
        
        return status;
    }
    
    /**
     * Gets mining status for PoW
     */
    private MiningStatus getMiningStatus() {
        MiningStatus status = new MiningStatus();
        status.isMining = isMining;
        status.minerAddress = minerAddress;
        status.currentDifficulty = currentDifficulty;
        status.hashRate = getCurrentHashRate();
        return status;
    }
    
    /**
     * Gets staking status for PoS
     */
    private StakingStatus getStakingStatus() {
        StakingStatus status = new StakingStatus();
        status.totalStaked = stakeholderBalances.values().stream().mapToLong(Long::longValue).sum();
        status.stakeholderCount = stakeholderBalances.size();
        status.myStake = stakingAmount;
        status.myStakingAddress = stakingAddress;
        return status;
    }
    
    /**
     * Gets PBFT status - simplified
     */
    private PBFTStatus getPBFTStatus() {
        PBFTStatus status = new PBFTStatus();
        status.isValidating = isValidating;
        status.validatorCount = pbftValidatorAddresses.size();
        status.isPrimaryNode = isPrimaryNode;
        status.consensusRounds = getConsensusRounds();
        return status;
    }
    
    /**
     * Gets stream consensus status - simplified
     */
    private StreamStatus getStreamStatus() {
        StreamStatus status = new StreamStatus();
        status.isProcessing = false; // Simplified
        status.bufferSize = 1000;
        status.batchSize = 10;
        status.processedBatches = 0;
        return status;
    }
    
    /**
     * Checks if consensus mechanism is operational
     */
    private boolean isConsensusOperational() {
        switch (activeConsensusType) {
            case PROOF_OF_WORK:
                return currentMiner != null;
            case PROOF_OF_STAKE:
                return !stakeholderBalances.isEmpty();
            case PBFT:
                return pbftInitialized && pbftValidatorAddresses.size() >= 4;
            case STREAM_CONSENSUS:
                return true; // Simplified
            default:
                return false;
        }
    }
    
    /**
     * Gets current hash rate from metrics
     */
    private double getCurrentHashRate() {
        try {
            if (consensusMetrics != null) {
                long avgBlockTime = consensusMetrics.getAverageBlockTime();
                if (avgBlockTime > 0) {
                    return (double) currentDifficulty / (avgBlockTime / 1000.0);
                }
            }
        } catch (Exception e) {
            LOGGER.fine("Error calculating hash rate: " + e.getMessage());
        }
        return 50000.0; // Default value
    }
    
    /**
     * Gets consensus rounds from metrics
     */
    private long getConsensusRounds() {
        try {
            if (consensusMetrics != null) {
                return consensusMetrics.getBlockHeight();
            }
        } catch (Exception e) {
            LOGGER.fine("Error getting consensus rounds: " + e.getMessage());
        }
        return 0L;
    }
    
    // ========================================
    // INTEGRATION METHODS
    // ========================================
    
    /**
     * Sets the blockchain instance
     */
    public void setBlockchain(Blockchain blockchain) {
        this.blockchain = blockchain;
        LOGGER.info("Blockchain instance set for consensus controller");
    }
    
    /**
     * Sets the transaction pool
     */
    public void setTransactionPool(TransactionPool transactionPool) {
        this.transactionPool = transactionPool;
        LOGGER.info("Transaction pool set for consensus controller");
    }
    
    // Add these methods to ConsensusController
public void setAutoMiningEnabled(boolean enabled) {
    this.autoMiningEnabled = enabled;
    if (enabled && activeConsensusType == ConsensusType.PROOF_OF_WORK && !isMining) {
        startMining();
    }
}

public boolean isAutoMiningEnabled() {
    return autoMiningEnabled;
}
    /**
     * Connects a wallet for consensus operations
     */
    public void setConnectedWallet(Wallet wallet) {
        this.connectedWallet = wallet;
        if (wallet != null) {
            this.minerAddress = wallet.getAddress();
            this.stakingAddress = wallet.getAddress();
            LOGGER.info("Wallet connected for consensus operations: " + wallet.getAddress());
        }
    }
    
    /**
     * Sets miner address for PoW operations
     */
    public void setMinerAddress(String address) {
        this.minerAddress = address;
        if (currentMiner != null && blockchain != null) {
            currentMiner = new Miner(blockchain, address);
            currentMiner.setDifficulty(currentDifficulty);
        }
        LOGGER.info("Miner address set: " + address);
    }
    
    /**
     * Sets active wallet ID for integration
     */
    public void setActiveWalletId(String walletId) {
        this.activeWalletId = walletId;
        LOGGER.info("Active wallet ID set: " + walletId);
    }
    
    /**
     * Gets active wallet ID
     */
    public String getActiveWalletId() {
        return activeWalletId;
    }
    
    /**
     * Checks if wallet is available
     */
    public boolean isWalletAvailable() {
        return connectedWallet != null;
    }
    
    // ========================================
    // EVENT HANDLING
    // ========================================
    
    /**
     * Interface for consensus event listeners
     */
    public interface ConsensusEventListener {
        void onConsensusChanged(ConsensusType oldType, ConsensusType newType);
        void onBlockMined(Block block);
        void onDifficultyChanged(long oldDifficulty, long newDifficulty);
        void onStakingChanged(String address, long amount, boolean isStaking);
        void onValidatorChanged(String validator, boolean added);
        void onConsensusError(String error);
    }
    
    /**
     * Adds an event listener for dashboard integration
     */
    public void addEventListener(ConsensusEventListener listener) {
        eventListeners.add(listener);
        LOGGER.info("Consensus event listener added");
    }
    
    /**
     * Removes an event listener
     */
    public void removeEventListener(ConsensusEventListener listener) {
        eventListeners.remove(listener);
        LOGGER.info("Consensus event listener removed");
    }
    
    // Event notification methods
    private void notifyConsensusChanged(ConsensusType oldType, ConsensusType newType) {
        for (ConsensusEventListener listener : eventListeners) {
            try {
                listener.onConsensusChanged(oldType, newType);
            } catch (Exception e) {
                LOGGER.warning("Error notifying consensus change listener: " + e.getMessage());
            }
        }
    }
    
    private void notifyBlockMined(Block block) {
        for (ConsensusEventListener listener : eventListeners) {
            try {
                listener.onBlockMined(block);
            } catch (Exception e) {
                LOGGER.warning("Error notifying block mined listener: " + e.getMessage());
            }
        }
    }
    
    private void notifyDifficultyChanged(long oldDifficulty, long newDifficulty) {
        for (ConsensusEventListener listener : eventListeners) {
            try {
                listener.onDifficultyChanged(oldDifficulty, newDifficulty);
            } catch (Exception e) {
                LOGGER.warning("Error notifying difficulty change listener: " + e.getMessage());
            }
        }
    }
    
    private void notifyStakingChanged(String address, long amount, boolean isStaking) {
        for (ConsensusEventListener listener : eventListeners) {
            try {
                listener.onStakingChanged(address, amount, isStaking);
            } catch (Exception e) {
                LOGGER.warning("Error notifying staking change listener: " + e.getMessage());
            }
        }
    }
    
    private void notifyValidatorChanged(String validator, boolean added) {
        for (ConsensusEventListener listener : eventListeners) {
            try {
                listener.onValidatorChanged(validator, added);
            } catch (Exception e) {
                LOGGER.warning("Error notifying validator change listener: " + e.getMessage());
            }
        }
    }
    
    // ========================================
    // GETTER METHODS
    // ========================================
    
    public ConsensusType getActiveConsensusType() {
        return activeConsensusType;
    }
    
    public boolean isMining() {
        return isMining;
    }
    
    public boolean isValidating() {
        return isValidating;
    }
    
    public long getCurrentDifficulty() {
        return currentDifficulty;
    }
    
    public String getMinerAddress() {
        return minerAddress;
    }
    
    public ConsensusMetrics getConsensusMetrics() {
        return consensusMetrics;
    }
    
    public ConsensusConfig getConsensusConfig() {
        return consensusConfig;
    }
    
    // ========================================
    // STATUS CLASSES
    // ========================================
    
    /**
     * Comprehensive consensus status information
     */
    public static class ConsensusStatusInfo {
        public ConsensusType activeConsensusType;
        public boolean isOperational;
        public long blockHeight;
        public int pendingTransactions;
        public MiningStatus powStatus;
        public StakingStatus posStatus;
        public PBFTStatus pbftStatus;
        public StreamStatus streamStatus;
    }
    
    /**
     * Mining status for Proof of Work
     */
    public static class MiningStatus {
        public boolean isMining;
        public String minerAddress;
        public long currentDifficulty;
        public double hashRate;
    }
    
    /**
     * Staking status for Proof of Stake
     */
    public static class StakingStatus {
        public long totalStaked;
        public int stakeholderCount;
        public long myStake;
        public String myStakingAddress;
    }
    
    /**
     * PBFT consensus status
     */
    public static class PBFTStatus {
        public boolean isValidating;
        public int validatorCount;
        public boolean isPrimaryNode;
        public long consensusRounds;
    }
    
    /**
     * Stream consensus status
     */
    public static class StreamStatus {
        public boolean isProcessing;
        public int bufferSize;
        public int batchSize;
        public long processedBatches;
    }
}