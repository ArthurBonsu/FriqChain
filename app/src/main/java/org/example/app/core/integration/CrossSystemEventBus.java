package org.example.app.core.integration;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * CrossSystemEventBus enables event-driven communication between
 * banking, blockchain, scaling, and oracle modules.
 */
public class CrossSystemEventBus {
    private static final Logger logger = Logger.getLogger(CrossSystemEventBus.class.getName());
    
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<Event>>> listeners;
    private final ExecutorService eventExecutor;
    
    public CrossSystemEventBus() {
        this.listeners = new ConcurrentHashMap<>();
        this.eventExecutor = Executors.newFixedThreadPool(10);
        logger.info("CrossSystemEventBus initialized");
    }
    
    public void subscribe(String eventType, Consumer<Event> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
    }
    
    public void publish(Event event) {
        CopyOnWriteArrayList<Consumer<Event>> eventListeners = listeners.get(event.getType());
        if (eventListeners != null) {
            for (Consumer<Event> listener : eventListeners) {
                eventExecutor.submit(() -> listener.accept(event));
            }
        }
    }
    
    public void shutdown() {
        eventExecutor.shutdown();
    }
    
    public static class Event {
        private final String type;
        private final Map<String, Object> data;
        private final long timestamp;
        
        public Event(String type, Map<String, Object> data) {
            this.type = type;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getType() { return type; }
        public Map<String, Object> getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }
}
