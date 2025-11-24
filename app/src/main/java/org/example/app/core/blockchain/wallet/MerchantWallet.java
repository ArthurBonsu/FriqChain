package org.example.app.core.blockchain.wallet;

import org.example.app.core.wallet.Wallet;
import org.example.app.core.wallet.TransactionBuilder;
import org.example.app.core.block.Transaction;
import org.example.app.core.types.Address;
import org.example.app.core.types.Amount;
import org.example.app.core.types.Hash;
import org.example.app.core.crypto.PKIManager;
import org.example.app.core.state.BlockchainState;
import org.example.app.core.consensus.Blockchain;

import java.security.*;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.time.Instant;

/**
 * MerchantWallet - Wallet for businesses and merchants accepting tokenized payments
 * 
 * Features:
 * - Multi-currency payment acceptance (all African tokens)
 * - Automatic conversion to preferred settlement currency (often ASU)
 * - Invoice generation and tracking
 * - Payment processing with QR codes
 * - Settlement batching for lower fees
 * - Tax reporting and compliance
 * - Refund and chargeback handling
 * - Real-time exchange rate updates
 */
public class MerchantWallet extends Wallet {
    private static final Logger LOGGER = Logger.getLogger(MerchantWallet.class.getName());
    
    // Merchant properties
    private final String merchantId;
    private final String businessName;
    private final String businessType;
    private final String countryCode;
    private final String taxIdNumber;
    private final String settlementCurrency; // Preferred currency for settlements
    
    // Multi-currency balances
    private final Map<String, BigInteger> tokenBalances;
    private final Set<String> acceptedCurrencies;
    
    // Payment processing
    private final Map<String, Invoice> activeInvoices;
    private final Map<String, Payment> paymentHistory;
    private final Map<String, Refund> refundHistory;
    
    // Settlement configuration
    private final boolean autoConvertToSettlementCurrency;
    private final BigInteger minimumSettlementAmount;
    private BigInteger pendingSettlementAmount;
    
    // Dependencies
    private final PKIManager pkiManager;
    private final BlockchainState blockchainState;
    private final Blockchain blockchain;
    
    // Compliance
    private final List<TaxRecord> taxRecords;
    private BigInteger totalRevenue;
    private BigInteger totalRefunds;
    
    /**
     * Invoice for payment requests
     */
    public static class Invoice {
        private final String invoiceId;
        private final String merchantId;
        private final String description;
        private final String currency;
        private final BigInteger amount;
        private final Instant createdAt;
        private final Instant expiresAt;
        private InvoiceStatus status;
        private String paymentTxHash;
        
        public enum InvoiceStatus {
            PENDING, PAID, EXPIRED, CANCELLED
        }
        
        public Invoice(String merchantId, String description, String currency, 
                      BigInteger amount, long expiryMinutes) {
            this.invoiceId = UUID.randomUUID().toString();
            this.merchantId = merchantId;
            this.description = description;
            this.currency = currency;
            this.amount = amount;
            this.createdAt = Instant.now();
            this.expiresAt = Instant.now().plusSeconds(expiryMinutes * 60);
            this.status = InvoiceStatus.PENDING;
        }
        
        public String getInvoiceId() { return invoiceId; }
        public String getMerchantId() { return merchantId; }
        public String getDescription() { return description; }
        public String getCurrency() { return currency; }
        public BigInteger getAmount() { return amount; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getExpiresAt() { return expiresAt; }
        public InvoiceStatus getStatus() { return status; }
        public String getPaymentTxHash() { return paymentTxHash; }
        
        public void setStatus(InvoiceStatus status) { this.status = status; }
        public void setPaymentTxHash(String txHash) { this.paymentTxHash = txHash; }
        
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
        
        public String generatePaymentQRCode() {
            // In production, generate actual QR code
            return String.format("TINC-PAY:%s:%s:%s:%s", 
                merchantId, invoiceId, currency, amount.toString());
        }
    }
    
