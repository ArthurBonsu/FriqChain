package org.example.app.core.blockchain.transaction;

import org.example.app.core.types.Address;
import org.example.app.core.types.Amount;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * ExportTokenTransaction represents goods tokenized into TradeReceipts upon export.
 * This is critical for the African Tokenized Currency System's defense mechanism:
 * "Keeps trade moving even when foreign buyers delay or refuse payment -
 * African markets can absorb export tokens domestically"
 * 
 * Export tokens are convertible to ASU or national tokens, creating internal
 * liquidity even when external markets are closed or manipulative.
 */
public class ExportTokenTransaction extends BaseTransaction {
    
    private final Address exporterAddress;
    private final Address recipientAddress; // Can be buyer or domestic market
    private final String exporterCountry;
    private final String commodityType; // "GOLD", "OIL", "COCOA", "COFFEE", etc.
    private final Amount tokenAmount;
    private final byte[] tradeReceipt; // Bill of Lading or shipping receipt
    private final byte[] customsSignature; // Customs authority signature
    private final String portOfOrigin;
    private final String destination;
    private final Date shipmentDate;
    private final boolean isDomesticRedemption; // True if being redeemed locally
    private final List<byte[]> verificationProofs; // Multi-source verification
    
    /**
     * Creates a new ExportTokenTransaction
     * 
     * @param exporterAddress Exporter's blockchain address
     * @param recipientAddress Recipient (buyer or domestic market)
     * @param exporterCountry Exporter's country
     * @param commodityType Type of commodity being exported
     * @param tokenAmount Amount of export tokens
     * @param tradeReceipt Verified trade receipt (Bill of Lading)
     * @param customsSignature Customs authority signature
     * @param portOfOrigin Port of origin
     * @param destination Destination port/country
     * @param isDomesticRedemption Whether this is domestic redemption
     * @param verificationProofs Additional verification proofs
     */
    public ExportTokenTransaction(
            Address exporterAddress, Address recipientAddress,
            String exporterCountry, String commodityType,
            Amount tokenAmount, byte[] tradeReceipt,
            byte[] customsSignature, String portOfOrigin,
            String destination, boolean isDomesticRedemption,
            List<byte[]> verificationProofs) {
        
        super(buildTransactionData(exporterAddress, recipientAddress, exporterCountry,
                commodityType, tokenAmount, tradeReceipt, customsSignature, portOfOrigin,
                destination, isDomesticRedemption, verificationProofs), 
                TransactionType.EXPORT_TOKEN);
        
        this.exporterAddress = exporterAddress;
        this.recipientAddress = recipientAddress;
        this.exporterCountry = exporterCountry;
        this.commodityType = commodityType.toUpperCase();
        this.tokenAmount = tokenAmount;
        this.tradeReceipt = Arrays.copyOf(tradeReceipt, tradeReceipt.length);
        this.customsSignature = Arrays.copyOf(customsSignature, customsSignature.length);
        this.portOfOrigin = portOfOrigin;
        this.destination = destination;
        this.shipmentDate = new Date();
        this.isDomesticRedemption = isDomesticRedemption;
        this.verificationProofs = new ArrayList<>();
        for (byte[] proof : verificationProofs) {
            this.verificationProofs.add(Arrays.copyOf(proof, proof.length));
        }
    }
    
    /**
     * Builds the transaction data byte array
     */
    private static byte[] buildTransactionData(
            Address exporterAddress, Address recipientAddress,
            String exporterCountry, String commodityType,
            Amount tokenAmount, byte[] tradeReceipt,
            byte[] customsSignature, String portOfOrigin,
            String destination, boolean isDomesticRedemption,
            List<byte[]> verificationProofs) {
        
        ByteBuffer buffer = ByteBuffer.allocate(16384); // Large buffer for proofs
        
        // Write addresses
        buffer.put(exporterAddress.toSlice());
        buffer.put(recipientAddress.toSlice());
        
        // Write exporter country
        byte[] countryBytes = exporterCountry.getBytes();
        buffer.putInt(countryBytes.length);
        buffer.put(countryBytes);
        
        // Write commodity type
        byte[] commodityBytes = commodityType.getBytes();
        buffer.putInt(commodityBytes.length);
        buffer.put(commodityBytes);
        
        // Write token amount
        byte[] amountBytes = tokenAmount.toBytes();
        buffer.putInt(amountBytes.length);
        buffer.put(amountBytes);
        
        // Write trade receipt
        buffer.putInt(tradeReceipt.length);
        buffer.put(tradeReceipt);
        
        // Write customs signature
        buffer.putInt(customsSignature.length);
        buffer.put(customsSignature);
        
        // Write port of origin
        byte[] originBytes = portOfOrigin.getBytes();
        buffer.putInt(originBytes.length);
        buffer.put(originBytes);
        
        // Write destination
        byte[] destBytes = destination.getBytes();
        buffer.putInt(destBytes.length);
        buffer.put(destBytes);
        
        // Write domestic redemption flag
        buffer.put((byte) (isDomesticRedemption ? 1 : 0));
        
        // Write verification proofs
        buffer.putInt(verificationProofs.size());
        for (byte[] proof : verificationProofs) {
            buffer.putInt(proof.length);
            buffer.put(proof);
        }
        
        buffer.flip();
        byte[] result = new byte[buffer.limit()];
        buffer.get(result);
        return result;
    }
    
