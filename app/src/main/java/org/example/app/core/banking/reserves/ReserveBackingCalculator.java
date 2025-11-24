package org.example.app.core.banking.reserves;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * ReserveBackingCalculator - Calculates reserve backing ratios and collateral metrics
 * 
 * Implements complex backing calculations:
 * 1. Simple backing ratio: Total Reserves / Circulating Supply
 * 2. Weighted backing: Different reserve types weighted differently
 * 3. Haircut adjustments: Liquidity and credit risk adjustments
 * 4. Cross-collateral: ASU and inter-country backing
 * 5. Stress testing: Backing under adverse scenarios
 */
public class ReserveBackingCalculator {
    private static final Logger LOGGER = Logger.getLogger(ReserveBackingCalculator.class.getName());
    
    /**
     * Reserve weighting configuration
     */
    public enum ReserveWeight {
        COMMODITY_GOLD(1.0, "Physical gold - highest weight"),
        COMMODITY_OIL(0.95, "Oil reserves - slight haircut"),
        COMMODITY_MINERALS(0.90, "Strategic minerals - moderate haircut"),
        COMMODITY_AGRICULTURAL(0.85, "Agricultural commodities - volatile"),
        TREASURY_FIAT(0.80, "Foreign currency - counterparty risk"),
        TREASURY_SECURITIES(0.85, "Government bonds - interest rate risk"),
        PRODUCTION_OUTPUT(0.75, "Production proofs - realization risk"),
        CRYPTO_BUFFER(0.50, "Crypto holdings - volatility risk"),
        ASU_COLLATERAL(0.90, "ASU backing - cross-collateral"),
        BANK_DEPOSIT(0.70, "Bank deposits - counterparty risk");
        
        private final double weight;
        private final String description;
        
        ReserveWeight(double weight, String description) {
            this.weight = weight;
            this.description = description;
        }
        
        public double getWeight() { return weight; }
        public String getDescription() { return description; }
    }
    
    /**
     * Haircut adjustments for liquidity and risk
     */
    public static class HaircutAdjustment {
        private final String adjustmentId;
        private final ProofOfReservesContract.ReserveType reserveType;
        private final BigDecimal haircutPercentage;
        private final String reason;
        private final Date effectiveDate;
        private final String authorizedBy;
        
        public HaircutAdjustment(String adjustmentId, ProofOfReservesContract.ReserveType type,
                               BigDecimal haircut, String reason, String authorizedBy) {
            this.adjustmentId = adjustmentId;
            this.reserveType = type;
            this.haircutPercentage = haircut;
            this.reason = reason;
            this.effectiveDate = new Date();
            this.authorizedBy = authorizedBy;
        }
        
        public String getAdjustmentId() { return adjustmentId; }
        public ProofOfReservesContract.ReserveType getReserveType() { return reserveType; }
        public BigDecimal getHaircutPercentage() { return haircutPercentage; }
        public String getReason() { return reason; }
        public Date getEffectiveDate() { return effectiveDate; }
        public String getAuthorizedBy() { return authorizedBy; }
    }
    
    /**
     * Backing calculation result with components
     */
    public static class BackingCalculationResult {
        private final String calculationId;
        private final String tokenSymbol;
        private final BigDecimal totalCirculatingSupply;
        private final BigDecimal totalReserves;
        private final BigDecimal simpleBackingRatio;
        private final BigDecimal weightedBackingRatio;
        private final BigDecimal adjustedBackingRatio;
        private final Map<ProofOfReservesContract.ReserveType, BigDecimal> reservesByType;
        private final Map<ProofOfReservesContract.ReserveType, BigDecimal> weightedReserves;
        private final BigDecimal totalHaircutAmount;
        private final Date calculationDate;
        private final boolean meetsMinimum;
        private final String calculationDetails;
        
