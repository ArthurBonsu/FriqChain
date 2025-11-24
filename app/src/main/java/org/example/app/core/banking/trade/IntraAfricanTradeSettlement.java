package org.example.app.core.banking.trade;

import org.example.app.core.types.Address;
import org.example.app.core.types.Amount;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * IntraAfricanTradeSettlement enables trade settlements between African countries
 * using ASU (African Stable Unit) as the settlement currency, removing dependency
 * on foreign currency systems.
 */
public class IntraAfricanTradeSettlement {
    private static final Logger LOGGER = Logger.getLogger(IntraAfricanTradeSettlement.class.getName());
    
    private final Map<String, Settlement> settlements = new ConcurrentHashMap<>();
    private final Map<String, SettlementBatch> batchSettlements = new ConcurrentHashMap<>();
    private final Map<String, SettlementPair> tradingPairs = new ConcurrentHashMap<>();
    
    /**
     * Settles trade between two African countries using ASU
     */
    public Settlement settleTradeInASU(String exporterCountry, String importerCountry,
                                      String commodityType, Amount quantity,
                                      Amount asuPrice, String exportTokenId) throws SettlementException {
        try {
            if (!isAfricanCountry(exporterCountry) || !isAfricanCountry(importerCountry)) {
                throw new SettlementException("Both countries must be African");
            }
            
            // Calculate ASU amount
            Amount totalASU = asuPrice.multiply(quantity.getValue());
            
            // Create settlement record
            String settlementId = UUID.randomUUID().toString();
            Settlement settlement = new Settlement(
                settlementId,
                exporterCountry,
                importerCountry,
                commodityType,
                quantity,
                totalASU,
                exportTokenId,
                SettlementStatus.INITIATED,
                System.currentTimeMillis()
            );
            
            settlements.put(settlementId, settlement);
            
            LOGGER.info("Trade settlement initiated: " + settlementId + 
                       " - " + exporterCountry + " -> " + importerCountry);
            
            return settlement;
        } catch (SettlementException e) {
            throw e;
        } catch (Exception e) {
            throw new SettlementException("Settlement creation failed: " + e.getMessage());
        }
    }
    
    /**
     * Completes a settlement transaction
     */
    public void completeSettlement(String settlementId, Address exporterAddress,
                                   Address importerAddress) throws SettlementException {
        try {
            Settlement settlement = settlements.get(settlementId);
            if (settlement == null) {
                throw new SettlementException("Settlement not found: " + settlementId);
            }
            
            // Verify addresses
            if (exporterAddress == null || importerAddress == null) {
                throw new SettlementException("Invalid addresses");
            }
            
            // Update settlement status
            settlement.setStatus(SettlementStatus.COMPLETED);
            settlement.setExporterAddress(exporterAddress);
            settlement.setImporterAddress(importerAddress);
            settlement.setCompletionTime(System.currentTimeMillis());
            
            LOGGER.info("Settlement completed: " + settlementId + 
                       " - ASU transferred: " + settlement.getAsuAmount());
        } catch (SettlementException e) {
            throw e;
        } catch (Exception e) {
            throw new SettlementException("Settlement completion failed: " + e.getMessage());
        }
    }
    
    /**
     * Processes batch settlements for multiple trades
     */
    public SettlementBatch processBatchSettlements(List<SettlementRequest> requests) 
            throws SettlementException {
        List<Settlement> settlements = new ArrayList<>();
        
        try {
            String batchId = UUID.randomUUID().toString();
            
            for (SettlementRequest request : requests) {
                Settlement settlement = settleTradeInASU(
                    request.getExporterCountry(),
                    request.getImporterCountry(),
                    request.getCommodityType(),
                    request.getQuantity(),
                    request.getAsuPrice(),
                    request.getExportTokenId()
                );
                
                settlements.add(settlement);
            }
            
            Amount totalASU = calculateBatchTotal(settlements);
            
            SettlementBatch batch = new SettlementBatch(
                batchId,
                settlements,
                totalASU,
                System.currentTimeMillis()
            );
            
            batchSettlements.put(batchId, batch);
            LOGGER.info("Batch settlements processed: " + batchId + 
                       " - " + settlements.size() + " settlements");
            
            return batch;
        } catch (Exception e) {
            throw new SettlementException("Batch processing failed: " + e.getMessage());
        }
    }
    
