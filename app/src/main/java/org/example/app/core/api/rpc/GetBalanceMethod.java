// GetBalanceMethod.java
package org.example.app.core.api.rpc;

import org.example.app.core.consensus.Blockchain;

public class GetBalanceMethod implements RPCMethod {
    
    private Blockchain blockchain;
    
    public GetBalanceMethod(Blockchain blockchain) {
        this.blockchain = blockchain;
    }
    
    @Override
    public String getName() {
        return "getBalance";
    }

    @Override
    public RPCResponse execute(RPCRequest request) {
        try {
            // Extract parameters from the request
            Object[] params = request.getParams();
            
            if (params == null || params.length == 0) {
                return RPCResponse.error(-32602, "Missing address parameter", request.getId());
            }
            
            String address = params[0].toString();
            
            // Validate address format (basic validation)
            if (address == null || address.trim().isEmpty()) {
                return RPCResponse.error(-32602, "Invalid address parameter", request.getId());
            }
            
            // Get balance from blockchain
            String balance = getBalance(address);
            
            // Return success response with the balance
            return RPCResponse.success(balance, request.getId());
            
        } catch (Exception e) {
            return RPCResponse.error(-32603, "Error getting balance: " + e.getMessage(), request.getId());
        }
    }
    
    /**
     * Helper method to get balance for a specific address
     * @param address The address to get balance for
     * @return The balance as a hexadecimal string (e.g., "0x1bc16d674ec80000" for 2 ETH)
     */
    private String getBalance(String address) {
        try {
            // TODO: Implement actual balance lookup logic
            // This would typically involve:
            // 1. Validating the address format (e.g., 0x followed by 40 hex characters)
            // 2. Looking up the address in the blockchain state/UTXO set
            // 3. Calculating the total balance from all UTXOs or account state
            // 4. Converting to appropriate format (wei/satoshi to hex string)
            
            // For now, return a placeholder balance
            // In a real implementation, you would call something like:
            // return blockchain.getAccountBalance(address);
            
            // Simulating different balances for testing
            if (address.equals("0x0000000000000000000000000000000000000000")) {
                return "0x0"; // Genesis/null address has 0 balance
            } else {
                return "0x1bc16d674ec80000"; // 2 ETH in wei (hexadecimal)
            }
            
        } catch (Exception e) {
            // Log error and return 0 balance as fallback
            System.err.println("Error retrieving balance for address " + address + ": " + e.getMessage());
            return "0x0";
        }
    }
}