package org.example.app.core.wallet;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERSequence;
import org.example.app.core.crypto.Keypair.SignatureResult;
import org.example.app.core.crypto.Secp256k1;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;

/**
 * Wallet-facing key helpers. All cryptography is delegated to {@link Secp256k1}: secp256k1 keys,
 * addresses derived as the last 20 bytes of {@code keccak256(publicKey)}, and deterministic low-S
 * ECDSA signatures.
 */
public final class KeyUtils {

    private KeyUtils() {
    }

    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        return Secp256k1.generateKeyPair();
    }

    /** Canonical address: {@code 0x} + 40 lowercase hex characters. */
    public static String getAddress(PublicKey publicKey) {
        return Secp256k1.address(publicKey);
    }

    /** Signs {@code message} (hashed with Keccak-256) and returns the signature as DER bytes. */
    public static byte[] sign(byte[] message, PrivateKey privateKey) {
        if (message == null) {
            throw new IllegalArgumentException("Message must not be null");
        }
        SignatureResult sig = Secp256k1.sign(Secp256k1.keccak256(message), Secp256k1.privateScalar(privateKey));
        try {
            return new DERSequence(new org.bouncycastle.asn1.ASN1Encodable[]{
                    new ASN1Integer(sig.getR()), new ASN1Integer(sig.getS())}).getEncoded("DER");
        } catch (IOException e) {
            throw new IllegalStateException("Signing failed", e);
        }
    }

    /** Verifies a DER signature produced by {@link #sign}; returns false for anything malformed. */
    public static boolean verifySignature(byte[] message, byte[] signature, PublicKey publicKey) {
        if (message == null || signature == null || publicKey == null) {
            return false;
        }
        try {
            ASN1Sequence seq = ASN1Sequence.getInstance(signature);
            if (seq.size() != 2 || !Arrays.equals(seq.getEncoded("DER"), signature)) {
                return false; // not canonical DER
            }
            BigInteger r = ASN1Integer.getInstance(seq.getObjectAt(0)).getValue();
            BigInteger s = ASN1Integer.getInstance(seq.getObjectAt(1)).getValue();
            return Secp256k1.verify(Secp256k1.keccak256(message), new SignatureResult(r, s),
                    Secp256k1.rawPublicKey(publicKey));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
