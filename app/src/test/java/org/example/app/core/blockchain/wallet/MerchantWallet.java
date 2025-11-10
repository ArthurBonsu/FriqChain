package org.example.app.core.blockchain.wallet;

import org.example.app.core.wallet.Wallet;
import org.example.app.core.wallet.TransactionBuilder;
import org.example.app.core.block.Transaction;
import org.example.app.core.types.Address;
import org.example.app.core.types.Hash;
import org.example.app.core.account.Account;
import org.example.app.core.consensus.HashUtils;
import org.example.app.core.mempool.TransactionValidator;

import java.security.*;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * MerchantWallet - Business wallet for merchants and service providers
 * 
 * Features:
 * - Multi-currency payment processing with automatic conversion
 * - Integration with local banks while maintaining independence
 * - Inventory management and sales tracking
 * - Customer management and loyalty programs
 * - Government-resistant business continuity features
 * - Fair exchange rates favoring African currencies
 * - Offline payment acceptance for poor connectivity areas
 * - Tax compliance and reporting capabilities
 * - Supply chain financing and trade credit
 */
public class MerchantWallet extends Wallet {
    private static final Logger LOGGER = Logger.getLogger(MerchantWallet.class.getName());
    
    // Merchant identification and business info
    private final String merchantId;
    private final String businessName;
    private final String businessType;
    private final String countryCode;
    private final String businessRegistrationNumber;
    private final String taxIdentificationNumber;
    
    // Payment processing
    private final Map<String, PaymentMethod> acceptedPaymentMethods;
    private final Map<String, CurrencyAccount> currencyAccounts;
    private final PaymentProcessingEngine paymentProcessor;
    private final PointOfSaleSystem posSystem;
    
    // Customer and sales management
    private final Map<String, Customer> customers;
    private final Map<String, Sale> sales;
    private final LoyaltyProgramManager loyaltyProgram;
    private final CustomerAnalytics customerAnalytics;
    
    // Inventory and business operations
    private final Map<String, Product> inventory;
    private final Map<String, Supplier> suppliers;
    private final BusinessOperations businessOps;
    private final SupplyChainFinancing supplyChain;
    
    // Government resistance and independence
    private final BusinessContinuityPlan continuityPlan;
    private final AlternativePaymentSystems altPayments;
    private final EmergencyBusinessModes emergencyModes;
    private final DecentralizedBusinessNetwork businessNetwork;
    
    // Financial management
    private final CashFlowManager cashFlowManager;
    private final TaxComplianceManager taxManager;
    private final FinancialReporting financialReporting;
    private final BankIntegrationManager bankIntegration;
    
    // Security and compliance
    private final ReentrantLock merchantLock;
    private final TransactionValidator validator;
    private final FraudDetectionSystem fraudDetection;
    private final ComplianceMonitor complianceMonitor;
    private final List<BusinessAlert> businessAlerts;
    
    /**
     * PaymentMethod represents an accepted payment method
     */
    public static class PaymentMethod {
        private final String methodId;
        private final PaymentType type;
        private final String currency;
        private final boolean isEnabled;
        private final BigDecimal processingFee;
        private final long settlementTimeHours;
        private final boolean requiresConnectivity;
        
        public enum PaymentType {
            CASH, MOBILE_MONEY, BANK_TRANSFER, CRYPTO, QR_CODE, SMS, OFFLINE_VOUCHER
        }
        
        public PaymentMethod(String methodId, PaymentType type, String currency, 
                           BigDecimal processingFee, long settlementTimeHours, 
                           boolean requiresConnectivity) {
            this.methodId = methodId;
            this.type = type;
            this.currency = currency;
            this.isEnabled = true;
            this.processingFee = processingFee;
            this.settlementTimeHours = settlementTimeHours;
            this.requiresConnectivity = requiresConnectivity;
        }
        
        public String getMethodId() { return methodId; }
        public PaymentType getType() { return type; }
        public String getCurrency() { return currency; }
        public boolean isEnabled() { return isEnabled; }
        public BigDecimal getProcessingFee() { return processingFee; }
        public long getSettlementTimeHours() { return settlementTimeHours; }
        public boolean requiresConnectivity() { return requiresConnectivity; }
    }
    
    /**
     * CurrencyAccount represents business account in specific currency
     */
    public static class CurrencyAccount {
        private final String currency;
        private final String currencyName;
        private final String symbol;
        private volatile BigDecimal availableBalance;
        private volatile BigDecimal pendingBalance;
        private volatile BigDecimal dailyRevenue;
        private volatile BigDecimal monthlyRevenue;
        private final boolean isBaseCurrency;
        private Instant lastTransactionTime;
        
        public CurrencyAccount(String currency, String currencyName, String symbol, 
                             boolean isBaseCurrency) {
            this.currency = currency;
            this.currencyName = currencyName;
            this.symbol = symbol;
            this.availableBalance = BigDecimal.ZERO;
            this.pendingBalance = BigDecimal.ZERO;
            this.dailyRevenue = BigDecimal.ZERO;
            this.monthlyRevenue = BigDecimal.ZERO;
            this.isBaseCurrency = isBaseCurrency;
            this.lastTransactionTime = Instant.now();
        }
        
        public String getCurrency() { return currency; }
        public String getCurrencyName() { return currencyName; }
        public String getSymbol() { return symbol; }
        public BigDecimal getAvailableBalance() { return availableBalance; }
        public BigDecimal getPendingBalance() { return pendingBalance; }
        public BigDecimal getDailyRevenue() { return dailyRevenue; }
        public BigDecimal getMonthlyRevenue() { return monthlyRevenue; }
        public boolean isBaseCurrency() { return isBaseCurrency; }
        public Instant getLastTransactionTime() { return lastTransactionTime; }
        
        public synchronized void updateBalance(BigDecimal newAvailable, BigDecimal newPending) {
            this.availableBalance = newAvailable.max(BigDecimal.ZERO);
            this.pendingBalance = newPending.max(BigDecimal.ZERO);
            this.lastTransactionTime = Instant.now();
        }
        
        public synchronized void addRevenue(BigDecimal amount) {
            this.availableBalance = availableBalance.add(amount);
            this.dailyRevenue = dailyRevenue.add(amount);
            this.monthlyRevenue = monthlyRevenue.add(amount);
            this.lastTransactionTime = Instant.now();
        }
        
