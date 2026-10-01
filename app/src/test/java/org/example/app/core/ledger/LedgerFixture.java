package org.example.app.core.ledger;

import org.example.app.core.block.Transaction;
import org.example.app.core.crypto.Secp256k1;
import org.example.app.core.wallet.Wallet;

import java.math.BigInteger;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Shared test setup: equal-stake validators, two funded wallets and a live clock. */
final class LedgerFixture {
    static final long GENESIS_TIME = 1_000_000L;
    static final BigInteger FUNDS = BigInteger.valueOf(1_000_000_000L);

    final List<KeyPair> validatorKeys = new ArrayList<>();
    final Map<String, KeyPair> keysByAddress = new HashMap<>();
    final StakeRegistry registry;
    final AtomicLong clock = new AtomicLong(GENESIS_TIME);
    final Wallet alice;
    final Wallet bob;
    final Ledger ledger;

    private LedgerFixture(int validators, long chainId) throws Exception {
        registry = new StakeRegistry(100, 10, BigInteger.TEN, 5_000, null);
        for (int i = 0; i < validators; i++) {
            KeyPair k = Secp256k1.generateKeyPair();
            validatorKeys.add(k);
            keysByAddress.put(Secp256k1.address(k.getPublic()), k);
            registry.bond(Secp256k1.rawPublicKey(k.getPublic()), BigInteger.valueOf(100));
        }
        registry.sealEpoch(0);
        alice = new Wallet();
        bob = new Wallet();
        alice.updateBalance(FUNDS.longValue());
        Map<String, BigInteger> alloc = new HashMap<>();
        alloc.put(alice.getAddress(), FUNDS);
        alloc.put(bob.getAddress(), FUNDS);
        ledger = Ledger.create(chainId, AccountState.genesis(alloc), registry,
                LedgerConfig.defaults(GENESIS_TIME), new TransferExecutor(), clock::get);
    }

    static LedgerFixture create(int validators) throws Exception {
        return new LedgerFixture(validators, Transaction.DEFAULT_CHAIN_ID);
    }

    KeyPair proposerKey(int round) {
        return keysByAddress.get(ledger.expectedProposer(round).getAddress());
    }

    LedgerBlock propose(List<Transaction> txs, int round) {
        clock.addAndGet(1_000);
        return BlockProducer.propose(ledger, proposerKey(round), txs, round);
    }

    List<CommitVote> votes(LedgerBlock block, int howMany) throws Exception {
        List<CommitVote> votes = new ArrayList<>();
        for (int i = 0; i < howMany; i++) {
            votes.add(new ValidatorSigner(validatorKeys.get(i)).voteOn(ledger, block));
        }
        return votes;
    }

    LedgerBlock certify(LedgerBlock block, int voters) throws Exception {
        return block.withCommitVotes(votes(block, voters));
    }

    /** Proposes, gathers every validator's vote and appends. */
    LedgerBlock commit(List<Transaction> txs) throws Exception {
        LedgerBlock proposal = propose(txs, 0);
        CommitCollector collector = new CommitCollector(ledger, proposal);
        for (KeyPair k : validatorKeys) {
            collector.add(new ValidatorSigner(k).voteOn(ledger, proposal));
        }
        LedgerBlock certified = collector.certified();
        ledger.append(certified);
        return certified;
    }

    Transaction transfer(Wallet from, Wallet to, long amount, long gasPrice) {
        return from.createTransaction(to.getAddress(), amount, gasPrice);
    }

    /** Copy of {@code b} with selected fields replaced, to build malicious blocks. */
    static LedgerBlock rebuild(LedgerBlock b, byte[] parent, long ts, String proposer, byte[] txRoot,
                               byte[] stateRoot, byte[] vsHash, List<Transaction> txs) {
        return new LedgerBlock(b.getChainId(), b.getNumber(), parent, ts, b.getRound(), proposer,
                txRoot, stateRoot, vsHash, txs, List.of());
    }
}
