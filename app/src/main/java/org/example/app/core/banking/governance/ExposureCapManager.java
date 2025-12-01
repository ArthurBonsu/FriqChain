package org.example.app.core.banking.governance;

import org.example.app.core.banking.tokenization.TokenMintingController;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * ExposureCapManager - Foreign currency exposure limits (≤10%)
 * 
 * Monitors and enforces:
 * - Maximum 10% exposure to USD/EUR/RMB
 * - Auto-triggers circuit breaker if exceeded
 * - Pauses synthetic FX minting (sUSD, sEUR)
 * - Auto-liquidates excess exposure back to ASU
 */
public class ExposureCapManager {
    private static final Logger LOGGER = Logger.getLogger(ExposureCapManager.class.getName());
    
    private static final BigDecimal MAX_FOREIGN_EXPOSURE = new BigDecimal("0.10"); // 10%
    private static final long MONITORING_INTERVAL = 15 * 60 * 1000L; // 15 minutes
    
    private final TokenMintingController mintingController;
    private final GovernanceAuditLog auditLog;
    private final ScheduledExecutorService scheduler;
    
    private final Map<String, BigDecimal> foreignExposure; // Currency -> exposure %
    private BigDecimal totalForeignExposure;
    private boolean circuitBreakerActive;
    
    public ExposureCapManager(TokenMintingController mintingController,
                             GovernanceAuditLog auditLog) {
        this.mintingController = mintingController;
        this.auditLog = auditLog;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.foreignExposure = new ConcurrentHashMap<>();
        this.totalForeignExposure = BigDecimal.ZERO;
        this.circuitBreakerActive = false;
        
        startMonitoring();
        LOGGER.info("ExposureCapManager initialized");
    }
    
    /**
     * Monitors foreign currency exposure (called every 15 minutes)
     */
    public void monitorForeignExposure() {
        // Calculate current exposure
        calculateCurrentExposure();
        
        if (totalForeignExposure.compareTo(MAX_FOREIGN_EXPOSURE) > 0) {
            LOGGER.severe("FOREIGN EXPOSURE LIMIT EXCEEDED: " + 
                totalForeignExposure + " > " + MAX_FOREIGN_EXPOSURE);
            
            enforceExposureCap();
        } else if (circuitBreakerActive) {
            // Check if we can resume (exposure back under limit)
            if (totalForeignExposure.compareTo(new BigDecimal("0.08")) <= 0) { // 8% threshold
                resumeNormalOperations();
            }
        }
    }
    
    /**
     * Enforces exposure cap by activating circuit breaker
     */
    public void enforceExposureCap() {
        if (circuitBreakerActive) {
            LOGGER.warning("Circuit breaker already active");
            return;
        }
        
        LOGGER.severe("Activating circuit breaker - foreign exposure: " + totalForeignExposure);
        circuitBreakerActive = true;
        
        // 1. Pause synthetic FX minting
        mintingController.pauseMinting("sUSD");
        mintingController.pauseMinting("sEUR");
        mintingController.pauseMinting("sRMB");
        
        // 2. Calculate liquidation amount
        BigDecimal excessExposure = totalForeignExposure.subtract(new BigDecimal("0.08")); // Target 8%
        BigDecimal liquidationAmount = calculateLiquidationAmount(excessExposure);
        
        // 3. Execute liquidation (convert excess FX back to ASU)
        liquidateSyntheticFX(liquidationAmount);
        
        // 4. Record in audit log
        auditLog.recordEvent(GovernanceAuditLog.AuditEventType.CIRCUIT_BREAKER_ACTIVATED,
            "EXPOSURE_CAP_MANAGER", "FOREIGN_EXPOSURE",
            Map.of("totalExposure", totalForeignExposure.toString(),
                   "maxExposure", MAX_FOREIGN_EXPOSURE.toString(),
                   "liquidationAmount", liquidationAmount.toString()),
            "Circuit breaker activated - foreign exposure limit exceeded");
        
        auditLog.recordEvent(GovernanceAuditLog.AuditEventType.FX_EXPOSURE_LIMIT_REACHED,
            "EXPOSURE_CAP_MANAGER", "SYSTEM",
            foreignExposure.entrySet().stream()
                .collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue().toString()), HashMap::putAll),
            "Foreign exposure cap triggered");
    }
    
    /**
     * Liquidates synthetic FX tokens back to ASU
     */
    private void liquidateSyntheticFX(BigDecimal amount) {
        LOGGER.warning("Liquidating synthetic FX: " + amount);
        
        // In production: execute actual liquidation through DEX
        // Convert sUSD/sEUR/sRMB back to ASU
        
        // Recalculate exposure after liquidation
        calculateCurrentExposure();
        
        LOGGER.info("Foreign exposure after liquidation: " + totalForeignExposure);
    }
    
    /**
     * Resumes normal operations after exposure drops below threshold
     */
    private void resumeNormalOperations() {
        LOGGER.info("Resuming normal operations - exposure: " + totalForeignExposure);
        
        circuitBreakerActive = false;
        
        // Resume synthetic FX minting
        mintingController.resumeMinting("sUSD");
        mintingController.resumeMinting("sEUR");
        mintingController.resumeMinting("sRMB");
        
        auditLog.recordEvent(GovernanceAuditLog.AuditEventType.CIRCUIT_BREAKER_ACTIVATED,
            "EXPOSURE_CAP_MANAGER", "FOREIGN_EXPOSURE",
            Map.of("totalExposure", totalForeignExposure.toString(),
                   "status", "DEACTIVATED"),
            "Circuit breaker deactivated - exposure normalized");
    }
    
    private void calculateCurrentExposure() {
        // In production: query actual holdings
        // For now: placeholder
        foreignExposure.put("USD", new BigDecimal("0.06")); // 6%
        foreignExposure.put("EUR", new BigDecimal("0.03")); // 3%
        foreignExposure.put("RMB", new BigDecimal("0.02")); // 2%
        
        totalForeignExposure = foreignExposure.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal calculateLiquidationAmount(BigDecimal excessPercentage) {
        // Calculate actual token amount to liquidate
        // Simplified: assume 1B ASU total value
        BigDecimal totalValue = new BigDecimal("1000000000");
        return totalValue.multiply(excessPercentage);
    }
    
    private void startMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                monitorForeignExposure();
            } catch (Exception e) {
                LOGGER.severe("Error in exposure monitoring: " + e.getMessage());
            }
        }, 1, 15, TimeUnit.MINUTES);
    }
    
    public BigDecimal getTotalForeignExposure() { return totalForeignExposure; }
    public boolean isCircuitBreakerActive() { return circuitBreakerActive; }
    
    public void shutdown() {
        scheduler.shutdown();
    }
}