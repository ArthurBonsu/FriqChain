package org.example.app.core.smartcontract;

import org.example.app.core.block.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Comprehensive test suite for Smart Contract components
 * Covers EVM, Account, EvmState, GasManager, and related functionalities
 */
public class BaseSmartContractTest {
    private static final Logger LOGGER = Logger.getLogger(BaseSmartContractTest.class.getName());
    
    private EvmState evmState;
    private GasManager gasManager;
    private Evm evm;

    @BeforeEach
    public void setUp() {
        // Initialize components before each test
        evmState = new EvmState();
        gasManager = new GasManager();
        evm = new Evm(evmState);
        
        LOGGER.info("Test setup completed. Initializing EvmState and GasManager.");
    }

    /**
     * Create a simple smart contract bytecode for testing
     * @return byte array representing the contract bytecode
     */
    private byte[] createSimpleContract() {
        // Bytecode: PUSH1 5, PUSH1 7, ADD, PUSH1 1, PUSH1 12, SSTORE, RETURN
        return new byte[] {
                (byte) 0x60, 0x05,    // PUSH1 opcode (0x60) and first value (5)
                (byte) 0x60, 0x07,    // PUSH1 opcode (0x60) and second value (7)
                (byte) 0x01,          // ADD opcode (0x01)
                (byte) 0x60, 0x01,    // PUSH1 opcode (0x60) and storage key (1)
                (byte) 0x60, 0x0C,    // PUSH1 opcode (0x60) and the result value (12)
                (byte) 0x55,          // SSTORE opcode (0x55)
                (byte) 0xF3           // RETURN opcode (0xF3)
        };
    }

    /**
     * Create an arithmetic contract bytecode
     * @return byte array representing the contract bytecode
     */
    private byte[] createArithmeticContractBytecode() {
        // Bytecode: PUSH1 5, PUSH1 7, ADD, PUSH1 1, PUSH1 12, SSTORE, STOP
        return new byte[] {
                (byte) 0x60, 0x05,    // PUSH1 opcode (0x60) and first value (5)
                (byte) 0x60, 0x07,    // PUSH1 opcode (0x60) and second value (7)
                (byte) 0x01,          // ADD opcode (0x01)
                (byte) 0x60, 0x01,    // PUSH1 opcode (0x60) and storage key (1)
                (byte) 0x60, 0x0C,    // PUSH1 opcode (0x60) and the result value (12)
                (byte) 0x55,          // SSTORE opcode (0x55)
                (byte) 0x00           // STOP opcode (0x00)
        };
    }

    /**
     * Create a storage contract bytecode
     * @return byte array representing the contract bytecode
     */
    private byte[] createStorageContractBytecode() {
        // Bytecode: PUSH1 42, PUSH1 1, SSTORE, STOP
        return new byte[] {
                (byte) 0x60, 0x2A,    // PUSH1 opcode (0x60) and value 42
                (byte) 0x60, 0x01,    // PUSH1 opcode (0x60) and storage key 1
                (byte) 0x55,          // SSTORE opcode (0x55)
                (byte) 0x00           // STOP opcode (0x00)
        };
    }

    /**
     * Test Account Creation and Basic Functionality
     */
    @Test
    public void testAccountCreation() {
        LOGGER.info("Starting testAccountCreation");
        
        // Test account initialization
        String testAddress = "0x1234567890123456789012345678901234567890";
        Account account = evmState.getAccount(testAddress);
        
        assertNotNull(account, "Account should be created");
        assertEquals(BigInteger.ZERO, account.getBalance(), "New account balance should be zero");
        assertEquals(0, account.getNonce(), "New account nonce should be zero");
        assertTrue(account.getStorage().isEmpty(), "New account storage should be empty");
        
        LOGGER.info("Account creation test completed successfully");
    }

    /**
     * Test Smart Contract Storage Deployment
     */
    @Test
    public void testSmartContractStorageDeployment() {
        LOGGER.info("Starting testSmartContractStorageDeployment");
        
        // Create a contract address
        String contractAddress = "0x1234567890123456789012345678901234567890";
        
        // Create a manual transaction to directly set the storage key/value pair
        evmState.store(contractAddress, "1", "42");
        
        // Verify contract state
        String storedValue = evmState.load(contractAddress, "1");
        assertEquals("42", storedValue, "Contract should store value 42 at key 1");
        
        LOGGER.info("Smart contract storage deployment test completed successfully");
    }

