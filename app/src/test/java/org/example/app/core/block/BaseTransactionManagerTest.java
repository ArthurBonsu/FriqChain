package org.example.app.core.block;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BaseTransactionManagerTest {

    private TransactionManager transactionManager;
    private Transaction transaction1;
    private Transaction transaction2;
    private Transaction transaction3;

    @BeforeEach
    void setUp() {
        // Initialize transaction manager
        transactionManager = new TransactionManager();

        // Create mock transactions
        transaction1 = mock(Transaction.class);
        transaction2 = mock(Transaction.class);
        transaction3 = mock(Transaction.class);
    }

    @Test
    @DisplayName("Constructor should initialize an empty transaction pool")
    void testConstructor() {
        // Verify transaction pool is initially empty
        List<Transaction> transactions = transactionManager.getTransactionsForBlock();
        assertNotNull(transactions, "Transaction list should not be null");
        assertTrue(transactions.isEmpty(), "Transaction list should be empty");
    }

    @Test
    @DisplayName("addTransaction should add transaction to the pool")
    void testAddTransaction() {
        // Add a transaction
        transactionManager.addTransaction(transaction1);

        // Verify transaction is in the pool
        List<Transaction> transactions = transactionManager.getTransactionsForBlock();
        assertEquals(1, transactions.size(), "Transaction pool should contain 1 transaction");
        assertTrue(transactions.contains(transaction1), "Transaction pool should contain the added transaction");
    }

    @Test
    @DisplayName("addTransaction should accept multiple transactions")
    void testAddMultipleTransactions() {
        // Add multiple transactions
        transactionManager.addTransaction(transaction1);
        transactionManager.addTransaction(transaction2);
        transactionManager.addTransaction(transaction3);

        // Verify all transactions are in the pool
        List<Transaction> transactions = transactionManager.getTransactionsForBlock();
        assertEquals(3, transactions.size(), "Transaction pool should contain 3 transactions");
        assertTrue(transactions.contains(transaction1), "Transaction pool should contain transaction1");
        assertTrue(transactions.contains(transaction2), "Transaction pool should contain transaction2");
        assertTrue(transactions.contains(transaction3), "Transaction pool should contain transaction3");
    }

    @Test
    @DisplayName("addTransaction should accept duplicate transactions")
    void testAddDuplicateTransactions() {
        // Add the same transaction multiple times
        transactionManager.addTransaction(transaction1);
        transactionManager.addTransaction(transaction1);
        transactionManager.addTransaction(transaction1);

        // Verify transaction is added multiple times
        List<Transaction> transactions = transactionManager.getTransactionsForBlock();
        assertEquals(3, transactions.size(), "Transaction pool should contain 3 transactions");
        assertEquals(transaction1, transactions.get(0), "First transaction should be transaction1");
        assertEquals(transaction1, transactions.get(1), "Second transaction should be transaction1");
        assertEquals(transaction1, transactions.get(2), "Third transaction should be transaction1");
    }

    @Test
    @DisplayName("addTransaction should accept null transactions")
    void testAddNullTransaction() {
        // Add null transaction
        transactionManager.addTransaction(null);

        // Verify null transaction is added
        List<Transaction> transactions = transactionManager.getTransactionsForBlock();
        assertEquals(1, transactions.size(), "Transaction pool should contain 1 transaction");
        assertNull(transactions.get(0), "Transaction should be null");
    }

    @Test
    @DisplayName("getTransactionsForBlock should return a copy of the transaction pool")
    void testGetTransactionsForBlockReturnsCopy() {
        // Add transactions
        transactionManager.addTransaction(transaction1);
        transactionManager.addTransaction(transaction2);

        // Get transactions
        List<Transaction> transactions1 = transactionManager.getTransactionsForBlock();
        assertEquals(2, transactions1.size(), "Transaction pool should contain 2 transactions");

        // Modify the returned list
        transactions1.remove(0);
        assertEquals(1, transactions1.size(), "Modified list should contain 1 transaction");

        // Get transactions again - should still have 2
        List<Transaction> transactions2 = transactionManager.getTransactionsForBlock();
        assertEquals(2, transactions2.size(), "Transaction pool should still contain 2 transactions");
    }

    @Test
    @DisplayName("getTransactionsForBlock should return transactions in the order they were added")
    void testGetTransactionsForBlockPreservesOrder() {
        // Add transactions in specific order
        transactionManager.addTransaction(transaction1);
        transactionManager.addTransaction(transaction2);
        transactionManager.addTransaction(transaction3);

        // Get transactions
        List<Transaction> transactions = transactionManager.getTransactionsForBlock();

        // Verify order is preserved
        assertEquals(3, transactions.size(), "Transaction pool should contain 3 transactions");
        assertSame(transaction1, transactions.get(0), "First transaction should be transaction1");
        assertSame(transaction2, transactions.get(1), "Second transaction should be transaction2");
        assertSame(transaction3, transactions.get(2), "Third transaction should be transaction3");
    }
}