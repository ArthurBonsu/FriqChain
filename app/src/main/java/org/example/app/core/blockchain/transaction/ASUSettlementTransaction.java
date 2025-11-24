package org.example.app.core.blockchain.transaction;

import org.example.app.core.types.Address;
import org.example.app.core.types.Amount;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * ASUSettlementTransaction represents inter-country trade settlement using
 * the African Stable Unit (ASU). This is the core mechanism for removing
 * dependency on USD-based settlement (no SWIFT, no IMF clearing).
 * 
 * The ASU is backed by a weighted basket:
 * - African currency tokens (major weight)
 * - Gold/oil/agricultural commodities (collateralized)
 * - Small crypto reserve buffer (≤10% for global liquidity)
 * 
 * Formula: ASU = Σ(α × Currency) + Σ(β × Commodity) + (γ × Crypto_reserve)
 */
public class ASUSettlementTransaction extends BaseTransaction {
    
    private final Address senderAddress;
    private final Address recipientAddress;
    private final String senderCountry;
    private final String recipientCountry;
    private final Amount asuAmount;
    private final Map<String, BigInteger> basketComposition; // Currency → Weight
    private final String settlementId;
    private final byte[] proofOfReserves; // Cryptographic proof of backing
    private final List<String> oracleSignatures; // Oracle validation signatures
    private final boolean isIntraAfrican; // True if both parties are African entities
    
    /**
     * Creates a new ASUSettlementTransaction
     * 
     * @param senderAddress Sender's blockchain address
     * @param recipientAddress Recipient's blockchain address
     * @param senderCountry Sender's country code (e.g., "NGN", "GHS")
     * @param recipientCountry Recipient's country code
     * @param asuAmount Amount in ASU
     * @param basketComposition Current ASU basket weights
     * @param proofOfReserves Proof that settlement is backed by reserves
     * @param oracleSignatures Oracle validation signatures
     */
    public ASUSettlementTransaction(
            Address senderAddress, Address recipientAddress,
            String senderCountry, String recipientCountry,
            Amount asuAmount, Map<String, BigInteger> basketComposition,
            byte[] proofOfReserves, List<String> oracleSignatures) {
        
        super(buildTransactionData(senderAddress, recipientAddress, senderCountry,
                recipientCountry, asuAmount, basketComposition, proofOfReserves,
                oracleSignatures), TransactionType.ASU_SETTLEMENT);
        
        this.senderAddress = senderAddress;
        this.recipientAddress = recipientAddress;
        this.senderCountry = senderCountry;
        this.recipientCountry = recipientCountry;
        this.asuAmount = asuAmount;
        this.basketComposition = new HashMap<>(basketComposition);
        this.settlementId = "ASU-" + UUID.randomUUID().toString();
        this.proofOfReserves = proofOfReserves != null ? 
            Arrays.copyOf(proofOfReserves, proofOfReserves.length) : new byte[0];
        this.oracleSignatures = new ArrayList<>(oracleSignatures);
        this.isIntraAfrican = isAfricanCountry(senderCountry) && isAfricanCountry(recipientCountry);
    }
    
    /**
     * Builds the transaction data byte array
     */
    private static byte[] buildTransactionData(
            Address senderAddress, Address recipientAddress,
            String senderCountry, String recipientCountry,
            Amount asuAmount, Map<String, BigInteger> basketComposition,
            byte[] proofOfReserves, List<String> oracleSignatures) {
        
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        
        // Write addresses
        buffer.put(senderAddress.toSlice());
        buffer.put(recipientAddress.toSlice());
        
        // Write country codes
        byte[] senderCountryBytes = senderCountry.getBytes();
        buffer.putInt(senderCountryBytes.length);
        buffer.put(senderCountryBytes);
        
        byte[] recipientCountryBytes = recipientCountry.getBytes();
        buffer.putInt(recipientCountryBytes.length);
        buffer.put(recipientCountryBytes);
        
        // Write ASU amount
        byte[] amountBytes = asuAmount.toBytes();
        buffer.putInt(amountBytes.length);
        buffer.put(amountBytes);
        
        // Write basket composition
        buffer.putInt(basketComposition.size());
        for (Map.Entry<String, BigInteger> entry : basketComposition.entrySet()) {
            byte[] currencyBytes = entry.getKey().getBytes();
            buffer.putInt(currencyBytes.length);
            buffer.put(currencyBytes);
            
            byte[] weightBytes = entry.getValue().toByteArray();
            buffer.putInt(weightBytes.length);
            buffer.put(weightBytes);
        }
        
        // Write proof of reserves
        buffer.putInt(proofOfReserves.length);
        buffer.put(proofOfReserves);
        
        // Write oracle signatures
        buffer.putInt(oracleSignatures.size());
        for (String signature : oracleSignatures) {
            byte[] sigBytes = signature.getBytes();
            buffer.putInt(sigBytes.length);
            buffer.put(sigBytes);
        }
        
        buffer.flip();
        byte[] result = new byte[buffer.limit()];
        buffer.get(result);
        return result;
    }
    
    @Override
    public Address getSenderAddress() {
        return senderAddress;
    }
    
    @Override
    public Address getRecipientAddress() {
        return recipientAddress;
    }
    
    @Override
    public Amount getAmount() {
        return asuAmount;
    }
    
    public String getSenderCountry() {
        return senderCountry;
    }
    
    public String getRecipientCountry() {
        return recipientCountry;
    }
    
    public String getSettlementId() {
        return settlementId;
    }
    
    public Map<String, BigInteger> getBasketComposition() {
        return new HashMap<>(basketComposition);
    }
    
