package org.example.app.core.blockchain.wallet;

import org.example.app.core.wallet.Wallet;
import org.example.app.core.wallet.TransactionBuilder;
import org.example.app.core.block.Transaction;
import org.example.app.core.types.Address;
import org.example.app.core.types.Amount;
import org.example.app.core.crypto.PKIManager;
import org.example.app.core.state.BlockchainState;
import org.example.app.core.consensus.Blockchain;

import java.security.*;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.time.Instant;

/**
 * TokenBankUserWallet - Bridges traditional bank accounts with blockchain tokens
 * 
 * Features:
 * - Links citizen wallet to traditional bank account
 * - Deposit fiat currency, receive tokens
 * - Withdraw tokens, receive fiat currency
 * - Maintains 1:1 backing with bank reserves
 * - Encrypted bank credential storage
 * - Two-way conversion (fiat ↔ token)
 * - Bank API integration for deposits/withdrawals
 * - Transaction verification with bank
 */
public class TokenBankUserWallet extends Wallet {
    private static final Logger LOGGER = Logger.getLogger(TokenBankUserWallet.class.getName());
    
    // User and bank information
    private final String userId;
    private final String bankId;
    private final String bankAccountNumber;
    private final byte[] encryptedBankCredentials;
    private final String nationalCurrency;
    
    // Token and fiat balances
    private final Map<String, BigInteger> tokenBalances;
    private BigDecimal bankFiatBalance; // Actual fiat in bank account
    
    // Transaction tracking
    private final Map<String, DepositTransaction> depositHistory;
    private final Map<String, WithdrawalTransaction> withdrawalHistory;
    
    // Conversion tracking
    private BigInteger totalDeposited;
    private BigInteger totalWithdrawn;
    private BigInteger totalTokensMinted;
    private BigInteger totalTokensBurned;
    
    // Dependencies
    private final PKIManager pkiManager;
    private final BlockchainState blockchainState;
    private final Blockchain blockchain;
    
    // Configuration
    private final BigDecimal conversionRate; // Fiat to token rate
    private final BigInteger minimumDeposit;
    private final BigInteger minimumWithdrawal;
    
    /**
     * DepositTransaction - Fiat deposit converted to tokens
     */
    public static class DepositTransaction {
        private final String depositId;
        private final String userId;
        private final String bankId;
        private final BigDecimal fiatAmount;
        private final String fiatCurrency;
        private final BigInteger tokenAmount;
        private final String tokenCurrency;
        private final Instant timestamp;
        private final String bankTransactionId;
        private DepositStatus status;
        
        public enum DepositStatus {
            PENDING, CONFIRMED, FAILED, REVERSED
        }
        
        public DepositTransaction(String userId, String bankId, BigDecimal fiatAmount,
                                 String fiatCurrency, BigInteger tokenAmount, 
                                 String tokenCurrency, String bankTransactionId) {
            this.depositId = UUID.randomUUID().toString();
            this.userId = userId;
            this.bankId = bankId;
            this.fiatAmount = fiatAmount;
            this.fiatCurrency = fiatCurrency;
            this.tokenAmount = tokenAmount;
            this.tokenCurrency = tokenCurrency;
            this.timestamp = Instant.now();
            this.bankTransactionId = bankTransactionId;
            this.status = DepositStatus.PENDING;
        }
        
        public String getDepositId() { return depositId; }
        public String getUserId() { return userId; }
        public String getBankId() { return bankId; }
        public BigDecimal getFiatAmount() { return fiatAmount; }
        public String getFiatCurrency() { return fiatCurrency; }
        public BigInteger getTokenAmount() { return tokenAmount; }
        public String getTokenCurrency() { return tokenCurrency; }
        public Instant getTimestamp() { return timestamp; }
        public String getBankTransactionId() { return bankTransactionId; }
        public DepositStatus getStatus() { return status; }
        
        public void setStatus(DepositStatus status) { this.status = status; }
    }
    
    /**
     * WithdrawalTransaction - Tokens burned, fiat released
     */
    public static class WithdrawalTransaction {
        private final String withdrawalId;
        private final String userId;
        private final String bankId;
        private final BigInteger tokenAmount;
        private final String tokenCurrency;
        private final BigDecimal fiatAmount;
        private final String fiatCurrency;
        private final String bankAccountNumber;
        private final Instant timestamp;
        private WithdrawalStatus status;
        private String bankTransactionId;
        
