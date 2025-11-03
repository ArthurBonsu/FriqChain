package org.example.app.core.config;
import org.example.app.core.ui.consensus.ConsensusController.ConsensusType;
import org.example.app.core.ui.consensus.ConsensusManager.ConsensusPolicy;

import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Enhanced ConsensusConfig using Builder pattern as required by the codebase
 * 
 * Configuration for consensus mechanisms including:
 * - PBFT settings with enhanced parameters
 * - Proof of Work parameters with dynamic difficulty
 * - Proof of Stake configuration with staking rewards
 * - Stream consensus options with processing parameters
 * - Policy-based configuration management
 * - Monitoring and optimization settings
 * - Wallet integration parameters
 */
public class ConsensusConfig {
    private static final Logger LOGGER = Logger.getLogger(ConsensusConfig.class.getName());
    
    // ========================================
    // PBFT Configuration
    // ========================================
    private final int pbftViewTimeout;
    private final int pbftMaxFaultyNodes;
    private final boolean pbftByzantineFaultTolerance;
    private final int pbftCheckpointInterval;
    private final int pbftMinValidators;
    private final double pbftByzantineThreshold;
    private final long pbftConsensusTimeout;
    
    // ========================================
    // PoW Configuration
    // ========================================
    private final long powTargetBlockTime;
    private final long powInitialDifficulty;
    private final double powDifficultyAdjustmentFactor;
    private final int powRetargetInterval;
    private final long powMaxDifficulty;
    private final long powMinDifficulty;
    private final boolean powDynamicDifficultyEnabled;
    private final double powHashRateTarget;
    
    // ========================================
    // PoS Configuration
    // ========================================
    private final long posMinimumStake;
    private final double posSlashingPenalty;
    private final int posValidatorSetSize;
    private final long posEpochLength;
    private final double posRewardRate;
    private final long posUnbondingPeriod;
    private final double posInflationRate;
    private final boolean posSlashingEnabled;
    
    // ========================================
    // Stream Consensus Configuration
    // ========================================
    private final int streamBufferSize;
    private final int streamBatchSize;
    private final long streamProcessingInterval;
    private final boolean streamOrderedProcessing;
    private final int streamMaxConcurrentBatches;
    private final long streamBatchTimeout;
    private final boolean streamCompressionEnabled;
    
    // ========================================
    // General Configuration
    // ========================================
    private final int maxBlockSize;
    private final int maxTransactionsPerBlock;
    private final long blockTimestamp;
    private final boolean enableMetrics;
    private final ConsensusType defaultConsensusType;
    private final ConsensusPolicy defaultConsensusPolicy;
    
    // ========================================
    // Monitoring and Optimization Configuration
    // ========================================
    private final boolean healthMonitoringEnabled;
    private final long healthCheckInterval;
    private final boolean autoOptimizationEnabled;
    private final long optimizationInterval;
    private final double healthThreshold;
    private final boolean realTimeMetricsEnabled;
    
    // ========================================
    // Network and Integration Configuration
    // ========================================
    private final boolean dashboardIntegrationEnabled;
    private final boolean walletIntegrationEnabled;
    private final boolean smartContractIntegrationEnabled;
    private final long networkSyncInterval;
    private final int maxPeerConnections;
    
    // ========================================
    // Advanced Configuration Maps
    // ========================================
    private final Map<ConsensusType, Map<String, Object>> consensusSpecificParams;
    private final Map<String, String> customProperties;
    
