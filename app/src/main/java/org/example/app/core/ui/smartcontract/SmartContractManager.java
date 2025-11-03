package org.example.app.core.ui.smartcontract;

import org.example.app.core.smartcontract.EventLog;
import org.example.app.core.mempool.TransactionPool;
import org.example.app.core.ui.wallet.WalletController;
import org.example.app.core.ui.wallet.WalletManager;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

public class SmartContractManager {
    private static final Logger LOGGER = Logger.getLogger(SmartContractManager.class.getName());
    
    private final SmartContractController smartContractController;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;
    private final List<SmartContractEventListener> eventListeners;
    private Map<String, ContractSession> activeSessions;
    private boolean eventMonitoringEnabled;
    private String activeContractAddress;
    
    // Add wallet integration
    private WalletController walletController;
    private WalletManager walletManager;
    
    public SmartContractManager() {
        this.smartContractController = new SmartContractController();
        this.executorService = Executors.newFixedThreadPool(4);
        this.scheduledExecutor = Executors.newScheduledThreadPool(2);
        this.eventListeners = new ArrayList<>();
        this.activeSessions = new HashMap<>();
        this.eventMonitoringEnabled = false;
        LOGGER.info("SmartContractManager initialized");
    }
    
    // Add wallet integration methods
    public void setWalletController(WalletController walletController) {
    this.walletController = walletController;
    this.smartContractController.setWalletController(walletController);
    LOGGER.info("WalletController set in SmartContractManager and passed to SmartContractController");
}

public void setWalletManager(WalletManager walletManager) {
    this.walletManager = walletManager;
    this.smartContractController.setWalletManager(walletManager);
    
    // Set active wallet if available
    if (walletManager != null) {
        String activeWalletId = walletManager.getActiveWalletId();
        if (activeWalletId != null) {
            this.smartContractController.setActiveWalletId(activeWalletId);
            LOGGER.info("Active wallet set in SmartContractManager: " + activeWalletId);
        }
    }
}
    
    // Add method to update active wallet
   public void updateActiveWallet(String walletId) {
    if (walletId != null) {
        this.smartContractController.setActiveWalletId(walletId);
        LOGGER.info("Active wallet updated in SmartContractManager: " + walletId);
    }
}
    
    // Add method to check wallet availability
    /**
 * ✅ PRODUCTION FIX: Enhanced wallet availability check
 */
public boolean isWalletAvailable() {
    try {
        return smartContractController != null && smartContractController.isWalletAvailable();
    } catch (Exception e) {
        LOGGER.warning("Error checking wallet availability in manager: " + e.getMessage());
        return false;
    }
}

    // Add method to get current wallet info
 /**
 * ✅ PRODUCTION FIX: Enhanced wallet info retrieval
 */
public WalletController.WalletBalanceInfo getCurrentWalletInfo() {
    try {
        return smartContractController != null ? smartContractController.getCurrentWalletInfo() : null;
    } catch (Exception e) {
        LOGGER.warning("Error getting wallet info in manager: " + e.getMessage());
        return null;
    }
}
    public void setTransactionPool(TransactionPool transactionPool) {
        smartContractController.setTransactionPool(transactionPool);
    }
    
    public CompletableFuture<SmartContractController.ContractCompilationResult> compileContractAsync(
            String contractName, String sourceCode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SmartContractController.ContractCompilationResult result = 
                    smartContractController.compileSolidityContract(contractName, sourceCode);
                
                if (result.success) {
                    fireContractCompiled(result.compilationInfo);
                }
                return result;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error compiling contract", e);
                return new SmartContractController.ContractCompilationResult(
                    false, "Compilation failed: " + e.getMessage(), null);
            }
        }, executorService);
    }
    
/**
 * ✅ PRODUCTION FIX: Enhanced deployment with robust wallet validation
 */
/**
 * ✅ PRODUCTION FIX: Enhanced deployment with robust wallet validation
 */
