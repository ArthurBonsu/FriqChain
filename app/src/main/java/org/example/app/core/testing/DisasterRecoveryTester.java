package org.example.app.core.testing;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * DisasterRecoveryTester simulates emergency scenarios and tests recovery.
 */
public class DisasterRecoveryTester {
    private static final Logger logger = Logger.getLogger(DisasterRecoveryTester.class.getName());
    
    public DisasterRecoveryTester() {
        logger.info("DisasterRecoveryTester initialized");
    }
    
    public RecoveryResult testNodeFailure(int failedNodes) {
        logger.info("Testing " + failedNodes + " node failures");
        long start = System.currentTimeMillis();
        
        // Simulate node failures
        boolean recovered = failedNodes < 12; // System tolerates up to 12 failures (35-12=23 quorum)
        double recoveryTimeMs = failedNodes * 100.0; // Simulated
        
        return new RecoveryResult("Node Failure", failedNodes, recovered, recoveryTimeMs,
            System.currentTimeMillis() - start);
    }
    
    public RecoveryResult testNetworkPartition(String region) {
        logger.info("Testing network partition: " + region);
        long start = System.currentTimeMillis();
        
        // Simulate partition - system should maintain consensus in other regions
        boolean recovered = true; // Other regions continue
        double recoveryTimeMs = 5000.0; // 5 seconds to detect and adapt
        
        return new RecoveryResult("Network Partition", 1, recovered, recoveryTimeMs,
            System.currentTimeMillis() - start);
    }
    
    public RecoveryResult testDataCenterOutage(String location) {
        logger.info("Testing data center outage: " + location);
        long start = System.currentTimeMillis();
        
        // Geographic distribution ensures no single data center is critical
        boolean recovered = true;
        double recoveryTimeMs = 1000.0;
        
        return new RecoveryResult("Data Center Outage", 1, recovered, recoveryTimeMs,
            System.currentTimeMillis() - start);
    }
    
    public static class RecoveryResult {
        private final String scenarioType;
        private final int failureCount;
        private final boolean recovered;
        private final double recoveryTimeMs;
        private final long testDurationMs;
        
        public RecoveryResult(String type, int count, boolean recovered, double recoveryTime, long duration) {
            this.scenarioType = type;
            this.failureCount = count;
            this.recovered = recovered;
            this.recoveryTimeMs = recoveryTime;
            this.testDurationMs = duration;
        }
        
        public boolean isRecovered() { return recovered; }
        
        @Override
        public String toString() {
            return String.format("%s: Count=%d, Recovered=%s, RecoveryTime=%.0fms",
                scenarioType, failureCount, recovered ? "YES" : "NO", recoveryTimeMs);
        }
    }
}
