package org.example.app.core.banking.registration;

import org.example.app.core.crypto.Certificate;
import org.example.app.core.crypto.CertificateAuthority;
import org.example.app.core.crypto.PKIManager;
import org.example.app.core.state.StateManager;
import org.example.app.core.internal.EventLog;
import org.example.app.core.p2p.PeerNetwork;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;

import java.math.BigInteger;
import java.security.PublicKey;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * AuditorRegistry - Manages independent auditors for Proof of Reserves verification
 */
public class AuditorRegistry {
    private static final Logger LOGGER = Logger.getLogger(AuditorRegistry.class.getName());
    
    private static final BigInteger MIN_AUDITOR_BOND = new BigInteger("50000000000000000000");
    private static final int MIN_AUDITORS_FOR_VERIFICATION = 3;
    private static final double AUDIT_CONSENSUS_THRESHOLD = 0.75;
    private static final int MAX_AUDITORS_PER_COUNTRY = 3;
    private static final int MAX_AUDITORS_PER_ORG_TYPE = 5;
    
    public enum AuditType {
        RESERVE_VERIFICATION, TRANSACTION_AUDIT, SECURITY_AUDIT,
        GOVERNANCE_AUDIT, FINANCIAL_STATEMENT, COMMODITY_VERIFICATION, REGULATORY_COMPLIANCE
    }
    
    public enum OrganizationType {
        BIG_FOUR, REGIONAL_FIRM, SPECIALIZED_FIRM, NGO, ACADEMIC, GOVERNMENT_WATCHDOG, INDEPENDENT
    }
    
    public enum AuditorStatus { PENDING, ACTIVE, SUSPENDED, UNDER_REVIEW, REMOVED }
    public enum AuditReportStatus { DRAFT, SUBMITTED, UNDER_REVIEW, VERIFIED, DISPUTED, REJECTED, FINALIZED }
    
    public static class Auditor {
        private final String auditorId;
        private final String organizationName;
        private final OrganizationType organizationType;
        private final Address operatorAddress;
        private final PublicKey signingKey;
        private final Certificate certificate;
        private final Set<AuditType> certifiedAuditTypes;
        private final String country;
        private final String licenseNumber;
        private final Date registrationDate;
        private final List<String> accreditations;
        
        private BigInteger bondAmount;
        private AuditorStatus status;
        private int reputation;
        private long totalAudits, verifiedAudits, disputedAudits;
        private Date lastAuditDate;
        private Set<String> currentAssignments;
        
        public Auditor(String auditorId, String orgName, OrganizationType orgType,
                      Address opAddress, PublicKey signingKey, Certificate cert,
                      Set<AuditType> auditTypes, String country, String license,
                      BigInteger bond, List<String> accreditations) {
            this.auditorId = auditorId;
            this.organizationName = orgName;
            this.organizationType = orgType;
            this.operatorAddress = opAddress;
            this.signingKey = signingKey;
            this.certificate = cert;
            this.certifiedAuditTypes = new HashSet<>(auditTypes);
            this.country = country;
            this.licenseNumber = license;
            this.registrationDate = new Date();
            this.accreditations = new ArrayList<>(accreditations);
            this.bondAmount = bond;
            this.status = AuditorStatus.PENDING;
            this.reputation = 500;
            this.currentAssignments = ConcurrentHashMap.newKeySet();
        }
        
        public String getAuditorId() { return auditorId; }
        public String getOrganizationName() { return organizationName; }
        public OrganizationType getOrganizationType() { return organizationType; }
        public Address getOperatorAddress() { return operatorAddress; }
        public PublicKey getSigningKey() { return signingKey; }
        public Certificate getCertificate() { return certificate; }
        public Set<AuditType> getCertifiedAuditTypes() { return new HashSet<>(certifiedAuditTypes); }
        public String getCountry() { return country; }
        public String getLicenseNumber() { return licenseNumber; }
        public Date getRegistrationDate() { return registrationDate; }
        public List<String> getAccreditations() { return new ArrayList<>(accreditations); }
        public BigInteger getBondAmount() { return bondAmount; }
        public AuditorStatus getStatus() { return status; }
        public int getReputation() { return reputation; }
        public long getTotalAudits() { return totalAudits; }
        public long getVerifiedAudits() { return verifiedAudits; }
        public Date getLastAuditDate() { return lastAuditDate; }
        public Set<String> getCurrentAssignments() { return new HashSet<>(currentAssignments); }
        
