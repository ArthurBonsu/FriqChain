package org.example.app.core.deployment;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * ConfigurationManager handles configuration across environments.
 */
public class ConfigurationManager {
    private static final Logger logger = Logger.getLogger(ConfigurationManager.class.getName());
    
    private final Map<String, EnvironmentConfig> environments;
    
    public ConfigurationManager() {
        this.environments = new ConcurrentHashMap<>();
        initializeDefaultEnvironments();
        logger.info("ConfigurationManager initialized");
    }
    
    private void initializeDefaultEnvironments() {
        // Development environment
        environments.put("dev", new EnvironmentConfig(
            "dev",
            "localhost:8545",
            1,
            false,
            new HashMap<String, String>() {{
                put("max.validators", "10");
                put("block.time", "5000");
                put("log.level", "DEBUG");
            }}
        ));
        
        // Staging environment
        environments.put("staging", new EnvironmentConfig(
            "staging",
            "staging.tinc.africa:8545",
            5,
            false,
            new HashMap<String, String>() {{
                put("max.validators", "20");
                put("block.time", "3000");
                put("log.level", "INFO");
            }}
        ));
        
        // Production environment
        environments.put("production", new EnvironmentConfig(
            "production",
            "mainnet.tinc.africa:8545",
            35,
            true,
            new HashMap<String, String>() {{
                put("max.validators", "500");
                put("block.time", "2000");
                put("log.level", "WARN");
                put("tps.target", "100000");
                put("sovereignty.enforcement", "true");
            }}
        ));
    }
    
    public EnvironmentConfig getEnvironmentConfig(String environment) {
        return environments.get(environment);
    }
    
    public String getConfigValue(String environment, String key) {
        EnvironmentConfig config = environments.get(environment);
        return config != null ? config.getProperty(key) : null;
    }
    
    public void setConfigValue(String environment, String key, String value) {
        EnvironmentConfig config = environments.get(environment);
        if (config != null) {
            config.setProperty(key, value);
            logger.info("Updated config: " + environment + "." + key + " = " + value);
        }
    }
    
    public static class EnvironmentConfig {
        private final String name;
        private final String rpcEndpoint;
        private final int nodeCount;
        private final boolean productionMode;
        private final Map<String, String> properties;
        
        public EnvironmentConfig(String name, String rpc, int nodes, boolean prod,
                                Map<String, String> props) {
            this.name = name;
            this.rpcEndpoint = rpc;
            this.nodeCount = nodes;
            this.productionMode = prod;
            this.properties = new ConcurrentHashMap<>(props);
        }
        
        public String getName() { return name; }
        public String getRpcEndpoint() { return rpcEndpoint; }
        public int getNodeCount() { return nodeCount; }
        public boolean isProductionMode() { return productionMode; }
        
        public String getProperty(String key) { return properties.get(key); }
        public void setProperty(String key, String value) { properties.put(key, value); }
        public Map<String, String> getAllProperties() { return new HashMap<>(properties); }
    }
}
