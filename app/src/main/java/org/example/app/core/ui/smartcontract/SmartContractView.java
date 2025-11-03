package org.example.app.core.ui.smartcontract;

import org.example.app.core.ui.smartcontract.SmartContractController.*;
import org.example.app.core.smartcontract.EventLog;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.cell.PropertyValueFactory;

import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.logging.Level;



public class SmartContractView {
    private static final Logger LOGGER = Logger.getLogger(SmartContractView.class.getName());
    
    private final SmartContractController smartContractController;
    private final SmartContractManager smartContractManager;
    private final DecimalFormat decimalFormat = new DecimalFormat("#,###");
    
    // Callback for dashboard updates
    private Runnable dashboardUpdateCallback;
    private Runnable navigationCallback;
    
    // ===== NEW: Wallet integration components =====
    private Label walletStatusLabel;
    private Button walletActionButton;
    private VBox walletStatusSection;
    
    // UI state
    private String selectedContractAddress;
    private ObservableList<ContractListItem> deployedContractsList;
    private ObservableList<EventListItem> contractEventsList;
    
    // Store compilation result for deployment
    private ContractCompilationResult lastCompilationResult;
    
    public SmartContractView(SmartContractController controller, SmartContractManager manager) {
        this.smartContractController = controller;
        this.smartContractManager = manager;
        this.deployedContractsList = FXCollections.observableArrayList();
        this.contractEventsList = FXCollections.observableArrayList();
        
        // Set up event listener
        smartContractManager.addEventListener(new SmartContractManager.SmartContractEventListener() {
            @Override
            public void onContractDeployed(ContractInfo contractInfo) {
                Platform.runLater(() -> refreshContractsList());
            }
            
            @Override
            public void onEventDetected(EventLog.Event event) {
                Platform.runLater(() -> refreshEventsList());
            }
        });
        
        LOGGER.info("SmartContractView initialized with enhanced wallet integration");
    }
    
    public void setDashboardUpdateCallback(Runnable callback) {
        this.dashboardUpdateCallback = callback;
    }
    
    public void setNavigationCallback(Runnable callback) {
        this.navigationCallback = callback;
    }
    
    /**
     * Creates the main smart contract management interface with enhanced wallet integration
     */
    /**
     * ===== NEW: Creates enhanced header with real-time wallet monitoring =====
     */
    private HBox createEnhancedSmartContractHeader() {
        HBox headerBox = new HBox(20);
        headerBox.setStyle("-fx-padding: 0 0 20 0; -fx-alignment: center-left;");
        
        Label headerLabel = new Label("⚡ Smart Contract Manager");
        headerLabel.setStyle("-fx-text-fill: #00ff88; -fx-font-size: 32px; -fx-font-weight: bold;");
        
        Label subtitleLabel = new Label("Deploy, interact with, and monitor smart contracts on TINC");
        subtitleLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 16px;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // ✅ ENHANCED: Real-time wallet status display
        walletStatusLabel = new Label();
        walletActionButton = new Button();
        
        Button refreshButton = new Button("🔄 Refresh");
        refreshButton.setStyle("-fx-background-color: #40e0d0; -fx-text-fill: black; -fx-font-weight: bold; " +
                              "-fx-padding: 8 16; -fx-background-radius: 6; -fx-font-size: 12px;");
        refreshButton.setOnAction(e -> refreshWalletStatus());
        
        Button backButton = new Button("← Back to Dashboard");
        backButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #40e0d0; " +
                           "-fx-border-color: #40e0d0; -fx-border-width: 2; -fx-font-weight: bold; " +
                           "-fx-padding: 8 16; -fx-background-radius: 6; -fx-font-size: 12px;");
        backButton.setOnAction(e -> {
            if (navigationCallback != null) {
                navigationCallback.run();
            }
        });
        
        VBox titleBox = new VBox(5);
        titleBox.getChildren().addAll(headerLabel, subtitleLabel);
        
        HBox controlsBox = new HBox(10);
        controlsBox.getChildren().addAll(walletStatusLabel, walletActionButton, refreshButton, backButton);
        
        headerBox.getChildren().addAll(titleBox, spacer, controlsBox);
        
        // ✅ NEW: Initialize wallet status immediately
        updateWalletStatusDisplay();
        
        return headerBox;
    }
    

// ADD this new method to properly update wallet status:
private void updateWalletStatusLabel(Label walletStatusLabel) {
    try {
        boolean hasWallet = smartContractController.isWalletAvailable();
        String activeWalletId = smartContractController.getActiveWalletId();
        
        if (hasWallet && activeWalletId != null) {
            org.example.app.core.ui.wallet.WalletController.WalletBalanceInfo walletInfo = 
                smartContractController.getCurrentWalletInfo();
            if (walletInfo != null) {
                walletStatusLabel.setText("✅ Wallet: " + activeWalletId + " (" + walletInfo.nativeBalance + " TINC)");
                walletStatusLabel.setStyle("-fx-text-fill: #00ff88; -fx-font-weight: bold; -fx-font-size: 12px;");
            } else {
                walletStatusLabel.setText("⚠️ Wallet loaded but no balance info");
                walletStatusLabel.setStyle("-fx-text-fill: #ffa500; -fx-font-weight: bold; -fx-font-size: 12px;");
            }
        } else {
            walletStatusLabel.setText("❌ No Active Wallet");
            walletStatusLabel.setStyle("-fx-text-fill: #ff6b6b; -fx-font-weight: bold; -fx-font-size: 12px;");
        }
    } catch (Exception e) {
        walletStatusLabel.setText("❌ Wallet Error");
        walletStatusLabel.setStyle("-fx-text-fill: #ff6b6b; -fx-font-weight: bold; -fx-font-size: 12px;");
        LOGGER.log(Level.SEVERE, "Error updating wallet status", e);
    }
}

