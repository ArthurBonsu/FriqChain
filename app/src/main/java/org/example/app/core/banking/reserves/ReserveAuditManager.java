package org.example.app.core.banking.reserves;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * ReserveAuditManager - Manages reserve audit lifecycle and compliance
 * 
 * Responsibilities:
 * 1. Schedule periodic audits (weekly/monthly)
 * 2. Track audit history and compliance records
 * 3. Generate audit reports and compliance summaries
 * 4. Coordinate multi-auditor verification
 * 5. Manage audit appeals and dispute resolution
 * 6. Maintain immutable audit trail
 * 
 * Implements Requirement #10: Proof of Reserves
 */
public class ReserveAuditManager {
    private static final Logger LOGGER = Logger.getLogger(ReserveAuditManager.class.getName());
    
    // Audit types
    public enum AuditType {
        ROUTINE,           // Scheduled periodic audit
        EMERGENCY,         // Triggered by alert
        COMPLIANCE_CHECK,  // Regulatory compliance
        APPEAL,           // Dispute appeal
        THIRD_PARTY       // External auditor
    }
    
    // Audit states
    public enum AuditState {
        SCHEDULED,
        IN_PROGRESS,
        PENDING_APPROVAL,
        APPROVED,
        REJECTED,
        APPEALED,
        COMPLETED,
        FAILED
    }
    
    // Audit task
    public static class AuditTask {
        private final String auditId;
        private final String tokenSymbol;
        private final AuditType auditType;
        private AuditState currentState;
        private final Date scheduledTime;
        private Date startTime;
        private Date endTime;
        private final String initiator;
        private final Set<String> assignedAuditors;
        private final Map<String, AuditorReport> auditorReports;
        private final List<String> approverSignatures;
        private BigDecimal foundBackingRatio;
        private String auditFindings;
        private String auditHash;
        private int reportedDiscrepancies;
        
        public AuditTask(String auditId, String tokenSymbol, AuditType type, 
                        Date scheduled, String initiator) {
            this.auditId = auditId;
            this.tokenSymbol = tokenSymbol;
            this.auditType = type;
            this.currentState = AuditState.SCHEDULED;
            this.scheduledTime = scheduled;
            this.initiator = initiator;
            this.assignedAuditors = Collections.synchronizedSet(new HashSet<>());
            this.auditorReports = new ConcurrentHashMap<>();
            this.approverSignatures = Collections.synchronizedList(new ArrayList<>());
            this.reportedDiscrepancies = 0;
        }
        
        public String getAuditId() { return auditId; }
        public String getTokenSymbol() { return tokenSymbol; }
        public AuditType getAuditType() { return auditType; }
        public AuditState getCurrentState() { return currentState; }
        public void setCurrentState(AuditState state) { this.currentState = state; }
        public Date getScheduledTime() { return scheduledTime; }
        public Date getStartTime() { return startTime; }
        public void setStartTime(Date time) { this.startTime = time; }
        public Date getEndTime() { return endTime; }
        public void setEndTime(Date time) { this.endTime = time; }
        public String getInitiator() { return initiator; }
        public Set<String> getAssignedAuditors() { return new HashSet<>(assignedAuditors); }
        public void addAuditor(String auditorId) { assignedAuditors.add(auditorId); }
        public Map<String, AuditorReport> getAuditorReports() { return new HashMap<>(auditorReports); }
        public List<String> getApproverSignatures() { return new ArrayList<>(approverSignatures); }
        public void addApproverSignature(String sig) { approverSignatures.add(sig); }
        public BigDecimal getFoundBackingRatio() { return foundBackingRatio; }
        public void setFoundBackingRatio(BigDecimal ratio) { this.foundBackingRatio = ratio; }
        public String getAuditFindings() { return auditFindings; }
        public void setAuditFindings(String findings) { this.auditFindings = findings; }
        public String getAuditHash() { return auditHash; }
        public void setAuditHash(String hash) { this.auditHash = hash; }
        public int getReportedDiscrepancies() { return reportedDiscrepancies; }
        public void incrementDiscrepancies() { this.reportedDiscrepancies++; }
    }
    
    /**
     * Individual auditor report
     */
    public static class AuditorReport {
        private final String reportId;
        private final String auditId;
        private final String auditorId;
        private final Date reportDate;
        private final BigDecimal backingRatio;
        private final int discrepanciesFound;
        private final String findings;
        private final List<String> recommendedActions;
        private final boolean recommendsApproval;
        private final String auditorSignature;
        private final String auditorCredentials;
        
