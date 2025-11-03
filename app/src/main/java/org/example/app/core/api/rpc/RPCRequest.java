package org.example.app.core.api.rpc;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Represents a JSON-RPC request
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RPCRequest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @JsonProperty("jsonrpc")
    private String jsonrpc = "2.0";
    
    @JsonProperty("method")
    private String method;
    
    @JsonProperty("params")
    private Object[] params;
    
    @JsonProperty("id")
    private String id;

    /**
     * Default constructor for Jackson deserialization
     */
    public RPCRequest() {
    }
    
    /**
     * Creates a new RPC request
     */
    public RPCRequest(String method, Object[] params, String id) {
        this.jsonrpc = "2.0";
        this.method = method;
        this.params = params;
        this.id = id;
    }

    /**
     * Creates a new RPC request with no params
     */
    public RPCRequest(String method, String id) {
        this(method, new Object[0], id);
    }
    
    /**
     * Gets the method name
     */
    public String getMethod() { 
        return method; 
    }
    
    /**
     * Sets the method name
     */
    public void setMethod(String method) {
        this.method = method;
    }
    
    /**
     * Gets the parameters
     */
    public Object[] getParams() { 
        return params; 
    }
    
    /**
     * Sets the parameters
     */
    public void setParams(Object[] params) {
        this.params = params;
    }
    
    /**
     * Gets the request ID
     */
    public String getId() { 
        return id; 
    }
    
    /**
     * Sets the request ID
     */
    public void setId(String id) {
        this.id = id;
    }
    
    /**
     * Gets the JSON-RPC version
     */
    public String getJsonrpc() {
        return jsonrpc;
    }
    
    /**
     * Sets the JSON-RPC version
     */
    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }
    
    /**
     * Converts this request to a JSON string
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"jsonrpc\":\"2.0\",\"method\":\"").append(method).append("\"");
            
            // Add params if present
            if (params != null && params.length > 0) {
                sb.append(",\"params\":[");
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) {
                        sb.append(",");
                    }
                    sb.append(String.valueOf(params[i]));
                }
                sb.append("]");
            }
            
            // Add id if present
            if (id != null) {
                sb.append(",\"id\":\"").append(id).append("\"");
            }
            
            sb.append("}");
            return sb.toString();
        }
    }
    
    /**
     * Creates an RPCRequest from a JSON string
     */
    public static RPCRequest fromJson(String json) {
        try {
            return MAPPER.readValue(json, RPCRequest.class);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public String toString() {
        return "RPCRequest{" +
                "jsonrpc='" + jsonrpc + '\'' +
                ", method='" + method + '\'' +
                ", params=" + (params != null ? java.util.Arrays.toString(params) : "null") +
                ", id='" + id + '\'' +
                '}';
    }
}