        public BackingCalculationResult(String calcId, String tokenSymbol,
                                       BigDecimal supply, BigDecimal reserves,
                                       BigDecimal simpleRatio, BigDecimal weightedRatio,
                                       BigDecimal adjustedRatio,
                                       Map<ProofOfReservesContract.ReserveType, BigDecimal> byType,
                                       Map<ProofOfReservesContract.ReserveType, BigDecimal> weighted,
                                       BigDecimal haircut, boolean meets, String details) {
            this.calculationId = calcId;
            this.tokenSymbol = tokenSymbol;
            this.totalCirculatingSupply = supply;
            this.totalReserves = reserves;
            this.simpleBackingRatio = simpleRatio;
            this.weightedBackingRatio = weightedRatio;
            this.adjustedBackingRatio = adjustedRatio;
            this.reservesByType = new HashMap<>(byType);
            this.weightedReserves = new HashMap<>(weighted);
            this.totalHaircutAmount = haircut;
            this.calculationDate = new Date();
            this.meetsMinimum = meets;
            this.calculationDetails = details;
        }
        
        public String getCalculationId() { return calculationId; }
        public String getTokenSymbol() { return tokenSymbol; }
        public BigDecimal getTotalCirculatingSupply() { return totalCirculatingSupply; }
        public BigDecimal getTotalReserves() { return totalReserves; }
        public BigDecimal getSimpleBackingRatio() { return simpleBackingRatio; }
        public BigDecimal getWeightedBackingRatio() { return weightedBackingRatio; }
        public BigDecimal getAdjustedBackingRatio() { return adjustedBackingRatio; }
        public Map<ProofOfReservesContract.ReserveType, BigDecimal> getReservesByType() { 
            return new HashMap<>(reservesByType); 
        }
        public Map<ProofOfReservesContract.ReserveType, BigDecimal> getWeightedReserves() { 
            return new HashMap<>(weightedReserves); 
        }
        public BigDecimal getTotalHaircutAmount() { return totalHaircutAmount; }
        public Date getCalculationDate() { return calculationDate; }
        public boolean meetsMini minimum() { return meetsMinimum; }
        public String getCalculationDetails() { return calculationDetails; }
    }
    
    /**
     * Stress test scenario
     */
    public static class StressTestScenario {
        private final String scenarioId;
        private final String scenarioName;
        private final BigDecimal commodityPriceShock; // -0.30 = 30% price drop
        private final BigDecimal treasuryValueShock;
        private final BigDecimal liquidityShock; // Portion of reserves become illiquid
        private final BigDecimal creditShock; // Bank default risk
        private final Date scenarioDate;
        
        public StressTestScenario(String scenarioId, String name, BigDecimal commodity,
                                BigDecimal treasury, BigDecimal liquidity, BigDecimal credit) {
            this.scenarioId = scenarioId;
            this.scenarioName = name;
            this.commodityPriceShock = commodity;
            this.treasuryValueShock = treasury;
            this.liquidityShock = liquidity;
            this.creditShock = credit;
            this.scenarioDate = new Date();
        }
        
        public String getScenarioId() { return scenarioId; }
        public String getScenarioName() { return scenarioName; }
        public BigDecimal getCommodityPriceShock() { return commodityPriceShock; }
        public BigDecimal getTreasuryValueShock() { return treasuryValueShock; }
        public BigDecimal getLiquidityShock() { return liquidityShock; }
        public BigDecimal getCreditShock() { return creditShock; }
        public Date getScenarioDate() { return scenarioDate; }
    }
    
    /**
     * Stress test result
     */
    public static class StressTestResult {
        private final String testId;
        private final String tokenSymbol;
        private final StressTestScenario scenario;
        private final BigDecimal baselineBackingRatio;
        private final BigDecimal stressedBackingRatio;
        private final BigDecimal backingRatioChange;
        private final boolean survivesStress;
        private final Map<ProofOfReservesContract.ReserveType, BigDecimal> stressedReserves;
        private final String analysis;
        
