package org.example.app.core.banking.trade;

import org.example.app.core.types.Address;
import org.example.app.core.types.Amount;
import org.example.app.core.banking.oracle.AfricanSovereigntyTypes.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * ExportTokenIssuer - Token issuance with African Sovereignty
 * SOVEREIGNTY: Multi-region verification (3+ regions), African customs priority (3x)
 */
public class ExportTokenIssuer {
    private static final Logger LOGGER = Logger.getLogger(ExportTokenIssuer.class.getName());
    private static final int MIN_REGIONS_REQUIRED = 3;
    
    private final Map<String, IssuedToken> issuedTokens = new ConcurrentHashMap<>();
    private final TradeReceiptVerifier receiptVerifier;
    private final CustomsSignatureValidator customsValidator;
    
    public ExportTokenIssuer(TradeReceiptVerifier receiptVerifier, CustomsSignatureValidator customsValidator) {
        this.receiptVerifier = receiptVerifier;
        this.customsValidator = customsValidator;
    }
    
    public IssuedToken issueExportToken(String commodityType, Amount quantity, 
                                        byte[] billOfLading, byte[] customsSignature,
                                        String portOfOrigin, String destination,
                                        Map<AfricanRegion, Boolean> regionalVerifications) 
            throws ExportException {
        try {
            if (regionalVerifications.size() < MIN_REGIONS_REQUIRED) {
                throw new ExportException("Insufficient regional verifications: " + regionalVerifications.size());
            }
            
            if (!receiptVerifier.verifyTradeReceipt(billOfLading)) {
                throw new ExportException("Invalid trade receipt");
            }
            
            if (!customsValidator.validateSignature(customsSignature, billOfLading)) {
                throw new ExportException("Invalid customs signature");
            }
            
            String tokenId = UUID.randomUUID().toString();
            IssuedToken token = new IssuedToken(tokenId, commodityType, quantity, portOfOrigin,
                destination, System.currentTimeMillis(), regionalVerifications.size());
            
            issuedTokens.put(tokenId, token);
            LOGGER.info("Export token issued: " + tokenId + " - Regions: " + regionalVerifications.size());
            
            return token;
        } catch (ExportException e) {
            throw e;
        } catch (Exception e) {
            throw new ExportException("Token issuance failed: " + e.getMessage());
        }
    }
    
    public Amount convertToASU(String tokenId, BigDecimal exchangeRate) throws ExportException {
        try {
            IssuedToken token = issuedTokens.get(tokenId);
            if (token == null) throw new ExportException("Token not found");
            
            BigInteger asuAmount = token.getQuantity().getValue().multiply(exchangeRate.toBigInteger());
            return new Amount(asuAmount, "ASU");
        } catch (Exception e) {
            throw new ExportException("Conversion failed: " + e.getMessage());
        }
    }
    
    public static class IssuedToken {
        private final String tokenId;
        private final String commodityType;
        private final Amount quantity;
        private final String portOfOrigin;
        private final String destination;
        private final long issueTime;
        private final int regionalVerifications;
        private TokenStatus status;
        
        public IssuedToken(String tokenId, String commodityType, Amount quantity,
                          String portOfOrigin, String destination, long issueTime, int regionalVerifications) {
            this.tokenId = tokenId;
            this.commodityType = commodityType;
            this.quantity = quantity;
            this.portOfOrigin = portOfOrigin;
            this.destination = destination;
            this.issueTime = issueTime;
            this.regionalVerifications = regionalVerifications;
            this.status = TokenStatus.ACTIVE;
        }
        
        public enum TokenStatus { ACTIVE, CONVERTED, REDEEMED, EXPIRED }
        
        public String getTokenId() { return tokenId; }
        public String getCommodityType() { return commodityType; }
        public Amount getQuantity() { return quantity; }
        public String getPortOfOrigin() { return portOfOrigin; }
        public String getDestination() { return destination; }
        public long getIssueTime() { return issueTime; }
        public int getRegionalVerifications() { return regionalVerifications; }
        public TokenStatus getStatus() { return status; }
    }
    
    public static class ExportException extends Exception {
        public ExportException(String message) { super(message); }
    }
}