        public void setStatus(AuditorStatus s) { this.status = s; }
        public void setReputation(int r) { this.reputation = Math.max(0, Math.min(1000, r)); }
        public void setLastAuditDate(Date d) { this.lastAuditDate = d; }
        public void addAssignment(String id) { currentAssignments.add(id); }
        public void removeAssignment(String id) { currentAssignments.remove(id); }
        
        public void recordVerifiedAudit() { totalAudits++; verifiedAudits++; reputation = Math.min(1000, reputation + 15); }
        public void recordDisputedAudit() { totalAudits++; disputedAudits++; reputation = Math.max(0, reputation - 50); }
        public double getVerificationRate() { return totalAudits == 0 ? 1.0 : (double) verifiedAudits / totalAudits; }
        public boolean canPerformAudit(AuditType type) {
            return status == AuditorStatus.ACTIVE && certifiedAuditTypes.contains(type) && currentAssignments.size() < 5;
        }
    }
    
    public static class AuditAssignment {
        private final String assignmentId;
        private final AuditType auditType;
        private final String targetEntityId, targetEntityName;
        private final List<String> assignedAuditors;
        private final Date createdDate, deadline;
        private final int requiredSignatures;
        private AuditReportStatus status;
        private final Map<String, AuditReport> auditorReports;
        private AuditConsensus finalConsensus;
        
        public AuditAssignment(AuditType type, String entityId, String entityName,
                              List<String> auditors, Date deadline, int reqSigs) {
            this.assignmentId = UUID.randomUUID().toString();
            this.auditType = type;
            this.targetEntityId = entityId;
            this.targetEntityName = entityName;
            this.assignedAuditors = new ArrayList<>(auditors);
            this.createdDate = new Date();
            this.deadline = deadline;
            this.requiredSignatures = reqSigs;
            this.status = AuditReportStatus.DRAFT;
            this.auditorReports = new ConcurrentHashMap<>();
        }
        
        public String getAssignmentId() { return assignmentId; }
        public AuditType getAuditType() { return auditType; }
        public String getTargetEntityId() { return targetEntityId; }
        public String getTargetEntityName() { return targetEntityName; }
        public List<String> getAssignedAuditors() { return new ArrayList<>(assignedAuditors); }
        public Date getDeadline() { return deadline; }
        public int getRequiredSignatures() { return requiredSignatures; }
        public AuditReportStatus getStatus() { return status; }
        public Map<String, AuditReport> getAuditorReports() { return new HashMap<>(auditorReports); }
        public AuditConsensus getFinalConsensus() { return finalConsensus; }
        
        public void setStatus(AuditReportStatus s) { this.status = s; }
        public void setFinalConsensus(AuditConsensus c) { this.finalConsensus = c; }
        public void addReport(AuditReport r) { auditorReports.put(r.getAuditorId(), r); }
        public boolean hasEnoughReports() { return auditorReports.size() >= requiredSignatures; }
        public boolean isExpired() { return new Date().after(deadline); }
    }
    
    public static class AuditReport {
        private final String reportId, auditorId, assignmentId, summary;
        private final AuditType auditType;
        private final Date submissionDate;
        private final boolean passed;
        private final Map<String, Object> findings;
        private final Map<String, BigInteger> verifiedAmounts;
        private final List<String> concerns, recommendations;
        private final byte[] signature, evidenceHash;
        
        public AuditReport(String auditorId, String assignmentId, AuditType type,
                         boolean passed, String summary, Map<String, Object> findings,
                         Map<String, BigInteger> amounts, List<String> concerns,
                         List<String> recs, byte[] sig, byte[] evidence) {
            this.reportId = UUID.randomUUID().toString();
            this.auditorId = auditorId;
            this.assignmentId = assignmentId;
            this.auditType = type;
            this.submissionDate = new Date();
            this.passed = passed;
            this.summary = summary;
            this.findings = new HashMap<>(findings);
            this.verifiedAmounts = new HashMap<>(amounts);
            this.concerns = new ArrayList<>(concerns);
            this.recommendations = new ArrayList<>(recs);
            this.signature = sig;
            this.evidenceHash = evidence;
        }
        