        public String getFormattedBalance() {
            return symbol + " " + availableBalance.setScale(2, RoundingMode.HALF_UP).toString();
        }
    }
    
    /**
     * Customer represents a business customer
     */
    public static class Customer {
        private final String customerId;
        private final String customerName;
        private final String phoneNumber;
        private final String email;
        private final String walletAddress;
        private final CustomerType type;
        private BigDecimal totalSpent;
        private int visitCount;
        private Instant firstVisit;
        private Instant lastVisit;
        private int loyaltyPoints;
        private CustomerStatus status;
        
        public enum CustomerType {
            INDIVIDUAL, BUSINESS, GOVERNMENT, NGO
        }
        
        public enum CustomerStatus {
            NEW, REGULAR, VIP, INACTIVE
        }
        
        public Customer(String customerId, String customerName, String phoneNumber, 
                       String email, String walletAddress, CustomerType type) {
            this.customerId = customerId;
            this.customerName = customerName;
            this.phoneNumber = phoneNumber;
            this.email = email;
            this.walletAddress = walletAddress;
            this.type = type;
            this.totalSpent = BigDecimal.ZERO;
            this.visitCount = 0;
            this.firstVisit = Instant.now();
            this.lastVisit = Instant.now();
            this.loyaltyPoints = 0;
            this.status = CustomerStatus.NEW;
        }
        
        public void recordPurchase(BigDecimal amount) {
            this.totalSpent = totalSpent.add(amount);
            this.visitCount++;
            this.lastVisit = Instant.now();
            
            // Add loyalty points (1 point per currency unit spent)
            this.loyaltyPoints += amount.intValue();
            
            // Update status based on spending and visits
            updateCustomerStatus();
        }
        
        private void updateCustomerStatus() {
            if (totalSpent.compareTo(new BigDecimal("100000")) > 0 && visitCount >= 10) {
                this.status = CustomerStatus.VIP;
            } else if (totalSpent.compareTo(new BigDecimal("10000")) > 0 && visitCount >= 5) {
                this.status = CustomerStatus.REGULAR;
            } else if (lastVisit.isBefore(Instant.now().minusSeconds(30 * 24 * 3600))) {
                this.status = CustomerStatus.INACTIVE;
            }
        }
        
        // Getters
        public String getCustomerId() { return customerId; }
        public String getCustomerName() { return customerName; }
        public String getPhoneNumber() { return phoneNumber; }
        public String getEmail() { return email; }
        public String getWalletAddress() { return walletAddress; }
        public CustomerType getType() { return type; }
        public BigDecimal getTotalSpent() { return totalSpent; }
        public int getVisitCount() { return visitCount; }
        public int getLoyaltyPoints() { return loyaltyPoints; }
        public CustomerStatus getStatus() { return status; }
    }
    
    /**
     * Sale represents a completed sale transaction
     */
    public static class Sale {
        private final String saleId;
        private final String customerId;
        private final List<SaleItem> items;
        private final BigDecimal subtotal;
        private final BigDecimal taxAmount;
        private final BigDecimal totalAmount;
        private final String currency;
        private final PaymentMethod.PaymentType paymentType;
        private final Instant saleTime;
        private final String cashierId;
        private SaleStatus status;
        
        public enum SaleStatus {
            PENDING, COMPLETED, REFUNDED, CANCELLED
        }
        
        public Sale(String customerId, List<SaleItem> items, BigDecimal subtotal, 
                   BigDecimal taxAmount, String currency, PaymentMethod.PaymentType paymentType, 
                   String cashierId) {
            this.saleId = generateSaleId();
            this.customerId = customerId;
            this.items = new ArrayList<>(items);
            this.subtotal = subtotal;
            this.taxAmount = taxAmount;
            this.totalAmount = subtotal.add(taxAmount);
            this.currency = currency;
            this.paymentType = paymentType;
            this.saleTime = Instant.now();
            this.cashierId = cashierId;
            this.status = SaleStatus.PENDING;
        }
        
        private String generateSaleId() {
            return "SALE_" + System.currentTimeMillis() + "_" + 
                   Integer.toHexString(new Random().nextInt());
        }
        
        public void markCompleted() { this.status = SaleStatus.COMPLETED; }
        public void markRefunded() { this.status = SaleStatus.REFUNDED; }
        public void markCancelled() { this.status = SaleStatus.CANCELLED; }
        
        // Getters
        public String getSaleId() { return saleId; }
        public String getCustomerId() { return customerId; }
        public List<SaleItem> getItems() { return new ArrayList<>(items); }
        public BigDecimal getSubtotal() { return subtotal; }
        public BigDecimal getTaxAmount() { return taxAmount; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public String getCurrency() { return currency; }
        public PaymentMethod.PaymentType getPaymentType() { return paymentType; }
        public Instant getSaleTime() { return saleTime; }
        public String getCashierId() { return cashierId; }
        public SaleStatus getStatus() { return status; }
        
        public static class SaleItem {
            private final String productId;
            private final String productName;
            private final int quantity;
            private final BigDecimal unitPrice;
            private final BigDecimal lineTotal;
            
            public SaleItem(String productId, String productName, int quantity, BigDecimal unitPrice) {
                this.productId = productId;
                this.productName = productName;
                this.quantity = quantity;
                this.unitPrice = unitPrice;
                this.lineTotal = unitPrice.multiply(new BigDecimal(quantity));
            }
            
            // Getters
            public String getProductId() { return productId; }
            public String getProductName() { return productName; }
            public int getQuantity() { return quantity; }
            public BigDecimal getUnitPrice() { return unitPrice; }
            public BigDecimal getLineTotal() { return lineTotal; }
        }
    }
    
    /**
     * Product represents inventory item
     */
    public static class Product {
        private final String productId;
        private final String productName;
        private final String category;
        private final String description;
        private final String sku;
        private BigDecimal unitPrice;
        private final String currency;
        private int stockQuantity;
        private int reorderLevel;
        private boolean isActive;
        private final Instant createdAt;
        
        public Product(String productId, String productName, String category, String description,
                      String sku, BigDecimal unitPrice, String currency, int stockQuantity, 
                      int reorderLevel) {
            this.productId = productId;
            this.productName = productName;
            this.category = category;
            this.description = description;
            this.sku = sku;
            this.unitPrice = unitPrice;
            this.currency = currency;
            this.stockQuantity = stockQuantity;
            this.reorderLevel = reorderLevel;
            this.isActive = true;
            this.createdAt = Instant.now();
        }
        
