package org.example.app.core.monitoring;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * AlertingEngine monitors metrics and generates alerts for threshold violations.
 */
public class AlertingEngine {
    private static final Logger logger = Logger.getLogger(AlertingEngine.class.getName());
    
    private final MetricsAggregator aggregator;
    private final ConcurrentLinkedQueue<Alert> activeAlerts;
    private final Map<String, AlertRule> rules;
    
    public AlertingEngine(MetricsAggregator aggregator) {
        this.aggregator = aggregator;
        this.activeAlerts = new ConcurrentLinkedQueue<>();
        this.rules = new ConcurrentHashMap<>();
        initializeDefaultRules();
        logger.info("AlertingEngine initialized");
    }
    
    private void initializeDefaultRules() {
        rules.put("LOW_TPS", new AlertRule("TPS below 50,000", snapshot -> 
            snapshot.getScalingStats().getCurrentTPS() < 50000));
        rules.put("HIGH_CLOUD_DEPENDENCY", new AlertRule("Cloud dependency above 20%", snapshot ->
            snapshot.getSovereigntyStats().getCloudDependency() > 0.20));
        rules.put("LOW_COMMUNITY_OWNERSHIP", new AlertRule("Community ownership below 50%", snapshot ->
            snapshot.getSovereigntyStats().getCommunityOwnership() < 0.50));
        rules.put("HIGH_LATENCY", new AlertRule("Network latency above 500ms", snapshot ->
            snapshot.getNetworkStats().getAverageLatency() > 500));
    }
    
    public void checkAlerts() {
        MetricsAggregator.AggregatedSnapshot snapshot = aggregator.getLatestSnapshot();
        if (snapshot == null) return;
        
        for (Map.Entry<String, AlertRule> entry : rules.entrySet()) {
            if (entry.getValue().condition.test(snapshot)) {
                Alert alert = new Alert(entry.getKey(), entry.getValue().description);
                activeAlerts.offer(alert);
                logger.warning("ALERT: " + alert.getDescription());
            }
        }
        
        while (activeAlerts.size() > 100) {
            activeAlerts.poll();
        }
    }
    
    public List<Alert> getActiveAlerts() {
        return new ArrayList<>(activeAlerts);
    }
    
    public static class AlertRule {
        private final String description;
        private final java.util.function.Predicate<MetricsAggregator.AggregatedSnapshot> condition;
        
        AlertRule(String description, java.util.function.Predicate<MetricsAggregator.AggregatedSnapshot> condition) {
            this.description = description;
            this.condition = condition;
        }
    }
    
    public static class Alert {
        private final String type;
        private final String description;
        private final long timestamp;
        
        Alert(String type, String description) {
            this.type = type;
            this.description = description;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getType() { return type; }
        public String getDescription() { return description; }
        public long getTimestamp() { return timestamp; }
    }
}
