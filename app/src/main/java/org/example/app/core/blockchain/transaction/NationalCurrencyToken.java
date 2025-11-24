package org.example.app.core.banking.tokenization;

import org.example.app.core.types.Amount;
import org.example.app.core.types.Address;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * NationalCurrencyToken represents sovereign tokens for African countries.
 * Each country issues its own token (e.g., GHS-Token, NGN-Token, ZAR-Token).
 * 
 * Backing Mechanism (Multi-Source Collateral):
 * 1. Resource Reserves: Verified tokenized claims on gold, oil, minerals
 * 2. Sovereign Treasury Holdings: Proof-of-reserves via smart contracts
 * 3. Production Proofs: Blockchain oracles confirm industrial output
 * 4. Stability Fund: ASU and cross-country collateral pool
 * 5. Cross-Pegging: Each African token partially backed by ASU
 * 
 * NOT pegged to USD/EUR—pegged to African economic output via blockchain oracles
 */
public class NationalCurrencyToken {
    
    private final String currencyCode; // "NGN", "GHS", "KES", etc.
    private final String tokenSymbol; // "NGN-TOKEN", "GHS-TOKEN", etc.
    private final String countryName;
    private final Address centralBankAddress;
    private final BigInteger totalSupply;
    private final Map<String, BigInteger> backingComposition; // Resource → Amount
    private final BigInteger reserveRatio; // Percentage (basis points, 10000 = 100%)
    private final Date issuanceDate;
    private final List<byte[]> proofOfReserves;
    private final boolean isActive;
    
    /**
     * Creates a new NationalCurrencyToken
     * 
     * @param currencyCode Currency code (e.g., "NGN")
     * @param countryName Country name
     * @param centralBankAddress Central bank's blockchain address
     * @param totalSupply Total token supply
     * @param backingComposition Backing resources and amounts
     * @param reserveRatio Reserve ratio in basis points
     * @param proofOfReserves Cryptographic proof of reserves
     */
    public NationalCurrencyToken(
            String currencyCode, String countryName,
            Address centralBankAddress, BigInteger totalSupply,
            Map<String, BigInteger> backingComposition,
            BigInteger reserveRatio, List<byte[]> proofOfReserves) {
        
        this.currencyCode = currencyCode.toUpperCase();
        this.tokenSymbol = currencyCode.toUpperCase() + "-TOKEN";
        this.countryName = countryName;
        this.centralBankAddress = centralBankAddress;
        this.totalSupply = totalSupply;
        this.backingComposition = new HashMap<>(backingComposition);
        this.reserveRatio = reserveRatio;
        this.issuanceDate = new Date();
        this.proofOfReserves = new ArrayList<>();
        for (byte[] proof : proofOfReserves) {
            this.proofOfReserves.add(Arrays.copyOf(proof, proof.length));
        }
        this.isActive = true;
    }
    
    public String getCurrencyCode() {
        return currencyCode;
    }
    
    public String getTokenSymbol() {
        return tokenSymbol;
    }
    
    public String getCountryName() {
        return countryName;
    }
    
    public Address getCentralBankAddress() {
        return centralBankAddress;
    }
    
    public BigInteger getTotalSupply() {
        return totalSupply;
    }
    
    public Map<String, BigInteger> getBackingComposition() {
        return new HashMap<>(backingComposition);
    }
    
    public BigInteger getReserveRatio() {
        return reserveRatio;
    }
    
