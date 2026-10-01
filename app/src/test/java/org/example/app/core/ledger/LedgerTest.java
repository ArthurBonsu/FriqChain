package org.example.app.core.ledger;

import org.example.app.core.block.Transaction;
import org.example.app.core.crypto.Secp256k1;
import org.example.app.core.wallet.Wallet;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LedgerTest {

    private static void rejects(Ledger ledger, LedgerBlock block, String fragment) {
        InvalidBlockException e = assertThrows(InvalidBlockException.class, () -> ledger.append(block));
        assertTrue(e.getMessage().contains(fragment), "expected '" + fragment + "' but was: " + e.getMessage());
    }

    @Test
    void genesisIsDeterministicAndEmpty() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        assertEquals(0L, f.ledger.height());
        LedgerBlock g = f.ledger.tip();
        assertEquals(0, g.getTransactions().size());
        assertArrayEquals(f.ledger.stateRoot(), g.getStateRoot());
        assertArrayEquals(f.registry.validatorSetFor(0).hash(), g.getValidatorSetHash());
    }

    @Test
    void endToEndTransferWithFourValidators() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        Transaction tx = f.transfer(f.alice, f.bob, 5_000, 2);
        LedgerBlock block = f.commit(List.of(tx));

        assertEquals(1L, f.ledger.height());
        assertEquals(4, block.getCommitVotes().size());
        long fee = 2L * TransferExecutor.TRANSFER_GAS;
        assertEquals(LedgerFixture.FUNDS.subtract(BigInteger.valueOf(5_000 + fee)), f.ledger.balanceOf(f.alice.getAddress()));
        assertEquals(LedgerFixture.FUNDS.add(BigInteger.valueOf(5_000)), f.ledger.balanceOf(f.bob.getAddress()));
        assertEquals(BigInteger.valueOf(fee), f.ledger.balanceOf(block.getProposer()));
        assertEquals(1L, f.ledger.nonceOf(f.alice.getAddress()));
        assertEquals(LedgerFixture.FUNDS.multiply(BigInteger.TWO), f.ledger.stateSnapshot().totalSupply());
        assertSame(block, f.ledger.blockByNumber(1));
        assertSame(block, f.ledger.blockByHash(block.hashHex()));
    }

    @Test
    void chainGrowsAcrossManyHeights() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        for (int i = 0; i < 12; i++) {
            f.commit(List.of(f.transfer(f.alice, f.bob, 10, 1)));
        }
        assertEquals(12L, f.ledger.height());
        assertEquals(12L, f.ledger.nonceOf(f.alice.getAddress()));
        for (long n = 1; n <= 12; n++) {
            assertArrayEquals(f.ledger.blockByNumber(n - 1).hash(), f.ledger.blockByNumber(n).getParentHash());
        }
    }

    @Test
    void threeOfFourEqualStakeIsQuorumButTwoIsNot() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        LedgerBlock proposal = f.propose(List.of(f.transfer(f.alice, f.bob, 1, 1)), 0);
        rejects(f.ledger, f.certify(proposal, 2), "quorum");
        assertEquals(0L, f.ledger.height());
        f.ledger.append(f.certify(proposal, 3));
        assertEquals(1L, f.ledger.height());
    }

    @Test
    void duplicateVotesCountOnce() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        LedgerBlock proposal = f.propose(List.of(f.transfer(f.alice, f.bob, 1, 1)), 0);
        CommitVote one = f.votes(proposal, 1).get(0);
        rejects(f.ledger, proposal.withCommitVotes(List.of(one, one, one, one)), "quorum");
    }

    @Test
    void voteFromOutsiderIsRejected() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        LedgerBlock proposal = f.propose(List.of(f.transfer(f.alice, f.bob, 1, 1)), 0);
        List<CommitVote> votes = new ArrayList<>(f.votes(proposal, 3));
        votes.add(CommitVote.sign(1, 1, proposal.hash(), Secp256k1.generateKeyPair()));
        rejects(f.ledger, proposal.withCommitVotes(votes), "non-validator");
    }

    @Test
    void voteForAnotherBlockDoesNotCount() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        LedgerBlock proposal = f.propose(List.of(f.transfer(f.alice, f.bob, 1, 1)), 0);
        List<CommitVote> votes = new ArrayList<>(f.votes(proposal, 2));
        votes.add(CommitVote.sign(1, 1, new byte[32], f.validatorKeys.get(2)));
        rejects(f.ledger, proposal.withCommitVotes(votes), "Invalid commit signature");
    }

    @Test
    void voteForAnotherHeightOrChainDoesNotVerify() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        LedgerBlock proposal = f.propose(List.of(f.transfer(f.alice, f.bob, 1, 1)), 0);
        List<CommitVote> votes = new ArrayList<>(f.votes(proposal, 3));
        votes.set(0, CommitVote.sign(1, 99, proposal.hash(), f.validatorKeys.get(0)));
        rejects(f.ledger, proposal.withCommitVotes(votes), "Invalid commit signature");
        votes.set(0, CommitVote.sign(2, 1, proposal.hash(), f.validatorKeys.get(0)));
        rejects(f.ledger, proposal.withCommitVotes(votes), "Invalid commit signature");
    }

    @Test
    void rejectsTamperedTransactionRoot() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        Transaction tx = f.transfer(f.alice, f.bob, 1, 1);
        LedgerBlock p = f.propose(List.of(tx), 0);
        LedgerBlock bad = LedgerFixture.rebuild(p, p.getParentHash(), p.getTimestamp(), p.getProposer(),
                new byte[32], p.getStateRoot(), p.getValidatorSetHash(), p.getTransactions());
        rejects(f.ledger, bad, "Transaction root");
    }

    @Test
    void rejectsWrongStateRoot() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        LedgerBlock p = f.propose(List.of(f.transfer(f.alice, f.bob, 1, 1)), 0);
        LedgerBlock bad = LedgerFixture.rebuild(p, p.getParentHash(), p.getTimestamp(), p.getProposer(),
                p.getTxRoot(), new byte[32], p.getValidatorSetHash(), p.getTransactions());
        rejects(f.ledger, bad, "State root");
    }

    @Test
    void rejectsWrongParent() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        LedgerBlock p = f.propose(List.of(f.transfer(f.alice, f.bob, 1, 1)), 0);
        LedgerBlock bad = LedgerFixture.rebuild(p, new byte[32], p.getTimestamp(), p.getProposer(),
                p.getTxRoot(), p.getStateRoot(), p.getValidatorSetHash(), p.getTransactions());
        rejects(f.ledger, bad, "Parent");
    }

    @Test
    void rejectsWrongValidatorSetHash() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        LedgerBlock p = f.propose(List.of(f.transfer(f.alice, f.bob, 1, 1)), 0);
        LedgerBlock bad = LedgerFixture.rebuild(p, p.getParentHash(), p.getTimestamp(), p.getProposer(),
                p.getTxRoot(), p.getStateRoot(), new byte[32], p.getTransactions());
        rejects(f.ledger, bad, "Validator set hash");
    }

    @Test
    void rejectsNonDesignatedProposer() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        LedgerBlock p = f.propose(List.of(f.transfer(f.alice, f.bob, 1, 1)), 0);
        String other = null;
        for (ValidatorInfo v : f.registry.validatorSetFor(1).getValidators()) {
            if (!v.getAddress().equals(p.getProposer())) { other = v.getAddress(); break; }
        }
        LedgerBlock bad = LedgerFixture.rebuild(p, p.getParentHash(), p.getTimestamp(), other,
                p.getTxRoot(), p.getStateRoot(), p.getValidatorSetHash(), p.getTransactions());
        rejects(f.ledger, bad, "designated proposer");
    }

    @Test
    void rejectsBadTimestamps() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        LedgerBlock p = f.propose(List.of(f.transfer(f.alice, f.bob, 1, 1)), 0);
        LedgerBlock old = LedgerFixture.rebuild(p, p.getParentHash(), LedgerFixture.GENESIS_TIME, p.getProposer(),
                p.getTxRoot(), p.getStateRoot(), p.getValidatorSetHash(), p.getTransactions());
        rejects(f.ledger, old, "later than the parent");
        LedgerBlock future = LedgerFixture.rebuild(p, p.getParentHash(), f.clock.get() + 3_600_000L, p.getProposer(),
                p.getTxRoot(), p.getStateRoot(), p.getValidatorSetHash(), p.getTransactions());
        rejects(f.ledger, future, "future");
    }

    @Test
    void rejectsForgedTransactionInsideBlock() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        Transaction tx = f.transfer(f.alice, f.bob, 1, 1);
        tx.setValue(900_000L); // changed after signing
        List<Transaction> txs = List.of(tx);
        LedgerBlock p = f.propose(List.of(), 0);
        LedgerBlock bad = LedgerFixture.rebuild(p, p.getParentHash(), p.getTimestamp(), p.getProposer(),
                LedgerBlock.computeTxRoot(txs), p.getStateRoot(), p.getValidatorSetHash(), txs);
        rejects(f.ledger, bad, "Invalid signature");
    }

    @Test
    void rejectsReplayedTransaction() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        Transaction tx = f.transfer(f.alice, f.bob, 1, 1);
        f.commit(List.of(tx));
        List<Transaction> txs = List.of(tx);
        LedgerBlock p = f.propose(List.of(), 0);
        LedgerBlock bad = LedgerFixture.rebuild(p, p.getParentHash(), p.getTimestamp(), p.getProposer(),
                LedgerBlock.computeTxRoot(txs), p.getStateRoot(), p.getValidatorSetHash(), txs);
        rejects(f.ledger, bad, "Bad nonce");
    }

    @Test
    void rejectsDuplicateTransactionInOneBlock() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        Transaction tx = f.transfer(f.alice, f.bob, 1, 1);
        List<Transaction> txs = List.of(tx, tx);
        LedgerBlock p = f.propose(List.of(), 0);
        LedgerBlock bad = LedgerFixture.rebuild(p, p.getParentHash(), p.getTimestamp(), p.getProposer(),
                LedgerBlock.computeTxRoot(txs), p.getStateRoot(), p.getValidatorSetHash(), txs);
        rejects(f.ledger, bad, "Duplicate");
    }

    @Test
    void transactionSignedForAnotherChainIsRefused() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        Transaction tx = f.transfer(f.alice, f.bob, 1, 1); // chain id 1
        // a sibling shard with a different chain id must not accept it
        LedgerFixture other = LedgerFixture.create(4);
        Ledger shard2 = Ledger.create(2, AccountState.genesis(java.util.Map.of(f.alice.getAddress(), LedgerFixture.FUNDS)),
                other.registry, LedgerConfig.defaults(LedgerFixture.GENESIS_TIME), new TransferExecutor(), other.clock::get);
        other.clock.addAndGet(1_000);
        LedgerBlock p = BlockProducer.propose(shard2, other.keysByAddress.get(shard2.expectedProposer(0).getAddress()), List.of(tx), 0);
        assertEquals(0, p.getTransactions().size(), "producer must skip the foreign-chain transaction");
        List<Transaction> txs = List.of(tx);
        LedgerBlock bad = LedgerFixture.rebuild(p, p.getParentHash(), p.getTimestamp(), p.getProposer(),
                LedgerBlock.computeTxRoot(txs), p.getStateRoot(), p.getValidatorSetHash(), txs);
        rejects(shard2, bad, "Wrong chain id");
    }

    @Test
    void producerSkipsUnaffordableAndKeepsGoodTransactions() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        Wallet poor = new Wallet();
        poor.updateBalance(1_000_000L);
        Transaction broke = poor.createTransaction(f.bob.getAddress(), 5, 1); // no on-chain funds
        Transaction good = f.transfer(f.alice, f.bob, 7, 1);
        LedgerBlock block = f.commit(List.of(broke, good));
        assertEquals(1, block.getTransactions().size());
        assertEquals(good.getHash(), block.getTransactions().get(0).getHash());
    }

    @Test
    void sameSenderNoncesChainWithinOneBlock() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        List<Transaction> txs = List.of(f.transfer(f.alice, f.bob, 1, 1), f.transfer(f.alice, f.bob, 2, 1), f.transfer(f.alice, f.bob, 3, 1));
        LedgerBlock block = f.commit(txs);
        assertEquals(3, block.getTransactions().size());
        assertEquals(3L, f.ledger.nonceOf(f.alice.getAddress()));
    }

    @Test
    void signerRefusesToVoteForTwoBlocksAtTheSameHeight() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        Transaction tx = f.transfer(f.alice, f.bob, 1, 1);
        LedgerBlock round0 = f.propose(List.of(tx), 0);
        LedgerBlock round1 = f.propose(List.of(tx), 1);
        ValidatorSigner signer = new ValidatorSigner(f.validatorKeys.get(0));
        signer.voteOn(f.ledger, round0);
        signer.voteOn(f.ledger, round0); // the same block again is fine
        assertThrows(DoubleSignException.class, () -> signer.voteOn(f.ledger, round1));
    }

    @Test
    void signerRefusesInvalidProposalsAndOutsiders() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        LedgerBlock p = f.propose(List.of(f.transfer(f.alice, f.bob, 1, 1)), 0);
        LedgerBlock bad = LedgerFixture.rebuild(p, p.getParentHash(), p.getTimestamp(), p.getProposer(),
                p.getTxRoot(), new byte[32], p.getValidatorSetHash(), p.getTransactions());
        assertThrows(InvalidBlockException.class, () -> new ValidatorSigner(f.validatorKeys.get(0)).voteOn(f.ledger, bad));
        assertThrows(InvalidBlockException.class, () -> new ValidatorSigner(Secp256k1.generateKeyPair()).voteOn(f.ledger, p));
    }

    @Test
    void onlyTheDesignatedProposerCanPropose() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        String expected = f.ledger.expectedProposer(0).getAddress();
        for (java.security.KeyPair k : f.validatorKeys) {
            if (!Secp256k1.address(k.getPublic()).equals(expected)) {
                assertThrows(IllegalStateException.class, () -> BlockProducer.propose(f.ledger, k, List.of(), 0));
                return;
            }
        }
    }

    @Test
    void transactionInclusionProofVerifiesAgainstBlockRoot() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        List<Transaction> txs = new ArrayList<>();
        for (int i = 1; i <= 5; i++) txs.add(f.transfer(f.alice, f.bob, i, 1));
        LedgerBlock block = f.commit(txs);
        for (int i = 0; i < 5; i++) {
            List<byte[]> proof = block.transactionProof(i);
            assertTrue(MerkleTree.verify(block.getTransactions().get(i).toBytes(), i, 5, proof, block.getTxRoot()));
            assertFalse(MerkleTree.verify(block.getTransactions().get((i + 1) % 5).toBytes(), i, 5, proof, block.getTxRoot()));
        }
    }

    @Test
    void commitVotesDoNotChangeTheBlockHash() throws Exception {
        LedgerFixture f = LedgerFixture.create(4);
        LedgerBlock p = f.propose(List.of(f.transfer(f.alice, f.bob, 1, 1)), 0);
        assertArrayEquals(p.hash(), f.certify(p, 4).hash());
    }
}
