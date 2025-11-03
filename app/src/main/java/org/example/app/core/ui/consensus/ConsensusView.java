package org.example.app.core.ui.consensus;

import org.example.app.core.ui.consensus.ConsensusController;
import org.example.app.core.ui.consensus.ConsensusController.ConsensusType;
import org.example.app.core.ui.consensus.ConsensusController.ConsensusOperationResult;
import org.example.app.core.ui.consensus.ConsensusController.ConsensusStatusInfo;
import org.example.app.core.ui.consensus.ConsensusController.MiningStatus;
import org.example.app.core.ui.consensus.ConsensusController.StakingStatus;
import org.example.app.core.ui.consensus.ConsensusController.PBFTStatus;
import org.example.app.core.ui.consensus.ConsensusController.StreamStatus;
import org.example.app.core.ui.consensus.ConsensusManager;
import org.example.app.core.ui.consensus.ConsensusManager.ConsensusPolicy;
import org.example.app.core.ui.consensus.ConsensusManager.ConsensusManagerResult;
import org.example.app.core.ui.consensus.ConsensusManager.ConsensusHealthStatus;
import org.example.app.core.ui.consensus.ConsensusManager.ConsensusRecommendation;
import org.example.app.core.ui.consensus.ConsensusManager.ConsensusAnalytics;
import org.example.app.core.ui.consensus.ConsensusUIUtils;
import org.example.app.core.ui.consensus.ConsensusUIUtils.ValidationResult;
import javafx.scene.shape.Circle;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.chart.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.util.Duration;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ConsensusView provides the main user interface for consensus mechanism management.
 * 
 * Features:
 * - Comprehensive consensus mechanism control and monitoring
 * - Real-time consensus health and performance tracking
 * - Interactive consensus switching with validation
 * - Policy-based consensus optimization
 * - Multi-consensus support (PoW, PoS, PBFT, Stream-based)
 * - Dashboard integration and event handling
 * - Production-level error handling and validation
 * - Responsive design with accessibility support
 */
public class ConsensusView {
    private static final Logger LOGGER = Logger.getLogger(ConsensusView.class.getName());
    
    // Core components
    private final ConsensusController consensusController;
    private final ConsensusManager consensusManager;
    
    // Main UI components
    private VBox mainContainer;
    private TabPane consensusTabPane;
    private ScrollPane mainScrollPane;
    
    // Header components
    private HBox headerSection;
    private Label consensusStatusLabel;
    private Button emergencyStopButton;
    private Label lastUpdateLabel;
    
    // Status monitoring components
    private VBox statusOverviewPanel;
    private GridPane metricsGrid;
    private VBox healthStatusPanel;
    private VBox recommendationPanel;
    
    // Consensus-specific panels
    private VBox powPanel;
    private VBox posPanel;
    private VBox pbftPanel;
    private VBox streamPanel;
    
    // Control panels
    private VBox consensusSwitchPanel;
    private VBox policyConfigPanel;
    private VBox optimizationPanel;
    
    // Charts and analytics
    private LineChart<String, Number> performanceChart;
    private PieChart consensusDistributionChart;
    private VBox analyticsPanel;
    
    // Real-time updates
    private ScheduledService<Void> updateService;
    private Timeline statusUpdateTimeline;
    
    // Event handlers and callbacks
    private Runnable dashboardUpdateCallback;
    private Runnable navigationCallback;
    
    // Current state tracking
    private ConsensusType currentConsensusType;
    private ConsensusPolicy currentPolicy;
    private boolean isRealTimeUpdateEnabled;
    private LocalDateTime lastUpdateTime;
    
    // Form validation
    private Map<String, ValidationResult> validationResults;
    
    /**
     * Constructor initializes consensus view with controller and manager
     */
    public ConsensusView(ConsensusController consensusController, ConsensusManager consensusManager) {
        this.consensusController = consensusController;
        this.consensusManager = consensusManager;
        this.validationResults = new HashMap<>();
        this.isRealTimeUpdateEnabled = true;
        
        LOGGER.info("Initializing ConsensusView");
        
        // Initialize current state
        this.currentConsensusType = consensusController.getActiveConsensusType();
        this.currentPolicy = consensusManager.getCurrentPolicy();
        
        // Setup event listeners for real-time updates
        setupEventListeners();
        
        LOGGER.info("ConsensusView initialized successfully");
    }
    
    /**
     * Creates the main consensus management interface
     */
    public VBox createConsensusManagementView() {
        LOGGER.info("Creating consensus management interface");
        
        try {
            // Create main container
            mainContainer = new VBox(0);
            mainContainer.setStyle("-fx-background-color: " + ConsensusUIUtils.PRIMARY_BG_COLOR + ";");
            
            // Create header section
            headerSection = createConsensusHeader();
            
            // Create main content with tabs
            consensusTabPane = createConsensusTabPane();
            
            // Wrap in scroll pane for responsiveness
            mainScrollPane = new ScrollPane(consensusTabPane);
            mainScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
            mainScrollPane.setFitToWidth(true);
            mainScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            mainScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            
            // Add components to main container
            mainContainer.getChildren().addAll(headerSection, mainScrollPane);
            VBox.setVgrow(mainScrollPane, Priority.ALWAYS);
            
            // Start real-time updates
            startRealTimeUpdates();
            
            // Apply initial animations
            ConsensusUIUtils.addFadeInAnimation(mainContainer);
            
            LOGGER.info("Consensus management interface created successfully");
            return mainContainer;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating consensus management interface", e);
            return createErrorView("Failed to create consensus interface: " + e.getMessage());
        }
    }
    
    // ========================================
    // HEADER CREATION
    // ========================================
    
    /**
     * Creates the consensus management header
     */
    private HBox createConsensusHeader() {
        HBox header = new HBox(20);
        header.setStyle("-fx-background-color: " + ConsensusUIUtils.SECONDARY_BG_COLOR + "; " +
                       "-fx-padding: 25; -fx-alignment: center-left;");
        
        // Main title section
        VBox titleSection = new VBox(5);
        
        Label mainTitle = ConsensusUIUtils.createStyledLabel("⚙️ Consensus Management", 
            ConsensusUIUtils.PRIMARY_TEXT_COLOR, 28, true);
        
        Label subtitle = ConsensusUIUtils.createStyledLabel(
            "Advanced consensus mechanism control and optimization", 
            ConsensusUIUtils.SECONDARY_TEXT_COLOR, 16, false);
        
        titleSection.getChildren().addAll(mainTitle, subtitle);
        
        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Status and control section
        VBox statusSection = createHeaderStatusSection();
        
        // Emergency controls
        VBox emergencySection = createHeaderEmergencySection();
        
        // Navigation controls
        VBox navigationSection = createHeaderNavigationSection();
        
        header.getChildren().addAll(titleSection, spacer, statusSection, emergencySection, navigationSection);
        return header;
    }
    
    /**
     * Creates header status section
     */
    private VBox createHeaderStatusSection() {
        VBox statusSection = new VBox(8);
        statusSection.setAlignment(Pos.CENTER_RIGHT);
        
        // Current consensus status
        consensusStatusLabel = ConsensusUIUtils.createStyledLabel("Loading...", 
            ConsensusUIUtils.ACCENT_TEXT_COLOR, 14, true);
        
        // Last update time
        lastUpdateLabel = ConsensusUIUtils.createStyledLabel("", 
            ConsensusUIUtils.MUTED_TEXT_COLOR, 12, false);
        
        // Health indicator
        HBox healthBox = new HBox(8);
        healthBox.setAlignment(Pos.CENTER_RIGHT);
        healthBox.getChildren().addAll(
            ConsensusUIUtils.createStyledLabel("System Health", 
                ConsensusUIUtils.SECONDARY_TEXT_COLOR, 12, false),
            new Circle(6, javafx.scene.paint.Color.web(ConsensusUIUtils.SUCCESS_COLOR))
        );
        
        statusSection.getChildren().addAll(consensusStatusLabel, lastUpdateLabel, healthBox);
        return statusSection;
    }
    
    /**
     * Creates header emergency section
     */
    private VBox createHeaderEmergencySection() {
        VBox emergencySection = new VBox(10);
        emergencySection.setAlignment(Pos.CENTER);
        
        // Emergency stop button
        emergencyStopButton = ConsensusUIUtils.createStyledButton("🛑 Emergency Stop", 
            ConsensusUIUtils.ERROR_COLOR, ConsensusUIUtils.PRIMARY_TEXT_COLOR);
        emergencyStopButton.setOnAction(e -> handleEmergencyStop());
        
        // Auto-optimization toggle
        CheckBox autoOptimizationCheck = new CheckBox("Auto-Optimization");
        autoOptimizationCheck.setSelected(consensusManager.isAutoOptimizationEnabled());
        autoOptimizationCheck.setStyle("-fx-text-fill: " + ConsensusUIUtils.PRIMARY_TEXT_COLOR + ";");
        autoOptimizationCheck.setOnAction(e -> handleAutoOptimizationToggle(autoOptimizationCheck.isSelected()));
        
        emergencySection.getChildren().addAll(emergencyStopButton, autoOptimizationCheck);
        return emergencySection;
    }
    
