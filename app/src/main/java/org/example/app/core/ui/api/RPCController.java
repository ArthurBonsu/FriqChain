package org.example.app.core.ui.api;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;

import org.example.app.core.api.rpc.RPCService;
import org.example.app.core.api.rpc.RPCRequest;
import org.example.app.core.api.rpc.RPCResponse;
import org.example.app.core.consensus.Blockchain;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RPCController - Handles RPC method execution and UI interactions
 * Based on your proven TransactionController.java pattern
 */
public class RPCController {
    private static final Logger LOGGER = Logger.getLogger(RPCController.class.getName());
    
    private final RPCService rpcService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    
    private Map<String, RPCMethodInfo> methodRegistry;
    private RPCView view;
    
    // RPC Method information for documentation and parameter handling
    private static class RPCMethodInfo {
        String name;
        String description;
        String[] parameters;
        
        RPCMethodInfo(String name, String description, String[] parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }
    }
    
    public RPCController(Blockchain blockchain) {
        this.rpcService = new RPCService(blockchain);
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newFixedThreadPool(2);
        this.methodRegistry = new HashMap<>();
        
        initializeMethodRegistry();
        LOGGER.info("RPCController initialized");
    }
    
    /**
     * Initializes the method registry with available RPC methods
     */
    private void initializeMethodRegistry() {
        // Account and Balance Methods
        methodRegistry.put("eth_getBalance", new RPCMethodInfo(
            "eth_getBalance",
            "Returns the balance of an account in wei (smallest unit of Ether equivalent).",
            new String[]{"address"}
        ));
        
        // Block Methods
        methodRegistry.put("eth_getBlock", new RPCMethodInfo(
            "eth_getBlock",
            "Returns information about a block by hash or number.",
            new String[]{"hash", "number", "latest", "includeTransactions"}
        ));
        
        methodRegistry.put("eth_getBlockByHash", new RPCMethodInfo(
            "eth_getBlockByHash",
            "Returns a block matching the given block hash.",
            new String[]{"hash", "includeTransactions"}
        ));
        
        methodRegistry.put("eth_getBlockByNumber", new RPCMethodInfo(
            "eth_getBlockByNumber",
            "Returns a block matching the given block number.",
            new String[]{"number", "includeTransactions"}
        ));
        
        // Transaction Methods
        methodRegistry.put("eth_getTransaction", new RPCMethodInfo(
            "eth_getTransaction",
            "Returns the information about a transaction requested by transaction hash.",
            new String[]{"hash"}
        ));
        
        methodRegistry.put("eth_getTransactionByHash", new RPCMethodInfo(
            "eth_getTransactionByHash",
            "Returns transaction information by transaction hash.",
            new String[]{"hash"}
        ));
        
        methodRegistry.put("eth_getTransactionCount", new RPCMethodInfo(
            "eth_getTransactionCount",
            "Returns the number of transactions sent from an address.",
            new String[]{"address"}
        ));
        
        methodRegistry.put("eth_sendTransaction", new RPCMethodInfo(
            "eth_sendTransaction",
            "Submits a new transaction to the network.",
            new String[]{"from", "to", "value", "gasPrice", "gasLimit", "data"}
        ));
        
        // Token Methods
        methodRegistry.put("eth_getTokenBalance", new RPCMethodInfo(
            "eth_getTokenBalance",
            "Returns the ERC-20 token balance for an account.",
            new String[]{"token", "address"}
        ));
        
        LOGGER.info("Initialized " + methodRegistry.size() + " RPC methods in registry");
    }
    
    /**
     * Sets the RPC view for this controller
     */
    public void setView(RPCView view) {
        this.view = view;
        LOGGER.info("RPCView connected to RPCController");
        
        // Configure view with available methods
        ObservableList<String> methods = FXCollections.observableArrayList(
            methodRegistry.keySet()
        );
        view.setAvailableMethods(methods);
        
        // Setup button listeners
        view.getExecuteButton().setOnAction(e -> handleExecuteRequest());
        view.getClearButton().setOnAction(e -> handleClearRequest());
        view.getCopyResponseButton().setOnAction(e -> handleCopyResponse());
        view.getMethodComboBox().setOnAction(e -> handleMethodSelection());
    }
    
    /**
     * Handles method selection change
     */
    private void handleMethodSelection() {
        String selectedMethod = view.getSelectedMethod();
        if (selectedMethod != null && methodRegistry.containsKey(selectedMethod)) {
            RPCMethodInfo methodInfo = methodRegistry.get(selectedMethod);
            view.setMethodDescription(methodInfo.description);
            updateParameterLabels(methodInfo);
        }
    }
    