    public byte[] getProofOfReserves() {
        return Arrays.copyOf(proofOfReserves, proofOfReserves.length);
    }
    
    public List<String> getOracleSignatures() {
        return new ArrayList<>(oracleSignatures);
    }
    
    public boolean isIntraAfrican() {
        return isIntraAfrican;
    }
    
    @Override
    public boolean isCrossBorder() {
        return !senderCountry.equals(recipientCountry);
    }
    
    /**
     * Checks if a country code represents an African country
     */
    private static boolean isAfricanCountry(String countryCode) {
        String[] africanCodes = {
            "NGN", "GHS", "KES", "ZAR", "EGP", "MAD", "TZS", "UGX", 
            "XOF", "XAF", "ETB", "RWF", "ZMW", "BWP", "MUR", "GMD"
        };
        
        for (String code : africanCodes) {
            if (countryCode.toUpperCase().startsWith(code)) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    protected boolean validateTransactionRules() {
        // Validate ASU amount
        if (!asuAmount.isASU()) {
            return false;
        }
        
        // Validate country codes
        if (senderCountry == null || senderCountry.isEmpty() ||
            recipientCountry == null || recipientCountry.isEmpty()) {
            return false;
        }
        
        // Validate basket composition
        if (basketComposition == null || basketComposition.isEmpty()) {
            return false;
        }
        
        // Validate basket weights sum to 100% (represented as 1000000 for precision)
        BigInteger totalWeight = BigInteger.ZERO;
        for (BigInteger weight : basketComposition.values()) {
            totalWeight = totalWeight.add(weight);
        }
        
        // Allow 0.1% tolerance for rounding
        BigInteger expectedTotal = BigInteger.valueOf(1000000);
        BigInteger tolerance = BigInteger.valueOf(1000);
        if (totalWeight.subtract(expectedTotal).abs().compareTo(tolerance) > 0) {
            return false;
        }
        
        // Validate proof of reserves
        if (proofOfReserves == null || proofOfReserves.length == 0) {
            return false;
        }
        
        // Validate oracle signatures (minimum 3 required)
        if (oracleSignatures == null || oracleSignatures.size() < 3) {
            return false;
        }
        
        // Verify proof of reserves
        return verifyProofOfReserves();
    }
    
    /**
     * Verifies the cryptographic proof that this ASU amount is backed by reserves
     */
    private boolean verifyProofOfReserves() {
        try {
            // Extract proof components
            ByteBuffer buffer = ByteBuffer.wrap(proofOfReserves);
            
            // Read timestamp
            long timestamp = buffer.getLong();
            
            // Check timestamp is recent (within last hour)
            long currentTime = System.currentTimeMillis();
            if (currentTime - timestamp > 3600000) {
                return false; // Proof too old
            }
            
            // Read reserve hash
            byte[] reserveHash = new byte[32];
            buffer.get(reserveHash);
            
            // Read merkle root of basket composition
            byte[] basketMerkleRoot = new byte[32];
            buffer.get(basketMerkleRoot);
            
            // Verify oracle signatures on the proof
            int validSignatures = 0;
            for (String signature : oracleSignatures) {
                if (verifyOracleSignature(signature, reserveHash, basketMerkleRoot)) {
                    validSignatures++;
                }
            }
            
            // Require at least 3 valid oracle signatures
            return validSignatures >= 3;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Verifies a single oracle signature
     */
    private boolean verifyOracleSignature(String signature, byte[] reserveHash, byte[] basketRoot) {
        // In production, this would verify the signature using the oracle's public key
        // For now, we'll simulate verification
        
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        
        // Simulate signature verification
        return signature.length() >= 64; // Basic validation
    }
    
    /**
     * Calculates the equivalent value in a specific national currency
     */
    public Amount convertToNationalCurrency(String currencyCode) {
        // Get the weight of this currency in the basket
        BigInteger weight = basketComposition.get(currencyCode + "-TOKEN");
        
        if (weight == null) {
            throw new IllegalArgumentException("Currency not in ASU basket: " + currencyCode);
        }
        
        // Calculate: nationalAmount = asuAmount × (weight / totalWeight)
        BigInteger totalWeight = BigInteger.valueOf(1000000); // 100% in basis points
        BigInteger asuValue = asuAmount.getValue();
        
        BigInteger nationalValue = asuValue.multiply(weight).divide(totalWeight);
        
        return Amount.forNationalCurrency(nationalValue, currencyCode);
    }
    
    /**
     * Gets the required fee for ASU settlement
     * Lower fees for intra-African trade to encourage African trade ecosystem
     */
    @Override
    public Amount getRequiredFee() {
        if (isIntraAfrican) {
            // 0.1% fee for intra-African trade
            BigInteger fee = asuAmount.getValue().divide(BigInteger.valueOf(1000));
            return Amount.forASU(fee);
        } else {
            // 0.5% fee for external trade
            BigInteger fee = asuAmount.getValue().divide(BigInteger.valueOf(200));
            return Amount.forASU(fee);
        }
    }
    
    @Override
    public String getDescription() {
        return String.format("ASU Settlement: %s from %s (%s) to %s (%s)",
            asuAmount.toFormattedString(),
            senderAddress.toString().substring(0, 8) + "...",
            senderCountry,
            recipientAddress.toString().substring(0, 8) + "...",
            recipientCountry
        );
    }
    
    @Override
    public String toString() {
        return String.format("ASUSettlementTransaction{id='%s', amount=%s, sender=%s, recipient=%s, intraAfrican=%b}",
            settlementId, asuAmount, senderCountry, recipientCountry, isIntraAfrican);
    }
}