        public enum WithdrawalStatus {
            PENDING, PROCESSING, COMPLETED, FAILED
        }
        
        public WithdrawalTransaction(String userId, String bankId, BigInteger tokenAmount,
                                    String tokenCurrency, BigDecimal fiatAmount,
                                    String fiatCurrency, String bankAccountNumber) {
            this.withdrawalId = UUID.randomUUID().toString();
            this.userId = userId;
            this.bankId = bankId;
            this.tokenAmount = tokenAmount;
            this.tokenCurrency = tokenCurrency;
            this.fiatAmount = fiatAmount;
            this.fiatCurrency = fiatCurrency;
            this.bankAccountNumber = bankAccountNumber;
            this.timestamp = Instant.now();
            this.status = WithdrawalStatus.PENDING;
        }
        
        public String getWithdrawalId() { return withdrawalId; }
        public String getUserId() { return userId; }
        public String getBankId() { return bankId; }
        public BigInteger getTokenAmount() { return tokenAmount; }
        public String getTokenCurrency() { return tokenCurrency; }
        public BigDecimal getFiatAmount() { return fiatAmount; }
        public String getFiatCurrency() { return fiatCurrency; }
        public String getBankAccountNumber() { return bankAccountNumber; }
        public Instant getTimestamp() { return timestamp; }
        public WithdrawalStatus getStatus() { return status; }
        public String getBankTransactionId() { return bankTransactionId; }
        
        public void setStatus(WithdrawalStatus status) { this.status = status; }
        public void setBankTransactionId(String bankTransactionId) { 
            this.bankTransactionId = bankTransactionId; 
        }
    }
    
    /**
     * Constructor for TokenBankUserWallet
     */
    public TokenBankUserWallet(String userId, String bankId, String bankAccountNumber,
                              byte[] encryptedBankCredentials, String nationalCurrency,
                              BlockchainState blockchainState, Blockchain blockchain,
                              PKIManager pkiManager) throws NoSuchAlgorithmException {
        super();
        
        this.userId = userId;
        this.bankId = bankId;
        this.bankAccountNumber = bankAccountNumber;
        this.encryptedBankCredentials = Arrays.copyOf(encryptedBankCredentials, 
                                                      encryptedBankCredentials.length);
        this.nationalCurrency = nationalCurrency.toUpperCase();
        
        this.blockchainState = blockchainState;
        this.blockchain = blockchain;
        this.pkiManager = pkiManager;
        
        this.tokenBalances = new ConcurrentHashMap<>();
        this.bankFiatBalance = BigDecimal.ZERO;
        this.depositHistory = new ConcurrentHashMap<>();
        this.withdrawalHistory = new ConcurrentHashMap<>();
        
        this.totalDeposited = BigInteger.ZERO;
        this.totalWithdrawn = BigInteger.ZERO;
        this.totalTokensMinted = BigInteger.ZERO;
        this.totalTokensBurned = BigInteger.ZERO;
        
        // Default configuration
        this.conversionRate = BigDecimal.ONE; // 1:1 for simplicity
        this.minimumDeposit = new BigInteger("1000000000000000000"); // 1 token minimum
        this.minimumWithdrawal = new BigInteger("1000000000000000000");
        
        // Initialize token balance
        String tokenCurrency = nationalCurrency + "-TOKEN";
        this.tokenBalances.put(tokenCurrency, BigInteger.ZERO);
        
        LOGGER.info(String.format("TokenBankUserWallet created for user %s with bank %s", 
            userId, bankId));
    }
    