public CompletableFuture<SmartContractController.ContractDeploymentResult> deployContractAsync(
        String contractName, byte[] bytecode, String abi, int gasLimit) {
    return CompletableFuture.supplyAsync(() -> {
        try {
            LOGGER.info("🚀 STARTING ASYNC DEPLOYMENT: " + contractName);
            
            // ✅ PRODUCTION VALIDATION: Multiple-layer wallet checking
            if (!smartContractController.isWalletAvailable()) {
                LOGGER.severe("❌ DEPLOYMENT BLOCKED: No wallet available in controller");
                return new SmartContractController.ContractDeploymentResult(
                    false, "No wallet available for contract deployment. Please create or load a wallet first.", null);
            }
            
            // ✅ ADDITIONAL VALIDATION: Check if controller has currentWallet
            if (smartContractController.getCurrentWallet() == null) {
                LOGGER.severe("❌ DEPLOYMENT BLOCKED: Controller currentWallet is null");
                
                // ✅ RECOVERY ATTEMPT: Try to trigger wallet connection recovery
                String activeWalletId = smartContractController.getActiveWalletId();
                if (activeWalletId != null) {
                    LOGGER.info("Attempting to recover wallet connection for deployment");
                    
                    // Trigger availability check which includes recovery logic
                    boolean recovered = smartContractController.isWalletAvailable();
                    if (!recovered || smartContractController.getCurrentWallet() == null) {
                        return new SmartContractController.ContractDeploymentResult(
                            false, "Wallet connection lost. Please reload your wallet and try again.", null);
                    }
                    LOGGER.info("✅ Wallet connection recovered successfully");
                } else {
                    return new SmartContractController.ContractDeploymentResult(
                        false, "No active wallet ID. Please load a wallet first.", null);
                }
            }
            
            LOGGER.info("✅ WALLET VALIDATION PASSED - Proceeding with deployment");
            LOGGER.info("   - Contract Name: " + contractName);
            LOGGER.info("   - Bytecode Size: " + bytecode.length + " bytes");
            LOGGER.info("   - Gas Limit: " + gasLimit);
            LOGGER.info("   - Active Wallet: " + smartContractController.getActiveWalletId());
            
            // ✅ PRODUCTION DEPLOYMENT: Call the controller's deploy method
            SmartContractController.ContractDeploymentResult result = 
                smartContractController.deployContract(contractName, bytecode, abi, gasLimit);
            
            if (result.success) {
                LOGGER.info("🎉 DEPLOYMENT SUCCESSFUL!");
                LOGGER.info("   - Contract Address: " + result.contractInfo.contractAddress);
                LOGGER.info("   - Gas Used: " + result.contractInfo.gasUsed);
                LOGGER.info("   - Deployer: " + result.contractInfo.deployer);
                
                // Create session for the deployed contract
                ContractSession session = new ContractSession(
                    result.contractInfo.contractAddress, contractName, System.currentTimeMillis()
                );
                activeSessions.put(result.contractInfo.contractAddress, session);
                fireContractDeployed(result.contractInfo);
                
                LOGGER.info("✅ Contract session created and events fired");
            } else {
                LOGGER.warning("❌ DEPLOYMENT FAILED: " + result.message);
            }
            
            return result;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ DEPLOYMENT EXCEPTION", e);
            return new SmartContractController.ContractDeploymentResult(
                false, "Deployment failed with exception: " + e.getMessage(), null);
        }
    }, executorService);
}
    
 /**
 * ✅ PRODUCTION FIX: Enhanced function call with robust wallet validation
 *//**
 * ✅ PRODUCTION FIX: Enhanced function call with robust wallet validation
 */
