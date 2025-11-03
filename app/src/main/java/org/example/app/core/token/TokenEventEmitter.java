package org.example.app.core.token;

import java.util.logging.Logger;
import org.example.app.core.smartcontract.EventLog;

/**
 * Emits token-related events
 */
public class TokenEventEmitter {
    private static final Logger LOGGER = Logger.getLogger(TokenEventEmitter.class.getName());
    private final EventLog eventLog;
    
    public TokenEventEmitter() {
        this.eventLog = new EventLog();
    }
    
    /**
     * Emits a Transfer event
     * 
     * @param from Sender address
     * @param to Recipient address
     * @param value Amount transferred
     * @param contractAddress Token contract address
     */
    public void emitTransfer(String from, String to, long value, String contractAddress) {
        String eventSignature = "Transfer(address,address,uint256)";
        String[] topics = new String[] { from, to };
        byte[] data = longToBytes(value);
        
        eventLog.logEvent(
            contractAddress,
            eventSignature,
            topics,
            data,
            System.currentTimeMillis(), // Using current time as block number
            "0x" + Long.toHexString(System.currentTimeMillis()), // Mock transaction hash
            0 // Mock transaction index
        );
        
        LOGGER.fine(String.format(
            "Emitted Transfer event: from=%s, to=%s, value=%d", 
            from, to, value
        ));
    }
    
    /**
     * Emits an Approval event
     * 
     * @param owner Token owner
     * @param spender Address approved to spend tokens
     * @param value Amount approved
     * @param contractAddress Token contract address
     */
    public void emitApproval(String owner, String spender, long value, String contractAddress) {
        String eventSignature = "Approval(address,address,uint256)";
        String[] topics = new String[] { owner, spender };
        byte[] data = longToBytes(value);
        
        eventLog.logEvent(
            contractAddress,
            eventSignature,
            topics,
            data,
            System.currentTimeMillis(), // Using current time as block number
            "0x" + Long.toHexString(System.currentTimeMillis()), // Mock transaction hash
            0 // Mock transaction index
        );
        
        LOGGER.fine(String.format(
            "Emitted Approval event: owner=%s, spender=%s, value=%d", 
            owner, spender, value
        ));
    }
    
    /**
     * Converts a long value to byte array
     */
    private byte[] longToBytes(long value) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return result;
    }
}