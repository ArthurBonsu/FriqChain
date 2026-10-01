package org.example.app.core.ledger;

import org.example.app.core.block.Transaction;

/**
 * Applies one transaction to a state. Implementations must be deterministic: every node must reach
 * exactly the same state for the same transaction. Contract calls, token transfers and payment
 * operations plug in here.
 */
public interface TransactionExecutor {
    /**
     * @param state    working copy that the executor may mutate
     * @param proposer address of the block proposer (receives the fee)
     * @return gas used by the transaction
     * @throws InvalidTransactionException if the transaction is invalid; the caller discards the state copy
     */
    long execute(AccountState state, Transaction tx, String proposer, long chainId) throws InvalidTransactionException;
}
