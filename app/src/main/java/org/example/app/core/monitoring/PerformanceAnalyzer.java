package org.example.app.core.monitoring;

import java.util.*;
import java.util.logging.Logger;

/**
 * PerformanceAnalyzer analyzes trends and identifies performance issues.
 */
public class PerformanceAnalyzer {
    private static final Logger logger = Logger.getLogger(PerformanceAnalyzer.class.getName());
    
    private final MetricsAggregator aggregator;
    
    public PerformanceAnalyzer(MetricsAggregator aggregator) {
        this.aggregator = aggregator;
        logger.info("PerformanceAnalyzer initialized");
    }
    
    public PerformanceTrend analyzeTPS(int durationMinutes) {
        List<MetricsAggregator.TimeSeriesPoint> timeSeries = aggregator.getTimeSeries("tps", durationMinutes);
        
        if (timeSeries.isEmpty()) {
            return new PerformanceTrend("TPS", 0, 0, 0, TrendDirection.STABLE);
        }
        
        double avg = timeSeries.stream().mapToDouble(MetricsAggregator.TimeSeriesPoint::getValue).average().orElse(0);
        double max = timeSeries.stream().mapToDouble(MetricsAggregator.TimeSeriesPoint::getValue).max().orElse(0);
        double min = timeSeries.stream().mapToDouble(MetricsAggregator.TimeSeriesPoint::getValue).min().orElse(0);
        
        TrendDirection direction = determineTrend(timeSeries);
        
        return new PerformanceTrend("TPS", avg, max, min, direction);
    }
    
    private TrendDirection determineTrend(List<MetricsAggregator.TimeSeriesPoint> timeSeries) {
        if (timeSeries.size() < 2) return TrendDirection.STABLE;
        
        double first = timeSeries.get(0).getValue();
        double last = timeSeries.get(timeSeries.size() - 1).getValue();
        
        double change = (last - first) / first;
        
        if (change > 0.10) return TrendDirection.IMPROVING;
        if (change < -0.10) return TrendDirection.DECLINING;
        return TrendDirection.STABLE;
    }
    
    public enum TrendDirection { IMPROVING, STABLE, DECLINING }
    
    public static class PerformanceTrend {
        private final String metric;
        private final double average;
        private final double max;
        private final double min;
        private final TrendDirection direction;
        
        public PerformanceTrend(String metric, double avg, double max, double min, TrendDirection direction) {
            this.metric = metric;
            this.average = avg;
            this.max = max;
            this.min = min;
            this.direction = direction;
        }
        
        public String getMetric() { return metric; }
        public double getAverage() { return average; }
        public double getMax() { return max; }
        public double getMin() { return min; }
        public TrendDirection getDirection() { return direction; }
    }
}
