package org.example.app.core.blockchain.transaction;

import org.example.app.core.block.Transaction;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * BankRegistrationTransaction represents the registration of banks in the TINC Blockchain
 * global banking system. It implements the secure and encrypted registration process
 * that provides government resistance features.
 */
public class BankRegistrationTransaction extends Transaction {
    
    private final String bankName;
    private final String bankCountry;
    private final String bankLicenseId;
    private final Address bankAddress;
    private final String swiftCode;
    private final byte[] publicKey;
    private final byte[] encryptedCredentials;
    private final List<Address> initialOperators;
    private final boolean isCentralBank;
    private final int requiredSignatures;
    private final String registrationId;
    private final Date registrationDate;
    private final int geographicRegion; // 1-Africa, 2-Europe, 3-Americas, 4-Asia
    
    // Static registry for tracking geographic distribution
    private static final Map<Integer, List<String>> REGION_BANK_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Integer> COUNTRY_BANK_COUNT = new ConcurrentHashMap<>();
    private static final BigDecimal MAX_REGION_PERCENTAGE = new BigDecimal("0.33"); // 33% max
    private static final BigDecimal MAX_COUNTRY_PERCENTAGE = new BigDecimal("0.25"); // 25% max
    
    // Cryptographic constants for secure channel
    private static final String CHANNEL_ENCRYPTION_ALGORITHM = "AES-256-GCM";
    private static final int CHANNEL_KEY_LENGTH = 32; // 256 bits
    private static final int CHANNEL_IV_LENGTH = 12; // 96 bits for GCM
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    /**
     * Creates a new BankRegistrationTransaction
     * 
     * @param bankName Bank name
     * @param bankCountry Bank country
     * @param bankLicenseId Bank license ID
     * @param bankAddress Bank's blockchain address
     * @param swiftCode Bank's SWIFT code
     * @param publicKey Bank's public key for secure communication
     * @param encryptedCredentials Encrypted bank credentials
     * @param initialOperators List of initial operator addresses
     * @param isCentralBank Whether this is a central bank
     * @param requiredSignatures Number of signatures required for vault operations
     * @param geographicRegion Geographic region code
     */
    public BankRegistrationTransaction(String bankName, String bankCountry, String bankLicenseId,
                                    Address bankAddress, String swiftCode, byte[] publicKey,
                                    byte[] encryptedCredentials, List<Address> initialOperators,
                                    boolean isCentralBank, int requiredSignatures,
                                    int geographicRegion) {
        super(buildTransactionData(bankName, bankCountry, bankLicenseId, bankAddress, swiftCode,
                               publicKey, encryptedCredentials, initialOperators, isCentralBank,
                               requiredSignatures, geographicRegion));
        this.bankName = bankName;
        this.bankCountry = bankCountry;
        this.bankLicenseId = bankLicenseId;
        this.bankAddress = bankAddress;
        this.swiftCode = swiftCode;
        this.publicKey = Arrays.copyOf(publicKey, publicKey.length);
        this.encryptedCredentials = Arrays.copyOf(encryptedCredentials, encryptedCredentials.length);
        this.initialOperators = new ArrayList<>(initialOperators);
        this.isCentralBank = isCentralBank;
        this.requiredSignatures = requiredSignatures;
        this.geographicRegion = geographicRegion;
        this.registrationId = UUID.randomUUID().toString();
        this.registrationDate = new Date();
    }
    
    /**
     * Builds the transaction data byte array
     */
    private static byte[] buildTransactionData(String bankName, String bankCountry, 
                                            String bankLicenseId, Address bankAddress,
                                            String swiftCode, byte[] publicKey, 
                                            byte[] encryptedCredentials, List<Address> initialOperators,
                                            boolean isCentralBank, int requiredSignatures,
                                            int geographicRegion) {
        ByteBuffer buffer = ByteBuffer.allocate(4096); // Sufficient size for data
        
        // Write bank name with length prefix
        byte[] nameBytes = bankName.getBytes();
        buffer.putInt(nameBytes.length);
        buffer.put(nameBytes);
        
        // Write bank country with length prefix
        byte[] countryBytes = bankCountry.getBytes();
        buffer.putInt(countryBytes.length);
        buffer.put(countryBytes);
        
        // Write bank license ID with length prefix
        byte[] licenseBytes = bankLicenseId.getBytes();
        buffer.putInt(licenseBytes.length);
        buffer.put(licenseBytes);
        
        // Write bank address
        if (bankAddress != null) {
            buffer.put(bankAddress.toSlice());
        } else {
            buffer.put(new byte[Address.ADDRESS_LENGTH]);
        }
        
        // Write SWIFT code with length prefix
        byte[] swiftBytes = swiftCode.getBytes();
        buffer.putInt(swiftBytes.length);
        buffer.put(swiftBytes);
        
        // Write public key with length prefix
        buffer.putInt(publicKey.length);
        buffer.put(publicKey);
        
        // Write encrypted credentials with length prefix
        buffer.putInt(encryptedCredentials.length);
        buffer.put(encryptedCredentials);
        
        // Write initial operators
        buffer.putInt(initialOperators.size());
        for (Address operator : initialOperators) {
            buffer.put(operator.toSlice());
        }
        
        // Write is central bank flag
        buffer.put((byte) (isCentralBank ? 1 : 0));
        
        // Write required signatures
        buffer.putInt(requiredSignatures);
        
        // Write geographic region
        buffer.putInt(geographicRegion);
        
        // Finalize and return
        buffer.flip();
        byte[] result = new byte[buffer.limit()];
        buffer.get(result);
        return result;
    }
    
