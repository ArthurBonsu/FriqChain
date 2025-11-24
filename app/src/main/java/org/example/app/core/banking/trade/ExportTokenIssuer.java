package org.example.app.core.banking.trade;

import org.example.app.core.types.Address;
import org.example.app.core.types.Amount;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * ExportTokenIssuer creates and issues export tokens upon verification
 * of actual shipments. Export tokens are convertible to ASU or national tokens,
 * enabling domestic market absorption when external buyers refuse payment.
 */
public class ExportTokenIssuer {
    private static final Logger LOGGER = Logger.getLogger(ExportTokenIssuer.class.getName());
    
    private final Map<String, IssuedToken> issuedTokens = new ConcurrentHashMap<>();
    private final TradeReceiptVerifier receiptVerifier;
    private final CustomsSignatureValidator customsValidator;
    
    public ExportTokenIssuer(TradeReceiptVerifier receiptVerifier, 
                            CustomsSignatureValidator customsValidator) {
        this.receiptVerifier = receiptVerifier;
        this.customsValidator = customsValidator;
    }
    
    /**
     * Issues export tokens after verifying shipment documents
     */
    public IssuedToken issueExportToken(String commodityType, Amount quantity, 
                                        byte[] billOfLading, byte[] customsSignature,
                                        String portOfOrigin, String destination) throws ExportException {
        try {
            // Verify trade receipt
            if (!receiptVerifier.verifyTradeReceipt(billOfLading)) {
                throw new ExportException("Invalid trade receipt");
            }
            
            // Verify customs signature
            if (!customsValidator.validateSignature(customsSignature, billOfLading)) {
                throw new ExportException("Invalid customs signature");
            }
            
            // Create export token
            String tokenId = UUID.randomUUID().toString();
            IssuedToken token = new IssuedToken(
                tokenId,
                commodityType,
                quantity,
                portOfOrigin,
                destination,
                System.currentTimeMillis()
            );
            
            issuedTokens.put(tokenId, token);
            LOGGER.info("Export token issued: " + tokenId + " - " + quantity + " " + commodityType);
            
            return token;
        } catch (ExportException e) {
            throw e;
        } catch (Exception e) {
            throw new ExportException("Token issuance failed: " + e.getMessage());
        }
    }
    
    /**
     * Issues bulk export tokens for multiple shipments
     */
    public List<IssuedToken> issueBulkExportTokens(List<ExportShipment> shipments) throws ExportException {
        List<IssuedToken> tokens = new ArrayList<>();
        
        try {
            for (ExportShipment shipment : shipments) {
                IssuedToken token = issueExportToken(
                    shipment.getCommodityType(),
                    shipment.getQuantity(),
                    shipment.getBillOfLading(),
                    shipment.getCustomsSignature(),
                    shipment.getPortOfOrigin(),
                    shipment.getDestination()
                );
                tokens.add(token);
            }
            
            LOGGER.info("Bulk export tokens issued: " + tokens.size());
            return tokens;
        } catch (Exception e) {
            throw new ExportException("Bulk issuance failed: " + e.getMessage());
        }
    }
    
    /**
     * Converts export token to ASU (African Stable Unit)
     */
    public Amount convertToASU(String tokenId, BigDecimal exchangeRate) throws ExportException {
        try {
            IssuedToken token = issuedTokens.get(tokenId);
            if (token == null) {
                throw new ExportException("Token not found: " + tokenId);
            }
            
            BigInteger asuAmount = token.getQuantity().getValue()
                .multiply(exchangeRate.toBigInteger());
            
            Amount asuValue = new Amount(asuAmount, "ASU");
            LOGGER.info("Export token converted to ASU: " + asuValue);
            
            return asuValue;
        } catch (ExportException e) {
            throw e;
        } catch (Exception e) {
            throw new ExportException("Conversion failed: " + e.getMessage());
        }
    }
    
    /**
     * Converts export token to national currency
     */
    public Amount convertToNationalCurrency(String tokenId, String currencyCode, 
                                           BigDecimal exchangeRate) throws ExportException {
        try {
            IssuedToken token = issuedTokens.get(tokenId);
            if (token == null) {
                throw new ExportException("Token not found: " + tokenId);
            }
            
            BigInteger currencyAmount = token.getQuantity().getValue()
                .multiply(exchangeRate.toBigInteger());
            
            Amount currencyValue = new Amount(currencyAmount, currencyCode);
            LOGGER.info("Export token converted to " + currencyCode + ": " + currencyValue);
            
            return currencyValue;
        } catch (ExportException e) {
            throw e;
        } catch (Exception e) {
            throw new ExportException("Conversion failed: " + e.getMessage());
        }
    }
    
    public IssuedToken getToken(String tokenId) {
        return issuedTokens.get(tokenId);
    }
    
    public Collection<IssuedToken> getAllTokens() {
        return new ArrayList<>(issuedTokens.values());
    }
    
    public static class IssuedToken {
        private final String tokenId;
        private final String commodityType;
        private final Amount quantity;
        private final String portOfOrigin;
        private final String destination;
        private final long issueTime;
        private TokenStatus status;
        
        public IssuedToken(String tokenId, String commodityType, Amount quantity,
                          String portOfOrigin, String destination, long issueTime) {
            this.tokenId = tokenId;
            this.commodityType = commodityType;
            this.quantity = quantity;
            this.portOfOrigin = portOfOrigin;
            this.destination = destination;
            this.issueTime = issueTime;
            this.status = TokenStatus.ACTIVE;
        }
        
        public enum TokenStatus {
            ACTIVE, CONVERTED, REDEEMED, EXPIRED
        }
        
        public String getTokenId() { return tokenId; }
        public String getCommodityType() { return commodityType; }
        public Amount getQuantity() { return quantity; }
        public String getPortOfOrigin() { return portOfOrigin; }
        public String getDestination() { return destination; }
        public long getIssueTime() { return issueTime; }
        public TokenStatus getStatus() { return status; }
        public void setStatus(TokenStatus status) { this.status = status; }
    }
    
    public static class ExportShipment {
        private final String commodityType;
        private final Amount quantity;
        private final byte[] billOfLading;
        private final byte[] customsSignature;
        private final String portOfOrigin;
        private final String destination;
        
        public ExportShipment(String commodityType, Amount quantity, byte[] billOfLading,
                             byte[] customsSignature, String portOfOrigin, String destination) {
            this.commodityType = commodityType;
            this.quantity = quantity;
            this.billOfLading = billOfLading.clone();
            this.customsSignature = customsSignature.clone();
            this.portOfOrigin = portOfOrigin;
            this.destination = destination;
        }
        
        public String getCommodityType() { return commodityType; }
        public Amount getQuantity() { return quantity; }
        public byte[] getBillOfLading() { return billOfLading.clone(); }
        public byte[] getCustomsSignature() { return customsSignature.clone(); }
        public String getPortOfOrigin() { return portOfOrigin; }
        public String getDestination() { return destination; }
    }
    
    public static class ExportException extends Exception {
        public ExportException(String message) {
            super(message);
        }
        
        public ExportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}