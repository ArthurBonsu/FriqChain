package org.example.app.core.ledger;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Account balances and nonces with a deterministic Merkle state root. Mutation is package-private:
 * only the ledger's executors change state, always on a private copy that is committed only after
 * the whole block has verified.
 */
public final class AccountState {

    private static final class Account {
        BigInteger balance = BigInteger.ZERO;
        long nonce = 0;

        Account copy() {
            Account c = new Account();
            c.balance = balance;
            c.nonce = nonce;
            return c;
        }

        boolean isEmpty() {
            return balance.signum() == 0 && nonce == 0;
        }
    }

    private final TreeMap<String, Account> accounts = new TreeMap<>();

    public AccountState() {
    }

    /** Builds the initial state from a genesis allocation (address to balance). */
    public static AccountState genesis(Map<String, BigInteger> allocation) {
        AccountState state = new AccountState();
        for (Map.Entry<String, BigInteger> e : allocation.entrySet()) {
            if (e.getValue().signum() < 0) {
                throw new IllegalArgumentException("Negative genesis balance");
            }
            state.credit(e.getKey(), e.getValue());
        }
        return state;
    }

    public AccountState copy() {
        AccountState c = new AccountState();
        for (Map.Entry<String, Account> e : accounts.entrySet()) {
            c.accounts.put(e.getKey(), e.getValue().copy());
        }
        return c;
    }

    public BigInteger balanceOf(String address) {
        Account a = accounts.get(Addresses.canonical(address));
        return a == null ? BigInteger.ZERO : a.balance;
    }

    public long nonceOf(String address) {
        Account a = accounts.get(Addresses.canonical(address));
        return a == null ? 0 : a.nonce;
    }

    public BigInteger totalSupply() {
        BigInteger total = BigInteger.ZERO;
        for (Account a : accounts.values()) {
            total = total.add(a.balance);
        }
        return total;
    }

    void credit(String address, BigInteger amount) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Negative credit");
        }
        Account a = accounts.computeIfAbsent(Addresses.canonical(address), k -> new Account());
        a.balance = a.balance.add(amount);
    }

    void debit(String address, BigInteger amount) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Negative debit");
        }
        String key = Addresses.canonical(address);
        Account a = accounts.get(key);
        BigInteger current = a == null ? BigInteger.ZERO : a.balance;
        if (current.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }
        if (a != null) {
            a.balance = current.subtract(amount);
        }
    }

    void incrementNonce(String address) {
        accounts.computeIfAbsent(Addresses.canonical(address), k -> new Account()).nonce++;
    }

    /** Merkle root over (address, balance, nonce) of every non-empty account, ordered by address. */
    public byte[] root() {
        List<byte[]> leaves = new ArrayList<>();
        for (Map.Entry<String, Account> e : accounts.entrySet()) {
            Account a = e.getValue();
            if (a.isEmpty()) {
                continue;
            }
            leaves.add(Bytes.concat(Addresses.bytes(e.getKey()), Bytes.unsigned(a.balance, 32), Bytes.u64(a.nonce)));
        }
        return MerkleTree.root(leaves);
    }
}
