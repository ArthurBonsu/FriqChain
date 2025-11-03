package org.example.app.core.token;

import org.example.app.core.block.Block;
import org.example.app.core.block.Transaction;
import org.example.app.core.eth.Blockchain;
import org.example.app.core.smartcontract.Evm;
import org.example.app.core.smartcontract.EvmState;
import org.example.app.core.state.StateDB;
import org.example.app.core.state.StateManager;
import org.example.app.core.wallet.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.math.BigInteger;

/**
 * RealNetworkTokenTest - Comprehensive real-world token test
 * Testing actual token deployments and transfers on the blockchain
 */
public class RealNetworkTokenTest {
    private static final Logger LOGGER = Logger.getLogger(RealNetworkTokenTest.class.getName());
    
    // Blockchain components
    private Blockchain blockchain;
    private StateDB stateDB;
    private StateManager stateManager;
    private EvmState evmState;
    private Evm evm;
    
    // Token components
    private TokenRegistry tokenRegistry;
    private TokenTransferProcessor transferProcessor;
    
    // Wallets
    private Wallet adminWallet;
    private Wallet aliceWallet;
    private Wallet bobWallet;
    
    // Token information
    private static final String TOKEN_NAME = "Tinc Test Token";
    private static final String TOKEN_SYMBOL = "TTT";
    private static final int TOKEN_DECIMALS = 18;
    private static final long INITIAL_SUPPLY = 1_000_000L;
    private String tokenContractAddress;
    private ERC20Token testToken;
    
    // Genesis block parameters
    private static final String GENESIS_MINER = "genesis-miner";
    
    /**
     * Normalizes an address to ensure consistent comparisons
     */
    private String normalizeAddress(String address) {
        if (address == null) return "";
        
        String normalized = address.toLowerCase();
        // Remove 0x prefix if present for consistent comparison
        if (normalized.startsWith("0x")) {
            normalized = normalized.substring(2);
        }
        
        LOGGER.fine("Test normalized address: " + address + " -> " + normalized);
        return normalized;
    }
    
    @BeforeEach
    public void setUp() throws Exception {
        LOGGER.info("Setting up RealNetworkTokenTest...");
        
        // Initialize blockchain components
        blockchain = new Blockchain();
        stateDB = new StateDB(); // Create StateDB first
        stateManager = new StateManager(stateDB); // Pass StateDB to StateManager
        evmState = new EvmState();
        evm = new Evm(evmState);
        
        // Initialize token registry
        tokenRegistry = TokenRegistry.getInstance();
        transferProcessor = new TokenTransferProcessor(blockchain);
        
        // Create wallets
        createTestWallets();
        
        // Fund the wallets
        fundWallets();
        
        // Create genesis block
        Block genesisBlock = createGenesisBlock();
        blockchain.addBlock(genesisBlock);
        
        LOGGER.info("Created blockchain with genesis block: " + genesisBlock.getHash());
        LOGGER.info("Test setup complete with admin wallet: " + adminWallet.getAddress());
    }

    @AfterEach
    public void tearDown() {
        LOGGER.info("Cleaning up RealNetworkTokenTest...");
        
        // Unregister token if it exists
        if (tokenContractAddress != null) {
            try {
                tokenRegistry.unregisterToken(tokenContractAddress);
                LOGGER.info("Unregistered token: " + tokenContractAddress);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error unregistering token", e);
            }
        }
        
        // If TokenRegistry has clearAllTokens method, use it
        try {
            tokenRegistry.clearAllTokens();
            LOGGER.info("Cleared all tokens from registry");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error clearing tokens", e);
            
            // Alternative approach: manually clear tokens
            LOGGER.info("Attempting alternative token cleanup...");
            if (testToken != null) {
                testToken.clearAllTokens();
            }
        }
    }
    
