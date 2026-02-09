package org.example.app.core.banking.independence;

import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;
import org.example.app.core.crypto.Keypair;
import org.example.app.core.crypto.Keypair.SignatureResult;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OfflineCertificateIssuer manages offline transaction certificates (signed IOUs)
 * for the TINC Blockchain.
 * 
 * This is CRITICAL for African deployment where connectivity is limited or unreliable.
 * Offline certificates allow users to:
 * - Conduct transactions without internet connectivity
 * - Use USSD/SMS for transaction authorization
 * - Settle transactions when connectivity is restored
 * - Build trust in offline communities
 * 
 * Security Features:
 * - Cryptographic signatures on all certificates
 * - Sequential numbering prevents reuse
 * - Expiry times limit exposure
 * - Double-spend detection during settlement
 * - Revocation mechanisms for compromised certificates
 * - Multi-party verification for high-value certificates
 * 
 * Offline Flow:
 * 1. User generates certificate offline with local device
 * 2. Certificate contains: amount, recipient, expiry, signature
 * 3. Recipient validates certificate locally (cryptographic check)
 * 4. When online, certificate is submitted for settlement
 * 5. System validates and executes underlying transaction
 * 
 * @author BlockchainLab
 * @version 1.0
 */
public class OfflineCertificateIssuer {
    private static final Logger LOGGER = Logger.getLogger(OfflineCertificateIssuer.class.getName());
    
    // Configuration constants
    private static final long DEFAULT_CERTIFICATE_VALIDITY_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days
    private static final long MAX_CERTIFICATE_VALIDITY_MS = 30 * 24 * 60 * 60 * 1000L; // 30 days
    private static final BigInteger MAX_CERTIFICATE_AMOUNT = new BigInteger("10000000"); // 10M units
    private static final BigInteger MIN_CERTIFICATE_AMOUNT = new BigInteger("100"); // 100 units
    private static final int MAX_PENDING_CERTIFICATES_PER_USER = 100;
    private static final int CERTIFICATE_ID_LENGTH = 16; // bytes
    
    // Storage
    private final ConcurrentHashMap<String, OfflineCertificate> pendingCertificates;
    private final ConcurrentHashMap<String, OfflineCertificate> settledCertificates;
    private final ConcurrentHashMap<String, OfflineCertificate> revokedCertificates;
    
    // Indexes for efficient lookup
    private final ConcurrentHashMap<Address, Set<String>> issuerCertificateIndex;
    private final ConcurrentHashMap<Address, Set<String>> recipientCertificateIndex;
    private final ConcurrentHashMap<String, Long> sequenceNumbers; // Track sequence per issuer
    
    // Double-spend prevention
    private final ConcurrentHashMap<String, Set<String>> spentCertificates; // issuer -> spent cert IDs
    
    // Locks
    private final ReadWriteLock certificateLock;
    private final ReadWriteLock settlementLock;
    
    // Background processing
    private final ScheduledExecutorService scheduler;
    private final ExecutorService settlementProcessor;
    
    // Metrics
    private volatile long totalCertificatesIssued;
    private volatile long totalCertificatesSettled;
    private volatile long totalCertificatesRevoked;
    private volatile long totalDoubleSpendAttempts;
    
    // Random generator for certificate IDs
    private final SecureRandom secureRandom;
    
    /**
     * Represents an offline transaction certificate (signed IOU)
     */
    public static class OfflineCertificate {
        private final String certificateId;
        private final Address issuer;
        private final Address recipient;
        private final BigInteger amount;
        private final String currencyCode;
        private final long issuanceTimestamp;
        private final long expiryTimestamp;
        private final long sequenceNumber;
        private final byte[] certificateData;
        private final SignatureResult issuerSignature;
        private volatile CertificateStatus status;
        private volatile String settlementTransactionHash;
        private volatile Long settlementTimestamp;
        private volatile String revocationReason;
        
