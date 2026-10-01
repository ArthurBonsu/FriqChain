package org.example.app.core.block;

import org.example.app.core.crypto.Keypair.SignatureResult;
import org.example.app.core.crypto.Secp256k1;
import org.example.app.core.wallet.TransactionBuilder;
import org.example.app.core.wallet.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

/** Integrity, authentication and replay-protection properties of {@link Transaction}. */
class TransactionSigningTest {
    private static final String RECIPIENT = "0x1234567890123456789012345678901234567890";

    private KeyPair keys;
    private String sender;

    @BeforeEach
    void setUp() throws Exception {
        keys = Secp256k1.generateKeyPair();
        sender = Secp256k1.address(keys.getPublic());
    }

    private TransactionBuilder builder() {
        return new TransactionBuilder()
                .from(sender).to(RECIPIENT)
                .value(BigInteger.valueOf(100)).gasPrice(BigInteger.TEN).nonce(5);
    }

    @Test
    void signedTransactionVerifies() {
        Transaction tx = builder().privateKey(keys.getPrivate()).build();
        assertTrue(tx.isValid());
        assertTrue(tx.verifySignature());
        assertEquals(5, tx.getNonce(), "The builder's nonce must be applied");
    }

    @Test
    void unsignedTransactionDoesNotVerify() {
        Transaction tx = builder().build();
        assertFalse(tx.verifySignature());
        assertFalse(tx.isValid(), "An unsigned transaction is not valid");
    }

    @Test
    void placeholderSignatureIsRejected() {
        Transaction tx = builder().build();
        tx.setSignature(new SignatureResult(BigInteger.ONE, BigInteger.ONE));
        assertFalse(tx.verifySignature(), "The old r=1,s=1 placeholder must not authenticate anything");
        tx.setPublicKey(Secp256k1.rawPublicKey(keys.getPublic()));
        assertFalse(tx.verifySignature());
    }

    @Test
    void tamperingWithAnySignedFieldInvalidatesTheSignature() {
        Transaction original = builder().data(new byte[]{1, 2, 3}).privateKey(keys.getPrivate()).build();

        Transaction t = copyOf(original);
        t.setValue(101L);
        assertFalse(t.verifySignature(), "value");

        t = copyOf(original);
        t.setRecipient("0x9999999999999999999999999999999999999999");
        assertFalse(t.verifySignature(), "recipient");

        t = copyOf(original);
        t.setNonce(6);
        assertFalse(t.verifySignature(), "nonce");

        t = copyOf(original);
        t.setGasPrice(BigInteger.valueOf(11));
        assertFalse(t.verifySignature(), "gas price");

        t = copyOf(original);
        t.setGasLimit(30000);
        assertFalse(t.verifySignature(), "gas limit");

        t = copyOf(original);
        t.setChainId(2);
        assertFalse(t.verifySignature(), "chain id");

        assertTrue(original.verifySignature(), "the untouched transaction still verifies");
    }

    @Test
    void cannotImpersonateAnotherAddress() throws Exception {
        KeyPair attacker = Secp256k1.generateKeyPair();
        // The attacker signs a transaction that claims to come from the victim.
        Transaction tx = builder().build();
        assertThrows(IllegalStateException.class, () -> tx.sign(attacker.getPrivate()),
                "Signing with a key that does not own the sender address must be refused");

        // Even assembling the signature by hand does not work: the public key must hash to the sender.
        tx.setPublicKey(Secp256k1.rawPublicKey(attacker.getPublic()));
        tx.setSignature(Secp256k1.sign(tx.signingHash(), Secp256k1.privateScalar(attacker.getPrivate())));
        assertFalse(tx.verifySignature());
    }

    @Test
    void chainIdPreventsCrossShardReplay() {
        Transaction shardA = builder().chainId(10).privateKey(keys.getPrivate()).build();
        Transaction replay = copyOf(shardA);
        replay.setChainId(11);
        assertFalse(replay.verifySignature(), "A transaction signed for one chain id must not be valid on another");
    }

    @Test
    void hashCommitsToEveryField() {
        Transaction a = builder().build();
        assertEquals(a.getHash(), builder().build().getHash(), "Identical content has an identical hash");
        assertEquals(64, a.getHash().length());

        assertNotEquals(a.getHash(), builder().value(BigInteger.valueOf(101)).build().getHash(), "value");
        assertNotEquals(a.getHash(), builder().to("0x9999999999999999999999999999999999999999").build().getHash(), "recipient");
        assertNotEquals(a.getHash(), builder().nonce(6).build().getHash(), "nonce");
        assertNotEquals(a.getHash(), builder().data(new byte[]{1}).build().getHash(), "data");
        assertNotEquals(a.getHash(), builder().chainId(9).build().getHash(), "chain id");
    }

    @Test
    void hashReflectsLaterChangesAndSignature() {
        Transaction tx = builder().build();
        String unsigned = tx.getHash();
        tx.setValue(7L);
        assertNotEquals(unsigned, tx.getHash(), "The hash must never go stale after a change");

        String beforeSigning = tx.getHash();
        tx.sign(keys.getPrivate());
        assertNotEquals(beforeSigning, tx.getHash(), "A signed transaction has a different id than the unsigned one");
    }

    @Test
    void equalityFollowsContent() {
        Transaction a = builder().privateKey(keys.getPrivate()).build();
        Transaction b = builder().privateKey(keys.getPrivate()).build();
        assertEquals(a, b, "Deterministic signing makes identical transactions equal");
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, builder().value(BigInteger.valueOf(1)).privateKey(keys.getPrivate()).build());
    }

    @Test
    void walletTransactionsAreAuthenticated() throws Exception {
        Wallet wallet = new Wallet();
        wallet.updateBalance(1000);
        Transaction tx = wallet.createTransaction(RECIPIENT, 10, 1);
        assertTrue(tx.verifySignature());
        assertEquals(wallet.getAddress(), tx.getSender());
    }

    @Test
    void contractCreationMayOmitTheRecipient() {
        Transaction tx = new Transaction(new byte[]{0x60, 0x00});
        tx.setSender(sender);
        tx.setContractCreation(true);
        tx.setGasPrice(BigInteger.ONE);
        tx.sign(keys.getPrivate());
        assertTrue(tx.isValid());
        assertTrue(tx.verifySignature());

        Transaction plain = new Transaction(new byte[0]);
        plain.setSender(sender);
        plain.sign(keys.getPrivate());
        assertFalse(plain.isValid(), "A non-creation transaction needs a recipient");
    }

    /** Re-creates a transaction with the same field values (including signature) via the public API. */
    private static Transaction copyOf(Transaction t) {
        Transaction c = new Transaction(t.getData());
        c.setSender(t.getSender());
        c.setRecipient(t.getRecipient());
        c.setValue(t.getValue());
        c.setGasPrice(t.getGasPrice());
        c.setGasLimit(t.getGasLimit());
        c.setNonce(t.getNonce());
        c.setChainId(t.getChainId());
        c.setSignature(t.getSignature());
        c.setPublicKey(t.getPublicKey());
        return c;
    }
}
