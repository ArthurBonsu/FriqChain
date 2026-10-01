package org.example.app.core.token;

import java.util.logging.Logger;

import org.example.app.core.block.Transaction;
import org.example.app.core.eth.Blockchain;
import org.example.app.core.token.TokenRegistry;
import org.example.app.core.token.ERC20Standard;
import org.example.app.core.wallet.Wallet;

/**
 * Processes token transfer transactions
 */
public class TokenTransferProcessor {
    private static final Logger LOGGER = Logger.getLogger(TokenTransferProcessor.class.getName());
    
    private final TokenRegistry tokenRegistry;
    private final Blockchain blockchain;
    
    public TokenTransferProcessor(Blockchain blockchain) {
        this.tokenRegistry = TokenRegistry.getInstance();
        this.blockchain = blockchain;
    }
    
    /**
     * Processes a token transfer from a transaction
     * 
     * @param tx The transaction
     * @return True if the token transfer was processed successfully
     */
    public boolean processTokenTransfer(Transaction tx) {
        if (tx == null) {
            LOGGER.warning("Cannot process null transaction");
            return false;
        }
        
        String contractAddress = tx.getRecipient();
        if (contractAddress == null || contractAddress.isEmpty()) {
            LOGGER.warning("Transaction has no recipient");
            return false;
        }
        
        // Check if the recipient is a token contract
        if (!tokenRegistry.hasToken(contractAddress)) {
            LOGGER.warning("Recipient is not a registered token contract: " + contractAddress);
            return false;
        }
        
        // Get the token
        ERC20Standard token = tokenRegistry.getERC20Token(contractAddress);
        if (token == null) {
            LOGGER.warning("Token at " + contractAddress + " is not an ERC20 token");
            return false;
        }
        
        // Parse the transaction data to determine the operation
        byte[] data = tx.getData();
        if (data == null || data.length < 4) {
            LOGGER.warning("Transaction data is too short");
            return false;
        }
        
        // Extract function signature from first 4 bytes
        byte[] functionSignature = new byte[4];
        System.arraycopy(data, 0, functionSignature, 0, 4);
        
        // Determine the operation based on the function signature
        String operation = getFunctionName(functionSignature);
        
        // Process the operation
        switch (operation) {
            case "transfer":
                return processTransfer(token, tx);
            case "approve":
                return processApproval(token, tx);
            case "transferFrom":
                return processTransferFrom(token, tx);
            default:
                LOGGER.warning("Unknown token operation: " + operation);
                return false;
        }
    }
    
    /**
     * Processes a token transfer operation
     */
    private boolean processTransfer(ERC20Standard token, Transaction tx) {
        // Extract recipient and amount from transaction data
        String from = tx.getSender();
        String to = extractAddress(tx.getData(), 4); // Address starts at offset 4
        long amount = extractUint256(tx.getData(), 36); // uint256 starts at offset 4 + 32
        
        LOGGER.info(String.format(
            "Processing token transfer: from=%s, to=%s, amount=%d", 
            from, to, amount
        ));
        
        // Execute the transfer
        return token.transfer(from, to, amount);
    }
    
    /**
     * Processes a token approval operation
     */
    private boolean processApproval(ERC20Standard token, Transaction tx) {
        // Extract spender and amount from transaction data
        String owner = tx.getSender();
        String spender = extractAddress(tx.getData(), 4); // Address starts at offset 4
        long amount = extractUint256(tx.getData(), 36); // uint256 starts at offset 4 + 32
        
        LOGGER.info(String.format(
            "Processing token approval: owner=%s, spender=%s, amount=%d", 
            owner, spender, amount
        ));
        
        // Execute the approval
        return token.approve(owner, spender, amount);
    }
    
    /**
     * Processes a token transferFrom operation
     */
    private boolean processTransferFrom(ERC20Standard token, Transaction tx) {
        // Extract from, to, and amount from transaction data
        String spender = tx.getSender();
        String from = extractAddress(tx.getData(), 4); // From address starts at offset 4
        String to = extractAddress(tx.getData(), 36); // To address starts at offset 4 + 32
        long amount = extractUint256(tx.getData(), 68); // uint256 starts at offset 4 + 32 + 32
        
        LOGGER.info(String.format(
            "Processing token transferFrom: spender=%s, from=%s, to=%s, amount=%d", 
            spender, from, to, amount
        ));
        
        // Execute the transferFrom
        return token.transferFrom(spender, from, to, amount);
    }
    
    /**
     * Gets the function name from a function signature
     */
    private String getFunctionName(byte[] functionSignature) {
        // Simple mock implementation - in real code, this would use keccak256 hash
        if (functionSignature[0] == (byte) 0xa9 && functionSignature[1] == (byte) 0x05) {
            return "transfer";
        } else if (functionSignature[0] == (byte) 0x09 && functionSignature[1] == (byte) 0x5e) {
            return "approve";
        } else if (functionSignature[0] == (byte) 0x23 && functionSignature[1] == (byte) 0xb8) {
            return "transferFrom";
        }
        return "unknown";
    }
    
    /**
     * Extracts an address from transaction data
     */
    private String extractAddress(byte[] data, int offset) {
        if (data.length < offset + 32) {
            return "0x0000000000000000000000000000000000000000"; // Zero address
        }
        
        // In Ethereum, addresses are 20 bytes but are padded to 32 bytes in calldata
        byte[] addressBytes = new byte[20];
        System.arraycopy(data, offset + 12, addressBytes, 0, 20);
        
        return bytesToHexString(addressBytes);
    }
    
    /**
     * Extracts a uint256 from transaction data
     */
    private long extractUint256(byte[] data, int offset) {
        if (data.length < offset + 32) {
            return 0;
        }
        
        long result = 0;
        for (int i = 0; i < 8; i++) { // Only using 8 bytes (long)
            result = (result << 8) | (data[offset + 24 + i] & 0xFF);
        }
        
        return result;
    }
    
    /**
     * Converts byte array to hex string
     */
    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder("0x");
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}