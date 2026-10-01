package org.example.app.core.ledger;

import org.example.app.core.block.Transaction;

import java.math.BigInteger;

/**
 * Plain value transfers. Every transaction must be signed by the sender, carry this shard's chain
 * id and the sender's next nonce, and the sender must be able to pay {@code value + fee}. The fee is
 * {@code gasPrice * 21000} and goes to the block proposer. Transactions with call data are refused
 * until a contract executor is registered for them.
 */
public final class TransferExecutor implements TransactionExecutor {
    public static final long TRANSFER_GAS = 21_000L;

    @Override
    public long execute(AccountState state, Transaction tx, String proposer, long chainId) throws InvalidTransactionException {
        if (tx == null) {
            throw new InvalidTransactionException("Null transaction");
        }
        String sender;
        String recipient;
        try {
            sender = Addresses.canonical(tx.getSender());
            recipient = Addresses.canonical(tx.getRecipient());
        } catch (IllegalArgumentException e) {
            throw new InvalidTransactionException("Invalid sender or recipient address");
        }
        if (!tx.verifySignature()) {
            throw new InvalidTransactionException("Invalid signature");
        }
        if (tx.getChainId() != chainId) {
            throw new InvalidTransactionException("Wrong chain id: " + tx.getChainId());
        }
        if (tx.getNonce() != state.nonceOf(sender)) {
            throw new InvalidTransactionException("Bad nonce: expected " + state.nonceOf(sender) + " but was " + tx.getNonce());
        }
        if (tx.getValue() < 0) {
            throw new InvalidTransactionException("Negative value");
        }
        if (tx.getData() != null && tx.getData().length > 0) {
            throw new InvalidTransactionException("Contract calls are not supported by the transfer executor");
        }
        if (tx.getGasLimit() < TRANSFER_GAS) {
            throw new InvalidTransactionException("Gas limit below the transfer cost");
        }
        if (tx.getGasPrice() == null || tx.getGasPrice().signum() < 0) {
            throw new InvalidTransactionException("Invalid gas price");
        }

        BigInteger fee = tx.getGasPrice().multiply(BigInteger.valueOf(TRANSFER_GAS));
        BigInteger total = BigInteger.valueOf(tx.getValue()).add(fee);
        if (state.balanceOf(sender).compareTo(total) < 0) {
            throw new InvalidTransactionException("Insufficient balance");
        }

        state.debit(sender, total);
        state.credit(recipient, BigInteger.valueOf(tx.getValue()));
        state.credit(proposer, fee);
        state.incrementNonce(sender);
        return TRANSFER_GAS;
    }
}
