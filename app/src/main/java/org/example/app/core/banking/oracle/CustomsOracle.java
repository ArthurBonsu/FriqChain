package org.example.app.core.banking.oracle;

import org.example.app.core.types.Address;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * CustomsOracle - Customs and port data oracle for export validation
 * 
 * Provides verified customs data for:
 * - Export token validation
 * - Bill of lading verification
 * - Customs clearance confirmation
 * - Container tracking
 * - Trade volume verification
 * 
 * Data sources:
 * - Port authority systems
 * - Customs databases
 * - Shipping manifests
 * - IoT container trackers
 * 
 * Based on Implementation Guidebook Section VI: Oracle Data Integrity - Export Validation
 */
public class CustomsOracle extends OracleReporter {
    private static final Logger LOGGER = Logger.getLogger(CustomsOracle.class.getName());
    
    // Validation parameters
    private static final int MIN_SIGNATURES_REQUIRED = 2; // Port + Customs minimum
    
    // Customs record tracking
    private final Map<String, CustomsRecord> customsRecords;
    private final Map<String, List<CustomsVerification>> verificationHistory;
    
    /**
     * Customs record
     */
    public static class CustomsRecord {
        private final String recordId;
        private final String exporterId;
        private final String destinationCountry;
        private final String commodityType;
        private final BigDecimal quantity;
        private final BigDecimal declaredValue;
        private final String billOfLadingNumber;
        private final String containerNumber;
        private final long clearanceTimestamp;
        private final List<byte[]> signatures;
        private final Map<String, Object> metadata;
        
        public CustomsRecord(String recordId, String exporterId, String destinationCountry,
                           String commodityType, BigDecimal quantity, BigDecimal declaredValue,
                           String billOfLadingNumber, String containerNumber) {
            this.recordId = recordId;
            this.exporterId = exporterId;
            this.destinationCountry = destinationCountry;
            this.commodityType = commodityType;
            this.quantity = quantity;
            this.declaredValue = declaredValue;
            this.billOfLadingNumber = billOfLadingNumber;
            this.containerNumber = containerNumber;
            this.clearanceTimestamp = System.currentTimeMillis();
            this.signatures = new ArrayList<>();
            this.metadata = new HashMap<>();
        }
        
        public String getRecordId() { return recordId; }
        public String getExporterId() { return exporterId; }
        public String getDestinationCountry() { return destinationCountry; }
        public String getCommodityType() { return commodityType; }
        public BigDecimal getQuantity() { return quantity; }
        public BigDecimal getDeclaredValue() { return declaredValue; }
        public String getBillOfLadingNumber() { return billOfLadingNumber; }
        public String getContainerNumber() { return containerNumber; }
        public long getClearanceTimestamp() { return clearanceTimestamp; }
        public List<byte[]> getSignatures() { return new ArrayList<>(signatures); }
        public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
        
        public void addSignature(byte[] signature) {
            signatures.add(signature);
        }
        
        public void addMetadata(String key, Object value) {
            metadata.put(key, value);
        }
        
        public boolean hasMinimumSignatures() {
            return signatures.size() >= MIN_SIGNATURES_REQUIRED;
        }
    }
    
    /**
     * Customs verification event
     */
    public static class CustomsVerification {
        private final String recordId;
        private final String verifierType; // "PORT_AUTHORITY", "CUSTOMS", "SATELLITE"
        private final boolean verified;
        private final String note;
        private final long timestamp;
        
