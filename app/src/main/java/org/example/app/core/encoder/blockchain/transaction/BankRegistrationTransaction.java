package org.example.app.core.blockchain.transaction;

import org.example.app.core.block.Transaction;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        // This would check the current geographic distribution of validators
        // in the blockchain state and verify this new registration wouldn't
        // exceed the 33% threshold for any region.
        
        // Placeholder implementation - in a real system, this would query
        // the blockchain state for all registered banks and their regions.
        return true;
    }
    
    /**
     * Creates an encrypted API channel configuration for secure bank communication
     */
    public byte[] createEncryptedChannelConfig() {
        // In a real implementation, this would generate a secure channel config
        // using the bank's public key
        
        // Placeholder implementation
        return "ENCRYPTED_CHANNEL_CONFIG".getBytes();
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
        
        if (encryptedCredentials == null || encryptedCredentials.length == 0) {
            return false;
        }
        
        if (initialOperators == null || initialOperators.isEmpty()) {
            return false;
        }
        
        if (requiredSignatures <= 0 || requiredSignatures > initialOperators.size()) {
            return false;
        }
        
        if (geographicRegion < 1 || geographicRegion > 4) {
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