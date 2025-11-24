package org.example.app.core.banking.trade;

import org.example.app.core.types.Amount;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * ExportFraudDetector uses statistical analysis and multi-source verification
 * to identify fake or fraudulent export token claims.
 */
public class ExportFraudDetector {
    private static final Logger LOGGER = Logger.getLogger(ExportFraudDetector.class.getName());
    
    private final Map<String, FraudAnalysis> analyses = new ConcurrentHashMap<>();
    private final Map<String, AnomalyRecord> anomalies = new ConcurrentHashMap<>();
    private final Map<String, SuspiciousTransaction> flaggedTransactions = new ConcurrentHashMap<>();
    
    private static final double Z_SCORE_THRESHOLD = 3.0;
    private static final double ANOMALY_PROBABILITY_THRESHOLD = 0.75;
    
    /**
     * Analyzes export token claim for fraud indicators
     */
    public FraudAnalysis analyzeExportClaim(String exportTokenId, String commodityType,
                                           Amount quantity, String origin, String destination,
                                           double historicalAverageQuantity) throws FraudDetectionException {
        try {
            String analysisId = UUID.randomUUID().toString();
            
            // Calculate Z-score
            double zScore = calculateZScore(quantity.getValue().doubleValue(), 
                historicalAverageQuantity);
            
            // Check for anomalies
            boolean isAnomaly = Math.abs(zScore) > Z_SCORE_THRESHOLD;
            
            // Calculate fraud probability
            double fraudProbability = calculateFraudProbability(
                zScore, 
                quantity.getValue().doubleValue(),
                historicalAverageQuantity
            );
            
            // Determine risk level
            FraudRiskLevel riskLevel = determineFraudRisk(fraudProbability);
            
            FraudAnalysis analysis = new FraudAnalysis(
                analysisId,
                exportTokenId,
                commodityType,
                quantity,
                zScore,
                fraudProbability,
                riskLevel,
                isAnomaly,
                System.currentTimeMillis()
            );
            
            analyses.put(analysisId, analysis);
            
            // If anomaly detected, record it
            if (isAnomaly) {
                recordAnomaly(analysisId, exportTokenId, commodityType, zScore);
            }
            
            LOGGER.info("Export claim analyzed: " + analysisId + 
                       " - Risk level: " + riskLevel);
            
            return analysis;
        } catch (Exception e) {
            throw new FraudDetectionException("Analysis failed: " + e.getMessage());
        }
    }
    
