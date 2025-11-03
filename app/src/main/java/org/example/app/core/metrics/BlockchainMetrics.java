package org.example.app.core.metrics;

import org.example.app.core.block.Block;
import org.example.app.core.block.Transaction;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class BlockchainMetrics {

    private final AtomicInteger totalBlocks = new AtomicInteger(0);
    private final AtomicInteger totalTransactions = new AtomicInteger(0);
    private final AtomicLong totalBlockTime = new AtomicLong(0);
    private final AtomicLong lastBlockTimestamp = new AtomicLong(-1);

    public void recordNewBlock(Block block) {
        totalBlocks.incrementAndGet();

        List<Transaction> transactions = null;
        if (block != null) {
            try {
                transactions = block.getTransactions();
            } catch (Exception ignored) {}
        }

        if (transactions != null) {
            totalTransactions.addAndGet(transactions.size());
        }

        long now = System.currentTimeMillis();
        long lastTime = lastBlockTimestamp.getAndSet(now);
        if (lastTime > 0) {
            totalBlockTime.addAndGet(now - lastTime);
        }
    }

    public int getTotalBlocks() {
        return totalBlocks.get();
    }

    public int getTotalTransactions() {
        return totalTransactions.get();
    }

    public long getAverageBlockTime() {
        int blocks = totalBlocks.get();
        return (blocks <= 1) ? 0 : totalBlockTime.get() / (blocks - 1);
    }

    public void reset() {
        totalBlocks.set(0);
        totalTransactions.set(0);
        totalBlockTime.set(0);
        lastBlockTimestamp.set(-1);
    }
}