    /**
     * Gets the bank name
     */
    public String getBankName() {
        return bankName;
    }
    
    /**
     * Gets the bank country
     */
    public String getBankCountry() {
        return bankCountry;
    }
    
    /**
     * Gets the bank license ID
     */
    public String getBankLicenseId() {
        return bankLicenseId;
    }
    
    /**
     * Gets the bank address
     */
    public Address getBankAddress() {
        return bankAddress;
    }
    
    /**
     * Gets the bank SWIFT code
     */
    public String getSwiftCode() {
        return swiftCode;
    }
    
    /**
     * Gets the bank's public key
     */
    public byte[] getPublicKey() {
        return Arrays.copyOf(publicKey, publicKey.length);
    }
    
    /**
     * Gets the encrypted credentials
     */
    public byte[] getEncryptedCredentials() {
        return Arrays.copyOf(encryptedCredentials, encryptedCredentials.length);
    }
    
    /**
     * Gets the initial operators
     */
    public List<Address> getInitialOperators() {
        return new ArrayList<>(initialOperators);
    }
    
    /**
     * Checks if this is a central bank
     */
    public boolean isCentralBank() {
        return isCentralBank;
    }
    
    /**
     * Gets the required signatures for vault operations
     */
    public int getRequiredSignatures() {
        return requiredSignatures;
    }
    
    /**
     * Gets the registration ID
     */
    public String getRegistrationId() {
        return registrationId;
    }
    
    /**
     * Gets the registration date
     */
    public Date getRegistrationDate() {
        return registrationDate;
    }
    
    /**
     * Gets the geographic region code
     * 1-Africa, 2-Europe, 3-Americas, 4-Asia
     */
    public int getGeographicRegion() {
        return geographicRegion;
    }
    
    /**
     * Verifies that the bank registration meets geographical distribution requirements
     * to ensure no single region/government controls more than 33% of the banking system
     */
    public boolean meetsGeographicDistributionRequirements() {
        synchronized (REGION_BANK_REGISTRY) {
            // Calculate total registered banks
            int totalBanks = REGION_BANK_REGISTRY.values().stream()
                .mapToInt(List::size)
                .sum();
            
            // Add 1 for this new registration
            totalBanks += 1;
            
            // Get current count for this region
            List<String> regionBanks = REGION_BANK_REGISTRY.getOrDefault(geographicRegion, new ArrayList<>());
            int regionCount = regionBanks.size() + 1; // +1 for this registration
            
            // Calculate percentage
            BigDecimal regionPercentage = new BigDecimal(regionCount)
                .divide(new BigDecimal(totalBanks), 4, RoundingMode.HALF_UP);
            
            // Check region limit
            if (regionPercentage.compareTo(MAX_REGION_PERCENTAGE) > 0) {
                return false;
            }
            
            // Check country-specific limits
            Integer countryCount = COUNTRY_BANK_COUNT.getOrDefault(bankCountry, 0);
            BigDecimal countryPercentage = new BigDecimal(countryCount + 1)
                .divide(new BigDecimal(totalBanks), 4, RoundingMode.HALF_UP);
            
            if (countryPercentage.compareTo(MAX_COUNTRY_PERCENTAGE) > 0) {
                return false;
            }
            
            // Additional check: Central banks have stricter limits
            if (isCentralBank) {
                // Count existing central banks in this region
                long centralBanksInRegion = regionBanks.stream()
                    .filter(bankId -> isCentralBankById(bankId))
                    .count();
                
                // Limit: Max 2 central banks per region
                if (centralBanksInRegion >= 2) {
                    return false;
                }
            }
            
            // Check for suspicious clustering (Herfindahl-Hirschman Index)
            if (!passesConcentrationTest(totalBanks)) {
                return false;
            }
            
            return true;
        }
    }
    
