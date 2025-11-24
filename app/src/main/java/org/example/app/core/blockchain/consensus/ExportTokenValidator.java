package org.example.app.core.blockchain.consensus;

import org.example.app.core.blockchain.transaction.ExportTokenTransaction;
import org.example.app.core.types.Hash;
import org.example.app.core.state.BlockchainState;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * ExportTokenValidator - Validates export tokens and trade receipts (Trade Layer)
 * 
 * Based on Implementation Guidebook Section III: Trade Settlement Mechanisms
 * 
 * Export Token Flow:
 * 1. Exporter submits Bill-of-Lading signed by port authority
 * 2. Customs node signs clearance
 * 3. Satellite analytics confirms container count
 * 4. Aggregator requires 2 primary + 1 satellite confirmation
 * 5. Merkle root + weighted aggregate posted on-chain
 * 6. Dispute window allows challenges
 * 
 * Prevents:
 * - Export fraud (fake trade tokens)
 * - Duplicate token issuance
 * - Invalid trade receipts
 * - Manipulation of export values
 */
public class ExportTokenValidator {
    private static final Logger LOGGER = Logger.getLogger(ExportTokenValidator.class.getName());
    
    // Verification requirements
    private static final int MIN_PRIMARY_CONFIRMATIONS = 2; // Port + Customs
    private static final int MIN_SATELLITE_CONFIRMATIONS = 1;
    private static final int DISPUTE_WINDOW_MS = 604800000; // 7 days
    
    // Export token limits
    private static final BigDecimal MAX_SINGLE_EXPORT_VALUE = new BigDecimal("10000000"); // 10M
    private static final BigDecimal MIN_EXPORT_VALUE = new BigDecimal("1000"); // 1K
    
    // Collateral requirements
    private static final BigDecimal COLLATERAL_PERCENTAGE = new BigDecimal("0.10"); // 10% staking
    
    // Dependencies
    private final BlockchainState blockchainState;
    
    // Tracking
    private final Map<Hash, ExportTokenRecord> exportTokens;
    private final Map<String, Set<Hash>> exporterHistory; // Exporter -> token hashes
    private final Map<Hash, DisputeRecord> disputes;
    
    /**
     * Represents an export token record
     */
    public static class ExportTokenRecord {
        private final Hash tokenHash;
        private final String exporterId;
        private final String commodityType;
        private final BigDecimal quantity;
        private final BigDecimal estimatedValue;
        private final String destination;
        private final TradeReceipt tradeReceipt;
        private final List<Confirmation> confirmations;
        private final BigDecimal collateralStaked;
        private final long creationTime;
        private boolean redeemed;
        private boolean disputed;
        
        public ExportTokenRecord(ExportTokenTransaction tx) {
            this.tokenHash = tx.getHash();
            this.exporterId = tx.getExporterId();
            this.commodityType = tx.getCommodityType();
            this.quantity = tx.getQuantity();
            this.estimatedValue = tx.getEstimatedValue();
            this.destination = tx.getDestination();
            this.tradeReceipt = new TradeReceipt(tx);
            this.confirmations = new ArrayList<>();
            this.collateralStaked = tx.getCollateralStaked();
            this.creationTime = System.currentTimeMillis();
            this.redeemed = false;
            this.disputed = false;
        }
        
        public Hash getTokenHash() { return tokenHash; }
        public String getExporterId() { return exporterId; }
        public String getCommodityType() { return commodityType; }
        public BigDecimal getQuantity() { return quantity; }
        public BigDecimal getEstimatedValue() { return estimatedValue; }
        public String getDestination() { return destination; }
        public TradeReceipt getTradeReceipt() { return tradeReceipt; }
        public List<Confirmation> getConfirmations() { return new ArrayList<>(confirmations); }
        public BigDecimal getCollateralStaked() { return collateralStaked; }
        public boolean isRedeemed() { return redeemed; }
        public boolean isDisputed() { return disputed; }
        
        public void addConfirmation(Confirmation confirmation) { confirmations.add(confirmation); }
        public void setRedeemed(boolean redeemed) { this.redeemed = redeemed; }
        public void setDisputed(boolean disputed) { this.disputed = disputed; }
    }
    