        public StressTestResult(String testId, String tokenSymbol, StressTestScenario scenario,
                              BigDecimal baseline, BigDecimal stressed,
                              Map<ProofOfReservesContract.ReserveType, BigDecimal> reserves,
                              boolean survives, String analysis) {
            this.testId = testId;
            this.tokenSymbol = tokenSymbol;
            this.scenario = scenario;
            this.baselineBackingRatio = baseline;
            this.stressedBackingRatio = stressed;
            this.backingRatioChange = stressed.subtract(baseline);
            this.survivesStress = survives;
            this.stressedReserves = new HashMap<>(reserves);
            this.analysis = analysis;
        }
        
        public String getTestId() { return testId; }
        public String getTokenSymbol() { return tokenSymbol; }
        public StressTestScenario getScenario() { return scenario; }
        public BigDecimal getBaselineBackingRatio() { return baselineBackingRatio; }
        public BigDecimal getStressedBackingRatio() { return stressedBackingRatio; }
        public BigDecimal getBackingRatioChange() { return backingRatioChange; }
        public boolean survivesStress() { return survivesStress; }
        public Map<ProofOfReservesContract.ReserveType, BigDecimal> getStressedReserves() { 
            return new HashMap<>(stressedReserves); 
        }
        public String getAnalysis() { return analysis; }
    }
    
    // Storage
    private final Map<String, HaircutAdjustment> haircutAdjustments;
    private final Map<String, BackingCalculationResult> calculationResults;
    private final Map<String, StressTestResult> stressTestResults;
    private final Map<ProofOfReservesContract.ReserveType, BigDecimal> reserveWeights;
    
    // Minimum backing threshold
    private static final BigDecimal MINIMUM_BACKING = new BigDecimal("1.20");
    
    public ReserveBackingCalculator() {
        this.haircutAdjustments = new ConcurrentHashMap<>();
        this.calculationResults = new ConcurrentHashMap<>();
        this.stressTestResults = new ConcurrentHashMap<>();
        this.reserveWeights = new ConcurrentHashMap<>();
        
        initializeReserveWeights();
        
        LOGGER.info("ReserveBackingCalculator initialized");
    }
    
    /**
     * Calculates simple backing ratio
     */
    public BigDecimal calculateBackingRatio(BigDecimal totalReserves, BigDecimal circulatingSupply)
            throws Exception {
        
        if (circulatingSupply.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Circulating supply must be positive");
        }
        
        return totalReserves.divide(circulatingSupply, 4, RoundingMode.HALF_UP);
    }
    
    /**
     * Calculates weighted backing ratio with reserve type weights
     */
    public BigDecimal calculateWeightedBackingRatio(
            Map<ProofOfReservesContract.ReserveType, BigDecimal> reservesByType,
            BigDecimal circulatingSupply) throws Exception {
        
        BigDecimal weightedReserves = BigDecimal.ZERO;
        
        for (Map.Entry<ProofOfReservesContract.ReserveType, BigDecimal> entry : 
             reservesByType.entrySet()) {
            
            BigDecimal weight = reserveWeights.getOrDefault(entry.getKey(), BigDecimal.ONE);
            BigDecimal weighted = entry.getValue().multiply(weight);
            weightedReserves = weightedReserves.add(weighted);
        }
        
        return weightedReserves.divide(circulatingSupply, 4, RoundingMode.HALF_UP);
    }
    
    /**
     * Calculates adjusted backing ratio with haircuts
     */
    public BigDecimal calculateAdjustedBackingRatio(
            Map<ProofOfReservesContract.ReserveType, BigDecimal> reservesByType,
            BigDecimal circulatingSupply) throws Exception {
        
        BigDecimal adjustedReserves = BigDecimal.ZERO;
        
        for (Map.Entry<ProofOfReservesContract.ReserveType, BigDecimal> entry : 
             reservesByType.entrySet()) {
            
            BigDecimal value = entry.getValue();
            
            // Apply haircut if exists
            Collection<HaircutAdjustment> adjustments = haircutAdjustments.values();
            for (HaircutAdjustment adj : adjustments) {
                if (adj.getReserveType() == entry.getKey()) {
                    BigDecimal haircutFactor = BigDecimal.ONE.subtract(
                        adj.getHaircutPercentage().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                    );
                    value = value.multiply(haircutFactor);
                }
            }
            
            // Apply weight
            BigDecimal weight = reserveWeights.getOrDefault(entry.getKey(), BigDecimal.ONE);
            BigDecimal adjusted = value.multiply(weight);
            adjustedReserves = adjustedReserves.add(adjusted);
        }
        
        return adjustedReserves.divide(circulatingSupply, 4, RoundingMode.HALF_UP);
    }
    
