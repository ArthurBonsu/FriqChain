package org.example.app.core.crypto;

import java.io.File;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.*;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PublicKeyManager provides functionality for managing key pairs using an SQLite database.
 */
public class PublicKeyManager {
    private static final Logger LOGGER = Logger.getLogger(PublicKeyManager.class.getName());
    private static String DATABASE_FILE;

    // Static initialization of JDBC driver
    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            LOGGER.severe("SQLite JDBC driver not found: " + e.getMessage());
            throw new RuntimeException("SQLite JDBC driver not found", e);
        }
    }

    /**
     * Sets the path to the database file with robust error handling.
     *
     * @param databaseFilePath The file path to the SQLite database.
     */
    public static void setDatabaseFile(String databaseFilePath) {
        if (databaseFilePath == null || databaseFilePath.isEmpty()) {
            throw new IllegalArgumentException("Database file path cannot be null or empty.");
        }
        
        try {
            // Ensure the directory exists
            File dbFile = new File(databaseFilePath);
            dbFile.getParentFile().mkdirs();
            
            DATABASE_FILE = "jdbc:sqlite:" + databaseFilePath;
            initializeDatabase();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to set database file", e);
            throw new RuntimeException("Failed to set database file: " + e.getMessage(), e);
        }
    }

    /**
     * Initializes the SQLite database with comprehensive error handling.
     */
    static void initializeDatabase() {
        if (DATABASE_FILE == null) {
            throw new IllegalStateException("Database file path is not set. Please call setDatabaseFile first.");
        }
        
        try (Connection conn = DriverManager.getConnection(DATABASE_FILE);
             Statement stmt = conn.createStatement()) {
            
            String createTableSQL = "CREATE TABLE IF NOT EXISTS keypairs (" +
                    "node_id TEXT PRIMARY KEY, " +
                    "private_key TEXT NOT NULL, " +
                    "public_key TEXT NOT NULL" +
                    ");";
            stmt.execute(createTableSQL);
            LOGGER.info("Database initialized successfully: " + DATABASE_FILE);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize database", e);
            throw new RuntimeException("Failed to initialize database: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a new key pair.
     *
     * @return The generated key pair.
     */
    public static Keypair generateKeypair() {
        return Keypair.generate();
    }

    /**
     * Stores a key pair securely in the SQLite database.
     *
     * @param keypair The key pair to store.
     * @param nodeId  A unique identifier for the node.
     */
    public static void storeKeypair(Keypair keypair, String nodeId) {
        if (DATABASE_FILE == null) {
            throw new IllegalStateException("Database not initialized. Call setDatabaseFile first.");
        }
        
        String insertSQL = "INSERT OR REPLACE INTO keypairs (node_id, private_key, public_key) VALUES (?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DATABASE_FILE);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {

            String privateKeyBase64 = Base64.getEncoder().encodeToString(keypair.getPrivateKey().getEncoded());
            String publicKeyBase64 = Base64.getEncoder().encodeToString(keypair.getPublicKey().getEncoded());

            pstmt.setString(1, nodeId);
            pstmt.setString(2, privateKeyBase64);
            pstmt.setString(3, publicKeyBase64);

            pstmt.executeUpdate();
            LOGGER.info("Keys securely stored for nodeId: " + nodeId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to store key pair", e);
            throw new RuntimeException("Failed to store key pair for nodeId " + nodeId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Loads a key pair securely from the SQLite database.
     *
     * @param nodeId The unique identifier for the node.
     * @return The loaded key pair.
     */
    public static Keypair loadKeypair(String nodeId) {
        if (DATABASE_FILE == null) {
            throw new IllegalStateException("Database not initialized. Call setDatabaseFile first.");
        }
        
        String selectSQL = "SELECT private_key, public_key FROM keypairs WHERE node_id = ?";
        try (Connection conn = DriverManager.getConnection(DATABASE_FILE);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {

            pstmt.setString(1, nodeId);
            ResultSet rs = pstmt.executeQuery();

            if (!rs.next()) {
                throw new RuntimeException("Keypair not found for nodeId: " + nodeId);
            }

            String privateKeyBase64 = rs.getString("private_key");
            String publicKeyBase64 = rs.getString("public_key");

            KeyFactory keyFactory = KeyFactory.getInstance("EC");

            PrivateKey privateKey = keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64))
            );
            PublicKey publicKey = keyFactory.generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64))
            );

            return new Keypair(privateKey, publicKey);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load key pair", e);
            throw new RuntimeException("Failed to load key pair for nodeId " + nodeId + ": " + e.getMessage(), e);
        } catch (GeneralSecurityException e) {
            LOGGER.log(Level.SEVERE, "Failed to reconstruct key pair", e);
            throw new RuntimeException("Failed to reconstruct key pair for nodeId " + nodeId + ": " + e.getMessage(), e);
        }
    }
}