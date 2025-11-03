package org.example.app.core.ui.smartcontract;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class SmartContractUIUtils {
    private static final Pattern CONTRACT_ADDRESS_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{40}$");
    private static final Pattern FUNCTION_SIGNATURE_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*\\([^)]*\\)$");
    private static final Pattern SOLIDITY_IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final DecimalFormat GAS_FORMATTER = new DecimalFormat("#,##0");
    
    // UI Colors matching TINC theme
    public static final Color PRIMARY_COLOR = new Color(0, 255, 136);    // #00FF88
    public static final Color BACKGROUND_COLOR = new Color(22, 33, 78);  // #16214E
    public static final Color SECONDARY_COLOR = new Color(34, 51, 78);   // #22334E
    public static final Color TEXT_COLOR = Color.WHITE;
    public static final Color ERROR_COLOR = new Color(255, 82, 82);     // #FF5252
    public static final Color SUCCESS_COLOR = new Color(76, 175, 80);   // #4CAF50
    public static final Color WARNING_COLOR = new Color(255, 193, 7);   // #FFC107
    
    // Gas limits and validation
    public static final int MIN_GAS_LIMIT = 21000;
    public static final int MAX_GAS_LIMIT = 10_000_000;
    public static final int DEFAULT_CONTRACT_GAS_LIMIT = 3_000_000;
    public static final long MIN_GAS_PRICE = 1000000000L; // 1 Gwei
    public static final long MAX_GAS_PRICE = 1000000000000L; // 1000 Gwei
    
    /**
     * Validates if the given string is a valid Ethereum-style contract address
     */
    public static boolean isValidContractAddress(String address) {
        return address != null && CONTRACT_ADDRESS_PATTERN.matcher(address.trim()).matches();
    }
    
    /**
     * Validates if the given string is a valid Solidity function signature
     */
    public static boolean isValidFunctionSignature(String functionSignature) {
        return functionSignature != null && 
               FUNCTION_SIGNATURE_PATTERN.matcher(functionSignature.trim()).matches();
    }
    
    /**
     * Validates if the given string is a valid Solidity identifier (contract name, variable name, etc.)
     */
    public static boolean isValidSolidityIdentifier(String identifier) {
        return identifier != null && 
               SOLIDITY_IDENTIFIER_PATTERN.matcher(identifier.trim()).matches();
    }
    
    /**
     * Validates if the given gas limit is within acceptable bounds
     */
    public static boolean isValidGasLimit(String gasLimit) {
        try {
            int value = Integer.parseInt(gasLimit.trim());
            return value >= MIN_GAS_LIMIT && value <= MAX_GAS_LIMIT;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Validates if the given gas price is within acceptable bounds
     */
    public static boolean isValidGasPrice(String gasPrice) {
        try {
            long value = Long.parseLong(gasPrice.trim());
            return value >= MIN_GAS_PRICE && value <= MAX_GAS_PRICE;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Validates if the given value represents a valid Wei amount
     */
    public static boolean isValidWeiAmount(String amount) {
        try {
            long value = Long.parseLong(amount.trim());
            return value >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Validates if the Solidity source code has basic structure
     */
    public static boolean isValidSolidityCode(String sourceCode) {
        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            return false;
        }
        
        String code = sourceCode.trim();
        // Basic validation: should contain "contract" keyword
        return code.contains("contract ") && 
               code.contains("{") && 
               code.contains("}");
    }
    
    /**
     * Validates if bytecode is properly formatted hex string
     */
    public static boolean isValidBytecode(byte[] bytecode) {
        return bytecode != null && bytecode.length > 0;
    }
    
    /**
     * Formats gas amount for display
     */
    public static String formatGas(long gas) {
        return GAS_FORMATTER.format(gas);
    }
    
    /**
     * Formats gas with unit for display
     */
    public static String formatGasWithUnit(long gas, String unit) {
        return formatGas(gas) + " " + unit;
    }
    
    /**
     * Formats contract address for compact display
     */
    public static String formatContractAddress(String address) {
        if (address == null || address.length() < 10) {
            return address;
        }
        return address.substring(0, 8) + "..." + address.substring(address.length() - 6);
    }
    
    /**
     * Formats function signature for display
     */
    public static String formatFunctionSignature(String signature) {
        if (signature == null || signature.length() <= 30) {
            return signature;
        }
        return signature.substring(0, 27) + "...";
    }
    
    /**
     * Formats timestamp for display
     */
    public static String formatTimestamp(long timestamp) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(timestamp));
    }
    
    /**
     * Formats bytecode for display (showing first few bytes)
     */
    public static String formatBytecode(byte[] bytecode) {
        if (bytecode == null || bytecode.length == 0) {
            return "Empty";
        }
        
        StringBuilder sb = new StringBuilder("0x");
        int displayLength = Math.min(8, bytecode.length);
        
        for (int i = 0; i < displayLength; i++) {
            sb.append(String.format("%02x", bytecode[i] & 0xFF));
        }
        
        if (bytecode.length > displayLength) {
            sb.append("... (").append(bytecode.length).append(" bytes)");
        }
        
        return sb.toString();
    }
    
    /**
     * Extracts contract name from Solidity source code
     */
    public static String extractContractName(String sourceCode) {
        if (sourceCode == null) return "Unknown";
        
        Pattern pattern = Pattern.compile("contract\\s+([a-zA-Z_][a-zA-Z0-9_]*)");
        Matcher matcher = pattern.matcher(sourceCode);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return "Unknown";
    }
    
    /**
     * Calculates estimated gas cost
     */
    public static long calculateEstimatedCost(int gasLimit, long gasPrice) {
        return (long) gasLimit * gasPrice;
    }
    
    /**
     * Formats estimated cost for display
     */
    public static String formatEstimatedCost(int gasLimit, long gasPrice) {
        long cost = calculateEstimatedCost(gasLimit, gasPrice);
        return formatGas(cost) + " Wei";
    }
    
    /**
     * Validates contract constructor parameters
     */
    public static boolean isValidConstructorParams(String params) {
        if (params == null || params.trim().isEmpty()) {
            return true; // Empty params are valid
        }
        
        // Basic validation - should be comma-separated values
        String trimmed = params.trim();
        return !trimmed.startsWith(",") && !trimmed.endsWith(",");
    }
    
    /**
     * Parses function parameters from string
     */
    public static Object[] parseFunctionParameters(String paramString) {
        if (paramString == null || paramString.trim().isEmpty()) {
            return new Object[0];
        }
        
        String[] parts = paramString.split(",");
        Object[] params = new Object[parts.length];
        
        for (int i = 0; i < parts.length; i++) {
            String param = parts[i].trim();
            
            // Try to parse as different types
            if (param.startsWith("\"") && param.endsWith("\"")) {
                // String parameter
                params[i] = param.substring(1, param.length() - 1);
            } else if (param.equalsIgnoreCase("true") || param.equalsIgnoreCase("false")) {
                // Boolean parameter
                params[i] = Boolean.parseBoolean(param);
            } else {
                // Try numeric parameter
                try {
                    if (param.contains(".")) {
                        params[i] = Double.parseDouble(param);
                    } else {
                        params[i] = Long.parseLong(param);
                    }
                } catch (NumberFormatException e) {
                    // Default to string if can't parse as number
                    params[i] = param;
                }
            }
        }
        
        return params;
    }
    
    /**
     * Formats event data for display
     */
    public static String formatEventData(byte[] eventData) {
        if (eventData == null || eventData.length == 0) {
            return "No data";
        }
        
        // Convert to hex string
        StringBuilder sb = new StringBuilder("0x");
        for (byte b : eventData) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        
        // Truncate if too long
        String hex = sb.toString();
        if (hex.length() > 50) {
            return hex.substring(0, 50) + "...";
        }
        
        return hex;
    }
    
    /**
     * Gets color based on transaction status
     */
    public static Color getStatusColor(String status) {
        if (status == null) return TEXT_COLOR;
        
        switch (status.toUpperCase()) {
            case "SUCCESS":
            case "DEPLOYED":
            case "COMPLETED":
                return SUCCESS_COLOR;
            case "FAILED":
            case "ERROR":
                return ERROR_COLOR;
            case "PENDING":
            case "COMPILING":
                return WARNING_COLOR;
            default:
                return TEXT_COLOR;
        }
    }
}