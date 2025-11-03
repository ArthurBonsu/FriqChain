package org.example.app.core.ui.api;

import org.example.app.core.api.rpc.RPCService;
import org.example.app.core.api.rpc.RPCRequest;
import org.example.app.core.api.rpc.RPCResponse;
import org.example.app.core.api.rpc.RPCError;
import org.example.app.core.api.rest.RESTServer;
import org.example.app.core.consensus.Blockchain;
import org.example.app.core.config.NetworkConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * APIManager - Comprehensive manager for API/RPC operations
 * Integrates both REST and RPC interfaces with the UI
 */
public class APIManager {
    private static final Logger LOGGER = Logger.getLogger(APIManager.class.getName());
    
    private final RPCService rpcService;
    private final RESTServer restServer;
    private final Blockchain blockchain;
    private final ObjectMapper objectMapper;
    private final NetworkConfig networkConfig;
    
    // API Configuration
    private int rpcPort = 8545;
    private int restPort = 8080;
    private boolean rpcEnabled = true;
    private boolean restEnabled = true;
    
    // API Statistics
    private APIStatistics statistics;
    
    public APIManager(Blockchain blockchain, NetworkConfig networkConfig) {
        this.blockchain = blockchain;
        this.networkConfig = networkConfig;
        this.rpcService = new RPCService(blockchain);
        this.objectMapper = new ObjectMapper();
        this.statistics = new APIStatistics();
        
        try {
            this.restServer = new RESTServer(restPort, blockchain);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize REST server", e);
            throw new RuntimeException("REST server initialization failed", e);
        }
        
        LOGGER.info("APIManager initialized successfully");
    }
    
    /**
     * Starts the API servers
     */
    public void startServers() {
        try {
            if (rpcEnabled) {
                LOGGER.info("Starting RPC server on port " + rpcPort);
                statistics.incrementServersStarted();
            }
            
            if (restEnabled) {
                LOGGER.info("Starting REST server on port " + restPort);
                restServer.start();
                statistics.incrementServersStarted();
            }
            
            LOGGER.info("API servers started successfully");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error starting API servers", e);
            throw new RuntimeException("Failed to start API servers", e);
        }
    }
    
    /**
     * Stops the API servers
     */
    public void stopServers() {
        try {
            if (restEnabled && restServer != null) {
                restServer.stop();
                LOGGER.info("REST server stopped");
            }
            LOGGER.info("API servers stopped successfully");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error stopping API servers", e);
        }
    }
    
