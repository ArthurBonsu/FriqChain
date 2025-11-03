package org.example.app.core.wallet;

import java.security.*;
import java.util.HashMap;
import java.math.BigInteger;
import org.example.app.core.block.Transaction;

import java.security.spec.InvalidKeySpecException;

public class Wallet {
    private final KeyPair keyPair;
    private final HashMap<String, Transaction> transactions;
    private long nonce;
    private long balance;

    public Wallet() throws NoSuchAlgorithmException {
        // Generate key pair using standard Java cryptography
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC"); // Elliptic Curve
        keyGen.initialize(256); // 256-bit key
        this.keyPair = keyGen.generateKeyPair();

        this.transactions = new HashMap<>();
        this.nonce = 0;
        this.balance = 0;
    }

    public Transaction createTransaction(String to, long amount, long gasPrice) {
        if (amount + gasPrice > balance) {
            throw new IllegalStateException("Insufficient funds");
        }

        // Use TransactionBuilder for creating transactions
        TransactionBuilder builder = new TransactionBuilder();
        Transaction tx = builder
                .from(getAddress())
                .to(to)
                .value(BigInteger.valueOf(amount))
                .gasPrice(BigInteger.valueOf(gasPrice))
                .nonce(nonce++)
                .privateKey(keyPair.getPrivate())
                .build();

        // Generate a unique hash for the transaction
        String txHash = generateTransactionHash(tx);

        // Store transaction with generated hash
        transactions.put(txHash, tx);
        return tx;
    }

    // Method to generate a unique hash for the transaction
    private String generateTransactionHash(Transaction tx) {
        try {
            // Use transaction's byte representation to generate a hash
            byte[] txBytes = tx.toBytes();

            // Use SHA-256 for hash generation
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(txBytes);

            return bytesToHex(hashBytes);
        } catch (Exception e) {
            // Fallback to a unique identifier
            return String.valueOf(System.currentTimeMillis());
        }
    }

    // Helper method to convert byte array to hex string
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public String getAddress() {
        // Generate address from public key
        // This is a simplified address generation
        PublicKey publicKey = keyPair.getPublic();
        byte[] publicKeyBytes = publicKey.getEncoded();
        return bytesToHex(publicKeyBytes).substring(0, 40); // First 40 characters
    }

    // Additional wallet methods
    public void updateBalance(long newBalance) {
        this.balance = newBalance;
    }

    public long getBalance() {
        return balance;
    }

    public HashMap<String, Transaction> getTransactions() {
        return new HashMap<>(transactions);
    }

    public Transaction getTransaction(String hash) {
        return transactions.get(hash);
    }

    // Add this to your Wallet.java class
public Transaction createContractTransaction(String contractAddress, byte[] data, long gasPrice) {
    Transaction tx = createTransaction(contractAddress, 0, gasPrice);
    
    // Create a new transaction with the data payload
    Transaction dataTransaction = new Transaction(data);
    dataTransaction.setRecipient(contractAddress);
    dataTransaction.setSender(getAddress());
    dataTransaction.setGasPrice(BigInteger.valueOf(gasPrice));
    
    // Copy any other necessary properties from the original transaction
    
    return dataTransaction;
}

// Add this to your Wallet.java class
public Transaction createTokenTransferTransaction(String tokenAddress, String to, long amount, long gasPrice) {
    byte[] data = createTokenTransferData(to, amount);
    return createContractTransaction(tokenAddress, data, gasPrice);
}

private byte[] createTokenTransferData(String to, long amount) {
    // Create the token transfer function call data
    // Function signature: transfer(address,uint256)
    byte[] data = new byte[4 + 32 + 32]; // 4 bytes function selector + 32 bytes address + 32 bytes amount
    
    // Function selector for transfer(address,uint256): 0xa9059cbb
    data[0] = (byte) 0xa9;
    data[1] = (byte) 0x05;
    data[2] = (byte) 0x9c;
    data[3] = (byte) 0xbb;
    
    // Encode recipient address (padded to 32 bytes)
    byte[] addressBytes = hexStringToBytes(to);
    if (addressBytes.length == 20) {
        System.arraycopy(addressBytes, 0, data, 4 + 12, 20); // Pad left with zeros (12 bytes)
    }
    
    // Encode amount (padded to 32 bytes)
    for (int i = 0; i < 8; i++) { // Only using 8 bytes (long)
        data[4 + 32 + 24 + i] = (byte) ((amount >> ((7 - i) * 8)) & 0xFF);
    }
    
    return data;
}

private byte[] hexStringToBytes(String hexString) {
    if (hexString.startsWith("0x")) {
        hexString = hexString.substring(2);
    }
    
    int len = hexString.length();
    byte[] data = new byte[len / 2];
    
    for (int i = 0; i < len; i += 2) {
        data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                            + Character.digit(hexString.charAt(i + 1), 16));
    }
    
    return data;
}
}