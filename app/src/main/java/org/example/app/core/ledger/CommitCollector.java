package org.example.app.core.ledger;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Gathers commit votes for one proposal and produces the certified block once a quorum is reached. */
public final class CommitCollector {
    private final Ledger ledger;
    private final LedgerBlock block;
    private final ValidatorSet set;
    private final Map<String, CommitVote> votes = new LinkedHashMap<>();
    private BigInteger signedStake = BigInteger.ZERO;

    public CommitCollector(Ledger ledger, LedgerBlock block) {
        this.ledger = ledger;
        this.block = block;
        this.set = ledger.validatorSetForNext();
    }

    /** Returns true if the vote was valid and new. Invalid or duplicate votes are ignored. */
    public synchronized boolean add(CommitVote vote) {
        ValidatorInfo v = set.find(vote.getValidator());
        if (v == null || votes.containsKey(v.getAddress())) {
            return false;
        }
        if (!vote.verify(ledger.getChainId(), block.getNumber(), block.hash(), v.getPublicKey())) {
            return false;
        }
        votes.put(v.getAddress(), vote);
        signedStake = signedStake.add(v.getStake());
        return true;
    }

    public synchronized boolean hasQuorum() {
        return set.isQuorum(signedStake);
    }

    public synchronized LedgerBlock certified() {
        if (!hasQuorum()) {
            throw new IllegalStateException("No quorum yet");
        }
        return block.withCommitVotes(new ArrayList<>(votes.values()));
    }
}