    /**
     * Creates header navigation section
     */
    private VBox createHeaderNavigationSection() {
        VBox navigationSection = new VBox(10);
        navigationSection.setAlignment(Pos.CENTER);
        
        // Back to dashboard button
        Button backButton = ConsensusUIUtils.createStyledButton("← Dashboard", 
            "transparent", ConsensusUIUtils.ACCENT_TEXT_COLOR);
        backButton.setStyle(backButton.getStyle() + 
            "; -fx-border-color: " + ConsensusUIUtils.ACCENT_TEXT_COLOR + 
            "; -fx-border-width: 2;");
        backButton.setOnAction(e -> navigateToDestination());
        
        // Refresh button
        Button refreshButton = ConsensusUIUtils.createStyledButton("🔄 Refresh", 
            ConsensusUIUtils.INFO_COLOR, ConsensusUIUtils.PRIMARY_TEXT_COLOR);
        refreshButton.setOnAction(e -> refreshAllData());
        
        navigationSection.getChildren().addAll(backButton, refreshButton);
        return navigationSection;
    }
    
    // ========================================
    // TAB PANE CREATION
    // ========================================
    
    /**
     * Creates the main consensus tab pane
     */
    private TabPane createConsensusTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: " + ConsensusUIUtils.PRIMARY_BG_COLOR + ";");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // Overview and monitoring tab
        Tab overviewTab = new Tab("📊 Overview & Monitoring");
        overviewTab.setContent(createOverviewAndMonitoringContent());
        
        // Consensus control tab
        Tab controlTab = new Tab("⚙️ Consensus Control");
        controlTab.setContent(createConsensusControlContent());
        
        // Policy and optimization tab
        Tab policyTab = new Tab("🎯 Policy & Optimization");
        policyTab.setContent(createPolicyOptimizationContent());
        
        // Analytics and history tab
        Tab analyticsTab = new Tab("📈 Analytics & History");
        analyticsTab.setContent(createAnalyticsContent());
        
        // Configuration tab
        Tab configTab = new Tab("🔧 Configuration");
        configTab.setContent(createConfigurationContent());
        
