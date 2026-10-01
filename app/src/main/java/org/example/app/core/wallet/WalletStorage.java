package org.example.app.core.wallet;

import org.bouncycastle.crypto.generators.SCrypt;
import org.example.app.core.crypto.Secp256k1;

import javax.crypto.Cipher;
import javax.crypto.AEADBadTagException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Encrypted on-disk wallet keystore.
 *
 * <p>Each wallet is one {@code <id>.wallet} file. The private key is never written in the clear:
 * it is encrypted with AES-256-GCM under a key derived from the password with scrypt
 * (N=2^15, r=8, p=1 by default, random 16-byte salt, random 12-byte IV). GCM authenticates the
 * ciphertext and the wallet address, so a wrong password or any tampering is detected on load.
 * Files are written atomically and, where the file system supports it, readable only by the owner.
 *
 * <p>The {@code lastNonce} and {@code balance_*} entries are convenience caches stored outside the
 * authenticated envelope. They must never be trusted for consensus decisions; the chain state is
 * the source of truth.
 */
public class WalletStorage {
    private static final Logger LOGGER = Logger.getLogger(WalletStorage.class.getName());

    private static final int FORMAT_VERSION = 2;
    private static final int DEFAULT_SCRYPT_N = 1 << 15;
    private static final int SCRYPT_R = 8;
    private static final int SCRYPT_P = 1;
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String EXTENSION = ".wallet";
    private static final Pattern WALLET_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path walletPath;
    private final int scryptN;

    /** Stores wallets under {@code <user.home>/.tinc/<directory>}. */
    public WalletStorage(String directory) {
        this(resolveUnderTincHome(directory), DEFAULT_SCRYPT_N);
    }

    /** Stores wallets under an explicit directory (used by tests and embedders). */
    public WalletStorage(Path directory) {
        this(directory, DEFAULT_SCRYPT_N);
    }

    /** Lets tests lower the scrypt cost; never use a low cost outside tests. */
    WalletStorage(Path directory, int scryptN) {
        if (directory == null) {
            throw new IllegalArgumentException("Wallet directory must not be null");
        }
        if (scryptN < 2 || Integer.bitCount(scryptN) != 1) {
            throw new IllegalArgumentException("scrypt N must be a power of two");
        }
        this.walletPath = directory.toAbsolutePath().normalize();
        this.scryptN = scryptN;
        initializeStorage();
    }

