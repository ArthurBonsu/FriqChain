package org.example.app.core.monitoring;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class GovernanceMonitor {
    private static final Logger logger = Logger.getLogger(GovernanceMonitor.class.getName());
    
    private final AtomicLong activeProposals;
    private final AtomicLong completedVotes;
    private final AtomicLong totalParticipants;
    
    public GovernanceMonitor() {
        this.activeProposals = new AtomicLong(0);
        this.completedVotes = new AtomicLong(0);
        this.totalParticipants = new AtomicLong(0);
        logger.info("GovernanceMonitor initialized");
    }
    
    public void recordProposal() { activeProposals.incrementAndGet(); }
    public void recordVote() { completedVotes.incrementAndGet(); }
    public void recordParticipant() { totalParticipants.incrementAndGet(); }
    
    public GovernanceStats getStats() {
        return new GovernanceStats(
            activeProposals.get(),
            completedVotes.get(),
            totalParticipants.get()
        );
    }
    
    public static class GovernanceStats {
        private final long activeProposals;
        private final long completedVotes;
        private final long totalParticipants;
        
        public GovernanceStats(long proposals, long votes, long participants) {
            this.activeProposals = proposals;
            this.completedVotes = votes;
            this.totalParticipants = participants;
        }
        
        public long getActiveProposals() { return activeProposals; }
        public long getCompletedVotes() { return completedVotes; }
        public long getTotalParticipants() { return totalParticipants; }
    }
}
