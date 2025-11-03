package org.example.app.core.token;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.example.app.core.token.ERC20Token;
import org.example.app.core.token.TokenRegistry;
import org.example.app.core.token.ERC20Standard;
import org.example.app.core.token.TokenBuilder;

import java.util.logging.Logger;

public class TokenTest {
    private static final Logger LOGGER = Logger.getLogger(TokenTest.class.getName());
    
    private static final String CREATOR_ADDRESS = "0x1111111111111111111111111111111111111111";
    private static final String RECIPIENT_ADDRESS = "0x2222222222222222222222222222222222222222";
    private static final String TOKEN_ADDRESS = "0x3333333333333333333333333333333333333333";
    
    private ERC20Token token;
    
    @BeforeEach
    public void setUp() {
        LOGGER.info("Setting up token test...");
        // Create a test token
        token = new TokenBuilder()
            .name("Test Token")
            .symbol("TST")
            .decimals(18)
            .initialSupply(1000000)
            .contractAddress(TOKEN_ADDRESS)
            .mintable(true)
            .creator(CREATOR_ADDRESS)
            .buildERC20();
        LOGGER.info("Test token created with address: " + TOKEN_ADDRESS);
    }
    
    @AfterEach
    public void tearDown() {
        LOGGER.info("Cleaning up token test...");
        // Unregister the token
        TokenRegistry.getInstance().unregisterToken(TOKEN_ADDRESS);
        LOGGER.info("Test token unregistered from registry");
    }
    
    @Test
    public void testTokenCreation() {
        LOGGER.info("Testing token creation...");
        assertEquals("Test Token", token.getName());
        assertEquals("TST", token.getSymbol());
        assertEquals(18, token.getDecimals());
        assertEquals(1000000, token.getTotalSupply());
        assertEquals(TOKEN_ADDRESS, token.getContractAddress());
        assertTrue(token.isMintable());
        assertEquals(1000000, token.balanceOf(CREATOR_ADDRESS));
        LOGGER.info("Token creation test passed");
    }
    
    @Test
    public void testTokenTransfer() {
        LOGGER.info("Testing token transfer...");
        // Transfer tokens
        boolean success = token.transfer(CREATOR_ADDRESS, RECIPIENT_ADDRESS, 50000);
        
        // Verify the transfer
        assertTrue(success);
        assertEquals(950000, token.balanceOf(CREATOR_ADDRESS));
        assertEquals(50000, token.balanceOf(RECIPIENT_ADDRESS));
        LOGGER.info("Token transfer test passed");
    }
    
    @Test
    public void testTokenApproval() {
        LOGGER.info("Testing token approval...");
        // Approve tokens
        boolean success = token.approve(CREATOR_ADDRESS, RECIPIENT_ADDRESS, 30000);
        
        // Verify the approval
        assertTrue(success);
        assertEquals(30000, token.allowance(CREATOR_ADDRESS, RECIPIENT_ADDRESS));
        LOGGER.info("Token approval test passed");
    }
    
    @Test
    public void testTokenTransferFrom() {
        LOGGER.info("Testing token transferFrom...");
        // Approve tokens
        token.approve(CREATOR_ADDRESS, RECIPIENT_ADDRESS, 40000);
        
        // Transfer tokens using transferFrom
        boolean success = token.transferFrom(RECIPIENT_ADDRESS, CREATOR_ADDRESS, RECIPIENT_ADDRESS, 25000);
        
        // Verify the transfer
        assertTrue(success);
        assertEquals(975000, token.balanceOf(CREATOR_ADDRESS));
        assertEquals(25000, token.balanceOf(RECIPIENT_ADDRESS));
        assertEquals(15000, token.allowance(CREATOR_ADDRESS, RECIPIENT_ADDRESS));
        LOGGER.info("Token transferFrom test passed");
    }
    
    @Test
    public void testTokenMinting() {
        LOGGER.info("Testing token minting...");
        // Mint tokens
        boolean success = token.mint(CREATOR_ADDRESS, RECIPIENT_ADDRESS, 75000);
        
        // Verify the minting
        assertTrue(success);
        assertEquals(1075000, token.getTotalSupply());
        assertEquals(1000000, token.balanceOf(CREATOR_ADDRESS));
        assertEquals(75000, token.balanceOf(RECIPIENT_ADDRESS));
        LOGGER.info("Token minting test passed");
    }
    
    @Test
    public void testTokenRegistry() {
        LOGGER.info("Testing token registry...");
        // Get the token from the registry
        ERC20Standard registeredToken = TokenRegistry.getInstance().getERC20Token(TOKEN_ADDRESS);
        
        // Verify the token
        assertNotNull(registeredToken);
        assertEquals("Test Token", registeredToken.getName());
        assertEquals("TST", registeredToken.getSymbol());
        assertEquals(1000000, registeredToken.getTotalSupply());
        LOGGER.info("Token registry test passed");
    }
}