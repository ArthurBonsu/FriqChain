package org.example.app.core.ledger;

/** Consensus-critical limits of one shard. Every validator of the shard must use identical values. */
public final class LedgerConfig {
    private final int maxTransactionsPerBlock;
    private final long maxBlockGas;
    private final long maxClockDriftMillis;
    private final long genesisTimestamp;

    public LedgerConfig(int maxTransactionsPerBlock, long maxBlockGas, long maxClockDriftMillis, long genesisTimestamp) {
        if (maxTransactionsPerBlock <= 0 || maxBlockGas <= 0 || maxClockDriftMillis < 0) {
            throw new IllegalArgumentException("Invalid ledger limits");
        }
        this.maxTransactionsPerBlock = maxTransactionsPerBlock;
        this.maxBlockGas = maxBlockGas;
        this.maxClockDriftMillis = maxClockDriftMillis;
        this.genesisTimestamp = genesisTimestamp;
    }

    public static LedgerConfig defaults(long genesisTimestamp) {
        return new LedgerConfig(1_000, 30_000_000L, 15_000L, genesisTimestamp);
    }

    public int getMaxTransactionsPerBlock() { return maxTransactionsPerBlock; }
    public long getMaxBlockGas() { return maxBlockGas; }
    public long getMaxClockDriftMillis() { return maxClockDriftMillis; }
    public long getGenesisTimestamp() { return genesisTimestamp; }
}