        public boolean needsReorder() {
            return stockQuantity <= reorderLevel;
        }
        
        public boolean reduceStock(int quantity) {
            if (stockQuantity >= quantity) {
                stockQuantity -= quantity;
                return true;
            }
            return false;
        }
        
        public void addStock(int quantity) {
            stockQuantity += quantity;
        }
        
        // Getters and setters
        public String getProductId() { return productId; }
        public String getProductName() { return productName; }
        public String getCategory() { return category; }
        public String getDescription() { return description; }
        public String getSku() { return sku; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public String getCurrency() { return currency; }
        public int getStockQuantity() { return stockQuantity; }
        public int getReorderLevel() { return reorderLevel; }
        public boolean isActive() { return isActive; }
        public Instant getCreatedAt() { return createdAt; }
        
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public void setReorderLevel(int reorderLevel) { this.reorderLevel = reorderLevel; }
        public void setActive(boolean active) { this.isActive = active; }
    }
    
    /**
     * Constructor for MerchantWallet
     */
    public MerchantWallet(String merchantId, String businessName, String businessType, 
                         String countryCode, String businessRegistrationNumber, 
                         String taxIdentificationNumber, String baseCurrency) 
            throws NoSuchAlgorithmException {
        super();
        
        this.merchantId = merchantId;
        this.businessName = businessName;
        this.businessType = businessType;
        this.countryCode = countryCode;
        this.businessRegistrationNumber = businessRegistrationNumber;
        this.taxIdentificationNumber = taxIdentificationNumber;
        
        this.acceptedPaymentMethods = new ConcurrentHashMap<>();
        this.currencyAccounts = new ConcurrentHashMap<>();
        this.customers = new ConcurrentHashMap<>();
        this.sales = new ConcurrentHashMap<>();
        this.inventory = new ConcurrentHashMap<>();
        this.suppliers = new ConcurrentHashMap<>();
        this.businessAlerts = new ArrayList<>();
        this.merchantLock = new ReentrantLock();
        
        // Initialize components
        this.paymentProcessor = new PaymentProcessingEngine(merchantId);
        this.posSystem = new PointOfSaleSystem(businessName);
        this.loyaltyProgram = new LoyaltyProgramManager(merchantId);
        this.customerAnalytics = new CustomerAnalytics();
        this.businessOps = new BusinessOperations(businessName);
        this.supplyChain = new SupplyChainFinancing(merchantId);
        this.continuityPlan = new BusinessContinuityPlan(merchantId);
        this.altPayments = new AlternativePaymentSystems();
        this.emergencyModes = new EmergencyBusinessModes();
        this.businessNetwork = new DecentralizedBusinessNetwork(merchantId);
        this.cashFlowManager = new CashFlowManager(baseCurrency);
        this.taxManager = new TaxComplianceManager(countryCode, taxIdentificationNumber);
        this.financialReporting = new FinancialReporting(merchantId);
        this.bankIntegration = new BankIntegrationManager(countryCode);
        this.fraudDetection = new FraudDetectionSystem();
        this.complianceMonitor = new ComplianceMonitor(countryCode);
        this.validator = new TransactionValidator(new MerchantWalletMempoolConfig());
        
        // Initialize base currency account
        addCurrencyAccount(baseCurrency, getCurrencyName(baseCurrency), 
                         getCurrencySymbol(baseCurrency), true);
        
        // Setup default payment methods
        setupDefaultPaymentMethods(baseCurrency);
        
        LOGGER.info("Created MerchantWallet for " + businessName + " (" + merchantId + 
                   ") in " + countryCode + " with base currency " + baseCurrency);
        
        addBusinessAlert("MERCHANT_WALLET_CREATED", 
            "Merchant wallet created for " + businessName, "INFO");
    }
    
