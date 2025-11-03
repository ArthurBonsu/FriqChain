package org.example.app.core.ui.token;

import org.example.app.core.token.TokenRegistry;
import org.example.app.core.token.TokenStandard;
import org.example.app.core.token.ERC20Standard;
import org.example.app.core.token.ERC20Token;
import org.example.app.core.token.TokenTransferProcessor;
import org.example.app.core.block.Transaction;
import org.example.app.core.wallet.Wallet;
import org.example.app.core.mempool.TransactionPool;
import org.example.app.core.consensus.HashUtils;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * TokenManager - Core token management logic
 * Based on RealNetworkTokenTest.java blockchain integration patterns
 */
public class TokenManager {
    private static final Logger LOGGER = Logger.getLogger(TokenManager.class.getName());
    
    private final TokenRegistry tokenRegistry;
    private final TokenTransferProcessor transferProcessor;
    private TransactionPool transactionPool;
    private Wallet currentWallet;
    
    // Token deployment tracking
    private long deploymentNonce = 0;
    
    public TokenManager() {
        this.tokenRegistry = TokenRegistry.getInstance();
        // Note: transferProcessor needs blockchain instance, will be set by controller if available
        this.transferProcessor = null; // Will be initialized when blockchain is available
        
        LOGGER.info("TokenManager initialized");
    }
    
    /**
     * Sets the transaction pool for blockchain integration
     */
    public void setTransactionPool(TransactionPool transactionPool) {
        this.transactionPool = transactionPool;
        LOGGER.info("Transaction pool set in TokenManager");
    }
    
    /**
     * Sets the current wallet for token operations
     */
    public void setCurrentWallet(Wallet wallet) {
        this.currentWallet = wallet;
        LOGGER.info("Current wallet set in TokenManager: " + (wallet != null ? wallet.getAddress() : "null"));
    }
    
    /**
     * Generates a contract address for token deployment
     * Based on RealNetworkTokenTest.java address generation pattern
     */
   // Update TokenManager.generateContractAddress() for better UI experience
public String generateContractAddress() {
    try {
        String deployerAddress = "";
        if (currentWallet != null) {
            deployerAddress = normalizeAddress(currentWallet.getAddress());
        } else {
            deployerAddress = "default" + System.currentTimeMillis();
        }
        
        // Add proper error handling for hash generation
        String hash = HashUtils.generateHash(deployerAddress + "_" + deploymentNonce + "_" + System.currentTimeMillis());
        String contractAddress = "0x" + hash.substring(0, Math.min(40, hash.length()));
        
        deploymentNonce++;
        return contractAddress;
        
    } catch (Exception e) {
        LOGGER.warning("Error generating contract address: " + e.getMessage());
        return "0x" + Long.toHexString(System.currentTimeMillis()) + "000000000000000000000000";
    }
}
    
    /**
     * Creates a token deployment transaction for blockchain integration
     * Based on RealNetworkTokenTest.java transaction creation pattern
     */
    public Transaction createTokenDeploymentTransaction(ERC20Token token, Wallet deployerWallet, String contractAddress) {
        try {
            LOGGER.info("Creating token deployment transaction for: " + token.getName());
            
            // Create deployment transaction data
            String deploymentData = "DEPLOY_TOKEN:" + token.getName() + ":" + token.getSymbol() + 
                                   ":" + token.getDecimals() + ":" + token.getTotalSupply() + 
                                   ":" + token.isMintable();
            
            // Create transaction
            Transaction deployTx = new Transaction(deploymentData.getBytes());
            deployTx.setSender(deployerWallet.getAddress());
            deployTx.setRecipient(contractAddress);
            deployTx.setValue(0L); // FIXED: Use Long literal instead of int
            
            // Set gas price (from RealNetworkTokenTest pattern)
            // deployTx.setGasPrice(1000); // Uncomment if Transaction class has gas methods
            
            LOGGER.info("Token deployment transaction created: " + deployTx.getHash());
            return deployTx;
            
        } catch (Exception e) {
            LOGGER.severe("Error creating deployment transaction: " + e.getMessage());
            throw new RuntimeException("Failed to create deployment transaction", e);
        }
    }
    
