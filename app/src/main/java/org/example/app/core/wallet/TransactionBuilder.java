package org.example.app.core.wallet;

import java.math.BigInteger;
import java.security.PrivateKey;
import org.example.app.core.block.Transaction;

/**
 * Fluent builder for {@link Transaction}. When a private key is supplied the transaction is signed
 * with a real secp256k1 ECDSA signature; the key must belong to the {@code from} address.
 */
public class TransactionBuilder {
    private String from;
    private String to;
    private BigInteger value;
    private BigInteger gasPrice;
    private BigInteger gasLimit;
    private byte[] data;
    private long nonce;
    private long chainId = Transaction.DEFAULT_CHAIN_ID;
    private PrivateKey privateKey;

    public TransactionBuilder() {
        this.value = BigInteger.ZERO;
        this.gasPrice = BigInteger.valueOf(1_000_000_000); // 1 Gwei
        this.gasLimit = BigInteger.valueOf(21000); // Standard transfer
        this.data = new byte[0];
    }

    public TransactionBuilder from(String from) {
        this.from = from;
        return this;
    }

    public TransactionBuilder to(String to) {
        this.to = to;
        return this;
    }

    public TransactionBuilder value(BigInteger value) {
        this.value = value;
        return this;
    }

    public TransactionBuilder gasPrice(BigInteger gasPrice) {
        this.gasPrice = gasPrice;
        return this;
    }

    public TransactionBuilder gasLimit(BigInteger gasLimit) {
        this.gasLimit = gasLimit;
        return this;
    }

    public TransactionBuilder data(byte[] data) {
        this.data = data;
        return this;
    }

    public TransactionBuilder nonce(long nonce) {
        this.nonce = nonce;
        return this;
    }

    public TransactionBuilder chainId(long chainId) {
        this.chainId = chainId;
        return this;
    }

    public TransactionBuilder privateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
        return this;
    }

    public Transaction build() {
        validate();

        Transaction tx = new Transaction(data);
        tx.setSender(from);
        tx.setRecipient(to);
        try {
            tx.setValue(value.longValueExact());
            tx.setGasLimit(gasLimit.intValueExact());
        } catch (ArithmeticException e) {
            throw new IllegalStateException("Value or gas limit exceeds the supported range", e);
        }
        tx.setGasPrice(gasPrice);
        tx.setNonce(nonce);
        tx.setChainId(chainId);

        if (privateKey != null) {
            tx.sign(privateKey);
        }
        return tx;
    }

    private void validate() {
        if (from == null || from.isEmpty()) {
            throw new IllegalStateException("From address is required");
        }
        if (to == null || to.isEmpty()) {
            throw new IllegalStateException("To address is required");
        }
        if (value == null) {
            throw new IllegalStateException("Value cannot be null");
        }
        if (value.signum() < 0) {
            throw new IllegalStateException("Value cannot be negative");
        }
        if (gasPrice == null || gasPrice.compareTo(BigInteger.ZERO) <= 0) {
            throw new IllegalStateException("Invalid gas price");
        }
        if (gasLimit == null || gasLimit.compareTo(BigInteger.ZERO) <= 0) {
            throw new IllegalStateException("Invalid gas limit");
        }
        if (nonce < 0) {
            throw new IllegalStateException("Nonce cannot be negative");
        }
    }

    /** Resets the builder for reuse. */
    public TransactionBuilder reset() {
        this.from = null;
        this.to = null;
        this.value = BigInteger.ZERO;
        this.gasPrice = BigInteger.valueOf(1_000_000_000);
        this.gasLimit = BigInteger.valueOf(21000);
        this.data = new byte[0];
        this.nonce = 0;
        this.chainId = Transaction.DEFAULT_CHAIN_ID;
        this.privateKey = null;
        return this;
    }
}
