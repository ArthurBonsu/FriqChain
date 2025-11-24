package org.example.app.core.banking.compliance;

import org.example.app.core.types.Address;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * ChallengeWindowManager - Manages appeal windows and enforcement deadlines
 * 
 * Timeline:
 * - Evidence Phase: Unlimited (until moveToArbitration)
 * - Challenge Window: 7 days (to challenge evidence or verdict)
 * - Appeal Hearing: Additional 14 days (if challenge filed)
 * - Final Challenge: 7 days (for appeal verdict)
 * - Grace Period: 168 hours before enforcement (7 days)
 * 
 * Implements automatic closure and enforcement triggering
 */

public class ChallengeWindowManager {
    private static final Logger LOGGER = Logger.getLogger(ChallengeWindowManager.class.getName());
    
    private final Map<String, ChallengeWindow> windows; // Dispute ID -> ChallengeWindow
    private final Map<String, Long> challengeWindowDeadlines; // Dispute ID -> deadline timestamp
    private final Map<String, List<Challenge>> challenges; // Dispute ID -> list of challenges
    private final ScheduledExecutorService scheduler;
    private final DisputeResolutionContract disputeContract;
    
    // Time constants (in milliseconds)
    private static final long INITIAL_CHALLENGE_WINDOW = 7 * 24 * 60 * 60 * 1000; // 7 days
    private static final long APPEAL_HEARING_DURATION = 14 * 24 * 60 * 60 * 1000; // 14 days
    private static final long FINAL_APPEAL_WINDOW = 7 * 24 * 60 * 60 * 1000; // 7 days
    private static final long ENFORCEMENT_GRACE_PERIOD = 7 * 24 * 60 * 60 * 1000; // 7 days
    
    public enum ChallengePhase {
        INITIAL_CHALLENGE,      // 7 days to challenge verdict or evidence
        APPEAL_HEARING,         // 14 days for arbitration on appeal
        FINAL_APPEAL_CHALLENGE, // 7 days to challenge appeal verdict
        ENFORCEMENT_GRACE,      // 7 days before enforcement begins
        CLOSED
    }
    
    public static class ChallengeWindow {
        private final String disputeId;
        private final long createdTime;
        private ChallengePhase currentPhase;
        private long phaseStartTime;
        private long phaseEndTime;
        private final List<String> challengeIds;
        private final Map<String, Address> challengeFilers;
        private int escalationCount; // Number of times escalated to appeal
        private boolean enforcementScheduled;
        private long enforcementTime;
        
        public ChallengeWindow(String disputeId) {
            this.disputeId = disputeId;
            this.createdTime = System.currentTimeMillis();
            this.currentPhase = ChallengePhase.INITIAL_CHALLENGE;
            this.phaseStartTime = createdTime;
            this.phaseEndTime = createdTime + INITIAL_CHALLENGE_WINDOW;
            this.challengeIds = new ArrayList<>();
            this.challengeFilers = new ConcurrentHashMap<>();
            this.escalationCount = 0;
            this.enforcementScheduled = false;
            this.enforcementTime = 0;
        }
        
        // Getters and setters
        public String getDisputeId() { return disputeId; }
        public ChallengePhase getCurrentPhase() { return currentPhase; }
        public long getPhaseEndTime() { return phaseEndTime; }
        public List<String> getChallengeIds() { return new ArrayList<>(challengeIds); }
        public int getEscalationCount() { return escalationCount; }
        public boolean isEnforcementScheduled() { return enforcementScheduled; }
        public long getEnforcementTime() { return enforcementTime; }
        
        public void addChallenge(String challengeId, Address filer) {
            challengeIds.add(challengeId);
            challengeFilers.put(challengeId, filer);
        }
        