    /**
     * Payment record
     */
    public static class Payment {
        private final String paymentId;
        private final String invoiceId;
        private final String payerAddress;
        private final String currency;
        private final BigInteger amount;
        private final Instant timestamp;
        private final String txHash;
        private PaymentStatus status;
        
        public enum PaymentStatus {
            PENDING, CONFIRMED, FAILED
        }
        
        public Payment(String invoiceId, String payerAddress, String currency, 
                      BigInteger amount, String txHash) {
            this.paymentId = UUID.randomUUID().toString();
            this.invoiceId = invoiceId;
            this.payerAddress = payerAddress;
            this.currency = currency;
            this.amount = amount;
            this.timestamp = Instant.now();
            this.txHash = txHash;
            this.status = PaymentStatus.PENDING;
        }
        
        public String getPaymentId() { return paymentId; }
        public String getInvoiceId() { return invoiceId; }
        public String getPayerAddress() { return payerAddress; }
        public String getCurrency() { return currency; }
        public BigInteger getAmount() { return amount; }
        public Instant getTimestamp() { return timestamp; }
        public String getTxHash() { return txHash; }
        public PaymentStatus getStatus() { return status; }
        
        public void setStatus(PaymentStatus status) { this.status = status; }
    }
    
    /**
     * Refund record
     */
    public static class Refund {
        private final String refundId;
        private final String paymentId;
        private final String recipientAddress;
        private final String currency;
        private final BigInteger amount;
        private final String reason;
        private final Instant timestamp;
        private final String txHash;
        
        public Refund(String paymentId, String recipientAddress, String currency, 
                     BigInteger amount, String reason, String txHash) {
            this.refundId = UUID.randomUUID().toString();
            this.paymentId = paymentId;
            this.recipientAddress = recipientAddress;
            this.currency = currency;
            this.amount = amount;
            this.reason = reason;
            this.timestamp = Instant.now();
            this.txHash = txHash;
        }
        
        public String getRefundId() { return refundId; }
        public String getPaymentId() { return paymentId; }
        public String getRecipientAddress() { return recipientAddress; }
        public String getCurrency() { return currency; }
        public BigInteger getAmount() { return amount; }
        public String getReason() { return reason; }
        public Instant getTimestamp() { return timestamp; }
        public String getTxHash() { return txHash; }
    }
    
    /**
     * Tax record for compliance
     */
    public static class TaxRecord {
        private final Instant timestamp;
        private final String currency;
        private final BigInteger revenue;
        private final BigInteger taxAmount;
        private final String taxPeriod;
        
        public TaxRecord(String currency, BigInteger revenue, BigInteger taxAmount, String taxPeriod) {
            this.timestamp = Instant.now();
            this.currency = currency;
            this.revenue = revenue;
            this.taxAmount = taxAmount;
            this.taxPeriod = taxPeriod;
        }
        
        public Instant getTimestamp() { return timestamp; }
        public String getCurrency() { return currency; }
        public BigInteger getRevenue() { return revenue; }
        public BigInteger getTaxAmount() { return taxAmount; }
        public String getTaxPeriod() { return taxPeriod; }
    }
    
    /**
     * Constructor for MerchantWallet
     */
    public MerchantWallet(String merchantId, String businessName, String businessType,
                         String countryCode, String taxIdNumber, String settlementCurrency,
                         boolean autoConvertToSettlementCurrency,
                         BlockchainState blockchainState, Blockchain blockchain,
                         PKIManager pkiManager) throws NoSuchAlgorithmException {
        super();
        
        this.merchantId = merchantId;
        this.businessName = businessName;
        this.businessType = businessType;
        this.countryCode = countryCode;
        this.taxIdNumber = taxIdNumber;
        this.settlementCurrency = settlementCurrency.toUpperCase();
        this.autoConvertToSettlementCurrency = autoConvertToSettlementCurrency;
        this.minimumSettlementAmount = new BigInteger("100000000000000000"); // 0.1 tokens with 18 decimals
        this.pendingSettlementAmount = BigInteger.ZERO;
        
        this.blockchainState = blockchainState;
        this.blockchain = blockchain;
        this.pkiManager = pkiManager;
        
        this.tokenBalances = new ConcurrentHashMap<>();
        this.acceptedCurrencies = ConcurrentHashMap.newKeySet();
        this.activeInvoices = new ConcurrentHashMap<>();
        this.paymentHistory = new ConcurrentHashMap<>();
        this.refundHistory = new ConcurrentHashMap<>();
        this.taxRecords = Collections.synchronizedList(new ArrayList<>());
        
        this.totalRevenue = BigInteger.ZERO;
        this.totalRefunds = BigInteger.ZERO;
        
        // Initialize with settlement currency
        this.tokenBalances.put(settlementCurrency, BigInteger.ZERO);
        this.acceptedCurrencies.add(settlementCurrency);
        
        LOGGER.info(String.format("MerchantWallet created for %s (%s) in %s", 
            businessName, merchantId, countryCode));
    }
    
