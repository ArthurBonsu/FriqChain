package org.example.app.core.banking.oracle;
import org.example.app.core.types.Address;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * ReserveAuditOracle - Reserve verification and proof-of-reserves
 * 
 * Integrates with ProofOfReservesValidator for comprehensive reserve auditing
 */
public class ReserveAuditOracle extends OracleReporter {
    private static final Logger LOGGER = Logger.getLogger(ReserveAuditOracle.class.getName());
    
    private final Map<String, ReserveAudit> auditRecords;
    
    public static class ReserveAudit {
        private final String tokenSymbol;
        private final BigDecimal totalReserves;
        private final BigDecimal circulatingSupply;
        private final BigDecimal backingRatio;
        private final List<String> auditors;
        private final boolean passed;
        private final long auditTime;
        
        public ReserveAudit(String tokenSymbol, BigDecimal totalReserves, 
                          BigDecimal circulatingSupply, List<String> auditors, boolean passed) {
            this.tokenSymbol = tokenSymbol;
            this.totalReserves = totalReserves;
            this.circulatingSupply = circulatingSupply;
            this.backingRatio = circulatingSupply.compareTo(BigDecimal.ZERO) == 0 ? 
                BigDecimal.ZERO : totalReserves.divide(circulatingSupply, 4, BigDecimal.ROUND_HALF_UP);
            this.auditors = new ArrayList<>(auditors);
            this.passed = passed;
            this.auditTime = System.currentTimeMillis();
        }
        
        public String getTokenSymbol() { return tokenSymbol; }
        public BigDecimal getTotalReserves() { return totalReserves; }
        public BigDecimal getCirculatingSupply() { return circulatingSupply; }
        public BigDecimal getBackingRatio() { return backingRatio; }
        public List<String> getAuditors() { return new ArrayList<>(auditors); }
        public boolean isPassed() { return passed; }
        public long getAuditTime() { return auditTime; }
    }
    
    public ReserveAuditOracle(String reporterId, Address reporterAddress, BigDecimal minimumStake) {
        super(reporterId, "RESERVE_AUDIT", reporterAddress, minimumStake);
        this.auditRecords = new ConcurrentHashMap<>();
        LOGGER.info("ReserveAuditOracle initialized: " + reporterId);
    }
    
    @Override
    public OracleData fetchData(String dataKey) throws Exception {
        String tokenSymbol = dataKey.replace("AUDIT:", "");
        
        ReserveAudit audit = auditRecords.get(tokenSymbol);
        if (audit == null) {
            throw new Exception("No audit found for token: " + tokenSymbol);
        }
        
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("token", audit.getTokenSymbol());
        auditData.put("reserves", audit.getTotalReserves());
        auditData.put("supply", audit.getCirculatingSupply());
        auditData.put("ratio", audit.getBackingRatio());
        auditData.put("auditors", audit.getAuditors());
        auditData.put("passed", audit.isPassed());
        
        byte[] signature = new byte[32];
        submitData("RESERVE_AUDIT", auditData, signature);
        
        return new OracleData("RESERVE_AUDIT", auditData, reporterId,
            new BigDecimal("0.98"), signature);
    }
    
    public void submitAudit(String tokenSymbol, BigDecimal totalReserves,
                          BigDecimal circulatingSupply, List<String> auditors) {
        boolean passed = totalReserves.divide(circulatingSupply, 4, BigDecimal.ROUND_HALF_UP)
            .compareTo(new BigDecimal("1.2")) >= 0; // 120% minimum
        
        ReserveAudit audit = new ReserveAudit(tokenSymbol, totalReserves, circulatingSupply, auditors, passed);
        auditRecords.put(tokenSymbol, audit);
        
        LOGGER.info(String.format("Reserve audit submitted: %s - %s", tokenSymbol, passed ? "PASSED" : "FAILED"));
    }
    
    @Override
    protected boolean validateDataFormat(Object data) {
        return data instanceof Map;
    }
    
    @Override
    protected String getDataTypeIdentifier() {
        return "RESERVE_AUDIT";
    }
}
