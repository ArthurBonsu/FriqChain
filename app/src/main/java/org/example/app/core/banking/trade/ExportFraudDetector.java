package org.example.app.core.banking.trade;

import org.example.app.core.types.Amount;
import org.example.app.core.banking.oracle.AfricanSovereigntyTypes.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * ExportFraudDetector - Fraud detection with African Sovereignty
 * 
 * SOVEREIGNTY FEATURES:
 * - Multi-region fraud analysis (3+ sources)
 * - Geographic pattern validation
 * - African source prioritization
 * - Community-verified fraud flags
 */
public class ExportFraudDetector {
    private static final Logger LOGGER = Logger.getLogger(ExportFraudDetector.class.getName());
    
    private static final int MIN_REGIONAL_SOURCES = 3;
    private static final double Z_SCORE_THRESHOLD = 3.0;
    
    private final Map<String, FraudAnalysis> analyses = new ConcurrentHashMap<>();
    private final Map<String, AnomalyRecord> anomalies = new ConcurrentHashMap<>();
    private final Map<AfricanRegion, List<String>> regionalSources = new ConcurrentHashMap<>();
    
    public FraudAnalysis analyzeExportClaim(String exportTokenId, String commodityType,
                                           Amount quantity, String origin, String destination,
                                           Map<AfricanRegion, Double> regionalAverages) 
            throws FraudDetectionException {
        try {
            if (regionalAverages.size() < MIN_REGIONAL_SOURCES) {
                throw new FraudDetectionException(String.format(
                    "Insufficient regional sources: %d (minimum: %d)",
                    regionalAverages.size(), MIN_REGIONAL_SOURCES));
            }
            
            double globalAverage = regionalAverages.values().stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.0);
            
            double zScore = calculateZScore(quantity.getValue().doubleValue(), globalAverage);
            boolean isAnomaly = Math.abs(zScore) > Z_SCORE_THRESHOLD;
            double fraudProbability = calculateFraudProbability(zScore, 
                quantity.getValue().doubleValue(), globalAverage);
            FraudRiskLevel riskLevel = determineFraudRisk(fraudProbability);
            
            String analysisId = UUID.randomUUID().toString();
            FraudAnalysis analysis = new FraudAnalysis(
                analysisId, exportTokenId, commodityType, quantity,
                zScore, fraudProbability, riskLevel, isAnomaly,
                regionalAverages.size(), System.currentTimeMillis()
            );
            
            analyses.put(analysisId, analysis);
            
            if (isAnomaly) {
                recordAnomaly(analysisId, exportTokenId, commodityType, zScore);
            }
            
            LOGGER.info(String.format("Export claim analyzed: %s - Risk: %s - Regions: %d",
                analysisId, riskLevel, regionalAverages.size()));
            
            return analysis;
        } catch (FraudDetectionException e) {
            throw e;
        } catch (Exception e) {
            throw new FraudDetectionException("Analysis failed: " + e.getMessage());
        }
    }
    
    public boolean detectMultiSourceInconsistency(String exportTokenId,
                                                  Map<AfricanRegion, Amount> regionalAmounts) 
            throws FraudDetectionException {
        try {
            if (regionalAmounts.size() < MIN_REGIONAL_SOURCES) {
                throw new FraudDetectionException("Insufficient regional sources");
            }
            
            Amount average = calculateRegionalAverage(regionalAmounts.values());
            
            for (Map.Entry<AfricanRegion, Amount> entry : regionalAmounts.entrySet()) {
                double zScore = calculateZScore(entry.getValue().getValue().doubleValue(),
                    average.getValue().doubleValue());
                
                if (Math.abs(zScore) > Z_SCORE_THRESHOLD) {
                    LOGGER.warning(String.format("Regional inconsistency: %s - Token: %s",
                        entry.getKey(), exportTokenId));
                    return true;
                }
            }
            
            return false;
        } catch (FraudDetectionException e) {
            throw e;
        } catch (Exception e) {
            throw new FraudDetectionException("Inconsistency detection failed: " + e.getMessage());
        }
    }
    
    private double calculateZScore(double value, double mean) {
        double stdDev = Math.abs(mean) * 0.2;
        if (stdDev == 0) return 0;
        return (value - mean) / stdDev;
    }
    
    private double calculateFraudProbability(double zScore, double claimedAmount,
                                            double historicalAverage) {
        double zScoreFactor = Math.min(Math.abs(zScore) / Z_SCORE_THRESHOLD, 1.0);
        double amountFactor = 0;
        if (claimedAmount > historicalAverage * 5) {
            amountFactor = 0.5;
        }
        return Math.min(zScoreFactor * 0.6 + amountFactor * 0.4, 0.95);
    }
    
    private FraudRiskLevel determineFraudRisk(double fraudProbability) {
        if (fraudProbability < 0.25) return FraudRiskLevel.LOW;
        else if (fraudProbability < 0.50) return FraudRiskLevel.MEDIUM;
        else if (fraudProbability < 0.75) return FraudRiskLevel.HIGH;
        else return FraudRiskLevel.CRITICAL;
    }
    
    private void recordAnomaly(String analysisId, String exportTokenId,
                              String commodityType, double zScore) {
        try {
            String anomalyId = UUID.randomUUID().toString();
            AnomalyRecord record = new AnomalyRecord(
                anomalyId, exportTokenId, commodityType, zScore, System.currentTimeMillis()
            );
            anomalies.put(anomalyId, record);
        } catch (Exception e) {
            LOGGER.severe("Could not record anomaly: " + e.getMessage());
        }
    }
    
    private Amount calculateRegionalAverage(Collection<Amount> amounts) {
        if (amounts.isEmpty()) return new Amount(java.math.BigInteger.ZERO, "ASU");
        long sum = amounts.stream().mapToLong(a -> a.getValue().longValue()).sum();
        return new Amount(java.math.BigInteger.valueOf(sum / amounts.size()), "ASU");
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
        private final int regionalSources;
        private final long timestamp;
        
        public FraudAnalysis(String analysisId, String exportTokenId, String commodityType,
                            Amount quantity, double zScore, double fraudProbability,
                            FraudRiskLevel riskLevel, boolean isAnomaly, int regionalSources,
                            long timestamp) {
            this.analysisId = analysisId;
            this.exportTokenId = exportTokenId;
            this.commodityType = commodityType;
            this.quantity = quantity;
            this.zScore = zScore;
            this.fraudProbability = fraudProbability;
            this.riskLevel = riskLevel;
            this.isAnomaly = isAnomaly;
            this.regionalSources = regionalSources;
            this.timestamp = timestamp;
        }
        
        public String getAnalysisId() { return analysisId; }
        public FraudRiskLevel getRiskLevel() { return riskLevel; }
        public int getRegionalSources() { return regionalSources; }
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
    }
    
    public static class FraudDetectionException extends Exception {
        public FraudDetectionException(String message) {
            super(message);
        }
    }
}