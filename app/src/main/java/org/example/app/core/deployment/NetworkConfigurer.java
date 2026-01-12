package org.example.app.core.deployment;
import java.util.*;
import java.util.logging.Logger;

/**
 * NetworkConfigurer manages network configuration.
 */
public class NetworkConfigurer {
    private static final Logger logger = Logger.getLogger(NetworkConfigurer.class.getName());
    
    public NetworkConfigurer() {
        logger.info("NetworkConfigurer initialized");
    }
    
    public NetworkConfig configureNetwork(String environment) {
        logger.info("Configuring network for environment: " + environment);
        
        if (environment.equals("production")) {
            return new NetworkConfig(
                "mainnet",
                1,
                Arrays.asList(
                    "node-nga-0.tinc.africa:30303",
                    "node-ken-0.tinc.africa:30303",
                    "node-zaf-0.tinc.africa:30303"
                ),
                8545,
                30303
            );
        } else {
            return new NetworkConfig(
                "testnet",
                999,
                Arrays.asList("localhost:30303"),
                8545,
                30303
            );
        }
    }
    
    public static class NetworkConfig {
        private final String networkName;
        private final int chainId;
        private final List<String> bootstrapNodes;
        private final int rpcPort;
        private final int p2pPort;
        
        public NetworkConfig(String name, int chainId, List<String> bootstrap,
                           int rpc, int p2p) {
            this.networkName = name;
            this.chainId = chainId;
            this.bootstrapNodes = bootstrap;
            this.rpcPort = rpc;
            this.p2pPort = p2p;
        }
        
        public String getNetworkName() { return networkName; }
        public int getChainId() { return chainId; }
        public List<String> getBootstrapNodes() { return bootstrapNodes; }
        public int getRpcPort() { return rpcPort; }
        public int getP2pPort() { return p2pPort; }
    }
}
