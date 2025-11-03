package org.example.app.core.ui.consensus;

import org.example.app.core.ui.consensus.ConsensusController.ConsensusType;
import org.example.app.core.ui.consensus.ConsensusController.ConsensusStatusInfo;
import org.example.app.core.ui.consensus.ConsensusController.MiningStatus;
import org.example.app.core.ui.consensus.ConsensusController.StakingStatus;
import org.example.app.core.ui.consensus.ConsensusController.PBFTStatus;
import org.example.app.core.ui.consensus.ConsensusController.StreamStatus;
import org.example.app.core.ui.consensus.ConsensusManager.ConsensusPolicy;
import org.example.app.core.ui.consensus.ConsensusManager.ConsensusHealthStatus;
import org.example.app.core.ui.consensus.ConsensusManager.ConsensusRecommendation;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.chart.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.util.Duration;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * ConsensusUIUtils provides comprehensive UI utilities for consensus management interface.
 * 
 * Key features:
 * - Consensus-specific UI components and styling
 * - Data formatting and validation utilities
 * - Interactive charts and visualizations
 * - Status indicators and progress monitoring
 * - Theme consistency across consensus modules
 * - Responsive design utilities
 * - Animation and transition effects
 * - Accessibility support
 * 
 * Production-level utilities for:
 * - Real-time consensus monitoring displays
 * - Interactive consensus switching interfaces
 * - Performance metrics visualization
 * - Health status indicators
 * - Configuration forms and validation
 */
public class ConsensusUIUtils {
    private static final Logger LOGGER = Logger.getLogger(ConsensusUIUtils.class.getName());
    
    // ========================================
    // COLOR SCHEME AND STYLING CONSTANTS
    // ========================================
    
    // Primary consensus colors
    public static final String PRIMARY_BG_COLOR = "#16213e";
    public static final String SECONDARY_BG_COLOR = "#22334e";
    public static final String ACCENT_BG_COLOR = "#2a3f5f";
    public static final String CARD_BG_COLOR = "#1a2644";
    
    // Consensus type specific colors
    public static final String POW_COLOR = "#ff6b35"; // Orange for Proof of Work
    public static final String POS_COLOR = "#4ecdc4"; // Teal for Proof of Stake
    public static final String PBFT_COLOR = "#45b7d1"; // Blue for PBFT
    public static final String STREAM_COLOR = "#96ceb4"; // Green for Stream Consensus
    
    // Status colors
    public static final String SUCCESS_COLOR = "#00ff88";
    public static final String WARNING_COLOR = "#ffa726";
    public static final String ERROR_COLOR = "#ef5350";
    public static final String INFO_COLOR = "#42a5f5";
    public static final String INACTIVE_COLOR = "#78909c";
    
    // Text colors
    public static final String PRIMARY_TEXT_COLOR = "#ffffff";
    public static final String SECONDARY_TEXT_COLOR = "#b0bec5";
    public static final String ACCENT_TEXT_COLOR = "#00ff88";
    public static final String MUTED_TEXT_COLOR = "#78909c";
    
    // Border and accent colors
    public static final String BORDER_COLOR = "#37474f";
    public static final String HIGHLIGHT_COLOR = "#00ff88";
    public static final String SELECTION_COLOR = "rgba(0,255,136,0.3)";
    
    // ========================================
    // FONT AND SIZING CONSTANTS
    // ========================================
    
    public static final String PRIMARY_FONT = "Segoe UI";
    public static final int HEADER_FONT_SIZE = 24;
    public static final int SUBHEADER_FONT_SIZE = 18;
    public static final int BODY_FONT_SIZE = 14;
    public static final int CAPTION_FONT_SIZE = 12;
    
    public static final double CARD_RADIUS = 12.0;
    public static final double BUTTON_RADIUS = 8.0;
    public static final double BORDER_RADIUS = 6.0;
    
    public static final Insets CARD_PADDING = new Insets(20);
    public static final Insets CONTENT_PADDING = new Insets(15);
    public static final Insets BUTTON_PADDING = new Insets(12, 24, 12, 24);
    
    // ========================================
    // FORMATTING UTILITIES
    // ========================================
    
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,###.##");
    private static final DecimalFormat PERCENTAGE_FORMAT = new DecimalFormat("#0.0%");
    private static final DecimalFormat HASH_RATE_FORMAT = new DecimalFormat("#,###.0");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MMM dd, HH:mm");
    
    // ========================================
    // CONSENSUS STATUS INDICATORS
    // ========================================
    
