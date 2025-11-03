package org.example.app.core.token;

/**
 * Interface for ERC20 compatible tokens
 */
public interface ERC20Standard extends TokenStandard {
    /**
     * Gets the balance of the given account
     * 
     * @param accountAddress The account address
     * @return The account balance
     */
    long balanceOf(String accountAddress);
    
    /**
     * Transfers tokens to a recipient
     * 
     * @param from The sender address
     * @param to The recipient address
     * @param amount The amount to transfer
     * @return True if the transfer was successful
     */
    boolean transfer(String from, String to, long amount);
    
    /**
     * Approves spending of tokens by a spender
     * 
     * @param owner The token owner
     * @param spender The address allowed to spend
     * @param amount The amount approved for spending
     * @return True if the approval was successful
     */
    boolean approve(String owner, String spender, long amount);
    
    /**
     * Transfers tokens from one address to another
     * 
     * @param spender The address spending the tokens
     * @param from The address tokens are spent from
     * @param to The recipient address
     * @param amount The amount to transfer
     * @return True if the transfer was successful
     */
    boolean transferFrom(String spender, String from, String to, long amount);
    
    /**
     * Gets the amount approved for spending
     * 
     * @param owner The token owner
     * @param spender The address allowed to spend
     * @return The amount approved for spending
     */
    long allowance(String owner, String spender);
    
    /**
     * Gets the number of decimal places for token amounts
     */
    int getDecimals();
}