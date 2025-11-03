package org.example.app.core.ui.wallet;

import org.example.app.core.wallet.Wallet;
import org.example.app.core.wallet.WalletStorage;
import org.example.app.core.wallet.TransactionBuilder;
import org.example.app.core.wallet.ContractWallet;
import org.example.app.core.block.Transaction;
import org.example.app.core.token.ERC20Token;
import org.example.app.core.token.TokenBuilder;
import org.example.app.core.token.TokenRegistry;
import org.example.app.core.account.AccountState;
import org.example.app.core.types.Address;
import org.example.app.core.smartcontract.EvmState;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;

public class WalletController {
    private static final Logger LOGGER = Logger.getLogger(WalletController.class.getName());
    
    private final WalletStorage walletStorage;
    private final AccountState accountState;
    private final TokenRegistry tokenRegistry;
    private Map<String, Wallet> loadedWallets;
    private WalletMetrics metrics;
    
    public WalletController() {
        this.walletStorage = new WalletStorage("wallets");
        this.accountState = new AccountState();
        this.tokenRegistry = TokenRegistry.getInstance();
        this.loadedWallets = new HashMap<>();
        this.metrics = new WalletMetrics();
        LOGGER.info("WalletController initialized");
    }
    
    public WalletCreationResult createWallet(String walletId, String password, boolean isContractWallet) {
        try {
            if (walletStorage.walletExists(walletId)) {
                return new WalletCreationResult(false, "Wallet already exists", null);
            }
            
            Wallet newWallet;
            if (isContractWallet) {
                try {
                    EvmState evmState = new EvmState();
                    newWallet = new ContractWallet(evmState);
                } catch (Exception e) {
                    newWallet = new Wallet();
                }
            } else {
                newWallet = new Wallet();
            }
            
            WalletStorage.WalletData walletData = new WalletStorage.WalletData();
            walletData.address = newWallet.getAddress();
            walletData.lastNonce = 0;
            walletData.balances = new HashMap<>();
            walletData.balances.put("TINC", BigInteger.ZERO);
            
            walletStorage.saveWallet(walletId, walletData, password);
            loadedWallets.put(walletId, newWallet);
            metrics.incrementWalletsCreated();
            
            return new WalletCreationResult(true, "Wallet created successfully", 
                new WalletInfo(walletId, newWallet.getAddress(), newWallet.getBalance(), isContractWallet));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to create wallet", e);
            return new WalletCreationResult(false, "Failed to create wallet: " + e.getMessage(), null);
        }
    }
    
    public WalletLoadResult loadWallet(String walletId, String password) {
        try {
            if (!walletStorage.walletExists(walletId)) {
                return new WalletLoadResult(false, "Wallet not found", null);
            }
            
            WalletStorage.WalletData walletData = walletStorage.loadWallet(walletId, password);
            Wallet wallet = new Wallet();
            wallet.updateBalance(walletData.balances.getOrDefault("TINC", BigInteger.ZERO).longValue());
            loadedWallets.put(walletId, wallet);
            
            return new WalletLoadResult(true, "Wallet loaded successfully", 
                new WalletInfo(walletId, walletData.address, wallet.getBalance(), false));
        } catch (Exception e) {
            return new WalletLoadResult(false, "Failed to load wallet: " + e.getMessage(), null);
        }
    }
    
    public TransactionResult createTransaction(String fromWalletId, String toAddress, long amount, long gasPrice, int gasLimit) {
        try {
            Wallet wallet = loadedWallets.get(fromWalletId);
            if (wallet == null) {
                return new TransactionResult(false, "Wallet not loaded", null);
            }
            
            Transaction transaction = wallet.createTransaction(toAddress, amount, gasPrice);
            metrics.incrementTransactionsCreated();
            metrics.addTransactionAmount(amount);
            
            return new TransactionResult(true, "Transaction created successfully", 
                new TransactionInfo(transaction.getHash(), wallet.getAddress(), toAddress, amount, gasPrice));
        } catch (Exception e) {
            return new TransactionResult(false, "Failed to create transaction: " + e.getMessage(), null);
        }
    }
    
    public TransactionResult createTokenTransfer(String fromWalletId, String tokenAddress, String toAddress, long tokenAmount, long gasPrice) {
        try {
            Wallet wallet = loadedWallets.get(fromWalletId);
            if (wallet == null) {
                return new TransactionResult(false, "Wallet not loaded", null);
            }
            
            ERC20Token token = (ERC20Token) tokenRegistry.getToken(tokenAddress);
            if (token == null) {
                return new TransactionResult(false, "Token not found", null);
            }
            
            Transaction tokenTx = wallet.createTokenTransferTransaction(tokenAddress, toAddress, tokenAmount, gasPrice);
            boolean success = token.transfer(wallet.getAddress(), toAddress, tokenAmount);
            
            if (!success) {
                return new TransactionResult(false, "Token transfer failed", null);
            }
            
            return new TransactionResult(true, "Token transfer created successfully", 
                new TransactionInfo(tokenTx.getHash(), wallet.getAddress(), toAddress, tokenAmount, gasPrice));
        } catch (Exception e) {
            return new TransactionResult(false, "Failed to create token transfer: " + e.getMessage(), null);
        }
    }
    