        public String getReportId() { return reportId; }
        public String getAuditorId() { return auditorId; }
        public boolean isPassed() { return passed; }
        public Map<String, BigInteger> getVerifiedAmounts() { return new HashMap<>(verifiedAmounts); }
    }
    
    public static class AuditConsensus {
        private final String consensusId, assignmentId, consolidatedSummary;
        private final AuditType auditType;
        private final boolean overallPassed;
        private final double consensusScore;
        private final List<String> agreeingAuditors, disagreeingAuditors;
        private final Map<String, BigInteger> consensusAmounts;
        private final Date finalizedDate;
        private final byte[] thresholdSignature;
        
        public AuditConsensus(String assignmentId, AuditType type, boolean passed,
                            double score, List<String> agreeing, List<String> disagreeing,
                            Map<String, BigInteger> amounts, String summary, byte[] sig) {
            this.consensusId = UUID.randomUUID().toString();
            this.assignmentId = assignmentId;
            this.auditType = type;
            this.overallPassed = passed;
            this.consensusScore = score;
            this.agreeingAuditors = new ArrayList<>(agreeing);
            this.disagreeingAuditors = new ArrayList<>(disagreeing);
            this.consensusAmounts = new HashMap<>(amounts);
            this.consolidatedSummary = summary;
            this.finalizedDate = new Date();
            this.thresholdSignature = sig;
        }
        
        public String getConsensusId() { return consensusId; }
        public boolean isOverallPassed() { return overallPassed; }
        public double getConsensusScore() { return consensusScore; }
        public Map<String, BigInteger> getConsensusAmounts() { return new HashMap<>(consensusAmounts); }
    }
    
    private final StateManager stateManager;
    private final CertificateAuthority certificateAuthority;
    private final PKIManager pkiManager;
    private final EventLog eventLog;
    private final PeerNetwork peerNetwork;
    
    private final Map<String, Auditor> auditors = new ConcurrentHashMap<>();
    private final Map<Address, String> addressToAuditor = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> countryAuditors = new ConcurrentHashMap<>();
    private final Map<OrganizationType, Set<String>> typeAuditors = new ConcurrentHashMap<>();
    private final Map<AuditType, Set<String>> auditTypeAuditors = new ConcurrentHashMap<>();
    private final Map<String, AuditAssignment> assignments = new ConcurrentHashMap<>();
    private final AtomicLong totalAudits = new AtomicLong(0);
    private final ScheduledExecutorService scheduler;
    
