package org.example.app.core.token;

import org.example.app.core.token.TokenRegistry;
import java.util.logging.Logger;

/**
 * Builder for creating tokens
 */
public class TokenBuilder {
    private static final Logger LOGGER = Logger.getLogger(TokenBuilder.class.getName());
    
    private String name;
    private String symbol;
    private int decimals = 18; // Default
    private long initialSupply = 0;
    private String contractAddress;
    private boolean mintable = false;
    private String creator;
    
    /**
     * Sets the token name
     */
    public TokenBuilder name(String name) {
        this.name = name;
        return this;
    }
    
    /**
     * Sets the token symbol
     */
    public TokenBuilder symbol(String symbol) {
        this.symbol = symbol;
        return this;
    }
    
    /**
     * Sets the number of decimal places
     */
    public TokenBuilder decimals(int decimals) {
        this.decimals = decimals;
        return this;
    }
    
    /**
     * Sets the initial token supply
     */
    public TokenBuilder initialSupply(long initialSupply) {
        this.initialSupply = initialSupply;
        return this;
    }
    
    /**
     * Sets the contract address
     */
    public TokenBuilder contractAddress(String contractAddress) {
        this.contractAddress = contractAddress;
        return this;
    }
    
    /**
     * Sets whether the token is mintable
     */
    public TokenBuilder mintable(boolean mintable) {
        this.mintable = mintable;
        return this;
    }
    
    /**
     * Sets the token creator
     */
    public TokenBuilder creator(String creator) {
        this.creator = creator;
        return this;
    }
    
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
        
        LOGGER.fine("TokenBuilder normalized address: " + address + " -> " + normalized);
        return normalized;
    }
    
    /**
     * Builds an ERC20 token and registers it
     */
    public ERC20Token buildERC20() {
        validate();
        
        LOGGER.info("Building ERC20 token: " + name + 
                    " with creator address: " + creator + 
                    " (normalized: " + normalizeAddress(creator) + ")");
        
        ERC20Token token = new ERC20Token(
            name,
            symbol,
            decimals,
            initialSupply,
            contractAddress,
            mintable,
            creator // The ERC20Token constructor will normalize this
        );
        
        // Register the token
        TokenRegistry.getInstance().registerToken(token);
        
        return token;
    }
    
    /**
     * Validates the token parameters
     */
    private void validate() {
        if (name == null || name.isEmpty()) {
            throw new IllegalStateException("Token name is required");
        }
        
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalStateException("Token symbol is required");
        }
        
        if (decimals < 0 || decimals > 18) {
            throw new IllegalStateException("Decimals must be between 0 and 18");
        }
        
        if (initialSupply < 0) {
            throw new IllegalStateException("Initial supply cannot be negative");
        }
        
        if (contractAddress == null || contractAddress.isEmpty()) {
            throw new IllegalStateException("Contract address is required");
        }
        
        if (creator == null || creator.isEmpty()) {
            throw new IllegalStateException("Creator address is required");
        }
    }
}