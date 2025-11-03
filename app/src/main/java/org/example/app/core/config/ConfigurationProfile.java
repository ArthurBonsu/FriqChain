package org.example.app.core.config;


import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents a configuration profile with predefined settings
 */
@XmlRootElement(name = "profile")
@XmlAccessorType(XmlAccessType.FIELD)
public class ConfigurationProfile implements Serializable {
    private static final long serialVersionUID = 1L;
    
    @XmlAttribute
    private String name;
    
    @XmlElement
    private String description;
    
    @XmlElement
    private long createdTimestamp;
    
    @XmlElement
    private long modifiedTimestamp;
    
    @XmlElementWrapper(name = "settings")
    private Map<String, Object> settings;
    
    private transient boolean hasUnsavedChanges;
    
    // Default constructor for JAXB
    public ConfigurationProfile() {
        this.settings = new HashMap<>();
        this.createdTimestamp = System.currentTimeMillis();
        this.modifiedTimestamp = this.createdTimestamp;
    }
    
    public ConfigurationProfile(String name, String description, Map<String, Object> settings) {
        this();
        this.name = name;
        this.description = description;
        this.settings = new HashMap<>(settings);
    }
    
    /**
     * Create default profile
     */
    public static ConfigurationProfile createDefault() {
        Map<String, Object> defaultSettings = new HashMap<>();
        
        // Blockchain settings
        defaultSettings.put("blockchain.blockTime", 15000L);
        defaultSettings.put("blockchain.maxBlockSize", 8000000L);
        
        // Consensus settings
        defaultSettings.put("consensus.type", "PROOF_OF_WORK");
        defaultSettings.put("consensus.powTargetBlockTime", 15000L);
        defaultSettings.put("consensus.powInitialDifficulty", 1000L);
        
        // Network settings
        defaultSettings.put("network.networkId", "1");
        defaultSettings.put("network.maxPeers", 50);
        defaultSettings.put("network.port", 30303);
        
        // Storage settings
        defaultSettings.put("storage.storageType", "leveldb");
        defaultSettings.put("storage.dataDir", "./data");
        defaultSettings.put("storage.cacheSize", 512);
        
        return new ConfigurationProfile("Default", "Default configuration profile", defaultSettings);
    }
    
    /**
     * Create testnet profile
     */
    public static ConfigurationProfile createTestnet() {
        Map<String, Object> testnetSettings = new HashMap<>();
        
        testnetSettings.put("blockchain.blockTime", 10000L);
        testnetSettings.put("blockchain.maxBlockSize", 4000000L);
        testnetSettings.put("consensus.type", "PROOF_OF_WORK");
        testnetSettings.put("consensus.powInitialDifficulty", 100L);
        testnetSettings.put("network.networkId", "3");
        testnetSettings.put("network.maxPeers", 25);
        testnetSettings.put("storage.storageType", "leveldb");
        testnetSettings.put("storage.cacheSize", 256);
        
        return new ConfigurationProfile("Testnet", "Testnet configuration profile", testnetSettings);
    }
    
    /**
     * Create mainnet profile
     */
    public static ConfigurationProfile createMainnet() {
        Map<String, Object> mainnetSettings = new HashMap<>();
        
        mainnetSettings.put("blockchain.blockTime", 15000L);
        mainnetSettings.put("blockchain.maxBlockSize", 8000000L);
        mainnetSettings.put("consensus.type", "PBFT");
        mainnetSettings.put("consensus.pbftMinValidators", 21);
        mainnetSettings.put("network.networkId", "1");
        mainnetSettings.put("network.maxPeers", 100);
        mainnetSettings.put("storage.storageType", "rocksdb");
        mainnetSettings.put("storage.cacheSize", 1024);
        
        return new ConfigurationProfile("Mainnet", "Mainnet configuration profile", mainnetSettings);
    }
    
    /**
     * Create development profile
     */
    public static ConfigurationProfile createDevelopment() {
        Map<String, Object> devSettings = new HashMap<>();
        
        devSettings.put("blockchain.blockTime", 5000L);
        devSettings.put("blockchain.maxBlockSize", 1000000L);
        devSettings.put("consensus.type", "PROOF_OF_WORK");
        devSettings.put("consensus.powInitialDifficulty", 10L);
        devSettings.put("network.networkId", "1337");
        devSettings.put("network.maxPeers", 5);
        devSettings.put("storage.storageType", "memory");
        devSettings.put("storage.cacheSize", 128);
        
        return new ConfigurationProfile("Development", "Development configuration profile", devSettings);
    }
    
    /**
     * Update setting
     */
    public void updateSetting(String key, Object value) {
        settings.put(key, value);
        modifiedTimestamp = System.currentTimeMillis();
        hasUnsavedChanges = true;
    }
    
    /**
     * Get setting
     */
    public Object getSetting(String key) {
        return settings.get(key);
    }
    
    /**
     * Mark as saved
     */
    public void markSaved() {
        hasUnsavedChanges = false;
    }
    
    // Getters
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Map<String, Object> getSettings() { return new HashMap<>(settings); }
    public long getCreatedTimestamp() { return createdTimestamp; }
    public long getModifiedTimestamp() { return modifiedTimestamp; }
    public boolean hasUnsavedChanges() { return hasUnsavedChanges; }
}