/**
     * ===== NEW: Creates wallet integration status section =====
     */
    private VBox createWalletIntegrationSection() {
        VBox integrationSection = new VBox(15);
        integrationSection.setStyle("-fx-background-color: #22334e; -fx-background-radius: 12; -fx-padding: 20;");
        
        Label integrationTitle = new Label("🔗 Wallet Integration Status");
        integrationTitle.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        
        // Wallet connection status
        HBox connectionStatus = createWalletConnectionStatus();
        
        // Integration actions
        HBox integrationActions = createWalletIntegrationActions();
        
        integrationSection.getChildren().addAll(integrationTitle, connectionStatus, integrationActions);
        
        return integrationSection;
    }
    
    /**
     * ===== NEW: Creates wallet connection status display =====
     */
    private HBox createWalletConnectionStatus() {
        HBox statusBox = new HBox(20);
        statusBox.setStyle("-fx-alignment: center-left; -fx-padding: 10 0;");
        
        // Wallet availability indicator
        Label walletAvailableLabel = new Label();
        updateWalletAvailabilityIndicator(walletAvailableLabel);
        
        // Wallet details display
        Label walletDetailsLabel = new Label();
        updateWalletDetailsDisplay(walletDetailsLabel);
        
        // Connection health indicator
        Label connectionHealthLabel = new Label();
        updateConnectionHealthIndicator(connectionHealthLabel);
        
        statusBox.getChildren().addAll(walletAvailableLabel, walletDetailsLabel, connectionHealthLabel);
        
        return statusBox;
    }
    
    /**
     * ===== NEW: Creates wallet integration actions =====
     */
    private HBox createWalletIntegrationActions() {
        HBox actionsBox = new HBox(15);
        actionsBox.setStyle("-fx-alignment: center-left; -fx-padding: 10 0;");
        
        Button connectWalletBtn = new Button("🔗 Connect Wallet");
        connectWalletBtn.setStyle("-fx-background-color: #00ff88; -fx-text-fill: black; -fx-font-weight: bold; " +
                                 "-fx-padding: 10 20; -fx-background-radius: 8; -fx-font-size: 12px;");
        connectWalletBtn.setOnAction(e -> handleConnectWallet());
        
        Button testConnectionBtn = new Button("🧪 Test Connection");
        testConnectionBtn.setStyle("-fx-background-color: #40e0d0; -fx-text-fill: black; -fx-font-weight: bold; " +
                                  "-fx-padding: 10 20; -fx-background-radius: 8; -fx-font-size: 12px;");
        testConnectionBtn.setOnAction(e -> testWalletConnection());
        
        Button goToWalletBtn = new Button("👛 Wallet Management");
        goToWalletBtn.setStyle("-fx-background-color: #8a2be2; -fx-text-fill: white; -fx-font-weight: bold; " +
                              "-fx-padding: 10 20; -fx-background-radius: 8; -fx-font-size: 12px;");
        goToWalletBtn.setOnAction(e -> handleGoToWalletManagement());
        
        actionsBox.getChildren().addAll(connectWalletBtn, testConnectionBtn, goToWalletBtn);
        
        return actionsBox;
    }
    
    /**
     * ===== NEW: Refreshes wallet status in real-time =====
     */
    public void refreshWalletStatus() {
        LOGGER.info("Refreshing wallet status for smart contract integration");
        
        Platform.runLater(() -> {
            try {
                updateWalletStatusDisplay();
                updateWalletIntegrationSection();
                updateMainTabsBasedOnWalletStatus();
                
                LOGGER.info("Wallet status refreshed successfully");
            } catch (Exception e) {
                LOGGER.severe("Error refreshing wallet status: " + e.getMessage());
            }
        });
    }
    
    /**
     * ===== NEW: Updates wallet status display in real-time =====
     */
    private void updateWalletStatusDisplay() {
        try {
            boolean hasWallet = smartContractController.isWalletAvailable();
            String activeWalletId = smartContractController.getActiveWalletId();
            
            if (hasWallet && activeWalletId != null) {
                org.example.app.core.ui.wallet.WalletController.WalletBalanceInfo walletInfo = 
                    smartContractController.getCurrentWalletInfo();
                
                if (walletInfo != null) {
                    walletStatusLabel.setText("✅ Connected: " + activeWalletId + " (" + walletInfo.nativeBalance + " TINC)");
                    walletStatusLabel.setStyle("-fx-text-fill: #00ff88; -fx-font-weight: bold; -fx-font-size: 12px;");
                    
                    walletActionButton.setText("🚀 Ready to Deploy");
                    walletActionButton.setStyle("-fx-background-color: #00ff88; -fx-text-fill: black; " +
                                               "-fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6;");
                    walletActionButton.setOnAction(e -> showDeploymentReadyDialog());
                } else {
                    walletStatusLabel.setText("⚠️ Wallet Connected (No Balance Info)");
                    walletStatusLabel.setStyle("-fx-text-fill: #ffa500; -fx-font-weight: bold; -fx-font-size: 12px;");
                    
                    walletActionButton.setText("🔄 Refresh Wallet");
                    walletActionButton.setStyle("-fx-background-color: #ffa500; -fx-text-fill: black; " +
                                               "-fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6;");
                    walletActionButton.setOnAction(e -> refreshWalletStatus());
                }
            } else {
                walletStatusLabel.setText("❌ No Active Wallet");
                walletStatusLabel.setStyle("-fx-text-fill: #ff6b6b; -fx-font-weight: bold; -fx-font-size: 12px;");
                
                walletActionButton.setText("👛 Setup Wallet");
                walletActionButton.setStyle("-fx-background-color: #ff6b6b; -fx-text-fill: white; " +
                                           "-fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6;");
                walletActionButton.setOnAction(e -> handleSetupWallet());
            }
        } catch (Exception e) {
            walletStatusLabel.setText("❌ Status Error");
            walletStatusLabel.setStyle("-fx-text-fill: #ff6b6b; -fx-font-weight: bold; -fx-font-size: 12px;");
            LOGGER.log(Level.SEVERE, "Error updating wallet status", e);
        }
    }
    
    /**
     * ===== NEW: Updates wallet integration section =====
     */
    private void updateWalletIntegrationSection() {
        if (walletStatusSection != null) {
            // Update individual components
            walletStatusSection.getChildren().forEach(node -> {
                if (node instanceof HBox) {
                    HBox hbox = (HBox) node;
                    hbox.getChildren().forEach(child -> {
                        if (child instanceof Label) {
                            Label label = (Label) child;
                            if (label.getText().contains("Wallet Available") || label.getText().contains("❌") || label.getText().contains("✅")) {
                                updateWalletAvailabilityIndicator(label);
                            } else if (label.getText().contains("Details") || label.getText().contains("ID:")) {
                                updateWalletDetailsDisplay(label);
                            } else if (label.getText().contains("Connection") || label.getText().contains("Health")) {
                                updateConnectionHealthIndicator(label);
                            }
                        }
                    });
                }
            });
        }
    }
    
    /**
     * ===== NEW: Updates wallet availability indicator =====
     */
    private void updateWalletAvailabilityIndicator(Label indicator) {
        boolean hasWallet = smartContractController.isWalletAvailable();
        if (hasWallet) {
            indicator.setText("✅ Wallet Available");
            indicator.setStyle("-fx-text-fill: #00ff88; -fx-font-weight: bold;");
        } else {
            indicator.setText("❌ No Wallet");
            indicator.setStyle("-fx-text-fill: #ff6b6b; -fx-font-weight: bold;");
        }
    }
    
    /**
     * ===== NEW: Updates wallet details display =====
     */
    private void updateWalletDetailsDisplay(Label details) {
        String activeWalletId = smartContractController.getActiveWalletId();
        org.example.app.core.ui.wallet.WalletController.WalletBalanceInfo walletInfo = 
            smartContractController.getCurrentWalletInfo();
        
        if (activeWalletId != null && walletInfo != null) {
            details.setText("ID: " + activeWalletId + " | Balance: " + walletInfo.nativeBalance + " TINC");
            details.setStyle("-fx-text-fill: #40e0d0; -fx-font-size: 12px;");
        } else {
            details.setText("No wallet details available");
            details.setStyle("-fx-text-fill: #ccc; -fx-font-size: 12px;");
        }
    }
    
    /**
     * ===== NEW: Updates connection health indicator =====
     */
    private void updateConnectionHealthIndicator(Label health) {
        try {
            boolean isConnected = smartContractController.isWalletAvailable();
            String activeWalletId = smartContractController.getActiveWalletId();
            
            if (isConnected && activeWalletId != null) {
                health.setText("🟢 Connection Healthy");
                health.setStyle("-fx-text-fill: #00ff88; -fx-font-size: 12px;");
            } else {
                health.setText("🔴 Not Connected");
                health.setStyle("-fx-text-fill: #ff6b6b; -fx-font-size: 12px;");
            }
        } catch (Exception e) {
            health.setText("⚠️ Connection Error");
            health.setStyle("-fx-text-fill: #ffa500; -fx-font-size: 12px;");
        }
    }
    
    /**
     * ===== NEW: Updates main tabs based on wallet status =====
     */
    private void updateMainTabsBasedOnWalletStatus() {
        // This method can be expanded to enable/disable tabs based on wallet availability
        LOGGER.info("Main tabs updated based on wallet status");
    }
    
    // ===== ACTION HANDLERS =====
    