        public void escalatePhase() {
            switch (currentPhase) {
                case INITIAL_CHALLENGE:
                    currentPhase = ChallengePhase.APPEAL_HEARING;
                    phaseStartTime = System.currentTimeMillis();
                    phaseEndTime = phaseStartTime + APPEAL_HEARING_DURATION;
                    escalationCount++;
                    break;
                case APPEAL_HEARING:
                    currentPhase = ChallengePhase.FINAL_APPEAL_CHALLENGE;
                    phaseStartTime = System.currentTimeMillis();
                    phaseEndTime = phaseStartTime + FINAL_APPEAL_WINDOW;
                    escalationCount++;
                    break;
                case FINAL_APPEAL_CHALLENGE:
                    currentPhase = ChallengePhase.ENFORCEMENT_GRACE;
                    phaseStartTime = System.currentTimeMillis();
                    phaseEndTime = phaseStartTime + ENFORCEMENT_GRACE_PERIOD;
                    break;
                case ENFORCEMENT_GRACE:
                    currentPhase = ChallengePhase.CLOSED;
                    enforcementScheduled = true;
                    enforcementTime = System.currentTimeMillis();
                    break;
            }
        }
        
        public long getTimeRemainingInPhase() {
            return Math.max(0, phaseEndTime - System.currentTimeMillis());
        }
        
        public boolean isPhaseExpired() {
            return System.currentTimeMillis() >= phaseEndTime;
        }
    }
    
    public static class Challenge {
        private final String challengeId;
        private final String disputeId;
        private final Address challenger;
        private final String reason;
        private final long filedTime;
        private final byte[] evidence;
        private boolean appealed;
        private String appealVerdict;
        
        public Challenge(String challengeId, String disputeId, Address challenger,
                        String reason, byte[] evidence) {
            this.challengeId = challengeId;
            this.disputeId = disputeId;
            this.challenger = challenger;
            this.reason = reason;
            this.evidence = evidence != null ? evidence.clone() : null;
            this.filedTime = System.currentTimeMillis();
            this.appealed = false;
        }
        
        // Getters
        public String getChallengeId() { return challengeId; }
        public String getDisputeId() { return disputeId; }
        public Address getChallenger() { return challenger; }
        public String getReason() { return reason; }
        public long getFiledTime() { return filedTime; }
        public byte[] getEvidence() { return evidence != null ? evidence.clone() : null; }
        public boolean isAppealed() { return appealed; }
        public String getAppealVerdict() { return appealVerdict; }
        
        public void markAppealed(String verdict) {
            this.appealed = true;
            this.appealVerdict = verdict;
        }
    }
    
    public ChallengeWindowManager(DisputeResolutionContract disputeContract) {
        this.windows = new ConcurrentHashMap<>();
        this.challengeWindowDeadlines = new ConcurrentHashMap<>();
        this.challenges = new ConcurrentHashMap<>();
        this.disputeContract = disputeContract;
        this.scheduler = Executors.newScheduledThreadPool(4);
        
        // Start periodic checks for expired windows
        startWindowExpirationMonitor();
        LOGGER.info("ChallengeWindowManager initialized");
    }
    
    /**
     * Creates a new challenge window for a dispute
     */
    public String createChallengeWindow(String disputeId) {
        ChallengeWindow window = new ChallengeWindow(disputeId);
        windows.put(disputeId, window);
        challengeWindowDeadlines.put(disputeId, window.getPhaseEndTime());
        challenges.put(disputeId, new ArrayList<>());
        
        // Schedule automatic closure
        scheduleWindowClosure(disputeId, window.getPhaseEndTime());
        
        LOGGER.info("Challenge window created for dispute: " + disputeId);
        return disputeId;
    }
    
    /**
     * Files a challenge within the active window
     */
    public String fileChallenge(String disputeId, Address challenger, String reason, byte[] evidence) {
        ChallengeWindow window = windows.get(disputeId);
        if (window == null) {
            LOGGER.warning("Challenge window not found for dispute: " + disputeId);
            return null;
        }
        
        // Verify window is still open
        if (window.isPhaseExpired()) {
            LOGGER.warning("Challenge window expired for dispute: " + disputeId);
            return null;
        }
        
        String challengeId = UUID.randomUUID().toString();
        Challenge challenge = new Challenge(challengeId, disputeId, challenger, reason, evidence);
        
        window.addChallenge(challengeId, challenger);
        challenges.get(disputeId).add(challenge);
        
        LOGGER.info("Challenge filed: " + challengeId + " for dispute: " + disputeId +
                   " by " + challenger.toString());
        return challengeId;
    }
    
