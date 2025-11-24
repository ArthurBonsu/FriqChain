package org.example.app.core.blockchain.block;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.Logger;

/**
 * SettlementRecord tracks inter-bank and cross-border settlements
 * in the African Tokenized Currency System.
 * 
 * Key features:
 * - Tracks bank-to-bank settlements
 * - ASU (African Stable Unit) settlements for inter-country trade
 * - Proof of settlement with cryptographic verification
 * - Compliance and audit trail
 */
public class SettlementRecord {
    private static final Logger logger = Logger.getLogger(SettlementRecord.class.getName());
    
    // Settlement identification
    private final String settlementId;
    private final String sourceBank;
    private final String destinationBank;
    
    // Settlement details
    private final BigInteger amount;
    private final String currency; // Token symbol (e.g., "NGN-Token", "ASU")
    private final long timestamp;
    private final SettlementStatus status;
    
    // Settlement type
    private final SettlementType type;
    
    // Verification
    private final byte[] settlementProof; // Merkle proof or cryptographic signature
    private final String blockHash; // Block containing this settlement
    private final long blockNumber;
    
    // Cross-border metadata
    private final String sourceCountry;
    private final String destinationCountry;
    private final boolean isCrossBorder;
    
    // Reconciliation
    private final String externalReferenceId; // Bank's internal reference
    private final Map<String, String> metadata; // Additional settlement data
    
    /**
     * Settlement status enum
     */
    public enum SettlementStatus {
        PENDING,        // Settlement initiated
        IN_PROGRESS,    // Being processed
        COMPLETED,      // Successfully completed
        FAILED,         // Settlement failed
        REVERSED,       // Settlement reversed/refunded
        DISPUTED        // Under dispute resolution
    }
    
    /**
     * Settlement type enum
     */
    public enum SettlementType {
        DOMESTIC,           // Same-country settlement
        CROSS_BORDER,       // Different countries
        ASU_SETTLEMENT,     // Using ASU as settlement token
        EXPORT_TOKEN,       // Export token redemption
        LIQUIDITY_POOL,     // Liquidity pool settlement
        P2P_TRANSFER,       // Peer-to-peer transfer
        EMERGENCY_EXIT      // Emergency withdrawal settlement
    }
    
    /**
     * Constructor for settlement record
     */
    public SettlementRecord(String settlementId, String sourceBank, String destinationBank,
                          BigInteger amount, String currency, long timestamp,
                          SettlementStatus status) {
        this(settlementId, sourceBank, destinationBank, amount, currency, timestamp,
             status, SettlementType.DOMESTIC, new byte[32], "", 0,
             "", "", false, "", new HashMap<>());
    }
    
