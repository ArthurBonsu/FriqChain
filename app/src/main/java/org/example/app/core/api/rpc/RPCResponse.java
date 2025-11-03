package org.example.app.core.api.rpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Represents a JSON-RPC response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RPCResponse {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @JsonProperty("jsonrpc")
    private String jsonrpc = "2.0";
    
    @JsonProperty("result")
    private Object result;
    
    @JsonProperty("error")
    private RPCError error;
    
    @JsonProperty("id")
    private String id;

    /**
     * Default constructor for Jackson deserialization
     */
    public RPCResponse() {
    }
    
    /**
     * Creates a new RPC response
     */
    public RPCResponse(Object result, RPCError error, String id) {
        this.jsonrpc = "2.0";
        this.result = result;
        this.error = error;
        this.id = id;
    }
    
    /**
     * Creates a success response
     */
    public static RPCResponse success(Object result, String id) {
        return new RPCResponse(result, null, id);
    }
    
    /**
     * Creates an error response
     */
    public static RPCResponse error(RPCError error, String id) {
        return new RPCResponse(null, error, id);
    }
    
    /**
     * Creates an error response with the given code and message
     */
    public static RPCResponse error(int code, String message, String id) {
        return new RPCResponse(null, new RPCError(code, message), id);
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
     * Gets the result
     */
    public Object getResult() {
        return result;
    }

    /**
     * Sets the result
     */
    public void setResult(Object result) {
        this.result = result;
    }

    /**
     * Gets the error
     */
    public RPCError getError() {
        return error;
    }

    /**
     * Sets the error
     */
    public void setError(RPCError error) {
        this.error = error;
    }

    /**
     * Gets the ID
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the ID
     */
    public void setId(String id) {
        this.id = id;
    }
    
    /**
     * Determines if this is a success response
     */
    public boolean isSuccess() {
        return error == null;
    }
    
    /**
     * Converts this response to a JSON string
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"jsonrpc\":\"2.0\",");
            
            // Add result or error
            if (error == null) {
                sb.append("\"result\":").append(String.valueOf(result));
            } else {
                sb.append("\"error\":").append(error.toJson());
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
     * Creates an RPCResponse from a JSON string
     */
    public static RPCResponse fromJson(String json) {
        try {
            return MAPPER.readValue(json, RPCResponse.class);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public String toString() {
        return "RPCResponse{" +
                "jsonrpc='" + jsonrpc + '\'' +
                ", result=" + result +
                ", error=" + error +
                ", id='" + id + '\'' +
                '}';
    }
}