    // Private constructor - only accessible via Builder
    private ConsensusConfig(Builder builder) {
        // PBFT settings
        this.pbftViewTimeout = builder.pbftViewTimeout;
        this.pbftMaxFaultyNodes = builder.pbftMaxFaultyNodes;
        this.pbftByzantineFaultTolerance = builder.pbftByzantineFaultTolerance;
        this.pbftCheckpointInterval = builder.pbftCheckpointInterval;
        this.pbftMinValidators = builder.pbftMinValidators;
        this.pbftByzantineThreshold = builder.pbftByzantineThreshold;
        this.pbftConsensusTimeout = builder.pbftConsensusTimeout;
        
        // PoW settings
        this.powTargetBlockTime = builder.powTargetBlockTime;
        this.powInitialDifficulty = builder.powInitialDifficulty;
        this.powDifficultyAdjustmentFactor = builder.powDifficultyAdjustmentFactor;
        this.powRetargetInterval = builder.powRetargetInterval;
        this.powMaxDifficulty = builder.powMaxDifficulty;
        this.powMinDifficulty = builder.powMinDifficulty;
        this.powDynamicDifficultyEnabled = builder.powDynamicDifficultyEnabled;
        this.powHashRateTarget = builder.powHashRateTarget;
        
        // PoS settings
        this.posMinimumStake = builder.posMinimumStake;
        this.posSlashingPenalty = builder.posSlashingPenalty;
        this.posValidatorSetSize = builder.posValidatorSetSize;
        this.posEpochLength = builder.posEpochLength;
        this.posRewardRate = builder.posRewardRate;
        this.posUnbondingPeriod = builder.posUnbondingPeriod;
        this.posInflationRate = builder.posInflationRate;
        this.posSlashingEnabled = builder.posSlashingEnabled;
        
        // Stream settings
        this.streamBufferSize = builder.streamBufferSize;
        this.streamBatchSize = builder.streamBatchSize;
        this.streamProcessingInterval = builder.streamProcessingInterval;
        this.streamOrderedProcessing = builder.streamOrderedProcessing;
        this.streamMaxConcurrentBatches = builder.streamMaxConcurrentBatches;
        this.streamBatchTimeout = builder.streamBatchTimeout;
        this.streamCompressionEnabled = builder.streamCompressionEnabled;
        
        // General settings
        this.maxBlockSize = builder.maxBlockSize;
        this.maxTransactionsPerBlock = builder.maxTransactionsPerBlock;
        this.blockTimestamp = builder.blockTimestamp;
        this.enableMetrics = builder.enableMetrics;
        this.defaultConsensusType = builder.defaultConsensusType;
        this.defaultConsensusPolicy = builder.defaultConsensusPolicy;
        
        // Monitoring and optimization settings
        this.healthMonitoringEnabled = builder.healthMonitoringEnabled;
        this.healthCheckInterval = builder.healthCheckInterval;
        this.autoOptimizationEnabled = builder.autoOptimizationEnabled;
        this.optimizationInterval = builder.optimizationInterval;
        this.healthThreshold = builder.healthThreshold;
        this.realTimeMetricsEnabled = builder.realTimeMetricsEnabled;
        
        // Network and integration settings
        this.dashboardIntegrationEnabled = builder.dashboardIntegrationEnabled;
        this.walletIntegrationEnabled = builder.walletIntegrationEnabled;
        this.smartContractIntegrationEnabled = builder.smartContractIntegrationEnabled;
        this.networkSyncInterval = builder.networkSyncInterval;
        this.maxPeerConnections = builder.maxPeerConnections;
        
        // Advanced configuration
        this.consensusSpecificParams = new HashMap<>(builder.consensusSpecificParams);
        this.customProperties = new HashMap<>(builder.customProperties);
    }
    
    // Static method to create default config
    public static ConsensusConfig createDefault() {
        return new Builder().build();
    }
    
    // Static method to get a new builder
    public static Builder builder() {
        return new Builder();
    }
    
    // Static method to load from properties file
    public static ConsensusConfig loadFromFile(String configPath) {
        try {
            Properties props = new Properties();
            props.load(new FileInputStream(configPath));
            return createFromProperties(props);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load config from file: " + configPath, e);
            return createDefault();
        }
    }
    
    // Static method to create from properties
    public static ConsensusConfig createFromProperties(Properties props) {
        Builder builder = new Builder();
        
        // Load PBFT properties
        builder.pbftViewTimeout(getIntProperty(props, "pbft.view.timeout", 10000));
        builder.pbftMaxFaultyNodes(getIntProperty(props, "pbft.max.faulty.nodes", 1));
        builder.pbftByzantineFaultTolerance(getBooleanProperty(props, "pbft.byzantine.fault.tolerance", true));
        builder.pbftCheckpointInterval(getIntProperty(props, "pbft.checkpoint.interval", 100));
        
        // Load PoW properties
        builder.powTargetBlockTime(getLongProperty(props, "pow.target.block.time", 15000L));
        builder.powInitialDifficulty(getLongProperty(props, "pow.initial.difficulty", 1000L));
        builder.powDifficultyAdjustmentFactor(getDoubleProperty(props, "pow.difficulty.adjustment.factor", 2.0));
        builder.powRetargetInterval(getIntProperty(props, "pow.retarget.interval", 10));
        
        // Load PoS properties
        builder.posMinimumStake(getLongProperty(props, "pos.minimum.stake", 1000L));
        builder.posSlashingPenalty(getDoubleProperty(props, "pos.slashing.penalty", 0.05));
        builder.posValidatorSetSize(getIntProperty(props, "pos.validator.set.size", 21));
        builder.posEpochLength(getLongProperty(props, "pos.epoch.length", 100L));
        
        // Load Stream properties
        builder.streamBufferSize(getIntProperty(props, "stream.buffer.size", 1000));
        builder.streamBatchSize(getIntProperty(props, "stream.batch.size", 10));
        builder.streamProcessingInterval(getLongProperty(props, "stream.processing.interval", 500L));
        builder.streamOrderedProcessing(getBooleanProperty(props, "stream.ordered.processing", true));
        
        // Load general properties
        builder.maxBlockSize(getIntProperty(props, "general.max.block.size", 1048576));
        builder.maxTransactionsPerBlock(getIntProperty(props, "general.max.transactions.per.block", 1000));
        builder.enableMetrics(getBooleanProperty(props, "general.enable.metrics", true));
        
        // Load consensus type and policy
        String consensusType = props.getProperty("consensus.default.type", "PROOF_OF_WORK");
        try {
            builder.defaultConsensusType(ConsensusType.valueOf(consensusType));
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Invalid consensus type in config: " + consensusType + ", using default");
        }
        
        String consensusPolicy = props.getProperty("consensus.default.policy", "BALANCED");
        try {
            builder.defaultConsensusPolicy(ConsensusPolicy.valueOf(consensusPolicy));
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Invalid consensus policy in config: " + consensusPolicy + ", using default");
        }
        
        return builder.build();
    }
    