    /**
     * Creates a new invoice for payment
     */
    public Invoice createInvoice(String description, String currency, BigInteger amount, 
                                long expiryMinutes) {
        if (!acceptedCurrencies.contains(currency)) {
            LOGGER.warning("Currency not accepted: " + currency);
            return null;
        }
        
        Invoice invoice = new Invoice(merchantId, description, currency, amount, expiryMinutes);
        activeInvoices.put(invoice.getInvoiceId(), invoice);
        
        LOGGER.info(String.format("Invoice created: %s for %s %s", 
            invoice.getInvoiceId(), amount.toString(), currency));
        
        return invoice;
    }
    
    /**
     * Processes an incoming payment
     */
    public Payment processPayment(String invoiceId, String payerAddress, String currency, 
                                 BigInteger amount, String txHash) {
        Invoice invoice = activeInvoices.get(invoiceId);
        
        if (invoice == null) {
            LOGGER.warning("Invoice not found: " + invoiceId);
            return null;
        }
        
        if (invoice.isExpired()) {
            invoice.setStatus(Invoice.InvoiceStatus.EXPIRED);
            LOGGER.warning("Invoice expired: " + invoiceId);
            return null;
        }
        
        if (invoice.getStatus() != Invoice.InvoiceStatus.PENDING) {
            LOGGER.warning("Invoice not pending: " + invoiceId);
            return null;
        }
        
        // Verify payment amount and currency
        if (!invoice.getCurrency().equals(currency) || 
            invoice.getAmount().compareTo(amount) != 0) {
            LOGGER.warning("Payment mismatch for invoice: " + invoiceId);
            return null;
        }
        
        // Create payment record
        Payment payment = new Payment(invoiceId, payerAddress, currency, amount, txHash);
        paymentHistory.put(payment.getPaymentId(), payment);
        
        // Update invoice
        invoice.setStatus(Invoice.InvoiceStatus.PAID);
        invoice.setPaymentTxHash(txHash);
        
        // Add to balance
        BigInteger currentBalance = tokenBalances.getOrDefault(currency, BigInteger.ZERO);
        tokenBalances.put(currency, currentBalance.add(amount));
        
        // Update revenue
        totalRevenue = totalRevenue.add(amount);
        pendingSettlementAmount = pendingSettlementAmount.add(amount);
        
        // Auto-convert to settlement currency if enabled
        if (autoConvertToSettlementCurrency && !currency.equals(settlementCurrency)) {
            convertToSettlementCurrency(currency, amount);
        }
        
        // Check if settlement threshold reached
        if (pendingSettlementAmount.compareTo(minimumSettlementAmount) >= 0) {
            settlePayments();
        }
        
        LOGGER.info(String.format("Payment processed: %s - %s %s from %s", 
            payment.getPaymentId(), amount.toString(), currency, payerAddress));
        
        return payment;
    }
    