    /**
     * Performs complete backing calculation
     */
    public BackingCalculationResult performFullBackingCalculation(String tokenSymbol,
            Map<ProofOfReservesContract.ReserveType, BigDecimal> reservesByType,
            BigDecimal circulatingSupply) throws Exception {
        
        String calcId = generateCalculationId(tokenSymbol);
        
        // Calculate totals
        BigDecimal totalReserves = reservesByType.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Simple ratio
        BigDecimal simpleRatio = calculateBackingRatio(totalReserves, circulatingSupply);
        
        // Weighted ratio
        Map<ProofOfReservesContract.ReserveType, BigDecimal> weightedReserves = 
            calculateWeightedReservesByType(reservesByType);
        BigDecimal weightedRatio = calculateWeightedBackingRatio(reservesByType, circulatingSupply);
        
        // Adjusted ratio
        BigDecimal adjustedRatio = calculateAdjustedBackingRatio(reservesByType, circulatingSupply);
        
        // Calculate haircuts
        BigDecimal totalHaircut = totalReserves.subtract(
            weightedReserves.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
        );
        
        // Determine if meets minimum
        boolean meets = adjustedRatio.compareTo(MINIMUM_BACKING) >= 0;
        
        // Create result
        BackingCalculationResult result = new BackingCalculationResult(
            calcId,
            tokenSymbol,
            circulatingSupply,
            totalReserves,
            simpleRatio,
            weightedRatio,
            adjustedRatio,
            reservesByType,
            weightedReserves,
            totalHaircut,
            meets,
            "Full backing calculation completed"
        );
        
        calculationResults.put(calcId, result);
        
        LOGGER.info(String.format(
            "Backing calculation complete: %s - Simple: %.2f%%, Weighted: %.2f%%, Adjusted: %.2f%%",
            tokenSymbol, simpleRatio.multiply(new BigDecimal("100")),
            weightedRatio.multiply(new BigDecimal("100")),
            adjustedRatio.multiply(new BigDecimal("100"))
        ));
        
        return result;
    }
    
