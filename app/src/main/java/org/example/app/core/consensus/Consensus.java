package org.example.app.core.consensus;

import org.example.app.core.block.Block;
import java.util.logging.Logger;

public class Consensus {
    private static final Logger LOGGER = Logger.getLogger(Consensus.class.getName());
    
    private EthashConsensus ethashConsensus;
    private static final long BASE_DIFFICULTY = 1000000L; // Example base difficulty

    public Consensus() {
        this.ethashConsensus = new EthashConsensus();
    }

    // Updated method to match the test's call signature
    public Block mineBlock(Block previousBlock, String miner, long difficulty) {
        // Validate input parameters
        if (previousBlock == null) {
            throw new IllegalArgumentException("Previous block cannot be null");
        }

        if (miner == null || miner.trim().isEmpty()) {
            throw new IllegalArgumentException("Miner address cannot be null or empty");
        }

        return ethashConsensus.mineBlock(previousBlock, miner, difficulty);
    }

    // Validates the block using the consensus mechanism
    public boolean validateBlock(Block block) {
        // Null check
        if (block == null) {
            LOGGER.warning("Block is null");
            return false;
        }

        LOGGER.info("Validating block: " + block);

        // Validate block properties
        if (block.getNumber() < 0) {
            LOGGER.warning("Invalid block number: " + block.getNumber());
            return false;
        }
        
        if (block.getTimestamp() <= 0) {
            LOGGER.warning("Invalid timestamp: " + block.getTimestamp());
            return false;
        }
        
        // Difficulty validation with more flexibility
        try {
            long difficulty = block.getDifficulty();
            if (difficulty < 0 || difficulty > BASE_DIFFICULTY * 2) {
                LOGGER.warning("Invalid difficulty: " + difficulty);
                return false;
            }
        } catch (NullPointerException e) {
            LOGGER.warning("Difficulty is null");
            return false;
        }
        
        // Miner validation
        if (block.getMiner() == null || block.getMiner().trim().isEmpty()) {
            LOGGER.warning("Miner is null or empty");
            return false;
        }
        
        // Hash validation
        if (block.getHash() == null || block.getHash().trim().isEmpty()) {
            LOGGER.warning("Block hash is null or empty");
            return false;
        }

        // Special handling for genesis block
        if (block.getNumber() == 0) {
            boolean isGenesisValid = block.getParentHash() == null;
            LOGGER.info("Genesis block validation result: " + isGenesisValid);
            return isGenesisValid;
        }

        // Validate parent hash for non-genesis blocks
        if (block.getParentHash() == null || block.getParentHash().trim().isEmpty()) {
            LOGGER.warning("Parent hash is null or empty for non-genesis block");
            return false;
        }

        // Proof of work validation
        boolean isProofOfWorkValid = ethashConsensus.validateProofOfWork(block);
        LOGGER.info("Proof of work validation result: " + isProofOfWorkValid);
        
        return isProofOfWorkValid;
    }
}