    @Override
    public Address getSenderAddress() {
        return exporterAddress;
    }
    
    @Override
    public Address getRecipientAddress() {
        return recipientAddress;
    }
    
    @Override
    public Amount getAmount() {
        return tokenAmount;
    }
    
    public String getExporterCountry() {
        return exporterCountry;
    }
    
    public String getCommodityType() {
        return commodityType;
    }
    
    public byte[] getTradeReceipt() {
        return Arrays.copyOf(tradeReceipt, tradeReceipt.length);
    }
    
    public byte[] getCustomsSignature() {
        return Arrays.copyOf(customsSignature, customsSignature.length);
    }
    
    public String getPortOfOrigin() {
        return portOfOrigin;
    }
    
    public String getDestination() {
        return destination;
    }
    
    public Date getShipmentDate() {
        return shipmentDate;
    }
    
    public boolean isDomesticRedemption() {
        return isDomesticRedemption;
    }
    
    public List<byte[]> getVerificationProofs() {
        List<byte[]> copies = new ArrayList<>();
        for (byte[] proof : verificationProofs) {
            copies.add(Arrays.copyOf(proof, proof.length));
        }
        return copies;
    }
    
    @Override
    public boolean isCrossBorder() {
        return !isDomesticRedemption;
    }
    
    @Override
    protected boolean validateTransactionRules() {
        // Validate commodity type
        if (!isValidCommodityType(commodityType)) {
            return false;
        }
        
        // Validate token is export token
        if (!tokenAmount.isExportToken()) {
            return false;
        }
        
        // Validate trade receipt
        if (tradeReceipt == null || tradeReceipt.length == 0) {
            return false;
        }
        
        // Validate customs signature
        if (customsSignature == null || customsSignature.length == 0) {
            return false;
        }
        
        // Validate ports
        if (portOfOrigin == null || portOfOrigin.isEmpty() ||
            destination == null || destination.isEmpty()) {
            return false;
        }
        
        // Require minimum verification proofs
        // For export fraud prevention: minimum 2 sources required
        if (verificationProofs == null || verificationProofs.size() < 2) {
            return false;
        }
        
        // Verify trade receipt authenticity
        if (!verifyTradeReceipt()) {
            return false;
        }
        
        // Verify customs signature
        if (!verifyCustomsSignature()) {
            return false;
        }
        
        // Verify additional proofs (satellite, IoT, etc.)
        return verifyAdditionalProofs();
    }
    
