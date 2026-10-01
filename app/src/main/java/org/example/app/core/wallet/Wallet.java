package org.example.app.core.wallet;

import org.example.app.core.block.Transaction;
import org.example.app.core.crypto.Secp256k1;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.HashMap;

/**
 * A single-key account wallet. The address is derived from the secp256k1 public key
 * ({@code 0x} + last 20 bytes of {@code keccak256(publicKey)}), so every wallet has a unique
 * address, and every transaction the wallet creates carries a real ECDSA signature. The private key
 * never leaves this object except into an encrypted {@link WalletStorage} file.
 */
public class Wallet {
    private static final byte[] TRANSFER_SELECTOR = {(byte) 0xa9, 0x05, (byte) 0x9c, (byte) 0xbb}; // transfer(address,uint256)

    private final KeyPair keyPair;
    private final String address;
    private final HashMap<String, Transaction> transactions;
    private long nonce;
    private long balance;

    /** Creates a wallet with a freshly generated key. */
    public Wallet() throws NoSuchAlgorithmException {
        this(KeyUtils.generateKeyPair());
    }

    /** Wraps an existing secp256k1 key pair. */
    public Wallet(KeyPair keyPair) {
        if (keyPair == null) {
            throw new IllegalArgumentException("Key pair must not be null");
        }
        this.keyPair = keyPair;
        this.address = KeyUtils.getAddress(keyPair.getPublic());
        this.transactions = new HashMap<>();
        this.nonce = 0;
        this.balance = 0;
    }

    /** Restores a wallet from a 32-byte private key (for example one decrypted from a keystore). */
    public static Wallet fromPrivateKey(byte[] privateKey32) {
        return new Wallet(Secp256k1.keyPairFromPrivate(privateKey32));
    }

    // ------------------------------------------------------------------ persistence

    /** Encrypts this wallet's private key under {@code password} and stores it. */
    public void save(WalletStorage storage, String walletId, String password) throws Exception {
        WalletStorage.WalletData data = new WalletStorage.WalletData();
        data.address = address;
        data.lastNonce = nonce;
        data.privateKey = Secp256k1.toUnsigned32(Secp256k1.privateScalar(keyPair.getPrivate()));
        try {
            storage.saveWallet(walletId, data, password);
        } finally {
            Arrays.fill(data.privateKey, (byte) 0);
        }
    }

    /** Loads a wallet previously written with {@link #save}. */
    public static Wallet load(WalletStorage storage, String walletId, String password) throws Exception {
        WalletStorage.WalletData data = storage.loadWallet(walletId, password);
        if (data.privateKey == null) {
            throw new IllegalStateException("Wallet '" + walletId + "' does not contain a private key");
        }
        try {
            Wallet wallet = fromPrivateKey(data.privateKey);
            wallet.nonce = data.lastNonce;
            return wallet;
        } finally {
            Arrays.fill(data.privateKey, (byte) 0);
        }
    }

    // ------------------------------------------------------------------ transactions

    /**
     * Creates and signs a value transfer. The nonce is consumed only when the transaction was
     * created successfully.
     *
     * @throws IllegalStateException if the wallet's known balance cannot cover {@code amount + gasPrice}
     */
    public synchronized Transaction createTransaction(String to, long amount, long gasPrice) {
        requireFunds(amount, gasPrice);
        return buildAndRecord(to, BigInteger.valueOf(amount), new byte[0], gasPrice);
    }

    /** Creates and signs a contract call carrying {@code data}. */
    public synchronized Transaction createContractTransaction(String contractAddress, byte[] data, long gasPrice) {
        requireFunds(0, gasPrice);
        return buildAndRecord(contractAddress, BigInteger.ZERO, data == null ? new byte[0] : data, gasPrice);
    }

    /** Creates a signed ERC-20 {@code transfer(to, amount)} call against {@code tokenAddress}. */
    public Transaction createTokenTransferTransaction(String tokenAddress, String to, long amount, long gasPrice) {
        return createTokenTransferTransaction(tokenAddress, to, BigInteger.valueOf(amount), gasPrice);
    }

