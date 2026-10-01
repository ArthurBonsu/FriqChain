package org.example.app.core.ledger;

import org.example.app.core.crypto.Secp256k1;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

class StakeRegistryTest {

    private static byte[] pub(KeyPair k) { return Secp256k1.rawPublicKey(k.getPublic()); }
    private static BigInteger n(long v) { return BigInteger.valueOf(v); }

    @Test
    void epochSnapshotsApplyToTheirHeights() throws Exception {
        StakeRegistry r = new StakeRegistry(10, 5, n(10), 1000, null);
        KeyPair a = Secp256k1.generateKeyPair(), b = Secp256k1.generateKeyPair();
        r.bond(pub(a), n(50));
        ValidatorSet e0 = r.sealEpoch(0);
        r.bond(pub(b), n(50));
        ValidatorSet e1 = r.sealEpoch(1);
        assertEquals(1, e0.size());
        assertEquals(2, e1.size());
        assertArrayEquals(e0.hash(), r.validatorSetFor(0).hash());
        assertArrayEquals(e0.hash(), r.validatorSetFor(9).hash());
        assertArrayEquals(e1.hash(), r.validatorSetFor(10).hash());
        assertArrayEquals(e1.hash(), r.validatorSetFor(55).hash()); // latest sealed epoch continues
    }

    @Test
    void laterBondsDoNotChangeASealedEpoch() throws Exception {
        StakeRegistry r = new StakeRegistry(10, 5, n(10), 1000, null);
        r.bond(pub(Secp256k1.generateKeyPair()), n(50));
        byte[] before = r.sealEpoch(0).hash();
        r.bond(pub(Secp256k1.generateKeyPair()), n(500));
        assertArrayEquals(before, r.validatorSetFor(3).hash());
    }

    @Test
    void epochsMustBeSealedInOrderOnce() throws Exception {
        StakeRegistry r = new StakeRegistry(10, 5, n(10), 1000, null);
        r.bond(pub(Secp256k1.generateKeyPair()), n(50));
        assertThrows(IllegalStateException.class, () -> r.sealEpoch(1));
        r.sealEpoch(0);
        assertThrows(IllegalStateException.class, () -> r.sealEpoch(0));
        assertThrows(IllegalStateException.class, () -> new StakeRegistry(10, 5, n(10), 1000, null).validatorSetFor(0));
    }

    @Test
    void minimumStakeCapAndAdmissionPolicyApply() throws Exception {
        KeyPair small = Secp256k1.generateKeyPair(), a = Secp256k1.generateKeyPair(), b = Secp256k1.generateKeyPair(), c = Secp256k1.generateKeyPair();
        String blocked = Secp256k1.address(c.getPublic());
        StakeRegistry r = new StakeRegistry(10, 2, n(10), 1000, info -> !info.getAddress().equals(blocked));
        r.bond(pub(small), n(9));
        r.bond(pub(a), n(30));
        r.bond(pub(b), n(20));
        r.bond(pub(c), n(1000));
        ValidatorSet set = r.sealEpoch(0);
        assertEquals(2, set.size());
        assertTrue(set.contains(Secp256k1.address(a.getPublic())));
        assertTrue(set.contains(Secp256k1.address(b.getPublic())));
        assertFalse(set.contains(blocked));
        assertFalse(set.contains(Secp256k1.address(small.getPublic())));
    }

    @Test
    void topValidatorsByStakeWinWhenOverCap() throws Exception {
        StakeRegistry r = new StakeRegistry(10, 2, n(1), 1000, null);
        KeyPair low = Secp256k1.generateKeyPair();
        r.bond(pub(low), n(5));
        r.bond(pub(Secp256k1.generateKeyPair()), n(50));
        r.bond(pub(Secp256k1.generateKeyPair()), n(40));
        assertFalse(r.sealEpoch(0).contains(Secp256k1.address(low.getPublic())));
    }

    @Test
    void unbondReducesStakeAndValidatesAmount() throws Exception {
        StakeRegistry r = new StakeRegistry(10, 5, n(10), 1000, null);
        KeyPair a = Secp256k1.generateKeyPair();
        r.bond(pub(a), n(50));
        String addr = Secp256k1.address(a.getPublic());
        r.unbond(addr, n(20));
        assertEquals(n(30), r.bondedOf(addr));
        assertThrows(IllegalArgumentException.class, () -> r.unbond(addr, n(31)));
        assertThrows(IllegalArgumentException.class, () -> r.unbond(addr, n(0)));
        r.unbond(addr, n(30));
        assertEquals(BigInteger.ZERO, r.bondedOf(addr));
    }

    private static DoubleSignEvidence evidence(KeyPair k, long chain, long height, byte[] h1, byte[] h2) {
        return new DoubleSignEvidence(chain, height, h1, CommitVote.sign(chain, height, h1, k),
                h2, CommitVote.sign(chain, height, h2, k));
    }

    @Test
    void doubleSigningIsSlashedOnceAndJailed() throws Exception {
        StakeRegistry r = new StakeRegistry(10, 5, n(10), 5000, null);
        KeyPair a = Secp256k1.generateKeyPair();
        r.bond(pub(a), n(1000));
        r.bond(pub(Secp256k1.generateKeyPair()), n(1000));
        byte[] h1 = new byte[32], h2 = new byte[32];
        h2[0] = 1;
        DoubleSignEvidence ev = evidence(a, 1, 7, h1, h2);
        assertTrue(r.submitEvidence(ev));
        assertEquals(n(500), r.totalSlashed());
        assertFalse(r.submitEvidence(ev), "same offence must not be punished twice");
        assertFalse(r.submitEvidence(evidence(a, 1, 7, h2, h1)), "swapped order is the same offence");
        assertThrows(IllegalStateException.class, () -> r.bond(pub(a), n(100)));
        assertFalse(r.sealEpoch(0).contains(Secp256k1.address(a.getPublic())));
    }

    @Test
    void invalidEvidenceIsIgnored() throws Exception {
        StakeRegistry r = new StakeRegistry(10, 5, n(10), 5000, null);
        KeyPair a = Secp256k1.generateKeyPair(), other = Secp256k1.generateKeyPair();
        r.bond(pub(a), n(1000));
        byte[] h = new byte[32];
        byte[] h2 = new byte[32];
        h2[5] = 9;
        assertFalse(r.submitEvidence(evidence(a, 1, 7, h, h)), "same block twice is not a double sign");
        // two votes by different validators are not evidence against anyone
        DoubleSignEvidence mixed = new DoubleSignEvidence(1, 7, h, CommitVote.sign(1, 7, h, a), h2, CommitVote.sign(1, 7, h2, other));
        assertFalse(r.submitEvidence(mixed));
        // evidence whose signatures were made for another height does not verify
        DoubleSignEvidence wrongHeight = new DoubleSignEvidence(1, 7, h, CommitVote.sign(1, 8, h, a), h2, CommitVote.sign(1, 8, h2, a));
        assertFalse(r.submitEvidence(wrongHeight));
        assertEquals(BigInteger.ZERO, r.totalSlashed());
        // unknown validator
        assertFalse(r.submitEvidence(evidence(other, 1, 7, h, h2)));
    }
}
