package org.example.app.core.ui.api;

import org.example.app.core.api.rpc.RPCService;
import org.example.app.core.api.rpc.RPCRequest;
import org.example.app.core.api.rpc.RPCResponse;
import org.example.app.core.api.rpc.RPCError;
import org.example.app.core.consensus.Blockchain;
import org.example.app.core.block.Transaction;
import org.example.app.core.account.Account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPCManager - Manages RPC operations and API interactions
 * Based on your proven UITransactionManager.java pattern
 */
public class RPCManager {
    private static final Logger LOGGER = Logger.getLogger(RPCManager.class.getName());
    
    private final RPCService rpcService;
    private final Blockchain blockchain;
    private final ObjectMapper objectMapper;
    
    // Request/Response caching
    private Map<String, CachedResponse> responseCache;
    private long cacheExpirationTime = 60000; // 60 seconds
    
    // Statistics and monitoring
    private RPCStatistics statistics;
    
    /**
     * Cached RPC response with timestamp
     */
    private static class CachedResponse {
        String response;
        long timestamp;
        
        CachedResponse(String response) {
            this.response = response;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired(long expirationTime) {
            return System.currentTimeMillis() - timestamp > expirationTime;
        }
    }
    
    public RPCManager(Blockchain blockchain) {
        this.blockchain = blockchain;
        this.rpcService = new RPCService(blockchain);
        this.objectMapper = new ObjectMapper();
        this.responseCache = new ConcurrentHashMap<>();
        this.statistics = new RPCStatistics();
        
        LOGGER.info("RPCManager initialized");
    }
    
    /**
     * Executes an RPC request
     */
    public RPCResponse executeRequest(String methodName, Map<String, Object> parameters) {
        try {
            LOGGER.info("Executing RPC request: " + methodName);
            statistics.incrementRequestCount();
            
            // Check cache for read-only methods
            if (isCacheableMethod(methodName)) {
                String cacheKey = generateCacheKey(methodName, parameters);
                CachedResponse cached = responseCache.get(cacheKey);
                
                if (cached != null && !cached.isExpired(cacheExpirationTime)) {
                    LOGGER.info("Returning cached response for: " + methodName);
                    statistics.incrementCacheHits();
                    return objectMapper.readValue(cached.response, RPCResponse.class);
                }
            }
            
            // Create RPC request
            RPCRequest request = new RPCRequest(
                methodName,
                new Object[]{parameters},
                String.valueOf(System.currentTimeMillis())
            );
            
            // Convert to JSON
            String requestJson = objectMapper.writeValueAsString(request);
            
            // Execute through RPC service
            String responseJson = rpcService.handleRequest(requestJson);
            
            // Cache if applicable
            if (isCacheableMethod(methodName)) {
                String cacheKey = generateCacheKey(methodName, parameters);
                responseCache.put(cacheKey, new CachedResponse(responseJson));
            }
            
            // Parse and return response
            RPCResponse response = objectMapper.readValue(responseJson, RPCResponse.class);
            
            if (response.isSuccess()) {
                statistics.incrementSuccessCount();
            } else {
                statistics.incrementErrorCount();
            }
            
            return response;
            
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "JSON processing error", e);
            statistics.incrementErrorCount();
            return createErrorResponse("JSON processing error: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing RPC request", e);
            statistics.incrementErrorCount();
            return createErrorResponse("Error: " + e.getMessage());
        }
    }
    
    /**
     * Gets account balance using RPC
     */
    public String getBalance(String address) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("address", address);
            
            RPCResponse response = executeRequest("eth_getBalance", params);
            
