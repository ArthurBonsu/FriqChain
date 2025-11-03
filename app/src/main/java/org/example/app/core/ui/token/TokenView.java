package org.example.app.core.ui.token;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.util.StringConverter; // FIXED: Added missing import

import org.example.app.core.token.TokenStandard;
import org.example.app.core.token.ERC20Standard;
import org.example.app.core.token.ERC20Token;
import org.example.app.core.ui.token.TokenUIUtils.TokenCreationParams;
import org.example.app.core.ui.token.TokenManager.TokenStatistics;
import org.example.app.core.ui.token.TokenManager.TokenBalance;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * TokenView - Main token view management component
 * Provides comprehensive token UI views and interfaces
 * Based on the dashboard token management patterns
 */

public class TokenView {
    private static final Logger LOGGER = Logger.getLogger(TokenView.class.getName());
    
    private final TokenController tokenController;
    private final TokenManager tokenManager;
    private Runnable navigationCallback;

    // UI Components
    private VBox mainContainer;
    private HBox statsSection;
    private VBox tokenRegistrySection;
    private VBox tokenCreationSection;
    
    // Callback for dashboard integration
    private Runnable dashboardUpdateCallback;
    
    public TokenView(TokenController tokenController, TokenManager tokenManager) {
        this.tokenController = tokenController;
        this.tokenManager = tokenManager;
        
        LOGGER.info("TokenView initialized");
    }
    
    // Add this class to TokenView.java
public static class MintParams {
    private final String tokenAddress;
    private final String toAddress;
    private final long amount;
    
    public MintParams(String tokenAddress, String toAddress, long amount) {
        this.tokenAddress = tokenAddress;
        this.toAddress = toAddress;
        this.amount = amount;
    }
    
    public String getTokenAddress() { return tokenAddress; }
    public String getToAddress() { return toAddress; }
    public long getAmount() { return amount; }
}
    /**
     * Sets callback for dashboard updates
     */
    public void setDashboardUpdateCallback(Runnable callback) {
        this.dashboardUpdateCallback = callback;
    }
    
    /**
     * Creates the main token management view
     * Based on the token interface from DashboardController
     */
    public VBox createTokenManagementView() {
        LOGGER.info("Creating token management view");
        
        mainContainer = new VBox(25);
        mainContainer.setStyle("-fx-background-color: #16213e; -fx-padding: 30;");
        
        // Header section
        VBox headerSection = createHeaderSection();
        
        // Token statistics section
        statsSection = createTokenStatsSection();
        
        // Token actions section
        HBox actionsSection = createTokenActionsSection();
        
        // Token registry section
        tokenRegistrySection = createTokenRegistrySection();
        
        // Token creation section
        tokenCreationSection = createTokenCreationSection();
        
        mainContainer.getChildren().addAll(
            headerSection,
            statsSection,
            actionsSection,
            tokenRegistrySection,
            tokenCreationSection
        );
        
        return mainContainer;
    }
    
    /**
     * Creates the header section with title and back button
     */
    private VBox createHeaderSection() {
        HBox headerBox = new HBox(20);
        headerBox.setStyle("-fx-padding: 0 0 20 0; -fx-alignment: center-left;");
        
        VBox titleBox = TokenUIUtils.createSectionHeader(
            "🪙 Token Management",
            "Create, manage, and transfer ERC20 tokens on TINC"
        );
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Update the back button
         Button backButton = TokenUIUtils.createBorderButton("← Back to Dashboard", "#40e0d0", 
    () -> {
        LOGGER.info("Returning to dashboard from token management");
        if (navigationCallback != null) {
            navigationCallback.run();
        }
    }
        );
        headerBox.getChildren().addAll(titleBox, spacer, backButton);
        
        VBox headerSection = new VBox();
        headerSection.getChildren().add(headerBox);
        return headerSection;
    }
    
    // Add this method
public void setNavigationCallback(Runnable callback) {
    this.navigationCallback = callback;
}