    // Utility methods for property parsing
    private static int getIntProperty(Properties props, String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private static long getLongProperty(Properties props, String key, long defaultValue) {
        try {
            return Long.parseLong(props.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private static double getDoubleProperty(Properties props, String key, double defaultValue) {
        try {
            return Double.parseDouble(props.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private static boolean getBooleanProperty(Properties props, String key, boolean defaultValue) {
        return Boolean.parseBoolean(props.getProperty(key, String.valueOf(defaultValue)));
    }
    
    // ========================================
    // BUILDER CLASS
    // ========================================
    
    public static class Builder {
        // PBFT Configuration with enhanced defaults
        private int pbftViewTimeout = 10000; // 10 seconds
        private int pbftMaxFaultyNodes = 1;
        private boolean pbftByzantineFaultTolerance = true;
        private int pbftCheckpointInterval = 100;
        private int pbftMinValidators = 4;
        private double pbftByzantineThreshold = 0.33; // 33% Byzantine fault tolerance
        private long pbftConsensusTimeout = 30000; // 30 seconds
        
        // PoW Configuration with enhanced defaults
        private long powTargetBlockTime = 15000; // 15 seconds
        private long powInitialDifficulty = 1000;
        private double powDifficultyAdjustmentFactor = 2.0;
        private int powRetargetInterval = 10; // blocks
        private long powMaxDifficulty = 1000000000L;
        private long powMinDifficulty = 100L;
        private boolean powDynamicDifficultyEnabled = true;
        private double powHashRateTarget = 100000.0; // Hash/s
        
        // PoS Configuration with enhanced defaults
        private long posMinimumStake = 1000;
        private double posSlashingPenalty = 0.05; // 5%
        private int posValidatorSetSize = 21;
        private long posEpochLength = 100; // blocks
        private double posRewardRate = 0.08; // 8% annual reward
        private long posUnbondingPeriod = 604800; // 1 week in seconds
        private double posInflationRate = 0.03; // 3% annual inflation
        private boolean posSlashingEnabled = true;
        
        // Stream Consensus Configuration with enhanced defaults
        private int streamBufferSize = 1000;
        private int streamBatchSize = 10;
        private long streamProcessingInterval = 500; // milliseconds
        private boolean streamOrderedProcessing = true;
        private int streamMaxConcurrentBatches = 5;
        private long streamBatchTimeout = 10000; // 10 seconds
        private boolean streamCompressionEnabled = false;
        
        // General Configuration with enhanced defaults
        private int maxBlockSize = 1024 * 1024; // 1MB
        private int maxTransactionsPerBlock = 1000;
        private long blockTimestamp = System.currentTimeMillis();
        private boolean enableMetrics = true;
        private ConsensusType defaultConsensusType = ConsensusType.PROOF_OF_WORK;
        private ConsensusPolicy defaultConsensusPolicy = ConsensusPolicy.BALANCED;
        
        // Monitoring and optimization defaults
        private boolean healthMonitoringEnabled = true;
        private long healthCheckInterval = 10000; // 10 seconds
        private boolean autoOptimizationEnabled = true;
        private long optimizationInterval = 300000; // 5 minutes
        private double healthThreshold = 0.7; // 70% health threshold
        private boolean realTimeMetricsEnabled = true;
        
        // Network and integration defaults
        private boolean dashboardIntegrationEnabled = true;
        private boolean walletIntegrationEnabled = true;
        private boolean smartContractIntegrationEnabled = true;
        private long networkSyncInterval = 30000; // 30 seconds
        private int maxPeerConnections = 50;
        
        // Advanced configuration
        private Map<ConsensusType, Map<String, Object>> consensusSpecificParams = new HashMap<>();
        private Map<String, String> customProperties = new HashMap<>();
        
        public Builder() {
            // Initialize consensus-specific parameters
            initializeConsensusSpecificParams();
        }
        
        private void initializeConsensusSpecificParams() {
            // Initialize PoW specific parameters
            Map<String, Object> powParams = new HashMap<>();
            powParams.put("memory_hard_mining", false);
            powParams.put("asic_resistant", false);
            powParams.put("mining_pool_support", true);
            consensusSpecificParams.put(ConsensusType.PROOF_OF_WORK, powParams);
            
            // Initialize PoS specific parameters
            Map<String, Object> posParams = new HashMap<>();
            posParams.put("delegation_enabled", true);
            posParams.put("max_validators", 100);
            posParams.put("reward_distribution_mode", "proportional");
            consensusSpecificParams.put(ConsensusType.PROOF_OF_STAKE, posParams);
            
            // Initialize PBFT specific parameters
            Map<String, Object> pbftParams = new HashMap<>();
            pbftParams.put("fast_byzantine_detection", true);
            pbftParams.put("view_change_optimization", true);
            pbftParams.put("checkpoint_optimization", true);
            consensusSpecificParams.put(ConsensusType.PBFT, pbftParams);
            
            // Initialize Stream specific parameters
            Map<String, Object> streamParams = new HashMap<>();
            streamParams.put("parallel_processing", true);
            streamParams.put("adaptive_batching", true);
            streamParams.put("flow_control", true);
            consensusSpecificParams.put(ConsensusType.STREAM_CONSENSUS, streamParams);
        }
        
        // PBFT Builder methods with enhanced options
        public Builder pbftViewTimeout(int timeout) {
            this.pbftViewTimeout = timeout;
            return this;
        }
        
        public Builder pbftMaxFaultyNodes(int maxFaultyNodes) {
            this.pbftMaxFaultyNodes = maxFaultyNodes;
            return this;
        }
        
        public Builder pbftByzantineFaultTolerance(boolean enable) {
            this.pbftByzantineFaultTolerance = enable;
            return this;
        }
        
        public Builder pbftCheckpointInterval(int interval) {
            this.pbftCheckpointInterval = interval;
            return this;
        }
        
        public Builder pbftMinValidators(int minValidators) {
            this.pbftMinValidators = minValidators;
            return this;
        }
        
        public Builder pbftByzantineThreshold(double threshold) {
            this.pbftByzantineThreshold = threshold;
            return this;
        }
        
        public Builder pbftConsensusTimeout(long timeout) {
            this.pbftConsensusTimeout = timeout;
            return this;
        }
        
        // PoW Builder methods with enhanced options
        public Builder powTargetBlockTime(long blockTime) {
            this.powTargetBlockTime = blockTime;
            return this;
        }
        
        public Builder powInitialDifficulty(long difficulty) {
            this.powInitialDifficulty = difficulty;
            return this;
        }
        
        public Builder powDifficultyAdjustmentFactor(double factor) {
            this.powDifficultyAdjustmentFactor = factor;
            return this;
        }
        
        public Builder powRetargetInterval(int interval) {
            this.powRetargetInterval = interval;
            return this;
        }
        
        public Builder powMaxDifficulty(long maxDifficulty) {
            this.powMaxDifficulty = maxDifficulty;
            return this;
        }
        
        public Builder powMinDifficulty(long minDifficulty) {
            this.powMinDifficulty = minDifficulty;
            return this;
        }
        
        public Builder powDynamicDifficultyEnabled(boolean enabled) {
            this.powDynamicDifficultyEnabled = enabled;
            return this;
        }
        
        public Builder powHashRateTarget(double hashRateTarget) {
            this.powHashRateTarget = hashRateTarget;
            return this;
        }
        
        // PoS Builder methods with enhanced options
        public Builder posMinimumStake(long minimumStake) {
            this.posMinimumStake = minimumStake;
            return this;
        }
        
        public Builder posSlashingPenalty(double penalty) {
            this.posSlashingPenalty = penalty;
            return this;
        }
        
        public Builder posValidatorSetSize(int size) {
            this.posValidatorSetSize = size;
            return this;
        }
        
        public Builder posEpochLength(long length) {
            this.posEpochLength = length;
            return this;
        }
        
        public Builder posRewardRate(double rewardRate) {
            this.posRewardRate = rewardRate;
            return this;
        }
        
        public Builder posUnbondingPeriod(long unbondingPeriod) {
            this.posUnbondingPeriod = unbondingPeriod;
            return this;
        }
        
        public Builder posInflationRate(double inflationRate) {
            this.posInflationRate = inflationRate;
            return this;
        }
        
        public Builder posSlashingEnabled(boolean enabled) {
            this.posSlashingEnabled = enabled;
            return this;
        }
        
        // Stream Builder methods with enhanced options
        public Builder streamBufferSize(int bufferSize) {
            this.streamBufferSize = bufferSize;
            return this;
        }
        
        public Builder streamBatchSize(int batchSize) {
            this.streamBatchSize = batchSize;
            return this;
        }
        
        public Builder streamProcessingInterval(long interval) {
            this.streamProcessingInterval = interval;
            return this;
        }
        
        public Builder streamOrderedProcessing(boolean ordered) {
            this.streamOrderedProcessing = ordered;
            return this;
        }
        
        public Builder streamMaxConcurrentBatches(int maxBatches) {
            this.streamMaxConcurrentBatches = maxBatches;
            return this;
        }
        
        public Builder streamBatchTimeout(long timeout) {
            this.streamBatchTimeout = timeout;
            return this;
        }
        
        public Builder streamCompressionEnabled(boolean enabled) {
            this.streamCompressionEnabled = enabled;
            return this;
        }
        
        // General Builder methods with enhanced options
        public Builder maxBlockSize(int blockSize) {
            this.maxBlockSize = blockSize;
            return this;
        }
        
        public Builder maxTransactionsPerBlock(int maxTransactions) {
            this.maxTransactionsPerBlock = maxTransactions;
            return this;
        }
        
        public Builder blockTimestamp(long timestamp) {
            this.blockTimestamp = timestamp;
            return this;
        }
        
        public Builder enableMetrics(boolean enable) {
            this.enableMetrics = enable;
            return this;
        }
        
        public Builder defaultConsensusType(ConsensusType type) {
            this.defaultConsensusType = type;
            return this;
        }
        
        public Builder defaultConsensusPolicy(ConsensusPolicy policy) {
            this.defaultConsensusPolicy = policy;
            return this;
        }
        
        // Monitoring and optimization builder methods
        public Builder healthMonitoringEnabled(boolean enabled) {
            this.healthMonitoringEnabled = enabled;
            return this;
        }
        
        public Builder healthCheckInterval(long interval) {
            this.healthCheckInterval = interval;
            return this;
        }
        
        public Builder autoOptimizationEnabled(boolean enabled) {
            this.autoOptimizationEnabled = enabled;
            return this;
        }
        
        public Builder optimizationInterval(long interval) {
            this.optimizationInterval = interval;
            return this;
        }
        
        public Builder healthThreshold(double threshold) {
            this.healthThreshold = threshold;
            return this;
        }
        
        public Builder realTimeMetricsEnabled(boolean enabled) {
            this.realTimeMetricsEnabled = enabled;
            return this;
        }
        
        // Network and integration builder methods
        public Builder dashboardIntegrationEnabled(boolean enabled) {
            this.dashboardIntegrationEnabled = enabled;
            return this;
        }
        
        public Builder walletIntegrationEnabled(boolean enabled) {
            this.walletIntegrationEnabled = enabled;
            return this;
        }
        
        public Builder smartContractIntegrationEnabled(boolean enabled) {
            this.smartContractIntegrationEnabled = enabled;
            return this;
        }
        
        public Builder networkSyncInterval(long interval) {
            this.networkSyncInterval = interval;
            return this;
        }
        
        public Builder maxPeerConnections(int maxConnections) {
            this.maxPeerConnections = maxConnections;
            return this;
        }
        
        // Advanced configuration methods
        public Builder addConsensusSpecificParam(ConsensusType type, String key, Object value) {
            consensusSpecificParams.computeIfAbsent(type, k -> new HashMap<>()).put(key, value);
            return this;
        }
        
        public Builder addCustomProperty(String key, String value) {
            this.customProperties.put(key, value);
            return this;
        }
        
        // Build method
        public ConsensusConfig build() {
            return new ConsensusConfig(this);
        }
    }
    
    // ========================================
    // ENHANCED GETTER METHODS
    // ========================================
    
    // PBFT Getters
    public int getPbftViewTimeout() { return pbftViewTimeout; }
    public int getPbftMaxFaultyNodes() { return pbftMaxFaultyNodes; }
    public boolean isPbftByzantineFaultTolerance() { return pbftByzantineFaultTolerance; }
    public int getPbftCheckpointInterval() { return pbftCheckpointInterval; }
    public int getPbftMinValidators() { return pbftMinValidators; }
    public double getPbftByzantineThreshold() { return pbftByzantineThreshold; }
    public long getPbftConsensusTimeout() { return pbftConsensusTimeout; }
    
    // PoW Getters
    public long getPowTargetBlockTime() { return powTargetBlockTime; }
    public long getPowInitialDifficulty() { return powInitialDifficulty; }
    public double getPowDifficultyAdjustmentFactor() { return powDifficultyAdjustmentFactor; }
    public int getPowRetargetInterval() { return powRetargetInterval; }
    public long getPowMaxDifficulty() { return powMaxDifficulty; }
    public long getPowMinDifficulty() { return powMinDifficulty; }
    public boolean isPowDynamicDifficultyEnabled() { return powDynamicDifficultyEnabled; }
    public double getPowHashRateTarget() { return powHashRateTarget; }
    
    // PoS Getters
    public long getPosMinimumStake() { return posMinimumStake; }
    public double getPosSlashingPenalty() { return posSlashingPenalty; }
    public int getPosValidatorSetSize() { return posValidatorSetSize; }
    public long getPosEpochLength() { return posEpochLength; }
    public double getPosRewardRate() { return posRewardRate; }
    public long getPosUnbondingPeriod() { return posUnbondingPeriod; }
    public double getPosInflationRate() { return posInflationRate; }
    public boolean isPosSlashingEnabled() { return posSlashingEnabled; }
    
    // Stream Getters
    public int getStreamBufferSize() { return streamBufferSize; }
    public int getStreamBatchSize() { return streamBatchSize; }
    public long getStreamProcessingInterval() { return streamProcessingInterval; }
    public boolean isStreamOrderedProcessing() { return streamOrderedProcessing; }
    public int getStreamMaxConcurrentBatches() { return streamMaxConcurrentBatches; }
    public long getStreamBatchTimeout() { return streamBatchTimeout; }
    public boolean isStreamCompressionEnabled() { return streamCompressionEnabled; }
    
    // General Getters
    public int getMaxBlockSize() { return maxBlockSize; }
    public int getMaxTransactionsPerBlock() { return maxTransactionsPerBlock; }
    public long getBlockTimestamp() { return blockTimestamp; }
    public boolean isEnableMetrics() { return enableMetrics; }
    public ConsensusType getDefaultConsensusType() { return defaultConsensusType; }
    public ConsensusPolicy getDefaultConsensusPolicy() { return defaultConsensusPolicy; }
    
    // Monitoring and optimization getters
    public boolean isHealthMonitoringEnabled() { return healthMonitoringEnabled; }
    public long getHealthCheckInterval() { return healthCheckInterval; }
    public boolean isAutoOptimizationEnabled() { return autoOptimizationEnabled; }
    public long getOptimizationInterval() { return optimizationInterval; }
    public double getHealthThreshold() { return healthThreshold; }
    public boolean isRealTimeMetricsEnabled() { return realTimeMetricsEnabled; }
    
    // Network and integration getters
    public boolean isDashboardIntegrationEnabled() { return dashboardIntegrationEnabled; }
    public boolean isWalletIntegrationEnabled() { return walletIntegrationEnabled; }
    public boolean isSmartContractIntegrationEnabled() { return smartContractIntegrationEnabled; }
    public long getNetworkSyncInterval() { return networkSyncInterval; }
    public int getMaxPeerConnections() { return maxPeerConnections; }
    
    // Advanced configuration getters
    public Map<String, Object> getConsensusSpecificParams(ConsensusType type) {
        return consensusSpecificParams.getOrDefault(type, new HashMap<>());
    }
    
    public Object getConsensusSpecificParam(ConsensusType type, String key) {
        return consensusSpecificParams.getOrDefault(type, new HashMap<>()).get(key);
    }
    
    public String getCustomProperty(String key) {
        return customProperties.get(key);
    }
    
    public Map<String, String> getAllCustomProperties() {
        return new HashMap<>(customProperties);
    }
    
    // ========================================
    // CONFIGURATION MANAGEMENT METHODS
    // ========================================
    
    /**
     * Saves the current configuration to a properties file
     */
    public void saveToFile(String configPath) throws IOException {
        Properties props = toProperties();
        try (FileOutputStream out = new FileOutputStream(configPath)) {
            props.store(out, "TINC Blockchain Consensus Configuration");
        }
        LOGGER.info("Configuration saved to: " + configPath);
    }
    
    /**
     * Converts configuration to Properties object
     */
    public Properties toProperties() {
        Properties props = new Properties();
        
        // PBFT properties
        props.setProperty("pbft.view.timeout", String.valueOf(pbftViewTimeout));
        props.setProperty("pbft.max.faulty.nodes", String.valueOf(pbftMaxFaultyNodes));
        props.setProperty("pbft.byzantine.fault.tolerance", String.valueOf(pbftByzantineFaultTolerance));
        props.setProperty("pbft.checkpoint.interval", String.valueOf(pbftCheckpointInterval));
        props.setProperty("pbft.min.validators", String.valueOf(pbftMinValidators));
        props.setProperty("pbft.byzantine.threshold", String.valueOf(pbftByzantineThreshold));
        props.setProperty("pbft.consensus.timeout", String.valueOf(pbftConsensusTimeout));
        
        // PoW properties
        props.setProperty("pow.target.block.time", String.valueOf(powTargetBlockTime));
        props.setProperty("pow.initial.difficulty", String.valueOf(powInitialDifficulty));
        props.setProperty("pow.difficulty.adjustment.factor", String.valueOf(powDifficultyAdjustmentFactor));
        props.setProperty("pow.retarget.interval", String.valueOf(powRetargetInterval));
        props.setProperty("pow.max.difficulty", String.valueOf(powMaxDifficulty));
        props.setProperty("pow.min.difficulty", String.valueOf(powMinDifficulty));
        props.setProperty("pow.dynamic.difficulty.enabled", String.valueOf(powDynamicDifficultyEnabled));
        props.setProperty("pow.hash.rate.target", String.valueOf(powHashRateTarget));
        
        // PoS properties
        props.setProperty("pos.minimum.stake", String.valueOf(posMinimumStake));
        props.setProperty("pos.slashing.penalty", String.valueOf(posSlashingPenalty));
        props.setProperty("pos.validator.set.size", String.valueOf(posValidatorSetSize));
        props.setProperty("pos.epoch.length", String.valueOf(posEpochLength));
        props.setProperty("pos.reward.rate", String.valueOf(posRewardRate));
        props.setProperty("pos.unbonding.period", String.valueOf(posUnbondingPeriod));
        props.setProperty("pos.inflation.rate", String.valueOf(posInflationRate));
        props.setProperty("pos.slashing.enabled", String.valueOf(posSlashingEnabled));
        
        // Stream properties
        props.setProperty("stream.buffer.size", String.valueOf(streamBufferSize));
        props.setProperty("stream.batch.size", String.valueOf(streamBatchSize));
        props.setProperty("stream.processing.interval", String.valueOf(streamProcessingInterval));
        props.setProperty("stream.ordered.processing", String.valueOf(streamOrderedProcessing));
        props.setProperty("stream.max.concurrent.batches", String.valueOf(streamMaxConcurrentBatches));
        props.setProperty("stream.batch.timeout", String.valueOf(streamBatchTimeout));
        props.setProperty("stream.compression.enabled", String.valueOf(streamCompressionEnabled));
        
        // General properties
        props.setProperty("general.max.block.size", String.valueOf(maxBlockSize));
        props.setProperty("general.max.transactions.per.block", String.valueOf(maxTransactionsPerBlock));
        props.setProperty("general.enable.metrics", String.valueOf(enableMetrics));
        props.setProperty("consensus.default.type", defaultConsensusType.name());
        props.setProperty("consensus.default.policy", defaultConsensusPolicy.name());
        
        // Monitoring properties
        props.setProperty("monitoring.health.enabled", String.valueOf(healthMonitoringEnabled));
        props.setProperty("monitoring.health.check.interval", String.valueOf(healthCheckInterval));
        props.setProperty("monitoring.auto.optimization.enabled", String.valueOf(autoOptimizationEnabled));
        props.setProperty("monitoring.optimization.interval", String.valueOf(optimizationInterval));
        props.setProperty("monitoring.health.threshold", String.valueOf(healthThreshold));
        props.setProperty("monitoring.real.time.metrics.enabled", String.valueOf(realTimeMetricsEnabled));
        
        // Integration properties
        props.setProperty("integration.dashboard.enabled", String.valueOf(dashboardIntegrationEnabled));
        props.setProperty("integration.wallet.enabled", String.valueOf(walletIntegrationEnabled));
        props.setProperty("integration.smart.contract.enabled", String.valueOf(smartContractIntegrationEnabled));
        
        // Network properties
        props.setProperty("network.sync.interval", String.valueOf(networkSyncInterval));
        props.setProperty("network.max.peer.connections", String.valueOf(maxPeerConnections));
        
        // Add custom properties
        for (Map.Entry<String, String> entry : customProperties.entrySet()) {
            props.setProperty("custom." + entry.getKey(), entry.getValue());
        }
        
        return props;
    }
    
    /**
     * Validates the configuration for consistency and correctness
     */
    public boolean validate() {
        try {
            // Validate PBFT settings
            if (pbftViewTimeout <= 0) {
                LOGGER.warning("Invalid PBFT view timeout: " + pbftViewTimeout);
                return false;
            }
            if (pbftMaxFaultyNodes < 0) {
                LOGGER.warning("Invalid PBFT max faulty nodes: " + pbftMaxFaultyNodes);
                return false;
            }
            if (pbftMinValidators < 4) {
                LOGGER.warning("PBFT requires at least 4 validators, got: " + pbftMinValidators);
                return false;
            }
            if (pbftByzantineThreshold <= 0 || pbftByzantineThreshold >= 1) {
                LOGGER.warning("Invalid Byzantine threshold: " + pbftByzantineThreshold);
                return false;
            }
            
            // Validate PoW settings
            if (powTargetBlockTime <= 0) {
                LOGGER.warning("Invalid PoW target block time: " + powTargetBlockTime);
                return false;
            }
            if (powInitialDifficulty <= 0) {
                LOGGER.warning("Invalid PoW initial difficulty: " + powInitialDifficulty);
                return false;
            }
            if (powMinDifficulty <= 0 || powMaxDifficulty <= powMinDifficulty) {
                LOGGER.warning("Invalid PoW difficulty bounds: min=" + powMinDifficulty + ", max=" + powMaxDifficulty);
                return false;
            }
            
            // Validate PoS settings
            if (posMinimumStake <= 0) {
                LOGGER.warning("Invalid PoS minimum stake: " + posMinimumStake);
                return false;
            }
            if (posSlashingPenalty < 0 || posSlashingPenalty > 1) {
                LOGGER.warning("Invalid PoS slashing penalty: " + posSlashingPenalty);
                return false;
            }
            if (posValidatorSetSize <= 0) {
                LOGGER.warning("Invalid PoS validator set size: " + posValidatorSetSize);
                return false;
            }
            if (posRewardRate < 0) {
                LOGGER.warning("Invalid PoS reward rate: " + posRewardRate);
                return false;
            }
            
            // Validate Stream settings
            if (streamBufferSize <= 0) {
                LOGGER.warning("Invalid stream buffer size: " + streamBufferSize);
                return false;
            }
            if (streamBatchSize <= 0) {
                LOGGER.warning("Invalid stream batch size: " + streamBatchSize);
                return false;
            }
            if (streamProcessingInterval <= 0) {
                LOGGER.warning("Invalid stream processing interval: " + streamProcessingInterval);
                return false;
            }
            
            // Validate general settings
            if (maxBlockSize <= 0) {
                LOGGER.warning("Invalid max block size: " + maxBlockSize);
                return false;
            }
            if (maxTransactionsPerBlock <= 0) {
                LOGGER.warning("Invalid max transactions per block: " + maxTransactionsPerBlock);
                return false;
            }
            
            LOGGER.info("Configuration validation passed");
            return true;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during configuration validation", e);
            return false;
        }
    }
    
    /**
     * Creates a copy of this configuration with modifications
     */
    public Builder toBuilder() {
        Builder builder = new Builder();
        
        // Copy PBFT settings
        builder.pbftViewTimeout(pbftViewTimeout)
               .pbftMaxFaultyNodes(pbftMaxFaultyNodes)
               .pbftByzantineFaultTolerance(pbftByzantineFaultTolerance)
               .pbftCheckpointInterval(pbftCheckpointInterval)
               .pbftMinValidators(pbftMinValidators)
               .pbftByzantineThreshold(pbftByzantineThreshold)
               .pbftConsensusTimeout(pbftConsensusTimeout);
        
        // Copy PoW settings
        builder.powTargetBlockTime(powTargetBlockTime)
               .powInitialDifficulty(powInitialDifficulty)
               .powDifficultyAdjustmentFactor(powDifficultyAdjustmentFactor)
               .powRetargetInterval(powRetargetInterval)
               .powMaxDifficulty(powMaxDifficulty)
               .powMinDifficulty(powMinDifficulty)
               .powDynamicDifficultyEnabled(powDynamicDifficultyEnabled)
               .powHashRateTarget(powHashRateTarget);
        
        // Copy PoS settings
        builder.posMinimumStake(posMinimumStake)
               .posSlashingPenalty(posSlashingPenalty)
               .posValidatorSetSize(posValidatorSetSize)
               .posEpochLength(posEpochLength)
               .posRewardRate(posRewardRate)
               .posUnbondingPeriod(posUnbondingPeriod)
               .posInflationRate(posInflationRate)
               .posSlashingEnabled(posSlashingEnabled);
        
        // Copy Stream settings
        builder.streamBufferSize(streamBufferSize)
               .streamBatchSize(streamBatchSize)
               .streamProcessingInterval(streamProcessingInterval)
               .streamOrderedProcessing(streamOrderedProcessing)
               .streamMaxConcurrentBatches(streamMaxConcurrentBatches)
               .streamBatchTimeout(streamBatchTimeout)
               .streamCompressionEnabled(streamCompressionEnabled);
        
        // Copy general settings
        builder.maxBlockSize(maxBlockSize)
               .maxTransactionsPerBlock(maxTransactionsPerBlock)
               .blockTimestamp(blockTimestamp)
               .enableMetrics(enableMetrics)
               .defaultConsensusType(defaultConsensusType)
               .defaultConsensusPolicy(defaultConsensusPolicy);
        
        // Copy monitoring settings
        builder.healthMonitoringEnabled(healthMonitoringEnabled)
               .healthCheckInterval(healthCheckInterval)
               .autoOptimizationEnabled(autoOptimizationEnabled)
               .optimizationInterval(optimizationInterval)
               .healthThreshold(healthThreshold)
               .realTimeMetricsEnabled(realTimeMetricsEnabled);
        
        // Copy integration settings
        builder.dashboardIntegrationEnabled(dashboardIntegrationEnabled)
               .walletIntegrationEnabled(walletIntegrationEnabled)
               .smartContractIntegrationEnabled(smartContractIntegrationEnabled)
               .networkSyncInterval(networkSyncInterval)
               .maxPeerConnections(maxPeerConnections);
        
        // Copy advanced settings
        for (Map.Entry<ConsensusType, Map<String, Object>> entry : consensusSpecificParams.entrySet()) {
            for (Map.Entry<String, Object> paramEntry : entry.getValue().entrySet()) {
                builder.addConsensusSpecificParam(entry.getKey(), paramEntry.getKey(), paramEntry.getValue());
            }
        }
        
        for (Map.Entry<String, String> entry : customProperties.entrySet()) {
            builder.addCustomProperty(entry.getKey(), entry.getValue());
        }
        
        return builder;
    }
    
    /**
     * Returns a string representation of the configuration
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ConsensusConfig{\n");
        sb.append("  defaultConsensusType=").append(defaultConsensusType).append("\n");
        sb.append("  defaultConsensusPolicy=").append(defaultConsensusPolicy).append("\n");
        sb.append("  PBFT: {viewTimeout=").append(pbftViewTimeout)
          .append(", maxFaultyNodes=").append(pbftMaxFaultyNodes)
          .append(", byzantineFaultTolerance=").append(pbftByzantineFaultTolerance).append("}\n");
        sb.append("  PoW: {targetBlockTime=").append(powTargetBlockTime)
          .append(", initialDifficulty=").append(powInitialDifficulty)
          .append(", dynamicDifficulty=").append(powDynamicDifficultyEnabled).append("}\n");
        sb.append("  PoS: {minimumStake=").append(posMinimumStake)
          .append(", slashingPenalty=").append(posSlashingPenalty)
          .append(", validatorSetSize=").append(posValidatorSetSize).append("}\n");
        sb.append("  Stream: {bufferSize=").append(streamBufferSize)
          .append(", batchSize=").append(streamBatchSize)
          .append(", processingInterval=").append(streamProcessingInterval).append("}\n");
        sb.append("  Monitoring: {healthEnabled=").append(healthMonitoringEnabled)
          .append(", autoOptimization=").append(autoOptimizationEnabled).append("}\n");
        sb.append("  Integration: {dashboard=").append(dashboardIntegrationEnabled)
          .append(", wallet=").append(walletIntegrationEnabled)
          .append(", smartContract=").append(smartContractIntegrationEnabled).append("}\n");
        sb.append("}");
        return sb.toString();
    }
}