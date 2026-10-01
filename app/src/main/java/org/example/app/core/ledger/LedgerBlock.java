package org.example.app.core.ledger;

import org.example.app.core.block.Transaction;
import org.example.app.core.crypto.Secp256k1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A block of the BFT ledger. The header commits to everything that matters: the shard (chain id),
 * height, parent, time, proposer, the Merkle root of the transactions, the Merkle root of the
 * resulting account state and the hash of the validator set. {@link #hash()} is the Keccak-256 of the
 * canonical header encoding, so it is always recomputed from the content and never merely claimed.
 * The commit votes that make the block final are NOT part of the hash (they sign it).
 *
 * <p>Transactions are mutable objects; they must not be modified after a block is built. The ledger
 * recomputes the transaction root from the block's transactions when it verifies, so a tampered
 * transaction is detected.
 */
public final class LedgerBlock {
    public static final int VERSION = 1;

    private final long chainId;
    private final long number;
    private final byte[] parentHash;
    private final long timestamp;
    private final int round;
    private final String proposer;
    private final byte[] txRoot;
    private final byte[] stateRoot;
    private final byte[] validatorSetHash;
    private final List<Transaction> transactions;
    private final List<CommitVote> commitVotes;
    private final byte[] hash;

    public LedgerBlock(long chainId, long number, byte[] parentHash, long timestamp, int round, String proposer,
                       byte[] txRoot, byte[] stateRoot, byte[] validatorSetHash,
                       List<Transaction> transactions, List<CommitVote> commitVotes) {
        requireLength(parentHash, "parentHash");
        requireLength(txRoot, "txRoot");
        requireLength(stateRoot, "stateRoot");
        requireLength(validatorSetHash, "validatorSetHash");
        this.chainId = chainId;
        this.number = number;
        this.parentHash = parentHash.clone();
        this.timestamp = timestamp;
        this.round = round;
        this.proposer = Addresses.canonical(proposer);
        this.txRoot = txRoot.clone();
        this.stateRoot = stateRoot.clone();
        this.validatorSetHash = validatorSetHash.clone();
        this.transactions = Collections.unmodifiableList(new ArrayList<>(transactions));
        this.commitVotes = Collections.unmodifiableList(new ArrayList<>(commitVotes));
        this.hash = Secp256k1.keccak256(headerBytes());
    }

    private static void requireLength(byte[] value, String name) {
        if (value == null || value.length != 32) {
            throw new IllegalArgumentException(name + " must be 32 bytes");
        }
    }

    /** Canonical header encoding that the block hash commits to. */
    byte[] headerBytes() {
        return Bytes.concat(
                Bytes.u32(VERSION), Bytes.u64(chainId), Bytes.u64(number), parentHash,
                Bytes.u64(timestamp), Bytes.u32(round), Addresses.bytes(proposer),
                txRoot, stateRoot, validatorSetHash);
    }

    /** Merkle root over the encoded transactions, in order. */
    public static byte[] computeTxRoot(List<Transaction> transactions) {
        List<byte[]> leaves = new ArrayList<>(transactions.size());
        for (Transaction tx : transactions) {
            leaves.add(tx.toBytes());
        }
        return MerkleTree.root(leaves);
    }

    /** Inclusion proof for the transaction at {@code index}; verify with {@link MerkleTree#verify}. */
    public List<byte[]> transactionProof(int index) {
        List<byte[]> leaves = new ArrayList<>(transactions.size());
        for (Transaction tx : transactions) {
            leaves.add(tx.toBytes());
        }
        return MerkleTree.proof(leaves, index);
    }

    public LedgerBlock withCommitVotes(List<CommitVote> votes) {
        return new LedgerBlock(chainId, number, parentHash, timestamp, round, proposer, txRoot, stateRoot,
                validatorSetHash, transactions, votes);
    }

    public byte[] hash() {
        return hash.clone();
    }

    public String hashHex() {
        return "0x" + Secp256k1.toHex(hash);
    }

    public long getChainId() { return chainId; }
    public long getNumber() { return number; }
    public byte[] getParentHash() { return parentHash.clone(); }
    public long getTimestamp() { return timestamp; }
    public int getRound() { return round; }
    public String getProposer() { return proposer; }
    public byte[] getTxRoot() { return txRoot.clone(); }
    public byte[] getStateRoot() { return stateRoot.clone(); }
    public byte[] getValidatorSetHash() { return validatorSetHash.clone(); }
    public List<Transaction> getTransactions() { return transactions; }
    public List<CommitVote> getCommitVotes() { return commitVotes; }

    @Override
    public String toString() {
        return "LedgerBlock{number=" + number + ", hash=" + hashHex() + ", txs=" + transactions.size() + "}";
    }
}
