package org.example.app.core.smartcontract;

import org.example.app.core.block.Transaction;
import java.util.Stack;
import java.util.logging.Logger;

/**
 * Handles contract-to-contract calls in the EVM
 */
public class ContractCallManager {
    private static final Logger LOGGER = Logger.getLogger(ContractCallManager.class.getName());
    
    // Maximum allowed call depth to prevent stack overflows
    private static final int MAX_CALL_DEPTH = 1024;
    
    // Current call stack
    private final Stack<CallFrame> callStack = new Stack<>();
    
    // EVM state
    private final EvmState evmState;
    
    // Gas manager
    private final GasManager gasManager;
    
    public ContractCallManager(EvmState evmState, GasManager gasManager) {
        this.evmState = evmState;
        this.gasManager = gasManager;
    }
    
    /**
     * Execute a call from one contract to another
     * 
     * @param caller The address of the calling contract
     * @param target The address of the target contract
     * @param calldata The calldata for the target contract
     * @param value The value (in wei) to send with the call
     * @param gasLimit The gas limit for the call
     * @return The return data from the call
     */
    public byte[] executeCall(
            String caller,
            String target,
            byte[] calldata,
            long value,
            int gasLimit) {
        
        LOGGER.info("Executing call from " + caller + " to " + target);
        
        // Check call depth
        if (callStack.size() >= MAX_CALL_DEPTH) {
            LOGGER.warning("Call depth exceeded");
            return new byte[0]; // Return empty data on call depth exceeded
        }
        
        // Check gas limit
        if (gasManager.getGasRemaining() < gasLimit) {
            LOGGER.warning("Insufficient gas for call");
            throw new RuntimeException("Out of gas");
        }
        
        // Check if the target contract exists
        if (!evmState.hasAccount(target)) {
            LOGGER.warning("Target contract does not exist: " + target);
            return new byte[0]; // Return empty data if contract doesn't exist
        }
        
        // Save current context
        CallFrame currentFrame = callStack.isEmpty() ? null : callStack.peek();
        
        // Create a new call frame
        CallFrame newFrame = new CallFrame(
                caller,
                target,
                calldata,
                value,
                gasLimit
        );
        
        // Push the new frame to the stack
        callStack.push(newFrame);
        
        try {
            // Transfer value from caller to target
            if (value > 0) {
                transferValue(caller, target, value);
            }
            
            // Set up a new transaction for the call
            Transaction tx = new Transaction(calldata);
            tx.setRecipient(target);
            tx.setGasLimit(gasLimit);
            tx.setValue(value);
            
            // Create a new EVM instance for the call
            Evm evm = new Evm(evmState);
            
            // Execute the call
            evm.execute(calldata, tx);
            
            // Return the result
            // In a real implementation, we would get the return data from the EVM
            // For now, we'll just return an empty byte array
            return new byte[0];
        } finally {
            // Pop the call frame
            callStack.pop();
        }
    }
    
    /**
     * Transfer value from one account to another
     */
    private void transferValue(String from, String target, long value) {
        // In a real implementation, this would update account balances
        LOGGER.info("Transferring " + value + " wei from " + from + " to " + target);
    }
    
    /**
     * Get the current call depth
     */
    public int getCallDepth() {
        return callStack.size();
    }
    
    /**
     * Get the current call frame
     */
    public CallFrame getCurrentFrame() {
        return callStack.isEmpty() ? null : callStack.peek();
    }
    
    /**
     * Class to represent a call frame in the call stack
     */
    public static class CallFrame {
        private final String caller;
        private final String target;
        private final byte[] calldata;
        private final long value;
        private final int gasLimit;
        private byte[] returnData;
        
        public CallFrame(
                String caller,
                String target,
                byte[] calldata,
                long value,
                int gasLimit) {
            
            this.caller = caller;
            this.target = target;
            this.calldata = calldata;
            this.value = value;
            this.gasLimit = gasLimit;
        }
        
        public String getCaller() {
            return caller;
        }
        
        public String getTarget() {
            return target;
        }
        
        public byte[] getCalldata() {
            return calldata;
        }
        
        public long getValue() {
            return value;
        }
        
        public int getGasLimit() {
            return gasLimit;
        }
        
        public byte[] getReturnData() {
            return returnData;
        }
        
        public void setReturnData(byte[] returnData) {
            this.returnData = returnData;
        }
    }
}