    /**
     * Checks if commodity type is valid
     */
    private boolean isValidCommodityType(String type) {
        String[] validTypes = {
            "GOLD", "OIL", "COCOA", "COFFEE", "COTTON", "COPPER",
            "DIAMONDS", "RUBBER", "TIMBER", "AGRICULTURAL", "MINERALS"
        };
        
        for (String valid : validTypes) {
            if (type.equals(valid)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Verifies the trade receipt (Bill of Lading) authenticity
     * Defense against "Export Fraud (Fake trade tokens)"
     */
    private boolean verifyTradeReceipt() {
        try {
            // Parse trade receipt
            ByteBuffer buffer = ByteBuffer.wrap(tradeReceipt);
            
            // Read receipt header
            byte[] magic = new byte[8];
            buffer.get(magic);
            if (!Arrays.equals(magic, "TINCTRAD".getBytes())) {
                return false;
            }
            
            // Read bill of lading number
            int bolLength = buffer.getInt();
            byte[] bolNumber = new byte[bolLength];
            buffer.get(bolNumber);
            
            // Read shipping line signature
            int sigLength = buffer.getInt();
            byte[] shippingSignature = new byte[sigLength];
            buffer.get(shippingSignature);
            
            // Verify shipping signature
            // In production, validate against shipping line's public key
            if (shippingSignature.length < 64) {
                return false;
            }
            
            // Read container/cargo hash
            byte[] cargoHash = new byte[32];
            buffer.get(cargoHash);
            
            // Verify cargo hash matches customs declaration
            // In production, cross-reference with customs database
            
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Verifies the customs authority signature
     * Ensures ExportTokens require verifiable trade receipts signed by customs
     */
    private boolean verifyCustomsSignature() {
        try {
            // Parse customs signature
            ByteBuffer buffer = ByteBuffer.wrap(customsSignature);
            
            // Read customs authority ID
            int authorityIdLength = buffer.getInt();
            byte[] authorityId = new byte[authorityIdLength];
            buffer.get(authorityId);
            
            // Read digital signature
            int sigLength = buffer.getInt();
            byte[] signature = new byte[sigLength];
            buffer.get(signature);
            
            // Read timestamp
            long timestamp = buffer.getLong();
            
            // Verify signature is recent (within 7 days of shipment)
            long currentTime = System.currentTimeMillis();
            if (currentTime - timestamp > 7 * 24 * 60 * 60 * 1000L) {
                return false;
            }
            
            // In production, verify signature against customs authority's public key
            // registered on blockchain
            
            return signature.length >= 64;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Verifies additional proofs from multiple sources
     * Implements: "Multi-source oracles (customs, satellite, banks)"
     */
    private boolean verifyAdditionalProofs() {
        int validProofs = 0;
        
        for (byte[] proof : verificationProofs) {
            if (verifyIndividualProof(proof)) {
                validProofs++;
            }
        }
        
        // Require at least 2 valid proofs from different sources
        return validProofs >= 2;
    }
    
    /**
     * Verifies an individual proof
     */
    private boolean verifyIndividualProof(byte[] proof) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(proof);
            
            // Read proof type
            byte proofType = buffer.get();
            
            // Read proof data
            int dataLength = buffer.getInt();
            byte[] proofData = new byte[dataLength];
            buffer.get(proofData);
            
            // Read signature
            int sigLength = buffer.getInt();
            byte[] signature = new byte[sigLength];
            buffer.get(signature);
            
            // Verify based on proof type
            switch (proofType) {
                case 0x01: // Satellite imagery proof
                    return verifySatelliteProof(proofData, signature);
                case 0x02: // IoT container sensor proof
                    return verifyIoTProof(proofData, signature);
                case 0x03: // Bank settlement confirmation
                    return verifyBankProof(proofData, signature);
                default:
                    return false;
            }
            
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean verifySatelliteProof(byte[] data, byte[] signature) {
        // Verify satellite imagery confirms ship at port
        // In production: validate signature from satellite oracle
        return data.length > 32 && signature.length >= 64;
    }
    
    private boolean verifyIoTProof(byte[] data, byte[] signature) {
        // Verify IoT sensors confirm container movement
        // In production: validate signature from IoT oracle
        return data.length > 32 && signature.length >= 64;
    }
    
    private boolean verifyBankProof(byte[] data, byte[] signature) {
        // Verify bank settlement feeds confirm transaction
        // In production: validate signature from bank oracle
        return data.length > 32 && signature.length >= 64;
    }
    
    /**
     * Converts export token to ASU or national currency
     * Implements: "Convertible to ASU or national tokens"
     */
    public Amount convertToASU(BigInteger conversionRate) {
        BigInteger asuValue = tokenAmount.getValue()
            .multiply(conversionRate)
            .divide(BigInteger.valueOf(1000000)); // Rate precision
        
        return Amount.forASU(asuValue);
    }
    
    public Amount convertToNationalCurrency(String currencyCode, BigInteger conversionRate) {
        BigInteger nationalValue = tokenAmount.getValue()
            .multiply(conversionRate)
            .divide(BigInteger.valueOf(1000000));
        
        return Amount.forNationalCurrency(nationalValue, currencyCode);
    }
    
    @Override
    public Amount getRequiredFee() {
        // Lower fees for domestic redemption to encourage internal liquidity
        if (isDomesticRedemption) {
            BigInteger fee = tokenAmount.getValue().divide(BigInteger.valueOf(1000)); // 0.1%
            return new Amount(fee, tokenAmount.getCurrency(), tokenAmount.getDecimals());
        } else {
            BigInteger fee = tokenAmount.getValue().divide(BigInteger.valueOf(500)); // 0.2%
            return new Amount(fee, tokenAmount.getCurrency(), tokenAmount.getDecimals());
        }
    }
    
    @Override
    public String getDescription() {
        return String.format("Export Token: %s %s from %s to %s %s",
            tokenAmount.toDecimalString(),
            commodityType,
            portOfOrigin,
            destination,
            isDomesticRedemption ? "(Domestic Redemption)" : ""
        );
    }
    
    @Override
    public String toString() {
        return String.format("ExportTokenTransaction{commodity='%s', amount=%s, origin='%s', dest='%s', domestic=%b}",
            commodityType, tokenAmount, portOfOrigin, destination, isDomesticRedemption);
    }
}