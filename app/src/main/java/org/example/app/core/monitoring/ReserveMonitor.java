package org.example.app.core.monitoring;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * ReserveMonitor tracks reserve audits and treasury balances.
 */
public class ReserveMonitor {
    private static final Logger logger = Logger.getLogger(ReserveMonitor.class.getName());
    
    private final AtomicLong totalReserves;
    private final AtomicLong auditsCompleted;
    private final AtomicLong treasuryBalance;
    
    public ReserveMonitor() {
        this.totalReserves = new AtomicLong(0);
        this.auditsCompleted = new AtomicLong(0);
        this.treasuryBalance = new AtomicLong(0);
        logger.info("ReserveMonitor initialized");
    }
    
    public void recordReserveUpdate(long amount) {
        totalReserves.set(amount);
    }
    
    public void recordAuditCompletion() {
        auditsCompleted.incrementAndGet();
    }
    
    public void recordTreasuryUpdate(long balance) {
        treasuryBalance.set(balance);
    }
    
    public ReserveStats getStats() {
        return new ReserveStats(
            totalReserves.get(),
            auditsCompleted.get(),
            treasuryBalance.get()
        );
    }
    
    public static class ReserveStats {
        private final long totalReserves;
        private final long auditsCompleted;
        private final long treasuryBalance;
        
        public ReserveStats(long reserves, long audits, long treasury) {
            this.totalReserves = reserves;
            this.auditsCompleted = audits;
            this.treasuryBalance = treasury;
        }
        
        public long getTotalReserves() { return totalReserves; }
        public long getAuditsCompleted() { return auditsCompleted; }
        public long getTreasuryBalance() { return treasuryBalance; }
    }
}
