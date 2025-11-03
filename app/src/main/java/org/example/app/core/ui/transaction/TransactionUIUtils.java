package org.example.app.core.ui.transaction;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.cell.PropertyValueFactory;

import org.example.app.core.block.Transaction;
import org.example.app.core.ui.transaction.UITransactionManager.TransactionStatistics;

import java.math.BigInteger;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * TransactionUIUtils - Utility methods for creating transaction UI components
 * Based on the transaction management interface patterns
 */
public class TransactionUIUtils {
    private static final Logger LOGGER = Logger.getLogger(TransactionUIUtils.class.getName());
    
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,###.####");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    // UI Color Constants (matching your dashboard theme)
    private static final String PRIMARY_BG = "#16213e";
    private static final String SECONDARY_BG = "#22334e";
    private static final String ACCENT_GREEN = "#00ff88";
    private static final String ACCENT_CYAN = "#40e0d0";
    private static final String ACCENT_PURPLE = "#8a2be2";
    private static final String ACCENT_ORANGE = "#ff8c00";
    private static final String TEXT_WHITE = "white";
    private static final String TEXT_GRAY = "#ccc";
    
    /**
     * Creates a transaction statistics card
     */
    public static VBox createTransactionStatsCard(String title, String value, String color, double width) {
        VBox card = new VBox(10);
        card.setStyle(String.format(
            "-fx-background-color: %s; -fx-background-radius: 12; " +
            "-fx-padding: 25; -fx-pref-width: %.0f; -fx-alignment: center;",
            SECONDARY_BG, width
        ));
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 14px; -fx-alignment: center;", TEXT_GRAY));
        
        Label valueLabel = new Label(value);
        valueLabel.setStyle(String.format(
            "-fx-text-fill: %s; -fx-font-size: 28px; -fx-font-weight: bold; -fx-alignment: center;",
            color
        ));
        
        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }
    
    /**
     * Creates transaction action buttons section
     */
    public static HBox createTransactionActionsSection(
            Runnable createAction,
            Runnable sendAction,
            Runnable mineAction,
            Runnable historyAction) {
        
        HBox actionsBox = new HBox(15);
        actionsBox.setStyle("-fx-padding: 20 0;");
        
        Button createBtn = createActionButton("📝 Create Transaction", ACCENT_GREEN, "black", createAction);
        Button sendBtn = createActionButton("🚀 Send Transaction", ACCENT_CYAN, "black", sendAction);
        Button mineBtn = createActionButton("⛏️ Mine Transactions", ACCENT_PURPLE, TEXT_WHITE, mineAction);
        Button historyBtn = createBorderButton("📊 View History", ACCENT_ORANGE, historyAction);
        
        actionsBox.getChildren().addAll(createBtn, sendBtn, mineBtn, historyBtn);
        return actionsBox;
    }
    