        public CustomsVerification(String recordId, String verifierType, 
                                  boolean verified, String note) {
            this.recordId = recordId;
            this.verifierType = verifierType;
            this.verified = verified;
            this.note = note;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRecordId() { return recordId; }
        public String getVerifierType() { return verifierType; }
        public boolean isVerified() { return verified; }
        public String getNote() { return note; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Constructor
     */
    public CustomsOracle(String reporterId, Address reporterAddress, BigDecimal minimumStake,
                        AfricanSovereigntyTypes.AfricanRegion region, String country) {
        super(reporterId, "CUSTOMS", reporterAddress, minimumStake, region, country,
              AfricanSovereigntyTypes.NodeType.CUSTOMS_ORACLE, true);
        
        this.customsRecords = new ConcurrentHashMap<>();
        this.verificationHistory = new ConcurrentHashMap<>();
        
        LOGGER.info("CustomsOracle initialized: " + reporterId);
    }
    
    /**
     * Fetches customs data for export validation
     */
    @Override
    public OracleData fetchData(String dataKey) throws Exception {
        // DataKey format: "EXPORT:exporterId:billOfLadingNumber"
        String[] parts = dataKey.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid customs data key: " + dataKey);
        }
        
        String exporterId = parts[1];
        String bolNumber = parts[2];
        
        // Look up customs record
        CustomsRecord record = findRecord(exporterId, bolNumber);
        
        if (record == null) {
            throw new Exception("Customs record not found: " + dataKey);
        }
        
        // Verify minimum signatures
        if (!record.hasMinimumSignatures()) {
            throw new Exception("Insufficient signatures on customs record: " + dataKey);
        }
        
        // Create oracle data with customs verification
        Map<String, Object> customsData = new HashMap<>();
        customsData.put("recordId", record.getRecordId());
        customsData.put("exporterId", record.getExporterId());
        customsData.put("destination", record.getDestinationCountry());
        customsData.put("commodity", record.getCommodityType());
        customsData.put("quantity", record.getQuantity());
        customsData.put("declaredValue", record.getDeclaredValue());
        customsData.put("billOfLading", record.getBillOfLadingNumber());
        customsData.put("container", record.getContainerNumber());
        customsData.put("clearanceTime", record.getClearanceTimestamp());
        customsData.put("signatureCount", record.getSignatures().size());
        
        // Calculate confidence based on verification sources
        BigDecimal confidence = calculateConfidence(record);
        
        byte[] signature = signData(customsData);
        submitData("CUSTOMS", customsData, signature);
        
        OracleData oracleData = new OracleData("CUSTOMS", customsData, reporterId, 
            confidence, signature);
        
        // oracleData.addMetadata("verified", true); // TODO: OracleData doesn't have addMetadata
        // oracleData.addMetadata("signatures", record.getSignatures().size());
        
        LOGGER.info(String.format("Customs data fetched: %s (confidence: %.2f%%)",
            dataKey, confidence.multiply(new BigDecimal("100"))));
        
        return oracleData;
    }
    
    /**
     * Submits new customs record
     */
    public String submitCustomsRecord(String exporterId, String destinationCountry,
                                     String commodityType, BigDecimal quantity, 
                                     BigDecimal declaredValue, String billOfLadingNumber,
                                     String containerNumber) {
        String recordId = UUID.randomUUID().toString();
        
        CustomsRecord record = new CustomsRecord(
            recordId, exporterId, destinationCountry, commodityType,
            quantity, declaredValue, billOfLadingNumber, containerNumber
        );
        
        customsRecords.put(recordId, record);
        
        LOGGER.info(String.format("Customs record submitted: %s - BOL: %s",
            recordId, billOfLadingNumber));
        
        return recordId;
    }
    
    /**
     * Adds verification signature
     */
    public boolean addVerificationSignature(String recordId, byte[] signature, 
                                          String verifierType, String note) {
        CustomsRecord record = customsRecords.get(recordId);
        
        if (record == null) {
            LOGGER.warning("Record not found for verification: " + recordId);
            return false;
        }
        
        record.addSignature(signature);
        
        CustomsVerification verification = new CustomsVerification(
            recordId, verifierType, true, note
        );
        
        verificationHistory.computeIfAbsent(recordId, k -> new ArrayList<>()).add(verification);
        
        LOGGER.info(String.format("Verification added: %s - Type: %s - Total signatures: %d",
            recordId, verifierType, record.getSignatures().size()));
        
        return true;
    }
    
    /**
     * Finds record by exporter and BOL
     */
    private CustomsRecord findRecord(String exporterId, String bolNumber) {
        return customsRecords.values().stream()
            .filter(r -> r.getExporterId().equals(exporterId) && 
                        r.getBillOfLadingNumber().equals(bolNumber))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Calculates confidence score based on verifications
     */
    private BigDecimal calculateConfidence(CustomsRecord record) {
        int signatures = record.getSignatures().size();
        
        // Base confidence from signatures
        BigDecimal baseConfidence = new BigDecimal("0.5");
        
        if (signatures >= 2) baseConfidence = new BigDecimal("0.75");
        if (signatures >= 3) baseConfidence = new BigDecimal("0.90");
        if (signatures >= 4) baseConfidence = new BigDecimal("0.95");
        
        // Boost for diverse verification types
        List<CustomsVerification> verifications = verificationHistory.get(record.getRecordId());
        if (verifications != null) {
            long uniqueTypes = verifications.stream()
                .map(CustomsVerification::getVerifierType)
                .distinct()
                .count();
            
            if (uniqueTypes >= 3) {
                baseConfidence = baseConfidence.multiply(new BigDecimal("1.05"));
            }
        }
        
        // Cap at 1.0
        return baseConfidence.min(BigDecimal.ONE);
    }
    
    /**
     * Gets customs record
     */
    public CustomsRecord getRecord(String recordId) {
        return customsRecords.get(recordId);
    }
    
    /**
     * Gets verification history
     */
    public List<CustomsVerification> getVerificationHistory(String recordId) {
        return new ArrayList<>(verificationHistory.getOrDefault(recordId, new ArrayList<>()));
    }
    
    /**
     * Gets statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRecords", customsRecords.size());
        stats.put("verifiedRecords", (int) customsRecords.values().stream()
            .filter(CustomsRecord::hasMinimumSignatures).count());
        stats.put("totalVerifications", verificationHistory.values().stream()
            .mapToInt(List::size).sum());
        return stats;
    }
    
    @Override
    protected boolean validateDataFormat(Object data) {
        return data instanceof Map;
    }
    
    @Override
    protected String getDataTypeIdentifier() {
        return "CUSTOMS";
    }
    
    /**
     * Signs data (simulated)
     */
    private byte[] signData(Object data) {
        try {
            return createDataHash(data);
        } catch (Exception e) {
            return new byte[32];
        }
    }
    
    /**
     * Creates hash of data
     */
    private byte[] createDataHash(Object data) throws Exception {
        String dataStr = data.toString();
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        return digest.digest(dataStr.getBytes());
    }
}
