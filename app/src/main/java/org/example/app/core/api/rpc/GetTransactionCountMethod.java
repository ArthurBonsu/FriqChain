// GetTransactionCountMethod.java
package org.example.app.core.api.rpc;

import org.example.app.core.consensus.Blockchain;
import org.example.app.core.block.Block;
import org.example.app.core.block.Transaction;
import java.util.HashMap;
import java.util.Map;

public class GetTransactionCountMethod implements RPCMethod {
    private final Blockchain blockchain;
    
    public GetTransactionCountMethod(Blockchain blockchain) {
        this.blockchain = blockchain;
    }
    
    @Override
    public String getName() {
        return "getTransactionCount";
    }
    
    @Override
    public RPCResponse execute(RPCRequest request) {
        // Get parameters
        Object[] paramsArray = request.getParams();
        if (paramsArray == null || paramsArray.length == 0) {
            return RPCResponse.error(RPCError.invalidParams("Missing parameters"), request.getId());
        }
        
        // Convert parameters
        Map<String, Object> params = new HashMap<>();
        if (paramsArray[0] instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> paramMap = (Map<String, Object>) paramsArray[0];
            params = paramMap;
        } else if (paramsArray[0] instanceof String) {
            params.put("address", paramsArray[0]);
        }
        
        // Get address
        String address = (String) params.get("address");
        if (address == null) {
            return RPCResponse.error(RPCError.invalidParams("Missing 'address' parameter"), request.getId());
        }
        
        // Count transactions for the address
        long count = countTransactionsForAddress(address);
        
        // Convert to hex string format (like Ethereum)
        String hexCount = "0x" + Long.toHexString(count);
        
        return RPCResponse.success(hexCount, request.getId());
    }
    
    /**
     * Count transactions for a specific address
     */
    private long countTransactionsForAddress(String address) {
        long count = 0;
        
        for (int i = 0; i < blockchain.getChainLength(); i++) {
            Block block = blockchain.getBlockByIndex(i);
            for (Transaction tx : block.getTransactions()) {
                if (address.equals(tx.getSender())) {
                    count++;
                }
            }
        }
        
        return count;
    }
}