    /**
     * Represents a trade receipt (Bill of Lading)
     */
    public static class TradeReceipt {
        private final String billOfLadingNumber;
        private final byte[] portAuthoritySignature;
        private final byte[] customsSignature;
        private final String vesselName;
        private final String containerNumbers;
        private final byte[] shippingManifest;
        private final long shipmentDate;
        
        public TradeReceipt(ExportTokenTransaction tx) {
            this.billOfLadingNumber = tx.getBillOfLadingNumber();
            this.portAuthoritySignature = tx.getPortAuthoritySignature();
            this.customsSignature = tx.getCustomsSignature();
            this.vesselName = tx.getVesselName();
            this.containerNumbers = tx.getContainerNumbers();
            this.shippingManifest = tx.getShippingManifest();
            this.shipmentDate = tx.getShipmentDate();
        }
        
        public String getBillOfLadingNumber() { return billOfLadingNumber; }
        public byte[] getPortAuthoritySignature() { return portAuthoritySignature; }
        public byte[] getCustomsSignature() { return customsSignature; }
        public String getVesselName() { return vesselName; }
        public String getContainerNumbers() { return containerNumbers; }
        public byte[] getShippingManifest() { return shippingManifest; }
        public long getShipmentDate() { return shipmentDate; }
    }
    
    /**
     * Represents a confirmation from an oracle
     */
    public static class Confirmation {
        private final String sourceType; // PORT, CUSTOMS, SATELLITE, SHIPPING
        private final String sourceId;
        private final byte[] signature;
        private final Map<String, String> metadata;
        private final long confirmationTime;
        
        public Confirmation(String sourceType, String sourceId, byte[] signature) {
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.signature = signature;
            this.metadata = new HashMap<>();
            this.confirmationTime = System.currentTimeMillis();
        }
        
        public String getSourceType() { return sourceType; }
        public String getSourceId() { return sourceId; }
        public byte[] getSignature() { return signature; }
        public Map<String, String> getMetadata() { return new HashMap<>(metadata); }
        
        public void addMetadata(String key, String value) { metadata.put(key, value); }
    }
    
    /**
     * Represents a dispute record
     */
    public static class DisputeRecord {
        private final Hash tokenHash;
        private final String disputant;
        private final String disputeReason;
        private final byte[] evidence;
        private final long disputeTime;
        private boolean resolved;
        private String resolution;
        
        public DisputeRecord(Hash tokenHash, String disputant, String reason, byte[] evidence) {
            this.tokenHash = tokenHash;
            this.disputant = disputant;
            this.disputeReason = reason;
            this.evidence = evidence;
            this.disputeTime = System.currentTimeMillis();
            this.resolved = false;
        }
        
        public Hash getTokenHash() { return tokenHash; }
        public String getDisputant() { return disputant; }
        public String getDisputeReason() { return disputeReason; }
        public byte[] getEvidence() { return evidence; }
        public boolean isResolved() { return resolved; }
        public String getResolution() { return resolution; }
        
        public void resolve(String resolution) {
            this.resolved = true;
            this.resolution = resolution;
        }
    }
    
    /**
     * Validation result
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String failureReason;
        private final Map<String, Object> metadata;
        
        private ValidationResult(boolean valid, String reason, Map<String, Object> metadata) {
            this.valid = valid;
            this.failureReason = reason;
            this.metadata = metadata != null ? metadata : new HashMap<>();
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, null, new HashMap<>());
        }
        
        public static ValidationResult success(Map<String, Object> metadata) {
            return new ValidationResult(true, null, metadata);
        }
        
        public static ValidationResult failure(String reason) {
            return new ValidationResult(false, reason, new HashMap<>());
        }
        
        public boolean isValid() { return valid; }
        public String getFailureReason() { return failureReason; }
        public Map<String, Object> getMetadata() { return metadata; }
    }
    
    /**
     * Constructor
     */
    public ExportTokenValidator(BlockchainState blockchainState) {
        this.blockchainState = blockchainState;
        this.exportTokens = new ConcurrentHashMap<>();
        this.exporterHistory = new ConcurrentHashMap<>();
        this.disputes = new ConcurrentHashMap<>();
        
        LOGGER.info("ExportTokenValidator initialized");
    }
    