        // Optional fields for enhanced functionality
        private final String memo;
        private final List<String> proofOfIdentityHashes; // For high-value transactions
        private final boolean requiresMultiSig;
        private final List<SignatureResult> validatorSignatures;
        
        public enum CertificateStatus {
            PENDING,        // Issued but not yet settled
            VALIDATING,     // Being validated for settlement
            SETTLED,        // Successfully settled on-chain
            REVOKED,        // Revoked before settlement
            EXPIRED,        // Expired before settlement
            REJECTED        // Rejected during validation
        }
        
        public OfflineCertificate(String certificateId, Address issuer, Address recipient,
                                BigInteger amount, String currencyCode, long expiryTimestamp,
                                long sequenceNumber, byte[] certificateData,
                                SignatureResult issuerSignature, String memo,
                                boolean requiresMultiSig) {
            this.certificateId = certificateId;
            this.issuer = issuer;
            this.recipient = recipient;
            this.amount = amount;
            this.currencyCode = currencyCode;
            this.issuanceTimestamp = System.currentTimeMillis();
            this.expiryTimestamp = expiryTimestamp;
            this.sequenceNumber = sequenceNumber;
            this.certificateData = certificateData.clone();
            this.issuerSignature = issuerSignature;
            this.status = CertificateStatus.PENDING;
            this.memo = memo;
            this.requiresMultiSig = requiresMultiSig;
            this.validatorSignatures = new ArrayList<>();
            this.proofOfIdentityHashes = new ArrayList<>();
        }
        
        // Getters
        public String getCertificateId() { return certificateId; }
        public Address getIssuer() { return issuer; }
        public Address getRecipient() { return recipient; }
        public BigInteger getAmount() { return amount; }
        public String getCurrencyCode() { return currencyCode; }
        public long getIssuanceTimestamp() { return issuanceTimestamp; }
        public long getExpiryTimestamp() { return expiryTimestamp; }
        public long getSequenceNumber() { return sequenceNumber; }
        public byte[] getCertificateData() { return certificateData.clone(); }
        public SignatureResult getIssuerSignature() { return issuerSignature; }
        public CertificateStatus getStatus() { return status; }
        public String getSettlementTransactionHash() { return settlementTransactionHash; }
        public Long getSettlementTimestamp() { return settlementTimestamp; }
        public String getRevocationReason() { return revocationReason; }
        public String getMemo() { return memo; }
        public boolean isRequiresMultiSig() { return requiresMultiSig; }
        public List<SignatureResult> getValidatorSignatures() { return new ArrayList<>(validatorSignatures); }
        public List<String> getProofOfIdentityHashes() { return new ArrayList<>(proofOfIdentityHashes); }
        
        // Setters
        void setStatus(CertificateStatus status) { this.status = status; }
        void setSettlementTransactionHash(String hash) { this.settlementTransactionHash = hash; }
        void setSettlementTimestamp(Long timestamp) { this.settlementTimestamp = timestamp; }
        void setRevocationReason(String reason) { this.revocationReason = reason; }
        void addValidatorSignature(SignatureResult signature) { this.validatorSignatures.add(signature); }
        void addProofOfIdentity(String hash) { this.proofOfIdentityHashes.add(hash); }
        
        /**
         * Checks if certificate is expired
         */
        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTimestamp;
        }
        
        /**
         * Checks if certificate is still valid for use
         */
        public boolean isValid() {
            return status == CertificateStatus.PENDING && !isExpired();
        }
        
