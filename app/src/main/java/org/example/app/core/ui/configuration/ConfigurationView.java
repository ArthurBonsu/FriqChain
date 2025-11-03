package org.example.app.core.ui.configuration;

import org.example.app.core.config.*;
import org.example.app.core.ui.consensus.ConsensusController.ConsensusType;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.collections.FXCollections;
import javafx.beans.binding.Bindings;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import java.io.File;
import java.util.*;
import java.util.logging.Logger;
import javafx.stage.DirectoryChooser;
/**
 * Main configuration view with all panels integrated
 */
public class ConfigurationView {
    private static final Logger LOGGER = Logger.getLogger(ConfigurationView.class.getName());
    
    private final ConfigurationController controller;
    private final ConfigurationManager manager;
    
    // UI Components
    private VBox mainContainer;
    private TabPane configTabPane;
    private Label statusLabel;
    private Button saveButton;
    private Button resetButton;
    private ComboBox<String> profileSelector;
    
    // Callbacks
    private Runnable dashboardUpdateCallback;
    private Runnable navigationCallback;
    
    // Current configuration values
    private final Map<String, Object> pendingChanges = new HashMap<>();
    
    public ConfigurationView(ConfigurationController controller, ConfigurationManager manager) {
        this.controller = controller;
        this.manager = manager;
        initialize();
    }
    
    private void initialize() {
        setupEventListeners();
        LOGGER.info("ConfigurationView initialized");
    }
    
    /**
     * Create the main configuration management view
     */
    public VBox createConfigurationManagementView() {
        mainContainer = new VBox(20);
        mainContainer.setStyle("-fx-background-color: #16213e; -fx-padding: 30;");
        
        // Header section
        HBox headerSection = createHeaderSection();
        
        // Profile selection section
        HBox profileSection = createProfileSection();
        
        // Main configuration tabs
        configTabPane = createConfigurationTabs();
        configTabPane.setPrefHeight(500);
        
        // Action buttons section
        HBox actionSection = createActionSection();
        
        // Status section
        HBox statusSection = createStatusSection();
        
        mainContainer.getChildren().addAll(
            headerSection,
            profileSection,
            new Separator(),
            configTabPane,
            new Separator(),
            actionSection,
            statusSection
        );
        
        // Load current configuration
        loadCurrentConfiguration();
        
        return mainContainer;
    }
    