    @Test
    @DisplayName("Deploy ERC20 token and perform transfers")
    public void testTokenDeploymentAndTransfers() throws Exception {
        LOGGER.info("Starting token deployment and transfer test...");
        
        // Debug wallet addresses
        LOGGER.info("Admin wallet address (original): " + adminWallet.getAddress());
        LOGGER.info("Alice wallet address (original): " + aliceWallet.getAddress());
        LOGGER.info("Bob wallet address (original): " + bobWallet.getAddress());
        
        LOGGER.info("Admin wallet address (normalized): " + normalizeAddress(adminWallet.getAddress()));
        LOGGER.info("Alice wallet address (normalized): " + normalizeAddress(aliceWallet.getAddress()));
        LOGGER.info("Bob wallet address (normalized): " + normalizeAddress(bobWallet.getAddress()));
        
        // 1. Deploy token
        deployTestToken();
        assertNotNull(testToken, "Token should be deployed successfully");
        assertEquals(TOKEN_NAME, testToken.getName(), "Token name should match");
        assertEquals(TOKEN_SYMBOL, testToken.getSymbol(), "Token symbol should match");
        assertEquals(TOKEN_DECIMALS, testToken.getDecimals(), "Token decimals should match");
        assertEquals(INITIAL_SUPPLY, testToken.getTotalSupply(), "Token supply should match");
        
        LOGGER.info("Token deployed successfully at: " + tokenContractAddress);
        LOGGER.info("Token creator: " + testToken.getCreator());
        
        // 2. Check initial token balances
        LOGGER.info("Initial token balances check:");
        long adminBalance = testToken.balanceOf(adminWallet.getAddress());
        long aliceBalance = testToken.balanceOf(aliceWallet.getAddress());
        long bobBalance = testToken.balanceOf(bobWallet.getAddress());
        
        LOGGER.info("  Admin balance: " + adminBalance);
        LOGGER.info("  Alice balance: " + aliceBalance);
        LOGGER.info("  Bob balance: " + bobBalance);
        
        // Verify initial balances
        assertEquals(INITIAL_SUPPLY, adminBalance, 
                    "Admin should have all initial tokens. Admin address: " + adminWallet.getAddress());
        assertEquals(0, aliceBalance, 
                    "Alice should have no tokens. Alice address: " + aliceWallet.getAddress());
        assertEquals(0, bobBalance, 
                    "Bob should have no tokens. Bob address: " + bobWallet.getAddress());
        
        LOGGER.info("Initial token balances verified - Admin: " + adminBalance);
        
        // 3. Create token transfer transaction
        long transferAmount = 50000;
        LOGGER.info("Creating token transfer transaction from Admin to Alice: " + transferAmount + " tokens");
        
        Transaction tokenTx = adminWallet.createTokenTransferTransaction(
            tokenContractAddress, 
            aliceWallet.getAddress(), 
            transferAmount, 
            1000
        );
        
        // 4. Create and add block with the token transfer
        int lastBlockIndex = blockchain.getHeight() - 1;
        Block latestBlock = blockchain.getBlockByIndex(lastBlockIndex);
        LOGGER.info("Latest block: " + latestBlock.getHash() + " at height: " + lastBlockIndex);
        
        Block tokenTransferBlock = createAndMineBlock(1, latestBlock.getHash(), tokenTx);
        LOGGER.info("Token transfer block mined: " + tokenTransferBlock.getHash());
        
        // Add the block to the blockchain - this was missing in the original test
        blockchain.addBlock(tokenTransferBlock);
        LOGGER.info("Token transfer block added to blockchain");
        
        // 5. Process the transfer transaction
        LOGGER.info("Processing token transfer transaction...");
        boolean processed = transferProcessor.processTokenTransfer(tokenTx);
        assertTrue(processed, "Token transfer should be processed successfully");
        
        // 6. Verify token balances after transfer
        LOGGER.info("Verifying balances after transfer...");
        long newAdminBalance = testToken.balanceOf(adminWallet.getAddress());
        long newAliceBalance = testToken.balanceOf(aliceWallet.getAddress());
        
        LOGGER.info("  Admin balance after transfer: " + newAdminBalance);
        LOGGER.info("  Alice balance after transfer: " + newAliceBalance);
        
        assertEquals(INITIAL_SUPPLY - transferAmount, newAdminBalance, 
            "Admin balance should be reduced by transfer amount");
        assertEquals(transferAmount, newAliceBalance, "Alice should receive tokens");
        
        LOGGER.info("Token balances after transfer - Admin: " + newAdminBalance + ", Alice: " + newAliceBalance);
        
        // 7. Test approval and transferFrom functionality
        LOGGER.info("Testing token approval and transferFrom...");
        long approvalAmount = 20000;
        
        // Alice approves Bob to spend her tokens
        boolean approvalSuccess = testToken.approve(aliceWallet.getAddress(), bobWallet.getAddress(), approvalAmount);
        assertTrue(approvalSuccess, "Approval should be successful");
        
        // Verify approval
        long allowance = testToken.allowance(aliceWallet.getAddress(), bobWallet.getAddress());
        LOGGER.info("Allowance after approval: " + allowance);
        assertEquals(approvalAmount, allowance, "Allowance should match approved amount");
        
        // Bob transfers tokens from Alice to himself
        long transferFromAmount = 10000;
        LOGGER.info("Bob transferring " + transferFromAmount + " tokens from Alice to himself");
        boolean transferFromSuccess = testToken.transferFrom(
            bobWallet.getAddress(), 
            aliceWallet.getAddress(), 
            bobWallet.getAddress(), 
            transferFromAmount
        );
        
        assertTrue(transferFromSuccess, "TransferFrom should be successful");
        
        // Verify balances after transferFrom
        long finalAliceBalance = testToken.balanceOf(aliceWallet.getAddress());
        long finalBobBalance = testToken.balanceOf(bobWallet.getAddress());
        long finalAllowance = testToken.allowance(aliceWallet.getAddress(), bobWallet.getAddress());
        
        LOGGER.info("Final balances and allowances:");
        LOGGER.info("  Alice balance: " + finalAliceBalance);
        LOGGER.info("  Bob balance: " + finalBobBalance);
        LOGGER.info("  Remaining allowance: " + finalAllowance);
        
        assertEquals(transferAmount - transferFromAmount, finalAliceBalance,
            "Alice's balance should be reduced by the transferred amount");
        assertEquals(transferFromAmount, finalBobBalance,
            "Bob should receive the transferred tokens");
        assertEquals(approvalAmount - transferFromAmount, finalAllowance,
            "Allowance should be reduced by the transferred amount");
        
        LOGGER.info("Final token balances - Admin: " + testToken.balanceOf(adminWallet.getAddress()) +
                   ", Alice: " + finalAliceBalance +
                   ", Bob: " + finalBobBalance);
        
        LOGGER.info("Token deployment and transfer test completed successfully");
    }
    
