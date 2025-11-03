package org.example.app.core.ui.api;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.logging.Logger;

/**
 * RPCView - UI component for displaying RPC method calls and responses
 * Follows the pattern established in TransactionView.java
 * FIXED: Added missing Region import from javafx.scene.layout
 */
public class RPCView {
    private static final Logger LOGGER = Logger.getLogger(RPCView.class.getName());
    
    // Main container
    private VBox mainContainer;
    
    // RPC Method Selection
    private ComboBox<String> methodComboBox;
    private Label methodDescriptionLabel;
    
    // Request Parameters Section
    private GridPane parametersGridPane;
    private TextField[] parameterFields;
    private Label[] parameterLabels;
    
    // Response Display Section
    private TextArea responseTextArea;
    private Label responseStatusLabel;
    private ProgressIndicator loadingIndicator;
    
    // Action Buttons
    private Button executeButton;
    private Button clearButton;
    private Button copyResponseButton;
    
    // History Section
    private ListView<String> requestHistoryListView;
    private ObservableList<String> requestHistory;
    
    // Tabs for organization
    private TabPane tabPane;
    private Tab builderTab;
    private Tab historyTab;
    private Tab docsTab;
    
    public RPCView() {
        LOGGER.info("Initializing RPCView");
        initializeUI();
    }
    
