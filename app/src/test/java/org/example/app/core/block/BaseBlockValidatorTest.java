package org.example.app.core.block;

import org.example.app.core.consensus.Blockchain;
import org.example.app.core.types.Hash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for BlockValidator that focus on black-box testing of
 * the public validateBlock method with various inputs.
 */
public class BaseBlockValidatorTest {

    @Mock
    private Blockchain blockchain;

    private BlockValidator blockValidator;
    private Block validBlock;
    private List<Transaction> validTransactions;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize mocks
        MockitoAnnotations.openMocks(this);

        // Create the BlockValidator instance to test
        blockValidator = new BlockValidator(blockchain);

        // Create valid transactions
        validTransactions = new ArrayList<>();
        Transaction tx1 = mock(Transaction.class);
        Transaction tx2 = mock(Transaction.class);

        when(tx1.getSender()).thenReturn("0xsender1");
        when(tx1.getRecipient()).thenReturn("0xrecipient1");
        when(tx1.getGasPrice()).thenReturn(BigInteger.valueOf(10_000_000_000L)); // 10 Gwei

        when(tx2.getSender()).thenReturn("0xsender2");
        when(tx2.getRecipient()).thenReturn("0xrecipient2");
        when(tx2.getGasPrice()).thenReturn(BigInteger.valueOf(20_000_000_000L)); // 20 Gwei

        validTransactions.add(tx1);
        validTransactions.add(tx2);

        // Set up parent hash for valid block
        Hash prevBlockHash = new Hash(new byte[32]);

        // Create a mock block with valid properties
        validBlock = mock(Block.class);
        when(validBlock.getNumber()).thenReturn(1L);
        when(validBlock.getMiner()).thenReturn("0xminer123");
        when(validBlock.getParentHash()).thenReturn(prevBlockHash.toString());
        when(validBlock.getTimestamp()).thenReturn(System.currentTimeMillis() - 5000); // 5 seconds ago
        when(validBlock.getTransactions()).thenReturn(validTransactions);
        when(validBlock.getHash()).thenReturn("0x0000123456789abcdef"); // Very low hash value to pass PoW
        when(validBlock.getDifficulty()).thenReturn(1L); // Low difficulty
        when(validBlock.toString()).thenReturn("MockBlockString"); // For size calculation

        // For validation to pass, create a previous block
        Block previousBlock = mock(Block.class);
        when(previousBlock.getDifficulty()).thenReturn(1L);
        when(previousBlock.getTimestamp()).thenReturn(System.currentTimeMillis() - 10000); // 10 seconds ago

