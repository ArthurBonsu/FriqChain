package org.example.app.core.banking.trade;

import org.example.app.core.types.Amount;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * CommodityTokenizer converts physical commodities into blockchain-tradeable tokens
 * with verifiable backing and provenance tracking.
 */
public class CommodityTokenizer {
    private static final Logger LOGGER = Logger.getLogger(CommodityTokenizer.class.getName());
    
    private final Map<String, TokenizedCommodity> tokenizedAssets = new ConcurrentHashMap<>();
    private final Map<String, CommodityBatch> commodityBatches = new ConcurrentHashMap<>();
    private final Map<String, ProvenianceRecord> provenance = new ConcurrentHashMap<>();
    
    /**
     * Tokenizes a commodity batch
     */
    public TokenizedCommodity tokenizeCommodity(String commodityType, String origin,
                                               Amount quantity, byte[] certificateOfOrigin,
                                               String qualityGrade) throws TokenizationException {
        try {
            if (commodityType == null || commodityType.isEmpty()) {
                throw new TokenizationException("Commodity type cannot be empty");
            }
            
            if (quantity == null || quantity.isZero()) {
                throw new TokenizationException("Quantity must be positive");
            }
            
            // Validate certificate
            if (!validateCertificate(certificateOfOrigin)) {
                throw new TokenizationException("Invalid certificate of origin");
            }
            
            String tokenId = UUID.randomUUID().toString();
            TokenizedCommodity token = new TokenizedCommodity(
                tokenId,
                commodityType,
                origin,
                quantity,
                qualityGrade,
                certificateOfOrigin,
                System.currentTimeMillis()
            );
            
            tokenizedAssets.put(tokenId, token);
            
            // Create provenance record
            createProvenanceRecord(tokenId, commodityType, origin, certificateOfOrigin);
            
            LOGGER.info("Commodity tokenized: " + tokenId + " - " + quantity + " " + commodityType);
            return token;
        } catch (TokenizationException e) {
            throw e;
        } catch (Exception e) {
            throw new TokenizationException("Tokenization failed: " + e.getMessage());
        }
    }
    
    /**
     * Tokenizes batch of commodities
     */
    public List<TokenizedCommodity> tokenizeBatch(List<CommodityInput> commodities) 
            throws TokenizationException {
        List<TokenizedCommodity> tokenizedList = new ArrayList<>();
        
        try {
            String batchId = UUID.randomUUID().toString();
            CommodityBatch batch = new CommodityBatch(batchId, System.currentTimeMillis());
            
            for (CommodityInput commodity : commodities) {
                TokenizedCommodity token = tokenizeCommodity(
                    commodity.getCommodityType(),
                    commodity.getOrigin(),
                    commodity.getQuantity(),
                    commodity.getCertificateOfOrigin(),
                    commodity.getQualityGrade()
                );
                
                tokenizedList.add(token);
                batch.addToken(token.getTokenId());
            }
            
            commodityBatches.put(batchId, batch);
            LOGGER.info("Commodity batch tokenized: " + batchId + " - " + tokenizedList.size() + " items");
            
            return tokenizedList;
        } catch (TokenizationException e) {
            throw e;
        } catch (Exception e) {
            throw new TokenizationException("Batch tokenization failed: " + e.getMessage());
        }
    }
    
    /**
     * Splits a tokenized commodity
     */
    public List<TokenizedCommodity> splitToken(String tokenId, List<Amount> portions) 
            throws TokenizationException {
        try {
            TokenizedCommodity original = tokenizedAssets.get(tokenId);
            if (original == null) {
                throw new TokenizationException("Token not found: " + tokenId);
            }
            
            // Verify portions sum to original quantity
            Amount total = portions.get(0);
            for (int i = 1; i < portions.size(); i++) {
                total = total.add(portions.get(i));
            }
            
            if (!total.equals(original.getQuantity())) {
                throw new TokenizationException("Portions do not equal original quantity");
            }
            
            List<TokenizedCommodity> splitTokens = new ArrayList<>();
            for (Amount portion : portions) {
                String newTokenId = UUID.randomUUID().toString();
                TokenizedCommodity newToken = new TokenizedCommodity(
                    newTokenId,
                    original.getCommodityType(),
                    original.getOrigin(),
                    portion,
                    original.getQualityGrade(),
                    original.getCertificateOfOrigin(),
                    System.currentTimeMillis()
                );
                
                tokenizedAssets.put(newTokenId, newToken);
                splitTokens.add(newToken);
                
                // Record provenance
                provenance.put(newTokenId, new ProvenianceRecord(
                    newTokenId,
                    original.getCommodityType(),
                    original.getOrigin(),
                    "SPLIT_FROM:" + tokenId,
                    System.currentTimeMillis()
                ));
            }
            
            // Mark original as spent
            original.markSpent();
            
            LOGGER.info("Token split: " + tokenId + " into " + splitTokens.size() + " tokens");
            return splitTokens;
        } catch (TokenizationException e) {
            throw e;
        } catch (Exception e) {
            throw new TokenizationException("Token split failed: " + e.getMessage());
        }
    }
    