            if (response.isSuccess()) {
                return (String) response.getResult();
            } else {
                LOGGER.warning("Error getting balance for address: " + address);
                return "0x0";
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in getBalance", e);
            return "0x0";
        }
    }
    
    /**
     * Gets block information using RPC
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getBlock(String blockIdentifier, boolean includeTransactions) {
        try {
            Map<String, Object> params = new HashMap<>();
            
            // Try to determine if identifier is hash or number
            if (blockIdentifier.startsWith("0x")) {
                params.put("hash", blockIdentifier);
            } else {
                try {
                    int blockNumber = Integer.parseInt(blockIdentifier);
                    params.put("number", blockNumber);
                } catch (NumberFormatException e) {
                    // Treat as hash
                    params.put("hash", blockIdentifier);
                }
            }
            
            params.put("includeTransactions", includeTransactions);
            
            RPCResponse response = executeRequest("eth_getBlock", params);
            
            if (response.isSuccess()) {
                return (Map<String, Object>) response.getResult();
            } else {
                LOGGER.warning("Error getting block: " + blockIdentifier);
                return null;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in getBlock", e);
            return null;
        }
    }
    
    /**
     * Gets transaction information using RPC
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getTransaction(String transactionHash) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("hash", transactionHash);
            
            RPCResponse response = executeRequest("eth_getTransaction", params);
            
            if (response.isSuccess()) {
                return (Map<String, Object>) response.getResult();
            } else {
                LOGGER.warning("Error getting transaction: " + transactionHash);
                return null;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in getTransaction", e);
            return null;
        }
    }
    
    /**
     * Gets transaction count using RPC
     */
    public long getTransactionCount(String address) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("address", address);
            
            RPCResponse response = executeRequest("eth_getTransactionCount", params);
            
            if (response.isSuccess()) {
                String hexString = (String) response.getResult();
                return Long.parseLong(hexString.substring(2), 16);
            } else {
                LOGGER.warning("Error getting transaction count for address: " + address);
                return 0;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in getTransactionCount", e);
            return 0;
        }
    }
    
    /**
     * Sends a transaction using RPC
     */
    @SuppressWarnings("unchecked")
    public String sendTransaction(String from, String to, long value, long gasPrice, int gasLimit, String data) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("from", from);
            params.put("to", to);
            params.put("value", value);
            params.put("gasPrice", gasPrice);
            params.put("gasLimit", gasLimit);
            if (data != null && !data.isEmpty()) {
                params.put("data", data);
            }
            
            RPCResponse response = executeRequest("eth_sendTransaction", params);
            
            if (response.isSuccess()) {
                Map<String, Object> result = (Map<String, Object>) response.getResult();
                return (String) result.get("hash");
            } else {
                LOGGER.warning("Error sending transaction from: " + from + " to: " + to);
                return null;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in sendTransaction", e);
            return null;
        }
    }
    
    /**
     * Gets token balance using RPC
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getTokenBalance(String tokenAddress, String accountAddress) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("token", tokenAddress);
            params.put("address", accountAddress);
            
            RPCResponse response = executeRequest("eth_getTokenBalance", params);
            
            if (response.isSuccess()) {
                return (Map<String, Object>) response.getResult();
            } else {
                LOGGER.warning("Error getting token balance");
                return null;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in getTokenBalance", e);
            return null;
        }
    }
    
    /**
     * Determines if a method's results can be cached
     */
    private boolean isCacheableMethod(String methodName) {
        // Read-only methods can be cached
        return methodName.startsWith("eth_get") || 
               methodName.startsWith("web3_") ||
               methodName.startsWith("net_");
    }
    
    /**
     * Generates a cache key for the method and parameters
     */
    private String generateCacheKey(String methodName, Map<String, Object> parameters) {
        try {
            String paramString = objectMapper.writeValueAsString(parameters);
            return methodName + ":" + paramString.hashCode();
        } catch (JsonProcessingException e) {
            return methodName + ":" + parameters.hashCode();
        }
    }
    
    /**
     * Clears the response cache
     */
    public void clearCache() {
        responseCache.clear();
        LOGGER.info("Response cache cleared");
    }
    
    /**
     * Sets cache expiration time in milliseconds
     */
    public void setCacheExpirationTime(long expirationTimeMs) {
        this.cacheExpirationTime = expirationTimeMs;
        LOGGER.info("Cache expiration time set to: " + expirationTimeMs + "ms");
    }
    
    /**
     * Creates an error response
     */
    private RPCResponse createErrorResponse(String errorMessage) {
        RPCResponse response = new RPCResponse();
        response.setError(new RPCError(-32603, errorMessage));
        response.setId(String.valueOf(System.currentTimeMillis()));
        return response;
    }
    
    /**
     * Gets RPC statistics
     */
    public RPCStatistics getStatistics() {
        return statistics;
    }
    
    /**
     * Gets available RPC methods
     */
    public String[] getAvailableMethods() {
        return rpcService.getRegisteredMethods();
    }
    
    /**
     * Registers a custom RPC method
     */
    public void registerCustomMethod(String methodName, String description, String[] parameters) {
        LOGGER.info("Registering custom RPC method: " + methodName);
    }
    
    /**
     * Gets RPC service for direct access if needed
     */
    public RPCService getRPCService() {
        return rpcService;
    }
    
    /**
     * RPC Statistics data class
     */
    public static class RPCStatistics {
        private long totalRequests = 0;
        private long successfulRequests = 0;
        private long failedRequests = 0;
        private long cacheHits = 0;
        
        public void incrementRequestCount() { totalRequests++; }
        public void incrementSuccessCount() { successfulRequests++; }
        public void incrementErrorCount() { failedRequests++; }
        public void incrementCacheHits() { cacheHits++; }
        
        public long getTotalRequests() { return totalRequests; }
        public long getSuccessfulRequests() { return successfulRequests; }
        public long getFailedRequests() { return failedRequests; }
        public long getCacheHits() { return cacheHits; }
        
        public double getSuccessRate() {
            if (totalRequests == 0) return 0;
            return (double) successfulRequests / totalRequests * 100;
        }
        
        public double getCacheHitRate() {
            if (totalRequests == 0) return 0;
            return (double) cacheHits / totalRequests * 100;
        }
        
        @Override
        public String toString() {
            return "RPCStatistics{" +
                    "totalRequests=" + totalRequests +
                    ", successfulRequests=" + successfulRequests +
                    ", failedRequests=" + failedRequests +
                    ", cacheHits=" + cacheHits +
                    ", successRate=" + String.format("%.2f", getSuccessRate()) + "%" +
                    ", cacheHitRate=" + String.format("%.2f", getCacheHitRate()) + "%" +
                    '}';
        }
    }
}