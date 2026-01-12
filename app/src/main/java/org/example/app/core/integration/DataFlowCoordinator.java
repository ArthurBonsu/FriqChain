package org.example.app.core.integration;

import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * DataFlowCoordinator manages data flow between banking, blockchain,
 * oracles, and scaling components.
 */
public class DataFlowCoordinator {
    private static final Logger logger = Logger.getLogger(DataFlowCoordinator.class.getName());
    
    private final OracleValidatorIntegrator oracleIntegrator;
    private final CrossSystemEventBus eventBus;
    private final BlockingQueue<DataFlowTask> taskQueue;
    private final ExecutorService executor;
    
    public DataFlowCoordinator(OracleValidatorIntegrator oracleIntegrator,
                              CrossSystemEventBus eventBus) {
        this.oracleIntegrator = oracleIntegrator;
        this.eventBus = eventBus;
        this.taskQueue = new LinkedBlockingQueue<>();
        this.executor = Executors.newFixedThreadPool(5);
        logger.info("DataFlowCoordinator initialized");
    }
    
    public void submitTask(DataFlowTask task) {
        taskQueue.offer(task);
    }
    
    public void start() {
        for (int i = 0; i < 5; i++) {
            executor.submit(this::processTasks);
        }
    }
    
    private void processTasks() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                DataFlowTask task = taskQueue.poll(1, TimeUnit.SECONDS);
                if (task != null) {
                    task.execute();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    public void shutdown() {
        executor.shutdown();
    }
    
    public interface DataFlowTask {
        void execute();
    }
}