public void onWalletStateChanged() {
    LOGGER.info("Handling wallet state change in SmartContractView");
    
    Platform.runLater(() -> {
        try {
            // Refresh wallet status
            refreshWalletStatus();
            
            // Update wallet integration section
            updateWalletIntegrationSection();
            
            // Update main tabs based on wallet availability
            updateMainTabsBasedOnWalletStatus();
            
            // Check if smart contract access is now available
            boolean canAccess = smartContractController.isWalletAvailable();
            
            if (canAccess) {
                // Show success notification
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Wallet Connected Successfully! 🎉");
                alert.setHeaderText("Smart Contract Management Ready");
                alert.setContentText("Your wallet is now connected and smart contract management is fully operational!\n\n" +
                                   "You can now deploy and interact with smart contracts.");
                alert.showAndWait();
            }
            
            LOGGER.info("Wallet state change handled successfully");
            
        } catch (Exception e) {
            LOGGER.severe("Error handling wallet state change: " + e.getMessage());
        }
    });
}

    /**
     * ===== NEW: Handles wallet connection =====
     */
    private void handleConnectWallet() {
        LOGGER.info("Attempting to connect wallet to smart contract controller");
        
        try {
            // Trigger wallet refresh and connection
            refreshWalletStatus();
            
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Wallet Connection");
                alert.setHeaderText("Connecting Wallet");
                alert.setContentText("Attempting to connect your active wallet to smart contract management.\n\n" +
                                   "If no wallet is found, please:\n" +
                                   "1. Go to Wallet Management\n" +
                                   "2. Create or load a wallet\n" +
                                   "3. Return to Smart Contract Management");
                
                ButtonType refreshButton = new ButtonType("Refresh Status");
                ButtonType walletMgmtButton = new ButtonType("Go to Wallet Management");
                ButtonType okButton = new ButtonType("OK");
                alert.getButtonTypes().setAll(refreshButton, walletMgmtButton, okButton);
                
                alert.showAndWait().ifPresent(response -> {
                    if (response == refreshButton) {
                        refreshWalletStatus();
                    } else if (response == walletMgmtButton) {
                        handleGoToWalletManagement();
                    }
                });
            });
            
        } catch (Exception e) {
            LOGGER.severe("Error connecting wallet: " + e.getMessage());
            showErrorAlert("Failed to connect wallet: " + e.getMessage());
        }
    }
    
    /**
     * ===== NEW: Tests wallet connection =====
     */
    private void testWalletConnection() {
        LOGGER.info("Testing wallet connection");
        
        Platform.runLater(() -> {
            StringBuilder testResults = new StringBuilder();
            testResults.append("🧪 WALLET CONNECTION TEST RESULTS:\n\n");
            
            // Test wallet availability
            boolean hasWallet = smartContractController.isWalletAvailable();
            testResults.append("Wallet Available: ").append(hasWallet ? "✅ PASS" : "❌ FAIL").append("\n");
            
            // Test active wallet ID
            String activeWalletId = smartContractController.getActiveWalletId();
            testResults.append("Active Wallet ID: ").append(activeWalletId != null ? "✅ " + activeWalletId : "❌ NULL").append("\n");
            
            // Test wallet info
            org.example.app.core.ui.wallet.WalletController.WalletBalanceInfo walletInfo = 
                smartContractController.getCurrentWalletInfo();
            testResults.append("Wallet Info: ").append(walletInfo != null ? "✅ AVAILABLE" : "❌ NULL").append("\n");
            
            if (walletInfo != null) {
                testResults.append("   - Address: ").append(walletInfo.address).append("\n");
                testResults.append("   - Balance: ").append(walletInfo.nativeBalance).append(" TINC\n");
            }
            
            // Test smart contract controller state
            testResults.append("Smart Contract Controller: ").append(smartContractController != null ? "✅ ACTIVE" : "❌ NULL").append("\n");
            testResults.append("Smart Contract Manager: ").append(smartContractManager != null ? "✅ ACTIVE" : "❌ NULL").append("\n");
            
            testResults.append("\n");
            
            if (hasWallet && activeWalletId != null && walletInfo != null) {
                testResults.append("🎉 CONNECTION STATUS: READY FOR SMART CONTRACT DEPLOYMENT!");
            } else {
                testResults.append("⚠️ CONNECTION STATUS: WALLET SETUP REQUIRED");
            }
            
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Wallet Connection Test");
            alert.setHeaderText("Connection Test Results");
            alert.setContentText(testResults.toString());
            alert.showAndWait();
        });
    }
    
    /**
     * ===== NEW: Handles going to wallet management =====
     */
    private void handleGoToWalletManagement() {
        LOGGER.info("Navigating to wallet management from smart contract view");
        
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Navigate to Wallet Management");
            alert.setHeaderText("Switch to Wallet Management");
            alert.setContentText("You will be taken to Wallet Management to set up your wallet.\n\n" +
                               "After setting up your wallet, you can return to Smart Contract Management " +
                               "and your wallet will be automatically connected.\n\n" +
                               "Continue to Wallet Management?");
            
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK && navigationCallback != null) {
                    // This should trigger the dashboard to switch to wallet view
                    navigationCallback.run();
                    
                    // Then programmatically switch to wallet management
                    // This would need to be implemented in the dashboard controller
                    Platform.runLater(() -> {
                        try {
                            // Signal to switch to wallet management
                            // This should be handled by the dashboard controller
                            LOGGER.info("Requested navigation to wallet management");
                        } catch (Exception e) {
                            LOGGER.warning("Error requesting navigation: " + e.getMessage());
                        }
                    });
                }
            });
        });
    }
    
    /**
     * ===== NEW: Handles wallet setup =====
     */
    private void handleSetupWallet() {
        LOGGER.info("Handling wallet setup from smart contract view");
        handleGoToWalletManagement();
    }
    
    /**
     * ===== NEW: Shows deployment ready dialog =====
     */
    private void showDeploymentReadyDialog() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Smart Contract Deployment Ready");
            alert.setHeaderText("Wallet Connected Successfully!");
            alert.setContentText("Your wallet is connected and ready for smart contract deployment.\n\n" +
                               "You can now:\n" +
                               "✅ Compile Solidity contracts\n" +
                               "✅ Deploy contracts to the TINC blockchain\n" +
                               "✅ Interact with deployed contracts\n" +
                               "✅ Monitor contract events\n\n" +
                               "Use the tabs below to get started!");
            alert.showAndWait();
        });
    }
    
    /**
     * ===== NEW: Shows error alert with proper styling =====
     */
    private void showErrorAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Smart Contract Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

