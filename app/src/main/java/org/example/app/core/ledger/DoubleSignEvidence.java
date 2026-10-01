package org.example.app.core.ledger;

import org.example.app.core.crypto.Secp256k1;

/** Proof that one validator signed two different blocks at the same height of the same chain. */
public final class DoubleSignEvidence {
    private final long chainId;
    private final long height;
    private final byte[] hashA;
    private final CommitVote voteA;
    private final byte[] hashB;
    private final CommitVote voteB;

    public DoubleSignEvidence(long chainId, long height, byte[] hashA, CommitVote voteA, byte[] hashB, CommitVote voteB) {
        this.chainId = chainId;
        this.height = height;
        this.hashA = hashA.clone();
        this.voteA = voteA;
        this.hashB = hashB.clone();
        this.voteB = voteB;
    }

    public String getValidator() { return voteA.getValidator(); }
    public long getChainId() { return chainId; }
    public long getHeight() { return height; }

    /** Stable identifier so the same offence is never punished twice. */
    public String id() {
        byte[] first = hashA, second = hashB;
        if (compare(first, second) > 0) { byte[] t = first; first = second; second = t; }
        return Secp256k1.toHex(Secp256k1.keccak256(Bytes.concat(
                Addresses.bytes(getValidator()), Bytes.u64(chainId), Bytes.u64(height), first, second)));
    }

    public boolean isValid(byte[] validatorPublicKey) {
        if (!voteA.getValidator().equals(voteB.getValidator()) || Bytes.equal(hashA, hashB)) {
            return false;
        }
        return voteA.verify(chainId, height, hashA, validatorPublicKey)
                && voteB.verify(chainId, height, hashB, validatorPublicKey);
    }

    private static int compare(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) {
            int d = (a[i] & 0xff) - (b[i] & 0xff);
            if (d != 0) return d;
        }
        return 0;
    }
}
