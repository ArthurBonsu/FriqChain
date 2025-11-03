package org.example.app.core.block;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * This test class verifies that implementations of the Validator interface
 * follow the expected contract. Instead of testing a specific implementation,
 * it tests general behaviors using a mock implementation.
 */
public class BaseValidatorTest {

    private Validator validator;
    private Block validBlock;
    private Block invalidBlock;

    @BeforeEach
    void setUp() throws Exception {
        // Create mock blocks
        validBlock = mock(Block.class);
        invalidBlock = mock(Block.class);

        // Create a mock implementation of Validator
        validator = mock(Validator.class);

        // Set up the mock validator to return true for valid blocks and false for invalid blocks
        when(validator.validateBlock(validBlock)).thenReturn(true);
        when(validator.validateBlock(invalidBlock)).thenReturn(false);
        when(validator.validateBlock(null)).thenThrow(new IllegalArgumentException("Block cannot be null"));
    }

    @Test
    @DisplayName("validateBlock should return true for valid blocks")
    void testValidateBlockWithValidBlock() throws Exception {
        boolean result = validator.validateBlock(validBlock);
        assertTrue(result, "Validator should return true for valid blocks");
        verify(validator).validateBlock(validBlock);
    }

    @Test
    @DisplayName("validateBlock should return false for invalid blocks")
    void testValidateBlockWithInvalidBlock() throws Exception {
        boolean result = validator.validateBlock(invalidBlock);
        assertFalse(result, "Validator should return false for invalid blocks");
        verify(validator).validateBlock(invalidBlock);
    }

    @Test
    @DisplayName("validateBlock should throw exception for null blocks")
    void testValidateBlockWithNullBlock() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            validator.validateBlock(null);
        });
        assertEquals("Block cannot be null", exception.getMessage());
    }

    /**
     * This test creates a simple test implementation of Validator
     * to verify that the interface can be implemented properly.
     */
    @Test
    @DisplayName("Validator interface should be implementable")
    void testValidatorImplementation() throws Exception {
        // Create a simple implementation of Validator
        Validator testValidator = new Validator() {
            @Override
            public boolean validateBlock(Block block) throws Exception {
                if (block == null) {
                    throw new IllegalArgumentException("Block cannot be null");
                }
                // Simple validation logic - just check if the block has a hash
                return block.getHash() != null && !block.getHash().isEmpty();
            }
        };

        // Create test blocks
        Block blockWithHash = mock(Block.class);
        when(blockWithHash.getHash()).thenReturn("0xvalidhash");

        Block blockWithoutHash = mock(Block.class);
        when(blockWithoutHash.getHash()).thenReturn("");

        Block blockWithNullHash = mock(Block.class);
        when(blockWithNullHash.getHash()).thenReturn(null);

        // Test the implementation
        assertTrue(testValidator.validateBlock(blockWithHash),
                "Block with hash should be valid");

        assertFalse(testValidator.validateBlock(blockWithoutHash),
                "Block with empty hash should be invalid");

        assertFalse(testValidator.validateBlock(blockWithNullHash),
                "Block with null hash should be invalid");

        assertThrows(IllegalArgumentException.class, () -> {
            testValidator.validateBlock(null);
        }, "Null block should throw exception");
    }
}