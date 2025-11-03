package org.example.app.core.api.rpc;

import org.example.app.core.api.rpc.RPCError;
import org.example.app.core.api.rpc.RPCMethod;
import org.example.app.core.api.rpc.RPCRequest;
import org.example.app.core.api.rpc.RPCResponse;
import org.example.app.core.block.Block;
import org.example.app.core.block.Transaction;
import org.example.app.core.consensus.Blockchain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * RPC method for retrieving block information
 */
public class GetBlockMethod implements RPCMethod {
    private static final Logger LOGGER = Logger.getLogger(GetBlockMethod.class.getName());
    
    private final Blockchain blockchain;
    
    public GetBlockMethod(Blockchain blockchain) {
        this.blockchain = blockchain;
    }
    
    @Override
    public String getName() {
        return "getBlock";
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
        
        // Check if querying by hash or number
        String hash = (String) params.get("hash");
        Object numberObj = params.get("number");
        boolean includeTransactions = Boolean.TRUE.equals(params.get("includeTransactions"));
        
        // Find the block
        Block block = null;
        
        if (hash != null) {
            // Query by hash
            block = blockchain.getBlockByHash(hash);
        } else if (numberObj != null) {
            // Query by number
            int number;
            try {
                if (numberObj instanceof Number) {
                    number = ((Number) numberObj).intValue();
                } else {
                    number = Integer.parseInt(numberObj.toString());
                }
                
                if (number < 0 || number >= blockchain.getChainLength()) {
                    return RPCResponse.error(
                        RPCError.invalidParams("Block number out of range: " + number), 
                        request.getId());
                }
                
                block = blockchain.getBlockByIndex(number);
            } catch (NumberFormatException e) {
                return RPCResponse.error(
                    RPCError.invalidParams("Invalid block number: " + numberObj), 
                    request.getId());
            }
        } else if (params.containsKey("latest") && Boolean.TRUE.equals(params.get("latest"))) {
            // Get latest block
            block = blockchain.getLatestBlock();
        } else {
            return RPCResponse.error(
                RPCError.invalidParams("Must specify 'hash', 'number', or 'latest'"), 
                request.getId());
        }
        
        // Check if block was found
        if (block == null) {
            return RPCResponse.error(
                RPCError.resourceNotFound("Block not found"), 
                request.getId());
        }
        
        // Convert block to JSON
        Map<String, Object> result = blockToJson(block, includeTransactions);
        
        return RPCResponse.success(result, request.getId());
    }
    
    /**
     * Convert a block to a JSON-serializable map
     */
    private Map<String, Object> blockToJson(Block block, boolean includeTransactions) {
        Map<String, Object> json = new HashMap<>();
        
        json.put("hash", block.getHash());
        json.put("parentHash", block.getParentHash());
        json.put("number", block.getNumber());
        json.put("timestamp", block.getTimestamp());
        json.put("miner", block.getMiner());
        json.put("difficulty", block.getDifficulty());
        json.put("stateRoot", block.getStateRoot() != null 
            ? java.util.Base64.getEncoder().encodeToString(block.getStateRoot()) 
            : null);
        
        // Add transaction data
        json.put("transactionCount", block.getTransactions().size());
        
        if (includeTransactions) {
            List<Map<String, Object>> txs = new ArrayList<>();
            for (Transaction tx : block.getTransactions()) {
                Map<String, Object> txData = new HashMap<>();
                txData.put("hash", tx.getSignature() != null ? tx.getSignature().toString() : null);
                txData.put("from", tx.getSender());
                txData.put("to", tx.getRecipient());
                txData.put("value", tx.getValue());
                
                txs.add(txData);
            }
            json.put("transactions", txs);
        } else {
            // Include only transaction hashes
            List<String> txHashes = new ArrayList<>();
            for (Transaction tx : block.getTransactions()) {
                txHashes.add(tx.getSignature() != null ? tx.getSignature().toString() : null);
            }
            json.put("transactionHashes", txHashes);
        }
        
        return json;
    }
}