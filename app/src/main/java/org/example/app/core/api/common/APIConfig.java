package org.example.app.core.api.common;

/**
 * Configuration parameters for the API module
 */
public class APIConfig {
    // Server configuration
    private int restPort = 8080;
    private int rpcPort = 8081;
    private int threadPoolSize = 10;
    private int connectionTimeout = 30000; // 30 seconds
    private int requestTimeout = 60000; // 60 seconds
    
    // CORS configuration
    private boolean corsEnabled = true;
    private String allowedOrigins = "*";
    private String allowedMethods = "GET, POST, OPTIONS";
    private String allowedHeaders = "Content-Type, Authorization, X-Requested-With";
    
    // Rate limiting
    private boolean rateLimitEnabled = false;
    private int maxRequestsPerMinute = 60;
    
    // Caching
    private boolean cachingEnabled = true;
    private int cacheTtlSeconds = 30;
    
    // Default constructor with default values
    public APIConfig() {
    }
    
    // Builder methods for fluent configuration
    public APIConfig withRestPort(int port) {
        this.restPort = port;
        return this;
    }
    
    public APIConfig withRpcPort(int port) {
        this.rpcPort = port;
        return this;
    }
    
    public APIConfig withThreadPoolSize(int size) {
        this.threadPoolSize = size;
        return this;
    }
    
    public APIConfig withConnectionTimeout(int timeout) {
        this.connectionTimeout = timeout;
        return this;
    }
    
    public APIConfig withRequestTimeout(int timeout) {
        this.requestTimeout = timeout;
        return this;
    }
    
    public APIConfig withCorsEnabled(boolean enabled) {
        this.corsEnabled = enabled;
        return this;
    }
    
    public APIConfig withAllowedOrigins(String origins) {
        this.allowedOrigins = origins;
        return this;
    }
    
    public APIConfig withAllowedMethods(String methods) {
        this.allowedMethods = methods;
        return this;
    }
    
    public APIConfig withAllowedHeaders(String headers) {
        this.allowedHeaders = headers;
        return this;
    }
    
    public APIConfig withRateLimiting(boolean enabled, int maxRequests) {
        this.rateLimitEnabled = enabled;
        this.maxRequestsPerMinute = maxRequests;
        return this;
    }
    
    public APIConfig withCaching(boolean enabled, int ttlSeconds) {
        this.cachingEnabled = enabled;
        this.cacheTtlSeconds = ttlSeconds;
        return this;
    }
    
    // Getters
    public int getRestPort() {
        return restPort;
    }
    
    public int getRpcPort() {
        return rpcPort;
    }
    
    public int getThreadPoolSize() {
        return threadPoolSize;
    }
    
    public int getConnectionTimeout() {
        return connectionTimeout;
    }
    
    public int getRequestTimeout() {
        return requestTimeout;
    }
    
    public boolean isCorsEnabled() {
        return corsEnabled;
    }
    
    public String getAllowedOrigins() {
        return allowedOrigins;
    }
    
    public String getAllowedMethods() {
        return allowedMethods;
    }
    
    public String getAllowedHeaders() {
        return allowedHeaders;
    }
    
    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }
    
    public int getMaxRequestsPerMinute() {
        return maxRequestsPerMinute;
    }
    
    public boolean isCachingEnabled() {
        return cachingEnabled;
    }
    
    public int getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }
}