    /**
     * Performs stress testing on reserves
     */
    public StressTestResult runStressTest(String tokenSymbol,
            Map<ProofOfReservesContract.ReserveType, BigDecimal> baselineReserves,
            BigDecimal circulatingSupply,
            StressTestScenario scenario) throws Exception {
        
        String testId = generateStressTestId(tokenSymbol);
        
        // Calculate baseline ratio
        BigDecimal baselineRatio = calculateAdjustedBackingRatio(baselineReserves, circulatingSupply);
        
        // Apply shocks to reserves
        Map<ProofOfReservesContract.ReserveType, BigDecimal> stressedReserves = new HashMap<>();
        
        for (Map.Entry<ProofOfReservesContract.ReserveType, BigDecimal> entry : 
             baselineReserves.entrySet()) {
            
            BigDecimal value = entry.getValue();
            
            // Apply commodity price shock
            if (entry.getKey().toString().contains("COMMODITY")) {
                BigDecimal shockFactor = BigDecimal.ONE.add(scenario.getCommodityPriceShock());
                value = value.multiply(shockFactor);
            }
            
            // Apply treasury shock
            if (entry.getKey().toString().contains("TREASURY")) {
                BigDecimal shockFactor = BigDecimal.ONE.add(scenario.getTreasuryValueShock());
                value = value.multiply(shockFactor);
            }
            
            // Apply liquidity shock
            BigDecimal liquidityFactor = BigDecimal.ONE.subtract(scenario.getLiquidityShock());
            value = value.multiply(liquidityFactor);
            
            stressedReserves.put(entry.getKey(), value);
        }
        
        // Calculate stressed ratio
        BigDecimal stressedRatio = calculateAdjustedBackingRatio(stressedReserves, circulatingSupply);
        
        // Determine if survives
        boolean survives = stressedRatio.compareTo(MINIMUM_BACKING) >= 0;
        
        // Create result
        StressTestResult result = new StressTestResult(
            testId,
            tokenSymbol,
            scenario,
            baselineRatio,
            stressedRatio,
            stressedReserves,
            survives,
            String.format("Scenario: %s - Backing drops from %.2f%% to %.2f%%",
                scenario.getScenarioName(),
                baselineRatio.multiply(new BigDecimal("100")),
                stressedRatio.multiply(new BigDecimal("100")))
        );
        
        stressTestResults.put(testId, result);
        
        LOGGER.info(String.format(
            "Stress test completed: %s - Baseline: %.2f%%, Stressed: %.2f%%, Survives: %b",
            testId, baselineRatio.multiply(new BigDecimal("100")),
            stressedRatio.multiply(new BigDecimal("100")), survives
        ));
        
        return result;
    }
    
    /**
     * Helper methods
     */
    
    private void initializeReserveWeights() {
        reserveWeights.put(ProofOfReservesContract.ReserveType.COMMODITY_GOLD, 
            new BigDecimal("1.0"));
        reserveWeights.put(ProofOfReservesContract.ReserveType.COMMODITY_OIL, 
            new BigDecimal("0.95"));
        reserveWeights.put(ProofOfReservesContract.ReserveType.COMMODITY_MINERALS, 
            new BigDecimal("0.90"));
        reserveWeights.put(ProofOfReservesContract.ReserveType.COMMODITY_AGRICULTURAL, 
            new BigDecimal("0.85"));
        reserveWeights.put(ProofOfReservesContract.ReserveType.TREASURY_FIAT, 
            new BigDecimal("0.80"));
        reserveWeights.put(ProofOfReservesContract.ReserveType.TREASURY_SECURITIES, 
            new BigDecimal("0.85"));
        reserveWeights.put(ProofOfReservesContract.ReserveType.PRODUCTION_OUTPUT, 
            new BigDecimal("0.75"));
        reserveWeights.put(ProofOfReservesContract.ReserveType.CRYPTO_BUFFER, 
            new BigDecimal("0.50"));
        reserveWeights.put(ProofOfReservesContract.ReserveType.ASU_COLLATERAL, 
            new BigDecimal("0.90"));
        reserveWeights.put(ProofOfReservesContract.ReserveType.BANK_DEPOSIT, 
            new BigDecimal("0.70"));
    }
    
    private Map<ProofOfReservesContract.ReserveType, BigDecimal> calculateWeightedReservesByType(
            Map<ProofOfReservesContract.ReserveType, BigDecimal> baseReserves) {
        
        Map<ProofOfReservesContract.ReserveType, BigDecimal> weighted = new HashMap<>();
        
        for (Map.Entry<ProofOfReservesContract.ReserveType, BigDecimal> entry : baseReserves.entrySet()) {
            BigDecimal weight = reserveWeights.getOrDefault(entry.getKey(), BigDecimal.ONE);
            weighted.put(entry.getKey(), entry.getValue().multiply(weight));
        }
        
        return weighted;
    }
    
    private String generateCalculationId(String tokenSymbol) {
        return "CALC_" + tokenSymbol + "_" + System.currentTimeMillis();
    }
    
    private String generateStressTestId(String tokenSymbol) {
        return "STRESS_" + tokenSymbol + "_" + System.currentTimeMillis();
    }
}