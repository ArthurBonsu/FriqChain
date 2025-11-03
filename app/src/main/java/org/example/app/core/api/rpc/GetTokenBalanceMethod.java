package org.example.app.core.api.rpc;

import org.example.app.core.api.rpc.RPCError;
import org.example.app.core.api.rpc.RPCMethod;
import org.example.app.core.api.rpc.RPCRequest;
import org.example.app.core.api.rpc.RPCResponse;
import org.example.app.core.token.ERC20Standard;
import org.example.app.core.token.TokenRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * RPC method for retrieving token balances
 */
public class GetTokenBalanceMethod implements RPCMethod {
    private static final Logger LOGGER = Logger.getLogger(GetTokenBalanceMethod.class.getName());
    
    private final TokenRegistry tokenRegistry;
    
    public GetTokenBalanceMethod(TokenRegistry tokenRegistry) {
        this.tokenRegistry = tokenRegistry;
    }
    
    @Override
    public String getName() {
        return "getTokenBalance";
    }
    
    @Override
    public RPCResponse execute(RPCRequest request) {
        // Get parameters - convert Object[] to Map if needed
        Object[] paramsArray = request.getParams();
        if (paramsArray == null || paramsArray.length == 0) {
            return RPCResponse.error(RPCError.invalidParams("Missing parameters"), request.getId());
        }
        
        // Convert parameters to Map - assuming first parameter is a Map
        Map<String, Object> params;
        try {
            if (paramsArray[0] instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> paramMap = (Map<String, Object>) paramsArray[0];
                params = paramMap;
            } else {
                return RPCResponse.error(RPCError.invalidParams("Parameters must be an object"), request.getId());
            }
        } catch (Exception e) {
            return RPCResponse.error(RPCError.invalidParams("Invalid parameters format"), request.getId());
        }
        
        // Get token and account addresses
        String tokenAddress = (String) params.get("token");
        String accountAddress = (String) params.get("address");
        
        if (tokenAddress == null || tokenAddress.isEmpty()) {
            return RPCResponse.error(RPCError.invalidParams("Missing 'token' parameter"), request.getId());
        }
        
        if (accountAddress == null || accountAddress.isEmpty()) {
            return RPCResponse.error(RPCError.invalidParams("Missing 'address' parameter"), request.getId());
        }
        
        // Get token
        ERC20Standard token = tokenRegistry.getERC20Token(tokenAddress);
        if (token == null) {
            return RPCResponse.error(
                RPCError.resourceNotFound("Token not found: " + tokenAddress), 
                request.getId());
        }
        
        // Get balance
        try {
            long balance = token.balanceOf(accountAddress);
            
            // Prepare response
            Map<String, Object> result = new HashMap<>();
            result.put("token", tokenAddress);
            result.put("address", accountAddress);
            result.put("balance", String.valueOf(balance));
            result.put("symbol", token.getSymbol());
            result.put("decimals", token.getDecimals());
            
            return RPCResponse.success(result, request.getId());
        } catch (Exception e) {
            LOGGER.severe("Error getting token balance: " + e.getMessage());
            return RPCResponse.error(
                RPCError.executionError("Failed to get token balance: " + e.getMessage()), 
                request.getId());
        }
    }
}