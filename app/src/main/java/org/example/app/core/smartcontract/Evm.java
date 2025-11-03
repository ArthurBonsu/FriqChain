package org.example.app.core.smartcontract;

import org.example.app.core.block.Transaction;
import org.example.app.core.token.ERC20Standard;
import org.example.app.core.token.TokenRegistry;

import java.util.logging.Logger;
import java.util.logging.Level;

public class Evm {
    private static final Logger LOGGER = Logger.getLogger(Evm.class.getName());

    private final Stack<Integer> stack = new Stack<>();
    private final Memory memory = new Memory();
    private final GasManager gasManager = new GasManager();
    private final EvmState state;
    private int pc = 0; // Program Counter
    private boolean running = true;

    // Enum for Opcodes
    public enum Opcode {
        STOP(0x00, 0),
        ADD(0x01, 3),
        MUL(0x02, 5),
        SUB(0x03, 3),
        DIV(0x04, 5),
        PUSH1(0x60, 3),
        SLOAD(0x54, 2100),
        SSTORE(0x55, 20000),
        RETURN(0xF3, 0),
        JUMP(0x56, 8),
        JUMPI(0x57, 10);

        private final int value;
        private final int gasCost;

        Opcode(int value, int gasCost) {
            this.value = value;
            this.gasCost = gasCost;
        }

        public static Opcode fromValue(byte value) {
            for (Opcode opcode : values()) {
                if (opcode.value == (value & 0xFF)) {
                    return opcode;
                }
            }
            throw new IllegalArgumentException("Unknown opcode: " + String.format("0x%02X", value));
        }

        public int getGasCost() {
            return gasCost;
        }
    }

    public Evm(EvmState state) {
        this.state = state;
    }

    public GasManager getGasManager() {
        return gasManager;
    }

    public void execute(byte[] bytecode, Transaction tx) {
        // Validate inputs
        if (bytecode == null || bytecode.length == 0) {
            throw new IllegalArgumentException("Bytecode cannot be null or empty");
        }

        // Reset EVM state
        stack.clear();
        pc = 0;
        running = true;

        // Set initial gas
        int gasLimit = tx.getGasLimit();
        gasManager.setInitialGas(gasLimit);

        LOGGER.info("Starting execution of bytecode with length: " + bytecode.length + " for transaction with recipient: " + tx.getRecipient());

        while (running && pc < bytecode.length) {
            try {
                // Fetch the next opcode byte
                Opcode opcode = Opcode.fromValue(bytecode[pc]);
                LOGGER.fine("Executing opcode: " + opcode + " at PC: " + pc);
                pc++;

                // Consume gas for the operation
                gasManager.consumeGas(opcode.getGasCost());

                // Process opcode
                processOpcode(opcode, bytecode, tx);

                // Debug stack state
                LOGGER.fine("Stack size after opcode: " + stack.size());

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "EVM Execution Error at PC: " + pc, e);
                running = false;
                throw new RuntimeException("EVM Execution Failed at PC " + pc + ": " + e.getMessage(), e);
            }
        }

