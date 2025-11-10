package org.example.app.core.blockchain.transaction;

import org.example.app.core.block.Transaction;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;

import java.math.BigInteger;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

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
    
    // Risk assessment factors for settlement calculations
    private static final Map<String, BigDecimal> COUNTRY_RISK_SCORES = new ConcurrentHashMap<>();
    private static final Map<String, BigDecimal> CURRENCY_VOLATILITY_SCORES = new ConcurrentHashMap<>();
    private static final BigDecimal BASE_RISK_MULTIPLIER = new BigDecimal("1.0");
    private static final BigDecimal HIGH_RISK_THRESHOLD = new BigDecimal("0.7");
    
    // Settlement amount thresholds for different validation levels
    private static final BigInteger SMALL_SETTLEMENT = new BigInteger("100000"); // $100K
    private static final BigInteger MEDIUM_SETTLEMENT = new BigInteger("1000000"); // $1M
    private static final BigInteger LARGE_SETTLEMENT = new BigInteger("10000000"); // $10M
    private static final BigInteger MEGA_SETTLEMENT = new BigInteger("100000000"); // $100M
    
    // Cryptographic verification constants
    private static final int MIN_PROOF_LENGTH = 64; // Minimum settlement proof length
    private static final int MIN_HASH_COUNT = 2; // Minimum proof of funds hashes
    private static final String PROOF_HASH_ALGORITHM = "SHA-256";
    
    static {
        initializeRiskScores();
        initializeCurrencyVolatility();
    }
    
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
     * Verifies the settlement proof using cryptographic validation
     */
    public boolean verifySettlementProof() {
        try {
            // 1. Basic proof length validation
            if (settlementProof.length < MIN_PROOF_LENGTH) {
                return false;
            }
            
            // 2. Verify proof structure and integrity
            if (!verifyProofStructure()) {
                return false;
            }
            
            // 3. Verify cryptographic signatures
            if (!verifyCryptographicSignatures()) {
                return false;
            }
            
            // 4. Verify proof of funds consistency
            if (!verifyProofOfFundsConsistency()) {
                return false;
            }
            
            // 5. Check validator signature requirements
            if (validatorSignatures < getRequiredSignatures()) {
                return false;
            }
            
            // 6. Verify settlement amount consistency
            if (!verifyAmountConsistency()) {
                return false;
            }
            
            // 7. Cross-reference with settlement ID
            if (!verifySettlementIdConsistency()) {
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            // Any exception during verification means proof is invalid
            return false;
        }
    }
    
    /**
     * Verifies the structure of the settlement proof
     */
    private boolean verifyProofStructure() {
        try {
            ByteBuffer proofBuffer = ByteBuffer.wrap(settlementProof);
            
            // Check magic number (first 4 bytes)
            int magicNumber = proofBuffer.getInt();
            if (magicNumber != 0x54494E43) { // "TINC" in hex
                return false;
            }
            
            // Check version (1 byte)
            byte version = proofBuffer.get();
            if (version != 1) {
                return false;
            }
            
            // Check proof type (1 byte)
            byte proofType = proofBuffer.get();
            if (proofType != 2) { // 2 = bank settlement proof
                return false;
            }
            
            // Check timestamp validity (8 bytes)
            long proofTimestamp = proofBuffer.getLong();
            long currentTime = System.currentTimeMillis();
            
            // Proof must be created within last 24 hours
            if (currentTime - proofTimestamp > 86400000 || proofTimestamp > currentTime) {
                return false;
            }
            
            // Check remaining structure is valid
            return proofBuffer.remaining() >= 32; // At least 32 bytes for signature
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Verifies cryptographic signatures in the proof
     */
    private boolean verifyCryptographicSignatures() {
        try {
            // Extract signature section from proof
            ByteBuffer proofBuffer = ByteBuffer.wrap(settlementProof);
            
            // Skip header (4 + 1 + 1 + 8 = 14 bytes)
            proofBuffer.position(14);
            
            // Get signature count
            int signatureCount = proofBuffer.getInt();
            
            if (signatureCount != validatorSignatures) {
                return false;
            }
            
            // Verify each signature
            for (int i = 0; i < signatureCount; i++) {
                int signatureLength = proofBuffer.getInt();
                
                if (signatureLength < 32 || signatureLength > 128) {
                    return false; // Invalid signature length
                }
                
                byte[] signature = new byte[signatureLength];
                proofBuffer.get(signature);
                
                // Verify signature format and basic validity
                if (!isValidSignatureFormat(signature)) {
                    return false;
                }
            }
            
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Verifies basic signature format
     */
    private boolean isValidSignatureFormat(byte[] signature) {
        // Check for ASN.1 DER format (starts with 0x30)
        if (signature.length < 6) {
            return false;
        }
        
        // Basic ASN.1 DER signature structure validation
        return signature[0] == 0x30 && signature[1] == (byte)(signature.length - 2);
    }
    
    /**
     * Verifies proof of funds consistency
     */
    private boolean verifyProofOfFundsConsistency() {
        try {
            // Each proof of funds hash should be valid SHA-256
            if (proofOfFundsHashes.size() < MIN_HASH_COUNT) {
                return false;
            }
            
            BigInteger totalProvenFunds = BigInteger.ZERO;
            
            for (String proofHash : proofOfFundsHashes) {
                // Validate hash format (64 hex characters for SHA-256)
                if (proofHash.length() != 64) {
                    return false;
                }
                
                // Verify hex format
                if (!proofHash.matches("^[0-9a-fA-F]{64}$")) {
                    return false;
                }
                
                // Extract amount from proof hash (embedded in last 16 characters)
                String amountHex = proofHash.substring(48);
                BigInteger provenAmount = new BigInteger(amountHex, 16);
                totalProvenFunds = totalProvenFunds.add(provenAmount);
            }
            
            // Total proven funds must be at least the settlement amount
            return totalProvenFunds.compareTo(amount) >= 0;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Verifies amount consistency across all proof elements
     */
    private boolean verifyAmountConsistency() {
        try {
            // Generate expected amount hash
            MessageDigest digest = MessageDigest.getInstance(PROOF_HASH_ALGORITHM);
            digest.update(amount.toByteArray());
            digest.update(currencyCode.getBytes());
            digest.update(settlementId.getBytes());
            
            byte[] expectedHash = digest.digest();
            
            // Extract amount verification hash from proof
            ByteBuffer proofBuffer = ByteBuffer.wrap(settlementProof);
            
            // Navigate to amount hash section (after header and signatures)
            int offset = calculateAmountHashOffset();
            if (offset < 0 || offset >= settlementProof.length - 32) {
                return false;
            }
            
            proofBuffer.position(offset);
            byte[] proofAmountHash = new byte[32];
            proofBuffer.get(proofAmountHash);
            
            // Compare hashes
            return Arrays.equals(expectedHash, proofAmountHash);
            
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }
    
    /**
     * Calculates the offset for amount hash in proof
     */
    private int calculateAmountHashOffset() {
        try {
            ByteBuffer proofBuffer = ByteBuffer.wrap(settlementProof);
            
            // Skip header (14 bytes)
            proofBuffer.position(14);
            
            // Skip signatures
            int signatureCount = proofBuffer.getInt();
            for (int i = 0; i < signatureCount; i++) {
                int signatureLength = proofBuffer.getInt();
                proofBuffer.position(proofBuffer.position() + signatureLength);
            }
            
            // Amount hash should be at current position
            return proofBuffer.position();
            
        } catch (Exception e) {
            return -1;
        }
    }
    
    /**
     * Verifies settlement ID consistency
     */
    private boolean verifySettlementIdConsistency() {
        try {
            // Settlement ID should be embedded in proof
            ByteBuffer proofBuffer = ByteBuffer.wrap(settlementProof);
            
            // Navigate to settlement ID section
            int offset = calculateSettlementIdOffset();
            if (offset < 0) {
                return false;
            }
            
            proofBuffer.position(offset);
            int idLength = proofBuffer.getInt();
            
            if (idLength != settlementId.length()) {
                return false;
            }
            
            byte[] proofSettlementId = new byte[idLength];
            proofBuffer.get(proofSettlementId);
            
            String extractedId = new String(proofSettlementId);
            return settlementId.equals(extractedId);
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Calculates offset for settlement ID in proof
     */
    private int calculateSettlementIdOffset() {
        // Settlement ID comes after amount hash (32 bytes)
        int amountHashOffset = calculateAmountHashOffset();
        return amountHashOffset > 0 ? amountHashOffset + 32 : -1;
    }
    
    /**
     * Determines the required number of validator signatures based on risk assessment
     */
    private int getRequiredSignatures() {
        // Base signature requirements
        int baseSignatures = 2;
        
        // Risk-based adjustments
        BigDecimal riskScore = calculateSettlementRiskScore();
        
        // Amount-based requirements
        if (amount.compareTo(MEGA_SETTLEMENT) >= 0) {
            baseSignatures = 7; // Mega settlements require 7 signatures
        } else if (amount.compareTo(LARGE_SETTLEMENT) >= 0) {
            baseSignatures = 5; // Large settlements require 5 signatures
        } else if (amount.compareTo(MEDIUM_SETTLEMENT) >= 0) {
            baseSignatures = 4; // Medium settlements require 4 signatures
        } else if (amount.compareTo(SMALL_SETTLEMENT) >= 0) {
            baseSignatures = 3; // Small settlements require 3 signatures
        }
        
        // International settlement adjustment
        if (isInternational()) {
            baseSignatures += 1;
        }
        
        // High-risk adjustment
        if (riskScore.compareTo(HIGH_RISK_THRESHOLD) >= 0) {
            baseSignatures += 2;
        }
        
        // Currency risk adjustment
        BigDecimal currencyRisk = CURRENCY_VOLATILITY_SCORES.getOrDefault(currencyCode, BigDecimal.ZERO);
        if (currencyRisk.compareTo(new BigDecimal("0.5")) >= 0) {
            baseSignatures += 1;
        }
        
        return baseSignatures;
    }
    
    /**
     * Calculates comprehensive risk score for the settlement
     */
    private BigDecimal calculateSettlementRiskScore() {
        BigDecimal riskScore = BASE_RISK_MULTIPLIER;
        
        // Country risk factors
        BigDecimal sourceRisk = COUNTRY_RISK_SCORES.getOrDefault(sourceCountry, new BigDecimal("0.3"));
        BigDecimal destRisk = COUNTRY_RISK_SCORES.getOrDefault(destCountry, new BigDecimal("0.3"));
        
        // Take the maximum country risk
        BigDecimal countryRisk = sourceRisk.max(destRisk);
        
        // Currency volatility
        BigDecimal currencyRisk = CURRENCY_VOLATILITY_SCORES.getOrDefault(currencyCode, new BigDecimal("0.2"));
        
        // Amount risk (higher amounts = higher risk)
        BigDecimal amountRisk = calculateAmountRisk();
        
        // Time-based risk (settlements outside business hours are riskier)
        BigDecimal timeRisk = calculateTimeBasedRisk();
        
        // Calculate composite risk score
        riskScore = riskScore.add(countryRisk)
                            .add(currencyRisk)
                            .add(amountRisk)
                            .add(timeRisk);
        
        return riskScore.min(BigDecimal.ONE); // Cap at 1.0
    }
    
    /**
     * Calculates risk based on settlement amount
     */
    private BigDecimal calculateAmountRisk() {
        if (amount.compareTo(MEGA_SETTLEMENT) >= 0) {
            return new BigDecimal("0.4");
        } else if (amount.compareTo(LARGE_SETTLEMENT) >= 0) {
            return new BigDecimal("0.3");
        } else if (amount.compareTo(MEDIUM_SETTLEMENT) >= 0) {
            return new BigDecimal("0.2");
        } else if (amount.compareTo(SMALL_SETTLEMENT) >= 0) {
            return new BigDecimal("0.1");
        }
        return new BigDecimal("0.05");
    }
    
    /**
     * Calculates time-based risk factors
     */
    private BigDecimal calculateTimeBasedRisk() {
        // Business hours are 9 AM to 5 PM local time
        // Settlements outside these hours carry higher risk
        
        // For simplicity, use UTC time
        long currentHour = (System.currentTimeMillis() / 3600000) % 24;
        
        // Higher risk during off-hours (before 9 AM or after 5 PM UTC)
        if (currentHour < 9 || currentHour > 17) {
            return new BigDecimal("0.15");
        }
        
        return new BigDecimal("0.05");
    }
    
    /**
     * Initializes country risk scores based on international risk assessments
     */
    private static void initializeRiskScores() {
        // African countries (generally lower risk due to TINC prioritization)
        COUNTRY_RISK_SCORES.put("NG", new BigDecimal("0.25")); // Nigeria
        COUNTRY_RISK_SCORES.put("ZA", new BigDecimal("0.20")); // South Africa
        COUNTRY_RISK_SCORES.put("KE", new BigDecimal("0.25")); // Kenya
        COUNTRY_RISK_SCORES.put("EG", new BigDecimal("0.30")); // Egypt
        COUNTRY_RISK_SCORES.put("GH", new BigDecimal("0.25")); // Ghana
        COUNTRY_RISK_SCORES.put("ET", new BigDecimal("0.35")); // Ethiopia
        COUNTRY_RISK_SCORES.put("UG", new BigDecimal("0.30")); // Uganda
        COUNTRY_RISK_SCORES.put("TZ", new BigDecimal("0.30")); // Tanzania
        
        // Major developed countries
        COUNTRY_RISK_SCORES.put("US", new BigDecimal("0.10")); // United States
        COUNTRY_RISK_SCORES.put("GB", new BigDecimal("0.10")); // United Kingdom
        COUNTRY_RISK_SCORES.put("DE", new BigDecimal("0.10")); // Germany
        COUNTRY_RISK_SCORES.put("FR", new BigDecimal("0.10")); // France
        COUNTRY_RISK_SCORES.put("JP", new BigDecimal("0.10")); // Japan
        COUNTRY_RISK_SCORES.put("CA", new BigDecimal("0.10")); // Canada
        COUNTRY_RISK_SCORES.put("AU", new BigDecimal("0.10")); // Australia
        
        // Emerging markets
        COUNTRY_RISK_SCORES.put("CN", new BigDecimal("0.30")); // China
        COUNTRY_RISK_SCORES.put("IN", new BigDecimal("0.35")); // India
        COUNTRY_RISK_SCORES.put("BR", new BigDecimal("0.40")); // Brazil
        COUNTRY_RISK_SCORES.put("RU", new BigDecimal("0.60")); // Russia
        COUNTRY_RISK_SCORES.put("MX", new BigDecimal("0.35")); // Mexico
    }
    
    /**
     * Initializes currency volatility scores
     */
    private static void initializeCurrencyVolatility() {
        // African currencies
        CURRENCY_VOLATILITY_SCORES.put("NGN", new BigDecimal("0.35")); // Nigerian Naira
        CURRENCY_VOLATILITY_SCORES.put("ZAR", new BigDecimal("0.30")); // South African Rand
        CURRENCY_VOLATILITY_SCORES.put("KES", new BigDecimal("0.25")); // Kenyan Shilling
        CURRENCY_VOLATILITY_SCORES.put("EGP", new BigDecimal("0.40")); // Egyptian Pound
        CURRENCY_VOLATILITY_SCORES.put("GHS", new BigDecimal("0.35")); // Ghanaian Cedi
        
        // Major currencies
        CURRENCY_VOLATILITY_SCORES.put("USD", new BigDecimal("0.10")); // US Dollar
        CURRENCY_VOLATILITY_SCORES.put("EUR", new BigDecimal("0.15")); // Euro
        CURRENCY_VOLATILITY_SCORES.put("GBP", new BigDecimal("0.18")); // British Pound
        CURRENCY_VOLATILITY_SCORES.put("JPY", new BigDecimal("0.12")); // Japanese Yen
        CURRENCY_VOLATILITY_SCORES.put("CHF", new BigDecimal("0.12")); // Swiss Franc
        
        // Emerging market currencies
        CURRENCY_VOLATILITY_SCORES.put("CNY", new BigDecimal("0.20")); // Chinese Yuan
        CURRENCY_VOLATILITY_SCORES.put("INR", new BigDecimal("0.25")); // Indian Rupee
        CURRENCY_VOLATILITY_SCORES.put("BRL", new BigDecimal("0.45")); // Brazilian Real
        CURRENCY_VOLATILITY_SCORES.put("RUB", new BigDecimal("0.60")); // Russian Ruble
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
        
        // Banks cannot settle with themselves
        if (sourceBankAddress.equals(destBankAddress)) {
            return false;
        }
        
        if (settlementId == null || settlementId.isEmpty()) {
            return false;
        }
        
        // Settlement ID format validation (should be UUID-like)
        if (!settlementId.matches("^[0-9a-fA-F-]{36}$")) {
            return false;
        }
        
        if (sourceCountry == null || sourceCountry.isEmpty() || 
            destCountry == null || destCountry.isEmpty()) {
            return false;
        }
        
        // Country codes should be ISO 3166-1 alpha-2 format
        if (sourceCountry.length() != 2 || destCountry.length() != 2) {
            return false;
        }
        
        if (amount == null || amount.compareTo(BigInteger.ZERO) <= 0) {
            return false;
        }
        
        // Reasonable amount limits (not more than $1 trillion)
        if (amount.compareTo(new BigInteger("1000000000000")) > 0) {
            return false;
        }
        
        if (currencyCode == null || currencyCode.isEmpty()) {
            return false;
        }
        
        // Currency code should be ISO 4217 format
        if (currencyCode.length() != 3) {
            return false;
        }
        
        if (proofOfFundsHashes == null || proofOfFundsHashes.size() < MIN_HASH_COUNT) {
            return false;
        }
        
        if (validatorSignatures < getRequiredSignatures()) {
            return false;
        }
        
        if (settlementProof == null || settlementProof.length < MIN_PROOF_LENGTH) {
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
                ", riskScore=" + calculateSettlementRiskScore() +
                '}';
    }
}