package org.example.app.core.token;

import java.math.BigInteger;
import java.util.Arrays;
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
        
        // Authenticate first: the sender field is only trustworthy if the signature proves that the
        // holder of that address's private key authorised exactly this transaction.
        if (!tx.verifySignature()) {
            LOGGER.warning("Rejected token transaction with a missing or invalid signature");
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
    
    // ERC-20 function selectors (first 4 bytes of keccak256 of the canonical signature).
    private static final byte[] SELECTOR_TRANSFER = {(byte) 0xa9, 0x05, (byte) 0x9c, (byte) 0xbb};      // transfer(address,uint256)
    private static final byte[] SELECTOR_APPROVE = {0x09, 0x5e, (byte) 0xa7, (byte) 0xb3};              // approve(address,uint256)
    private static final byte[] SELECTOR_TRANSFER_FROM = {0x23, (byte) 0xb8, 0x72, (byte) 0xdd};        // transferFrom(address,address,uint256)

    private static final int WORD = 32;
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    /**
     * Processes a token transfer operation
     */
    private boolean processTransfer(ERC20Standard token, Transaction tx) {
        byte[] data = tx.getData();
        if (data.length != 4 + 2 * WORD) {
            LOGGER.warning("transfer call data has the wrong length: " + data.length);
            return false;
        }
        String from = tx.getSender();
        String to = extractAddress(data, 4);
        long amount = extractAmount(data, 4 + WORD);
        if (to == null || amount < 0) {
            LOGGER.warning("Rejected malformed transfer call data");
            return false;
        }

        LOGGER.info(String.format(
            "Processing token transfer: from=%s, to=%s, amount=%d",
            from, to, amount
        ));
        return token.transfer(from, to, amount);
    }

    /**
     * Processes a token approval operation
     */
    private boolean processApproval(ERC20Standard token, Transaction tx) {
        byte[] data = tx.getData();
        if (data.length != 4 + 2 * WORD) {
            LOGGER.warning("approve call data has the wrong length: " + data.length);
            return false;
        }
        String owner = tx.getSender();
        String spender = extractAddress(data, 4);
        long amount = extractAmount(data, 4 + WORD);
        if (spender == null || amount < 0) {
            LOGGER.warning("Rejected malformed approve call data");
            return false;
        }

        LOGGER.info(String.format(
            "Processing token approval: owner=%s, spender=%s, amount=%d",
            owner, spender, amount
        ));
        return token.approve(owner, spender, amount);
    }

    /**
     * Processes a token transferFrom operation
     */
    private boolean processTransferFrom(ERC20Standard token, Transaction tx) {
        byte[] data = tx.getData();
        if (data.length != 4 + 3 * WORD) {
            LOGGER.warning("transferFrom call data has the wrong length: " + data.length);
            return false;
        }
        String spender = tx.getSender();
        String from = extractAddress(data, 4);
        String to = extractAddress(data, 4 + WORD);
        long amount = extractAmount(data, 4 + 2 * WORD);
        if (from == null || to == null || amount < 0) {
            LOGGER.warning("Rejected malformed transferFrom call data");
            return false;
        }

        LOGGER.info(String.format(
            "Processing token transferFrom: spender=%s, from=%s, to=%s, amount=%d",
            spender, from, to, amount
        ));
        return token.transferFrom(spender, from, to, amount);
    }

    /**
     * Gets the function name from the 4-byte selector (all four bytes must match).
     */
    private String getFunctionName(byte[] functionSignature) {
        if (Arrays.equals(functionSignature, SELECTOR_TRANSFER)) {
            return "transfer";
        } else if (Arrays.equals(functionSignature, SELECTOR_APPROVE)) {
            return "approve";
        } else if (Arrays.equals(functionSignature, SELECTOR_TRANSFER_FROM)) {
            return "transferFrom";
        }
        return "unknown";
    }

    /**
     * Extracts an address from a 32-byte ABI word. Returns null when the word is not a valid
     * left-padded address (non-zero padding), so malformed calls are rejected instead of being
     * redirected to some other account.
     */
    private String extractAddress(byte[] data, int offset) {
        if (data.length < offset + WORD) {
            return null;
        }
        for (int i = 0; i < 12; i++) {
            if (data[offset + i] != 0) {
                return null;
            }
        }
        byte[] addressBytes = new byte[20];
        System.arraycopy(data, offset + 12, addressBytes, 0, 20);
        return bytesToHexString(addressBytes);
    }

    /**
     * Extracts a uint256 amount. Returns -1 when the value does not fit in a non-negative long,
     * because the token interface is long-based; silently truncating would move the wrong amount.
     */
    private long extractAmount(byte[] data, int offset) {
        if (data.length < offset + WORD) {
            return -1;
        }
        BigInteger amount = new BigInteger(1, Arrays.copyOfRange(data, offset, offset + WORD));
        return amount.compareTo(LONG_MAX) > 0 ? -1 : amount.longValue();
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