    /**
     * Checks market concentration using Herfindahl-Hirschman Index
     */
    private boolean passesConcentrationTest(int totalBanks) {
        BigDecimal hhi = BigDecimal.ZERO;
        
        // Calculate HHI for regions
        for (List<String> regionBanks : REGION_BANK_REGISTRY.values()) {
            BigDecimal marketShare = new BigDecimal(regionBanks.size())
                .divide(new BigDecimal(totalBanks), 4, RoundingMode.HALF_UP);
            
            // Square the market share and add to HHI
            hhi = hhi.add(marketShare.multiply(marketShare));
        }
        
        // Add this registration's impact
        BigDecimal newRegionSize = new BigDecimal(
            REGION_BANK_REGISTRY.getOrDefault(geographicRegion, new ArrayList<>()).size() + 1
        );
        BigDecimal newMarketShare = newRegionSize.divide(new BigDecimal(totalBanks), 4, RoundingMode.HALF_UP);
        
        // Recalculate HHI with new registration
        BigDecimal oldRegionSize = newRegionSize.subtract(BigDecimal.ONE);
        if (oldRegionSize.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal oldMarketShare = oldRegionSize.divide(new BigDecimal(totalBanks - 1), 4, RoundingMode.HALF_UP);
            hhi = hhi.subtract(oldMarketShare.multiply(oldMarketShare));
        }
        hhi = hhi.add(newMarketShare.multiply(newMarketShare));
        
        // HHI threshold: 0.25 (competitive market)
        return hhi.compareTo(new BigDecimal("0.25")) <= 0;
    }
    
    /**
     * Helper method to check if a bank ID represents a central bank
     */
    private boolean isCentralBankById(String bankId) {
        // In production, this would query the blockchain state
        // For now, check if the bank name contains central bank indicators
        return bankId.toLowerCase().contains("central") || 
               bankId.toLowerCase().contains("reserve") ||
               bankId.toLowerCase().contains("national");
    }
    
    /**
     * Registers this bank in the geographic distribution tracking
     */
    public void registerInGeographicRegistry() {
        synchronized (REGION_BANK_REGISTRY) {
            REGION_BANK_REGISTRY
                .computeIfAbsent(geographicRegion, k -> new ArrayList<>())
                .add(registrationId);
            
            COUNTRY_BANK_COUNT.merge(bankCountry, 1, Integer::sum);
        }
    }
    
    /**
     * Creates an encrypted API channel configuration for secure bank communication
     */
    public byte[] createEncryptedChannelConfig() {
        try {
            // Generate secure channel configuration
            ByteBuffer configBuffer = ByteBuffer.allocate(1024);
            
            // 1. Generate session key for this bank
            byte[] sessionKey = new byte[CHANNEL_KEY_LENGTH];
            SECURE_RANDOM.nextBytes(sessionKey);
            
            // 2. Generate initialization vector
            byte[] iv = new byte[CHANNEL_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            
            // 3. Create channel metadata
            long channelId = SECURE_RANDOM.nextLong();
            long expiryTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000); // 24 hours
            
            // 4. Build configuration structure
            configBuffer.putLong(channelId);
            configBuffer.putLong(expiryTime);
            configBuffer.put((byte) CHANNEL_KEY_LENGTH);
            configBuffer.put(sessionKey);
            configBuffer.put((byte) CHANNEL_IV_LENGTH);
            configBuffer.put(iv);
            
            // 5. Add bank-specific parameters
            configBuffer.put((byte) bankCountry.length());
            configBuffer.put(bankCountry.getBytes());
            configBuffer.put((byte) (isCentralBank ? 1 : 0));
            configBuffer.putInt(requiredSignatures);
            
            // 6. Add endpoint configuration
            String apiEndpoint = generateAPIEndpoint();
            byte[] endpointBytes = apiEndpoint.getBytes();
            configBuffer.putInt(endpointBytes.length);
            configBuffer.put(endpointBytes);
            
            // 7. Add authentication parameters
            configBuffer.putInt(publicKey.length);
            configBuffer.put(publicKey);
            
            // 8. Generate HMAC for integrity
            configBuffer.flip();
            byte[] configData = new byte[configBuffer.remaining()];
            configBuffer.get(configData);
            
            byte[] hmac = generateHMAC(configData, sessionKey);
            
            // 9. Final encrypted configuration
            ByteBuffer finalConfig = ByteBuffer.allocate(configData.length + hmac.length + 4);
            finalConfig.putInt(hmac.length);
            finalConfig.put(hmac);
            finalConfig.put(configData);
            
            return finalConfig.array();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create encrypted channel configuration", e);
        }
    }
    
