package org.example.app.core.block;

import org.example.app.core.consensus.Blockchain;
import org.example.app.core.state.StateTransition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BaseBlockProcessorTest {

    @Mock
    private Blockchain blockchain;

    @Mock
    private StateTransition stateTransition;

    private BlockProcessor blockProcessor;

    private Block validBlock;
    private Block invalidBlockParentNotFound;
    private Block invalidBlockLowerDifficulty;

    @BeforeEach
    void setUp() {
        // Initialize mocks
        MockitoAnnotations.openMocks(this);

        // Create the BlockProcessor instance to test
        blockProcessor = new BlockProcessor(blockchain, stateTransition);

        // Create test blocks
        String validParentHash = "0xparenthash123";
        String invalidParentHash = "0xinvalidparenthash";
        String minerAddress = "0xminer456";
        long timestamp = System.currentTimeMillis();

        // Create a valid block
        validBlock = new Block(
                "0xvalidblockhash",
                validParentHash,
                minerAddress,
                timestamp,
                2000,  // Higher difficulty
                1,
                new ArrayList<>()
        );

        // Create an invalid block (parent not found)
        invalidBlockParentNotFound = new Block(
                "0xinvalidblockhash1",
                invalidParentHash,  // This parent hash won't be found
                minerAddress,
                timestamp,
                2000,
                2,
                new ArrayList<>()
        );

        // Create an invalid block (lower difficulty)
        invalidBlockLowerDifficulty = new Block(
                "0xinvalidblockhash2",
                validParentHash,
                minerAddress,
                timestamp,
                1000,  // Lower difficulty than parent
                3,
                new ArrayList<>()
        );

        // Create a mock previous block with higher difficulty
        Block mockPreviousBlock = new Block(
                validParentHash,
                "0xgrandparenthash",
                minerAddress,
                timestamp - 1000,
                1500,  // Parent difficulty
                0,
                new ArrayList<>()
        );

        // Set up mock behavior
        when(blockchain.getBlockByHash(validParentHash)).thenReturn(mockPreviousBlock);
        when(blockchain.getBlockByHash(invalidParentHash)).thenReturn(null);
    }

    @Test
    @DisplayName("processBlock should return true and add valid block to blockchain")
    void testProcessValidBlock() {
        // Arrange
        // (Setup is done in setUp method)

        // Act
        boolean result = blockProcessor.processBlock(validBlock);

        // Assert
        assertTrue(result);

        // Verify that stateTransition.applyStateTransition was called with validBlock
        verify(stateTransition).applyStateTransition(validBlock);

        // Verify that blockchain.addBlock was called with validBlock
        verify(blockchain).addBlock(validBlock);
    }

    @Test
    @DisplayName("processBlock should return false for block with unknown parent")
    void testProcessBlockWithUnknownParent() {
        // Arrange
        // (Setup is done in setUp method)

        // Act
        boolean result = blockProcessor.processBlock(invalidBlockParentNotFound);

        // Assert
        assertFalse(result);

        // Verify that stateTransition.applyStateTransition was NOT called
        verify(stateTransition, never()).applyStateTransition(any(Block.class));

        // Verify that blockchain.addBlock was NOT called
        verify(blockchain, never()).addBlock(any(Block.class));
    }

    @Test
    @DisplayName("processBlock should return false for block with lower difficulty")
    void testProcessBlockWithLowerDifficulty() {
        // Arrange
        // (Setup is done in setUp method)

        // Act
        boolean result = blockProcessor.processBlock(invalidBlockLowerDifficulty);

        // Assert
        assertFalse(result);

        // Verify that stateTransition.applyStateTransition was NOT called
        verify(stateTransition, never()).applyStateTransition(any(Block.class));

        // Verify that blockchain.addBlock was NOT called
        verify(blockchain, never()).addBlock(any(Block.class));
    }

    @Test
    @DisplayName("Constructor should properly initialize BlockProcessor")
    void testConstructor() {
        // Create new mocks
        Blockchain newBlockchain = mock(Blockchain.class);
        StateTransition newStateTransition = mock(StateTransition.class);

        // Create new instance
        BlockProcessor newProcessor = new BlockProcessor(newBlockchain, newStateTransition);

        // Test with a valid block to verify internals were set correctly
        Block testBlock = new Block(
                "0xtesthash",
                "0xtestparent",
                "0xtestminer",
                System.currentTimeMillis(),
                1000,
                1,
                new ArrayList<>()
        );

        // Setup mock behavior for the validation check
        when(newBlockchain.getBlockByHash("0xtestparent")).thenReturn(
                new Block(
                        "0xtestparent",
                        "0xgrandparent",
                        "0xtestminer",
                        System.currentTimeMillis() - 1000,
                        900,  // Lower difficulty than test block
                        0,
                        new ArrayList<>()
                )
        );

        // Process the block
        boolean result = newProcessor.processBlock(testBlock);

        // Assert the correct objects were used
        assertTrue(result);
        verify(newBlockchain).getBlockByHash("0xtestparent");
        verify(newStateTransition).applyStateTransition(testBlock);
        verify(newBlockchain).addBlock(testBlock);
    }

    @Test
    @DisplayName("BlockProcessor should handle exceptions from dependencies")
    void testHandleExceptions() {
        // Set up exception-throwing behavior
        doThrow(new RuntimeException("State transition failed"))
                .when(stateTransition).applyStateTransition(validBlock);

        // Wrap the call in try-catch to verify exception handling behavior
        try {
            boolean result = blockProcessor.processBlock(validBlock);

            // If the BlockProcessor swallows exceptions, the method returns false
            // and doesn't call blockchain.addBlock
            assertFalse(result);
            verify(blockchain, never()).addBlock(validBlock);
        } catch (RuntimeException e) {
            // If the BlockProcessor lets exceptions propagate, verify correct exception
            assertEquals("State transition failed", e.getMessage());
        }
    }
}