    /**
     * Main validation method for export tokens
     */
    public ValidationResult validate(ExportTokenTransaction transaction) {
        try {
            LOGGER.info("Validating export token: " + transaction.getHash());
            
            // 1. Validate basic structure
            ValidationResult structureCheck = validateStructure(transaction);
            if (!structureCheck.isValid()) {
                return structureCheck;
            }
            
            // 2. Validate trade receipt
            ValidationResult receiptCheck = validateTradeReceipt(transaction);
            if (!receiptCheck.isValid()) {
                return receiptCheck;
            }
            
            // 3. Validate signatures (port + customs)
            ValidationResult signatureCheck = validateSignatures(transaction);
            if (!signatureCheck.isValid()) {
                return signatureCheck;
            }
            
            // 4. Check for duplicate exports
            ValidationResult duplicateCheck = checkDuplicateExport(transaction);
            if (!duplicateCheck.isValid()) {
                return duplicateCheck;
            }
            
            // 5. Validate export value
            ValidationResult valueCheck = validateExportValue(transaction);
            if (!valueCheck.isValid()) {
                return valueCheck;
            }
            
            // 6. Validate collateral staked
            ValidationResult collateralCheck = validateCollateral(transaction);
            if (!collateralCheck.isValid()) {
                return collateralCheck;
            }
            
            // 7. Validate exporter reputation
            ValidationResult reputationCheck = validateExporterReputation(transaction);
            if (!reputationCheck.isValid()) {
                return reputationCheck;
            }
            
            // Create export token record
            ExportTokenRecord record = new ExportTokenRecord(transaction);
            exportTokens.put(transaction.getHash(), record);
            
            // Track exporter history
            Set<Hash> history = exporterHistory.computeIfAbsent(
                transaction.getExporterId(),
                k -> ConcurrentHashMap.newKeySet()
            );
            history.add(transaction.getHash());
            
            LOGGER.info("Export token validated successfully: " + transaction.getHash());
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("tokenHash", transaction.getHash().toString());
            metadata.put("exporterId", transaction.getExporterId());
            metadata.put("commodityType", transaction.getCommodityType());
            metadata.put("value", transaction.getEstimatedValue());
            metadata.put("destination", transaction.getDestination());
            
            return ValidationResult.success(metadata);
            
        } catch (Exception e) {
            LOGGER.severe("Export token validation error: " + e.getMessage());
            return ValidationResult.failure("Validation exception: " + e.getMessage());
        }
    }
    
