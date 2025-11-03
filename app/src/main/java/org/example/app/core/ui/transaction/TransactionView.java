package org.example.app.core.ui.transaction;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;

import org.example.app.core.block.Transaction;
import org.example.app.core.block.Block;
import org.example.app.core.ui.transaction.TransactionUIUtils.TransactionCreationParams;
import org.example.app.core.ui.transaction.UITransactionManager.TransactionStatistics;

import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * TransactionView - Main transaction view management component
 * Provides comprehensive transaction UI views and interfaces
 * Based on your proven transaction management patterns
 */
public class TransactionView {
    private static final Logger LOGGER = Logger.getLogger(TransactionView.class.getName());
    
    private final TransactionController transactionController;
    private final UITransactionManager transactionManager;
    
    // UI Components
    private VBox mainContainer;
    private HBox statsSection;
    private VBox pendingTransactionsSection;
    private VBox transactionFormSection;
    
    // Callback for dashboard integration
    private Runnable dashboardUpdateCallback;
    
    public TransactionView(TransactionController transactionController, UITransactionManager transactionManager) {
        this.transactionController = transactionController;
        this.transactionManager = transactionManager;
        
        LOGGER.info("TransactionView initialized");
    }
    
    /**
     * Sets callback for dashboard updates
     */
    public void setDashboardUpdateCallback(Runnable callback) {
        this.dashboardUpdateCallback = callback;
    }
    
    /**
     * Creates the main transaction management view
     */
    public VBox createTransactionManagementView() {
        LOGGER.info("Creating transaction management view");
        
        mainContainer = new VBox(25);
        mainContainer.setStyle("-fx-background-color: #16213e; -fx-padding: 30;");
        
        // Header section
        VBox headerSection = createHeaderSection();
        
        // Transaction statistics section
        statsSection = createTransactionStatsSection();
        
        // Transaction actions section
        HBox actionsSection = createTransactionActionsSection();
        
        // Pending transactions section
        pendingTransactionsSection = createPendingTransactionsSection();
        
        // Transaction creation form section
        transactionFormSection = createTransactionFormSection();
        
        mainContainer.getChildren().addAll(
            headerSection,
            statsSection,
            actionsSection,
            pendingTransactionsSection,
            transactionFormSection
        );
        
        return mainContainer;
    }
    
    /**
     * Creates the header section with title and back button
     */
    private VBox createHeaderSection() {
        HBox headerBox = new HBox(20);
        headerBox.setStyle("-fx-padding: 0 0 20 0; -fx-alignment: center-left;");
        
        VBox titleBox = TransactionUIUtils.createSectionHeader(
            "💳 Transaction Management",
            "Create, manage, and mine transactions on TINC blockchain"
        );
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button backButton = TransactionUIUtils.createBorderButton("← Back to Dashboard", "#40e0d0", 
            () -> {
                LOGGER.info("Returning to dashboard from transaction management");
                // This will be handled by the DashboardController
            }
        );
        
        headerBox.getChildren().addAll(titleBox, spacer, backButton);
        
        VBox headerSection = new VBox();
        headerSection.getChildren().add(headerBox);
        return headerSection;
    }
    
    /**
     * Creates transaction statistics section
     */
    private HBox createTransactionStatsSection() {
        HBox statsBox = new HBox(20);
        statsBox.setStyle("-fx-padding: 20 0;");
        
        // Get current statistics
        TransactionStatistics stats = transactionManager.getStatistics();
        
        // Create stats cards
        VBox pendingCard = TransactionUIUtils.createTransactionStatsCard(
            "Pending Transactions", 
            String.valueOf(stats.getPendingTransactions()), 
            "#00ff88", 
            200
        );
        
        VBox totalValueCard = TransactionUIUtils.createTransactionStatsCard(
            "Total Value", 
            TransactionUIUtils.formatTransactionAmount(stats.getTotalValue()), 
            "#40e0d0", 
            200
        );
        
        VBox totalFeesCard = TransactionUIUtils.createTransactionStatsCard(
            "Total Fees", 
            TransactionUIUtils.formatTransactionAmount(stats.getTotalFees()), 
            "#8a2be2", 
            200
        );
        
        VBox miningStatusCard = TransactionUIUtils.createTransactionStatsCard(
            "Auto Mining", 
            transactionManager.isAutoMining() ? "ON" : "OFF", 
            transactionManager.isAutoMining() ? "#00ff88" : "#ff6b6b", 
            200
        );
        
        statsBox.getChildren().addAll(pendingCard, totalValueCard, totalFeesCard, miningStatusCard);
        return statsBox;
    }
    
