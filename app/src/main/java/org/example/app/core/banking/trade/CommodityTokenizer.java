package org.example.app.core.banking.trade;

import org.example.app.core.types.Amount;
import org.example.app.core.banking.oracle.AfricanSovereigntyTypes.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * CommodityTokenizer - Tokenization with African Sovereignty
 * SOVEREIGNTY: Multi-region verification (3+ regions), African provenance priority
 */
public class CommodityTokenizer {
    private static final Logger LOGGER = Logger.getLogger(CommodityTokenizer.class.getName());
    private static final int MIN_REGIONS_FOR_VERIFICATION = 3;
    
    private final Map<String, TokenizedCommodity> tokenizedAssets = new ConcurrentHashMap<>();
    private final Map<String, ProvenianceRecord> provenance = new ConcurrentHashMap<>();
    private final Map<AfricanRegion, Set<String>> regionalTokens = new ConcurrentHashMap<>();
    
    public TokenizedCommodity tokenizeCommodity(String commodityType, String origin,
                                               Amount quantity, byte[] certificateOfOrigin,
                                               String qualityGrade, AfricanRegion originRegion,
                                               Map<AfricanRegion, Boolean> regionalVerifications) 
            throws TokenizationException {
        try {
            if (regionalVerifications.size() < MIN_REGIONS_FOR_VERIFICATION) {
                throw new TokenizationException("Insufficient regional verifications: " + regionalVerifications.size());
            }
            
            if (commodityType == null || commodityType.isEmpty()) {
                throw new TokenizationException("Commodity type cannot be empty");
            }
            
            if (!validateCertificate(certificateOfOrigin)) {
                throw new TokenizationException("Invalid certificate of origin");
            }
            
            String tokenId = UUID.randomUUID().toString();
            TokenizedCommodity token = new TokenizedCommodity(tokenId, commodityType, origin,
                quantity, qualityGrade, certificateOfOrigin, System.currentTimeMillis(),
                originRegion, regionalVerifications.size());
            
            tokenizedAssets.put(tokenId, token);
            
            if (originRegion != null) {
                regionalTokens.computeIfAbsent(originRegion, k -> new HashSet<>()).add(tokenId);
            }
            
            createProvenanceRecord(tokenId, commodityType, origin, certificateOfOrigin, originRegion);
            
            LOGGER.info(String.format("Commodity tokenized: %s - %s - Region: %s - Verifications: %d",
                tokenId, commodityType, originRegion, regionalVerifications.size()));
            
            return token;
        } catch (TokenizationException e) {
            throw e;
        } catch (Exception e) {
            throw new TokenizationException("Tokenization failed: " + e.getMessage());
        }
    }
    
    private boolean validateCertificate(byte[] certificate) {
        return certificate != null && certificate.length > 0;
    }
    
    private void createProvenanceRecord(String tokenId, String commodityType, String origin,
                                       byte[] certificate, AfricanRegion region) {
        try {
            ProvenianceRecord record = new ProvenianceRecord(tokenId, commodityType, origin,
                "TOKENIZED", System.currentTimeMillis(), region);
            provenance.put(tokenId, record);
        } catch (Exception e) {
            LOGGER.warning("Could not create provenance record: " + e.getMessage());
        }
    }
    
    public static class TokenizedCommodity {
        private final String tokenId;
        private final String commodityType;
        private final String origin;
        private final Amount quantity;
        private final String qualityGrade;
        private final byte[] certificateOfOrigin;
        private final long tokenizationTime;
        private final AfricanRegion originRegion;
        private final int regionalVerifications;
        private boolean spent;
        
        public TokenizedCommodity(String tokenId, String commodityType, String origin,
                                 Amount quantity, String qualityGrade, byte[] certificateOfOrigin,
                                 long tokenizationTime, AfricanRegion originRegion,
                                 int regionalVerifications) {
            this.tokenId = tokenId;
            this.commodityType = commodityType;
            this.origin = origin;
            this.quantity = quantity;
            this.qualityGrade = qualityGrade;
            this.certificateOfOrigin = certificateOfOrigin.clone();
            this.tokenizationTime = tokenizationTime;
            this.originRegion = originRegion;
            this.regionalVerifications = regionalVerifications;
            this.spent = false;
        }
        
        public String getTokenId() { return tokenId; }
        public AfricanRegion getOriginRegion() { return originRegion; }
        public int getRegionalVerifications() { return regionalVerifications; }
    }
    
    public static class ProvenianceRecord {
        private final String tokenId;
        private final String commodityType;
        private final String origin;
        private final String status;
        private final long timestamp;
        private final AfricanRegion region;
        
        public ProvenianceRecord(String tokenId, String commodityType, String origin,
                                String status, long timestamp, AfricanRegion region) {
            this.tokenId = tokenId;
            this.commodityType = commodityType;
            this.origin = origin;
            this.status = status;
            this.timestamp = timestamp;
            this.region = region;
        }
        
        public AfricanRegion getRegion() { return region; }
    }
    
    public static class TokenizationException extends Exception {
        public TokenizationException(String message) { super(message); }
    }
}