    /**
     * Creates token statistics section
     */
    private HBox createTokenStatsSection() {
        HBox statsBox = new HBox(20);
        statsBox.setStyle("-fx-padding: 20 0;");
        
        // Get current statistics
        TokenStatistics stats = tokenManager.getTokenStatistics();
        
        // Create stats cards
        VBox totalTokensCard = TokenUIUtils.createTokenStatsCard(
            "Total Tokens", 
            String.valueOf(stats.getTotalTokens()), 
            "#00ff88", 
            250
        );
        
        VBox mintableTokensCard = TokenUIUtils.createTokenStatsCard(
            "Mintable Tokens", 
            String.valueOf(stats.getMintableTokens()), 
            "#40e0d0", 
            250
        );
        
        VBox totalSupplyCard = TokenUIUtils.createTokenStatsCard(
            "Total Supply", 
            TokenUIUtils.formatTokenAmount(stats.getTotalSupply()), 
            "#8a2be2", 
            250
        );
        
        statsBox.getChildren().addAll(totalTokensCard, mintableTokensCard, totalSupplyCard);
        return statsBox;
    }
    
    /**
     * Creates token actions section
     */
    private HBox createTokenActionsSection() {
        return TokenUIUtils.createTokenActionsSection(
            this::handleCreateToken,
            this::handleTransferTokens,
            this::handleMintTokens,
            this::handleRefreshRegistry
        );
    }
    
    /**
     * Creates token registry section
     */
    private VBox createTokenRegistrySection() {
        Map<String, TokenStandard> allTokens = tokenManager.getAllRegisteredTokens();
        return TokenUIUtils.createTokenRegistrySection(allTokens, this::handleViewTokenDetails);
    }
    
    /**
     * Creates token creation form section
     */
    private VBox createTokenCreationSection() {
        return TokenUIUtils.createTokenCreationForm(this::handleQuickTokenCreation);
    }
    
    /**
     * Creates token balance view for a specific address
     */
    public VBox createTokenBalanceView(String address) {
        LOGGER.info("Creating token balance view for address: " + address);
        
        VBox balanceView = new VBox(20);
        balanceView.setStyle("-fx-background-color: #16213e; -fx-padding: 30;");
        
        // Header
        VBox headerSection = TokenUIUtils.createSectionHeader(
            "Token Balances",
            "Token holdings for address: " + address
        );
        
        // Get token balances
        List<TokenBalance> balances = tokenManager.getTokenBalancesForAddress(address);
        
        if (balances.isEmpty()) {
            Label noBalancesLabel = new Label("No token balances found for this address.");
            noBalancesLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 16px; -fx-padding: 20;");
            balanceView.getChildren().addAll(headerSection, noBalancesLabel);
        } else {
            // Create balance table
            TableView<TokenBalance> balanceTable = TokenUIUtils.createTokenBalanceTable(balances);
            balanceView.getChildren().addAll(headerSection, balanceTable);
        }
        
        return balanceView;
    }

    
    
