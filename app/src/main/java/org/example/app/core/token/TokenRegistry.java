package org.example.app.core.token;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.example.app.core.token.ERC20Token;
import org.example.app.core.token.ERC20Standard;
import org.example.app.core.token.TokenStandard;

/**
 * Registry for deployed tokens
 */
public class TokenRegistry {
    private static final Logger LOGGER = Logger.getLogger(TokenRegistry.class.getName());
    
    // Singleton instance
    private static TokenRegistry instance;
    
    // Token registry: contract address -> token
    private final Map<String, TokenStandard> tokens = new HashMap<>();
    
    // Private constructor for singleton
    private TokenRegistry() {}
    
    /**
     * Gets the singleton instance
     */
    public static synchronized TokenRegistry getInstance() {
        if (instance == null) {
            instance = new TokenRegistry();
        }
        return instance;
    }
    
    /**
     * Registers a token in the registry
     * 
     * @param token The token to register
     * @return True if registration was successful
     */
    public boolean registerToken(TokenStandard token) {
        if (token == null) {
            LOGGER.warning("Cannot register null token");
            return false;
        }
        
        String address = token.getContractAddress();
        if (address == null || address.isEmpty()) {
            LOGGER.warning("Token has invalid contract address");
            return false;
        }
        
        if (tokens.containsKey(address)) {
            LOGGER.warning("Token with address " + address + " already registered");
            return false;
        }
        
        tokens.put(address, token);
        
        LOGGER.info(String.format(
            "Registered token %s (%s) at address %s", 
            token.getName(), token.getSymbol(), address
        ));
        
        return true;
    }
    
    /**
     * Gets a token by contract address
     * 
     * @param contractAddress The token contract address
     * @return The token, or null if not found
     */
    public TokenStandard getToken(String contractAddress) {
        return tokens.get(contractAddress);
    }
    
    /**
     * Gets an ERC20 token by contract address
     * 
     * @param contractAddress The token contract address
     * @return The ERC20 token, or null if not found or not an ERC20 token
     */
    public ERC20Standard getERC20Token(String contractAddress) {
        TokenStandard token = getToken(contractAddress);
        if (token instanceof ERC20Standard) {
            return (ERC20Standard) token;
        }
        return null;
    }
    
    /**
     * Checks if a token is registered
     * 
     * @param contractAddress The token contract address
     * @return True if the token is registered
     */
    public boolean hasToken(String contractAddress) {
        return tokens.containsKey(contractAddress);
    }
    
    /**
     * Unregisters a token from the registry
     * 
     * @param contractAddress The token contract address
     * @return True if unregistration was successful
     */
    public boolean unregisterToken(String contractAddress) {
        if (!tokens.containsKey(contractAddress)) {
            LOGGER.warning("Token with address " + contractAddress + " not registered");
            return false;
        }
        
        TokenStandard token = tokens.remove(contractAddress);
        
        LOGGER.info(String.format(
            "Unregistered token %s (%s) from address %s", 
            token.getName(), token.getSymbol(), contractAddress
        ));
        
        return true;
    }
    
    /**
     * Gets all registered tokens
     */
    public Map<String, TokenStandard> getAllTokens() {
        return new HashMap<>(tokens);
    }

    /**
 * Clears all registered tokens (for testing purposes)
 */
public void clearAllTokens() {
    tokens.clear();
    LOGGER.info("All tokens cleared from registry");
}
}