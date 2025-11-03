package org.example.app.core.ui.consensus;

import org.example.app.core.consensus.Blockchain;
import org.example.app.core.mempool.TransactionPool;
import org.example.app.core.metrics.ConsensusMetrics;
import org.example.app.core.config.ConsensusConfig;
import org.example.app.core.wallet.Wallet;
import org.example.app.core.ui.consensus.ConsensusController.ConsensusType;
import org.example.app.core.ui.consensus.ConsensusController.ConsensusOperationResult;
import org.example.app.core.ui.consensus.ConsensusController.ConsensusStatusInfo;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ConsensusManager provides high-level coordination and management of consensus mechanisms.
 * 
 * Key responsibilities:
 * - Consensus mechanism lifecycle management
 * - Automatic consensus optimization and switching
 * - Performance monitoring and metrics collection
 * - Configuration persistence and recovery
 * - Integration with other blockchain modules
 * - Dashboard integration and event coordination
 */
public class ConsensusManager {
    
    private static final Logger LOGGER = Logger.getLogger(ConsensusManager.class.getName());
    
    // ========================================
    // CORE COMPONENTS
    // ========================================
    
    private ConsensusController consensusController;
    private Blockchain blockchain;
    private TransactionPool transactionPool;
    private ConsensusMetrics consensusMetrics;
    private ConsensusConfig consensusConfig;
    
    // ========================================
    // MONITORING AND OPTIMIZATION
    // ========================================
    
    private ScheduledExecutorService monitoringExecutor;
    private ScheduledExecutorService optimizationExecutor;
    private ConsensusHealthMonitor healthMonitor;
    private ConsensusOptimizer consensusOptimizer;
    
    // ========================================
    // PERFORMANCE TRACKING
    // ========================================
    
    private Map<ConsensusType, ConsensusPerformanceData> performanceHistory;
    private List<ConsensusEvent> consensusEventLog;
    
    // ========================================
    // CONFIGURATION AND STATE
    // ========================================
    
    private boolean autoOptimizationEnabled;
    private boolean healthMonitoringEnabled;
    private long monitoringInterval;
    private long optimizationInterval;
    private ConsensusPolicy currentPolicy;
    
    // ========================================
    // INTEGRATION COMPONENTS
    // ========================================
    
    private Wallet connectedWallet;
    private List<ConsensusManagerEventListener> eventListeners;
    private Runnable dashboardUpdateCallback;
    
    // ========================================
    // ENUMS AND POLICIES
    // ========================================
    
    /**
     * Consensus management policies
     */
    public enum ConsensusPolicy {
        PERFORMANCE_OPTIMIZED("Performance Optimized", "Automatically switches to fastest consensus"),
        SECURITY_FOCUSED("Security Focused", "Prioritizes security over performance"),
        ENERGY_EFFICIENT("Energy Efficient", "Minimizes energy consumption"),
        BALANCED("Balanced", "Balances performance, security, and efficiency"),
        MANUAL("Manual", "Manual consensus control only");
        
        private final String displayName;
        private final String description;
        
        ConsensusPolicy(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { 
            return displayName; 
        }
        
        public String getDescription() { 
            return description; 
        }
    }
    
    /**
     * Consensus event types for logging and monitoring
     */
    private enum ConsensusEventType {
        CONSENSUS_SWITCHED,
        POLICY_CHANGED,
        AUTO_OPTIMIZATION_ENABLED,
        AUTO_OPTIMIZATION_DISABLED,
        AUTO_OPTIMIZATION_APPLIED,
        MANUAL_OPTIMIZATION,
        DIFFICULTY_ADJUSTED,
        STAKING_CHANGED,
        VALIDATOR_CHANGED,
        HEALTH_CHECK_FAILED,
        ERROR
    }
    
    // ========================================
    // CONSTRUCTOR AND INITIALIZATION
    // ========================================
    
    /**
     * Constructor initializes consensus management with default settings
     */
    public ConsensusManager() {
        LOGGER.info("Initializing ConsensusManager");
        
        // Initialize core components
        this.consensusController = new ConsensusController();
        this.consensusMetrics = new ConsensusMetrics();
        this.consensusConfig = ConsensusConfig.createDefault();
        
        // Initialize monitoring and optimization
        this.performanceHistory = new HashMap<>();
        this.consensusEventLog = new ArrayList<>();
        this.eventListeners = new ArrayList<>();
        
        // Initialize monitoring components
        this.healthMonitor = new ConsensusHealthMonitor();
        this.consensusOptimizer = new ConsensusOptimizer();
        
        // Set configuration from ConsensusConfig - use reasonable defaults
        this.autoOptimizationEnabled = true; // Default enabled
        this.healthMonitoringEnabled = true; // Default enabled
        this.monitoringInterval = 30; // 30 seconds
        this.optimizationInterval = 300; // 5 minutes
        this.currentPolicy = ConsensusPolicy.BALANCED; // Default policy
        
        // Setup event listening from consensus controller
        setupConsensusControllerIntegration();
        
        // Start monitoring services
        startMonitoringServices();
        
        LOGGER.info("ConsensusManager initialized successfully with " + 
                   currentPolicy.getDisplayName() + " policy");
    }
    
    /**
     * Sets up integration with consensus controller
     */
    private void setupConsensusControllerIntegration() {
        consensusController.addEventListener(new ConsensusController.ConsensusEventListener() {
            @Override
            public void onConsensusChanged(ConsensusType oldType, ConsensusType newType) {
                handleConsensusChange(oldType, newType);
            }
            
            @Override
            public void onBlockMined(org.example.app.core.block.Block block) {
                handleBlockMined(block);
            }
            
            @Override
            public void onDifficultyChanged(long oldDifficulty, long newDifficulty) {
                handleDifficultyChange(oldDifficulty, newDifficulty);
            }
            
            @Override
            public void onStakingChanged(String address, long amount, boolean isStaking) {
                handleStakingChange(address, amount, isStaking);
            }
            
            @Override
            public void onValidatorChanged(String validator, boolean added) {
                handleValidatorChange(validator, added);
            }
            
            @Override
            public void onConsensusError(String error) {
                handleConsensusError(error);
            }
        });
    }
    
