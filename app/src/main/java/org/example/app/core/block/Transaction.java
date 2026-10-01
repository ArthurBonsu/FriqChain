package org.example.app.core.block;

import org.example.app.core.crypto.Keypair.SignatureResult;
import org.example.app.core.crypto.Secp256k1;
import org.example.app.core.types.ByteSerializable;
import org.example.app.core.types.Hash;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Locale;

/**
 * A value transfer or contract call on the TINC chain.
 *
 * <h2>Integrity and authentication</h2>
 * <ul>
 *   <li>{@link #signingBytes()} is a canonical, length-prefixed encoding of every field that the
 *       sender authorises (chain id, nonce, gas, sender, recipient, value, data).</li>
 *   <li>{@link #getHash()} is the Keccak-256 of {@link #toBytes()} (signing bytes plus signature and
 *       public key). It is computed from the current contents on every call, so it can never be
 *       stale, and two transactions that differ in any authorised field never share a hash.</li>
 *   <li>{@link #sign(PrivateKey)} produces a real secp256k1 ECDSA signature and records the signer's
 *       public key; {@link #verifySignature()} checks that the public key hashes to the sender
 *       address and that the signature is valid over the signing digest.</li>
 *   <li>The chain id is part of the signed data, so a transaction signed for one country shard
 *       cannot be replayed on another.</li>
 * </ul>
 */
public class Transaction implements java.io.Serializable, ByteSerializable {
    private static final long serialVersionUID = 2L;

    /** Wire-format version tag and domain separator that is signed together with the fields. */
    private static final byte[] DOMAIN_TAG = "TINC-TX-V1".getBytes(StandardCharsets.US_ASCII);

    /** Chain id used when none is set explicitly. Shards/networks should set their own. */
    public static final long DEFAULT_CHAIN_ID = 1L;

    private static final int DEFAULT_GAS_LIMIT = 21000;

    private byte[] data;
    private long value;
    private SignatureResult signature;
    private byte[] publicKey; // 64-byte secp256k1 public key of the signer (X || Y)
    private long nonce;
    private long chainId = DEFAULT_CHAIN_ID;
    private Hash hash; // explicit override installed through setHash(); normally null
    private int gasLimit;
    private BigInteger gasPrice;
    private TransactionStatus status;

    private String sender;
    private String recipient;
    private BigInteger fee;
    private boolean contractCreation;
    private boolean contractCall;

    public enum TransactionStatus {
        PENDING,
        CONFIRMED,
        FAILED,
        INVALID
    }

    public Transaction(byte[] data) {
        this.data = data != null ? data.clone() : new byte[0];
        this.nonce = 0;
        this.status = TransactionStatus.PENDING;
        this.gasPrice = BigInteger.ZERO;
        this.gasLimit = DEFAULT_GAS_LIMIT;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                (data != null ? Arrays.toString(data) : "null") +
                "}";
    }

    /** Two transactions are equal when their full contents (including signature) are identical. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return Arrays.equals(toBytes(), that.toBytes());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(toBytes());
    }

    // ------------------------------------------------------------------ accessors

    public String getSender() { return sender; }
    public String getRecipient() { return recipient; }
    public Long getValue() { return value; }
    public BigInteger getFee() { return fee; }
    public long getNonce() { return nonce; }
    public byte[] getData() { return data; }
    public boolean isContractCreation() { return contractCreation; }
    public boolean isContractCall() { return contractCall; }
    public long getChainId() { return chainId; }
    public int getGasLimit() { return this.gasLimit; }
    public BigInteger getGasPrice() { return gasPrice; }
    public TransactionStatus getStatus() { return status; }
    public SignatureResult getSignature() { return this.signature; }

    /** The signer's 64-byte public key, or null when the transaction is unsigned. */
    public byte[] getPublicKey() { return publicKey == null ? null : publicKey.clone(); }

    public void setNonce(long nonce) { this.nonce = nonce; }
    public void setChainId(long chainId) { this.chainId = chainId; }
    public void setSender(String sender) { this.sender = sender; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public void setGasLimit(int gasLimit) { this.gasLimit = gasLimit; }
    public void setStatus(TransactionStatus status) { this.status = status; }
    public void setContractCreation(boolean contractCreation) { this.contractCreation = contractCreation; }
    public void setContractCall(boolean contractCall) { this.contractCall = contractCall; }
    public void setSignature(SignatureResult signature) { this.signature = signature; }
    public void setPublicKey(byte[] publicKey) { this.publicKey = publicKey == null ? null : publicKey.clone(); }

    public void setValue(Long value) {
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }
        this.value = value; // negative values are reported by isValid(), as before
    }

    public void setGasPrice(BigInteger gasPrice) {
        if (gasPrice == null || gasPrice.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("Gas price must be non-negative");
        }
        this.gasPrice = gasPrice;
    }

