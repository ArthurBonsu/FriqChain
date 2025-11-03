package org.example.app.core.audit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Production-ready audit logger for configuration changes
 */
public class AuditLogger {
    private static final Logger LOGGER = Logger.getLogger(AuditLogger.class.getName());
    
    private final String component;
    private final Path auditLogPath;
    private final BlockingQueue<AuditEvent> eventQueue;
    private final ExecutorService executorService;
    private final Gson gson;
    private volatile boolean running = true;
    
    public static class AuditEvent {
        public final String id = UUID.randomUUID().toString();
        public final String component;
        public final String action;
        public final String userId;
        public final Map<String, Object> details;
        public final Instant timestamp = Instant.now();
        public final String severity;
        
        public AuditEvent(String component, String action, String userId, 
                         Map<String, Object> details, String severity) {
            this.component = component;
            this.action = action;
            this.userId = userId;
            this.details = details;
            this.severity = severity;
        }
    }
    
    public AuditLogger(String component) {
        this.component = component;
        this.auditLogPath = Paths.get("logs/audit", 
            component.toLowerCase() + "_audit.log");
        this.eventQueue = new LinkedBlockingQueue<>();
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("AuditLogger-" + component);
            return t;
        });
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            .create();
        
        initialize();
    }
    
    private void initialize() {
        try {
            Files.createDirectories(auditLogPath.getParent());
            
            // Start event processor
            executorService.submit(this::processEvents);
            
            LOGGER.info("Audit logger initialized for: " + component);
        } catch (IOException e) {
            LOGGER.severe("Failed to initialize audit logger: " + e.getMessage());
        }
    }
    
    private void processEvents() {
        while (running) {
            try {
                AuditEvent event = eventQueue.poll(1, TimeUnit.SECONDS);
                if (event != null) {
                    writeEvent(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.severe("Error processing audit event: " + e.getMessage());
            }
        }
    }
    
    private void writeEvent(AuditEvent event) {
        try {
            String logEntry = gson.toJson(event) + System.lineSeparator();
            
            Files.write(auditLogPath, logEntry.getBytes(),
                       StandardOpenOption.CREATE,
                       StandardOpenOption.APPEND,
                       StandardOpenOption.WRITE);
                       
            // Also write to system log
            LOGGER.info(String.format("AUDIT [%s] %s - %s by %s",
                event.severity, event.component, event.action, event.userId));
                
        } catch (IOException e) {
            LOGGER.severe("Failed to write audit event: " + e.getMessage());
        }
    }
    
    public void logConfigurationChangeStart(String transactionId, Map<String, Object> changes) {
        Map<String, Object> details = new HashMap<>();
        details.put("transactionId", transactionId);
        details.put("changes", changes);
        details.put("changeCount", changes.size());
        
        eventQueue.offer(new AuditEvent(
            component, "CONFIG_CHANGE_START", getCurrentUser(),
            details, "INFO"
        ));
    }
    
    public void logConfigurationChangeSuccess(String transactionId, Map<String, Object> applied) {
        Map<String, Object> details = new HashMap<>();
        details.put("transactionId", transactionId);
        details.put("appliedChanges", applied);
        
        eventQueue.offer(new AuditEvent(
            component, "CONFIG_CHANGE_SUCCESS", getCurrentUser(),
            details, "INFO"
        ));
    }
    
    public void logConfigurationChangeFailure(String transactionId, Exception error) {
        Map<String, Object> details = new HashMap<>();
        details.put("transactionId", transactionId);
        details.put("error", error.getMessage());
        details.put("errorType", error.getClass().getName());
        
        eventQueue.offer(new AuditEvent(
            component, "CONFIG_CHANGE_FAILURE", getCurrentUser(),
            details, "ERROR"
        ));
    }
    
    public void logConfigurationSave(String filepath, String userId) {
        Map<String, Object> details = new HashMap<>();
        details.put("filepath", filepath);
        
        eventQueue.offer(new AuditEvent(
            component, "CONFIG_SAVE", userId,
            details, "INFO"
        ));
    }
    
    public void logConfigurationLoad(String filepath, String userId) {
        Map<String, Object> details = new HashMap<>();
        details.put("filepath", filepath);
        
        eventQueue.offer(new AuditEvent(
            component, "CONFIG_LOAD", userId,
            details, "INFO"
        ));
    }
    
    public void logConfigurationRollback(Map<String, Object> rollback) {
        Map<String, Object> details = new HashMap<>();
        details.put("rollbackChanges", rollback);
        
        eventQueue.offer(new AuditEvent(
            component, "CONFIG_ROLLBACK", getCurrentUser(),
            details, "WARNING"
        ));
    }
    
    public void logSnapshotRestore(String snapshotId, String description) {
        Map<String, Object> details = new HashMap<>();
        details.put("snapshotId", snapshotId);
        details.put("description", description);
        
        eventQueue.offer(new AuditEvent(
            component, "SNAPSHOT_RESTORE", getCurrentUser(),
            details, "WARNING"
        ));
    }
    
    public void logConfigurationReset(String userId) {
        eventQueue.offer(new AuditEvent(
            component, "CONFIG_RESET", userId,
            new HashMap<>(), "WARNING"
        ));
    }
    
    public void logProfileLoad(String profileName, String userId) {
        Map<String, Object> details = new HashMap<>();
        details.put("profileName", profileName);
        
        eventQueue.offer(new AuditEvent(
            component, "PROFILE_LOAD", userId,
            details, "INFO"
        ));
    }
    
    public void logEmergencyMode(String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put("reason", reason);
        
        eventQueue.offer(new AuditEvent(
            component, "EMERGENCY_MODE", "system",
            details, "CRITICAL"
        ));
    }
    
    private String getCurrentUser() {
        // In production, this would get actual user from authentication context
        return System.getProperty("user.name", "unknown");
    }
    
    public void shutdown() {
        running = false;
        executorService.shutdown();
        
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            
            // Flush remaining events
            while (!eventQueue.isEmpty()) {
                AuditEvent event = eventQueue.poll();
                if (event != null) {
                    writeEvent(event);
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}