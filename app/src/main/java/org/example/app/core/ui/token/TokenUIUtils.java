package org.example.app.core.ui.token;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.text.Font;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.cell.PropertyValueFactory;

import org.example.app.core.token.TokenStandard;
import org.example.app.core.token.ERC20Standard;
import org.example.app.core.token.ERC20Token;
import org.example.app.core.ui.token.TokenManager.TokenBalance;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * TokenUIUtils - Utility methods for creating token UI components
 * Based on the token management interface patterns from DashboardController
 */
public class TokenUIUtils {
    private static final Logger LOGGER = Logger.getLogger(TokenUIUtils.class.getName());
    
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,###");
    
    // UI Color Constants (matching your dashboard theme)
    private static final String PRIMARY_BG = "#16213e";
    private static final String SECONDARY_BG = "#22334e";
    private static final String ACCENT_GREEN = "#00ff88";
    private static final String ACCENT_CYAN = "#40e0d0";
    private static final String ACCENT_PURPLE = "#8a2be2";
    private static final String TEXT_WHITE = "white";
    private static final String TEXT_GRAY = "#ccc";
    
    /**
     * Creates a token statistics card
     */
    public static VBox createTokenStatsCard(String title, String value, String color, double width) {
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
     * Creates token action buttons section
     */
    public static HBox createTokenActionsSection(
            Runnable createAction,
            Runnable transferAction, 
            Runnable mintAction,
            Runnable refreshAction) {
        
        HBox actionsBox = new HBox(15);
        actionsBox.setStyle("-fx-padding: 20 0;");
        
        Button createBtn = createActionButton("🆕 Create New Token", ACCENT_GREEN, "black", createAction);
        Button transferBtn = createActionButton("💸 Transfer Tokens", ACCENT_CYAN, "black", transferAction);
        Button mintBtn = createActionButton("⚡ Mint Tokens", ACCENT_PURPLE, TEXT_WHITE, mintAction);
        Button refreshBtn = createBorderButton("🔄 Refresh Registry", ACCENT_GREEN, refreshAction);
        
        actionsBox.getChildren().addAll(createBtn, transferBtn, mintBtn, refreshBtn);
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
     * Creates token registry table showing all registered tokens
     */
    public static VBox createTokenRegistrySection(Map<String, TokenStandard> tokens, Consumer<TokenStandard> viewAction) {
        VBox registrySection = new VBox(15);
        registrySection.setStyle(String.format(
            "-fx-background-color: %s; -fx-background-radius: 12; -fx-padding: 25;",
            SECONDARY_BG
        ));
        
        Label registryTitle = new Label("📋 Registered Tokens");
        registryTitle.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 18px; -fx-font-weight: bold;", TEXT_WHITE));
        
        if (tokens.isEmpty()) {
            Label noTokensLabel = new Label("No tokens registered yet. Create your first token!");
            noTokensLabel.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 14px; -fx-padding: 20;", TEXT_GRAY));
            registrySection.getChildren().addAll(registryTitle, noTokensLabel);
        } else {
            VBox tokenList = new VBox(10);
            
            for (TokenStandard token : tokens.values()) {
                HBox tokenItem = createTokenListItem(token, viewAction);
                tokenList.getChildren().add(tokenItem);
            }
            
            ScrollPane scrollPane = new ScrollPane(tokenList);
            scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
            scrollPane.setFitToWidth(true);
            scrollPane.setPrefHeight(200);
            
            registrySection.getChildren().addAll(registryTitle, scrollPane);
        }
        
        return registrySection;
    }
    
    /**
     * Creates a single token list item
     */
    public static HBox createTokenListItem(TokenStandard token, Consumer<TokenStandard> viewAction) {
        HBox item = new HBox(15);
        item.setStyle(String.format(
            "-fx-padding: 15; -fx-background-color: rgba(255,255,255,0.1); " +
            "-fx-background-radius: 8; -fx-alignment: center-left;"
        ));
        
        // Token info
        VBox tokenInfo = new VBox(5);
        
        Label nameLabel = new Label(token.getName() + " (" + token.getSymbol() + ")");
        nameLabel.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 16px; -fx-font-weight: bold;", ACCENT_GREEN));
        
        Label addressLabel = new Label("Contract: " + token.getContractAddress());
        addressLabel.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 12px;", TEXT_GRAY));
        
        Label supplyLabel = new Label("Total Supply: " + DECIMAL_FORMAT.format(token.getTotalSupply()));
        supplyLabel.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 12px;", ACCENT_CYAN));
        
        tokenInfo.getChildren().addAll(nameLabel, addressLabel, supplyLabel);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // View button
        Button viewBtn = createActionButton("View", ACCENT_CYAN, "black", 
            viewAction != null ? () -> viewAction.accept(token) : null);
        viewBtn.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: black; -fx-font-weight: bold; " +
            "-fx-padding: 8 16; -fx-background-radius: 6;",
            ACCENT_CYAN
        ));
        
        item.getChildren().addAll(tokenInfo, spacer, viewBtn);
        return item;
    }
    