    /** Total the sender must hold: {@code gasPrice * gasLimit + value}. */
    public BigInteger getTotalCost() {
        return gasPrice.multiply(BigInteger.valueOf(gasLimit)).add(BigInteger.valueOf(value));
    }

    // ------------------------------------------------------------------ encoding

    /**
     * Canonical bytes that the sender signs. Every variable-length field is length-prefixed so that
     * two different transactions can never serialise to the same bytes.
     */
    public byte[] signingBytes() {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(128 + (data == null ? 0 : data.length));
            DataOutputStream out = new DataOutputStream(buffer);
            out.write(DOMAIN_TAG);
            out.writeLong(chainId);
            out.writeLong(nonce);
            writeField(out, unsignedBytes(gasPrice));
            out.writeInt(gasLimit);
            writeField(out, normalizeId(sender).getBytes(StandardCharsets.UTF_8));
            writeField(out, normalizeId(recipient).getBytes(StandardCharsets.UTF_8));
            out.writeLong(value);
            writeField(out, data == null ? new byte[0] : data);
            out.flush();
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to encode transaction", e); // cannot happen for in-memory streams
        }
    }

    /** The 32-byte digest that is actually signed. */
    public byte[] signingHash() {
        return Secp256k1.keccak256(signingBytes());
    }

    /** Signing bytes followed by the signature and public key (if any). */
    @Override
    public byte[] toBytes() {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buffer);
            out.write(signingBytes());
            if (signature != null && publicKey != null) {
                out.writeByte(1);
                writeField(out, unsignedBytes(signature.getR()));
                writeField(out, unsignedBytes(signature.getS()));
                writeField(out, publicKey);
            } else {
                out.writeByte(0);
            }
            out.flush();
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to encode transaction", e);
        }
    }

    // ------------------------------------------------------------------ signing

    /**
     * Signs this transaction. The key must belong to {@link #getSender()}; signing with any other
     * key is refused because the result would never verify.
     */
    public void sign(PrivateKey privateKey) {
        BigInteger scalar = Secp256k1.privateScalar(privateKey);
        byte[] signerPublicKey = Secp256k1.publicKeyFromPrivate(scalar);
        String signerAddress = Secp256k1.address(signerPublicKey);
        if (sender == null || !normalizeId(sender).equals(normalizeId(signerAddress))) {
            throw new IllegalStateException("Sender does not match signing key");
        }
        this.publicKey = signerPublicKey;
        this.signature = Secp256k1.sign(signingHash(), scalar);
    }

    /**
     * Cryptographically verifies the transaction: the embedded public key must hash to the sender
     * address and the signature must be a valid, low-S ECDSA signature over {@link #signingHash()}.
     * Returns false for unsigned, tampered or forged transactions.
     */
    public boolean verifySignature() {
        if (signature == null || publicKey == null || sender == null) {
            return false;
        }
        try {
            String derived = Secp256k1.toHex(Secp256k1.addressBytes(publicKey));
            if (!normalizeId(sender).equals(derived)) {
                return false;
            }
            return Secp256k1.verify(signingHash(), signature, publicKey);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Structural validity (required fields present, non-negative amounts, signature attached). This
     * does not verify the signature; call {@link #verifySignature()} for authentication.
     */
    public boolean isValid() {
        if (sender == null || sender.isEmpty()) {
            return false;
        }
        if (!contractCreation && (recipient == null || recipient.isEmpty())) {
            return false; // only contract-creation transactions may omit the recipient
        }
        if (value < 0 || gasLimit < 0) {
            return false;
        }
        if (gasPrice == null || gasPrice.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }
        return signature != null;
    }

    // ------------------------------------------------------------------ hash

    /** Hex transaction hash, always derived from the current contents unless explicitly overridden. */
    public String getHash() {
        if (hash != null) {
            return hash.toString();
        }
        return calculateHash().toString();
    }

    /** Installs an explicit hash (legacy); call {@link #updateHash()} to go back to the computed one. */
    public void setHash(Hash hash) {
        this.hash = hash;
    }

    /** Discards any explicit hash so {@link #getHash()} reflects the current contents. */
    public void updateHash() {
        this.hash = null;
    }

    private Hash calculateHash() {
        return Hash.fromBytes(Secp256k1.keccak256(toBytes()));
    }

    // ------------------------------------------------------------------ helpers

    /** Lower-case, trimmed, without a leading 0x; identifiers compare equal regardless of form. */
    static String normalizeId(String id) {
        if (id == null) {
            return "";
        }
        String s = id.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("0x") ? s.substring(2) : s;
    }

    private static void writeField(DataOutputStream out, byte[] field) throws IOException {
        out.writeInt(field.length);
        out.write(field);
    }

    private static byte[] unsignedBytes(BigInteger value) {
        if (value == null || value.signum() == 0) {
            return new byte[0];
        }
        byte[] raw = value.toByteArray();
        return raw[0] == 0 ? Arrays.copyOfRange(raw, 1, raw.length) : raw;
    }
}