    /**
     * Creates a filled action button
     */
    public static Button createActionButton(String text, String bgColor, String textColor, Runnable action) {
        Button button = new Button(text);
        button.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: %s; -fx-font-weight: bold; " +
            "-fx-padding: 12 24; -fx-background-radius: 8; -fx-font-size: 14px;",
            bgColor, textColor
        ));
        
        if (action != null) {
            button.setOnAction(e -> action.run());
        }
        
        return button;
    }
    
    /**
     * Creates a border-only button
     */
    public static Button createBorderButton(String text, String borderColor, Runnable action) {
        Button button = new Button(text);
        button.setStyle(String.format(
            "-fx-background-color: transparent; -fx-text-fill: %s; -fx-border-color: %s; " +
            "-fx-border-width: 2; -fx-font-weight: bold; -fx-padding: 12 24; " +
            "-fx-background-radius: 8; -fx-font-size: 14px;",
            borderColor, borderColor
        ));
        
        if (action != null) {
            button.setOnAction(e -> action.run());
        }
        
        return button;
    }
    
    /**
     * Creates pending transactions table
     */
    public static VBox createPendingTransactionsSection(List<Transaction> transactions, 
                                                       Consumer<Transaction> viewAction) {
        VBox transactionsSection = new VBox(15);
        transactionsSection.setStyle(String.format(
            "-fx-background-color: %s; -fx-background-radius: 12; -fx-padding: 25;",
            SECONDARY_BG
        ));
        
        Label transactionsTitle = new Label("⏳ Pending Transactions");
        transactionsTitle.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 18px; -fx-font-weight: bold;", TEXT_WHITE));
        
        if (transactions.isEmpty()) {
            Label noTransactionsLabel = new Label("No pending transactions. Create a transaction to get started!");
            noTransactionsLabel.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 14px; -fx-padding: 20;", TEXT_GRAY));
            transactionsSection.getChildren().addAll(transactionsTitle, noTransactionsLabel);
        } else {
            TableView<UITransaction> transactionTable = createTransactionTable(transactions, viewAction);
            transactionsSection.getChildren().addAll(transactionsTitle, transactionTable);
        }
        
        return transactionsSection;
    }
    
    /**
     * Creates transaction table
     */
    public static TableView<UITransaction> createTransactionTable(List<Transaction> transactions, 
                                                                 Consumer<Transaction> viewAction) {
        TableView<UITransaction> table = new TableView<>();
        table.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 12;", SECONDARY_BG));
        table.setPrefHeight(300);
        
        // Hash Column
        TableColumn<UITransaction, String> hashColumn = new TableColumn<>("Transaction Hash");
        hashColumn.setCellValueFactory(new PropertyValueFactory<>("hash"));
        hashColumn.setPrefWidth(150);
        hashColumn.setCellFactory(column -> new TableCell<UITransaction, String>() {
            @Override
            protected void updateItem(String hash, boolean empty) {
                super.updateItem(hash, empty);
                if (empty || hash == null) {
                    setText(null);
                } else {
                    setText(hash.length() > 16 ? hash.substring(0, 16) + "..." : hash);
                    setTooltip(new Tooltip(hash));
                }
            }
        });
        
        // From Column
        TableColumn<UITransaction, String> fromColumn = new TableColumn<>("From");
        fromColumn.setCellValueFactory(new PropertyValueFactory<>("from"));
        fromColumn.setPrefWidth(120);
        fromColumn.setCellFactory(column -> new TableCell<UITransaction, String>() {
            @Override
            protected void updateItem(String from, boolean empty) {
                super.updateItem(from, empty);
                if (empty || from == null) {
                    setText(null);
                } else {
                    setText(from.length() > 12 ? from.substring(0, 12) + "..." : from);
                    setTooltip(new Tooltip(from));
                }
            }
        });
        
        // To Column
        TableColumn<UITransaction, String> toColumn = new TableColumn<>("To");
        toColumn.setCellValueFactory(new PropertyValueFactory<>("to"));
        toColumn.setPrefWidth(120);
        toColumn.setCellFactory(column -> new TableCell<UITransaction, String>() {
            @Override
            protected void updateItem(String to, boolean empty) {
                super.updateItem(to, empty);
                if (empty || to == null) {
                    setText(null);
                } else {
                    setText(to.length() > 12 ? to.substring(0, 12) + "..." : to);
                    setTooltip(new Tooltip(to));
                }
            }
        });
        
        // Value Column
        TableColumn<UITransaction, String> valueColumn = new TableColumn<>("Value (TINC)");
        valueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
        valueColumn.setPrefWidth(100);
        
        // Gas Price Column
        TableColumn<UITransaction, String> gasPriceColumn = new TableColumn<>("Gas Price");
        gasPriceColumn.setCellValueFactory(new PropertyValueFactory<>("gasPrice"));
        gasPriceColumn.setPrefWidth(100);
        
        // Status Column
        TableColumn<UITransaction, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusColumn.setPrefWidth(80);
        
        // Action Column
        TableColumn<UITransaction, Void> actionColumn = new TableColumn<>("Action");
        actionColumn.setPrefWidth(80);
        actionColumn.setCellFactory(column -> new TableCell<UITransaction, Void>() {
            private final Button viewBtn = new Button("View");
            
            {
                viewBtn.setStyle(String.format(
                    "-fx-background-color: %s; -fx-text-fill: black; -fx-font-weight: bold; " +
                    "-fx-padding: 6 12; -fx-background-radius: 6; -fx-font-size: 12px;",
                    ACCENT_CYAN
                ));
                
                viewBtn.setOnAction(e -> {
                    UITransaction uiTx = getTableView().getItems().get(getIndex());
                    if (viewAction != null && uiTx.getOriginalTransaction() != null) {
                        viewAction.accept(uiTx.getOriginalTransaction());
                    }
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(viewBtn);
                }
            }
        });
        
        table.getColumns().addAll(hashColumn, fromColumn, toColumn, valueColumn, gasPriceColumn, statusColumn, actionColumn);
        
        // Convert transactions to UI transactions
        ObservableList<UITransaction> uiTransactions = FXCollections.observableArrayList();
        for (Transaction tx : transactions) {
            uiTransactions.add(new UITransaction(tx));
        }
        table.setItems(uiTransactions);
        
        return table;
    }
    
    /**
     * Creates transaction creation form
     */
    public static VBox createTransactionForm(Consumer<TransactionCreationParams> createAction) {
        VBox formSection = new VBox(15);
        formSection.setStyle(String.format(
            "-fx-background-color: %s; -fx-background-radius: 12; -fx-padding: 25;",
            SECONDARY_BG
        ));
        
        Label formTitle = new Label("💳 Create New Transaction");
        formTitle.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 18px; -fx-font-weight: bold;", TEXT_WHITE));
        
        // Form fields
        VBox formFields = new VBox(15);
        
        // Recipient and Value row
        HBox recipientValueRow = new HBox(15);
        
        VBox recipientBox = createFormField("Recipient Address:", "0x...");
        TextField recipientField = (TextField) recipientBox.getChildren().get(1);
        
        VBox valueBox = createFormField("Value (TINC):", "0.0");
        TextField valueField = (TextField) valueBox.getChildren().get(1);
        
        recipientValueRow.getChildren().addAll(recipientBox, valueBox);
        HBox.setHgrow(recipientBox, Priority.ALWAYS);
        HBox.setHgrow(valueBox, Priority.ALWAYS);
        
        // Gas Limit and Gas Price row
        HBox gasRow = new HBox(15);
        
        VBox gasLimitBox = createFormField("Gas Limit:", "21000");
        TextField gasLimitField = (TextField) gasLimitBox.getChildren().get(1);
        gasLimitField.setText("21000");
        
        VBox gasPriceBox = createFormField("Gas Price (Gwei):", "10");
        TextField gasPriceField = (TextField) gasPriceBox.getChildren().get(1);
        gasPriceField.setText("10");
        
        gasRow.getChildren().addAll(gasLimitBox, gasPriceBox);
        HBox.setHgrow(gasLimitBox, Priority.ALWAYS);
        HBox.setHgrow(gasPriceBox, Priority.ALWAYS);
        
        // Data field
        VBox dataBox = createFormField("Data (Optional):", "Enter transaction data...");
        TextField dataField = (TextField) dataBox.getChildren().get(1);
        
        // Create button
        Button createBtn = createActionButton("🚀 Create Transaction", ACCENT_GREEN, "black", () -> {
            if (createAction != null) {
                try {
                    String recipient = recipientField.getText().trim();
                    String valueText = valueField.getText().trim();
                    String gasLimitText = gasLimitField.getText().trim();
                    String gasPriceText = gasPriceField.getText().trim();
                    String data = dataField.getText().trim();
                    
                    long value = valueText.isEmpty() ? 0 : (long) (Double.parseDouble(valueText) * 1e18); // Convert to wei
                    int gasLimit = gasLimitText.isEmpty() ? 21000 : Integer.parseInt(gasLimitText);
                    BigInteger gasPrice = gasPriceText.isEmpty() ? 
                        BigInteger.valueOf(10_000_000_000L) : // 10 Gwei default
                        BigInteger.valueOf((long) (Double.parseDouble(gasPriceText) * 1e9)); // Convert Gwei to wei
                    
                    TransactionCreationParams params = new TransactionCreationParams(
                        recipient, value, data, gasLimit, gasPrice
                    );
                    createAction.accept(params);
                    
                    // Clear form after successful creation
                    recipientField.clear();
                    valueField.clear();
                    gasLimitField.setText("21000");
                    gasPriceField.setText("10");
                    dataField.clear();
                    
                } catch (NumberFormatException e) {
                    LOGGER.warning("Invalid number format in transaction form: " + e.getMessage());
                }
            }
        });
        
        formFields.getChildren().addAll(recipientValueRow, gasRow, dataBox, createBtn);
        formSection.getChildren().addAll(formTitle, formFields);
        
        return formSection;
    }
    
    /**
     * Creates a form field with label and text field
     */
    public static VBox createFormField(String labelText, String promptText) {
        VBox fieldBox = new VBox(5);
        
        Label label = new Label(labelText);
        label.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 12px;", TEXT_GRAY));
        
        TextField textField = new TextField();
        textField.setPromptText(promptText);
        textField.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: %s; " +
            "-fx-border-radius: 6; -fx-padding: 8;",
            PRIMARY_BG, TEXT_WHITE, ACCENT_CYAN
        ));
        
        fieldBox.getChildren().addAll(label, textField);
        return fieldBox;
    }
    
    /**
     * Creates a section header with title and subtitle
     */
    public static VBox createSectionHeader(String title, String subtitle) {
        VBox headerBox = new VBox(5);
        headerBox.setStyle("-fx-padding: 0 0 20 0; -fx-alignment: center-left;");
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 32px; -fx-font-weight: bold;", TEXT_WHITE));
        
        if (subtitle != null && !subtitle.isEmpty()) {
            Label subtitleLabel = new Label(subtitle);
            subtitleLabel.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 16px;", TEXT_GRAY));
            headerBox.getChildren().addAll(titleLabel, subtitleLabel);
        } else {
            headerBox.getChildren().add(titleLabel);
        }
        
        return headerBox;
    }
    
    /**
     * Formats transaction amount for display
     */
    public static String formatTransactionAmount(long amount) {
        return DECIMAL_FORMAT.format(amount / 1e18); // Convert wei to TINC
    }
    
    /**
     * Formats gas price for display
     */
    public static String formatGasPrice(BigInteger gasPrice) {
        if (gasPrice == null) return "0";
        return DECIMAL_FORMAT.format(gasPrice.longValue() / 1e9) + " Gwei"; // Convert wei to Gwei
    }
    
    /**
     * Transaction creation parameters data class
     */
    public static class TransactionCreationParams {
        private final String recipient;
        private final long value;
        private final String data;
        private final int gasLimit;
        private final BigInteger gasPrice;
        
        public TransactionCreationParams(String recipient, long value, String data, int gasLimit, BigInteger gasPrice) {
            this.recipient = recipient;
            this.value = value;
            this.data = data;
            this.gasLimit = gasLimit;
            this.gasPrice = gasPrice;
        }
        
        public String getRecipient() { return recipient; }
        public long getValue() { return value; }
        public String getData() { return data; }
        public int getGasLimit() { return gasLimit; }
        public BigInteger getGasPrice() { return gasPrice; }
    }
    
    /**
     * UI Transaction wrapper class for table display
     */
    public static class UITransaction {
        private final Transaction originalTransaction;
        private final String hash;
        private final String from;
        private final String to;
        private final String value;
        private final String gasPrice;
        private final String status;
        
        public UITransaction(Transaction transaction) {
            this.originalTransaction = transaction;
            this.hash = transaction.getHash() != null ? transaction.getHash() : "N/A";
            this.from = transaction.getSender() != null ? transaction.getSender() : "N/A";
            this.to = transaction.getRecipient() != null ? transaction.getRecipient() : "N/A";
            this.value = formatTransactionAmount(transaction.getValue() != null ? transaction.getValue() : 0L);
            this.gasPrice = formatGasPrice(transaction.getGasPrice());
            this.status = transaction.getStatus() != null ? transaction.getStatus().toString() : "PENDING";
        }
        
        public Transaction getOriginalTransaction() { return originalTransaction; }
        public String getHash() { return hash; }
        public String getFrom() { return from; }
        public String getTo() { return to; }
        public String getValue() { return value; }
        public String getGasPrice() { return gasPrice; }
        public String getStatus() { return status; }
    }
}