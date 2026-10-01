package org.example.app.core.crypto;

import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import org.example.app.core.crypto.Keypair.SignatureResult;

/**
 * Cryptographic primitives for the TINC chain: secp256k1 keys, Keccak-256 addresses and
 * deterministic, low-S ECDSA signatures over 32-byte digests.
 *
 * <p>Conventions (identical to Ethereum so standard tooling and mobile libraries interoperate):
 * <ul>
 *   <li>A public key is the 64-byte concatenation {@code X || Y} (no 0x04 prefix).</li>
 *   <li>An address is the last 20 bytes of {@code keccak256(publicKey)}.</li>
 *   <li>Signatures are (r, s) with {@code s <= n/2}; a high-S signature is rejected, which removes
 *       signature malleability (a third party cannot produce a second valid signature for the
 *       same message and so cannot alter a transaction hash that includes the signature).</li>
 *   <li>Nonces are derived per RFC 6979, so signing never depends on the quality of a runtime
 *       random number generator.</li>
 * </ul>
 * This class is stateless and thread-safe.
 */
public final class Secp256k1 {

    public static final int PRIVATE_KEY_LENGTH = 32;
    public static final int PUBLIC_KEY_LENGTH = 64;
    public static final int DIGEST_LENGTH = 32;

    private static final Provider BC = new BouncyCastleProvider();
    private static final X9ECParameters PARAMS = CustomNamedCurves.getByName("secp256k1");
    private static final ECDomainParameters DOMAIN =
            new ECDomainParameters(PARAMS.getCurve(), PARAMS.getG(), PARAMS.getN(), PARAMS.getH());
    private static final BigInteger N = PARAMS.getN();
    private static final BigInteger HALF_N = N.shiftRight(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private Secp256k1() {
    }

    // ------------------------------------------------------------------ hashing

    /** Keccak-256 (the original Keccak padding used by Ethereum, not NIST SHA3-256). */
    public static byte[] keccak256(byte[] input) {
        return new Keccak.Digest256().digest(input);
    }

    // ------------------------------------------------------------------ keys

    /** Generates a new secp256k1 key pair as standard JCA keys. */
    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", BC);
            generator.initialize(new ECGenParameterSpec("secp256k1"), RANDOM);
            return generator.generateKeyPair();
        } catch (InvalidAlgorithmParameterException e) {
            throw new IllegalStateException("secp256k1 is not available", e);
        }
    }

    /** Returns the private scalar of a JCA private key. */
    public static BigInteger privateScalar(PrivateKey key) {
        if (!(key instanceof ECPrivateKey)) {
            throw new IllegalArgumentException("Not an EC private key: " + (key == null ? "null" : key.getAlgorithm()));
        }
        return ((ECPrivateKey) key).getS();
    }

    /** Returns the 64-byte public key ({@code X || Y}) of a JCA public key. */
    public static byte[] rawPublicKey(PublicKey key) {
        if (!(key instanceof ECPublicKey)) {
            throw new IllegalArgumentException("Not an EC public key: " + (key == null ? "null" : key.getAlgorithm()));
        }
        java.security.spec.ECPoint w = ((ECPublicKey) key).getW();
        return concat(toUnsigned32(w.getAffineX()), toUnsigned32(w.getAffineY()));
    }

    /** Derives the 64-byte public key from a private scalar. */
    public static byte[] publicKeyFromPrivate(BigInteger privateScalar) {
        requireValidScalar(privateScalar);
        ECPoint q = DOMAIN.getG().multiply(privateScalar).normalize();
        return concat(toUnsigned32(q.getAffineXCoord().toBigInteger()),
                toUnsigned32(q.getAffineYCoord().toBigInteger()));
    }

    /** Rebuilds a JCA key pair from a 32-byte private key. */
    public static KeyPair keyPairFromPrivate(byte[] privateKey32) {
        if (privateKey32 == null || privateKey32.length != PRIVATE_KEY_LENGTH) {
            throw new IllegalArgumentException("Private key must be exactly " + PRIVATE_KEY_LENGTH + " bytes");
        }
        try {
            BigInteger d = new BigInteger(1, privateKey32);
            requireValidScalar(d);
            java.security.KeyFactory factory = java.security.KeyFactory.getInstance("EC", BC);
            org.bouncycastle.jce.spec.ECParameterSpec spec = new org.bouncycastle.jce.spec.ECParameterSpec(
                    PARAMS.getCurve(), PARAMS.getG(), PARAMS.getN(), PARAMS.getH());
            PrivateKey privateKey = factory.generatePrivate(new org.bouncycastle.jce.spec.ECPrivateKeySpec(d, spec));
            ECPoint q = DOMAIN.getG().multiply(d).normalize();
            PublicKey publicKey = factory.generatePublic(new org.bouncycastle.jce.spec.ECPublicKeySpec(q, spec));
            return new KeyPair(publicKey, privateKey);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Unable to rebuild key pair", e);
        }
    }

    // ------------------------------------------------------------------ addresses

    /** Address bytes (20) for a 64-byte public key. */
    public static byte[] addressBytes(byte[] publicKey64) {
        requirePublicKey(publicKey64);
        byte[] hash = keccak256(publicKey64);
        return Arrays.copyOfRange(hash, hash.length - 20, hash.length);
    }

    /** Canonical address string: {@code 0x} followed by 40 lowercase hex characters. */
    public static String address(byte[] publicKey64) {
        return "0x" + toHex(addressBytes(publicKey64));
    }

    public static String address(PublicKey publicKey) {
        return address(rawPublicKey(publicKey));
    }

    // ------------------------------------------------------------------ signatures

    /**
     * Signs a 32-byte digest. Deterministic (RFC 6979) and always returns a low-S signature.
     */
    public static SignatureResult sign(byte[] digest32, BigInteger privateScalar) {
        requireDigest(digest32);
        requireValidScalar(privateScalar);
        ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));
        signer.init(true, new ECPrivateKeyParameters(privateScalar, DOMAIN));
        BigInteger[] rs = signer.generateSignature(digest32);
        BigInteger s = rs[1];
        if (s.compareTo(HALF_N) > 0) {
            s = N.subtract(s);
        }
        return new SignatureResult(rs[0], s);
    }

    /**
     * Verifies a signature over a 32-byte digest. Returns false (never throws) for any malformed
     * input, for a public key that is not on the curve and for a high-S signature.
     */
    public static boolean verify(byte[] digest32, SignatureResult signature, byte[] publicKey64) {
        try {
            if (digest32 == null || digest32.length != DIGEST_LENGTH
                    || signature == null || signature.getR() == null || signature.getS() == null
                    || publicKey64 == null || publicKey64.length != PUBLIC_KEY_LENGTH) {
                return false;
            }
            BigInteger r = signature.getR();
            BigInteger s = signature.getS();
            if (r.signum() <= 0 || s.signum() <= 0 || r.compareTo(N) >= 0 || s.compareTo(HALF_N) > 0) {
                return false;
            }
            BigInteger x = new BigInteger(1, Arrays.copyOfRange(publicKey64, 0, 32));
            BigInteger y = new BigInteger(1, Arrays.copyOfRange(publicKey64, 32, 64));
            ECPoint point = PARAMS.getCurve().validatePoint(x, y);
            ECDSASigner verifier = new ECDSASigner();
            verifier.init(false, new ECPublicKeyParameters(point, DOMAIN));
            return verifier.verifySignature(digest32, r, s);
        } catch (RuntimeException e) {
            return false; // invalid curve point or malformed input
        }
    }

    // ------------------------------------------------------------------ helpers

    public static String toHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[2 * i] = digits[(bytes[i] >> 4) & 0xF];
            out[2 * i + 1] = digits[bytes[i] & 0xF];
        }
        return new String(out);
    }

    /** Strict hex decoder: optional 0x prefix, even length, hex digits only. */
    public static byte[] fromHex(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("Hex string is null");
        }
        String h = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
        if ((h.length() & 1) != 0) {
            throw new IllegalArgumentException("Hex string has odd length: " + hex);
        }
        byte[] out = new byte[h.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(h.charAt(2 * i), 16);
            int lo = Character.digit(h.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Invalid hex character in: " + hex);
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    public static byte[] toUnsigned32(BigInteger value) {
        byte[] raw = value.toByteArray();
        if (raw.length == 32) {
            return raw;
        }
        byte[] out = new byte[32];
        if (raw.length > 32) { // leading sign byte
            System.arraycopy(raw, raw.length - 32, out, 0, 32);
        } else {
            System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
        }
        return out;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static void requireValidScalar(BigInteger d) {
        if (d == null || d.signum() <= 0 || d.compareTo(N) >= 0) {
            throw new IllegalArgumentException("Private key out of range");
        }
    }

    private static void requireDigest(byte[] digest) {
        if (digest == null || digest.length != DIGEST_LENGTH) {
            throw new IllegalArgumentException("Digest must be exactly " + DIGEST_LENGTH + " bytes");
        }
    }

    private static void requirePublicKey(byte[] key) {
        if (key == null || key.length != PUBLIC_KEY_LENGTH) {
            throw new IllegalArgumentException("Public key must be exactly " + PUBLIC_KEY_LENGTH + " bytes");
        }
    }
}
