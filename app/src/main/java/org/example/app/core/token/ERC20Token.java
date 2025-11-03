package org.example.app.core.token;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.example.app.core.smartcontract.EventLog;
import org.example.app.core.token.TokenEvent;
import org.example.app.core.token.TokenEventEmitter;
import org.example.app.core.token.ERC20Standard;

/**
 * Implementation of the ERC20 token standard
 */
public class ERC20Token implements ERC20Standard {
    private static final Logger LOGGER = Logger.getLogger(ERC20Token.class.getName());
    
    private final String name;
    private final String symbol;
    private final int decimals;
    private final String contractAddress;
    private long totalSupply;
    private final boolean mintable;
    private final String creator; // Store creator address for reference
    
    // Balances mapping: address -> balance
    private final Map<String, Long> balances = new HashMap<>();
    
    // Allowances mapping: owner -> (spender -> amount)
    private final Map<String, Map<String, Long>> allowances = new HashMap<>();
    
    // Event emitter for token events
    private final TokenEventEmitter eventEmitter;
    
    /**
     * Creates a new ERC20 token
     * 
     * @param name Token name
     * @param symbol Token symbol
     * @param decimals Number of decimal places
     * @param initialSupply Initial token supply
     * @param contractAddress Contract address
     * @param mintable Whether the token is mintable
     * @param creator Address of the token creator
     */
    public ERC20Token(String name, String symbol, int decimals, long initialSupply, 
                    String contractAddress, boolean mintable, String creator) {
        this.name = name;
        this.symbol = symbol;
        this.decimals = decimals;
        this.contractAddress = contractAddress;
        this.totalSupply = initialSupply;
        this.mintable = mintable;
        this.creator = normalizeAddress(creator); // Store normalized creator address
        this.eventEmitter = new TokenEventEmitter();
        
        // Assign initial supply to creator
        if (initialSupply > 0 && creator != null) {
            LOGGER.info("Assigning initial supply to creator: " + this.creator);
            balances.put(this.creator, initialSupply);
            
            // Emit transfer event from zero address
            eventEmitter.emitTransfer(
                "0x0000000000000000000000000000000000000000",
                this.creator,
                initialSupply,
                contractAddress
            );
            
            LOGGER.info(String.format(
                "Created token %s (%s) with initial supply %d to %s", 
                name, symbol, initialSupply, this.creator
            ));
        }
    }
    