    /**
     * Creates token transfer dialog - SIMPLIFIED VERSION WITHOUT StringConverter
     */
    public Dialog<TokenTransferParams> createTokenTransferDialog() {
        Dialog<TokenTransferParams> dialog = new Dialog<>();
        dialog.setTitle("Transfer Tokens");
        dialog.setHeaderText("Transfer ERC20 tokens between addresses");
        
        // Create form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        // Token selection - SIMPLIFIED: Use TextField instead of ComboBox
        TextField tokenField = new TextField();
        tokenField.setPromptText("Token contract address");
        
        TextField fromField = new TextField();
        fromField.setPromptText("From address");
        
        TextField toField = new TextField();
        toField.setPromptText("To address");
        
        TextField amountField = new TextField();
        amountField.setPromptText("Amount to transfer");
        
        grid.add(new Label("Token Address:"), 0, 0);
        grid.add(tokenField, 1, 0);
        grid.add(new Label("From:"), 0, 1);
        grid.add(fromField, 1, 1);
        grid.add(new Label("To:"), 0, 2);
        grid.add(toField, 1, 2);
        grid.add(new Label("Amount:"), 0, 3);
        grid.add(amountField, 1, 3);
        
        dialog.getDialogPane().setContent(grid);
        
        ButtonType transferButtonType = new ButtonType("Transfer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(transferButtonType, ButtonType.CANCEL);
        
        // Result converter
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == transferButtonType) {
                try {
                    String tokenAddress = tokenField.getText().trim();
                    String from = fromField.getText().trim();
                    String to = toField.getText().trim();
                    long amount = Long.parseLong(amountField.getText().trim());
                    
                    if (!tokenAddress.isEmpty() && !from.isEmpty() && !to.isEmpty() && amount > 0) {
                        return new TokenTransferParams(tokenAddress, from, to, amount);
                    }
                } catch (NumberFormatException e) {
                    LOGGER.warning("Invalid amount in transfer dialog: " + e.getMessage());
                }
            }
            return null;
        });
        
        return dialog;
    }
    
    /**
     * Refreshes the token management view
     */
    public void refreshView() {
        LOGGER.info("Refreshing token management view");
        
        Platform.runLater(() -> {
            try {
                // Update statistics section
                if (statsSection != null) {
                    HBox newStatsSection = createTokenStatsSection();
                    int statsIndex = mainContainer.getChildren().indexOf(statsSection);
                    if (statsIndex >= 0) {
                        mainContainer.getChildren().set(statsIndex, newStatsSection);
                        statsSection = newStatsSection;
                    }
                }
                
                // Update token registry section
                if (tokenRegistrySection != null) {
                    VBox newRegistrySection = createTokenRegistrySection();
                    int registryIndex = mainContainer.getChildren().indexOf(tokenRegistrySection);
                    if (registryIndex >= 0) {
                        mainContainer.getChildren().set(registryIndex, newRegistrySection);
                        tokenRegistrySection = newRegistrySection;
                    }
                }
                
                LOGGER.info("Token management view refreshed successfully");
            } catch (Exception e) {
                LOGGER.severe("Error refreshing token view: " + e.getMessage());
            }
        });
    }
    
    // ACTION HANDLERS
    
    /**
     * Handles create token action
     */
    // In TokenView.java - Replace handleCreateToken() method
private void handleCreateToken() {
    LOGGER.info("Opening token creation form");
    
    // Create a proper token creation dialog
    Dialog<TokenCreationParams> dialog = createTokenCreationDialog();
    dialog.showAndWait().ifPresent(params -> {
        boolean success = tokenController.createToken(
            params.getName(),
            params.getSymbol(),
            params.getDecimals(),
            params.getInitialSupply(),
            params.isMintable()
        );
        
        if (success) {
            tokenController.showSuccessAlert(
                "Token Created Successfully!",
                String.format("Token: %s (%s)\nSupply: %s\nDecimals: %d\nMintable: %s",
                    params.getName(),
                    params.getSymbol(),
                    TokenUIUtils.formatTokenAmount(params.getInitialSupply()),
                    params.getDecimals(),
                    params.isMintable() ? "Yes" : "No"
                )
            );
            
            refreshView();
            if (dashboardUpdateCallback != null) {
                dashboardUpdateCallback.run();
            }
        }
    });
}

