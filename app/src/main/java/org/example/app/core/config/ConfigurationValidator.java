package org.example.app.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Validates configuration settings for consistency and correctness
 */
public class ConfigurationValidator {
    private static final Logger LOGGER = Logger.getLogger(ConfigurationValidator.class.getName());
    
    private final List<String> validationErrors = new ArrayList<>();
    
    /**
     * Validate all configurations
     */
    public boolean validateAll(Map<String, Object> config) {
        validationErrors.clear();
        
        boolean valid = true;
        
        // Validate each component
        valid &= validateBlockchainSettings(config);
        valid &= validateConsensusSettings(config);
        valid &= validateNetworkSettings(config);
        valid &= validateStorageSettings(config);
        
        if (!valid) {
            LOGGER.warning("Configuration validation failed with " + 
                          validationErrors.size() + " errors");
        }
        
        return valid;
    }
    
    /**
     * Validate blockchain configuration
     */
    public boolean validateBlockchainConfig(BlockchainConfig config) {
        if (config == null) {
            validationErrors.add("Blockchain configuration is null");
            return false;
        }
        
        boolean valid = true;
        
        if (config.getBlockTime() <= 0) {
            validationErrors.add("Block time must be positive");
            valid = false;
        }
        
        if (config.getBlockTime() < 1000) {
            validationErrors.add("Block time less than 1 second may cause issues");
        }
        
        return valid;
    }
    
    /**
     * Validate consensus configuration
     */
    public boolean validateConsensusConfig(ConsensusConfig config) {
        if (config == null) {
            validationErrors.add("Consensus configuration is null");
            return false;
        }
        
        // ConsensusConfig already has its own validate method
        return config.validate();
    }
    
    /**
     * Validate network configuration
     */
    public boolean validateNetworkConfig(NetworkConfig config) {
        if (config == null) {
            validationErrors.add("Network configuration is null");
            return false;
        }
        
        boolean valid = true;
        
        if (config.getNetworkId() == null || config.getNetworkId().isEmpty()) {
            validationErrors.add("Network ID cannot be empty");
            valid = false;
        }
        
        if (config.getMaxPeers() <= 0) {
            validationErrors.add("Max peers must be positive");
            valid = false;
        }
        
        if (config.getPort() <= 0 || config.getPort() > 65535) {
            validationErrors.add("Port must be between 1 and 65535");
            valid = false;
        }
        
        if (config.getPingInterval() <= 0) {
            validationErrors.add("Ping interval must be positive");
            valid = false;
        }
        
        if (config.getSyncInterval() <= 0) {
            validationErrors.add("Sync interval must be positive");
            valid = false;
        }
        
        return valid;
    }
    
    /**
     * Validate storage configuration
     */
    public boolean validateStorageConfig(StorageConfig config) {
        if (config == null) {
            validationErrors.add("Storage configuration is null");
            return false;
        }
        
        boolean valid = true;
        
        if (config.getStorageType() == null || config.getStorageType().isEmpty()) {
            validationErrors.add("Storage type cannot be empty");
            valid = false;
        }
        
        if (config.getDataDir() == null || config.getDataDir().isEmpty()) {
            validationErrors.add("Data directory cannot be empty");
            valid = false;
        }
        
        if (config.getCacheSize() <= 0) {
            validationErrors.add("Cache size must be positive");
            valid = false;
        }
        
        if (config.getWriteBuffer() <= 0) {
            validationErrors.add("Write buffer size must be positive");
            valid = false;
        }
        
        return valid;
    }
    
    /**
     * Validate blockchain settings from map
     */
    private boolean validateBlockchainSettings(Map<String, Object> config) {
        boolean valid = true;
        
        Object blockTime = config.get("blockchain.blockTime");
        if (blockTime instanceof Number) {
            long time = ((Number) blockTime).longValue();
            if (time <= 0) {
                validationErrors.add("Block time must be positive");
                valid = false;
            }
        }
        
        return valid;
    }
    
    /**
     * Validate consensus settings from map
     */
    private boolean validateConsensusSettings(Map<String, Object> config) {
        boolean valid = true;
        
        Object consensusType = config.get("consensus.type");
        if (consensusType == null) {
            validationErrors.add("Consensus type is required");
            valid = false;
        }
        
        return valid;
    }
    
    /**
     * Validate network settings from map
     */
    private boolean validateNetworkSettings(Map<String, Object> config) {
        boolean valid = true;
        
        Object networkId = config.get("network.networkId");
        if (networkId == null || networkId.toString().isEmpty()) {
            validationErrors.add("Network ID is required");
            valid = false;
        }
        
        Object maxPeers = config.get("network.maxPeers");
        if (maxPeers instanceof Number) {
            int peers = ((Number) maxPeers).intValue();
            if (peers <= 0) {
                validationErrors.add("Max peers must be positive");
                valid = false;
            }
        }
        
        return valid;
    }
    
    /**
     * Validate storage settings from map
     */
    private boolean validateStorageSettings(Map<String, Object> config) {
        boolean valid = true;
        
        Object storageType = config.get("storage.storageType");
        if (storageType == null || storageType.toString().isEmpty()) {
            validationErrors.add("Storage type is required");
            valid = false;
        }
        
        return valid;
    }
    
    /**
     * Get validation errors
     */
    public List<String> getValidationErrors() {
        return new ArrayList<>(validationErrors);
    }
    
    /**
     * Clear validation errors
     */
    public void clearErrors() {
        validationErrors.clear();
    }
}