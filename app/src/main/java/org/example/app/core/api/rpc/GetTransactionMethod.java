package org.example.app.core.api.rpc;

import org.example.app.core.api.rpc.RPCError;
import org.example.app.core.api.rpc.RPCMethod;
import org.example.app.core.api.rpc.RPCRequest;
import org.example.app.core.api.rpc.RPCResponse;
import org.example.app.core.block.Block;
import org.example.app.core.block.Transaction;
import org.example.app.core.consensus.Blockchain;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * RPC method for retrieving transaction information
 */
public class GetTransactionMethod implements RPCMethod {
    private static final Logger LOGGER = Logger.getLogger(GetTransactionMethod.class.getName());
    
    private final Blockchain blockchain;
    
    public GetTransactionMethod(Blockchain blockchain) {
        this.blockchain = blockchain;
    }
    
    @Override
    public String getName() {
        return "getTransaction";
    }
    
    @Override
    public RPCResponse execute(RPCRequest request) {
        // Get parameters - assuming getParams() returns Object[] based on error
        Object[] paramsArray = request.getParams();
        if (paramsArray == null || paramsArray.length == 0) {
            return RPCResponse.error(RPCError.invalidParams("Missing parameters"), request.getId());
        }
        
        // Convert first parameter to Map if it's a Map, or create Map from parameters
        Map<String, Object> params = new HashMap<>();
        if (paramsArray[0] instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> paramMap = (Map<String, Object>) paramsArray[0];
            params = paramMap;
        } else {
            // If parameters are passed as individual values, handle accordingly
            // This assumes the first parameter is the hash
            if (paramsArray[0] instanceof String) {
                params.put("hash", paramsArray[0]);
            }
        }
        
        // Get hash
        String hash = (String) params.get("hash");
        if (hash == null) {
            return RPCResponse.error(RPCError.invalidParams("Missing 'hash' parameter"), request.getId());
        }
        
        // Find the transaction
        Transaction transaction = findTransactionByHash(hash);
        
        // Check if transaction was found
        if (transaction == null) {
            return RPCResponse.error(RPCError.resourceNotFound("Transaction not found: " + hash), request.getId());
        }
        
        // Get block info
        String blockHash = null;
        long blockNumber = -1;
        int txIndex = -1;
        
        for (int i = 0; i < blockchain.getChainLength(); i++) {
            Block block = blockchain.getBlockByIndex(i);
            
            for (int j = 0; j < block.getTransactions().size(); j++) {
                Transaction tx = block.getTransactions().get(j);
                if (tx.getSignature() != null && hash.equals(tx.getSignature().toString())) {
                    blockHash = block.getHash();
                    blockNumber = block.getNumber();
                    txIndex = j;
                    break;
                }
            }
            
            if (blockHash != null) {
                break;
            }
        }
        
        // Convert transaction to JSON
        Map<String, Object> result = transactionToJson(transaction, blockHash, blockNumber, txIndex);
        
        // Convert result to JSON string for RPC response
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            if (!first) jsonBuilder.append(",");
            jsonBuilder.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                jsonBuilder.append("\"").append(value).append("\"");
            } else if (value instanceof Number) {
                jsonBuilder.append(value);
            } else if (value == null) {
                jsonBuilder.append("null");
            } else {
                jsonBuilder.append("\"").append(value.toString()).append("\"");
            }
            first = false;
        }
        jsonBuilder.append("}");
        
        return RPCResponse.success(jsonBuilder.toString(), request.getId());
    }
    
    /**
     * Find a transaction by its hash
     */
    private Transaction findTransactionByHash(String txHash) {
        // In a real implementation, this would search the blockchain and mempool
        // For now, we'll search the blockchain blocks
        
        for (int i = 0; i < blockchain.getChainLength(); i++) {
            Block block = blockchain.getBlockByIndex(i);
            for (Transaction tx : block.getTransactions()) {
                // Using signature as a mock hash for comparison
                if (tx.getSignature() != null && txHash.equals(tx.getSignature().toString())) {
                    return tx;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Convert a transaction to a JSON-serializable map
     */
    private Map<String, Object> transactionToJson(Transaction transaction, 
                                                 String blockHash, 
                                                 long blockNumber, 
                                                 int transactionIndex) {
        Map<String, Object> json = new HashMap<>();
        
        json.put("hash", transaction.getSignature() != null ? transaction.getSignature().toString() : null);
        json.put("from", transaction.getSender());
        json.put("to", transaction.getRecipient());
        json.put("value", transaction.getValue());
        json.put("gasPrice", transaction.getGasPrice());
        json.put("gasLimit", transaction.getGasLimit());
        json.put("nonce", transaction.getNonce());
        
        // Add block info
        if (blockHash != null) {
            json.put("blockHash", blockHash);
            json.put("blockNumber", blockNumber);
            json.put("transactionIndex", transactionIndex);
        } else {
            json.put("status", "pending");
        }
        
        // Add data if present
        if (transaction.getData() != null) {
            json.put("data", java.util.Base64.getEncoder().encodeToString(transaction.getData()));
        }
        
        return json;
    }
}