package org.example.app.core.ledger;

import org.example.app.core.crypto.Keypair.SignatureResult;
import org.example.app.core.crypto.Secp256k1;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

/**
 * A validator's signed commitment to one block. Signing binds the shard (chain id), the height and
 * the block hash, so a vote cannot be replayed on another shard, at another height or for another
 * block. Two votes by the same validator for different blocks at one height are provable
 * misbehaviour (see {@link DoubleSignEvidence}).
 */
public final class CommitVote {
    private static final byte[] TAG = "TINC-COMMIT-V1".getBytes(StandardCharsets.US_ASCII);

    private final String validator;
    private final SignatureResult signature;

    public CommitVote(String validator, SignatureResult signature) {
        if (signature == null) {
            throw new IllegalArgumentException("Signature is required");
        }
        this.validator = Addresses.canonical(validator);
        this.signature = signature;
    }

    public static byte[] digest(long chainId, long height, byte[] blockHash) {
        if (blockHash == null || blockHash.length != 32) {
            throw new IllegalArgumentException("Block hash must be 32 bytes");
        }
        return Secp256k1.keccak256(Bytes.concat(TAG, Bytes.u64(chainId), Bytes.u64(height), blockHash));
    }

    public static CommitVote sign(long chainId, long height, byte[] blockHash, KeyPair validatorKey) {
        BigInteger scalar = Secp256k1.privateScalar(validatorKey.getPrivate());
        SignatureResult sig = Secp256k1.sign(digest(chainId, height, blockHash), scalar);
        return new CommitVote(Secp256k1.address(validatorKey.getPublic()), sig);
    }

    public boolean verify(long chainId, long height, byte[] blockHash, byte[] validatorPublicKey) {
        if (!validator.equals(Secp256k1.address(validatorPublicKey))) {
            return false;
        }
        return Secp256k1.verify(digest(chainId, height, blockHash), signature, validatorPublicKey);
    }

    public String getValidator() {
        return validator;
    }

    public SignatureResult getSignature() {
        return signature;
    }
}
