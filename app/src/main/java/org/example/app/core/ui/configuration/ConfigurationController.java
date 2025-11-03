package org.example.app.core.ui.configuration;

import org.example.app.core.config.*;
import org.example.app.core.ui.consensus.ConsensusController.ConsensusType;
import org.example.app.core.ui.consensus.ConsensusManager.ConsensusPolicy;
import org.example.app.core.ui.dashboard.DashboardController;
import org.example.app.core.security.EncryptionService;
import org.example.app.core.metrics.MetricsCollector;
import org.example.app.core.audit.AuditLogger;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.concurrent.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.lang.ref.WeakReference;
/**
 * Production-ready controller for managing blockchain configuration
 * with thread safety, encryption, auditing, and dashboard integration
 */

 import org.example.app.core.ui.configuration.*;
 
public class ConfigurationController {
    private static final Logger LOGGER = Logger.getLogger(ConfigurationController.class.getName());
    
    // Version for configuration compatibility
    private static final String CONFIG_VERSION = "2.0.0";
    
    // Thread safety
    private final ReadWriteLock configLock = new ReentrantReadWriteLock();
    private static final java.util.concurrent.atomic.AtomicInteger threadCounter = 
        new java.util.concurrent.atomic.AtomicInteger(0);
    private final ExecutorService executorService = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("Config-Worker-" + threadCounter.incrementAndGet());
        return t;
    });
    
    // Configuration instances
    private volatile BlockchainConfig blockchainConfig;
    private volatile ConsensusConfig consensusConfig;
    private volatile NetworkConfig networkConfig;
    private volatile StorageConfig storageConfig;
    
    // Configuration management
    private final ConfigurationManager configManager;
    private final ConfigurationValidator validator;
    private final EncryptionService encryptionService;
    private final AuditLogger auditLogger;
    private final MetricsCollector metrics;
    
    // Configuration history for rollback
    private final Deque<ConfigurationSnapshot> configHistory = new ConcurrentLinkedDeque<>();
    private static final int MAX_HISTORY_SIZE = 50;
    
    // Dashboard integration
    private DashboardController dashboardController;
    private final List<WeakReference<ConfigurationChangeListener>> dashboardListeners = 
        Collections.synchronizedList(new ArrayList<>());
    
    // Properties for UI binding
    private final StringProperty currentProfileName = new SimpleStringProperty("Default");
    private final BooleanProperty hasUnsavedChanges = new SimpleBooleanProperty(false);
    private final BooleanProperty configurationValid = new SimpleBooleanProperty(true);
    private final StringProperty configurationStatus = new SimpleStringProperty("Ready");
    private final DoubleProperty loadProgress = new SimpleDoubleProperty(0.0);
    
    // Observable lists for UI
    private final ObservableList<String> availableProfiles = FXCollections.observableArrayList();
    private final ObservableList<String> recentConfigurations = FXCollections.observableArrayList();
    private final ObservableList<ConfigurationError> validationErrors = FXCollections.observableArrayList();
    
    // Event listeners
    private final List<ConfigurationEventListener> listeners = new CopyOnWriteArrayList<>();
    
    // Configuration cache for performance
    private final Map<String, Object> configCache = new ConcurrentHashMap<>();
    private volatile long cacheTimestamp = 0;
    private static final long CACHE_VALIDITY_MS = 5000; // 5 seconds
    
    // Auto-save service
    private final ScheduledExecutorService autoSaveScheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> autoSaveFuture;
    

    @FXML private Button configNavBtn;  

    /**
     * Configuration event listener interface
     */
    public interface ConfigurationEventListener {
        void onConfigurationChanged(ConfigurationChangeEvent event);
        void onProfileLoaded(String profileName, boolean success);
        void onConfigurationSaved(String filepath, boolean success);
        void onValidationError(List<ConfigurationError> errors);
        void onConfigurationRollback(String snapshotId);
    }
    
    /**
     * Dashboard-specific configuration listener
     */
    public interface ConfigurationChangeListener {
        void onConfigurationUpdate(String component, Map<String, Object> changes);
    }
    
    /**
     * Configuration change event with detailed information
     */
    public static class ConfigurationChangeEvent {
        public final String component;
        public final String property;
        public final Object oldValue;
        public final Object newValue;
        public final String userId;
        public final Instant timestamp;
        public final String changeId;
        
        public ConfigurationChangeEvent(String component, String property, 
                                       Object oldValue, Object newValue, String userId) {
            this.component = component;
            this.property = property;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.userId = userId;
            this.timestamp = Instant.now();
            this.changeId = UUID.randomUUID().toString();
        }
    }
    
    /**
     * Configuration error with context
     */
    public static class ConfigurationError {
        public final String component;
        public final String property;
        public final String message;
        public final ErrorSeverity severity;
        public final Instant timestamp;
        
        public enum ErrorSeverity {
            WARNING, ERROR, CRITICAL
        }
        
        public ConfigurationError(String component, String property, 
                                 String message, ErrorSeverity severity) {
            this.component = component;
            this.property = property;
            this.message = message;
            this.severity = severity;
            this.timestamp = Instant.now();
        }
    }
    
    /**
     * Configuration snapshot for rollback support
     */
    private static class ConfigurationSnapshot {
        public final String id;
        public final BlockchainConfig blockchainConfig;
        public final ConsensusConfig consensusConfig;
        public final NetworkConfig networkConfig;
        public final StorageConfig storageConfig;
        public final Instant timestamp;
        public final String description;
        
        public ConfigurationSnapshot(String description, BlockchainConfig blockchain,
                                    ConsensusConfig consensus, NetworkConfig network,
                                    StorageConfig storage) {
            this.id = UUID.randomUUID().toString();
            this.description = description;
            this.blockchainConfig = blockchain;
            this.consensusConfig = consensus;
            this.networkConfig = network;
            this.storageConfig = storage;
            this.timestamp = Instant.now();
        }
    }
    
    /**
     * Constructor with dependency injection
     */
    public ConfigurationController() {
        this.configManager = new ConfigurationManager();
        this.validator = new ConfigurationValidator();
        this.encryptionService = new EncryptionService();
        this.auditLogger = new AuditLogger("ConfigurationController");
        this.metrics = MetricsCollector.getInstance();
        
        initialize();
    }
    
    /**
     * Initialize controller with error recovery
     */
    private void initialize() {
        try {
            metrics.recordEvent("config.controller.init.start");
            
            // Load default configurations with validation
            loadDefaultConfigurations();
            
            // Load available profiles
            loadAvailableProfiles();
            
            // Initialize auto-save
            setupAutoSave();
            
            // Create initial snapshot
            createSnapshot("Initial configuration");
            
            // Start configuration monitoring
            startConfigurationMonitoring();
            
            metrics.recordEvent("config.controller.init.success");
            LOGGER.info("ConfigurationController initialized successfully");
            
        } catch (Exception e) {
            metrics.recordError("config.controller.init.error", e);
            LOGGER.log(Level.SEVERE, "Critical error initializing ConfigurationController", e);
            
            // Attempt recovery with minimal configuration
            loadEmergencyConfiguration();
        }
    }
    
    /**
     * Load default configurations with validation
     */
    private void loadDefaultConfigurations() {
        configLock.writeLock().lock();
        try {
            blockchainConfig = new BlockchainConfig.Builder().build();
            consensusConfig = ConsensusConfig.createDefault();
            networkConfig = new NetworkConfig.Builder().build();
            storageConfig = new StorageConfig.Builder().build();
            
            // Validate loaded configurations
            if (!validateAllConfigurations()) {
                throw new IllegalStateException("Default configurations are invalid");
            }
            
            auditLogger.logConfigurationLoad("default", "system");
            
        } finally {
            configLock.writeLock().unlock();
        }
    }
    
    /**
     * Load emergency configuration on failure
     */
    private void loadEmergencyConfiguration() {
        LOGGER.warning("Loading emergency configuration");
        configLock.writeLock().lock();
        try {
            // Minimal safe configuration
            blockchainConfig = new BlockchainConfig.Builder()
                .blockTime(30000) // 30 seconds - very conservative
                .build();
                
            consensusConfig = ConsensusConfig.builder()
                .defaultConsensusType(ConsensusType.PROOF_OF_WORK)
                .powInitialDifficulty(1L)
                .build();
                
            networkConfig = new NetworkConfig.Builder()
                .networkId("emergency")
                .maxPeers(5)
                .build();
                
            storageConfig = new StorageConfig.Builder()
                .storageType("memory")
                .build();
                
            configurationStatus.set("Emergency Mode");
            auditLogger.logEmergencyMode("Configuration system");
            
        } finally {
            configLock.writeLock().unlock();
        }
    }
    
    /**
     * Set dashboard controller for integration
     */
    public void setDashboardController(DashboardController dashboard) {
        this.dashboardController = dashboard;
        LOGGER.info("Dashboard controller connected to configuration");
    }
    
    /**
     * Apply configuration changes with transaction support
     */
    public CompletableFuture<Boolean> applyConfiguration(Map<String, Object> changes) {
        return CompletableFuture.supplyAsync(() -> {
            String transactionId = UUID.randomUUID().toString();
            metrics.startTimer("config.apply", transactionId);
            
            configLock.writeLock().lock();
            try {
                LOGGER.info("Applying configuration changes: " + changes.size() + " changes");
                auditLogger.logConfigurationChangeStart(transactionId, changes);
                
                // Create snapshot before changes
                createSnapshot("Before configuration change");
                
                // Validate changes
                List<ConfigurationError> errors = validateChanges(changes);
                if (!errors.isEmpty()) {
                    handleValidationErrors(errors);
                    return false;
                }
                
                // Apply changes atomically
                Map<String, Object> appliedChanges = new HashMap<>();
                
                try {
                    // Apply changes to each component
                    applyComponentChanges(changes, appliedChanges);
                    
                    // Validate resulting configuration
                    if (!validateAllConfigurations()) {
                        // Rollback on validation failure
                        rollbackChanges(appliedChanges);
                        return false;
                    }
                    
                    // Update cache
                    invalidateCache();
                    
                    // Mark as having unsaved changes
                    Platform.runLater(() -> hasUnsavedChanges.set(true));
                    
                    // Notify listeners
                    notifyConfigurationChanged(changes);
                    
                    // Update dashboard
                    updateDashboard(changes);
                    
                    auditLogger.logConfigurationChangeSuccess(transactionId, appliedChanges);
                    metrics.recordSuccess("config.apply");
                    
                    return true;
                    
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error applying configuration", e);
                    rollbackChanges(appliedChanges);
                    auditLogger.logConfigurationChangeFailure(transactionId, e);
                    metrics.recordError("config.apply", e);
                    return false;
                }
                
            } finally {
                configLock.writeLock().unlock();
                metrics.stopTimer("config.apply", transactionId);
            }
        }, executorService);
    }
    
    /**
     * Apply component-specific changes
     */
    private void applyComponentChanges(Map<String, Object> changes, 
                                      Map<String, Object> appliedChanges) throws Exception {
        // Group changes by component
        Map<String, Map<String, Object>> componentChanges = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            String[] parts = key.split("\\.", 2);
            if (parts.length != 2) continue;
            
            String component = parts[0];
            String property = parts[1];
            
            componentChanges.computeIfAbsent(component, k -> new HashMap<>())
                          .put(property, value);
        }
        
        // Apply to each component
        for (Map.Entry<String, Map<String, Object>> entry : componentChanges.entrySet()) {
            String component = entry.getKey();
            Map<String, Object> props = entry.getValue();
            
            switch (component) {
                case "blockchain":
                    applyBlockchainChanges(props, appliedChanges);
                    break;
                case "consensus":
                    applyConsensusChanges(props, appliedChanges);
                    break;
                case "network":
                    applyNetworkChanges(props, appliedChanges);
                    break;
                case "storage":
                    applyStorageChanges(props, appliedChanges);
                    break;
                default:
                    LOGGER.warning("Unknown configuration component: " + component);
            }
        }
    }
    
    /**
     * Apply blockchain configuration changes
     */
    private void applyBlockchainChanges(Map<String, Object> changes, 
                                       Map<String, Object> appliedChanges) throws Exception {
        BlockchainConfig.Builder builder = new BlockchainConfig.Builder();
        
        // Preserve existing values
        builder.blockTime(blockchainConfig.getBlockTime());
        
        // Apply changes
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String property = entry.getKey();
            Object value = entry.getValue();
            Object oldValue = getBlockchainProperty(property);
            
            switch (property) {
                case "blockTime":
                    if (value instanceof Number) {
                        builder.blockTime(((Number) value).longValue());
                        appliedChanges.put("blockchain." + property, oldValue);
                    }
                    break;
                // Add more properties as needed
                default:
                    LOGGER.warning("Unknown blockchain property: " + property);
            }
        }
        
        blockchainConfig = builder.build();
    }
    
    /**
     * Apply consensus configuration changes
     */
    private void applyConsensusChanges(Map<String, Object> changes,
                                      Map<String, Object> appliedChanges) throws Exception {
        ConsensusConfig.Builder builder = consensusConfig.toBuilder();
        
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String property = entry.getKey();
            Object value = entry.getValue();
            Object oldValue = getConsensusProperty(property);
            
            switch (property) {
                case "defaultConsensusType":
                    if (value instanceof ConsensusType) {
                        builder.defaultConsensusType((ConsensusType) value);
                        appliedChanges.put("consensus." + property, oldValue);
                    }
                    break;
                case "pbftViewTimeout":
                    if (value instanceof Number) {
                        builder.pbftViewTimeout(((Number) value).intValue());
                        appliedChanges.put("consensus." + property, oldValue);
                    }
                    break;
                case "powTargetBlockTime":
                    if (value instanceof Number) {
                        builder.powTargetBlockTime(((Number) value).longValue());
                        appliedChanges.put("consensus." + property, oldValue);
                    }
                    break;
                case "posMinimumStake":
                    if (value instanceof Number) {
                        builder.posMinimumStake(((Number) value).longValue());
                        appliedChanges.put("consensus." + property, oldValue);
                    }
                    break;
                // Add more consensus properties
                default:
                    LOGGER.warning("Unknown consensus property: " + property);
            }
        }
        
        consensusConfig = builder.build();
    }
    

    /**
     * Apply network configuration changes
     */
    private void applyNetworkChanges(Map<String, Object> changes,
                                    Map<String, Object> appliedChanges) throws Exception {
        NetworkConfig.Builder builder = new NetworkConfig.Builder();
        
        // Preserve existing values
        builder.networkId(networkConfig.getNetworkId())
               .maxPeers(networkConfig.getMaxPeers());
        
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String property = entry.getKey();
            Object value = entry.getValue();
            Object oldValue = getNetworkProperty(property);
            
            switch (property) {
                case "networkId":
                    if (value instanceof String) {
                        builder.networkId((String) value);
                        appliedChanges.put("network." + property, oldValue);
                    }
                    break;
                case "maxPeers":
                    if (value instanceof Number) {
                        builder.maxPeers(((Number) value).intValue());
                        appliedChanges.put("network." + property, oldValue);
                    }
                    break;
                // Add more network properties
                default:
                    LOGGER.warning("Unknown network property: " + property);
            }
        }
        
        networkConfig = builder.build();
    }
    
    /**
     * Apply storage configuration changes
     */
    private void applyStorageChanges(Map<String, Object> changes,
                                    Map<String, Object> appliedChanges) throws Exception {
        StorageConfig.Builder builder = new StorageConfig.Builder();
        
        // Preserve existing values
        builder.storageType(storageConfig.getStorageType());
        
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String property = entry.getKey();
            Object value = entry.getValue();
            Object oldValue = getStorageProperty(property);
            
            switch (property) {
                case "storageType":
                    if (value instanceof String) {
                        builder.storageType((String) value);
                        appliedChanges.put("storage." + property, oldValue);
                    }
                    break;
                // Add more storage properties
                default:
                    LOGGER.warning("Unknown storage property: " + property);
            }
        }
        
        storageConfig = builder.build();
    }
    
    /**
     * Get blockchain property value
     */
    private Object getBlockchainProperty(String property) {
        switch (property) {
            case "blockTime":
                return blockchainConfig.getBlockTime();
            default:
                return null;
        }
    }
    
    /**
     * Get consensus property value
     */
    private Object getConsensusProperty(String property) {
        switch (property) {
            case "defaultConsensusType":
                return consensusConfig.getDefaultConsensusType();
            case "pbftViewTimeout":
                return consensusConfig.getPbftViewTimeout();
            case "powTargetBlockTime":
                return consensusConfig.getPowTargetBlockTime();
            case "posMinimumStake":
                return consensusConfig.getPosMinimumStake();
            default:
                return null;
        }
    }
    
    /**
     * Get network property value
     */
    private Object getNetworkProperty(String property) {
        switch (property) {
            case "networkId":
                return networkConfig.getNetworkId();
            case "maxPeers":
                return networkConfig.getMaxPeers();
            default:
                return null;
        }
    }
    
    /**
     * Get storage property value
     */
    private Object getStorageProperty(String property) {
        switch (property) {
            case "storageType":
                return storageConfig.getStorageType();
            default:
                return null;
        }
    }
    
    /**
     * Validate configuration changes
     */
    private List<ConfigurationError> validateChanges(Map<String, Object> changes) {
        List<ConfigurationError> errors = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            String[] parts = key.split("\\.", 2);
            if (parts.length != 2) {
                errors.add(new ConfigurationError(
                    "unknown", key, "Invalid configuration key format",
                    ConfigurationError.ErrorSeverity.ERROR
                ));
                continue;
            }
            
            String component = parts[0];
            String property = parts[1];
            
            // Component-specific validation
            switch (component) {
                case "blockchain":
                    validateBlockchainProperty(property, value, errors);
                    break;
                case "consensus":
                    validateConsensusProperty(property, value, errors);
                    break;
                case "network":
                    validateNetworkProperty(property, value, errors);
                    break;
                case "storage":
                    validateStorageProperty(property, value, errors);
                    break;
            }
        }
        
        return errors;
    }
    
    /**
     * Validate blockchain property
     */
    private void validateBlockchainProperty(String property, Object value,
                                           List<ConfigurationError> errors) {
        switch (property) {
            case "blockTime":
                if (!(value instanceof Number)) {
                    errors.add(new ConfigurationError(
                        "blockchain", property, "Block time must be a number",
                        ConfigurationError.ErrorSeverity.ERROR
                    ));
                } else {
                    long blockTime = ((Number) value).longValue();
                    if (blockTime <= 0) {
                        errors.add(new ConfigurationError(
                            "blockchain", property, "Block time must be positive",
                            ConfigurationError.ErrorSeverity.ERROR
                        ));
                    } else if (blockTime < 1000) {
                        errors.add(new ConfigurationError(
                            "blockchain", property, "Block time less than 1s may cause issues",
                            ConfigurationError.ErrorSeverity.WARNING
                        ));
                    }
                }
                break;
        }
    }
    

    
    /**
     * Validate consensus property
     */
    private void validateConsensusProperty(String property, Object value,
                                          List<ConfigurationError> errors) {
        // Add consensus-specific validation
    }
    
    /**
     * Validate network property
     */
    private void validateNetworkProperty(String property, Object value,
                                        List<ConfigurationError> errors) {
        switch (property) {
            case "maxPeers":
                if (!(value instanceof Number)) {
                    errors.add(new ConfigurationError(
                        "network", property, "Max peers must be a number",
                        ConfigurationError.ErrorSeverity.ERROR
                    ));
                } else {
                    int maxPeers = ((Number) value).intValue();
                    if (maxPeers <= 0) {
                        errors.add(new ConfigurationError(
                            "network", property, "Max peers must be positive",
                            ConfigurationError.ErrorSeverity.ERROR
                        ));
                    }
                }
                break;
        }
    }
    
    /**
     * Validate storage property
     */
    private void validateStorageProperty(String property, Object value,
                                        List<ConfigurationError> errors) {
        // Add storage-specific validation
    }
    
    /**
     * Rollback configuration changes
     */
    private void rollbackChanges(Map<String, Object> appliedChanges) {
        LOGGER.warning("Rolling back configuration changes");
        
        if (!appliedChanges.isEmpty()) {
            // Restore previous values
            Map<String, Object> rollbackChanges = new HashMap<>();
            
            for (Map.Entry<String, Object> entry : appliedChanges.entrySet()) {
                String key = entry.getKey();
                Object oldValue = entry.getValue();
                rollbackChanges.put(key, oldValue);
            }
            
            try {
                // Apply rollback without validation
                applyComponentChanges(rollbackChanges, new HashMap<>());
                auditLogger.logConfigurationRollback(rollbackChanges);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to rollback configuration", e);
                // Last resort - restore from snapshot
                restoreLatestSnapshot();
            }
        }
    }
    
    /**
     * Handle validation errors
     */
    private void handleValidationErrors(List<ConfigurationError> errors) {
        Platform.runLater(() -> {
            validationErrors.clear();
            validationErrors.addAll(errors);
            configurationValid.set(false);
        });
        
        for (ConfigurationEventListener listener : listeners) {
            listener.onValidationError(errors);
        }
        
        metrics.recordCounter("config.validation.errors", errors.size());
    }
    
    /**
     * Validate all configurations
     */
    private boolean validateAllConfigurations() {
        List<ConfigurationError> errors = new ArrayList<>();
        
        configLock.readLock().lock();
        try {
            // Validate each configuration
            if (!validator.validateBlockchainConfig(blockchainConfig)) {
                errors.add(new ConfigurationError(
                    "blockchain", "all", "Blockchain configuration is invalid",
                    ConfigurationError.ErrorSeverity.ERROR
                ));
            }
            
            if (!consensusConfig.validate()) {
                errors.add(new ConfigurationError(
                    "consensus", "all", "Consensus configuration is invalid",
                    ConfigurationError.ErrorSeverity.ERROR
                ));
            }
            
            if (!validator.validateNetworkConfig(networkConfig)) {
                errors.add(new ConfigurationError(
                    "network", "all", "Network configuration is invalid",
                    ConfigurationError.ErrorSeverity.ERROR
                ));
            }
            
            if (!validator.validateStorageConfig(storageConfig)) {
                errors.add(new ConfigurationError(
                    "storage", "all", "Storage configuration is invalid",
                    ConfigurationError.ErrorSeverity.ERROR
                ));
            }
            
            Platform.runLater(() -> {
                validationErrors.clear();
                validationErrors.addAll(errors);
                configurationValid.set(errors.isEmpty());
            });
            
            return errors.isEmpty();
            
        } finally {
            configLock.readLock().unlock();
        }
    }
    
    /**
     * Save configuration with encryption
     */
    public CompletableFuture<Boolean> saveConfiguration(String filepath) {
        return CompletableFuture.supplyAsync(() -> {
            String saveId = UUID.randomUUID().toString();
            metrics.startTimer("config.save", saveId);
            
            configLock.readLock().lock();
            try {
                LOGGER.info("Saving configuration to: " + filepath);
                
                Properties props = new Properties();
                
                // Collect all configuration properties
                collectConfigurationProperties(props);
                
                // Add metadata
                props.setProperty("version", CONFIG_VERSION);
                props.setProperty("timestamp", Instant.now().toString());
                props.setProperty("checksum", calculateChecksum(props));
                
                // Encrypt sensitive data
                encryptSensitiveProperties(props);
                
                // Save to file with atomic write
                Path tempFile = Paths.get(filepath + ".tmp");
                Path targetFile = Paths.get(filepath);
                
                try (OutputStream out = Files.newOutputStream(tempFile)) {
                    props.store(out, "TINC Blockchain Configuration v" + CONFIG_VERSION);
                }
                
                // Atomic move
                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING,
                          StandardCopyOption.ATOMIC_MOVE);
                
                // Update state
                Platform.runLater(() -> {
                    hasUnsavedChanges.set(false);
                    recentConfigurations.add(0, filepath);
                    if (recentConfigurations.size() > 10) {
                        recentConfigurations.remove(10);
                    }
                });
                
                // Notify listeners
                for (ConfigurationEventListener listener : listeners) {
                    listener.onConfigurationSaved(filepath, true);
                }
                
                auditLogger.logConfigurationSave(filepath, "user");
                metrics.recordSuccess("config.save");
                
                return true;
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error saving configuration", e);
                
                for (ConfigurationEventListener listener : listeners) {
                    listener.onConfigurationSaved(filepath, false);
                }
                
                metrics.recordError("config.save", e);
                return false;
                
            } finally {
                configLock.readLock().unlock();
                metrics.stopTimer("config.save", saveId);
            }
        }, executorService);
    }
    
    /**
     * Collect all configuration properties
     */
    private void collectConfigurationProperties(Properties props) {
        // Blockchain config
        props.setProperty("blockchain.blockTime", String.valueOf(blockchainConfig.getBlockTime()));
        
        // Consensus config
        consensusConfig.toProperties().forEach((key, value) -> 
            props.setProperty("consensus." + key, String.valueOf(value)));
        
        // Network config
        props.setProperty("network.networkId", networkConfig.getNetworkId());
        props.setProperty("network.maxPeers", String.valueOf(networkConfig.getMaxPeers()));
        props.setProperty("network.port", String.valueOf(networkConfig.getPort()));
        props.setProperty("network.pingInterval", String.valueOf(networkConfig.getPingInterval()));
        props.setProperty("network.syncInterval", String.valueOf(networkConfig.getSyncInterval()));
        
        // Storage config
        props.setProperty("storage.storageType", storageConfig.getStorageType());
        props.setProperty("storage.dataDir", storageConfig.getDataDir());
        props.setProperty("storage.cacheSize", String.valueOf(storageConfig.getCacheSize()));
        props.setProperty("storage.compression", String.valueOf(storageConfig.isCompression()));
        props.setProperty("storage.writeBuffer", String.valueOf(storageConfig.getWriteBuffer()));
    }
    
    /**
     * Encrypt sensitive properties
     */
    private void encryptSensitiveProperties(Properties props) throws Exception {
        Set<String> sensitiveKeys = Set.of(
            "consensus.validatorPrivateKey",
            "network.tlsCertificate",
            "storage.encryptionKey"
        );
        
        for (String key : sensitiveKeys) {
            String value = props.getProperty(key);
            if (value != null && !value.isEmpty()) {
                String encrypted = encryptionService.encrypt(value);
                props.setProperty(key, "ENC:" + encrypted);
            }
        }
    }
    
    /**
     * Calculate configuration checksum
     */
    private String calculateChecksum(Properties props) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        
        // Sort properties for consistent checksum
        TreeMap<String, String> sorted = new TreeMap<>();
        props.forEach((k, v) -> sorted.put(k.toString(), v.toString()));
        
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            digest.update(entry.getKey().getBytes());
            digest.update(entry.getValue().getBytes());
        }
        
        byte[] hash = digest.digest();
        return Base64.getEncoder().encodeToString(hash);
    }
    
    /**
     * Load configuration with decryption and validation
     */
    public CompletableFuture<Boolean> loadConfiguration(String filepath) {
        return CompletableFuture.supplyAsync(() -> {
            String loadId = UUID.randomUUID().toString();
            metrics.startTimer("config.load", loadId);
            
            configLock.writeLock().lock();
            try {
                LOGGER.info("Loading configuration from: " + filepath);
                
                Path configPath = Paths.get(filepath);
                if (!Files.exists(configPath)) {
                    throw new FileNotFoundException("Configuration file not found: " + filepath);
                }
                
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(configPath)) {
                    props.load(in);
                }
                
                // Verify version compatibility
                String version = props.getProperty("version");
                if (!isVersionCompatible(version)) {
                    throw new IllegalStateException("Incompatible configuration version: " + version);
                }
                
                // Verify checksum
                String storedChecksum = props.getProperty("checksum");
                props.remove("checksum");
                String calculatedChecksum = calculateChecksum(props);
                if (!calculatedChecksum.equals(storedChecksum)) {
                    LOGGER.warning("Configuration checksum mismatch - file may be corrupted");
                }
                
                // Decrypt sensitive properties
                decryptSensitiveProperties(props);
                
                // Parse and apply configuration
                Map<String, Object> changes = parseProperties(props);
                
                // Create snapshot before loading
                createSnapshot("Before loading configuration");
                
                // Apply changes
                applyComponentChanges(changes, new HashMap<>());
                
                // Validate loaded configuration
                if (!validateAllConfigurations()) {
                    throw new IllegalStateException("Loaded configuration is invalid");
                }
                
                // Update state
                Platform.runLater(() -> {
                    hasUnsavedChanges.set(false);
                    configurationStatus.set("Loaded from: " + filepath);
                });
                
                // Update dashboard
                updateDashboard(changes);
                
                auditLogger.logConfigurationLoad(filepath, "user");
                metrics.recordSuccess("config.load");
                
                return true;
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error loading configuration", e);
                
                // Restore from snapshot on failure
                restoreLatestSnapshot();
                
                metrics.recordError("config.load", e);
                return false;
                
            } finally {
                configLock.writeLock().unlock();
                metrics.stopTimer("config.load", loadId);
            }
        }, executorService);
    }
    
    /**
     * Check version compatibility
     */
    private boolean isVersionCompatible(String version) {
        if (version == null) return false;
        
        String[] current = CONFIG_VERSION.split("\\.");
        String[] loaded = version.split("\\.");
        
        // Major version must match
        return current[0].equals(loaded[0]);
    }
    
    /**
     * Decrypt sensitive properties
     */
    private void decryptSensitiveProperties(Properties props) throws Exception {
        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key);
            if (value.startsWith("ENC:")) {
                String encrypted = value.substring(4);
                String decrypted = encryptionService.decrypt(encrypted);
                props.setProperty(key, decrypted);
            }
        }
    }
    
    /**
     * Parse properties into configuration map
     */
    private Map<String, Object> parseProperties(Properties props) {
        Map<String, Object> changes = new HashMap<>();
        
        for (String key : props.stringPropertyNames()) {
            if (key.equals("version") || key.equals("timestamp")) {
                continue;
            }
            
            String value = props.getProperty(key);
            
            // Parse value based on key
            if (key.contains("Time") || key.contains("Interval") || 
                key.contains("Size") || key.contains("Difficulty")) {
                try {
                    changes.put(key, Long.parseLong(value));
                } catch (NumberFormatException e) {
                    changes.put(key, value);
                }
            } else if (key.contains("maxPeers") || key.contains("port") || 
                      key.contains("validators")) {
                try {
                    changes.put(key, Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    changes.put(key, value);
                }
            } else if (key.contains("enabled") || key.contains("Enable") || 
                      key.contains("compression")) {
                changes.put(key, Boolean.parseBoolean(value));
            } else if (key.contains("rate") || key.contains("threshold") || 
                      key.contains("factor")) {
                try {
                    changes.put(key, Double.parseDouble(value));
                } catch (NumberFormatException e) {
                    changes.put(key, value);
                }
            } else if (key.equals("consensus.defaultConsensusType")) {
                try {
                    changes.put(key, ConsensusType.valueOf(value));
                } catch (IllegalArgumentException e) {
                    changes.put(key, value);
                }
            } else {
                changes.put(key, value);
            }
        }
        
        return changes;
    }
    
    /**
     * Load profile with validation
     */
    public CompletableFuture<Boolean> loadProfile(String profileName) {
        return CompletableFuture.supplyAsync(() -> {
            metrics.recordEvent("config.profile.load", profileName);
            
            try {
                LOGGER.info("Loading profile: " + profileName);
                
                ConfigurationProfile profile = configManager.getProfile(profileName);
                if (profile == null) {
                    LOGGER.warning("Profile not found: " + profileName);
                    return false;
                }
                
                // Create snapshot before loading profile
                createSnapshot("Before loading profile: " + profileName);
                
                // Apply profile settings
                Map<String, Object> profileSettings = profile.getSettings();
                Boolean result = applyConfiguration(profileSettings).get(5, TimeUnit.SECONDS);
                
                if (result) {
                    Platform.runLater(() -> {
                        currentProfileName.set(profileName);
                        hasUnsavedChanges.set(false);
                    });
                    
                    // Notify listeners
                    for (ConfigurationEventListener listener : listeners) {
                        listener.onProfileLoaded(profileName, true);
                    }
                    
                    auditLogger.logProfileLoad(profileName, "user");
                    metrics.recordSuccess("config.profile.load");
                    
                    return true;
                } else {
                    for (ConfigurationEventListener listener : listeners) {
                        listener.onProfileLoaded(profileName, false);
                    }
                    return false;
                }
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error loading profile: " + profileName, e);
                metrics.recordError("config.profile.load", e);
                return false;
            }
        }, executorService);
    }
    
    /**
     * Create configuration snapshot
     */
    private void createSnapshot(String description) {
        configLock.readLock().lock();
        try {
            ConfigurationSnapshot snapshot = new ConfigurationSnapshot(
                description,
                blockchainConfig,
                consensusConfig,
                networkConfig,
                storageConfig
            );
            
            configHistory.addFirst(snapshot);
            
            // Limit history size
            while (configHistory.size() > MAX_HISTORY_SIZE) {
                configHistory.removeLast();
            }
            
            LOGGER.fine("Created configuration snapshot: " + description);
            
        } finally {
            configLock.readLock().unlock();
        }
    }
    
    /**
     * Restore from latest snapshot
     */
    private void restoreLatestSnapshot() {
        ConfigurationSnapshot snapshot = configHistory.peekFirst();
        if (snapshot != null) {
            restoreSnapshot(snapshot);
        }
    }
    
    /**
     * Restore specific snapshot
     */
    public void restoreSnapshot(String snapshotId) {
        configLock.writeLock().lock();
        try {
            ConfigurationSnapshot snapshot = configHistory.stream()
                .filter(s -> s.id.equals(snapshotId))
                .findFirst()
                .orElse(null);
                
            if (snapshot != null) {
                restoreSnapshot(snapshot);
            }
        } finally {
            configLock.writeLock().unlock();
        }
    }
    
    /**
     * Restore configuration from snapshot
     */
    private void restoreSnapshot(ConfigurationSnapshot snapshot) {
        configLock.writeLock().lock();
        try {
            blockchainConfig = snapshot.blockchainConfig;
            consensusConfig = snapshot.consensusConfig;
            networkConfig = snapshot.networkConfig;
            storageConfig = snapshot.storageConfig;
            
            invalidateCache();
            
            Platform.runLater(() -> {
                configurationStatus.set("Restored from snapshot: " + snapshot.description);
            });
            
            for (ConfigurationEventListener listener : listeners) {
                listener.onConfigurationRollback(snapshot.id);
            }
            
            auditLogger.logSnapshotRestore(snapshot.id, snapshot.description);
            
            LOGGER.info("Restored configuration from snapshot: " + snapshot.description);
            
        } finally {
            configLock.writeLock().unlock();
        }
    }
    
    /**
     * Setup auto-save functionality
     */
    private void setupAutoSave() {
        autoSaveFuture = autoSaveScheduler.scheduleAtFixedRate(() -> {
            if (hasUnsavedChanges.get()) {
                String autoSavePath = "config/autosave/config_" + 
                    LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME) + 
                    ".properties";
                    
                saveConfiguration(autoSavePath).thenAccept(success -> {
                    if (success) {
                        LOGGER.info("Auto-saved configuration to: " + autoSavePath);
                    }
                });
            }
        }, 5, 5, TimeUnit.MINUTES);
    }
    
    /**
     * Start configuration monitoring
     */
    private void startConfigurationMonitoring() {
        executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(10000); // Check every 10 seconds
                    
                    // Monitor configuration health
                    if (!validateAllConfigurations()) {
                        Platform.runLater(() -> 
                            configurationStatus.set("Configuration validation issues detected"));
                    }
                    
                    // Monitor resource usage
                    Runtime runtime = Runtime.getRuntime();
                    long memoryUsage = runtime.totalMemory() - runtime.freeMemory();
                    metrics.recordGauge("config.memory.usage", memoryUsage);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
    
    /**
     * Load available profiles
     */
    private void loadAvailableProfiles() {
        availableProfiles.clear();
        
        // Add system profiles
        availableProfiles.addAll(Arrays.asList(
            "Default", "Testnet", "Mainnet", "Development", "Custom"
        ));
        
        // Load user profiles
        try {
            Path profilesDir = Paths.get("config/profiles");
            if (Files.exists(profilesDir) && Files.isDirectory(profilesDir)) {
                Files.list(profilesDir)
                    .filter(path -> path.toString().endsWith(".profile"))
                    .forEach(path -> {
                        String filename = path.getFileName().toString();
                        String profileName = filename.replace(".profile", "");
                        if (!availableProfiles.contains(profileName)) {
                            availableProfiles.add(profileName);
                        }
                    });
            }
        } catch (IOException e) {
            LOGGER.warning("Error loading user profiles: " + e.getMessage());
        }
    }
    
    /**
     * Invalidate configuration cache
     */
    private void invalidateCache() {
        configCache.clear();
        cacheTimestamp = 0;
    }
    
    /**
     * Get cached configuration value
     */
    private Object getCachedValue(String key) {
        if (System.currentTimeMillis() - cacheTimestamp > CACHE_VALIDITY_MS) {
            invalidateCache();
            return null;
        }
        return configCache.get(key);
    }
    
    /**
     * Update dashboard with configuration changes
     */
    private void updateDashboard(Map<String, Object> changes) {
        if (dashboardController != null) {
            Platform.runLater(() -> {
                try {
                    // Notify dashboard of configuration changes
                    dashboardController.onConfigurationChanged(changes);
                    
                    // Update specific dashboard components based on changes
                    for (Map.Entry<String, Object> entry : changes.entrySet()) {
                        String key = entry.getKey();
                        
                        if (key.startsWith("consensus.")) {
                            dashboardController.updateConsensusDisplay();
                        } else if (key.startsWith("network.")) {
                            dashboardController.updateNetworkDisplay();
                        } else if (key.startsWith("blockchain.")) {
                            dashboardController.updateBlockchainMetrics();
                        }
                    }
                    
                    LOGGER.info("Dashboard updated with configuration changes");
                    
                } catch (Exception e) {
                    LOGGER.warning("Error updating dashboard: " + e.getMessage());
                }
            });
        }
        
        // Notify registered listeners
        synchronized (dashboardListeners) {
            dashboardListeners.removeIf(ref -> ref.get() == null);
            
            for (WeakReference<ConfigurationChangeListener> ref : dashboardListeners) {
                ConfigurationChangeListener listener = ref.get();
                if (listener != null) {
                    // Group changes by component
                    Map<String, Map<String, Object>> componentChanges = new HashMap<>();
                    
                    for (Map.Entry<String, Object> entry : changes.entrySet()) {
                        String key = entry.getKey();
                        String[] parts = key.split("\\.", 2);
                        if (parts.length == 2) {
                            componentChanges.computeIfAbsent(parts[0], k -> new HashMap<>())
                                          .put(parts[1], entry.getValue());
                        }
                    }
                    
                    // Notify listener for each component
                    for (Map.Entry<String, Map<String, Object>> entry : componentChanges.entrySet()) {
                        listener.onConfigurationUpdate(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
    }
    
    /**
     * Register dashboard configuration listener
     */
    public void addDashboardListener(ConfigurationChangeListener listener) {
        dashboardListeners.add(new WeakReference<>(listener));
    }
    
    /**
     * Notify configuration changed
     */
    private void notifyConfigurationChanged(Map<String, Object> changes) {
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            String[] parts = key.split("\\.", 2);
            if (parts.length == 2) {
                ConfigurationChangeEvent event = new ConfigurationChangeEvent(
                    parts[0], parts[1], null, value, "user"
                );
                
                for (ConfigurationEventListener listener : listeners) {
                    listener.onConfigurationChanged(event);
                }
            }
        }
    }
    
    /**
     * Export configuration
     */
    public CompletableFuture<Boolean> exportConfiguration(String filepath, String format) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> allConfig = getAllConfigurations();
                
                switch (format.toLowerCase()) {
                    case "json":
                        return configManager.exportToJson(filepath, allConfig);
                    case "xml":
                        return configManager.exportToXml(filepath, allConfig);
                    default:
                        return saveConfiguration(filepath).get();
                }
            } catch (Exception e) {
                LOGGER.severe("Error exporting configuration: " + e.getMessage());
                return false;
            }
        }, executorService);
    }
    
    /**
     * Import configuration
     */
    public CompletableFuture<Boolean> importConfiguration(String filepath, String format) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> imported;
                
                switch (format.toLowerCase()) {
                    case "json":
                        imported = configManager.importFromJson(filepath);
                        break;
                    case "xml":
                        imported = configManager.importFromXml(filepath);
                        break;
                    default:
                        return loadConfiguration(filepath).get();
                }
                
                return applyConfiguration(imported).get();
                
            } catch (Exception e) {
                LOGGER.severe("Error importing configuration: " + e.getMessage());
                return false;
            }
        }, executorService);
    }
    
    /**
     * Get all configurations as map
     */
    public Map<String, Object> getAllConfigurations() {
        configLock.readLock().lock();
        try {
            Properties props = new Properties();
            collectConfigurationProperties(props);
            
            Map<String, Object> result = new HashMap<>();
            props.forEach((k, v) -> result.put(k.toString(), v));
            
            return result;
            
        } finally {
            configLock.readLock().unlock();
        }
    }
    
    /**
     * Reset to default configuration
     */
    public void resetToDefaults() {
        configLock.writeLock().lock();
        try {
            createSnapshot("Before reset to defaults");
            loadDefaultConfigurations();
            
            Platform.runLater(() -> {
                currentProfileName.set("Default");
                hasUnsavedChanges.set(false);
                configurationStatus.set("Reset to defaults");
            });
            
            // Update dashboard
            updateDashboard(getAllConfigurations());
            
            auditLogger.logConfigurationReset("user");
            LOGGER.info("Configuration reset to defaults");
            
        } finally {
            configLock.writeLock().unlock();
        }
    }
    
    /**
     * Shutdown controller
     */
    public void shutdown() {
        LOGGER.info("Shutting down ConfigurationController");
        
        // Cancel auto-save
        if (autoSaveFuture != null) {
            autoSaveFuture.cancel(false);
        }
        
        // Shutdown schedulers
        autoSaveScheduler.shutdown();
        executorService.shutdown();
        
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!autoSaveScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                autoSaveScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            autoSaveScheduler.shutdownNow();
        }
        
        // Save final state if needed
        if (hasUnsavedChanges.get()) {
            try {
                saveConfiguration("config/shutdown_save.properties").get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOGGER.warning("Failed to save configuration on shutdown: " + e.getMessage());
            }
        }
        
        LOGGER.info("ConfigurationController shutdown complete");
    }
    
    // Event listener management
    public void addEventListener(ConfigurationEventListener listener) {
        listeners.add(listener);
    }
    
    public void removeEventListener(ConfigurationEventListener listener) {
        listeners.remove(listener);
    }
    
    // Getters with thread safety
    public BlockchainConfig getBlockchainConfig() {
        configLock.readLock().lock();
        try {
            return blockchainConfig;
        } finally {
            configLock.readLock().unlock();
        }
    }
    
    public ConsensusConfig getConsensusConfig() {
        configLock.readLock().lock();
        try {
            return consensusConfig;
        } finally {
            configLock.readLock().unlock();
        }
    }
    
    public NetworkConfig getNetworkConfig() {
        configLock.readLock().lock();
        try {
            return networkConfig;
        } finally {
            configLock.readLock().unlock();
        }
    }
    
    public StorageConfig getStorageConfig() {
        configLock.readLock().lock();
        try {
            return storageConfig;
        } finally {
            configLock.readLock().unlock();
        }
    }
    
    // Property getters for UI binding
    public StringProperty currentProfileNameProperty() { return currentProfileName; }
    public BooleanProperty hasUnsavedChangesProperty() { return hasUnsavedChanges; }
    public BooleanProperty configurationValidProperty() { return configurationValid; }
    public StringProperty configurationStatusProperty() { return configurationStatus; }
    public DoubleProperty loadProgressProperty() { return loadProgress; }
    public ObservableList<String> getAvailableProfiles() { return availableProfiles; }
    public ObservableList<String> getRecentConfigurations() { return recentConfigurations; }
    public ObservableList<ConfigurationError> getValidationErrors() { return validationErrors; }

    /**
     * Get the configuration manager
     */
    public ConfigurationManager getConfigManager() {
        return configManager;
    }

    /**
     * Validate configuration (public method for UI)
     */
    public boolean validateConfiguration() {
        return validateAllConfigurations();
    }

    /**
     * Get the current configuration as a map
     *
     * @return A map containing all current configuration settings
     */
    public Map<String, Object> getCurrentConfiguration() {
        return getAllConfigurations();
    }
}