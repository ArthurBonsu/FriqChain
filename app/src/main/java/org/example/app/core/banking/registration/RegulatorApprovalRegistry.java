package org.example.app.core.banking.registration;

import org.example.app.core.crypto.Certificate;
import org.example.app.core.crypto.CertificateAuthority;
import org.example.app.core.crypto.PKIManager;
import org.example.app.core.state.StateManager;
import org.example.app.core.state.BlockchainState;
import org.example.app.core.internal.EventLog;
import org.example.app.core.p2p.PeerNetwork;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;

import java.security.PublicKey;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * RegulatorApprovalRegistry - Decentralized regulatory compliance tracking system
 * 
 * This registry implements a government-resistant approval process where multiple
 * independent regulatory authorities from different jurisdictions must approve
 * bank registrations, preventing any single government from controlling the system.
 */
public class RegulatorApprovalRegistry {
    private static final Logger LOGGER = Logger.getLogger(RegulatorApprovalRegistry.class.getName());
    
    // Approval requirements - 66% consensus required (similar to BFT)
    private static final double APPROVAL_THRESHOLD = 0.66;
    private static final int MIN_REGULATORS_REQUIRED = 7; // Minimum number of registered regulators
    private static final int MAX_SAME_COUNTRY_REGULATORS = 2; // Max regulators from same country
    
    // Regulator types
    public enum RegulatorType {
        CENTRAL_BANK,           // National central bank
        BANKING_AUTHORITY,      // National banking regulator
        FINANCIAL_SERVICES,     // Financial services authority
        INTERNATIONAL_ORG,      // International organization (IMF, World Bank, etc.)
        HUMAN_RIGHTS_ORG       // Human rights organization for crisis response
    }
    
    // Approval status for bank registrations
    public enum ApprovalStatus {
        PENDING,
        APPROVED,
        REJECTED,
        UNDER_REVIEW,
        REQUIRES_MORE_INFO
    }
    
    /**
     * Represents a regulatory authority in the system
     */
    public static class Regulator {
        private final String regulatorId;
        private final String organizationName;
        private final String country;
        private final RegulatorType type;
        private final PublicKey publicKey;
        private final Certificate certificate;
        private final Date registrationDate;
        private final boolean isActive;
        private final Set<String> jurisdictions; // Countries/regions this regulator covers
        private final int reputation; // Reputation score (0-100)
        
        public Regulator(String regulatorId, String organizationName, String country,
                        RegulatorType type, PublicKey publicKey, Certificate certificate,
                        Set<String> jurisdictions, int reputation) {
            this.regulatorId = regulatorId;
            this.organizationName = organizationName;
            this.country = country;
            this.type = type;
            this.publicKey = publicKey;
            this.certificate = certificate;
            this.registrationDate = new Date();
            this.isActive = true;
            this.jurisdictions = new HashSet<>(jurisdictions);
            this.reputation = Math.max(0, Math.min(100, reputation));
        }
        
        // Getters
        public String getRegulatorId() { return regulatorId; }
        public String getOrganizationName() { return organizationName; }
        public String getCountry() { return country; }
        public RegulatorType getType() { return type; }
        public PublicKey getPublicKey() { return publicKey; }
        public Certificate getCertificate() { return certificate; }
        public Date getRegistrationDate() { return registrationDate; }
        public boolean isActive() { return isActive; }
        public Set<String> getJurisdictions() { return new HashSet<>(jurisdictions); }
        public int getReputation() { return reputation; }
        
        public boolean canRegulateCountry(String country) {
            return jurisdictions.contains(country) || jurisdictions.contains("GLOBAL");
        }
    }
    
    /**
     * Represents a regulatory decision on a bank registration
     */
    public static class RegulatoryDecision {
        private final String decisionId;
        private final String regulatorId;
        private final String bankRegistrationId;
        private final ApprovalStatus decision;
        private final String reasoning;
        private final Date decisionDate;
        private final byte[] digitalSignature;
        private final Map<String, String> conditions; // Any conditions attached to approval
        