    /**
     * Registers currency trading pair for settlement conversion
     */
    public void registerTradingPair(String currency1, String currency2, 
                                   BigDecimal exchangeRate) throws SettlementException {
        try {
            String pairId = UUID.randomUUID().toString();
            SettlementPair pair = new SettlementPair(
                pairId,
                currency1,
                currency2,
                exchangeRate,
                System.currentTimeMillis()
            );
            
            tradingPairs.put(pairId, pair);
            LOGGER.info("Trading pair registered: " + currency1 + "/" + currency2 + 
                       " @ " + exchangeRate);
        } catch (Exception e) {
            throw new SettlementException("Pair registration failed: " + e.getMessage());
        }
    }
    
    /**
     * Converts settlement amount between currencies
     */
    public Amount convertSettlementCurrency(String fromCurrency, String toCurrency,
                                           Amount amount) throws SettlementException {
        try {
            if (fromCurrency.equals(toCurrency)) {
                return amount;
            }
            
            // Find exchange rate
            BigDecimal rate = null;
            for (SettlementPair pair : tradingPairs.values()) {
                if (pair.getCurrency1().equals(fromCurrency) && pair.getCurrency2().equals(toCurrency)) {
                    rate = pair.getExchangeRate();
                    break;
                }
            }
            
            if (rate == null) {
                throw new SettlementException("Exchange rate not found: " + 
                    fromCurrency + "/" + toCurrency);
            }
            
            BigInteger convertedAmount = amount.getValue()
                .multiply(rate.toBigInteger());
            
            Amount converted = new Amount(convertedAmount, toCurrency);
            LOGGER.info("Settlement currency converted: " + amount + " -> " + converted);
            
            return converted;
        } catch (SettlementException e) {
            throw e;
        } catch (Exception e) {
            throw new SettlementException("Conversion failed: " + e.getMessage());
        }
    }
    
    /**
     * Verifies both countries are African
     */
    private boolean isAfricanCountry(String countryCode) {
        // List of African country codes
        Set<String> africanCountries = new HashSet<>(Arrays.asList(
            "ZA", "NG", "GH", "KE", "ET", "EG", "MA", "UG", "TZ", "CM",
            "CI", "SN", "RW", "BW", "ZM", "ZW", "MZ", "MW", "ML", "SL"
        ));
        return africanCountries.contains(countryCode);
    }
    
    /**
     * Calculates total ASU for batch
     */
    private Amount calculateBatchTotal(List<Settlement> settlements) {
        Amount total = settlements.get(0).getAsuAmount();
        for (int i = 1; i < settlements.size(); i++) {
            total = total.add(settlements.get(i).getAsuAmount());
        }
        return total;
    }
    
    public Settlement getSettlement(String settlementId) {
        return settlements.get(settlementId);
    }
    
    public SettlementBatch getBatchSettlement(String batchId) {
        return batchSettlements.get(batchId);
    }
    
    public enum SettlementStatus {
        INITIATED, PENDING, IN_PROGRESS, COMPLETED, FAILED, DISPUTED
    }
    
    public static class Settlement {
        private final String settlementId;
        private final String exporterCountry;
        private final String importerCountry;
        private final String commodityType;
        private final Amount quantity;
        private final Amount asuAmount;
        private final String exportTokenId;
        private SettlementStatus status;
        private Address exporterAddress;
        private Address importerAddress;
        private long completionTime;
        private final long initiationTime;
        
