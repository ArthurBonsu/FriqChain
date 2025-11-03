package org.example.app.core.api.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.example.app.core.consensus.Blockchain;
import org.example.app.core.api.rpc.SendTransactionMethod;
import org.example.app.core.api.rpc.GetBalanceMethod;
import org.example.app.core.api.rpc.GetBlockMethod;
import org.example.app.core.api.rpc.GetTransactionCountMethod;

// import org.example.app.core.api.rpc.GetTokenBalanceMethod; // Commented out until TokenRegistry is available
import org.example.app.core.api.rpc.GetBalanceMethod;
import org.example.app.core.api.rpc.GetBlockMethod;
import org.example.app.core.api.rpc.GetTransactionCountMethod;
import org.example.app.core.api.rpc.GetTokenBalanceMethod;
import org.example.app.core.api.rpc.GetTransactionMethod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for handling JSON-RPC requests
 */
public class RPCService {
    private static final Logger LOGGER = Logger.getLogger(RPCService.class.getName());
    
    private final Blockchain blockchain;
    private final ObjectMapper objectMapper;
    private final Map<String, RPCMethod> methods;

    /**
     * Creates a new RPC service
     */
    public RPCService(Blockchain blockchain) {
        this.blockchain = blockchain;
        this.objectMapper = new ObjectMapper();
        this.methods = new ConcurrentHashMap<>();
        registerMethods();
    }

    /**
     * Registers all available RPC methods
     */
    private void registerMethods() {
        // Pass blockchain instance to constructors if they require it
        
        // Account and balance methods
        methods.put("eth_getBalance", new GetBalanceMethod(blockchain));
        // methods.put("eth_getTokenBalance", new GetTokenBalanceMethod(tokenRegistry)); // Commented out until TokenRegistry is available
        
        // Block methods
        methods.put("eth_getBlock", new GetBlockMethod(blockchain));
        methods.put("eth_getBlockByHash", new GetBlockMethod(blockchain));
        methods.put("eth_getBlockByNumber", new GetBlockMethod(blockchain));
        
        // Transaction methods
        methods.put("eth_sendTransaction", new SendTransactionMethod(blockchain));
        methods.put("eth_getTransaction", new GetTransactionMethod(blockchain));
        methods.put("eth_getTransactionByHash", new GetTransactionMethod(blockchain));
        methods.put("eth_getTransactionCount", new GetTransactionCountMethod(blockchain));
        
        // Additional Ethereum-compatible methods (you can implement these later)
        // methods.put("eth_gasPrice", new GetGasPriceMethod(blockchain));
        // methods.put("eth_blockNumber", new GetBlockNumberMethod(blockchain));
        // methods.put("eth_getTransactionReceipt", new GetTransactionReceiptMethod(blockchain));
        // methods.put("eth_call", new CallMethod(blockchain));
        // methods.put("eth_estimateGas", new EstimateGasMethod(blockchain));
        
        // Web3 methods
        // methods.put("web3_clientVersion", new Web3ClientVersionMethod());
        // methods.put("web3_sha3", new Web3Sha3Method());
        
        // Net methods  
        // methods.put("net_version", new NetVersionMethod());
        // methods.put("net_listening", new NetListeningMethod());
        // methods.put("net_peerCount", new NetPeerCountMethod());
        
        LOGGER.info("Registered " + methods.size() + " RPC methods");
    }

    /**
     * Handles an RPC request
     * 
     * @param requestJson The JSON request string
     * @return The JSON response string
     */
    public String handleRequest(String requestJson) {
        LOGGER.info("Handling RPC request: " + requestJson);
        
        try {
            // Parse the request
            RPCRequest request = objectMapper.readValue(requestJson, RPCRequest.class);
            String methodName = request.getMethod();
            
            LOGGER.info("Method requested: " + methodName);
            
            // Find the method implementation
            RPCMethod method = methods.get(methodName);
            
            if (method == null) {
                LOGGER.warning("Method not found: " + methodName);
                return createErrorResponse(RPCError.methodNotFound(), request.getId());
            }

            try {
                // Execute the method - pass the entire request object
                RPCResponse result = method.execute(request);
                return objectMapper.writeValueAsString(result);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error executing method: " + methodName, e);
                return createErrorResponse(RPCError.executionError(e.getMessage()), request.getId());
            }
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "Invalid JSON request", e);
            return createErrorResponse(RPCError.parseError(), null);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error processing request", e);
            return createErrorResponse(RPCError.internalError(e.getMessage()), null);
        }
    }

    /**
     * Creates a success response
     */
    private String createSuccessResponse(Object result, String id) {
        RPCResponse response = RPCResponse.success(result, id);
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "Error serializing success response", e);
            return "{}"; // Fallback
        }
    }

    /**
     * Creates an error response
     */
    private String createErrorResponse(RPCError error, String id) {
        RPCResponse response = RPCResponse.error(error, id);
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "Error serializing error response", e);
            return "{}"; // Fallback
        }
    }
    
    /**
     * Registers a new method
     */
    public void registerMethod(String name, RPCMethod method) {
        methods.put(name, method);
        LOGGER.info("Registered new RPC method: " + name);
    }
    
    /**
     * Unregisters a method
     */
    public void unregisterMethod(String name) {
        RPCMethod removed = methods.remove(name);
        if (removed != null) {
            LOGGER.info("Unregistered RPC method: " + name);
        } else {
            LOGGER.warning("Attempted to unregister non-existent method: " + name);
        }
    }
    
    /**
     * Gets all registered method names
     */
    public String[] getRegisteredMethods() {
        return methods.keySet().toArray(new String[0]);
    }
    
    /**
     * Checks if a method is registered
     */
    public boolean isMethodRegistered(String methodName) {
        return methods.containsKey(methodName);
    }
    
    /**
     * Gets the number of registered methods
     */
    public int getMethodCount() {
        return methods.size();
    }
    
    /**
     * Lists all registered methods (useful for debugging)
     */
    public void listRegisteredMethods() {
        LOGGER.info("=== Registered RPC Methods ===");
        methods.keySet().stream().sorted().forEach(method -> 
            LOGGER.info("  " + method)
        );
        LOGGER.info("Total: " + methods.size() + " methods");
    }
}