package org.example.app.core.scaling;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * BATCH PROCESSOR
 * 
 * Efficiently processes large volumes of transactions through batching:
 * - Groups transactions for optimized L1 settlement
 * - Parallel processing across multiple workers
 * - Priority queues for urgent transactions
 * - Automatic batch optimization for gas efficiency
 * 
 * Target Performance:
 * - Process 5,000 TPS per shard
 * - Batch size optimization (100-1000 txs)
 * - <100ms batch creation time
 * - Parallel validation across cores
 */
public class BatchProcessor {
    private static final Logger LOGGER = Logger.getLogger(BatchProcessor.class.getName());
    
    // Configuration
    private static final int MIN_BATCH_SIZE = 100;
    private static final int MAX_BATCH_SIZE = 1000;
    private static final int OPTIMAL_BATCH_SIZE = 500;
    private static final long BATCH_TIMEOUT_MS = 2000; // 2 seconds
    private static final int MAX_PENDING_BATCHES = 100;
    
    // Transaction Queues (Priority-based)
    private final BlockingQueue<PrioritizedTransaction> highPriorityQueue;
    private final BlockingQueue<PrioritizedTransaction> normalPriorityQueue;
    private final BlockingQueue<PrioritizedTransaction> lowPriorityQueue;
    
    // Batch Management
    private final Map<Long, TransactionBatch> pendingBatches;
    private final Map<Long, TransactionBatch> completedBatches;
    private final ReadWriteLock batchLock;
    private final AtomicLong batchIdCounter;
    
    // Worker Threads
    private final ExecutorService batchWorkers;
    private final ExecutorService validationWorkers;
    private final ScheduledExecutorService batchScheduler;
    
    // Metrics
    private final AtomicLong transactionsProcessed;
    private final AtomicLong batchesCreated;
    private final AtomicLong batchesCompleted;
    private final AtomicLong validationFailures;
    
    // Batch Statistics
    private final ConcurrentHashMap<BatchStatus, AtomicLong> statusCounts;
    
    // Shutdown flag
    private volatile boolean shutdown;
    
    public enum TransactionPriority {
        HIGH,    // Time-sensitive, pay premium
        NORMAL,  // Standard transactions
        LOW      // Non-urgent, lower fees
    }
    
    public enum BatchStatus {
        PENDING,     // Collecting transactions
        VALIDATING,  // Running validation
        READY,       // Ready for settlement
        COMPLETED,   // Successfully settled
        FAILED       // Validation or settlement failed
    }
    
    /**
     * Constructor
     */
    public BatchProcessor() {
        // Initialize priority queues
        this.highPriorityQueue = new LinkedBlockingQueue<>(10000);
        this.normalPriorityQueue = new LinkedBlockingQueue<>(50000);
        this.lowPriorityQueue = new LinkedBlockingQueue<>(100000);
        
        // Initialize batch tracking
        this.pendingBatches = new ConcurrentHashMap<>();
        this.completedBatches = new ConcurrentHashMap<>();
        this.batchLock = new ReentrantReadWriteLock();
        this.batchIdCounter = new AtomicLong(0);
        
        // Initialize thread pools
        int cores = Runtime.getRuntime().availableProcessors();
        this.batchWorkers = Executors.newFixedThreadPool(
            Math.max(2, cores / 2),
            r -> new Thread(r, "Batch-Worker")
        );
        this.validationWorkers = Executors.newFixedThreadPool(
            Math.max(4, cores),
            r -> new Thread(r, "Validation-Worker")
        );
        this.batchScheduler = Executors.newScheduledThreadPool(1,
            r -> new Thread(r, "Batch-Scheduler"));
        
        // Initialize metrics
        this.transactionsProcessed = new AtomicLong(0);
        this.batchesCreated = new AtomicLong(0);
        this.batchesCompleted = new AtomicLong(0);
        this.validationFailures = new AtomicLong(0);
        
        this.statusCounts = new ConcurrentHashMap<>();
        for (BatchStatus status : BatchStatus.values()) {
            statusCounts.put(status, new AtomicLong(0));
        }
        
        this.shutdown = false;
        
        // Start batch creation scheduler
        startBatchScheduler();
        
        LOGGER.info(String.format(
            "Batch Processor initialized: batch_workers=%d, validation_workers=%d",
            Math.max(2, cores / 2), Math.max(4, cores)
        ));
    }
    
    /**
     * Submit transaction for batching
     */
    public void submitTransaction(Transaction transaction, TransactionPriority priority)
            throws Exception {
        
        if (shutdown) {
            throw new IllegalStateException("Batch processor is shut down");
        }
        
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }
        
