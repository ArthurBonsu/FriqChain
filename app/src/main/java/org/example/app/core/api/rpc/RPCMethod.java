package org.example.app.core.api.rpc;

/**
 * Interface for RPC methods
 */
public interface RPCMethod {
    /**
     * Gets the name of the method
     * 
     * @return The method name
     */
    String getName();
    
    /**
     * Executes the method with the given request
     * 
     * @param request The RPC request
     * @return The RPC response
     */
    RPCResponse execute(RPCRequest request);
}