    /**
     * Gets time remaining in current phase
     */
    public long getTimeRemaining(String disputeId) {
        ChallengeWindow window = windows.get(disputeId);
        if (window == null) {
            return 0;
        }
        return window.getTimeRemainingInPhase();
    }
    
    /**
     * Gets current phase
     */
    public ChallengePhase getCurrentPhase(String disputeId) {
        ChallengeWindow window = windows.get(disputeId);
        if (window == null) {
            return null;
        }
        return window.getCurrentPhase();
    }
    
    /**
     * Closes current phase and escalates to next (if challenges filed)
     */
    public void escalateToChallengeWindow(String disputeId) {
        ChallengeWindow window = windows.get(disputeId);
        if (window == null) {
            return;
        }
        
        // Check if any challenges were filed
        List<Challenge> disputeChallenges = challenges.get(disputeId);
        if (disputeChallenges != null && !disputeChallenges.isEmpty()) {
            // Escalate to appeal
            window.escalatePhase();
            long newDeadline = window.getPhaseEndTime();
            challengeWindowDeadlines.put(disputeId, newDeadline);
            scheduleWindowClosure(disputeId, newDeadline);
            
            LOGGER.info("Dispute escalated to appeal: " + disputeId + " (escalation #" + window.getEscalationCount() + ")");
        } else {
            // No challenges, move to enforcement
            window.escalatePhase();
            window.escalatePhase(); // Skip final appeal if no challenge
            scheduleEnforcement(disputeId, window.getEnforcementTime());
            
            LOGGER.info("No challenges filed, scheduling enforcement for: " + disputeId);
        }
    }
    
    /**
     * Closes a phase automatically after deadline
     */
    private void scheduleWindowClosure(String disputeId, long deadline) {
        long delayMs = deadline - System.currentTimeMillis();
        if (delayMs > 0) {
            scheduler.schedule(() -> {
                ChallengeWindow window = windows.get(disputeId);
                if (window != null && !window.isPhaseExpired()) {
                    escalateToChallengeWindow(disputeId);
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        }
    }
    
    /**
     * Schedules enforcement after grace period
     */
    private void scheduleEnforcement(String disputeId, long enforcementTime) {
        long delayMs = enforcementTime + ENFORCEMENT_GRACE_PERIOD - System.currentTimeMillis();
        if (delayMs > 0) {
            scheduler.schedule(() -> {
                DisputeResolutionContract.Dispute dispute = disputeContract.getDispute(disputeId);
                if (dispute != null) {
                    disputeContract.enforceVerdict(disputeId);
                    LOGGER.info("Enforcement triggered for dispute: " + disputeId);
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        }
    }
    
    /**
     * Monitors for expired windows and triggers actions
     */
    private void startWindowExpirationMonitor() {
        scheduler.scheduleAtFixedRate(() -> {
            for (Map.Entry<String, ChallengeWindow> entry : windows.entrySet()) {
                String disputeId = entry.getKey();
                ChallengeWindow window = entry.getValue();
                
                if (!window.isPhaseExpired() || window.getCurrentPhase() == ChallengePhase.CLOSED) {
                    continue;
                }
                
                // Window expired, escalate
                escalateToChallengeWindow(disputeId);
            }
        }, 60, 60, TimeUnit.SECONDS); // Check every minute
    }
    
    /**
     * Gets all challenges for a dispute
     */
    public List<Challenge> getChallenges(String disputeId) {
        return new ArrayList<>(challenges.getOrDefault(disputeId, new ArrayList<>()));
    }
    
    /**
     * Gets challenge window details
     */
    public ChallengeWindow getWindow(String disputeId) {
        return windows.get(disputeId);
    }
    
    /**
     * Cancels a challenge window (for dismissed disputes)
     */
    public void cancelWindow(String disputeId) {
        ChallengeWindow window = windows.get(disputeId);
        if (window != null) {
            window.currentPhase = ChallengePhase.CLOSED;
            LOGGER.info("Challenge window cancelled for dispute: " + disputeId);
        }
    }
    
    /**
     * Shutdown the manager
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}