    /**
     * Issues a refund
     */
    public Refund issueRefund(String paymentId, String reason) {
        Payment payment = paymentHistory.get(paymentId);
        
        if (payment == null) {
            LOGGER.warning("Payment not found for refund: " + paymentId);
            return null;
        }
        
        String currency = payment.getCurrency();
        BigInteger amount = payment.getAmount();
        
        // Check balance
        BigInteger balance = tokenBalances.getOrDefault(currency, BigInteger.ZERO);
        if (balance.compareTo(amount) < 0) {
            LOGGER.warning("Insufficient balance for refund");
            return null;
        }
        
        try {
            // Create refund transaction
            TransactionBuilder builder = new TransactionBuilder()
                .from(getAddress())
                .to(payment.getPayerAddress())
                .value(amount)
                .data(("REFUND:" + paymentId + ":" + reason).getBytes())
                .gasPrice(calculateGasPrice())
                .nonce(getNonce());
            
            Transaction tx = builder.build();
            
            // Update balance
            tokenBalances.put(currency, balance.subtract(amount));
            
            // Create refund record
            Refund refund = new Refund(paymentId, payment.getPayerAddress(), 
                                      currency, amount, reason, tx.getHash().toString());
            refundHistory.put(refund.getRefundId(), refund);
            
            // Update totals
            totalRefunds = totalRefunds.add(amount);
            
            LOGGER.info(String.format("Refund issued: %s - %s %s to %s", 
                refund.getRefundId(), amount.toString(), currency, payment.getPayerAddress()));
            
            return refund;
            
        } catch (Exception e) {
            LOGGER.severe("Error issuing refund: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Converts currency to settlement currency
     */
    private boolean convertToSettlementCurrency(String fromCurrency, BigInteger amount) {
        if (fromCurrency.equals(settlementCurrency)) {
            return true; // Already in settlement currency
        }
        
        try {
            // Get exchange rate (in production, use ExchangeRateOracle)
            BigDecimal exchangeRate = getExchangeRate(fromCurrency, settlementCurrency);
            BigInteger convertedAmount = new BigDecimal(amount)
                .multiply(exchangeRate)
                .toBigInteger();
            
            // Update balances
            BigInteger fromBalance = tokenBalances.getOrDefault(fromCurrency, BigInteger.ZERO);
            tokenBalances.put(fromCurrency, fromBalance.subtract(amount));
            
            BigInteger toBalance = tokenBalances.getOrDefault(settlementCurrency, BigInteger.ZERO);
            tokenBalances.put(settlementCurrency, toBalance.add(convertedAmount));
            
            LOGGER.info(String.format("Auto-converted: %s %s -> %s %s", 
                amount.toString(), fromCurrency, convertedAmount.toString(), settlementCurrency));
            
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Error converting currency: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Settles accumulated payments
     */
    public boolean settlePayments() {
        if (pendingSettlementAmount.compareTo(minimumSettlementAmount) < 0) {
            LOGGER.info("Settlement amount below minimum threshold");
            return false;
        }
        
        // In production, would batch settle to bank or liquidity pool
        LOGGER.info(String.format("Settling %s %s to merchant account", 
            pendingSettlementAmount.toString(), settlementCurrency));
        
        // Reset pending amount
        pendingSettlementAmount = BigInteger.ZERO;
        
        return true;
    }
    
    /**
     * Adds accepted currency
     */
    public boolean addAcceptedCurrency(String currency) {
        boolean added = acceptedCurrencies.add(currency);
        if (added) {
            tokenBalances.putIfAbsent(currency, BigInteger.ZERO);
            LOGGER.info("Added accepted currency: " + currency);
        }
        return added;
    }
    
    /**
     * Removes accepted currency
     */
    public boolean removeAcceptedCurrency(String currency) {
        if (currency.equals(settlementCurrency)) {
            LOGGER.warning("Cannot remove settlement currency");
            return false;
        }
        
        boolean removed = acceptedCurrencies.remove(currency);
        if (removed) {
            LOGGER.info("Removed accepted currency: " + currency);
        }
        return removed;
    }
    
    /**
     * Generates tax report for a period
     */
    public TaxRecord generateTaxReport(String taxPeriod, BigDecimal taxRate) {
        // Calculate tax on total revenue
        BigInteger taxableRevenue = totalRevenue.subtract(totalRefunds);
        BigInteger taxAmount = new BigDecimal(taxableRevenue)
            .multiply(taxRate)
            .toBigInteger();
        
        TaxRecord taxRecord = new TaxRecord(settlementCurrency, taxableRevenue, 
                                           taxAmount, taxPeriod);
        taxRecords.add(taxRecord);
        
        LOGGER.info(String.format("Tax report generated for %s: %s %s revenue, %s %s tax", 
            taxPeriod, taxableRevenue.toString(), settlementCurrency, 
            taxAmount.toString(), settlementCurrency));
        
        return taxRecord;
    }
    
    /**
     * Gets daily revenue report
     */
    public Map<String, BigInteger> getDailyRevenue() {
        Map<String, BigInteger> dailyRevenue = new HashMap<>();
        
        Instant yesterday = Instant.now().minusSeconds(24 * 60 * 60);
        
        for (Payment payment : paymentHistory.values()) {
            if (payment.getTimestamp().isAfter(yesterday) && 
                payment.getStatus() == Payment.PaymentStatus.CONFIRMED) {
                
                String currency = payment.getCurrency();
                BigInteger amount = payment.getAmount();
                
                dailyRevenue.put(currency, 
                    dailyRevenue.getOrDefault(currency, BigInteger.ZERO).add(amount));
            }
        }
        
        return dailyRevenue;
    }
    
    // Helper methods
    
    private BigInteger calculateGasPrice() {
        return BigInteger.valueOf(2000000000L); // 2 Gwei
    }
    
    private long getNonce() {
        return System.currentTimeMillis();
    }
    
    private BigDecimal getExchangeRate(String fromCurrency, String toCurrency) {
        // In production, query ExchangeRateOracle
        return BigDecimal.ONE;
    }
    
    // Getters and utility methods
    
    public String getMerchantId() { return merchantId; }
    public String getBusinessName() { return businessName; }
    public String getBusinessType() { return businessType; }
    public String getCountryCode() { return countryCode; }
    public String getTaxIdNumber() { return taxIdNumber; }
    public String getSettlementCurrency() { return settlementCurrency; }
    
    public BigInteger getBalance(String currency) {
        return tokenBalances.getOrDefault(currency, BigInteger.ZERO);
    }
    
    public Map<String, BigInteger> getAllBalances() {
        return new HashMap<>(tokenBalances);
    }
    
    public Set<String> getAcceptedCurrencies() {
        return new HashSet<>(acceptedCurrencies);
    }
    
    public Invoice getInvoice(String invoiceId) {
        return activeInvoices.get(invoiceId);
    }
    
    public List<Invoice> getActiveInvoices() {
        return activeInvoices.values().stream()
            .filter(inv -> inv.getStatus() == Invoice.InvoiceStatus.PENDING)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    public Payment getPayment(String paymentId) {
        return paymentHistory.get(paymentId);
    }
    
    public List<Payment> getRecentPayments(int limit) {
        return paymentHistory.values().stream()
            .sorted((p1, p2) -> p2.getTimestamp().compareTo(p1.getTimestamp()))
            .limit(limit)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    public List<Refund> getRefundHistory() {
        return new ArrayList<>(refundHistory.values());
    }
    
    public BigInteger getTotalRevenue() {
        return totalRevenue;
    }
    
    public BigInteger getTotalRefunds() {
        return totalRefunds;
    }
    
    public BigInteger getPendingSettlementAmount() {
        return pendingSettlementAmount;
    }
    
    public List<TaxRecord> getTaxRecords() {
        return new ArrayList<>(taxRecords);
    }
    
    @Override
    public String toString() {
        return String.format("MerchantWallet{id='%s', business='%s', country='%s', " +
            "currencies=%d, totalRevenue=%s %s}",
            merchantId, businessName, countryCode, acceptedCurrencies.size(),
            totalRevenue.toString(), settlementCurrency);
    }
}