package org.example.app.core.ledger;

import org.example.app.core.block.Transaction;
import org.example.app.core.crypto.Secp256k1;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

/** Builds a proposal for the next height. Candidates that do not execute cleanly are skipped. */
public final class BlockProducer {
    private BlockProducer() {
    }

    public static LedgerBlock propose(Ledger ledger, KeyPair proposerKey, List<Transaction> candidates, int round) {
        String me = Secp256k1.address(proposerKey.getPublic());
        ValidatorInfo expected = ledger.expectedProposer(round);
        if (!expected.getAddress().equals(me)) {
            throw new IllegalStateException("Not the proposer for round " + round + "; expected " + expected.getAddress());
        }
        LedgerBlock parent = ledger.tip();
        AccountState working = ledger.stateSnapshot();
        List<Transaction> chosen = new ArrayList<>();
        long gas = 0;
        for (Transaction tx : candidates) {
            if (chosen.size() >= ledger.getConfig().getMaxTransactionsPerBlock()) {
                break;
            }
            AccountState attempt = working.copy();
            try {
                long used = ledger.getExecutor().execute(attempt, tx, me, ledger.getChainId());
                if (gas + used > ledger.getConfig().getMaxBlockGas()) {
                    continue;
                }
                gas += used;
                working = attempt;
                chosen.add(tx);
            } catch (InvalidTransactionException e) {
                // skipped: not valid against the state built so far
            }
        }
        long timestamp = Math.max(ledger.now(), parent.getTimestamp() + 1);
        return new LedgerBlock(ledger.getChainId(), parent.getNumber() + 1, parent.hash(), timestamp, round, me,
                LedgerBlock.computeTxRoot(chosen), working.root(),
                ledger.validatorSetForNext().hash(), chosen, List.of());
    }
}
