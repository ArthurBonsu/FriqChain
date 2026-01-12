package org.example.app.core.deployment;
import java.util.logging.Logger;

/**
 * DeploymentScriptGenerator generates deployment scripts.
 */
public class DeploymentScriptGenerator {
    private static final Logger logger = Logger.getLogger(DeploymentScriptGenerator.class.getName());
    
    public String generateNodeScript(String nodeId, String country, String environment) {
        StringBuilder script = new StringBuilder();
        
        script.append("#!/bin/bash\n");
        script.append("# TINC Blockchain Node Deployment Script\n");
        script.append("# Node: ").append(nodeId).append("\n");
        script.append("# Country: ").append(country).append("\n");
        script.append("# Environment: ").append(environment).append("\n\n");
        
        script.append("set -e\n\n");
        
        // Install dependencies
        script.append("echo \"Installing dependencies...\"\n");
        script.append("sudo apt-get update\n");
        script.append("sudo apt-get install -y openjdk-17-jdk\n\n");
        
        // Download blockchain software
        script.append("echo \"Downloading TINC blockchain...\"\n");
        script.append("wget https://releases.tinc.africa/tinc-node-latest.tar.gz\n");
        script.append("tar -xzf tinc-node-latest.tar.gz\n\n");
        
        // Configure node
        script.append("echo \"Configuring node...\"\n");
        script.append("cd tinc-node\n");
        script.append("./configure.sh --node-id=").append(nodeId);
        script.append(" --country=").append(country);
        script.append(" --env=").append(environment).append("\n\n");
        
        // Start node
        script.append("echo \"Starting node...\"\n");
        script.append("sudo systemctl enable tinc-node\n");
        script.append("sudo systemctl start tinc-node\n\n");
        
        script.append("echo \"Node deployed successfully!\"\n");
        
        return script.toString();
    }
    
    public String generateDockerCompose(String environment) {
        StringBuilder compose = new StringBuilder();
        
        compose.append("version: '3.8'\n");
        compose.append("services:\n");
        compose.append("  tinc-node:\n");
        compose.append("    image: tinc/blockchain:latest\n");
        compose.append("    environment:\n");
        compose.append("      - ENV=").append(environment).append("\n");
        compose.append("      - JAVA_OPTS=-Xmx4g\n");
        compose.append("    ports:\n");
        compose.append("      - \"8545:8545\"\n");
        compose.append("      - \"30303:30303\"\n");
        compose.append("    volumes:\n");
        compose.append("      - ./data:/data\n");
        compose.append("      - ./config:/config\n");
        compose.append("    restart: unless-stopped\n");
        
        return compose.toString();
    }
}
