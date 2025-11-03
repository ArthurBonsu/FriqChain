// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

/**
 * @title SimpleStorage
 * @dev Store & retrieve a value in the blockchain
 */
contract SimpleStorage {
    // State variable to store a number
    uint256 private storedValue;
    
    // Event emitted when the stored value is changed
    event ValueChanged(uint256 newValue);
    
    /**
     * @dev Store a new value
     * @param value The new value to store
     */
    function store(uint256 value) public {
        storedValue = value;
        emit ValueChanged(value);
    }
    
    /**
     * @dev Return the stored value
     * @return The stored value
     */
    function retrieve() public view returns (uint256) {
        return storedValue;
    }
    
    /**
     * @dev Increment the stored value by 1
     */
    function increment() public {
        storedValue += 1;
        emit ValueChanged(storedValue);
    }
    
    /**
     * @dev Decrement the stored value by 1
     */
    function decrement() public {
        require(storedValue > 0, "Value cannot be negative");
        storedValue -= 1;
        emit ValueChanged(storedValue);
    }
}