// ADD this FIXED dialog method with proper JavaFX styling:
private void showFixedWalletManagementDialog() {
    // Create custom dialog with proper styling
    Dialog<ButtonType> dialog = new Dialog<>();
    dialog.setTitle("Smart Contract Wallet Setup");
    dialog.setHeaderText("Wallet Management");
    
    // Create content with proper styling
    VBox content = new VBox(15);
    content.setStyle("-fx-background-color: #2b2b2b; -fx-padding: 20;");
    
    Label messageLabel = new Label("Smart contracts work better with an active wallet.\n\nChoose an option:");
    messageLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-wrap-text: true;");
    
    Button quickWalletBtn = new Button("🚀 Create Quick Wallet");
    quickWalletBtn.setStyle("-fx-background-color: #00ff88; -fx-text-fill: #000000; -fx-font-weight: bold; " +
                           "-fx-padding: 10 20; -fx-background-radius: 6; -fx-font-size: 12px;");
    quickWalletBtn.setOnAction(e -> {
        dialog.close();
        createQuickWalletForSmartContracts();
    });
    
    Button fullManagementBtn = new Button("👛 Go to Wallet Management");
    fullManagementBtn.setStyle("-fx-background-color: #40e0d0; -fx-text-fill: #000000; -fx-font-weight: bold; " +
                              "-fx-padding: 10 20; -fx-background-radius: 6; -fx-font-size: 12px;");
    fullManagementBtn.setOnAction(e -> {
        dialog.close();
        if (navigationCallback != null) {
            navigationCallback.run();
        }
    });
    
    Button stayButton = new Button("📋 Stay Here (View Only)");
    stayButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffffff; " +
                       "-fx-border-color: #ffffff; -fx-border-width: 1; -fx-font-weight: bold; " +
                       "-fx-padding: 10 20; -fx-background-radius: 6; -fx-font-size: 12px;");
    stayButton.setOnAction(e -> dialog.close());
    
    HBox buttonBox = new HBox(10);
    buttonBox.getChildren().addAll(quickWalletBtn, fullManagementBtn, stayButton);
    
    content.getChildren().addAll(messageLabel, buttonBox);
    
    // Set the content and show
    dialog.getDialogPane().setContent(content);
    dialog.getDialogPane().setStyle("-fx-background-color: #2b2b2b;");
    
    // Add close button
    ButtonType closeButtonType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
    dialog.getDialogPane().getButtonTypes().add(closeButtonType);
    
    dialog.showAndWait();
}

// ADD this method to create quick wallet:
private void createQuickWalletForSmartContracts() {
    try {
        // This will call your DashboardController's method through navigation callback
        showInfoAlert("Quick Wallet", 
            "Quick wallet creation will be handled by the dashboard.\n\n" +
            "If you have wallet components available, they will be used automatically.");
            
        // Try to notify dashboard if possible
        if (dashboardUpdateCallback != null) {
            dashboardUpdateCallback.run();
        }
    } catch (Exception e) {
        showInfoAlert("Error", "Could not create quick wallet: " + e.getMessage());
    }
}

