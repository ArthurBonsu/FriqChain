package org.example.app.core.ui.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * RPCUIUtils - Utility functions for RPC UI operations
 * Based on your proven TransactionUIUtils.java pattern
 */
public class RPCUIUtils {
    private static final Logger LOGGER = Logger.getLogger(RPCUIUtils.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Formats a hex string value to a readable format
     */
    public static String formatHexValue(String hexValue) {
        try {
            if (hexValue == null || hexValue.isEmpty()) {
                return "0";
            }
            
            // Remove 0x prefix if present
            String cleanHex = hexValue.startsWith("0x") ? hexValue.substring(2) : hexValue;
            
            // Convert hex to decimal
            long value = Long.parseLong(cleanHex, 16);
            return String.format("%,d", value);
            
        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING, "Error formatting hex value: " + hexValue, e);
            return hexValue;
        }
    }
    
    /**
     * Formats a hex string as Ether (wei to ETH conversion)
     */
    public static String formatAsEther(String hexValue) {
        try {
            if (hexValue == null || hexValue.isEmpty()) {
                return "0 ETH";
            }
            
            String cleanHex = hexValue.startsWith("0x") ? hexValue.substring(2) : hexValue;
            long weiValue = Long.parseLong(cleanHex, 16);
            
            // 1 ETH = 10^18 wei
            double etherValue = weiValue / 1e18;
            return String.format("%.6f ETH", etherValue);
            
        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING, "Error formatting as Ether: " + hexValue, e);
            return hexValue;
        }
    }
    
    /**
     * Formats a transaction hash for display (shortened version)
     */
    public static String formatTransactionHash(String hash) {
        if (hash == null || hash.length() < 10) {
            return hash;
        }
        return hash.substring(0, 6) + "..." + hash.substring(hash.length() - 4);
    }
    
    /**
     * Formats an address for display (shortened version)
     */
    public static String formatAddress(String address) {
        if (address == null || address.length() < 10) {
            return address;
        }
        return address.substring(0, 6) + "..." + address.substring(address.length() - 4);
    }
    
    /**
     * Validates an Ethereum address format
     */
    public static boolean isValidAddress(String address) {
        if (address == null || address.isEmpty()) {
            return false;
        }
        
        // Check if it matches 0x followed by 40 hex characters
        return address.matches("^0x[a-fA-F0-9]{40}$");
    }
    
    /**
     * Validates a transaction hash format
     */
    public static boolean isValidTransactionHash(String hash) {
        if (hash == null || hash.isEmpty()) {
            return false;
        }
        
        // Check if it matches 0x followed by 64 hex characters
        return hash.matches("^0x[a-fA-F0-9]{64}$");
    }
    
    /**
     * Converts a decimal string to hex
     */
    public static String decimalToHex(String decimalValue) {
        try {
            long value = Long.parseLong(decimalValue);
            return "0x" + Long.toHexString(value);
        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING, "Error converting to hex: " + decimalValue, e);
            return decimalValue;
        }
    }
    
    /**
     * Converts a hex string to decimal
     */
    public static String hexToDecimal(String hexValue) {
        try {
            String cleanHex = hexValue.startsWith("0x") ? hexValue.substring(2) : hexValue;
            long value = Long.parseLong(cleanHex, 16);
            return String.valueOf(value);
        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING, "Error converting from hex: " + hexValue, e);
            return hexValue;
        }
    }
    
    /**
     * Formats JSON string with pretty printing
     */
    public static String formatJSON(String jsonString) {
        try {
            Object json = objectMapper.readValue(jsonString, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.WARNING, "Error formatting JSON", e);
            return jsonString;
        }
    }
    
    /**
     * Validates JSON string
     */
    public static boolean isValidJSON(String jsonString) {
        try {
            objectMapper.readTree(jsonString);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
    
    /**
     * Extracts error message from RPC response
     */
    @SuppressWarnings("unchecked")
    public static String extractErrorMessage(String jsonResponse) {
        try {
            Map<String, Object> response = objectMapper.readValue(jsonResponse, Map.class);
            
            if (response.containsKey("error")) {
                Map<String, Object> error = (Map<String, Object>) response.get("error");
                if (error.containsKey("message")) {
                    return (String) error.get("message");
                }
            }
            
            return "Unknown error";
        } catch (JsonProcessingException e) {
            return "Error parsing response: " + e.getMessage();
        }
    }
    
    /**
     * Formats timestamp to readable date string
     */
    public static String formatTimestamp(long timestamp) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.format(new Date(timestamp));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error formatting timestamp: " + timestamp, e);
            return String.valueOf(timestamp);
        }
    }
    
    /**
     * Formats block number with commas
     */
    public static String formatBlockNumber(long blockNumber) {
        return String.format("%,d", blockNumber);
    }
    
    /**
     * Formats gas values
     */
    public static String formatGas(long gasValue) {
        return String.format("%,d Wei", gasValue);
    }
    
    /**
     * Formats gas price in Gwei
     */
    public static String formatGasPrice(long gasPriceWei) {
        // 1 Gwei = 10^9 Wei
        double gweiValue = gasPriceWei / 1e9;
        return String.format("%.2f Gwei", gweiValue);
    }
    
    /**
     * Converts parameters map to query string format
     */
    public static String parametersToQueryString(Map<String, Object> parameters) {
        StringBuilder sb = new StringBuilder();
        
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        
        return sb.toString();
    }
    
    /**
     * Parses query string to parameters map
     */
    public static Map<String, String> queryStringToParameters(String queryString) {
        Map<String, String> parameters = new LinkedHashMap<>();
        
        if (queryString != null && !queryString.isEmpty()) {
            String[] pairs = queryString.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    parameters.put(keyValue[0], keyValue[1]);
                }
            }
        }
        
        return parameters;
    }
    
    /**
     * Generates a request ID for RPC calls
     */
    public static String generateRequestId() {
        return String.valueOf(System.currentTimeMillis());
    }
    
    /**
     * Truncates a long string for display
     */
    public static String truncateString(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        
        if (str.length() <= maxLength) {
            return str;
        }
        
        return str.substring(0, maxLength - 3) + "...";
    }
    
    /**
     * Gets the RPC method category from method name
     */
    public static String getMethodCategory(String methodName) {
        if (methodName.startsWith("eth_get")) {
            return "Query";
        } else if (methodName.startsWith("eth_send")) {
            return "Execution";
        } else if (methodName.startsWith("web3_")) {
            return "Utility";
        } else if (methodName.startsWith("net_")) {
            return "Network";
        } else {
            return "Other";
        }
    }
    
    /**
     * Gets RPC method description
     */
    public static String getMethodDescription(String methodName) {
        Map<String, String> descriptions = new HashMap<>();
        
        descriptions.put("eth_getBalance", "Returns the balance of an account in wei");
        descriptions.put("eth_getBlock", "Returns information about a block");
        descriptions.put("eth_getTransaction", "Returns transaction information");
        descriptions.put("eth_getTransactionCount", "Returns number of transactions for an address");
        descriptions.put("eth_sendTransaction", "Submits a transaction to the network");
        descriptions.put("eth_getTokenBalance", "Returns ERC-20 token balance");
        
        return descriptions.getOrDefault(methodName, "No description available");
    }
    
    /**
     * Validates RPC method parameters
     */
    public static boolean validateParameters(String methodName, Map<String, Object> parameters) {
        // Add parameter validation logic based on method name
        switch (methodName) {
            case "eth_getBalance":
                return parameters.containsKey("address") && isValidAddress(parameters.get("address").toString());
                
            case "eth_sendTransaction":
                return parameters.containsKey("from") && parameters.containsKey("to") && 
                       isValidAddress(parameters.get("from").toString()) &&
                       isValidAddress(parameters.get("to").toString());
                
            case "eth_getTransaction":
                return parameters.containsKey("hash") && 
                       isValidTransactionHash(parameters.get("hash").toString());
                
            default:
                return true;
        }
    }
    
    /**
     * Sanitizes user input for RPC parameters
     */
    public static String sanitizeInput(String input) {
        if (input == null) {
            return "";
        }
        
        // Remove any potentially malicious characters
        return input.replaceAll("[^a-zA-Z0-9x.-]", "");
    }
    
    /**
     * Logs RPC operation for debugging
     */
    public static void logRPCOperation(String methodName, Map<String, Object> parameters, String response) {
        LOGGER.info("RPC Operation: " + methodName);
        LOGGER.info("Parameters: " + parameters);
        LOGGER.info("Response: " + truncateString(response, 200));
    }
}