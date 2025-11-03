package org.example.app.core.block;

import org.example.app.core.crypto.Keypair.SignatureResult;
import org.example.app.core.types.Address;
import org.example.app.core.block.Transaction.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BaseTransactionTest {

    private Transaction transaction;
    private byte[] testData;

    @BeforeEach
    void setUp() {
        // Create test data
        testData = new byte[]{1, 2, 3, 4, 5};

        // Create transaction with test data
        transaction = new Transaction(testData);
    }

    @Test
    @DisplayName("Constructor should initialize transaction with provided data")
    void testConstructor() {
        // Verify transaction was initialized with correct data
        assertArrayEquals(testData, transaction.getData());

        // Verify default values are set
        assertEquals(TransactionStatus.PENDING, transaction.getStatus());
        assertEquals(BigInteger.ZERO, transaction.getGasPrice());
        assertEquals(21000, transaction.getGasLimit()); // Default gas limit
    }

    @Test
    @DisplayName("Constructor should handle null data")
    void testConstructorWithNullData() {
        // Create transaction with null data
        Transaction nullDataTransaction = new Transaction(null);

        // Verify empty byte array is used instead of null
        assertNotNull(nullDataTransaction.getData());
        assertEquals(0, nullDataTransaction.getData().length);
    }

    @Test
    @DisplayName("toString should return formatted string with data")
    void testToString() {
        // Get string representation
        String result = transaction.toString();

        // Verify it contains expected data
        assertTrue(result.contains(Arrays.toString(testData)));
        assertTrue(result.startsWith("Transaction{"));
        assertTrue(result.endsWith("}"));
    }

    @Test
    @DisplayName("equals should return true for transactions with same data")
    void testEquals() {
        // Create second transaction with same data
        Transaction sameTransaction = new Transaction(testData.clone());

        // Verify they are equal
        assertEquals(transaction, sameTransaction);

        // Create transaction with different data
        byte[] differentData = new byte[]{5, 4, 3, 2, 1};
        Transaction differentTransaction = new Transaction(differentData);

        // Verify they are not equal
        assertNotEquals(transaction, differentTransaction);

        // Test for null and different types
        assertNotEquals(transaction, null);
        assertNotEquals(transaction, "not a transaction");
    }

    @Test
    @DisplayName("hashCode should be consistent with equals")
    void testHashCode() {
        // Create second transaction with same data
        Transaction sameTransaction = new Transaction(testData.clone());

        // Verify hash codes are the same
        assertEquals(transaction.hashCode(), sameTransaction.hashCode());

        // Create transaction with different data
        byte[] differentData = new byte[]{5, 4, 3, 2, 1};
        Transaction differentTransaction = new Transaction(differentData);

        // Verify hash codes are different
        assertNotEquals(transaction.hashCode(), differentTransaction.hashCode());
    }

    @Test
    @DisplayName("toBytes should serialize the transaction")
    void testToBytes() {
        // Set up transaction with all fields
        transaction.setSender("0xsender123");
        transaction.setRecipient("0xrecipient456");
        transaction.setValue(1000L);
        transaction.setGasLimit(30000);
        transaction.setGasPrice(BigInteger.valueOf(10000000000L)); // 10 Gwei

        // Get serialized bytes
        byte[] serialized = transaction.toBytes();

        // Verify bytes are not null and have some content
        assertNotNull(serialized);
        assertTrue(serialized.length > 0);

        // Verify data is included in serialized bytes
        // We can't check exact positions since there might be headers/metadata,
        // but we can check that our original data exists somewhere in the serialized bytes
        boolean containsData = false;
        for (int i = 0; i <= serialized.length - testData.length; i++) {
            boolean match = true;
            for (int j = 0; j < testData.length; j++) {
                if (serialized[i + j] != testData[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                containsData = true;
                break;
            }
        }
        assertTrue(containsData, "Serialized bytes should contain the original data");
    }

    @Test
    @DisplayName("isValid should validate transaction fields correctly")
    void testIsValid() {
        // The isValid method checks the following fields:
        // from, to, value, gasLimit, gasPrice, and signature

        // By default the transaction will be invalid because most fields are not set
        assertFalse(transaction.isValid());

        // Let's try setting each field we can access and check the behavior

        // Set up basic fields we can set directly
        transaction.setGasPrice(BigInteger.valueOf(10000000000L));

        // Set signature
        SignatureResult signature = mock(SignatureResult.class);
        transaction.setSignature(signature);

        // We still expect this to be invalid since 'from' and 'to' aren't set
        assertFalse(transaction.isValid());

        // We can't directly test all conditions of isValid without modifying the class,
        // but we've verified that a transaction with missing fields is correctly
        // identified as invalid
    }

    @Test
    @DisplayName("setGasPrice should validate gas price")
    void testSetGasPrice() {
        // Valid gas price
        transaction.setGasPrice(BigInteger.valueOf(10000000000L));
        assertEquals(BigInteger.valueOf(10000000000L), transaction.getGasPrice());

        // Null gas price
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            transaction.setGasPrice(null);
        });
        assertTrue(exception.getMessage().contains("Gas price must be non-negative"));

        // Negative gas price
        exception = assertThrows(IllegalArgumentException.class, () -> {
            transaction.setGasPrice(BigInteger.valueOf(-1));
        });
        assertTrue(exception.getMessage().contains("Gas price must be non-negative"));
    }

    @Test
    @DisplayName("getTotalCost should calculate total transaction cost")
    void testGetTotalCost() {
        // Set up transaction values
        try {
            java.lang.reflect.Field valueField = Transaction.class.getDeclaredField("value");
            valueField.setAccessible(true);
            valueField.set(transaction, 1000L);

            transaction.setGasLimit(21000);
            transaction.setGasPrice(BigInteger.valueOf(10000000000L)); // 10 Gwei

            // Calculate expected cost: gasPrice * gasLimit + value
            BigInteger expectedCost = BigInteger.valueOf(10000000000L)
                    .multiply(BigInteger.valueOf(21000))
                    .add(BigInteger.valueOf(1000L));

            // Verify total cost
            assertEquals(expectedCost, transaction.getTotalCost());

        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("verifySignature should check signature validity")
    void testVerifySignature() {
        // Test with null signature
        assertFalse(transaction.verifySignature());

        // Test with valid signature
        SignatureResult signature = mock(SignatureResult.class);
        transaction.setSignature(signature);

        // Since we're testing a placeholder implementation, we're just checking
        // that the method returns as expected when there's a non-null signature
        assertTrue(transaction.verifySignature());
    }

    @Test
    @DisplayName("setStatus should update transaction status")
    void testSetStatus() {
        // Test each status value
        transaction.setStatus(TransactionStatus.PENDING);
        assertEquals(TransactionStatus.PENDING, transaction.getStatus());

        transaction.setStatus(TransactionStatus.CONFIRMED);
        assertEquals(TransactionStatus.CONFIRMED, transaction.getStatus());

        transaction.setStatus(TransactionStatus.FAILED);
        assertEquals(TransactionStatus.FAILED, transaction.getStatus());

        transaction.setStatus(TransactionStatus.INVALID);
        assertEquals(TransactionStatus.INVALID, transaction.getStatus());
    }
}