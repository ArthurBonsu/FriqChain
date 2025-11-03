package org.example.app.core.consensus;

import org.example.app.core.block.Block;
import org.example.app.core.block.Transaction;
import java.util.List;
import java.util.logging.Logger;

public class Blockchain {
    private static final Logger LOGGER = Logger.getLogger(Blockchain.class.getName());
    
    private List<Block> blocks;
    private List<Transaction> pendingTransactions;
    private Consensus consensus;

    public Blockchain() {
        this.blocks = new java.util.ArrayList<>();
        this.pendingTransactions = new java.util.ArrayList<>();
        this.consensus = new Consensus();
    }

    // Add a new block to the blockchain after consensus validation
    public boolean addBlock(Block block) {
        LOGGER.info("Attempting to add block: " + block);
        
        // Special handling for genesis block (first block in the chain)
        if (blocks.isEmpty()) {
            // For genesis block, only validate the block
            if (block.getNumber() == 0) {
                boolean isValid = consensus.validateBlock(block);
                LOGGER.info("Genesis block validation result: " + isValid);
                if (isValid) {
                    blocks.add(block);
                    return true;
                }
                return false;
            }
        } else {
            // For non-genesis blocks, perform more rigorous validation
            Block latestBlock = getLatestBlock();
            
            LOGGER.info("Latest block: " + latestBlock);
            LOGGER.info("New block parent hash: " + block.getParentHash());
            LOGGER.info("Latest block hash: " + latestBlock.getHash());
            
            // Validate parent hash
            if (!block.getParentHash().equals(latestBlock.getHash())) {
                LOGGER.warning("Parent hash validation failed");
                return false;
            }
            
            // Validate sequential block number
            if (block.getNumber() != latestBlock.getNumber() + 1) {
                LOGGER.warning("Block number validation failed");
                return false;
            }
        }
        
        // Validate block using consensus mechanism
        boolean isConsensusValid = consensus.validateBlock(block);
        LOGGER.info("Consensus validation result: " + isConsensusValid);
        
        if (isConsensusValid) {
            blocks.add(block);
            return true;
        }
        
        return false;
    }

    // Get block by its hash
    public Block getBlockByHash(String blockHash) {
        for (Block block : blocks) {
            if (block.getHash().equals(blockHash)) {
                return block;
            }
        }
        return null;
    }

    // Retrieve the most recent block
    public Block getLatestBlock() {
        if (blocks.isEmpty()) {
            return null;
        }
        return blocks.get(blocks.size() - 1);
    }

    // Returns the current length/height of the blockchain
    public int getChainLength() {
        return blocks.size();
    }

    // Alias for getChainLength to maintain compatibility with tests
    public int getBlockCount() {
        return getChainLength();
    }

    /**
     * Retrieves a block at the specified index position in the blockchain
     * 
     * @param index The position of the block to retrieve (0-based)
     * @return The Block at the specified index, or null if index is out of bounds
     */
    public Block getBlockByIndex(int index) {
        if (index < 0 || index >= blocks.size()) {
            LOGGER.warning("Attempted to access block at invalid index: " + index);
            return null;
        }
        return blocks.get(index);
    }

    /**
     * Adds a transaction to the pending transaction pool
     * 
     * @param transaction The transaction to add
     */
    public void addTransaction(Transaction transaction) {
        if (transaction == null) {
            LOGGER.warning("Attempted to add null transaction");
            return;
        }
        
        // Basic validation - can be expanded based on your transaction validation logic
        if (isValidTransaction(transaction)) {
            LOGGER.info("Adding transaction to pool: " + transaction.getHash());
            pendingTransactions.add(transaction);
        } else {
            LOGGER.warning("Invalid transaction rejected: " + transaction.getHash());
        }
    }

    /**
     * Validates a transaction
     * 
     * @param transaction The transaction to validate
     * @return true if the transaction is valid, false otherwise
     */
    private boolean isValidTransaction(Transaction transaction) {
        // Implement transaction validation logic
        // This is a placeholder - implement according to your transaction rules
        return transaction.getHash() != null && !transaction.getHash().isEmpty();
    }

    /**
     * Gets all pending transactions
     * 
     * @return List of pending transactions
     */
    public List<Transaction> getPendingTransactions() {
        return new java.util.ArrayList<>(pendingTransactions);
    }

    /**
     * Removes transactions that have been included in a block
     * 
     * @param transactions List of transactions to remove from the pending pool
     */
    public void removeTransactions(List<Transaction> transactions) {
        pendingTransactions.removeAll(transactions);
    }
    
    /**
     * Gets the consensus mechanism
     */
    public Consensus getConsensus() {
        return consensus;
    }
    
    /**
     * Sets the consensus mechanism
     */
    public void setConsensus(Consensus consensus) {
        this.consensus = consensus;
    }
    
    /**
     * Gets all blocks in the blockchain
     */
    public List<Block> getAllBlocks() {
        return new java.util.ArrayList<>(blocks);
    }
}