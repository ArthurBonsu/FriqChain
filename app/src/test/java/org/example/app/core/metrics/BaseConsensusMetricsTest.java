package org.example.app.core.metrics;

import org.example.app.core.block.Block;
import org.example.app.core.block.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BaseConsensusMetricsTest {

    private ConsensusMetrics consensusMetrics;
    private Block block;

    @BeforeEach
    public void setup() {
        consensusMetrics = new ConsensusMetrics();
        block = mock(Block.class);
    }

    @Test
    public void testRecordNewBlockIncrementsMetrics() {
        // Mock transactions for the block using org.example.app.core.block.Transaction
        Transaction tx1 = mock(Transaction.class);
        Transaction tx2 = mock(Transaction.class);
        when(block.getTransactions()).thenReturn(Arrays.asList(tx1, tx2));

        consensusMetrics.recordNewBlock(block, "validator1");

        assertEquals(1, consensusMetrics.getBlockHeight());
        assertEquals(2, consensusMetrics.getTotalTransactions());

        Map<String, Long> validatorMetrics = consensusMetrics.getValidatorMetrics();
        assertTrue(validatorMetrics.containsKey("validator1"));
        assertEquals(1L, validatorMetrics.get("validator1"));
    }

    @Test
    public void testAverageBlockTimeIsUpdated() throws InterruptedException {
        when(block.getTransactions()).thenReturn(Collections.singletonList(mock(Transaction.class)));

        consensusMetrics.recordNewBlock(block, "validator1");
        Thread.sleep(10); // simulate time delay
        consensusMetrics.recordNewBlock(block, "validator1");

        assertTrue(consensusMetrics.getAverageBlockTime() > 0);
    }

    @Test
    public void testResetClearsMetrics() {
        when(block.getTransactions()).thenReturn(Collections.singletonList(mock(Transaction.class)));

        consensusMetrics.recordNewBlock(block, "validator1");
        consensusMetrics.reset();

        assertEquals(0, consensusMetrics.getBlockHeight());
        assertEquals(0, consensusMetrics.getTotalTransactions());
        assertEquals(0, consensusMetrics.getAverageBlockTime());
        assertTrue(consensusMetrics.getValidatorMetrics().isEmpty());
    }

    @Test
    public void testConsensusFailureLogging() {
        consensusMetrics.recordConsensusFailure("network partition");
        // Just verify it doesn't throw exceptions; real logging would be tested with a logging framework
    }
}