    /** As above with a full 256-bit amount. */
    public Transaction createTokenTransferTransaction(String tokenAddress, String to, BigInteger amount, long gasPrice) {
        return createContractTransaction(tokenAddress, encodeTokenTransfer(to, amount), gasPrice);
    }

    private void requireFunds(long amount, long gasPrice) {
        if (amount < 0 || gasPrice < 0) {
            throw new IllegalArgumentException("Amount and gas price must not be negative");
        }
        long required;
        try {
            required = Math.addExact(amount, gasPrice);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Amount plus gas price overflows", e);
        }
        if (required > balance) {
            throw new IllegalStateException("Insufficient funds");
        }
    }

    private Transaction buildAndRecord(String to, BigInteger value, byte[] data, long gasPrice) {
        Transaction tx = new TransactionBuilder()
                .from(address)
                .to(to)
                .value(value)
                .data(data)
                .gasPrice(BigInteger.valueOf(gasPrice))
                .nonce(nonce)
                .privateKey(keyPair.getPrivate())
                .build();
        nonce++; // only after the transaction exists
        transactions.put(tx.getHash(), tx);
        return tx;
    }

    /**
     * ABI-encodes {@code transfer(address,uint256)}. The recipient must be a valid 20-byte hex
     * address and the amount must fit in 256 bits; anything else is rejected rather than silently
     * encoded as the zero address or truncated.
     */
    static byte[] encodeTokenTransfer(String to, BigInteger amount) {
        if (to == null) {
            throw new IllegalArgumentException("Recipient address is required");
        }
        byte[] recipient = Secp256k1.fromHex(to);
        if (recipient.length != 20) {
            throw new IllegalArgumentException("Recipient must be a 20-byte address: " + to);
        }
        if (amount == null || amount.signum() < 0 || amount.bitLength() > 256) {
            throw new IllegalArgumentException("Token amount must be between 0 and 2^256-1");
        }
        byte[] data = new byte[4 + 32 + 32];
        System.arraycopy(TRANSFER_SELECTOR, 0, data, 0, 4);
        System.arraycopy(recipient, 0, data, 4 + 12, 20);
        byte[] amountBytes = amount.toByteArray();
        int offset = amountBytes[0] == 0 && amountBytes.length > 1 ? 1 : 0; // drop the sign byte
        int length = amountBytes.length - offset;
        System.arraycopy(amountBytes, offset, data, 4 + 32 + (32 - length), length);
        return data;
    }

    // ------------------------------------------------------------------ accessors

    /** Canonical address: {@code 0x} + 40 lowercase hex characters, unique per key. */
    public String getAddress() {
        return address;
    }

    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }

    /** The 64-byte secp256k1 public key ({@code X || Y}). */
    public byte[] getPublicKeyBytes() {
        return Secp256k1.rawPublicKey(keyPair.getPublic());
    }

    /** The nonce the next transaction created by this wallet will use. */
    public synchronized long getNextNonce() {
        return nonce;
    }

    /**
     * Signs a transaction created elsewhere (for example by a contract helper). The sender is set
     * to this wallet's address if empty, the next wallet nonce is assigned, and a real ECDSA
     * signature is attached.
     */
    public synchronized Transaction sign(Transaction tx) {
        if (tx == null) {
            throw new IllegalArgumentException("Transaction must not be null");
        }
        if (tx.getSender() == null || tx.getSender().isEmpty()) {
            tx.setSender(address);
        }
        tx.setNonce(nonce);
        tx.sign(keyPair.getPrivate());
        nonce++;
        transactions.put(tx.getHash(), tx);
        return tx;
    }

    public void updateBalance(long newBalance) {
        this.balance = newBalance;
    }

    public long getBalance() {
        return balance;
    }

    public synchronized HashMap<String, Transaction> getTransactions() {
        return new HashMap<>(transactions);
    }

    public synchronized Transaction getTransaction(String hash) {
        return transactions.get(hash);
    }
}