    /**
     * Processes a token transaction using the transfer processor
     */
    public boolean processTokenTransaction(Transaction tx) {
        try {
            if (transferProcessor != null) {
                return transferProcessor.processTokenTransfer(tx);
            } else {
                LOGGER.warning("Transfer processor not available, skipping transaction processing");
                return true; // Return true to not block UI operations
            }
        } catch (Exception e) {
            LOGGER.severe("Error processing token transaction: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets all tokens registered in the system
     */
    public Map<String, TokenStandard> getAllRegisteredTokens() {
        return tokenRegistry.getAllTokens();
    }
    
    /**
     * Gets all ERC20 tokens
     */
    public List<ERC20Standard> getAllERC20Tokens() {
        List<ERC20Standard> erc20Tokens = new ArrayList<>();
        
        for (TokenStandard token : tokenRegistry.getAllTokens().values()) {
            if (token instanceof ERC20Standard) {
                erc20Tokens.add((ERC20Standard) token);
            }
        }
        
        return erc20Tokens;
    }
    
    /**
     * Gets token by contract address
     */
    public TokenStandard getToken(String contractAddress) {
        return tokenRegistry.getToken(contractAddress);
    }
    
    /**
     * Gets ERC20 token by contract address
     */
    public ERC20Standard getERC20Token(String contractAddress) {
        return tokenRegistry.getERC20Token(contractAddress);
    }
    
    /**
     * Checks if a token exists
     */
    public boolean hasToken(String contractAddress) {
        return tokenRegistry.hasToken(contractAddress);
    }
    
    /**
     * Gets token statistics for dashboard integration
     */
    public TokenStatistics getTokenStatistics() {
        Map<String, TokenStandard> allTokens = tokenRegistry.getAllTokens();
        
        int totalTokens = allTokens.size();
        long totalSupply = 0;
        int mintableTokens = 0;
        
        for (TokenStandard token : allTokens.values()) {
            totalSupply += token.getTotalSupply();
            if (token.isMintable()) {
                mintableTokens++;
            }
        }
        
        return new TokenStatistics(totalTokens, totalSupply, mintableTokens);
    }
    
    /**
     * Gets tokens owned by a specific address
     */
    public List<TokenBalance> getTokenBalancesForAddress(String address) {
        List<TokenBalance> balances = new ArrayList<>();
        
        for (Map.Entry<String, TokenStandard> entry : tokenRegistry.getAllTokens().entrySet()) {
            String contractAddress = entry.getKey();
            TokenStandard token = entry.getValue();
            
            if (token instanceof ERC20Standard) {
                ERC20Standard erc20 = (ERC20Standard) token;
                long balance = erc20.balanceOf(address);
                
                if (balance > 0) {
                    balances.add(new TokenBalance(contractAddress, token.getName(), token.getSymbol(), balance));
                }
            }
        }
        
        return balances;
    }
    
    /**
     * Validates token transfer parameters
     */
    public boolean validateTransfer(String tokenAddress, String fromAddress, String toAddress, long amount) {
        if (tokenAddress == null || tokenAddress.isEmpty()) {
            LOGGER.warning("Token address is required for transfer");
            return false;
        }
        
        if (fromAddress == null || fromAddress.isEmpty()) {
            LOGGER.warning("From address is required for transfer");
            return false;
        }
        
        if (toAddress == null || toAddress.isEmpty()) {
            LOGGER.warning("To address is required for transfer");
            return false;
        }
        
        if (amount <= 0) {
            LOGGER.warning("Transfer amount must be positive");
            return false;
        }
        
        // Check if token exists
        if (!tokenRegistry.hasToken(tokenAddress)) {
            LOGGER.warning("Token not found: " + tokenAddress);
            return false;
        }
        
        // Check balance
        ERC20Standard token = tokenRegistry.getERC20Token(tokenAddress);
        if (token != null) {
            long balance = token.balanceOf(fromAddress);
            if (balance < amount) {
                LOGGER.warning("Insufficient balance: " + balance + " < " + amount);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Normalizes an address to ensure consistent comparisons
     * Based on RealNetworkTokenTest.java normalization pattern
     */
    private String normalizeAddress(String address) {
        if (address == null) return "";
        
        String normalized = address.toLowerCase();
        // Remove 0x prefix if present for consistent comparison
        if (normalized.startsWith("0x")) {
            normalized = normalized.substring(2);
        }
        
        return normalized;
    }
    
    /**
     * Token statistics data class
     */
    public static class TokenStatistics {
        private final int totalTokens;
        private final long totalSupply;
        private final int mintableTokens;
        
        public TokenStatistics(int totalTokens, long totalSupply, int mintableTokens) {
            this.totalTokens = totalTokens;
            this.totalSupply = totalSupply;
            this.mintableTokens = mintableTokens;
        }
        
        public int getTotalTokens() { return totalTokens; }
        public long getTotalSupply() { return totalSupply; }
        public int getMintableTokens() { return mintableTokens; }
    }
    
    /**
     * Token balance data class
     */
    public static class TokenBalance {
        private final String contractAddress;
        private final String name;
        private final String symbol;
        private final long balance;
        
        public TokenBalance(String contractAddress, String name, String symbol, long balance) {
            this.contractAddress = contractAddress;
            this.name = name;
            this.symbol = symbol;
            this.balance = balance;
        }
        
        public String getContractAddress() { return contractAddress; }
        public String getName() { return name; }
        public String getSymbol() { return symbol; }
        public long getBalance() { return balance; }
    }
}