    public AuditorRegistry(StateManager stateManager, CertificateAuthority ca,
                          PKIManager pkiManager, EventLog eventLog, PeerNetwork peerNetwork) {
        this.stateManager = stateManager;
        this.certificateAuthority = ca;
        this.pkiManager = pkiManager;
        this.eventLog = eventLog;
        this.peerNetwork = peerNetwork;
        
        for (OrganizationType t : OrganizationType.values()) typeAuditors.put(t, ConcurrentHashMap.newKeySet());
        for (AuditType t : AuditType.values()) auditTypeAuditors.put(t, ConcurrentHashMap.newKeySet());
        
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::processExpiredAssignments, 1, 1, TimeUnit.HOURS);
        LOGGER.info("AuditorRegistry initialized");
    }
    
    public String registerAuditor(String orgName, OrganizationType orgType, Address opAddress,
                                 PublicKey signingKey, Set<AuditType> auditTypes, String country,
                                 String license, BigInteger bond, List<String> accreditations) {
        try {
            if (bond.compareTo(MIN_AUDITOR_BOND) < 0) return null;
            Set<String> cSet = countryAuditors.get(country);
            if (cSet != null && cSet.size() >= MAX_AUDITORS_PER_COUNTRY) return null;
            if (typeAuditors.get(orgType).size() >= MAX_AUDITORS_PER_ORG_TYPE) return null;
            if (addressToAuditor.containsKey(opAddress)) return null;
            
            String auditorId = generateAuditorId(orgName, opAddress);
            Certificate cert = certificateAuthority.issueCertificate("AUDITOR_" + auditorId, signingKey);
            Auditor auditor = new Auditor(auditorId, orgName, orgType, opAddress, signingKey, cert,
                                         auditTypes, country, license, bond, accreditations);
            
            auditors.put(auditorId, auditor);
            addressToAuditor.put(opAddress, auditorId);
            countryAuditors.computeIfAbsent(country, k -> ConcurrentHashMap.newKeySet()).add(auditorId);
            typeAuditors.get(orgType).add(auditorId);
            for (AuditType t : auditTypes) auditTypeAuditors.get(t).add(auditorId);
            stateManager.updateBalance(opAddress.toString(), bond.negate());
            
            eventLog.logEvent("AUDITOR_REGISTERED: " + auditorId);
            return auditorId;
        } catch (Exception e) {
            LOGGER.severe("Error registering auditor: " + e.getMessage());
            return null;
        }
    }
    
    public boolean activateAuditor(String auditorId) {
        Auditor a = auditors.get(auditorId);
        if (a == null || a.getStatus() != AuditorStatus.PENDING) return false;
        a.setStatus(AuditorStatus.ACTIVE);
        eventLog.logEvent("AUDITOR_ACTIVATED: " + auditorId);
        return true;
    }
    
    public String createAuditAssignment(AuditType type, String entityId, String entityName,
                                       int numAuditors, int reqSigs, int deadlineDays) {
        List<String> selected = selectDiverseAuditors(type, numAuditors);
        if (selected.size() < MIN_AUDITORS_FOR_VERIFICATION) return null;
        
        Date deadline = new Date(System.currentTimeMillis() + (long)deadlineDays * 86400000);
        AuditAssignment assignment = new AuditAssignment(type, entityId, entityName, selected, deadline, reqSigs);
        assignments.put(assignment.getAssignmentId(), assignment);
        
        for (String id : selected) {
            Auditor a = auditors.get(id);
            if (a != null) a.addAssignment(assignment.getAssignmentId());
        }
        
        totalAudits.incrementAndGet();
        eventLog.logEvent("AUDIT_ASSIGNED: " + assignment.getAssignmentId());
        return assignment.getAssignmentId();
    }
    
    private List<String> selectDiverseAuditors(AuditType type, int num) {
        Set<String> eligible = auditTypeAuditors.get(type);
        if (eligible == null) return new ArrayList<>();
        
        List<Auditor> candidates = eligible.stream().map(auditors::get)
            .filter(a -> a != null && a.canPerformAudit(type))
            .sorted((a, b) -> Integer.compare(b.getReputation(), a.getReputation()))
            .collect(Collectors.toList());
        
        List<String> selected = new ArrayList<>();
        Set<String> countries = new HashSet<>();
        Set<OrganizationType> types = new HashSet<>();
        
        for (Auditor c : candidates) {
            if (selected.size() >= num) break;
            if (!countries.contains(c.getCountry()) || countries.size() >= num / 2) {
                if (!types.contains(c.getOrganizationType()) || types.size() >= 3) {
                    selected.add(c.getAuditorId());
                    countries.add(c.getCountry());
                    types.add(c.getOrganizationType());
                }
            }
        }
        return selected;
    }
    
    public boolean submitAuditReport(String assignmentId, String auditorId, boolean passed,
                                    String summary, Map<String, Object> findings,
                                    Map<String, BigInteger> amounts, List<String> concerns,
                                    List<String> recs, byte[] sig, byte[] evidence) {
        AuditAssignment assignment = assignments.get(assignmentId);
        if (assignment == null || !assignment.getAssignedAuditors().contains(auditorId)) return false;
        if (assignment.isExpired()) return false;
        
        Auditor auditor = auditors.get(auditorId);
        if (auditor == null || auditor.getStatus() != AuditorStatus.ACTIVE) return false;
        
        AuditReport report = new AuditReport(auditorId, assignmentId, assignment.getAuditType(),
                                            passed, summary, findings, amounts, concerns, recs, sig, evidence);
        assignment.addReport(report);
        assignment.setStatus(AuditReportStatus.SUBMITTED);
        auditor.setLastAuditDate(new Date());
        
        eventLog.logEvent("AUDIT_REPORT_SUBMITTED: " + report.getReportId());
        if (assignment.hasEnoughReports()) attemptAuditConsensus(assignment);
        return true;
    }
    
    private void attemptAuditConsensus(AuditAssignment assignment) {
        Map<String, AuditReport> reports = assignment.getAuditorReports();
        List<String> agreeing = new ArrayList<>(), disagreeing = new ArrayList<>();
        Map<String, List<BigInteger>> amountsByKey = new HashMap<>();
        
        for (AuditReport r : reports.values()) {
            (r.isPassed() ? agreeing : disagreeing).add(r.getAuditorId());
            r.getVerifiedAmounts().forEach((k, v) -> 
                amountsByKey.computeIfAbsent(k, x -> new ArrayList<>()).add(v));
        }
        
        int total = agreeing.size() + disagreeing.size();
        double score = (double) Math.max(agreeing.size(), disagreeing.size()) / total;
        
        if (score >= AUDIT_CONSENSUS_THRESHOLD && total >= assignment.getRequiredSignatures()) {
            boolean passed = agreeing.size() > disagreeing.size();
            Map<String, BigInteger> consensusAmounts = new HashMap<>();
            amountsByKey.forEach((k, v) -> {
                v.sort(BigInteger::compareTo);
                consensusAmounts.put(k, v.get(v.size() / 2));
            });
            
            AuditConsensus consensus = new AuditConsensus(assignment.getAssignmentId(),
                assignment.getAuditType(), passed, score,
                passed ? agreeing : disagreeing, passed ? disagreeing : agreeing,
                consensusAmounts, "Consensus: " + String.format("%.1f%%", score * 100), new byte[64]);
            
            assignment.setFinalConsensus(consensus);
            assignment.setStatus(AuditReportStatus.FINALIZED);
            
            (passed ? agreeing : disagreeing).forEach(id -> {
                Auditor a = auditors.get(id);
                if (a != null) { a.recordVerifiedAudit(); a.removeAssignment(assignment.getAssignmentId()); }
            });
            (passed ? disagreeing : agreeing).forEach(id -> {
                Auditor a = auditors.get(id);
                if (a != null) { a.recordDisputedAudit(); a.removeAssignment(assignment.getAssignmentId()); }
            });
            
            eventLog.logEvent("AUDIT_CONSENSUS: " + assignment.getAssignmentId() + " passed=" + passed);
        }
    }
    
    private void processExpiredAssignments() {
        assignments.values().stream().filter(AuditAssignment::isExpired)
            .filter(a -> a.getStatus() != AuditReportStatus.FINALIZED)
            .forEach(a -> { a.setStatus(AuditReportStatus.REJECTED); 
                           eventLog.logEvent("AUDIT_EXPIRED: " + a.getAssignmentId()); });
    }
    
    private String generateAuditorId(String name, Address address) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] h = d.digest((name + address.toString() + System.currentTimeMillis()).getBytes());
            return Hash.fromBytes(h).toString().substring(0, 16).toUpperCase();
        } catch (Exception e) { return UUID.randomUUID().toString().substring(0, 16).toUpperCase(); }
    }
    
    public Auditor getAuditor(String id) { return auditors.get(id); }
    public Collection<Auditor> getAllAuditors() { return new ArrayList<>(auditors.values()); }
    public AuditAssignment getAssignment(String id) { return assignments.get(id); }
    
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAuditors", auditors.size());
        stats.put("activeAuditors", auditors.values().stream().filter(a -> a.getStatus() == AuditorStatus.ACTIVE).count());
        stats.put("totalAudits", totalAudits.get());
        stats.put("pendingAssignments", assignments.values().stream().filter(a -> a.getStatus() == AuditReportStatus.SUBMITTED).count());
        return stats;
    }
    
    public void shutdown() { scheduler.shutdown(); LOGGER.info("AuditorRegistry shutdown"); }
}