    public TokenCreationResult createToken(String creatorWalletId, String tokenName, String tokenSymbol, int decimals, long initialSupply, boolean mintable) {
        try {
            Wallet creatorWallet = loadedWallets.get(creatorWalletId);
            if (creatorWallet == null) {
                return new TokenCreationResult(false, "Creator wallet not loaded", null);
            }
            
            String contractAddress = "0x" + System.currentTimeMillis();
            
            ERC20Token token = new TokenBuilder()
                .name(tokenName)
                .symbol(tokenSymbol)
                .decimals(decimals)
                .initialSupply(initialSupply)
                .contractAddress(contractAddress)
                .mintable(mintable)
                .creator(creatorWallet.getAddress())
                .buildERC20();
            
            return new TokenCreationResult(true, "Token created successfully", 
                new TokenInfo(contractAddress, tokenName, tokenSymbol, decimals, initialSupply, mintable));
        } catch (Exception e) {
            return new TokenCreationResult(false, "Failed to create token: " + e.getMessage(), null);
        }
    }
    
    public WalletBalanceInfo getWalletBalance(String walletId) {
        try {
            Wallet wallet = loadedWallets.get(walletId);
            if (wallet == null) return null;
            
            WalletBalanceInfo balanceInfo = new WalletBalanceInfo();
            balanceInfo.walletId = walletId;
            balanceInfo.address = wallet.getAddress();
            balanceInfo.nativeBalance = wallet.getBalance();
            balanceInfo.tokenBalances = new HashMap<>();
            
            Map<String, org.example.app.core.token.TokenStandard> allTokens = tokenRegistry.getAllTokens();
            for (Map.Entry<String, org.example.app.core.token.TokenStandard> entry : allTokens.entrySet()) {
                if (entry.getValue() instanceof ERC20Token) {
                    ERC20Token token = (ERC20Token) entry.getValue();
                    long balance = token.balanceOf(wallet.getAddress());
                    if (balance > 0) {
                        balanceInfo.tokenBalances.put(entry.getKey(), balance);
                    }
                }
            }
            return balanceInfo;
        } catch (Exception e) {
            return null;
        }
    }
    
    public List<WalletInfo> listWallets() {
        List<WalletInfo> walletList = new ArrayList<>();
        try {
            String[] walletIds = walletStorage.listWallets();
            for (String walletId : walletIds) {
                Wallet wallet = loadedWallets.get(walletId);
                if (wallet != null) {
                    walletList.add(new WalletInfo(walletId, wallet.getAddress(), wallet.getBalance(), false));
                } else {
                    walletList.add(new WalletInfo(walletId, "Not loaded", 0L, false));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to list wallets", e);
        }
        return walletList;
    }
    
    public boolean deleteWallet(String walletId) {
        try {
            loadedWallets.remove(walletId);
            walletStorage.deleteWallet(walletId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public WalletMetrics getMetrics() {
        return metrics;
    }
    
    // Result classes
    public static class WalletCreationResult {
        public final boolean success;
        public final String message;
        public final WalletInfo walletInfo;
        
        public WalletCreationResult(boolean success, String message, WalletInfo walletInfo) {
            this.success = success;
            this.message = message;
            this.walletInfo = walletInfo;
        }
    }
    
    public static class WalletLoadResult {
        public final boolean success;
        public final String message;
        public final WalletInfo walletInfo;
        
        public WalletLoadResult(boolean success, String message, WalletInfo walletInfo) {
            this.success = success;
            this.message = message;
            this.walletInfo = walletInfo;
        }
    }
    
    public static class TransactionResult {
        public final boolean success;
        public final String message;
        public final TransactionInfo transactionInfo;
        
        public TransactionResult(boolean success, String message, TransactionInfo transactionInfo) {
            this.success = success;
            this.message = message;
            this.transactionInfo = transactionInfo;
        }
    }
    
    public static class TokenCreationResult {
        public final boolean success;
        public final String message;
        public final TokenInfo tokenInfo;
        
        public TokenCreationResult(boolean success, String message, TokenInfo tokenInfo) {
            this.success = success;
            this.message = message;
            this.tokenInfo = tokenInfo;
        }
    }
    
    public static class WalletInfo {
        public final String walletId;
        public final String address;
        public final long balance;
        public final boolean isContractWallet;
        
        public WalletInfo(String walletId, String address, long balance, boolean isContractWallet) {
            this.walletId = walletId;
            this.address = address;
            this.balance = balance;
            this.isContractWallet = isContractWallet;
        }
    }
    
    public static class TransactionInfo {
        public final String hash;
        public final String from;
        public final String to;
        public final long amount;
        public final long gasPrice;
        
        public TransactionInfo(String hash, String from, String to, long amount, long gasPrice) {
            this.hash = hash;
            this.from = from;
            this.to = to;
            this.amount = amount;
            this.gasPrice = gasPrice;
        }
    }
    
    public static class TokenInfo {
        public final String contractAddress;
        public final String name;
        public final String symbol;
        public final int decimals;
        public final long totalSupply;
        public final boolean mintable;
        
        public TokenInfo(String contractAddress, String name, String symbol, int decimals, long totalSupply, boolean mintable) {
            this.contractAddress = contractAddress;
            this.name = name;
            this.symbol = symbol;
            this.decimals = decimals;
            this.totalSupply = totalSupply;
            this.mintable = mintable;
        }
    }
    
    public static class WalletBalanceInfo {
        public String walletId;
        public String address;
        public long nativeBalance;
        public Map<String, Long> tokenBalances;
    }
    
    public static class WalletMetrics {
        private int walletsCreated = 0;
        private int transactionsCreated = 0;
        private long totalTransactionAmount = 0;
        
        public void incrementWalletsCreated() { walletsCreated++; }
        public void incrementTransactionsCreated() { transactionsCreated++; }
        public void addTransactionAmount(long amount) { totalTransactionAmount += amount; }
        
        public int getWalletsCreated() { return walletsCreated; }
        public int getTransactionsCreated() { return transactionsCreated; }
        public long getTotalTransactionAmount() { return totalTransactionAmount; }
    }
}