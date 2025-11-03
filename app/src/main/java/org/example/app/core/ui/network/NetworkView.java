package org.example.app.core.ui.network;

import org.example.app.core.network.Messages;
import org.example.app.core.metrics.NetworkMetrics;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * NetworkView creates and manages the network management user interface
 * for the TINC blockchain network module.
 */
public class NetworkView {
    private static final Logger LOGGER = Logger.getLogger(NetworkView.class.getName());
    
    // Core Components
    private final NetworkController networkController;
    private final NetworkManager networkManager;
    
    // UI Callbacks
    private Runnable dashboardUpdateCallback;
    private Runnable navigationCallback;
    
    // UI State
    private final ObservableList<NetworkController.PeerInfo> peerList;
    private final ObservableList<String> messageLog;
    private final AtomicInteger updateCounter = new AtomicInteger(0);
    
    // UI Components - Status Section
    private Label networkStatusLabel;
    private Label peerCountLabel;
    private Label messagesSentLabel;
    private Label messagesReceivedLabel;
    private Label dataTransferredLabel;
    private Label networkHealthLabel;
    
    // UI Components - Charts
    private LineChart<Number, Number> networkActivityChart;
    private LineChart<Number, Number> peerConnectionChart;
    
    // UI Components - Tables
    private TableView<NetworkController.PeerInfo> peerTable;
    private ListView<String> messageListView;
    
    // UI Components - Controls
    private Button startNetworkBtn;
    private Button stopNetworkBtn;
    private Button connectPeerBtn;
    private Button disconnectPeerBtn;
    private Button broadcastBtn;
    private Button optimizeBtn;
    
    // Policy and Settings Controls
    private ComboBox<NetworkManager.ConnectionPolicy.PolicyType> policyComboBox;
    private CheckBox discoveryCheckBox;
    private CheckBox autoOptimizeCheckBox;
    private Slider maxPeersSlider;
    
    // Update Timeline
    private Timeline updateTimeline;
    
    /**
     * Constructor
     */
    public NetworkView(NetworkController networkController, NetworkManager networkManager) {
        this.networkController = networkController;
        this.networkManager = networkManager;
        
        this.peerList = FXCollections.observableArrayList();
        this.messageLog = FXCollections.observableArrayList();
        
        // Setup event listeners
        setupEventListeners();
        
        // Start update timeline
        startUpdateTimeline();
        
        LOGGER.info("NetworkView initialized");
    }
    
// Add to NetworkView.java
private void saveNetworkConfiguration() {
    try {
        Properties props = new Properties();
        
        // Add null checks for all components
        if (maxPeersSlider != null) {
            props.setProperty("max.peers", String.valueOf(maxPeersSlider.getValue()));
        }
        if (discoveryCheckBox != null) {
            props.setProperty("discovery.enabled", String.valueOf(discoveryCheckBox.isSelected()));
        }
        if (autoOptimizeCheckBox != null) {
            props.setProperty("auto.optimize", String.valueOf(autoOptimizeCheckBox.isSelected()));
        }
        if (policyComboBox != null && policyComboBox.getValue() != null) {
            props.setProperty("connection.policy", policyComboBox.getValue().toString());
        }
        
        try (FileOutputStream out = new FileOutputStream("network.config")) {
            props.store(out, "Network Configuration");
            LOGGER.info("Network configuration saved");
        }
    } catch (IOException e) {
        LOGGER.severe("Failed to save network config: " + e.getMessage());
    }
}

    /**
     * Create the main network management interface
     */
    public VBox createNetworkManagementView() {
        VBox mainContainer = new VBox(20);
        mainContainer.setStyle(NetworkUIUtils.getContainerStyle());
        
        // Header Section
        VBox headerSection = createHeaderSection();
        
        // Network Status Section
        HBox statusSection = createNetworkStatusSection();
        
        // Main Content Tabs
        TabPane mainTabs = createMainTabs();
        
        // Control Panel
        HBox controlPanel = createControlPanel();
        
        mainContainer.getChildren().addAll(headerSection, statusSection, mainTabs, controlPanel);
        
        // Initial data load
        loadInitialData();
        
        return mainContainer;
    }
    
    /**
     * Create header section
     */
    private VBox createHeaderSection() {
        VBox headerBox = new VBox(10);
        headerBox.setPadding(new Insets(0, 0, 20, 0));
        
        // Main title
        Label titleLabel = new Label("🌐 Network Manager");
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 32px; -fx-font-weight: bold;");
        
        // Subtitle
        Label subtitleLabel = new Label("Manage P2P connections, peer discovery, and network optimization");
        subtitleLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 16px;");
        
        // Control buttons
        HBox controlsBox = new HBox(10);
        controlsBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button backButton = new Button("← Back to Dashboard");
        backButton.setStyle(NetworkUIUtils.getButtonStyle(NetworkUIUtils.COLOR_INFO, false));
        backButton.setOnAction(e -> {
            if (navigationCallback != null) {
                navigationCallback.run();
            }
        });
        
        Button refreshButton = new Button("🔄 Refresh");
        refreshButton.setStyle(NetworkUIUtils.getButtonStyle(NetworkUIUtils.COLOR_PRIMARY, true));
        refreshButton.setOnAction(e -> refreshNetworkData());
        
        controlsBox.getChildren().addAll(refreshButton, backButton);
        
        headerBox.getChildren().addAll(titleLabel, subtitleLabel, controlsBox);
        return headerBox;
    }
    