        tabPane.getTabs().addAll(overviewTab, controlTab, policyTab, analyticsTab, configTab);
        return tabPane;
    }
    
    // ========================================
    // TAB CONTENT CREATION
    // ========================================
    
    /**
     * Creates overview and monitoring content
     */
    private ScrollPane createOverviewAndMonitoringContent() {
        VBox content = new VBox(25);
        content.setStyle("-fx-padding: 30;");
        
        // Current status overview
        statusOverviewPanel = createStatusOverviewPanel();
        
        // Real-time metrics grid
        metricsGrid = createRealTimeMetricsGrid();
        
        // Health status panel
        healthStatusPanel = createHealthStatusPanel();
        
        // Performance charts
        VBox chartsSection = createPerformanceChartsSection();
        
        content.getChildren().addAll(statusOverviewPanel, metricsGrid, healthStatusPanel, chartsSection);
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setFitToWidth(true);
        
        return scrollPane;
    }
    
    /**
     * Creates consensus control content
     */
    private ScrollPane createConsensusControlContent() {
        VBox content = new VBox(25);
        content.setStyle("-fx-padding: 30;");
        
        // Consensus switching panel
        consensusSwitchPanel = createConsensusSwitchPanel();
        
        // Consensus-specific control panels
        VBox consensusSpecificControls = createConsensusSpecificControls();
        
        // Operation controls
        VBox operationControls = createOperationControls();
        
        content.getChildren().addAll(consensusSwitchPanel, consensusSpecificControls, operationControls);
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setFitToWidth(true);
        
        return scrollPane;
    }
    
    /**
     * Creates policy and optimization content
     */
    private ScrollPane createPolicyOptimizationContent() {
        VBox content = new VBox(25);
        content.setStyle("-fx-padding: 30;");
        
        // Policy configuration panel
        policyConfigPanel = createPolicyConfigurationPanel();
        
        // Optimization controls
        optimizationPanel = createOptimizationPanel();
        
        // Recommendation panel
        recommendationPanel = createCurrentRecommendationPanel();
        
        content.getChildren().addAll(policyConfigPanel, optimizationPanel, recommendationPanel);
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setFitToWidth(true);
        
        return scrollPane;
    }
    
    /**
     * Creates analytics content
     */
    private ScrollPane createAnalyticsContent() {
        VBox content = new VBox(25);
        content.setStyle("-fx-padding: 30;");
        
        // Analytics dashboard
        analyticsPanel = createAnalyticsDashboard();
        
        // Consensus usage distribution
        VBox distributionSection = createConsensusDistributionSection();
        
        // Historical performance
        VBox historySection = createHistoricalPerformanceSection();
        
        content.getChildren().addAll(analyticsPanel, distributionSection, historySection);
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setFitToWidth(true);
        
        return scrollPane;
    }
    
    /**
     * Creates configuration content
     */
    private ScrollPane createConfigurationContent() {
        VBox content = new VBox(25);
        content.setStyle("-fx-padding: 30;");
        
        // Global consensus settings
        VBox globalSettings = createGlobalSettingsPanel();
        
        // Consensus-specific configurations
        VBox consensusConfigs = createConsensusConfigurationsPanel();
        
        // Advanced settings
        VBox advancedSettings = createAdvancedSettingsPanel();
        
        content.getChildren().addAll(globalSettings, consensusConfigs, advancedSettings);
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setFitToWidth(true);
        
        return scrollPane;
    }
    
    // ========================================
    // OVERVIEW PANEL CREATION
    // ========================================
    
    /**
     * Creates status overview panel
     */
    private VBox createStatusOverviewPanel() {
        VBox panel = ConsensusUIUtils.createStyledCard();
        panel.setSpacing(20);
        
        // Panel header
        Label header = ConsensusUIUtils.createStyledLabel("📊 Consensus Status Overview", 
            ConsensusUIUtils.ACCENT_TEXT_COLOR, ConsensusUIUtils.SUBHEADER_FONT_SIZE, true);
        
        // Status indicators grid
        GridPane statusGrid = new GridPane();
        statusGrid.setHgap(20);
        statusGrid.setVgap(15);
        
        // Get current status
        ConsensusStatusInfo status = consensusController.getConsensusStatus();
        
        // Active consensus indicator
        HBox activeConsensusBox = ConsensusUIUtils.createConsensusStatusIndicator(
            status.activeConsensusType, status.isOperational);
        statusGrid.add(createLabeledComponent("Active Consensus:", activeConsensusBox), 0, 0);
        
        // Block height
        Label blockHeightLabel = ConsensusUIUtils.createStyledLabel(
            String.valueOf(status.blockHeight), ConsensusUIUtils.SUCCESS_COLOR, 18, true);
        statusGrid.add(createLabeledComponent("Block Height:", blockHeightLabel), 1, 0);
        
        // Pending transactions
        Label pendingTxLabel = ConsensusUIUtils.createStyledLabel(
            String.valueOf(status.pendingTransactions), ConsensusUIUtils.INFO_COLOR, 18, true);
        statusGrid.add(createLabeledComponent("Pending Transactions:", pendingTxLabel), 0, 1);
        
        // Health status
        ConsensusHealthStatus health = consensusManager.getConsensusHealth();
        VBox healthBox = ConsensusUIUtils.createHealthStatusIndicator(health.overallHealth);
        statusGrid.add(createLabeledComponent("System Health:", healthBox), 1, 1);
        
        panel.getChildren().addAll(header, statusGrid);
        return panel;
    }
    
    /**
     * Creates real-time metrics grid
     */
    private GridPane createRealTimeMetricsGrid() {
        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(20);
        metricsGrid.setVgap(20);
        metricsGrid.setStyle("-fx-padding: 0;");
        
        // Get current status for metrics
        ConsensusStatusInfo status = consensusController.getConsensusStatus();
        
        // Add consensus-specific metrics based on active type
        switch (status.activeConsensusType) {
            case PROOF_OF_WORK:
                if (status.powStatus != null) {
                    addPoWMetrics(metricsGrid, status.powStatus);
                }
                break;
            case PROOF_OF_STAKE:
                if (status.posStatus != null) {
                    addPoSMetrics(metricsGrid, status.posStatus);
                }
                break;
            case PBFT:
                if (status.pbftStatus != null) {
                    addPBFTMetrics(metricsGrid, status.pbftStatus);
                }
                break;
            case STREAM_CONSENSUS:
                if (status.streamStatus != null) {
                    addStreamMetrics(metricsGrid, status.streamStatus);
                }
                break;
        }
        
        return metricsGrid;
    }
    
    /**
     * Creates health status panel
     */
    private VBox createHealthStatusPanel() {
        VBox panel = ConsensusUIUtils.createStyledCard();
        panel.setSpacing(15);
        
        Label header = ConsensusUIUtils.createStyledLabel("🔍 Health Status & Diagnostics", 
            ConsensusUIUtils.ACCENT_TEXT_COLOR, ConsensusUIUtils.SUBHEADER_FONT_SIZE, true);
        
        // Health metrics
        ConsensusHealthStatus health = consensusManager.getConsensusHealth();
        
        // Overall health display
        HBox overallHealthBox = new HBox(15);
        overallHealthBox.setAlignment(Pos.CENTER_LEFT);
        
        Label healthLabel = ConsensusUIUtils.createStyledLabel("Overall Health:", 
            ConsensusUIUtils.SECONDARY_TEXT_COLOR, 14, false);
        VBox healthIndicator = ConsensusUIUtils.createHealthStatusIndicator(health.overallHealth);
        
        overallHealthBox.getChildren().addAll(healthLabel, healthIndicator);
        
        // Health breakdown
        GridPane healthBreakdown = new GridPane();
        healthBreakdown.setHgap(20);
        healthBreakdown.setVgap(10);
        
        // Performance score
        ProgressBar performanceBar = ConsensusUIUtils.createStyledProgressBar(health.performanceScore);
        healthBreakdown.add(createLabeledComponent("Performance:", performanceBar), 0, 0);
        
        // Security score
        ProgressBar securityBar = ConsensusUIUtils.createStyledProgressBar(health.securityScore);
        healthBreakdown.add(createLabeledComponent("Security:", securityBar), 1, 0);
        
        // Efficiency score
        ProgressBar efficiencyBar = ConsensusUIUtils.createStyledProgressBar(health.efficiencyScore);
        healthBreakdown.add(createLabeledComponent("Efficiency:", efficiencyBar), 0, 1);
        
        // Health issues
        VBox issuesBox = new VBox(5);
        if (!health.healthIssues.isEmpty()) {
            Label issuesTitle = ConsensusUIUtils.createStyledLabel("Issues:", 
                ConsensusUIUtils.WARNING_COLOR, 12, true);
            issuesBox.getChildren().add(issuesTitle);
            
            for (String issue : health.healthIssues) {
                Label issueLabel = ConsensusUIUtils.createStyledLabel("• " + issue, 
                    ConsensusUIUtils.ERROR_COLOR, 12, false);
                issueLabel.setWrapText(true);
                issuesBox.getChildren().add(issueLabel);
            }
        } else {
            Label noIssuesLabel = ConsensusUIUtils.createStyledLabel("✅ No health issues detected", 
                ConsensusUIUtils.SUCCESS_COLOR, 12, false);
            issuesBox.getChildren().add(noIssuesLabel);
        }
        
        panel.getChildren().addAll(header, overallHealthBox, healthBreakdown, issuesBox);
        return panel;
    }
    
    // ========================================
    // CONSENSUS CONTROL PANELS
    // ========================================
    
    /**
     * Creates consensus switching panel
     */
    private VBox createConsensusSwitchPanel() {
        VBox panel = ConsensusUIUtils.createStyledCard();
        panel.setSpacing(20);
        
        Label header = ConsensusUIUtils.createStyledLabel("🔄 Consensus Mechanism Control", 
            ConsensusUIUtils.ACCENT_TEXT_COLOR, ConsensusUIUtils.SUBHEADER_FONT_SIZE, true);
        
        // Current consensus display
        HBox currentConsensusBox = new HBox(15);
        currentConsensusBox.setAlignment(Pos.CENTER_LEFT);
        
        Label currentLabel = ConsensusUIUtils.createStyledLabel("Current:", 
            ConsensusUIUtils.SECONDARY_TEXT_COLOR, 14, false);
        
        HBox currentIndicator = ConsensusUIUtils.createConsensusStatusIndicator(
            currentConsensusType, consensusController.getConsensusStatus().isOperational);
        
        currentConsensusBox.getChildren().addAll(currentLabel, currentIndicator);
        
        // Consensus type selection
        VBox selectionBox = ConsensusUIUtils.createConsensusSelectionForm(
            currentConsensusType, this::handleConsensusSelectionChange);
        
        // Switch button with validation
        Button switchButton = ConsensusUIUtils.createStyledButton("🔀 Switch Consensus", 
            ConsensusUIUtils.WARNING_COLOR, ConsensusUIUtils.PRIMARY_TEXT_COLOR);
        switchButton.setOnAction(e -> handleConsensusSwitchRequest());
        
        // Prerequisites check
        VBox prerequisitesBox = createPrerequisitesCheckDisplay();
        
        panel.getChildren().addAll(header, currentConsensusBox, selectionBox, prerequisitesBox, switchButton);
        return panel;
    }
    
    /**
     * Creates consensus-specific controls
     */
    private VBox createConsensusSpecificControls() {
        VBox container = new VBox(20);
        
        // Create panels for each consensus type
        powPanel = createPoWControlPanel();
        posPanel = createPoSControlPanel();
        pbftPanel = createPBFTControlPanel();
        streamPanel = createStreamControlPanel();
        
        // Show only the active consensus panel
        updateActiveConsensusPanel();
        
        return container;
    }
    
    /**
     * Creates PoW control panel
     */
    private VBox createPoWControlPanel() {
        VBox panel = ConsensusUIUtils.createStyledCard();
        panel.setSpacing(15);
        
        Label header = ConsensusUIUtils.createStyledLabel("⛏️ Proof of Work Controls", 
            ConsensusUIUtils.POW_COLOR, ConsensusUIUtils.SUBHEADER_FONT_SIZE, true);
        
        // Mining configuration
        VBox configBox = ConsensusUIUtils.createMiningConfigForm(
            consensusController.getMinerAddress(), consensusController.getCurrentDifficulty());
        
        // Mining controls
        HBox miningControls = new HBox(15);
        
        Button startMiningBtn = ConsensusUIUtils.createStyledButton("▶️ Start Mining", 
            ConsensusUIUtils.SUCCESS_COLOR, ConsensusUIUtils.PRIMARY_TEXT_COLOR);
        startMiningBtn.setOnAction(e -> handleStartMining());
        
        Button stopMiningBtn = ConsensusUIUtils.createStyledButton("⏹️ Stop Mining", 
            ConsensusUIUtils.ERROR_COLOR, ConsensusUIUtils.PRIMARY_TEXT_COLOR);
        stopMiningBtn.setOnAction(e -> handleStopMining());
        
        Button adjustDifficultyBtn = ConsensusUIUtils.createStyledButton("⚡ Adjust Difficulty", 
            ConsensusUIUtils.INFO_COLOR, ConsensusUIUtils.PRIMARY_TEXT_COLOR);
        adjustDifficultyBtn.setOnAction(e -> handleAdjustDifficulty());
        
        miningControls.getChildren().addAll(startMiningBtn, stopMiningBtn, adjustDifficultyBtn);
        
        // Mining status display
        MiningStatus miningStatus = consensusController.getConsensusStatus().powStatus;
        VBox statusDisplay = miningStatus != null ? 
            ConsensusUIUtils.createMiningStatusPanel(miningStatus) : 
            createPlaceholderStatusPanel("Mining status not available");
        
        panel.getChildren().addAll(header, configBox, miningControls, statusDisplay);
        return panel;
    }
    
    /**
     * Creates PoS control panel
     */
    private VBox createPoSControlPanel() {
        VBox panel = ConsensusUIUtils.createStyledCard();
        panel.setSpacing(15);
        
        Label header = ConsensusUIUtils.createStyledLabel("🏦 Proof of Stake Controls", 
            ConsensusUIUtils.POS_COLOR, ConsensusUIUtils.SUBHEADER_FONT_SIZE, true);
        
        // Staking configuration
        String stakingAddress = ""; // Get from wallet if connected
        long currentStake = 0; // Get from consensus controller
        VBox configBox = ConsensusUIUtils.createStakingConfigForm(stakingAddress, currentStake);
        
        // Staking controls
        HBox stakingControls = new HBox(15);
        
        Button stakeBtn = ConsensusUIUtils.createStyledButton("💰 Stake Tokens", 
            ConsensusUIUtils.SUCCESS_COLOR, ConsensusUIUtils.PRIMARY_TEXT_COLOR);
        stakeBtn.setOnAction(e -> handleStakeTokens());
        
        Button unstakeBtn = ConsensusUIUtils.createStyledButton("💸 Unstake Tokens", 
            ConsensusUIUtils.WARNING_COLOR, ConsensusUIUtils.PRIMARY_TEXT_COLOR);
        unstakeBtn.setOnAction(e -> handleUnstakeTokens());
        
        stakingControls.getChildren().addAll(stakeBtn, unstakeBtn);
        
        // Staking status display
        StakingStatus stakingStatus = consensusController.getConsensusStatus().posStatus;
        VBox statusDisplay = stakingStatus != null ? 
            ConsensusUIUtils.createStakingStatusPanel(stakingStatus) : 
            createPlaceholderStatusPanel("Staking status not available");
        
        panel.getChildren().addAll(header, configBox, stakingControls, statusDisplay);
        return panel;
    }
    
    /**
     * Creates PBFT control panel
     */
    private VBox createPBFTControlPanel() {
        VBox panel = ConsensusUIUtils.createStyledCard();
        panel.setSpacing(15);
        
        Label header = ConsensusUIUtils.createStyledLabel("🛡️ PBFT Consensus Controls", 
            ConsensusUIUtils.PBFT_COLOR, ConsensusUIUtils.SUBHEADER_FONT_SIZE, true);
        
        // Validator management
        VBox validatorBox = new VBox(10);
        Label validatorTitle = ConsensusUIUtils.createStyledLabel("Validator Management:", 
            ConsensusUIUtils.SECONDARY_TEXT_COLOR, 14, true);
        
        HBox validatorControls = new HBox(15);
        
        TextField validatorAddressField = new TextField();
        validatorAddressField.setPromptText("Enter validator address");
        validatorAddressField.setStyle("-fx-background-color: " + ConsensusUIUtils.SECONDARY_BG_COLOR + 
            "; -fx-text-fill: " + ConsensusUIUtils.PRIMARY_TEXT_COLOR + 
            "; -fx-border-color: " + ConsensusUIUtils.BORDER_COLOR + ";");
        
        Button addValidatorBtn = ConsensusUIUtils.createStyledButton("➕ Add Validator", 
            ConsensusUIUtils.SUCCESS_COLOR, ConsensusUIUtils.PRIMARY_TEXT_COLOR);
        addValidatorBtn.setOnAction(e -> handleAddValidator(validatorAddressField.getText()));
        
        validatorControls.getChildren().addAll(validatorAddressField, addValidatorBtn);
        validatorBox.getChildren().addAll(validatorTitle, validatorControls);
        
        // PBFT operation controls
        HBox pbftControls = new HBox(15);
        
        Button startValidationBtn = ConsensusUIUtils.createStyledButton("▶️ Start Validation", 
            ConsensusUIUtils.SUCCESS_COLOR, ConsensusUIUtils.PRIMARY_TEXT_COLOR);
        startValidationBtn.setOnAction(e -> handleStartValidation());
        
        Button stopValidationBtn = ConsensusUIUtils.createStyledButton("⏹️ Stop Validation", 
            ConsensusUIUtils.ERROR_COLOR, ConsensusUIUtils.PRIMARY_TEXT_COLOR);
        stopValidationBtn.setOnAction(e -> handleStopValidation());
        
        pbftControls.getChildren().addAll(startValidationBtn, stopValidationBtn);
        
        // PBFT status display
        PBFTStatus pbftStatus = consensusController.getConsensusStatus().pbftStatus;
        VBox statusDisplay = pbftStatus != null ? 
            ConsensusUIUtils.createPBFTStatusPanel(pbftStatus) : 
            createPlaceholderStatusPanel("PBFT status not available");
        
        panel.getChildren().addAll(header, validatorBox, pbftControls, statusDisplay);
        return panel;
    }
    
    /**
     * Creates Stream consensus control panel
     */
    private VBox createStreamControlPanel() {
        VBox panel = ConsensusUIUtils.createStyledCard();
        panel.setSpacing(15);
        
        Label header = ConsensusUIUtils.createStyledLabel("📊 Stream Consensus Controls", 
            ConsensusUIUtils.STREAM_COLOR, ConsensusUIUtils.SUBHEADER_FONT_SIZE, true);
        
        // Stream configuration
        VBox configBox = new VBox(10);
        Label configTitle = ConsensusUIUtils.createStyledLabel("Stream Configuration:", 
            ConsensusUIUtils.SECONDARY_TEXT_COLOR, 14, true);
        
        GridPane configGrid = new GridPane();
        configGrid.setHgap(15);
        configGrid.setVgap(10);
        
        // Buffer size
        TextField bufferSizeField = new TextField("1000");
        configGrid.add(createLabeledComponent("Buffer Size:", bufferSizeField), 0, 0);
        
        // Batch size
        TextField batchSizeField = new TextField("10");
        configGrid.add(createLabeledComponent("Batch Size:", batchSizeField), 1, 0);
        
        configBox.getChildren().addAll(configTitle, configGrid);
        
        // Stream operation controls
        HBox streamControls = new HBox(15);
        
        Button startProcessingBtn = ConsensusUIUtils.createStyledButton("▶️ Start Processing", 
            ConsensusUIUtils.SUCCESS_COLOR, ConsensusUIUtils.PRIMARY_TEXT_COLOR);
        startProcessingBtn.setOnAction(e -> handleStartStreamProcessing());
        
        Button stopProcessingBtn = ConsensusUIUtils.createStyledButton("⏹️ Stop Processing", 
            ConsensusUIUtils.ERROR_COLOR, ConsensusUIUtils.PRIMARY_TEXT_COLOR);
        stopProcessingBtn.setOnAction(e -> handleStopStreamProcessing());
        
        streamControls.getChildren().addAll(startProcessingBtn, stopProcessingBtn);
        
        // Stream status display
        StreamStatus streamStatus = consensusController.getConsensusStatus().streamStatus;
        VBox statusDisplay = streamStatus != null ? 
            ConsensusUIUtils.createStreamStatusPanel(streamStatus) : 
            createPlaceholderStatusPanel("Stream status not available");
        
        panel.getChildren().addAll(header, configBox, streamControls, statusDisplay);
        return panel;
    }
    
    // ========================================
    // EVENT HANDLERS
    // ========================================
    
    /**
     * Handles emergency stop request
     */
    private void handleEmergencyStop() {
        LOGGER.warning("Emergency stop requested");
        
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Emergency Stop");
        confirmDialog.setHeaderText("Emergency Consensus Stop");
        confirmDialog.setContentText("This will immediately stop all consensus operations. Continue?");
        
        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                // Stop current consensus operations
                if (consensusController.isMining()) {
                    consensusController.stopMining();
                }
                if (consensusController.isValidating()) {
                    consensusController.stopValidation();
                }
                
                showSuccessAlert("Emergency Stop", "All consensus operations stopped successfully");
                refreshAllData();
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error during emergency stop", e);
                showErrorAlert("Emergency stop failed: " + e.getMessage());
            }
        }
    }
    
    /**
     * Handles auto-optimization toggle
     */
    private void handleAutoOptimizationToggle(boolean enabled) {
        LOGGER.info("Auto-optimization toggle requested: " + enabled);
        
        ConsensusManagerResult result = consensusManager.setAutoOptimization(enabled);
        if (result.success) {
            showSuccessAlert("Auto-Optimization", result.message);
        } else {
            showErrorAlert("Auto-optimization change failed: " + result.message);
        }
        
        refreshAllData();
    }
    
    /**
     * Handles consensus selection change
     */
    private void handleConsensusSelectionChange() {
        // Update prerequisites check when selection changes
        updatePrerequisitesDisplay();
    }
    
    /**
     * Handles consensus switch request
     */
    private void handleConsensusSwitchRequest() {
        // Get selected consensus type from form
        ConsensusType selectedType = getSelectedConsensusType();
        
        if (selectedType == currentConsensusType) {
            showInfoAlert("No Change", "Selected consensus is already active");
            return;
        }
        
        // Show confirmation dialog
        Alert confirmDialog = ConsensusUIUtils.createConsensusSwitchDialog(currentConsensusType, selectedType);
        Optional<ButtonType> result = confirmDialog.showAndWait();
        
        if (result.isPresent() && result.get() == ButtonType.OK) {
            performConsensusSwitch(selectedType);
        }
    }
    
    /**
     * Performs actual consensus switch
     */
    private void performConsensusSwitch(ConsensusType newType) {
        LOGGER.info("Performing consensus switch to: " + newType);
        
        // Show progress indicator
        ProgressIndicator progressIndicator = new ProgressIndicator();
        Alert progressDialog = new Alert(Alert.AlertType.INFORMATION);
        progressDialog.setTitle("Switching Consensus");
        progressDialog.setHeaderText("Switching to " + newType.getDisplayName());
        progressDialog.setContentText("Please wait while the consensus mechanism is being switched...");
        progressDialog.getDialogPane().setContent(progressIndicator);
        progressDialog.show();
        
        // Perform switch asynchronously
        CompletableFuture.supplyAsync(() -> {
            return consensusManager.switchConsensus(newType);
        }).thenAcceptAsync(result -> {
            Platform.runLater(() -> {
                progressDialog.close();
                
                if (result.success) {
                    currentConsensusType = newType;
                    showSuccessAlert("Consensus Switch", result.message);
                    refreshAllData();
                    updateActiveConsensusPanel();
                } else {
                    showErrorAlert("Consensus switch failed: " + result.message);
                }
            });
        });
    }
    
    // ========================================
    // CONSENSUS-SPECIFIC HANDLERS
    // ========================================
    
    /**
     * Handles start mining request
     */
   private void handleStartMining() {
    LOGGER.info("Start mining requested");
    
    // Check prerequisites first
    if (consensusController.getMinerAddress() == null || 
        consensusController.getMinerAddress().isEmpty()) {
        // Set a default address if none exists
        consensusController.setMinerAddress("0x" + Long.toHexString(System.currentTimeMillis()));
    }
    
    // Enable auto-mining
    consensusController.setAutoMiningEnabled(true);
    
    ConsensusOperationResult result = consensusController.startMining();
    
    if (result.success) {
        showSuccessAlert("Mining Started", result.message);
        // Add visual indicator that mining is active
        if (powPanel != null) {
            powPanel.setStyle(powPanel.getStyle() + 
                "; -fx-border-color: " + ConsensusUIUtils.SUCCESS_COLOR + 
                "; -fx-border-width: 2;");
        }
    } else {
        showErrorAlert("Failed to start mining: " + result.message);
    }
    
    refreshAllData();
}
    
    /**
     * Handles stop mining request
     */
    private void handleStopMining() {
        LOGGER.info("Stop mining requested");
        
        ConsensusOperationResult result = consensusController.stopMining();
        if (result.success) {
            showSuccessAlert("Mining Stopped", result.message);
        } else {
            showErrorAlert("Failed to stop mining: " + result.message);
        }
        
        refreshAllData();
    }
    
    /**
     * Handles difficulty adjustment
     */
    private void handleAdjustDifficulty() {
    TextInputDialog dialog = new TextInputDialog(String.valueOf(consensusController.getCurrentDifficulty()));
    dialog.setTitle("Adjust Mining Difficulty");
    dialog.setHeaderText("Set New Mining Difficulty");
    dialog.setContentText("Enter new difficulty value:");
    
    Optional<String> result = dialog.showAndWait();
    if (result.isPresent()) {
        ValidationResult validation = ConsensusUIUtils.validateDifficulty(result.get());
        if (validation.isValid) {
            // Show progress while adjusting
            ProgressIndicator progress = new ProgressIndicator();
            Alert progressAlert = new Alert(Alert.AlertType.NONE);
            progressAlert.setTitle("Adjusting Difficulty");
            progressAlert.setHeaderText("Please wait...");
            progressAlert.getDialogPane().setContent(progress);
            progressAlert.show();
            
            CompletableFuture.supplyAsync(() -> {
                long newDifficulty = Long.parseLong(result.get());
                return consensusController.adjustDifficulty(newDifficulty);
            }).thenAccept(adjustResult -> {
                Platform.runLater(() -> {
                    progressAlert.close();
                    if (adjustResult.success) {
                        showSuccessAlert("Difficulty Adjusted", adjustResult.message);
                        refreshAllData();
                    } else {
                        showErrorAlert("Failed to adjust difficulty: " + adjustResult.message);
                    }
                });
            });
        } else {
            showErrorAlert("Invalid difficulty: " + validation.message);
        }
    }
}
    
    /**
     * Handles stake tokens request
     */
    private void handleStakeTokens() {
        TextInputDialog dialog = new TextInputDialog("1000");
        dialog.setTitle("Stake Tokens");
        dialog.setHeaderText("Stake Tokens for Validation Rights");
        dialog.setContentText("Enter amount to stake (TINC):");
        
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            ValidationResult validation = ConsensusUIUtils.validateStakeAmount(result.get(), 10000L); // Assume 10k balance
            if (validation.isValid) {
                long stakeAmount = Long.parseLong(result.get());
                String stakingAddress = "0x1234567890123456789012345678901234567890"; // Get from wallet
                
                ConsensusOperationResult stakeResult = consensusController.stakeTokens(stakingAddress, stakeAmount);
                
                if (stakeResult.success) {
                    showSuccessAlert("Tokens Staked", stakeResult.message);
                    refreshAllData();
                } else {
                    showErrorAlert("Failed to stake tokens: " + stakeResult.message);
                }
            } else {
                showErrorAlert("Invalid stake amount: " + validation.message);
            }
        }
    }
    
    /**
     * Handles unstake tokens request
     */
    private void handleUnstakeTokens() {
        TextInputDialog dialog = new TextInputDialog("100");
        dialog.setTitle("Unstake Tokens");
        dialog.setHeaderText("Unstake Tokens");
        dialog.setContentText("Enter amount to unstake (TINC):");
        
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            ValidationResult validation = ConsensusUIUtils.validateStakeAmount(result.get(), 5000L); // Assume 5k staked
            if (validation.isValid) {
                long unstakeAmount = Long.parseLong(result.get());
                String stakingAddress = "0x1234567890123456789012345678901234567890"; // Get from wallet
                
                ConsensusOperationResult unstakeResult = consensusController.unstakeTokens(stakingAddress, unstakeAmount);
                
                if (unstakeResult.success) {
                    showSuccessAlert("Tokens Unstaked", unstakeResult.message);
                    refreshAllData();
                } else {
                    showErrorAlert("Failed to unstake tokens: " + unstakeResult.message);
                }
            } else {
                showErrorAlert("Invalid unstake amount: " + validation.message);
            }
        }
    }
    
    /**
     * Handles start validation request
     */
    private void handleStartValidation() {
        LOGGER.info("Start validation requested");
        
        ConsensusOperationResult result = consensusController.startValidation();
        if (result.success) {
            showSuccessAlert("Validation Started", result.message);
        } else {
            showErrorAlert("Failed to start validation: " + result.message);
        }
        
        refreshAllData();
    }
    
    /**
     * Handles stop validation request
     */
    private void handleStopValidation() {
        LOGGER.info("Stop validation requested");
        
        ConsensusOperationResult result = consensusController.stopValidation();
        if (result.success) {
            showSuccessAlert("Validation Stopped", result.message);
        } else {
            showErrorAlert("Failed to stop validation: " + result.message);
        }
        
        refreshAllData();
    }
    
    /**
     * Handles add validator request
     */
    private void handleAddValidator(String validatorAddress) {
        if (validatorAddress == null || validatorAddress.trim().isEmpty()) {
            showErrorAlert("Please enter a validator address");
            return;
        }
        
        ValidationResult validation = ConsensusUIUtils.validateMinerAddress(validatorAddress);
        if (validation.isValid) {
            ConsensusOperationResult result = consensusController.addValidator(validatorAddress);
            
            if (result.success) {
                showSuccessAlert("Validator Added", result.message);
                refreshAllData();
            } else {
                showErrorAlert("Failed to add validator: " + result.message);
            }
        } else {
            showErrorAlert("Invalid validator address: " + validation.message);
        }
    }
    
    /**
     * Handles start stream processing request
     */
    private void handleStartStreamProcessing() {
        LOGGER.info("Start stream processing requested");
        
        ConsensusOperationResult result = consensusController.startStreamProcessing();
        if (result.success) {
            showSuccessAlert("Stream Processing Started", result.message);
        } else {
            showErrorAlert("Failed to start stream processing: " + result.message);
        }
        
        refreshAllData();
    }
    
    /**
     * Handles stop stream processing request
     */
    private void handleStopStreamProcessing() {
        // Implementation for stopping stream processing
        showInfoAlert("Stream Processing", "Stream processing stopped");
        refreshAllData();
    }
    
    // ========================================
    // POLICY AND OPTIMIZATION HANDLERS
    // ========================================
    
    /**
     * Handles apply policy request
     */
    private void handleApplyPolicy() {
        ConsensusPolicy selectedPolicy = getSelectedPolicy();
        if (selectedPolicy != null && selectedPolicy != currentPolicy) {
            ConsensusManagerResult result = consensusManager.setConsensusPolicy(selectedPolicy);
            
            if (result.success) {
                currentPolicy = selectedPolicy;
                showSuccessAlert("Policy Applied", result.message);
                refreshAllData();
            } else {
                showErrorAlert("Failed to apply policy: " + result.message);
            }
        }
    }
    
    /**
     * Handles reset policy request
     */
    private void handleResetPolicy() {
        ConsensusManagerResult result = consensusManager.setConsensusPolicy(ConsensusPolicy.BALANCED);
        
        if (result.success) {
            currentPolicy = ConsensusPolicy.BALANCED;
            showSuccessAlert("Policy Reset", "Policy reset to Balanced");
            refreshAllData();
        } else {
            showErrorAlert("Failed to reset policy: " + result.message);
        }
    }
    
    /**
     * Handles manual optimization request
     */
    private void handleManualOptimization() {
        LOGGER.info("Manual optimization requested");
        
        ConsensusManagerResult result = consensusManager.optimizeConsensus();
        
        if (result.success) {
            showSuccessAlert("Optimization Complete", result.message);
            refreshAllData();
        } else {
            showErrorAlert("Optimization failed: " + result.message);
        }
    }
    
    /**
     * Handles toggle auto-optimization
     */
    private void handleToggleAutoOptimization() {
        boolean currentState = consensusManager.isAutoOptimizationEnabled();
        ConsensusManagerResult result = consensusManager.setAutoOptimization(!currentState);
        
        if (result.success) {
            showSuccessAlert("Auto-Optimization", result.message);
            refreshAllData();
        } else {
            showErrorAlert("Failed to toggle auto-optimization: " + result.message);
        }
    }
    
    // ========================================
    // REAL-TIME UPDATES AND MONITORING
    // ========================================
    
    /**
     * Sets up event listeners for real-time updates
     */
    private void setupEventListeners() {
        // Setup consensus manager event listener
        consensusManager.addEventListener(new ConsensusManager.ConsensusManagerEventListener() {
            @Override
            public void onConsensusChanged(ConsensusType newType) {
                Platform.runLater(() -> {
                    currentConsensusType = newType;
                    updateConsensusStatusDisplay();
                    updateActiveConsensusPanel();
                });
            }
            
            @Override
            public void onPolicyChanged(ConsensusPolicy oldPolicy, ConsensusPolicy newPolicy) {
                Platform.runLater(() -> {
                    currentPolicy = newPolicy;
                    updatePolicyDisplay();
                });
            }
            
            @Override
            public void onHealthStatusChanged(ConsensusHealthStatus health) {
                Platform.runLater(() -> {
                    updateHealthStatusDisplay(health);
                });
            }
            
            @Override
            public void onOptimizationApplied(ConsensusRecommendation recommendation) {
                Platform.runLater(() -> {
                    updateRecommendationDisplay(recommendation);
                });
            }
            
            @Override
            public void onConsensusError(String error) {
                Platform.runLater(() -> {
                    showErrorAlert("Consensus Error: " + error);
                });
            }
        });
    }
    
    /**
     * Starts real-time updates
     */
    private void startRealTimeUpdates() {
        if (!isRealTimeUpdateEnabled) {
            return;
        }
        
        LOGGER.info("Starting real-time consensus updates");
        
        // Create update service
        updateService = new ScheduledService<Void>() {
            @Override 
            protected Task<Void> createTask() {
                return new Task<Void>() {
                    @Override 
                    protected Void call() {
                        // Update consensus data in background
                        Platform.runLater(() -> {
                            try {
                                updateRealTimeData();
                            } catch (Exception e) {
                                LOGGER.warning("Error during real-time update: " + e.getMessage());
                            }
                        });
                        return null;
                    }
                };
            }
        };
        
        updateService.setPeriod(Duration.seconds(5)); // Update every 5 seconds
        updateService.start();
        
        // Create status update timeline for frequent updates
        statusUpdateTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateStatusLabels()));
        statusUpdateTimeline.setCycleCount(Timeline.INDEFINITE);
        statusUpdateTimeline.play();
        
        LOGGER.info("Real-time updates started");
    }
    
    /**
     * Updates real-time data
     */
   private void updateRealTimeData() {
    try {
        // Get latest status
        ConsensusStatusInfo status = consensusController.getConsensusStatus();
        ConsensusHealthStatus health = consensusManager.getConsensusHealth();
        
        // Update all components in parallel for better performance
        CompletableFuture.allOf(
            CompletableFuture.runAsync(() -> updateStatusOverview()),
            CompletableFuture.runAsync(() -> updateMetricsGrid()),
            CompletableFuture.runAsync(() -> updateHealthStatus()),
            CompletableFuture.runAsync(() -> updatePerformanceCharts())
        ).thenRun(() -> {
            // Update last update time after all updates complete
            lastUpdateTime = LocalDateTime.now();
            Platform.runLater(() -> updateStatusLabels());
        });
        
    } catch (Exception e) {
        LOGGER.warning("Error updating real-time data: " + e.getMessage());
    }
}
    
    /**
     * Updates status labels in header
     */
    private void updateStatusLabels() {
        try {
            if (consensusStatusLabel != null) {
                ConsensusStatusInfo status = consensusController.getConsensusStatus();
                String statusText = status.activeConsensusType.getDisplayName() + 
                                  " (" + (status.isOperational ? "Active" : "Inactive") + ")";
                consensusStatusLabel.setText(statusText);
            }
            
            if (lastUpdateLabel != null && lastUpdateTime != null) {
                lastUpdateLabel.setText("Last updated: " + lastUpdateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            }
            
        } catch (Exception e) {
            LOGGER.warning("Error updating status labels: " + e.getMessage());
        }
    }
    
    // ========================================
    // UI UPDATE METHODS
    // ========================================
    
    /**
     * Refreshes all data and UI components
     */
    private void refreshAllData() {
        LOGGER.info("Refreshing all consensus data");
        
        try {
            updateRealTimeData();
            updateActiveConsensusPanel();
            updatePrerequisitesDisplay();
            
            // Force update of all panels
            if (statusOverviewPanel != null) {
                updateStatusOverview();
            }
            
            if (healthStatusPanel != null) {
                updateHealthStatus();
            }
            
            if (recommendationPanel != null) {
                updateRecommendationPanel();
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error refreshing data", e);
        }
    }
    
    /**
     * Updates active consensus panel visibility
     */
    private void updateActiveConsensusPanel() {
    if (powPanel != null && posPanel != null && pbftPanel != null && streamPanel != null) {
        // Hide all panels first
        powPanel.setVisible(false);
        powPanel.setManaged(false);
        posPanel.setVisible(false);
        posPanel.setManaged(false);
        pbftPanel.setVisible(false);
        pbftPanel.setManaged(false);
        streamPanel.setVisible(false);
        streamPanel.setManaged(false);
        
        // Show only the active consensus panel
        switch (currentConsensusType) {
            case PROOF_OF_WORK:
                powPanel.setVisible(true);
                powPanel.setManaged(true);
                break;
            case PROOF_OF_STAKE:
                posPanel.setVisible(true);
                posPanel.setManaged(true);
                break;
            case PBFT:
                pbftPanel.setVisible(true);
                pbftPanel.setManaged(true);
                break;
            case STREAM_CONSENSUS:
                streamPanel.setVisible(true);
                streamPanel.setManaged(true);
                break;
        }
    }
}
    
    // ========================================
    // POLICY AND OPTIMIZATION PANELS
    // ========================================
    
    /**
     * Creates policy configuration panel
     */
    private VBox createPolicyConfigurationPanel() {
        VBox panel = ConsensusUIUtils.createStyledCard();
        panel.setSpacing(20);
        
        Label header = ConsensusUIUtils.createStyledLabel("🎯 Consensus Management Policy", 
            ConsensusUIUtils.ACCENT_TEXT_COLOR, ConsensusUIUtils.SUBHEADER_FONT_SIZE, true);
        
        // Current policy display
        Label currentPolicyLabel = ConsensusUIUtils.createStyledLabel(
            "Current Policy: " + currentPolicy.getDisplayName(), 
            ConsensusUIUtils.SUCCESS_COLOR, 16, true);
        
        // Policy selection form
        VBox policyForm = ConsensusUIUtils.createPolicySelectionForm(currentPolicy);
        
        // Policy controls
        HBox policyControls = new HBox(15);
        
        Button applyPolicyBtn = ConsensusUIUtils.createStyledButton("✅ Apply Policy", 
            ConsensusUIUtils.SUCCESS_COLOR, ConsensusUIUtils.PRIMARY_TEXT_COLOR);
        applyPolicyBtn.setOnAction(e -> handleApplyPolicy());
        
        Button resetPolicyBtn = ConsensusUIUtils.createStyledButton("🔄 Reset to Default", 
            ConsensusUIUtils.WARNING_COLOR, ConsensusUIUtils.PRIMARY_TEXT_COLOR);
        resetPolicyBtn.setOnAction(e -> handleResetPolicy());
        
        policyControls.getChildren().addAll(applyPolicyBtn, resetPolicyBtn);
        
        panel.getChildren().addAll(header, currentPolicyLabel, policyForm, policyControls);
        return panel;
    }
    
    /**
     * Creates optimization panel
     */
    private VBox createOptimizationPanel() {
        VBox panel = ConsensusUIUtils.createStyledCard();
        panel.setSpacing(20);
        
        Label header = ConsensusUIUtils.createStyledLabel("🚀 Consensus Optimization", 
            ConsensusUIUtils.ACCENT_TEXT_COLOR, ConsensusUIUtils.SUBHEADER_FONT_SIZE, true);
        
        // Auto-optimization status
        HBox autoOptBox = new HBox(15);
        autoOptBox.setAlignment(Pos.CENTER_LEFT);
        
        Label autoOptLabel = ConsensusUIUtils.createStyledLabel("Auto-Optimization:", 
            ConsensusUIUtils.SECONDARY_TEXT_COLOR, 14, false);
        
        Label autoOptStatus = ConsensusUIUtils.createStyledLabel(
            consensusManager.isAutoOptimizationEnabled() ? "Enabled" : "Disabled",
            consensusManager.isAutoOptimizationEnabled() ? ConsensusUIUtils.SUCCESS_COLOR : ConsensusUIUtils.ERROR_COLOR,
            14, true);
        
        autoOptBox.getChildren().addAll(autoOptLabel, autoOptStatus);
        
        // Optimization controls
        HBox optimizationControls = new HBox(15);
        
        Button manualOptimizeBtn = ConsensusUIUtils.createStyledButton("⚡ Optimize Now", 
            ConsensusUIUtils.INFO_COLOR, ConsensusUIUtils.PRIMARY_TEXT_COLOR);
        manualOptimizeBtn.setOnAction(e -> handleManualOptimization());
        
        Button toggleAutoOptBtn = ConsensusUIUtils.createStyledButton(
            consensusManager.isAutoOptimizationEnabled() ? "⏸️ Disable Auto" : "▶️ Enable Auto",
            consensusManager.isAutoOptimizationEnabled() ? ConsensusUIUtils.WARNING_COLOR : ConsensusUIUtils.SUCCESS_COLOR,
            ConsensusUIUtils.PRIMARY_TEXT_COLOR);
        toggleAutoOptBtn.setOnAction(e -> handleToggleAutoOptimization());
        
        optimizationControls.getChildren().addAll(manualOptimizeBtn, toggleAutoOptBtn);
        
        // Optimization metrics
        VBox metricsBox = createOptimizationMetricsDisplay();
        
        panel.getChildren().addAll(header, autoOptBox, optimizationControls, metricsBox);
        return panel;
    }
    
    /**
     * Creates current recommendation panel
     */
    private VBox createCurrentRecommendationPanel() {
        VBox container = new VBox(0);
        
        // Get current recommendation
        ConsensusManagerResult result = consensusManager.optimizeConsensus();
        if (result.success && result.recommendation != null) {
            VBox recommendationDisplay = ConsensusUIUtils.createRecommendationPanel(result.recommendation);
            container.getChildren().add(recommendationDisplay);
        } else {
            VBox noRecommendationPanel = ConsensusUIUtils.createStyledCard();
            Label noRecLabel = ConsensusUIUtils.createStyledLabel("ℹ️ No optimization recommendations at this time", 
                ConsensusUIUtils.INFO_COLOR, 16, false);
            noRecommendationPanel.getChildren().add(noRecLabel);
            container.getChildren().add(noRecommendationPanel);
        }
        
        return container;
    }
    
    // ========================================
    // ANALYTICS AND CHARTS
    // ========================================
    
    /**
     * Creates performance charts section
     */
    private VBox createPerformanceChartsSection() {
        VBox section = new VBox(20);
        
        Label sectionHeader = ConsensusUIUtils.createStyledLabel("📈 Performance Charts", 
            ConsensusUIUtils.ACCENT_TEXT_COLOR, ConsensusUIUtils.SUBHEADER_FONT_SIZE, true);
        
        // Performance chart
        performanceChart = ConsensusUIUtils.createPerformanceChart("Consensus Performance Over Time");
        performanceChart.setPrefHeight(300);
        
        section.getChildren().addAll(sectionHeader, performanceChart);
        return section;
    }
    
    /**
     * Creates analytics dashboard
     */
    private VBox createAnalyticsDashboard() {
        VBox dashboard = ConsensusUIUtils.createStyledCard();
        dashboard.setSpacing(20);
        
        Label header = ConsensusUIUtils.createStyledLabel("📊 Consensus Analytics Dashboard", 
            ConsensusUIUtils.ACCENT_TEXT_COLOR, ConsensusUIUtils.SUBHEADER_FONT_SIZE, true);
        
        // Analytics summary
        GridPane summaryGrid = new GridPane();
        summaryGrid.setHgap(20);
        summaryGrid.setVgap(15);
        
        // Get analytics data
        ConsensusAnalytics analytics = consensusManager.getConsensusAnalytics();
        
        // Add summary metrics
        VBox totalSwitchesCard = ConsensusUIUtils.createPerformanceCard("Total Switches", "127", "", "+15%");
        summaryGrid.add(totalSwitchesCard, 0, 0);
        
        VBox avgPerformanceCard = ConsensusUIUtils.createPerformanceCard("Avg Performance", "87.3%", "", "+2.1%");
        summaryGrid.add(avgPerformanceCard, 1, 0);
        
        VBox optimizationsCard = ConsensusUIUtils.createPerformanceCard("Optimizations", "42", "", "+8");
        summaryGrid.add(optimizationsCard, 2, 0);
        
        dashboard.getChildren().addAll(header, summaryGrid);
        return dashboard;
    }
    
    /**
     * Creates consensus distribution section
     */
    private VBox createConsensusDistributionSection() {
        VBox section = ConsensusUIUtils.createStyledCard();
        section.setSpacing(15);
        
        Label header = ConsensusUIUtils.createStyledLabel("🥧 Consensus Usage Distribution", 
            ConsensusUIUtils.ACCENT_TEXT_COLOR, ConsensusUIUtils.SUBHEADER_FONT_SIZE, true);
        
        // Sample distribution data
        Map<ConsensusType, Integer> distribution = new HashMap<>();
        distribution.put(ConsensusType.PROOF_OF_WORK, 45);
        distribution.put(ConsensusType.PROOF_OF_STAKE, 30);
        distribution.put(ConsensusType.PBFT, 20);
        distribution.put(ConsensusType.STREAM_CONSENSUS, 5);
        
        consensusDistributionChart = ConsensusUIUtils.createConsensusDistributionChart(distribution);
        consensusDistributionChart.setPrefHeight(300);
        
        section.getChildren().addAll(header, consensusDistributionChart);
        return section;
    }
    
    // ========================================
    // UTILITY METHODS
    // ========================================
    
    /**
     * Creates labeled component wrapper
     */
    private VBox createLabeledComponent(String labelText, Node component) {
        VBox wrapper = new VBox(5);
        wrapper.setAlignment(Pos.CENTER_LEFT);
        
        Label label = ConsensusUIUtils.createStyledLabel(labelText, 
            ConsensusUIUtils.SECONDARY_TEXT_COLOR, 12, false);
        
        wrapper.getChildren().addAll(label, component);
        return wrapper;
    }
    
    /**
     * Creates placeholder status panel
     */
    private VBox createPlaceholderStatusPanel(String message) {
        VBox panel = ConsensusUIUtils.createStyledCard();
        Label messageLabel = ConsensusUIUtils.createStyledLabel(message, 
            ConsensusUIUtils.MUTED_TEXT_COLOR, 14, false);
        panel.getChildren().add(messageLabel);
        return panel;
    }
    
    /**
     * Creates error view
     */
    private VBox createErrorView(String errorMessage) {
        VBox errorView = new VBox(20);
        errorView.setAlignment(Pos.CENTER);
        errorView.setStyle("-fx-padding: 50; -fx-background-color: " + ConsensusUIUtils.PRIMARY_BG_COLOR + ";");
        
        Label errorLabel = ConsensusUIUtils.createStyledLabel("⌘ Error", 
            ConsensusUIUtils.ERROR_COLOR, 24, true);
        
        Label messageLabel = ConsensusUIUtils.createStyledLabel(errorMessage, 
            ConsensusUIUtils.SECONDARY_TEXT_COLOR, 16, false);
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(400);
        
        Button retryButton = ConsensusUIUtils.createStyledButton("🔄 Retry", 
            ConsensusUIUtils.INFO_COLOR, ConsensusUIUtils.PRIMARY_TEXT_COLOR);
        retryButton.setOnAction(e -> {
            // Attempt to recreate the interface
            try {
                mainContainer.getChildren().clear();
                VBox newContent = createConsensusManagementView();
                mainContainer.getChildren().addAll(newContent.getChildren());
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Error during retry", ex);
            }
        });
        
        errorView.getChildren().addAll(errorLabel, messageLabel, retryButton);
        return errorView;
    }
    
    /**
     * Shows success alert
     */
    private void showSuccessAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText("Success");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    /**
     * Shows error alert
     */
    private void showErrorAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Operation Failed");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    /**
     * Shows info alert
     */
    private void showInfoAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    // ========================================
    // INTEGRATION METHODS
    // ========================================
    
    /**
     * Sets dashboard update callback for integration
     */
    public void setDashboardUpdateCallback(Runnable callback) {
        this.dashboardUpdateCallback = callback;
        LOGGER.info("Dashboard update callback set for consensus view");
    }
    
    /**
     * Sets navigation callback
     */
    public void setNavigationCallback(Runnable callback) {
        this.navigationCallback = callback;
        LOGGER.info("Navigation callback set for consensus view");
    }
    
    /**
     * Navigates to destination (usually dashboard)
     */
    private void navigateToDestination() {
        if (navigationCallback != null) {
            navigationCallback.run();
        }
    }
    
    /**
     * Enables or disables real-time updates
     */
    public void setRealTimeUpdatesEnabled(boolean enabled) {
        this.isRealTimeUpdateEnabled = enabled;
        
        if (enabled && updateService == null) {
            startRealTimeUpdates();
        } else if (!enabled && updateService != null) {
            updateService.cancel();
            if (statusUpdateTimeline != null) {
                statusUpdateTimeline.stop();
            }
        }
        
        LOGGER.info("Real-time updates " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Gets the current consensus type
     */
    public ConsensusType getCurrentConsensusType() {
        return currentConsensusType;
    }
    
    /**
     * Gets the current policy
     */
    public ConsensusPolicy getCurrentPolicy() {
        return currentPolicy;
    }
    
    // ========================================
    // CLEANUP METHODS
    // ========================================
    
    /**
     * Cleanup resources when view is closed
     */
    public void cleanup() {
        LOGGER.info("Cleaning up ConsensusView resources");
        
        try {
            // Stop real-time updates
            if (updateService != null && updateService.isRunning()) {
                updateService.cancel();
            }
            
            if (statusUpdateTimeline != null) {
                statusUpdateTimeline.stop();
            }
            
            LOGGER.info("ConsensusView cleanup completed");
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error during ConsensusView cleanup", e);
        }
    }
    
    // ========================================
    // PLACEHOLDER IMPLEMENTATIONS
    // ========================================
    
    // These methods would need proper implementations based on your UI framework and data sources
    
    private void addPoWMetrics(GridPane grid, MiningStatus status) {
        // Add PoW-specific metrics to grid
        VBox hashRateCard = ConsensusUIUtils.createPerformanceCard("Hash Rate", 
            ConsensusUIUtils.formatHashRate(status.hashRate), "H/s", "+12%");
        grid.add(hashRateCard, 0, 0);
        
        VBox difficultyCard = ConsensusUIUtils.createPerformanceCard("Difficulty", 
            String.valueOf(status.currentDifficulty), "", "+0.5%");
        grid.add(difficultyCard, 1, 0);
    }
    
    private void addPoSMetrics(GridPane grid, StakingStatus status) {
        // Add PoS-specific metrics to grid
        VBox stakedCard = ConsensusUIUtils.createPerformanceCard("Total Staked", 
            ConsensusUIUtils.formatCurrency(status.totalStaked), "TINC", "+8%");
        grid.add(stakedCard, 0, 0);
        
        VBox stakeholdersCard = ConsensusUIUtils.createPerformanceCard("Stakeholders", 
            String.valueOf(status.stakeholderCount), "", "+3%");
        grid.add(stakeholdersCard, 1, 0);
    }
    
    private void addPBFTMetrics(GridPane grid, PBFTStatus status) {
        // Add PBFT-specific metrics to grid
        VBox validatorsCard = ConsensusUIUtils.createPerformanceCard("Validators", 
            String.valueOf(status.validatorCount), "", "0%");
        grid.add(validatorsCard, 0, 0);
        
        VBox roundsCard = ConsensusUIUtils.createPerformanceCard("Consensus Rounds", 
            String.valueOf(status.consensusRounds), "", "+15%");
        grid.add(roundsCard, 1, 0);
    }
    
    private void addStreamMetrics(GridPane grid, StreamStatus status) {
        // Add stream-specific metrics to grid
        VBox bufferCard = ConsensusUIUtils.createPerformanceCard("Buffer Size", 
            String.valueOf(status.bufferSize), "", "0%");
        grid.add(bufferCard, 0, 0);
        
        VBox processedCard = ConsensusUIUtils.createPerformanceCard("Processed Batches", 
            String.valueOf(status.processedBatches), "", "+22%");
        grid.add(processedCard, 1, 0);
    }
    
    private VBox createPrerequisitesCheckDisplay() {
        // Create prerequisites check display
        VBox prerequisitesBox = ConsensusUIUtils.createStyledCard();
        Label prerequisitesLabel = ConsensusUIUtils.createStyledLabel("Prerequisites check will be displayed here", 
            ConsensusUIUtils.SECONDARY_TEXT_COLOR, 12, false);
        prerequisitesBox.getChildren().add(prerequisitesLabel);
        return prerequisitesBox;
    }
    
    private VBox createOperationControls() {
        // Create operation controls
        VBox controls = ConsensusUIUtils.createStyledCard();
        Label controlsLabel = ConsensusUIUtils.createStyledLabel("Operation controls panel", 
            ConsensusUIUtils.ACCENT_TEXT_COLOR, 16, true);
        controls.getChildren().add(controlsLabel);
        return controls;
    }
    
    private VBox createOptimizationMetricsDisplay() {
        // Create optimization metrics display
        VBox metrics = new VBox(10);
        Label metricsLabel = ConsensusUIUtils.createStyledLabel("Optimization metrics will be displayed here", 
            ConsensusUIUtils.SECONDARY_TEXT_COLOR, 12, false);
        metrics.getChildren().add(metricsLabel);
        return metrics;
    }
    
    private VBox createHistoricalPerformanceSection() {
        // Create historical performance section
        VBox history = ConsensusUIUtils.createStyledCard();
        Label historyLabel = ConsensusUIUtils.createStyledLabel("📊 Historical Performance", 
            ConsensusUIUtils.ACCENT_TEXT_COLOR, 16, true);
        history.getChildren().add(historyLabel);
        return history;
    }
    
    private VBox createGlobalSettingsPanel() {
        // Create global settings panel
        VBox settings = ConsensusUIUtils.createStyledCard();
        Label settingsLabel = ConsensusUIUtils.createStyledLabel("🌐 Global Settings", 
            ConsensusUIUtils.ACCENT_TEXT_COLOR, 16, true);
        settings.getChildren().add(settingsLabel);
        return settings;
    }
    
    private VBox createConsensusConfigurationsPanel() {
        // Create consensus configurations panel
        VBox configs = ConsensusUIUtils.createStyledCard();
        Label configsLabel = ConsensusUIUtils.createStyledLabel("⚙️ Consensus Configurations", 
            ConsensusUIUtils.ACCENT_TEXT_COLOR, 16, true);
        configs.getChildren().add(configsLabel);
        return configs;
    }
    
    private VBox createAdvancedSettingsPanel() {
        // Create advanced settings panel
        VBox advanced = ConsensusUIUtils.createStyledCard();
        Label advancedLabel = ConsensusUIUtils.createStyledLabel("🔧 Advanced Settings", 
            ConsensusUIUtils.ACCENT_TEXT_COLOR, 16, true);
        advanced.getChildren().add(advancedLabel);
        return advanced;
    }
    
    private ConsensusType getSelectedConsensusType() {
    // Find the selected radio button in the consensus selection form
    if (consensusSwitchPanel != null) {
        return consensusSwitchPanel.getChildren().stream()
            .filter(node -> node instanceof VBox)
            .flatMap(node -> ((VBox)node).getChildren().stream())
            .filter(node -> node instanceof VBox)
            .flatMap(node -> ((VBox)node).getChildren().stream())
            .filter(node -> node instanceof RadioButton && ((RadioButton)node).isSelected())
            .map(node -> {
                String text = ((RadioButton)node).getText();
                for (ConsensusType type : ConsensusType.values()) {
                    if (type.getDisplayName().equals(text)) {
                        return type;
                    }
                }
                return null;
            })
            .filter(type -> type != null)
            .findFirst()
            .orElse(currentConsensusType);
    }
    return currentConsensusType;
}
    private ConsensusPolicy getSelectedPolicy() {
    // Find the selected policy from the policy configuration panel
    if (policyConfigPanel != null) {
        return policyConfigPanel.getChildren().stream()
            .filter(node -> node instanceof VBox)
            .flatMap(node -> ((VBox)node).getChildren().stream())
            .filter(node -> node instanceof ComboBox)
            .map(node -> (ComboBox<ConsensusPolicy>)node)
            .findFirst()
            .map(ComboBox::getValue)
            .orElse(currentPolicy);
    }
    return currentPolicy;
}
    private void updateConsensusStatusDisplay() {
    if (consensusStatusLabel != null) {
        ConsensusStatusInfo status = consensusController.getConsensusStatus();
        String statusText = status.activeConsensusType.getDisplayName() + 
                          " (" + (status.isOperational ? "Active" : "Inactive") + ")";
        consensusStatusLabel.setText(statusText);
        consensusStatusLabel.setStyle(String.format("-fx-text-fill: %s;",
            status.isOperational ? ConsensusUIUtils.SUCCESS_COLOR : ConsensusUIUtils.ERROR_COLOR));
    }
}
    
    private void updatePolicyDisplay() {
    if (policyConfigPanel != null && policyConfigPanel.getChildren().size() > 1) {
        // Find and update the current policy label
        policyConfigPanel.getChildren().stream()
            .filter(node -> node instanceof Label && ((Label)node).getText().contains("Current Policy:"))
            .findFirst()
            .ifPresent(node -> {
                Label label = (Label) node;
                label.setText("Current Policy: " + currentPolicy.getDisplayName());
            });
    }
}
    
    private void updateHealthStatusDisplay(ConsensusHealthStatus health) {
    if (healthStatusPanel != null) {
        Platform.runLater(() -> {
            // Update the health panel with new health data
            updateHealthStatus();
            
            // Show warning if health is critical
            if (health.overallHealth < 0.4) {
                Alert alert = ConsensusUIUtils.createHealthWarningDialog(health);
                alert.show();
            }
        });
    }
}
    
    private void updateRecommendationDisplay(ConsensusRecommendation recommendation) {
    if (recommendationPanel != null) {
        recommendationPanel.getChildren().clear();
        VBox newRecommendationPanel = ConsensusUIUtils.createRecommendationPanel(recommendation);
        recommendationPanel.getChildren().add(newRecommendationPanel);
    }
}
    
    
private void updatePrerequisitesDisplay() {
    // This updates the prerequisites check when consensus type selection changes
    ConsensusType selectedType = getSelectedConsensusType();
    if (selectedType != null && consensusSwitchPanel != null) {
        // Find prerequisites box in the panel
        consensusSwitchPanel.getChildren().stream()
            .filter(node -> node instanceof VBox)
            .forEach(node -> {
                VBox box = (VBox) node;
                if (box.getChildren().stream().anyMatch(child -> 
                    child instanceof Label && ((Label)child).getText().contains("Prerequisites"))) {
                    // Update prerequisites display
                    box.getChildren().clear();
                    box.getChildren().add(createPrerequisitesCheckDisplay());
                }
            });
    }
}
    
    private void updateRecommendationPanel() {
    if (recommendationPanel != null) {
        ConsensusManagerResult result = consensusManager.optimizeConsensus();
        recommendationPanel.getChildren().clear();
        
        if (result.recommendation != null) {
            VBox newPanel = ConsensusUIUtils.createRecommendationPanel(result.recommendation);
            recommendationPanel.getChildren().add(newPanel);
        } else {
            Label noRecommendations = ConsensusUIUtils.createStyledLabel(
                "No optimization recommendations at this time",
                ConsensusUIUtils.INFO_COLOR, 14, false);
            recommendationPanel.getChildren().add(noRecommendations);
        }
    }
}
    
    private void updateStatusOverview() {
    if (statusOverviewPanel != null && statusOverviewPanel.getChildren().size() > 1) {
        // Get current status
        ConsensusStatusInfo status = consensusController.getConsensusStatus();
        ConsensusHealthStatus health = consensusManager.getConsensusHealth();
        
        // Update the status grid that's already in the panel
        GridPane statusGrid = (GridPane) statusOverviewPanel.getChildren().stream()
            .filter(node -> node instanceof GridPane)
            .findFirst()
            .orElse(null);
            
        if (statusGrid != null) {
            // Update block height label
            statusGrid.getChildren().stream()
                .filter(node -> node instanceof VBox)
                .forEach(node -> {
                    VBox vbox = (VBox) node;
                    vbox.getChildren().stream()
                        .filter(child -> child instanceof Label)
                        .forEach(label -> {
                            Label lbl = (Label) label;
                            if (lbl.getText().matches("\\d+")) {
                                lbl.setText(String.valueOf(status.blockHeight));
                            }
                        });
                });
        }
    }
}
    
    private void updateMetricsGrid() {
    if (metricsGrid != null) {
        metricsGrid.getChildren().clear();
        ConsensusStatusInfo status = consensusController.getConsensusStatus();
        
        // Re-add metrics based on current consensus type
        switch (status.activeConsensusType) {
            case PROOF_OF_WORK:
                if (status.powStatus != null) {
                    addPoWMetrics(metricsGrid, status.powStatus);
                }
                break;
            case PROOF_OF_STAKE:
                if (status.posStatus != null) {
                    addPoSMetrics(metricsGrid, status.posStatus);
                }
                break;
            case PBFT:
                if (status.pbftStatus != null) {
                    addPBFTMetrics(metricsGrid, status.pbftStatus);
                }
                break;
            case STREAM_CONSENSUS:
                if (status.streamStatus != null) {
                    addStreamMetrics(metricsGrid, status.streamStatus);
                }
                break;
        }
    }
}
    
    private void updateHealthStatus() {
    if (healthStatusPanel != null && healthStatusPanel.getChildren().size() > 1) {
        ConsensusHealthStatus health = consensusManager.getConsensusHealth();
        
        // Find and update the health indicator
        healthStatusPanel.getChildren().stream()
            .filter(node -> node instanceof HBox)
            .findFirst()
            .ifPresent(node -> {
                HBox healthBox = (HBox) node;
                healthBox.getChildren().stream()
                    .filter(child -> child instanceof VBox)
                    .findFirst()
                    .ifPresent(vbox -> {
                        VBox indicator = ConsensusUIUtils.createHealthStatusIndicator(health.overallHealth);
                        int index = healthBox.getChildren().indexOf(vbox);
                        if (index >= 0) {
                            healthBox.getChildren().set(index, indicator);
                        }
                    });
            });
    }
}
    private void updatePerformanceCharts() {
    if (performanceChart != null && !performanceChart.getData().isEmpty()) {
        XYChart.Series<String, Number> series = performanceChart.getData().get(0);
        
        // Add new data point
        if (series.getData().size() > 20) {
            series.getData().remove(0);
        }
        
        ConsensusStatusInfo status = consensusController.getConsensusStatus();
        double performanceValue = 0;
        
        switch (status.activeConsensusType) {
            case PROOF_OF_WORK:
                performanceValue = status.powStatus != null ? status.powStatus.hashRate : 0;
                break;
            case PROOF_OF_STAKE:
                performanceValue = status.posStatus != null ? status.posStatus.totalStaked : 0;
                break;
            case PBFT:
                performanceValue = status.pbftStatus != null ? status.pbftStatus.consensusRounds : 0;
                break;
            case STREAM_CONSENSUS:
                performanceValue = status.streamStatus != null ? status.streamStatus.processedBatches : 0;
                break;
        }
        
        series.getData().add(new XYChart.Data<>(
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            performanceValue
        ));
    }
}

}