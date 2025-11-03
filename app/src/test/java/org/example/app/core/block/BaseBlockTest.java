package org.example.app.core.block;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.example.app.core.block.Transaction;

import static org.junit.jupiter.api.Assertions.*;

public class BaseBlockTest {

    private Block block;
    private String hash;
    private String parentHash;
    private String miner;
    private long timestamp;
    private long difficulty;
    private long number;
    private List<Transaction> transactions;
    private byte[] stateRoot;

    @BeforeEach
    void setUp() {
        hash = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
        parentHash = "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        miner = "0x0123456789abcdef0123456789abcdef01234567";
        timestamp = System.currentTimeMillis();
        difficulty = 1000L;
        number = 12345L;

        // Create a list of test transactions
        transactions = new ArrayList<>();
        transactions.add(new Transaction(new byte[]{1, 2, 3}));
        transactions.add(new Transaction(new byte[]{4, 5, 6}));

        block = new Block(hash, parentHash, miner, timestamp, difficulty, number, transactions);

        // Set stateRoot
        stateRoot = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        block.setStateRoot(stateRoot);
    }

    @Test
    @DisplayName("Constructor should initialize block with correct values")
    void testConstructor() {
        assertEquals(hash, block.getHash());
        assertEquals(parentHash, block.getParentHash());
        assertEquals(miner, block.getMiner());
        assertEquals(timestamp, block.getTimestamp());
        assertEquals(difficulty, block.getDifficulty());
        assertEquals(number, block.getNumber());
        assertEquals(transactions, block.getTransactions());
        assertArrayEquals(stateRoot, block.getStateRoot());
    }

    @Test
    @DisplayName("Getters should return correct values")
    void testGetters() {
        assertEquals(hash, block.getHash());
        assertEquals(parentHash, block.getParentHash());
        assertEquals(miner, block.getMiner());
        assertEquals(timestamp, block.getTimestamp());
        assertEquals(difficulty, block.getDifficulty());
        assertEquals(number, block.getNumber());
        assertEquals(transactions, block.getTransactions());
        assertArrayEquals(stateRoot, block.getStateRoot());
    }

    @Test
    @DisplayName("Setters should update values correctly")
    void testSetters() {
        // New test values
        String newHash = "0xnewhash1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        String newParentHash = "0xnewparent1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        String newMiner = "0xnewminer789abcdef0123456789abcdef01234567";
        long newTimestamp = timestamp + 1000;
        long newDifficulty = 2000L;
        long newNumber = 23456L;
        List<Transaction> newTransactions = new ArrayList<>();
        newTransactions.add(new Transaction(new byte[]{7, 8, 9}));
        byte[] newStateRoot = new byte[]{10, 9, 8, 7, 6, 5, 4, 3, 2, 1};

        // Set new values
        block.setHash(newHash);
        block.setParentHash(newParentHash);
        block.setMiner(newMiner);
        block.setTimestamp(newTimestamp);
        block.setDifficulty(newDifficulty);
        block.setNumber(newNumber);
        block.setTransactions(newTransactions);
        block.setStateRoot(newStateRoot);

        // Verify new values were set
        assertEquals(newHash, block.getHash());
        assertEquals(newParentHash, block.getParentHash());
        assertEquals(newMiner, block.getMiner());
        assertEquals(newTimestamp, block.getTimestamp());
        assertEquals(newDifficulty, block.getDifficulty());
        assertEquals(newNumber, block.getNumber());
        assertEquals(newTransactions, block.getTransactions());
        assertArrayEquals(newStateRoot, block.getStateRoot());
    }

    @Test
    @DisplayName("toString should return a formatted string representation")
    void testToString() {
        String result = block.toString();

        // Verify the toString contains all the important fields
        assertTrue(result.contains(hash));
        assertTrue(result.contains(parentHash));
        assertTrue(result.contains(miner));
        assertTrue(result.contains(String.valueOf(timestamp)));
        assertTrue(result.contains(String.valueOf(difficulty)));
        assertTrue(result.contains(String.valueOf(number)));

        // Verify transaction info is included
        if (!transactions.isEmpty()) {
            assertTrue(result.contains(transactions.get(0).toString()));
        }
    }

    @Test
    @DisplayName("serialize method should return null (not implemented)")
    void testSerialize() {
        assertNull(block.serialize());
    }

    @Test
    @DisplayName("deserialize method should return null (not implemented)")
    void testDeserialize() {
        byte[] testData = new byte[]{1, 2, 3};
        assertNull(Block.deserialize(testData));
    }

    @Test
    @DisplayName("Block can be created with empty transaction list")
    void testBlockWithEmptyTransactions() {
        Block emptyTxBlock = new Block(
                hash,
                parentHash,
                miner,
                timestamp,
                difficulty,
                number,
                new ArrayList<>()
        );

        assertEquals(0, emptyTxBlock.getTransactions().size());

        // Verify toString works with empty transaction list
        String result = emptyTxBlock.toString();
        assertTrue(result.contains("transactions=[]"));
    }

    @Test
    @DisplayName("Block can be created with null transaction list")
    void testBlockWithNullTransactions() {
        Block nullTxBlock = new Block(
                hash,
                parentHash,
                miner,
                timestamp,
                difficulty,
                number,
                null
        );

        assertNull(nullTxBlock.getTransactions());

        // Verify toString doesn't throw NPE with null transaction list
        String result = nullTxBlock.toString();
        assertTrue(result.contains("transactions=["));
    }
}