    /**
     * Updates parameter labels based on selected method
     */
    private void updateParameterLabels(RPCMethodInfo methodInfo) {
        LOGGER.info("Parameters for " + methodInfo.name + ": " + String.join(", ", methodInfo.parameters));
    }
    
    /**
     * Handles RPC request execution
     */
    private void handleExecuteRequest() {
        String selectedMethod = view.getSelectedMethod();
        
        if (selectedMethod == null || selectedMethod.isEmpty()) {
            showErrorAlert("Please select an RPC method");
            return;
        }
        
        RPCMethodInfo methodInfo = methodRegistry.get(selectedMethod);
        if (methodInfo == null) {
            showErrorAlert("Method not found: " + selectedMethod);
            return;
        }
        
        // Get parameter values from view
        String[] paramValues = view.getParameterValues(methodInfo.parameters.length);
        
        // Execute request in background thread
        executorService.submit(() -> executeRPCRequest(selectedMethod, methodInfo.parameters, paramValues));
    }
    
    /**
     * Executes the RPC request
     */
    private void executeRPCRequest(String methodName, String[] paramNames, String[] paramValues) {
        try {
            view.showLoadingIndicator(true);
            
            LOGGER.info("Executing RPC method: " + methodName);
            
            // Build parameters map
            Map<String, Object> params = new HashMap<>();
            for (int i = 0; i < paramNames.length && i < paramValues.length; i++) {
                if (paramValues[i] != null && !paramValues[i].isEmpty()) {
                    params.put(paramNames[i], paramValues[i]);
                }
            }
            
            // Create RPC request
            RPCRequest request = new RPCRequest(
                methodName,
                new Object[]{params},
                String.valueOf(System.currentTimeMillis())
            );
            
            // Convert to JSON
            String requestJson = objectMapper.writeValueAsString(request);
            LOGGER.info("RPC Request: " + requestJson);
            
            // Execute request through RPC service
            String responseJson = rpcService.handleRequest(requestJson);
            LOGGER.info("RPC Response: " + responseJson);
            
            // Parse response
            RPCResponse response = objectMapper.readValue(responseJson, RPCResponse.class);
            
            // Format and display response
            String formattedResponse = formatResponse(response);
            boolean isSuccess = response.isSuccess();
            
            view.displayResponse(formattedResponse, isSuccess);
            
            // Add to history
            String historyEntry = methodName + " - " + (isSuccess ? "SUCCESS" : "ERROR");
            view.addToHistory(historyEntry);
            
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "JSON processing error", e);
            view.displayResponse("JSON Error: " + e.getMessage(), false);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing RPC request", e);
            view.displayResponse("Error: " + e.getMessage(), false);
        } finally {
            view.showLoadingIndicator(false);
        }
    }
    
    /**
     * Formats the RPC response for display
     */
    private String formatResponse(RPCResponse response) {
        try {
            // Use Jackson ObjectMapper to pretty-print JSON
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
        } catch (JsonProcessingException e) {
            // Fallback to string representation
            return response.toString();
        }
    }
    
    /**
     * Handles clear request
     */
    private void handleClearRequest() {
        view.clearParameters();
        view.displayResponse("", true);
        LOGGER.info("Request cleared");
    }
    
    /**
     * Handles copy response action
     */
    private void handleCopyResponse() {
        String responseText = view.getResponseText();
        if (responseText != null && !responseText.isEmpty()) {
            // Copy to clipboard
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(responseText);
            clipboard.setContent(content);
            
            showSuccessAlert("Response copied to clipboard");
            LOGGER.info("Response copied to clipboard");
        } else {
            showErrorAlert("No response to copy");
        }
    }
    
    /**
     * Shows success alert
     */
    private void showSuccessAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Success");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    /**
     * Shows error alert
     */
    private void showErrorAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    /**
     * Registers a new RPC method in the controller
     */
    public void registerMethod(String name, String description, String[] parameters) {
        methodRegistry.put(name, new RPCMethodInfo(name, description, parameters));
        LOGGER.info("Registered RPC method: " + name);
    }
    
    /**
     * Gets registered methods
     */
    public String[] getRegisteredMethods() {
        return rpcService.getRegisteredMethods();
    }
    
    /**
     * Lists all registered methods
     */
    public void listRegisteredMethods() {
        rpcService.listRegisteredMethods();
    }
    
    /**
     * Shutdown executor service
     */
    public void shutdown() {
        executorService.shutdown();
        LOGGER.info("RPCController shutdown");
    }
}