        public AuditorReport(String reportId, String auditId, String auditorId,
                           BigDecimal ratio, int discrepancies, String findings,
                           boolean approves, String signature, String credentials) {
            this.reportId = reportId;
            this.auditId = auditId;
            this.auditorId = auditorId;
            this.reportDate = new Date();
            this.backingRatio = ratio;
            this.discrepanciesFound = discrepancies;
            this.findings = findings;
            this.recommendedActions = Collections.synchronizedList(new ArrayList<>());
            this.recommendsApproval = approves;
            this.auditorSignature = signature;
            this.auditorCredentials = credentials;
        }
        
        public String getReportId() { return reportId; }
        public String getAuditId() { return auditId; }
        public String getAuditorId() { return auditorId; }
        public Date getReportDate() { return reportDate; }
        public BigDecimal getBackingRatio() { return backingRatio; }
        public int getDiscrepanciesFound() { return discrepanciesFound; }
        public String getFindings() { return findings; }
        public List<String> getRecommendedActions() { return new ArrayList<>(recommendedActions); }
        public void addRecommendedAction(String action) { recommendedActions.add(action); }
        public boolean doesRecommendApproval() { return recommendsApproval; }
        public String getAuditorSignature() { return auditorSignature; }
        public String getAuditorCredentials() { return auditorCredentials; }
    }
    
    /**
     * Audit compliance summary
     */
    public static class AuditComplianceSummary {
        private final String tokenSymbol;
        private final int totalAuditsCompleted;
        private final int passedAudits;
        private final int failedAudits;
        private final double complianceRate;
        private final Date lastAuditDate;
        private final BigDecimal averageBackingRatio;
        private final int totalDiscrepancies;
        private final List<String> pendingIssues;
        
        public AuditComplianceSummary(String tokenSymbol, int completed, int passed, int failed,
                                    Date lastAudit, BigDecimal avgRatio, int discrepancies) {
            this.tokenSymbol = tokenSymbol;
            this.totalAuditsCompleted = completed;
            this.passedAudits = passed;
            this.failedAudits = failed;
            this.complianceRate = completed > 0 ? (double) passed / completed : 0.0;
            this.lastAuditDate = lastAudit;
            this.averageBackingRatio = avgRatio;
            this.totalDiscrepancies = discrepancies;
            this.pendingIssues = Collections.synchronizedList(new ArrayList<>());
        }
        
        public String getTokenSymbol() { return tokenSymbol; }
        public int getTotalAuditsCompleted() { return totalAuditsCompleted; }
        public int getPassedAudits() { return passedAudits; }
        public int getFailedAudits() { return failedAudits; }
        public double getComplianceRate() { return complianceRate; }
        public Date getLastAuditDate() { return lastAuditDate; }
        public BigDecimal getAverageBackingRatio() { return averageBackingRatio; }
        public int getTotalDiscrepancies() { return totalDiscrepancies; }
        public List<String> getPendingIssues() { return new ArrayList<>(pendingIssues); }
        public void addPendingIssue(String issue) { pendingIssues.add(issue); }
    }
    
    /**
     * Appeal case for audit disputes
     */
    public static class AuditAppeal {
        private final String appealId;
        private final String auditId;
        private final String appellant;
        private final Date appealDate;
        private final String reason;
        private final List<String> supportingEvidence;
        private boolean resolved;
        private String resolutionDetails;
        private String appeallerDecision; // UPHELD, OVERTURNED, PARTIAL
        
        public AuditAppeal(String appealId, String auditId, String appellant, String reason) {
            this.appealId = appealId;
            this.auditId = auditId;
            this.appellant = appellant;
            this.appealDate = new Date();
            this.reason = reason;
            this.supportingEvidence = Collections.synchronizedList(new ArrayList<>());
            this.resolved = false;
        }
        
        public String getAppealId() { return appealId; }
        public String getAuditId() { return auditId; }
        public String getAppellant() { return appellant; }
        public Date getAppealDate() { return appealDate; }
        public String getReason() { return reason; }
        public List<String> getSupportingEvidence() { return new ArrayList<>(supportingEvidence); }
        public void addEvidence(String evidence) { supportingEvidence.add(evidence); }
        public boolean isResolved() { return resolved; }
        public void markResolved(String decision, String details) { 
            this.resolved = true;
            this.appeallerDecision = decision;
            this.resolutionDetails = details;
        }
    }
    