    /**
     * Creates test wallets for the administrators and users
     */
    private void createTestWallets() throws NoSuchAlgorithmException {
        LOGGER.info("Creating test wallets...");
        adminWallet = new Wallet();
        aliceWallet = new Wallet();
        bobWallet = new Wallet();
        
        LOGGER.info("Created wallets with addresses:");
        LOGGER.info("Admin: " + adminWallet.getAddress());
        LOGGER.info("Alice: " + aliceWallet.getAddress());
        LOGGER.info("Bob: " + bobWallet.getAddress());
    }
    
    /**
     * Funds test wallets with initial balances
     */
    private void fundWallets() {
        LOGGER.info("Funding test wallets...");
        
        // In a real scenario, these would come from the state
        // For this test, we'll just update the wallet's internal balance
        adminWallet.updateBalance(10_000_000);
        aliceWallet.updateBalance(1_000_000);
        bobWallet.updateBalance(1_000_000);
        
        // Also update the state manager with these balances - using BigInteger as required
        stateManager.updateBalance(adminWallet.getAddress(), BigInteger.valueOf(10_000_000));
        stateManager.updateBalance(aliceWallet.getAddress(), BigInteger.valueOf(1_000_000));
        stateManager.updateBalance(bobWallet.getAddress(), BigInteger.valueOf(1_000_000));
        
        LOGGER.info("Wallets funded with initial balances:");
        LOGGER.info("Admin: " + adminWallet.getBalance());
        LOGGER.info("Alice: " + aliceWallet.getBalance());
        LOGGER.info("Bob: " + bobWallet.getBalance());
    }
    
