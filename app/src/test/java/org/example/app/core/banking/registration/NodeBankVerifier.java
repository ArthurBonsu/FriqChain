package org.example.app.core.banking.registration;

import org.example.app.core.crypto.Certificate;
import org.example.app.core.crypto.CertificateAuthority;
import org.example.app.core.crypto.PKIManager;
import org.example.app.core.p2p.PeerNetwork;
import org.example.app.core.p2p.Peer;
import org.example.app.core.internal.EventLog;
import org.example.app.core.state.StateManager;
import org.example.app.core.types.Hash;
import org.example.app.core.types.Address;
import org.example.app.core.blockchain.transaction.BankRegistrationTransaction;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * NodeBankVerifier - Peer-to-peer bank credential verification system
 * 
 * This system implements distributed verification of bank credentials across the
 * P2P network to ensure no single node can approve fraudulent bank registrations.
 * Multiple independent nodes must verify and reach consensus on bank credentials.
 */
public class NodeBankVerifier {
    private static final Logger LOGGER = Logger.getLogger(NodeBankVerifier.class.getName());
    
    // Verification requirements
    private static final double VERIFICATION_CONSENSUS_THRESHOLD = 0.75; // 75% consensus required
    private static final int MIN_VERIFYING_NODES = 5; // Minimum nodes for verification
    private static final int MAX_VERIFICATION_TIME_MINUTES = 30;
    private static final int VERIFICATION_TIMEOUT_SECONDS = 300; // 5 minutes per node
    
    // Verification types
    public enum VerificationType {
        CERTIFICATE_VALIDATION,    // PKI certificate verification
        CREDENTIAL_AUTHENTICITY,   // Bank credential validation
        SWIFT_CODE_VALIDATION,     // SWIFT code verification
        LICENSE_VERIFICATION,      // Banking license verification
        GEOGRAPHIC_VALIDATION,     // Geographic location verification
        REPUTATION_CHECK          // Bank reputation verification
    }
    
    // Verification result status
    public enum VerificationStatus {
        PENDING,
        VERIFIED,
        REJECTED,
        TIMEOUT,
        NETWORK_ERROR
    }
    
    /**
     * Represents a verification request sent to peer nodes
     */
    public static class VerificationRequest {
        private final String requestId;
        private final String bankRegistrationId;
        private final VerificationType type;
        private final Map<String, Object> verificationData;
        private final Date timestamp;
        private final String requestingNodeId;
        private final byte[] requestHash;
        
        public VerificationRequest(String bankRegistrationId, VerificationType type,
                                 Map<String, Object> verificationData, String requestingNodeId) {
            this.requestId = UUID.randomUUID().toString();
            this.bankRegistrationId = bankRegistrationId;
            this.type = type;
            this.verificationData = new HashMap<>(verificationData);
            this.timestamp = new Date();
            this.requestingNodeId = requestingNodeId;
            this.requestHash = generateRequestHash();
        }
        
        // Getters
        public String getRequestId() { return requestId; }
        public String getBankRegistrationId() { return bankRegistrationId; }
        public VerificationType getType() { return type; }
        public Map<String, Object> getVerificationData() { return new HashMap<>(verificationData); }
        public Date getTimestamp() { return timestamp; }
        public String getRequestingNodeId() { return requestingNodeId; }
        public byte[] getRequestHash() { return Arrays.copyOf(requestHash, requestHash.length); }
        
        private byte[] generateRequestHash() {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update(requestId.getBytes());
                digest.update(bankRegistrationId.getBytes());
                digest.update(type.toString().getBytes());
                digest.update(requestingNodeId.getBytes());
                return digest.digest();
            } catch (Exception e) {
                return new byte[32]; // Fallback to empty hash
            }
        }
        