    /**
     * Creates token creation form section
     */
    public static VBox createTokenCreationForm(Consumer<TokenCreationParams> createAction) {
        VBox creationSection = new VBox(15);
        creationSection.setStyle(String.format(
            "-fx-background-color: %s; -fx-background-radius: 12; -fx-padding: 25;",
            SECONDARY_BG
        ));
        
        Label creationTitle = new Label("🏭 Quick Token Creation");
        creationTitle.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 18px; -fx-font-weight: bold;", TEXT_WHITE));
        
        // Form fields
        VBox formFields = new VBox(10);
        
        // Name and symbol row
        HBox nameSymbolRow = new HBox(15);
        
        VBox nameBox = createFormField("Token Name:", "e.g., My Token");
        TextField nameField = (TextField) nameBox.getChildren().get(1);
        
        VBox symbolBox = createFormField("Symbol:", "e.g., MTK");
        TextField symbolField = (TextField) symbolBox.getChildren().get(1);
        
        nameSymbolRow.getChildren().addAll(nameBox, symbolBox);
        HBox.setHgrow(nameBox, Priority.ALWAYS);
        HBox.setHgrow(symbolBox, Priority.ALWAYS);
        
        // Supply and decimals row
        HBox supplyDecimalsRow = new HBox(15);
        
        VBox supplyBox = createFormField("Initial Supply:", "e.g., 1000000");
        TextField supplyField = (TextField) supplyBox.getChildren().get(1);
        
        VBox decimalsBox = createFormField("Decimals:", "18");
        TextField decimalsField = (TextField) decimalsBox.getChildren().get(1);
        decimalsField.setText("18");
        
        supplyDecimalsRow.getChildren().addAll(supplyBox, decimalsBox);
        HBox.setHgrow(supplyBox, Priority.ALWAYS);
        HBox.setHgrow(decimalsBox, Priority.ALWAYS);
        
        // Mintable checkbox
        CheckBox mintableCheck = new CheckBox("Make token mintable");
        mintableCheck.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 14px;", TEXT_WHITE));
        
        // Create button
        Button createBtn = createActionButton("🚀 Create Token", ACCENT_GREEN, "black", () -> {
            if (createAction != null) {
                try {
                    String name = nameField.getText().trim();
                    String symbol = symbolField.getText().trim();
                    long initialSupply = supplyField.getText().isEmpty() ? 0 : Long.parseLong(supplyField.getText());
                    int decimals = decimalsField.getText().isEmpty() ? 18 : Integer.parseInt(decimalsField.getText());
                    boolean mintable = mintableCheck.isSelected();
                    
                    TokenCreationParams params = new TokenCreationParams(name, symbol, decimals, initialSupply, mintable);
                    createAction.accept(params);
                    
                    // Clear form after successful creation
                    nameField.clear();
                    symbolField.clear();
                    supplyField.clear();
                    decimalsField.setText("18");
                    mintableCheck.setSelected(false);
                    
                } catch (NumberFormatException e) {
                    LOGGER.warning("Invalid number format in token creation form");
                }
            }
        });
        
        formFields.getChildren().addAll(nameSymbolRow, supplyDecimalsRow, mintableCheck, createBtn);
        creationSection.getChildren().addAll(creationTitle, formFields);
        
        return creationSection;
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
     * Creates token balance table
     */
    @SuppressWarnings("unchecked")
    public static TableView<TokenBalance> createTokenBalanceTable(List<TokenBalance> balances) {
        TableView<TokenBalance> table = new TableView<>();
        table.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 12;", SECONDARY_BG));
        
        // Columns
        TableColumn<TokenBalance, String> nameColumn = new TableColumn<>("Token");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameColumn.setPrefWidth(150);
        
        TableColumn<TokenBalance, String> symbolColumn = new TableColumn<>("Symbol");
        symbolColumn.setCellValueFactory(new PropertyValueFactory<>("symbol"));
        symbolColumn.setPrefWidth(80);
        
        TableColumn<TokenBalance, Long> balanceColumn = new TableColumn<>("Balance");
        balanceColumn.setCellValueFactory(new PropertyValueFactory<>("balance"));
        balanceColumn.setPrefWidth(120);
        balanceColumn.setCellFactory(column -> new TableCell<TokenBalance, Long>() {
            @Override
            protected void updateItem(Long balance, boolean empty) {
                super.updateItem(balance, empty);
                setText(empty || balance == null ? null : DECIMAL_FORMAT.format(balance));
            }
        });
        
        TableColumn<TokenBalance, String> addressColumn = new TableColumn<>("Contract Address");
        addressColumn.setCellValueFactory(new PropertyValueFactory<>("contractAddress"));
        addressColumn.setPrefWidth(300);
       
        table.getColumns().addAll(nameColumn, symbolColumn, balanceColumn, addressColumn);

        ObservableList<TokenBalance> observableBalances = FXCollections.observableArrayList(balances);
        table.setItems(observableBalances);
        
        return table;
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
     * Token creation parameters data class
     */
    public static class TokenCreationParams {
        private final String name;
        private final String symbol;
        private final int decimals;
        private final long initialSupply;
        private final boolean mintable;
        
        public TokenCreationParams(String name, String symbol, int decimals, long initialSupply, boolean mintable) {
            this.name = name;
            this.symbol = symbol;
            this.decimals = decimals;
            this.initialSupply = initialSupply;
            this.mintable = mintable;
        }
        
        public String getName() { return name; }
        public String getSymbol() { return symbol; }
        public int getDecimals() { return decimals; }
        public long getInitialSupply() { return initialSupply; }
        public boolean isMintable() { return mintable; }
    }
    
    /**
     * Formats token amount for display
     */
    public static String formatTokenAmount(long amount) {
        return DECIMAL_FORMAT.format(amount);
    }
}