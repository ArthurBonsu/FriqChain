package org.example.app.core.ui.token;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;

import org.example.app.core.token.TokenBuilder;
import org.example.app.core.token.ERC20Token;
import org.example.app.core.token.TokenRegistry;
import org.example.app.core.token.TokenStandard;
import org.example.app.core.token.ERC20Standard;
import org.example.app.core.block.Transaction;
import org.example.app.core.wallet.Wallet;
import org.example.app.core.mempool.TransactionPool;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.logging.Logger;

/**
 * TokenController - Handles token operations and UI logic
 * Based on working test files: RealNetworkTokenTest.java and TokenTest.java
 */
public class TokenController {
    private static final Logger LOGGER = Logger.getLogger(TokenController.class.getName());
    
    private final TokenRegistry tokenRegistry;
    private final TokenManager tokenManager;
    private final DecimalFormat decimalFormat;
    private TransactionPool transactionPool;
    private Wallet currentWallet;
    
    public TokenController() {
        this.tokenRegistry = TokenRegistry.getInstance();
        this.tokenManager = new TokenManager();
        this.decimalFormat = new DecimalFormat("#,###");
        
        LOGGER.info("TokenController initialized successfully");
    }
    
    /**
     * Sets the transaction pool for blockchain integration
     */
    public void setTransactionPool(TransactionPool transactionPool) {
        this.transactionPool = transactionPool;
        this.tokenManager.setTransactionPool(transactionPool);
    }
    
    /**
     * Sets the current wallet for token operations
     */
    public void setCurrentWallet(Wallet wallet) {
        this.currentWallet = wallet;
        this.tokenManager.setCurrentWallet(wallet);
    }
    
    /**
     * Creates a new ERC20 token based on user input
     * Integrates with real blockchain components from test files
     */
    public boolean createToken(String name, String symbol, int decimals, 
                              long initialSupply, boolean mintable) {
        try {
            LOGGER.info("Creating new token: " + name + " (" + symbol + ")");
            
            // Generate contract address using blockchain state (from RealNetworkTokenTest)
            String contractAddress = tokenManager.generateContractAddress();
            
            // Get creator address from current wallet
            String creator = getCurrentCreatorAddress();
            
            // Create token using TokenBuilder (from test files)
            TokenBuilder builder = new TokenBuilder()
                .name(name)
                .symbol(symbol)
                .decimals(decimals)
                .initialSupply(initialSupply)
                .contractAddress(contractAddress)
                .mintable(mintable)
                .creator(creator);
            
            ERC20Token token = builder.buildERC20();
            
            // Create blockchain transaction for token deployment (from RealNetworkTokenTest)
            if (currentWallet != null && transactionPool != null) {
                Transaction deployTx = tokenManager.createTokenDeploymentTransaction(
                    token, currentWallet, contractAddress
                );
                
                // Add to transaction pool
                transactionPool.addTransaction(deployTx);
                LOGGER.info("Token deployment transaction added to mempool: " + deployTx.getHash());
            }
            
            LOGGER.info("Token created successfully: " + token.getName() + " at " + contractAddress);
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Error creating token: " + e.getMessage());
            return false;
        }
    }
    
// Add to TokenController.java (this helps your UI)
// Add to TokenController.java (this helps your UI)
private boolean isValidAddress(String address) {
    if (address == null || address.trim().isEmpty()) {
        return false;
    }
    
    // Normalize by removing 0x prefix
    String normalized = address.toLowerCase().replace("0x", "");
    
    // Allow standard Ethereum addresses (40 hex chars)
    // For testing environment, allow shorter addresses too
    showErrorAlert("Invalid from address");
    return normalized.matches("^[0-9a-f]{40}$") || 
           (normalized.matches("^[0-9a-f]+$") && normalized.length() >= 8);
}


    /**
     * Transfers tokens between addresses
     * Based on transfer logic from RealNetworkTokenTest
     */
   /**
 * Transfers tokens between addresses with enhanced validation
 */
public boolean transferTokens(String tokenAddress, String fromAddress, String toAddress, long amount) {
    try {
        LOGGER.info("Transferring " + amount + " tokens from " + fromAddress + " to " + toAddress);
        
        // Add validation before transfer (prevents UI crashes)
        if (!validateTransferParams(tokenAddress, fromAddress, toAddress, amount)) {
            return false;
        }
        
        // Get token from registry
        ERC20Standard token = tokenRegistry.getERC20Token(tokenAddress);
        if (token == null) {
            LOGGER.warning("Token not found: " + tokenAddress);
            showErrorAlert("Token not found: " + tokenAddress);
            return false;
        }
        
        // Check balance
        long balance = token.balanceOf(fromAddress);
        if (balance < amount) {
            LOGGER.warning("Insufficient balance: " + balance + " < " + amount);
            showErrorAlert("Insufficient balance: " + balance + " but trying to transfer " + amount);
            return false;
        }
        
        // Perform transfer using your proven ERC20 method
        boolean success = token.transfer(fromAddress, toAddress, amount);
        
        if (success && currentWallet != null && transactionPool != null) {
            // Create blockchain transaction for the transfer (from RealNetworkTokenTest)
            Transaction transferTx = currentWallet.createTokenTransferTransaction(
                tokenAddress, toAddress, amount, 1000
            );
            
            // Add to transaction pool
            transactionPool.addTransaction(transferTx);
            LOGGER.info("Token transfer transaction added to mempool: " + transferTx.getHash());
        }
        
        if (success) {
            LOGGER.info("Token transfer completed successfully");
        } else {
            LOGGER.warning("Token transfer failed");
            showErrorAlert("Token transfer failed");
        }
        
        return success;
        
    } catch (Exception e) {
        LOGGER.severe("Error transferring tokens: " + e.getMessage());
        showErrorAlert("Transfer failed: " + e.getMessage());
        return false;
    }
}

/**
 * Validates transfer parameters (prevents UI errors)
 */
private boolean validateTransferParams(String tokenAddress, String fromAddress, String toAddress, long amount) {
    if (tokenAddress == null || tokenAddress.trim().isEmpty()) {
        showErrorAlert("Token contract address is required");
        return false;
    }
    
    if (fromAddress == null || fromAddress.trim().isEmpty()) {
        showErrorAlert("Sender address is required");
        return false;
    }
    
    if (toAddress == null || toAddress.trim().isEmpty()) {
        showErrorAlert("Recipient address is required");
        return false;
    }
    
    if (amount <= 0) {
        showErrorAlert("Transfer amount must be positive");
        return false;
    }
    
    // Prevent self-transfer
    if (normalizeAddress(fromAddress).equals(normalizeAddress(toAddress))) {
        showErrorAlert("Cannot transfer to the same address");
        return false;
    }
    
    // Check if token exists in registry
    if (!tokenRegistry.hasToken(tokenAddress)) {
        showErrorAlert("Token not found in registry");
        return false;
    }
    
    return true;
}

/**
 * Normalizes address for consistent comparison
 */
private String normalizeAddress(String address) {
    if (address == null) return "";
    
    String normalized = address.toLowerCase();
    if (normalized.startsWith("0x")) {
        normalized = normalized.substring(2);
    }
    
    return normalized;
}
    
