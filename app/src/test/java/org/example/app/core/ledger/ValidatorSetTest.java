package org.example.app.core.ledger;

import org.example.app.core.crypto.Secp256k1;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorSetTest {

    private static ValidatorInfo v(long stake) throws Exception {
        return new ValidatorInfo(Secp256k1.rawPublicKey(Secp256k1.generateKeyPair().getPublic()), BigInteger.valueOf(stake));
    }

    @Test
    void quorumIsStrictlyMoreThanTwoThirds() throws Exception {
        ValidatorSet set = new ValidatorSet(List.of(v(1), v(1), v(1)));
        assertFalse(set.isQuorum(BigInteger.valueOf(2))); // exactly 2/3 is not enough
        assertTrue(set.isQuorum(BigInteger.valueOf(3)));
        ValidatorSet four = new ValidatorSet(List.of(v(1), v(1), v(1), v(1)));
        assertFalse(four.isQuorum(BigInteger.valueOf(2)));
        assertTrue(four.isQuorum(BigInteger.valueOf(3)));
    }

    @Test
    void quorumIsStakeWeighted() throws Exception {
        ValidatorSet set = new ValidatorSet(List.of(v(60), v(20), v(10), v(10)));
        assertFalse(set.isQuorum(BigInteger.valueOf(60)));
        assertFalse(set.isQuorum(BigInteger.valueOf(66)));
        assertTrue(set.isQuorum(BigInteger.valueOf(67)));
    }

    @Test
    void proposerIsDeterministicAndOrderIndependent() throws Exception {
        List<ValidatorInfo> list = new ArrayList<>(List.of(v(10), v(20), v(30), v(40)));
        ValidatorSet a = new ValidatorSet(list);
        java.util.Collections.reverse(list);
        ValidatorSet b = new ValidatorSet(list);
        assertArrayEquals(a.hash(), b.hash());
        for (long h = 1; h < 50; h++) {
            assertEquals(a.proposerFor(h, 0).getAddress(), b.proposerFor(h, 0).getAddress());
        }
    }

    @Test
    void proposerFrequencyFollowsStake() throws Exception {
        ValidatorInfo big = v(90);
        ValidatorSet set = new ValidatorSet(List.of(big, v(10)));
        int bigWins = 0;
        for (long h = 1; h <= 2000; h++) {
            if (set.proposerFor(h, 0).getAddress().equals(big.getAddress())) bigWins++;
        }
        assertTrue(bigWins > 1650 && bigWins < 1950, "bigWins=" + bigWins);
    }

    @Test
    void roundsRotateTheProposer() throws Exception {
        ValidatorSet set = new ValidatorSet(List.of(v(1), v(1), v(1), v(1)));
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int r = 0; r < 60; r++) seen.add(set.proposerFor(5, r).getAddress());
        assertTrue(seen.size() > 1);
    }

    @Test
    void hashChangesWithStakeOrMembership() throws Exception {
        ValidatorInfo x = v(10);
        ValidatorInfo y = v(10);
        byte[] h1 = new ValidatorSet(List.of(x, y)).hash();
        byte[] h2 = new ValidatorSet(List.of(x, new ValidatorInfo(y.getPublicKey(), BigInteger.valueOf(11)))).hash();
        assertFalse(java.util.Arrays.equals(h1, h2));
        assertFalse(java.util.Arrays.equals(h1, new ValidatorSet(List.of(x)).hash()));
    }

    @Test
    void rejectsEmptyDuplicatesAndBadValidators() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new ValidatorSet(List.of()));
        ValidatorInfo x = v(5);
        assertThrows(IllegalArgumentException.class, () -> new ValidatorSet(List.of(x, x)));
        assertThrows(IllegalArgumentException.class, () -> new ValidatorInfo(new byte[10], BigInteger.ONE));
        assertThrows(IllegalArgumentException.class, () -> new ValidatorInfo(x.getPublicKey(), BigInteger.ZERO));
    }

    @Test
    void findIsCaseInsensitiveAndNullSafe() throws Exception {
        ValidatorInfo x = v(5);
        ValidatorSet set = new ValidatorSet(List.of(x));
        assertNotNull(set.find(x.getAddress().toUpperCase().replace("0X", "0x")));
        assertNull(set.find("nonsense"));
        assertNull(set.find(null));
    }
}
