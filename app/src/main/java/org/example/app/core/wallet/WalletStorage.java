// ADD this WalletStorage implementation to your project
// Create this file: src/main/java/org/example/app/core/wallet/WalletStorage.java

package org.example.app.core.wallet;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.math.BigInteger;

/**
 * Handles wallet persistence and storage operations
 */
public class WalletStorage {
    private static final Logger LOGGER = Logger.getLogger(WalletStorage.class.getName());
    
    private final String walletDirectory;
    private final Path walletPath;
    
    public WalletStorage(String directory) {
        this.walletDirectory = directory;
        this.walletPath = Paths.get(System.getProperty("user.home"), ".tinc", directory);
        initializeStorage();
    }
    
    /**
     * Initialize storage directory
     */
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
    
    /**
     * Save wallet data to storage
     */
    public void saveWallet(String walletId, WalletData walletData, String password) throws Exception {
        try {
            Path walletFile = walletPath.resolve(walletId + ".wallet");
            
            // Create properties for wallet data
            Properties props = new Properties();
            props.setProperty("address", walletData.address != null ? walletData.address : "");
            props.setProperty("lastNonce", String.valueOf(walletData.lastNonce));
            props.setProperty("created", String.valueOf(System.currentTimeMillis()));
            props.setProperty("password_hash", String.valueOf(password.hashCode())); // Simple hash for demo
            
            // Save balances
            if (walletData.balances != null) {
                for (Map.Entry<String, BigInteger> entry : walletData.balances.entrySet()) {
                    props.setProperty("balance_" + entry.getKey(), entry.getValue().toString());
                }
            }
            
            // Write to file
            try (FileOutputStream fos = new FileOutputStream(walletFile.toFile())) {
                props.store(fos, "TINC Wallet Data for " + walletId);
            }
            
            LOGGER.info("Wallet saved successfully: " + walletId);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save wallet: " + walletId, e);
            throw new Exception("Failed to save wallet: " + e.getMessage());
        }
    }
    
    /**
     * Load wallet data from storage
     */
    public WalletData loadWallet(String walletId, String password) throws Exception {
        try {
            Path walletFile = walletPath.resolve(walletId + ".wallet");
            
            if (!Files.exists(walletFile)) {
                throw new Exception("Wallet file not found: " + walletId);
            }
            
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(walletFile.toFile())) {
                props.load(fis);
            }
            
            // Verify password (simple check for demo)
            String storedPasswordHash = props.getProperty("password_hash");
            if (storedPasswordHash != null && !storedPasswordHash.equals(String.valueOf(password.hashCode()))) {
                throw new Exception("Invalid password");
            }
            
            // Load wallet data
            WalletData walletData = new WalletData();
            walletData.address = props.getProperty("address", "");
            walletData.lastNonce = Long.parseLong(props.getProperty("lastNonce", "0"));
            walletData.balances = new HashMap<>();
            
            // Load balances
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith("balance_")) {
                    String currency = key.substring(8); // Remove "balance_" prefix
                    String balanceStr = props.getProperty(key);
                    walletData.balances.put(currency, new BigInteger(balanceStr));
                }
            }
            
            // Ensure TINC balance exists
            if (!walletData.balances.containsKey("TINC")) {
                walletData.balances.put("TINC", BigInteger.ZERO);
            }
            
            LOGGER.info("Wallet loaded successfully: " + walletId);
            return walletData;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load wallet: " + walletId, e);
            throw new Exception("Failed to load wallet: " + e.getMessage());
        }
    }
    
    /**
     * Check if wallet exists
     */
    public boolean walletExists(String walletId) {
        Path walletFile = walletPath.resolve(walletId + ".wallet");
        boolean exists = Files.exists(walletFile);
        LOGGER.info("Wallet exists check for " + walletId + ": " + exists);
        return exists;
    }
    
    /**
     * List all available wallets
     */
    public String[] listWallets() {
        try {
            if (!Files.exists(walletPath)) {
                return new String[0];
            }
            
            List<String> walletIds = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(walletPath, "*.wallet")) {
                for (Path path : stream) {
                    String fileName = path.getFileName().toString();
                    String walletId = fileName.substring(0, fileName.lastIndexOf(".wallet"));
                    walletIds.add(walletId);
                }
            }
            
            LOGGER.info("Found " + walletIds.size() + " wallets");
            return walletIds.toArray(new String[0]);
            
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to list wallets", e);
            return new String[0];
        }
    }
    
    /**
     * Delete wallet
     */
    public void deleteWallet(String walletId) throws Exception {
        try {
            Path walletFile = walletPath.resolve(walletId + ".wallet");
            
            if (Files.exists(walletFile)) {
                Files.delete(walletFile);
                LOGGER.info("Wallet deleted successfully: " + walletId);
            } else {
                throw new Exception("Wallet file not found: " + walletId);
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to delete wallet: " + walletId, e);
            throw new Exception("Failed to delete wallet: " + e.getMessage());
        }
    }
    
    /**
     * Wallet data container
     */
    public static class WalletData {
        public String address;
        public long lastNonce;
        public Map<String, BigInteger> balances;
        
        public WalletData() {
            this.balances = new HashMap<>();
        }
    }
}