        // Wrap in prioritized container
        PrioritizedTransaction prioritizedTx = new PrioritizedTransaction(
            transaction,
            priority,
            System.currentTimeMillis()
        );
        
        // Add to appropriate queue
        boolean added = switch (priority) {
            case HIGH -> highPriorityQueue.offer(prioritizedTx, 1, TimeUnit.SECONDS);
            case NORMAL -> normalPriorityQueue.offer(prioritizedTx, 2, TimeUnit.SECONDS);
            case LOW -> lowPriorityQueue.offer(prioritizedTx, 5, TimeUnit.SECONDS);
        };
        
        if (!added) {
            throw new Exception("Failed to queue transaction - system overloaded");
        }
        
        LOGGER.fine(String.format(
            "Transaction queued: id=%s, priority=%s",
            transaction.getId(), priority
        ));
    }
    
    /**
     * Create batch from queued transactions
     */
    public TransactionBatch createBatch(int maxSize) {
        if (shutdown) {
            return null;
        }
        
        long batchId = batchIdCounter.incrementAndGet();
        int targetSize = Math.min(maxSize, OPTIMAL_BATCH_SIZE);
        
        List<Transaction> transactions = new ArrayList<>(targetSize);
        
        // Collect transactions from priority queues
        collectFromQueue(highPriorityQueue, transactions, targetSize);
        
        if (transactions.size() < targetSize) {
            collectFromQueue(normalPriorityQueue, transactions, 
                targetSize - transactions.size());
        }
        
        if (transactions.size() < targetSize) {
            collectFromQueue(lowPriorityQueue, transactions,
                targetSize - transactions.size());
        }
        
        if (transactions.isEmpty()) {
            return null; // No transactions available
        }
        
        // Create batch
        TransactionBatch batch = new TransactionBatch(
            batchId,
            transactions,
            System.currentTimeMillis()
        );
        
        batchLock.writeLock().lock();
        try {
            pendingBatches.put(batchId, batch);
        } finally {
            batchLock.writeLock().unlock();
        }
        
        batchesCreated.incrementAndGet();
        statusCounts.get(BatchStatus.PENDING).incrementAndGet();
        
        // Submit for validation
        submitForValidation(batch);
        
        LOGGER.info(String.format(
            "Batch created: id=%d, size=%d transactions",
            batchId, transactions.size()
        ));
        
        return batch;
    }
    
    /**
     * Collect transactions from queue
     */
    private void collectFromQueue(BlockingQueue<PrioritizedTransaction> queue,
                                  List<Transaction> transactions, int maxCount) {
        List<PrioritizedTransaction> collected = new ArrayList<>();
        queue.drainTo(collected, maxCount);
        
        for (PrioritizedTransaction ptx : collected) {
            transactions.add(ptx.getTransaction());
            transactionsProcessed.incrementAndGet();
        }
    }
    
    /**
     * Submit batch for parallel validation
     */
    private void submitForValidation(TransactionBatch batch) {
        batch.setStatus(BatchStatus.VALIDATING);
        statusCounts.get(BatchStatus.VALIDATING).incrementAndGet();
        
        validationWorkers.submit(() -> {
            try {
                validateBatch(batch);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Batch validation failed: " + batch.getBatchId(), e);
                batch.setStatus(BatchStatus.FAILED);
                batch.setError(e.getMessage());
                validationFailures.incrementAndGet();
                statusCounts.get(BatchStatus.FAILED).incrementAndGet();
            }
        });
    }
    
    /**
     * Validate batch transactions in parallel
     */
    private void validateBatch(TransactionBatch batch) throws Exception {
        List<Transaction> transactions = batch.getTransactions();
        int chunkSize = Math.max(1, transactions.size() / 
            Runtime.getRuntime().availableProcessors());
        
        // Split into chunks for parallel validation
        List<CompletableFuture<Boolean>> validationFutures = new ArrayList<>();
        
        for (int i = 0; i < transactions.size(); i += chunkSize) {
            int endIndex = Math.min(i + chunkSize, transactions.size());
            List<Transaction> chunk = transactions.subList(i, endIndex);
            
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                return validateChunk(chunk);
            }, validationWorkers);
            
            validationFutures.add(future);
        }
        
        // Wait for all validations
        CompletableFuture<Void> allValidations = CompletableFuture.allOf(
            validationFutures.toArray(new CompletableFuture[0])
        );
        
        allValidations.get(30, TimeUnit.SECONDS);
        
        // Check results
        for (CompletableFuture<Boolean> future : validationFutures) {
            if (!future.get()) {
                throw new Exception("Batch contains invalid transactions");
            }
        }
        
        // Mark as ready
        batch.setStatus(BatchStatus.READY);
        statusCounts.get(BatchStatus.READY).incrementAndGet();
        
        LOGGER.info(String.format(
            "Batch validated: id=%d, %d transactions",
            batch.getBatchId(), transactions.size()
        ));
    }
    
    /**
     * Validate chunk of transactions
     */
    private boolean validateChunk(List<Transaction> chunk) {
        for (Transaction tx : chunk) {
            if (!validateTransaction(tx)) {
                LOGGER.warning("Invalid transaction: " + tx.getId());
                return false;
            }
        }
        return true;
    }
    
    /**
     * Validate individual transaction
     */
    private boolean validateTransaction(Transaction tx) {
        // Basic validation checks
        if (tx.getId() == null || tx.getId().isEmpty()) {
            return false;
        }
        
        if (tx.getSender() == null || tx.getSender().isEmpty()) {
            return false;
        }
        
        if (tx.getAmount() < 0) {
            return false;
        }
        
        // Signature verification
        // Balance checks
        // Gas validation
        // State consistency
        
        return true;
    }
    
    /**
     * Mark batch as completed
     */
    public void completeBatch(long batchId) {
        batchLock.writeLock().lock();
        try {
            TransactionBatch batch = pendingBatches.remove(batchId);
            if (batch != null) {
                batch.setStatus(BatchStatus.COMPLETED);
                batch.setCompletedAt(System.currentTimeMillis());
                completedBatches.put(batchId, batch);
                
                batchesCompleted.incrementAndGet();
                statusCounts.get(BatchStatus.COMPLETED).incrementAndGet();
                
                LOGGER.info("Batch completed: " + batchId);
            }
        } finally {
            batchLock.writeLock().unlock();
        }
    }
    
    /**
     * Get batch by ID
     */
    public TransactionBatch getBatch(long batchId) {
        batchLock.readLock().lock();
        try {
            TransactionBatch batch = pendingBatches.get(batchId);
            if (batch == null) {
                batch = completedBatches.get(batchId);
            }
            return batch;
        } finally {
            batchLock.readLock().unlock();
        }
    }
    
    /**
     * Get all ready batches for settlement
     */
    public List<TransactionBatch> getReadyBatches() {
        List<TransactionBatch> ready = new ArrayList<>();
        
        batchLock.readLock().lock();
        try {
            for (TransactionBatch batch : pendingBatches.values()) {
                if (batch.getStatus() == BatchStatus.READY) {
                    ready.add(batch);
                }
            }
        } finally {
            batchLock.readLock().unlock();
        }
        
        return ready;
    }
    
    /**
     * Start batch creation scheduler
     */
    private void startBatchScheduler() {
        batchScheduler.scheduleAtFixedRate(() -> {
            try {
                if (!shutdown) {
                    // Create batch if enough transactions queued
                    int totalPending = highPriorityQueue.size() + 
                                      normalPriorityQueue.size() + 
                                      lowPriorityQueue.size();
                    
                    if (totalPending >= MIN_BATCH_SIZE) {
                        createBatch(MAX_BATCH_SIZE);
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Batch scheduler error", e);
            }
        }, BATCH_TIMEOUT_MS, BATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Get processor statistics
     */
    public ProcessorStatistics getStatistics() {
        Map<BatchStatus, Long> statusMap = new HashMap<>();
        for (Map.Entry<BatchStatus, AtomicLong> entry : statusCounts.entrySet()) {
            statusMap.put(entry.getKey(), entry.getValue().get());
        }
        
        return new ProcessorStatistics(
            transactionsProcessed.get(),
            batchesCreated.get(),
            batchesCompleted.get(),
            validationFailures.get(),
            highPriorityQueue.size(),
            normalPriorityQueue.size(),
            lowPriorityQueue.size(),
            pendingBatches.size(),
            statusMap
        );
    }
    
    /**
     * Shutdown processor
     */
    public void shutdown() {
        shutdown = true;
        
        batchScheduler.shutdown();
        batchWorkers.shutdown();
        validationWorkers.shutdown();
        
        try {
            if (!batchScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                batchScheduler.shutdownNow();
            }
            if (!batchWorkers.awaitTermination(10, TimeUnit.SECONDS)) {
                batchWorkers.shutdownNow();
            }
            if (!validationWorkers.awaitTermination(10, TimeUnit.SECONDS)) {
                validationWorkers.shutdownNow();
            }
        } catch (InterruptedException e) {
            batchScheduler.shutdownNow();
            batchWorkers.shutdownNow();
            validationWorkers.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        LOGGER.info("Batch Processor shut down");
    }
    
    // Inner classes
    
    public static class Transaction {
        private final String id;
        private final String sender;
        private final String recipient;
        private final long amount;
        private final long timestamp;
        
        public Transaction(String id, String sender, String recipient, 
                          long amount, long timestamp) {
            this.id = id;
            this.sender = sender;
            this.recipient = recipient;
            this.amount = amount;
            this.timestamp = timestamp;
        }
        
        public String getId() { return id; }
        public String getSender() { return sender; }
        public String getRecipient() { return recipient; }
        public long getAmount() { return amount; }
        public long getTimestamp() { return timestamp; }
    }
    
    private static class PrioritizedTransaction {
        private final Transaction transaction;
        private final TransactionPriority priority;
        private final long queuedAt;
        
        public PrioritizedTransaction(Transaction transaction, TransactionPriority priority,
                                     long queuedAt) {
            this.transaction = transaction;
            this.priority = priority;
            this.queuedAt = queuedAt;
        }
        
        public Transaction getTransaction() { return transaction; }
        public TransactionPriority getPriority() { return priority; }
        public long getQueuedAt() { return queuedAt; }
    }
    
    public static class TransactionBatch {
        private final long batchId;
        private final List<Transaction> transactions;
        private final long createdAt;
        private BatchStatus status;
        private String error;
        private long completedAt;
        
        public TransactionBatch(long batchId, List<Transaction> transactions, long createdAt) {
            this.batchId = batchId;
            this.transactions = new ArrayList<>(transactions);
            this.createdAt = createdAt;
            this.status = BatchStatus.PENDING;
        }
        
        public long getBatchId() { return batchId; }
        public List<Transaction> getTransactions() { return transactions; }
        public int getSize() { return transactions.size(); }
        public long getCreatedAt() { return createdAt; }
        public BatchStatus getStatus() { return status; }
        public void setStatus(BatchStatus status) { this.status = status; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public long getCompletedAt() { return completedAt; }
        public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }
    }
    
    public static class ProcessorStatistics {
        private final long transactionsProcessed;
        private final long batchesCreated;
        private final long batchesCompleted;
        private final long validationFailures;
        private final int highPriorityQueued;
        private final int normalPriorityQueued;
        private final int lowPriorityQueued;
        private final int pendingBatches;
        private final Map<BatchStatus, Long> statusCounts;
        
        public ProcessorStatistics(long transactionsProcessed, long batchesCreated,
                                  long batchesCompleted, long validationFailures,
                                  int highPriorityQueued, int normalPriorityQueued,
                                  int lowPriorityQueued, int pendingBatches,
                                  Map<BatchStatus, Long> statusCounts) {
            this.transactionsProcessed = transactionsProcessed;
            this.batchesCreated = batchesCreated;
            this.batchesCompleted = batchesCompleted;
            this.validationFailures = validationFailures;
            this.highPriorityQueued = highPriorityQueued;
            this.normalPriorityQueued = normalPriorityQueued;
            this.lowPriorityQueued = lowPriorityQueued;
            this.pendingBatches = pendingBatches;
            this.statusCounts = statusCounts;
        }
        
        public long getTransactionsProcessed() { return transactionsProcessed; }
        public long getBatchesCreated() { return batchesCreated; }
        public long getBatchesCompleted() { return batchesCompleted; }
        public long getValidationFailures() { return validationFailures; }
        public int getHighPriorityQueued() { return highPriorityQueued; }
        public int getNormalPriorityQueued() { return normalPriorityQueued; }
        public int getLowPriorityQueued() { return lowPriorityQueued; }
        public int getTotalQueued() { 
            return highPriorityQueued + normalPriorityQueued + lowPriorityQueued;
        }
        public int getPendingBatches() { return pendingBatches; }
        public Map<BatchStatus, Long> getStatusCounts() { return statusCounts; }
        
        @Override
        public String toString() {
            return String.format(
                "ProcessorStats[txs=%d, batches=%d/%d, queued=%d, pending=%d]",
                transactionsProcessed, batchesCompleted, batchesCreated,
                getTotalQueued(), pendingBatches
            );
        }
    }
}