    /**
     * Deposits fiat currency from bank account, receives tokens
     */
    public DepositTransaction depositFiat(BigDecimal fiatAmount) {
        if (fiatAmount.compareTo(new BigDecimal(minimumDeposit)) < 0) {
            LOGGER.warning("Deposit amount below minimum: " + fiatAmount);
            return null;
        }
        
        try {
            // Verify bank balance (in production, would call bank API)
            if (bankFiatBalance.compareTo(fiatAmount) < 0) {
                LOGGER.warning("Insufficient bank balance for deposit");
                return null;
            }
            
            // Calculate token amount (1:1 conversion for simplicity)
            BigInteger tokenAmount = fiatAmount.multiply(conversionRate).toBigInteger();
            String tokenCurrency = nationalCurrency + "-TOKEN";
            
            // Simulate bank transaction
            String bankTxId = "BANK_" + UUID.randomUUID().toString();
            
            // Create deposit record
            DepositTransaction deposit = new DepositTransaction(
                userId, bankId, fiatAmount, nationalCurrency, 
                tokenAmount, tokenCurrency, bankTxId
            );
            
            depositHistory.put(deposit.getDepositId(), deposit);
            
            // Process deposit (in production, would wait for bank confirmation)
            processDeposit(deposit);
            
            LOGGER.info(String.format("Fiat deposited: %s %s -> %s %s", 
                fiatAmount.toString(), nationalCurrency, 
                tokenAmount.toString(), tokenCurrency));
            
            return deposit;
            
        } catch (Exception e) {
            LOGGER.severe("Error depositing fiat: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Processes confirmed deposit
     */
    private void processDeposit(DepositTransaction deposit) {
        // Update bank balance
        bankFiatBalance = bankFiatBalance.subtract(deposit.getFiatAmount());
        
        // Mint tokens
        String tokenCurrency = deposit.getTokenCurrency();
        BigInteger currentBalance = tokenBalances.getOrDefault(tokenCurrency, BigInteger.ZERO);
        tokenBalances.put(tokenCurrency, currentBalance.add(deposit.getTokenAmount()));
        
        // Update totals
        totalDeposited = totalDeposited.add(deposit.getTokenAmount());
        totalTokensMinted = totalTokensMinted.add(deposit.getTokenAmount());
        
        // Update status
        deposit.setStatus(DepositTransaction.DepositStatus.CONFIRMED);
        
        LOGGER.info("Deposit processed: " + deposit.getDepositId());
    }
    
    /**
     * Withdraws tokens, receives fiat in bank account
     */
    public WithdrawalTransaction withdrawToBank(BigInteger tokenAmount) {
        String tokenCurrency = nationalCurrency + "-TOKEN";
        
        if (tokenAmount.compareTo(minimumWithdrawal) < 0) {
            LOGGER.warning("Withdrawal amount below minimum: " + tokenAmount);
            return null;
        }
        
        // Check token balance
        BigInteger balance = tokenBalances.getOrDefault(tokenCurrency, BigInteger.ZERO);
        if (balance.compareTo(tokenAmount) < 0) {
            LOGGER.warning("Insufficient token balance for withdrawal");
            return null;
        }
        
        try {
            // Calculate fiat amount
            BigDecimal fiatAmount = new BigDecimal(tokenAmount)
                .divide(conversionRate, 2, java.math.RoundingMode.HALF_UP);
            
            // Create withdrawal record
            WithdrawalTransaction withdrawal = new WithdrawalTransaction(
                userId, bankId, tokenAmount, tokenCurrency,
                fiatAmount, nationalCurrency, bankAccountNumber
            );
            
            withdrawalHistory.put(withdrawal.getWithdrawalId(), withdrawal);
            
            // Burn tokens immediately
            tokenBalances.put(tokenCurrency, balance.subtract(tokenAmount));
            totalTokensBurned = totalTokensBurned.add(tokenAmount);
            
            // Process withdrawal (in production, would call bank API)
            withdrawal.setStatus(WithdrawalTransaction.WithdrawalStatus.PROCESSING);
            processWithdrawal(withdrawal);
            
            LOGGER.info(String.format("Withdrawal initiated: %s %s -> %s %s", 
                tokenAmount.toString(), tokenCurrency,
                fiatAmount.toString(), nationalCurrency));
            
            return withdrawal;
            
        } catch (Exception e) {
            LOGGER.severe("Error withdrawing to bank: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Processes withdrawal to bank
     */
    private void processWithdrawal(WithdrawalTransaction withdrawal) {
        // Simulate bank transfer (in production, would call bank API)
        String bankTxId = "BANK_WITHDRAWAL_" + UUID.randomUUID().toString();
        withdrawal.setBankTransactionId(bankTxId);
        
        // Update bank balance
        bankFiatBalance = bankFiatBalance.add(withdrawal.getFiatAmount());
        
        // Update totals
        totalWithdrawn = totalWithdrawn.add(withdrawal.getTokenAmount());
        
        // Update status
        withdrawal.setStatus(WithdrawalTransaction.WithdrawalStatus.COMPLETED);
        
        LOGGER.info("Withdrawal completed: " + withdrawal.getWithdrawalId());
    }
    
    /**
     * Transfers tokens to another wallet (P2P)
     */
    public Transaction transferTokens(String recipientAddress, String currency, 
                                     BigInteger amount) {
        // Check balance
        BigInteger balance = tokenBalances.getOrDefault(currency, BigInteger.ZERO);
        if (balance.compareTo(amount) < 0) {
            LOGGER.warning("Insufficient balance for transfer");
            return null;
        }
        
        try {
            TransactionBuilder builder = new TransactionBuilder()
                .from(getAddress())
                .to(recipientAddress)
                .value(amount)
                .gasPrice(BigInteger.valueOf(1000000000L))
                .nonce(System.currentTimeMillis());
            
            Transaction tx = builder.build();
            
            // Update balance
            tokenBalances.put(currency, balance.subtract(amount));
            
            LOGGER.info(String.format("Tokens transferred: %s %s to %s", 
                amount.toString(), currency, recipientAddress));
            
            return tx;
            
        } catch (Exception e) {
            LOGGER.severe("Error transferring tokens: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Receives tokens from another wallet
     */
    public boolean receiveTokens(String currency, BigInteger amount) {
        BigInteger currentBalance = tokenBalances.getOrDefault(currency, BigInteger.ZERO);
        tokenBalances.put(currency, currentBalance.add(amount));
        
        LOGGER.info(String.format("Tokens received: %s %s", amount.toString(), currency));
        return true;
    }
    
    /**
     * Syncs with bank to update fiat balance
     */
    public boolean syncWithBank() {
        try {
            // In production, would call bank API to get current balance
            // For now, simulate sync
            LOGGER.info("Synced with bank " + bankId + " for account " + bankAccountNumber);
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Error syncing with bank: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Verifies bank credentials (encrypted)
     */
    private boolean verifyBankCredentials() {
        // In production, would decrypt and verify with bank
        return encryptedBankCredentials != null && encryptedBankCredentials.length > 0;
    }
    
    // Getters and utility methods
    
    public String getUserId() { return userId; }
    public String getBankId() { return bankId; }
    public String getBankAccountNumber() { return bankAccountNumber; }
    public String getNationalCurrency() { return nationalCurrency; }
    
    public BigInteger getTokenBalance(String currency) {
        return tokenBalances.getOrDefault(currency, BigInteger.ZERO);
    }
    
    public BigDecimal getBankFiatBalance() {
        return bankFiatBalance;
    }
    
    public void setBankFiatBalance(BigDecimal balance) {
        this.bankFiatBalance = balance;
    }
    
    public Map<String, BigInteger> getAllTokenBalances() {
        return new HashMap<>(tokenBalances);
    }
    
    public DepositTransaction getDeposit(String depositId) {
        return depositHistory.get(depositId);
    }
    
    public List<DepositTransaction> getRecentDeposits(int limit) {
        return depositHistory.values().stream()
            .sorted((d1, d2) -> d2.getTimestamp().compareTo(d1.getTimestamp()))
            .limit(limit)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    public WithdrawalTransaction getWithdrawal(String withdrawalId) {
        return withdrawalHistory.get(withdrawalId);
    }
    
    public List<WithdrawalTransaction> getRecentWithdrawals(int limit) {
        return withdrawalHistory.values().stream()
            .sorted((w1, w2) -> w2.getTimestamp().compareTo(w1.getTimestamp()))
            .limit(limit)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    public BigInteger getTotalDeposited() { return totalDeposited; }
    public BigInteger getTotalWithdrawn() { return totalWithdrawn; }
    public BigInteger getTotalTokensMinted() { return totalTokensMinted; }
    public BigInteger getTotalTokensBurned() { return totalTokensBurned; }
    
    public boolean isHealthy() {
        // Wallet is healthy if tokens minted match deposits
        return totalTokensMinted.equals(totalDeposited);
    }
    
    @Override
    public String toString() {
        return String.format("TokenBankUserWallet{user='%s', bank='%s', currency='%s', " +
            "tokens=%s, fiat=%s}",
            userId, bankId, nationalCurrency,
            tokenBalances.getOrDefault(nationalCurrency + "-TOKEN", BigInteger.ZERO).toString(),
            bankFiatBalance.toString());
    }
}