// ADD this helper method for properly styled info alerts:
private void showInfoAlert(String title, String message) {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(message);
    
    // Style the dialog properly
    alert.getDialogPane().setStyle("-fx-background-color: #2b2b2b;");
    alert.getDialogPane().lookup(".content.label").setStyle("-fx-text-fill: #ffffff;");
    
    alert.showAndWait();
}
    /**
     * Creates the statistics section
     */
   
    /**
     * Creates a statistics card
     */
    private VBox createStatsCard(String title, String value, String color) {
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color: #22334e; -fx-background-radius: 12; " +
                      "-fx-padding: 25; -fx-pref-width: 200; -fx-alignment: center;");
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 14px; -fx-alignment: center;");
        
        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 24px; " +
                           "-fx-font-weight: bold; -fx-alignment: center;");
        
        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }
    
    /**
     * Creates the main tab pane with different smart contract operations
     */
    private TabPane createMainTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: #22334e;");
        
        // Contract Deployment Tab
        Tab deploymentTab = new Tab("Deploy Contract");
        deploymentTab.setClosable(false);
        deploymentTab.setContent(createDeploymentTab());
        
        // Contract Interaction Tab
        Tab interactionTab = new Tab("Interact");
        interactionTab.setClosable(false);
        interactionTab.setContent(createInteractionTab());
        
        // Contract Management Tab
        Tab managementTab = new Tab("Manage Contracts");
        managementTab.setClosable(false);
        managementTab.setContent(createManagementTab());
        
        // Event Monitor Tab
        Tab eventsTab = new Tab("Events");
        eventsTab.setClosable(false);
        eventsTab.setContent(createEventsTab());
        
        tabPane.getTabs().addAll(deploymentTab, interactionTab, managementTab, eventsTab);
        return tabPane;
    }
    
    /**
     * Creates the contract deployment tab content
     */
    private VBox createDeploymentTab() {
        VBox deploymentBox = new VBox(20);
        deploymentBox.setStyle("-fx-padding: 25; -fx-background-color: #16213e;");
        
        // Solidity Code Section
        Label codeLabel = new Label("Solidity Source Code:");
        codeLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");
        
        TextArea sourceCodeArea = new TextArea();
        sourceCodeArea.setPromptText("Enter your Solidity contract code here...\n\n" +
                                    "Example:\n" +
                                    "contract SimpleStorage {\n" +
                                    "    uint256 private storedValue;\n" +
                                    "    \n" +
                                    "    function store(uint256 value) public {\n" +
                                    "        storedValue = value;\n" +
                                    "    }\n" +
                                    "    \n" +
                                    "    function retrieve() public view returns (uint256) {\n" +
                                    "        return storedValue;\n" +
                                    "    }\n" +
                                    "}");
        sourceCodeArea.setPrefRowCount(12);
        sourceCodeArea.setStyle("-fx-background-color: #22334e; -fx-text-fill: white; " +
                               "-fx-border-color: #40e0d0; -fx-border-width: 1; -fx-background-radius: 6;");
        
        // Compilation Section
        HBox compileSection = new HBox(15);
        compileSection.setStyle("-fx-alignment: center-left;");
        
        TextField contractNameField = new TextField();
        contractNameField.setPromptText("Contract Name (auto-detected)");
        contractNameField.setStyle("-fx-background-color: #22334e; -fx-text-fill: white; " +
                                  "-fx-border-color: #40e0d0; -fx-border-radius: 6; -fx-padding: 8;");
        
        Button compileButton = new Button("🔨 Compile Contract");
        compileButton.setStyle("-fx-background-color: #00ff88; -fx-text-fill: black; -fx-font-weight: bold; " +
                              "-fx-padding: 12 24; -fx-background-radius: 8;");
        
        compileSection.getChildren().addAll(contractNameField, compileButton);
        
        // Deployment Section
        Label deployLabel = new Label("Deployment Parameters:");
        deployLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");
        
        HBox deploySection = new HBox(15);
        deploySection.setStyle("-fx-alignment: center-left;");
        
        TextField gasLimitField = new TextField("3000000");
        gasLimitField.setPromptText("Gas Limit");
        gasLimitField.setStyle("-fx-background-color: #22334e; -fx-text-fill: white; " +
                              "-fx-border-color: #40e0d0; -fx-border-radius: 6; -fx-padding: 8;");
        
        Button deployButton = new Button("🚀 Deploy Contract");
        deployButton.setStyle("-fx-background-color: #40e0d0; -fx-text-fill: black; -fx-font-weight: bold; " +
                             "-fx-padding: 12 24; -fx-background-radius: 8;");
        deployButton.setDisable(true);
        
        deploySection.getChildren().addAll(gasLimitField, deployButton);
        
        // Status Area
        TextArea statusArea = new TextArea();
        statusArea.setEditable(false);
        statusArea.setPrefRowCount(4);
        statusArea.setStyle("-fx-background-color: #22334e; -fx-text-fill: #ccc; " +
                           "-fx-border-color: #555; -fx-border-width: 1; -fx-background-radius: 6;");
        statusArea.setText("Ready to compile contract...");
        
        // Event Handlers
        compileButton.setOnAction(e -> {
            String sourceCode = sourceCodeArea.getText().trim();
            String contractName = contractNameField.getText().trim();
            
            if (contractName.isEmpty()) {
                contractName = SmartContractUIUtils.extractContractName(sourceCode);
                contractNameField.setText(contractName);
            }
            
            compileContract(sourceCode, contractName, statusArea, deployButton);
        });
        
        deployButton.setOnAction(e -> {
            String gasLimitStr = gasLimitField.getText().trim();
            deployContract(contractNameField.getText(), gasLimitStr, statusArea);
        });
        
        deploymentBox.getChildren().addAll(
            codeLabel, sourceCodeArea,
            compileSection,
            deployLabel, deploySection,
            new Label("Status:") {{ setStyle("-fx-text-fill: white; -fx-font-weight: bold;"); }},
            statusArea
        );
        
        return deploymentBox;
    }

    
    
    /**
     * Creates the contract interaction tab content
     */
    private VBox createInteractionTab() {
        VBox interactionBox = new VBox(20);
        interactionBox.setStyle("-fx-padding: 25; -fx-background-color: #16213e;");
        
        // Contract Selection
        Label selectLabel = new Label("Select Contract:");
        selectLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");
        
        ComboBox<String> contractComboBox = new ComboBox<>();
        contractComboBox.setPromptText("Choose a deployed contract...");
        contractComboBox.setStyle("-fx-background-color: #22334e; -fx-text-fill: white;");
        
        // Function Call Section
        Label functionLabel = new Label("Function Call:");
        functionLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");
        
        HBox functionSection = new HBox(15);
        functionSection.setStyle("-fx-alignment: center-left;");
        
        TextField functionField = new TextField();
        functionField.setPromptText("e.g., store(uint256)");
        functionField.setStyle("-fx-background-color: #22334e; -fx-text-fill: white; " +
                              "-fx-border-color: #40e0d0; -fx-border-radius: 6; -fx-padding: 8;");
        
        TextField parametersField = new TextField();
        parametersField.setPromptText("Parameters: e.g., 42");
        parametersField.setStyle("-fx-background-color: #22334e; -fx-text-fill: white; " +
                                "-fx-border-color: #40e0d0; -fx-border-radius: 6; -fx-padding: 8;");
        
        functionSection.getChildren().addAll(functionField, parametersField);
        
        // Transaction Options
        HBox optionsSection = new HBox(15);
        optionsSection.setStyle("-fx-alignment: center-left;");
        
        TextField gasLimitField = new TextField("300000");
        gasLimitField.setPromptText("Gas Limit");
        gasLimitField.setStyle("-fx-background-color: #22334e; -fx-text-fill: white; " +
                              "-fx-border-color: #40e0d0; -fx-border-radius: 6; -fx-padding: 8;");
        
        TextField valueField = new TextField("0");
        valueField.setPromptText("Value (Wei)");
        valueField.setStyle("-fx-background-color: #22334e; -fx-text-fill: white; " +
                           "-fx-border-color: #40e0d0; -fx-border-radius: 6; -fx-padding: 8;");
        
        Button callButton = new Button("📞 Call Function");
        callButton.setStyle("-fx-background-color: #8a2be2; -fx-text-fill: white; -fx-font-weight: bold; " +
                           "-fx-padding: 12 24; -fx-background-radius: 8;");
        
        optionsSection.getChildren().addAll(gasLimitField, valueField, callButton);
        
        // Results Area
        TextArea resultsArea = new TextArea();
        resultsArea.setEditable(false);
        resultsArea.setPrefRowCount(6);
        resultsArea.setStyle("-fx-background-color: #22334e; -fx-text-fill: #ccc; " +
                            "-fx-border-color: #555; -fx-border-width: 1; -fx-background-radius: 6;");
        resultsArea.setText("Select a contract and function to interact with...");
        
        // Event Handlers
        contractComboBox.setOnAction(e -> {
            selectedContractAddress = contractComboBox.getValue();
            resultsArea.setText("Selected contract: " + selectedContractAddress);
        });
        
        callButton.setOnAction(e -> {
            String contractAddress = contractComboBox.getValue();
            String functionSignature = functionField.getText().trim();
            String parameters = parametersField.getText().trim();
            String gasLimitStr = gasLimitField.getText().trim();
            String valueStr = valueField.getText().trim();
            
            callContractFunction(contractAddress, functionSignature, parameters, gasLimitStr, valueStr, resultsArea);
        });
        
        // Refresh contracts list when tab is loaded
        Platform.runLater(() -> {
            refreshContractComboBox(contractComboBox);
        });
        
        interactionBox.getChildren().addAll(
            selectLabel, contractComboBox,
            functionLabel, functionSection,
            new Label("Transaction Options:") {{ setStyle("-fx-text-fill: white; -fx-font-weight: bold;"); }},
            optionsSection,
            new Label("Results:") {{ setStyle("-fx-text-fill: white; -fx-font-weight: bold;"); }},
            resultsArea
        );
        
        return interactionBox;
    }
    
    /**
     * Creates the contract management tab content
     */
    private VBox createManagementTab() {
        VBox managementBox = new VBox(20);
        managementBox.setStyle("-fx-padding: 25; -fx-background-color: #16213e;");
        
        Label manageLabel = new Label("📋 Deployed Contracts");
        manageLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        
        // Contracts Table
        TableView<ContractListItem> contractsTable = new TableView<>();
        contractsTable.setStyle("-fx-background-color: #22334e;");
        contractsTable.setPrefHeight(300);
        
        TableColumn<ContractListItem, String> nameColumn = new TableColumn<>("Contract Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("contractName"));
        nameColumn.setPrefWidth(150);
        
        TableColumn<ContractListItem, String> addressColumn = new TableColumn<>("Address");
        addressColumn.setCellValueFactory(new PropertyValueFactory<>("contractAddress"));
        addressColumn.setPrefWidth(200);
        
        TableColumn<ContractListItem, String> deployerColumn = new TableColumn<>("Deployer");
        deployerColumn.setCellValueFactory(new PropertyValueFactory<>("deployer"));
        deployerColumn.setPrefWidth(150);
        
        TableColumn<ContractListItem, String> gasUsedColumn = new TableColumn<>("Gas Used");
        gasUsedColumn.setCellValueFactory(new PropertyValueFactory<>("gasUsed"));
        gasUsedColumn.setPrefWidth(100);
        
        TableColumn<ContractListItem, String> timestampColumn = new TableColumn<>("Deployed");
        timestampColumn.setCellValueFactory(new PropertyValueFactory<>("deploymentTime"));
        timestampColumn.setPrefWidth(150);
        
        contractsTable.getColumns().clear();
contractsTable.getColumns().add(nameColumn);
contractsTable.getColumns().add(addressColumn);
contractsTable.getColumns().add(deployerColumn);
contractsTable.getColumns().add(gasUsedColumn);
contractsTable.getColumns().add(timestampColumn);

        contractsTable.setItems(deployedContractsList);
        
        // Action Buttons
        HBox actionButtons = new HBox(15);
        actionButtons.setStyle("-fx-alignment: center-left;");
        
        Button refreshButton = new Button("🔄 Refresh");
        refreshButton.setStyle("-fx-background-color: #00ff88; -fx-text-fill: black; -fx-font-weight: bold; " +
                              "-fx-padding: 8 16; -fx-background-radius: 6;");
        refreshButton.setOnAction(e -> refreshContractsList());
        
        Button inspectButton = new Button("🔍 Inspect");
        inspectButton.setStyle("-fx-background-color: #40e0d0; -fx-text-fill: black; -fx-font-weight: bold; " +
                              "-fx-padding: 8 16; -fx-background-radius: 6;");
        inspectButton.setOnAction(e -> {
            ContractListItem selected = contractsTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                inspectContract(selected.getContractAddress());
            }
        });
        
        actionButtons.getChildren().addAll(refreshButton, inspectButton);
        
        managementBox.getChildren().addAll(manageLabel, contractsTable, actionButtons);
        return managementBox;
    }

      /**
     * Creates the statistics section - ENHANCED with wallet status
     */
    private HBox createSmartContractStatsSection() {
        HBox statsBox = new HBox(20);
        statsBox.setStyle("-fx-padding: 20 0;");
        
        SmartContractManager.ContractStatistics stats = smartContractManager.getContractStatistics();
        
        VBox compiledCard = createStatsCard("Contracts Compiled", 
            String.valueOf(stats.getContractsCompiled()), "#00ff88");
        VBox deployedCard = createStatsCard("Contracts Deployed", 
            String.valueOf(stats.getContractsDeployed()), "#40e0d0");
        VBox callsCard = createStatsCard("Function Calls", 
            String.valueOf(stats.getFunctionCalls()), "#8a2be2");
        
        // ✅ NEW: Add wallet status card
        boolean hasWallet = smartContractController.isWalletAvailable();
        VBox walletCard = createStatsCard("Wallet Status", 
            hasWallet ? "Connected" : "Not Connected", 
            hasWallet ? "#00ff88" : "#ff6b6b");
        
        statsBox.getChildren().addAll(compiledCard, deployedCard, callsCard, walletCard);
        return statsBox;
    }
    

      public void onReturnFromWalletManagement() {
        LOGGER.info("Handling return from wallet management");
        Platform.runLater(() -> {
            refreshWalletStatus();
            updateWalletStatusDisplay();
        });
    }

    /**
     * Creates the events monitoring tab content
     */
    private VBox createEventsTab() {
        VBox eventsBox = new VBox(20);
        eventsBox.setStyle("-fx-padding: 25; -fx-background-color: #16213e;");
        
        // Header with controls
        HBox eventsHeader = new HBox(20);
        eventsHeader.setStyle("-fx-alignment: center-left;");
        
        Label eventsLabel = new Label("📡 Contract Events");
        eventsLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button startMonitoringButton = new Button("▶️ Start Monitoring");
        startMonitoringButton.setStyle("-fx-background-color: #00ff88; -fx-text-fill: black; -fx-font-weight: bold; " +
                                     "-fx-padding: 8 16; -fx-background-radius: 6;");
        startMonitoringButton.setOnAction(e -> {
            smartContractManager.startEventMonitoring();
            startMonitoringButton.setText("⏸️ Stop Monitoring");
            startMonitoringButton.setOnAction(evt -> {
                smartContractManager.stopEventMonitoring();
                startMonitoringButton.setText("▶️ Start Monitoring");
            });
        });
        
        Button clearEventsButton = new Button("🗑️ Clear Events");
        clearEventsButton.setStyle("-fx-background-color: #ff6b6b; -fx-text-fill: white; -fx-font-weight: bold; " +
                                  "-fx-padding: 8 16; -fx-background-radius: 6;");
        clearEventsButton.setOnAction(e -> {
            smartContractManager.clearEventLog();
            contractEventsList.clear();
        });
        
        eventsHeader.getChildren().addAll(eventsLabel, spacer, startMonitoringButton, clearEventsButton);
        
        // Events Table
        TableView<EventListItem> eventsTable = new TableView<>();
        eventsTable.setStyle("-fx-background-color: #22334e;");
        eventsTable.setPrefHeight(350);
        
        TableColumn<EventListItem, String> contractColumn = new TableColumn<>("Contract");
        contractColumn.setCellValueFactory(new PropertyValueFactory<>("contractAddress"));
        contractColumn.setPrefWidth(150);
        
        TableColumn<EventListItem, String> eventColumn = new TableColumn<>("Event");
        eventColumn.setCellValueFactory(new PropertyValueFactory<>("eventSignature"));
        eventColumn.setPrefWidth(200);
        
        TableColumn<EventListItem, String> blockColumn = new TableColumn<>("Block");
        blockColumn.setCellValueFactory(new PropertyValueFactory<>("blockNumber"));
        blockColumn.setPrefWidth(100);
        
        TableColumn<EventListItem, String> txColumn = new TableColumn<>("Transaction");
        txColumn.setCellValueFactory(new PropertyValueFactory<>("transactionHash"));
        txColumn.setPrefWidth(150);
        
        TableColumn<EventListItem, String> dataColumn = new TableColumn<>("Data");
        dataColumn.setCellValueFactory(new PropertyValueFactory<>("eventData"));
        dataColumn.setPrefWidth(200);
        
        eventsTable.getColumns().clear();
        eventsTable.getColumns().add(contractColumn);
        eventsTable.getColumns().add(eventColumn);
        eventsTable.getColumns().add(blockColumn);
       eventsTable.getColumns().add(txColumn);
       eventsTable.getColumns().add(dataColumn);
        eventsTable.setItems(contractEventsList);
        
        eventsBox.getChildren().addAll(eventsHeader, eventsTable);
        return eventsBox;
    }
    
    // Event Handlers and Helper Methods
    
    private void compileContract(String sourceCode, String contractName, TextArea statusArea, Button deployButton) {
        statusArea.setText("Compiling contract...");
        deployButton.setDisable(true);
        lastCompilationResult = null;
        
        CompletableFuture<ContractCompilationResult> future = 
            smartContractManager.compileContractAsync(contractName, sourceCode);
        
        future.thenAccept(result -> {
            Platform.runLater(() -> {
                if (result.success) {
                    lastCompilationResult = result; // Store real compilation result
                    statusArea.setText("✅ Compilation successful!\n" +
                                     "Contract: " + result.compilationInfo.contractName + "\n" +
                                     "Bytecode size: " + result.compilationInfo.bytecode.length + " bytes\n" +
                                     "ABI: " + (result.compilationInfo.abi != null ? "Generated" : "None") + "\n" +
                                     "✅ Ready for PRODUCTION deployment!");
                    deployButton.setDisable(false);
                } else {
                    statusArea.setText("❌ Compilation failed:\n" + result.message);
                    deployButton.setDisable(true);
                }
            });
        });
    }

    private void deployContract(String contractName, String gasLimitStr, TextArea statusArea) {
        if (!SmartContractUIUtils.isValidGasLimit(gasLimitStr)) {
            statusArea.setText("❌ Invalid gas limit");
            return;
        }
        
        if (lastCompilationResult == null || !lastCompilationResult.success) {
            statusArea.setText("❌ No compiled contract available. Please compile first.");
            return;
        }
        
        statusArea.setText("🚀 Deploying REAL contract to TINC blockchain...\n" +
                          "Using compiled bytecode (" + lastCompilationResult.compilationInfo.bytecode.length + " bytes)");
        
        int gasLimit = Integer.parseInt(gasLimitStr);
        
        // ✅ PRODUCTION DEPLOYMENT - Using REAL compiled bytecode and ABI
        byte[] realBytecode = lastCompilationResult.compilationInfo.bytecode;
        String realAbi = lastCompilationResult.compilationInfo.abi;
        
        CompletableFuture<ContractDeploymentResult> future = 
            smartContractManager.deployContractAsync(contractName, realBytecode, realAbi, gasLimit);
        
        future.thenAccept(result -> {
            Platform.runLater(() -> {
                if (result.success) {
                    statusArea.setText("✅ PRODUCTION CONTRACT DEPLOYED SUCCESSFULLY! 🎉\n\n" +
                                     "📍 Contract Address: " + result.contractInfo.contractAddress + "\n" +
                                     "⛽ Gas Used: " + decimalFormat.format(result.contractInfo.gasUsed) + "\n" +
                                     "👤 Deployer: " + result.contractInfo.deployer + "\n" +
                                     "📦 Bytecode Size: " + result.contractInfo.bytecode.length + " bytes\n" +
                                     "🔗 Transaction added to mempool\n" +
                                     "💾 Contract state stored in EvmState\n\n" +
                                     "Your contract is now LIVE on the TINC blockchain! 🚀");
                    
                    // Clear compilation result after successful deployment
                    lastCompilationResult = null;
                    
                    if (dashboardUpdateCallback != null) {
                        dashboardUpdateCallback.run();
                    }
                } else {
                    statusArea.setText("❌ DEPLOYMENT FAILED:\n" + result.message);
                }
            });
        });
    }
    
    private void callContractFunction(String contractAddress, String functionSignature, 
                                    String parameters, String gasLimitStr, String valueStr, TextArea resultsArea) {
        if (contractAddress == null || contractAddress.isEmpty()) {
            resultsArea.setText("❌ Please select a contract");
            return;
        }
        
        if (!SmartContractUIUtils.isValidFunctionSignature(functionSignature)) {
            resultsArea.setText("❌ Invalid function signature");
            return;
        }
        
        resultsArea.setText("Calling function...");
        
        Object[] args = SmartContractUIUtils.parseFunctionParameters(parameters);
        int gasLimit = Integer.parseInt(gasLimitStr);
        long value = Long.parseLong(valueStr);
        
        CompletableFuture<ContractCallResult> future = 
            smartContractManager.callContractFunctionAsync(contractAddress, functionSignature, args, gasLimit, value);
        
        future.thenAccept(result -> {
            Platform.runLater(() -> {
                if (result.success) {
                    resultsArea.setText("✅ Function call successful!\n" +
                                      "Transaction: " + result.callInfo.transactionHash + "\n" +
                                      "Gas used: " + result.callInfo.gasUsed + "\n" +
                                      "Return data: " + SmartContractUIUtils.formatEventData(result.callInfo.returnData));
                    
                    if (dashboardUpdateCallback != null) {
                        dashboardUpdateCallback.run();
                    }
                } else {
                    resultsArea.setText("❌ Function call failed:\n" + result.message);
                }
            });
        });
    }
    
    private void inspectContract(String contractAddress) {
        ContractInfo contractInfo = smartContractManager.getContractInfo(contractAddress);
        if (contractInfo != null) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Contract Inspector");
            alert.setHeaderText("Contract Details");
            alert.setContentText(String.format(
                "Name: %s\n" +
                "Address: %s\n" +
                "Deployer: %s\n" +
                "Bytecode Size: %d bytes\n" +
                "Gas Used: %d\n" +
                "Deployed: %s",
                contractInfo.contractName,
                contractInfo.contractAddress,
                contractInfo.deployer,
                contractInfo.bytecode.length,
                contractInfo.gasUsed,
                SmartContractUIUtils.formatTimestamp(contractInfo.deploymentTime)
            ));
            alert.showAndWait();
        }
    }
    
    private void refreshContractsList() {
        deployedContractsList.clear();
        List<ContractInfo> contracts = smartContractManager.getDeployedContracts();
        
        for (ContractInfo contract : contracts) {
            deployedContractsList.add(new ContractListItem(
                contract.contractName,
                SmartContractUIUtils.formatContractAddress(contract.contractAddress),
                SmartContractUIUtils.formatContractAddress(contract.deployer),
                String.valueOf(contract.gasUsed),
                SmartContractUIUtils.formatTimestamp(contract.deploymentTime)
            ));
        }
    }
    
    private void refreshContractComboBox(ComboBox<String> comboBox) {
        comboBox.getItems().clear();
        List<ContractInfo> contracts = smartContractManager.getDeployedContracts();
        
        for (ContractInfo contract : contracts) {
            comboBox.getItems().add(contract.contractAddress);
        }
    }
    
    private void refreshEventsList() {
        contractEventsList.clear();
        
        for (String contractAddress : smartContractManager.getDeployedContracts()
                .stream().map(c -> c.contractAddress).toArray(String[]::new)) {
            List<EventLog.Event> events = smartContractManager.getContractEvents(contractAddress, null);
            
            for (EventLog.Event event : events) {
                contractEventsList.add(new EventListItem(
                    SmartContractUIUtils.formatContractAddress(event.getContractAddress()),
                    event.getEventSignature(),
                    String.valueOf(event.getBlockNumber()),
                    SmartContractUIUtils.formatContractAddress(event.getTransactionHash()),
                    SmartContractUIUtils.formatEventData(event.getData())
                ));
            }
        }
    }

    
    // Table Item Classes
    public static class ContractListItem {
        private final String contractName;
        private final String contractAddress;
        private final String deployer;
        private final String gasUsed;
        private final String deploymentTime;
        
        public ContractListItem(String contractName, String contractAddress, String deployer, 
                              String gasUsed, String deploymentTime) {
            this.contractName = contractName;
            this.contractAddress = contractAddress;
            this.deployer = deployer;
            this.gasUsed = gasUsed;
            this.deploymentTime = deploymentTime;
        }
        
        public String getContractName() { return contractName; }
        public String getContractAddress() { return contractAddress; }
        public String getDeployer() { return deployer; }
        public String getGasUsed() { return gasUsed; }
        public String getDeploymentTime() { return deploymentTime; }
    }

    public static class EventListItem {
        private final String contractAddress;
        private final String eventSignature;
        private final String blockNumber;
        private final String transactionHash;
        private final String eventData;
        
        public EventListItem(String contractAddress, String eventSignature, String blockNumber,
                           String transactionHash, String eventData) {
            this.contractAddress = contractAddress;
            this.eventSignature = eventSignature;
            this.blockNumber = blockNumber;
            this.transactionHash = transactionHash;
            this.eventData = eventData;
        }
        
        public String getContractAddress() { return contractAddress; }
        public String getEventSignature() { return eventSignature; }
        public String getBlockNumber() { return blockNumber; }
        public String getTransactionHash() { return transactionHash; }
        public String getEventData() { return eventData; }
    }


  /**
     * Creates the main smart contract management interface for dashboard integration
     */
    public VBox createSmartContractManagementView() {
        VBox smartContractContainer = new VBox(25);
        smartContractContainer.setStyle("-fx-background-color: #16213e; -fx-padding: 30;");
        
        // ===== ENHANCED: Header section with real-time wallet status =====
        HBox headerSection = createEnhancedSmartContractHeader();
        
        // ===== NEW: Wallet integration status section =====
        walletStatusSection = createWalletIntegrationSection();
        
        // Statistics section
        HBox statsSection = createSmartContractStatsSection();
        
        // Main content tabs
        TabPane mainTabs = createMainTabPane();
        
        smartContractContainer.getChildren().addAll(
            headerSection, 
            walletStatusSection, 
            statsSection, 
            mainTabs
        );
        
        // Load initial data and update wallet status
        refreshContractsList();
        refreshEventsList();
        refreshWalletStatus(); // ✅ NEW: Immediate wallet status check
        
        return smartContractContainer;
    }
}