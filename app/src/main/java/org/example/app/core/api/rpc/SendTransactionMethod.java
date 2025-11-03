package org.example.app.core.api.rpc;

import org.example.app.core.api.rpc.RPCError;
import org.example.app.core.api.rpc.RPCMethod;
import org.example.app.core.api.rpc.RPCRequest;
import org.example.app.core.api.rpc.RPCResponse;
import org.example.app.core.block.Transaction;
import org.example.app.core.consensus.Blockchain;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * RPC method for submitting a transaction to the blockchain
 */
public class SendTransactionMethod implements RPCMethod {
    private static final Logger LOGGER = Logger.getLogger(SendTransactionMethod.class.getName());

    private final Blockchain blockchain;

    public SendTransactionMethod(Blockchain blockchain) {
        this.blockchain = blockchain;
    }

    @Override
    public RPCResponse execute(RPCRequest request) {
        try {
            // Get parameters - RPCRequest.getParams() returns Object[]
            Object[] paramsArray = request.getParams();
            Map<String, Object> params = null;

            if (paramsArray != null && paramsArray.length > 0) {
                // First parameter should be a Map containing transaction parameters
                // Add proper type checking to eliminate unchecked cast warning
                if (paramsArray[0] instanceof Map<?, ?>) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> checkedMap = (Map<String, Object>) paramsArray[0];
                    params = checkedMap;
                } else {
                    return RPCResponse.error(
                            RPCError.invalidParams("First parameter must be a transaction object"),
                            request.getId()
                    );
                }
            }

            if (params == null) {
                return RPCResponse.error(
                        RPCError.invalidParams("Missing transaction parameters"),
                        request.getId()
                );
            }

            // Extract transaction parameters
            String from = (String) params.get("from");
            String to = (String) params.get("to");
            String dataStr = (String) params.get("data");
            Object valueObj = params.get("value");
            Object gasPriceObj = params.get("gasPrice");
            Object gasLimitObj = params.get("gasLimit");

            // Validate required parameters
            if (from == null || from.isEmpty()) {
                return RPCResponse.error(
                        RPCError.invalidParams("Missing 'from' parameter"),
                        request.getId()
                );
            }

            if (to == null || to.isEmpty()) {
                return RPCResponse.error(
                        RPCError.invalidParams("Missing 'to' parameter"),
                        request.getId()
                );
            }

            // Parse value
            long value = 0;
            if (valueObj != null) {
                try {
                    if (valueObj instanceof Number) {
                        value = ((Number) valueObj).longValue();
                    } else {
                        value = Long.parseLong(valueObj.toString());
                    }
                } catch (NumberFormatException e) {
                    return RPCResponse.error(
                            RPCError.invalidParams("Invalid 'value' parameter: " + valueObj),
                            request.getId()
                    );
                }
            }

            // Parse gas price
            BigInteger gasPrice = BigInteger.valueOf(1_000_000_000); // Default: 1 Gwei
            if (gasPriceObj != null) {
                try {
                    if (gasPriceObj instanceof Number) {
                        gasPrice = BigInteger.valueOf(((Number) gasPriceObj).longValue());
                    } else {
                        gasPrice = new BigInteger(gasPriceObj.toString());
                    }
                } catch (NumberFormatException e) {
                    return RPCResponse.error(
                            RPCError.invalidParams("Invalid 'gasPrice' parameter: " + gasPriceObj),
                            request.getId()
                    );
                }
            }

            // Parse gas limit
            int gasLimit = 21000; // Default gas limit
            if (gasLimitObj != null) {
                try {
                    if (gasLimitObj instanceof Number) {
                        gasLimit = ((Number) gasLimitObj).intValue();
                    } else {
                        gasLimit = Integer.parseInt(gasLimitObj.toString());
                    }
                } catch (NumberFormatException e) {
                    return RPCResponse.error(
                            RPCError.invalidParams("Invalid 'gasLimit' parameter: " + gasLimitObj),
                            request.getId()
                    );
                }
            }

            // Parse data
            byte[] data = new byte[0];
            if (dataStr != null && !dataStr.isEmpty()) {
                try {
                    data = java.util.Base64.getDecoder().decode(dataStr);
                } catch (IllegalArgumentException e) {
                    return RPCResponse.error(
                            RPCError.invalidParams("Invalid 'data' parameter: " + dataStr),
                            request.getId()
                    );
                }
            }

            // Create and submit the transaction
            Transaction tx = new Transaction(data);
            tx.setSender(from);
            tx.setRecipient(to);
            tx.setValue(value);
            tx.setGasPrice(gasPrice);
            tx.setGasLimit(gasLimit);

            // Add the transaction to the blockchain
            blockchain.addTransaction(tx);

            // Prepare response
            Map<String, Object> result = new HashMap<>();
            result.put("hash", tx.getSignature() != null ? tx.getSignature().toString() : "tx_" + System.currentTimeMillis());
            result.put("status", "pending");

            return RPCResponse.success(result, request.getId());

        } catch (Exception e) {
            LOGGER.severe("Unexpected error in SendTransactionMethod: " + e.getMessage());
            return RPCResponse.error(
                    RPCError.internalError("Internal server error: " + e.getMessage()),
                    request.getId()
            );
        }
    }

    @Override
    public String getName() {
        return "eth_sendTransaction";
    }
}