    /**
     * Merges multiple tokens into one
     */
    public TokenizedCommodity mergeTokens(List<String> tokenIds) throws TokenizationException {
        try {
            if (tokenIds == null || tokenIds.isEmpty()) {
                throw new TokenizationException("No tokens to merge");
            }
            
            Amount totalQuantity = null;
            String commodityType = null;
            String origin = null;
            String qualityGrade = null;
            
            for (String tokenId : tokenIds) {
                TokenizedCommodity token = tokenizedAssets.get(tokenId);
                if (token == null) {
                    throw new TokenizationException("Token not found: " + tokenId);
                }
                
                if (commodityType == null) {
                    commodityType = token.getCommodityType();
                    origin = token.getOrigin();
                    qualityGrade = token.getQualityGrade();
                } else if (!commodityType.equals(token.getCommodityType())) {
                    throw new TokenizationException("Cannot merge different commodity types");
                }
                
                if (totalQuantity == null) {
                    totalQuantity = token.getQuantity();
                } else {
                    totalQuantity = totalQuantity.add(token.getQuantity());
                }
            }
            
            String mergedTokenId = UUID.randomUUID().toString();
            TokenizedCommodity mergedToken = new TokenizedCommodity(
                mergedTokenId,
                commodityType,
                origin,
                totalQuantity,
                qualityGrade,
                new byte[0],
                System.currentTimeMillis()
            );
            
            tokenizedAssets.put(mergedTokenId, mergedToken);
            
            // Mark originals as spent
            for (String tokenId : tokenIds) {
                tokenizedAssets.get(tokenId).markSpent();
            }
            
            LOGGER.info("Tokens merged: " + tokenIds.size() + " into " + mergedTokenId);
            return mergedToken;
        } catch (TokenizationException e) {
            throw e;
        } catch (Exception e) {
            throw new TokenizationException("Token merge failed: " + e.getMessage());
        }
    }
    
    /**
     * Validates certificate of origin
     */
    private boolean validateCertificate(byte[] certificate) {
        try {
            return certificate != null && certificate.length > 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Creates provenance record for audit trail
     */
    private void createProvenanceRecord(String tokenId, String commodityType,
                                       String origin, byte[] certificate) {
        try {
            ProvenianceRecord record = new ProvenianceRecord(
                tokenId,
                commodityType,
                origin,
                "TOKENIZED",
                System.currentTimeMillis()
            );
            
            provenance.put(tokenId, record);
        } catch (Exception e) {
            LOGGER.warning("Could not create provenance record: " + e.getMessage());
        }
    }
    
    public TokenizedCommodity getToken(String tokenId) {
        return tokenizedAssets.get(tokenId);
    }
    
    public ProvenianceRecord getProvenance(String tokenId) {
        return provenance.get(tokenId);
    }
    
    public Collection<TokenizedCommodity> getAllTokens() {
        return new ArrayList<>(tokenizedAssets.values());
    }
    
    public static class TokenizedCommodity {
        private final String tokenId;
        private final String commodityType;
        private final String origin;
        private final Amount quantity;
        private final String qualityGrade;
        private final byte[] certificateOfOrigin;
        private final long tokenizationTime;
        private boolean spent;
        
        public TokenizedCommodity(String tokenId, String commodityType, String origin,
                                 Amount quantity, String qualityGrade, 
                                 byte[] certificateOfOrigin, long tokenizationTime) {
            this.tokenId = tokenId;
            this.commodityType = commodityType;
            this.origin = origin;
            this.quantity = quantity;
            this.qualityGrade = qualityGrade;
            this.certificateOfOrigin = certificateOfOrigin.clone();
            this.tokenizationTime = tokenizationTime;
            this.spent = false;
        }
        
        public void markSpent() { this.spent = true; }
        
        public String getTokenId() { return tokenId; }
        public String getCommodityType() { return commodityType; }
        public String getOrigin() { return origin; }
        public Amount getQuantity() { return quantity; }
        public String getQualityGrade() { return qualityGrade; }
        public byte[] getCertificateOfOrigin() { return certificateOfOrigin.clone(); }
        public long getTokenizationTime() { return tokenizationTime; }
        public boolean isSpent() { return spent; }
    }
    
    public static class CommodityInput {
        private final String commodityType;
        private final String origin;
        private final Amount quantity;
        private final byte[] certificateOfOrigin;
        private final String qualityGrade;
        
        public CommodityInput(String commodityType, String origin, Amount quantity,
                             byte[] certificateOfOrigin, String qualityGrade) {
            this.commodityType = commodityType;
            this.origin = origin;
            this.quantity = quantity;
            this.certificateOfOrigin = certificateOfOrigin.clone();
            this.qualityGrade = qualityGrade;
        }
        
        public String getCommodityType() { return commodityType; }
        public String getOrigin() { return origin; }
        public Amount getQuantity() { return quantity; }
        public byte[] getCertificateOfOrigin() { return certificateOfOrigin.clone(); }
        public String getQualityGrade() { return qualityGrade; }
    }
    
    public static class CommodityBatch {
        private final String batchId;
        private final List<String> tokenIds = new ArrayList<>();
        private final long createdTime;
        
        public CommodityBatch(String batchId, long createdTime) {
            this.batchId = batchId;
            this.createdTime = createdTime;
        }
        
        public void addToken(String tokenId) { this.tokenIds.add(tokenId); }
        
        public String getBatchId() { return batchId; }
        public List<String> getTokenIds() { return new ArrayList<>(tokenIds); }
        public long getCreatedTime() { return createdTime; }
    }
    
    public static class ProvenianceRecord {
        private final String tokenId;
        private final String commodityType;
        private final String origin;
        private final String status;
        private final long timestamp;
        
        public ProvenianceRecord(String tokenId, String commodityType, String origin,
                                String status, long timestamp) {
            this.tokenId = tokenId;
            this.commodityType = commodityType;
            this.origin = origin;
            this.status = status;
            this.timestamp = timestamp;
        }
        
        public String getTokenId() { return tokenId; }
        public String getCommodityType() { return commodityType; }
        public String getOrigin() { return origin; }
        public String getStatus() { return status; }
        public long getTimestamp() { return timestamp; }
    }
    
    public static class TokenizationException extends Exception {
        public TokenizationException(String message) {
            super(message);
        }
        
        public TokenizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}