    /**
     * Creates a genesis block for the test blockchain
     */
    private Block createGenesisBlock() {
        LOGGER.info("Creating genesis block...");
        
        String genesisHash = "0000000000000000000000000000000000000000000000000000000000000000";
        long timestamp = System.currentTimeMillis();
        
        return new Block(
            genesisHash,  // Hash
            null,         // Parent hash (null for genesis)
            GENESIS_MINER,// Miner
            timestamp,    // Timestamp
            1000,         // Difficulty
            0,            // Block number
            new ArrayList<>() // No transactions in genesis
        );
    }
    
    /**
     * Deploys a test ERC20 token to the blockchain
     */
    private void deployTestToken() {
        LOGGER.info("Deploying test token...");
        
        // Normalize admin address for consistency
        String adminAddress = normalizeAddress(adminWallet.getAddress());
        LOGGER.info("Admin address for token creation (normalized): " + adminAddress);
        
        // Set contract address based on deployer's address
        tokenContractAddress = "0x" + adminAddress + "_token";
        LOGGER.info("Token contract address: " + tokenContractAddress);
        
        // Create the token using TokenBuilder
        testToken = new TokenBuilder()
            .name(TOKEN_NAME)
            .symbol(TOKEN_SYMBOL)
            .decimals(TOKEN_DECIMALS)
            .initialSupply(INITIAL_SUPPLY)
            .contractAddress(tokenContractAddress)
            .mintable(true)
            .creator(adminWallet.getAddress()) // Use original address - normalization happens in ERC20Token
            .buildERC20();
        
        LOGGER.info("Token deployed with name: " + testToken.getName() + 
                   ", symbol: " + testToken.getSymbol() + 
                   ", supply: " + testToken.getTotalSupply());
                   
        // Verify token was registered correctly
        boolean isRegistered = tokenRegistry.hasToken(tokenContractAddress);
        LOGGER.info("Token registered successfully: " + isRegistered);
    }
    
    /**
     * Creates and mines a new block with the given transactions
     */
    private Block createAndMineBlock(long blockNumber, String parentHash, Transaction... transactions) {
        LOGGER.info("Mining block " + blockNumber + " with " + transactions.length + " transactions...");
        
        List<Transaction> txList = new ArrayList<>();
        for (Transaction tx : transactions) {
            txList.add(tx);
        }
        
        String blockHash = calculateBlockHash(parentHash, blockNumber);
        String minerAddress = "miner-" + blockNumber;
        long timestamp = System.currentTimeMillis();
        
        Block block = new Block(
            blockHash,
            parentHash,
            minerAddress,
            timestamp,
            1000,  // Difficulty
            blockNumber,
            txList
        );
        
        LOGGER.info("Block " + blockNumber + " mined with hash: " + blockHash);
        return block;
    }
    
    /**
     * Simple hash calculation for blocks (simplified for testing)
     */
    private String calculateBlockHash(String parentHash, long blockNumber) {
        // In a real implementation, this would calculate a proper hash
        // based on the block header and meet the difficulty requirement
        return "0000000000000000" + Long.toHexString(System.currentTimeMillis()) + 
               Long.toHexString(blockNumber);
    }
}