    private static Path resolveUnderTincHome(String directory) {
        if (directory == null || directory.isBlank()) {
            throw new IllegalArgumentException("Wallet directory must not be empty");
        }
        Path base = Paths.get(System.getProperty("user.home"), ".tinc").toAbsolutePath().normalize();
        Path resolved = base.resolve(directory).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("Wallet directory escapes the TINC home directory: " + directory);
        }
        return resolved;
    }

    private void initializeStorage() {
        try {
            if (!Files.exists(walletPath)) {
                Files.createDirectories(walletPath);
                LOGGER.info("Created wallet storage directory: " + walletPath);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to create wallet storage directory", e);
        }
    }

    // ------------------------------------------------------------------ save

    /**
     * Saves wallet data, encrypting {@code walletData.privateKey} (if present) under the password.
     * Overwrites an existing wallet with the same id.
     */
    public void saveWallet(String walletId, WalletData walletData, String password) throws Exception {
        byte[] passwordBytes = null;
        byte[] key = null;
        try {
            Path walletFile = walletFile(walletId);
            if (walletData == null) {
                throw new IllegalArgumentException("Wallet data must not be null");
            }
            passwordBytes = passwordBytes(password);
            String address = walletData.address != null ? walletData.address : "";

            byte[] plaintext = walletData.privateKey != null ? walletData.privateKey : new byte[0];
            if (plaintext.length != 0 && plaintext.length != Secp256k1.PRIVATE_KEY_LENGTH) {
                throw new IllegalArgumentException("Private key must be exactly 32 bytes");
            }
            if (plaintext.length != 0) {
                String derived = Secp256k1.address(Secp256k1.publicKeyFromPrivate(new BigInteger(1, plaintext)));
                if (!address.isEmpty() && !normalizeAddress(address).equals(normalizeAddress(derived))) {
                    throw new IllegalArgumentException("Address does not match the private key");
                }
                address = derived;
            }

            byte[] salt = randomBytes(SALT_LENGTH);
            byte[] iv = randomBytes(IV_LENGTH);
            key = SCrypt.generate(passwordBytes, salt, scryptN, SCRYPT_R, SCRYPT_P, 32);
            byte[] ciphertext = aesGcm(Cipher.ENCRYPT_MODE, key, iv, aad(address), plaintext);

            Properties props = new Properties();
            props.setProperty("version", String.valueOf(FORMAT_VERSION));
            props.setProperty("address", address);
            props.setProperty("lastNonce", String.valueOf(walletData.lastNonce));
            props.setProperty("created", String.valueOf(System.currentTimeMillis()));
            props.setProperty("kdf", "scrypt");
            props.setProperty("kdf.n", String.valueOf(scryptN));
            props.setProperty("kdf.r", String.valueOf(SCRYPT_R));
            props.setProperty("kdf.p", String.valueOf(SCRYPT_P));
            props.setProperty("kdf.salt", Secp256k1.toHex(salt));
            props.setProperty("cipher", "aes-256-gcm");
            props.setProperty("cipher.iv", Secp256k1.toHex(iv));
            props.setProperty("ciphertext", Secp256k1.toHex(ciphertext));
            props.setProperty("hasPrivateKey", String.valueOf(plaintext.length != 0));
            if (walletData.balances != null) {
                for (Map.Entry<String, BigInteger> entry : walletData.balances.entrySet()) {
                    props.setProperty("balance_" + entry.getKey(), entry.getValue().toString());
                }
            }

            writeAtomically(walletFile, props, "TINC Wallet " + walletId);
            LOGGER.info("Wallet saved successfully: " + walletId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save wallet: " + walletId, e);
            throw new Exception("Failed to save wallet: " + e.getMessage());
        } finally {
            wipe(key);
            wipe(passwordBytes);
        }
    }

    // ------------------------------------------------------------------ load

    /**
     * Loads and decrypts a wallet. Fails with "Invalid password" for a wrong password and with a
     * descriptive message for corrupted or unsupported files.
     */
    public WalletData loadWallet(String walletId, String password) throws Exception {
        byte[] passwordBytes = null;
        byte[] key = null;
        try {
            Path walletFile = walletFile(walletId);
            if (!Files.exists(walletFile)) {
                throw new Exception("Wallet file not found: " + walletId);
            }
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(walletFile)) {
                props.load(in);
            }
            if (!String.valueOf(FORMAT_VERSION).equals(props.getProperty("version"))) {
                throw new Exception("Unsupported wallet format (legacy or unknown version); re-create the wallet");
            }

            passwordBytes = passwordBytes(password);
            String address = props.getProperty("address", "");
            int n = Integer.parseInt(required(props, "kdf.n"));
            int r = Integer.parseInt(required(props, "kdf.r"));
            int p = Integer.parseInt(required(props, "kdf.p"));
            if (n < 2 || Integer.bitCount(n) != 1 || r < 1 || p < 1 || n > (1 << 22) || r > 32 || p > 16) {
                throw new Exception("Wallet file has invalid key-derivation parameters");
            }
            byte[] salt = Secp256k1.fromHex(required(props, "kdf.salt"));
            byte[] iv = Secp256k1.fromHex(required(props, "cipher.iv"));
            byte[] ciphertext = Secp256k1.fromHex(required(props, "ciphertext"));
            if (iv.length != IV_LENGTH) {
                throw new Exception("Wallet file has an invalid IV");
            }

            key = SCrypt.generate(passwordBytes, salt, n, r, p, 32);
            byte[] plaintext;
            try {
                plaintext = aesGcm(Cipher.DECRYPT_MODE, key, iv, aad(address), ciphertext);
            } catch (AEADBadTagException e) {
                throw new Exception("Invalid password");
            }

            WalletData data = new WalletData();
            data.address = address;
            data.lastNonce = Long.parseLong(props.getProperty("lastNonce", "0"));
            if (plaintext.length != 0) {
                if (plaintext.length != Secp256k1.PRIVATE_KEY_LENGTH) {
                    throw new Exception("Wallet file is corrupted");
                }
                String derived = Secp256k1.address(Secp256k1.publicKeyFromPrivate(new BigInteger(1, plaintext)));
                if (!normalizeAddress(derived).equals(normalizeAddress(address))) {
                    throw new Exception("Wallet file is corrupted (address mismatch)");
                }
                data.privateKey = plaintext;
            }
            for (String name : props.stringPropertyNames()) {
                if (name.startsWith("balance_")) {
                    data.balances.put(name.substring(8), new BigInteger(props.getProperty(name)));
                }
            }
            data.balances.putIfAbsent("TINC", BigInteger.ZERO);

            LOGGER.info("Wallet loaded successfully: " + walletId);
            return data;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load wallet: " + walletId, e);
            throw new Exception("Failed to load wallet: " + e.getMessage());
        } finally {
            wipe(key);
            wipe(passwordBytes);
        }
    }

    // ------------------------------------------------------------------ queries

    public boolean walletExists(String walletId) {
        if (walletId == null || !WALLET_ID.matcher(walletId).matches()) {
            return false;
        }
        boolean exists = Files.exists(walletPath.resolve(walletId + EXTENSION));
        LOGGER.info("Wallet exists check for " + walletId + ": " + exists);
        return exists;
    }

    public String[] listWallets() {
        try {
            if (!Files.exists(walletPath)) {
                return new String[0];
            }
            List<String> walletIds = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(walletPath, "*" + EXTENSION)) {
                for (Path path : stream) {
                    String fileName = path.getFileName().toString();
                    walletIds.add(fileName.substring(0, fileName.length() - EXTENSION.length()));
                }
            }
            java.util.Collections.sort(walletIds);
            LOGGER.info("Found " + walletIds.size() + " wallets");
            return walletIds.toArray(new String[0]);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to list wallets", e);
            return new String[0];
        }
    }

    public void deleteWallet(String walletId) throws Exception {
        try {
            Path walletFile = walletFile(walletId);
            if (!Files.exists(walletFile)) {
                throw new Exception("Wallet file not found: " + walletId);
            }
            Files.delete(walletFile);
            LOGGER.info("Wallet deleted successfully: " + walletId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to delete wallet: " + walletId, e);
            throw new Exception("Failed to delete wallet: " + e.getMessage());
        }
    }

    /** Directory the wallets are stored in. */
    public Path getDirectory() {
        return walletPath;
    }

    // ------------------------------------------------------------------ internals

    private Path walletFile(String walletId) {
        if (walletId == null || !WALLET_ID.matcher(walletId).matches()) {
            throw new IllegalArgumentException("Invalid wallet id (allowed: letters, digits, '.', '_', '-'; max 128)");
        }
        return walletPath.resolve(walletId + EXTENSION);
    }

    private static byte[] aesGcm(int mode, byte[] key, byte[] iv, byte[] aad, byte[] input) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        cipher.updateAAD(aad);
        return cipher.doFinal(input);
    }

    private static byte[] aad(String address) {
        return ("TINC-KEYSTORE-V" + FORMAT_VERSION + "|" + normalizeAddress(address)).getBytes(StandardCharsets.UTF_8);
    }

    private static String normalizeAddress(String address) {
        String a = address == null ? "" : address.trim().toLowerCase(java.util.Locale.ROOT);
        return a.startsWith("0x") ? a.substring(2) : a;
    }

    private static byte[] passwordBytes(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        return Normalizer.normalize(password, Normalizer.Form.NFKC).getBytes(StandardCharsets.UTF_8);
    }

    private static String required(Properties props, String name) throws Exception {
        String value = props.getProperty(name);
        if (value == null) {
            throw new Exception("Wallet file is missing '" + name + "'");
        }
        return value;
    }

    private static byte[] randomBytes(int length) {
        byte[] out = new byte[length];
        RANDOM.nextBytes(out);
        return out;
    }

    private static void wipe(byte[] bytes) {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private static void writeAtomically(Path target, Properties props, String comment) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            props.store(out, comment);
        }
        try {
            Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX file system (e.g. Windows): rely on the user profile ACLs.
        }
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Wallet data container. {@code privateKey} is only populated by {@link #loadWallet}; wipe it after use. */
    public static class WalletData {
        public String address;
        public long lastNonce;
        public Map<String, BigInteger> balances;
        public byte[] privateKey;

        public WalletData() {
            this.balances = new HashMap<>();
        }
    }
}
