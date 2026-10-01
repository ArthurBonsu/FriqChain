package org.example.app.core.ledger;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MerkleTreeTest {

    private static List<byte[]> leaves(int n) {
        List<byte[]> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(("leaf-" + i).getBytes());
        return l;
    }

    @Test
    void everyLeafOfEverySizeHasAVerifyingProof() {
        for (int n = 1; n <= 40; n++) {
            List<byte[]> l = leaves(n);
            byte[] root = MerkleTree.root(l);
            for (int i = 0; i < n; i++) {
                assertTrue(MerkleTree.verify(l.get(i), i, n, MerkleTree.proof(l, i), root), "n=" + n + " i=" + i);
            }
        }
    }

    @Test
    void proofFailsForWrongLeafIndexSizeOrRoot() {
        List<byte[]> l = leaves(7);
        byte[] root = MerkleTree.root(l);
        List<byte[]> proof = MerkleTree.proof(l, 3);
        assertFalse(MerkleTree.verify("other".getBytes(), 3, 7, proof, root));
        assertFalse(MerkleTree.verify(l.get(3), 2, 7, proof, root));
        // the size only fixes the shape of the path: a size that would need a different shape fails
        assertFalse(MerkleTree.verify(l.get(3), 3, 3, proof, root));
        assertFalse(MerkleTree.verify(l.get(3), 3, 40, proof, root));
        assertFalse(MerkleTree.verify(l.get(3), 3, 7, proof, new byte[32]));
        assertFalse(MerkleTree.verify(l.get(3), 7, 7, proof, root));
        assertFalse(MerkleTree.verify(l.get(3), -1, 7, proof, root));
    }

    @Test
    void tamperedOrTruncatedProofFails() {
        List<byte[]> l = leaves(9);
        byte[] root = MerkleTree.root(l);
        List<byte[]> proof = new ArrayList<>(MerkleTree.proof(l, 4));
        List<byte[]> truncated = new ArrayList<>(proof.subList(0, proof.size() - 1));
        assertFalse(MerkleTree.verify(l.get(4), 4, 9, truncated, root));
        byte[] flipped = proof.get(0).clone();
        flipped[0] ^= 1;
        proof.set(0, flipped);
        assertFalse(MerkleTree.verify(l.get(4), 4, 9, proof, root));
    }

    @Test
    void oddSizedTreesDoNotCollideWithDuplicatedLeaf() {
        List<byte[]> three = leaves(3);
        List<byte[]> four = new ArrayList<>(three);
        four.add(three.get(2));
        assertFalse(java.util.Arrays.equals(MerkleTree.root(three), MerkleTree.root(four)));
    }

    @Test
    void leavesAndInteriorNodesAreDomainSeparated() {
        List<byte[]> two = leaves(2);
        byte[] interior = MerkleTree.root(two);
        // a single leaf whose content equals the two child hashes must not reproduce the root
        byte[] forged = Bytes.concat(MerkleTree.leafHash(two.get(0)), MerkleTree.leafHash(two.get(1)));
        assertFalse(java.util.Arrays.equals(interior, MerkleTree.root(List.of(forged))));
    }

    @Test
    void orderMatters() {
        List<byte[]> a = leaves(4);
        List<byte[]> b = new ArrayList<>(a);
        java.util.Collections.swap(b, 0, 1);
        assertFalse(java.util.Arrays.equals(MerkleTree.root(a), MerkleTree.root(b)));
    }

    @Test
    void emptyTreeHasStableRootDifferentFromSingleEmptyLeaf() {
        assertArrayEquals(MerkleTree.emptyRoot(), MerkleTree.root(List.of()));
        assertFalse(java.util.Arrays.equals(MerkleTree.emptyRoot(), MerkleTree.root(List.of(new byte[0]))));
    }
}
