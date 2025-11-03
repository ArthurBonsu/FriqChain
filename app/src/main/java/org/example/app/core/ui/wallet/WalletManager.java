// ENHANCED WalletManager.java - ADD THESE METHODS AND MODIFICATIONS

package org.example.app.core.ui.wallet;

import org.example.app.core.wallet.WalletStorage;
import org.example.app.core.token.TokenRegistry;
import org.example.app.core.account.AccountState;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.logging.Level;

public class WalletManager {
    private static final Logger LOGGER = Logger.getLogger(WalletManager.class.getName());
    
    private final WalletController walletController;
    private final ExecutorService executorService;
    private final List<WalletEventListener> eventListeners;
    private String activeWalletId;  // ✅ This was already there
    private Map<String, WalletSession> activeSessions;
    
    // ===== ADD THESE NEW FIELDS =====
    private WalletStatus currentStatus;
    private long lastStatusUpdate;
    
    public WalletManager() {
        this.walletController = new WalletController();
        this.executorService = Executors.newFixedThreadPool(4);
        this.eventListeners = new ArrayList<>();
        this.activeSessions = new HashMap<>();
        this.currentStatus = WalletStatus.NO_WALLET;
        this.lastStatusUpdate = System.currentTimeMillis();
        LOGGER.info("WalletManager initialized with enhanced wallet management");
    }
    
    // ===== ADD THIS MISSING METHOD =====
    /**
     * Sets the active wallet ID and updates all connected components
     */
    public void setActiveWallet(String walletId) {
        LOGGER.info("Setting active wallet: " + walletId);
        
        this.activeWalletId = walletId;
        this.lastStatusUpdate = System.currentTimeMillis();
        
        if (walletId != null) {
            this.currentStatus = WalletStatus.ACTIVE;
            
            // Create or update session
            WalletSession session = activeSessions.get(walletId);
            if (session == null) {
                session = new WalletSession(walletId, System.currentTimeMillis());
                activeSessions.put(walletId, session);
            }
            session.updateLastAccess();
            
            LOGGER.info("✅ Active wallet set successfully: " + walletId);
            
            // Notify all event listeners
            fireWalletActivated(walletId);
        } else {
            this.currentStatus = WalletStatus.NO_WALLET;
            LOGGER.info("Active wallet cleared");
        }
    }
    
    // ===== ADD THESE NEW METHODS =====
    
    /**
     * Gets the current wallet status
     */
    public WalletStatus getWalletStatus() {
        return currentStatus;
    }
    
    /**
     * Checks if there's an active wallet
     */
    public boolean hasActiveWallet() {
        return activeWalletId != null && currentStatus == WalletStatus.ACTIVE;
    }
    
    /**
     * Gets detailed wallet status information
     */
    public WalletStatusInfo getDetailedStatus() {
    WalletStatusInfo statusInfo = new WalletStatusInfo();
    statusInfo.hasActiveWallet = hasActiveWallet();
    statusInfo.activeWalletId = activeWalletId;
    statusInfo.status = currentStatus;
    statusInfo.lastUpdate = lastStatusUpdate;
    statusInfo.sessionCount = activeSessions.size();
    
    if (activeWalletId != null) {
        WalletController.WalletBalanceInfo balanceInfo = walletController.getWalletBalance(activeWalletId);
        if (balanceInfo != null) {
            statusInfo.walletAddress = balanceInfo.address;
            statusInfo.balance = balanceInfo.nativeBalance;
            statusInfo.isReady = true;
        } else {
            // ✅ FIX: Set ready to true if wallet is active, even without balance info
            statusInfo.isReady = (currentStatus == WalletStatus.ACTIVE);
        }
    }
    
    return statusInfo;
}
    