// Add this method to TokenView.java
private Dialog<TokenCreationParams> createTokenCreationDialog() {
    Dialog<TokenCreationParams> dialog = new Dialog<>();
    dialog.setTitle("Create New Token");
    dialog.setHeaderText("Create a new ERC20 token");
    
    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.setPadding(new Insets(20, 150, 10, 10));
    
    TextField nameField = new TextField();
    nameField.setPromptText("Token Name");
    TextField symbolField = new TextField();
    symbolField.setPromptText("Token Symbol");
    TextField supplyField = new TextField();
    supplyField.setPromptText("Initial Supply");
    TextField decimalsField = new TextField("18");
    CheckBox mintableCheck = new CheckBox("Mintable");
    
    grid.add(new Label("Name:"), 0, 0);
    grid.add(nameField, 1, 0);
    grid.add(new Label("Symbol:"), 0, 1);
    grid.add(symbolField, 1, 1);
    grid.add(new Label("Supply:"), 0, 2);
    grid.add(supplyField, 1, 2);
    grid.add(new Label("Decimals:"), 0, 3);
    grid.add(decimalsField, 1, 3);
    grid.add(mintableCheck, 1, 4);
    
    dialog.getDialogPane().setContent(grid);
    
    ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);
    
    dialog.setResultConverter(dialogButton -> {
        if (dialogButton == createButtonType) {
            try {
                String name = nameField.getText().trim();
                String symbol = symbolField.getText().trim();
                long supply = supplyField.getText().isEmpty() ? 0 : Long.parseLong(supplyField.getText());
                int decimals = Integer.parseInt(decimalsField.getText());
                boolean mintable = mintableCheck.isSelected();
                
                if (!name.isEmpty() && !symbol.isEmpty()) {
                    return new TokenCreationParams(name, symbol, decimals, supply, mintable);
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
     * Handles quick token creation from form
     */
    private void handleQuickTokenCreation(TokenCreationParams params) {
        LOGGER.info("Quick creating token: " + params.getName());
        
        boolean success = tokenController.createToken(
            params.getName(),
            params.getSymbol(),
            params.getDecimals(),
            params.getInitialSupply(),
            params.isMintable()
        );
        
        if (success) {
            tokenController.showSuccessAlert(
                "Token Created Successfully!",
                String.format("Token: %s (%s)\nSupply: %s\nDecimals: %d\nMintable: %s",
                    params.getName(),
                    params.getSymbol(),
                    TokenUIUtils.formatTokenAmount(params.getInitialSupply()),
                    params.getDecimals(),
                    params.isMintable() ? "Yes" : "No"
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
     * Handles token transfer action
     */
    private void handleTransferTokens() {
        LOGGER.info("Opening token transfer dialog");
        
        Dialog<TokenTransferParams> dialog = createTokenTransferDialog();
        dialog.showAndWait().ifPresent(params -> {
            boolean success = tokenController.transferTokens(
                params.getTokenAddress(),
                params.getFromAddress(),
                params.getToAddress(),
                params.getAmount()
            );
            
            if (success) {
                tokenController.showSuccessAlert(
                    "Transfer Successful!",
                    String.format("Transferred %s tokens successfully", 
                        TokenUIUtils.formatTokenAmount(params.getAmount()))
                );
                refreshView();
            }
        });
    }
    
    /**
     * Handles mint tokens action
     */
    private void handleMintTokens() {
    LOGGER.info("Opening token minting interface");
    
    // Get available mintable tokens
    Map<String, TokenStandard> allTokens = tokenManager.getAllRegisteredTokens();
    List<TokenStandard> mintableTokens = allTokens.values().stream()
        .filter(TokenStandard::isMintable)
        .collect(Collectors.toList());
    
    if (mintableTokens.isEmpty()) {
        tokenController.showErrorAlert("No mintable tokens found. Create a mintable token first.");
        return;
    }
    
    // Create mint dialog
    Dialog<MintParams> dialog = createMintTokenDialog(mintableTokens);
    dialog.showAndWait().ifPresent(params -> {
        boolean success = tokenController.mintTokens(
            params.getTokenAddress(),
            params.getToAddress(),
            params.getAmount()
        );
        
        if (success) {
            tokenController.showSuccessAlert(
                "Tokens Minted Successfully!",
                String.format("Minted %s tokens to %s",
                    TokenUIUtils.formatTokenAmount(params.getAmount()),
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

// Add this method and MintParams class to TokenView.java
private Dialog<MintParams> createMintTokenDialog(List<TokenStandard> mintableTokens) {
    Dialog<MintParams> dialog = new Dialog<>();
    dialog.setTitle("Mint Tokens");
    dialog.setHeaderText("Mint new tokens for a mintable token");
    
    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.setPadding(new Insets(20, 150, 10, 10));
    
    ComboBox<TokenStandard> tokenCombo = new ComboBox<>();
    tokenCombo.getItems().addAll(mintableTokens);
    tokenCombo.setConverter(new StringConverter<TokenStandard>() {
        @Override
        public String toString(TokenStandard token) {
            return token != null ? token.getName() + " (" + token.getSymbol() + ")" : "";
        }
        
        @Override
        public TokenStandard fromString(String string) {
            return null;
        }
    });
    
    TextField toField = new TextField();
    toField.setPromptText("Recipient Address");
    TextField amountField = new TextField();
    amountField.setPromptText("Amount to Mint");
    
    grid.add(new Label("Token:"), 0, 0);
    grid.add(tokenCombo, 1, 0);
    grid.add(new Label("To:"), 0, 1);
    grid.add(toField, 1, 1);
    grid.add(new Label("Amount:"), 0, 2);
    grid.add(amountField, 1, 2);
    
    dialog.getDialogPane().setContent(grid);
    
    ButtonType mintButtonType = new ButtonType("Mint", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(mintButtonType, ButtonType.CANCEL);
    
    dialog.setResultConverter(dialogButton -> {
        if (dialogButton == mintButtonType) {
            try {
                TokenStandard token = tokenCombo.getValue();
                String to = toField.getText().trim();
                long amount = Long.parseLong(amountField.getText());
                
                if (token != null && !to.isEmpty() && amount > 0) {
                    return new MintParams(token.getContractAddress(), to, amount);
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
     * Handles refresh registry action
     */
    private void handleRefreshRegistry() {
        LOGGER.info("Refreshing token registry");
        refreshView();
        
        if (dashboardUpdateCallback != null) {
            dashboardUpdateCallback.run();
        }
        
        tokenController.showSuccessAlert(
            "Registry Refreshed",
            "Token registry has been refreshed with latest data"
        );
    }
    
    /**
     * Handles view token details action
     */
    private void handleViewTokenDetails(TokenStandard token) {
        LOGGER.info("Viewing details for token: " + token.getName());
        
        StringBuilder details = new StringBuilder();
        details.append("Token Details:\n\n");
        details.append("Name: ").append(token.getName()).append("\n");
        details.append("Symbol: ").append(token.getSymbol()).append("\n");
        details.append("Contract Address: ").append(token.getContractAddress()).append("\n");
        details.append("Total Supply: ").append(TokenUIUtils.formatTokenAmount(token.getTotalSupply())).append("\n");
        details.append("Mintable: ").append(token.isMintable() ? "Yes" : "No").append("\n");
        
        if (token instanceof ERC20Token) {
            ERC20Token erc20 = (ERC20Token) token;
            details.append("Decimals: ").append(erc20.getDecimals()).append("\n");
            details.append("Creator: ").append(erc20.getCreator()).append("\n");
        }
        
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Token Details");
            alert.setHeaderText(token.getName() + " (" + token.getSymbol() + ")");
            alert.setContentText(details.toString());
            alert.showAndWait();
        });
    }
    
    /**
     * Token transfer parameters data class
     */
    public static class TokenTransferParams {
        private final String tokenAddress;
        private final String fromAddress;
        private final String toAddress;
        private final long amount;
        
        public TokenTransferParams(String tokenAddress, String fromAddress, String toAddress, long amount) {
            this.tokenAddress = tokenAddress;
            this.fromAddress = fromAddress;
            this.toAddress = toAddress;
            this.amount = amount;
        }
        
        public String getTokenAddress() { return tokenAddress; }
        public String getFromAddress() { return fromAddress; }
        public String getToAddress() { return toAddress; }
        public long getAmount() { return amount; }
    }
}