    /**
     * Normalizes an address to ensure consistent comparisons
     * 
     * @param address The address to normalize
     * @return The normalized address
     */
    private String normalizeAddress(String address) {
        if (address == null) return "";
        
        String normalized = address.toLowerCase();
        // Remove 0x prefix if present for consistent comparison
        if (normalized.startsWith("0x")) {
            normalized = normalized.substring(2);
        }
        
        LOGGER.fine("Normalized address: " + address + " -> " + normalized);
        return normalized;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getSymbol() {
        return symbol;
    }
    
    @Override
    public int getDecimals() {
        return decimals;
    }
    
    @Override
    public long getTotalSupply() {
        return totalSupply;
    }
    
    @Override
    public boolean isMintable() {
        return mintable;
    }
    
    @Override
    public String getContractAddress() {
        return contractAddress;
    }
    
    /**
     * Gets the creator address of this token
     */
    public String getCreator() {
        return creator;
    }
    
    @Override
    public long balanceOf(String accountAddress) {
        String normalizedAddress = normalizeAddress(accountAddress);
        LOGGER.fine("Getting balance for: " + accountAddress + " (normalized: " + normalizedAddress + ")");
        return balances.getOrDefault(normalizedAddress, 0L);
    }
    
    @Override
    public boolean transfer(String from, String to, long amount) {
        // Validate parameters
        if (from == null || to == null || amount <= 0) {
            LOGGER.warning("Invalid transfer parameters");
            return false;
        }
        
        // Normalize addresses
        String normalizedFrom = normalizeAddress(from);
        String normalizedTo = normalizeAddress(to);
        
        // Check balance
        long fromBalance = balanceOf(normalizedFrom);
        if (fromBalance < amount) {
            LOGGER.warning(String.format(
                "Insufficient balance: %s has %d but tried to transfer %d", 
                from, fromBalance, amount
            ));
            return false;
        }
        
        // Update balances
        balances.put(normalizedFrom, fromBalance - amount);
        balances.put(normalizedTo, balanceOf(normalizedTo) + amount);
        
        // Emit transfer event
        eventEmitter.emitTransfer(from, to, amount, contractAddress);
        
        LOGGER.info(String.format(
            "Transferred %d tokens from %s to %s", 
            amount, from, to
        ));
        
        return true;
    }
    
    @Override
    public boolean approve(String owner, String spender, long amount) {
        // Validate parameters
        if (owner == null || spender == null || amount < 0) {
            LOGGER.warning("Invalid approval parameters");
            return false;
        }
        
        // Normalize addresses
        String normalizedOwner = normalizeAddress(owner);
        String normalizedSpender = normalizeAddress(spender);
        
        // Update allowance
        Map<String, Long> ownerAllowances = allowances.computeIfAbsent(normalizedOwner, k -> new HashMap<>());
        ownerAllowances.put(normalizedSpender, amount);
        
        // Emit approval event
        eventEmitter.emitApproval(owner, spender, amount, contractAddress);
        
        LOGGER.info(String.format(
            "Approved %d tokens from %s to be spent by %s", 
            amount, owner, spender
        ));
        
        return true;
    }
    
    @Override
    public boolean transferFrom(String spender, String from, String to, long amount) {
        // Validate parameters
        if (spender == null || from == null || to == null || amount <= 0) {
            LOGGER.warning("Invalid transferFrom parameters");
            return false;
        }
        
        // Normalize addresses
        String normalizedSpender = normalizeAddress(spender);
        String normalizedFrom = normalizeAddress(from);
        String normalizedTo = normalizeAddress(to);
        
        // Check balance
        long fromBalance = balanceOf(normalizedFrom);
        if (fromBalance < amount) {
            LOGGER.warning(String.format(
                "Insufficient balance: %s has %d but tried to transfer %d", 
                from, fromBalance, amount
            ));
            return false;
        }
        
        // Check allowance
        long spenderAllowance = allowance(normalizedFrom, normalizedSpender);
        if (spenderAllowance < amount) {
            LOGGER.warning(String.format(
                "Insufficient allowance: %s is allowed to spend %d from %s but tried to transfer %d", 
                spender, spenderAllowance, from, amount
            ));
            return false;
        }
        
        // Update balances
        balances.put(normalizedFrom, fromBalance - amount);
        balances.put(normalizedTo, balanceOf(normalizedTo) + amount);
        
        // Update allowance
        Map<String, Long> fromAllowances = allowances.get(normalizedFrom);
        fromAllowances.put(normalizedSpender, spenderAllowance - amount);
        
        // Emit transfer event
        eventEmitter.emitTransfer(from, to, amount, contractAddress);
        
        LOGGER.info(String.format(
            "Transferred %d tokens from %s to %s by %s", 
            amount, from, to, spender
        ));
        
        return true;
    }
    
    @Override
    public long allowance(String owner, String spender) {
        // Normalize addresses
        String normalizedOwner = normalizeAddress(owner);
        String normalizedSpender = normalizeAddress(spender);
        
        Map<String, Long> ownerAllowances = allowances.getOrDefault(normalizedOwner, new HashMap<>());
        return ownerAllowances.getOrDefault(normalizedSpender, 0L);
    }
    
    /**
     * Mints new tokens and assigns them to an account
     * 
     * @param minter Address with minting permission
     * @param to Recipient of the minted tokens
     * @param amount Amount to mint
     * @return True if minting was successful
     */
    public boolean mint(String minter, String to, long amount) {
        // Check if token is mintable
        if (!mintable) {
            LOGGER.warning("Token is not mintable");
            return false;
        }
        
        // Validate parameters
        if (minter == null || to == null || amount <= 0) {
            LOGGER.warning("Invalid mint parameters");
            return false;
        }
        
        // Normalize addresses
        String normalizedTo = normalizeAddress(to);
        
        // Update total supply and recipient balance
        totalSupply += amount;
        balances.put(normalizedTo, balanceOf(normalizedTo) + amount);
        
        // Emit transfer event from zero address
        eventEmitter.emitTransfer(
            "0x0000000000000000000000000000000000000000",
            to,
            amount,
            contractAddress
        );
        
        LOGGER.info(String.format(
            "Minted %d tokens to %s by %s", 
            amount, to, minter
        ));
        
        return true;
    }
    
    /**
     * Clears all token balances (for testing purposes)
     */
    public void clearAllTokens() {
        balances.clear();
        allowances.clear();
        LOGGER.info("All token balances and allowances cleared");
    }
}