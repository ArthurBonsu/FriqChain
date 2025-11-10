package org.example.app.core.blockchain.transaction;

import org.example.app.core.block.Transaction;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

/**
 * BankSettlementTransaction represents settlement operations between banks
 * in the TINC Blockchain global banking system. These transactions are used
 * for cross-bank settlements with immutable records.
 */
public class BankSettlementTransaction extends Transaction {
    
    private final Address sourceBankAddress;
    private final Address destBankAddress;
    private final String settlementId;
    private final String sourceCountry;
    private final String destCountry;
    private final BigInteger amount;
    private final String currencyCode;
    private final Date settlementDate;
    private final List<String> proofOfFundsHashes;
    private final int validatorSignatures;
    private final boolean isInternational;
    private final byte[] settlementProof;
    
    /**
     * Creates a new BankSettlementTransaction
     * 
     * @param sourceBankAddress Source bank address
     * @param destBankAddress Destination bank address
     * @param settlementId Unique settlement ID
     * @param sourceCountry Source country code
     * @param destCountry Destination country code
     * @param amount Settlement amount
     * @param currencyCode Currency code for settlement
     * @param proofOfFundsHashes List of proof of funds hashes
     * @param validatorSignatures Number of validator signatures required
     * @param settlementProof Cryptographic proof of settlement
     */
    public BankSettlementTransaction(Address sourceBankAddress, Address destBankAddress, 
                                  String settlementId, String sourceCountry, String destCountry,
                                  BigInteger amount, String currencyCode, 
                                  List<String> proofOfFundsHashes, int validatorSignatures,
                                  byte[] settlementProof) {
        super(buildTransactionData(sourceBankAddress, destBankAddress, settlementId, 
                                 sourceCountry, destCountry, amount, currencyCode,
                                 proofOfFundsHashes, validatorSignatures, settlementProof));
        this.sourceBankAddress = sourceBankAddress;
        this.destBankAddress = destBankAddress;
        this.settlementId = settlementId;
        this.sourceCountry = sourceCountry;
        this.destCountry = destCountry;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.settlementDate = new Date();
        this.proofOfFundsHashes = new ArrayList<>(proofOfFundsHashes);
        this.validatorSignatures = validatorSignatures;
        this.isInternational = !sourceCountry.equals(destCountry);
        this.settlementProof = settlementProof.clone();
    }
    
    /**
     * Builds the transaction data byte array
     */
    private static byte[] buildTransactionData(Address sourceBankAddress, Address destBankAddress,
                                            String settlementId, String sourceCountry, 
                                            String destCountry, BigInteger amount, 
                                            String currencyCode, List<String> proofOfFundsHashes,
                                            int validatorSignatures, byte[] settlementProof) {
        ByteBuffer buffer = ByteBuffer.allocate(2048); // Sufficient size for data
        
        // Write addresses
        if (sourceBankAddress != null) {
            buffer.put(sourceBankAddress.toSlice());
        } else {
            buffer.put(new byte[Address.ADDRESS_LENGTH]);
        }
        
        if (destBankAddress != null) {
            buffer.put(destBankAddress.toSlice());
        } else {
            buffer.put(new byte[Address.ADDRESS_LENGTH]);
        }
        
        // Write settlement ID with length prefix
        byte[] idBytes = settlementId.getBytes();
        buffer.putInt(idBytes.length);
        buffer.put(idBytes);
        
        // Write country codes with length prefixes
        byte[] sourceCountryBytes = sourceCountry.getBytes();
        buffer.putInt(sourceCountryBytes.length);
        buffer.put(sourceCountryBytes);
        
        byte[] destCountryBytes = destCountry.getBytes();
        buffer.putInt(destCountryBytes.length);
        buffer.put(destCountryBytes);
        
        // Write amount
        byte[] amountBytes = amount.toByteArray();
        buffer.putInt(amountBytes.length);
        buffer.put(amountBytes);
        
        // Write currency code with length prefix
        byte[] currencyBytes = currencyCode.getBytes();
        buffer.putInt(currencyBytes.length);
        buffer.put(currencyBytes);
        
        // Write proof of funds hashes
        buffer.putInt(proofOfFundsHashes.size());
        for (String proofHash : proofOfFundsHashes) {
            byte[] proofBytes = proofHash.getBytes();
            buffer.putInt(proofBytes.length);
            buffer.put(proofBytes);
        }
        
        // Write validator signatures count
        buffer.putInt(validatorSignatures);
        
        // Write settlement proof with length prefix
        buffer.putInt(settlementProof.length);
        buffer.put(settlementProof);
        
        // Finalize and return
        buffer.flip();
        byte[] result = new byte[buffer.limit()];
        buffer.get(result);
        return result;
    }
    
