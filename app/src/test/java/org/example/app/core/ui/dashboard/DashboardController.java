package org.example.app.core.ui.dashboard;

// JavaFX imports
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.beans.property.*;

import org.example.app.core.config.BlockchainConfig;
import org.example.app.core.config.ConsensusConfig;
import org.example.app.core.config.NetworkConfig;
import org.example.app.core.config.StorageConfig;
// TINC imports (only the ones that definitely exist)
import org.example.app.core.consensus.Blockchain;
import org.example.app.core.metrics.BlockchainMetrics;
import org.example.app.core.metrics.NetworkMetrics;
import org.example.app.core.ui.configuration.ConfigurationController;
import org.example.app.core.ui.configuration.ConfigurationManager;
import org.example.app.core.ui.configuration.ConfigurationView;
import org.example.app.core.mempool.TransactionPool;

// Java standard imports
import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Simplified Dashboard Controller for TINC Blockchain
 * Works with existing TINC project structure
 */
public class DashboardController implements Initializable {
    private static final Logger LOGGER = Logger.getLogger(DashboardController.class.getName());
    
    // FXML Elements - Basic metrics
    @FXML private Label networkStatusLabel;
    @FXML private Label consensusAlgorithmLabel;
    @FXML private Label blockHeightLabel;
    @FXML private Label activePeersLabel;
    @FXML private Label mempoolSizeLabel;
    @FXML private Label networkHashrateLabel;
    @FXML private Label uptimeLabel;
    @FXML private Label lastBlockTimeLabel;
    
    // FXML Elements - Tables (simplified)
    @FXML private TableView recentBlocksTable;
    @FXML private TableColumn blockHashColumn;
    @FXML private TableColumn blockNumberColumn;
    @FXML private TableColumn blockMinerColumn;
    @FXML private TableColumn blockTimestampColumn;
    @FXML private TableColumn blockTxCountColumn;
    
    @FXML private TableView recentTransactionsTable;
    @FXML private TableColumn txHashColumn;
    @FXML private TableColumn txFromColumn;
    @FXML private TableColumn txToColumn;
    @FXML private TableColumn txValueColumn;
    @FXML private TableColumn txStatusColumn;
    
    // FXML Elements - Charts
    @FXML private LineChart blockTimeChart;
    @FXML private LineChart transactionChart;
    
    // TINC components (only the ones that exist)
    private Blockchain blockchain;
    private BlockchainMetrics blockchainMetrics;
    private NetworkMetrics networkMetrics;
    private TransactionPool transactionPool;
    
    // Formatting
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final DecimalFormat decimalFormat = new DecimalFormat("#,###");
    private long networkStartTime;


      @FXML private Button configNavBtn;  // Add this to your navigation buttons

       // ========================================
    private ConfigurationController configurationController;
    private ConfigurationManager configurationManager;
    private ConfigurationView configurationView;
    
    // Configuration state
    private BlockchainConfig currentBlockchainConfig;
    private ConsensusConfig currentConsensusConfig;
    private NetworkConfig currentNetworkConfig;
    private StorageConfig currentStorageConfig;

    // Configuration listener for real-time updates
    private ConfigurationController.ConfigurationEventListener configEventListener;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        LOGGER.info("Initializing TINC Dashboard Controller...");
        
        try {
            // Initialize TINC components safely
            initializeTincComponents();
            
            // Setup UI
            setupBasicUI();
            
            // Load initial data
            loadInitialData();
            
            LOGGER.info("Dashboard Controller initialized successfully");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error initializing Dashboard Controller", e);
            // Don't crash - just log and continue with limited functionality
        }
    }

    /**
     * Initialize TINC blockchain components safely
     */
    private void initializeTincComponents() {
        try {
            blockchain = new Blockchain();
            blockchainMetrics = new BlockchainMetrics();
            networkMetrics = new NetworkMetrics();
            transactionPool = new TransactionPool(1000);
            networkStartTime = System.currentTimeMillis();
            
            LOGGER.info("TINC components initialized successfully");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Some TINC components failed to initialize", e);
        }
    }

    /**
     * Setup basic UI elements
     */
    private void setupBasicUI() {
        // Set initial values safely
        Platform.runLater(() -> {
            if (networkStatusLabel != null) {
                networkStatusLabel.setText("Online");
                networkStatusLabel.setStyle("-fx-text-fill: #00ff00;");
            }
            
            if (consensusAlgorithmLabel != null) {
                consensusAlgorithmLabel.setText("PBFT + PoW");
            }
            
            if (blockHeightLabel != null) {
                blockHeightLabel.setText("0");
            }
            
            if (activePeersLabel != null) {
                activePeersLabel.setText("5");
            }
            
            if (mempoolSizeLabel != null) {
                mempoolSizeLabel.setText("0");
            }
            
            if (networkHashrateLabel != null) {
                networkHashrateLabel.setText("750 H/s");
            }
            
            if (uptimeLabel != null) {
                uptimeLabel.setText("00:00:00");
            }
        });
        
        LOGGER.info("Basic UI setup completed");
    }

    /**
     * Load initial data from TINC blockchain
     */
    private void loadInitialData() {
        try {
            if (blockchain != null) {
                int chainLength = blockchain.getChainLength();
                
                Platform.runLater(() -> {
                    if (blockHeightLabel != null) {
                        blockHeightLabel.setText(decimalFormat.format(chainLength));
                    }
                });
                
                LOGGER.info("Loaded blockchain data - Chain length: " + chainLength);
            }
            
            if (transactionPool != null) {
                int poolSize = transactionPool.size();
                
                Platform.runLater(() -> {
                    if (mempoolSizeLabel != null) {
                        mempoolSizeLabel.setText(String.valueOf(poolSize));
                    }
                });
                
                LOGGER.info("Loaded mempool data - Pool size: " + poolSize);
            }
            
            // Update uptime
            updateUptime();
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error loading initial data", e);
        }
    }

    /**
     * Update uptime display
     */
    private void updateUptime() {
        try {
            long uptimeMs = System.currentTimeMillis() - networkStartTime;
            String formattedUptime = formatUptime(uptimeMs);
            
            Platform.runLater(() -> {
                if (uptimeLabel != null) {
                    uptimeLabel.setText(formattedUptime);
                }
            });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error updating uptime", e);
        }
    }

    /**
     * Format uptime duration
     */
    private String formatUptime(long uptimeMs) {
        long seconds = uptimeMs / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    /**
     * Public method to refresh dashboard data
     */
    public void refreshDashboard() {
        loadInitialData();
    }

    /**
     * Get current blockchain status
     */
    public String getBlockchainStatus() {
        try {
            if (blockchain != null) {
                return "Chain length: " + blockchain.getChainLength() + 
                       ", Pool size: " + (transactionPool != null ? transactionPool.size() : 0);
            }
            return "Blockchain not initialized";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}