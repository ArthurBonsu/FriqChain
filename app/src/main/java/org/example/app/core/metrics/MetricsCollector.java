package org.example.app.core.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Production-ready metrics collection for monitoring
 */
public class MetricsCollector {
    private static final Logger LOGGER = Logger.getLogger(MetricsCollector.class.getName());
    
    private static MetricsCollector instance;
    private final MeterRegistry registry;
    private final Map<String, Timer.Sample> activeTimers;
    
    private MetricsCollector() {
        this.registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        this.activeTimers = new ConcurrentHashMap<>();
        
        // Register JVM metrics
        new ClassLoaderMetrics().bindTo(registry);
        new JvmMemoryMetrics().bindTo(registry);
        new JvmGcMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
    }
    
    public static MetricsCollector getInstance() {
        if (instance == null) {
            synchronized (MetricsCollector.class) {
                if (instance == null) {
                    instance = new MetricsCollector();
                }
            }
        }
        return instance;
    }
    
    public void recordEvent(String name) {
        recordEvent(name, "");
    }
    
    public void recordEvent(String name, String value) {
        Counter.builder(name)
            .tag("value", value)
            .register(registry)
            .increment();
    }
    
    public void recordCounter(String name, double value) {
        registry.counter(name).increment(value);
    }
    
    public void recordGauge(String name, double value) {
        Gauge.builder(name, () -> value)
            .register(registry);
    }
    
    public void recordSuccess(String operation) {
        recordEvent(operation + ".success");
    }
    
    public void recordError(String operation, Exception error) {
        Counter.builder(operation + ".error")
            .tag("exception", error.getClass().getSimpleName())
            .register(registry)
            .increment();
    }
    
    public void startTimer(String name, String id) {
        Timer.Sample sample = Timer.start(registry);
        activeTimers.put(name + ":" + id, sample);
    }
    
    public void stopTimer(String name, String id) {
        Timer.Sample sample = activeTimers.remove(name + ":" + id);
        if (sample != null) {
            sample.stop(Timer.builder(name).register(registry));
        }
    }
    
    public String getMetricsEndpoint() {
        if (registry instanceof PrometheusMeterRegistry) {
            return ((PrometheusMeterRegistry) registry).scrape();
        }
        return "";
    }
}