        public RegulatoryDecision(String regulatorId, String bankRegistrationId,
                                 ApprovalStatus decision, String reasoning,
                                 byte[] digitalSignature) {
            this.decisionId = UUID.randomUUID().toString();
            this.regulatorId = regulatorId;
            this.bankRegistrationId = bankRegistrationId;
            this.decision = decision;
            this.reasoning = reasoning;
            this.decisionDate = new Date();
            this.digitalSignature = digitalSignature;
            this.conditions = new HashMap<>();
        }
        
        // Getters
        public String getDecisionId() { return decisionId; }
        public String getRegulatorId() { return regulatorId; }
        public String getBankRegistrationId() { return bankRegistrationId; }
        public ApprovalStatus getDecision() { return decision; }
        public String getReasoning() { return reasoning; }
        public Date getDecisionDate() { return decisionDate; }
        public byte[] getDigitalSignature() { return digitalSignature; }
        public Map<String, String> getConditions() { return new HashMap<>(conditions); }
        
        public void addCondition(String key, String value) {
            conditions.put(key, value);
        }
    }
    
    /**
     * Represents the approval process for a bank registration
     */
    public static class ApprovalProcess {
        private final String processId;
        private final String bankRegistrationId;
        private final Date startDate;
        private final Map<String, RegulatoryDecision> decisions; // regulatorId -> decision
        private ApprovalStatus finalStatus;
        private Date completionDate;
        private String finalReasoning;
        
        public ApprovalProcess(String bankRegistrationId) {
            this.processId = UUID.randomUUID().toString();
            this.bankRegistrationId = bankRegistrationId;
            this.startDate = new Date();
            this.decisions = new ConcurrentHashMap<>();
            this.finalStatus = ApprovalStatus.PENDING;
        }
        
        // Getters and setters
        public String getProcessId() { return processId; }
        public String getBankRegistrationId() { return bankRegistrationId; }
        public Date getStartDate() { return startDate; }
        public Map<String, RegulatoryDecision> getDecisions() { return new HashMap<>(decisions); }
        public ApprovalStatus getFinalStatus() { return finalStatus; }
        public Date getCompletionDate() { return completionDate; }
        public String getFinalReasoning() { return finalReasoning; }
        
        public void addDecision(RegulatoryDecision decision) {
            decisions.put(decision.getRegulatorId(), decision);
        }
        
        public void setFinalStatus(ApprovalStatus status, String reasoning) {
            this.finalStatus = status;
            this.finalReasoning = reasoning;
            this.completionDate = new Date();
        }
        
        public int getApprovalCount() {
            return (int) decisions.values().stream()
                .filter(d -> d.getDecision() == ApprovalStatus.APPROVED)
                .count();
        }
        
        public int getRejectionCount() {
            return (int) decisions.values().stream()
                .filter(d -> d.getDecision() == ApprovalStatus.REJECTED)
                .count();
        }
    }
    
    // Core infrastructure
    private final StateManager stateManager;
    private final BlockchainState blockchainState;
    private final CertificateAuthority certificateAuthority;
    private final PKIManager pkiManager;
    private final EventLog eventLog;
    private final PeerNetwork peerNetwork;
    
    // Registry storage
    private final Map<String, Regulator> registeredRegulators;
    private final Map<String, ApprovalProcess> approvalProcesses;
    private final Map<String, List<String>> countryRegulators; // country -> list of regulator IDs
    private final AtomicLong totalDecisions;
    
    /**
     * Constructor
     */
    public RegulatorApprovalRegistry(StateManager stateManager,
                                   BlockchainState blockchainState,
                                   CertificateAuthority certificateAuthority,
                                   PKIManager pkiManager,
                                   EventLog eventLog,
                                   PeerNetwork peerNetwork) {
        this.stateManager = stateManager;
        this.blockchainState = blockchainState;
        this.certificateAuthority = certificateAuthority;
        this.pkiManager = pkiManager;
        this.eventLog = eventLog;
        this.peerNetwork = peerNetwork;
        
        this.registeredRegulators = new ConcurrentHashMap<>();
        this.approvalProcesses = new ConcurrentHashMap<>();
        this.countryRegulators = new ConcurrentHashMap<>();
        this.totalDecisions = new AtomicLong(0);
        
        LOGGER.info("RegulatorApprovalRegistry initialized");
    }
    
