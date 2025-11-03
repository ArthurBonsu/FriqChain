package org.example.app.core.api.rest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.example.app.core.api.common.APIResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base controller for handling REST requests
 */
public abstract class LatestBlockHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(LatestBlockHandler.class.getName());
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        
        try {
            // Parse query parameters
            Map<String, String> queryParams = parseQueryParams(query);
            
            // Parse request body for POST/PUT requests
            String requestBody = null;
            if ("POST".equals(method) || "PUT".equals(method)) {
                requestBody = readRequestBody(exchange);
            }
            
            // Add CORS headers
            addCorsHeaders(exchange);
            
            // Handle CORS preflight request
            if ("OPTIONS".equals(method)) {
                handleCorsPreflightRequest(exchange);
                return;
            }
            
            // Dispatch to the appropriate method handler
            switch (method) {
                case "GET":
                    handleGet(exchange, path, queryParams);
                    break;
                case "POST":
                    handlePost(exchange, path, queryParams, requestBody);
                    break;
                case "PUT":
                    handlePut(exchange, path, queryParams, requestBody);
                    break;
                case "DELETE":
                    handleDelete(exchange, path, queryParams);
                    break;
                default:
                    sendResponse(exchange, APIResponse.error("Method not allowed").toJson(), 405);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error handling request: " + path, e);
            sendResponse(exchange, APIResponse.error("Internal server error: " + e.getMessage()).toJson(), 500);
        }
    }
    
    /**
     * Handle a GET request
     */
    protected abstract void handleGet(HttpExchange exchange, String path, Map<String, String> queryParams) 
        throws IOException;
    
    /**
     * Handle a POST request
     */
    protected void handlePost(HttpExchange exchange, String path, Map<String, String> queryParams, String requestBody) 
        throws IOException {
        // Default implementation - override in subclasses
        sendResponse(exchange, APIResponse.error("Method not allowed").toJson(), 405);
    }
    
    /**
     * Handle a PUT request
     */
    protected void handlePut(HttpExchange exchange, String path, Map<String, String> queryParams, String requestBody) 
        throws IOException {
        // Default implementation - override in subclasses
        sendResponse(exchange, APIResponse.error("Method not allowed").toJson(), 405);
    }
    
    /**
     * Handle a DELETE request
     */
    protected void handleDelete(HttpExchange exchange, String path, Map<String, String> queryParams) 
        throws IOException {
        // Default implementation - override in subclasses
        sendResponse(exchange, APIResponse.error("Method not allowed").toJson(), 405);
    }
    
    /**
     * Parse query parameters
     */
    protected Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query != null && !query.isEmpty()) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    String key = pair.substring(0, idx);
                    String value = pair.substring(idx + 1);
                    params.put(key, value);
                }
            }
        }
        return params;
    }
    
    /**
     * Read the request body from the HttpExchange
     */
    protected String readRequestBody(HttpExchange exchange) throws IOException {
        StringBuilder requestBody = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))) {
            String line;
            while ((line = br.readLine()) != null) {
                requestBody.append(line);
            }
        }
        return requestBody.toString();
    }
    
    /**
     * Send an HTTP response
     */
    protected void sendResponse(HttpExchange exchange, String response, int statusCode) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
    
    /**
     * Add CORS headers to the response
     */
    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
    
    /**
     * Handle CORS preflight request
     */
    private void handleCorsPreflightRequest(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Max-Age", "86400");
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }
}