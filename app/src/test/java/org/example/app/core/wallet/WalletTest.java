package org.example.app.core.wallet;

import org.example.app.core.block.Transaction;
import org.example.app.core.crypto.Secp256k1;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wallet, key and keystore behaviour. Every wallet test runs against its own temporary directory,
 * so nothing is written to the user's real wallet folder.
 */
class WalletTest {
    private static final String PASSWORD = "testPassword123";
    private static final String RECIPIENT = "0x1234567890123456789012345678901234567890";
    // A low scrypt cost keeps the suite fast; production uses the default cost.
    private static final int TEST_SCRYPT_N = 1 << 10;

    private Path directory;
    private WalletStorage storage;
    private Wallet wallet;

    @BeforeEach
    void setUp() throws Exception {
        directory = Files.createTempDirectory("tinc-wallet-test");
        storage = new WalletStorage(directory, TEST_SCRYPT_N);
        wallet = new Wallet();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (directory != null && Files.exists(directory)) {
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    // ------------------------------------------------------------------ keys and addresses

    @Test
    void testWalletCreation() {
        String address = wallet.getAddress();
        assertNotNull(address, "Wallet should have an address");
        assertTrue(address.matches("0x[0-9a-f]{40}"), "Address must be 0x + 40 lowercase hex characters: " + address);
        assertEquals(0, wallet.getBalance(), "New wallet should have zero balance");
        assertTrue(wallet.getTransactions().isEmpty(), "New wallet should have no transactions");
    }

    @Test
    void testEveryWalletHasAUniqueAddress() throws Exception {
        Set<String> addresses = new HashSet<>();
        for (int i = 0; i < 25; i++) {
            addresses.add(new Wallet().getAddress());
        }
        assertEquals(25, addresses.size(), "Distinct wallets must never share an address");
    }

    @Test
    void testAddressIsDerivedFromThePublicKey() {
        byte[] hash = Secp256k1.keccak256(wallet.getPublicKeyBytes());
        String expected = "0x" + Secp256k1.toHex(java.util.Arrays.copyOfRange(hash, 12, 32));
        assertEquals(expected, wallet.getAddress());
    }

    @Test
    void testKeyUtilsAddressGeneration() throws Exception {
        KeyPair keyPair = KeyUtils.generateKeyPair();
        String address = KeyUtils.getAddress(keyPair.getPublic());
        assertTrue(address.matches("0x[0-9a-f]{40}"), "Address format");
        assertNotEquals("0x" + "0".repeat(40), address, "Address must not be the zero placeholder");
        assertNotEquals(address, KeyUtils.getAddress(KeyUtils.generateKeyPair().getPublic()));
    }

    @Test
    void testDigitalSignature() throws Exception {
        KeyPair keyPair = KeyUtils.generateKeyPair();
        byte[] message = "Test message for signing".getBytes(StandardCharsets.UTF_8);

        byte[] signature = KeyUtils.sign(message, keyPair.getPrivate());
        assertTrue(KeyUtils.verifySignature(message, signature, keyPair.getPublic()), "Signature should be valid");

        byte[] other = "Another message".getBytes(StandardCharsets.UTF_8);
        assertFalse(KeyUtils.verifySignature(other, signature, keyPair.getPublic()), "Signature must not verify for a different message");
        assertFalse(KeyUtils.verifySignature(message, signature, KeyUtils.generateKeyPair().getPublic()), "Signature must not verify for a different key");
        assertFalse(KeyUtils.verifySignature(message, new byte[]{1, 2, 3}, keyPair.getPublic()), "Garbage signature must be rejected, not throw");
    }

    // ------------------------------------------------------------------ transaction building

    @Test
    void testTransactionBuilderValidation() {
        TransactionBuilder builder = new TransactionBuilder();

        Exception fromException = assertThrows(IllegalStateException.class, builder::build);
        assertEquals("From address is required", fromException.getMessage());

        builder.from("0x1111111111111111111111111111111111111111");
        Exception toException = assertThrows(IllegalStateException.class, builder::build);
        assertEquals("To address is required", toException.getMessage());

        builder.to("0x2222222222222222222222222222222222222222");
        assertNotNull(builder.build(), "A complete builder should build");

        assertThrows(IllegalStateException.class, () -> builder.value(BigInteger.valueOf(-1)).build(), "Negative value");
        assertThrows(IllegalStateException.class,
                () -> builder.value(BigInteger.ONE.shiftLeft(80)).build(), "Value beyond long range must not be truncated");
    }

    @Test
    void testInsufficientFunds() {
        assertEquals(0, wallet.getBalance());
        Exception exception = assertThrows(IllegalStateException.class,
                () -> wallet.createTransaction(RECIPIENT, 100, 10));
        assertEquals("Insufficient funds", exception.getMessage());
        assertEquals(0, wallet.getNextNonce(), "A failed transaction must not consume a nonce");
        assertTrue(wallet.getTransactions().isEmpty());
    }

    @Test
    void testTransactionBuilding() {
        wallet.updateBalance(1000);
        Transaction tx = wallet.createTransaction(RECIPIENT, 100, 10);

        assertEquals(wallet.getAddress(), tx.getSender());
        assertEquals(RECIPIENT, tx.getRecipient());
        assertEquals(100L, tx.getValue().longValue());
        assertTrue(tx.isValid(), "Wallet transactions must be structurally valid");
        assertTrue(tx.verifySignature(), "Wallet transactions must carry a valid signature");
        assertEquals(1, wallet.getTransactions().size());
        assertSame(tx, wallet.getTransaction(tx.getHash()));
    }

    @Test
    void testNonceIncrement() {
        wallet.updateBalance(10_000);
        for (int i = 0; i < 3; i++) {
            Transaction tx = wallet.createTransaction(RECIPIENT, 100 + i * 100L, 10);
            assertEquals(i, tx.getNonce(), "Nonces must be sequential starting at 0");
        }
        assertEquals(3, wallet.getTransactions().size(), "Every transaction must be recorded");
        assertEquals(3, wallet.getNextNonce());
    }

    @Test
    void testIdenticalTransfersGetDistinctHashes() {
        wallet.updateBalance(10_000);
        Transaction first = wallet.createTransaction(RECIPIENT, 100, 10);
        Transaction second = wallet.createTransaction(RECIPIENT, 100, 10);
        assertNotEquals(first.getHash(), second.getHash(), "The nonce must make identical transfers distinct");
    }

    @Test
    void testTokenTransferEncodingIsStrict() {
        wallet.updateBalance(10_000);
        Wallet other = newWallet();

        Transaction tx = wallet.createTokenTransferTransaction("0xabc0000000000000000000000000000000000001", other.getAddress(), 50_000L, 10);
        byte[] data = tx.getData();
        assertEquals(68, data.length);
        assertArrayEquals(new byte[]{(byte) 0xa9, 0x05, (byte) 0x9c, (byte) 0xbb}, java.util.Arrays.copyOfRange(data, 0, 4));
        assertArrayEquals(Secp256k1.fromHex(other.getAddress()), java.util.Arrays.copyOfRange(data, 16, 36));
        assertEquals(BigInteger.valueOf(50_000L), new BigInteger(1, java.util.Arrays.copyOfRange(data, 36, 68)));
        assertTrue(tx.verifySignature(), "Token transfers must be signed");

        // Malformed recipients are refused instead of silently becoming the zero address.
        assertThrows(IllegalArgumentException.class, () -> wallet.createTokenTransferTransaction("0xabc", "0x1234", 1L, 10));
        assertThrows(IllegalArgumentException.class, () -> wallet.createTokenTransferTransaction("0xabc", "not-hex", 1L, 10));
        assertThrows(IllegalArgumentException.class, () -> wallet.createTokenTransferTransaction("0xabc", RECIPIENT, -1L, 10));

        // Full 256-bit amounts are encoded without truncation.
        BigInteger huge = BigInteger.ONE.shiftLeft(200).add(BigInteger.valueOf(7));
        byte[] big = Wallet.encodeTokenTransfer(RECIPIENT, huge);
        assertEquals(huge, new BigInteger(1, java.util.Arrays.copyOfRange(big, 36, 68)));
        assertThrows(IllegalArgumentException.class, () -> Wallet.encodeTokenTransfer(RECIPIENT, BigInteger.ONE.shiftLeft(256)));
    }

    @Test
    void testContractTransactionsAreSignedAndConsumeExactlyOneNonce() {
        wallet.updateBalance(10_000);
        Transaction tx = wallet.createContractTransaction("0xabc0000000000000000000000000000000000001", new byte[]{1, 2, 3}, 10);
        assertTrue(tx.verifySignature(), "Contract calls must be signed");
        assertEquals(1, wallet.getNextNonce());
        assertEquals(1, wallet.getTransactions().size());
    }

    // ------------------------------------------------------------------ keystore

    @Test
    void testWalletStorageSaveAndLoad() throws Exception {
        wallet.updateBalance(500);
        wallet.createTransaction(RECIPIENT, 100, 10); // nonce is now 1
        wallet.save(storage, "alice", PASSWORD);

        assertTrue(storage.walletExists("alice"));
        Wallet restored = Wallet.load(storage, "alice", PASSWORD);

        assertEquals(wallet.getAddress(), restored.getAddress(), "Restored wallet must have the same address");
        assertEquals(1, restored.getNextNonce(), "Nonce must be restored");
        restored.updateBalance(500);
        Transaction tx = restored.createTransaction(RECIPIENT, 100, 10);
        assertTrue(tx.verifySignature(), "Restored wallet must be able to sign");
        assertEquals(wallet.getAddress(), tx.getSender());
    }

    @Test
    void testWalletStorageSecurity() throws Exception {
        wallet.save(storage, "secure", PASSWORD);

        // Wrong password: rejected, never returns key material.
        Exception wrong = assertThrows(Exception.class, () -> Wallet.load(storage, "secure", "wrong-password"));
        assertTrue(wrong.getMessage().contains("Invalid password"), "Wrong password must be reported: " + wrong.getMessage());

        // The private key must not appear in the file, in hex or as raw bytes.
        Path file = directory.resolve("secure.wallet");
        String content = Files.readString(file, StandardCharsets.ISO_8859_1);
        String keyHex = Secp256k1.toHex(Secp256k1.toUnsigned32(Secp256k1.privateScalar(privateKeyOf(wallet))));
        assertFalse(content.contains(keyHex), "Private key must not be stored in the clear");
        assertFalse(content.toLowerCase().contains("password_hash"), "No weak password hash may be stored");

        // Tampering with the ciphertext or the stored address is detected.
        String original = Files.readString(file);
        String ciphertextLine = original.lines().filter(l -> l.startsWith("ciphertext=")).findFirst().orElseThrow();
        String flipped = ciphertextLine.substring(0, ciphertextLine.length() - 1)
                + (ciphertextLine.endsWith("0") ? "1" : "0");
        Files.writeString(file, original.replace(ciphertextLine, flipped));
        assertThrows(Exception.class, () -> Wallet.load(storage, "secure", PASSWORD), "Tampered ciphertext must be rejected");

        Files.writeString(file, original.replace(wallet.getAddress(), newWallet().getAddress()));
        assertThrows(Exception.class, () -> Wallet.load(storage, "secure", PASSWORD), "Tampered address must be rejected");

        // The untouched file still loads.
        Files.writeString(file, original);
        assertEquals(wallet.getAddress(), Wallet.load(storage, "secure", PASSWORD).getAddress());
    }

    @Test
    void testWalletStorageRejectsUnsafeIdsAndEmptyPasswords() throws Exception {
        assertThrows(Exception.class, () -> wallet.save(storage, "../escape", PASSWORD));
        assertThrows(Exception.class, () -> wallet.save(storage, "a/b", PASSWORD));
        assertThrows(Exception.class, () -> wallet.save(storage, "ok", ""));
        assertFalse(storage.walletExists("../escape"));
        assertEquals(0, storage.listWallets().length);
    }

    @Test
    void testWalletStorageListWallets() throws Exception {
        for (int i = 0; i < 3; i++) {
            newWallet().save(storage, "wallet-" + i, PASSWORD);
        }
        String[] wallets = storage.listWallets();
        assertEquals(3, wallets.length, "Should list exactly the wallets that were saved");
        assertEquals(java.util.List.of("wallet-0", "wallet-1", "wallet-2"), java.util.Arrays.asList(wallets));

        storage.deleteWallet("wallet-1");
        assertEquals(2, storage.listWallets().length);
        assertThrows(Exception.class, () -> storage.deleteWallet("wallet-1"), "Deleting a missing wallet must fail");
    }

    @Test
    void testWalletStorageKeepsBalanceCacheAndMetadataOnlyWallets() throws Exception {
        WalletStorage.WalletData data = new WalletStorage.WalletData();
        data.address = wallet.getAddress();
        data.lastNonce = 7;
        data.balances.put("TINC", BigInteger.valueOf(42));
        storage.saveWallet("watch-only", data, PASSWORD);

        WalletStorage.WalletData loaded = storage.loadWallet("watch-only", PASSWORD);
        assertEquals(wallet.getAddress(), loaded.address);
        assertEquals(7, loaded.lastNonce);
        assertEquals(BigInteger.valueOf(42), loaded.balances.get("TINC"));
        assertNull(loaded.privateKey, "A metadata-only wallet has no private key");
        assertThrows(Exception.class, () -> storage.loadWallet("watch-only", "wrong"));
        assertThrows(IllegalStateException.class, () -> Wallet.load(storage, "watch-only", PASSWORD));
    }

    // ------------------------------------------------------------------ helpers

    private static Wallet newWallet() {
        try {
            return new Wallet();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static java.security.PrivateKey privateKeyOf(Wallet wallet) throws Exception {
        java.lang.reflect.Field field = Wallet.class.getDeclaredField("keyPair");
        field.setAccessible(true);
        return ((KeyPair) field.get(wallet)).getPrivate();
    }
}