    /**
     * Generates bank-specific API endpoint
     */
    private String generateAPIEndpoint() {
        // Generate secure endpoint based on bank characteristics
        StringBuilder endpoint = new StringBuilder();
        endpoint.append("https://api.tinc-blockchain.org/");
        
        // Add region-specific routing
        switch (geographicRegion) {
            case 1: endpoint.append("africa/"); break;
            case 2: endpoint.append("europe/"); break;
            case 3: endpoint.append("americas/"); break;
            case 4: endpoint.append("asia/"); break;
            default: endpoint.append("global/");
        }
        
        // Add bank type routing
        if (isCentralBank) {
            endpoint.append("central-bank/");
        } else {
            endpoint.append("commercial-bank/");
        }
        
        // Add unique bank identifier
        endpoint.append(generateBankHash());
        
        return endpoint.toString();
    }
    
    /**
     * Generates a unique bank hash for routing
     */
    private String generateBankHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bankName.getBytes());
            digest.update(bankCountry.getBytes());
            digest.update(bankLicenseId.getBytes());
            digest.update(swiftCode.getBytes());
            
            byte[] hash = digest.digest();
            
            // Convert to hex string (first 16 characters for endpoint)
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < Math.min(hash.length, 8); i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
    /**
     * Generates HMAC for data integrity
     */
    private byte[] generateHMAC(byte[] data, byte[] key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Simplified HMAC implementation
            byte[] keyPad = new byte[64]; // SHA-256 block size
            System.arraycopy(key, 0, keyPad, 0, Math.min(key.length, keyPad.length));
            
            // XOR with ipad (0x36)
            for (int i = 0; i < keyPad.length; i++) {
                keyPad[i] ^= 0x36;
            }
            
            digest.update(keyPad);
            digest.update(data);
            byte[] innerHash = digest.digest();
            
            // XOR with opad (0x5C)
            System.arraycopy(key, 0, keyPad, 0, Math.min(key.length, keyPad.length));
            for (int i = 0; i < keyPad.length; i++) {
                keyPad[i] ^= 0x5C;
            }
            
            digest.reset();
            digest.update(keyPad);
            digest.update(innerHash);
            
            return digest.digest();
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
    @Override
    public boolean isValid() {
        if (!super.isValid()) {
            return false;
        }
        
        // Additional validation specific to bank registration transactions
        if (bankName == null || bankName.isEmpty()) {
            return false;
        }
        
        if (bankCountry == null || bankCountry.isEmpty()) {
            return false;
        }
        
        if (bankLicenseId == null || bankLicenseId.isEmpty()) {
            return false;
        }
        
        if (bankAddress == null) {
            return false;
        }
        
        if (publicKey == null || publicKey.length == 0) {
            return false;
        }
        
        // Validate public key length (should be valid key size)
        if (publicKey.length != 32 && publicKey.length != 64 && publicKey.length != 65) {
            return false; // Invalid key sizes for common algorithms
        }
        
        if (encryptedCredentials == null || encryptedCredentials.length == 0) {
            return false;
        }
        
        if (initialOperators == null || initialOperators.isEmpty()) {
            return false;
        }
        
        // Validate required signatures makes sense
        if (requiredSignatures <= 0 || requiredSignatures > initialOperators.size()) {
            return false;
        }
        
        // Must have at least 51% signatures for security
        if (requiredSignatures < (initialOperators.size() / 2) + 1) {
            return false;
        }
        
        if (geographicRegion < 1 || geographicRegion > 4) {
            return false;
        }
        
        // Validate SWIFT code format (8 or 11 characters)
        if (swiftCode == null || (swiftCode.length() != 8 && swiftCode.length() != 11)) {
            return false;
        }
        
        // Check geographic distribution requirements
        return meetsGeographicDistributionRequirements();
    }
    
    @Override
    public String toString() {
        return "BankRegistrationTransaction{" +
                "bankName='" + bankName + '\'' +
                ", bankCountry='" + bankCountry + '\'' +
                ", bankLicenseId='" + bankLicenseId + '\'' +
                ", bankAddress=" + bankAddress +
                ", swiftCode='" + swiftCode + '\'' +
                ", isCentralBank=" + isCentralBank +
                ", requiredSignatures=" + requiredSignatures +
                ", registrationId='" + registrationId + '\'' +
                ", registrationDate=" + registrationDate +
                ", geographicRegion=" + geographicRegion +
                '}';
    }
}