    /**
     * Creates a consensus status indicator with color-coded status
     */
    public static HBox createConsensusStatusIndicator(ConsensusType type, boolean isOperational) {
        HBox statusBox = new HBox(10);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        
        // Status circle
        Circle statusCircle = new Circle(8);
        statusCircle.setFill(Color.web(isOperational ? SUCCESS_COLOR : ERROR_COLOR));
        
        // Add pulsing animation for active status
        if (isOperational) {
            addPulsingAnimation(statusCircle);
        }
        
        // Consensus type label
        Label typeLabel = new Label(type.getDisplayName());
        typeLabel.setStyle(String.format(
            "-fx-text-fill: %s; -fx-font-size: %dpx; -fx-font-weight: bold;",
            getConsensusTypeColor(type), BODY_FONT_SIZE
        ));
        
        // Status text
        Label statusLabel = new Label(isOperational ? "Operational" : "Inactive");
        statusLabel.setStyle(String.format(
            "-fx-text-fill: %s; -fx-font-size: %dpx;",
            isOperational ? SUCCESS_COLOR : ERROR_COLOR, CAPTION_FONT_SIZE
        ));
        
        statusBox.getChildren().addAll(statusCircle, typeLabel, statusLabel);
        return statusBox;
    }
    
    /**
     * Creates a health status indicator with percentage and visual representation
     */
    public static VBox createHealthStatusIndicator(double healthPercentage) {
        VBox healthBox = new VBox(5);
        healthBox.setAlignment(Pos.CENTER);
        
        // Health percentage label
        Label healthLabel = new Label(PERCENTAGE_FORMAT.format(healthPercentage));
        healthLabel.setStyle(String.format(
            "-fx-text-fill: %s; -fx-font-size: %dpx; -fx-font-weight: bold;",
            getHealthColor(healthPercentage), HEADER_FONT_SIZE
        ));
        
        // Health status text
        String healthText = getHealthStatusText(healthPercentage);
        Label statusText = new Label(healthText);
        statusText.setStyle(String.format(
            "-fx-text-fill: %s; -fx-font-size: %dpx;",
            SECONDARY_TEXT_COLOR, CAPTION_FONT_SIZE
        ));
        
        // Health progress bar
        ProgressBar healthBar = createStyledProgressBar(healthPercentage);
        healthBar.setPrefWidth(100);
        
        healthBox.getChildren().addAll(healthLabel, statusText, healthBar);
        return healthBox;
    }
    
    /**
     * Creates a consensus performance card with metrics
     */
    public static VBox createPerformanceCard(String title, String value, String unit, String trend) {
        VBox card = createStyledCard();
        card.setPrefWidth(200);
        
        // Title
        Label titleLabel = createStyledLabel(title, SECONDARY_TEXT_COLOR, CAPTION_FONT_SIZE, false);
        
        // Value
        HBox valueBox = new HBox(5);
        valueBox.setAlignment(Pos.BASELINE_LEFT);
        
        Label valueLabel = createStyledLabel(value, PRIMARY_TEXT_COLOR, HEADER_FONT_SIZE, true);
        Label unitLabel = createStyledLabel(unit, SECONDARY_TEXT_COLOR, BODY_FONT_SIZE, false);
        
        valueBox.getChildren().addAll(valueLabel, unitLabel);
        
        // Trend indicator
        Label trendLabel = createTrendLabel(trend);
        
        card.getChildren().addAll(titleLabel, valueBox, trendLabel);
        return card;
    }
    
    // ========================================
    // CONSENSUS-SPECIFIC UI COMPONENTS
    // ========================================
    
    /**
     * Creates a mining status panel for Proof of Work
     */
    public static VBox createMiningStatusPanel(MiningStatus status) {
        VBox panel = createStyledCard();
        panel.setSpacing(15);
        
        // Header
        Label header = createStyledLabel("⛏️ Mining Status", ACCENT_TEXT_COLOR, SUBHEADER_FONT_SIZE, true);
        
        // Mining status
        HBox statusRow = createInfoRow("Status", status.isMining ? "Mining" : "Stopped", 
            status.isMining ? SUCCESS_COLOR : ERROR_COLOR);
        
        // Miner address
        HBox addressRow = createInfoRow("Miner Address", 
            formatAddress(status.minerAddress), INFO_COLOR);
        
        // Difficulty
        HBox difficultyRow = createInfoRow("Difficulty", 
            DECIMAL_FORMAT.format(status.currentDifficulty), POW_COLOR);
        
        // Hash rate
        HBox hashRateRow = createInfoRow("Hash Rate", 
            formatHashRate(status.hashRate), POW_COLOR);
        
        panel.getChildren().addAll(header, statusRow, addressRow, difficultyRow, hashRateRow);
        return panel;
    }
    