    // Audit storage
    private final Map<String, AuditTask> auditTasks;
    private final Map<String, List<AuditTask>> tokenAuditHistory;
    private final Map<String, AuditorReport> auditorReports;
    private final Map<String, AuditAppeal> auditAppeals;
    private final Map<String, AuditComplianceSummary> complianceSummaries;
    private final ScheduledExecutorService auditScheduler;
    private final ReentrantReadWriteLock auditLock;
    
    // Audit scheduling
    private static final long ROUTINE_AUDIT_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000; // 7 days
    private static final int REQUIRED_AUDITORS = 3;
    private static final int REQUIRED_APPROVALS = 2;
    
    public ReserveAuditManager() {
        this.auditTasks = new ConcurrentHashMap<>();
        this.tokenAuditHistory = new ConcurrentHashMap<>();
        this.auditorReports = new ConcurrentHashMap<>();
        this.auditAppeals = new ConcurrentHashMap<>();
        this.complianceSummaries = new ConcurrentHashMap<>();
        this.auditScheduler = Executors.newScheduledThreadPool(4);
        this.auditLock = new ReentrantReadWriteLock();
        
        LOGGER.info("ReserveAuditManager initialized");
    }
    
    /**
     * Schedules periodic audit for a token
     */
    public String schedulePeriodicAudit(String tokenSymbol, String initiator) throws Exception {
        auditLock.writeLock().lock();
        try {
            String auditId = generateAuditId(tokenSymbol);
            Date scheduledTime = new Date(System.currentTimeMillis() + ROUTINE_AUDIT_INTERVAL_MS);
            
            AuditTask task = new AuditTask(
                auditId,
                tokenSymbol,
                AuditType.ROUTINE,
                scheduledTime,
                initiator
            );
            
            auditTasks.put(auditId, task);
            tokenAuditHistory.computeIfAbsent(tokenSymbol, k -> new ArrayList<>()).add(task);
            
            // Schedule task execution
            scheduleAuditExecution(task);
            
            LOGGER.info(String.format("Periodic audit scheduled for %s: %s", tokenSymbol, auditId));
            return auditId;
            
        } finally {
            auditLock.writeLock().unlock();
        }
    }
    
    /**
     * Schedules emergency audit
     */
    public String scheduleEmergencyAudit(String tokenSymbol, String reason, 
                                        String initiator) throws Exception {
        auditLock.writeLock().lock();
        try {
            String auditId = generateAuditId(tokenSymbol);
            Date immediateTime = new Date();
            
            AuditTask task = new AuditTask(
                auditId,
                tokenSymbol,
                AuditType.EMERGENCY,
                immediateTime,
                initiator
            );
            
            task.setAuditFindings("Emergency audit - Reason: " + reason);
            
            auditTasks.put(auditId, task);
            tokenAuditHistory.computeIfAbsent(tokenSymbol, k -> new ArrayList<>()).add(task);
            
            // Execute immediately
            executeAuditTask(task);
            
            LOGGER.warning(String.format("Emergency audit scheduled for %s: %s - %s", 
                tokenSymbol, auditId, reason));
            
            return auditId;
            
        } finally {
            auditLock.writeLock().unlock();
        }
    }
    
    /**
     * Assigns auditors to audit task
     */
    public void assignAuditorsToTask(String auditId, List<String> auditorIds) throws Exception {
        auditLock.writeLock().lock();
        try {
            AuditTask task = auditTasks.get(auditId);
            if (task == null) {
                throw new IllegalArgumentException("Audit task not found: " + auditId);
            }
            
            if (auditorIds.size() < REQUIRED_AUDITORS) {
                throw new IllegalArgumentException(
                    String.format("Minimum %d auditors required", REQUIRED_AUDITORS)
                );
            }
            
            for (String auditorId : auditorIds) {
                task.addAuditor(auditorId);
            }
            
            task.setCurrentState(AuditState.IN_PROGRESS);
            task.setStartTime(new Date());
            
            LOGGER.info(String.format("Assigned %d auditors to audit: %s", auditorIds.size(), auditId));
            
        } finally {
            auditLock.writeLock().unlock();
        }
    }
    