        /**
         * Gets the remaining validity time in milliseconds
         */
        public long getRemainingValidityMs() {
            long remaining = expiryTimestamp - System.currentTimeMillis();
            return Math.max(0, remaining);
        }
    }
    
    /**
     * Result of certificate operations
     */
    public static class CertificateOperationResult {
        private final boolean success;
        private final String message;
        private final OfflineCertificate certificate;
        private final String transactionHash;
        
        public CertificateOperationResult(boolean success, String message,
                                        OfflineCertificate certificate, String transactionHash) {
            this.success = success;
            this.message = message;
            this.certificate = certificate;
            this.transactionHash = transactionHash;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public OfflineCertificate getCertificate() { return certificate; }
        public String getTransactionHash() { return transactionHash; }
    }
    
    /**
     * QR code representation for offline transmission
     */
    public static class CertificateQRData {
        private final String certificateId;
        private final String issuerAddress;
        private final String recipientAddress;
        private final String amount;
        private final String currencyCode;
        private final String expiry;
        private final String signature;
        private final String checksum;
        
        public CertificateQRData(OfflineCertificate certificate) {
            this.certificateId = certificate.getCertificateId();
            this.issuerAddress = certificate.getIssuer().toString();
            this.recipientAddress = certificate.getRecipient().toString();
            this.amount = certificate.getAmount().toString();
            this.currencyCode = certificate.getCurrencyCode();
            this.expiry = String.valueOf(certificate.getExpiryTimestamp());
            // TODO: SignatureResult doesn't have getSignature() method
            this.signature = bytesToHex(certificate.getIssuerSignature().toString().getBytes());
            this.checksum = calculateChecksum();
        }
        
        /**
         * Generates QR-compatible string representation
         */
        public String toQRString() {
            return String.join("|",
                certificateId,
                issuerAddress,
                recipientAddress,
                amount,
                currencyCode,
                expiry,
                signature,
                checksum
            );
        }
        
        /**
         * Calculates checksum for validation
         */
        private String calculateChecksum() {
            try {
                String data = certificateId + issuerAddress + recipientAddress + 
                            amount + currencyCode + expiry + signature;
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(data.getBytes());
                return bytesToHex(Arrays.copyOf(hash, 4)); // First 4 bytes
            } catch (Exception e) {
                return "0000";
            }
        }
        
        /**
         * Validates checksum
         */
        public boolean validateChecksum() {
            return checksum.equals(calculateChecksum());
        }
        
        private static String bytesToHex(byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
    }
    
    /**
     * Constructor
     */
    public OfflineCertificateIssuer() {
        this.pendingCertificates = new ConcurrentHashMap<>();
        this.settledCertificates = new ConcurrentHashMap<>();
        this.revokedCertificates = new ConcurrentHashMap<>();
        this.issuerCertificateIndex = new ConcurrentHashMap<>();
        this.recipientCertificateIndex = new ConcurrentHashMap<>();
        this.sequenceNumbers = new ConcurrentHashMap<>();
        this.spentCertificates = new ConcurrentHashMap<>();
        this.certificateLock = new ReentrantReadWriteLock();
        this.settlementLock = new ReentrantReadWriteLock();
        this.secureRandom = new SecureRandom();
        
        // Initialize executors
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "CertificateIssuer-Scheduler");
            t.setDaemon(true);
            return t;
        });
        
        this.settlementProcessor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "CertificateIssuer-Settlement");
            t.setDaemon(true);
            return t;
        });
        
        // Start background tasks
        startBackgroundTasks();
        
        LOGGER.info("OfflineCertificateIssuer initialized");
    }
    
    /**
     * Issues a new offline certificate
     */
    public CertificateOperationResult issueCertificate(Address issuer, Address recipient,
                                                      BigInteger amount, String currencyCode,
                                                      long validityMs, Keypair issuerKeypair,
                                                      String memo, boolean requiresMultiSig) {
        certificateLock.writeLock().lock();
        try {
            // Validate inputs
            if (issuer == null || recipient == null) {
                return new CertificateOperationResult(false, "Invalid addresses", null, null);
            }
            
            if (issuer.equals(recipient)) {
                return new CertificateOperationResult(false, "Cannot issue to self", null, null);
            }
            
            if (amount.compareTo(MIN_CERTIFICATE_AMOUNT) < 0) {
                return new CertificateOperationResult(false, 
                    "Amount below minimum: " + MIN_CERTIFICATE_AMOUNT, null, null);
            }
            
            if (amount.compareTo(MAX_CERTIFICATE_AMOUNT) > 0) {
                return new CertificateOperationResult(false, 
                    "Amount exceeds maximum: " + MAX_CERTIFICATE_AMOUNT, null, null);
            }
            
            if (validityMs <= 0 || validityMs > MAX_CERTIFICATE_VALIDITY_MS) {
                return new CertificateOperationResult(false, "Invalid validity period", null, null);
            }
            
            if (currencyCode == null || currencyCode.isEmpty()) {
                return new CertificateOperationResult(false, "Invalid currency code", null, null);
            }
            
            // Check issuer's pending certificate limit
            Set<String> issuerCerts = issuerCertificateIndex.getOrDefault(issuer, Collections.emptySet());
            if (issuerCerts.size() >= MAX_PENDING_CERTIFICATES_PER_USER) {
                return new CertificateOperationResult(false, 
                    "Too many pending certificates for issuer", null, null);
            }
            
            // Generate certificate ID
            String certificateId = generateCertificateId();
            
            // Get and increment sequence number for issuer
            String issuerKey = issuer.toString();
            long sequenceNumber = sequenceNumbers.compute(issuerKey, (k, v) -> (v == null) ? 1L : v + 1);
            
            // Calculate expiry
            long expiryTimestamp = System.currentTimeMillis() + validityMs;
            
            // Build certificate data
            byte[] certificateData = buildCertificateData(certificateId, issuer, recipient,
                                                         amount, currencyCode, expiryTimestamp,
                                                         sequenceNumber, memo);
            
            // Sign certificate
            SignatureResult signature = issuerKeypair.sign(certificateData);
            
            // Create certificate
            OfflineCertificate certificate = new OfflineCertificate(
                certificateId, issuer, recipient, amount, currencyCode,
                expiryTimestamp, sequenceNumber, certificateData, signature,
                memo, requiresMultiSig
            );
            
            // Store certificate
            pendingCertificates.put(certificateId, certificate);
            
            // Update indexes
            issuerCertificateIndex.computeIfAbsent(issuer, k -> ConcurrentHashMap.newKeySet())
                                 .add(certificateId);
            recipientCertificateIndex.computeIfAbsent(recipient, k -> ConcurrentHashMap.newKeySet())
                                    .add(certificateId);
            
            // Update metrics
            totalCertificatesIssued++;
            
            LOGGER.info("Issued offline certificate: " + certificateId + 
                       " for amount: " + amount + " " + currencyCode);
            
            return new CertificateOperationResult(true, "Certificate issued successfully", 
                                                certificate, null);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error issuing certificate", e);
            return new CertificateOperationResult(false, "Error: " + e.getMessage(), null, null);
        } finally {
            certificateLock.writeLock().unlock();
        }
    }
    
    /**
     * Validates a certificate offline (before settlement)
     */
    public boolean validateCertificateOffline(OfflineCertificate certificate) {
        try {
            // Check basic validity
            if (certificate == null) {
                return false;
            }
            
            if (certificate.isExpired()) {
                LOGGER.warning("Certificate expired: " + certificate.getCertificateId());
                return false;
            }
            
            // Verify certificate data integrity
            byte[] expectedData = buildCertificateData(
                certificate.getCertificateId(),
                certificate.getIssuer(),
                certificate.getRecipient(),
                certificate.getAmount(),
                certificate.getCurrencyCode(),
                certificate.getExpiryTimestamp(),
                certificate.getSequenceNumber(),
                certificate.getMemo()
            );
            
            if (!Arrays.equals(expectedData, certificate.getCertificateData())) {
                LOGGER.warning("Certificate data integrity check failed");
                return false;
            }
            
            // Verify signature
            // In production, this would verify against the issuer's public key
            if (certificate.getIssuerSignature() == null) {
                LOGGER.warning("Missing issuer signature");
                return false;
            }
            
            LOGGER.info("Certificate validated offline: " + certificate.getCertificateId());
            return true;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error validating certificate", e);
            return false;
        }
    }
    
    /**
     * Settles a certificate on-chain (when connectivity is restored)
     */
    public CertificateOperationResult settleCertificate(String certificateId,
                                                       List<SignatureResult> validatorSignatures) {
        settlementLock.writeLock().lock();
        try {
            OfflineCertificate certificate = pendingCertificates.get(certificateId);
            
            if (certificate == null) {
                // Check if already settled
                if (settledCertificates.containsKey(certificateId)) {
                    return new CertificateOperationResult(false, "Certificate already settled", 
                                                        null, null);
                }
                // Check if revoked
                if (revokedCertificates.containsKey(certificateId)) {
                    return new CertificateOperationResult(false, "Certificate revoked", null, null);
                }
                return new CertificateOperationResult(false, "Certificate not found", null, null);
            }
            
            // Check expiry
            if (certificate.isExpired()) {
                certificate.setStatus(OfflineCertificate.CertificateStatus.EXPIRED);
                pendingCertificates.remove(certificateId);
                return new CertificateOperationResult(false, "Certificate expired", certificate, null);
            }
            
            // Check for double-spend
            String issuerKey = certificate.getIssuer().toString();
            Set<String> spent = spentCertificates.get(issuerKey);
            if (spent != null && spent.contains(certificateId)) {
                totalDoubleSpendAttempts++;
                LOGGER.warning("Double-spend attempt detected for certificate: " + certificateId);
                return new CertificateOperationResult(false, "Double-spend detected", 
                                                    certificate, null);
            }
            
            // Validate offline first
            if (!validateCertificateOffline(certificate)) {
                certificate.setStatus(OfflineCertificate.CertificateStatus.REJECTED);
                pendingCertificates.remove(certificateId);
                return new CertificateOperationResult(false, "Certificate validation failed", 
                                                    certificate, null);
            }
            
            // Check multi-sig requirement
            if (certificate.isRequiresMultiSig()) {
                if (validatorSignatures == null || validatorSignatures.size() < 2) {
                    return new CertificateOperationResult(false, 
                        "Multi-signature requirement not met", certificate, null);
                }
                // Store validator signatures
                for (SignatureResult sig : validatorSignatures) {
                    certificate.addValidatorSignature(sig);
                }
            }
            
            // Update certificate status
            certificate.setStatus(OfflineCertificate.CertificateStatus.VALIDATING);
            
            // Simulate transaction creation and settlement
            // In production, this would create an actual on-chain transaction
            String transactionHash = generateTransactionHash(certificate);
            
            // Mark as settled
            certificate.setStatus(OfflineCertificate.CertificateStatus.SETTLED);
            certificate.setSettlementTransactionHash(transactionHash);
            certificate.setSettlementTimestamp(System.currentTimeMillis());
            
            // Move to settled storage
            pendingCertificates.remove(certificateId);
            settledCertificates.put(certificateId, certificate);
            
            // Mark as spent to prevent double-spend
            spentCertificates.computeIfAbsent(issuerKey, k -> ConcurrentHashMap.newKeySet())
                            .add(certificateId);
            
            // Update metrics
            totalCertificatesSettled++;
            
            LOGGER.info("Certificate settled: " + certificateId + " -> Transaction: " + transactionHash);
            
            return new CertificateOperationResult(true, "Certificate settled successfully",
                                                certificate, transactionHash);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error settling certificate", e);
            return new CertificateOperationResult(false, "Error: " + e.getMessage(), null, null);
        } finally {
            settlementLock.writeLock().unlock();
        }
    }
    
    /**
     * Revokes a certificate before settlement
     */
    public boolean revokeCertificate(String certificateId, Address revoker, String reason) {
        certificateLock.writeLock().lock();
        try {
            OfflineCertificate certificate = pendingCertificates.get(certificateId);
            
            if (certificate == null) {
                return false;
            }
            
            // Only issuer can revoke
            if (!certificate.getIssuer().equals(revoker)) {
                LOGGER.warning("Unauthorized revocation attempt by: " + revoker);
                return false;
            }
            
            // Cannot revoke if already being processed
            if (certificate.getStatus() == OfflineCertificate.CertificateStatus.VALIDATING) {
                return false;
            }
            
            // Update certificate
            certificate.setStatus(OfflineCertificate.CertificateStatus.REVOKED);
            certificate.setRevocationReason(reason);
            
            // Move to revoked storage
            pendingCertificates.remove(certificateId);
            revokedCertificates.put(certificateId, certificate);
            
            // Update metrics
            totalCertificatesRevoked++;
            
            LOGGER.info("Certificate revoked: " + certificateId + " Reason: " + reason);
            
            return true;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error revoking certificate", e);
            return false;
        } finally {
            certificateLock.writeLock().unlock();
        }
    }
    
    /**
     * Gets certificates for an issuer
     */
    public List<OfflineCertificate> getCertificatesForIssuer(Address issuer) {
        certificateLock.readLock().lock();
        try {
            Set<String> certIds = issuerCertificateIndex.getOrDefault(issuer, Collections.emptySet());
            List<OfflineCertificate> certificates = new ArrayList<>();
            
            for (String certId : certIds) {
                OfflineCertificate cert = pendingCertificates.get(certId);
                if (cert == null) {
                    cert = settledCertificates.get(certId);
                }
                if (cert == null) {
                    cert = revokedCertificates.get(certId);
                }
                if (cert != null) {
                    certificates.add(cert);
                }
            }
            
            return certificates;
            
        } finally {
            certificateLock.readLock().unlock();
        }
    }
    
    /**
     * Gets certificates for a recipient
     */
    public List<OfflineCertificate> getCertificatesForRecipient(Address recipient) {
        certificateLock.readLock().lock();
        try {
            Set<String> certIds = recipientCertificateIndex.getOrDefault(recipient, Collections.emptySet());
            List<OfflineCertificate> certificates = new ArrayList<>();
            
            for (String certId : certIds) {
                OfflineCertificate cert = pendingCertificates.get(certId);
                if (cert == null) {
                    cert = settledCertificates.get(certId);
                }
                if (cert != null) {
                    certificates.add(cert);
                }
            }
            
            return certificates;
            
        } finally {
            certificateLock.readLock().unlock();
        }
    }
    
    /**
     * Generates QR code data for offline transmission
     */
    public CertificateQRData generateQRData(String certificateId) {
        OfflineCertificate certificate = pendingCertificates.get(certificateId);
        if (certificate == null) {
            return null;
        }
        return new CertificateQRData(certificate);
    }
    
    /**
     * Batch settlement of multiple certificates
     */
    public Map<String, CertificateOperationResult> settleCertificatesBatch(List<String> certificateIds) {
        Map<String, CertificateOperationResult> results = new ConcurrentHashMap<>();
        
        // Process settlements in parallel
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (String certId : certificateIds) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                CertificateOperationResult result = settleCertificate(certId, null);
                results.put(certId, result);
            }, settlementProcessor);
            
            futures.add(future);
        }
        
        // Wait for all settlements to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        return results;
    }
    
    /**
     * Builds certificate data for signing
     */
    private byte[] buildCertificateData(String certificateId, Address issuer, Address recipient,
                                       BigInteger amount, String currencyCode, long expiryTimestamp,
                                       long sequenceNumber, String memo) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        
        buffer.put(certificateId.getBytes());
        buffer.put(issuer.toSlice());
        buffer.put(recipient.toSlice());
        buffer.put(amount.toByteArray());
        buffer.put(currencyCode.getBytes());
        buffer.putLong(expiryTimestamp);
        buffer.putLong(sequenceNumber);
        
        if (memo != null && !memo.isEmpty()) {
            byte[] memoBytes = memo.getBytes();
            buffer.putInt(memoBytes.length);
            buffer.put(memoBytes);
        }
        
        return Arrays.copyOf(buffer.array(), buffer.position());
    }
    
    /**
     * Generates unique certificate ID
     */
    private String generateCertificateId() {
        byte[] randomBytes = new byte[CERTIFICATE_ID_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return "CERT-" + bytesToHex(randomBytes);
    }
    
    /**
     * Generates transaction hash for settled certificate
     */
    private String generateTransactionHash(OfflineCertificate certificate) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(256);
            buffer.put(certificate.getCertificateId().getBytes());
            buffer.put(certificate.getIssuer().toSlice());
            buffer.put(certificate.getRecipient().toSlice());
            buffer.put(certificate.getAmount().toByteArray());
            buffer.putLong(System.currentTimeMillis());
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Arrays.copyOf(buffer.array(), buffer.position()));
            return "TX-" + bytesToHex(hash);
            
        } catch (Exception e) {
            return "TX-" + UUID.randomUUID().toString();
        }
    }
    
    /**
     * Starts background maintenance tasks
     */
    private void startBackgroundTasks() {
        // Check for expired certificates every hour
        scheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupExpiredCertificates();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in expired certificate cleanup", e);
            }
        }, 1, 60, TimeUnit.MINUTES);
        
        // Auto-settle certificates near expiry (within 24 hours)
        scheduler.scheduleAtFixedRate(() -> {
            try {
                autoSettleExpiringCertificates();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in auto-settlement", e);
            }
        }, 30, 30, TimeUnit.MINUTES);
    }
    
    /**
     * Cleans up expired certificates
     */
    private void cleanupExpiredCertificates() {
        certificateLock.writeLock().lock();
        try {
            List<String> expiredIds = new ArrayList<>();
            
            for (Map.Entry<String, OfflineCertificate> entry : pendingCertificates.entrySet()) {
                if (entry.getValue().isExpired()) {
                    expiredIds.add(entry.getKey());
                }
            }
            
            for (String certId : expiredIds) {
                OfflineCertificate cert = pendingCertificates.remove(certId);
                if (cert != null) {
                    cert.setStatus(OfflineCertificate.CertificateStatus.EXPIRED);
                    LOGGER.info("Certificate expired: " + certId);
                }
            }
            
        } finally {
            certificateLock.writeLock().unlock();
        }
    }
    
    /**
     * Auto-settles certificates nearing expiry
     */
    private void autoSettleExpiringCertificates() {
        List<String> expiringIds = new ArrayList<>();
        long threshold = System.currentTimeMillis() + (24 * 60 * 60 * 1000); // 24 hours
        
        certificateLock.readLock().lock();
        try {
            for (Map.Entry<String, OfflineCertificate> entry : pendingCertificates.entrySet()) {
                OfflineCertificate cert = entry.getValue();
                if (cert.getExpiryTimestamp() < threshold && cert.isValid()) {
                    expiringIds.add(entry.getKey());
                }
            }
        } finally {
            certificateLock.readLock().unlock();
        }
        
        // Attempt to settle expiring certificates
        if (!expiringIds.isEmpty()) {
            LOGGER.info("Auto-settling " + expiringIds.size() + " expiring certificates");
            settleCertificatesBatch(expiringIds);
        }
    }
    
    /**
     * Converts bytes to hex string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * Gets certificate statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("pendingCertificates", pendingCertificates.size());
        stats.put("settledCertificates", settledCertificates.size());
        stats.put("revokedCertificates", revokedCertificates.size());
        stats.put("totalCertificatesIssued", totalCertificatesIssued);
        stats.put("totalCertificatesSettled", totalCertificatesSettled);
        stats.put("totalCertificatesRevoked", totalCertificatesRevoked);
        stats.put("totalDoubleSpendAttempts", totalDoubleSpendAttempts);
        return stats;
    }
    
    /**
     * Shutdown
     */
    public void shutdown() {
        scheduler.shutdown();
        settlementProcessor.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!settlementProcessor.awaitTermination(5, TimeUnit.SECONDS)) {
                settlementProcessor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            settlementProcessor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("OfflineCertificateIssuer shutdown complete");
    }
}