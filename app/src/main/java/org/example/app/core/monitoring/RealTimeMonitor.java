package org.example.app.core.monitoring;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * RealTimeMonitor streams live metrics updates to connected dashboards.
 */
public class RealTimeMonitor {
    private static final Logger logger = Logger.getLogger(RealTimeMonitor.class.getName());
    
    private final MetricsAggregator aggregator;
    private final CopyOnWriteArrayList<Consumer<MetricsAggregator.AggregatedSnapshot>> subscribers;
    private final ScheduledExecutorService scheduler;
    
    public RealTimeMonitor(MetricsAggregator aggregator) {
        this.aggregator = aggregator;
        this.subscribers = new CopyOnWriteArrayList<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
        logger.info("RealTimeMonitor initialized");
    }
    
    public void start() {
        scheduler.scheduleAtFixedRate(() -> {
            MetricsAggregator.AggregatedSnapshot snapshot = aggregator.getLatestSnapshot();
            if (snapshot != null) {
                notifySubscribers(snapshot);
            }
        }, 0, 5, TimeUnit.SECONDS);
    }
    
    public void subscribe(Consumer<MetricsAggregator.AggregatedSnapshot> subscriber) {
        subscribers.add(subscriber);
    }
    
    private void notifySubscribers(MetricsAggregator.AggregatedSnapshot snapshot) {
        for (Consumer<MetricsAggregator.AggregatedSnapshot> subscriber : subscribers) {
            try {
                subscriber.accept(snapshot);
            } catch (Exception e) {
                logger.warning("Error notifying subscriber: " + e.getMessage());
            }
        }
    }
    
    public void shutdown() {
        scheduler.shutdown();
    }
}
