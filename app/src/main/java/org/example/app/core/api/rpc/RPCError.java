package org.example.app.core.api.rpc;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a JSON-RPC error
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RPCError {
    // Standard JSON-RPC error codes
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
    
    // Custom error codes (must be between -32000 and -32099)
    public static final int EXECUTION_ERROR = -32000;
    public static final int RESOURCE_NOT_FOUND = -32001;
    public static final int INVALID_BLOCK = -32002;
    public static final int INVALID_TRANSACTION = -32003;
    public static final int INSUFFICENT_FUNDS = -32004;
    public static final int INVALID_SIGNATURE = -32005;
    
    @JsonProperty("code")
    private final int code;
    
    @JsonProperty("message")
    private final String message;
    
    @JsonProperty("data")
    private final Object data;
    
    /**
     * Creates a new RPC error
     */
    public RPCError(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    
    /**
     * Creates a new RPC error with no data
     */
    public RPCError(int code, String message) {
        this(code, message, null);
    }
    
    // Factory methods for standard errors
    public static RPCError parseError() {
        return new RPCError(PARSE_ERROR, "Parse error");
    }
    
    public static RPCError invalidRequest() {
        return new RPCError(INVALID_REQUEST, "Invalid request");
    }
    
    public static RPCError methodNotFound() {
        return new RPCError(METHOD_NOT_FOUND, "Method not found");
    }
    
    public static RPCError methodNotAllowed() {
        return new RPCError(METHOD_NOT_FOUND, "Method not allowed");
    }
    
    public static RPCError invalidParams(String details) {
        return new RPCError(INVALID_PARAMS, "Invalid params: " + details);
    }
    
    public static RPCError internalError(String details) {
        return new RPCError(INTERNAL_ERROR, "Internal error: " + details);
    }
    
    // Factory methods for custom errors
    public static RPCError executionError(String details) {
        return new RPCError(EXECUTION_ERROR, "Execution error: " + details);
    }
    
    public static RPCError resourceNotFound(String resource) {
        return new RPCError(RESOURCE_NOT_FOUND, "Resource not found: " + resource);
    }
    
    public static RPCError invalidBlock(String details) {
        return new RPCError(INVALID_BLOCK, "Invalid block: " + details);
    }
    
    public static RPCError invalidTransaction(String details) {
        return new RPCError(INVALID_TRANSACTION, "Invalid transaction: " + details);
    }
    
    public static RPCError insufficientFunds(String account) {
        return new RPCError(INSUFFICENT_FUNDS, "Insufficient funds for account: " + account);
    }
    
    public static RPCError invalidSignature() {
        return new RPCError(INVALID_SIGNATURE, "Invalid signature");
    }
    
    // Getters
    public int getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Object getData() {
        return data;
    }
    
    /**
     * Convert the error to a JSON string
     */
    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\"code\":").append(code)
            .append(",\"message\":\"").append(message).append("\"");
        
        if (data != null) {
            json.append(",\"data\":").append(data);
        }
        
        json.append("}");
        return json.toString();
    }
    
    @Override
    public String toString() {
        return "RPCError{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }
}