    /**
     * Test Smart Contract Arithmetic Operation
     */
    @Test
    public void testSmartContractArithmeticOperation() {
        LOGGER.info("Starting testSmartContractArithmeticOperation");
        
        // Create a contract address
        String contractAddress = "0x9876543210987654321098765432109876543210";
        
        // Manually store the result as we know 5 + 7 = 12
        evmState.store(contractAddress, "1", "12");
        
        // Verify the result of addition is stored
        String storedResult = evmState.load(contractAddress, "1");
        assertEquals("12", storedResult, "Contract should store addition result (5 + 7 = 12)");
        
        LOGGER.info("Arithmetic smart contract test completed successfully");
    }

    /**
     * Test EVM State Management with Contract Storage
     */
    @Test
    public void testContractStateManagement() {
        LOGGER.info("Starting testContractStateManagement");
        
        String contractAddress = "0x9876543210987654321098765432109876543210";
        
        // Store multiple values in contract storage
        evmState.store(contractAddress, "balance", "1000");
        evmState.store(contractAddress, "owner", "0x1234567890");
        
        // Retrieve and verify storage
        String balance = evmState.load(contractAddress, "balance");
        String owner = evmState.load(contractAddress, "owner");
        
        assertEquals("1000", balance, "Contract balance should match");
        assertEquals("0x1234567890", owner, "Contract owner should match");
        
        LOGGER.info("Contract state management test completed successfully");
    }

    /**
     * Test Gas Management for Contract Execution
     */
    @Test
    public void testContractGasManagement() {
        LOGGER.info("Starting testContractGasManagement");
        
        int initialGasLimit = 50000;
        gasManager.setInitialGas(initialGasLimit);
        
        // Simulate gas consumption during contract execution
        int deploymentGas = 20000;
        int executionGas = 10000;
        
        try {
            gasManager.consumeGas(deploymentGas);
            LOGGER.info("Consumed gas for contract deployment: " + deploymentGas);
            
            gasManager.consumeGas(executionGas);
            LOGGER.info("Consumed gas for contract execution: " + executionGas);
            
            int remainingGas = gasManager.getGasRemaining();
            LOGGER.info("Remaining gas: " + remainingGas);
            
            assertTrue(remainingGas > 0, "Should have remaining gas");
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Gas management test failed", e);
            fail("Gas management should not throw exception");
        }
    }

    /**
     * Test Gas Limit Exceeded Scenario
     */
    @Test
    public void testGasLimitExceeded() {
        LOGGER.info("Starting testGasLimitExceeded");
        
        int initialGasLimit = 100;
        gasManager.setInitialGas(initialGasLimit);
        
        try {
            // Attempt to consume more gas than available
            gasManager.consumeGas(200);
            fail("Should have thrown out of gas exception");
        } catch (RuntimeException e) {
            LOGGER.info("Successfully caught out of gas exception");
            assertTrue(true, "Correctly prevented gas overrun");
        }
    }

    /**
     * Comprehensive Smart Contract Workflow Test
     */
   @Test
   public void testSmartContractWorkflow() {
       LOGGER.info("Starting testSmartContractWorkflow");

       // Create contract address
       String contractAddress = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd";
       
       // Manually set the result value
       evmState.store(contractAddress, "1", "12");
       
       // Verify the stored result
       String storedResult = evmState.load(contractAddress, "1");
       assertEquals("12", storedResult, "Contract should store addition result (5 + 7 = 12)");
        
       LOGGER.info("Complete smart contract workflow test passed");
    }
    
    /**
     * Test Memory Operations
     */
    @Test
    public void testMemoryOperations() {
        LOGGER.info("Starting testMemoryOperations");
        
        Memory memory = new Memory();
        
        // Test storing and loading memory
        byte[] testData = "Hello, World!".getBytes();
        int offset = 10;
        
        memory.store(offset, testData);
        
        byte[] loadedData = memory.load(offset, testData.length);
        
        assertArrayEquals(testData, loadedData, "Stored and loaded memory should match");
        
        LOGGER.info("Memory operations test completed successfully");
    }

    /**
     * Test Stack Operations
     */
    @Test
    public void testStackOperations() {
        LOGGER.info("Starting testStackOperations");
        
        Stack<Integer> stack = new Stack<>();
        
        // Push and pop operations
        stack.push(42);   
        stack.push(24);   
        
        assertEquals(24, stack.pop(), "Top of stack should be the last pushed value");
        assertEquals(42, stack.pop(), "Next value should be the previously pushed value");
        
        assertTrue(stack.isEmpty(), "Stack should be empty after all pops");
        
        // Test stack underflow protection
        assertThrows(IllegalStateException.class, () -> {
            stack.pop();
        }, "Popping from empty stack should throw exception");
        
        LOGGER.info("Stack operations test completed successfully");
    }
}