    /**
     * Full constructor for settlement record
     */
    public SettlementRecord(String settlementId, String sourceBank, String destinationBank,
                          BigInteger amount, String currency, long timestamp,
                          SettlementStatus status, SettlementType type,
                          byte[] settlementProof, String blockHash, long blockNumber,
                          String sourceCountry, String destinationCountry,
                          boolean isCrossBorder, String externalReferenceId,
                          Map<String, String> metadata) {
        
        if (settlementId == null || settlementId.isEmpty()) {
            throw new IllegalArgumentException("Settlement ID cannot be null or empty");
        }
        if (sourceBank == null || destinationBank == null) {
            throw new IllegalArgumentException("Bank identifiers cannot be null");
        }
        if (amount == null || amount.compareTo(BigInteger.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (currency == null || currency.isEmpty()) {
            throw new IllegalArgumentException("Currency cannot be null or empty");
        }
        
        this.settlementId = settlementId;
        this.sourceBank = sourceBank;
        this.destinationBank = destinationBank;
        this.amount = amount;
        this.currency = currency;
        this.timestamp = timestamp;
        this.status = status;
        this.type = type;
        this.settlementProof = Arrays.copyOf(settlementProof, settlementProof.length);
        this.blockHash = blockHash;
        this.blockNumber = blockNumber;
        this.sourceCountry = sourceCountry;
        this.destinationCountry = destinationCountry;
        this.isCrossBorder = isCrossBorder;
        this.externalReferenceId = externalReferenceId;
        this.metadata = new HashMap<>(metadata);
    }
    
    /**
     * Creates a domestic settlement record
     */
    public static SettlementRecord createDomesticSettlement(
            String sourceBank, String destinationBank,
            BigInteger amount, String currency,
            String country, String externalRef) {
        
        String settlementId = generateSettlementId(sourceBank, destinationBank);
        
        return new SettlementRecord(
            settlementId, sourceBank, destinationBank,
            amount, currency, System.currentTimeMillis(),
            SettlementStatus.PENDING, SettlementType.DOMESTIC,
            new byte[32], "", 0,
            country, country, false, externalRef,
            new HashMap<>()
        );
    }
    
    /**
     * Creates a cross-border settlement record
     */
    public static SettlementRecord createCrossBorderSettlement(
            String sourceBank, String destinationBank,
            BigInteger amount, String currency,
            String sourceCountry, String destinationCountry,
            String externalRef) {
        
        String settlementId = generateSettlementId(sourceBank, destinationBank);
        
        return new SettlementRecord(
            settlementId, sourceBank, destinationBank,
            amount, currency, System.currentTimeMillis(),
            SettlementStatus.PENDING, SettlementType.CROSS_BORDER,
            new byte[32], "", 0,
            sourceCountry, destinationCountry, true, externalRef,
            new HashMap<>()
        );
    }
    
    /**
     * Creates an ASU settlement record (pan-African)
     */
    public static SettlementRecord createASUSettlement(
            String sourceBank, String destinationBank,
            BigInteger asuAmount,
            String sourceCountry, String destinationCountry,
            Map<String, String> tradeMetadata) {
        
        String settlementId = generateSettlementId(sourceBank, destinationBank);
        
        return new SettlementRecord(
            settlementId, sourceBank, destinationBank,
            asuAmount, "ASU", System.currentTimeMillis(),
            SettlementStatus.PENDING, SettlementType.ASU_SETTLEMENT,
            new byte[32], "", 0,
            sourceCountry, destinationCountry, true, "",
            tradeMetadata
        );
    }
    
    /**
     * Generates a unique settlement ID
     */
    private static String generateSettlementId(String sourceBank, String destinationBank) {
        return String.format("SETTLE-%s-%s-%d",
            sourceBank.substring(0, Math.min(8, sourceBank.length())),
            destinationBank.substring(0, Math.min(8, destinationBank.length())),
            System.currentTimeMillis()
        );
    }
    
    // Getters
    
    public String getSettlementId() {
        return settlementId;
    }
    
    public String getSourceBank() {
        return sourceBank;
    }
    
    public String getDestinationBank() {
        return destinationBank;
    }
    
    public BigInteger getAmount() {
        return amount;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public SettlementStatus getStatus() {
        return status;
    }
    
    public SettlementType getType() {
        return type;
    }
    
    public byte[] getSettlementProof() {
        return Arrays.copyOf(settlementProof, settlementProof.length);
    }
    
    public String getBlockHash() {
        return blockHash;
    }
    
    public long getBlockNumber() {
        return blockNumber;
    }
    
    public String getSourceCountry() {
        return sourceCountry;
    }
    
    public String getDestinationCountry() {
        return destinationCountry;
    }
    
    public boolean isCrossBorder() {
        return isCrossBorder;
    }
    
    public String getExternalReferenceId() {
        return externalReferenceId;
    }
    
    public Map<String, String> getMetadata() {
        return new HashMap<>(metadata);
    }
    
    /**
     * Checks if this settlement involves a specific bank
     */
    public boolean involvesBank(String bankId) {
        return sourceBank.equals(bankId) || destinationBank.equals(bankId);
    }
    
    /**
     * Checks if settlement is finalized
     */
    public boolean isFinalized() {
        return status == SettlementStatus.COMPLETED || 
               status == SettlementStatus.FAILED ||
               status == SettlementStatus.REVERSED;
    }
    
    /**
     * Checks if settlement is using ASU
     */
    public boolean isASUSettlement() {
        return currency.equals("ASU") || type == SettlementType.ASU_SETTLEMENT;
    }
    
    /**
     * Creates a settlement with updated status
     */
    public SettlementRecord withStatus(SettlementStatus newStatus) {
        return new SettlementRecord(
            settlementId, sourceBank, destinationBank,
            amount, currency, timestamp,
            newStatus, type, settlementProof, blockHash, blockNumber,
            sourceCountry, destinationCountry, isCrossBorder,
            externalReferenceId, metadata
        );
    }
    
    /**
     * Creates a settlement with block information
     */
    public SettlementRecord withBlockInfo(String blockHash, long blockNumber, byte[] proof) {
        return new SettlementRecord(
            settlementId, sourceBank, destinationBank,
            amount, currency, timestamp,
            status, type, proof, blockHash, blockNumber,
            sourceCountry, destinationCountry, isCrossBorder,
            externalReferenceId, metadata
        );
    }
    
    /**
     * Validates the settlement record
     */
    public boolean validate() {
        // Basic validation
        if (settlementId == null || settlementId.isEmpty()) {
            logger.warning("Invalid settlement ID");
            return false;
        }
        
        if (amount == null || amount.compareTo(BigInteger.ZERO) <= 0) {
            logger.warning("Invalid amount: " + amount);
            return false;
        }
        
        if (timestamp <= 0 || timestamp > System.currentTimeMillis() + 60000) {
            logger.warning("Invalid timestamp: " + timestamp);
            return false;
        }
        
        // Cross-border validation
        if (isCrossBorder) {
            if (sourceCountry == null || destinationCountry == null ||
                sourceCountry.isEmpty() || destinationCountry.isEmpty()) {
                logger.warning("Cross-border settlement missing country info");
                return false;
            }
        }
        
        // ASU settlement validation
        if (isASUSettlement()) {
            if (!isCrossBorder) {
                logger.warning("ASU settlement should be cross-border");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Serializes settlement to bytes
     */
    public byte[] serialize() {
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        
        // Write settlement ID
        byte[] idBytes = settlementId.getBytes();
        buffer.putInt(idBytes.length);
        buffer.put(idBytes);
        
        // Write bank IDs
        byte[] sourceBytes = sourceBank.getBytes();
        buffer.putInt(sourceBytes.length);
        buffer.put(sourceBytes);
        
        byte[] destBytes = destinationBank.getBytes();
        buffer.putInt(destBytes.length);
        buffer.put(destBytes);
        
        // Write amount
        byte[] amountBytes = amount.toByteArray();
        buffer.putInt(amountBytes.length);
        buffer.put(amountBytes);
        
        // Write currency
        byte[] currencyBytes = currency.getBytes();
        buffer.putInt(currencyBytes.length);
        buffer.put(currencyBytes);
        
        // Write timestamp and status
        buffer.putLong(timestamp);
        buffer.put((byte) status.ordinal());
        buffer.put((byte) type.ordinal());
        
        // Write block info
        buffer.putLong(blockNumber);
        
        // Write countries
        byte[] sourceCountryBytes = sourceCountry.getBytes();
        buffer.putInt(sourceCountryBytes.length);
        buffer.put(sourceCountryBytes);
        
        byte[] destCountryBytes = destinationCountry.getBytes();
        buffer.putInt(destCountryBytes.length);
        buffer.put(destCountryBytes);
        
        buffer.put((byte) (isCrossBorder ? 1 : 0));
        
        buffer.flip();
        byte[] result = new byte[buffer.limit()];
        buffer.get(result);
        return result;
    }
    
    /**
     * Calculates settlement fee based on type and amount
     */
    public BigInteger calculateFee() {
        BigInteger baseFee;
        
        switch (type) {
            case DOMESTIC:
                // 0.1% fee for domestic
                baseFee = amount.multiply(BigInteger.valueOf(1))
                               .divide(BigInteger.valueOf(1000));
                break;
                
            case CROSS_BORDER:
            case ASU_SETTLEMENT:
                // 0.2% fee for cross-border
                baseFee = amount.multiply(BigInteger.valueOf(2))
                               .divide(BigInteger.valueOf(1000));
                break;
                
            case EXPORT_TOKEN:
                // 0.3% fee for export token settlements
                baseFee = amount.multiply(BigInteger.valueOf(3))
                               .divide(BigInteger.valueOf(1000));
                break;
                
            case P2P_TRANSFER:
                // Fixed small fee for P2P
                baseFee = BigInteger.valueOf(100);
                break;
                
            case EMERGENCY_EXIT:
                // 1% fee for emergency exits (to prevent abuse)
                baseFee = amount.multiply(BigInteger.valueOf(10))
                               .divide(BigInteger.valueOf(1000));
                break;
                
            default:
                baseFee = BigInteger.ZERO;
        }
        
        // Minimum fee
        BigInteger minFee = BigInteger.valueOf(10);
        return baseFee.max(minFee);
    }
    
    /**
     * Estimates settlement time in seconds
     */
    public long estimateSettlementTime() {
        switch (type) {
            case DOMESTIC:
                return 3; // 3 seconds (1 block)
                
            case CROSS_BORDER:
                return 15; // 15 seconds (5 blocks)
                
            case ASU_SETTLEMENT:
                return 10; // 10 seconds (ASU optimized)
                
            case EXPORT_TOKEN:
                return 30; // 30 seconds (verification needed)
                
            case P2P_TRANSFER:
                return 3; // Instant
                
            case EMERGENCY_EXIT:
                return 60; // 1 minute (additional verification)
                
            default:
                return 10;
        }
    }
    
    @Override
    public String toString() {
        return String.format("SettlementRecord{id=%s, %s->%s, %s %s, status=%s, type=%s, crossBorder=%b}",
            settlementId, sourceBank, destinationBank, amount, currency,
            status, type, isCrossBorder);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SettlementRecord that = (SettlementRecord) o;
        return settlementId.equals(that.settlementId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(settlementId);
    }
}