        public boolean isExpired() {
            long ageMinutes = (System.currentTimeMillis() - timestamp.getTime()) / (1000 * 60);
            return ageMinutes > MAX_VERIFICATION_TIME_MINUTES;
        }
    }
    
    /**
     * Represents a verification response from a peer node
     */
    public static class VerificationResponse {
        private final String responseId;
        private final String requestId;
        private final String verifyingNodeId;
        private final VerificationStatus status;
        private final boolean isVerified;
        private final String reasoning;
        private final Map<String, Object> verificationDetails;
        private final Date timestamp;
        private final byte[] digitalSignature;
        private final int confidenceScore; // 0-100
        
        public VerificationResponse(String requestId, String verifyingNodeId,
                                  VerificationStatus status, boolean isVerified,
                                  String reasoning, int confidenceScore,
                                  byte[] digitalSignature) {
            this.responseId = UUID.randomUUID().toString();
            this.requestId = requestId;
            this.verifyingNodeId = verifyingNodeId;
            this.status = status;
            this.isVerified = isVerified;
            this.reasoning = reasoning;
            this.verificationDetails = new HashMap<>();
            this.timestamp = new Date();
            this.digitalSignature = digitalSignature;
            this.confidenceScore = Math.max(0, Math.min(100, confidenceScore));
        }
        
        // Getters
        public String getResponseId() { return responseId; }
        public String getRequestId() { return requestId; }
        public String getVerifyingNodeId() { return verifyingNodeId; }
        public VerificationStatus getStatus() { return status; }
        public boolean isVerified() { return isVerified; }
        public String getReasoning() { return reasoning; }
        public Map<String, Object> getVerificationDetails() { return new HashMap<>(verificationDetails); }
        public Date getTimestamp() { return timestamp; }
        public byte[] getDigitalSignature() { return Arrays.copyOf(digitalSignature, digitalSignature.length); }
        public int getConfidenceScore() { return confidenceScore; }
        
        public void addVerificationDetail(String key, Object value) {
            verificationDetails.put(key, value);
        }
    }
    
    /**
     * Represents a complete verification process for a bank registration
     */
    public static class BankVerificationProcess {
        private final String processId;
        private final String bankRegistrationId;
        private final Date startTime;
        private final Map<VerificationType, List<VerificationResponse>> responses;
        private final Map<VerificationType, VerificationStatus> typeStatus;
        private VerificationStatus overallStatus;
        private Date completionTime;
        private String finalReasoning;
        
        public BankVerificationProcess(String bankRegistrationId) {
            this.processId = UUID.randomUUID().toString();
            this.bankRegistrationId = bankRegistrationId;
            this.startTime = new Date();
            this.responses = new ConcurrentHashMap<>();
            this.typeStatus = new ConcurrentHashMap<>();
            this.overallStatus = VerificationStatus.PENDING;
            
            // Initialize verification types
            for (VerificationType type : VerificationType.values()) {
                responses.put(type, new ArrayList<>());
                typeStatus.put(type, VerificationStatus.PENDING);
            }
        }
        
        // Getters and methods
        public String getProcessId() { return processId; }
        public String getBankRegistrationId() { return bankRegistrationId; }
        public Date getStartTime() { return startTime; }
        public Map<VerificationType, List<VerificationResponse>> getResponses() { return new HashMap<>(responses); }
        public Map<VerificationType, VerificationStatus> getTypeStatus() { return new HashMap<>(typeStatus); }
        public VerificationStatus getOverallStatus() { return overallStatus; }
        public Date getCompletionTime() { return completionTime; }
        public String getFinalReasoning() { return finalReasoning; }
        
        public synchronized void addResponse(VerificationType type, VerificationResponse response) {
            responses.get(type).add(response);
        }
        
        public synchronized void setTypeStatus(VerificationType type, VerificationStatus status) {
            typeStatus.put(type, status);
        }
        
        public synchronized void setOverallStatus(VerificationStatus status, String reasoning) {
            this.overallStatus = status;
            this.finalReasoning = reasoning;
            this.completionTime = new Date();
        }
        
        public boolean isExpired() {
            long ageMinutes = (System.currentTimeMillis() - startTime.getTime()) / (1000 * 60);
            return ageMinutes > MAX_VERIFICATION_TIME_MINUTES;
        }
        
        public double getOverallConfidence() {
            return responses.values().stream()
                .flatMap(List::stream)
                .filter(r -> r.getStatus() == VerificationStatus.VERIFIED)
                .mapToInt(VerificationResponse::getConfidenceScore)
                .average()
                .orElse(0.0);
        }
    }
    
    // Core infrastructure
    private final PeerNetwork peerNetwork;
    private final PKIManager pkiManager;
    private final CertificateAuthority certificateAuthority;
    private final StateManager stateManager;
    private final EventLog eventLog;
    
    // Verification storage and management
    private final Map<String, BankVerificationProcess> activeVerifications;
    private final Map<String, VerificationRequest> pendingRequests;
    private final ExecutorService verificationExecutor;
    private final ScheduledExecutorService cleanupExecutor;
    private final AtomicLong totalVerifications;
    
    // Node capabilities
    private final Set<VerificationType> nodeCapabilities;
    private final String nodeId;
    
    /**
     * Constructor
     */
    public NodeBankVerifier(PeerNetwork peerNetwork, PKIManager pkiManager,
                           CertificateAuthority certificateAuthority,
                           StateManager stateManager, EventLog eventLog) {
        this.peerNetwork = peerNetwork;
        this.pkiManager = pkiManager;
        this.certificateAuthority = certificateAuthority;
        this.stateManager = stateManager;
        this.eventLog = eventLog;
        
        this.activeVerifications = new ConcurrentHashMap<>();
        this.pendingRequests = new ConcurrentHashMap<>();
        this.verificationExecutor = Executors.newFixedThreadPool(10);
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        this.totalVerifications = new AtomicLong(0);
        
        this.nodeCapabilities = initializeNodeCapabilities();
        this.nodeId = peerNetwork.getPeerId();
        
        // Start cleanup task
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredRequests, 5, 5, TimeUnit.MINUTES);
        
        LOGGER.info("NodeBankVerifier initialized for node: " + nodeId);
    }
    
    /**
     * Initiates verification process for a bank registration
     */
    public String startBankVerification(BankRegistrationTransaction bankRegistration) {
        try {
            String bankRegId = bankRegistration.getRegistrationId();
            LOGGER.info("Starting bank verification process for: " + bankRegId);
            
            // Create verification process
            BankVerificationProcess process = new BankVerificationProcess(bankRegId);
            activeVerifications.put(process.getProcessId(), process);
            
            // Start verification for each type
            for (VerificationType type : VerificationType.values()) {
                CompletableFuture.runAsync(() -> {
                    startVerificationType(process, type, bankRegistration);
                }, verificationExecutor);
            }
            
            // Monitor overall progress
            CompletableFuture.runAsync(() -> {
                monitorVerificationProgress(process);
            }, verificationExecutor);
            
            eventLog.logEvent("VERIFICATION_STARTED: " + bankRegId);
            totalVerifications.incrementAndGet();
            
            return process.getProcessId();
            
        } catch (Exception e) {
            LOGGER.severe("Error starting bank verification: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Starts verification for a specific type
     */
    private void startVerificationType(BankVerificationProcess process, VerificationType type,
                                     BankRegistrationTransaction bankRegistration) {
        try {
            // Create verification data for this type
            Map<String, Object> verificationData = createVerificationData(type, bankRegistration);
            
            // Create verification request
            VerificationRequest request = new VerificationRequest(
                process.getBankRegistrationId(), type, verificationData, nodeId);
            
            pendingRequests.put(request.getRequestId(), request);
            
            // Send request to peer nodes
            List<Peer> availablePeers = getAvailableVerifyingPeers(type);
            if (availablePeers.size() < MIN_VERIFYING_NODES) {
                LOGGER.warning("Insufficient verifying nodes for type: " + type + 
                             " (available: " + availablePeers.size() + 
                             ", required: " + MIN_VERIFYING_NODES + ")");
                process.setTypeStatus(type, VerificationStatus.NETWORK_ERROR);
                return;
            }
            
            // Send verification requests
            sendVerificationRequests(request, availablePeers);
            
            // Wait for responses with timeout
            waitForVerificationResponses(process, request, type);
            
        } catch (Exception e) {
            LOGGER.severe("Error in verification type " + type + ": " + e.getMessage());
            process.setTypeStatus(type, VerificationStatus.NETWORK_ERROR);
        }
    }
    
    /**
     * Creates verification data for a specific type
     */
    private Map<String, Object> createVerificationData(VerificationType type,
                                                     BankRegistrationTransaction bankRegistration) {
        Map<String, Object> data = new HashMap<>();
        
        switch (type) {
            case CERTIFICATE_VALIDATION:
                data.put("publicKey", Arrays.toString(bankRegistration.getPublicKey()));
                data.put("bankAddress", bankRegistration.getBankAddress().toString());
                break;
                
            case CREDENTIAL_AUTHENTICITY:
                data.put("encryptedCredentials", Arrays.toString(bankRegistration.getEncryptedCredentials()));
                data.put("bankLicense", bankRegistration.getBankLicenseId());
                break;
                
            case SWIFT_CODE_VALIDATION:
                data.put("swiftCode", bankRegistration.getSwiftCode());
                data.put("bankCountry", bankRegistration.getBankCountry());
                break;
                
            case LICENSE_VERIFICATION:
                data.put("licenseId", bankRegistration.getBankLicenseId());
                data.put("bankCountry", bankRegistration.getBankCountry());
                data.put("isCentralBank", bankRegistration.isCentralBank());
                break;
                
            case GEOGRAPHIC_VALIDATION:
                data.put("bankCountry", bankRegistration.getBankCountry());
                data.put("geographicRegion", bankRegistration.getGeographicRegion());
                break;
                
            case REPUTATION_CHECK:
                data.put("bankName", bankRegistration.getBankName());
                data.put("swiftCode", bankRegistration.getSwiftCode());
                data.put("bankCountry", bankRegistration.getBankCountry());
                break;
        }
        
        return data;
    }
    
    /**
     * Gets available peers that can verify a specific type
     */
    private List<Peer> getAvailableVerifyingPeers(VerificationType type) {
        // In a real implementation, this would query peer capabilities
        // For now, return all active peers
        return peerNetwork.getActivePeers().stream()
            .filter(peer -> !peer.getId().equals(nodeId)) // Exclude self
            .limit(MIN_VERIFYING_NODES * 2) // Get enough peers
            .collect(Collectors.toList());
    }
    
    /**
     * Sends verification requests to peer nodes
     */
    private void sendVerificationRequests(VerificationRequest request, List<Peer> peers) {
        for (Peer peer : peers) {
            try {
                // In a real implementation, this would send the request over P2P network
                LOGGER.info("Sending verification request to peer: " + peer.getId() + 
                           " for type: " + request.getType());
                
                // Simulate network delay
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(100); // Simulate network latency
                        // In reality, this would be handled by network protocol
                        handleIncomingVerificationRequest(request, peer.getId());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, verificationExecutor);
                
            } catch (Exception e) {
                LOGGER.warning("Error sending verification request to peer " + peer.getId() + 
                             ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Handles incoming verification request from another node
     */
    public void handleIncomingVerificationRequest(VerificationRequest request, String fromNodeId) {
        try {
            // Check if we can handle this verification type
            if (!nodeCapabilities.contains(request.getType())) {
                LOGGER.info("Node cannot handle verification type: " + request.getType());
                return;
            }
            
            // Perform verification
            VerificationResponse response = performVerification(request);
            
            // Send response back
            sendVerificationResponse(response, fromNodeId);
            
        } catch (Exception e) {
            LOGGER.severe("Error handling verification request: " + e.getMessage());
        }
    }
    
    /**
     * Performs verification based on the request type
     */
    private VerificationResponse performVerification(VerificationRequest request) {
        try {
            boolean isVerified = false;
            int confidence = 0;
            String reasoning = "";
            
            Map<String, Object> data = request.getVerificationData();
            
            switch (request.getType()) {
                case CERTIFICATE_VALIDATION:
                    isVerified = verifyCertificateData(data);
                    confidence = isVerified ? 95 : 20;
                    reasoning = isVerified ? "Certificate validation successful" : "Certificate validation failed";
                    break;
                    
                case SWIFT_CODE_VALIDATION:
                    isVerified = verifySwiftCode(data);
                    confidence = isVerified ? 90 : 10;
                    reasoning = isVerified ? "SWIFT code is valid" : "SWIFT code validation failed";
                    break;
                    
                case CREDENTIAL_AUTHENTICITY:
                    isVerified = verifyBankCredentials(data);
                    confidence = isVerified ? 85 : 15;
                    reasoning = isVerified ? "Bank credentials verified" : "Credential verification failed";
                    break;
                    
                case LICENSE_VERIFICATION:
                    isVerified = verifyBankingLicense(data);
                    confidence = isVerified ? 80 : 25;
                    reasoning = isVerified ? "Banking license verified" : "License verification failed";
                    break;
                    
                case GEOGRAPHIC_VALIDATION:
                    isVerified = verifyGeographicData(data);
                    confidence = isVerified ? 75 : 30;
                    reasoning = isVerified ? "Geographic data verified" : "Geographic validation failed";
                    break;
                    
                case REPUTATION_CHECK:
                    isVerified = verifyBankReputation(data);
                    confidence = isVerified ? 70 : 40;
                    reasoning = isVerified ? "Bank reputation check passed" : "Bank reputation concerns";
                    break;
            }
            
            // Create digital signature (simplified for demo)
            byte[] signature = createVerificationSignature(request, isVerified);
            
            VerificationResponse response = new VerificationResponse(
                request.getRequestId(), nodeId, VerificationStatus.VERIFIED,
                isVerified, reasoning, confidence, signature);
            
            LOGGER.info("Verification completed for type " + request.getType() + 
                       ": " + isVerified + " (confidence: " + confidence + "%)");
            
            return response;
            
        } catch (Exception e) {
            LOGGER.severe("Error performing verification: " + e.getMessage());
            
            return new VerificationResponse(
                request.getRequestId(), nodeId, VerificationStatus.NETWORK_ERROR,
                false, "Verification error: " + e.getMessage(), 0, new byte[0]);
        }
    }
    
    /**
     * Verification methods for different types
     */
    private boolean verifyCertificateData(Map<String, Object> data) {
        // In a real implementation, this would verify PKI certificates
        return data.containsKey("publicKey") && data.containsKey("bankAddress");
    }
    
    private boolean verifySwiftCode(Map<String, Object> data) {
        String swiftCode = (String) data.get("swiftCode");
        // Basic SWIFT code format validation
        return swiftCode != null && swiftCode.matches("[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?");
    }
    
    private boolean verifyBankCredentials(Map<String, Object> data) {
        // In a real implementation, this would verify encrypted credentials
        return data.containsKey("encryptedCredentials") && data.containsKey("bankLicense");
    }
    
    private boolean verifyBankingLicense(Map<String, Object> data) {
        String licenseId = (String) data.get("licenseId");
        // Basic license validation
        return licenseId != null && !licenseId.trim().isEmpty();
    }
    
    private boolean verifyGeographicData(Map<String, Object> data) {
        String country = (String) data.get("bankCountry");
        Integer region = (Integer) data.get("geographicRegion");
        // Basic geographic validation
        return country != null && region != null && region >= 1 && region <= 4;
    }
    
    private boolean verifyBankReputation(Map<String, Object> data) {
        // In a real implementation, this would check reputation databases
        String bankName = (String) data.get("bankName");
        return bankName != null && !bankName.trim().isEmpty();
    }
    
    /**
     * Creates a digital signature for verification response
     */
    private byte[] createVerificationSignature(VerificationRequest request, boolean isVerified) {
        try {
            // In a real implementation, this would use proper digital signatures
            String data = request.getRequestId() + nodeId + isVerified;
            return data.getBytes();
        } catch (Exception e) {
            LOGGER.warning("Error creating verification signature: " + e.getMessage());
            return new byte[0];
        }
    }
    
    /**
     * Sends verification response back to requesting node
     */
    private void sendVerificationResponse(VerificationResponse response, String toNodeId) {
        try {
            // In a real implementation, this would send over P2P network
            LOGGER.info("Sending verification response to node: " + toNodeId);
            
            // Simulate receiving the response
            CompletableFuture.runAsync(() -> {
                handleVerificationResponse(response);
            }, verificationExecutor);
            
        } catch (Exception e) {
            LOGGER.warning("Error sending verification response: " + e.getMessage());
        }
    }
    
    /**
     * Handles incoming verification response
     */
    private void handleVerificationResponse(VerificationResponse response) {
        try {
            // Find the corresponding process
            BankVerificationProcess process = findProcessByRequestId(response.getRequestId());
            if (process == null) {
                LOGGER.warning("No process found for response: " + response.getRequestId());
                return;
            }
            
            // Find the verification type
            VerificationRequest request = pendingRequests.get(response.getRequestId());
            if (request == null) {
                LOGGER.warning("No request found for response: " + response.getRequestId());
                return;
            }
            
            // Add response to process
            process.addResponse(request.getType(), response);
            
            LOGGER.info("Received verification response from " + response.getVerifyingNodeId() + 
                       " for type " + request.getType() + ": " + response.isVerified());
            
        } catch (Exception e) {
            LOGGER.severe("Error handling verification response: " + e.getMessage());
        }
    }
    
    /**
     * Waits for verification responses with timeout
     */
    private void waitForVerificationResponses(BankVerificationProcess process,
                                            VerificationRequest request,
                                            VerificationType type) {
        try {
            // Wait for responses or timeout
            long startTime = System.currentTimeMillis();
            long timeoutMs = VERIFICATION_TIMEOUT_SECONDS * 1000L;
            
            while ((System.currentTimeMillis() - startTime) < timeoutMs) {
                List<VerificationResponse> responses = process.getResponses().get(type);
                
                if (responses.size() >= MIN_VERIFYING_NODES) {
                    evaluateVerificationConsensus(process, type, responses);
                    return;
                }
                
                Thread.sleep(1000); // Check every second
            }
            
            // Timeout
            LOGGER.warning("Verification timeout for type: " + type);
            process.setTypeStatus(type, VerificationStatus.TIMEOUT);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.setTypeStatus(type, VerificationStatus.NETWORK_ERROR);
        } catch (Exception e) {
            LOGGER.severe("Error waiting for verification responses: " + e.getMessage());
            process.setTypeStatus(type, VerificationStatus.NETWORK_ERROR);
        }
    }
    
    /**
     * Evaluates verification consensus for a specific type
     */
    private void evaluateVerificationConsensus(BankVerificationProcess process,
                                             VerificationType type,
                                             List<VerificationResponse> responses) {
        try {
            int totalResponses = responses.size();
            long verifiedCount = responses.stream()
                .filter(VerificationResponse::isVerified)
                .count();
            
            double verificationRatio = (double) verifiedCount / totalResponses;
            double avgConfidence = responses.stream()
                .filter(VerificationResponse::isVerified)
                .mapToInt(VerificationResponse::getConfidenceScore)
                .average()
                .orElse(0.0);
            
            if (verificationRatio >= VERIFICATION_CONSENSUS_THRESHOLD && avgConfidence >= 70) {
                process.setTypeStatus(type, VerificationStatus.VERIFIED);
                LOGGER.info("Verification consensus reached for type " + type + 
                           ": " + String.format("%.1f%%", verificationRatio * 100));
            } else {
                process.setTypeStatus(type, VerificationStatus.REJECTED);
                LOGGER.info("Verification consensus failed for type " + type + 
                           ": " + String.format("%.1f%%", verificationRatio * 100));
            }
            
        } catch (Exception e) {
            LOGGER.severe("Error evaluating verification consensus: " + e.getMessage());
            process.setTypeStatus(type, VerificationStatus.NETWORK_ERROR);
        }
    }
    
    /**
     * Monitors overall verification progress
     */
    private void monitorVerificationProgress(BankVerificationProcess process) {
        try {
            while (process.getOverallStatus() == VerificationStatus.PENDING && !process.isExpired()) {
                // Check if all types are complete
                boolean allComplete = process.getTypeStatus().values().stream()
                    .allMatch(status -> status != VerificationStatus.PENDING);
                
                if (allComplete) {
                    evaluateOverallVerification(process);
                    break;
                }
                
                Thread.sleep(5000); // Check every 5 seconds
            }
            
            if (process.isExpired() && process.getOverallStatus() == VerificationStatus.PENDING) {
                process.setOverallStatus(VerificationStatus.TIMEOUT, "Verification process expired");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.severe("Error monitoring verification progress: " + e.getMessage());
        }
    }
    
    /**
     * Evaluates overall verification result
     */
    private void evaluateOverallVerification(BankVerificationProcess process) {
        Map<VerificationType, VerificationStatus> typeStatus = process.getTypeStatus();
        
        long verifiedTypes = typeStatus.values().stream()
            .filter(status -> status == VerificationStatus.VERIFIED)
            .count();
        
        double overallVerificationRatio = (double) verifiedTypes / typeStatus.size();
        
        if (overallVerificationRatio >= VERIFICATION_CONSENSUS_THRESHOLD) {
            process.setOverallStatus(VerificationStatus.VERIFIED,
                "Bank verification successful: " + 
                String.format("%.1f%%", overallVerificationRatio * 100) + " of verification types passed");
            
            eventLog.logEvent("BANK_VERIFICATION_SUCCESS: " + process.getBankRegistrationId());
            
        } else {
            process.setOverallStatus(VerificationStatus.REJECTED,
                "Bank verification failed: Only " + 
                String.format("%.1f%%", overallVerificationRatio * 100) + " of verification types passed");
            
            eventLog.logEvent("BANK_VERIFICATION_FAILED: " + process.getBankRegistrationId());
        }
        
        LOGGER.info("Overall verification completed for " + process.getBankRegistrationId() + 
                   ": " + process.getOverallStatus());
    }
    
    /**
     * Utility methods
     */
    private BankVerificationProcess findProcessByRequestId(String requestId) {
        return activeVerifications.values().stream()
            .filter(process -> 
                process.getResponses().values().stream()
                    .flatMap(List::stream)
                    .anyMatch(r -> r.getRequestId().equals(requestId)))
            .findFirst()
            .orElse(null);
    }
    
    private Set<VerificationType> initializeNodeCapabilities() {
        // For demo purposes, all nodes support all verification types
        return EnumSet.allOf(VerificationType.class);
    }
    
    /**
     * Cleanup expired requests and processes
     */
    private void cleanupExpiredRequests() {
        try {
            // Remove expired requests
            pendingRequests.entrySet().removeIf(entry -> entry.getValue().isExpired());
            
            // Remove completed or expired processes
            activeVerifications.entrySet().removeIf(entry -> {
                BankVerificationProcess process = entry.getValue();
                return process.getOverallStatus() != VerificationStatus.PENDING || process.isExpired();
            });
            
            LOGGER.info("Cleanup completed. Active verifications: " + activeVerifications.size() + 
                       ", Pending requests: " + pendingRequests.size());
            
        } catch (Exception e) {
            LOGGER.severe("Error during cleanup: " + e.getMessage());
        }
    }
    
    /**
     * Public API methods
     */
    public BankVerificationProcess getVerificationProcess(String processId) {
        return activeVerifications.get(processId);
    }
    
    public Collection<BankVerificationProcess> getActiveVerifications() {
        return new ArrayList<>(activeVerifications.values());
    }
    
    public Map<String, Object> getSystemStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalVerifications", totalVerifications.get());
        stats.put("activeVerifications", activeVerifications.size());
        stats.put("pendingRequests", pendingRequests.size());
        stats.put("nodeCapabilities", new ArrayList<>(nodeCapabilities));
        
        // Status distribution
        Map<VerificationStatus, Long> statusDistribution = activeVerifications.values().stream()
            .collect(Collectors.groupingBy(BankVerificationProcess::getOverallStatus,
                    Collectors.counting()));
        stats.put("statusDistribution", statusDistribution);
        
        return stats;
    }
    
    /**
     * Shutdown method
     */
    public void shutdown() {
        try {
            verificationExecutor.shutdown();
            cleanupExecutor.shutdown();
            
            if (!verificationExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                verificationExecutor.shutdownNow();
            }
            if (!cleanupExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
            
            LOGGER.info("NodeBankVerifier shutdown completed");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Shutdown interrupted");
        }
    }
}