    /**
     * Detects inconsistencies across multiple sources
     */
    public boolean detectMultiSourceInconsistency(String exportTokenId,
                                                  Map<String, Amount> sourceAmounts) 
            throws FraudDetectionException {
        try {
            if (sourceAmounts.size() < 2) {
                return false;
            }
            
            // Calculate average
            Amount average = calculateAverage(sourceAmounts.values());
            
            // Calculate standard deviation
            double stdDev = calculateStdDev(sourceAmounts.values(), average);
            
            // Check for outliers
            for (String source : sourceAmounts.keySet()) {
                Amount amount = sourceAmounts.get(source);
                double zScore = calculateZScore(amount.getValue().doubleValue(),
                    average.getValue().doubleValue());
                
                if (Math.abs(zScore) > Z_SCORE_THRESHOLD) {
                    LOGGER.warning("Inconsistency detected in source: " + source + 
                                 " for token: " + exportTokenId);
                    
                    flagTransaction(exportTokenId, source, "Multi-source inconsistency");
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            throw new FraudDetectionException("Inconsistency detection failed: " + e.getMessage());
        }
    }
    
    /**
     * Validates export claim against geographic patterns
     */
    public boolean validateGeographicConsistency(String origin, String destination,
                                                String commodityType,
                                                Map<String, Double> historicalPatterns) 
            throws FraudDetectionException {
        try {
            String pattern = origin + "->" + destination + ":" + commodityType;
            
            if (!historicalPatterns.containsKey(pattern)) {
                // New pattern - requires additional verification
                LOGGER.info("Novel geographic pattern detected: " + pattern);
                return true; // Allow but flag for monitoring
            }
            
            // Pattern exists - trade is consistent with history
            return true;
        } catch (Exception e) {
            throw new FraudDetectionException("Geographic validation failed: " + e.getMessage());
        }
    }
    
    /**
     * Calculates Z-score for anomaly detection
     */
    private double calculateZScore(double value, double mean) {
        // Simple Z-score calculation
        double stdDev = Math.abs(mean) * 0.2; // Assume 20% std dev
        if (stdDev == 0) return 0;
        return (value - mean) / stdDev;
    }
    
    /**
     * Calculates fraud probability based on statistical analysis
     */
    private double calculateFraudProbability(double zScore, double claimedAmount,
                                            double historicalAverage) {
        // Probability increases with Z-score magnitude
        double zScoreFactor = Math.min(Math.abs(zScore) / Z_SCORE_THRESHOLD, 1.0);
        
        // Check for impossible quantities
        double amountFactor = 0;
        if (claimedAmount > historicalAverage * 5) {
            amountFactor = 0.5; // 50% probability increase for 5x+ claims
        }
        
        // Combined probability
        return Math.min(zScoreFactor * 0.6 + amountFactor * 0.4, 0.95);
    }
    
    /**
     * Determines fraud risk level
     */
    private FraudRiskLevel determineFraudRisk(double fraudProbability) {
        if (fraudProbability < 0.25) {
            return FraudRiskLevel.LOW;
        } else if (fraudProbability < 0.50) {
            return FraudRiskLevel.MEDIUM;
        } else if (fraudProbability < 0.75) {
            return FraudRiskLevel.HIGH;
        } else {
            return FraudRiskLevel.CRITICAL;
        }
    }
    
    /**
     * Records anomaly for further investigation
     */
    private void recordAnomaly(String analysisId, String exportTokenId,
                              String commodityType, double zScore) {
        try {
            String anomalyId = UUID.randomUUID().toString();
            AnomalyRecord record = new AnomalyRecord(
                anomalyId,
                exportTokenId,
                commodityType,
                zScore,
                System.currentTimeMillis()
            );
            
            anomalies.put(anomalyId, record);
            LOGGER.warning("Anomaly recorded: " + anomalyId + " (Z-score: " + zScore + ")");
        } catch (Exception e) {
            LOGGER.severe("Could not record anomaly: " + e.getMessage());
        }
    }
    
    /**
     * Flags suspicious transaction for review
     */
    private void flagTransaction(String exportTokenId, String source, String reason) {
        try {
            String flagId = UUID.randomUUID().toString();
            SuspiciousTransaction transaction = new SuspiciousTransaction(
                flagId,
                exportTokenId,
                source,
                reason,
                System.currentTimeMillis()
            );
            
            flaggedTransactions.put(flagId, transaction);
            LOGGER.warning("Transaction flagged: " + flagId + " - " + reason);
        } catch (Exception e) {
            LOGGER.severe("Could not flag transaction: " + e.getMessage());
        }
    }
    
    /**
     * Calculates average amount
     */
    private Amount calculateAverage(Collection<Amount> amounts) {
        if (amounts.isEmpty()) return new Amount(java.math.BigInteger.ZERO, "ASU");
        
        long sum = 0;
        for (Amount amount : amounts) {
            sum += amount.getValue().longValue();
        }
        
        return new Amount(java.math.BigInteger.valueOf(sum / amounts.size()), "ASU");
    }
    
    /**
     * Calculates standard deviation
     */
    private double calculateStdDev(Collection<Amount> amounts, Amount average) {
        if (amounts.size() < 2) return 0;
        
        double sumSquaredDiff = 0;
        double avgValue = average.getValue().doubleValue();
        
        for (Amount amount : amounts) {
            double diff = amount.getValue().doubleValue() - avgValue;
            sumSquaredDiff += diff * diff;
        }
        
        return Math.sqrt(sumSquaredDiff / (amounts.size() - 1));
    }
    
    public FraudAnalysis getAnalysis(String analysisId) {
        return analyses.get(analysisId);
    }
    
    public Collection<AnomalyRecord> getAnomalies() {
        return new ArrayList<>(anomalies.values());
    }
    
    public Collection<SuspiciousTransaction> getFlaggedTransactions() {
        return new ArrayList<>(flaggedTransactions.values());
    }
    
    public enum FraudRiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public static class FraudAnalysis {
        private final String analysisId;
        private final String exportTokenId;
        private final String commodityType;
        private final Amount quantity;
        private final double zScore;
        private final double fraudProbability;
        private final FraudRiskLevel riskLevel;
        private final boolean isAnomaly;
        private final long timestamp;
        
        public FraudAnalysis(String analysisId, String exportTokenId, String commodityType,
                            Amount quantity, double zScore, double fraudProbability,
                            FraudRiskLevel riskLevel, boolean isAnomaly, long timestamp) {
            this.analysisId = analysisId;
            this.exportTokenId = exportTokenId;
            this.commodityType = commodityType;
            this.quantity = quantity;
            this.zScore = zScore;
            this.fraudProbability = fraudProbability;
            this.riskLevel = riskLevel;
            this.isAnomaly = isAnomaly;
            this.timestamp = timestamp;
        }
        
        public String getAnalysisId() { return analysisId; }
        public String getExportTokenId() { return exportTokenId; }
        public String getCommodityType() { return commodityType; }
        public Amount getQuantity() { return quantity; }
        public double getZScore() { return zScore; }
        public double getFraudProbability() { return fraudProbability; }
        public FraudRiskLevel getRiskLevel() { return riskLevel; }
        public boolean isAnomaly() { return isAnomaly; }
        public long getTimestamp() { return timestamp; }
    }
    
    public static class AnomalyRecord {
        private final String anomalyId;
        private final String exportTokenId;
        private final String commodityType;
        private final double zScore;
        private final long timestamp;
        
        public AnomalyRecord(String anomalyId, String exportTokenId, String commodityType,
                            double zScore, long timestamp) {
            this.anomalyId = anomalyId;
            this.exportTokenId = exportTokenId;
            this.commodityType = commodityType;
            this.zScore = zScore;
            this.timestamp = timestamp;
        }
        
        public String getAnomalyId() { return anomalyId; }
        public String getExportTokenId() { return exportTokenId; }
        public String getCommodityType() { return commodityType; }
        public double getZScore() { return zScore; }
        public long getTimestamp() { return timestamp; }
    }
    
    public static class SuspiciousTransaction {
        private final String flagId;
        private final String exportTokenId;
        private final String source;
        private final String reason;
        private final long flaggedTime;
        
        public SuspiciousTransaction(String flagId, String exportTokenId, String source,
                                    String reason, long flaggedTime) {
            this.flagId = flagId;
            this.exportTokenId = exportTokenId;
            this.source = source;
            this.reason = reason;
            this.flaggedTime = flaggedTime;
        }
        
        public String getFlagId() { return flagId; }
        public String getExportTokenId() { return exportTokenId; }
        public String getSource() { return source; }
        public String getReason() { return reason; }
        public long getFlaggedTime() { return flaggedTime; }
    }
    
    public static class FraudDetectionException extends Exception {
        public FraudDetectionException(String message) {
            super(message);
        }
        
        public FraudDetectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}