    /**
     * Create header section
     */
    private HBox createHeaderSection() {
        HBox header = new HBox(20);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label("⚙️ Configuration Management");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 28));
        titleLabel.setTextFill(Color.web("#ffffff"));
        
        Label subtitleLabel = new Label("Configure blockchain, consensus, network, and storage settings");
        subtitleLabel.setTextFill(Color.web("#cccccc"));
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button backButton = ConfigurationUIUtils.createSecondaryButton("← Back to Dashboard");
        backButton.setOnAction(e -> {
            if (controller.hasUnsavedChangesProperty().get()) {
                if (ConfigurationUIUtils.showConfirmDialog(
                    "Unsaved Changes",
                    "You have unsaved changes",
                    "Do you want to save before leaving?")) {
                    saveConfiguration();
                }
            }
            if (navigationCallback != null) {
                navigationCallback.run();
            }
        });
        
        VBox titleBox = new VBox(5);
        titleBox.getChildren().addAll(titleLabel, subtitleLabel);
        
        header.getChildren().addAll(titleBox, spacer, backButton);
        return header;
    }
    
    /**
     * Create profile selection section
     */
    private HBox createProfileSection() {
        HBox section = new HBox(20);
        section.setAlignment(Pos.CENTER_LEFT);
        section.setPadding(new Insets(10, 0, 10, 0));
        
        Label profileLabel = new Label("Configuration Profile:");
        profileLabel.setTextFill(Color.web("#ffffff"));
        profileLabel.setStyle("-fx-font-weight: bold;");
        
        profileSelector = ConfigurationUIUtils.createStyledComboBox();
        profileSelector.setItems(controller.getAvailableProfiles());
        profileSelector.setValue(controller.currentProfileNameProperty().get());
        profileSelector.setPrefWidth(200);
        
        Button loadProfileBtn = ConfigurationUIUtils.createPrimaryButton("Load Profile");
        loadProfileBtn.setOnAction(e -> loadSelectedProfile());
        
        Button saveProfileBtn = ConfigurationUIUtils.createSecondaryButton("Save as Profile");
        saveProfileBtn.setOnAction(e -> saveAsProfile());
        
        Button deleteProfileBtn = ConfigurationUIUtils.createDangerButton("Delete Profile");
        deleteProfileBtn.setOnAction(e -> deleteProfile());
        
        section.getChildren().addAll(
            profileLabel, profileSelector,
            loadProfileBtn, saveProfileBtn, deleteProfileBtn
        );
        
        return section;
    }
    
    /**
     * Create main configuration tabs
     */
    private TabPane createConfigurationTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: #22334e;");
        
        // Quick Setup tab
        Tab quickSetupTab = new Tab("Quick Setup");
        quickSetupTab.setClosable(false);
        quickSetupTab.setContent(createQuickSetupPanel());
        
        // Consensus tab
        Tab consensusTab = new Tab("Consensus");
        consensusTab.setClosable(false);
        consensusTab.setContent(createConsensusPanel());
        
        // Network tab
        Tab networkTab = new Tab("Network");
        networkTab.setClosable(false);
        networkTab.setContent(createNetworkPanel());
        
        // Storage tab
        Tab storageTab = new Tab("Storage");
        storageTab.setClosable(false);
        storageTab.setContent(createStoragePanel());
        
        // Security tab
        Tab securityTab = new Tab("Security");
        securityTab.setClosable(false);
        securityTab.setContent(createSecurityPanel());
        
        // Advanced tab
        Tab advancedTab = new Tab("Advanced");
        advancedTab.setClosable(false);
        advancedTab.setContent(createAdvancedPanel());
        
        tabPane.getTabs().addAll(
            quickSetupTab, consensusTab, networkTab,
            storageTab, securityTab, advancedTab
        );
        
        return tabPane;
    }
    
    /**
     * Create Quick Setup panel
     */
    private ScrollPane createQuickSetupPanel() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #16213e;");
        
        Label header = ConfigurationUIUtils.createSectionHeader("Quick Configuration Setup");
        
        // Network type selection
        VBox networkTypeCard = ConfigurationUIUtils.createConfigCard(
            "Network Type",
            "Choose the network type for your blockchain",
            null
        );
        
        ToggleGroup networkGroup = new ToggleGroup();
        RadioButton mainnetRadio = new RadioButton("Mainnet");
        RadioButton testnetRadio = new RadioButton("Testnet");
        RadioButton devRadio = new RadioButton("Development");
        
        mainnetRadio.setToggleGroup(networkGroup);
        testnetRadio.setToggleGroup(networkGroup);
        devRadio.setToggleGroup(networkGroup);
        devRadio.setSelected(true);
        
        mainnetRadio.setTextFill(Color.WHITE);
        testnetRadio.setTextFill(Color.WHITE);
        devRadio.setTextFill(Color.WHITE);
        
        HBox networkRadios = new HBox(20);
        networkRadios.getChildren().addAll(mainnetRadio, testnetRadio, devRadio);
        networkTypeCard.getChildren().add(networkRadios);
        
        // Consensus mechanism selection
        VBox consensusCard = ConfigurationUIUtils.createConfigCard(
            "Consensus Mechanism",
            "Select the primary consensus algorithm",
            null
        );
        
        ComboBox<ConsensusType> consensusCombo = ConfigurationUIUtils.createStyledComboBox();
        consensusCombo.setItems(FXCollections.observableArrayList(ConsensusType.values()));
        consensusCombo.setValue(ConsensusType.PROOF_OF_WORK);
        consensusCombo.setPrefWidth(250);
        
        consensusCard.getChildren().add(consensusCombo);
        
        // Performance presets
        VBox performanceCard = ConfigurationUIUtils.createConfigCard(
            "Performance Preset",
            "Choose a performance configuration preset",
            null
        );
        
        ToggleGroup perfGroup = new ToggleGroup();
        RadioButton lowPerf = new RadioButton("Low (Minimal resources)");
        RadioButton medPerf = new RadioButton("Medium (Balanced)");
        RadioButton highPerf = new RadioButton("High (Maximum performance)");
        
        lowPerf.setToggleGroup(perfGroup);
        medPerf.setToggleGroup(perfGroup);
        highPerf.setToggleGroup(perfGroup);
        medPerf.setSelected(true);
        
        lowPerf.setTextFill(Color.WHITE);
        medPerf.setTextFill(Color.WHITE);
        highPerf.setTextFill(Color.WHITE);
        
        VBox perfRadios = new VBox(10);
        perfRadios.getChildren().addAll(lowPerf, medPerf, highPerf);
        performanceCard.getChildren().add(perfRadios);
        
        // Apply quick setup button
        Button applyButton = ConfigurationUIUtils.createPrimaryButton("Apply Quick Setup");
        applyButton.setOnAction(e -> {
            applyQuickSetup(
                networkGroup.getSelectedToggle() == devRadio ? "development" :
                networkGroup.getSelectedToggle() == testnetRadio ? "testnet" : "mainnet",
                consensusCombo.getValue(),
                perfGroup.getSelectedToggle() == lowPerf ? "low" :
                perfGroup.getSelectedToggle() == highPerf ? "high" : "medium"
            );
        });
        
        content.getChildren().addAll(
            header, networkTypeCard, consensusCard, performanceCard, applyButton
        );
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #16213e; -fx-background-color: #16213e;");
        
        return scrollPane;
    }
    
    /**
     * Create Consensus panel
     */
    private ScrollPane createConsensusPanel() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #16213e;");
        
        Label header = ConfigurationUIUtils.createSectionHeader("Consensus Configuration");
        
        // General consensus settings
        VBox generalCard = ConfigurationUIUtils.createConfigCard(
            "General Settings",
            "Configure general consensus parameters",
            null
        );
        
        ComboBox<ConsensusType> typeCombo = ConfigurationUIUtils.createStyledComboBox();
        typeCombo.setItems(FXCollections.observableArrayList(ConsensusType.values()));
        typeCombo.setValue(controller.getConsensusConfig().getDefaultConsensusType());
        
        TextField blockTimeField = ConfigurationUIUtils.createNumberField("Block time (ms)", false);
        blockTimeField.setText(String.valueOf(controller.getConsensusConfig().getMaxBlockSize()));
        
        generalCard.getChildren().addAll(
            ConfigurationUIUtils.createConfigField("Consensus Type", typeCombo),
            ConfigurationUIUtils.createConfigField("Max Block Size", blockTimeField)
        );
        
        // PoW settings
        VBox powCard = ConfigurationUIUtils.createConfigCard(
            "Proof of Work Settings",
            "Configure PoW-specific parameters",
            null
        );
        
        TextField targetBlockTime = ConfigurationUIUtils.createNumberField("Target time (ms)", false);
        targetBlockTime.setText(String.valueOf(controller.getConsensusConfig().getPowTargetBlockTime()));
        
        TextField initialDifficulty = ConfigurationUIUtils.createNumberField("Initial difficulty", false);
        initialDifficulty.setText(String.valueOf(controller.getConsensusConfig().getPowInitialDifficulty()));
        
        CheckBox dynamicDifficulty = ConfigurationUIUtils.createStyledCheckBox("Enable dynamic difficulty");
        dynamicDifficulty.setSelected(controller.getConsensusConfig().isPowDynamicDifficultyEnabled());
        
        powCard.getChildren().addAll(
            ConfigurationUIUtils.createConfigField("Target Block Time", targetBlockTime),
            ConfigurationUIUtils.createConfigField("Initial Difficulty", initialDifficulty),
            dynamicDifficulty
        );
        
        // PoS settings
        VBox posCard = ConfigurationUIUtils.createConfigCard(
            "Proof of Stake Settings",
            "Configure PoS-specific parameters",
            null
        );
        
        TextField minStake = ConfigurationUIUtils.createNumberField("Minimum stake", false);
        minStake.setText(String.valueOf(controller.getConsensusConfig().getPosMinimumStake()));
        
        TextField validatorCount = ConfigurationUIUtils.createNumberField("Validators", false);
        validatorCount.setText(String.valueOf(controller.getConsensusConfig().getPosValidatorSetSize()));
        
        posCard.getChildren().addAll(
            ConfigurationUIUtils.createConfigField("Minimum Stake", minStake),
            ConfigurationUIUtils.createConfigField("Validator Count", validatorCount)
        );
        
        // PBFT settings
        VBox pbftCard = ConfigurationUIUtils.createConfigCard(
            "PBFT Settings",
            "Configure PBFT-specific parameters",
            null
        );
        
        TextField viewTimeout = ConfigurationUIUtils.createNumberField("View timeout (ms)", false);
        viewTimeout.setText(String.valueOf(controller.getConsensusConfig().getPbftViewTimeout()));
        
        TextField minValidators = ConfigurationUIUtils.createNumberField("Min validators", false);
        minValidators.setText(String.valueOf(controller.getConsensusConfig().getPbftMinValidators()));
        
        pbftCard.getChildren().addAll(
            ConfigurationUIUtils.createConfigField("View Timeout", viewTimeout),
            ConfigurationUIUtils.createConfigField("Min Validators", minValidators)
        );
        
        content.getChildren().addAll(header, generalCard, powCard, posCard, pbftCard);
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #16213e; -fx-background-color: #16213e;");
        
        return scrollPane;
    }
    
    /**
     * Create Network panel
     */
    private ScrollPane createNetworkPanel() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #16213e;");
        
        Label header = ConfigurationUIUtils.createSectionHeader("Network Configuration");
        
        // Network settings
        VBox networkCard = ConfigurationUIUtils.createConfigCard(
            "Network Settings",
            "Configure network and P2P parameters",
            null
        );
        
        TextField networkIdField = ConfigurationUIUtils.createStyledTextField("Network ID");
        networkIdField.setText(controller.getNetworkConfig().getNetworkId());
        
        TextField maxPeersField = ConfigurationUIUtils.createNumberField("Max peers", false);
        maxPeersField.setText(String.valueOf(controller.getNetworkConfig().getMaxPeers()));
        
        TextField portField = ConfigurationUIUtils.createNumberField("Port", false);
        portField.setText(String.valueOf(controller.getNetworkConfig().getPort()));
        
        networkCard.getChildren().addAll(
            ConfigurationUIUtils.createConfigField("Network ID", networkIdField),
            ConfigurationUIUtils.createConfigField("Max Peers", maxPeersField),
            ConfigurationUIUtils.createConfigField("P2P Port", portField)
        );
        
        // Sync settings
        VBox syncCard = ConfigurationUIUtils.createConfigCard(
            "Synchronization Settings",
            "Configure blockchain synchronization",
            null
        );
        
        VBox pingSlider = ConfigurationUIUtils.createSliderWithLabel(
            "Ping Interval", 5000, 60000,
            controller.getNetworkConfig().getPingInterval(), "ms"
        );
        
        VBox syncSlider = ConfigurationUIUtils.createSliderWithLabel(
            "Sync Interval", 10000, 120000,
            controller.getNetworkConfig().getSyncInterval(), "ms"
        );
        
        syncCard.getChildren().addAll(pingSlider, syncSlider);
        
        content.getChildren().addAll(header, networkCard, syncCard);
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #16213e; -fx-background-color: #16213e;");
        
        return scrollPane;
    }
    
    /**
     * Create Storage panel
     */
    private ScrollPane createStoragePanel() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #16213e;");
        
        Label header = ConfigurationUIUtils.createSectionHeader("Storage Configuration");
        
        // Storage settings
        VBox storageCard = ConfigurationUIUtils.createConfigCard(
            "Storage Settings",
            "Configure data storage parameters",
            null
        );
        
        ComboBox<String> storageTypeCombo = ConfigurationUIUtils.createStyledComboBox();
        storageTypeCombo.setItems(FXCollections.observableArrayList(
            "leveldb", "rocksdb", "sqlite", "memory"
        ));
        storageTypeCombo.setValue(controller.getStorageConfig().getStorageType());
        
        TextField dataDirField = ConfigurationUIUtils.createStyledTextField("Data directory");
        dataDirField.setText(controller.getStorageConfig().getDataDir());
        
        Button browseBtn = ConfigurationUIUtils.createSecondaryButton("Browse...");
        browseBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Data Directory");
            File dir = chooser.showDialog(mainContainer.getScene().getWindow());
            if (dir != null) {
                dataDirField.setText(dir.getAbsolutePath());
            }
        });
        
        HBox dataDirBox = new HBox(10);
        dataDirBox.getChildren().addAll(dataDirField, browseBtn);
        
        storageCard.getChildren().addAll(
            ConfigurationUIUtils.createConfigField("Storage Type", storageTypeCombo),
            ConfigurationUIUtils.createConfigField("Data Directory", dataDirField),
            dataDirBox
        );
        
        // Cache settings
        VBox cacheCard = ConfigurationUIUtils.createConfigCard(
            "Cache Settings",
            "Configure caching parameters",
            null
        );
        
        VBox cacheSlider = ConfigurationUIUtils.createSliderWithLabel(
            "Cache Size", 64, 2048,
            controller.getStorageConfig().getCacheSize(), "MB"
        );
        
        VBox writeBufferSlider = ConfigurationUIUtils.createSliderWithLabel(
            "Write Buffer", 16, 256,
            controller.getStorageConfig().getWriteBuffer(), "MB"
        );
        
        CheckBox compressionCheck = ConfigurationUIUtils.createStyledCheckBox("Enable compression");
        compressionCheck.setSelected(controller.getStorageConfig().isCompression());
        
        cacheCard.getChildren().addAll(cacheSlider, writeBufferSlider, compressionCheck);
        
        content.getChildren().addAll(header, storageCard, cacheCard);
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #16213e; -fx-background-color: #16213e;");
        
        return scrollPane;
    }
    
    /**
     * Create Security panel
     */
    private ScrollPane createSecurityPanel() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #16213e;");
        
        Label header = ConfigurationUIUtils.createSectionHeader("Security Configuration");
        
        // Security settings card
        VBox securityCard = ConfigurationUIUtils.createConfigCard(
            "Security Settings",
            "Configure blockchain security parameters",
            null
        );
        
        CheckBox tlsEnabled = ConfigurationUIUtils.createStyledCheckBox("Enable TLS/SSL");
        CheckBox authRequired = ConfigurationUIUtils.createStyledCheckBox("Require authentication");
        CheckBox encryptWallet = ConfigurationUIUtils.createStyledCheckBox("Encrypt wallet storage");
        
        securityCard.getChildren().addAll(tlsEnabled, authRequired, encryptWallet);
        
        // API security
        VBox apiCard = ConfigurationUIUtils.createConfigCard(
            "API Security",
            "Configure API access and security",
            null
        );
        
        CheckBox corsEnabled = ConfigurationUIUtils.createStyledCheckBox("Enable CORS");
        CheckBox rateLimiting = ConfigurationUIUtils.createStyledCheckBox("Enable rate limiting");
        
        TextField allowedOrigins = ConfigurationUIUtils.createStyledTextField("Allowed origins (comma-separated)");
        TextField rateLimit = ConfigurationUIUtils.createNumberField("Rate limit (req/min)", false);
        
        apiCard.getChildren().addAll(
            corsEnabled,
            ConfigurationUIUtils.createConfigField("Allowed Origins", allowedOrigins),
            rateLimiting,
            ConfigurationUIUtils.createConfigField("Rate Limit", rateLimit)
        );
        
        content.getChildren().addAll(header, securityCard, apiCard);
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #16213e; -fx-background-color: #16213e;");
        
        return scrollPane;
    }
    
    /**
     * Create Advanced panel
     */
    private ScrollPane createAdvancedPanel() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #16213e;");
        
        Label header = ConfigurationUIUtils.createSectionHeader("Advanced Configuration");
        
        // Performance tuning
        VBox performanceCard = ConfigurationUIUtils.createConfigCard(
            "Performance Tuning",
            "Advanced performance optimization settings",
            null
        );
        
        VBox threadPoolSlider = ConfigurationUIUtils.createSliderWithLabel(
            "Thread Pool Size", 2, 32,
            Runtime.getRuntime().availableProcessors(), "threads"
        );
        
        VBox memorySlider = ConfigurationUIUtils.createSliderWithLabel(
            "Max Memory Allocation", 512, 8192,
            2048, "MB"
        );
        
        performanceCard.getChildren().addAll(threadPoolSlider, memorySlider);
        
        // Debug settings
        VBox debugCard = ConfigurationUIUtils.createConfigCard(
            "Debug Settings",
            "Configure debug and logging options",
            null
        );
        
        ComboBox<String> logLevelCombo = ConfigurationUIUtils.createStyledComboBox();
        logLevelCombo.setItems(FXCollections.observableArrayList(
            "ERROR", "WARNING", "INFO", "DEBUG", "TRACE"
        ));
        logLevelCombo.setValue("INFO");
        
        CheckBox enableProfiling = ConfigurationUIUtils.createStyledCheckBox("Enable CPU profiling");
        CheckBox enableMetrics = ConfigurationUIUtils.createStyledCheckBox("Enable metrics collection");
        
        debugCard.getChildren().addAll(
            ConfigurationUIUtils.createConfigField("Log Level", logLevelCombo),
            enableProfiling,
            enableMetrics
        );
        
        // Import/Export
        VBox importExportCard = ConfigurationUIUtils.createConfigCard(
            "Import/Export",
            "Import or export configuration files",
            null
        );
        
        Button exportBtn = ConfigurationUIUtils.createPrimaryButton("Export Configuration");
        exportBtn.setOnAction(e -> exportConfiguration());
        
        Button importBtn = ConfigurationUIUtils.createSecondaryButton("Import Configuration");
        importBtn.setOnAction(e -> importConfiguration());
        
        HBox importExportButtons = new HBox(15);
        importExportButtons.getChildren().addAll(exportBtn, importBtn);
        importExportCard.getChildren().add(importExportButtons);
        
        content.getChildren().addAll(header, performanceCard, debugCard, importExportCard);
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #16213e; -fx-background-color: #16213e;");
        
        return scrollPane;
    }
    
    /**
     * Create action section
     */
    private HBox createActionSection() {
        HBox section = new HBox(15);
        section.setAlignment(Pos.CENTER);
        section.setPadding(new Insets(20, 0, 20, 0));
        
        saveButton = ConfigurationUIUtils.createPrimaryButton("Save Configuration");
        saveButton.setOnAction(e -> saveConfiguration());
        saveButton.disableProperty().bind(
            Bindings.not(controller.hasUnsavedChangesProperty())
        );
        
        Button applyButton = ConfigurationUIUtils.createSecondaryButton("Apply Changes");
        applyButton.setOnAction(e -> applyChanges());
        
        resetButton = ConfigurationUIUtils.createWarningButton("Reset to Defaults");
        resetButton.setOnAction(e -> resetConfiguration());
        
        Button validateButton = ConfigurationUIUtils.createSecondaryButton("Validate");
        validateButton.setOnAction(e -> validateConfiguration());
        
        section.getChildren().addAll(saveButton, applyButton, validateButton, resetButton);
        
        return section;
    }
    
    /**
     * Create status section
     */
    private HBox createStatusSection() {
        HBox section = new HBox(20);
        section.setAlignment(Pos.CENTER_LEFT);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: #22334e; -fx-background-radius: 8;");
        
        statusLabel = new Label("Configuration loaded successfully");
        statusLabel.setTextFill(Color.web("#00ff88"));
        
        Label validLabel = new Label();
        validLabel.textProperty().bind(Bindings.when(controller.configurationValidProperty())
            .then("✓ Valid")
            .otherwise("✗ Invalid")
        );
        validLabel.textFillProperty().bind(Bindings.when(controller.configurationValidProperty())
            .then(Color.web("#00ff88"))
            .otherwise(Color.web("#ff6b6b"))
        );
        
        Label changesLabel = new Label();
        changesLabel.textProperty().bind(Bindings.when(controller.hasUnsavedChangesProperty())
            .then("• Unsaved changes")
            .otherwise("• No changes")
        );
        changesLabel.setTextFill(Color.web("#ffa500"));
        changesLabel.visibleProperty().bind(controller.hasUnsavedChangesProperty());
        
        section.getChildren().addAll(statusLabel, validLabel, changesLabel);
        
        return section;
    }
    
    /**
     * Setup event listeners
     */
    private void setupEventListeners() {
        controller.addEventListener(new ConfigurationController.ConfigurationEventListener() {
            @Override
            public void onConfigurationChanged(ConfigurationController.ConfigurationChangeEvent event) {
                updateStatus("Configuration changed: " + event.property);
            }

            @Override
            public void onProfileLoaded(String profileName, boolean success) {
                if (success) {
                    updateStatus("Profile loaded: " + profileName);
                    loadCurrentConfiguration();
                } else {
                    updateStatus("Failed to load profile: " + profileName, true);
                }
            }

            @Override
            public void onConfigurationSaved(String filepath, boolean success) {
                if (success) {
                    updateStatus("Configuration saved to: " + filepath);
                } else {
                    updateStatus("Failed to save configuration to: " + filepath, true);
                }
            }

            @Override
            public void onValidationError(List<ConfigurationController.ConfigurationError> errors) {
                StringBuilder errorMsg = new StringBuilder("Validation errors: ");
                for (ConfigurationController.ConfigurationError error : errors) {
                    errorMsg.append(error.component).append(".").append(error.property).append(": ")
                           .append(error.message).append("; ");
                }
                updateStatus(errorMsg.toString(), true);
            }

            @Override
            public void onConfigurationRollback(String snapshotId) {
                updateStatus("Configuration rolled back to snapshot: " + snapshotId);
            }
        });
    }
    
    /**
     * Load current configuration into UI
     */
    private void loadCurrentConfiguration() {
        // This would populate all the UI fields with current config values
        LOGGER.info("Loading current configuration into UI");
        pendingChanges.clear();
    }
    
    /**
     * Apply quick setup
     */
    private void applyQuickSetup(String networkType, ConsensusType consensusType, String performance) {
        Map<String, Object> changes = new HashMap<>();
        
        // Apply network type presets
        switch (networkType) {
            case "mainnet":
                changes.put("network.networkId", "1");
                changes.put("network.maxPeers", 50);
                break;
            case "testnet":
                changes.put("network.networkId", "3");
                changes.put("network.maxPeers", 25);
                break;
            case "development":
                changes.put("network.networkId", "1337");
                changes.put("network.maxPeers", 10);
                break;
        }
        
        // Apply consensus type
        changes.put("consensus.defaultConsensusType", consensusType);
        
        // Apply performance presets
        switch (performance) {
            case "low":
                changes.put("storage.cacheSize", 128);
                changes.put("network.maxPeers", 10);
                break;
            case "high":
                changes.put("storage.cacheSize", 2048);
                changes.put("network.maxPeers", 100);
                break;
            default:
                changes.put("storage.cacheSize", 512);
                changes.put("network.maxPeers", 50);
        }
        
        controller.applyConfiguration(changes).thenAccept(success -> {
            javafx.application.Platform.runLater(() -> {
                if (success) {
                    updateStatus("Quick setup applied successfully");
                    ConfigurationUIUtils.showInfoDialog(
                        "Quick Setup",
                        "Configuration Applied",
                        "Quick setup has been applied successfully. Remember to save your configuration."
                    );
                } else {
                    updateStatus("Failed to apply quick setup", true);
                }
            });
        });
    }
    
    /**
     * Load selected profile
     */
    private void loadSelectedProfile() {
        String selectedProfile = profileSelector.getValue();
        if (selectedProfile != null) {
            controller.loadProfile(selectedProfile).thenAccept(success -> {
                javafx.application.Platform.runLater(() -> {
                    if (success) {
                        updateStatus("Profile loaded: " + selectedProfile);
                    } else {
                        updateStatus("Failed to load profile: " + selectedProfile, true);
                    }
                });
            });
        }
    }
    
    /**
     * Save as profile
     */
    private void saveAsProfile() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Save as Profile");
        dialog.setHeaderText("Save current configuration as a new profile");
        dialog.setContentText("Profile name:");
        
        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                ConfigurationProfile profile = manager.createProfile(
                    name, "User-created profile",
                    controller.getCurrentConfiguration()
                );
                
                if (profile != null) {
                    controller.getAvailableProfiles().add(name);
                    profileSelector.setValue(name);
                    updateStatus("Profile saved: " + name);
                }
            }
        });
    }
    
    /**
     * Delete profile
     */
    private void deleteProfile() {
        String selectedProfile = profileSelector.getValue();
        if (selectedProfile != null && !selectedProfile.equals("Default")) {
            if (ConfigurationUIUtils.showConfirmDialog(
                "Delete Profile",
                "Delete profile: " + selectedProfile,
                "Are you sure you want to delete this profile?")) {
                
                if (manager.deleteProfile(selectedProfile)) {
                    controller.getAvailableProfiles().remove(selectedProfile);
                    profileSelector.setValue("Default");
                    updateStatus("Profile deleted: " + selectedProfile);
                }
            }
        } else {
            ConfigurationUIUtils.showWarningDialog(
                "Cannot Delete",
                "System Profile",
                "System profiles cannot be deleted."
            );
        }
    }
    
    /**
     * Save configuration
     */
    private void saveConfiguration() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Configuration");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Properties Files", "*.properties"),
            new FileChooser.ExtensionFilter("JSON Files", "*.json"),
            new FileChooser.ExtensionFilter("XML Files", "*.xml")
        );
        
        File file = chooser.showSaveDialog(mainContainer.getScene().getWindow());
        if (file != null) {
            String filepath = file.getAbsolutePath();
            controller.saveConfiguration(filepath).thenAccept(success -> {
                javafx.application.Platform.runLater(() -> {
                    if (success) {
                        updateStatus("Configuration saved to: " + file.getName());
                    } else {
                        updateStatus("Failed to save configuration", true);
                    }
                });
            });
        }
    }
    
    /**
     * Apply changes
     */
    private void applyChanges() {
        if (!pendingChanges.isEmpty()) {
            controller.applyConfiguration(pendingChanges).thenAccept(success -> {
                javafx.application.Platform.runLater(() -> {
                    if (success) {
                        updateStatus("Changes applied successfully");
                        pendingChanges.clear();

                        // Notify dashboard to update
                        if (dashboardUpdateCallback != null) {
                            dashboardUpdateCallback.run();
                        }
                    } else {
                        updateStatus("Failed to apply changes", true);
                    }
                });
            });
        }
    }
    
    /**
     * Reset configuration
     */
    private void resetConfiguration() {
        if (ConfigurationUIUtils.showConfirmDialog(
            "Reset Configuration",
            "Reset to Defaults",
            "Are you sure you want to reset all settings to defaults?")) {
            
            controller.resetToDefaults();
            loadCurrentConfiguration();
            updateStatus("Configuration reset to defaults");
        }
    }
    
    /**
     * Validate configuration
     */
    private void validateConfiguration() {
        if (controller.validateConfiguration()) {
            updateStatus("Configuration is valid");
            ConfigurationUIUtils.showInfoDialog(
                "Validation",
                "Configuration Valid",
                "All configuration settings are valid."
            );
        } else {
            updateStatus("Configuration validation failed", true);
        }
    }
    
    /**
     * Export configuration
     */
    private void exportConfiguration() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Configuration");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("JSON Files", "*.json"),
            new FileChooser.ExtensionFilter("XML Files", "*.xml"),
            new FileChooser.ExtensionFilter("Properties Files", "*.properties")
        );
        
        File file = chooser.showSaveDialog(mainContainer.getScene().getWindow());
        if (file != null) {
            String format = "properties";
            if (file.getName().endsWith(".json")) format = "json";
            else if (file.getName().endsWith(".xml")) format = "xml";

            String finalFormat = format;
            controller.exportConfiguration(file.getAbsolutePath(), format).thenAccept(success -> {
                javafx.application.Platform.runLater(() -> {
                    if (success) {
                        updateStatus("Configuration exported to: " + file.getName());
                    } else {
                        updateStatus("Failed to export configuration", true);
                    }
                });
            });
        }
    }
    
    /**
     * Import configuration
     */
    private void importConfiguration() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Configuration");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("All Configs", "*.json", "*.xml", "*.properties"),
            new FileChooser.ExtensionFilter("JSON Files", "*.json"),
            new FileChooser.ExtensionFilter("XML Files", "*.xml"),
            new FileChooser.ExtensionFilter("Properties Files", "*.properties")
        );
        
        File file = chooser.showOpenDialog(mainContainer.getScene().getWindow());
        if (file != null) {
            String format = "properties";
            if (file.getName().endsWith(".json")) format = "json";
            else if (file.getName().endsWith(".xml")) format = "xml";

            String finalFormat = format;
            controller.importConfiguration(file.getAbsolutePath(), format).thenAccept(success -> {
                javafx.application.Platform.runLater(() -> {
                    if (success) {
                        loadCurrentConfiguration();
                        updateStatus("Configuration imported from: " + file.getName());
                    } else {
                        updateStatus("Failed to import configuration", true);
                    }
                });
            });
        }
    }
    
    /**
     * Update status message
     */
    private void updateStatus(String message) {
        updateStatus(message, false);
    }
    
    private void updateStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setTextFill(Color.web(isError ? "#ff6b6b" : "#00ff88"));
        LOGGER.info("Status: " + message);
    }
    
    // Setters for callbacks
    public void setDashboardUpdateCallback(Runnable callback) {
        this.dashboardUpdateCallback = callback;
    }
    
    public void setNavigationCallback(Runnable callback) {
        this.navigationCallback = callback;
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        if (manager != null) {
            manager.shutdown();
        }
        LOGGER.info("ConfigurationView cleanup complete");
    }
}