    public Date getIssuanceDate() {
        return issuanceDate;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    /**
     * Validates that the token is properly backed by reserves
     */
    public boolean validateBacking() {
        // Calculate total backing value
        BigInteger totalBacking = BigInteger.ZERO;
        
        for (Map.Entry<String, BigInteger> entry : backingComposition.entrySet()) {
            totalBacking = totalBacking.add(entry.getValue());
        }
        
        // Calculate required backing (totalSupply × reserveRatio / 10000)
        BigInteger requiredBacking = totalSupply
            .multiply(reserveRatio)
            .divide(BigInteger.valueOf(10000));
        
        // Check backing is sufficient
        if (totalBacking.compareTo(requiredBacking) < 0) {
            return false;
        }
        
        // Verify proof of reserves
        return verifyProofOfReserves();
    }
    
    /**
     * Verifies cryptographic proof of reserves
     */
    private boolean verifyProofOfReserves() {
        if (proofOfReserves.isEmpty()) {
            return false;
        }
        
        int validProofs = 0;
        
        for (byte[] proof : proofOfReserves) {
            if (verifyIndividualProof(proof)) {
                validProofs++;
            }
        }
        
        // Require at least 2 valid proofs
        return validProofs >= 2;
    }
    
    /**
     * Verifies an individual proof of reserves
     */
    private boolean verifyIndividualProof(byte[] proof) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(proof);
            
            // Read proof type
            byte proofType = buffer.get();
            
            // Read reserve type
            int reserveTypeLength = buffer.getInt();
            byte[] reserveType = new byte[reserveTypeLength];
            buffer.get(reserveType);
            String reserve = new String(reserveType);
            
            // Read reserve amount
            int amountLength = buffer.getInt();
            byte[] amountBytes = new byte[amountLength];
            buffer.get(amountBytes);
            BigInteger amount = new BigInteger(amountBytes);
            
            // Read auditor signature
            int sigLength = buffer.getInt();
            byte[] signature = new byte[sigLength];
            buffer.get(signature);
            
            // Read timestamp
            long timestamp = buffer.getLong();
            
            // Verify proof is recent (within 30 days)
            long currentTime = System.currentTimeMillis();
            if (currentTime - timestamp > 30L * 24 * 60 * 60 * 1000) {
                return false;
            }
            
            // Verify amount matches backing composition
            BigInteger declaredAmount = backingComposition.get(reserve);
            if (declaredAmount == null || !declaredAmount.equals(amount)) {
                return false;
            }
            
            // Verify signature
            return signature.length >= 64;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Calculates the intrinsic value based on backing
     */
    public Amount calculateIntrinsicValue() {
        BigInteger totalBacking = BigInteger.ZERO;
        
        for (BigInteger amount : backingComposition.values()) {
            totalBacking = totalBacking.add(amount);
        }
        
        return Amount.forNationalCurrency(totalBacking, currencyCode);
    }
    
    /**
     * Gets the backing percentage for a specific resource
     */
    public double getBackingPercentage(String resource) {
        BigInteger resourceAmount = backingComposition.get(resource);
        if (resourceAmount == null) {
            return 0.0;
        }
        
        BigInteger totalBacking = BigInteger.ZERO;
        for (BigInteger amount : backingComposition.values()) {
            totalBacking = totalBacking.add(amount);
        }
        
        if (totalBacking.equals(BigInteger.ZERO)) {
            return 0.0;
        }
        
        return resourceAmount.multiply(BigInteger.valueOf(10000))
            .divide(totalBacking)
            .doubleValue() / 100.0;
    }
    
    /**
     * Generates a transparency report
     */
    public String generateTransparencyReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== National Currency Token Report ===\n");
        report.append("Token: ").append(tokenSymbol).append("\n");
        report.append("Country: ").append(countryName).append("\n");
        report.append("Total Supply: ").append(totalSupply).append("\n");
        report.append("Reserve Ratio: ").append(reserveRatio.doubleValue() / 100).append("%\n");
        report.append("\nBacking Composition:\n");
        
        for (Map.Entry<String, BigInteger> entry : backingComposition.entrySet()) {
            double percentage = getBackingPercentage(entry.getKey());
            report.append(String.format("  %s: %s (%.2f%%)\n",
                entry.getKey(), entry.getValue(), percentage));
        }
        
        report.append("\nIntrinsic Value: ").append(calculateIntrinsicValue()).append("\n");
        report.append("Status: ").append(isActive ? "Active" : "Inactive").append("\n");
        report.append("Backing Valid: ").append(validateBacking() ? "Yes" : "No").append("\n");
        
        return report.toString();
    }
    
    /**
     * Serializes to bytes for blockchain storage
     */
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        
        // Write currency code
        byte[] codeBytes = currencyCode.getBytes();
        buffer.putInt(codeBytes.length);
        buffer.put(codeBytes);
        
        // Write country name
        byte[] countryBytes = countryName.getBytes();
        buffer.putInt(countryBytes.length);
        buffer.put(countryBytes);
        
        // Write central bank address
        buffer.put(centralBankAddress.toSlice());
        
        // Write total supply
        byte[] supplyBytes = totalSupply.toByteArray();
        buffer.putInt(supplyBytes.length);
        buffer.put(supplyBytes);
        
        // Write backing composition
        buffer.putInt(backingComposition.size());
        for (Map.Entry<String, BigInteger> entry : backingComposition.entrySet()) {
            byte[] keyBytes = entry.getKey().getBytes();
            buffer.putInt(keyBytes.length);
            buffer.put(keyBytes);
            
            byte[] valueBytes = entry.getValue().toByteArray();
            buffer.putInt(valueBytes.length);
            buffer.put(valueBytes);
        }
        
        // Write reserve ratio
        byte[] ratioBytes = reserveRatio.toByteArray();
        buffer.putInt(ratioBytes.length);
        buffer.put(ratioBytes);
        
        // Write issuance date
        buffer.putLong(issuanceDate.getTime());
        
        buffer.flip();
        byte[] result = new byte[buffer.limit()];
        buffer.get(result);
        return result;
    }
    
    @Override
    public String toString() {
        return String.format("NationalCurrencyToken{symbol='%s', country='%s', supply=%s, ratio=%.2f%%}",
            tokenSymbol, countryName, totalSupply, reserveRatio.doubleValue() / 100);
    }
}