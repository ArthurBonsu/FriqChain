package org.example.app.core.metrics;

import org.example.app.core.block.Block;
import org.example.app.core.block.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BaseBlockchainMetricsTest {

    private BlockchainMetrics metrics;
    private Block mockBlock;

    @BeforeEach
    void setUp() {
        metrics = new BlockchainMetrics();
        mockBlock = mock(Block.class);

        List<Transaction> transactions = new ArrayList<>();
        transactions.add(mock(Transaction.class));
        transactions.add(mock(Transaction.class));

        when(mockBlock.getTransactions()).thenReturn(transactions);
    }

    @Test
    @DisplayName("Constructor should initialize metrics to zero")
    void testConstructor() {
        assertEquals(0, metrics.getTotalBlocks());
        assertEquals(0, metrics.getTotalTransactions());
        assertEquals(0, metrics.getAverageBlockTime());
    }

    @Test
    @DisplayName("recordNewBlock should increment block count")
    void testRecordNewBlockIncrementsBlockCount() {
        metrics.recordNewBlock(mockBlock);
        assertEquals(1, metrics.getTotalBlocks());

        metrics.recordNewBlock(mockBlock);
        assertEquals(2, metrics.getTotalBlocks());
    }

    @Test
    @DisplayName("recordNewBlock should add transaction count")
    void testRecordNewBlockAddsTransactionCount() {
        metrics.recordNewBlock(mockBlock);
        assertEquals(2, metrics.getTotalTransactions());

        metrics.recordNewBlock(mockBlock);
        assertEquals(4, metrics.getTotalTransactions());
    }

    @Test
    @DisplayName("recordNewBlock should update average block time")
    void testRecordNewBlockUpdatesAverageBlockTime() throws InterruptedException {
        metrics.recordNewBlock(mockBlock); // First call sets lastBlockTimestamp
        Thread.sleep(50); // Ensure time difference
        metrics.recordNewBlock(mockBlock); // Second call adds to totalBlockTime

        long average = metrics.getAverageBlockTime();
        assertTrue(average > 0, "Average block time should be > 0 after two blocks");
    }

    @Test
    @DisplayName("recordNewBlock should handle blocks with empty transactions")
    void testRecordNewBlockWithEmptyTransactions() {
        Block emptyBlock = mock(Block.class);
        when(emptyBlock.getTransactions()).thenReturn(new ArrayList<>());

        metrics.recordNewBlock(emptyBlock);

        assertEquals(1, metrics.getTotalBlocks());
        assertEquals(0, metrics.getTotalTransactions());
    }

    @Test
    @DisplayName("recordNewBlock should handle blocks with null transactions")
    void testRecordNewBlockWithNullTransactions() {
        Block nullTxBlock = mock(Block.class);
        when(nullTxBlock.getTransactions()).thenReturn(null);

        assertDoesNotThrow(() -> metrics.recordNewBlock(nullTxBlock));

        assertEquals(1, metrics.getTotalBlocks());
        assertEquals(0, metrics.getTotalTransactions());
    }

    @Test
    @DisplayName("reset should reset all metrics to zero")
    void testReset() throws InterruptedException {
        metrics.recordNewBlock(mockBlock);
        Thread.sleep(10);
        metrics.recordNewBlock(mockBlock);

        assertTrue(metrics.getTotalBlocks() > 0);
        assertTrue(metrics.getTotalTransactions() > 0);
        assertTrue(metrics.getAverageBlockTime() > 0);

        metrics.reset();

        assertEquals(0, metrics.getTotalBlocks());
        assertEquals(0, metrics.getTotalTransactions());
        assertEquals(0, metrics.getAverageBlockTime());
    }

    @Test
    @DisplayName("getters should return current metric values")
    void testGetters() {
        metrics.recordNewBlock(mockBlock);

        assertEquals(1, metrics.getTotalBlocks());
        assertEquals(2, metrics.getTotalTransactions());
        assertTrue(metrics.getAverageBlockTime() >= 0);
    }

    @Test
    @DisplayName("metrics should be thread-safe")
    void testThreadSafety() throws InterruptedException {
        final int threadCount = 10;
        final int blocksPerThread = 100;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < blocksPerThread; j++) {
                    metrics.recordNewBlock(mockBlock);
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(threadCount * blocksPerThread, metrics.getTotalBlocks());
        assertEquals(threadCount * blocksPerThread * 2, metrics.getTotalTransactions());
    }
}
