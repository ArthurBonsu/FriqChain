package org.example.app.core.token;

/**
 * Base interface for all token standards
 */
public interface TokenStandard {
    /**
     * Gets the token name
     */
    String getName();
    
    /**
     * Gets the token symbol
     */
    String getSymbol();
    
    /**
     * Gets the total supply of tokens
     */
    long getTotalSupply();
    
    /**
     * Checks if the token is mintable
     */
    boolean isMintable();
    
    /**
     * Gets the token contract address
     */
    String getContractAddress();
}