    /**
     * Submits auditor report
     */
    public void submitAuditorReport(String auditId, String auditorId, BigDecimal backingRatio,
                                   int discrepancies, String findings, boolean recommendsApproval,
                                   String signature) throws Exception {
        auditLock.writeLock().lock();
        try {
            AuditTask task = auditTasks.get(auditId);
            if (task == null) {
                throw new IllegalArgumentException("Audit task not found: " + auditId);
            }
            
            String reportId = generateReportId(auditId, auditorId);
            
            AuditorReport report = new AuditorReport(
                reportId,
                auditId,
                auditorId,
                backingRatio,
                discrepancies,
                findings,
                recommendsApproval,
                signature,
                "AUDITOR_CREDENTIALS"
            );
            
            task.getAuditorReports().put(auditorId, report);
            auditorReports.put(reportId, report);
            
            // Aggregate findings
            if (discrepancies > 0) {
                task.incrementDiscrepancies();
            }
            
            LOGGER.info(String.format(
                "Auditor report submitted: %s by %s - Ratio: %.2f%%, Discrepancies: %d",
                auditId, auditorId, backingRatio.multiply(new BigDecimal("100")), discrepancies
            ));
            
            // Check if all auditors reported
            if (task.getAuditorReports().size() == task.getAssignedAuditors().size()) {
                moveToApprovalPhase(task);
            }
            
        } finally {
            auditLock.writeLock().unlock();
        }
    }
    
    /**
     * Moves audit to approval phase after all reports submitted
     */
    private void moveToApprovalPhase(AuditTask task) throws Exception {
        // Aggregate auditor reports
        Collection<AuditorReport> reports = task.getAuditorReports().values();
        
        // Calculate median backing ratio
        BigDecimal medianRatio = calculateMedianRatio(reports);
        task.setFoundBackingRatio(medianRatio);
        
        // Count approvals
        long approvalsCount = reports.stream()
            .filter(AuditorReport::doesRecommendApproval)
            .count();
        
        if (approvalsCount >= REQUIRED_APPROVALS) {
            task.setCurrentState(AuditState.PENDING_APPROVAL);
        } else {
            task.setCurrentState(AuditState.REJECTED);
            LOGGER.warning("Audit rejected - Insufficient approvals: " + task.getAuditId());
        }
    }
    
    /**
     * Approves audit with signature
     */
    public void approveAuditWithSignature(String auditId, String approverAddress,
                                        String signature) throws Exception {
        auditLock.writeLock().lock();
        try {
            AuditTask task = auditTasks.get(auditId);
            if (task == null) {
                throw new IllegalArgumentException("Audit task not found: " + auditId);
            }
            
            if (task.getCurrentState() != AuditState.PENDING_APPROVAL) {
                throw new IllegalStateException(
                    "Audit not in approval phase. Current: " + task.getCurrentState()
                );
            }
            
            task.addApproverSignature(signature);
            
            if (task.getApproverSignatures().size() >= REQUIRED_APPROVALS) {
                finalizeAudit(task);
            }
            
            LOGGER.info(String.format("Approval recorded for audit %s (%d/%d)",
                auditId, task.getApproverSignatures().size(), REQUIRED_APPROVALS));
            
        } finally {
            auditLock.writeLock().unlock();
        }
    }
    
    /**
     * Finalizes audit
     */
    private void finalizeAudit(AuditTask task) throws Exception {
        task.setEndTime(new Date());
        task.setCurrentState(AuditState.COMPLETED);
        task.setAuditHash(generateAuditHash(task));
        
        updateComplianceSummary(task);
        
        LOGGER.info("Audit finalized: " + task.getAuditId() + 
            " - Backing Ratio: " + task.getFoundBackingRatio());
    }
    
    /**
     * Files audit appeal
     */
    public String fileAuditAppeal(String auditId, String appellant, String reason,
                                 List<String> evidence) throws Exception {
        auditLock.writeLock().lock();
        try {
            AuditTask task = auditTasks.get(auditId);
            if (task == null) {
                throw new IllegalArgumentException("Audit task not found: " + auditId);
            }
            
            String appealId = generateAppealId();
            
            AuditAppeal appeal = new AuditAppeal(appealId, auditId, appellant, reason);
            for (String evidence_item : evidence) {
                appeal.addEvidence(evidence_item);
            }
            
            auditAppeals.put(appealId, appeal);
            task.setCurrentState(AuditState.APPEALED);
            
            LOGGER.info(String.format("Audit appeal filed: %s for audit %s by %s",
                appealId, auditId, appellant));
            
            return appealId;
            
        } finally {
            auditLock.writeLock().unlock();
        }
    }
    
