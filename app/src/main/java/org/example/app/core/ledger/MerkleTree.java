package org.example.app.core.ledger;

import org.example.app.core.crypto.Secp256k1;

import java.util.ArrayList;
import java.util.List;

/**
 * Binary Merkle tree over Keccak-256, following the RFC 6962 / RFC 9162 construction:
 * leaves and inner nodes are domain separated (0x00 / 0x01 prefixes) and an odd node is never
 * duplicated, so a tree cannot be made to hash to the same root with different contents
 * (no second-preimage or duplicate-leaf ambiguity). Inclusion proofs are what a country shard
 * hands to another shard to prove that a transaction or state entry is in a committed block,
 * without revealing anything else.
 */
public final class MerkleTree {
    private static final byte[] LEAF_PREFIX = {0x00};
    private static final byte[] NODE_PREFIX = {0x01};

    private MerkleTree() {
    }

    public static byte[] emptyRoot() {
        return Secp256k1.keccak256(new byte[0]);
    }

    public static byte[] leafHash(byte[] data) {
        return Secp256k1.keccak256(Bytes.concat(LEAF_PREFIX, data));
    }

    private static byte[] nodeHash(byte[] left, byte[] right) {
        return Secp256k1.keccak256(Bytes.concat(NODE_PREFIX, left, right));
    }

    public static byte[] root(List<byte[]> leaves) {
        return subtreeRoot(hashLeaves(leaves), 0, leaves.size());
    }

    /** Inclusion proof (audit path) for the leaf at {@code index}. */
    public static List<byte[]> proof(List<byte[]> leaves, int index) {
        if (index < 0 || index >= leaves.size()) {
            throw new IllegalArgumentException("Leaf index out of range: " + index);
        }
        List<byte[]> path = new ArrayList<>();
        buildPath(hashLeaves(leaves), index, 0, leaves.size(), path);
        return path;
    }

    /** Verifies that {@code leafData} is the leaf at {@code index} of a tree of {@code size} leaves with root {@code root}. */
    public static boolean verify(byte[] leafData, int index, int size, List<byte[]> proof, byte[] root) {
        if (leafData == null || proof == null || root == null || index < 0 || size <= 0 || index >= size) {
            return false;
        }
        long fn = index;
        long sn = (long) size - 1;
        byte[] r = leafHash(leafData);
        for (byte[] p : proof) {
            if (sn == 0) {
                return false;
            }
            if ((fn & 1) == 1 || fn == sn) {
                r = nodeHash(p, r);
                if ((fn & 1) == 0) {
                    while ((fn & 1) == 0 && fn != 0) {
                        fn >>= 1;
                        sn >>= 1;
                    }
                }
            } else {
                r = nodeHash(r, p);
            }
            fn >>= 1;
            sn >>= 1;
        }
        return sn == 0 && Bytes.equal(r, root);
    }

    private static byte[][] hashLeaves(List<byte[]> leaves) {
        byte[][] hashes = new byte[leaves.size()][];
        for (int i = 0; i < hashes.length; i++) {
            hashes[i] = leafHash(leaves.get(i));
        }
        return hashes;
    }

    private static byte[] subtreeRoot(byte[][] hashes, int from, int to) {
        int n = to - from;
        if (n == 0) {
            return emptyRoot();
        }
        if (n == 1) {
            return hashes[from];
        }
        int k = largestPowerOfTwoBelow(n);
        return nodeHash(subtreeRoot(hashes, from, from + k), subtreeRoot(hashes, from + k, to));
    }

    private static void buildPath(byte[][] hashes, int index, int from, int to, List<byte[]> out) {
        int n = to - from;
        if (n <= 1) {
            return;
        }
        int k = largestPowerOfTwoBelow(n);
        if (index < from + k) {
            buildPath(hashes, index, from, from + k, out);
            out.add(subtreeRoot(hashes, from + k, to));
        } else {
            buildPath(hashes, index, from + k, to, out);
            out.add(subtreeRoot(hashes, from, from + k));
        }
    }

    private static int largestPowerOfTwoBelow(int n) {
        int k = Integer.highestOneBit(n);
        return k == n ? k >> 1 : k;
    }
}
