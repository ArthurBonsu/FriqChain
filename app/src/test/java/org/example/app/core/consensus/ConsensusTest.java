package org.example.app.core.consensus;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import org.example.app.core.block.Block;
import org.example.app.core.block.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@DisplayName("Consensus Mechanism Tests")
public class ConsensusTest {
    private static final Logger LOGGER = Logger.getLogger(ConsensusTest.class.getName());
    
    private Blockchain blockchain;
    private Consensus consensus;
    private EthashConsensus ethashConsensus;
    private ProofOfWork proofOfWork;

    @BeforeEach
    void setUp() {
        blockchain = new Blockchain();
        consensus = new Consensus();
        ethashConsensus = new EthashConsensus();
        proofOfWork = new ProofOfWork();
    }

    @Test
    @DisplayName("Null Block Validation")
    void testNullBlockValidation() {
        // Test null block validation
        assertFalse(consensus.validateBlock(null), 
            "Null block should not pass validation");
    }

    @Test
    @DisplayName("Invalid Block Properties")
    void testInvalidBlockProperties() {
        // Test block with negative number
        Block invalidNumberBlock = createBlockWithInvalidNumber();
        assertFalse(consensus.validateBlock(invalidNumberBlock), 
            "Block with negative number should not pass validation");

        // Test block with zero timestamp
        Block invalidTimestampBlock = createBlockWithInvalidTimestamp();
        assertFalse(consensus.validateBlock(invalidTimestampBlock), 
            "Block with zero timestamp should not pass validation");

        // Test block with null miner
        Block nullMinerBlock = createBlockWithNullMiner();
        assertFalse(consensus.validateBlock(nullMinerBlock), 
            "Block with null miner should not pass validation");

        // Test block with null hash
        Block nullHashBlock = createBlockWithNullHash();
        assertFalse(consensus.validateBlock(nullHashBlock), 
            "Block with null hash should not pass validation");
    }

    @Test
    @DisplayName("Blockchain Block Addition")
    void testBlockchainAddBlock() {
        // Create a genesis block with explicit validation-friendly parameters
        Block genesisBlock = createGenesisBlock();
        
        LOGGER.info("Genesis Block Details:");
        logBlockDetails(genesisBlock);
        
        // Add genesis block
        boolean genesisAdded = blockchain.addBlock(genesisBlock);
        assertTrue(genesisAdded, 
            "Genesis block should be added successfully");
        
        // Create and add a new block with correct parent hash and number
        Block nextBlock = createNextBlock(genesisBlock);
        
        LOGGER.info("Next Block Details:");
        logBlockDetails(nextBlock);
        
        boolean nextBlockAdded = blockchain.addBlock(nextBlock);
        assertTrue(nextBlockAdded, 
            "Next block should be added successfully");
        
        assertEquals(nextBlock, blockchain.getLatestBlock(), 
            "Latest block should be the recently added block");
        
        // Verify blockchain length
        assertEquals(2, blockchain.getChainLength(), 
            "Blockchain should have two blocks after adding genesis and next block");
    }

    @Test
    @DisplayName("Block Mining Null Checks")
    void testBlockMiningNullChecks() {
        // Prepare a previous block for mining
        Block previousBlock = createGenesisBlock();

        // Test null previous block
        assertThrows(IllegalArgumentException.class, 
            () -> consensus.mineBlock(null, "miner-address", 1000L),
            "Mining with null previous block should throw an exception");

        // Test null miner
        assertThrows(IllegalArgumentException.class, 
            () -> consensus.mineBlock(previousBlock, null, 1000L),
            "Mining with null miner should throw an exception");
    }

    @Test
    @DisplayName("Block Hash Validation")
    void testBlockHashValidation() {
        // Test block with predefined hash that should pass validation
        Block validHashBlock = createBlockWithValidHash();
        
        assertTrue(consensus.validateBlock(validHashBlock), 
            "Block with valid hash should pass validation");
    }

    @Test
    @DisplayName("Block Mining Process")
    void testBlockMiningProcess() {
        Block genesisBlock = createGenesisBlock();
        
        Block minedBlock = consensus.mineBlock(genesisBlock, "miner-address", 1000L);
        
        assertNotNull(minedBlock, "Mined block should not be null");
        assertEquals(genesisBlock.getHash(), minedBlock.getParentHash(), 
            "Mined block should reference parent block");
        assertEquals(genesisBlock.getNumber() + 1, minedBlock.getNumber(), 
            "Mined block number should be parent number + 1");
    }

    // Helper methods for block creation
    private Block createGenesisBlock() {
        return new Block(
            HashUtils.generateHash("genesis"),  // hash
            null,                               // parent hash
            "genesis-miner",                    // miner
            System.currentTimeMillis(),         // timestamp
            1000L,                              // difficulty
            0,                                  // block number
            new ArrayList<>()                   // empty transactions
        );
    }

    private Block createNextBlock(Block previousBlock) {
        return new Block(
            HashUtils.generateHash(previousBlock.getHash() + "next"), // hash
            previousBlock.getHash(),            // parent hash matches previous block
            "next-miner",                       // miner
            System.currentTimeMillis(),         // timestamp
            1000L,                              // difficulty
            previousBlock.getNumber() + 1,      // block number is sequential
            new ArrayList<>()                   // empty transactions
        );
    }

    // Methods for creating blocks with invalid properties
    private Block createBlockWithInvalidNumber() {
        return new Block(
            HashUtils.generateHash("invalid-number"),  // hash
            "parent-hash",                             // parent hash
            "test-miner",                              // miner
            System.currentTimeMillis(),                // timestamp
            1000L,                                     // difficulty
            -1,                                        // invalid block number
            new ArrayList<>()                          // empty transactions
        );
    }

    private Block createBlockWithInvalidTimestamp() {
        return new Block(
            HashUtils.generateHash("invalid-timestamp"),  // hash
            "parent-hash",                                // parent hash
            "test-miner",                                 // miner
            0,                                            // invalid timestamp
            1000L,                                        // difficulty
            1,                                            // block number
            new ArrayList<>()                             // empty transactions
        );
    }

    private Block createBlockWithNullMiner() {
        return new Block(
            HashUtils.generateHash("null-miner"),  // hash
            "parent-hash",                         // parent hash
            null,                                  // null miner
            System.currentTimeMillis(),            // timestamp
            1000L,                                 // difficulty
            1,                                     // block number
            new ArrayList<>()                      // empty transactions
        );
    }

    private Block createBlockWithNullHash() {
        return new Block(
            null,                                  // null hash
            "parent-hash",                         // parent hash
            "test-miner",                          // miner
            System.currentTimeMillis(),            // timestamp
            1000L,                                 // difficulty
            1,                                     // block number
            new ArrayList<>()                      // empty transactions
        );
    }

    private Block createBlockWithValidHash() {
        return new Block(
            "0000000000000000ffff",            // hash that will pass validation
            "0000000000000000000000",          // parent hash
            "test-miner",                      // miner
            System.currentTimeMillis(),        // timestamp
            1000L,                             // difficulty
            1,                                 // block number
            new ArrayList<>()                  // empty transactions
        );
    }

    // Utility method for logging block details
    private void logBlockDetails(Block block) {
        LOGGER.info("Hash: " + block.getHash());
        LOGGER.info("Parent Hash: " + block.getParentHash());
        LOGGER.info("Number: " + block.getNumber());
        LOGGER.info("Miner: " + block.getMiner());
        LOGGER.info("Timestamp: " + block.getTimestamp());
        LOGGER.info("Difficulty: " + block.getDifficulty());
    }
}
