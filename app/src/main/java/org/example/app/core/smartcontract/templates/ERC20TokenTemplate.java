package org.example.app.core.smartcontract.templates;

import org.example.app.core.smartcontract.ContractABI;
import org.example.app.core.types.Address;
import org.example.app.core.wallet.ContractWallet;

import java.io.IOException;
import java.math.BigInteger;
import java.util.logging.Logger;

/**
 * Standard ERC20 Token Template
 * Provides a simplified interface for deploying and interacting with ERC20 tokens
 */
public class ERC20TokenTemplate {
    private static final Logger LOGGER = Logger.getLogger(ERC20TokenTemplate.class.getName());
    
    // Standard ERC20 contract template in Solidity
    private static final String ERC20_TEMPLATE = 
            "// SPDX-License-Identifier: MIT\n" +
            "pragma solidity ^0.8.0;\n" +
            "\n" +
            "contract ERC20Token {\n" +
            "    string public name;\n" +
            "    string public symbol;\n" +
            "    uint8 public decimals;\n" +
            "    uint256 public totalSupply;\n" +
            "\n" +
            "    mapping(address => uint256) private balances;\n" +
            "    mapping(address => mapping(address => uint256)) private allowances;\n" +
            "\n" +
            "    event Transfer(address indexed from, address indexed to, uint256 value);\n" +
            "    event Approval(address indexed owner, address indexed spender, uint256 value);\n" +
            "\n" +
            "    constructor(string memory _name, string memory _symbol, uint8 _decimals, uint256 _initialSupply) {\n" +
            "        name = _name;\n" +
            "        symbol = _symbol;\n" +
            "        decimals = _decimals;\n" +
            "        totalSupply = _initialSupply * (10 ** decimals);\n" +
            "        balances[msg.sender] = totalSupply;\n" +
            "        emit Transfer(address(0), msg.sender, totalSupply);\n" +
            "    }\n" +
            "\n" +
            "    function balanceOf(address account) public view returns (uint256) {\n" +
            "        return balances[account];\n" +
            "    }\n" +
            "\n" +
            "    function transfer(address to, uint256 amount) public returns (bool) {\n" +
            "        require(balances[msg.sender] >= amount, \"Insufficient balance\");\n" +
            "        \n" +
            "        balances[msg.sender] -= amount;\n" +
            "        balances[to] += amount;\n" +
            "        emit Transfer(msg.sender, to, amount);\n" +
            "        return true;\n" +
            "    }\n" +
            "\n" +
            "    function allowance(address owner, address spender) public view returns (uint256) {\n" +
            "        return allowances[owner][spender];\n" +
            "    }\n" +
            "\n" +
            "    function approve(address spender, uint256 amount) public returns (bool) {\n" +
            "        allowances[msg.sender][spender] = amount;\n" +
            "        emit Approval(msg.sender, spender, amount);\n" +
            "        return true;\n" +
            "    }\n" +
            "\n" +
            "    function transferFrom(address from, address to, uint256 amount) public returns (bool) {\n" +
            "        require(balances[from] >= amount, \"Insufficient balance\");\n" +
            "        require(allowances[from][msg.sender] >= amount, \"Insufficient allowance\");\n" +
            "        \n" +
            "        balances[from] -= amount;\n" +
            "        balances[to] += amount;\n" +
            "        allowances[from][msg.sender] -= amount;\n" +
            "        \n" +
            "        emit Transfer(from, to, amount);\n" +
            "        return true;\n" +
            "    }\n" +
            "}\n";
    
    private final ContractWallet wallet;
    private String contractAddress;
    
    public ERC20TokenTemplate(ContractWallet wallet) {
        this.wallet = wallet;
    }
    
    /**
     * Deploy a new ERC20 token
     * 
     * @param name Token name
     * @param symbol Token symbol
     * @param decimals Token decimals
     * @param initialSupply Initial token supply
     * @return Address of the deployed token contract
     * @throws IOException If deployment fails
     */
    public String deployToken(String name, String symbol, int decimals, long initialSupply) throws IOException {
        LOGGER.info("Deploying ERC20 token: " + name + " (" + symbol + ")");
        
        // Deploy the contract with constructor parameters
        Object[] constructorArgs = {
            name,
            symbol,
            decimals,
            BigInteger.valueOf(initialSupply)
        };
        
        contractAddress = wallet.deployContract(ERC20_TEMPLATE, "ERC20Token", constructorArgs);
        return contractAddress;
    }
    
    /**
     * Get the token name
     */
    public String getName() {
        assertContractDeployed();
        Object[] result = wallet.callContractFunction(contractAddress, "name", new Object[0]);
        return (String) result[0];
    }
    
    /**
     * Get the token symbol
     */
    public String getSymbol() {
        assertContractDeployed();
        Object[] result = wallet.callContractFunction(contractAddress, "symbol", new Object[0]);
        return (String) result[0];
    }
    
    /**
     * Get the token decimals
     */
    public int getDecimals() {
        assertContractDeployed();
        Object[] result = wallet.callContractFunction(contractAddress, "decimals", new Object[0]);
        return ((BigInteger) result[0]).intValue();
    }
    
    /**
     * Get the total token supply
     */
    public BigInteger getTotalSupply() {
        assertContractDeployed();
        Object[] result = wallet.callContractFunction(contractAddress, "totalSupply", new Object[0]);
        return (BigInteger) result[0];
    }
    
    /**
     * Get the balance of an address
     */
    public BigInteger balanceOf(String address) {
        assertContractDeployed();
        Object[] result = wallet.callContractFunction(contractAddress, "balanceOf", new Object[] { address });
        return (BigInteger) result[0];
    }
    
    /**
     * Transfer tokens to an address
     */
    public boolean transfer(String to, BigInteger amount) {
        assertContractDeployed();
        Object[] result = wallet.callContractFunction(contractAddress, "transfer", new Object[] { to, amount });
        return (Boolean) result[0];
    }
    
    /**
     * Approve an address to spend tokens
     */
    public boolean approve(String spender, BigInteger amount) {
        assertContractDeployed();
        Object[] result = wallet.callContractFunction(contractAddress, "approve", new Object[] { spender, amount });
        return (Boolean) result[0];
    }
    
    /**
     * Get the allowance from owner to spender
     */
    public BigInteger allowance(String owner, String spender) {
        assertContractDeployed();
        Object[] result = wallet.callContractFunction(contractAddress, "allowance", new Object[] { owner, spender });
        return (BigInteger) result[0];
    }
    
    /**
     * Transfer tokens from one address to another
     */
    public boolean transferFrom(String from, String to, BigInteger amount) {
        assertContractDeployed();
        Object[] result = wallet.callContractFunction(
            contractAddress, 
            "transferFrom", 
            new Object[] { from, to, amount }
        );
        return (Boolean) result[0];
    }
    
    /**
     * Check if the contract is deployed
     */
    private void assertContractDeployed() {
        if (contractAddress == null) {
            throw new IllegalStateException("ERC20 token contract is not deployed");
        }
    }
    
    /**
     * Get the contract address
     */
    public String getContractAddress() {
        return contractAddress;
    }
}