    /**
     * Validates basic transaction structure
     */
    private ValidationResult validateStructure(ExportTokenTransaction transaction) {
        if (transaction == null) {
            return ValidationResult.failure("Transaction is null");
        }
        
        if (transaction.getHash() == null) {
            return ValidationResult.failure("Transaction hash is missing");
        }
        
        if (transaction.getExporterId() == null || transaction.getExporterId().isEmpty()) {
            return ValidationResult.failure("Exporter ID is required");
        }
        
        if (transaction.getCommodityType() == null || transaction.getCommodityType().isEmpty()) {
            return ValidationResult.failure("Commodity type is required");
        }
        
        if (transaction.getQuantity() == null || transaction.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.failure("Invalid quantity");
        }
        
        if (transaction.getEstimatedValue() == null || transaction.getEstimatedValue().compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.failure("Invalid estimated value");
        }
        
        if (transaction.getDestination() == null || transaction.getDestination().isEmpty()) {
            return ValidationResult.failure("Destination is required");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates trade receipt (Bill of Lading)
     */
    private ValidationResult validateTradeReceipt(ExportTokenTransaction transaction) {
        if (transaction.getBillOfLadingNumber() == null || transaction.getBillOfLadingNumber().isEmpty()) {
            return ValidationResult.failure("Bill of Lading number is required");
        }
        
        // Check BoL format (simplified)
        String bol = transaction.getBillOfLadingNumber();
        if (bol.length() < 10 || bol.length() > 30) {
            return ValidationResult.failure("Invalid Bill of Lading format");
        }
        
        if (transaction.getVesselName() == null || transaction.getVesselName().isEmpty()) {
            return ValidationResult.failure("Vessel name is required");
        }
        
        if (transaction.getContainerNumbers() == null || transaction.getContainerNumbers().isEmpty()) {
            return ValidationResult.failure("Container numbers are required");
        }
        
        if (transaction.getShippingManifest() == null || transaction.getShippingManifest().length == 0) {
            return ValidationResult.failure("Shipping manifest is required");
        }
        
        if (transaction.getShipmentDate() <= 0) {
            return ValidationResult.failure("Invalid shipment date");
        }
        
        // Shipment date should be recent (within last 30 days)
        long now = System.currentTimeMillis();
        long daysSinceShipment = (now - transaction.getShipmentDate()) / 86400000;
        
        if (daysSinceShipment > 30) {
            return ValidationResult.failure("Shipment date too old: " + daysSinceShipment + " days");
        }
        
        if (daysSinceShipment < 0) {
            return ValidationResult.failure("Shipment date cannot be in the future");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates port authority and customs signatures
     */
    private ValidationResult validateSignatures(ExportTokenTransaction transaction) {
        byte[] portSignature = transaction.getPortAuthoritySignature();
        if (portSignature == null || portSignature.length == 0) {
            return ValidationResult.failure("Port authority signature is required");
        }
        
        if (portSignature.length < 64 || portSignature.length > 128) {
            return ValidationResult.failure("Invalid port authority signature format");
        }
        
        byte[] customsSignature = transaction.getCustomsSignature();
        if (customsSignature == null || customsSignature.length == 0) {
            return ValidationResult.failure("Customs signature is required");
        }
        
        if (customsSignature.length < 64 || customsSignature.length > 128) {
            return ValidationResult.failure("Invalid customs signature format");
        }
        
        // In production, verify these signatures cryptographically
        // against registered port authority and customs public keys
        
        return ValidationResult.success();
    }
    
    /**
     * Checks for duplicate export (same BoL)
     */
    private ValidationResult checkDuplicateExport(ExportTokenTransaction transaction) {
        String bol = transaction.getBillOfLadingNumber();
        
        // Check if this BoL has been used before
        boolean duplicate = exportTokens.values().stream()
            .anyMatch(record -> 
                record.getTradeReceipt().getBillOfLadingNumber().equals(bol) &&
                !record.isDisputed()
            );
        
        if (duplicate) {
            return ValidationResult.failure("Duplicate Bill of Lading: " + bol);
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates export value is reasonable
     */
    private ValidationResult validateExportValue(ExportTokenTransaction transaction) {
        BigDecimal value = transaction.getEstimatedValue();
        
        if (value.compareTo(MIN_EXPORT_VALUE) < 0) {
            return ValidationResult.failure("Export value below minimum: " + MIN_EXPORT_VALUE);
        }
        
        if (value.compareTo(MAX_SINGLE_EXPORT_VALUE) > 0) {
            return ValidationResult.failure("Export value exceeds maximum: " + MAX_SINGLE_EXPORT_VALUE);
        }
        
        // Check value against typical commodity prices
        // In production, cross-reference with oracle price feeds
        
        return ValidationResult.success();
    }
    
    /**
     * Validates collateral staked
     */
    private ValidationResult validateCollateral(ExportTokenTransaction transaction) {
        BigDecimal requiredCollateral = transaction.getEstimatedValue()
            .multiply(COLLATERAL_PERCENTAGE);
        
        BigDecimal stakedCollateral = transaction.getCollateralStaked();
        
        if (stakedCollateral == null || stakedCollateral.compareTo(requiredCollateral) < 0) {
            return ValidationResult.failure(
                String.format("Insufficient collateral staked. Required: %s, Staked: %s",
                    requiredCollateral, stakedCollateral)
            );
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validates exporter reputation
     */
    private ValidationResult validateExporterReputation(ExportTokenTransaction transaction) {
        String exporterId = transaction.getExporterId();
        Set<Hash> history = exporterHistory.get(exporterId);
        
        if (history == null || history.isEmpty()) {
            // New exporter - requires extra scrutiny
            LOGGER.info("New exporter detected: " + exporterId);
            return ValidationResult.success();
        }
        
        // Check for disputed exports in history
        long disputedCount = history.stream()
            .map(exportTokens::get)
            .filter(Objects::nonNull)
            .filter(ExportTokenRecord::isDisputed)
            .count();
        
        double disputeRate = (double) disputedCount / history.size();
        
        if (disputeRate > 0.2) { // 20% dispute rate threshold
            return ValidationResult.failure(
                String.format("Exporter has high dispute rate: %.1f%% (max: 20%%)",
                    disputeRate * 100)
            );
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Adds an oracle confirmation
     */
    public void addConfirmation(Hash tokenHash, String sourceType, String sourceId, byte[] signature) {
        ExportTokenRecord record = exportTokens.get(tokenHash);
        
        if (record == null) {
            LOGGER.warning("Cannot add confirmation: token not found");
            return;
        }
        
        Confirmation confirmation = new Confirmation(sourceType, sourceId, signature);
        record.addConfirmation(confirmation);
        
        LOGGER.info(String.format("Confirmation added: %s from %s", tokenHash, sourceType));
    }
    
    /**
     * Checks if export token has sufficient confirmations
     */
    public boolean hasSufficientConfirmations(Hash tokenHash) {
        ExportTokenRecord record = exportTokens.get(tokenHash);
        
        if (record == null) {
            return false;
        }
        
        long primaryCount = record.getConfirmations().stream()
            .filter(c -> c.getSourceType().equals("PORT") || c.getSourceType().equals("CUSTOMS"))
            .count();
        
        long satelliteCount = record.getConfirmations().stream()
            .filter(c -> c.getSourceType().equals("SATELLITE"))
            .count();
        
        return primaryCount >= MIN_PRIMARY_CONFIRMATIONS && satelliteCount >= MIN_SATELLITE_CONFIRMATIONS;
    }
    
    /**
     * Files a dispute against an export token
     */
    public void fileDispute(Hash tokenHash, String disputant, String reason, byte[] evidence) {
        ExportTokenRecord record = exportTokens.get(tokenHash);
        
        if (record == null) {
            LOGGER.warning("Cannot file dispute: token not found");
            return;
        }
        
        // Check if still within dispute window
        long tokenAge = System.currentTimeMillis() - record.creationTime;
        if (tokenAge > DISPUTE_WINDOW_MS) {
            LOGGER.warning("Dispute window expired for token: " + tokenHash);
            return;
        }
        
        DisputeRecord dispute = new DisputeRecord(tokenHash, disputant, reason, evidence);
        disputes.put(tokenHash, dispute);
        record.setDisputed(true);
        
        LOGGER.warning(String.format("Dispute filed against token %s: %s", tokenHash, reason));
    }
    
    /**
     * Redeems an export token
     */
    public ValidationResult redeemToken(Hash tokenHash) {
        ExportTokenRecord record = exportTokens.get(tokenHash);
        
        if (record == null) {
            return ValidationResult.failure("Token not found");
        }
        
        if (record.isRedeemed()) {
            return ValidationResult.failure("Token already redeemed");
        }
        
        if (record.isDisputed()) {
            DisputeRecord dispute = disputes.get(tokenHash);
            if (dispute != null && !dispute.isResolved()) {
                return ValidationResult.failure("Token is disputed and unresolved");
            }
        }
        
        if (!hasSufficientConfirmations(tokenHash)) {
            return ValidationResult.failure("Insufficient confirmations");
        }
        
        record.setRedeemed(true);
        
        LOGGER.info("Export token redeemed: " + tokenHash);
        return ValidationResult.success();
    }
    
    /**
     * Gets export statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalExportTokens", exportTokens.size());
        stats.put("redeemedTokens", exportTokens.values().stream()
            .filter(ExportTokenRecord::isRedeemed).count());
        stats.put("disputedTokens", exportTokens.values().stream()
            .filter(ExportTokenRecord::isDisputed).count());
        stats.put("totalExporters", exporterHistory.size());
        stats.put("activeDisputes", disputes.values().stream()
            .filter(d -> !d.isResolved()).count());
        
        return stats;
    }
    
    /**
     * Gets export token record
     */
    public ExportTokenRecord getExportToken(Hash tokenHash) {
        return exportTokens.get(tokenHash);
    }
}