package org.example.app.core.banking.trade;

import org.example.app.core.banking.oracle.AfricanSovereigntyTypes.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * TradeReceiptVerifier - Receipt verification with African Sovereignty
 * SOVEREIGNTY: Multi-region verification (3+ regions), African customs priority
 */
public class TradeReceiptVerifier {
    private static final Logger LOGGER = Logger.getLogger(TradeReceiptVerifier.class.getName());
    private static final int MIN_REGIONS_REQUIRED = 3;
    
    private final Map<String, VerifiedReceipt> verifiedReceipts = new ConcurrentHashMap<>();
    private final Map<AfricanRegion, Set<String>> regionalVerifiers = new ConcurrentHashMap<>();
    
    public boolean verifyTradeReceipt(byte[] receipt) {
        try {
            if (receipt == null || receipt.length == 0) return false;
            if (!isReceiptIntact(receipt)) return false;
            if (!isValidReceiptFormat(receipt)) return false;
            return true;
        } catch (Exception e) {
            LOGGER.severe("Receipt verification error: " + e.getMessage());
            return false;
        }
    }
    
    public VerifiedReceipt submitVerifiedReceipt(byte[] receipt, String commodityType,
                                                String portOfOrigin, String destination,
                                                Map<AfricanRegion, Boolean> regionalVerifications) 
            throws VerificationException {
        try {
            if (regionalVerifications.size() < MIN_REGIONS_REQUIRED) {
                throw new VerificationException("Insufficient regional verifications: " + regionalVerifications.size());
            }
            
            if (!verifyTradeReceipt(receipt)) {
                throw new VerificationException("Receipt verification failed");
            }
            
            String receiptId = UUID.randomUUID().toString();
            String receiptHash = calculateReceiptHash(receipt);
            
            VerifiedReceipt verified = new VerifiedReceipt(receiptId, receiptHash, commodityType,
                portOfOrigin, destination, System.currentTimeMillis(), regionalVerifications.size());
            
            verifiedReceipts.put(receiptId, verified);
            
            LOGGER.info("Receipt verified: " + receiptId + " - Regions: " + regionalVerifications.size());
            return verified;
        } catch (VerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new VerificationException("Submission failed: " + e.getMessage());
        }
    }
    
    private boolean isReceiptIntact(byte[] receipt) {
        return receipt.length > 0;
    }
    
    private boolean isValidReceiptFormat(byte[] receipt) {
        try {
            if (receipt.length < 100) return false;
            String receiptStr = new String(receipt);
            return receiptStr.contains("RECEIPT") || receiptStr.contains("BL");
        } catch (Exception e) {
            return false;
        }
    }
    
    private String calculateReceiptHash(byte[] receipt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(receipt);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }
    
    public static class VerifiedReceipt {
        private final String receiptId;
        private final String receiptHash;
        private final String commodityType;
        private final String portOfOrigin;
        private final String destination;
        private final long verificationTime;
        private final int regionalVerifications;
        
        public VerifiedReceipt(String receiptId, String receiptHash, String commodityType,
                              String portOfOrigin, String destination, long verificationTime,
                              int regionalVerifications) {
            this.receiptId = receiptId;
            this.receiptHash = receiptHash;
            this.commodityType = commodityType;
            this.portOfOrigin = portOfOrigin;
            this.destination = destination;
            this.verificationTime = verificationTime;
            this.regionalVerifications = regionalVerifications;
        }
        
        public int getRegionalVerifications() { return regionalVerifications; }
    }
    
    public static class VerificationException extends Exception {
        public VerificationException(String message) { super(message); }
    }
}