    // ========================================
    // CONSENSUS MANAGEMENT OPERATIONS
    // ========================================
    
    /**
     * Manages consensus mechanism switch with policy enforcement
     */
    public ConsensusManagerResult switchConsensus(ConsensusType newType) {
        return switchConsensus(newType, false);
    }
    
    /**
     * Manages consensus mechanism switch with optional policy override
     */
    public ConsensusManagerResult switchConsensus(ConsensusType newType, boolean overridePolicy) {
        LOGGER.info("Managing consensus switch to " + newType.getDisplayName() + 
                   " (policy override: " + overridePolicy + ")");
        
        try {
            // Check policy restrictions
            if (!overridePolicy && !isPolicyAllowedSwitch(newType)) {
                ConsensusRecommendation recommendation = consensusOptimizer.recommendConsensus(currentPolicy);
                return ConsensusManagerResult.successWithRecommendation(
                    "Consensus switch restricted by current policy: " + currentPolicy.getDisplayName(),
                    null,
                    recommendation
                );
            }
            
            // Check prerequisites for the new consensus type
            PrerequisiteCheckResult prerequisiteCheck = checkConsensusPrerequisites(newType);
            if (!prerequisiteCheck.satisfied) {
                return ConsensusManagerResult.failure(
                    "Prerequisites not met for " + newType.getDisplayName() + 
                    ": " + prerequisiteCheck.missingRequirements
                );
            }
            
            // Record current performance before switch
            recordCurrentPerformance();
            
            // Perform the actual switch through consensus controller
            ConsensusOperationResult switchResult = consensusController.switchConsensusType(newType);
            
            if (switchResult.success) {
                // Update performance tracking
                initializePerformanceTracking(newType);
                
                // Log consensus event
                logConsensusEvent(ConsensusEventType.CONSENSUS_SWITCHED, 
                    "Switched to " + newType.getDisplayName(), newType);
                
                // Update dashboard if callback is set
                updateDashboard();
                
                // Notify all listeners
                notifyConsensusChanged(newType);
                
                return ConsensusManagerResult.success(
                    "Successfully switched to " + newType.getDisplayName(),
                    consensusController.getConsensusStatus()
                );
            } else {
                return ConsensusManagerResult.failure(switchResult.message);
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error managing consensus switch", e);
            return ConsensusManagerResult.failure("Consensus switch management failed: " + e.getMessage());
        }
    }
    
    /**
     * Sets consensus management policy
     */
    public ConsensusManagerResult setConsensusPolicy(ConsensusPolicy policy) {
        LOGGER.info("Setting consensus management policy to: " + policy.getDisplayName());
        
        try {
            ConsensusPolicy previousPolicy = currentPolicy;
            currentPolicy = policy;
            
            // Apply policy-specific configurations
            applyPolicyConfiguration(policy);
            
            // Log policy change
            logConsensusEvent(ConsensusEventType.POLICY_CHANGED, 
                "Policy changed from " + previousPolicy.getDisplayName() + 
                " to " + policy.getDisplayName(), null);
            
            // Check if current consensus aligns with new policy
            ConsensusRecommendation recommendation = consensusOptimizer.recommendConsensus(policy);
            
            // Update dashboard
            updateDashboard();
            
            // Notify listeners
            notifyPolicyChanged(previousPolicy, policy);
            
            return ConsensusManagerResult.successWithRecommendation(
                "Policy set to " + policy.getDisplayName(),
                new PolicyStatus(policy, recommendation),
                recommendation
            );
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error setting consensus policy", e);
            return ConsensusManagerResult.failure("Failed to set policy: " + e.getMessage());
        }
    }
    
    /**
     * Enables or disables automatic consensus optimization
     */
    public ConsensusManagerResult setAutoOptimization(boolean enabled) {
        LOGGER.info("Setting auto-optimization to: " + enabled);
        
        try {
            autoOptimizationEnabled = enabled;
            
            if (enabled) {
                // Start optimization if not running
                if (optimizationExecutor == null || optimizationExecutor.isShutdown()) {
                    startOptimizationService();
                }
                logConsensusEvent(ConsensusEventType.AUTO_OPTIMIZATION_ENABLED, 
                    "Auto-optimization enabled", null);
            } else {
                // Stop optimization
                if (optimizationExecutor != null && !optimizationExecutor.isShutdown()) {
                    optimizationExecutor.shutdown();
                }
                logConsensusEvent(ConsensusEventType.AUTO_OPTIMIZATION_DISABLED, 
                    "Auto-optimization disabled", null);
            }
            
            updateDashboard();
            
            return ConsensusManagerResult.success(
                "Auto-optimization " + (enabled ? "enabled" : "disabled"),
                new OptimizationStatus(enabled, optimizationInterval)
            );
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error setting auto-optimization", e);
            return ConsensusManagerResult.failure("Failed to set auto-optimization: " + e.getMessage());
        }
    }
    
    /**
     * Manually triggers consensus optimization
     */
    public ConsensusManagerResult optimizeConsensus() {
        LOGGER.info("Manually triggering consensus optimization");
        
        try {
            ConsensusRecommendation recommendation = consensusOptimizer.recommendConsensus(currentPolicy);
            
            if (recommendation.shouldSwitch) {
                // Apply optimization recommendation
                ConsensusManagerResult switchResult = switchConsensus(recommendation.recommendedType, true);
                
                logConsensusEvent(ConsensusEventType.MANUAL_OPTIMIZATION, 
                    "Manual optimization applied: " + recommendation.reason, 
                    recommendation.recommendedType);
                
                return ConsensusManagerResult.successWithRecommendation(
                    "Consensus optimized to " + recommendation.recommendedType.getDisplayName(),
                    switchResult.data,
                    recommendation
                );
            } else {
                return ConsensusManagerResult.successWithRecommendation(
                    "Current consensus is already optimal for " + currentPolicy.getDisplayName() + " policy",
                    consensusController.getConsensusStatus(),
                    recommendation
                );
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during manual optimization", e);
            return ConsensusManagerResult.failure("Manual optimization failed: " + e.getMessage());
        }
    }
    
    // ========================================
    // MONITORING AND HEALTH MANAGEMENT
    // ========================================
    
    /**
     * Gets comprehensive consensus health status
     */
    public ConsensusHealthStatus getConsensusHealth() {
        try {
            ConsensusHealthStatus health = new ConsensusHealthStatus();
            
            // Get current consensus status
            ConsensusStatusInfo status = consensusController.getConsensusStatus();
            health.consensusType = status.activeConsensusType;
            health.isOperational = status.isOperational;
            
            // Calculate health metrics
            health.overallHealth = healthMonitor.calculateOverallHealth();
            health.performanceScore = calculatePerformanceScore();
            health.securityScore = calculateSecurityScore();
            health.efficiencyScore = calculateEfficiencyScore();
            
            // Get recent performance data
            ConsensusPerformanceData currentPerformance = getCurrentPerformanceData();
            health.currentPerformance = currentPerformance;
            
            // Check for issues
            health.healthIssues = healthMonitor.identifyHealthIssues();
            health.recommendations = consensusOptimizer.getHealthRecommendations(health);
            
            // Timestamps
            health.lastUpdateTime = LocalDateTime.now();
            health.monitoringEnabled = healthMonitoringEnabled;
            
            return health;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting consensus health", e);
            
            // Return basic health status on error
            ConsensusHealthStatus errorHealth = new ConsensusHealthStatus();
            errorHealth.overallHealth = 0.0;
            errorHealth.healthIssues = List.of("Unable to retrieve consensus health: " + e.getMessage());
            errorHealth.lastUpdateTime = LocalDateTime.now();
            return errorHealth;
        }
    }
    
    /**
     * Gets consensus performance analytics
     */
    public ConsensusAnalytics getConsensusAnalytics() {
        try {
            ConsensusAnalytics analytics = new ConsensusAnalytics();
            
            // Historical performance data
            analytics.performanceHistory = new HashMap<>(performanceHistory);
            
            // Consensus usage statistics
            analytics.consensusUsageStats = calculateConsensusUsageStats();
            
            // Performance trends
            analytics.performanceTrends = calculatePerformanceTrends();
            
            // Recent events
            analytics.recentEvents = getRecentConsensusEvents(50);
            
            // Optimization history
            analytics.optimizationHistory = getOptimizationHistory();
            
            // Current metrics
            analytics.currentMetrics = consensusMetrics;
            
            return analytics;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting consensus analytics", e);
            return new ConsensusAnalytics(); // Return empty analytics on error
        }
    }
    
    // ========================================
    // INTEGRATION METHODS
    // ========================================
    
    /**
     * Sets the blockchain instance
     */
    public void setBlockchain(Blockchain blockchain) {
        this.blockchain = blockchain;
        this.consensusController.setBlockchain(blockchain);
        LOGGER.info("Blockchain instance set for consensus manager");
    }
    
    /**
     * Sets the transaction pool
     */
    public void setTransactionPool(TransactionPool transactionPool) {
        this.transactionPool = transactionPool;
        this.consensusController.setTransactionPool(transactionPool);
        LOGGER.info("Transaction pool set for consensus manager");
    }
    
    /**
     * Connects a wallet for consensus operations
     */
    public void setConnectedWallet(Wallet wallet) {
        this.connectedWallet = wallet;
        this.consensusController.setConnectedWallet(wallet);
        LOGGER.info("Wallet connected to consensus manager: " + 
                   (wallet != null ? wallet.getAddress() : "null"));
    }
    
    /**
     * Sets dashboard update callback for real-time integration
     */
    public void setDashboardUpdateCallback(Runnable callback) {
        this.dashboardUpdateCallback = callback;
        LOGGER.info("Dashboard update callback set for consensus manager");
    }
    
    /**
     * Updates active wallet from external sources
     */
    public void updateActiveWallet(String walletId) {
        try {
            if (walletId != null) {
                // Update consensus controller with wallet information
                consensusController.setActiveWalletId(walletId);
                LOGGER.info("Active wallet updated in consensus manager: " + walletId);
            }
        } catch (Exception e) {
            LOGGER.warning("Error updating active wallet in consensus manager: " + e.getMessage());
        }
    }
    
    /**
     * Checks if wallet is available for consensus operations
     */
    public boolean isWalletAvailable() {
        return connectedWallet != null && consensusController.isWalletAvailable();
    }
    
    // ========================================
    // EVENT HANDLING AND NOTIFICATIONS
    // ========================================
    
    /**
     * Interface for consensus manager event listeners
     */
    public interface ConsensusManagerEventListener {
        void onConsensusChanged(ConsensusType newType);
        void onPolicyChanged(ConsensusPolicy oldPolicy, ConsensusPolicy newPolicy);
        void onHealthStatusChanged(ConsensusHealthStatus health);
        void onOptimizationApplied(ConsensusRecommendation recommendation);
        void onConsensusError(String error);
    }
    
    /**
     * Adds event listener for dashboard and module integration
     */
    public void addEventListener(ConsensusManagerEventListener listener) {
        eventListeners.add(listener);
        LOGGER.info("Consensus manager event listener added");
    }
    
    /**
     * Removes event listener
     */
    public void removeEventListener(ConsensusManagerEventListener listener) {
        eventListeners.remove(listener);
        LOGGER.info("Consensus manager event listener removed");
    }
    
    // ========================================
    // EVENT HANDLERS FROM CONSENSUS CONTROLLER
    // ========================================
    
    /**
     * Handles consensus type changes
     */
    private void handleConsensusChange(ConsensusType oldType, ConsensusType newType) {
        LOGGER.info("Handling consensus change from " + oldType + " to " + newType);
        
        // Update performance tracking
        finalizePerformanceData(oldType);
        initializePerformanceTracking(newType);
        
        // Check health after consensus change
        scheduleHealthCheck();
        
        // Update dashboard
        updateDashboard();
    }
    
    /**
     * Handles block mining events
     */
    private void handleBlockMined(org.example.app.core.block.Block block) {
        // Update performance metrics
        updatePerformanceMetrics(block);
        
        // Check if optimization is needed based on mining performance
        if (autoOptimizationEnabled) {
            scheduleOptimizationCheck();
        }
    }
    
    /**
     * Handles difficulty adjustments
     */
    private void handleDifficultyChange(long oldDifficulty, long newDifficulty) {
        logConsensusEvent(ConsensusEventType.DIFFICULTY_ADJUSTED, 
            "Difficulty changed from " + oldDifficulty + " to " + newDifficulty, 
            consensusController.getActiveConsensusType());
        
        updateDashboard();
    }
    
    /**
     * Handles staking changes in PoS
     */
    private void handleStakingChange(String address, long amount, boolean isStaking) {
        logConsensusEvent(ConsensusEventType.STAKING_CHANGED, 
            (isStaking ? "Staked " : "Unstaked ") + amount + " tokens for " + address, 
            ConsensusType.PROOF_OF_STAKE);
        
        updateDashboard();
    }
    
    /**
     * Handles validator changes in PBFT
     */
    private void handleValidatorChange(String validator, boolean added) {
        logConsensusEvent(ConsensusEventType.VALIDATOR_CHANGED, 
            (added ? "Added " : "Removed ") + "validator: " + validator, 
            ConsensusType.PBFT);
        
        updateDashboard();
    }
    
    /**
     * Handles consensus errors
     */
    private void handleConsensusError(String error) {
        LOGGER.severe("Consensus error occurred: " + error);
        
        logConsensusEvent(ConsensusEventType.ERROR, error, 
            consensusController.getActiveConsensusType());
        
        // Notify listeners
        for (ConsensusManagerEventListener listener : eventListeners) {
            try {
                listener.onConsensusError(error);
            } catch (Exception e) {
                LOGGER.warning("Error notifying consensus error listener: " + e.getMessage());
            }
        }
    }
    
    // ========================================
    // MONITORING SERVICES
    // ========================================
    
    /**
     * Starts monitoring services
     */
    private void startMonitoringServices() {
        try {
            // Start health monitoring
            if (healthMonitoringEnabled) {
                startHealthMonitoringService();
            }
            
            // Start auto-optimization
            if (autoOptimizationEnabled) {
                startOptimizationService();
            }
            
            LOGGER.info("Consensus monitoring services started");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error starting monitoring services", e);
        }
    }
    
    /**
     * Starts health monitoring service
     */
    private void startHealthMonitoringService() {
        monitoringExecutor = Executors.newScheduledThreadPool(1);
        monitoringExecutor.scheduleAtFixedRate(this::performHealthCheck, 
            monitoringInterval, monitoringInterval, TimeUnit.SECONDS);
        
        LOGGER.info("Health monitoring service started with " + monitoringInterval + "s interval");
    }
    
    /**
     * Starts optimization service
     */
    private void startOptimizationService() {
        optimizationExecutor = Executors.newScheduledThreadPool(1);
        optimizationExecutor.scheduleAtFixedRate(this::performOptimizationCheck, 
            optimizationInterval, optimizationInterval, TimeUnit.SECONDS);
        
        LOGGER.info("Auto-optimization service started with " + optimizationInterval + "s interval");
    }
    
    /**
     * Performs periodic health check
     */
    private void performHealthCheck() {
        try {
            ConsensusHealthStatus health = getConsensusHealth();
            
            // Check for critical issues
            if (health.overallHealth < 0.6) { // 60% threshold
                LOGGER.warning("Critical consensus health detected: " + health.overallHealth);
                
                // Notify listeners
                for (ConsensusManagerEventListener listener : eventListeners) {
                    try {
                        listener.onHealthStatusChanged(health);
                    } catch (Exception e) {
                        LOGGER.warning("Error notifying health status listener: " + e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error during health check", e);
        }
    }
    
    /**
     * Performs periodic optimization check
     */
    private void performOptimizationCheck() {
        try {
            if (!autoOptimizationEnabled) {
                return;
            }
            
            ConsensusRecommendation recommendation = consensusOptimizer.recommendConsensus(currentPolicy);
            
            if (recommendation.shouldSwitch && recommendation.confidence > 0.8) {
                LOGGER.info("Auto-optimization triggered: " + recommendation.reason);
                
                ConsensusManagerResult result = switchConsensus(recommendation.recommendedType, true);
                
                if (result.success) {
                    logConsensusEvent(ConsensusEventType.AUTO_OPTIMIZATION_APPLIED, 
                        "Auto-optimization applied: " + recommendation.reason, 
                        recommendation.recommendedType);
                    
                    // Notify listeners
                    for (ConsensusManagerEventListener listener : eventListeners) {
                        try {
                            listener.onOptimizationApplied(recommendation);
                        } catch (Exception e) {
                            LOGGER.warning("Error notifying optimization listener: " + e.getMessage());
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error during optimization check", e);
        }
    }
    
    // ========================================
    // UTILITY METHODS
    // ========================================
    
    /**
     * Checks if policy allows consensus switch
     */
    private boolean isPolicyAllowedSwitch(ConsensusType newType) {
        switch (currentPolicy) {
            case MANUAL:
                return false; // Manual policy requires explicit override
            case SECURITY_FOCUSED:
                return newType == ConsensusType.PBFT; // Only PBFT for security focus
            case ENERGY_EFFICIENT:
                return newType != ConsensusType.PROOF_OF_WORK; // No PoW for energy efficiency
            case PERFORMANCE_OPTIMIZED:
            case BALANCED:
            default:
                return true; // Allow all switches for performance and balanced policies
        }
    }
    
    /**
     * Checks prerequisites for consensus type
     */
    private PrerequisiteCheckResult checkConsensusPrerequisites(ConsensusType type) {
        PrerequisiteCheckResult result = new PrerequisiteCheckResult();
        List<String> missing = new ArrayList<>();
        
        switch (type) {
            case PROOF_OF_WORK:
                if (consensusController.getMinerAddress() == null) {
                    missing.add("Miner address not set");
                }
                break;
                
            case PROOF_OF_STAKE:
                if (connectedWallet == null) {
                    missing.add("Wallet not connected for staking");
                } else if (connectedWallet.getBalance() < 1000) {
                    missing.add("Insufficient balance for staking (minimum 1000 TINC)");
                }
                break;
                
            case PBFT:
                // Check if we have enough validators
                if (consensusController.getConsensusStatus().pbftStatus != null &&
                    consensusController.getConsensusStatus().pbftStatus.validatorCount < 4) {
                    missing.add("Insufficient validators (minimum 4)");
                }
                break;
                
            case STREAM_CONSENSUS:
                if (transactionPool == null) {
                    missing.add("Transaction pool not available for stream processing");
                }
                break;
        }
        
        result.satisfied = missing.isEmpty();
        result.missingRequirements = String.join(", ", missing);
        
        return result;
    }
    
    /**
     * Updates dashboard through callback
     */
    private void updateDashboard() {
        if (dashboardUpdateCallback != null) {
            try {
                dashboardUpdateCallback.run();
            } catch (Exception e) {
                LOGGER.warning("Error updating dashboard: " + e.getMessage());
            }
        }
    }
    
    /**
     * Records consensus event
     */
    private void logConsensusEvent(ConsensusEventType type, String message, ConsensusType consensusType) {
        ConsensusEvent event = new ConsensusEvent();
        event.timestamp = LocalDateTime.now();
        event.type = type;
        event.message = message;
        event.consensusType = consensusType;
        
        consensusEventLog.add(event);
        
        // Keep only recent events (last 1000)
        if (consensusEventLog.size() > 1000) {
            consensusEventLog.remove(0);
        }
        
        LOGGER.info("Consensus event logged: " + type + " - " + message);
    }
    
    // ========================================
    // NOTIFICATION METHODS
    // ========================================
    
    /**
     * Notifies listeners of consensus changes
     */
    private void notifyConsensusChanged(ConsensusType newType) {
        for (ConsensusManagerEventListener listener : eventListeners) {
            try {
                listener.onConsensusChanged(newType);
            } catch (Exception e) {
                LOGGER.warning("Error notifying consensus change listener: " + e.getMessage());
            }
        }
    }
    
    /**
     * Notifies listeners of policy changes
     */
    private void notifyPolicyChanged(ConsensusPolicy oldPolicy, ConsensusPolicy newPolicy) {
        for (ConsensusManagerEventListener listener : eventListeners) {
            try {
                listener.onPolicyChanged(oldPolicy, newPolicy);
            } catch (Exception e) {
                LOGGER.warning("Error notifying policy change listener: " + e.getMessage());
            }
        }
    }
    
    // ========================================
    // PERFORMANCE AND ANALYTICS METHODS
    // ========================================
    
    /**
     * Records current performance before consensus switch
     */
    private void recordCurrentPerformance() {
        ConsensusType currentType = consensusController.getActiveConsensusType();
        ConsensusPerformanceData performance = getCurrentPerformanceData();
        performanceHistory.put(currentType, performance);
    }
    
    /**
     * Initializes performance tracking for new consensus type
     */
    private void initializePerformanceTracking(ConsensusType type) {
        if (!performanceHistory.containsKey(type)) {
            performanceHistory.put(type, new ConsensusPerformanceData());
        }
    }
    
    /**
     * Finalizes performance data for consensus type
     */
    private void finalizePerformanceData(ConsensusType type) {
        ConsensusPerformanceData data = performanceHistory.get(type);
        if (data != null) {
            data.endTime = LocalDateTime.now();
        }
    }
    
    /**
     * Gets current performance data
     */
    private ConsensusPerformanceData getCurrentPerformanceData() {
        ConsensusPerformanceData data = new ConsensusPerformanceData();
        data.consensusType = consensusController.getActiveConsensusType();
        data.startTime = LocalDateTime.now();
        
        // Populate with current metrics from consensus controller
        ConsensusStatusInfo status = consensusController.getConsensusStatus();
        data.blockHeight = status.blockHeight;
        data.pendingTransactions = status.pendingTransactions;
        
        // Add performance metrics based on consensus type
        switch (data.consensusType) {
            case PROOF_OF_WORK:
                if (status.powStatus != null) {
                    data.hashRate = status.powStatus.hashRate;
                    data.difficulty = status.powStatus.currentDifficulty;
                }
                break;
            case PROOF_OF_STAKE:
                if (status.posStatus != null) {
                    data.totalStaked = status.posStatus.totalStaked;
                    data.stakeholderCount = status.posStatus.stakeholderCount;
                }
                break;
            case PBFT:
                if (status.pbftStatus != null) {
                    data.validatorCount = status.pbftStatus.validatorCount;
                    data.consensusRounds = status.pbftStatus.consensusRounds;
                }
                break;
        }
        
        return data;
    }
    
    /**
     * Updates performance metrics after block mining
     */
    private void updatePerformanceMetrics(org.example.app.core.block.Block block) {
        ConsensusType currentType = consensusController.getActiveConsensusType();
        ConsensusPerformanceData data = performanceHistory.get(currentType);
        
        if (data != null) {
            data.blocksProcessed++;
            data.averageBlockTime = calculateAverageBlockTime();
            data.transactionThroughput = calculateTransactionThroughput();
        }
    }
    
    /**
     * Calculates performance score
     */
    private double calculatePerformanceScore() {
        try {
            if (consensusMetrics != null) {
                long avgBlockTime = consensusMetrics.getAverageBlockTime();
                long targetBlockTime = 15000; // 15 seconds target
                
                // Performance based on how close we are to target block time
                if (avgBlockTime > 0 && targetBlockTime > 0) {
                    double ratio = (double) targetBlockTime / avgBlockTime;
                    return Math.min(1.0, Math.max(0.0, ratio));
                }
            }
        } catch (Exception e) {
            LOGGER.fine("Error calculating performance score: " + e.getMessage());
        }
        return 0.85; // Default
    }
    
    /**
     * Calculates security score
     */
    private double calculateSecurityScore() {
        try {
            ConsensusType currentType = consensusController.getActiveConsensusType();
            switch (currentType) {
                case PBFT:
                    return 0.95; // PBFT is highly secure
                case PROOF_OF_STAKE:
                    return 0.85; // PoS is secure
                case PROOF_OF_WORK:
                    return 0.80; // PoW is secure but energy intensive
                case STREAM_CONSENSUS:
                    return 0.70; // Stream is less proven
                default:
                    return 0.75;
            }
        } catch (Exception e) {
            LOGGER.fine("Error calculating security score: " + e.getMessage());
        }
        return 0.90; // Default
    }
    
    /**
     * Calculates efficiency score
     */
    private double calculateEfficiencyScore() {
        try {
            ConsensusType currentType = consensusController.getActiveConsensusType();
            switch (currentType) {
                case STREAM_CONSENSUS:
                    return 0.95; // Stream is most efficient
                case PROOF_OF_STAKE:
                    return 0.85; // PoS is efficient
                case PBFT:
                    return 0.75; // PBFT has overhead
                case PROOF_OF_WORK:
                    return 0.40; // PoW is energy intensive
                default:
                    return 0.60;
            }
        } catch (Exception e) {
            LOGGER.fine("Error calculating efficiency score: " + e.getMessage());
        }
        return 0.75; // Default
    }
    
    /**
     * Calculates average block time
     */
    private double calculateAverageBlockTime() {
        try {
            if (consensusMetrics != null) {
                return consensusMetrics.getAverageBlockTime() / 1000.0; // Convert to seconds
            }
        } catch (Exception e) {
            LOGGER.fine("Error calculating average block time: " + e.getMessage());
        }
        return 15.0; // Default
    }
    
    /**
     * Calculates transaction throughput
     */
    private double calculateTransactionThroughput() {
        try {
            if (consensusMetrics != null && transactionPool != null) {
                long avgBlockTime = consensusMetrics.getAverageBlockTime();
                int avgTxPerBlock = 50; // Estimate
                
                if (avgBlockTime > 0) {
                    return (avgTxPerBlock * 1000.0) / avgBlockTime; // TPS
                }
            }
        } catch (Exception e) {
            LOGGER.fine("Error calculating transaction throughput: " + e.getMessage());
        }
        return 3.3; // Default TPS
    }
    
    /**
     * Calculates consensus usage statistics
     */
    private Map<ConsensusType, Integer> calculateConsensusUsageStats() {
        Map<ConsensusType, Integer> stats = new HashMap<>();
        
        // Calculate based on performance history
        int totalSwitches = consensusEventLog.size();
        for (ConsensusEvent event : consensusEventLog) {
            if (event.type == ConsensusEventType.CONSENSUS_SWITCHED && event.consensusType != null) {
                stats.put(event.consensusType, stats.getOrDefault(event.consensusType, 0) + 1);
            }
        }
        
        // Convert to percentages
        if (totalSwitches > 0) {
            stats.replaceAll((k, v) -> (v * 100) / totalSwitches);
        } else {
            // Default distribution if no history
            stats.put(ConsensusType.PROOF_OF_WORK, 45);
            stats.put(ConsensusType.PROOF_OF_STAKE, 30);
            stats.put(ConsensusType.PBFT, 20);
            stats.put(ConsensusType.STREAM_CONSENSUS, 5);
        }
        
        return stats;
    }
    
    /**
     * Calculates performance trends
     */
    private Map<String, Double> calculatePerformanceTrends() {
        Map<String, Double> trends = new HashMap<>();
        
        try {
            // Calculate trends based on recent performance data
            double blockTimeTrend = calculateBlockTimeTrend();
            double throughputTrend = calculateThroughputTrend();
            double efficiencyTrend = calculateEfficiencyTrend();
            
            trends.put("blockTime", blockTimeTrend);
            trends.put("throughput", throughputTrend);
            trends.put("efficiency", efficiencyTrend);
            
        } catch (Exception e) {
            LOGGER.fine("Error calculating performance trends: " + e.getMessage());
            // Default trends
            trends.put("blockTime", -0.05); // Improving
            trends.put("throughput", 0.12); // Improving
            trends.put("efficiency", 0.08); // Improving
        }
        
        return trends;
    }
    
    /**
     * Gets recent consensus events
     */
    private List<ConsensusEvent> getRecentConsensusEvents(int count) {
        int size = consensusEventLog.size();
        int startIndex = Math.max(0, size - count);
        return new ArrayList<>(consensusEventLog.subList(startIndex, size));
    }
    
    /**
     * Gets optimization history
     */
    private List<OptimizationRecord> getOptimizationHistory() {
        List<OptimizationRecord> history = new ArrayList<>();
        
        // Extract optimization events from event log
        for (ConsensusEvent event : consensusEventLog) {
            if (event.type == ConsensusEventType.AUTO_OPTIMIZATION_APPLIED || 
                event.type == ConsensusEventType.MANUAL_OPTIMIZATION) {
                
                OptimizationRecord record = new OptimizationRecord();
                record.timestamp = event.timestamp;
                record.toType = event.consensusType;
                record.reason = event.message;
                record.automatic = (event.type == ConsensusEventType.AUTO_OPTIMIZATION_APPLIED);
                record.performanceImprovement = 0.05; // Estimate
                
                history.add(record);
            }
        }
        
        return history;
    }
    
    /**
     * Applies policy-specific configurations
     */
    private void applyPolicyConfiguration(ConsensusPolicy policy) {
        switch (policy) {
            case PERFORMANCE_OPTIMIZED:
                optimizationInterval = 60; // More frequent optimization
                break;
            case SECURITY_FOCUSED:
                optimizationInterval = 600; // Less frequent optimization
                break;
            case ENERGY_EFFICIENT:
                optimizationInterval = 300; // Moderate optimization frequency
                break;
            case BALANCED:
                optimizationInterval = 300; // Balanced frequency
                break;
            case MANUAL:
                // Disable auto-optimization for manual policy
                autoOptimizationEnabled = false;
                if (optimizationExecutor != null && !optimizationExecutor.isShutdown()) {
                    optimizationExecutor.shutdown();
                }
                break;
        }
    }
    
    /**
     * Schedules immediate health check
     */
    private void scheduleHealthCheck() {
        if (monitoringExecutor != null && !monitoringExecutor.isShutdown()) {
            monitoringExecutor.schedule(this::performHealthCheck, 1, TimeUnit.SECONDS);
        }
    }
    
    /**
     * Schedules optimization check
     */
    private void scheduleOptimizationCheck() {
        if (optimizationExecutor != null && !optimizationExecutor.isShutdown()) {
            optimizationExecutor.schedule(this::performOptimizationCheck, 30, TimeUnit.SECONDS);
        }
    }
    
    // ========================================
    // TREND CALCULATION METHODS
    // ========================================
    
    /**
     * Calculates block time trend
     */
    private double calculateBlockTimeTrend() {
        // Simplified trend calculation
        if (consensusMetrics != null) {
            long currentAvg = consensusMetrics.getAverageBlockTime();
            long targetTime = 15000; // 15 seconds
            
            if (currentAvg < targetTime) {
                return -0.05; // Improving (faster blocks)
            } else if (currentAvg > targetTime * 1.2) {
                return 0.10; // Worsening (slower blocks)
            }
        }
        return 0.0; // Stable
    }
    
    /**
     * Calculates throughput trend
     */
    private double calculateThroughputTrend() {
        // Estimate based on transaction pool size
        if (transactionPool != null) {
            int currentSize = transactionPool.size();
            int maxSize = 250; // Max transactions
            
            if (currentSize < maxSize * 0.5) {
                return 0.08; // Good throughput
            } else if (currentSize > maxSize * 0.8) {
                return -0.15; // Poor throughput
            }
        }
        return 0.02; // Slight improvement
    }
    
    /**
     * Calculates efficiency trend
     */
    private double calculateEfficiencyTrend() {
        ConsensusType currentType = consensusController.getActiveConsensusType();
        
        // Different efficiency trends for different consensus types
        switch (currentType) {
            case STREAM_CONSENSUS:
                return 0.12; // Stream is improving
            case PROOF_OF_STAKE:
                return 0.05; // PoS is stable
            case PBFT:
                return 0.03; // PBFT is gradually improving
            case PROOF_OF_WORK:
                return -0.02; // PoW efficiency declining due to difficulty
            default:
                return 0.0;
        }
    }
    
    // ========================================
    // GETTER METHODS
    // ========================================
    
    public ConsensusController getConsensusController() {
        return consensusController;
    }
    
    public ConsensusPolicy getCurrentPolicy() {
        return currentPolicy;
    }
    
    public boolean isAutoOptimizationEnabled() {
        return autoOptimizationEnabled;
    }
    
    public boolean isHealthMonitoringEnabled() {
        return healthMonitoringEnabled;
    }
    
    public ConsensusMetrics getConsensusMetrics() {
        return consensusMetrics;
    }
    
    public ConsensusConfig getConsensusConfig() {
        return consensusConfig;
    }
    
    // ========================================
    // CLEANUP AND SHUTDOWN
    // ========================================
    
    /**
     * Shuts down consensus manager and all monitoring services
     */
    public void shutdown() {
        LOGGER.info("Shutting down ConsensusManager");
        
        try {
            // Shutdown monitoring services
            if (monitoringExecutor != null && !monitoringExecutor.isShutdown()) {
                monitoringExecutor.shutdown();
                monitoringExecutor.awaitTermination(5, TimeUnit.SECONDS);
            }
            
            if (optimizationExecutor != null && !optimizationExecutor.isShutdown()) {
                optimizationExecutor.shutdown();
                optimizationExecutor.awaitTermination(5, TimeUnit.SECONDS);
            }
            
            LOGGER.info("ConsensusManager shutdown completed");
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error during ConsensusManager shutdown", e);
        }
    }
    
    // ========================================
    // RESULT AND STATUS CLASSES
    // ========================================
    
    /**
     * Consensus management results
     */
    public static class ConsensusManagerResult {
        public final boolean success;
        public final String message;
        public final Object data;
        public final ConsensusRecommendation recommendation;
        
        public ConsensusManagerResult(boolean success, String message, Object data, 
                                     ConsensusRecommendation recommendation) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.recommendation = recommendation;
        }
        
        public static ConsensusManagerResult success(String message, Object data) {
            return new ConsensusManagerResult(true, message, data, null);
        }
        
        public static ConsensusManagerResult successWithRecommendation(String message, Object data, 
                                                                      ConsensusRecommendation recommendation) {
            return new ConsensusManagerResult(true, message, data, recommendation);
        }
        
        public static ConsensusManagerResult failure(String message) {
            return new ConsensusManagerResult(false, message, null, null);
        }
    }
    
    /**
     * Policy status information
     */
    public static class PolicyStatus {
        public final ConsensusPolicy policy;
        public final ConsensusRecommendation currentRecommendation;
        
        public PolicyStatus(ConsensusPolicy policy, ConsensusRecommendation recommendation) {
            this.policy = policy;
            this.currentRecommendation = recommendation;
        }
    }
    
    /**
     * Optimization status information
     */
    public static class OptimizationStatus {
        public final boolean enabled;
        public final long intervalSeconds;
        
        public OptimizationStatus(boolean enabled, long intervalSeconds) {
            this.enabled = enabled;
            this.intervalSeconds = intervalSeconds;
        }
    }
    
    /**
     * Prerequisite check result
     */
    private static class PrerequisiteCheckResult {
        public boolean satisfied;
        public String missingRequirements;
    }
    
    // ========================================
    // DATA STRUCTURES
    // ========================================
    
    /**
     * Consensus event record
     */
    private static class ConsensusEvent {
        public LocalDateTime timestamp;
        public ConsensusEventType type;
        public String message;
        public ConsensusType consensusType;
    }
    
    /**
     * Performance data tracking for each consensus type
     */
    public static class ConsensusPerformanceData {
        public ConsensusType consensusType;
        public LocalDateTime startTime;
        public LocalDateTime endTime;
        public long blockHeight;
        public int pendingTransactions;
        public int blocksProcessed;
        public double averageBlockTime;
        public double transactionThroughput;
        public double hashRate;
        public long difficulty;
        public long totalStaked;
        public int stakeholderCount;
        public int validatorCount;
        public long consensusRounds;
    }
    
    /**
     * Consensus health status
     */
    public static class ConsensusHealthStatus {
        public ConsensusType consensusType;
        public boolean isOperational;
        public double overallHealth; // 0.0 to 1.0
        public double performanceScore;
        public double securityScore;
        public double efficiencyScore;
        public ConsensusPerformanceData currentPerformance;
        public List<String> healthIssues;
        public List<ConsensusRecommendation> recommendations;
        public LocalDateTime lastUpdateTime;
        public boolean monitoringEnabled;
    }
    
    /**
     * Consensus recommendation
     */
    public static class ConsensusRecommendation {
        public boolean shouldSwitch;
        public ConsensusType recommendedType;
        public String reason;
        public double confidence; // 0.0 to 1.0
        public List<String> benefits;
        public List<String> considerations;
    }
    
    /**
     * Analytics data structure
     */
    public static class ConsensusAnalytics {
        public Map<ConsensusType, ConsensusPerformanceData> performanceHistory;
        public Map<ConsensusType, Integer> consensusUsageStats;
        public Map<String, Double> performanceTrends;
        public List<ConsensusEvent> recentEvents;
        public List<OptimizationRecord> optimizationHistory;
        public ConsensusMetrics currentMetrics;
    }
    
    /**
     * Optimization record for history tracking
     */
    public static class OptimizationRecord {
        public LocalDateTime timestamp;
        public ConsensusType fromType;
        public ConsensusType toType;
        public String reason;
        public boolean automatic;
        public double performanceImprovement;
    }
    
    // ========================================
    // SPECIALIZED MONITORING COMPONENTS
    // ========================================
    
    /**
     * Consensus health monitoring component
     */
    private static class ConsensusHealthMonitor {
        
        /**
         * Calculates overall consensus health score
         */
        public double calculateOverallHealth() {
            // Implementation for calculating overall consensus health
            // This would analyze various metrics like block time consistency,
            // transaction processing efficiency, network participation, etc.
            return 0.85; // Placeholder
        }
        
        /**
         * Identifies specific health issues
         */
        public List<String> identifyHealthIssues() {
            // Implementation for identifying health issues
            // This would check for problems like slow block times,
            // high memory usage, network connectivity issues, etc.
            return new ArrayList<>(); // Placeholder
        }
    }
    
    /**
     * Consensus optimization component
     */
    private static class ConsensusOptimizer {
        
        /**
         * Recommends optimal consensus based on policy
         */
        public ConsensusRecommendation recommendConsensus(ConsensusPolicy policy) {
            // Implementation for consensus recommendation based on policy
            // This would analyze current performance, network conditions,
            // resource usage, and policy requirements
            ConsensusRecommendation recommendation = new ConsensusRecommendation();
            recommendation.shouldSwitch = false;
            recommendation.reason = "Current consensus is optimal for " + policy.getDisplayName();
            recommendation.confidence = 0.9;
            return recommendation;
        }
        
        /**
         * Provides health-based recommendations
         */
        public List<ConsensusRecommendation> getHealthRecommendations(ConsensusHealthStatus health) {
            // Implementation for health-based recommendations
            // This would analyze health metrics and suggest improvements
            return new ArrayList<>(); // Placeholder
        }
        
    }
}