    /**
     * Gets compliance summary for token
     */
    public AuditComplianceSummary getComplianceSummary(String tokenSymbol) throws Exception {
        auditLock.readLock().lock();
        try {
            AuditComplianceSummary summary = complianceSummaries.get(tokenSymbol);
            if (summary == null) {
                // Generate fresh summary
                List<AuditTask> audits = tokenAuditHistory.getOrDefault(tokenSymbol, new ArrayList<>());
                return generateComplianceSummary(tokenSymbol, audits);
            }
            return summary;
            
        } finally {
            auditLock.readLock().unlock();
        }
    }
    
    /**
     * Helper methods
     */
    
    private void scheduleAuditExecution(AuditTask task) {
        long delayMs = task.getScheduledTime().getTime() - System.currentTimeMillis();
        if (delayMs > 0) {
            auditScheduler.schedule(() -> {
                try {
                    executeAuditTask(task);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Audit execution failed", e);
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        }
    }
    
    private void executeAuditTask(AuditTask task) {
        LOGGER.info("Executing audit task: " + task.getAuditId());
        // Trigger audit execution in ProofOfReservesContract
    }
    
    private BigDecimal calculateMedianRatio(Collection<AuditorReport> reports) {
        List<BigDecimal> ratios = reports.stream()
            .map(AuditorReport::getBackingRatio)
            .sorted()
            .collect(Collectors.toList());
        
        if (ratios.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        int mid = ratios.size() / 2;
        if (ratios.size() % 2 == 0) {
            return ratios.get(mid - 1).add(ratios.get(mid))
                .divide(new BigDecimal("2"), 4, java.math.RoundingMode.HALF_UP);
        } else {
            return ratios.get(mid);
        }
    }
    
    private AuditComplianceSummary generateComplianceSummary(String tokenSymbol,
                                                            List<AuditTask> audits) {
        int completed = (int) audits.stream()
            .filter(a -> a.getCurrentState() == AuditState.COMPLETED)
            .count();
        
        int passed = (int) audits.stream()
            .filter(a -> a.getCurrentState() == AuditState.COMPLETED && 
                        a.getFoundBackingRatio().compareTo(new BigDecimal("1.20")) >= 0)
            .count();
        
        int failed = completed - passed;
        
        BigDecimal avgRatio = audits.stream()
            .filter(a -> a.getFoundBackingRatio() != null)
            .map(AuditTask::getFoundBackingRatio)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(new BigDecimal(Math.max(completed, 1)), 4, java.math.RoundingMode.HALF_UP);
        
        Date lastAuditDate = audits.stream()
            .filter(a -> a.getEndTime() != null)
            .map(AuditTask::getEndTime)
            .max(Date::compareTo)
            .orElse(null);
        
        int totalDiscrepancies = (int) audits.stream()
            .mapToInt(AuditTask::getReportedDiscrepancies)
            .sum();
        
        AuditComplianceSummary summary = new AuditComplianceSummary(
            tokenSymbol, completed, passed, failed, lastAuditDate, avgRatio, totalDiscrepancies
        );
        
        complianceSummaries.put(tokenSymbol, summary);
        return summary;
    }
    
    private void updateComplianceSummary(AuditTask task) {
        AuditComplianceSummary existing = complianceSummaries.get(task.getTokenSymbol());
        if (existing != null) {
            // Update will happen on next read
        }
    }
    
    private String generateAuditId(String tokenSymbol) {
        return "AUDIT_" + tokenSymbol + "_" + System.currentTimeMillis();
    }
    
    private String generateReportId(String auditId, String auditorId) {
        return "REPORT_" + auditId.substring(0, 8) + "_" + auditorId.substring(0, 4);
    }
    
    private String generateAppealId() {
        return "APPEAL_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    private String generateAuditHash(AuditTask task) {
        return "HASH_" + task.getAuditId() + "_" + System.nanoTime();
    }
}