    /**
     * Creates a staking status panel for Proof of Stake
     */
    public static VBox createStakingStatusPanel(StakingStatus status) {
        VBox panel = createStyledCard();
        panel.setSpacing(15);
        
        // Header
        Label header = createStyledLabel("🏦 Staking Status", ACCENT_TEXT_COLOR, SUBHEADER_FONT_SIZE, true);
        
        // Total staked
        HBox totalStakedRow = createInfoRow("Total Staked", 
            formatCurrency(status.totalStaked) + " TINC", POS_COLOR);
        
        // Stakeholder count
        HBox stakeholderRow = createInfoRow("Stakeholders", 
            String.valueOf(status.stakeholderCount), POS_COLOR);
        
        // My stake
        HBox myStakeRow = createInfoRow("My Stake", 
            formatCurrency(status.myStake) + " TINC", SUCCESS_COLOR);
        
        // My staking address
        HBox addressRow = createInfoRow("Staking Address", 
            formatAddress(status.myStakingAddress), INFO_COLOR);
        
        panel.getChildren().addAll(header, totalStakedRow, stakeholderRow, myStakeRow, addressRow);
        return panel;
    }
    
    /**
     * Creates a PBFT validator status panel
     */
    public static VBox createPBFTStatusPanel(PBFTStatus status) {
        VBox panel = createStyledCard();
        panel.setSpacing(15);
        
        // Header
        Label header = createStyledLabel("🛡️ PBFT Consensus", ACCENT_TEXT_COLOR, SUBHEADER_FONT_SIZE, true);
        
        // Validation status
        HBox validationRow = createInfoRow("Validation", 
            status.isValidating ? "Active" : "Inactive", 
            status.isValidating ? SUCCESS_COLOR : ERROR_COLOR);
        
        // Validator count
        HBox validatorRow = createInfoRow("Validators", 
            String.valueOf(status.validatorCount), PBFT_COLOR);
        
        // Primary node status
        HBox primaryRow = createInfoRow("Node Role", 
            status.isPrimaryNode ? "Primary" : "Replica", 
            status.isPrimaryNode ? WARNING_COLOR : INFO_COLOR);
        
        // Consensus rounds
        HBox roundsRow = createInfoRow("Consensus Rounds", 
            String.valueOf(status.consensusRounds), PBFT_COLOR);
        
        panel.getChildren().addAll(header, validationRow, validatorRow, primaryRow, roundsRow);
        return panel;
    }
    
    /**
     * Creates a stream consensus status panel
     */
    public static VBox createStreamStatusPanel(StreamStatus status) {
        VBox panel = createStyledCard();
        panel.setSpacing(15);
        
        // Header
        Label header = createStyledLabel("📊 Stream Consensus", ACCENT_TEXT_COLOR, SUBHEADER_FONT_SIZE, true);
        
        // Processing status
        HBox processingRow = createInfoRow("Processing", 
            status.isProcessing ? "Active" : "Stopped", 
            status.isProcessing ? SUCCESS_COLOR : ERROR_COLOR);
        
        // Buffer size
        HBox bufferRow = createInfoRow("Buffer Size", 
            String.valueOf(status.bufferSize), STREAM_COLOR);
        
        // Batch size
        HBox batchRow = createInfoRow("Batch Size", 
            String.valueOf(status.batchSize), STREAM_COLOR);
        
        // Processed batches
        HBox processedRow = createInfoRow("Processed Batches", 
            String.valueOf(status.processedBatches), SUCCESS_COLOR);
        
        panel.getChildren().addAll(header, processingRow, bufferRow, batchRow, processedRow);
        return panel;
    }
    
    // ========================================
    // CONSENSUS CONFIGURATION FORMS
    // ========================================
    
    /**
     * Creates a consensus type selection form
     */
    public static VBox createConsensusSelectionForm(ConsensusType currentType, 
                                                   Runnable onSelectionChanged) {
        VBox form = new VBox(15);
        
        Label formTitle = createStyledLabel("Select Consensus Mechanism", 
            PRIMARY_TEXT_COLOR, SUBHEADER_FONT_SIZE, true);
        
        // Radio button group for consensus types
        ToggleGroup consensusGroup = new ToggleGroup();
        VBox radioBox = new VBox(10);
        
        for (ConsensusType type : ConsensusType.values()) {
            RadioButton radio = createConsensusRadioButton(type, currentType == type);
            radio.setToggleGroup(consensusGroup);
            radio.setOnAction(e -> {
                if (onSelectionChanged != null) {
                    onSelectionChanged.run();
                }
            });
            radioBox.getChildren().add(radio);
        }
        
        form.getChildren().addAll(formTitle, radioBox);
        return form;
    }
    