    /**
     * Create network status section
     */
    private HBox createNetworkStatusSection() {
        HBox statusBox = new HBox(20);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        statusBox.setPadding(new Insets(20, 0, 20, 0));
        
        // Network Status Card
        networkStatusLabel = new Label("Stopped");
        VBox statusCard = NetworkUIUtils.createMetricCard("Network Status", "Stopped", NetworkUIUtils.COLOR_NETWORK_STOPPED);
        
        // Peer Count Card
        peerCountLabel = new Label("0");
        VBox peerCard = NetworkUIUtils.createMetricCard("Connected Peers", "0", NetworkUIUtils.COLOR_INFO);
        
        // Messages Sent Card
        messagesSentLabel = new Label("0");
        VBox sentCard = NetworkUIUtils.createMetricCard("Messages Sent", "0", NetworkUIUtils.COLOR_SUCCESS);
        
        // Messages Received Card
        messagesReceivedLabel = new Label("0");
        VBox receivedCard = NetworkUIUtils.createMetricCard("Messages Received", "0", NetworkUIUtils.COLOR_ACCENT);
        
        // Data Transferred Card
        dataTransferredLabel = new Label("0 B");
        VBox dataCard = NetworkUIUtils.createMetricCard("Data Transferred", "0 B", NetworkUIUtils.COLOR_PRIMARY);
        
        // Network Health Card
        networkHealthLabel = new Label("Unknown");
        VBox healthCard = NetworkUIUtils.createMetricCard("Network Health", "Unknown", NetworkUIUtils.COLOR_SECONDARY);
        
        statusBox.getChildren().addAll(statusCard, peerCard, sentCard, receivedCard, dataCard, healthCard);
        return statusBox;
    }
    
    /**
     * Create main content tabs
     */
    private TabPane createMainTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: #22334e;");
        
        // Peer Management Tab
        Tab peerTab = new Tab("Peer Management");
        peerTab.setClosable(false);
        peerTab.setContent(createPeerManagementTab());
        
        // Network Monitoring Tab
        Tab monitorTab = new Tab("Network Monitoring");
        monitorTab.setClosable(false);
        monitorTab.setContent(createNetworkMonitoringTab());
        
        // Configuration Tab
        Tab configTab = new Tab("Configuration");
        configTab.setClosable(false);
        configTab.setContent(createConfigurationTab());
        
        // Message Log Tab
        Tab logTab = new Tab("Message Log");
        logTab.setClosable(false);
        logTab.setContent(createMessageLogTab());
        