        // Setup the blockchain to return the previous block
        when(blockchain.getBlockByHash(anyString())).thenReturn(previousBlock);
    }

    @Test
    @DisplayName("validateBlock should return false for null block")
    void testValidateNullBlock() throws Exception {
        // Act
        boolean result = blockValidator.validateBlock(null);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("validateBlock should return false for block with null miner")
    void testValidateBlockNullMiner() throws Exception {
        // Arrange
        when(validBlock.getMiner()).thenReturn(null);

        // Act
        boolean result = blockValidator.validateBlock(validBlock);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("validateBlock should return false for block with empty miner")
    void testValidateBlockEmptyMiner() throws Exception {
        // Arrange
        when(validBlock.getMiner()).thenReturn("");

        // Act
        boolean result = blockValidator.validateBlock(validBlock);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("validateBlock should return false for block with incorrect previous hash")
    void testValidateBlockIncorrectPreviousHash() throws Exception {
        // Arrange - set up blockchain to return null for any hash
        when(blockchain.getBlockByHash(anyString())).thenReturn(null);

        // Act
        boolean result = blockValidator.validateBlock(validBlock);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("validateBlock should return false for block with future timestamp")
    void testValidateBlockFutureTimestamp() throws Exception {
        // Arrange
        long farFutureTime = System.currentTimeMillis() + 20 * 60 * 1000; // 20 minutes in future (exceeds 15 min limit)
        when(validBlock.getTimestamp()).thenReturn(farFutureTime);

        // Act
        boolean result = blockValidator.validateBlock(validBlock);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("validateBlock should return false for block with null transactions")
    void testValidateBlockNullTransactions() throws Exception {
        // Arrange
        when(validBlock.getTransactions()).thenReturn(null);

        // Act
        boolean result = blockValidator.validateBlock(validBlock);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("validateBlock should return false for block with too many transactions")
    void testValidateBlockTooManyTransactions() throws Exception {
        // Arrange
        List<Transaction> tooManyTx = new ArrayList<>();
        for (int i = 0; i < 1001; i++) { // MAX_TRANSACTIONS_PER_BLOCK is 1000
            Transaction tx = mock(Transaction.class);
            when(tx.getSender()).thenReturn("0xsender" + i);
            when(tx.getRecipient()).thenReturn("0xrecipient" + i);
            when(tx.getGasPrice()).thenReturn(BigInteger.valueOf(10_000_000_000L));
            tooManyTx.add(tx);
        }
        when(validBlock.getTransactions()).thenReturn(tooManyTx);

        // Act
        boolean result = blockValidator.validateBlock(validBlock);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("validateBlock should return false for block with invalid transaction (null sender)")
    void testValidateBlockTransactionNullSender() throws Exception {
        // Arrange
        Transaction invalidTx = mock(Transaction.class);
        when(invalidTx.getSender()).thenReturn(null);
        when(invalidTx.getRecipient()).thenReturn("0xrecipient");
        when(invalidTx.getGasPrice()).thenReturn(BigInteger.valueOf(10_000_000_000L));

        List<Transaction> transactions = new ArrayList<>(validTransactions);
        transactions.add(invalidTx);
        when(validBlock.getTransactions()).thenReturn(transactions);

        // Act
        boolean result = blockValidator.validateBlock(validBlock);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("validateBlock should return false for block with invalid transaction (null recipient)")
    void testValidateBlockTransactionNullRecipient() throws Exception {
        // Arrange
        Transaction invalidTx = mock(Transaction.class);
        when(invalidTx.getSender()).thenReturn("0xsender");
        when(invalidTx.getRecipient()).thenReturn(null);
        when(invalidTx.getGasPrice()).thenReturn(BigInteger.valueOf(10_000_000_000L));

        List<Transaction> transactions = new ArrayList<>(validTransactions);
        transactions.add(invalidTx);
        when(validBlock.getTransactions()).thenReturn(transactions);

        // Act
        boolean result = blockValidator.validateBlock(validBlock);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("validateBlock should return false for block exceeding gas limit")
    void testValidateBlockExceedingGasLimit() throws Exception {
        // Arrange
        List<Transaction> highGasTx = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Transaction tx = mock(Transaction.class);
            when(tx.getSender()).thenReturn("0xsender" + i);
            when(tx.getRecipient()).thenReturn("0xrecipient" + i);
            // Very high gas price that would exceed the MAX_BLOCK_GAS_LIMIT
            when(tx.getGasPrice()).thenReturn(BigInteger.valueOf(10_000_000_000_000_000L));
            highGasTx.add(tx);
        }
        when(validBlock.getTransactions()).thenReturn(highGasTx);

        // Act
        boolean result = blockValidator.validateBlock(validBlock);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("validateBlock should return false for block exceeding size limit")
    void testValidateBlockExceedingSizeLimit() throws Exception {
        // Arrange
        // Create a string that's larger than MAX_BLOCK_SIZE_BYTES (2MB)
        StringBuilder largeString = new StringBuilder();
        for (int i = 0; i < 3 * 1024 * 1024 / 10; i++) { // 3MB / 10 to avoid memory issues
            largeString.append("XXXXXXXXXX"); // 10 chars each iteration
        }
        when(validBlock.toString()).thenReturn(largeString.toString());

        // Act
        boolean result = blockValidator.validateBlock(validBlock);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("validateBlock should return false for block with invalid proof of work")
    void testValidateBlockInvalidProofOfWork() throws Exception {
        // Arrange
        when(validBlock.getHash()).thenReturn("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"); // Very high hash value
        when(validBlock.getDifficulty()).thenReturn(1000000000L); // High difficulty

        // Act
        boolean result = blockValidator.validateBlock(validBlock);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("validateBlock should return false for block with null hash")
    void testValidateBlockNullHash() throws Exception {
        // Arrange
        when(validBlock.getHash()).thenReturn(null);

        // Act
        boolean result = blockValidator.validateBlock(validBlock);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("validateBlock should return false for block with empty hash")
    void testValidateBlockEmptyHash() throws Exception {
        // Arrange
        when(validBlock.getHash()).thenReturn("");

        // Act
        boolean result = blockValidator.validateBlock(validBlock);

        // Assert
        assertFalse(result);
    }
}