    /**
     * Gets the source bank address
     */
    public Address getSourceBankAddress() {
        return sourceBankAddress;
    }
    
    /**
     * Gets the destination bank address
     */
    public Address getDestBankAddress() {
        return destBankAddress;
    }
    
    /**
     * Gets the settlement ID
     */
    public String getSettlementId() {
        return settlementId;
    }
    
    /**
     * Gets the source country
     */
    public String getSourceCountry() {
        return sourceCountry;
    }
    
    /**
     * Gets the destination country
     */
    public String getDestCountry() {
        return destCountry;
    }
    
    /**
     * Gets the settlement amount
     */
    public BigInteger getAmount() {
        return amount;
    }
    
    /**
     * Gets the currency code
     */
    public String getCurrencyCode() {
        return currencyCode;
    }
    
    /**
     * Gets the settlement date
     */
    public Date getSettlementDate() {
        return settlementDate;
    }
    
    /**
     * Gets the proof of funds hashes
     */
    public List<String> getProofOfFundsHashes() {
        return new ArrayList<>(proofOfFundsHashes);
    }
    
    /**
     * Gets the number of validator signatures
     */
    public int getValidatorSignatures() {
        return validatorSignatures;
    }
    
    /**
     * Checks if this is an international settlement
     */
    public boolean isInternational() {
        return isInternational;
    }
    
    /**
     * Gets the settlement proof
     */
    public byte[] getSettlementProof() {
        return settlementProof.clone();
    }
    
    /**
     * Verifies the settlement proof
     */
    public boolean verifySettlementProof() {
        // This would normally involve cryptographic verification
        // Simplified implementation for now
        return settlementProof.length > 0 && validatorSignatures >= getRequiredSignatures();
    }
    
    /**
     * Determines the required number of validator signatures based on amount and international status
     */
    private int getRequiredSignatures() {
        // Higher requirements for international and large value transfers
        if (isInternational()) {
            // International transfers require more signatures
            if (amount.compareTo(new BigInteger("1000000")) > 0) {
                return 5; // Very large international transfers
            } else {
                return 3; // Standard international transfers
            }
        } else {
            // Domestic transfers
            if (amount.compareTo(new BigInteger("1000000")) > 0) {
                return 3; // Large domestic transfers
            } else {
                return 2; // Standard domestic transfers
            }
        }
    }
    
    @Override
    public boolean isValid() {
        if (!super.isValid()) {
            return false;
        }
        
        // Additional validation specific to bank settlement transactions
        if (sourceBankAddress == null || destBankAddress == null) {
            return false;
        }
        
        if (settlementId == null || settlementId.isEmpty()) {
            return false;
        }
        
        if (sourceCountry == null || sourceCountry.isEmpty() || 
            destCountry == null || destCountry.isEmpty()) {
            return false;
        }
        
        if (amount == null || amount.compareTo(BigInteger.ZERO) <= 0) {
            return false;
        }
        
        if (currencyCode == null || currencyCode.isEmpty()) {
            return false;
        }
        
        if (proofOfFundsHashes == null || proofOfFundsHashes.isEmpty()) {
            return false;
        }
        
        if (validatorSignatures < getRequiredSignatures()) {
            return false;
        }
        
        if (settlementProof == null || settlementProof.length == 0) {
            return false;
        }
        
        return verifySettlementProof();
    }
    
    @Override
    public String toString() {
        return "BankSettlementTransaction{" +
                "sourceBank='" + sourceBankAddress + '\'' +
                ", destBank='" + destBankAddress + '\'' +
                ", settlementId='" + settlementId + '\'' +
                ", sourceCountry='" + sourceCountry + '\'' +
                ", destCountry='" + destCountry + '\'' +
                ", amount=" + amount +
                ", currency='" + currencyCode + '\'' +
                ", settlementDate=" + settlementDate +
                ", validatorSignatures=" + validatorSignatures +
                ", isInternational=" + isInternational +
                '}';
    }
}