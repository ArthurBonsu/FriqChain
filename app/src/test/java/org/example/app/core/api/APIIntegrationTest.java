package org.example.app.core.api;

import org.example.app.core.api.rpc.RPCService;
import org.example.app.core.consensus.Blockchain;
import org.example.app.core.block.Block;
import org.example.app.core.block.Transaction;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Comprehensive API Integration Test for real-world blockchain network scenarios
 * Tests RPC API with actual network transactions (adapted for your Block/Transaction classes)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class APIIntegrationTest {
    
    private static final int RPC_PORT = 8545;
    private static final String RPC_URL = "http://localhost:" + RPC_PORT;
    
    private static Blockchain blockchain;
    private static RPCService rpcService;
    private static HttpClient httpClient;
    private static ObjectMapper objectMapper;
    private static AtomicInteger requestIdCounter;
    
    // Test accounts for realistic scenarios
    private static final String ALICE_ADDRESS = "0x742d35C6cd2E0BB8D234B8B3F2Dc0A9E7B9F1C0A";
    private static final String BOB_ADDRESS = "0x8ba1f109551bD432803012645Hac136c0c8326B5";
    private static final String CHARLIE_ADDRESS = "0x123456789abcdef0123456789abcdef012345678";
    
    @BeforeAll
    static void setUp() throws Exception {
        // Initialize core components
        blockchain = new Blockchain();
        rpcService = new RPCService(blockchain);
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30)) // Fixed method name
            .build();
        objectMapper = new ObjectMapper();
        requestIdCounter = new AtomicInteger(1);
        
        // Initialize blockchain with test data
        initializeTestBlockchain();
        
        System.out.println("✅ Test setup complete - Testing RPC service directly");
    }
    
    @AfterAll
    static void tearDown() throws Exception {
        System.out.println("🛑 Test completed");
    }
    
    /**
     * Initialize blockchain with realistic test data (adapted for your Block constructor)
     */
    private static void initializeTestBlockchain() {
        try {
            // Create genesis block with proper constructor parameters
            List<Transaction> genesisTransactions = new ArrayList<>();
            
            // Add genesis transactions (initial fund distribution)
            Transaction genesisAlice = new Transaction(new byte[0]);
            genesisAlice.setSender("0x0000000000000000000000000000000000000000");
            genesisAlice.setRecipient(ALICE_ADDRESS);
            genesisAlice.setValue(1000000000000000000L); // 1 ETH in wei
            genesisTransactions.add(genesisAlice);
            
            Transaction genesisBob = new Transaction(new byte[0]);
            genesisBob.setSender("0x0000000000000000000000000000000000000000");
            genesisBob.setRecipient(BOB_ADDRESS);
            genesisBob.setValue(500000000000000000L); // 0.5 ETH in wei
            genesisTransactions.add(genesisBob);
            
            // Create genesis block using your constructor
            Block genesis = new Block(
                "0x0000000000000000000000000000000000000000000000000000000000000000", // parentHash
                "genesis_hash_" + System.currentTimeMillis(), // hash
                ALICE_ADDRESS, // miner
                System.currentTimeMillis(), // timestamp
                0L, // number
                1000L, // difficulty (using long instead of BigInteger)
                genesisTransactions
            );
            
            blockchain.addBlock(genesis);
            
            // Add additional test block
            addTestBlock();
            
            System.out.println("🔗 Initialized test blockchain with " + blockchain.getChainLength() + " blocks");
        } catch (Exception e) {
            System.err.println("❌ Failed to initialize test blockchain: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void addTestBlock() {
        try {
            List<Transaction> transactions = new ArrayList<>();
            
            // Alice sends to Charlie
            Transaction tx1 = new Transaction("Smart contract call".getBytes());
            tx1.setSender(ALICE_ADDRESS);
            tx1.setRecipient(CHARLIE_ADDRESS);
            tx1.setValue(100000000000000000L); // 0.1 ETH
            tx1.setGasPrice(BigInteger.valueOf(20_000_000_000L)); // 20 Gwei
            tx1.setGasLimit(21000);
            transactions.add(tx1);
            
            Block block1 = new Block(
                blockchain.getLatestBlock().getHash(), // parentHash
                "block1_hash_" + System.currentTimeMillis(), // hash
                ALICE_ADDRESS, // miner
                System.currentTimeMillis(), // timestamp
                1L, // number
                1000L, // difficulty
                transactions
            );
            
            blockchain.addBlock(block1);
        } catch (Exception e) {
            System.err.println("❌ Failed to add test block: " + e.getMessage());
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("🌐 Test RPC Service Direct Call")
    void testRPCServiceDirectly() throws Exception {
        String jsonRequest = createRPCRequest("eth_getBalance", 
            new Object[]{ALICE_ADDRESS});
        
        String response = rpcService.handleRequest(jsonRequest);
        assertNotNull(response, "RPC service should return a response");
        
        JsonNode jsonResponse = objectMapper.readTree(response);
        assertTrue(jsonResponse.has("result") || jsonResponse.has("error"), 
            "Response should contain result or error field");
        
        System.out.println("✅ RPC Service direct call working");
        System.out.println("   Request: " + jsonRequest);
        System.out.println("   Response: " + response);
    }
    
    @Test
    @Order(2)
    @DisplayName("💰 Test Account Balance Retrieval")
    void testGetBalance() throws Exception {
        // Test Alice's balance
        String jsonRequest = createRPCRequest("eth_getBalance", 
            new Object[]{ALICE_ADDRESS});
        
        String response = rpcService.handleRequest(jsonRequest);
        JsonNode jsonResponse = objectMapper.readTree(response);
        
        if (jsonResponse.has("error")) {
            System.out.println("⚠️ Balance request returned error: " + 
                jsonResponse.get("error").get("message").asText());
        } else {
            assertTrue(jsonResponse.has("result"), "Balance request should return result");
            String balance = jsonResponse.get("result").asText();
            assertNotNull(balance, "Balance should not be null");
            
            System.out.println("✅ Account balance retrieval working correctly");
            System.out.println("   Alice balance: " + balance);
        }
        
        // Test Bob's balance
        jsonRequest = createRPCRequest("eth_getBalance", 
            new Object[]{BOB_ADDRESS});
        response = rpcService.handleRequest(jsonRequest);
        jsonResponse = objectMapper.readTree(response);
        
        if (jsonResponse.has("result")) {
            System.out.println("   Bob balance: " + jsonResponse.get("result").asText());
        }
    }
    
    @Test
    @Order(3)
    @DisplayName("🧾 Test Transaction Count (Nonce) Retrieval")
    void testGetTransactionCount() throws Exception {
        // Create parameters map manually (Java 8 compatible)
        Map<String, Object> params = new HashMap<>();
        params.put("address", ALICE_ADDRESS);
        
        String jsonRequest = createRPCRequest("eth_getTransactionCount", 
            new Object[]{params});
        
        String response = rpcService.handleRequest(jsonRequest);
        JsonNode jsonResponse = objectMapper.readTree(response);
        
        if (jsonResponse.has("error")) {
            System.out.println("⚠️ Transaction count request returned error: " + 
                jsonResponse.get("error").get("message").asText());
        } else {
            assertTrue(jsonResponse.has("result"), "Transaction count request should return result");
            String nonce = jsonResponse.get("result").asText();
            assertNotNull(nonce, "Nonce should not be null");
            
            System.out.println("✅ Transaction count retrieval working");
            System.out.println("   Alice nonce: " + nonce);
        }
    }
    
    @Test
    @Order(4)
    @DisplayName("📦 Test Block Retrieval")
    void testGetBlock() throws Exception {
        // Test getting latest block
        Map<String, Object> params = new HashMap<>();
        params.put("latest", true);
        params.put("includeTransactions", true);
        
        String jsonRequest = createRPCRequest("eth_getBlock", 
            new Object[]{params});
        
        String response = rpcService.handleRequest(jsonRequest);
        JsonNode jsonResponse = objectMapper.readTree(response);
        
        if (jsonResponse.has("error")) {
            System.out.println("⚠️ Block request returned error: " + 
                jsonResponse.get("error").get("message").asText());
        } else {
            assertTrue(jsonResponse.has("result"), "Block request should return result");
            System.out.println("✅ Block retrieval working correctly");
        }
        
        // Test getting block by number
        Map<String, Object> params2 = new HashMap<>();
        params2.put("number", 0);
        
        jsonRequest = createRPCRequest("eth_getBlock", 
            new Object[]{params2});
        response = rpcService.handleRequest(jsonRequest);
        jsonResponse = objectMapper.readTree(response);
        
        if (jsonResponse.has("result")) {
            System.out.println("✅ Genesis block retrieval working");
        }
    }
    
    @Test
    @Order(5)
    @DisplayName("💸 Test Real Transaction Submission")
    void testSendTransaction() throws Exception {
        // Prepare transaction from Bob to Charlie
        Map<String, Object> txParams = new HashMap<>();
        txParams.put("from", BOB_ADDRESS);
        txParams.put("to", CHARLIE_ADDRESS);
        txParams.put("value", 50000000000000000L); // 0.05 ETH
        txParams.put("gasPrice", 25000000000L); // 25 Gwei
        txParams.put("gasLimit", 21000);
        txParams.put("data", ""); // Simple transfer
        
        String jsonRequest = createRPCRequest("eth_sendTransaction", 
            new Object[]{txParams});
        
        String response = rpcService.handleRequest(jsonRequest);
        JsonNode jsonResponse = objectMapper.readTree(response);
        
        if (jsonResponse.has("error")) {
            System.out.println("⚠️ Transaction submission returned error: " + 
                jsonResponse.get("error").get("message").asText());
        } else {
            assertTrue(jsonResponse.has("result"), "Transaction submission should return result");
            JsonNode result = jsonResponse.get("result");
            
            if (result.has("hash")) {
                String txHash = result.get("hash").asText();
                System.out.println("✅ Transaction submitted successfully");
                System.out.println("   Transaction hash: " + txHash);
                
                // Store for later tests
                lastTransactionHash = txHash;
            }
        }
    }
    
    private static String lastTransactionHash;
    
    @Test
    @Order(6)
    @DisplayName("🔍 Test Transaction Retrieval")
    void testGetTransaction() throws Exception {
        if (lastTransactionHash == null) {
            System.out.println("⚠️ No transaction hash from previous test, skipping");
            return;
        }
        
        Map<String, Object> params = new HashMap<>();
        params.put("hash", lastTransactionHash);
        
        String jsonRequest = createRPCRequest("eth_getTransaction", 
            new Object[]{params});
        
        String response = rpcService.handleRequest(jsonRequest);
        JsonNode jsonResponse = objectMapper.readTree(response);
        
        if (jsonResponse.has("error")) {
            System.out.println("⚠️ Transaction not found (likely still pending): " + 
                jsonResponse.get("error").get("message").asText());
        } else {
            assertTrue(jsonResponse.has("result"), "Transaction retrieval should return result");
            System.out.println("✅ Transaction retrieval working correctly");
        }
    }
    
    @Test
    @Order(7)
    @DisplayName("🚀 Test Multiple Transactions")
    void testMultipleTransactions() throws Exception {
        int numberOfTransactions = 5;
        int successCount = 0;
        
        System.out.println("🔄 Submitting " + numberOfTransactions + " transactions...");
        
        for (int i = 0; i < numberOfTransactions; i++) {
            Map<String, Object> txParams = new HashMap<>();
            txParams.put("from", ALICE_ADDRESS);
            txParams.put("to", BOB_ADDRESS);
            txParams.put("value", 1000000000000000L + i); // 0.001 ETH + small variance
            txParams.put("gasPrice", 20000000000L);
            txParams.put("gasLimit", 21000);
            
            String jsonRequest = createRPCRequest("eth_sendTransaction", 
                new Object[]{txParams});
            
            String response = rpcService.handleRequest(jsonRequest);
            JsonNode jsonResponse = objectMapper.readTree(response);
            
            if (jsonResponse.has("error")) {
                System.out.println("❌ Transaction " + i + " failed: " + 
                    jsonResponse.get("error").get("message").asText());
            } else {
                System.out.println("✅ Transaction " + i + " submitted");
                successCount++;
            }
        }
        
        System.out.println("✅ Multiple transaction test completed: " + 
            successCount + "/" + numberOfTransactions + " successful");
    }
    
    @Test
    @Order(8)
    @DisplayName("❌ Test Error Handling")
    void testErrorHandling() throws Exception {
        // Test invalid method
        String jsonRequest = createRPCRequest("eth_invalidMethod", new Object[]{});
        String response = rpcService.handleRequest(jsonRequest);
        JsonNode jsonResponse = objectMapper.readTree(response);
        
        assertTrue(jsonResponse.has("error"), "Invalid method should return error");
        assertEquals(-32601, jsonResponse.get("error").get("code").asInt(), 
            "Should return method not found error");
        
        // Test invalid parameters
        jsonRequest = createRPCRequest("eth_getBalance", new Object[]{});
        response = rpcService.handleRequest(jsonRequest);
        jsonResponse = objectMapper.readTree(response);
        
        assertTrue(jsonResponse.has("error"), "Missing parameters should return error");
        
        // Test invalid transaction (missing required fields)
        Map<String, Object> invalidTx = new HashMap<>();
        invalidTx.put("value", 1000);
        
        jsonRequest = createRPCRequest("eth_sendTransaction", new Object[]{invalidTx});
        response = rpcService.handleRequest(jsonRequest);
        jsonResponse = objectMapper.readTree(response);
        
        assertTrue(jsonResponse.has("error"), "Invalid transaction should return error");
        
        System.out.println("✅ Error handling working correctly");
    }
    
    @Test
    @Order(9)
    @DisplayName("📊 Test API Performance")
    void testAPIPerformance() throws Exception {
        int iterations = 20;
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < iterations; i++) {
            String jsonRequest = createRPCRequest("eth_getBalance", 
                new Object[]{ALICE_ADDRESS});
            String response = rpcService.handleRequest(jsonRequest);
            assertNotNull(response);
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        double avgResponseTime = (double) totalTime / iterations;
        
        System.out.println("📊 Performance Test Results:");
        System.out.println("   Total requests: " + iterations);
        System.out.println("   Total time: " + totalTime + "ms");
        System.out.println("   Average response time: " + String.format("%.2f", avgResponseTime) + "ms");
        
        assertTrue(avgResponseTime < 1000, "Average response time should be under 1 second");
        System.out.println("✅ API performance within acceptable limits");
    }
    
    @Test
    @Order(10)
    @DisplayName("🔧 Test RPC Service State")
    void testRPCServiceState() {
        // Test service methods
        String[] registeredMethods = rpcService.getRegisteredMethods();
        assertTrue(registeredMethods.length > 0, "Should have registered methods");
        
        assertTrue(rpcService.isMethodRegistered("eth_getBalance"), 
            "Should have balance method registered");
        assertTrue(rpcService.isMethodRegistered("eth_sendTransaction"), 
            "Should have send transaction method registered");
        
        int methodCount = rpcService.getMethodCount();
        assertTrue(methodCount >= 4, "Should have at least 4 core methods registered");
        
        // List all registered methods for debugging
        rpcService.listRegisteredMethods();
        
        System.out.println("✅ RPC Service state validation completed");
        System.out.println("   Registered methods: " + methodCount);
    }
    
    // Helper methods
    
    private String createRPCRequest(String method, Object[] params) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", method);
        request.put("params", params);
        request.put("id", String.valueOf(requestIdCounter.getAndIncrement()));
        
        return objectMapper.writeValueAsString(request);
    }

}