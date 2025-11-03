package org.example.app.core.api.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Common API response format for REST endpoints
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class APIResponse {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("data")
    private Object data;
    
    @JsonProperty("errors")
    private Map<String, String> errors;
    
    /**
     * Default constructor - creates a success response
     */
    public APIResponse() {
        this.status = "success";
        this.message = null;
        this.data = null;
        this.errors = null;
    }
    
    /**
     * Creates a response with the given data
     */
    public APIResponse(Object data) {
        this.status = "success";
        this.message = null;
        this.data = data;
        this.errors = null;
    }
    
    /**
     * Creates a response with the given message
     */
    public APIResponse(String message) {
        this.status = "success";
        this.message = message;
        this.data = null;
        this.errors = null;
    }
    
    /**
     * Creates a response with the given message and data
     */
    public APIResponse(String message, Object data) {
        this.status = "success";
        this.message = message;
        this.data = data;
        this.errors = null;
    }
    
    /**
     * Creates a response with the given status and message
     */
    public APIResponse(String status, String message) {
        this.status = status;
        this.message = message;
        this.data = null;
        this.errors = null;
    }
    
    /**
     * Creates a response with all fields
     */
    public APIResponse(String status, String message, Object data, Map<String, String> errors) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.errors = errors;
    }
    
    /**
     * Creates a success response
     */
    public static APIResponse success() {
        return new APIResponse();
    }
    
    /**
     * Creates a success response with a message
     */
    public static APIResponse success(String message) {
        return new APIResponse(message);
    }
    
    /**
     * Creates a success response with data
     */
    public static APIResponse success(Object data) {
        return new APIResponse(data);
    }
    
    /**
     * Creates a success response with a message and data
     */
    public static APIResponse success(String message, Object data) {
        return new APIResponse(message, data);
    }
    
    /**
     * Creates an error response with a message
     */
    public static APIResponse error(String message) {
        return new APIResponse("error", message);
    }
    
    /**
     * Creates an error response with a message and error details
     */
    public static APIResponse error(String message, Map<String, String> errors) {
        return new APIResponse("error", message, null, errors);
    }
    
    /**
     * Creates an error response with a message and a single error
     */
    public static APIResponse error(String message, String errorKey, String errorValue) {
        Map<String, String> errors = new HashMap<>();
        errors.put(errorKey, errorValue);
        return new APIResponse("error", message, null, errors);
    }
    
    /**
     * Gets the status
     */
    public String getStatus() {
        return status;
    }
    
    /**
     * Sets the status
     */
    public void setStatus(String status) {
        this.status = status;
    }
    
    /**
     * Gets the message
     */
    public String getMessage() {
        return message;
    }
    
    /**
     * Sets the message
     */
    public void setMessage(String message) {
        this.message = message;
    }
    
    /**
     * Gets the data
     */
    public Object getData() {
        return data;
    }
    
    /**
     * Sets the data
     */
    public void setData(Object data) {
        this.data = data;
    }
    
    /**
     * Gets the errors
     */
    public Map<String, String> getErrors() {
        return errors;
    }
    
    /**
     * Sets the errors
     */
    public void setErrors(Map<String, String> errors) {
        this.errors = errors;
    }
    
    /**
     * Adds an error
     */
    public void addError(String key, String value) {
        if (this.errors == null) {
            this.errors = new HashMap<>();
        }
        this.errors.put(key, value);
    }
    
    /**
     * Converts this response to a JSON string
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
    
    /**
     * Creates an APIResponse from a JSON string
     */
    public static APIResponse fromJson(String json) {
        try {
            return MAPPER.readValue(json, APIResponse.class);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public String toString() {
        return "APIResponse{" +
                "status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", data=" + data +
                ", errors=" + errors +
                '}';
    }
}