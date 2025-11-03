package org.example.app.core.account;

import org.example.app.core.types.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

public class BaseAccountTest {

    private Address testAddress;
    private Account account;

    @BeforeEach
    void setUp() {
        // Create a valid Address object for testing with exactly 20 bytes
        byte[] addressBytes = new byte[Address.ADDRESS_LENGTH]; // 20 bytes, all zeros
        // Fill with some test data
        for (int i = 0; i < addressBytes.length; i++) {
            addressBytes[i] = (byte) i;
        }
        testAddress = new Address(addressBytes);
        account = new Account(testAddress);
    }

    @Test
    @DisplayName("Constructor should initialize account with zero balance")
    void testConstructor() {
        // Verify that a new account has the correct address and zero balance
        assertEquals(testAddress, account.getAddress());
        assertEquals(0, account.getBalance());
    }

    @Test
    @DisplayName("getAddress should return the account's address")
    void testGetAddress() {
        // Verify getAddress returns the correct address
        assertEquals(testAddress, account.getAddress());

        // Create a different address and verify it's different
        byte[] differentBytes = new byte[Address.ADDRESS_LENGTH];
        for (int i = 0; i < differentBytes.length; i++) {
            differentBytes[i] = (byte) (i + 100); // Different values than the first address
        }
        Address differentAddress = new Address(differentBytes);
        Account differentAccount = new Account(differentAddress);

        assertEquals(differentAddress, differentAccount.getAddress());
        assertNotEquals(account.getAddress(), differentAccount.getAddress());
    }

    @Test
    @DisplayName("getBalance should return the current balance")
    void testGetBalance() {
        // Initial balance should be zero
        assertEquals(0, account.getBalance());

        // Set balance and verify getBalance returns the new value
        account.setBalance(100);
        assertEquals(100, account.getBalance());
    }

    @Test
    @DisplayName("setBalance should update the account balance")
    void testSetBalance() {
        // Set positive balance
        account.setBalance(1000);
        assertEquals(1000, account.getBalance());

        // Update to a different balance
        account.setBalance(2500);
        assertEquals(2500, account.getBalance());

        // Set to zero
        account.setBalance(0);
        assertEquals(0, account.getBalance());

        // Set to negative (assuming this is allowed in your implementation)
        account.setBalance(-500);
        assertEquals(-500, account.getBalance());
    }

    @Test
    @DisplayName("toString should return balance as string")
    void testToString() {
        // Test with zero balance
        assertEquals("0", account.toString());

        // Test with positive balance
        account.setBalance(1234);
        assertEquals("1234", account.toString());

        // Test with negative balance
        account.setBalance(-789);
        assertEquals("-789", account.toString());
    }
}