    /**
     * Creates transaction actions section
     */
    private HBox createTransactionActionsSection() {
        return TransactionUIUtils.createTransactionActionsSection(
            this::handleCreateTransaction,
            this::handleSendTransaction,
            this::handleMineTransactions,
            this::handleViewHistory
        );
    }
    
    /**
     * Creates pending transactions section
     */
    private VBox createPendingTransactionsSection() {
        List<Transaction> pendingTransactions = transactionManager.getPendingTransactions();
        return TransactionUIUtils.createPendingTransactionsSection(
            pendingTransactions, 
            this::handleViewTransactionDetails
        );
    }
    
    /**
     * Creates transaction form section
     */
    private VBox createTransactionFormSection() {
        return TransactionUIUtils.createTransactionForm(this::handleQuickTransactionCreation);
    }
    
    /**
     * Creates mining control panel
     */
    public VBox createMiningControlPanel() {
        VBox miningPanel = new VBox(15);
        miningPanel.setStyle("-fx-background-color: #22334e; -fx-background-radius: 12; -fx-padding: 25;");
        
        Label miningTitle = new Label("⛏️ Mining Controls");
        miningTitle.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        
        // Auto mining toggle
        HBox autoMiningBox = new HBox(10);
        autoMiningBox.setStyle("-fx-alignment: center-left;");
        
        CheckBox autoMiningCheck = new CheckBox("Auto Mining");
        autoMiningCheck.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
        autoMiningCheck.setSelected(transactionManager.isAutoMining());
        autoMiningCheck.setOnAction(e -> {
            transactionManager.setAutoMining(autoMiningCheck.isSelected());
            refreshView();
        });
        
        Label autoMiningInfo = new Label("Automatically mines transactions into blocks");
        autoMiningInfo.setStyle("-fx-text-fill: #ccc; -fx-font-size: 12px;");
        
        autoMiningBox.getChildren().addAll(autoMiningCheck, autoMiningInfo);
        
        // Mining difficulty slider
        HBox difficultyBox = new HBox(10);
        difficultyBox.setStyle("-fx-alignment: center-left;");
        
        Label difficultyLabel = new Label("Mining Difficulty:");
        difficultyLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
        
        Slider difficultySlider = new Slider(100, 10000, transactionManager.getMiningDifficulty());
        difficultySlider.setShowTickLabels(true);
        difficultySlider.setShowTickMarks(true);
        difficultySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            transactionManager.setMiningDifficulty(newVal.longValue());
        });
        
        Label difficultyValue = new Label(String.valueOf((int) transactionManager.getMiningDifficulty()));
        difficultyValue.setStyle("-fx-text-fill: #40e0d0; -fx-font-size: 14px; -fx-font-weight: bold;");
        
        difficultySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            difficultyValue.setText(String.valueOf(newVal.intValue()));
        });
        
        difficultyBox.getChildren().addAll(difficultyLabel, difficultySlider, difficultyValue);
        
        miningPanel.getChildren().addAll(miningTitle, autoMiningBox, difficultyBox);
        return miningPanel;
    }
    
    /**
     * Refreshes the transaction management view
     */
    public void refreshView() {
        LOGGER.info("Refreshing transaction management view");
        
        Platform.runLater(() -> {
            try {
                // Update statistics section
                if (statsSection != null) {
                    HBox newStatsSection = createTransactionStatsSection();
                    int statsIndex = mainContainer.getChildren().indexOf(statsSection);
                    if (statsIndex >= 0) {
                        mainContainer.getChildren().set(statsIndex, newStatsSection);
                        statsSection = newStatsSection;
                    }
                }
                
                // Update pending transactions section
                if (pendingTransactionsSection != null) {
                    VBox newTransactionsSection = createPendingTransactionsSection();
                    int transactionsIndex = mainContainer.getChildren().indexOf(pendingTransactionsSection);
                    if (transactionsIndex >= 0) {
                        mainContainer.getChildren().set(transactionsIndex, newTransactionsSection);
                        pendingTransactionsSection = newTransactionsSection;
                    }
                }
                
                LOGGER.info("Transaction management view refreshed successfully");
            } catch (Exception e) {
                LOGGER.severe("Error refreshing transaction view: " + e.getMessage());
            }
        });
    }
    
    // ACTION HANDLERS
    
    /**
     * Handles create transaction action
     */
    private void handleCreateTransaction() {
        LOGGER.info("Opening advanced transaction creation dialog");
        
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Create Transaction");
            alert.setHeaderText("Advanced Transaction Creation");
            alert.setContentText("Advanced transaction creation features:\n\n" +
                                 "- Smart contract interactions\n" +
                                 "- Batch transaction creation\n" +
                                 "- Advanced gas optimization\n" +
                                 "- Transaction scheduling\n\n" +
                                 "Use the Quick Transaction form below for basic transactions.");
            alert.showAndWait();
        });
    }
    
    /**
     * Handles quick transaction creation from form
     */
    private void handleQuickTransactionCreation(TransactionCreationParams params) {
        LOGGER.info("Quick creating transaction to: " + params.getRecipient());
        
        boolean success = transactionController.createTransaction(
            params.getRecipient(),
            params.getValue(),
            params.getData(),
            params.getGasLimit(),
            params.getGasPrice()
        );
        
        if (success) {
            transactionController.showSuccessAlert(
                "Transaction Created Successfully!",
                String.format("Transaction to: %s\nValue: %s TINC\nGas Limit: %d\nGas Price: %s",
                    params.getRecipient(),
                    TransactionUIUtils.formatTransactionAmount(params.getValue()),
                    params.getGasLimit(),
                    TransactionUIUtils.formatGasPrice(params.getGasPrice())
                )
            );
            
            // Refresh view and notify dashboard
            refreshView();
            if (dashboardUpdateCallback != null) {
                dashboardUpdateCallback.run();
            }
        }
    }
    
    /**
     * Handles send transaction action
     */
    private void handleSendTransaction() {
        LOGGER.info("Opening send transaction interface");
        
        Dialog<SendTransactionParams> dialog = createSendTransactionDialog();
        dialog.showAndWait().ifPresent(params -> {
            boolean success = transactionController.createTransaction(
                params.getToAddress(),
                params.getValue(),
                params.getData(),
                params.getGasLimit(),
                params.getGasPrice()
            );
            
            if (success) {
                transactionController.showSuccessAlert(
                    "Transaction Sent Successfully!",
                    String.format("Sent %s TINC to %s",
                        TransactionUIUtils.formatTransactionAmount(params.getValue()),
                        params.getToAddress()
                    )
                );
                
                refreshView();
                if (dashboardUpdateCallback != null) {
                    dashboardUpdateCallback.run();
                }
            }
        });
    }
    
    /**
     * Handles mine transactions action
     */
    private void handleMineTransactions() {
        LOGGER.info("Mining pending transactions");
        
        Platform.runLater(() -> {
            int pendingCount = transactionManager.getTransactionCount();
            if (pendingCount == 0) {
                transactionController.showErrorAlert("No pending transactions to mine");
                return;
            }
            
            // Show progress dialog
            Alert progressAlert = new Alert(Alert.AlertType.INFORMATION);
            progressAlert.setTitle("Mining Transactions");
            progressAlert.setHeaderText("Mining in Progress");
            progressAlert.setContentText("Mining " + pendingCount + " transactions into a block...");
            
            // Mine in background thread
            new Thread(() -> {
                try {
                    Block minedBlock = transactionManager.mineTransactionsToBlock();
                    
                    Platform.runLater(() -> {
                        progressAlert.close();
                        
                        if (minedBlock != null) {
                            transactionController.showSuccessAlert(
                                "Mining Successful!",
                                String.format("Successfully mined block: %s\nTransactions: %d\nBlock Number: %d",
                                    minedBlock.getHash(),
                                    minedBlock.getTransactions().size(),
                                    minedBlock.getNumber()
                                )
                            );
                            
                            refreshView();
                            if (dashboardUpdateCallback != null) {
                                dashboardUpdateCallback.run();
                            }
                        } else {
                            transactionController.showErrorAlert("Mining failed. Please try again.");
                        }
                    });
                    
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        progressAlert.close();
                        transactionController.showErrorAlert("Mining error: " + e.getMessage());
                    });
                }
            }).start();
            
            progressAlert.show();
        });
    }
    
    /**
     * Handles view transaction history action
     */
    private void handleViewHistory() {
        LOGGER.info("Opening transaction history view");
        
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Transaction History");
            alert.setHeaderText("Transaction History Viewer");
            alert.setContentText("Transaction history features:\n\n" +
                                 "- View all confirmed transactions\n" +
                                 "- Filter by address, amount, date\n" +
                                 "- Export transaction data\n" +
                                 "- Transaction analytics\n\n" +
                                 "This feature will integrate with your blockchain's transaction history.");
            alert.showAndWait();
        });
    }
    
    /**
     * Handles view transaction details action
     */
    private void handleViewTransactionDetails(Transaction transaction) {
        LOGGER.info("Viewing details for transaction: " + transaction.getHash());
        
        StringBuilder details = new StringBuilder();
        details.append("Transaction Details:\n\n");
        details.append("Hash: ").append(transaction.getHash()).append("\n");
        details.append("From: ").append(transaction.getSender()).append("\n");
        details.append("To: ").append(transaction.getRecipient()).append("\n");
        details.append("Value: ").append(TransactionUIUtils.formatTransactionAmount(transaction.getValue() != null ? transaction.getValue() : 0)).append(" TINC\n");
        details.append("Gas Limit: ").append(transaction.getGasLimit()).append("\n");
        details.append("Gas Price: ").append(TransactionUIUtils.formatGasPrice(transaction.getGasPrice())).append("\n");
        details.append("Status: ").append(transaction.getStatus()).append("\n");
        details.append("Nonce: ").append(transaction.getNonce()).append("\n");
        
        if (transaction.getData() != null && transaction.getData().length > 0) {
            details.append("Data: ").append(new String(transaction.getData())).append("\n");
        }
        
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Transaction Details");
            alert.setHeaderText("Transaction: " + (transaction.getHash().length() > 16 ? 
                transaction.getHash().substring(0, 16) + "..." : transaction.getHash()));
            alert.setContentText(details.toString());
            alert.showAndWait();
        });
    }
    
    /**
     * Creates send transaction dialog
     */
    private Dialog<SendTransactionParams> createSendTransactionDialog() {
        Dialog<SendTransactionParams> dialog = new Dialog<>();
        dialog.setTitle("Send Transaction");
        dialog.setHeaderText("Send TINC to another address");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField toField = new TextField();
        toField.setPromptText("Recipient Address");
        TextField valueField = new TextField();
        valueField.setPromptText("Amount (TINC)");
        TextField gasLimitField = new TextField("21000");
        TextField gasPriceField = new TextField("10");
        TextField dataField = new TextField();
        dataField.setPromptText("Optional data");
        
        grid.add(new Label("To:"), 0, 0);
        grid.add(toField, 1, 0);
        grid.add(new Label("Value:"), 0, 1);
        grid.add(valueField, 1, 1);
        grid.add(new Label("Gas Limit:"), 0, 2);
        grid.add(gasLimitField, 1, 2);
        grid.add(new Label("Gas Price (Gwei):"), 0, 3);
        grid.add(gasPriceField, 1, 3);
        grid.add(new Label("Data:"), 0, 4);
        grid.add(dataField, 1, 4);
        
        dialog.getDialogPane().setContent(grid);
        
        ButtonType sendButtonType = new ButtonType("Send", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(sendButtonType, ButtonType.CANCEL);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == sendButtonType) {
                try {
                    String to = toField.getText().trim();
                    String valueText = valueField.getText().trim();
                    String gasLimitText = gasLimitField.getText().trim();
                    String gasPriceText = gasPriceField.getText().trim();
                    String data = dataField.getText().trim();
                    
                    if (!to.isEmpty() && !valueText.isEmpty()) {
                        long value = (long) (Double.parseDouble(valueText) * 1e18); // Convert to wei
                        int gasLimit = Integer.parseInt(gasLimitText);
                        java.math.BigInteger gasPrice = java.math.BigInteger.valueOf((long) (Double.parseDouble(gasPriceText) * 1e9));
                        
                        return new SendTransactionParams(to, value, data, gasLimit, gasPrice);
                    }
                } catch (NumberFormatException e) {
                    // Handle error
                }
            }
            return null;
        });
        
        return dialog;
    }
    
    /**
     * Send transaction parameters data class
     */
    public static class SendTransactionParams {
        private final String toAddress;
        private final long value;
        private final String data;
        private final int gasLimit;
        private final java.math.BigInteger gasPrice;
        
        public SendTransactionParams(String toAddress, long value, String data, int gasLimit, java.math.BigInteger gasPrice) {
            this.toAddress = toAddress;
            this.value = value;
            this.data = data;
            this.gasLimit = gasLimit;
            this.gasPrice = gasPrice;
        }
        
        public String getToAddress() { return toAddress; }
        public long getValue() { return value; }
        public String getData() { return data; }
        public int getGasLimit() { return gasLimit; }
        public java.math.BigInteger getGasPrice() { return gasPrice; }
    }
}