    /**
     * Processes a customer payment with automatic currency conversion
     */
    public Transaction processPayment(String customerId, List<Sale.SaleItem> items, 
                                    String paymentCurrency, PaymentMethod.PaymentType paymentType, 
                                    String cashierId) {
        merchantLock.lock();
        try {
            // Calculate sale totals
            BigDecimal subtotal = items.stream()
                .map(Sale.SaleItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal taxAmount = calculateTax(subtotal);
            
            // Create sale record
            Sale sale = new Sale(customerId, items, subtotal, taxAmount, 
                               paymentCurrency, paymentType, cashierId);
            
            // Validate payment method
            PaymentMethod paymentMethod = findPaymentMethod(paymentType, paymentCurrency);
            if (paymentMethod == null || !paymentMethod.isEnabled()) {
                LOGGER.warning("Payment method not available: " + paymentType + "/" + paymentCurrency);
                return null;
            }
            
            // Check connectivity requirements for payment method
            if (paymentMethod.requiresConnectivity() && !hasConnectivity()) {
                // Use offline mode
                return processOfflinePayment(sale, paymentMethod);
            }
            
            // Fraud detection check
            if (!fraudDetection.validatePayment(customerId, sale.getTotalAmount(), paymentType)) {
                LOGGER.warning("Payment failed fraud detection: " + sale.getSaleId());
                addBusinessAlert("FRAUD_DETECTED", 
                    "Suspicious payment blocked: " + sale.getSaleId(), "WARNING");
                return null;
            }
            
            // Process inventory reduction
            if (!processInventoryReduction(items)) {
                LOGGER.warning("Insufficient inventory for sale: " + sale.getSaleId());
                return null;
            }
            
            // Create payment transaction
            byte[] paymentData = createPaymentTransactionData(sale, paymentMethod);
            
            TransactionBuilder builder = new TransactionBuilder()
                .from(customerId) // Customer pays
                .to(getAddress()) // Merchant receives
                .value(BigInteger.valueOf(sale.getTotalAmount().longValue()))
                .data(paymentData)
                .gasPrice(BigInteger.valueOf(calculateMerchantGasPrice(paymentCurrency)))
                .nonce(getNonce());
            
            Transaction tx = builder.build();
            
            // Update currency account
            CurrencyAccount account = currencyAccounts.get(paymentCurrency);
            if (account == null) {
                // Create account for new currency
                addCurrencyAccount(paymentCurrency, getCurrencyName(paymentCurrency),
                                 getCurrencySymbol(paymentCurrency), false);
                account = currencyAccounts.get(paymentCurrency);
            }
            
            // Add revenue (subtract processing fee)
            BigDecimal netAmount = sale.getTotalAmount().subtract(paymentMethod.getProcessingFee());
            account.addRevenue(netAmount);
            
            // Update customer record
            updateCustomerRecord(customerId, sale);
            
            // Complete sale
            sale.markCompleted();
            sales.put(sale.getSaleId(), sale);
            
            // Update loyalty points
            loyaltyProgram.addPoints(customerId, sale.getTotalAmount().intValue());
            
            // Record for tax compliance
            taxManager.recordSale(sale);
            
            // Update analytics
            customerAnalytics.recordSale(sale);
            
            addBusinessAlert("PAYMENT_PROCESSED", 
                "Payment processed: " + sale.getTotalAmount() + " " + paymentCurrency, "INFO");
            
            LOGGER.info("Processed payment: " + sale.getSaleId() + " for " + 
                       sale.getTotalAmount() + " " + paymentCurrency);
            
            return tx;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing payment", e);
            addBusinessAlert("PAYMENT_ERROR", "Payment processing error: " + e.getMessage(), "ERROR");
            return null;
        } finally {
            merchantLock.unlock();
        }
    }
    
    /**
     * Processes offline payments for connectivity issues
     */
    private Transaction processOfflinePayment(Sale sale, PaymentMethod paymentMethod) {
        try {
            // Generate offline payment voucher
            String voucherCode = generateOfflineVoucher(sale);
            
            // Create offline transaction data
            byte[] offlineData = createOfflinePaymentData(sale, voucherCode);
            
            TransactionBuilder builder = new TransactionBuilder()
                .from(sale.getCustomerId())
                .to(getAddress())
                .value(BigInteger.valueOf(sale.getTotalAmount().longValue()))
                .data(offlineData)
                .gasPrice(BigInteger.valueOf(1000000000L)) // Standard gas for offline
                .nonce(getNonce());
            
            Transaction tx = builder.build();
            
            // Store for later synchronization
            altPayments.storeOfflinePayment(sale, voucherCode, tx);
            
            // Mark sale as pending offline settlement
            sale.status = Sale.SaleStatus.PENDING;
            sales.put(sale.getSaleId(), sale);
            
            addBusinessAlert("OFFLINE_PAYMENT", 
                "Offline payment created: " + sale.getSaleId() + " voucher: " + voucherCode, "INFO");
            
            LOGGER.info("Created offline payment: " + sale.getSaleId() + " voucher: " + voucherCode);
            
            return tx;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing offline payment", e);
            return null;
        }
    }
    
    /**
     * Converts currency using fair rates that favor African currencies
     */
    public Transaction convertCurrency(String fromCurrency, String toCurrency, BigDecimal amount) {
        merchantLock.lock();
        try {
            CurrencyAccount fromAccount = currencyAccounts.get(fromCurrency);
            if (fromAccount == null || fromAccount.getAvailableBalance().compareTo(amount) < 0) {
                LOGGER.warning("Insufficient balance for currency conversion: " + fromCurrency);
                return null;
            }
            
            // Get fair exchange rate
            ExchangeRate exchangeRate = getBusinessExchangeRate(fromCurrency, toCurrency);
            if (exchangeRate == null) {
                LOGGER.warning("Exchange rate not available: " + fromCurrency + "/" + toCurrency);
                return null;
            }
            
            // Apply African currency bonus
            double rate = isAfricanCurrencyPair(fromCurrency, toCurrency) ? 
                exchangeRate.getFairRateWithBonus() : exchangeRate.getStandardRate();
            
            BigDecimal convertedAmount = amount.multiply(new BigDecimal(rate))
                                              .setScale(2, RoundingMode.HALF_UP);
            
            // Create conversion transaction
            byte[] conversionData = createCurrencyConversionData(fromCurrency, toCurrency, 
                                                               amount, convertedAmount, rate);
            
            TransactionBuilder builder = new TransactionBuilder()
                .from(getAddress())
                .to(getAddress()) // Self transaction
                .value(BigInteger.ZERO)
                .data(conversionData)
                .gasPrice(BigInteger.valueOf(calculateConversionGasPrice(fromCurrency, toCurrency)))
                .nonce(getNonce());
            
            Transaction tx = builder.build();
            
            // Update accounts
            fromAccount.updateBalance(fromAccount.getAvailableBalance().subtract(amount), 
                                    fromAccount.getPendingBalance());
            
            CurrencyAccount toAccount = currencyAccounts.get(toCurrency);
            if (toAccount == null) {
                addCurrencyAccount(toCurrency, getCurrencyName(toCurrency),
                                 getCurrencySymbol(toCurrency), false);
                toAccount = currencyAccounts.get(toCurrency);
            }
            
            toAccount.updateBalance(toAccount.getAvailableBalance().add(convertedAmount), 
                                  toAccount.getPendingBalance());
            
            // Record for financial reporting
            financialReporting.recordCurrencyConversion(fromCurrency, toCurrency, 
                                                      amount, convertedAmount, rate);
            
            addBusinessAlert("CURRENCY_CONVERTED", 
                "Converted " + amount + " " + fromCurrency + " to " + 
                convertedAmount + " " + toCurrency, "INFO");
            
            LOGGER.info("Converted " + amount + " " + fromCurrency + " to " + 
                       convertedAmount + " " + toCurrency + " at rate " + rate);
            
            return tx;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error converting currency", e);
            return null;
        } finally {
            merchantLock.unlock();
        }
    }
    
    /**
     * Transfers funds to suppliers or business partners
     */
    public Transaction transferToSupplier(String supplierId, BigDecimal amount, String currency, 
                                        String purpose, String invoiceReference) {
        merchantLock.lock();
        try {
            Supplier supplier = suppliers.get(supplierId);
            if (supplier == null) {
                LOGGER.warning("Supplier not found: " + supplierId);
                return null;
            }
            
            CurrencyAccount account = currencyAccounts.get(currency);
            if (account == null || account.getAvailableBalance().compareTo(amount) < 0) {
                LOGGER.warning("Insufficient balance for supplier payment: " + currency);
                return null;
            }
            
            // Validate business relationship
            if (!supplier.isApproved()) {
                LOGGER.warning("Supplier not approved for payments: " + supplierId);
                return null;
            }
            
            // Create supplier payment transaction
            byte[] supplierData = createSupplierPaymentData(supplierId, amount, currency, 
                                                          purpose, invoiceReference);
            
            TransactionBuilder builder = new TransactionBuilder()
                .from(getAddress())
                .to(supplier.getWalletAddress())
                .value(BigInteger.valueOf(amount.longValue()))
                .data(supplierData)
                .gasPrice(BigInteger.valueOf(calculateBusinessGasPrice(currency)))
                .nonce(getNonce());
            
            Transaction tx = builder.build();
            
            // Update balance
            account.updateBalance(account.getAvailableBalance().subtract(amount), 
                                account.getPendingBalance());
            
            // Record supplier payment
            supplier.recordPayment(amount, currency, invoiceReference);
            
            // Update cash flow
            cashFlowManager.recordExpense(amount, currency, "SUPPLIER_PAYMENT", purpose);
            
            // Record for tax purposes
            taxManager.recordExpense(amount, currency, "SUPPLIER_PAYMENT", supplierId);
            
            addBusinessAlert("SUPPLIER_PAYMENT", 
                "Payment to supplier " + supplier.getBusinessName() + ": " + 
                amount + " " + currency, "INFO");
            
            LOGGER.info("Transferred " + amount + " " + currency + " to supplier " + 
                       supplier.getBusinessName() + " (" + supplierId + ")");
            
            return tx;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error transferring to supplier", e);
            return null;
        } finally {
            merchantLock.unlock();
        }
    }
    
    /**
     * Creates emergency business transaction for government resistance
     */
    public Transaction createEmergencyBusinessTransaction(String emergencyType, String destination, 
                                                        String emergencyPhrase) {
        merchantLock.lock();
        try {
            // Validate emergency authorization
            if (!continuityPlan.validateEmergencyAccess(emergencyPhrase)) {
                LOGGER.severe("Invalid emergency authorization");
                addBusinessAlert("INVALID_EMERGENCY_AUTH", 
                    "Invalid emergency authorization attempted", "CRITICAL");
                return null;
            }
            
            // Activate emergency mode
            emergencyModes.activateEmergencyMode(emergencyType);
            
            // Identify critical assets to preserve
            List<CurrencyAccount> criticalAccounts = identifyCriticalAccounts();
            BigDecimal totalValue = BigDecimal.ZERO;
            
            for (CurrencyAccount account : criticalAccounts) {
                totalValue = totalValue.add(account.getAvailableBalance());
            }
            
            // Create emergency transfer transaction
            byte[] emergencyData = createEmergencyBusinessData(emergencyType, destination, 
                                                             totalValue, emergencyPhrase);
            
            TransactionBuilder builder = new TransactionBuilder()
                .from(getAddress())
                .to(destination)
                .value(BigInteger.valueOf(totalValue.longValue()))
                .data(emergencyData)
                .gasPrice(BigInteger.valueOf(15000000000L)) // Maximum priority
                .nonce(getNonce());
            
            Transaction tx = builder.build();
            
            // Clear critical account balances
            for (CurrencyAccount account : criticalAccounts) {
                account.updateBalance(BigDecimal.ZERO, BigDecimal.ZERO);
            }
            
            // Record emergency action
            financialReporting.recordEmergencyAction(emergencyType, totalValue);
            
            addBusinessAlert("EMERGENCY_TRANSACTION", 
                "Emergency business transaction executed: " + emergencyType + 
                " total value: " + totalValue, "CRITICAL");
            
            LOGGER.severe("EMERGENCY BUSINESS TRANSACTION: " + emergencyType + 
                         " total value: " + totalValue);
            
            return tx;
            
        } finally {
            merchantLock.unlock();
        }
    }
    
    /**
     * Synchronizes offline payments when connectivity is restored
     */
    public List<Transaction> synchronizeOfflinePayments() {
        merchantLock.lock();
        try {
            List<Transaction> syncedTransactions = altPayments.synchronizePendingPayments();
            
            for (Transaction tx : syncedTransactions) {
                // Update sales status
                for (Sale sale : sales.values()) {
                    if (sale.getStatus() == Sale.SaleStatus.PENDING) {
                        sale.markCompleted();
                        
                        // Update customer record retroactively
                        updateCustomerRecord(sale.getCustomerId(), sale);
                        
                        // Update revenue
                        CurrencyAccount account = currencyAccounts.get(sale.getCurrency());
                        if (account != null) {
                            account.addRevenue(sale.getTotalAmount());
                        }
                    }
                }
            }
            
            addBusinessAlert("OFFLINE_SYNC", 
                "Synchronized " + syncedTransactions.size() + " offline payments", "INFO");
            
            return syncedTransactions;
            
        } finally {
            merchantLock.unlock();
        }
    }
    
    // Inventory and product management methods
    
    /**
     * Adds a product to inventory
     */
    public boolean addProduct(String productName, String category, String description, String sku,
                            BigDecimal unitPrice, String currency, int initialStock, int reorderLevel) {
        merchantLock.lock();
        try {
            String productId = generateProductId(sku, productName);
            
            Product product = new Product(productId, productName, category, description, sku,
                                        unitPrice, currency, initialStock, reorderLevel);
            
            inventory.put(productId, product);
            
            addBusinessAlert("PRODUCT_ADDED", 
                "Product added: " + productName + " (SKU: " + sku + ")", "INFO");
            
            LOGGER.info("Added product: " + productName + " with " + initialStock + " units");
            
            return true;
            
        } finally {
            merchantLock.unlock();
        }
    }
    
    /**
     * Updates product stock
     */
    public boolean updateProductStock(String productId, int newStock) {
        merchantLock.lock();
        try {
            Product product = inventory.get(productId);
            if (product == null) {
                LOGGER.warning("Product not found: " + productId);
                return false;
            }
            
            int oldStock = product.getStockQuantity();
            product.addStock(newStock - oldStock);
            
            if (product.needsReorder()) {
                addBusinessAlert("LOW_STOCK", 
                    "Product needs reorder: " + product.getProductName() + 
                    " (Stock: " + product.getStockQuantity() + ")", "WARNING");
            }
            
            LOGGER.info("Updated stock for " + product.getProductName() + 
                       " from " + oldStock + " to " + newStock);
            
            return true;
            
        } finally {
            merchantLock.unlock();
        }
    }
    
    // Data creation methods
    
    private byte[] createPaymentTransactionData(Sale sale, PaymentMethod paymentMethod) {
        try {
            StringBuilder dataBuilder = new StringBuilder();
            dataBuilder.append("MERCHANT_PAYMENT:");
            dataBuilder.append("SALE_ID=").append(sale.getSaleId()).append(";");
            dataBuilder.append("CUSTOMER_ID=").append(sale.getCustomerId()).append(";");
            dataBuilder.append("AMOUNT=").append(sale.getTotalAmount()).append(";");
            dataBuilder.append("CURRENCY=").append(sale.getCurrency()).append(";");
            dataBuilder.append("PAYMENT_TYPE=").append(sale.getPaymentType()).append(";");
            dataBuilder.append("MERCHANT_ID=").append(merchantId).append(";");
            dataBuilder.append("BUSINESS_NAME=").append(businessName).append(";");
            dataBuilder.append("TIMESTAMP=").append(System.currentTimeMillis()).append(";");
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(dataBuilder.toString().getBytes());
            
        } catch (NoSuchAlgorithmException e) {
            return new byte[32];
        }
    }
    
    private byte[] createCurrencyConversionData(String fromCurrency, String toCurrency, 
                                              BigDecimal fromAmount, BigDecimal toAmount, double rate) {
        try {
            StringBuilder dataBuilder = new StringBuilder();
            dataBuilder.append("MERCHANT_CONVERSION:");
            dataBuilder.append("FROM_CURRENCY=").append(fromCurrency).append(";");
            dataBuilder.append("TO_CURRENCY=").append(toCurrency).append(";");
            dataBuilder.append("FROM_AMOUNT=").append(fromAmount).append(";");
            dataBuilder.append("TO_AMOUNT=").append(toAmount).append(";");
            dataBuilder.append("RATE=").append(rate).append(";");
            dataBuilder.append("MERCHANT_ID=").append(merchantId).append(";");
            dataBuilder.append("TIMESTAMP=").append(System.currentTimeMillis()).append(";");
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(dataBuilder.toString().getBytes());
            
        } catch (NoSuchAlgorithmException e) {
            return new byte[32];
        }
    }
    
    private byte[] createOfflinePaymentData(Sale sale, String voucherCode) {
        try {
            StringBuilder dataBuilder = new StringBuilder();
            dataBuilder.append("OFFLINE_PAYMENT:");
            dataBuilder.append("SALE_ID=").append(sale.getSaleId()).append(";");
            dataBuilder.append("VOUCHER_CODE=").append(voucherCode).append(";");
            dataBuilder.append("MERCHANT_ID=").append(merchantId).append(";");
            dataBuilder.append("CREATED_AT=").append(System.currentTimeMillis()).append(";");
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(dataBuilder.toString().getBytes());
            
        } catch (NoSuchAlgorithmException e) {
            return new byte[32];
        }
    }
    
    private byte[] createSupplierPaymentData(String supplierId, BigDecimal amount, String currency,
                                           String purpose, String invoiceReference) {
        try {
            StringBuilder dataBuilder = new StringBuilder();
            dataBuilder.append("SUPPLIER_PAYMENT:");
            dataBuilder.append("SUPPLIER_ID=").append(supplierId).append(";");
            dataBuilder.append("AMOUNT=").append(amount).append(";");
            dataBuilder.append("CURRENCY=").append(currency).append(";");
            dataBuilder.append("PURPOSE=").append(purpose).append(";");
            dataBuilder.append("INVOICE_REF=").append(invoiceReference).append(";");
            dataBuilder.append("MERCHANT_ID=").append(merchantId).append(";");
            dataBuilder.append("TIMESTAMP=").append(System.currentTimeMillis()).append(";");
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(dataBuilder.toString().getBytes());
            
        } catch (NoSuchAlgorithmException e) {
            return new byte[32];
        }
    }
    
    private byte[] createEmergencyBusinessData(String emergencyType, String destination, 
                                             BigDecimal totalValue, String emergencyPhrase) {
        try {
            StringBuilder dataBuilder = new StringBuilder();
            dataBuilder.append("EMERGENCY_BUSINESS:");
            dataBuilder.append("TYPE=").append(emergencyType).append(";");
            dataBuilder.append("DESTINATION=").append(destination).append(";");
            dataBuilder.append("TOTAL_VALUE=").append(totalValue).append(";");
            dataBuilder.append("PHRASE_HASH=").append(HashUtils.generateHash(emergencyPhrase)).append(";");
            dataBuilder.append("MERCHANT_ID=").append(merchantId).append(";");
            dataBuilder.append("BUSINESS_NAME=").append(businessName).append(";");
            dataBuilder.append("TIMESTAMP=").append(System.currentTimeMillis()).append(";");
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(dataBuilder.toString().getBytes());
            
        } catch (NoSuchAlgorithmException e) {
            return new byte[32];
        }
    }
    
    // Utility and helper methods
    
    private void addCurrencyAccount(String currency, String currencyName, String symbol, boolean isBase) {
        CurrencyAccount account = new CurrencyAccount(currency, currencyName, symbol, isBase);
        currencyAccounts.put(currency, account);
    }
    
    private void setupDefaultPaymentMethods(String baseCurrency) {
        // Cash payment
        acceptedPaymentMethods.put("CASH_" + baseCurrency, 
            new PaymentMethod("CASH_" + baseCurrency, PaymentMethod.PaymentType.CASH, 
                            baseCurrency, BigDecimal.ZERO, 0, false));
        
        // Mobile money
        acceptedPaymentMethods.put("MOBILE_" + baseCurrency, 
            new PaymentMethod("MOBILE_" + baseCurrency, PaymentMethod.PaymentType.MOBILE_MONEY, 
                            baseCurrency, new BigDecimal("0.02"), 1, true));
        
        // QR Code
        acceptedPaymentMethods.put("QR_" + baseCurrency, 
            new PaymentMethod("QR_" + baseCurrency, PaymentMethod.PaymentType.QR_CODE, 
                            baseCurrency, new BigDecimal("0.01"), 0, false));
    }
    
    private PaymentMethod findPaymentMethod(PaymentMethod.PaymentType type, String currency) {
        return acceptedPaymentMethods.values().stream()
            .filter(pm -> pm.getType() == type && pm.getCurrency().equals(currency))
            .findFirst()
            .orElse(null);
    }
    
    private boolean hasConnectivity() {
        // In production, this would check actual network connectivity
        return Math.random() > 0.1; // Simulate 90% connectivity
    }
    
    private BigDecimal calculateTax(BigDecimal subtotal) {
        // Simplified tax calculation - in production would use complex tax rules
        return subtotal.multiply(new BigDecimal("0.15")); // 15% tax rate
    }
    
    private boolean processInventoryReduction(List<Sale.SaleItem> items) {
        for (Sale.SaleItem item : items) {
            Product product = inventory.get(item.getProductId());
            if (product == null || !product.reduceStock(item.getQuantity())) {
                return false;
            }
        }
        return true;
    }
    
    private void updateCustomerRecord(String customerId, Sale sale) {
        Customer customer = customers.get(customerId);
        if (customer != null) {
            customer.recordPurchase(sale.getTotalAmount());
        }
    }
    
    private String generateOfflineVoucher(Sale sale) {
        return "VOUCHER_" + merchantId.substring(0, 4) + "_" + 
               sale.getSaleId().substring(sale.getSaleId().length() - 8) + "_" + 
               System.currentTimeMillis();
    }
    
    private String generateProductId(String sku, String productName) {
        return HashUtils.generateHash(sku + productName + merchantId).substring(0, 12);
    }
    
    private ExchangeRate getBusinessExchangeRate(String fromCurrency, String toCurrency) {
        // In production, this would fetch from multiple business-oriented exchanges
        return new ExchangeRate(fromCurrency, toCurrency, 
                               getSimulatedExchangeRate(fromCurrency, toCurrency));
    }
    
    private double getSimulatedExchangeRate(String fromCurrency, String toCurrency) {
        // Simplified rate simulation
        Map<String, Double> rates = Map.of(
            "NGN/USD", 0.0024,
            "KES/USD", 0.0067,
            "ZAR/USD", 0.055,
            "GHS/USD", 0.083
        );
        
        String pair = fromCurrency + "/" + toCurrency;
        String reversePair = toCurrency + "/" + fromCurrency;
        
        if (rates.containsKey(pair)) {
            return rates.get(pair);
        } else if (rates.containsKey(reversePair)) {
            return 1.0 / rates.get(reversePair);
        }
        return 1.0;
    }
    
    private boolean isAfricanCurrencyPair(String currency1, String currency2) {
        Set<String> africanCurrencies = Set.of("NGN", "KES", "ZAR", "GHS", "EGP", "MAD", "TZS", "UGX");
        return africanCurrencies.contains(currency1) || africanCurrencies.contains(currency2);
    }
    
    private List<CurrencyAccount> identifyCriticalAccounts() {
        return currencyAccounts.values().stream()
            .filter(account -> account.getAvailableBalance().compareTo(BigDecimal.ZERO) > 0)
            .sorted((a, b) -> b.getAvailableBalance().compareTo(a.getAvailableBalance()))
            .toList();
    }
    
    private String getCurrencyName(String currencyCode) {
        Map<String, String> names = Map.of(
            "NGN", "Nigerian Naira", "KES", "Kenyan Shilling", "ZAR", "South African Rand",
            "GHS", "Ghanaian Cedi", "USD", "US Dollar", "EUR", "Euro"
        );
        return names.getOrDefault(currencyCode, currencyCode);
    }
    
    private String getCurrencySymbol(String currencyCode) {
        Map<String, String> symbols = Map.of(
            "NGN", "₦", "KES", "KSh", "ZAR", "R", "GHS", "₵", "USD", "$", "EUR", "€"
        );
        return symbols.getOrDefault(currencyCode, currencyCode);
    }
    
    private long calculateMerchantGasPrice(String currency) {
        // Business-optimized gas pricing
        return 1500000000L; // 1.5 Gwei for merchant transactions
    }
    
    private long calculateConversionGasPrice(String fromCurrency, String toCurrency) {
        long baseGas = 2000000000L;
        // Lower gas for African currency conversions
        if (isAfricanCurrencyPair(fromCurrency, toCurrency)) {
            baseGas = (long) (baseGas * 0.8);
        }
        return baseGas;
    }
    
    private long calculateBusinessGasPrice(String currency) {
        return 2500000000L; // Higher for business-to-business transactions
    }
    
    private void addBusinessAlert(String alertType, String message, String severity) {
        BusinessAlert alert = new BusinessAlert(alertType, message, severity, Instant.now());
        businessAlerts.add(alert);
        
        if (businessAlerts.size() > 500) {
            businessAlerts.subList(0, 100).clear();
        }
    }
    
    private long getNonce() {
        return System.currentTimeMillis();
    }
    
    // Supporting component classes (simplified implementations)
    
    private static class PaymentProcessingEngine {
        private final String merchantId;
        PaymentProcessingEngine(String merchantId) { this.merchantId = merchantId; }
    }
    
    private static class PointOfSaleSystem {
        private final String businessName;
        PointOfSaleSystem(String businessName) { this.businessName = businessName; }
    }
    
    private static class LoyaltyProgramManager {
        private final String merchantId;
        private final Map<String, Integer> customerPoints = new HashMap<>();
        
        LoyaltyProgramManager(String merchantId) { this.merchantId = merchantId; }
        
        void addPoints(String customerId, int points) {
            customerPoints.put(customerId, customerPoints.getOrDefault(customerId, 0) + points);
        }
    }
    
    private static class CustomerAnalytics {
        void recordSale(Sale sale) {
            // Analytics logic
        }
    }
    
    private static class BusinessOperations {
        private final String businessName;
        BusinessOperations(String businessName) { this.businessName = businessName; }
    }
    
    private static class SupplyChainFinancing {
        private final String merchantId;
        SupplyChainFinancing(String merchantId) { this.merchantId = merchantId; }
    }
    
    private static class BusinessContinuityPlan {
        private final String merchantId;
        BusinessContinuityPlan(String merchantId) { this.merchantId = merchantId; }
        
        boolean validateEmergencyAccess(String phrase) {
            return phrase != null && phrase.length() >= 12;
        }
    }
    
    private static class AlternativePaymentSystems {
        private final List<OfflinePayment> pendingPayments = new ArrayList<>();
        
        void storeOfflinePayment(Sale sale, String voucherCode, Transaction tx) {
            pendingPayments.add(new OfflinePayment(sale, voucherCode, tx));
        }
        
        List<Transaction> synchronizePendingPayments() {
            List<Transaction> synced = new ArrayList<>();
            for (OfflinePayment payment : pendingPayments) {
                synced.add(payment.transaction);
            }
            pendingPayments.clear();
            return synced;
        }
        
        private static class OfflinePayment {
            final Sale sale;
            final String voucherCode;
            final Transaction transaction;
            
            OfflinePayment(Sale sale, String voucherCode, Transaction transaction) {
                this.sale = sale;
                this.voucherCode = voucherCode;
                this.transaction = transaction;
            }
        }
    }
    
    private static class EmergencyBusinessModes {
        void activateEmergencyMode(String type) {
            LOGGER.severe("Emergency mode activated: " + type);
        }
    }
    
    private static class DecentralizedBusinessNetwork {
        private final String merchantId;
        DecentralizedBusinessNetwork(String merchantId) { this.merchantId = merchantId; }
    }
    
    private static class CashFlowManager {
        private final String baseCurrency;
        CashFlowManager(String baseCurrency) { this.baseCurrency = baseCurrency; }
        
        void recordExpense(BigDecimal amount, String currency, String category, String description) {
            // Cash flow tracking logic
        }
    }
    
    private static class TaxComplianceManager {
        private final String countryCode;
        private final String taxId;
        
        TaxComplianceManager(String countryCode, String taxId) {
            this.countryCode = countryCode;
            this.taxId = taxId;
        }
        
        void recordSale(Sale sale) {
            // Tax recording logic
        }
        
        void recordExpense(BigDecimal amount, String currency, String category, String reference) {
            // Expense recording logic
        }
    }
    
    private static class FinancialReporting {
        private final String merchantId;
        FinancialReporting(String merchantId) { this.merchantId = merchantId; }
        
        void recordCurrencyConversion(String from, String to, BigDecimal fromAmount, 
                                    BigDecimal toAmount, double rate) {
            // Reporting logic
        }
        
        void recordEmergencyAction(String type, BigDecimal amount) {
            // Emergency action reporting
        }
    }
    
    private static class BankIntegrationManager {
        private final String countryCode;
        BankIntegrationManager(String countryCode) { this.countryCode = countryCode; }
    }
    
    private static class FraudDetectionSystem {
        boolean validatePayment(String customerId, BigDecimal amount, PaymentMethod.PaymentType type) {
            // Fraud detection logic
            return true; // Simplified
        }
    }
    
    private static class ComplianceMonitor {
        private final String countryCode;
        ComplianceMonitor(String countryCode) { this.countryCode = countryCode; }
    }
    
    private static class Supplier {
        private final String supplierId;
        private final String businessName;
        private final String walletAddress;
        private boolean isApproved;
        private final List<SupplierPayment> payments = new ArrayList<>();
        
        Supplier(String supplierId, String businessName, String walletAddress) {
            this.supplierId = supplierId;
            this.businessName = businessName;
            this.walletAddress = walletAddress;
            this.isApproved = false;
        }
        
        String getBusinessName() { return businessName; }
        String getWalletAddress() { return walletAddress; }
        boolean isApproved() { return isApproved; }
        
        void recordPayment(BigDecimal amount, String currency, String invoiceRef) {
            payments.add(new SupplierPayment(amount, currency, invoiceRef));
        }
        
        private static class SupplierPayment {
            final BigDecimal amount;
            final String currency;
            final String invoiceRef;
            final Instant timestamp;
            
            SupplierPayment(BigDecimal amount, String currency, String invoiceRef) {
                this.amount = amount;
                this.currency = currency;
                this.invoiceRef = invoiceRef;
                this.timestamp = Instant.now();
            }
        }
    }
    
    private static class ExchangeRate {
        private final String fromCurrency;
        private final String toCurrency;
        private final double standardRate;
        private final Instant timestamp;
        
        ExchangeRate(String fromCurrency, String toCurrency, double standardRate) {
            this.fromCurrency = fromCurrency;
            this.toCurrency = toCurrency;
            this.standardRate = standardRate;
            this.timestamp = Instant.now();
        }
        
        double getStandardRate() { return standardRate; }
        double getFairRateWithBonus() { return standardRate * 1.02; } // 2% African bonus
    }
    
    private static class BusinessAlert {
        private final String alertType;
        private final String message;
        private final String severity;
        private final Instant timestamp;
        
        BusinessAlert(String alertType, String message, String severity, Instant timestamp) {
            this.alertType = alertType;
            this.message = message;
            this.severity = severity;
            this.timestamp = timestamp;
        }
    }
    
    // Public getters and methods
    
    public String getMerchantId() { return merchantId; }
    public String getBusinessName() { return businessName; }
    public String getBusinessType() { return businessType; }
    public String getCountryCode() { return countryCode; }
    
    public CurrencyAccount getCurrencyAccount(String currency) {
        return currencyAccounts.get(currency);
    }
    
    public Map<String, CurrencyAccount> getAllCurrencyAccounts() {
        return new HashMap<>(currencyAccounts);
    }
    
    public List<Sale> getRecentSales(int limit) {
        return sales.values().stream()
                   .sorted((a, b) -> b.getSaleTime().compareTo(a.getSaleTime()))
                   .limit(limit)
                   .toList();
    }
    
    public List<Product> getLowStockProducts() {
        return inventory.values().stream()
                       .filter(Product::needsReorder)
                       .toList();
    }
    
    public List<Customer> getTopCustomers(int limit) {
        return customers.values().stream()
                       .sorted((a, b) -> b.getTotalSpent().compareTo(a.getTotalSpent()))
                       .limit(limit)
                       .toList();
    }
    
    public BigDecimal getTotalDailyRevenue() {
        return currencyAccounts.values().stream()
            .map(CurrencyAccount::getDailyRevenue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    public BigDecimal getTotalMonthlyRevenue() {
        return currencyAccounts.values().stream()
            .map(CurrencyAccount::getMonthlyRevenue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    public List<BusinessAlert> getRecentBusinessAlerts(int limit) {
        int size = businessAlerts.size();
        int fromIndex = Math.max(0, size - limit);
        return new ArrayList<>(businessAlerts.subList(fromIndex, size));
    }
    
    /**
     * MerchantWalletMempoolConfig for business transaction validation
     */
    private static class MerchantWalletMempoolConfig implements org.example.app.core.mempool.MempoolConfig {
        @Override
        public BigInteger getMinGasPrice() {
            return BigInteger.valueOf(1000000000L); // 1 Gwei for business
        }
        
        @Override
        public int getMaxTxPoolSize() {
            return 100; // Higher limit for business operations
        }
        
        @Override
        public long getMaxTxAge() {
            return 1200000; // 20 minutes for business transactions
        }
    }
}