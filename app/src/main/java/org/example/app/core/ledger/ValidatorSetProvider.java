package org.example.app.core.ledger;

/**
 * Supplies the validator set for a block height. It must be a pure function of committed chain
 * state, so that every honest node derives the same set for the same height.
 */
public interface ValidatorSetProvider {
    ValidatorSet validatorSetFor(long height);
}