    /**
     * Approves token spending by another address
     * Based on approval logic from TokenTest
     */
    public boolean approveTokens(String tokenAddress, String ownerAddress, 
                               String spenderAddress, long amount) {
        try {
            LOGGER.info("Approving " + amount + " tokens for " + spenderAddress + " to spend from " + ownerAddress);
            
            ERC20Standard token = tokenRegistry.getERC20Token(tokenAddress);
            if (token == null) {
                LOGGER.warning("Token not found: " + tokenAddress);
                return false;
            }
            
            boolean success = token.approve(ownerAddress, spenderAddress, amount);
            LOGGER.info("Token approval completed: " + success);
            return success;
            
        } catch (Exception e) {
            LOGGER.severe("Error approving tokens: " + e.getMessage());
            return false;
        }
    }
    


    /**
     * Mints new tokens (if token is mintable)
     * Based on minting logic from TokenTest
     */
    public boolean mintTokens(String tokenAddress, String toAddress, long amount) {
        try {
            LOGGER.info("Minting " + amount + " tokens to " + toAddress);
            
            ERC20Token token = (ERC20Token) tokenRegistry.getERC20Token(tokenAddress);
            if (token == null) {
                LOGGER.warning("Token not found: " + tokenAddress);
                return false;
            }
            
            if (!token.isMintable()) {
                LOGGER.warning("Token is not mintable: " + tokenAddress);
                return false;
            }
            
            String minterAddress = getCurrentCreatorAddress();
            boolean success = token.mint(minterAddress, toAddress, amount);
            
            LOGGER.info("Token minting completed: " + success);
            return success;
            
        } catch (Exception e) {
            LOGGER.severe("Error minting tokens: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets token balance for an address
     */
    public long getTokenBalance(String tokenAddress, String accountAddress) {
        try {
            ERC20Standard token = tokenRegistry.getERC20Token(tokenAddress);
            if (token == null) {
                return 0;
            }
            return token.balanceOf(accountAddress);
        } catch (Exception e) {
            LOGGER.warning("Error getting token balance: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Gets all registered tokens
     */
    public Map<String, TokenStandard> getAllTokens() {
        return tokenRegistry.getAllTokens();
    }
    
    /**
     * Gets token allowance
     */
    public long getTokenAllowance(String tokenAddress, String ownerAddress, String spenderAddress) {
        try {
            ERC20Standard token = tokenRegistry.getERC20Token(tokenAddress);
            if (token == null) {
                return 0;
            }
            return token.allowance(ownerAddress, spenderAddress);
        } catch (Exception e) {
            LOGGER.warning("Error getting token allowance: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Gets current creator address from wallet or generates one
     */
    private String getCurrentCreatorAddress() {
        if (currentWallet != null) {
            return currentWallet.getAddress();
        }
        // Fallback: generate a default address
        return "0x" + Long.toHexString(System.currentTimeMillis());
    }
    
    /**
     * Shows success alert
     */
    public void showSuccessAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    /**
     * Shows error alert
     */
    public void showErrorAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    /**
     * Formats token amount for display
     */
    public String formatTokenAmount(long amount) {
        return decimalFormat.format(amount);
    }
    
    /**
     * Validates token creation parameters
     */
   // Enhance your existing validateTokenCreationParams method
public boolean validateTokenCreationParams(String name, String symbol, int decimals, long initialSupply) {
    if (name == null || name.trim().isEmpty()) {
        showErrorAlert("Token name is required");
        return false;
    }
    
    if (symbol == null || symbol.trim().isEmpty()) {
        showErrorAlert("Token symbol is required");
        return false;
    }
    
    if (symbol.length() > 10) {
        showErrorAlert("Token symbol must be 10 characters or less");
        return false;
    }
    
    if (decimals < 0 || decimals > 18) {
        showErrorAlert("Decimals must be between 0 and 18");
        return false;
    }
    
    if (initialSupply < 0) {
        showErrorAlert("Initial supply cannot be negative");
        return false;
    }
    
    return true;
}

}