        LOGGER.info("Execution completed. Remaining gas: " + gasManager.getGasRemaining());
    }

    private void processOpcode(Opcode opcode, byte[] bytecode, Transaction tx) {
        switch (opcode) {
            case STOP:
                LOGGER.fine("STOP: Halting execution");
                running = false;
                break;

            case PUSH1:
                executePush1(bytecode);
                break;

            case ADD:
                executeAdd();
                break;

            case MUL:
                executeMul();
                break;

            case SUB:
                executeSub();
                break;

            case DIV:
                executeDiv();
                break;

            case SSTORE:
                executeSStore(tx);
                break;

            case SLOAD:
                executeSLoad(tx);
                break;

            case RETURN:
                LOGGER.fine("RETURN: End of execution");
                running = false;
                break;

            case JUMP:
                executeJump();
                break;

            case JUMPI:
                executeConditionalJump();
                break;

            default:
                throw new IllegalArgumentException("Unsupported opcode: " + opcode);
        }
    }

    private void executePush1(byte[] bytecode) {
        if (pc >= bytecode.length) {
            throw new RuntimeException("Incomplete PUSH1 instruction");
        }
        int value = bytecode[pc] & 0xFF;
        stack.push(value);
        LOGGER.fine("PUSH1: Pushed value " + value);
        pc++;
    }

    private void executeAdd() {
        if (stack.size() < 2) {
            throw new RuntimeException("Insufficient stack elements for ADD operation");
        }
        int b = stack.pop();
        int a = stack.pop();
        int result = a + b;
        stack.push(result);
        LOGGER.fine("ADD: " + a + " + " + b + " = " + result);
    }

    private void executeMul() {
        if (stack.size() < 2) {
            throw new RuntimeException("Insufficient stack elements for MUL operation");
        }
        int b = stack.pop();
        int a = stack.pop();
        int result = a * b;
        stack.push(result);
        LOGGER.fine("MUL: " + a + " * " + b + " = " + result);
    }

    private void executeSub() {
        if (stack.size() < 2) {
            throw new RuntimeException("Insufficient stack elements for SUB operation");
        }
        int b = stack.pop();
        int a = stack.pop();
        int result = a - b;
        stack.push(result);
        LOGGER.fine("SUB: " + a + " - " + b + " = " + result);
    }

    private void executeDiv() {
        if (stack.size() < 2) {
            throw new RuntimeException("Insufficient stack elements for DIV operation");
        }
        int b = stack.pop();
        if (b == 0) {
            throw new RuntimeException("Division by zero");
        }
        int a = stack.pop();
        int result = a / b;
        stack.push(result);
        LOGGER.fine("DIV: " + a + " / " + b + " = " + result);
    }

    private void executeSStore(Transaction tx) {
        if (stack.size() < 2) {
            throw new RuntimeException("Insufficient stack elements for SSTORE operation");
        }

        // SSTORE takes two arguments from the stack:
        // First pop is the value to store
        int value = stack.pop();
        // Second pop is the key/location where to store it
        int key = stack.pop();

        // Convert to strings for storage
        String keyStr = String.valueOf(key);
        String valueStr = String.valueOf(value);

        // Get the contract address from the transaction
        String contractAddress = tx.getRecipient();

        LOGGER.info("SSTORE: Storing value " + valueStr + " at key " + keyStr + " in contract " + contractAddress);

        // Store in the state
        state.store(contractAddress, keyStr, valueStr);
    }

    private void executeSLoad(Transaction tx) {
        if (stack.isEmpty()) {
            throw new RuntimeException("Insufficient stack elements for SLOAD operation");
        }

        // SLOAD takes one argument from the stack: the key to load
        int key = stack.pop();
        String keyStr = String.valueOf(key);

        // Get the contract address from the transaction
        String contractAddress = tx.getRecipient();

        // Load the value from the state
        String valueStr = state.load(contractAddress, keyStr);
        LOGGER.info("SLOAD: Loading from key " + keyStr + " in contract " + contractAddress + ", found value: " + valueStr);

        // Convert the value to an integer and push it onto the stack
        int value = 0;
        try {
            value = Integer.parseInt(valueStr);
        } catch (NumberFormatException e) {
            LOGGER.warning("SLOAD: Failed to parse value '" + valueStr + "' as integer, using 0");
        }

        stack.push(value);
    }

    private void executeJump() {
        if (stack.isEmpty()) {
            throw new RuntimeException("Insufficient stack elements for JUMP operation");
        }
        pc = stack.pop();
        LOGGER.fine("JUMP: Jumping to PC " + pc);
    }

    private void executeConditionalJump() {
        if (stack.size() < 2) {
            throw new RuntimeException("Insufficient stack elements for JUMPI operation");
        }
        int condition = stack.pop();
        int destination = stack.pop();

        if (condition != 0) {
            pc = destination;
            LOGGER.fine("JUMPI: Condition true, jumping to PC " + pc);
        } else {
            LOGGER.fine("JUMPI: Condition false, continuing execution");
        }
    }


    // Add these methods to Evm.java to support token operations

public boolean executeTokenTransfer(String tokenAddress, String from, String to, long amount) {
    // Get the token from the registry
    ERC20Standard token = TokenRegistry.getInstance().getERC20Token(tokenAddress);
    if (token == null) {
        LOGGER.warning("Token not found: " + tokenAddress);
        return false;
    }
    
    // Execute the transfer
    return token.transfer(from, to, amount);
}

public long getTokenBalance(String tokenAddress, String accountAddress) {
    // Get the token from the registry
    ERC20Standard token = TokenRegistry.getInstance().getERC20Token(tokenAddress);
    if (token == null) {
        LOGGER.warning("Token not found: " + tokenAddress);
        return 0;
    }
    
    // Get the balance
    return token.balanceOf(accountAddress);
}
}