package org.example.app.core.ledger;

import org.example.app.core.crypto.Secp256k1;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The validators that may vote on a block, with their stake. Voting power is proportional to stake
 * and a block is final once validators holding MORE than two thirds of the total stake have signed
 * it (the classic BFT bound: safe while less than one third of the stake is faulty or malicious).
 * The set is immutable and has a deterministic hash, which every block header commits to.
 */
public final class ValidatorSet {
    private final List<ValidatorInfo> validators;
    private final Map<String, ValidatorInfo> byAddress;
    private final BigInteger totalStake;
    private final byte[] hash;

    public ValidatorSet(Collection<ValidatorInfo> validators) {
        if (validators == null || validators.isEmpty()) {
            throw new IllegalArgumentException("A validator set needs at least one validator");
        }
        Map<String, ValidatorInfo> sorted = new TreeMap<>();
        BigInteger total = BigInteger.ZERO;
        for (ValidatorInfo v : validators) {
            if (sorted.put(v.getAddress(), v) != null) {
                throw new IllegalArgumentException("Duplicate validator: " + v.getAddress());
            }
            total = total.add(v.getStake());
        }
        this.byAddress = Collections.unmodifiableMap(sorted);
        this.validators = Collections.unmodifiableList(new ArrayList<>(sorted.values()));
        this.totalStake = total;

        byte[][] parts = new byte[this.validators.size() + 1][];
        parts[0] = Bytes.u32(this.validators.size());
        for (int i = 0; i < this.validators.size(); i++) {
            parts[i + 1] = this.validators.get(i).encode();
        }
        this.hash = Secp256k1.keccak256(Bytes.concat(parts));
    }

    public List<ValidatorInfo> getValidators() {
        return validators;
    }

    public int size() {
        return validators.size();
    }

    public BigInteger getTotalStake() {
        return totalStake;
    }

    public boolean contains(String address) {
        return find(address) != null;
    }

    public ValidatorInfo find(String address) {
        if (address == null || !Addresses.isValid(address)) {
            return null;
        }
        return byAddress.get(Addresses.canonical(address));
    }

    public byte[] hash() {
        return hash.clone();
    }

    /** True when {@code signedStake} is strictly more than two thirds of the total stake. */
    public boolean isQuorum(BigInteger signedStake) {
        return signedStake.multiply(BigInteger.valueOf(3)).compareTo(totalStake.multiply(BigInteger.TWO)) > 0;
    }

    /**
     * Deterministic stake-weighted proposer for a height and round. The choice depends only on
     * public data, so every node computes the same proposer and a proposal from anyone else is
     * rejected; a new round (after a timeout) selects a different validator.
     */
    public ValidatorInfo proposerFor(long height, int round) {
        byte[] seed = Secp256k1.keccak256(Bytes.concat(hash, Bytes.u64(height), Bytes.u32(round)));
        BigInteger ticket = new BigInteger(1, seed).mod(totalStake);
        BigInteger cumulative = BigInteger.ZERO;
        for (ValidatorInfo v : validators) {
            cumulative = cumulative.add(v.getStake());
            if (ticket.compareTo(cumulative) < 0) {
                return v;
            }
        }
        throw new IllegalStateException("Unreachable: ticket below total stake");
    }

    /** Orders validators by descending stake, then address; used when trimming to a maximum size. */
    static final Comparator<ValidatorInfo> BY_STAKE_DESC =
            Comparator.comparing(ValidatorInfo::getStake).reversed().thenComparing(ValidatorInfo::getAddress);
}
