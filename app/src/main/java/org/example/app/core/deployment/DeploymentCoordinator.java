package org.example.app.core.deployment;

import java.util.logging.Logger;

/**
 * DeploymentCoordinator coordinates all deployment operations.
 */
public class DeploymentCoordinator {
    private static final Logger logger = Logger.getLogger(DeploymentCoordinator.class.getName());
    
    private final NodeDeployer nodeDeployer;
    private final ConfigurationManager configManager;
    private final UpdateManager updateManager;
    private final HealthCheckService healthCheck;
    private final BackupManager backupManager;
    private final NetworkConfigurer networkConfigurer;
    private final SecurityManager securityManager;
    private final EnvironmentManager environmentManager;
    private final DeploymentScriptGenerator scriptGenerator;
    
    public DeploymentCoordinator() {
        this.nodeDeployer = new NodeDeployer();
        this.configManager = new ConfigurationManager();
        this.updateManager = new UpdateManager();
        this.healthCheck = new HealthCheckService();
        this.backupManager = new BackupManager();
        this.networkConfigurer = new NetworkConfigurer();
        this.securityManager = new SecurityManager();
        this.environmentManager = new EnvironmentManager();
        this.scriptGenerator = new DeploymentScriptGenerator();
        
        logger.info("DeploymentCoordinator initialized");
    }
    
    /**
     * Deploy production environment
     */
    public ProductionDeploymentResult deployProduction() {
        logger.info("Starting production deployment...");
        
        long startTime = System.currentTimeMillis();
        
        // Switch to production environment
        environmentManager.switchEnvironment("production");
        
        // Deploy Data Availability Committee
        NodeDeployer.DeploymentResult daResult = nodeDeployer.deployDataAvailabilityCommittee();
        
        // Deploy Community Validators (targeting 50%+ community ownership)
        NodeDeployer.DeploymentResult validatorResult = nodeDeployer.deployCommunityValidators(50);
        
        // Start health monitoring
        healthCheck.startMonitoring();
        
        // Create initial backups
        logger.info("Creating initial backups...");
        
        long endTime = System.currentTimeMillis();
        
        boolean success = daResult.getSuccessRate() >= 0.95 && 
                         validatorResult.getSuccessRate() >= 0.90;
        
        ProductionDeploymentResult result = new ProductionDeploymentResult(
            daResult,
            validatorResult,
            success,
            endTime - startTime
        );
        
        logger.info("Production deployment completed: " + 
            (success ? "SUCCESS" : "PARTIAL"));
        
        return result;
    }
    
    /**
     * Deploy staging environment
     */
    public void deployStaging() {
        logger.info("Deploying staging environment...");
        environmentManager.switchEnvironment("staging");
        nodeDeployer.deployCommunityValidators(5);
    }
    
    /**
     * Deploy development environment
     */
    public void deployDevelopment() {
        logger.info("Deploying development environment...");
        environmentManager.switchEnvironment("dev");
        nodeDeployer.deployCommunityValidators(1);
    }
    
    /**
     * Get deployment summary
     */
    public String getDeploymentSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("═══════════════════════════════════════════════════════════\n");
        summary.append("           TINC BLOCKCHAIN DEPLOYMENT SUMMARY\n");
        summary.append("═══════════════════════════════════════════════════════════\n\n");
        
        summary.append("ENVIRONMENT:\n");
        summary.append(environmentManager.getCurrentEnvironment()).append("\n\n");
        
        summary.append("NODES:\n");
        NodeDeployer.DeploymentSummary nodeSummary = nodeDeployer.getDeploymentSummary();
        summary.append(nodeSummary).append("\n");
        
        summary.append("HEALTH:\n");
        int healthyNodes = 0;
        int totalNodes = healthCheck.getAllNodeHealth().size();
        for (HealthCheckService.NodeHealth health : healthCheck.getAllNodeHealth().values()) {
            if (health.isHealthy()) healthyNodes++;
        }
        summary.append(String.format("Healthy Nodes: %d/%d (%.2f%%)\n\n",
            healthyNodes, totalNodes, 
            totalNodes > 0 ? (double) healthyNodes / totalNodes * 100 : 0));
        
        summary.append("═══════════════════════════════════════════════════════════\n");
        
        return summary.toString();
    }
    
    /**
     * Shutdown coordinator
     */
    public void shutdown() {
        nodeDeployer.shutdown();
        updateManager.shutdown();
        healthCheck.shutdown();
        logger.info("DeploymentCoordinator shutdown complete");
    }
    
    // Getters
    public NodeDeployer getNodeDeployer() { return nodeDeployer; }
    public ConfigurationManager getConfigManager() { return configManager; }
    public UpdateManager getUpdateManager() { return updateManager; }
    public HealthCheckService getHealthCheck() { return healthCheck; }
    public BackupManager getBackupManager() { return backupManager; }
    public NetworkConfigurer getNetworkConfigurer() { return networkConfigurer; }
    public SecurityManager getSecurityManager() { return securityManager; }
    public EnvironmentManager getEnvironmentManager() { return environmentManager; }
    public DeploymentScriptGenerator getScriptGenerator() { return scriptGenerator; }
    
    /**
     * Production deployment result
     */
    public static class ProductionDeploymentResult {
        private final NodeDeployer.DeploymentResult daResult;
        private final NodeDeployer.DeploymentResult validatorResult;
        private final boolean overallSuccess;
        private final long durationMs;
        
        public ProductionDeploymentResult(NodeDeployer.DeploymentResult da,
                                         NodeDeployer.DeploymentResult val,
                                         boolean success, long duration) {
            this.daResult = da;
            this.validatorResult = val;
            this.overallSuccess = success;
            this.durationMs = duration;
        }
        
        public boolean isSuccess() { return overallSuccess; }
        
        @Override
        public String toString() {
            return String.format(
                "Production Deployment: %s\nDA Nodes: %s\nValidators: %s\nDuration: %.2fs",
                overallSuccess ? "SUCCESS ✓" : "PARTIAL",
                daResult, validatorResult, durationMs / 1000.0
            );
        }
    }
}
