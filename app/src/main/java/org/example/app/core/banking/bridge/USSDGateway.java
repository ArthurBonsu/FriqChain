package org.example.app.core.banking.bridge;

import org.example.app.core.state.StateManager;
import org.example.app.core.internal.EventLog;
import org.example.app.core.types.Address;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * USSDGateway - Feature phone access via USSD (Unstructured Supplementary Service Data)
 * 
 * Enables blockchain access for users without smartphones via:
 * - USSD menu system (*123# style codes)
 * - SMS confirmations
 * - Offline transaction certificates
 * - Simple PIN-based authentication
 * 
 * Critical for reaching underbanked populations (Requirement #7: Independence from banks)
 * Supports offline-first transactions that sync when connectivity available
 */
public class USSDGateway {
    private static final Logger LOGGER = Logger.getLogger(USSDGateway.class.getName());
    
    // USSD configuration
    private static final String USSD_CODE = "*384#"; // Example USSD code
    private static final int SESSION_TIMEOUT_SECONDS = 180; // 3 minutes
    private static final int MAX_MENU_DEPTH = 5;
    private static final int PIN_LENGTH = 4;
    
    // Transaction limits for USSD (lower for security)
    private static final BigDecimal MAX_USSD_TRANSACTION = new BigDecimal("5000");
    private static final int MAX_DAILY_TRANSACTIONS = 20;
    
    /**
     * USSD menu structure
     */
    public enum MenuOption {
        MAIN_MENU(0, "Main Menu", null),
        CHECK_BALANCE(1, "Check Balance", MAIN_MENU),
        SEND_MONEY(2, "Send Money", MAIN_MENU),
        RECEIVE_MONEY(3, "Receive Money", MAIN_MENU),
        BUY_AIRTIME(4, "Buy Airtime", MAIN_MENU),
        PAY_BILL(5, "Pay Bill", MAIN_MENU),
        RECENT_TRANSACTIONS(6, "Recent Transactions", MAIN_MENU),
        CHANGE_PIN(7, "Change PIN", MAIN_MENU),
        REGISTER(8, "Register Account", MAIN_MENU),
        
        // Send Money sub-menu
        SEND_TO_PHONE(21, "Send to Phone Number", SEND_MONEY),
        SEND_TO_ACCOUNT(22, "Send to Account", SEND_MONEY),
        
        // Receive Money sub-menu
        SHOW_QR_CODE(31, "Show My Code", RECEIVE_MONEY),
        REQUEST_MONEY(32, "Request Money", RECEIVE_MONEY);
        
        private final int code;
        private final String displayText;
        private final MenuOption parent;
        
        MenuOption(int code, String displayText, MenuOption parent) {
            this.code = code;
            this.displayText = displayText;
            this.parent = parent;
        }
        
        public int getCode() { return code; }
        public String getDisplayText() { return displayText; }
        public MenuOption getParent() { return parent; }
        
        public static MenuOption fromCode(int code) {
            for (MenuOption option : values()) {
                if (option.code == code) return option;
            }
            return null;
        }
    }
    
    /**
     * Represents an active USSD session
     */
    public static class USSDSession {
        private final String sessionId;
        private final String phoneNumber;
        private final Date startTime;
        private final Deque<MenuOption> menuStack;
        private final Map<String, String> sessionData;
        
        private MenuOption currentMenu;
        private Date lastActivity;
        private boolean isActive;
        private int failedPinAttempts;
        
        public USSDSession(String phoneNumber) {
            this.sessionId = UUID.randomUUID().toString();
            this.phoneNumber = phoneNumber;
            this.startTime = new Date();
            this.menuStack = new ArrayDeque<>();
            this.sessionData = new HashMap<>();
            this.currentMenu = MenuOption.MAIN_MENU;
            this.lastActivity = new Date();
            this.isActive = true;
            this.failedPinAttempts = 0;
        }
        
        // Getters
        public String getSessionId() { return sessionId; }
        public String getPhoneNumber() { return phoneNumber; }
        public Date getStartTime() { return startTime; }
        public MenuOption getCurrentMenu() { return currentMenu; }
        public Date getLastActivity() { return lastActivity; }
        public boolean isActive() { return isActive; }
        public int getFailedPinAttempts() { return failedPinAttempts; }
        
        public void navigateTo(MenuOption menu) {
            if (currentMenu != null) {
                menuStack.push(currentMenu);
            }
            this.currentMenu = menu;
            this.lastActivity = new Date();
        }
        
        public void goBack() {
            if (!menuStack.isEmpty()) {
                this.currentMenu = menuStack.pop();
                this.lastActivity = new Date();
            }
        }
        
        public void updateActivity() {
            this.lastActivity = new Date();
        }
        
        public void terminate() {
            this.isActive = false;
        }
        
        public void incrementFailedPin() {
            this.failedPinAttempts++;
        }
        
        public void storeData(String key, String value) {
            sessionData.put(key, value);
        }
        
        public String getData(String key) {
            return sessionData.get(key);
        }
        
        public boolean isExpired() {
            long ageSeconds = (System.currentTimeMillis() - lastActivity.getTime()) / 1000;
            return ageSeconds > SESSION_TIMEOUT_SECONDS;
        }
    }
    
    /**
     * Represents a USSD user account
     */
    public static class USSDAccount {
        private final String accountId;
        private final String phoneNumber;
        private final Address blockchainAddress;
        private final Date registrationDate;
        
        private String pinHash;
        private boolean isActive;
        private BigDecimal balance;
        private String preferredLanguage; // en, sw, fr, etc.
        
        public USSDAccount(String phoneNumber, Address blockchainAddress, String pinHash) {
            this.accountId = UUID.randomUUID().toString();
            this.phoneNumber = phoneNumber;
            this.blockchainAddress = blockchainAddress;
            this.pinHash = pinHash;
            this.registrationDate = new Date();
            this.isActive = true;
            this.balance = BigDecimal.ZERO;
            this.preferredLanguage = "en";
        }
        
        // Getters
        public String getAccountId() { return accountId; }
        public String getPhoneNumber() { return phoneNumber; }
        public Address getBlockchainAddress() { return blockchainAddress; }
        public Date getRegistrationDate() { return registrationDate; }
        public String getPinHash() { return pinHash; }
        public boolean isActive() { return isActive; }
        public BigDecimal getBalance() { return balance; }
        public String getPreferredLanguage() { return preferredLanguage; }
        
        public void setPinHash(String pinHash) { this.pinHash = pinHash; }
        public void setActive(boolean active) { this.isActive = active; }
        public void setBalance(BigDecimal balance) { this.balance = balance; }
        public void setPreferredLanguage(String language) { this.preferredLanguage = language; }
    }
    
    /**
     * Represents an offline transaction certificate
     */
    public static class OfflineTransactionCertificate {
        private final String certificateId;
        private final String fromPhone;
        private final String toPhone;
        private final BigDecimal amount;
        private final Date issuedAt;
        private final Date expiresAt;
        private final String signature;
        
        private boolean isRedeemed;
        private Date redemptionDate;
        
        public OfflineTransactionCertificate(String fromPhone, String toPhone,
                                            BigDecimal amount, String signature) {
            this.certificateId = generateCertificateCode();
            this.fromPhone = fromPhone;
            this.toPhone = toPhone;
            this.amount = amount;
            this.issuedAt = new Date();
            this.signature = signature;
            this.isRedeemed = false;
            
            // Expires in 7 days
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, 7);
            this.expiresAt = cal.getTime();
        }
        
        // Getters
        public String getCertificateId() { return certificateId; }
        public String getFromPhone() { return fromPhone; }
        public String getToPhone() { return toPhone; }
        public BigDecimal getAmount() { return amount; }
        public Date getIssuedAt() { return issuedAt; }
        public Date getExpiresAt() { return expiresAt; }
        public String getSignature() { return signature; }
        public boolean isRedeemed() { return isRedeemed; }
        public Date getRedemptionDate() { return redemptionDate; }
        
        public void redeem() {
            this.isRedeemed = true;
            this.redemptionDate = new Date();
        }
        
        public boolean isExpired() {
            return new Date().after(expiresAt);
        }
        
        public boolean isValid() {
            return !isRedeemed && !isExpired();
        }
        
        private static String generateCertificateCode() {
            // Generate 12-digit code for easy manual entry
            SecureRandom random = new SecureRandom();
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                code.append(random.nextInt(10));
                if ((i + 1) % 4 == 0 && i < 11) {
                    code.append("-");
                }
            }
            return code.toString();
        }
    }
    
    // Core infrastructure
    private final StateManager stateManager;
    private final EventLog eventLog;
    
    // Session and account management
    private final Map<String, USSDSession> activeSessions; // sessionId -> session
    private final Map<String, String> phoneToSession; // phoneNumber -> sessionId
    private final Map<String, USSDAccount> accounts; // phoneNumber -> account
    private final Map<String, OfflineTransactionCertificate> certificates;
    
    // Processing
    private final ExecutorService processingExecutor;
    private final ScheduledExecutorService cleanupExecutor;
    
    // Statistics
    private final AtomicLong totalSessions;
    private final AtomicLong totalTransactions;
    private final AtomicLong offlineCertificatesIssued;
    
    /**
     * Constructor
     */
    public USSDGateway(StateManager stateManager, EventLog eventLog) {
        this.stateManager = stateManager;
        this.eventLog = eventLog;
        
        this.activeSessions = new ConcurrentHashMap<>();
        this.phoneToSession = new ConcurrentHashMap<>();
        this.accounts = new ConcurrentHashMap<>();
        this.certificates = new ConcurrentHashMap<>();
        
        this.processingExecutor = Executors.newFixedThreadPool(10);
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        
        this.totalSessions = new AtomicLong(0);
        this.totalTransactions = new AtomicLong(0);
        this.offlineCertificatesIssued = new AtomicLong(0);
        
        startCleanupTask();
        
        LOGGER.info("USSDGateway initialized with USSD code: " + USSD_CODE);
    }
    
    /**
     * Handles USSD session initiation
     */
    public String initiateSession(String phoneNumber) {
        try {
            LOGGER.info("Initiating USSD session for: " + phoneNumber);
            
            // Check if user has existing active session
            String existingSessionId = phoneToSession.get(phoneNumber);
            if (existingSessionId != null) {
                USSDSession existingSession = activeSessions.get(existingSessionId);
                if (existingSession != null && !existingSession.isExpired()) {
                    existingSession.updateActivity();
                    return existingSession.getSessionId();
                }
            }
            
            // Create new session
            USSDSession session = new USSDSession(phoneNumber);
            activeSessions.put(session.getSessionId(), session);
            phoneToSession.put(phoneNumber, session.getSessionId());
            totalSessions.incrementAndGet();
            
            eventLog.logEvent("USSD_SESSION_INITIATED: " + phoneNumber);
            
            return session.getSessionId();
            
        } catch (Exception e) {
            LOGGER.severe("Error initiating USSD session: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Processes USSD input from user
     */
    public String processInput(String sessionId, String input) {
        try {
            USSDSession session = activeSessions.get(sessionId);
            if (session == null || !session.isActive()) {
                return "Session expired. Please dial " + USSD_CODE + " to start again.";
            }
            
            if (session.isExpired()) {
                session.terminate();
                return "Session timed out. Please dial " + USSD_CODE + " to start again.";
            }
            
            session.updateActivity();
            
            // Handle input based on current menu
            return handleMenuInput(session, input);
            
        } catch (Exception e) {
            LOGGER.severe("Error processing USSD input: " + e.getMessage());
            return "An error occurred. Please try again.";
        }
    }
    
    /**
     * Handles menu navigation and input
     */
    private String handleMenuInput(USSDSession session, String input) {
        MenuOption currentMenu = session.getCurrentMenu();
        
        switch (currentMenu) {
            case MAIN_MENU:
                return handleMainMenu(session, input);
            case CHECK_BALANCE:
                return handleCheckBalance(session);
            case SEND_MONEY:
                return handleSendMoneyMenu(session, input);
            case SEND_TO_PHONE:
                return handleSendToPhone(session, input);
            case RECEIVE_MONEY:
                return handleReceiveMoneyMenu(session, input);
            case SHOW_QR_CODE:
                return handleShowQRCode(session);
            case RECENT_TRANSACTIONS:
                return handleRecentTransactions(session);
            case CHANGE_PIN:
                return handleChangePin(session, input);
            case REGISTER:
                return handleRegister(session, input);
            default:
                return displayMainMenu(session);
        }
    }
    
    /**
     * Main menu handler
     */
    private String handleMainMenu(USSDSession session, String input) {
        try {
            int choice = Integer.parseInt(input.trim());
            MenuOption selectedOption = MenuOption.fromCode(choice);
            
            if (selectedOption != null && selectedOption.getParent() == MenuOption.MAIN_MENU) {
                session.navigateTo(selectedOption);
                return processInput(session.getSessionId(), "");
            } else {
                return displayMainMenu(session) + "\n\nInvalid option. Please try again.";
            }
            
        } catch (NumberFormatException e) {
            return displayMainMenu(session) + "\n\nInvalid input. Please enter a number.";
        }
    }
    
    /**
     * Displays main menu
     */
    private String displayMainMenu(USSDSession session) {
        StringBuilder menu = new StringBuilder();
        menu.append("Welcome to Blockchain Banking\n");
        menu.append("1. Check Balance\n");
        menu.append("2. Send Money\n");
        menu.append("3. Receive Money\n");
        menu.append("4. Buy Airtime\n");
        menu.append("5. Pay Bill\n");
        menu.append("6. Recent Transactions\n");
        menu.append("7. Change PIN\n");
        menu.append("8. Register Account\n");
        menu.append("0. Exit");
        
        return menu.toString();
    }
    
    /**
     * Check balance handler
     */
    private String handleCheckBalance(USSDSession session) {
        USSDAccount account = accounts.get(session.getPhoneNumber());
        
        if (account == null) {
            return "Account not found. Please register first.\n0. Main Menu";
        }
        
        return String.format("Your Balance: %s\nAccount: %s\n\n0. Main Menu",
                           formatCurrency(account.getBalance()),
                           account.getAccountId().substring(0, 8) + "...");
    }
    
    /**
     * Send money menu handler
     */
    private String handleSendMoneyMenu(USSDSession session, String input) {
        if (input.isEmpty()) {
            return "Send Money\n1. To Phone Number\n2. To Account\n0. Back";
        }
        
        try {
            int choice = Integer.parseInt(input.trim());
            
            switch (choice) {
                case 1:
                    session.navigateTo(MenuOption.SEND_TO_PHONE);
                    return "Enter recipient phone number:";
                case 2:
                    session.navigateTo(MenuOption.SEND_TO_ACCOUNT);
                    return "Enter recipient account ID:";
                case 0:
                    session.goBack();
                    return displayMainMenu(session);
                default:
                    return "Invalid option.\n1. To Phone\n2. To Account\n0. Back";
            }
        } catch (NumberFormatException e) {
            return "Invalid input. Please enter a number.";
        }
    }
    
    /**
     * Send to phone handler
     */
    private String handleSendToPhone(USSDSession session, String input) {
        // Multi-step process
        String step = session.getData("send_step");
        
        if (step == null) {
            // Step 1: Get recipient phone
            session.storeData("recipient_phone", input);
            session.storeData("send_step", "amount");
            return "Enter amount to send:";
            
        } else if ("amount".equals(step)) {
            // Step 2: Get amount
            try {
                BigDecimal amount = new BigDecimal(input);
                
                if (amount.compareTo(MAX_USSD_TRANSACTION) > 0) {
                    return "Amount exceeds limit of " + formatCurrency(MAX_USSD_TRANSACTION) + 
                           "\n\nEnter amount to send:";
                }
                
                session.storeData("amount", input);
                session.storeData("send_step", "pin");
                return "Enter your PIN to confirm:";
                
            } catch (NumberFormatException e) {
                return "Invalid amount. Please enter a valid number:";
            }
            
        } else if ("pin".equals(step)) {
            // Step 3: Verify PIN and execute
            String recipientPhone = session.getData("recipient_phone");
            String amountStr = session.getData("amount");
            
            // Verify PIN
            if (!verifyPin(session.getPhoneNumber(), input)) {
                session.incrementFailedPin();
                if (session.getFailedPinAttempts() >= 3) {
                    session.terminate();
                    return "Too many failed attempts. Session terminated.";
                }
                return "Incorrect PIN. Try again:";
            }
            
            // Execute transaction
            boolean success = executeSendMoney(session.getPhoneNumber(), 
                                              recipientPhone, 
                                              new BigDecimal(amountStr));
            
            if (success) {
                totalTransactions.incrementAndGet();
                return "Money sent successfully!\n" + 
                       formatCurrency(new BigDecimal(amountStr)) + " to " + recipientPhone + 
                       "\n\n0. Main Menu";
            } else {
                return "Transaction failed. Insufficient balance.\n\n0. Main Menu";
            }
        }
        
        return "Error processing transaction.\n\n0. Main Menu";
    }
    
    /**
     * Receive money menu handler
     */
    private String handleReceiveMoneyMenu(USSDSession session, String input) {
        if (input.isEmpty()) {
            return "Receive Money\n1. Show My Code\n2. Request Money\n0. Back";
        }
        
        try {
            int choice = Integer.parseInt(input.trim());
            
            switch (choice) {
                case 1:
                    session.navigateTo(MenuOption.SHOW_QR_CODE);
                    return handleShowQRCode(session);
                case 2:
                    return "Enter amount to request:";
                case 0:
                    session.goBack();
                    return displayMainMenu(session);
                default:
                    return "Invalid option.\n1. Show Code\n2. Request\n0. Back";
            }
        } catch (NumberFormatException e) {
            return "Invalid input. Please enter a number.";
        }
    }
    
    /**
     * Show QR code handler (displays account code)
     */
    private String handleShowQRCode(USSDSession session) {
        USSDAccount account = accounts.get(session.getPhoneNumber());
        
        if (account == null) {
            return "Account not found. Please register first.\n0. Main Menu";
        }
        
        // Generate simple code for offline transactions
        String code = account.getAccountId().substring(0, 8).toUpperCase();
        
        return "Your Payment Code:\n" + code + "\n\nShare this code to receive payments.\n\n0. Main Menu";
    }
    
    /**
     * Recent transactions handler
     */
    private String handleRecentTransactions(USSDSession session) {
        // In production, fetch from blockchain
        return "Recent Transactions:\n" +
               "1. Sent 1000 to +254...\n" +
               "2. Received 500 from +254...\n" +
               "3. Sent 250 to +254...\n\n" +
               "0. Main Menu";
    }
    
    /**
     * Change PIN handler
     */
    private String handleChangePin(USSDSession session, String input) {
        String step = session.getData("pin_step");
        
        if (step == null) {
            session.storeData("pin_step", "old");
            return "Enter current PIN:";
        } else if ("old".equals(step)) {
            if (!verifyPin(session.getPhoneNumber(), input)) {
                return "Incorrect PIN. Try again:";
            }
            session.storeData("pin_step", "new");
            return "Enter new PIN (4 digits):";
        } else if ("new".equals(step)) {
            if (!isValidPin(input)) {
                return "PIN must be 4 digits. Try again:";
            }
            session.storeData("new_pin", input);
            session.storeData("pin_step", "confirm");
            return "Confirm new PIN:";
        } else if ("confirm".equals(step)) {
            String newPin = session.getData("new_pin");
            if (!input.equals(newPin)) {
                return "PINs don't match. Enter new PIN:";
            }
            
            // Update PIN
            updatePin(session.getPhoneNumber(), hashPin(input));
            return "PIN changed successfully!\n\n0. Main Menu";
        }
        
        return "Error changing PIN.\n\n0. Main Menu";
    }
    
    /**
     * Register handler
     */
    private String handleRegister(USSDSession session, String input) {
        String step = session.getData("register_step");
        
        if (step == null) {
            session.storeData("register_step", "pin");
            return "Create a 4-digit PIN:";
        } else if ("pin".equals(step)) {
            if (!isValidPin(input)) {
                return "PIN must be 4 digits. Try again:";
            }
            session.storeData("pin", input);
            session.storeData("register_step", "confirm");
            return "Confirm PIN:";
        } else if ("confirm".equals(step)) {
            String pin = session.getData("pin");
            if (!input.equals(pin)) {
                return "PINs don't match. Create a 4-digit PIN:";
            }
            
            // Create account
            boolean success = registerAccount(session.getPhoneNumber(), hashPin(input));
            
            if (success) {
                return "Registration successful!\nYour account is ready.\n\n0. Main Menu";
            } else {
                return "Registration failed. Please try again later.\n\n0. Main Menu";
            }
        }
        
        return "Error during registration.\n\n0. Main Menu";
    }
    
    /**
     * Issues offline transaction certificate
     */
    public String issueOfflineCertificate(String fromPhone, String toPhone, BigDecimal amount) {
        try {
            USSDAccount fromAccount = accounts.get(fromPhone);
            if (fromAccount == null || fromAccount.getBalance().compareTo(amount) < 0) {
                return null;
            }
            
            // Generate signature
            String signature = generateSignature(fromPhone, toPhone, amount);
            
            // Create certificate
            OfflineTransactionCertificate cert = new OfflineTransactionCertificate(
                fromPhone, toPhone, amount, signature);
            
            certificates.put(cert.getCertificateId(), cert);
            offlineCertificatesIssued.incrementAndGet();
            
            // Deduct from sender (held until certificate redeemed or expires)
            fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
            
            eventLog.logEvent("OFFLINE_CERT_ISSUED: " + cert.getCertificateId() + 
                            " (Amount: " + amount + ")");
            
            LOGGER.info("Offline certificate issued: " + cert.getCertificateId());
            return cert.getCertificateId();
            
        } catch (Exception e) {
            LOGGER.severe("Error issuing offline certificate: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Redeems offline transaction certificate
     */
    public boolean redeemCertificate(String certificateId, String recipientPhone) {
        try {
            OfflineTransactionCertificate cert = certificates.get(certificateId);
            
            if (cert == null || !cert.isValid()) {
                return false;
            }
            
            if (!cert.getToPhone().equals(recipientPhone)) {
                return false;
            }
            
            // Verify signature
            if (!verifySignature(cert)) {
                return false;
            }
            
            // Credit recipient
            USSDAccount recipientAccount = accounts.get(recipientPhone);
            if (recipientAccount != null) {
                recipientAccount.setBalance(recipientAccount.getBalance().add(cert.getAmount()));
            }
            
            cert.redeem();
            
            eventLog.logEvent("OFFLINE_CERT_REDEEMED: " + certificateId);
            LOGGER.info("Offline certificate redeemed: " + certificateId);
            
            return true;
            
        } catch (Exception e) {
            LOGGER.severe("Error redeeming certificate: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Helper methods
     */
    private boolean registerAccount(String phoneNumber, String pinHash) {
        if (accounts.containsKey(phoneNumber)) {
            return false;
        }
        
        Address blockchainAddress = generateAddress(phoneNumber);
        USSDAccount account = new USSDAccount(phoneNumber, blockchainAddress, pinHash);
        accounts.put(phoneNumber, account);
        
        eventLog.logEvent("USSD_ACCOUNT_REGISTERED: " + phoneNumber);
        return true;
    }
    
    private boolean verifyPin(String phoneNumber, String pin) {
        USSDAccount account = accounts.get(phoneNumber);
        return account != null && hashPin(pin).equals(account.getPinHash());
    }
    
    private void updatePin(String phoneNumber, String newPinHash) {
        USSDAccount account = accounts.get(phoneNumber);
        if (account != null) {
            account.setPinHash(newPinHash);
        }
    }
    
    private boolean isValidPin(String pin) {
        return pin != null && pin.matches("\\d{4}");
    }
    
    private String hashPin(String pin) {
        // Simple hash (in production, use proper password hashing)
        return String.valueOf(pin.hashCode());
    }
    
    private boolean executeSendMoney(String fromPhone, String toPhone, BigDecimal amount) {
        USSDAccount fromAccount = accounts.get(fromPhone);
        USSDAccount toAccount = accounts.get(toPhone);
        
        if (fromAccount == null || toAccount == null) {
            return false;
        }
        
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            return false;
        }
        
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));
        
        return true;
    }
    
    private String generateSignature(String fromPhone, String toPhone, BigDecimal amount) {
        return "SIG_" + (fromPhone + toPhone + amount).hashCode();
    }
    
    private boolean verifySignature(OfflineTransactionCertificate cert) {
        String expectedSig = generateSignature(cert.getFromPhone(), 
                                              cert.getToPhone(), 
                                              cert.getAmount());
        return expectedSig.equals(cert.getSignature());
    }
    
    private Address generateAddress(String phoneNumber) {
        return Address.fromString("0x" + phoneNumber.hashCode() + "000000000000000000000000000000000000");
    }
    
    private String formatCurrency(BigDecimal amount) {
        return String.format("%.2f", amount);
    }
    
    /**
     * Cleanup task
     */
    private void startCleanupTask() {
        cleanupExecutor.scheduleAtFixedRate(() -> {
            try {
                // Remove expired sessions
                activeSessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
                
                // Remove expired certificates
                certificates.entrySet().removeIf(entry -> 
                    entry.getValue().isExpired() || entry.getValue().isRedeemed());
                
            } catch (Exception e) {
                LOGGER.warning("Error during cleanup: " + e.getMessage());
            }
        }, 60, 60, TimeUnit.SECONDS);
    }
    
    /**
     * Public API methods
     */
    public USSDAccount getAccount(String phoneNumber) {
        return accounts.get(phoneNumber);
    }
    
    public USSDSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }
    
    public OfflineTransactionCertificate getCertificate(String certificateId) {
        return certificates.get(certificateId);
    }
    
    public Map<String, Object> getSystemStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalAccounts", accounts.size());
        stats.put("activeSessions", activeSessions.size());
        stats.put("totalSessions", totalSessions.get());
        stats.put("totalTransactions", totalTransactions.get());
        stats.put("offlineCertificatesIssued", offlineCertificatesIssued.get());
        stats.put("activeCertificates", certificates.size());
        stats.put("ussdCode", USSD_CODE);
        
        return stats;
    }
    
    /**
     * Shutdown method
     */
    public void shutdown() {
        try {
            processingExecutor.shutdown();
            cleanupExecutor.shutdown();
            
            if (!processingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                processingExecutor.shutdownNow();
            }
            if (!cleanupExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
            
            LOGGER.info("USSDGateway shutdown completed");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Shutdown interrupted");
        }
    }
}