package org.example.app.core.ledger;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Open, stake-based validator registry. Anyone can bond stake; an epoch's validator set is sealed
 * as an immutable snapshot (top {@code maxValidators} by stake that pass the admission policy) and
 * then applies to every height of that epoch, so all nodes agree on the set for any height.
 * Proven double signing slashes the validator and removes it from future snapshots.
 *
 * <p>Open staking cannot prove where a node physically runs; data-residency is enforced by the
 * pluggable {@link #admission} policy (for example a national regulator's allow-list).</p>
 */
public final class StakeRegistry implements ValidatorSetProvider {
    private final long epochLength;
    private final int maxValidators;
    private final BigInteger minStake;
    private final Predicate<ValidatorInfo> admission;
    private final int slashBasisPoints;

    private final Map<String, byte[]> keys = new TreeMap<>();
    private final Map<String, BigInteger> bonded = new TreeMap<>();
    private final Set<String> jailed = new HashSet<>();
    private final Set<String> punished = new HashSet<>();
    private final Map<Long, ValidatorSet> snapshots = new TreeMap<>();
    private BigInteger slashedTotal = BigInteger.ZERO;

    public StakeRegistry(long epochLength, int maxValidators, BigInteger minStake, int slashBasisPoints,
                         Predicate<ValidatorInfo> admission) {
        if (epochLength <= 0 || maxValidators <= 0 || minStake.signum() <= 0 || slashBasisPoints <= 0 || slashBasisPoints > 10_000) {
            throw new IllegalArgumentException("Invalid staking parameters");
        }
        this.epochLength = epochLength;
        this.maxValidators = maxValidators;
        this.minStake = minStake;
        this.slashBasisPoints = slashBasisPoints;
        this.admission = admission == null ? v -> true : admission;
    }

    public synchronized void bond(byte[] publicKey64, BigInteger amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Bond must be positive");
        }
        String address = new ValidatorInfo(publicKey64, BigInteger.ONE).getAddress();
        if (jailed.contains(address)) {
            throw new IllegalStateException("Validator is jailed");
        }
        keys.put(address, publicKey64.clone());
        bonded.merge(address, amount, BigInteger::add);
    }

    public synchronized void unbond(String address, BigInteger amount) {
        String key = Addresses.canonical(address);
        BigInteger current = bonded.getOrDefault(key, BigInteger.ZERO);
        if (amount.signum() <= 0 || amount.compareTo(current) > 0) {
            throw new IllegalArgumentException("Invalid unbond amount");
        }
        BigInteger left = current.subtract(amount);
        if (left.signum() == 0) bonded.remove(key); else bonded.put(key, left);
    }

    public synchronized BigInteger bondedOf(String address) {
        return bonded.getOrDefault(Addresses.canonical(address), BigInteger.ZERO);
    }

    public synchronized BigInteger totalSlashed() { return slashedTotal; }

    /** Slashes and jails a double signer. Returns false if the evidence is invalid or already used. */
    public synchronized boolean submitEvidence(DoubleSignEvidence evidence) {
        String address = Addresses.canonical(evidence.getValidator());
        byte[] pub = keys.get(address);
        if (pub == null || !evidence.isValid(pub) || !punished.add(evidence.id())) {
            return false;
        }
        BigInteger stake = bonded.getOrDefault(address, BigInteger.ZERO);
        BigInteger penalty = stake.multiply(BigInteger.valueOf(slashBasisPoints)).divide(BigInteger.valueOf(10_000));
        slashedTotal = slashedTotal.add(penalty);
        bonded.remove(address); // remaining stake is released; validator can no longer join
        jailed.add(address);
        return true;
    }

    /** Freezes the validator set for {@code epoch} from the current bonds. Epochs must be sealed in order. */
    public synchronized ValidatorSet sealEpoch(long epoch) {
        if (snapshots.containsKey(epoch)) {
            throw new IllegalStateException("Epoch already sealed");
        }
        if (epoch != 0 && !snapshots.containsKey(epoch - 1)) {
            throw new IllegalStateException("Previous epoch is not sealed");
        }
        List<ValidatorInfo> candidates = new ArrayList<>();
        for (Map.Entry<String, BigInteger> e : bonded.entrySet()) {
            if (e.getValue().compareTo(minStake) < 0 || jailed.contains(e.getKey())) continue;
            ValidatorInfo info = new ValidatorInfo(keys.get(e.getKey()), e.getValue());
            if (admission.test(info)) candidates.add(info);
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No eligible validators for epoch " + epoch);
        }
        candidates.sort(ValidatorSet.BY_STAKE_DESC);
        ValidatorSet set = new ValidatorSet(candidates.subList(0, Math.min(maxValidators, candidates.size())));
        snapshots.put(epoch, set);
        return set;
    }

    @Override
    public synchronized ValidatorSet validatorSetFor(long height) {
        long epoch = height / epochLength;
        ValidatorSet set = null;
        for (Map.Entry<Long, ValidatorSet> e : snapshots.entrySet()) {
            if (e.getKey() <= epoch) set = e.getValue(); else break;
        }
        if (set == null) {
            throw new IllegalStateException("No validator set sealed for height " + height);
        }
        return set;
    }
}
