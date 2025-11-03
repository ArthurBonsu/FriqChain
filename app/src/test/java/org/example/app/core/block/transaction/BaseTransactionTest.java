package org.example.app.core.block.transaction;

import org.example.app.core.block.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Field;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

public class BaseTransactionTest {
    private Transaction transaction;
    private String sender;
    private String recipient;
    private long value;
    private BigInteger fee;
    private long nonce;
    private byte[] data;
    private boolean contractCreation;
    private boolean contractCall;

    @BeforeEach
    void setUp() throws Exception {
        // Use default constructor (with empty byte array)
        transaction = new Transaction(new byte[0]);

        // Prepare test data
        sender = "0x1234567890123456789012345678901234567890";
        recipient = "0x0987654321098765432109876543210987654321";
        value = 1000000000000000000L; // 1 ETH in wei (as long)
        fee = BigInteger.valueOf(21000000000000L);       // converted from long
        nonce = 123L;
        data = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05};
        contractCreation = false;
        contractCall = true;

        // Use reflection to set the private fields
        Field senderField = Transaction.class.getDeclaredField("sender");
        senderField.setAccessible(true);
        senderField.set(transaction, sender);

        Field recipientField = Transaction.class.getDeclaredField("recipient");
        recipientField.setAccessible(true);
        recipientField.set(transaction, recipient);

        Field valueField = Transaction.class.getDeclaredField("value");
        valueField.setAccessible(true);
        valueField.set(transaction, value); // long -> long (match)

        Field feeField = Transaction.class.getDeclaredField("fee");
        feeField.setAccessible(true);
        feeField.set(transaction, fee); // now it's long

        Field nonceField = Transaction.class.getDeclaredField("nonce");
        nonceField.setAccessible(true);
        nonceField.set(transaction, nonce);

        Field dataField = Transaction.class.getDeclaredField("data");
        dataField.setAccessible(true);
        dataField.set(transaction, data);

        Field contractCreationField = Transaction.class.getDeclaredField("contractCreation");
        contractCreationField.setAccessible(true);
        contractCreationField.set(transaction, contractCreation);

        Field contractCallField = Transaction.class.getDeclaredField("contractCall");
        contractCallField.setAccessible(true);
        contractCallField.set(transaction, contractCall);
    }

    @Test
    @DisplayName("getSender should return the correct sender address")
    void testGetSender() {
        assertEquals(sender, transaction.getSender());
    }

    @Test
    @DisplayName("getRecipient should return the correct recipient address")
    void testGetRecipient() {
        assertEquals(recipient, transaction.getRecipient());
    }

    @Test
    @DisplayName("getValue should return the correct transaction value")
    void testGetValue() {
        assertEquals(value, transaction.getValue());
    }

    @Test
    @DisplayName("getFee should return the correct transaction fee")
    void testGetFee() {
        assertEquals(fee, transaction.getFee());
    }

    @Test
    @DisplayName("getNonce should return the correct nonce")
    void testGetNonce() {
        assertEquals(nonce, transaction.getNonce());
    }

    @Test
    @DisplayName("getData should return the correct data byte array")
    void testGetData() {
        assertArrayEquals(data, transaction.getData());
    }

    @Test
    @DisplayName("isContractCreation should return the correct contract creation flag")
    void testIsContractCreation() {
        assertEquals(contractCreation, transaction.isContractCreation());
    }

    @Test
    @DisplayName("isContractCall should return the correct contract call flag")
    void testIsContractCall() {
        assertEquals(contractCall, transaction.isContractCall());
    }

    @Test
    @DisplayName("Test with contract creation transaction")
    void testContractCreationTransaction() throws Exception {
        Transaction contractTx = new Transaction(new byte[0]);

        Field senderField = Transaction.class.getDeclaredField("sender");
        senderField.setAccessible(true);
        senderField.set(contractTx, sender);

        Field recipientField = Transaction.class.getDeclaredField("recipient");
        recipientField.setAccessible(true);
        recipientField.set(contractTx, null); // No recipient

        Field valueField = Transaction.class.getDeclaredField("value");
        valueField.setAccessible(true);
        valueField.set(contractTx, 0L); // No value sent

        Field contractCreationField = Transaction.class.getDeclaredField("contractCreation");
        contractCreationField.setAccessible(true);
        contractCreationField.set(contractTx, true);

        Field contractCallField = Transaction.class.getDeclaredField("contractCall");
        contractCallField.setAccessible(true);
        contractCallField.set(contractTx, false);

        assertTrue(contractTx.isContractCreation());
        assertFalse(contractTx.isContractCall());
        assertNull(contractTx.getRecipient());
        assertEquals(0L, contractTx.getValue());
    }

    @Test
    @DisplayName("Test with contract call transaction")
    void testContractCallTransaction() {
        assertTrue(transaction.isContractCall());
        assertFalse(transaction.isContractCreation());
        assertNotNull(transaction.getRecipient());
    }

    @Test
    @DisplayName("Test with null data")
    void testNullData() throws Exception {
        Transaction nullDataTx = new Transaction(new byte[0]);

        Field dataField = Transaction.class.getDeclaredField("data");
        dataField.setAccessible(true);
        dataField.set(nullDataTx, null);

        assertNull(nullDataTx.getData());
    }

    @Test
    @DisplayName("Test with empty data")
    void testEmptyData() throws Exception {
        Transaction emptyDataTx = new Transaction(new byte[0]);

        Field dataField = Transaction.class.getDeclaredField("data");
        dataField.setAccessible(true);
        dataField.set(emptyDataTx, new byte[0]);

        assertNotNull(emptyDataTx.getData());
        assertEquals(0, emptyDataTx.getData().length);
    }
}