package org.example.app.core.banking.oracle;

import org.example.app.core.types.Address;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * BankFeedOracle - Bank settlement feed oracle (aggregated, anonymized)
 *
 * Provides anonymized bank settlement data for:
 * - Cross-bank transaction validation
 * - Liquidity assessment
 * - Settlement confirmation
 * - Currency flow monitoring
 *
 * Data is aggregated and anonymized to protect bank privacy while
 * providing validation for the TINC network.
 *
 * Based on Implementation Guidebook Section VI: Multi-Source Verification
 */
public class BankFeedOracle extends OracleReporter {
    private static final Logger LOGGER = Logger.getLogger(BankFeedOracle.class.getName());

    // Settlement tracking
    private final Map<String, BankSettlementFeed> settlementFeeds;

    /**
     * Bank settlement feed (anonymized)
     */
    public static class BankSettlementFeed {
        private final String feedId;
        private final String currencyCode;
        private final BigDecimal totalVolume;
        private final int transactionCount;
        private final BigDecimal averageSize;
        private final long timestamp;
        private final boolean isVerified;

        public BankSettlementFeed(String currencyCode, BigDecimal totalVolume,
                                  int transactionCount) {
            this.feedId = UUID.randomUUID().toString();
            this.currencyCode = currencyCode;
            this.totalVolume = totalVolume;
            this.transactionCount = transactionCount;
            this.averageSize = transactionCount > 0 ?
                    totalVolume.divide(new BigDecimal(transactionCount), 2, RoundingMode.HALF_UP) :
                    BigDecimal.ZERO;
            this.timestamp = System.currentTimeMillis();
            this.isVerified = true;
        }

        public String getFeedId() { return feedId; }
        public String getCurrencyCode() { return currencyCode; }
        public BigDecimal getTotalVolume() { return totalVolume; }
        public int getTransactionCount() { return transactionCount; }
        public BigDecimal getAverageSize() { return averageSize; }
        public long getTimestamp() { return timestamp; }
        public boolean isVerified() { return isVerified; }
    }

    /**
     * Constructor
     */
    public BankFeedOracle(String reporterId, Address reporterAddress, BigDecimal minimumStake,
                         AfricanSovereigntyTypes.AfricanRegion region, String country) {
        super(reporterId, "BANK_FEED", reporterAddress, minimumStake, region, country,
              AfricanSovereigntyTypes.NodeType.BANK_ORACLE, true);
        this.settlementFeeds = new ConcurrentHashMap<>();
        LOGGER.info("BankFeedOracle initialized: " + reporterId);
    }

    @Override
    public OracleData fetchData(String dataKey) throws Exception {
        // DataKey format: "SETTLEMENT:currencyCode"
        String currencyCode = dataKey.replace("SETTLEMENT:", "");

        BankSettlementFeed feed = getRecentFeed(currencyCode);
        if (feed == null) {
            throw new Exception("No recent settlement feed for: " + currencyCode);
        }

        Map<String, Object> feedData = new HashMap<>();
        feedData.put("currency", feed.getCurrencyCode());
        feedData.put("volume", feed.getTotalVolume());
        feedData.put("transactions", feed.getTransactionCount());
        feedData.put("avgSize", feed.getAverageSize());

        byte[] signature = new byte[32];
        submitData("BANK_FEED", feedData, signature);

        return new OracleData("BANK_FEED", feedData, reporterId,
                new BigDecimal("0.95"), signature);
    }

    public void submitSettlementFeed(String currencyCode, BigDecimal totalVolume,
                                     int transactionCount) {
        BankSettlementFeed feed = new BankSettlementFeed(currencyCode, totalVolume, transactionCount);
        settlementFeeds.put(feed.getFeedId(), feed);
        LOGGER.info(String.format("Settlement feed submitted: %s - Volume: %s", currencyCode, totalVolume));
    }

    private BankSettlementFeed getRecentFeed(String currencyCode) {
        return settlementFeeds.values().stream()
                .filter(f -> f.getCurrencyCode().equals(currencyCode))
                .filter(f -> System.currentTimeMillis() - f.getTimestamp() < 3600000) // 1 hour
                .max(Comparator.comparing(BankSettlementFeed::getTimestamp))
                .orElse(null);
    }

    @Override
    protected boolean validateDataFormat(Object data) {
        return data instanceof Map;
    }

    @Override
    protected String getDataTypeIdentifier() {
        return "BANK_FEED";
    }
}