package org.example.app.core.ledger;

import org.example.app.core.block.Transaction;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * One country shard's chain. A block is appended only when every rule holds: chain id, height,
 * parent link, timestamp, expected proposer, validator-set hash, transaction root, deterministic
 * execution to exactly the declared state root, size/gas limits, and a commit certificate carrying
 * signatures from more than two thirds of the stake of the validator set for that height.
 */
public final class Ledger {
    private static final String ZERO_ADDRESS = "0x" + "00".repeat(20);

    private final long chainId;
    private final LedgerConfig config;
    private final ValidatorSetProvider validators;
    private final TransactionExecutor executor;
    private final LongSupplier clock;

    private final List<LedgerBlock> blocks = new ArrayList<>();
    private final Map<String, LedgerBlock> byHash = new HashMap<>();
    private AccountState state;

    private Ledger(long chainId, LedgerConfig config, ValidatorSetProvider validators,
                   TransactionExecutor executor, LongSupplier clock) {
        this.chainId = chainId;
        this.config = config;
        this.validators = validators;
        this.executor = executor;
        this.clock = clock;
    }

    public static Ledger create(long chainId, AccountState genesisState, ValidatorSetProvider validators,
                                LedgerConfig config, TransactionExecutor executor, LongSupplier clock) {
        Ledger ledger = new Ledger(chainId, config, validators, executor, clock);
        ledger.state = genesisState.copy();
        LedgerBlock genesis = new LedgerBlock(chainId, 0, new byte[32], config.getGenesisTimestamp(), 0, ZERO_ADDRESS,
                MerkleTree.emptyRoot(), ledger.state.root(), validators.validatorSetFor(0).hash(),
                List.of(), List.of());
        ledger.blocks.add(genesis);
        ledger.byHash.put(genesis.hashHex(), genesis);
        return ledger;
    }

    // ------------------------------------------------------------------ queries

    public long getChainId() { return chainId; }
    public LedgerConfig getConfig() { return config; }
    public TransactionExecutor getExecutor() { return executor; }
    public long now() { return clock.getAsLong(); }

    public synchronized LedgerBlock tip() { return blocks.get(blocks.size() - 1); }
    public synchronized long height() { return tip().getNumber(); }
    public synchronized LedgerBlock blockByNumber(long number) {
        return number < 0 || number >= blocks.size() ? null : blocks.get((int) number);
    }
    public synchronized LedgerBlock blockByHash(String hashHex) { return byHash.get(hashHex.toLowerCase()); }
    public synchronized BigInteger balanceOf(String address) { return state.balanceOf(address); }
    public synchronized long nonceOf(String address) { return state.nonceOf(address); }
    public synchronized byte[] stateRoot() { return state.root(); }
    public synchronized AccountState stateSnapshot() { return state.copy(); }

    public ValidatorSet validatorSetForNext() { return validators.validatorSetFor(height() + 1); }

    public synchronized ValidatorInfo expectedProposer(int round) {
        return validators.validatorSetFor(height() + 1).proposerFor(height() + 1, round);
    }

    // ------------------------------------------------------------------ validation

    /**
     * Checks everything about a proposal except its commit certificate and returns the state that
     * results from executing it. Validators call this before voting.
     */
    public synchronized AccountState verifyProposal(LedgerBlock block) throws InvalidBlockException {
        if (block == null) {
            throw new InvalidBlockException("Block is null");
        }
        LedgerBlock parent = tip();
        if (block.getChainId() != chainId) {
            throw new InvalidBlockException("Wrong chain id");
        }
        if (block.getNumber() != parent.getNumber() + 1) {
            throw new InvalidBlockException("Wrong height: expected " + (parent.getNumber() + 1));
        }
        if (!Bytes.equal(block.getParentHash(), parent.hash())) {
            throw new InvalidBlockException("Parent hash mismatch");
        }
        if (block.getTimestamp() <= parent.getTimestamp()) {
            throw new InvalidBlockException("Timestamp must be later than the parent's");
        }
        if (block.getTimestamp() > clock.getAsLong() + config.getMaxClockDriftMillis()) {
            throw new InvalidBlockException("Timestamp too far in the future");
        }
        if (block.getRound() < 0) {
            throw new InvalidBlockException("Negative round");
        }
        ValidatorSet set = validators.validatorSetFor(block.getNumber());
        if (!Bytes.equal(block.getValidatorSetHash(), set.hash())) {
            throw new InvalidBlockException("Validator set hash mismatch");
        }
        ValidatorInfo expected = set.proposerFor(block.getNumber(), block.getRound());
        if (!expected.getAddress().equals(block.getProposer())) {
            throw new InvalidBlockException("Not the designated proposer for this height and round");
        }
        List<Transaction> txs = block.getTransactions();
        if (txs.size() > config.getMaxTransactionsPerBlock()) {
            throw new InvalidBlockException("Too many transactions");
        }
        if (!Bytes.equal(block.getTxRoot(), LedgerBlock.computeTxRoot(txs))) {
            throw new InvalidBlockException("Transaction root mismatch");
        }
        Set<String> seen = new HashSet<>();
        AccountState next = state.copy();
        long gas = 0;
        for (Transaction tx : txs) {
            if (!seen.add(tx.getHash())) {
                throw new InvalidBlockException("Duplicate transaction in block");
            }
            try {
                gas += executor.execute(next, tx, block.getProposer(), chainId);
            } catch (InvalidTransactionException e) {
                throw new InvalidBlockException("Invalid transaction " + tx.getHash() + ": " + e.getMessage());
            }
            if (gas > config.getMaxBlockGas()) {
                throw new InvalidBlockException("Block gas limit exceeded");
            }
        }
        if (!Bytes.equal(block.getStateRoot(), next.root())) {
            throw new InvalidBlockException("State root mismatch");
        }
        return next;
    }

    /** Verifies that {@code votes} hold a valid quorum for {@code block}; duplicates count once. */
    public void verifyCommitCertificate(LedgerBlock block, List<CommitVote> votes) throws InvalidBlockException {
        ValidatorSet set = validators.validatorSetFor(block.getNumber());
        byte[] hash = block.hash();
        Set<String> counted = new HashSet<>();
        BigInteger signed = BigInteger.ZERO;
        for (CommitVote vote : votes) {
            ValidatorInfo v = set.find(vote.getValidator());
            if (v == null) {
                throw new InvalidBlockException("Commit vote from a non-validator");
            }
            if (!vote.verify(chainId, block.getNumber(), hash, v.getPublicKey())) {
                throw new InvalidBlockException("Invalid commit signature from " + v.getAddress());
            }
            if (counted.add(v.getAddress())) {
                signed = signed.add(v.getStake());
            }
        }
        if (!set.isQuorum(signed)) {
            throw new InvalidBlockException("Commit certificate lacks a two-thirds stake quorum");
        }
    }

    /** Validates {@code block} completely (including its commit votes) and appends it. */
    public synchronized void append(LedgerBlock block) throws InvalidBlockException {
        AccountState next = verifyProposal(block);
        verifyCommitCertificate(block, block.getCommitVotes());
        blocks.add(block);
        byHash.put(block.hashHex(), block);
        state = next;
    }
}