    /**
     * Creates a mining configuration form
     */
    public static VBox createMiningConfigForm(String currentAddress, long currentDifficulty) {
        VBox form = new VBox(15);
        
        Label formTitle = createStyledLabel("Mining Configuration", 
            PRIMARY_TEXT_COLOR, SUBHEADER_FONT_SIZE, true);
        
        // Miner address field
        VBox addressGroup = createFormGroup("Miner Address:", currentAddress, 
            "Enter your miner address");
        
        // Difficulty field
        VBox difficultyGroup = createFormGroup("Difficulty:", String.valueOf(currentDifficulty), 
            "Enter mining difficulty (higher = more secure, slower)");
        
        form.getChildren().addAll(formTitle, addressGroup, difficultyGroup);
        return form;
    }
    
    /**
     * Creates a staking configuration form
     */
    public static VBox createStakingConfigForm(String currentAddress, long currentStake) {
        VBox form = new VBox(15);
        
        Label formTitle = createStyledLabel("Staking Configuration", 
            PRIMARY_TEXT_COLOR, SUBHEADER_FONT_SIZE, true);
        
        // Staking address field
        VBox addressGroup = createFormGroup("Staking Address:", currentAddress, 
            "Enter your staking address");
        
        // Stake amount field
        VBox stakeGroup = createFormGroup("Stake Amount:", String.valueOf(currentStake), 
            "Enter amount to stake (minimum 1000 TINC)");
        
        form.getChildren().addAll(formTitle, addressGroup, stakeGroup);
        return form;
    }
    
    // ========================================
    // POLICY AND OPTIMIZATION UI
    // ========================================
    
    /**
     * Creates a consensus policy selection form
     */
    public static VBox createPolicySelectionForm(ConsensusPolicy currentPolicy) {
        VBox form = new VBox(15);
        
        Label formTitle = createStyledLabel("Consensus Management Policy", 
            PRIMARY_TEXT_COLOR, SUBHEADER_FONT_SIZE, true);
        
        // Policy combo box
        ComboBox<ConsensusPolicy> policyCombo = new ComboBox<>();
        policyCombo.getItems().addAll(ConsensusPolicy.values());
        policyCombo.setValue(currentPolicy);
        policyCombo.setStyle(createComboBoxStyle());
        
        // Policy description
        Label descriptionLabel = createStyledLabel(currentPolicy.getDescription(), 
            SECONDARY_TEXT_COLOR, BODY_FONT_SIZE, false);
        descriptionLabel.setWrapText(true);
        
        // Update description when policy changes
        policyCombo.setOnAction(e -> {
            ConsensusPolicy selected = policyCombo.getValue();
            if (selected != null) {
                descriptionLabel.setText(selected.getDescription());
            }
        });
        
        form.getChildren().addAll(formTitle, policyCombo, descriptionLabel);
        return form;
    }
    
    /**
     * Creates a recommendation display panel
     */
    public static VBox createRecommendationPanel(ConsensusRecommendation recommendation) {
        VBox panel = createStyledCard();
        panel.setSpacing(15);
        
        // Header with recommendation icon
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label icon = createStyledLabel("💡", WARNING_COLOR, HEADER_FONT_SIZE, false);
        Label title = createStyledLabel("Consensus Recommendation", 
            WARNING_COLOR, SUBHEADER_FONT_SIZE, true);
        
        header.getChildren().addAll(icon, title);
        
        if (recommendation.shouldSwitch) {
            // Recommendation to switch
            Label switchLabel = createStyledLabel(
                "Recommended: " + recommendation.recommendedType.getDisplayName(),
                ACCENT_TEXT_COLOR, BODY_FONT_SIZE, true);
            
            Label reasonLabel = createStyledLabel(recommendation.reason, 
                SECONDARY_TEXT_COLOR, BODY_FONT_SIZE, false);
            reasonLabel.setWrapText(true);
            
            // Confidence indicator
            HBox confidenceBox = new HBox(10);
            confidenceBox.setAlignment(Pos.CENTER_LEFT);
            
            Label confidenceText = createStyledLabel("Confidence:", 
                SECONDARY_TEXT_COLOR, CAPTION_FONT_SIZE, false);
            ProgressBar confidenceBar = createStyledProgressBar(recommendation.confidence);
            confidenceBar.setPrefWidth(100);
            Label confidencePercent = createStyledLabel(
                PERCENTAGE_FORMAT.format(recommendation.confidence),
                PRIMARY_TEXT_COLOR, CAPTION_FONT_SIZE, true);
            
            confidenceBox.getChildren().addAll(confidenceText, confidenceBar, confidencePercent);
            
            panel.getChildren().addAll(header, switchLabel, reasonLabel, confidenceBox);
        } else {
            // No switch recommended
            Label noSwitchLabel = createStyledLabel("Current consensus is optimal", 
                SUCCESS_COLOR, BODY_FONT_SIZE, true);
            
            Label reasonLabel = createStyledLabel(recommendation.reason, 
                SECONDARY_TEXT_COLOR, BODY_FONT_SIZE, false);
            reasonLabel.setWrapText(true);
            
            panel.getChildren().addAll(header, noSwitchLabel, reasonLabel);
        }
        
        return panel;
    }
    
