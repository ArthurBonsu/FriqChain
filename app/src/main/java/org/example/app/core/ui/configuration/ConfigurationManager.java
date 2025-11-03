package org.example.app.core.ui.configuration;

import org.example.app.core.config.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

// With these specific imports:
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Manages configuration state, persistence, and profiles for the UI
 */
public class ConfigurationManager {
    private static final Logger LOGGER = Logger.getLogger(ConfigurationManager.class.getName());
    
    private final Map<String, ConfigurationProfile> profiles;
    private final List<ConfigurationChangeListener> changeListeners;
    private final ScheduledExecutorService scheduler;
    private final Gson gson;
    private ConfigurationProfile activeProfile;
    private boolean autoSaveEnabled = true;
    private int autoSaveIntervalSeconds = 300; // 5 minutes
    
    /**
     * Configuration change listener interface
     */
    public interface ConfigurationChangeListener {
        void onConfigurationChanged(ConfigurationChangeEvent event);
    }
    
    /**
     * Configuration change event
     */
    public static class ConfigurationChangeEvent {
        public final String component;
        public final String property;
        public final Object oldValue;
        public final Object newValue;
        public final long timestamp;
        
        public ConfigurationChangeEvent(String component, String property, 
                                       Object oldValue, Object newValue) {
            this.component = component;
            this.property = property;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    public ConfigurationManager() {
        this.profiles = new ConcurrentHashMap<>();
        this.changeListeners = new CopyOnWriteArrayList<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        
        initialize();
    }
    
    private void initialize() {
        // Load default profiles
        loadDefaultProfiles();
        
        // Load user profiles
        loadUserProfiles();
        
        // Start auto-save if enabled
        if (autoSaveEnabled) {
            startAutoSave();
        }
        
        LOGGER.info("ConfigurationManager initialized with " + profiles.size() + " profiles");
    }
    
    /**
     * Load default profiles
     */
    private void loadDefaultProfiles() {
        // Default profile
        ConfigurationProfile defaultProfile = ConfigurationProfile.createDefault();
        profiles.put("Default", defaultProfile);
        
        // Testnet profile
        ConfigurationProfile testnetProfile = ConfigurationProfile.createTestnet();
        profiles.put("Testnet", testnetProfile);
        
        // Mainnet profile
        ConfigurationProfile mainnetProfile = ConfigurationProfile.createMainnet();
        profiles.put("Mainnet", mainnetProfile);
        
        // Development profile
        ConfigurationProfile devProfile = ConfigurationProfile.createDevelopment();
        profiles.put("Development", devProfile);
    }
    
    /**
     * Load user-created profiles from disk
     */
    private void loadUserProfiles() {
        File profilesDir = new File("config/profiles");
        if (!profilesDir.exists()) {
            profilesDir.mkdirs();
            return;
        }
        
        File[] profileFiles = profilesDir.listFiles((dir, name) -> 
            name.endsWith(".json") || name.endsWith(".xml"));
            
        if (profileFiles != null) {
            for (File file : profileFiles) {
                try {
                    ConfigurationProfile profile = loadProfileFromFile(file);
                    if (profile != null) {
                        profiles.put(profile.getName(), profile);
                        LOGGER.info("Loaded user profile: " + profile.getName());
                    }
                } catch (Exception e) {
                    LOGGER.warning("Failed to load profile from " + file.getName() + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Load profile from file
     */
    private ConfigurationProfile loadProfileFromFile(File file) throws IOException {
        if (file.getName().endsWith(".json")) {
            try (Reader reader = new FileReader(file)) {
                return gson.fromJson(reader, ConfigurationProfile.class);
            }
        } else if (file.getName().endsWith(".xml")) {
            try {
                JAXBContext context = JAXBContext.newInstance(ConfigurationProfile.class);
                Unmarshaller unmarshaller = context.createUnmarshaller();
                return (ConfigurationProfile) unmarshaller.unmarshal(file);
            } catch (JAXBException e) {
                throw new IOException("Failed to unmarshal XML", e);
            }
        }
        return null;
    }
    
    /**
     * Save profile to file
     */
    public boolean saveProfile(ConfigurationProfile profile, String filepath) {
        try {
            File file = new File(filepath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            if (filepath.endsWith(".json")) {
                try (Writer writer = new FileWriter(file)) {
                    gson.toJson(profile, writer);
                }
            } else if (filepath.endsWith(".xml")) {
                JAXBContext context = JAXBContext.newInstance(ConfigurationProfile.class);
                Marshaller marshaller = context.createMarshaller();
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
                marshaller.marshal(profile, file);
            } else {
                // Save as properties file
                Properties props = new Properties();
                profile.getSettings().forEach((key, value) -> 
                    props.setProperty(key, String.valueOf(value)));
                    
                try (FileOutputStream out = new FileOutputStream(file)) {
                    props.store(out, "Configuration Profile: " + profile.getName());
                }
            }
            
            LOGGER.info("Profile saved: " + profile.getName() + " to " + filepath);
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Failed to save profile: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Create a new profile
     */
    public ConfigurationProfile createProfile(String name, String description, 
                                             Map<String, Object> settings) {
        ConfigurationProfile profile = new ConfigurationProfile(name, description, settings);
        profiles.put(name, profile);
        
        // Save to disk
        String filepath = "config/profiles/" + name + ".json";
        saveProfile(profile, filepath);
        
        return profile;
    }
    
    /**
     * Delete a profile
     */
    public boolean deleteProfile(String name) {
        if ("Default".equals(name) || "Testnet".equals(name) || 
            "Mainnet".equals(name) || "Development".equals(name)) {
            LOGGER.warning("Cannot delete system profile: " + name);
            return false;
        }
        
        ConfigurationProfile removed = profiles.remove(name);
        if (removed != null) {
            // Delete from disk
            File file = new File("config/profiles/" + name + ".json");
            if (file.exists()) {
                file.delete();
            }
            
            LOGGER.info("Profile deleted: " + name);
            return true;
        }
        
        return false;
    }
    
    /**
     * Export configuration to JSON
     */
    public boolean exportToJson(String filepath, Map<String, Object> config) {
        try (Writer writer = new FileWriter(filepath)) {
            gson.toJson(config, writer);
            LOGGER.info("Configuration exported to JSON: " + filepath);
            return true;
        } catch (IOException e) {
            LOGGER.severe("Failed to export to JSON: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Export configuration to XML
     */
    public boolean exportToXml(String filepath, Map<String, Object> config) {
        try {
            // Create a wrapper for XML serialization
            ConfigurationWrapper wrapper = new ConfigurationWrapper(config);
            
            JAXBContext context = JAXBContext.newInstance(ConfigurationWrapper.class);
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(wrapper, new File(filepath));
            
            LOGGER.info("Configuration exported to XML: " + filepath);
            return true;
            
        } catch (JAXBException e) {
            LOGGER.severe("Failed to export to XML: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Import configuration from JSON
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> importFromJson(String filepath) throws IOException {
        try (Reader reader = new FileReader(filepath)) {
            Map<String, Object> config = gson.fromJson(reader, Map.class);
            LOGGER.info("Configuration imported from JSON: " + filepath);
            return config;
        }
    }
    
    /**
     * Import configuration from XML
     */
    public Map<String, Object> importFromXml(String filepath) throws IOException {
        try {
            JAXBContext context = JAXBContext.newInstance(ConfigurationWrapper.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            ConfigurationWrapper wrapper = (ConfigurationWrapper) unmarshaller.unmarshal(new File(filepath));
            
            LOGGER.info("Configuration imported from XML: " + filepath);
            return wrapper.getConfig();
            
        } catch (JAXBException e) {
            throw new IOException("Failed to import from XML", e);
        }
    }
    
    /**
     * Start auto-save
     */
    private void startAutoSave() {
        scheduler.scheduleAtFixedRate(() -> {
            if (activeProfile != null && activeProfile.hasUnsavedChanges()) {
                String filepath = "config/profiles/" + activeProfile.getName() + ".json";
                saveProfile(activeProfile, filepath);
                activeProfile.markSaved();
                LOGGER.info("Auto-saved profile: " + activeProfile.getName());
            }
        }, autoSaveIntervalSeconds, autoSaveIntervalSeconds, TimeUnit.SECONDS);
    }
    
    /**
     * Notify configuration change
     */
    public void notifyChange(String component, String property, Object oldValue, Object newValue) {
        ConfigurationChangeEvent event = new ConfigurationChangeEvent(component, property, oldValue, newValue);
        for (ConfigurationChangeListener listener : changeListeners) {
            listener.onConfigurationChanged(event);
        }
    }
    
    /**
     * Validate configuration consistency
     */
    public boolean validateConfiguration(Map<String, Object> config) {
        ConfigurationValidator validator = new ConfigurationValidator();
        return validator.validateAll(config);
    }
    
    /**
     * Merge configurations
     */
    public Map<String, Object> mergeConfigurations(Map<String, Object> base, Map<String, Object> overlay) {
        Map<String, Object> merged = new HashMap<>(base);
        merged.putAll(overlay);
        return merged;
    }
    
    /**
     * Shutdown the manager
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        
        LOGGER.info("ConfigurationManager shutdown complete");
    }
    
    // Getters and setters
    public ConfigurationProfile getProfile(String name) {
        return profiles.get(name);
    }
    
    public Set<String> getProfileNames() {
        return new HashSet<>(profiles.keySet());
    }
    
    public ConfigurationProfile getActiveProfile() {
        return activeProfile;
    }
    
    public void setActiveProfile(ConfigurationProfile profile) {
        this.activeProfile = profile;
    }
    
    public void addChangeListener(ConfigurationChangeListener listener) {
        changeListeners.add(listener);
    }
    
    public void removeChangeListener(ConfigurationChangeListener listener) {
        changeListeners.remove(listener);
    }
    
    public boolean isAutoSaveEnabled() {
        return autoSaveEnabled;
    }
    
    public void setAutoSaveEnabled(boolean enabled) {
        this.autoSaveEnabled = enabled;
    }
    
    /**
     * Wrapper class for XML serialization
     */
    @XmlRootElement(name = "configuration")
    private static class ConfigurationWrapper {
        private Map<String, Object> config;
        
        public ConfigurationWrapper() {}
        
        public ConfigurationWrapper(Map<String, Object> config) {
            this.config = config;
        }
        
        @XmlElement
        public Map<String, Object> getConfig() {
            return config;
        }
        
        public void setConfig(Map<String, Object> config) {
            this.config = config;
        }
    }
}