    /**
     * Validates that the active wallet is still accessible
     */
    public boolean validateActiveWallet() {
        if (activeWalletId == null) {
            currentStatus = WalletStatus.NO_WALLET;
            return false;
        }
        
        try {
            WalletController.WalletBalanceInfo balanceInfo = walletController.getWalletBalance(activeWalletId);
            if (balanceInfo != null) {
                currentStatus = WalletStatus.ACTIVE;
                lastStatusUpdate = System.currentTimeMillis();
                return true;
            } else {
                currentStatus = WalletStatus.ERROR;
                LOGGER.warning("Active wallet validation failed: " + activeWalletId);
                return false;
            }
        } catch (Exception e) {
            currentStatus = WalletStatus.ERROR;
            LOGGER.severe("Error validating active wallet: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Refreshes wallet status and returns updated info
     */
    public WalletStatusInfo refreshStatus() {
        LOGGER.info("Refreshing wallet status");
        validateActiveWallet();
        return getDetailedStatus();
    }
    
    // ===== ENHANCED EXISTING METHODS =====
    
    public CompletableFuture<WalletController.WalletCreationResult> createWalletAsync(String walletId, String password, boolean isContractWallet) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                WalletController.WalletCreationResult result = walletController.createWallet(walletId, password, isContractWallet);
                if (result.success) {
                    WalletSession session = new WalletSession(walletId, System.currentTimeMillis());
                    activeSessions.put(walletId, session);
                    
                    LOGGER.info("Wallet created successfully, firing events: " + walletId);
                    fireWalletCreated(result.walletInfo);
                }
                return result;
            } catch (Exception e) {
                LOGGER.severe("Error creating wallet: " + e.getMessage());
                return new WalletController.WalletCreationResult(false, "Failed to create wallet: " + e.getMessage(), null);
            }
        }, executorService);
    }
    
    public CompletableFuture<WalletController.WalletLoadResult> loadWalletAsync(String walletId, String password) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info("Loading wallet: " + walletId);
                WalletController.WalletLoadResult result = walletController.loadWallet(walletId, password);
                
                if (result.success) {
                    // ===== ENHANCED: Automatically set as active wallet =====
                    setActiveWallet(walletId);
                    
                    LOGGER.info("Wallet loaded and set as active: " + walletId);
                    fireWalletLoaded(result.walletInfo);
                } else {
                    LOGGER.warning("Failed to load wallet: " + result.message);
                }
                return result;
            } catch (Exception e) {
                LOGGER.severe("Error loading wallet: " + e.getMessage());
                return new WalletController.WalletLoadResult(false, "Failed to load wallet: " + e.getMessage(), null);
            }
        }, executorService);
    }
    
    public WalletController.WalletBalanceInfo getWalletBalance(String walletId) {
        return walletController.getWalletBalance(walletId);
    }
    
    /**
     * Gets balance for the active wallet
     */
    public WalletController.WalletBalanceInfo getActiveWalletBalance() {
        if (activeWalletId != null) {
            return getWalletBalance(activeWalletId);
        }
        return null;
    }
    
    public List<WalletController.WalletInfo> listWallets() {
        return walletController.listWallets();
    }
    
    public String getActiveWalletId() {
        return activeWalletId;
    }
    
    // ===== ENHANCED: Deactivate wallet when deleted =====
    public boolean deleteWallet(String walletId) {
        if (walletId.equals(activeWalletId)) {
            setActiveWallet(null); // Deactivate if deleting active wallet
        }
        closeWalletSession(walletId);
        return walletController.deleteWallet(walletId);
    }
    
    public void closeWalletSession(String walletId) {
        activeSessions.remove(walletId);
        if (walletId.equals(activeWalletId)) {
            setActiveWallet(null);
        }
    }
    
    // ===== ENHANCED EVENT SYSTEM =====
    
    public void addEventListener(WalletEventListener listener) {
        if (listener != null && !eventListeners.contains(listener)) {
            eventListeners.add(listener);
            LOGGER.info("Wallet event listener added");
        }
    }
    
    public void removeEventListener(WalletEventListener listener) {
        eventListeners.remove(listener);
    }
    
    private void fireWalletCreated(WalletController.WalletInfo walletInfo) {
        LOGGER.info("Firing wallet created event for: " + walletInfo.walletId);
        for (WalletEventListener listener : eventListeners) {
            try {
                listener.onWalletCreated(walletInfo);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in wallet created event listener", e);
            }
        }
    }
    


    private void fireWalletLoaded(WalletController.WalletInfo walletInfo) {
        LOGGER.info("Firing wallet loaded event for: " + walletInfo.walletId);
        for (WalletEventListener listener : eventListeners) {
            try {
                listener.onWalletLoaded(walletInfo);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in wallet loaded event listener", e);
            }
        }
    }
    
    // ===== NEW: Fire wallet activated event =====
    private void fireWalletActivated(String walletId) {
        LOGGER.info("Firing wallet activated event for: " + walletId);
        for (WalletEventListener listener : eventListeners) {
            try {
                if (listener instanceof EnhancedWalletEventListener) {
                    ((EnhancedWalletEventListener) listener).onWalletActivated(walletId);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in wallet activated event listener", e);
            }
        }
    }
    
    public void shutdown() {
        executorService.shutdown();
        LOGGER.info("WalletManager shutdown complete");
    }
    
    /**
 * Gets wallet balance using the wallet controller
 */

    // ===== ENHANCED WALLET SESSION =====
    public static class WalletSession {
        private final String walletId;
        private final long createdAt;
        private long lastAccess;
        
        public WalletSession(String walletId, long createdAt) {
            this.walletId = walletId;
            this.createdAt = createdAt;
            this.lastAccess = createdAt;
        }
        
        public void updateLastAccess() {
            this.lastAccess = System.currentTimeMillis();
        }
        
        public String getWalletId() { return walletId; }
        public long getCreatedAt() { return createdAt; }
        public long getLastAccess() { return lastAccess; }
    }
    
    // ===== NEW ENUMS AND CLASSES =====
    
    public enum WalletStatus {
        NO_WALLET,
        LOADING,
        ACTIVE,
        ERROR
    }
    
    public static class WalletStatusInfo {
        public boolean hasActiveWallet;
        public String activeWalletId;
        public WalletStatus status;
        public long lastUpdate;
        public int sessionCount;
        public String walletAddress;
        public long balance;
        public boolean isReady;
        
        @Override
        public String toString() {
            return String.format("WalletStatus{active=%s, id=%s, status=%s, ready=%s}", 
                hasActiveWallet, activeWalletId, status, isReady);
        }
    }
    
    // ===== ENHANCED EVENT LISTENER INTERFACE =====
    public interface WalletEventListener {
        default void onWalletCreated(WalletController.WalletInfo walletInfo) {}
        default void onWalletLoaded(WalletController.WalletInfo walletInfo) {}
    }
    
    public interface EnhancedWalletEventListener extends WalletEventListener {
        default void onWalletActivated(String walletId) {}
        default void onWalletDeactivated(String walletId) {}
        default void onWalletStatusChanged(WalletStatus oldStatus, WalletStatus newStatus) {}
    }
}