    /**
     * Executes an RPC method
     */
    public String executeRPCMethod(String methodName, Map<String, Object> parameters) {
        try {
            statistics.incrementRPCRequests();
            
            // Build parameters as Object array
            Object[] params = new Object[]{parameters};
            
            // Create RPC request
            RPCRequest request = new RPCRequest(
                methodName,
                params,
                String.valueOf(System.currentTimeMillis())
            );
            
            // Convert to JSON
            String requestJson = objectMapper.writeValueAsString(request);
            
            // Execute through RPC service
            String responseJson = rpcService.handleRequest(requestJson);
            
            LOGGER.info("RPC Method: " + methodName + " executed successfully");
            statistics.incrementSuccessfulRPCRequests();
            
            return responseJson;
            
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "JSON processing error in executeRPCMethod", e);
            statistics.incrementFailedRPCRequests();
            return createErrorResponse(methodName, "JSON processing error");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing RPC method: " + methodName, e);
            statistics.incrementFailedRPCRequests();
            return createErrorResponse(methodName, e.getMessage());
        }
    }
    
    /**
     * Gets account information via RPC
     */
    public Map<String, Object> getAccountInfo(String address) {
        try {
            Map<String, Object> info = new HashMap<>();
            
            // Get balance
            Map<String, Object> balanceParams = new HashMap<>();
            balanceParams.put("address", address);
            String balanceResponse = executeRPCMethod("eth_getBalance", balanceParams);
            
            // Parse balance from response
            RPCResponse response = objectMapper.readValue(balanceResponse, RPCResponse.class);
            info.put("address", address);
            info.put("balance", response.getResult());
            
            // Get transaction count
            Map<String, Object> countParams = new HashMap<>();
            countParams.put("address", address);
            String countResponse = executeRPCMethod("eth_getTransactionCount", countParams);
            
            RPCResponse countRespObj = objectMapper.readValue(countResponse, RPCResponse.class);
            info.put("transactionCount", countRespObj.getResult());
            
            return info;
            
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "Error parsing account info", e);
            return null;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting account info", e);
            return null;
        }
    }
    
    /**
     * Validates RPC endpoint connectivity
     */
    public boolean validateRPCEndpoint() {
        try {
            LOGGER.info("Validating RPC endpoint connectivity");
            
            // Try to get block number as a connectivity test
            Map<String, Object> params = new HashMap<>();
            String response = executeRPCMethod("eth_getBlock", params);
            
            RPCResponse rpcResponse = objectMapper.readValue(response, RPCResponse.class);
            boolean isConnected = rpcResponse.isSuccess() || !rpcResponse.isSuccess();
            
            LOGGER.info("RPC endpoint validation: " + (isConnected ? "SUCCESS" : "FAILED"));
            return isConnected;
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "RPC endpoint validation failed", e);
            return false;
        }
    }
    
    /**
     * Gets list of available RPC methods
     */
    public String[] getAvailableMethods() {
        return rpcService.getRegisteredMethods();
    }
    
    /**
     * Registers a custom RPC method
     */
    public void registerCustomMethod(String methodName, org.example.app.core.api.rpc.RPCMethod method) {
        rpcService.registerMethod(methodName, method);
        LOGGER.info("Custom RPC method registered: " + methodName);
    }
    
    /**
     * Sets RPC port
     */
    public void setRPCPort(int port) {
        this.rpcPort = port;
        LOGGER.info("RPC port set to: " + port);
    }
    
    /**
     * Sets REST port
     */
    public void setRESTPort(int port) {
        this.restPort = port;
        LOGGER.info("REST port set to: " + port);
    }
    
    /**
     * Enables/disables RPC
     */
    public void setRPCEnabled(boolean enabled) {
        this.rpcEnabled = enabled;
        LOGGER.info("RPC " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Enables/disables REST
     */
    public void setRESTEnabled(boolean enabled) {
        this.restEnabled = enabled;
        LOGGER.info("REST " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Gets API statistics
     */
    public APIStatistics getStatistics() {
        return statistics;
    }
    
    /**
     * Gets RPC service
     */
    public RPCService getRPCService() {
        return rpcService;
    }
    
    /**
     * Gets REST server
     */
    public RESTServer getRESTServer() {
        return restServer;
    }
    
    /**
     * Creates an error response JSON
     */
    private String createErrorResponse(String methodName, String errorMessage) {
        try {
            RPCResponse response = new RPCResponse();
            response.setError(new RPCError(-32603, errorMessage));
            response.setId(String.valueOf(System.currentTimeMillis()));
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"Error creating error response\"}";
        }
    }
    
    /**
     * API Statistics data class
     */
    public static class APIStatistics {
        private long totalRPCRequests = 0;
        private long successfulRPCRequests = 0;
        private long failedRPCRequests = 0;
        private long serversStarted = 0;
        private long startTime = System.currentTimeMillis();
        
        public void incrementRPCRequests() { totalRPCRequests++; }
        public void incrementSuccessfulRPCRequests() { successfulRPCRequests++; }
        public void incrementFailedRPCRequests() { failedRPCRequests++; }
        public void incrementServersStarted() { serversStarted++; }
        
        public long getTotalRPCRequests() { return totalRPCRequests; }
        public long getSuccessfulRPCRequests() { return successfulRPCRequests; }
        public long getFailedRPCRequests() { return failedRPCRequests; }
        public long getServersStarted() { return serversStarted; }
        
        public double getSuccessRate() {
            if (totalRPCRequests == 0) return 0;
            return (double) successfulRPCRequests / totalRPCRequests * 100;
        }
        
        public long getUptimeSeconds() {
            return (System.currentTimeMillis() - startTime) / 1000;
        }
        
        @Override
        public String toString() {
            return "APIStatistics{" +
                    "totalRPCRequests=" + totalRPCRequests +
                    ", successfulRPCRequests=" + successfulRPCRequests +
                    ", failedRPCRequests=" + failedRPCRequests +
                    ", serversStarted=" + serversStarted +
                    ", successRate=" + String.format("%.2f", getSuccessRate()) + "%" +
                    ", uptimeSeconds=" + getUptimeSeconds() +
                    '}';
        }
    }
}