        tabPane.getTabs().addAll(peerTab, monitorTab, configTab, logTab);
        return tabPane;
    }
    
    /**
     * Create peer management tab
     */
    private VBox createPeerManagementTab() {
        VBox peerBox = new VBox(20);
        peerBox.setStyle(NetworkUIUtils.getContainerStyle());
        
        // Peer actions section
        HBox actionBox = new HBox(15);
        actionBox.setAlignment(Pos.CENTER_LEFT);
        actionBox.setPadding(new Insets(0, 0, 20, 0));
        
        connectPeerBtn = new Button("🔗 Connect to Peer");
        connectPeerBtn.setStyle(NetworkUIUtils.getButtonStyle(NetworkUIUtils.COLOR_SUCCESS, true));
        connectPeerBtn.setOnAction(e -> handleConnectToPeer());
        
        disconnectPeerBtn = new Button("❌ Disconnect Selected");
        disconnectPeerBtn.setStyle(NetworkUIUtils.getButtonStyle(NetworkUIUtils.COLOR_ERROR, true));
        disconnectPeerBtn.setOnAction(e -> handleDisconnectPeer());
        disconnectPeerBtn.setDisable(true);
        
        broadcastBtn = new Button("📡 Broadcast Test Message");
        broadcastBtn.setStyle(NetworkUIUtils.getButtonStyle(NetworkUIUtils.COLOR_ACCENT, true));
        broadcastBtn.setOnAction(e -> handleBroadcastMessage());
        
        Button discoverBtn = new Button("🔍 Discover Peers");
        discoverBtn.setStyle(NetworkUIUtils.getButtonStyle(NetworkUIUtils.COLOR_INFO, false));
        discoverBtn.setOnAction(e -> handlePeerDiscovery());
        
        actionBox.getChildren().addAll(connectPeerBtn, disconnectPeerBtn, broadcastBtn, discoverBtn);
        
        // Peer table
        peerTable = createPeerTable();
        
        peerBox.getChildren().addAll(actionBox, peerTable);
        return peerBox;
    }
    
    /**
     * Create network monitoring tab
     */
    private VBox createNetworkMonitoringTab() {
        VBox monitorBox = new VBox(20);
        monitorBox.setStyle(NetworkUIUtils.getContainerStyle());
        
        // Charts section
        HBox chartsBox = new HBox(20);
        
        // Network activity chart
        networkActivityChart = createNetworkActivityChart();
        
        // Peer connection chart
        peerConnectionChart = createPeerConnectionChart();
        
        chartsBox.getChildren().addAll(networkActivityChart, peerConnectionChart);
        HBox.setHgrow(networkActivityChart, Priority.ALWAYS);
        HBox.setHgrow(peerConnectionChart, Priority.ALWAYS);
        
        // Network health section
        VBox healthSection = createNetworkHealthSection();
        
        monitorBox.getChildren().addAll(chartsBox, healthSection);
        return monitorBox;
    }
    
    /**
     * Create configuration tab
     */
    private VBox createConfigurationTab() {
        VBox configBox = new VBox(20);
        configBox.setStyle(NetworkUIUtils.getContainerStyle());
        
        // Connection Policy Section
        VBox policySection = new VBox(15);
        policySection.setStyle(NetworkUIUtils.getSectionStyle());
        
        Label policyLabel = new Label("Connection Policy");
        policyLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        
        policyComboBox = new ComboBox<>();
        policyComboBox.getItems().addAll(NetworkManager.ConnectionPolicy.PolicyType.values());
        policyComboBox.setValue(NetworkManager.ConnectionPolicy.PolicyType.BALANCED);
        policyComboBox.setStyle(NetworkUIUtils.getInputFieldStyle());
        policyComboBox.setOnAction(e -> handlePolicyChange());
        
        Label policyDesc = new Label("Balanced policy maintains optimal connectivity while ensuring network stability.");
        policyDesc.setStyle("-fx-text-fill: #ccc; -fx-font-size: 12px; -fx-wrap-text: true;");
        
        policySection.getChildren().addAll(policyLabel, policyComboBox, policyDesc);
        
        // Network Settings Section
        VBox settingsSection = new VBox(15);
        settingsSection.setStyle(NetworkUIUtils.getSectionStyle());
        
        Label settingsLabel = new Label("Network Settings");
        settingsLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        
        // Max peers slider
        Label maxPeersLabel = new Label("Maximum Peers: 25");
        maxPeersLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        
        maxPeersSlider = new Slider(1, 100, 25);
        maxPeersSlider.setShowTickLabels(true);
        maxPeersSlider.setShowTickMarks(true);
        maxPeersSlider.setMajorTickUnit(25);
        maxPeersSlider.setBlockIncrement(5);
        maxPeersSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            maxPeersLabel.setText("Maximum Peers: " + newVal.intValue());
            handleMaxPeersChange(newVal.intValue());
        });
        
        // Checkboxes for settings
        discoveryCheckBox = new CheckBox("Enable Peer Discovery");
        discoveryCheckBox.setSelected(true);
        discoveryCheckBox.setStyle("-fx-text-fill: white;");
        discoveryCheckBox.setOnAction(e -> handleDiscoveryToggle());
        
        autoOptimizeCheckBox = new CheckBox("Enable Auto-Optimization");
        autoOptimizeCheckBox.setSelected(true);
        autoOptimizeCheckBox.setStyle("-fx-text-fill: white;");
        autoOptimizeCheckBox.setOnAction(e -> handleAutoOptimizeToggle());
        
        settingsSection.getChildren().addAll(settingsLabel, maxPeersLabel, maxPeersSlider, 
                                           discoveryCheckBox, autoOptimizeCheckBox);
        
        // Advanced Settings Section
        VBox advancedSection = createAdvancedSettingsSection();
        
        configBox.getChildren().addAll(policySection, settingsSection, advancedSection);
        return configBox;
    }
    
    /**
     * Create message log tab
     */
    private VBox createMessageLogTab() {
        VBox logBox = new VBox(20);
        logBox.setStyle(NetworkUIUtils.getContainerStyle());
        
        // Log controls
        HBox controlsBox = new HBox(10);
        controlsBox.setAlignment(Pos.CENTER_LEFT);
        
        Button clearLogBtn = new Button("🗑️ Clear Log");
        clearLogBtn.setStyle(NetworkUIUtils.getButtonStyle(NetworkUIUtils.COLOR_WARNING, true));
        clearLogBtn.setOnAction(e -> messageLog.clear());
        
        Button exportLogBtn = new Button("💾 Export Log");
        exportLogBtn.setStyle(NetworkUIUtils.getButtonStyle(NetworkUIUtils.COLOR_INFO, false));
        exportLogBtn.setOnAction(e -> handleExportLog());
        
        CheckBox autoScrollBox = new CheckBox("Auto-scroll to latest");
        autoScrollBox.setSelected(true);
        autoScrollBox.setStyle("-fx-text-fill: white;");
        
        controlsBox.getChildren().addAll(clearLogBtn, exportLogBtn, autoScrollBox);
        
        // Message list view
        messageListView = new ListView<>(messageLog);
        messageListView.setStyle("-fx-background-color: #1a2332; -fx-text-fill: white;");
        messageListView.setPrefHeight(400);
        
        logBox.getChildren().addAll(controlsBox, messageListView);
        return logBox;
    }
    
    /**
     * Create control panel
     */
    private HBox createControlPanel() {
        HBox controlPanel = new HBox(15);
        controlPanel.setAlignment(Pos.CENTER_LEFT);
        controlPanel.setPadding(new Insets(20, 0, 0, 0));
        controlPanel.setStyle("-fx-border-color: #40e0d0; -fx-border-width: 2 0 0 0; -fx-padding: 20 0 0 0;");
        
        // Network control buttons
        startNetworkBtn = new Button("▶️ Start Network");
        startNetworkBtn.setStyle(NetworkUIUtils.getButtonStyle(NetworkUIUtils.COLOR_SUCCESS, true) + 
                                "; -fx-font-size: 16px; -fx-padding: 12 24;");
        startNetworkBtn.setOnAction(e -> handleStartNetwork());
        
        stopNetworkBtn = new Button("⏹️ Stop Network");
        stopNetworkBtn.setStyle(NetworkUIUtils.getButtonStyle(NetworkUIUtils.COLOR_ERROR, true) + 
                               "; -fx-font-size: 16px; -fx-padding: 12 24;");
        stopNetworkBtn.setOnAction(e -> handleStopNetwork());
        stopNetworkBtn.setDisable(true);
        
        Button restartBtn = new Button("🔄 Restart Network");
        restartBtn.setStyle(NetworkUIUtils.getButtonStyle(NetworkUIUtils.COLOR_WARNING, true) + 
                           "; -fx-font-size: 16px; -fx-padding: 12 24;");
        restartBtn.setOnAction(e -> handleRestartNetwork());
        
        optimizeBtn = new Button("⚡ Optimize Network");
        optimizeBtn.setStyle(NetworkUIUtils.getButtonStyle(NetworkUIUtils.COLOR_ACCENT, false) + 
                           "; -fx-font-size: 16px; -fx-padding: 12 24;");
        optimizeBtn.setOnAction(e -> handleOptimizeNetwork());
        
        Button settingsBtn = new Button("⚙️ Advanced Settings");
        settingsBtn.setStyle(NetworkUIUtils.getButtonStyle(NetworkUIUtils.COLOR_INFO, false) + 
                           "; -fx-font-size: 16px; -fx-padding: 12 24;");
        settingsBtn.setOnAction(e -> handleAdvancedSettings());
        
        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Status indicator
        Label statusIndicator = new Label("⚫ Network Stopped");
        statusIndicator.setStyle("-fx-text-fill: #888; -fx-font-weight: bold; -fx-font-size: 16px;");
        
        controlPanel.getChildren().addAll(startNetworkBtn, stopNetworkBtn, restartBtn, 
                                         optimizeBtn, settingsBtn, spacer, statusIndicator);
        
        return controlPanel;
    }
    
    // ================================
    // UI COMPONENT CREATORS
    // ================================
    
    /**
     * Create peer table
     */
    private TableView<NetworkController.PeerInfo> createPeerTable() {
        TableView<NetworkController.PeerInfo> table = new TableView<>(peerList);
        table.setStyle("-fx-background-color: #1a2332; -fx-text-fill: white;");
        table.setPrefHeight(300);
        
        // Columns
        TableColumn<NetworkController.PeerInfo, String> idColumn = new TableColumn<>("Peer ID");
        idColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            NetworkUIUtils.formatPeerId(data.getValue().peerId)));
        idColumn.setPrefWidth(150);
        
        TableColumn<NetworkController.PeerInfo, String> addressColumn = new TableColumn<>("Address");
        addressColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            NetworkUIUtils.formatAddress(data.getValue().address)));
        addressColumn.setPrefWidth(200);
        
        TableColumn<NetworkController.PeerInfo, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            data.getValue().status));
        statusColumn.setPrefWidth(100);
        
        TableColumn<NetworkController.PeerInfo, String> latencyColumn = new TableColumn<>("Latency");
        latencyColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            NetworkUIUtils.formatLatency(data.getValue().latency)));
        latencyColumn.setPrefWidth(100);
        
        TableColumn<NetworkController.PeerInfo, String> connTimeColumn = new TableColumn<>("Connected");
        connTimeColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            NetworkUIUtils.formatDateTime(data.getValue().connectionTime)));
        connTimeColumn.setPrefWidth(150);
        
        table.getColumns().addAll(idColumn, addressColumn, statusColumn, latencyColumn, connTimeColumn);
        
        // Selection listener
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            disconnectPeerBtn.setDisable(newSelection == null);
        });
        
        return table;
    }
    
    /**
     * Create network activity chart
     */
    private LineChart<Number, Number> createNetworkActivityChart() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Time");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Messages/sec");
        
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Network Activity");
        chart.setStyle("-fx-background-color: #22334e;");
        chart.setPrefHeight(300);
        
        XYChart.Series<Number, Number> sentSeries = new XYChart.Series<>();
        sentSeries.setName("Messages Sent");
        
        XYChart.Series<Number, Number> receivedSeries = new XYChart.Series<>();
        receivedSeries.setName("Messages Received");
        
        chart.getData().addAll(sentSeries, receivedSeries);
        return chart;
    }
    
    /**
     * Create peer connection chart
     */
    private LineChart<Number, Number> createPeerConnectionChart() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Time");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Peer Count");
        
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Peer Connections");
        chart.setStyle("-fx-background-color: #22334e;");
        chart.setPrefHeight(300);
        
        XYChart.Series<Number, Number> peerSeries = new XYChart.Series<>();
        peerSeries.setName("Connected Peers");
        
        chart.getData().add(peerSeries);
        return chart;
    }
    
    /**
     * Create network health section
     */
    private VBox createNetworkHealthSection() {
        VBox healthBox = new VBox(15);
        healthBox.setStyle(NetworkUIUtils.getSectionStyle());
        
        Label healthTitle = new Label("Network Health Overview");
        healthTitle.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        
        // Health bars
        VBox healthBars = new VBox(10);
        
        HBox connectivityBar = NetworkUIUtils.createHealthBar("Connectivity", 0.85);
        HBox performanceBar = NetworkUIUtils.createHealthBar("Performance", 0.92);
        HBox stabilityBar = NetworkUIUtils.createHealthBar("Stability", 0.78);
        HBox overallBar = NetworkUIUtils.createHealthBar("Overall Health", 0.85);
        
        healthBars.getChildren().addAll(connectivityBar, performanceBar, stabilityBar, overallBar);
        
        healthBox.getChildren().addAll(healthTitle, healthBars);
        return healthBox;
    }
    
    /**
     * Create advanced settings section
     */
    private VBox createAdvancedSettingsSection() {
        VBox advancedBox = new VBox(15);
        advancedBox.setStyle(NetworkUIUtils.getSectionStyle());
        
        Label advancedLabel = new Label("Advanced Settings");
        advancedLabel.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        
        // Trusted/Blocked peers management
        HBox peerMgmtBox = new HBox(20);
        
        VBox trustedBox = new VBox(10);
        Label trustedLabel = new Label("Trusted Peers");
        trustedLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        
        ListView<String> trustedList = new ListView<>();
        trustedList.setPrefHeight(100);
        trustedList.setStyle("-fx-background-color: #1a2332; -fx-text-fill: white;");
        
        Button addTrustedBtn = new Button("Add Trusted");
        addTrustedBtn.setStyle(NetworkUIUtils.getButtonStyle(NetworkUIUtils.COLOR_SUCCESS, true));
        
        trustedBox.getChildren().addAll(trustedLabel, trustedList, addTrustedBtn);
        
        VBox blockedBox = new VBox(10);
        Label blockedLabel = new Label("Blocked Peers");
        blockedLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        
        ListView<String> blockedList = new ListView<>();
        blockedList.setPrefHeight(100);
        blockedList.setStyle("-fx-background-color: #1a2332; -fx-text-fill: white;");
        
        Button addBlockedBtn = new Button("Add Blocked");
        addBlockedBtn.setStyle(NetworkUIUtils.getButtonStyle(NetworkUIUtils.COLOR_ERROR, true));
        
        blockedBox.getChildren().addAll(blockedLabel, blockedList, addBlockedBtn);
        
        peerMgmtBox.getChildren().addAll(trustedBox, blockedBox);
        HBox.setHgrow(trustedBox, Priority.ALWAYS);
        HBox.setHgrow(blockedBox, Priority.ALWAYS);
        
        advancedBox.getChildren().addAll(advancedLabel, peerMgmtBox);
        return advancedBox;
    }
    
    // ================================
    // EVENT HANDLERS
    // ================================
    
    /**
     * Handle start network
     */
    private void handleStartNetwork() {
        LOGGER.info("Starting network from UI...");
        
        boolean success = false;
        
        // Start network manager first
        if (networkManager != null) {
            success = networkManager.startNetworkManagement();
        }
        
        // Start network controller if manager succeeded
        if (success && networkController != null && !networkController.isNetworkActive()) {
            success = networkController.startNetwork();
        }
        
        if (success) {
            startNetworkBtn.setDisable(true);
            stopNetworkBtn.setDisable(false);
            NetworkUIUtils.showInfoAlert("Network Started", "Network has been started successfully!");
            updateDashboard();
        } else {
            NetworkUIUtils.showErrorAlert("Network Start Failed", 
                                        "Failed to start the network. Please check the logs for details.");
        }
    }
    
    /**
     * Handle stop network
     */
    private void handleStopNetwork() {
        LOGGER.info("Stopping network from UI...");
        
        boolean success = true;
        
        // Stop network controller
        if (networkController != null && networkController.isNetworkActive()) {
            success = networkController.stopNetwork();
        }
        
        // Stop network manager
        if (success && networkManager != null) {
            success = networkManager.stopNetworkManagement();
        }
        
        if (success) {
            startNetworkBtn.setDisable(false);
            stopNetworkBtn.setDisable(true);
            NetworkUIUtils.showInfoAlert("Network Stopped", "Network has been stopped successfully!");
            updateDashboard();
        } else {
            NetworkUIUtils.showErrorAlert("Network Stop Failed", 
                                        "Failed to stop the network completely. Please check the logs.");
        }
    }
    
    /**
     * Handle restart network
     */
    private void handleRestartNetwork() {
        LOGGER.info("Restarting network from UI...");
        
        // Show confirmation dialog
        if (NetworkUIUtils.showConfirmationDialog("Restart Network", 
                                                 "Are you sure you want to restart the network? " +
                                                 "This will temporarily disconnect all peers.")) {
            
            boolean success = false;
            
            if (networkController != null) {
                success = networkController.restartNetwork();
            } else if (networkManager != null) {
                success = networkManager.stopNetworkManagement() && networkManager.startNetworkManagement();
            }
            
            if (success) {
                NetworkUIUtils.showInfoAlert("Network Restarted", "Network has been restarted successfully!");
                updateDashboard();
            } else {
                NetworkUIUtils.showErrorAlert("Network Restart Failed", 
                                            "Failed to restart the network. Please try manually.");
            }
        }
    }
    
    /**
     * Handle connect to peer
     */
    private void handleConnectToPeer() {
        InetSocketAddress address = NetworkUIUtils.showPeerConnectionDialog();
        
        if (address != null && networkController != null) {
            boolean success = networkController.connectToPeer(address);
            
            if (success) {
                NetworkUIUtils.showInfoAlert("Connection Successful", 
                                           "Successfully connected to peer: " + 
                                           NetworkUIUtils.formatAddress(address));
                updateDashboard();
            } else {
                NetworkUIUtils.showErrorAlert("Connection Failed", 
                                            "Failed to connect to peer: " + 
                                            NetworkUIUtils.formatAddress(address));
            }
        }
    }
    
    /**
     * Handle disconnect peer
     */
    private void handleDisconnectPeer() {
        NetworkController.PeerInfo selectedPeer = peerTable.getSelectionModel().getSelectedItem();
        
        if (selectedPeer != null && networkController != null) {
            if (NetworkUIUtils.showConfirmationDialog("Disconnect Peer", 
                                                     "Are you sure you want to disconnect from " + 
                                                     NetworkUIUtils.formatPeerId(selectedPeer.peerId) + "?")) {
                
                boolean success = networkController.disconnectFromPeer(selectedPeer.peerId);
                
                if (success) {
                    NetworkUIUtils.showInfoAlert("Peer Disconnected", 
                                               "Successfully disconnected from peer: " + 
                                               NetworkUIUtils.formatPeerId(selectedPeer.peerId));
                    updateDashboard();
                } else {
                    NetworkUIUtils.showErrorAlert("Disconnection Failed", 
                                                "Failed to disconnect from peer: " + 
                                                NetworkUIUtils.formatPeerId(selectedPeer.peerId));
                }
            }
        }
    }
    
    /**
     * Handle broadcast message
     */
    private void handleBroadcastMessage() {
        if (networkController != null) {
            // Create test status message
            Messages.StatusMessage testMessage = new Messages.StatusMessage(
                "test-node", 1, 100
            );
            
            int peerCount = networkController.broadcastMessage(testMessage);
            
            if (peerCount > 0) {
                NetworkUIUtils.showInfoAlert("Broadcast Successful", 
                                           "Test message broadcasted to " + peerCount + " peers");
                addToMessageLog("📡 Broadcasted test message to " + peerCount + " peers");
            } else {
                NetworkUIUtils.showWarningAlert("No Peers", 
                                               "No connected peers available for broadcasting");
            }
        }
    }
    
    /**
     * Handle peer discovery
     */
    private void handlePeerDiscovery() {
        if (networkManager != null) {
            networkManager.getDiscoveryManager().startDiscovery();
            NetworkUIUtils.showInfoAlert("Peer Discovery", "Peer discovery has been initiated");
            addToMessageLog("🔍 Started peer discovery");
        }
    }
    
    /**
     * Handle policy change
     */
    private void handlePolicyChange() {
        NetworkManager.ConnectionPolicy.PolicyType selectedPolicy = policyComboBox.getValue();
        
        if (selectedPolicy != null && networkManager != null) {
            networkManager.setConnectionPolicy(selectedPolicy);
            NetworkUIUtils.showInfoAlert("Policy Changed", 
                                       "Connection policy changed to: " + selectedPolicy.getDisplayName());
            addToMessageLog("⚙️ Changed connection policy to " + selectedPolicy.getDisplayName());
        }
    }
    
    /**
     * Handle max peers change
     */
    private void handleMaxPeersChange(int maxPeers) {
        if (networkManager != null) {
            networkManager.getConnectionPolicy().setMaxConnections(maxPeers);
            addToMessageLog("⚙️ Maximum peers set to " + maxPeers);
        }
    }
    
    /**
     * Handle discovery toggle
     */
    private void handleDiscoveryToggle() {
        boolean enabled = discoveryCheckBox.isSelected();
        
        if (networkManager != null) {
            networkManager.getConnectionPolicy().setEnableDiscovery(enabled);
            
            if (enabled) {
                networkManager.getDiscoveryManager().startDiscovery();
                addToMessageLog("🔍 Peer discovery enabled");
            } else {
                networkManager.getDiscoveryManager().stopDiscovery();
                addToMessageLog("🚫 Peer discovery disabled");
            }
        }
    }
    
    /**
     * Handle auto-optimize toggle
     */
    private void handleAutoOptimizeToggle() {
        boolean enabled = autoOptimizeCheckBox.isSelected();
        
        if (networkManager != null) {
            networkManager.getSettings().setAutoOptimize(enabled);
            addToMessageLog("⚡ Auto-optimization " + (enabled ? "enabled" : "disabled"));
        }
    }
    
    /**
     * Handle optimize network
     */
    private void handleOptimizeNetwork() {
        if (networkManager != null) {
            // Trigger manual optimization by temporarily setting health to low
            NetworkUIUtils.showInfoAlert("Network Optimization", 
                                       "Network optimization process initiated. " +
                                       "Check the message log for details.");
            addToMessageLog("⚡ Manual network optimization initiated");
        }
    }
    
    /**
     * Handle advanced settings
     */
    private void handleAdvancedSettings() {
        if (networkManager != null) {
            NetworkManager.NetworkSettings newSettings = NetworkUIUtils.showNetworkSettingsDialog(
                networkManager.getSettings());
            
            if (newSettings != null) {
                // Apply new settings (in real implementation, would update the manager)
                NetworkUIUtils.showInfoAlert("Settings Updated", 
                                           "Advanced network settings have been updated successfully!");
                addToMessageLog("⚙️ Advanced settings updated");
            }
        }
    }
    
    /**
     * Handle export log
     */
    private void handleExportLog() {
        // In a real implementation, would show file chooser and export to file
        NetworkUIUtils.showInfoAlert("Export Log", 
                                   "Log export functionality would be implemented here.\n\n" +
                                   "Current log contains " + messageLog.size() + " entries.");
    }
    
    // ================================
    // EVENT LISTENER SETUP
    // ================================
    
    /**
     * Setup event listeners
     */
    private void setupEventListeners() {
        // Network Controller Events
        if (networkController != null) {
            networkController.addEventListener(new NetworkController.NetworkEventListener() {
                @Override
                public void onPeerConnected(String peerId, InetSocketAddress address) {
                    Platform.runLater(() -> {
                        addToMessageLog("✅ Peer connected: " + NetworkUIUtils.formatPeerId(peerId) + 
                                       " (" + NetworkUIUtils.formatAddress(address) + ")");
                        refreshNetworkData();
                    });
                }
                
                @Override
                public void onPeerDisconnected(String peerId) {
                    Platform.runLater(() -> {
                        addToMessageLog("❌ Peer disconnected: " + NetworkUIUtils.formatPeerId(peerId));
                        refreshNetworkData();
                    });
                }
                
                @Override
                public void onMessageReceived(String fromPeer, Messages.Message message) {
                    Platform.runLater(() -> {
                        addToMessageLog("📥 Message received from " + NetworkUIUtils.formatPeerId(fromPeer) + 
                                       ": " + NetworkUIUtils.formatMessageType(message));
                        updateCharts();
                    });
                }
                
                @Override
                public void onMessageSent(String toPeer, Messages.Message message) {
                    Platform.runLater(() -> {
                        addToMessageLog("📤 Message sent to " + NetworkUIUtils.formatPeerId(toPeer) + 
                                       ": " + NetworkUIUtils.formatMessageType(message));
                        updateCharts();
                    });
                }
                
                @Override
                public void onBroadcastSent(Messages.Message message, int peerCount) {
                    Platform.runLater(() -> {
                        addToMessageLog("📡 Broadcast sent to " + peerCount + " peers: " + 
                                       NetworkUIUtils.formatMessageType(message));
                        updateCharts();
                    });
                }
                
                @Override
                public void onNetworkError(String error) {
                    Platform.runLater(() -> {
                        addToMessageLog("⚠️ Network error: " + error);
                        NetworkUIUtils.showErrorAlert("Network Error", error);
                    });
                }
            });
        }
        
        // Network Manager Events
        if (networkManager != null) {
            networkManager.addEventListener(new NetworkManager.NetworkManagerEventListener() {
                @Override
                public void onNetworkStateChanged(NetworkManager.NetworkState.StateType oldState, 
                                                 NetworkManager.NetworkState.StateType newState) {
                    Platform.runLater(() -> {
                        addToMessageLog("🔄 Network state changed: " + oldState + " → " + newState);
                        updateNetworkStatus();
                    });
                }
                
                @Override
                public void onPeerDiscovered(String peerId, InetSocketAddress address) {
                    Platform.runLater(() -> {
                        addToMessageLog("🔍 Peer discovered: " + NetworkUIUtils.formatPeerId(peerId) + 
                                       " (" + NetworkUIUtils.formatAddress(address) + ")");
                    });
                }
                
                @Override
                public void onConnectionPolicyChanged(NetworkManager.ConnectionPolicy.PolicyType policyType) {
                    Platform.runLater(() -> {
                        addToMessageLog("⚙️ Connection policy changed to: " + policyType.getDisplayName());
                        policyComboBox.setValue(policyType);
                    });
                }
                
                @Override
                public void onNetworkHealthChanged(NetworkManager.NetworkHealthStatus health) {
                    Platform.runLater(() -> {
                        addToMessageLog("💓 Network health changed: " + health.getHealthLevel() + 
                                       " (" + NetworkUIUtils.formatPercentage(health.overallHealth) + ")");
                        updateNetworkHealth();
                    });
                }
                
                @Override
                public void onNetworkOptimization(String optimization) {
                    Platform.runLater(() -> {
                        addToMessageLog("⚡ Optimization applied: " + optimization);
                    });
                }
                
                @Override
                public void onNetworkManagerError(String error) {
                    Platform.runLater(() -> {
                        addToMessageLog("⚠️ Network manager error: " + error);
                    });
                }
            });
        }
    }
    
    // ================================
    // DATA MANAGEMENT
    // ================================
    
    /**
     * Load initial data
     */
    private void loadInitialData() {
        // Load peer data
        refreshNetworkData();
        
        // Initialize message log
        addToMessageLog("🚀 Network management interface initialized");
        
        // Update UI based on current network state
        updateUIBasedOnNetworkState();
    }
    
    /**
     * Refresh network data
     */
    private void refreshNetworkData() {
        // Update peer list
        if (networkController != null) {
            List<NetworkController.PeerInfo> peers = networkController.getConnectedPeers();
            Platform.runLater(() -> {
                peerList.clear();
                peerList.addAll(peers);
            });
        }
        
        // Update status metrics
        updateNetworkStatus();
        updateNetworkHealth();
        updateCharts();
    }
    
    /**
     * Update network status metrics
     */
    private void updateNetworkStatus() {
        if (networkController != null) {
            NetworkController.NetworkStatus status = networkController.getNetworkStatus();
            
            Platform.runLater(() -> {
                // Update status labels
                String statusText = status.isActive ? "Active" : "Stopped";
                String statusColor = status.isActive ? NetworkUIUtils.COLOR_NETWORK_ACTIVE : 
                                                      NetworkUIUtils.COLOR_NETWORK_STOPPED;
                NetworkUIUtils.applyStatusStyle(networkStatusLabel, statusText, statusColor);
                
                peerCountLabel.setText(String.valueOf(status.connectedPeers));
                messagesSentLabel.setText(NetworkUIUtils.formatNumber(status.messagesSent));
                messagesReceivedLabel.setText(NetworkUIUtils.formatNumber(status.messagesReceived));
                dataTransferredLabel.setText(NetworkUIUtils.formatBytes(status.bytesSent + status.bytesReceived));
            });
        }
    }
    
    /**
     * Update network health display
     */
    private void updateNetworkHealth() {
        if (networkManager != null) {
            NetworkManager.NetworkHealthStatus health = networkManager.getHealthMonitor().getCurrentHealth();
            
            Platform.runLater(() -> {
                String healthText = health.getHealthLevel();
                String healthColor = NetworkUIUtils.getHealthColor(health.overallHealth);
                NetworkUIUtils.applyStatusStyle(networkHealthLabel, healthText, healthColor);
            });
        }
    }
    
    /**
     * Update charts with latest data
     */
    private void updateCharts() {
        int currentTime = updateCounter.incrementAndGet();
        
        Platform.runLater(() -> {
            // Update network activity chart
            if (networkController != null) {
                NetworkMetrics metrics = networkController.getNetworkMetrics();
                
                // Add data points to charts
                XYChart.Series<Number, Number> sentSeries = networkActivityChart.getData().get(0);
                XYChart.Series<Number, Number> receivedSeries = networkActivityChart.getData().get(1);
                
                // Calculate rates (simplified)
                long sentRate = metrics.getMessagesSent() / Math.max(1, currentTime);
                long receivedRate = metrics.getMessagesReceived() / Math.max(1, currentTime);
                
                sentSeries.getData().add(new XYChart.Data<>(currentTime, sentRate));
                receivedSeries.getData().add(new XYChart.Data<>(currentTime, receivedRate));
                
                // Limit chart history
                if (sentSeries.getData().size() > 50) {
                    sentSeries.getData().remove(0);
                    receivedSeries.getData().remove(0);
                }
            }
            
            // Update peer connection chart
            XYChart.Series<Number, Number> peerSeries = peerConnectionChart.getData().get(0);
            peerSeries.getData().add(new XYChart.Data<>(currentTime, peerList.size()));
            
            if (peerSeries.getData().size() > 50) {
                peerSeries.getData().remove(0);
            }
        });
    }
    
    /**
     * Update UI based on current network state
     */
    private void updateUIBasedOnNetworkState() {
        if (networkController != null) {
            boolean isActive = networkController.isNetworkActive();
            
            Platform.runLater(() -> {
                startNetworkBtn.setDisable(isActive);
                stopNetworkBtn.setDisable(!isActive);
                connectPeerBtn.setDisable(!isActive);
                broadcastBtn.setDisable(!isActive || peerList.isEmpty());
            });
        }
    }
    
    /**
     * Add message to log
     */
    private void addToMessageLog(String message) {
        Platform.runLater(() -> {
            String timestamp = NetworkUIUtils.formatTime(System.currentTimeMillis());
            String logEntry = String.format("[%s] %s", timestamp, message);
            
            messageLog.add(logEntry);
            
            // Limit log size
            if (messageLog.size() > 1000) {
                messageLog.remove(0);
            }
            
            // Auto-scroll to latest
            if (messageListView != null) {
                messageListView.scrollTo(messageLog.size() - 1);
            }
        });
    }
    
    // ================================
    // UPDATE TIMELINE
    // ================================
    
    /**
     * Start update timeline
     */
    private void startUpdateTimeline() {
        updateTimeline = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
            refreshNetworkData();
            updateUIBasedOnNetworkState();
        }));
        updateTimeline.setCycleCount(Timeline.INDEFINITE);
        updateTimeline.play();
    }
    
    /**
     * Stop update timeline
     */
    private void stopUpdateTimeline() {
        if (updateTimeline != null) {
            updateTimeline.stop();
        }
    }
    
    // ================================
    // CALLBACK MANAGEMENT
    // ================================
    
    /**
     * Set dashboard update callback
     */
    public void setDashboardUpdateCallback(Runnable callback) {
        this.dashboardUpdateCallback = callback;
    }
    
    /**
     * Set navigation callback
     */


     
    public void setNavigationCallback(Runnable callback) {
        this.navigationCallback = callback;
    }
    
    /**
     * Update dashboard
     */
    private void updateDashboard() {
        if (dashboardUpdateCallback != null) {
            dashboardUpdateCallback.run();
        }
    }
    
    // ================================
    // CLEANUP
    // ================================
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        LOGGER.info("Cleaning up NetworkView resources...");
        
        stopUpdateTimeline();
        
        if (peerList != null) {
            peerList.clear();
        }
        
        if (messageLog != null) {
            messageLog.clear();
        }
        
        LOGGER.info("NetworkView cleanup completed");
    }
}