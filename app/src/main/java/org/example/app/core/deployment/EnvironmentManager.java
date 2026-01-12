package org.example.app.core.deployment;

import java.util.*;
import java.util.logging.Logger;

/**
 * EnvironmentManager manages different deployment environments.
 */
public class EnvironmentManager {
    private static final Logger logger = Logger.getLogger(EnvironmentManager.class.getName());
    
    private String currentEnvironment;
    private final Map<String, Environment> environments;
    
    public EnvironmentManager() {
        this.environments = new HashMap<>();
        this.currentEnvironment = "dev";
        initializeEnvironments();
        logger.info("EnvironmentManager initialized");
    }
    
    private void initializeEnvironments() {
        // Development environment
        environments.put("dev", new Environment(
            "dev",
            "Development",
            1,
            false,
            "http://localhost:8545"
        ));
        
        // Staging environment
        environments.put("staging", new Environment(
            "staging",
            "Staging",
            5,
            false,
            "https://staging.tinc.africa"
        ));
        
        // Production environment
        environments.put("production", new Environment(
            "production",
            "Production",
            35,
            true,
            "https://mainnet.tinc.africa"
        ));
    }
    
    public void switchEnvironment(String environment) {
        if (environments.containsKey(environment)) {
            this.currentEnvironment = environment;
            logger.info("Switched to environment: " + environment);
        } else {
            logger.warning("Environment not found: " + environment);
        }
    }
    
    public Environment getCurrentEnvironment() {
        return environments.get(currentEnvironment);
    }
    
    public Environment getEnvironment(String name) {
        return environments.get(name);
    }
    
    public List<String> listEnvironments() {
        return new ArrayList<>(environments.keySet());
    }
    
    public static class Environment {
        private final String name;
        private final String displayName;
        private final int requiredNodes;
        private final boolean productionMode;
        private final String apiEndpoint;
        
        public Environment(String name, String displayName, int nodes, boolean prod, String api) {
            this.name = name;
            this.displayName = displayName;
            this.requiredNodes = nodes;
            this.productionMode = prod;
            this.apiEndpoint = api;
        }
        
        public String getName() { return name; }
        public String getDisplayName() { return displayName; }
        public int getRequiredNodes() { return requiredNodes; }
        public boolean isProductionMode() { return productionMode; }
        public String getApiEndpoint() { return apiEndpoint; }
        
        @Override
        public String toString() {
            return String.format("%s (%s): %d nodes, Production=%s",
                displayName, name, requiredNodes, productionMode);
        }
    }
}
