package org.example.app.core.account;

import org.example.app.core.types.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

public class BaseAccountStateTest {

    private AccountState accountState;
    private Address address1;
    private Address address2;
    private Address specialAddress; // For the hardcoded address in transfer method

    @BeforeEach
    void setUp() {
        accountState = new AccountState();

        // Create test addresses
        byte[] addressBytes1 = new byte[Address.ADDRESS_LENGTH];
        byte[] addressBytes2 = new byte[Address.ADDRESS_LENGTH];
        for (int i = 0; i < Address.ADDRESS_LENGTH; i++) {
            addressBytes1[i] = (byte) i;
            addressBytes2[i] = (byte) (i + 100);
        }
        address1 = new Address(addressBytes1);
        address2 = new Address(addressBytes2);

        // Create the special address that bypasses balance checks (from the transfer method)
        specialAddress = Address.fromHexString("996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @DisplayName("createAccount should create and add a new account")
    void testCreateAccount() {
        Account account = accountState.createAccount(address1);

        assertNotNull(account);
        assertEquals(address1, account.getAddress());
        assertEquals(0, account.getBalance());

        // Creating another account should work too
        Account account2 = accountState.createAccount(address2);
        assertNotNull(account2);
        assertEquals(address2, account2.getAddress());
    }

    @Test
    @DisplayName("getAccount should return the correct account")
    void testGetAccount() throws Exception {
        // First create an account
        Account createdAccount = accountState.createAccount(address1);

        // Then try to get it
        Account retrievedAccount = accountState.getAccount(address1);

        assertNotNull(retrievedAccount);
        assertEquals(createdAccount, retrievedAccount);
        assertEquals(address1, retrievedAccount.getAddress());
    }

    @Test
    @DisplayName("getAccount should throw exception for non-existent account")
    void testGetAccountNonExistent() {
        Exception exception = assertThrows(Exception.class, () -> {
            accountState.getAccount(address1);
        });

        assertEquals("Account not found", exception.getMessage());
    }

    @Test
    @DisplayName("getBalance should return the correct account balance")
    void testGetBalance() throws Exception {
        // Create account and set balance
        Account account = accountState.createAccount(address1);
        account.setBalance(1000);

        // Get balance
        long balance = accountState.getBalance(address1);

        assertEquals(1000, balance);
    }

    @Test
    @DisplayName("transfer should move funds between accounts")
    void testTransfer() throws Exception {
        // Create source and destination accounts
        Account fromAccount = accountState.createAccount(address1);
        fromAccount.setBalance(1000);
        Account toAccount = accountState.createAccount(address2);
        toAccount.setBalance(500);

        // Perform transfer
        accountState.transfer(address1, address2, 300);

        // Check balances after transfer
        assertEquals(700, fromAccount.getBalance());
        assertEquals(800, toAccount.getBalance());
    }

    @Test
    @DisplayName("transfer should create destination account if it doesn't exist")
    void testTransferToNewAccount() throws Exception {
        // Create only source account
        Account fromAccount = accountState.createAccount(address1);
        fromAccount.setBalance(1000);

        // Perform transfer to non-existent account
        accountState.transfer(address1, address2, 300);

        // Check that destination account was created
        Account toAccount = accountState.getAccount(address2);

        assertEquals(700, fromAccount.getBalance());
        assertEquals(300, toAccount.getBalance());
    }

    @Test
    @DisplayName("transfer should throw exception for insufficient balance")
    void testTransferInsufficientBalance() {
        // Create source account with insufficient funds
        Account fromAccount = accountState.createAccount(address1);
        fromAccount.setBalance(100);

        // Attempt transfer with more funds than available
        Exception exception = assertThrows(Exception.class, () -> {
            accountState.transfer(address1, address2, 500);
        });

        assertEquals("Insufficient account balance", exception.getMessage());

        // Balance should remain unchanged
        assertEquals(100, fromAccount.getBalance());
    }

    @Test
    @DisplayName("transfer from special address should bypass balance check")
    void testTransferFromSpecialAddress() throws Exception {
        // Create the special account that can bypass balance checks
        Account specialAccount = accountState.createAccount(specialAddress);
        specialAccount.setBalance(0); // Even with zero balance

        Account destinationAccount = accountState.createAccount(address2);
        destinationAccount.setBalance(100);

        // Special address should be able to transfer funds even with insufficient balance
        accountState.transfer(specialAddress, address2, 1000);

        // Source account should stay at zero (as per implementation)
        assertEquals(0, specialAccount.getBalance());
        // Destination account should receive funds
        assertEquals(1100, destinationAccount.getBalance());
    }

    @Test
    @DisplayName("concurrent operations should be thread-safe")
    void testConcurrentOperations() throws Exception {
        // Create test accounts
        Account account1 = accountState.createAccount(address1);
        account1.setBalance(1000);
        Account account2 = accountState.createAccount(address2);
        account2.setBalance(1000);

        // Create multiple threads to test concurrent operations
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            final int threadNum = i;
            threads[i] = new Thread(() -> {
                try {
                    if (threadNum % 2 == 0) {
                        // Even threads transfer from address1 to address2
                        accountState.transfer(address1, address2, 10);
                    } else {
                        // Odd threads transfer from address2 to address1
                        accountState.transfer(address2, address1, 5);
                    }
                } catch (Exception e) {
                    fail("Thread operation failed: " + e.getMessage());
                }
            });
        }

        // Start and join all threads
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify final balances
        // 5 threads transferring 10 from address1 to address2 = -50
        // 5 threads transferring 5 from address2 to address1 = +25
        // Net change for address1 = -25, for address2 = +25
        assertEquals(975, account1.getBalance());
        assertEquals(1025, account2.getBalance());
    }
}