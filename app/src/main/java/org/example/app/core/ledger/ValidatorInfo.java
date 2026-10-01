package org.example.app.core.ledger;

import org.example.app.core.crypto.Secp256k1;

import java.math.BigInteger;

/** A validator's identity and voting weight. The address must be derived from the public key. */
public final class ValidatorInfo {
    private final String address;
    private final byte[] publicKey;
    private final BigInteger stake;

    public ValidatorInfo(byte[] publicKey, BigInteger stake) {
        if (publicKey == null || publicKey.length != Secp256k1.PUBLIC_KEY_LENGTH) {
            throw new IllegalArgumentException("Validator public key must be 64 bytes");
        }
        if (stake == null || stake.signum() <= 0) {
            throw new IllegalArgumentException("Validator stake must be positive");
        }
        this.publicKey = publicKey.clone();
        this.address = Secp256k1.address(publicKey);
        this.stake = stake;
    }

    public String getAddress() {
        return address;
    }

    public byte[] getPublicKey() {
        return publicKey.clone();
    }

    public BigInteger getStake() {
        return stake;
    }

    /** Canonical encoding: address (20) || public key (64) || stake (32). */
    byte[] encode() {
        return Bytes.concat(Addresses.bytes(address), publicKey, Bytes.unsigned(stake, 32));
    }
}
