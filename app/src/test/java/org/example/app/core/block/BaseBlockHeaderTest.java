package org.example.app.core.block;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class BaseBlockHeaderTest {

    private BlockHeader blockHeader;
    private String parentHash;
    private String miner;
    private long timestamp;
    private long difficulty;
    private long number;
    private String[] transactions;

    @BeforeEach
    void setUp() {
        parentHash = "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        miner = "0x0123456789abcdef0123456789abcdef01234567";
        timestamp = System.currentTimeMillis();
        difficulty = 1000L;
        number = 12345L;
        transactions = new String[]{"0xtx1", "0xtx2", "0xtx3"};

        blockHeader = new BlockHeader(
                parentHash,
                miner,
                timestamp,
                difficulty,
                number,
                transactions
        );
    }

    @Test
    @DisplayName("Constructor should initialize BlockHeader with correct values")
    void testConstructor() {
        assertEquals(parentHash, blockHeader.getParentHash());
        assertEquals(miner, blockHeader.getMiner());
        assertEquals(timestamp, blockHeader.getTimestamp());
        assertEquals(difficulty, blockHeader.getDifficulty());
        assertEquals(number, blockHeader.getNumber());
        assertArrayEquals(transactions, blockHeader.getTransactions());
    }

    @Test
    @DisplayName("getParentHash should return the correct parent hash")
    void testGetParentHash() {
        assertEquals(parentHash, blockHeader.getParentHash());
    }

    @Test
    @DisplayName("setParentHash should update the parent hash")
    void testSetParentHash() {
        String newParentHash = "0xnewhash1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        blockHeader.setParentHash(newParentHash);
        assertEquals(newParentHash, blockHeader.getParentHash());
    }

    @Test
    @DisplayName("getMiner should return the correct miner address")
    void testGetMiner() {
        assertEquals(miner, blockHeader.getMiner());
    }

    @Test
    @DisplayName("setMiner should update the miner address")
    void testSetMiner() {
        String newMiner = "0xnewminer89abcdef0123456789abcdef01234567";
        blockHeader.setMiner(newMiner);
        assertEquals(newMiner, blockHeader.getMiner());
    }

    @Test
    @DisplayName("getTimestamp should return the correct timestamp")
    void testGetTimestamp() {
        assertEquals(timestamp, blockHeader.getTimestamp());
    }

    @Test
    @DisplayName("setTimestamp should update the timestamp")
    void testSetTimestamp() {
        long newTimestamp = timestamp + 3600;
        blockHeader.setTimestamp(newTimestamp);
        assertEquals(newTimestamp, blockHeader.getTimestamp());
    }

    @Test
    @DisplayName("getDifficulty should return the correct difficulty")
    void testGetDifficulty() {
        assertEquals(difficulty, blockHeader.getDifficulty());
    }

    @Test
    @DisplayName("setDifficulty should update the difficulty")
    void testSetDifficulty() {
        long newDifficulty = 2000L;
        blockHeader.setDifficulty(newDifficulty);
        assertEquals(newDifficulty, blockHeader.getDifficulty());
    }

    @Test
    @DisplayName("getNumber should return the correct block number")
    void testGetNumber() {
        assertEquals(number, blockHeader.getNumber());
    }

    @Test
    @DisplayName("setNumber should update the block number")
    void testSetNumber() {
        long newNumber = 54321L;
        blockHeader.setNumber(newNumber);
        assertEquals(newNumber, blockHeader.getNumber());
    }

    @Test
    @DisplayName("getTransactions should return the correct transactions array")
    void testGetTransactions() {
        assertArrayEquals(transactions, blockHeader.getTransactions());
    }

    @Test
    @DisplayName("setTransactions should update the transactions array")
    void testSetTransactions() {
        String[] newTransactions = new String[]{"0xnewtx1", "0xnewtx2", "0xnewtx3", "0xnewtx4"};
        blockHeader.setTransactions(newTransactions);
        assertArrayEquals(newTransactions, blockHeader.getTransactions());
    }

    @Test
    @DisplayName("toString should return a properly formatted string representation")
    void testToString() {
        String result = blockHeader.toString();

        // Verify the string contains all the key fields
        assertTrue(result.contains(parentHash));
        assertTrue(result.contains(miner));
        assertTrue(result.contains(String.valueOf(timestamp)));
        assertTrue(result.contains(String.valueOf(difficulty)));
        assertTrue(result.contains(String.valueOf(number)));

        // Verify all transactions are included
        for (String tx : transactions) {
            assertTrue(result.contains(tx));
        }
    }

    @Test
    @DisplayName("BlockHeader should handle empty transactions array")
    void testEmptyTransactions() {
        String[] emptyTransactions = new String[0];
        BlockHeader emptyTxHeader = new BlockHeader(
                parentHash,
                miner,
                timestamp,
                difficulty,
                number,
                emptyTransactions
        );

        assertNotNull(emptyTxHeader.getTransactions());
        assertEquals(0, emptyTxHeader.getTransactions().length);

        // Verify toString doesn't throw exceptions with empty array
        String result = emptyTxHeader.toString();
        assertTrue(result.contains("transactions="));
    }

    @Test
    @DisplayName("BlockHeader should handle null transactions array")
    void testNullTransactions() {
        BlockHeader nullTxHeader = new BlockHeader(
                parentHash,
                miner,
                timestamp,
                difficulty,
                number,
                null
        );

        assertNull(nullTxHeader.getTransactions());

        // The toString method might throw NullPointerException because it tries to join the array
        // Let's verify that by catching the exception
        assertThrows(NullPointerException.class, nullTxHeader::toString);
    }

    @Test
    @DisplayName("Test array modification behavior")
    void testArrayModification() {
        // Get the transactions array from the BlockHeader
        String[] retrievedTransactions = blockHeader.getTransactions();

        // Store the original value
        String originalValue = retrievedTransactions[0];

        // Modify the retrieved array
        retrievedTransactions[0] = "0xmodified";

        // Verify the actual behavior (current implementation allows modification)
        String[] currentTransactions = blockHeader.getTransactions();
        assertEquals("0xmodified", currentTransactions[0]);

        // Note: In an ideal implementation, the class would use defensive copying
        // to prevent external modification, but this test documents the actual behavior
    }
}