public CompletableFuture<SmartContractController.ContractCallResult> callContractFunctionAsync(
        String contractAddress, String functionSignature, Object[] args, int gasLimit, long value) {
    return CompletableFuture.supplyAsync(() -> {
        try {
            LOGGER.info("📞 STARTING ASYNC FUNCTION CALL: " + functionSignature);
            
            // ✅ PRODUCTION VALIDATION: Check wallet availability before function call
            if (!smartContractController.isWalletAvailable()) {
                LOGGER.severe("❌ FUNCTION CALL BLOCKED: No wallet available");
                return new SmartContractController.ContractCallResult(
                    false, "No wallet available for contract interaction. Please create or load a wallet first.", null);
            }
            
            // ✅ ADDITIONAL VALIDATION: Check if controller has currentWallet
            if (smartContractController.getCurrentWallet() == null) {
                LOGGER.severe("❌ FUNCTION CALL BLOCKED: Controller currentWallet is null");
                return new SmartContractController.ContractCallResult(
                    false, "Wallet connection lost. Please reload your wallet and try again.", null);
            }
            
            LOGGER.info("✅ WALLET VALIDATION PASSED - Proceeding with function call");
            
            SmartContractController.ContractCallResult result = 
                smartContractController.callContractFunction(contractAddress, functionSignature, args, gasLimit, value);
            
            if (result.success) {
                fireFunctionCalled(result.callInfo);
                LOGGER.info("✅ FUNCTION CALL SUCCESSFUL: " + functionSignature);
            } else {
                LOGGER.warning("❌ FUNCTION CALL FAILED: " + result.message);
            }
            
            return result;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ FUNCTION CALL EXCEPTION", e);
            return new SmartContractController.ContractCallResult(
                false, "Function call failed with exception: " + e.getMessage(), null);
        }
    }, executorService);
}

    
    public SmartContractController.ContractQueryResult queryContractState(String contractAddress, String storageKey) {
        try {
            return smartContractController.queryContractState(contractAddress, storageKey);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error querying contract state", e);
            return new SmartContractController.ContractQueryResult(false, "Query failed: " + e.getMessage(), null);
        }
    }
    
    public List<SmartContractController.ContractInfo> getDeployedContracts() {
        try {
            return smartContractController.getDeployedContracts();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting deployed contracts", e);
            return new ArrayList<>();
        }
    }
    
    public SmartContractController.ContractInfo getContractInfo(String contractAddress) {
        try {
            return smartContractController.getContractInfo(contractAddress);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting contract info", e);
            return null;
        }
    }
    
    public void setActiveContract(String contractAddress) {
        this.activeContractAddress = contractAddress;
        if (contractAddress != null && !activeSessions.containsKey(contractAddress)) {
            SmartContractController.ContractInfo contractInfo = smartContractController.getContractInfo(contractAddress);
            if (contractInfo != null) {
                ContractSession session = new ContractSession(
                    contractAddress, contractInfo.contractName, System.currentTimeMillis()
                );
                activeSessions.put(contractAddress, session);
            }
        }
    }
    
    public String getActiveContractAddress() {
        return activeContractAddress;
    }
    
    public void startEventMonitoring() {
        if (!eventMonitoringEnabled) {
            eventMonitoringEnabled = true;
            scheduledExecutor.scheduleAtFixedRate(this::monitorContractEvents, 0, 5, TimeUnit.SECONDS);
            LOGGER.info("Contract event monitoring started");
        }
    }
    
    public void stopEventMonitoring() {
        eventMonitoringEnabled = false;
        LOGGER.info("Contract event monitoring stopped");
    }
    
    private void monitorContractEvents() {
        if (!eventMonitoringEnabled) return;
        
        try {
            for (String contractAddress : activeSessions.keySet()) {
                List<EventLog.Event> events = smartContractController.getContractEvents(contractAddress, null);
                for (EventLog.Event event : events) {
                    fireEventDetected(event);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error monitoring contract events", e);
        }
    }
    
    public List<EventLog.Event> getContractEvents(String contractAddress, String eventSignature) {
        try {
            return smartContractController.getContractEvents(contractAddress, eventSignature);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting contract events", e);
            return new ArrayList<>();
        }
    }
    
    public void clearEventLog() {
        try {
            smartContractController.clearEventLog();
            LOGGER.info("Contract event log cleared");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error clearing event log", e);
        }
    }
    
    public SmartContractController.SmartContractMetrics getMetrics() {
        try {
            return smartContractController.getMetrics();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting metrics", e);
            // Return empty metrics on error
            return new SmartContractController.SmartContractMetrics();
        }
    }
    
    public ContractStatistics getContractStatistics() {
        try {
            SmartContractController.SmartContractMetrics metrics = smartContractController.getMetrics();
            return new ContractStatistics(
                metrics.getContractsCompiled(),
                metrics.getContractsDeployed(),
                metrics.getFunctionCalls(),
                metrics.getTotalGasUsed(),
                activeSessions.size()
            );
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting contract statistics", e);
            // Return empty statistics on error
            return new ContractStatistics(0, 0, 0, 0, 0);
        }
    }
    
    public void closeContractSession(String contractAddress) {
        activeSessions.remove(contractAddress);
        if (contractAddress.equals(activeContractAddress)) {
            activeContractAddress = null;
        }
    }
    
    public void addEventListener(SmartContractEventListener listener) {
        if (listener != null) {
            eventListeners.add(listener);
        }
    }
    
    public void removeEventListener(SmartContractEventListener listener) {
        eventListeners.remove(listener);
    }
    
    private void fireContractCompiled(SmartContractController.ContractCompilationInfo compilationInfo) {
        for (SmartContractEventListener listener : eventListeners) {
            try {
                listener.onContractCompiled(compilationInfo);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in event listener", e);
            }
        }
    }
    
    private void fireContractDeployed(SmartContractController.ContractInfo contractInfo) {
        for (SmartContractEventListener listener : eventListeners) {
            try {
                listener.onContractDeployed(contractInfo);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in event listener", e);
            }
        }
    }
    
    private void fireFunctionCalled(SmartContractController.ContractCallInfo callInfo) {
        for (SmartContractEventListener listener : eventListeners) {
            try {
                listener.onFunctionCalled(callInfo);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in event listener", e);
            }
        }
    }
    
    private void fireEventDetected(EventLog.Event event) {
        for (SmartContractEventListener listener : eventListeners) {
            try {
                listener.onEventDetected(event);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in event listener", e);
            }
        }
    }
    
    public void shutdown() {
        stopEventMonitoring();
        executorService.shutdown();
        scheduledExecutor.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("SmartContractManager shutdown complete");
    }
    
    // Support classes
    public static class ContractSession {
        private final String contractAddress;
        private final String contractName;
        private final long createdAt;
        private long lastInteraction;
        
        public ContractSession(String contractAddress, String contractName, long createdAt) {
            this.contractAddress = contractAddress;
            this.contractName = contractName;
            this.createdAt = createdAt;
            this.lastInteraction = createdAt;
        }
        
        public String getContractAddress() { return contractAddress; }
        public String getContractName() { return contractName; }
        public long getCreatedAt() { return createdAt; }
        public long getLastInteraction() { return lastInteraction; }
        public void updateLastInteraction() { this.lastInteraction = System.currentTimeMillis(); }
    }
    
    public static class ContractStatistics {
        private final int contractsCompiled;
        private final int contractsDeployed;
        private final int functionCalls;
        private final long totalGasUsed;
        private final int activeSessions;
        
        public ContractStatistics(int contractsCompiled, int contractsDeployed, int functionCalls, 
                                long totalGasUsed, int activeSessions) {
            this.contractsCompiled = contractsCompiled;
            this.contractsDeployed = contractsDeployed;
            this.functionCalls = functionCalls;
            this.totalGasUsed = totalGasUsed;
            this.activeSessions = activeSessions;
        }
        
        public int getContractsCompiled() { return contractsCompiled; }
        public int getContractsDeployed() { return contractsDeployed; }
        public int getFunctionCalls() { return functionCalls; }
        public long getTotalGasUsed() { return totalGasUsed; }
        public int getActiveSessions() { return activeSessions; }
    }
    
    public interface SmartContractEventListener {
        default void onContractCompiled(SmartContractController.ContractCompilationInfo compilationInfo) {}
        default void onContractDeployed(SmartContractController.ContractInfo contractInfo) {}
        default void onFunctionCalled(SmartContractController.ContractCallInfo callInfo) {}
        default void onEventDetected(EventLog.Event event) {}
    }
}