    // ========================================
    // CHARTS AND VISUALIZATIONS
    // ========================================
    
    /**
     * Creates a consensus performance chart
     */
    public static LineChart<String, Number> createPerformanceChart(String title) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Time");
        xAxis.setTickLabelFill(Color.web(SECONDARY_TEXT_COLOR));
        
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Performance");
        yAxis.setTickLabelFill(Color.web(SECONDARY_TEXT_COLOR));
        
        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setStyle(createChartStyle());
        chart.setLegendVisible(true);
        chart.setAnimated(true);
        
        return chart;
    }
    
    /**
     * Creates a consensus distribution pie chart
     */
    public static PieChart createConsensusDistributionChart(Map<ConsensusType, Integer> distribution) {
        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        
        for (Map.Entry<ConsensusType, Integer> entry : distribution.entrySet()) {
            PieChart.Data slice = new PieChart.Data(
                entry.getKey().getDisplayName(), 
                entry.getValue()
            );
            data.add(slice);
        }
        
        PieChart chart = new PieChart(data);
        chart.setTitle("Consensus Usage Distribution");
        chart.setStyle(createChartStyle());
        chart.setLegendVisible(true);
        
        // Apply consensus type colors to pie slices
        chart.getData().forEach(pieData -> {
            for (ConsensusType type : ConsensusType.values()) {
                if (pieData.getName().equals(type.getDisplayName())) {
                    pieData.getNode().setStyle("-fx-pie-color: " + getConsensusTypeColor(type) + ";");
                    break;
                }
            }
        });
        
        return chart;
    }
    
    /**
     * Creates a real-time metrics dashboard
     */
    public static GridPane createMetricsDashboard(ConsensusStatusInfo status) {
        GridPane dashboard = new GridPane();
        dashboard.setHgap(15);
        dashboard.setVgap(15);
        dashboard.setStyle("-fx-padding: 20;");
        
        // Block height metric
        VBox blockHeightCard = createPerformanceCard("Block Height", 
            String.valueOf(status.blockHeight), "blocks", "+2.3%");
        dashboard.add(blockHeightCard, 0, 0);
        
        // Pending transactions metric
        VBox pendingTxCard = createPerformanceCard("Pending Transactions", 
            String.valueOf(status.pendingTransactions), "txs", "-5.1%");
        dashboard.add(pendingTxCard, 1, 0);
        
        // Add consensus-specific metrics
        if (status.powStatus != null) {
            VBox hashRateCard = createPerformanceCard("Hash Rate", 
                formatHashRate(status.powStatus.hashRate), "H/s", "+12.7%");
            dashboard.add(hashRateCard, 2, 0);
            
            VBox difficultyCard = createPerformanceCard("Difficulty", 
                DECIMAL_FORMAT.format(status.powStatus.currentDifficulty), "", "+0.8%");
            dashboard.add(difficultyCard, 0, 1);
        }
        
        if (status.posStatus != null) {
            VBox stakedCard = createPerformanceCard("Total Staked", 
                formatCurrency(status.posStatus.totalStaked), "TINC", "+8.4%");
            dashboard.add(stakedCard, 2, 0);
            
            VBox stakeholdersCard = createPerformanceCard("Stakeholders", 
                String.valueOf(status.posStatus.stakeholderCount), "", "+3.2%");
            dashboard.add(stakeholdersCard, 0, 1);
        }
        
        if (status.pbftStatus != null) {
            VBox validatorsCard = createPerformanceCard("Validators", 
                String.valueOf(status.pbftStatus.validatorCount), "", "0%");
            dashboard.add(validatorsCard, 2, 0);
            
            VBox roundsCard = createPerformanceCard("Consensus Rounds", 
                String.valueOf(status.pbftStatus.consensusRounds), "", "+15.6%");
            dashboard.add(roundsCard, 0, 1);
        }
        
        return dashboard;
    }
    
    // ========================================
    // DIALOGS AND ALERTS
    // ========================================
    
    /**
     * Creates a consensus switch confirmation dialog
     */
    public static Alert createConsensusSwitchDialog(ConsensusType fromType, ConsensusType toType) {
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("Consensus Mechanism Switch");
        dialog.setHeaderText("Switch from " + fromType.getDisplayName() + " to " + toType.getDisplayName());
        
        String content = String.format(
            "Are you sure you want to switch the consensus mechanism?\n\n" +
            "From: %s\n" +
            "To: %s\n\n" +
            "This operation will:\n" +
            "• Stop current consensus operations\n" +
            "• Transition blockchain state\n" +
            "• Start new consensus mechanism\n\n" +
            "The process may take a few moments to complete.",
            fromType.getDisplayName(),
            toType.getDisplayName()
        );
        
        dialog.setContentText(content);
        styleAlert(dialog);
        
        return dialog;
    }
    
    /**
     * Creates a consensus health warning dialog
     */
    public static Alert createHealthWarningDialog(ConsensusHealthStatus health) {
        Alert dialog = new Alert(Alert.AlertType.WARNING);
        dialog.setTitle("Consensus Health Warning");
        dialog.setHeaderText("Consensus Health Issues Detected");
        
        StringBuilder content = new StringBuilder();
        content.append(String.format("Overall Health: %s\n\n", 
            PERCENTAGE_FORMAT.format(health.overallHealth)));
        
        content.append("Issues Detected:\n");
        for (String issue : health.healthIssues) {
            content.append("• ").append(issue).append("\n");
        }
        
        if (!health.recommendations.isEmpty()) {
            content.append("\nRecommendations:\n");
            for (ConsensusRecommendation rec : health.recommendations) {
                content.append("• ").append(rec.reason).append("\n");
            }
        }
        
        dialog.setContentText(content.toString());
        styleAlert(dialog);
        
        return dialog;
    }
    
    // ========================================
    // VALIDATION UTILITIES
    // ========================================
    
    /**
     * Validates miner address format
     */
    public static ValidationResult validateMinerAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return new ValidationResult(false, "Miner address cannot be empty");
        }
        
        if (!address.matches("^0x[a-fA-F0-9]{40}$")) {
            return new ValidationResult(false, "Invalid address format. Must be 42 characters starting with 0x");
        }
        
        return new ValidationResult(true, "Valid miner address");
    }
    
    /**
     * Validates difficulty value
     */
    public static ValidationResult validateDifficulty(String difficultyStr) {
        try {
            long difficulty = Long.parseLong(difficultyStr);
            if (difficulty <= 0) {
                return new ValidationResult(false, "Difficulty must be positive");
            }
            if (difficulty > 1000000000L) {
                return new ValidationResult(false, "Difficulty too high (maximum: 1,000,000,000)");
            }
            return new ValidationResult(true, "Valid difficulty");
        } catch (NumberFormatException e) {
            return new ValidationResult(false, "Difficulty must be a valid number");
        }
    }
    
    /**
     * Validates stake amount
     */
    public static ValidationResult validateStakeAmount(String amountStr, long availableBalance) {
        try {
            long amount = Long.parseLong(amountStr);
            if (amount <= 0) {
                return new ValidationResult(false, "Stake amount must be positive");
            }
            if (amount < 1000) {
                return new ValidationResult(false, "Minimum stake amount is 1,000 TINC");
            }
            if (amount > availableBalance) {
                return new ValidationResult(false, "Insufficient balance for staking");
            }
            return new ValidationResult(true, "Valid stake amount");
        } catch (NumberFormatException e) {
            return new ValidationResult(false, "Stake amount must be a valid number");
        }
    }
    
    // ========================================
    // STYLE CREATION UTILITIES
    // ========================================
    
    /**
     * Creates a styled card container
     */
    public static VBox createStyledCard() {
        VBox card = new VBox(10);
        card.setStyle(String.format(
            "-fx-background-color: %s; " +
            "-fx-background-radius: %.1f; " +
            "-fx-padding: 20; " +
            "-fx-border-color: %s; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: %.1f;",
            CARD_BG_COLOR, CARD_RADIUS, BORDER_COLOR, CARD_RADIUS
        ));
        return card;
    }
    
    /**
     * Creates a styled label
     */
    public static Label createStyledLabel(String text, String color, int fontSize, boolean bold) {
        Label label = new Label(text);
        label.setStyle(String.format(
            "-fx-text-fill: %s; " +
            "-fx-font-size: %dpx; " +
            "-fx-font-weight: %s;",
            color, fontSize, bold ? "bold" : "normal"
        ));
        return label;
    }
    
    /**
     * Creates a styled button
     */
    public static Button createStyledButton(String text, String backgroundColor, String textColor) {
        Button button = new Button(text);
        button.setStyle(String.format(
            "-fx-background-color: %s; " +
            "-fx-text-fill: %s; " +
            "-fx-font-weight: bold; " +
            "-fx-padding: 12 24; " +
            "-fx-background-radius: %.1f; " +
            "-fx-border-radius: %.1f; " +
            "-fx-font-size: %dpx;",
            backgroundColor, textColor, BUTTON_RADIUS, BUTTON_RADIUS, BODY_FONT_SIZE
        ));
        
        // Add hover effect
        button.setOnMouseEntered(e -> button.setStyle(button.getStyle() + 
            "; -fx-effect: dropshadow(gaussian, rgba(0,255,136,0.5), 10, 0.3, 0, 0);"));
        button.setOnMouseExited(e -> button.setStyle(button.getStyle().replace(
            "; -fx-effect: dropshadow(gaussian, rgba(0,255,136,0.5), 10, 0.3, 0, 0)", "")));
        
        return button;
    }
    
    /**
     * Creates a styled progress bar
     */
    public static ProgressBar createStyledProgressBar(double progress) {
        ProgressBar bar = new ProgressBar(progress);
        bar.setStyle(String.format(
            "-fx-accent: %s; " +
            "-fx-background-color: %s; " +
            "-fx-background-radius: 3;",
            SUCCESS_COLOR, SECONDARY_BG_COLOR
        ));
        return bar;
    }
    
    // ========================================
    // HELPER METHODS
    // ========================================
    
    /**
     * Gets color for consensus type
     */
    public static String getConsensusTypeColor(ConsensusType type) {
        switch (type) {
            case PROOF_OF_WORK: return POW_COLOR;
            case PROOF_OF_STAKE: return POS_COLOR;
            case PBFT: return PBFT_COLOR;
            case STREAM_CONSENSUS: return STREAM_COLOR;
            default: return INFO_COLOR;
        }
    }
    
    /**
     * Gets health status color based on percentage
     */
    private static String getHealthColor(double healthPercentage) {
        if (healthPercentage >= 0.8) return SUCCESS_COLOR;
        if (healthPercentage >= 0.6) return WARNING_COLOR;
        return ERROR_COLOR;
    }
    
    /**
     * Gets health status text based on percentage
     */
    private static String getHealthStatusText(double healthPercentage) {
        if (healthPercentage >= 0.9) return "Excellent";
        if (healthPercentage >= 0.8) return "Good";
        if (healthPercentage >= 0.6) return "Fair";
        if (healthPercentage >= 0.4) return "Poor";
        return "Critical";
    }
    
    /**
     * Creates an info row with label and value
     */
    private static HBox createInfoRow(String label, String value, String valueColor) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        
        Label labelText = createStyledLabel(label + ":", SECONDARY_TEXT_COLOR, BODY_FONT_SIZE, false);
        labelText.setPrefWidth(120);
        
        Label valueText = createStyledLabel(value, valueColor, BODY_FONT_SIZE, true);
        
        row.getChildren().addAll(labelText, valueText);
        return row;
    }
    
    /**
     * Creates a trend label with appropriate styling
     */
    private static Label createTrendLabel(String trend) {
        Label trendLabel = new Label(trend);
        
        String color = SUCCESS_COLOR; // Default to positive
        if (trend.startsWith("-")) {
            color = ERROR_COLOR;
        } else if (trend.equals("0%")) {
            color = SECONDARY_TEXT_COLOR;
        }
        
        trendLabel.setStyle(String.format(
            "-fx-text-fill: %s; -fx-font-size: %dpx; -fx-font-weight: bold;",
            color, CAPTION_FONT_SIZE
        ));
        
        return trendLabel;
    }
    
    /**
     * Creates a consensus radio button with custom styling
     */
    private static RadioButton createConsensusRadioButton(ConsensusType type, boolean selected) {
        RadioButton radio = new RadioButton(type.getDisplayName());
        radio.setSelected(selected);
        radio.setStyle(String.format(
            "-fx-text-fill: %s; " +
            "-fx-font-size: %dpx; " +
            "-fx-padding: 5;",
            PRIMARY_TEXT_COLOR, BODY_FONT_SIZE
        ));
        return radio;
    }
    
    /**
     * Creates a form group with label, text field, and help text
     */
    private static VBox createFormGroup(String labelText, String defaultValue, String helpText) {
        VBox group = new VBox(5);
        
        Label label = createStyledLabel(labelText, PRIMARY_TEXT_COLOR, BODY_FONT_SIZE, true);
        
        TextField textField = new TextField(defaultValue);
        textField.setStyle(String.format(
            "-fx-background-color: %s; " +
            "-fx-text-fill: %s; " +
            "-fx-border-color: %s; " +
            "-fx-border-radius: %.1f; " +
            "-fx-padding: 8;",
            SECONDARY_BG_COLOR, PRIMARY_TEXT_COLOR, BORDER_COLOR, BORDER_RADIUS
        ));
        
        Label help = createStyledLabel(helpText, MUTED_TEXT_COLOR, CAPTION_FONT_SIZE, false);
        help.setWrapText(true);
        
        group.getChildren().addAll(label, textField, help);
        return group;
    }
    
    // ========================================
    // FORMATTING UTILITIES
    // ========================================
    
    /**
     * Formats address for display (shows first 8 and last 6 characters)
     */
    public static String formatAddress(String address) {
        if (address == null || address.length() < 14) {
            return address != null ? address : "Not Set";
        }
        return address.substring(0, 8) + "..." + address.substring(address.length() - 6);
    }
    
    /**
     * Formats hash rate for display
     */
    public static String formatHashRate(double hashRate) {
        if (hashRate >= 1_000_000_000) {
            return HASH_RATE_FORMAT.format(hashRate / 1_000_000_000) + " GH/s";
        } else if (hashRate >= 1_000_000) {
            return HASH_RATE_FORMAT.format(hashRate / 1_000_000) + " MH/s";
        } else if (hashRate >= 1_000) {
            return HASH_RATE_FORMAT.format(hashRate / 1_000) + " KH/s";
        } else {
            return HASH_RATE_FORMAT.format(hashRate) + " H/s";
        }
    }
    
    /**
     * Formats currency amount
     */
    public static String formatCurrency(long amount) {
        return DECIMAL_FORMAT.format(amount);
    }
    
    /**
     * Formats timestamp for display
     */
    public static String formatTimestamp(LocalDateTime timestamp) {
        return timestamp != null ? timestamp.format(DATE_TIME_FORMAT) : "N/A";
    }
    
    // ========================================
    // ANIMATION UTILITIES
    // ========================================
    
    /**
     * Adds pulsing animation to a node
     */
    private static void addPulsingAnimation(Node node) {
        ScaleTransition pulse = new ScaleTransition(Duration.seconds(1), node);
        pulse.setFromX(1.0);
        pulse.setToX(1.2);
        pulse.setFromY(1.0);
        pulse.setToY(1.2);
        pulse.setCycleCount(Timeline.INDEFINITE);
        pulse.setAutoReverse(true);
        pulse.play();
    }
    
    /**
     * Adds fade-in animation to a node
     */
    public static void addFadeInAnimation(Node node) {
        FadeTransition fade = new FadeTransition(Duration.millis(500), node);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.play();
    }
    
    // ========================================
    // STYLE STRING BUILDERS
    // ========================================
    
    /**
     * Creates combo box style
     */
    private static String createComboBoxStyle() {
        return String.format(
            "-fx-background-color: %s; " +
            "-fx-text-fill: %s; " +
            "-fx-border-color: %s; " +
            "-fx-border-radius: %.1f; " +
            "-fx-padding: 8;",
            SECONDARY_BG_COLOR, PRIMARY_TEXT_COLOR, BORDER_COLOR, BORDER_RADIUS
        );
    }
    
    /**
     * Creates chart style
     */
    private static String createChartStyle() {
        return String.format(
            "-fx-background-color: %s; " +
            "-fx-text-fill: %s; " +
            "-fx-title-side: top;",
            PRIMARY_BG_COLOR, PRIMARY_TEXT_COLOR
        );
    }
    
    /**
     * Applies consistent styling to alerts
     */
    private static void styleAlert(Alert alert) {
        // Apply dark theme styling to alert dialogs
        alert.getDialogPane().setStyle(String.format(
            "-fx-background-color: %s; " +
            "-fx-text-fill: %s;",
            PRIMARY_BG_COLOR, PRIMARY_TEXT_COLOR
        ));
    }
    
    // ========================================
    // VALIDATION RESULT CLASS
    // ========================================
    
    /**
     * Validation result container
     */
    public static class ValidationResult {
        public final boolean isValid;
        public final String message;
        
        public ValidationResult(boolean isValid, String message) {
            this.isValid = isValid;
            this.message = message;
        }
    }
}