    /**
     * Initializes all UI components
     */
    private void initializeUI() {
        mainContainer = new VBox();
        mainContainer.setSpacing(10);
        mainContainer.setPadding(new Insets(10));
        mainContainer.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1;");
        
        // Create tab pane
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // Create tabs
        builderTab = createBuilderTab();
        historyTab = createHistoryTab();
        docsTab = createDocsTab();
        
        tabPane.getTabs().addAll(builderTab, historyTab, docsTab);
        
        mainContainer.getChildren().add(tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        
        LOGGER.info("RPCView UI initialization complete");
    }
    
    /**
     * Creates the Builder Tab for RPC method execution
     */
    private Tab createBuilderTab() {
        VBox builderContent = new VBox();
        builderContent.setSpacing(10);
        builderContent.setPadding(new Insets(10));
        
        // Method Selection Section
        VBox methodSection = createMethodSelectionSection();
        
        // Parameters Section
        VBox parametersSection = createParametersSection();
        
        // Response Section
        VBox responseSection = createResponseSection();
        
        // Action Buttons
        HBox actionButtons = createActionButtonsSection();
        
        builderContent.getChildren().addAll(
            methodSection,
            new Separator(),
            parametersSection,
            new Separator(),
            responseSection,
            new Separator(),
            actionButtons
        );
        
        ScrollPane scrollPane = new ScrollPane(builderContent);
        scrollPane.setFitToWidth(true);
        
        Tab tab = new Tab("RPC Builder", scrollPane);
        tab.setStyle("-fx-text-base-color: #333333;");
        return tab;
    }
    
    /**
     * Creates the method selection section
     */
    private VBox createMethodSelectionSection() {
        VBox section = new VBox();
        section.setSpacing(5);
        
        Label sectionTitle = new Label("RPC Method");
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        
        methodComboBox = new ComboBox<>();
        methodComboBox.setPrefWidth(Double.MAX_VALUE);
        methodComboBox.setStyle("-fx-font-size: 12;");
        
        methodDescriptionLabel = new Label("Select a method to view its description");
        methodDescriptionLabel.setWrapText(true);
        methodDescriptionLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11;");
        
        section.getChildren().addAll(sectionTitle, methodComboBox, methodDescriptionLabel);
        return section;
    }
    
    /**
     * Creates the parameters input section
     */
    private VBox createParametersSection() {
        VBox section = new VBox();
        section.setSpacing(5);
        
        Label sectionTitle = new Label("Parameters");
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        
        parametersGridPane = new GridPane();
        parametersGridPane.setHgap(10);
        parametersGridPane.setVgap(10);
        parametersGridPane.setStyle("-fx-padding: 10; -fx-border-color: #f0f0f0; -fx-border-width: 1;");
        
        // Initialize parameter fields and labels
        parameterFields = new TextField[10];
        parameterLabels = new Label[10];
        
        for (int i = 0; i < 5; i++) {
            parameterLabels[i] = new Label("Parameter " + (i + 1) + ":");
            parameterLabels[i].setStyle("-fx-font-size: 12;");
            
            parameterFields[i] = new TextField();
            parameterFields[i].setPromptText("Enter value...");
            parameterFields[i].setPrefWidth(300);
            
            parametersGridPane.add(parameterLabels[i], 0, i);
            parametersGridPane.add(parameterFields[i], 1, i);
        }
        
        ScrollPane scrollPane = new ScrollPane(parametersGridPane);
        scrollPane.setFitToWidth(true);
        
        section.getChildren().addAll(sectionTitle, scrollPane);
        return section;
    }
    
    /**
     * Creates the response display section
     */
    private VBox createResponseSection() {
        VBox section = new VBox();
        section.setSpacing(5);
        
        Label sectionTitle = new Label("Response");
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        
        // Response status
        HBox statusBar = new HBox();
        statusBar.setSpacing(10);
        statusBar.setPadding(new Insets(5));
        statusBar.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");
        
        responseStatusLabel = new Label("Ready");
        responseStatusLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #333333;");
        
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(20, 20);
        loadingIndicator.setVisible(false);
        
        statusBar.getChildren().addAll(loadingIndicator, responseStatusLabel);
        
        // Response text area
        responseTextArea = new TextArea();
        responseTextArea.setWrapText(true);
        responseTextArea.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11; -fx-control-inner-background: #f5f5f5;");
        responseTextArea.setPrefRowCount(10);
        responseTextArea.setEditable(false);
        
        section.getChildren().addAll(sectionTitle, statusBar, responseTextArea);
        VBox.setVgrow(responseTextArea, Priority.ALWAYS);
        
        return section;
    }
    
    /**
     * Creates the action buttons section
     */
    private HBox createActionButtonsSection() {
        HBox buttonBox = new HBox();
        buttonBox.setSpacing(10);
        buttonBox.setStyle("-fx-padding: 10;");
        
        // Execute Button
        executeButton = new Button("Execute");
        executeButton.setStyle("-fx-font-size: 12; -fx-padding: 8 20 8 20; -fx-background-color: #4CAF50; -fx-text-fill: white;");
        executeButton.setPrefWidth(100);
        
        // Clear Button
        clearButton = new Button("Clear");
        clearButton.setStyle("-fx-font-size: 12; -fx-padding: 8 20 8 20; -fx-background-color: #f44336; -fx-text-fill: white;");
        clearButton.setPrefWidth(100);
        
        // Copy Response Button
        copyResponseButton = new Button("Copy Response");
        copyResponseButton.setStyle("-fx-font-size: 12; -fx-padding: 8 20 8 20; -fx-background-color: #2196F3; -fx-text-fill: white;");
        copyResponseButton.setPrefWidth(120);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        buttonBox.getChildren().addAll(executeButton, clearButton, spacer, copyResponseButton);
        return buttonBox;
    }
    
    /**
     * Creates the History Tab
     */
    private Tab createHistoryTab() {
        VBox historyContent = new VBox();
        historyContent.setSpacing(10);
        historyContent.setPadding(new Insets(10));
        
        Label historyTitle = new Label("Request History");
        historyTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        
        requestHistory = FXCollections.observableArrayList();
        requestHistoryListView = new ListView<>(requestHistory);
        requestHistoryListView.setPrefHeight(400);
        requestHistoryListView.setStyle("-fx-font-size: 11;");
        
        historyContent.getChildren().addAll(historyTitle, requestHistoryListView);
        VBox.setVgrow(requestHistoryListView, Priority.ALWAYS);
        
        Tab tab = new Tab("History", historyContent);
        tab.setStyle("-fx-text-base-color: #333333;");
        return tab;
    }
    
    /**
     * Creates the Documentation Tab
     */
    private Tab createDocsTab() {
        VBox docsContent = new VBox();
        docsContent.setSpacing(10);
        docsContent.setPadding(new Insets(10));
        
        Label docsTitle = new Label("RPC Method Documentation");
        docsTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        
        TextArea docsArea = new TextArea();
        docsArea.setWrapText(true);
        docsArea.setEditable(false);
        docsArea.setStyle("-fx-font-size: 11; -fx-control-inner-background: #f5f5f5;");
        docsArea.setText(getRPCMethodDocumentation());
        
        docsContent.getChildren().addAll(docsTitle, docsArea);
        VBox.setVgrow(docsArea, Priority.ALWAYS);
        
        Tab tab = new Tab("Documentation", docsContent);
        tab.setStyle("-fx-text-base-color: #333333;");
        return tab;
    }
    
    /**
     * Gets RPC method documentation
     */
    private String getRPCMethodDocumentation() {
        return """
            ETHEREUM-COMPATIBLE RPC METHODS
            ================================
            
            1. eth_getBalance
               Returns the balance of an account in wei.
               Parameters: address (string) - The account address
               Response: balance (hex string) - The balance in hexadecimal format
               
            2. eth_getBlock
               Returns block information.
               Parameters: hash (string) - Block hash, or number (int) - Block number
               Response: Block object with hash, miner, timestamp, transactions, etc.
               
            3. eth_getTransaction
               Returns transaction information.
               Parameters: hash (string) - Transaction hash
               Response: Transaction object with from, to, value, status, etc.
               
            4. eth_getTransactionCount
               Returns the number of transactions for an address.
               Parameters: address (string) - The account address
               Response: count (hex string) - Number of transactions
               
            5. eth_sendTransaction
               Submits a transaction to the network.
               Parameters: from, to, value, gasPrice, gasLimit, data
               Response: hash (string) - Transaction hash, status (string)
               
            6. eth_getTokenBalance
               Returns ERC-20 token balance for an account.
               Parameters: token (string) - Token address, address (string) - Account
               Response: balance, symbol, decimals
            """;
    }
    
    /**
     * Gets the main container
     */
    public VBox getMainContainer() {
        return mainContainer;
    }
    
    /**
     * Sets available RPC methods
     */
    public void setAvailableMethods(ObservableList<String> methods) {
        methodComboBox.setItems(methods);
    }
    
    /**
     * Gets selected RPC method
     */
    public String getSelectedMethod() {
        return methodComboBox.getValue();
    }
    
    /**
     * Gets parameter values
     */
    public String[] getParameterValues(int count) {
        String[] values = new String[count];
        for (int i = 0; i < count; i++) {
            values[i] = parameterFields[i].getText();
        }
        return values;
    }
    
    /**
     * Sets method description
     */
    public void setMethodDescription(String description) {
        methodDescriptionLabel.setText(description);
    }
    
    /**
     * Displays response
     */
    public void displayResponse(String response, boolean isSuccess) {
        Platform.runLater(() -> {
            responseTextArea.setText(response);
            if (isSuccess) {
                responseStatusLabel.setText("Success");
                responseStatusLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 12;");
            } else {
                responseStatusLabel.setText("Error");
                responseStatusLabel.setStyle("-fx-text-fill: #f44336; -fx-font-size: 12;");
            }
        });
    }
    
    /**
     * Shows loading indicator
     */
    public void showLoadingIndicator(boolean show) {
        Platform.runLater(() -> {
            loadingIndicator.setVisible(show);
            if (show) {
                responseStatusLabel.setText("Loading...");
            }
        });
    }
    
    /**
     * Adds entry to request history
     */
    public void addToHistory(String entry) {
        Platform.runLater(() -> {
            requestHistory.add(0, entry);
            if (requestHistory.size() > 100) {
                requestHistory.remove(requestHistory.size() - 1);
            }
        });
    }
    
    /**
     * Clears all parameters
     */
    public void clearParameters() {
        for (int i = 0; i < parameterFields.length; i++) {
            parameterFields[i].setText("");
        }
    }
    
    /**
     * Gets response text
     */
    public String getResponseText() {
        return responseTextArea.getText();
    }
    
    /**
     * Gets the execute button
     */
    public Button getExecuteButton() {
        return executeButton;
    }
    
    /**
     * Gets the clear button
     */
    public Button getClearButton() {
        return clearButton;
    }
    
    /**
     * Gets the copy response button
     */
    public Button getCopyResponseButton() {
        return copyResponseButton;
    }
    
    /**
     * Gets method combo box
     */
    public ComboBox<String> getMethodComboBox() {
        return methodComboBox;
    }
}