    /**
     * Registers a new regulatory authority
     */
    public boolean registerRegulator(String organizationName, String country, RegulatorType type,
                                   PublicKey publicKey, Set<String> jurisdictions, int reputation) {
        try {
            // Validate input
            if (organizationName == null || country == null || publicKey == null) {
                LOGGER.warning("Invalid regulator registration parameters");
                return false;
            }
            
            // Check country limits for government resistance
            if (!canAddRegulatorFromCountry(country)) {
                LOGGER.warning("Cannot add more regulators from country: " + country + 
                             " (max " + MAX_SAME_COUNTRY_REGULATORS + " per country)");
                return false;
            }
            
            // Generate regulator ID
            String regulatorId = generateRegulatorId(organizationName, country);
            
            // Issue certificate
            Certificate certificate = certificateAuthority.issueCertificate(
                "REG_" + regulatorId, publicKey);
            
            // Create regulator
            Regulator regulator = new Regulator(regulatorId, organizationName, country,
                                              type, publicKey, certificate, jurisdictions, reputation);
            
            // Store regulator
            registeredRegulators.put(regulatorId, regulator);
            
            // Update country mapping
            countryRegulators.computeIfAbsent(country, k -> new ArrayList<>()).add(regulatorId);
            
            eventLog.logEvent("REGULATOR_REGISTERED: " + organizationName + " (" + country + ")");
            LOGGER.info("Regulator registered: " + organizationName + " from " + country);
            
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Error registering regulator: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Initiates an approval process for a bank registration
     */
    public String initiateApprovalProcess(String bankRegistrationId, String bankCountry) {
        try {
            // Get eligible regulators for this bank's country
            List<Regulator> eligibleRegulators = getEligibleRegulatorsForCountry(bankCountry);
            
            if (eligibleRegulators.size() < MIN_REGULATORS_REQUIRED) {
                LOGGER.warning("Insufficient regulators for approval process. Required: " + 
                             MIN_REGULATORS_REQUIRED + ", Available: " + eligibleRegulators.size());
                return null;
            }
            
            // Create approval process
            ApprovalProcess process = new ApprovalProcess(bankRegistrationId);
            approvalProcesses.put(process.getProcessId(), process);
            
            // Notify eligible regulators
            notifyRegulatorsForReview(eligibleRegulators, bankRegistrationId, process.getProcessId());
            
            eventLog.logEvent("APPROVAL_PROCESS_INITIATED: " + bankRegistrationId);
            LOGGER.info("Approval process initiated for bank registration: " + bankRegistrationId);
            
            return process.getProcessId();
            
        } catch (Exception e) {
            LOGGER.severe("Error initiating approval process: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Submits a regulatory decision
     */
    public boolean submitRegulatoryDecision(String processId, String regulatorId,
                                          ApprovalStatus decision, String reasoning,
                                          byte[] digitalSignature) {
        try {
            // Validate process and regulator
            ApprovalProcess process = approvalProcesses.get(processId);
            if (process == null) {
                LOGGER.warning("Approval process not found: " + processId);
                return false;
            }
            
            Regulator regulator = registeredRegulators.get(regulatorId);
            if (regulator == null || !regulator.isActive()) {
                LOGGER.warning("Regulator not found or inactive: " + regulatorId);
                return false;
            }
            
            // Verify digital signature
            if (!verifyRegulatoryDecisionSignature(processId, decision, reasoning, 
                                                  digitalSignature, regulator.getPublicKey())) {
                LOGGER.warning("Invalid digital signature for regulatory decision");
                return false;
            }
            
            // Create and add decision
            RegulatoryDecision regulatoryDecision = new RegulatoryDecision(
                regulatorId, process.getBankRegistrationId(), decision, reasoning, digitalSignature);
            
            process.addDecision(regulatoryDecision);
            totalDecisions.incrementAndGet();
            
            // Check if process is complete
            evaluateApprovalProcess(process);
            
            eventLog.logEvent("REGULATORY_DECISION: " + regulator.getOrganizationName() + 
                             " - " + decision + " for " + process.getBankRegistrationId());
            
            LOGGER.info("Regulatory decision submitted: " + regulator.getOrganizationName() + 
                       " decided " + decision);
            
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Error submitting regulatory decision: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Evaluates if an approval process is complete and determines final status
     */
    private void evaluateApprovalProcess(ApprovalProcess process) {
        List<Regulator> eligibleRegulators = getEligibleRegulatorsForBankRegistration(
            process.getBankRegistrationId());
        
        int totalEligible = eligibleRegulators.size();
        int approvals = process.getApprovalCount();
        int rejections = process.getRejectionCount();
        int totalDecisions = approvals + rejections;
        
        double approvalRatio = (double) approvals / totalEligible;
        double rejectionRatio = (double) rejections / totalEligible;
        
        // Check for final approval (66% consensus)
        if (approvalRatio >= APPROVAL_THRESHOLD) {
            process.setFinalStatus(ApprovalStatus.APPROVED, 
                "Achieved required consensus: " + String.format("%.1f%%", approvalRatio * 100));
            eventLog.logEvent("BANK_APPROVED_BY_CONSENSUS: " + process.getBankRegistrationId());
            
        // Check for final rejection (>34% rejections makes approval impossible)
        } else if (rejectionRatio > (1.0 - APPROVAL_THRESHOLD)) {
            process.setFinalStatus(ApprovalStatus.REJECTED,
                "Too many rejections: " + String.format("%.1f%%", rejectionRatio * 100));
            eventLog.logEvent("BANK_REJECTED_BY_CONSENSUS: " + process.getBankRegistrationId());
            
        // Check if all eligible regulators have decided
        } else if (totalDecisions == totalEligible) {
            process.setFinalStatus(ApprovalStatus.REJECTED,
                "Insufficient approvals: " + String.format("%.1f%%", approvalRatio * 100) + 
                " (required: " + String.format("%.1f%%", APPROVAL_THRESHOLD * 100) + ")");
            eventLog.logEvent("BANK_REJECTED_INSUFFICIENT_APPROVALS: " + process.getBankRegistrationId());
        }
        
        LOGGER.info("Approval process evaluation: " + process.getBankRegistrationId() + 
                   " - Status: " + process.getFinalStatus() + 
                   " (" + approvals + "/" + totalEligible + " approvals)");
    }
    
    /**
     * Gets eligible regulators for a specific country
     */
    private List<Regulator> getEligibleRegulatorsForCountry(String country) {
        return registeredRegulators.values().stream()
            .filter(reg -> reg.isActive())
            .filter(reg -> reg.canRegulateCountry(country))
            .filter(reg -> reg.getReputation() >= 70) // Minimum reputation threshold
            .collect(Collectors.toList());
    }
    
    /**
     * Gets eligible regulators for a bank registration
     */
    private List<Regulator> getEligibleRegulatorsForBankRegistration(String bankRegistrationId) {
        // This would typically look up the bank's country from the registration
        // For now, return all active regulators with sufficient reputation
        return registeredRegulators.values().stream()
            .filter(reg -> reg.isActive())
            .filter(reg -> reg.getReputation() >= 70)
            .collect(Collectors.toList());
    }
    
    /**
     * Notifies regulators about a new bank registration to review
     */
    private void notifyRegulatorsForReview(List<Regulator> regulators, String bankRegistrationId, 
                                         String processId) {
        for (Regulator regulator : regulators) {
            // In a real system, this would send notifications through the P2P network
            LOGGER.info("Notifying regulator for review: " + regulator.getOrganizationName() + 
                       " for bank " + bankRegistrationId);
        }
    }
    
    /**
     * Verifies the digital signature of a regulatory decision
     */
    private boolean verifyRegulatoryDecisionSignature(String processId, ApprovalStatus decision,
                                                     String reasoning, byte[] signature,
                                                     PublicKey publicKey) {
        // In a real implementation, this would verify the digital signature
        // For now, return true if signature is not null
        return signature != null && signature.length > 0;
    }
    
    /**
     * Checks if another regulator can be added from a specific country
     */
    private boolean canAddRegulatorFromCountry(String country) {
        List<String> regulatorsFromCountry = countryRegulators.get(country);
        return regulatorsFromCountry == null || regulatorsFromCountry.size() < MAX_SAME_COUNTRY_REGULATORS;
    }
    
    /**
     * Generates a unique regulator ID
     */
    private String generateRegulatorId(String organizationName, String country) {
        String base = organizationName.replaceAll("[^A-Za-z0-9]", "") + "_" + country;
        return Hash.create(base.getBytes()).toString().substring(0, 16).toUpperCase();
    }
    
    /**
     * Gets approval process by ID
     */
    public ApprovalProcess getApprovalProcess(String processId) {
        return approvalProcesses.get(processId);
    }
    
    /**
     * Gets approval process by bank registration ID
     */
    public ApprovalProcess getApprovalProcessForBank(String bankRegistrationId) {
        return approvalProcesses.values().stream()
            .filter(process -> process.getBankRegistrationId().equals(bankRegistrationId))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Gets regulator by ID
     */
    public Regulator getRegulator(String regulatorId) {
        return registeredRegulators.get(regulatorId);
    }
    
    /**
     * Gets all registered regulators
     */
    public Collection<Regulator> getAllRegulators() {
        return new ArrayList<>(registeredRegulators.values());
    }
    
    /**
     * Gets active regulators only
     */
    public List<Regulator> getActiveRegulators() {
        return registeredRegulators.values().stream()
            .filter(Regulator::isActive)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets regulators by country
     */
    public List<Regulator> getRegulatorsByCountry(String country) {
        List<String> regulatorIds = countryRegulators.get(country);
        if (regulatorIds == null) {
            return new ArrayList<>();
        }
        
        return regulatorIds.stream()
            .map(registeredRegulators::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets system statistics
     */
    public Map<String, Object> getSystemStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalRegulators", registeredRegulators.size());
        stats.put("activeRegulators", getActiveRegulators().size());
        stats.put("totalApprovalProcesses", approvalProcesses.size());
        stats.put("totalDecisions", totalDecisions.get());
        
        // Process statistics
        long pendingProcesses = approvalProcesses.values().stream()
            .filter(p -> p.getFinalStatus() == ApprovalStatus.PENDING)
            .count();
        long approvedProcesses = approvalProcesses.values().stream()
            .filter(p -> p.getFinalStatus() == ApprovalStatus.APPROVED)
            .count();
        long rejectedProcesses = approvalProcesses.values().stream()
            .filter(p -> p.getFinalStatus() == ApprovalStatus.REJECTED)
            .count();
        
        stats.put("pendingProcesses", pendingProcesses);
        stats.put("approvedProcesses", approvedProcesses);
        stats.put("rejectedProcesses", rejectedProcesses);
        
        // Geographic distribution
        Map<String, Integer> countryDistribution = new HashMap<>();
        for (Regulator regulator : registeredRegulators.values()) {
            countryDistribution.merge(regulator.getCountry(), 1, Integer::sum);
        }
        stats.put("regulatorsByCountry", countryDistribution);
        
        // Government resistance check
        boolean isGovernmentResistant = countryDistribution.values().stream()
            .allMatch(count -> count <= MAX_SAME_COUNTRY_REGULATORS);
        stats.put("isGovernmentResistant", isGovernmentResistant);
        
        return stats;
    }
    
    /**
     * Checks if the regulatory system maintains government resistance
     */
    public boolean isGovernmentResistant() {
        return countryRegulators.values().stream()
            .allMatch(regulators -> regulators.size() <= MAX_SAME_COUNTRY_REGULATORS);
    }
}