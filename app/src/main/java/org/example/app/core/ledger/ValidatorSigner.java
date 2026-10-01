package org.example.app.core.ledger;

import org.example.app.core.crypto.Secp256k1;

import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;

/**
 * A validator's voting key behind a sign guard: it fully re-verifies a proposal before voting and
 * never signs two different blocks for the same height. A production node must persist the guard
 * (height to block hash) before releasing a vote so a restart cannot cause a double sign.
 */
public final class ValidatorSigner {
    private final KeyPair key;
    private final String address;
    private final Map<Long, String> signed = new HashMap<>();

    public ValidatorSigner(KeyPair key) {
        this.key = key;
        this.address = Secp256k1.address(key.getPublic());
    }

    public String getAddress() {
        return address;
    }

    public synchronized CommitVote voteOn(Ledger ledger, LedgerBlock block) throws InvalidBlockException {
        if (!ledger.validatorSetForNext().contains(address)) {
            throw new InvalidBlockException("This signer is not in the validator set");
        }
        ledger.verifyProposal(block);
        String previous = signed.get(block.getNumber());
        if (previous != null && !previous.equals(block.hashHex())) {
            throw new DoubleSignException("Already voted for " + previous + " at height " + block.getNumber());
        }
        signed.put(block.getNumber(), block.hashHex());
        return CommitVote.sign(block.getChainId(), block.getNumber(), block.hash(), key);
    }
}
