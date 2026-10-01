package org.example.app.core.crypto;

import org.example.app.core.crypto.Keypair.SignatureResult;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

/** Known-answer and property tests for the secp256k1 / Keccak-256 primitives. */
class Secp256k1Test {

    private static final BigInteger N =
            new BigInteger("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", 16);

    private static byte[] digest(String message) {
        return Secp256k1.keccak256(message.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void keccak256MatchesPublishedVector() {
        assertEquals("c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470",
                Secp256k1.toHex(Secp256k1.keccak256(new byte[0])));
    }

    @Test
    void addressesMatchPublishedEthereumVectors() {
        // Well-known addresses of the private keys 1 and 2 on secp256k1 / Keccak-256.
        assertEquals("0x7e5f4552091a69125d5dfcb7b8c2659029395bdf",
                Secp256k1.address(Secp256k1.publicKeyFromPrivate(BigInteger.ONE)));
        assertEquals("0x2b5ad5c4795c026514f8317c7a215e218dccd6cf",
                Secp256k1.address(Secp256k1.publicKeyFromPrivate(BigInteger.TWO)));
    }

    @Test
    void generatedKeysHaveDistinctWellFormedAddresses() throws Exception {
        KeyPair a = Secp256k1.generateKeyPair();
        KeyPair b = Secp256k1.generateKeyPair();
        String addressA = Secp256k1.address(a.getPublic());
        String addressB = Secp256k1.address(b.getPublic());
        assertTrue(addressA.matches("0x[0-9a-f]{40}"), "Address must be 0x + 40 lowercase hex characters");
        assertNotEquals(addressA, addressB, "Two generated keys must not share an address");
    }

    @Test
    void publicKeyDerivationAndKeyPairRebuildAreConsistent() throws Exception {
        KeyPair pair = Secp256k1.generateKeyPair();
        BigInteger d = Secp256k1.privateScalar(pair.getPrivate());
        assertArrayEquals(Secp256k1.rawPublicKey(pair.getPublic()), Secp256k1.publicKeyFromPrivate(d));

        KeyPair rebuilt = Secp256k1.keyPairFromPrivate(Secp256k1.toUnsigned32(d));
        assertEquals(Secp256k1.address(pair.getPublic()), Secp256k1.address(rebuilt.getPublic()));
    }

    @Test
    void signatureVerifiesAndIsDeterministic() throws Exception {
        KeyPair pair = Secp256k1.generateKeyPair();
        BigInteger d = Secp256k1.privateScalar(pair.getPrivate());
        byte[] publicKey = Secp256k1.rawPublicKey(pair.getPublic());
        byte[] digest = digest("pay 10 TINC");

        SignatureResult first = Secp256k1.sign(digest, d);
        SignatureResult second = Secp256k1.sign(digest, d);

        assertTrue(Secp256k1.verify(digest, first, publicKey), "Signature must verify");
        assertEquals(first.getR(), second.getR(), "RFC 6979 signing must be deterministic");
        assertEquals(first.getS(), second.getS(), "RFC 6979 signing must be deterministic");
    }

    @Test
    void manySignaturesAreLowSAndValid() throws Exception {
        KeyPair pair = Secp256k1.generateKeyPair();
        BigInteger d = Secp256k1.privateScalar(pair.getPrivate());
        byte[] publicKey = Secp256k1.rawPublicKey(pair.getPublic());
        for (int i = 0; i < 100; i++) {
            byte[] digest = digest("message-" + i);
            SignatureResult sig = Secp256k1.sign(digest, d);
            assertTrue(sig.getS().compareTo(N.shiftRight(1)) <= 0, "S must be in the lower half of the curve order");
            assertTrue(Secp256k1.verify(digest, sig, publicKey), "Signature " + i + " must verify");
        }
    }

    @Test
    void wrongMessageOrWrongKeyIsRejected() throws Exception {
        KeyPair signer = Secp256k1.generateKeyPair();
        KeyPair other = Secp256k1.generateKeyPair();
        SignatureResult sig = Secp256k1.sign(digest("original"), Secp256k1.privateScalar(signer.getPrivate()));

        assertFalse(Secp256k1.verify(digest("tampered"), sig, Secp256k1.rawPublicKey(signer.getPublic())));
        assertFalse(Secp256k1.verify(digest("original"), sig, Secp256k1.rawPublicKey(other.getPublic())));
    }

    @Test
    void forgedPlaceholderAndMalleatedSignaturesAreRejected() throws Exception {
        KeyPair pair = Secp256k1.generateKeyPair();
        byte[] publicKey = Secp256k1.rawPublicKey(pair.getPublic());
        byte[] digest = digest("forge me");

        // The old code base attached r = 1, s = 1 to every transaction; it must never verify.
        assertFalse(Secp256k1.verify(digest, new SignatureResult(BigInteger.ONE, BigInteger.ONE), publicKey));

        // A high-S twin of a valid signature is rejected, so signatures are not malleable.
        SignatureResult valid = Secp256k1.sign(digest, Secp256k1.privateScalar(pair.getPrivate()));
        SignatureResult twin = new SignatureResult(valid.getR(), N.subtract(valid.getS()));
        assertFalse(Secp256k1.verify(digest, twin, publicKey));
    }

    @Test
    void malformedInputsNeverThrowFromVerify() throws Exception {
        KeyPair pair = Secp256k1.generateKeyPair();
        byte[] publicKey = Secp256k1.rawPublicKey(pair.getPublic());
        byte[] digest = digest("x");
        SignatureResult sig = Secp256k1.sign(digest, Secp256k1.privateScalar(pair.getPrivate()));

        byte[] offCurve = publicKey.clone();
        offCurve[7] ^= 1;
        assertFalse(Secp256k1.verify(digest, sig, offCurve), "Point not on the curve");
        assertFalse(Secp256k1.verify(digest, sig, new byte[10]), "Wrong public key length");
        assertFalse(Secp256k1.verify(new byte[5], sig, publicKey), "Wrong digest length");
        assertFalse(Secp256k1.verify(digest, null, publicKey), "Missing signature");
    }

    @Test
    void strictHexDecoding() {
        assertArrayEquals(new byte[]{0x12, (byte) 0xab}, Secp256k1.fromHex("0x12aB"));
        assertThrows(IllegalArgumentException.class, () -> Secp256k1.fromHex("0x123"));
        assertThrows(IllegalArgumentException.class, () -> Secp256k1.fromHex("0xzz"));
        assertThrows(IllegalArgumentException.class, () -> Secp256k1.fromHex(null));
    }
}