        public Settlement(String settlementId, String exporterCountry, String importerCountry,
                         String commodityType, Amount quantity, Amount asuAmount,
                         String exportTokenId, SettlementStatus status, long initiationTime) {
            this.settlementId = settlementId;
            this.exporterCountry = exporterCountry;
            this.importerCountry = importerCountry;
            this.commodityType = commodityType;
            this.quantity = quantity;
            this.asuAmount = asuAmount;
            this.exportTokenId = exportTokenId;
            this.status = status;
            this.initiationTime = initiationTime;
        }
        
        public String getSettlementId() { return settlementId; }
        public String getExporterCountry() { return exporterCountry; }
        public String getImporterCountry() { return importerCountry; }
        public String getCommodityType() { return commodityType; }
        public Amount getQuantity() { return quantity; }
        public Amount getAsuAmount() { return asuAmount; }
        public String getExportTokenId() { return exportTokenId; }
        public SettlementStatus getStatus() { return status; }
        public void setStatus(SettlementStatus status) { this.status = status; }
        public Address getExporterAddress() { return exporterAddress; }
        public void setExporterAddress(Address address) { this.exporterAddress = address; }
        public Address getImporterAddress() { return importerAddress; }
        public void setImporterAddress(Address address) { this.importerAddress = address; }
        public long getCompletionTime() { return completionTime; }
        public void setCompletionTime(long time) { this.completionTime = time; }
        public long getInitiationTime() { return initiationTime; }
    }
    
    public static class SettlementRequest {
        private final String exporterCountry;
        private final String importerCountry;
        private final String commodityType;
        private final Amount quantity;
        private final Amount asuPrice;
        private final String exportTokenId;
        
        public SettlementRequest(String exporterCountry, String importerCountry,
                                String commodityType, Amount quantity, Amount asuPrice,
                                String exportTokenId) {
            this.exporterCountry = exporterCountry;
            this.importerCountry = importerCountry;
            this.commodityType = commodityType;
            this.quantity = quantity;
            this.asuPrice = asuPrice;
            this.exportTokenId = exportTokenId;
        }
        
        public String getExporterCountry() { return exporterCountry; }
        public String getImporterCountry() { return importerCountry; }
        public String getCommodityType() { return commodityType; }
        public Amount getQuantity() { return quantity; }
        public Amount getAsuPrice() { return asuPrice; }
        public String getExportTokenId() { return exportTokenId; }
    }
    
    public static class SettlementBatch {
        private final String batchId;
        private final List<Settlement> settlements;
        private final Amount totalASU;
        private final long createdTime;
        
        public SettlementBatch(String batchId, List<Settlement> settlements,
                              Amount totalASU, long createdTime) {
            this.batchId = batchId;
            this.settlements = new ArrayList<>(settlements);
            this.totalASU = totalASU;
            this.createdTime = createdTime;
        }
        
        public String getBatchId() { return batchId; }
        public List<Settlement> getSettlements() { return new ArrayList<>(settlements); }
        public Amount getTotalASU() { return totalASU; }
        public long getCreatedTime() { return createdTime; }
    }
    
    public static class SettlementPair {
        private final String pairId;
        private final String currency1;
        private final String currency2;
        private final BigDecimal exchangeRate;
        private final long registrationTime;
        
        public SettlementPair(String pairId, String currency1, String currency2,
                             BigDecimal exchangeRate, long registrationTime) {
            this.pairId = pairId;
            this.currency1 = currency1;
            this.currency2 = currency2;
            this.exchangeRate = exchangeRate;
            this.registrationTime = registrationTime;
        }
        
        public String getPairId() { return pairId; }
        public String getCurrency1() { return currency1; }
        public String getCurrency2() { return currency2; }
        public BigDecimal getExchangeRate() { return exchangeRate; }
        public long getRegistrationTime() { return registrationTime; }
    }
    
    public static class SettlementException extends Exception {
        public SettlementException(String message) {
            super(message);
        }
        
        public SettlementException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}