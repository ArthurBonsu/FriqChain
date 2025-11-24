package org.example.app.core.banking.oracle;

import org.example.app.core.types.Address;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * CommodityPriceOracle - Commodity prices (gold, oil, agricultural products)
 * 
 * Provides commodity price data for ASU backing and resource reserve valuation
 */
public class CommodityPriceOracle extends OracleReporter {
    private static final Logger LOGGER = Logger.getLogger(CommodityPriceOracle.class.getName());
    
    private final Map<String, CommodityPrice> commodityPrices;
    
    public static class CommodityPrice {
        private final String commodityType;
        private final BigDecimal pricePerUnit;
        private final String unit;
        private final BigDecimal changePercent24h;
        private final long timestamp;
        
        public CommodityPrice(String commodityType, BigDecimal pricePerUnit, String unit,
                             BigDecimal changePercent24h) {
            this.commodityType = commodityType;
            this.pricePerUnit = pricePerUnit;
            this.unit = unit;
            this.changePercent24h = changePercent24h;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getCommodityType() { return commodityType; }
        public BigDecimal getPricePerUnit() { return pricePerUnit; }
        public String getUnit() { return unit; }
        public BigDecimal getChangePercent24h() { return changePercent24h; }
        public long getTimestamp() { return timestamp; }
    }
    
    public CommodityPriceOracle(String reporterId, Address reporterAddress, BigDecimal minimumStake) {
        super(reporterId, "COMMODITY_PRICE", reporterAddress, minimumStake);
        this.commodityPrices = new ConcurrentHashMap<>();
        LOGGER.info("CommodityPriceOracle initialized: " + reporterId);
    }
    
    @Override
    public OracleData fetchData(String dataKey) throws Exception {
        String commodity = dataKey.replace("COMMODITY:", "");
        
        CommodityPrice price = commodityPrices.get(commodity);
        if (price == null || isStale(price)) {
            price = fetchCommodityPrice(commodity);
            commodityPrices.put(commodity, price);
        }
        
        Map<String, Object> priceData = new HashMap<>();
        priceData.put("commodity", price.getCommodityType());
        priceData.put("price", price.getPricePerUnit());
        priceData.put("unit", price.getUnit());
        priceData.put("change24h", price.getChangePercent24h());
        
        byte[] signature = new byte[32];
        submitData("COMMODITY_PRICE", priceData, signature);
        
        return new OracleData("COMMODITY_PRICE", priceData, reporterId,
            new BigDecimal("0.93"), signature);
    }
    
    private CommodityPrice fetchCommodityPrice(String commodity) {
        // Simulate commodity price fetch
        Map<String, Object[]> prices = new HashMap<>();
        prices.put("GOLD", new Object[]{new BigDecimal("2050.00"), "USD/oz", new BigDecimal("0.5")});
        prices.put("OIL", new Object[]{new BigDecimal("85.50"), "USD/barrel", new BigDecimal("-1.2")});
        prices.put("COCOA", new Object[]{new BigDecimal("4200.00"), "USD/ton", new BigDecimal("2.3")});
        prices.put("COFFEE", new Object[]{new BigDecimal("1.75"), "USD/lb", new BigDecimal("1.1")});
        
        Object[] data = prices.getOrDefault(commodity.toUpperCase(), 
            new Object[]{new BigDecimal("100.00"), "USD/unit", BigDecimal.ZERO});
        
        return new CommodityPrice(commodity, (BigDecimal) data[0], (String) data[1], (BigDecimal) data[2]);
    }
    
    private boolean isStale(CommodityPrice price) {
        return System.currentTimeMillis() - price.getTimestamp() > 300000; // 5 minutes
    }
    
    @Override
    protected boolean validateDataFormat(Object data) {
        return data instanceof Map;
    }
    
    @Override
    protected String getDataTypeIdentifier() {
        return "COMMODITY_PRICE";
    }
}
